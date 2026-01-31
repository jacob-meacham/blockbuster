import React from 'react'
import { Box, Button, Grid, MenuItem, Select, TextField, Card, CardContent, Typography, Stack } from '@mui/material'

type PluginInfo = { name: string; description: string }
type SearchResult<T> = { title: string; url?: string | null; mediaUrl?: string | null; content: T }
type RokuContent = { channelName?: string; channelId: string; ecpCommand?: string; contentId: string; mediaType?: string; title?: string }

export function SearchView() {
  const [plugins, setPlugins] = React.useState<PluginInfo[]>([])
  const [plugin, setPlugin] = React.useState('')
  const [q, setQ] = React.useState('')
  const [results, setResults] = React.useState<SearchResult<any>[]>([])
  const [loading, setLoading] = React.useState(false)
  const [error, setError] = React.useState<string | null>(null)

  React.useEffect(() => {
    fetch('/search/plugins').then(r => r.json()).then(d => setPlugins(d.plugins || [])).catch(() => setPlugins([]))
  }, [])

  async function onSearch() {
    if (!q || !plugin) { setError('Enter query and select a plugin'); return }
    setLoading(true); setError(null)
    try {
      const resp = await fetch(`/search/${encodeURIComponent(plugin)}?q=${encodeURIComponent(q)}`)
      const data = await resp.json()
      if (!resp.ok) throw new Error(data.error || 'Search failed')
      setResults(data.results || [])
    } catch (e: any) {
      setError(e.message || 'Search failed')
    } finally { setLoading(false) }
  }

  async function addToLibrary(r: SearchResult<any>) {
    try {
      const resp = await fetch(`/library/${encodeURIComponent(plugin)}`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ content: r.content })
      })
      const data = await resp.json()
      if (!resp.ok) throw new Error(data.error || 'Failed to add')
      alert(`Added with UUID: ${data.uuid}`)
    } catch (e: any) { alert(e.message || 'Failed to add') }
  }

  return (
    <Box>
      <Grid container spacing={2} sx={{ mb: 2 }}>
        <Grid item xs={12} md={6}><TextField fullWidth label="Search" value={q} onChange={e => setQ(e.target.value)} onKeyDown={e => e.key==='Enter'&&onSearch()} /></Grid>
        <Grid item xs={12} md={3}>
          <Select fullWidth displayEmpty value={plugin} onChange={e => setPlugin(String(e.target.value))}>
            <MenuItem value=""><em>Select plugin</em></MenuItem>
            {plugins.map(p => <MenuItem key={p.name} value={p.name}>{p.name} - {p.description}</MenuItem>)}
          </Select>
        </Grid>
        <Grid item xs={12} md={3}><Button fullWidth variant="contained" onClick={onSearch}>Search</Button></Grid>
      </Grid>
      {loading && <Typography>Searching...</Typography>}
      {error && <Typography color="error">{error}</Typography>}
      <Grid container spacing={2}>
        {results.map((r, i) => {
          const c = r.content as RokuContent
          return (
            <Grid item xs={12} md={6} lg={4} key={i}>
              <Card>
                <CardContent>
                  <Stack spacing={1}>
                    <Typography variant="h6">{r.title}</Typography>
                    {c?.channelName && <Typography color="secondary">{c.channelName}</Typography>}
                    <Stack direction="row" spacing={1}>
                      {c?.mediaType && <Typography variant="caption" sx={{ bgcolor: 'primary.main', color: '#fff', px: 1, borderRadius: 1 }}>{String(c.mediaType).toUpperCase()}</Typography>}
                      {r.url && <a href={r.url} target="_blank" rel="noreferrer">Open</a>}
                    </Stack>
                    <Button variant="contained" color="success" onClick={() => addToLibrary(r)}>Add to Library</Button>
                  </Stack>
                </CardContent>
              </Card>
            </Grid>
          )
        })}
      </Grid>
    </Box>
  )
}




