package com.example.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.data.preferences.DefaultQualityPreference
import com.example.data.preferences.DownloadStorageLocation
import com.example.ui.theme.GlassCard
import com.example.ui.theme.VortexThemeMode

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showClearHistoryConfirm by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showLocationDialog by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.setNotificationsEnabled(granted)
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 820.dp),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text("Settings", style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = "Appearance, Engine & Privacy Controls",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            // Theme Mode Section
            item {
                GlassCard {
                    Column {
                        SectionHeader(Icons.Rounded.Palette, "Appearance & Theme")
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = settings.themeMode == VortexThemeMode.DARK,
                                onClick = { viewModel.setThemeMode(VortexThemeMode.DARK) },
                                label = { Text("Dark Mode") },
                                modifier = Modifier.weight(1f).testTag("theme_dark_chip")
                            )
                            FilterChip(
                                selected = settings.themeMode == VortexThemeMode.LIGHT,
                                onClick = { viewModel.setThemeMode(VortexThemeMode.LIGHT) },
                                label = { Text("Light Mode") },
                                modifier = Modifier.weight(1f).testTag("theme_light_chip")
                            )
                            FilterChip(
                                selected = settings.themeMode == VortexThemeMode.DYNAMIC,
                                onClick = { viewModel.setThemeMode(VortexThemeMode.DYNAMIC) },
                                label = { Text("Dynamic") },
                                modifier = Modifier.weight(1f).testTag("theme_dynamic_chip")
                            )
                        }
                    }
                }
            }

            // Download Engine Settings
            item {
                GlassCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader(Icons.Rounded.Speed, "Download Engine")

                        SettingActionRow(
                            icon = Icons.Rounded.Folder,
                            title = "Download Location",
                            subtitle = settings.storageLocation.label,
                            onClick = { showLocationDialog = true }
                        )

                        SettingActionRow(
                            icon = Icons.Rounded.HighQuality,
                            title = "Default Video Quality",
                            subtitle = settings.defaultQuality.label,
                            onClick = { showQualityDialog = true }
                        )

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Maximum Simultaneous Downloads", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "${settings.maxSimultaneousDownloads}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                            Slider(
                                value = settings.maxSimultaneousDownloads.toFloat(),
                                onValueChange = { viewModel.setMaxSimultaneous(it.toInt()) },
                                valueRange = 1f..5f,
                                steps = 3
                            )
                        }

                        SettingSwitchRow(
                            title = "Download Over Wi-Fi Only",
                            subtitle = "Pause queued jobs on metered connections",
                            checked = settings.wifiOnly,
                            onCheckedChange = viewModel::setWifiOnly
                        )

                        SettingSwitchRow(
                            title = "Mobile Data Download Permission",
                            subtitle = "Allow transfers over cellular networks",
                            checked = settings.allowMobileData,
                            onCheckedChange = viewModel::setAllowMobileData
                        )

                        SettingSwitchRow(
                            title = "Auto Resume",
                            subtitle = "Automatically resume interrupted downloads when network returns",
                            checked = settings.autoResume,
                            onCheckedChange = viewModel::setAutoResume
                        )
                    }
                }
            }

            // Notifications & Clipboard Privacy
            item {
                GlassCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader(Icons.Rounded.Notifications, "Notifications & Clipboard")

                        SettingSwitchRow(
                            title = "Notification Settings",
                            subtitle = "Show live progress and completion alerts",
                            checked = settings.notificationsEnabled,
                            onCheckedChange = { wantEnabled ->
                                if (wantEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (!granted) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        viewModel.setNotificationsEnabled(true)
                                    }
                                } else {
                                    viewModel.setNotificationsEnabled(wantEnabled)
                                }
                            }
                        )

                        SettingSwitchRow(
                            title = "Local Clipboard Link Detection",
                            subtitle = "Detect media URLs locally on device (never uploaded)",
                            checked = settings.clipboardAutoDetect,
                            onCheckedChange = viewModel::setClipboardAutoDetect
                        )
                    }
                }
            }

            // Data, Security, Privacy Policy & About
            item {
                GlassCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader(Icons.Rounded.Security, "Security, Privacy & Info")

                        SettingActionRow(
                            icon = Icons.Rounded.DeleteSweep,
                            title = "Clear Download History",
                            subtitle = "Remove completed, failed, and cancelled logs",
                            onClick = { showClearHistoryConfirm = true }
                        )

                        SettingActionRow(
                            icon = Icons.Rounded.Policy,
                            title = "Privacy Policy",
                            subtitle = "Zero data collection • HTTPS-only • Local Keystore",
                            onClick = { showPrivacyDialog = true }
                        )

                        SettingActionRow(
                            icon = Icons.Rounded.Info,
                            title = "About Application",
                            subtitle = "Vortex Video Downloader • Version ${BuildConfig.VERSION_NAME}",
                            onClick = { showAboutDialog = true }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            title = { Text("Default Video Quality") },
            text = {
                Column {
                    DefaultQualityPreference.entries.forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setDefaultQuality(option)
                                    showQualityDialog = false
                                }
                                .padding(vertical = 6.dp)
                        ) {
                            RadioButton(
                                selected = settings.defaultQuality == option,
                                onClick = {
                                    viewModel.setDefaultQuality(option)
                                    showQualityDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(option.label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showQualityDialog = false }) { Text("Close") } }
        )
    }

    if (showLocationDialog) {
        AlertDialog(
            onDismissRequest = { showLocationDialog = false },
            title = { Text("Download Location") },
            text = {
                Column {
                    DownloadStorageLocation.entries.forEach { loc ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setStorageLocation(loc)
                                    showLocationDialog = false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = settings.storageLocation == loc,
                                onClick = {
                                    viewModel.setStorageLocation(loc)
                                    showLocationDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(loc.label, style = MaterialTheme.typography.titleMedium)
                                Text(loc.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLocationDialog = false }) { Text("Close") } }
        )
    }

    if (showClearHistoryConfirm) {
        AlertDialog(
            onDismissRequest = { showClearHistoryConfirm = false },
            title = { Text("Clear Download & Browser History?") },
            text = { Text("This clears history records from the app database.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.clearDownloadHistory()
                    viewModel.clearBrowserHistory()
                    showClearHistoryConfirm = false
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { showClearHistoryConfirm = false }) { Text("Cancel") } }
        )
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Vortex Privacy Policy") },
            text = {
                Text(
                    "• Zero Analytics or Tracking: Vortex collects, stores, or sells zero personal data.\n" +
                        "• HTTPS Only: Cleartext HTTP requests and private/loopback SSRF addresses are blocked.\n" +
                        "• Android Keystore: Sensitive local vault data is encrypted via AES-256-GCM.\n" +
                        "• Clipboard Privacy: Clipboard URL checks happen strictly on-device and are never transmitted.\n" +
                        "• Content Rights: Vortex respects DRM, authentication gates, and platform Terms of Service."
                )
            },
            confirmButton = { Button(onClick = { showPrivacyDialog = false }) { Text("Close") } }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("Vortex Video Downloader") },
            text = {
                Text(
                    "App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
                        "Package: ${BuildConfig.APPLICATION_ID}\n\n" +
                        "Built with Jetpack Compose, Material 3, Media3 ExoPlayer, Room, WorkManager, and OkHttp TLS 1.3."
                )
            },
            confirmButton = { Button(onClick = { showAboutDialog = false }) { Text("OK") } }
        )
    }
}

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
