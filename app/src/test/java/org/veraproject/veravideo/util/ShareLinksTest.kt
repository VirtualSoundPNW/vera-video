package org.veraproject.veravideo.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ShareLinksTest {

    @Test
    fun `builds a watch url for a video`() {
        assertThat(ShareLinks.videoUrl("dQw4w9WgXcQ"))
            .isEqualTo("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
    }

    @Test
    fun `builds an anonymous playlist url from video ids`() {
        assertThat(ShareLinks.playlistUrl(listOf("a", "b", "c")))
            .isEqualTo("https://www.youtube.com/watch_videos?video_ids=a,b,c")
    }

    @Test
    fun `a single video still produces a playlist url`() {
        assertThat(ShareLinks.playlistUrl(listOf("a")))
            .isEqualTo("https://www.youtube.com/watch_videos?video_ids=a")
    }

    // Sharing an empty playlist should offer nothing rather than a URL that
    // opens an error page on YouTube.
    @Test
    fun `an empty playlist has no url`() {
        assertThat(ShareLinks.playlistUrl(emptyList())).isNull()
    }

    @Test
    fun `blank ids are dropped`() {
        assertThat(ShareLinks.playlistUrl(listOf("a", "", "  ", "b")))
            .isEqualTo("https://www.youtube.com/watch_videos?video_ids=a,b")
    }

    @Test
    fun `a list of only blank ids has no url`() {
        assertThat(ShareLinks.playlistUrl(listOf("", "   "))).isNull()
    }

    // YouTube ignores ids past 50; including them would produce a link that
    // silently drops videos.
    @Test
    fun `caps the url at fifty videos`() {
        val ids = (1..80).map { "v$it" }

        val url = ShareLinks.playlistUrl(ids)!!

        assertThat(url.substringAfter("video_ids=").split(",")).hasSize(50)
        assertThat(url).contains("v50")
        assertThat(url).doesNotContain("v51")
    }

    @Test
    fun `reports truncation only past the cap`() {
        assertThat(ShareLinks.isTruncated((1..50).map { "v$it" })).isFalse()
        assertThat(ShareLinks.isTruncated((1..51).map { "v$it" })).isTrue()
    }

    @Test
    fun `playlist share text includes the link and a readable listing`() {
        val text = ShareLinks.playlistShareText(
            playlistName = "Vera favourites",
            videos = listOf("a" to "First song", "b" to "Second song"),
        )

        assertThat(text).contains("Vera favourites")
        assertThat(text).contains("https://www.youtube.com/watch_videos?video_ids=a,b")
        assertThat(text).contains("1. First song — https://www.youtube.com/watch?v=a")
        assertThat(text).contains("2. Second song — https://www.youtube.com/watch?v=b")
    }

    @Test
    fun `playlist share text for an empty playlist omits the link`() {
        val text = ShareLinks.playlistShareText("Empty", emptyList())

        assertThat(text).isEqualTo("Empty")
        assertThat(text).doesNotContain("watch_videos")
    }

    @Test
    fun `video share text pairs the title with its url`() {
        assertThat(ShareLinks.videoShareText("A title", "abc"))
            .isEqualTo("A title\nhttps://www.youtube.com/watch?v=abc")
    }
}
