package com.blockbuster.plugin

import com.blockbuster.media.RokuMediaContent
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class PrimeVideoRokuChannelPluginTest {

    private val plugin = PrimeVideoRokuChannelPlugin()

    @Test
    fun `getChannelId should return Prime Video channel ID`() {
        assertEquals("13", plugin.getChannelId())
    }

    @Test
    fun `getChannelName should return Prime Video`() {
        assertEquals("Prime Video", plugin.getChannelName())
    }

    @Test
    fun `buildPlaybackCommand should create ActionSequence with profile selection`() {
        // Given
        val content = RokuMediaContent(
            channelId = "13",
            contentId = "B0DKTFF815",
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
        assertEquals("13", launchAction.channelId)
        assertEquals("contentId=B0DKTFF815&mediaType=movie", launchAction.params)

        // Verify Wait action
        val waitAction = actionSequence.actions[1] as RokuAction.Wait
        assertEquals(2000L, waitAction.milliseconds)

        // Verify SELECT press
        val selectAction = actionSequence.actions[2] as RokuAction.Press
        assertEquals(RokuKey.SELECT, selectAction.key)
        assertEquals(1, selectAction.count)
    }

    @Test
    fun `buildPlaybackCommand should handle series mediaType`() {
        // Given
        val content = RokuMediaContent(
            channelId = "13",
            contentId = "B0FQM41JFJ",
            mediaType = "series",
            title = "Test Series"
        )
        val rokuIp = "192.168.1.100"

        // When
        val command = plugin.buildPlaybackCommand(content, rokuIp)

        // Then
        val actionSequence = command as RokuPlaybackCommand.ActionSequence
        val launchAction = actionSequence.actions[0] as RokuAction.Launch
        assertTrue(launchAction.params.contains("mediaType=series"))
        assertTrue(launchAction.params.contains("contentId=B0FQM41JFJ"))
    }

    @Test
    fun `buildPlaybackCommand should default to movie when mediaType is null`() {
        // Given
        val content = RokuMediaContent(
            channelId = "13",
            contentId = "B0EXAMPLE",
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
    fun `buildPlaybackCommand should handle ASIN format`() {
        // Given
        val content = RokuMediaContent(
            channelId = "13",
            contentId = "B0123456789",
            mediaType = "movie",
            title = "Test Movie"
        )
        val rokuIp = "192.168.1.100"

        // When
        val command = plugin.buildPlaybackCommand(content, rokuIp)

        // Then
        val actionSequence = command as RokuPlaybackCommand.ActionSequence
        val launchAction = actionSequence.actions[0] as RokuAction.Launch
        assertTrue(launchAction.params.contains("contentId=B0123456789"))
    }

    @Test
    fun `search should return manual search instructions`() {
        // When
        val results = plugin.search("jack ryan")

        // Then
        assertEquals(1, results.size)

        val instruction = results[0]
        assertEquals("MANUAL_SEARCH_REQUIRED", instruction.contentId)
        assertEquals("Manual Search: How to find Prime Video content IDs", instruction.title)
        assertEquals("instruction", instruction.mediaType)
        assertEquals("Prime Video", instruction.channelName)
        assertEquals("13", instruction.channelId)

        // Verify instructions are present
        val instructions = instruction.metadata?.get("instructions") as? String
        assertNotNull(instructions)
        assertTrue(instructions!!.contains("Amazon Prime Video"))
        assertTrue(instructions.contains("ASIN"))
        assertTrue(instructions.contains("B0"))

        // Verify search query is stored
        val searchQuery = instruction.metadata?.get("searchQuery") as? String
        assertEquals("jack ryan", searchQuery)
    }

    @Test
    fun `search instructions should include example ASINs`() {
        // When
        val results = plugin.search("test")

        // Then
        val instructions = results[0].metadata?.get("instructions") as String
        assertTrue(instructions.contains("B0DKTFF815"))
        assertTrue(instructions.contains("B0FQM41JFJ"))
    }

    @Test
    fun `search instructions should explain series behavior`() {
        // When
        val results = plugin.search("test")

        // Then
        val instructions = results[0].metadata?.get("instructions") as String
        assertTrue(instructions.contains("series"))
        assertTrue(instructions.contains("S1E1"))
        assertTrue(instructions.contains("movie"))
    }

    @Test
    fun `search instructions should explain ASIN format`() {
        // When
        val results = plugin.search("test")

        // Then
        val instructions = results[0].metadata?.get("instructions") as String
        assertTrue(instructions.contains("Standard Identification Number"))
        assertTrue(instructions.contains("start with \"B0\""))
    }
}
