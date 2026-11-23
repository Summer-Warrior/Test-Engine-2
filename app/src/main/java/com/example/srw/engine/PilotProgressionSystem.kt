package com.example.srw.engine

import com.example.srw.model.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Outcome returned when unlocking a permanent passive ability or upgrading a Spirit Command using XP.
 */
data class PilotProgressionOutcome(
    val success: Boolean,
    val updatedUnit: MechaUnit,
    val xpSpent: Int,
    val headline: String,
    val detail: String
)

/**
 * XP & Kill reward summary generated after each tactical combat exchange.
 */
data class PilotXpGrant(
    val unitId: String,
    val pilotName: String,
    val xpAwarded: Int,
    val killsAdded: Int,
    val newTotalXp: Int,
    val reason: String
)

data class PilotXpUpdateResult(
    val updatedUnits: List<MechaUnit>,
    val grants: List<PilotXpGrant>,
    val summaryText: String
)

/**
 * SRW Pilot Progression System (パイロット養成):
 * - Earns Pilot XP from combat hits, criticals, counterattacks, and kills.
 * - Unlocks permanent Passive Abilities ([PilotPassiveAbility]) using earned XP.
 * - Upgrades existing Spirit Command effects (Rank 0 -> Rank 1 [+] -> Rank 2 [MAX]) using XP.
 */
class PilotProgressionSystem {

    companion object {
        const val MAX_SPIRIT_RANK = 2
        const val SPIRIT_RANK_1_XP_COST = 100
        const val SPIRIT_RANK_2_XP_COST = 180
    }

    fun getSpiritUpgradeCost(currentRank: Int): Int = when (currentRank) {
        0 -> SPIRIT_RANK_1_XP_COST
        1 -> SPIRIT_RANK_2_XP_COST
        else -> 0
    }

    fun getRankBadgeLabel(rank: Int): String = when (rank.coerceIn(0, MAX_SPIRIT_RANK)) {
        0 -> "BASE"
        1 -> "LV.2 +"
        else -> "MAX ★"
    }

    /**
     * Effective SP cost after applying Spirit mastery rank discounts and SP_CONSERVATION (-20%).
     */
    fun getEffectiveSpCost(pilot: Pilot, spirit: SpiritCommand): Int {
        val rank = pilot.spiritRank(spirit)
        val rankDiscount = when {
            rank >= 2 && (spirit == SpiritCommand.FOCUS || spirit == SpiritCommand.DRIVE) -> 5
            rank >= 2 && (spirit == SpiritCommand.STRIKE || spirit == SpiritCommand.ALERT) -> 4
            else -> 0
        }
        val baseAfterRank = max(5, spirit.spCost - rankDiscount)
        return if (pilot.hasPassive(PilotPassiveAbility.SP_CONSERVATION)) {
            max(5, (baseAfterRank * 0.80).roundToInt())
        } else {
            baseAfterRank
        }
    }

    /**
     * Human-readable summary of [spirit] scaled to [pilot]'s current Spirit Mastery rank.
     */
    fun getEffectiveSpiritSummary(pilot: Pilot, spirit: SpiritCommand): String =
        getSummaryForRank(spirit, pilot.spiritRank(spirit))

    fun getSummaryForRank(spirit: SpiritCommand, rank: Int): String = when (spirit) {
        SpiritCommand.FOCUS -> when (rank) {
            0 -> spirit.summary
            1 -> "+40% Hit & Evade, +10% Crit for 1 turn"
            else -> "+50% Hit & Evade, +18% Crit for 1 turn (-5 SP)"
        }
        SpiritCommand.VALOR -> when (rank) {
            0 -> spirit.summary
            1 -> "2.25x Damage on next attack"
            else -> "2.50x Damage & +10% Hit on next attack"
        }
        SpiritCommand.SOUL -> when (rank) {
            0 -> spirit.summary
            1 -> "2.75x Damage on next attack"
            else -> "3.00x Damage & +15% Crit on next attack"
        }
        SpiritCommand.STRIKE -> when (rank) {
            0 -> spirit.summary
            1 -> "100% Accuracy & +15% Crit for 1 turn"
            else -> "100% Accuracy & +25% Crit for 1 turn (-4 SP)"
        }
        SpiritCommand.ALERT -> when (rank) {
            0 -> spirit.summary
            1 -> "100% Dodge vs next attack & +5 Will"
            else -> "100% Dodge vs next attack, +8 Will (-4 SP)"
        }
        SpiritCommand.IRON_WALL -> when (rank) {
            0 -> spirit.summary
            1 -> "-70% Damage taken for 1 turn"
            else -> "-80% Damage taken & +200 Barrier for 1 turn"
        }
        SpiritCommand.ACCEL -> when (rank) {
            0 -> spirit.summary
            1 -> "+4 Move Range & +35 Initiative this turn"
            else -> "+5 Move Range & +45 Initiative this turn"
        }
        SpiritCommand.DRIVE -> when (rank) {
            0 -> spirit.summary
            1 -> "+15 Pilot Will immediately"
            else -> "+22 Pilot Will immediately (-5 SP)"
        }
        SpiritCommand.ROUSE -> when (rank) {
            0 -> spirit.summary
            1 -> "Target Ally: +18 Will & cleanse debuffs"
            else -> "Target Ally: +25 Will, +15 SP & cleanse debuffs"
        }
        SpiritCommand.ANALYZE -> when (rank) {
            0 -> spirit.summary
            1 -> "Target Enemy: +35% Damage taken & -22% Evade"
            else -> "Target Enemy: +45% Damage taken & -30% Evade"
        }
        SpiritCommand.EXHAUST -> when (rank) {
            0 -> spirit.summary
            1 -> "Target Enemy: -22 Will & -45 EN drained"
            else -> "Target Enemy: -30 Will & -60 EN drained"
        }
        SpiritCommand.CONFUSE -> when (rank) {
            0 -> spirit.summary
            1 -> "All Enemies: -45% Hit & -22 Mobility for 1 turn"
            else -> "All Enemies: -55% Hit & -30 Mobility for 1 turn"
        }
        SpiritCommand.TRUST -> when (rank) {
            0 -> spirit.summary
            1 -> "Target Ally: Restore 4,500 HP & cleanse debuffs"
            else -> "Target Ally: Restore 6,000 HP, +15 EN & cleanse"
        }
        SpiritCommand.FRIENDSHIP -> when (rank) {
            0 -> spirit.summary
            1 -> "All Allies: Restore 3,800 HP, +50 EN & cleanse"
            else -> "All Allies: Restore 5,200 HP, +70 EN & cleanse"
        }
        SpiritCommand.RESUPPLY -> when (rank) {
            0 -> spirit.summary
            1 -> "Target Ally: +1,500 HP, 100% EN & 100% Ammo"
            else -> "Target Ally: +3,000 HP, 100% EN/Ammo & +10 Will"
        }
    }

    // =========================================================================
    // SPIRIT & PASSIVE COMBAT MODIFIER RESOLVERS
    // =========================================================================

    fun getSpiritAttackMultiplier(attacker: MechaUnit): Double = when {
        attacker.activeSpirits.contains(SpiritCommand.SOUL) -> {
            when (attacker.pilot.spiritRank(SpiritCommand.SOUL)) {
                0 -> 2.5
                1 -> 2.75
                else -> 3.0
            }
        }
        attacker.activeSpirits.contains(SpiritCommand.VALOR) -> {
            when (attacker.pilot.spiritRank(SpiritCommand.VALOR)) {
                0 -> 2.0
                1 -> 2.25
                else -> 2.5
            }
        }
        else -> 1.0
    }

    fun getFocusHitEvadeBonus(unit: MechaUnit): Int {
        if (!unit.activeSpirits.contains(SpiritCommand.FOCUS)) return 0
        return when (unit.pilot.spiritRank(SpiritCommand.FOCUS)) {
            0 -> 30
            1 -> 40
            else -> 50
        }
    }

    fun getSpiritHitBonus(attacker: MechaUnit): Int {
        var bonus = getFocusHitEvadeBonus(attacker)
        if (attacker.activeSpirits.contains(SpiritCommand.VALOR) &&
            attacker.pilot.spiritRank(SpiritCommand.VALOR) >= 2
        ) {
            bonus += 10
        }
        return bonus
    }

    fun getSpiritCritBonus(attacker: MechaUnit): Int {
        var bonus = 0
        if (attacker.activeSpirits.contains(SpiritCommand.FOCUS)) {
            bonus += when (attacker.pilot.spiritRank(SpiritCommand.FOCUS)) {
                1 -> 10
                2 -> 18
                else -> 0
            }
        }
        if (attacker.activeSpirits.contains(SpiritCommand.STRIKE)) {
            bonus += when (attacker.pilot.spiritRank(SpiritCommand.STRIKE)) {
                1 -> 15
                2 -> 25
                else -> 0
            }
        }
        if (attacker.activeSpirits.contains(SpiritCommand.SOUL) &&
            attacker.pilot.spiritRank(SpiritCommand.SOUL) >= 2
        ) {
            bonus += 15
        }
        return bonus
    }

    fun getIronWallDamageMultiplier(defender: MechaUnit): Double {
        if (!defender.activeSpirits.contains(SpiritCommand.IRON_WALL)) return 1.0
        return when (defender.pilot.spiritRank(SpiritCommand.IRON_WALL)) {
            0 -> 0.40
            1 -> 0.30
            else -> 0.20
        }
    }

    fun getAnalyzeDamageMultiplier(defender: MechaUnit): Double {
        if (!defender.activeDebuffs.contains(SpiritDebuff.ANALYZED)) return 1.0
        val rank = defender.debuffRanks[SpiritDebuff.ANALYZED] ?: 0
        return when (rank) {
            0 -> 1.25
            1 -> 1.35
            else -> 1.45
        }
    }

    fun getAnalyzeEvadePenalty(defender: MechaUnit): Int {
        if (!defender.activeDebuffs.contains(SpiritDebuff.ANALYZED)) return 0
        val rank = defender.debuffRanks[SpiritDebuff.ANALYZED] ?: 0
        return when (rank) {
            0 -> 15
            1 -> 22
            else -> 30
        }
    }

    fun getConfuseHitPenalty(attacker: MechaUnit): Int {
        if (!attacker.activeDebuffs.contains(SpiritDebuff.CONFUSED)) return 0
        val rank = attacker.debuffRanks[SpiritDebuff.CONFUSED] ?: 0
        return when (rank) {
            0 -> 35
            1 -> 45
            else -> 55
        }
    }

    fun getAccelInitiativeBonus(unit: MechaUnit): Int {
        if (!unit.activeSpirits.contains(SpiritCommand.ACCEL)) return 0
        return when (unit.pilot.spiritRank(SpiritCommand.ACCEL)) {
            0 -> 25
            1 -> 35
            else -> 45
        }
    }

    fun getPassiveAttackMultiplier(attacker: MechaUnit, isCounterattack: Boolean): Double {
        var mult = 1.0
        if (attacker.pilot.hasPassive(PilotPassiveAbility.ATTACKER_INSTINCT) &&
            attacker.pilot.will >= 115
        ) {
            mult *= 1.12
        }
        if (isCounterattack && attacker.pilot.hasPassive(PilotPassiveAbility.COUNTER_MASTER)) {
            mult *= 1.15
        }
        return mult
    }

    fun getPassiveHitBonus(unit: MechaUnit): Int =
        if (unit.pilot.hasPassive(PilotPassiveAbility.PRE_EMPTIVE_ACE)) 10 else 0

    fun getPassiveEvadeBonus(unit: MechaUnit, defenderStance: DefendStance): Int {
        var bonus = 0
        if (unit.pilot.hasPassive(PilotPassiveAbility.PRE_EMPTIVE_ACE)) bonus += 10
        if (unit.isPotentialActive) bonus += 15
        if (defenderStance == DefendStance.COUNTER &&
            unit.pilot.hasPassive(PilotPassiveAbility.COUNTER_MASTER)
        ) {
            bonus += 12
        }
        return bonus
    }

    fun getPassiveCritBonus(unit: MechaUnit): Int {
        var bonus = 0
        if (unit.pilot.hasPassive(PilotPassiveAbility.PRE_EMPTIVE_ACE)) bonus += 10
        if (unit.isPotentialActive) bonus += 20
        return bonus
    }

    fun getPassiveInitiativeBonus(unit: MechaUnit): Int =
        if (unit.pilot.hasPassive(PilotPassiveAbility.PRE_EMPTIVE_ACE)) 15 else 0

    fun getSpRegenBonus(unit: MechaUnit): Int =
        if (unit.pilot.hasPassive(PilotPassiveAbility.SP_CONSERVATION)) 4 else 0

    // =========================================================================
    // XP SPENDING OPERATIONS
    // =========================================================================

    fun unlockPassiveAbility(unit: MechaUnit, ability: PilotPassiveAbility): PilotProgressionOutcome {
        if (unit.pilot.hasPassive(ability)) {
            return PilotProgressionOutcome(
                success = false,
                updatedUnit = unit,
                xpSpent = 0,
                headline = "Already Unlocked",
                detail = "${unit.pilot.name} already mastered ${ability.title}."
            )
        }
        if (unit.pilot.xp < ability.xpCost) {
            return PilotProgressionOutcome(
                success = false,
                updatedUnit = unit,
                xpSpent = 0,
                headline = "Insufficient Pilot XP",
                detail = "Needs ${ability.xpCost} XP (Available: ${unit.pilot.xp} XP)."
            )
        }

        val updatedPilot = unit.pilot.copy(
            xp = unit.pilot.xp - ability.xpCost,
            passiveAbilities = unit.pilot.passiveAbilities + ability,
            will = if (ability == PilotPassiveAbility.MORALE_SURGE) min(170, unit.pilot.will + 8) else unit.pilot.will,
            currentSp = if (ability == PilotPassiveAbility.SP_CONSERVATION) unit.pilot.currentSp + 25 else unit.pilot.currentSp
        )
        val updatedUnit = unit.copy(pilot = updatedPilot)

        return PilotProgressionOutcome(
            success = true,
            updatedUnit = updatedUnit,
            xpSpent = ability.xpCost,
            headline = "${unit.pilot.name} unlocked Passive [${ability.title} / ${ability.jpName}]!",
            detail = "Spent ${ability.xpCost} XP. ${ability.summary}"
        )
    }

    fun upgradeSpiritCommand(unit: MechaUnit, spirit: SpiritCommand): PilotProgressionOutcome {
        if (!unit.pilot.spirits.contains(spirit)) {
            return PilotProgressionOutcome(
                success = false,
                updatedUnit = unit,
                xpSpent = 0,
                headline = "Spirit Not Learned",
                detail = "${unit.pilot.name} does not possess ${spirit.codeName}."
            )
        }

        val currentRank = unit.pilot.spiritRank(spirit)
        if (currentRank >= MAX_SPIRIT_RANK) {
            return PilotProgressionOutcome(
                success = false,
                updatedUnit = unit,
                xpSpent = 0,
                headline = "Spirit Already Mastered",
                detail = "${spirit.codeName} is already at MAX Mastery."
            )
        }

        val cost = getSpiritUpgradeCost(currentRank)
        if (unit.pilot.xp < cost) {
            return PilotProgressionOutcome(
                success = false,
                updatedUnit = unit,
                xpSpent = 0,
                headline = "Insufficient Pilot XP",
                detail = "Needs $cost XP to upgrade ${spirit.codeName} (Available: ${unit.pilot.xp} XP)."
            )
        }

        val nextRank = currentRank + 1
        val updatedUpgrades = unit.pilot.spiritUpgrades.toMutableMap().apply {
            this[spirit] = nextRank
        }
        val updatedUnit = unit.copy(
            pilot = unit.pilot.copy(
                xp = unit.pilot.xp - cost,
                spiritUpgrades = updatedUpgrades
            )
        )
        val upgradedSummary = getEffectiveSpiritSummary(updatedUnit.pilot, spirit)

        return PilotProgressionOutcome(
            success = true,
            updatedUnit = updatedUnit,
            xpSpent = cost,
            headline = "${unit.pilot.name} upgraded [${spirit.codeName}] to ${getRankBadgeLabel(nextRank)}!",
            detail = "Spent $cost XP. New Effect: $upgradedSummary"
        )
    }

    // =========================================================================
    // BATTLE XP REWARDS
    // =========================================================================

    fun processCombatXp(
        sequence: BattleCutsceneSequence,
        currentUnits: List<MechaUnit>
    ): PilotXpUpdateResult {
        val xpRewards = mutableMapOf<String, Pair<Int, Int>>() // unitId -> (xp, killsAdded)
        val reasons = mutableMapOf<String, String>()

        val attackerSurvived = sequence.attackerHpAfterCounter > 0
        val defenderSurvived = sequence.defenderHpAfterPrimary > 0

        if (sequence.attacker.faction == Faction.ALLY && attackerSurvived) {
            var xp = when (sequence.primaryOutcome) {
                StrikeOutcome.CRITICAL -> 55
                StrikeOutcome.HIT -> 35
                StrikeOutcome.MISS -> 15
            }
            var killsAdded = 0
            if (!defenderSurvived) {
                val isBoss = sequence.defender.id.contains("boss") || sequence.defender.maxHp >= 12000
                xp += if (isBoss) 220 else 90
                killsAdded = 1
            }
            xpRewards[sequence.attacker.id] = xp to killsAdded
            reasons[sequence.attacker.id] = if (killsAdded > 0) {
                "+$xp XP (Shot down ${sequence.defender.name})"
            } else {
                "+$xp XP (${sequence.primaryOutcome.name} vs ${sequence.defender.name})"
            }
        }

        if (sequence.defender.faction == Faction.ALLY && defenderSurvived) {
            var xp = 25
            var killsAdded = 0
            if (sequence.counterExecuted) {
                xp += when (sequence.counterOutcome) {
                    StrikeOutcome.CRITICAL -> 45
                    StrikeOutcome.HIT -> 30
                    StrikeOutcome.MISS -> 10
                }
                if (!attackerSurvived) {
                    xp += 100
                    killsAdded = 1
                }
            }
            xpRewards[sequence.defender.id] = xp to killsAdded
            reasons[sequence.defender.id] = "+$xp XP (Defense/Counter)"
        }

        val grants = mutableListOf<PilotXpGrant>()
        val updatedUnits = currentUnits.map { unit ->
            val reward = xpRewards[unit.id]
            if (reward != null && unit.isAlive()) {
                val (xpGain, killsGain) = reward
                val updatedPilot = unit.pilot.copy(
                    xp = unit.pilot.xp + xpGain,
                    totalXpEarned = unit.pilot.totalXpEarned + xpGain,
                    kills = unit.pilot.kills + killsGain
                )
                grants.add(
                    PilotXpGrant(
                        unitId = unit.id,
                        pilotName = unit.pilot.name,
                        xpAwarded = xpGain,
                        killsAdded = killsGain,
                        newTotalXp = updatedPilot.xp,
                        reason = reasons[unit.id] ?: "+$xpGain XP"
                    )
                )
                unit.copy(pilot = updatedPilot)
            } else {
                unit
            }
        }

        val summary = grants.joinToString(" • ") { "${it.pilotName} ${it.reason}" }
        return PilotXpUpdateResult(
            updatedUnits = updatedUnits,
            grants = grants,
            summaryText = summary
        )
    }
}
