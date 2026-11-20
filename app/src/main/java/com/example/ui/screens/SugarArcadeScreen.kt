package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.ArcadeGameMode
import com.example.data.VaultSecuritySettings

private val ArcadeScreenBg = Color(0xFF130B2B)
private val ArcadeAccentGold = Color(0xFFFFD54F)
private val ArcadeAccentPink = Color(0xFFFF4F8B)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SugarArcadeScreen(
    securitySettings: VaultSecuritySettings,
    onSaveCandyProgress: (score: Int, level: Int) -> Unit,
    onSavePlatformerProgress: (score: Int, level: Int) -> Unit,
    onSelectGameMode: (ArcadeGameMode) -> Unit,
    onOpenSecretVault: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var showRulesDialog by remember { mutableStateOf(false) }
    var restartSignal by remember { mutableIntStateOf(0) }
    val activeMode = securitySettings.selectedGameMode

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(ArcadeScreenBg, Color(0xFF1F1147), Color(0xFF120A2A))
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 920.dp)
                .align(Alignment.Center)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Shared Top Header: Long-press "Sugar Pop" badge to unlock Secret Vault + Game Mode Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.White.copy(alpha = 0.13f),
                    modifier = Modifier
                        .border(
                            1.5.dp,
                            Brush.horizontalGradient(listOf(ArcadeAccentPink, ArcadeAccentGold)),
                            RoundedCornerShape(50)
                        )
                        .clip(RoundedCornerShape(50))
                        .combinedClickable(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onOpenSecretVault()
                            }
                        )
                        .testTag("sugar_pop_secret_trigger")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = "Sugar Pop Logo",
                            tint = ArcadeAccentGold,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Sugar Pop",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Dual Game Switcher Pill (2.5D Platformer <-> Match-3)
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = 0.35f),
                    modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(50))
                ) {
                    Row(
                        modifier = Modifier.padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ArcadeModeChip(
                            selected = activeMode == ArcadeGameMode.PLATFORMER,
                            label = "2.5D Run",
                            icon = Icons.Rounded.DirectionsRun,
                            tag = "arcade_switch_platformer",
                            onClick = { onSelectGameMode(ArcadeGameMode.PLATFORMER) }
                        )
                        ArcadeModeChip(
                            selected = activeMode == ArcadeGameMode.MATCH3,
                            label = "Match-3",
                            icon = Icons.Rounded.GridView,
                            tag = "arcade_switch_match3",
                            onClick = { onSelectGameMode(ArcadeGameMode.MATCH3) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                    IconButton(
                        onClick = { showRulesDialog = true },
                        modifier = Modifier.testTag("game_help_btn")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.HelpOutline,
                            contentDescription = "How to Play",
                            tint = Color.White.copy(alpha = 0.85f)
                        )
                    }
                    IconButton(
                        onClick = { restartSignal++ },
                        modifier = Modifier.testTag("game_restart_btn")
                    ) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = "Restart Level",
                            tint = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            // First-launch-only Vault Setup Notice
            AnimatedVisibility(visible = !securitySettings.hasPinConfigured) {
                Surface(
                    onClick = onOpenSecretVault,
                    color = Color.Black.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ArcadeAccentGold.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                        .testTag("first_launch_vault_setup_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = ArcadeAccentGold,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Tap to Set Secret Photo Vault PIN (or Restore Backup)",
                                style = MaterialTheme.typography.titleSmall,
                                color = ArcadeAccentGold
                            )
                            Text(
                                text = "After setup, long-press \"Sugar Pop\" above anytime to open your hidden vault.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }

            // Active Game Canvas
            Crossfade(
                targetState = activeMode,
                modifier = Modifier.weight(1f),
                label = "arcadeGameModeSwitch"
            ) { mode ->
                when (mode) {
                    ArcadeGameMode.PLATFORMER -> {
                        PlatformerGameContent(
                            initialLevel = securitySettings.platformerLevel,
                            highScore = securitySettings.platformerHighScore,
                            restartSignal = restartSignal,
                            isExternalDialogOpen = showRulesDialog,
                            onSaveProgress = onSavePlatformerProgress
                        )
                    }
                    ArcadeGameMode.MATCH3 -> {
                        CandyMatchGameContent(
                            initialLevel = securitySettings.candyLevel,
                            highScore = securitySettings.candyHighScore,
                            restartSignal = restartSignal,
                            onSaveProgress = onSaveCandyProgress
                        )
                    }
                }
            }
        }
    }

    if (showRulesDialog) {
        AlertDialog(
            onDismissRequest = { showRulesDialog = false },
            title = {
                Text(
                    if (activeMode == ArcadeGameMode.PLATFORMER) {
                        "Sugar Pop — 2.5D Run"
                    } else {
                        "Sugar Pop — Match-3"
                    }
                )
            },
            text = {
                Text(
                    if (activeMode == ArcadeGameMode.PLATFORMER) {
                        "• Hold ◀ / ▶ to run and hold DASH (⚡) for fast long jumps.\n" +
                            "• Tap JUMP for short hops or hold JUMP for full-height leaps.\n" +
                            "• Bonk ? blocks for Coins, Heart Berries, and Rainbow Stars.\n" +
                            "• Stomp Jelly Walkers and kick Shell Beetles across platforms!\n\n" +
                            "Switch between 2.5D Run and Match-3 anytime at the top.\n" +
                            "Pro Tip: Long-press the \"Sugar Pop\" badge to open your Private Vault."
                    } else {
                        "• Swipe or tap two adjacent candies to match 3 or more.\n" +
                            "• Match 4 creates a Striped Candy; match 5 creates a Color Bomb.\n" +
                            "• Use Candy Hammer to smash any tile or Shuffle anytime.\n\n" +
                            "Switch between Match-3 and 2.5D Run anytime at the top.\n" +
                            "Pro Tip: Long-press the \"Sugar Pop\" badge to open your Private Vault."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { showRulesDialog = false }) { Text("Got It") }
            }
        )
    }
}

@Composable
private fun ArcadeModeChip(
    selected: Boolean,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tag: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) ArcadeAccentPink else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .testTag(tag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
