# Blockbuster - Claude Agent Guide

Welcome to the Blockbuster project! This is an NFC-powered media library system that enables physical media cartridges to trigger playback on Roku devices and other media platforms.

## Quick Start for Agents

### What is Blockbuster?

Blockbuster is an NFC tag system for playing media. Users tap cassette-sized cartridges containing NFC tags, which triggers playback of movies, TV shows, or music on their home media systems (primarily Roku devices).

**Core Architecture:**
- **Web Service**: Dropwizard REST API that handles NFC tag resolution
- **Plugin System**: Extensible architecture for different media services (Roku, Spotify, etc.)
- **Content Registry**: SQLite database mapping NFC tag UUIDs to media content
- **Roku Integration**: Full External Control Protocol (ECP) implementation with deep linking

### Project Structure

```
blockbuster/
├── src/main/kotlin/com/blockbuster/
│   ├── BlockbusterApplication.kt    # Main Dropwizard application
│   ├── BlockbusterConfiguration.kt  # YAML config mapping
│   ├── media/                        # Media content models
│   │   ├── MediaStore.kt            # SQLite content registry
│   │   ├── RokuMediaContent.kt      # Roku-specific content
│   │   └── MediaContent.kt          # Base content interface
│   ├── plugin/                       # Plugin system
│   │   ├── MediaPlugin.kt           # Base plugin interface
│   │   ├── MediaPluginManager.kt    # Plugin registry
│   │   ├── PluginFactory.kt         # Plugin creation from config
│   │   ├── RokuPlugin.kt            # Roku device control
│   │   ├── RokuChannelPlugin.kt     # Channel-specific logic
│   │   ├── EmbyRokuChannelPlugin.kt # Emby integration
│   │   ├── DisneyPlusRokuChannelPlugin.kt
│   │   ├── NetflixRokuChannelPlugin.kt
│   │   ├── HBOMaxRokuChannelPlugin.kt
│   │   └── PrimeVideoRokuChannelPlugin.kt
│   └── resource/                     # REST endpoints
│       ├── SearchResource.kt        # Search API
│       ├── LibraryResource.kt       # NFC tag library management
│       └── StaticResource.kt        # Web interface
├── docs/                             # Documentation
│   ├── agents/                       # Agent-specific docs
│   │   └── CONSTITUTION.md          # **CODE QUALITY STANDARDS** ⭐
│   ├── specs/                        # Specifications
│   │   ├── SPECIFICATIONS.md        # System requirements
│   │   └── IMPLEMENTATION_STRATEGY.md
│   ├── CHANNEL_DEEP_LINKING_DISCOVERY.md  # Roku channel testing results
│   ├── EMBY_ROKU_DISCOVERY.md       # Emby deep linking process
│   ├── roku_deep_linking.md         # Roku ECP protocol reference
│   └── ROKU_ACTION_SEQUENCES.md     # UI navigation patterns
└── config.yml                        # Application configuration

```

## Engineering Standards

### ⭐ CRITICAL: Read the Constitution

**Before making ANY code changes, read [docs/agents/CONSTITUTION.md](docs/agents/CONSTITUTION.md)**

This document is non-negotiable. It defines:
- Code quality standards (simplicity, type safety, error handling)
- Architectural patterns (plugin system, sealed classes, value types)
- Testing requirements (unit tests required for all new code)
- Documentation standards (KDoc for public APIs)

### ⭐ CRITICAL: Build and Test After Every Change

**After ANY code change, you MUST:**

1. **Run the build**:
   ```bash
   ./gradlew build
   ```

2. **Verify tests pass**:
   ```bash
   ./gradlew test
   ```

3. **Fix any failures immediately** - Never commit broken code

**Why this matters:**
- Kotlin compilation catches type errors
- Tests validate behavior
- Early detection prevents cascading failures
- Elite code quality requires continuous verification

**When to run:**
- ✅ After implementing new features
- ✅ After refactoring existing code
- ✅ After updating dependencies
- ✅ Before creating commits or PRs
- ✅ After resolving merge conflicts

**If tests fail:**
- Fix the issue immediately
- Do NOT proceed to other tasks
- Do NOT commit failing code
- Treat test failures as blocking issues



## Key Technical Concepts

### Roku Plugin Architecture

The Roku plugin uses a **two-layer architecture**:

1. **RokuPlugin** - Handles device communication (ECP protocol, keypresses)
2. **RokuChannelPlugin** - Handles channel-specific logic (Emby, Netflix, etc.)

This separation allows different channels to use different playback strategies:
- **Pure DeepLink**: Direct URL launch (Emby custom format)
- **Hybrid ActionSequence**: DeepLink + keypresses (Disney+, Netflix, HBO Max, Prime Video)

### Playback Commands

```kotlin
sealed class RokuPlaybackCommand {
    data class DeepLink(val url: String) : RokuPlaybackCommand()
    data class ActionSequence(val actions: List<RokuAction>) : RokuPlaybackCommand()
}
```

Example - Emby (pure deep link):
```kotlin
RokuPlaybackCommand.DeepLink(
    url = "http://$rokuIp:8060/launch/44191?Command=PlayNow&ItemIds=$itemId"
)
```

Example - Netflix (hybrid with PLAY press):
```kotlin
RokuPlaybackCommand.ActionSequence(
    listOf(
        RokuAction.Launch(channelId = "12", params = "contentId=$id&mediaType=movie"),
        RokuAction.Wait(2000),
        RokuAction.Press(RokuKey.PLAY, 1)
    )
)
```

## Common Tasks

### Adding a New Roku Channel Plugin

1. Create new file: `src/main/kotlin/com/blockbuster/plugin/{ChannelName}RokuChannelPlugin.kt`
2. Implement `RokuChannelPlugin` interface
3. Define channel ID and name
4. Implement `buildPlaybackCommand()` with appropriate deep link or action sequence
5. Optionally implement `search()` if channel provides an API
6. Add unit tests in `src/test/kotlin/com/blockbuster/plugin/{ChannelName}RokuChannelPluginTest.kt`
7. Document discovery process in `docs/`

### Testing Deep Linking

See [docs/CHANNEL_DEEP_LINKING_DISCOVERY.md](docs/CHANNEL_DEEP_LINKING_DISCOVERY.md) for the testing methodology:

1. Extract content IDs from channel URLs
2. Test standard ECP format: `http://{roku-ip}:8060/launch/{channelId}?contentId={id}&mediaType={type}`
3. Try alternative parameter names if standard doesn't work
4. Document what works and what doesn't
5. Determine if SELECT/PLAY presses are needed

### Running the Application

```bash
# Build
./gradlew build

# Run server
./gradlew run --args="server config.yml"

# Run tests
./gradlew test

# Access web interface
open http://localhost:8080/
```

### Configuration

Edit `config.yml` for:
- Database path (SQLite)
- Plugin configurations (Roku device IP, Emby server URL, API keys)
- Server ports and logging

## Important Files

### Must Read
- **[docs/agents/CONSTITUTION.md](docs/agents/CONSTITUTION.md)** - Engineering standards (READ FIRST!)
- **[docs/CHANNEL_DEEP_LINKING_DISCOVERY.md](docs/CHANNEL_DEEP_LINKING_DISCOVERY.md)** - Channel testing results

### Reference
- **[docs/roku_deep_linking.md](docs/roku_deep_linking.md)** - Roku ECP protocol documentation
- **[docs/specs/SPECIFICATIONS.md](docs/specs/SPECIFICATIONS.md)** - System requirements
- **[docs/specs/IMPLEMENTATION_STRATEGY.md](docs/specs/IMPLEMENTATION_STRATEGY.md)** - Architecture decisions

### Discovery Notes
- **[docs/EMBY_ROKU_DISCOVERY.md](docs/EMBY_ROKU_DISCOVERY.md)** - How Emby deep linking was discovered
- **[docs/ROKU_ACTION_SEQUENCES.md](docs/ROKU_ACTION_SEQUENCES.md)** - UI navigation patterns

## Testing Philosophy

From the Constitution:

> Every public function deserves a test. If you can't test it, you can't trust it.

- **Unit tests required**: All new code must have unit tests
- **Test files mirror source**: `RokuPlugin.kt` → `RokuPluginTest.kt`
- **Use descriptive test names**: `fun testEmbyDeepLinkBuildsCorrectUrl()`
- **Mock external dependencies**: Use test doubles for HTTP clients, databases

## Getting Help

- Check [docs/agents/CONSTITUTION.md](docs/agents/CONSTITUTION.md) for code standards
- Review existing plugin implementations as examples
- Read discovery docs to understand how channels work
- All Roku channel plugins follow similar patterns

## Project Context

**User Profile:**
- Expects elite code quality and thoughtful architecture
- Values simplicity, type safety, and testability
- Appreciates well-documented discovery processes

**Next Steps:**
- Theater plugin system (Harmony Hub integration for equipment setup)
- Additional media plugins (Spotify, etc.)
- NFC tag management UI
