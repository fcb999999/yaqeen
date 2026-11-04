package app.yaqeen.feature.quran.bookmarks

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import app.yaqeen.AppContainer
import app.yaqeen.ui.PlaceholderScreen

/**
 * === عقد العلامات المرجعية (يملكه منفذ "الأوراد والعلامات والصوت والتفسير") ===
 * القارئ يفتح [BookmarkEditorSheet] عند الضغط على "علامة" لآية.
 */
@Composable
fun BookmarksScreen(nav: NavHostController, c: AppContainer) {
    PlaceholderScreen("العلامات المرجعية")
}

/**
 * نافذة سفلية لإضافة/تعديل/حذف علامة على آية (عنوان، ملاحظة، لون، تصنيف).
 * إن كانت للآية علامة موجودة تُعرض للتعديل.
 */
@Composable
fun BookmarkEditorSheet(c: AppContainer, surah: Int, ayah: Int, onDismiss: () -> Unit) {
    onDismiss()
}
