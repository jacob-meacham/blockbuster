package com.blockbuster.health

import com.blockbuster.plugin.MediaPlugin
import com.blockbuster.plugin.PluginRegistry
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class MediaPluginHealthCheckTest {
    @Test
    fun `check returns healthy when plugins are loaded`() {
        // Given
        val mockPlugin = mock<MediaPlugin<*>>()
        whenever(mockPlugin.getPluginName()).thenReturn("roku")
        val plugins = mapOf("roku" to mockPlugin)

        val healthCheck = MediaPluginHealthCheck(PluginRegistry(plugins))

        // When
        val result = healthCheck.execute()

        // Then
        assertTrue(result.isHealthy)
        assertTrue(result.message.contains("1 plugin(s) loaded"))
        assertTrue(result.message.contains("roku"))
    }

    @Test
    fun `check returns healthy with multiple plugins`() {
        // Given
        val plugin1 = mock<MediaPlugin<*>>()
        whenever(plugin1.getPluginName()).thenReturn("roku")
        val plugin2 = mock<MediaPlugin<*>>()
        whenever(plugin2.getPluginName()).thenReturn("spotify")
        val plugins = mapOf("roku" to plugin1, "spotify" to plugin2)

        val healthCheck = MediaPluginHealthCheck(PluginRegistry(plugins))

        // When
        val result = healthCheck.execute()

        // Then
        assertTrue(result.isHealthy)
        assertTrue(result.message.contains("2 plugin(s) loaded"))
    }

    @Test
    fun `check returns unhealthy when no plugins are loaded`() {
        // Given
        val plugins = emptyMap<String, MediaPlugin<*>>()

        val healthCheck = MediaPluginHealthCheck(PluginRegistry(plugins))

        // When
        val result = healthCheck.execute()

        // Then
        assertFalse(result.isHealthy)
        assertTrue(result.message.contains("No media plugins are loaded"))
    }
}
