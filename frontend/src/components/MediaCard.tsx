import React from 'react'
import {
  Card,
  CardContent,
  Typography,
  Chip,
  Box,
  Zoom
} from '@mui/material'
import CheckCircleIcon from '@mui/icons-material/CheckCircle'
import { channelColors } from '../types'

export type MediaCardProps = {
  title: string
  channelName?: string
  channelId: string
  contentId: string
  description?: string
  mediaType?: string
  instructions?: string
  isInLibrary: boolean
  isManualSearchTile?: boolean
  onClick: () => void
  index: number
}

export function MediaCard({
  title,
  channelName,
  description,
  mediaType,
  instructions,
  isInLibrary,
  isManualSearchTile,
  onClick,
  index
}: MediaCardProps) {
  const [hovered, setHovered] = React.useState(false)

  const isManualInstruction = !!instructions
  const isClickable = !isManualInstruction

  const color = channelColors[channelName || ''] || '#666'

  return (
    <Zoom in style={{ transitionDelay: `${index * 30}ms` }}>
      <Card
        onMouseEnter={() => setHovered(true)}
        onMouseLeave={() => setHovered(false)}
        onClick={isClickable || isManualSearchTile ? onClick : undefined}
        sx={{
          bgcolor: '#1a1a1a',
          borderRadius: 1.5,
          overflow: 'hidden',
          cursor: isClickable || isManualSearchTile ? 'pointer' : 'default',
          transition: 'all 0.2s ease',
          transform: hovered && isClickable ? 'translateY(-2px)' : 'none',
          boxShadow: hovered && isClickable
            ? `0 8px 24px rgba(0,0,0,0.4), 0 0 0 1px ${color}`
            : '0 2px 8px rgba(0,0,0,0.3)',
          borderLeft: `3px solid ${color}`
        }}
      >
        <CardContent sx={{ p: 1.5, '&:last-child': { pb: 1.5 } }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.5 }}>
            {channelName && !isManualSearchTile && (
              <Chip
                label={channelName}
                size="small"
                sx={{
                  bgcolor: color,
                  color: '#fff',
                  fontWeight: 600,
                  fontSize: '0.7rem',
                  height: 20
                }}
              />
            )}
            {mediaType && !isManualInstruction && !isManualSearchTile && (
              <Chip
                label={mediaType.toUpperCase()}
                size="small"
                variant="outlined"
                sx={{
                  color: '#999',
                  borderColor: '#333',
                  fontSize: '0.65rem',
                  height: 20
                }}
              />
            )}
            {isInLibrary && !isManualSearchTile && (
              <CheckCircleIcon sx={{ fontSize: 16, color: '#4caf50', ml: 'auto' }} />
            )}
          </Box>

          <Typography
            variant="body2"
            sx={{
              color: '#fff',
              fontWeight: 600,
              lineHeight: 1.3,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap'
            }}
          >
            {title}
          </Typography>

          {description && !isManualInstruction && (
            <Typography
              variant="caption"
              sx={{
                color: '#777',
                lineHeight: 1.3,
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
                display: 'block'
              }}
            >
              {description}
            </Typography>
          )}

          {isManualInstruction && instructions && (
            <Typography
              variant="caption"
              sx={{ color: '#ff9800', display: 'block', mt: 0.5 }}
            >
              Manual search required
            </Typography>
          )}
        </CardContent>
      </Card>
    </Zoom>
  )
}
