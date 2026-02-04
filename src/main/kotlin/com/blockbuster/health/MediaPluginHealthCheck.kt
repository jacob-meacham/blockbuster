package com.blockbuster.health

import com.blockbuster.plugin.PluginRegistry
import com.codahale.metrics.health.HealthCheck

/**
 * Dropwizard health check that verifies media plugins are loaded.
 *
 * Reports healthy when at least one plugin is registered.
 * Reports the count and names of loaded plugins in the health check message.
 */
class MediaPluginHealthCheck(private val plugins: PluginRegistry) : HealthCheck() {
    override fun check(): Result {
        if (plugins.isEmpty()) {
            return Result.unhealthy("No media plugins are loaded")
        }
        val pluginNames = plugins.keys.toList()
        return Result.healthy("${plugins.size} plugin(s) loaded: $pluginNames")
    }
}
