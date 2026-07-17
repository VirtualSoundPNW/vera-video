package org.veraproject.veravideo.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "3:42", or "1:02:03" once there is an hour. Null duration renders as an em dash. */
fun formatDuration(seconds: Int?): String {
    if (seconds == null || seconds < 0) return "—"

    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    // Locale.US, not the default: these are clock-style numerals, and a locale
    // with non-Latin digits would render a duration the player cannot echo.
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, secs)
    }
}

/**
 * The formatter is built per call rather than held in a static field: the user
 * can change locale while the app is running, and a captured
 * `Locale.getDefault()` would keep formatting dates in the old one.
 */
fun formatDate(
    instant: Instant,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String = DateTimeFormatter.ofPattern("d MMM yyyy", locale).withZone(zone).format(instant)
