package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.LockStatus
import kotlin.math.roundToInt

@Composable
fun PinLockScreen(
    lockStatus: LockStatus,
    pinEntry: String,
    pinError: String?,
    isChangingPin: Boolean,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onCancelChangePin: () -> Unit,
    onRestoreBackup: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    val shakeOffset = remember { Animatable(0f) }

    LaunchedEffect(pinError) {
        if (pinError != null) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            val offsets = listOf(-18f, 18f, -14f, 14f, -8f, 8f, 0f)
            for (target in offsets) {
                shakeOffset.animateTo(
                    targetValue = target,
                    animationSpec = spring(stiffness = Spring.StiffnessHigh)
                )
            }
        }
    }

    val title = when (lockStatus) {
        LockStatus.SETUP_PIN -> stringResource(R.string.pin_create_title)
        LockStatus.CONFIRM_PIN -> stringResource(R.string.pin_confirm_title)
        else -> stringResource(R.string.pin_unlock_title)
    }
    val subtitle = when (lockStatus) {
        LockStatus.SETUP_PIN -> stringResource(R.string.pin_create_subtitle)
        LockStatus.CONFIRM_PIN -> stringResource(R.string.pin_confirm_subtitle)
        else -> stringResource(R.string.pin_unlock_subtitle)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceContainerLowest,
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceContainerLow
                    )
                )
            )
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        val isCompactHeight = maxHeight < 640.dp
        val keySize = if (isCompactHeight) 60.dp else 72.dp
        val keyRowSpacing = if (isCompactHeight) 10.dp else 14.dp
        val keyColSpacing = if (isCompactHeight) 18.dp else 24.dp
        val emblemSize = if (isCompactHeight) 56.dp else 72.dp
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .heightIn(min = maxHeight)
                .padding(
                    horizontal = 24.dp,
                    vertical = if (isCompactHeight) 10.dp else 20.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Header & Vault Shield Emblem
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = if (isCompactHeight) 4.dp else 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(emblemSize)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                            )
                        )
                        .border(
                            1.5.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (lockStatus == LockStatus.LOCKED) {
                            Icons.Rounded.Lock
                        } else {
                            Icons.Rounded.Shield
                        },
                        contentDescription = "Keepsake Vault Shield",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(if (isCompactHeight) 28.dp else 34.dp)
                    )
                }

                Spacer(Modifier.height(if (isCompactHeight) 12.dp else 20.dp))
                Text(
                    text = title,
                    style = if (isCompactHeight) {
                        MaterialTheme.typography.titleLarge
                    } else {
                        MaterialTheme.typography.headlineMedium
                    },
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(if (isCompactHeight) 16.dp else 28.dp))

                // Animated 4-dot PIN indicator
                Row(
                    modifier = Modifier.offset { IntOffset(shakeOffset.value.roundToInt(), 0) },
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(4) { index ->
                        val filled = index < pinEntry.length
                        val size by animateDpAsState(
                            targetValue = if (filled) 18.dp else 14.dp,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "pinDotSize"
                        )
                        val color by animateColorAsState(
                            targetValue = when {
                                pinError != null -> MaterialTheme.colorScheme.error
                                filled -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.outline
                            },
                            label = "pinDotColor"
                        )
                        Box(
                            modifier = Modifier
                                .size(size)
                                .clip(CircleShape)
                                .background(
                                    if (filled) color else MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                                .border(1.5.dp, color, CircleShape)
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                Box(modifier = Modifier.height(22.dp), contentAlignment = Alignment.Center) {
                    if (pinError != null) {
                        Text(
                            text = pinError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.testTag("pin_error_text")
                        )
                    }
                }
            }

            // Keypad
            Column(
                verticalArrangement = Arrangement.spacedBy(keyRowSpacing),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = if (isCompactHeight) 6.dp else 12.dp)
            ) {
                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9")
                )
                for (row in rows) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(keyColSpacing),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (digit in row) {
                            PinDigitKey(
                                label = digit,
                                keySize = keySize,
                                compact = isCompactHeight,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onDigit(digit)
                                }
                            )
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(keyColSpacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Clear key
                    Box(
                        modifier = Modifier
                            .size(keySize)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = true),
                                onClick = onClear
                            )
                            .testTag("pin_key_clear"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "CLR",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    PinDigitKey(
                        label = "0",
                        keySize = keySize,
                        compact = isCompactHeight,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onDigit("0")
                        }
                    )

                    // Backspace key
                    Box(
                        modifier = Modifier
                            .size(keySize)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = true),
                                onClick = onBackspace
                            )
                            .testTag("pin_key_backspace"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Backspace,
                            contentDescription = "Backspace",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                if (isChangingPin) {
                    TextButton(
                        onClick = onCancelChangePin,
                        modifier = Modifier.testTag("cancel_change_pin_btn")
                    ) {
                        Text("Cancel PIN Change")
                    }
                } else if (lockStatus == LockStatus.SETUP_PIN) {
                    TextButton(
                        onClick = onRestoreBackup,
                        modifier = Modifier.testTag("pin_restore_backup_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Restore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.size(6.dp))
                        Text("Restore Previous Vault Backup")
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = "LOCAL SHA-256 VAULT LOCK",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PinDigitKey(
    label: String,
    keySize: Dp,
    compact: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
        modifier = Modifier
            .size(keySize)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                CircleShape
            )
            .testTag("pin_key_$label")
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontFamily = MaterialTheme.typography.labelMedium.fontFamily,
                fontSize = if (compact) 22.sp else 26.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
