package com.blockbuster

import com.blockbuster.theater.TheaterDevice
import io.dropwizard.core.Configuration
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull

class BlockbusterConfiguration : Configuration() {

    @Valid
    @NotNull
    @JsonProperty("database")
    var database: DatabaseConfiguration = DatabaseConfiguration()

    @JsonProperty("baseUrl")
    var baseUrl: String = "http://localhost:8080"

    @JsonProperty("plugins")
    var plugins: PluginsConfiguration = PluginsConfiguration()

    @JsonProperty("braveSearch")
    var braveSearch: BraveSearchConfiguration = BraveSearchConfiguration()

    @JsonProperty("appliances")
    var appliances: Map<String, ApplianceConfig> = emptyMap()
}

class BraveSearchConfiguration {
    @JsonProperty("enabled")
    var enabled: Boolean = false

    @JsonProperty("apiKey")
    var apiKey: String? = null

}

class DatabaseConfiguration {
    @JsonProperty("type")
    var type: String = "sqlite"
    
    @JsonProperty("jdbcUrl")
    var jdbcUrl: String = "jdbc:sqlite:blockbuster.db"
    
}

class PluginsConfiguration {
    @JsonProperty("enabled")
    var enabled: List<PluginDefinition> = listOf(
        PluginDefinition("roku", mapOf(
            "deviceIp" to "192.168.1.100",
            "deviceName" to "Living Room Roku"
        ))
    )
}

class PluginDefinition {
    @JsonProperty("type")
    var type: String = ""

    @JsonProperty("config")
    var config: Map<String, Any> = emptyMap()

    // Default constructor for Jackson
    constructor()

    // Constructor for programmatic creation
    constructor(type: String, config: Map<String, Any> = emptyMap()) {
        this.type = type
        this.config = config
    }
}

/**
 * Configuration for an NFC appliance.
 *
 * @param theater The theater device associated with this appliance
 */
data class ApplianceConfig(
    @JsonProperty("theater")
    val theater: TheaterDevice
)
