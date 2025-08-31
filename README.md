Build an NFC video and album library. The idea would be to create small (cassette tape sized) cartridges with an NFC tag in them + a QR code. When tapped or scanned, it would play a specific album from the appropriate place (most likely Spotify) or start a movie in the appropriate place via a Roku deeplink (if that is possible). Let's build this using Kotlin.

This assumes that we will need the Roku API as well as Spotify. This can also assume we're using home-assistant if that is necessary.

## Architecture Overview

### Core Design Principles
- **Simple NFC Tags**: Store URLs like `https://your-nfc-library.com/play/{tag-id}` for maximum compatibility
- **Plugin-Based System**: Extensible architecture for different media services
- **Stateless Operation**: No complex state management required
- **Fail-Fast**: System stops if plugins fail to start

### System Components
1. ✅ **Web Service**: Handles NFC resolution and admin interface (Dropwizard + Jersey)
2. ✅ **Plugin System**: Simple `play(contentId, options)` interface with factory pattern
3. ✅ **Content Registry**: SQLite database mapping tags to content with Flyway migrations
4. ✅ **Configuration**: Single YAML file for all plugin settings

### Data Flow
```
NFC Tag (URL) → Web Service → Content Lookup → Plugin Call → Media Playback
```

### Plugin Interface
```kotlin
interface MediaPlugin {
    fun getPluginName(): String
    fun getDescription(): String

    @Throws(PluginException::class)
    fun play(contentId: String, options: Map<String, Any>)
}
```

### Database Schema
```sql
roku_media (
    uuid TEXT PRIMARY KEY,
    channel_id TEXT NOT NULL,
    ecp_command TEXT DEFAULT 'launch',
    content_id TEXT NOT NULL,
    media_type TEXT,
    title TEXT,
    created_at TIMESTAMP DEFAULT (datetime('now')),
    updated_at TIMESTAMP DEFAULT (datetime('now'))
)
```

## ✅ Completed Implementation

### Core System
- **Dropwizard Application**: RESTful web service with Jersey endpoints
- **Plugin Architecture**: Factory-based plugin system with YAML configuration
- **Database Integration**: SQLite with Flyway migrations and proper schema
- **Health Monitoring**: Basic health check endpoint for system status

### Roku Plugin
- **ECP Protocol**: Full Roku External Control Protocol implementation
- **Content Storage**: SQLite-based media content registry
- **Device Configuration**: Configurable device IP and naming
- **Error Handling**: Comprehensive exception handling with PluginException

### Configuration System
- **YAML Configuration**: Single config file for all settings
- **Plugin Management**: Dynamic plugin loading and configuration
- **Database Settings**: Configurable JDBC URLs and connection properties

### Testing
- **Unit Tests**: Comprehensive test coverage for all components
- **Plugin Factory Tests**: Plugin creation and configuration validation
- **Database Tests**: SQLite integration and migration testing
- **Integration Tests**: Full system integration testing

### 1.1 NFC Infrastructure
- [ ] **Phone-Based NFC**: Use NFC support on modern phones
- [ ] **Tag Programming**: NFC tag encoding and management via phone
- [ ] **Physical Design**: Cassette-style cartridge design (hardware handled separately)
- [ ] **Integration**: Optional integration with Home Assistant to start up a media mode

### 1.2 Media Service Integration
- ✅ **Roku Integration**: Full Roku ECP protocol implementation with device configuration
- [ ] **Streaming Services**: Additional services beyond Roku (Amazon Prime, Netflix, HBO Max, Apple TV+)
- [ ] **Local Media**: Emby integration for local media libraries

### 1.3 Web Service Architecture
- ✅ **REST API**: Dropwizard-based REST service with Jersey endpoints
- ✅ **Plugin System**: Extensible plugin architecture with factory pattern
- ✅ **Database Layer**: SQLite with Flyway migrations and content registry
- ✅ **Configuration**: YAML-based plugin and system configuration

### 1.4 User Experience
- ✅ **Health Endpoint**: System health monitoring with migration status
- [ ] **Tag Management UI**: Web interface for programming and organizing tags
- [ ] **Playback Controls**: Easy media control and queue management
- [ ] **History Tracking**: Log of played media and usage patterns
- [ ] **Multi-Room**: Support for different playback locations

### 1.6 Advanced NFC Features
- [ ] **Multi-Tag Support**: Combine multiple tags for complex actions
- [ ] **Conditional Playback**: Time, presence, or state-based media selection
- [ ] **Social Features**: Share playlists, track favorites
- [ ] **Analytics**: Usage patterns and recommendations

### 1.7 Performance & Reliability
- [ ] **Caching**: Implement smart caching for media metadata
- [ ] **Error Handling**: Robust error handling and recovery
- [ ] **Monitoring**: System health monitoring and alerting
- [ ] **Backup**: Automated backup and restore procedures


## Technical Requirements

### Hardware
- **NFC Readers**: USB NFC reader on modern iPhone
- **NFC Tags**: NTAG213/215/216 tags for media identification
- **3D Printer**: For cartridge manufacturing (optional)
- **Raspberry Pi**: Ensure adequate performance for media processing

### Software Dependencies
- ✅ **Web Framework**: Dropwizard 4.x with Jersey for REST endpoints
- ✅ **Database**: SQLite with Flyway migrations for schema management
- ✅ **Plugin System**: Custom plugin architecture with YAML configuration
- ✅ **Roku API**: External Control Protocol (ECP) implementation
- [ ] **Home Assistant**: Optional integration for home automation
- [ ] **Additional Media APIs**: Spotify, Emby, etc.

## Future Enhancements

### Advanced Features
- **Search Interface**: Real-time content discovery across all enabled plugins
- **Error Handling**: Comprehensive error pages with debugging information
- **NFC Helper Tools**: URL generation, QR codes, and programming instructions
- **Content Caching**: Smart caching for media metadata and search results
- **Multi-User Support**: User authentication and content isolation
- **Real-time Updates**: WebSocket support for live status updates
- **Analytics Dashboard**: Usage patterns and system health monitoring

