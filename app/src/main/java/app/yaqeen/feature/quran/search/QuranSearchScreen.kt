package app.yaqeen.feature.quran.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.yaqeen.AppContainer
import app.yaqeen.core.ArabicText
import app.yaqeen.data.quran.Ayah
import app.yaqeen.data.quran.QuranData
import app.yaqeen.feature.quran.reader.QuranLoadGate
import app.yaqeen.feature.quran.reader.QuranText
import app.yaqeen.feature.quran.reader.SearchHighlight
import app.yaqeen.feature.quran.reader.rememberQuranLoad
import app.yaqeen.ui.components.EmptyState
import app.yaqeen.ui.components.YaqeenCard
import app.yaqeen.ui.components.YaqeenTopBar
import app.yaqeen.ui.navigation.Routes
import app.yaqeen.ui.theme.YaqeenTextStyles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val RESULT_LIMIT = 200

private sealed interface SearchState {
    data object Idle : SearchState
    data object Searching : SearchState
    data class Done(val query: String, val hits: List<Ayah>) : SearchState
}

/** بحث نصي في المصحف (لا يتأثر بالتشكيل) مع إبراز الكلمات المطابقة؛ النقر يفتح القارئ عند الآية. */
@Composable
fun QuranSearchScreen(nav: NavHostController, c: AppContainer) {
    var retry by remember { mutableIntStateOf(0) }
    val load by rememberQuranLoad(c, retry)
    var query by rememberSaveable { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        YaqeenTopBar(title = "البحث في القرآن", onBack = { nav.popBackStack() })
        QuranLoadGate(load, onRetry = { retry++ }) { quran ->
            SearchBody(nav, quran, query) { query = it }
        }
    }
}

@Composable
private fun SearchBody(nav: NavHostController, quran: QuranData, query: String, onQuery: (String) -> Unit) {
    var state by remember { mutableStateOf<SearchState>(SearchState.Idle) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(query) {
        if (query.isBlank()) {
            state = SearchState.Idle
            return@LaunchedEffect
        }
        if (state !is SearchState.Done) state = SearchState.Searching // نُبقي النتائج السابقة ظاهرة أثناء الكتابة
        delay(250) // انتظار انتهاء الكتابة
        val hits = withContext(Dispatchers.Default) { quran.search(query, RESULT_LIMIT) }
        state = SearchState.Done(query, hits)
    }

    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        singleLine = true,
        placeholder = { Text("اكتب كلمة أو أكثر من الآية") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Close, contentDescription = "مسح") }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .focusRequester(focus),
    )

    when (val st = state) {
        SearchState.Idle -> EmptyState(
            title = "ابحث في القرآن الكريم",
            message = "اكتب كلمة أو أكثر؛ لا يؤثر التشكيل ولا اختلاف شكل الألف والياء والهمزة على النتيجة.",
            icon = Icons.Filled.Search,
        )
        SearchState.Searching -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
        is SearchState.Done -> if (st.hits.isEmpty()) {
            EmptyState(
                title = "لا توجد نتائج",
                message = "لم نجد آيات تحتوي كل هذه الكلمات. جرّب كلمات أقل أو إملاءً آخر.",
                icon = Icons.Filled.SearchOff,
            )
        } else {
            Results(nav, quran, st)
        }
    }
}

@Composable
private fun Results(nav: NavHostController, quran: QuranData, st: SearchState.Done) {
    val count = ArabicText.indic(st.hits.size)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                if (st.hits.size >= RESULT_LIMIT) "عرض أول $count نتيجة؛ ضيّق البحث بكلمة إضافية" else "$count نتيجة",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(st.hits, key = { it.id }) { a ->
            ResultCard(quran, a, st.query) {
                nav.navigate(Routes.quranReader(surah = a.surah, ayah = a.number)) {
                    popUpTo(Routes.QURAN_READER_PATTERN) { inclusive = true }
                }
            }
        }
    }
}

@Composable
private fun ResultCard(quran: QuranData, a: Ayah, query: String, onClick: () -> Unit) {
    val hlBg = MaterialTheme.colorScheme.tertiaryContainer
    val hlFg = MaterialTheme.colorScheme.onTertiaryContainer
    val text = remember(a.id, query, hlBg, hlFg) {
        val ranges = SearchHighlight.ranges(a.text, query)
        buildAnnotatedString {
            append(a.text)
            for (r in ranges) addStyle(SpanStyle(background = hlBg, color = hlFg), r.first, r.last + 1)
        }
    }
    YaqeenCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Text(
            "سورة ${QuranText.plainSurahName(quran.surah(a.surah).nameAr)} – الآية ${ArabicText.indic(a.number)} – صفحة ${ArabicText.indic(a.page)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text,
            style = YaqeenTextStyles.quran(20f, 1.8f).copy(color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Start),
            maxLines = 8,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
