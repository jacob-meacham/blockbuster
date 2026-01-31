package com.blockbuster.plugin

import com.blockbuster.media.RokuMediaContent

/**
 * HBO Max channel plugin for Roku.
 *
 * Uses standard Roku ECP deep linking with hybrid ActionSequence (profile selection required).
 *
 * Format: http://<roku-ip>:8060/launch/61322?contentId=<id>&mediaType=<type>
 * - contentId: UUID from HBO Max video URLs (first ID from /video/watch/{id1}/{id2})
 * - mediaType: "movie" or "episode"
 *
 * Important: Use first ID from /video/watch/{id1}/{id2} URLs, NOT from /movie/{id} URLs
 *
 * Workflow:
 * 1. Deep link launches HBO Max to content
 * 2. Profile selection screen appears
 * 3. SELECT press chooses default profile
 * 4. Content auto-plays
 */
class HBOMaxRokuChannelPlugin : RokuChannelPlugin {

    override fun getChannelId(): String = "61322"

    override fun getChannelName(): String = "HBO Max"

    override fun buildPlaybackCommand(content: RokuMediaContent, rokuDeviceIp: String): RokuPlaybackCommand {
        val contentId = content.contentId
        val mediaType = content.mediaType?.lowercase() ?: "movie"

        return RokuPlaybackCommand.ActionSequence(
            listOf(
                RokuAction.Launch(
                    channelId = getChannelId(),
                    params = "contentId=$contentId&mediaType=$mediaType"
                ),
                RokuAction.Wait(2000),  // Wait for profile selection screen
                RokuAction.Press(RokuKey.SELECT, 1)  // Select default profile
            )
        )
    }

    override fun search(query: String): List<RokuMediaContent> {
        // HBO Max doesn't provide a public search API
        // Return instructions for manual content ID discovery
        return listOf(
            RokuMediaContent(
                channelName = getChannelName(),
                channelId = getChannelId(),
                contentId = "MANUAL_SEARCH_REQUIRED",
                title = "Manual Search: How to find HBO Max content IDs",
                mediaType = "instruction",
                metadata = mapOf(
                    "instructions" to """
                        HBO Max does not provide a public search API.

                        To find content IDs manually:

                        1. Open HBO Max in your web browser
                        2. Search for and navigate to the content you want
                        3. Look at the URL in your browser's address bar
                        4. Extract the FIRST UUID from video URLs

                        IMPORTANT: Use /video/watch/ URLs, NOT /movie/ URLs

                        Examples:
                        ✅ CORRECT:
                        • URL: https://play.hbomax.com/video/watch/bd43b2a4-1639-4197-96d4-2ec14eb45e9e/b42d9d8f-71ca-40e2-8f88-2abe03ff9579
                        • Content ID: bd43b2a4-1639-4197-96d4-2ec14eb45e9e (use FIRST UUID)

                        ❌ INCORRECT (won't work):
                        • URL: https://play.hbomax.com/movie/7a7a03ca-dd3a-4e62-9e43-e845f338f85e
                        • This format doesn't work with Roku deep linking

                        Use the first UUID from /video/watch/ URLs as contentId.
                        Set mediaType to "movie" or "episode" as appropriate.
                    """.trimIndent(),
                    "searchQuery" to query
                )
            )
        )
    }
}
