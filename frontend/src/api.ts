import type { SearchResult, LibraryItem, PluginInfo, ChannelInfo } from './types'

export async function searchAll(query: string, plugin?: string): Promise<SearchResult[]> {
  const pluginParam = plugin && plugin !== 'all' ? `&plugin=${plugin}` : ''
  const resp = await fetch(`/search/all?q=${encodeURIComponent(query)}${pluginParam}`)
  const data = await resp.json()
  return data.results || []
}

export async function fetchLibrary(): Promise<LibraryItem[]> {
  const resp = await fetch('/library?page=1&pageSize=1000')
  const data = await resp.json()
  return (data.items || []).map(
    (it: { uuid: string; plugin: string; playUrl: string; configJson: string; updatedAt: string }) => {
      let parsedContent = null
      try {
        parsedContent = JSON.parse(it.configJson)
      } catch {
        // ignore parse errors
      }
      return {
        id: it.uuid,
        ...it,
        parsedContent
      }
    }
  )
}

export async function addToLibrary(
  result: SearchResult
): Promise<{ uuid: string; url: string }> {
  const pluginName = result.plugin || 'roku'
  const resp = await fetch(`/library/${pluginName}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ content: result.content })
  })
  if (!resp.ok) {
    const data = await resp.json()
    throw new Error(data.error || 'Failed to add to library')
  }
  return resp.json()
}

export async function fetchPlugins(): Promise<PluginInfo[]> {
  const resp = await fetch('/search/plugins')
  const data = await resp.json()
  return data.plugins || []
}

export async function fetchChannels(): Promise<ChannelInfo[]> {
  const resp = await fetch('/search/channels')
  const data = await resp.json()
  return data.channels || []
}
