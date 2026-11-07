package com.v2ray.ang.ui.compose

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Day palette: warm off-white with deep teal
private val LightColor = lightColorScheme(
    primary = Color(0xFF006E5E),        // Deep teal
    onPrimary = Color(0xFFFFFFFF),       // White
    primaryContainer = Color(0xFFC8F0EA), // Pale teal
    onPrimaryContainer = Color(0xFF00211E), // Dark teal
    secondary = Color(0xFF4A6B5A),       // Muted sage
    onSecondary = Color(0xFFFFFFFF),     // White
    secondaryContainer = Color(0xFFCCE0D4), // Light sage
    onSecondaryContainer = Color(0xFF082015), // Dark sage
    tertiary = Color(0xFF6B5B00),        // Warm gold
    onTertiary = Color(0xFFFFFFFF),      // White
    tertiaryContainer = Color(0xFFFFDE7F), // Pale gold
    onTertiaryContainer = Color(0xFF221E00), // Dark gold
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onError = Color(0xFFFFFFFF),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF8F9F7),      // Warm off-white
    onBackground = Color(0xFF1C1E1D),    // Near black
    surface = Color(0xFFF8F9F7),         // Warm off-white
    onSurface = Color(0xFF1C1E1D),
    surfaceVariant = Color(0xFFDEE5E0),  // Light teal-gray
    onSurfaceVariant = Color(0xFF434946),
    outline = Color(0xFF737976),
    outlineVariant = Color(0xFFC3C9C5),
    inverseSurface = Color(0xFF2E3130),
    inverseOnSurface = Color(0xFFF0F1EF),
    inversePrimary = Color(0xFFA0D8C8),
    scrim = Color(0xFF000000),
    surfaceTint = Color(0xFF006E5E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F4F2),
    surfaceContainer = Color(0xFFECEEEB),
    surfaceContainerHigh = Color(0xFFE6E8E5),
    surfaceContainerHighest = Color(0xFFE0E2DF),
)

// Night palette: ink/navy with emerald accent
private val DarkColor = darkColorScheme(
    primary = Color(0xFF5EE8C4),         // Emerald
    onPrimary = Color(0xFF00211E),       // Dark teal
    primaryContainer = Color(0xFF005448), // Deep teal
    onPrimaryContainer = Color(0xFFC8F0EA),
    secondary = Color(0xFFB0C9BA),       // Light sage
    onSecondary = Color(0xFF1F3328),
    secondaryContainer = Color(0xFF354E40),
    onSecondaryContainer = Color(0xFFCCE0D4),
    tertiary = Color(0xFFFFD24D),        // Warm gold
    onTertiary = Color(0xFF221E00),
    tertiaryContainer = Color(0xFF514A00),
    onTertiaryContainer = Color(0xFFFFDE7F),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onError = Color(0xFF690005),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0D1311),      // Ink/navy
    onBackground = Color(0xFFE0E2DF),
    surface = Color(0xFF0D1311),
    onSurface = Color(0xFFE0E2DF),
    surfaceVariant = Color(0xFF434946),
    onSurfaceVariant = Color(0xFFC3C9C5),
    outline = Color(0xFF8E9490),
    outlineVariant = Color(0xFF434946),
    inverseSurface = Color(0xFFE0E2DF),
    inverseOnSurface = Color(0xFF2E3130),
    inversePrimary = Color(0xFF006E5E),
    scrim = Color(0xFF000000),
    surfaceTint = Color(0xFF5EE8C4),
    surfaceContainerLowest = Color(0xFF080E0C),
    surfaceContainerLow = Color(0xFF141C18),
    surfaceContainer = Color(0xFF18201D),
    surfaceContainerHigh = Color(0xFF222A26),
    surfaceContainerHighest = Color(0xFF2D3531),
)

// Semantic colors (accessible on both palettes)
val colorPing = Color(0xFF009966)
val colorPingRed = Color(0xFFFF0099)
val colorConfigType = Color(0xFFf97910)
val colorFabActive = Color(0xFFf97910)
val colorFabInactiveLight = Color(0xFF9C9C9C)
val colorFabInactiveDark = Color(0xFF646464)
val dividerColorLight = Color(0xFFE0E0E0)
val dividerColorDark = Color(0xFF424242)

// Toast Colors 85%
val toastNormalBgLight = Color(0xD9353A3E)
val toastNormalBgDark = Color(0xD94A4F54)
val toastSuccessBg = Color(0xD9388E3C)
val toastErrorBg = Color(0xD9D50000)
val toastInfoBg = Color(0xD93F51B5)
val toastIconCircleBg = Color(0x33FFFFFF)
val toastTextColor = Color.White

object ThemeManager {
    private val _themeMode = MutableStateFlow(
        MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"
    )
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _dynamicColorEnabled = MutableStateFlow(
        MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, true)
    )
    val dynamicColorEnabled: StateFlow<Boolean> = _dynamicColorEnabled.asStateFlow()

    fun setThemeMode(mode: String) {
        MmkvManager.encodeSettings(AppConfig.PREF_UI_MODE_NIGHT, mode)
        _themeMode.value = mode
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_DYNAMIC_COLOR, enabled)
        _dynamicColorEnabled.value = enabled
    }

    fun refresh() {
        _themeMode.value =
            MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"
        _dynamicColorEnabled.value =
            MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, true)
    }
}

@Composable
fun resolveDarkTheme(): Boolean {
    val mode by ThemeManager.themeMode.collectAsState()
    return when (mode) {
        "1" -> false
        "2" -> true
        else -> isSystemInDarkTheme()
    }
}

val LocalDarkTheme = compositionLocalOf { false }

@Composable
fun AppTheme(
    darkTheme: Boolean = resolveDarkTheme(),
    content: @Composable () -> Unit
) {
    val dynamicColor by ThemeManager.dynamicColorEnabled.collectAsState()
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColor
        else -> LightColor
    }
    val snackbarController = rememberAppSnackbarController()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val window = activity.window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalAppSnackbar provides snackbarController
    ) {
        MaterialTheme(
            colorScheme = colorScheme
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AppSnackbarBridge(controller = snackbarController)
                content()
                AppSnackbarHost(hostState = snackbarController.hostState)
            }
        }
    }
}
