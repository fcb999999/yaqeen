package app.yaqeen.feature.prayer.times

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.abs

/** قراءة من البوصلة: [azimuth] بالدرجات من الشمال المغناطيسي، و[accuracy] من SensorManager.SENSOR_STATUS_*. */
data class CompassReading(val azimuth: Double, val accuracy: Int)

/**
 * بوصلة قائمة على حساس TYPE_ROTATION_VECTOR (دمج المغناطيسية والتسارع والجيروسكوب).
 * لا تعمل إلا أثناء جمع التدفق (يُسجَّل المستمع ويُلغى تلقائيًا) فلا استهلاك للبطارية في الخلفية.
 */
object CompassSensor {
    fun isAvailable(context: Context): Boolean {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return false
        return sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null
    }

    fun readings(context: Context): Flow<CompassReading> = callbackFlow {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sm == null || sensor == null) {
            close()
            return@callbackFlow
        }
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        var lastAccuracy = SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
        val matrix = FloatArray(9)
        val remapped = FloatArray(9)
        val orientation = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                @Suppress("DEPRECATION")
                val rotation = wm.defaultDisplay.rotation
                val az = azimuth(matrix, remapped, orientation, rotation)
                trySend(CompassReading(az, lastAccuracy.coerceAtMost(event.accuracy.takeIf { it >= 0 } ?: lastAccuracy)))
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                lastAccuracy = accuracy
            }
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        awaitClose { sm.unregisterListener(listener) }
    }

    /**
     * زاوية اتجاه الجهاز من الشمال المغناطيسي (0..360). عند حمل الهاتف أفقيًا يُحسب من محور أعلى الشاشة،
     * وعند حمله شبه رأسي (الكاميرا أمامك) يُحسب من اتجاه الظهر حتى لا تتشوش القراءة.
     */
    private fun azimuth(r: FloatArray, out: FloatArray, orientation: FloatArray, displayRotation: Int): Double {
        val upright = abs(r[8]) < 0.45f
        val ok = if (upright) {
            SensorManager.remapCoordinateSystem(r, SensorManager.AXIS_X, SensorManager.AXIS_Z, out)
        } else {
            when (displayRotation) {
                Surface.ROTATION_90 -> SensorManager.remapCoordinateSystem(r, SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, out)
                Surface.ROTATION_180 -> SensorManager.remapCoordinateSystem(r, SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y, out)
                Surface.ROTATION_270 -> SensorManager.remapCoordinateSystem(r, SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X, out)
                else -> { r.copyInto(out); true }
            }
        }
        if (!ok) r.copyInto(out)
        SensorManager.getOrientation(out, orientation)
        return QiblaMath.normalize(Math.toDegrees(orientation[0].toDouble()))
    }
}
