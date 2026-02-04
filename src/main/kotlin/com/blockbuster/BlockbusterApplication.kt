package com.blockbuster

import com.blockbuster.db.FlywayManager
import com.blockbuster.health.DatabaseHealthCheck
import com.blockbuster.health.MediaPluginHealthCheck
import com.blockbuster.media.SqliteMediaStore
import com.blockbuster.plugin.MediaPlugin
import com.blockbuster.plugin.PluginFactory
import com.blockbuster.plugin.PluginRegistry
import com.blockbuster.resource.AuthResource
import com.blockbuster.resource.FrontendResource
import com.blockbuster.resource.HealthResource
import com.blockbuster.resource.LibraryResource
import com.blockbuster.resource.PlayResource
import com.blockbuster.resource.PluginResource
import com.blockbuster.resource.SearchResource
import com.blockbuster.theater.DefaultTheaterHttpClient
import com.blockbuster.theater.TheaterDeviceManager
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.dropwizard.core.Application
import io.dropwizard.core.setup.Bootstrap
import io.dropwizard.core.setup.Environment
import okhttp3.OkHttpClient
import org.slf4j.LoggerFactory

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
        // Initialize database with HikariCP connection pool
        val hikariConfig =
            HikariConfig().apply {
                jdbcUrl = configuration.database.jdbcUrl
                maximumPoolSize = 5
                addDataSourceProperty("enforceForeignKeys", "true")
            }
        val dataSource = HikariDataSource(hikariConfig)

        val flywayManager = FlywayManager(dataSource, configuration.database.jdbcUrl)
        flywayManager.migrate()

        val mediaStore = SqliteMediaStore(dataSource)

        // Create HTTP client for plugins and theater
        val httpClient = OkHttpClient()

        // Create plugins from configuration
        val braveApiKey = if (configuration.braveSearch.enabled) configuration.braveSearch.apiKey else null
        val pluginFactory = PluginFactory(mediaStore, httpClient, braveApiKey, dataSource)
        val plugins = createPlugins(configuration, pluginFactory)

        // Build plugin registry
        val registry = PluginRegistry(plugins.associateBy { it.getPluginName() })
        logger.info("Loaded {} plugins: {}", registry.size, registry.keys)

        // Create theater device manager
        val theaterHttpClient = DefaultTheaterHttpClient(httpClient)
        val theaterDevices = configuration.appliances.mapValues { (_, appliance) -> appliance.theater }
        val theaterManager = TheaterDeviceManager(theaterDevices, theaterHttpClient)

        // Register health checks
        environment.healthChecks().register("database", DatabaseHealthCheck(dataSource))
        environment.healthChecks().register("plugins", MediaPluginHealthCheck(registry))

        // Register resources
        environment.jersey().register(HealthResource(flywayManager, dataSource))
        environment.jersey().register(SearchResource(registry))
        environment.jersey().register(LibraryResource(registry, mediaStore))
        environment.jersey().register(PlayResource(mediaStore, registry, theaterManager))
        environment.jersey().register(PluginResource(registry))
        environment.jersey().register(AuthResource(registry))
        environment.jersey().register(FrontendResource())

        // Register lifecycle shutdown hooks
        registerLifecycle(environment, dataSource, httpClient)
    }

    private fun createPlugins(
        configuration: BlockbusterConfiguration,
        pluginFactory: PluginFactory,
    ): List<MediaPlugin<*>> =
        configuration.plugins.map { pluginDef ->
            try {
                pluginFactory.createPlugin(pluginDef)
            } catch (e: Exception) {
                logger.error("Failed to create plugin of type '{}': {}", pluginDef.type, e.message)
                throw e
            }
        }

    private fun registerLifecycle(
        environment: Environment,
        dataSource: HikariDataSource,
        httpClient: OkHttpClient,
    ) {
        environment.lifecycle().manage(
            object : io.dropwizard.lifecycle.Managed {
                override fun start() {
                    // Everything is initialized above
                }

                override fun stop() {
                    dataSource.close()
                    httpClient.dispatcher.executorService.shutdown()
                    httpClient.connectionPool.evictAll()
                }
            },
        )
    }
}
