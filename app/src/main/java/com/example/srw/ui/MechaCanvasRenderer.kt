package com.example.srw.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.srw.model.Faction
import com.example.srw.model.MechaVisualRig
import kotlin.math.cos
import kotlin.math.sin

object MechaCanvasRenderer {

    /**
     * Draws a crisp tactical map icon/sprite for a mecha unit inside a grid cell.
     */
    fun DrawScope.drawMapUnitSprite(
        rig: MechaVisualRig,
        faction: Faction,
        hasActed: Boolean,
        pulsePhase: Float
    ) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        val basePrimary = when (rig) {
            MechaVisualRig.KAISER_BLAZE -> Color(0xFFFF3D00)
            MechaVisualRig.ASTRAL_GUNDAM -> Color(0xFF00E5FF)
            MechaVisualRig.VALKYRIE_ZERO -> Color(0xFFFFD600)
            MechaVisualRig.AEGIS_BATTLESHIP -> Color(0xFF7C4DFF)
            MechaVisualRig.ZAKU_VANGUARD -> Color(0xFFEF5350)
            MechaVisualRig.GRAV_DESTROYER -> Color(0xFFFF7043)
            MechaVisualRig.EMPEROR_ZEPH -> Color(0xFFD500F9)
        }

        val primaryColor = if (hasActed) basePrimary.copy(alpha = 0.45f) else basePrimary
        val factionRingColor = if (faction == Faction.ALLY) Color(0xFF00E5FF) else Color(0xFFFF2A54)

        // Base tactical faction hex/circle ring
        drawCircle(
            color = factionRingColor.copy(alpha = if (hasActed) 0.25f else 0.4f + 0.15f * sin(pulsePhase)),
            radius = w * 0.42f
        )
        drawCircle(
            color = factionRingColor.copy(alpha = if (hasActed) 0.4f else 0.9f),
            radius = w * 0.42f,
            style = Stroke(width = w * 0.05f)
        )

        when (rig) {
            MechaVisualRig.KAISER_BLAZE -> {
                // Winged Super Robot crest + solar chest core
                val wingPath = Path().apply {
                    moveTo(cx, cy - h * 0.22f)
                    lineTo(cx - w * 0.34f, cy - h * 0.34f)
                    lineTo(cx - w * 0.26f, cy + h * 0.18f)
                    lineTo(cx, cy + h * 0.28f)
                    lineTo(cx + w * 0.26f, cy + h * 0.18f)
                    lineTo(cx + w * 0.34f, cy - h * 0.34f)
                    close()
                }
                drawPath(wingPath, color = primaryColor)
                // Golden V-Horn
                val hornPath = Path().apply {
                    moveTo(cx, cy - h * 0.14f)
                    lineTo(cx - w * 0.24f, cy - h * 0.38f)
                    lineTo(cx, cy - h * 0.24f)
                    lineTo(cx + w * 0.24f, cy - h * 0.38f)
                    close()
                }
                drawPath(hornPath, color = Color(0xFFFFEA00))
                // Solar Core
                drawCircle(
                    color = Color(0xFF00E5FF),
                    radius = w * 0.11f,
                    center = Offset(cx, cy + h * 0.04f)
                )
            }

            MechaVisualRig.ASTRAL_GUNDAM -> {
                // Fin-Funnel back binder silhouette + Gundam V-Fin
                for (i in -2..2) {
                    if (i == 0) continue
                    drawRoundRect(
                        color = Color(0xFFE0F7FA),
                        topLeft = Offset(cx + i * w * 0.11f - w * 0.03f, cy - h * 0.34f),
                        size = Size(w * 0.06f, h * 0.36f),
                        cornerRadius = CornerRadius(2f, 2f)
                    )
                }
                drawRoundRect(
                    color = primaryColor,
                    topLeft = Offset(cx - w * 0.18f, cy - h * 0.12f),
                    size = Size(w * 0.36f, h * 0.34f),
                    cornerRadius = CornerRadius(4f, 4f)
                )
                // V-Fin
                drawLine(
                    color = Color(0xFFFFEA00),
                    start = Offset(cx, cy - h * 0.12f),
                    end = Offset(cx - w * 0.25f, cy - h * 0.32f),
                    strokeWidth = w * 0.06f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color(0xFFFFEA00),
                    start = Offset(cx, cy - h * 0.12f),
                    end = Offset(cx + w * 0.25f, cy - h * 0.32f),
                    strokeWidth = w * 0.06f,
                    cap = StrokeCap.Round
                )
            }

            MechaVisualRig.VALKYRIE_ZERO -> {
                // Swept-wing variable fighter / battroid silhouette
                val jetPath = Path().apply {
                    moveTo(cx, cy - h * 0.38f)
                    lineTo(cx - w * 0.34f, cy + h * 0.12f)
                    lineTo(cx - w * 0.12f, cy + h * 0.16f)
                    lineTo(cx - w * 0.18f, cy + h * 0.34f)
                    lineTo(cx, cy + h * 0.22f)
                    lineTo(cx + w * 0.18f, cy + h * 0.34f)
                    lineTo(cx + w * 0.12f, cy + h * 0.16f)
                    lineTo(cx + w * 0.34f, cy + h * 0.12f)
                    close()
                }
                drawPath(jetPath, color = Color(0xFFECEFF1))
                drawLine(
                    color = primaryColor,
                    start = Offset(cx, cy - h * 0.30f),
                    end = Offset(cx, cy + h * 0.20f),
                    strokeWidth = w * 0.08f
                )
            }

            MechaVisualRig.AEGIS_BATTLESHIP -> {
                // Space Battleship twin-prow hull
                val hullPath = Path().apply {
                    moveTo(cx - w * 0.12f, cy - h * 0.38f)
                    lineTo(cx - w * 0.32f, cy + h * 0.28f)
                    lineTo(cx, cy + h * 0.36f)
                    lineTo(cx + w * 0.32f, cy + h * 0.28f)
                    lineTo(cx + w * 0.12f, cy - h * 0.38f)
                    lineTo(cx, cy - h * 0.15f)
                    close()
                }
                drawPath(hullPath, color = primaryColor)
                drawCircle(
                    color = Color(0xFF18FFFF),
                    radius = w * 0.10f,
                    center = Offset(cx, cy + h * 0.05f)
                )
            }

            MechaVisualRig.ZAKU_VANGUARD -> {
                // Spiked shoulder dome + glowing Crimson Mono-Eye
                drawCircle(
                    color = Color(0xFF37474F),
                    radius = w * 0.28f,
                    center = Offset(cx, cy)
                )
                drawRect(
                    color = Color(0xFF102027),
                    topLeft = Offset(cx - w * 0.22f, cy - h * 0.08f),
                    size = Size(w * 0.44f, h * 0.14f)
                )
                // Glowing Mono-Eye
                drawCircle(
                    color = Color(0xFFFF1744),
                    radius = w * 0.09f,
                    center = Offset(cx, cy - h * 0.01f)
                )
            }

            MechaVisualRig.GRAV_DESTROYER -> {
                // Heavy Twin-Cannon Dreadnought Mech
                drawRoundRect(
                    color = primaryColor,
                    topLeft = Offset(cx - w * 0.30f, cy - h * 0.22f),
                    size = Size(w * 0.60f, h * 0.48f),
                    cornerRadius = CornerRadius(6f, 6f)
                )
                drawCircle(
                    color = Color(0xFFFFEA00),
                    radius = w * 0.12f,
                    center = Offset(cx, cy)
                )
            }

            MechaVisualRig.EMPEROR_ZEPH -> {
                // Boss Dark Emperor crown + singularity core
                val crown = Path().apply {
                    moveTo(cx, cy - h * 0.42f)
                    lineTo(cx - w * 0.36f, cy - h * 0.20f)
                    lineTo(cx - w * 0.26f, cy + h * 0.30f)
                    lineTo(cx, cy + h * 0.38f)
                    lineTo(cx + w * 0.26f, cy + h * 0.30f)
                    lineTo(cx + w * 0.36f, cy - h * 0.20f)
                    close()
                }
                drawPath(crown, color = primaryColor)
                drawCircle(
                    color = Color(0xFFFF1744),
                    radius = w * 0.14f,
                    center = Offset(cx, cy)
                )
                drawCircle(
                    color = Color(0xFFFFEA00),
                    radius = w * 0.14f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 3f)
                )
            }
        }
    }

    /**
     * Draws an articulated side-view battle mecha rig for the Cinematic Battle Engine.
     * facingRight = true for left-side units (or flipped for right-side units).
     */
    fun DrawScope.drawCinematicMechaRig(
        rig: MechaVisualRig,
        center: Offset,
        scale: Float,
        facingRight: Boolean,
        poseTiltDegrees: Float,
        auraIntensity: Float,
        auraColor: Color,
        weaponActive: Boolean,
        drawMeleeBlade: Boolean
    ) {
        val dir = if (facingRight) 1f else -1f

        // Spirit / Solar Aura behind the mecha
        if (auraIntensity > 0.05f) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        auraColor.copy(alpha = 0.65f * auraIntensity),
                        auraColor.copy(alpha = 0.20f * auraIntensity),
                        Color.Transparent
                    ),
                    center = center,
                    radius = 125f * scale * (0.9f + 0.25f * auraIntensity)
                ),
                radius = 125f * scale * (0.9f + 0.25f * auraIntensity),
                center = center
            )
        }

        withTransform({
            translate(center.x, center.y)
            rotate(poseTiltDegrees * dir, Offset.Zero)
            scale(scaleX = dir * scale, scaleY = scale, pivot = Offset.Zero)
        }) {
            // Back vernier thruster flare
            val thrusterFlame = Path().apply {
                moveTo(-36f, -10f)
                lineTo(-92f, -26f)
                lineTo(-76f, -4f)
                lineTo(-105f, 8f)
                lineTo(-36f, 16f)
                close()
            }
            drawPath(
                path = thrusterFlame,
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, Color(0xFF00E5FF), Color.White)
                )
            )

            when (rig) {
                MechaVisualRig.KAISER_BLAZE -> drawKaiserBlazeRig(weaponActive, drawMeleeBlade)
                MechaVisualRig.ASTRAL_GUNDAM -> drawAstralGundamRig(weaponActive, drawMeleeBlade)
                MechaVisualRig.VALKYRIE_ZERO -> drawValkyrieZeroRig(weaponActive, drawMeleeBlade)
                MechaVisualRig.AEGIS_BATTLESHIP -> drawAegisBattleshipRig(weaponActive)
                MechaVisualRig.ZAKU_VANGUARD -> drawZakuVanguardRig(weaponActive, drawMeleeBlade)
                MechaVisualRig.GRAV_DESTROYER -> drawGravDestroyerRig(weaponActive)
                MechaVisualRig.EMPEROR_ZEPH -> drawEmperorZephRig(weaponActive, drawMeleeBlade)
            }
        }
    }

    private fun DrawScope.drawKaiserBlazeRig(weaponActive: Boolean, drawMeleeBlade: Boolean) {
        // Crimson Scramble Wings
        val wing = Path().apply {
            moveTo(-12f, -18f)
            lineTo(-75f, -78f)
            lineTo(-52f, -10f)
            lineTo(-68f, 34f)
            lineTo(-15f, 12f)
            close()
        }
        drawPath(wing, Color(0xFFD50000))
        drawPath(wing, Color(0xFFFFEA00), style = Stroke(width = 3f))

        // Legs
        drawRoundRect(
            color = Color(0xFF263238),
            topLeft = Offset(-22f, 28f),
            size = Size(18f, 52f),
            cornerRadius = CornerRadius(4f, 4f)
        )
        drawRoundRect(
            color = Color(0xFFB71C1C),
            topLeft = Offset(4f, 26f),
            size = Size(20f, 54f),
            cornerRadius = CornerRadius(4f, 4f)
        )

        // Torso & Solar Chest Fire Plate
        drawRoundRect(
            color = Color(0xFF1C2636),
            topLeft = Offset(-28f, -34f),
            size = Size(56f, 64f),
            cornerRadius = CornerRadius(8f, 8f)
        )
        val chestPlate = Path().apply {
            moveTo(-22f, -24f)
            lineTo(28f, -28f)
            lineTo(22f, 6f)
            lineTo(4f, 18f)
            lineTo(-20f, 8f)
            close()
        }
        drawPath(chestPlate, Color(0xFFFF3D00))
        drawCircle(
            color = if (weaponActive) Color(0xFFFFEA00) else Color(0xFF00E5FF),
            radius = 11f,
            center = Offset(8f, -8f)
        )

        // Spiked Super Robot Pauldron
        val shoulder = Path().apply {
            moveTo(-34f, -38f)
            lineTo(0f, -54f)
            lineTo(28f, -36f)
            lineTo(14f, -14f)
            lineTo(-28f, -16f)
            close()
        }
        drawPath(shoulder, Color(0xFFD50000))
        drawPath(shoulder, Color(0xFFFFC400), style = Stroke(width = 2.5f))

        // Head & Golden Kaiser V-Crest
        drawRoundRect(
            color = Color(0xFFECEFF1),
            topLeft = Offset(-12f, -60f),
            size = Size(26f, 24f),
            cornerRadius = CornerRadius(4f, 4f)
        )
        drawRect(
            color = Color(0xFFFFEA00),
            topLeft = Offset(-2f, -52f),
            size = Size(16f, 6f)
        )
        val horn = Path().apply {
            moveTo(2f, -56f)
            lineTo(-28f, -86f)
            lineTo(8f, -66f)
            lineTo(36f, -84f)
            lineTo(14f, -56f)
            close()
        }
        drawPath(horn, Color(0xFFFFD600))

        // Forward Arm + Giant Sun-Breaker Greatsword
        drawRoundRect(
            color = Color(0xFFECEFF1),
            topLeft = Offset(14f, -18f),
            size = Size(34f, 16f),
            cornerRadius = CornerRadius(4f, 4f)
        )
        if (drawMeleeBlade || weaponActive) {
            val blade = Path().apply {
                moveTo(42f, -16f)
                lineTo(138f, -42f)
                lineTo(154f, -8f)
                lineTo(132f, 8f)
                lineTo(42f, 2f)
                close()
            }
            drawPath(
                path = blade,
                brush = Brush.horizontalGradient(
                    listOf(Color(0xFFFF6D00), Color(0xFFFFEA00), Color.White)
                )
            )
        }
    }

    private fun DrawScope.drawAstralGundamRig(weaponActive: Boolean, drawMeleeBlade: Boolean) {
        // 6 Fin-Funnels on back rack
        for (i in 0..4) {
            val offsetX = -46f - i * 8f
            val offsetY = -58f + i * 12f
            drawRoundRect(
                color = Color(0xFFECEFF1),
                topLeft = Offset(offsetX, offsetY),
                size = Size(12f, 46f),
                cornerRadius = CornerRadius(2f, 2f)
            )
            drawRect(
                color = Color(0xFF00E5FF),
                topLeft = Offset(offsetX + 3f, offsetY + 8f),
                size = Size(6f, 30f)
            )
        }

        // Legs
        drawRoundRect(
            color = Color(0xFFCFD8DC),
            topLeft = Offset(-18f, 26f),
            size = Size(16f, 52f),
            cornerRadius = CornerRadius(3f, 3f)
        )
        drawRoundRect(
            color = Color(0xFFFFFFFF),
            topLeft = Offset(6f, 26f),
            size = Size(16f, 54f),
            cornerRadius = CornerRadius(3f, 3f)
        )

        // Torso (Classic Navy/White/Yellow V-vent)
        drawRoundRect(
            color = Color(0xFF1A237E),
            topLeft = Offset(-22f, -32f),
            size = Size(46f, 58f),
            cornerRadius = CornerRadius(6f, 6f)
        )
        drawRect(
            color = Color(0xFFFFD600),
            topLeft = Offset(-12f, -20f),
            size = Size(12f, 10f)
        )
        drawRect(
            color = Color(0xFFFFD600),
            topLeft = Offset(6f, -20f),
            size = Size(12f, 10f)
        )

        // Head + V-Fin + Green Eyes
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(-10f, -56f),
            size = Size(24f, 22f),
            cornerRadius = CornerRadius(4f, 4f)
        )
        drawRect(
            color = Color(0xFF00E676),
            topLeft = Offset(2f, -48f),
            size = Size(12f, 5f)
        )
        drawLine(
            color = Color(0xFFFFD600),
            start = Offset(4f, -54f),
            end = Offset(-22f, -78f),
            strokeWidth = 4f
        )
        drawLine(
            color = Color(0xFFFFD600),
            start = Offset(4f, -54f),
            end = Offset(26f, -78f),
            strokeWidth = 4f
        )

        // Weapon: Plasma Beam Saber OR High-Output Beam Rifle
        if (drawMeleeBlade) {
            drawLine(
                color = Color(0xFFFF4081),
                start = Offset(32f, -10f),
                end = Offset(132f, -56f),
                strokeWidth = 11f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White,
                start = Offset(32f, -10f),
                end = Offset(128f, -54f),
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )
        } else {
            // Beam Rifle + Shield
            drawRoundRect(
                color = Color(0xFF37474F),
                topLeft = Offset(20f, -18f),
                size = Size(58f, 14f),
                cornerRadius = CornerRadius(3f, 3f)
            )
            drawRect(
                color = Color(0xFF00E5FF),
                topLeft = Offset(38f, -15f),
                size = Size(26f, 5f)
            )
        }
    }

    private fun DrawScope.drawValkyrieZeroRig(weaponActive: Boolean, drawMeleeBlade: Boolean) {
        // Variable Swept Wings on Back
        val sweptWing = Path().apply {
            moveTo(-12f, -24f)
            lineTo(-78f, -68f)
            lineTo(-64f, -12f)
            lineTo(-12f, 4f)
            close()
        }
        drawPath(sweptWing, Color(0xFFECEFF1))
        drawLine(
            color = Color(0xFFFFC400),
            start = Offset(-18f, -22f),
            end = Offset(-72f, -58f),
            strokeWidth = 5f
        )

        // Agile Battroid Frame
        drawRoundRect(
            color = Color(0xFFCFD8DC),
            topLeft = Offset(-18f, -28f),
            size = Size(38f, 54f),
            cornerRadius = CornerRadius(6f, 6f)
        )
        drawRoundRect(
            color = Color(0xFF90A4AE),
            topLeft = Offset(-14f, 26f),
            size = Size(14f, 50f),
            cornerRadius = CornerRadius(3f, 3f)
        )
        drawRoundRect(
            color = Color(0xFFECEFF1),
            topLeft = Offset(6f, 26f),
            size = Size(14f, 50f),
            cornerRadius = CornerRadius(3f, 3f)
        )

        // Skull Squadron Visor Head + Twin Head Lasers
        drawRoundRect(
            color = Color(0xFF263238),
            topLeft = Offset(-8f, -50f),
            size = Size(22f, 20f),
            cornerRadius = CornerRadius(4f, 4f)
        )
        drawRect(
            color = Color(0xFF00E5FF),
            topLeft = Offset(0f, -44f),
            size = Size(14f, 6f)
        )

        // GU-11 Gunpod
        drawRoundRect(
            color = Color(0xFF455A64),
            topLeft = Offset(16f, -14f),
            size = Size(54f, 14f),
            cornerRadius = CornerRadius(4f, 4f)
        )
        if (drawMeleeBlade) {
            drawCircle(
                color = Color(0xFF00E5FF).copy(alpha = 0.8f),
                radius = 22f,
                center = Offset(58f, -7f)
            )
        }
    }

    private fun DrawScope.drawAegisBattleshipRig(weaponActive: Boolean) {
        // Colossal Command Carrier Twin-Boom Hull
        val mainHull = Path().apply {
            moveTo(-85f, -16f)
            lineTo(45f, -26f)
            lineTo(95f, -8f)
            lineTo(75f, 18f)
            lineTo(-75f, 24f)
            close()
        }
        drawPath(mainHull, Color(0xFF311B92))
        drawPath(mainHull, Color(0xFF00E5FF), style = Stroke(width = 3f))

        // Command Bridge Tower
        val bridge = Path().apply {
            moveTo(-25f, -24f)
            lineTo(-10f, -62f)
            lineTo(18f, -58f)
            lineTo(24f, -24f)
            close()
        }
        drawPath(bridge, Color(0xFF5E35B1))
        drawRect(
            color = Color(0xFF18FFFF),
            topLeft = Offset(-4f, -52f),
            size = Size(18f, 7f)
        )

        // Forward Graviton Cannon Barrel Glow
        if (weaponActive) {
            drawCircle(
                color = Color(0xFF18FFFF),
                radius = 18f,
                center = Offset(92f, -6f)
            )
        }
    }

    private fun DrawScope.drawZakuVanguardRig(weaponActive: Boolean, drawMeleeBlade: Boolean) {
        // Spiked Pauldron + Olive/Crimson Armored Frame
        drawRoundRect(
            color = Color(0xFF4E342E),
            topLeft = Offset(-24f, -30f),
            size = Size(48f, 58f),
            cornerRadius = CornerRadius(8f, 8f)
        )
        drawRoundRect(
            color = Color(0xFF3E2723),
            topLeft = Offset(-18f, 26f),
            size = Size(18f, 48f),
            cornerRadius = CornerRadius(4f, 4f)
        )
        drawRoundRect(
            color = Color(0xFF5D4037),
            topLeft = Offset(4f, 26f),
            size = Size(18f, 48f),
            cornerRadius = CornerRadius(4f, 4f)
        )
        // Dome Head + Glowing Mono-Eye
        drawCircle(
            color = Color(0xFF8D6E63),
            radius = 16f,
            center = Offset(0f, -44f)
        )
        drawRect(
            color = Color(0xFF1B0000),
            topLeft = Offset(-12f, -48f),
            size = Size(26f, 8f)
        )
        drawCircle(
            color = Color(0xFFFF1744),
            radius = 5f,
            center = Offset(6f, -44f)
        )
        // Thermal Heat Axe or Mag-Rifle
        if (drawMeleeBlade) {
            drawLine(
                color = Color(0xFFFF9100),
                start = Offset(24f, -12f),
                end = Offset(68f, -54f),
                strokeWidth = 8f
            )
        } else {
            drawRoundRect(
                color = Color(0xFF263238),
                topLeft = Offset(18f, -16f),
                size = Size(50f, 14f),
                cornerRadius = CornerRadius(3f, 3f)
            )
        }
    }

    private fun DrawScope.drawGravDestroyerRig(weaponActive: Boolean) {
        // Bulky Dreadnought Super Mech
        drawRoundRect(
            color = Color(0xFFBF360C),
            topLeft = Offset(-34f, -38f),
            size = Size(68f, 68f),
            cornerRadius = CornerRadius(10f, 10f)
        )
        drawCircle(
            color = if (weaponActive) Color(0xFFFFEA00) else Color(0xFFFF6D00),
            radius = 16f,
            center = Offset(6f, -6f)
        )
        // Twin Shoulder Graviton Cannons
        drawRoundRect(
            color = Color(0xFF37474F),
            topLeft = Offset(-10f, -64f),
            size = Size(64f, 16f),
            cornerRadius = CornerRadius(4f, 4f)
        )
    }

    private fun DrawScope.drawEmperorZephRig(weaponActive: Boolean, drawMeleeBlade: Boolean) {
        // Dark Emperor Wings of Calamity
        val darkWing = Path().apply {
            moveTo(-10f, -24f)
            lineTo(-92f, -88f)
            lineTo(-64f, -8f)
            lineTo(-84f, 42f)
            lineTo(-14f, 16f)
            close()
        }
        drawPath(darkWing, Color(0xFF4A148C))
        drawPath(darkWing, Color(0xFFD500F9), style = Stroke(width = 3f))

        // Obsidian & Gold Royal Armor
        drawRoundRect(
            color = Color(0xFF12002B),
            topLeft = Offset(-32f, -38f),
            size = Size(64f, 68f),
            cornerRadius = CornerRadius(8f, 8f)
        )
        drawRoundRect(
            color = Color(0xFF12002B),
            topLeft = Offset(-24f, 28f),
            size = Size(20f, 54f),
            cornerRadius = CornerRadius(4f, 4f)
        )
        drawRoundRect(
            color = Color(0xFF311B92),
            topLeft = Offset(6f, 28f),
            size = Size(20f, 54f),
            cornerRadius = CornerRadius(4f, 4f)
        )

        // Singularity Core in Chest
        drawCircle(
            color = Color(0xFFFF1744),
            radius = 15f,
            center = Offset(6f, -8f)
        )
        drawCircle(
            color = Color(0xFFFFD600),
            radius = 15f,
            center = Offset(6f, -8f),
            style = Stroke(width = 3f)
        )

        // Crowned Head
        val crown = Path().apply {
            moveTo(-12f, -42f)
            lineTo(-24f, -78f)
            lineTo(0f, -58f)
            lineTo(8f, -84f)
            lineTo(18f, -58f)
            lineTo(32f, -76f)
            lineTo(16f, -42f)
            close()
        }
        drawPath(crown, Color(0xFFFFD600))

        if (drawMeleeBlade) {
            val halberd = Path().apply {
                moveTo(30f, -14f)
                lineTo(140f, -46f)
                lineTo(124f, 14f)
                close()
            }
            drawPath(halberd, Color(0xFFD500F9))
        }
    }
}
