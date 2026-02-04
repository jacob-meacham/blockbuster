package com.blockbuster.plugin.spotify

/**
 * Persistence layer for Spotify OAuth tokens.
 *
 * Implementations handle storing the refresh token durably and caching/refreshing
 * the short-lived access token transparently.
 */
interface SpotifyTokenStore {
    /**
     * Get a valid access token, refreshing if necessary.
     * Returns null if the user has not authenticated yet.
     */
    fun getAccessToken(): String?

    /**
     * Store tokens received from the Spotify OAuth token endpoint.
     *
     * @param accessToken  Short-lived access token
     * @param refreshToken Long-lived refresh token (nullable on refresh responses)
     * @param expiresInSeconds Token TTL in seconds
     */
    fun storeTokens(
        accessToken: String,
        refreshToken: String?,
        expiresInSeconds: Long,
    )

    /** Whether we have a stored refresh token (i.e. the user has completed OAuth). */
    fun isAuthenticated(): Boolean
}
