# Blockbuster: Engineering Constitution

> *"Quality is not an act, it is a habit."* — Aristotle

This document establishes the engineering standards, patterns, and principles that govern the Blockbuster codebase. These are not suggestions—they are requirements. Every commit must uphold these standards.

---

## Core Principles

### 1. Simplicity Over Cleverness

**Write code for humans, not compilers.**

- If you need to explain how it works, rewrite it to be obvious
- Three lines of clear code beats one line of clever code
- Avoid language tricks, operator overloading, or DSL abuse
- Prefer explicit over implicit

**Bad**:
```kotlin
fun MediaStore.getOrPut(uuid: UUID, plugin: String, factory: () -> MediaContent) =
    get(uuid)?.let { parse(it) } ?: factory().also { put(plugin, it) }
```

**Good**:
```kotlin
fun getOrCreate(uuid: UUID, plugin: String, defaultContent: MediaContent): MediaContent {
    val existing = mediaStore.get(uuid)
    if (existing != null) {
        return parseContent(existing)
    }

    mediaStore.put(plugin, defaultContent)
    return defaultContent
}
```

### 2. Type Safety as a Defense Mechanism

**Use the type system to make illegal states unrepresentable.**

- Prefer sealed classes over enums with `when` branches
- Use value classes for domain primitives (UUIDs, IDs)
- Avoid `Any`, avoid `!!`, avoid unchecked casts
- Make functions total (handle all cases) rather than partial

**Bad**:
```kotlin
fun play(contentId: String, plugin: String) {
    val content = getContent(contentId) as RokuMediaContent  // ❌ Unchecked cast
    rokuPlugin.play(content.contentId, emptyMap())
}
```

**Good**:
```kotlin
fun <C : MediaContent> play(contentId: String, plugin: MediaPlugin<C>) {
    val content = mediaStore.getParsed(
        UUID.fromString(contentId),
        plugin.getPluginName(),
        plugin.getContentParser()
    ) ?: throw NotFoundException("Content not found: $contentId")

    plugin.play(contentId, emptyMap())
}
```

### 3. Explicit Error Handling

**Never swallow exceptions. Never use empty catch blocks.**

- Every exception must be logged or propagated
- Use specific exception types, not generic `RuntimeException`
- Log context: what failed, why it failed, what was being attempted
- Return null only when absence is a valid state (not an error)

**Bad**:
```kotlin
try {
    httpClient.newCall(request).execute()
} catch (e: Exception) {
    // ❌ Silent failure
}
```

**Good**:
```kotlin
try {
    val response = httpClient.newCall(request).execute()
    if (!response.isSuccessful) {
        logger.error("Roku ECP command failed: ${response.code} - ${response.message}")
        throw RokuCommandException("ECP command failed with status ${response.code}")
    }
} catch (e: IOException) {
    logger.error("Network error executing Roku command", e)
    throw RokuNetworkException("Failed to reach Roku device at $deviceIp", e)
}
```

### 4. Single Responsibility

**Every class, function, and module should do one thing well.**

- Functions should fit on one screen (< 50 lines)
- Classes should have a single reason to change
- Files should be cohesive (related functionality together)
- Avoid "god objects" that know too much

**Bad**:
```kotlin
class MediaManager {
    fun searchMedia() { }
    fun playMedia() { }
    fun storeMedia() { }
    fun sendHttpRequest() { }  // ❌ Too many concerns
    fun parseJson() { }        // ❌ Infrastructure mixed with domain
}
```

**Good**:
```kotlin
// Domain: What the system does
class MediaPluginManager(private val plugins: Map<String, MediaPlugin<*>>) {
    fun getPlugin(name: String): MediaPlugin<*>? = plugins[name]
    fun listPlugins(): List<PluginInfo> = plugins.map { /* ... */ }
}

// Infrastructure: How it's done
class HttpMediaClient(private val httpClient: OkHttpClient) {
    fun sendRequest(request: Request): Response { }
}

// Data: What we store
interface MediaStore {
    fun put(plugin: String, content: MediaContent): UUID
    fun get(uuid: UUID): MediaItem?
}
```

### 5. Immutability by Default

**Mutable state is the root of all evil.**

- Use `val` over `var` (99% of the time)
- Use `data class` with `val` properties
- Avoid mutable collections (`MutableList`, `MutableMap`)
- If mutation is needed, encapsulate it and document why

**Bad**:
```kotlin
class MediaLibrary {
    var items = mutableListOf<MediaItem>()  // ❌ Public mutable state

    fun addItem(item: MediaItem) {
        items.add(item)  // ❌ Callers can mutate items directly
    }
}
```

**Good**:
```kotlin
class MediaLibrary(private val mediaStore: MediaStore) {
    fun addItem(plugin: String, content: MediaContent): UUID {
        return mediaStore.put(plugin, content)
    }

    fun getItems(offset: Int, limit: Int): List<MediaItem> {
        return mediaStore.list(offset, limit)  // Returns immutable list
    }
}
```

### 6. Test Everything That Matters

**If it's not tested, it's broken.**

- Every public function has a test
- Every bug fix includes a regression test
- Every edge case has a test case
- Test names describe behavior, not implementation

**Test Structure** (Given-When-Then):
```kotlin
@Test
fun `play command constructs correct ECP URL with all parameters`() {
    // Given
    val content = RokuMediaContent(
        channelId = "12",
        contentId = "movie-123",
        mediaType = "movie",
        channelName = "Netflix"
    )
    mediaStore.put("roku", content)

    // When
    rokuPlugin.play(content.contentId, emptyMap())

    // Then
    val capturedRequest = requestCaptor.firstValue
    assertThat(capturedRequest.url.toString())
        .isEqualTo("http://192.168.1.100:8060/launch/12?contentId=movie-123&mediaType=movie")
}
```

---

## Code Organization

### Package Structure

```
com.blockbuster/
├── BlockbusterApplication.kt           # Main entry point only
├── BlockbusterConfiguration.kt         # Config classes only
├── db/                                 # Database infrastructure
│   ├── FlywayManager.kt
│   └── DataSourceFactory.kt
├── media/                              # Core domain: Media content
│   ├── MediaContent.kt                 # Interface
│   ├── MediaStore.kt                   # Interface
│   ├── SqliteMediaStore.kt             # Implementation
│   ├── RokuMediaContent.kt             # Roku domain model
│   └── EmbyMediaContent.kt             # Emby domain model
├── plugin/                             # Plugin system
│   ├── MediaPlugin.kt                  # Interface
│   ├── MediaPluginManager.kt           # Registry
│   ├── PluginFactory.kt                # Factory
│   ├── RokuPlugin.kt                   # Roku implementation
│   └── EmbyPlugin.kt                   # Emby implementation
├── resource/                           # REST API layer
│   ├── HealthResource.kt
│   ├── LibraryResource.kt
│   ├── SearchResource.kt
│   ├── PlayResource.kt
│   └── StaticResource.kt
├── exception/                          # Domain exceptions
│   ├── NotFoundException.kt
│   ├── PluginException.kt
│   └── RokuCommandException.kt
└── util/                               # Shared utilities (use sparingly)
    └── JsonUtils.kt
```

**Rules**:
- No circular dependencies between packages
- `resource` depends on `plugin` and `media`, never the reverse
- `plugin` implementations depend on `media`, not on each other
- `db` is infrastructure, never depends on domain packages

### File Naming

- **Classes**: `PascalCase.kt` (matches class name)
- **Interfaces**: No `I` prefix, just `MediaStore.kt`
- **Tests**: `{ClassName}Test.kt`
- **Extensions**: `{Type}Extensions.kt` (e.g., `StringExtensions.kt`)

### Import Organization

1. Standard library imports
2. Third-party library imports (alphabetical)
3. Project imports (alphabetical)
4. Blank line between groups

```kotlin
import java.util.UUID
import java.time.Instant

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.OkHttpClient
import okhttp3.Request

import com.blockbuster.media.MediaContent
import com.blockbuster.media.MediaStore
import com.blockbuster.plugin.MediaPlugin
```

---

## Kotlin Style Guide

### Naming Conventions

| Type | Convention | Example |
|------|------------|---------|
| Classes | PascalCase | `MediaPluginManager` |
| Interfaces | PascalCase, no `I` prefix | `MediaStore` |
| Functions | camelCase, verb-based | `getPlugin()`, `parseContent()` |
| Properties | camelCase | `deviceIp`, `channelId` |
| Constants | UPPER_SNAKE_CASE | `DEFAULT_TIMEOUT` |
| Packages | lowercase, no underscores | `com.blockbuster.plugin` |

### Function Naming

**Use specific, descriptive verbs:**

- `get` - Returns existing value, may return null
- `find` - Searches for value, may return null
- `create` - Creates new instance
- `build` - Constructs complex object
- `parse` - Converts from one format to another
- `validate` - Checks correctness, throws on invalid
- `check` - Verifies condition, returns boolean

**Bad**:
```kotlin
fun doStuff(thing: String): Result  // ❌ Vague
fun handle(input: Request): Response  // ❌ Generic
fun process(data: Data): Data  // ❌ Meaningless
```

**Good**:
```kotlin
fun parseMediaContent(json: String): MediaContent
fun validatePluginConfig(config: Map<String, Any>)
fun buildEcpUrl(channelId: String, contentId: String): String
```

### Property Style

**Prefer expression bodies for simple properties:**

```kotlin
// Good
val fullUrl: String
    get() = "$baseUrl/$path"

// Bad - unnecessary custom getter
val fullUrl: String
    get() {
        return "$baseUrl/$path"
    }
```

**Initialize properties in constructor:**

```kotlin
// Good
class RokuPlugin(
    private val deviceIp: String,
    private val deviceName: String,
    private val mediaStore: MediaStore
) : MediaPlugin<RokuMediaContent> {
    private val httpClient = OkHttpClient()
    private val baseUrl = "http://$deviceIp:8060"
}

// Bad - late initialization without good reason
class RokuPlugin {
    private lateinit var deviceIp: String  // ❌ Could be constructor param
    private lateinit var httpClient: OkHttpClient  // ❌ Could be initialized immediately
}
```

### Control Flow

**Prefer early returns over nested conditionals:**

```kotlin
// Good
fun play(contentId: String, options: Map<String, Any>) {
    val content = mediaStore.get(contentId)
    if (content == null) {
        logger.warn("Content not found: $contentId")
        return
    }

    if (content.channelId.isEmpty()) {
        logger.error("Invalid content: missing channelId")
        return
    }

    sendPlayCommand(content)
}

// Bad
fun play(contentId: String, options: Map<String, Any>) {
    val content = mediaStore.get(contentId)
    if (content != null) {
        if (content.channelId.isNotEmpty()) {
            sendPlayCommand(content)
        } else {
            logger.error("Invalid content: missing channelId")
        }
    } else {
        logger.warn("Content not found: $contentId")
    }
}
```

**Use `when` for complex conditionals:**

```kotlin
// Good
fun getHttpMethod(operation: Operation): String = when (operation) {
    Operation.SEARCH -> "GET"
    Operation.PLAY -> "POST"
    Operation.DELETE -> "DELETE"
}

// Bad - unnecessary if-else chain
fun getHttpMethod(operation: Operation): String {
    if (operation == Operation.SEARCH) {
        return "GET"
    } else if (operation == Operation.PLAY) {
        return "POST"
    } else if (operation == Operation.DELETE) {
        return "DELETE"
    } else {
        throw IllegalArgumentException("Unknown operation")
    }
}
```

### Data Classes

**Use data classes for value objects:**

```kotlin
// Good - concise, gets equals/hashCode/toString for free
data class SearchResult<T : MediaContent>(
    val title: String,
    val url: String?,
    val mediaUrl: String?,
    val content: T
)

// Bad - boilerplate hell
class SearchResult<T : MediaContent>(
    val title: String,
    val url: String?,
    val mediaUrl: String?,
    val content: T
) {
    override fun equals(other: Any?): Boolean { /* ... */ }
    override fun hashCode(): Int { /* ... */ }
    override fun toString(): String { /* ... */ }
}
```

**Validate in `init` block, not constructor:**

```kotlin
data class RokuMediaContent(
    val channelId: String,
    val contentId: String,
    val ecpCommand: String = "launch",
    val channelName: String? = null
) : MediaContent {
    init {
        require(channelId.isNotBlank()) { "channelId must not be blank" }
        require(contentId.isNotBlank()) { "contentId must not be blank" }
        require(ecpCommand.isNotBlank()) { "ecpCommand must not be blank" }
    }
}
```

---

## Testing Standards

### Coverage Requirements

| Component | Minimum Coverage | Target Coverage |
|-----------|------------------|-----------------|
| Plugin implementations | 80% | 90% |
| MediaStore | 90% | 95% |
| REST resources | 70% | 80% |
| Domain models | 80% | 90% |
| Utilities | 90% | 95% |

**Exemptions**: DTOs, configuration classes, simple getters/setters

### Test Structure

**One test file per class:**
- `RokuPlugin.kt` → `RokuPluginTest.kt`
- `SqliteMediaStore.kt` → `SqliteMediaStoreTest.kt`

**Test organization:**

```kotlin
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RokuPluginTest {
    private lateinit var rokuPlugin: RokuPlugin
    private lateinit var mockMediaStore: MediaStore
    private lateinit var mockHttpClient: OkHttpClient

    @BeforeAll
    fun setup() {
        // One-time expensive setup
    }

    @BeforeEach
    fun setupEach() {
        // Per-test setup
        mockMediaStore = mock(MediaStore::class.java)
        mockHttpClient = mock(OkHttpClient::class.java)
        rokuPlugin = RokuPlugin("192.168.1.100", "Test Roku", mockMediaStore, mockHttpClient)
    }

    @AfterEach
    fun tearDown() {
        // Cleanup
    }

    @Nested
    inner class PlayCommands {
        @Test
        fun `play constructs correct URL for Netflix content`() { }

        @Test
        fun `play handles missing content gracefully`() { }

        @Test
        fun `play logs network errors`() { }
    }

    @Nested
    inner class SearchFunctionality {
        @Test
        fun `search returns results for valid query`() { }

        @Test
        fun `search handles empty query`() { }
    }
}
```

### Test Naming

**Use backticks for readable test names:**

```kotlin
@Test
fun `search returns empty list when no results found`() { }

@Test
fun `play throws NotFoundException when content doesn't exist`() { }

@Test
fun `buildEcpUrl encodes special characters correctly`() { }
```

**Test name structure**: `{function} {does what} {under what condition}`

### Assertions

**Use AssertJ for fluent assertions:**

```kotlin
// Good
assertThat(result).isNotNull()
assertThat(result.size).isEqualTo(3)
assertThat(result.first().title).isEqualTo("The Matrix")

// Bad
assertTrue(result != null)  // ❌ Poor failure message
assertEquals(3, result.size)  // ❌ Backwards (expected, actual)
```

### Mock Usage

**Mock external dependencies, not domain logic:**

```kotlin
// Good - mock infrastructure
val mockHttpClient = mock(OkHttpClient::class.java)
val mockMediaStore = mock(MediaStore::class.java)

// Bad - don't mock domain logic
val mockRokuPlugin = mock(RokuPlugin::class.java)  // ❌ This is what we're testing
```

**Verify behavior, not implementation:**

```kotlin
// Good - verify outcome
verify(mockHttpClient).newCall(any())
assertThat(capturedUrl).contains("contentId=movie-123")

// Bad - over-specification
verify(mockHttpClient, times(1)).newCall(any())  // ❌ Implementation detail
verify(mockMediaStore).get(eq(uuid))  // ❌ Testing mock interactions, not behavior
```

---

## Database Standards

### Migration Rules

1. **Never modify existing migrations**
   - Migrations are immutable once committed
   - Create new migration for changes

2. **Use semantic versioning for migrations**
   - `V1__Create_media_library_table.sql`
   - `V2__Add_plugin_version_column.sql`
   - `V3__Create_index_on_created_at.sql`

3. **Make migrations reversible**
   - Include `DROP` statements in comments for reference
   - Test rollback scenarios

4. **Test migrations on production-like data**
   - Test with 10,000+ rows
   - Test upgrade from previous version
   - Test schema validation

### SQL Style

```sql
-- Good - readable, explicit
CREATE TABLE IF NOT EXISTS media_library (
    uuid TEXT PRIMARY KEY,
    plugin TEXT NOT NULL,
    config_json TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT (datetime('now')),
    updated_at TIMESTAMP DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_library_plugin
    ON media_library(plugin);

-- Bad - hard to read
CREATE TABLE media_library(uuid TEXT PRIMARY KEY,plugin TEXT NOT NULL,config_json TEXT NOT NULL,created_at TIMESTAMP DEFAULT(datetime('now')),updated_at TIMESTAMP DEFAULT(datetime('now')));
```

### Query Safety

**Always use prepared statements:**

```kotlin
// Good
val stmt = connection.prepareStatement(
    "SELECT * FROM media_library WHERE uuid = ?"
)
stmt.setString(1, uuid.toString())
val rs = stmt.executeQuery()

// Bad - SQL injection vulnerability
val query = "SELECT * FROM media_library WHERE uuid = '$uuid'"  // ❌ NEVER
connection.createStatement().executeQuery(query)
```

**Handle null properly:**

```kotlin
// Good
if (rs.getString("channel_name") != null) {
    content.channelName = rs.getString("channel_name")
}

// Bad
content.channelName = rs.getString("channel_name")  // ❌ NPE if null
```

---

## REST API Standards

### Endpoint Naming

**Use plural nouns, HTTP verbs for actions:**

```
✅ GET    /library
✅ POST   /library/{plugin}
✅ GET    /library/{uuid}
✅ DELETE /library/{uuid}
✅ GET    /search/{plugin}?q={query}

❌ GET    /getLibrary
❌ POST   /addToLibrary
❌ GET    /libraryItem/{uuid}
```

### Response Format

**Consistent JSON structure:**

```kotlin
// Success response
{
    "data": { /* ... */ },
    "meta": {
        "timestamp": "2024-08-31T22:52:00Z"
    }
}

// Error response
{
    "error": {
        "code": "NOT_FOUND",
        "message": "Content not found: 123e4567-e89b-12d3-a456-426614174000",
        "details": { }
    },
    "meta": {
        "timestamp": "2024-08-31T22:52:00Z"
    }
}
```

### HTTP Status Codes

| Code | Usage |
|------|-------|
| 200 OK | Successful read operation |
| 201 Created | Successful creation, return UUID in body |
| 204 No Content | Successful delete |
| 400 Bad Request | Invalid input (missing params, invalid JSON) |
| 404 Not Found | Resource doesn't exist |
| 409 Conflict | Resource already exists |
| 500 Internal Server Error | Unexpected server error |

**Never use 200 for errors:**

```kotlin
// Bad
return Response.ok(mapOf("error" to "Not found")).build()  // ❌ 200 status with error

// Good
return Response.status(404)
    .entity(mapOf("error" to "Content not found: $uuid"))
    .build()
```

### Pagination

**Always paginate list endpoints:**

```kotlin
@GET
@Path("/library")
fun listLibrary(
    @QueryParam("page") @DefaultValue("1") page: Int,
    @QueryParam("pageSize") @DefaultValue("25") pageSize: Int,
    @QueryParam("plugin") plugin: String?
): Response {
    val offset = (page - 1) * pageSize
    val items = mediaStore.list(offset, pageSize, plugin)
    val total = mediaStore.count(plugin)

    return Response.ok(mapOf(
        "items" to items,
        "page" to page,
        "pageSize" to pageSize,
        "total" to total,
        "totalPages" to (total + pageSize - 1) / pageSize
    )).build()
}
```

---

## Logging Standards

### Log Levels

| Level | Usage | Example |
|-------|-------|---------|
| ERROR | Unrecoverable errors, immediate attention needed | Failed to connect to database |
| WARN | Recoverable issues, degraded functionality | Roku device not responding, using cached data |
| INFO | Important business events | NFC tag registered, media playback started |
| DEBUG | Detailed diagnostic information | ECP URL constructed, SQL query executed |
| TRACE | Very detailed, potentially noisy | HTTP request/response bodies |

### Logging Best Practices

**Include context in every log:**

```kotlin
// Good - who, what, why, when
logger.error(
    "Failed to execute Roku play command for content {}: HTTP {} - {}",
    contentId,
    response.code,
    response.message,
    exception
)

// Bad - no context
logger.error("Play command failed")  // ❌ What content? Which device? Why?
```

**Use SLF4J placeholders, not string concatenation:**

```kotlin
// Good
logger.debug("Searching for media: {} using plugin: {}", query, pluginName)

// Bad
logger.debug("Searching for media: " + query + " using plugin: " + pluginName)  // ❌ Always evaluates
```

**Log exceptions with full stack traces:**

```kotlin
// Good
try {
    executeRokuCommand(request)
} catch (e: IOException) {
    logger.error("Network error communicating with Roku device at {}", deviceIp, e)
    throw RokuNetworkException("Failed to reach device", e)
}

// Bad
logger.error("Error: " + e.message)  // ❌ Lost stack trace
```

### What Not to Log

❌ Passwords, API keys, secrets
❌ Full user data (PII)
❌ Excessive DEBUG logs in loops
❌ Redundant information (logging same event at multiple levels)

---

## Security Standards

### Secrets Management

**Never commit secrets:**

```yaml
# config.yml - Good
plugins:
  enabled:
    - type: emby
      config:
        serverUrl: ${EMBY_SERVER_URL}
        apiKey: ${EMBY_API_KEY}  # ✅ Environment variable
        userId: ${EMBY_USER_ID}

# config.yml - Bad
plugins:
  enabled:
    - type: emby
      config:
        apiKey: "abc123secretkey"  # ❌ NEVER commit this
```

### Input Validation

**Validate all external inputs:**

```kotlin
@POST
@Path("/library/{pluginName}")
fun addToLibrary(
    @PathParam("pluginName") pluginName: String,
    request: AddToLibraryRequest
): Response {
    // Validate plugin exists
    val plugin = pluginManager.getPlugin(pluginName)
        ?: return Response.status(404).entity(mapOf(
            "error" to "Plugin not found: $pluginName"
        )).build()

    // Validate UUID format if provided
    val uuid = request.uuid?.let {
        try {
            UUID.fromString(it)
        } catch (e: IllegalArgumentException) {
            return Response.status(400).entity(mapOf(
                "error" to "Invalid UUID format: $it"
            )).build()
        }
    } ?: UUID.randomUUID()

    // Validate content structure
    val content = try {
        plugin.getContentParser().parse(request.content)
    } catch (e: Exception) {
        return Response.status(400).entity(mapOf(
            "error" to "Invalid content format: ${e.message}"
        )).build()
    }

    mediaStore.put(pluginName, content)
    return Response.ok(mapOf("uuid" to uuid.toString())).build()
}
```

### SQL Injection Prevention

**Always use prepared statements (already covered in Database Standards)**

### XSS Prevention

**Sanitize all user inputs before rendering:**

```typescript
// Frontend - Good
import DOMPurify from 'dompurify';

function SearchResult({ result }: { result: SearchResult }) {
    const sanitizedTitle = DOMPurify.sanitize(result.title);
    return <div dangerouslySetInnerHTML={{ __html: sanitizedTitle }} />;
}

// Better - just use text content
function SearchResult({ result }: { result: SearchResult }) {
    return <div>{result.title}</div>;  // ✅ React escapes by default
}
```

---

## Performance Standards

### Database Optimization

**Use indexes for common queries:**

```sql
-- Query: List library items by plugin
CREATE INDEX idx_library_plugin ON media_library(plugin);

-- Query: List library items by date
CREATE INDEX idx_library_created_at ON media_library(created_at DESC);

-- Query: Search by UUID (already primary key - free index)
```

**Batch operations when possible:**

```kotlin
// Good - single transaction
fun bulkAdd(items: List<Pair<String, MediaContent>>) {
    connection.autoCommit = false
    try {
        val stmt = connection.prepareStatement(
            "INSERT INTO media_library (uuid, plugin, config_json) VALUES (?, ?, ?)"
        )
        items.forEach { (plugin, content) ->
            stmt.setString(1, UUID.randomUUID().toString())
            stmt.setString(2, plugin)
            stmt.setString(3, content.toJson())
            stmt.addBatch()
        }
        stmt.executeBatch()
        connection.commit()
    } catch (e: Exception) {
        connection.rollback()
        throw e
    } finally {
        connection.autoCommit = true
    }
}

// Bad - individual transactions
items.forEach { (plugin, content) ->
    mediaStore.put(plugin, content)  // ❌ N database round-trips
}
```

### HTTP Client Optimization

**Reuse HTTP clients (connection pooling):**

```kotlin
// Good - single client instance
class RokuPlugin(
    private val deviceIp: String,
    private val httpClient: OkHttpClient  // ✅ Injected, shared
) {
    fun play(contentId: String) {
        httpClient.newCall(buildRequest()).execute()
    }
}

// Bad - create new client every time
fun play(contentId: String) {
    val client = OkHttpClient()  // ❌ New connection pool every call
    client.newCall(buildRequest()).execute()
}
```

### Caching Strategy

**Cache expensive operations:**

```kotlin
class EmbyPlugin(
    private val serverUrl: String,
    private val apiKey: String,
    private val httpClient: OkHttpClient
) {
    // Cache library items for 5 minutes
    private val libraryCache = ConcurrentHashMap<String, CachedValue<List<EmbyItem>>>()

    fun search(query: String, options: Map<String, Any>): List<SearchResult<EmbyMediaContent>> {
        val cacheKey = "search:$query"
        val cached = libraryCache[cacheKey]

        if (cached != null && !cached.isExpired()) {
            logger.debug("Returning cached search results for query: {}", query)
            return cached.value
        }

        val results = performSearchRequest(query)
        libraryCache[cacheKey] = CachedValue(results, expiresAt = System.currentTimeMillis() + 300_000)
        return results
    }
}

data class CachedValue<T>(
    val value: T,
    val expiresAt: Long
) {
    fun isExpired() = System.currentTimeMillis() > expiresAt
}
```

---

## Documentation Standards

### Code Comments

**When to comment:**

✅ Why, not what (explain business logic, not syntax)
✅ Workarounds for library bugs
✅ Performance-critical sections
✅ Complex algorithms
✅ Non-obvious behavior

**When not to comment:**

❌ Obvious code (the code is the documentation)
❌ Commented-out code (delete it, use git history)
❌ Redundant comments that repeat the function name

**Examples:**

```kotlin
// Good - explains WHY
// Roku devices don't support concurrent ECP commands, so we serialize all requests
private val commandLock = ReentrantLock()

// Bad - explains WHAT (code already says this)
// Create a new UUID
val uuid = UUID.randomUUID()

// Good - explains workaround
// OkHttp doesn't URL-encode '+' characters by default, causing issues with Roku
val encodedContentId = contentId.replace("+", "%2B")

// Bad - redundant
/**
 * Gets the plugin name
 * @return the plugin name
 */
fun getPluginName(): String = "roku"  // ❌ Comment adds zero value
```

### KDoc

**Document all public APIs:**

```kotlin
/**
 * Manages media plugins and routes playback commands.
 *
 * This class maintains a registry of available [MediaPlugin] instances and
 * provides methods to search and play media across all plugins.
 *
 * Thread-safe: All public methods are synchronized.
 *
 * @property plugins Map of plugin names to plugin instances
 */
class MediaPluginManager(private val plugins: Map<String, MediaPlugin<*>>) {

    /**
     * Plays media content identified by the given UUID.
     *
     * @param uuid The unique identifier of the media content
     * @throws NotFoundException if content with given UUID doesn't exist
     * @throws PluginException if the plugin fails to execute the play command
     */
    fun play(uuid: UUID) {
        // Implementation
    }
}
```

---

## Git Workflow

### Commit Messages

**Format**: `<type>(<scope>): <subject>`

**Types**:
- `feat`: New feature
- `fix`: Bug fix
- `refactor`: Code change that neither fixes a bug nor adds a feature
- `test`: Adding or updating tests
- `docs`: Documentation only
- `perf`: Performance improvement
- `chore`: Build process, dependency updates

**Examples**:

```
✅ feat(emby): Add Emby plugin with search and playback
✅ fix(roku): Handle special characters in content IDs
✅ refactor(media): Extract MediaStore interface from RokuMediaStore
✅ test(plugin): Add tests for plugin factory error cases
✅ docs(api): Document search endpoint parameters
✅ perf(db): Add index on media_library.plugin column

❌ Fixed bug
❌ Updates
❌ WIP
```

### Branch Strategy

- `main`: Production-ready code
- `develop`: Integration branch (if needed for larger projects)
- `feature/emby-plugin`: Feature branches
- `fix/roku-special-chars`: Bug fix branches

### Pull Request Standards

**PR Title**: Same format as commit messages

**PR Description**:

```markdown
## Summary
Brief description of what this PR does (1-2 sentences)

## Changes
- Added EmbyPlugin with search functionality
- Created EmbyMediaContent data class
- Updated PluginFactory to support Emby
- Added comprehensive tests for Emby integration

## Testing
- [ ] Unit tests pass
- [ ] Integration tests pass
- [ ] Manual testing completed
- [ ] Tested on actual Emby server

## Performance Impact
- Search latency: ~200ms for 1000-item library
- No database schema changes

## Breaking Changes
None
```

---

## Build and CI/CD

### Build Requirements

**All checks must pass before merge:**

1. ✅ Unit tests pass (`./gradlew test`)
2. ✅ Code compiles without warnings
3. ✅ Test coverage meets minimums (80%+)
4. ✅ No TODOs or FIXMEs in code (only in comments with issue references)
5. ✅ Frontend builds successfully (`npm run build`)

### Pre-Commit Hooks

```bash
#!/bin/bash
# .git/hooks/pre-commit

# Run Kotlin linter
./gradlew ktlintCheck || exit 1

# Run tests
./gradlew test || exit 1

# Check for secrets
git diff --cached --name-only | xargs grep -l "apiKey.*=.*\"" && {
    echo "ERROR: Potential secret detected in commit"
    exit 1
}

echo "Pre-commit checks passed ✅"
```

---

## Review Checklist

Before submitting code for review, ensure:

### Code Quality
- [ ] No compiler warnings
- [ ] No TODO/FIXME without issue reference
- [ ] No commented-out code
- [ ] No debug print statements
- [ ] No magic numbers (use constants)
- [ ] All functions < 50 lines
- [ ] All files < 500 lines

### Testing
- [ ] New code has tests
- [ ] Tests are independent (no shared state)
- [ ] Tests use descriptive names
- [ ] Edge cases are tested
- [ ] Error cases are tested

### Documentation
- [ ] Public APIs have KDoc
- [ ] Complex logic has comments
- [ ] README updated if needed
- [ ] API docs updated if endpoint changed

### Security
- [ ] No secrets in code
- [ ] All inputs validated
- [ ] SQL uses prepared statements
- [ ] No XSS vulnerabilities

### Performance
- [ ] No N+1 queries
- [ ] HTTP clients reused
- [ ] Indexes exist for queries
- [ ] No unbounded loops

---

## Exceptions and Pragmatism

> *"A foolish consistency is the hobgoblin of little minds."* — Ralph Waldo Emerson

**These standards are guidelines, not laws.** There will be times when breaking a rule makes sense:

- **Prototyping**: Speed over perfection during exploration
- **Emergency fixes**: In production incidents, fix first, refactor later
- **External constraints**: Third-party libraries may not follow our conventions
- **Performance**: Micro-optimizations may require "ugly" code

**When breaking a rule:**
1. Document why in a comment
2. Create a tech debt issue to address it later
3. Notify the team in the PR

**Example:**

```kotlin
// TODO(issue-123): This uses mutable state for performance reasons.
// Refactor to use immutable collections once we upgrade to Kotlin 2.0.
private val cache = mutableMapOf<String, CachedValue<EmbyItem>>()
```

---

## Enforcement

- **Code reviews**: All code requires at least one review
- **Automated checks**: CI pipeline enforces test coverage, linting
- **Regular refactoring**: Dedicate 20% of time to tech debt
- **Team retrospectives**: Review and update these standards quarterly

---

## Evolution

This constitution is a living document. As the codebase grows and the team learns, these standards will evolve. Propose changes through PRs to this document.

**Last Updated**: 2024-08-31
**Version**: 1.0
**Authors**: Jacob (with Claude Sonnet 4.5)
