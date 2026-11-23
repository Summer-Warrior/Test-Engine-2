package com.example.srw.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import com.example.srw.model.Faction
import com.example.srw.model.MechaUnit
import com.example.srw.model.Pilot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.min

/**
 * Snapshot of a pilot's current level, XP earned inside the active tier, and threshold to reach the next level.
 */
data class PilotLevelProgress(
    val unitId: String,
    val pilotName: String,
    val level: Int,
    val totalXp: Int,
    val currentLevelThreshold: Int,
    val nextLevelThreshold: Int,
    val xpIntoLevel: Int,
    val xpNeededForNextLevel: Int,
    val xpRemaining: Int,
    val isMaxLevel: Boolean,
    val progressFraction: Float
)

/**
 * Visual notification payload emitted when a pilot crosses an XP threshold to reach a new level.
 */
data class PilotLevelUpNotification(
    val id: Long,
    val unitId: String,
    val unitName: String,
    val pilotName: String,
    val callsign: String,
    val previousLevel: Int,
    val newLevel: Int,
    val newRankTitle: String,
    val totalXpEarned: Int,
    val nextLevelThreshold: Int,
    val xpRemainingToNext: Int,
    val statBoostSummary: String,
    val accentColor: Color
)

data class PilotLevelManagerUiState(
    val progressByUnitId: Map<String, PilotLevelProgress> = emptyMap(),
    val activeNotification: PilotLevelUpNotification? = null,
    val pendingNotifications: List<PilotLevelUpNotification> = emptyList(),
    val levelUpHistory: List<PilotLevelUpNotification> = emptyList()
)

data class PilotLevelEvaluationResult(
    val updatedUnits: List<MechaUnit>,
    val notifications: List<PilotLevelUpNotification>,
    val summaryHeadline: String,
    val summaryDetail: String
)

/**
 * Dedicated ViewModel that calculates progressive XP thresholds for pilot leveling,
 * applies per-level stat growth, tracks XP progress per unit, and manages queued visual
 * level-up notifications when a pilot reaches a new level.
 */
class PilotLevelManager : ViewModel() {

    companion object {
        const val MAX_PILOT_LEVEL = 50
        const val STAT_GAIN_PER_LEVEL = 4
        const val SP_GAIN_PER_LEVEL = 5
    }

    private val _uiState = MutableStateFlow(PilotLevelManagerUiState())
    val uiState: StateFlow<PilotLevelManagerUiState> = _uiState.asStateFlow()

    // Tracks the highest level already notified per unit so state recompositions never replay old alerts
    private val highestNotifiedLevelByUnit = mutableMapOf<String, Int>()
    private var notificationSequence = 0L

    /**
     * Cumulative total XP threshold required to reach [level].
     * Level 1 = 0, Level 2 = 200, Level 3 = 450, Level 4 = 750, Level 5 = 1100, ...
     */
    fun xpThresholdForLevel(level: Int): Int {
        val clamped = level.coerceIn(1, MAX_PILOT_LEVEL)
        if (clamped <= 1) return 0
        val n = clamped - 1
        return 175 * n + 25 * n * n
    }

    /**
     * Incremental XP span needed to advance from [level] to [level] + 1.
     */
    fun xpStepCostForLevel(level: Int): Int {
        val clamped = level.coerceIn(1, MAX_PILOT_LEVEL)
        if (clamped >= MAX_PILOT_LEVEL) return 0
        return xpThresholdForLevel(clamped + 1) - xpThresholdForLevel(clamped)
    }

    /**
     * Resolves the pilot level corresponding to [totalXp] using progressive thresholds.
     */
    fun calculateLevelForXp(totalXp: Int): Int {
        val safeXp = totalXp.coerceAtLeast(0)
        var resolvedLevel = 1
        for (lvl in 2..MAX_PILOT_LEVEL) {
            if (safeXp >= xpThresholdForLevel(lvl)) {
                resolvedLevel = lvl
            } else {
                break
            }
        }
        return resolvedLevel
    }

    /**
     * Computes progress metrics between the pilot's current level threshold and the next level threshold.
     */
    fun getLevelProgress(pilot: Pilot, unitId: String = pilot.id): PilotLevelProgress {
        val effectiveLevel = maxOf(pilot.level, calculateLevelForXp(pilot.totalXpEarned))
            .coerceIn(1, MAX_PILOT_LEVEL)
        val isMax = effectiveLevel >= MAX_PILOT_LEVEL
        val currentThreshold = xpThresholdForLevel(effectiveLevel)
        val nextThreshold = if (isMax) currentThreshold else xpThresholdForLevel(effectiveLevel + 1)
        val xpSpan = (nextThreshold - currentThreshold).coerceAtLeast(0)
        val xpInto = if (isMax) 0 else (pilot.totalXpEarned - currentThreshold).coerceAtLeast(0)
        val xpLeft = if (isMax) 0 else (nextThreshold - pilot.totalXpEarned).coerceAtLeast(0)
        val fraction = if (isMax || xpSpan == 0) {
            1f
        } else {
            (xpInto.toFloat() / xpSpan.toFloat()).coerceIn(0f, 1f)
        }

        return PilotLevelProgress(
            unitId = unitId,
            pilotName = pilot.name,
            level = effectiveLevel,
            totalXp = pilot.totalXpEarned,
            currentLevelThreshold = currentThreshold,
            nextLevelThreshold = nextThreshold,
            xpIntoLevel = xpInto,
            xpNeededForNextLevel = xpSpan,
            xpRemaining = xpLeft,
            isMaxLevel = isMax,
            progressFraction = fraction
        )
    }

    fun getLevelProgress(unit: MechaUnit): PilotLevelProgress =
        getLevelProgress(unit.pilot, unit.id)

    /**
     * Inspects all units, advances any allied pilots whose [Pilot.totalXpEarned] crossed one or more
     * XP thresholds, applies per-level stat boosts, updates reactive progress state, and enqueues
     * visual notifications for newly reached levels.
     */
    fun evaluateRosterProgress(units: List<MechaUnit>): PilotLevelEvaluationResult {
        val newNotifications = mutableListOf<PilotLevelUpNotification>()
        val progressMap = mutableMapOf<String, PilotLevelProgress>()

        val updatedUnits = units.map { unit ->
            if (unit.faction != Faction.ALLY) {
                unit
            } else {
                val pilot = unit.pilot
                val targetLevel = calculateLevelForXp(pilot.totalXpEarned)
                val previousLevel = pilot.level

                val updatedUnit = if (targetLevel > previousLevel) {
                    val levelsGained = targetLevel - previousLevel
                    val statGain = STAT_GAIN_PER_LEVEL * levelsGained
                    val spGain = SP_GAIN_PER_LEVEL * levelsGained
                    val newMaxSp = pilot.maxSp + spGain
                    val leveledPilot = pilot.copy(
                        level = targetLevel,
                        meleeStat = pilot.meleeStat + statGain,
                        rangedStat = pilot.rangedStat + statGain,
                        hitStat = pilot.hitStat + statGain,
                        evadeStat = pilot.evadeStat + statGain,
                        maxSp = newMaxSp,
                        currentSp = min(
                            newMaxSp + (unit.magicLevel - 1) * 20,
                            pilot.currentSp + spGain
                        )
                    )
                    val leveledUnit = unit.copy(pilot = leveledPilot)
                    val progressAfter = getLevelProgress(leveledUnit)

                    val alreadyNotifiedLevel = highestNotifiedLevelByUnit[unit.id] ?: previousLevel
                    if (targetLevel > alreadyNotifiedLevel) {
                        highestNotifiedLevelByUnit[unit.id] = targetLevel
                        notificationSequence += 1L
                        newNotifications.add(
                            PilotLevelUpNotification(
                                id = notificationSequence,
                                unitId = unit.id,
                                unitName = unit.name,
                                pilotName = leveledPilot.name,
                                callsign = leveledPilot.callsign,
                                previousLevel = previousLevel,
                                newLevel = targetLevel,
                                newRankTitle = leveledPilot.rankTitle,
                                totalXpEarned = leveledPilot.totalXpEarned,
                                nextLevelThreshold = progressAfter.nextLevelThreshold,
                                xpRemainingToNext = progressAfter.xpRemaining,
                                statBoostSummary = "+$statGain MEL/RNG/HIT/EVA • +$spGain MAX SP",
                                accentColor = leveledPilot.primaryColor
                            )
                        )
                    }
                    leveledUnit
                } else {
                    unit
                }

                progressMap[unit.id] = getLevelProgress(updatedUnit)
                updatedUnit
            }
        }

        _uiState.update { state ->
            if (newNotifications.isEmpty()) {
                state.copy(progressByUnitId = progressMap)
            } else {
                val nextActive = state.activeNotification ?: newNotifications.first()
                val additionalQueued = if (state.activeNotification == null) {
                    newNotifications.drop(1)
                } else {
                    newNotifications
                }
                state.copy(
                    progressByUnitId = progressMap,
                    activeNotification = nextActive,
                    pendingNotifications = state.pendingNotifications + additionalQueued,
                    levelUpHistory = (newNotifications.reversed() + state.levelUpHistory).take(25)
                )
            }
        }

        val summaryHeadline = when {
            newNotifications.isEmpty() -> ""
            newNotifications.size == 1 -> {
                val n = newNotifications.first()
                "LEVEL UP! ${n.pilotName} reached LV.${n.newLevel} (${n.newRankTitle})"
            }
            else -> "${newNotifications.size} PILOTS LEVELED UP!"
        }
        val summaryDetail = newNotifications.joinToString(" • ") { n ->
            "${n.pilotName} LV.${n.previousLevel}→LV.${n.newLevel} (${n.statBoostSummary}; Next LV.${n.newLevel + 1} at ${n.nextLevelThreshold} XP)"
        }

        return PilotLevelEvaluationResult(
            updatedUnits = updatedUnits,
            notifications = newNotifications,
            summaryHeadline = summaryHeadline,
            summaryDetail = summaryDetail
        )
    }

    /**
     * Grants [xpGain] battle XP directly to [unitId] and checks whether any XP threshold was crossed.
     */
    fun awardBattleXp(
        unitId: String,
        xpGain: Int,
        units: List<MechaUnit>
    ): PilotLevelEvaluationResult {
        if (xpGain <= 0) return evaluateRosterProgress(units)
        val grantedUnits = units.map { unit ->
            if (unit.id == unitId && unit.isAlive()) {
                unit.copy(
                    pilot = unit.pilot.copy(
                        xp = unit.pilot.xp + xpGain,
                        totalXpEarned = unit.pilot.totalXpEarned + xpGain
                    )
                )
            } else {
                unit
            }
        }
        return evaluateRosterProgress(grantedUnits)
    }

    /**
     * Dismisses the currently visible level-up notification banner and displays the next queued one if present.
     */
    fun dismissNotification() {
        _uiState.update { state ->
            val next = state.pendingNotifications.firstOrNull()
            val remaining = if (state.pendingNotifications.isNotEmpty()) {
                state.pendingNotifications.drop(1)
            } else {
                emptyList()
            }
            state.copy(
                activeNotification = next,
                pendingNotifications = remaining
            )
        }
    }

    fun clearAllNotifications() {
        _uiState.update {
            it.copy(
                activeNotification = null,
                pendingNotifications = emptyList()
            )
        }
    }
}
