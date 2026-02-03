import React from 'react'
import {
  Box,
  TextField,
  Card,
  CardContent,
  Typography,
  Chip,
  Container,
  InputAdornment,
  Skeleton,
  Fade,
  Zoom,
  Dialog,
  DialogTitle,
  DialogContent,
  IconButton,
  List,
  ListItem,
  ListItemText,
  ToggleButton,
  ToggleButtonGroup,
  Snackbar,
  Alert
} from '@mui/material'
import { useSearchParams } from 'react-router-dom'
import SearchIcon from '@mui/icons-material/Search'
import PlayArrowIcon from '@mui/icons-material/PlayArrow'
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined'
import HelpOutlineIcon from '@mui/icons-material/HelpOutline'
import CloseIcon from '@mui/icons-material/Close'

type RokuMediaMetadata = {
  description?: string;
  overview?: string;
  imageUrl?: string;
  searchUrl?: string;
  originalUrl?: string;
  resumePositionTicks?: number;
  runtimeTicks?: number;
  playedPercentage?: number;
  isFavorite?: boolean;
  communityRating?: number;
  officialRating?: string;
  genres?: string[];
  serverId?: string;
  itemType?: string;
  seriesName?: string;
  seasonNumber?: number;
  episodeNumber?: number;
  year?: number;
  instructions?: string;
}

type RokuMediaContent = {
  channelName: string;
  channelId: string;
  contentId: string;
  title: string;
  mediaType?: string;
  metadata?: RokuMediaMetadata;
}

type SearchResult = {
  source: string;
  plugin: string;
  title: string;
  channelName?: string;
  channelId: string;
  contentId: string;
  mediaType?: string;
  url?: string;
  description?: string;
  imageUrl?: string;
  content: RokuMediaContent;
}

const channelColors: Record<string, string> = {
  'Netflix': '#E50914',
  'Disney+': '#113CCF',
  'HBO Max': '#9D34DA',
  'Prime Video': '#00A8E1',
  'Emby': '#52B54B'
}

const channelLogos: Record<string, string> = {
  'Netflix': 'https://cdn.worldvectorlogo.com/logos/netflix-3.svg',
  'Disney+': 'https://cdn.worldvectorlogo.com/logos/disney-plus.svg',
  'HBO Max': 'https://cdn.worldvectorlogo.com/logos/hbo-max-2.svg',
  'Prime Video': 'https://cdn.worldvectorlogo.com/logos/amazon-prime-video.svg'
}

type ChannelInfo = {
  channelId: string;
  channelName: string;
  searchUrl: string;
}

type PluginInfo = {
  name: string;
  description: string;
}

export function SearchView() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [query, setQuery] = React.useState(searchParams.get('q') || '')
  const [selectedPlugin, setSelectedPlugin] = React.useState<string>('all')
  const [results, setResults] = React.useState<SearchResult[]>([])
  const [loading, setLoading] = React.useState(false)
  const [hoveredCard, setHoveredCard] = React.useState<number | null>(null)
  const [manualDialogOpen, setManualDialogOpen] = React.useState(false)
  const [snackbar, setSnackbar] = React.useState<{ open: boolean, message: string, severity: 'success' | 'error' }>({ open: false, message: '', severity: 'success' })
  const [channels, setChannels] = React.useState<ChannelInfo[]>([])
  const [plugins, setPlugins] = React.useState<PluginInfo[]>([])
  const searchTimeoutRef = React.useRef<NodeJS.Timeout>()

  // Fetch channel and plugin info on mount
  React.useEffect(() => {
    fetch('/search/channels')
      .then(res => res.json())
      .then(data => setChannels(data.channels || []))
      .catch(err => console.error('Failed to fetch channels:', err))

    fetch('/search/plugins')
      .then(res => res.json())
      .then(data => setPlugins(data.plugins || []))
      .catch(err => console.error('Failed to fetch plugins:', err))
  }, [])

  // Update query when URL changes
  React.useEffect(() => {
    const q = searchParams.get('q')
    if (q && q !== query) {
      setQuery(q)
    }
  }, [searchParams, query])

  // Debounced search
  React.useEffect(() => {
    if (searchTimeoutRef.current) {
      clearTimeout(searchTimeoutRef.current)
    }

    if (query.length < 2) {
      setResults([])
      return
    }

    searchTimeoutRef.current = setTimeout(() => {
      performSearch(query)
    }, 500)

    return () => {
      if (searchTimeoutRef.current) {
        clearTimeout(searchTimeoutRef.current)
      }
    }
  }, [query, selectedPlugin])

  async function performSearch(searchQuery: string) {
    setLoading(true)
    try {
      const pluginParam = selectedPlugin !== 'all' ? `&plugin=${selectedPlugin}` : ''
      const resp = await fetch(`/search/all?q=${encodeURIComponent(searchQuery)}${pluginParam}`)
      const data = await resp.json()
      setResults(data.results || [])
    } catch (e) {
      console.error('Search failed:', e)
      setResults([])
    } finally {
      setLoading(false)
    }
  }

  async function addToLibrary(result: SearchResult) {
    try {
      const pluginName = result.plugin || 'roku'
      const resp = await fetch(`/library/${pluginName}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ content: result.content })
      })
      const data = await resp.json()
      if (resp.ok) {
        await navigator.clipboard.writeText(data.url)
        setSnackbar({ open: true, message: 'Copied play URL to clipboard', severity: 'success' })
      } else {
        setSnackbar({ open: true, message: `Failed to add: ${data.error}`, severity: 'error' })
      }
    } catch (e: unknown) {
      const message = e instanceof Error ? e.message : String(e)
      setSnackbar({ open: true, message: `Error: ${message}`, severity: 'error' })
    }
  }

  const isManualInstruction = (result: SearchResult) => {
    return result.contentId === "MANUAL_SEARCH_REQUIRED" ||
           result.content?.metadata?.instructions
  }

  const isManualSearchTile = (result: SearchResult) => {
    return result.contentId === "MANUAL_SEARCH_TILE"
  }

  function handleCardClick(result: SearchResult) {
    if (isManualSearchTile(result)) {
      setManualDialogOpen(true)
    } else if (!isManualInstruction(result)) {
      addToLibrary(result)
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
                value={selectedPlugin}
                exclusive
                onChange={(_, value) => value && setSelectedPlugin(value)}
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
                <ToggleButton value="all">All Sources</ToggleButton>
                {plugins.map((plugin) => (
                  <ToggleButton key={plugin.name} value={plugin.name}>
                    {plugin.name.charAt(0).toUpperCase() + plugin.name.slice(1)}
                  </ToggleButton>
                ))}
              </ToggleButtonGroup>
            </Box>
          )}

          <TextField
            fullWidth
            value={query}
            onChange={(e) => {
              const newQuery = e.target.value
              setQuery(newQuery)
              // Update URL with query parameter
              if (newQuery) {
                setSearchParams({ q: newQuery })
              } else {
                setSearchParams({})
              }
            }}
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
        </Box>

        {/* Loading Skeletons */}
        {loading && (
          <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: 3 }}>
            {[1, 2, 3, 4, 5, 6].map((i) => (
              <Skeleton
                key={i}
                variant="rectangular"
                height={400}
                sx={{ bgcolor: 'rgba(255,255,255,0.1)', borderRadius: 2 }}
              />
            ))}
          </Box>
        )}

        {/* Results Grid */}
        {!loading && results.length > 0 && (
          <Box sx={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))',
            gap: 3
          }}>
            {results.map((result, idx) => (
              <Zoom in key={idx} style={{ transitionDelay: `${idx * 50}ms` }}>
                <Card
                  onMouseEnter={() => setHoveredCard(idx)}
                  onMouseLeave={() => setHoveredCard(null)}
                  onClick={() => handleCardClick(result)}
                  sx={{
                    bgcolor: '#1a1a1a',
                    borderRadius: 2,
                    overflow: 'hidden',
                    cursor: isManualInstruction(result) ? 'default' : 'pointer',
                    transition: 'all 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
                    transform: hoveredCard === idx ? 'scale(1.05) translateY(-8px)' : 'scale(1)',
                    boxShadow: hoveredCard === idx
                      ? `0 20px 40px rgba(0,0,0,0.5), 0 0 0 2px ${channelColors[result.channelName || ''] || '#666'}`
                      : '0 4px 12px rgba(0,0,0,0.3)',
                    position: 'relative',
                    '&::before': {
                      content: '""',
                      position: 'absolute',
                      top: 0,
                      left: 0,
                      right: 0,
                      bottom: 0,
                      background: `linear-gradient(180deg, transparent 0%, rgba(0,0,0,0.8) 100%)`,
                      opacity: hoveredCard === idx ? 1 : 0.7,
                      transition: 'opacity 0.3s',
                      zIndex: 1,
                      pointerEvents: 'none'
                    }
                  }}
                >
                  {/* Content Image or Channel Logo/Color Bar */}
                  <Box sx={{
                    height: 270,
                    background: result.imageUrl
                      ? `url(${result.imageUrl}) center/cover no-repeat`
                      : result.channelName && channelLogos[result.channelName]
                      ? `url(${channelLogos[result.channelName]}) center/contain no-repeat, ${channelColors[result.channelName] || '#333'}`
                      : isManualSearchTile(result)
                      ? 'linear-gradient(135deg, #FF6B6B 0%, #4ECDC4 100%)'
                      : channelColors[result.channelName || ''] || 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    position: 'relative'
                  }}>
                    {/* Manual search icon */}
                    {isManualSearchTile(result) && (
                      <HelpOutlineIcon sx={{ fontSize: 80, color: '#fff', opacity: 0.9 }} />
                    )}

                    {/* Play button overlay */}
                    {!isManualInstruction(result) && !isManualSearchTile(result) && hoveredCard === idx && (
                      <Fade in>
                        <Box sx={{
                          position: 'absolute',
                          zIndex: 2,
                          bgcolor: 'rgba(0,0,0,0.8)',
                          borderRadius: '50%',
                          width: 64,
                          height: 64,
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center'
                        }}>
                          <PlayArrowIcon sx={{ fontSize: 40, color: '#fff' }} />
                        </Box>
                      </Fade>
                    )}
                  </Box>

                  <CardContent sx={{ position: 'relative', zIndex: 2, pt: 2 }}>
                    {/* Channel Badge */}
                    {result.channelName && (
                      <Chip
                        label={result.channelName}
                        size="small"
                        sx={{
                          bgcolor: channelColors[result.channelName] || '#666',
                          color: '#fff',
                          fontWeight: 600,
                          mb: 1.5,
                          fontSize: '0.75rem'
                        }}
                      />
                    )}

                    {/* Title */}
                    <Typography
                      variant="h6"
                      sx={{
                        color: '#fff',
                        fontWeight: 600,
                        mb: 1,
                        lineHeight: 1.3,
                        height: '2.6em',
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        display: '-webkit-box',
                        WebkitLineClamp: 2,
                        WebkitBoxOrient: 'vertical'
                      }}
                    >
                      {result.title}
                    </Typography>

                    {/* Description */}
                    {result.description && !isManualInstruction(result) && (
                      <Typography
                        variant="body2"
                        sx={{
                          color: '#999',
                          mb: 1.5,
                          lineHeight: 1.4,
                          height: '2.8em',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                          display: '-webkit-box',
                          WebkitLineClamp: 2,
                          WebkitBoxOrient: 'vertical'
                        }}
                      >
                        {result.description}
                      </Typography>
                    )}

                    {/* Manual Instructions */}
                    {isManualInstruction(result) && result.content?.metadata?.instructions && (
                      <Box sx={{
                        bgcolor: 'rgba(255, 152, 0, 0.1)',
                        border: '1px solid rgba(255, 152, 0, 0.3)',
                        borderRadius: 1,
                        p: 1.5,
                        mt: 1
                      }}>
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
                          <InfoOutlinedIcon sx={{ color: '#ff9800', fontSize: 20 }} />
                          <Typography variant="caption" sx={{ color: '#ff9800', fontWeight: 600 }}>
                            Manual Search Required
                          </Typography>
                        </Box>
                        <Typography
                          variant="caption"
                          sx={{
                            color: '#ccc',
                            whiteSpace: 'pre-line',
                            lineHeight: 1.4,
                            display: 'block'
                          }}
                        >
                          {result.content.metadata.instructions.slice(0, 200)}...
                        </Typography>
                      </Box>
                    )}

                    {/* Media Type */}
                    {result.mediaType && !isManualInstruction(result) && (
                      <Chip
                        label={result.mediaType.toUpperCase()}
                        size="small"
                        variant="outlined"
                        sx={{
                          color: '#999',
                          borderColor: '#333',
                          fontSize: '0.7rem',
                          height: 24
                        }}
                      />
                    )}
                  </CardContent>
                </Card>
              </Zoom>
            ))}
          </Box>
        )}

        {/* Empty State */}
        {!loading && query.length >= 2 && results.length === 0 && (
          <Box sx={{ textAlign: 'center', py: 8 }}>
            <Typography variant="h5" sx={{ color: '#666', mb: 2 }}>
              No results found
            </Typography>
            <Typography variant="body2" sx={{ color: '#999' }}>
              Try a different search term or paste a URL from your streaming service
            </Typography>
          </Box>
        )}

        {/* Initial State */}
        {!loading && query.length < 2 && (
          <Box sx={{ textAlign: 'center', py: 8 }}>
            <SearchIcon sx={{ fontSize: 80, color: '#333', mb: 2 }} />
            <Typography variant="h5" sx={{ color: '#666', mb: 1 }}>
              Start searching
            </Typography>
            <Typography variant="body2" sx={{ color: '#999', maxWidth: 500, mx: 'auto' }}>
              Search for movies and TV shows across Netflix, Disney+, HBO Max, Prime Video, and more.
              Or paste a URL to quickly add content to your library.
            </Typography>
          </Box>
        )}

        {/* Snackbar for copy/error feedback */}
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
                        bgcolor: channelColors[channel.channelName] || '#666',
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
