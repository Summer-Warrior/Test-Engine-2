package com.example.srw.engine

import androidx.compose.ui.graphics.Color
import com.example.srw.model.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Discrete SRW Pilot Morale (Will / 気力) threshold tiers.
 * Governs attack output multipliers, armor resistance, accuracy/evasion modifiers,
 * critical strike bonuses, and access to Will-gated special weapons.
 */
enum class MoraleTier(
    val minWill: Int,
    val maxWill: Int,
    val displayName: String,
    val jpKanji: String,
    val badgeColor: Color,
    val attackOutputMultiplier: Double,
    val defenseMultiplier: Double,
    val hitEvadeBonusPercent: Int,
    val critBonusPercent: Int,
    val mobilityModifier: Int,
    val summary: String
) {
    WAVERING(
        minWill = 50,
        maxWill = 84,
        displayName = "WAVERING",
        jpKanji = "動揺",
        badgeColor = Color(0xFFFF5252),
        attackOutputMultiplier = 0.82,
        defenseMultiplier = 0.85,
        hitEvadeBonusPercent = -10,
        critBonusPercent = -10,
        mobilityModifier = -12,
        summary = "-18% Attack Output, -15% Defense, -10% Hit/Evade, -12 Mobility. Special weapons locked."
    ),
    STEADY(
        minWill = 85,
        maxWill = 99,
        displayName = "STEADY",
        jpKanji = "平常",
        badgeColor = Color(0xFF90A4AE),
        attackOutputMultiplier = 0.94,
        defenseMultiplier = 0.95,
        hitEvadeBonusPercent = -2,
        critBonusPercent = 0,
        mobilityModifier = -4,
        summary = "-6% Attack Output, -5% Defense, -4 Mobility. Standard weapons operational."
    ),
    FOCUSED(
        minWill = 100,
        maxWill = 109,
        displayName = "FOCUSED",
        jpKanji = "気合",
        badgeColor = Color(0xFF00E5FF),
        attackOutputMultiplier = 1.00,
        defenseMultiplier = 1.00,
        hitEvadeBonusPercent = 4,
        critBonusPercent = 4,
        mobilityModifier = 0,
        summary = "100% Baseline Output, +4% Hit/Evade/Crit. Unlocks 105-Will Special Weapons."
    ),
    HIGH_SPIRIT(
        minWill = 110,
        maxWill = 129,
        displayName = "HIGH WILL",
        jpKanji = "高揚",
        badgeColor = Color(0xFF00E676),
        attackOutputMultiplier = 1.10,
        defenseMultiplier = 1.08,
        hitEvadeBonusPercent = 8,
        critBonusPercent = 10,
        mobilityModifier = 8,
        summary = "+10% Attack Bonus, +8% Defense, +8 Mobility, +10% Crit. Unlocks Signature Finishers."
    ),
    AWAKENED_OVERDRIVE(
        minWill = 130,
        maxWill = 149,
        displayName = "OVERDRIVE",
        jpKanji = "覚醒",
        badgeColor = Color(0xFFFFD740),
        attackOutputMultiplier = 1.20,
        defenseMultiplier = 1.15,
        hitEvadeBonusPercent = 14,
        critBonusPercent = 16,
        mobilityModifier = 16,
        summary = "+20% Attack Bonus, +15% Defense, +16 Mobility, +16% Crit. Unlocks Overdrive Finishers."
    ),
    MAX_TRANSCENDENT(
        minWill = 150,
        maxWill = 200,
        displayName = "MAX LIMIT",
        jpKanji = "限界突破",
        badgeColor = Color(0xFFD500F9),
        attackOutputMultiplier = 1.32,
        defenseMultiplier = 1.22,
        hitEvadeBonusPercent = 20,
        critBonusPercent = 24,
        mobilityModifier = 24,
        summary = "+32% Attack Bonus, +22% Defense, +24 Mobility, +24% Crit. All Special Weapons unlocked."
    );

    companion object {
        fun fromWill(will: Int): MoraleTier =
            entries.firstOrNull { will in it.minWill..it.maxWill }
                ?: if (will < 50) WAVERING else MAX_TRANSCENDENT
    }
}

/**
 * Specific tactical event triggers that cause single-unit or squad-wide Pilot Morale (Will) fluctuations.
 */
enum class TacticalMoraleEvent(
    val title: String,
    val jpSubtitle: String,
    val description: String,
    val badgeColor: Color
) {
    AEGIS_FLEET_BROADCAST(
        title = "Aegis Command Broadcast",
        jpSubtitle = "全艦突撃指令 (+12 Ally Will)",
        description = "Captain Elena broadcasts a fleet-wide rally: all living Allies gain +12 Will, unlocking 110+ Will Finishers.",
        badgeColor = Color(0xFF00E5FF)
    ),
    REACTOR_OVERDRIVE_SURGE(
        title = "Prana Limit Release",
        jpSubtitle = "気力限界突破 (+20 Selected Will)",
        description = "Surges the selected pilot's Will by +20 (up to 170), activating Overdrive/Max Limit attack scaling and special weapons.",
        badgeColor = Color(0xFFFFD740)
    ),
    ENEMY_PSYCHIC_JAMMING(
        title = "Zeph Psychic Shockwave",
        jpSubtitle = "精神汚染波 (-15 Ally Will)",
        description = "Simulates an enemy morale trap: all Allies lose -15 Will, dropping morale and locking high-Will special weapons.",
        badgeColor = Color(0xFFFF5252)
    ),
    INTIMIDATION_BARRAGE(
        title = "Suppressive Intimidation",
        jpSubtitle = "威圧斉射 (-14 Enemy Will)",
        description = "Demoralizes all hostile units by -14 Will, weakening enemy attack output and locking boss finishers.",
        badgeColor = Color(0xFFB388FF)
    ),
    FIRST_BLOOD_MOMENTUM(
        title = "First Blood Breakthrough",
        jpSubtitle = "先制撃破ボーナス (+8 Ally Will)",
        description = "Vanguard breakthrough event: all Allies gain +8 Will and +10 SP.",
        badgeColor = Color(0xFF00E676)
    )
}

/**
 * Status of a weapon's Will requirement relative to its pilot's current Morale (Will).
 */
data class WeaponMoraleStatus(
    val weapon: Weapon,
    val currentWill: Int,
    val requiredWill: Int,
    val isWillUnlocked: Boolean,
    val willDeficit: Int,
    val isSpecialFinisher: Boolean
)

/**
 * Individual unit morale fluctuation log item produced during combat or events.
 */
data class UnitMoraleChange(
    val unitId: String,
    val unitName: String,
    val pilotName: String,
    val previousWill: Int,
    val newWill: Int,
    val delta: Int,
    val previousTier: MoraleTier,
    val newTier: MoraleTier,
    val reason: String,
    val newlyUnlockedWeapons: List<String> = emptyList(),
    val newlyLockedWeapons: List<String> = emptyList()
) {
    val tierChanged: Boolean get() = previousTier != newTier
}

/**
 * Result of applying a combat or event-driven morale update across all units on the map.
 */
data class MoraleUpdateResult(
    val updatedUnits: List<MechaUnit>,
    val changes: List<UnitMoraleChange>,
    val summaryHeadline: String,
    val summaryDetail: String
)

/**
 * Consolidated unit stat modifiers derived from a pilot's current Will and [MoraleTier].
 */
data class UnitMoraleStatModifiers(
    val will: Int,
    val tier: MoraleTier,
    val attackOutputPercent: Int,
    val defenseModifierPercent: Int,
    val hitBonusPercent: Int,
    val evadeBonusPercent: Int,
    val critBonusPercent: Int,
    val mobilityDelta: Int,
    val initiativeDelta: Int,
    val effectiveArmor: Int,
    val effectiveMobility: Int,
    val effectiveHitStat: Int,
    val effectiveEvadeStat: Int
)

/**
 * Core SRW Pilot Morale (Will / 気力) System.
 *
 * Manages:
 * 1. Morale fluctuation rules from Kills, Boss Kills, Heavy Strikes, Damage Taken, Crisis Potential,
 *    Critical Hits, Evasions, Missed Attacks, Ally Losses, MORALE_SURGE, and Scenario Event Triggers.
 * 2. Morale-based unit stat modifiers (attack output, armor defense, mobility, hit/evade, crit, initiative).
 * 3. Access to Will-gated Special & Ultimate Weapons (`willReq`).
 */
class PilotMoraleSystem {

    companion object {
        const val MIN_WILL = 50
        const val BASE_WILL = 100
        const val MAX_WILL = 170
    }

    /**
     * Resolves the current [MoraleTier] for a unit or raw Will value.
     */
    fun getMoraleTier(unit: MechaUnit): MoraleTier = MoraleTier.fromWill(unit.pilot.will)

    fun getMoraleTier(will: Int): MoraleTier = MoraleTier.fromWill(will)

    /**
     * Computes all effective unit stat modifiers produced by the pilot's current Will & [MoraleTier].
     */
    fun computeUnitStatModifiers(unit: MechaUnit): UnitMoraleStatModifiers {
        val will = unit.pilot.will
        val tier = MoraleTier.fromWill(will)
        val effectiveMobility = (unit.mobility + tier.mobilityModifier).coerceAtLeast(30)
        val effectiveArmor = (unit.armor * computeDefensiveWillMultiplier(will)).roundToInt()
        val initiativeDelta = ((will - BASE_WILL) * 0.50).roundToInt() + (tier.mobilityModifier * 0.40).roundToInt()

        return UnitMoraleStatModifiers(
            will = will,
            tier = tier,
            attackOutputPercent = ((tier.attackOutputMultiplier - 1.0) * 100.0).roundToInt(),
            defenseModifierPercent = ((tier.defenseMultiplier - 1.0) * 100.0).roundToInt(),
            hitBonusPercent = tier.hitEvadeBonusPercent,
            evadeBonusPercent = tier.hitEvadeBonusPercent,
            critBonusPercent = tier.critBonusPercent + max(0, (will - BASE_WILL) / 5),
            mobilityDelta = tier.mobilityModifier,
            initiativeDelta = initiativeDelta,
            effectiveArmor = effectiveArmor,
            effectiveMobility = effectiveMobility,
            effectiveHitStat = (unit.pilot.hitStat + tier.hitEvadeBonusPercent).coerceAtLeast(10),
            effectiveEvadeStat = (unit.pilot.evadeStat + tier.hitEvadeBonusPercent).coerceAtLeast(10)
        )
    }

    /**
     * Computes the Will value after turn-start upkeep: wavering pilots (<100) recover +3 Will toward
     * composure; steady/focused+ pilots gain +2 Will up to [MAX_WILL].
     */
    fun computeTurnUpkeepWill(unit: MechaUnit): Int {
        val recovery = if (unit.pilot.will < BASE_WILL) 3 else 2
        return (unit.pilot.will + recovery).coerceAtMost(MAX_WILL)
    }

    /**
     * Computes the total offensive multiplier contributed by Pilot Will.
     * Combines the classic SRW linear Will scale with the [MoraleTier.attackOutputMultiplier].
     */
    fun computeOffensiveWillMultiplier(pilotStat: Int, will: Int): Double {
        val tier = MoraleTier.fromWill(will)
        val baseScale = (pilotStat + will) / 215.0
        return baseScale * tier.attackOutputMultiplier
    }

    /**
     * Computes the total defensive armor multiplier contributed by Defender Pilot Will.
     */
    fun computeDefensiveWillMultiplier(will: Int): Double {
        val tier = MoraleTier.fromWill(will)
        val linearWillScale = will / 100.0
        return linearWillScale * tier.defenseMultiplier
    }

    /**
     * Evaluates every weapon on [unit] against the pilot's current Will to show which
     * special weapons are unlocked or locked by morale.
     */
    fun evaluateWeaponWillAccess(unit: MechaUnit): List<WeaponMoraleStatus> {
        val currentWill = unit.pilot.will
        return unit.weapons.map { weapon ->
            val unlocked = currentWill >= weapon.willReq
            val deficit = max(0, weapon.willReq - currentWill)
            WeaponMoraleStatus(
                weapon = weapon,
                currentWill = currentWill,
                requiredWill = weapon.willReq,
                isWillUnlocked = unlocked,
                willDeficit = deficit,
                isSpecialFinisher = weapon.willReq > 100
            )
        }
    }

    /**
     * Applies a Will delta to a single [MechaUnit], clamping within [MIN_WILL]..[MAX_WILL]
     * and detecting newly unlocked or locked Will-gated special weapons.
     */
    fun adjustUnitWill(
        unit: MechaUnit,
        delta: Int,
        reason: String
    ): Pair<MechaUnit, UnitMoraleChange> {
        val oldWill = unit.pilot.will
        val newWill = (oldWill + delta).coerceIn(MIN_WILL, MAX_WILL)
        val oldTier = MoraleTier.fromWill(oldWill)
        val newTier = MoraleTier.fromWill(newWill)

        val newlyUnlocked = unit.weapons
            .filter { it.willReq > 100 && oldWill < it.willReq && newWill >= it.willReq }
            .map { it.name }

        val newlyLocked = unit.weapons
            .filter { it.willReq > 100 && oldWill >= it.willReq && newWill < it.willReq }
            .map { it.name }

        val updatedUnit = unit.copy(
            pilot = unit.pilot.copy(will = newWill)
        )

        val change = UnitMoraleChange(
            unitId = unit.id,
            unitName = unit.name,
            pilotName = unit.pilot.name,
            previousWill = oldWill,
            newWill = newWill,
            delta = newWill - oldWill,
            previousTier = oldTier,
            newTier = newTier,
            reason = reason,
            newlyUnlockedWeapons = newlyUnlocked,
            newlyLockedWeapons = newlyLocked
        )

        return updatedUnit to change
    }

    /**
     * Processes all Pilot Morale (Will) fluctuations resulting from a combat exchange:
     * - Kills (+8 Will, or +12 Will for Boss kill) + Fleet Kill Broadcast (+2/+4 Will to all allies, -2 Will to enemies).
     * - Damage Taken (+2 Will) & Crisis Potential (+6 Will when taking >=30% HP damage or dropping <35% HP).
     * - Critical Hit (+3 Will), Normal Hit (+1 Will), Evasion (+3 Will), or Miss Penalty (-3 Will).
     * - Ally Shot Down (+6 Vengeance Will for Super Robots/Ships, -4 Shock Will for wavering units).
     */
    fun processCombatExchangeMorale(
        sequence: BattleCutsceneSequence,
        currentUnits: List<MechaUnit>
    ): MoraleUpdateResult {
        val attackerSurvived = sequence.attackerHpAfterCounter > 0
        val defenderSurvived = sequence.defenderHpAfterPrimary > 0
        val isBossTarget = sequence.defender.id.contains("boss") || sequence.defender.maxHp >= 12000

        val changes = mutableListOf<UnitMoraleChange>()

        val updatedUnits = currentUnits.map { unit ->
            if (!unit.isAlive() && unit.id != sequence.attacker.id && unit.id != sequence.defender.id) {
                return@map unit
            }

            when (unit.id) {
                sequence.attacker.id -> {
                    if (!attackerSurvived) return@map unit

                    val Reasons = mutableListOf<String>()
                    var totalDelta = 0

                    // 1. Primary Attack Result
                    when {
                        !defenderSurvived -> {
                            val killBonus = if (isBossTarget) 12 else 8
                            totalDelta += killBonus
                            Reasons.add(if (isBossTarget) "Boss Kill +$killBonus" else "Kill +$killBonus")
                        }
                        sequence.primaryOutcome == StrikeOutcome.CRITICAL -> {
                            totalDelta += 3
                            Reasons.add("Critical Hit +3")
                        }
                        sequence.primaryOutcome == StrikeOutcome.HIT -> {
                            totalDelta += 1
                            Reasons.add("Hit Landed +1")
                        }
                        sequence.primaryOutcome == StrikeOutcome.MISS -> {
                            totalDelta -= 3
                            Reasons.add("Attack Evaded -3")
                        }
                    }

                    // Heavy non-lethal strike momentum (>= 45% of defender max HP)
                    if (defenderSurvived && sequence.primaryOutcome != StrikeOutcome.MISS &&
                        sequence.primaryDamage >= (sequence.defender.maxHp * 0.45)
                    ) {
                        totalDelta += 2
                        Reasons.add("Heavy Impact +2")
                    }

                    // MORALE_SURGE passive (+3 bonus Will on enemy hit or kill)
                    if (sequence.primaryOutcome != StrikeOutcome.MISS &&
                        unit.pilot.hasPassive(PilotPassiveAbility.MORALE_SURGE)
                    ) {
                        totalDelta += 3
                        Reasons.add("Morale Surge +3")
                    }

                    // 2. Counter-Attack Damage Taken, Barrier Deflect, or Evaded
                    if (sequence.counterExecuted) {
                        if (sequence.counterOutcome == StrikeOutcome.MISS) {
                            totalDelta += 3
                            Reasons.add("Evaded Counter +3")
                        } else if (sequence.counterDamage > 0) {
                            val hpRatioAfter = sequence.attackerHpAfterCounter.toDouble() / unit.maxHp.toDouble()
                            val heavyHit = sequence.counterDamage >= unit.maxHp * 0.30 || hpRatioAfter <= 0.35
                            val dmgGain = if (heavyHit) 6 else 2
                            totalDelta += dmgGain
                            Reasons.add(if (heavyHit) "Crisis Potential +$dmgGain" else "Damage Taken +$dmgGain")
                        } else if (sequence.counterBarrierActivated) {
                            totalDelta += 1
                            Reasons.add("Barrier Deflect +1")
                        }
                    }

                    val (adjusted, change) = adjustUnitWill(unit, totalDelta, Reasons.joinToString(", "))
                    changes.add(change)
                    adjusted
                }

                sequence.defender.id -> {
                    if (!defenderSurvived) return@map unit

                    val reasons = mutableListOf<String>()
                    var totalDelta = 0

                    // 1. Primary Hit Taken or Dodged
                    if (sequence.primaryOutcome == StrikeOutcome.MISS) {
                        totalDelta += 3
                        reasons.add("Ace Evasion +3")
                    } else if (sequence.primaryDamage > 0) {
                        val hpRatioAfter = sequence.defenderHpAfterPrimary.toDouble() / unit.maxHp.toDouble()
                        val heavyHit = sequence.primaryDamage >= unit.maxHp * 0.30 || hpRatioAfter <= 0.35
                        val dmgGain = if (heavyHit) 6 else 2
                        totalDelta += dmgGain
                        reasons.add(if (heavyHit) "Crisis Potential +$dmgGain" else "Damage Taken +$dmgGain")
                    } else if (sequence.primaryBarrierActivated) {
                        totalDelta += 1
                        reasons.add("Barrier Deflect +1")
                    }

                    // 2. Counter-Attack Kill or Hit/Miss
                    if (sequence.counterExecuted) {
                        when {
                            !attackerSurvived -> {
                                totalDelta += 8
                                reasons.add("Counter Kill +8")
                            }
                            sequence.counterOutcome == StrikeOutcome.CRITICAL -> {
                                totalDelta += 3
                                reasons.add("Counter Crit +3")
                            }
                            sequence.counterOutcome == StrikeOutcome.HIT -> {
                                totalDelta += 1
                                reasons.add("Counter Hit +1")
                            }
                            sequence.counterOutcome == StrikeOutcome.MISS -> {
                                totalDelta -= 2
                                reasons.add("Counter Missed -2")
                            }
                        }

                        if (sequence.counterOutcome != StrikeOutcome.MISS &&
                            unit.pilot.hasPassive(PilotPassiveAbility.MORALE_SURGE)
                        ) {
                            totalDelta += 3
                            reasons.add("Morale Surge +3")
                        }
                    }

                    val (adjusted, change) = adjustUnitWill(unit, totalDelta, reasons.joinToString(", "))
                    changes.add(change)
                    adjusted
                }

                else -> {
                    // Bystander unit morale broadcast on kills / deaths
                    var broadcastDelta = 0
                    var broadcastReason = ""

                    val enemyKilled = (!defenderSurvived && sequence.defender.faction == Faction.ENEMY) ||
                        (!attackerSurvived && sequence.attacker.faction == Faction.ENEMY)
                    val allyKilled = (!defenderSurvived && sequence.defender.faction == Faction.ALLY) ||
                        (!attackerSurvived && sequence.attacker.faction == Faction.ALLY)

                    if (enemyKilled) {
                        if (unit.faction == Faction.ALLY) {
                            broadcastDelta = if (isBossTarget) 4 else 2
                            broadcastReason = "Ally Shootdown Broadcast +$broadcastDelta"
                        } else if (unit.faction == Faction.ENEMY) {
                            val isBossBystander = unit.id.contains("boss")
                            broadcastDelta = if (isBossBystander) 3 else -2
                            broadcastReason = if (isBossBystander) "Vanguard Fallen Wrath +3" else "Squad Losses -2"
                        }
                    } else if (allyKilled && unit.faction == Faction.ALLY) {
                        broadcastDelta = if (unit.archetype == MechaArchetype.SUPER_ROBOT ||
                            unit.archetype == MechaArchetype.COMMAND_SHIP
                        ) 6 else -4
                        broadcastReason = if (broadcastDelta > 0) "Heroic Vengeance +6" else "Allied Loss Shock -4"
                    }

                    if (broadcastDelta != 0) {
                        val (adjusted, change) = adjustUnitWill(unit, broadcastDelta, broadcastReason)
                        changes.add(change)
                        adjusted
                    } else {
                        unit
                    }
                }
            }
        }

        val unlockedStrings = changes.flatMap { c ->
            c.newlyUnlockedWeapons.map { wName -> "${c.unitName} unlocked [$wName]" }
        }
        val lockedStrings = changes.flatMap { c ->
            c.newlyLockedWeapons.map { wName -> "${c.unitName} locked [$wName]" }
        }

        val primaryChangeSummary = changes
            .filter { it.unitId == sequence.attacker.id || it.unitId == sequence.defender.id }
            .joinToString(" • ") { c ->
                val sign = if (c.delta >= 0) "+${c.delta}" else "${c.delta}"
                "${c.pilotName} Will ${c.previousWill}->${c.newWill} ($sign: ${c.reason})"
            }

        val weaponGateNotice = when {
            unlockedStrings.isNotEmpty() -> " ★ UNLOCKED: ${unlockedStrings.joinToString(", ")}!"
            lockedStrings.isNotEmpty() -> " ⚠ LOCKED: ${lockedStrings.joinToString(", ")}."
            else -> ""
        }

        return MoraleUpdateResult(
            updatedUnits = updatedUnits,
            changes = changes,
            summaryHeadline = "MORALE FLUCTUATION // $primaryChangeSummary",
            summaryDetail = "$primaryChangeSummary$weaponGateNotice"
        )
    }

    /**
     * Triggers a specific [TacticalMoraleEvent] across the battlefield, fluctuating Pilot Will
     * and unlocking or locking Will-gated special weapons.
     */
    fun triggerMoraleEvent(
        event: TacticalMoraleEvent,
        units: List<MechaUnit>,
        selectedUnitId: String?
    ): MoraleUpdateResult {
        val changes = mutableListOf<UnitMoraleChange>()

        val updatedUnits = units.map { unit ->
            if (!unit.isAlive()) return@map unit

            when (event) {
                TacticalMoraleEvent.AEGIS_FLEET_BROADCAST -> {
                    if (unit.faction == Faction.ALLY) {
                        val (adj, chg) = adjustUnitWill(unit, +12, event.title)
                        changes.add(chg)
                        adj
                    } else unit
                }

                TacticalMoraleEvent.REACTOR_OVERDRIVE_SURGE -> {
                    if (unit.id == selectedUnitId || (selectedUnitId == null && unit.id == "ally_kaiser")) {
                        val (adj, chg) = adjustUnitWill(unit, +20, event.title)
                        changes.add(chg)
                        adj
                    } else unit
                }

                TacticalMoraleEvent.ENEMY_PSYCHIC_JAMMING -> {
                    if (unit.faction == Faction.ALLY) {
                        val (adj, chg) = adjustUnitWill(unit, -15, event.title)
                        changes.add(chg)
                        adj
                    } else unit
                }

                TacticalMoraleEvent.INTIMIDATION_BARRAGE -> {
                    if (unit.faction == Faction.ENEMY) {
                        val (adj, chg) = adjustUnitWill(unit, -14, event.title)
                        changes.add(chg)
                        adj
                    } else unit
                }

                TacticalMoraleEvent.FIRST_BLOOD_MOMENTUM -> {
                    if (unit.faction == Faction.ALLY) {
                        val (adj, chg) = adjustUnitWill(unit, +8, event.title)
                        changes.add(chg)
                        adj.copy(
                            pilot = adj.pilot.copy(
                                currentSp = min(adj.effectiveMaxSp, adj.pilot.currentSp + 10)
                            )
                        )
                    } else unit
                }
            }
        }

        val unlockedWeapons = changes.flatMap { c ->
            c.newlyUnlockedWeapons.map { "${c.unitName}: $it" }
        }
        val lockedWeapons = changes.flatMap { c ->
            c.newlyLockedWeapons.map { "${c.unitName}: $it" }
        }

        val detailMsg = buildString {
            append(event.description)
            if (unlockedWeapons.isNotEmpty()) {
                append(" ★ Special Weapons Unlocked: ${unlockedWeapons.joinToString(", ")}!")
            }
            if (lockedWeapons.isNotEmpty()) {
                append(" ⚠ Special Weapons Locked: ${lockedWeapons.joinToString(", ")}.")
            }
        }

        return MoraleUpdateResult(
            updatedUnits = updatedUnits,
            changes = changes,
            summaryHeadline = "MORALE EVENT [${event.title}]: ${event.jpSubtitle}",
            summaryDetail = detailMsg
        )
    }
}
