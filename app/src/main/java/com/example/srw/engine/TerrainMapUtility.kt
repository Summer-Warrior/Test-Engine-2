package com.example.srw.engine

import com.example.srw.model.GridPos
import com.example.srw.model.MechaArchetype
import com.example.srw.model.MechaUnit
import com.example.srw.model.Pilot
import com.example.srw.model.TerrainType
import kotlin.math.roundToInt

/**
 * Immutable tactical definition of a map terrain tile, specifying its dodge (evasion)
 * and defense modifiers, movement costs, regeneration, and ASCII blueprint code.
 */
data class TerrainTileDefinition(
    val terrainType: TerrainType,
    val codeChar: Char,
    val name: String,
    val shortCode: String,
    val dodgeModifierPercent: Int,
    val defenseModifierPercent: Int,
    val attackModifierPercent: Int,
    val hitModifierPercent: Int,
    val critModifierPercent: Int,
    val groundMoveCost: Int,
    val airMoveCost: Int,
    val hpRegenPercent: Int,
    val enRegenPercent: Int,
    val providesCover: Boolean,
    val description: String
)

/**
 * Structured 2D tactical map backed by [TerrainType] rows with safe coordinate lookup,
 * tile-definition resolution, and spatial queries.
 */
data class TacticalTileMap(
    val width: Int,
    val height: Int,
    val grid: List<List<TerrainType>>
) {
    fun isInBounds(pos: GridPos): Boolean = pos.x in 0 until width && pos.y in 0 until height

    fun tileAt(pos: GridPos): TerrainType =
        grid.getOrNull(pos.y)?.getOrNull(pos.x) ?: TerrainType.PLAIN

    fun definitionAt(pos: GridPos): TerrainTileDefinition =
        TerrainMapUtility.getTileDefinition(tileAt(pos))

    fun positionsOf(terrainType: TerrainType): List<GridPos> = buildList {
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (tileAt(GridPos(x, y)) == terrainType) {
                    add(GridPos(x, y))
                }
            }
        }
    }

    fun coverPositions(): List<GridPos> = buildList {
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pos = GridPos(x, y)
                if (definitionAt(pos).providesCover) {
                    add(pos)
                }
            }
        }
    }

    fun withTile(pos: GridPos, terrainType: TerrainType): TacticalTileMap {
        if (!isInBounds(pos)) return this
        val updatedGrid = grid.mapIndexed { y, row ->
            if (y != pos.y) row
            else row.mapIndexed { x, existing -> if (x == pos.x) terrainType else existing }
        }
        return copy(grid = updatedGrid)
    }
}

/**
 * Resolved dodge and defense modifiers applied to a [Pilot] (and optionally their [MechaUnit])
 * occupying a specific terrain tile on the battlefield.
 */
data class PilotTerrainModifierResult(
    val pos: GridPos,
    val pilotId: String,
    val pilotName: String,
    val unitId: String? = null,
    val unitName: String? = null,
    val tile: TerrainTileDefinition,
    val baseDodgeModifierPercent: Int,
    val baseDefenseModifierPercent: Int,
    val archetypeDodgeBonusPercent: Int,
    val archetypeDefenseBonusPercent: Int,
    val effectiveDodgeModifierPercent: Int,
    val effectiveDefenseModifierPercent: Int,
    val basePilotEvadeStat: Int,
    val effectivePilotEvadeStat: Int,
    val baseArmor: Int?,
    val effectiveArmor: Int?,
    val defenseDamageMultiplier: Double,
    val incomingDamageScale: Double,
    val adaptationGrade: String,
    val summary: String
)

/**
 * Reusable map utility that defines tactical terrain tiles (including Forest, Water, City,
 * Plain, Mountain, Command Base, Space, and Asteroid), constructs/queries 2D tile maps,
 * and applies tile dodge and defense modifiers to pilots and units occupying those tiles.
 */
object TerrainMapUtility {

    private val terrainSystem = GridTerrainSystem()

    val FOREST: TerrainTileDefinition = buildDefinition(TerrainType.FOREST, codeChar = 'F', name = "Forest")
    val WATER: TerrainTileDefinition = buildDefinition(TerrainType.SEA, codeChar = 'W', name = "Water")
    val CITY: TerrainTileDefinition = buildDefinition(TerrainType.CITY, codeChar = 'C', name = "City")
    val PLAIN: TerrainTileDefinition = buildDefinition(TerrainType.PLAIN, codeChar = 'P', name = "Plain")
    val MOUNTAIN: TerrainTileDefinition = buildDefinition(TerrainType.MOUNTAIN, codeChar = 'M', name = "Mountain")
    val BASE: TerrainTileDefinition = buildDefinition(TerrainType.BASE, codeChar = 'B', name = "Command Base")
    val SPACE: TerrainTileDefinition = buildDefinition(TerrainType.SPACE, codeChar = 'O', name = "Space")
    val ASTEROID: TerrainTileDefinition = buildDefinition(TerrainType.ASTEROID, codeChar = 'A', name = "Asteroid")

    val ALL_TILE_DEFINITIONS: List<TerrainTileDefinition> = listOf(
        FOREST,
        WATER,
        CITY,
        MOUNTAIN,
        BASE,
        PLAIN,
        SPACE,
        ASTEROID
    )

    private val definitionByType: Map<TerrainType, TerrainTileDefinition> =
        ALL_TILE_DEFINITIONS.associateBy { it.terrainType }

    private fun buildDefinition(
        terrainType: TerrainType,
        codeChar: Char,
        name: String
    ): TerrainTileDefinition {
        val profile = terrainSystem.getProfile(terrainType)
        return TerrainTileDefinition(
            terrainType = terrainType,
            codeChar = codeChar,
            name = name,
            shortCode = terrainType.shortCode,
            dodgeModifierPercent = profile.evasionBonusPercent,
            defenseModifierPercent = profile.defenseBonusPercent,
            attackModifierPercent = profile.attackBonusPercent,
            hitModifierPercent = profile.hitBonusPercent,
            critModifierPercent = profile.critBonusPercent,
            groundMoveCost = profile.groundMoveCost,
            airMoveCost = profile.airMoveCost,
            hpRegenPercent = profile.hpRegenPercent,
            enRegenPercent = profile.enRegenPercent,
            providesCover = profile.providesCoverFromRanged,
            description = profile.tacticalDescription
        )
    }

    /**
     * Retrieves the [TerrainTileDefinition] for a given [TerrainType].
     */
    fun getTileDefinition(terrainType: TerrainType): TerrainTileDefinition {
        return definitionByType[terrainType] ?: PLAIN
    }

    /**
     * Resolves a [TerrainTileDefinition] from an ASCII blueprint character
     * (`F`=Forest, `W`/`S`=Water, `C`=City, `M`=Mountain, `B`=Base, `P`/`.`=Plain, `A`=Asteroid, `O`=Space).
     */
    fun getTileDefinitionByCode(codeChar: Char): TerrainTileDefinition {
        return when (codeChar.uppercaseChar()) {
            'F' -> FOREST
            'W', 'S' -> WATER
            'C', 'U' -> CITY
            'M' -> MOUNTAIN
            'B' -> BASE
            'A' -> ASTEROID
            'O' -> SPACE
            'P', '.' -> PLAIN
            else -> PLAIN
        }
    }

    /**
     * Resolves a [TerrainTileDefinition] by common name or short code (case-insensitive),
     * such as `"forest"`, `"water"`, `"sea"`, `"city"`, `"urban"`, `"mountain"`, `"base"`, `"plain"`.
     */
    fun getTileDefinitionByName(name: String): TerrainTileDefinition? {
        return when (name.trim().lowercase()) {
            "forest", "woods", "dense forest", "frs" -> FOREST
            "water", "sea", "ocean", "ocean trench", "lake", "river" -> WATER
            "city", "urban", "urban sector", "urb" -> CITY
            "plain", "plains", "plains sector", "pln" -> PLAIN
            "mountain", "mountains", "ridge", "highland", "highland ridge", "mtn" -> MOUNTAIN
            "base", "command base", "aegis command base", "bse" -> BASE
            "space", "orbit", "zero-g orbit", "spc" -> SPACE
            "asteroid", "debris", "debris reef", "ast" -> ASTEROID
            else -> null
        }
    }

    /**
     * Programmatically creates a [TacticalTileMap] of [width] x [height] filled with [defaultTerrain]
     * and applies any coordinate-specific [tileOverrides].
     */
    fun createMap(
        width: Int,
        height: Int,
        defaultTerrain: TerrainType = TerrainType.PLAIN,
        tileOverrides: Map<GridPos, TerrainType> = emptyMap()
    ): TacticalTileMap {
        val safeW = width.coerceAtLeast(1)
        val safeH = height.coerceAtLeast(1)
        val grid = List(safeH) { y ->
            List(safeW) { x ->
                tileOverrides[GridPos(x, y)] ?: defaultTerrain
            }
        }
        return TacticalTileMap(width = safeW, height = safeH, grid = grid)
    }

    /**
     * Parses a rectangular [TacticalTileMap] from ASCII blueprint rows (ignoring spaces).
     * Example row: `"P F C W M B"` -> Plain, Forest, City, Water, Mountain, Base.
     */
    fun fromAsciiBlueprint(
        rows: List<String>,
        defaultTerrain: TerrainType = TerrainType.PLAIN
    ): TacticalTileMap {
        val sanitizedRows = rows
            .map { line -> line.filterNot { it.isWhitespace() } }
            .filter { it.isNotEmpty() }
        if (sanitizedRows.isEmpty()) {
            return createMap(width = 1, height = 1, defaultTerrain = defaultTerrain)
        }
        val width = sanitizedRows.maxOf { it.length }
        val grid = sanitizedRows.map { row ->
            List(width) { x ->
                if (x < row.length) getTileDefinitionByCode(row[x]).terrainType else defaultTerrain
            }
        }
        return TacticalTileMap(width = width, height = grid.size, grid = grid)
    }

    /**
     * Wraps an existing `List<List<TerrainType>>` grid into a [TacticalTileMap].
     */
    fun fromGrid(grid: List<List<TerrainType>>): TacticalTileMap {
        if (grid.isEmpty() || grid.first().isEmpty()) {
            return createMap(1, 1)
        }
        return TacticalTileMap(
            width = grid.first().size,
            height = grid.size,
            grid = grid
        )
    }

    /**
     * Applies the dodge and defense modifiers of a specific [TerrainType] directly to a [Pilot],
     * optionally incorporating [archetype] terrain adaptation and [baseArmor] scaling.
     */
    fun applyModifiersToPilot(
        pilot: Pilot,
        terrain: TerrainType,
        pos: GridPos = GridPos(0, 0),
        archetype: MechaArchetype? = null,
        baseArmor: Int? = null,
        unitId: String? = null,
        unitName: String? = null
    ): PilotTerrainModifierResult {
        val tile = getTileDefinition(terrain)

        val extraDodge = when {
            archetype == MechaArchetype.REAL_ROBOT &&
                (terrain == TerrainType.FOREST || terrain == TerrainType.CITY || terrain == TerrainType.ASTEROID) -> 5
            else -> 0
        }
        val extraDefense = when {
            archetype == MechaArchetype.SUPER_ROBOT &&
                (terrain == TerrainType.MOUNTAIN || terrain == TerrainType.BASE || terrain == TerrainType.CITY) -> 5
            archetype == MechaArchetype.COMMAND_SHIP && terrain == TerrainType.BASE -> 5
            else -> 0
        }

        val effectiveDodge = tile.dodgeModifierPercent + extraDodge
        val effectiveDefense = tile.defenseModifierPercent + extraDefense
        val defenseMultiplier = 1.0 + (effectiveDefense / 100.0)
        val incomingScale = 1.0 / defenseMultiplier

        val effectiveEvadeStat = (pilot.evadeStat + effectiveDodge).coerceAtLeast(0)
        val effectiveArmor = baseArmor?.let { (it * defenseMultiplier).roundToInt() }
        val adaptationGrade = if (extraDodge > 0 || extraDefense > 0) "S" else "A"

        val armorSnippet = if (baseArmor != null && effectiveArmor != null) {
            " | ARM $baseArmor->$effectiveArmor"
        } else {
            ""
        }
        val summary = "${pilot.name} on ${tile.name} (${tile.shortCode}): " +
            "DODGE +$effectiveDodge% (EVA ${pilot.evadeStat}->$effectiveEvadeStat) • " +
            "DEF +$effectiveDefense%$armorSnippet [Rank $adaptationGrade]"

        return PilotTerrainModifierResult(
            pos = pos,
            pilotId = pilot.id,
            pilotName = pilot.name,
            unitId = unitId,
            unitName = unitName,
            tile = tile,
            baseDodgeModifierPercent = tile.dodgeModifierPercent,
            baseDefenseModifierPercent = tile.defenseModifierPercent,
            archetypeDodgeBonusPercent = extraDodge,
            archetypeDefenseBonusPercent = extraDefense,
            effectiveDodgeModifierPercent = effectiveDodge,
            effectiveDefenseModifierPercent = effectiveDefense,
            basePilotEvadeStat = pilot.evadeStat,
            effectivePilotEvadeStat = effectiveEvadeStat,
            baseArmor = baseArmor,
            effectiveArmor = effectiveArmor,
            defenseDamageMultiplier = defenseMultiplier,
            incomingDamageScale = incomingScale,
            adaptationGrade = adaptationGrade,
            summary = summary
        )
    }

    /**
     * Applies dodge and defense modifiers for a [Pilot] standing at [pos] on a [TacticalTileMap].
     */
    fun applyModifiersToPilot(
        pilot: Pilot,
        pos: GridPos,
        tileMap: TacticalTileMap,
        archetype: MechaArchetype? = null,
        baseArmor: Int? = null
    ): PilotTerrainModifierResult {
        return applyModifiersToPilot(
            pilot = pilot,
            terrain = tileMap.tileAt(pos),
            pos = pos,
            archetype = archetype,
            baseArmor = baseArmor
        )
    }

    /**
     * Evaluates the terrain tile occupied by [unit] (or [overridePos]) on [terrainGrid]
     * and returns the pilot's effective dodge and defense modifiers.
     */
    fun applyOccupiedTileModifiers(
        unit: MechaUnit,
        terrainGrid: List<List<TerrainType>>,
        overridePos: GridPos = unit.pos
    ): PilotTerrainModifierResult {
        val terrain = terrainSystem.getTerrainAt(overridePos, terrainGrid)
        return applyModifiersToPilot(
            pilot = unit.pilot,
            terrain = terrain,
            pos = overridePos,
            archetype = unit.archetype,
            baseArmor = unit.armor,
            unitId = unit.id,
            unitName = unit.name
        )
    }

    /**
     * Evaluates the terrain tile occupied by [unit] (or [overridePos]) on [tileMap]
     * and returns the pilot's effective dodge and defense modifiers.
     */
    fun applyOccupiedTileModifiers(
        unit: MechaUnit,
        tileMap: TacticalTileMap,
        overridePos: GridPos = unit.pos
    ): PilotTerrainModifierResult {
        return applyOccupiedTileModifiers(unit = unit, terrainGrid = tileMap.grid, overridePos = overridePos)
    }

    /**
     * Computes occupied-tile dodge and defense modifiers for every living unit in [units],
     * keyed by `MechaUnit.id`.
     */
    fun applyModifiersToRoster(
        units: List<MechaUnit>,
        terrainGrid: List<List<TerrainType>>
    ): Map<String, PilotTerrainModifierResult> {
        return units
            .filter { it.isAlive() }
            .associate { unit ->
                unit.id to applyOccupiedTileModifiers(unit, terrainGrid)
            }
    }

    /**
     * Overload of [applyModifiersToRoster] accepting a [TacticalTileMap].
     */
    fun applyModifiersToRoster(
        units: List<MechaUnit>,
        tileMap: TacticalTileMap
    ): Map<String, PilotTerrainModifierResult> = applyModifiersToRoster(units, tileMap.grid)

    /**
     * Ranks [candidatePositions] for [unit] and returns the tile offering the strongest combined
     * dodge and defense modifier (preferring higher dodge for Real Robots and higher defense for
     * Super Robots / Command Ships on ties).
     */
    fun findBestCoverTile(
        unit: MechaUnit,
        candidatePositions: Collection<GridPos>,
        terrainGrid: List<List<TerrainType>>
    ): PilotTerrainModifierResult? {
        if (candidatePositions.isEmpty()) return null
        return candidatePositions
            .map { pos -> applyOccupiedTileModifiers(unit, terrainGrid, overridePos = pos) }
            .maxWithOrNull(
                compareBy<PilotTerrainModifierResult>(
                    { it.effectiveDodgeModifierPercent + it.effectiveDefenseModifierPercent },
                    {
                        if (unit.archetype == MechaArchetype.REAL_ROBOT) it.effectiveDodgeModifierPercent
                        else it.effectiveDefenseModifierPercent
                    },
                    { it.tile.hpRegenPercent }
                )
            )
    }

    /**
     * Overload of [findBestCoverTile] accepting a [TacticalTileMap].
     */
    fun findBestCoverTile(
        unit: MechaUnit,
        candidatePositions: Collection<GridPos>,
        tileMap: TacticalTileMap
    ): PilotTerrainModifierResult? = findBestCoverTile(unit, candidatePositions, tileMap.grid)
}
