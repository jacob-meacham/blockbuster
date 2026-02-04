package com.blockbuster.media

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SpotifyMediaContentTest {
    @Test
    fun `should round-trip through JSON serialization`() {
        val content =
            SpotifyMediaContent(
                contentId = "4aawyAB9vmqN3uQ7FjRGTy",
                title = "OK Computer",
                spotifyUri = "spotify:album:4aawyAB9vmqN3uQ7FjRGTy",
                mediaType = "album",
                artist = "Radiohead",
                imageUrl = "https://i.scdn.co/image/abc123",
                description = "Classic album",
            )

        val json = content.toJson()
        val parsed = SpotifyMediaContent.fromJson(json)

        assertEquals(content, parsed)
    }

    @Test
    fun `should deserialize with only required fields`() {
        val json =
            """
            {
                "contentId": "abc123",
                "title": "My Playlist",
                "spotifyUri": "spotify:playlist:abc123",
                "mediaType": "playlist"
            }
            """.trimIndent()

        val parsed = SpotifyMediaContent.fromJson(json)

        assertEquals("abc123", parsed.contentId)
        assertEquals("My Playlist", parsed.title)
        assertEquals("spotify:playlist:abc123", parsed.spotifyUri)
        assertEquals("playlist", parsed.mediaType)
        assertNull(parsed.artist)
        assertNull(parsed.imageUrl)
        assertNull(parsed.description)
    }

    @Test
    fun `should ignore unknown properties`() {
        val json =
            """
            {
                "contentId": "abc123",
                "title": "Test",
                "spotifyUri": "spotify:album:abc123",
                "unknownField": "should be ignored",
                "anotherUnknown": 42
            }
            """.trimIndent()

        val parsed = SpotifyMediaContent.fromJson(json)

        assertEquals("abc123", parsed.contentId)
        assertEquals("Test", parsed.title)
    }

    @Test
    fun `should reject blank contentId`() {
        assertThrows(IllegalArgumentException::class.java) {
            SpotifyMediaContent(
                contentId = "  ",
                title = "Test",
                spotifyUri = "spotify:album:abc",
            )
        }
    }

    @Test
    fun `should reject blank title`() {
        assertThrows(IllegalArgumentException::class.java) {
            SpotifyMediaContent(
                contentId = "abc",
                title = "",
                spotifyUri = "spotify:album:abc",
            )
        }
    }

    @Test
    fun `should reject blank spotifyUri`() {
        assertThrows(IllegalArgumentException::class.java) {
            SpotifyMediaContent(
                contentId = "abc",
                title = "Test",
                spotifyUri = "",
            )
        }
    }

    @Test
    fun `should default mediaType to album`() {
        val content =
            SpotifyMediaContent(
                contentId = "abc",
                title = "Test",
                spotifyUri = "spotify:album:abc",
            )

        assertEquals("album", content.mediaType)
    }
}
