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
    primary = PoliceBlueDarkPrimary,
    secondary = BlueLightContainer,
    tertiary = PinkLightContainer,
    background = PoliceBackgroundDark,
    surface = PoliceSurfaceDark,
    onBackground = BentoTextPrimaryDark,
    onSurface = BentoTextPrimaryDark,
    surfaceVariant = PoliceSurfaceDark,
    outline = PoliceBlueDarkOutline,
    outlineVariant = PoliceBlueDarkOutline.copy(alpha = 0.3f)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = PoliceBluePrimary,
    secondary = BlueLightContainer,
    tertiary = PinkLightContainer,
    background = PoliceBackgroundLight,
    surface = PoliceSurfaceLight,
    onPrimary = PoliceSurfaceLight,
    onSecondary = BlueDarkText,
    onTertiary = PinkDarkText,
    onBackground = BentoTextPrimaryLight,
    onSurface = BentoTextPrimaryLight,
    surfaceVariant = PoliceBackgroundLight,
    outline = PoliceBlueOutline,
    outlineVariant = PoliceBlueOutline.copy(alpha = 0.2f)
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is false by default to ensure bento theme layout colors are perfectly preserved
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

