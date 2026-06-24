package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme =
  lightColorScheme(
    primary = NaturalAccent,
    onPrimary = NaturalOnAccent,
    primaryContainer = NaturalCard,
    onPrimaryContainer = NaturalTextPrimary,
    secondary = NaturalTextSecondary,
    onSecondary = NaturalBg,
    background = NaturalBg,
    onBackground = NaturalTextPrimary,
    surface = NaturalBg,
    onSurface = NaturalTextPrimary,
    surfaceVariant = NaturalCard,
    onSurfaceVariant = NaturalTextSecondary,
    outline = NaturalBorder
  )

private val DarkColorScheme =
  darkColorScheme(
    primary = NaturalCard,
    onPrimary = NaturalTextPrimary,
    primaryContainer = NaturalAccent,
    onPrimaryContainer = NaturalOnAccent,
    secondary = NaturalTextSecondary,
    onSecondary = NaturalBg,
    background = NaturalTextPrimary,
    onBackground = NaturalBg,
    surface = NaturalTextPrimary,
    onSurface = NaturalBg,
    surfaceVariant = NaturalAccent,
    onSurfaceVariant = NaturalCard,
    outline = NaturalBorder
  )

// Glassy Theme Palette with precise styling according to WWDC25 Liquid Glass design rules
private val GlassyLightColorScheme =
  lightColorScheme(
    primary = Color(0xFF007AFF), // iOS premium blue
    onPrimary = Color.White,
    primaryContainer = Color(0x1F007AFF), // Translucent blue
    onPrimaryContainer = Color(0xFF007AFF),
    secondary = Color(0xFF555555),
    onSecondary = Color.White,
    background = Color(0xFFF2F2F7),
    onBackground = Color(0xFF1C1C1E),
    surface = Color(0xB2FFFFFF), // Translucent white glass
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0x11000000), // Highly subtle tint card background
    onSurfaceVariant = Color(0xFF55555C),
    outline = Color(0x22000000)
  )

private val GlassyDarkColorScheme =
  darkColorScheme(
    primary = Color(0xFF0A84FF), // Vivid neon iOS blue
    onPrimary = Color.White,
    primaryContainer = Color(0x330A84FF), // Translucent dark blue
    onPrimaryContainer = Color(0xFF0A84FF),
    secondary = Color(0xFFAAAAAA),
    onSecondary = Color.Black,
    background = Color(0xFF0E0E11),
    onBackground = Color(0xFFF2F2F7),
    surface = Color(0x8D1A1A1E), // Translucent dark slate glass
    onSurface = Color(0xFFF2F2F7),
    surfaceVariant = Color(0x1AFFFFFF), // Sleek subtle container highlight
    onSurfaceVariant = Color(0xFF9E9E9F),
    outline = Color(0x22FFFFFF)
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Override dynamicColor to false by default so our Natural Tones design theme is fully shown
  dynamicColor: Boolean = false,
  appThemeStyle: String = "natural",
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      appThemeStyle == "glassy" -> {
        if (darkTheme) GlassyDarkColorScheme else GlassyLightColorScheme
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
