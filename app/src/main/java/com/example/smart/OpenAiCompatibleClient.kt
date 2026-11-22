package com.example.smart

import com.example.data.CourseColorPalette
import com.example.data.CustomAiConfig
import com.example.data.DateTimeUtils
import com.example.data.RepeatRule
import com.example.data.ScheduleCategory
import com.example.data.SectionTimeEntity
import com.example.data.WeekType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Optional client for user-configured OpenAI-compatible /v1/chat/completions endpoints
 * (e.g., DeepSeek, Qwen/DashScope, SiliconFlow, Moonshot, or local Ollama).
 * Pure standard HttpURLConnection + org.json — zero Google Cloud / Firebase dependencies.
 */
object OpenAiCompatibleClient {

    private const val CONNECT_TIMEOUT_MS = 12_000
    private const val READ_TIMEOUT_MS = 22_000

    fun normalizeChatCompletionsUrl(rawBaseUrl: String): String {
        val trimmed = rawBaseUrl.trim().trimEnd('/')
        if (trimmed.isEmpty()) return ""
        return when {
            trimmed.endsWith("/chat/completions") -> trimmed
            trimmed.endsWith("/v1") || trimmed.endsWith("/compatible-mode/v1") -> "$trimmed/chat/completions"
            else -> "$trimmed/v1/chat/completions"
        }
    }

    suspend fun testConnection(config: CustomAiConfig): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = normalizeChatCompletionsUrl(config.baseUrl)
            require(endpoint.startsWith("https://") || endpoint.startsWith("http://")) {
                "Base URL 需以 https:// 或 http:// 开头"
            }
            require(config.apiKey.isNotBlank()) { "请先填写 API Key" }
            require(config.model.isNotBlank()) { "请先填写模型名称" }

            val requestBody = JSONObject().apply {
                put("model", config.model.trim())
                put("temperature", 0.0)
                put("max_tokens", 16)
                put(
                    "messages",
                    JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", "回复 OK")
                        })
                    },
                )
            }
            val reply = executeChatRequest(endpoint, config.apiKey.trim(), requestBody)
            "连接成功（模型响应：${reply.take(24)}）"
        }
    }

    suspend fun parseToDrafts(
        rawInput: String,
        config: CustomAiConfig,
        nowMillis: Long,
        sectionTimes: List<SectionTimeEntity>,
        defaultTotalWeeks: Int,
    ): Result<List<SmartParsedDraft>> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = normalizeChatCompletionsUrl(config.baseUrl)
            require(endpoint.startsWith("https://") || endpoint.startsWith("http://")) {
                "无效的 Base URL"
            }

            val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm (EEEE)", Locale.CHINA).format(Date(nowMillis))
            val systemPrompt = """
                你是中国高校与个人日程解析助手。请将用户的中文自然语言解析为严格 JSON 对象，不要输出任何额外解释或 Markdown。
                当前时间：$nowStr
                输出格式：
                {
                  "items": [
                    {
                      "type": "SCHEDULE 或 COURSE",
                      "title": "简短清晰标题",
                      "location": "地点，不含@前缀，无则空字符串",
                      "date": "YYYY-MM-DD",
                      "startTime": "HH:mm",
                      "endTime": "HH:mm",
                      "daysOfWeek": [1,3,5],
                      "startSection": 1,
                      "endSection": 2,
                      "startWeek": 1,
                      "endWeek": $defaultTotalWeeks,
                      "weekType": "ALL 或 ODD 或 EVEN",
                      "repeatRule": "NONE 或 DAILY 或 WEEKLY 或 WORKDAYS",
                      "reminderMinutes": 15,
                      "enableDnd": false
                    }
                  ]
                }
                规则：
                - 早八默认 08:00-09:40（第1-2节），早十默认 10:00-11:40（第3-4节）。
                - 如果提到多天重复课程（如每周一三五），type 设为 COURSE 且 daysOfWeek 填入对应星期数字（1=周一..7=周日）。
                - 如果提到“免打扰/勿扰/静音”，enableDnd 设为 true。
                - 如果一句话包含多个日程或课程，请在 items 数组中返回多条记录。
            """.trimIndent()

            val requestBody = JSONObject().apply {
                put("model", config.model.trim())
                put("temperature", 0.1)
                put(
                    "messages",
                    JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", systemPrompt)
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", rawInput.trim())
                        })
                    },
                )
            }

            val content = executeChatRequest(endpoint, config.apiKey.trim(), requestBody)
            val parsed = parseStructuredJsonResponse(
                content = content,
                nowMillis = nowMillis,
                sectionTimes = sectionTimes.ifEmpty { CourseColorPalette.defaultSectionTimes(0L) },
                defaultTotalWeeks = defaultTotalWeeks,
            )
            require(parsed.isNotEmpty()) { "模型未返回有效日程条目" }
            parsed
        }
    }

    private fun executeChatRequest(
        endpoint: String,
        apiKey: String,
        body: JSONObject,
    ): String {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            doInput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Authorization", "Bearer $apiKey")
        }
        try {
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(body.toString())
                writer.flush()
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.let {
                BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { reader -> reader.readText() }
            }.orEmpty()

            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code: ${responseText.take(160)}")
            }
            val root = JSONObject(responseText)
            val choices = root.optJSONArray("choices")
                ?: throw IllegalStateException("响应缺少 choices 字段")
            val firstChoice = choices.optJSONObject(0)
                ?: throw IllegalStateException("响应 choices 为空")
            val message = firstChoice.optJSONObject("message")
                ?: throw IllegalStateException("响应缺少 message 字段")
            return message.optString("content", "").trim()
        } finally {
            connection.disconnect()
        }
    }

    internal fun parseStructuredJsonResponse(
        content: String,
        nowMillis: Long,
        sectionTimes: List<SectionTimeEntity>,
        defaultTotalWeeks: Int,
    ): List<SmartParsedDraft> {
        val stripped = content
            .replace(Regex("^```(?:json)?\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*```$"), "")
            .trim()

        val startIdx = stripped.indexOf('{').takeIf { it >= 0 } ?: stripped.indexOf('[')
        if (startIdx < 0) return emptyList()
        val jsonSlice = stripped.substring(startIdx)

        val itemsArray: JSONArray = if (jsonSlice.startsWith("[")) {
            JSONArray(jsonSlice)
        } else {
            val obj = JSONObject(jsonSlice)
            obj.optJSONArray("items") ?: JSONArray().apply { put(obj) }
        }

        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val result = mutableListOf<SmartParsedDraft>()

        for (i in 0 until itemsArray.length()) {
            val item = itemsArray.optJSONObject(i) ?: continue
            val title = item.optString("title").trim()
            if (title.isEmpty()) continue

            val typeRaw = item.optString("type", "SCHEDULE").uppercase(Locale.US)
            val draftType = if (typeRaw == "COURSE") DraftType.COURSE else DraftType.SCHEDULE
            val location = item.optString("location", "").trim().removePrefix("@")

            val dateStr = item.optString("date", "")
            val dayMillis = runCatching {
                if (dateStr.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                    DateTimeUtils.startOfDayMillis(dateFmt.parse(dateStr)!!.time)
                } else {
                    DateTimeUtils.startOfDayMillis(nowMillis)
                }
            }.getOrDefault(DateTimeUtils.startOfDayMillis(nowMillis))

            val (sh, sm) = parseHm(item.optString("startTime", "09:00"), 9, 0)
            val (eh, em) = parseHm(item.optString("endTime", ""), (sh + 1).coerceAtMost(23), sm)
            val startMillis = DateTimeUtils.combineDateAndTime(dayMillis, sh, sm)
            var endMillis = DateTimeUtils.combineDateAndTime(dayMillis, eh, em)
            if (endMillis <= startMillis) {
                endMillis = startMillis + 60 * 60_000L
            }

            val daysArr = item.optJSONArray("daysOfWeek")
            val days = buildList {
                if (daysArr != null) {
                    for (d in 0 until daysArr.length()) {
                        val v = daysArr.optInt(d, -1)
                        if (v in 1..7 && v !in this) add(v)
                    }
                }
            }.ifEmpty { listOf(DateTimeUtils.isoDayOfWeek(startMillis)) }

            val mappedSections = ChineseNaturalLanguageParser.mapTimeToSectionRange(
                startTimeMillis = startMillis,
                endTimeMillis = endMillis,
                sectionTimes = sectionTimes,
            )
            val maxSec = (sectionTimes.maxOfOrNull { it.sectionNumber } ?: 12).coerceIn(6, 16)
            val startSec = item.optInt("startSection", mappedSections.first).coerceIn(1, maxSec)
            val endSec = item.optInt("endSection", mappedSections.second).coerceIn(startSec, maxSec)
            val startWeek = item.optInt("startWeek", 1).coerceIn(1, 30)
            val endWeek = item.optInt("endWeek", defaultTotalWeeks).coerceIn(startWeek, 30)
            val weekType = runCatching {
                WeekType.valueOf(item.optString("weekType", "ALL").uppercase(Locale.US))
            }.getOrDefault(WeekType.ALL)
            val repeatRule = runCatching {
                RepeatRule.valueOf(item.optString("repeatRule", "NONE").uppercase(Locale.US))
            }.getOrDefault(RepeatRule.NONE)
            val reminderMinutes = item.optInt("reminderMinutes", 15).coerceIn(0, 120)
            val enableDnd = item.optBoolean("enableDnd", false)

            val notesParts = buildList {
                if (location.isNotBlank()) add("@$location")
                if (enableDnd) add("[免打扰]")
            }

            result.add(
                SmartParsedDraft(
                    draftType = draftType,
                    title = title,
                    location = location,
                    startTimeMillis = startMillis,
                    endTimeMillis = endMillis,
                    daysOfWeek = days,
                    startSection = startSec,
                    endSection = endSec,
                    startWeek = startWeek,
                    endWeek = endWeek,
                    weekType = weekType,
                    repeatRule = repeatRule,
                    reminderMinutesBefore = reminderMinutes,
                    enableDnd = enableDnd,
                    category = if (draftType == DraftType.COURSE) ScheduleCategory.FOCUS else ScheduleCategory.WORK,
                    notes = notesParts.joinToString(" · "),
                ),
            )
        }
        return result
    }

    private fun parseHm(raw: String, fallbackH: Int, fallbackM: Int): Pair<Int, Int> {
        val parts = raw.trim().split(":", "：")
        if (parts.size < 2) return fallbackH to fallbackM
        val h = parts[0].toIntOrNull()?.coerceIn(0, 23) ?: fallbackH
        val m = parts[1].toIntOrNull()?.coerceIn(0, 59) ?: fallbackM
        return h to m
    }
}
