package com.example.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView

/**
 * Global toggle for in-app haptic feedback (default enabled).
 * Provided at the root screen so both main tabs and modal sheets/dialogs respect the user setting.
 */
val LocalAppHapticsEnabled = staticCompositionLocalOf { true }

/**
 * Unified, lightweight haptic feedback helper backed by [android.view.View.performHapticFeedback]
 * without any override flags, strictly respecting the device's system-wide touch/haptic settings.
 */
@Immutable
class AppHaptics(
    val enabled: Boolean,
    private val performAction: (Int) -> Unit,
) {
    /**
     * Crisp micro-tick for high-frequency navigation & UI selections
     * (tabs, filter chips, switches, weekday strips, cards, steppers).
     */
    fun tick() {
        if (enabled) {
            performAction(resolveTickConstant())
        }
    }

    /**
     * Affirmative confirmation pulse for task/schedule completion and saving changes.
     */
    fun confirm() {
        if (enabled) {
            performAction(resolveConfirmConstant())
        }
    }

    /**
     * Distinct tactile feedback when opening a long-press contextual menu.
     */
    fun longPress() {
        if (enabled) {
            performAction(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    companion object {
        fun resolveTickConstant(): Int = HapticFeedbackConstants.CLOCK_TICK

        fun resolveConfirmConstant(sdkInt: Int = Build.VERSION.SDK_INT): Int =
            if (sdkInt >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.CONTEXT_CLICK
            }
    }
}

@Composable
fun rememberAppHaptics(
    enabled: Boolean = LocalAppHapticsEnabled.current,
): AppHaptics {
    val view = LocalView.current
    return remember(view, enabled) {
        AppHaptics(enabled = enabled) { constant ->
            // Omit flags so system-level haptic / vibration settings are always honored.
            view.performHapticFeedback(constant)
        }
    }
}
