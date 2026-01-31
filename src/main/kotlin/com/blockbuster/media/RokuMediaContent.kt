package com.blockbuster.media

import java.time.Instant

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
 * @property metadata Channel-specific metadata (resume position, ratings, etc.)
 */
data class RokuMediaContent(
    val channelName: String? = null,
    val channelId: String,
    val contentId: String,
    val title: String? = null,
    val mediaType: String? = null,
    val metadata: Map<String, Any>? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
) : MediaContent {
    override fun toJson(): String = MediaJson.mapper.writeValueAsString(this)

    companion object Parser : MediaContentParser<RokuMediaContent> {
        override fun fromJson(json: String): RokuMediaContent =
            MediaJson.mapper.readValue(json, RokuMediaContent::class.java)
    }
}


