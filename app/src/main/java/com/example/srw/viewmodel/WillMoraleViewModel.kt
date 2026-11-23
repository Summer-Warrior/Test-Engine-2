package com.example.srw.viewmodel

import androidx.lifecycle.ViewModel
import com.example.srw.engine.MoraleTier
import com.example.srw.engine.MoraleUpdateResult
import com.example.srw.engine.PilotMoraleSystem
import com.example.srw.engine.TacticalMoraleEvent
import com.example.srw.engine.UnitMoraleChange
import com.example.srw.model.BattleCutsceneSequence
import com.example.srw.model.Faction
import com.example.srw.model.MechaUnit
import com.example.srw.model.PilotPassiveAbility
import com.example.srw.model.StrikeOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Defines a discrete Pilot Confidence (Will / Morale) threshold band and the exact
 * threshold-based stat multipliers unlocked upon reaching it.
 */
data class ConfidenceThreshold(
    val tier: MoraleTier,
    val minConfidence: Int,
    val maxConfidence: Int,
    val label: String,
    val jpKanji: String,
    val attackMultiplier: Double,
    val defenseMultiplier: Double,
    val accuracyMultiplier: Double,
    val evasionMultiplier: Double,
    val mobilityMultiplier: Double,
    val critBonusPercent: Int,
    val summary: String
)

/**
 * Threshold-based stat multipliers and scaled combat stats computed for a pilot/unit
 * at their current confidence level.
 */
data class ThresholdStatMultipliers(
    val confidenceLevel: Int,
    val threshold: ConfidenceThreshold,
    val attackMultiplier: Double,
    val defenseMultiplier: Double,
    val accuracyMultiplier: Double,
    val evasionMultiplier: Double,
    val mobilityMultiplier: Double,
    val critBonusPercent: Int,
    val effectiveMeleeStat: Int,
    val effectiveRangedStat: Int,
    val effectiveHitStat: Int,
    val effectiveEvadeStat: Int,
    val effectiveArmor: Int,
    val effectiveMobility: Int
)

/**
 * Live confidence profile tracked per unit/pilot inside [WillMoraleViewModel].
 */
data class PilotConfidenceProfile(
    val unitId: String,
    val pilotId: String,
    val pilotName: String,
    val unitName: String,
    val faction: Faction,
    val confidenceLevel: Int,
    val tier: MoraleTier,
    val threshold: ConfidenceThreshold,
    val nextThreshold: ConfidenceThreshold?,
    val pointsToNextThreshold: Int,
    val progressWithinTier: Float,
    val totalKillsTracked: Int,
    val totalDamageDealt: Int,
    val statMultipliers: ThresholdStatMultipliers,
    val lastDelta: Int = 0,
    val lastReason: String = ""
)

/**
 * Audit record emitted whenever a pilot's confidence shifts from kills, damage dealt,
 * combat exchanges, or morale events.
 */
data class ConfidenceEventRecord(
    val id: Long,
    val unitId: String,
    val pilotName: String,
    val unitName: String,
    val previousConfidence: Int,
    val newConfidence: Int,
    val delta: Int,
    val previousTier: MoraleTier,
    val newTier: MoraleTier,
    val crossedThreshold: Boolean,
    val reason: String,
    val damageDealt: Int = 0,
    val killsAwarded: Int = 0
)

data class WillMoraleUiState(
    val thresholds: List<ConfidenceThreshold> = WillMoraleViewModel.CONFIDENCE_THRESHOLDS,
    val profilesByUnitId: Map<String, PilotConfidenceProfile> = emptyMap(),
    val lastThresholdNotification: ConfidenceEventRecord? = null,
    val recentEvents: List<ConfidenceEventRecord> = emptyList()
)

/**
 * ViewModel service for the SRW Will/Morale system:
 * - Tracks each pilot's live confidence level (Will 50..170), cumulative kills, and cumulative damage dealt.
 * - Increases confidence dynamically from kills (including boss kills & squad momentum) and damage dealt.
 * - Evaluates discrete threshold-based stat multipliers (Attack, Defense, Accuracy, Evasion, Mobility, Crit)
 *   and publishes reactive [StateFlow] updates and threshold-crossing notifications.
 */
class WillMoraleViewModel(
    private val pilotMoraleSystem: PilotMoraleSystem = PilotMoraleSystem()
) : ViewModel() {

    companion object {
        const val MIN_CONFIDENCE = PilotMoraleSystem.MIN_WILL
        const val BASE_CONFIDENCE = PilotMoraleSystem.BASE_WILL
        const val MAX_CONFIDENCE = PilotMoraleSystem.MAX_WILL

        const val DAMAGE_PER_CONFIDENCE_POINT = 1200
        const val STANDARD_KILL_CONFIDENCE_GAIN = 8
        const val BOSS_KILL_CONFIDENCE_GAIN = 12
        const val ALLY_KILL_BROADCAST_GAIN = 2
        const val ALLY_BOSS_KILL_BROADCAST_GAIN = 4

        /**
         * Canonical ordered list of confidence thresholds and their stat multipliers.
         */
        val CONFIDENCE_THRESHOLDS: List<ConfidenceThreshold> = listOf(
            ConfidenceThreshold(
                tier = MoraleTier.WAVERING,
                minConfidence = 50,
                maxConfidence = 84,
                label = MoraleTier.WAVERING.displayName,
                jpKanji = MoraleTier.WAVERING.jpKanji,
                attackMultiplier = 0.82,
                defenseMultiplier = 0.85,
                accuracyMultiplier = 0.90,
                evasionMultiplier = 0.90,
                mobilityMultiplier = 0.90,
                critBonusPercent = -10,
                summary = MoraleTier.WAVERING.summary
            ),
            ConfidenceThreshold(
                tier = MoraleTier.STEADY,
                minConfidence = 85,
                maxConfidence = 99,
                label = MoraleTier.STEADY.displayName,
                jpKanji = MoraleTier.STEADY.jpKanji,
                attackMultiplier = 0.94,
                defenseMultiplier = 0.95,
                accuracyMultiplier = 0.98,
                evasionMultiplier = 0.98,
                mobilityMultiplier = 0.97,
                critBonusPercent = 0,
                summary = MoraleTier.STEADY.summary
            ),
            ConfidenceThreshold(
                tier = MoraleTier.FOCUSED,
                minConfidence = 100,
                maxConfidence = 109,
                label = MoraleTier.FOCUSED.displayName,
                jpKanji = MoraleTier.FOCUSED.jpKanji,
                attackMultiplier = 1.00,
                defenseMultiplier = 1.00,
                accuracyMultiplier = 1.04,
                evasionMultiplier = 1.04,
                mobilityMultiplier = 1.00,
                critBonusPercent = 4,
                summary = MoraleTier.FOCUSED.summary
            ),
            ConfidenceThreshold(
                tier = MoraleTier.HIGH_SPIRIT,
                minConfidence = 110,
                maxConfidence = 129,
                label = MoraleTier.HIGH_SPIRIT.displayName,
                jpKanji = MoraleTier.HIGH_SPIRIT.jpKanji,
                attackMultiplier = 1.10,
                defenseMultiplier = 1.08,
                accuracyMultiplier = 1.08,
                evasionMultiplier = 1.08,
                mobilityMultiplier = 1.07,
                critBonusPercent = 10,
                summary = MoraleTier.HIGH_SPIRIT.summary
            ),
            ConfidenceThreshold(
                tier = MoraleTier.AWAKENED_OVERDRIVE,
                minConfidence = 130,
                maxConfidence = 149,
                label = MoraleTier.AWAKENED_OVERDRIVE.displayName,
                jpKanji = MoraleTier.AWAKENED_OVERDRIVE.jpKanji,
                attackMultiplier = 1.20,
                defenseMultiplier = 1.15,
                accuracyMultiplier = 1.14,
                evasionMultiplier = 1.14,
                mobilityMultiplier = 1.14,
                critBonusPercent = 16,
                summary = MoraleTier.AWAKENED_OVERDRIVE.summary
            ),
            ConfidenceThreshold(
                tier = MoraleTier.MAX_TRANSCENDENT,
                minConfidence = 150,
                maxConfidence = MAX_CONFIDENCE,
                label = MoraleTier.MAX_TRANSCENDENT.displayName,
                jpKanji = MoraleTier.MAX_TRANSCENDENT.jpKanji,
                attackMultiplier = 1.32,
                defenseMultiplier = 1.22,
                accuracyMultiplier = 1.20,
                evasionMultiplier = 1.20,
                mobilityMultiplier = 1.20,
                critBonusPercent = 24,
                summary = MoraleTier.MAX_TRANSCENDENT.summary
            )
        )
    }

    private val _uiState = MutableStateFlow(WillMoraleUiState())
    val uiState: StateFlow<WillMoraleUiState> = _uiState.asStateFlow()

    private var eventSequence = 0L
    private val trackedDamageByUnitId = mutableMapOf<String, Int>()
    private val trackedKillsByUnitId = mutableMapOf<String, Int>()
    private var lastTelemetrySequenceStamp: Int? = null

    /**
     * Resolves the active [ConfidenceThreshold] for a given numeric [confidence] level.
     */
    fun getThresholdForConfidence(confidence: Int): ConfidenceThreshold {
        val clamped = confidence.coerceIn(MIN_CONFIDENCE, MAX_CONFIDENCE)
        return CONFIDENCE_THRESHOLDS.firstOrNull { clamped in it.minConfidence..it.maxConfidence }
            ?: if (clamped < MIN_CONFIDENCE) CONFIDENCE_THRESHOLDS.first() else CONFIDENCE_THRESHOLDS.last()
    }

    /**
     * Computes all threshold-based stat multipliers and scaled combat stats for [unit]
     * at [confidenceOverride] (defaults to the pilot's current Will).
     */
    fun computeThresholdStatMultipliers(
        unit: MechaUnit,
        confidenceOverride: Int = unit.pilot.will
    ): ThresholdStatMultipliers {
        val confidence = confidenceOverride.coerceIn(MIN_CONFIDENCE, MAX_CONFIDENCE)
        val threshold = getThresholdForConfidence(confidence)
        val critBonus = threshold.critBonusPercent + max(0, (confidence - BASE_CONFIDENCE) / 5)

        return ThresholdStatMultipliers(
            confidenceLevel = confidence,
            threshold = threshold,
            attackMultiplier = threshold.attackMultiplier,
            defenseMultiplier = threshold.defenseMultiplier,
            accuracyMultiplier = threshold.accuracyMultiplier,
            evasionMultiplier = threshold.evasionMultiplier,
            mobilityMultiplier = threshold.mobilityMultiplier,
            critBonusPercent = critBonus,
            effectiveMeleeStat = (unit.pilot.meleeStat * threshold.attackMultiplier).roundToInt(),
            effectiveRangedStat = (unit.pilot.rangedStat * threshold.attackMultiplier).roundToInt(),
            effectiveHitStat = (unit.pilot.hitStat * threshold.accuracyMultiplier).roundToInt(),
            effectiveEvadeStat = (unit.pilot.evadeStat * threshold.evasionMultiplier).roundToInt(),
            effectiveArmor = (unit.armor * threshold.defenseMultiplier).roundToInt(),
            effectiveMobility = (unit.mobility * threshold.mobilityMultiplier).roundToInt().coerceAtLeast(30)
        )
    }

    /**
     * Builds a complete [PilotConfidenceProfile] for [unit], combining live Will with tracked
     * kills, damage dealt, next-threshold progress, and threshold-based stat multipliers.
     */
    fun buildConfidenceProfile(
        unit: MechaUnit,
        lastDelta: Int = 0,
        lastReason: String = ""
    ): PilotConfidenceProfile {
        val confidence = unit.pilot.will.coerceIn(MIN_CONFIDENCE, MAX_CONFIDENCE)
        val threshold = getThresholdForConfidence(confidence)
        val currentIdx = CONFIDENCE_THRESHOLDS.indexOfFirst { it.tier == threshold.tier }
        val nextThreshold = CONFIDENCE_THRESHOLDS.getOrNull(currentIdx + 1)
        val pointsToNext = if (nextThreshold != null) {
            max(0, nextThreshold.minConfidence - confidence)
        } else {
            0
        }
        val progress = if (nextThreshold != null) {
            val span = (nextThreshold.minConfidence - threshold.minConfidence).coerceAtLeast(1)
            ((confidence - threshold.minConfidence).toFloat() / span.toFloat()).coerceIn(0f, 1f)
        } else {
            1f
        }

        val kills = max(unit.pilot.kills, trackedKillsByUnitId[unit.id] ?: 0)
        val damage = trackedDamageByUnitId[unit.id] ?: 0

        return PilotConfidenceProfile(
            unitId = unit.id,
            pilotId = unit.pilot.id,
            pilotName = unit.pilot.name,
            unitName = unit.name,
            faction = unit.faction,
            confidenceLevel = confidence,
            tier = threshold.tier,
            threshold = threshold,
            nextThreshold = nextThreshold,
            pointsToNextThreshold = pointsToNext,
            progressWithinTier = progress,
            totalKillsTracked = kills,
            totalDamageDealt = damage,
            statMultipliers = computeThresholdStatMultipliers(unit, confidence),
            lastDelta = lastDelta,
            lastReason = lastReason
        )
    }

    /**
     * Synchronizes the current battlefield [units] into reactive [uiState] confidence profiles
     * while preserving tracked cumulative kills and damage dealt.
     */
    fun syncRoster(units: List<MechaUnit>) {
        val previousProfiles = _uiState.value.profilesByUnitId
        val nextProfiles = units
            .filter { it.isAlive() }
            .associate { unit ->
                val prev = previousProfiles[unit.id]
                trackedKillsByUnitId[unit.id] = max(unit.pilot.kills, trackedKillsByUnitId[unit.id] ?: 0)
                unit.id to buildConfidenceProfile(
                    unit = unit,
                    lastDelta = prev?.lastDelta ?: 0,
                    lastReason = prev?.lastReason ?: ""
                )
            }
        _uiState.update { it.copy(profilesByUnitId = nextProfiles) }
    }

    /**
     * Calculates confidence gained from dealing [damageDealt] in combat:
     * - Base: +1 per [DAMAGE_PER_CONFIDENCE_POINT] dealt (minimum +1 for any damage > 0).
     * - Heavy strike bonus: +2 when dealing >= 40% of [targetMaxHp].
     * - Critical hit bonus: +2 when [isCritical] is true.
     * - Passive bonus: +3 when pilot has [PilotPassiveAbility.MORALE_SURGE].
     */
    fun calculateConfidenceGainForDamage(
        unit: MechaUnit,
        damageDealt: Int,
        targetMaxHp: Int = 0,
        isCritical: Boolean = false
    ): Int {
        if (damageDealt <= 0) return 0
        val scaledBase = max(1, damageDealt / DAMAGE_PER_CONFIDENCE_POINT)
        val heavyBonus = if (targetMaxHp > 0 && damageDealt >= targetMaxHp * 0.40) 2 else 0
        val critBonus = if (isCritical) 2 else 0
        val surgeBonus = if (unit.pilot.hasPassive(PilotPassiveAbility.MORALE_SURGE)) 3 else 0
        return scaledBase + heavyBonus + critBonus + surgeBonus
    }

    /**
     * Increases a pilot's confidence level after dealing damage in battle, updates their
     * cumulative damage dealt & threshold stat multipliers, and returns the updated roster.
     */
    fun recordDamageDealt(
        unitId: String,
        damageDealt: Int,
        units: List<MechaUnit>,
        targetMaxHp: Int = 0,
        isCritical: Boolean = false
    ): MoraleUpdateResult {
        val targetUnit = units.find { it.id == unitId && it.isAlive() }
            ?: return MoraleUpdateResult(units, emptyList(), "NO UNIT", "Unit not found.")

        val gain = calculateConfidenceGainForDamage(
            unit = targetUnit,
            damageDealt = damageDealt,
            targetMaxHp = targetMaxHp,
            isCritical = isCritical
        )
        if (gain <= 0) {
            return MoraleUpdateResult(units, emptyList(), "NO CONFIDENCE GAIN", "Zero damage dealt.")
        }

        trackedDamageByUnitId[unitId] = (trackedDamageByUnitId[unitId] ?: 0) + damageDealt
        val reason = buildString {
            append("Dealt $damageDealt DMG")
            if (isCritical) append(" (CRIT)")
            append(" +$gain")
        }

        val (updatedUnit, change) = pilotMoraleSystem.adjustUnitWill(targetUnit, gain, reason)
        val updatedUnits = units.map { if (it.id == unitId) updatedUnit else it }

        publishChanges(
            updatedUnits = updatedUnits,
            changes = listOf(change),
            damageByUnit = mapOf(unitId to damageDealt)
        )

        return MoraleUpdateResult(
            updatedUnits = updatedUnits,
            changes = listOf(change),
            summaryHeadline = "CONFIDENCE SURGE // ${updatedUnit.pilot.name} +${change.delta} Will",
            summaryDetail = "${updatedUnit.pilot.name} confidence ${change.previousWill}->${change.newWill} ($reason)"
        )
    }

    /**
     * Increases a pilot's confidence level upon scoring a kill (+8 standard / +12 boss,
     * plus MORALE_SURGE and heavy damage bonus) and broadcasts morale momentum to living allies.
     */
    fun recordKill(
        unitId: String,
        units: List<MechaUnit>,
        isBossKill: Boolean = false,
        damageDealt: Int = 0
    ): MoraleUpdateResult {
        val killer = units.find { it.id == unitId && it.isAlive() }
            ?: return MoraleUpdateResult(units, emptyList(), "NO UNIT", "Unit not found.")

        trackedKillsByUnitId[unitId] = max(killer.pilot.kills, trackedKillsByUnitId[unitId] ?: 0) + 1
        if (damageDealt > 0) {
            trackedDamageByUnitId[unitId] = (trackedDamageByUnitId[unitId] ?: 0) + damageDealt
        }

        val baseKillGain = if (isBossKill) BOSS_KILL_CONFIDENCE_GAIN else STANDARD_KILL_CONFIDENCE_GAIN
        val surgeBonus = if (killer.pilot.hasPassive(PilotPassiveAbility.MORALE_SURGE)) 3 else 0
        val overkillBonus = if (damageDealt >= 4000) 2 else 0
        val totalKillerGain = baseKillGain + surgeBonus + overkillBonus
        val broadcastGain = if (isBossKill) ALLY_BOSS_KILL_BROADCAST_GAIN else ALLY_KILL_BROADCAST_GAIN

        val changes = mutableListOf<UnitMoraleChange>()
        val updatedUnits = units.map { unit ->
            if (!unit.isAlive()) return@map unit
            when {
                unit.id == unitId -> {
                    val reason = buildString {
                        append(if (isBossKill) "Boss Kill +$baseKillGain" else "Enemy Kill +$baseKillGain")
                        if (overkillBonus > 0) append(", Heavy Finisher +$overkillBonus")
                        if (surgeBonus > 0) append(", Morale Surge +$surgeBonus")
                    }
                    val (adj, chg) = pilotMoraleSystem.adjustUnitWill(unit, totalKillerGain, reason)
                    changes.add(chg)
                    adj.copy(pilot = adj.pilot.copy(kills = adj.pilot.kills + 1))
                }
                unit.faction == killer.faction -> {
                    val (adj, chg) = pilotMoraleSystem.adjustUnitWill(
                        unit,
                        broadcastGain,
                        "Squad Kill Momentum +$broadcastGain"
                    )
                    changes.add(chg)
                    adj
                }
                else -> unit
            }
        }

        publishChanges(
            updatedUnits = updatedUnits,
            changes = changes,
            damageByUnit = if (damageDealt > 0) mapOf(unitId to damageDealt) else emptyMap(),
            killsByUnit = mapOf(unitId to 1)
        )

        val killerChange = changes.first { it.unitId == unitId }
        return MoraleUpdateResult(
            updatedUnits = updatedUnits,
            changes = changes,
            summaryHeadline = "KILL CONFIDENCE // ${killer.pilot.name} +${killerChange.delta} Will",
            summaryDetail = "${killer.pilot.name} reached ${killerChange.newWill} Will [${killerChange.newTier.displayName}] (${killerChange.reason})"
        )
    }

    /**
     * Direct confidence adjustment helper for custom tactical events or test scenarios.
     */
    fun adjustConfidence(
        unitId: String,
        delta: Int,
        reason: String,
        units: List<MechaUnit>
    ): MoraleUpdateResult {
        val targetUnit = units.find { it.id == unitId && it.isAlive() }
            ?: return MoraleUpdateResult(units, emptyList(), "NO UNIT", "Unit not found.")

        val (updatedUnit, change) = pilotMoraleSystem.adjustUnitWill(targetUnit, delta, reason)
        val updatedUnits = units.map { if (it.id == unitId) updatedUnit else it }

        publishChanges(updatedUnits = updatedUnits, changes = listOf(change))
        return MoraleUpdateResult(
            updatedUnits = updatedUnits,
            changes = listOf(change),
            summaryHeadline = "CONFIDENCE ADJUSTED // ${updatedUnit.pilot.name}",
            summaryDetail = "${updatedUnit.pilot.name} Will ${change.previousWill}->${change.newWill} ($reason)"
        )
    }

    /**
     * Processes a complete combat exchange through [PilotMoraleSystem], tracking both combatants'
     * kills and damage dealt and updating all reactive confidence profiles and threshold multipliers.
     */
    fun processCombatExchange(
        sequence: BattleCutsceneSequence,
        currentUnits: List<MechaUnit>
    ): MoraleUpdateResult {
        val result = pilotMoraleSystem.processCombatExchangeMorale(sequence, currentUnits)
        val damageMap = mutableMapOf<String, Int>()
        val killsMap = mutableMapOf<String, Int>()

        if (sequence.primaryOutcome != StrikeOutcome.MISS && sequence.primaryDamage > 0) {
            damageMap[sequence.attacker.id] = sequence.primaryDamage
            trackedDamageByUnitId[sequence.attacker.id] =
                (trackedDamageByUnitId[sequence.attacker.id] ?: 0) + sequence.primaryDamage
        }
        if (sequence.defenderHpAfterPrimary <= 0) {
            killsMap[sequence.attacker.id] = 1
            trackedKillsByUnitId[sequence.attacker.id] =
                (trackedKillsByUnitId[sequence.attacker.id] ?: 0) + 1
        }
        if (sequence.counterExecuted && sequence.counterOutcome != StrikeOutcome.MISS && sequence.counterDamage > 0) {
            damageMap[sequence.defender.id] = sequence.counterDamage
            trackedDamageByUnitId[sequence.defender.id] =
                (trackedDamageByUnitId[sequence.defender.id] ?: 0) + sequence.counterDamage
        }
        if (sequence.counterExecuted && sequence.attackerHpAfterCounter <= 0) {
            killsMap[sequence.defender.id] = 1
            trackedKillsByUnitId[sequence.defender.id] =
                (trackedKillsByUnitId[sequence.defender.id] ?: 0) + 1
        }

        publishChanges(
            updatedUnits = result.updatedUnits,
            changes = result.changes,
            damageByUnit = damageMap,
            killsByUnit = killsMap
        )
        return result
    }

    /**
     * Records kill, damage, and morale-change telemetry for a combat exchange already resolved by
     * [SrwEngineViewModel] without double-applying Will deltas.
     */
    fun recordCompletedCombatTelemetry(
        sequenceStamp: Int,
        sequence: BattleCutsceneSequence,
        changes: List<UnitMoraleChange>,
        currentUnits: List<MechaUnit>
    ) {
        if (lastTelemetrySequenceStamp == sequenceStamp) return
        lastTelemetrySequenceStamp = sequenceStamp

        val damageMap = mutableMapOf<String, Int>()
        val killsMap = mutableMapOf<String, Int>()

        if (sequence.primaryOutcome != StrikeOutcome.MISS && sequence.primaryDamage > 0) {
            damageMap[sequence.attacker.id] = sequence.primaryDamage
            trackedDamageByUnitId[sequence.attacker.id] =
                (trackedDamageByUnitId[sequence.attacker.id] ?: 0) + sequence.primaryDamage
        }
        if (sequence.defenderHpAfterPrimary <= 0) {
            killsMap[sequence.attacker.id] = 1
            trackedKillsByUnitId[sequence.attacker.id] =
                (trackedKillsByUnitId[sequence.attacker.id] ?: 0) + 1
        }
        if (sequence.counterExecuted && sequence.counterOutcome != StrikeOutcome.MISS && sequence.counterDamage > 0) {
            damageMap[sequence.defender.id] = sequence.counterDamage
            trackedDamageByUnitId[sequence.defender.id] =
                (trackedDamageByUnitId[sequence.defender.id] ?: 0) + sequence.counterDamage
        }
        if (sequence.counterExecuted && sequence.attackerHpAfterCounter <= 0) {
            killsMap[sequence.defender.id] = 1
            trackedKillsByUnitId[sequence.defender.id] =
                (trackedKillsByUnitId[sequence.defender.id] ?: 0) + 1
        }

        publishChanges(
            updatedUnits = currentUnits,
            changes = changes,
            damageByUnit = damageMap,
            killsByUnit = killsMap
        )
    }

    /**
     * Triggers a battlefield [TacticalMoraleEvent] and updates all reactive confidence profiles.
     */
    fun triggerMoraleEvent(
        event: TacticalMoraleEvent,
        units: List<MechaUnit>,
        selectedUnitId: String?
    ): MoraleUpdateResult {
        val result = pilotMoraleSystem.triggerMoraleEvent(event, units, selectedUnitId)
        publishChanges(updatedUnits = result.updatedUnits, changes = result.changes)
        return result
    }

    fun dismissThresholdNotification() {
        _uiState.update { it.copy(lastThresholdNotification = null) }
    }

    private fun publishChanges(
        updatedUnits: List<MechaUnit>,
        changes: List<UnitMoraleChange>,
        damageByUnit: Map<String, Int> = emptyMap(),
        killsByUnit: Map<String, Int> = emptyMap()
    ) {
        val changeByUnitId = changes.associateBy { it.unitId }
        val newRecords = changes.map { chg ->
            eventSequence += 1L
            ConfidenceEventRecord(
                id = eventSequence,
                unitId = chg.unitId,
                pilotName = chg.pilotName,
                unitName = chg.unitName,
                previousConfidence = chg.previousWill,
                newConfidence = chg.newWill,
                delta = chg.delta,
                previousTier = chg.previousTier,
                newTier = chg.newTier,
                crossedThreshold = chg.tierChanged,
                reason = chg.reason,
                damageDealt = damageByUnit[chg.unitId] ?: 0,
                killsAwarded = killsByUnit[chg.unitId] ?: 0
            )
        }

        val thresholdCrossRecord = newRecords.firstOrNull { it.crossedThreshold }

        _uiState.update { state ->
            val nextProfiles = updatedUnits
                .filter { it.isAlive() }
                .associate { unit ->
                    val chg = changeByUnitId[unit.id]
                    val prev = state.profilesByUnitId[unit.id]
                    unit.id to buildConfidenceProfile(
                        unit = unit,
                        lastDelta = chg?.delta ?: (prev?.lastDelta ?: 0),
                        lastReason = chg?.reason ?: (prev?.lastReason ?: "")
                    )
                }

            state.copy(
                profilesByUnitId = nextProfiles,
                lastThresholdNotification = thresholdCrossRecord ?: state.lastThresholdNotification,
                recentEvents = (newRecords.reversed() + state.recentEvents).take(30)
            )
        }
    }
}
