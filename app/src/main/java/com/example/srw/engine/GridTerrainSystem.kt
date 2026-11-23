package com.example.srw.engine

import com.example.srw.model.GridPos
import com.example.srw.model.MechaArchetype
import com.example.srw.model.MechaUnit
import com.example.srw.model.TerrainType
import kotlin.math.roundToInt

/**
 * Defines the intrinsic defensive, offensive, regeneration, and movement properties
 * mapped to a specific [TerrainType].
 */
data class TerrainBonusProfile(
    val terrainType: TerrainType,
    val defenseBonusPercent: Int,
    val evasionBonusPercent: Int,
    val attackBonusPercent: Int,
    val hitBonusPercent: Int,
    val critBonusPercent: Int,
    val groundMoveCost: Int,
    val airMoveCost: Int,
    val hpRegenPercent: Int,
    val enRegenPercent: Int,
    val providesCoverFromRanged: Boolean,
    val tacticalRoleLabel: String,
    val tacticalDescription: String
)

/**
 * Resolved defensive and offensive combat modifiers for a specific [MechaUnit]
 * occupying a specific [GridPos] cell.
 */
data class CellOccupancyBonus(
    val pos: GridPos,
    val terrainType: TerrainType,
    val baseDefenseBonusPercent: Int,
    val baseEvasionBonusPercent: Int,
    val baseAttackBonusPercent: Int,
    val baseHitBonusPercent: Int,
    val baseCritBonusPercent: Int,
    val unitAdaptationBonusPercent: Int,
    val effectiveDefenseBonusPercent: Int,
    val effectiveEvasionBonusPercent: Int,
    val effectiveAttackBonusPercent: Int,
    val effectiveHitBonusPercent: Int,
    val effectiveCritBonusPercent: Int,
    val defenseDamageMultiplier: Double,
    val attackPowerMultiplier: Double,
    val movementCost: Int,
    val hpRegenAmount: Int,
    val enRegenAmount: Int,
    val adaptationGrade: String,
    val summaryLabel: String
)

/**
 * Comparative terrain analysis between an Attacker's cell (offensive modifiers)
 * and a Defender's cell (defensive/evasion modifiers) during combat.
 */
data class BattlefieldTerrainComparison(
    val attackerCell: CellOccupancyBonus,
    val defenderCell: CellOccupancyBonus,
    val attackerAttackBonusPercent: Int,
    val attackerHitBonusPercent: Int,
    val attackerCritBonusPercent: Int,
    val defenderDefenseBonusPercent: Int,
    val defenderEvasionBonusPercent: Int,
    val netAccuracyShift: Int,
    val defenderDamageReductionPercent: Int,
    val comparisonSummary: String
)

/**
 * Grid Terrain System that maps specific tile types (e.g., Forest, Mountain, Urban/City, Base,
 * Plains, Sea, Space, Asteroid) to both **Defensive** (Armor Defense, Evasion, Regeneration) and
 * **Offensive** (Attack Output, Targeting Accuracy/Hit, Critical Ambush) combat modifiers.
 */
class GridTerrainSystem {

    private val orderedTerrainTypes: List<TerrainType> = listOf(
        TerrainType.FOREST,
        TerrainType.MOUNTAIN,
        TerrainType.CITY,
        TerrainType.BASE,
        TerrainType.PLAIN,
        TerrainType.SEA,
        TerrainType.SPACE,
        TerrainType.ASTEROID
    )

    private val terrainProfiles: Map<TerrainType, TerrainBonusProfile> = mapOf(
        TerrainType.PLAIN to TerrainBonusProfile(
            terrainType = TerrainType.PLAIN,
            defenseBonusPercent = 5,
            evasionBonusPercent = 5,
            attackBonusPercent = 0,
            hitBonusPercent = 5,
            critBonusPercent = 0,
            groundMoveCost = 1,
            airMoveCost = 1,
            hpRegenPercent = 0,
            enRegenPercent = 0,
            providesCoverFromRanged = false,
            tacticalRoleLabel = "Open Sightline",
            tacticalDescription = "Open plains offering unobstructed targeting (+5% HIT) and minimal cover (+5% DEF, +5% EVADE)."
        ),
        TerrainType.FOREST to TerrainBonusProfile(
            terrainType = TerrainType.FOREST,
            defenseBonusPercent = 15,
            evasionBonusPercent = 20,
            attackBonusPercent = 5,
            hitBonusPercent = 0,
            critBonusPercent = 12,
            groundMoveCost = 2,
            airMoveCost = 1,
            hpRegenPercent = 0,
            enRegenPercent = 0,
            providesCoverFromRanged = true,
            tacticalRoleLabel = "Evasion Cover & Ambush",
            tacticalDescription = "Dense forest foliage strongly boosts evasion (+15% DEF, +20% EVADE) and sets up ambush strikes (+5% ATK, +12% CRIT)."
        ),
        TerrainType.CITY to TerrainBonusProfile(
            terrainType = TerrainType.CITY,
            defenseBonusPercent = 15,
            evasionBonusPercent = 15,
            attackBonusPercent = 8,
            hitBonusPercent = 5,
            critBonusPercent = 5,
            groundMoveCost = 1,
            airMoveCost = 1,
            hpRegenPercent = 5,
            enRegenPercent = 5,
            providesCoverFromRanged = true,
            tacticalRoleLabel = "Balanced Urban Grid",
            tacticalDescription = "Urban skyscrapers & power grid grant cover (+15% DEF, +15% EVADE, +5% Regen) and flanking offense (+8% ATK, +5% HIT/CRIT)."
        ),
        TerrainType.BASE to TerrainBonusProfile(
            terrainType = TerrainType.BASE,
            defenseBonusPercent = 25,
            evasionBonusPercent = 20,
            attackBonusPercent = 10,
            hitBonusPercent = 15,
            critBonusPercent = 5,
            groundMoveCost = 1,
            airMoveCost = 1,
            hpRegenPercent = 15,
            enRegenPercent = 15,
            providesCoverFromRanged = true,
            tacticalRoleLabel = "Bastion & Fire-Control",
            tacticalDescription = "Fortified command base with radar uplinks (+25% DEF, +20% EVADE, +15% Regen) & weapon fire-control (+10% ATK, +15% HIT, +5% CRIT)."
        ),
        TerrainType.MOUNTAIN to TerrainBonusProfile(
            terrainType = TerrainType.MOUNTAIN,
            defenseBonusPercent = 20,
            evasionBonusPercent = 15,
            attackBonusPercent = 15,
            hitBonusPercent = 12,
            critBonusPercent = 10,
            groundMoveCost = 2,
            airMoveCost = 1,
            hpRegenPercent = 0,
            enRegenPercent = 0,
            providesCoverFromRanged = true,
            tacticalRoleLabel = "High-Ground Dominance",
            tacticalDescription = "Elevated ridge crags grant cover (+20% DEF, +15% EVADE) and commanding high-ground fire (+15% ATK, +12% HIT, +10% CRIT)."
        ),
        TerrainType.SEA to TerrainBonusProfile(
            terrainType = TerrainType.SEA,
            defenseBonusPercent = 10,
            evasionBonusPercent = 0,
            attackBonusPercent = 5,
            hitBonusPercent = -5,
            critBonusPercent = 0,
            groundMoveCost = 2,
            airMoveCost = 1,
            hpRegenPercent = 0,
            enRegenPercent = 0,
            providesCoverFromRanged = false,
            tacticalRoleLabel = "Beam Cooling / Drag",
            tacticalDescription = "Ocean trench dissipates thermal blasts (+10% DEF, +5% ATK cooling) while water resistance hinders aim (-5% HIT, 0% EVADE)."
        ),
        TerrainType.SPACE to TerrainBonusProfile(
            terrainType = TerrainType.SPACE,
            defenseBonusPercent = 0,
            evasionBonusPercent = 0,
            attackBonusPercent = 5,
            hitBonusPercent = 10,
            critBonusPercent = 8,
            groundMoveCost = 1,
            airMoveCost = 1,
            hpRegenPercent = 0,
            enRegenPercent = 0,
            providesCoverFromRanged = false,
            tacticalRoleLabel = "Zero-G Precision",
            tacticalDescription = "Unobstructed zero-gravity vacuum offers zero cover (0% DEF/EVADE) but sharpens long-range ballistics (+5% ATK, +10% HIT, +8% CRIT)."
        ),
        TerrainType.ASTEROID to TerrainBonusProfile(
            terrainType = TerrainType.ASTEROID,
            defenseBonusPercent = 20,
            evasionBonusPercent = 20,
            attackBonusPercent = 5,
            hitBonusPercent = 0,
            critBonusPercent = 10,
            groundMoveCost = 2,
            airMoveCost = 1,
            hpRegenPercent = 5,
            enRegenPercent = 5,
            providesCoverFromRanged = true,
            tacticalRoleLabel = "3D Cover & Ambush",
            tacticalDescription = "Dense orbital debris reef providing 3D evasion cover (+20% DEF, +20% EVADE) and ambush angles (+5% ATK, +10% CRIT)."
        )
    )

    /**
     * Retrieves the static [TerrainBonusProfile] for a given [TerrainType].
     */
    fun getProfile(terrainType: TerrainType): TerrainBonusProfile {
        return terrainProfiles[terrainType] ?: terrainProfiles.getValue(TerrainType.PLAIN)
    }

    /**
     * Returns all terrain bonus profiles in a curated display order.
     */
    fun getAllTerrainProfiles(): List<TerrainBonusProfile> {
        return orderedTerrainTypes.map { getProfile(it) }
    }

    /**
     * Safely looks up the [TerrainType] at [pos] on the [terrainGrid].
     */
    fun getTerrainAt(pos: GridPos, terrainGrid: List<List<TerrainType>>): TerrainType {
        return terrainGrid.getOrNull(pos.y)?.getOrNull(pos.x) ?: TerrainType.PLAIN
    }

    /**
     * Resolves the exact Defensive (Defense, Evasion, Regen) and Offensive (Attack, Hit, Crit)
     * bonuses for a [MechaUnit] occupying its current cell (or [overridePos]) on [terrainGrid].
     *
     * Accounts for SRW unit archetype terrain adaptation (Rank S vs Rank A):
     * - Real Robots gain +5% Evasion & +5% Crit in cover tiles (`FOREST`, `CITY`, `ASTEROID`)
     *   and +5% Hit on precision vantage tiles (`MOUNTAIN`, `SPACE`).
     * - Super Robots gain +5% Defense & +5% Attack Power on heavy/fortified tiles (`MOUNTAIN`, `BASE`, `CITY`).
     * - Command Ships gain +5% Defense & +5% Hit on uplink/orbital tiles (`BASE`, `SPACE`).
     */
    fun resolveCellOccupancyBonus(
        unit: MechaUnit,
        terrainGrid: List<List<TerrainType>>,
        overridePos: GridPos = unit.pos
    ): CellOccupancyBonus {
        val terrain = getTerrainAt(overridePos, terrainGrid)
        return resolveUnitTerrainBonus(unit, terrain, overridePos)
    }

    /**
     * Resolves the exact [CellOccupancyBonus] for a [MechaUnit] on a given [TerrainType].
     */
    fun resolveUnitTerrainBonus(
        unit: MechaUnit,
        terrain: TerrainType,
        pos: GridPos = unit.pos
    ): CellOccupancyBonus {
        val profile = getProfile(terrain)

        val extraEvasion = when {
            unit.archetype == MechaArchetype.REAL_ROBOT &&
                (terrain == TerrainType.FOREST || terrain == TerrainType.CITY || terrain == TerrainType.ASTEROID) -> 5
            else -> 0
        }

        val extraDefense = when {
            unit.archetype == MechaArchetype.SUPER_ROBOT &&
                (terrain == TerrainType.MOUNTAIN || terrain == TerrainType.BASE || terrain == TerrainType.CITY) -> 5
            unit.archetype == MechaArchetype.COMMAND_SHIP && terrain == TerrainType.BASE -> 5
            else -> 0
        }

        val extraAttack = when {
            unit.archetype == MechaArchetype.SUPER_ROBOT &&
                (terrain == TerrainType.MOUNTAIN || terrain == TerrainType.BASE || terrain == TerrainType.CITY) -> 5
            else -> 0
        }

        val extraHit = when {
            unit.archetype == MechaArchetype.REAL_ROBOT &&
                (terrain == TerrainType.MOUNTAIN || terrain == TerrainType.SPACE) -> 5
            unit.archetype == MechaArchetype.COMMAND_SHIP &&
                (terrain == TerrainType.BASE || terrain == TerrainType.SPACE) -> 5
            else -> 0
        }

        val extraCrit = when {
            unit.archetype == MechaArchetype.REAL_ROBOT &&
                (terrain == TerrainType.FOREST || terrain == TerrainType.CITY || terrain == TerrainType.ASTEROID) -> 5
            else -> 0
        }

        val adaptationBonus = extraEvasion + extraDefense + extraAttack + extraHit + extraCrit
        val adaptationGrade = if (adaptationBonus > 0) "S" else "A"

        val effectiveDef = profile.defenseBonusPercent + extraDefense
        val effectiveEva = profile.evasionBonusPercent + extraEvasion
        val effectiveAtk = profile.attackBonusPercent + extraAttack
        val effectiveHit = profile.hitBonusPercent + extraHit
        val effectiveCrit = profile.critBonusPercent + extraCrit

        val defenseMultiplier = 1.0 + (effectiveDef / 100.0)
        val attackMultiplier = 1.0 + (effectiveAtk / 100.0)
        val moveCost = if (unit.canFly) profile.airMoveCost else profile.groundMoveCost

        val hpRegen = (unit.maxHp * (profile.hpRegenPercent / 100.0)).roundToInt()
        val enRegen = (unit.maxEn * (profile.enRegenPercent / 100.0)).roundToInt() + 10

        val hitLabel = if (effectiveHit >= 0) "+$effectiveHit%" else "$effectiveHit%"
        val summary = "${terrain.displayName} [${terrain.shortCode}]: " +
            "DEF +$effectiveDef% • EVA +$effectiveEva% | ATK +$effectiveAtk% • HIT $hitLabel • CRT +$effectiveCrit% (Rank $adaptationGrade)"

        return CellOccupancyBonus(
            pos = pos,
            terrainType = terrain,
            baseDefenseBonusPercent = profile.defenseBonusPercent,
            baseEvasionBonusPercent = profile.evasionBonusPercent,
            baseAttackBonusPercent = profile.attackBonusPercent,
            baseHitBonusPercent = profile.hitBonusPercent,
            baseCritBonusPercent = profile.critBonusPercent,
            unitAdaptationBonusPercent = adaptationBonus,
            effectiveDefenseBonusPercent = effectiveDef,
            effectiveEvasionBonusPercent = effectiveEva,
            effectiveAttackBonusPercent = effectiveAtk,
            effectiveHitBonusPercent = effectiveHit,
            effectiveCritBonusPercent = effectiveCrit,
            defenseDamageMultiplier = defenseMultiplier,
            attackPowerMultiplier = attackMultiplier,
            movementCost = moveCost,
            hpRegenAmount = hpRegen,
            enRegenAmount = enRegen,
            adaptationGrade = adaptationGrade,
            summaryLabel = summary
        )
    }

    /**
     * Compares the occupied cells of [attacker] (offensive terrain advantage) and
     * [defender] (defensive & evasion terrain cover) during battle calculations.
     */
    fun compareBattlefieldCells(
        attacker: MechaUnit,
        defender: MechaUnit,
        terrainGrid: List<List<TerrainType>>
    ): BattlefieldTerrainComparison {
        val attackerCell = resolveCellOccupancyBonus(attacker, terrainGrid)
        val defenderCell = resolveCellOccupancyBonus(defender, terrainGrid)

        val netAccuracyShift = attackerCell.effectiveHitBonusPercent - defenderCell.effectiveEvasionBonusPercent
        val dmgReduction = defenderCell.effectiveDefenseBonusPercent

        return BattlefieldTerrainComparison(
            attackerCell = attackerCell,
            defenderCell = defenderCell,
            attackerAttackBonusPercent = attackerCell.effectiveAttackBonusPercent,
            attackerHitBonusPercent = attackerCell.effectiveHitBonusPercent,
            attackerCritBonusPercent = attackerCell.effectiveCritBonusPercent,
            defenderDefenseBonusPercent = defenderCell.effectiveDefenseBonusPercent,
            defenderEvasionBonusPercent = defenderCell.effectiveEvasionBonusPercent,
            netAccuracyShift = netAccuracyShift,
            defenderDamageReductionPercent = dmgReduction,
            comparisonSummary = "ATK [${attackerCell.terrainType.shortCode}: +${attackerCell.effectiveAttackBonusPercent}% ATK, " +
                "${if (attackerCell.effectiveHitBonusPercent >= 0) "+${attackerCell.effectiveHitBonusPercent}%" else "${attackerCell.effectiveHitBonusPercent}%"} HIT] " +
                "vs DEF [${defenderCell.terrainType.shortCode}: +${defenderCell.effectiveDefenseBonusPercent}% DEF, +${defenderCell.effectiveEvasionBonusPercent}% EVA]"
        )
    }
}
