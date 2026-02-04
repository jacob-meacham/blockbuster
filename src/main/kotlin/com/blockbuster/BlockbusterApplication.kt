package com.blockbuster

import com.blockbuster.db.FlywayManager
import com.blockbuster.health.DatabaseHealthCheck
import com.blockbuster.health.MediaPluginHealthCheck
import com.blockbuster.media.SqliteMediaStore
import com.blockbuster.plugin.MediaPluginManager
import com.blockbuster.plugin.PluginFactory
import com.blockbuster.resource.FrontendResource
import com.blockbuster.resource.HealthResource
import com.blockbuster.resource.LibraryResource
import com.blockbuster.resource.PlayResource
import com.blockbuster.resource.SearchResource
import com.blockbuster.theater.DefaultTheaterHttpClient
import com.blockbuster.theater.TheaterDeviceManager
import com.blockbuster.theater.createTheaterHandler
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.dropwizard.core.Application
import io.dropwizard.core.server.DefaultServerFactory
import io.dropwizard.core.setup.Bootstrap
import io.dropwizard.core.setup.Environment
import io.dropwizard.jetty.HttpConnectorFactory
import okhttp3.OkHttpClient
import org.slf4j.LoggerFactory
import org.sqlite.SQLiteDataSource

class BlockbusterApplication : Application<BlockbusterConfiguration>() {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val MASTHEAD = """
        ╔══════════════════════════════════════════════════════════════════════════╗
        ║                                                                          ║
        ║    ██████╗ ██╗      ██████╗  ██████╗██╗  ██╗███████╗████████╗███████╗    ║
        ║    ██╔══██╗██║     ██╔═══██╗██╔════╝██║ ██╔╝██╔════╝╚══██╔══╝██╔════╝    ║
        ║    ██████╔╝██║     ██║   ██║██║     █████╔╝ █████╗     ██║   ███████╗    ║
        ║    ██╔══██╗██║     ██║   ██║██║     ██╔═██╗ ██╔══╝     ██║   ╚════██║    ║
        ║    ██████╔╝███████╗╚██████╔╝╚██████╗██║  ██╗███████╗   ██║   ███████║    ║
        ║    ╚═════╝ ╚══════╝ ╚═════╝  ╚═════╝╚═╝  ╚═╝╚══════╝   ╚═╝   ╚══════╝    ║
        ║                                                                          ║
        ║                        🎬 NFC Media Library System 🎵                    ║
        ║                                                                          ║
        ║                    Bringing Physical Media to the Digital Age            ║
        ║                                                                          ║
        ╚══════════════════════════════════════════════════════════════════════════╝
        """

        private val startupLogger = LoggerFactory.getLogger(BlockbusterApplication::class.java)

        @JvmStatic
        fun main(args: Array<String>) {
            startupLogger.info(MASTHEAD)
            startupLogger.info("Starting NFC Library System...")

            BlockbusterApplication().run(*args)
        }
    }

    override fun getName(): String = "blockbuster"

    override fun initialize(bootstrap: Bootstrap<BlockbusterConfiguration>) {
        bootstrap.objectMapper.registerModule(KotlinModule.Builder().build())
    }

    override fun run(
        configuration: BlockbusterConfiguration,
        environment: Environment,
    ) {
        // Initialize database with Flyway
        val dataSource =
            SQLiteDataSource().apply {
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
        val plugins =
            configuration.plugins.map { pluginDef ->
                try {
                    pluginFactory.createPlugin(pluginDef)
                } catch (e: Exception) {
                    logger.error("Failed to create plugin of type '{}': {}", pluginDef.type, e.message)
                    throw e
                }
            }

        // Create plugin manager
        val pluginManager = MediaPluginManager(plugins)

        // Create theater device manager
        val theaterHttpClient = DefaultTheaterHttpClient(java.net.http.HttpClient.newHttpClient())
        val theaterHandlers =
            configuration.appliances.mapValues { (_, appliance) ->
                createTheaterHandler(appliance.theater, theaterHttpClient)
            }
        val theaterManager = TheaterDeviceManager(theaterHandlers)

        // Register health checks
        environment.healthChecks().register("database", DatabaseHealthCheck(dataSource))
        environment.healthChecks().register("plugins", MediaPluginHealthCheck(pluginManager))

        // Register resources
        environment.jersey().register(HealthResource(flywayManager, dataSource))
        environment.jersey().register(SearchResource(pluginManager))
        environment.jersey().register(LibraryResource(pluginManager, mediaStore))
        environment.jersey().register(PlayResource(mediaStore, pluginManager, theaterManager))
        environment.jersey().register(FrontendResource())

        // Register managed objects for lifecycle management
        environment.lifecycle().manage(
            object : io.dropwizard.lifecycle.Managed {
                override fun start() {
                    // Everything is initialized above
                }

                override fun stop() {
                    // Close HTTP client
                    httpClient.dispatcher.executorService.shutdown()
                    httpClient.connectionPool.evictAll()
                }
            },
        )

        // Extract port configuration for display
        val serverFactory = configuration.serverFactory
        val (appPort, adminPort) =
            if (serverFactory is DefaultServerFactory) {
                val applicationPort = (serverFactory.applicationConnectors.firstOrNull() as? HttpConnectorFactory)?.port ?: 8080
                val adminPortValue = (serverFactory.adminConnectors.firstOrNull() as? HttpConnectorFactory)?.port ?: 8081
                applicationPort to adminPortValue
            } else {
                8080 to 8081
            }

        logger.info("Blockbuster open for business!")
        logger.info("Database initialized: {}", configuration.database.type)
        logger.info("JDBC URL: {}", configuration.database.jdbcUrl)
        logger.info("Plugins loaded: {}", plugins.map { it.getPluginName() })
        logger.info("Web interface: http://localhost:{}/", appPort)
        logger.info("NFC Tags: http://localhost:{}/play/{{tag-id}}", appPort)
        logger.info("Search API: http://localhost:{}/search/{{plugin}}", appPort)
        logger.info("Admin interface: http://localhost:{}/", adminPort)
    }
}
