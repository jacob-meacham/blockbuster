package com.blockbuster.plugin.roku

import com.blockbuster.media.RokuMediaContent
import com.blockbuster.media.RokuMediaMetadata
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URLEncoder

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
    private val objectMapper: ObjectMapper,
) : RokuChannelPlugin {
    companion object {
        private val logger = LoggerFactory.getLogger(EmbyRokuChannelPlugin::class.java)
        private const val SEARCH_LIMIT = 50
    }

    override fun getChannelId(): String = "44191" // Emby for Roku channel ID

    override fun getChannelName(): String = "Emby"

    override fun getPublicSearchDomain(): String = "" // Private server, not for public web search

    override fun buildPlaybackCommand(content: RokuMediaContent): RokuPlaybackCommand {
        val itemId = content.contentId
        val resumePosition = content.metadata?.resumePositionTicks

        val params =
            mutableListOf(
                "Command=PlayNow",
                "ItemIds=$itemId",
            )

        if (resumePosition != null && resumePosition > 0) {
            params.add("StartPositionTicks=$resumePosition")
        }

        return RokuPlaybackCommand.DeepLink(
            channelId = getChannelId(),
            params = params.joinToString("&"),
        )
    }

    override fun search(query: String): List<RokuMediaContent> {
        logger.debug("Searching Emby library for: {}", query)

        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val searchUrl =
            "$embyServerUrl/Users/$embyUserId/Items?" +
                "searchTerm=$encodedQuery" +
                "&recursive=true" +
                "&limit=$SEARCH_LIMIT" +
                "&fields=Overview,Path,ImageTags,Genres,CommunityRating,OfficialRating,UserData" +
                "&includeItemTypes=Movie,Episode"

        val request =
            Request.Builder()
                .url(searchUrl)
                .header("X-Emby-Token", embyApiKey)
                .get()
                .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Emby search failed: ${response.code} - ${response.message}")
            }

            val responseBody =
                response.body?.string()
                    ?: throw IOException("Empty response body from Emby search")
            val searchResponse = objectMapper.readValue(responseBody, EmbySearchResponse::class.java)

            logger.info("Found {} Emby results for query: {}", searchResponse.items.size, query)

            return searchResponse.items.map { item ->
                RokuMediaContent(
                    channelName = getChannelName(),
                    channelId = getChannelId(),
                    contentId = item.id,
                    title = buildTitle(item),
                    mediaType = item.type,
                    metadata =
                        RokuMediaMetadata(
                            serverId = item.serverId,
                            itemType = item.type,
                            seriesName = item.seriesName,
                            seasonNumber = item.parentIndexNumber,
                            episodeNumber = item.indexNumber,
                            year = item.productionYear,
                            overview = item.overview,
                            imageUrl = buildImageUrl(item.id, item.imageTags?.primary),
                            resumePositionTicks = item.userData?.playbackPositionTicks,
                            runtimeTicks = item.runTimeTicks,
                            playedPercentage = item.userData?.playedPercentage,
                            isFavorite = item.userData?.isFavorite,
                            communityRating = item.communityRating,
                            officialRating = item.officialRating,
                            genres = item.genres,
                        ),
                )
            }
        }
    }

    private fun buildTitle(item: EmbyItem): String =
        when (item.type) {
            "Episode" -> "${item.seriesName} - S${item.parentIndexNumber}E${item.indexNumber} - ${item.name}"
            "Movie" -> "${item.name}${item.productionYear?.let { " ($it)" } ?: ""}"
            else -> item.name
        }

    private fun buildImageUrl(
        itemId: String,
        imageTag: String?,
    ): String {
        return imageTag?.let { "$embyServerUrl/Items/$itemId/Images/Primary?tag=$it" } ?: ""
    }

    // Emby API response DTOs
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class EmbySearchResponse(
        @JsonProperty("Items") val items: List<EmbyItem>,
        @JsonProperty("TotalRecordCount") val totalRecordCount: Int,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
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
        @JsonProperty("Genres") val genres: List<String>? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ImageTags(
        @JsonProperty("Primary") val primary: String? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class UserData(
        @JsonProperty("PlaybackPositionTicks") val playbackPositionTicks: Long? = null,
        @JsonProperty("PlayedPercentage") val playedPercentage: Double? = null,
        @JsonProperty("IsFavorite") val isFavorite: Boolean = false,
        @JsonProperty("PlayCount") val playCount: Int? = null,
    )
}
