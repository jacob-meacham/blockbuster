package com.blockbuster.search

import com.blockbuster.media.MediaJson
import com.blockbuster.media.RokuMediaContent
import com.blockbuster.media.RokuMediaMetadata
import com.blockbuster.plugin.roku.RokuChannelPlugin
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory

/**
 * Brave Search API integration for discovering streaming media content across services.
 *
 * This provider automatically builds site: filters from Roku channel plugins,
 * searching only public streaming services (Netflix, Disney+, etc.) and excluding
 * private servers (Emby, Plex).
 *
 * Configuration:
 * - API Key: Get from https://brave.com/search/api/
 * - Pricing: $3-5 per 1,000 searches (100 free queries/day)
 * - Rate Limit: Depends on plan
 *
 * @see RokuChannelPlugin.getPublicSearchDomain
 */
class BraveStreamingSearchProvider(
    private val apiKey: String,
    private val httpClient: OkHttpClient,
    private val channelPlugins: Collection<RokuChannelPlugin>,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Search for streaming content across configured public channels.
     */
    fun searchStreaming(
        query: String,
        maxResults: Int = 10,
    ): List<RokuMediaContent> {
        if (apiKey.isBlank()) {
            logger.debug("Brave Search API key not configured, skipping")
            return emptyList()
        }

        val siteQuery = buildSiteQuery(query)
        val response = executeSearch(siteQuery, maxResults)
        return extractContent(response)
    }

    /**
     * Builds site: filters dynamically from channel plugins' public search domains.
     *
     * Automatically filters out private/local servers (Emby, Plex) by checking
     * getPublicSearchDomain(). Only includes channels that return a non-empty domain.
     *
     * Example output: "the matrix (site:netflix.com OR site:disneyplus.com)"
     */
    private fun buildSiteQuery(query: String): String {
        val sites =
            channelPlugins.mapNotNull { channel ->
                val domain = channel.getPublicSearchDomain()
                if (domain.isBlank()) {
                    logger.debug("Skipping '{}' - no public search domain", channel.getChannelName())
                    null
                } else {
                    "site:$domain"
                }
            }

        return if (sites.isNotEmpty()) {
            "watch $query (${sites.joinToString(" OR ")})"
        } else {
            logger.warn("No public search domains found, searching without site: filters")
            query
        }
    }

    private fun executeSearch(
        query: String,
        count: Int,
    ): BraveSearchResponse {
        val url =
            "https://api.search.brave.com/res/v1/web/search" +
                "?q=${java.net.URLEncoder.encode(query, "UTF-8")}" +
                "&count=$count"

        val request =
            Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("X-Subscription-Token", apiKey)
                .get()
                .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                logger.error("Brave Search API error ({}): {}", response.code, body)
                throw BraveSearchException(
                    "API request failed with status ${response.code}: ${response.message}",
                    statusCode = response.code,
                )
            }

            if (body.isEmpty()) {
                throw BraveSearchException("Empty response from Brave Search API")
            }

            return MediaJson.mapper.readValue(body, BraveSearchResponse::class.java)
        }
    }

    private fun extractContent(response: BraveSearchResponse): List<RokuMediaContent> {
        val results = mutableListOf<RokuMediaContent>()

        response.web?.results?.forEach { result ->
            val url = result.url ?: return@forEach

            // Amazon results must have "| Prime Video" in the title to be streaming content
            if ("amazon.com" in url && result.title?.contains("| Prime Video") != true) {
                logger.debug("Skipping non-Prime-Video Amazon result: {}", url)
                return@forEach
            }

            // Try each plugin until one successfully extracts content
            val content = channelPlugins.firstNotNullOfOrNull { plugin -> plugin.extractFromUrl(url) }

            if (content != null) {
                // Enrich with title, description, image, and original URL from search result
                val enriched =
                    content.copy(
                        title = result.title ?: content.title,
                        metadata =
                            (content.metadata ?: RokuMediaMetadata()).copy(
                                description = result.description ?: content.metadata?.description,
                                originalUrl = url,
                                imageUrl = result.thumbnail?.src ?: content.metadata?.imageUrl,
                            ),
                    )
                results.add(enriched)
            } else {
                logger.debug("No channel matched URL: {}", url)
            }
        }

        return results.distinctBy { it.channelId to it.contentId }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class BraveSearchResponse(
        val web: WebResults?,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class WebResults(
        val results: List<SearchResult>?,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class SearchResult(
        val title: String?,
        val url: String?,
        val description: String?,
        val thumbnail: Thumbnail? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Thumbnail(
        val src: String?,
    )
}

/**
 * Exception thrown when Brave Search API operations fail.
 */
class BraveSearchException(
    message: String,
    val statusCode: Int? = null,
    cause: Throwable? = null,
) : Exception(message, cause)
