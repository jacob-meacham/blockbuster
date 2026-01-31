package com.blockbuster.plugin

import com.blockbuster.media.RokuMediaContent
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class NetflixRokuChannelPluginTest {

    private val plugin = NetflixRokuChannelPlugin()

    @Test
    fun `getChannelId should return Netflix channel ID`() {
        assertEquals("12", plugin.getChannelId())
    }

    @Test
    fun `getChannelName should return Netflix`() {
        assertEquals("Netflix", plugin.getChannelName())
    }

    @Test
    fun `buildPlaybackCommand should create ActionSequence with PLAY press`() {
        // Given
        val content = RokuMediaContent(
            channelId = "12",
            contentId = "81444554",
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
        assertEquals("12", launchAction.channelId)
        assertEquals("contentId=81444554&mediaType=movie", launchAction.params)

        // Verify Wait action
        val waitAction = actionSequence.actions[1] as RokuAction.Wait
        assertEquals(2000L, waitAction.milliseconds)

        // Verify PLAY press (different from Disney+)
        val playAction = actionSequence.actions[2] as RokuAction.Press
        assertEquals(RokuKey.PLAY, playAction.key)
        assertEquals(1, playAction.count)
    }

    @Test
    fun `buildPlaybackCommand should handle episode mediaType`() {
        // Given
        val content = RokuMediaContent(
            channelId = "12",
            contentId = "80179766",
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
            channelId = "12",
            contentId = "12345",
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
    fun `buildPlaybackCommand should handle numeric content IDs`() {
        // Given
        val content = RokuMediaContent(
            channelId = "12",
            contentId = "987654321",
            mediaType = "movie",
            title = "Test Movie"
        )
        val rokuIp = "192.168.1.100"

        // When
        val command = plugin.buildPlaybackCommand(content, rokuIp)

        // Then
        val actionSequence = command as RokuPlaybackCommand.ActionSequence
        val launchAction = actionSequence.actions[0] as RokuAction.Launch
        assertTrue(launchAction.params.contains("contentId=987654321"))
    }

    @Test
    fun `search should return manual search instructions`() {
        // When
        val results = plugin.search("stranger things")

        // Then
        assertEquals(1, results.size)

        val instruction = results[0]
        assertEquals("MANUAL_SEARCH_REQUIRED", instruction.contentId)
        assertEquals("Manual Search: How to find Netflix content IDs", instruction.title)
        assertEquals("instruction", instruction.mediaType)
        assertEquals("Netflix", instruction.channelName)
        assertEquals("12", instruction.channelId)

        // Verify instructions are present
        val instructions = instruction.metadata?.get("instructions") as? String
        assertNotNull(instructions)
        assertTrue(instructions!!.contains("Netflix"))
        assertTrue(instructions.contains("netflix.com"))
        assertTrue(instructions.contains("numeric ID"))

        // Verify search query is stored
        val searchQuery = instruction.metadata?.get("searchQuery") as? String
        assertEquals("stranger things", searchQuery)
    }

    @Test
    fun `search instructions should include example content IDs`() {
        // When
        val results = plugin.search("test")

        // Then
        val instructions = results[0].metadata?.get("instructions") as String
        assertTrue(instructions.contains("81444554"))
        assertTrue(instructions.contains("80179766"))
    }

    @Test
    fun `search instructions should explain mediaType options`() {
        // When
        val results = plugin.search("test")

        // Then
        val instructions = results[0].metadata?.get("instructions") as String
        assertTrue(instructions.contains("movie"))
        assertTrue(instructions.contains("episode"))
    }
}
