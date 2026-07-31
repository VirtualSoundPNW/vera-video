package org.veraproject.veravideo.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant

// These flags gate visible UI: the filter-row chips, the date-range chip, and
// the "Clear" button all appear only when they report true. A regression here
// fails silently — the button vanishes or lingers — so the logic is pinned down
// here rather than left to a Compose test.
class SearchQueryTest {

    private val someInstant = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun `an after bound alone counts as a date range`() {
        assertThat(SearchQuery(publishedAfter = someInstant).hasDateRange).isTrue()
    }

    @Test
    fun `a before bound alone counts as a date range`() {
        assertThat(SearchQuery(publishedBefore = someInstant).hasDateRange).isTrue()
    }

    @Test
    fun `no bounds is not a date range`() {
        assertThat(SearchQuery().hasDateRange).isFalse()
    }

    @Test
    fun `a date range counts as a filter`() {
        assertThat(SearchQuery(publishedAfter = someInstant).hasFilters).isTrue()
    }

    @Test
    fun `channel and duration bounds each count as filters`() {
        assertThat(SearchQuery(channelId = "UCvera").hasFilters).isTrue()
        assertThat(SearchQuery(minDurationSeconds = 60).hasFilters).isTrue()
        assertThat(SearchQuery(maxDurationSeconds = 60).hasFilters).isTrue()
    }

    @Test
    fun `plain text alone is not a filter`() {
        val query = SearchQuery(text = "chastity belt")

        assertThat(query.hasFilters).isFalse()
        assertThat(query.isEmpty).isFalse()
    }

    @Test
    fun `a query with neither text nor filters is empty`() {
        assertThat(SearchQuery().isEmpty).isTrue()
    }

    @Test
    fun `blank text with a filter is not empty`() {
        assertThat(SearchQuery(text = "   ", channelId = "UCvera").isEmpty).isFalse()
    }
}
