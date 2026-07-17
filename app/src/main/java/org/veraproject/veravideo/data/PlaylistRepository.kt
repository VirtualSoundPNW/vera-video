package org.veraproject.veravideo.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.veraproject.veravideo.data.local.PlaylistDao
import org.veraproject.veravideo.data.local.PlaylistEntity
import org.veraproject.veravideo.domain.Playlist
import org.veraproject.veravideo.domain.PlaylistWithVideos
import org.veraproject.veravideo.domain.Video
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepository @Inject constructor(
    private val playlistDao: PlaylistDao,
) {

    fun observePlaylists(): Flow<List<Playlist>> =
        playlistDao.observePlaylists().map { summaries -> summaries.map { it.toDomain() } }

    fun observePlaylistVideos(playlistId: Long): Flow<List<Video>> =
        playlistDao.observePlaylistVideos(playlistId).map { entities -> entities.map { it.toDomain() } }

    /** Which of the videos in this playlist are still playable. */
    fun observeUnavailableVideoIds(playlistId: Long): Flow<Set<String>> =
        playlistDao.observePlaylistVideos(playlistId).map { entities ->
            entities.filterNot { it.isAvailable }.mapTo(mutableSetOf()) { it.videoId }
        }

    fun observePlaylistWithVideos(playlistId: Long): Flow<PlaylistWithVideos?> =
        combine(
            playlistDao.observePlaylist(playlistId),
            playlistDao.observePlaylistVideos(playlistId),
        ) { entity, videos ->
            entity?.let {
                PlaylistWithVideos(
                    playlist = Playlist(
                        id = it.id,
                        name = it.name,
                        createdAt = it.createdAt,
                        updatedAt = it.updatedAt,
                        videoCount = videos.size,
                        thumbnailUrl = videos.firstOrNull()?.thumbnailUrl,
                    ),
                    videos = videos.map { video -> video.toDomain() },
                )
            }
        }

    fun observePlaylistIdsContaining(videoId: String): Flow<Set<Long>> =
        playlistDao.observePlaylistIdsContaining(videoId).map { it.toSet() }

    suspend fun create(name: String): Long {
        val now = Instant.now()
        return playlistDao.insertPlaylist(PlaylistEntity(name = name.trim(), createdAt = now, updatedAt = now))
    }

    suspend fun rename(playlistId: Long, name: String) =
        playlistDao.rename(playlistId, name.trim(), Instant.now())

    suspend fun delete(playlistId: Long) = playlistDao.deletePlaylist(playlistId)

    suspend fun addVideo(playlistId: Long, videoId: String) =
        playlistDao.addVideo(playlistId, videoId, Instant.now())

    suspend fun removeVideo(playlistId: Long, videoId: String) =
        playlistDao.removeVideo(playlistId, videoId, Instant.now())

    suspend fun moveVideo(playlistId: Long, fromIndex: Int, toIndex: Int) =
        playlistDao.moveVideo(playlistId, fromIndex, toIndex, Instant.now())
}
