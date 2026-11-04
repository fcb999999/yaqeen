package app.yaqeen.feature.prayer.tracking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.yaqeen.AppContainer
import app.yaqeen.domain.model.Prayer
import app.yaqeen.domain.model.PrayerStatus
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * صف مصغّر لتسجيل الصلوات الخمس ليوم [date] (للصفحة الرئيسية وغيرها).
 * كل صلاة شريحة بحالتها الحالية؛ النقر يفتح قائمة صغيرة: مسجد / بيت / لم أصلِّ بعد / مسح التسجيل.
 * الكتابة عبر [PrayerTrackingRepository] نفسه الذي تستعمله صفحة الصلاة (فلا تختلف النتائج).
 * الأيام المستقبلية معطّلة.
 */
@Composable
fun PrayerQuickRow(c: AppContainer, date: LocalDate, modifier: Modifier = Modifier) {
    val repo = remember(c) { PrayerTrackingRepository(c) }
    val statuses by remember(repo, date) { repo.observeDay(date) }.collectAsState(initial = emptyMap())
    var menuFor by remember { mutableStateOf<Prayer?>(null) }
    val enabled = repo.canEdit(date)

    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Prayer.entries.forEach { prayer ->
            val status = statuses[prayer]
            Box(Modifier.weight(1f)) {
                QuickChip(
                    prayer = prayer,
                    status = status,
                    enabled = enabled,
                    onClick = { menuFor = prayer },
                )
                DropdownMenu(expanded = menuFor == prayer, onDismissRequest = { menuFor = null }) {
                    PrayerStatus.entries.forEach { s ->
                        DropdownMenuItem(
                            text = { Text(s.ar) },
                            leadingIcon = { Icon(statusIcon(s), null, tint = statusAccent(s)) },
                            trailingIcon = if (status == s) ({ Icon(Icons.Filled.Check, contentDescription = "المحدد حاليًا") }) else null,
                            onClick = {
                                menuFor = null
                                c.appScope.launch { repo.setStatus(date, prayer, s) }
                            },
                        )
                    }
                    if (status != null) {
                        DropdownMenuItem(
                            text = { Text("مسح التسجيل") },
                            leadingIcon = { Icon(Icons.Filled.Close, null) },
                            onClick = {
                                menuFor = null
                                c.appScope.launch { repo.setStatus(date, prayer, null) }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickChip(prayer: Prayer, status: PrayerStatus?, enabled: Boolean, onClick: () -> Unit) {
    val description = "${prayer.ar}: ${status?.ar ?: "غير مسجّلة"}"
    Column(
        Modifier.fillMaxWidth().semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = MaterialTheme.shapes.small,
            color = statusContainer(status),
            contentColor = statusOnContainer(status),
            border = BorderStroke(1.dp, if (status != null) statusAccent(status) else MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.size(width = 52.dp, height = 44.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    statusIcon(status),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = if (status != null) statusAccent(status) else MaterialTheme.colorScheme.outline,
                )
            }
        }
        Spacer(Modifier.size(4.dp))
        Text(prayer.ar, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
