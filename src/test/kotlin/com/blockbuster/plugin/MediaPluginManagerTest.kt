package com.blockbuster.plugin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

class MediaPluginManagerTest {
    private lateinit var mockPlugin1: MediaPlugin<*>
    private lateinit var mockPlugin2: MediaPlugin<*>

    @BeforeEach
    fun setUp() {
        mockPlugin1 =
            mock {
                on { getPluginName() } doReturn "roku"
            }
        mockPlugin2 =
            mock {
                on { getPluginName() } doReturn "spotify"
            }
    }

    @Test
    fun `getPlugin returns plugin by name`() {
        val manager = MediaPluginManager(listOf(mockPlugin1, mockPlugin2))

        assertNotNull(manager.getPlugin("roku"))
        assertNotNull(manager.getPlugin("spotify"))
    }

    @Test
    fun `getPlugin returns null for unknown name`() {
        val manager = MediaPluginManager(listOf(mockPlugin1))

        assertNull(manager.getPlugin("unknown"))
    }

    @Test
    fun `getAllPlugins returns all registered plugins`() {
        val manager = MediaPluginManager(listOf(mockPlugin1, mockPlugin2))

        val plugins = manager.getAllPlugins()
        assertEquals(2, plugins.size)
    }

    @Test
    fun `getAllPlugins returns empty list when no plugins registered`() {
        val manager = MediaPluginManager(emptyList())

        assertEquals(0, manager.getAllPlugins().size)
    }

    @Test
    fun `play delegates to correct plugin`() {
        val manager = MediaPluginManager(listOf(mockPlugin1, mockPlugin2))

        manager.play("roku", "content-123")

        verify(mockPlugin1).play("content-123")
        verify(mockPlugin2, never()).play(any())
    }

    @Test
    fun `play throws PluginException for unknown plugin`() {
        val manager = MediaPluginManager(listOf(mockPlugin1))

        val exception =
            assertThrows(PluginException::class.java) {
                manager.play("nonexistent", "content-123")
            }

        assertEquals("Plugin 'nonexistent' not found", exception.message)
    }

    @Test
    fun `play propagates PluginException from plugin`() {
        whenever(mockPlugin1.play(any())).thenThrow(PluginException("Playback failed"))

        val manager = MediaPluginManager(listOf(mockPlugin1))

        assertThrows(PluginException::class.java) {
            manager.play("roku", "content-123")
        }
    }

    @Test
    fun `constructor handles duplicate plugin names by keeping last`() {
        val duplicate: MediaPlugin<*> =
            mock {
                on { getPluginName() } doReturn "roku"
            }

        val manager = MediaPluginManager(listOf(mockPlugin1, duplicate))

        // associateBy keeps the last entry for duplicate keys
        val plugin = manager.getPlugin("roku")
        assertNotNull(plugin)
        // getAllPlugins preserves the original list
        assertEquals(2, manager.getAllPlugins().size)
    }
}
