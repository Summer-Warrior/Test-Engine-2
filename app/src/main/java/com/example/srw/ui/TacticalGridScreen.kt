package com.example.srw.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.srw.engine.DamageCalculationBreakdown
import com.example.srw.engine.InitiativeEntry
import com.example.srw.engine.MoraleTier
import com.example.srw.engine.PilotMoraleSystem
import com.example.srw.engine.TacticalCombatEngine
import com.example.srw.engine.TacticalMoraleEvent
import com.example.srw.engine.TerrainMapUtility
import com.example.srw.model.*
import com.example.srw.ui.MechaCanvasRenderer.drawMapUnitSprite
import com.example.srw.viewmodel.ActiveSpiritStatModifiers
import com.example.srw.viewmodel.PilotConfidenceProfile
import com.example.srw.viewmodel.SrwEngineUiState
import com.example.srw.viewmodel.TacticalInputMode
import com.example.srw.viewmodel.WillMoraleViewModel
import com.example.ui.theme.*
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
fun TacticalGridScreen(
    uiState: SrwEngineUiState,
    onTileTapped: (GridPos) -> Unit,
    onSelectInitiativeUnit: (String) -> Unit,
    onMoveClicked: () -> Unit,
    onAttackClicked: () -> Unit,
    onSpiritClicked: () -> Unit,
    onMoraleClicked: () -> Unit = {},
    onRepairClicked: () -> Unit,
    onStandbyClicked: () -> Unit,
    onCancelClicked: () -> Unit,
    onSelectWeapon: (Weapon) -> Unit,
    onActivateSpirit: (SpiritCommand, String?) -> Unit,
    onCastMagicSpell: (MagicSpell) -> Unit = {},
    onTriggerMoraleEvent: (TacticalMoraleEvent) -> Unit = {},
    onChangeDefenderStance: (DefendStance) -> Unit,
    onAttackerWeaponChange: (Weapon) -> Unit = {},
    onConfirmAttack: () -> Unit,
    onNextReadyAlly: () -> Unit,
    onEndPhase: () -> Unit,
    onRestartScenario: () -> Unit,
    modifier: Modifier = Modifier,
    spiritModifiersByUnitId: Map<String, ActiveSpiritStatModifiers> = emptyMap(),
    confidenceProfilesByUnitId: Map<String, PilotConfidenceProfile> = emptyMap()
) {
    var showTerrainMatrix by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "gridPulse")
    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.283f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing)
        ),
        label = "pulsePhase"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // 1. Top Scenario & Phase Header Bar
        TopPhaseHeaderBar(
            uiState = uiState,
            onNextReadyAlly = onNextReadyAlly,
            onEndPhase = onEndPhase
        )

        // 2. Pilot-Stat Initiative Order Queue Strip
        InitiativeOrderTimelineBar(
            entries = uiState.initiativeOrder,
            selectedUnitId = uiState.selectedUnitId,
            onSelectUnit = onSelectInitiativeUnit
        )

        // 3. Status Banner Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PanelNavy, CutCornerShape(6.dp))
                .border(1.dp, PanelBorderCyan.copy(alpha = 0.5f), CutCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = uiState.statusBannerMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = SteelText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (uiState.inputMode != TacticalInputMode.IDLE_BROWSE) {
                TextButton(
                    onClick = onCancelClicked,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(26.dp)
                        .testTag("cmd_cancel_button")
                ) {
                    Text(
                        text = "CANCEL",
                        style = MaterialTheme.typography.labelLarge,
                        color = WarningAmber
                    )
                }
            }
        }

        // 3. Interactive 10x10 Tactical Grid Map
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            val mapSide = minOf(maxWidth, maxHeight)
            Box(
                modifier = Modifier
                    .size(mapSide)
                    .clip(CutCornerShape(10.dp))
                    .border(1.5.dp, PanelBorderCyan, CutCornerShape(10.dp))
                    .background(VoidNavy)
                    .testTag("tactical_grid_canvas")
                    .pointerInput(uiState.terrainGrid.size) {
                        detectTapGestures { offset ->
                            val rows = uiState.terrainGrid.size
                            val cols = if (rows > 0) uiState.terrainGrid[0].size else 0
                            if (rows > 0 && cols > 0) {
                                val cellW = size.width.toFloat() / cols
                                val cellH = size.height.toFloat() / rows
                                val gx = floor(offset.x / cellW).toInt().coerceIn(0, cols - 1)
                                val gy = floor(offset.y / cellH).toInt().coerceIn(0, rows - 1)
                                onTileTapped(GridPos(gx, gy))
                            }
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawTacticalMapCanvas(
                        uiState = uiState,
                        pulsePhase = pulsePhase
                    )
                }

                // Victory / Defeat Banner Overlay if mission ended
                if (uiState.isVictory || uiState.isDefeat) {
                    MissionResultOverlay(
                        isVictory = uiState.isVictory,
                        turnCount = uiState.turnNumber,
                        onRestart = onRestartScenario
                    )
                }
            }
        }

        // 4. Bottom Tactical Unit Telemetry & SRW Action Command Panel
        BottomUnitTelemetryAndCommandBar(
            uiState = uiState,
            spiritModifiersByUnitId = spiritModifiersByUnitId,
            onTerrainBadgeClicked = { showTerrainMatrix = true },
            onMoveClicked = onMoveClicked,
            onAttackClicked = onAttackClicked,
            onSpiritClicked = onSpiritClicked,
            onMoraleClicked = onMoraleClicked,
            onRepairClicked = onRepairClicked,
            onStandbyClicked = onStandbyClicked,
            onCancelClicked = onCancelClicked
        )
    }

    // 5. Modal Overlays for Weapon Selection, Spirit Commands, Morale System, and Combat Forecast
    if (uiState.inputMode == TacticalInputMode.SELECTING_WEAPON) {
        val unit = uiState.selectedUnit
        if (unit != null) {
            WeaponSelectionDialog(
                unit = unit,
                allUnits = uiState.units,
                onSelectWeapon = onSelectWeapon,
                onDismiss = onCancelClicked
            )
        }
    }

    if (uiState.inputMode == TacticalInputMode.SELECTING_SPIRIT) {
        val unit = uiState.selectedUnit
        if (unit != null) {
            SpiritCommandDialog(
                unit = unit,
                allUnits = uiState.units,
                activeSpiritModifiers = spiritModifiersByUnitId[unit.id],
                onActivateSpirit = onActivateSpirit,
                onCastMagicSpell = onCastMagicSpell,
                onDismiss = onCancelClicked
            )
        }
    }

    if (uiState.inputMode == TacticalInputMode.SELECTING_MORALE_EVENT) {
        MoraleSystemDialog(
            uiState = uiState,
            confidenceProfilesByUnitId = confidenceProfilesByUnitId,
            onTriggerEvent = onTriggerMoraleEvent,
            onDismiss = onCancelClicked
        )
    }

    if (uiState.inputMode == TacticalInputMode.SHOWING_COMBAT_FORECAST) {
        val forecast = uiState.combatForecast
        if (forecast != null) {
            CombatForecastDialog(
                forecast = forecast,
                damageBreakdown = uiState.forecastDamageBreakdown,
                allUnits = uiState.units,
                onActivateSpirit = onActivateSpirit,
                onCastMagicSpell = onCastMagicSpell,
                onStanceChange = onChangeDefenderStance,
                onWeaponChange = onAttackerWeaponChange,
                onConfirmBattle = onConfirmAttack,
                onCancel = onCancelClicked
            )
        }
    }

    if (showTerrainMatrix) {
        TerrainMatrixDialog(
            activeTerrain = uiState.terrainAtCursor,
            inspectedUnit = uiState.unitAtCursor ?: uiState.selectedUnit,
            onDismiss = { showTerrainMatrix = false }
        )
    }
}

@Composable
private fun InitiativeOrderTimelineBar(
    entries: List<InitiativeEntry>,
    selectedUnitId: String?,
    onSelectUnit: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PanelNavy.copy(alpha = 0.85f), CutCornerShape(6.dp))
            .border(1.dp, GridLineColor, CutCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("initiative_order_bar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "INIT:",
            style = MaterialTheme.typography.labelLarge,
            color = SpiritGold
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(entries, key = { it.unit.id }) { entry ->
                val isSelected = entry.unit.id == selectedUnitId
                val factionColor = if (entry.unit.faction == Faction.ALLY) AllyBeamBlue else EnemyCrimson
                val borderAlpha = if (entry.hasActed) 0.3f else 0.9f

                Surface(
                    onClick = { onSelectUnit(entry.unit.id) },
                    color = if (isSelected) factionColor.copy(alpha = 0.25f) else VoidNavy,
                    shape = CutCornerShape(5.dp),
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) SpiritGold else factionColor.copy(alpha = borderAlpha)
                    ),
                    modifier = Modifier.testTag("init_chip_${entry.unit.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "#${entry.rank}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (entry.hasActed) MutedTelemetry else SpiritGold
                        )
                        Text(
                            text = entry.unit.pilot.callsign,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (entry.hasActed) MutedTelemetry else Color.White
                        )
                        Text(
                            text = "${entry.initiativeScore}",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (entry.hasActed) MutedTelemetry else factionColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopPhaseHeaderBar(
    uiState: SrwEngineUiState,
    onNextReadyAlly: () -> Unit,
    onEndPhase: () -> Unit
) {
    val isPlayerPhase = uiState.currentPhase == Faction.ALLY
    val phaseColor = if (isPlayerPhase) AllyBeamBlue else EnemyCrimson
    val readyAllyCount = uiState.units.count {
        it.faction == Faction.ALLY && it.isAlive() && !it.hasActed
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PanelNavy, CutCornerShape(8.dp))
            .border(1.dp, phaseColor.copy(alpha = 0.75f), CutCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = phaseColor.copy(alpha = 0.2f),
                    shape = CutCornerShape(4.dp),
                    border = BorderStroke(1.dp, phaseColor)
                ) {
                    Text(
                        text = if (isPlayerPhase) "PLAYER PHASE" else "ENEMY PHASE",
                        style = MaterialTheme.typography.labelLarge,
                        color = phaseColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = "TURN ${uiState.turnNumber}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Text(
                    text = "• ${uiState.credits} CR",
                    style = MaterialTheme.typography.labelLarge,
                    color = SpiritGold
                )
            }
            Text(
                text = "${uiState.currentScenario?.code ?: "STAGE"}: ${uiState.currentScenario?.title ?: ""} • Ready Units: $readyAllyCount",
                style = MaterialTheme.typography.labelSmall,
                color = MutedTelemetry,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(
                onClick = onNextReadyAlly,
                enabled = isPlayerPhase && readyAllyCount > 0 && !uiState.isEnemyAiThinking,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                border = BorderStroke(1.dp, AllyBeamBlue),
                modifier = Modifier
                    .height(36.dp)
                    .testTag("next_unit_button")
            ) {
                Text(
                    text = "NEXT",
                    style = MaterialTheme.typography.labelLarge,
                    color = AllyBeamBlue
                )
            }

            Button(
                onClick = onEndPhase,
                enabled = isPlayerPhase && !uiState.isEnemyAiThinking,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EnemyCrimson),
                modifier = Modifier
                    .height(36.dp)
                    .testTag("end_phase_button")
            ) {
                Text(
                    text = "END TURN",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White
                )
            }
        }
    }
}

private fun DrawScope.drawTacticalMapCanvas(
    uiState: SrwEngineUiState,
    pulsePhase: Float
) {
    val grid = uiState.terrainGrid
    val rows = grid.size
    if (rows == 0) return
    val cols = grid[0].size

    val cellW = size.width / cols
    val cellH = size.height / rows

    // 1. Draw Terrain Tiles & Tactical Overlays
    for (y in 0 until rows) {
        for (x in 0 until cols) {
            val pos = GridPos(x, y)
            val terrain = grid[y][x]
            val topLeft = Offset(x * cellW, y * cellH)
            val tileSize = Size(cellW, cellH)

            // Base terrain fill
            drawRect(
                color = terrain.tilePrimaryColor,
                topLeft = topLeft,
                size = tileSize
            )

            // Terrain micro-detail art
            drawTerrainTileDetails(
                terrain = terrain,
                topLeft = topLeft,
                cellW = cellW,
                cellH = cellH
            )

            // Reachable Movement Overlay (Cyan)
            if (pos in uiState.reachableTiles) {
                drawRect(
                    color = AllyBeamBlue.copy(alpha = 0.28f),
                    topLeft = topLeft,
                    size = tileSize
                )
                drawRect(
                    color = AllyBeamBlue.copy(alpha = 0.85f),
                    topLeft = Offset(topLeft.x + 2f, topLeft.y + 2f),
                    size = Size(cellW - 4f, cellH - 4f),
                    style = Stroke(width = 2f)
                )
            }

            // Weapon Range Overlay (Crimson)
            if (pos in uiState.weaponRangeTiles) {
                drawRect(
                    color = EnemyCrimson.copy(alpha = 0.28f),
                    topLeft = topLeft,
                    size = tileSize
                )
                drawRect(
                    color = EnemyCrimson.copy(alpha = 0.85f),
                    topLeft = Offset(topLeft.x + 2f, topLeft.y + 2f),
                    size = Size(cellW - 4f, cellH - 4f),
                    style = Stroke(width = 2f)
                )
            }

            // Repair Range Overlay (Emerald)
            if (pos in uiState.repairRangeTiles) {
                drawRect(
                    color = EnergyGreen.copy(alpha = 0.25f),
                    topLeft = topLeft,
                    size = tileSize
                )
                drawRect(
                    color = EnergyGreen,
                    topLeft = Offset(topLeft.x + 2f, topLeft.y + 2f),
                    size = Size(cellW - 4f, cellH - 4f),
                    style = Stroke(width = 2f)
                )
            }

            // Subtle Grid Lines
            drawRect(
                color = GridLineColor.copy(alpha = 0.65f),
                topLeft = topLeft,
                size = tileSize,
                style = Stroke(width = 1f)
            )
        }
    }

    // 2. Draw Movement Trajectory Line if unit moved
    if (uiState.movementTrajectory.size >= 2) {
        val path = Path()
        uiState.movementTrajectory.forEachIndexed { idx, p ->
            val cx = (p.x + 0.5f) * cellW
            val cy = (p.y + 0.5f) * cellH
            if (idx == 0) path.moveTo(cx, cy) else path.lineTo(cx, cy)
        }
        drawPath(
            path = path,
            color = SpiritGold,
            style = Stroke(
                width = 4f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
            )
        )
    }

    // 3. Draw Command Ship Aura Ring around selected unit if it has a Command Aura
    val activeUnit = uiState.unitAtCursor ?: uiState.selectedUnit
    if (activeUnit != null && activeUnit.commandAuraRadius > 0) {
        for (y in 0 until rows) {
            for (x in 0 until cols) {
                val p = GridPos(x, y)
                if (p.distanceTo(activeUnit.pos) <= activeUnit.commandAuraRadius) {
                    drawRect(
                        color = SpiritGold.copy(alpha = 0.09f),
                        topLeft = Offset(x * cellW, y * cellH),
                        size = Size(cellW, cellH)
                    )
                }
            }
        }
    }

    // 4. Draw All Living Mecha Units on Grid
    for (unit in uiState.units) {
        if (!unit.isAlive()) continue
        val cellLeft = unit.pos.x * cellW
        val cellTop = unit.pos.y * cellH

        drawUnitInCell(
            unit = unit,
            cellLeft = cellLeft,
            cellTop = cellTop,
            cellW = cellW,
            cellH = cellH,
            isTargetable = unit.pos in uiState.weaponRangeTiles && unit.faction == Faction.ENEMY,
            pulsePhase = pulsePhase
        )
    }

    // 5. Draw Tactical Selection Cursor Brackets at selectedPos
    val cursorLeft = uiState.selectedPos.x * cellW
    val cursorTop = uiState.selectedPos.y * cellH
    val bracketLen = cellW * 0.28f
    val cursorColor = SpiritGold

    // Top-Left corner bracket
    drawLine(cursorColor, Offset(cursorLeft + 2f, cursorTop + 2f), Offset(cursorLeft + bracketLen, cursorTop + 2f), strokeWidth = 4f)
    drawLine(cursorColor, Offset(cursorLeft + 2f, cursorTop + 2f), Offset(cursorLeft + 2f, cursorTop + bracketLen), strokeWidth = 4f)
    // Top-Right
    drawLine(cursorColor, Offset(cursorLeft + cellW - 2f, cursorTop + 2f), Offset(cursorLeft + cellW - bracketLen, cursorTop + 2f), strokeWidth = 4f)
    drawLine(cursorColor, Offset(cursorLeft + cellW - 2f, cursorTop + 2f), Offset(cursorLeft + cellW - 2f, cursorTop + bracketLen), strokeWidth = 4f)
    // Bottom-Left
    drawLine(cursorColor, Offset(cursorLeft + 2f, cursorTop + cellH - 2f), Offset(cursorLeft + bracketLen, cursorTop + cellH - 2f), strokeWidth = 4f)
    drawLine(cursorColor, Offset(cursorLeft + 2f, cursorTop + cellH - 2f), Offset(cursorLeft + 2f, cursorTop + cellH - bracketLen), strokeWidth = 4f)
    // Bottom-Right
    drawLine(cursorColor, Offset(cursorLeft + cellW - 2f, cursorTop + cellH - 2f), Offset(cursorLeft + cellW - bracketLen, cursorTop + cellH - 2f), strokeWidth = 4f)
    drawLine(cursorColor, Offset(cursorLeft + cellW - 2f, cursorTop + cellH - 2f), Offset(cursorLeft + cellW - 2f, cursorTop + cellH - bracketLen), strokeWidth = 4f)
}

private fun DrawScope.drawTerrainTileDetails(
    terrain: TerrainType,
    topLeft: Offset,
    cellW: Float,
    cellH: Float
) {
    when (terrain) {
        TerrainType.BASE -> {
            // Octagonal/Cross Command Pad
            drawRoundRect(
                color = terrain.tileAccentColor.copy(alpha = 0.35f),
                topLeft = Offset(topLeft.x + cellW * 0.16f, topLeft.y + cellH * 0.16f),
                size = Size(cellW * 0.68f, cellH * 0.68f),
                cornerRadius = CornerRadius(4f, 4f),
                style = Stroke(width = 2f)
            )
            drawLine(
                color = terrain.tileAccentColor.copy(alpha = 0.4f),
                start = Offset(topLeft.x + cellW * 0.5f, topLeft.y + cellH * 0.2f),
                end = Offset(topLeft.x + cellW * 0.5f, topLeft.y + cellH * 0.8f),
                strokeWidth = 1.5f
            )
        }
        TerrainType.CITY -> {
            // Mini skyscraper blocks
            drawRect(
                color = terrain.tileAccentColor.copy(alpha = 0.35f),
                topLeft = Offset(topLeft.x + cellW * 0.18f, topLeft.y + cellH * 0.25f),
                size = Size(cellW * 0.22f, cellH * 0.52f)
            )
            drawRect(
                color = terrain.tileAccentColor.copy(alpha = 0.45f),
                topLeft = Offset(topLeft.x + cellW * 0.48f, topLeft.y + cellH * 0.18f),
                size = Size(cellW * 0.28f, cellH * 0.60f)
            )
        }
        TerrainType.FOREST -> {
            // Dual tactical forest canopy triangles
            val leftCanopy = Path().apply {
                moveTo(topLeft.x + cellW * 0.16f, topLeft.y + cellH * 0.76f)
                lineTo(topLeft.x + cellW * 0.36f, topLeft.y + cellH * 0.24f)
                lineTo(topLeft.x + cellW * 0.56f, topLeft.y + cellH * 0.76f)
                close()
            }
            val rightCanopy = Path().apply {
                moveTo(topLeft.x + cellW * 0.42f, topLeft.y + cellH * 0.78f)
                lineTo(topLeft.x + cellW * 0.66f, topLeft.y + cellH * 0.20f)
                lineTo(topLeft.x + cellW * 0.86f, topLeft.y + cellH * 0.78f)
                close()
            }
            drawPath(leftCanopy, terrain.tileAccentColor.copy(alpha = 0.50f))
            drawPath(rightCanopy, terrain.tileAccentColor.copy(alpha = 0.68f))
        }
        TerrainType.MOUNTAIN -> {
            val peak = Path().apply {
                moveTo(topLeft.x + cellW * 0.15f, topLeft.y + cellH * 0.78f)
                lineTo(topLeft.x + cellW * 0.5f, topLeft.y + cellH * 0.22f)
                lineTo(topLeft.x + cellW * 0.85f, topLeft.y + cellH * 0.78f)
                close()
            }
            drawPath(peak, terrain.tileAccentColor.copy(alpha = 0.45f))
        }
        TerrainType.ASTEROID -> {
            drawCircle(
                color = terrain.tileAccentColor.copy(alpha = 0.55f),
                radius = cellW * 0.22f,
                center = Offset(topLeft.x + cellW * 0.42f, topLeft.y + cellH * 0.45f)
            )
            drawCircle(
                color = terrain.tileAccentColor.copy(alpha = 0.4f),
                radius = cellW * 0.14f,
                center = Offset(topLeft.x + cellW * 0.68f, topLeft.y + cellH * 0.62f)
            )
        }
        TerrainType.SEA -> {
            // Stylized tactical water wave bands
            val waveColor = PanelBorderCyan.copy(alpha = 0.45f)
            listOf(0.32f, 0.52f, 0.72f).forEachIndexed { idx, yRatio ->
                val y = topLeft.y + cellH * yRatio
                val xOffset = if (idx % 2 == 0) 0.14f else 0.22f
                val wavePath = Path().apply {
                    moveTo(topLeft.x + cellW * xOffset, y)
                    quadraticTo(
                        topLeft.x + cellW * (xOffset + 0.16f),
                        y - cellH * 0.08f,
                        topLeft.x + cellW * (xOffset + 0.32f),
                        y
                    )
                    quadraticTo(
                        topLeft.x + cellW * (xOffset + 0.48f),
                        y + cellH * 0.08f,
                        topLeft.x + cellW * (xOffset + 0.64f),
                        y
                    )
                }
                drawPath(wavePath, color = waveColor, style = Stroke(width = 1.8f))
            }
        }
        TerrainType.SPACE -> {
            drawCircle(
                color = Color.White.copy(alpha = 0.35f),
                radius = 1.5f,
                center = Offset(topLeft.x + cellW * 0.3f, topLeft.y + cellH * 0.3f)
            )
            drawCircle(
                color = PanelBorderCyan.copy(alpha = 0.35f),
                radius = 2f,
                center = Offset(topLeft.x + cellW * 0.72f, topLeft.y + cellH * 0.68f)
            )
        }
        else -> Unit
    }
}

private fun DrawScope.drawUnitInCell(
    unit: MechaUnit,
    cellLeft: Float,
    cellTop: Float,
    cellW: Float,
    cellH: Float,
    isTargetable: Boolean,
    pulsePhase: Float
) {
    val cx = cellLeft + cellW * 0.5f
    val cy = cellTop + cellH * 0.46f
    val r = minOf(cellW, cellH) * 0.36f

    val factionColor = if (unit.faction == Faction.ALLY) AllyBeamBlue else EnemyCrimson
    val alphaMod = if (unit.hasActed) 0.42f else 1f

    // Faction circular base
    drawCircle(
        color = factionColor.copy(alpha = 0.26f * alphaMod),
        radius = r,
        center = Offset(cx, cy)
    )
    drawCircle(
        color = factionColor.copy(alpha = 0.9f * alphaMod),
        radius = r,
        center = Offset(cx, cy),
        style = Stroke(width = 2.5f)
    )

    // Mecha Silhouette inside cell
    val unitColor = when (unit.visualRig) {
        MechaVisualRig.KAISER_BLAZE -> Color(0xFFFF3D00)
        MechaVisualRig.ASTRAL_GUNDAM -> Color(0xFF00E5FF)
        MechaVisualRig.VALKYRIE_ZERO -> Color(0xFFFFEA00)
        MechaVisualRig.AEGIS_BATTLESHIP -> Color(0xFFB388FF)
        MechaVisualRig.ZAKU_VANGUARD -> Color(0xFFFF5252)
        MechaVisualRig.GRAV_DESTROYER -> Color(0xFFFF9100)
        MechaVisualRig.EMPEROR_ZEPH -> Color(0xFFD500F9)
    }.copy(alpha = alphaMod)

    val crestPath = Path().apply {
        moveTo(cx, cy - r * 0.78f)
        lineTo(cx - r * 0.68f, cy + r * 0.42f)
        lineTo(cx, cy + r * 0.18f)
        lineTo(cx + r * 0.68f, cy + r * 0.42f)
        close()
    }
    drawPath(crestPath, unitColor)

    // Glowing Visor / Core Dot
    drawCircle(
        color = unit.pilot.visorColor.copy(alpha = alphaMod),
        radius = r * 0.24f,
        center = Offset(cx, cy - r * 0.05f)
    )

    // Pilot Morale Overdrive / Max Limit Aura Ring (Will >= 130)
    if (unit.pilot.will >= 130) {
        val moraleTier = MoraleTier.fromWill(unit.pilot.will)
        drawCircle(
            color = moraleTier.badgeColor.copy(alpha = 0.65f * alphaMod),
            radius = r * 1.10f,
            center = Offset(cx, cy),
            style = Stroke(width = 2f)
        )
    }

    // Active Spirit Debuff Ring (if unit is afflicted with ANALYZED, CONFUSED, or DAUNTED)
    if (unit.activeDebuffs.isNotEmpty()) {
        val debuffColor = unit.activeDebuffs.first().badgeColor
        drawCircle(
            color = debuffColor.copy(alpha = 0.85f),
            radius = r * 1.18f,
            center = Offset(cx, cy),
            style = Stroke(
                width = 2.2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), pulsePhase * 8f)
            )
        )
    }

    // Target Lock-On Crosshair if Enemy is in Weapon Range
    if (isTargetable) {
        drawCircle(
            color = SpiritGold,
            radius = r * 1.15f,
            center = Offset(cx, cy),
            style = Stroke(width = 2.5f)
        )
    }

    // Mini HP Bar at bottom of cell
    val hpRatio = (unit.currentHp.toFloat() / unit.maxHp.toFloat()).coerceIn(0f, 1f)
    val barWidth = cellW * 0.78f
    val barHeight = (cellH * 0.11f).coerceAtLeast(3.5f)
    val barLeft = cellLeft + (cellW - barWidth) / 2f
    val barTop = cellTop + cellH - barHeight - 2.5f

    drawRect(
        color = VoidNavy,
        topLeft = Offset(barLeft, barTop),
        size = Size(barWidth, barHeight)
    )
    drawRect(
        color = when {
            hpRatio < 0.3f -> EnemyCrimson
            unit.faction == Faction.ALLY -> EnergyGreen
            else -> WarningAmber
        },
        topLeft = Offset(barLeft, barTop),
        size = Size(barWidth * hpRatio, barHeight)
    )
}

@Composable
private fun BottomUnitTelemetryAndCommandBar(
    uiState: SrwEngineUiState,
    spiritModifiersByUnitId: Map<String, ActiveSpiritStatModifiers>,
    onTerrainBadgeClicked: () -> Unit,
    onMoveClicked: () -> Unit,
    onAttackClicked: () -> Unit,
    onSpiritClicked: () -> Unit,
    onMoraleClicked: () -> Unit,
    onRepairClicked: () -> Unit,
    onStandbyClicked: () -> Unit,
    onCancelClicked: () -> Unit
) {
    val inspectedUnit = uiState.unitAtCursor ?: uiState.selectedUnit
    val activeSpiritMods = inspectedUnit?.let { spiritModifiersByUnitId[it.id] }
    val terrain = uiState.terrainAtCursor
    val canControlUnit = inspectedUnit != null &&
        inspectedUnit.faction == Faction.ALLY &&
        !inspectedUnit.hasActed &&
        uiState.currentPhase == Faction.ALLY &&
        !uiState.isEnemyAiThinking

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PanelNavy, CutCornerShape(10.dp))
            .border(1.dp, PanelBorderCyan.copy(alpha = 0.7f), CutCornerShape(10.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (inspectedUnit != null) {
            val hpRatio = (inspectedUnit.currentHp.toFloat() / inspectedUnit.maxHp.toFloat()).coerceIn(0f, 1f)
            val enRatio = (inspectedUnit.currentEn.toFloat() / inspectedUnit.maxEn.toFloat()).coerceIn(0f, 1f)
            val moraleMods = remember(inspectedUnit) {
                com.example.srw.engine.TacticalCombatEngine.defaultCombatManager
                    .pilotMoraleSystem
                    .computeUnitStatModifiers(inspectedUnit)
            }
            val moraleTier = moraleMods.tier

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = if (inspectedUnit.faction == Faction.ALLY) AllyDeepBlue else EnemyDarkRed,
                        shape = CutCornerShape(4.dp)
                    ) {
                        Text(
                            text = inspectedUnit.archetype.badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = "${inspectedUnit.name} (LV.${inspectedUnit.pilot.level} ${inspectedUnit.pilot.name})",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "T${inspectedUnit.frameTier}•${inspectedUnit.elementalAffinity.kanji}",
                        style = MaterialTheme.typography.labelLarge,
                        color = inspectedUnit.elementalAffinity.badgeColor
                    )
                    Surface(
                        onClick = onMoraleClicked,
                        color = moraleTier.badgeColor.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, moraleTier.badgeColor)
                    ) {
                        Text(
                            text = "WILL ${inspectedUnit.pilot.will} [${moraleTier.jpKanji} ×${"%.2f".format(moraleTier.attackOutputMultiplier)}]",
                            style = MaterialTheme.typography.labelSmall,
                            color = moraleTier.badgeColor,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = "SP ${inspectedUnit.pilot.currentSp}/${inspectedUnit.effectiveMaxSp}",
                        style = MaterialTheme.typography.labelLarge,
                        color = PanelBorderCyan
                    )
                }
            }

            // HP & EN Gauges + Terrain Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("HP", style = MaterialTheme.typography.labelSmall, color = MutedTelemetry)
                        Text(
                            "${inspectedUnit.currentHp}/${inspectedUnit.maxHp}",
                            style = MaterialTheme.typography.labelSmall,
                            color = EnergyGreen
                        )
                    }
                    LinearProgressIndicator(
                        progress = { hpRatio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = if (hpRatio < 0.3f) EnemyCrimson else EnergyGreen,
                        trackColor = VoidNavy
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("EN", style = MaterialTheme.typography.labelSmall, color = MutedTelemetry)
                        Text(
                            "${inspectedUnit.currentEn}/${inspectedUnit.maxEn}",
                            style = MaterialTheme.typography.labelSmall,
                            color = PanelBorderCyan
                        )
                    }
                    LinearProgressIndicator(
                        progress = { enRatio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = PanelBorderCyan,
                        trackColor = VoidNavy
                    )
                }

                // Clickable Terrain Chip (Defensive + Offensive bonuses from GridTerrainSystem)
                val cellBonus = remember(inspectedUnit.id, terrain) {
                    com.example.srw.engine.TacticalCombatEngine.defaultCombatManager
                        .gridTerrainSystem
                        .resolveUnitTerrainBonus(inspectedUnit, terrain)
                }
                val hitSign = if (cellBonus.effectiveHitBonusPercent >= 0) "+${cellBonus.effectiveHitBonusPercent}%" else "${cellBonus.effectiveHitBonusPercent}%"
                Surface(
                    onClick = onTerrainBadgeClicked,
                    color = VoidNavy,
                    shape = CutCornerShape(6.dp),
                    border = BorderStroke(1.dp, terrain.tileAccentColor),
                    modifier = Modifier.testTag("terrain_telemetry_chip")
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "${terrain.shortCode} [S:${cellBonus.adaptationGrade}] +${cellBonus.effectiveDefenseBonusPercent}%D +${cellBonus.effectiveEvasionBonusPercent}%E",
                            style = MaterialTheme.typography.labelSmall,
                            color = EnergyGreen
                        )
                        Text(
                            text = "+${cellBonus.effectiveAttackBonusPercent}%ATK $hitSign HIT +${cellBonus.effectiveCritBonusPercent}%CRT",
                            style = MaterialTheme.typography.labelSmall,
                            color = SpiritGold
                        )
                    }
                }
            }

            // Live Morale Unit Stat Modifiers Telemetry Strip
            val atkSign = if (moraleMods.attackOutputPercent >= 0) "+${moraleMods.attackOutputPercent}%" else "${moraleMods.attackOutputPercent}%"
            val defSign = if (moraleMods.defenseModifierPercent >= 0) "+${moraleMods.defenseModifierPercent}%" else "${moraleMods.defenseModifierPercent}%"
            val mobSign = if (moraleMods.mobilityDelta >= 0) "+${moraleMods.mobilityDelta}" else "${moraleMods.mobilityDelta}"
            val hitSign = if (moraleMods.hitBonusPercent >= 0) "+${moraleMods.hitBonusPercent}%" else "${moraleMods.hitBonusPercent}%"
            val critSign = if (moraleMods.critBonusPercent >= 0) "+${moraleMods.critBonusPercent}%" else "${moraleMods.critBonusPercent}%"
            Surface(
                onClick = onMoraleClicked,
                color = VoidNavy.copy(alpha = 0.85f),
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, moraleTier.badgeColor.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("unit_morale_stat_modifiers_strip")
            ) {
                Text(
                    text = "STAT MODS [${moraleTier.displayName}]: ATK $atkSign • DEF $defSign • MOB ${moraleMods.effectiveMobility} ($mobSign) • HIT/EVA $hitSign • CRT $critSign",
                    style = MaterialTheme.typography.labelSmall,
                    color = moraleTier.badgeColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }

            // Active Spirit Command & Debuff Badges (with net Spirit stat modifiers)
            if (inspectedUnit.activeSpirits.isNotEmpty() || inspectedUnit.activeDebuffs.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (activeSpiritMods != null && activeSpiritMods.activeEffectTags.isNotEmpty()) {
                        items(activeSpiritMods.activeEffectTags) { effectTag ->
                            Surface(
                                color = SpiritGold.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, SpiritGold)
                            ) {
                                Text(
                                    text = effectTag,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SpiritGold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    } else {
                        items(inspectedUnit.activeSpirits.toList()) { sp ->
                            Surface(
                                color = sp.badgeColor.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, sp.badgeColor)
                            ) {
                                Text(
                                    text = "${sp.codeName} (${sp.jpKanji})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = sp.badgeColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    items(inspectedUnit.activeDebuffs.toList()) { debuff ->
                        Surface(
                            color = debuff.badgeColor.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, debuff.badgeColor)
                        ) {
                            Text(
                                text = "DEBUFF: ${debuff.codeName} (${debuff.jpKanji})",
                                style = MaterialTheme.typography.labelSmall,
                                color = debuff.badgeColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        // SRW Tactical Command Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Button(
                onClick = onMoveClicked,
                enabled = canControlUnit && !(inspectedUnit?.hasMoved ?: true),
                colors = ButtonDefaults.buttonColors(containerColor = AllyDeepBlue),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .testTag("cmd_move_button")
            ) {
                Text("MOVE", style = MaterialTheme.typography.labelLarge)
            }

            Button(
                onClick = onAttackClicked,
                enabled = canControlUnit,
                colors = ButtonDefaults.buttonColors(containerColor = EnemyCrimson),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .testTag("cmd_attack_button")
            ) {
                Text("ATTACK", style = MaterialTheme.typography.labelLarge)
            }

            Button(
                onClick = onSpiritClicked,
                enabled = canControlUnit,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A3800)),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .testTag("cmd_spirit_button")
            ) {
                Text("SPIRIT", style = MaterialTheme.typography.labelLarge, color = SpiritGold)
            }

            Button(
                onClick = onMoraleClicked,
                enabled = !uiState.isEnemyAiThinking,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF281845)),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .testTag("cmd_morale_button")
            ) {
                Text("WILL", style = MaterialTheme.typography.labelLarge, color = Color(0xFFE040FB))
            }

            if (inspectedUnit?.archetype == MechaArchetype.COMMAND_SHIP) {
                Button(
                    onClick = onRepairClicked,
                    enabled = canControlUnit,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF004D40)),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .testTag("cmd_repair_button")
                ) {
                    Text("REPAIR", style = MaterialTheme.typography.labelLarge, color = EnergyGreen)
                }
            }

            OutlinedButton(
                onClick = onStandbyClicked,
                enabled = canControlUnit,
                border = BorderStroke(1.dp, MutedTelemetry),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .testTag("cmd_standby_button")
            ) {
                Text("WAIT", style = MaterialTheme.typography.labelLarge, color = SteelText)
            }
        }
    }
}

@Composable
private fun WeaponSelectionDialog(
    unit: MechaUnit,
    allUnits: List<MechaUnit>,
    onSelectWeapon: (Weapon) -> Unit,
    onDismiss: () -> Unit
) {
    val moraleTier = MoraleTier.fromWill(unit.pilot.will)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CockpitSlate,
        title = {
            Column {
                Text(
                    text = "SELECT WEAPON // ${unit.name.uppercase()}",
                    style = MaterialTheme.typography.titleLarge,
                    color = PanelBorderCyan
                )
                Text(
                    text = "Will: ${unit.pilot.will} [${moraleTier.displayName} ${moraleTier.jpKanji} • ×${"%.2f".format(moraleTier.attackOutputMultiplier)} ATK] • EN: ${unit.currentEn}/${unit.maxEn}",
                    style = MaterialTheme.typography.labelSmall,
                    color = moraleTier.badgeColor
                )
            }
        },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(unit.weapons) { weapon ->
                    val usable = unit.canUseWeapon(weapon, afterMove = unit.hasMoved)
                    val willUnlocked = unit.pilot.will >= weapon.willReq
                    val willDeficit = (weapon.willReq - unit.pilot.will).coerceAtLeast(0)
                    val baseEffPwr = weapon.effectivePower(unit.upgradeWeapon, unit.magicLevel)
                    val moraleScaledPwr = (baseEffPwr * moraleTier.attackOutputMultiplier).roundToInt()

                    val targetsInRange = allUnits.count { enemy ->
                        enemy.isAlive() &&
                            enemy.faction != unit.faction &&
                            unit.pos.distanceTo(enemy.pos) in weapon.minRange..weapon.maxRange
                    }

                    Surface(
                        onClick = { if (usable) onSelectWeapon(weapon) },
                        enabled = usable,
                        color = if (usable) PanelNavy else VoidNavy.copy(alpha = 0.6f),
                        shape = CutCornerShape(8.dp),
                        border = BorderStroke(
                            1.dp,
                            if (!willUnlocked) EnemyCrimson.copy(alpha = 0.7f)
                            else if (usable && targetsInRange > 0) EnemyCrimson
                            else if (usable) PanelBorderCyan
                            else MutedTelemetry.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("weapon_card_${weapon.id}")
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (weapon.isPostMove) {
                                        Surface(
                                            color = AllyBeamBlue.copy(alpha = 0.25f),
                                            shape = RoundedCornerShape(3.dp)
                                        ) {
                                            Text(
                                                text = "P",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = AllyBeamBlue,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = if (weapon.forgeLevel > 0) "${weapon.name} +${weapon.forgeLevel}" else weapon.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (usable) Color.White else MutedTelemetry
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "PWR $moraleScaledPwr",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (willUnlocked) SpiritGold else MutedTelemetry
                                    )
                                    if (moraleScaledPwr != baseEffPwr) {
                                        Text(
                                            text = "Base $baseEffPwr ×${"%.2f".format(moraleTier.attackOutputMultiplier)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = moraleTier.badgeColor
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "RNG ${weapon.minRange}~${weapon.maxRange} • ${
                                        if (weapon.enCost > 0) "EN ${weapon.enCost}"
                                        else if (weapon.maxAmmo > 0) "AMMO ${weapon.currentAmmo}/${weapon.maxAmmo}"
                                        else "0 EN"
                                    }",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MutedTelemetry
                                )
                                Surface(
                                    color = if (willUnlocked) EnergyGreen.copy(alpha = 0.16f) else EnemyCrimson.copy(alpha = 0.22f),
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (willUnlocked) EnergyGreen else EnemyCrimson
                                    )
                                ) {
                                    Text(
                                        text = if (willUnlocked) {
                                            "WILL ${weapon.willReq} UNLOCKED • ${if (targetsInRange > 0) "$targetsInRange IN RNG" else "OK"}"
                                        } else {
                                            "LOCKED: REQ ${weapon.willReq} WILL (+$willDeficit)"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (willUnlocked) EnergyGreen else EnemyCrimson,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("CLOSE", color = PanelBorderCyan)
            }
        }
    )
}

@Composable
private fun SpiritCommandDialog(
    unit: MechaUnit,
    allUnits: List<MechaUnit>,
    activeSpiritModifiers: ActiveSpiritStatModifiers?,
    onActivateSpirit: (SpiritCommand, String?) -> Unit,
    onCastMagicSpell: (MagicSpell) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCategoryFilter by remember { mutableStateOf<SpiritCategory?>(null) }
    var pendingTargetedSpirit by remember { mutableStateOf<SpiritCommand?>(null) }
    val progressionSystem = remember { com.example.srw.engine.TacticalCombatEngine.defaultCombatManager.pilotProgressionSystem }

    val filteredSpirits = remember(unit.pilot.spirits, selectedCategoryFilter) {
        if (selectedCategoryFilter == null) {
            unit.pilot.spirits
        } else {
            unit.pilot.spirits.filter { it.category == selectedCategoryFilter }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CockpitSlate,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (pendingTargetedSpirit != null) {
                        "SELECT TARGET // ${pendingTargetedSpirit!!.codeName} (${pendingTargetedSpirit!!.jpKanji})"
                    } else {
                        "SPIRITS & ARCANE MAGIC // ${unit.pilot.name.uppercase()}"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = SpiritGold
                )
                Text(
                    text = "SP: ${unit.pilot.currentSp}/${unit.effectiveMaxSp} • Will: ${unit.pilot.will} • XP: ${unit.pilot.xp} (${unit.pilot.rankTitle})",
                    style = MaterialTheme.typography.labelSmall,
                    color = PanelBorderCyan
                )
                if (activeSpiritModifiers != null && activeSpiritModifiers.activeEffectTags.isNotEmpty()) {
                    Text(
                        text = "ACTIVE EFFECTS: ${activeSpiritModifiers.activeEffectTags.joinToString(" • ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = EnergyGreen
                    )
                }
            }
        },
        text = {
            val targetSpirit = pendingTargetedSpirit
            if (targetSpirit != null) {
                val eligibleTargets = remember(targetSpirit, allUnits, unit.id) {
                    com.example.srw.engine.TacticalCombatEngine.defaultCombatManager
                        .spiritCommandSystem
                        .getEligibleTargets(unit, targetSpirit, allUnits)
                }
                val targetSpiritCost = progressionSystem.getEffectiveSpCost(unit.pilot, targetSpirit)
                val targetSpiritRank = unit.pilot.spiritRank(targetSpirit)
                val targetSpiritSummary = progressionSystem.getEffectiveSpiritSummary(unit.pilot, targetSpirit)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = targetSpirit.badgeColor.copy(alpha = 0.15f),
                        shape = CutCornerShape(6.dp),
                        border = BorderStroke(1.dp, targetSpirit.badgeColor)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "${targetSpirit.codeName} (${targetSpirit.jpKanji}) • ${progressionSystem.getRankBadgeLabel(targetSpiritRank)} • Cost: $targetSpiritCost SP • ${targetSpirit.targetType.label}",
                                style = MaterialTheme.typography.labelLarge,
                                color = targetSpirit.badgeColor
                            )
                            Text(
                                text = targetSpiritSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = SteelText
                            )
                        }
                    }

                    Text(
                        text = "TAP UNIT TO RECEIVE [${targetSpirit.codeName}]:",
                        style = MaterialTheme.typography.labelSmall,
                        color = SpiritGold
                    )

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.heightIn(max = 280.dp)
                    ) {
                        items(eligibleTargets, key = { it.id }) { targetUnit ->
                            val isAlly = targetUnit.faction == Faction.ALLY
                            val borderCol = if (isAlly) AllyBeamBlue else EnemyCrimson
                            Surface(
                                onClick = {
                                    onActivateSpirit(targetSpirit, targetUnit.id)
                                    pendingTargetedSpirit = null
                                },
                                color = PanelNavy,
                                shape = CutCornerShape(8.dp),
                                border = BorderStroke(1.dp, borderCol),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("spirit_target_${targetUnit.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${targetUnit.name} (${targetUnit.pilot.name})",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "HP ${targetUnit.currentHp}/${targetUnit.maxHp} • EN ${targetUnit.currentEn}/${targetUnit.maxEn} • Will ${targetUnit.pilot.will}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MutedTelemetry
                                        )
                                    }
                                    Surface(
                                        color = targetSpirit.badgeColor.copy(alpha = 0.22f),
                                        shape = RoundedCornerShape(4.dp),
                                        border = BorderStroke(1.dp, targetSpirit.badgeColor)
                                    ) {
                                        Text(
                                            text = "CAST",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = targetSpirit.badgeColor,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Category Filter Row: ALL / BUFF / DEBUFF / HEAL
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            Surface(
                                onClick = { selectedCategoryFilter = null },
                                color = if (selectedCategoryFilter == null) SpiritGold.copy(alpha = 0.24f) else VoidNavy,
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (selectedCategoryFilter == null) SpiritGold else MutedTelemetry.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.testTag("spirit_filter_all")
                            ) {
                                Text(
                                    text = "ALL (${unit.pilot.spirits.size})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selectedCategoryFilter == null) SpiritGold else SteelText,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        items(SpiritCategory.entries) { category ->
                            val count = unit.pilot.spirits.count { it.category == category }
                            val isSelected = selectedCategoryFilter == category
                            Surface(
                                onClick = { selectedCategoryFilter = category },
                                color = if (isSelected) category.badgeColor.copy(alpha = 0.24f) else VoidNavy,
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) category.badgeColor else MutedTelemetry.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.testTag("spirit_filter_${category.name}")
                            ) {
                                Text(
                                    text = "${category.displayName.uppercase()} ${category.jpKanji} ($count)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) category.badgeColor else SteelText,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.heightIn(max = 340.dp)
                    ) {
                        item {
                            Text(
                                text = "PILOT SPIRIT COMMANDS (精神コマンド: BUFFS • DEBUFFS • HEALS)",
                                style = MaterialTheme.typography.labelSmall,
                                color = SpiritGold
                            )
                        }
                        items(filteredSpirits) { spirit ->
                            val alreadyActive = !spirit.isInstantAction && unit.activeSpirits.contains(spirit)
                            val effectiveCost = progressionSystem.getEffectiveSpCost(unit.pilot, spirit)
                            val spiritRank = unit.pilot.spiritRank(spirit)
                            val spiritSummary = progressionSystem.getEffectiveSpiritSummary(unit.pilot, spirit)
                            val canCast = unit.pilot.currentSp >= effectiveCost && !alreadyActive
                            val requiresTargetSelection = spirit.targetType == SpiritTargetType.SINGLE_ALLY ||
                                spirit.targetType == SpiritTargetType.SINGLE_ENEMY

                            Surface(
                                onClick = {
                                    if (canCast) {
                                        if (requiresTargetSelection) {
                                            pendingTargetedSpirit = spirit
                                        } else {
                                            onActivateSpirit(spirit, null)
                                        }
                                    }
                                },
                                enabled = canCast,
                                color = if (canCast) PanelNavy else VoidNavy.copy(alpha = 0.6f),
                                shape = CutCornerShape(8.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (alreadyActive) EnergyGreen
                                    else if (canCast) spirit.badgeColor
                                    else MutedTelemetry.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("spirit_card_${spirit.name}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${spirit.codeName} (${spirit.jpKanji})",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = spirit.badgeColor
                                            )
                                            if (spiritRank > 0) {
                                                Surface(
                                                    color = SpiritGold.copy(alpha = 0.2f),
                                                    shape = RoundedCornerShape(3.dp),
                                                    border = BorderStroke(0.8.dp, SpiritGold)
                                                ) {
                                                    Text(
                                                        text = progressionSystem.getRankBadgeLabel(spiritRank),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = SpiritGold,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                            Surface(
                                                color = spirit.category.badgeColor.copy(alpha = 0.18f),
                                                shape = RoundedCornerShape(3.dp),
                                                border = BorderStroke(0.8.dp, spirit.category.badgeColor)
                                            ) {
                                                Text(
                                                    text = "${spirit.category.jpKanji} • ${spirit.targetType.label.uppercase()}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = spirit.category.badgeColor,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = spiritSummary,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = SteelText
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = if (alreadyActive) "ACTIVE" else "$effectiveCost SP",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (alreadyActive) EnergyGreen else SpiritGold
                                        )
                                        if (requiresTargetSelection && canCast) {
                                            Text(
                                                text = "SELECT TARGET ▶",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = PanelBorderCyan
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (unit.unlockedSpells.isNotEmpty()) {
                            item {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "PRANA & ARCANE MAGIC SPELLS (魔導術式)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = unit.elementalAffinity.badgeColor
                                )
                            }
                            items(unit.unlockedSpells.toList()) { spell ->
                                val canCastSpell = unit.pilot.currentSp >= spell.manaSpCost
                                Surface(
                                    onClick = { if (canCastSpell) onCastMagicSpell(spell) },
                                    enabled = canCastSpell,
                                    color = if (canCastSpell) PanelNavy else VoidNavy.copy(alpha = 0.6f),
                                    shape = CutCornerShape(8.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (canCastSpell) spell.badgeColor else MutedTelemetry.copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("spell_card_${spell.name}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${spell.codeName} (${spell.jpName})",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = spell.badgeColor
                                            )
                                            Text(
                                                text = spell.summary,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = SteelText
                                            )
                                        }
                                        Text(
                                            text = "${spell.manaSpCost} SP",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (canCastSpell) spell.badgeColor else MutedTelemetry
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (pendingTargetedSpirit != null) {
                    TextButton(onClick = { pendingTargetedSpirit = null }) {
                        Text("BACK TO SPIRITS", color = SpiritGold)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("CLOSE", color = PanelBorderCyan)
                }
            }
        }
    )
}

@Composable
private fun CombatForecastDialog(
    forecast: CombatForecast,
    damageBreakdown: DamageCalculationBreakdown?,
    allUnits: List<MechaUnit> = emptyList(),
    onActivateSpirit: (SpiritCommand, String?) -> Unit = { _, _ -> },
    onCastMagicSpell: (MagicSpell) -> Unit = {},
    onStanceChange: (DefendStance) -> Unit,
    onWeaponChange: (Weapon) -> Unit,
    onConfirmBattle: () -> Unit,
    onCancel: () -> Unit
) {
    val progressionSystem = remember { com.example.srw.engine.TacticalCombatEngine.defaultCombatManager.pilotProgressionSystem }
    var showPreAttackSpiritOverlay by remember { mutableStateOf(false) }
    var simulatedAttackerWill by remember(
        forecast.attacker.id,
        forecast.attacker.pilot.will,
        forecast.attackerWeapon.id,
        forecast.defenderStance
    ) {
        mutableStateOf<Int?>(null)
    }
    var simulatedDefenderWill by remember(
        forecast.defender.id,
        forecast.defender.pilot.will,
        forecast.attackerWeapon.id,
        forecast.defenderStance
    ) {
        mutableStateOf<Int?>(null)
    }

    val isSimulatingMorale = simulatedAttackerWill != null || simulatedDefenderWill != null
    val (displayForecast, displayBreakdown) = remember(
        forecast,
        damageBreakdown,
        simulatedAttackerWill,
        simulatedDefenderWill
    ) {
        if (simulatedAttackerWill == null && simulatedDefenderWill == null) {
            forecast to damageBreakdown
        } else {
            TacticalCombatEngine.defaultCombatManager.simulateForecastMorale(
                baseForecast = forecast,
                simulatedAttackerWill = simulatedAttackerWill ?: forecast.attacker.pilot.will,
                simulatedDefenderWill = simulatedDefenderWill ?: forecast.defender.pilot.will
            )
        }
    }

    val usableWeaponsAtRange = remember(
        forecast.attacker.id,
        forecast.attacker.hasMoved,
        forecast.distance,
        forecast.attacker.pilot.will
    ) {
        forecast.attacker.availableWeapons(
            afterMove = forecast.attacker.hasMoved,
            distance = forecast.distance
        )
    }
    val atkMoraleTier = remember(displayForecast.attackerWill) {
        MoraleTier.fromWill(displayForecast.attackerWill)
    }
    val defMoraleTier = remember(displayForecast.defenderWill) {
        MoraleTier.fromWill(displayForecast.defenderWill)
    }
    val isLethalForecast = displayForecast.defenderProjectedHpAfter == 0
    val atkPrimaryStatLabel = if (displayForecast.attackerWeapon.isMelee) {
        "MEL ${displayForecast.attacker.pilot.meleeStat}"
    } else {
        "RNG ${displayForecast.attacker.pilot.rangedStat}"
    }

    val atkOutPctSign = if (displayForecast.attackerMoraleOutputPercent >= 0) {
        "+${displayForecast.attackerMoraleOutputPercent}%"
    } else {
        "${displayForecast.attackerMoraleOutputPercent}%"
    }
    val atkDmgDeltaSign = if (displayForecast.attackerMoraleDamageDelta >= 0) {
        "+${displayForecast.attackerMoraleDamageDelta}"
    } else {
        "${displayForecast.attackerMoraleDamageDelta}"
    }
    val atkMoraleColor = when {
        displayForecast.attackerMoraleDamageDelta > 0 -> EnergyGreen
        displayForecast.attackerMoraleDamageDelta < 0 -> EnemyCrimson
        else -> PanelBorderCyan
    }
    val weaponWillLockedInSim = displayForecast.attackerWill < displayForecast.attackerWeapon.willReq

    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = CockpitSlate,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "COMBAT PREVIEW",
                        style = MaterialTheme.typography.titleLarge,
                        color = SpiritGold
                    )
                    Surface(
                        color = when {
                            isLethalForecast -> EnemyCrimson.copy(alpha = 0.25f)
                            displayForecast.defenderCanCounter -> WarningAmber.copy(alpha = 0.22f)
                            else -> EnergyGreen.copy(alpha = 0.22f)
                        },
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(
                            1.dp,
                            when {
                                isLethalForecast -> EnemyCrimson
                                displayForecast.defenderCanCounter -> WarningAmber
                                else -> EnergyGreen
                            }
                        )
                    ) {
                        Text(
                            text = when {
                                isLethalForecast -> "PROJECTED KO"
                                displayForecast.defenderCanCounter -> "COUNTER RISK"
                                else -> "SAFE STRIKE"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                isLethalForecast -> EnemyCrimson
                                displayForecast.defenderCanCounter -> WarningAmber
                                else -> EnergyGreen
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = "Range ${displayForecast.distance} Tile(s) • Unit Stats, Morale & Terrain Applied",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedTelemetry
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState())
                    .testTag("combat_preview_modal"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isSimulatingMorale) {
                    Surface(
                        color = SpiritGold.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, SpiritGold)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "WHAT-IF MORALE PREVIEW (Actual Will: ${forecast.attacker.pilot.will})",
                                style = MaterialTheme.typography.labelSmall,
                                color = SpiritGold,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    simulatedAttackerWill = null
                                    simulatedDefenderWill = null
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.testTag("preview_morale_reset")
                            ) {
                                Text("RESET", style = MaterialTheme.typography.labelSmall, color = PanelBorderCyan)
                            }
                        }
                    }
                }

                // 1. ATTACKER PREVIEW CARD
                Surface(
                    color = PanelNavy,
                    shape = CutCornerShape(8.dp),
                    border = BorderStroke(1.2.dp, AllyBeamBlue)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "ATK // ${displayForecast.attacker.name}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = AllyBeamBlue,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${displayForecast.attacker.pilot.name} • SP ${displayForecast.attacker.pilot.currentSp}/${displayForecast.attacker.effectiveMaxSp} • $atkPrimaryStatLabel • HIT ${displayForecast.attacker.pilot.hitStat} • EVA ${displayForecast.attacker.pilot.evadeStat}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MutedTelemetry
                                )
                            }
                            Surface(
                                color = displayForecast.attackerTerrain.tileAccentColor.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = displayForecast.attackerTerrain.shortCode,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PanelBorderCyan,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Active Spirit Buffs & Unlocked Pilot Passives on Attacker
                        if (displayForecast.attacker.activeSpirits.isNotEmpty() || displayForecast.attacker.pilot.passiveAbilities.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                items(displayForecast.attacker.activeSpirits.toList(), key = { "sp_${it.name}" }) { activeSpirit ->
                                    val sRank = displayForecast.attacker.pilot.spiritRank(activeSpirit)
                                    Surface(
                                        color = activeSpirit.badgeColor.copy(alpha = 0.20f),
                                        shape = RoundedCornerShape(4.dp),
                                        border = BorderStroke(1.dp, activeSpirit.badgeColor),
                                        modifier = Modifier.testTag("forecast_active_spirit_${activeSpirit.name}")
                                    ) {
                                        Text(
                                            text = if (sRank > 0) "${activeSpirit.codeName}+${sRank}" else "${activeSpirit.codeName} ${activeSpirit.jpKanji}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = activeSpirit.badgeColor,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                items(displayForecast.attacker.pilot.passiveAbilities.toList(), key = { "pas_${it.name}" }) { passive ->
                                    val highlighted = passive != PilotPassiveAbility.POTENTIAL || displayForecast.attacker.isPotentialActive
                                    Surface(
                                        color = if (highlighted) passive.badgeColor.copy(alpha = 0.16f) else VoidNavy,
                                        shape = RoundedCornerShape(4.dp),
                                        border = BorderStroke(1.dp, passive.badgeColor.copy(alpha = if (highlighted) 0.9f else 0.4f)),
                                        modifier = Modifier.testTag("forecast_passive_${passive.name}")
                                    ) {
                                        Text(
                                            text = if (passive == PilotPassiveAbility.POTENTIAL && displayForecast.attacker.isPotentialActive) {
                                                "POTENTIAL ★"
                                            } else {
                                                passive.shortBadge
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (highlighted) passive.badgeColor else MutedTelemetry,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Attacker Will / Morale Tier Banner
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = atkMoraleTier.badgeColor.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, atkMoraleTier.badgeColor.copy(alpha = 0.7f)),
                                modifier = Modifier.testTag("preview_attacker_morale_badge")
                            ) {
                                Text(
                                    text = "WILL ${displayForecast.attackerWill} • ${atkMoraleTier.displayName} ${atkMoraleTier.jpKanji}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = atkMoraleTier.badgeColor,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Surface(
                                color = atkMoraleColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.testTag("preview_attacker_morale_delta")
                            ) {
                                Text(
                                    text = "$atkOutPctSign OUT ($atkDmgDeltaSign DMG)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = atkMoraleColor,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (weaponWillLockedInSim) {
                            Surface(
                                color = EnemyCrimson.copy(alpha = 0.20f),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, EnemyCrimson)
                            ) {
                                Text(
                                    text = "WILL LOCKED: ${displayForecast.attackerWeapon.name} requires ${displayForecast.attackerWeapon.willReq} Will (currently ${displayForecast.attackerWill})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EnemyCrimson,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }

                        // Attacker HP Bar & Counter Risk
                        val atkHpRatio = (displayForecast.attackerProjectedHpAfter.toFloat() / displayForecast.attacker.maxHp.toFloat()).coerceIn(0f, 1f)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Weapon: ${displayForecast.attackerWeapon.name}",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "HP ${displayForecast.attacker.currentHp} → ${displayForecast.attackerProjectedHpAfter}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (displayForecast.defenderCanCounter && displayForecast.defenderPredictedDamage > 0) WarningAmber else EnergyGreen
                            )
                        }
                        LinearProgressIndicator(
                            progress = { atkHpRatio },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = EnergyGreen,
                            trackColor = VoidNavy
                        )

                        // Key Preview Metrics: Projected Damage, Hit Rate, Evasion, Crit
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            PreviewMetricPill(
                                label = "PROJ DMG",
                                value = "${displayForecast.attackerPredictedDamage}",
                                valueColor = if (displayForecast.attackerMoraleDamageDelta < 0) WarningAmber else EnergyGreen,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("preview_attacker_damage")
                            )
                            PreviewMetricPill(
                                label = "HIT RATE",
                                value = "${displayForecast.attackerHitPercent}%",
                                valueColor = SpiritGold,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("preview_attacker_hit")
                            )
                            PreviewMetricPill(
                                label = "EVASION",
                                value = "${displayForecast.attackerEvasionPercent}%",
                                valueColor = PanelBorderCyan,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("preview_attacker_evasion")
                            )
                            PreviewMetricPill(
                                label = "CRIT",
                                value = "${displayForecast.attackerCritPercent}%",
                                valueColor = Color(0xFFFFAB40),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Quick-Switch Weapons at Current Range (if more than 1 weapon available)
                if (usableWeaponsAtRange.size > 1) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "SWAP IN-RANGE WEAPON:",
                            style = MaterialTheme.typography.labelSmall,
                            color = PanelBorderCyan
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(usableWeaponsAtRange, key = { it.id }) { weapon ->
                                val isSelected = weapon.id == forecast.attackerWeapon.id
                                Surface(
                                    onClick = { onWeaponChange(weapon) },
                                    color = if (isSelected) AllyBeamBlue.copy(alpha = 0.25f) else VoidNavy,
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) AllyBeamBlue else MutedTelemetry.copy(alpha = 0.45f)
                                    ),
                                    modifier = Modifier.testTag("preview_weapon_chip_${weapon.id}")
                                ) {
                                    Text(
                                        text = if (weapon.willReq > 100) "${weapon.name} (W${weapon.willReq})" else weapon.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) AllyBeamBlue else SteelText,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. DEFENDER PREVIEW CARD
                Surface(
                    color = PanelNavy,
                    shape = CutCornerShape(8.dp),
                    border = BorderStroke(1.2.dp, EnemyCrimson)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "DEF // ${displayForecast.defender.name}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = EnemyCrimson,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${displayForecast.defender.pilot.name} • ARM ${displayForecast.defender.armor} • MOB ${displayForecast.defender.mobility} • EVA ${displayForecast.defender.pilot.evadeStat}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MutedTelemetry
                                )
                            }
                            Surface(
                                color = displayForecast.defenderTerrain.tileAccentColor.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = displayForecast.defenderTerrain.shortCode,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EnergyGreen,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Defender Will / Morale Badge
                        val defArmSign = if (displayForecast.defenderMoraleArmorPercent >= 0) {
                            "+${displayForecast.defenderMoraleArmorPercent}%"
                        } else {
                            "${displayForecast.defenderMoraleArmorPercent}%"
                        }
                        val defOutSign = if (displayForecast.defenderMoraleOutputPercent >= 0) {
                            "+${displayForecast.defenderMoraleOutputPercent}%"
                        } else {
                            "${displayForecast.defenderMoraleOutputPercent}%"
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = defMoraleTier.badgeColor.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, defMoraleTier.badgeColor.copy(alpha = 0.6f)),
                                modifier = Modifier.testTag("preview_defender_morale_badge")
                            ) {
                                Text(
                                    text = "WILL ${displayForecast.defenderWill} • ${defMoraleTier.displayName} ${defMoraleTier.jpKanji}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = defMoraleTier.badgeColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = if (displayForecast.defenderCanCounter) {
                                    "$defArmSign ARM • $defOutSign CTR"
                                } else {
                                    "$defArmSign ARM SCALE"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = SteelText
                            )
                        }

                        if (displayForecast.defender.activeDebuffs.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                items(displayForecast.defender.activeDebuffs.toList(), key = { it.name }) { debuff ->
                                    Surface(
                                        color = debuff.badgeColor.copy(alpha = 0.18f),
                                        shape = RoundedCornerShape(4.dp),
                                        border = BorderStroke(1.dp, debuff.badgeColor),
                                        modifier = Modifier.testTag("forecast_defender_debuff_${debuff.name}")
                                    ) {
                                        Text(
                                            text = "${debuff.codeName} ${debuff.jpKanji}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = debuff.badgeColor,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Defender Projected HP Bar
                        val defHpRatio = (displayForecast.defenderProjectedHpAfter.toFloat() / displayForecast.defender.maxHp.toFloat()).coerceIn(0f, 1f)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (displayForecast.defenderCanCounter) {
                                    "Counter: ${displayForecast.defenderWeapon?.name ?: "None"}"
                                } else {
                                    "Stance: ${displayForecast.defenderStance.label}"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = SteelText,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "HP ${displayForecast.defender.currentHp} → ${displayForecast.defenderProjectedHpAfter}" +
                                    if (isLethalForecast) " (KO!)" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isLethalForecast) EnemyCrimson else WarningAmber
                            )
                        }
                        LinearProgressIndicator(
                            progress = { defHpRatio },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = if (isLethalForecast) EnemyCrimson else WarningAmber,
                            trackColor = VoidNavy
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            PreviewMetricPill(
                                label = "CTR DMG",
                                value = if (displayForecast.defenderCanCounter) "${displayForecast.defenderPredictedDamage}" else "—",
                                valueColor = if (displayForecast.defenderCanCounter) EnemyCrimson else MutedTelemetry,
                                modifier = Modifier.weight(1f)
                            )
                            PreviewMetricPill(
                                label = "CTR HIT",
                                value = if (displayForecast.defenderCanCounter) "${displayForecast.defenderHitPercent}%" else "0%",
                                valueColor = if (displayForecast.defenderCanCounter) SpiritGold else MutedTelemetry,
                                modifier = Modifier.weight(1f)
                            )
                            PreviewMetricPill(
                                label = "EVASION",
                                value = "${displayForecast.defenderEvasionPercent}%",
                                valueColor = EnergyGreen,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("preview_defender_evasion")
                            )
                            PreviewMetricPill(
                                label = "CTR CRIT",
                                value = if (displayForecast.defenderCanCounter) "${displayForecast.defenderCritPercent}%" else "0%",
                                valueColor = if (displayForecast.defenderCanCounter) Color(0xFFFFAB40) else MutedTelemetry,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // 3. UNIT STATS, MORALE & TERRAIN MODIFIER BREAKDOWN
                Surface(
                    color = VoidNavy,
                    shape = CutCornerShape(8.dp),
                    border = BorderStroke(1.dp, displayForecast.defenderTerrain.tileAccentColor)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(9.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "STATS, MORALE & TERRAIN BREAKDOWN",
                            style = MaterialTheme.typography.labelSmall,
                            color = PanelBorderCyan
                        )
                        val atkHitSign = if (displayForecast.attackerTerrainHitBonus >= 0) {
                            "+${displayForecast.attackerTerrainHitBonus}%"
                        } else {
                            "${displayForecast.attackerTerrainHitBonus}%"
                        }
                        val moraleHitSign = if (atkMoraleTier.hitEvadeBonusPercent >= 0) {
                            "+${atkMoraleTier.hitEvadeBonusPercent}%"
                        } else {
                            "${atkMoraleTier.hitEvadeBonusPercent}%"
                        }
                        val moraleCritSign = if (atkMoraleTier.critBonusPercent >= 0) {
                            "+${atkMoraleTier.critBonusPercent}%"
                        } else {
                            "${atkMoraleTier.critBonusPercent}%"
                        }
                        Text(
                            text = "• Morale Output (${atkMoraleTier.displayName}, Will ${displayForecast.attackerWill}): $atkOutPctSign damage ($atkDmgDeltaSign DMG vs 100 Will ${displayForecast.attackerBaselineDamageAt100Will}) • $moraleHitSign Hit/Eva • $moraleCritSign Crit",
                            style = MaterialTheme.typography.labelSmall,
                            color = atkMoraleTier.badgeColor
                        )
                        Text(
                            text = "• Accuracy vs Evasion: ATK Rating ${displayForecast.attackerAccuracyRating} vs DEF Evasion ${displayForecast.defenderEvasionRating} → ${displayForecast.attackerHitPercent}% Hit / ${displayForecast.defenderEvasionPercent}% Evade",
                            style = MaterialTheme.typography.labelSmall,
                            color = SteelText
                        )
                        Text(
                            text = "• Attacker Tile [${displayForecast.attackerTerrain.displayName}]: +${displayForecast.attackerTerrainAttackBonus}% ATK • $atkHitSign HIT • +${displayForecast.attackerTerrainCritBonus}% CRT • +${displayForecast.attackerTerrainEvadeBonus}% EVA",
                            style = MaterialTheme.typography.labelSmall,
                            color = SpiritGold
                        )
                        Text(
                            text = "• Defender Tile [${displayForecast.defenderTerrain.displayName}]: +${displayForecast.defenderTerrainDefBonus}% DEF • +${displayForecast.defenderTerrainEvadeBonus}% EVADE",
                            style = MaterialTheme.typography.labelSmall,
                            color = EnergyGreen
                        )
                        if (displayForecast.attackerAuraBonus > 0 || displayForecast.defenderAuraBonus > 0) {
                            Text(
                                text = "• Command Aura: ATK +${displayForecast.attackerAuraBonus}% • DEF +${displayForecast.defenderAuraBonus}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFB388FF)
                            )
                        }
                        if (displayForecast.attacker.activeSpirits.isNotEmpty() || displayForecast.defender.activeDebuffs.isNotEmpty()) {
                            val spiritLabels = displayForecast.attacker.activeSpirits.map { s ->
                                val rank = displayForecast.attacker.pilot.spiritRank(s)
                                val rankTag = if (rank > 0) " ${progressionSystem.getRankBadgeLabel(rank)}" else ""
                                "${s.codeName}$rankTag (${progressionSystem.getEffectiveSpiritSummary(displayForecast.attacker.pilot, s)})"
                            } + displayForecast.defender.activeDebuffs.map { d ->
                                "Enemy ${d.codeName} (${d.summary})"
                            }
                            Text(
                                text = "• Active Spirits: ${spiritLabels.joinToString(" • ")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SpiritGold
                            )
                        }
                        if (displayBreakdown != null) {
                            Text(
                                text = "• Formula: ${displayBreakdown.formulaSummary}",
                                style = MaterialTheme.typography.labelSmall,
                                color = PanelBorderCyan
                            )
                        }
                    }
                }

                // 3.5 PRE-ATTACK SPIRIT COMMAND QUICK-CAST BAR + OVERLAY TRIGGER
                Surface(
                    color = PanelNavy,
                    shape = CutCornerShape(8.dp),
                    border = BorderStroke(1.dp, SpiritGold.copy(alpha = 0.75f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "PRE-ATTACK SPIRIT COMMANDS (精神)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SpiritGold,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${forecast.attacker.pilot.name} SP: ${forecast.attacker.pilot.currentSp}/${forecast.attacker.effectiveMaxSp} • Tap to buff before strike",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MutedTelemetry
                                )
                            }
                            OutlinedButton(
                                onClick = { showPreAttackSpiritOverlay = true },
                                border = BorderStroke(1.dp, SpiritGold),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("open_pre_attack_spirit_overlay")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Open Spirit Command Overlay",
                                    tint = SpiritGold,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "OVERLAY",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SpiritGold
                                )
                            }
                        }

                        val orderedSpirits = remember(forecast.attacker.pilot.spirits) {
                            forecast.attacker.pilot.spirits.sortedBy { spirit ->
                                when (spirit) {
                                    SpiritCommand.FOCUS -> 0
                                    SpiritCommand.STRIKE -> 1
                                    SpiritCommand.ALERT -> 2
                                    SpiritCommand.VALOR -> 3
                                    SpiritCommand.SOUL -> 4
                                    SpiritCommand.IRON_WALL -> 5
                                    SpiritCommand.ANALYZE -> 6
                                    SpiritCommand.DRIVE -> 7
                                    SpiritCommand.EXHAUST -> 8
                                    else -> 10 + spirit.ordinal
                                }
                            }
                        }

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(orderedSpirits, key = { it.name }) { spirit ->
                                val isAlreadyActive = !spirit.isInstantAction && forecast.attacker.activeSpirits.contains(spirit)
                                val isAlreadyAnalyzed = spirit == SpiritCommand.ANALYZE &&
                                    forecast.defender.activeDebuffs.contains(SpiritDebuff.ANALYZED)
                                val effectiveCost = progressionSystem.getEffectiveSpCost(forecast.attacker.pilot, spirit)
                                val spiritRank = forecast.attacker.pilot.spiritRank(spirit)
                                val hasEnoughSp = forecast.attacker.pilot.currentSp >= effectiveCost
                                val canCastNow = hasEnoughSp && !isAlreadyActive && !isAlreadyAnalyzed
                                val targetId = when (spirit.targetType) {
                                    SpiritTargetType.SINGLE_ENEMY -> forecast.defender.id
                                    SpiritTargetType.SINGLE_ALLY -> forecast.attacker.id
                                    else -> null
                                }
                                Surface(
                                    onClick = {
                                        if (canCastNow) {
                                            onActivateSpirit(spirit, targetId)
                                        }
                                    },
                                    enabled = canCastNow,
                                    color = when {
                                        isAlreadyActive || isAlreadyAnalyzed -> EnergyGreen.copy(alpha = 0.22f)
                                        canCastNow -> spirit.badgeColor.copy(alpha = 0.16f)
                                        else -> VoidNavy
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        when {
                                            isAlreadyActive || isAlreadyAnalyzed -> EnergyGreen
                                            canCastNow -> spirit.badgeColor
                                            else -> MutedTelemetry.copy(alpha = 0.35f)
                                        }
                                    ),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("forecast_spirit_chip_${spirit.name}")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                        verticalArrangement = Arrangement.spacedBy(1.dp)
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (spiritRank > 0) "${spirit.codeName}+${spiritRank}" else spirit.codeName,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isAlreadyActive || isAlreadyAnalyzed) EnergyGreen else spirit.badgeColor,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = if (isAlreadyActive || isAlreadyAnalyzed) "ON" else "${effectiveCost}SP",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isAlreadyActive || isAlreadyAnalyzed) EnergyGreen else SpiritGold
                                            )
                                        }
                                        Text(
                                            text = spiritQuickEffectTag(spirit, spiritRank),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (canCastNow || isAlreadyActive || isAlreadyAnalyzed) SteelText else MutedTelemetry
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. INTERACTIVE MORALE / WILL FLUCTUATION INSPECTOR
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PREVIEW WILL / MORALE FLUCTUATION:",
                            style = MaterialTheme.typography.labelSmall,
                            color = SpiritGold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Surface(
                                onClick = {
                                    val next = (displayForecast.attackerWill - 10).coerceIn(
                                        PilotMoraleSystem.MIN_WILL,
                                        PilotMoraleSystem.MAX_WILL
                                    )
                                    simulatedAttackerWill = if (next == forecast.attacker.pilot.will) null else next
                                },
                                color = VoidNavy,
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, EnemyCrimson.copy(alpha = 0.7f)),
                                modifier = Modifier.testTag("preview_morale_minus")
                            ) {
                                Text(
                                    text = "-10",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EnemyCrimson,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Surface(
                                onClick = {
                                    val next = (displayForecast.attackerWill + 10).coerceIn(
                                        PilotMoraleSystem.MIN_WILL,
                                        PilotMoraleSystem.MAX_WILL
                                    )
                                    simulatedAttackerWill = if (next == forecast.attacker.pilot.will) null else next
                                },
                                color = VoidNavy,
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, EnergyGreen.copy(alpha = 0.7f)),
                                modifier = Modifier.testTag("preview_morale_plus")
                            ) {
                                Text(
                                    text = "+10",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EnergyGreen,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val presets = listOf(
                            Triple(75, "75 WAVER", "preview_morale_preset_75"),
                            Triple(100, "100 BASE", "preview_morale_preset_100"),
                            Triple(forecast.attacker.pilot.will, "ACTUAL (${forecast.attacker.pilot.will})", "preview_morale_preset_actual"),
                            Triple(135, "135 OVER", "preview_morale_preset_135"),
                            Triple(155, "155 MAX", "preview_morale_preset_155")
                        )
                        items(presets, key = { it.third }) { (targetWill, label, tag) ->
                            val presetTier = MoraleTier.fromWill(targetWill)
                            val selected = if (tag == "preview_morale_preset_actual") {
                                simulatedAttackerWill == null
                            } else {
                                simulatedAttackerWill == targetWill
                            }
                            Surface(
                                onClick = {
                                    simulatedAttackerWill = if (tag == "preview_morale_preset_actual" || targetWill == forecast.attacker.pilot.will) {
                                        null
                                    } else {
                                        targetWill
                                    }
                                },
                                color = if (selected) presetTier.badgeColor.copy(alpha = 0.24f) else VoidNavy,
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) presetTier.badgeColor else MutedTelemetry.copy(alpha = 0.45f)
                                ),
                                modifier = Modifier.testTag(tag)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) presetTier.badgeColor else SteelText,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }

                // 5. DEFENDER REACTION STANCE SIMULATION SELECTOR
                Text(
                    text = "SIMULATE DEFENDER STANCE:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedTelemetry
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DefendStance.entries.forEach { stance ->
                        val selected = forecast.defenderStance == stance
                        OutlinedButton(
                            onClick = { onStanceChange(stance) },
                            border = BorderStroke(1.dp, if (selected) SpiritGold else MutedTelemetry),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("preview_stance_${stance.name}")
                        ) {
                            Text(
                                text = stance.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) SpiritGold else SteelText
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmBattle,
                colors = ButtonDefaults.buttonColors(containerColor = EnemyCrimson),
                modifier = Modifier.testTag("launch_battle_button")
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Initiate attack")
                Spacer(Modifier.width(4.dp))
                Text("INITIATE ATTACK", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.testTag("abort_battle_button")
            ) {
                Text("CANCEL", color = MutedTelemetry)
            }
        }
    )

    if (showPreAttackSpiritOverlay) {
        PreAttackSpiritOverlayDialog(
            attacker = forecast.attacker,
            defender = forecast.defender,
            allUnits = allUnits,
            onActivateSpirit = { spirit, targetId ->
                onActivateSpirit(spirit, targetId)
            },
            onCastMagicSpell = onCastMagicSpell,
            onDismiss = { showPreAttackSpiritOverlay = false }
        )
    }
}

private fun spiritQuickEffectTag(spirit: SpiritCommand, rank: Int = 0): String = when (spirit) {
    SpiritCommand.FOCUS -> when (rank) {
        1 -> "+40% Hit/Eva"
        2 -> "+50% Hit/Eva"
        else -> "+30% Hit/Eva"
    }
    SpiritCommand.VALOR -> when (rank) {
        1 -> "2.25x Damage"
        2 -> "2.50x Damage"
        else -> "2.0x Damage"
    }
    SpiritCommand.SOUL -> when (rank) {
        1 -> "2.75x Damage"
        2 -> "3.00x Damage"
        else -> "2.5x Damage"
    }
    SpiritCommand.STRIKE -> if (rank > 0) "100% Hit/+Crit" else "100% Hit"
    SpiritCommand.ALERT -> if (rank > 0) "Dodge/+Will" else "100% Dodge"
    SpiritCommand.IRON_WALL -> when (rank) {
        1 -> "-70% Counter"
        2 -> "-80% Counter"
        else -> "-60% Counter"
    }
    SpiritCommand.ACCEL -> "+${3 + rank} Move"
    SpiritCommand.DRIVE -> "+${if (rank == 1) 15 else if (rank >= 2) 22 else 10} Will"
    SpiritCommand.ROUSE -> "+${if (rank == 1) 18 else if (rank >= 2) 25 else 12} Will"
    SpiritCommand.ANALYZE -> "+${if (rank == 1) 35 else if (rank >= 2) 45 else 25}% Enemy Dmg"
    SpiritCommand.EXHAUST -> "-${if (rank == 1) 22 else if (rank >= 2) 30 else 15} Enemy Will"
    SpiritCommand.CONFUSE -> "-${if (rank == 1) 45 else if (rank >= 2) 55 else 35}% All Hit"
    SpiritCommand.TRUST -> "+${if (rank == 1) 4500 else if (rank >= 2) 6000 else 3000} HP"
    SpiritCommand.FRIENDSHIP -> "Squad Heal"
    SpiritCommand.RESUPPLY -> "Full EN/Ammo"
}

@Composable
private fun PreAttackSpiritOverlayDialog(
    attacker: MechaUnit,
    defender: MechaUnit,
    allUnits: List<MechaUnit>,
    onActivateSpirit: (SpiritCommand, String?) -> Unit,
    onCastMagicSpell: (MagicSpell) -> Unit,
    onDismiss: () -> Unit
) {
    val progressionSystem = remember { com.example.srw.engine.TacticalCombatEngine.defaultCombatManager.pilotProgressionSystem }
    var selectedCategory by remember { mutableStateOf<SpiritCategory?>(null) }
    val filteredSpirits = remember(attacker.pilot.spirits, selectedCategory) {
        if (selectedCategory == null) attacker.pilot.spirits
        else attacker.pilot.spirits.filter { it.category == selectedCategory }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VoidNavy,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SPIRIT COMMAND OVERLAY",
                        style = MaterialTheme.typography.titleLarge,
                        color = SpiritGold
                    )
                    Surface(
                        color = SpiritGold.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, SpiritGold)
                    ) {
                        Text(
                            text = "SP ${attacker.pilot.currentSp}/${attacker.effectiveMaxSp}",
                            style = MaterialTheme.typography.labelLarge,
                            color = SpiritGold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Text(
                    text = "${attacker.pilot.name} (${attacker.name}) → Target: ${defender.name}",
                    style = MaterialTheme.typography.labelSmall,
                    color = PanelBorderCyan
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .testTag("pre_attack_spirit_overlay"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Active Buffs Summary Banner
                if (attacker.activeSpirits.isNotEmpty() || defender.activeDebuffs.isNotEmpty()) {
                    Surface(
                        color = PanelNavy,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, EnergyGreen.copy(alpha = 0.65f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = "ACTIVE COMBAT EFFECTS:",
                                style = MaterialTheme.typography.labelSmall,
                                color = EnergyGreen
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                items(attacker.activeSpirits.toList(), key = { "atk_${it.name}" }) { s ->
                                    Surface(
                                        color = s.badgeColor.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp),
                                        border = BorderStroke(0.8.dp, s.badgeColor)
                                    ) {
                                        Text(
                                            text = "${s.codeName} (${s.jpKanji})",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = s.badgeColor,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                items(defender.activeDebuffs.toList(), key = { "def_${it.name}" }) { d ->
                                    Surface(
                                        color = d.badgeColor.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp),
                                        border = BorderStroke(0.8.dp, d.badgeColor)
                                    ) {
                                        Text(
                                            text = "FOE ${d.codeName}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = d.badgeColor,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Category Filter Chips
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        val isAll = selectedCategory == null
                        Surface(
                            onClick = { selectedCategory = null },
                            color = if (isAll) SpiritGold.copy(alpha = 0.24f) else PanelNavy,
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, if (isAll) SpiritGold else MutedTelemetry.copy(alpha = 0.4f)),
                            modifier = Modifier.testTag("pre_attack_spirit_filter_all")
                        ) {
                            Text(
                                text = "ALL (${attacker.pilot.spirits.size})",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isAll) SpiritGold else SteelText,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    items(SpiritCategory.entries) { cat ->
                        val count = attacker.pilot.spirits.count { it.category == cat }
                        val selected = selectedCategory == cat
                        Surface(
                            onClick = { selectedCategory = cat },
                            color = if (selected) cat.badgeColor.copy(alpha = 0.24f) else PanelNavy,
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, if (selected) cat.badgeColor else MutedTelemetry.copy(alpha = 0.4f)),
                            modifier = Modifier.testTag("pre_attack_spirit_filter_${cat.name}")
                        ) {
                            Text(
                                text = "${cat.displayName.uppercase()} ($count)",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) cat.badgeColor else SteelText,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Scrollable Spirit & Spell List
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier.heightIn(max = 290.dp)
                ) {
                    items(filteredSpirits, key = { it.name }) { spirit ->
                        val alreadyActive = !spirit.isInstantAction && attacker.activeSpirits.contains(spirit)
                        val alreadyAnalyzed = spirit == SpiritCommand.ANALYZE &&
                            defender.activeDebuffs.contains(SpiritDebuff.ANALYZED)
                        val effectiveCost = progressionSystem.getEffectiveSpCost(attacker.pilot, spirit)
                        val spiritRank = attacker.pilot.spiritRank(spirit)
                        val effectiveSummary = progressionSystem.getEffectiveSpiritSummary(attacker.pilot, spirit)
                        val canCast = attacker.pilot.currentSp >= effectiveCost && !alreadyActive && !alreadyAnalyzed
                        val autoTargetId = when (spirit.targetType) {
                            SpiritTargetType.SINGLE_ENEMY -> defender.id
                            SpiritTargetType.SINGLE_ALLY -> attacker.id
                            else -> null
                        }
                        val targetLabel = when (spirit.targetType) {
                            SpiritTargetType.SELF -> "SELF"
                            SpiritTargetType.SINGLE_ENEMY -> "TARGET: ${defender.name}"
                            SpiritTargetType.SINGLE_ALLY -> "ALLY: ${attacker.name}"
                            SpiritTargetType.ALL_ALLIES -> "ALL ALLIES (${allUnits.count { it.isAlive() && it.faction == attacker.faction }})"
                            SpiritTargetType.ALL_ENEMIES -> "ALL ENEMIES (${allUnits.count { it.isAlive() && it.faction != attacker.faction }})"
                        }

                        Surface(
                            onClick = {
                                if (canCast) {
                                    onActivateSpirit(spirit, autoTargetId)
                                }
                            },
                            enabled = canCast,
                            color = if (canCast) PanelNavy else CockpitSlate.copy(alpha = 0.55f),
                            shape = CutCornerShape(8.dp),
                            border = BorderStroke(
                                1.dp,
                                when {
                                    alreadyActive || alreadyAnalyzed -> EnergyGreen
                                    canCast -> spirit.badgeColor
                                    else -> MutedTelemetry.copy(alpha = 0.3f)
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("pre_attack_spirit_card_${spirit.name}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${spirit.codeName} (${spirit.jpKanji})",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (alreadyActive || alreadyAnalyzed) EnergyGreen else spirit.badgeColor
                                        )
                                        if (spiritRank > 0) {
                                            Surface(
                                                color = SpiritGold.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(3.dp),
                                                border = BorderStroke(0.8.dp, SpiritGold)
                                            ) {
                                                Text(
                                                    text = progressionSystem.getRankBadgeLabel(spiritRank),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = SpiritGold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = targetLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = PanelBorderCyan
                                        )
                                    }
                                    Text(
                                        text = effectiveSummary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SteelText
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    color = when {
                                        alreadyActive || alreadyAnalyzed -> EnergyGreen.copy(alpha = 0.22f)
                                        canCast -> spirit.badgeColor.copy(alpha = 0.20f)
                                        else -> VoidNavy
                                    },
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        when {
                                            alreadyActive || alreadyAnalyzed -> EnergyGreen
                                            canCast -> spirit.badgeColor
                                            else -> MutedTelemetry.copy(alpha = 0.4f)
                                        }
                                    )
                                ) {
                                    Text(
                                        text = when {
                                            alreadyActive || alreadyAnalyzed -> "ACTIVE"
                                            canCast -> "$effectiveCost SP"
                                            else -> "LOW SP"
                                        },
                                        style = MaterialTheme.typography.labelLarge,
                                        color = when {
                                            alreadyActive || alreadyAnalyzed -> EnergyGreen
                                            canCast -> SpiritGold
                                            else -> MutedTelemetry
                                        },
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (attacker.unlockedSpells.isNotEmpty()) {
                        item {
                            Text(
                                text = "ARCANE MAGIC SPELLS (魔導術式)",
                                style = MaterialTheme.typography.labelSmall,
                                color = attacker.elementalAffinity.badgeColor
                            )
                        }
                        items(attacker.unlockedSpells.toList(), key = { it.name }) { spell ->
                            val canCastSpell = attacker.pilot.currentSp >= spell.manaSpCost
                            Surface(
                                onClick = { if (canCastSpell) onCastMagicSpell(spell) },
                                enabled = canCastSpell,
                                color = if (canCastSpell) PanelNavy else CockpitSlate.copy(alpha = 0.55f),
                                shape = CutCornerShape(8.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (canCastSpell) spell.badgeColor else MutedTelemetry.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("pre_attack_spell_card_${spell.name}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${spell.codeName} (${spell.jpName})",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = spell.badgeColor
                                        )
                                        Text(
                                            text = spell.summary,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SteelText
                                        )
                                    }
                                    Text(
                                        text = "${spell.manaSpCost} SP",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (canCastSpell) spell.badgeColor else MutedTelemetry
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = AllyBeamBlue),
                modifier = Modifier.testTag("back_to_combat_preview_button")
            ) {
                Text("APPLY & RETURN TO PREVIEW", style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}

@Composable
private fun PreviewMetricPill(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = VoidNavy,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(0.8.dp, valueColor.copy(alpha = 0.45f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MutedTelemetry,
                maxLines = 1
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = valueColor,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TerrainMatrixDialog(
    activeTerrain: TerrainType,
    inspectedUnit: MechaUnit?,
    onDismiss: () -> Unit
) {
    val profiles = remember {
        com.example.srw.engine.TacticalCombatEngine.defaultCombatManager
            .gridTerrainSystem
            .getAllTerrainProfiles()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CockpitSlate,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "GRID TERRAIN TACTICAL MATRIX",
                    style = MaterialTheme.typography.titleLarge,
                    color = PanelBorderCyan
                )
                Text(
                    text = if (inspectedUnit != null) {
                        "Active Unit: ${inspectedUnit.name} (${inspectedUnit.archetype.label})"
                    } else {
                        "Offensive & Defensive Grid Tile Modifiers"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = SpiritGold
                )
            }
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 380.dp)
            ) {
                items(profiles, key = { it.terrainType.name }) { profile ->
                    val isCurrent = profile.terrainType == activeTerrain
                    val tileDef = remember(profile.terrainType) {
                        TerrainMapUtility.getTileDefinition(profile.terrainType)
                    }
                    val unitBonus = inspectedUnit?.let {
                        com.example.srw.engine.TacticalCombatEngine.defaultCombatManager
                            .gridTerrainSystem
                            .resolveUnitTerrainBonus(it, profile.terrainType)
                    }
                    val pilotTileMod = inspectedUnit?.let {
                        TerrainMapUtility.applyModifiersToPilot(
                            pilot = it.pilot,
                            terrain = profile.terrainType,
                            pos = it.pos,
                            archetype = it.archetype,
                            baseArmor = it.armor,
                            unitId = it.id,
                            unitName = it.name
                        )
                    }

                    Surface(
                        color = if (isCurrent) PanelNavy else VoidNavy.copy(alpha = 0.78f),
                        shape = CutCornerShape(8.dp),
                        border = BorderStroke(
                            width = if (isCurrent) 1.5.dp else 1.dp,
                            color = if (isCurrent) SpiritGold else profile.terrainType.tileAccentColor
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("terrain_matrix_${profile.terrainType.name}")
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${tileDef.name} (${profile.terrainType.displayName}) [${profile.terrainType.shortCode}]",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isCurrent) SpiritGold else Color.White,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Surface(
                                    color = profile.terrainType.tileAccentColor.copy(alpha = 0.28f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = if (unitBonus != null) {
                                            "RANK ${unitBonus.adaptationGrade}"
                                        } else {
                                            profile.tacticalRoleLabel
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = PanelBorderCyan,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            val defVal = unitBonus?.effectiveDefenseBonusPercent ?: profile.defenseBonusPercent
                            val evaVal = unitBonus?.effectiveEvasionBonusPercent ?: profile.evasionBonusPercent
                            val atkVal = unitBonus?.effectiveAttackBonusPercent ?: profile.attackBonusPercent
                            val hitVal = unitBonus?.effectiveHitBonusPercent ?: profile.hitBonusPercent
                            val crtVal = unitBonus?.effectiveCritBonusPercent ?: profile.critBonusPercent
                            val hitTxt = if (hitVal >= 0) "+$hitVal%" else "$hitVal%"

                            Text(
                                text = "DEFENSE: +$defVal% DEF • +$evaVal% DODGE" +
                                    if (profile.hpRegenPercent > 0) " • +${profile.hpRegenPercent}% HP/EN Regen" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = EnergyGreen
                            )
                            if (pilotTileMod != null) {
                                val armorText = if (pilotTileMod.baseArmor != null && pilotTileMod.effectiveArmor != null) {
                                    " • ARM ${pilotTileMod.baseArmor}→${pilotTileMod.effectiveArmor}"
                                } else {
                                    ""
                                }
                                Text(
                                    text = "PILOT MOD (${pilotTileMod.pilotName}): EVA ${pilotTileMod.basePilotEvadeStat}→${pilotTileMod.effectivePilotEvadeStat}$armorText",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PanelBorderCyan
                                )
                            }
                            Text(
                                text = "OFFENSE: +$atkVal% ATK • $hitTxt HIT • +$crtVal% CRIT",
                                style = MaterialTheme.typography.labelSmall,
                                color = SpiritGold
                            )
                            Text(
                                text = profile.tacticalDescription,
                                style = MaterialTheme.typography.bodySmall,
                                color = SteelText
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("CLOSE", color = PanelBorderCyan)
            }
        }
    )
}

@Composable
private fun MissionResultOverlay(
    isVictory: Boolean,
    turnCount: Int,
    onRestart: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidNavy.copy(alpha = 0.88f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .background(PanelNavy, CutCornerShape(14.dp))
                .border(2.dp, if (isVictory) SpiritGold else EnemyCrimson, CutCornerShape(14.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (isVictory) "STAGE CLEAR // VICTORY" else "MISSION FAILED",
                style = MaterialTheme.typography.displayMedium,
                color = if (isVictory) SpiritGold else EnemyCrimson
            )
            Text(
                text = if (isVictory) {
                    "All hostile forces neutralized in $turnCount Turn(s)! Bonus Tactical Credits awarded."
                } else {
                    "All allied units were shot down. Upgrade your units in the Hangar and sortie again!"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = SteelText
            )
            Button(
                onClick = onRestart,
                colors = ButtonDefaults.buttonColors(containerColor = AllyBeamBlue),
                modifier = Modifier.testTag("restart_mission_button")
            ) {
                Text("REDEPLOY STAGE", style = MaterialTheme.typography.labelLarge, color = VoidNavy)
            }
        }
    }
}

@Composable
private fun MoraleSystemDialog(
    uiState: SrwEngineUiState,
    confidenceProfilesByUnitId: Map<String, PilotConfidenceProfile> = emptyMap(),
    onTriggerEvent: (TacticalMoraleEvent) -> Unit,
    onDismiss: () -> Unit
) {
    val inspected = uiState.unitAtCursor ?: uiState.selectedUnit ?: uiState.units.firstOrNull()
    val confidenceProfile = inspected?.let { confidenceProfilesByUnitId[it.id] }
    val moraleMods = remember(inspected) {
        inspected?.let {
            com.example.srw.engine.TacticalCombatEngine.defaultCombatManager
                .pilotMoraleSystem
                .computeUnitStatModifiers(it)
        }
    }
    val moraleTier = confidenceProfile?.tier
        ?: moraleMods?.tier
        ?: MoraleTier.fromWill(inspected?.pilot?.will ?: 100)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CockpitSlate,
        title = {
            Column {
                Text(
                    text = "PILOT MORALE (WILL / 気力) SYSTEM",
                    style = MaterialTheme.typography.titleLarge,
                    color = SpiritGold
                )
                if (inspected != null) {
                    Text(
                        text = "${inspected.pilot.name} (${inspected.name}) • Confidence: ${inspected.pilot.will}/170 [${moraleTier.displayName} ${moraleTier.jpKanji}]",
                        style = MaterialTheme.typography.labelSmall,
                        color = moraleTier.badgeColor
                    )
                }
            }
        },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // 1. Selected Pilot Confidence Progress, Kills/Damage Tracked & Threshold Multipliers
                if (inspected != null && moraleMods != null) {
                    item {
                        val atkSign = if (moraleMods.attackOutputPercent >= 0) "+${moraleMods.attackOutputPercent}%" else "${moraleMods.attackOutputPercent}%"
                        val defSign = if (moraleMods.defenseModifierPercent >= 0) "+${moraleMods.defenseModifierPercent}%" else "${moraleMods.defenseModifierPercent}%"
                        val mobSign = if (moraleMods.mobilityDelta >= 0) "+${moraleMods.mobilityDelta}" else "${moraleMods.mobilityDelta}"
                        val hitSign = if (moraleMods.hitBonusPercent >= 0) "+${moraleMods.hitBonusPercent}%" else "${moraleMods.hitBonusPercent}%"
                        val critSign = if (moraleMods.critBonusPercent >= 0) "+${moraleMods.critBonusPercent}%" else "${moraleMods.critBonusPercent}%"
                        val initSign = if (moraleMods.initiativeDelta >= 0) "+${moraleMods.initiativeDelta}" else "${moraleMods.initiativeDelta}"

                        Surface(
                            color = PanelNavy,
                            shape = CutCornerShape(8.dp),
                            border = BorderStroke(1.dp, moraleTier.badgeColor)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "CONFIDENCE: ${moraleTier.displayName} (${moraleTier.jpKanji})",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = moraleTier.badgeColor
                                    )
                                    Text(
                                        text = "ATK ×${"%.2f".format(moraleTier.attackOutputMultiplier)} • DEF ×${"%.2f".format(moraleTier.defenseMultiplier)}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = SpiritGold
                                    )
                                }
                                Text(
                                    text = moraleTier.summary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SteelText
                                )

                                // Pilot Confidence Progress Bar & Kills/Damage Telemetry
                                if (confidenceProfile != null) {
                                    val nextLabel = confidenceProfile.nextThreshold?.let {
                                        "Next: ${it.label} at ${it.minConfidence} (+${confidenceProfile.pointsToNextThreshold} Will)"
                                    } ?: "MAX CONFIDENCE THRESHOLD REACHED"
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.testTag("pilot_confidence_progress_section")
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Kills: ${confidenceProfile.totalKillsTracked} • DMG Dealt: ${confidenceProfile.totalDamageDealt}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = EnergyGreen
                                            )
                                            Text(
                                                text = nextLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = PanelBorderCyan
                                            )
                                        }
                                        LinearProgressIndicator(
                                            progress = { confidenceProfile.progressWithinTier },
                                            color = moraleTier.badgeColor,
                                            trackColor = VoidNavy,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                        )
                                    }
                                }

                                // Threshold-Based Unit Stat Multipliers Grid
                                Surface(
                                    color = VoidNavy,
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, moraleTier.badgeColor.copy(alpha = 0.5f)),
                                    modifier = Modifier.testTag("morale_dialog_stat_modifiers")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Text(
                                            text = "THRESHOLD STAT MULTIPLIERS (WILL ${moraleMods.will}):",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = PanelBorderCyan
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "ATK Output: $atkSign",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = moraleTier.badgeColor
                                            )
                                            Text(
                                                text = "Armor: ${moraleMods.effectiveArmor} ($defSign)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SteelText
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Mobility: ${moraleMods.effectiveMobility} ($mobSign)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = EnergyGreen
                                            )
                                            Text(
                                                text = "Hit/Eva: $hitSign • Crit: $critSign",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SpiritGold
                                            )
                                        }
                                        if (confidenceProfile != null) {
                                            val mult = confidenceProfile.statMultipliers
                                            Text(
                                                text = "ACC ×${"%.2f".format(mult.accuracyMultiplier)} • EVA ×${"%.2f".format(mult.evasionMultiplier)} • MOB ×${"%.2f".format(mult.mobilityMultiplier)} • Melee/Rng ${mult.effectiveMeleeStat}/${mult.effectiveRangedStat}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = PanelBorderCyan
                                            )
                                        }
                                        Text(
                                            text = "Initiative Edge: $initSign • Eff. Pilot Hit/Eva: ${moraleMods.effectiveHitStat}/${moraleMods.effectiveEvadeStat}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MutedTelemetry
                                        )
                                    }
                                }

                                // Compact Threshold Multiplier Ladder
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    WillMoraleViewModel.CONFIDENCE_THRESHOLDS.forEach { thr ->
                                        val isActive = thr.tier == moraleTier
                                        Surface(
                                            color = if (isActive) thr.tier.badgeColor.copy(alpha = 0.22f) else VoidNavy,
                                            shape = RoundedCornerShape(4.dp),
                                            border = BorderStroke(
                                                width = if (isActive) 1.2.dp else 0.75.dp,
                                                color = if (isActive) thr.tier.badgeColor else SteelText.copy(alpha = 0.35f)
                                            )
                                        ) {
                                            Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                                                Text(
                                                    text = "${thr.minConfidence}+ ${thr.label}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isActive) thr.tier.badgeColor else SteelText
                                                )
                                                Text(
                                                    text = "ATK ×${"%.2f".format(thr.attackMultiplier)} • DEF ×${"%.2f".format(thr.defenseMultiplier)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isActive) Color.White else MutedTelemetry
                                                )
                                            }
                                        }
                                    }
                                }

                                Text(
                                    text = "Combat Triggers: Kill +8 (+12 Boss) • Heavy Impact +2 • Ally Kill +2 • Crisis +6 • Damage Taken +2 • Crit/Evade +3 • Miss -3",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MutedTelemetry
                                )

                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "SPECIAL WEAPON WILL ACCESS (${inspected.name}):",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PanelBorderCyan
                                )
                                inspected.weapons.forEach { weapon ->
                                    val unlocked = inspected.pilot.will >= weapon.willReq
                                    val scaledPwr = (weapon.effectivePower(inspected.upgradeWeapon, inspected.magicLevel) *
                                        moraleTier.attackOutputMultiplier).roundToInt()
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "• ${weapon.name} (PWR $scaledPwr)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (unlocked) Color.White else MutedTelemetry
                                        )
                                        Text(
                                            text = if (unlocked) "REQ ${weapon.willReq} WILL [UNLOCKED]"
                                            else "REQ ${weapon.willReq} WILL [LOCKED]",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (unlocked) EnergyGreen else EnemyCrimson
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Interactive Battlefield Morale Event Triggers
                item {
                    Text(
                        text = "BATTLEFIELD MORALE EVENT TRIGGERS (戦況イベント):",
                        style = MaterialTheme.typography.labelSmall,
                        color = SpiritGold
                    )
                }

                items(TacticalMoraleEvent.entries) { event ->
                    Surface(
                        onClick = { onTriggerEvent(event) },
                        color = PanelNavy,
                        shape = CutCornerShape(8.dp),
                        border = BorderStroke(1.dp, event.badgeColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("morale_event_${event.name}")
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = event.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = event.badgeColor
                                )
                                Text(
                                    text = "TRIGGER",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = SpiritGold
                                )
                            }
                            Text(
                                text = event.jpSubtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                            Text(
                                text = event.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = SteelText
                            )
                        }
                    }
                }

                // 3. Recent Combat & Event Morale Fluctuation Log
                if (uiState.recentMoraleChanges.isNotEmpty()) {
                    item {
                        Text(
                            text = "RECENT MORALE FLUCTUATIONS:",
                            style = MaterialTheme.typography.labelSmall,
                            color = PanelBorderCyan
                        )
                    }
                    items(uiState.recentMoraleChanges) { chg ->
                        val sign = if (chg.delta >= 0) "+${chg.delta}" else "${chg.delta}"
                        Surface(
                            color = VoidNavy,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(
                                1.dp,
                                if (chg.delta >= 0) EnergyGreen.copy(alpha = 0.6f) else EnemyCrimson.copy(alpha = 0.6f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "${chg.pilotName} (${chg.unitName}): Will ${chg.previousWill} -> ${chg.newWill} ($sign) [${chg.newTier.displayName}]",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (chg.delta >= 0) EnergyGreen else EnemyCrimson
                                )
                                Text(
                                    text = chg.reason,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SteelText
                                )
                                if (chg.newlyUnlockedWeapons.isNotEmpty()) {
                                    Text(
                                        text = "★ Unlocked Special Weapon(s): ${chg.newlyUnlockedWeapons.joinToString(", ")}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SpiritGold
                                    )
                                }
                                if (chg.newlyLockedWeapons.isNotEmpty()) {
                                    Text(
                                        text = "⚠ Locked Special Weapon(s): ${chg.newlyLockedWeapons.joinToString(", ")}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EnemyCrimson
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("CLOSE", color = PanelBorderCyan)
            }
        }
    )
}

