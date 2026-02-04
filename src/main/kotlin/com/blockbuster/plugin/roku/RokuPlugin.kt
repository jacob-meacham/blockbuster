package com.blockbuster.plugin.roku

import com.blockbuster.media.MediaStore
import com.blockbuster.media.RokuMediaContent
import com.blockbuster.plugin.ChannelInfoItem
import com.blockbuster.plugin.ChannelInfoProvider
import com.blockbuster.plugin.MediaPlugin
import com.blockbuster.plugin.PluginException
import com.blockbuster.plugin.SearchOptions
import com.blockbuster.plugin.SearchResult
import com.blockbuster.search.BraveStreamingSearchProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory

/**
 * Roku plugin for controlling Roku devices via ECP (External Control Protocol).
 *
 * This plugin handles all Roku device communication while delegating channel-specific
 * logic (deep links, action sequences, search) to RokuChannelPlugin implementations.
 *
 * Optionally integrates Brave Search API for content discovery across streaming services.
 *
 * Architecture:
 * - RokuPlugin: Handles Roku device communication (ECP commands, keypresses)
 * - RokuChannelPlugin: Handles channel-specific logic (Emby, Netflix, etc.)
 * - BraveStreamingSearchProvider (optional): Discovers content across streaming services
 */
class RokuPlugin(
    private val deviceIp: String,
    private val deviceName: String = "Roku Device",
    private val mediaStore: MediaStore,
    private val httpClient: OkHttpClient,
    private val channelPlugins: Map<String, RokuChannelPlugin> = emptyMap(),
    braveSearchApiKey: String? = null,
) : MediaPlugin<RokuMediaContent>, ChannelInfoProvider {
    companion object {
        /** Standard Roku ECP (External Control Protocol) port. */
        const val ECP_PORT = 8060

        /**
         * Delay between individual character keypresses when typing text.
         * Roku devices need time to process each character input.
         */
        const val CHAR_DELAY_MS = 50L

        /**
         * Delay between remote control keypresses.
         * Ensures Roku processes each command before the next is sent.
         */
        const val KEYPRESS_DELAY_MS = 100L
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    private val ecpBaseUrl: HttpUrl = "http://$deviceIp:$ECP_PORT".toHttpUrl()

    // Initialize Brave Search provider if API key is provided
    private val braveSearchProvider: BraveStreamingSearchProvider? =
        if (!braveSearchApiKey.isNullOrBlank()) {
            logger.info("Brave Search enabled for Roku plugin with {} channels", channelPlugins.size)
            BraveStreamingSearchProvider(
                apiKey = braveSearchApiKey,
                httpClient = httpClient,
                channelPlugins = channelPlugins.values,
            )
        } else {
            logger.debug("Brave Search disabled for Roku plugin")
            null
        }

    override fun getPluginName(): String = "roku"

    override fun getDescription(): String = "$deviceName - Controls Roku devices via ECP protocol"

    override fun getContentParser() = RokuMediaContent.Parser

    override fun getChannelInfo(): List<ChannelInfoItem> =
        channelPlugins.values.map { channel ->
            ChannelInfoItem(
                channelId = channel.getChannelId(),
                channelName = channel.getChannelName(),
                searchUrl = channel.getSearchUrl(),
            )
        }

    @Throws(PluginException::class)
    override fun play(contentId: String) {
        try {
            val content =
                mediaStore.getParsed(contentId, getPluginName(), RokuMediaContent.Parser)
                    ?: throw PluginException("Content not found in media store: $contentId")

            logger.info("Playing content: {} on channel: {} ({})", content.title, content.channelName, content.channelId)

            val channelPlugin =
                channelPlugins[content.channelId]
                    ?: throw PluginException("No channel plugin registered for channel ID: ${content.channelId}")

            val command = channelPlugin.buildPlaybackCommand(content)

            runBlocking { executePlaybackCommand(command) }

            logger.info("Successfully initiated playback: {}", content.title)
        } catch (e: PluginException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to execute Roku command '{}': {}", contentId, e.message, e)
            throw PluginException("Failed to execute Roku command: ${e.message}", e)
        }
    }

    private suspend fun executePlaybackCommand(command: RokuPlaybackCommand) {
        when (command) {
            is RokuPlaybackCommand.DeepLink -> {
                val url =
                    ecpBaseUrl.newBuilder()
                        .addPathSegment("launch")
                        .addPathSegment(command.channelId)
                        .encodedQuery(command.params)
                        .build()
                logger.debug("Executing deep link: {}", url)
                sendEcpRequest(url.toString(), "POST")
            }
            is RokuPlaybackCommand.ActionSequence -> {
                logger.debug("Executing action sequence with {} steps", command.actions.size)
                executeActionSequence(command.actions)
            }
        }
    }

    private suspend fun executeActionSequence(actions: List<RokuAction>) {
        actions.forEachIndexed { index, action ->
            logger.debug("Action {}/{}: {}", index + 1, actions.size, action)

            when (action) {
                is RokuAction.Launch -> {
                    val urlBuilder =
                        ecpBaseUrl.newBuilder()
                            .addPathSegment("launch")
                            .addPathSegment(action.channelId)
                    if (action.params.isNotEmpty()) {
                        urlBuilder.encodedQuery(action.params)
                    }
                    sendEcpRequest(urlBuilder.build().toString(), "POST")
                }
                is RokuAction.Press -> {
                    repeat(action.count) {
                        sendKeyPress(action.key)
                    }
                }
                is RokuAction.Type -> {
                    typeText(action.text)
                }
                is RokuAction.Wait -> {
                    delay(action.milliseconds)
                }
            }
        }
    }

    private suspend fun typeText(text: String) {
        text.forEach { char ->
            val litCode =
                when {
                    char.isLetter() -> "Lit_${char.uppercaseChar()}"
                    char.isDigit() -> "Lit_$char"
                    char == ' ' -> "Lit_%20"
                    else -> {
                        logger.warn("Unsupported character for typing: {}", char)
                        return@forEach
                    }
                }

            val url =
                ecpBaseUrl.newBuilder()
                    .addPathSegment("keypress")
                    .addEncodedPathSegment(litCode)
                    .build()
            sendEcpRequest(url.toString(), "POST")
            delay(CHAR_DELAY_MS)
        }
    }

    private suspend fun sendKeyPress(key: RokuKey) {
        val url =
            ecpBaseUrl.newBuilder()
                .addPathSegment("keypress")
                .addPathSegment(key.ecpName)
                .build()
        sendEcpRequest(url.toString(), "POST")
        delay(KEYPRESS_DELAY_MS)
    }

    fun getDeviceInfo(): String? {
        val url =
            ecpBaseUrl.newBuilder()
                .addPathSegment("query")
                .addPathSegment("device-info")
                .build()
        return sendEcpRequest(url.toString(), "GET")
    }

    private fun sendEcpRequest(
        urlString: String,
        method: String,
    ): String? {
        return try {
            val requestBuilder = Request.Builder().url(urlString)

            when (method.uppercase()) {
                "GET" -> requestBuilder.get()
                "POST" -> requestBuilder.post("".toRequestBody())
                else -> throw IllegalArgumentException("Unsupported HTTP method: $method")
            }

            val request = requestBuilder.build()
            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()

                if (response.isSuccessful) {
                    logger.debug("ECP request successful: {}", urlString)
                    responseBody
                } else {
                    logger.error("ECP request failed: {}, response code: {}", urlString, response.code)
                    throw PluginException("ECP request failed with response code: ${response.code}")
                }
            }
        } catch (e: PluginException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to send ECP request to {}: {}", urlString, e.message, e)
            throw PluginException("Failed to send ECP request: ${e.message}", e)
        }
    }

    @Throws(PluginException::class)
    override fun search(
        query: String,
        options: SearchOptions,
    ): List<SearchResult<RokuMediaContent>> {
        try {
            logger.info("Searching for '{}' across {} channel(s)", query, channelPlugins.size)

            val allResults = mutableListOf<SearchResult<RokuMediaContent>>()

            // 1. Brave Search results (if configured)
            braveSearchProvider?.let { braveSearch ->
                try {
                    val braveResults = braveSearch.searchStreaming(query, options.limit)
                    logger.info("Brave Search returned {} results", braveResults.size)
                    allResults.addAll(
                        braveResults.map { content ->
                            SearchResult(
                                title = content.title ?: "Unknown",
                                url = content.metadata?.searchUrl,
                                content = content,
                                source = "brave",
                                description = content.metadata?.description ?: content.metadata?.overview,
                                imageUrl = content.metadata?.imageUrl,
                                dedupKey = "${content.channelId}-${content.contentId}",
                            )
                        },
                    )
                } catch (e: Exception) {
                    logger.warn("Brave Search failed: {}", e.message)
                }
            }

            // 2. Channel plugin search results (e.g., Emby API)
            channelPlugins.values.forEach { channelPlugin ->
                try {
                    val results = channelPlugin.search(query)
                    logger.debug("Channel '{}' returned {} results", channelPlugin.getChannelName(), results.size)
                    allResults.addAll(
                        results.map { content ->
                            SearchResult(
                                title = content.title ?: "Unknown",
                                content = content,
                                source = channelPlugin.getChannelName(),
                                description = content.metadata?.description ?: content.metadata?.overview,
                                imageUrl = content.metadata?.imageUrl,
                                dedupKey = "${content.channelId}-${content.contentId}",
                            )
                        },
                    )
                } catch (e: Exception) {
                    logger.warn("Channel '{}' search failed: {}", channelPlugin.getChannelName(), e.message)
                }
            }

            logger.info("Found {} total search results for query '{}'", allResults.size, query)
            return allResults
        } catch (e: Exception) {
            logger.error("Search failed for query '{}': {}", query, e.message, e)
            throw PluginException("Search failed: ${e.message}", e)
        }
    }

    /**
     * Returns all registered channel plugins.
     */
    fun getAllChannelPlugins(): Collection<RokuChannelPlugin> {
        return channelPlugins.values
    }
}
