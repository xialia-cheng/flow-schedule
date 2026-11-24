package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import androidx.test.core.app.ApplicationProvider
import com.example.ui.ScheduleViewModel
import com.example.ui.mine.MineScreen
import com.example.ui.smart.SmartCaptureViewModel
import com.example.ui.theme.FlowScheduleTheme
import com.example.ui.timetable.TimetableViewModel
import com.example.ui.update.AppUpdateViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MineScreenRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `mine screen groups start with group 1 expanded and groups 2 to 4 collapsed then expand on click`() {
        val app = ApplicationProvider.getApplicationContext<ScheduleApp>()
        val container = app.container
        val scheduleVm = ScheduleViewModel(
            repository = container.scheduleRepository,
            preferencesRepository = container.preferencesRepository,
            liveUpdateManager = container.liveUpdateManager,
        )
        val timetableVm = TimetableViewModel(
            timetableRepository = container.timetableRepository,
            preferencesRepository = container.preferencesRepository,
            liveUpdateCoordinator = container.liveUpdateCoordinator,
        )
        val smartVm = SmartCaptureViewModel(
            orchestrator = container.smartCaptureOrchestrator,
            preferencesRepository = container.preferencesRepository,
        )
        val updateVm = AppUpdateViewModel(
            preferencesRepository = container.preferencesRepository,
        )

        composeTestRule.setContent {
            FlowScheduleTheme {
                MineScreen(
                    timetableViewModel = timetableVm,
                    scheduleViewModel = scheduleVm,
                    smartCaptureViewModel = smartVm,
                    appUpdateViewModel = updateVm,
                    onOpenPermissions = {},
                    onOpenDndPolicySettings = {},
                    onTriggerFluidCloudTest = {},
                    onShowMessage = {},
                )
            }
        }

        composeTestRule.waitForIdle()

        // Overview card displays single-line version + build and privacy badge
        composeTestRule.onNodeWithTag("mine_app_version_badge").assertIsDisplayed()
        composeTestRule.onNodeWithText("本地优先 · 零数据收集").assertIsDisplayed()

        // Group 1 expanded by default
        composeTestRule.onNodeWithTag("mine_group_notification_header").assertIsDisplayed()
        composeTestRule.onNodeWithTag("mine_permissions_button").assertExists()

        // Groups 2, 3, 4 collapsed by default
        composeTestRule.onNodeWithTag("mine_switch_haptic_feedback").assertDoesNotExist()
        composeTestRule.onNodeWithTag("mine_export_json_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("mine_test_fluid_cloud_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("mine_about_help_entry").assertDoesNotExist()

        // Expand Group 2: Appearance & Interaction
        composeTestRule.onNodeWithTag("mine_lazy_list")
            .performScrollToKey("group_appearance_interaction")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("mine_group_appearance_header").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("mine_switch_haptic_feedback").assertExists()

        // Expand Group 3: Smart Parsing & Data Backup
        composeTestRule.onNodeWithTag("mine_lazy_list")
            .performScrollToKey("group_ai_backup")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("mine_group_ai_backup_header").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("mine_export_json_button").assertExists()

        // Expand Group 4: Advanced Debugging & About
        composeTestRule.onNodeWithTag("mine_lazy_list")
            .performScrollToKey("group_advanced_about")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("mine_group_advanced_header").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("mine_test_fluid_cloud_button").assertExists()
        composeTestRule.onNodeWithTag("mine_about_help_entry").assertExists()
        composeTestRule.onNodeWithTag("mine_stop_fluid_cloud_button").assertDoesNotExist()
    }

    @Test
    fun `fluid cloud test card shows stop test button when active test exists and triggers stop callback`() {
        val app = ApplicationProvider.getApplicationContext<ScheduleApp>()
        val container = app.container
        kotlinx.coroutines.runBlocking {
            container.scheduleRepository.createTestFluidCloudSchedule("高等数学", "@教A-301")
        }

        val scheduleVm = ScheduleViewModel(
            repository = container.scheduleRepository,
            preferencesRepository = container.preferencesRepository,
            liveUpdateManager = container.liveUpdateManager,
        )
        val timetableVm = TimetableViewModel(
            timetableRepository = container.timetableRepository,
            preferencesRepository = container.preferencesRepository,
            liveUpdateCoordinator = container.liveUpdateCoordinator,
        )
        val smartVm = SmartCaptureViewModel(
            orchestrator = container.smartCaptureOrchestrator,
            preferencesRepository = container.preferencesRepository,
        )
        val updateVm = AppUpdateViewModel(
            preferencesRepository = container.preferencesRepository,
        )

        var stopCalled = false
        composeTestRule.setContent {
            FlowScheduleTheme {
                MineScreen(
                    timetableViewModel = timetableVm,
                    scheduleViewModel = scheduleVm,
                    smartCaptureViewModel = smartVm,
                    appUpdateViewModel = updateVm,
                    onOpenPermissions = {},
                    onOpenDndPolicySettings = {},
                    onTriggerFluidCloudTest = {},
                    onStopFluidCloudTest = { stopCalled = true },
                    onShowMessage = {},
                )
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            scheduleVm.uiState.value.hasActiveTestSchedule
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("mine_lazy_list")
            .performScrollToKey("group_advanced_about")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("mine_group_advanced_header").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("mine_lazy_list")
            .performScrollToKey("mine_bottom_spacer")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("mine_stop_fluid_cloud_button")
            .assertExists()
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeTestRule.onNodeWithText("停止测试").assertIsDisplayed()
        composeTestRule.waitForIdle()
        org.junit.Assert.assertTrue(stopCalled)

        kotlinx.coroutines.runBlocking {
            container.scheduleRepository.stopTestFluidCloudSchedules()
        }
    }
}
