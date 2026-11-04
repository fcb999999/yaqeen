package app.yaqeen.feature.quran.audio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.yaqeen.core.ArabicText
import app.yaqeen.data.settings.Settings

/**
 * خيارات التلاوة: اختيار القارئ ونمط التكرار (بلا تكرار / تكرار الآية عددًا من المرات / تكرار السورة).
 * يمكن فتحها من شريط التلاوة المصغّر أو من أي مكان في القارئ.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioOptionsSheet(controller: QuranAudioController, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val c = controller.container
    val reciterId by c.settings.observe(Settings.QURAN_RECITER).collectAsState(initial = Reciters.DEFAULT_ID)
    val kind by c.settings.observe(AudioSettings.REPEAT_KIND).collectAsState(initial = AudioRepeat.NONE.name)
    val times by c.settings.observe(AudioSettings.REPEAT_TIMES).collectAsState(initial = 3)
    val repeat = runCatching { AudioRepeat.valueOf(kind) }.getOrDefault(AudioRepeat.NONE)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item("repeat-title") { Text("التكرار", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
            item("repeat-kinds") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = repeat == AudioRepeat.NONE, onClick = { controller.setRepeat(AudioRepeat.NONE) }, label = { Text("بلا تكرار") })
                    FilterChip(selected = repeat == AudioRepeat.AYAH, onClick = { controller.setRepeat(AudioRepeat.AYAH, times) }, label = { Text("تكرار الآية") })
                    FilterChip(selected = repeat == AudioRepeat.SURAH, onClick = { controller.setRepeat(AudioRepeat.SURAH, times) }, label = { Text("تكرار السورة") })
                }
            }
            if (repeat == AudioRepeat.AYAH) {
                item("repeat-times-label") { Text("عدد مرات تكرار كل آية", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp)) }
                item("repeat-times") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(AudioSettings.REPEAT_CHOICES) { n ->
                            FilterChip(selected = times == n, onClick = { controller.setRepeat(AudioRepeat.AYAH, n) }, label = { Text(ArabicText.indic(n)) })
                        }
                    }
                }
            }
            item("reciter-title") {
                Spacer(Modifier.height(12.dp))
                Text("القارئ", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            items(Reciters.all, key = { it.id }) { r ->
                Row(
                    Modifier.fillMaxWidth().clickable { controller.setReciter(r.id) }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = r.id == Reciters.byId(reciterId).id, onClick = { controller.setReciter(r.id) })
                    Spacer(Modifier.width(4.dp))
                    Text(r.nameAr, style = MaterialTheme.typography.bodyLarge)
                }
            }
            item("end") { Spacer(Modifier.height(32.dp)) }
        }
    }
}
