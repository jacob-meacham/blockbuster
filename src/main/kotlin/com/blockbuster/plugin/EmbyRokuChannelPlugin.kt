package com.blockbuster.plugin

import com.blockbuster.media.RokuMediaContent
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import java.io.IOException

/**
 * Roku channel plugin for Emby
 *
 * Uses VALIDATED deep linking format discovered through Emby web app:
 * - Command=PlayNow (triggers immediate playback)
 * - ItemIds={id} (plural form required)
 * - StartPositionTicks={ticks} (optional resume position)
 *
 * This provides 2-3 second playback vs 12-13 seconds with action sequences.
 */
class EmbyRokuChannelPlugin(
    private val embyServerUrl: String,
    private val embyApiKey: String,
    private val embyUserId: String,
    private val httpClient: OkHttpClient,
    private val objectMapper: ObjectMapper
) : RokuChannelPlugin {

    companion object {
        private val logger = LoggerFactory.getLogger(EmbyRokuChannelPlugin::class.java)
        private const val SEARCH_LIMIT = 50
    }

    override fun getChannelId(): String = "44191"  // Emby for Roku channel ID

    override fun getChannelName(): String = "Emby"

    override fun buildPlaybackCommand(content: RokuMediaContent, rokuDeviceIp: String): RokuPlaybackCommand {
        // Parse content metadata to get Emby-specific fields
        val itemId = content.contentId
        val resumePosition = content.metadata?.get("resumePositionTicks") as? Long

        // Build validated deep link URL
        val params = mutableListOf(
            "Command=PlayNow",
            "ItemIds=$itemId"
        )

        // Include resume position if available
        if (resumePosition != null && resumePosition > 0) {
            params.add("StartPositionTicks=$resumePosition")
        }

        val url = "http://$rokuDeviceIp:8060/launch/${getChannelId()}?${params.joinToString("&")}"

        logger.debug("Built Emby deep link: {}", url)

        return RokuPlaybackCommand.DeepLink(url)
    }

    override fun search(query: String): List<RokuMediaContent> {
        logger.debug("Searching Emby library for: {}", query)

        val searchUrl = "$embyServerUrl/Users/$embyUserId/Items?" +
                "searchTerm=$query" +
                "&recursive=true" +
                "&limit=$SEARCH_LIMIT" +
                "&fields=Overview,Path,ImageTags,Genres,CommunityRating,OfficialRating,UserData" +
                "&includeItemTypes=Movie,Episode"

        val request = Request.Builder()
            .url(searchUrl)
            .header("X-Emby-Token", embyApiKey)
            .get()
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    logger.warn("Emby search failed: {} - {}", response.code, response.message)
                    return emptyList()
                }

                val responseBody = response.body?.string() ?: return emptyList()
                val searchResponse = objectMapper.readValue(responseBody, EmbySearchResponse::class.java)

                logger.info("Found {} Emby results for query: {}", searchResponse.items.size, query)

                return searchResponse.items.map { item ->
                    RokuMediaContent(
                        channelName = getChannelName(),
                        channelId = getChannelId(),
                        contentId = item.id,
                        title = buildTitle(item),
                        mediaType = item.type,
                        metadata = mapOf(
                            "serverId" to item.serverId,
                            "itemType" to item.type,
                            "seriesName" to (item.seriesName ?: ""),
                            "seasonNumber" to (item.parentIndexNumber ?: 0),
                            "episodeNumber" to (item.indexNumber ?: 0),
                            "year" to (item.productionYear ?: 0),
                            "overview" to (item.overview ?: ""),
                            "imageUrl" to buildImageUrl(item.id, item.imageTags?.primary),
                            "resumePositionTicks" to (item.userData?.playbackPositionTicks ?: 0L),
                            "runtimeTicks" to (item.runTimeTicks ?: 0L),
                            "playedPercentage" to (item.userData?.playedPercentage ?: 0.0),
                            "isFavorite" to (item.userData?.isFavorite ?: false),
                            "communityRating" to (item.communityRating ?: 0.0),
                            "officialRating" to (item.officialRating ?: ""),
                            "genres" to (item.genres ?: emptyList<String>())
                        )
                    )
                }
            }
        } catch (e: IOException) {
            logger.error("Network error during Emby search", e)
            return emptyList()
        }
    }

    private fun buildTitle(item: EmbyItem): String = when (item.type) {
        "Episode" -> "${item.seriesName} - S${item.parentIndexNumber}E${item.indexNumber} - ${item.name}"
        "Movie" -> "${item.name}${item.productionYear?.let { " ($it)" } ?: ""}"
        else -> item.name
    }

    private fun buildImageUrl(itemId: String, imageTag: String?): String {
        return imageTag?.let { "$embyServerUrl/Items/$itemId/Images/Primary?tag=$it" } ?: ""
    }

    // Emby API response DTOs
    data class EmbySearchResponse(
        @JsonProperty("Items") val items: List<EmbyItem>,
        @JsonProperty("TotalRecordCount") val totalRecordCount: Int
    )

    data class EmbyItem(
        @JsonProperty("Id") val id: String,
        @JsonProperty("ServerId") val serverId: String,
        @JsonProperty("Name") val name: String,
        @JsonProperty("Type") val type: String,
        @JsonProperty("SeriesName") val seriesName: String? = null,
        @JsonProperty("ParentIndexNumber") val parentIndexNumber: Int? = null,
        @JsonProperty("IndexNumber") val indexNumber: Int? = null,
        @JsonProperty("ProductionYear") val productionYear: Int? = null,
        @JsonProperty("Overview") val overview: String? = null,
        @JsonProperty("Path") val path: String? = null,
        @JsonProperty("ImageTags") val imageTags: ImageTags? = null,
        @JsonProperty("RunTimeTicks") val runTimeTicks: Long? = null,
        @JsonProperty("UserData") val userData: UserData? = null,
        @JsonProperty("CommunityRating") val communityRating: Double? = null,
        @JsonProperty("OfficialRating") val officialRating: String? = null,
        @JsonProperty("Genres") val genres: List<String>? = null
    )

    data class ImageTags(
        @JsonProperty("Primary") val primary: String? = null
    )

    data class UserData(
        @JsonProperty("PlaybackPositionTicks") val playbackPositionTicks: Long? = null,
        @JsonProperty("PlayedPercentage") val playedPercentage: Double? = null,
        @JsonProperty("IsFavorite") val isFavorite: Boolean = false
    )
}
