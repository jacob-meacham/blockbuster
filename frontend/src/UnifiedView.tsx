import React from 'react'
import {
  Box,
  TextField,
  Typography,
  Container,
  InputAdornment,
  Skeleton,
  ToggleButton,
  ToggleButtonGroup,
  Snackbar,
  Alert,
  Dialog,
  DialogTitle,
  DialogContent,
  IconButton,
  List,
  ListItem,
  ListItemText,
  Chip,
  Button
} from '@mui/material'
import SearchIcon from '@mui/icons-material/Search'
import HelpOutlineIcon from '@mui/icons-material/HelpOutline'
import CloseIcon from '@mui/icons-material/Close'
import ListAltIcon from '@mui/icons-material/ListAlt'
import { Link as RouterLink } from 'react-router-dom'
import { useSearch } from './hooks/useSearch'
import { useLibrary } from './hooks/useLibrary'
import { fetchPlugins, fetchChannels, resolvePlayUrl, fetchAuthStatus } from './api'
import type { AuthStatus } from './api'
import { MediaCard } from './components/MediaCard'
import type { PluginInfo, ChannelInfo, SearchResult } from './types'
import { getSourceName, getSourceColor, sourceColors } from './types'

async function copyToClipboard(text: string): Promise<void> {
  if (navigator.clipboard?.writeText) {
    return navigator.clipboard.writeText(text)
  }
  // Fallback for non-secure contexts (HTTP)
  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.style.position = 'fixed'
  textarea.style.opacity = '0'
  document.body.appendChild(textarea)
  textarea.select()
  document.execCommand('copy')
  document.body.removeChild(textarea)
}

export function UnifiedView() {
  const search = useSearch()
  const library = useLibrary()
  const [plugins, setPlugins] = React.useState<PluginInfo[]>([])
  const [channels, setChannels] = React.useState<ChannelInfo[]>([])
  const [manualDialogOpen, setManualDialogOpen] = React.useState(false)
  const [snackbar, setSnackbar] = React.useState<{
    open: boolean
    message: string
    severity: 'success' | 'error'
  }>({ open: false, message: '', severity: 'success' })
  const [authStatus, setAuthStatus] = React.useState<AuthStatus | null>(null)

  React.useEffect(() => {
    if (!search.selectedPlugin) {
      setAuthStatus(null)
      return
    }
    fetchAuthStatus(search.selectedPlugin).then(setAuthStatus).catch(() => setAuthStatus(null))
  }, [search.selectedPlugin])

  React.useEffect(() => {
    fetchPlugins().then(async (p) => {
      setPlugins(p)
      // Default to first plugin if no plugin selected yet
      if (p.length > 0 && search.selectedPlugin === '') {
        search.setSelectedPlugin(p[0].name)
      }
      const allChannels = (await Promise.all(
        p.map(plugin => fetchChannels(plugin.name))
      )).flat()
      setChannels(allChannels)
    }).catch(console.error)
  }, [])

  const isSearching = search.query.length >= 2
  const isLoading = isSearching ? search.loading : library.loading

  // Deduplicate search results by dedupKey, preferring plugin results over brave
  const deduplicatedResults = React.useMemo(() => {
    const byKey = new Map<string, number>()
    search.results.forEach((result, idx) => {
      const key = result.dedupKey
      if (!key) return
      const existing = byKey.get(key)
      if (existing === undefined) {
        byKey.set(key, idx)
      } else if (!result.url && search.results[existing].url) {
        // Prefer plugin result (no url) over brave result (has url)
        byKey.set(key, idx)
      }
    })
    const keepIndices = new Set(byKey.values())
    return search.results.filter((result, idx) => {
      if (!result.dedupKey) return true
      return keepIndices.has(idx)
    })
  }, [search.results])

  async function handleCardClick(result: SearchResult) {
    // Manual search tile
    if (result.content.contentId === 'MANUAL_SEARCH_TILE') {
      setManualDialogOpen(true)
      return
    }

    // Manual instruction (not clickable) - check via Roku-specific metadata
    if (result.content.contentId === 'MANUAL_SEARCH_REQUIRED') {
      return
    }
    if ('metadata' in result.content && (result.content as any).metadata?.instructions) {
      return
    }

    const inLibrary = library.isInLibrary(result.plugin, result.content)

    if (inLibrary) {
      // Copy existing play URL
      const libItem = library.getLibraryItem(result.plugin, result.content)
      if (libItem?.playUrl) {
        try {
          await copyToClipboard(resolvePlayUrl(libItem.playUrl))
          setSnackbar({ open: true, message: 'Copied play URL to clipboard', severity: 'success' })
        } catch {
          setSnackbar({ open: true, message: 'Failed to copy URL', severity: 'error' })
        }
      }
    } else {
      // Add to library
      try {
        const url = await library.addItem(result)
        await copyToClipboard(resolvePlayUrl(url))
        setSnackbar({ open: true, message: 'Added to library — play URL copied', severity: 'success' })
      } catch (e: unknown) {
        const message = e instanceof Error ? e.message : String(e)
        setSnackbar({ open: true, message: `Error: ${message}`, severity: 'error' })
      }
    }
  }

  function handleLibraryCardClick(item: { playUrl: string }) {
    if (item.playUrl) {
      copyToClipboard(resolvePlayUrl(item.playUrl)).then(() => {
        setSnackbar({ open: true, message: 'Copied play URL to clipboard', severity: 'success' })
      }).catch(() => {
        setSnackbar({ open: true, message: 'Failed to copy URL', severity: 'error' })
      })
    }
  }

  return (
    <Box sx={{
      minHeight: '100vh',
      background: 'linear-gradient(to bottom, #141414 0%, #000000 100%)',
      pt: 4,
      pb: 8
    }}>
      <Container maxWidth="lg">
        <Box sx={{ display: 'flex', justifyContent: 'flex-end', mb: -2 }}>
          <IconButton
            component={RouterLink}
            to="/library"
            sx={{ color: '#999', '&:hover': { color: '#fff' } }}
            title="Library"
          >
            <ListAltIcon />
          </IconButton>
        </Box>

        {/* Hero Search */}
        <Box sx={{ mb: 6, textAlign: 'center' }}>
          <Typography
            variant="h2"
            sx={{
              fontWeight: 700,
              color: '#fff',
              mb: 1,
              fontSize: { xs: '2rem', md: '3rem' }
            }}
          >
            Blockbuster
          </Typography>
          <Typography variant="subtitle1" sx={{ color: '#999', mb: 4 }}>
            Search across all your streaming services
          </Typography>

          {/* Plugin Selector */}
          {plugins.length > 0 && (
            <Box sx={{ display: 'flex', justifyContent: 'center', mb: 3 }}>
              <ToggleButtonGroup
                value={search.selectedPlugin}
                exclusive
                onChange={(_, value) => value && search.setSelectedPlugin(value)}
                sx={{
                  '& .MuiToggleButton-root': {
                    color: '#999',
                    borderColor: '#333',
                    textTransform: 'none',
                    px: 3,
                    '&.Mui-selected': {
                      bgcolor: '#E50914',
                      color: '#fff',
                      borderColor: '#E50914',
                      '&:hover': {
                        bgcolor: '#B8070F'
                      }
                    },
                    '&:hover': {
                      bgcolor: 'rgba(255,255,255,0.05)'
                    }
                  }
                }}
              >
                {plugins.map((plugin) => (
                  <ToggleButton key={plugin.name} value={plugin.name}>
                    {plugin.name.charAt(0).toUpperCase() + plugin.name.slice(1)}
                  </ToggleButton>
                ))}
              </ToggleButtonGroup>
            </Box>
          )}

          {authStatus?.available && !authStatus.authenticated ? (
            <Box sx={{ py: 4 }}>
              <Typography variant="body1" sx={{ color: '#999', mb: 2 }}>
                Connect your {search.selectedPlugin} account to search and play
              </Typography>
              <Button
                variant="contained"
                href={`/auth/${search.selectedPlugin}`}
                sx={{
                  bgcolor: sourceColors[search.selectedPlugin.charAt(0).toUpperCase() + search.selectedPlugin.slice(1)] || '#E50914',
                  color: '#fff',
                  textTransform: 'none',
                  fontWeight: 600,
                  px: 4,
                  py: 1.5,
                  fontSize: '1rem',
                  '&:hover': {
                    bgcolor: sourceColors[search.selectedPlugin.charAt(0).toUpperCase() + search.selectedPlugin.slice(1)] || '#B8070F',
                    filter: 'brightness(0.85)'
                  }
                }}
              >
                Connect {search.selectedPlugin.charAt(0).toUpperCase() + search.selectedPlugin.slice(1)}
              </Button>
            </Box>
          ) : (
            <TextField
              fullWidth
              value={search.query}
              onChange={(e) => search.updateQuery(e.target.value)}
              placeholder="Search for movies, shows, or paste a URL..."
              variant="outlined"
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <SearchIcon sx={{ color: '#999', fontSize: 28 }} />
                  </InputAdornment>
                ),
                sx: {
                  background: 'rgba(255, 255, 255, 0.1)',
                  borderRadius: 2,
                  '& .MuiOutlinedInput-notchedOutline': {
                    border: '2px solid rgba(255, 255, 255, 0.2)'
                  },
                  '&:hover .MuiOutlinedInput-notchedOutline': {
                    border: '2px solid rgba(255, 255, 255, 0.4)'
                  },
                  '&.Mui-focused .MuiOutlinedInput-notchedOutline': {
                    border: '2px solid #E50914'
                  },
                  color: '#fff',
                  fontSize: '1.1rem',
                  py: 1
                }
              }}
            />
          )}
        </Box>

        {/* Section Header */}
        {!isLoading && (
          <Typography
            variant="h5"
            sx={{ color: '#fff', fontWeight: 600, mb: 3 }}
          >
            {isSearching ? 'Search Results' : 'Your Library'}
          </Typography>
        )}

        {/* Loading Skeletons */}
        {isLoading && (
          <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))', gap: 1.5 }}>
            {[1, 2, 3, 4, 5, 6, 7, 8].map((i) => (
              <Skeleton
                key={i}
                variant="rectangular"
                height={72}
                sx={{ bgcolor: 'rgba(255,255,255,0.1)', borderRadius: 1.5 }}
              />
            ))}
          </Box>
        )}

        {/* Search Results */}
        {!isLoading && isSearching && deduplicatedResults.length > 0 && (
          <Box sx={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))',
            gap: 1.5
          }}>
            {deduplicatedResults.map((result, idx) => (
              <MediaCard
                key={`${result.plugin}-${result.content.contentId}-${idx}`}
                title={result.title}
                subtitle={getSourceName(result.plugin, result.content)}
                description={result.description}
                mediaType={result.content.mediaType}
                instructions={'metadata' in result.content ? (result.content as any).metadata?.instructions : undefined}
                accentColor={getSourceColor(result.plugin, result.content)}
                isInLibrary={library.isInLibrary(result.plugin, result.content)}
                isManualSearchTile={result.content.contentId === 'MANUAL_SEARCH_TILE'}
                onClick={() => handleCardClick(result)}
                index={idx}
              />
            ))}
          </Box>
        )}

        {/* Empty Search State */}
        {!isLoading && isSearching && deduplicatedResults.length === 0 && (
          <Box sx={{ textAlign: 'center', py: 8 }}>
            <Typography variant="h5" sx={{ color: '#666', mb: 2 }}>
              No results found
            </Typography>
            <Typography variant="body2" sx={{ color: '#999' }}>
              Try a different search term or paste a URL from your streaming service
            </Typography>
          </Box>
        )}

        {/* Library Items (when not searching) */}
        {!isLoading && !isSearching && library.libraryItems.length > 0 && (
          <Box sx={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))',
            gap: 1.5
          }}>
            {library.libraryItems.map((item, idx) => (
              <MediaCard
                key={item.uuid}
                title={item.title || item.parsedContent?.title || item.parsedContent?.contentId || 'Untitled'}
                subtitle={getSourceName(item.plugin, item.parsedContent)}
                description={'metadata' in (item.parsedContent || {}) ? ((item.parsedContent as any)?.metadata?.description || (item.parsedContent as any)?.metadata?.overview) : undefined}
                mediaType={item.parsedContent?.mediaType}
                accentColor={getSourceColor(item.plugin, item.parsedContent)}
                isInLibrary={true}
                onClick={() => handleLibraryCardClick(item)}
                index={idx}
              />
            ))}
          </Box>
        )}

        {/* Empty Library State */}
        {!isLoading && !isSearching && library.libraryItems.length === 0 && (
          <Box sx={{ textAlign: 'center', py: 8 }}>
            <SearchIcon sx={{ fontSize: 80, color: '#333', mb: 2 }} />
            <Typography variant="h5" sx={{ color: '#666', mb: 1 }}>
              Your library is empty
            </Typography>
            <Typography variant="body2" sx={{ color: '#999', maxWidth: 500, mx: 'auto' }}>
              Search for movies and TV shows to add them to your library.
              Each item gets a unique play URL you can write to an NFC tag.
            </Typography>
          </Box>
        )}

        {/* Snackbar */}
        <Snackbar
          open={snackbar.open}
          autoHideDuration={3000}
          onClose={() => setSnackbar(s => ({ ...s, open: false }))}
          anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
        >
          <Alert
            onClose={() => setSnackbar(s => ({ ...s, open: false }))}
            severity={snackbar.severity}
            variant="filled"
          >
            {snackbar.message}
          </Alert>
        </Snackbar>

        {/* Manual Search Dialog */}
        <Dialog
          open={manualDialogOpen}
          onClose={() => setManualDialogOpen(false)}
          maxWidth="md"
          fullWidth
        >
          <DialogTitle sx={{ bgcolor: '#1a1a1a', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <HelpOutlineIcon />
              <span>Manual Search Instructions</span>
            </Box>
            <IconButton onClick={() => setManualDialogOpen(false)} sx={{ color: '#999' }}>
              <CloseIcon />
            </IconButton>
          </DialogTitle>
          <DialogContent sx={{ bgcolor: '#1a1a1a', color: '#fff', pt: 3 }}>
            <Typography variant="body2" sx={{ color: '#999', mb: 3 }}>
              Can't find what you're looking for? Search manually on your streaming services:
            </Typography>
            <List>
              {channels.map((channel) => (
                <ListItem
                  key={channel.channelId}
                  sx={{
                    bgcolor: '#252525',
                    borderRadius: 1,
                    mb: 2,
                    flexDirection: 'column',
                    alignItems: 'flex-start'
                  }}
                >
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
                    <Chip
                      label={channel.channelName}
                      size="small"
                      sx={{
                        bgcolor: sourceColors[channel.channelName] || '#666',
                        color: '#fff',
                        fontWeight: 600
                      }}
                    />
                  </Box>
                  <ListItemText
                    primary={
                      <Typography
                        variant="body2"
                        sx={{
                          color: '#ccc',
                          whiteSpace: 'pre-line',
                          fontFamily: 'monospace',
                          fontSize: '0.85rem'
                        }}
                      >
                        1. Open {channel.channelName} at: {channel.searchUrl}
{'\n'}2. Search for the title
{'\n'}3. Note the URL from your browser
{'\n'}4. Paste the URL in the search box above
                      </Typography>
                    }
                  />
                </ListItem>
              ))}
            </List>
          </DialogContent>
        </Dialog>
      </Container>
    </Box>
  )
}
