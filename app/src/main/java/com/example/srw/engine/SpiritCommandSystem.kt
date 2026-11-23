package com.example.srw.engine

import com.example.srw.model.*
import kotlin.math.max
import kotlin.math.min

/**
 * Result of casting a Pilot Spirit Command during a tactical turn.
 */
data class SpiritCastResult(
    val success: Boolean,
    val caster: MechaUnit,
    val spirit: SpiritCommand,
    val targetUnitIds: List<String>,
    val updatedUnits: List<MechaUnit>,
    val spSpent: Int,
    val headline: String,
    val detail: String
)

/**
 * Dedicated Super Robot Wars Spirit Command System (精神コマンドシステム) that allows pilots
 * to spend SP during tactical combat turns to cast:
 * 1. Combat Buffs (STRIKE, ALERT/FLASH, FOCUS, ACCEL, VALOR, SOUL, IRON_WALL, DRIVE, ROUSE)
 * 2. Enemy Debuffs (ANALYZE, EXHAUST/DAUNT, CONFUSE/DISTURB)
 * 3. Healing & Resupply Abilities (TRUST, FRIENDSHIP/BONDS, RESUPPLY)
 */
class SpiritCommandSystem(
    private val progressionSystem: PilotProgressionSystem = PilotProgressionSystem()
) {

    /**
     * Returns all living units that are valid targets for [spirit] when cast by [caster].
     */
    fun getEligibleTargets(
        caster: MechaUnit,
        spirit: SpiritCommand,
        allUnits: List<MechaUnit>
    ): List<MechaUnit> {
        val living = allUnits.filter { it.isAlive() }
        return when (spirit.targetType) {
            SpiritTargetType.SELF -> listOf(caster)
            SpiritTargetType.SINGLE_ALLY, SpiritTargetType.ALL_ALLIES ->
                living.filter { it.faction == caster.faction }
            SpiritTargetType.SINGLE_ENEMY, SpiritTargetType.ALL_ENEMIES ->
                living.filter { it.faction != caster.faction }
        }
    }

    /**
     * Checks whether [caster] can currently cast [spirit] on [target] (if single-target).
     */
    fun canCastSpirit(
        caster: MechaUnit,
        spirit: SpiritCommand,
        allUnits: List<MechaUnit>,
        targetUnitId: String? = null
    ): Boolean {
        if (!caster.isAlive() || caster.hasActed) return false
        val effectiveCost = progressionSystem.getEffectiveSpCost(caster.pilot, spirit)
        if (caster.pilot.currentSp < effectiveCost) return false
        if (!spirit.isInstantAction && caster.activeSpirits.contains(spirit)) return false

        val eligibleTargets = getEligibleTargets(caster, spirit, allUnits)
        if (eligibleTargets.isEmpty()) return false

        if (spirit.targetType == SpiritTargetType.SINGLE_ALLY ||
            spirit.targetType == SpiritTargetType.SINGLE_ENEMY
        ) {
            val resolvedTarget = if (targetUnitId != null) {
                eligibleTargets.find { it.id == targetUnitId }
            } else {
                eligibleTargets.firstOrNull()
            } ?: return false

            if (spirit == SpiritCommand.ANALYZE &&
                resolvedTarget.activeDebuffs.contains(SpiritDebuff.ANALYZED)
            ) {
                return false
            }
        }

        return true
    }

    /**
     * Spends SP on [casterId] and applies the Buff, Debuff, or Healing/Resupply effect of [spirit].
     */
    fun castSpiritCommand(
        casterId: String,
        spirit: SpiritCommand,
        allUnits: List<MechaUnit>,
        targetUnitId: String? = null
    ): SpiritCastResult {
        val caster = allUnits.find { it.id == casterId && it.isAlive() }
            ?: return SpiritCastResult(
                success = false,
                caster = allUnits.first(),
                spirit = spirit,
                targetUnitIds = emptyList(),
                updatedUnits = allUnits,
                spSpent = 0,
                headline = "Spirit Command Failed",
                detail = "Caster unit unavailable."
            )

        val effectiveCost = progressionSystem.getEffectiveSpCost(caster.pilot, spirit)
        if (caster.pilot.currentSp < effectiveCost) {
            return SpiritCastResult(
                success = false,
                caster = caster,
                spirit = spirit,
                targetUnitIds = emptyList(),
                updatedUnits = allUnits,
                spSpent = 0,
                headline = "Insufficient SP",
                detail = "${caster.pilot.name} needs $effectiveCost SP (Current: ${caster.pilot.currentSp} SP)."
            )
        }

        if (!spirit.isInstantAction && caster.activeSpirits.contains(spirit)) {
            return SpiritCastResult(
                success = false,
                caster = caster,
                spirit = spirit,
                targetUnitIds = emptyList(),
                updatedUnits = allUnits,
                spSpent = 0,
                headline = "${spirit.codeName} Already Active",
                detail = "${caster.name} already has ${spirit.codeName} (${spirit.jpKanji}) active."
            )
        }

        val eligibleTargets = getEligibleTargets(caster, spirit, allUnits)
        val resolvedTarget: MechaUnit? = when (spirit.targetType) {
            SpiritTargetType.SELF -> caster
            SpiritTargetType.SINGLE_ALLY -> {
                eligibleTargets.find { it.id == targetUnitId }
                    ?: eligibleTargets.minByOrNull { it.currentHp.toFloat() / it.maxHp.toFloat() }
                    ?: caster
            }
            SpiritTargetType.SINGLE_ENEMY -> {
                eligibleTargets.find { it.id == targetUnitId }
                    ?: eligibleTargets.maxByOrNull { it.currentHp }
            }
            SpiritTargetType.ALL_ALLIES, SpiritTargetType.ALL_ENEMIES -> null
        }

        if ((spirit.targetType == SpiritTargetType.SINGLE_ALLY ||
                spirit.targetType == SpiritTargetType.SINGLE_ENEMY) && resolvedTarget == null
        ) {
            return SpiritCastResult(
                success = false,
                caster = caster,
                spirit = spirit,
                targetUnitIds = emptyList(),
                updatedUnits = allUnits,
                spSpent = 0,
                headline = "No Valid Target",
                detail = "No eligible target found for ${spirit.codeName}."
            )
        }

        val spiritRank = caster.pilot.spiritRank(spirit)
        val spAfterCast = (caster.pilot.currentSp - effectiveCost).coerceAtLeast(0)
        val affectedIds = mutableListOf<String>()
        var detailSummary = progressionSystem.getEffectiveSpiritSummary(caster.pilot, spirit)

        val updatedUnits = allUnits.map { unit ->
            // Deduct SP from caster first
            val baseUnit = if (unit.id == caster.id) {
                unit.copy(pilot = unit.pilot.copy(currentSp = spAfterCast))
            } else {
                unit
            }

            if (!baseUnit.isAlive()) return@map baseUnit

            when (spirit) {
                // =============================================================
                // 1. SELF-BUFF SPIRIT COMMANDS (強化)
                // =============================================================
                SpiritCommand.STRIKE,
                SpiritCommand.ALERT,
                SpiritCommand.FOCUS,
                SpiritCommand.ACCEL,
                SpiritCommand.VALOR,
                SpiritCommand.SOUL,
                SpiritCommand.IRON_WALL -> {
                    if (baseUnit.id == caster.id) {
                        affectedIds.add(baseUnit.id)
                        val rankTag = if (spiritRank > 0) " [${progressionSystem.getRankBadgeLabel(spiritRank)}]" else ""
                        detailSummary = "${caster.name} gained [${spirit.codeName}$rankTag / ${spirit.jpKanji}]: ${progressionSystem.getEffectiveSpiritSummary(caster.pilot, spirit)}"
                        val willGain = if (spirit == SpiritCommand.ALERT && spiritRank > 0) {
                            if (spiritRank >= 2) 8 else 5
                        } else 0
                        baseUnit.copy(
                            activeSpirits = baseUnit.activeSpirits + spirit,
                            pilot = baseUnit.pilot.copy(will = min(170, baseUnit.pilot.will + willGain))
                        )
                    } else baseUnit
                }

                SpiritCommand.DRIVE -> {
                    if (baseUnit.id == caster.id) {
                        affectedIds.add(baseUnit.id)
                        val willBoost = when (spiritRank) {
                            1 -> 15
                            2 -> 22
                            else -> 10
                        }
                        val oldWill = baseUnit.pilot.will
                        val newWill = min(170, oldWill + willBoost)
                        detailSummary = "${caster.pilot.name} Will surged $oldWill → $newWill (+$willBoost Will)!"
                        baseUnit.copy(pilot = baseUnit.pilot.copy(will = newWill))
                    } else baseUnit
                }

                SpiritCommand.ROUSE -> {
                    if (resolvedTarget != null && baseUnit.id == resolvedTarget.id) {
                        affectedIds.add(baseUnit.id)
                        val willBoost = when (spiritRank) {
                            1 -> 18
                            2 -> 25
                            else -> 12
                        }
                        val spBoost = if (spiritRank >= 2) 15 else 0
                        val oldWill = baseUnit.pilot.will
                        val newWill = min(170, oldWill + willBoost)
                        val newSp = min(baseUnit.effectiveMaxSp, baseUnit.pilot.currentSp + spBoost)
                        detailSummary = "${baseUnit.pilot.name} (${baseUnit.name}) Will surged $oldWill → $newWill (+$willBoost Will)" +
                            (if (spBoost > 0) " & +$spBoost SP" else "") + " & debuffs cleansed!"
                        baseUnit.copy(
                            activeDebuffs = emptySet(),
                            debuffRanks = emptyMap(),
                            pilot = baseUnit.pilot.copy(will = newWill, currentSp = newSp)
                        )
                    } else baseUnit
                }

                // =============================================================
                // 2. ENEMY DEBUFF SPIRIT COMMANDS (妨害)
                // =============================================================
                SpiritCommand.ANALYZE -> {
                    if (resolvedTarget != null && baseUnit.id == resolvedTarget.id) {
                        affectedIds.add(baseUnit.id)
                        val dmgBonusPct = when (spiritRank) {
                            1 -> 35
                            2 -> 45
                            else -> 25
                        }
                        val evaPenalty = when (spiritRank) {
                            1 -> 22
                            2 -> 30
                            else -> 15
                        }
                        detailSummary = "Target ${baseUnit.name} analyzed! Takes +$dmgBonusPct% damage & suffers -$evaPenalty% evasion for 1 turn."
                        val nextRanks = baseUnit.debuffRanks.toMutableMap().apply {
                            this[SpiritDebuff.ANALYZED] = max(this[SpiritDebuff.ANALYZED] ?: 0, spiritRank)
                        }
                        baseUnit.copy(
                            activeDebuffs = baseUnit.activeDebuffs + SpiritDebuff.ANALYZED,
                            debuffRanks = nextRanks
                        )
                    } else baseUnit
                }

                SpiritCommand.EXHAUST -> {
                    if (resolvedTarget != null && baseUnit.id == resolvedTarget.id) {
                        affectedIds.add(baseUnit.id)
                        val willDrain = when (spiritRank) {
                            1 -> 22
                            2 -> 30
                            else -> 15
                        }
                        val enDrain = when (spiritRank) {
                            1 -> 45
                            2 -> 60
                            else -> 30
                        }
                        val oldWill = baseUnit.pilot.will
                        val newWill = max(50, oldWill - willDrain)
                        val oldEn = baseUnit.currentEn
                        val newEn = max(0, oldEn - enDrain)
                        detailSummary = "Target ${baseUnit.name} daunted! Will $oldWill → $newWill (-$willDrain) & EN $oldEn → $newEn (-$enDrain)."
                        baseUnit.copy(
                            currentEn = newEn,
                            activeDebuffs = baseUnit.activeDebuffs + SpiritDebuff.DAUNTED,
                            pilot = baseUnit.pilot.copy(will = newWill)
                        )
                    } else baseUnit
                }

                SpiritCommand.CONFUSE -> {
                    if (baseUnit.faction != caster.faction) {
                        affectedIds.add(baseUnit.id)
                        val nextRanks = baseUnit.debuffRanks.toMutableMap().apply {
                            this[SpiritDebuff.CONFUSED] = max(this[SpiritDebuff.CONFUSED] ?: 0, spiritRank)
                        }
                        baseUnit.copy(
                            activeDebuffs = baseUnit.activeDebuffs + SpiritDebuff.CONFUSED,
                            debuffRanks = nextRanks
                        )
                    } else baseUnit
                }

                // =============================================================
                // 3. HEALING & RESUPPLY SPIRIT COMMANDS (回復)
                // =============================================================
                SpiritCommand.TRUST -> {
                    if (resolvedTarget != null && baseUnit.id == resolvedTarget.id) {
                        affectedIds.add(baseUnit.id)
                        val healAmount = when (spiritRank) {
                            1 -> 4500
                            2 -> 6000
                            else -> 3000
                        }
                        val enGain = if (spiritRank >= 2) 15 else 0
                        val oldHp = baseUnit.currentHp
                        val newHp = min(baseUnit.maxHp, oldHp + healAmount)
                        val newEn = min(baseUnit.maxEn, baseUnit.currentEn + enGain)
                        detailSummary = "Repaired ${baseUnit.name}: HP $oldHp → $newHp (+${newHp - oldHp} HP)" +
                            (if (enGain > 0) ", +$enGain EN" else "") + " & cleansed debuffs."
                        baseUnit.copy(
                            currentHp = newHp,
                            currentEn = newEn,
                            activeDebuffs = emptySet(),
                            debuffRanks = emptyMap()
                        )
                    } else baseUnit
                }

                SpiritCommand.FRIENDSHIP -> {
                    if (baseUnit.faction == caster.faction) {
                        affectedIds.add(baseUnit.id)
                        val hpHeal = when (spiritRank) {
                            1 -> 3800
                            2 -> 5200
                            else -> 2500
                        }
                        val enHeal = when (spiritRank) {
                            1 -> 50
                            2 -> 70
                            else -> 35
                        }
                        val newHp = min(baseUnit.maxHp, baseUnit.currentHp + hpHeal)
                        val newEn = min(baseUnit.maxEn, baseUnit.currentEn + enHeal)
                        baseUnit.copy(
                            currentHp = newHp,
                            currentEn = newEn,
                            activeDebuffs = emptySet(),
                            debuffRanks = emptyMap()
                        )
                    } else baseUnit
                }

                SpiritCommand.RESUPPLY -> {
                    if (resolvedTarget != null && baseUnit.id == resolvedTarget.id) {
                        affectedIds.add(baseUnit.id)
                        val hpBonus = when (spiritRank) {
                            1 -> 1500
                            2 -> 3000
                            else -> 0
                        }
                        val willBonus = if (spiritRank >= 2) 10 else 0
                        val refilledWeapons = baseUnit.weapons.map { w ->
                            if (w.maxAmmo > 0) w.copy(currentAmmo = w.maxAmmo) else w
                        }
                        val newHp = min(baseUnit.maxHp, baseUnit.currentHp + hpBonus)
                        val newWill = min(170, baseUnit.pilot.will + willBonus)
                        detailSummary = "Resupplied ${baseUnit.name}: EN restored to ${baseUnit.maxEn}, ammo refilled 100%" +
                            (if (hpBonus > 0) ", +$hpBonus HP" else "") +
                            (if (willBonus > 0) ", +$willBonus Will" else "") + "!"
                        baseUnit.copy(
                            currentHp = newHp,
                            currentEn = baseUnit.maxEn,
                            weapons = refilledWeapons,
                            activeDebuffs = emptySet(),
                            debuffRanks = emptyMap(),
                            pilot = baseUnit.pilot.copy(will = newWill)
                        )
                    } else baseUnit
                }
            }
        }

        if (spirit == SpiritCommand.CONFUSE) {
            val hitPen = when (spiritRank) {
                1 -> 45
                2 -> 55
                else -> 35
            }
            val mobPen = 15 + spiritRank * 7
            detailSummary = "Inflicted CONFUSED (かく乱) on ${affectedIds.size} enemy unit(s): -$hitPen% Hit Rate & -$mobPen Mobility for 1 turn!"
        } else if (spirit == SpiritCommand.FRIENDSHIP) {
            val hpHeal = when (spiritRank) {
                1 -> 3800
                2 -> 5200
                else -> 2500
            }
            val enHeal = when (spiritRank) {
                1 -> 50
                2 -> 70
                else -> 35
            }
            detailSummary = "Squad-wide BONDS (友情) restored +$hpHeal HP, +$enHeal EN & cleansed debuffs across ${affectedIds.size} allied units!"
        }

        val updatedCaster = updatedUnits.first { it.id == caster.id }
        val rankSuffix = if (spiritRank > 0) " ${progressionSystem.getRankBadgeLabel(spiritRank)}" else ""
        val headline = "${caster.pilot.name} cast ${spirit.category.displayName} [${spirit.codeName}$rankSuffix / ${spirit.jpKanji}] (-$effectiveCost SP)!"

        return SpiritCastResult(
            success = true,
            caster = updatedCaster,
            spirit = spirit,
            targetUnitIds = affectedIds,
            updatedUnits = updatedUnits,
            spSpent = effectiveCost,
            headline = headline,
            detail = detailSummary
        )
    }
}
