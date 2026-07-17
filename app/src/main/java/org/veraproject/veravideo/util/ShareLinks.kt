package org.veraproject.veravideo.util

/**
 * Builds the links used by the share actions.
 *
 * Playlists are shared as a `watch_videos` URL, which makes YouTube assemble an
 * anonymous playlist from a list of ids. That means a shared playlist opens for
 * anyone, with no account and nothing hosted on our side.
 *
 * The trade-off: `watch_videos` is a long-standing but undocumented URL form,
 * so it could change without notice. [playlistUrl] returning null is the signal
 * to fall back to sharing plain per-video links.
 */
object ShareLinks {

    /** YouTube ignores ids past this count on a watch_videos URL. */
    const val MAX_PLAYLIST_VIDEOS = 50

    fun videoUrl(videoId: String): String = "https://www.youtube.com/watch?v=$videoId"

    /**
     * An anonymous-playlist URL for [videoIds], or null if there is nothing to
     * share. Ids beyond [MAX_PLAYLIST_VIDEOS] are dropped, since including them
     * would silently produce a truncated playlist anyway.
     */
    fun playlistUrl(videoIds: List<String>): String? {
        val ids = videoIds.filter { it.isNotBlank() }.take(MAX_PLAYLIST_VIDEOS)
        if (ids.isEmpty()) return null
        return "https://www.youtube.com/watch_videos?video_ids=${ids.joinToString(",")}"
    }

    /** True when a playlist is too long to share in full as one link. */
    fun isTruncated(videoIds: List<String>): Boolean = videoIds.size > MAX_PLAYLIST_VIDEOS

    /**
     * Share body for a playlist: the one-tap link plus a readable listing, so
     * the message is still useful if the link ever stops working.
     */
    fun playlistShareText(playlistName: String, videos: List<Pair<String, String>>): String {
        val url = playlistUrl(videos.map { it.first })
        return buildString {
            appendLine(playlistName)
            if (url != null) {
                appendLine()
                appendLine(url)
            }
            appendLine()
            videos.forEachIndexed { index, (videoId, title) ->
                appendLine("${index + 1}. $title — ${videoUrl(videoId)}")
            }
        }.trim()
    }

    fun videoShareText(title: String, videoId: String): String = "$title\n${videoUrl(videoId)}"
}
