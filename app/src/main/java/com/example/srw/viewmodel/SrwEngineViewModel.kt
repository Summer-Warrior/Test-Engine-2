package com.example.srw.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.srw.data.SrwRosterCatalog
import com.example.srw.engine.*
import com.example.srw.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.min

enum class EngineTab {
    TACTICAL_MAP,
    CINEMATIC_STUDIO,
    HANGAR_UPGRADE,
    COMBAT_LOG
}

enum class TacticalInputMode {
    IDLE_BROWSE,
    SELECTING_MOVE,
    POST_MOVE_COMMAND,
    SELECTING_WEAPON,
    SELECTING_TARGET,
    SELECTING_SPIRIT,
    SELECTING_MORALE_EVENT,
    SELECTING_REPAIR_TARGET,
    SHOWING_COMBAT_FORECAST
}

data class SandboxStudioConfig(
    val attackerIndex: Int = 0,
    val defenderIndex: Int = 6,
    val weaponIndex: Int = 2,
    val terrain: TerrainType = TerrainType.CITY,
    val forcedOutcome: StrikeOutcome? = null,
    val enableValor: Boolean = true,
    val enableCounter: Boolean = true
)

data class SrwEngineUiState(
    val activeTab: EngineTab = EngineTab.TACTICAL_MAP,
    val scenarios: List<ScenarioMission> = emptyList(),
    val currentScenarioIndex: Int = 0,
    val turnNumber: Int = 1,
    val currentPhase: Faction = Faction.ALLY,
    val phaseStage: TurnPhaseStage = TurnPhaseStage.PLAYER_PHASE,
    val initiativeOrder: List<InitiativeEntry> = emptyList(),
    val credits: Int = 18000,
    val terrainGrid: List<List<TerrainType>> = emptyList(),
    val units: List<MechaUnit> = emptyList(),
    val selectedPos: GridPos = GridPos(3, 6),
    val selectedUnitId: String? = "ally_kaiser",
    val selectedHangarUnitId: String = "ally_kaiser",
    val activeDevelopmentCategory: DevelopmentCategory = DevelopmentCategory.UNIT_FRAME,
    val preMovePos: GridPos? = null,
    val inputMode: TacticalInputMode = TacticalInputMode.IDLE_BROWSE,
    val reachableTiles: Map<GridPos, Int> = emptyMap(),
    val movementTrajectory: List<GridPos> = emptyList(),
    val selectedWeapon: Weapon? = null,
    val weaponRangeTiles: Set<GridPos> = emptySet(),
    val repairRangeTiles: Set<GridPos> = emptySet(),
    val combatForecast: CombatForecast? = null,
    val forecastDamageBreakdown: DamageCalculationBreakdown? = null,
    val activeCutscene: BattleCutsceneSequence? = null,
    val combatLogs: List<CombatLogEntry> = emptyList(),
    val recentMoraleChanges: List<UnitMoraleChange> = emptyList(),
    val lastCompletedBattleSequence: BattleCutsceneSequence? = null,
    val completedBattleCount: Int = 0,
    val statusBannerMessage: String = "PLAYER PHASE — Select a mobile weapon to issue tactical orders.",
    val isVictory: Boolean = false,
    val isDefeat: Boolean = false,
    val isEnemyAiThinking: Boolean = false,
    val sandboxConfig: SandboxStudioConfig = SandboxStudioConfig()
) {
    val currentScenario: ScenarioMission?
        get() = scenarios.getOrNull(currentScenarioIndex)

    val selectedUnit: MechaUnit?
        get() = units.find { it.id == selectedUnitId && it.isAlive() }

    val unitAtCursor: MechaUnit?
        get() = units.find { it.isAlive() && it.pos == selectedPos }

    val terrainAtCursor: TerrainType
        get() = terrainGrid.getOrNull(selectedPos.y)?.getOrNull(selectedPos.x) ?: TerrainType.PLAIN
}

class SrwEngineViewModel(
    val combatManager: CombatManager = CombatManager()
) : ViewModel() {

    val developmentSystem: DevelopmentSystem = combatManager.developmentSystem
    val pilotMoraleSystem: PilotMoraleSystem = combatManager.pilotMoraleSystem
    val pilotProgressionSystem: PilotProgressionSystem = combatManager.pilotProgressionSystem
    val spiritCommandSystem: SpiritCommandSystem = combatManager.spiritCommandSystem

    private val _uiState = MutableStateFlow(SrwEngineUiState())
    val uiState: StateFlow<SrwEngineUiState> = _uiState.asStateFlow()

    val sandboxAllTemplates: List<MechaUnit> = listOf(
        SrwRosterCatalog.createKaiserBlaze(GridPos(2, 5)).copy(
            pilot = SrwRosterCatalog.createKaiserBlaze(GridPos(2, 5)).pilot.copy(will = 135)
        ),
        SrwRosterCatalog.createAstralGundam(GridPos(2, 5)).copy(
            pilot = SrwRosterCatalog.createAstralGundam(GridPos(2, 5)).pilot.copy(will = 135)
        ),
        SrwRosterCatalog.createValkyrieZero(GridPos(2, 5)).copy(
            pilot = SrwRosterCatalog.createValkyrieZero(GridPos(2, 5)).pilot.copy(will = 135)
        ),
        SrwRosterCatalog.createAegisBattleship(GridPos(2, 5)).copy(
            pilot = SrwRosterCatalog.createAegisBattleship(GridPos(2, 5)).pilot.copy(will = 135)
        ),
        SrwRosterCatalog.createEnemyVanguard("sandbox_zaku", "Ace", GridPos(6, 5)).copy(
            pilot = SrwRosterCatalog.createEnemyVanguard("sandbox_zaku", "Ace", GridPos(6, 5)).pilot.copy(will = 130)
        ),
        SrwRosterCatalog.createEnemyGravDestroyer("sandbox_grav", "Titan", GridPos(6, 5)).copy(
            pilot = SrwRosterCatalog.createEnemyGravDestroyer("sandbox_grav", "Titan", GridPos(6, 5)).pilot.copy(will = 130)
        ),
        SrwRosterCatalog.createBossEmperorZeph(GridPos(6, 5)).copy(
            pilot = SrwRosterCatalog.createBossEmperorZeph(GridPos(6, 5)).pilot.copy(will = 140)
        )
    )

    init {
        val scenarios = SrwRosterCatalog.getScenarios()
        loadScenario(0, scenarios)
    }

    fun selectTab(tab: EngineTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun selectHangarUnit(unitId: String) {
        _uiState.update { it.copy(selectedHangarUnitId = unitId) }
    }

    fun selectDevelopmentCategory(category: DevelopmentCategory) {
        _uiState.update { it.copy(activeDevelopmentCategory = category) }
    }

    fun loadScenario(index: Int, allScenarios: List<ScenarioMission> = _uiState.value.scenarios) {
        val mission = allScenarios.getOrNull(index) ?: return
        val existingUpgrades = _uiState.value.units.associateBy { it.id }

        val mergedAllies = mission.initialAllies.map { fresh ->
            val prev = existingUpgrades[fresh.id]
            if (prev != null) {
                val mergedWeapons = prev.weapons.map { pw ->
                    pw.copy(currentAmmo = pw.maxAmmo)
                }
                val rebuilt = fresh.copy(
                    name = prev.name,
                    modelCode = prev.modelCode,
                    upgradeHp = prev.upgradeHp,
                    upgradeEn = prev.upgradeEn,
                    upgradeArmor = prev.upgradeArmor,
                    upgradeMobility = prev.upgradeMobility,
                    upgradeWeapon = prev.upgradeWeapon,
                    frameTier = prev.frameTier,
                    armorPlating = prev.armorPlating,
                    barrierUpgradeLevel = prev.barrierUpgradeLevel,
                    magicLevel = prev.magicLevel,
                    elementalAffinity = prev.elementalAffinity,
                    unlockedSpells = prev.unlockedSpells,
                    unlockedPrototypeWeapon = prev.unlockedPrototypeWeapon,
                    weapons = mergedWeapons
                )
                val baseDeployWill = if (rebuilt.hasFullCustomBonus) 115 else 105
                val deployWill = baseDeployWill +
                    (if (prev.pilot.hasPassive(PilotPassiveAbility.MORALE_SURGE)) 8 else 0)
                val carriedPilot = rebuilt.pilot.copy(
                    xp = prev.pilot.xp,
                    totalXpEarned = prev.pilot.totalXpEarned,
                    level = prev.pilot.level,
                    meleeStat = prev.pilot.meleeStat,
                    rangedStat = prev.pilot.rangedStat,
                    hitStat = prev.pilot.hitStat,
                    evadeStat = prev.pilot.evadeStat,
                    maxSp = prev.pilot.maxSp,
                    kills = prev.pilot.kills,
                    passiveAbilities = prev.pilot.passiveAbilities,
                    spiritUpgrades = prev.pilot.spiritUpgrades,
                    will = min(170, deployWill)
                )
                val unitWithPilot = rebuilt.copy(pilot = carriedPilot)
                unitWithPilot.copy(
                    currentHp = unitWithPilot.maxHp,
                    currentEn = unitWithPilot.maxEn,
                    pilot = unitWithPilot.pilot.copy(
                        currentSp = unitWithPilot.effectiveMaxSp
                    )
                )
            } else fresh
        }

        val combinedUnits = mergedAllies + mission.initialEnemies
        val initiativeQueue = combatManager.computeInitiativeOrder(combinedUnits)
        val leadAlly = combatManager.getNextReadyUnitByInitiative(combinedUnits, Faction.ALLY)
            ?: mergedAllies.firstOrNull()

        _uiState.update { state ->
            state.copy(
                scenarios = allScenarios,
                currentScenarioIndex = index,
                turnNumber = 1,
                currentPhase = Faction.ALLY,
                phaseStage = TurnPhaseStage.PLAYER_PHASE,
                initiativeOrder = initiativeQueue,
                terrainGrid = mission.terrainGrid,
                units = combinedUnits,
                selectedPos = leadAlly?.pos ?: GridPos(3, 6),
                selectedUnitId = leadAlly?.id,
                preMovePos = null,
                inputMode = TacticalInputMode.IDLE_BROWSE,
                reachableTiles = emptyMap(),
                movementTrajectory = emptyList(),
                selectedWeapon = null,
                weaponRangeTiles = emptySet(),
                repairRangeTiles = emptySet(),
                combatForecast = null,
                forecastDamageBreakdown = null,
                activeCutscene = null,
                isVictory = false,
                isDefeat = false,
                isEnemyAiThinking = false,
                statusBannerMessage = "${mission.code}: ${mission.title} — Initiative #1: ${leadAlly?.name ?: "Ready"}!",
                combatLogs = listOf(
                    CombatLogEntry(
                        id = System.currentTimeMillis(),
                        turn = 1,
                        phase = Faction.ALLY,
                        headline = "${mission.code} INITIALIZED: ${mission.title}",
                        detail = "${mission.briefing} Initiative Lead: ${leadAlly?.name} (${leadAlly?.pilot?.name})."
                    )
                )
            )
        }
    }

    fun selectUnitFromInitiativeQueue(unitId: String) {
        val state = _uiState.value
        if (state.isEnemyAiThinking || state.activeCutscene != null) return
        val unit = state.units.find { it.id == unitId && it.isAlive() } ?: return
        val initEntry = combatManager.calculateUnitInitiative(unit)
        _uiState.update {
            it.copy(
                selectedPos = unit.pos,
                selectedUnitId = unit.id,
                inputMode = TacticalInputMode.IDLE_BROWSE,
                reachableTiles = emptyMap(),
                weaponRangeTiles = emptySet(),
                statusBannerMessage = "${unit.name} (${unit.pilot.name}) • INIT ${initEntry.initiativeScore} [${unit.elementalAffinity.displayName}]"
            )
        }
    }

    fun onTileTapped(pos: GridPos) {
        val state = _uiState.value
        if (state.isEnemyAiThinking || state.activeCutscene != null) return

        when (state.inputMode) {
            TacticalInputMode.IDLE_BROWSE -> {
                val tappedUnit = state.units.find { it.isAlive() && it.pos == pos }
                _uiState.update {
                    it.copy(
                        selectedPos = pos,
                        selectedUnitId = tappedUnit?.id ?: it.selectedUnitId,
                        statusBannerMessage = if (tappedUnit != null) {
                            val init = combatManager.calculateUnitInitiative(tappedUnit)
                            "${tappedUnit.name} [${tappedUnit.elementalAffinity.kanji}] • INIT ${init.initiativeScore} • HP ${tappedUnit.currentHp}/${tappedUnit.maxHp}"
                        } else {
                            val terrain = it.terrainGrid[pos.y][pos.x]
                            val hitLabel = if (terrain.hitBonusPercent >= 0) "+${terrain.hitBonusPercent}%" else "${terrain.hitBonusPercent}%"
                            "(${pos.x},${pos.y}) ${terrain.displayName}: DEF +${terrain.defBonusPercent}% EVA +${terrain.evadeBonusPercent}% | ATK +${terrain.attackBonusPercent}% HIT $hitLabel CRT +${terrain.critBonusPercent}%"
                        }
                    )
                }
            }

            TacticalInputMode.SELECTING_MOVE -> {
                val mover = state.selectedUnit ?: return
                if (pos in state.reachableTiles) {
                    val path = TacticalCombatEngine.findShortestPath(
                        mover = mover,
                        destination = pos,
                        terrainGrid = state.terrainGrid,
                        allUnits = state.units
                    )
                    val updatedUnits = state.units.map { u ->
                        if (u.id == mover.id) u.copy(pos = pos, hasMoved = pos != mover.pos) else u
                    }
                    _uiState.update {
                        it.copy(
                            units = updatedUnits,
                            initiativeOrder = combatManager.computeInitiativeOrder(updatedUnits),
                            selectedPos = pos,
                            preMovePos = mover.pos,
                            movementTrajectory = path,
                            reachableTiles = emptyMap(),
                            inputMode = TacticalInputMode.POST_MOVE_COMMAND,
                            statusBannerMessage = "${mover.name} moved to (${pos.x}, ${pos.y}). Choose Post-Move [P] Attack, Spirit/Magic, or Standby."
                        )
                    }
                } else {
                    cancelCurrentOrder()
                }
            }

            TacticalInputMode.SELECTING_TARGET -> {
                val attacker = state.selectedUnit ?: return
                val weapon = state.selectedWeapon ?: return
                val targetUnit = state.units.find {
                    it.isAlive() && it.faction != attacker.faction && it.pos == pos
                }

                if (targetUnit != null && pos in state.weaponRangeTiles) {
                    val dist = attacker.pos.distanceTo(targetUnit.pos)
                    val counterWeapon = TacticalCombatEngine.selectBestCounterWeapon(targetUnit, dist)
                    val forecast = combatManager.buildCombatForecast(
                        attacker = attacker,
                        defender = targetUnit,
                        attackerWeapon = weapon,
                        defenderWeapon = counterWeapon,
                        defenderStance = DefendStance.COUNTER,
                        terrainGrid = state.terrainGrid,
                        allUnits = state.units
                    )
                    val breakdown = combatManager.calculateDetailedDamage(
                        attacker = attacker,
                        defender = targetUnit,
                        weapon = weapon,
                        defenderTerrain = forecast.defenderTerrain,
                        defenderStance = DefendStance.COUNTER,
                        isCritical = false,
                        attackerTerrain = forecast.attackerTerrain
                    )
                    _uiState.update {
                        it.copy(
                            selectedPos = pos,
                            combatForecast = forecast,
                            forecastDamageBreakdown = breakdown,
                            inputMode = TacticalInputMode.SHOWING_COMBAT_FORECAST,
                            statusBannerMessage = "Combat Forecast Locked: ${attacker.name} vs ${targetUnit.name}"
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            statusBannerMessage = "Select a valid hostile unit inside the crimson weapon range."
                        )
                    }
                }
            }

            TacticalInputMode.SELECTING_REPAIR_TARGET -> {
                val healer = state.selectedUnit ?: return
                val allyTarget = state.units.find {
                    it.isAlive() && it.faction == Faction.ALLY && it.pos == pos
                }
                if (allyTarget != null && pos in state.repairRangeTiles) {
                    val healedHp = min(allyTarget.maxHp, allyTarget.currentHp + 2800)
                    val healedEn = min(allyTarget.maxEn, allyTarget.currentEn + 60)
                    val updatedUnits = state.units.map { u ->
                        when (u.id) {
                            allyTarget.id -> u.copy(currentHp = healedHp, currentEn = healedEn)
                            healer.id -> u.copy(hasMoved = true, hasActed = true)
                            else -> u
                        }
                    }
                    addCombatLog(
                        headline = "${healer.name} repaired ${allyTarget.name}",
                        detail = "Restored +2,800 HP and +60 EN via Aegis Repair Drones."
                    )
                    _uiState.update {
                        it.copy(
                            units = updatedUnits,
                            initiativeOrder = combatManager.computeInitiativeOrder(updatedUnits),
                            repairRangeTiles = emptySet(),
                            inputMode = TacticalInputMode.IDLE_BROWSE,
                            statusBannerMessage = "Repaired & resupplied ${allyTarget.name}!"
                        )
                    }
                    checkAutoPhaseAdvance()
                }
            }

            else -> {
                _uiState.update { it.copy(selectedPos = pos) }
            }
        }
    }

    fun startMoveCommand() {
        val state = _uiState.value
        val unit = state.selectedUnit ?: return
        if (unit.faction != Faction.ALLY || unit.hasActed || unit.hasMoved) return

        val reachable = TacticalCombatEngine.computeReachableTiles(
            mover = unit,
            terrainGrid = state.terrainGrid,
            allUnits = state.units
        )
        _uiState.update {
            it.copy(
                inputMode = TacticalInputMode.SELECTING_MOVE,
                reachableTiles = reachable,
                weaponRangeTiles = emptySet(),
                statusBannerMessage = "Select destination tile for ${unit.name} (Move Range: ${unit.effectiveMoveRange})."
            )
        }
    }

    fun openWeaponSelector() {
        val state = _uiState.value
        val unit = state.selectedUnit ?: return
        if (unit.faction != Faction.ALLY || unit.hasActed) return

        _uiState.update {
            it.copy(
                inputMode = TacticalInputMode.SELECTING_WEAPON,
                reachableTiles = emptyMap(),
                statusBannerMessage = "Select weapon for ${unit.name} (Will: ${unit.pilot.will}, EN: ${unit.currentEn})."
            )
        }
    }

    fun selectWeaponForAttack(weapon: Weapon) {
        val state = _uiState.value
        val unit = state.selectedUnit ?: return
        if (!unit.canUseWeapon(weapon, afterMove = unit.hasMoved)) return

        val height = state.terrainGrid.size
        val width = if (height > 0) state.terrainGrid[0].size else 0
        val rangeTiles = TacticalCombatEngine.computeWeaponRangeTiles(
            origin = unit.pos,
            weapon = weapon,
            mapWidth = width,
            mapHeight = height
        )

        val enemiesInRange = state.units.filter {
            it.isAlive() && it.faction != unit.faction && it.pos in rangeTiles
        }

        val firstEnemy = enemiesInRange.firstOrNull()
        _uiState.update {
            it.copy(
                selectedWeapon = weapon,
                weaponRangeTiles = rangeTiles,
                selectedPos = firstEnemy?.pos ?: unit.pos,
                inputMode = TacticalInputMode.SELECTING_TARGET,
                statusBannerMessage = if (enemiesInRange.isNotEmpty()) {
                    "${weapon.name} armed! Tap a hostile unit in crimson range (${enemiesInRange.size} target(s) in range)."
                } else {
                    "${weapon.name} armed — No enemy units within range ${weapon.minRange}~${weapon.maxRange}."
                }
            )
        }
    }

    fun openSpiritSelector() {
        val state = _uiState.value
        val unit = state.selectedUnit ?: return
        if (unit.faction != Faction.ALLY || unit.hasActed) return

        _uiState.update {
            it.copy(
                inputMode = TacticalInputMode.SELECTING_SPIRIT,
                statusBannerMessage = "Select a Pilot Spirit Command or Arcane Spell for ${unit.pilot.name}."
            )
        }
    }

    fun openMoraleEventDialog() {
        val state = _uiState.value
        if (state.isEnemyAiThinking || state.activeCutscene != null) return
        _uiState.update {
            it.copy(
                inputMode = TacticalInputMode.SELECTING_MORALE_EVENT,
                statusBannerMessage = "Pilot Morale (Will) Telemetry & Battlefield Event Triggers."
            )
        }
    }

    fun triggerTacticalMoraleEvent(event: TacticalMoraleEvent) {
        val state = _uiState.value
        val result = pilotMoraleSystem.triggerMoraleEvent(
            event = event,
            units = state.units,
            selectedUnitId = state.selectedUnitId
        )
        applyMoraleUpdateResult(result)
    }

    fun applyMoraleUpdateResult(result: MoraleUpdateResult) {
        val newInitiative = combatManager.computeInitiativeOrder(result.updatedUnits)

        addCombatLog(
            headline = result.summaryHeadline,
            detail = result.summaryDetail
        )

        _uiState.update {
            it.copy(
                units = result.updatedUnits,
                initiativeOrder = newInitiative,
                recentMoraleChanges = result.changes,
                inputMode = TacticalInputMode.IDLE_BROWSE,
                statusBannerMessage = "${result.summaryHeadline} — ${result.summaryDetail}"
            )
        }
    }

    fun resolveSpiritCasterAndTarget(
        spirit: SpiritCommand,
        targetUnitId: String? = null
    ): Pair<String, String?>? {
        val state = _uiState.value
        val activeForecast = state.combatForecast
        val isInPreAttackPreview = state.inputMode == TacticalInputMode.SHOWING_COMBAT_FORECAST && activeForecast != null
        val unit = if (isInPreAttackPreview) {
            state.units.find { it.id == activeForecast!!.attacker.id } ?: state.selectedUnit
        } else {
            state.selectedUnit
        } ?: return null

        val resolvedTargetId = targetUnitId ?: if (isInPreAttackPreview && activeForecast != null) {
            when (spirit.targetType) {
                SpiritTargetType.SINGLE_ENEMY -> activeForecast.defender.id
                SpiritTargetType.SINGLE_ALLY -> activeForecast.attacker.id
                else -> null
            }
        } else {
            null
        }
        return unit.id to resolvedTargetId
    }

    fun activateSpiritCommand(spirit: SpiritCommand, targetUnitId: String? = null) {
        val state = _uiState.value
        val (casterId, resolvedTargetId) = resolveSpiritCasterAndTarget(spirit, targetUnitId) ?: return
        val castResult = spiritCommandSystem.castSpiritCommand(
            casterId = casterId,
            spirit = spirit,
            allUnits = state.units,
            targetUnitId = resolvedTargetId
        )
        applySpiritCastResult(castResult)
    }

    fun applySpiritCastResult(castResult: com.example.srw.engine.SpiritCastResult) {
        val state = _uiState.value
        val activeForecast = state.combatForecast
        val isInPreAttackPreview = state.inputMode == TacticalInputMode.SHOWING_COMBAT_FORECAST && activeForecast != null

        if (!castResult.success) {
            _uiState.update {
                it.copy(statusBannerMessage = "${castResult.headline}: ${castResult.detail}")
            }
            return
        }

        val newInitiative = combatManager.computeInitiativeOrder(castResult.updatedUnits)

        addCombatLog(
            headline = castResult.headline,
            detail = castResult.detail
        )

        if (isInPreAttackPreview && activeForecast != null) {
            val updatedAttacker = castResult.updatedUnits.first { it.id == activeForecast.attacker.id }
            val updatedDefender = castResult.updatedUnits.first { it.id == activeForecast.defender.id }
            val counterWeapon = activeForecast.defenderWeapon ?: TacticalCombatEngine.selectBestCounterWeapon(
                updatedDefender,
                activeForecast.distance
            )
            val refreshedForecast = combatManager.buildCombatForecast(
                attacker = updatedAttacker,
                defender = updatedDefender,
                attackerWeapon = activeForecast.attackerWeapon,
                defenderWeapon = counterWeapon,
                defenderStance = activeForecast.defenderStance,
                terrainGrid = state.terrainGrid,
                allUnits = castResult.updatedUnits
            )
            val refreshedBreakdown = combatManager.calculateDetailedDamage(
                attacker = updatedAttacker,
                defender = updatedDefender,
                weapon = activeForecast.attackerWeapon,
                defenderTerrain = refreshedForecast.defenderTerrain,
                defenderStance = refreshedForecast.defenderStance,
                isCritical = false,
                attackerTerrain = refreshedForecast.attackerTerrain
            )
            _uiState.update {
                it.copy(
                    units = castResult.updatedUnits,
                    initiativeOrder = newInitiative,
                    combatForecast = refreshedForecast,
                    forecastDamageBreakdown = refreshedBreakdown,
                    inputMode = TacticalInputMode.SHOWING_COMBAT_FORECAST,
                    statusBannerMessage = "${castResult.headline} — Combat Preview updated."
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    units = castResult.updatedUnits,
                    initiativeOrder = newInitiative,
                    inputMode = if (castResult.caster.hasMoved) TacticalInputMode.POST_MOVE_COMMAND else TacticalInputMode.IDLE_BROWSE,
                    statusBannerMessage = "${castResult.headline} — ${castResult.detail}"
                )
            }
        }
    }

    fun castMagicSpellOnSelectedUnit(spell: MagicSpell) {
        val state = _uiState.value
        val activeForecast = state.combatForecast
        val isInPreAttackPreview = state.inputMode == TacticalInputMode.SHOWING_COMBAT_FORECAST && activeForecast != null
        val unit = if (isInPreAttackPreview) {
            state.units.find { it.id == activeForecast!!.attacker.id } ?: state.selectedUnit
        } else {
            state.selectedUnit
        } ?: return
        val updatedUnit = developmentSystem.castMagicSpell(unit, spell) ?: return

        val updatedUnits = state.units.map { if (it.id == unit.id) updatedUnit else it }
        val newInitiative = combatManager.computeInitiativeOrder(updatedUnits)

        addCombatLog(
            headline = "${unit.pilot.name} cast Arcane Spell [${spell.codeName} / ${spell.jpName}]!",
            detail = spell.summary
        )

        if (isInPreAttackPreview && activeForecast != null) {
            val updatedDefender = updatedUnits.first { it.id == activeForecast.defender.id }
            val counterWeapon = activeForecast.defenderWeapon ?: TacticalCombatEngine.selectBestCounterWeapon(
                updatedDefender,
                activeForecast.distance
            )
            val refreshedForecast = combatManager.buildCombatForecast(
                attacker = updatedUnit,
                defender = updatedDefender,
                attackerWeapon = activeForecast.attackerWeapon,
                defenderWeapon = counterWeapon,
                defenderStance = activeForecast.defenderStance,
                terrainGrid = state.terrainGrid,
                allUnits = updatedUnits
            )
            val refreshedBreakdown = combatManager.calculateDetailedDamage(
                attacker = updatedUnit,
                defender = updatedDefender,
                weapon = activeForecast.attackerWeapon,
                defenderTerrain = refreshedForecast.defenderTerrain,
                defenderStance = refreshedForecast.defenderStance,
                isCritical = false,
                attackerTerrain = refreshedForecast.attackerTerrain
            )
            _uiState.update {
                it.copy(
                    units = updatedUnits,
                    initiativeOrder = newInitiative,
                    combatForecast = refreshedForecast,
                    forecastDamageBreakdown = refreshedBreakdown,
                    inputMode = TacticalInputMode.SHOWING_COMBAT_FORECAST,
                    statusBannerMessage = "Arcane Spell [${spell.codeName}] cast on ${unit.name} — Preview updated."
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    units = updatedUnits,
                    initiativeOrder = newInitiative,
                    inputMode = if (updatedUnit.hasMoved) TacticalInputMode.POST_MOVE_COMMAND else TacticalInputMode.IDLE_BROWSE,
                    statusBannerMessage = "Arcane Spell [${spell.codeName}] cast on ${unit.name}!"
                )
            }
        }
    }

    fun startRepairCommand() {
        val state = _uiState.value
        val unit = state.selectedUnit ?: return
        if (unit.archetype != MechaArchetype.COMMAND_SHIP || unit.hasActed) return

        val height = state.terrainGrid.size
        val width = if (height > 0) state.terrainGrid[0].size else 0
        val tiles = mutableSetOf<GridPos>()
        for (y in 0 until height) {
            for (x in 0 until width) {
                val p = GridPos(x, y)
                if (unit.pos.distanceTo(p) <= 2) tiles.add(p)
            }
        }
        _uiState.update {
            it.copy(
                repairRangeTiles = tiles,
                inputMode = TacticalInputMode.SELECTING_REPAIR_TARGET,
                statusBannerMessage = "Tap an allied unit within 2 tiles of ${unit.name} to Repair (+2800 HP, +60 EN)."
            )
        }
    }

    fun changeForecastDefenderStance(stance: DefendStance) {
        val state = _uiState.value
        val currentForecast = state.combatForecast ?: return
        val updatedForecast = combatManager.buildCombatForecast(
            attacker = currentForecast.attacker,
            defender = currentForecast.defender,
            attackerWeapon = currentForecast.attackerWeapon,
            defenderWeapon = currentForecast.defenderWeapon ?: TacticalCombatEngine.selectBestCounterWeapon(
                currentForecast.defender,
                currentForecast.distance
            ),
            defenderStance = stance,
            terrainGrid = state.terrainGrid,
            allUnits = state.units
        )
        val updatedBreakdown = combatManager.calculateDetailedDamage(
            attacker = currentForecast.attacker,
            defender = currentForecast.defender,
            weapon = currentForecast.attackerWeapon,
            defenderTerrain = updatedForecast.defenderTerrain,
            defenderStance = stance,
            isCritical = false,
            attackerTerrain = updatedForecast.attackerTerrain
        )
        _uiState.update {
            it.copy(
                combatForecast = updatedForecast,
                forecastDamageBreakdown = updatedBreakdown
            )
        }
    }

    fun changeForecastAttackerWeapon(weapon: Weapon) {
        val state = _uiState.value
        val currentForecast = state.combatForecast ?: return
        if (!currentForecast.attacker.canUseWeapon(
                weapon,
                afterMove = currentForecast.attacker.hasMoved,
                distance = currentForecast.distance
            )
        ) return

        val updatedForecast = combatManager.buildCombatForecast(
            attacker = currentForecast.attacker,
            defender = currentForecast.defender,
            attackerWeapon = weapon,
            defenderWeapon = currentForecast.defenderWeapon ?: TacticalCombatEngine.selectBestCounterWeapon(
                currentForecast.defender,
                currentForecast.distance
            ),
            defenderStance = currentForecast.defenderStance,
            terrainGrid = state.terrainGrid,
            allUnits = state.units
        )
        val updatedBreakdown = combatManager.calculateDetailedDamage(
            attacker = currentForecast.attacker,
            defender = currentForecast.defender,
            weapon = weapon,
            defenderTerrain = updatedForecast.defenderTerrain,
            defenderStance = updatedForecast.defenderStance,
            isCritical = false,
            attackerTerrain = updatedForecast.attackerTerrain
        )
        _uiState.update {
            it.copy(
                selectedWeapon = weapon,
                combatForecast = updatedForecast,
                forecastDamageBreakdown = updatedBreakdown
            )
        }
    }

    fun executeConfirmedCombat() {
        val state = _uiState.value
        val forecast = state.combatForecast ?: return
        val sequence = combatManager.resolveCombatExchange(
            forecast = forecast,
            isSandboxPreview = false
        )
        _uiState.update {
            it.copy(
                activeCutscene = sequence,
                combatForecast = null,
                forecastDamageBreakdown = null,
                weaponRangeTiles = emptySet(),
                reachableTiles = emptyMap(),
                movementTrajectory = emptyList()
            )
        }
    }

    fun onCutsceneFinished() {
        val state = _uiState.value
        val seq = state.activeCutscene ?: return

        if (seq.isSandboxPreview) {
            _uiState.update { it.copy(activeCutscene = null) }
            return
        }

        val attackerSurvived = seq.attackerHpAfterCounter > 0
        val defenderSurvived = seq.defenderHpAfterPrimary > 0

        // 1. Apply HP, EN, Ammo, and Spirit consumption first
        val postCombatUnits = state.units.map { unit ->
            when (unit.id) {
                seq.attacker.id -> {
                    val spentEn = (unit.currentEn - seq.attackerWeapon.enCost -
                        if (seq.counterBarrierActivated) 10 else 0).coerceAtLeast(0)
                    val updatedWeapons = unit.weapons.map { w ->
                        if (w.id == seq.attackerWeapon.id && w.maxAmmo > 0) {
                            w.copy(currentAmmo = (w.currentAmmo - 1).coerceAtLeast(0))
                        } else w
                    }
                    val remainingSpirits = unit.activeSpirits - setOf(
                        SpiritCommand.VALOR,
                        SpiritCommand.SOUL,
                        if (seq.counterExecuted) SpiritCommand.ALERT else null
                    ).filterNotNull().toSet()

                    unit.copy(
                        currentHp = seq.attackerHpAfterCounter,
                        currentEn = spentEn,
                        hasMoved = true,
                        hasActed = true,
                        weapons = updatedWeapons,
                        activeSpirits = remainingSpirits
                    )
                }

                seq.defender.id -> {
                    val counterEnCost = if (seq.counterExecuted && seq.defenderWeapon != null) {
                        seq.defenderWeapon.enCost
                    } else 0
                    val barrierEnCost = if (seq.primaryBarrierActivated) 10 else 0
                    val spentEn = (unit.currentEn - counterEnCost - barrierEnCost).coerceAtLeast(0)

                    val updatedWeapons = unit.weapons.map { w ->
                        if (seq.counterExecuted && w.id == seq.defenderWeapon?.id && w.maxAmmo > 0) {
                            w.copy(currentAmmo = (w.currentAmmo - 1).coerceAtLeast(0))
                        } else w
                    }
                    val remainingSpirits = unit.activeSpirits - setOf(SpiritCommand.ALERT)

                    unit.copy(
                        currentHp = seq.defenderHpAfterPrimary,
                        currentEn = spentEn,
                        weapons = updatedWeapons,
                        activeSpirits = remainingSpirits
                    )
                }

                else -> unit
            }
        }

        // 2. Process dynamic Pilot Morale (Will) fluctuations & Battle XP / Kill progression
        val moraleResult = pilotMoraleSystem.processCombatExchangeMorale(
            sequence = seq,
            currentUnits = postCombatUnits
        )
        val xpResult = pilotProgressionSystem.processCombatXp(
            sequence = seq,
            currentUnits = moraleResult.updatedUnits
        )
        val updatedUnits = xpResult.updatedUnits

        val creditReward = when {
            !defenderSurvived && seq.defender.faction == Faction.ENEMY ->
                if (seq.defender.id.contains("boss")) 12000 else 4500
            seq.attacker.faction == Faction.ALLY && seq.primaryDamage > 0 -> 800
            else -> 0
        }

        addCombatLog(
            headline = "${seq.attacker.name} (${seq.attackerWeapon.name}) vs ${seq.defender.name}",
            detail = buildString {
                if (seq.primaryOutcome == StrikeOutcome.MISS) {
                    append("${seq.defender.name} evaded! ")
                } else {
                    append("Dealt ${seq.primaryDamage} DMG (${seq.primaryOutcome.name}). ")
                }
                if (!defenderSurvived) {
                    append("${seq.defender.name} SHOT DOWN! (+$creditReward CR) ")
                } else if (seq.counterExecuted) {
                    if (seq.counterOutcome == StrikeOutcome.MISS) {
                        append("Counter-attack missed. ")
                    } else {
                        append("Counter dealt ${seq.counterDamage} DMG. ")
                    }
                }
                if (xpResult.summaryText.isNotEmpty()) {
                    append("• XP: ${xpResult.summaryText} ")
                }
                append("• ${moraleResult.summaryDetail}")
            },
            isCritical = seq.primaryOutcome == StrikeOutcome.CRITICAL || seq.counterOutcome == StrikeOutcome.CRITICAL,
            isDestruction = !defenderSurvived || !attackerSurvived
        )

        val anyEnemiesLeft = updatedUnits.any { it.faction == Faction.ENEMY && it.isAlive() }
        val anyAlliesLeft = updatedUnits.any { it.faction == Faction.ALLY && it.isAlive() }
        val newInitiative = combatManager.computeInitiativeOrder(updatedUnits)

        _uiState.update {
            it.copy(
                units = updatedUnits,
                initiativeOrder = newInitiative,
                recentMoraleChanges = moraleResult.changes,
                lastCompletedBattleSequence = seq,
                completedBattleCount = it.completedBattleCount + 1,
                credits = it.credits + creditReward,
                activeCutscene = null,
                preMovePos = null,
                inputMode = TacticalInputMode.IDLE_BROWSE,
                isVictory = !anyEnemiesLeft,
                isDefeat = !anyAlliesLeft,
                phaseStage = if (!anyEnemiesLeft || !anyAlliesLeft) TurnPhaseStage.STAGE_COMPLETED else it.phaseStage,
                statusBannerMessage = when {
                    !anyEnemiesLeft -> "STAGE CLEAR! All hostile mobile weapons eliminated!"
                    !anyAlliesLeft -> "MISSION FAILED — All allied mobile suits lost."
                    !defenderSurvived -> "${seq.defender.name} destroyed! ${xpResult.summaryText}"
                    xpResult.summaryText.isNotEmpty() -> "${xpResult.summaryText} • ${moraleResult.summaryDetail}"
                    else -> moraleResult.summaryDetail.ifEmpty { "Combat exchange complete." }
                }
            )
        }

        if (anyEnemiesLeft && anyAlliesLeft && _uiState.value.currentPhase == Faction.ALLY) {
            checkAutoPhaseAdvance()
        }
    }

    fun standbySelectedUnit() {
        val state = _uiState.value
        val unit = state.selectedUnit ?: return
        if (unit.faction != Faction.ALLY || unit.hasActed) return

        val updatedUnits = state.units.map {
            if (it.id == unit.id) it.copy(hasMoved = true, hasActed = true) else it
        }
        val nextReady = combatManager.getNextReadyUnitByInitiative(updatedUnits, Faction.ALLY)
        _uiState.update {
            it.copy(
                units = updatedUnits,
                initiativeOrder = combatManager.computeInitiativeOrder(updatedUnits),
                selectedUnitId = nextReady?.id ?: it.selectedUnitId,
                selectedPos = nextReady?.pos ?: it.selectedPos,
                preMovePos = null,
                reachableTiles = emptyMap(),
                weaponRangeTiles = emptySet(),
                movementTrajectory = emptyList(),
                inputMode = TacticalInputMode.IDLE_BROWSE,
                statusBannerMessage = "${unit.name} standing by." +
                    (if (nextReady != null) " Next by Initiative: ${nextReady.name}." else "")
            )
        }
        checkAutoPhaseAdvance()
    }

    fun cancelCurrentOrder() {
        val state = _uiState.value
        val unit = state.selectedUnit
        val restoredUnits = if (state.preMovePos != null && unit != null) {
            state.units.map { u ->
                if (u.id == unit.id) u.copy(pos = state.preMovePos, hasMoved = false) else u
            }
        } else state.units

        _uiState.update {
            it.copy(
                units = restoredUnits,
                initiativeOrder = combatManager.computeInitiativeOrder(restoredUnits),
                selectedPos = state.preMovePos ?: it.selectedPos,
                preMovePos = null,
                reachableTiles = emptyMap(),
                weaponRangeTiles = emptySet(),
                repairRangeTiles = emptySet(),
                movementTrajectory = emptyList(),
                combatForecast = null,
                forecastDamageBreakdown = null,
                inputMode = TacticalInputMode.IDLE_BROWSE,
                statusBannerMessage = "Order cancelled."
            )
        }
    }

    fun selectNextReadyAlly() {
        val state = _uiState.value
        val readyAlliesByInit = combatManager.computeInitiativeOrder(state.units, Faction.ALLY)
            .filter { !it.hasActed }
        if (readyAlliesByInit.isEmpty()) return

        val currentIdx = readyAlliesByInit.indexOfFirst { it.unit.id == state.selectedUnitId }
        val nextEntry = readyAlliesByInit[(currentIdx + 1) % readyAlliesByInit.size]
        val nextAlly = nextEntry.unit
        _uiState.update {
            it.copy(
                selectedUnitId = nextAlly.id,
                selectedPos = nextAlly.pos,
                inputMode = TacticalInputMode.IDLE_BROWSE,
                reachableTiles = emptyMap(),
                weaponRangeTiles = emptySet(),
                statusBannerMessage = "Initiative #${nextEntry.rank}: ${nextAlly.name} (${nextAlly.pilot.name} • INIT ${nextEntry.initiativeScore})."
            )
        }
    }

    private fun checkAutoPhaseAdvance() {
        val state = _uiState.value
        if (combatManager.isPhaseComplete(state.units, Faction.ALLY) &&
            !state.isVictory &&
            !state.isDefeat &&
            state.currentPhase == Faction.ALLY
        ) {
            endPlayerPhase()
        }
    }

    fun endPlayerPhase() {
        val state = _uiState.value
        if (state.isEnemyAiThinking || state.activeCutscene != null || state.isVictory || state.isDefeat) return

        viewModelScope.launch {
            val transition = combatManager.beginEnemyPhase(
                turnNumber = state.turnNumber,
                units = state.units
            )
            _uiState.update {
                it.copy(
                    currentPhase = Faction.ENEMY,
                    phaseStage = transition.phaseStage,
                    units = transition.updatedUnits,
                    initiativeOrder = transition.initiativeOrder,
                    isEnemyAiThinking = true,
                    inputMode = TacticalInputMode.IDLE_BROWSE,
                    reachableTiles = emptyMap(),
                    weaponRangeTiles = emptySet(),
                    preMovePos = null,
                    statusBannerMessage = transition.phaseAnnouncement
                )
            }

            delay(450)
            runNextEnemyAiTurn()
        }
    }

    private suspend fun runNextEnemyAiTurn() {
        while (true) {
            while (_uiState.value.activeCutscene != null) {
                delay(200)
            }

            val state = _uiState.value
            if (state.isVictory || state.isDefeat) {
                _uiState.update { it.copy(isEnemyAiThinking = false) }
                return
            }

            val nextEnemy = combatManager.getNextReadyUnitByInitiative(state.units, Faction.ENEMY)
                ?: break

            val enemyInit = combatManager.calculateUnitInitiative(nextEnemy)
            val allies = state.units.filter { it.faction == Faction.ALLY && it.isAlive() }
            if (allies.isEmpty()) break

            val reachable = TacticalCombatEngine.computeReachableTiles(
                mover = nextEnemy,
                terrainGrid = state.terrainGrid,
                allUnits = state.units
            )

            val targetAlly = allies.minByOrNull { ally ->
                nextEnemy.pos.distanceTo(ally.pos) * 100 + (ally.currentHp * 100 / ally.maxHp)
            } ?: allies.first()

            val bestTile = reachable.keys.minByOrNull { tile ->
                val dist = tile.distanceTo(targetAlly.pos)
                val canAttackFromTile = nextEnemy.weapons.any { w ->
                    nextEnemy.canUseWeapon(w, afterMove = tile != nextEnemy.pos, distance = dist)
                }
                if (canAttackFromTile) {
                    val terrainTile = state.terrainGrid[tile.y][tile.x]
                    val terrainScore = terrainTile.defBonusPercent + terrainTile.evadeBonusPercent +
                        terrainTile.attackBonusPercent + terrainTile.hitBonusPercent
                    -terrainScore
                } else {
                    100 + dist * 10
                }
            } ?: nextEnemy.pos

            val movedEnemy = nextEnemy.copy(
                pos = bestTile,
                hasMoved = bestTile != nextEnemy.pos,
                hasActed = true
            )

            val updatedUnits = state.units.map { if (it.id == movedEnemy.id) movedEnemy else it }
            _uiState.update { s ->
                s.copy(
                    selectedPos = bestTile,
                    selectedUnitId = movedEnemy.id,
                    units = updatedUnits,
                    initiativeOrder = combatManager.computeInitiativeOrder(updatedUnits),
                    statusBannerMessage = "ENEMY PHASE [INIT ${enemyInit.initiativeScore}]: ${movedEnemy.name} advancing to (${bestTile.x}, ${bestTile.y})!"
                )
            }

            delay(380)

            val currentState = _uiState.value
            val attackCandidate = allies.mapNotNull { ally ->
                val dist = movedEnemy.pos.distanceTo(ally.pos)
                val usableWeapon = movedEnemy.availableWeapons(
                    afterMove = movedEnemy.hasMoved,
                    distance = dist
                ).maxByOrNull { it.effectivePower(movedEnemy.upgradeWeapon, movedEnemy.magicLevel) }
                if (usableWeapon != null) Pair(ally, usableWeapon) else null
            }.firstOrNull()

            if (attackCandidate != null) {
                val (defenderAlly, enemyWeapon) = attackCandidate
                val dist = movedEnemy.pos.distanceTo(defenderAlly.pos)
                val counterWeapon = TacticalCombatEngine.selectBestCounterWeapon(defenderAlly, dist)
                val forecast = combatManager.buildCombatForecast(
                    attacker = movedEnemy,
                    defender = defenderAlly,
                    attackerWeapon = enemyWeapon,
                    defenderWeapon = counterWeapon,
                    defenderStance = DefendStance.COUNTER,
                    terrainGrid = currentState.terrainGrid,
                    allUnits = currentState.units
                )
                val cutscene = combatManager.resolveCombatExchange(
                    forecast = forecast,
                    isSandboxPreview = false
                )
                _uiState.update { it.copy(activeCutscene = cutscene) }
                while (_uiState.value.activeCutscene != null) {
                    delay(200)
                }
            }
        }

        startNewPlayerTurn()
    }

    private fun startNewPlayerTurn() {
        val state = _uiState.value
        val transition = combatManager.beginNextPlayerTurn(
            currentTurnNumber = state.turnNumber,
            units = state.units,
            terrainGrid = state.terrainGrid
        )

        addCombatLog(
            headline = "TURN ${transition.turnNumber} — PLAYER PHASE STARTED",
            detail = "${transition.phaseAnnouncement}. All units recovered EN, SP/Mana, and +2 Will."
        )

        _uiState.update {
            it.copy(
                turnNumber = transition.turnNumber,
                currentPhase = Faction.ALLY,
                phaseStage = transition.phaseStage,
                isEnemyAiThinking = false,
                units = transition.updatedUnits,
                initiativeOrder = transition.initiativeOrder,
                selectedUnitId = transition.nextActiveUnit?.id ?: it.selectedUnitId,
                selectedPos = transition.nextActiveUnit?.pos ?: it.selectedPos,
                inputMode = TacticalInputMode.IDLE_BROWSE,
                statusBannerMessage = transition.phaseAnnouncement
            )
        }
    }

    // =========================================================================
    // UNIT, WEAPON, ARMOR & MAGIC DEVELOPMENT SYSTEM ACTIONS
    // =========================================================================

    private fun applyDevelopmentOutcome(unitId: String, outcome: DevelopmentOutcome) {
        if (!outcome.success) {
            _uiState.update { it.copy(statusBannerMessage = outcome.detail) }
            return
        }
        val state = _uiState.value
        val updatedUnits = state.units.map { if (it.id == unitId) outcome.updatedUnit else it }
        addCombatLog(headline = outcome.headline, detail = outcome.detail)
        _uiState.update {
            it.copy(
                credits = outcome.remainingCredits,
                units = updatedUnits,
                initiativeOrder = combatManager.computeInitiativeOrder(updatedUnits),
                statusBannerMessage = "${outcome.headline} (${outcome.detail})"
            )
        }
    }

    fun upgradeUnitStat(unitId: String, statType: String) {
        val state = _uiState.value
        val unit = state.units.find { it.id == unitId } ?: return
        val outcome = developmentSystem.tuneUnitStat(unit, statType, state.credits)
        applyDevelopmentOutcome(unitId, outcome)
    }

    fun evolveUnitFrame(unitId: String) {
        val state = _uiState.value
        val unit = state.units.find { it.id == unitId } ?: return
        val outcome = developmentSystem.evolveUnitFrame(unit, state.credits)
        applyDevelopmentOutcome(unitId, outcome)
    }

    fun forgeWeapon(unitId: String, weaponId: String) {
        val state = _uiState.value
        val unit = state.units.find { it.id == unitId } ?: return
        val outcome = developmentSystem.forgeWeapon(unit, weaponId, state.credits)
        applyDevelopmentOutcome(unitId, outcome)
    }

    fun developPrototypeWeapon(unitId: String) {
        val state = _uiState.value
        val unit = state.units.find { it.id == unitId } ?: return
        val outcome = developmentSystem.developPrototypeWeapon(unit, state.credits)
        applyDevelopmentOutcome(unitId, outcome)
    }

    fun installArmorPlating(unitId: String, platingTier: ArmorPlatingTier) {
        val state = _uiState.value
        val unit = state.units.find { it.id == unitId } ?: return
        val outcome = developmentSystem.installArmorPlating(unit, platingTier, state.credits)
        applyDevelopmentOutcome(unitId, outcome)
    }

    fun upgradeBarrierGenerator(unitId: String) {
        val state = _uiState.value
        val unit = state.units.find { it.id == unitId } ?: return
        val outcome = developmentSystem.upgradeBarrierGenerator(unit, state.credits)
        applyDevelopmentOutcome(unitId, outcome)
    }

    fun upgradeMagicConduit(unitId: String) {
        val state = _uiState.value
        val unit = state.units.find { it.id == unitId } ?: return
        val outcome = developmentSystem.upgradeMagicConduit(unit, state.credits)
        applyDevelopmentOutcome(unitId, outcome)
    }

    fun attuneElementalAffinity(unitId: String, affinity: ElementalAffinity) {
        val state = _uiState.value
        val unit = state.units.find { it.id == unitId } ?: return
        val outcome = developmentSystem.attuneElementalAffinity(unit, affinity, state.credits)
        applyDevelopmentOutcome(unitId, outcome)
    }

    fun researchMagicSpell(unitId: String, spell: MagicSpell) {
        val state = _uiState.value
        val unit = state.units.find { it.id == unitId } ?: return
        val outcome = developmentSystem.researchMagicSpell(unit, spell, state.credits)
        applyDevelopmentOutcome(unitId, outcome)
    }

    // =========================================================================
    // PILOT ACADEMY PROGRESSION (PASSIVE ABILITIES & SPIRIT MASTERY)
    // =========================================================================

    private fun applyPilotProgressionOutcome(unitId: String, outcome: PilotProgressionOutcome) {
        if (!outcome.success) {
            _uiState.update { it.copy(statusBannerMessage = outcome.detail) }
            return
        }
        val state = _uiState.value
        val updatedUnits = state.units.map { if (it.id == unitId) outcome.updatedUnit else it }
        addCombatLog(headline = outcome.headline, detail = outcome.detail)
        _uiState.update {
            it.copy(
                units = updatedUnits,
                initiativeOrder = combatManager.computeInitiativeOrder(updatedUnits),
                statusBannerMessage = "${outcome.headline} (${outcome.detail})"
            )
        }
    }

    fun unlockPilotPassive(unitId: String, ability: PilotPassiveAbility) {
        val unit = _uiState.value.units.find { it.id == unitId } ?: return
        val outcome = pilotProgressionSystem.unlockPassiveAbility(unit, ability)
        applyPilotProgressionOutcome(unitId, outcome)
    }

    fun upgradePilotSpirit(unitId: String, spirit: SpiritCommand) {
        val unit = _uiState.value.units.find { it.id == unitId } ?: return
        val outcome = pilotProgressionSystem.upgradeSpiritCommand(unit, spirit)
        applyPilotProgressionOutcome(unitId, outcome)
    }

    fun applyPilotLevelUps(
        leveledUnits: List<MechaUnit>,
        headline: String,
        detail: String
    ) {
        val leveledPilotByUnitId = leveledUnits.associate { it.id to it.pilot }
        val state = _uiState.value
        val mergedUnits = state.units.map { existing ->
            val upgradedPilot = leveledPilotByUnitId[existing.id]
            if (upgradedPilot != null && upgradedPilot.level > existing.pilot.level) {
                existing.copy(pilot = upgradedPilot)
            } else {
                existing
            }
        }
        if (headline.isNotBlank()) {
            addCombatLog(headline = headline, detail = detail)
        }
        _uiState.update {
            it.copy(
                units = mergedUnits,
                initiativeOrder = combatManager.computeInitiativeOrder(mergedUnits),
                statusBannerMessage = headline.ifBlank { it.statusBannerMessage }
            )
        }
    }

    // --- Cinematic Battle Studio / Sandbox ---
    fun updateSandboxConfig(transform: (SandboxStudioConfig) -> SandboxStudioConfig) {
        _uiState.update { state ->
            val updated = transform(state.sandboxConfig)
            val attackerTemplate = sandboxAllTemplates.getOrElse(updated.attackerIndex) { sandboxAllTemplates.first() }
            val coercedWeaponIdx = updated.weaponIndex.coerceIn(0, attackerTemplate.weapons.lastIndex)
            state.copy(sandboxConfig = updated.copy(weaponIndex = coercedWeaponIdx))
        }
    }

    fun launchSandboxCutscene() {
        val cfg = _uiState.value.sandboxConfig
        val rawAttacker = sandboxAllTemplates.getOrElse(cfg.attackerIndex) { sandboxAllTemplates.first() }
        val rawDefender = sandboxAllTemplates.getOrElse(cfg.defenderIndex) { sandboxAllTemplates.last() }

        val attackerSpirits = if (cfg.enableValor) {
            setOf(SpiritCommand.VALOR, SpiritCommand.STRIKE)
        } else {
            emptySet()
        }

        val attacker = rawAttacker.copy(
            pos = GridPos(2, 5),
            activeSpirits = attackerSpirits
        )
        val defender = rawDefender.copy(
            pos = GridPos(4, 5)
        )

        val weapon = attacker.weapons.getOrElse(cfg.weaponIndex) { attacker.weapons.last() }
        val counterWeapon = if (cfg.enableCounter) defender.weapons.lastOrNull() else null

        val dummyGrid = List(10) { List(10) { cfg.terrain } }
        val forecast = combatManager.buildCombatForecast(
            attacker = attacker,
            defender = defender,
            attackerWeapon = weapon,
            defenderWeapon = counterWeapon,
            defenderStance = if (cfg.enableCounter) DefendStance.COUNTER else DefendStance.DEFEND,
            terrainGrid = dummyGrid,
            allUnits = listOf(attacker, defender)
        )

        val sequence = combatManager.resolveCombatExchange(
            forecast = forecast,
            forcedPrimaryOutcome = cfg.forcedOutcome,
            isSandboxPreview = true
        )

        _uiState.update { it.copy(activeCutscene = sequence) }
    }

    private fun addCombatLog(
        headline: String,
        detail: String,
        isCritical: Boolean = false,
        isDestruction: Boolean = false
    ) {
        val state = _uiState.value
        val entry = CombatLogEntry(
            id = System.nanoTime(),
            turn = state.turnNumber,
            phase = state.currentPhase,
            headline = headline,
            detail = detail,
            isCritical = isCritical,
            isDestruction = isDestruction
        )
        _uiState.update {
            it.copy(combatLogs = listOf(entry) + it.combatLogs.take(49))
        }
    }
}
