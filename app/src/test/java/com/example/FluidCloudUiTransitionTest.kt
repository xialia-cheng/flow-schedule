package com.example

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.example.data.ScheduleEntity
import com.example.notification.LiveActivityItem
import com.example.notification.LiveActivitySourceType
import com.example.notification.LiveUpdatePermissionStatus
import com.example.ui.components.FluidCloudCapsuleBanner
import com.example.ui.components.HeroLiveCard
import com.example.ui.theme.FlowMotion
import com.example.ui.theme.FlowScheduleTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FluidCloudUiTransitionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `fluid cloud capsule banner enters updates phase and exits cleanly`() {
        ApplicationProvider.getApplicationContext<ScheduleApp>()
        val baseStart = 1_700_000_600_000L
        val baseEnd = baseStart + 45 * 60_000L
        val courseItem = LiveActivityItem(
            sourceType = LiveActivitySourceType.COURSE,
            sourceId = 101L,
            dateKey = "2026-11-24",
            title = "操作系统原理",
            location = "信工楼302",
            startTimeMillis = baseStart,
            endTimeMillis = baseEnd,
            reminderMinutesBefore = 15,
        )
        val permissionStatus = LiveUpdatePermissionStatus(
            hasPostNotifications = true,
            canPostPromotedNotifications = true,
            canScheduleExactAlarms = true,
            isAndroid16OrAbove = true,
        )

        var activeItemState by mutableStateOf<LiveActivityItem?>(null)
        var nowState by mutableLongStateOf(baseStart - 10 * 60_000L)

        composeTestRule.setContent {
            FlowScheduleTheme {
                val visible = activeItemState != null && activeItemState!!.isLiveEligible(nowState)
                var retainedItem by remember { mutableStateOf<LiveActivityItem?>(null) }
                if (visible && activeItemState != null) {
                    retainedItem = activeItemState
                }
                AnimatedVisibility(
                    visible = visible && retainedItem != null,
                    enter = fadeIn(tween(320, easing = FastOutSlowInEasing)) +
                        expandVertically(FlowMotion.standardSpring(), expandFrom = Alignment.Top) +
                        scaleIn(animationSpec = tween(300, easing = FastOutSlowInEasing), initialScale = 0.96f),
                    exit = fadeOut(tween(240, easing = FastOutSlowInEasing)) +
                        shrinkVertically(tween(260, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top) +
                        scaleOut(animationSpec = tween(220, easing = FastOutSlowInEasing), targetScale = 0.96f),
                ) {
                    retainedItem?.let { liveItem ->
                        FluidCloudCapsuleBanner(
                            item = liveItem,
                            permissionStatus = permissionStatus,
                            nowMillis = nowState,
                            onOpenPermissions = {},
                        )
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("timetable_fluid_cloud_banner").assertDoesNotExist()

        // Enter in reminder window
        composeTestRule.runOnUiThread {
            activeItemState = courseItem
            nowState = baseStart - 10 * 60_000L
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("timetable_fluid_cloud_banner").assertIsDisplayed()
        composeTestRule.onNodeWithText("操作系统原理").assertIsDisplayed()
        composeTestRule.onNodeWithText("实况通知 · 课前临近").assertIsDisplayed()

        // Transition phase to in-progress
        composeTestRule.runOnUiThread {
            nowState = baseStart + 5 * 60_000L
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("实况通知 · 正在上课").assertIsDisplayed()

        // Exit smoothly back to hidden
        composeTestRule.runOnUiThread {
            activeItemState = null
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("timetable_fluid_cloud_banner").assertDoesNotExist()
    }

    @Test
    fun `hero live card transitions smoothly from idle to prep to active and back to idle`() {
        val start = 1_700_000_600_000L
        val end = start + 30 * 60_000L
        val schedule = ScheduleEntity(
            id = 55L,
            title = "项目迭代评审",
            startTimeMillis = start,
            endTimeMillis = end,
            reminderMinutesBefore = 15,
        )

        var currentSchedule by mutableStateOf<ScheduleEntity?>(null)
        var nowState by mutableLongStateOf(start - 5 * 60_000L)
        var liveActiveState by mutableStateOf(false)

        composeTestRule.setContent {
            FlowScheduleTheme {
                HeroLiveCard(
                    schedule = currentSchedule,
                    nowMillis = nowState,
                    isLiveActive = liveActiveState,
                    onComplete = {},
                    onSnooze10Min = {},
                    onEdit = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("hero_idle_card").assertIsDisplayed()

        // Transition idle -> reminder window
        composeTestRule.runOnUiThread {
            currentSchedule = schedule
            nowState = start - 5 * 60_000L
            liveActiveState = true
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("hero_live_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("实况通知激活 · 即将开始").assertIsDisplayed()

        // Transition reminder -> in progress
        composeTestRule.runOnUiThread {
            nowState = start + 5 * 60_000L
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("实况通知激活 · 进行中").assertIsDisplayed()

        // Transition active -> idle
        composeTestRule.runOnUiThread {
            currentSchedule = null
            liveActiveState = false
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("hero_idle_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("hero_live_card").assertDoesNotExist()
    }
}
