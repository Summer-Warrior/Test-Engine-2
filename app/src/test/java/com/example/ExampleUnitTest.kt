package com.example

import com.example.srw.data.SrwRosterCatalog
import com.example.srw.engine.CombatManager
import com.example.srw.engine.GridTerrainSystem
import com.example.srw.engine.MoraleTier
import com.example.srw.engine.TacticalCombatEngine
import com.example.srw.engine.TerrainMapUtility
import com.example.srw.engine.TurnPhaseStage
import com.example.srw.model.BattleCutsceneSequence
import com.example.srw.model.DefendStance
import com.example.srw.model.Faction
import com.example.srw.model.GridPos
import com.example.srw.model.PilotPassiveAbility
import com.example.srw.model.SpiritCategory
import com.example.srw.model.SpiritCommand
import com.example.srw.model.StrikeOutcome
import com.example.srw.model.TerrainType
import com.example.srw.viewmodel.PilotLevelManager
import com.example.srw.viewmodel.SpiritCommandsViewModel
import com.example.srw.viewmodel.SrwEngineViewModel
import com.example.srw.viewmodel.TacticalInputMode
import com.example.srw.viewmodel.WillMoraleViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    private val terrainSystem = GridTerrainSystem()
    private val combatManager = CombatManager(gridTerrainSystem = terrainSystem)

    @Test
    fun pathfinding_computesReachableTilesAndRespectsEnemyCollision() {
        val scenario = SrwRosterCatalog.getScenarios().first()
        val kaiser = scenario.initialAllies.first { it.id == "ally_kaiser" }
        val reachable = TacticalCombatEngine.computeReachableTiles(
            mover = kaiser,
            terrainGrid = scenario.terrainGrid,
            allUnits = scenario.initialAllies + scenario.initialEnemies
        )
        assertTrue("Origin tile should be reachable with cost 0", reachable[kaiser.pos] == 0)
        assertTrue("Kaiser Blaze should reach multiple tiles", reachable.size > 8)
    }

    @Test
    fun gridTerrainSystem_mapsForestAndUrbanTilesToEvasionAndDefenseBonuses() {
        val astral = SrwRosterCatalog.createAstralGundam(GridPos(2, 2)) // Real Robot
        val kaiser = SrwRosterCatalog.createKaiserBlaze(GridPos(3, 2))  // Super Robot
        val zaku = SrwRosterCatalog.createEnemyVanguard("e1", "Alpha", GridPos(2, 3))

        // Forest tile: base +15% DEF, +20% EVADE (+5% EVADE S-rank adaptation for Real Robot)
        val forestProfile = terrainSystem.getProfile(TerrainType.FOREST)
        assertEquals(15, forestProfile.defenseBonusPercent)
        assertEquals(20, forestProfile.evasionBonusPercent)

        val astralForestBonus = terrainSystem.resolveUnitTerrainBonus(astral, TerrainType.FOREST)
        assertEquals(15, astralForestBonus.effectiveDefenseBonusPercent)
        assertEquals(25, astralForestBonus.effectiveEvasionBonusPercent)
        assertEquals("S", astralForestBonus.adaptationGrade)

        // Urban (CITY) tile: base +15% DEF, +15% EVADE (+5% DEF S-rank adaptation for Super Robot)
        val urbanProfile = terrainSystem.getProfile(TerrainType.CITY)
        assertEquals(15, urbanProfile.defenseBonusPercent)
        assertEquals(15, urbanProfile.evasionBonusPercent)

        val kaiserUrbanBonus = terrainSystem.resolveUnitTerrainBonus(kaiser, TerrainType.CITY)
        assertEquals(20, kaiserUrbanBonus.effectiveDefenseBonusPercent)
        assertEquals(15, kaiserUrbanBonus.effectiveEvasionBonusPercent)

        // Verify terrain bonuses directly impact hit rate and damage during battle calculations
        val weapon = zaku.weapons.last()
        val hitVsSpace = combatManager.calculateHitRate(
            attacker = zaku,
            defender = astral,
            weapon = weapon,
            defenderTerrain = TerrainType.SPACE,
            attackerAuraBonus = 0,
            defenderAuraBonus = 0,
            defenderStance = DefendStance.COUNTER
        )
        val hitVsForest = combatManager.calculateHitRate(
            attacker = zaku,
            defender = astral,
            weapon = weapon,
            defenderTerrain = TerrainType.FOREST,
            attackerAuraBonus = 0,
            defenderAuraBonus = 0,
            defenderStance = DefendStance.COUNTER
        )
        assertTrue("Forest evasion bonus must reduce enemy hit rate", hitVsForest < hitVsSpace)

        val dmgVsSpace = combatManager.calculateDetailedDamage(
            attacker = zaku,
            defender = kaiser,
            weapon = weapon,
            defenderTerrain = TerrainType.SPACE,
            defenderStance = DefendStance.COUNTER,
            isCritical = false
        ).finalDamage
        val dmgVsUrban = combatManager.calculateDetailedDamage(
            attacker = zaku,
            defender = kaiser,
            weapon = weapon,
            defenderTerrain = TerrainType.CITY,
            defenderStance = DefendStance.COUNTER,
            isCritical = false
        ).finalDamage
        assertTrue("Urban defense bonus must reduce incoming damage", dmgVsUrban < dmgVsSpace)
    }

    @Test
    fun combatManager_computesInitiativeOrderBasedOnPilotStatsAndMobility() {
        val kaiser = SrwRosterCatalog.createKaiserBlaze(GridPos(3, 6))
        val valkyrie = SrwRosterCatalog.createValkyrieZero(GridPos(5, 6))
        val aegis = SrwRosterCatalog.createAegisBattleship(GridPos(2, 8))

        val queue = combatManager.computeInitiativeOrder(listOf(aegis, kaiser, valkyrie))
        assertEquals(3, queue.size)

        assertEquals("ally_valkyrie", queue.first().unit.id)
        assertEquals("ally_aegis", queue.last().unit.id)
        assertTrue(queue[0].initiativeScore > queue[1].initiativeScore)
        assertTrue(queue[1].initiativeScore > queue[2].initiativeScore)

        val actedValkyrie = valkyrie.copy(hasActed = true)
        val nextReady = combatManager.getNextReadyUnitByInitiative(
            listOf(aegis, kaiser, actedValkyrie),
            Faction.ALLY
        )
        assertNotNull(nextReady)
        assertEquals("ally_kaiser", nextReady?.id)
    }

    @Test
    fun combatManager_handlesTurnPhaseTransitionsAndUpkeep() {
        val scenario = SrwRosterCatalog.getScenarios().first()
        val initialUnits = scenario.initialAllies.map {
            it.copy(
                currentHp = it.maxHp - 500,
                currentEn = it.maxEn - 30,
                hasMoved = true,
                hasActed = true,
                activeSpirits = setOf(SpiritCommand.FOCUS, SpiritCommand.VALOR)
            )
        } + scenario.initialEnemies

        assertTrue(combatManager.isPhaseComplete(initialUnits, Faction.ALLY))

        val enemyTransition = combatManager.beginEnemyPhase(turnNumber = 1, units = initialUnits)
        assertEquals(Faction.ENEMY, enemyTransition.activeFaction)
        assertEquals(TurnPhaseStage.ENEMY_PHASE, enemyTransition.phaseStage)
        assertNotNull(enemyTransition.nextActiveUnit)

        val nextTurnTransition = combatManager.beginNextPlayerTurn(
            currentTurnNumber = 1,
            units = enemyTransition.updatedUnits,
            terrainGrid = scenario.terrainGrid
        )
        assertEquals(2, nextTurnTransition.turnNumber)
        assertEquals(Faction.ALLY, nextTurnTransition.activeFaction)
        assertEquals(TurnPhaseStage.PLAYER_PHASE, nextTurnTransition.phaseStage)

        val refreshedKaiser = nextTurnTransition.updatedUnits.first { it.id == "ally_kaiser" }
        assertFalse(refreshedKaiser.hasActed)
        assertTrue("1-turn FOCUS should expire on new turn", !refreshedKaiser.activeSpirits.contains(SpiritCommand.FOCUS))
        assertTrue("Untriggered VALOR should persist across turn upkeep", refreshedKaiser.activeSpirits.contains(SpiritCommand.VALOR))
        assertTrue("Pilot Will should increase by +2 on new turn", refreshedKaiser.pilot.will > 105)
    }

    @Test
    fun combatManager_calculatesDetailedDamageAndSpiritMultipliers() {
        val grid = List(10) { List(10) { TerrainType.CITY } }
        val kaiser = SrwRosterCatalog.createKaiserBlaze(GridPos(3, 5))
        val zaku = SrwRosterCatalog.createEnemyVanguard("e1", "Alpha", GridPos(4, 5))
        val weapon = kaiser.weapons.first()

        val baseBreakdown = combatManager.calculateDetailedDamage(
            attacker = kaiser,
            defender = zaku,
            weapon = weapon,
            defenderTerrain = TerrainType.CITY,
            defenderStance = DefendStance.COUNTER,
            isCritical = false
        )

        val valorKaiser = kaiser.copy(activeSpirits = setOf(SpiritCommand.VALOR, SpiritCommand.STRIKE))
        val valorBreakdown = combatManager.calculateDetailedDamage(
            attacker = valorKaiser,
            defender = zaku,
            weapon = weapon,
            defenderTerrain = TerrainType.CITY,
            defenderStance = DefendStance.COUNTER,
            isCritical = false
        )

        assertEquals(2.0, valorBreakdown.spiritAttackMultiplier, 0.01)
        assertEquals(baseBreakdown.finalDamage * 2, valorBreakdown.finalDamage)
        assertTrue(valorBreakdown.formulaSummary.contains("SPIRIT"))

        val forecast = combatManager.buildCombatForecast(
            attacker = valorKaiser,
            defender = zaku,
            attackerWeapon = weapon,
            defenderWeapon = zaku.weapons.first(),
            defenderStance = DefendStance.COUNTER,
            terrainGrid = grid,
            allUnits = listOf(valorKaiser, zaku)
        )
        assertEquals(100, forecast.attackerHitPercent)

        val cutscene = combatManager.resolveCombatExchange(
            forecast = forecast,
            forcedPrimaryOutcome = StrikeOutcome.CRITICAL,
            isSandboxPreview = true
        )
        assertEquals(StrikeOutcome.CRITICAL, cutscene.primaryOutcome)
        assertTrue(cutscene.primaryDamage > 0)
    }

    @Test
    fun developmentSystem_handlesUnitWeaponArmorAndMagicProgression() {
        val devSystem = combatManager.developmentSystem
        var kaiser = SrwRosterCatalog.createKaiserBlaze(GridPos(3, 5))
        var credits = 50000

        // 1. UNIT DEVELOPMENT: Stat Tuning + Frame Evolution
        val hpTune = devSystem.tuneUnitStat(kaiser, "HP", credits)
        assertTrue(hpTune.success)
        kaiser = hpTune.updatedUnit
        credits = hpTune.remainingCredits
        assertEquals(1, kaiser.upgradeHp)

        // Advance Armor tuning
        repeat(3) {
            val res = devSystem.tuneUnitStat(kaiser, "ARMOR", credits)
            assertTrue(res.success)
            kaiser = res.updatedUnit
            credits = res.remainingCredits
        }

        val evoRes = devSystem.evolveUnitFrame(kaiser, credits)
        assertTrue(evoRes.success)
        kaiser = evoRes.updatedUnit
        credits = evoRes.remainingCredits
        assertEquals(2, kaiser.frameTier)
        assertTrue(kaiser.modelCode.contains("-K2"))

        // 2. WEAPON DEVELOPMENT: Individual Weapon Forging + Prototype Finisher R&D
        val firstWeaponId = kaiser.weapons.first().id
        val forgeRes = devSystem.forgeWeapon(kaiser, firstWeaponId, credits)
        assertTrue(forgeRes.success)
        kaiser = forgeRes.updatedUnit
        credits = forgeRes.remainingCredits
        assertEquals(1, kaiser.weapons.first().forgeLevel)
        assertTrue(kaiser.weapons.first().effectivePower(kaiser.upgradeWeapon, kaiser.magicLevel) > 1700)

        val protoRes = devSystem.developPrototypeWeapon(kaiser, credits)
        assertTrue("Kaiser should unlock Final Kaiser Ragnarok", protoRes.success)
        kaiser = protoRes.updatedUnit
        credits = protoRes.remainingCredits
        assertTrue(kaiser.unlockedPrototypeWeapon)
        assertTrue(kaiser.weapons.any { it.id == "w_proto_kaiser_ragnarok" })

        // 3. ARMOR DEVELOPMENT: Alloy Plating + Barrier Generator
        val armorRes = devSystem.installArmorPlating(
            kaiser,
            com.example.srw.model.ArmorPlatingTier.GUNDARIUM_COMPOSITE,
            credits
        )
        assertTrue(armorRes.success)
        kaiser = armorRes.updatedUnit
        credits = armorRes.remainingCredits
        assertEquals(com.example.srw.model.ArmorPlatingTier.GUNDARIUM_COMPOSITE, kaiser.armorPlating)

        val barrierBefore = kaiser.effectiveBarrierThreshold
        val barrierRes = devSystem.upgradeBarrierGenerator(kaiser, credits)
        assertTrue(barrierRes.success)
        kaiser = barrierRes.updatedUnit
        credits = barrierRes.remainingCredits
        assertTrue(kaiser.effectiveBarrierThreshold > barrierBefore)

        // 4. MAGIC DEVELOPMENT: Prana Conduit, Elemental Affinity & Arcane Spell Casting
        val magicRes = devSystem.upgradeMagicConduit(kaiser, credits)
        assertTrue(magicRes.success)
        kaiser = magicRes.updatedUnit
        credits = magicRes.remainingCredits
        assertEquals(2, kaiser.magicLevel)
        assertTrue("Magic Conduit Lv.2 should mount Prana Elemental Cannon", kaiser.weapons.any { it.isArcaneMagic })

        val attuneRes = devSystem.attuneElementalAffinity(
            kaiser,
            com.example.srw.model.ElementalAffinity.ASTRAL_WIND,
            credits
        )
        assertTrue(attuneRes.success)
        kaiser = attuneRes.updatedUnit
        credits = attuneRes.remainingCredits
        assertEquals(com.example.srw.model.ElementalAffinity.ASTRAL_WIND, kaiser.elementalAffinity)

        // Cast unlocked PRANA_OVERDRIVE spell in combat
        val willBefore = kaiser.pilot.will
        val castedKaiser = devSystem.castMagicSpell(kaiser, com.example.srw.model.MagicSpell.PRANA_OVERDRIVE)
        assertNotNull(castedKaiser)
        assertTrue(castedKaiser!!.pilot.will >= willBefore + 12)
        assertTrue(castedKaiser.activeSpirits.contains(SpiritCommand.VALOR))
    }

    @Test
    fun pilotMoraleSystem_fluctuatesOnKillsDamageAndEvents_andScalesAttackOutputAndSpecialWeaponAccess() {
        val moraleSystem = combatManager.pilotMoraleSystem
        var kaiser = SrwRosterCatalog.createKaiserBlaze(GridPos(3, 5))
        val astral = SrwRosterCatalog.createAstralGundam(GridPos(4, 5))
        val zaku1 = SrwRosterCatalog.createEnemyVanguard("e1", "Alpha", GridPos(3, 4))
        val zaku2 = SrwRosterCatalog.createEnemyVanguard("e2", "Beta", GridPos(5, 4))

        // At initial Will 105 (FOCUSED), Kaiser Sun-Breaker (110 Will req) is locked
        val sunBreaker = kaiser.weapons.first { it.id == "w_kaiser_sunbreaker" }
        assertFalse("Sun-Breaker requires 110 Will and should be locked at 105 Will", kaiser.canUseWeapon(sunBreaker, afterMove = false))

        // 1. Simulate Kill & Heavy Counter Damage in Combat Exchange
        val killSequence = com.example.srw.model.BattleCutsceneSequence(
            attacker = kaiser,
            defender = zaku1,
            attackerWeapon = kaiser.weapons.first(),
            defenderWeapon = zaku1.weapons.first(),
            defenderStance = DefendStance.COUNTER,
            attackerTerrain = TerrainType.PLAIN,
            defenderTerrain = TerrainType.PLAIN,
            primaryOutcome = StrikeOutcome.HIT,
            primaryDamage = zaku1.currentHp,
            primaryBarrierActivated = false,
            defenderHpAfterPrimary = 0, // Zaku1 destroyed!
            counterExecuted = false,
            counterOutcome = StrikeOutcome.MISS,
            counterDamage = 0,
            counterBarrierActivated = false,
            attackerHpAfterCounter = kaiser.currentHp,
            attackerQuote = "Take this!",
            defenderQuote = "Ejecting!",
            attackerHitRate = 100,
            defenderHitRate = 0
        )

        val moraleAfterKill = moraleSystem.processCombatExchangeMorale(
            sequence = killSequence,
            currentUnits = listOf(kaiser, astral, zaku1.copy(currentHp = 0), zaku2)
        )
        kaiser = moraleAfterKill.updatedUnits.first { it.id == "ally_kaiser" }
        val updatedAstral = moraleAfterKill.updatedUnits.first { it.id == "ally_astral" }
        val updatedZaku2 = moraleAfterKill.updatedUnits.first { it.id == "e2" }

        // Killer gained +8 Will (105 -> 113), unlocking Kaiser Sun-Breaker (110 Will)!
        assertEquals(113, kaiser.pilot.will)
        assertEquals(com.example.srw.engine.MoraleTier.HIGH_SPIRIT, moraleSystem.getMoraleTier(kaiser))
        assertTrue("Kaiser Sun-Breaker should now be unlocked at 113 Will", kaiser.canUseWeapon(sunBreaker, afterMove = false))
        assertTrue(
            "Morale change log should record newly unlocked Kaiser Sun-Breaker",
            moraleAfterKill.changes.first { it.unitId == "ally_kaiser" }.newlyUnlockedWeapons.contains("Kaiser Sun-Breaker")
        )

        // Ally bystander gained +2 Will (105 -> 107), Enemy bystander lost -2 Will (100 -> 98)
        assertEquals(107, updatedAstral.pilot.will)
        assertEquals(98, updatedZaku2.pilot.will)

        // 2. Specific Event Trigger: Enemy Psychic Jamming (-15 Ally Will) locks Sun-Breaker again
        val jammedResult = moraleSystem.triggerMoraleEvent(
            event = com.example.srw.engine.TacticalMoraleEvent.ENEMY_PSYCHIC_JAMMING,
            units = moraleAfterKill.updatedUnits,
            selectedUnitId = kaiser.id
        )
        val jammedKaiser = jammedResult.updatedUnits.first { it.id == "ally_kaiser" }
        assertEquals(98, jammedKaiser.pilot.will)
        assertFalse("Sun-Breaker should be locked again at 98 Will", jammedKaiser.canUseWeapon(sunBreaker, afterMove = false))

        // 3. Specific Event Trigger: Reactor Overdrive Surge (+20 Selected Will -> 118, then again -> 138 OVERDRIVE)
        val surge1 = moraleSystem.triggerMoraleEvent(
            event = com.example.srw.engine.TacticalMoraleEvent.REACTOR_OVERDRIVE_SURGE,
            units = jammedResult.updatedUnits,
            selectedUnitId = kaiser.id
        )
        val surge2 = moraleSystem.triggerMoraleEvent(
            event = com.example.srw.engine.TacticalMoraleEvent.REACTOR_OVERDRIVE_SURGE,
            units = surge1.updatedUnits,
            selectedUnitId = kaiser.id
        )
        val overdriveKaiser = surge2.updatedUnits.first { it.id == "ally_kaiser" }
        assertEquals(138, overdriveKaiser.pilot.will)
        assertEquals(com.example.srw.engine.MoraleTier.AWAKENED_OVERDRIVE, moraleSystem.getMoraleTier(overdriveKaiser))

        // 4. Verify Attack Output scales significantly with Pilot Morale
        val dmgAtWavering = combatManager.calculateDetailedDamage(
            attacker = kaiser.copy(pilot = kaiser.pilot.copy(will = 75)),
            defender = updatedZaku2,
            weapon = kaiser.weapons.first(),
            defenderTerrain = TerrainType.PLAIN,
            defenderStance = DefendStance.COUNTER,
            isCritical = false
        ).finalDamage

        val dmgAtOverdrive = combatManager.calculateDetailedDamage(
            attacker = overdriveKaiser,
            defender = updatedZaku2,
            weapon = kaiser.weapons.first(),
            defenderTerrain = TerrainType.PLAIN,
            defenderStance = DefendStance.COUNTER,
            isCritical = false
        ).finalDamage

        assertTrue("Overdrive Morale (138 Will) must deal substantially higher damage than Wavering Morale (75 Will)", dmgAtOverdrive > dmgAtWavering * 1.5)
    }

    @Test
    fun spiritCommandSystem_spendsSpToCastBuffsDebuffsAndHealingAbilities() {
        val spiritSystem = combatManager.spiritCommandSystem
        val kaiser = SrwRosterCatalog.createKaiserBlaze(GridPos(3, 6)).copy(
            currentHp = 4000,
            currentEn = 80
        )
        val astral = SrwRosterCatalog.createAstralGundam(GridPos(4, 6)).copy(
            currentHp = 2500,
            currentEn = 90
        )
        val aegis = SrwRosterCatalog.createAegisBattleship(GridPos(2, 8)).copy(
            pilot = SrwRosterCatalog.createAegisBattleship(GridPos(2, 8)).pilot.copy(
                maxSp = 200,
                currentSp = 200
            )
        )
        val zeph = SrwRosterCatalog.createBossEmperorZeph(GridPos(4, 2))
        val zaku = SrwRosterCatalog.createEnemyVanguard("e1", "Alpha", GridPos(3, 3))

        var units = listOf(kaiser, astral, aegis, zeph, zaku)

        // 1. Cast Enemy Debuff: ANALYZE (分析) from Astral onto Boss Zeph (-20 SP, +25% damage taken & -15% evade)
        val baseDmgVsZeph = combatManager.calculateDetailedDamage(
            attacker = kaiser,
            defender = zeph,
            weapon = kaiser.weapons.first(),
            defenderTerrain = TerrainType.PLAIN,
            defenderStance = DefendStance.COUNTER,
            isCritical = false
        ).finalDamage

        val analyzeRes = spiritSystem.castSpiritCommand(
            casterId = astral.id,
            spirit = SpiritCommand.ANALYZE,
            allUnits = units,
            targetUnitId = zeph.id
        )
        assertTrue(analyzeRes.success)
        assertEquals(20, analyzeRes.spSpent)
        units = analyzeRes.updatedUnits

        val analyzedZeph = units.first { it.id == zeph.id }
        assertTrue(analyzedZeph.activeDebuffs.contains(com.example.srw.model.SpiritDebuff.ANALYZED))

        val analyzedDmgVsZeph = combatManager.calculateDetailedDamage(
            attacker = kaiser,
            defender = analyzedZeph,
            weapon = kaiser.weapons.first(),
            defenderTerrain = TerrainType.PLAIN,
            defenderStance = DefendStance.COUNTER,
            isCritical = false
        ).finalDamage
        assertTrue("ANALYZE debuff must increase damage dealt to Boss Zeph", analyzedDmgVsZeph > baseDmgVsZeph)

        // 2. Cast Enemy Debuff: EXHAUST / DAUNT (脱力) from Kaiser onto Boss Zeph (-35 SP, -15 Will, -30 EN)
        val exhaustRes = spiritSystem.castSpiritCommand(
            casterId = kaiser.id,
            spirit = SpiritCommand.EXHAUST,
            allUnits = units,
            targetUnitId = zeph.id
        )
        assertTrue(exhaustRes.success)
        units = exhaustRes.updatedUnits
        val dauntedZeph = units.first { it.id == zeph.id }
        assertEquals(zeph.pilot.will - 15, dauntedZeph.pilot.will)
        assertEquals(zeph.currentEn - 30, dauntedZeph.currentEn)

        // 3. Cast All-Enemy Debuff: CONFUSE / DISTURB (かく乱) from Aegis (-40 SP, -35% Hit & -15 Mobility on all enemies)
        val hitBeforeConfuse = combatManager.calculateHitRate(
            attacker = zaku,
            defender = astral,
            weapon = zaku.weapons.first(),
            defenderTerrain = TerrainType.PLAIN,
            attackerAuraBonus = 0,
            defenderAuraBonus = 0,
            defenderStance = DefendStance.COUNTER
        )
        val confuseRes = spiritSystem.castSpiritCommand(
            casterId = aegis.id,
            spirit = SpiritCommand.CONFUSE,
            allUnits = units
        )
        assertTrue(confuseRes.success)
        units = confuseRes.updatedUnits
        val confusedZaku = units.first { it.id == zaku.id }
        assertTrue(confusedZaku.activeDebuffs.contains(com.example.srw.model.SpiritDebuff.CONFUSED))

        val hitAfterConfuse = combatManager.calculateHitRate(
            attacker = confusedZaku,
            defender = astral,
            weapon = confusedZaku.weapons.first(),
            defenderTerrain = TerrainType.PLAIN,
            attackerAuraBonus = 0,
            defenderAuraBonus = 0,
            defenderStance = DefendStance.COUNTER
        )
        assertTrue("CONFUSE debuff must reduce enemy accuracy", hitAfterConfuse < hitBeforeConfuse)

        // 4. Cast Targeted Ally Healing: TRUST (信頼) from Aegis onto Astral (-25 SP, +3000 HP)
        val trustRes = spiritSystem.castSpiritCommand(
            casterId = aegis.id,
            spirit = SpiritCommand.TRUST,
            allUnits = units,
            targetUnitId = astral.id
        )
        assertTrue(trustRes.success)
        units = trustRes.updatedUnits
        val healedAstral = units.first { it.id == astral.id }
        assertEquals(astral.maxHp, healedAstral.currentHp)

        // 5. Cast Squad-Wide Healing: FRIENDSHIP / BONDS (友情) from Aegis (-40 SP, +2500 HP & +35 EN to all allies)
        val friendshipRes = spiritSystem.castSpiritCommand(
            casterId = aegis.id,
            spirit = SpiritCommand.FRIENDSHIP,
            allUnits = units
        )
        assertTrue(friendshipRes.success)
        units = friendshipRes.updatedUnits
        val healedKaiser = units.first { it.id == kaiser.id }
        assertEquals(6500, healedKaiser.currentHp)
        assertEquals(115, healedKaiser.currentEn)

        // 6. Cast Targeted Ally Resupply: RESUPPLY (補給) from Aegis onto Kaiser (-45 SP, 100% EN & Ammo)
        val resupplyRes = spiritSystem.castSpiritCommand(
            casterId = aegis.id,
            spirit = SpiritCommand.RESUPPLY,
            allUnits = units,
            targetUnitId = kaiser.id
        )
        assertTrue(resupplyRes.success)
        units = resupplyRes.updatedUnits
        val resuppliedKaiser = units.first { it.id == kaiser.id }
        assertEquals(kaiser.maxEn, resuppliedKaiser.currentEn)
    }

    @Test
    fun gridTerrainSystem_appliesOffensiveAndDefensiveBonusesAcrossMountainForestAndBaseTiles() {
        val kaiser = SrwRosterCatalog.createKaiserBlaze(GridPos(0, 0)) // Super Robot
        val astral = SrwRosterCatalog.createAstralGundam(GridPos(2, 0)) // Real Robot
        val zaku = SrwRosterCatalog.createEnemyVanguard("e1", "Alpha", GridPos(2, 1))

        // 1. Mountain tile grants both defensive (+20% DEF, +15% EVADE) and high-ground offensive bonuses (+15% ATK, +12% HIT, +10% CRIT)
        val mountainProfile = terrainSystem.getProfile(TerrainType.MOUNTAIN)
        assertEquals(20, mountainProfile.defenseBonusPercent)
        assertEquals(15, mountainProfile.evasionBonusPercent)
        assertEquals(15, mountainProfile.attackBonusPercent)
        assertEquals(12, mountainProfile.hitBonusPercent)
        assertEquals(10, mountainProfile.critBonusPercent)

        // Super Robot on Mountain receives Rank S +5% DEF and +5% ATK (+25% DEF, +20% ATK)
        val kaiserMountain = terrainSystem.resolveUnitTerrainBonus(kaiser, TerrainType.MOUNTAIN)
        assertEquals(25, kaiserMountain.effectiveDefenseBonusPercent)
        assertEquals(20, kaiserMountain.effectiveAttackBonusPercent)
        assertEquals("S", kaiserMountain.adaptationGrade)

        // 2. Verify Mountain and Forest tiles reduce enemy hit rate via evasion bonuses compared to Space
        val hitInSpace = combatManager.calculateHitRate(
            attacker = zaku,
            defender = astral,
            weapon = zaku.weapons.first(),
            defenderTerrain = TerrainType.SPACE,
            attackerAuraBonus = 0,
            defenderAuraBonus = 0,
            defenderStance = DefendStance.COUNTER,
            attackerTerrain = TerrainType.PLAIN
        )
        val hitInMountain = combatManager.calculateHitRate(
            attacker = zaku,
            defender = astral,
            weapon = zaku.weapons.first(),
            defenderTerrain = TerrainType.MOUNTAIN,
            attackerAuraBonus = 0,
            defenderAuraBonus = 0,
            defenderStance = DefendStance.COUNTER,
            attackerTerrain = TerrainType.PLAIN
        )
        val hitInForest = combatManager.calculateHitRate(
            attacker = zaku,
            defender = astral,
            weapon = zaku.weapons.first(),
            defenderTerrain = TerrainType.FOREST,
            attackerAuraBonus = 0,
            defenderAuraBonus = 0,
            defenderStance = DefendStance.COUNTER,
            attackerTerrain = TerrainType.PLAIN
        )
        assertTrue("Mountain evasion bonus must lower enemy hit rate", hitInMountain < hitInSpace)
        assertTrue("Forest evasion bonus must lower enemy hit rate even further", hitInForest < hitInMountain)

        // 3. Verify Offensive Terrain Bonuses (Attack Power, Hit Rate, Critical Rate) from Mountain vs Plain
        val dmgFromPlain = combatManager.calculateDetailedDamage(
            attacker = kaiser,
            defender = zaku,
            weapon = kaiser.weapons.first(),
            defenderTerrain = TerrainType.PLAIN,
            defenderStance = DefendStance.COUNTER,
            isCritical = false,
            attackerTerrain = TerrainType.PLAIN
        ).finalDamage

        val dmgFromMountain = combatManager.calculateDetailedDamage(
            attacker = kaiser,
            defender = zaku,
            weapon = kaiser.weapons.first(),
            defenderTerrain = TerrainType.PLAIN,
            defenderStance = DefendStance.COUNTER,
            isCritical = false,
            attackerTerrain = TerrainType.MOUNTAIN
        ).finalDamage

        assertTrue("Attacking from Mountain high ground must boost damage dealt", dmgFromMountain > dmgFromPlain)

        val hitFromPlain = combatManager.calculateHitRate(
            attacker = zaku,
            defender = astral,
            weapon = zaku.weapons.first(),
            defenderTerrain = TerrainType.FOREST,
            attackerAuraBonus = 0,
            defenderAuraBonus = 0,
            defenderStance = DefendStance.COUNTER,
            attackerTerrain = TerrainType.PLAIN
        )
        val hitFromBase = combatManager.calculateHitRate(
            attacker = zaku,
            defender = astral,
            weapon = zaku.weapons.first(),
            defenderTerrain = TerrainType.FOREST,
            attackerAuraBonus = 0,
            defenderAuraBonus = 0,
            defenderStance = DefendStance.COUNTER,
            attackerTerrain = TerrainType.BASE
        )
        assertTrue("Command Base fire-control uplink must increase attacker hit rate ($hitFromBase vs $hitFromPlain)", hitFromBase > hitFromPlain)

        val critFromPlain = combatManager.calculateCritRate(astral, zaku, astral.weapons.first(), TerrainType.PLAIN)
        val critFromForest = combatManager.calculateCritRate(astral, zaku, astral.weapons.first(), TerrainType.FOREST)
        assertTrue("Forest ambush cover must boost attacker critical rate", critFromForest > critFromPlain)
    }

    @Test
    fun combatManager_buildsCombatPreviewWithProjectedDamageHitAndEvasionFromStatsAndTerrain() {
        val grid = MutableList(10) { MutableList(10) { TerrainType.PLAIN } }
        grid[3][3] = TerrainType.MOUNTAIN // Attacker high ground (+15% ATK, +12% HIT, +15% EVADE, +20% DEF)
        grid[3][4] = TerrainType.FOREST   // Defender evasion cover (+15% DEF, +20% EVADE)

        val kaiser = SrwRosterCatalog.createKaiserBlaze(GridPos(3, 3))
        val zaku = SrwRosterCatalog.createEnemyVanguard("e1", "Alpha", GridPos(4, 3))

        val counterPreview = combatManager.buildCombatForecast(
            attacker = kaiser,
            defender = zaku,
            attackerWeapon = kaiser.weapons.first(),
            defenderWeapon = zaku.weapons.first(),
            defenderStance = DefendStance.COUNTER,
            terrainGrid = grid,
            allUnits = listOf(kaiser, zaku)
        )

        assertEquals(TerrainType.MOUNTAIN, counterPreview.attackerTerrain)
        assertEquals(TerrainType.FOREST, counterPreview.defenderTerrain)
        assertEquals(100 - counterPreview.attackerHitPercent, counterPreview.defenderEvasionPercent)
        assertEquals(100 - counterPreview.defenderHitPercent, counterPreview.attackerEvasionPercent)
        assertTrue(counterPreview.attackerAccuracyRating > 0)
        assertTrue(counterPreview.defenderEvasionRating > 0)
        assertTrue(counterPreview.attackerEvasionRating > 0)
        assertEquals(20, counterPreview.attackerTerrainAttackBonus) // +15% Mountain +5% Super Robot S-Rank
        assertEquals(25, counterPreview.defenderTerrainEvadeBonus) // +20% Forest +5% Real Robot S-Rank
        assertEquals(
            (zaku.currentHp - counterPreview.attackerPredictedDamage).coerceAtLeast(0),
            counterPreview.defenderProjectedHpAfter
        )

        // Switching defender stance to EVADE boosts defender Evasion % and lowers attacker Hit %
        val evadePreview = combatManager.buildCombatForecast(
            attacker = kaiser,
            defender = zaku,
            attackerWeapon = kaiser.weapons.first(),
            defenderWeapon = zaku.weapons.first(),
            defenderStance = DefendStance.EVADE,
            terrainGrid = grid,
            allUnits = listOf(kaiser, zaku)
        )
        assertFalse(evadePreview.defenderCanCounter)
        assertEquals(100, evadePreview.attackerEvasionPercent)
        assertTrue("EVADE stance must raise defender evasion rating", evadePreview.defenderEvasionRating > counterPreview.defenderEvasionRating)

        // Switching defender stance to DEFEND reduces projected incoming damage
        val defendPreview = combatManager.buildCombatForecast(
            attacker = kaiser,
            defender = zaku,
            attackerWeapon = kaiser.weapons.first(),
            defenderWeapon = zaku.weapons.first(),
            defenderStance = DefendStance.DEFEND,
            terrainGrid = grid,
            allUnits = listOf(kaiser, zaku)
        )
        assertTrue(
            "DEFEND stance must reduce projected damage",
            defendPreview.attackerPredictedDamage < counterPreview.attackerPredictedDamage
        )
    }

    @Test
    fun combatForecast_accountsForPilotMoraleFluctuationsInProjectedDamageHitAndEvasion() {
        val grid = MutableList(10) { MutableList(10) { TerrainType.PLAIN } }
        val baseKaiser = SrwRosterCatalog.createKaiserBlaze(GridPos(3, 3))
        val zaku = SrwRosterCatalog.createEnemyVanguard("e1", "Alpha", GridPos(4, 3))
        val weapon = baseKaiser.weapons.first()

        // 1. Neutral 100 Will baseline forecast
        val neutralKaiser = baseKaiser.copy(pilot = baseKaiser.pilot.copy(will = 100))
        val baselineForecast = combatManager.buildCombatForecast(
            attacker = neutralKaiser,
            defender = zaku,
            attackerWeapon = weapon,
            defenderWeapon = zaku.weapons.first(),
            defenderStance = DefendStance.COUNTER,
            terrainGrid = grid,
            allUnits = listOf(neutralKaiser, zaku)
        )
        assertEquals(100, baselineForecast.attackerWill)
        assertEquals("FOCUSED", baselineForecast.attackerMoraleTierName)
        assertEquals(0, baselineForecast.attackerMoraleDamageDelta)
        assertEquals(0, baselineForecast.attackerMoraleOutputPercent)
        assertEquals(baselineForecast.attackerPredictedDamage, baselineForecast.attackerBaselineDamageAt100Will)

        // 2. Wavering Morale (75 Will) decreases projected damage & output percentage
        val (waveringForecast, _) = combatManager.simulateForecastMorale(
            baseForecast = baselineForecast,
            simulatedAttackerWill = 75
        )
        assertEquals(75, waveringForecast.attackerWill)
        assertEquals("WAVERING", waveringForecast.attackerMoraleTierName)
        assertTrue("Wavering morale must lower damage below 100-Will baseline", waveringForecast.attackerPredictedDamage < baselineForecast.attackerPredictedDamage)
        assertTrue("Wavering morale delta must be negative", waveringForecast.attackerMoraleDamageDelta < 0)
        assertTrue("Wavering morale output percent must be negative", waveringForecast.attackerMoraleOutputPercent < 0)

        // 3. Awakened Overdrive (135 Will) & Max Limit (155 Will) progressively increase projected damage & hit/crit
        val (overdriveForecast, _) = combatManager.simulateForecastMorale(
            baseForecast = baselineForecast,
            simulatedAttackerWill = 135
        )
        val (maxLimitForecast, _) = combatManager.simulateForecastMorale(
            baseForecast = baselineForecast,
            simulatedAttackerWill = 155
        )
        assertEquals("OVERDRIVE", overdriveForecast.attackerMoraleTierName)
        assertEquals("MAX LIMIT", maxLimitForecast.attackerMoraleTierName)
        assertTrue(overdriveForecast.attackerPredictedDamage > baselineForecast.attackerPredictedDamage)
        assertTrue(maxLimitForecast.attackerPredictedDamage > overdriveForecast.attackerPredictedDamage)
        assertTrue(overdriveForecast.attackerMoraleDamageDelta > 0)
        assertTrue(maxLimitForecast.attackerMoraleDamageDelta > overdriveForecast.attackerMoraleDamageDelta)
        assertTrue(maxLimitForecast.attackerCritPercent > baselineForecast.attackerCritPercent)

        // 4. Defender Morale fluctuations also shift incoming damage via armor scaling and counterattack output
        val (highMoraleDefenderForecast, _) = combatManager.simulateForecastMorale(
            baseForecast = baselineForecast,
            simulatedAttackerWill = 100,
            simulatedDefenderWill = 140
        )
        val (lowMoraleDefenderForecast, _) = combatManager.simulateForecastMorale(
            baseForecast = baselineForecast,
            simulatedAttackerWill = 100,
            simulatedDefenderWill = 75
        )
        assertTrue(
            "High-morale defender must take less damage and deal stronger counterattack",
            highMoraleDefenderForecast.attackerPredictedDamage < lowMoraleDefenderForecast.attackerPredictedDamage &&
                highMoraleDefenderForecast.defenderPredictedDamage > lowMoraleDefenderForecast.defenderPredictedDamage
        )
        assertTrue(highMoraleDefenderForecast.defenderMoraleArmorPercent > 0)
        assertTrue(lowMoraleDefenderForecast.defenderMoraleArmorPercent < 0)
    }

    @Test
    fun pilotProgressionSystem_awardsBattleXpUnlocksPassivesAndUpgradesSpiritCommands() {
        val progression = combatManager.pilotProgressionSystem
        val spiritSystem = combatManager.spiritCommandSystem
        var kaiser = SrwRosterCatalog.createKaiserBlaze(GridPos(3, 5)).copy(
            pilot = SrwRosterCatalog.createKaiserBlaze(GridPos(3, 5)).pilot.copy(
                xp = 700,
                totalXpEarned = 700,
                will = 120
            )
        )
        val zaku = SrwRosterCatalog.createEnemyVanguard("e1", "Alpha", GridPos(4, 5))

        // 1. Battle XP reward from a kill exchange (+55 CRIT + 90 KILL = +145 XP)
        val killSequence = BattleCutsceneSequence(
            attacker = kaiser,
            defender = zaku,
            attackerWeapon = kaiser.weapons.first(),
            defenderWeapon = zaku.weapons.first(),
            defenderStance = DefendStance.COUNTER,
            attackerTerrain = TerrainType.PLAIN,
            defenderTerrain = TerrainType.PLAIN,
            primaryOutcome = StrikeOutcome.CRITICAL,
            primaryDamage = zaku.currentHp,
            primaryBarrierActivated = false,
            defenderHpAfterPrimary = 0,
            counterExecuted = false,
            counterOutcome = StrikeOutcome.MISS,
            counterDamage = 0,
            counterBarrierActivated = false,
            attackerHpAfterCounter = kaiser.currentHp,
            attackerQuote = "Finisher!",
            defenderQuote = "No!",
            attackerHitRate = 100,
            defenderHitRate = 0
        )
        val xpUpdate = progression.processCombatXp(killSequence, listOf(kaiser, zaku.copy(currentHp = 0)))
        kaiser = xpUpdate.updatedUnits.first { it.id == kaiser.id }
        assertEquals(845, kaiser.pilot.xp)
        assertEquals(1, kaiser.pilot.kills)
        assertEquals(1, xpUpdate.grants.size)

        // 2. Unlock SP_CONSERVATION (+25 Max SP & -20% SP cost) and PRE_EMPTIVE_ACE (+15 Init, +10% Crit, +10 Hit/Eva)
        val baseSpMax = kaiser.effectiveMaxSp
        val baseFocusCost = progression.getEffectiveSpCost(kaiser.pilot, SpiritCommand.FOCUS)
        val baseInit = combatManager.calculateUnitInitiative(kaiser).initiativeScore
        val baseCrit = combatManager.calculateCritRate(kaiser, zaku, kaiser.weapons.first())

        val spOutcome = progression.unlockPassiveAbility(kaiser, PilotPassiveAbility.SP_CONSERVATION)
        assertTrue(spOutcome.success)
        kaiser = spOutcome.updatedUnit
        assertEquals(baseSpMax + 25, kaiser.effectiveMaxSp)
        assertTrue("SP_CONSERVATION must reduce Spirit SP costs", progression.getEffectiveSpCost(kaiser.pilot, SpiritCommand.FOCUS) < baseFocusCost)

        val aceOutcome = progression.unlockPassiveAbility(kaiser, PilotPassiveAbility.PRE_EMPTIVE_ACE)
        assertTrue(aceOutcome.success)
        kaiser = aceOutcome.updatedUnit
        assertEquals(baseInit + 15, combatManager.calculateUnitInitiative(kaiser).initiativeScore)
        assertEquals(baseCrit + 10, combatManager.calculateCritRate(kaiser, zaku, kaiser.weapons.first()))

        // 3. Unlock ATTACKER_INSTINCT (+12% Damage at Will >= 115) & COUNTER_MASTER (+15% Counter Damage)
        val dmgBeforeAttacker = combatManager.calculateDetailedDamage(
            attacker = kaiser,
            defender = zaku,
            weapon = kaiser.weapons.first(),
            defenderTerrain = TerrainType.PLAIN,
            defenderStance = DefendStance.COUNTER,
            isCritical = false
        ).finalDamage
        val atkOutcome = progression.unlockPassiveAbility(kaiser, PilotPassiveAbility.ATTACKER_INSTINCT)
        assertTrue(atkOutcome.success)
        kaiser = atkOutcome.updatedUnit
        val dmgWithAttacker = combatManager.calculateDetailedDamage(
            attacker = kaiser,
            defender = zaku,
            weapon = kaiser.weapons.first(),
            defenderTerrain = TerrainType.PLAIN,
            defenderStance = DefendStance.COUNTER,
            isCritical = false
        ).finalDamage
        assertTrue("ATTACKER_INSTINCT must raise damage at Will >= 115", dmgWithAttacker > dmgBeforeAttacker)

        val counterOutcome = progression.unlockPassiveAbility(kaiser, PilotPassiveAbility.COUNTER_MASTER)
        assertTrue(counterOutcome.success)
        kaiser = counterOutcome.updatedUnit
        val counterDmg = combatManager.calculateDetailedDamage(
            attacker = kaiser,
            defender = zaku,
            weapon = kaiser.weapons.first(),
            defenderTerrain = TerrainType.PLAIN,
            defenderStance = DefendStance.COUNTER,
            isCritical = false,
            isCounterattack = true
        ).finalDamage
        assertTrue("COUNTER_MASTER must deal higher damage when counterattacking", counterDmg > dmgWithAttacker)

        // 4. Unlock POTENTIAL (HP <= 50% gives +20% Armor & +20% Crit)
        val potentialOutcome = progression.unlockPassiveAbility(kaiser, PilotPassiveAbility.POTENTIAL)
        assertTrue(potentialOutcome.success)
        val healthyKaiser = potentialOutcome.updatedUnit
        val lowHpKaiser = healthyKaiser.copy(currentHp = healthyKaiser.maxHp / 2)
        assertFalse(healthyKaiser.isPotentialActive)
        assertTrue(lowHpKaiser.isPotentialActive)
        assertTrue("POTENTIAL must increase armor when HP <= 50%", lowHpKaiser.armor > healthyKaiser.armor)
        assertTrue(
            "POTENTIAL must boost crit rate when HP <= 50%",
            combatManager.calculateCritRate(lowHpKaiser, zaku, lowHpKaiser.weapons.first()) >
                combatManager.calculateCritRate(healthyKaiser, zaku, healthyKaiser.weapons.first())
        )
        // Verify XP gating rejects passive unlock when remaining XP (85) < MORALE_SURGE cost (150)
        val insufficientRes = progression.unlockPassiveAbility(healthyKaiser, PilotPassiveAbility.MORALE_SURGE)
        assertFalse(insufficientRes.success)

        // 5. Upgrade VALOR twice (Rank 0: 2.0x -> Rank 1: 2.25x -> Rank 2: 2.50x)
        var valorUnit = SrwRosterCatalog.createKaiserBlaze(GridPos(3, 5)).copy(
            pilot = SrwRosterCatalog.createKaiserBlaze(GridPos(3, 5)).pilot.copy(xp = 400),
            activeSpirits = setOf(SpiritCommand.VALOR)
        )
        assertEquals(2.0, progression.getSpiritAttackMultiplier(valorUnit), 0.01)

        val valorRank1 = progression.upgradeSpiritCommand(valorUnit, SpiritCommand.VALOR)
        assertTrue(valorRank1.success)
        valorUnit = valorRank1.updatedUnit
        assertEquals(1, valorUnit.pilot.spiritRank(SpiritCommand.VALOR))
        assertEquals(2.25, progression.getSpiritAttackMultiplier(valorUnit), 0.01)

        val valorRank2 = progression.upgradeSpiritCommand(valorUnit, SpiritCommand.VALOR)
        assertTrue(valorRank2.success)
        valorUnit = valorRank2.updatedUnit
        assertEquals(2, valorUnit.pilot.spiritRank(SpiritCommand.VALOR))
        assertEquals(2.5, progression.getSpiritAttackMultiplier(valorUnit), 0.01)

        // 6. Upgrade FOCUS to Rank 1 (+40% Hit/Eva) and verify SpiritCommandSystem casts Drive with Rank 1 (+15 Will)
        var driveUnit = SrwRosterCatalog.createKaiserBlaze(GridPos(3, 5)).copy(
            pilot = SrwRosterCatalog.createKaiserBlaze(GridPos(3, 5)).pilot.copy(xp = 200)
        )
        driveUnit = progression.upgradeSpiritCommand(driveUnit, SpiritCommand.DRIVE).updatedUnit
        val willBeforeDrive = driveUnit.pilot.will
        val driveCast = spiritSystem.castSpiritCommand(driveUnit.id, SpiritCommand.DRIVE, listOf(driveUnit))
        assertTrue(driveCast.success)
        assertEquals(willBeforeDrive + 15, driveCast.caster.pilot.will)
    }

    @Test
    fun viewModel_activatesSpiritCommandsInCombatPreviewAndPersistsPilotProgression() {
        val viewModel = SrwEngineViewModel()
        val initialState = viewModel.uiState.value
        val kaiser = initialState.units.first { it.id == "ally_kaiser" }

        // Test XP spending through ViewModel (POTENTIAL 120 XP -> 60 XP remaining; let's test upgradePilotSpirit too)
        val xpBefore = kaiser.pilot.xp
        viewModel.unlockPilotPassive(kaiser.id, PilotPassiveAbility.POTENTIAL)
        val afterUnlock = viewModel.uiState.value.units.first { it.id == kaiser.id }
        assertTrue(afterUnlock.pilot.hasPassive(PilotPassiveAbility.POTENTIAL))
        assertEquals(xpBefore - PilotPassiveAbility.POTENTIAL.xpCost, afterUnlock.pilot.xp)

        val astral = viewModel.uiState.value.units.first { it.id == "ally_astral" }
        viewModel.upgradePilotSpirit(astral.id, SpiritCommand.SOUL)
        val upgradedAstral = viewModel.uiState.value.units.first { it.id == "ally_astral" }
        assertEquals(1, upgradedAstral.pilot.spiritRank(SpiritCommand.SOUL))
        assertEquals(astral.pilot.xp - 100, upgradedAstral.pilot.xp)

        // Move Astral Gundam toward nearest enemy and enter Combat Preview with post-move 1-5 range Fin Funnels
        val nearestEnemy = viewModel.uiState.value.units
            .filter { it.faction == Faction.ENEMY && it.isAlive() }
            .minByOrNull { it.pos.distanceTo(upgradedAstral.pos) }!!

        viewModel.selectUnitFromInitiativeQueue(upgradedAstral.id)
        val funnels = upgradedAstral.weapons.first { it.id == "w_astral_funnels" }
        viewModel.startMoveCommand()
        val moveTarget = viewModel.uiState.value.reachableTiles.keys.minByOrNull { tile ->
            tile.distanceTo(nearestEnemy.pos)
        }!!
        viewModel.onTileTapped(moveTarget)
        viewModel.selectWeaponForAttack(funnels)

        val targetInRange = viewModel.uiState.value.units.firstOrNull {
            it.isAlive() && it.faction == Faction.ENEMY && it.pos in viewModel.uiState.value.weaponRangeTiles
        }
        assertNotNull("Astral Gundam should have an enemy within Fin Funnel range after advancing", targetInRange)

        viewModel.onTileTapped(targetInRange!!.pos)
        assertEquals(TacticalInputMode.SHOWING_COMBAT_FORECAST, viewModel.uiState.value.inputMode)
        val baseForecast = viewModel.uiState.value.combatForecast!!
        val spBefore = baseForecast.attacker.pilot.currentSp

        // Activate FOCUS & upgraded SOUL (Rank 1 -> 2.75x DMG) directly in Combat Preview
        viewModel.activateSpiritCommand(SpiritCommand.FOCUS)
        viewModel.activateSpiritCommand(SpiritCommand.SOUL)

        val afterSpiritState = viewModel.uiState.value
        assertEquals(TacticalInputMode.SHOWING_COMBAT_FORECAST, afterSpiritState.inputMode)
        val buffedForecast = afterSpiritState.combatForecast!!
        assertTrue(buffedForecast.attacker.activeSpirits.contains(SpiritCommand.FOCUS))
        assertTrue(buffedForecast.attacker.activeSpirits.contains(SpiritCommand.SOUL))
        assertTrue(buffedForecast.attacker.pilot.currentSp < spBefore)
        assertTrue(
            "Upgraded SOUL (2.75x) must dramatically increase projected damage in Combat Preview",
            buffedForecast.attackerPredictedDamage > baseForecast.attackerPredictedDamage * 2
        )
        assertTrue(
            "FOCUS must maintain or boost Hit Rate in Combat Preview",
            buffedForecast.attackerHitPercent >= baseForecast.attackerHitPercent
        )
    }

    @Test
    fun pilotLevelManager_calculatesXpThresholdsGrantsLevelStatBonusesAndEmitsNotifications() {
        val manager = PilotLevelManager()

        // 1. Verify progressive cumulative XP thresholds & per-tier costs
        assertEquals(0, manager.xpThresholdForLevel(1))
        assertEquals(200, manager.xpThresholdForLevel(2))
        assertEquals(450, manager.xpThresholdForLevel(3))
        assertEquals(750, manager.xpThresholdForLevel(4))
        assertEquals(1100, manager.xpThresholdForLevel(5))

        assertEquals(200, manager.xpStepCostForLevel(1))
        assertEquals(250, manager.xpStepCostForLevel(2))
        assertEquals(300, manager.xpStepCostForLevel(3))
        assertEquals(350, manager.xpStepCostForLevel(4))

        // 2. Verify boundary resolution for totalXp -> level
        assertEquals(1, manager.calculateLevelForXp(0))
        assertEquals(1, manager.calculateLevelForXp(199))
        assertEquals(2, manager.calculateLevelForXp(200))
        assertEquals(2, manager.calculateLevelForXp(449))
        assertEquals(3, manager.calculateLevelForXp(450))
        assertEquals(5, manager.calculateLevelForXp(1100))

        // 3. Initial pilot starts at Lv.1 with 180 totalXpEarned (90% of the way to Lv.2 at 200 XP)
        val initialKaiser = SrwRosterCatalog.createKaiserBlaze(GridPos(3, 5))
        val initialAstral = SrwRosterCatalog.createAstralGundam(GridPos(4, 5))
        val initialProgress = manager.getLevelProgress(initialKaiser)
        assertEquals(1, initialProgress.level)
        assertEquals(0, initialProgress.currentLevelThreshold)
        assertEquals(200, initialProgress.nextLevelThreshold)
        assertEquals(180, initialProgress.xpIntoLevel)
        assertEquals(200, initialProgress.xpNeededForNextLevel)
        assertEquals(20, initialProgress.xpRemaining)
        assertEquals(0.9f, initialProgress.progressFraction, 0.001f)

        // 4. Awarding +35 battle XP crosses 200 XP threshold -> Lv.2 + stat growth + visual notification
        val firstEval = manager.awardBattleXp(
            unitId = initialKaiser.id,
            xpGain = 35,
            units = listOf(initialKaiser, initialAstral)
        )
        assertEquals(1, firstEval.notifications.size)
        val lv2Kaiser = firstEval.updatedUnits.first { it.id == initialKaiser.id }
        assertEquals(2, lv2Kaiser.pilot.level)
        assertEquals(215, lv2Kaiser.pilot.totalXpEarned)
        assertEquals(initialKaiser.pilot.meleeStat + PilotLevelManager.STAT_GAIN_PER_LEVEL, lv2Kaiser.pilot.meleeStat)
        assertEquals(initialKaiser.pilot.rangedStat + PilotLevelManager.STAT_GAIN_PER_LEVEL, lv2Kaiser.pilot.rangedStat)
        assertEquals(initialKaiser.pilot.hitStat + PilotLevelManager.STAT_GAIN_PER_LEVEL, lv2Kaiser.pilot.hitStat)
        assertEquals(initialKaiser.pilot.evadeStat + PilotLevelManager.STAT_GAIN_PER_LEVEL, lv2Kaiser.pilot.evadeStat)
        assertEquals(initialKaiser.pilot.maxSp + PilotLevelManager.SP_GAIN_PER_LEVEL, lv2Kaiser.pilot.maxSp)

        val activeNotif = manager.uiState.value.activeNotification
        assertNotNull(activeNotif)
        assertEquals(1, activeNotif!!.previousLevel)
        assertEquals(2, activeNotif.newLevel)
        assertEquals(450, activeNotif.nextLevelThreshold)
        assertEquals(initialKaiser.pilot.name, activeNotif.pilotName)

        // 5. Re-evaluating unchanged units must not emit duplicate notifications
        val duplicateCheck = manager.evaluateRosterProgress(firstEval.updatedUnits)
        assertTrue("Re-evaluating same XP/level should not duplicate notifications", duplicateCheck.notifications.isEmpty())

        // 6. Multi-level jump (+600 XP -> 815 total XP >= 750 -> Lv.4) + second pilot leveling up queues notifications
        val multiJumpEval = manager.awardBattleXp(
            unitId = lv2Kaiser.id,
            xpGain = 600,
            units = firstEval.updatedUnits
        )
        val lv4Kaiser = multiJumpEval.updatedUnits.first { it.id == lv2Kaiser.id }
        assertEquals(4, lv4Kaiser.pilot.level)
        assertEquals(initialKaiser.pilot.meleeStat + PilotLevelManager.STAT_GAIN_PER_LEVEL * 3, lv4Kaiser.pilot.meleeStat)
        assertEquals(initialKaiser.pilot.maxSp + PilotLevelManager.SP_GAIN_PER_LEVEL * 3, lv4Kaiser.pilot.maxSp)

        val secondPilotEval = manager.awardBattleXp(
            unitId = initialAstral.id,
            xpGain = 50,
            units = multiJumpEval.updatedUnits
        )
        assertEquals(1, secondPilotEval.notifications.size)
        val lv2Astral = secondPilotEval.updatedUnits.first { it.id == initialAstral.id }
        assertEquals(2, lv2Astral.pilot.level)

        // Queue should currently hold Lv.1->2 (active) plus Lv.2->4 (Kaiser) and Lv.1->2 (Astral) pending
        assertEquals(2, manager.uiState.value.pendingNotifications.size)
        manager.dismissNotification()
        assertEquals(4, manager.uiState.value.activeNotification?.newLevel)
        assertEquals(initialKaiser.pilot.name, manager.uiState.value.activeNotification?.pilotName)

        manager.dismissNotification()
        assertEquals(2, manager.uiState.value.activeNotification?.newLevel)
        assertEquals(initialAstral.pilot.name, manager.uiState.value.activeNotification?.pilotName)

        manager.dismissNotification()
        assertNull(manager.uiState.value.activeNotification)
        assertTrue(manager.uiState.value.pendingNotifications.isEmpty())
    }

    @Test
    fun pilotMoraleSystem_computesUnitStatModifiersShiftsMobilityAndAppliesMoraleSurgeInCombat() {
        val moraleSystem = combatManager.pilotMoraleSystem
        val baseKaiser = SrwRosterCatalog.createKaiserBlaze(GridPos(3, 5))
        val waveringKaiser = baseKaiser.copy(pilot = baseKaiser.pilot.copy(will = 75))
        val focusedKaiser = baseKaiser.copy(pilot = baseKaiser.pilot.copy(will = 100))
        val overdriveKaiser = baseKaiser.copy(pilot = baseKaiser.pilot.copy(will = 138))
        val maxLimitKaiser = baseKaiser.copy(pilot = baseKaiser.pilot.copy(will = 155))

        // 1. Unit Stat Modifiers scale Armor, Mobility, Hit/Evade, Crit, Attack %, and Initiative
        val waveringMods = moraleSystem.computeUnitStatModifiers(waveringKaiser)
        val focusedMods = moraleSystem.computeUnitStatModifiers(focusedKaiser)
        val overdriveMods = moraleSystem.computeUnitStatModifiers(overdriveKaiser)
        val maxMods = moraleSystem.computeUnitStatModifiers(maxLimitKaiser)

        assertEquals(-18, waveringMods.attackOutputPercent)
        assertEquals(-15, waveringMods.defenseModifierPercent)
        assertEquals(-12, waveringMods.mobilityDelta)
        assertEquals(baseKaiser.mobility - 12, waveringMods.effectiveMobility)

        assertEquals(0, focusedMods.attackOutputPercent)
        assertEquals(0, focusedMods.mobilityDelta)
        assertEquals(baseKaiser.mobility, focusedMods.effectiveMobility)

        assertEquals(20, overdriveMods.attackOutputPercent)
        assertEquals(15, overdriveMods.defenseModifierPercent)
        assertEquals(16, overdriveMods.mobilityDelta)
        assertEquals(baseKaiser.mobility + 16, overdriveMods.effectiveMobility)
        assertTrue(overdriveMods.effectiveArmor > focusedMods.effectiveArmor)
        assertTrue(maxMods.effectiveArmor > overdriveMods.effectiveArmor)
        assertEquals(24, maxMods.mobilityDelta)

        // 2. Initiative & Evasion Rating dynamically respond to Morale Mobility & Stat Modifiers
        val initWavering = combatManager.calculateUnitInitiative(waveringKaiser).initiativeScore
        val initOverdrive = combatManager.calculateUnitInitiative(overdriveKaiser).initiativeScore
        assertTrue("Overdrive Will must increase initiative score via Will & Mobility", initOverdrive > initWavering)

        val evadeWavering = combatManager.calculateDefenseEvasionRating(
            defender = waveringKaiser,
            defenderTerrain = TerrainType.PLAIN,
            defenderAuraBonus = 0,
            defenderStance = DefendStance.COUNTER
        )
        val evadeOverdrive = combatManager.calculateDefenseEvasionRating(
            defender = overdriveKaiser,
            defenderTerrain = TerrainType.PLAIN,
            defenderAuraBonus = 0,
            defenderStance = DefendStance.COUNTER
        )
        assertTrue("Overdrive Will must raise Defense Evasion Rating via effectiveMobility & tier evade bonus", evadeOverdrive > evadeWavering)

        // 3. Turn upkeep recovers +3 Will when wavering (<100), +2 when >= 100
        assertEquals(78, moraleSystem.computeTurnUpkeepWill(waveringKaiser))
        assertEquals(102, moraleSystem.computeTurnUpkeepWill(focusedKaiser))

        // 4. Heavy Impact (+2) & MORALE_SURGE (+3) recorded inside PilotMoraleSystem without double-counting in XP
        val surgeKaiser = baseKaiser.copy(
            pilot = baseKaiser.pilot.copy(
                will = 104,
                passiveAbilities = setOf(PilotPassiveAbility.MORALE_SURGE)
            )
        )
        val toughEnemy = SrwRosterCatalog.createEnemyGravDestroyer("e_heavy", "Heavy", GridPos(4, 5))
        val heavyDamage = (toughEnemy.maxHp * 0.50).toInt()
        val heavyHitSequence = BattleCutsceneSequence(
            attacker = surgeKaiser,
            defender = toughEnemy,
            attackerWeapon = surgeKaiser.weapons.first(),
            defenderWeapon = toughEnemy.weapons.first(),
            defenderStance = DefendStance.DEFEND,
            attackerTerrain = TerrainType.PLAIN,
            defenderTerrain = TerrainType.PLAIN,
            primaryOutcome = StrikeOutcome.HIT, // +1 Hit
            primaryDamage = heavyDamage, // +2 Heavy Impact (>= 45% target maxHp)
            primaryBarrierActivated = false,
            defenderHpAfterPrimary = toughEnemy.maxHp - heavyDamage,
            counterExecuted = false,
            counterOutcome = StrikeOutcome.MISS,
            counterDamage = 0,
            counterBarrierActivated = false,
            attackerHpAfterCounter = surgeKaiser.currentHp,
            attackerQuote = "Breaker!",
            defenderQuote = "Gah!",
            attackerHitRate = 95,
            defenderHitRate = 0
        )

        val moraleRes = moraleSystem.processCombatExchangeMorale(
            sequence = heavyHitSequence,
            currentUnits = listOf(surgeKaiser, toughEnemy.copy(currentHp = toughEnemy.maxHp - heavyDamage))
        )
        val afterMoraleKaiser = moraleRes.updatedUnits.first { it.id == surgeKaiser.id }
        // +1 (Hit) +2 (Heavy Impact) +3 (MORALE_SURGE) = +6 Will (104 -> 110), unlocking Kaiser Sun-Breaker!
        assertEquals(110, afterMoraleKaiser.pilot.will)
        val kaiserChange = moraleRes.changes.first { it.unitId == surgeKaiser.id }
        assertTrue(kaiserChange.newlyUnlockedWeapons.contains("Kaiser Sun-Breaker"))

        val xpRes = combatManager.pilotProgressionSystem.processCombatXp(heavyHitSequence, moraleRes.updatedUnits)
        val afterXpKaiser = xpRes.updatedUnits.first { it.id == surgeKaiser.id }
        assertEquals("XP processing should not double-apply MORALE_SURGE Will", 110, afterXpKaiser.pilot.will)
    }

    @Test
    fun spiritCommandsViewModel_exposesPredefinedEffectsActivatesAbilitiesAndAggregatesStatModifiers() {
        val service = SpiritCommandsViewModel()
        val state0 = service.uiState.value

        // 1. Predefined catalog covers every SpiritCommand with structured stat modifiers
        assertEquals(SpiritCommand.entries.size, state0.predefinedEffects.size)
        val baseValor = service.getPredefinedEffect(SpiritCommand.VALOR, rank = 0)
        assertEquals(2.0, baseValor.attackMultiplier, 0.01)
        val maxValor = service.getPredefinedEffect(SpiritCommand.VALOR, rank = 2)
        assertEquals(2.5, maxValor.attackMultiplier, 0.01)
        assertEquals(10, maxValor.hitBonusPercent)

        val focusRank1 = service.getPredefinedEffect(SpiritCommand.FOCUS, rank = 1)
        assertEquals(40, focusRank1.hitBonusPercent)
        assertEquals(40, focusRank1.evadeBonusPercent)
        assertEquals(10, focusRank1.critBonusPercent)

        // 2. Sync roster & filter pilot abilities by SpiritCategory
        val kaiser = SrwRosterCatalog.createKaiserBlaze(GridPos(3, 5)).copy(
            pilot = SrwRosterCatalog.createKaiserBlaze(GridPos(3, 5)).pilot.copy(currentSp = 120)
        )
        val enemy = SrwRosterCatalog.createEnemyVanguard("e_spirit", "Target", GridPos(5, 5))
        val roster = listOf(kaiser, enemy)
        service.syncRoster(roster)

        val buffsOnly = service.getAvailableEffectsForUnit(kaiser, roster, SpiritCategory.BUFF)
        assertTrue(buffsOnly.isNotEmpty())
        assertTrue(buffsOnly.all { it.spirit.category == SpiritCategory.BUFF })

        // 3. Activate FOCUS (+30% Hit/Eva, +20 Init) & VALOR (2.0x ATK) through ViewModel service
        val focusRes = service.activateSpiritCommand(kaiser.id, SpiritCommand.FOCUS, roster)
        assertTrue(focusRes.success)
        val valorRes = service.activateSpiritCommand(kaiser.id, SpiritCommand.VALOR, focusRes.updatedUnits)
        assertTrue(valorRes.success)

        val mods = service.uiState.value.modifiersByUnitId[kaiser.id]
        assertNotNull(mods)
        assertEquals(2.0, mods!!.attackMultiplier, 0.01)
        assertEquals(30, mods.hitBonusPercent)
        assertEquals(30, mods.evadeBonusPercent)
        assertEquals(20, mods.initiativeBonus)
        assertTrue(mods.hasCombatModifiers)
        assertTrue(mods.activeEffectTags.any { it.contains("VALOR") })
        assertTrue(mods.activeEffectTags.any { it.contains("FOCUS") })

        // 4. Activation banner and history recorded cleanly
        assertEquals(SpiritCommand.VALOR, service.uiState.value.lastActivationBanner?.spirit)
        assertEquals(2, service.uiState.value.activationHistory.size)
        service.dismissActivationBanner()
        assertNull(service.uiState.value.lastActivationBanner)

        // 5. Cast ANALYZE debuff on enemy & verify enemy combat modifiers reflect +25% target dmg / -15% evade
        val analyzeRes = service.activateSpiritCommand(
            casterId = kaiser.id,
            spirit = SpiritCommand.ANALYZE,
            allUnits = valorRes.updatedUnits,
            targetUnitId = enemy.id
        )
        assertTrue(analyzeRes.success)
        val enemyMods = service.uiState.value.modifiersByUnitId[enemy.id]
        assertNotNull(enemyMods)
        assertEquals(1.25, enemyMods!!.incomingDamageMultiplier, 0.01)
        assertEquals(-15, enemyMods.evadeBonusPercent)
    }

    @Test
    fun terrainMapUtility_definesForestWaterCityTilesAndAppliesPilotDodgeAndDefenseModifiers() {
        // 1. Verify Forest, Water, and City tile definitions & alias resolution
        assertEquals(TerrainType.FOREST, TerrainMapUtility.FOREST.terrainType)
        assertEquals(20, TerrainMapUtility.FOREST.dodgeModifierPercent)
        assertEquals(15, TerrainMapUtility.FOREST.defenseModifierPercent)
        assertTrue(TerrainMapUtility.FOREST.providesCover)

        assertEquals(TerrainType.SEA, TerrainMapUtility.WATER.terrainType)
        assertEquals(0, TerrainMapUtility.WATER.dodgeModifierPercent)
        assertEquals(10, TerrainMapUtility.WATER.defenseModifierPercent)
        assertEquals(TerrainMapUtility.WATER, TerrainMapUtility.getTileDefinitionByName("water"))
        assertEquals(TerrainMapUtility.WATER, TerrainMapUtility.getTileDefinitionByName("sea"))
        assertEquals(TerrainMapUtility.CITY, TerrainMapUtility.getTileDefinitionByName("city"))
        assertEquals(TerrainMapUtility.FOREST, TerrainMapUtility.getTileDefinitionByName("forest"))

        // 2. Build tactical tile map from ASCII blueprint & verify coordinate queries
        val tileMap = TerrainMapUtility.fromAsciiBlueprint(
            listOf(
                "P F C W",
                "M B . F"
            )
        )
        assertEquals(4, tileMap.width)
        assertEquals(2, tileMap.height)
        assertTrue(tileMap.isInBounds(GridPos(3, 1)))
        assertFalse(tileMap.isInBounds(GridPos(4, 0)))
        assertEquals(TerrainType.FOREST, tileMap.tileAt(GridPos(1, 0)))
        assertEquals(TerrainType.CITY, tileMap.tileAt(GridPos(2, 0)))
        assertEquals(TerrainType.SEA, tileMap.tileAt(GridPos(3, 0)))
        assertEquals(TerrainType.BASE, tileMap.tileAt(GridPos(1, 1)))
        assertEquals(listOf(GridPos(1, 0), GridPos(3, 1)), tileMap.positionsOf(TerrainType.FOREST))

        // Out-of-bounds falls back cleanly to Plain, and immutable withTile updates a single cell
        assertEquals(TerrainType.PLAIN, tileMap.tileAt(GridPos(99, 99)))
        val updatedMap = tileMap.withTile(GridPos(0, 0), TerrainType.SEA)
        assertEquals(TerrainType.SEA, updatedMap.tileAt(GridPos(0, 0)))
        assertEquals(TerrainType.PLAIN, tileMap.tileAt(GridPos(0, 0)))

        // 3. Apply tile dodge & defense modifiers directly to a Pilot and to occupying MechaUnits
        val astral = SrwRosterCatalog.createAstralGundam(GridPos(1, 0)) // Real Robot on Forest
        val kaiser = SrwRosterCatalog.createKaiserBlaze(GridPos(2, 0)) // Super Robot on City
        val enemy = SrwRosterCatalog.createEnemyVanguard("e_water", "Water Unit", GridPos(3, 0)) // on Water

        val pilotOnlyForest = TerrainMapUtility.applyModifiersToPilot(
            pilot = astral.pilot,
            pos = GridPos(1, 0),
            tileMap = tileMap
        )
        assertEquals(20, pilotOnlyForest.effectiveDodgeModifierPercent)
        assertEquals(15, pilotOnlyForest.effectiveDefenseModifierPercent)
        assertEquals(astral.pilot.evadeStat + 20, pilotOnlyForest.effectivePilotEvadeStat)
        assertEquals("A", pilotOnlyForest.adaptationGrade)

        // Real Robot occupying Forest gets +5% archetype dodge adaptation (20 + 5 = +25% Dodge, +15% Def)
        val astralForestMod = TerrainMapUtility.applyOccupiedTileModifiers(astral, tileMap)
        assertEquals(25, astralForestMod.effectiveDodgeModifierPercent)
        assertEquals(15, astralForestMod.effectiveDefenseModifierPercent)
        assertEquals(astral.pilot.evadeStat + 25, astralForestMod.effectivePilotEvadeStat)
        assertEquals("S", astralForestMod.adaptationGrade)
        assertTrue(astralForestMod.effectiveArmor!! > astral.armor)

        // Super Robot occupying City gets +5% archetype defense adaptation (+15% Dodge, +20% Def)
        val kaiserCityMod = TerrainMapUtility.applyOccupiedTileModifiers(kaiser, tileMap)
        assertEquals(15, kaiserCityMod.effectiveDodgeModifierPercent)
        assertEquals(20, kaiserCityMod.effectiveDefenseModifierPercent)
        assertEquals(1.20, kaiserCityMod.defenseDamageMultiplier, 0.001)
        assertEquals("S", kaiserCityMod.adaptationGrade)

        // Unit occupying Water gets +10% Def, 0% Dodge
        val waterMod = TerrainMapUtility.applyOccupiedTileModifiers(enemy, tileMap)
        assertEquals(TerrainType.SEA, waterMod.tile.terrainType)
        assertEquals(0, waterMod.effectiveDodgeModifierPercent)
        assertEquals(10, waterMod.effectiveDefenseModifierPercent)
        assertEquals(enemy.pilot.evadeStat, waterMod.effectivePilotEvadeStat)

        // 4. Roster evaluation & best cover tile selection
        val rosterMods = TerrainMapUtility.applyModifiersToRoster(listOf(astral, kaiser, enemy), tileMap)
        assertEquals(3, rosterMods.size)
        assertEquals(25, rosterMods[astral.id]?.effectiveDodgeModifierPercent)

        val bestForAstral = TerrainMapUtility.findBestCoverTile(
            unit = astral,
            candidatePositions = listOf(GridPos(0, 0), GridPos(1, 0), GridPos(3, 0)), // Plain, Forest, Water
            tileMap = tileMap
        )
        assertNotNull(bestForAstral)
        assertEquals(GridPos(1, 0), bestForAstral!!.pos)
        assertEquals(TerrainType.FOREST, bestForAstral.tile.terrainType)
    }

    @Test
    fun willMoraleViewModel_tracksConfidenceIncreasesWithKillsAndDamageAndAppliesThresholdStatMultipliers() {
        val service = WillMoraleViewModel()
        assertEquals(6, service.uiState.value.thresholds.size)

        // 1. Sync roster & verify initial FOCUSED (100 Will) baseline multipliers
        val kaiser0 = SrwRosterCatalog.createKaiserBlaze(GridPos(3, 5)).let {
            it.copy(pilot = it.pilot.copy(will = 100))
        }
        val astral0 = SrwRosterCatalog.createAstralGundam(GridPos(4, 5)).let {
            it.copy(pilot = it.pilot.copy(will = 100))
        }
        val enemy0 = SrwRosterCatalog.createEnemyVanguard("e_conf", "Target", GridPos(5, 5))
        var roster = listOf(kaiser0, astral0, enemy0)
        service.syncRoster(roster)

        val initialProfile = service.uiState.value.profilesByUnitId[kaiser0.id]!!
        assertEquals(100, initialProfile.confidenceLevel)
        assertEquals(MoraleTier.FOCUSED, initialProfile.tier)
        assertEquals(1.00, initialProfile.statMultipliers.attackMultiplier, 0.001)
        assertEquals(1.00, initialProfile.statMultipliers.defenseMultiplier, 0.001)
        assertEquals(10, initialProfile.pointsToNextThreshold) // 110 for HIGH_SPIRIT
        assertEquals(0, initialProfile.totalDamageDealt)
        assertEquals(0, initialProfile.totalKillsTracked)

        // 2. Record heavy critical damage dealt (3,600 DMG vs 6,000 HP -> +3 scaled +2 heavy +2 crit = +7 Will -> 107)
        val dmgRes = service.recordDamageDealt(
            unitId = kaiser0.id,
            damageDealt = 3600,
            units = roster,
            targetMaxHp = 6000,
            isCritical = true
        )
        roster = dmgRes.updatedUnits
        val afterDmgProfile = service.uiState.value.profilesByUnitId[kaiser0.id]!!
        assertEquals(107, afterDmgProfile.confidenceLevel)
        assertEquals(3600, afterDmgProfile.totalDamageDealt)
        assertEquals(MoraleTier.FOCUSED, afterDmgProfile.tier)
        assertEquals(3, afterDmgProfile.pointsToNextThreshold)

        // 3. Record standard kill (+8 Will -> 115, crossing into HIGH_SPIRIT 110+ threshold + +2 ally broadcast)
        val killRes = service.recordKill(
            unitId = kaiser0.id,
            units = roster,
            isBossKill = false,
            damageDealt = 2400
        )
        roster = killRes.updatedUnits
        val afterKillProfile = service.uiState.value.profilesByUnitId[kaiser0.id]!!
        val allyBroadcastProfile = service.uiState.value.profilesByUnitId[astral0.id]!!

        assertEquals(115, afterKillProfile.confidenceLevel)
        assertEquals(1, afterKillProfile.totalKillsTracked)
        assertEquals(6000, afterKillProfile.totalDamageDealt)
        assertEquals(MoraleTier.HIGH_SPIRIT, afterKillProfile.tier)
        assertEquals(1.10, afterKillProfile.statMultipliers.attackMultiplier, 0.001)
        assertEquals(1.08, afterKillProfile.statMultipliers.defenseMultiplier, 0.001)
        assertEquals(1.08, afterKillProfile.statMultipliers.accuracyMultiplier, 0.001)
        assertEquals(1.07, afterKillProfile.statMultipliers.mobilityMultiplier, 0.001)
        assertTrue(afterKillProfile.statMultipliers.effectiveMeleeStat > kaiser0.pilot.meleeStat)
        assertTrue(afterKillProfile.statMultipliers.effectiveArmor > kaiser0.armor)
        assertEquals(102, allyBroadcastProfile.confidenceLevel)

        // Threshold crossing notification emitted and dismissible
        val notif = service.uiState.value.lastThresholdNotification
        assertNotNull(notif)
        assertTrue(notif!!.crossedThreshold)
        assertEquals(MoraleTier.FOCUSED, notif.previousTier)
        assertEquals(MoraleTier.HIGH_SPIRIT, notif.newTier)
        service.dismissThresholdNotification()
        assertNull(service.uiState.value.lastThresholdNotification)

        // 4. Record Boss Kill with heavy finisher (+12 Boss +2 Overkill = +14 -> 129) then +2 damage -> 131 OVERDRIVE
        val bossKillRes = service.recordKill(
            unitId = kaiser0.id,
            units = roster,
            isBossKill = true,
            damageDealt = 4800
        )
        roster = bossKillRes.updatedUnits
        val overdriveRes = service.recordDamageDealt(
            unitId = kaiser0.id,
            damageDealt = 2400,
            units = roster
        )
        roster = overdriveRes.updatedUnits
        val overdriveProfile = service.uiState.value.profilesByUnitId[kaiser0.id]!!
        assertEquals(131, overdriveProfile.confidenceLevel)
        assertEquals(2, overdriveProfile.totalKillsTracked)
        assertEquals(MoraleTier.AWAKENED_OVERDRIVE, overdriveProfile.tier)
        assertEquals(1.20, overdriveProfile.statMultipliers.attackMultiplier, 0.001)
        assertEquals(1.15, overdriveProfile.statMultipliers.defenseMultiplier, 0.001)
        assertEquals(1.14, overdriveProfile.statMultipliers.evasionMultiplier, 0.001)

        // 5. Verify WAVERING (<85) penalty multipliers & MAX_TRANSCENDENT (150+) cap multipliers
        val waveringMult = service.computeThresholdStatMultipliers(kaiser0, confidenceOverride = 75)
        val maxMult = service.computeThresholdStatMultipliers(kaiser0, confidenceOverride = 160)
        assertEquals(0.82, waveringMult.attackMultiplier, 0.001)
        assertEquals(0.85, waveringMult.defenseMultiplier, 0.001)
        assertTrue(waveringMult.effectiveArmor < kaiser0.armor)
        assertEquals(1.32, maxMult.attackMultiplier, 0.001)
        assertEquals(1.22, maxMult.defenseMultiplier, 0.001)
        assertEquals(1.20, maxMult.mobilityMultiplier, 0.001)
        assertTrue(maxMult.effectiveMobility > kaiser0.mobility)
    }
}






