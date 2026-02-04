package com.blockbuster.resource

import com.blockbuster.media.MediaContentParser
import com.blockbuster.media.RokuMediaContent
import com.blockbuster.plugin.AuthenticablePlugin
import com.blockbuster.plugin.MediaPlugin
import com.blockbuster.plugin.PluginRegistry
import com.blockbuster.plugin.SearchOptions
import com.blockbuster.plugin.SearchResult
import jakarta.ws.rs.core.UriInfo
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.net.URI

class AuthResourceTest {
    private lateinit var mockUriInfo: UriInfo

    @BeforeEach
    fun setUp() {
        mockUriInfo = mock()
        whenever(mockUriInfo.baseUri).thenReturn(URI.create("http://localhost:8584/"))
    }

    /**
     * A fake plugin that implements both MediaPlugin and AuthenticablePlugin.
     */
    private class FakeAuthPlugin(
        private val authenticated: Boolean = false,
        private val authUrl: String = "https://example.com/auth",
    ) : MediaPlugin<RokuMediaContent>, AuthenticablePlugin {
        var lastCode: String? = null

        override fun getPluginName() = "fake-auth"

        override fun getDescription() = "Fake auth plugin"

        override fun play(contentId: String) = Unit

        override fun search(
            query: String,
            options: SearchOptions,
        ) = emptyList<SearchResult<RokuMediaContent>>()

        override fun getContentParser(): MediaContentParser<RokuMediaContent> = RokuMediaContent.Parser

        override fun buildAuthorizationUrl(callbackBaseUrl: String) = authUrl

        override fun handleAuthCallback(
            code: String,
            callbackBaseUrl: String,
        ) {
            lastCode = code
        }

        override fun isAuthenticated() = authenticated
    }

    /**
     * A fake plugin that does NOT implement AuthenticablePlugin.
     */
    private class FakeNonAuthPlugin : MediaPlugin<RokuMediaContent> {
        override fun getPluginName() = "non-auth"

        override fun getDescription() = "Non-auth plugin"

        override fun play(contentId: String) = Unit

        override fun search(
            query: String,
            options: SearchOptions,
        ) = emptyList<SearchResult<RokuMediaContent>>()

        override fun getContentParser(): MediaContentParser<RokuMediaContent> = RokuMediaContent.Parser
    }

    @Test
    fun `initiateAuth should redirect for authenticable plugin`() {
        val plugin = FakeAuthPlugin(authUrl = "https://accounts.spotify.com/authorize?test=1")
        val registry = PluginRegistry(mapOf("spotify" to plugin))
        val resource = AuthResource(registry)

        val response = resource.initiateAuth("spotify", mockUriInfo)

        assertEquals(303, response.status)
        assertEquals(
            URI.create("https://accounts.spotify.com/authorize?test=1"),
            response.location,
        )
    }

    @Test
    fun `initiateAuth should return 404 for unknown plugin`() {
        val registry = PluginRegistry(emptyMap())
        val resource = AuthResource(registry)

        val response = resource.initiateAuth("unknown", mockUriInfo)

        assertEquals(404, response.status)
    }

    @Test
    fun `initiateAuth should return 404 for non-authenticable plugin`() {
        val plugin = FakeNonAuthPlugin()
        val registry = PluginRegistry(mapOf("roku" to plugin))
        val resource = AuthResource(registry)

        val response = resource.initiateAuth("roku", mockUriInfo)

        assertEquals(404, response.status)
    }

    @Test
    fun `callback should process code for authenticable plugin`() {
        val plugin = FakeAuthPlugin()
        val registry = PluginRegistry(mapOf("spotify" to plugin))
        val resource = AuthResource(registry)

        val response = resource.authCallback("spotify", "test-code-123", null, mockUriInfo)

        assertEquals(200, response.status)
        assertEquals("test-code-123", plugin.lastCode)
    }

    @Test
    fun `callback should return error when OAuth error parameter present`() {
        val plugin = FakeAuthPlugin()
        val registry = PluginRegistry(mapOf("spotify" to plugin))
        val resource = AuthResource(registry)

        val response = resource.authCallback("spotify", null, "access_denied", mockUriInfo)

        assertEquals(400, response.status)
    }

    @Test
    fun `callback should return error when code is missing`() {
        val plugin = FakeAuthPlugin()
        val registry = PluginRegistry(mapOf("spotify" to plugin))
        val resource = AuthResource(registry)

        val response = resource.authCallback("spotify", null, null, mockUriInfo)

        assertEquals(400, response.status)
    }

    @Test
    fun `callback should return 404 for unknown plugin`() {
        val registry = PluginRegistry(emptyMap())
        val resource = AuthResource(registry)

        val response = resource.authCallback("unknown", "code", null, mockUriInfo)

        assertEquals(404, response.status)
    }

    @Test
    fun `status should return authenticated true for authenticated plugin`() {
        val plugin = FakeAuthPlugin(authenticated = true)
        val registry = PluginRegistry(mapOf("spotify" to plugin))
        val resource = AuthResource(registry)

        val response = resource.authStatus("spotify")

        assertEquals(200, response.status)
        val body = response.entity as AuthStatusResponse
        assertTrue(body.authenticated)
        assertTrue(body.available)
        assertEquals("spotify", body.plugin)
    }

    @Test
    fun `status should return authenticated false for unauthenticated plugin`() {
        val plugin = FakeAuthPlugin(authenticated = false)
        val registry = PluginRegistry(mapOf("spotify" to plugin))
        val resource = AuthResource(registry)

        val response = resource.authStatus("spotify")

        assertEquals(200, response.status)
        val body = response.entity as AuthStatusResponse
        assertFalse(body.authenticated)
        assertTrue(body.available)
    }

    @Test
    fun `status should return available false for non-authenticable plugin`() {
        val plugin = FakeNonAuthPlugin()
        val registry = PluginRegistry(mapOf("roku" to plugin))
        val resource = AuthResource(registry)

        val response = resource.authStatus("roku")

        assertEquals(200, response.status)
        val body = response.entity as AuthStatusResponse
        assertFalse(body.authenticated)
        assertFalse(body.available)
    }

    @Test
    fun `status should return 404 for unknown plugin`() {
        val registry = PluginRegistry(emptyMap())
        val resource = AuthResource(registry)

        val response = resource.authStatus("unknown")

        assertEquals(404, response.status)
    }
}
