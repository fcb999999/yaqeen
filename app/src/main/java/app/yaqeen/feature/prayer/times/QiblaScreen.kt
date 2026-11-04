package app.yaqeen.feature.prayer.times

import android.hardware.GeomagneticField
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate as rotateDraw
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import app.yaqeen.ui.components.EmptyState
import app.yaqeen.ui.components.LoadingBox
import app.yaqeen.ui.components.YaqeenCard
import app.yaqeen.ui.containerViewModel
import app.yaqeen.ui.theme.YaqeenTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.cos
import kotlin.math.sin

/** الحدّ (بالدرجات) الذي نعدّ عنده الجهاز متجهًا إلى القبلة. */
private const val ALIGN_TOLERANCE_DEG = 3.0

@Composable
fun QiblaScreen(nav: NavHostController) {
    val vm = containerViewModel { PrayerTimesViewModel(it) }
    val locState by vm.location.collectAsStateWithLifecycle()
    PrayerScreenFrame(title = "اتجاه القبلة", onBack = { nav.popBackStack() }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val st = locState) {
                LocState.Loading -> LoadingBox()
                LocState.None -> EmptyState(
                    title = "حدّد موقعك لمعرفة القبلة",
                    message = "نحسب اتجاه القبلة من موقعك على جهازك دون إنترنت.",
                    icon = Icons.Filled.LocationOn,
                    modifier = Modifier.align(Alignment.Center),
                    action = { Button(onClick = { nav.navigate(PrayerRoutes.LOCATION) }) { Text("تحديد الموقع") } },
                )
                is LocState.Ready -> QiblaContent(st.loc, onChangeLocation = { nav.navigate(PrayerRoutes.LOCATION) })
            }
        }
    }
}

@Composable
private fun QiblaContent(loc: ResolvedLocation, onChangeLocation: () -> Unit) {
    val context = LocalContext.current
    val bearing = remember(loc.latitude, loc.longitude) { QiblaMath.bearingDegrees(loc.latitude, loc.longitude) }
    val distance = remember(loc.latitude, loc.longitude) { QiblaMath.distanceKm(loc.latitude, loc.longitude) }
    val sensorAvailable = remember { CompassSensor.isAvailable(context) }
    // الشمال المغناطيسي ← الحقيقي بإضافة الانحراف المغناطيسي لموقعك.
    val declination = remember(loc.latitude, loc.longitude) {
        runCatching { GeomagneticField(loc.latitude.toFloat(), loc.longitude.toFloat(), 0f, System.currentTimeMillis()).declination.toDouble() }.getOrDefault(0.0)
    }

    // اتجاه الجهاز (متصل بلا قفزات عند 0/360) والدقة.
    var heading by remember { mutableDoubleStateOf(0.0) }
    var gotReading by remember { mutableStateOf(false) }
    var accuracy by remember { mutableIntStateOf(SensorManager.SENSOR_STATUS_ACCURACY_HIGH) }
    var hintDismissed by remember { mutableStateOf(false) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val declRef by rememberUpdatedState(declination)
    LaunchedEffect(lifecycle, sensorAvailable) {
        if (!sensorAvailable) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val filter = CircularLowPass(0.15)
            CompassSensor.readings(context).collect { r ->
                val smooth = filter.update(r.azimuth + declRef)
                // نحافظ على استمرارية الدوران: نضيف أقصر فرق للقيمة المتصلة السابقة.
                heading += QiblaMath.signedDelta(QiblaMath.normalize(heading), smooth)
                accuracy = r.accuracy
                gotReading = true
            }
        }
    }

    // إبقاء الشاشة مضاءة أثناء استخدام البوصلة.
    val view = LocalView.current
    DisposableEffect(Unit) {
        val prev = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = prev }
    }

    val shownHeading = if (sensorAvailable && gotReading) heading else 0.0
    val delta = QiblaMath.signedDelta(QiblaMath.normalize(shownHeading), bearing)
    val aligned = sensorAvailable && gotReading && kotlin.math.abs(delta) <= ALIGN_TOLERANCE_DEG

    val haptics = LocalHapticFeedback.current
    var wasAligned by remember { mutableStateOf(false) }
    LaunchedEffect(aligned) {
        if (aligned && !wasAligned) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        wasAligned = aligned
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.LocationOn, null, tint = MaterialTheme.colorScheme.primary)
            Text(" ${loc.name}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onChangeLocation) { Text("تغيير الموقع") }
        }

        YaqeenCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("القبلة من موقعك", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${PrayerFormat.degrees(bearing)} (${QiblaMath.directionName(bearing)})",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    "المسافة إلى الكعبة المشرفة: ${PrayerFormat.km(distance)}",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        CompassDial(
            heading = if (sensorAvailable) shownHeading else 0.0,
            qiblaBearing = bearing,
            aligned = aligned,
            modifier = Modifier.fillMaxWidth().widthIn(max = 340.dp).aspectRatio(1f),
        )

        when {
            !sensorAvailable -> YaqeenCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, null, tint = YaqeenTheme.extra.warning)
                    Text(
                        "  جهازك لا يحتوي على حساس البوصلة اللازم. الشمال في أعلى الشاشة، والسهم الذهبي يشير إلى زاوية القبلة المحسوبة؛ استعن ببوصلة خارجية أو بخرائط الجهاز.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            !gotReading -> Text("جارٍ قراءة البوصلة…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            aligned -> Text("أنت تواجه القبلة", style = MaterialTheme.typography.titleLarge, color = YaqeenTheme.extra.success)
            else -> {
                val turn = kotlin.math.abs(delta).toInt()
                Text(
                    "أدِر الجهاز نحو ${if (delta > 0) "اليمين" else "اليسار"} بمقدار ${app.yaqeen.core.ArabicText.indic(turn)}°",
                    style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
                )
            }
        }

        if (sensorAvailable && gotReading && accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW && !hintDismissed) {
            YaqeenCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
                Text(
                    "دقة البوصلة منخفضة. حرّك الجهاز في الهواء على شكل رقم ٨ عدة مرات لمعايرته، وابتعد عن المعادن والمغناطيس والأجهزة الكهربائية.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                TextButton(onClick = { hintDismissed = true }) { Text("تجاهل") }
            }
        }

        Text(
            "ضع الجهاز أفقيًا ثابتًا بعيدًا عن المعادن. يعمل دون إنترنت، وتُحسب الزاوية بالدائرة الكبرى من موقعك إلى الكعبة.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
    }
}

/** قرص البوصلة: يدور عكس اتجاه الجهاز فيبقى الشمال نحو الشمال الحقيقي، وسهم القبلة الذهبي مثبّت على زاويته. */
@Composable
private fun CompassDial(heading: Double, qiblaBearing: Double, aligned: Boolean, modifier: Modifier = Modifier) {
    val ring = MaterialTheme.colorScheme.outline
    val tick = MaterialTheme.colorScheme.onSurfaceVariant
    val north = MaterialTheme.colorScheme.error
    val face = MaterialTheme.colorScheme.surface
    val gold = YaqeenTheme.extra.gold
    val success = YaqeenTheme.extra.success
    val onSurface = MaterialTheme.colorScheme.onSurface
    val pointer = if (aligned) success else MaterialTheme.colorScheme.primary
    val qiblaColor = if (aligned) success else gold

    Box(modifier, contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().rotate((-heading).toFloat()), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val c = center
                val r = size.minDimension / 2f - 6.dp.toPx()
                drawCircle(face, r)
                drawCircle(if (aligned) success else ring, r, style = Stroke(width = if (aligned) 5.dp.toPx() else 2.dp.toPx()))
                // التدريج: كل ١٠° خط قصير، وكل ٣٠° خط أطول.
                for (deg in 0 until 360 step 10) {
                    val long = deg % 30 == 0
                    val len = if (long) 14.dp.toPx() else 7.dp.toPx()
                    val a = Math.toRadians(deg.toDouble())
                    val s = Offset(c.x + (r - len) * sin(a).toFloat(), c.y - (r - len) * cos(a).toFloat())
                    val e = Offset(c.x + r * sin(a).toFloat(), c.y - r * cos(a).toFloat())
                    drawLine(if (deg == 0) north else tick, s, e, strokeWidth = if (long) 2.dp.toPx() else 1.dp.toPx(), cap = StrokeCap.Round)
                }
                // علامة الشمال (مثلث أحمر صغير).
                val northMark = Path().apply {
                    moveTo(c.x, c.y - r + 18.dp.toPx())
                    lineTo(c.x - 7.dp.toPx(), c.y - r + 34.dp.toPx())
                    lineTo(c.x + 7.dp.toPx(), c.y - r + 34.dp.toPx())
                    close()
                }
                drawPath(northMark, north)
                // سهم القبلة وأيقونة الكعبة على الحافة.
                rotateDraw(qiblaBearing.toFloat(), c) {
                    drawLine(qiblaColor, Offset(c.x, c.y), Offset(c.x, c.y - r + 44.dp.toPx()), strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
                    drawKaaba(Offset(c.x, c.y - r + 30.dp.toPx()), 22.dp.toPx(), onSurface, qiblaColor)
                }
                drawCircle(qiblaColor, 7.dp.toPx(), c)
            }
            // الجهات الأربع (تدور مع القرص).
            Text("ش", Modifier.align(Alignment.TopCenter).padding(top = 40.dp), color = north, fontSize = 16.sp, style = MaterialTheme.typography.titleMedium)
            Text("ج", Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp), color = tick, style = MaterialTheme.typography.titleMedium)
            Text("ق", Modifier.align(Alignment.CenterStart).padding(start = 26.dp), color = tick, style = MaterialTheme.typography.titleMedium)
            Text("غ", Modifier.align(Alignment.CenterEnd).padding(end = 26.dp), color = tick, style = MaterialTheme.typography.titleMedium)
        }
        // مؤشر ثابت أعلى القرص: الاتجاه الذي يواجهه الجهاز.
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = size.minDimension / 2f
            val p = Path().apply {
                moveTo(c.x, c.y - r + 14.dp.toPx())
                lineTo(c.x - 9.dp.toPx(), c.y - r - 2.dp.toPx())
                lineTo(c.x + 9.dp.toPx(), c.y - r - 2.dp.toPx())
                close()
            }
            drawPath(p, pointer)
        }
    }
}

/** رسم مبسّط للكعبة: مكعب داكن بشريط ذهبي. */
private fun DrawScope.drawKaaba(topCenter: Offset, size: Float, body: Color, band: Color) {
    val half = size / 2f
    val tl = Offset(topCenter.x - half, topCenter.y - half)
    drawRect(body, tl, Size(size, size))
    drawRect(band, Offset(tl.x, tl.y + size * 0.28f), Size(size, size * 0.14f))
}
