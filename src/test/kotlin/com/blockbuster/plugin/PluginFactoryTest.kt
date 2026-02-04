package com.blockbuster.plugin

import com.blockbuster.PluginDefinition
import com.blockbuster.media.MediaStore
import com.blockbuster.media.SqliteMediaStore
import okhttp3.OkHttpClient
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.sqlite.SQLiteDataSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PluginFactoryTest {
    private lateinit var pluginFactory: PluginFactory
    private lateinit var httpClient: OkHttpClient
    private lateinit var mediaStore: MediaStore
    private lateinit var dataSource: SQLiteDataSource

    @BeforeEach
    fun setUp() {
        // Create in-memory database-backed store
        dataSource =
            SQLiteDataSource().apply {
                url = "jdbc:sqlite:file:testdb?mode=memory&cache=shared"
                setEnforceForeignKeys(true)
            }
        mediaStore = SqliteMediaStore(dataSource)
        httpClient = OkHttpClient()
        pluginFactory = PluginFactory(mediaStore, httpClient, dataSource = dataSource)
    }

    @AfterEach
    fun tearDown() {
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
    }

    @Test
    fun `should create Roku plugin from configuration`() {
        // Given
        val pluginDefinition =
            PluginDefinition(
                type = "roku",
                config =
                    mapOf(
                        "deviceIp" to "192.168.1.100",
                        "deviceName" to "Test Roku",
                    ),
            )

        // When
        val plugin = pluginFactory.createPlugin(pluginDefinition)

        // Then
        assertNotNull(plugin)
        assertEquals("roku", plugin.getPluginName())
        assertEquals("Test Roku - Controls Roku devices via ECP protocol", plugin.getDescription())
    }

    @Test
    fun `should throw exception for unknown plugin type`() {
        // Given
        val pluginDefinition =
            PluginDefinition(
                type = "unknown",
                config = emptyMap(),
            )

        // When/Then
        val exception =
            assertThrows<IllegalArgumentException> {
                pluginFactory.createPlugin(pluginDefinition)
            }
        assertTrue(exception.message?.contains("Unknown plugin type") == true)
    }

    @Test
    fun `should throw exception when Roku plugin missing deviceIp`() {
        // Given
        val pluginDefinition =
            PluginDefinition(
                type = "roku",
                // Missing deviceIp
                config = mapOf("deviceName" to "Test Roku"),
            )

        // When/Then
        val exception =
            assertThrows<IllegalArgumentException> {
                pluginFactory.createPlugin(pluginDefinition)
            }
        assertTrue(exception.message?.contains("deviceIp") == true)
    }

    @Test
    fun `should create Roku plugin with default device name when not specified`() {
        // Given
        val pluginDefinition =
            PluginDefinition(
                type = "roku",
                // No deviceName
                config = mapOf("deviceIp" to "192.168.1.200"),
            )

        // When
        val plugin = pluginFactory.createPlugin(pluginDefinition)

        // Then
        assertNotNull(plugin)
        assertEquals("roku", plugin.getPluginName())
    }

    @Test
    fun `should create multiple plugins of same type with different configs`() {
        // Given
        val pluginDef1 =
            PluginDefinition(
                type = "roku",
                config = mapOf("deviceIp" to "192.168.1.100", "deviceName" to "Living Room"),
            )

        val pluginDef2 =
            PluginDefinition(
                type = "roku",
                config = mapOf("deviceIp" to "192.168.1.101", "deviceName" to "Bedroom"),
            )

        // When
        val plugin1 = pluginFactory.createPlugin(pluginDef1)
        val plugin2 = pluginFactory.createPlugin(pluginDef2)

        // Then
        assertNotNull(plugin1)
        assertNotNull(plugin2)
        assertEquals("roku", plugin1.getPluginName())
        assertEquals("roku", plugin2.getPluginName())
        assertNotEquals(plugin1, plugin2) // Different instances
    }

    // ── Spotify plugin factory tests ────────────────────────────────────────

    @Test
    fun `should create Spotify plugin from configuration`() {
        val pluginDefinition =
            PluginDefinition(
                type = "spotify",
                config =
                    mapOf(
                        "clientId" to "spotify-client-id",
                        "clientSecret" to "spotify-client-secret",
                    ),
            )

        val plugin = pluginFactory.createPlugin(pluginDefinition)

        assertNotNull(plugin)
        assertEquals("spotify", plugin.getPluginName())
        assertTrue(plugin.getDescription().contains("Spotify"))
    }

    @Test
    fun `should throw when Spotify plugin missing clientId`() {
        val pluginDefinition =
            PluginDefinition(
                type = "spotify",
                config = mapOf("clientSecret" to "secret"),
            )

        val exception =
            assertThrows<IllegalArgumentException> {
                pluginFactory.createPlugin(pluginDefinition)
            }
        assertTrue(exception.message?.contains("clientId") == true)
    }

    @Test
    fun `should throw when Spotify plugin missing clientSecret`() {
        val pluginDefinition =
            PluginDefinition(
                type = "spotify",
                config = mapOf("clientId" to "id"),
            )

        val exception =
            assertThrows<IllegalArgumentException> {
                pluginFactory.createPlugin(pluginDefinition)
            }
        assertTrue(exception.message?.contains("clientSecret") == true)
    }

    @Test
    fun `should throw when Spotify plugin has no dataSource`() {
        val factoryWithoutDs = PluginFactory(mediaStore, httpClient, dataSource = null)

        val pluginDefinition =
            PluginDefinition(
                type = "spotify",
                config =
                    mapOf(
                        "clientId" to "id",
                        "clientSecret" to "secret",
                    ),
            )

        val exception =
            assertThrows<IllegalArgumentException> {
                factoryWithoutDs.createPlugin(pluginDefinition)
            }
        assertTrue(exception.message?.contains("dataSource") == true)
    }

    @Test
    fun `Spotify plugin should implement AuthenticablePlugin`() {
        val pluginDefinition =
            PluginDefinition(
                type = "spotify",
                config =
                    mapOf(
                        "clientId" to "id",
                        "clientSecret" to "secret",
                    ),
            )

        val plugin = pluginFactory.createPlugin(pluginDefinition)

        assertTrue(plugin is AuthenticablePlugin)
    }
}
