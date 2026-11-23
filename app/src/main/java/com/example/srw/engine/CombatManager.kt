package com.example.srw.engine

import com.example.srw.model.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Represents the discrete state-machine stages of a tactical turn in the SRW engine.
 */
enum class TurnPhaseStage(val displayLabel: String) {
    TURN_START_UPKEEP("TURN UPKEEP"),
    PLAYER_PHASE("PLAYER PHASE"),
    ENEMY_PHASE("ENEMY PHASE"),
    TURN_END_CLEANUP("TURN CLEANUP"),
    STAGE_COMPLETED("STAGE COMPLETED")
}

/**
 * Detailed breakdown of a unit's Initiative Score based on Pilot stats, Mecha mobility, Will, and Spirits.
 */
data class InitiativeEntry(
    val rank: Int,
    val unit: MechaUnit,
    val initiativeScore: Int,
    val pilotReactionScore: Int,
    val mobilityScore: Int,
    val willBonusScore: Int,
    val spiritBonusScore: Int,
    val hasActed: Boolean
)

/**
 * Detailed breakdown of the SRW damage calculation pipeline for telemetry and forecast inspection.
 */
data class DamageCalculationBreakdown(
    val baseWeaponPower: Int,
    val pilotOffensiveStat: Int,
    val attackerWill: Int,
    val attackerMoraleTier: MoraleTier = MoraleTier.FOCUSED,
    val offensiveScalingMultiplier: Double,
    val rawOffensivePower: Int,
    val defenderBaseArmor: Int,
    val defenderWill: Int,
    val defenderMoraleTier: MoraleTier = MoraleTier.FOCUSED,
    val terrainDefenseBonusPercent: Int,
    val terrainEvasionBonusPercent: Int,
    val terrainAdaptationGrade: String,
    val attackerTerrainAttackBonusPercent: Int = 0,
    val attackerTerrainHitBonusPercent: Int = 0,
    val attackerTerrainCritBonusPercent: Int = 0,
    val effectiveDefenseDeduction: Int,
    val elementalMultiplier: Double,
    val armorPlatingMitigationMultiplier: Double,
    val spiritAttackMultiplier: Double,
    val criticalMultiplier: Double,
    val defenderMitigationMultiplier: Double,
    val barrierThresholdApplied: Int,
    val finalDamage: Int,
    val formulaSummary: String
)

/**
 * Snapshot returned when advancing turn phases through [CombatManager].
 */
data class TurnPhaseTransitionResult(
    val turnNumber: Int,
    val activeFaction: Faction,
    val phaseStage: TurnPhaseStage,
    val updatedUnits: List<MechaUnit>,
    val initiativeOrder: List<InitiativeEntry>,
    val nextActiveUnit: MechaUnit?,
    val phaseAnnouncement: String
)

/**
 * Breakdown of Hit Rate and Evasion Rate from Unit Stats, Terrain Bonuses, Morale, Spirits, and Stance.
 */
data class HitEvasionBreakdown(
    val finalHitPercent: Int,
    val defenderEvasionPercent: Int,
    val attackerAccuracyRating: Int,
    val defenderEvasionRating: Int
)

/**
 * Central Combat Manager handling:
 * 1. Turn Phase state transitions & upkeep (terrain HP/EN regen, Will/SP recovery, Spirit expiration).
 * 2. Unit Initiative Order calculation based on Pilot stats, Mobility, Will, Spirits, and Elemental Affinity.
 * 3. Damage, Accuracy, Critical Rate, Armor Plating, Elemental Magic, and Barrier calculation logic.
 */
class CombatManager(
    val gridTerrainSystem: GridTerrainSystem = GridTerrainSystem(),
    val developmentSystem: DevelopmentSystem = DevelopmentSystem(),
    val pilotMoraleSystem: PilotMoraleSystem = PilotMoraleSystem(),
    val pilotProgressionSystem: PilotProgressionSystem = PilotProgressionSystem(),
    val spiritCommandSystem: SpiritCommandSystem = SpiritCommandSystem(pilotProgressionSystem),
    private val randomGenerator: Random = Random.Default
) {

    // =========================================================================
    // 1. UNIT INITIATIVE ORDER (PILOT STATS + MOBILITY + WILL + SPIRITS + MAGIC)
    // =========================================================================

    fun calculateUnitInitiative(unit: MechaUnit, rank: Int = 1): InitiativeEntry {
        val pilot = unit.pilot
        val highestCombatStat = max(pilot.meleeStat, pilot.rangedStat)

        val pilotReaction = (
            pilot.evadeStat * 0.45 +
                pilot.hitStat * 0.35 +
                highestCombatStat * 0.20
            ).roundToInt()

        val moraleModifiers = pilotMoraleSystem.computeUnitStatModifiers(unit)
        val mobilityContribution = (moraleModifiers.effectiveMobility * 0.40).roundToInt()
        val willContribution = ((pilot.will - 100) * 0.50).roundToInt()

        val elementalBonus = if (unit.elementalAffinity == ElementalAffinity.CHRONO_LIGHTNING) 15 else 0
        val spiritContribution =
            pilotProgressionSystem.getAccelInitiativeBonus(unit) +
                (if (unit.activeSpirits.contains(SpiritCommand.FOCUS)) 20 else 0) +
                (if (unit.activeSpirits.contains(SpiritCommand.STRIKE)) 8 else 0) +
                pilotProgressionSystem.getPassiveInitiativeBonus(unit) +
                elementalBonus

        val totalScore = (pilotReaction + mobilityContribution + willContribution + spiritContribution)
            .coerceAtLeast(10)

        return InitiativeEntry(
            rank = rank,
            unit = unit,
            initiativeScore = totalScore,
            pilotReactionScore = pilotReaction,
            mobilityScore = mobilityContribution,
            willBonusScore = willContribution,
            spiritBonusScore = spiritContribution,
            hasActed = unit.hasActed
        )
    }

    fun computeInitiativeOrder(
        units: List<MechaUnit>,
        factionFilter: Faction? = null
    ): List<InitiativeEntry> {
        val eligible = units.filter {
            it.isAlive() && (factionFilter == null || it.faction == factionFilter)
        }

        val unsortedEntries = eligible.map { calculateUnitInitiative(it) }

        val sorted = unsortedEntries.sortedWith(
            compareBy<InitiativeEntry> { it.hasActed }
                .thenByDescending { it.initiativeScore }
                .thenByDescending { it.unit.pilot.evadeStat }
                .thenByDescending { it.unit.pilot.will }
                .thenBy { it.unit.id }
        )

        return sorted.mapIndexed { index, entry ->
            entry.copy(rank = index + 1)
        }
    }

    fun getNextReadyUnitByInitiative(
        units: List<MechaUnit>,
        faction: Faction
    ): MechaUnit? {
        return computeInitiativeOrder(units, factionFilter = faction)
            .firstOrNull { !it.hasActed }
            ?.unit
    }

    // =========================================================================
    // 2. TURN PHASE MANAGEMENT & UPKEEP LOGIC
    // =========================================================================

    fun isPhaseComplete(units: List<MechaUnit>, faction: Faction): Boolean {
        return units.none { it.isAlive() && it.faction == faction && !it.hasActed }
    }

    fun beginEnemyPhase(
        turnNumber: Int,
        units: List<MechaUnit>
    ): TurnPhaseTransitionResult {
        val preparedUnits = units.map { unit ->
            if (unit.isAlive() && unit.faction == Faction.ENEMY) {
                unit.copy(hasMoved = false, hasActed = false)
            } else {
                unit
            }
        }

        val initiativeQueue = computeInitiativeOrder(preparedUnits)
        val firstEnemy = getNextReadyUnitByInitiative(preparedUnits, Faction.ENEMY)

        return TurnPhaseTransitionResult(
            turnNumber = turnNumber,
            activeFaction = Faction.ENEMY,
            phaseStage = TurnPhaseStage.ENEMY_PHASE,
            updatedUnits = preparedUnits,
            initiativeOrder = initiativeQueue,
            nextActiveUnit = firstEnemy,
            phaseAnnouncement = if (firstEnemy != null) {
                "ENEMY PHASE — Initiative Lead: ${firstEnemy.name} (${firstEnemy.pilot.name})!"
            } else {
                "ENEMY PHASE — No active enemy units remaining."
            }
        )
    }

    fun beginNextPlayerTurn(
        currentTurnNumber: Int,
        units: List<MechaUnit>,
        terrainGrid: List<List<TerrainType>>
    ): TurnPhaseTransitionResult {
        val nextTurn = currentTurnNumber + 1

        val refreshedUnits = units.map { unit ->
            if (!unit.isAlive()) {
                unit
            } else {
                val cellBonus = gridTerrainSystem.resolveCellOccupancyBonus(unit, terrainGrid)

                val persistentSpirits = unit.activeSpirits.intersect(
                    setOf(SpiritCommand.VALOR, SpiritCommand.SOUL, SpiritCommand.ALERT)
                )

                val spRegen = 6 + (unit.magicLevel - 1) * 2 + pilotProgressionSystem.getSpRegenBonus(unit)

                unit.copy(
                    currentHp = min(unit.maxHp, unit.currentHp + cellBonus.hpRegenAmount),
                    currentEn = min(unit.maxEn, unit.currentEn + cellBonus.enRegenAmount),
                    hasMoved = false,
                    hasActed = false,
                    activeSpirits = persistentSpirits,
                    activeDebuffs = emptySet(),
                    debuffRanks = emptyMap(),
                    pilot = unit.pilot.copy(
                        will = pilotMoraleSystem.computeTurnUpkeepWill(unit),
                        currentSp = min(unit.effectiveMaxSp, unit.pilot.currentSp + spRegen)
                    )
                )
            }
        }

        val initiativeQueue = computeInitiativeOrder(refreshedUnits)
        val leadAlly = getNextReadyUnitByInitiative(refreshedUnits, Faction.ALLY)

        return TurnPhaseTransitionResult(
            turnNumber = nextTurn,
            activeFaction = Faction.ALLY,
            phaseStage = TurnPhaseStage.PLAYER_PHASE,
            updatedUnits = refreshedUnits,
            initiativeOrder = initiativeQueue,
            nextActiveUnit = leadAlly,
            phaseAnnouncement = buildString {
                append("TURN $nextTurn PLAYER PHASE — ")
                if (leadAlly != null) {
                    val initEntry = calculateUnitInitiative(leadAlly)
                    append("Initiative #1: ${leadAlly.name} (INIT ${initEntry.initiativeScore})")
                } else {
                    append("All units ready.")
                }
            }
        )
    }

    // =========================================================================
    // 3. DAMAGE, ACCURACY & CRITICAL CALCULATION LOGIC
    // =========================================================================

    fun calculateDetailedDamage(
        attacker: MechaUnit,
        defender: MechaUnit,
        weapon: Weapon,
        defenderTerrain: TerrainType,
        defenderStance: DefendStance,
        isCritical: Boolean,
        attackerTerrain: TerrainType = TerrainType.PLAIN,
        isCounterattack: Boolean = false
    ): DamageCalculationBreakdown {
        val pilotStat = if (weapon.isMelee) attacker.pilot.meleeStat else attacker.pilot.rangedStat
        val weaponPower = weapon.effectivePower(attacker.upgradeWeapon, attacker.magicLevel)

        val attackerMoraleTier = pilotMoraleSystem.getMoraleTier(attacker)
        val defenderMoraleTier = pilotMoraleSystem.getMoraleTier(defender)

        // Resolve attacker offensive terrain bonus & defender defensive terrain bonus via GridTerrainSystem
        val attackerCellBonus = gridTerrainSystem.resolveUnitTerrainBonus(attacker, attackerTerrain)
        val defenderCellBonus = gridTerrainSystem.resolveUnitTerrainBonus(defender, defenderTerrain)

        val offensiveScaling = pilotMoraleSystem.computeOffensiveWillMultiplier(
            pilotStat = pilotStat,
            will = attacker.pilot.will
        )
        val rawOffense = weaponPower * offensiveScaling * attackerCellBonus.attackPowerMultiplier

        val defenderWillScale = pilotMoraleSystem.computeDefensiveWillMultiplier(defender.pilot.will)

        // Void Gravity elemental affinity pierces 20% of enemy armor
        val armorPierceFactor = if (attacker.elementalAffinity == ElementalAffinity.ABYSSAL_GRAVITY) 0.80 else 1.0
        val defenseDeduction = defender.armor * armorPierceFactor * defenderWillScale *
            defenderCellBonus.defenseDamageMultiplier * 0.48

        var runningDamage = max(350.0, rawOffense - defenseDeduction)

        // Elemental Affinity & Solar Fire multipliers
        val hasElemAdvantage = attacker.elementalAffinity.hasAdvantageOver(defender.elementalAffinity)
        val solarBonus = if (attacker.elementalAffinity == ElementalAffinity.SOLAR_FIRE) 1.10 else 1.0
        val elementalMult = (if (hasElemAdvantage) 1.15 else 1.0) * solarBonus
        runningDamage *= elementalMult

        // Spirit & Pilot Passive Attack Multipliers
        val spiritAttackMult = pilotProgressionSystem.getSpiritAttackMultiplier(attacker)
        val passiveAttackMult = pilotProgressionSystem.getPassiveAttackMultiplier(attacker, isCounterattack)

        val critMult = if (spiritAttackMult == 1.0 && isCritical) 1.35 else 1.0
        runningDamage *= (spiritAttackMult * passiveAttackMult * critMult)

        // Defender Armor Plating damage reduction
        val platingMult = 1.0 - (defender.armorPlating.damageReductionPercent / 100.0)
        runningDamage *= platingMult

        // Defender Spirit / Guard Stance / Analyze Debuff mitigation
        var mitigationMult = 1.0
        mitigationMult *= pilotProgressionSystem.getIronWallDamageMultiplier(defender)
        if (defenderStance == DefendStance.DEFEND) {
            mitigationMult *= 0.55
        }
        val analyzeMult = pilotProgressionSystem.getAnalyzeDamageMultiplier(defender)
        mitigationMult *= analyzeMult
        runningDamage *= mitigationMult

        val barrierThreshold = defender.effectiveBarrierThreshold
        val barrierApplied = if (barrierThreshold > 0 && defender.currentEn >= 10) {
            barrierThreshold
        } else 0

        if (barrierApplied > 0) {
            runningDamage = max(180.0, runningDamage - barrierApplied)
        }

        val finalDmg = runningDamage.roundToInt()
        val statLabel = if (weapon.isMelee) "MEL" else "RNG"

        val summary = buildString {
            append("PWR $weaponPower ×${"%.2f".format(offensiveScaling)} ($statLabel $pilotStat, WILL ${attacker.pilot.will} ${attackerMoraleTier.displayName})")
            if (attackerCellBonus.effectiveAttackBonusPercent != 0) {
                append(" [${attackerTerrain.shortCode} +${attackerCellBonus.effectiveAttackBonusPercent}% ATK]")
            }
            append(" - DEF ${defenseDeduction.roundToInt()} [${defenderTerrain.shortCode} +${defenderCellBonus.effectiveDefenseBonusPercent}% DEF]")
            if (hasElemAdvantage) append(" ×1.15 (${attacker.elementalAffinity.kanji}>${defender.elementalAffinity.kanji})")
            if (spiritAttackMult > 1.0) append(" ×${"%.2f".format(spiritAttackMult)} (SPIRIT)")
            if (passiveAttackMult > 1.0) append(" ×${"%.2f".format(passiveAttackMult)} (PASSIVE)")
            if (defender.armorPlating != ArmorPlatingTier.STANDARD_TITANIUM) {
                append(" -${defender.armorPlating.damageReductionPercent}% (${defender.armorPlating.shortCode})")
            }
            if (analyzeMult > 1.0) {
                append(" ×${"%.2f".format(analyzeMult)} (ANALYZE)")
            }
            if (barrierApplied > 0) append(" -$barrierApplied (${defender.effectiveBarrierName})")
            append(" = $finalDmg DMG")
        }

        return DamageCalculationBreakdown(
            baseWeaponPower = weaponPower,
            pilotOffensiveStat = pilotStat,
            attackerWill = attacker.pilot.will,
            attackerMoraleTier = attackerMoraleTier,
            offensiveScalingMultiplier = offensiveScaling,
            rawOffensivePower = rawOffense.roundToInt(),
            defenderBaseArmor = defender.armor,
            defenderWill = defender.pilot.will,
            defenderMoraleTier = defenderMoraleTier,
            terrainDefenseBonusPercent = defenderCellBonus.effectiveDefenseBonusPercent,
            terrainEvasionBonusPercent = defenderCellBonus.effectiveEvasionBonusPercent,
            terrainAdaptationGrade = defenderCellBonus.adaptationGrade,
            attackerTerrainAttackBonusPercent = attackerCellBonus.effectiveAttackBonusPercent,
            attackerTerrainHitBonusPercent = attackerCellBonus.effectiveHitBonusPercent,
            attackerTerrainCritBonusPercent = attackerCellBonus.effectiveCritBonusPercent,
            effectiveDefenseDeduction = defenseDeduction.roundToInt(),
            elementalMultiplier = elementalMult,
            armorPlatingMitigationMultiplier = platingMult,
            spiritAttackMultiplier = spiritAttackMult,
            criticalMultiplier = critMult,
            defenderMitigationMultiplier = mitigationMult,
            barrierThresholdApplied = barrierApplied,
            finalDamage = finalDmg,
            formulaSummary = summary
        )
    }

    fun calculateDefenseEvasionRating(
        defender: MechaUnit,
        defenderTerrain: TerrainType,
        defenderAuraBonus: Int,
        defenderStance: DefendStance
    ): Int {
        val defenderCellBonus = gridTerrainSystem.resolveUnitTerrainBonus(defender, defenderTerrain)
        val defenderModifiers = pilotMoraleSystem.computeUnitStatModifiers(defender)
        val defenderMorale = defenderModifiers.tier
        val windEvasionBonus = if (defender.elementalAffinity == ElementalAffinity.ASTRAL_WIND) 10 else 0
        val analyzeEvadePenalty = pilotProgressionSystem.getAnalyzeEvadePenalty(defender)
        val focusEvadeBonus = pilotProgressionSystem.getFocusHitEvadeBonus(defender)
        val passiveEvadeBonus = pilotProgressionSystem.getPassiveEvadeBonus(defender, defenderStance)

        return (defenderModifiers.effectiveMobility / 3 + defender.pilot.evadeStat / 3 +
            defenderCellBonus.effectiveEvasionBonusPercent + defenderAuraBonus + windEvasionBonus +
            defenderMorale.hitEvadeBonusPercent +
            focusEvadeBonus + passiveEvadeBonus +
            (if (defenderStance == DefendStance.EVADE) 45 else 0) -
            analyzeEvadePenalty).coerceAtLeast(0)
    }

    fun calculateHitEvasionBreakdown(
        attacker: MechaUnit,
        defender: MechaUnit,
        weapon: Weapon,
        defenderTerrain: TerrainType,
        attackerAuraBonus: Int,
        defenderAuraBonus: Int,
        defenderStance: DefendStance,
        attackerTerrain: TerrainType = TerrainType.PLAIN
    ): HitEvasionBreakdown {
        val attackerInit = calculateUnitInitiative(attacker).initiativeScore
        val defenderInit = calculateUnitInitiative(defender).initiativeScore
        val initiativeEdge = ((attackerInit - defenderInit) / 12).coerceIn(-10, 10)

        val attackerMorale = pilotMoraleSystem.getMoraleTier(attacker)
        val attackerCellBonus = gridTerrainSystem.resolveUnitTerrainBonus(attacker, attackerTerrain)
        val voltAccuracyBonus = if (attacker.elementalAffinity == ElementalAffinity.CHRONO_LIGHTNING) 10 else 0
        val confusePenalty = pilotProgressionSystem.getConfuseHitPenalty(attacker)
        val spiritHitBonus = pilotProgressionSystem.getSpiritHitBonus(attacker)
        val passiveHitBonus = pilotProgressionSystem.getPassiveHitBonus(attacker)

        val attackAccuracy = (attacker.pilot.hitStat / 2 + 82 + weapon.effectiveHitBonus +
            attackerCellBonus.effectiveHitBonusPercent +
            attackerAuraBonus + initiativeEdge + voltAccuracyBonus + attackerMorale.hitEvadeBonusPercent +
            spiritHitBonus + passiveHitBonus -
            confusePenalty).coerceAtLeast(0)

        val defendEvasion = calculateDefenseEvasionRating(
            defender = defender,
            defenderTerrain = defenderTerrain,
            defenderAuraBonus = defenderAuraBonus,
            defenderStance = defenderStance
        )

        val finalHit = when {
            defender.activeSpirits.contains(SpiritCommand.ALERT) -> 0
            attacker.activeSpirits.contains(SpiritCommand.STRIKE) -> 100
            else -> (attackAccuracy - defendEvasion).coerceIn(5, 100)
        }

        return HitEvasionBreakdown(
            finalHitPercent = finalHit,
            defenderEvasionPercent = 100 - finalHit,
            attackerAccuracyRating = attackAccuracy,
            defenderEvasionRating = defendEvasion
        )
    }

    fun calculateHitRate(
        attacker: MechaUnit,
        defender: MechaUnit,
        weapon: Weapon,
        defenderTerrain: TerrainType,
        attackerAuraBonus: Int,
        defenderAuraBonus: Int,
        defenderStance: DefendStance,
        attackerTerrain: TerrainType = TerrainType.PLAIN
    ): Int = calculateHitEvasionBreakdown(
        attacker = attacker,
        defender = defender,
        weapon = weapon,
        defenderTerrain = defenderTerrain,
        attackerAuraBonus = attackerAuraBonus,
        defenderAuraBonus = defenderAuraBonus,
        defenderStance = defenderStance,
        attackerTerrain = attackerTerrain
    ).finalHitPercent

    fun calculateCritRate(
        attacker: MechaUnit,
        defender: MechaUnit,
        weapon: Weapon,
        attackerTerrain: TerrainType = TerrainType.PLAIN
    ): Int {
        val skillDelta = (attacker.pilot.hitStat - defender.pilot.evadeStat) / 4
        val attackerMorale = pilotMoraleSystem.getMoraleTier(attacker)
        val attackerCellBonus = gridTerrainSystem.resolveUnitTerrainBonus(attacker, attackerTerrain)
        val willBonus = max(0, (attacker.pilot.will - 100) / 5) + attackerMorale.critBonusPercent
        val customBonus = if (attacker.hasFullCustomBonus) 10 else 0
        val spiritCritBonus = pilotProgressionSystem.getSpiritCritBonus(attacker)
        val passiveCritBonus = pilotProgressionSystem.getPassiveCritBonus(attacker)
        return (skillDelta + weapon.effectiveCritBonus + attackerCellBonus.effectiveCritBonusPercent +
            willBonus + customBonus + spiritCritBonus + passiveCritBonus)
            .coerceIn(5, 95)
    }

    fun calculateCommandAuraBonus(unit: MechaUnit, allUnits: List<MechaUnit>): Int {
        val hasCommanderInRange = allUnits.any { ally ->
            ally.isAlive() &&
                ally.faction == unit.faction &&
                ally.id != unit.id &&
                ally.commandAuraRadius > 0 &&
                ally.pos.distanceTo(unit.pos) <= ally.commandAuraRadius
        }
        return if (hasCommanderInRange) 15 else 0
    }

    fun buildCombatForecast(
        attacker: MechaUnit,
        defender: MechaUnit,
        attackerWeapon: Weapon,
        defenderWeapon: Weapon?,
        defenderStance: DefendStance,
        terrainGrid: List<List<TerrainType>>,
        allUnits: List<MechaUnit>
    ): CombatForecast {
        val distance = attacker.pos.distanceTo(defender.pos)
        val terrainComparison = gridTerrainSystem.compareBattlefieldCells(attacker, defender, terrainGrid)
        val attackerTerrain = terrainComparison.attackerCell.terrainType
        val defenderTerrain = terrainComparison.defenderCell.terrainType
        val attackerAura = calculateCommandAuraBonus(attacker, allUnits)
        val defenderAura = calculateCommandAuraBonus(defender, allUnits)

        return buildForecastFromContext(
            attacker = attacker,
            defender = defender,
            attackerWeapon = attackerWeapon,
            defenderWeapon = defenderWeapon,
            defenderStance = defenderStance,
            distance = distance,
            attackerTerrain = attackerTerrain,
            defenderTerrain = defenderTerrain,
            attackerAura = attackerAura,
            defenderAura = defenderAura,
            attackerTerrainAttackBonus = terrainComparison.attackerAttackBonusPercent,
            attackerTerrainHitBonus = terrainComparison.attackerHitBonusPercent,
            attackerTerrainCritBonus = terrainComparison.attackerCritBonusPercent,
            attackerTerrainEvadeBonus = terrainComparison.attackerCell.effectiveEvasionBonusPercent,
            defenderTerrainDefBonus = terrainComparison.defenderDefenseBonusPercent,
            defenderTerrainEvadeBonus = terrainComparison.defenderEvasionBonusPercent
        ).first
    }

    /**
     * Recomputes a [CombatForecast] and [DamageCalculationBreakdown] under simulated Pilot Will values
     * while preserving all battlefield terrain, stance, and command aura modifiers from [baseForecast].
     */
    fun simulateForecastMorale(
        baseForecast: CombatForecast,
        simulatedAttackerWill: Int = baseForecast.attacker.pilot.will,
        simulatedDefenderWill: Int = baseForecast.defender.pilot.will
    ): Pair<CombatForecast, DamageCalculationBreakdown> {
        val clampedAttackerWill = simulatedAttackerWill.coerceIn(
            PilotMoraleSystem.MIN_WILL,
            PilotMoraleSystem.MAX_WILL
        )
        val clampedDefenderWill = simulatedDefenderWill.coerceIn(
            PilotMoraleSystem.MIN_WILL,
            PilotMoraleSystem.MAX_WILL
        )
        val simAttacker = baseForecast.attacker.copy(
            pilot = baseForecast.attacker.pilot.copy(will = clampedAttackerWill)
        )
        val simDefender = baseForecast.defender.copy(
            pilot = baseForecast.defender.pilot.copy(will = clampedDefenderWill)
        )
        val candidateDefenderWeapon = baseForecast.defenderWeapon ?: TacticalCombatEngine.selectBestCounterWeapon(
            simDefender,
            baseForecast.distance
        )

        return buildForecastFromContext(
            attacker = simAttacker,
            defender = simDefender,
            attackerWeapon = baseForecast.attackerWeapon,
            defenderWeapon = candidateDefenderWeapon,
            defenderStance = baseForecast.defenderStance,
            distance = baseForecast.distance,
            attackerTerrain = baseForecast.attackerTerrain,
            defenderTerrain = baseForecast.defenderTerrain,
            attackerAura = baseForecast.attackerAuraBonus,
            defenderAura = baseForecast.defenderAuraBonus,
            attackerTerrainAttackBonus = baseForecast.attackerTerrainAttackBonus,
            attackerTerrainHitBonus = baseForecast.attackerTerrainHitBonus,
            attackerTerrainCritBonus = baseForecast.attackerTerrainCritBonus,
            attackerTerrainEvadeBonus = baseForecast.attackerTerrainEvadeBonus,
            defenderTerrainDefBonus = baseForecast.defenderTerrainDefBonus,
            defenderTerrainEvadeBonus = baseForecast.defenderTerrainEvadeBonus
        )
    }

    private fun buildForecastFromContext(
        attacker: MechaUnit,
        defender: MechaUnit,
        attackerWeapon: Weapon,
        defenderWeapon: Weapon?,
        defenderStance: DefendStance,
        distance: Int,
        attackerTerrain: TerrainType,
        defenderTerrain: TerrainType,
        attackerAura: Int,
        defenderAura: Int,
        attackerTerrainAttackBonus: Int,
        attackerTerrainHitBonus: Int,
        attackerTerrainCritBonus: Int,
        attackerTerrainEvadeBonus: Int,
        defenderTerrainDefBonus: Int,
        defenderTerrainEvadeBonus: Int
    ): Pair<CombatForecast, DamageCalculationBreakdown> {
        val attackerMoraleTier = pilotMoraleSystem.getMoraleTier(attacker)
        val defenderMoraleTier = pilotMoraleSystem.getMoraleTier(defender)

        val attackerHitBreakdown = calculateHitEvasionBreakdown(
            attacker = attacker,
            defender = defender,
            weapon = attackerWeapon,
            defenderTerrain = defenderTerrain,
            attackerAuraBonus = attackerAura,
            defenderAuraBonus = defenderAura,
            defenderStance = defenderStance,
            attackerTerrain = attackerTerrain
        )
        val attackerHit = attackerHitBreakdown.finalHitPercent

        val attackerCrit = calculateCritRate(attacker, defender, attackerWeapon, attackerTerrain)
        val attackerDamageBreakdown = calculateDetailedDamage(
            attacker = attacker,
            defender = defender,
            weapon = attackerWeapon,
            defenderTerrain = defenderTerrain,
            defenderStance = defenderStance,
            isCritical = false,
            attackerTerrain = attackerTerrain
        )

        // Isolate Attacker Will/Morale impact relative to neutral 100 Will baseline
        val attackerBaselineAt100Breakdown = if (attacker.pilot.will == PilotMoraleSystem.BASE_WILL) {
            attackerDamageBreakdown
        } else {
            calculateDetailedDamage(
                attacker = attacker.copy(pilot = attacker.pilot.copy(will = PilotMoraleSystem.BASE_WILL)),
                defender = defender,
                weapon = attackerWeapon,
                defenderTerrain = defenderTerrain,
                defenderStance = defenderStance,
                isCritical = false,
                attackerTerrain = attackerTerrain
            )
        }
        val attackerBaselineDamage = attackerBaselineAt100Breakdown.finalDamage
        val attackerMoraleDamageDelta = attackerDamageBreakdown.finalDamage - attackerBaselineDamage
        val attackerMoraleOutputPercent = if (attackerBaselineAt100Breakdown.offensiveScalingMultiplier > 0.0) {
            ((attackerDamageBreakdown.offensiveScalingMultiplier /
                attackerBaselineAt100Breakdown.offensiveScalingMultiplier - 1.0) * 100.0).roundToInt()
        } else 0

        val canCounter = defenderStance == DefendStance.COUNTER &&
            defenderWeapon != null &&
            defender.canUseWeapon(defenderWeapon, afterMove = false, distance = distance)

        val defenderHitBreakdown = if (canCounter && defenderWeapon != null) {
            calculateHitEvasionBreakdown(
                attacker = defender,
                defender = attacker,
                weapon = defenderWeapon,
                defenderTerrain = attackerTerrain,
                attackerAuraBonus = defenderAura,
                defenderAuraBonus = attackerAura,
                defenderStance = DefendStance.COUNTER,
                attackerTerrain = defenderTerrain
            )
        } else null

        val defenderHit = defenderHitBreakdown?.finalHitPercent ?: 0
        val attackerEvasionRating = defenderHitBreakdown?.defenderEvasionRating
            ?: calculateDefenseEvasionRating(
                defender = attacker,
                defenderTerrain = attackerTerrain,
                defenderAuraBonus = attackerAura,
                defenderStance = DefendStance.COUNTER
            )
        val attackerEvasionPercent = if (canCounter) 100 - defenderHit else 100

        val defenderCrit = if (canCounter && defenderWeapon != null) {
            calculateCritRate(defender, attacker, defenderWeapon, defenderTerrain)
        } else 0

        val defenderDamageBreakdown = if (canCounter && defenderWeapon != null) {
            calculateDetailedDamage(
                attacker = defender,
                defender = attacker,
                weapon = defenderWeapon,
                defenderTerrain = attackerTerrain,
                defenderStance = DefendStance.COUNTER,
                isCritical = false,
                attackerTerrain = defenderTerrain,
                isCounterattack = true
            )
        } else null
        val defenderDamage = defenderDamageBreakdown?.finalDamage ?: 0

        val defenderBaselineDamageBreakdown = if (canCounter && defenderWeapon != null && defenderDamageBreakdown != null) {
            if (defender.pilot.will == PilotMoraleSystem.BASE_WILL) {
                defenderDamageBreakdown
            } else {
                calculateDetailedDamage(
                    attacker = defender.copy(pilot = defender.pilot.copy(will = PilotMoraleSystem.BASE_WILL)),
                    defender = attacker,
                    weapon = defenderWeapon,
                    defenderTerrain = attackerTerrain,
                    defenderStance = DefendStance.COUNTER,
                    isCritical = false,
                    attackerTerrain = defenderTerrain,
                    isCounterattack = true
                )
            }
        } else null

        val defenderBaselineCounterDmg = defenderBaselineDamageBreakdown?.finalDamage ?: 0
        val defenderMoraleDamageDelta = defenderDamage - defenderBaselineCounterDmg
        val defenderMoraleOutputPercent = if (
            defenderDamageBreakdown != null &&
            defenderBaselineDamageBreakdown != null &&
            defenderBaselineDamageBreakdown.offensiveScalingMultiplier > 0.0
        ) {
            ((defenderDamageBreakdown.offensiveScalingMultiplier /
                defenderBaselineDamageBreakdown.offensiveScalingMultiplier - 1.0) * 100.0).roundToInt()
        } else 0

        val defenderWillArmorScale = pilotMoraleSystem.computeDefensiveWillMultiplier(defender.pilot.will)
        val defenderMoraleArmorPercent = ((defenderWillArmorScale - 1.0) * 100.0).roundToInt()

        val forecast = CombatForecast(
            attacker = attacker,
            defender = defender,
            attackerWeapon = attackerWeapon,
            defenderWeapon = if (canCounter) defenderWeapon else null,
            defenderStance = defenderStance,
            distance = distance,
            attackerTerrain = attackerTerrain,
            defenderTerrain = defenderTerrain,
            attackerPredictedDamage = attackerDamageBreakdown.finalDamage,
            attackerHitPercent = attackerHit,
            attackerCritPercent = attackerCrit,
            defenderCanCounter = canCounter,
            defenderPredictedDamage = defenderDamage,
            defenderHitPercent = defenderHit,
            defenderCritPercent = defenderCrit,
            attackerAuraBonus = attackerAura,
            defenderAuraBonus = defenderAura,
            attackerTerrainAttackBonus = attackerTerrainAttackBonus,
            attackerTerrainHitBonus = attackerTerrainHitBonus,
            attackerTerrainCritBonus = attackerTerrainCritBonus,
            attackerTerrainEvadeBonus = attackerTerrainEvadeBonus,
            defenderTerrainDefBonus = defenderTerrainDefBonus,
            defenderTerrainEvadeBonus = defenderTerrainEvadeBonus,
            attackerEvasionPercent = attackerEvasionPercent,
            defenderEvasionPercent = attackerHitBreakdown.defenderEvasionPercent,
            attackerAccuracyRating = attackerHitBreakdown.attackerAccuracyRating,
            defenderEvasionRating = attackerHitBreakdown.defenderEvasionRating,
            defenderAccuracyRating = defenderHitBreakdown?.attackerAccuracyRating ?: 0,
            attackerEvasionRating = attackerEvasionRating,
            defenderProjectedHpAfter = (defender.currentHp - attackerDamageBreakdown.finalDamage).coerceAtLeast(0),
            attackerProjectedHpAfter = (attacker.currentHp - defenderDamage).coerceAtLeast(0),
            attackerWill = attacker.pilot.will,
            defenderWill = defender.pilot.will,
            attackerMoraleTierName = attackerMoraleTier.displayName,
            defenderMoraleTierName = defenderMoraleTier.displayName,
            attackerBaselineDamageAt100Will = attackerBaselineDamage,
            attackerMoraleDamageDelta = attackerMoraleDamageDelta,
            attackerMoraleOutputPercent = attackerMoraleOutputPercent,
            defenderBaselineCounterDamageAt100Will = defenderBaselineCounterDmg,
            defenderMoraleDamageDelta = defenderMoraleDamageDelta,
            defenderMoraleOutputPercent = defenderMoraleOutputPercent,
            defenderMoraleArmorPercent = defenderMoraleArmorPercent
        )
        return forecast to attackerDamageBreakdown
    }

    fun resolveCombatExchange(
        forecast: CombatForecast,
        forcedPrimaryOutcome: StrikeOutcome? = null,
        isSandboxPreview: Boolean = false
    ): BattleCutsceneSequence {
        val attacker = forecast.attacker
        val defender = forecast.defender
        val attackerWeapon = forecast.attackerWeapon
        val defenderWeapon = forecast.defenderWeapon

        val primaryOutcome = forcedPrimaryOutcome ?: run {
            val hitRoll = randomGenerator.nextInt(100)
            if (hitRoll >= forecast.attackerHitPercent) {
                StrikeOutcome.MISS
            } else {
                val critRoll = randomGenerator.nextInt(100)
                if (critRoll < forecast.attackerCritPercent) StrikeOutcome.CRITICAL else StrikeOutcome.HIT
            }
        }

        val primaryDamage = when (primaryOutcome) {
            StrikeOutcome.MISS -> 0
            StrikeOutcome.HIT -> forecast.attackerPredictedDamage
            StrikeOutcome.CRITICAL -> calculateDetailedDamage(
                attacker = attacker,
                defender = defender,
                weapon = attackerWeapon,
                defenderTerrain = forecast.defenderTerrain,
                defenderStance = forecast.defenderStance,
                isCritical = true,
                attackerTerrain = forecast.attackerTerrain
            ).finalDamage
        }

        val primaryBarrierUsed = primaryOutcome != StrikeOutcome.MISS &&
            defender.effectiveBarrierThreshold > 0 &&
            defender.currentEn >= 10

        val defenderHpAfter = (defender.currentHp - primaryDamage).coerceAtLeast(0)
        val defenderSurvived = defenderHpAfter > 0

        val counterExecuted = defenderSurvived && forecast.defenderCanCounter && defenderWeapon != null
        val counterOutcome = if (counterExecuted && defenderWeapon != null) {
            val hitRoll = randomGenerator.nextInt(100)
            if (hitRoll >= forecast.defenderHitPercent) {
                StrikeOutcome.MISS
            } else {
                val critRoll = randomGenerator.nextInt(100)
                if (critRoll < forecast.defenderCritPercent) StrikeOutcome.CRITICAL else StrikeOutcome.HIT
            }
        } else StrikeOutcome.MISS

        val counterDamage = if (counterExecuted && defenderWeapon != null) {
            when (counterOutcome) {
                StrikeOutcome.MISS -> 0
                StrikeOutcome.HIT -> forecast.defenderPredictedDamage
                StrikeOutcome.CRITICAL -> calculateDetailedDamage(
                    attacker = defender,
                    defender = attacker,
                    weapon = defenderWeapon,
                    defenderTerrain = forecast.attackerTerrain,
                    defenderStance = DefendStance.COUNTER,
                    isCritical = true,
                    attackerTerrain = forecast.defenderTerrain,
                    isCounterattack = true
                ).finalDamage
            }
        } else 0

        val counterBarrierUsed = counterExecuted &&
            counterOutcome != StrikeOutcome.MISS &&
            attacker.effectiveBarrierThreshold > 0 &&
            attacker.currentEn >= 10

        val attackerHpAfter = (attacker.currentHp - counterDamage).coerceAtLeast(0)

        val attackerQuote = if (attackerWeapon.willReq >= 105 || primaryOutcome == StrikeOutcome.CRITICAL) {
            attacker.pilot.finisherQuote
        } else {
            attacker.pilot.attackQuotes.firstOrNull() ?: "Engaging target!"
        }

        val defenderQuote = when {
            primaryOutcome == StrikeOutcome.MISS -> defender.pilot.dodgeQuote
            defenderHpAfter == 0 -> "Critical reactor failure!! Ejecting—!"
            counterExecuted -> defender.pilot.attackQuotes.firstOrNull() ?: "Returning fire!"
            else -> defender.pilot.damageQuote
        }

        return BattleCutsceneSequence(
            attacker = attacker,
            defender = defender,
            attackerWeapon = attackerWeapon,
            defenderWeapon = defenderWeapon,
            defenderStance = forecast.defenderStance,
            attackerTerrain = forecast.attackerTerrain,
            defenderTerrain = forecast.defenderTerrain,
            primaryOutcome = primaryOutcome,
            primaryDamage = primaryDamage,
            primaryBarrierActivated = primaryBarrierUsed,
            defenderHpAfterPrimary = defenderHpAfter,
            counterExecuted = counterExecuted,
            counterOutcome = counterOutcome,
            counterDamage = counterDamage,
            counterBarrierActivated = counterBarrierUsed,
            attackerHpAfterCounter = attackerHpAfter,
            attackerQuote = attackerQuote,
            defenderQuote = defenderQuote,
            attackerHitRate = forecast.attackerHitPercent,
            defenderHitRate = forecast.defenderHitPercent,
            isSandboxPreview = isSandboxPreview
        )
    }
}
