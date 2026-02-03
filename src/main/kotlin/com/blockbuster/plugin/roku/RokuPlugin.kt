package com.blockbuster.plugin.roku

import com.blockbuster.media.MediaStore
import com.blockbuster.media.RokuMediaContent
import com.blockbuster.plugin.MediaPlugin
import com.blockbuster.plugin.PluginException
import com.blockbuster.plugin.SearchOptions
import com.blockbuster.plugin.SearchResult
import com.blockbuster.search.BraveStreamingSearchProvider
import org.slf4j.LoggerFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

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
    braveSearchApiKey: String? = null
) : MediaPlugin<RokuMediaContent> {

    companion object {
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

    // Initialize Brave Search provider if API key is provided
    private val braveSearchProvider: BraveStreamingSearchProvider? = if (!braveSearchApiKey.isNullOrBlank()) {
        logger.info("Brave Search enabled for Roku plugin with ${channelPlugins.size} channels")
        BraveStreamingSearchProvider(
            apiKey = braveSearchApiKey,
            httpClient = httpClient,
            channelPlugins = channelPlugins.values
        )
    } else {
        logger.debug("Brave Search disabled for Roku plugin")
        null
    }

    override fun getPluginName(): String = "roku"

    override fun getDescription(): String = "Controls Roku devices via ECP protocol"

    override fun getContentParser() = RokuMediaContent.Parser

    @Throws(PluginException::class)
    override fun play(contentId: String) {
        try {
            val content = mediaStore.getParsed(contentId, getPluginName(), RokuMediaContent.Parser)
                ?: throw PluginException("Content not found in media store: $contentId")

            logger.info("Playing content: {} on channel: {} ({})", content.title, content.channelName, content.channelId)

            // Get the channel plugin for this content
            val channelPlugin = channelPlugins[content.channelId]
                ?: throw PluginException("No channel plugin registered for channel ID: ${content.channelId}")

            // Build playback command (deep link or action sequence)
            val command = channelPlugin.buildPlaybackCommand(content, deviceIp)

            // Execute the command
            executePlaybackCommand(command)

            logger.info("✅ Successfully initiated playback: {}", content.title)

        } catch (e: Exception) {
            logger.error("Failed to execute Roku command '$contentId': ${e.message}", e)
            throw PluginException("Failed to execute Roku command: ${e.message}", e)
        }
    }

    /**
     * Executes a playback command (either deep link or action sequence)
     */
    private fun executePlaybackCommand(command: RokuPlaybackCommand) {
        when (command) {
            is RokuPlaybackCommand.DeepLink -> {
                logger.debug("Executing deep link: {}", command.url)
                sendEcpRequest(command.url, "POST")
            }
            is RokuPlaybackCommand.ActionSequence -> {
                logger.debug("Executing action sequence with {} steps", command.actions.size)
                executeActionSequence(command.actions)
            }
        }
    }

    /**
     * Executes a sequence of Roku actions
     */
    private fun executeActionSequence(actions: List<RokuAction>) {
        actions.forEachIndexed { index, action ->
            logger.debug("Action {}/{}: {}", index + 1, actions.size, action)

            when (action) {
                is RokuAction.Launch -> {
                    val url = if (action.params.isNotEmpty()) {
                        "http://$deviceIp:8060/launch/${action.channelId}?${action.params}"
                    } else {
                        "http://$deviceIp:8060/launch/${action.channelId}"
                    }
                    sendEcpRequest(url, "POST")
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
                    Thread.sleep(action.milliseconds)
                }
            }
        }
    }

    /**
     * Types text character by character on Roku
     */
    private fun typeText(text: String) {
        text.forEach { char ->
            val litCode = when {
                char.isLetter() -> "Lit_${char.uppercaseChar()}"
                char.isDigit() -> "Lit_$char"
                char == ' ' -> "Lit_%20"
                else -> {
                    logger.warn("Unsupported character for typing: {}", char)
                    return@forEach
                }
            }

            val url = "http://$deviceIp:8060/keypress/$litCode"
            sendEcpRequest(url, "POST")
            Thread.sleep(CHAR_DELAY_MS)  // Small delay between characters
        }
    }

    /**
     * Sends a key press to the Roku device
     */
    private fun sendKeyPress(key: RokuKey) {
        val keyName = when (key) {
            RokuKey.HOME -> "Home"
            RokuKey.UP -> "Up"
            RokuKey.DOWN -> "Down"
            RokuKey.LEFT -> "Left"
            RokuKey.RIGHT -> "Right"
            RokuKey.SELECT -> "Select"
            RokuKey.BACK -> "Back"
            RokuKey.BACKSPACE -> "Backspace"
            RokuKey.PLAY -> "Play"
            RokuKey.PAUSE -> "Pause"
            RokuKey.REV -> "Rev"
            RokuKey.FWD -> "Fwd"
            RokuKey.INSTANT_REPLAY -> "InstantReplay"
            RokuKey.INFO -> "Info"
            RokuKey.SEARCH -> "Search"
        }

        val url = "http://$deviceIp:8060/keypress/$keyName"
        sendEcpRequest(url, "POST")
        Thread.sleep(KEYPRESS_DELAY_MS)  // Small delay between keypresses
    }

    /**
     * Gets device information from Roku
     */
    fun getDeviceInfo(): String? {
        val url = "http://$deviceIp:8060/query/device-info"
        return sendEcpRequest(url, "GET")
    }

    /**
     * Sends an ECP request to the Roku device
     */
    private fun sendEcpRequest(urlString: String, method: String): String? {
        return try {
            val requestBuilder = Request.Builder().url(urlString)

            when (method.uppercase()) {
                "GET" -> requestBuilder.get()
                "POST" -> requestBuilder.post("".toRequestBody())
                else -> throw IllegalArgumentException("Unsupported HTTP method: $method")
            }

            val request = requestBuilder.build()
            val response = httpClient.newCall(request).execute()

            val responseBody = response.body?.string()

            if (response.isSuccessful) {
                logger.debug("ECP request successful: $urlString")
                responseBody
            } else {
                logger.error("ECP request failed: $urlString, response code: ${response.code}")
                throw PluginException("ECP request failed with response code: ${response.code}")
            }
        } catch (e: Exception) {
            logger.error("Failed to send ECP request to $urlString: ${e.message}", e)
            throw PluginException("Failed to send ECP request: ${e.message}", e)
        }
    }

    @Throws(PluginException::class)
    override fun search(query: String, options: SearchOptions): List<SearchResult<RokuMediaContent>> {
        try {
            logger.info("Searching for '$query' across {} channel(s)", channelPlugins.size)

            val allResults = mutableListOf<SearchResult<RokuMediaContent>>()

            // 1. Brave Search results (if configured)
            braveSearchProvider?.let { braveSearch ->
                try {
                    val braveResults = braveSearch.searchStreaming(query, options.limit)
                    logger.info("Brave Search returned {} results", braveResults.size)
                    allResults.addAll(braveResults.map { content ->
                        SearchResult(
                            title = content.title ?: "Unknown",
                            url = content.metadata?.searchUrl,
                            mediaUrl = null,
                            content = content
                        )
                    })
                } catch (e: Exception) {
                    logger.warn("Brave Search failed: ${e.message}")
                }
            }

            // 2. Channel plugin search results (e.g., Emby API)
            channelPlugins.values.forEach { channelPlugin ->
                try {
                    val results = channelPlugin.search(query)
                    logger.debug("Channel '{}' returned {} results", channelPlugin.getChannelName(), results.size)
                    allResults.addAll(results.map { content ->
                        SearchResult(
                            title = content.title ?: "Unknown",
                            url = null,
                            mediaUrl = null,
                            content = content
                        )
                    })
                } catch (e: Exception) {
                    logger.warn("Channel '{}' search failed: {}", channelPlugin.getChannelName(), e.message)
                }
            }

            // Deduplicate by channel + contentId
            val dedupResults = allResults.distinctBy { it.content.channelId to it.content.contentId }

            logger.info("Found {} total search results for query '$query'", dedupResults.size)
            return dedupResults

        } catch (e: Exception) {
            logger.error("Search failed for query '$query': ${e.message}", e)
            throw PluginException("Search failed: ${e.message}", e)
        }
    }

    /**
     * Returns all registered channel plugins.
     * Used by SearchResource to expose channel search URLs.
     */
    fun getAllChannelPlugins(): Collection<RokuChannelPlugin> {
        return channelPlugins.values
    }
}
