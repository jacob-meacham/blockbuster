package com.blockbuster.plugin.roku

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HBOMaxRokuChannelPluginTest : StreamingRokuChannelPluginTest() {
    override val plugin = HBOMaxRokuChannelPlugin()
    override val expectedChannelId = "61322"
    override val expectedChannelName = "HBO Max"
    override val expectedPostLaunchKey = RokuKey.SELECT
    override val sampleContentId = "bd43b2a4-1639-4197-96d4-2ec14eb45e9e"

    @Test
    fun `extractFromUrl should extract content from max com video watch URL`() {
        val content = plugin.extractFromUrl("https://www.max.com/video/watch/bd43b2a4-1639-4197-96d4-2ec14eb45e9e")

        assertNotNull(content)
        assertEquals("HBO Max", content?.channelName)
        assertEquals("61322", content?.channelId)
        assertEquals("bd43b2a4-1639-4197-96d4-2ec14eb45e9e", content?.contentId)
        assertEquals("movie", content?.mediaType)
        assertEquals("HBO Max Content", content?.title)
    }

    @Test
    fun `extractFromUrl should extract content from max com play URL`() {
        val content = plugin.extractFromUrl("https://max.com/play/some-show-id")

        assertNotNull(content)
        assertEquals("some-show-id", content?.contentId)
    }

    @Test
    fun `extractFromUrl should extract content from legacy hbomax com URLs`() {
        val content = plugin.extractFromUrl("https://www.hbomax.com/video/watch/legacy-id")

        assertNotNull(content)
        assertEquals("HBO Max", content?.channelName)
        assertEquals("legacy-id", content?.contentId)
    }

    @Test
    fun `extractFromUrl should return null for invalid URL format`() {
        assertNull(plugin.extractFromUrl("https://max.com/browse"))
        assertNull(plugin.extractFromUrl("https://max.com/"))
        assertNull(plugin.extractFromUrl("https://www.max.com/search?q=test"))
    }

    @Test
    fun `extractFromUrl should return null for non-HBO URLs`() {
        assertNull(plugin.extractFromUrl("https://netflix.com/watch/12345"))
        assertNull(plugin.extractFromUrl("https://disneyplus.com/play/abc-123"))
    }

    @Test
    fun `extractFromUrl should extract content from hbomax com movies URL`() {
        val url = "https://www.hbomax.com/movies/howls-moving-castle/7a7a03ca-dd3a-4e62-9e43-e845f338f85e"
        val content = plugin.extractFromUrl(url)

        assertNotNull(content)
        assertEquals("HBO Max", content?.channelName)
        assertEquals("61322", content?.channelId)
        assertEquals("7a7a03ca-dd3a-4e62-9e43-e845f338f85e", content?.contentId)
        assertEquals("movie", content?.mediaType)
    }

    @Test
    fun `extractFromUrl should extract content from hbomax com series URL`() {
        val url = "https://www.hbomax.com/series/the-wire/12345678-1234-1234-1234-123456789012"
        val content = plugin.extractFromUrl(url)

        assertNotNull(content)
        assertEquals("12345678-1234-1234-1234-123456789012", content?.contentId)
    }
}
