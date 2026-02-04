package com.blockbuster.plugin.spotify

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import java.time.Instant
import javax.sql.DataSource

/**
 * Stores Spotify OAuth tokens in the auth_tokens SQLite table.
 *
 * The refresh token is persisted to the database.
 * The access token is cached in memory and automatically refreshed when expired.
 *
 * @param dataSource Database connection source
 * @param httpClient HTTP client for token refresh requests
 * @param clientId Spotify application client ID
 * @param clientSecret Spotify application client secret
 */
class SqliteSpotifyTokenStore(
    private val dataSource: DataSource,
    private val httpClient: OkHttpClient,
    private val clientId: String,
    private val clientSecret: String,
    private val tokenUrl: String = SPOTIFY_TOKEN_URL,
) : SpotifyTokenStore {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val mapper = jacksonObjectMapper()

    @Volatile
    private var cachedAccessToken: String? = null

    @Volatile
    private var tokenExpiresAt: Instant = Instant.MIN

    companion object {
        private const val PLUGIN_NAME = "spotify"
        private const val TOKEN_TYPE_REFRESH = "refresh_token"
        private const val SPOTIFY_TOKEN_URL = "https://accounts.spotify.com/api/token"
    }

    override fun getAccessToken(): String? {
        // If cached token is still valid (with 60s buffer), return it
        val cached = cachedAccessToken
        if (cached != null && Instant.now().isBefore(tokenExpiresAt.minusSeconds(60))) {
            return cached
        }

        // Try to refresh using stored refresh token
        val refreshToken = loadRefreshToken() ?: return null
        return refreshAccessToken(refreshToken)
    }

    override fun storeTokens(
        accessToken: String,
        refreshToken: String?,
        expiresInSeconds: Long,
    ) {
        // Cache access token in memory
        cachedAccessToken = accessToken
        tokenExpiresAt = Instant.now().plusSeconds(expiresInSeconds)

        // Persist refresh token to database (if provided — refresh responses may omit it)
        if (refreshToken != null) {
            upsertToken(TOKEN_TYPE_REFRESH, refreshToken, null)
        }
    }

    override fun isAuthenticated(): Boolean {
        return loadRefreshToken() != null
    }

    private fun loadRefreshToken(): String? {
        val sql = "SELECT token_value FROM auth_tokens WHERE plugin = ? AND token_type = ?"
        return try {
            dataSource.connection.use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, PLUGIN_NAME)
                    ps.setString(2, TOKEN_TYPE_REFRESH)
                    val rs = ps.executeQuery()
                    if (rs.next()) rs.getString("token_value") else null
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to load refresh token: {}", e.message, e)
            null
        }
    }

    private fun upsertToken(
        tokenType: String,
        tokenValue: String,
        expiresAt: Instant?,
    ) {
        val sql =
            """
            INSERT INTO auth_tokens (plugin, token_type, token_value, expires_at, updated_at)
            VALUES (?, ?, ?, ?, datetime('now'))
            ON CONFLICT (plugin, token_type)
            DO UPDATE SET token_value = excluded.token_value,
                          expires_at = excluded.expires_at,
                          updated_at = datetime('now')
            """.trimIndent()

        try {
            dataSource.connection.use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, PLUGIN_NAME)
                    ps.setString(2, tokenType)
                    ps.setString(3, tokenValue)
                    if (expiresAt != null) {
                        ps.setString(4, expiresAt.toString())
                    } else {
                        ps.setNull(4, java.sql.Types.TIMESTAMP)
                    }
                    ps.executeUpdate()
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to upsert token: {}", e.message, e)
            throw e
        }
    }

    private fun refreshAccessToken(refreshToken: String): String? {
        val body =
            FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
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
                    logger.error("Token refresh failed: HTTP {}", response.code)
                    return null
                }

                val json = mapper.readTree(response.body?.string())
                val accessToken = json["access_token"]?.asText() ?: return null
                val expiresIn = json["expires_in"]?.asLong() ?: 3600
                val newRefreshToken = json["refresh_token"]?.asText()

                storeTokens(accessToken, newRefreshToken, expiresIn)
                accessToken
            }
        } catch (e: Exception) {
            logger.error("Failed to refresh access token: {}", e.message, e)
            null
        }
    }
}
