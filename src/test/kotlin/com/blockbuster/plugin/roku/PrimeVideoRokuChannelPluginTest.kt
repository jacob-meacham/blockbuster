package com.blockbuster.plugin.roku

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PrimeVideoRokuChannelPluginTest : StreamingRokuChannelPluginTest() {
    override val plugin = PrimeVideoRokuChannelPlugin()
    override val expectedChannelId = "13"
    override val expectedChannelName = "Prime Video"
    override val expectedPostLaunchKey = RokuKey.SELECT
    override val sampleContentId = "B0DKTFF815"

    @Test
    fun `extractFromUrl should extract ASIN from amazon gp video URL`() {
        val content = plugin.extractFromUrl("https://www.amazon.com/gp/video/detail/B0DKTFF815")

        assertNotNull(content)
        assertEquals("Prime Video", content?.channelName)
        assertEquals("13", content?.channelId)
        assertEquals("B0DKTFF815", content?.contentId)
        assertEquals("movie", content?.mediaType)
        assertEquals("Prime Video Content", content?.title)
    }

    @Test
    fun `extractFromUrl should extract ASIN from various amazon URL formats`() {
        val content1 = plugin.extractFromUrl("https://amazon.com/gp/video/detail/B0FQM41JFJ/ref=xyz")
        val content2 = plugin.extractFromUrl("https://www.amazon.com/gp/video/offers/B012345678")
        val content3 = plugin.extractFromUrl("https://amazon.com/dp/B0FQM41JFJ/ref=xyz")

        assertNotNull(content1)
        assertEquals("B0FQM41JFJ", content1?.contentId)

        assertNotNull(content2)
        assertEquals("B012345678", content2?.contentId)

        assertNotNull(content3)
        assertEquals("B0FQM41JFJ", content3?.contentId)
    }

    @Test
    fun `extractFromUrl should handle primevideo com URLs`() {
        val content = plugin.extractFromUrl("https://www.primevideo.com/detail/B0EXAMPL12")

        assertNotNull(content)
        assertEquals("Prime Video", content?.channelName)
        assertEquals("B0EXAMPL12", content?.contentId)
    }

    @Test
    fun `extractFromUrl should return null for URLs without ASIN`() {
        assertNull(plugin.extractFromUrl("https://amazon.com/browse"))
        assertNull(plugin.extractFromUrl("https://www.amazon.com/"))
        assertNull(plugin.extractFromUrl("https://amazon.com/search?k=movies"))
    }

    @Test
    fun `extractFromUrl should return null for non-Prime-Video URLs`() {
        assertNull(plugin.extractFromUrl("https://netflix.com/watch/12345"))
        assertNull(plugin.extractFromUrl("https://disneyplus.com/play/abc-123"))
    }
}
