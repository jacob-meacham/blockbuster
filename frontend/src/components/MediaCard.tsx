import React from 'react'
import {
  Card,
  CardContent,
  Typography,
  Chip,
  Box,
  Fade,
  Zoom
} from '@mui/material'
import PlayArrowIcon from '@mui/icons-material/PlayArrow'
import AddIcon from '@mui/icons-material/Add'
import ContentCopyIcon from '@mui/icons-material/ContentCopy'
import HelpOutlineIcon from '@mui/icons-material/HelpOutline'
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined'
import CheckCircleIcon from '@mui/icons-material/CheckCircle'
import { channelColors, channelLogos } from '../types'

export type MediaCardProps = {
  title: string
  channelName?: string
  channelId: string
  contentId: string
  imageUrl?: string
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
  imageUrl,
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
    <Zoom in style={{ transitionDelay: `${index * 50}ms` }}>
      <Card
        onMouseEnter={() => setHovered(true)}
        onMouseLeave={() => setHovered(false)}
        onClick={isClickable || isManualSearchTile ? onClick : undefined}
        sx={{
          bgcolor: '#1a1a1a',
          borderRadius: 2,
          overflow: 'hidden',
          cursor: isClickable || isManualSearchTile ? 'pointer' : 'default',
          transition: 'all 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
          transform: hovered && isClickable ? 'scale(1.05) translateY(-8px)' : 'scale(1)',
          boxShadow: hovered && isClickable
            ? `0 20px 40px rgba(0,0,0,0.5), 0 0 0 2px ${color}`
            : '0 4px 12px rgba(0,0,0,0.3)',
          position: 'relative',
          '&::before': {
            content: '""',
            position: 'absolute',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            background: 'linear-gradient(180deg, transparent 0%, rgba(0,0,0,0.8) 100%)',
            opacity: hovered ? 1 : 0.7,
            transition: 'opacity 0.3s',
            zIndex: 1,
            pointerEvents: 'none'
          }
        }}
      >
        {/* Image / Channel Logo / Color Bar */}
        <Box sx={{
          height: 270,
          background: imageUrl
            ? `url(${imageUrl}) center/cover no-repeat`
            : channelName && channelLogos[channelName]
            ? `url(${channelLogos[channelName]}) center/contain no-repeat, ${color}`
            : isManualSearchTile
            ? 'linear-gradient(135deg, #FF6B6B 0%, #4ECDC4 100%)'
            : color || 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          position: 'relative'
        }}>
          {/* Manual search icon */}
          {isManualSearchTile && (
            <HelpOutlineIcon sx={{ fontSize: 80, color: '#fff', opacity: 0.9 }} />
          )}

          {/* In Library badge */}
          {isInLibrary && !isManualSearchTile && (
            <Chip
              icon={<CheckCircleIcon sx={{ fontSize: 16 }} />}
              label="In Library"
              size="small"
              sx={{
                position: 'absolute',
                top: 12,
                right: 12,
                zIndex: 3,
                bgcolor: 'rgba(46, 125, 50, 0.9)',
                color: '#fff',
                fontWeight: 600,
                fontSize: '0.75rem',
                backdropFilter: 'blur(4px)'
              }}
            />
          )}

          {/* Hover overlay icon */}
          {isClickable && !isManualSearchTile && hovered && (
            <Fade in>
              <Box sx={{
                position: 'absolute',
                zIndex: 2,
                bgcolor: 'rgba(0,0,0,0.8)',
                borderRadius: '50%',
                width: 64,
                height: 64,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center'
              }}>
                {isInLibrary ? (
                  <ContentCopyIcon sx={{ fontSize: 32, color: '#fff' }} />
                ) : (
                  <AddIcon sx={{ fontSize: 40, color: '#fff' }} />
                )}
              </Box>
            </Fade>
          )}
        </Box>

        <CardContent sx={{ position: 'relative', zIndex: 2, pt: 2 }}>
          {/* Channel Badge */}
          {channelName && !isManualSearchTile && (
            <Chip
              label={channelName}
              size="small"
              sx={{
                bgcolor: color,
                color: '#fff',
                fontWeight: 600,
                mb: 1.5,
                fontSize: '0.75rem'
              }}
            />
          )}

          {/* Title */}
          <Typography
            variant="h6"
            sx={{
              color: '#fff',
              fontWeight: 600,
              mb: 1,
              lineHeight: 1.3,
              height: '2.6em',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              display: '-webkit-box',
              WebkitLineClamp: 2,
              WebkitBoxOrient: 'vertical'
            }}
          >
            {title}
          </Typography>

          {/* Description */}
          {description && !isManualInstruction && (
            <Typography
              variant="body2"
              sx={{
                color: '#999',
                mb: 1.5,
                lineHeight: 1.4,
                height: '2.8em',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                display: '-webkit-box',
                WebkitLineClamp: 2,
                WebkitBoxOrient: 'vertical'
              }}
            >
              {description}
            </Typography>
          )}

          {/* Manual Instructions */}
          {isManualInstruction && instructions && (
            <Box sx={{
              bgcolor: 'rgba(255, 152, 0, 0.1)',
              border: '1px solid rgba(255, 152, 0, 0.3)',
              borderRadius: 1,
              p: 1.5,
              mt: 1
            }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
                <InfoOutlinedIcon sx={{ color: '#ff9800', fontSize: 20 }} />
                <Typography variant="caption" sx={{ color: '#ff9800', fontWeight: 600 }}>
                  Manual Search Required
                </Typography>
              </Box>
              <Typography
                variant="caption"
                sx={{
                  color: '#ccc',
                  whiteSpace: 'pre-line',
                  lineHeight: 1.4,
                  display: 'block'
                }}
              >
                {instructions.slice(0, 200)}...
              </Typography>
            </Box>
          )}

          {/* Media Type */}
          {mediaType && !isManualInstruction && !isManualSearchTile && (
            <Chip
              label={mediaType.toUpperCase()}
              size="small"
              variant="outlined"
              sx={{
                color: '#999',
                borderColor: '#333',
                fontSize: '0.7rem',
                height: 24
              }}
            />
          )}
        </CardContent>
      </Card>
    </Zoom>
  )
}
