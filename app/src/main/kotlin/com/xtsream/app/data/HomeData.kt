package com.xtsream.app.data

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.request.ImageRequest
import com.xtsream.app.AppDarkBg
import com.xtsream.app.DataFetch
import com.xtsream.app.manage.CacheManager
import com.xtsream.app.manage.CategoryCacheData
import com.xtsream.app.manage.ProviderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class MediaItem(val id: String, val img: String, val title: String, val type: String, val providerId: String)

class CategorySection(val id: String, val name: String) {
    var items by mutableStateOf<List<MediaItem>?>(null)
    var nextPage by mutableStateOf("1")
    var hasMore by mutableStateOf(true)
    var isLoadingMore by mutableStateOf(false)
}

class HomeData : ViewModel() {
    var catalogLoaded by mutableStateOf(false)
    var categoriesList by mutableStateOf<List<String>>(emptyList())
    var categoryMap by mutableStateOf<Map<String, List<Pair<String, String>>>>(emptyMap())
    
    var selectedCategory by mutableStateOf("")
    var sliderItems by mutableStateOf<List<MediaItem>?>(null)
    var posterSections by mutableStateOf<List<CategorySection>>(emptyList())
    var extractedColors = mutableListOf<Color>()

    var isInitialBatchLoading by mutableStateOf(false)
    var isPaginationLoading by mutableStateOf(false)
    var allSubcategoriesLoaded by mutableStateOf(false)

    private val memoryPendingSections = mutableMapOf<String, List<CategorySection>>()
    private var pendingSections = mutableListOf<CategorySection>()
    private var fetchingSections = mutableListOf<CategorySection>()

    private var categoryFetchJob: Job? = null
    private var imagePreloadJob: Job? = null
    private val imageQueue = Channel<String>(Channel.UNLIMITED)

    fun clearHomeState() {
        catalogLoaded = false
        categoriesList = emptyList()
        categoryMap = emptyMap()
        selectedCategory = ""
        sliderItems = null
        posterSections = emptyList()
        extractedColors.clear()
        memoryPendingSections.clear()
        pendingSections.clear()
        fetchingSections.clear()
    }

    fun fetchCatalog() {
        if (catalogLoaded || ProviderManager.currentProvider == null) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val catResp = DataFetch.fetch(ProviderManager.currentProvider!!.id, """{"request_type":"catalog"}""")
                val parsed = parseCatalogData(catResp)

                withContext(Dispatchers.Main) {
                    if (parsed.isNotEmpty()) {
                        categoryMap = parsed
                        categoriesList = parsed.keys.toList()
                        catalogLoaded = true
                    }
                }
            } catch (e: Exception) {}
        }
    }

    fun cancelAllOperations() {
        categoryFetchJob?.cancel()
        categoryFetchJob = null
        imagePreloadJob?.cancel()
        imagePreloadJob = null
        
        while (imageQueue.tryReceive().isSuccess) { } 

        if (fetchingSections.isNotEmpty()) {
            pendingSections.addAll(0, fetchingSections)
            fetchingSections.clear()
        }
        
        if (selectedCategory.isNotEmpty()) {
            memoryPendingSections[selectedCategory] = pendingSections.toList()
        }

        isInitialBatchLoading = false
        isPaginationLoading = false
    }

    fun resumeCategory(context: Context?) {
        if (selectedCategory.isBlank() || allSubcategoriesLoaded) return
        startSequentialImagePreloader(context)
        if (!isInitialBatchLoading && !isPaginationLoading && pendingSections.isNotEmpty()) {
            loadNextSubcategoryBatch(context)
        }
    }

    fun selectCategory(categoryName: String, context: Context?, force: Boolean = false) {
        if (categoryName == selectedCategory && !force) return
        
        cancelAllOperations()
        selectedCategory = categoryName

        val cached = CacheManager.getCategoryData(categoryName)
        if (cached != null) {
            sliderItems = cached.sliderItems
            extractedColors.clear()
            extractedColors.addAll(cached.extractedColors)
            posterSections = cached.posterSections
            
            pendingSections = memoryPendingSections[categoryName]?.toMutableList() ?: mutableListOf()
            if (pendingSections.isEmpty() && posterSections.isNotEmpty()) {
                allSubcategoriesLoaded = true
            } else {
                allSubcategoriesLoaded = false
                resumeCategory(context)
            }
            return
        }

        sliderItems = null
        extractedColors.clear()
        posterSections = emptyList()
        pendingSections.clear()
        fetchingSections.clear()
        allSubcategoriesLoaded = false
        isInitialBatchLoading = true

        val sectionList = categoryMap[selectedCategory] ?: emptyList()
        var slideId = ""
        val tempSections = mutableListOf<CategorySection>()

        for ((id, nm) in sectionList) {
            if (nm.equals("slug", ignoreCase = true)) slideId = id
            else tempSections.add(CategorySection(id, nm))
        }

        val initial3 = tempSections.take(3)
        if (tempSections.size > 3) {
            pendingSections = tempSections.drop(3).toMutableList()
            memoryPendingSections[selectedCategory] = pendingSections.toList()
        }

        categoryFetchJob = viewModelScope.launch(Dispatchers.IO) {
            startSequentialImagePreloader(context)
            val pid = ProviderManager.currentProvider?.id ?: return@launch

            if (slideId.isNotEmpty() && context != null) {
                try {
                    val slideResp = DataFetch.fetch(pid, """{"request_type":"home","id":"$slideId","page":"1"}""")
                    val (slideList, _) = parseApiData(slideResp, pid)

                    withContext(Dispatchers.Main) {
                        sliderItems = slideList
                        extractedColors.clear()
                        extractedColors.addAll(List(if (slideList.isEmpty()) 4 else slideList.size) { AppDarkBg })
                    }

                    slideList.forEach { item -> if (item.img.isNotBlank()) imageQueue.send(item.img) }

                    launch(Dispatchers.IO) {
                        val imageLoader = coil.Coil.imageLoader(context)
                        slideList.forEachIndexed { index, item ->
                            try {
                                val req = ImageRequest.Builder(context).data(item.img).allowHardware(false).build()
                                val result = imageLoader.execute(req)
                                val bitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                                if (bitmap != null) {
                                    val domColor = extractDominantColor(bitmap)
                                    withContext(Dispatchers.Main) {
                                        if (index < extractedColors.size) extractedColors[index] = domColor
                                    }
                                }
                            } catch (e: Exception) { }
                        }
                    }
                } catch (e: Exception) {}
            }

            fetchingSections.addAll(initial3)
            val firstBatchDeferreds = fetchingSections.map { sec ->
                async(Dispatchers.IO) {
                    try {
                        val secResp = DataFetch.fetch(pid, """{"request_type":"home","id":"${sec.id}","page":"1"}""")
                        val (secList, nxtPg) = parseApiData(secResp, pid)
                        sec.items = secList
                        sec.nextPage = nxtPg
                        if (secList.isEmpty()) sec.hasMore = false
                        sec
                    } catch (e: Exception) {
                        sec.items = emptyList()
                        sec
                    }
                }
            }

            val resolvedFirstBatch = firstBatchDeferreds.awaitAll()

            withContext(Dispatchers.Main) {
                posterSections = resolvedFirstBatch
                fetchingSections.clear()
                isInitialBatchLoading = false
                if (pendingSections.isEmpty()) allSubcategoriesLoaded = true
                cacheCurrentState(categoryName)
            }

            resolvedFirstBatch.forEach { section ->
                section.items?.forEach { item -> if (item.img.isNotBlank()) imageQueue.send(item.img) }
            }
        }
    }

    fun loadNextSubcategoryBatch(context: Context?) {
        if (isPaginationLoading || allSubcategoriesLoaded || pendingSections.isEmpty()) return
        isPaginationLoading = true

        val next2 = pendingSections.take(2)
        pendingSections = pendingSections.drop(2).toMutableList()
        memoryPendingSections[selectedCategory] = pendingSections.toList()
        fetchingSections.addAll(next2)

        categoryFetchJob = viewModelScope.launch(Dispatchers.IO) {
            val pid = ProviderManager.currentProvider?.id ?: return@launch
            val batchDeferreds = fetchingSections.map { sec ->
                async(Dispatchers.IO) {
                    try {
                        val secResp = DataFetch.fetch(pid, """{"request_type":"home","id":"${sec.id}","page":"1"}""")
                        val (secList, nxtPg) = parseApiData(secResp, pid)
                        sec.items = secList
                        sec.nextPage = nxtPg
                        if (secList.isEmpty()) sec.hasMore = false
                        sec
                    } catch (e: Exception) {
                        sec.items = emptyList()
                        sec
                    }
                }
            }

            val resolvedBatch = batchDeferreds.awaitAll()

            withContext(Dispatchers.Main) {
                posterSections = posterSections + resolvedBatch
                fetchingSections.clear()
                isPaginationLoading = false
                if (pendingSections.isEmpty()) allSubcategoriesLoaded = true
                cacheCurrentState(selectedCategory)
            }

            resolvedBatch.forEach { section ->
                section.items?.forEach { item -> if (item.img.isNotBlank()) imageQueue.send(item.img) }
            }
        }
    }

    private fun startSequentialImagePreloader(context: Context?) {
        if (context == null) return
        imagePreloadJob?.cancel()
        imagePreloadJob = viewModelScope.launch(Dispatchers.IO) {
            val imageLoader = coil.Coil.imageLoader(context)
            for (imageUrl in imageQueue) {
                ensureActive()
                try {
                    val req = ImageRequest.Builder(context).data(imageUrl).build()
                    imageLoader.execute(req)
                } catch (e: Exception) {}
            }
        }
    }

    fun loadMoreForSection(section: CategorySection) {
        if (section.isLoadingMore || !section.hasMore || section.nextPage.isEmpty()) return
        section.isLoadingMore = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pid = ProviderManager.currentProvider?.id ?: return@launch
                val resp = DataFetch.fetch(pid, """{"request_type":"home","id":"${section.id}","page":"${section.nextPage}"}""")
                val (moreItems, nxtPg) = parseApiData(resp, pid)
                withContext(Dispatchers.Main) {
                    if (moreItems.isEmpty()) {
                        section.hasMore = false
                    } else {
                        section.items = (section.items ?: emptyList()) + moreItems
                        section.nextPage = nxtPg
                    }
                    cacheCurrentState(selectedCategory)
                }
            } catch (e: Exception) {}
            section.isLoadingMore = false
        }
    }

    private fun cacheCurrentState(categoryName: String) {
        CacheManager.saveCategoryData(categoryName, CategoryCacheData(sliderItems, extractedColors.toList(), posterSections))
    }

    fun parseApiData(jsonStr: String, providerId: String): Pair<List<MediaItem>, String> {
        val list = mutableListOf<MediaItem>()
        var nextPage = ""
        try {
            val root = JSONObject(jsonStr)
            if (root.has("nextpage")) nextPage = root.optString("nextpage", "")
            if (root.has("data")) {
                val dataArr = root.getJSONArray("data")
                for (i in 0 until dataArr.length()) {
                    val item = dataArr.getJSONObject(i)
                    list.add(MediaItem(item.optString("id", ""), item.optString("Img", ""), item.optString("titel", ""), item.optString("Type", ""), providerId))
                }
            }
        } catch (e: Exception) {}
        return Pair(list, nextPage)
    }

    private fun parseCatalogData(jsonStr: String): Map<String, List<Pair<String, String>>> {
        val result = mutableMapOf<String, List<Pair<String, String>>>()
        try {
            val root = JSONObject(jsonStr)
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val arr = root.optJSONArray(key) ?: continue
                val list = mutableListOf<Pair<String, String>>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("i", "")
                    val nm = obj.optString("nm", "")
                    if (id.isNotEmpty() && nm.isNotEmpty()) list.add(Pair(id, nm))
                }
                result[key] = list
            }
        } catch (e: Exception) {}
        return result
    }

    private suspend fun extractDominantColor(bitmap: Bitmap): Color = withContext(Dispatchers.Default) {
        val scaled = Bitmap.createScaledBitmap(bitmap, 40, 40, false)
        val scanHeight = (scaled.height * 0.2f).toInt().coerceAtLeast(1)
        val colorCounts = mutableMapOf<Int, Int>()
        for (x in 0 until scaled.width step 2) {
            for (y in 0 until scanHeight step 1) {
                val pixel = scaled.getPixel(x, y)
                val red = android.graphics.Color.red(pixel)
                val green = android.graphics.Color.green(pixel)
                val blue = android.graphics.Color.blue(pixel)
                val quantizedR = (red / 16) * 16
                val quantizedG = (green / 16) * 16
                val quantizedB = (blue / 16) * 16
                if (quantizedR < 10 && quantizedG < 10 && quantizedB < 10) continue
                val rgb = android.graphics.Color.rgb(quantizedR, quantizedG, quantizedB)
                colorCounts[rgb] = (colorCounts[rgb] ?: 0) + 1
            }
        }
        scaled.recycle()
        val dominantRgb = colorCounts.maxByOrNull { it.value }?.key ?: android.graphics.Color.rgb(42, 42, 42)
        val r = android.graphics.Color.red(dominantRgb)
        val g = android.graphics.Color.green(dominantRgb)
        val b = android.graphics.Color.blue(dominantRgb)
        val luminance = (0.299 * r + 0.587 * g + 0.114 * b)
        var finalR = r / 255f
        var finalG = g / 255f
        var finalB = b / 255f
        val darkFactor = when {
            luminance > 180 -> 0.35f
            luminance > 130 -> 0.45f
            luminance > 80 -> 0.55f
            luminance > 40 -> 0.65f
            else -> 0.75f
        }
        finalR *= darkFactor * 0.85f
        finalG *= darkFactor * 0.85f
        finalB *= darkFactor * 0.85f
        Color(finalR.coerceIn(0f, 1f), finalG.coerceIn(0f, 1f), finalB.coerceIn(0f, 1f))
    }
}
