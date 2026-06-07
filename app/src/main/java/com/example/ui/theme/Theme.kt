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
import androidx.compose.ui.graphics.Color


private val DarkColorScheme =
  darkColorScheme(
    primary = CyanHolo,
    secondary = PurpleCyber,
    tertiary = EmeraldBio,
    background = DarkBg,
    surface = SurfaceDark,
    onBackground = TextMain,
    onSurface = TextMain,
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF94A3B8)
  )

private val LightColorScheme = DarkColorScheme // Force dark theme everywhere for Elegant Cyber-JARVIS look

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force Dark theme
  dynamicColor: Boolean = false, // Force custom JARVIS color palette instead of dynamic Android wallpaper colors
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme


  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
