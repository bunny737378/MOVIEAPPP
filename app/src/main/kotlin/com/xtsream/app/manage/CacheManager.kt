package com.xtsream.app.manage

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.xtsream.app.data.CategorySection
import com.xtsream.app.data.FullMetaData
import com.xtsream.app.data.FullSeasonInfo
import com.xtsream.app.data.MediaItem

data class CategoryCacheData(
    val sliderItems: List<MediaItem>?,
    val extractedColors: List<Color>,
    val posterSections: List<CategorySection>
)

object CacheManager {
    private val categoryCache = mutableMapOf<String, CategoryCacheData>()
    private val metaCache = mutableMapOf<String, FullMetaData>()
    private val seasonCache = mutableMapOf<String, FullSeasonInfo>()

    fun saveCategoryData(categoryId: String, data: CategoryCacheData) {
        categoryCache[categoryId] = data
    }

    fun getCategoryData(categoryId: String): CategoryCacheData? {
        return categoryCache[categoryId]
    }

    fun saveMetaData(mediaId: String, data: FullMetaData) {
        metaCache[mediaId] = data
    }

    fun getMetaData(mediaId: String): FullMetaData? {
        return metaCache[mediaId]
    }
    
    fun saveSeasonInfo(mediaId: String, data: FullSeasonInfo) {
        seasonCache[mediaId] = data
    }

    fun getSeasonInfo(mediaId: String): FullSeasonInfo? {
        return seasonCache[mediaId]
    }

    fun clearSession(context: Context) {
        categoryCache.clear()
        metaCache.clear()
        seasonCache.clear()
        try {
            context.cacheDir.deleteRecursively()
        } catch (e: Exception) {}
    }
}