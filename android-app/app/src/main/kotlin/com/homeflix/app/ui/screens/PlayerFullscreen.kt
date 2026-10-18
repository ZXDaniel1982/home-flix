package com.homeflix.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Applies the window-level side effects of player fullscreen: hides the system bars and
 * requests landscape while [enabled], then restores both when disabled or when the host
 * leaves composition.
 */
@Composable
internal fun PlayerFullscreenEffect(enabled: Boolean) {
    val view = LocalView.current
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() } ?: return
    val window = activity.window

    DisposableEffect(enabled, activity) {
        val controller = WindowCompat.getInsetsController(window, view)
        val previousOrientation = activity.requestedOrientation
        if (enabled) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onDispose {
            if (enabled) {
                controller.show(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                activity.requestedOrientation = previousOrientation
            }
        }
    }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
