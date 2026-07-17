package org.veraproject.veravideo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.veraproject.veravideo.R
import org.veraproject.veravideo.domain.Video
import org.veraproject.veravideo.util.formatDate
import org.veraproject.veravideo.util.formatDuration

/**
 * One video in a list: thumbnail, title, and the channel/date/duration line.
 *
 * [unavailable] dims the row and labels it rather than hiding it — inside a
 * playlist, silently dropping an entry the user added is worse than showing
 * that YouTube removed it.
 */
@Composable
fun VideoRow(
    video: Video,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    unavailable: Boolean = false,
    trailingContent: (@Composable () -> Unit)? = null,
    onOverflowClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = !unavailable, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Thumbnail(
            video = video,
            dimmed = unavailable,
            modifier = Modifier.width(148.dp),
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (unavailable) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA)
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )

            Text(
                text = if (unavailable) {
                    stringResource(R.string.video_unavailable)
                } else {
                    "${video.channelTitle} · ${formatDate(video.publishedAt)}"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (unavailable) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        trailingContent?.invoke()

        onOverflowClick?.let { onClick ->
            IconButton(onClick = onClick) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
            }
        }
    }
}

@Composable
private fun Thumbnail(
    video: Video,
    dimmed: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        AsyncImage(
            model = video.thumbnailUrl,
            contentDescription = stringResource(R.string.thumbnail_of, video.title),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .then(if (dimmed) Modifier.background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)) else Modifier),
        )

        // Duration sits on a scrim so it stays legible over any thumbnail.
        Text(
            text = formatDuration(video.durationSeconds),
            style = MaterialTheme.typography.labelMedium,
            color = androidx.compose.ui.graphics.Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.75f))
                .padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}

private const val DISABLED_ALPHA = 0.5f
