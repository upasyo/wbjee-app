package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = BentoPrimary,
    secondary = BentoSecondary,
    background = BentoBackground,
    surface = BentoSurface,
    onPrimary = BentoOnPrimary,
    onSecondary = BentoOnSecondary,
    primaryContainer = BentoPrimaryContainer,
    onPrimaryContainer = BentoOnPrimaryContainer,
    surfaceVariant = BentoSurfaceVariant,
    onSurfaceVariant = BentoOnSurfaceVariant,
    outline = BentoOutline
  )

private val LightColorScheme =
  lightColorScheme(
    primary = BentoPrimary,
    secondary = BentoSecondary,
    background = BentoBackground,
    surface = BentoSurface,
    onPrimary = BentoOnPrimary,
    onSecondary = BentoOnSecondary,
    primaryContainer = BentoPrimaryContainer,
    onPrimaryContainer = BentoOnPrimaryContainer,
    surfaceVariant = BentoSurfaceVariant,
    onSurfaceVariant = BentoOnSurfaceVariant,
    outline = BentoOutline
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Disable dynamic color by default to enforce the elegant Bento palette
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
