# Blockbuster Implementation Strategy

## Architecture Decision

**Use Emby as content source, Roku as playback device**

```
NFC Tap → Blockbuster → Emby API (search) → Roku ECP (playback) → TV
```

**Why**:
- ✅ Emby API provides rich metadata and search
- ✅ Roku ECP provides deep linking to channels
- ✅ 2-3 second playback from NFC tap (validated)
- ✅ Supports resume positions
- ✅ No fragile UI navigation required

See [SPECIFICATIONS.md](SPECIFICATIONS.md) for detailed technical specifications.

---

## Implementation Phases

### Phase 1: ✅ Core Roku Integration (COMPLETE)

**Objective**: Roku device control with channel plugins for major streaming services

**Deliverables**:
- [x] RokuPlugin architecture (device communication layer)
- [x] RokuChannelPlugin interface (channel-specific logic)
- [x] Deep linking discovery for all major channels
- [x] Channel plugins: Emby, Disney+, Netflix, HBO Max, Prime Video
- [x] Comprehensive test coverage (47 tests)
- [x] Configuration system for channel setup
- [x] Documentation of deep linking formats

**Status**: ✅ Complete - All channel plugins implemented and tested

---

### Phase 2: ⏳ Roku Database & Library Management (IN PROGRESS)

**Objective**: Store and manage Roku content in SQLite

**Tasks**:
- [ ] Design RokuMediaContent database schema
- [ ] Implement Flyway migrations for Roku tables
- [ ] Update SqliteMediaStore for RokuMediaContent
- [ ] Add LibraryResource endpoints for managing Roku content
- [ ] Test database operations with all channel types
- [ ] Update frontend to display Roku content

**Dependencies**: Phase 1 complete

**Estimated Duration**: 1-2 days

---

### Phase 3: Frontend Integration

**Objective**: Complete web interface for searching and managing content

**Tasks**:
- [ ] Update search UI to show channel-specific results
- [ ] Display manual search instructions for non-API channels
- [ ] Implement "Add to Library" functionality
- [ ] Show rich metadata (posters, ratings) for Emby
- [ ] Library view with content cards
- [ ] NFC tag assignment interface
- [ ] Test with all channel types

**Dependencies**: Phase 2 complete

**Estimated Duration**: 2-3 days

---

### Phase 4: Theater Plugin System

**Objective**: Equipment setup before playback (Harmony Hub integration)

**Tasks**:
- [ ] Design TheaterPlugin interface
- [ ] Implement TheaterPluginManager
- [ ] Create HarmonyHubTheaterPlugin
- [ ] Integrate theater setup into playback flow
- [ ] Configuration for theater equipment
- [ ] Test with real Harmony Hub

**Dependencies**: Phase 3 complete

**Estimated Duration**: 2-3 days

---

### Phase 5: NFC Infrastructure

**Objective**: Physical NFC tag programming and reading

**Tasks**:
- [ ] NFC tag URL format design
- [ ] Phone-based NFC programming guide
- [ ] QR code generation for tags
- [ ] Physical cartridge design specs
- [ ] Tag management UI
- [ ] Testing with real NFC tags

**Dependencies**: Phase 4 complete

**Estimated Duration**: 3-5 days

---

### Phase 6: Additional Media Plugins

**Objective**: Expand beyond Roku to other media services

**Tasks**:
- [ ] Spotify plugin design
- [ ] Spotify API integration
- [ ] Spotify content model
- [ ] Configuration for Spotify credentials
- [ ] Search and playback implementation
- [ ] Testing with Spotify API

**Dependencies**: Phase 5 complete

**Estimated Duration**: 2-3 days per plugin

---

## Current Status

### ✅ Completed

- Roku plugin architecture
- All major streaming channel plugins (Emby, Disney+, Netflix, HBO Max, Prime Video)
- Deep linking discovery and validation
- Comprehensive test suite
- Documentation structure
- Configuration system

### 🚧 In Progress

- Phase 2: Database schema and library management

### 📋 Next Steps

1. **Immediate**: Design and implement RokuMediaContent database schema
2. **Short-term**: Complete library management and frontend integration
3. **Medium-term**: Theater plugin system for equipment setup
4. **Long-term**: NFC infrastructure and additional media plugins

---

## Success Criteria

### Phase 1 (Complete)
- ✅ Deep linking validated for all channels
- ✅ 2-3 second playback from API call
- ✅ Channel plugins for 5 major services
- ✅ 95% test coverage

### Phase 2
- [ ] All channel content types stored in database
- [ ] CRUD operations for library management
- [ ] Migration system working
- [ ] 80%+ test coverage

### Phase 3
- [ ] Functional search across all channels
- [ ] Content can be added to library
- [ ] Library view displays all content
- [ ] Responsive design works on mobile

### Phase 4
- [ ] Theater equipment powers on before playback
- [ ] Harmony Hub integration works
- [ ] Configuration documented
- [ ] Error handling for offline equipment

### Phase 5
- [ ] NFC tags programmable via phone
- [ ] QR codes generated
- [ ] Tags trigger playback
- [ ] Physical cartridges designed

### Phase 6
- [ ] Additional plugins working
- [ ] Multi-plugin search aggregation
- [ ] Consistent UX across plugins

---

## Risk Mitigation

### Technical Risks

| Risk | Mitigation | Status |
|------|------------|--------|
| Roku deep linking fails | Validated all formats with real testing | ✅ Resolved |
| Channel APIs unavailable | Return manual search instructions | ✅ Implemented |
| Database migrations fail | Use Flyway with rollback support | Planned |
| Frontend state complexity | Keep it simple, avoid Redux | Planned |
| NFC compatibility | Test with multiple phones | Planned |

### User Experience Risks

| Risk | Mitigation | Status |
|------|------------|--------|
| Too complex to set up | Detailed setup guides | In progress |
| Slow playback | 2-3 second target validated | ✅ Achieved |
| Tags don't work | Multiple programming methods | Planned |
| Physical design poor | Iterate on prototypes | Planned |

---

## Development Guidelines

### Before Starting Each Phase

1. Read [docs/agents/CONSTITUTION.md](../agents/CONSTITUTION.md)
2. Review [SPECIFICATIONS.md](SPECIFICATIONS.md) for technical details
3. Create tasks in todo list
4. Write tests first when possible

### After Completing Code

1. **ALWAYS run**: `./gradlew build`
2. **ALWAYS run**: `./gradlew test`
3. Fix any failures immediately
4. Update documentation
5. Mark phase tasks as complete

### Quality Standards

- Unit tests required for all new code
- Test coverage: 80%+ minimum
- No `!!` operators in Kotlin
- All public APIs have KDoc
- Error handling comprehensive

---

## References

- [System Specifications](SPECIFICATIONS.md) - Deep technical details
- [Constitution](../agents/CONSTITUTION.md) - Code quality standards
- [Channel Deep Linking Discovery](../CHANNEL_DEEP_LINKING_DISCOVERY.md) - Roku testing results
- [Emby Roku Discovery](../EMBY_ROKU_DISCOVERY.md) - Emby deep linking details
- [Roku Deep Linking](../roku_deep_linking.md) - ECP protocol reference
