package com.xtsream.app.screens

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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xtsream.app.AppDarkBg
import com.xtsream.app.DataFetch
import com.xtsream.app.data.MediaItem
import com.xtsream.app.manage.PluginConfig
import com.xtsream.app.manage.ProviderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class HomeSearchViewModel : ViewModel() {
    var query by mutableStateOf("")
    var isSearching by mutableStateOf(false)
    
    var searchResults = mutableStateMapOf<PluginConfig, List<MediaItem>>()
    private var searchJob: Job? = null

    fun performGlobalSearch() {
        if (query.isBlank()) return
        searchJob?.cancel()
        isSearching = true
        searchResults.clear()

        searchJob = viewModelScope.launch(Dispatchers.IO) {
            ProviderManager.plugins.filter { it.isInstalled }.forEach { provider ->
                launch(Dispatchers.IO) {
                    try {
                        val req = """{"request_type":"allsearch", "searchKey":"$query", "page":"1"}"""
                        val resp = DataFetch.fetch(provider.id, req)
                        ensureActive()
                        val parsed = parseMedia(resp, provider.id)
                        if (parsed.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                searchResults[provider] = parsed
                            }
                        }
                    } catch (e: Exception) {}
                }
            }
            withContext(Dispatchers.Main) { isSearching = false }
        }
    }

    private fun parseMedia(jsonStr: String, providerId: String): List<MediaItem> {
        val list = mutableListOf<MediaItem>()
        try {
            val root = JSONObject(jsonStr)
            if (root.has("data")) {
                val dataArr = root.getJSONArray("data")
                for (i in 0 until dataArr.length()) {
                    val item = dataArr.getJSONObject(i)
                    list.add(MediaItem(item.optString("id", ""), item.optString("Img", ""), item.optString("titel", ""), item.optString("Type", ""), providerId))
                }
            }
        } catch (e: Exception) {}
        return list
    }
}

@Composable
fun HomeSearchUI(onMediaClick: (MediaItem) -> Unit, onSeeAllClick: (String, String, String) -> Unit) {
    val vm: HomeSearchViewModel = viewModel()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        if (vm.query.isEmpty()) focusRequester.requestFocus()
    }

    Column(modifier = Modifier.fillMaxSize().background(AppDarkBg).statusBarsPadding().pointerInput(Unit){}) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.weight(1f).height(40.dp).background(Color(0x1FFFFFFF), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = vm.query,
                        onValueChange = { vm.query = it },
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                        cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus(); vm.performGlobalSearch() }),
                        modifier = Modifier.weight(1f).focusRequester(focusRequester),
                        decorationBox = { innerTextField ->
                            if (vm.query.isEmpty()) Text("Global Search...", color = Color(0x61FFFFFF), fontSize = 16.sp)
                            innerTextField()
                        }
                    )
                    if (vm.query.isNotEmpty()) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(20.dp).clickable { vm.query = ""; vm.searchResults.clear() })
                    }
                }
            }
        }

        if (vm.isSearching && vm.searchResults.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CustomLoader() }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 100.dp)) {
                items(vm.searchResults.keys.toList()) { provider ->
                    val results = vm.searchResults[provider] ?: emptyList()
                    Column {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(text = provider.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(text = "See all", color = Color(0xFFBEF3D9), fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clickable { onSeeAllClick(vm.query, provider.id, provider.name) }.padding(4.dp))
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
                            items(results.size + 1) { i ->
                                if (i == results.size) {
                                    Box(modifier = Modifier.width(110.dp).clickable { onSeeAllClick(vm.query, provider.id, provider.name) }) {
                                        MediaPosterCard(null, Modifier.fillMaxWidth())
                                        Box(modifier = Modifier.matchParentSize().background(Color(0xE6121212)), contentAlignment = Alignment.Center) { Text("more ➔", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                                    }
                                } else {
                                    MediaPosterCard(results[i], Modifier.width(110.dp), onClick = { onMediaClick(results[i]) })
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

// 🔥 RESTORED GLOBAL SEARCH SEE ALL LOGIC 🔥

class GlobalSearchSeeAllViewModel : ViewModel() {
    var query by mutableStateOf("")
    var providerId by mutableStateOf("")

    var availableModes by mutableStateOf(listOf(SearchMode("ALL", "allsearch")))
    var selectedMode by mutableStateOf(availableModes.first())

    var gridResults by mutableStateOf<List<MediaItem>>(emptyList())
    var isLoadingGrid by mutableStateOf(false)
    var isLoadingMore by mutableStateOf(false)
    var nextSearchPage by mutableStateOf("1")
    var hasMoreResults by mutableStateOf(true)
    var isGridView by mutableStateOf(true)

    private var searchJob: Job? = null
    var isInitialized = false

    fun initSearch(initialQuery: String, provId: String) {
        if (isInitialized) return
        query = initialQuery
        providerId = provId
        isInitialized = true
        fetchAvailableModesAndSearch()
    }

    private fun fetchAvailableModesAndSearch() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resp = DataFetch.fetch(providerId, """{"request_type":"apislist"}""")
                val modes = mutableListOf(SearchMode("ALL", "allsearch"))
                try {
                    val root = JSONObject(resp)
                    val searchtext = root.optJSONObject("apis")?.optJSONArray("searchtext")
                    if (searchtext != null) {
                        for (i in 0 until searchtext.length()) {
                            val obj = searchtext.getJSONObject(i)
                            val name = obj.optString("name", "")
                            val field = obj.optString("field", "")
                            if (name.isNotBlank() && field.isNotBlank()) modes.add(SearchMode(name, field))
                        }
                    }
                } catch (e: Exception) {}

                withContext(Dispatchers.Main) {
                    availableModes = modes
                    if (!availableModes.contains(selectedMode)) selectedMode = availableModes.first()
                    performFullSearch()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { performFullSearch() }
            }
        }
    }

    fun selectMode(mode: SearchMode) {
        if (selectedMode == mode) return
        selectedMode = mode
        performFullSearch()
    }

    private fun performFullSearch() {
        isLoadingGrid = true
        gridResults = emptyList()
        nextSearchPage = "1"
        hasMoreResults = true

        searchJob?.cancel()
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val req = """{"request_type":"${selectedMode.field}", "searchKey":"$query", "page":"1"}"""
                val resp = DataFetch.fetch(providerId, req)
                ensureActive()
                val (parsed, nextPg) = parseMediaWithNextPage(resp, providerId)
                val hasMore = parsed.isNotEmpty() && nextPg.isNotBlank()

                withContext(Dispatchers.Main) {
                    gridResults = parsed
                    nextSearchPage = nextPg
                    hasMoreResults = hasMore
                }
            } catch (e: Exception) {}
            withContext(Dispatchers.Main) { isLoadingGrid = false }
        }
    }

    fun loadMoreResults() {
        if (isLoadingMore || !hasMoreResults || nextSearchPage.isBlank()) return
        isLoadingMore = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val req = """{"request_type":"${selectedMode.field}", "searchKey":"$query", "page":"$nextSearchPage"}"""
                val resp = DataFetch.fetch(providerId, req)
                ensureActive()
                val (parsed, nextPg) = parseMediaWithNextPage(resp, providerId)
                withContext(Dispatchers.Main) {
                    if (parsed.isNotEmpty()) {
                        gridResults = gridResults + parsed
                        nextSearchPage = nextPg
                        hasMoreResults = nextPg.isNotBlank()
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
        } catch (e: Exception) {}
        return Pair(list, nextPg)
    }
}

@Composable
fun GlobalSearchSeeAllUI(query: String, providerId: String, providerName: String, onBack: () -> Unit, onMediaClick: (MediaItem) -> Unit) {
    val vm: GlobalSearchSeeAllViewModel = viewModel(key = "${providerId}_$query")

    LaunchedEffect(Unit) {
        vm.initSearch(query, providerId)
    }

    Column(modifier = Modifier.fillMaxSize().background(AppDarkBg).statusBarsPadding().pointerInput(Unit){}) {
        Row(modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(36.dp).clickable { onBack() }, contentAlignment = Alignment.Center) {
                Icon(imageVector = ArrowBackVector, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "$providerName : $query", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Icon(imageVector = if (vm.isGridView) ListViewIcon else GridViewIcon, contentDescription = "Toggle Layout", tint = Color.White, modifier = Modifier.size(22.dp).clickable { vm.isGridView = !vm.isGridView })
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (vm.availableModes.size > 1) {
            LazyRow(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(vm.availableModes) { mode ->
                    val isSelected = vm.selectedMode == mode
                    Box(modifier = Modifier.clip(RoundedCornerShape(50)).background(if (isSelected) Color.White else Color(0xFF222222)).border(1.dp, if (isSelected) Color.White else Color.Gray, RoundedCornerShape(50)).clickable { vm.selectMode(mode) }.padding(horizontal = 14.dp, vertical = 6.dp)) {
                        Text(text = mode.name, color = if (isSelected) Color.Black else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (vm.isLoadingGrid && vm.gridResults.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CustomLoader() }
        } else if (vm.gridResults.isNotEmpty()) {
            val gridState = rememberLazyGridState()
            val isAtBottom by remember { derivedStateOf { val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull(); lastVisible != null && lastVisible.index >= gridState.layoutInfo.totalItemsCount - 3 } }

            LaunchedEffect(isAtBottom) { if (isAtBottom && !vm.isLoadingMore && vm.hasMoreResults) vm.loadMoreResults() }

            if (vm.isGridView) {
                LazyVerticalGrid(state = gridState, columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(vm.gridResults.size) { index -> MediaPosterCard(item = vm.gridResults[index], modifier = Modifier.fillMaxWidth(), onClick = { onMediaClick(vm.gridResults[index]) }) }
                    if (vm.isLoadingMore) { item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) { Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CustomLoader() } } }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = 100.dp)) {
                    items(vm.gridResults) { media -> SearchListItem(item = media, onClick = { onMediaClick(media) }); Spacer(modifier = Modifier.height(10.dp)) }
                    if (vm.isLoadingMore) { item { Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CustomLoader() } } }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text = "No results found.", color = Color.Gray, fontSize = 16.sp) }
        }
    }
}