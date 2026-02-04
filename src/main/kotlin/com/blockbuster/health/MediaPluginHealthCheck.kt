package com.blockbuster.health

import com.blockbuster.plugin.MediaPluginManager
import com.codahale.metrics.health.HealthCheck

/**
 * Dropwizard health check that verifies media plugins are loaded.
 *
 * Reports healthy when at least one plugin is registered in the
 * [MediaPluginManager]. Reports the count and names of loaded plugins
 * in the health check message.
 *
 * @property pluginManager the plugin manager to check
 */
class MediaPluginHealthCheck(private val pluginManager: MediaPluginManager) : HealthCheck() {
    override fun check(): Result {
        val plugins = pluginManager.getAllPlugins()
        if (plugins.isEmpty()) {
            return Result.unhealthy("No media plugins are loaded")
        }
        val pluginNames = plugins.map { it.getPluginName() }
        return Result.healthy("${plugins.size} plugin(s) loaded: $pluginNames")
    }
}
