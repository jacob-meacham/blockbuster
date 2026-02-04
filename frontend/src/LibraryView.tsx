import React from 'react'
import {
  Box,
  Container,
  Typography,
  TextField,
  InputAdornment,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TableSortLabel,
  IconButton,
  Chip,
  Snackbar,
  Alert,
  Skeleton,
  Select,
  MenuItem,
  FormControl,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button
} from '@mui/material'
import SearchIcon from '@mui/icons-material/Search'
import ContentCopyIcon from '@mui/icons-material/ContentCopy'
import DeleteIcon from '@mui/icons-material/Delete'
import EditIcon from '@mui/icons-material/Edit'
import AddIcon from '@mui/icons-material/Add'
import { Link as RouterLink } from 'react-router-dom'
import { useLibrary } from './hooks/useLibrary'
import {
  resolvePlayUrl,
  deleteLibraryItem,
  renameLibraryItem,
  addToLibrary,
  fetchPlugins,
  fetchChannels
} from './api'
import type { LibraryItem, PluginInfo, ChannelInfo } from './types'
import { getSourceName, getSourceColor } from './types'
import type { RokuMediaContent } from './types'

async function copyToClipboard(text: string): Promise<void> {
  if (navigator.clipboard?.writeText) {
    return navigator.clipboard.writeText(text)
  }
  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.style.position = 'fixed'
  textarea.style.opacity = '0'
  document.body.appendChild(textarea)
  textarea.select()
  document.execCommand('copy')
  document.body.removeChild(textarea)
}

type SortKey = 'plugin' | 'source' | 'title' | 'contentId'
type SortDir = 'asc' | 'desc'

function getItemValue(item: LibraryItem, key: SortKey): string {
  switch (key) {
    case 'plugin': return item.plugin || ''
    case 'source': return getSourceName(item.plugin, item.parsedContent)
    case 'title': return item.parsedContent?.title || ''
    case 'contentId': return item.parsedContent?.contentId || ''
  }
}

export function LibraryView() {
  const library = useLibrary()
  const [filter, setFilter] = React.useState('')
  const [pluginFilter, setPluginFilter] = React.useState<string>('all')
  const [sortKey, setSortKey] = React.useState<SortKey>('title')
  const [sortDir, setSortDir] = React.useState<SortDir>('asc')
  const [plugins, setPlugins] = React.useState<PluginInfo[]>([])
  const [channels, setChannels] = React.useState<ChannelInfo[]>([])
  const [snackbar, setSnackbar] = React.useState<{
    open: boolean
    message: string
    severity: 'success' | 'error'
  }>({ open: false, message: '', severity: 'success' })

  // Rename dialog state
  const [renameItem, setRenameItem] = React.useState<LibraryItem | null>(null)
  const [renameValue, setRenameValue] = React.useState('')

  // Add dialog state
  const [addOpen, setAddOpen] = React.useState(false)
  const [addPlugin, setAddPlugin] = React.useState('')
  const [addChannel, setAddChannel] = React.useState('')
  const [addTitle, setAddTitle] = React.useState('')
  const [addContentId, setAddContentId] = React.useState('')
  const [addMediaType, setAddMediaType] = React.useState('movie')

  React.useEffect(() => {
    fetchPlugins().then(async (p) => {
      setPlugins(p)
      if (p.length > 0) setAddPlugin(p[0].name)
      const allChannels = (await Promise.all(
        p.map(plugin => fetchChannels(plugin.name))
      )).flat()
      setChannels(allChannels)
      if (allChannels.length > 0) setAddChannel(allChannels[0].channelId)
    }).catch(console.error)
  }, [])

  const filtered = React.useMemo(() => {
    let items = library.libraryItems
    if (pluginFilter !== 'all') {
      items = items.filter((item) => item.plugin === pluginFilter)
    }
    if (filter) {
      const lower = filter.toLowerCase()
      items = items.filter((item) => {
        const title = item.parsedContent?.title?.toLowerCase() || ''
        const source = getSourceName(item.plugin, item.parsedContent).toLowerCase()
        const contentId = item.parsedContent?.contentId?.toLowerCase() || ''
        return title.includes(lower) || source.includes(lower) || contentId.includes(lower)
      })
    }
    return [...items].sort((a, b) => {
      const av = getItemValue(a, sortKey).toLowerCase()
      const bv = getItemValue(b, sortKey).toLowerCase()
      const cmp = av.localeCompare(bv)
      return sortDir === 'asc' ? cmp : -cmp
    })
  }, [library.libraryItems, filter, pluginFilter, sortKey, sortDir])

  function handleSort(key: SortKey) {
    if (sortKey === key) {
      setSortDir(sortDir === 'asc' ? 'desc' : 'asc')
    } else {
      setSortKey(key)
      setSortDir('asc')
    }
  }

  function handleCopy(playUrl: string) {
    copyToClipboard(resolvePlayUrl(playUrl)).then(() => {
      setSnackbar({ open: true, message: 'Copied play URL to clipboard', severity: 'success' })
    }).catch(() => {
      setSnackbar({ open: true, message: 'Failed to copy URL', severity: 'error' })
    })
  }

  async function handleDelete(uuid: string) {
    try {
      await deleteLibraryItem(uuid)
      library.refresh()
      setSnackbar({ open: true, message: 'Item deleted', severity: 'success' })
    } catch {
      setSnackbar({ open: true, message: 'Failed to delete item', severity: 'error' })
    }
  }

  function openRename(item: LibraryItem) {
    setRenameItem(item)
    setRenameValue(item.parsedContent?.title || '')
  }

  async function handleRename() {
    if (!renameItem || !renameValue.trim()) return
    try {
      await renameLibraryItem(renameItem.uuid, renameValue.trim())
      library.refresh()
      setRenameItem(null)
      setSnackbar({ open: true, message: 'Item renamed', severity: 'success' })
    } catch {
      setSnackbar({ open: true, message: 'Failed to rename item', severity: 'error' })
    }
  }

  async function handleAdd() {
    if (!addTitle.trim() || !addContentId.trim()) return
    const channel = channels.find((c) => c.channelId === addChannel)
    try {
      const content: RokuMediaContent = {
        channelName: channel?.channelName || '',
        channelId: addChannel,
        contentId: addContentId.trim(),
        title: addTitle.trim(),
        mediaType: addMediaType
      }
      await addToLibrary({
        source: 'manual',
        plugin: addPlugin,
        title: addTitle.trim(),
        content
      })
      library.refresh()
      setAddOpen(false)
      setAddTitle('')
      setAddContentId('')
      setSnackbar({ open: true, message: 'Item added to library', severity: 'success' })
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : 'Failed to add item'
      setSnackbar({ open: true, message: msg, severity: 'error' })
    }
  }

  const uniquePlugins = [...new Set(library.libraryItems.map((i) => i.plugin))].sort()

  const headerSx = { color: '#999', fontWeight: 600, whiteSpace: 'nowrap' as const }

  return (
    <Box sx={{
      minHeight: '100vh',
      background: 'linear-gradient(to bottom, #141414 0%, #000000 100%)',
      pt: 4,
      pb: 8
    }}>
      <Container maxWidth="lg">
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 3 }}>
          <Typography variant="h4" sx={{ color: '#fff', fontWeight: 700 }}>
            Library
          </Typography>
          <Box sx={{ display: 'flex', gap: 1 }}>
            <IconButton
              onClick={() => setAddOpen(true)}
              sx={{ color: '#999', '&:hover': { color: '#fff' } }}
              title="Add item"
            >
              <AddIcon />
            </IconButton>
            <IconButton
              component={RouterLink}
              to="/"
              sx={{ color: '#999', '&:hover': { color: '#fff' } }}
              title="Search"
            >
              <SearchIcon />
            </IconButton>
          </Box>
        </Box>

        <Box sx={{ display: 'flex', gap: 2, mb: 2 }}>
          <TextField
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
            placeholder="Filter..."
            variant="outlined"
            size="small"
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon sx={{ color: '#999', fontSize: 20 }} />
                </InputAdornment>
              ),
              sx: {
                background: 'rgba(255, 255, 255, 0.08)',
                borderRadius: 1,
                '& .MuiOutlinedInput-notchedOutline': {
                  border: '1px solid rgba(255, 255, 255, 0.15)'
                },
                '&:hover .MuiOutlinedInput-notchedOutline': {
                  border: '1px solid rgba(255, 255, 255, 0.3)'
                },
                '&.Mui-focused .MuiOutlinedInput-notchedOutline': {
                  border: '1px solid #E50914'
                },
                color: '#fff',
                fontSize: '0.9rem'
              }
            }}
            sx={{ flex: 1 }}
          />
          <FormControl size="small" sx={{ minWidth: 140 }}>
            <Select
              value={pluginFilter}
              onChange={(e) => setPluginFilter(e.target.value)}
              sx={{
                color: '#fff',
                background: 'rgba(255,255,255,0.08)',
                '& .MuiOutlinedInput-notchedOutline': {
                  borderColor: 'rgba(255,255,255,0.15)'
                },
                '&:hover .MuiOutlinedInput-notchedOutline': {
                  borderColor: 'rgba(255,255,255,0.3)'
                },
                '& .MuiSvgIcon-root': { color: '#999' }
              }}
            >
              <MenuItem value="all">All plugins</MenuItem>
              {uniquePlugins.map((p) => (
                <MenuItem key={p} value={p}>{p.charAt(0).toUpperCase() + p.slice(1)}</MenuItem>
              ))}
            </Select>
          </FormControl>
        </Box>

        {library.loading ? (
          <Box>
            {[1, 2, 3, 4, 5].map((i) => (
              <Skeleton
                key={i}
                variant="rectangular"
                height={40}
                sx={{ bgcolor: 'rgba(255,255,255,0.05)', mb: 0.5, borderRadius: 0.5 }}
              />
            ))}
          </Box>
        ) : (
          <TableContainer>
            <Table size="small" sx={{ '& .MuiTableCell-root': { borderColor: '#333', py: 0.75 } }}>
              <TableHead>
                <TableRow>
                  <TableCell sx={headerSx}>
                    <TableSortLabel
                      active={sortKey === 'plugin'}
                      direction={sortKey === 'plugin' ? sortDir : 'asc'}
                      onClick={() => handleSort('plugin')}
                      sx={{ '&.MuiTableSortLabel-root': { color: '#999' }, '& .MuiTableSortLabel-icon': { color: '#666 !important' } }}
                    >
                      Plugin
                    </TableSortLabel>
                  </TableCell>
                  <TableCell sx={headerSx}>
                    <TableSortLabel
                      active={sortKey === 'source'}
                      direction={sortKey === 'source' ? sortDir : 'asc'}
                      onClick={() => handleSort('source')}
                      sx={{ '&.MuiTableSortLabel-root': { color: '#999' }, '& .MuiTableSortLabel-icon': { color: '#666 !important' } }}
                    >
                      Source
                    </TableSortLabel>
                  </TableCell>
                  <TableCell sx={headerSx}>
                    <TableSortLabel
                      active={sortKey === 'title'}
                      direction={sortKey === 'title' ? sortDir : 'asc'}
                      onClick={() => handleSort('title')}
                      sx={{ '&.MuiTableSortLabel-root': { color: '#999' }, '& .MuiTableSortLabel-icon': { color: '#666 !important' } }}
                    >
                      Name
                    </TableSortLabel>
                  </TableCell>
                  <TableCell sx={headerSx}>
                    <TableSortLabel
                      active={sortKey === 'contentId'}
                      direction={sortKey === 'contentId' ? sortDir : 'asc'}
                      onClick={() => handleSort('contentId')}
                      sx={{ '&.MuiTableSortLabel-root': { color: '#999' }, '& .MuiTableSortLabel-icon': { color: '#666 !important' } }}
                    >
                      Content ID
                    </TableSortLabel>
                  </TableCell>
                  <TableCell sx={{ ...headerSx, width: 120 }} />
                </TableRow>
              </TableHead>
              <TableBody>
                {filtered.map((item) => {
                  const sourceName = getSourceName(item.plugin, item.parsedContent)
                  const color = getSourceColor(item.plugin, item.parsedContent)
                  return (
                    <TableRow
                      key={item.uuid}
                      hover
                      sx={{ '&:hover': { bgcolor: 'rgba(255,255,255,0.03)' } }}
                    >
                      <TableCell sx={{ color: '#ccc' }}>{item.plugin}</TableCell>
                      <TableCell>
                        <Chip
                          label={sourceName}
                          size="small"
                          sx={{
                            bgcolor: color,
                            color: '#fff',
                            fontWeight: 600,
                            fontSize: '0.7rem',
                            height: 20
                          }}
                        />
                      </TableCell>
                      <TableCell sx={{ color: '#fff' }}>
                        {item.parsedContent?.title || 'Untitled'}
                      </TableCell>
                      <TableCell>
                        <Typography
                          variant="caption"
                          sx={{ color: '#999', fontFamily: 'monospace' }}
                        >
                          {item.parsedContent?.contentId || '—'}
                        </Typography>
                      </TableCell>
                      <TableCell sx={{ whiteSpace: 'nowrap' }}>
                        <IconButton
                          size="small"
                          onClick={() => handleCopy(item.playUrl)}
                          sx={{ color: '#999', '&:hover': { color: '#fff' } }}
                          title="Copy play URL"
                        >
                          <ContentCopyIcon sx={{ fontSize: 16 }} />
                        </IconButton>
                        <IconButton
                          size="small"
                          onClick={() => openRename(item)}
                          sx={{ color: '#999', '&:hover': { color: '#fff' } }}
                          title="Rename"
                        >
                          <EditIcon sx={{ fontSize: 16 }} />
                        </IconButton>
                        <IconButton
                          size="small"
                          onClick={() => handleDelete(item.uuid)}
                          sx={{ color: '#999', '&:hover': { color: '#f44336' } }}
                          title="Delete"
                        >
                          <DeleteIcon sx={{ fontSize: 16 }} />
                        </IconButton>
                      </TableCell>
                    </TableRow>
                  )
                })}
                {filtered.length === 0 && !library.loading && (
                  <TableRow>
                    <TableCell colSpan={5} sx={{ textAlign: 'center', color: '#666', py: 4 }}>
                      {library.libraryItems.length === 0 ? 'Library is empty' : 'No matching items'}
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </TableContainer>
        )}

        {/* Rename Dialog */}
        <Dialog
          open={!!renameItem}
          onClose={() => setRenameItem(null)}
          maxWidth="sm"
          fullWidth
        >
          <DialogTitle sx={{ bgcolor: '#1a1a1a', color: '#fff' }}>Rename</DialogTitle>
          <DialogContent sx={{ bgcolor: '#1a1a1a' }}>
            <TextField
              autoFocus
              fullWidth
              value={renameValue}
              onChange={(e) => setRenameValue(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleRename()}
              variant="outlined"
              size="small"
              sx={{ mt: 1, '& .MuiOutlinedInput-root': { color: '#fff', '& .MuiOutlinedInput-notchedOutline': { borderColor: '#555' } } }}
            />
          </DialogContent>
          <DialogActions sx={{ bgcolor: '#1a1a1a' }}>
            <Button onClick={() => setRenameItem(null)} sx={{ color: '#999' }}>Cancel</Button>
            <Button onClick={handleRename} variant="contained" color="primary">Save</Button>
          </DialogActions>
        </Dialog>

        {/* Manual Add Dialog */}
        <Dialog
          open={addOpen}
          onClose={() => setAddOpen(false)}
          maxWidth="sm"
          fullWidth
        >
          <DialogTitle sx={{ bgcolor: '#1a1a1a', color: '#fff' }}>Add to Library</DialogTitle>
          <DialogContent sx={{ bgcolor: '#1a1a1a', display: 'flex', flexDirection: 'column', gap: 2, pt: '16px !important' }}>
            <FormControl size="small" fullWidth>
              <Typography variant="caption" sx={{ color: '#999', mb: 0.5 }}>Plugin</Typography>
              <Select
                value={addPlugin}
                onChange={(e) => setAddPlugin(e.target.value)}
                sx={{ color: '#fff', '& .MuiOutlinedInput-notchedOutline': { borderColor: '#555' }, '& .MuiSvgIcon-root': { color: '#999' } }}
              >
                {plugins.map((p) => (
                  <MenuItem key={p.name} value={p.name}>{p.name.charAt(0).toUpperCase() + p.name.slice(1)}</MenuItem>
                ))}
              </Select>
            </FormControl>
            <FormControl size="small" fullWidth>
              <Typography variant="caption" sx={{ color: '#999', mb: 0.5 }}>Channel</Typography>
              <Select
                value={addChannel}
                onChange={(e) => setAddChannel(e.target.value)}
                sx={{ color: '#fff', '& .MuiOutlinedInput-notchedOutline': { borderColor: '#555' }, '& .MuiSvgIcon-root': { color: '#999' } }}
              >
                {channels.map((c) => (
                  <MenuItem key={c.channelId} value={c.channelId}>{c.channelName}</MenuItem>
                ))}
              </Select>
            </FormControl>
            <TextField
              label="Title"
              value={addTitle}
              onChange={(e) => setAddTitle(e.target.value)}
              fullWidth
              size="small"
              sx={{ '& .MuiOutlinedInput-root': { color: '#fff', '& .MuiOutlinedInput-notchedOutline': { borderColor: '#555' } }, '& .MuiInputLabel-root': { color: '#999' } }}
            />
            <TextField
              label="Content ID"
              value={addContentId}
              onChange={(e) => setAddContentId(e.target.value)}
              fullWidth
              size="small"
              sx={{ '& .MuiOutlinedInput-root': { color: '#fff', '& .MuiOutlinedInput-notchedOutline': { borderColor: '#555' } }, '& .MuiInputLabel-root': { color: '#999' } }}
            />
            <FormControl size="small" fullWidth>
              <Typography variant="caption" sx={{ color: '#999', mb: 0.5 }}>Media Type</Typography>
              <Select
                value={addMediaType}
                onChange={(e) => setAddMediaType(e.target.value)}
                sx={{ color: '#fff', '& .MuiOutlinedInput-notchedOutline': { borderColor: '#555' }, '& .MuiSvgIcon-root': { color: '#999' } }}
              >
                <MenuItem value="movie">Movie</MenuItem>
                <MenuItem value="series">Series</MenuItem>
                <MenuItem value="episode">Episode</MenuItem>
              </Select>
            </FormControl>
          </DialogContent>
          <DialogActions sx={{ bgcolor: '#1a1a1a' }}>
            <Button onClick={() => setAddOpen(false)} sx={{ color: '#999' }}>Cancel</Button>
            <Button
              onClick={handleAdd}
              variant="contained"
              color="primary"
              disabled={!addTitle.trim() || !addContentId.trim()}
            >
              Add
            </Button>
          </DialogActions>
        </Dialog>

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
      </Container>
    </Box>
  )
}
