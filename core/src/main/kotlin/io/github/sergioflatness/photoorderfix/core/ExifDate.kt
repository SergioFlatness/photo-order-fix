package io.github.sergioflatness.photoorderfix.core

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Reads and writes the EXIF date text ("2019:08:03 14:22:10" plus an optional "+09:00").
 *
 * Android drops a photo's EXIF date when the file has no time zone tag and its file time is more
 * than a day away from the EXIF date (ModernMediaScanner.parseOptionalDateTaken). So we read the
 * date ourselves when Android has none, and always write the zone tag along with the date.
 */
object ExifDate {

    private val format = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

    /** EXIF dates without a zone are local camera time; [zone] is used for those. */
    fun parse(dateTime: String?, offset: String?, zone: ZoneId): Long? {
        val text = dateTime?.trim()?.takeIf { it.length >= 19 } ?: return null
        return runCatching {
            val local = LocalDateTime.parse(text.substring(0, 19), format)
            val zoned = offset?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { runCatching { ZoneOffset.of(it) }.getOrNull() }
            (if (zoned != null) local.atOffset(zoned).toInstant() else local.atZone(zone).toInstant()).toEpochMilli()
        }.getOrNull()
    }

    fun formatDate(epochMs: Long, zone: ZoneId): String =
        format.format(Instant.ofEpochMilli(epochMs).atZone(zone))

    /** The zone tag for [epochMs] in [zone], such as "+09:00". */
    fun formatOffset(epochMs: Long, zone: ZoneId): String {
        val offset = zone.rules.getOffset(Instant.ofEpochMilli(epochMs))
        return if (offset == ZoneOffset.UTC) "+00:00" else offset.id
    }
}
