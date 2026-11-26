package dev.localremote.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val OneRemoteLightColors = lightColorScheme(
    primary = Color(0xFF2457D6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE7FF),
    onPrimaryContainer = Color(0xFF071B4F),
    secondary = Color(0xFF53647E),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF087F73),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF5F7FC),
    onBackground = Color(0xFF121A2B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF121A2B),
    surfaceVariant = Color(0xFFE9EEF7),
    onSurfaceVariant = Color(0xFF56647A),
    surfaceContainer = Color(0xFFEDF1F8),
    outline = Color(0xFF8995A8)
)

private val OneRemoteDarkColors = darkColorScheme(
    primary = Color(0xFF9DBBFF),
    onPrimary = Color(0xFF09265E),
    primaryContainer = Color(0xFF173B83),
    onPrimaryContainer = Color(0xFFDCE7FF),
    secondary = Color(0xFFB7C5DC),
    onSecondary = Color(0xFF253248),
    tertiary = Color(0xFF79D8C8),
    onTertiary = Color(0xFF00382F),
    background = Color(0xFF0C1321),
    onBackground = Color(0xFFE6ECF6),
    surface = Color(0xFF121D2D),
    onSurface = Color(0xFFE6ECF6),
    surfaceVariant = Color(0xFF202C40),
    onSurfaceVariant = Color(0xFFB5C1D4),
    surfaceContainer = Color(0xFF182438),
    outline = Color(0xFF718099)
)

@Composable
fun OneRemoteTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) OneRemoteDarkColors else OneRemoteLightColors
    MaterialTheme(colorScheme = colors, content = content)
}
