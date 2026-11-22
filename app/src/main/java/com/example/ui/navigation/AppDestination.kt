package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EventNote
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Person
import androidx.compose.ui.graphics.vector.ImageVector

enum class AppDestination(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
) {
    SCHEDULE(
        label = "日程",
        selectedIcon = Icons.Rounded.EventNote,
        unselectedIcon = Icons.Outlined.EventNote,
        testTag = "nav_tab_schedule",
    ),
    TIMETABLE(
        label = "课表",
        selectedIcon = Icons.Rounded.CalendarMonth,
        unselectedIcon = Icons.Outlined.CalendarMonth,
        testTag = "nav_tab_timetable",
    ),
    STUDY(
        label = "学习",
        selectedIcon = Icons.Rounded.MenuBook,
        unselectedIcon = Icons.Outlined.MenuBook,
        testTag = "nav_tab_study",
    ),
    MINE(
        label = "我的",
        selectedIcon = Icons.Rounded.Person,
        unselectedIcon = Icons.Outlined.Person,
        testTag = "nav_tab_mine",
    ),
}
