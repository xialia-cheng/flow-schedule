package com.example.data

import java.util.Calendar

enum class RepeatRule(val label: String) {
    NONE("不重复"),
    DAILY("每天"),
    WEEKLY("每周"),
    WORKDAYS("工作日");

    fun computeNextOccurrence(startMillis: Long, endMillis: Long): Pair<Long, Long>? {
        if (this == NONE) return null
        val duration = (endMillis - startMillis).coerceAtLeast(60_000L)
        val cal = Calendar.getInstance().apply { timeInMillis = startMillis }

        when (this) {
            NONE -> return null
            DAILY -> cal.add(Calendar.DAY_OF_YEAR, 1)
            WEEKLY -> cal.add(Calendar.WEEK_OF_YEAR, 1)
            WORKDAYS -> {
                do {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                    val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                } while (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY)
            }
        }
        val nextStart = cal.timeInMillis
        return nextStart to (nextStart + duration)
    }
}
