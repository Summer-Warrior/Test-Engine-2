package com.example.srw.viewmodel

import androidx.lifecycle.ViewModel
import com.example.srw.engine.PilotProgressionSystem
import com.example.srw.engine.SpiritCastResult
import com.example.srw.engine.SpiritCommandSystem
import com.example.srw.model.Faction
import com.example.srw.model.MechaUnit
import com.example.srw.model.Pilot
import com.example.srw.model.SpiritCategory
import com.example.srw.model.SpiritCommand
import com.example.srw.model.SpiritDebuff
import com.example.srw.model.SpiritTargetType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.max

/**
 * Structured predefined combat stat effect definition for a [SpiritCommand].
 */
data class PredefinedSpiritEffect(
    val spirit: SpiritCommand,
    val baseSpCost: Int,
    val category: SpiritCategory,
    val targetType: SpiritTargetType,
    val attackMultiplier: Double = 1.0,
    val incomingDamageMultiplier: Double = 1.0,
    val hitBonusPercent: Int = 0,
    val evadeBonusPercent: Int = 0,
    val critBonusPercent: Int = 0,
    val moveRangeBonus: Int = 0,
    val initiativeBonus: Int = 0,
    val willDelta: Int = 0,
    val hpRestoreAmount: Int = 0,
    val enRestoreAmount: Int = 0,
    val enemyDefensePenaltyMultiplier: Double = 1.0,
    val enemyMobilityPenalty: Int = 0,
    val guaranteedHit: Boolean = false,
    val guaranteedEvade: Boolean = false,
    val cleansesDebuffs: Boolean = false,
    val restoresFullEnAndAmmo: Boolean = false,
    val effectStatTags: List<String> = emptyList(),
    val description: String = spirit.summary
)

/**
 * Net active combat stat modifiers currently applied to a unit from active Spirit Commands & Debuffs.
 */
data class ActiveSpiritStatModifiers(
    val unitId: String,
    val activeSpirits: Set<SpiritCommand>,
    val activeDebuffs: Set<SpiritDebuff>,
    val attackMultiplier: Double,
    val incomingDamageMultiplier: Double,
    val hitBonusPercent: Int,
    val evadeBonusPercent: Int,
    val critBonusPercent: Int,
    val moveRangeBonus: Int,
    val initiativeBonus: Int,
    val guaranteedHit: Boolean,
    val guaranteedEvade: Boolean,
    val activeEffectTags: List<String>
) {
    val hasCombatModifiers: Boolean
        get() = activeSpirits.isNotEmpty() || activeDebuffs.isNotEmpty()
}

/**
 * UI-ready status of an individual predefined Spirit Command owned by a pilot.
 */
data class PilotSpiritAbilityStatus(
    val spirit: SpiritCommand,
    val effect: PredefinedSpiritEffect,
    val effectiveSpCost: Int,
    val masteryRank: Int,
    val isAlreadyActive: Boolean,
    val canCast: Boolean,
    val eligibleTargetCount: Int
)

/**
 * Record emitted whenever a pilot activates a Spirit Command through [SpiritCommandsViewModel].
 */
data class SpiritActivationRecord(
    val id: Long,
    val casterId: String,
    val casterName: String,
    val pilotName: String,
    val spirit: SpiritCommand,
    val predefinedEffect: PredefinedSpiritEffect,
    val spSpent: Int,
    val targetUnitIds: List<String>,
    val headline: String,
    val detail: String
)

data class SpiritCommandsViewModelUiState(
    val predefinedEffects: List<PredefinedSpiritEffect> = SpiritCommandsViewModel.PREDEFINED_SPIRIT_EFFECTS,
    val modifiersByUnitId: Map<String, ActiveSpiritStatModifiers> = emptyMap(),
    val selectedCategoryFilter: SpiritCategory? = null,
    val lastActivationBanner: SpiritActivationRecord? = null,
    val activationHistory: List<SpiritActivationRecord> = emptyList()
)

/**
 * ViewModel service managing the SRW Spirit Commands system:
 * - Exposes the canonical list of predefined Spirit Command combat effects ([PREDEFINED_SPIRIT_EFFECTS]).
 * - Computes live net combat stat modifiers ([ActiveSpiritStatModifiers]) per unit.
 * - Executes Spirit Command activations on behalf of pilots and publishes reactive activation records.
 */
class SpiritCommandsViewModel(
    private val progressionSystem: PilotProgressionSystem = PilotProgressionSystem(),
    private val spiritCommandSystem: SpiritCommandSystem = SpiritCommandSystem(progressionSystem)
) : ViewModel() {

    companion object {
        /**
         * Canonical predefined list of Spirit Command combat effects & stat modifiers.
         */
        val PREDEFINED_SPIRIT_EFFECTS: List<PredefinedSpiritEffect> = listOf(
            PredefinedSpiritEffect(
                spirit = SpiritCommand.STRIKE,
                baseSpCost = SpiritCommand.STRIKE.spCost,
                category = SpiritCommand.STRIKE.category,
                targetType = SpiritCommand.STRIKE.targetType,
                hitBonusPercent = 100,
                initiativeBonus = 8,
                guaranteedHit = true,
                effectStatTags = listOf("100% HIT", "+8 INIT")
            ),
            PredefinedSpiritEffect(
                spirit = SpiritCommand.ALERT,
                baseSpCost = SpiritCommand.ALERT.spCost,
                category = SpiritCommand.ALERT.category,
                targetType = SpiritCommand.ALERT.targetType,
                evadeBonusPercent = 100,
                guaranteedEvade = true,
                effectStatTags = listOf("100% DODGE")
            ),
            PredefinedSpiritEffect(
                spirit = SpiritCommand.FOCUS,
                baseSpCost = SpiritCommand.FOCUS.spCost,
                category = SpiritCommand.FOCUS.category,
                targetType = SpiritCommand.FOCUS.targetType,
                hitBonusPercent = 30,
                evadeBonusPercent = 30,
                initiativeBonus = 20,
                effectStatTags = listOf("+30% HIT", "+30% EVA", "+20 INIT")
            ),
            PredefinedSpiritEffect(
                spirit = SpiritCommand.ACCEL,
                baseSpCost = SpiritCommand.ACCEL.spCost,
                category = SpiritCommand.ACCEL.category,
                targetType = SpiritCommand.ACCEL.targetType,
                moveRangeBonus = 3,
                initiativeBonus = 25,
                effectStatTags = listOf("+3 MOVE", "+25 INIT")
            ),
            PredefinedSpiritEffect(
                spirit = SpiritCommand.VALOR,
                baseSpCost = SpiritCommand.VALOR.spCost,
                category = SpiritCommand.VALOR.category,
                targetType = SpiritCommand.VALOR.targetType,
                attackMultiplier = 2.0,
                effectStatTags = listOf("2.00x ATK")
            ),
            PredefinedSpiritEffect(
                spirit = SpiritCommand.SOUL,
                baseSpCost = SpiritCommand.SOUL.spCost,
                category = SpiritCommand.SOUL.category,
                targetType = SpiritCommand.SOUL.targetType,
                attackMultiplier = 2.5,
                effectStatTags = listOf("2.50x ATK")
            ),
            PredefinedSpiritEffect(
                spirit = SpiritCommand.IRON_WALL,
                baseSpCost = SpiritCommand.IRON_WALL.spCost,
                category = SpiritCommand.IRON_WALL.category,
                targetType = SpiritCommand.IRON_WALL.targetType,
                incomingDamageMultiplier = 0.40,
                effectStatTags = listOf("-60% DMG TAKEN")
            ),
            PredefinedSpiritEffect(
                spirit = SpiritCommand.DRIVE,
                baseSpCost = SpiritCommand.DRIVE.spCost,
                category = SpiritCommand.DRIVE.category,
                targetType = SpiritCommand.DRIVE.targetType,
                willDelta = 10,
                effectStatTags = listOf("+10 WILL")
            ),
            PredefinedSpiritEffect(
                spirit = SpiritCommand.ROUSE,
                baseSpCost = SpiritCommand.ROUSE.spCost,
                category = SpiritCommand.ROUSE.category,
                targetType = SpiritCommand.ROUSE.targetType,
                willDelta = 12,
                cleansesDebuffs = true,
                effectStatTags = listOf("+12 WILL", "CLEANSE")
            ),
            PredefinedSpiritEffect(
                spirit = SpiritCommand.ANALYZE,
                baseSpCost = SpiritCommand.ANALYZE.spCost,
                category = SpiritCommand.ANALYZE.category,
                targetType = SpiritCommand.ANALYZE.targetType,
                evadeBonusPercent = -15,
                enemyDefensePenaltyMultiplier = 1.25,
                effectStatTags = listOf("+25% TARGET DMG", "-15% TARGET EVA")
            ),
            PredefinedSpiritEffect(
                spirit = SpiritCommand.EXHAUST,
                baseSpCost = SpiritCommand.EXHAUST.spCost,
                category = SpiritCommand.EXHAUST.category,
                targetType = SpiritCommand.EXHAUST.targetType,
                willDelta = -15,
                enRestoreAmount = -30,
                effectStatTags = listOf("-15 ENEMY WILL", "-30 EN")
            ),
            PredefinedSpiritEffect(
                spirit = SpiritCommand.CONFUSE,
                baseSpCost = SpiritCommand.CONFUSE.spCost,
                category = SpiritCommand.CONFUSE.category,
                targetType = SpiritCommand.CONFUSE.targetType,
                hitBonusPercent = -35,
                enemyMobilityPenalty = 15,
                effectStatTags = listOf("-35% ENEMY HIT", "-15 MOBILITY")
            ),
            PredefinedSpiritEffect(
                spirit = SpiritCommand.TRUST,
                baseSpCost = SpiritCommand.TRUST.spCost,
                category = SpiritCommand.TRUST.category,
                targetType = SpiritCommand.TRUST.targetType,
                hpRestoreAmount = 3000,
                cleansesDebuffs = true,
                effectStatTags = listOf("+3,000 HP", "CLEANSE")
            ),
            PredefinedSpiritEffect(
                spirit = SpiritCommand.FRIENDSHIP,
                baseSpCost = SpiritCommand.FRIENDSHIP.spCost,
                category = SpiritCommand.FRIENDSHIP.category,
                targetType = SpiritCommand.FRIENDSHIP.targetType,
                hpRestoreAmount = 2500,
                enRestoreAmount = 35,
                cleansesDebuffs = true,
                effectStatTags = listOf("+2,500 ALLY HP", "+35 EN")
            ),
            PredefinedSpiritEffect(
                spirit = SpiritCommand.RESUPPLY,
                baseSpCost = SpiritCommand.RESUPPLY.spCost,
                category = SpiritCommand.RESUPPLY.category,
                targetType = SpiritCommand.RESUPPLY.targetType,
                cleansesDebuffs = true,
                restoresFullEnAndAmmo = true,
                effectStatTags = listOf("100% EN", "100% AMMO")
            )
        )

        private val baseEffectBySpirit: Map<SpiritCommand, PredefinedSpiritEffect> =
            PREDEFINED_SPIRIT_EFFECTS.associateBy { it.spirit }
    }

    private val _uiState = MutableStateFlow(SpiritCommandsViewModelUiState())
    val uiState: StateFlow<SpiritCommandsViewModelUiState> = _uiState.asStateFlow()

    private var activationSequence = 0L

    /**
     * Resolves a predefined [PredefinedSpiritEffect] for [spirit] scaled by [rank] (0..2).
     */
    fun getPredefinedEffect(spirit: SpiritCommand, rank: Int = 0): PredefinedSpiritEffect {
        val base = baseEffectBySpirit.getValue(spirit)
        val clampedRank = rank.coerceIn(0, PilotProgressionSystem.MAX_SPIRIT_RANK)
        val description = progressionSystem.getSummaryForRank(spirit, clampedRank)

        return when (spirit) {
            SpiritCommand.FOCUS -> when (clampedRank) {
                0 -> base.copy(description = description)
                1 -> base.copy(
                    hitBonusPercent = 40,
                    evadeBonusPercent = 40,
                    critBonusPercent = 10,
                    effectStatTags = listOf("+40% HIT", "+40% EVA", "+10% CRIT", "+20 INIT"),
                    description = description
                )
                else -> base.copy(
                    hitBonusPercent = 50,
                    evadeBonusPercent = 50,
                    critBonusPercent = 18,
                    effectStatTags = listOf("+50% HIT", "+50% EVA", "+18% CRIT", "+20 INIT"),
                    description = description
                )
            }
            SpiritCommand.VALOR -> when (clampedRank) {
                0 -> base.copy(description = description)
                1 -> base.copy(
                    attackMultiplier = 2.25,
                    effectStatTags = listOf("2.25x ATK"),
                    description = description
                )
                else -> base.copy(
                    attackMultiplier = 2.50,
                    hitBonusPercent = 10,
                    effectStatTags = listOf("2.50x ATK", "+10% HIT"),
                    description = description
                )
            }
            SpiritCommand.SOUL -> when (clampedRank) {
                0 -> base.copy(description = description)
                1 -> base.copy(
                    attackMultiplier = 2.75,
                    effectStatTags = listOf("2.75x ATK"),
                    description = description
                )
                else -> base.copy(
                    attackMultiplier = 3.00,
                    critBonusPercent = 15,
                    effectStatTags = listOf("3.00x ATK", "+15% CRIT"),
                    description = description
                )
            }
            SpiritCommand.STRIKE -> when (clampedRank) {
                0 -> base.copy(description = description)
                1 -> base.copy(
                    critBonusPercent = 15,
                    effectStatTags = listOf("100% HIT", "+15% CRIT", "+8 INIT"),
                    description = description
                )
                else -> base.copy(
                    critBonusPercent = 25,
                    effectStatTags = listOf("100% HIT", "+25% CRIT", "+8 INIT"),
                    description = description
                )
            }
            SpiritCommand.ALERT -> when (clampedRank) {
                0 -> base.copy(description = description)
                1 -> base.copy(
                    willDelta = 5,
                    effectStatTags = listOf("100% DODGE", "+5 WILL"),
                    description = description
                )
                else -> base.copy(
                    willDelta = 8,
                    effectStatTags = listOf("100% DODGE", "+8 WILL"),
                    description = description
                )
            }
            SpiritCommand.IRON_WALL -> when (clampedRank) {
                0 -> base.copy(description = description)
                1 -> base.copy(
                    incomingDamageMultiplier = 0.30,
                    effectStatTags = listOf("-70% DMG TAKEN"),
                    description = description
                )
                else -> base.copy(
                    incomingDamageMultiplier = 0.20,
                    effectStatTags = listOf("-80% DMG TAKEN", "+200 BARRIER"),
                    description = description
                )
            }
            SpiritCommand.ACCEL -> when (clampedRank) {
                0 -> base.copy(description = description)
                1 -> base.copy(
                    moveRangeBonus = 4,
                    initiativeBonus = 35,
                    effectStatTags = listOf("+4 MOVE", "+35 INIT"),
                    description = description
                )
                else -> base.copy(
                    moveRangeBonus = 5,
                    initiativeBonus = 45,
                    effectStatTags = listOf("+5 MOVE", "+45 INIT"),
                    description = description
                )
            }
            SpiritCommand.DRIVE -> when (clampedRank) {
                0 -> base.copy(description = description)
                1 -> base.copy(willDelta = 15, effectStatTags = listOf("+15 WILL"), description = description)
                else -> base.copy(willDelta = 22, effectStatTags = listOf("+22 WILL"), description = description)
            }
            SpiritCommand.ROUSE -> when (clampedRank) {
                0 -> base.copy(description = description)
                1 -> base.copy(willDelta = 18, effectStatTags = listOf("+18 WILL", "CLEANSE"), description = description)
                else -> base.copy(willDelta = 25, effectStatTags = listOf("+25 WILL", "+15 SP", "CLEANSE"), description = description)
            }
            SpiritCommand.ANALYZE -> when (clampedRank) {
                0 -> base.copy(description = description)
                1 -> base.copy(evadeBonusPercent = -22, enemyDefensePenaltyMultiplier = 1.35, effectStatTags = listOf("+35% TARGET DMG", "-22% EVA"), description = description)
                else -> base.copy(evadeBonusPercent = -30, enemyDefensePenaltyMultiplier = 1.45, effectStatTags = listOf("+45% TARGET DMG", "-30% EVA"), description = description)
            }
            SpiritCommand.EXHAUST -> when (clampedRank) {
                0 -> base.copy(description = description)
                1 -> base.copy(willDelta = -22, enRestoreAmount = -45, effectStatTags = listOf("-22 WILL", "-45 EN"), description = description)
                else -> base.copy(willDelta = -30, enRestoreAmount = -60, effectStatTags = listOf("-30 WILL", "-60 EN"), description = description)
            }
            SpiritCommand.CONFUSE -> when (clampedRank) {
                0 -> base.copy(description = description)
                1 -> base.copy(hitBonusPercent = -45, enemyMobilityPenalty = 22, effectStatTags = listOf("-45% HIT", "-22 MOB"), description = description)
                else -> base.copy(hitBonusPercent = -55, enemyMobilityPenalty = 30, effectStatTags = listOf("-55% HIT", "-30 MOB"), description = description)
            }
            SpiritCommand.TRUST -> when (clampedRank) {
                0 -> base.copy(description = description)
                1 -> base.copy(hpRestoreAmount = 4500, effectStatTags = listOf("+4,500 HP", "CLEANSE"), description = description)
                else -> base.copy(hpRestoreAmount = 6000, enRestoreAmount = 15, effectStatTags = listOf("+6,000 HP", "+15 EN"), description = description)
            }
            SpiritCommand.FRIENDSHIP -> when (clampedRank) {
                0 -> base.copy(description = description)
                1 -> base.copy(hpRestoreAmount = 3800, enRestoreAmount = 50, effectStatTags = listOf("+3,800 HP", "+50 EN"), description = description)
                else -> base.copy(hpRestoreAmount = 5200, enRestoreAmount = 70, effectStatTags = listOf("+5,200 HP", "+70 EN"), description = description)
            }
            SpiritCommand.RESUPPLY -> when (clampedRank) {
                0 -> base.copy(description = description)
                1 -> base.copy(hpRestoreAmount = 1500, effectStatTags = listOf("+1,500 HP", "100% EN/AMMO"), description = description)
                else -> base.copy(hpRestoreAmount = 3000, willDelta = 10, effectStatTags = listOf("+3,000 HP", "100% EN/AMMO", "+10 WILL"), description = description)
            }
        }
    }

    /**
     * Computes aggregated combat stat modifiers currently active on [unit] from its active Spirit
     * Commands and Debuffs using the predefined effect definitions.
     */
    fun computeActiveStatModifiers(unit: MechaUnit): ActiveSpiritStatModifiers {
        var attackMult = 1.0
        var incomingDmgMult = 1.0
        var hitBonus = 0
        var evadeBonus = 0
        var critBonus = 0
        var moveBonus = 0
        var initBonus = 0
        var guaranteedHit = false
        var guaranteedEvade = false
        val tags = mutableListOf<String>()

        // Apply active self-buffs using rank-scaled predefined definitions
        if (unit.activeSpirits.contains(SpiritCommand.SOUL)) {
            val eff = getPredefinedEffect(SpiritCommand.SOUL, unit.pilot.spiritRank(SpiritCommand.SOUL))
            attackMult = max(attackMult, eff.attackMultiplier)
            critBonus += eff.critBonusPercent
            tags.add("SOUL ×${"%.2f".format(eff.attackMultiplier)} ATK")
        } else if (unit.activeSpirits.contains(SpiritCommand.VALOR)) {
            val eff = getPredefinedEffect(SpiritCommand.VALOR, unit.pilot.spiritRank(SpiritCommand.VALOR))
            attackMult = max(attackMult, eff.attackMultiplier)
            hitBonus += eff.hitBonusPercent
            tags.add("VALOR ×${"%.2f".format(eff.attackMultiplier)} ATK")
        }

        if (unit.activeSpirits.contains(SpiritCommand.FOCUS)) {
            val eff = getPredefinedEffect(SpiritCommand.FOCUS, unit.pilot.spiritRank(SpiritCommand.FOCUS))
            hitBonus += eff.hitBonusPercent
            evadeBonus += eff.evadeBonusPercent
            critBonus += eff.critBonusPercent
            initBonus += eff.initiativeBonus
            tags.add("FOCUS +${eff.hitBonusPercent}% HIT/EVA")
        }

        if (unit.activeSpirits.contains(SpiritCommand.STRIKE)) {
            val eff = getPredefinedEffect(SpiritCommand.STRIKE, unit.pilot.spiritRank(SpiritCommand.STRIKE))
            guaranteedHit = true
            critBonus += eff.critBonusPercent
            initBonus += eff.initiativeBonus
            tags.add("STRIKE 100% HIT")
        }

        if (unit.activeSpirits.contains(SpiritCommand.ALERT)) {
            guaranteedEvade = true
            tags.add("ALERT 100% DODGE")
        }

        if (unit.activeSpirits.contains(SpiritCommand.ACCEL)) {
            val eff = getPredefinedEffect(SpiritCommand.ACCEL, unit.pilot.spiritRank(SpiritCommand.ACCEL))
            moveBonus += eff.moveRangeBonus
            initBonus += eff.initiativeBonus
            tags.add("ACCEL +${eff.moveRangeBonus} MOVE")
        }

        if (unit.activeSpirits.contains(SpiritCommand.IRON_WALL)) {
            val eff = getPredefinedEffect(SpiritCommand.IRON_WALL, unit.pilot.spiritRank(SpiritCommand.IRON_WALL))
            incomingDmgMult *= eff.incomingDamageMultiplier
            val pct = ((1.0 - eff.incomingDamageMultiplier) * 100).toInt()
            tags.add("WALL -${pct}% DMG")
        }

        // Factor in active enemy debuffs
        if (unit.activeDebuffs.contains(SpiritDebuff.ANALYZED)) {
            val eff = getPredefinedEffect(SpiritCommand.ANALYZE, unit.debuffRanks[SpiritDebuff.ANALYZED] ?: 0)
            incomingDmgMult *= eff.enemyDefensePenaltyMultiplier
            evadeBonus += eff.evadeBonusPercent
            tags.add("ANALYZED ${eff.evadeBonusPercent}% EVA")
        }
        if (unit.activeDebuffs.contains(SpiritDebuff.CONFUSED)) {
            val eff = getPredefinedEffect(SpiritCommand.CONFUSE, unit.debuffRanks[SpiritDebuff.CONFUSED] ?: 0)
            hitBonus += eff.hitBonusPercent
            tags.add("CONFUSED ${eff.hitBonusPercent}% HIT")
        }
        if (unit.activeDebuffs.contains(SpiritDebuff.DAUNTED)) {
            tags.add("DAUNTED")
        }

        return ActiveSpiritStatModifiers(
            unitId = unit.id,
            activeSpirits = unit.activeSpirits,
            activeDebuffs = unit.activeDebuffs,
            attackMultiplier = attackMult,
            incomingDamageMultiplier = incomingDmgMult,
            hitBonusPercent = hitBonus,
            evadeBonusPercent = evadeBonus,
            critBonusPercent = critBonus,
            moveRangeBonus = moveBonus,
            initiativeBonus = initBonus,
            guaranteedHit = guaranteedHit,
            guaranteedEvade = guaranteedEvade,
            activeEffectTags = tags
        )
    }

    /**
     * Synchronizes all living units' active Spirit Command stat modifiers into [uiState].
     */
    fun syncRoster(units: List<MechaUnit>) {
        val nextMap = units.filter { it.isAlive() }
            .associate { unit -> unit.id to computeActiveStatModifiers(unit) }
        _uiState.update { it.copy(modifiersByUnitId = nextMap) }
    }

    fun setCategoryFilter(category: SpiritCategory?) {
        _uiState.update { it.copy(selectedCategoryFilter = category) }
    }

    /**
     * Lists all Spirit Commands available to [unit] enriched with predefined effect data,
     * rank-adjusted SP cost, cast eligibility, and active status.
     */
    fun getAvailableEffectsForUnit(
        unit: MechaUnit,
        allUnits: List<MechaUnit>,
        categoryFilter: SpiritCategory? = _uiState.value.selectedCategoryFilter
    ): List<PilotSpiritAbilityStatus> {
        val filtered = if (categoryFilter == null) {
            unit.pilot.spirits
        } else {
            unit.pilot.spirits.filter { it.category == categoryFilter }
        }

        return filtered.map { spirit ->
            val rank = unit.pilot.spiritRank(spirit)
            val effect = getPredefinedEffect(spirit, rank)
            val spCost = progressionSystem.getEffectiveSpCost(unit.pilot, spirit)
            val alreadyActive = !spirit.isInstantAction && unit.activeSpirits.contains(spirit)
            val eligibleTargets = spiritCommandSystem.getEligibleTargets(unit, spirit, allUnits)
            val canCast = spiritCommandSystem.canCastSpirit(unit, spirit, allUnits)

            PilotSpiritAbilityStatus(
                spirit = spirit,
                effect = effect,
                effectiveSpCost = spCost,
                masteryRank = rank,
                isAlreadyActive = alreadyActive,
                canCast = canCast,
                eligibleTargetCount = eligibleTargets.size
            )
        }
    }

    /**
     * Activates [spirit] on behalf of [casterId] using the predefined effect rules, updates
     * reactive combat stat modifiers in [uiState], and records the activation event.
     */
    fun activateSpiritCommand(
        casterId: String,
        spirit: SpiritCommand,
        allUnits: List<MechaUnit>,
        targetUnitId: String? = null
    ): SpiritCastResult {
        val casterBefore = allUnits.find { it.id == casterId }
        val rank = casterBefore?.pilot?.spiritRank(spirit) ?: 0
        val predefinedEffect = getPredefinedEffect(spirit, rank)

        val result = spiritCommandSystem.castSpiritCommand(
            casterId = casterId,
            spirit = spirit,
            allUnits = allUnits,
            targetUnitId = targetUnitId
        )

        if (result.success) {
            activationSequence += 1L
            val record = SpiritActivationRecord(
                id = activationSequence,
                casterId = result.caster.id,
                casterName = result.caster.name,
                pilotName = result.caster.pilot.name,
                spirit = spirit,
                predefinedEffect = predefinedEffect,
                spSpent = result.spSpent,
                targetUnitIds = result.targetUnitIds,
                headline = result.headline,
                detail = result.detail
            )
            val updatedModifiers = result.updatedUnits.filter { it.isAlive() }
                .associate { u -> u.id to computeActiveStatModifiers(u) }

            _uiState.update { state ->
                state.copy(
                    modifiersByUnitId = updatedModifiers,
                    lastActivationBanner = record,
                    activationHistory = (listOf(record) + state.activationHistory).take(25)
                )
            }
        }

        return result
    }

    fun dismissActivationBanner() {
        _uiState.update { it.copy(lastActivationBanner = null) }
    }

    /**
     * Utility helper to retrieve only allied units with active Spirit combat stat modifiers.
     */
    fun getAlliedUnitsWithActiveModifiers(units: List<MechaUnit>): List<ActiveSpiritStatModifiers> =
        units.filter { it.isAlive() && it.faction == Faction.ALLY }
            .map { computeActiveStatModifiers(it) }
            .filter { it.hasCombatModifiers }
}
