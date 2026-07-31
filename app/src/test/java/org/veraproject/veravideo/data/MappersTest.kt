package org.veraproject.veravideo.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.veraproject.veravideo.data.remote.CatalogVideoDto
import org.veraproject.veravideo.domain.SavedSearch
import org.veraproject.veravideo.domain.SearchQuery
import org.veraproject.veravideo.domain.SortOrder
import java.time.Instant

class MappersTest {

    // A saved search stores every filter, including the date window. If a bound
    // were dropped in either direction of the mapping, a saved date-range search
    // would silently come back narrower or wider than the user left it.
    @Test
    fun `a saved search round-trips every filter through the entity`() {
        val saved = SavedSearch(
            id = 7,
            name = "Recent Vera sets",
            query = SearchQuery(
                text = "live",
                channelId = "UCvera",
                minDurationSeconds = 300,
                maxDurationSeconds = 1200,
                publishedAfter = Instant.parse("2024-01-01T00:00:00Z"),
                publishedBefore = Instant.parse("2024-12-31T23:59:59.999Z"),
                sortOrder = SortOrder.OLDEST,
            ),
            createdAt = Instant.parse("2026-07-01T00:00:00Z"),
        )

        val restored = saved.toEntity().toDomain()

        assertThat(restored).isEqualTo(saved)
    }

    // Sort order is stored as its enum name. A value written by an older build
    // whose enum constant no longer exists must degrade to the default rather
    // than crash when the saved search is read back.
    @Test
    fun `an unknown stored sort order falls back to newest`() {
        val entity = SavedSearch(
            name = "Legacy",
            query = SearchQuery(sortOrder = SortOrder.TITLE),
            createdAt = Instant.parse("2026-07-01T00:00:00Z"),
        ).toEntity().copy(sortOrder = "SOME_REMOVED_ORDER")

        assertThat(entity.toDomain().query.sortOrder).isEqualTo(SortOrder.NEWEST)
    }

    @Test
    fun `a valid catalog video maps to an entity`() {
        val entity = dto(publishedAt = "2026-05-01T00:00:00Z", updatedAt = "2026-07-01T00:00:00Z").toEntity()

        assertThat(entity).isNotNull()
        assertThat(entity!!.videoId).isEqualTo("v1")
        assertThat(entity.publishedAt).isEqualTo(Instant.parse("2026-05-01T00:00:00Z"))
    }

    // Mapped to null rather than defaulted to the epoch: a video dated 1970 would
    // sit at the bottom of every "newest first" list forever, unnoticed.
    @Test
    fun `a video with an unparseable published date is dropped`() {
        assertThat(dto(publishedAt = "not-a-date").toEntity()).isNull()
    }

    @Test
    fun `a video with an unparseable updated date is dropped`() {
        assertThat(dto(updatedAt = "").toEntity()).isNull()
    }

    private fun dto(
        publishedAt: String = "2026-05-01T00:00:00Z",
        updatedAt: String = "2026-07-01T00:00:00Z",
    ) = CatalogVideoDto(
        videoId = "v1",
        title = "A set",
        publishedAt = publishedAt,
        updatedAt = updatedAt,
    )
}
