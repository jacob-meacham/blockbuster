import { useState, useEffect, useCallback } from 'react'
import type { LibraryItem, SearchResult } from '../types'
import { fetchLibrary, addToLibrary as addToLibraryApi } from '../api'

function libraryKey(channelId?: string, contentId?: string): string | null {
  if (!channelId || !contentId) return null
  return `${channelId}-${contentId}`
}

export function useLibrary() {
  const [libraryItems, setLibraryItems] = useState<LibraryItem[]>([])
  const [libraryMap, setLibraryMap] = useState<Map<string, LibraryItem>>(new Map())
  const [loading, setLoading] = useState(false)

  const rebuildMap = useCallback((items: LibraryItem[]) => {
    const map = new Map<string, LibraryItem>()
    items.forEach(item => {
      const key = libraryKey(item.parsedContent?.channelId, item.parsedContent?.contentId)
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

  function isInLibrary(channelId: string, contentId: string): boolean {
    const key = libraryKey(channelId, contentId)
    return key !== null && libraryMap.has(key)
  }

  function getLibraryItem(channelId: string, contentId: string): LibraryItem | undefined {
    const key = libraryKey(channelId, contentId)
    return key ? libraryMap.get(key) : undefined
  }

  async function addItem(result: SearchResult): Promise<string> {
    const { uuid, url } = await addToLibraryApi(result)

    // Optimistic local state update
    const newItem: LibraryItem = {
      id: uuid,
      uuid,
      plugin: result.plugin || 'roku',
      playUrl: url,
      configJson: JSON.stringify(result.content),
      updatedAt: new Date().toISOString(),
      parsedContent: result.content
    }

    setLibraryItems(prev => [newItem, ...prev])
    setLibraryMap(prev => {
      const next = new Map(prev)
      const key = libraryKey(result.content.channelId, result.content.contentId)
      if (key) next.set(key, newItem)
      return next
    })

    return url
  }

  return { libraryItems, isInLibrary, getLibraryItem, addItem, loading, refresh: loadLibrary }
}
