package com.blockbuster.plugin.roku

import com.blockbuster.media.RokuMediaContent
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Abstract base test for streaming Roku channel plugins.
 * Tests the shared behavior from [StreamingRokuChannelPlugin].
 *
 * Subclasses provide channel-specific test values and add URL extraction tests.
 */
abstract class StreamingRokuChannelPluginTest {

    abstract val plugin: StreamingRokuChannelPlugin
    abstract val expectedChannelId: String
    abstract val expectedChannelName: String
    abstract val expectedPostLaunchKey: RokuKey
    abstract val sampleContentId: String

    @Test
    fun `getChannelId should return correct channel ID`() {
        assertEquals(expectedChannelId, plugin.getChannelId())
    }

    @Test
    fun `getChannelName should return correct channel name`() {
        assertEquals(expectedChannelName, plugin.getChannelName())
    }

    @Test
    fun `buildPlaybackCommand should create ActionSequence with correct structure`() {
        val content = RokuMediaContent(
            channelId = expectedChannelId,
            contentId = sampleContentId,
            mediaType = "movie",
            title = "Test Movie"
        )

        val command = plugin.buildPlaybackCommand(content, "192.168.1.100")

        assertTrue(command is RokuPlaybackCommand.ActionSequence)
        val actionSequence = command as RokuPlaybackCommand.ActionSequence
        assertEquals(3, actionSequence.actions.size)

        val launchAction = actionSequence.actions[0] as RokuAction.Launch
        assertEquals(expectedChannelId, launchAction.channelId)
        assertEquals("contentId=$sampleContentId&mediaType=movie", launchAction.params)

        val waitAction = actionSequence.actions[1] as RokuAction.Wait
        assertEquals(2000L, waitAction.milliseconds)

        val pressAction = actionSequence.actions[2] as RokuAction.Press
        assertEquals(expectedPostLaunchKey, pressAction.key)
        assertEquals(1, pressAction.count)
    }

    @Test
    fun `buildPlaybackCommand should handle episode mediaType`() {
        val content = RokuMediaContent(
            channelId = expectedChannelId,
            contentId = sampleContentId,
            mediaType = "episode",
            title = "Test Episode"
        )

        val command = plugin.buildPlaybackCommand(content, "192.168.1.100")
        val actionSequence = command as RokuPlaybackCommand.ActionSequence
        val launchAction = actionSequence.actions[0] as RokuAction.Launch
        assertTrue(launchAction.params.contains("mediaType=episode"))
    }

    @Test
    fun `buildPlaybackCommand should default to movie when mediaType is null`() {
        val content = RokuMediaContent(
            channelId = expectedChannelId,
            contentId = sampleContentId,
            mediaType = null,
            title = "Test Content"
        )

        val command = plugin.buildPlaybackCommand(content, "192.168.1.100")
        val actionSequence = command as RokuPlaybackCommand.ActionSequence
        val launchAction = actionSequence.actions[0] as RokuAction.Launch
        assertTrue(launchAction.params.contains("mediaType=movie"))
    }

    @Test
    fun `buildPlaybackCommand should handle uppercase mediaType`() {
        val content = RokuMediaContent(
            channelId = expectedChannelId,
            contentId = sampleContentId,
            mediaType = "SERIES",
            title = "Test Series"
        )

        val command = plugin.buildPlaybackCommand(content, "192.168.1.100")
        val actionSequence = command as RokuPlaybackCommand.ActionSequence
        val launchAction = actionSequence.actions[0] as RokuAction.Launch
        assertTrue(launchAction.params.contains("mediaType=series"))
    }

    @Test
    fun `search should return empty list`() {
        assertEquals(0, plugin.search("test query").size)
    }
}
