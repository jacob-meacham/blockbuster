package com.blockbuster.plugin

import com.blockbuster.media.RokuMediaStore
import com.blockbuster.media.SqliteRokuMediaStore
import okhttp3.OkHttpClient
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.sqlite.SQLiteDataSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PluginFactoryTest {

    private lateinit var pluginFactory: PluginFactory
    private lateinit var rokuMediaStore: RokuMediaStore
    private lateinit var httpClient: OkHttpClient

    @BeforeEach
    fun setUp() {
        // Create in-memory database for testing
        val dataSource = SQLiteDataSource().apply {
            url = "jdbc:sqlite:file:testdb?mode=memory&cache=shared"
            setEnforceForeignKeys(true)
        }

        rokuMediaStore = SqliteRokuMediaStore(dataSource)
        httpClient = OkHttpClient()
        pluginFactory = PluginFactory(rokuMediaStore, httpClient)
    }

    @AfterEach
    fun tearDown() {
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
    }

    @Test
    fun `should create Roku plugin from configuration`() {
        // Given
        val pluginDefinition = PluginDefinition(
            type = "roku",
            config = mapOf(
                "deviceIp" to "192.168.1.100",
                "deviceName" to "Test Roku"
            )
        )

        // When
        val plugin = pluginFactory.createPlugin(pluginDefinition)

        // Then
        assertNotNull(plugin)
        assertEquals("roku", plugin.getPluginName())
        assertEquals("Controls Roku devices via ECP protocol", plugin.getDescription())
    }

    @Test
    fun `should throw exception for unknown plugin type`() {
        // Given
        val pluginDefinition = PluginDefinition(
            type = "unknown",
            config = emptyMap()
        )

        // When/Then
        val exception = assertThrows<IllegalArgumentException> {
            pluginFactory.createPlugin(pluginDefinition)
        }
        assertTrue(exception.message?.contains("Unknown plugin type") == true)
    }

    @Test
    fun `should throw exception when Roku plugin missing deviceIp`() {
        // Given
        val pluginDefinition = PluginDefinition(
            type = "roku",
            config = mapOf("deviceName" to "Test Roku") // Missing deviceIp
        )

        // When/Then
        val exception = assertThrows<IllegalArgumentException> {
            pluginFactory.createPlugin(pluginDefinition)
        }
        assertTrue(exception.message?.contains("deviceIp") == true)
    }

    @Test
    fun `should create Roku plugin with default device name when not specified`() {
        // Given
        val pluginDefinition = PluginDefinition(
            type = "roku",
            config = mapOf("deviceIp" to "192.168.1.200") // No deviceName
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
        val pluginDef1 = PluginDefinition(
            type = "roku",
            config = mapOf("deviceIp" to "192.168.1.100", "deviceName" to "Living Room")
        )

        val pluginDef2 = PluginDefinition(
            type = "roku",
            config = mapOf("deviceIp" to "192.168.1.101", "deviceName" to "Bedroom")
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
}
