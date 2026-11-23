package com.example.srw.data

import androidx.compose.ui.graphics.Color
import com.example.srw.model.*

object SrwRosterCatalog {

    fun createKaiserBlaze(pos: GridPos): MechaUnit = MechaUnit(
        id = "ally_kaiser",
        name = "GX-99 Kaiser Blaze",
        modelCode = "SRX-01",
        faction = Faction.ALLY,
        archetype = MechaArchetype.SUPER_ROBOT,
        visualRig = MechaVisualRig.KAISER_BLAZE,
        canFly = true,
        baseMaxHp = 7800,
        currentHp = 7800,
        baseMaxEn = 240,
        currentEn = 240,
        baseArmor = 1750,
        baseMobility = 105,
        baseMoveRange = 4,
        pos = pos,
        barrierName = "Photon Barrier",
        barrierThreshold = 750,
        elementalAffinity = ElementalAffinity.SOLAR_FIRE,
        pilot = Pilot(
            id = "pilot_kaito",
            name = "Kaito Ryuga",
            callsign = "SOLAR-1",
            meleeStat = 178,
            rangedStat = 148,
            hitStat = 152,
            evadeStat = 128,
            will = 105,
            maxSp = 110,
            currentSp = 110,
            spirits = listOf(
                SpiritCommand.VALOR,
                SpiritCommand.STRIKE,
                SpiritCommand.IRON_WALL,
                SpiritCommand.DRIVE,
                SpiritCommand.EXHAUST,
                SpiritCommand.TRUST
            ),
            attackQuotes = listOf(
                "Solar reactor at critical output! Take this!",
                "Burn through the darkness, Kaiser Blaze!",
                "My armor won't flinch at scratches like that!"
            ),
            finisherQuote = "ROAR, SOLAR CORE! KAISER... SUN-BREAKER!!",
            dodgeQuote = "Too slow! Even a Super Robot can read that trajectory!",
            damageQuote = "Guh! Photon Armor holding at 85%—keep pushing!",
            primaryColor = Color(0xFFFF3D00),
            visorColor = Color(0xFFFFEA00)
        ),
        weapons = listOf(
            Weapon(
                id = "w_kaiser_tomahawk",
                name = "Kaiser Tomahawk Boomerang",
                basePower = 2600,
                minRange = 1,
                maxRange = 3,
                enCost = 10,
                maxAmmo = 0,
                currentAmmo = 0,
                willReq = 100,
                isPostMove = true,
                critBonus = 15,
                hitBonus = 20,
                animStyle = AnimationStyle.BEAM_SABER_BLITZ,
                isMelee = true
            ),
            Weapon(
                id = "w_kaiser_magma",
                name = "Breast Magma Flare",
                basePower = 3500,
                minRange = 1,
                maxRange = 4,
                enCost = 30,
                maxAmmo = 0,
                currentAmmo = 0,
                willReq = 100,
                isPostMove = true,
                critBonus = 20,
                hitBonus = 18,
                animStyle = AnimationStyle.HYPER_BUSTER_RIFLE,
                isMelee = false
            ),
            Weapon(
                id = "w_kaiser_sunbreaker",
                name = "Kaiser Sun-Breaker",
                basePower = 5200,
                minRange = 1,
                maxRange = 2,
                enCost = 55,
                maxAmmo = 0,
                currentAmmo = 0,
                willReq = 110,
                isPostMove = true,
                critBonus = 35,
                hitBonus = 28,
                animStyle = AnimationStyle.KAISER_NOVA_BREAKER,
                isMelee = true
            )
        )
    )

    fun createAstralGundam(pos: GridPos): MechaUnit = MechaUnit(
        id = "ally_astral",
        name = "RX-93V Astral Frame",
        modelCode = "NT-09",
        faction = Faction.ALLY,
        archetype = MechaArchetype.REAL_ROBOT,
        visualRig = MechaVisualRig.ASTRAL_GUNDAM,
        canFly = true,
        baseMaxHp = 5100,
        currentHp = 5100,
        baseMaxEn = 210,
        currentEn = 210,
        baseArmor = 1280,
        baseMobility = 158,
        baseMoveRange = 5,
        pos = pos,
        barrierName = "Psycho-Field",
        barrierThreshold = 500,
        elementalAffinity = ElementalAffinity.CHRONO_LIGHTNING,
        pilot = Pilot(
            id = "pilot_elena",
            name = "Elena Vance",
            callsign = "ASTRA-7",
            meleeStat = 156,
            rangedStat = 184,
            hitStat = 182,
            evadeStat = 176,
            will = 105,
            maxSp = 105,
            currentSp = 105,
            spirits = listOf(
                SpiritCommand.FOCUS,
                SpiritCommand.ALERT,
                SpiritCommand.SOUL,
                SpiritCommand.ANALYZE,
                SpiritCommand.ROUSE,
                SpiritCommand.STRIKE
            ),
            attackQuotes = listOf(
                "Psycho-frame resonance locked. Target in sight!",
                "I can see the ripples in your movement path!",
                "Don't underestimate a Real Robot's precision!"
            ),
            finisherQuote = "FIN FUNNELS, DEPLOY ALL-RANGE BARRAGE! SHOOT THROUGH!",
            dodgeQuote = "I already sensed your firing vector three seconds ago.",
            damageQuote = "Shield integrity dropping! Adjusting thruster vectors!",
            primaryColor = Color(0xFF00E5FF),
            visorColor = Color(0xFF00E676)
        ),
        weapons = listOf(
            Weapon(
                id = "w_astral_saber",
                name = "Twin Plasma Saber",
                basePower = 2400,
                minRange = 1,
                maxRange = 1,
                enCost = 0,
                maxAmmo = 0,
                currentAmmo = 0,
                willReq = 100,
                isPostMove = true,
                critBonus = 25,
                hitBonus = 30,
                animStyle = AnimationStyle.BEAM_SABER_BLITZ,
                isMelee = true
            ),
            Weapon(
                id = "w_astral_rifle",
                name = "High-Output Beam Rifle",
                basePower = 2900,
                minRange = 1,
                maxRange = 4,
                enCost = 10,
                maxAmmo = 8,
                currentAmmo = 8,
                willReq = 100,
                isPostMove = true,
                critBonus = 20,
                hitBonus = 28,
                animStyle = AnimationStyle.HYPER_BUSTER_RIFLE,
                isMelee = false
            ),
            Weapon(
                id = "w_astral_funnels",
                name = "All-Range Fin Funnels",
                basePower = 4350,
                minRange = 1,
                maxRange = 5,
                enCost = 35,
                maxAmmo = 0,
                currentAmmo = 0,
                willReq = 105,
                isPostMove = true,
                critBonus = 35,
                hitBonus = 38,
                animStyle = AnimationStyle.FIN_FUNNEL_SWARM,
                isMelee = false
            ),
            Weapon(
                id = "w_astral_buster",
                name = "Hyper Mega Buster Cannon",
                basePower = 4900,
                minRange = 2,
                maxRange = 6,
                enCost = 50,
                maxAmmo = 0,
                currentAmmo = 0,
                willReq = 115,
                isPostMove = false,
                critBonus = 25,
                hitBonus = 25,
                animStyle = AnimationStyle.HYPER_BUSTER_RIFLE,
                isMelee = false
            )
        )
    )

    fun createValkyrieZero(pos: GridPos): MechaUnit = MechaUnit(
        id = "ally_valkyrie",
        name = "VF-0S Valkyrie Zero",
        modelCode = "VFX-00",
        faction = Faction.ALLY,
        archetype = MechaArchetype.REAL_ROBOT,
        visualRig = MechaVisualRig.VALKYRIE_ZERO,
        canFly = true,
        baseMaxHp = 4600,
        currentHp = 4600,
        baseMaxEn = 190,
        currentEn = 190,
        baseArmor = 1150,
        baseMobility = 172,
        baseMoveRange = 6,
        pos = pos,
        elementalAffinity = ElementalAffinity.ASTRAL_WIND,
        pilot = Pilot(
            id = "pilot_shin",
            name = "Shin Kazama",
            callsign = "SKULL-1",
            meleeStat = 152,
            rangedStat = 174,
            hitStat = 178,
            evadeStat = 186,
            will = 100,
            maxSp = 95,
            currentSp = 95,
            spirits = listOf(
                SpiritCommand.ACCEL,
                SpiritCommand.FOCUS,
                SpiritCommand.ALERT,
                SpiritCommand.ANALYZE,
                SpiritCommand.CONFUSE,
                SpiritCommand.VALOR
            ),
            attackQuotes = listOf(
                "Skull Leader entering attack vector! Breaking sound barrier!",
                "Try locking onto Mach 4 variable geometry!",
                "Pinpoint barrier aligned on gunpod!"
            ),
            finisherQuote = "ALL MISSILE PODS OPEN! ITANO MICRO-MISSILE CIRCUS!!",
            dodgeQuote = "You're shooting at my afterimage!",
            damageQuote = "Stabilizer clipped! Switching to Battroid thruster control!",
            primaryColor = Color(0xFFFFC400),
            visorColor = Color(0xFF00E5FF)
        ),
        weapons = listOf(
            Weapon(
                id = "w_valk_knife",
                name = "Pinpoint Assault Knife",
                basePower = 2300,
                minRange = 1,
                maxRange = 1,
                enCost = 0,
                maxAmmo = 0,
                currentAmmo = 0,
                willReq = 100,
                isPostMove = true,
                critBonus = 30,
                hitBonus = 32,
                animStyle = AnimationStyle.BEAM_SABER_BLITZ,
                isMelee = true
            ),
            Weapon(
                id = "w_valk_gunpod",
                name = "GU-11 Rapid Gunpod",
                basePower = 2750,
                minRange = 1,
                maxRange = 4,
                enCost = 0,
                maxAmmo = 10,
                currentAmmo = 10,
                willReq = 100,
                isPostMove = true,
                critBonus = 20,
                hitBonus = 28,
                animStyle = AnimationStyle.MACROSS_MISSILE_STORM,
                isMelee = false
            ),
            Weapon(
                id = "w_valk_circus",
                name = "Micro-Missile Storm",
                basePower = 4100,
                minRange = 1,
                maxRange = 5,
                enCost = 30,
                maxAmmo = 4,
                currentAmmo = 4,
                willReq = 105,
                isPostMove = true,
                critBonus = 30,
                hitBonus = 35,
                animStyle = AnimationStyle.MACROSS_MISSILE_STORM,
                isMelee = false
            )
        )
    )

    fun createAegisBattleship(pos: GridPos): MechaUnit = MechaUnit(
        id = "ally_aegis",
        name = "SDF-01 Aegis Ark",
        modelCode = "ARK-01",
        faction = Faction.ALLY,
        archetype = MechaArchetype.COMMAND_SHIP,
        visualRig = MechaVisualRig.AEGIS_BATTLESHIP,
        canFly = true,
        baseMaxHp = 9600,
        currentHp = 9600,
        baseMaxEn = 280,
        currentEn = 280,
        baseArmor = 1850,
        baseMobility = 95,
        baseMoveRange = 4,
        pos = pos,
        barrierName = "Aegis Deflector",
        barrierThreshold = 850,
        commandAuraRadius = 3,
        elementalAffinity = ElementalAffinity.ABYSSAL_GRAVITY,
        pilot = Pilot(
            id = "pilot_miranda",
            name = "Capt. Miranda Lockheart",
            callsign = "AEGIS-ACTUAL",
            meleeStat = 130,
            rangedStat = 172,
            hitStat = 168,
            evadeStat = 118,
            will = 105,
            maxSp = 120,
            currentSp = 120,
            spirits = listOf(
                SpiritCommand.TRUST,
                SpiritCommand.FRIENDSHIP,
                SpiritCommand.RESUPPLY,
                SpiritCommand.ROUSE,
                SpiritCommand.CONFUSE,
                SpiritCommand.IRON_WALL
            ),
            attackQuotes = listOf(
                "All batteries, fire for effect! Protect our mobile suits!",
                "Tactical datalink active! Suppress the enemy line!",
                "Bridge crew, divert auxiliary power to forward turrets!"
            ),
            finisherQuote = "MAIN GRAVITON CONVERGENCE CANNON... FIRE!!",
            dodgeQuote = "Hard port thrusters! Evasive maneuvers succeeded!",
            damageQuote = "Shields absorbing impact! Damage control teams to Deck 4!",
            primaryColor = Color(0xFF7C4DFF),
            visorColor = Color(0xFF18FFFF)
        ),
        weapons = listOf(
            Weapon(
                id = "w_aegis_ciws",
                name = "Pulse Laser Phalanx",
                basePower = 2400,
                minRange = 1,
                maxRange = 3,
                enCost = 10,
                maxAmmo = 0,
                currentAmmo = 0,
                willReq = 100,
                isPostMove = true,
                critBonus = 15,
                hitBonus = 25,
                animStyle = AnimationStyle.HYPER_BUSTER_RIFLE,
                isMelee = false
            ),
            Weapon(
                id = "w_aegis_torpedo",
                name = "Cruiser Torpedo Barrage",
                basePower = 3400,
                minRange = 2,
                maxRange = 5,
                enCost = 0,
                maxAmmo = 6,
                currentAmmo = 6,
                willReq = 100,
                isPostMove = true,
                critBonus = 15,
                hitBonus = 22,
                animStyle = AnimationStyle.MACROSS_MISSILE_STORM,
                isMelee = false
            ),
            Weapon(
                id = "w_aegis_cannon",
                name = "Heavy Macross Ion Cannon",
                basePower = 5400,
                minRange = 2,
                maxRange = 6,
                enCost = 65,
                maxAmmo = 0,
                currentAmmo = 0,
                willReq = 110,
                isPostMove = false,
                critBonus = 25,
                hitBonus = 30,
                animStyle = AnimationStyle.HYPER_BUSTER_RIFLE,
                isMelee = false
            )
        )
    )

    fun createEnemyVanguard(id: String, nameSuffix: String, pos: GridPos): MechaUnit = MechaUnit(
        id = id,
        name = "ZVG-06 Vanguard $nameSuffix",
        modelCode = "ZVG-06",
        faction = Faction.ENEMY,
        archetype = MechaArchetype.REAL_ROBOT,
        visualRig = MechaVisualRig.ZAKU_VANGUARD,
        canFly = false,
        baseMaxHp = 3900,
        currentHp = 3900,
        baseMaxEn = 140,
        currentEn = 140,
        baseArmor = 1080,
        baseMobility = 115,
        baseMoveRange = 4,
        pos = pos,
        elementalAffinity = ElementalAffinity.ASTRAL_WIND,
        pilot = Pilot(
            id = "pilot_$id",
            name = "Vanguard Trooper $nameSuffix",
            callsign = "RED-$nameSuffix",
            meleeStat = 135,
            rangedStat = 138,
            hitStat = 136,
            evadeStat = 120,
            will = 100,
            maxSp = 50,
            currentSp = 50,
            spirits = listOf(SpiritCommand.STRIKE),
            attackQuotes = listOf(
                "Mono-eye sensor locked! Glory to the Crimson Armada!",
                "Suppressing Earth Federation units!",
                "Heat Hawk charged! Charge the perimeter!"
            ),
            finisherQuote = "FULL BURST ASSAULT! CRUSH THEM!",
            dodgeQuote = "Thrusters engaged! You missed!",
            damageQuote = "Armor breached! Requesting backup!",
            primaryColor = Color(0xFFFF2A54),
            visorColor = Color(0xFFFF1744)
        ),
        weapons = listOf(
            Weapon(
                id = "w_zaku_hawk",
                name = "Thermal Heat Axe",
                basePower = 2100,
                minRange = 1,
                maxRange = 1,
                enCost = 0,
                maxAmmo = 0,
                currentAmmo = 0,
                willReq = 100,
                isPostMove = true,
                critBonus = 15,
                hitBonus = 18,
                animStyle = AnimationStyle.BEAM_SABER_BLITZ,
                isMelee = true
            ),
            Weapon(
                id = "w_zaku_mg",
                name = "120mm Mag-Rifle",
                basePower = 2450,
                minRange = 1,
                maxRange = 4,
                enCost = 10,
                maxAmmo = 8,
                currentAmmo = 8,
                willReq = 100,
                isPostMove = true,
                critBonus = 10,
                hitBonus = 15,
                animStyle = AnimationStyle.MACROSS_MISSILE_STORM,
                isMelee = false
            )
        )
    )

    fun createEnemyGravDestroyer(id: String, nameSuffix: String, pos: GridPos): MechaUnit = MechaUnit(
        id = id,
        name = "GD-88 Graviton $nameSuffix",
        modelCode = "GD-88",
        faction = Faction.ENEMY,
        archetype = MechaArchetype.SUPER_ROBOT,
        visualRig = MechaVisualRig.GRAV_DESTROYER,
        canFly = true,
        baseMaxHp = 6400,
        currentHp = 6400,
        baseMaxEn = 180,
        currentEn = 180,
        baseArmor = 1520,
        baseMobility = 102,
        baseMoveRange = 4,
        pos = pos,
        barrierName = "Distortion Field",
        barrierThreshold = 500,
        elementalAffinity = ElementalAffinity.CHRONO_LIGHTNING,
        pilot = Pilot(
            id = "pilot_$id",
            name = "Captain Viktor $nameSuffix",
            callsign = "IRON-$nameSuffix",
            meleeStat = 154,
            rangedStat = 158,
            hitStat = 148,
            evadeStat = 115,
            will = 105,
            maxSp = 70,
            currentSp = 70,
            spirits = listOf(SpiritCommand.STRIKE, SpiritCommand.IRON_WALL),
            attackQuotes = listOf(
                "Heavy Graviton cannons online! Kneel before the Armada!",
                "Your peashooters cannot pierce heavy dreadnought plating!"
            ),
            finisherQuote = "GRAVITON PULSAR... OBLITERATE THEM!",
            dodgeQuote = "Calculated trajectory—evaded!",
            damageQuote = "Impossible! They penetrated the Distortion Field?!",
            primaryColor = Color(0xFFFF6D00),
            visorColor = Color(0xFFFFD600)
        ),
        weapons = listOf(
            Weapon(
                id = "w_grav_claw",
                name = "Crusher Vibro-Claw",
                basePower = 2800,
                minRange = 1,
                maxRange = 2,
                enCost = 10,
                maxAmmo = 0,
                currentAmmo = 0,
                willReq = 100,
                isPostMove = true,
                critBonus = 15,
                hitBonus = 15,
                animStyle = AnimationStyle.BEAM_SABER_BLITZ,
                isMelee = true
            ),
            Weapon(
                id = "w_grav_beam",
                name = "Heavy Graviton Ray",
                basePower = 3600,
                minRange = 1,
                maxRange = 5,
                enCost = 25,
                maxAmmo = 0,
                currentAmmo = 0,
                willReq = 100,
                isPostMove = true,
                critBonus = 15,
                hitBonus = 20,
                animStyle = AnimationStyle.GRAVITY_SINGULARITY_CANNON,
                isMelee = false
            )
        )
    )

    fun createBossEmperorZeph(pos: GridPos): MechaUnit = MechaUnit(
        id = "enemy_boss_zeph",
        name = "X-00 Dark Emperor Zeph",
        modelCode = "ZEPH-00",
        faction = Faction.ENEMY,
        archetype = MechaArchetype.SUPER_ROBOT,
        visualRig = MechaVisualRig.EMPEROR_ZEPH,
        canFly = true,
        baseMaxHp = 11500,
        currentHp = 11500,
        baseMaxEn = 300,
        currentEn = 300,
        baseArmor = 1920,
        baseMobility = 138,
        baseMoveRange = 5,
        pos = pos,
        barrierName = "Dark Aegis Barrier",
        barrierThreshold = 900,
        commandAuraRadius = 3,
        elementalAffinity = ElementalAffinity.ABYSSAL_GRAVITY,
        pilot = Pilot(
            id = "pilot_zeph",
            name = "Overlord Zepharias",
            callsign = "OMEGA-ZERO",
            meleeStat = 182,
            rangedStat = 186,
            hitStat = 176,
            evadeStat = 148,
            will = 120,
            maxSp = 150,
            currentSp = 150,
            spirits = listOf(SpiritCommand.VALOR, SpiritCommand.STRIKE, SpiritCommand.IRON_WALL),
            attackQuotes = listOf(
                "Witness the true power of the Singularity Engine!",
                "Pitiful mortals! Your mechs are relics of a dying era!",
                "Bow before the Dark Emperor's dominion!"
            ),
            finisherQuote = "COLLAPSE INTO THE ABYSS! SINGULARITY NOVA CANNON!!",
            dodgeQuote = "Foolish! You strike at a shadow in warp space!",
            damageQuote = "What?! A mortal machine cracked my Dark Aegis?!",
            primaryColor = Color(0xFFD500F9),
            visorColor = Color(0xFFFF1744)
        ),
        weapons = listOf(
            Weapon(
                id = "w_zeph_blade",
                name = "Emperor Calamity Halberd",
                basePower = 3600,
                minRange = 1,
                maxRange = 2,
                enCost = 15,
                maxAmmo = 0,
                currentAmmo = 0,
                willReq = 100,
                isPostMove = true,
                critBonus = 25,
                hitBonus = 28,
                animStyle = AnimationStyle.KAISER_NOVA_BREAKER,
                isMelee = true
            ),
            Weapon(
                id = "w_zeph_funnels",
                name = "Abyssal Fang Drones",
                basePower = 4100,
                minRange = 1,
                maxRange = 5,
                enCost = 25,
                maxAmmo = 0,
                currentAmmo = 0,
                willReq = 100,
                isPostMove = true,
                critBonus = 25,
                hitBonus = 30,
                animStyle = AnimationStyle.FIN_FUNNEL_SWARM,
                isMelee = false
            ),
            Weapon(
                id = "w_zeph_nova",
                name = "Singularity Nova Cannon",
                basePower = 5100,
                minRange = 1,
                maxRange = 6,
                enCost = 45,
                maxAmmo = 0,
                currentAmmo = 0,
                willReq = 110,
                isPostMove = true,
                critBonus = 30,
                hitBonus = 32,
                animStyle = AnimationStyle.GRAVITY_SINGULARITY_CANNON,
                isMelee = false
            )
        )
    )

    fun getScenarios(): List<ScenarioMission> = listOf(
        createScenarioNeoTokyo(),
        createScenarioAsteroidBelt(),
        createScenarioEmperorCitadel()
    )

    private fun createScenarioNeoTokyo(): ScenarioMission {
        val P = TerrainType.PLAIN
        val F = TerrainType.FOREST
        val C = TerrainType.CITY
        val B = TerrainType.BASE
        val M = TerrainType.MOUNTAIN
        val S = TerrainType.SEA

        val grid = listOf(
            listOf(M, M, F, C, C, B, C, F, S, S),
            listOf(M, F, P, C, C, C, P, F, S, S),
            listOf(F, P, C, B, C, F, P, M, P, S),
            listOf(P, C, C, F, P, F, M, M, P, P),
            listOf(P, F, P, F, B, F, P, F, C, P),
            listOf(S, P, F, C, C, F, P, C, C, P),
            listOf(S, S, P, C, B, C, F, P, F, P),
            listOf(S, P, F, C, C, C, P, M, M, P),
            listOf(P, F, B, C, P, F, P, M, M, M),
            listOf(P, P, C, C, P, P, F, P, M, M)
        )

        return ScenarioMission(
            id = "mission_01",
            code = "STAGE 01",
            title = "Operation Burning Sky",
            subtitle = "Neo-Tokyo Defense Line",
            briefing = "Vanguard strike forces and Overlord Zepharias have descended on Sector 7. Hold the Aegis Command Bases (+25% DEF, +15% Regen) and eliminate all hostile mobile weapons!",
            victoryCondition = "Destroy all Enemy Units (Defeat Boss X-00 Dark Emperor Zeph)",
            mapWidth = 10,
            mapHeight = 10,
            terrainGrid = grid,
            initialAllies = listOf(
                createKaiserBlaze(GridPos(3, 6)),
                createAstralGundam(GridPos(4, 6)),
                createValkyrieZero(GridPos(5, 6)),
                createAegisBattleship(GridPos(2, 8))
            ),
            initialEnemies = listOf(
                createEnemyVanguard("enemy_v1", "Alpha", GridPos(3, 3)),
                createEnemyVanguard("enemy_v2", "Beta", GridPos(5, 3)),
                createEnemyGravDestroyer("enemy_g1", "Kronos", GridPos(4, 2)),
                createBossEmperorZeph(GridPos(5, 0))
            )
        )
    }

    private fun createScenarioAsteroidBelt(): ScenarioMission {
        val S = TerrainType.SPACE
        val A = TerrainType.ASTEROID
        val B = TerrainType.BASE

        val grid = listOf(
            listOf(S, S, A, A, S, B, S, A, S, S),
            listOf(S, A, A, S, S, S, S, A, A, S),
            listOf(S, S, S, S, A, A, S, S, S, S),
            listOf(A, S, B, S, A, S, S, B, S, A),
            listOf(A, S, S, S, S, S, A, S, S, A),
            listOf(S, S, A, S, B, S, S, A, S, S),
            listOf(S, A, A, S, S, S, S, A, A, S),
            listOf(S, S, S, S, A, A, S, S, S, S),
            listOf(S, S, B, S, S, S, S, B, S, S),
            listOf(S, S, S, A, A, A, S, S, S, S)
        )

        return ScenarioMission(
            id = "mission_02",
            code = "STAGE 02",
            title = "Stardust Singularity",
            subtitle = "Lagrange Point Debris Reef",
            briefing = "Zero-G orbital interception! Use Asteroid Debris Reefs (+20% DEF/EVADE) to shield your approach and unleash All-Range Fin Funnels.",
            victoryCondition = "Rout the Orbital Armada",
            mapWidth = 10,
            mapHeight = 10,
            terrainGrid = grid,
            initialAllies = listOf(
                createAstralGundam(GridPos(4, 7)),
                createKaiserBlaze(GridPos(3, 7)),
                createValkyrieZero(GridPos(5, 7)),
                createAegisBattleship(GridPos(2, 8))
            ),
            initialEnemies = listOf(
                createEnemyVanguard("enemy_v1", "Gamma", GridPos(2, 3)),
                createEnemyVanguard("enemy_v2", "Delta", GridPos(6, 3)),
                createEnemyGravDestroyer("enemy_g1", "Nemesis", GridPos(4, 2)),
                createEnemyGravDestroyer("enemy_g2", "Phobos", GridPos(5, 2)),
                createBossEmperorZeph(GridPos(5, 0))
            )
        )
    }

    private fun createScenarioEmperorCitadel(): ScenarioMission {
        val M = TerrainType.MOUNTAIN
        val B = TerrainType.BASE
        val C = TerrainType.CITY
        val P = TerrainType.PLAIN

        val grid = listOf(
            listOf(M, M, M, B, B, B, M, M, M, M),
            listOf(M, M, C, C, B, C, C, M, M, M),
            listOf(M, P, P, C, C, C, P, P, M, M),
            listOf(M, P, B, P, P, P, B, P, P, M),
            listOf(P, P, P, M, B, M, P, P, P, P),
            listOf(P, P, P, M, M, M, P, P, P, P),
            listOf(P, C, B, P, P, P, B, C, P, P),
            listOf(P, C, C, P, P, P, C, C, P, P),
            listOf(M, P, P, P, B, P, P, P, M, M),
            listOf(M, M, P, P, P, P, P, M, M, M)
        )

        return ScenarioMission(
            id = "mission_03",
            code = "STAGE 03",
            title = "Final Battle: Eclipse Throne",
            subtitle = "Red Canyon Imperial Citadel",
            briefing = "The final assault on Overlord Zepharias's fortress! Activate Spirit Commands (VALOR, SOUL, FOCUS) to pierce the Dark Aegis Barrier!",
            victoryCondition = "Defeat X-00 Dark Emperor Zeph and his Royal Guard",
            mapWidth = 10,
            mapHeight = 10,
            terrainGrid = grid,
            initialAllies = listOf(
                createKaiserBlaze(GridPos(4, 6)),
                createAstralGundam(GridPos(3, 6)),
                createValkyrieZero(GridPos(5, 6)),
                createAegisBattleship(GridPos(4, 8))
            ),
            initialEnemies = listOf(
                createEnemyGravDestroyer("enemy_g1", "Praetorian-I", GridPos(3, 2)),
                createEnemyGravDestroyer("enemy_g2", "Praetorian-II", GridPos(5, 2)),
                createEnemyVanguard("enemy_v1", "Guard-A", GridPos(2, 3)),
                createEnemyVanguard("enemy_v2", "Guard-B", GridPos(6, 3)),
                createBossEmperorZeph(GridPos(4, 1))
            )
        )
    }
}
