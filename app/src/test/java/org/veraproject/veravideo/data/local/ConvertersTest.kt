package org.veraproject.veravideo.data.local

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant

// Room persists every entity through these converters. A break here corrupts
// stored data silently — timestamps land on the wrong day, tags vanish — so the
// round-trips and the awkward edges (empty list, blank tokens) are pinned here.
class ConvertersTest {

    private val converters = Converters()

    @Test
    fun `an instant survives a round trip through millis`() {
        val instant = Instant.parse("2026-05-01T12:34:56Z")

        val restored = converters.longToInstant(converters.instantToLong(instant))

        assertThat(restored).isEqualTo(instant)
    }

    @Test
    fun `a null instant round-trips as null`() {
        assertThat(converters.instantToLong(null)).isNull()
        assertThat(converters.longToInstant(null)).isNull()
    }

    @Test
    fun `a tag list survives a round trip`() {
        val tags = listOf("punk", "all ages", "live set")

        val restored = converters.stringToTags(converters.tagsToString(tags))

        assertThat(restored).containsExactly("punk", "all ages", "live set").inOrder()
    }

    @Test
    fun `an empty tag list becomes an empty string and back`() {
        assertThat(converters.tagsToString(emptyList())).isEmpty()
        assertThat(converters.stringToTags("")).isEmpty()
    }

    @Test
    fun `a null tag list is treated as empty`() {
        assertThat(converters.tagsToString(null)).isEmpty()
        assertThat(converters.stringToTags(null)).isEmpty()
    }

    // A trailing or doubled separator (e.g. from an empty tag) must not surface
    // as a blank entry that would then be indexed as an empty FTS token.
    @Test
    fun `blank tags are dropped when reading back`() {
        assertThat(converters.stringToTags("punk\n\n\nrock")).containsExactly("punk", "rock").inOrder()
    }
}
