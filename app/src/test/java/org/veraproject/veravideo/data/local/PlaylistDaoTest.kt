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
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class PlaylistDaoTest {

    private lateinit var database: VeraDatabase
    private lateinit var dao: PlaylistDao
    private lateinit var videoDao: VideoDao

    private val now: Instant = Instant.parse("2026-07-01T00:00:00Z")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            VeraDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.playlistDao()
        videoDao = database.videoDao()
    }

    @After
    fun tearDown() = database.close()

    private suspend fun seedVideos(vararg ids: String) {
        videoDao.upsertAll(
            ids.map { id ->
                VideoEntity(
                    videoId = id,
                    title = "Video $id",
                    description = "",
                    channelId = "UCvera",
                    channelTitle = "Vera",
                    publishedAt = now,
                    durationSeconds = 100,
                    thumbnailUrl = "https://img.test/$id.jpg",
                    tags = emptyList(),
                    status = VideoStatus.ACTIVE,
                    updatedAt = now,
                )
            },
        )
    }

    private suspend fun createPlaylist(name: String = "Favourites"): Long =
        dao.insertPlaylist(PlaylistEntity(name = name, createdAt = now, updatedAt = now))

    private suspend fun order(playlistId: Long) = dao.orderedVideoIds(playlistId)

    @Test
    fun `appends videos in the order they are added`() = runTest {
        seedVideos("a", "b", "c")
        val id = createPlaylist()

        dao.addVideo(id, "a", now)
        dao.addVideo(id, "b", now)
        dao.addVideo(id, "c", now)

        assertThat(order(id)).containsExactly("a", "b", "c").inOrder()
    }

    // Adding the same video twice is an easy double-tap; it must not blow up on
    // the composite primary key.
    @Test
    fun `adding the same video twice is a no-op`() = runTest {
        seedVideos("a")
        val id = createPlaylist()

        dao.addVideo(id, "a", now)
        dao.addVideo(id, "a", now)

        assertThat(order(id)).containsExactly("a")
    }

    @Test
    fun `the same video can live in two playlists`() = runTest {
        seedVideos("a")
        val first = createPlaylist("First")
        val second = createPlaylist("Second")

        dao.addVideo(first, "a", now)
        dao.addVideo(second, "a", now)

        assertThat(order(first)).containsExactly("a")
        assertThat(order(second)).containsExactly("a")
    }

    @Test
    fun `removing a video closes the gap in positions`() = runTest {
        seedVideos("a", "b", "c")
        val id = createPlaylist()
        listOf("a", "b", "c").forEach { dao.addVideo(id, it, now) }

        dao.removeVideo(id, "b", now)

        assertThat(order(id)).containsExactly("a", "c").inOrder()
        // Positions must stay dense, or the next append would collide.
        val positions = database.query("SELECT position FROM playlist_items ORDER BY position", null).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getInt(0)) }
        }
        assertThat(positions).containsExactly(0, 1).inOrder()
    }

    @Test
    fun `appending after a removal does not reuse a position`() = runTest {
        seedVideos("a", "b", "c")
        val id = createPlaylist()
        dao.addVideo(id, "a", now)
        dao.addVideo(id, "b", now)
        dao.removeVideo(id, "a", now)
        dao.addVideo(id, "c", now)

        assertThat(order(id)).containsExactly("b", "c").inOrder()
    }

    @Test
    fun `moves a video down`() = runTest {
        seedVideos("a", "b", "c")
        val id = createPlaylist()
        listOf("a", "b", "c").forEach { dao.addVideo(id, it, now) }

        dao.moveVideo(id, fromIndex = 0, toIndex = 2, now = now)

        assertThat(order(id)).containsExactly("b", "c", "a").inOrder()
    }

    @Test
    fun `moves a video up`() = runTest {
        seedVideos("a", "b", "c")
        val id = createPlaylist()
        listOf("a", "b", "c").forEach { dao.addVideo(id, it, now) }

        dao.moveVideo(id, fromIndex = 2, toIndex = 0, now = now)

        assertThat(order(id)).containsExactly("c", "a", "b").inOrder()
    }

    @Test
    fun `moving to an out-of-range index leaves the order untouched`() = runTest {
        seedVideos("a", "b")
        val id = createPlaylist()
        listOf("a", "b").forEach { dao.addVideo(id, it, now) }

        dao.moveVideo(id, fromIndex = 0, toIndex = 5, now = now)

        assertThat(order(id)).containsExactly("a", "b").inOrder()
    }

    @Test
    fun `moving an item onto itself changes nothing`() = runTest {
        seedVideos("a", "b")
        val id = createPlaylist()
        listOf("a", "b").forEach { dao.addVideo(id, it, now) }

        dao.moveVideo(id, fromIndex = 1, toIndex = 1, now = now)

        assertThat(order(id)).containsExactly("a", "b").inOrder()
    }

    @Test
    fun `deleting a playlist removes its items`() = runTest {
        seedVideos("a")
        val id = createPlaylist()
        dao.addVideo(id, "a", now)

        dao.deletePlaylist(id)

        assertThat(order(id)).isEmpty()
    }

    @Test
    fun `summary reports the video count and a cover image`() = runTest {
        seedVideos("a", "b")
        val id = createPlaylist("Mix")
        dao.addVideo(id, "a", now)
        dao.addVideo(id, "b", now)

        val summary = dao.observePlaylists().first().single()

        assertThat(summary.name).isEqualTo("Mix")
        assertThat(summary.videoCount).isEqualTo(2)
        // The cover is the first item's thumbnail, not an arbitrary one.
        assertThat(summary.thumbnailUrl).isEqualTo("https://img.test/a.jpg")
    }

    @Test
    fun `an empty playlist reports zero videos and no cover`() = runTest {
        createPlaylist("Empty")

        val summary = dao.observePlaylists().first().single()

        assertThat(summary.videoCount).isEqualTo(0)
        assertThat(summary.thumbnailUrl).isNull()
    }

    // A video removed from YouTube stays in the catalog as 'removed', so the
    // playlist entry must survive rather than silently disappearing.
    @Test
    fun `keeps an entry whose video is no longer available`() = runTest {
        seedVideos("a", "gone")
        val id = createPlaylist()
        dao.addVideo(id, "a", now)
        dao.addVideo(id, "gone", now)

        videoDao.updateStatus(listOf("gone"), VideoStatus.REMOVED)

        val videos = dao.observePlaylistVideos(id).first()
        assertThat(videos.map { it.videoId }).containsExactly("a", "gone").inOrder()
        assertThat(videos.first { it.videoId == "gone" }.status).isEqualTo(VideoStatus.REMOVED)
    }

    @Test
    fun `reports which playlists contain a video`() = runTest {
        seedVideos("a")
        val first = createPlaylist("First")
        createPlaylist("Second")
        dao.addVideo(first, "a", now)

        assertThat(dao.observePlaylistIdsContaining("a").first()).containsExactly(first)
    }

    @Test
    fun `adding a video touches the playlist so it sorts to the top`() = runTest {
        seedVideos("a")
        val id = createPlaylist()
        val later = now.plusSeconds(3600)

        dao.addVideo(id, "a", later)

        assertThat(dao.observePlaylist(id).first()!!.updatedAt).isEqualTo(later)
    }
}
