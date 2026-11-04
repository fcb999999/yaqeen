package app.yaqeen.feature.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import app.yaqeen.AppContainer
import app.yaqeen.ui.components.Pill
import app.yaqeen.ui.components.YaqeenCard
import app.yaqeen.ui.components.YaqeenTopBar
import app.yaqeen.ui.containerViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TaskPresetsVm(private val c: AppContainer) : ViewModel() {
    private val repo = TasksRepository(c)

    val added: StateFlow<Set<String>> =
        repo.observePresetKeys().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun add(p: TaskPreset) {
        viewModelScope.launch { runCatching { repo.addPreset(p) } }
    }
}

@Composable
fun TaskPresetsScreen(nav: NavHostController) {
    val vm = containerViewModel { TaskPresetsVm(it) }
    val added by vm.added.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        YaqeenTopBar("المهام الجاهزة", onBack = { nav.popBackStack() })
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "intro") {
                Text(
                    "أضف ما يناسبك بنقرة واحدة. المهام المرتبطة بعبادة داخل التطبيق يُحتسب إنجازها تلقائيًا من سجلّك الفعلي، ويمكنك تعديل وقتها وتذكيرها لاحقًا.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(TaskPreset.values().toList(), key = { it.name }) { p ->
                val isAdded = p.oneTap && p.name in added
                YaqeenCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.titleAr, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(2.dp))
                            Text(p.descriptionAr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(12.dp))
                        when {
                            isAdded -> Pill("مضافة")
                            p.oneTap -> Button(onClick = { vm.add(p) }) { Text("إضافة") }
                            else -> OutlinedButton(onClick = { nav.navigate(TasksRoutes.editor(preset = p.name)) }) { Text("إنشاء") }
                        }
                    }
                }
            }
            item(key = "custom") {
                OutlinedButton(onClick = { nav.navigate(TasksRoutes.editor()) }, modifier = Modifier.fillMaxWidth()) { Text("مهمة مخصصة من الصفر") }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
