# Roku Action Sequences for Emby Navigation

## Problem Statement

The Emby for Roku channel (**v4.1.54, channel ID 44191**) does **not support ECP deep linking** with `itemId` parameters. Deep link commands launch Emby to the home screen, ignoring content-specific parameters.

**Solution**: Programmatically navigate the Emby UI using Roku External Control Protocol (ECP) keypress commands.

---

## Roku ECP Keypress API

### Available Commands

**Navigation**:
```bash
curl -d '' "http://192.168.1.252:8060/keypress/Home"
curl -d '' "http://192.168.1.252:8060/keypress/Up"
curl -d '' "http://192.168.1.252:8060/keypress/Down"
curl -d '' "http://192.168.1.252:8060/keypress/Left"
curl -d '' "http://192.168.1.252:8060/keypress/Right"
curl -d '' "http://192.168.1.252:8060/keypress/Select"
curl -d '' "http://192.168.1.252:8060/keypress/Back"
```

**Playback**:
```bash
curl -d '' "http://192.168.1.252:8060/keypress/Play"
curl -d '' "http://192.168.1.252:8060/keypress/Pause"
curl -d '' "http://192.168.1.252:8060/keypress/Rev"      # Rewind
curl -d '' "http://192.168.1.252:8060/keypress/Fwd"      # Fast-forward
curl -d '' "http://192.168.1.252:8060/keypress/InstantReplay"
```

**Special**:
```bash
curl -d '' "http://192.168.1.252:8060/keypress/Search"
curl -d '' "http://192.168.1.252:8060/keypress/Info"
curl -d '' "http://192.168.1.252:8060/keypress/Backspace"
```

**Text Input** (for search):
```bash
# Letters: Lit_A through Lit_Z
curl -d '' "http://192.168.1.252:8060/keypress/Lit_H"
curl -d '' "http://192.168.1.252:8060/keypress/Lit_e"
curl -d '' "http://192.168.1.252:8060/keypress/Lit_l"
curl -d '' "http://192.168.1.252:8060/keypress/Lit_l"
curl -d '' "http://192.168.1.252:8060/keypress/Lit_o"

# Numbers: Lit_0 through Lit_9
curl -d '' "http://192.168.1.252:8060/keypress/Lit_1"
curl -d '' "http://192.168.1.252:8060/keypress/Lit_2"

# Space
curl -d '' "http://192.168.1.252:8060/keypress/Lit_%20"
```

---

## Emby Navigation Strategy

### Approach 1: Search-Based Navigation

**Sequence**:
1. Launch Emby channel (`http://roku-ip:8060/launch/44191`)
2. Wait for Emby to load (3-4 seconds)
3. Press Search button (`/keypress/Search`)
4. Wait for search UI (1-2 seconds)
5. Type movie/show title (character by character via `Lit_X`)
6. Wait for search results (1-2 seconds)
7. Press Down to first result (may require 0-2 Down presses depending on UI)
8. Press Select to open details
9. Wait for details page (1 second)
10. Press Play (or Select if Play button is focused)

**Advantages**:
- Works for any content type (movies, episodes, etc.)
- Leverages Emby's search functionality
- Relatively reliable if title matches exactly

**Disadvantages**:
- Slow (~10-15 seconds total)
- Fragile if UI layout changes
- Requires exact title match
- Multiple search results require guessing which one is correct

### Approach 2: Library Browse Navigation

**Sequence**:
1. Launch Emby channel
2. Navigate to "Movies" or "TV Shows" library (Left/Right)
3. Press Down to enter library
4. Navigate grid to specific item (complex - requires knowing position)
5. Press Select to open details
6. Press Play

**Advantages**:
- More visual confirmation along the way

**Disadvantages**:
- Very fragile (depends on library organization)
- Requires knowing grid position of item
- Slower than search
- Changes when new content added

**Verdict**: Use **Approach 1 (Search-Based)** as primary method.

---

## Implementation Model

### Kotlin Data Structures

```kotlin
sealed class RokuAction {
    data class Press(val key: RokuKey, val count: Int = 1) : RokuAction()
    data class Wait(val milliseconds: Long) : RokuAction()
    data class Launch(val channelId: String) : RokuAction()
    data class Type(val text: String) : RokuAction()
}

enum class RokuKey {
    HOME, UP, DOWN, LEFT, RIGHT, SELECT, BACK, BACKSPACE,
    PLAY, PAUSE, REV, FWD,
    INSTANT_REPLAY, INFO,
    SEARCH, ENTER
}

data class EmbyMediaContent(
    // ... existing Emby fields ...

    // Action sequence for Roku playback (since deep linking doesn't work)
    val searchTitle: String,                 // Title to search for
    val rokuActionSequence: List<RokuAction>? = null,  // Custom sequence
    val useDefaultSequence: Boolean = true,  // Use default search sequence

    // Roku device info
    val rokuDeviceIp: String? = null,
    val embyChannelId: String? = null
) : MediaContent
```

### Sequence Executor

```kotlin
class RokuActionSequenceExecutor(
    private val httpClient: OkHttpClient
) {
    companion object {
        private val logger = LoggerFactory.getLogger(RokuActionSequenceExecutor::class.java)
    }

    /**
     * Executes a sequence of Roku actions
     */
    fun execute(deviceIp: String, actions: List<RokuAction>) {
        logger.info("Executing Roku action sequence with {} steps", actions.size)

        actions.forEachIndexed { index, action ->
            logger.debug("Step {}/{}: {}", index + 1, actions.size, action)

            when (action) {
                is RokuAction.Launch -> launch(deviceIp, action.channelId)
                is RokuAction.Press -> repeat(action.count) { press(deviceIp, action.key) }
                is RokuAction.Wait -> Thread.sleep(action.milliseconds)
                is RokuAction.Type -> type(deviceIp, action.text)
            }
        }

        logger.info("Roku action sequence completed")
    }

    private fun launch(deviceIp: String, channelId: String) {
        val url = "http://$deviceIp:8060/launch/$channelId"
        sendEcpCommand(url)
    }

    private fun press(deviceIp: String, key: RokuKey) {
        val keyName = when (key) {
            RokuKey.HOME -> "Home"
            RokuKey.UP -> "Up"
            RokuKey.DOWN -> "Down"
            RokuKey.LEFT -> "Left"
            RokuKey.RIGHT -> "Right"
            RokuKey.SELECT -> "Select"
            RokuKey.BACK -> "Back"
            RokuKey.BACKSPACE -> "Backspace"
            RokuKey.PLAY -> "Play"
            RokuKey.PAUSE -> "Pause"
            RokuKey.REV -> "Rev"
            RokuKey.FWD -> "Fwd"
            RokuKey.INSTANT_REPLAY -> "InstantReplay"
            RokuKey.INFO -> "Info"
            RokuKey.SEARCH -> "Search"
            RokuKey.ENTER -> "Enter"
        }

        val url = "http://$deviceIp:8060/keypress/$keyName"
        sendEcpCommand(url)
        Thread.sleep(100)  // Small delay between keypresses
    }

    private fun type(deviceIp: String, text: String) {
        text.forEach { char ->
            val litCode = when {
                char.isLetter() -> "Lit_${char.uppercaseChar()}"
                char.isDigit() -> "Lit_$char"
                char == ' ' -> "Lit_%20"
                else -> {
                    logger.warn("Unsupported character for typing: {}", char)
                    return@forEach
                }
            }

            val url = "http://$deviceIp:8060/keypress/$litCode"
            sendEcpCommand(url)
            Thread.sleep(50)  // Delay between characters
        }
    }

    private fun sendEcpCommand(url: String) {
        val request = Request.Builder()
            .url(url)
            .post("".toRequestBody())
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                logger.warn("ECP command failed: {} - {}", response.code, response.message)
            }
        } catch (e: IOException) {
            logger.error("Network error sending ECP command to {}", url, e)
            throw RokuNetworkException("Failed to send ECP command", e)
        }
    }
}
```

### Default Emby Search Sequence

```kotlin
class EmbyPlugin(
    // ... existing constructor params ...
    private val rokuDeviceIp: String,
    private val embyChannelId: String,
    private val sequenceExecutor: RokuActionSequenceExecutor
) : MediaPlugin<EmbyMediaContent> {

    override fun play(contentId: String, options: Map<String, Any>) {
        val uuid = UUID.fromString(contentId)
        val content = mediaStore.getParsed(uuid, getPluginName(), getContentParser())
            ?: throw NotFoundException("Content not found: $contentId")

        logger.info("Playing Emby content via Roku action sequence: {}", content.title)

        val deviceIp = content.rokuDeviceIp ?: rokuDeviceIp
        val channelId = content.embyChannelId ?: embyChannelId

        // Build search-based action sequence
        val sequence = buildEmbySearchSequence(channelId, content.searchTitle)

        // Execute sequence
        sequenceExecutor.execute(deviceIp, sequence)
    }

    /**
     * Builds the default Emby search sequence
     *
     * TODO: Adjust based on actual testing of Emby UI behavior
     */
    private fun buildEmbySearchSequence(channelId: String, searchTitle: String): List<RokuAction> {
        return listOf(
            // 1. Launch Emby
            RokuAction.Launch(channelId),
            RokuAction.Wait(4000),  // Wait for Emby to load

            // 2. Open search
            RokuAction.Press(RokuKey.SEARCH),
            RokuAction.Wait(1500),  // Wait for search UI

            // 3. Type search query
            RokuAction.Type(searchTitle),
            RokuAction.Wait(2000),  // Wait for search results

            // 4. Navigate to first result
            // TODO: Adjust number of Down presses based on UI layout
            RokuAction.Press(RokuKey.DOWN, count = 1),
            RokuAction.Wait(500),

            // 5. Select first result
            RokuAction.Press(RokuKey.SELECT),
            RokuAction.Wait(1500),  // Wait for details page

            // 6. Play
            RokuAction.Press(RokuKey.PLAY)
        )
    }
}
```

---

## Testing and Calibration

### Interactive Testing Script

```bash
#!/bin/bash
# test-roku-sequence.sh

ROKU_IP="192.168.1.252"
EMBY_CHANNEL="44191"

echo "=== Roku Action Sequence Testing ==="
echo ""

# Step 1: Launch Emby
echo "Step 1: Launching Emby..."
curl -s -d '' "http://$ROKU_IP:8060/launch/$EMBY_CHANNEL"
echo "   Wait 4 seconds for Emby to load..."
sleep 4
read -p "   Is Emby loaded? Press Enter to continue..."

# Step 2: Open search
echo ""
echo "Step 2: Opening search..."
curl -s -d '' "http://$ROKU_IP:8060/keypress/Search"
echo "   Wait 1.5 seconds for search UI..."
sleep 1.5
read -p "   Did search UI open? Press Enter to continue..."

# Step 3: Type search query
echo ""
echo "Step 3: Typing '12 Angry Men'..."
for char in 1 2 " " A n g r y " " M e n; do
    if [ "$char" = " " ]; then
        curl -s -d '' "http://$ROKU_IP:8060/keypress/Lit_%20"
    else
        curl -s -d '' "http://$ROKU_IP:8060/keypress/Lit_${char}"
    fi
    sleep 0.05
done
echo "   Wait 2 seconds for search results..."
sleep 2
read -p "   Did search results appear? How many results? Press Enter..."

# Step 4: Navigate to first result
echo ""
echo "Step 4: Pressing Down to first result..."
read -p "   How many Down presses needed? " down_count
for i in $(seq 1 $down_count); do
    curl -s -d '' "http://$ROKU_IP:8060/keypress/Down"
    sleep 0.3
done
read -p "   Is first result highlighted? Press Enter..."

# Step 5: Select result
echo ""
echo "Step 5: Selecting result..."
curl -s -d '' "http://$ROKU_IP:8060/keypress/Select"
echo "   Wait 1.5 seconds for details page..."
sleep 1.5
read -p "   Did details page open? Press Enter..."

# Step 6: Play
echo ""
echo "Step 6: Pressing Play..."
curl -s -d '' "http://$ROKU_IP:8060/keypress/Play"
echo "   ✓ Sequence complete!"

echo ""
echo "=== Results ==="
echo "Document the following:"
echo "1. Total time from launch to playback: ___ seconds"
echo "2. Number of Down presses needed: $down_count"
echo "3. Did playback start successfully? Yes/No"
echo "4. Any issues or UI quirks observed:"
```

Make executable and run:
```bash
chmod +x test-roku-sequence.sh
./test-roku-sequence.sh
```

### Calibration Parameters

Based on testing, adjust these values in the code:

| Parameter | Default | Notes |
|-----------|---------|-------|
| Emby load time | 4000ms | Time for Emby home screen to appear |
| Search UI load time | 1500ms | Time for search interface to appear |
| Search results delay | 2000ms | Time for results to populate after typing |
| Details page load | 1500ms | Time for item details to appear |
| Down presses to first result | 1 | May vary by Emby UI version |

---

## Optimizations and Future Improvements

### 1. Adaptive Timing

Learn optimal wait times based on actual response:

```kotlin
class AdaptiveSequenceExecutor {
    private val timingHistory = mutableMapOf<String, List<Long>>()

    fun getOptimalWaitTime(stepName: String, defaultMs: Long): Long {
        val history = timingHistory[stepName] ?: return defaultMs
        return history.average().toLong()
    }

    fun recordTiming(stepName: String, actualMs: Long) {
        timingHistory.compute(stepName) { _, existing ->
            (existing ?: emptyList()) + actualMs
        }
    }
}
```

### 2. Sequence Recording

Build a UI to record user's manual navigation and generate sequence:

```kotlin
class SequenceRecorder {
    private val recordedActions = mutableListOf<RokuAction>()
    private var lastActionTime = System.currentTimeMillis()

    fun recordKeypress(key: RokuKey) {
        val now = System.currentTimeMillis()
        val waitTime = now - lastActionTime

        if (waitTime > 100) {
            recordedActions.add(RokuAction.Wait(waitTime))
        }

        recordedActions.add(RokuAction.Press(key))
        lastActionTime = now
    }

    fun getSequence(): List<RokuAction> = recordedActions.toList()
}
```

### 3. Sequence Validation

Verify sequence still works before executing:

```kotlin
fun validateSequence(deviceIp: String, sequence: List<RokuAction>): Boolean {
    // Could use Roku screen capture API or other validation
    // For now, just check device is reachable
    return isRokuReachable(deviceIp)
}
```

### 4. Fallback Strategies

If primary sequence fails, try alternatives:

```kotlin
fun playWithFallbacks(content: EmbyMediaContent) {
    try {
        // Try search-based sequence
        executeSearchSequence(content)
    } catch (e: SequenceFailureException) {
        logger.warn("Search sequence failed, trying browse method")
        try {
            executeBrowseSequence(content)
        } catch (e: SequenceFailureException) {
            logger.error("All playback methods failed")
            throw PlaybackException("Unable to play content via Roku", e)
        }
    }
}
```

---

## Known Limitations

1. **UI Changes**: Emby UI updates can break sequences
2. **Timing Variability**: Network speed, Roku model affect load times
3. **Search Ambiguity**: Multiple results require choosing first (may not be correct)
4. **No Confirmation**: Can't verify playback actually started without screen scraping
5. **Resume Position**: No way to seek to resume position via ECP

## Mitigation Strategies

| Limitation | Mitigation |
|------------|------------|
| UI changes | Version sequences, validate before release |
| Timing | Use conservative waits, add adaptive timing |
| Search ambiguity | Use full titles, include year for movies |
| No confirmation | Log sequence, rely on user feedback |
| Resume position | Accept limitation, document in UI |

---

## Next Steps

1. **Complete interactive testing** to find optimal parameters
2. **Implement RokuActionSequenceExecutor** in Kotlin
3. **Update EmbyPlugin.play()** to use sequences
4. **Add configuration** for tuning timing parameters
5. **Build sequence recorder UI** (future enhancement)
6. **Document user expectations** (10-15 second playback delay)
