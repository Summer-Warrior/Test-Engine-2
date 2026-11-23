package com.example.srw.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.srw.model.*
import com.example.srw.ui.MechaCanvasRenderer.drawCinematicMechaRig
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class CutscenePhase {
    INTRO_STANDOFF,
    ATTACKER_CUTIN,
    PRIMARY_ATTACK_CHARGE,
    PRIMARY_ATTACK_IMPACT,
    COUNTER_CUTIN,
    COUNTER_ATTACK_CHARGE,
    COUNTER_ATTACK_IMPACT,
    FINALE_SUMMARY
}

@Composable
fun CinematicBattleStage(
    sequence: BattleCutsceneSequence,
    onBattleFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    var speedMultiplier by remember { mutableFloatStateOf(1f) }
    var replayTrigger by remember { mutableIntStateOf(0) }
    var isCombatLogExpanded by remember(sequence.id) { mutableStateOf(true) }
    var currentPhase by remember(sequence.id, replayTrigger) {
        mutableStateOf(CutscenePhase.INTRO_STANDOFF)
    }

    val phaseProgress = remember(sequence.id, replayTrigger) { Animatable(0f) }

    // Animated HP & EN values for smooth SRW-style gauge draining
    val displayedDefenderHp by animateIntAsState(
        targetValue = when (currentPhase) {
            CutscenePhase.INTRO_STANDOFF,
            CutscenePhase.ATTACKER_CUTIN,
            CutscenePhase.PRIMARY_ATTACK_CHARGE -> sequence.defender.currentHp
            else -> sequence.defenderHpAfterPrimary
        },
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "defenderHpAnim"
    )

    val displayedAttackerHp by animateIntAsState(
        targetValue = when (currentPhase) {
            CutscenePhase.COUNTER_ATTACK_IMPACT,
            CutscenePhase.FINALE_SUMMARY -> sequence.attackerHpAfterCounter
            else -> sequence.attacker.currentHp
        },
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "attackerHpAnim"
    )

    val displayedAttackerEn by animateIntAsState(
        targetValue = when (currentPhase) {
            CutscenePhase.INTRO_STANDOFF,
            CutscenePhase.ATTACKER_CUTIN -> sequence.attacker.currentEn
            CutscenePhase.PRIMARY_ATTACK_CHARGE,
            CutscenePhase.PRIMARY_ATTACK_IMPACT,
            CutscenePhase.COUNTER_CUTIN,
            CutscenePhase.COUNTER_ATTACK_CHARGE ->
                (sequence.attacker.currentEn - sequence.attackerWeapon.enCost).coerceAtLeast(0)
            CutscenePhase.COUNTER_ATTACK_IMPACT,
            CutscenePhase.FINALE_SUMMARY ->
                (sequence.attacker.currentEn -
                    sequence.attackerWeapon.enCost -
                    if (sequence.counterBarrierActivated) 10 else 0).coerceAtLeast(0)
        },
        animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
        label = "attackerEnAnim"
    )

    val displayedDefenderEn by animateIntAsState(
        targetValue = when (currentPhase) {
            CutscenePhase.INTRO_STANDOFF,
            CutscenePhase.ATTACKER_CUTIN,
            CutscenePhase.PRIMARY_ATTACK_CHARGE -> sequence.defender.currentEn
            CutscenePhase.PRIMARY_ATTACK_IMPACT,
            CutscenePhase.COUNTER_CUTIN ->
                (sequence.defender.currentEn - if (sequence.primaryBarrierActivated) 10 else 0).coerceAtLeast(0)
            CutscenePhase.COUNTER_ATTACK_CHARGE,
            CutscenePhase.COUNTER_ATTACK_IMPACT,
            CutscenePhase.FINALE_SUMMARY -> {
                val barrierCost = if (sequence.primaryBarrierActivated) 10 else 0
                val counterCost = if (sequence.counterExecuted) sequence.defenderWeapon?.enCost ?: 0 else 0
                (sequence.defender.currentEn - barrierCost - counterCost).coerceAtLeast(0)
            }
        },
        animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
        label = "defenderEnAnim"
    )

    // Sequencer coroutine driving the multi-stage SRW battle animation
    LaunchedEffect(sequence.id, replayTrigger, speedMultiplier) {
        suspend fun runStage(phase: CutscenePhase, baseDurationMs: Int) {
            currentPhase = phase
            phaseProgress.snapTo(0f)
            val scaledDuration = (baseDurationMs / speedMultiplier).roundToInt().coerceAtLeast(160)
            phaseProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = scaledDuration, easing = LinearEasing)
            )
        }

        runStage(CutscenePhase.INTRO_STANDOFF, 650)
        runStage(CutscenePhase.ATTACKER_CUTIN, 1050)
        runStage(CutscenePhase.PRIMARY_ATTACK_CHARGE, 1150)
        runStage(CutscenePhase.PRIMARY_ATTACK_IMPACT, 1250)

        if (sequence.counterExecuted && sequence.defenderWeapon != null) {
            runStage(CutscenePhase.COUNTER_CUTIN, 950)
            runStage(CutscenePhase.COUNTER_ATTACK_CHARGE, 1050)
            runStage(CutscenePhase.COUNTER_ATTACK_IMPACT, 1150)
        }

        currentPhase = CutscenePhase.FINALE_SUMMARY
        delay((850 / speedMultiplier).toLong())
        if (!sequence.isSandboxPreview) {
            onBattleFinished()
        }
    }

    val activeQuoteSpeaker = when (currentPhase) {
        CutscenePhase.INTRO_STANDOFF,
        CutscenePhase.ATTACKER_CUTIN,
        CutscenePhase.PRIMARY_ATTACK_CHARGE -> sequence.attacker.pilot.name
        CutscenePhase.PRIMARY_ATTACK_IMPACT ->
            if (sequence.primaryOutcome == StrikeOutcome.MISS) sequence.defender.pilot.name
            else sequence.attacker.pilot.name
        CutscenePhase.COUNTER_CUTIN,
        CutscenePhase.COUNTER_ATTACK_CHARGE,
        CutscenePhase.COUNTER_ATTACK_IMPACT -> sequence.defender.pilot.name
        CutscenePhase.FINALE_SUMMARY -> "COMBAT TELEMETRY"
    }

    val activeQuoteText = when (currentPhase) {
        CutscenePhase.INTRO_STANDOFF ->
            "${sequence.attacker.name} engaging ${sequence.defender.name} with ${sequence.attackerWeapon.name}!"
        CutscenePhase.ATTACKER_CUTIN,
        CutscenePhase.PRIMARY_ATTACK_CHARGE -> sequence.attackerQuote
        CutscenePhase.PRIMARY_ATTACK_IMPACT -> when {
            sequence.primaryOutcome == StrikeOutcome.MISS -> sequence.defender.pilot.dodgeQuote
            sequence.defenderHpAfterPrimary == 0 ->
                "${sequence.defender.name} critical core overload! Target destroyed!"
            else -> sequence.defenderQuote
        }
        CutscenePhase.COUNTER_CUTIN,
        CutscenePhase.COUNTER_ATTACK_CHARGE -> sequence.defenderQuote
        CutscenePhase.COUNTER_ATTACK_IMPACT -> when {
            sequence.counterOutcome == StrikeOutcome.MISS -> sequence.attacker.pilot.dodgeQuote
            sequence.attackerHpAfterCounter == 0 ->
                "${sequence.attacker.name} sustained critical damage!"
            else -> sequence.attacker.pilot.damageQuote
        }
        CutscenePhase.FINALE_SUMMARY -> {
            val primarySummary = if (sequence.primaryOutcome == StrikeOutcome.MISS) {
                "${sequence.attacker.name} missed!"
            } else {
                "${sequence.attacker.name} dealt ${sequence.primaryDamage} DMG (${sequence.primaryOutcome.name})!"
            }
            val counterSummary = if (sequence.counterExecuted) {
                if (sequence.counterOutcome == StrikeOutcome.MISS) " Counter evaded!"
                else " Counter dealt ${sequence.counterDamage} DMG!"
            } else ""
            primarySummary + counterSummary
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VoidNavy)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("cinematic_battle_stage"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Top Control & Terrain Telemetry Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PanelNavy, CutCornerShape(8.dp))
                .border(1.dp, PanelBorderCyan.copy(alpha = 0.6f), CutCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SRW BATTLE ENGINE // ${sequence.attackerTerrain.displayName.uppercase()}",
                    style = MaterialTheme.typography.labelLarge,
                    color = PanelBorderCyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${sequence.attackerWeapon.animStyle.label} • DIST ${sequence.attacker.pos.distanceTo(sequence.defender.pos)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedTelemetry
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = { speedMultiplier = if (speedMultiplier == 1f) 2f else 1f },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("battle_speed_button"),
                    border = BorderStroke(1.dp, SpiritGold)
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "Toggle animation speed",
                        tint = SpiritGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (speedMultiplier > 1f) "2X" else "1X",
                        style = MaterialTheme.typography.labelLarge,
                        color = SpiritGold
                    )
                }

                OutlinedButton(
                    onClick = { replayTrigger++ },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("battle_replay_button"),
                    border = BorderStroke(1.dp, AllyBeamBlue)
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay,
                        contentDescription = "Replay battle animation",
                        tint = AllyBeamBlue,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Button(
                    onClick = onBattleFinished,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EnemyCrimson),
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("battle_skip_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Finish or skip animation",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (sequence.isSandboxPreview || currentPhase == CutscenePhase.FINALE_SUMMARY) "DONE" else "SKIP",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        // 2. Main Theatrical Side-View Canvas Stage
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(CutCornerShape(12.dp))
                .border(1.5.dp, PanelBorderCyan, CutCornerShape(12.dp))
        ) {
            val t = phaseProgress.value

            Canvas(modifier = Modifier.fillMaxSize()) {
                drawBattlefieldStage(
                    sequence = sequence,
                    phase = currentPhase,
                    t = t
                )
            }

            // Diagonal Pilot Cut-In Banner during ATTACKER_CUTIN or COUNTER_CUTIN
            androidx.compose.animation.AnimatedVisibility(
                visible = currentPhase == CutscenePhase.ATTACKER_CUTIN ||
                    currentPhase == CutscenePhase.COUNTER_CUTIN,
                enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            ) {
                val cutInUnit = if (currentPhase == CutscenePhase.ATTACKER_CUTIN) {
                    sequence.attacker
                } else {
                    sequence.defender
                }
                val cutInWeapon = if (currentPhase == CutscenePhase.ATTACKER_CUTIN) {
                    sequence.attackerWeapon
                } else {
                    sequence.defenderWeapon ?: sequence.defender.weapons.first()
                }
                PilotCutInBanner(unit = cutInUnit, weapon = cutInWeapon)
            }

            // Floating Impact / Outcome Telemetry Banner
            if (currentPhase == CutscenePhase.PRIMARY_ATTACK_IMPACT ||
                currentPhase == CutscenePhase.COUNTER_ATTACK_IMPACT ||
                currentPhase == CutscenePhase.FINALE_SUMMARY
            ) {
                val isCounterPhase = currentPhase == CutscenePhase.COUNTER_ATTACK_IMPACT
                val outcome = if (isCounterPhase) sequence.counterOutcome else sequence.primaryOutcome
                val dmg = if (isCounterPhase) sequence.counterDamage else sequence.primaryDamage
                val barrierActive = if (isCounterPhase) {
                    sequence.counterBarrierActivated
                } else {
                    sequence.primaryBarrierActivated
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(14.dp)
                        .background(VoidNavy.copy(alpha = 0.88f), CutCornerShape(10.dp))
                        .border(
                            width = 1.5.dp,
                            color = when (outcome) {
                                StrikeOutcome.CRITICAL -> SpiritGold
                                StrikeOutcome.HIT -> EnemyCrimson
                                StrikeOutcome.MISS -> AllyBeamBlue
                            },
                            shape = CutCornerShape(10.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = when (outcome) {
                            StrikeOutcome.CRITICAL -> "CRITICAL STRIKE!!"
                            StrikeOutcome.HIT -> "DIRECT HIT!"
                            StrikeOutcome.MISS -> "TARGET EVADED // MISS!"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = when (outcome) {
                            StrikeOutcome.CRITICAL -> SpiritGold
                            StrikeOutcome.HIT -> Color.White
                            StrikeOutcome.MISS -> AllyBeamBlue
                        }
                    )
                    if (outcome != StrikeOutcome.MISS) {
                        Text(
                            text = "-$dmg HP",
                            style = MaterialTheme.typography.displayMedium,
                            color = if (outcome == StrikeOutcome.CRITICAL) SpiritGold else EnemyCrimson
                        )
                    }
                    if (barrierActive) {
                        Text(
                            text = "BARRIER REDUCED DMG!",
                            style = MaterialTheme.typography.labelSmall,
                            color = PanelBorderCyan
                        )
                    }
                }
            }

            // Bottom Anime Subtitle Dialogue Overlay inside the stage
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, VoidNavy.copy(alpha = 0.92f), VoidNavy)
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CutCornerShape(8.dp))
                        .background(PanelNavy)
                        .border(1.dp, PanelBorderCyan, CutCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = activeQuoteSpeaker.take(2).uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        color = SpiritGold
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activeQuoteSpeaker.uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = SpiritGold
                    )
                    Text(
                        text = "\"$activeQuoteText\"",
                        style = MaterialTheme.typography.bodyLarge,
                        color = SteelText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 3. Reusable Material 3 Side-View Battle HUD (Health Bars, Energy Levels & Combat Action Log)
        val combatActions = remember(sequence.id, currentPhase) {
            buildStageCombatActions(sequence = sequence, currentPhase = currentPhase)
        }
        val attackerIsActing = currentPhase in listOf(
            CutscenePhase.ATTACKER_CUTIN,
            CutscenePhase.PRIMARY_ATTACK_CHARGE,
            CutscenePhase.PRIMARY_ATTACK_IMPACT
        )
        val defenderIsActing = currentPhase in listOf(
            CutscenePhase.COUNTER_CUTIN,
            CutscenePhase.COUNTER_ATTACK_CHARGE,
            CutscenePhase.COUNTER_ATTACK_IMPACT
        )

        SideViewBattleHudPanel(
            leftCombatant = sequence.attacker.toSideViewCombatantState(
                displayedHp = displayedAttackerHp,
                displayedEn = displayedAttackerEn,
                weaponName = sequence.attackerWeapon.name,
                weaponEnCost = sequence.attackerWeapon.enCost,
                hitPercent = sequence.attackerHitRate,
                barrierActive = sequence.counterBarrierActivated &&
                    currentPhase.ordinal >= CutscenePhase.COUNTER_ATTACK_IMPACT.ordinal,
                isActiveActor = attackerIsActing
            ),
            rightCombatant = sequence.defender.toSideViewCombatantState(
                displayedHp = displayedDefenderHp,
                displayedEn = displayedDefenderEn,
                weaponName = sequence.defenderWeapon?.name ?: sequence.defenderStance.label,
                weaponEnCost = if (sequence.counterExecuted) sequence.defenderWeapon?.enCost ?: 0 else 0,
                hitPercent = sequence.defenderHitRate,
                barrierActive = sequence.primaryBarrierActivated &&
                    currentPhase.ordinal >= CutscenePhase.PRIMARY_ATTACK_IMPACT.ordinal,
                isActiveActor = defenderIsActing
            ),
            combatActions = combatActions,
            isLogExpanded = isCombatLogExpanded,
            onToggleLogExpanded = { isCombatLogExpanded = !isCombatLogExpanded }
        )
    }
}

@Composable
private fun PilotCutInBanner(
    unit: MechaUnit,
    weapon: Weapon
) {
    Surface(
        color = VoidNavy.copy(alpha = 0.92f),
        shape = CutCornerShape(topStart = 18.dp, bottomEnd = 18.dp),
        border = BorderStroke(2.dp, unit.pilot.primaryColor),
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .height(74.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            unit.pilot.primaryColor.copy(alpha = 0.38f),
                            PanelNavy,
                            VoidNavy
                        )
                    )
                )
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Stylized Pilot Visor Emblem Canvas
            Canvas(modifier = Modifier.size(54.dp)) {
                val w = size.width
                val h = size.height
                drawCircle(
                    color = unit.pilot.primaryColor.copy(alpha = 0.3f),
                    radius = w * 0.48f
                )
                // Helmet contour
                val helmet = Path().apply {
                    moveTo(w * 0.2f, h * 0.8f)
                    lineTo(w * 0.15f, h * 0.35f)
                    lineTo(w * 0.5f, h * 0.12f)
                    lineTo(w * 0.85f, h * 0.35f)
                    lineTo(w * 0.8f, h * 0.8f)
                    close()
                }
                drawPath(helmet, Color(0xFFECEFF1))
                // Glowing Visor Slash
                val visor = Path().apply {
                    moveTo(w * 0.22f, h * 0.42f)
                    lineTo(w * 0.5f, h * 0.52f)
                    lineTo(w * 0.78f, h * 0.42f)
                    lineTo(w * 0.72f, h * 0.58f)
                    lineTo(w * 0.5f, h * 0.65f)
                    lineTo(w * 0.28f, h * 0.58f)
                    close()
                }
                drawPath(visor, unit.pilot.visorColor)
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = unit.pilot.callsign,
                        style = MaterialTheme.typography.labelLarge,
                        color = unit.pilot.visorColor
                    )
                    Text(
                        text = "• WILL ${unit.pilot.will}",
                        style = MaterialTheme.typography.labelLarge,
                        color = SpiritGold
                    )
                    unit.activeSpirits.forEach { sp ->
                        Text(
                            text = "[${sp.codeName}]",
                            style = MaterialTheme.typography.labelSmall,
                            color = sp.badgeColor
                        )
                    }
                }
                Text(
                    text = weapon.name.uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun buildStageCombatActions(
    sequence: BattleCutsceneSequence,
    currentPhase: CutscenePhase
): List<SideViewCombatActionEntry> {
    val entries = mutableListOf<SideViewCombatActionEntry>()
    val phaseOrd = currentPhase.ordinal

    entries.add(
        SideViewCombatActionEntry(
            id = "${sequence.id}_engage",
            phaseTag = "ENGAGE",
            actorName = sequence.attacker.name,
            actionText = "Targets ${sequence.defender.name} on ${sequence.defenderTerrain.displayName} (Stance: ${sequence.defenderStance.label}).",
            deltaBadge = "${sequence.attackerHitRate}% HIT",
            severity = BattleLogSeverity.INFO
        )
    )

    if (sequence.attacker.activeSpirits.isNotEmpty()) {
        val spiritsLabel = sequence.attacker.activeSpirits.joinToString(", ") { it.codeName }
        entries.add(
            SideViewCombatActionEntry(
                id = "${sequence.id}_spirit",
                phaseTag = "SPIRIT",
                actorName = sequence.attacker.pilot.name,
                actionText = "Channels active Spirit Commands: $spiritsLabel.",
                severity = BattleLogSeverity.CRITICAL
            )
        )
    }

    if (phaseOrd >= CutscenePhase.ATTACKER_CUTIN.ordinal) {
        entries.add(
            SideViewCombatActionEntry(
                id = "${sequence.id}_cutin",
                phaseTag = "LOCK",
                actorName = sequence.attacker.pilot.callsign,
                actionText = "Primes ${sequence.attackerWeapon.name} (Will ${sequence.attacker.pilot.will}).",
                deltaBadge = if (sequence.attackerWeapon.enCost > 0) "-${sequence.attackerWeapon.enCost} EN" else null,
                severity = BattleLogSeverity.INFO
            )
        )
    }

    if (phaseOrd >= CutscenePhase.PRIMARY_ATTACK_IMPACT.ordinal) {
        val impactSeverity = when {
            sequence.defenderHpAfterPrimary == 0 -> BattleLogSeverity.DESTRUCTION
            sequence.primaryOutcome == StrikeOutcome.CRITICAL -> BattleLogSeverity.CRITICAL
            sequence.primaryOutcome == StrikeOutcome.MISS -> BattleLogSeverity.EVADE
            else -> BattleLogSeverity.STRIKE
        }
        val impactTag = when {
            sequence.defenderHpAfterPrimary == 0 -> "KO"
            sequence.primaryOutcome == StrikeOutcome.CRITICAL -> "CRIT"
            sequence.primaryOutcome == StrikeOutcome.MISS -> "MISS"
            else -> "HIT"
        }
        val impactText = when {
            sequence.primaryOutcome == StrikeOutcome.MISS ->
                "${sequence.defender.name} evades ${sequence.attackerWeapon.name}!"
            sequence.defenderHpAfterPrimary == 0 ->
                "${sequence.attackerWeapon.name} destroys ${sequence.defender.name}!"
            sequence.primaryOutcome == StrikeOutcome.CRITICAL ->
                "${sequence.attackerWeapon.name} lands a critical strike on ${sequence.defender.name}!"
            else ->
                "${sequence.attackerWeapon.name} connects with ${sequence.defender.name}."
        }
        entries.add(
            SideViewCombatActionEntry(
                id = "${sequence.id}_primary_impact",
                phaseTag = impactTag,
                actorName = sequence.attacker.name,
                actionText = impactText,
                deltaBadge = if (sequence.primaryOutcome == StrikeOutcome.MISS) "EVADED" else "-${sequence.primaryDamage} HP",
                severity = impactSeverity
            )
        )
        if (sequence.primaryBarrierActivated) {
            entries.add(
                SideViewCombatActionEntry(
                    id = "${sequence.id}_primary_barrier",
                    phaseTag = "BARRIER",
                    actorName = sequence.defender.name,
                    actionText = "${sequence.defender.effectiveBarrierName} absorbs incoming impact.",
                    deltaBadge = "-10 EN",
                    severity = BattleLogSeverity.BARRIER
                )
            )
        }
    }

    if (sequence.counterExecuted && sequence.defenderWeapon != null &&
        phaseOrd >= CutscenePhase.COUNTER_CUTIN.ordinal
    ) {
        entries.add(
            SideViewCombatActionEntry(
                id = "${sequence.id}_counter_prep",
                phaseTag = "COUNTER",
                actorName = sequence.defender.name,
                actionText = "Initiates counterattack with ${sequence.defenderWeapon.name}.",
                deltaBadge = if (sequence.defenderWeapon.enCost > 0) "-${sequence.defenderWeapon.enCost} EN" else null,
                severity = BattleLogSeverity.COUNTER
            )
        )
    }

    if (sequence.counterExecuted && sequence.defenderWeapon != null &&
        phaseOrd >= CutscenePhase.COUNTER_ATTACK_IMPACT.ordinal
    ) {
        val counterSeverity = when {
            sequence.attackerHpAfterCounter == 0 -> BattleLogSeverity.DESTRUCTION
            sequence.counterOutcome == StrikeOutcome.CRITICAL -> BattleLogSeverity.CRITICAL
            sequence.counterOutcome == StrikeOutcome.MISS -> BattleLogSeverity.EVADE
            else -> BattleLogSeverity.COUNTER
        }
        val counterTag = when {
            sequence.attackerHpAfterCounter == 0 -> "KO"
            sequence.counterOutcome == StrikeOutcome.CRITICAL -> "CRIT"
            sequence.counterOutcome == StrikeOutcome.MISS -> "MISS"
            else -> "HIT"
        }
        val counterText = when {
            sequence.counterOutcome == StrikeOutcome.MISS ->
                "${sequence.attacker.name} evades counter fire!"
            sequence.attackerHpAfterCounter == 0 ->
                "${sequence.defenderWeapon.name} downs ${sequence.attacker.name}!"
            else ->
                "${sequence.defenderWeapon.name} strikes ${sequence.attacker.name}."
        }
        entries.add(
            SideViewCombatActionEntry(
                id = "${sequence.id}_counter_impact",
                phaseTag = counterTag,
                actorName = sequence.defender.name,
                actionText = counterText,
                deltaBadge = if (sequence.counterOutcome == StrikeOutcome.MISS) "EVADED" else "-${sequence.counterDamage} HP",
                severity = counterSeverity
            )
        )
        if (sequence.counterBarrierActivated) {
            entries.add(
                SideViewCombatActionEntry(
                    id = "${sequence.id}_counter_barrier",
                    phaseTag = "BARRIER",
                    actorName = sequence.attacker.name,
                    actionText = "${sequence.attacker.effectiveBarrierName} dampens counter damage.",
                    deltaBadge = "-10 EN",
                    severity = BattleLogSeverity.BARRIER
                )
            )
        }
    }

    if (phaseOrd >= CutscenePhase.FINALE_SUMMARY.ordinal) {
        entries.add(
            SideViewCombatActionEntry(
                id = "${sequence.id}_finale",
                phaseTag = "RESULT",
                actorName = "TELEMETRY",
                actionText = "Exchange concluded. ${sequence.attacker.name} ${sequence.attackerHpAfterCounter} HP • ${sequence.defender.name} ${sequence.defenderHpAfterPrimary} HP.",
                severity = BattleLogSeverity.INFO
            )
        )
    }

    return entries
}

/**
 * Core Canvas choreographer for the Super Robot Wars side-view battle animation.
 */
private fun DrawScope.drawBattlefieldStage(
    sequence: BattleCutsceneSequence,
    phase: CutscenePhase,
    t: Float
) {
    val w = size.width
    val h = size.height

    // 1. Draw Terrain-Matched Atmospheric Sky & Ground/Space Backdrop
    drawStageEnvironment(
        terrain = sequence.defenderTerrain,
        phase = phase,
        t = t
    )

    // 2. Compute camera shake & dynamic positions for Attacker (Left) and Defender (Right)
    val isPrimaryImpact = phase == CutscenePhase.PRIMARY_ATTACK_IMPACT &&
        sequence.primaryOutcome != StrikeOutcome.MISS
    val isCounterImpact = phase == CutscenePhase.COUNTER_ATTACK_IMPACT &&
        sequence.counterOutcome != StrikeOutcome.MISS

    val shakeMagnitude = when {
        isPrimaryImpact || isCounterImpact -> (1f - t) * 18f
        else -> 0f
    }
    val shakeX = sin(t * 50f) * shakeMagnitude
    val shakeY = cos(t * 42f) * shakeMagnitude

    val baseLeftX = w * 0.24f
    val baseRightX = w * 0.76f
    val baseCenterY = h * 0.56f

    // Attacker dash during melee attacks
    val primaryStyle = sequence.attackerWeapon.animStyle
    val attackerDashOffset = when (phase) {
        CutscenePhase.PRIMARY_ATTACK_CHARGE -> {
            if (primaryStyle == AnimationStyle.BEAM_SABER_BLITZ ||
                primaryStyle == AnimationStyle.KAISER_NOVA_BREAKER
            ) {
                t * (w * 0.36f)
            } else 0f
        }
        CutscenePhase.PRIMARY_ATTACK_IMPACT -> {
            if (primaryStyle == AnimationStyle.BEAM_SABER_BLITZ ||
                primaryStyle == AnimationStyle.KAISER_NOVA_BREAKER
            ) {
                (1f - t * 0.6f) * (w * 0.36f)
            } else 0f
        }
        CutscenePhase.COUNTER_ATTACK_IMPACT -> {
            if (sequence.counterOutcome == StrikeOutcome.MISS) {
                // Dodge back-step!
                -sin(t * PI.toFloat()) * (w * 0.10f)
            } else 0f
        }
        else -> 0f
    }

    val counterStyle = sequence.defenderWeapon?.animStyle ?: AnimationStyle.BEAM_SABER_BLITZ
    val defenderDashOffset = when (phase) {
        CutscenePhase.PRIMARY_ATTACK_IMPACT -> {
            if (sequence.primaryOutcome == StrikeOutcome.MISS) {
                // Defender evades with a high-speed back-step!
                sin(t * PI.toFloat()) * (w * 0.11f)
            } else 0f
        }
        CutscenePhase.COUNTER_ATTACK_CHARGE -> {
            if (counterStyle == AnimationStyle.BEAM_SABER_BLITZ ||
                counterStyle == AnimationStyle.KAISER_NOVA_BREAKER
            ) {
                -t * (w * 0.36f)
            } else 0f
        }
        CutscenePhase.COUNTER_ATTACK_IMPACT -> {
            if (counterStyle == AnimationStyle.BEAM_SABER_BLITZ ||
                counterStyle == AnimationStyle.KAISER_NOVA_BREAKER
            ) {
                -(1f - t * 0.6f) * (w * 0.36f)
            } else 0f
        }
        else -> 0f
    }

    val attackerCenter = Offset(baseLeftX + attackerDashOffset + shakeX, baseCenterY + shakeY)
    val defenderCenter = Offset(baseRightX + defenderDashOffset + shakeX, baseCenterY + shakeY)

    val unitScale = (w / 720f).coerceIn(0.85f, 1.45f)

    // 3. Draw Afterimages during high-speed melee dashes or dodges
    if (attackerDashOffset > 20f) {
        for (ghost in 1..2) {
            drawCinematicMechaRig(
                rig = sequence.attacker.visualRig,
                center = Offset(attackerCenter.x - ghost * 42f, attackerCenter.y),
                scale = unitScale,
                facingRight = true,
                poseTiltDegrees = 12f,
                auraIntensity = 0.25f,
                auraColor = Color(0xFF00E5FF),
                weaponActive = true,
                drawMeleeBlade = true
            )
        }
    }

    // 4. Draw Attacker Rig (if alive)
    val attackerDestroyed = (phase == CutscenePhase.COUNTER_ATTACK_IMPACT || phase == CutscenePhase.FINALE_SUMMARY) &&
        sequence.attackerHpAfterCounter == 0 && t > 0.55f

    if (!attackerDestroyed) {
        val attackerAura = when {
            sequence.attacker.activeSpirits.contains(SpiritCommand.SOUL) ||
                sequence.attacker.activeSpirits.contains(SpiritCommand.VALOR) -> 0.9f
            phase == CutscenePhase.ATTACKER_CUTIN || phase == CutscenePhase.PRIMARY_ATTACK_CHARGE -> 0.7f
            else -> 0.25f
        }
        drawCinematicMechaRig(
            rig = sequence.attacker.visualRig,
            center = attackerCenter,
            scale = unitScale,
            facingRight = true,
            poseTiltDegrees = if (attackerDashOffset > 10f) 10f else 0f,
            auraIntensity = attackerAura,
            auraColor = sequence.attacker.pilot.primaryColor,
            weaponActive = phase == CutscenePhase.PRIMARY_ATTACK_CHARGE ||
                phase == CutscenePhase.PRIMARY_ATTACK_IMPACT,
            drawMeleeBlade = sequence.attackerWeapon.isMelee &&
                (phase == CutscenePhase.PRIMARY_ATTACK_CHARGE || phase == CutscenePhase.PRIMARY_ATTACK_IMPACT)
        )
    }

    // 5. Draw Defender Rig (if alive)
    val defenderDestroyed = (phase == CutscenePhase.PRIMARY_ATTACK_IMPACT || phase == CutscenePhase.FINALE_SUMMARY) &&
        sequence.defenderHpAfterPrimary == 0 && t > 0.55f

    if (!defenderDestroyed) {
        val defenderAura = when {
            phase == CutscenePhase.COUNTER_CUTIN || phase == CutscenePhase.COUNTER_ATTACK_CHARGE -> 0.75f
            else -> 0.25f
        }
        drawCinematicMechaRig(
            rig = sequence.defender.visualRig,
            center = defenderCenter,
            scale = unitScale,
            facingRight = false,
            poseTiltDegrees = if (defenderDashOffset < -10f) 10f else 0f,
            auraIntensity = defenderAura,
            auraColor = sequence.defender.pilot.primaryColor,
            weaponActive = phase == CutscenePhase.COUNTER_ATTACK_CHARGE ||
                phase == CutscenePhase.COUNTER_ATTACK_IMPACT,
            drawMeleeBlade = (sequence.defenderWeapon?.isMelee == true) &&
                (phase == CutscenePhase.COUNTER_ATTACK_CHARGE || phase == CutscenePhase.COUNTER_ATTACK_IMPACT)
        )
    }

    // 6. Draw Active Weapon Choreography Effects!
    when (phase) {
        CutscenePhase.PRIMARY_ATTACK_CHARGE,
        CutscenePhase.PRIMARY_ATTACK_IMPACT -> {
            drawWeaponChoreography(
                style = sequence.attackerWeapon.animStyle,
                origin = attackerCenter,
                target = defenderCenter,
                isImpactPhase = phase == CutscenePhase.PRIMARY_ATTACK_IMPACT,
                outcome = sequence.primaryOutcome,
                barrierActive = sequence.primaryBarrierActivated,
                targetDestroyed = sequence.defenderHpAfterPrimary == 0,
                t = t
            )
        }
        CutscenePhase.COUNTER_ATTACK_CHARGE,
        CutscenePhase.COUNTER_ATTACK_IMPACT -> {
            sequence.defenderWeapon?.let { counterWeapon ->
                drawWeaponChoreography(
                    style = counterWeapon.animStyle,
                    origin = defenderCenter,
                    target = attackerCenter,
                    isImpactPhase = phase == CutscenePhase.COUNTER_ATTACK_IMPACT,
                    outcome = sequence.counterOutcome,
                    barrierActive = sequence.counterBarrierActivated,
                    targetDestroyed = sequence.attackerHpAfterCounter == 0,
                    t = t
                )
            }
        }
        else -> Unit
    }
}

private fun DrawScope.drawStageEnvironment(
    terrain: TerrainType,
    phase: CutscenePhase,
    t: Float
) {
    val w = size.width
    val h = size.height

    val isSuperFinisher = (phase == CutscenePhase.PRIMARY_ATTACK_CHARGE ||
        phase == CutscenePhase.PRIMARY_ATTACK_IMPACT) &&
        terrain != TerrainType.SPACE

    // Sky Backdrop Gradient
    val skyColors = when (terrain) {
        TerrainType.SPACE, TerrainType.ASTEROID -> listOf(
            Color(0xFF04060E),
            Color(0xFF0E1538),
            Color(0xFF1A0E36)
        )
        TerrainType.CITY, TerrainType.BASE -> listOf(
            Color(0xFF071124),
            Color(0xFF132A4A),
            Color(0xFF1F3E5A)
        )
        TerrainType.MOUNTAIN -> listOf(
            Color(0xFF180E19),
            Color(0xFF3A1E2D),
            Color(0xFF5D3430)
        )
        else -> listOf(
            Color(0xFF081826),
            Color(0xFF12324A),
            Color(0xFF1D4D4F)
        )
    }

    drawRect(brush = Brush.verticalGradient(skyColors))

    // Speed lines during action phases
    if (phase != CutscenePhase.INTRO_STANDOFF && phase != CutscenePhase.FINALE_SUMMARY) {
        for (i in 0..14) {
            val yPos = (h * ((i * 0.07f + t * 0.2f) % 1f))
            val lineStart = ((i * 97f + t * w * 1.4f) % w)
            drawLine(
                color = Color.White.copy(alpha = 0.14f),
                start = Offset(lineStart, yPos),
                end = Offset((lineStart + w * 0.25f).coerceAtMost(w), yPos),
                strokeWidth = 2f
            )
        }
    }

    // Horizon & Ground/Space Details
    val horizonY = h * 0.72f
    if (terrain == TerrainType.SPACE || terrain == TerrainType.ASTEROID) {
        // Stars & Nebula
        for (i in 1..28) {
            val sx = (i * 137f) % w
            val sy = (i * 89f) % (h * 0.85f)
            drawCircle(
                color = if (i % 4 == 0) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.7f),
                radius = if (i % 5 == 0) 2.5f else 1.5f,
                center = Offset(sx, sy)
            )
        }
        // Orbital Ring
        drawArc(
            color = Color(0xFF00E5FF).copy(alpha = 0.22f),
            startAngle = 190f,
            sweepAngle = 160f,
            useCenter = false,
            topLeft = Offset(-w * 0.1f, h * 0.55f),
            size = Size(w * 1.2f, h * 0.6f),
            style = Stroke(width = 6f)
        )
    } else {
        // Distant Mountain / City Silhouette
        if (terrain == TerrainType.CITY || terrain == TerrainType.BASE) {
            for (b in 0..12) {
                val bx = b * (w / 12f)
                val bw = (w / 14f)
                val bh = 35f + (b * 29 % 65)
                drawRect(
                    color = Color(0xFF0B162C),
                    topLeft = Offset(bx, horizonY - bh),
                    size = Size(bw, bh)
                )
                drawRect(
                    color = Color(0xFF00E5FF).copy(alpha = 0.25f),
                    topLeft = Offset(bx + 4f, horizonY - bh + 6f),
                    size = Size(bw - 8f, 3f)
                )
            }
        } else {
            val ridge = Path().apply {
                moveTo(0f, horizonY)
                lineTo(w * 0.18f, horizonY - 48f)
                lineTo(w * 0.35f, horizonY - 16f)
                lineTo(w * 0.55f, horizonY - 64f)
                lineTo(w * 0.78f, horizonY - 22f)
                lineTo(w, horizonY - 52f)
                lineTo(w, horizonY)
                close()
            }
            drawPath(ridge, terrain.tilePrimaryColor)
        }

        // Ground plane with perspective tactical grid lines
        drawRect(
            brush = Brush.verticalGradient(
                listOf(terrain.tileAccentColor.copy(alpha = 0.55f), VoidNavy),
                startY = horizonY,
                endY = h
            ),
            topLeft = Offset(0f, horizonY),
            size = Size(w, h - horizonY)
        )
        drawLine(
            color = PanelBorderCyan.copy(alpha = 0.65f),
            start = Offset(0f, horizonY),
            end = Offset(w, horizonY),
            strokeWidth = 2f
        )
    }
}

private fun DrawScope.drawWeaponChoreography(
    style: AnimationStyle,
    origin: Offset,
    target: Offset,
    isImpactPhase: Boolean,
    outcome: StrikeOutcome,
    barrierActive: Boolean,
    targetDestroyed: Boolean,
    t: Float
) {
    val dir = if (target.x >= origin.x) 1f else -1f

    when (style) {
        AnimationStyle.HYPER_BUSTER_RIFLE -> {
            val muzzle = Offset(origin.x + dir * 70f, origin.y - 12f)
            if (!isImpactPhase) {
                // Charging energy sphere + converging particles
                val chargeRadius = 10f + 26f * t
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color.White, Color(0xFF00E5FF), Color.Transparent),
                        center = muzzle,
                        radius = chargeRadius * 1.6f
                    ),
                    radius = chargeRadius * 1.6f,
                    center = muzzle
                )
                for (i in 0..7) {
                    val angle = (i * 45f + t * 180f) * (PI.toFloat() / 180f)
                    val dist = (1f - t) * 65f
                    val px = muzzle.x + cos(angle) * dist
                    val py = muzzle.y + sin(angle) * dist
                    drawCircle(Color(0xFFFFEA00), radius = 4f, center = Offset(px, py))
                }
            } else {
                // Colossal multi-layered Buster Beam across the stage
                val beamHeight = 38f * (1f - t * 0.35f)
                drawLine(
                    color = Color(0xFF00E5FF).copy(alpha = 0.55f),
                    start = muzzle,
                    end = Offset(target.x + dir * 120f, target.y - 8f),
                    strokeWidth = beamHeight * 1.7f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color(0xFFFFEA00).copy(alpha = 0.85f),
                    start = muzzle,
                    end = Offset(target.x + dir * 120f, target.y - 8f),
                    strokeWidth = beamHeight,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color.White,
                    start = muzzle,
                    end = Offset(target.x + dir * 120f, target.y - 8f),
                    strokeWidth = beamHeight * 0.45f,
                    cap = StrokeCap.Round
                )
            }
        }

        AnimationStyle.FIN_FUNNEL_SWARM -> {
            // 6 Autonomous Fin-Funnels orbit the target and fire intersecting lasers
            for (f in 0..5) {
                val angle = (f * 60f + t * 90f) * (PI.toFloat() / 180f)
                val orbitRadius = 115f
                val fx = target.x + cos(angle) * orbitRadius
                val fy = target.y - 25f + sin(angle) * (orbitRadius * 0.65f)
                val funnelPos = Offset(fx, fy)

                // Funnel Drone body
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(fx - 10f, fy - 5f),
                    size = Size(20f, 10f),
                    cornerRadius = CornerRadius(3f, 3f)
                )
                drawCircle(Color(0xFF00E676), radius = 5f, center = funnelPos)

                // Synchronized All-Range Laser Beam
                if (isImpactPhase || t > 0.45f) {
                    drawLine(
                        color = Color(0xFF00E5FF),
                        start = funnelPos,
                        end = target,
                        strokeWidth = 6f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color.White,
                        start = funnelPos,
                        end = target,
                        strokeWidth = 2.5f,
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        AnimationStyle.KAISER_NOVA_BREAKER -> {
            // Blazing Super Robot Solar Aura + Giant Golden Greatsword Cleave
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFFFFEA00).copy(alpha = 0.5f), Color(0xFFFF3D00).copy(alpha = 0.2f), Color.Transparent),
                    center = origin,
                    radius = 180f
                ),
                radius = 180f,
                center = origin
            )
            if (isImpactPhase) {
                // Giant Diagonal Solar Slash X-Arc across target
                drawLine(
                    color = Color(0xFFFF3D00),
                    start = Offset(target.x - 95f, target.y - 95f),
                    end = Offset(target.x + 95f, target.y + 95f),
                    strokeWidth = 22f * (1f - t * 0.5f),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color(0xFFFFEA00),
                    start = Offset(target.x - 95f, target.y - 95f),
                    end = Offset(target.x + 95f, target.y + 95f),
                    strokeWidth = 10f * (1f - t * 0.5f),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color(0xFFFFEA00),
                    start = Offset(target.x - 85f, target.y + 85f),
                    end = Offset(target.x + 85f, target.y - 85f),
                    strokeWidth = 14f * (1f - t * 0.5f),
                    cap = StrokeCap.Round
                )
            }
        }

        AnimationStyle.BEAM_SABER_BLITZ -> {
            if (isImpactPhase) {
                // High-Speed Plasma Twin-Slash
                drawLine(
                    color = Color(0xFFFF4081),
                    start = Offset(target.x - 80f, target.y - 70f),
                    end = Offset(target.x + 80f, target.y + 70f),
                    strokeWidth = 14f * (1f - t * 0.4f),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color.White,
                    start = Offset(target.x - 80f, target.y - 70f),
                    end = Offset(target.x + 80f, target.y + 70f),
                    strokeWidth = 5f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color(0xFF00E5FF),
                    start = Offset(target.x - 70f, target.y + 75f),
                    end = Offset(target.x + 75f, target.y - 75f),
                    strokeWidth = 12f * (1f - t * 0.4f),
                    cap = StrokeCap.Round
                )
            }
        }

        AnimationStyle.MACROSS_MISSILE_STORM -> {
            // 12 Curving Itano Circus Micro-Missiles
            val progress = if (!isImpactPhase) t * 0.7f else (0.7f + t * 0.3f)
            for (m in 0..11) {
                val mProg = ((progress - m * 0.03f).coerceIn(0f, 1f))
                val mx = origin.x + (target.x - origin.x) * mProg
                val arcHeight = (if (m % 2 == 0) -1f else 1f) * (35f + (m % 4) * 22f)
                val my = origin.y + (target.y - origin.y) * mProg + sin(mProg * PI.toFloat()) * arcHeight

                // Smoke contrail
                drawLine(
                    color = Color.White.copy(alpha = 0.45f),
                    start = Offset(origin.x, origin.y - 20f),
                    end = Offset(mx, my),
                    strokeWidth = 2.5f
                )
                // Missile warhead flare
                drawCircle(
                    color = Color(0xFFFF9100),
                    radius = 6f,
                    center = Offset(mx, my)
                )
            }
        }

        AnimationStyle.GRAVITY_SINGULARITY_CANNON -> {
            val orbCenter = if (!isImpactPhase) {
                Offset(
                    origin.x + (target.x - origin.x) * t,
                    origin.y - 20f
                )
            } else target

            val radius = if (!isImpactPhase) 24f + 25f * t else 78f * sin(t * PI.toFloat()).coerceAtLeast(0.2f)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color.Black, Color(0xFFD500F9), Color(0xFFFF1744), Color.Transparent),
                    center = orbCenter,
                    radius = radius * 1.4f
                ),
                radius = radius * 1.4f,
                center = orbCenter
            )
            drawCircle(
                color = Color(0xFFFFEA00),
                radius = radius,
                center = orbCenter,
                style = Stroke(width = 3f)
            )
        }
    }

    // Impact Explosions, Energy Barriers, and Destruction Shockwaves
    if (isImpactPhase) {
        if (outcome != StrikeOutcome.MISS) {
            if (barrierActive) {
                // Hexagonal / Circular Energy Barrier shimmer
                drawCircle(
                    color = Color(0xFF00E5FF).copy(alpha = 0.45f * (1f - t * 0.5f)),
                    radius = 85f,
                    center = target
                )
                drawCircle(
                    color = Color(0xFF00E5FF),
                    radius = 85f,
                    center = target,
                    style = Stroke(width = 4f)
                )
            }

            // Impact Explosion Fireball + Shockwave Ring
            val blastRadius = 35f + 95f * t
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(
                        Color.White.copy(alpha = 1f - t),
                        Color(0xFFFFEA00).copy(alpha = (1f - t) * 0.85f),
                        Color(0xFFFF3D00).copy(alpha = (1f - t) * 0.65f),
                        Color.Transparent
                    ),
                    center = target,
                    radius = blastRadius
                ),
                radius = blastRadius,
                center = target
            )

            if (targetDestroyed) {
                // Extra Super Robot Wars Boss/Unit Destruction Shockwave Ring!
                drawCircle(
                    color = Color(0xFFFFEA00).copy(alpha = (1f - t * 0.7f).coerceIn(0f, 1f)),
                    radius = 50f + 180f * t,
                    center = target,
                    style = Stroke(width = 8f * (1f - t * 0.6f))
                )
            }
        }
    }
}
