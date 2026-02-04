package com.blockbuster.media

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.Instant

/**
 * Type-safe metadata for Roku media content.
 *
 * All fields are optional to support different channel types (Emby, Brave Search, etc.).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class RokuMediaMetadata(
    val description: String? = null,
    val overview: String? = null,
    val imageUrl: String? = null,
    val searchUrl: String? = null,
    val originalUrl: String? = null,
    val resumePositionTicks: Long? = null,
    val runtimeTicks: Long? = null,
    val playedPercentage: Double? = null,
    val isFavorite: Boolean? = null,
    val communityRating: Double? = null,
    val officialRating: String? = null,
    val genres: List<String>? = null,
    val serverId: String? = null,
    val itemType: String? = null,
    val seriesName: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val year: Int? = null,
)

/**
 * Roku media content that can be played on any Roku channel.
 *
 * This is a generic container that works with RokuChannelPlugin implementations
 * to determine how to actually launch the content (deep link vs action sequence).
 *
 * @property channelName Human-readable channel name (e.g., "Emby", "Netflix")
 * @property channelId Roku channel ID (e.g., "44191" for Emby, "12" for Netflix)
 * @property contentId Channel-specific content identifier (e.g., Emby item ID)
 * @property title Content title for display and fallback search
 * @property mediaType Channel-specific media type (e.g., "Movie", "Episode")
 * @property metadata Typed channel-specific metadata (resume position, ratings, etc.)
 */
data class RokuMediaContent(
    val channelName: String? = null,
    val channelId: String,
    val contentId: String,
    val title: String? = null,
    val mediaType: String? = null,
    val metadata: RokuMediaMetadata? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
) : MediaContent {
    override fun toJson(): String = MediaJson.mapper.writeValueAsString(this)

    companion object Parser : MediaContentParser<RokuMediaContent> {
        override fun fromJson(json: String): RokuMediaContent = MediaJson.mapper.readValue(json, RokuMediaContent::class.java)
    }
}
