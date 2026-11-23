package com.example.srw.engine

import com.example.srw.model.*
import kotlin.math.min

/**
 * Categories inside the Hangar R&D Development Lab.
 */
enum class DevelopmentCategory(val title: String, val badge: String) {
    UNIT_FRAME("Unit & Frame R&D", "UNIT"),
    WEAPON_FORGE("Weapon Forge & Prototypes", "WEAPON"),
    ARMOR_BARRIER("Armor & Barrier Lab", "ARMOR"),
    MAGIC_ARCANE("Magic & Prana Grimoire", "MAGIC"),
    PILOT_ACADEMY("Pilot XP & Mastery", "PILOT")
}

/**
 * Result returned by any R&D operation in [DevelopmentSystem].
 */
data class DevelopmentOutcome(
    val success: Boolean,
    val updatedUnit: MechaUnit,
    val remainingCredits: Int,
    val costSpent: Int,
    val headline: String,
    val detail: String
)

/**
 * Comprehensive Super Robot Wars Unit, Weapon, Armor, and Magic Development System.
 *
 * Handles:
 * 1. **Unit Development**: 5-bar custom tuning (HP, EN, Mobility, Full Custom Bonus) and 3-tier Mecha Frame Evolution.
 * 2. **Weapon Development**: Individual weapon forging (Rank 0..5) and Ultimate Prototype Weapon R&D unlocks.
 * 3. **Armor Development**: Plating alloy installation (Titanium -> Gundarium -> Nano-Laminate -> Orichalcum) and Barrier Generator tuning.
 * 4. **Magic Development**: Arcane Prana Conduit leveling (Lv.1..5), Elemental Affinity attunement, Spell Grimoire unlocks, and Arcane Spell Weapons.
 */
class DevelopmentSystem {

    // =========================================================================
    // 1. UNIT & FRAME DEVELOPMENT
    // =========================================================================

    fun getStatTuneCost(currentLevel: Int): Int = 1200 + currentLevel * 350

    fun getFrameEvolutionCost(currentFrameTier: Int): Int = when (currentFrameTier) {
        1 -> 3200
        2 -> 4800
        else -> 0
    }

    /**
     * Tunes a core unit stat (`HP`, `EN`, `ARMOR`, `MOBILITY`, `WEAPON`) up to level 5.
     * Automatically awards the SRW Full Custom Bonus if HP, EN, Armor, and Mobility reach Lv.3+.
     */
    fun tuneUnitStat(
        unit: MechaUnit,
        statType: String,
        availableCredits: Int
    ): DevelopmentOutcome {
        val currentLevel = when (statType) {
            "HP" -> unit.upgradeHp
            "EN" -> unit.upgradeEn
            "ARMOR" -> unit.upgradeArmor
            "MOBILITY" -> unit.upgradeMobility
            "WEAPON" -> unit.upgradeWeapon
            else -> return failure(unit, availableCredits, "Unknown stat type $statType.")
        }

        if (currentLevel >= 5) {
            return failure(unit, availableCredits, "${unit.name} [$statType] is already at MAX Level (Lv.5).")
        }

        val cost = getStatTuneCost(currentLevel)
        if (availableCredits < cost) {
            return failure(unit, availableCredits, "Insufficient Credits ($cost CR required).")
        }

        val hadCustomBonus = unit.hasFullCustomBonus

        val tuned = when (statType) {
            "HP" -> unit.copy(
                upgradeHp = unit.upgradeHp + 1,
                currentHp = unit.currentHp + 600
            )
            "EN" -> unit.copy(
                upgradeEn = unit.upgradeEn + 1,
                currentEn = unit.currentEn + 25
            )
            "ARMOR" -> unit.copy(upgradeArmor = unit.upgradeArmor + 1)
            "MOBILITY" -> unit.copy(upgradeMobility = unit.upgradeMobility + 1)
            "WEAPON" -> unit.copy(upgradeWeapon = unit.upgradeWeapon + 1)
            else -> unit
        }

        val unlockedCustomNow = !hadCustomBonus && tuned.hasFullCustomBonus
        val finalUnit = if (unlockedCustomNow) {
            tuned.copy(
                currentEn = tuned.currentEn + 30,
                pilot = tuned.pilot.copy(will = min(170, tuned.pilot.will + 10))
            )
        } else tuned

        val bonusNote = if (unlockedCustomNow) {
            " [FULL CUSTOM BONUS UNLOCKED: +30 Max EN & +10 Pilot Will!]"
        } else ""

        return DevelopmentOutcome(
            success = true,
            updatedUnit = finalUnit,
            remainingCredits = availableCredits - cost,
            costSpent = cost,
            headline = "Tuned ${finalUnit.name} [$statType] to Lv.${currentLevel + 1}!",
            detail = "Spent $cost CR.$bonusNote"
        )
    }

    /**
     * Evolves the mecha's chassis to the next Frame Tier (Tier 1 -> Tier 2 -> Tier 3),
     * upgrading its model designation and boosting HP (+900), EN (+35), Armor (+150), Mobility (+14),
     * and Move Range (+1 at Tier 3).
     */
    fun evolveUnitFrame(
        unit: MechaUnit,
        availableCredits: Int
    ): DevelopmentOutcome {
        if (unit.frameTier >= 3) {
            return failure(unit, availableCredits, "${unit.name} has already reached Master Frame Tier 3.")
        }

        val cost = getFrameEvolutionCost(unit.frameTier)
        if (availableCredits < cost) {
            return failure(unit, availableCredits, "Need $cost CR to evolve ${unit.name} Frame.")
        }

        val nextTier = unit.frameTier + 1
        val evolvedName = getEvolvedUnitName(unit.id, unit.name, nextTier)
        val evolvedCode = "${unit.modelCode.substringBefore("-K")}-K$nextTier"

        val updated = unit.copy(
            name = evolvedName,
            modelCode = evolvedCode,
            frameTier = nextTier,
            currentHp = unit.currentHp + 900,
            currentEn = unit.currentEn + 35,
            pilot = unit.pilot.copy(will = min(170, unit.pilot.will + 5))
        )

        return DevelopmentOutcome(
            success = true,
            updatedUnit = updated,
            remainingCredits = availableCredits - cost,
            costSpent = cost,
            headline = "FRAME EVOLUTION: ${unit.name} -> $evolvedName (Tier $nextTier)!",
            detail = "Gained +900 Max HP, +35 Max EN, +150 Armor, +14 Mobility" +
                if (nextTier >= 3) ", and +1 Move Range!" else "!"
        )
    }

    private fun getEvolvedUnitName(unitId: String, currentName: String, tier: Int): String = when (unitId) {
        "ally_kaiser" -> if (tier == 2) "GX-99K Kaiser Blaze Kai" else "GX-99X True Kaiser Nova"
        "ally_astral" -> if (tier == 2) "RX-93V2 Hi-Astral Gundam" else "RX-93VX Astral Omega"
        "ally_valkyrie" -> if (tier == 2) "VF-0S Super Valkyrie" else "VF-0X Armored Valkyrie"
        "ally_aegis" -> if (tier == 2) "SDF-01K Aegis Vanguard" else "SDF-01X Hyper Aegis Ark"
        else -> "$currentName Mk-$tier"
    }

    // =========================================================================
    // 2. WEAPON DEVELOPMENT (INDIVIDUAL FORGING & PROTOTYPE R&D)
    // =========================================================================

    fun getWeaponForgeCost(currentForgeLevel: Int): Int = 1100 + currentForgeLevel * 400

    val prototypeResearchCost: Int = 3500

    /**
     * Forges a specific weapon on [unit] up to Rank 5 (+260 Power, +4% Hit, +3% Crit per rank).
     */
    fun forgeWeapon(
        unit: MechaUnit,
        weaponId: String,
        availableCredits: Int
    ): DevelopmentOutcome {
        val targetWeapon = unit.weapons.find { it.id == weaponId }
            ?: return failure(unit, availableCredits, "Weapon not found on ${unit.name}.")

        if (targetWeapon.forgeLevel >= 5) {
            return failure(unit, availableCredits, "${targetWeapon.name} is already forged to MAX Rank (+5).")
        }

        val cost = getWeaponForgeCost(targetWeapon.forgeLevel)
        if (availableCredits < cost) {
            return failure(unit, availableCredits, "Need $cost CR to forge ${targetWeapon.name}.")
        }

        val nextRank = targetWeapon.forgeLevel + 1
        val forgedWeapon = targetWeapon.copy(forgeLevel = nextRank)
        val updatedUnit = unit.copy(
            weapons = unit.weapons.map { if (it.id == weaponId) forgedWeapon else it }
        )

        return DevelopmentOutcome(
            success = true,
            updatedUnit = updatedUnit,
            remainingCredits = availableCredits - cost,
            costSpent = cost,
            headline = "Forged ${forgedWeapon.name} +$nextRank!",
            detail = "Power increased to ${forgedWeapon.effectivePower(updatedUnit.upgradeWeapon, updatedUnit.magicLevel)}, Hit +${forgedWeapon.effectiveHitBonus}%, Crit +${forgedWeapon.effectiveCritBonus}%."
        )
    }

    /**
     * Returns the developable Prototype Finisher Weapon blueprint for a given unit.
     */
    fun getPrototypeWeaponBlueprint(unit: MechaUnit): Weapon = when (unit.id) {
        "ally_kaiser" -> Weapon(
            id = "w_proto_kaiser_ragnarok",
            name = "Final Kaiser Ragnarok",
            basePower = 6200,
            minRange = 1,
            maxRange = 3,
            enCost = 60,
            maxAmmo = 0,
            currentAmmo = 0,
            willReq = 115,
            isPostMove = true,
            critBonus = 40,
            hitBonus = 35,
            animStyle = AnimationStyle.KAISER_NOVA_BREAKER,
            isMelee = true,
            isPrototypeUnlock = true
        )
        "ally_astral" -> Weapon(
            id = "w_proto_astral_quantum",
            name = "Quantum Hi-Mega Fin-Burst",
            basePower = 5700,
            minRange = 1,
            maxRange = 6,
            enCost = 45,
            maxAmmo = 0,
            currentAmmo = 0,
            willReq = 110,
            isPostMove = true,
            critBonus = 40,
            hitBonus = 42,
            animStyle = AnimationStyle.FIN_FUNNEL_SWARM,
            isMelee = false,
            isPrototypeUnlock = true
        )
        "ally_valkyrie" -> Weapon(
            id = "w_proto_valk_reaction",
            name = "Reaction Warhead Salvo",
            basePower = 5300,
            minRange = 1,
            maxRange = 5,
            enCost = 35,
            maxAmmo = 4,
            currentAmmo = 4,
            willReq = 110,
            isPostMove = true,
            critBonus = 35,
            hitBonus = 38,
            animStyle = AnimationStyle.MACROSS_MISSILE_STORM,
            isMelee = false,
            isPrototypeUnlock = true
        )
        else -> Weapon(
            id = "w_proto_aegis_genesis",
            name = "Aegis Genesis Singularity Cannon",
            basePower = 6400,
            minRange = 2,
            maxRange = 7,
            enCost = 65,
            maxAmmo = 0,
            currentAmmo = 0,
            willReq = 115,
            isPostMove = true,
            critBonus = 30,
            hitBonus = 35,
            animStyle = AnimationStyle.GRAVITY_SINGULARITY_CANNON,
            isMelee = false,
            isPrototypeUnlock = true
        )
    }

    /**
     * Researches and mounts the unit's Ultimate Prototype Finisher Weapon.
     */
    fun developPrototypeWeapon(
        unit: MechaUnit,
        availableCredits: Int
    ): DevelopmentOutcome {
        if (unit.unlockedPrototypeWeapon) {
            return failure(unit, availableCredits, "Prototype weapon already mounted on ${unit.name}.")
        }
        if (availableCredits < prototypeResearchCost) {
            return failure(unit, availableCredits, "Need $prototypeResearchCost CR to develop Prototype Weapon.")
        }

        val proto = getPrototypeWeaponBlueprint(unit)
        val updatedUnit = unit.copy(
            weapons = unit.weapons + proto,
            unlockedPrototypeWeapon = true
        )

        return DevelopmentOutcome(
            success = true,
            updatedUnit = updatedUnit,
            remainingCredits = availableCredits - prototypeResearchCost,
            costSpent = prototypeResearchCost,
            headline = "PROTOTYPE WEAPON DEVELOPED: ${proto.name}!",
            detail = "Mounted ${proto.name} (PWR ${proto.basePower}, RNG ${proto.minRange}~${proto.maxRange}) onto ${unit.name}."
        )
    }

    // =========================================================================
    // 3. ARMOR & BARRIER DEVELOPMENT
    // =========================================================================

    fun getBarrierUpgradeCost(currentBarrierLevel: Int): Int = 1400 + currentBarrierLevel * 400

    /**
     * Installs a new [ArmorPlatingTier] on [unit], granting bonus armor, % damage reduction, and barrier capacity.
     */
    fun installArmorPlating(
        unit: MechaUnit,
        targetTier: ArmorPlatingTier,
        availableCredits: Int
    ): DevelopmentOutcome {
        if (unit.armorPlating == targetTier) {
            return failure(unit, availableCredits, "${targetTier.displayName} is already installed.")
        }
        if (targetTier.level < unit.armorPlating.level) {
            return failure(unit, availableCredits, "Higher grade armor plating is already installed.")
        }
        if (availableCredits < targetTier.creditCost) {
            return failure(unit, availableCredits, "Need ${targetTier.creditCost} CR for ${targetTier.displayName}.")
        }

        val updated = unit.copy(armorPlating = targetTier)
        return DevelopmentOutcome(
            success = true,
            updatedUnit = updated,
            remainingCredits = availableCredits - targetTier.creditCost,
            costSpent = targetTier.creditCost,
            headline = "Installed ${targetTier.displayName} on ${unit.name}!",
            detail = targetTier.summary
        )
    }

    /**
     * Upgrades the unit's Energy/Arcane Barrier Field Generator (`0..5`), adding +160 Barrier Threshold per rank.
     */
    fun upgradeBarrierGenerator(
        unit: MechaUnit,
        availableCredits: Int
    ): DevelopmentOutcome {
        if (unit.barrierUpgradeLevel >= 5) {
            return failure(unit, availableCredits, "${unit.name} Barrier Generator is already at Lv.5.")
        }

        val cost = getBarrierUpgradeCost(unit.barrierUpgradeLevel)
        if (availableCredits < cost) {
            return failure(unit, availableCredits, "Need $cost CR to upgrade Barrier Generator.")
        }

        val nextLevel = unit.barrierUpgradeLevel + 1
        val updated = unit.copy(
            barrierUpgradeLevel = nextLevel,
            barrierName = unit.barrierName ?: "Aegis Prana Field"
        )

        return DevelopmentOutcome(
            success = true,
            updatedUnit = updated,
            remainingCredits = availableCredits - cost,
            costSpent = cost,
            headline = "Upgraded ${updated.effectiveBarrierName} to Lv.$nextLevel!",
            detail = "Barrier now absorbs up to ${updated.effectiveBarrierThreshold} DMG per hit."
        )
    }

    // =========================================================================
    // 4. MAGIC & PRANA ETHER DEVELOPMENT SYSTEM
    // =========================================================================

    fun getMagicConduitUpgradeCost(currentMagicLevel: Int): Int = 1500 + (currentMagicLevel - 1) * 500

    val elementalAttuneCost: Int = 1000

    /**
     * Upgrades the unit's Arcane Prana Conduit (`magicLevel` 1 -> 5):
     * - Increases Pilot Max SP by +20 and restores +20 SP immediately.
     * - Boosts Arcane Spell Weapon power by +320 per level.
     * - At Magic Lv.2, automatically mounts an Elemental Arcane Spell Weapon onto the mecha!
     */
    fun upgradeMagicConduit(
        unit: MechaUnit,
        availableCredits: Int
    ): DevelopmentOutcome {
        if (unit.magicLevel >= 5) {
            return failure(unit, availableCredits, "${unit.name} Magic Conduit is already at Master Level 5.")
        }

        val cost = getMagicConduitUpgradeCost(unit.magicLevel)
        if (availableCredits < cost) {
            return failure(unit, availableCredits, "Need $cost CR to upgrade Magic Conduit.")
        }

        val nextLevel = unit.magicLevel + 1
        val arcaneWeaponId = "w_arcane_${unit.id}"
        val hasArcaneWeapon = unit.weapons.any { it.id == arcaneWeaponId }

        val updatedWeapons = if (!hasArcaneWeapon && nextLevel >= 2) {
            unit.weapons + createArcaneSpellWeapon(unit, nextLevel)
        } else {
            unit.weapons
        }

        // Auto-unlock tier-matched spell if not unlocked yet
        val autoSpell = MagicSpell.entries.find { it.requiredMagicLevel == nextLevel }
        val updatedSpells = if (autoSpell != null) unit.unlockedSpells + autoSpell else unit.unlockedSpells

        val updatedUnit = unit.copy(
            magicLevel = nextLevel,
            weapons = updatedWeapons,
            unlockedSpells = updatedSpells,
            pilot = unit.pilot.copy(
                currentSp = unit.pilot.currentSp + 20
            )
        )

        return DevelopmentOutcome(
            success = true,
            updatedUnit = updatedUnit,
            remainingCredits = availableCredits - cost,
            costSpent = cost,
            headline = "Arcane Magic Conduit upgraded to Lv.$nextLevel!",
            detail = buildString {
                append("Max SP increased to ${updatedUnit.effectiveMaxSp}. ")
                if (!hasArcaneWeapon && nextLevel >= 2) {
                    append("Mounted Arcane Spell Weapon [Prana Astral Flare]! ")
                }
                if (autoSpell != null) {
                    append("Unlocked Spell [${autoSpell.codeName} (${autoSpell.jpName})]!")
                }
            }
        )
    }

    private fun createArcaneSpellWeapon(unit: MechaUnit, magicLevel: Int): Weapon = Weapon(
        id = "w_arcane_${unit.id}",
        name = "Prana ${unit.elementalAffinity.displayName} Cannon",
        basePower = 4200,
        minRange = 1,
        maxRange = 5,
        enCost = 25,
        maxAmmo = 0,
        currentAmmo = 0,
        willReq = 100,
        isPostMove = true,
        critBonus = 30,
        hitBonus = 32,
        animStyle = AnimationStyle.GRAVITY_SINGULARITY_CANNON,
        isMelee = false,
        isArcaneMagic = true
    )

    /**
     * Attunes [unit] to a new [ElementalAffinity] (`SOLAR_FIRE`, `ASTRAL_WIND`, `CHRONO_LIGHTNING`, `ABYSSAL_GRAVITY`).
     */
    fun attuneElementalAffinity(
        unit: MechaUnit,
        affinity: ElementalAffinity,
        availableCredits: Int
    ): DevelopmentOutcome {
        if (unit.elementalAffinity == affinity) {
            return failure(unit, availableCredits, "${unit.name} is already attuned to ${affinity.displayName}.")
        }
        if (availableCredits < elementalAttuneCost) {
            return failure(unit, availableCredits, "Need $elementalAttuneCost CR to attune Elemental Prana.")
        }

        // Update Arcane Spell Weapon name if mounted
        val arcaneId = "w_arcane_${unit.id}"
        val updatedWeapons = unit.weapons.map { w ->
            if (w.id == arcaneId) w.copy(name = "Prana ${affinity.displayName} Cannon") else w
        }

        val updated = unit.copy(
            elementalAffinity = affinity,
            weapons = updatedWeapons
        )

        return DevelopmentOutcome(
            success = true,
            updatedUnit = updated,
            remainingCredits = availableCredits - elementalAttuneCost,
            costSpent = elementalAttuneCost,
            headline = "Attuned ${unit.name} to [${affinity.displayName} / ${affinity.kanji}]!",
            detail = affinity.summary
        )
    }

    /**
     * Unlocks a [MagicSpell] in the unit's Arcane Grimoire.
     */
    fun researchMagicSpell(
        unit: MechaUnit,
        spell: MagicSpell,
        availableCredits: Int
    ): DevelopmentOutcome {
        if (spell in unit.unlockedSpells) {
            return failure(unit, availableCredits, "${spell.codeName} is already inscribed in ${unit.pilot.name}'s Grimoire.")
        }
        if (unit.magicLevel < spell.requiredMagicLevel) {
            return failure(unit, availableCredits, "Requires Magic Conduit Lv.${spell.requiredMagicLevel} to research ${spell.codeName}.")
        }
        if (availableCredits < spell.researchCreditCost) {
            return failure(unit, availableCredits, "Need ${spell.researchCreditCost} CR to research ${spell.codeName}.")
        }

        val updated = unit.copy(unlockedSpells = unit.unlockedSpells + spell)
        return DevelopmentOutcome(
            success = true,
            updatedUnit = updated,
            remainingCredits = availableCredits - spell.researchCreditCost,
            costSpent = spell.researchCreditCost,
            headline = "Inscribed Spell [${spell.codeName} (${spell.jpName})]!",
            detail = spell.summary
        )
    }

    /**
     * Casts an unlocked [MagicSpell] during tactical combat using the pilot's Mana/SP pool.
     */
    fun castMagicSpell(unit: MechaUnit, spell: MagicSpell): MechaUnit? {
        if (spell !in unit.unlockedSpells) return null
        if (unit.pilot.currentSp < spell.manaSpCost) return null

        val remainingSp = unit.pilot.currentSp - spell.manaSpCost

        return when (spell) {
            MagicSpell.PRANA_OVERDRIVE -> unit.copy(
                activeSpirits = unit.activeSpirits + setOf(SpiritCommand.VALOR, SpiritCommand.FOCUS),
                pilot = unit.pilot.copy(
                    currentSp = remainingSp,
                    will = min(170, unit.pilot.will + 12)
                )
            )
            MagicSpell.AEGIS_SANCTUARY -> unit.copy(
                currentHp = min(unit.maxHp, unit.currentHp + 3200),
                currentEn = min(unit.maxEn, unit.currentEn + 50),
                activeSpirits = unit.activeSpirits + SpiritCommand.IRON_WALL,
                pilot = unit.pilot.copy(currentSp = remainingSp)
            )
            MagicSpell.STARLIGHT_BLINK -> unit.copy(
                activeSpirits = unit.activeSpirits + setOf(
                    SpiritCommand.ACCEL,
                    SpiritCommand.ALERT,
                    SpiritCommand.STRIKE
                ),
                pilot = unit.pilot.copy(currentSp = remainingSp)
            )
            MagicSpell.SOLAR_FLARE_NOVA -> unit.copy(
                currentEn = min(unit.maxEn, unit.currentEn + 40),
                activeSpirits = unit.activeSpirits + SpiritCommand.SOUL,
                pilot = unit.pilot.copy(
                    currentSp = remainingSp,
                    will = min(170, unit.pilot.will + 15)
                )
            )
        }
    }

    private fun failure(unit: MechaUnit, credits: Int, reason: String): DevelopmentOutcome =
        DevelopmentOutcome(
            success = false,
            updatedUnit = unit,
            remainingCredits = credits,
            costSpent = 0,
            headline = "Development Halted",
            detail = reason
        )
}
