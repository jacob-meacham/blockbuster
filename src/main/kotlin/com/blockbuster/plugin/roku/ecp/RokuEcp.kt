package com.blockbuster.plugin.roku.ecp

import com.blockbuster.media.RokuMediaContent
import com.blockbuster.plugin.roku.RokuAction
import com.blockbuster.plugin.roku.RokuKey
import com.blockbuster.plugin.roku.RokuPlaybackCommand

/**
 * Roku ECP URL-to-playback core.
 *
 * GENERATED from the `roku-deeplink` spec-library (v1.1.0) via speclib. Implements
 * the spec's two functions and channel catalog:
 *
 *  - [convertUrlToEcpCommand] — the spec's URL extraction (URL channels only)
 *  - [buildPlaybackCommand] — the spec's catalog-driven playback command:
 *    `[launch] + channel.post_launch_actions` (which may be empty, as for Emby)
 *
 * This is the portable, context-independent core the spec covers. Consumer
 * concerns (library search, device I/O, the Emby API) live in the channel
 * plugins, not here. Regenerate with `speclib sync roku-deeplink` rather than
 * hand-editing.
 */
object RokuEcp {
    private const val POST_LAUNCH_DELAY_MS = 2000L
    private const val DEFAULT_MEDIA_TYPE = "movie"

    private data class Channel(
        val id: String,
        val name: String,
        val urlPattern: Regex?,
        val determineMediaType: (url: String) -> String?,
        val buildLaunchParams: (content: RokuMediaContent) -> String,
        val postLaunchActions: List<RokuAction>,
    )

    private fun contentIdParams(content: RokuMediaContent): String =
        "contentId=${content.contentId}&mediaType=${content.mediaType?.lowercase() ?: DEFAULT_MEDIA_TYPE}"

    private fun embyParams(content: RokuMediaContent): String =
        buildString {
            append("Command=PlayNow&ItemIds=${content.contentId}")
            val ticks = content.metadata?.resumePositionTicks
            if (ticks != null && ticks > 0) append("&StartPositionTicks=$ticks")
        }

    private fun launchThenPress(key: RokuKey): List<RokuAction> =
        listOf(RokuAction.Wait(POST_LAUNCH_DELAY_MS), RokuAction.Press(key))

    private val catalog: List<Channel> =
        listOf(
            Channel(
                id = "12",
                name = "Netflix",
                urlPattern = Regex("""netflix\.com/(?:watch|title)/(\d+)"""),
                determineMediaType = { url -> if (url.contains("/watch/")) "movie" else "series" },
                buildLaunchParams = ::contentIdParams,
                postLaunchActions = launchThenPress(RokuKey.PLAY),
            ),
            Channel(
                id = "291097",
                name = "Disney+",
                urlPattern = Regex("""disneyplus\.com/(?:(?:play|video)/|browse/entity-)([a-f0-9-]+)"""),
                determineMediaType = { DEFAULT_MEDIA_TYPE },
                buildLaunchParams = ::contentIdParams,
                postLaunchActions = launchThenPress(RokuKey.SELECT),
            ),
            Channel(
                id = "61322",
                name = "HBO Max",
                urlPattern = Regex("""(?:max\.com|hbomax\.com)/(?:(?:movies|series)/[^/]+/|(?:video/watch|play)/)([^/?]+)"""),
                determineMediaType = { DEFAULT_MEDIA_TYPE },
                buildLaunchParams = ::contentIdParams,
                postLaunchActions = launchThenPress(RokuKey.SELECT),
            ),
            Channel(
                id = "13",
                name = "Prime Video",
                urlPattern = Regex("""(?:amazon\.com|primevideo\.com)/.*?/([B][A-Z0-9]{9})"""),
                determineMediaType = { DEFAULT_MEDIA_TYPE },
                buildLaunchParams = ::contentIdParams,
                postLaunchActions = launchThenPress(RokuKey.SELECT),
            ),
            Channel(
                id = "44191",
                name = "Emby",
                urlPattern = null,
                determineMediaType = { null },
                buildLaunchParams = ::embyParams,
                postLaunchActions = emptyList(),
            ),
        )

    private val byId: Map<String, Channel> = catalog.associateBy { it.id }

    /**
     * Spec Function 1: extract content from a public streaming URL, or `null` if
     * no URL channel matches. Uses `search` (not anchored `match`) semantics.
     */
    fun convertUrlToEcpCommand(url: String): RokuMediaContent? =
        catalog.firstNotNullOfOrNull { channel ->
            channel.urlPattern?.find(url)?.let { match ->
                RokuMediaContent(
                    channelName = channel.name,
                    channelId = channel.id,
                    contentId = match.groupValues[1],
                    title = "${channel.name} Content",
                    mediaType = channel.determineMediaType(url),
                )
            }
        }

    /**
     * Spec Function 2: build the playback command for a content descriptor — a
     * `launch` followed by the channel's post-launch actions (may be empty).
     */
    fun buildPlaybackCommand(content: RokuMediaContent): RokuPlaybackCommand {
        val channel =
            requireNotNull(byId[content.channelId]) {
                "Unknown Roku channel: ${content.channelId}"
            }
        val actions =
            buildList {
                add(RokuAction.Launch(channelId = channel.id, params = channel.buildLaunchParams(content)))
                addAll(channel.postLaunchActions)
            }
        return RokuPlaybackCommand.ActionSequence(actions)
    }
}
