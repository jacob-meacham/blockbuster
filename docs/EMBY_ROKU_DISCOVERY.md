# Emby-Roku Discovery Results

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

- [Roku External Control Protocol (ECP) Docs](https://developer.roku.com/docs/developer-program/debugging/external-control-api.md)
- [Emby API Docs](https://api.emby.media/)
- [Emby for Roku Channel](https://channelstore.roku.com/details/44191/emby)
