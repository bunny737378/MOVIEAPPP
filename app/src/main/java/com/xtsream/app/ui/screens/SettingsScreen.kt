package com.xtsream.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.network.initClient
import com.xtsream.app.R
import com.xtsream.app.data.WatchlistStore
import com.xtsream.app.engine.ExtensionController
import com.xtsream.app.ui.theme.AppColors
import kotlinx.coroutines.delay

private data class DnsOption(val id: Int, val label: String, val subtitle: String)

private val DNS_OPTIONS = listOf(
    DnsOption(0, "System Default (Off)", "Use device network DNS"),
    DnsOption(1, "Google DNS", "dns.google (DoH)"),
    DnsOption(2, "Cloudflare DNS", "1.1.1.1 (DoH)"),
    DnsOption(4, "AdGuard DNS", "dns.adguard.com (DoH)"),
    DnsOption(5, "DNS.WATCH", "resolver2.dns.watch (DoH)"),
    DnsOption(6, "Quad9", "dns.quad9.net (DoH)"),
    DnsOption(7, "DNS.SB", "doh.dns.sb (DoH)"),
    DnsOption(8, "Canadian Shield", "private.canadianshield.cira.ca (DoH)")
)

@Composable
fun SettingsScreen(
    onOpenProviders: () -> Unit
) {
    val context = LocalContext.current
    val repositories by ExtensionController.repositories.collectAsState()
    val installedPlugins by ExtensionController.installedPlugins.collectAsState()
    val loadedApis by ExtensionController.loadedApis.collectAsState()
    val activeApi by ExtensionController.activeApi.collectAsState()
    val recentSearches by WatchlistStore.recentSearches.collectAsState()
    val recentViewed by WatchlistStore.recentlyViewed.collectAsState()

    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val dnsKey = remember { context.getString(R.string.dns_key) }
    var selectedDns by remember { mutableIntStateOf(prefs.getInt(dnsKey, 0)) }
    var showDnsDialog by remember { mutableStateOf(false) }
    var feedbackBanner by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(feedbackBanner) {
        if (feedbackBanner != null) {
            delay(3000)
            feedbackBanner = null
        }
    }

    val currentDnsLabel = remember(selectedDns) {
        DNS_OPTIONS.firstOrNull { it.id == selectedDns }?.label ?: "System Default (Off)"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Settings",
                color = AppColors.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }

        HorizontalDivider(thickness = 0.5.dp, color = AppColors.Border)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            if (feedbackBanner != null) {
                item(key = "feedback_banner") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppColors.Success.copy(alpha = 0.14f))
                            .border(0.5.dp, AppColors.Success.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = AppColors.Success,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = feedbackBanner!!,
                            color = AppColors.TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Primary Providers / Extensions Entry Card
            item(key = "providers_hero") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("EXTENSIONS")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(AppColors.SurfaceCard)
                            .border(1.dp, AppColors.Accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                            .clickable { onOpenProviders() }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(AppColors.AccentDim),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Extension,
                                contentDescription = null,
                                tint = AppColors.Accent,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Providers",
                                color = AppColors.TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = if (installedPlugins.isEmpty() && repositories.isEmpty()) {
                                    "Add CloudStream repos (e.g. megarepo) & install .cs3 extensions"
                                } else {
                                    "${repositories.size} repo(s) • ${installedPlugins.size} installed • ${loadedApis.size} active"
                                },
                                color = AppColors.TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Open Providers",
                            tint = AppColors.Accent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Network Section
            item(key = "network_section") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("NETWORK")
                    SettingsCardGroup {
                        SettingsActionRow(
                            icon = Icons.Filled.Dns,
                            title = "DNS over HTTPS (DoH)",
                            subtitle = currentDnsLabel,
                            onClick = { showDnsDialog = true }
                        )
                    }
                }
            }

            // Storage & History Section
            item(key = "storage_section") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("STORAGE & HISTORY")
                    SettingsCardGroup {
                        SettingsActionRow(
                            icon = Icons.Filled.CleaningServices,
                            title = "Clear HTTP Cache",
                            subtitle = "Remove cached network responses & repository files",
                            onClick = {
                                val freedBytes = ExtensionController.clearHttpCache(context)
                                val kb = freedBytes / 1024.0
                                val formatted = if (kb >= 1024.0) {
                                    String.format("%.1f MB", kb / 1024.0)
                                } else {
                                    String.format("%.0f KB", kb)
                                }
                                feedbackBanner = "Cleared HTTP cache ($formatted freed)"
                            }
                        )
                        HorizontalDivider(thickness = 0.5.dp, color = AppColors.Border)
                        SettingsActionRow(
                            icon = Icons.Filled.DeleteSweep,
                            title = "Clear Search History",
                            subtitle = "${recentSearches.size} saved search query(s)",
                            onClick = {
                                WatchlistStore.clearRecentSearches()
                                feedbackBanner = "Search history cleared"
                            }
                        )
                        HorizontalDivider(thickness = 0.5.dp, color = AppColors.Border)
                        SettingsActionRow(
                            icon = Icons.Filled.History,
                            title = "Clear Recently Viewed",
                            subtitle = "${recentViewed.size} title(s) in viewing history",
                            onClick = {
                                WatchlistStore.clearRecentlyViewed()
                                feedbackBanner = "Recently viewed history cleared"
                            }
                        )
                    }
                }
            }

            // About / Engine Status
            item(key = "about_section") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("ABOUT")
                    SettingsCardGroup {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(AppColors.SurfaceElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Info,
                                    contentDescription = null,
                                    tint = AppColors.TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "MOVIEAPPP • CloudStream .cs3 Runtime",
                                    color = AppColors.TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Active provider: ${activeApi?.name ?: "None selected"}\nCatalog, search, and metadata engine enabled. Streaming & downloads are disabled in this build.",
                                    color = AppColors.TextSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDnsDialog) {
        AlertDialog(
            onDismissRequest = { showDnsDialog = false },
            containerColor = AppColors.Surface,
            titleContentColor = AppColors.TextPrimary,
            title = {
                Text("DNS over HTTPS", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DNS_OPTIONS.forEach { option ->
                        val isSelected = option.id == selectedDns
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) AppColors.AccentDim else Color.Transparent)
                                .clickable {
                                    selectedDns = option.id
                                    prefs.edit().putInt(dnsKey, option.id).apply()
                                    app.initClient(context.applicationContext)
                                    showDnsDialog = false
                                    feedbackBanner = "DNS set to ${option.label}"
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .border(
                                        width = if (isSelected) 5.dp else 1.5.dp,
                                        color = if (isSelected) AppColors.Accent else AppColors.TextMuted,
                                        shape = CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = option.label,
                                    color = AppColors.TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                                Text(
                                    text = option.subtitle,
                                    color = AppColors.TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDnsDialog = false }) {
                    Text("Close", color = AppColors.Accent)
                }
            }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = AppColors.TextMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

@Composable
private fun SettingsCardGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.SurfaceCard)
            .border(0.5.dp, AppColors.Border, RoundedCornerShape(14.dp)),
        content = content
    )
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(AppColors.SurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AppColors.TextSecondary,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = AppColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = AppColors.TextSecondary,
                fontSize = 12.sp
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = AppColors.TextMuted,
            modifier = Modifier.size(20.dp)
        )
    }
}
