package com.example.data

import androidx.compose.ui.graphics.Color
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

data class CustomScheduleTag(
    val id: String,
    val name: String,
    val colorHex: String,
) {
    fun resolvedColor(): Color {
        return CourseColorPalette.parseHexColorOrNull(colorHex) ?: CategoryWork
    }
}

object CustomScheduleTagCodec {
    val presetColorHexes: List<String> = listOf(
        "#0B72FF",
        "#009B8A",
        "#7C3AED",
        "#E67E22",
        "#E11D6D",
        "#10B981",
        "#4F46E5",
        "#EA580C",
        "#DC2626",
        "#D97706",
        "#0891B2",
        "#C026D3",
    )

    fun encode(tags: List<CustomScheduleTag>): String {
        val array = JSONArray()
        tags.forEach { tag ->
            array.put(
                JSONObject().apply {
                    put("id", tag.id)
                    put("name", tag.name)
                    put("colorHex", tag.colorHex)
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
                    val hex = obj.optString("colorHex", "#0B72FF").trim()
                    if (id.isNotEmpty() && name.isNotEmpty()) {
                        add(CustomScheduleTag(id = id, name = name, colorHex = hex))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }
}

