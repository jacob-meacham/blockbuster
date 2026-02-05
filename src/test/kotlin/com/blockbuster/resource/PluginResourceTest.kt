package com.blockbuster.resource

import com.blockbuster.media.RokuMediaContent
import com.blockbuster.plugin.ChannelInfoItem
import com.blockbuster.plugin.ChannelInfoProvider
import com.blockbuster.plugin.MediaPlugin
import com.blockbuster.plugin.PluginRegistry
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

class PluginResourceTest {
    private lateinit var plugins: MutableMap<String, MediaPlugin<*>>
    private lateinit var pluginResource: PluginResource

    @BeforeEach
    fun setUp() {
        plugins = mutableMapOf()
        pluginResource = PluginResource(PluginRegistry(plugins))
    }

    @Test
    fun `getChannelInfo returns channel info from ChannelInfoProvider`() {
        val rokuPlugin = mock<RokuPluginWithChannelInfo>()
        whenever(rokuPlugin.getChannelInfo()).thenReturn(
            listOf(
                ChannelInfoItem(channelId = "12", channelName = "Netflix"),
                ChannelInfoItem(channelId = "44191", channelName = "Emby"),
            ),
        )
        plugins["roku"] = rokuPlugin

        val response = pluginResource.getChannelInfo("roku")

        assertEquals(Response.Status.OK.statusCode, response.status)
        val body = response.entity as ChannelListResponse
        assertEquals(2, body.channels.size)
        assertEquals("12", body.channels[0].channelId)
        assertEquals("Netflix", body.channels[0].channelName)
        assertEquals("44191", body.channels[1].channelId)
        assertEquals("Emby", body.channels[1].channelName)
    }

    @Test
    fun `getChannelInfo returns 404 when plugin not found`() {
        val response = pluginResource.getChannelInfo("nonexistent")

        assertEquals(Response.Status.NOT_FOUND.statusCode, response.status)
        val body = response.entity as ErrorResponse
        assertEquals("Plugin 'nonexistent' not found", body.error)
    }

    @Test
    fun `getChannelInfo returns empty list when plugin is not ChannelInfoProvider`() {
        val nonProviderPlugin = mock<MediaPlugin<RokuMediaContent>>()
        plugins["roku"] = nonProviderPlugin

        val response = pluginResource.getChannelInfo("roku")

        assertEquals(Response.Status.OK.statusCode, response.status)
        val body = response.entity as ChannelListResponse
        assertTrue(body.channels.isEmpty())
    }

    // Helper interface for mocking a MediaPlugin that also implements ChannelInfoProvider
    private interface RokuPluginWithChannelInfo : MediaPlugin<RokuMediaContent>, ChannelInfoProvider
}
