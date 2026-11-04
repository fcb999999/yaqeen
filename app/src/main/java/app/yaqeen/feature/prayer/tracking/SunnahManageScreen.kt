package app.yaqeen.feature.prayer.tracking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.yaqeen.AppContainer
import app.yaqeen.ui.components.ConfirmDialog
import app.yaqeen.ui.components.EmptyState
import app.yaqeen.ui.components.Pill
import app.yaqeen.ui.components.YaqeenCard
import app.yaqeen.ui.components.YaqeenTopBar
import kotlinx.coroutines.launch

/** إدارة السنن المخصصة: إضافة، تعديل الاسم/عدد الركعات، أرشفة، حذف. */
@Composable
fun SunnahManageScreen(nav: NavHostController, c: AppContainer) {
    val repo = remember(c) { PrayerTrackingRepository(c) }
    val items by remember(repo) { repo.observeCustomItems() }.collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    var editing by remember { mutableStateOf<SunnahItem?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<SunnahItem?>(null) }

    Column(Modifier.fillMaxSize()) {
        YaqeenTopBar("السنن المخصصة", onBack = { nav.popBackStack() })
        val list = items
        if (list == null) {
            app.yaqeen.ui.components.LoadingBox()
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "intro") {
                    Text(
                        "أضف سننًا أو نوافل تحرص عليها (كالضحى والوتر) لتظهر في قائمة يومك مع السنن الرواتب. " +
                            "الأرشفة تخفي السنة من قائمة اليوم وتُبقي سجلاتها السابقة في الإحصاءات.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item(key = "add") {
                    Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("إضافة سنة")
                    }
                }
                if (list.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            title = "لم تضف سننًا مخصصة بعد",
                            message = "السنن الرواتب الخمس موجودة دائمًا في قائمة يومك.",
                        )
                    }
                }
                items(list, key = { it.id }) { item ->
                    YaqeenCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(item.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    PrayerFormat.rakaat(item.rakaat),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (item.archived) Pill("مؤرشفة")
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            IconButton(onClick = { editing = item }) { Icon(Icons.Filled.Edit, contentDescription = "تعديل ${item.name}") }
                            IconButton(onClick = { scope.launch { repo.setArchived(item.id, !item.archived) } }) {
                                Icon(
                                    if (item.archived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                                    contentDescription = if (item.archived) "إلغاء أرشفة ${item.name}" else "أرشفة ${item.name}",
                                )
                            }
                            IconButton(onClick = { deleting = item }) { Icon(Icons.Filled.Delete, contentDescription = "حذف ${item.name}") }
                        }
                    }
                }
                item(key = "bottom") { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    if (adding) {
        SunnahEditDialog(
            title = "إضافة سنة",
            initialName = "",
            initialRakaat = 2,
            onDismiss = { adding = false },
            onSave = { name, rakaat -> repo.addCustomSunnah(name, rakaat) },
        )
    }
    editing?.let { item ->
        SunnahEditDialog(
            title = "تعديل السنة",
            initialName = item.name,
            initialRakaat = item.rakaat,
            onDismiss = { editing = null },
            onSave = { name, rakaat -> repo.updateCustomSunnah(item.customEntityId.orEmpty(), name, rakaat) },
        )
    }
    deleting?.let { item ->
        ConfirmDialog(
            title = "حذف السنة",
            message = "ستُحذف «${item.name}» من قائمتك. تبقى سجلاتها السابقة محفوظة في الإحصاءات. هل تريد المتابعة؟",
            confirmText = "حذف",
            onConfirm = { scope.launch { repo.deleteCustomSunnah(item.customEntityId.orEmpty()) } },
            onDismiss = { deleting = null },
        )
    }
}

/** حوار إضافة/تعديل: اسم + عدد الركعات. [onSave] يعيد رسالة خطأ أو null عند النجاح. */
@Composable
private fun SunnahEditDialog(
    title: String,
    initialName: String,
    initialRakaat: Int,
    onDismiss: () -> Unit,
    onSave: suspend (String, Int) -> String?,
) {
    var name by remember { mutableStateOf(initialName) }
    var rakaat by remember { mutableStateOf(initialRakaat) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= SunnahModel.MAX_NAME_LENGTH + 10) { name = it; error = null } },
                    label = { Text("اسم السنة") },
                    singleLine = true,
                    isError = error != null,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("عدد الركعات", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { if (rakaat > SunnahModel.MIN_RAKAAT) { rakaat--; error = null } }) {
                        Icon(Icons.Filled.Remove, contentDescription = "إنقاص عدد الركعات")
                    }
                    Text(app.yaqeen.core.ArabicText.indic(rakaat), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 8.dp))
                    IconButton(onClick = { if (rakaat < SunnahModel.MAX_RAKAAT) { rakaat++; error = null } }) {
                        Icon(Icons.Filled.Add, contentDescription = "زيادة عدد الركعات")
                    }
                }
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    saving = true
                    scope.launch {
                        val result = runCatching { onSave(name, rakaat) }.getOrElse { "تعذّر الحفظ، حاول مرة أخرى" }
                        saving = false
                        if (result == null) onDismiss() else error = result
                    }
                },
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("إلغاء") } },
    )
}
