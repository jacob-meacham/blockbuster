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

export type RokuMediaContent = {
  channelName: string
  channelId: string
  contentId: string
  title: string
  mediaType?: string
  metadata?: RokuMediaMetadata
}

export type SearchResult = {
  source: string
  plugin: string
  title: string
  channelName?: string
  channelId: string
  contentId: string
  mediaType?: string
  url?: string
  description?: string
  imageUrl?: string
  content: RokuMediaContent
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
  playUrl: string
  configJson: string
  updatedAt: string
  parsedContent: RokuMediaContent | null
}

export const channelColors: Record<string, string> = {
  'Netflix': '#E50914',
  'Disney+': '#113CCF',
  'HBO Max': '#9D34DA',
  'Prime Video': '#00A8E1',
  'Emby': '#52B54B'
}

export const channelLogos: Record<string, string> = {
  'Netflix': 'https://cdn.worldvectorlogo.com/logos/netflix-3.svg',
  'Disney+': 'https://cdn.worldvectorlogo.com/logos/disney-plus.svg',
  'HBO Max': 'https://cdn.worldvectorlogo.com/logos/hbo-max-2.svg',
  'Prime Video': 'https://cdn.worldvectorlogo.com/logos/amazon-prime-video.svg'
}
