package com.example.srw.model

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

data class GridPos(val x: Int, val y: Int) {
    fun distanceTo(other: GridPos): Int = abs(x - other.x) + abs(y - other.y)

    fun neighbors(width: Int, height: Int): List<GridPos> = buildList {
        if (x > 0) add(GridPos(x - 1, y))
        if (x < width - 1) add(GridPos(x + 1, y))
        if (y > 0) add(GridPos(x, y - 1))
        if (y < height - 1) add(GridPos(x, y + 1))
    }
}

enum class Faction {
    ALLY,
    ENEMY
}

enum class TerrainType(
    val displayName: String,
    val shortCode: String,
    val defBonusPercent: Int,
    val evadeBonusPercent: Int,
    val attackBonusPercent: Int,
    val hitBonusPercent: Int,
    val critBonusPercent: Int,
    val moveCostGround: Int,
    val moveCostSky: Int,
    val hpEnRegenPercent: Int,
    val tacticalRoleLabel: String,
    val tilePrimaryColor: Color,
    val tileAccentColor: Color
) {
    PLAIN(
        displayName = "Plains Sector",
        shortCode = "PLN",
        defBonusPercent = 5,
        evadeBonusPercent = 5,
        attackBonusPercent = 0,
        hitBonusPercent = 5,
        critBonusPercent = 0,
        moveCostGround = 1,
        moveCostSky = 1,
        hpEnRegenPercent = 0,
        tacticalRoleLabel = "Open Sightline",
        tilePrimaryColor = Color(0xFF122628),
        tileAccentColor = Color(0xFF234B4E)
    ),
    FOREST(
        displayName = "Dense Forest",
        shortCode = "FRS",
        defBonusPercent = 15,
        evadeBonusPercent = 20,
        attackBonusPercent = 5,
        hitBonusPercent = 0,
        critBonusPercent = 12,
        moveCostGround = 2,
        moveCostSky = 1,
        hpEnRegenPercent = 0,
        tacticalRoleLabel = "Evasion Cover & Ambush",
        tilePrimaryColor = Color(0xFF0E291E),
        tileAccentColor = Color(0xFF2E7D5B)
    ),
    CITY(
        displayName = "Urban Sector (Neo-Tokyo)",
        shortCode = "URB",
        defBonusPercent = 15,
        evadeBonusPercent = 15,
        attackBonusPercent = 8,
        hitBonusPercent = 5,
        critBonusPercent = 5,
        moveCostGround = 1,
        moveCostSky = 1,
        hpEnRegenPercent = 5,
        tacticalRoleLabel = "Balanced Urban Grid",
        tilePrimaryColor = Color(0xFF1A243B),
        tileAccentColor = Color(0xFF395385)
    ),
    BASE(
        displayName = "Aegis Command Base",
        shortCode = "BSE",
        defBonusPercent = 25,
        evadeBonusPercent = 20,
        attackBonusPercent = 10,
        hitBonusPercent = 15,
        critBonusPercent = 5,
        moveCostGround = 1,
        moveCostSky = 1,
        hpEnRegenPercent = 15,
        tacticalRoleLabel = "Bastion & Fire-Control",
        tilePrimaryColor = Color(0xFF192B4D),
        tileAccentColor = Color(0xFF00E5FF)
    ),
    MOUNTAIN(
        displayName = "Highland Ridge",
        shortCode = "MTN",
        defBonusPercent = 20,
        evadeBonusPercent = 15,
        attackBonusPercent = 15,
        hitBonusPercent = 12,
        critBonusPercent = 10,
        moveCostGround = 2,
        moveCostSky = 1,
        hpEnRegenPercent = 0,
        tacticalRoleLabel = "High-Ground Dominance",
        tilePrimaryColor = Color(0xFF28201B),
        tileAccentColor = Color(0xFF5C4738)
    ),
    SEA(
        displayName = "Ocean Trench",
        shortCode = "SEA",
        defBonusPercent = 10,
        evadeBonusPercent = 0,
        attackBonusPercent = 5,
        hitBonusPercent = -5,
        critBonusPercent = 0,
        moveCostGround = 2,
        moveCostSky = 1,
        hpEnRegenPercent = 0,
        tacticalRoleLabel = "Beam Cooling / Drag",
        tilePrimaryColor = Color(0xFF0A1D37),
        tileAccentColor = Color(0xFF144272)
    ),
    SPACE(
        displayName = "Zero-G Orbit",
        shortCode = "SPC",
        defBonusPercent = 0,
        evadeBonusPercent = 0,
        attackBonusPercent = 5,
        hitBonusPercent = 10,
        critBonusPercent = 8,
        moveCostGround = 1,
        moveCostSky = 1,
        hpEnRegenPercent = 0,
        tacticalRoleLabel = "Zero-G Precision",
        tilePrimaryColor = Color(0xFF080B16),
        tileAccentColor = Color(0xFF1B2440)
    ),
    ASTEROID(
        displayName = "Debris Reef",
        shortCode = "AST",
        defBonusPercent = 20,
        evadeBonusPercent = 20,
        attackBonusPercent = 5,
        hitBonusPercent = 0,
        critBonusPercent = 10,
        moveCostGround = 2,
        moveCostSky = 1,
        hpEnRegenPercent = 5,
        tacticalRoleLabel = "3D Cover & Ambush",
        tilePrimaryColor = Color(0xFF1F1B2C),
        tileAccentColor = Color(0xFF4F436E)
    )
}

enum class MechaArchetype(val label: String, val badge: String) {
    SUPER_ROBOT("Super Robot", "SUPER"),
    REAL_ROBOT("Real Robot", "REAL"),
    COMMAND_SHIP("Command Carrier", "SHIP")
}

enum class MechaVisualRig {
    KAISER_BLAZE,
    ASTRAL_GUNDAM,
    VALKYRIE_ZERO,
    AEGIS_BATTLESHIP,
    ZAKU_VANGUARD,
    GRAV_DESTROYER,
    EMPEROR_ZEPH
}

enum class AnimationStyle(val label: String) {
    BEAM_SABER_BLITZ("Plasma Saber Combo"),
    HYPER_BUSTER_RIFLE("Hyper Buster Beam"),
    FIN_FUNNEL_SWARM("All-Range Fin Funnels"),
    KAISER_NOVA_BREAKER("Kaiser Sun-Breaker"),
    MACROSS_MISSILE_STORM("Micro-Missile Circus"),
    GRAVITY_SINGULARITY_CANNON("Graviton Singularity")
}

enum class SpiritCategory(
    val displayName: String,
    val jpKanji: String,
    val badgeColor: Color
) {
    BUFF("Combat Buff", "強化", Color(0xFF00E5FF)),
    DEBUFF("Enemy Debuff", "妨害", Color(0xFFFF5252)),
    HEAL("Heal & Resupply", "回復", Color(0xFF69F0AE))
}

enum class SpiritTargetType(val label: String) {
    SELF("Self"),
    SINGLE_ALLY("Target Ally"),
    ALL_ALLIES("All Allies"),
    SINGLE_ENEMY("Target Enemy"),
    ALL_ENEMIES("All Enemies")
}

enum class SpiritDebuff(
    val codeName: String,
    val jpKanji: String,
    val summary: String,
    val badgeColor: Color
) {
    ANALYZED(
        codeName = "ANALYZED",
        jpKanji = "分析",
        summary = "+25% Incoming Damage & -15% Evasion for 1 turn",
        badgeColor = Color(0xFFFFAB40)
    ),
    CONFUSED(
        codeName = "CONFUSED",
        jpKanji = "かく乱",
        summary = "-35% Hit Rate & -15 Mobility for 1 turn",
        badgeColor = Color(0xFFE040FB)
    ),
    DAUNTED(
        codeName = "DAUNTED",
        jpKanji = "脱力",
        summary = "Pilot Will & Reactor EN suppressed (-15 Will, -30 EN)",
        badgeColor = Color(0xFFFF5252)
    )
}

enum class SpiritCommand(
    val codeName: String,
    val jpKanji: String,
    val spCost: Int,
    val category: SpiritCategory,
    val targetType: SpiritTargetType,
    val summary: String,
    val badgeColor: Color,
    val isInstantAction: Boolean = false
) {
    // 1. COMBAT BUFFS (強化)
    STRIKE(
        codeName = "STRIKE",
        jpKanji = "必中",
        spCost = 15,
        category = SpiritCategory.BUFF,
        targetType = SpiritTargetType.SELF,
        summary = "100% Accuracy for 1 turn",
        badgeColor = Color(0xFF00E5FF)
    ),
    ALERT(
        codeName = "FLASH",
        jpKanji = "閃き",
        spCost = 15,
        category = SpiritCategory.BUFF,
        targetType = SpiritTargetType.SELF,
        summary = "100% Dodge vs next incoming attack",
        badgeColor = Color(0xFF64FFDA)
    ),
    FOCUS(
        codeName = "FOCUS",
        jpKanji = "集中",
        spCost = 20,
        category = SpiritCategory.BUFF,
        targetType = SpiritTargetType.SELF,
        summary = "+30% Hit Rate & Evasion for 1 turn",
        badgeColor = Color(0xFF40C4FF)
    ),
    ACCEL(
        codeName = "ACCEL",
        jpKanji = "加速",
        spCost = 15,
        category = SpiritCategory.BUFF,
        targetType = SpiritTargetType.SELF,
        summary = "+3 Move Range & +25 Initiative this turn",
        badgeColor = Color(0xFF00E676)
    ),
    VALOR(
        codeName = "VALOR",
        jpKanji = "熱血",
        spCost = 35,
        category = SpiritCategory.BUFF,
        targetType = SpiritTargetType.SELF,
        summary = "2.0x Damage on next attack",
        badgeColor = Color(0xFFFF5252)
    ),
    SOUL(
        codeName = "SOUL",
        jpKanji = "魂",
        spCost = 45,
        category = SpiritCategory.BUFF,
        targetType = SpiritTargetType.SELF,
        summary = "2.5x Damage on next attack",
        badgeColor = Color(0xFFFFD740)
    ),
    IRON_WALL(
        codeName = "WALL",
        jpKanji = "鉄壁",
        spCost = 25,
        category = SpiritCategory.BUFF,
        targetType = SpiritTargetType.SELF,
        summary = "-60% Damage taken for 1 turn",
        badgeColor = Color(0xFFB388FF)
    ),
    DRIVE(
        codeName = "DRIVE",
        jpKanji = "気合",
        spCost = 30,
        category = SpiritCategory.BUFF,
        targetType = SpiritTargetType.SELF,
        summary = "+10 Pilot Will immediately",
        badgeColor = Color(0xFFFFAB40),
        isInstantAction = true
    ),
    ROUSE(
        codeName = "ROUSE",
        jpKanji = "激励",
        spCost = 30,
        category = SpiritCategory.BUFF,
        targetType = SpiritTargetType.SINGLE_ALLY,
        summary = "Target Ally: +12 Pilot Will & cleanses active debuffs",
        badgeColor = Color(0xFFFFEA00),
        isInstantAction = true
    ),

    // 2. ENEMY DEBUFFS (妨害)
    ANALYZE(
        codeName = "ANALYZE",
        jpKanji = "分析",
        spCost = 20,
        category = SpiritCategory.DEBUFF,
        targetType = SpiritTargetType.SINGLE_ENEMY,
        summary = "Target Enemy: +25% Damage taken & -15% Evasion for 1 turn",
        badgeColor = Color(0xFFFFAB40),
        isInstantAction = true
    ),
    EXHAUST(
        codeName = "DAUNT",
        jpKanji = "脱力",
        spCost = 35,
        category = SpiritCategory.DEBUFF,
        targetType = SpiritTargetType.SINGLE_ENEMY,
        summary = "Target Enemy: -15 Pilot Will & -30 EN drained immediately",
        badgeColor = Color(0xFFFF5252),
        isInstantAction = true
    ),
    CONFUSE(
        codeName = "DISTURB",
        jpKanji = "かく乱",
        spCost = 40,
        category = SpiritCategory.DEBUFF,
        targetType = SpiritTargetType.ALL_ENEMIES,
        summary = "All Enemies: -35% Hit Rate & -15 Mobility for 1 turn",
        badgeColor = Color(0xFFE040FB),
        isInstantAction = true
    ),

    // 3. HEALING & RESUPPLY (回復)
    TRUST(
        codeName = "TRUST",
        jpKanji = "信頼",
        spCost = 25,
        category = SpiritCategory.HEAL,
        targetType = SpiritTargetType.SINGLE_ALLY,
        summary = "Target Ally: Restore 3,000 HP & cleanse debuffs",
        badgeColor = Color(0xFF69F0AE),
        isInstantAction = true
    ),
    FRIENDSHIP(
        codeName = "BONDS",
        jpKanji = "友情",
        spCost = 40,
        category = SpiritCategory.HEAL,
        targetType = SpiritTargetType.ALL_ALLIES,
        summary = "All Allies: Restore 2,500 HP, +35 EN & cleanse debuffs",
        badgeColor = Color(0xFF00E676),
        isInstantAction = true
    ),
    RESUPPLY(
        codeName = "RESUPPLY",
        jpKanji = "補給",
        spCost = 45,
        category = SpiritCategory.HEAL,
        targetType = SpiritTargetType.SINGLE_ALLY,
        summary = "Target Ally: Restore 100% EN & 100% Weapon Ammo",
        badgeColor = Color(0xFF18FFFF),
        isInstantAction = true
    )
}

/**
 * Armor Development System: Specialized plating materials providing armor, damage resistance, and barrier bonuses.
 */
enum class ArmorPlatingTier(
    val level: Int,
    val displayName: String,
    val shortCode: String,
    val creditCost: Int,
    val bonusArmor: Int,
    val damageReductionPercent: Int,
    val bonusBarrier: Int,
    val summary: String
) {
    STANDARD_TITANIUM(
        level = 0,
        displayName = "Titanium Composite",
        shortCode = "TI-STD",
        creditCost = 0,
        bonusArmor = 0,
        damageReductionPercent = 0,
        bonusBarrier = 0,
        summary = "Standard military-grade mobile suit titanium alloy."
    ),
    GUNDARIUM_COMPOSITE(
        level = 1,
        displayName = "Gundarium Alpha Plate",
        shortCode = "GND-A",
        creditCost = 1800,
        bonusArmor = 220,
        damageReductionPercent = 8,
        bonusBarrier = 120,
        summary = "+220 Armor, -8% Incoming Damage, +120 Barrier."
    ),
    NANO_LAMINATE_COAT(
        level = 2,
        displayName = "Nano-Laminate Mirror Coat",
        shortCode = "NLC-M",
        creditCost = 2800,
        bonusArmor = 380,
        damageReductionPercent = 16,
        bonusBarrier = 260,
        summary = "+380 Armor, -16% Beam/Kinetic Damage, +260 Barrier."
    ),
    ORICHALCUM_PRANA_ALLOY(
        level = 3,
        displayName = "Orichalcum Prana Armor",
        shortCode = "ORI-Z",
        creditCost = 4200,
        bonusArmor = 580,
        damageReductionPercent = 24,
        bonusBarrier = 420,
        summary = "+580 Armor, -24% Damage Taken, +420 Arcane Barrier."
    )
}

/**
 * Magic Development System: Elemental Prana/Ether affinities granting passive buffs and elemental advantage.
 * Advantage cycle: SOLAR_FIRE > ASTRAL_WIND > CHRONO_LIGHTNING > ABYSSAL_GRAVITY > SOLAR_FIRE.
 */
enum class ElementalAffinity(
    val displayName: String,
    val kanji: String,
    val badgeColor: Color,
    val summary: String
) {
    SOLAR_FIRE(
        displayName = "Solar Flame",
        kanji = "火",
        badgeColor = Color(0xFFFF5252),
        summary = "Strong vs Wind (+15% DMG) • +10% Weapon & Spell Power"
    ),
    ASTRAL_WIND(
        displayName = "Astral Gale",
        kanji = "風",
        badgeColor = Color(0xFF00E676),
        summary = "Strong vs Volt (+15% DMG) • +10% Evasion & +1 Move at Magic Lv.3+"
    ),
    CHRONO_LIGHTNING(
        displayName = "Chrono Volt",
        kanji = "雷",
        badgeColor = Color(0xFF00E5FF),
        summary = "Strong vs Void (+15% DMG) • +15 Initiative & +10% Hit Rate"
    ),
    ABYSSAL_GRAVITY(
        displayName = "Void Gravity",
        kanji = "闇",
        badgeColor = Color(0xFFD500F9),
        summary = "Strong vs Fire (+15% DMG) • Pierces 20% Enemy Armor & +200 Barrier"
    );

    fun hasAdvantageOver(other: ElementalAffinity): Boolean = when (this) {
        SOLAR_FIRE -> other == ASTRAL_WIND
        ASTRAL_WIND -> other == CHRONO_LIGHTNING
        CHRONO_LIGHTNING -> other == ABYSSAL_GRAVITY
        ABYSSAL_GRAVITY -> other == SOLAR_FIRE
    }
}

/**
 * Magic Development System: Developable Arcane / Prana Spells unlocked via Magic Conduit research.
 */
enum class MagicSpell(
    val codeName: String,
    val jpName: String,
    val requiredMagicLevel: Int,
    val manaSpCost: Int,
    val researchCreditCost: Int,
    val badgeColor: Color,
    val summary: String
) {
    PRANA_OVERDRIVE(
        codeName = "PRANA OVERDRIVE",
        jpName = "魔力覚醒",
        requiredMagicLevel = 1,
        manaSpCost = 25,
        researchCreditCost = 1500,
        badgeColor = Color(0xFFFF5252),
        summary = "Infuses reactor with Prana: grants VALOR (2.0x DMG), FOCUS (+30% Hit/Eva) & +12 Will."
    ),
    AEGIS_SANCTUARY(
        codeName = "CHRONO SANCTUARY",
        jpName = "聖域結界",
        requiredMagicLevel = 2,
        manaSpCost = 30,
        researchCreditCost = 2200,
        badgeColor = Color(0xFF69F0AE),
        summary = "Restores +3,200 HP, +50 EN, and deploys WALL (-60% DMG taken for 1 turn)."
    ),
    STARLIGHT_BLINK(
        codeName = "STARLIGHT BLINK",
        jpName = "転移閃光",
        requiredMagicLevel = 3,
        manaSpCost = 25,
        researchCreditCost = 2600,
        badgeColor = Color(0xFF00E5FF),
        summary = "Warps space-time: activates ACCEL (+3 Move), FLASH (100% Dodge) & STRIKE (100% Hit)."
    ),
    SOLAR_FLARE_NOVA(
        codeName = "ASTRAL NOVA SPELL",
        jpName = "星霊新星",
        requiredMagicLevel = 4,
        manaSpCost = 35,
        researchCreditCost = 3400,
        badgeColor = Color(0xFFFFD740),
        summary = "Grants SOUL (2.5x DMG), +15 Will, and +40 EN for an immediate Arcane Finisher."
    )
}

enum class DefendStance(val label: String, val subLabel: String) {
    COUNTER("COUNTER", "Strike back with selected weapon"),
    DEFEND("DEFEND", "Reduce incoming damage by 45%"),
    EVADE("EVADE", "Boost evasion rate by +45%")
}

data class Weapon(
    val id: String,
    val name: String,
    val basePower: Int,
    val minRange: Int,
    val maxRange: Int,
    val enCost: Int,
    val maxAmmo: Int, // 0 means energy-based/unlimited ammo
    val currentAmmo: Int,
    val willReq: Int,
    val isPostMove: Boolean, // [P] attribute in SRW
    val critBonus: Int,
    val hitBonus: Int,
    val animStyle: AnimationStyle,
    val isMelee: Boolean,
    val forgeLevel: Int = 0, // Individual Weapon Forging Rank (0..5)
    val isArcaneMagic: Boolean = false,
    val isPrototypeUnlock: Boolean = false
) {
    val effectiveHitBonus: Int get() = hitBonus + forgeLevel * 4
    val effectiveCritBonus: Int get() = critBonus + forgeLevel * 3

    fun effectivePower(unitUpgradeLevel: Int, magicLevel: Int = 1): Int =
        basePower + unitUpgradeLevel * 200 + forgeLevel * 260 +
            if (isArcaneMagic) (magicLevel - 1) * 320 else 0
}

enum class PilotPassiveAbility(
    val title: String,
    val jpName: String,
    val shortBadge: String,
    val xpCost: Int,
    val badgeColor: Color,
    val summary: String
) {
    POTENTIAL(
        title = "Potential",
        jpName = "底力",
        shortBadge = "POTENTIAL",
        xpCost = 120,
        badgeColor = Color(0xFFFF5252),
        summary = "When unit HP <= 50%, gain +20% Crit Rate, +15% Evasion, and +20% Armor."
    ),
    PRE_EMPTIVE_ACE(
        title = "Ace Instinct",
        jpName = "先制エース",
        shortBadge = "ACE INSTINCT",
        xpCost = 140,
        badgeColor = Color(0xFF00E5FF),
        summary = "Permanent +10 Hit Stat, +10 Evade Stat, +15 Initiative, and +10% Crit Rate."
    ),
    MORALE_SURGE(
        title = "Fighting Spirit",
        jpName = "闘争心",
        shortBadge = "FIGHT SPIRIT",
        xpCost = 150,
        badgeColor = Color(0xFFFFD740),
        summary = "Deploy with +8 starting Will and gain +3 extra Will on every enemy hit or kill."
    ),
    SP_CONSERVATION(
        title = "SP Font Mastery",
        jpName = "SPアップ",
        shortBadge = "SP FONT",
        xpCost = 160,
        badgeColor = Color(0xFF69F0AE),
        summary = "+25 Max SP, +4 SP regen per turn, and -20% SP cost on all Spirit Commands."
    ),
    ATTACKER_INSTINCT(
        title = "Attacker Mastery",
        jpName = "アタッカー",
        shortBadge = "ATTACKER",
        xpCost = 200,
        badgeColor = Color(0xFFD500F9),
        summary = "Permanent +12% Attack Damage Output whenever Pilot Will is 115 or higher."
    ),
    COUNTER_MASTER(
        title = "Counter Specialist",
        jpName = "カウンター",
        shortBadge = "COUNTER PRO",
        xpCost = 140,
        badgeColor = Color(0xFF40C4FF),
        summary = "+15% Counterattack Damage and +12% Evasion when defending in COUNTER stance."
    )
}

data class Pilot(
    val id: String,
    val name: String,
    val callsign: String,
    val meleeStat: Int,
    val rangedStat: Int,
    val hitStat: Int,
    val evadeStat: Int,
    val will: Int = 100,
    val maxSp: Int = 100,
    val currentSp: Int = 100,
    val spirits: List<SpiritCommand>,
    val attackQuotes: List<String>,
    val finisherQuote: String,
    val dodgeQuote: String,
    val damageQuote: String,
    val primaryColor: Color,
    val visorColor: Color,
    val xp: Int = 180,
    val totalXpEarned: Int = 180,
    val level: Int = 1,
    val kills: Int = 0,
    val passiveAbilities: Set<PilotPassiveAbility> = emptySet(),
    val spiritUpgrades: Map<SpiritCommand, Int> = emptyMap() // 0 = Base, 1 = Enhanced (+), 2 = Mastered (MAX)
) {
    fun hasPassive(ability: PilotPassiveAbility): Boolean = passiveAbilities.contains(ability)

    fun spiritRank(spirit: SpiritCommand): Int = (spiritUpgrades[spirit] ?: 0).coerceIn(0, 2)

    val rankTitle: String
        get() = when {
            level >= 6 || totalXpEarned >= 800 || kills >= 6 -> "SUPER ACE"
            level >= 4 || totalXpEarned >= 450 || kills >= 3 -> "ACE PILOT"
            level >= 2 || totalXpEarned >= 240 -> "LIEUTENANT"
            else -> "VANGUARD"
        }
}

data class MechaUnit(
    val id: String,
    val name: String,
    val modelCode: String,
    val faction: Faction,
    val archetype: MechaArchetype,
    val visualRig: MechaVisualRig,
    val canFly: Boolean,
    val baseMaxHp: Int,
    val currentHp: Int,
    val baseMaxEn: Int,
    val currentEn: Int,
    val baseArmor: Int,
    val baseMobility: Int,
    val baseMoveRange: Int,
    val pos: GridPos,
    val hasMoved: Boolean = false,
    val hasActed: Boolean = false,
    val pilot: Pilot,
    val weapons: List<Weapon>,
    val activeSpirits: Set<SpiritCommand> = emptySet(),
    val activeDebuffs: Set<SpiritDebuff> = emptySet(),
    val debuffRanks: Map<SpiritDebuff, Int> = emptyMap(),
    val barrierName: String? = null,
    val barrierThreshold: Int = 0,
    val commandAuraRadius: Int = 0,
    val upgradeHp: Int = 0,
    val upgradeEn: Int = 0,
    val upgradeArmor: Int = 0,
    val upgradeMobility: Int = 0,
    val upgradeWeapon: Int = 0,
    // Unit, Weapon, Armor & Magic Development System fields
    val frameTier: Int = 1, // 1 = Base, 2 = Kai Custom, 3 = Master Frame
    val armorPlating: ArmorPlatingTier = ArmorPlatingTier.STANDARD_TITANIUM,
    val barrierUpgradeLevel: Int = 0, // 0..5
    val magicLevel: Int = 1, // 1..5 Arcane Conduit Rank
    val elementalAffinity: ElementalAffinity = ElementalAffinity.SOLAR_FIRE,
    val unlockedSpells: Set<MagicSpell> = setOf(MagicSpell.PRANA_OVERDRIVE),
    val unlockedPrototypeWeapon: Boolean = false
) {
    val hasFullCustomBonus: Boolean
        get() = upgradeHp >= 3 && upgradeEn >= 3 && upgradeArmor >= 3 && upgradeMobility >= 3

    val isPotentialActive: Boolean
        get() = pilot.hasPassive(PilotPassiveAbility.POTENTIAL) && currentHp * 2 <= maxHp

    val maxHp: Int
        get() = baseMaxHp + upgradeHp * 600 + (frameTier - 1) * 900

    val maxEn: Int
        get() = baseMaxEn + upgradeEn * 25 + (frameTier - 1) * 35 + (if (hasFullCustomBonus) 30 else 0)

    val armor: Int
        get() {
            val base = baseArmor + upgradeArmor * 120 + armorPlating.bonusArmor + (frameTier - 1) * 150
            return if (isPotentialActive) (base * 1.20).toInt() else base
        }

    val mobility: Int
        get() {
            val confusePenalty = if (activeDebuffs.contains(SpiritDebuff.CONFUSED)) {
                15 + (debuffRanks[SpiritDebuff.CONFUSED] ?: 0) * 7
            } else 0
            return (baseMobility + upgradeMobility * 12 + (frameTier - 1) * 14 +
                (if (elementalAffinity == ElementalAffinity.ASTRAL_WIND) 10 else 0) -
                confusePenalty).coerceAtLeast(30)
        }

    val effectiveBarrierThreshold: Int
        get() = barrierThreshold + armorPlating.bonusBarrier + barrierUpgradeLevel * 160 +
            (if (elementalAffinity == ElementalAffinity.ABYSSAL_GRAVITY) 200 else 0) +
            (if (activeSpirits.contains(SpiritCommand.IRON_WALL) && pilot.spiritRank(SpiritCommand.IRON_WALL) >= 2) 200 else 0)

    val effectiveBarrierName: String
        get() = barrierName ?: if (effectiveBarrierThreshold > 0) "Arcane Aegis Field" else "None"

    val effectiveMaxSp: Int
        get() = pilot.maxSp + (magicLevel - 1) * 20 +
            (if (pilot.hasPassive(PilotPassiveAbility.SP_CONSERVATION)) 25 else 0)

    val effectiveMoveRange: Int
        get() {
            val accelBonus = if (activeSpirits.contains(SpiritCommand.ACCEL)) {
                3 + pilot.spiritRank(SpiritCommand.ACCEL)
            } else 0
            return baseMoveRange +
                accelBonus +
                (if (frameTier >= 3) 1 else 0) +
                (if (elementalAffinity == ElementalAffinity.ASTRAL_WIND && magicLevel >= 3) 1 else 0)
        }

    fun isAlive(): Boolean = currentHp > 0

    fun canUseWeapon(weapon: Weapon, afterMove: Boolean, distance: Int? = null): Boolean {
        if (afterMove && !weapon.isPostMove) return false
        if (pilot.will < weapon.willReq) return false
        if (currentEn < weapon.enCost) return false
        if (weapon.maxAmmo > 0 && weapon.currentAmmo <= 0) return false
        if (distance != null && (distance < weapon.minRange || distance > weapon.maxRange)) return false
        return true
    }

    fun availableWeapons(afterMove: Boolean, distance: Int? = null): List<Weapon> =
        weapons.filter { canUseWeapon(it, afterMove, distance) }
}

data class CombatForecast(
    val attacker: MechaUnit,
    val defender: MechaUnit,
    val attackerWeapon: Weapon,
    val defenderWeapon: Weapon?,
    val defenderStance: DefendStance,
    val distance: Int,
    val attackerTerrain: TerrainType,
    val defenderTerrain: TerrainType,
    val attackerPredictedDamage: Int,
    val attackerHitPercent: Int,
    val attackerCritPercent: Int,
    val defenderCanCounter: Boolean,
    val defenderPredictedDamage: Int,
    val defenderHitPercent: Int,
    val defenderCritPercent: Int,
    val attackerAuraBonus: Int,
    val defenderAuraBonus: Int,
    val attackerTerrainAttackBonus: Int = 0,
    val attackerTerrainHitBonus: Int = 0,
    val attackerTerrainCritBonus: Int = 0,
    val attackerTerrainEvadeBonus: Int = 0,
    val defenderTerrainDefBonus: Int = 0,
    val defenderTerrainEvadeBonus: Int = 0,
    val attackerEvasionPercent: Int = 0,
    val defenderEvasionPercent: Int = 0,
    val attackerAccuracyRating: Int = 0,
    val defenderEvasionRating: Int = 0,
    val defenderAccuracyRating: Int = 0,
    val attackerEvasionRating: Int = 0,
    val defenderProjectedHpAfter: Int = 0,
    val attackerProjectedHpAfter: Int = 0,
    val attackerWill: Int = 100,
    val defenderWill: Int = 100,
    val attackerMoraleTierName: String = "FOCUSED",
    val defenderMoraleTierName: String = "FOCUSED",
    val attackerBaselineDamageAt100Will: Int = 0,
    val attackerMoraleDamageDelta: Int = 0,
    val attackerMoraleOutputPercent: Int = 0,
    val defenderBaselineCounterDamageAt100Will: Int = 0,
    val defenderMoraleDamageDelta: Int = 0,
    val defenderMoraleOutputPercent: Int = 0,
    val defenderMoraleArmorPercent: Int = 0
)

enum class StrikeOutcome {
    HIT,
    CRITICAL,
    MISS
}

data class BattleCutsceneSequence(
    val id: Long = System.currentTimeMillis(),
    val attacker: MechaUnit,
    val defender: MechaUnit,
    val attackerWeapon: Weapon,
    val defenderWeapon: Weapon?,
    val defenderStance: DefendStance,
    val attackerTerrain: TerrainType,
    val defenderTerrain: TerrainType,
    val primaryOutcome: StrikeOutcome,
    val primaryDamage: Int,
    val primaryBarrierActivated: Boolean,
    val defenderHpAfterPrimary: Int,
    val counterExecuted: Boolean,
    val counterOutcome: StrikeOutcome,
    val counterDamage: Int,
    val counterBarrierActivated: Boolean,
    val attackerHpAfterCounter: Int,
    val attackerQuote: String,
    val defenderQuote: String,
    val attackerHitRate: Int,
    val defenderHitRate: Int,
    val isSandboxPreview: Boolean = false
)

data class CombatLogEntry(
    val id: Long,
    val turn: Int,
    val phase: Faction,
    val headline: String,
    val detail: String,
    val isCritical: Boolean = false,
    val isDestruction: Boolean = false
)

data class ScenarioMission(
    val id: String,
    val code: String,
    val title: String,
    val subtitle: String,
    val briefing: String,
    val victoryCondition: String,
    val mapWidth: Int,
    val mapHeight: Int,
    val terrainGrid: List<List<TerrainType>>,
    val initialAllies: List<MechaUnit>,
    val initialEnemies: List<MechaUnit>
)
