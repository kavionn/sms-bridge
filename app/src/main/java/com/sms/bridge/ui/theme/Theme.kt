package com.sms.bridge.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = IndigoDarkPrimary,
    secondary = IndigoDarkSecondary,
    tertiary = EmeraldActive,
    background = SlateDarkBg,
    surface = SlateDarkSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = SlateDarkOnBg,
    onSurface = SlateDarkOnBg,
    primaryContainer = IndigoDark,
    onPrimaryContainer = SlateLightOnPrimaryContainer,
    surfaceVariant = SlateDarkSurfaceVariant,
    onSurfaceVariant = SlateDarkOnSurfaceVariant,
    outlineVariant = SlateDarkSurfaceVariant
  )

private val LightColorScheme =
  lightColorScheme(
    primary = IndigoPrimary,
    secondary = IndigoDark,
    tertiary = EmeraldActive,
    background = SlateLightBg,
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = SlateDarkText,
    onSurface = SlateDarkText,
    primaryContainer = IndigoLight,
    onPrimaryContainer = IndigoDark,
    surfaceVariant = SlateLightSurfaceVariant,
    onSurfaceVariant = SlateMuted,
    outlineVariant = SlateLightOutlineVariant
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Disable dynamic system color by default to preserve the exact requested "Clean Utility / Minimal" design.
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
