package com.blockbuster.plugin.spotify

import com.blockbuster.media.MediaContentParser
import com.blockbuster.media.MediaStore
import com.blockbuster.media.SpotifyMediaContent
import com.blockbuster.plugin.AuthenticablePlugin
import com.blockbuster.plugin.MediaPlugin
import com.blockbuster.plugin.PluginException
import com.blockbuster.plugin.SearchOptions
import com.blockbuster.plugin.SearchResult
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import java.net.URLEncoder
import java.time.Instant

/**
 * Spotify media plugin.
 *
 * Search uses the client-credentials flow (no user login needed).
 * Playback uses Spotify Connect via the user's OAuth access token.
 *
 * @param clientId Spotify application client ID
 * @param clientSecret Spotify application client secret
 * @param mediaStore Shared media store for library persistence
 * @param httpClient OkHttp client for API calls
 * @param tokenStore Token store for user-level OAuth tokens
 */
class SpotifyPlugin(
    private val clientId: String,
    private val clientSecret: String,
    private val mediaStore: MediaStore,
    private val httpClient: OkHttpClient,
    private val tokenStore: SpotifyTokenStore,
    private val apiBaseUrl: String = SPOTIFY_API_BASE,
    private val tokenUrl: String = SPOTIFY_TOKEN_URL,
) : MediaPlugin<SpotifyMediaContent>, AuthenticablePlugin {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val mapper = jacksonObjectMapper()

    @Volatile
    private var clientAccessToken: String? = null

    @Volatile
    private var clientTokenExpiresAt: Instant = Instant.MIN

    companion object {
        private const val SPOTIFY_API_BASE = "https://api.spotify.com/v1"
        private const val SPOTIFY_TOKEN_URL = "https://accounts.spotify.com/api/token"
        private const val SPOTIFY_AUTHORIZE_URL = "https://accounts.spotify.com/authorize"
        private const val SCOPES = "user-modify-playback-state user-read-playback-state user-read-currently-playing"
    }

    // ── MediaPlugin ──────────────────────────────────────────────────────────

    override fun getPluginName(): String = "spotify"

    override fun getDescription(): String = "Spotify - Search and play music via Spotify Connect"

    override fun getContentParser(): MediaContentParser<SpotifyMediaContent> = SpotifyMediaContent.Parser

    override fun search(
        query: String,
        options: SearchOptions,
    ): List<SearchResult<SpotifyMediaContent>> {
        val token =
            getClientCredentialsToken()
                ?: throw PluginException("Failed to obtain Spotify client credentials token")

        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "$apiBaseUrl/search?q=$encodedQuery&type=album,playlist&limit=${options.limit}"

        val request =
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw PluginException("Spotify search failed: HTTP ${response.code}")
                }

                val json = mapper.readTree(response.body?.string())
                val results = mutableListOf<SearchResult<SpotifyMediaContent>>()
                parseAlbums(json, results)
                parsePlaylists(json, results)
                results
            }
        } catch (e: PluginException) {
            throw e
        } catch (e: Exception) {
            throw PluginException("Spotify search failed: ${e.message}", e)
        }
    }

    override fun play(contentId: String) {
        val content =
            mediaStore.getParsed(contentId, "spotify", SpotifyMediaContent.Parser)
                ?: throw PluginException("Content not found in media store for uuid=$contentId")

        val accessToken =
            tokenStore.getAccessToken()
                ?: throw PluginException("Spotify not authenticated. Complete OAuth flow at /auth/spotify")

        val body = mapper.writeValueAsString(mapOf("context_uri" to content.spotifyUri))

        val request =
            Request.Builder()
                .url("$apiBaseUrl/me/player/play")
                .header("Authorization", "Bearer $accessToken")
                .put(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                when (response.code) {
                    204, 200 -> logger.info("Spotify playback started for: {}", content.title)
                    404 -> throw PluginException("No active Spotify device found. Open Spotify on a device first.")
                    403 -> throw PluginException("Spotify Premium required for playback control")
                    else -> throw PluginException("Spotify playback failed: HTTP ${response.code}")
                }
            }
        } catch (e: PluginException) {
            throw e
        } catch (e: Exception) {
            throw PluginException("Spotify playback failed: ${e.message}", e)
        }
    }

    // ── AuthenticablePlugin ──────────────────────────────────────────────────

    override fun buildAuthorizationUrl(callbackBaseUrl: String): String {
        val redirectUrl = "$callbackBaseUrl/auth/spotify/callback"
        val encodedRedirect = URLEncoder.encode(redirectUrl, "UTF-8")
        val encodedScopes = URLEncoder.encode(SCOPES, "UTF-8")
        return "$SPOTIFY_AUTHORIZE_URL?response_type=code&client_id=$clientId" +
            "&scope=$encodedScopes&redirect_uri=$encodedRedirect"
    }

    override fun handleAuthCallback(
        code: String,
        callbackBaseUrl: String,
    ) {
        val redirectUrl = "$callbackBaseUrl/auth/spotify/callback"

        val body =
            FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", redirectUrl)
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .build()

        val request =
            Request.Builder()
                .url(tokenUrl)
                .post(body)
                .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()
                    throw PluginException("Spotify token exchange failed: HTTP ${response.code} - $errorBody")
                }

                val json = mapper.readTree(response.body?.string())
                val accessToken =
                    json["access_token"]?.asText()
                        ?: throw PluginException("No access_token in Spotify response")
                val refreshToken =
                    json["refresh_token"]?.asText()
                        ?: throw PluginException("No refresh_token in Spotify response")
                val expiresIn = json["expires_in"]?.asLong() ?: 3600

                tokenStore.storeTokens(accessToken, refreshToken, expiresIn)
                logger.info("Spotify OAuth completed successfully")
            }
        } catch (e: PluginException) {
            throw e
        } catch (e: Exception) {
            throw PluginException("Spotify OAuth callback failed: ${e.message}", e)
        }
    }

    override fun isAuthenticated(): Boolean = tokenStore.isAuthenticated()

    // ── Internal ─────────────────────────────────────────────────────────────

    private fun parseAlbums(
        json: JsonNode,
        results: MutableList<SearchResult<SpotifyMediaContent>>,
    ) {
        json["albums"]?.get("items")?.forEach { album ->
            val id = album["id"]?.asText() ?: return@forEach
            val name = album["name"]?.asText() ?: return@forEach
            val uri = album["uri"]?.asText() ?: return@forEach
            val artistName = album["artists"]?.firstOrNull()?.get("name")?.asText()
            val imageUrl = album["images"]?.firstOrNull()?.get("url")?.asText()

            results.add(
                SearchResult(
                    title = name,
                    content =
                        SpotifyMediaContent(
                            contentId = id,
                            title = name,
                            spotifyUri = uri,
                            mediaType = "album",
                            artist = artistName,
                            imageUrl = imageUrl,
                        ),
                    source = "Spotify",
                    description = artistName?.let { "Album by $it" },
                    imageUrl = imageUrl,
                    dedupKey = id,
                ),
            )
        }
    }

    private fun parsePlaylists(
        json: JsonNode,
        results: MutableList<SearchResult<SpotifyMediaContent>>,
    ) {
        json["playlists"]?.get("items")?.forEach { playlist ->
            val id = playlist["id"]?.asText() ?: return@forEach
            val name = playlist["name"]?.asText() ?: return@forEach
            val uri = playlist["uri"]?.asText() ?: return@forEach
            val desc = playlist["description"]?.asText()?.takeIf { it.isNotBlank() }
            val imageUrl = playlist["images"]?.firstOrNull()?.get("url")?.asText()

            results.add(
                SearchResult(
                    title = name,
                    content =
                        SpotifyMediaContent(
                            contentId = id,
                            title = name,
                            spotifyUri = uri,
                            mediaType = "playlist",
                            description = desc,
                            imageUrl = imageUrl,
                        ),
                    source = "Spotify",
                    description = desc ?: "Playlist",
                    imageUrl = imageUrl,
                    dedupKey = id,
                ),
            )
        }
    }

    /**
     * Get a client-credentials token for search (no user login needed).
     * Caches the token in memory and refreshes when expired.
     */
    private fun getClientCredentialsToken(): String? {
        val cached = clientAccessToken
        if (cached != null && Instant.now().isBefore(clientTokenExpiresAt.minusSeconds(60))) {
            return cached
        }

        val body =
            FormBody.Builder()
                .add("grant_type", "client_credentials")
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .build()

        val request =
            Request.Builder()
                .url(tokenUrl)
                .post(body)
                .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    logger.error("Client credentials token request failed: HTTP {}", response.code)
                    return null
                }

                val json = mapper.readTree(response.body?.string())
                val token = json["access_token"]?.asText() ?: return null
                val expiresIn = json["expires_in"]?.asLong() ?: 3600

                clientAccessToken = token
                clientTokenExpiresAt = Instant.now().plusSeconds(expiresIn)
                token
            }
        } catch (e: Exception) {
            logger.error("Failed to get client credentials token: {}", e.message, e)
            null
        }
    }
}
