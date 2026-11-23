package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.srw.ui.CinematicBattleStage
import com.example.srw.ui.CinematicStudioScreen
import com.example.srw.ui.CombatTelemetryLogScreen
import com.example.srw.ui.HangarUpgradeScreen
import com.example.srw.ui.PilotLevelUpOverlay
import com.example.srw.ui.TacticalGridScreen
import com.example.srw.viewmodel.EngineTab
import com.example.srw.viewmodel.PilotLevelManager
import com.example.srw.viewmodel.SpiritCommandsViewModel
import com.example.srw.viewmodel.SrwEngineViewModel
import com.example.srw.viewmodel.TacticalInputMode
import com.example.srw.viewmodel.WillMoraleViewModel
import com.example.ui.theme.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                SrwEngineApp()
            }
        }
    }
}

@Composable
fun SrwEngineApp(
    viewModel: SrwEngineViewModel = viewModel(),
    pilotLevelManager: PilotLevelManager = viewModel(),
    spiritCommandsViewModel: SpiritCommandsViewModel = viewModel(),
    willMoraleViewModel: WillMoraleViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val levelUiState by pilotLevelManager.uiState.collectAsStateWithLifecycle()
    val spiritUiState by spiritCommandsViewModel.uiState.collectAsStateWithLifecycle()
    val willMoraleUiState by willMoraleViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.units) {
        spiritCommandsViewModel.syncRoster(uiState.units)
        willMoraleViewModel.syncRoster(uiState.units)
        val eval = pilotLevelManager.evaluateRosterProgress(uiState.units)
        if (eval.notifications.isNotEmpty()) {
            viewModel.applyPilotLevelUps(
                leveledUnits = eval.updatedUnits,
                headline = eval.summaryHeadline,
                detail = eval.summaryDetail
            )
        }
    }

    LaunchedEffect(uiState.completedBattleCount) {
        val completedSeq = uiState.lastCompletedBattleSequence
        if (uiState.completedBattleCount > 0 && completedSeq != null) {
            willMoraleViewModel.recordCompletedCombatTelemetry(
                sequenceStamp = uiState.completedBattleCount,
                sequence = completedSeq,
                changes = uiState.recentMoraleChanges,
                currentUnits = uiState.units
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Full-screen Cinematic Battle Stage when a combat exchange or studio preview is active
        val activeCutscene = uiState.activeCutscene
        if (activeCutscene != null) {
            BackHandler {
                viewModel.onCutsceneFinished()
            }
            CinematicBattleStage(
                sequence = activeCutscene,
                onBattleFinished = { viewModel.onCutsceneFinished() }
            )
        } else {
            // BackHandler for secondary tabs or active tactical sub-commands
            BackHandler(
                enabled = uiState.activeTab != EngineTab.TACTICAL_MAP ||
                    uiState.inputMode != TacticalInputMode.IDLE_BROWSE
            ) {
                if (uiState.activeTab != EngineTab.TACTICAL_MAP) {
                    viewModel.selectTab(EngineTab.TACTICAL_MAP)
                } else {
                    viewModel.cancelCurrentOrder()
                }
            }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = VoidNavy,
        bottomBar = {
            NavigationBar(
                containerColor = CockpitSlate,
                contentColor = SteelText
            ) {
                NavigationBarItem(
                    selected = uiState.activeTab == EngineTab.TACTICAL_MAP,
                    onClick = { viewModel.selectTab(EngineTab.TACTICAL_MAP) },
                    icon = { Icon(Icons.Default.GridOn, contentDescription = "Tactical Map") },
                    label = { Text("TACTICAL", style = MaterialTheme.typography.labelSmall) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = VoidNavy,
                        selectedTextColor = AllyBeamBlue,
                        indicatorColor = AllyBeamBlue
                    ),
                    modifier = Modifier.testTag("nav_tactical_map")
                )

                NavigationBarItem(
                    selected = uiState.activeTab == EngineTab.CINEMATIC_STUDIO,
                    onClick = { viewModel.selectTab(EngineTab.CINEMATIC_STUDIO) },
                    icon = { Icon(Icons.Default.MovieFilter, contentDescription = "Battle Studio") },
                    label = { Text("ANIM STUDIO", style = MaterialTheme.typography.labelSmall) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = VoidNavy,
                        selectedTextColor = SpiritGold,
                        indicatorColor = SpiritGold
                    ),
                    modifier = Modifier.testTag("nav_cinematic_studio")
                )

                NavigationBarItem(
                    selected = uiState.activeTab == EngineTab.HANGAR_UPGRADE,
                    onClick = { viewModel.selectTab(EngineTab.HANGAR_UPGRADE) },
                    icon = { Icon(Icons.Default.Build, contentDescription = "Hangar Upgrade") },
                    label = { Text("HANGAR", style = MaterialTheme.typography.labelSmall) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = VoidNavy,
                        selectedTextColor = EnergyGreen,
                        indicatorColor = EnergyGreen
                    ),
                    modifier = Modifier.testTag("nav_hangar")
                )

                NavigationBarItem(
                    selected = uiState.activeTab == EngineTab.COMBAT_LOG,
                    onClick = { viewModel.selectTab(EngineTab.COMBAT_LOG) },
                    icon = { Icon(Icons.Default.ListAlt, contentDescription = "Combat Log") },
                    label = { Text("TELEMETRY", style = MaterialTheme.typography.labelSmall) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = VoidNavy,
                        selectedTextColor = PanelBorderCyan,
                        indicatorColor = PanelBorderCyan
                    ),
                    modifier = Modifier.testTag("nav_combat_log")
                )
            }
        }
    ) { innerPadding ->
        when (uiState.activeTab) {
            EngineTab.TACTICAL_MAP -> {
                TacticalGridScreen(
                    uiState = uiState,
                    spiritModifiersByUnitId = spiritUiState.modifiersByUnitId,
                    confidenceProfilesByUnitId = willMoraleUiState.profilesByUnitId,
                    onTileTapped = viewModel::onTileTapped,
                    onSelectInitiativeUnit = viewModel::selectUnitFromInitiativeQueue,
                    onMoveClicked = viewModel::startMoveCommand,
                    onAttackClicked = viewModel::openWeaponSelector,
                    onSpiritClicked = viewModel::openSpiritSelector,
                    onMoraleClicked = viewModel::openMoraleEventDialog,
                    onRepairClicked = viewModel::startRepairCommand,
                    onStandbyClicked = viewModel::standbySelectedUnit,
                    onCancelClicked = viewModel::cancelCurrentOrder,
                    onSelectWeapon = viewModel::selectWeaponForAttack,
                    onActivateSpirit = { spirit, targetId ->
                        val (casterId, resolvedTargetId) = viewModel.resolveSpiritCasterAndTarget(spirit, targetId)
                            ?: return@TacticalGridScreen
                        val result = spiritCommandsViewModel.activateSpiritCommand(
                            casterId = casterId,
                            spirit = spirit,
                            allUnits = uiState.units,
                            targetUnitId = resolvedTargetId
                        )
                        viewModel.applySpiritCastResult(result)
                    },
                    onCastMagicSpell = viewModel::castMagicSpellOnSelectedUnit,
                    onTriggerMoraleEvent = { event ->
                        val result = willMoraleViewModel.triggerMoraleEvent(
                            event = event,
                            units = uiState.units,
                            selectedUnitId = uiState.selectedUnitId
                        )
                        viewModel.applyMoraleUpdateResult(result)
                    },
                    onChangeDefenderStance = viewModel::changeForecastDefenderStance,
                    onAttackerWeaponChange = viewModel::changeForecastAttackerWeapon,
                    onConfirmAttack = viewModel::executeConfirmedCombat,
                    onNextReadyAlly = viewModel::selectNextReadyAlly,
                    onEndPhase = viewModel::endPlayerPhase,
                    onRestartScenario = { viewModel.loadScenario(uiState.currentScenarioIndex) },
                    modifier = Modifier.padding(innerPadding)
                )
            }

            EngineTab.CINEMATIC_STUDIO -> {
                CinematicStudioScreen(
                    config = uiState.sandboxConfig,
                    templates = viewModel.sandboxAllTemplates,
                    onUpdateConfig = viewModel::updateSandboxConfig,
                    onLaunchCutscene = viewModel::launchSandboxCutscene,
                    modifier = Modifier.padding(innerPadding)
                )
            }

            EngineTab.HANGAR_UPGRADE -> {
                HangarUpgradeScreen(
                    uiState = uiState,
                    developmentSystem = viewModel.developmentSystem,
                    pilotProgressionSystem = viewModel.pilotProgressionSystem,
                    levelProgressByUnitId = levelUiState.progressByUnitId,
                    onSelectHangarUnit = viewModel::selectHangarUnit,
                    onSelectCategory = viewModel::selectDevelopmentCategory,
                    onUpgradeStat = viewModel::upgradeUnitStat,
                    onEvolveFrame = viewModel::evolveUnitFrame,
                    onForgeWeapon = viewModel::forgeWeapon,
                    onDevelopPrototypeWeapon = viewModel::developPrototypeWeapon,
                    onInstallArmorPlating = viewModel::installArmorPlating,
                    onUpgradeBarrier = viewModel::upgradeBarrierGenerator,
                    onUpgradeMagicConduit = viewModel::upgradeMagicConduit,
                    onAttuneAffinity = viewModel::attuneElementalAffinity,
                    onResearchSpell = viewModel::researchMagicSpell,
                    onUnlockPilotPassive = viewModel::unlockPilotPassive,
                    onUpgradePilotSpirit = viewModel::upgradePilotSpirit,
                    onSelectScenario = { idx ->
                        viewModel.loadScenario(idx)
                        viewModel.selectTab(EngineTab.TACTICAL_MAP)
                    },
                    modifier = Modifier.padding(innerPadding)
                )
            }

            EngineTab.COMBAT_LOG -> {
                CombatTelemetryLogScreen(
                    logs = uiState.combatLogs,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
        }

        // Visual Level-Up Notification Banner Overlay (shown when cutscenes are not active)
        if (uiState.activeCutscene == null) {
            val activeNotification = levelUiState.activeNotification
            PilotLevelUpOverlay(
                notification = activeNotification,
                levelProgress = activeNotification?.let { levelUiState.progressByUnitId[it.unitId] },
                queuedCount = levelUiState.pendingNotifications.size,
                onDismiss = pilotLevelManager::dismissNotification,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}
