package com.blockbuster

import io.dropwizard.core.Configuration
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull

class BlockbusterConfiguration : Configuration() {
    
    @Valid
    @NotNull
    @JsonProperty("database")
    var database: DatabaseConfiguration = DatabaseConfiguration()
    
    @JsonProperty("plugins")
    var plugins: PluginsConfiguration = PluginsConfiguration()
    
    @JsonProperty("server")
    var server: ServerConfiguration = ServerConfiguration()
}

class DatabaseConfiguration {
    @JsonProperty("type")
    var type: String = "sqlite"
    
    @JsonProperty("jdbcUrl")
    var jdbcUrl: String = "jdbc:sqlite:blockbuster.db"
    
    @JsonProperty("username")
    var username: String? = null
    
    @JsonProperty("password")
    var password: String? = null
}

class PluginsConfiguration {
    @JsonProperty("configFile")
    var configFile: String = "plugins.yml"

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

class ServerConfiguration {
    @JsonProperty("port")
    var port: Int = 8080
    
    @JsonProperty("adminPort")
    var adminPort: Int = 8081
}
