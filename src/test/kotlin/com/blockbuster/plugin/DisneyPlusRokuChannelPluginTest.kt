package com.blockbuster.plugin

import com.blockbuster.media.RokuMediaContent
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class DisneyPlusRokuChannelPluginTest {

    private val plugin = DisneyPlusRokuChannelPlugin()

    @Test
    fun `getChannelId should return Disney+ channel ID`() {
        assertEquals("291097", plugin.getChannelId())
    }

    @Test
    fun `getChannelName should return Disney+`() {
        assertEquals("Disney+", plugin.getChannelName())
    }

    @Test
    fun `buildPlaybackCommand should create ActionSequence with profile selection`() {
        // Given
        val content = RokuMediaContent(
            channelId = "291097",
            contentId = "f63db666-b097-4c61-99c1-b778de2d4ae1",
            mediaType = "movie",
            title = "Test Movie"
        )
        val rokuIp = "192.168.1.100"

        // When
        val command = plugin.buildPlaybackCommand(content, rokuIp)

        // Then
        assertTrue(command is RokuPlaybackCommand.ActionSequence)
        val actionSequence = command as RokuPlaybackCommand.ActionSequence

        assertEquals(3, actionSequence.actions.size)

        // Verify Launch action
        val launchAction = actionSequence.actions[0] as RokuAction.Launch
        assertEquals("291097", launchAction.channelId)
        assertEquals("contentId=f63db666-b097-4c61-99c1-b778de2d4ae1&mediaType=movie", launchAction.params)

        // Verify Wait action
        val waitAction = actionSequence.actions[1] as RokuAction.Wait
        assertEquals(2000L, waitAction.milliseconds)

        // Verify SELECT press
        val selectAction = actionSequence.actions[2] as RokuAction.Press
        assertEquals(RokuKey.SELECT, selectAction.key)
        assertEquals(1, selectAction.count)
    }

    @Test
    fun `buildPlaybackCommand should handle episode mediaType`() {
        // Given
        val content = RokuMediaContent(
            channelId = "291097",
            contentId = "episode-uuid",
            mediaType = "episode",
            title = "Test Episode"
        )
        val rokuIp = "192.168.1.100"

        // When
        val command = plugin.buildPlaybackCommand(content, rokuIp)

        // Then
        val actionSequence = command as RokuPlaybackCommand.ActionSequence
        val launchAction = actionSequence.actions[0] as RokuAction.Launch
        assertTrue(launchAction.params.contains("mediaType=episode"))
    }

    @Test
    fun `buildPlaybackCommand should default to movie when mediaType is null`() {
        // Given
        val content = RokuMediaContent(
            channelId = "291097",
            contentId = "content-uuid",
            mediaType = null,
            title = "Test Content"
        )
        val rokuIp = "192.168.1.100"

        // When
        val command = plugin.buildPlaybackCommand(content, rokuIp)

        // Then
        val actionSequence = command as RokuPlaybackCommand.ActionSequence
        val launchAction = actionSequence.actions[0] as RokuAction.Launch
        assertTrue(launchAction.params.contains("mediaType=movie"))
    }

    @Test
    fun `buildPlaybackCommand should handle uppercase mediaType`() {
        // Given
        val content = RokuMediaContent(
            channelId = "291097",
            contentId = "content-uuid",
            mediaType = "SERIES",
            title = "Test Series"
        )
        val rokuIp = "192.168.1.100"

        // When
        val command = plugin.buildPlaybackCommand(content, rokuIp)

        // Then
        val actionSequence = command as RokuPlaybackCommand.ActionSequence
        val launchAction = actionSequence.actions[0] as RokuAction.Launch
        assertTrue(launchAction.params.contains("mediaType=series"))
    }

    @Test
    fun `search should return manual search instructions`() {
        // When
        val results = plugin.search("mandalorian")

        // Then
        assertEquals(1, results.size)

        val instruction = results[0]
        assertEquals("MANUAL_SEARCH_REQUIRED", instruction.contentId)
        assertEquals("Manual Search: How to find Disney+ content IDs", instruction.title)
        assertEquals("instruction", instruction.mediaType)
        assertEquals("Disney+", instruction.channelName)
        assertEquals("291097", instruction.channelId)

        // Verify instructions are present
        val instructions = instruction.metadata?.get("instructions") as? String
        assertNotNull(instructions)
        assertTrue(instructions!!.contains("Disney+"))
        assertTrue(instructions.contains("disneyplus.com"))
        assertTrue(instructions.contains("UUID"))

        // Verify search query is stored
        val searchQuery = instruction.metadata?.get("searchQuery") as? String
        assertEquals("mandalorian", searchQuery)
    }

    @Test
    fun `search should work with empty query`() {
        // When
        val results = plugin.search("")

        // Then
        assertEquals(1, results.size)
        val instruction = results[0]
        assertEquals("MANUAL_SEARCH_REQUIRED", instruction.contentId)
    }

    @Test
    fun `search instructions should include example UUIDs`() {
        // When
        val results = plugin.search("test")

        // Then
        val instructions = results[0].metadata?.get("instructions") as String
        assertTrue(instructions.contains("f63db666-b097-4c61-99c1-b778de2d4ae1"))
        assertTrue(instructions.contains("3jLIGMDYINqD"))
    }
}
