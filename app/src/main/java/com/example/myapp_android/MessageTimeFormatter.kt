package com.example.myapp_android

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Форматирование серверного времени в локальную таймзону пользователя.
 *
 * Бэкенд отдаёт createdAt в ISO-8601 UTC (например, "2026-10-01T19:20:53Z").
 * Также поддерживаются легаси-значения без зоны ("2026-10-01 19:20:53"),
 * которые трактуются как UTC.
 */
object MessageTimeFormatter {

    private val localFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.getDefault())

    private val legacyUtcFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun formatLocal(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val instant = parseAsUtc(raw) ?: return raw
        return instant.atZone(ZoneId.systemDefault()).format(localFormatter)
    }

    private fun parseAsUtc(raw: String): Instant? {
        return try {
            Instant.parse(raw)
        } catch (_: DateTimeParseException) {
            try {
                LocalDateTime.parse(raw, legacyUtcFormatter).toInstant(ZoneOffset.UTC)
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }
}
