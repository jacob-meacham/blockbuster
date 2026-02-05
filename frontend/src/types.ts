/**
 * Base content type that all plugins must provide.
 * Plugin-specific content types extend this with additional fields.
 */
export type MediaContentBase = {
  contentId: string
  title?: string
  mediaType?: string
}

export type RokuMediaMetadata = {
  description?: string
  overview?: string
  imageUrl?: string
  searchUrl?: string
  originalUrl?: string
  resumePositionTicks?: number
  runtimeTicks?: number
  playedPercentage?: number
  isFavorite?: boolean
  communityRating?: number
  officialRating?: string
  genres?: string[]
  serverId?: string
  itemType?: string
  seriesName?: string
  seasonNumber?: number
  episodeNumber?: number
  year?: number
  instructions?: string
}

export type RokuMediaContent = MediaContentBase & {
  channelName: string
  channelId: string
  metadata?: RokuMediaMetadata
}

export type SpotifyMediaContent = MediaContentBase & {
  spotifyUri: string
  artist?: string
  imageUrl?: string
  description?: string
}

export type SearchResult = {
  source: string
  plugin: string
  title: string
  url?: string
  description?: string
  imageUrl?: string
  dedupKey?: string
  content: MediaContentBase
}

export type ChannelInfo = {
  channelId: string
  channelName: string
  searchUrl: string
}

export type PluginInfo = {
  name: string
  description: string
}

export type LibraryItem = {
  id: string
  uuid: string
  plugin: string
  title: string | null
  playUrl: string
  configJson: string
  updatedAt: string
  parsedContent: MediaContentBase | null
}

export const sourceColors: Record<string, string> = {
  'Netflix': '#E50914',
  'Disney+': '#113CCF',
  'HBO Max': '#9D34DA',
  'Prime Video': '#00A8E1',
  'Emby': '#52B54B',
  'Spotify': '#1DB954',
}

export const channelLogos: Record<string, string> = {
  'Netflix': 'https://cdn.worldvectorlogo.com/logos/netflix-3.svg',
  'Disney+': 'https://cdn.worldvectorlogo.com/logos/disney-plus.svg',
  'HBO Max': 'https://cdn.worldvectorlogo.com/logos/hbo-max-2.svg',
  'Prime Video': 'https://cdn.worldvectorlogo.com/logos/amazon-prime-video.svg'
}

/** Get a display-friendly source name from plugin + content. */
export function getSourceName(plugin: string, content: MediaContentBase | null): string {
  if (!content) return plugin.charAt(0).toUpperCase() + plugin.slice(1)
  if (plugin === 'roku' && 'channelName' in content) {
    return (content as RokuMediaContent).channelName || 'Roku'
  }
  if (plugin === 'spotify') {
    return 'Spotify'
  }
  return plugin.charAt(0).toUpperCase() + plugin.slice(1)
}

/** Get the accent color for a source name. */
export function getSourceColor(plugin: string, content: MediaContentBase | null): string {
  const sourceName = getSourceName(plugin, content)
  return sourceColors[sourceName] || '#666'
}
