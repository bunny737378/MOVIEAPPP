package com.xtsream.app.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput 
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.xtsream.app.AppDarkBg
import com.xtsream.app.DataFetch
import com.xtsream.app.data.MediaItem
import com.xtsream.app.manage.ProviderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

val SearchSuggestIcon: ImageVector
    get() = ImageVector.Builder("search_suggest", 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Color.White), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(10f, 3f); arcTo(7f, 7f, 0f, isMoreThanHalf = true, isPositiveArc = false, 10f, 17f); arcTo(7f, 7f, 0f, isMoreThanHalf = true, isPositiveArc = false, 10f, 3f); close()
            moveTo(21f, 21f); lineTo(15f, 15f)
        }
    }.build()

val ArrowBackVector: ImageVector
    get() = ImageVector.Builder("arrow_back", 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Color.White), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(15f, 6f); lineTo(9f, 12f); lineTo(15f, 18f)
        }
    }.build()

val GridViewIcon: ImageVector
    get() = ImageVector.Builder("grid_view", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(3f, 3f); lineTo(11f, 3f); lineTo(11f, 11f); lineTo(3f, 11f); close()
            moveTo(13f, 3f); lineTo(21f, 3f); lineTo(21f, 11f); lineTo(13f, 11f); close()
            moveTo(3f, 13f); lineTo(11f, 13f); lineTo(11f, 21f); lineTo(3f, 21f); close()
            moveTo(13f, 13f); lineTo(21f, 13f); lineTo(21f, 21f); lineTo(13f, 21f); close()
        }
    }.build()

val ListViewIcon: ImageVector
    get() = ImageVector.Builder("list_view", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(3f, 4f); lineTo(21f, 4f); lineTo(21f, 7f); lineTo(3f, 7f); close()
            moveTo(3f, 10.5f); lineTo(21f, 10.5f); lineTo(21f, 13.5f); lineTo(3f, 13.5f); close()
            moveTo(3f, 17f); lineTo(21f, 17f); lineTo(21f, 20f); lineTo(3f, 20f); close()
        }
    }.build()

data class SearchMode(val name: String, val field: String)
data class CachedSearchResult(val items: List<MediaItem>, val nextPage: String, val hasMore: Boolean)

class SearchViewModel : ViewModel() {
    var query by mutableStateOf("")
    var isSearchFocused by mutableStateOf(true)

    var availableModes by mutableStateOf(listOf(SearchMode("ALL", "allsearch")))
    var hasQSearch by mutableStateOf(false)
    var selectedMode by mutableStateOf(availableModes.first())

    var quickResults by mutableStateOf<List<String>>(emptyList())
    var gridResults by mutableStateOf<List<MediaItem>>(emptyList())

    var isLoadingGrid by mutableStateOf(false)
    var isLoadingMore by mutableStateOf(false)
    var nextSearchPage by mutableStateOf("1")
    var hasMoreResults by mutableStateOf(true)

    var isGridView by mutableStateOf(true)

    private var qJob: Job? = null
    private var searchJob: Job? = null
    private val modeResultsCache = mutableMapOf<String, CachedSearchResult>()
    private var currentProviderId: String? = null

    // 🔥 RE-LOADS APISLIST WHENEVER PROVIDER CHANGES 🔥
    fun reloadForProvider(providerId: String?) {
        if (providerId == null || this.currentProviderId == providerId) return
        this.currentProviderId = providerId
        clearCache()
        fetchAvailableModes()
    }

    fun clearCache() {
        modeResultsCache.clear()
        query = ""
        isSearchFocused = true
        quickResults = emptyList()
        gridResults = emptyList()
        isLoadingGrid = false
        isLoadingMore = false
        nextSearchPage = "1"
        hasMoreResults = true
    }

    private fun fetchAvailableModes() {
        val pid = currentProviderId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resp = DataFetch.fetch(pid, """{"request_type":"apislist"}""")
                val modes = mutableListOf(SearchMode("ALL", "allsearch"))
                var qSearchEnabled = false
                try {
                    val root = JSONObject(resp)
                    val apis = root.optJSONObject("apis")
                    if (apis != null) {
                        qSearchEnabled = apis.optBoolean("qsearch", false)
                        val searchtext = apis.optJSONArray("searchtext")
                        if (searchtext != null) {
                            for (i in 0 until searchtext.length()) {
                                val obj = searchtext.getJSONObject(i)
                                val name = obj.optString("name", "")
                                val field = obj.optString("field", "")
                                if (name.isNotBlank() && field.isNotBlank()) modes.add(SearchMode(name, field))
                            }
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }

                withContext(Dispatchers.Main) {
                    hasQSearch = qSearchEnabled
                    availableModes = modes
                    if (!availableModes.contains(selectedMode)) selectedMode = availableModes.first()
                }
            } catch (e: Exception) {}
        }
    }

    fun onQueryChange(newQuery: String) {
        query = newQuery
        if (newQuery.isBlank()) {
            quickResults = emptyList()
            qJob?.cancel()
            return
        }
        val pid = currentProviderId ?: return
        if (hasQSearch) {
            qJob?.cancel()
            qJob = viewModelScope.launch(Dispatchers.IO) {
                delay(400)
                ensureActive()
                try {
                    val req = """{"request_type":"qsearch", "searchKey":"$query", "page":"1"}"""
                    val resp = DataFetch.fetch(pid, req)
                    ensureActive()
                    val (parsed, _) = parseMediaWithNextPage(resp, pid)
                    withContext(Dispatchers.Main) { quickResults = parsed.map { it.title }.distinct() }
                } catch (e: Exception) {}
            }
        }
    }

    fun selectMode(mode: SearchMode) {
        if (selectedMode == mode) return
        selectedMode = mode
        performFullSearch()
    }

    fun performFullSearch() {
        if (query.isBlank()) return
        qJob?.cancel()
        isSearchFocused = false

        val pid = currentProviderId ?: return
        val cacheKey = "${selectedMode.field}_${query.trim().lowercase()}"
        if (modeResultsCache.containsKey(cacheKey)) {
            val cached = modeResultsCache[cacheKey]!!
            gridResults = cached.items
            nextSearchPage = cached.nextPage
            hasMoreResults = cached.hasMore
            isLoadingGrid = false
            return
        }

        isLoadingGrid = true
        gridResults = emptyList()
        nextSearchPage = "1"
        hasMoreResults = true

        searchJob?.cancel()
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val req = """{"request_type":"${selectedMode.field}", "searchKey":"$query", "page":"1"}"""
                val resp = DataFetch.fetch(pid, req)
                ensureActive()
                val (parsed, nextPg) = parseMediaWithNextPage(resp, pid)
                val hasMore = parsed.isNotEmpty() && nextPg.isNotBlank()

                withContext(Dispatchers.Main) {
                    gridResults = parsed
                    nextSearchPage = nextPg
                    hasMoreResults = hasMore
                    modeResultsCache[cacheKey] = CachedSearchResult(parsed, nextPg, hasMore)
                }
            } catch (e: Exception) {}
            withContext(Dispatchers.Main) { isLoadingGrid = false }
        }
    }

    fun loadMoreResults() {
        if (isLoadingMore || !hasMoreResults || query.isBlank() || nextSearchPage.isBlank()) return
        isLoadingMore = true
        val pid = currentProviderId ?: return
        val cacheKey = "${selectedMode.field}_${query.trim().lowercase()}"

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val req = """{"request_type":"${selectedMode.field}", "searchKey":"$query", "page":"$nextSearchPage"}"""
                val resp = DataFetch.fetch(pid, req)
                ensureActive()
                val (parsed, nextPg) = parseMediaWithNextPage(resp, pid)
                withContext(Dispatchers.Main) {
                    if (parsed.isNotEmpty()) {
                        val updatedList = gridResults + parsed
                        gridResults = updatedList
                        nextSearchPage = nextPg
                        val hasMore = nextPg.isNotBlank()
                        hasMoreResults = hasMore
                        modeResultsCache[cacheKey] = CachedSearchResult(updatedList, nextPg, hasMore)
                    } else hasMoreResults = false
                }
            } catch (e: Exception) {}
            withContext(Dispatchers.Main) { isLoadingMore = false }
        }
    }

    private fun parseMediaWithNextPage(jsonStr: String, pid: String): Pair<List<MediaItem>, String> {
        val list = mutableListOf<MediaItem>()
        var nextPg = ""
        try {
            val root = JSONObject(jsonStr)
            if (root.has("nextpage")) nextPg = root.optString("nextpage", "")
            if (root.has("data")) {
                val dataArr = root.getJSONArray("data")
                for (i in 0 until dataArr.length()) {
                    val item = dataArr.getJSONObject(i)
                    list.add(MediaItem(item.optString("id", ""), item.optString("Img", ""), item.optString("titel", ""), item.optString("Type", ""), pid))
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return Pair(list, nextPg)
    }
}

@Composable
fun SearchUI(onBack: () -> Unit, onMediaClick: (MediaItem) -> Unit) {
    val vm: SearchViewModel = viewModel()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val currentPid = ProviderManager.currentProvider?.id

    // 🔥 REFRESHES MODES DYNAMICALLY WHEN PROVIDER CHANGES 🔥
    LaunchedEffect(currentPid) {
        vm.reloadForProvider(currentPid)
    }

    LaunchedEffect(Unit) {
        if (vm.query.isEmpty()) { delay(100); focusRequester.requestFocus() }
    }

    Column(modifier = Modifier.fillMaxSize().background(AppDarkBg).statusBarsPadding().pointerInput(Unit){}) {
        Row(modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(36.dp).clickable { onBack() }, contentAlignment = Alignment.Center) {
                Icon(imageVector = ArrowBackVector, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(4.dp))
            Box(modifier = Modifier.weight(1f).height(36.dp).background(Color(0x1FFFFFFF), RoundedCornerShape(5.dp)), contentAlignment = Alignment.CenterStart) {
                Row(modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = vm.query,
                        onValueChange = { vm.onQueryChange(it) },
                        textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                        cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus(); vm.performFullSearch() }),
                        modifier = Modifier.weight(1f).focusRequester(focusRequester).onFocusChanged { state -> if (state.isFocused) vm.isSearchFocused = true },
                        decorationBox = { innerTextField -> if (vm.query.isEmpty()) Text("Search movies, shows...", color = Color(0x61FFFFFF), fontSize = 14.sp); innerTextField() }
                    )
                    if (vm.query.isNotEmpty()) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(18.dp).clickable { vm.query = ""; vm.quickResults = emptyList(); focusRequester.requestFocus() })
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Color(0x33FFFFFF)))

        if (vm.isSearchFocused) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(vm.quickResults, key = { it }) { resultText ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { vm.query = resultText; focusManager.clearFocus(); vm.performFullSearch() }.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = SearchSuggestIcon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(text = resultText, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    if (vm.availableModes.size > 1) {
                        LazyRow(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(vm.availableModes) { mode ->
                                val isSelected = vm.selectedMode == mode
                                Box(modifier = Modifier.clip(RoundedCornerShape(50)).background(if (isSelected) Color.White else Color(0xFF222222)).border(1.dp, if (isSelected) Color.White else Color.Gray, RoundedCornerShape(50)).clickable { vm.selectMode(mode) }.padding(horizontal = 14.dp, vertical = 6.dp)) {
                                    Text(text = mode.name, color = if (isSelected) Color.Black else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else { Spacer(modifier = Modifier.weight(1f)) }
                    Icon(imageVector = if (vm.isGridView) ListViewIcon else GridViewIcon, contentDescription = "Toggle Layout", tint = Color.White, modifier = Modifier.size(22.dp).clickable { vm.isGridView = !vm.isGridView }.padding(start = 4.dp))
                }

                if (vm.isLoadingGrid && vm.gridResults.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CustomLoader() }
                } else if (vm.gridResults.isNotEmpty()) {
                    val gridState = rememberLazyGridState()
                    val isAtBottom by remember { derivedStateOf { val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull(); lastVisible != null && lastVisible.index >= gridState.layoutInfo.totalItemsCount - 3 } }

                    LaunchedEffect(isAtBottom) { if (isAtBottom && !vm.isLoadingMore && vm.hasMoreResults) vm.loadMoreResults() }

                    if (vm.isGridView) {
                        LazyVerticalGrid(state = gridState, columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(vm.gridResults.size) { index -> MediaPosterCard(item = vm.gridResults[index], modifier = Modifier.fillMaxWidth(), onClick = { onMediaClick(vm.gridResults[index]) }) }
                            if (vm.isLoadingMore) { item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) { Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CustomLoader() } } }
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                            items(vm.gridResults) { media -> SearchListItem(item = media, onClick = { onMediaClick(media) }); Spacer(modifier = Modifier.height(10.dp)) }
                            if (vm.isLoadingMore) { item { Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CustomLoader() } } }
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text = "No results found.", color = Color.Gray, fontSize = 16.sp) }
                }
            }
        }
    }
}

@Composable
fun SearchListItem(item: MediaItem, onClick: () -> Unit) {
    var isLoaded by remember { mutableStateOf(false) }
    val painter = rememberAsyncImagePainter(model = ImageRequest.Builder(LocalContext.current).data(item.img).crossfade(true).build(), onState = { state -> isLoaded = state is AsyncImagePainter.State.Success })
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Color(0xFF1E1E1E)).clickable { onClick() }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Card(shape = RoundedCornerShape(4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)), modifier = Modifier.width(70.dp).aspectRatio(230f / 325f)) {
            Image(painter = painter, contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().then(if (!isLoaded) Modifier.skeletonEffect() else Modifier))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (item.type.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = item.type.uppercase(), color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
