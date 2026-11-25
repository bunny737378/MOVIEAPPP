package com.xtsream.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.lagradost.cloudstream3.plugins.SitePlugin
import com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData
import com.xtsream.app.engine.AddSourceResult
import com.xtsream.app.engine.ExtensionController
import com.xtsream.app.engine.InstalledExtensionInfo
import com.xtsream.app.engine.PluginIconCache
import com.xtsream.app.engine.ProviderEntry
import com.xtsream.app.stremio.StremioAddonCategory
import com.xtsream.app.stremio.StremioAddonRecord
import com.xtsream.app.ui.components.AppIcons
import com.xtsream.app.ui.components.AppToastManager
import com.xtsream.app.ui.components.M3InlineSpinner
import com.xtsream.app.ui.components.buildCoilRequest
import com.xtsream.app.ui.theme.AppColors
import kotlinx.coroutines.launch
import java.io.File

private enum class ProviderEngineMode(val label: String) {
    CloudStream("CloudStream"),
    Stremio("Stremio")
}

private data class UniversalPluginMatch(
    val repo: RepositoryData,
    val plugin: SitePlugin
)

@Composable
fun ProvidersScreen(
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    val repositories by ExtensionController.repositories.collectAsState()
    val repoPlugins by ExtensionController.repoPlugins.collectAsState()
    val loadingRepos by ExtensionController.loadingRepos.collectAsState()
    val isPreloadingRepos by ExtensionController.isPreloadingRepos.collectAsState()
    val installedPlugins by ExtensionController.installedPlugins.collectAsState()
    val loadedProviders by ExtensionController.loadedProviders.collectAsState()
    val activeProvider by ExtensionController.activeProvider.collectAsState()
    val installingKeys by ExtensionController.installingKeys.collectAsState()
    val stremioAddons by ExtensionController.stremioAddons.collectAsState()

    var engineMode by remember { mutableStateOf(ProviderEngineMode.CloudStream) }

    // Animated universal title-bar search state
    var isUniversalSearchOpen by remember { mutableStateOf(false) }
    var universalSearchQuery by remember { mutableStateOf("") }
    val searchFocusRequester = remember { FocusRequester() }

    // CloudStream state
    var csSourceInput by remember { mutableStateOf("") }
    var isAddingCs by remember { mutableStateOf(false) }
    var csActiveTab by remember { mutableIntStateOf(0) } // 0 = Repositories, 1 = Installed
    var expandedRepoUrl by remember { mutableStateOf<String?>(null) }
    var pluginFilterQuery by remember { mutableStateOf("") }
    var repoPendingDelete by remember { mutableStateOf<RepositoryData?>(null) }

    // Stremio state
    var stremioInput by remember { mutableStateOf("") }
    var isAddingStremio by remember { mutableStateOf(false) }
    var selectedStremioCategory by remember { mutableStateOf(StremioAddonCategory.All) }
    var stremioPendingDelete by remember { mutableStateOf<StremioAddonRecord?>(null) }
    var refreshingStremioUrl by remember { mutableStateOf<String?>(null) }

    // Automatically preload ALL repository extension lists whenever Providers screen opens
    LaunchedEffect(repositories.size) {
        if (repositories.isNotEmpty()) {
            ExtensionController.preloadAllRepositories(force = false)
        }
    }

    LaunchedEffect(isUniversalSearchOpen) {
        if (isUniversalSearchOpen) {
            runCatching { searchFocusRequester.requestFocus() }
        }
    }

    // Universal CloudStream extension matches across ALL repositories (shows matching extensions directly!)
    val universalCsMatches = remember(universalSearchQuery, repositories, repoPlugins) {
        val q = universalSearchQuery.trim()
        if (q.isBlank()) return@remember emptyList<UniversalPluginMatch>()
        val repoByUrl = repositories.associateBy { it.url }
        buildList {
            repoPlugins.forEach { (repoUrl, plugins) ->
                val repo = repoByUrl[repoUrl] ?: return@forEach
                plugins.forEach { p ->
                    val matched = p.name.contains(q, ignoreCase = true) ||
                        p.internalName.contains(q, ignoreCase = true) ||
                        (p.language?.contains(q, ignoreCase = true) == true) ||
                        (p.description?.contains(q, ignoreCase = true) == true) ||
                        (p.tvTypes?.any { it.contains(q, ignoreCase = true) } == true)
                    if (matched) add(UniversalPluginMatch(repo, p))
                }
            }
        }.sortedWith(
            compareByDescending<UniversalPluginMatch> {
                it.plugin.name.startsWith(q, ignoreCase = true) || it.plugin.internalName.startsWith(q, ignoreCase = true)
            }.thenBy { it.plugin.name.lowercase() }
        )
    }

    // Filtered Stremio addons (respecting both category filter & optional universal search)
    val filteredStremioAddons = remember(stremioAddons, selectedStremioCategory, universalSearchQuery, isUniversalSearchOpen) {
        val q = if (isUniversalSearchOpen) universalSearchQuery.trim() else ""
        stremioAddons.filter { rec ->
            val matchesCat = rec.manifest.matchesCategory(selectedStremioCategory)
            val matchesQ = q.isBlank() ||
                rec.manifest.name.contains(q, ignoreCase = true) ||
                rec.manifest.description.contains(q, ignoreCase = true) ||
                rec.manifest.id.contains(q, ignoreCase = true) ||
                rec.manifest.resourceNames().any { it.contains(q, ignoreCase = true) } ||
                rec.manifest.catalogs.any { it.name.contains(q, ignoreCase = true) || it.type.contains(q, ignoreCase = true) }
            matchesCat && matchesQ
        }
    }

    fun submitCsSource() {
        val raw = csSourceInput.trim()
        if (raw.isBlank() || isAddingCs) return
        focusManager.clearFocus()
        isAddingCs = true
        scope.launch {
            val result = ExtensionController.addSourceFromInput(context, raw)
            isAddingCs = false
            result.onSuccess { res ->
                csSourceInput = ""
                when (res) {
                    is AddSourceResult.RepositoryAdded -> {
                        csActiveTab = 0
                        expandedRepoUrl = if (res.isMetaRepoSynced && res.addedChildRepos > 0) null else res.repository.url
                        pluginFilterQuery = ""
                        val message = when {
                            res.isMetaRepoSynced && res.addedChildRepos > 0 ->
                                "Synced ${res.repository.name}: added ${res.addedChildRepos} repositories"
                            res.addedChildRepos > 0 && res.plugins.isNotEmpty() ->
                                "Added ${res.repository.name} (${res.plugins.size} extensions + ${res.addedChildRepos} repos)"
                            res.addedChildRepos > 0 ->
                                "Added ${res.repository.name} (+${res.addedChildRepos} repositories)"
                            else ->
                                "Added \"${res.repository.name}\" (${res.plugins.size} extensions)"
                        }
                        AppToastManager.showSuccess(message)
                    }

                    is AddSourceResult.DirectPluginInstalled -> {
                        csActiveTab = if (res.addedRepos > 0) 0 else 1
                        val verb = if (res.wasUpdate) "Updated" else "Installed"
                        val detail = when {
                            res.addedRepos > 0 -> "$verb ${res.pluginName} & added ${res.addedRepos} repositories"
                            res.addedApis.isNotEmpty() -> "$verb ${res.pluginName} (${res.addedApis.joinToString()})"
                            else -> "$verb ${res.pluginName}"
                        }
                        AppToastManager.showSuccess(detail)
                    }
                }
            }.onFailure { err ->
                AppToastManager.showError(err.message ?: "Failed to load \"$raw\"")
            }
        }
    }

    fun submitStremioSource() {
        val raw = stremioInput.trim()
        if (raw.isBlank() || isAddingStremio) return
        focusManager.clearFocus()
        isAddingStremio = true
        scope.launch {
            val result = ExtensionController.addStremioAddon(context, raw)
            isAddingStremio = false
            result.onSuccess { rec ->
                stremioInput = ""
                val resSummary = rec.manifest.resourceNames().joinToString(", ").ifBlank { "addon" }
                AppToastManager.showSuccess("Added ${rec.manifest.name} ($resSummary)")
            }.onFailure { err ->
                AppToastManager.showError(err.message ?: "Failed to add Stremio addon")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .statusBarsPadding()
    ) {
        // Title Bar with Animated Search Icon -> Inline Universal Search Bar
        AnimatedContent(
            targetState = isUniversalSearchOpen,
            transitionSpec = {
                if (targetState) {
                    (fadeIn(tween(220)) + slideInHorizontally(tween(240)) { it / 3 } + scaleIn(tween(220), initialScale = 0.96f))
                        .togetherWith(fadeOut(tween(150)) + slideOutHorizontally(tween(180)) { -it / 4 })
                } else {
                    (fadeIn(tween(200)) + slideInHorizontally(tween(220)) { -it / 4 })
                        .togetherWith(fadeOut(tween(150)) + slideOutHorizontally(tween(180)) { it / 3 })
                }
            },
            label = "providers_header_search"
        ) { searching ->
            if (!searching) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .padding(start = if (onBack != null) 8.dp else 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = AppColors.Primary)
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Providers",
                            color = AppColors.TextPrimary,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val subtitle = if (engineMode == ProviderEngineMode.CloudStream) {
                            "${repositories.size} repositories • ${installedPlugins.size} installed"
                        } else {
                            "${stremioAddons.size} Stremio addon${if (stremioAddons.size == 1) "" else "s"} installed"
                        }
                        Text(
                            text = subtitle,
                            color = AppColors.TextMuted,
                            fontSize = 12.sp
                        )
                    }
                    IconButton(
                        onClick = { isUniversalSearchOpen = true }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search all extensions",
                            tint = AppColors.Primary,
                            modifier = Modifier.size(23.dp)
                        )
                    }
                }
            } else {
                // Animated Inline Universal Search Bar inside the Title Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(AppColors.SurfaceCard)
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                universalSearchQuery = ""
                                isUniversalSearchOpen = false
                                focusManager.clearFocus()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Close search",
                                tint = AppColors.TextSecondary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        BasicTextField(
                            value = universalSearchQuery,
                            onValueChange = { universalSearchQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(color = AppColors.TextPrimary, fontSize = 14.sp),
                            cursorBrush = SolidColor(AppColors.Primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(searchFocusRequester),
                            decorationBox = { inner ->
                                if (universalSearchQuery.isEmpty()) {
                                    Text(
                                        text = if (engineMode == ProviderEngineMode.CloudStream) {
                                            "Search extensions across all repos..."
                                        } else {
                                            "Search installed Stremio addons..."
                                        },
                                        color = AppColors.TextMuted,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                inner()
                            }
                        )
                        if (universalSearchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { universalSearchQuery = "" },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Clear",
                                    tint = AppColors.TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Top Engine Switcher: CloudStream | Stremio
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 10.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(AppColors.SurfaceCard)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ProviderEngineMode.entries.forEach { mode ->
                val selected = engineMode == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) AppColors.Primary else Color.Transparent)
                        .clickable { engineMode = mode },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mode.label,
                        color = if (selected) AppColors.OnPrimary else AppColors.TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        // Universal CloudStream Search Results View (direct matching extensions across ALL repos!)
        if (engineMode == ProviderEngineMode.CloudStream && isUniversalSearchOpen && universalSearchQuery.isNotBlank()) {
            HorizontalDivider(thickness = 0.5.dp, color = AppColors.Divider)

            if (isPreloadingRepos || loadingRepos.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AppColors.Surface)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    M3InlineSpinner(size = 15.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Loading repositories (${repoPlugins.size}/${repositories.size})...",
                        color = AppColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            if (universalCsMatches.isEmpty() && !isPreloadingRepos && loadingRepos.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Search, null, tint = AppColors.TextMuted, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No matching extensions for \"${universalSearchQuery.trim()}\"",
                            color = AppColors.TextSecondary,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    item(key = "uni_count") {
                        Text(
                            text = "${universalCsMatches.size} matching extension${if (universalCsMatches.size == 1) "" else "s"}",
                            color = AppColors.TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                    items(
                        items = universalCsMatches,
                        key = { "${it.repo.url}::${it.plugin.internalName}::${it.plugin.url}" }
                    ) { match ->
                        val installKey = "${match.repo.url}::${match.plugin.internalName}"
                        val isInstalling = installKey in installingKeys
                        val installedVer = ExtensionController.installedVersion(match.repo.url, match.plugin)
                        val isInstalled = ExtensionController.isPluginInstalled(match.repo.url, match.plugin)
                        val hasUpdate = isInstalled && installedVer != null && match.plugin.version > installedVer

                        SitePluginFlatRow(
                            plugin = match.plugin,
                            repoNameBadge = match.repo.name,
                            isInstalled = isInstalled,
                            hasUpdate = hasUpdate,
                            isInstalling = isInstalling,
                            onInstall = {
                                scope.launch {
                                    val res = ExtensionController.installPlugin(context, match.repo.url, match.plugin)
                                    res.onSuccess { msg -> AppToastManager.showSuccess(msg) }
                                        .onFailure { err -> AppToastManager.showError(err.message ?: "Failed installing ${match.plugin.name}") }
                                }
                            },
                            onUninstall = {
                                scope.launch {
                                    val res = ExtensionController.uninstallPlugin(context, match.repo.url, match.plugin)
                                    res.onSuccess { AppToastManager.showSuccess("Uninstalled ${match.plugin.name}") }
                                        .onFailure { err -> AppToastManager.showError(err.message ?: "Uninstall failed") }
                                }
                            }
                        )
                    }
                }
            }
            return
        }

        if (engineMode == ProviderEngineMode.CloudStream) {
            // CloudStream Source Input Bar
            SourceAddInputBar(
                value = csSourceInput,
                onValueChange = { csSourceInput = it },
                placeholder = "Shortname (megarepo), repo URL, or .cs3 link",
                isAdding = isAddingCs,
                onSubmit = ::submitCsSource
            )

            // Underline Tabs: Repositories | Installed
            Row(modifier = Modifier.fillMaxWidth()) {
                ProviderUnderlineTab(
                    label = "Repositories (${repositories.size})",
                    selected = csActiveTab == 0,
                    modifier = Modifier.weight(1f),
                    onClick = { csActiveTab = 0 }
                )
                ProviderUnderlineTab(
                    label = "Installed (${installedPlugins.size})",
                    selected = csActiveTab == 1,
                    modifier = Modifier.weight(1f),
                    onClick = { csActiveTab = 1 }
                )
            }
            HorizontalDivider(thickness = 0.5.dp, color = AppColors.Divider)

            if (csActiveTab == 0) {
                if (repositories.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(AppIcons.FolderOpen, null, tint = AppColors.TextMuted, modifier = Modifier.size(44.dp))
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "No Repositories Added",
                                color = AppColors.TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Enter a CloudStream repo shortname (e.g. megarepo), repo.json, plugins.json, or a direct .cs3 URL above.",
                                color = AppColors.TextSecondary,
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(repositories, key = { it.url }) { repo ->
                            val isExpanded = expandedRepoUrl == repo.url
                            val isLoading = repo.url in loadingRepos
                            val plugins = repoPlugins[repo.url].orEmpty()

                            LaunchedEffect(isExpanded, repo.url) {
                                if (isExpanded && !repoPlugins.containsKey(repo.url)) {
                                    ExtensionController.loadRepositoryPlugins(repo)
                                }
                            }

                            RepositoryFlatItem(
                                repo = repo,
                                isExpanded = isExpanded,
                                isLoading = isLoading,
                                plugins = plugins,
                                filterQuery = if (isExpanded) pluginFilterQuery else "",
                                onFilterChange = { pluginFilterQuery = it },
                                installingKeys = installingKeys,
                                onToggleExpand = {
                                    if (isExpanded) {
                                        expandedRepoUrl = null
                                    } else {
                                        expandedRepoUrl = repo.url
                                        pluginFilterQuery = ""
                                    }
                                },
                                onRefresh = {
                                    scope.launch {
                                        val res = ExtensionController.loadRepositoryPlugins(repo, force = true)
                                        res.onSuccess { list ->
                                            AppToastManager.showSuccess("Loaded ${list.size} extensions from ${repo.name}")
                                        }.onFailure { err ->
                                            AppToastManager.showError(err.message ?: "Refresh failed")
                                        }
                                    }
                                },
                                onDelete = { repoPendingDelete = repo },
                                onInstall = { plugin ->
                                    scope.launch {
                                        val res = ExtensionController.installPlugin(context, repo.url, plugin)
                                        res.onSuccess { msg -> AppToastManager.showSuccess(msg) }
                                            .onFailure { err -> AppToastManager.showError(err.message ?: "Failed installing ${plugin.name}") }
                                    }
                                },
                                onUninstall = { plugin ->
                                    scope.launch {
                                        val res = ExtensionController.uninstallPlugin(context, repo.url, plugin)
                                        res.onSuccess { AppToastManager.showSuccess("Uninstalled ${plugin.name}") }
                                            .onFailure { err -> AppToastManager.showError(err.message ?: "Uninstall failed") }
                                    }
                                }
                            )
                        }
                    }
                }
            } else {
                if (installedPlugins.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(AppIcons.Extension, null, tint = AppColors.TextMuted, modifier = Modifier.size(44.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No Extensions Installed",
                                color = AppColors.TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Install extensions from a repository or paste a direct .cs3 URL above.",
                                color = AppColors.TextSecondary,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(installedPlugins, key = { it.pluginData.filePath }) { info ->
                            InstalledExtensionFlatRow(
                                info = info,
                                activeProviderKey = activeProvider?.key,
                                onSelectEntry = { entry ->
                                    ExtensionController.selectProvider(entry)
                                    AppToastManager.showSuccess("Active provider: ${entry.displayName}")
                                },
                                onUninstall = {
                                    scope.launch {
                                        val res = ExtensionController.uninstallPlugin(info.pluginData.filePath)
                                        res.onSuccess { AppToastManager.showSuccess("Uninstalled ${info.pluginData.internalName}") }
                                            .onFailure { err -> AppToastManager.showError(err.message ?: "Failed to uninstall") }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        } else {
            // ======================== STREMIO ADDONS SECTION ========================
            SourceAddInputBar(
                value = stremioInput,
                onValueChange = { stremioInput = it },
                placeholder = "Stremio manifest URL (https://.../manifest.json)",
                isAdding = isAddingStremio,
                onSubmit = ::submitStremioSource
            )

            // Built-in Stremio Addon Type Filter Chips (All / Catalog / Meta / Stream / Subtitles)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                items(StremioAddonCategory.entries, key = { it.name }) { category ->
                    val count = remember(stremioAddons, category) {
                        stremioAddons.count { it.manifest.matchesCategory(category) }
                    }
                    val selected = selectedStremioCategory == category
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) AppColors.Primary else AppColors.SurfaceCard)
                            .clickable { selectedStremioCategory = category }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = category.label,
                            color = if (selected) AppColors.OnPrimary else AppColors.TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                        if (count > 0) {
                            Text(
                                text = count.toString(),
                                color = if (selected) AppColors.OnPrimary.copy(alpha = 0.72f) else AppColors.TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = AppColors.Divider)

            if (filteredStremioAddons.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(AppIcons.Extension, null, tint = AppColors.TextMuted, modifier = Modifier.size(44.dp))
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = if (stremioAddons.isEmpty()) "No Stremio Addons Installed" else "No Matching Addons",
                            color = AppColors.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (stremioAddons.isEmpty()) {
                                "Paste any Stremio addon manifest URL (e.g. https://v3-cinemeta.strem.io/manifest.json or stremio://...) above."
                            } else {
                                "No installed Stremio addons match the selected type filter."
                            },
                            color = AppColors.TextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(filteredStremioAddons, key = { it.manifestUrl }) { addon ->
                        val providerEntry = loadedProviders.firstOrNull {
                            it.isStremio && it.api.sourcePlugin == "stremio::${addon.manifestUrl}"
                        }
                        StremioAddonFlatRow(
                            addon = addon,
                            providerEntry = providerEntry,
                            isActiveProvider = providerEntry != null && providerEntry.key == activeProvider?.key,
                            isRefreshing = refreshingStremioUrl == addon.manifestUrl,
                            onSelectAsActive = { entry ->
                                ExtensionController.selectProvider(entry)
                                AppToastManager.showSuccess("Active provider: ${entry.displayName}")
                            },
                            onToggleEnabled = {
                                val nowEnabled = ExtensionController.toggleStremioAddon(context, addon.manifestUrl)
                                AppToastManager.showSuccess(
                                    if (nowEnabled) "Enabled ${addon.manifest.name}" else "Disabled ${addon.manifest.name}"
                                )
                            },
                            onRefresh = {
                                scope.launch {
                                    refreshingStremioUrl = addon.manifestUrl
                                    val res = ExtensionController.refreshStremioAddon(context, addon.manifestUrl)
                                    refreshingStremioUrl = null
                                    res.onSuccess { updated ->
                                        AppToastManager.showSuccess("Refreshed ${updated.manifest.name} v${updated.manifest.version}")
                                    }.onFailure { err ->
                                        AppToastManager.showError(err.message ?: "Refresh failed")
                                    }
                                }
                            },
                            onDelete = { stremioPendingDelete = addon }
                        )
                    }
                }
            }
        }
    }

    repoPendingDelete?.let { repo ->
        AlertDialog(
            onDismissRequest = { repoPendingDelete = null },
            containerColor = AppColors.Surface,
            titleContentColor = AppColors.TextPrimary,
            textContentColor = AppColors.TextSecondary,
            title = { Text("Remove Repository?", fontWeight = FontWeight.Bold) },
            text = { Text("Remove \"${repo.name}\" and delete extensions downloaded from it?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = repo
                        repoPendingDelete = null
                        if (expandedRepoUrl == target.url) expandedRepoUrl = null
                        scope.launch {
                            ExtensionController.removeRepository(context, target)
                            AppToastManager.showSuccess("Removed \"${target.name}\"")
                        }
                    }
                ) {
                    Text("Remove", color = AppColors.Danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { repoPendingDelete = null }) {
                    Text("Cancel", color = AppColors.TextSecondary)
                }
            }
        )
    }

    stremioPendingDelete?.let { addon ->
        AlertDialog(
            onDismissRequest = { stremioPendingDelete = null },
            containerColor = AppColors.Surface,
            titleContentColor = AppColors.TextPrimary,
            textContentColor = AppColors.TextSecondary,
            title = { Text("Remove Stremio Addon?", fontWeight = FontWeight.Bold) },
            text = { Text("Remove \"${addon.manifest.name}\" from your installed Stremio addons?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = addon
                        stremioPendingDelete = null
                        ExtensionController.removeStremioAddon(context, target.manifestUrl)
                        AppToastManager.showSuccess("Removed \"${target.manifest.name}\"")
                    }
                ) {
                    Text("Remove", color = AppColors.Danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { stremioPendingDelete = null }) {
                    Text("Cancel", color = AppColors.TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun SourceAddInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isAdding: Boolean,
    onSubmit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppColors.SurfaceCard)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = AppIcons.Link,
                    contentDescription = null,
                    tint = AppColors.TextMuted,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    enabled = !isAdding,
                    textStyle = TextStyle(color = AppColors.TextPrimary, fontSize = 14.sp),
                    cursorBrush = SolidColor(AppColors.Primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = AppColors.TextMuted,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        inner()
                    }
                )
                if (value.isNotEmpty() && !isAdding) {
                    IconButton(
                        onClick = { onValueChange("") },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Filled.Close, "Clear", tint = AppColors.TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Button(
                onClick = onSubmit,
                enabled = value.isNotBlank() && !isAdding,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Primary,
                    contentColor = Color.Black,
                    disabledContainerColor = AppColors.SurfaceCard,
                    disabledContentColor = AppColors.TextMuted
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier.height(46.dp)
            ) {
                if (isAdding) {
                    M3InlineSpinner(size = 18.dp, color = AppColors.Primary)
                } else {
                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun ProviderUnderlineTab(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier.clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = if (selected) AppColors.Primary else AppColors.TextMuted,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(vertical = 12.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .height(2.dp)
                .background(if (selected) AppColors.Primary else Color.Transparent)
        )
    }
}

@Composable
private fun RepositoryFlatItem(
    repo: RepositoryData,
    isExpanded: Boolean,
    isLoading: Boolean,
    plugins: List<SitePlugin>,
    filterQuery: String,
    onFilterChange: (String) -> Unit,
    installingKeys: Set<String>,
    onToggleExpand: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    onInstall: (SitePlugin) -> Unit,
    onUninstall: (SitePlugin) -> Unit
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleExpand() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!repo.iconUrl.isNullOrBlank()) {
                AsyncImage(
                    model = buildCoilRequest(context, repo.iconUrl),
                    contentDescription = repo.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AppColors.SurfaceCard)
                )
            } else {
                Icon(
                    imageVector = AppIcons.Extension,
                    contentDescription = null,
                    tint = AppColors.TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = repo.name.ifBlank { "Repository" },
                    color = AppColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = repo.description?.takeIf { it.isNotBlank() } ?: repo.url,
                    color = AppColors.TextMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isExpanded) {
                IconButton(onClick = onRefresh, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Filled.Refresh, "Refresh", tint = AppColors.TextSecondary, modifier = Modifier.size(18.dp))
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Filled.Delete, "Remove", tint = AppColors.TextMuted, modifier = Modifier.size(18.dp))
            }
            Icon(
                imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = AppColors.TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.Surface)
            ) {
                if (isLoading && plugins.isEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        M3InlineSpinner(size = 20.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Loading extensions...", color = AppColors.TextSecondary, fontSize = 13.sp)
                    }
                } else if (plugins.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No extensions found in this repository.", color = AppColors.TextMuted, fontSize = 13.sp)
                    }
                } else {
                    if (plugins.size > 5) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                                .height(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(AppColors.SurfaceCard)
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Search, null, tint = AppColors.TextMuted, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            BasicTextField(
                                value = filterQuery,
                                onValueChange = onFilterChange,
                                singleLine = true,
                                textStyle = TextStyle(color = AppColors.TextPrimary, fontSize = 13.sp),
                                cursorBrush = SolidColor(AppColors.Primary),
                                modifier = Modifier.weight(1f),
                                decorationBox = { inner ->
                                    if (filterQuery.isEmpty()) {
                                        Text("Filter ${plugins.size} extensions...", color = AppColors.TextMuted, fontSize = 13.sp)
                                    }
                                    inner()
                                }
                            )
                            if (filterQuery.isNotEmpty()) {
                                IconButton(onClick = { onFilterChange("") }, modifier = Modifier.size(22.dp)) {
                                    Icon(Icons.Filled.Close, "Clear", tint = AppColors.TextMuted, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }

                    val filtered = remember(plugins, filterQuery) {
                        if (filterQuery.isBlank()) plugins
                        else plugins.filter { p ->
                            p.name.contains(filterQuery, ignoreCase = true) ||
                                p.internalName.contains(filterQuery, ignoreCase = true) ||
                                (p.language?.contains(filterQuery, ignoreCase = true) == true) ||
                                (p.description?.contains(filterQuery, ignoreCase = true) == true)
                        }
                    }

                    filtered.forEach { plugin ->
                        val installKey = "${repo.url}::${plugin.internalName}"
                        val isInstalling = installKey in installingKeys
                        val installedVer = ExtensionController.installedVersion(repo.url, plugin)
                        val isInstalled = ExtensionController.isPluginInstalled(repo.url, plugin)
                        val hasUpdate = isInstalled && installedVer != null && plugin.version > installedVer

                        SitePluginFlatRow(
                            plugin = plugin,
                            repoNameBadge = null,
                            isInstalled = isInstalled,
                            hasUpdate = hasUpdate,
                            isInstalling = isInstalling,
                            onInstall = { onInstall(plugin) },
                            onUninstall = { onUninstall(plugin) }
                        )
                    }
                }
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = AppColors.Divider)
    }
}

@Composable
private fun SitePluginFlatRow(
    plugin: SitePlugin,
    repoNameBadge: String?,
    isInstalled: Boolean,
    hasUpdate: Boolean,
    isInstalling: Boolean,
    onInstall: () -> Unit,
    onUninstall: () -> Unit
) {
    val context = LocalContext.current
    val statusColor = when (plugin.status) {
        1 -> AppColors.Success
        2 -> AppColors.Warning
        3 -> AppColors.DubBlue
        else -> AppColors.Danger
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CachedIconTile(
                cachedPath = null,
                remoteUrl = plugin.iconUrl,
                contentDescription = plugin.name,
                size = 36
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = plugin.name,
                        color = AppColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!plugin.language.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = plugin.language.uppercase(),
                            color = AppColors.TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                val metaParts = buildList {
                    if (!repoNameBadge.isNullOrBlank()) add(repoNameBadge)
                    if (plugin.version > 0) add("v${plugin.version}")
                    val types = plugin.tvTypes.orEmpty().take(3)
                    if (types.isNotEmpty()) add(types.joinToString(", "))
                }
                if (metaParts.isNotEmpty()) {
                    Text(
                        text = metaParts.joinToString(" • "),
                        color = AppColors.TextMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (isInstalling) {
                Box(modifier = Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                    M3InlineSpinner(size = 18.dp)
                }
            } else if (hasUpdate) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onInstall) {
                        Text("Update", color = AppColors.Warning, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onUninstall, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Delete, "Uninstall", tint = AppColors.TextMuted, modifier = Modifier.size(18.dp))
                    }
                }
            } else if (isInstalled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Check, "Installed", tint = AppColors.Success, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = onUninstall, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Delete, "Uninstall", tint = AppColors.TextMuted, modifier = Modifier.size(18.dp))
                    }
                }
            } else {
                Button(
                    onClick = onInstall,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.SurfaceElevated,
                        contentColor = AppColors.Primary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(AppIcons.Download, null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Install", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 64.dp, end = 16.dp),
            thickness = 0.5.dp,
            color = AppColors.Divider
        )
    }
}

@Composable
private fun InstalledExtensionFlatRow(
    info: InstalledExtensionInfo,
    activeProviderKey: String?,
    onSelectEntry: (ProviderEntry) -> Unit,
    onUninstall: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CachedIconTile(
                    cachedPath = info.cachedIconPath,
                    remoteUrl = info.iconUrl,
                    contentDescription = info.pluginData.internalName,
                    size = 38
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = info.pluginData.internalName,
                        color = AppColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            if (info.pluginData.version > 0) append("v${info.pluginData.version} • ")
                            append(info.sourceLabel.ifBlank { if (info.pluginData.isOnline) "Installed .cs3" else "Local .cs3" })
                        },
                        color = AppColors.TextMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onUninstall, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Filled.Delete, "Remove", tint = AppColors.Danger, modifier = Modifier.size(19.dp))
                }
            }

            if (info.providedEntries.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.padding(start = 50.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    info.providedEntries.take(4).forEach { entry ->
                        val isActive = entry.key == activeProviderKey
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isActive) AppColors.Primary else AppColors.SurfaceCard)
                                .clickable { onSelectEntry(entry) }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isActive) {
                                Icon(Icons.Filled.Check, null, tint = Color.Black, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = entry.displayName,
                                color = if (isActive) Color.Black else AppColors.TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = AppColors.Divider)
    }
}

@Composable
private fun StremioAddonFlatRow(
    addon: StremioAddonRecord,
    providerEntry: ProviderEntry?,
    isActiveProvider: Boolean,
    isRefreshing: Boolean,
    onSelectAsActive: (ProviderEntry) -> Unit,
    onToggleEnabled: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val cachedIcon = remember(addon.manifest.logo) {
        PluginIconCache.getCachedIconFile(context, addon.manifest.logo)?.absolutePath
    }
    val resources = remember(addon.manifest) {
        addon.manifest.resourceNames().map { r ->
            when (r.lowercase()) {
                "catalog" -> "Catalog"
                "meta" -> "Meta"
                "stream" -> "Stream"
                "subtitles" -> "Subtitles"
                "addon_catalog" -> "Addon Catalog"
                else -> r.replaceFirstChar { it.uppercase() }
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CachedIconTile(
                    cachedPath = cachedIcon,
                    remoteUrl = addon.manifest.logo,
                    contentDescription = addon.manifest.name,
                    size = 40
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = addon.manifest.name,
                            color = if (addon.enabled) AppColors.TextPrimary else AppColors.TextMuted,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "v${addon.manifest.version}",
                            color = AppColors.TextMuted,
                            fontSize = 11.sp
                        )
                    }
                    if (addon.manifest.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = addon.manifest.description,
                            color = AppColors.TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (isRefreshing) {
                    Box(modifier = Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                        M3InlineSpinner(size = 18.dp)
                    }
                } else {
                    IconButton(onClick = onRefresh, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Filled.Refresh, "Refresh", tint = AppColors.TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Filled.Delete, "Remove", tint = AppColors.Danger, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 52.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                resources.take(4).forEach { badge ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(AppColors.SurfaceCard)
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = badge,
                            color = AppColors.TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                if (addon.manifest.catalogs.isNotEmpty()) {
                    Text(
                        text = "${addon.manifest.catalogs.size} cat.",
                        color = AppColors.TextMuted,
                        fontSize = 10.sp
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                if (providerEntry != null && addon.enabled) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isActiveProvider) AppColors.Primary else AppColors.SurfaceElevated)
                            .clickable { onSelectAsActive(providerEntry) }
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isActiveProvider) {
                            Icon(Icons.Filled.Check, null, tint = Color.Black, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                        }
                        Text(
                            text = if (isActiveProvider) "Active" else "Use",
                            color = if (isActiveProvider) Color.Black else AppColors.TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = if (addon.enabled) "On" else "Off",
                    color = if (addon.enabled) AppColors.Success else AppColors.TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(AppColors.SurfaceCard)
                        .clickable { onToggleEnabled() }
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                )
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = AppColors.Divider)
    }
}

@Composable
private fun CachedIconTile(
    cachedPath: String?,
    remoteUrl: String?,
    contentDescription: String,
    size: Int
) {
    val context = LocalContext.current
    val cachedFile = remember(cachedPath) {
        cachedPath?.let { File(it) }?.takeIf { it.isFile && it.length() > 32L }
    }
    var loadFailed by remember(cachedPath, remoteUrl) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.SurfaceCard),
        contentAlignment = Alignment.Center
    ) {
        val dataModel: Any? = when {
            cachedFile != null -> ImageRequest.Builder(context).data(cachedFile).crossfade(180).build()
            !remoteUrl.isNullOrBlank() -> buildCoilRequest(context, remoteUrl)
            else -> null
        }

        if (dataModel != null && !loadFailed) {
            AsyncImage(
                model = dataModel,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                onError = { loadFailed = true },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = AppIcons.Extension,
                contentDescription = null,
                tint = AppColors.TextMuted,
                modifier = Modifier.size((size * 0.55f).dp)
            )
        }
    }
}
