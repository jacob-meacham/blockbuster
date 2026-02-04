package com.blockbuster.plugin.spotify

import com.blockbuster.media.MediaStore
import com.blockbuster.media.SpotifyMediaContent
import com.blockbuster.plugin.PluginException
import com.blockbuster.plugin.SearchOptions
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SpotifyPluginTest {
    private lateinit var mockServer: MockWebServer
    private lateinit var httpClient: OkHttpClient
    private lateinit var mockMediaStore: MediaStore
    private lateinit var mockTokenStore: SpotifyTokenStore

    @BeforeEach
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()
        httpClient = OkHttpClient()
        mockMediaStore = mock()
        mockTokenStore = mock()
    }

    @AfterEach
    fun tearDown() {
        mockServer.shutdown()
    }

    private fun createPlugin(): SpotifyPlugin {
        return SpotifyPlugin(
            clientId = "test-client-id",
            clientSecret = "test-client-secret",
            mediaStore = mockMediaStore,
            httpClient = httpClient,
            tokenStore = mockTokenStore,
            apiBaseUrl = mockServer.url("/v1").toString(),
            tokenUrl = mockServer.url("/api/token").toString(),
        )
    }

    @Test
    fun `should have correct plugin name and description`() {
        val plugin = createPlugin()

        assertEquals("spotify", plugin.getPluginName())
        assertTrue(plugin.getDescription().contains("Spotify"))
    }

    @Test
    fun `should return SpotifyMediaContent parser`() {
        val plugin = createPlugin()

        val parser = plugin.getContentParser()
        val content =
            parser.fromJson(
                """
                {"contentId":"abc","title":"Test","spotifyUri":"spotify:album:abc"}
                """.trimIndent(),
            )

        assertEquals("abc", content.contentId)
        assertEquals("Test", content.title)
    }

    @Test
    fun `should build correct authorization URL`() {
        val plugin = createPlugin()

        val url = plugin.buildAuthorizationUrl("http://localhost:8584")

        assertTrue(url.startsWith("https://accounts.spotify.com/authorize"))
        assertTrue(url.contains("client_id=test-client-id"))
        assertTrue(url.contains("response_type=code"))
        assertTrue(url.contains("redirect_uri="))
        assertTrue(url.contains("scope="))
    }

    @Test
    fun `should delegate isAuthenticated to token store`() {
        val plugin = createPlugin()

        whenever(mockTokenStore.isAuthenticated()).thenReturn(true)
        assertTrue(plugin.isAuthenticated())

        whenever(mockTokenStore.isAuthenticated()).thenReturn(false)
        assertFalse(plugin.isAuthenticated())
    }

    @Test
    fun `should throw when content not found in media store`() {
        val plugin = createPlugin()

        whenever(mockMediaStore.getParsed("uuid-missing", "spotify", SpotifyMediaContent.Parser))
            .thenReturn(null)

        val exception =
            assertThrows(PluginException::class.java) {
                plugin.play("uuid-missing")
            }

        assertTrue(exception.message!!.contains("Content not found"))
    }

    @Test
    fun `should throw when not authenticated for playback`() {
        val plugin = createPlugin()

        val content =
            SpotifyMediaContent(
                contentId = "abc",
                title = "Test Album",
                spotifyUri = "spotify:album:abc",
            )
        whenever(mockMediaStore.getParsed("uuid-123", "spotify", SpotifyMediaContent.Parser))
            .thenReturn(content)
        whenever(mockTokenStore.getAccessToken()).thenReturn(null)

        val exception =
            assertThrows(PluginException::class.java) {
                plugin.play("uuid-123")
            }

        assertTrue(exception.message!!.contains("not authenticated"))
    }

    // ── Search tests ────────────────────────────────────────────────────────

    @Test
    fun `search should return albums and playlists`() {
        val plugin = createPlugin()

        // First request: client credentials token
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                        "access_token": "client-token-123",
                        "token_type": "Bearer",
                        "expires_in": 3600
                    }
                    """.trimIndent(),
                ),
        )

        // Second request: search results
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(spotifySearchResponse()),
        )

        val results = plugin.search("radiohead", SearchOptions(limit = 10))

        assertEquals(3, results.size)

        // Verify albums
        val album = results.first { it.content.mediaType == "album" }
        assertEquals("OK Computer", album.title)
        assertEquals("Spotify", album.source)
        assertEquals("4aawyAB9vmqN3uQ7FjRGTy", album.dedupKey)
        assertEquals("spotify:album:4aawyAB9vmqN3uQ7FjRGTy", album.content.spotifyUri)
        assertEquals("Radiohead", album.content.artist)
        assertNotNull(album.content.imageUrl)
        assertEquals("Album by Radiohead", album.description)

        // Verify playlists
        val playlists = results.filter { it.content.mediaType == "playlist" }
        assertEquals(2, playlists.size)
    }

    @Test
    fun `search should throw when client credentials token fails`() {
        val plugin = createPlugin()

        // Token request fails
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error":"invalid_client"}"""),
        )

        val exception =
            assertThrows(PluginException::class.java) {
                plugin.search("test", SearchOptions(limit = 10))
            }

        assertTrue(exception.message!!.contains("client credentials"))
    }

    @Test
    fun `search should throw when search API returns error`() {
        val plugin = createPlugin()

        // Token succeeds
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"access_token":"token","token_type":"Bearer","expires_in":3600}"""),
        )

        // Search fails
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("""{"error":"server_error"}"""),
        )

        val exception =
            assertThrows(PluginException::class.java) {
                plugin.search("test", SearchOptions(limit = 10))
            }

        assertTrue(exception.message!!.contains("search failed"))
    }

    @Test
    fun `search should cache client credentials token`() {
        val plugin = createPlugin()

        // Token request
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"access_token":"cached-token","token_type":"Bearer","expires_in":3600}"""),
        )

        // First search
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"albums":{"items":[]},"playlists":{"items":[]}}"""),
        )

        // Second search (should reuse token)
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"albums":{"items":[]},"playlists":{"items":[]}}"""),
        )

        plugin.search("first", SearchOptions(limit = 5))
        plugin.search("second", SearchOptions(limit = 5))

        // Should have 3 requests total: 1 token + 2 searches (token was cached for 2nd search)
        assertEquals(3, mockServer.requestCount)
    }

    @Test
    fun `search should handle empty results`() {
        val plugin = createPlugin()

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"access_token":"token","token_type":"Bearer","expires_in":3600}"""),
        )

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"albums":{"items":[]},"playlists":{"items":[]}}"""),
        )

        val results = plugin.search("nonexistent", SearchOptions(limit = 10))

        assertTrue(results.isEmpty())
    }

    @Test
    fun `search should skip albums with missing required fields`() {
        val plugin = createPlugin()

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"access_token":"token","token_type":"Bearer","expires_in":3600}"""),
        )

        // Album missing "id" field
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                        "albums": {
                            "items": [
                                {"name": "No ID Album", "uri": "spotify:album:x"},
                                {"id": "valid", "name": "Valid Album", "uri": "spotify:album:valid", "artists": [], "images": []}
                            ]
                        },
                        "playlists": {"items": []}
                    }
                    """.trimIndent(),
                ),
        )

        val results = plugin.search("test", SearchOptions(limit = 10))

        assertEquals(1, results.size)
        assertEquals("Valid Album", results[0].title)
    }

    // ── Play tests ──────────────────────────────────────────────────────────

    @Test
    fun `play should succeed with 204 response`() {
        val plugin = createPlugin()

        val content =
            SpotifyMediaContent(
                contentId = "album-id",
                title = "OK Computer",
                spotifyUri = "spotify:album:4aawyAB9vmqN3uQ7FjRGTy",
            )
        whenever(mockMediaStore.getParsed("uuid-play", "spotify", SpotifyMediaContent.Parser))
            .thenReturn(content)
        whenever(mockTokenStore.getAccessToken()).thenReturn("user-access-token")

        mockServer.enqueue(MockResponse().setResponseCode(204))

        plugin.play("uuid-play")

        val request = mockServer.takeRequest()
        assertEquals("PUT", request.method)
        assertTrue(request.path!!.contains("/me/player/play"))
        assertEquals("Bearer user-access-token", request.getHeader("Authorization"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("spotify:album:4aawyAB9vmqN3uQ7FjRGTy"))
    }

    @Test
    fun `play should succeed with 200 response`() {
        val plugin = createPlugin()

        val content =
            SpotifyMediaContent(
                contentId = "playlist-id",
                title = "My Playlist",
                spotifyUri = "spotify:playlist:abc123",
                mediaType = "playlist",
            )
        whenever(mockMediaStore.getParsed("uuid-pl", "spotify", SpotifyMediaContent.Parser))
            .thenReturn(content)
        whenever(mockTokenStore.getAccessToken()).thenReturn("token")

        mockServer.enqueue(MockResponse().setResponseCode(200))

        plugin.play("uuid-pl")

        assertEquals(1, mockServer.requestCount)
    }

    @Test
    fun `play should throw for 404 no active device`() {
        val plugin = createPlugin()

        val content =
            SpotifyMediaContent(
                contentId = "id",
                title = "Test",
                spotifyUri = "spotify:album:id",
            )
        whenever(mockMediaStore.getParsed("uuid-404", "spotify", SpotifyMediaContent.Parser))
            .thenReturn(content)
        whenever(mockTokenStore.getAccessToken()).thenReturn("token")

        mockServer.enqueue(MockResponse().setResponseCode(404))

        val exception =
            assertThrows(PluginException::class.java) {
                plugin.play("uuid-404")
            }

        assertTrue(exception.message!!.contains("No active Spotify device"))
    }

    @Test
    fun `play should throw for 403 premium required`() {
        val plugin = createPlugin()

        val content =
            SpotifyMediaContent(
                contentId = "id",
                title = "Test",
                spotifyUri = "spotify:album:id",
            )
        whenever(mockMediaStore.getParsed("uuid-403", "spotify", SpotifyMediaContent.Parser))
            .thenReturn(content)
        whenever(mockTokenStore.getAccessToken()).thenReturn("token")

        mockServer.enqueue(MockResponse().setResponseCode(403))

        val exception =
            assertThrows(PluginException::class.java) {
                plugin.play("uuid-403")
            }

        assertTrue(exception.message!!.contains("Premium required"))
    }

    @Test
    fun `play should throw for unexpected HTTP error`() {
        val plugin = createPlugin()

        val content =
            SpotifyMediaContent(
                contentId = "id",
                title = "Test",
                spotifyUri = "spotify:album:id",
            )
        whenever(mockMediaStore.getParsed("uuid-500", "spotify", SpotifyMediaContent.Parser))
            .thenReturn(content)
        whenever(mockTokenStore.getAccessToken()).thenReturn("token")

        mockServer.enqueue(MockResponse().setResponseCode(502))

        val exception =
            assertThrows(PluginException::class.java) {
                plugin.play("uuid-500")
            }

        assertTrue(exception.message!!.contains("playback failed"))
    }

    // ── Auth callback tests ─────────────────────────────────────────────────

    @Test
    fun `handleAuthCallback should exchange code for tokens`() {
        val plugin = createPlugin()

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                        "access_token": "new-access-token",
                        "refresh_token": "new-refresh-token",
                        "token_type": "Bearer",
                        "expires_in": 3600
                    }
                    """.trimIndent(),
                ),
        )

        plugin.handleAuthCallback("auth-code-123", "http://localhost:8584")

        val request = mockServer.takeRequest()
        assertEquals("POST", request.method)
        val body = request.body.readUtf8()
        assertTrue(body.contains("grant_type=authorization_code"))
        assertTrue(body.contains("code=auth-code-123"))
        assertTrue(body.contains("client_id=test-client-id"))
        assertTrue(body.contains("client_secret=test-client-secret"))
        assertTrue(body.contains("redirect_uri="))
    }

    @Test
    fun `handleAuthCallback should throw when token exchange fails`() {
        val plugin = createPlugin()

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody("""{"error":"invalid_grant"}"""),
        )

        val exception =
            assertThrows(PluginException::class.java) {
                plugin.handleAuthCallback("bad-code", "http://localhost:8584")
            }

        assertTrue(exception.message!!.contains("token exchange failed"))
    }

    @Test
    fun `handleAuthCallback should throw when response missing access token`() {
        val plugin = createPlugin()

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"token_type":"Bearer","expires_in":3600}"""),
        )

        val exception =
            assertThrows(PluginException::class.java) {
                plugin.handleAuthCallback("code", "http://localhost:8584")
            }

        assertTrue(exception.message!!.contains("access_token"))
    }

    @Test
    fun `handleAuthCallback should throw when response missing refresh token`() {
        val plugin = createPlugin()

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"access_token":"at","token_type":"Bearer","expires_in":3600}"""),
        )

        val exception =
            assertThrows(PluginException::class.java) {
                plugin.handleAuthCallback("code", "http://localhost:8584")
            }

        assertTrue(exception.message!!.contains("refresh_token"))
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun spotifySearchResponse(): String {
        return """
            {
                "albums": {
                    "items": [
                        {
                            "id": "4aawyAB9vmqN3uQ7FjRGTy",
                            "name": "OK Computer",
                            "uri": "spotify:album:4aawyAB9vmqN3uQ7FjRGTy",
                            "artists": [{"name": "Radiohead"}],
                            "images": [{"url": "https://i.scdn.co/image/abc123", "width": 640, "height": 640}]
                        }
                    ]
                },
                "playlists": {
                    "items": [
                        {
                            "id": "playlist1",
                            "name": "Radiohead Essentials",
                            "uri": "spotify:playlist:playlist1",
                            "description": "The best of Radiohead",
                            "images": [{"url": "https://mosaic.scdn.co/img1"}]
                        },
                        {
                            "id": "playlist2",
                            "name": "Radiohead Deep Cuts",
                            "uri": "spotify:playlist:playlist2",
                            "description": "",
                            "images": []
                        }
                    ]
                }
            }
            """.trimIndent()
    }
}
