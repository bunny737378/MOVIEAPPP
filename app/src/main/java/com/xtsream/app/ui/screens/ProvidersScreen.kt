package com.xtsream.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Update
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
import com.xtsream.app.engine.ExtensionController
import com.xtsream.app.engine.InstalledExtensionInfo
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

    var repoInput by remember { mutableStateOf("") }
    var isAddingRepo by remember { mutableStateOf(false) }
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

    fun submitRepo() {
        val raw = repoInput.trim()
        if (raw.isBlank() || isAddingRepo) return
        focusManager.clearFocus()
        isAddingRepo = true
        feedback = null
        scope.launch {
            val result = ExtensionController.addRepositoryFromInput(raw)
            isAddingRepo = false
            result.onSuccess { fetched ->
                repoInput = ""
                activeTab = 0
                expandedRepoUrl = fetched.repository.url
                pluginFilterQuery = ""
                val extra = if (fetched.addedChildRepos > 0) " + ${fetched.addedChildRepos} included repos" else ""
                feedback = FeedbackMessage(
                    text = "Added \"${fetched.repository.name}\" (${fetched.plugins.size} extensions$extra)",
                    isError = false
                )
            }.onFailure { err ->
                feedback = FeedbackMessage(
                    text = err.message ?: "Failed to load repository \"$raw\"",
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = AppColors.TextPrimary)
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
                    color = AppColors.TextSecondary,
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
                            feedback = FeedbackMessage("Refreshed ${repositories.size} repository list(s)", false)
                        }
                    }
                ) {
                    Icon(Icons.Filled.Refresh, "Refresh all", tint = AppColors.TextSecondary)
                }
            }
        }

        // Top Repository Shortname / URL Input Bar
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
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppColors.SurfaceCard)
                        .border(1.dp, AppColors.BorderStrong, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Link,
                        contentDescription = null,
                        tint = AppColors.Accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    BasicTextField(
                        value = repoInput,
                        onValueChange = { repoInput = it },
                        singleLine = true,
                        enabled = !isAddingRepo,
                        textStyle = TextStyle(color = AppColors.TextPrimary, fontSize = 14.sp),
                        cursorBrush = SolidColor(AppColors.Accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submitRepo() }),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (repoInput.isEmpty()) {
                                Text(
                                    text = "Repo shortname (e.g. megarepo) or URL",
                                    color = AppColors.TextMuted,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            inner()
                        }
                    )
                    if (repoInput.isNotEmpty() && !isAddingRepo) {
                        IconButton(
                            onClick = { repoInput = "" },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(Icons.Filled.Close, "Clear", tint = AppColors.TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Button(
                    onClick = { submitRepo() },
                    enabled = repoInput.isNotBlank() && !isAddingRepo,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.Accent,
                        contentColor = Color(0xFF071326),
                        disabledContainerColor = AppColors.SurfaceElevated,
                        disabledContentColor = AppColors.TextMuted
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    if (isAddingRepo) {
                        CircularProgressIndicator(
                            color = AppColors.Accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Supports CloudStream shortnames (megarepo, hexated, phisherrepo), !codes, and direct repo.json URLs.",
                color = AppColors.TextMuted,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
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
                        .clip(RoundedCornerShape(10.dp))
                        .background(tint.copy(alpha = 0.14f))
                        .border(0.5.dp, tint.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (msg.isError) Icons.Filled.ErrorOutline else Icons.Filled.Check,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = msg.text,
                        color = AppColors.TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { feedback = null },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Filled.Close, "Dismiss", tint = AppColors.TextSecondary, modifier = Modifier.size(15.dp))
                    }
                }
            }
        }

        // Sub-Tabs: Repositories / Installed
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 10.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(AppColors.SurfaceCard)
                .border(0.5.dp, AppColors.Border, RoundedCornerShape(10.dp))
                .padding(3.dp)
        ) {
            ProviderTabButton(
                label = "Repositories (${repositories.size})",
                selected = activeTab == 0,
                modifier = Modifier.weight(1f),
                onClick = { activeTab = 0 }
            )
            ProviderTabButton(
                label = "Installed (${installedPlugins.size})",
                selected = activeTab == 1,
                modifier = Modifier.weight(1f),
                onClick = { activeTab = 1 }
            )
        }

        HorizontalDivider(thickness = 0.5.dp, color = AppColors.Border)

        if (activeTab == 0) {
            if (repositories.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(AppColors.SurfaceCard)
                                .border(0.5.dp, AppColors.Border, RoundedCornerShape(18.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FolderOpen,
                                contentDescription = null,
                                tint = AppColors.TextMuted,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Repositories Added Yet",
                            color = AppColors.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Type a CloudStream repo shortname like \"megarepo\" in the bar above and tap Add to list all its extensions.",
                            color = AppColors.TextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
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

                        RepositoryCard(
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
            // Installed Extensions Tab
            if (installedPlugins.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Extension, null, tint = AppColors.TextMuted, modifier = Modifier.size(44.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No Extensions Installed",
                            color = AppColors.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Open a repository in the Repositories tab and tap Install on any .cs3 extension.",
                            color = AppColors.TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(installedPlugins, key = { it.pluginData.filePath }) { info ->
                        InstalledExtensionCard(
                            info = info,
                            activeApiName = activeApi?.name,
                            onSelectApi = { apiName ->
                                loadedApis.firstOrNull { it.name == apiName }?.let { api ->
                                    ExtensionController.selectProvider(api)
                                    feedback = FeedbackMessage("Active Home provider set to ${api.name}", false)
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

    // Confirm Repo Deletion Dialog
    repoPendingDelete?.let { repo ->
        AlertDialog(
            onDismissRequest = { repoPendingDelete = null },
            containerColor = AppColors.Surface,
            titleContentColor = AppColors.TextPrimary,
            textContentColor = AppColors.TextSecondary,
            title = { Text("Remove Repository?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Remove \"${repo.name}\" and delete extensions downloaded from this repository?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = repo
                        repoPendingDelete = null
                        if (expandedRepoUrl == target.url) expandedRepoUrl = null
                        scope.launch {
                            ExtensionController.removeRepository(context, target)
                            feedback = FeedbackMessage("Removed repository \"${target.name}\"", false)
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
private fun ProviderTabButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) AppColors.Accent else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color(0xFF071326) else AppColors.TextSecondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun RepositoryCard(
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.SurfaceCard)
            .border(
                width = if (isExpanded) 1.dp else 0.5.dp,
                color = if (isExpanded) AppColors.Accent.copy(alpha = 0.45f) else AppColors.Border,
                shape = RoundedCornerShape(14.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleExpand() }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppColors.SurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                if (!repo.iconUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = buildCoilRequest(context, repo.iconUrl),
                        contentDescription = repo.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Filled.Extension, null, tint = AppColors.Accent, modifier = Modifier.size(22.dp))
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = repo.name.ifBlank { "Repository" },
                    color = AppColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!repo.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = repo.description!!,
                        color = AppColors.TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = repo.url,
                        color = AppColors.TextMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isExpanded) {
                IconButton(onClick = onRefresh, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Filled.Refresh, "Refresh repo", tint = AppColors.TextSecondary, modifier = Modifier.size(18.dp))
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Filled.DeleteOutline, "Remove repo", tint = AppColors.TextMuted, modifier = Modifier.size(18.dp))
            }
            Icon(
                imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = AppColors.TextSecondary,
                modifier = Modifier.size(22.dp)
            )
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.Surface)
            ) {
                HorizontalDivider(thickness = 0.5.dp, color = AppColors.Border)

                if (isLoading && plugins.isEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = AppColors.Accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Fetching extension list...", color = AppColors.TextSecondary, fontSize = 13.sp)
                    }
                } else if (plugins.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No extensions found in this repository.",
                            color = AppColors.TextMuted,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    if (plugins.size > 5) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .height(38.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(AppColors.SurfaceCard)
                                .border(0.5.dp, AppColors.Border, RoundedCornerShape(9.dp))
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
                                cursorBrush = SolidColor(AppColors.Accent),
                                modifier = Modifier.weight(1f),
                                decorationBox = { inner ->
                                    if (filterQuery.isEmpty()) {
                                        Text(
                                            text = "Filter ${plugins.size} extensions...",
                                            color = AppColors.TextMuted,
                                            fontSize = 13.sp
                                        )
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

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        filtered.forEach { plugin ->
                            val installKey = "${repo.url}::${plugin.internalName}"
                            val isInstalling = installKey in installingKeys
                            val installedVer = ExtensionController.installedVersion(repo.url, plugin)
                            val isInstalled = ExtensionController.isPluginInstalled(repo.url, plugin)
                            val hasUpdate = isInstalled && installedVer != null && plugin.version > installedVer

                            SitePluginRow(
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
        }
    }
}

@Composable
private fun SitePluginRow(
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
        3 -> AppColors.Accent
        else -> AppColors.Danger
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.SurfaceCard)
            .border(0.5.dp, AppColors.Border, RoundedCornerShape(12.dp))
            .padding(11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(AppColors.SurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            if (!plugin.iconUrl.isNullOrBlank()) {
                AsyncImage(
                    model = buildCoilRequest(context, plugin.iconUrl),
                    contentDescription = plugin.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(Icons.Filled.Extension, null, tint = AppColors.TextMuted, modifier = Modifier.size(20.dp))
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = plugin.name,
                    color = AppColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (!plugin.language.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(AppColors.SurfaceElevated)
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = plugin.language.uppercase(),
                            color = AppColors.TextSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            val metaParts = buildList {
                if (plugin.version > 0) add("v${plugin.version}")
                val types = plugin.tvTypes.orEmpty().take(3)
                if (types.isNotEmpty()) add(types.joinToString(", "))
            }
            if (metaParts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = metaParts.joinToString(" • "),
                    color = AppColors.TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!plugin.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = plugin.description!!,
                    color = AppColors.TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (isInstalling) {
            Box(
                modifier = Modifier.size(36.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = AppColors.Accent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
            }
        } else if (hasUpdate) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = onInstall,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.Warning,
                        contentColor = Color(0xFF1A1200)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Filled.Update, null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Update", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onUninstall, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.DeleteOutline, "Uninstall", tint = AppColors.Danger, modifier = Modifier.size(18.dp))
                }
            }
        } else if (isInstalled) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(AppColors.Success.copy(alpha = 0.14f))
                        .border(0.5.dp, AppColors.Success.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.Check, "Installed", tint = AppColors.Success, modifier = Modifier.size(15.dp))
                }
                IconButton(onClick = onUninstall, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.DeleteOutline, "Uninstall", tint = AppColors.Danger, modifier = Modifier.size(18.dp))
                }
            }
        } else {
            Button(
                onClick = onInstall,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Accent,
                    contentColor = Color(0xFF071326)
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Icon(Icons.Filled.Download, null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Install", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun InstalledExtensionCard(
    info: InstalledExtensionInfo,
    activeApiName: String?,
    onSelectApi: (String) -> Unit,
    onUninstall: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.SurfaceCard)
            .border(0.5.dp, AppColors.Border, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppColors.AccentDim),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Extension, null, tint = AppColors.Accent, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = info.pluginData.internalName,
                    color = AppColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        if (info.pluginData.version > 0) append("v${info.pluginData.version} • ")
                        append(if (info.pluginData.isOnline) "Online .cs3" else "Local .cs3")
                    },
                    color = AppColors.TextMuted,
                    fontSize = 11.sp
                )
            }
            OutlinedButton(
                onClick = onUninstall,
                border = BorderStroke(0.5.dp, AppColors.Danger.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(Icons.Filled.DeleteOutline, null, tint = AppColors.Danger, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Remove", color = AppColors.Danger, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        if (info.providedApis.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                info.providedApis.take(4).forEach { apiName ->
                    val isActive = apiName == activeApiName
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isActive) AppColors.Accent else AppColors.SurfaceElevated)
                            .clickable { onSelectApi(apiName) }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isActive) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = Color(0xFF071326),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = apiName,
                            color = if (isActive) Color(0xFF071326) else AppColors.TextPrimary,
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
}
