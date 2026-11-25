package com.xtsream.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.plugins.SitePlugin
import com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData
import com.xtsream.app.engine.AddSourceResult
import com.xtsream.app.engine.ExtensionController
import com.xtsream.app.engine.InstalledExtensionInfo
import com.xtsream.app.ui.components.AppIcons
import com.xtsream.app.ui.components.M3InlineSpinner
import com.xtsream.app.ui.components.buildCoilRequest
import com.xtsream.app.ui.theme.AppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class FeedbackMessage(val text: String, val isError: Boolean)

@Composable
fun ProvidersScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    val repositories by ExtensionController.repositories.collectAsState()
    val repoPlugins by ExtensionController.repoPlugins.collectAsState()
    val loadingRepos by ExtensionController.loadingRepos.collectAsState()
    val installedPlugins by ExtensionController.installedPlugins.collectAsState()
    val loadedApis by ExtensionController.loadedApis.collectAsState()
    val activeApi by ExtensionController.activeApi.collectAsState()
    val installingKeys by ExtensionController.installingKeys.collectAsState()

    var sourceInput by remember { mutableStateOf("") }
    var isAdding by remember { mutableStateOf(false) }
    var activeTab by remember { mutableIntStateOf(0) } // 0 = Repositories, 1 = Installed
    var expandedRepoUrl by remember { mutableStateOf<String?>(null) }
    var pluginFilterQuery by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf<FeedbackMessage?>(null) }
    var repoPendingDelete by remember { mutableStateOf<RepositoryData?>(null) }

    LaunchedEffect(feedback) {
        if (feedback != null) {
            delay(5000)
            feedback = null
        }
    }

    fun submitSource() {
        val raw = sourceInput.trim()
        if (raw.isBlank() || isAdding) return
        focusManager.clearFocus()
        isAdding = true
        feedback = null
        scope.launch {
            val result = ExtensionController.addSourceFromInput(context, raw)
            isAdding = false
            result.onSuccess { res ->
                sourceInput = ""
                when (res) {
                    is AddSourceResult.RepositoryAdded -> {
                        activeTab = 0
                        expandedRepoUrl = res.repository.url
                        pluginFilterQuery = ""
                        val extra = if (res.addedChildRepos > 0) " + ${res.addedChildRepos} repos" else ""
                        feedback = FeedbackMessage(
                            text = "Added \"${res.repository.name}\" (${res.plugins.size} extensions$extra)",
                            isError = false
                        )
                    }

                    is AddSourceResult.DirectPluginInstalled -> {
                        activeTab = if (res.addedRepos > 0) 0 else 1
                        val detail = when {
                            res.addedRepos > 0 -> "Installed ${res.pluginName} & added ${res.addedRepos} repositories"
                            res.addedApis.isNotEmpty() -> "Installed ${res.pluginName} (${res.addedApis.joinToString()})"
                            else -> "Installed ${res.pluginName}"
                        }
                        feedback = FeedbackMessage(text = detail, isError = false)
                    }
                }
            }.onFailure { err ->
                feedback = FeedbackMessage(
                    text = err.message ?: "Failed to load \"$raw\"",
                    isError = true
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = AppColors.Primary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Providers",
                    color = AppColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${repositories.size} repositories • ${installedPlugins.size} installed",
                    color = AppColors.TextMuted,
                    fontSize = 12.sp
                )
            }
            if (repositories.isNotEmpty()) {
                IconButton(
                    onClick = {
                        scope.launch {
                            repositories.forEach { repo ->
                                ExtensionController.loadRepositoryPlugins(repo, force = true)
                            }
                            feedback = FeedbackMessage("Refreshed ${repositories.size} repositories", false)
                        }
                    }
                ) {
                    Icon(Icons.Filled.Refresh, "Refresh all", tint = AppColors.TextSecondary)
                }
            }
        }

        // Top Input Bar: Shortname / repo.json / plugins.json / Direct .cs3 URL
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
                        value = sourceInput,
                        onValueChange = { sourceInput = it },
                        singleLine = true,
                        enabled = !isAdding,
                        textStyle = TextStyle(color = AppColors.TextPrimary, fontSize = 14.sp),
                        cursorBrush = SolidColor(AppColors.Primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submitSource() }),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (sourceInput.isEmpty()) {
                                Text(
                                    text = "Shortname (megarepo), repo URL, or .cs3 link",
                                    color = AppColors.TextMuted,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            inner()
                        }
                    )
                    if (sourceInput.isNotEmpty() && !isAdding) {
                        IconButton(
                            onClick = { sourceInput = "" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Filled.Close, "Clear", tint = AppColors.TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Button(
                    onClick = { submitSource() },
                    enabled = sourceInput.isNotBlank() && !isAdding,
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

        // Feedback Banner
        AnimatedVisibility(visible = feedback != null) {
            feedback?.let { msg ->
                val tint = if (msg.isError) AppColors.Danger else AppColors.Success
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AppColors.SurfaceCard)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (msg.isError) Icons.Filled.Warning else Icons.Filled.Check,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = msg.text,
                        color = AppColors.TextPrimary,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { feedback = null }, modifier = Modifier.size(22.dp)) {
                        Icon(Icons.Filled.Close, "Dismiss", tint = AppColors.TextMuted, modifier = Modifier.size(15.dp))
                    }
                }
            }
        }

        // Clean Underline Tabs (YouTube / CloudStream style)
        Row(modifier = Modifier.fillMaxWidth()) {
            ProviderUnderlineTab(
                label = "Repositories (${repositories.size})",
                selected = activeTab == 0,
                modifier = Modifier.weight(1f),
                onClick = { activeTab = 0 }
            )
            ProviderUnderlineTab(
                label = "Installed (${installedPlugins.size})",
                selected = activeTab == 1,
                modifier = Modifier.weight(1f),
                onClick = { activeTab = 1 }
            )
        }
        HorizontalDivider(thickness = 0.5.dp, color = AppColors.Divider)

        if (activeTab == 0) {
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
                            lineHeight = 19.sp
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
                                        feedback = FeedbackMessage("Loaded ${list.size} extensions from ${repo.name}", false)
                                    }.onFailure { err ->
                                        feedback = FeedbackMessage(err.message ?: "Refresh failed", true)
                                    }
                                }
                            },
                            onDelete = { repoPendingDelete = repo },
                            onInstall = { plugin ->
                                scope.launch {
                                    val res = ExtensionController.installPlugin(context, repo.url, plugin)
                                    res.onSuccess { msg ->
                                        feedback = FeedbackMessage(msg, false)
                                    }.onFailure { err ->
                                        feedback = FeedbackMessage(err.message ?: "Failed installing ${plugin.name}", true)
                                    }
                                }
                            },
                            onUninstall = { plugin ->
                                scope.launch {
                                    val res = ExtensionController.uninstallPlugin(context, repo.url, plugin)
                                    res.onSuccess {
                                        feedback = FeedbackMessage("Uninstalled ${plugin.name}", false)
                                    }.onFailure { err ->
                                        feedback = FeedbackMessage(err.message ?: "Uninstall failed", true)
                                    }
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
                            fontSize = 13.sp
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
                            activeApiName = activeApi?.name,
                            onSelectApi = { apiName ->
                                loadedApis.firstOrNull { it.name == apiName }?.let { api ->
                                    ExtensionController.selectProvider(api)
                                    feedback = FeedbackMessage("Active provider: ${api.name}", false)
                                }
                            },
                            onUninstall = {
                                scope.launch {
                                    val res = ExtensionController.uninstallPlugin(info.pluginData.filePath)
                                    res.onSuccess {
                                        feedback = FeedbackMessage("Uninstalled ${info.pluginData.internalName}", false)
                                    }.onFailure { err ->
                                        feedback = FeedbackMessage(err.message ?: "Failed to uninstall", true)
                                    }
                                }
                            }
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
                            feedback = FeedbackMessage("Removed \"${target.name}\"", false)
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
            if (!plugin.iconUrl.isNullOrBlank()) {
                AsyncImage(
                    model = buildCoilRequest(context, plugin.iconUrl),
                    contentDescription = plugin.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AppColors.SurfaceCard)
                )
            } else {
                Icon(AppIcons.Extension, null, tint = AppColors.TextMuted, modifier = Modifier.size(22.dp))
            }

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
    activeApiName: String?,
    onSelectApi: (String) -> Unit,
    onUninstall: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Extension, null, tint = AppColors.TextSecondary, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(14.dp))
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
                            append(if (info.pluginData.isOnline) "Installed .cs3" else "Local .cs3")
                        },
                        color = AppColors.TextMuted,
                        fontSize = 12.sp
                    )
                }
                IconButton(onClick = onUninstall, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Filled.Delete, "Remove", tint = AppColors.Danger, modifier = Modifier.size(19.dp))
                }
            }

            if (info.providedApis.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.padding(start = 36.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    info.providedApis.take(4).forEach { apiName ->
                        val isActive = apiName == activeApiName
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isActive) AppColors.Primary else AppColors.SurfaceCard)
                                .clickable { onSelectApi(apiName) }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isActive) {
                                Icon(Icons.Filled.Check, null, tint = Color.Black, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = apiName,
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
