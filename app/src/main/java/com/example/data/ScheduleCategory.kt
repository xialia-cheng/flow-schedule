package com.example.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.EventNote
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.WorkOutline
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.theme.CategoryFocus
import com.example.ui.theme.CategoryHealth
import com.example.ui.theme.CategoryLife
import com.example.ui.theme.CategoryMeeting
import com.example.ui.theme.CategoryTest
import com.example.ui.theme.CategoryWork
import org.json.JSONArray
import org.json.JSONObject

enum class ScheduleCategory(val label: String, val accentColor: Color) {
    WORK("工作", CategoryWork),
    MEETING("会议", CategoryMeeting),
    FOCUS("专注", CategoryFocus),
    LIFE("生活", CategoryLife),
    HEALTH("健康", CategoryHealth),
    TEST("实况通知测试", CategoryTest),
}

data class TagIconOption(
    val key: String,
    val label: String,
    val icon: ImageVector,
)

object TagIconCatalog {
    val options: List<TagIconOption> = listOf(
        TagIconOption("label", "默认", Icons.Rounded.Label),
        TagIconOption("work", "工作", Icons.Rounded.WorkOutline),
        TagIconOption("study", "学习", Icons.Rounded.MenuBook),
        TagIconOption("meeting", "会议", Icons.Rounded.Groups),
        TagIconOption("life", "日常", Icons.Rounded.Coffee),
        TagIconOption("health", "健康", Icons.Rounded.FavoriteBorder),
        TagIconOption("fitness", "运动", Icons.Rounded.FitnessCenter),
        TagIconOption("code", "开发", Icons.Rounded.Code),
        TagIconOption("travel", "出行", Icons.Rounded.Flight),
        TagIconOption("note", "备忘", Icons.Rounded.EventNote),
    )

    fun iconForKey(key: String?): ImageVector {
        val normalized = key?.trim()?.lowercase().orEmpty()
        return options.firstOrNull { it.key == normalized }?.icon ?: Icons.Rounded.Label
    }

    fun iconForCategory(category: ScheduleCategory): ImageVector {
        return when (category) {
            ScheduleCategory.WORK -> Icons.Rounded.WorkOutline
            ScheduleCategory.MEETING -> Icons.Rounded.Groups
            ScheduleCategory.FOCUS -> Icons.Rounded.MenuBook
            ScheduleCategory.LIFE -> Icons.Rounded.Coffee
            ScheduleCategory.HEALTH -> Icons.Rounded.FavoriteBorder
            ScheduleCategory.TEST -> Icons.Rounded.Label
        }
    }
}

data class CustomScheduleTag(
    val id: String,
    val name: String,
    val colorHex: String,
    val iconKey: String = "label",
) {
    fun resolvedColor(): Color {
        return CourseColorPalette.parseHexColorOrNull(colorHex) ?: CategoryWork
    }

    fun resolvedIcon(): ImageVector {
        return TagIconCatalog.iconForKey(iconKey)
    }
}

object CustomScheduleTagCodec {
    // Curated 8 restrained low-glare tones harmonized with Material 3 light & dark surfaces
    val presetColorHexes: List<String> = listOf(
        "#3B82F6",
        "#0D9488",
        "#6366F1",
        "#8B5CF6",
        "#D97706",
        "#E11D48",
        "#059669",
        "#64748B",
    )

    fun encode(tags: List<CustomScheduleTag>): String {
        val array = JSONArray()
        tags.forEach { tag ->
            array.put(
                JSONObject().apply {
                    put("id", tag.id)
                    put("name", tag.name)
                    put("colorHex", tag.colorHex)
                    put("iconKey", tag.iconKey.ifBlank { "label" })
                },
            )
        }
        return array.toString()
    }

    fun decode(rawJson: String): List<CustomScheduleTag> {
        if (rawJson.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(rawJson)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val id = obj.optString("id").trim()
                    val name = obj.optString("name").trim()
                    val hex = obj.optString("colorHex", "#3B82F6").trim()
                    val iconKey = obj.optString("iconKey", "label").trim().ifEmpty { "label" }
                    if (id.isNotEmpty() && name.isNotEmpty()) {
                        add(
                            CustomScheduleTag(
                                id = id,
                                name = name,
                                colorHex = hex,
                                iconKey = iconKey,
                            ),
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
    }
}
