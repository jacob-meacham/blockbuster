package com.blockbuster.plugin.roku

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NetflixRokuChannelPluginTest : StreamingRokuChannelPluginTest() {
    override val plugin = NetflixRokuChannelPlugin()
    override val expectedChannelId = "12"
    override val expectedChannelName = "Netflix"
    override val expectedPostLaunchKey = RokuKey.PLAY
    override val sampleContentId = "81444554"

    @Test
    fun `extractFromUrl should extract content from watch URL as movie`() {
        val content = plugin.extractFromUrl("https://www.netflix.com/watch/81444554")

        assertNotNull(content)
        assertEquals("Netflix", content?.channelName)
        assertEquals("12", content?.channelId)
        assertEquals("81444554", content?.contentId)
        assertEquals("movie", content?.mediaType)
        assertEquals("Netflix Content", content?.title)
    }

    @Test
    fun `extractFromUrl should extract content from title URL as series`() {
        val content = plugin.extractFromUrl("https://www.netflix.com/title/80179766")

        assertNotNull(content)
        assertEquals("80179766", content?.contentId)
        assertEquals("series", content?.mediaType)
    }

    @Test
    fun `extractFromUrl should return null for invalid URL format`() {
        assertNull(plugin.extractFromUrl("https://netflix.com/browse"))
        assertNull(plugin.extractFromUrl("https://netflix.com/"))
        assertNull(plugin.extractFromUrl("https://www.netflix.com/search?q=test"))
    }

    @Test
    fun `extractFromUrl should return null for non-Netflix URLs`() {
        assertNull(plugin.extractFromUrl("https://disneyplus.com/play/abc-123"))
        assertNull(plugin.extractFromUrl("https://www.max.com/video/watch/xyz"))
    }
}
