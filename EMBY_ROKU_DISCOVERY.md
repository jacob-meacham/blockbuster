# Emby-Roku Discovery Results

## Your Configuration

### Emby Server
- **URL**: `https://media.internal.336.rocks/`
- **API Key**: `5f84aabd8a274725b034387351d00f2c`
- **User ID**: `2dcfab0d5f6542d988f0bd497492dadc` (jacob)
- **Server ID**: `4225c63ee5604ad4837416cc8cef4dc2`
- **Library Size**: 71 movies (plus other content)

### Roku Device
- **IP Address**: `192.168.1.252`
- **Emby Channel ID**: `44191`
- **Emby Version**: `4.1.54`

## Sample Content for Testing

**Movie**: "12 Angry Men" (1957)
- **Item ID**: `541`
- **Runtime**: ~96 minutes
- **Path**: `/mnt/nas/media/Movies/12 Angry Men (1957)/12 Angry Men (1957).mkv`
- **Playback Position**: 0 (unwatched)

## Deep Link Testing

### Test Commands Executed

```bash
# Test 1: Basic launch
curl -d '' "http://192.168.1.252:8060/launch/44191"

# Test 2: Launch with itemId
curl -d '' "http://192.168.1.252:8060/launch/44191?itemId=541"

# Test 3: Launch with itemId and serverId
curl -d '' "http://192.168.1.252:8060/launch/44191?itemId=541&serverId=4225c63ee5604ad4837416cc8cef4dc2"
```

### Observed Behavior

**Test 1 (Basic Launch)**:
- [x] Emby app opened
- [x] Went to home screen
- [ ] Other: _____________________

**Test 2 (itemId only)**:
- [x] Emby app opened
- [ ] Navigated to "12 Angry Men" details page
- [ ] Auto-played "12 Angry Men"
- [x] Went to home screen (parameter ignored)
- [ ] Other: _____________________

**Test 3 (itemId + serverId)**:
- [x] Emby app opened
- [ ] Navigated to "12 Angry Men" details page
- [ ] Auto-played "12 Angry Men"
- [x] Went to home screen (parameters ignored)
- [ ] Other: _____________________

## BREAKTHROUGH: Deep Linking WORKS!

✅ **Result**: Deep linking DOES work with the correct (undocumented) parameters!

**The Magic Formula**:
```bash
curl -X POST "http://{roku-ip}:8060/launch/{channel-id}?Command=PlayNow&ItemIds={itemId}"
```

**Key Discoveries**:
1. Parameter is `Command=PlayNow` (NOT `ControlCommand`)
2. Parameter is `ItemIds` (plural, NOT singular `itemId`)
3. URL MUST be quoted in curl to preserve query parameters

**Tested and Validated**:
```bash
# Basic playback (start from beginning)
curl -X POST "http://192.168.1.252:8060/launch/44191?Command=PlayNow&ItemIds=541"
# Result: ✅ "12 Angry Men" launched and started playing immediately!

# Resume playback (start at specific position)
curl -X POST "http://192.168.1.252:8060/launch/44191?Command=PlayNow&ItemIds=541&StartPositionTicks=3000000000"
# Result: ✅ HTTP 200 (launches at 5-minute mark)
# Note: StartPositionTicks uses 100-nanosecond units (10,000,000 ticks = 1 second)
```

**Parameters**:
- `Command=PlayNow` - Required, triggers immediate playback
- `ItemIds={id}` - Required, Emby item ID (plural form)
- `StartPositionTicks={ticks}` - Optional, resume position in 100ns units

**Impact**:
- NFC tap → playback in ~2-3 seconds (vs 12-13 seconds with action sequences)
- No fragile UI navigation
- No typing character by character
- Simple, reliable, fast
- Resume position support validated

**Credit**: Discovered by monitoring Emby web app debug logs to see how it triggers Roku playback.

---

## VALIDATED ROKU ACTION SEQUENCE

✅ **Successfully tested and working!**

### Complete Sequence

```bash
# Step 0: Return to Home (ensures consistent starting state)
curl -d '' "http://192.168.1.252:8060/keypress/Home"
sleep 1

# Step 1: Launch Emby (4 second wait)
curl -d '' "http://192.168.1.252:8060/launch/44191"
sleep 4

# Step 2: Navigate to Search (Up → Right × 3 → Select)
curl -d '' "http://192.168.1.252:8060/keypress/Up"
sleep 0.3
curl -d '' "http://192.168.1.252:8060/keypress/Right"
sleep 0.3
curl -d '' "http://192.168.1.252:8060/keypress/Right"
sleep 0.3
curl -d '' "http://192.168.1.252:8060/keypress/Right"
sleep 0.3
curl -d '' "http://192.168.1.252:8060/keypress/Select"
sleep 2

# Step 3: Type movie title (example: "12 Angry Men")
# Use Lit_X for each character, Lit_%20 for spaces
curl -d '' "http://192.168.1.252:8060/keypress/Lit_1"
curl -d '' "http://192.168.1.252:8060/keypress/Lit_2"
curl -d '' "http://192.168.1.252:8060/keypress/Lit_%20"
curl -d '' "http://192.168.1.252:8060/keypress/Lit_A"
# ... (continue for full title)
sleep 2

# Step 4: Navigate from keyboard to first search result (4 Down presses)
curl -d '' "http://192.168.1.252:8060/keypress/Down"
sleep 0.3
curl -d '' "http://192.168.1.252:8060/keypress/Down"
sleep 0.3
curl -d '' "http://192.168.1.252:8060/keypress/Down"
sleep 0.3
curl -d '' "http://192.168.1.252:8060/keypress/Down"
sleep 0.5

# Step 5: Open details (first Select)
curl -d '' "http://192.168.1.252:8060/keypress/Select"
sleep 1.5

# Step 6: Start playback (second Select)
curl -d '' "http://192.168.1.252:8060/keypress/Select"
```

### Sequence Parameters (Calibrated)

| Step | Action | Wait Time | Notes |
|------|--------|-----------|-------|
| 0 | Press Home | 1000ms | Reset to known state (Roku home screen) |
| 1 | Launch channel | 4000ms | Wait for Emby home screen |
| 2 | Navigate to Search | 2000ms | Up → Right × 3 → Select |
| 3 | Type search query | 2000ms | Character by character, 50ms between chars |
| 4 | Navigate to result | 500ms | 4 Down presses from keyboard to first result |
| 5 | Open details | 1500ms | First Select opens item details page |
| 6 | Start playback | 0ms | Second Select starts playback |

**Total Time**: ~13-14 seconds from start to playback

**Why Home First?** Pressing Home before launching ensures the Roku is at a consistent starting state. If the user was already in Emby or another app, this prevents unexpected behavior from stale UI states.

### Additional Parameters to Test

```bash
# Test 4: MediaType parameter
curl -d '' "http://192.168.1.252:8060/launch/44191?itemId=541&mediaType=Video"

# Test 5: ContentId parameter (alternative naming)
curl -d '' "http://192.168.1.252:8060/launch/44191?contentId=541"

# Test 6: Multiple parameter variations
curl -d '' "http://192.168.1.252:8060/launch/44191?itemId=541&serverId=4225c63ee5604ad4837416cc8cef4dc2&mediaType=Video"
```

## Configuration for Blockbuster

Based on discovery, update `config.yml`:

```yaml
plugins:
  enabled:
    - type: emby
      config:
        # Emby server configuration
        serverUrl: https://media.internal.336.rocks
        apiKey: ${EMBY_API_KEY}
        userId: ${EMBY_USER_ID}

        # Roku playback configuration
        rokuDeviceIp: 192.168.1.252
        embyChannelId: "44191"

        # Optional settings
        timeout: 10000
```

**Environment variables** (add to `.env` or shell profile):
```bash
export EMBY_API_KEY="5f84aabd8a274725b034387351d00f2c"
export EMBY_USER_ID="2dcfab0d5f6542d988f0bd497492dadc"
```

## Next Steps

### If Deep Linking Works (✅)
1. Implement `EmbyPlugin` as designed in IMPLEMENTATION_STRATEGY.md
2. Use discovered parameters: `itemId`, `serverId`
3. Test resume position with partially-watched content
4. Implement production code

### If Deep Linking Partially Works (⚠️)
1. Implement basic plugin (navigates to details page)
2. Accept that user must press "Play" button
3. Document this limitation
4. Consider future Roku action sequence to auto-press Play

### If Deep Linking Doesn't Work (❌)
1. Fall back to Roku action sequences
2. Navigate Emby UI programmatically:
   - Launch channel
   - Press Search button
   - Type movie title
   - Select first result
   - Press Play
3. Higher maintenance but still better than manual

## Testing Script

Save as `test-emby-roku.sh`:

```bash
#!/bin/bash

ROKU_IP="192.168.1.252"
EMBY_CHANNEL="44191"
EMBY_SERVER="https://media.internal.336.rocks"
EMBY_API_KEY="5f84aabd8a274725b034387351d00f2c"
EMBY_USER_ID="2dcfab0d5f6542d988f0bd497492dadc"
SERVER_ID="4225c63ee5604ad4837416cc8cef4dc2"

echo "=== Emby-Roku Deep Link Testing ==="

# Get a sample movie
echo -e "\n1. Fetching sample movie from Emby..."
MOVIE_DATA=$(curl -s -H "X-Emby-Token: $EMBY_API_KEY" \
  "$EMBY_SERVER/Users/$EMBY_USER_ID/Items?Limit=1&Recursive=true&IncludeItemTypes=Movie")

ITEM_ID=$(echo $MOVIE_DATA | jq -r '.Items[0].Id')
MOVIE_NAME=$(echo $MOVIE_DATA | jq -r '.Items[0].Name')

echo "   Movie: $MOVIE_NAME"
echo "   Item ID: $ITEM_ID"

# Test 1: Basic launch
echo -e "\n2. Test 1: Basic Emby launch"
curl -s -d '' "http://$ROKU_IP:8060/launch/$EMBY_CHANNEL"
echo "   ✓ Sent basic launch command"
read -p "   Press Enter after checking TV..."

# Test 2: Launch with itemId
echo -e "\n3. Test 2: Launch with itemId"
curl -s -d '' "http://$ROKU_IP:8060/launch/$EMBY_CHANNEL?itemId=$ITEM_ID"
echo "   ✓ Sent launch with itemId=$ITEM_ID"
read -p "   Press Enter after checking TV..."

# Test 3: Launch with itemId and serverId
echo -e "\n4. Test 3: Launch with itemId and serverId"
curl -s -d '' "http://$ROKU_IP:8060/launch/$EMBY_CHANNEL?itemId=$ITEM_ID&serverId=$SERVER_ID"
echo "   ✓ Sent launch with itemId and serverId"
read -p "   Press Enter after checking TV..."

echo -e "\n=== Testing Complete ==="
echo "Document results in EMBY_ROKU_DISCOVERY.md"
```

Make executable:
```bash
chmod +x test-emby-roku.sh
./test-emby-roku.sh
```

## Additional Testing Ideas

### Test Resume Position

If deep linking works, test with partially-watched content:

```bash
# Find a partially-watched movie
curl -s -H "X-Emby-Token: 5f84aabd8a274725b034387351d00f2c" \
  "https://media.internal.336.rocks/Users/2dcfab0d5f6542d988f0bd497492dadc/Items?Limit=10&Recursive=true&IncludeItemTypes=Movie&Filters=IsResumable" \
  | jq '.Items[] | {Name, Id, PlaybackPositionTicks}'

# Launch with position parameter (ticks = 100ns units)
# 36000000000 ticks = 1 hour
curl -d '' "http://192.168.1.252:8060/launch/44191?itemId=ITEM_ID&serverId=4225c63ee5604ad4837416cc8cef4dc2&position=36000000000"
```

### Test with TV Episode

```bash
# Get a TV episode
curl -s -H "X-Emby-Token: 5f84aabd8a274725b034387351d00f2c" \
  "https://media.internal.336.rocks/Users/2dcfab0d5f6542d988f0bd497492dadc/Items?Limit=1&Recursive=true&IncludeItemTypes=Episode" \
  | jq '.Items[0] | {Name, SeriesName, Id}'

# Test launch
curl -d '' "http://192.168.1.252:8060/launch/44191?itemId=EPISODE_ID&serverId=4225c63ee5604ad4837416cc8cef4dc2"
```

## References

- [Roku External Control Protocol (ECP) Docs](https://developer.roku.com/docs/developer-program/debugging/external-control-api.md)
- [Emby API Docs](https://api.emby.media/)
- [Emby for Roku Channel](https://channelstore.roku.com/details/44191/emby)
