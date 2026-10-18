package com.xtsream.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xtsream.app.AppDarkBg
import com.xtsream.app.DataFetch
import com.xtsream.app.data.HomeData
import com.xtsream.app.data.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SeeAllScreen(sectionId: String, sectionTitle: String, providerId: String, onBack: () -> Unit, homeData: HomeData, onMediaClick: (MediaItem) -> Unit) {
    val preloadedSection = homeData.posterSections.find { it.id == sectionId }
    var items by remember { mutableStateOf<List<MediaItem>?>(preloadedSection?.items) }
    var nextPage by remember { mutableStateOf(if (preloadedSection?.items.isNullOrEmpty()) "1" else "2") }
    var hasMore by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (items == null) {
            scope.launch(Dispatchers.IO) {
                try {
                    // Check if it's from Global Search
                    if (sectionId.startsWith("allsearch_")) {
                        val query = sectionId.replace("allsearch_", "")
                        val resp = DataFetch.fetch(providerId, """{"request_type":"allsearch","searchKey":"$query","page":"1"}""")
                        val (newItems, nxtPg) = homeData.parseApiData(resp, providerId)
                        withContext(Dispatchers.Main) { items = newItems; nextPage = nxtPg; if (newItems.isEmpty()) hasMore = false }
                    } else {
                        val resp = DataFetch.fetch(providerId, """{"request_type":"home","id":"$sectionId","page":"1"}""")
                        val (newItems, nxtPg) = homeData.parseApiData(resp, providerId)
                        withContext(Dispatchers.Main) { items = newItems; nextPage = nxtPg; if (newItems.isEmpty()) hasMore = false }
                    }
                } catch(e: Exception){}
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(AppDarkBg).pointerInput(Unit) {}) {
        Row(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = ArrowBackVector, contentDescription = "Back", tint = Color.White, modifier = Modifier.clickable { onBack() }.padding(end = 16.dp))
            Text(text = sectionTitle, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        if (items == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CustomLoader() }
        } else {
            LazyVerticalGrid(state = gridState, columns = GridCells.Fixed(3), contentPadding = PaddingValues(start = 15.dp, end = 15.dp, bottom = 100.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items!!.size) { index ->
                    MediaPosterCard(items!![index], Modifier.fillMaxWidth(), onClick = { onMediaClick(items!![index]) })

                    if (index >= items!!.size - 3 && !isLoadingMore && hasMore && nextPage.isNotEmpty()) {
                        LaunchedEffect(items!!.size) {
                            isLoadingMore = true
                            scope.launch(Dispatchers.IO) {
                                try {
                                    if (sectionId.startsWith("allsearch_")) {
                                        val query = sectionId.replace("allsearch_", "")
                                        val resp = DataFetch.fetch(providerId, """{"request_type":"allsearch","searchKey":"$query","page":"$nextPage"}""")
                                        val (moreItems, nxtPg) = homeData.parseApiData(resp, providerId)
                                        withContext(Dispatchers.Main) { if (moreItems.isEmpty()) hasMore = false else { items = items!! + moreItems; nextPage = nxtPg } }
                                    } else {
                                        val resp = DataFetch.fetch(providerId, """{"request_type":"home","id":"$sectionId","page":"$nextPage"}""")
                                        val (moreItems, nxtPg) = homeData.parseApiData(resp, providerId)
                                        withContext(Dispatchers.Main) { if (moreItems.isEmpty()) hasMore = false else { items = items!! + moreItems; nextPage = nxtPg } }
                                    }
                                } catch(e: Exception){}
                                isLoadingMore = false
                            }
                        }
                    }
                }
                if (isLoadingMore) { item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) { Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CustomLoader() } } }
            }
        }
    }
}