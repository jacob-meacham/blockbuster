Build an NFC video and album library. The idea would be to create small (cassette tape sized) cartridges with an NFC tag in them + a QR code. When tapped or scanned, it would play a specific album from the appropriate place (most likely Spotify) or start a movie in the appropriate place via a Roku deeplink (if that is possible). Let's build this using Kotlin.

This assumes that we will need the Roku API as well as Spotify. This can also assume we're using home-assistant if that is necessary.

## Architecture Overview

### Core Design Principles
- **Simple NFC Tags**: Store URLs like `https://your-nfc-library.com/play/{tag-id}` for maximum compatibility
- **Plugin-Based System**: Extensible architecture for different media services
- **Stateless Operation**: No complex state management required
- **Fail-Fast**: System stops if plugins fail to start

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

## 📚 Documentation

### For Claude Agents
- **[CLAUDE.md](CLAUDE.md)** - Quick start guide for AI agents working on this project

### Engineering Standards
- **[Constitution](docs/agents/CONSTITUTION.md)** - Code quality standards and development principles (REQUIRED READING)

### Specifications
- **[Implementation Strategy](docs/specs/IMPLEMENTATION_STRATEGY.md)** - Overall plan and architecture approach
- **[System Specifications](docs/specs/SPECIFICATIONS.md)** - Deep technical specifications for each feature

### Roku Deep Linking
- **[Channel Deep Linking Discovery](docs/CHANNEL_DEEP_LINKING_DISCOVERY.md)** - Deep linking test results for all streaming services

## 🚀 Getting Started

### Starting the Application

**Development Mode** (faster iteration, no JAR rebuild):
```bash
./gradlew run -Pargs="server config.yml"
```

**Production Mode** (using packaged JAR):
```bash
./gradlew build
java -jar build/libs/blockbuster-1.0.0-SNAPSHOT.jar server config.yml
```

### Building the Frontend

When you modify the React frontend, rebuild and deploy it:
```bash
cd frontend
npm run build:deploy
cd ..
./gradlew build
```

The `build:deploy` script builds the frontend and copies it to `src/main/resources/assets/`.

### Accessing the Web Interface
- **Main Interface**: http://localhost:8080/
- **Search API**: http://localhost:8080/search/roku?q=matrix
- **Plugin List**: http://localhost:8080/search/plugins
- **Health Check**: http://localhost:8080/health

### Example API Usage
```bash
# Search for content
curl "http://localhost:8080/search/roku?q=matrix"

# Get available plugins
curl "http://localhost:8080/search/plugins"

# Health check
curl "http://localhost:8080/health"
```

### Configuration
Edit `config.yml` to modify:
- Database settings
- Plugin configurations
- Server ports

Example search queries:
- "matrix" - Returns The Matrix Reloaded
- "game" or "throne" - Returns Game of Thrones
- "rings" or "lord" - Returns Lord of the Rings
- "marvel" or "avengers" - Returns Avengers: Endgame

## 🔧 Plugin Architecture

### Flexible Search Results
The `SearchResult` class uses a flexible metadata system to accommodate different plugin requirements:

```kotlin
data class SearchResult(
    val contentId: String,           // Unique identifier for the content
    val title: String,               // Human-readable title
    val description: String? = null, // Optional description/summary
    val thumbnailUrl: String? = null,// Optional thumbnail/preview image
    val year: Int? = null,           // Optional release year
    val metadata: Map<String, Any> = emptyMap() // Plugin-specific additional data
)
```

### Plugin-Specific Metadata Examples

**Roku Plugin:**
```kotlin
metadata = mapOf(
    "channelName" to "Netflix",
    "channelId" to "12",
    "mediaType" to "movie"
)
```

**Spotify Plugin (hypothetical):**
```kotlin
metadata = mapOf(
    "artist" to "The Beatles",
    "album" to "Abbey Road",
    "duration" to 289,  // seconds
    "trackNumber" to 1
)
```

**Emby Plugin (hypothetical):**
```kotlin
metadata = mapOf(
    "serverId" to "emby-server-123",
    "libraryId" to "movies",
    "itemType" to "Movie"
)
```

This design allows each plugin to store its specific requirements in the metadata field while maintaining a consistent interface across all plugins.

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

