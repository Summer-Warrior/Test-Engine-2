package com.example.srw.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.srw.viewmodel.PilotLevelProgress
import com.example.srw.viewmodel.PilotLevelUpNotification
import com.example.ui.theme.CockpitSlate
import com.example.ui.theme.EnergyGreen
import com.example.ui.theme.MutedTelemetry
import com.example.ui.theme.PanelBorderCyan
import com.example.ui.theme.SpiritGold
import com.example.ui.theme.SteelText
import com.example.ui.theme.VoidNavy
import kotlinx.coroutines.delay

/**
 * Floating tactical HUD banner shown whenever a pilot reaches a new level via XP threshold progression.
 */
@Composable
fun PilotLevelUpOverlay(
    notification: PilotLevelUpNotification?,
    levelProgress: PilotLevelProgress?,
    queuedCount: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(notification?.id) {
        if (notification != null) {
            delay(6000)
            onDismiss()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = notification != null,
            enter = slideInVertically(
                initialOffsetY = { -it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { -it },
                animationSpec = spring(stiffness = Spring.StiffnessMedium)
            ) + fadeOut()
        ) {
            if (notification != null) {
                val accent = SpiritGold
                val progressFraction = levelProgress?.progressFraction ?: 0f

                Surface(
                    color = CockpitSlate.copy(alpha = 0.97f),
                    shape = CutCornerShape(10.dp),
                    border = BorderStroke(1.5.dp, accent),
                    shadowElevation = 10.dp,
                    modifier = Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .testTag("pilot_level_up_notification")
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Top glowing gradient bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            notification.accentColor,
                                            SpiritGold,
                                            PanelBorderCyan
                                        )
                                    )
                                )
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        color = SpiritGold.copy(alpha = 0.22f),
                                        shape = CutCornerShape(6.dp),
                                        border = BorderStroke(1.dp, SpiritGold)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = "Pilot Level Up",
                                                tint = SpiritGold,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "LEVEL UP // LV.${notification.previousLevel} → LV.${notification.newLevel}",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = SpiritGold
                                            )
                                        }
                                    }

                                    Surface(
                                        color = PanelBorderCyan.copy(alpha = 0.16f),
                                        shape = RoundedCornerShape(4.dp),
                                        border = BorderStroke(1.dp, PanelBorderCyan.copy(alpha = 0.7f))
                                    ) {
                                        Text(
                                            text = notification.newRankTitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = PanelBorderCyan,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }

                                    if (queuedCount > 0) {
                                        Text(
                                            text = "+$queuedCount MORE",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = EnergyGreen
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = onDismiss,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("dismiss_level_up_notification")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Dismiss level up notification",
                                        tint = SteelText
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MilitaryTech,
                                    contentDescription = "Pilot rank icon",
                                    tint = notification.accentColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "${notification.pilotName} [${notification.callsign}] • ${notification.unitName}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Surface(
                                color = VoidNavy,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, EnergyGreen.copy(alpha = 0.55f))
                            ) {
                                Text(
                                    text = notification.statBoostSummary,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = EnergyGreen,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Total XP: ${notification.totalXpEarned} XP",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SteelText
                                    )
                                    Text(
                                        text = if (levelProgress?.isMaxLevel == true) {
                                            "MAX PILOT LEVEL"
                                        } else {
                                            "Next LV.${notification.newLevel + 1}: ${notification.nextLevelThreshold} XP (${notification.xpRemainingToNext} needed)"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = PanelBorderCyan
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { progressFraction },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = SpiritGold,
                                    trackColor = VoidNavy
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
