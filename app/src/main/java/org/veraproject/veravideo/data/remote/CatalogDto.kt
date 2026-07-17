package org.veraproject.veravideo.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Mirrors the `CatalogResponse` served by vera-video-backend. */
@Serializable
data class CatalogResponseDto(
    val generatedAt: String,
    val cursor: String? = null,
    val delta: Boolean = false,
    val count: Int = 0,
    val videos: List<CatalogVideoDto> = emptyList(),
)

@Serializable
data class CatalogVideoDto(
    val videoId: String,
    val title: String,
    val description: String = "",
    val channelId: String = "",
    val channelTitle: String = "",
    val publishedAt: String,
    val durationSeconds: Int? = null,
    val thumbnailUrl: String? = null,
    val tags: List<String> = emptyList(),
    @SerialName("status") val status: String = "active",
    val updatedAt: String,
)
