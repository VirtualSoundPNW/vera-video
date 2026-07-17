package org.veraproject.veravideo.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class FormattingTest {

    @Test
    fun `formats sub-hour durations as minutes and seconds`() {
        assertThat(formatDuration(222)).isEqualTo("3:42")
    }

    @Test
    fun `pads seconds`() {
        assertThat(formatDuration(65)).isEqualTo("1:05")
    }

    @Test
    fun `formats durations over an hour with hours`() {
        assertThat(formatDuration(3723)).isEqualTo("1:02:03")
    }

    @Test
    fun `formats exactly one hour`() {
        assertThat(formatDuration(3600)).isEqualTo("1:00:00")
    }

    @Test
    fun `formats zero`() {
        assertThat(formatDuration(0)).isEqualTo("0:00")
    }

    // Duration is nullable in the catalog (YouTube can omit it for live
    // streams), so the UI needs something printable rather than a crash.
    @Test
    fun `renders an unknown duration as a dash`() {
        assertThat(formatDuration(null)).isEqualTo("—")
    }

    @Test
    fun `renders a negative duration as a dash rather than nonsense`() {
        assertThat(formatDuration(-5)).isEqualTo("—")
    }

    // Locale is passed explicitly so the assertion does not depend on whatever
    // locale the machine running the tests happens to have.
    @Test
    fun `formats a date in the given zone`() {
        val instant = Instant.parse("2026-05-01T12:00:00Z")

        assertThat(formatDate(instant, ZoneId.of("UTC"), Locale.UK)).isEqualTo("1 May 2026")
    }

    // The instant is UTC but the user is in Seattle, where it is still April.
    @Test
    fun `formats a date using the local zone not UTC`() {
        val instant = Instant.parse("2026-05-01T03:00:00Z")

        assertThat(formatDate(instant, ZoneId.of("America/Los_Angeles"), Locale.UK)).isEqualTo("30 Apr 2026")
    }

    @Test
    fun `formats a date in the given locale`() {
        val instant = Instant.parse("2026-05-01T12:00:00Z")

        assertThat(formatDate(instant, ZoneId.of("UTC"), Locale.FRANCE)).isEqualTo("1 mai 2026")
    }
}
