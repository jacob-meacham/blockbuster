import { useEffect, useState } from 'react'
import { useParams, Link as RouterLink } from 'react-router-dom'
import {
  Box, Container, Typography, Button, CircularProgress,
  Alert, Chip, IconButton, Tooltip
} from '@mui/material'
import PlayArrowIcon from '@mui/icons-material/PlayArrow'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import ContentCopyIcon from '@mui/icons-material/ContentCopy'
import CheckIcon from '@mui/icons-material/Check'
import { getLibraryItem, triggerPlay, resolvePlayUrl } from './api'
import type { LibraryItem, RokuMediaContent, RokuMediaMetadata } from './types'
import { getSourceName, getSourceColor } from './types'

type PlayState = 'idle' | 'playing' | 'success' | 'error'

export function PlayView() {
  const { uuid } = useParams<{ uuid: string }>()
  const [item, setItem] = useState<LibraryItem | null>(null)
  const [loading, setLoading] = useState(true)
  const [notFound, setNotFound] = useState(false)
  const [playState, setPlayState] = useState<PlayState>('idle')
  const [errorMessage, setErrorMessage] = useState('')
  const [copied, setCopied] = useState(false)

  useEffect(() => {
    if (!uuid) return
    setLoading(true)
    getLibraryItem(uuid)
      .then(result => {
        if (!result) setNotFound(true)
        else setItem(result)
      })
      .catch(() => setNotFound(true))
      .finally(() => setLoading(false))
  }, [uuid])

  async function handlePlay() {
    if (!uuid) return
    setPlayState('playing')
    setErrorMessage('')
    try {
      await triggerPlay(uuid)
      setPlayState('success')
    } catch (e: unknown) {
      setPlayState('error')
      setErrorMessage(e instanceof Error ? e.message : 'Playback failed')
    }
  }

  function handleCopy() {
    if (!item) return
    navigator.clipboard.writeText(resolvePlayUrl(item.playUrl))
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  if (loading) {
    return (
      <Container maxWidth="sm" sx={{ py: 8, textAlign: 'center' }}>
        <CircularProgress />
      </Container>
    )
  }

  if (notFound || !item) {
    return (
      <Container maxWidth="sm" sx={{ py: 8 }}>
        <Typography variant="h4" sx={{ mb: 2 }}>Content Not Found</Typography>
        <Typography variant="body1" sx={{ color: '#999', mb: 3 }}>
          No library item exists with this UUID.
        </Typography>
        <Button component={RouterLink} to="/" startIcon={<ArrowBackIcon />} variant="outlined">
          Back to Library
        </Button>
      </Container>
    )
  }

  const sourceName = getSourceName(item.plugin, item.parsedContent)
  const sourceColor = getSourceColor(item.plugin, item.parsedContent)
  const title = item.title || item.parsedContent?.title || 'Untitled'

  // Extract metadata if available (Roku content)
  const metadata: RokuMediaMetadata | undefined =
    item.parsedContent && 'metadata' in item.parsedContent
      ? (item.parsedContent as RokuMediaContent).metadata ?? undefined
      : undefined

  const description = metadata?.description || metadata?.overview
  const year = metadata?.year
  const rating = metadata?.officialRating
  const communityRating = metadata?.communityRating

  return (
    <Container maxWidth="sm" sx={{ py: 4 }}>
      <Button
        component={RouterLink}
        to="/"
        startIcon={<ArrowBackIcon />}
        sx={{ mb: 3, color: '#999' }}
        size="small"
      >
        Library
      </Button>

      <Box sx={{ mb: 4 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 1 }}>
          <Chip
            label={sourceName}
            size="small"
            sx={{ bgcolor: sourceColor, color: '#fff', fontWeight: 600 }}
          />
          {year && (
            <Typography variant="body2" sx={{ color: '#999' }}>{year}</Typography>
          )}
          {rating && (
            <Typography variant="body2" sx={{ color: '#999' }}>{rating}</Typography>
          )}
          {communityRating != null && (
            <Typography variant="body2" sx={{ color: '#f5c518' }}>
              {communityRating.toFixed(1)}
            </Typography>
          )}
        </Box>

        <Typography variant="h4" sx={{ fontWeight: 700, mb: 1 }}>
          {title}
        </Typography>

        {description && (
          <Typography variant="body2" sx={{ color: '#aaa', mb: 2, lineHeight: 1.6 }}>
            {description}
          </Typography>
        )}

        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
          <Typography variant="caption" sx={{ color: '#666', fontFamily: 'monospace' }}>
            {uuid}
          </Typography>
          <Tooltip title={copied ? 'Copied!' : 'Copy play URL'}>
            <IconButton size="small" onClick={handleCopy} sx={{ color: '#666' }}>
              {copied ? <CheckIcon fontSize="small" /> : <ContentCopyIcon fontSize="small" />}
            </IconButton>
          </Tooltip>
        </Box>
      </Box>

      <Button
        variant="contained"
        size="large"
        startIcon={playState === 'playing' ? <CircularProgress size={20} color="inherit" /> : <PlayArrowIcon />}
        onClick={handlePlay}
        disabled={playState === 'playing'}
        fullWidth
        sx={{
          py: 2,
          fontSize: '1.1rem',
          fontWeight: 700,
          mb: 3,
        }}
      >
        {playState === 'playing' ? 'Starting...' : 'Play'}
      </Button>

      {playState === 'success' && (
        <Alert severity="success" sx={{ mb: 2 }}>Playback started</Alert>
      )}

      {playState === 'error' && (
        <Alert severity="error" sx={{ mb: 2 }}>{errorMessage}</Alert>
      )}
    </Container>
  )
}
