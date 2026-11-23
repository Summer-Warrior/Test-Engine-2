package com.example.srw.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.srw.engine.DevelopmentCategory
import com.example.srw.engine.DevelopmentSystem
import com.example.srw.engine.PilotProgressionSystem
import com.example.srw.model.*
import com.example.srw.viewmodel.PilotLevelManager
import com.example.srw.viewmodel.PilotLevelProgress
import com.example.srw.viewmodel.SandboxStudioConfig
import com.example.srw.viewmodel.SrwEngineUiState
import com.example.ui.theme.*

@Composable
fun CinematicStudioScreen(
    config: SandboxStudioConfig,
    templates: List<MechaUnit>,
    onUpdateConfig: ((SandboxStudioConfig) -> SandboxStudioConfig) -> Unit,
    onLaunchCutscene: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedAttacker = templates.getOrElse(config.attackerIndex) { templates.first() }
    val selectedWeapon = selectedAttacker.weapons.getOrElse(config.weaponIndex) { selectedAttacker.weapons.first() }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("cinematic_studio_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                color = PanelNavy,
                shape = CutCornerShape(10.dp),
                border = BorderStroke(1.5.dp, PanelBorderCyan)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MovieFilter,
                            contentDescription = "Cinematic Battle Director",
                            tint = SpiritGold
                        )
                        Text(
                            text = "SRW CINEMATIC BATTLE DIRECTOR",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                    }
                    Text(
                        text = "Configure any Super Robot vs Real Robot matchup, weapon choreography, terrain backdrop, and Spirit Command aura to test the side-view turn-based animation engine.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedTelemetry
                    )

                    Spacer(Modifier.height(4.dp))

                    Button(
                        onClick = onLaunchCutscene,
                        colors = ButtonDefaults.buttonColors(containerColor = EnemyCrimson),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("play_sandbox_cutscene_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play cutscene")
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "LAUNCH CHOREOGRAPHED BATTLE SCENE",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }

        // 1. Select Attacker Unit
        item {
            StudioSectionCard(title = "1. SELECT ATTACKER MOBILE WEAPON") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(templates) { idx, unit ->
                        val isSelected = idx == config.attackerIndex
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                onUpdateConfig { it.copy(attackerIndex = idx, weaponIndex = it.weaponIndex.coerceAtMost(unit.weapons.lastIndex)) }
                            },
                            label = {
                                Text(
                                    text = "${unit.name} (${unit.archetype.badge})",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AllyDeepBlue,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.testTag("studio_attacker_$idx")
                        )
                    }
                }
            }
        }

        // 2. Select Weapon Choreography
        item {
            StudioSectionCard(
                title = "2. SELECT WEAPON & ANIMATION CHOREOGRAPHY (${selectedWeapon.animStyle.label})"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    selectedAttacker.weapons.forEachIndexed { wIdx, weapon ->
                        val isSelected = wIdx == config.weaponIndex
                        Surface(
                            onClick = { onUpdateConfig { it.copy(weaponIndex = wIdx) } },
                            color = if (isSelected) AllyDeepBlue.copy(alpha = 0.55f) else VoidNavy,
                            shape = CutCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (isSelected) SpiritGold else GridLineColor),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("studio_weapon_$wIdx")
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = weapon.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (isSelected) SpiritGold else Color.White
                                    )
                                    Text(
                                        text = "Choreography: ${weapon.animStyle.label} • RNG ${weapon.minRange}~${weapon.maxRange}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = PanelBorderCyan
                                    )
                                }
                                Text(
                                    text = "PWR ${weapon.basePower}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = SpiritGold
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Select Defender Unit
        item {
            StudioSectionCard(title = "3. SELECT DEFENDER TARGET") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(templates) { idx, unit ->
                        val isSelected = idx == config.defenderIndex
                        FilterChip(
                            selected = isSelected,
                            onClick = { onUpdateConfig { it.copy(defenderIndex = idx) } },
                            label = {
                                Text(
                                    text = unit.name,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EnemyDarkRed,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.testTag("studio_defender_$idx")
                        )
                    }
                }
            }
        }

        // 4. Select Terrain Stage & Outcome Rules
        item {
            StudioSectionCard(title = "4. STAGE ENVIRONMENT & SPIRIT OVERRIDES") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val terrains = listOf(
                            TerrainType.CITY,
                            TerrainType.FOREST,
                            TerrainType.SPACE,
                            TerrainType.MOUNTAIN,
                            TerrainType.BASE,
                            TerrainType.ASTEROID
                        )
                        items(terrains) { terrain ->
                            val selected = config.terrain == terrain
                            FilterChip(
                                selected = selected,
                                onClick = { onUpdateConfig { it.copy(terrain = terrain) } },
                                label = {
                                    Text(terrain.displayName, style = MaterialTheme.typography.labelSmall)
                                }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = config.enableValor,
                            onClick = { onUpdateConfig { it.copy(enableValor = !it.enableValor) } },
                            label = { Text("VALOR Spirit (2.0x DMG)", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = config.enableCounter,
                            onClick = { onUpdateConfig { it.copy(enableCounter = !it.enableCounter) } },
                            label = { Text("Defender Counter-Attack", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val outcomes: List<Pair<String, StrikeOutcome?>> = listOf(
                            "AUTO" to null,
                            "CRITICAL" to StrikeOutcome.CRITICAL,
                            "DIRECT HIT" to StrikeOutcome.HIT,
                            "EVADE (MISS)" to StrikeOutcome.MISS
                        )
                        outcomes.forEach { (label, outcome) ->
                            val selected = config.forcedOutcome == outcome
                            OutlinedButton(
                                onClick = { onUpdateConfig { it.copy(forcedOutcome = outcome) } },
                                border = BorderStroke(1.dp, if (selected) SpiritGold else MutedTelemetry),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) SpiritGold else SteelText,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        color = PanelNavy,
        shape = CutCornerShape(8.dp),
        border = BorderStroke(1.dp, GridLineColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = PanelBorderCyan
            )
            content()
        }
    }
}

@Composable
fun HangarUpgradeScreen(
    uiState: SrwEngineUiState,
    developmentSystem: DevelopmentSystem,
    pilotProgressionSystem: PilotProgressionSystem = PilotProgressionSystem(),
    levelProgressByUnitId: Map<String, PilotLevelProgress> = emptyMap(),
    onSelectHangarUnit: (String) -> Unit,
    onSelectCategory: (DevelopmentCategory) -> Unit,
    onUpgradeStat: (String, String) -> Unit,
    onEvolveFrame: (String) -> Unit,
    onForgeWeapon: (String, String) -> Unit,
    onDevelopPrototypeWeapon: (String) -> Unit,
    onInstallArmorPlating: (String, ArmorPlatingTier) -> Unit,
    onUpgradeBarrier: (String) -> Unit,
    onUpgradeMagicConduit: (String) -> Unit,
    onAttuneAffinity: (String, ElementalAffinity) -> Unit,
    onResearchSpell: (String, MagicSpell) -> Unit,
    onUnlockPilotPassive: (String, PilotPassiveAbility) -> Unit = { _, _ -> },
    onUpgradePilotSpirit: (String, SpiritCommand) -> Unit = { _, _ -> },
    onSelectScenario: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val allies = uiState.units.filter { it.faction == Faction.ALLY }
    val selectedUnit = allies.find { it.id == uiState.selectedHangarUnitId } ?: allies.firstOrNull()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("hangar_upgrade_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Top R&D Credits & Development Lab Banner
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PanelNavy, CutCornerShape(8.dp))
                    .border(1.dp, SpiritGold.copy(alpha = 0.8f), CutCornerShape(8.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Build, contentDescription = "Development Lab", tint = SpiritGold)
                    Column {
                        Text(
                            text = "AEGIS R&D DEVELOPMENT SYSTEM",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Text(
                            text = "Unit Frame • Weapons • Armor • Magic • Pilot Academy",
                            style = MaterialTheme.typography.labelSmall,
                            color = PanelBorderCyan
                        )
                    }
                }
                Text(
                    text = "${uiState.credits} CR",
                    style = MaterialTheme.typography.titleLarge,
                    color = SpiritGold
                )
            }
        }

        // 2. Unit Selector Strip
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(allies, key = { it.id }) { unit ->
                    val isSelected = unit.id == selectedUnit?.id
                    Surface(
                        onClick = { onSelectHangarUnit(unit.id) },
                        color = if (isSelected) AllyDeepBlue else PanelNavy,
                        shape = CutCornerShape(8.dp),
                        border = BorderStroke(
                            1.5.dp,
                            if (isSelected) SpiritGold else GridLineColor
                        ),
                        modifier = Modifier.testTag("hangar_unit_chip_${unit.id}")
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "[${unit.elementalAffinity.kanji}]",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = unit.elementalAffinity.badgeColor
                                )
                                Text(
                                    text = unit.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White
                                )
                            }
                            val unitLevel = levelProgressByUnitId[unit.id]?.level ?: unit.pilot.level
                            Text(
                                text = "LV.$unitLevel • T${unit.frameTier} • ${unit.pilot.xp} XP",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) SpiritGold else MutedTelemetry
                            )
                        }
                    }
                }
            }
        }

        // 3. 5-Pillar Development Category Switcher (UNIT / WEAPON / ARMOR / MAGIC / PILOT)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DevelopmentCategory.entries.forEach { cat ->
                    val isSelected = uiState.activeDevelopmentCategory == cat
                    Button(
                        onClick = { onSelectCategory(cat) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) AllyBeamBlue else PanelNavy,
                            contentColor = if (isSelected) VoidNavy else SteelText
                        ),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                        shape = CutCornerShape(6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .testTag("dev_tab_${cat.name}")
                    ) {
                        Text(
                            text = cat.badge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // 4. Active Development Lab Content for Selected Unit
        if (selectedUnit != null) {
            when (uiState.activeDevelopmentCategory) {
                DevelopmentCategory.UNIT_FRAME -> {
                    item {
                        UnitFrameDevelopmentPanel(
                            unit = selectedUnit,
                            credits = uiState.credits,
                            developmentSystem = developmentSystem,
                            onUpgradeStat = { stat -> onUpgradeStat(selectedUnit.id, stat) },
                            onEvolveFrame = { onEvolveFrame(selectedUnit.id) }
                        )
                    }
                }

                DevelopmentCategory.WEAPON_FORGE -> {
                    item {
                        WeaponForgeDevelopmentPanel(
                            unit = selectedUnit,
                            credits = uiState.credits,
                            developmentSystem = developmentSystem,
                            onForgeWeapon = { wId -> onForgeWeapon(selectedUnit.id, wId) },
                            onDevelopPrototype = { onDevelopPrototypeWeapon(selectedUnit.id) }
                        )
                    }
                }

                DevelopmentCategory.ARMOR_BARRIER -> {
                    item {
                        ArmorAndBarrierDevelopmentPanel(
                            unit = selectedUnit,
                            credits = uiState.credits,
                            developmentSystem = developmentSystem,
                            onInstallPlating = { tier -> onInstallArmorPlating(selectedUnit.id, tier) },
                            onUpgradeBarrier = { onUpgradeBarrier(selectedUnit.id) }
                        )
                    }
                }

                DevelopmentCategory.MAGIC_ARCANE -> {
                    item {
                        MagicArcaneDevelopmentPanel(
                            unit = selectedUnit,
                            credits = uiState.credits,
                            developmentSystem = developmentSystem,
                            onUpgradeConduit = { onUpgradeMagicConduit(selectedUnit.id) },
                            onAttuneAffinity = { aff -> onAttuneAffinity(selectedUnit.id, aff) },
                            onResearchSpell = { spell -> onResearchSpell(selectedUnit.id, spell) }
                        )
                    }
                }

                DevelopmentCategory.PILOT_ACADEMY -> {
                    item {
                        val fallbackManager = androidx.compose.runtime.remember { PilotLevelManager() }
                        val progress = levelProgressByUnitId[selectedUnit.id]
                            ?: fallbackManager.getLevelProgress(selectedUnit)
                        PilotAcademyProgressionPanel(
                            unit = selectedUnit,
                            levelProgress = progress,
                            progressionSystem = pilotProgressionSystem,
                            onUnlockPassive = { ability -> onUnlockPilotPassive(selectedUnit.id, ability) },
                            onUpgradeSpirit = { spirit -> onUpgradePilotSpirit(selectedUnit.id, spirit) }
                        )
                    }
                }
            }
        }

        // 5. Tactical Scenario Operations Selector
        item {
            Surface(
                color = PanelNavy,
                shape = CutCornerShape(10.dp),
                border = BorderStroke(1.dp, PanelBorderCyan.copy(alpha = 0.6f))
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "TACTICAL SCENARIO OPERATIONS",
                        style = MaterialTheme.typography.titleMedium,
                        color = PanelBorderCyan
                    )
                    uiState.scenarios.forEachIndexed { idx, mission ->
                        val isCurrent = idx == uiState.currentScenarioIndex
                        Surface(
                            onClick = { onSelectScenario(idx) },
                            color = if (isCurrent) AllyDeepBlue.copy(alpha = 0.5f) else VoidNavy,
                            shape = CutCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (isCurrent) SpiritGold else GridLineColor),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("scenario_card_$idx")
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${mission.code}: ${mission.title}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (isCurrent) SpiritGold else Color.White
                                    )
                                    Text(
                                        text = mission.subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MutedTelemetry
                                    )
                                }
                                Text(
                                    text = if (isCurrent) "ACTIVE" else "DEPLOY",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isCurrent) EnergyGreen else AllyBeamBlue
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UnitFrameDevelopmentPanel(
    unit: MechaUnit,
    credits: Int,
    developmentSystem: DevelopmentSystem,
    onUpgradeStat: (String) -> Unit,
    onEvolveFrame: () -> Unit
) {
    val frameCost = developmentSystem.getFrameEvolutionCost(unit.frameTier)
    val canEvolve = unit.frameTier < 3 && credits >= frameCost

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Frame Evolution Card
        Surface(
            color = PanelNavy,
            shape = CutCornerShape(10.dp),
            border = BorderStroke(1.5.dp, SpiritGold)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "CHASSIS FRAME EVOLUTION // TIER ${unit.frameTier}/3",
                            style = MaterialTheme.typography.labelLarge,
                            color = SpiritGold
                        )
                        Text(
                            text = "${unit.name} [${unit.modelCode}]",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                    }
                    Button(
                        onClick = onEvolveFrame,
                        enabled = canEvolve,
                        colors = ButtonDefaults.buttonColors(containerColor = EnemyCrimson),
                        modifier = Modifier.testTag("evolve_frame_${unit.id}")
                    ) {
                        Text(
                            text = if (unit.frameTier >= 3) "MASTER TIER" else "EVOLVE ($frameCost CR)",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
                Text(
                    text = "Evolving Frame grants +900 Max HP, +35 Max EN, +150 Armor, +14 Mobility, and +1 Move Range at Tier 3.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedTelemetry
                )
                if (unit.hasFullCustomBonus) {
                    Text(
                        text = "★ FULL CUSTOM BONUS ACTIVE: +30 Max EN, +10 Starting Will, +10% Crit Rate!",
                        style = MaterialTheme.typography.labelLarge,
                        color = EnergyGreen
                    )
                } else {
                    Text(
                        text = "Full Custom Bonus: Tune HP, EN, ARMOR, and MOBILITY to Lv.3+ to unlock!",
                        style = MaterialTheme.typography.labelSmall,
                        color = PanelBorderCyan
                    )
                }
            }
        }

        // 5-Bar Core Stat Tuning Card
        Surface(
            color = PanelNavy,
            shape = CutCornerShape(10.dp),
            border = BorderStroke(1.dp, AllyBeamBlue.copy(alpha = 0.7f))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "CORE MECHA STAT TUNING",
                    style = MaterialTheme.typography.labelLarge,
                    color = PanelBorderCyan
                )

                val hpCost = developmentSystem.getStatTuneCost(unit.upgradeHp)
                UpgradeStatRow(
                    label = "MAX HP (${unit.maxHp})",
                    level = unit.upgradeHp,
                    costLabel = if (unit.upgradeHp >= 5) "MAX" else "$hpCost CR",
                    canAfford = credits >= hpCost && unit.upgradeHp < 5,
                    testTag = "upgrade_hp_${unit.id}",
                    onUpgrade = { onUpgradeStat("HP") }
                )

                val enCost = developmentSystem.getStatTuneCost(unit.upgradeEn)
                UpgradeStatRow(
                    label = "MAX EN (${unit.maxEn})",
                    level = unit.upgradeEn,
                    costLabel = if (unit.upgradeEn >= 5) "MAX" else "$enCost CR",
                    canAfford = credits >= enCost && unit.upgradeEn < 5,
                    testTag = "upgrade_en_${unit.id}",
                    onUpgrade = { onUpgradeStat("EN") }
                )

                val armorCost = developmentSystem.getStatTuneCost(unit.upgradeArmor)
                UpgradeStatRow(
                    label = "ARMOR (${unit.armor})",
                    level = unit.upgradeArmor,
                    costLabel = if (unit.upgradeArmor >= 5) "MAX" else "$armorCost CR",
                    canAfford = credits >= armorCost && unit.upgradeArmor < 5,
                    testTag = "upgrade_armor_${unit.id}",
                    onUpgrade = { onUpgradeStat("ARMOR") }
                )

                val mobCost = developmentSystem.getStatTuneCost(unit.upgradeMobility)
                UpgradeStatRow(
                    label = "MOBILITY (${unit.mobility})",
                    level = unit.upgradeMobility,
                    costLabel = if (unit.upgradeMobility >= 5) "MAX" else "$mobCost CR",
                    canAfford = credits >= mobCost && unit.upgradeMobility < 5,
                    testTag = "upgrade_mobility_${unit.id}",
                    onUpgrade = { onUpgradeStat("MOBILITY") }
                )

                val wepCost = developmentSystem.getStatTuneCost(unit.upgradeWeapon)
                UpgradeStatRow(
                    label = "ALL WEAPONS (+${unit.upgradeWeapon * 200})",
                    level = unit.upgradeWeapon,
                    costLabel = if (unit.upgradeWeapon >= 5) "MAX" else "$wepCost CR",
                    canAfford = credits >= wepCost && unit.upgradeWeapon < 5,
                    testTag = "upgrade_weapon_${unit.id}",
                    onUpgrade = { onUpgradeStat("WEAPON") }
                )
            }
        }
    }
}

@Composable
private fun WeaponForgeDevelopmentPanel(
    unit: MechaUnit,
    credits: Int,
    developmentSystem: DevelopmentSystem,
    onForgeWeapon: (String) -> Unit,
    onDevelopPrototype: () -> Unit
) {
    val protoBlueprint = developmentSystem.getPrototypeWeaponBlueprint(unit)
    val protoCost = developmentSystem.prototypeResearchCost

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Prototype Weapon R&D Card
        Surface(
            color = PanelNavy,
            shape = CutCornerShape(10.dp),
            border = BorderStroke(1.5.dp, if (unit.unlockedPrototypeWeapon) EnergyGreen else EnemyCrimson)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "PROTOTYPE FINISHER R&D",
                            style = MaterialTheme.typography.labelLarge,
                            color = SpiritGold
                        )
                        Text(
                            text = protoBlueprint.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                        Text(
                            text = "Base PWR ${protoBlueprint.basePower} • RNG ${protoBlueprint.minRange}~${protoBlueprint.maxRange} • WILL ${protoBlueprint.willReq}",
                            style = MaterialTheme.typography.labelSmall,
                            color = PanelBorderCyan
                        )
                    }
                    Button(
                        onClick = onDevelopPrototype,
                        enabled = !unit.unlockedPrototypeWeapon && credits >= protoCost,
                        colors = ButtonDefaults.buttonColors(containerColor = EnemyCrimson),
                        modifier = Modifier.testTag("develop_prototype_${unit.id}")
                    ) {
                        Text(
                            text = if (unit.unlockedPrototypeWeapon) "MOUNTED" else "DEVELOP ($protoCost CR)",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }

        // Individual Weapon Forging List
        Surface(
            color = PanelNavy,
            shape = CutCornerShape(10.dp),
            border = BorderStroke(1.dp, AllyBeamBlue.copy(alpha = 0.7f))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "INDIVIDUAL WEAPON FORGING (+260 PWR, +4% HIT, +3% CRIT / RANK)",
                    style = MaterialTheme.typography.labelLarge,
                    color = PanelBorderCyan
                )
                unit.weapons.forEach { weapon ->
                    val forgeCost = developmentSystem.getWeaponForgeCost(weapon.forgeLevel)
                    val canForge = weapon.forgeLevel < 5 && credits >= forgeCost
                    Surface(
                        color = VoidNavy,
                        shape = CutCornerShape(8.dp),
                        border = BorderStroke(1.dp, GridLineColor)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${weapon.name} ${if (weapon.forgeLevel > 0) "+${weapon.forgeLevel}" else ""}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (weapon.isArcaneMagic) Color(0xFFD500F9) else Color.White
                                )
                                Text(
                                    text = "PWR ${weapon.effectivePower(unit.upgradeWeapon, unit.magicLevel)}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = SpiritGold
                                )
                            }
                            Text(
                                text = "RNG ${weapon.minRange}~${weapon.maxRange} • HIT +${weapon.effectiveHitBonus}% • CRIT +${weapon.effectiveCritBonus}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MutedTelemetry
                            )
                            UpgradeStatRow(
                                label = "Forge Rank (${weapon.forgeLevel}/5)",
                                level = weapon.forgeLevel,
                                costLabel = if (weapon.forgeLevel >= 5) "MAX" else "$forgeCost CR",
                                canAfford = canForge,
                                testTag = "forge_weapon_${weapon.id}",
                                onUpgrade = { onForgeWeapon(weapon.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArmorAndBarrierDevelopmentPanel(
    unit: MechaUnit,
    credits: Int,
    developmentSystem: DevelopmentSystem,
    onInstallPlating: (ArmorPlatingTier) -> Unit,
    onUpgradeBarrier: () -> Unit
) {
    val barrierCost = developmentSystem.getBarrierUpgradeCost(unit.barrierUpgradeLevel)
    val canUpgradeBarrier = unit.barrierUpgradeLevel < 5 && credits >= barrierCost

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Barrier Field Generator R&D Card
        Surface(
            color = PanelNavy,
            shape = CutCornerShape(10.dp),
            border = BorderStroke(1.5.dp, PanelBorderCyan)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Shield, contentDescription = "Barrier Lab", tint = PanelBorderCyan)
                    Column {
                        Text(
                            text = "BARRIER FIELD GENERATOR // ${unit.effectiveBarrierName.uppercase()}",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Text(
                            text = "Current Barrier Mitigation: -${unit.effectiveBarrierThreshold} DMG per hit (Costs 10 EN)",
                            style = MaterialTheme.typography.labelSmall,
                            color = SpiritGold
                        )
                    }
                }
                UpgradeStatRow(
                    label = "Generator Rank (+160 Barrier/Lv)",
                    level = unit.barrierUpgradeLevel,
                    costLabel = if (unit.barrierUpgradeLevel >= 5) "MAX" else "$barrierCost CR",
                    canAfford = canUpgradeBarrier,
                    testTag = "upgrade_barrier_${unit.id}",
                    onUpgrade = onUpgradeBarrier
                )
            }
        }

        // Armor Plating Material Tiers
        Surface(
            color = PanelNavy,
            shape = CutCornerShape(10.dp),
            border = BorderStroke(1.dp, AllyBeamBlue.copy(alpha = 0.7f))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "ARMOR PLATING ALLOY SYNTHESIS (CURRENT: ${unit.armorPlating.displayName})",
                    style = MaterialTheme.typography.labelLarge,
                    color = PanelBorderCyan
                )
                ArmorPlatingTier.entries.forEach { tier ->
                    val isInstalled = unit.armorPlating == tier
                    val isLowerTier = tier.level < unit.armorPlating.level
                    val canInstall = !isInstalled && !isLowerTier && credits >= tier.creditCost

                    Surface(
                        color = if (isInstalled) AllyDeepBlue.copy(alpha = 0.55f) else VoidNavy,
                        shape = CutCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (isInstalled) EnergyGreen else GridLineColor)
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
                                    text = "${tier.displayName} [${tier.shortCode}]",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isInstalled) EnergyGreen else Color.White
                                )
                                Text(
                                    text = tier.summary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MutedTelemetry
                                )
                            }
                            OutlinedButton(
                                onClick = { onInstallPlating(tier) },
                                enabled = canInstall,
                                border = BorderStroke(1.dp, if (canInstall) SpiritGold else GridLineColor),
                                modifier = Modifier.testTag("install_armor_${tier.name}")
                            ) {
                                Text(
                                    text = when {
                                        isInstalled -> "INSTALLED"
                                        isLowerTier -> "SUPERSEDED"
                                        else -> "${tier.creditCost} CR"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isInstalled) EnergyGreen else if (canInstall) SpiritGold else MutedTelemetry
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MagicArcaneDevelopmentPanel(
    unit: MechaUnit,
    credits: Int,
    developmentSystem: DevelopmentSystem,
    onUpgradeConduit: () -> Unit,
    onAttuneAffinity: (ElementalAffinity) -> Unit,
    onResearchSpell: (MagicSpell) -> Unit
) {
    val conduitCost = developmentSystem.getMagicConduitUpgradeCost(unit.magicLevel)
    val canUpgradeConduit = unit.magicLevel < 5 && credits >= conduitCost

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // 1. Arcane Prana Conduit Level
        Surface(
            color = PanelNavy,
            shape = CutCornerShape(10.dp),
            border = BorderStroke(1.5.dp, Color(0xFFD500F9))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "Arcane Conduit", tint = Color(0xFFD500F9))
                    Column {
                        Text(
                            text = "ARCANE PRANA CONDUIT // LEVEL ${unit.magicLevel}/5",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Text(
                            text = "Max Mana/SP: ${unit.effectiveMaxSp} • Lv.2 Mounts Prana Elemental Cannon (+320 PWR/Lv)",
                            style = MaterialTheme.typography.labelSmall,
                            color = SpiritGold
                        )
                    }
                }
                UpgradeStatRow(
                    label = "Conduit Resonance (Lv.${unit.magicLevel}/5)",
                    level = unit.magicLevel,
                    costLabel = if (unit.magicLevel >= 5) "MAX" else "$conduitCost CR",
                    canAfford = canUpgradeConduit,
                    testTag = "upgrade_magic_conduit_${unit.id}",
                    onUpgrade = onUpgradeConduit
                )
            }
        }

        // 2. Elemental Affinity Attunement
        Surface(
            color = PanelNavy,
            shape = CutCornerShape(10.dp),
            border = BorderStroke(1.dp, AllyBeamBlue.copy(alpha = 0.7f))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "ELEMENTAL PRANA ATTUNEMENT (1,000 CR)",
                    style = MaterialTheme.typography.labelLarge,
                    color = PanelBorderCyan
                )
                ElementalAffinity.entries.forEach { affinity ->
                    val isCurrent = unit.elementalAffinity == affinity
                    val canAttune = !isCurrent && credits >= developmentSystem.elementalAttuneCost
                    Surface(
                        color = if (isCurrent) affinity.badgeColor.copy(alpha = 0.2f) else VoidNavy,
                        shape = CutCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (isCurrent) affinity.badgeColor else GridLineColor)
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
                                    text = "${affinity.displayName} [${affinity.kanji}]",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = affinity.badgeColor
                                )
                                Text(
                                    text = affinity.summary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SteelText
                                )
                            }
                            OutlinedButton(
                                onClick = { onAttuneAffinity(affinity) },
                                enabled = canAttune,
                                border = BorderStroke(1.dp, if (canAttune) affinity.badgeColor else GridLineColor),
                                modifier = Modifier.testTag("attune_element_${affinity.name}")
                            ) {
                                Text(
                                    text = if (isCurrent) "ATTUNED" else "1000 CR",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isCurrent) EnergyGreen else SteelText
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Arcane Spell Grimoire Research
        Surface(
            color = PanelNavy,
            shape = CutCornerShape(10.dp),
            border = BorderStroke(1.dp, SpiritGold.copy(alpha = 0.7f))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "ARCANE SPELL GRIMOIRE RESEARCH",
                    style = MaterialTheme.typography.labelLarge,
                    color = SpiritGold
                )
                MagicSpell.entries.forEach { spell ->
                    val isUnlocked = spell in unit.unlockedSpells
                    val meetsMagicLevel = unit.magicLevel >= spell.requiredMagicLevel
                    val canResearch = !isUnlocked && meetsMagicLevel && credits >= spell.researchCreditCost

                    Surface(
                        color = if (isUnlocked) AllyDeepBlue.copy(alpha = 0.45f) else VoidNavy,
                        shape = CutCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (isUnlocked) spell.badgeColor else GridLineColor)
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
                                    text = "${spell.codeName} (${spell.jpName}) • ${spell.manaSpCost} SP",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = spell.badgeColor
                                )
                                Text(
                                    text = spell.summary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SteelText
                                )
                                if (!meetsMagicLevel) {
                                    Text(
                                        text = "Requires Magic Conduit Lv.${spell.requiredMagicLevel}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = WarningAmber
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = { onResearchSpell(spell) },
                                enabled = canResearch,
                                border = BorderStroke(1.dp, if (canResearch) SpiritGold else GridLineColor),
                                modifier = Modifier.testTag("research_spell_${spell.name}")
                            ) {
                                Text(
                                    text = when {
                                        isUnlocked -> "LEARNED"
                                        !meetsMagicLevel -> "LV.${spell.requiredMagicLevel} REQ"
                                        else -> "${spell.researchCreditCost} CR"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isUnlocked) EnergyGreen else if (canResearch) SpiritGold else MutedTelemetry
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PilotAcademyProgressionPanel(
    unit: MechaUnit,
    levelProgress: PilotLevelProgress,
    progressionSystem: PilotProgressionSystem,
    onUnlockPassive: (PilotPassiveAbility) -> Unit,
    onUpgradeSpirit: (SpiritCommand) -> Unit
) {
    val pilot = unit.pilot
    val aceBonus = if (pilot.hasPassive(PilotPassiveAbility.PRE_EMPTIVE_ACE)) 10 else 0

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // 1. Pilot Dossier & Earned Battle XP Card
        Surface(
            color = PanelNavy,
            shape = CutCornerShape(10.dp),
            border = BorderStroke(1.5.dp, SpiritGold),
            modifier = Modifier.testTag("pilot_academy_dossier_${unit.id}")
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MilitaryTech,
                            contentDescription = "Pilot Academy",
                            tint = SpiritGold
                        )
                        Column {
                            Text(
                                text = "LV.${levelProgress.level} ${pilot.name.uppercase()} // ${pilot.rankTitle}",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Text(
                                text = "Callsign: ${pilot.callsign} • ${pilot.kills} Kill(s) • Total Earned: ${pilot.totalXpEarned} XP",
                                style = MaterialTheme.typography.labelSmall,
                                color = PanelBorderCyan
                            )
                        }
                    }
                    Surface(
                        color = SpiritGold.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, SpiritGold)
                    ) {
                        Text(
                            text = "${pilot.xp} XP",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = SpiritGold,
                            modifier = Modifier
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                .testTag("pilot_xp_badge")
                        )
                    }
                }

                // XP Threshold Level Progress Bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pilot_level_threshold_bar"),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "LVL ${levelProgress.level} PROGRESS (${levelProgress.xpIntoLevel}/${levelProgress.xpNeededForNextLevel} XP)",
                            style = MaterialTheme.typography.labelSmall,
                            color = SpiritGold
                        )
                        Text(
                            text = if (levelProgress.isMaxLevel) {
                                "MAX LEVEL"
                            } else {
                                "Next LV.${levelProgress.level + 1}: ${levelProgress.nextLevelThreshold} XP (${levelProgress.xpRemaining} left)"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = EnergyGreen
                        )
                    }
                    LinearProgressIndicator(
                        progress = { levelProgress.progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = SpiritGold,
                        trackColor = VoidNavy
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PilotStatMiniPill("MEL", "${pilot.meleeStat}", Modifier.weight(1f))
                    PilotStatMiniPill("RNG", "${pilot.rangedStat}", Modifier.weight(1f))
                    PilotStatMiniPill(
                        "HIT",
                        "${pilot.hitStat + aceBonus}" + if (aceBonus > 0) "+" else "",
                        Modifier.weight(1f)
                    )
                    PilotStatMiniPill(
                        "EVA",
                        "${pilot.evadeStat + aceBonus}" + if (aceBonus > 0) "+" else "",
                        Modifier.weight(1f)
                    )
                    PilotStatMiniPill("MAX SP", "${unit.effectiveMaxSp}", Modifier.weight(1f))
                }

                Text(
                    text = "Earn Pilot XP in battle (+15~55 XP/attack, +90~220 XP/kill) to level up stats (+4 MEL/RNG/HIT/EVA, +5 Max SP) and unlock Passives or Spirit Ranks.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedTelemetry
                )
            }
        }

        // 2. Permanent Passive Abilities (特殊スキル)
        Surface(
            color = PanelNavy,
            shape = CutCornerShape(10.dp),
            border = BorderStroke(1.dp, AllyBeamBlue.copy(alpha = 0.7f))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "PERMANENT PASSIVE ABILITIES (${pilot.passiveAbilities.size}/${PilotPassiveAbility.entries.size} UNLOCKED)",
                    style = MaterialTheme.typography.labelLarge,
                    color = PanelBorderCyan
                )
                PilotPassiveAbility.entries.forEach { ability ->
                    val unlocked = pilot.hasPassive(ability)
                    val canUnlock = !unlocked && pilot.xp >= ability.xpCost
                    Surface(
                        color = if (unlocked) ability.badgeColor.copy(alpha = 0.16f) else VoidNavy,
                        shape = CutCornerShape(8.dp),
                        border = BorderStroke(
                            1.dp,
                            if (unlocked) ability.badgeColor else GridLineColor
                        )
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
                                Text(
                                    text = "${ability.title} (${ability.jpName})",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (unlocked) ability.badgeColor else Color.White
                                )
                                Text(
                                    text = ability.summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SteelText
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = { onUnlockPassive(ability) },
                                enabled = canUnlock,
                                border = BorderStroke(
                                    1.dp,
                                    when {
                                        unlocked -> EnergyGreen
                                        canUnlock -> ability.badgeColor
                                        else -> GridLineColor
                                    }
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("unlock_passive_${ability.name}")
                            ) {
                                Text(
                                    text = if (unlocked) "ACTIVE" else "${ability.xpCost} XP",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when {
                                        unlocked -> EnergyGreen
                                        canUnlock -> SpiritGold
                                        else -> MutedTelemetry
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Spirit Command Mastery Upgrades (精神コマンド強化)
        Surface(
            color = PanelNavy,
            shape = CutCornerShape(10.dp),
            border = BorderStroke(1.dp, SpiritGold.copy(alpha = 0.7f))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "SPIRIT COMMAND MASTERY UPGRADES (RANK 1 -> MAX)",
                    style = MaterialTheme.typography.labelLarge,
                    color = SpiritGold
                )
                pilot.spirits.forEach { spirit ->
                    val currentRank = pilot.spiritRank(spirit)
                    val upgradeCost = progressionSystem.getSpiritUpgradeCost(currentRank)
                    val canUpgrade = currentRank < PilotProgressionSystem.MAX_SPIRIT_RANK && pilot.xp >= upgradeCost
                    val effectiveSpCost = progressionSystem.getEffectiveSpCost(pilot, spirit)
                    val currentSummary = progressionSystem.getSummaryForRank(spirit, currentRank)
                    val rankBadge = progressionSystem.getRankBadgeLabel(currentRank)

                    Surface(
                        color = if (currentRank > 0) spirit.badgeColor.copy(alpha = 0.14f) else VoidNavy,
                        shape = CutCornerShape(8.dp),
                        border = BorderStroke(
                            1.dp,
                            if (currentRank > 0) spirit.badgeColor else GridLineColor
                        )
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
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${spirit.codeName} (${spirit.jpKanji})",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = spirit.badgeColor
                                    )
                                    Surface(
                                        color = if (currentRank > 0) SpiritGold.copy(alpha = 0.2f) else CockpitSlate,
                                        shape = RoundedCornerShape(4.dp),
                                        border = BorderStroke(
                                            0.8.dp,
                                            if (currentRank > 0) SpiritGold else MutedTelemetry.copy(alpha = 0.5f)
                                        )
                                    ) {
                                        Text(
                                            text = "$rankBadge • ${effectiveSpCost}SP",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (currentRank > 0) SpiritGold else SteelText,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Current: $currentSummary",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White
                                )
                                if (currentRank < PilotProgressionSystem.MAX_SPIRIT_RANK) {
                                    Text(
                                        text = "Next (${progressionSystem.getRankBadgeLabel(currentRank + 1)}): ${progressionSystem.getSummaryForRank(spirit, currentRank + 1)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = PanelBorderCyan
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = { onUpgradeSpirit(spirit) },
                                enabled = canUpgrade,
                                border = BorderStroke(
                                    1.dp,
                                    when {
                                        currentRank >= PilotProgressionSystem.MAX_SPIRIT_RANK -> EnergyGreen
                                        canUpgrade -> SpiritGold
                                        else -> GridLineColor
                                    }
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("upgrade_spirit_${spirit.name}")
                            ) {
                                Text(
                                    text = if (currentRank >= PilotProgressionSystem.MAX_SPIRIT_RANK) {
                                        "MASTERED"
                                    } else {
                                        "UP ($upgradeCost XP)"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when {
                                        currentRank >= PilotProgressionSystem.MAX_SPIRIT_RANK -> EnergyGreen
                                        canUpgrade -> SpiritGold
                                        else -> MutedTelemetry
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PilotStatMiniPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = VoidNavy,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, GridLineColor),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MutedTelemetry)
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun UpgradeStatRow(
    label: String,
    level: Int,
    costLabel: String,
    canAfford: Boolean,
    testTag: String,
    onUpgrade: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = SteelText,
            modifier = Modifier.weight(1.2f)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(0.9f)
        ) {
            for (i in 1..5) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (i <= level) SpiritGold else VoidNavy)
                        .border(1.dp, GridLineColor, RoundedCornerShape(2.dp))
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        OutlinedButton(
            onClick = onUpgrade,
            enabled = canAfford,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            border = BorderStroke(1.dp, if (canAfford) EnergyGreen else GridLineColor),
            modifier = Modifier
                .height(30.dp)
                .testTag(testTag)
        ) {
            Text(
                text = costLabel,
                style = MaterialTheme.typography.labelSmall,
                color = if (canAfford) EnergyGreen else MutedTelemetry
            )
        }
    }
}

@Composable
fun CombatTelemetryLogScreen(
    logs: List<CombatLogEntry>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("combat_log_screen"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "TACTICAL COMBAT TELEMETRY LOG",
                style = MaterialTheme.typography.titleLarge,
                color = PanelBorderCyan
            )
        }

        items(logs, key = { it.id }) { entry ->
            val borderColor = when {
                entry.isDestruction -> EnemyCrimson
                entry.isCritical -> SpiritGold
                entry.phase == Faction.ALLY -> AllyBeamBlue.copy(alpha = 0.6f)
                else -> MutedTelemetry.copy(alpha = 0.5f)
            }

            Surface(
                color = PanelNavy,
                shape = CutCornerShape(8.dp),
                border = BorderStroke(1.dp, borderColor)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "TURN ${entry.turn} • ${entry.phase.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SpiritGold
                        )
                        if (entry.isDestruction) {
                            Text(
                                text = "UNIT SHOT DOWN",
                                style = MaterialTheme.typography.labelSmall,
                                color = EnemyCrimson
                            )
                        }
                    }
                    Text(
                        text = entry.headline,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = entry.detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedTelemetry
                    )
                }
            }
        }
    }
}
