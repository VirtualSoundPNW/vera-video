package org.veraproject.veravideo.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.veraproject.veravideo.data.local.VeraDatabase
import org.veraproject.veravideo.data.local.VideoDao
import org.veraproject.veravideo.data.local.VideoStatus
import org.veraproject.veravideo.data.prefs.SyncPrefs
import org.veraproject.veravideo.data.remote.CatalogApi
import org.veraproject.veravideo.domain.SearchQuery
import retrofit2.Retrofit
import java.io.File

@RunWith(RobolectricTestRunner::class)
class CatalogRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var database: VeraDatabase
    private lateinit var videoDao: VideoDao
    private lateinit var repository: CatalogRepository
    private lateinit var dataStoreFile: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var dataStoreScope: CoroutineScope

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            VeraDatabase::class.java,
        ).allowMainThreadQueries().build()
        videoDao = database.videoDao()

        dataStoreFile = File.createTempFile("sync_prefs", ".preferences_pb").apply { delete() }
        // A real scope, not TestScope: DataStore runs an internal actor, and a
        // TestScope's scheduler only advances inside runTest — so its first read
        // would block forever.
        dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        dataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) { dataStoreFile }

        val json = Json { ignoreUnknownKeys = true }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(CatalogApi::class.java)

        repository = CatalogRepository(api, videoDao, SyncPrefs(dataStore))
    }

    @After
    fun tearDown() {
        server.close()
        database.close()
        dataStoreScope.cancel()
        dataStoreFile.delete()
    }

    private fun catalogJson(
        videos: String,
        cursor: String = "2026-07-01T00:00:00Z",
        delta: Boolean = false,
    ) = """
        {
          "generatedAt": "2026-07-01T00:00:00Z",
          "cursor": "$cursor",
          "delta": $delta,
          "count": 0,
          "videos": [$videos]
        }
    """.trimIndent()

    private fun videoJson(
        id: String,
        title: String = "Video $id",
        status: String = "active",
        updatedAt: String = "2026-07-01T00:00:00Z",
    ) = """
        {
          "videoId": "$id",
          "title": "$title",
          "description": "",
          "channelId": "UCvera",
          "channelTitle": "Vera",
          "publishedAt": "2026-05-01T00:00:00Z",
          "durationSeconds": 200,
          "thumbnailUrl": null,
          "tags": [],
          "status": "$status",
          "updatedAt": "$updatedAt"
        }
    """.trimIndent()

    private fun enqueue(body: String, code: Int = 200, etag: String? = null) {
        val builder = MockResponse.Builder().code(code).body(body)
        etag?.let { builder.addHeader("ETag", it) }
        server.enqueue(builder.build())
    }

    private suspend fun storedIds() = videoDao.search(SearchQuery()).first().map { it.videoId }

    @Test
    fun `stores videos from a full sync`() = runTest {
        enqueue(catalogJson(listOf(videoJson("a"), videoJson("b")).joinToString(",")))

        val result = repository.sync()

        assertThat(result).isInstanceOf(SyncResult.Updated::class.java)
        assertThat(storedIds()).containsExactly("a", "b")
    }

    @Test
    fun `first sync requests the whole catalog with no cursor`() = runTest {
        enqueue(catalogJson(videoJson("a")))

        repository.sync()

        val request = server.takeRequest()
        assertThat(request.url.queryParameter("since")).isNull()
    }

    @Test
    fun `a later sync sends the stored cursor and etag`() = runTest {
        enqueue(catalogJson(videoJson("a"), cursor = "2026-07-05T00:00:00Z"), etag = "W/\"1\"")
        repository.sync()
        server.takeRequest()

        enqueue(catalogJson("", delta = true))
        repository.sync()

        val second = server.takeRequest()
        assertThat(second.url.queryParameter("since")).isEqualTo("2026-07-05T00:00:00Z")
        assertThat(second.headers["If-None-Match"]).isEqualTo("W/\"1\"")
    }

    @Test
    fun `a 304 reports not modified and keeps the catalog`() = runTest {
        enqueue(catalogJson(videoJson("a")), etag = "W/\"1\"")
        repository.sync()
        server.takeRequest()

        enqueue("", code = 304)
        val result = repository.sync()

        assertThat(result).isEqualTo(SyncResult.NotModified)
        assertThat(storedIds()).containsExactly("a")
    }

    // A full response lists only active videos, so anything active locally but
    // missing from it is gone. Reconciling this way (rather than wiping the
    // table) is what keeps playlist entries alive.
    @Test
    fun `a full sync retires videos the server no longer lists`() = runTest {
        enqueue(catalogJson(listOf(videoJson("a"), videoJson("b")).joinToString(",")))
        repository.sync()

        enqueue(catalogJson(videoJson("a")))
        val result = repository.sync()

        assertThat(storedIds()).containsExactly("a")
        assertThat(videoDao.getByIds(listOf("b")).single().status).isEqualTo(VideoStatus.REMOVED)
        assertThat((result as SyncResult.Updated).removed).isEqualTo(1)
    }

    @Test
    fun `a full sync does not delete rows so playlists keep their entries`() = runTest {
        enqueue(catalogJson(videoJson("b")))
        repository.sync()

        enqueue(catalogJson(videoJson("a")))
        repository.sync()

        // Still present, just not active.
        assertThat(videoDao.getByIds(listOf("b"))).hasSize(1)
    }

    @Test
    fun `a delta applies removals without touching untouched videos`() = runTest {
        enqueue(catalogJson(listOf(videoJson("a"), videoJson("b")).joinToString(",")))
        repository.sync()
        server.takeRequest()

        enqueue(catalogJson(videoJson("b", status = "removed"), delta = true))
        repository.sync()

        assertThat(storedIds()).containsExactly("a")
        assertThat(videoDao.getByIds(listOf("b")).single().status).isEqualTo(VideoStatus.REMOVED)
    }

    @Test
    fun `a delta does not retire videos merely absent from it`() = runTest {
        enqueue(catalogJson(listOf(videoJson("a"), videoJson("b")).joinToString(",")))
        repository.sync()

        enqueue(catalogJson(videoJson("a", title = "Retitled"), delta = true))
        repository.sync()

        // "b" was not in the delta, which means unchanged — not removed.
        assertThat(storedIds()).containsExactly("a", "b")
    }

    @Test
    fun `a delta updates metadata in place`() = runTest {
        enqueue(catalogJson(videoJson("a", title = "Old")))
        repository.sync()

        enqueue(catalogJson(videoJson("a", title = "New"), delta = true))
        repository.sync()

        assertThat(videoDao.getByIds(listOf("a")).single().title).isEqualTo("New")
    }

    @Test
    fun `a server error is reported and leaves the catalog intact`() = runTest {
        enqueue(catalogJson(videoJson("a")))
        repository.sync()

        enqueue("", code = 500)
        val result = repository.sync()

        assertThat(result).isInstanceOf(SyncResult.Failed::class.java)
        assertThat(storedIds()).containsExactly("a")
    }

    // Network failure must not throw: the local catalog is still usable and the
    // caller decides whether to surface it.
    @Test
    fun `a connection failure is reported rather than thrown`() = runTest {
        server.close()

        val result = repository.sync()

        assertThat(result).isInstanceOf(SyncResult.Failed::class.java)
    }

    @Test
    fun `malformed timestamps drop the video instead of dating it to the epoch`() = runTest {
        val bad = """
            {
              "videoId": "bad", "title": "Bad", "description": "",
              "channelId": "UCvera", "channelTitle": "Vera",
              "publishedAt": "not-a-date", "durationSeconds": 200,
              "thumbnailUrl": null, "tags": [], "status": "active",
              "updatedAt": "2026-07-01T00:00:00Z"
            }
        """.trimIndent()
        enqueue(catalogJson(listOf(videoJson("good"), bad).joinToString(",")))

        repository.sync()

        assertThat(storedIds()).containsExactly("good")
    }

    @Test
    fun `clearing the cache empties the catalog and forces a full resync`() = runTest {
        enqueue(catalogJson(videoJson("a"), cursor = "2026-07-05T00:00:00Z"))
        repository.sync()
        server.takeRequest()

        repository.clearCache()
        assertThat(storedIds()).isEmpty()

        enqueue(catalogJson(videoJson("a")))
        repository.sync()

        assertThat(server.takeRequest().url.queryParameter("since")).isNull()
    }
}
