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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import com.xtsream.app.R
import com.xtsream.app.data.WatchlistStore
import com.xtsream.app.engine.ExtensionController
import com.xtsream.app.ui.components.AppIcons
import com.xtsream.app.ui.components.AppToastManager
import com.xtsream.app.ui.theme.AppColors

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
fun SettingsScreen() {
    val context = LocalContext.current
    val recentSearches by WatchlistStore.recentSearches.collectAsState()
    val recentViewed by WatchlistStore.recentlyViewed.collectAsState()

    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val dnsKey = remember { context.getString(R.string.dns_key) }
    var selectedDns by remember { mutableIntStateOf(prefs.getInt(dnsKey, 0)) }
    var showDnsDialog by remember { mutableStateOf(false) }

    val currentDnsLabel = remember(selectedDns) {
        DNS_OPTIONS.firstOrNull { it.id == selectedDns }?.label ?: "System Default (Off)"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .statusBarsPadding()
    ) {
        Text(
            text = "Settings",
            color = AppColors.TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        )

        HorizontalDivider(thickness = 0.5.dp, color = AppColors.Divider)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item(key = "network_group") {
                SettingsCategoryHeader("NETWORK")
                SettingsFlatRow(
                    icon = AppIcons.Dns,
                    title = "DNS over HTTPS",
                    subtitle = currentDnsLabel,
                    showChevron = true,
                    onClick = { showDnsDialog = true }
                )
                HorizontalDivider(thickness = 0.5.dp, color = AppColors.Divider)
            }

            item(key = "storage_group") {
                SettingsCategoryHeader("STORAGE & DATA")
                SettingsFlatRow(
                    icon = Icons.Filled.Delete,
                    title = "Clear HTTP Cache",
                    subtitle = "Free cached network responses",
                    onClick = {
                        val freedBytes = ExtensionController.clearHttpCache(context)
                        val kb = freedBytes / 1024.0
                        val formatted = if (kb >= 1024.0) String.format("%.1f MB", kb / 1024.0) else String.format("%.0f KB", kb)
                        AppToastManager.showSuccess("Cleared HTTP cache ($formatted freed)")
                    }
                )
                SettingsFlatRow(
                    icon = Icons.Filled.Delete,
                    title = "Clear Search History",
                    subtitle = "${recentSearches.size} saved query(s)",
                    onClick = {
                        WatchlistStore.clearRecentSearches()
                        AppToastManager.showSuccess("Search history cleared")
                    }
                )
                SettingsFlatRow(
                    icon = AppIcons.History,
                    title = "Clear Recently Viewed",
                    subtitle = "${recentViewed.size} title(s) in history",
                    onClick = {
                        WatchlistStore.clearRecentlyViewed()
                        AppToastManager.showSuccess("Recently viewed cleared")
                    }
                )
            }
        }
    }

    if (showDnsDialog) {
        AlertDialog(
            onDismissRequest = { showDnsDialog = false },
            containerColor = AppColors.Surface,
            titleContentColor = AppColors.TextPrimary,
            title = { Text("DNS over HTTPS", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    DNS_OPTIONS.forEach { option ->
                        val isSelected = option.id == selectedDns
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    selectedDns = option.id
                                    prefs.edit().putInt(dnsKey, option.id).apply()
                                    ExtensionController.reinitNetworkClients(context)
                                    showDnsDialog = false
                                    AppToastManager.showSuccess("DNS set to ${option.label}")
                                }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .border(
                                        width = if (isSelected) 5.dp else 1.5.dp,
                                        color = if (isSelected) AppColors.Primary else AppColors.TextMuted,
                                        shape = CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = option.label,
                                    color = AppColors.TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(option.subtitle, color = AppColors.TextMuted, fontSize = 11.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDnsDialog = false }) {
                    Text("Close", color = AppColors.Primary)
                }
            }
        )
    }
}

@Composable
private fun SettingsCategoryHeader(title: String) {
    Text(
        text = title,
        color = AppColors.TextMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp)
    )
}

@Composable
private fun SettingsFlatRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    showChevron: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AppColors.TextSecondary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = AppColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = AppColors.TextMuted,
                fontSize = 12.sp
            )
        }
        if (showChevron) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = AppColors.TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
