package app.yaqeen.feature.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** منتقي تاريخ ميلادي (Material3). يعيد التاريخ المختار بصيغة LocalDate. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialogAr(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { ms ->
                    onPick(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate())
                }
                onDismiss()
            }) { Text("تأكيد") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    ) { DatePicker(state = state) }
}

/** منتقي وقت: يعيد "HH:mm". (الساعة تُعرض بترتيب LTR المعتاد). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialogAr(initial: String?, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val t = ArabicFormat.parseTime(initial)
    val state = rememberTimePickerState(initialHour = t?.hour ?: 8, initialMinute = t?.minute ?: 0, is24Hour = false)
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("اختر الوقت", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp))
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { TimePicker(state = state) }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    TextButton(onClick = onDismiss) { Text("إلغاء") }
                    TextButton(onClick = {
                        onPick(ArabicFormat.formatHHmm(state.hour, state.minute))
                        onDismiss()
                    }) { Text("تأكيد") }
                }
            }
        }
    }
}
