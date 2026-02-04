package com.blockbuster.media

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Content data for a Spotify album or playlist stored in the media library.
 *
 * @property contentId Spotify album/playlist ID (e.g. "4aawyAB9vmqN3uQ7FjRGTy")
 * @property title Human-readable title
 * @property spotifyUri Full Spotify URI (e.g. "spotify:album:4aawyAB9vmqN3uQ7FjRGTy")
 * @property mediaType "album" or "playlist"
 * @property artist Artist name (for albums)
 * @property imageUrl Album/playlist art URL
 * @property description Optional description
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class SpotifyMediaContent(
    val contentId: String,
    val title: String,
    val spotifyUri: String,
    val mediaType: String = "album",
    val artist: String? = null,
    val imageUrl: String? = null,
    val description: String? = null,
) : MediaContent {
    init {
        require(contentId.isNotBlank()) { "contentId must not be blank" }
        require(title.isNotBlank()) { "title must not be blank" }
        require(spotifyUri.isNotBlank()) { "spotifyUri must not be blank" }
    }

    override fun toJson(): String = MediaJson.mapper.writeValueAsString(this)

    companion object Parser : MediaContentParser<SpotifyMediaContent> {
        override fun fromJson(json: String): SpotifyMediaContent = MediaJson.mapper.readValue(json, SpotifyMediaContent::class.java)
    }
}
