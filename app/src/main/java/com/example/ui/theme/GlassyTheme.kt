package com.example.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * CompositionLocal to expose the active application theme style down the hierarchy.
 * Values: "natural" (original earthy tones) or "glassy" (WWDC25 Liquid Glass design).
 */
val LocalAppThemeStyle = staticCompositionLocalOf { "natural" }

/**
 * Custom glassy colors that align with Apple's Liquid Glass WWDC25 guidelines.
 */
object GlassyColors {
    // Dynamic glass colors
    val LightGlassBackground = Color(0xB2FFFFFF) // Translucent white
    val LightGlassBackgroundClear = Color(0x3DFFFFFF) // High-transparency clear version
    val DarkGlassBackground = Color(0x8D1A1A1E) // Translucent dark slate
    val DarkGlassBackgroundClear = Color(0x261A1A1E)

    // Specular border highlights
    val LightSpecularHighlight = Color(0xE6FFFFFF)
    val LightSpecularShadow = Color(0x1F000000)

    val DarkSpecularHighlight = Color(0x66FFFFFF)
    val DarkSpecularShadow = Color(0x40000000)

    // Vibrant tinted glass accents for focus elements
    val BlueTint = Color(0xFF0A84FF)
    val BlueTintGlass = Color(0x330A84FF)
    
    val EmeraldTint = Color(0xFF34C759)
    val EmeraldTintGlass = Color(0x3334C759)

    val WarmGoldTint = Color(0xFFFF9F0A)
    val WarmGoldTintGlass = Color(0x33FF9F0A)

    val RoseTint = Color(0xFFFF375F)
    val RoseTintGlass = Color(0x33FF375F)
}

/**
 * Renders a gorgeous dynamic "Liquid Wallpaper" backdrop with moving gaseous/liquid color blobs
 * in real-time when the theme is set to "glassy", allowing the translucent glass panels placed on
 * top of it to act like real glass, refracting and warping the background colors seamlessly.
 */
@Composable
fun GlassyBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val isGlassy = LocalAppThemeStyle.current == "glassy"
    val isDark = isSystemInDarkTheme()

    if (!isGlassy) {
        // Fall back to natural background
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            content = content
        )
        return
    }

    // Infinite animation for moving liquid color blobs
    val infiniteTransition = rememberInfiniteTransition(label = "liquid_glass_wallpaper")
    
    val animProgress1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(25000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "blob1"
    )

    val animProgress2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(32000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "blob2"
    )

    // Colors of our dynamic scene
    val baseBg = if (isDark) Color(0xFF0E0E11) else Color(0xFFF0EFF5)
    
    val colorBlob1 = if (isDark) Color(0x333A3075) else Color(0x2B7A75C7) // Indigo
    val colorBlob2 = if (isDark) Color(0x331F4F54) else Color(0x247BC6C1) // Cyan/Teal
    val colorBlob3 = if (isDark) Color(0x295C1C30) else Color(0x21E06A83) // Rose

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseBg)
            .drawBehind {
                val w = size.width
                val h = size.height
                if (w == 0f || h == 0f) return@drawBehind

                // Calculate animated positions for liquid blobs matching real-time physical movement
                val blob1X = w * (0.35f + 0.25f * cos(animProgress1))
                val blob1Y = h * (0.35f + 0.20f * sin(animProgress1))
                
                val blob2X = w * (0.65f + 0.20f * sin(animProgress2))
                val blob2Y = h * (0.60f + 0.25f * cos(animProgress2))

                val blob3X = w * (0.50f + 0.15f * cos(animProgress1 + PI.toFloat() / 2f))
                val blob3Y = h * (0.40f + 0.20f * sin(animProgress2 - PI.toFloat() / 3f))

                // Draw Blob 1 (Indigo glow)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(colorBlob1, Color.Transparent),
                        center = Offset(blob1X, blob1Y),
                        radius = w * 0.7f
                    ),
                    center = Offset(blob1X, blob1Y),
                    radius = w * 0.7f
                )

                // Draw Blob 2 (Teal flow)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(colorBlob2, Color.Transparent),
                        center = Offset(blob2X, blob2Y),
                        radius = w * 0.65f
                    ),
                    center = Offset(blob2X, blob2Y),
                    radius = w * 0.65f
                )

                // Draw Blob 3 (Rose warmth)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(colorBlob3, Color.Transparent),
                        center = Offset(blob3X, blob3Y),
                        radius = w * 0.55f
                    ),
                    center = Offset(blob3X, blob3Y),
                    radius = w * 0.55f
                )
            },
        content = content
    )
}

/**
 * Modifier that applies the precise WWDC25 Liquid Glass visual surface properties.
 * Highlights, shadows, and opacity automatically scale based on the requested container [depth].
 */
@Composable
fun Modifier.glassySurface(
    shape: Shape = RoundedCornerShape(16.dp),
    isClearVariant: Boolean = false,
    depth: Dp = 1.dp
): Modifier {
    val isDark = isSystemInDarkTheme()
    
    // 1. Select translucent background color based on variant preference and light/dark theme
    val bgColor = remember(isDark, isClearVariant) {
        if (isDark) {
            if (isClearVariant) GlassyColors.DarkGlassBackgroundClear else GlassyColors.DarkGlassBackground
        } else {
            if (isClearVariant) GlassyColors.LightGlassBackgroundClear else GlassyColors.LightGlassBackground
        }
    }

    // 2. Adjust shadows and specular highlights to emulate physical thickness/depth of material
    val shadowAlpha = remember(depth) {
        (0.04f + (depth.value / 40f)).coerceAtMost(0.25f)
    }
    val shadowElevation = remember(depth) {
        (depth * 3).coerceAtMost(48.dp)
    }

    // 3. Create a dual gradient border emphasizing "Specular Highlights" on the upper-left contours
    val borderBrush = remember(isDark) {
        Brush.linearGradient(
            colors = if (isDark) {
                listOf(
                    GlassyColors.DarkSpecularHighlight,
                    Color.Transparent,
                    GlassyColors.DarkSpecularShadow
                )
            } else {
                listOf(
                    GlassyColors.LightSpecularHighlight,
                    Color.Transparent,
                    GlassyColors.LightSpecularShadow
                )
            },
            start = Offset(0f, 0f),
            end = Offset.Infinite
        )
    }

    return this
        .then(
            if (shadowElevation > 0.dp) {
                Modifier.shadow(
                    elevation = shadowElevation,
                    shape = shape,
                    clip = false,
                    ambientColor = if (isDark) Color.Black else Color(0x3B000000),
                    spotColor = if (isDark) Color.Black else Color(0x2D000000)
                )
            } else {
                Modifier
            }
        )
        .background(color = bgColor, shape = shape)
        .border(
            width = 1.dp,
            brush = borderBrush,
            shape = shape
        )
        .clip(shape)
}

/**
 * A container panel that adapts to the active App Theme Style.
 * Renders as a Liquid Glass surface in "glassy" style, and as a standard elegant card in "natural" style.
 */
@Composable
fun GlassyCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    isClearVariant: Boolean = false,
    depth: Dp = 2.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val activeStyle = LocalAppThemeStyle.current

    if (activeStyle == "glassy") {
        Box(
            modifier = modifier.glassySurface(shape = shape, isClearVariant = isClearVariant, depth = depth)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                content = content
            )
        }
    } else {
        // Fallback to elegant Natural Tones card
        Card(
            modifier = modifier,
            shape = shape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                content = content
            )
        }
    }
}

/**
 * Highly customized button implementing Liquid Glassy visual characteristics.
 * Automatically morphs to concentric/capsule shape, adds subtle specularity and tactile highlights.
 */
@Composable
fun GlassyButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tintColor: Color = GlassyColors.BlueTint,
    tintGlassBg: Color = GlassyColors.BlueTintGlass,
    content: @Composable RowScope.() -> Unit
) {
    val activeStyle = LocalAppThemeStyle.current
    val isDark = isSystemInDarkTheme()

    if (activeStyle != "glassy") {
        // Standard Material 3 / Natural Tones Button
        Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            content = content
        )
        return
    }

    // Glassy Capsule styled button
    val interactionSource = remember { MutableInteractionSource() }
    
    // Animating states for interactive focus responses
    val infiniteTransition = rememberInfiniteTransition(label = "btn_glow")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_pulse"
    )

    val finalBgColor = if (enabled) {
        tintGlassBg.copy(alpha = tintGlassBg.alpha + pulseAlpha)
    } else {
        Color.Gray.copy(alpha = 0.1f)
    }

    val finalContentColor = if (enabled) tintColor else Color.Gray

    val specBrush = remember(isDark, enabled) {
        Brush.linearGradient(
            colors = if (!enabled) {
                listOf(Color.Transparent, Color.Transparent)
            } else if (isDark) {
                listOf(Color(0x33FFFFFF), tintColor.copy(alpha = 0.1f), Color(0x1A000000))
            } else {
                listOf(Color(0xE6FFFFFF), tintColor.copy(alpha = 0.15f), Color(0x33000000))
            },
            start = Offset(0f, 0f),
            end = Offset.Infinite
        )
    }

    Box(
        modifier = modifier
            .height(52.dp)
            .shadow(
                elevation = if (enabled) 6.dp else 0.dp,
                shape = CircleShape,
                clip = false
            )
            .background(finalBgColor, CircleShape)
            .border(1.dp, specBrush, CircleShape)
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(bounded = true, color = tintColor),
                onClick = onClick
            )
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxHeight()
        ) {
            CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides finalContentColor
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    content()
                }
            }
        }
    }
}
