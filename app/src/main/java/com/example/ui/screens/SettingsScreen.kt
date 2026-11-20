package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoDelete
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockReset
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.ScreenshotMonitor
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.VaultUiState
import com.example.ui.theme.ThemeMode
import com.example.util.formatFileSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: VaultUiState,
    onChangePin: () -> Unit,
    onLockNow: () -> Unit,
    onAutoLockChanged: (Boolean) -> Unit,
    onScreenShieldChanged: (Boolean) -> Unit,
    onPromptDeleteOriginalChanged: (Boolean) -> Unit,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onDefaultSpeedChanged: (Float) -> Unit,
    onExportBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    onWipeVault: () -> Unit
) {
    var showWipeConfirm by remember { mutableStateOf(false) }
    val settings = uiState.securitySettings
    val speedOptions = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_title)) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Vault Summary Card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Private Local Storage Active",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "${uiState.allMedia.size} hidden files · ${formatFileSize(uiState.totalSizeBytes)} shielded",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = onLockNow,
                        modifier = Modifier.testTag("settings_lock_now_btn")
                    ) {
                        Icon(Icons.Rounded.Lock, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Lock")
                    }
                }
            }

            // Security Section
            SectionHeader("Access & Privacy Shield")
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    SettingActionRow(
                        icon = Icons.Rounded.LockReset,
                        title = "Change Vault PIN",
                        subtitle = "Update your 4-digit access passcode",
                        testTag = "change_pin_row",
                        onClick = onChangePin
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingSwitchRow(
                        icon = Icons.Rounded.Lock,
                        title = "Auto-Lock on Background",
                        subtitle = "Require PIN whenever you switch away from the app",
                        checked = settings.autoLockOnBackground,
                        onCheckedChange = onAutoLockChanged,
                        testTag = "auto_lock_switch"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingSwitchRow(
                        icon = Icons.Rounded.ScreenshotMonitor,
                        title = "Screenshot & Recents Shield",
                        subtitle = "Blocks screenshots, screen recording & app-switcher previews",
                        checked = settings.screenShieldEnabled,
                        onCheckedChange = onScreenShieldChanged,
                        testTag = "screen_shield_switch"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingSwitchRow(
                        icon = Icons.Rounded.AutoDelete,
                        title = "Prompt to Remove Gallery Originals",
                        subtitle = "Ask to delete public copies right after hiding into vault",
                        checked = settings.promptDeleteOriginal,
                        onCheckedChange = onPromptDeleteOriginalChanged,
                        testTag = "prompt_delete_switch"
                    )
                }
            }

            // Advanced Player Preferences
            SectionHeader("Built-in Video Player")
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Default Playback Speed",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Applied when opening videos in the Media3 player",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(speedOptions) { speed ->
                            FilterChip(
                                selected = settings.defaultPlaybackSpeed == speed,
                                onClick = { onDefaultSpeedChanged(speed) },
                                label = { Text("${speed}x") }
                            )
                        }
                    }
                }
            }

            // Appearance
            SectionHeader("Appearance")
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Vault Theme",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.themeMode == mode,
                                onClick = { onThemeModeChanged(mode) },
                                label = {
                                    Text(
                                        when (mode) {
                                            ThemeMode.DARK -> "Obsidian Dark"
                                            ThemeMode.LIGHT -> "Daylight"
                                            ThemeMode.SYSTEM -> "System"
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // Backup & Reinstall Recovery
            SectionHeader("Backup & Reinstall Recovery")
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    SettingActionRow(
                        icon = Icons.Rounded.Backup,
                        title = "Export Vault Backup",
                        subtitle = "Save photos, videos, albums, PIN & game progress as a portable archive",
                        testTag = "export_backup_row",
                        onClick = onExportBackup
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingActionRow(
                        icon = Icons.Rounded.Restore,
                        title = "Restore Vault Backup",
                        subtitle = "Recover all hidden media, albums, PIN & scores after a reinstall",
                        testTag = "restore_backup_row",
                        onClick = onRestoreBackup
                    )
                }
            }

            // Danger Zone
            SectionHeader("Danger Zone")
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                SettingActionRow(
                    icon = Icons.Rounded.DeleteForever,
                    title = "Wipe Entire Vault",
                    subtitle = "Permanently delete all hidden photos, videos, and albums",
                    tint = MaterialTheme.colorScheme.error,
                    testTag = "wipe_vault_row",
                    onClick = { showWipeConfirm = true }
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showWipeConfirm) {
        AlertDialog(
            onDismissRequest = { showWipeConfirm = false },
            icon = {
                Icon(
                    Icons.Rounded.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Wipe Entire Vault?") },
            text = {
                Text("This will permanently erase all ${uiState.allMedia.size} hidden files and all private albums. This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showWipeConfirm = false
                        onWipeVault()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Wipe Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
}

@Composable
private fun SettingActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    testTag: String,
    tint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.titleSmall) },
        supportingContent = {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = { Icon(icon, contentDescription = null, tint = tint) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag)
    )
}

@Composable
private fun SettingSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.titleSmall) },
        supportingContent = {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag(testTag)
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
    )
}
