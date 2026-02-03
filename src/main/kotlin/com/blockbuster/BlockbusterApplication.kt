package com.blockbuster

import io.dropwizard.core.Application
import io.dropwizard.core.setup.Bootstrap
import io.dropwizard.core.setup.Environment
import io.dropwizard.core.server.DefaultServerFactory
import io.dropwizard.jetty.HttpConnectorFactory
import com.blockbuster.db.FlywayManager
import com.blockbuster.plugin.MediaPluginManager
import com.blockbuster.plugin.PluginFactory
import com.blockbuster.resource.HealthResource
import com.blockbuster.resource.SearchResource
import com.blockbuster.resource.LibraryResource
import com.blockbuster.resource.StaticResource
import com.blockbuster.resource.PlayResource
import com.blockbuster.media.SqliteMediaStore
import com.blockbuster.theater.TheaterDeviceManager
import com.fasterxml.jackson.module.kotlin.KotlinModule
import okhttp3.OkHttpClient
import org.slf4j.LoggerFactory
import org.sqlite.SQLiteDataSource

class BlockbusterApplication : Application<BlockbusterConfiguration>() {

    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val MASTHEAD = """
        ╔══════════════════════════════════════════════════════════════════════════════╗
        ║                                                                              ║
        ║    ██████╗ ██╗      ██████╗  ██████╗██╗  ██╗███████╗████████╗███████╗    ║
        ║    ██╔══██╗██║     ██╔═══██╗██╔════╝██║ ██╔╝██╔════╝╚══██╔══╝██╔════╝    ║
        ║    ██████╔╝██║     ██║   ██║██║     █████╔╝ █████╗     ██║   ███████╗    ║
        ║    ██╔══██╗██║     ██║   ██║██║     ██╔═██╗ ██╔══╝     ██║   ╚════██║    ║
        ║    ██████╔╝███████╗╚██████╔╝╚██████╗██║  ██╗███████╗   ██║   ███████║    ║
        ║    ╚═════╝ ╚══════╝ ╚═════╝  ╚═════╝╚═╝  ╚═╝╚══════╝   ╚═╝   ╚══════╝    ║
        ║                                                                              ║
        ║                        🎬 NFC Media Library System 🎵                        ║
        ║                                                                              ║
        ║                    Bringing Physical Media to the Digital Age                 ║
        ║                                                                              ║
        ╚══════════════════════════════════════════════════════════════════════════════╝
        """

        @JvmStatic
        fun main(args: Array<String>) {
            println(MASTHEAD)
            println("🚀 Starting NFC Library System...")
            println("=".repeat(80))

            BlockbusterApplication().run(*args)
        }
    }

    override fun getName(): String = "blockbuster"

    override fun initialize(bootstrap: Bootstrap<BlockbusterConfiguration>) {
        bootstrap.objectMapper.registerModule(KotlinModule.Builder().build())
    }

    override fun run(configuration: BlockbusterConfiguration, environment: Environment) {
        // Initialize database with Flyway
        val dataSource = SQLiteDataSource().apply {
            url = configuration.database.jdbcUrl
            setEnforceForeignKeys(true)
        }

        val flywayManager = FlywayManager(dataSource, configuration.database.jdbcUrl)
        flywayManager.migrate()

        val mediaStore = SqliteMediaStore(dataSource)

        // Create HTTP client for plugins
        val httpClient = okhttp3.OkHttpClient()

        // Create plugin factory
        val braveApiKey = if (configuration.braveSearch.enabled) configuration.braveSearch.apiKey else null
        val pluginFactory = PluginFactory(mediaStore, httpClient, braveApiKey)

        // Create plugins from configuration
        val plugins = configuration.plugins.enabled.map { pluginDef ->
            try {
                val pluginDefinition = com.blockbuster.plugin.PluginDefinition(
                    type = pluginDef.type,
                    config = pluginDef.config
                )
                pluginFactory.createPlugin(pluginDefinition)
            } catch (e: Exception) {
                logger.error("Failed to create plugin of type '${pluginDef.type}': ${e.message}")
                throw e
            }
        }

        // Create plugin manager
        val pluginManager = MediaPluginManager(plugins)

        // Create theater device manager
        val theaterManager = TheaterDeviceManager(configuration)

        // Register resources
        environment.jersey().register(StaticResource())
        environment.jersey().register(HealthResource(flywayManager))
        environment.jersey().register(SearchResource(pluginManager))
        environment.jersey().register(LibraryResource(pluginManager, mediaStore, configuration.baseUrl))
        environment.jersey().register(PlayResource(mediaStore, pluginManager, theaterManager))

        // Register managed objects for lifecycle management
        environment.lifecycle().manage(object : io.dropwizard.lifecycle.Managed {
            override fun start() {
                // Everything is initialized above
            }

            override fun stop() {
                // Close HTTP client
                httpClient.dispatcher.executorService.shutdown()
                httpClient.connectionPool.evictAll()
            }
        })

        // Extract port configuration for display
        val serverFactory = configuration.serverFactory
        val (appPort, adminPort) = if (serverFactory is DefaultServerFactory) {
            val applicationPort = (serverFactory.applicationConnectors.firstOrNull() as? HttpConnectorFactory)?.port ?: 8080
            val adminPortValue = (serverFactory.adminConnectors.firstOrNull() as? HttpConnectorFactory)?.port ?: 8081
            applicationPort to adminPortValue
        } else {
            8080 to 8081
        }

        println("✅ Blockbuster open for business!")
        println("🗄️  Database initialized: ${configuration.database.type}")
        println("🔗 JDBC URL: ${configuration.database.jdbcUrl}")
        println("🔌 Plugins loaded: ${plugins.map { it.getPluginName() }}")
        println("=".repeat(80))
        println("🌐 Web interface: http://localhost:$appPort/")
        println("📱 NFC Tags: http://localhost:$appPort/play/{tag-id}")
        println("🔍 Search API: http://localhost:$appPort/search/{plugin}")
        println("⚙️  Admin interface: http://localhost:$adminPort/")
        println("=".repeat(80))
    }
}
