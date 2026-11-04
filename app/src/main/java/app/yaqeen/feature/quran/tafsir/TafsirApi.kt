package app.yaqeen.feature.quran.tafsir

import androidx.compose.runtime.Composable
import app.yaqeen.AppContainer

/**
 * === عقد التفسير (يملكه منفذ "الأوراد والعلامات والصوت والتفسير") ===
 * القارئ يفتح [TafsirSheet] لآية. التفسير الميسر مضمَّن في assets/tafsir/ar.muyassar.json والبقية قابلة للتنزيل.
 */
@Composable
fun TafsirSheet(c: AppContainer, ayahId: Int, onDismiss: () -> Unit) {
    onDismiss()
}
