# Blockbuster

[![Coverage Status](https://coveralls.io/repos/github/jacob-meacham/blockbuster/badge.svg?branch=main)](https://coveralls.io/github/jacob-meacham/blockbuster?branch=main)

NFC-powered media library. Tap a cartridge, play a movie.

## Quick Start

### Docker (recommended)

```
cp config.yml.example config.yml    # edit with your device IPs and API keys
docker compose up --build
```

Frontend: http://localhost:8585 | API: http://localhost:8585

### Local Development

Prerequisites: JDK 21, Node 20

```
cp config.yml.example config.yml    # edit with your values
```

Backend (terminal 1):

```
./gradlew run -Pargs="server config.yml"
```

Frontend (terminal 2):

```
cd frontend && npm install && npm run dev
```

Backend: http://localhost:8584 | Frontend: http://localhost:8585

## Build & Test

```
./scripts/build
```

## Architecture

- **Backend**: Kotlin/Dropwizard REST API on :8585
  - Plugin system for media services (Roku channels, Emby)
  - SQLite content registry mapping NFC tag UUIDs to media
  - Theater device setup (Harmony Hub, Home Assistant, Roku)
- **Frontend**: React/TypeScript/Vite on :8586
  - Search across all configured media plugins
  - Library management for NFC tag assignments
- **NFC Flow**: Phone taps tag → opens /play/{uuid} → backend resolves content → triggers playback

## Configuration

Copy `config.yml.example` to `config.yml` and fill in:
- Roku device IP
- Emby server URL and API key
- Brave Search API key (for streaming service search)
- Theater device settings (optional)

## Docker

```
docker compose up --build       # start
docker compose down             # stop
docker compose up --build -d    # start detached
```

Frontend and backend both served on :8585.
