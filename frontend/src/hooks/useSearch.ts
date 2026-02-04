import { useState, useEffect, useRef } from 'react'
import { useSearchParams } from 'react-router-dom'
import type { SearchResult } from '../types'
import { searchAll } from '../api'

export function useSearch() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [query, setQuery] = useState(searchParams.get('q') || '')
  const [selectedPlugin, setSelectedPlugin] = useState<string>('')
  const [results, setResults] = useState<SearchResult[]>([])
  const [loading, setLoading] = useState(false)
  const searchTimeoutRef = useRef<ReturnType<typeof setTimeout>>()

  // Update query when URL changes externally
  useEffect(() => {
    const q = searchParams.get('q')
    if (q && q !== query) {
      setQuery(q)
    }
  }, [searchParams])

  // Debounced search
  useEffect(() => {
    if (searchTimeoutRef.current) {
      clearTimeout(searchTimeoutRef.current)
    }

    if (query.length < 2) {
      setResults([])
      return
    }

    searchTimeoutRef.current = setTimeout(async () => {
      setLoading(true)
      try {
        const plugin = selectedPlugin || undefined
        const data = await searchAll(query, plugin)
        setResults(data)
      } catch (e) {
        console.error('Search failed:', e)
        setResults([])
      } finally {
        setLoading(false)
      }
    }, 500)

    return () => {
      if (searchTimeoutRef.current) {
        clearTimeout(searchTimeoutRef.current)
      }
    }
  }, [query, selectedPlugin])

  function updateQuery(newQuery: string) {
    setQuery(newQuery)
    if (newQuery) {
      setSearchParams({ q: newQuery })
    } else {
      setSearchParams({})
    }
  }

  return { query, updateQuery, selectedPlugin, setSelectedPlugin, results, loading }
}
