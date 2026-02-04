import type { SearchResult, LibraryItem, PluginInfo, ChannelInfo } from './types'

/** Resolve a relative play URL path to an absolute URL using the current origin. */
export function resolvePlayUrl(path: string): string {
  if (path.startsWith('http://') || path.startsWith('https://')) return path
  return `${window.location.origin}${path}`
}

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
  const pluginName = result.plugin
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

export async function deleteLibraryItem(uuid: string): Promise<void> {
  const resp = await fetch(`/library/${uuid}`, { method: 'DELETE' })
  if (!resp.ok) {
    const data = await resp.json()
    throw new Error(data.error || 'Failed to delete item')
  }
}

export async function renameLibraryItem(uuid: string, title: string): Promise<void> {
  const resp = await fetch(`/library/${uuid}/rename`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ title })
  })
  if (!resp.ok) {
    const data = await resp.json()
    throw new Error(data.error || 'Failed to rename item')
  }
}

export async function fetchPlugins(): Promise<PluginInfo[]> {
  const resp = await fetch('/search/plugins')
  const data = await resp.json()
  return data.plugins || []
}

export async function fetchChannels(pluginName: string): Promise<ChannelInfo[]> {
  const resp = await fetch(`/${encodeURIComponent(pluginName)}/channels`)
  const data = await resp.json()
  return data.channels || []
}

export type AuthStatus = {
  plugin: string
  available: boolean
  authenticated: boolean
}

export async function fetchAuthStatus(plugin: string): Promise<AuthStatus> {
  const resp = await fetch(`/auth/${plugin}/status`)
  return resp.json()
}
