package com.cbulock.sinkpoint.ui

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.cbulock.sinkpoint.data.ThemeMode

private val Light = lightColorScheme(
    primary = Color(0xFF6554D9), onPrimary = Color.White, primaryContainer = Color(0xFFEAE6FF), onPrimaryContainer = Color(0xFF282640),
    secondary = Color(0xFF176952), secondaryContainer = Color(0xFFB8E9D7), onSecondaryContainer = Color(0xFF123E31),
    tertiary = Color(0xFFAB4235), tertiaryContainer = Color(0xFFFFDAD4),
    background = Color(0xFFF7F8FC), onBackground = Color(0xFF282640), surface = Color(0xFFF7F8FC), onSurface = Color(0xFF282640), surfaceContainer = Color.White,
    surfaceVariant = Color(0xFFE9EAF3), onSurfaceVariant = Color(0xFF616075), outlineVariant = Color(0xFFDCDCE9),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFC1B5FF), onPrimary = Color(0xFF2C205F), primaryContainer = Color(0xFF433477),
    secondary = Color(0xFFB8E9D7), secondaryContainer = Color(0xFF264C40), onSecondaryContainer = Color(0xFFB8E9D7),
    tertiary = Color(0xFFFF7969), tertiaryContainer = Color(0xFF60372F),
    background = Color(0xFF181824), onBackground = Color(0xFFF0EEFF), surface = Color(0xFF181824), onSurface = Color(0xFFF0EEFF), surfaceContainer = Color(0xFF232331),
    surfaceVariant = Color(0xFF333345), onSurfaceVariant = Color(0xFFC2BED5), outlineVariant = Color(0xFF464359),
)
@Composable fun SinkpointTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = mode == ThemeMode.DARK || mode == ThemeMode.SYSTEM && isSystemInDarkTheme()
    val colors = if (Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
    } else if (dark) Dark else Light
    val view = LocalView.current
    val activity = LocalActivity.current
    SideEffect {
        activity?.let {
            val controller = WindowCompat.getInsetsController(it.window, view)
            controller.isAppearanceLightStatusBars = colors.background.luminance() > .5f
            controller.isAppearanceLightNavigationBars = colors.background.luminance() > .5f
        }
    }
    MaterialTheme(colorScheme = colors, content = content)
}
