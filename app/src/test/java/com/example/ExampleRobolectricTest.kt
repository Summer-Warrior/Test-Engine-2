package com.example

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.srw.model.Faction
import com.example.srw.model.SpiritCommand
import com.example.srw.ui.BattleCombatActionLogCard
import com.example.srw.ui.BattleLogSeverity
import com.example.srw.ui.SideViewBattleHudPanel
import com.example.srw.ui.SideViewCombatActionEntry
import com.example.srw.ui.SideViewCombatantState
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Aegis Grid SRW Engine", appName)
    }

    @Test
    fun `side view battle hud displays health bars energy levels and combat log`() {
        var expanded by mutableStateOf(true)
        val actions = listOf(
            SideViewCombatActionEntry(
                id = "a1",
                phaseTag = "ENGAGE",
                actorName = "Kaiser Blaze",
                actionText = "Targets Zaku Commander on Urban Ridge.",
                deltaBadge = "94% HIT",
                severity = BattleLogSeverity.INFO
            ),
            SideViewCombatActionEntry(
                id = "a2",
                phaseTag = "CRIT",
                actorName = "Kaiser Blaze",
                actionText = "Kaiser Nova Breaker triggers critical overload!",
                deltaBadge = "-4,300 HP",
                severity = BattleLogSeverity.CRITICAL
            )
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                SideViewBattleHudPanel(
                    leftCombatant = SideViewCombatantState(
                        unitId = "ally_kaiser",
                        unitName = "Kaiser Blaze",
                        modelCode = "KZR-01",
                        pilotName = "Ryoma",
                        pilotLevel = 3,
                        pilotWill = 125,
                        faction = Faction.ALLY,
                        currentHp = 5800,
                        maxHp = 7200,
                        currentEn = 155,
                        maxEn = 220,
                        weaponName = "Kaiser Nova Breaker",
                        weaponEnCost = 35,
                        hitPercent = 94,
                        activeSpirits = setOf(SpiritCommand.VALOR),
                        isActiveActor = true
                    ),
                    rightCombatant = SideViewCombatantState(
                        unitId = "enemy_zaku",
                        unitName = "Zaku Commander",
                        modelCode = "MS-06S",
                        pilotName = "Char",
                        pilotLevel = 4,
                        pilotWill = 112,
                        faction = Faction.ENEMY,
                        currentHp = 2100,
                        maxHp = 6400,
                        currentEn = 95,
                        maxEn = 140,
                        weaponName = "Crimson Bazooka",
                        weaponEnCost = 20,
                        hitPercent = 58,
                        barrierActive = true
                    ),
                    combatActions = actions,
                    isLogExpanded = expanded,
                    onToggleLogExpanded = { expanded = !expanded }
                )
            }
        }

        composeTestRule.waitForIdle()

        // Verify HUD root, unit status cards, HP bars, EN bars, and readouts
        composeTestRule.onNodeWithTag("side_view_battle_hud").assertIsDisplayed()
        composeTestRule.onNodeWithTag("combatant_status_card_ally_kaiser").assertIsDisplayed()
        composeTestRule.onNodeWithTag("combatant_status_card_enemy_zaku").assertIsDisplayed()
        composeTestRule.onNodeWithTag("combatant_hp_bar_ally_kaiser").assertIsDisplayed()
        composeTestRule.onNodeWithTag("combatant_en_bar_ally_kaiser").assertIsDisplayed()
        composeTestRule.onNodeWithTag("combatant_hp_bar_enemy_zaku").assertIsDisplayed()
        composeTestRule.onNodeWithTag("combatant_en_bar_enemy_zaku").assertIsDisplayed()

        composeTestRule.onNodeWithText("Kaiser Blaze").assertIsDisplayed()
        composeTestRule.onNodeWithText("5,800 / 7,200").assertIsDisplayed()
        composeTestRule.onNodeWithText("155 / 220").assertIsDisplayed()
        composeTestRule.onNodeWithText("2,100 / 6,400").assertIsDisplayed()
        composeTestRule.onNodeWithText("95 / 140").assertIsDisplayed()

        // Verify Combat Action Log entries and collapse toggle
        composeTestRule.onNodeWithTag("battle_action_log_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("battle_log_entry_a1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("battle_log_entry_a2").assertIsDisplayed()
        composeTestRule.onNodeWithText("-4,300 HP").assertIsDisplayed()

        composeTestRule.onNodeWithTag("battle_action_log_toggle").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("battle_action_log_list").assertDoesNotExist()
    }

    @Test
    fun `combat action log card shows empty state when no actions exist`() {
        composeTestRule.setContent {
            MyApplicationTheme {
                BattleCombatActionLogCard(actions = emptyList())
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("battle_action_log_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Awaiting combat telemetry…").assertIsDisplayed()
    }
}

