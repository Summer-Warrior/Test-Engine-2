package com.example.srw.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Badge
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.srw.model.Faction
import com.example.srw.model.MechaUnit
import com.example.srw.model.SpiritCommand
import com.example.ui.theme.AllyBeamBlue
import com.example.ui.theme.CockpitSlate
import com.example.ui.theme.EnemyCrimson
import com.example.ui.theme.EnergyGreen
import com.example.ui.theme.MutedTelemetry
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.PanelBorderCyan
import com.example.ui.theme.PanelNavy
import com.example.ui.theme.SpiritGold
import com.example.ui.theme.SteelText
import com.example.ui.theme.VoidNavy
import com.example.ui.theme.WarningAmber
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Semantic classification for entries inside [BattleCombatActionLogCard].
 */
enum class BattleLogSeverity {
    INFO,
    STRIKE,
    CRITICAL,
    EVADE,
    BARRIER,
    COUNTER,
    DESTRUCTION
}

/**
 * Immutable UI model representing one combat action row in the side-view battle log.
 */
data class SideViewCombatActionEntry(
    val id: String,
    val phaseTag: String,
    val actorName: String,
    val actionText: String,
    val deltaBadge: String? = null,
    val severity: BattleLogSeverity = BattleLogSeverity.INFO
)

/**
 * Reusable UI model describing a combatant's live telemetry inside the side-view battle HUD.
 */
data class SideViewCombatantState(
    val unitId: String,
    val unitName: String,
    val modelCode: String,
    val pilotName: String,
    val pilotLevel: Int,
    val pilotWill: Int,
    val faction: Faction,
    val currentHp: Int,
    val maxHp: Int,
    val currentEn: Int,
    val maxEn: Int,
    val weaponName: String,
    val weaponEnCost: Int = 0,
    val hitPercent: Int,
    val activeSpirits: Set<SpiritCommand> = emptySet(),
    val barrierActive: Boolean = false,
    val isActiveActor: Boolean = false
)

/**
 * Maps a domain [MechaUnit] into a reusable [SideViewCombatantState] for side-view HUD rendering.
 */
fun MechaUnit.toSideViewCombatantState(
    displayedHp: Int = currentHp,
    displayedEn: Int = currentEn,
    weaponName: String,
    weaponEnCost: Int = 0,
    hitPercent: Int,
    barrierActive: Boolean = false,
    isActiveActor: Boolean = false
): SideViewCombatantState = SideViewCombatantState(
    unitId = id,
    unitName = name,
    modelCode = modelCode,
    pilotName = pilot.name,
    pilotLevel = pilot.level,
    pilotWill = pilot.will,
    faction = faction,
    currentHp = displayedHp.coerceIn(0, maxHp.coerceAtLeast(1)),
    maxHp = maxHp.coerceAtLeast(1),
    currentEn = displayedEn.coerceIn(0, maxEn.coerceAtLeast(1)),
    maxEn = maxEn.coerceAtLeast(1),
    weaponName = weaponName,
    weaponEnCost = weaponEnCost,
    hitPercent = hitPercent.coerceIn(0, 100),
    activeSpirits = activeSpirits,
    barrierActive = barrierActive,
    isActiveActor = isActiveActor
)

/**
 * Reusable Material 3 side-view battle HUD panel combining dual combatant status cards
 * (animated health bars, energy levels, active spirits, and hit telemetry) with a live,
 * auto-scrolling combat action log.
 */
@Composable
fun SideViewBattleHudPanel(
    leftCombatant: SideViewCombatantState,
    rightCombatant: SideViewCombatantState,
    combatActions: List<SideViewCombatActionEntry>,
    modifier: Modifier = Modifier,
    isLogExpanded: Boolean = true,
    onToggleLogExpanded: (() -> Unit)? = null,
    logTitle: String = stringResource(R.string.battle_hud_combat_log_title)
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .testTag("side_view_battle_hud")
    ) {
        val isWideViewport = maxWidth >= 720.dp

        if (isWideViewport) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1.35f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BattleCombatantStatusCard(
                        state = leftCombatant,
                        modifier = Modifier.weight(1f)
                    )
                    BattleCombatantStatusCard(
                        state = rightCombatant,
                        modifier = Modifier.weight(1f)
                    )
                }

                BattleCombatActionLogCard(
                    actions = combatActions,
                    title = logTitle,
                    expanded = isLogExpanded,
                    onToggleExpanded = onToggleLogExpanded,
                    maxBodyHeight = 134.dp,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BattleCombatantStatusCard(
                        state = leftCombatant,
                        modifier = Modifier.weight(1f)
                    )
                    BattleCombatantStatusCard(
                        state = rightCombatant,
                        modifier = Modifier.weight(1f)
                    )
                }

                BattleCombatActionLogCard(
                    actions = combatActions,
                    title = logTitle,
                    expanded = isLogExpanded,
                    onToggleExpanded = onToggleLogExpanded,
                    maxBodyHeight = 110.dp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Reusable Material 3 [ElevatedCard] displaying a single combatant's live HP bar,
 * energy (EN) bar, pilot morale/level, active spirits/barrier, and weapon hit telemetry.
 */
@Composable
fun BattleCombatantStatusCard(
    state: SideViewCombatantState,
    modifier: Modifier = Modifier
) {
    val factionAccent = when (state.faction) {
        Faction.ALLY -> MaterialTheme.colorScheme.primary
        Faction.ENEMY -> MaterialTheme.colorScheme.tertiary
    }
    val borderColor by animateColorAsState(
        targetValue = if (state.isActiveActor) {
            MaterialTheme.colorScheme.secondary
        } else {
            factionAccent.copy(alpha = 0.72f)
        },
        animationSpec = tween(durationMillis = 250),
        label = "combatantCardBorder"
    )

    val safeMaxHp = state.maxHp.coerceAtLeast(1)
    val safeMaxEn = state.maxEn.coerceAtLeast(1)
    val targetHpFraction = (state.currentHp.toFloat() / safeMaxHp.toFloat()).coerceIn(0f, 1f)
    val targetEnFraction = (state.currentEn.toFloat() / safeMaxEn.toFloat()).coerceIn(0f, 1f)

    val animatedHpFraction by animateFloatAsState(
        targetValue = targetHpFraction,
        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        label = "combatantHpFraction"
    )
    val animatedEnFraction by animateFloatAsState(
        targetValue = targetEnFraction,
        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        label = "combatantEnFraction"
    )

    val hpColor = when {
        targetHpFraction <= 0.30f -> EnemyCrimson
        targetHpFraction <= 0.55f -> WarningAmber
        else -> EnergyGreen
    }
    val enColor = if (targetEnFraction <= 0.20f) WarningAmber else PanelBorderCyan

    val numberFormat = NumberFormat.getIntegerInstance(Locale.US)
    val hpText = "${numberFormat.format(state.currentHp)} / ${numberFormat.format(safeMaxHp)}"
    val enText = "${state.currentEn} / $safeMaxEn"
    val hpPct = (targetHpFraction * 100f).roundToInt()

    ElevatedCard(
        modifier = modifier.testTag("combatant_status_card_${state.unitId}"),
        shape = CutCornerShape(10.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (state.isActiveActor) 8.dp else 3.dp
        )
    ) {
        Surface(
            color = Color.Transparent,
            shape = CutCornerShape(10.dp),
            border = BorderStroke(if (state.isActiveActor) 1.5.dp else 1.dp, borderColor)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Faction & active-turn top accent strip
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(if (state.isActiveActor) SpiritGold else factionAccent)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    // Header: Unit Name + Will Pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = state.unitName,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "LV.${state.pilotLevel} ${state.pilotName} • ${state.modelCode}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MutedTelemetry,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, SpiritGold.copy(alpha = 0.6f))
                        ) {
                            Text(
                                text = "WILL ${state.pilotWill}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = SpiritGold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Health (HP) Telemetry Row + Progress Bar
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = "Health Points",
                                    tint = hpColor,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = stringResource(R.string.battle_hud_hp_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MutedTelemetry
                                )
                                Text(
                                    text = "$hpPct%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = hpColor.copy(alpha = 0.9f)
                                )
                            }
                            Text(
                                text = hpText,
                                style = MaterialTheme.typography.labelLarge,
                                color = hpColor
                            )
                        }
                        LinearProgressIndicator(
                            progress = { animatedHpFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .testTag("combatant_hp_bar_${state.unitId}"),
                            color = hpColor,
                            trackColor = VoidNavy,
                            strokeCap = StrokeCap.Round
                        )
                    }

                    // Energy (EN) Telemetry Row + Progress Bar
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = "Energy Level",
                                    tint = enColor,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = stringResource(R.string.battle_hud_en_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MutedTelemetry
                                )
                                if (state.weaponEnCost > 0) {
                                    Text(
                                        text = "(-${state.weaponEnCost})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = WarningAmber
                                    )
                                }
                            }
                            Text(
                                text = enText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = enColor
                            )
                        }
                        LinearProgressIndicator(
                            progress = { animatedEnFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .testTag("combatant_en_bar_${state.unitId}"),
                            color = enColor,
                            trackColor = VoidNavy,
                            strokeCap = StrokeCap.Round
                        )
                    }

                    // Weapon & Hit % + Active Spirits / Barrier Footer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = state.weaponName,
                            style = MaterialTheme.typography.labelSmall,
                            color = SteelText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (state.barrierActive) {
                                Surface(
                                    color = PanelBorderCyan.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(1.dp, PanelBorderCyan.copy(alpha = 0.7f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Shield,
                                            contentDescription = "Barrier active",
                                            tint = PanelBorderCyan,
                                            modifier = Modifier.size(10.dp)
                                        )
                                        Text(
                                            text = "AEGIS",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = PanelBorderCyan
                                        )
                                    }
                                }
                            }
                            state.activeSpirits.take(2).forEach { spirit ->
                                Surface(
                                    color = spirit.badgeColor.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(1.dp, spirit.badgeColor.copy(alpha = 0.7f))
                                ) {
                                    Text(
                                        text = spirit.codeName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = spirit.badgeColor,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = "HIT ${state.hitPercent}%",
                                style = MaterialTheme.typography.labelLarge,
                                color = SpiritGold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Reusable Material 3 [OutlinedCard] presenting a chronological log of combat actions,
 * severity phase pills, and HP/EN/outcome badges with smooth auto-scroll.
 */
@Composable
fun BattleCombatActionLogCard(
    actions: List<SideViewCombatActionEntry>,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.battle_hud_combat_log_title),
    expanded: Boolean = true,
    onToggleExpanded: (() -> Unit)? = null,
    maxBodyHeight: androidx.compose.ui.unit.Dp = 120.dp
) {
    val listState = rememberLazyListState()

    LaunchedEffect(actions.size, expanded) {
        if (expanded && actions.isNotEmpty()) {
            listState.animateScrollToItem(actions.lastIndex)
        }
    }

    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("battle_action_log_card"),
        shape = CutCornerShape(10.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header bar with icon, entry count Badge, and optional 48.dp toggle control
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PanelNavy.copy(alpha = 0.85f))
                    .padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ListAlt,
                        contentDescription = title,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = SteelText
                    ) {
                        Text(
                            text = actions.size.toString(),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                if (onToggleExpanded != null) {
                    IconButton(
                        onClick = onToggleExpanded,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("battle_action_log_toggle")
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = stringResource(R.string.battle_hud_toggle_log),
                            tint = SteelText
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(36.dp))
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ) + fadeIn(),
                exit = shrinkVertically(
                    animationSpec = spring(stiffness = Spring.StiffnessMedium)
                ) + fadeOut()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
                    )

                    if (actions.isEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sensors,
                                contentDescription = "Awaiting combat telemetry",
                                tint = MutedTelemetry,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.battle_hud_empty_log),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MutedTelemetry
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = maxBodyHeight)
                                .testTag("battle_action_log_list")
                        ) {
                            itemsIndexed(
                                items = actions,
                                key = { _, entry -> entry.id }
                            ) { index, entry ->
                                CombatActionLogRow(entry = entry)
                                if (index < actions.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(top = 4.dp),
                                        thickness = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CombatActionLogRow(entry: SideViewCombatActionEntry) {
    val severityColor = when (entry.severity) {
        BattleLogSeverity.INFO -> PanelBorderCyan
        BattleLogSeverity.STRIKE -> AllyBeamBlue
        BattleLogSeverity.CRITICAL -> SpiritGold
        BattleLogSeverity.EVADE -> EnergyGreen
        BattleLogSeverity.BARRIER -> PanelBorderCyan
        BattleLogSeverity.COUNTER -> WarningAmber
        BattleLogSeverity.DESTRUCTION -> EnemyCrimson
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("battle_log_entry_${entry.id}"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = severityColor.copy(alpha = 0.16f),
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(1.dp, severityColor.copy(alpha = 0.65f))
        ) {
            Text(
                text = entry.phaseTag,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = severityColor,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${entry.actorName} — ${entry.actionText}",
                style = MaterialTheme.typography.bodyMedium,
                color = SteelText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (!entry.deltaBadge.isNullOrBlank()) {
            Surface(
                color = VoidNavy,
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, severityColor.copy(alpha = 0.55f))
            ) {
                Text(
                    text = entry.deltaBadge,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = severityColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF070B14)
@Composable
private fun SideViewBattleHudPanelPreview() {
    MyApplicationTheme {
        Surface(color = VoidNavy, modifier = Modifier.padding(12.dp)) {
            SideViewBattleHudPanel(
                leftCombatant = SideViewCombatantState(
                    unitId = "ally_kaiser",
                    unitName = "Kaiser Blaze",
                    modelCode = "KZR-01",
                    pilotName = "Ryoma",
                    pilotLevel = 3,
                    pilotWill = 122,
                    faction = Faction.ALLY,
                    currentHp = 5800,
                    maxHp = 7200,
                    currentEn = 155,
                    maxEn = 220,
                    weaponName = "Kaiser Nova Breaker",
                    weaponEnCost = 35,
                    hitPercent = 94,
                    activeSpirits = setOf(SpiritCommand.VALOR, SpiritCommand.FOCUS),
                    isActiveActor = true
                ),
                rightCombatant = SideViewCombatantState(
                    unitId = "enemy_boss",
                    unitName = "Zaku Commander",
                    modelCode = "MS-06S",
                    pilotName = "Char",
                    pilotLevel = 4,
                    pilotWill = 115,
                    faction = Faction.ENEMY,
                    currentHp = 2100,
                    maxHp = 6400,
                    currentEn = 95,
                    maxEn = 140,
                    weaponName = "Crimson Bazooka",
                    weaponEnCost = 20,
                    hitPercent = 58,
                    barrierActive = true
                ),
                combatActions = listOf(
                    SideViewCombatActionEntry(
                        id = "1",
                        phaseTag = "ENGAGE",
                        actorName = "Kaiser Blaze",
                        actionText = "Locks onto Zaku Commander on Urban Ridge.",
                        severity = BattleLogSeverity.INFO
                    ),
                    SideViewCombatActionEntry(
                        id = "2",
                        phaseTag = "CRIT",
                        actorName = "Kaiser Blaze",
                        actionText = "Kaiser Nova Breaker triggers critical overload!",
                        deltaBadge = "-4,300 HP",
                        severity = BattleLogSeverity.CRITICAL
                    ),
                    SideViewCombatActionEntry(
                        id = "3",
                        phaseTag = "COUNTER",
                        actorName = "Zaku Commander",
                        actionText = "Returns fire with Crimson Bazooka.",
                        deltaBadge = "-20 EN",
                        severity = BattleLogSeverity.COUNTER
                    )
                ),
                onToggleLogExpanded = {}
            )
        }
    }
}
