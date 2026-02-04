import { useState, useEffect, useCallback } from 'react'
import type { LibraryItem, MediaContentBase, SearchResult } from '../types'
import { fetchLibrary, addToLibrary as addToLibraryApi } from '../api'

/**
 * Plugin-aware content key for library dedup.
 * Roku needs channelId prefix since one plugin has many channels.
 */
function contentKey(plugin: string, content: MediaContentBase | null): string | null {
  if (!content?.contentId) return null
  if (plugin === 'roku' && 'channelId' in content && (content as any).channelId) {
    return `roku-${(content as any).channelId}-${content.contentId}`
  }
  return `${plugin}-${content.contentId}`
}

export function useLibrary() {
  const [libraryItems, setLibraryItems] = useState<LibraryItem[]>([])
  const [libraryMap, setLibraryMap] = useState<Map<string, LibraryItem>>(new Map())
  const [loading, setLoading] = useState(false)

  const rebuildMap = useCallback((items: LibraryItem[]) => {
    const map = new Map<string, LibraryItem>()
    items.forEach(item => {
      const key = contentKey(item.plugin, item.parsedContent)
      if (key) map.set(key, item)
    })
    setLibraryMap(map)
  }, [])

  const loadLibrary = useCallback(async () => {
    setLoading(true)
    try {
      const items = await fetchLibrary()
      setLibraryItems(items)
      rebuildMap(items)
    } catch (e) {
      console.error('Failed to load library:', e)
    } finally {
      setLoading(false)
    }
  }, [rebuildMap])

  useEffect(() => {
    loadLibrary()
  }, [loadLibrary])

  function isInLibrary(plugin: string, content: MediaContentBase): boolean {
    const key = contentKey(plugin, content)
    return key !== null && libraryMap.has(key)
  }

  function getLibraryItem(plugin: string, content: MediaContentBase): LibraryItem | undefined {
    const key = contentKey(plugin, content)
    return key ? libraryMap.get(key) : undefined
  }

  async function addItem(result: SearchResult): Promise<string> {
    const { uuid, url } = await addToLibraryApi(result)

    // Optimistic local state update
    const newItem: LibraryItem = {
      id: uuid,
      uuid,
      plugin: result.plugin,
      playUrl: url,
      configJson: JSON.stringify(result.content),
      updatedAt: new Date().toISOString(),
      parsedContent: result.content
    }

    setLibraryItems(prev => [newItem, ...prev])
    setLibraryMap(prev => {
      const next = new Map(prev)
      const key = contentKey(result.plugin, result.content)
      if (key) next.set(key, newItem)
      return next
    })

    return url
  }

  return { libraryItems, isInLibrary, getLibraryItem, addItem, loading, refresh: loadLibrary }
}
