# Channel Deep Linking Discovery Results

## Testing Setup

**Roku Device**: 192.168.1.252
**Date**: 2026-01-31

## Channel IDs Discovered

| Channel | Channel ID | Version |
|---------|------------|---------|
| **Emby** | 44191 | 4.1.54 |
| **Disney+** | 291097 | 1.57.2026010600 |
| **Netflix** | 12 | 5.2.130079005 |
| **Prime Video** | 13 | 15.4.2025110415 |
| **HBO Max** | 61322 | 59.14.1 |

---

## Summary Table

| Channel | Deep Linking | Format | Notes |
|---------|--------------|--------|-------|
| **Emby** | ✅ Full | Custom: `Command=PlayNow&ItemIds={id}` | Undocumented, discovered via web app |
| **Disney+** | ⚠️ Hybrid | Standard + SELECT press | Needs profile selection |
| **Netflix** | ⚠️ Hybrid | Standard + PLAY press | Goes to selection page, needs PLAY |
| **HBO Max** | ⚠️ Hybrid | Standard + SELECT press | Needs profile selection |
| **Prime Video** | ⚠️ Hybrid | Standard + SELECT press | ASIN format, needs profile selection |

---

## Disney+ (Channel ID: 291097) ✅ VALIDATED

### Test Content
- **URL**: https://www.disneyplus.com/play/f63db666-b097-4c61-99c1-b778de2d4ae1
- **Content ID**: `f63db666-b097-4c61-99c1-b778de2d4ae1`
- **Type**: Movie

### Working Format

```bash
curl -X POST "http://192.168.1.252:8060/launch/291097?contentId=f63db666-b097-4c61-99c1-b778de2d4ae1&mediaType=movie"
```

**Result**: ✅ **SUCCESS** - Content launched and played immediately!

### Parameters

- **contentId**: Required, standard Roku parameter
- **mediaType**: Required, value: `movie` (standard Roku mediaType)

### Implementation

Disney+ uses **standard Roku ECP deep linking** format as documented:
```
http://<roku-ip>:8060/launch/<channelId>?contentId=<contentId>&mediaType=<mediaType>
```

**Deep Linking Support**: ✅ **FULL** (with profile selection step)

**Recommended Approach**: Use **hybrid ActionSequence** combining deep link + profile selection:
```kotlin
RokuPlaybackCommand.ActionSequence(
    listOf(
        RokuAction.Launch(channelId = "291097", params = "contentId=$id&mediaType=$mediaType"),
        RokuAction.Wait(2000),  // Wait for profile screen
        RokuAction.Press(RokuKey.SELECT, 1)  // Select default profile
    )
)
```

**MediaType Values**:
- `movie` - ✅ Confirmed working
- `episode` - To test
- `series` - To test
- `season` - To test

---

## Emby (Channel ID: 44191) ✅ VALIDATED

### Working Format

```bash
curl -X POST "http://192.168.1.252:8060/launch/44191?Command=PlayNow&ItemIds=541"
```

**Result**: ✅ **SUCCESS** - 2-3 second playback

### Parameters

- **Command**: Required, value: `PlayNow` (NOT standard Roku parameter)
- **ItemIds**: Required, plural form (NOT standard `contentId`)
- **StartPositionTicks**: Optional, resume position in 100ns units

### Implementation

Emby uses **custom parameters** different from standard Roku ECP:
```
http://<roku-ip>:8060/launch/<channelId>?Command=PlayNow&ItemIds=<itemId>&StartPositionTicks=<ticks>
```

**Deep Linking Support**: ✅ **FULL (Custom Format)**

**Discovery**: Found by monitoring Emby web app debug logs

See [EMBY_ROKU_DISCOVERY.md](EMBY_ROKU_DISCOVERY.md) for complete details.

---

## Netflix (Channel ID: 12) ✅ VALIDATED

### Test Content
- **Movie ID**: `81444554`
- **Episode ID**: `80179766`
- **Type**: Movie and Episode

### Test Results

```bash
# Test 1: Movie - SUCCESS!
curl -X POST "http://192.168.1.252:8060/launch/12?contentId=81444554&mediaType=movie"
```
**Result**: ✅ **SUCCESS** - Movie deep linked successfully!

```bash
# Test 2: Episode - SUCCESS!
curl -X POST "http://192.168.1.252:8060/launch/12?contentId=80179766&mediaType=episode"
```
**Result**: ✅ **SUCCESS** - Episode deep linked successfully!

### Key Findings

1. **Deep linking IS fully supported** - Netflix processes `contentId` parameter correctly
2. **Standard Roku ECP format** - Uses standard `contentId` and `mediaType` parameters
3. **Content IDs from Netflix URLs work** - Extract numeric IDs from `netflix.com/watch/{id}` URLs

### Implementation

Netflix uses **standard Roku ECP deep linking** format:
```
http://<roku-ip>:8060/launch/12?contentId=<contentId>&mediaType=<mediaType>
```

**Deep Linking Support**: ✅ **FULL** (with PLAY press required)

**Content ID Format**:
- ✅ Extract numeric ID from Netflix URLs: `https://www.netflix.com/watch/{ID}`
- ✅ Works for both movies and episodes

**MediaType Values**:
- ✅ `movie` - Goes to selection page (requires PLAY)
- ✅ `episode` - Goes to selection page (requires PLAY)

**Recommended Approach**:
- Use **hybrid ActionSequence** combining deep link + PLAY press:
  ```kotlin
  RokuPlaybackCommand.ActionSequence(
      listOf(
          RokuAction.Launch(channelId = "12", params = "contentId=$contentId&mediaType=$mediaType"),
          RokuAction.Wait(2000),  // Wait for selection page
          RokuAction.Press(RokuKey.PLAY, 1)  // Start playback
      )
  )
  ```
- Extract content ID from Netflix URLs
- Total time: ~3-4 seconds (launch + PLAY)

---

## Prime Video (Channel ID: 13) ✅ VALIDATED

### Test Content
- **Movie ID**: `B0DKTFF815` (ASIN format)
- **Show ID**: `B0FQM41JFJ` (ASIN format)
- **Type**: Movie and Series

### Test Results

```bash
# Test 1: Movie - SUCCESS!
curl -X POST "http://192.168.1.252:8060/launch/13?contentId=B0DKTFF815&mediaType=movie"
```
**Result**: ✅ **SUCCESS** - Movie played with profile selection!

```bash
# Test 2: Series - SUCCESS with smart behavior!
curl -X POST "http://192.168.1.252:8060/launch/13?contentId=B0FQM41JFJ&mediaType=series"
```
**Result**: ✅ **SUCCESS** - Auto-played S1E1 with profile selection!

### Key Findings

1. **Deep linking IS fully supported** - Prime Video processes `contentId` parameter correctly
2. **Auto-play confirmed** - Content starts playing immediately after profile selection
3. **Profile selection required** - Shows profile selection screen before playback (like HBO Max)
4. **Smart series handling** - When launching a series, automatically plays S1E1
5. **ASIN format works** - Amazon Standard Identification Numbers (B0...) work as contentId

### Implementation

Prime Video uses **standard Roku ECP format** + **profile selection**:
```
http://<roku-ip>:8060/launch/13?contentId=<contentId>&mediaType=<mediaType>
```

**Deep Linking Support**: ✅ **FULL** (with profile selection step)

**Content ID Format**:
- ✅ Use Amazon ASIN format (starts with "B0...")
- ✅ Works for both movies and series

**MediaType Values**:
- ✅ `movie` - Plays the movie
- ✅ `series` - Intelligently plays S1E1 of the series

**Recommended Approach**:
- Use **hybrid ActionSequence** combining deep link + profile selection:
  ```kotlin
  RokuPlaybackCommand.ActionSequence(
      listOf(
          RokuAction.Launch(channelId = "13", params = "contentId=$asin&mediaType=$type"),
          RokuAction.Wait(2000),  // Wait for profile screen
          RokuAction.Press(RokuKey.SELECT, 1)  // Select default profile
      )
  )
  ```
- Use Amazon ASIN as contentId
- Total time: ~3-4 seconds (launch + profile select + auto-play)

---

## HBO Max (Channel ID: 61322) ✅ WORKING

### Test Content
- **Failed URL**: https://play.hbomax.com/movie/7a7a03ca-dd3a-4e62-9e43-e845f338f85e
- **Working URL**: https://play.hbomax.com/video/watch/bd43b2a4-1639-4197-96d4-2ec14eb45e9e/b42d9d8f-71ca-40e2-8f88-2abe03ff9579
- **Working Content ID**: `bd43b2a4-1639-4197-96d4-2ec14eb45e9e` (first ID from video URL)
- **Type**: Movie

### Test Results

```bash
# Test 1: Failed - Movie URL format (invalid ID)
curl -X POST "http://192.168.1.252:8060/launch/61322?contentId=7a7a03ca-dd3a-4e62-9e43-e845f338f85e&mediaType=movie"
```
**Result**: ❌ Invalid ID screen (but deep linking is processed!)

```bash
# Test 2: SUCCESS - Video URL format
curl -X POST "http://192.168.1.252:8060/launch/61322?contentId=bd43b2a4-1639-4197-96d4-2ec14eb45e9e&mediaType=movie"
```
**Result**: ✅ **SUCCESS** - Auto-plays immediately!

### Key Findings

1. **Deep linking IS fully supported** - HBO Max processes the `contentId` parameter correctly
2. **Auto-play confirmed** - Content starts playing immediately (like Disney+)
3. **Profile selection required** - Shows profile selection screen before playback
4. **Content ID format matters** - Use the first ID from `/video/watch/{id1}/{id2}` URLs
5. **Movie IDs from `/movie/{id}` URLs don't work** - May be different ID format or region-specific

### Implementation

HBO Max uses **standard Roku ECP format** + **profile selection**:
```
http://<roku-ip>:8060/launch/61322?contentId=<contentId>&mediaType=movie
```

**Deep Linking Support**: ✅ **FULL** (with profile selection step)

**Content ID Format**:
- ✅ Use first ID from: `https://play.hbomax.com/video/watch/{ID1}/{ID2}`
- ❌ Don't use ID from: `https://play.hbomax.com/movie/{ID}` (may be region-specific)

**Recommended Approach**:
- Use **hybrid ActionSequence** combining deep link + profile selection:
  ```kotlin
  RokuPlaybackCommand.ActionSequence(
      listOf(
          RokuAction.Launch(channelId = "61322", params = "contentId=$id&mediaType=movie"),
          RokuAction.Wait(2000),  // Wait for profile screen
          RokuAction.Press(RokuKey.SELECT, 1)  // Select default profile
      )
  )
  ```
- Extract content ID from HBO Max video URLs (first UUID in path)
- Total time: ~3-4 seconds (launch + profile select + auto-play)

---

## Next Steps

### For Channels with Standard Deep Linking (Disney+)
1. ✅ Implement `RokuChannelPlugin` with standard ECP DeepLink format
2. Test with different mediaTypes (episode, series, etc.)
3. No search capability (would require Disney+ API access)

### For Channels with Custom Deep Linking (Emby)
1. ✅ Implement `RokuChannelPlugin` with custom parameter format
2. ✅ Search via Emby API
3. ✅ Resume position support

### For Channels Without Deep Linking (TBD)
1. Design Roku action sequences for UI navigation
2. Implement `RokuChannelPlugin` with ActionSequence command
3. Test and calibrate timing

## References

- [Roku Deep Linking Documentation](roku_deep_linking.md)
- [Emby Discovery Results](EMBY_ROKU_DISCOVERY.md)
- Standard ECP Format: `http://<ip>:8060/launch/<channelId>?contentId=<id>&mediaType=<type>`
