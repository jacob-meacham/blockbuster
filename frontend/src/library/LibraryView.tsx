import React from 'react'
import { Box, Chip, TextField, InputAdornment, Snackbar, Alert } from '@mui/material'
import { DataGrid, GridColDef, GridRenderCellParams } from '@mui/x-data-grid'
import SearchIcon from '@mui/icons-material/Search'

type ParsedContent = {
  channelName?: string;
  channelId?: string;
  contentId?: string;
  title?: string;
  mediaType?: string;
}

type LibraryRow = {
  id: string;
  uuid: string;
  plugin: string;
  playUrl: string;
  configJson: string;
  updatedAt: string;
  parsedContent?: ParsedContent | null;
}

export function LibraryView() {
  const [allRows, setAllRows] = React.useState<LibraryRow[]>([])
  const [filter, setFilter] = React.useState('')
  const [loading, setLoading] = React.useState(false)
  const [snackbar, setSnackbar] = React.useState({ open: false, message: '' })

  const columns: GridColDef[] = [
    { field: 'plugin', headerName: 'Plugin', width: 100 },
    {
      field: 'title',
      headerName: 'Title',
      flex: 2,
      valueGetter: (params) => {
        const content = params.row.parsedContent
        return content?.title || content?.contentId || 'Untitled'
      }
    },
    {
      field: 'channelName',
      headerName: 'Channel',
      width: 130,
      renderCell: (params: GridRenderCellParams) => {
        const channelName = params.row.parsedContent?.channelName
        if (!channelName) return null
        return <Chip label={channelName} size="small" color="primary" />
      }
    },
    {
      field: 'mediaType',
      headerName: 'Type',
      width: 100,
      renderCell: (params: GridRenderCellParams) => {
        const mediaType = params.row.parsedContent?.mediaType
        if (!mediaType) return null
        return <Chip label={mediaType.toUpperCase()} size="small" variant="outlined" />
      }
    },
    {
      field: 'contentId',
      headerName: 'Content ID',
      flex: 1,
      valueGetter: (params) => params.row.parsedContent?.contentId || 'N/A'
    },
    { field: 'updatedAt', headerName: 'Updated', width: 180 }
  ]

  React.useEffect(() => {
    let ignore = false
    async function load() {
      setLoading(true)
      try {
        const resp = await fetch('/library?page=1&pageSize=1000')
        const data = await resp.json()
        if (ignore) return

        const items = (data.items || []).map((it: { uuid: string; plugin: string; playUrl: string; configJson: string; updatedAt: string }) => {
          let parsedContent
          try {
            parsedContent = JSON.parse(it.configJson)
          } catch (e) {
            parsedContent = null
          }

          return {
            id: it.uuid,
            ...it,
            parsedContent
          }
        })

        setAllRows(items)
      } finally {
        setLoading(false)
      }
    }
    load()
    return () => { ignore = true }
  }, [])

  const filteredRows = React.useMemo(() => {
    if (!filter) return allRows
    const lower = filter.toLowerCase()
    return allRows.filter(row => {
      const content = row.parsedContent
      const title = content?.title || ''
      const channel = content?.channelName || ''
      const contentId = content?.contentId || ''
      return title.toLowerCase().includes(lower) ||
        channel.toLowerCase().includes(lower) ||
        contentId.toLowerCase().includes(lower)
    })
  }, [allRows, filter])

  function handleRowClick(params: { row: LibraryRow }) {
    const url = params.row.playUrl
    if (url) {
      navigator.clipboard.writeText(url)
      setSnackbar({ open: true, message: 'Copied play URL to clipboard' })
    }
  }

  return (
    <Box sx={{ height: 600, width: '100%', display: 'flex', flexDirection: 'column', gap: 2, p: 2 }}>
      <TextField
        fullWidth
        value={filter}
        onChange={(e) => setFilter(e.target.value)}
        placeholder="Filter by title, channel, or content ID..."
        variant="outlined"
        size="small"
        InputProps={{
          startAdornment: (
            <InputAdornment position="start">
              <SearchIcon sx={{ color: '#999' }} />
            </InputAdornment>
          )
        }}
      />
      <Box sx={{ flex: 1, minHeight: 0 }}>
        <DataGrid
          columns={columns}
          rows={filteredRows}
          loading={loading}
          pageSizeOptions={[25, 50, 100]}
          onRowClick={handleRowClick}
          sx={{
            cursor: 'pointer',
            '& .MuiDataGrid-row:hover': {
              bgcolor: 'rgba(255, 255, 255, 0.08)'
            }
          }}
        />
      </Box>
      <Snackbar
        open={snackbar.open}
        autoHideDuration={3000}
        onClose={() => setSnackbar(s => ({ ...s, open: false }))}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert
          onClose={() => setSnackbar(s => ({ ...s, open: false }))}
          severity="success"
          variant="filled"
        >
          {snackbar.message}
        </Alert>
      </Snackbar>
    </Box>
  )
}
