package org.veraproject.veravideo.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.veraproject.veravideo.domain.SearchQuery
import org.veraproject.veravideo.domain.SortOrder
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class VideoDaoTest {

    private lateinit var database: VeraDatabase
    private lateinit var dao: VideoDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            VeraDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.videoDao()
    }

    @After
    fun tearDown() = database.close()

    private fun video(
        id: String,
        title: String = "Video $id",
        description: String = "",
        channelId: String = "UCvera",
        channelTitle: String = "The Vera Project",
        publishedAt: Instant = Instant.parse("2026-01-01T00:00:00Z"),
        durationSeconds: Int? = 300,
        tags: List<String> = emptyList(),
        status: String = VideoStatus.ACTIVE,
    ) = VideoEntity(
        videoId = id,
        title = title,
        description = description,
        channelId = channelId,
        channelTitle = channelTitle,
        publishedAt = publishedAt,
        durationSeconds = durationSeconds,
        thumbnailUrl = null,
        tags = tags,
        status = status,
        updatedAt = Instant.parse("2026-07-01T00:00:00Z"),
    )

    private suspend fun search(query: SearchQuery) = dao.search(query).first().map { it.videoId }

    // ---- full-text search -------------------------------------------------

    // The FTS table is external-content: it holds no data of its own and relies
    // on Room's generated triggers. If those were absent, search would silently
    // return nothing, so this is the load-bearing test for the whole feature.
    @Test
    fun `finds a video by a word in its title`() = runTest {
        dao.upsertAll(listOf(video("a", title = "Chastity Belt live set"), video("b", title = "Unrelated")))

        assertThat(search(SearchQuery(text = "chastity"))).containsExactly("a")
    }

    @Test
    fun `matches on a prefix so results appear while typing`() = runTest {
        dao.upsertAll(listOf(video("a", title = "Chastity Belt live set")))

        assertThat(search(SearchQuery(text = "chast"))).containsExactly("a")
    }

    @Test
    fun `searches the description as well as the title`() = runTest {
        dao.upsertAll(listOf(video("a", title = "Live set", description = "Recorded at the matinee show")))

        assertThat(search(SearchQuery(text = "matinee"))).containsExactly("a")
    }

    @Test
    fun `searches tags`() = runTest {
        dao.upsertAll(listOf(video("a", title = "Live set", tags = listOf("punk", "all ages"))))

        assertThat(search(SearchQuery(text = "punk"))).containsExactly("a")
    }

    @Test
    fun `searches the channel name`() = runTest {
        dao.upsertAll(listOf(video("a", title = "Set", channelTitle = "KEXP")))

        assertThat(search(SearchQuery(text = "kexp"))).containsExactly("a")
    }

    @Test
    fun `multiple words must all match`() = runTest {
        // Neutral channel title: the default one contains "Vera", which would
        // legitimately match both rows via the channel name.
        dao.upsertAll(
            listOf(
                video("both", title = "Chastity Belt at Vera", channelTitle = "Live Sets"),
                video("one", title = "Chastity Belt at the Showbox", channelTitle = "Live Sets"),
            ),
        )

        assertThat(search(SearchQuery(text = "chastity vera"))).containsExactly("both")
    }

    @Test
    fun `is case insensitive`() = runTest {
        dao.upsertAll(listOf(video("a", title = "Chastity Belt")))

        assertThat(search(SearchQuery(text = "CHASTITY"))).containsExactly("a")
    }

    // The FTS index must track edits, or a renamed video stays findable only by
    // its old title.
    @Test
    fun `reflects an updated title`() = runTest {
        dao.upsertAll(listOf(video("a", title = "Original title")))
        dao.upsertAll(listOf(video("a", title = "Renamed to Chastity Belt")))

        assertThat(search(SearchQuery(text = "chastity"))).containsExactly("a")
        assertThat(search(SearchQuery(text = "original"))).isEmpty()
    }

    @Test
    fun `stops matching a deleted video`() = runTest {
        dao.upsertAll(listOf(video("a", title = "Chastity Belt")))
        dao.clear()

        assertThat(search(SearchQuery(text = "chastity"))).isEmpty()
    }

    // Punctuation is FTS MATCH syntax; unsanitized input throws rather than
    // returning no results, which would crash the search box mid-typing.
    @Test
    fun `does not crash on punctuation that is FTS syntax`() = runTest {
        dao.upsertAll(listOf(video("a", title = "Chastity Belt")))

        listOf("\"", "-", "*", "^", "chastity \"belt", "NOT belt", "a OR b", "(", ":").forEach { input ->
            dao.search(SearchQuery(text = input)).first()
        }
    }

    @Test
    fun `blank text returns everything rather than nothing`() = runTest {
        dao.upsertAll(listOf(video("a"), video("b")))

        assertThat(search(SearchQuery(text = "   "))).containsExactly("a", "b")
    }

    @Test
    fun `punctuation-only text is treated as no search`() = runTest {
        dao.upsertAll(listOf(video("a"), video("b")))

        assertThat(search(SearchQuery(text = "!!!"))).containsExactly("a", "b")
    }

    // ---- filters ----------------------------------------------------------

    @Test
    fun `excludes removed videos from browsing`() = runTest {
        dao.upsertAll(listOf(video("live"), video("gone", status = VideoStatus.REMOVED)))

        assertThat(search(SearchQuery())).containsExactly("live")
    }

    @Test
    fun `filters by channel`() = runTest {
        dao.upsertAll(listOf(video("a", channelId = "UCvera"), video("b", channelId = "UCother")))

        assertThat(search(SearchQuery(channelId = "UCother"))).containsExactly("b")
    }

    @Test
    fun `filters by minimum duration`() = runTest {
        dao.upsertAll(listOf(video("short", durationSeconds = 60), video("long", durationSeconds = 3600)))

        assertThat(search(SearchQuery(minDurationSeconds = 600))).containsExactly("long")
    }

    @Test
    fun `filters by maximum duration`() = runTest {
        dao.upsertAll(listOf(video("short", durationSeconds = 60), video("long", durationSeconds = 3600)))

        assertThat(search(SearchQuery(maxDurationSeconds = 600))).containsExactly("short")
    }

    @Test
    fun `filters by published date range`() = runTest {
        dao.upsertAll(
            listOf(
                video("old", publishedAt = Instant.parse("2020-01-01T00:00:00Z")),
                video("new", publishedAt = Instant.parse("2026-06-01T00:00:00Z")),
            ),
        )

        assertThat(search(SearchQuery(publishedAfter = Instant.parse("2025-01-01T00:00:00Z"))))
            .containsExactly("new")
    }

    @Test
    fun `combines text search with filters`() = runTest {
        dao.upsertAll(
            listOf(
                video("match", title = "Chastity Belt", channelId = "UCvera", durationSeconds = 1200),
                video("wrongChannel", title = "Chastity Belt", channelId = "UCother", durationSeconds = 1200),
                video("tooShort", title = "Chastity Belt", channelId = "UCvera", durationSeconds = 30),
            ),
        )

        val results = search(
            SearchQuery(text = "chastity", channelId = "UCvera", minDurationSeconds = 600),
        )

        assertThat(results).containsExactly("match")
    }

    // ---- sorting ----------------------------------------------------------

    @Test
    fun `sorts newest first by default`() = runTest {
        dao.upsertAll(
            listOf(
                video("old", publishedAt = Instant.parse("2020-01-01T00:00:00Z")),
                video("new", publishedAt = Instant.parse("2026-06-01T00:00:00Z")),
            ),
        )

        assertThat(search(SearchQuery())).containsExactly("new", "old").inOrder()
    }

    @Test
    fun `sorts oldest first`() = runTest {
        dao.upsertAll(
            listOf(
                video("old", publishedAt = Instant.parse("2020-01-01T00:00:00Z")),
                video("new", publishedAt = Instant.parse("2026-06-01T00:00:00Z")),
            ),
        )

        assertThat(search(SearchQuery(sortOrder = SortOrder.OLDEST))).containsExactly("old", "new").inOrder()
    }

    @Test
    fun `sorts longest first`() = runTest {
        dao.upsertAll(listOf(video("short", durationSeconds = 60), video("long", durationSeconds = 3600)))

        assertThat(search(SearchQuery(sortOrder = SortOrder.LONGEST))).containsExactly("long", "short").inOrder()
    }

    @Test
    fun `sorts by title ignoring case`() = runTest {
        dao.upsertAll(listOf(video("b", title = "banana"), video("a", title = "Apple")))

        assertThat(search(SearchQuery(sortOrder = SortOrder.TITLE))).containsExactly("a", "b").inOrder()
    }

    // ---- other queries ----------------------------------------------------

    @Test
    fun `groups channels with counts and ignores removed videos`() = runTest {
        dao.upsertAll(
            listOf(
                video("a", channelId = "UCvera", channelTitle = "Vera"),
                video("b", channelId = "UCvera", channelTitle = "Vera"),
                video("c", channelId = "UCkexp", channelTitle = "KEXP"),
                video("d", channelId = "UCkexp", channelTitle = "KEXP", status = VideoStatus.REMOVED),
            ),
        )

        val channels = dao.observeChannels().first()

        assertThat(channels).hasSize(2)
        assertThat(channels.first { it.channelId == "UCvera" }.videoCount).isEqualTo(2)
        assertThat(channels.first { it.channelId == "UCkexp" }.videoCount).isEqualTo(1)
    }

    @Test
    fun `upsert preserves tags round trip`() = runTest {
        dao.upsertAll(listOf(video("a", tags = listOf("punk", "all ages", "seattle"))))

        val stored = dao.getByIds(listOf("a")).single()

        assertThat(stored.tags).containsExactly("punk", "all ages", "seattle").inOrder()
    }

    @Test
    fun `active ids exclude removed videos`() = runTest {
        dao.upsertAll(listOf(video("live"), video("gone", status = VideoStatus.REMOVED)))

        assertThat(dao.activeVideoIds()).containsExactly("live")
    }

    @Test
    fun `updateStatus marks videos removed`() = runTest {
        dao.upsertAll(listOf(video("a"), video("b")))

        dao.updateStatus(listOf("a"), VideoStatus.REMOVED)

        assertThat(dao.activeVideoIds()).containsExactly("b")
    }
}
