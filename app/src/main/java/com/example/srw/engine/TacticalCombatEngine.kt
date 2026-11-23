package com.example.srw.engine

import com.example.srw.model.*
import java.util.PriorityQueue

object TacticalCombatEngine {

    val defaultCombatManager = CombatManager()

    /**
     * Computes all reachable GridPos tiles and their movement cost using Dijkstra's algorithm.
     * Friendly units can be traversed through (but not stopped on); enemy units block movement.
     */
    fun computeReachableTiles(
        mover: MechaUnit,
        terrainGrid: List<List<TerrainType>>,
        allUnits: List<MechaUnit>
    ): Map<GridPos, Int> {
        val height = terrainGrid.size
        val width = if (height > 0) terrainGrid[0].size else 0
        val maxMove = mover.effectiveMoveRange

        val occupiedByEnemy = allUnits
            .filter { it.isAlive() && it.faction != mover.faction }
            .map { it.pos }
            .toSet()

        val occupiedByAlly = allUnits
            .filter { it.isAlive() && it.id != mover.id && it.faction == mover.faction }
            .map { it.pos }
            .toSet()

        val costSoFar = mutableMapOf<GridPos, Int>()
        val frontier = PriorityQueue<Pair<GridPos, Int>>(compareBy { it.second })

        costSoFar[mover.pos] = 0
        frontier.add(mover.pos to 0)

        while (frontier.isNotEmpty()) {
            val (current, currentCost) = frontier.poll()
            if (currentCost > (costSoFar[current] ?: Int.MAX_VALUE)) continue

            for (next in current.neighbors(width, height)) {
                if (next in occupiedByEnemy) continue
                val terrain = terrainGrid[next.y][next.x]
                val stepCost = if (mover.canFly) terrain.moveCostSky else terrain.moveCostGround
                val newCost = currentCost + stepCost
                if (newCost <= maxMove && newCost < (costSoFar[next] ?: Int.MAX_VALUE)) {
                    costSoFar[next] = newCost
                    frontier.add(next to newCost)
                }
            }
        }

        // Remove tiles occupied by other friendly units (can pass through, cannot end move on)
        return costSoFar.filterKeys { it !in occupiedByAlly }
    }

    /**
     * Reconstructs shortest path from mover.pos to destination for drawing the tactical trajectory line.
     */
    fun findShortestPath(
        mover: MechaUnit,
        destination: GridPos,
        terrainGrid: List<List<TerrainType>>,
        allUnits: List<MechaUnit>
    ): List<GridPos> {
        if (mover.pos == destination) return listOf(mover.pos)
        val height = terrainGrid.size
        val width = if (height > 0) terrainGrid[0].size else 0
        val occupiedByEnemy = allUnits
            .filter { it.isAlive() && it.faction != mover.faction }
            .map { it.pos }
            .toSet()

        val costSoFar = mutableMapOf<GridPos, Int>()
        val cameFrom = mutableMapOf<GridPos, GridPos>()
        val frontier = PriorityQueue<Pair<GridPos, Int>>(compareBy { it.second })

        costSoFar[mover.pos] = 0
        frontier.add(mover.pos to 0)

        while (frontier.isNotEmpty()) {
            val (current, currentCost) = frontier.poll()
            if (current == destination) break
            for (next in current.neighbors(width, height)) {
                if (next in occupiedByEnemy && next != destination) continue
                val terrain = terrainGrid[next.y][next.x]
                val stepCost = if (mover.canFly) terrain.moveCostSky else terrain.moveCostGround
                val newCost = currentCost + stepCost
                if (newCost < (costSoFar[next] ?: Int.MAX_VALUE)) {
                    costSoFar[next] = newCost
                    cameFrom[next] = current
                    frontier.add(next to newCost)
                }
            }
        }

        val path = mutableListOf<GridPos>()
        var curr: GridPos? = destination
        while (curr != null) {
            path.add(0, curr)
            if (curr == mover.pos) break
            curr = cameFrom[curr]
        }
        return if (path.firstOrNull() == mover.pos) path else listOf(mover.pos, destination)
    }

    /**
     * Computes all tiles within a weapon's minRange..maxRange from origin.
     */
    fun computeWeaponRangeTiles(
        origin: GridPos,
        weapon: Weapon,
        mapWidth: Int,
        mapHeight: Int
    ): Set<GridPos> {
        val result = mutableSetOf<GridPos>()
        for (y in 0 until mapHeight) {
            for (x in 0 until mapWidth) {
                val p = GridPos(x, y)
                val dist = origin.distanceTo(p)
                if (dist in weapon.minRange..weapon.maxRange) {
                    result.add(p)
                }
            }
        }
        return result
    }

    fun calculateCommandAuraBonus(unit: MechaUnit, allUnits: List<MechaUnit>): Int =
        defaultCombatManager.calculateCommandAuraBonus(unit, allUnits)

    fun selectBestCounterWeapon(defender: MechaUnit, distance: Int): Weapon? {
        return defender.availableWeapons(afterMove = false, distance = distance)
            .maxByOrNull { it.effectivePower(defender.upgradeWeapon) }
    }

    fun calculateCombatForecast(
        attacker: MechaUnit,
        defender: MechaUnit,
        attackerWeapon: Weapon,
        defenderWeapon: Weapon?,
        defenderStance: DefendStance,
        terrainGrid: List<List<TerrainType>>,
        allUnits: List<MechaUnit>
    ): CombatForecast = defaultCombatManager.buildCombatForecast(
        attacker = attacker,
        defender = defender,
        attackerWeapon = attackerWeapon,
        defenderWeapon = defenderWeapon,
        defenderStance = defenderStance,
        terrainGrid = terrainGrid,
        allUnits = allUnits
    )

    fun resolveBattleExchange(
        forecast: CombatForecast,
        forcedPrimaryOutcome: StrikeOutcome? = null,
        isSandboxPreview: Boolean = false
    ): BattleCutsceneSequence = defaultCombatManager.resolveCombatExchange(
        forecast = forecast,
        forcedPrimaryOutcome = forcedPrimaryOutcome,
        isSandboxPreview = isSandboxPreview
    )
}
