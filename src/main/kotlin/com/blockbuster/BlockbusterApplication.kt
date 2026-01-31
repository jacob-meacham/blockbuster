package com.blockbuster

import io.dropwizard.core.Application
import io.dropwizard.core.setup.Bootstrap
import io.dropwizard.core.setup.Environment
import com.blockbuster.db.FlywayManager
import com.blockbuster.plugin.MediaPluginManager
import com.blockbuster.plugin.PluginFactory
import com.blockbuster.resource.HealthResource
import com.blockbuster.resource.SearchResource
import com.blockbuster.resource.LibraryResource
import com.blockbuster.resource.StaticResource
import com.blockbuster.media.SqliteMediaStore
import okhttp3.OkHttpClient
import org.slf4j.LoggerFactory
import org.sqlite.SQLiteDataSource
import java.util.concurrent.TimeUnit

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
            println("🌐 Web interface: http://localhost:8080/")
            println("📱 NFC Tags will be accessible at: http://localhost:8080/play/{tag-id}")
            println("🔍 Search API: http://localhost:8080/search/{plugin}")
            println("⚙️  Admin interface available at: http://localhost:8080/admin")
            println("=".repeat(80))
            
            BlockbusterApplication().run(*args)
        }
    }

    override fun getName(): String = "blockbuster"

    override fun initialize(bootstrap: Bootstrap<BlockbusterConfiguration>) {
        // Add any bundles, commands, or configuration sources here
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
        val pluginFactory = PluginFactory(mediaStore, httpClient)

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

        // Register resources
        environment.jersey().register(StaticResource())
        environment.jersey().register(HealthResource(flywayManager))
        environment.jersey().register(SearchResource(pluginManager))
        environment.jersey().register(LibraryResource(pluginManager, mediaStore))

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

        println("✅ Blockbuster open for business!")
        println("🗄️  Database initialized: ${configuration.database.type}")
        println("🔗 JDBC URL: ${configuration.database.jdbcUrl}")
        println("🔌 Plugins loaded: ${plugins.map { it.getPluginName() }}")
    }
    // private fun setupMetrics(metrics: MetricRegistry) {
    //     val reporter = ConsoleReporter.forRegistry(metrics)
    //         .convertRatesTo(TimeUnit.SECONDS)
    //         .convertDurationsTo(TimeUnit.MILLISECONDS)
    //         .build()
    //     reporter.start(30, TimeUnit.SECONDS)
    // }
}
