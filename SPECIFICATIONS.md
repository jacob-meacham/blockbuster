# Blockbuster: Technical Specifications

## Executive Summary

Blockbuster is an NFC-powered media library system that bridges physical media nostalgia with modern streaming services. Users tap cassette-sized NFC cartridges to instantly play content across multiple media platforms. The system employs a plugin architecture to support diverse media services with varying control capabilities.

## Core Architecture

### System Philosophy

**Plugin-Agnostic Core**
- Zero assumptions about media service capabilities
- Each plugin declares its own control surface
- Unified storage layer with flexible JSON schemas
- Type-safe boundaries between plugins and core

**NFC as the Universal Remote**
- UUID-based content addressing
- Single tap → media playback
- No app switching, no search, no friction
- Physical library metaphor for digital content

### Technology Stack

#### Backend
- **Framework**: Dropwizard 4.0.16 (Jersey REST + Jetty)
- **Language**: Kotlin 1.9.21 (100% Kotlin codebase)
- **Database**: SQLite 3.44 with Flyway migrations
- **HTTP Client**: OkHttp 4.12.0
- **JSON**: Jackson (Dropwizard default)
- **Logging**: SLF4J + Logback

#### Frontend
- **Framework**: React 18 + TypeScript 5
- **Build Tool**: Vite 5
- **UI Library**: Material-UI v5
- **State**: React hooks (no Redux - keep it simple)

#### Testing
- **Framework**: JUnit 5
- **Mocking**: Mockito
- **Coverage Target**: 80%+ for core logic, 100% for plugin interfaces

---

## Plugin Architecture

### Design Principles

1. **Type Safety Over Runtime Checks**
   - Generic types enforce contracts at compile time
   - `MediaPlugin<C : MediaContent>` ensures type consistency
   - No casting, no `Any` types in plugin boundaries

2. **Capability-Based Design**
   - Plugins declare what they can do, not what they can't
   - Some plugins support search (Emby), others don't (Roku with action sequences)
   - Some plugins support deep linking (Emby), others use action macros (Roku)

3. **Configuration as Data**
   - YAML configuration for all plugin settings
   - No hardcoded credentials or endpoints
   - Environment-specific overrides supported

4. **Separation of Concerns**
   - **Media Plugins**: Handle content search and playback control
   - **Theater Plugins**: Handle theater/room setup (power, input switching, etc.)
   - Clean boundaries allow independent evolution

### Media Plugin Interface

```kotlin
interface MediaPlugin<C : MediaContent> {
    // Identity
    fun getPluginName(): String
    fun getDescription(): String

    // Core Capability: Playback
    fun play(contentId: String, options: Map<String, Any>)

    // Optional Capability: Search
    fun search(query: String, options: Map<String, Any> = emptyMap()): List<SearchResult<C>>

    // Type System Integration
    fun getContentParser(): MediaContentParser<C>
}
```

### Theater Plugin Interface

Theater plugins handle the physical setup of the viewing environment before media playback begins. This includes powering on equipment, switching inputs, adjusting lighting, and ensuring the theater is ready.

```kotlin
interface TheaterPlugin {
    // Identity
    fun getPluginName(): String
    fun getDescription(): String

    /**
     * Initiates theater setup sequence.
     * This method should be non-blocking and return immediately.
     *
     * Examples:
     * - Power on TV, receiver, and other equipment
     * - Switch receiver input to correct source
     * - Adjust lighting/shades
     * - Set volume to default level
     */
    suspend fun setup()

    /**
     * Checks if theater is ready for playback.
     * Blocks until theater setup is complete or timeout occurs.
     *
     * @param timeoutMs Maximum time to wait in milliseconds
     * @return true if theater is ready, false if timeout or setup failed
     */
    suspend fun isReady(timeoutMs: Long = 30000): Boolean

    /**
     * Optional: Get current theater state for diagnostics
     */
    fun getState(): TheaterState
}

data class TheaterState(
    val isReady: Boolean,
    val devices: Map<String, DeviceState>,
    val lastSetup: Instant? = null,
    val errorMessage: String? = null
)

data class DeviceState(
    val deviceName: String,
    val isPoweredOn: Boolean,
    val currentInput: String? = null,
    val isResponding: Boolean = true
)
```

#### Theater Plugin Integration Flow

```
NFC Tap → Check Media Center ID → Theater Setup → Media Playback
                    ↓
    [1] Lookup theater plugin for media center
    [2] Call theater.setup()
    [3] Poll/wait theater.isReady()
    [4] Call mediaPlugin.play()
```

**Implementation in PlayResource**:

```kotlin
@Path("/play/{uuid}")
class PlayResource(
    private val mediaStore: MediaStore,
    private val pluginManager: MediaPluginManager,
    private val theaterManager: TheaterPluginManager
) {
    @POST
    suspend fun play(@PathParam("uuid") uuidStr: String): Response {
        val uuid = UUID.fromString(uuidStr)
        val mediaItem = mediaStore.get(uuid)
            ?: throw NotFoundException("Content not found: $uuidStr")

        // Check if content specifies a media center
        val mediaCenterId = mediaItem.getMediaCenterId()

        // Setup theater if needed
        if (mediaCenterId != null) {
            val theaterPlugin = theaterManager.getPluginForCenter(mediaCenterId)
            if (theaterPlugin != null) {
                logger.info("Setting up theater: {}", mediaCenterId)
                theaterPlugin.setup()

                val ready = theaterPlugin.isReady(timeoutMs = 30000)
                if (!ready) {
                    logger.warn("Theater setup timed out or failed")
                    // Continue anyway - playback might still work
                }
            }
        }

        // Play media
        val plugin = pluginManager.getPlugin(mediaItem.plugin)
        plugin.play(uuidStr, emptyMap())

        return Response.ok().build()
    }
}
```

### Example: Harmony Hub Theater Plugin

```kotlin
class HarmonyHubTheaterPlugin(
    private val harmonyHubIp: String,
    private val activityId: String, // "Watch Roku" activity
    private val httpClient: OkHttpClient
) : TheaterPlugin {

    private val logger = LoggerFactory.getLogger(HarmonyHubTheaterPlugin::class.java)
    private var setupStartTime: Instant? = null

    override fun getPluginName() = "harmony-hub"
    override fun getDescription() = "Logitech Harmony Hub theater control"

    override suspend fun setup() {
        setupStartTime = Instant.now()

        logger.info("Starting Harmony activity: {}", activityId)

        // Harmony Hub WebSocket API call to start activity
        val request = Request.Builder()
            .url("http://$harmonyHubIp:8088/")
            .post(buildHarmonyActivityRequest(activityId).toRequestBody())
            .build()

        withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    logger.error("Failed to start Harmony activity: {}", response.code)
                }
            }
        }
    }

    override suspend fun isReady(timeoutMs: Long): Boolean {
        val deadline = Instant.now().plusMillis(timeoutMs)

        while (Instant.now().isBefore(deadline)) {
            val state = getCurrentActivity()

            if (state?.currentActivity == activityId) {
                logger.info("Harmony activity ready: {}", activityId)
                return true
            }

            delay(1000) // Poll every second
        }

        logger.warn("Harmony activity not ready after {}ms", timeoutMs)
        return false
    }

    override fun getState(): TheaterState {
        val currentActivity = getCurrentActivity()

        return TheaterState(
            isReady = currentActivity?.currentActivity == activityId,
            devices = currentActivity?.devices ?: emptyMap(),
            lastSetup = setupStartTime,
            errorMessage = currentActivity?.errorMessage
        )
    }

    private fun getCurrentActivity(): HarmonyStatus? {
        val request = Request.Builder()
            .url("http://$harmonyHubIp:8088/getCurrentActivity")
            .get()
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    // Parse Harmony response
                    parseHarmonyStatus(response.body?.string())
                } else {
                    null
                }
            }
        } catch (e: IOException) {
            logger.error("Failed to get Harmony status", e)
            null
        }
    }

    private fun buildHarmonyActivityRequest(activityId: String): String {
        // Harmony Hub JSON-RPC request format
        return """
        {
            "hubId": "0",
            "timeout": 30,
            "hbus": {
                "cmd": "harmony.activityengine?runactivity",
                "params": {
                    "async": "false",
                    "timestamp": 0,
                    "args": {
                        "rule": "start"
                    },
                    "activityId": "$activityId"
                }
            }
        }
        """.trimIndent()
    }

    private data class HarmonyStatus(
        val currentActivity: String,
        val devices: Map<String, DeviceState>,
        val errorMessage: String? = null
    )
}
```

### Content Model

Each plugin defines its own `MediaContent` implementation:

```kotlin
interface MediaContent {
    fun toJson(): String
}

// Roku: Action sequence-based control
data class RokuMediaContent(
    val channelId: String,
    val contentId: String,
    val ecpCommand: String = "launch",
    val channelName: String? = null,
    val mediaType: String? = null,
    val title: String? = null,
    // Future: action sequences for complex navigation
    val actionSequence: List<RokuAction>? = null
) : MediaContent

// Emby: Deep link-based control
data class EmbyMediaContent(
    val serverId: String,
    val itemId: String,
    val itemType: String,  // Movie, Episode, Season, etc.
    val title: String,
    val deepLink: String,  // Full deep link URL
    val resumePosition: Long? = null,  // Resume playback position
    val libraryId: String? = null,
    val mediaCenterId: String? = null  // Optional theater/media center reference
) : MediaContent
```

---

## Media Service Integration

### Roku Plugin: Action Sequence Model

**Current State**: Basic ECP launch commands

**Limitation**: Roku's ECP doesn't support true deep linking for all channels. Many channels don't accept `contentId` parameters or require navigation through menus.

**Solution**: Action Sequence Model

```kotlin
sealed class RokuAction {
    data class Press(val key: RokuKey) : RokuAction()
    data class Wait(val milliseconds: Long) : RokuAction()
    data class Launch(val channelId: String) : RokuAction()
    data class Type(val text: String) : RokuAction()
}

enum class RokuKey {
    HOME, UP, DOWN, LEFT, RIGHT, SELECT, BACK,
    PLAY, PAUSE, REWIND, FAST_FORWARD,
    SEARCH, INFO
}

// Example: Netflix "Stranger Things"
val netflixSequence = listOf(
    RokuAction.Launch("12"),          // Launch Netflix
    RokuAction.Wait(3000),            // Wait for Netflix to load
    RokuAction.Press(RokuKey.SEARCH), // Open search
    RokuAction.Type("Stranger Things"),
    RokuAction.Press(RokuKey.SELECT),
    RokuAction.Wait(1000),
    RokuAction.Press(RokuKey.SELECT)  // Play first result
)
```

**Recording Interface**: Capture user's manual navigation, convert to replayable sequence

**Optimization**: Learn optimal wait times, detect channel load completion

### Emby Plugin: Deep Link Model

**Capability**: Full API control with proper deep linking

**Advantages**:
- Direct item access via item ID
- Resume position support
- Rich metadata (posters, descriptions, cast)
- Real-time availability checking
- User library integration

**Implementation Priority**: HIGH (implement before refining Roku action sequences)

```kotlin
class EmbyPlugin(
    private val serverUrl: String,
    private val apiKey: String,
    private val userId: String,
    private val mediaStore: MediaStore,
    private val httpClient: OkHttpClient
) : MediaPlugin<EmbyMediaContent> {

    override fun play(contentId: String, options: Map<String, Any>) {
        // Retrieve content from media store
        val content = mediaStore.getParsed<EmbyMediaContent>(
            UUID.fromString(contentId),
            "emby",
            EmbyMediaContent.parser()
        ) ?: throw NotFoundException("Content not found: $contentId")

        // Option 1: HTTP POST to Emby play endpoint
        val playUrl = "$serverUrl/Sessions/{sessionId}/Playing"
        val request = Request.Builder()
            .url(playUrl)
            .header("X-Emby-Token", apiKey)
            .post(/* JSON body with itemId, position, etc. */)
            .build()

        httpClient.newCall(request).execute()

        // Option 2: Generate deep link for cast/external player
        // emby://play?itemId={itemId}&position={position}
    }

    override fun search(query: String, options: Map<String, Any>): List<SearchResult<EmbyMediaContent>> {
        val searchUrl = "$serverUrl/Users/$userId/Items"
        val request = Request.Builder()
            .url(searchUrl.toHttpUrl().newBuilder()
                .addQueryParameter("searchTerm", query)
                .addQueryParameter("recursive", "true")
                .addQueryParameter("fields", "Overview,Path")
                .build()
            )
            .header("X-Emby-Token", apiKey)
            .get()
            .build()

        val response = httpClient.newCall(request).execute()
        val items = /* parse JSON response */

        return items.map { item ->
            SearchResult(
                title = item.name,
                url = "$serverUrl/web/index.html#!/details?id=${item.id}",
                mediaUrl = item.path,
                content = EmbyMediaContent(
                    serverId = item.serverId,
                    itemId = item.id,
                    itemType = item.type,
                    title = item.name,
                    deepLink = "emby://play?itemId=${item.id}",
                    libraryId = item.parentId
                )
            )
        }
    }
}
```

---

## Data Model

### Media Library Schema

```sql
CREATE TABLE media_library (
    uuid TEXT PRIMARY KEY,              -- NFC tag identifier
    plugin TEXT NOT NULL,               -- Plugin name (roku, emby, spotify)
    config_json TEXT NOT NULL,          -- Plugin-specific JSON
    created_at TIMESTAMP DEFAULT (datetime('now')),
    updated_at TIMESTAMP DEFAULT (datetime('now'))
);

CREATE INDEX idx_library_plugin ON media_library(plugin);
```

**Design Rationale**:
- **Plugin-agnostic**: Single table for all media types
- **Flexible schema**: JSON allows plugin-specific fields without migrations
- **Queryable metadata**: Plugin and timestamps indexed for filtering
- **UUID addressing**: Globally unique identifiers for NFC tags

**Trade-offs**:
- ✅ Easy to add new plugins
- ✅ No schema migrations for plugin changes
- ✅ Simple backup/restore (single table)
- ❌ Can't query plugin-specific fields with SQL
- ❌ JSON parsing overhead for every read

**Mitigation**: For heavy query use cases, add plugin-specific materialized views

---

## REST API Specification

### Search Endpoints

#### List Available Plugins
```http
GET /search/plugins

Response 200:
{
  "plugins": [
    {
      "name": "roku",
      "description": "Roku streaming devices via ECP"
    },
    {
      "name": "emby",
      "description": "Emby media server"
    }
  ]
}
```

#### Search Media
```http
GET /search/{pluginName}?q={query}&limit={limit}

Parameters:
  - pluginName: string (required) - Plugin to search with
  - q: string (required) - Search query
  - limit: integer (optional, default=10) - Max results

Response 200:
{
  "plugin": "emby",
  "query": "inception",
  "totalResults": 1,
  "results": [
    {
      "title": "Inception",
      "url": "https://emby.local/web/index.html#!/details?id=12345",
      "mediaUrl": "/media/movies/Inception.mkv",
      "content": {
        "serverId": "emby-main",
        "itemId": "12345",
        "itemType": "Movie",
        "title": "Inception",
        "deepLink": "emby://play?itemId=12345",
        "libraryId": "movies"
      }
    }
  ]
}

Response 404:
{
  "error": "Plugin not found: unknown-plugin"
}
```

### Library Endpoints

#### List Library Items
```http
GET /library?page={page}&pageSize={pageSize}&plugin={plugin}

Parameters:
  - page: integer (optional, default=1) - Page number
  - pageSize: integer (optional, default=25) - Items per page
  - plugin: string (optional) - Filter by plugin

Response 200:
{
  "items": [
    {
      "uuid": "550e8400-e29b-41d4-a716-446655440000",
      "plugin": "emby",
      "configJson": "{\"serverId\":\"emby-main\",...}",
      "updatedAt": "2024-08-31T22:52:00Z"
    }
  ],
  "page": 1,
  "pageSize": 25,
  "total": 42
}
```

#### Add to Library
```http
POST /library/{pluginName}

Body:
{
  "content": {
    "serverId": "emby-main",
    "itemId": "12345",
    "itemType": "Movie",
    "title": "Inception",
    "deepLink": "emby://play?itemId=12345"
  },
  "uuid": "550e8400-e29b-41d4-a716-446655440000"  // optional
}

Response 200:
{
  "uuid": "550e8400-e29b-41d4-a716-446655440000"
}

Response 400:
{
  "error": "Invalid content format"
}
```

#### Play Content
```http
POST /play/{uuid}

Response 200:
{
  "status": "playing",
  "uuid": "550e8400-e29b-41d4-a716-446655440000"
}

Response 404:
{
  "error": "Content not found"
}
```

---

## Configuration

### Application Configuration (config.yml)

```yaml
server:
  applicationConnectors:
    - type: http
      port: 8080
  adminConnectors:
    - type: http
      port: 8081

database:
  path: blockbuster.db
  migrationPath: db/migration

plugins:
  media:
    - type: roku
      config:
        deviceIp: 192.168.1.100
        deviceName: Living Room Roku
        timeout: 5000

    - type: emby
      config:
        serverUrl: https://emby.local
        apiKey: ${EMBY_API_KEY}  # Environment variable
        userId: ${EMBY_USER_ID}
        timeout: 10000

    - type: spotify
      config:
        clientId: ${SPOTIFY_CLIENT_ID}
        clientSecret: ${SPOTIFY_CLIENT_SECRET}
        redirectUri: http://localhost:8080/callback/spotify

  theater:
    - id: living-room-theater
      type: harmony-hub
      config:
        hubIp: 192.168.1.50
        activityId: "12345678"  # "Watch Roku" activity
        activityName: Watch Roku
        timeout: 30000

    - id: bedroom-theater
      type: harmony-hub
      config:
        hubIp: 192.168.1.51
        activityId: "87654321"
        activityName: Watch TV
        timeout: 30000

logging:
  level: INFO
  loggers:
    com.blockbuster: DEBUG
    org.eclipse.jetty: WARN
```

---

## Development Workflow

### Project Structure

```
blockbuster/
├── src/main/kotlin/com/blockbuster/
│   ├── BlockbusterApplication.kt       # Main entry, Dropwizard setup
│   ├── BlockbusterConfiguration.kt     # Config classes
│   ├── db/
│   │   └── FlywayManager.kt           # Migration management
│   ├── media/
│   │   ├── MediaContent.kt            # Content interface
│   │   ├── MediaStore.kt              # Storage interface
│   │   ├── SqliteMediaStore.kt        # SQLite implementation
│   │   ├── RokuMediaContent.kt        # Roku content model
│   │   └── EmbyMediaContent.kt        # Emby content model
│   ├── plugin/
│   │   ├── MediaPlugin.kt             # Media plugin interface
│   │   ├── MediaPluginManager.kt      # Media plugin registry
│   │   ├── PluginFactory.kt           # Plugin creation
│   │   ├── RokuPlugin.kt              # Roku implementation
│   │   ├── EmbyPlugin.kt              # Emby implementation
│   │   ├── TheaterPlugin.kt           # Theater plugin interface
│   │   ├── TheaterPluginManager.kt    # Theater plugin registry
│   │   └── HarmonyHubTheaterPlugin.kt # Harmony Hub implementation
│   └── resource/
│       ├── HealthResource.kt          # Health checks
│       ├── LibraryResource.kt         # Library CRUD
│       ├── SearchResource.kt          # Search API
│       ├── PlayResource.kt            # Playback control
│       └── StaticResource.kt          # Frontend serving
├── src/main/resources/
│   ├── assets/                        # Frontend build output
│   │   └── index.html
│   ├── db/migration/
│   │   └── V1__Create_media_library_table.sql
│   └── config.yml
├── src/test/kotlin/com/blockbuster/
│   ├── media/
│   │   └── SqliteMediaStoreTest.kt
│   └── plugin/
│       ├── RokuPluginTest.kt
│       ├── EmbyPluginTest.kt
│       └── PluginFactoryTest.kt
├── frontend/
│   ├── src/
│   │   ├── main.tsx
│   │   ├── search/SearchView.tsx
│   │   ├── library/LibraryView.tsx
│   │   └── play/PlayView.tsx
│   ├── package.json
│   └── vite.config.ts
├── build.gradle.kts
├── SPECIFICATIONS.md                   # This file
├── CONSTITUTION.md                     # Engineering standards
└── README.md
```

### Build and Run

```bash
# Build backend
./gradlew clean build

# Run tests
./gradlew test

# Build frontend
cd frontend
npm run build:copy  # Builds and copies to resources/assets/

# Run application
java -jar build/libs/blockbuster-1.0-SNAPSHOT.jar server config.yml

# Development mode with auto-reload
./gradlew run --args='server config.yml'
```

---

## Future Enhancements

### Phase 1: Emby Integration (HIGH PRIORITY)
- ✅ Deep link discovery completed (Command=PlayNow&ItemIds)
- Implement EmbyPlugin with validated deep linking
- Search, library browsing, playback control
- Resume position tracking with StartPositionTicks parameter

### Phase 2: Theater Plugin System
- Implement TheaterPlugin interface
- Create TheaterPluginManager
- Build Harmony Hub implementation
- Integrate theater setup into playback flow
- Add media center ID tracking to content model

### Phase 3: Roku Action Sequences
- Action recording interface
- Sequence playback engine
- Wait time optimization
- Failure recovery

### Phase 3: NFC Management
- Tag writing interface
- Tag inventory management
- Bulk tag programming
- Tag validation

### Phase 4: Multi-Device Support
- Room-based routing (NFC tap in living room → play on living room device)
- Device discovery
- Session management
- Multi-room audio sync

### Phase 5: Analytics & Intelligence
- Play history tracking
- Recommendation engine
- Popular content discovery
- Usage patterns

### Phase 6: Additional Plugins
- Spotify (music streaming)
- Plex (alternative media server)
- YouTube (video platform)
- Apple Music (music streaming)

---

## Security Considerations

### Credential Management
- **Never commit secrets**: Use environment variables for API keys
- **Config file security**: Restrict permissions on config.yml (600)
- **HTTPS required**: All external API calls over HTTPS
- **Token rotation**: Support for refreshing API tokens

### Input Validation
- **SQL injection**: Use prepared statements (already implemented)
- **XSS prevention**: Sanitize all user inputs in frontend
- **Command injection**: Validate all plugin parameters
- **JSON parsing**: Validate schema before deserialization

### Network Security
- **Local network only**: Roku ECP endpoints on trusted network
- **Authentication**: Add API authentication for remote access
- **Rate limiting**: Prevent abuse of search/play endpoints

---

## Performance Targets

### Backend
- **Cold start**: < 5 seconds
- **Search latency**: < 500ms (Emby), < 100ms (local cache)
- **Play command**: < 200ms to execute
- **Database queries**: < 50ms for simple reads
- **Concurrent users**: Support 10+ simultaneous NFC taps

### Frontend
- **Initial load**: < 2 seconds (gzipped bundle)
- **Search responsiveness**: < 100ms debounce, show results within 500ms
- **Navigation**: Instant (client-side routing)

### Database
- **Max library size**: 10,000+ items without performance degradation
- **Backup size**: < 10MB for typical library
- **Query performance**: Indexed lookups in < 10ms

---

## Testing Strategy

### Unit Tests (80% coverage target)
- All plugin implementations
- MediaStore operations
- Factory creation logic
- Content serialization/deserialization

### Integration Tests
- Full plugin lifecycle (init → search → play)
- Database migrations
- REST endpoint contracts
- Frontend-backend integration

### End-to-End Tests
- NFC tap → playback flow
- Search → add to library → play
- Multi-plugin scenarios

### Manual Testing
- Physical NFC tag testing
- Multi-device scenarios
- Network failure recovery
- UI/UX validation

---

## Documentation Standards

### Code Documentation
- Public API: Full KDoc comments
- Complex algorithms: Inline comments explaining why, not what
- Plugin examples: Include usage examples in plugin interface
- Configuration: Document all config options with examples

### API Documentation
- OpenAPI/Swagger spec generation
- Example requests/responses
- Error code documentation
- Authentication requirements

### User Documentation
- Setup guide (installation, configuration)
- Plugin configuration guide
- NFC tag programming guide
- Troubleshooting guide

---

## Versioning and Compatibility

### Semantic Versioning
- **Major**: Breaking API changes, database schema changes
- **Minor**: New plugins, new features (backward compatible)
- **Patch**: Bug fixes, performance improvements

### Database Migrations
- **Never modify existing migrations**: Always create new migrations
- **Backward compatibility**: Support reading old JSON formats
- **Migration testing**: Test upgrade path from previous versions

### Plugin Compatibility
- **Plugin version in config**: Track plugin version for compatibility
- **Graceful degradation**: Handle missing plugin gracefully
- **Migration helpers**: Provide tools to migrate between plugin versions
