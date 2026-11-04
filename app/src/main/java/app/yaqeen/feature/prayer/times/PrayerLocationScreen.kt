package app.yaqeen.feature.prayer.times

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import app.yaqeen.AppContainer
import app.yaqeen.ui.components.ConfirmDialog
import app.yaqeen.ui.components.EmptyState
import app.yaqeen.ui.components.YaqeenCard
import app.yaqeen.ui.containerViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface GpsState {
    data object Idle : GpsState
    data object Fetching : GpsState
    data class Failed(val message: String, val action: GpsFailAction) : GpsState
}

enum class GpsFailAction { NONE, OPEN_LOCATION_SETTINGS, OPEN_APP_SETTINGS }

class PrayerLocationViewModel(private val c: AppContainer) : ViewModel() {
    private val allCities = MutableStateFlow<List<City>>(emptyList())
    val citiesLoaded = MutableStateFlow(false)
    val query = MutableStateFlow("")

    val results: StateFlow<List<City>> = combine(allCities, query) { all, q -> CityRepository.search(all, q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val current: StateFlow<ResolvedLocation?> = c.prayerTimes.observeLocation()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val gps = MutableStateFlow<GpsState>(GpsState.Idle)

    /** رسائل لمرة واحدة؛ [done] = true يعني اكتمل الاختيار ويجب الرجوع. */
    data class Event(val message: String, val done: Boolean)
    private val events = Channel<Event>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    init {
        viewModelScope.launch {
            allCities.value = runCatching { CityRepository.load(c.context) }.getOrDefault(emptyList())
            citiesLoaded.value = true
        }
    }

    fun setQuery(q: String) { query.value = q }

    fun permissionDenied(permanent: Boolean) {
        gps.value = if (permanent) {
            GpsState.Failed("إذن الموقع مرفوض. فعّله من إعدادات التطبيق، أو اختر مدينتك من القائمة أدناه.", GpsFailAction.OPEN_APP_SETTINGS)
        } else {
            GpsState.Failed("لم يُمنح إذن الموقع. يمكنك المحاولة مجددًا أو اختيار مدينتك من القائمة أدناه.", GpsFailAction.NONE)
        }
    }

    fun fetchGps() {
        if (gps.value == GpsState.Fetching) return
        gps.value = GpsState.Fetching
        viewModelScope.launch {
            when (val r = DeviceLocation.fetch(c.context)) {
                is DeviceLocationResult.Success -> {
                    c.prayerTimes.saveGpsLocation(r.latitude, r.longitude)
                    gps.value = GpsState.Idle
                    val name = c.prayerTimes.currentLocation()?.name ?: PrayerTimesService.CURRENT_LOCATION_NAME
                    events.send(
                        Event(
                            if (r.isStale) "استُخدم آخر موقع معروف للجهاز: $name" else "تم تحديد موقعك: $name",
                            done = true,
                        ),
                    )
                }
                DeviceLocationResult.NoPermission -> permissionDenied(permanent = false)
                DeviceLocationResult.ProviderDisabled -> gps.value = GpsState.Failed(
                    "خدمات الموقع معطّلة في جهازك. فعّلها ثم أعد المحاولة، أو اختر مدينتك يدويًا.", GpsFailAction.OPEN_LOCATION_SETTINGS,
                )
                DeviceLocationResult.Unavailable -> gps.value = GpsState.Failed(
                    "تعذّر تحديد موقعك الآن. جرّب الخروج إلى مكان مكشوف أو اختر مدينتك يدويًا.", GpsFailAction.NONE,
                )
            }
        }
    }

    fun pick(city: City) {
        viewModelScope.launch {
            c.prayerTimes.saveManualCity(city)
            events.send(Event("تم اختيار ${city.name}", done = true))
        }
    }

    fun clear() {
        viewModelScope.launch {
            c.prayerTimes.clearLocation()
            events.send(Event("تم مسح الموقع المحفوظ", done = false))
        }
    }
}

@Composable
fun PrayerLocationScreen(nav: NavHostController) {
    val vm = containerViewModel { PrayerLocationViewModel(it) }
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val results by vm.results.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val loaded by vm.citiesLoaded.collectAsStateWithLifecycle()
    val gps by vm.gps.collectAsStateWithLifecycle()
    val current by vm.current.collectAsStateWithLifecycle()
    var showRationale by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) {
            vm.fetchGps()
        } else {
            val activity = context.findActivity()
            val canAskAgain = activity != null && androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION)
            vm.permissionDenied(permanent = !canAskAgain)
        }
    }

    LaunchedEffect(Unit) {
        vm.eventFlow.collect { e ->
            if (e.done) {
                // الرسالة تُعرض كـToast لأن الشاشة تُغلق فورًا (الـSnackbar كان سيُقطع).
                android.widget.Toast.makeText(context, e.message, android.widget.Toast.LENGTH_SHORT).show()
                nav.popBackStack()
            } else {
                snackbar.showSnackbar(e.message)
            }
        }
    }

    fun onGpsClick() {
        if (DeviceLocation.hasPermission(context)) vm.fetchGps() else showRationale = true
    }

    PrayerScreenFrame(title = "اختيار الموقع", onBack = { nav.popBackStack() }, snackbar = snackbar) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "gps") {
                GpsCard(
                    gps = gps,
                    currentName = current?.name,
                    onUseGps = ::onGpsClick,
                    onOpenLocationSettings = { context.openLocationSettings() },
                    onOpenAppSettings = { context.openAppSettings() },
                    onClear = if (current != null) ({ confirmClear = true }) else null,
                )
            }
            item(key = "search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = vm::setQuery,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("ابحث عن مدينة أو دولة") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Filled.Close, "مسح") } },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
                )
            }
            if (!loaded) {
                item(key = "loading") { Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() } }
            } else if (results.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = "لا توجد نتائج",
                        message = "لم نجد مدينة بهذا الاسم في القائمة المدمجة. جرّب كتابة الاسم بطريقة أخرى أو ابحث باسم الدولة، أو استخدم تحديد الموقع التلقائي.",
                        icon = Icons.Filled.Search,
                    )
                }
            } else {
                // بلا بحث: تجميع بحسب الدولة بترتيب القائمة. مع البحث: قائمة مسطحة.
                val grouped = if (query.isBlank()) results.groupBy { it.country } else mapOf("" to results)
                grouped.forEach { (country, cities) ->
                    if (country.isNotEmpty()) {
                        item(key = "h_$country") {
                            Text(country, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
                        }
                    }
                    items(cities, key = { "c_${it.name}_${it.country}" }) { city ->
                        CityRow(
                            city = city,
                            showCountry = query.isNotBlank(),
                            selected = current?.isManual == true && current?.name == city.name,
                            onClick = { vm.pick(city) },
                        )
                    }
                }
            }
            item(key = "end") { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text("استخدام موقعك") },
            text = {
                Text(
                    "يحتاج التطبيق إلى موقعك التقريبي مرة واحدة لحساب مواقيت الصلاة واتجاه القبلة. " +
                        "يبقى الموقع محفوظًا على جهازك فقط ولا يُرسل إلى أي جهة، ولا يتتبع التطبيق حركتك.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }) { Text("متابعة") }
            },
            dismissButton = { TextButton(onClick = { showRationale = false }) { Text("ليس الآن") } },
        )
    }
    if (confirmClear) {
        ConfirmDialog(
            title = "مسح الموقع",
            message = "سيتوقف عرض المواقيت وتنبيهات الأذان حتى تحدد موقعًا جديدًا. هل تريد المتابعة؟",
            confirmText = "مسح",
            onConfirm = { vm.clear() },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun GpsCard(
    gps: GpsState,
    currentName: String?,
    onUseGps: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onClear: (() -> Unit)?,
) {
    YaqeenCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.MyLocation, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("تحديد موقعي تلقائيًا", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (currentName != null) "الموقع الحالي: $currentName" else "مرة واحدة فقط، دون تتبّع مستمر ودون إنترنت.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onUseGps, enabled = gps != GpsState.Fetching, modifier = Modifier.fillMaxWidth()) {
            if (gps == GpsState.Fetching) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
                Text("جارٍ تحديد الموقع…")
            } else {
                Text("استخدام موقعي الحالي")
            }
        }
        if (gps is GpsState.Failed) {
            Spacer(Modifier.height(8.dp))
            Text(gps.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            when (gps.action) {
                GpsFailAction.OPEN_LOCATION_SETTINGS -> OutlinedButton(onClick = onOpenLocationSettings, modifier = Modifier.padding(top = 4.dp)) { Text("فتح إعدادات الموقع") }
                GpsFailAction.OPEN_APP_SETTINGS -> OutlinedButton(onClick = onOpenAppSettings, modifier = Modifier.padding(top = 4.dp)) { Text("فتح إعدادات التطبيق") }
                GpsFailAction.NONE -> Unit
            }
        }
        if (onClear != null) TextButton(onClick = onClear) { Text("مسح الموقع المحفوظ", color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun CityRow(city: City, showCountry: Boolean, selected: Boolean, onClick: () -> Unit) {
    YaqeenCard(onClick = onClick, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(city.name, style = MaterialTheme.typography.titleSmall)
                if (showCountry) Text(city.country, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) Icon(Icons.Filled.Check, "المدينة الحالية", tint = MaterialTheme.colorScheme.primary)
        }
    }
}
