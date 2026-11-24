package com.example.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Tracks vertical scroll gestures on content containers to automatically collapse
 * the floating action button while scrolling down and expand it when scrolling up.
 */
@Stable
class ScrollAwareFabState(
    initialVisible: Boolean = true,
    private val thresholdPx: Float = 18f,
) {
    var isVisible by mutableStateOf(initialVisible)
        private set

    private var accumulatedDeltaY = 0f

    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            if (source != NestedScrollSource.UserInput) return Offset.Zero
            val dy = consumed.y
            if (dy == 0f) return Offset.Zero

            if ((accumulatedDeltaY > 0f && dy < 0f) || (accumulatedDeltaY < 0f && dy > 0f)) {
                accumulatedDeltaY = 0f
            }
            accumulatedDeltaY += dy

            if (accumulatedDeltaY <= -thresholdPx) {
                if (isVisible) isVisible = false
                accumulatedDeltaY = 0f
            } else if (accumulatedDeltaY >= thresholdPx) {
                if (!isVisible) isVisible = true
                accumulatedDeltaY = 0f
            }
            return Offset.Zero
        }
    }

    fun expand() {
        accumulatedDeltaY = 0f
        if (!isVisible) {
            isVisible = true
        }
    }
}

@Composable
fun rememberScrollAwareFabState(
    initialVisible: Boolean = true,
): ScrollAwareFabState {
    val thresholdPx = with(LocalDensity.current) { 8.dp.toPx() }
    return remember(thresholdPx) {
        ScrollAwareFabState(
            initialVisible = initialVisible,
            thresholdPx = thresholdPx,
        )
    }
}

/**
 * Shared bottom-right "✨ 一句话创建" FloatingActionButton used across both HomeScreen and
 * TimetableScreen for identical geometry, Material 3 colors, spring press rebound, and
 * scroll-reactive collapse/expand transitions.
 */
@Composable
fun SmartCaptureFab(
    visible: Boolean,
    contentDescription: String,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val colorScheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale = remember { Animatable(1f) }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            pressScale.animateTo(
                targetValue = 0.86f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessHigh,
                ),
            )
        } else {
            pressScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.42f,
                    stiffness = Spring.StiffnessMedium,
                ),
            )
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(
            initialScale = 0.45f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        ) + fadeIn(
            animationSpec = tween(durationMillis = 160),
        ) + slideInVertically(
            initialOffsetY = { it / 2 },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        ),
        exit = scaleOut(
            targetScale = 0.45f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        ) + fadeOut(
            animationSpec = tween(durationMillis = 130),
        ) + slideOutVertically(
            targetOffsetY = { it / 2 },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        ),
        modifier = modifier.padding(end = 16.dp, bottom = 16.dp),
    ) {
        FloatingActionButton(
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onClick()
            },
            interactionSource = interactionSource,
            containerColor = colorScheme.primaryContainer,
            contentColor = colorScheme.onPrimaryContainer,
            shape = CircleShape,
            modifier = Modifier
                .size(52.dp)
                .graphicsLayer {
                    val scale = pressScale.value
                    scaleX = scale
                    scaleY = scale
                }
                .testTag(testTag),
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = contentDescription,
            )
        }
    }
}
