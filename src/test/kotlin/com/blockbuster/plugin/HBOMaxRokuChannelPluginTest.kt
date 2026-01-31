package com.blockbuster.plugin

import com.blockbuster.media.RokuMediaContent
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class HBOMaxRokuChannelPluginTest {

    private val plugin = HBOMaxRokuChannelPlugin()

    @Test
    fun `getChannelId should return HBO Max channel ID`() {
        assertEquals("61322", plugin.getChannelId())
    }

    @Test
    fun `getChannelName should return HBO Max`() {
        assertEquals("HBO Max", plugin.getChannelName())
    }

    @Test
    fun `buildPlaybackCommand should create ActionSequence with profile selection`() {
        // Given
        val content = RokuMediaContent(
            channelId = "61322",
            contentId = "bd43b2a4-1639-4197-96d4-2ec14eb45e9e",
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
        assertEquals("61322", launchAction.channelId)
        assertEquals("contentId=bd43b2a4-1639-4197-96d4-2ec14eb45e9e&mediaType=movie", launchAction.params)

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
            channelId = "61322",
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
            channelId = "61322",
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
    fun `search should return manual search instructions`() {
        // When
        val results = plugin.search("game of thrones")

        // Then
        assertEquals(1, results.size)

        val instruction = results[0]
        assertEquals("MANUAL_SEARCH_REQUIRED", instruction.contentId)
        assertEquals("Manual Search: How to find HBO Max content IDs", instruction.title)
        assertEquals("instruction", instruction.mediaType)
        assertEquals("HBO Max", instruction.channelName)
        assertEquals("61322", instruction.channelId)

        // Verify instructions are present
        val instructions = instruction.metadata?.get("instructions") as? String
        assertNotNull(instructions)
        assertTrue(instructions!!.contains("HBO Max"))
        assertTrue(instructions.contains("play.hbomax.com"))
        assertTrue(instructions.contains("FIRST UUID"))

        // Verify search query is stored
        val searchQuery = instruction.metadata?.get("searchQuery") as? String
        assertEquals("game of thrones", searchQuery)
    }

    @Test
    fun `search instructions should warn about incorrect URL format`() {
        // When
        val results = plugin.search("test")

        // Then
        val instructions = results[0].metadata?.get("instructions") as String
        assertTrue(instructions.contains("/video/watch/"))
        assertTrue(instructions.contains("CORRECT"))
        assertTrue(instructions.contains("INCORRECT"))
        assertTrue(instructions.contains("/movie/"))
    }

    @Test
    fun `search instructions should include example UUID`() {
        // When
        val results = plugin.search("test")

        // Then
        val instructions = results[0].metadata?.get("instructions") as String
        assertTrue(instructions.contains("bd43b2a4-1639-4197-96d4-2ec14eb45e9e"))
    }
}
