package com.blockbuster.plugin.roku

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DisneyPlusRokuChannelPluginTest : StreamingRokuChannelPluginTest() {
    override val plugin = DisneyPlusRokuChannelPlugin()
    override val expectedChannelId = "291097"
    override val expectedChannelName = "Disney+"
    override val expectedPostLaunchKey = RokuKey.SELECT
    override val sampleContentId = "f63db666-b097-4c61-99c1-b778de2d4ae1"

    @Test
    fun `extractFromUrl should extract content from play URL`() {
        val content = plugin.extractFromUrl("https://www.disneyplus.com/play/f63db666-b097-4c61-99c1-b778de2d4ae1")

        assertNotNull(content)
        assertEquals("Disney+", content?.channelName)
        assertEquals("291097", content?.channelId)
        assertEquals("f63db666-b097-4c61-99c1-b778de2d4ae1", content?.contentId)
        assertEquals("movie", content?.mediaType)
        assertEquals("Disney+ Content", content?.title)
    }

    @Test
    fun `extractFromUrl should extract content from video URL`() {
        val content = plugin.extractFromUrl("https://disneyplus.com/video/abc-123-def")

        assertNotNull(content)
        assertEquals("abc-123-def", content?.contentId)
    }

    @Test
    fun `extractFromUrl should return null for invalid URL format`() {
        assertNull(plugin.extractFromUrl("https://disneyplus.com/browse"))
        assertNull(plugin.extractFromUrl("https://disneyplus.com/"))
        assertNull(plugin.extractFromUrl("https://www.disneyplus.com/search?q=test"))
    }

    @Test
    fun `extractFromUrl should return null for non-DisneyPlus URLs`() {
        assertNull(plugin.extractFromUrl("https://netflix.com/watch/12345"))
        assertNull(plugin.extractFromUrl("https://www.max.com/video/watch/xyz"))
    }
}
