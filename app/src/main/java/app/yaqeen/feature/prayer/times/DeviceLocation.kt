package app.yaqeen.feature.prayer.times

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

sealed interface DeviceLocationResult {
    data class Success(val latitude: Double, val longitude: Double, val isStale: Boolean) : DeviceLocationResult
    /** لم يُمنح إذن الموقع. */
    data object NoPermission : DeviceLocationResult
    /** خدمات الموقع معطّلة في النظام ولا يوجد موقع مخزَّن. */
    data object ProviderDisabled : DeviceLocationResult
    /** لم نحصل على تحديد للموقع خلال المهلة. */
    data object Unavailable : DeviceLocationResult
}

/**
 * تحديد الموقع مرة واحدة (لا تحديثات مستمرة) عبر LocationManager بلا خدمات Google:
 * getCurrentLocation على API 30+ وrequestSingleUpdate قبلها، مع الرجوع لآخر موقع معروف.
 */
object DeviceLocation {
    fun hasPermission(context: Context): Boolean = hasFine(context) || hasCoarse(context)
    private fun hasFine(c: Context) = ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    private fun hasCoarse(c: Context) = ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun isLocationEnabled(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return LocationManagerCompat.isLocationEnabled(lm)
    }

    @SuppressLint("MissingPermission")
    suspend fun fetch(context: Context): DeviceLocationResult {
        if (!hasPermission(context)) return DeviceLocationResult.NoPermission
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return DeviceLocationResult.Unavailable
        val fine = hasFine(context)

        // المزودات المتاحة بالترتيب: الشبكة/الموحّد (سريع ويكفي لمدينة)، ثم GPS (يتطلب الإذن الدقيق).
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= 31) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            if (fine) add(LocationManager.GPS_PROVIDER)
        }.filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }

        for (provider in providers) {
            val timeout = if (provider == LocationManager.GPS_PROVIDER) 25_000L else 10_000L
            val loc = withTimeoutOrNull(timeout) { currentFix(lm, provider) }
            if (loc != null) return DeviceLocationResult.Success(loc.latitude, loc.longitude, isStale = false)
        }

        // لا تحديد حي: نستعمل آخر موقع معروف (قد يكون قديمًا) إن وُجد.
        val last = lm.getProviders(true)
            .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            .maxByOrNull { it.time }
        if (last != null) return DeviceLocationResult.Success(last.latitude, last.longitude, isStale = true)

        return if (providers.isEmpty() && !isLocationEnabled(context)) DeviceLocationResult.ProviderDisabled else DeviceLocationResult.Unavailable
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentFix(lm: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { cont ->
            if (Build.VERSION.SDK_INT >= 30) {
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                try {
                    lm.getCurrentLocation(provider, signal, java.util.concurrent.Executor { it.run() }) { loc -> if (cont.isActive) cont.resume(loc) }
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
            } else {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) { if (cont.isActive) cont.resume(location) }
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) { if (cont.isActive) cont.resume(null) }
                }
                cont.invokeOnCancellation { runCatching { lm.removeUpdates(listener) } }
                try {
                    @Suppress("DEPRECATION")
                    lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
}
