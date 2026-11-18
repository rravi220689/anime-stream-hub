# 🎬 AniStream HUB

> **A Mobile-First, Responsive Streaming & Download Hub for Animations, Cartoons, Series, Movies & YouTube Videos.**

[![GitHub Repo](https://img.shields.io/badge/GitHub-rravi220689%2Fanime--stream--hub-blue?logo=github)](https://github.com/rravi220689/anime-stream-hub)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED?logo=docker&logoColor=white)](Dockerfile)
[![Node.js](https://img.shields.io/badge/Node.js-22.x-green?logo=node.js)](package.json)
[![License: ISC](https://img.shields.io/badge/License-ISC-yellow.svg)](LICENSE)

AniStream HUB is a modern web application engineered to aggregate, stream, and download animations and cartoons from diverse sources (such as Gogoanime, Animepahe, YouTube, VidSrc, and direct MP4/M3U8 streams) with responsive auto-adjustment across mobile phones, tablets, foldables, laptops, and ultra-wide displays.

---

## ✨ Key Features

### 📱 1. Mobile-First Auto-Screen Adjustment & Responsiveness
- **Auto-Adjusting Grid Layout**: Seamlessly transitions between 2 columns on mobile devices (`< 540px`), 3-4 columns on tablets (`540px - 900px`), and 5-6 columns on desktop displays (`> 1024px`).
- **Dynamic Viewport Sizing**: Uses `dvh` / `dvw` dynamic viewport units and safe-area insets (`env(safe-area-inset-top)` / `env(safe-area-inset-bottom)`) for edge-to-edge rendering around mobile notches and gestures.
- **Mobile Bottom Navigation Bar**: Fixed thumb-friendly bottom nav for one-handed mobile browsing (`Home`, `Anime`, `Cartoons`, `YouTube`, `Saved Watchlist`).
- **Sticky Mini-Player**: When scrolling down while a video is playing, the player collapses into a floating mini-dock at the screen bottom with expand and close buttons.
- **Cinema Mode & Fullscreen**: 16:9 auto-scaling aspect ratio container with one-click ambient cinema dimmer and fullscreen rotation.

---

### 📺 2. Multi-Source Streaming Engine (Playable In-Site)
- **Multi-Server Provider Switcher**:
  - **Server 1 (VidSrc Fast)**: High-speed anime and cartoon streaming backend.
  - **Server 2 (AutoEmbed HD)**: Ultra HD streaming server with multi-subtitle support.
  - **Server 3 (2Embed Stream)**: Alternative high-reliability streaming mirror.
  - **Server 4 (SmashyStream)**: Fallback ad-filtered anime server.
  - **Server 5 (EmbedSU)**: Fast streaming node for movies and series.
  - **YouTube HD Player**: Seamless embedded playback with ad-free sandbox containers.
  - **Direct HLS / MP4 Player**: Native HTML5 player with Hls.js for adaptive `.m3u8` and `.mp4` video files.
- **Episode Navigator**: Complete episode grid with next/prev buttons, quick jump selector, and automatic "watched" indicators.

---

### 📥 3. Video & Companion Subtitle Download Manager
- **Full Anime Episode Downloads**: Downloads full 24-minute episodes (not short teaser clips) remuxed directly into high-quality MP4 or MP3 with multi-resolution support (1080p, 720p, 480p, 360p).
- **High-Quality MP3 Audio Downloads**: Direct high-fidelity 320kbps MP3 audio extraction from any anime episode, YouTube cartoon, or direct video stream.
- **Companion English Subtitles (.srt & .vtt)**: Subtitles are automatically resolved from the stream source and downloaded alongside the video file into `/sdcard/Download/AniStream/` with matching base filenames (e.g. `One_Piece_Episode_1_1080p.mp4` and `One_Piece_Episode_1_1080p.srt`) for instant auto-loading in VLC, MX Player, and Android media players. Dedicated subtitle-only downloads are also available.
- **Real-Time Download Progress Tracking**: Real-time progress tracking (`%`, `XX MB / YY MB`, animated progress bar, status text) visible in Android system notifications, in-app floating progress card, and dedicated in-app **Download Manager Dialog**.
- **YouTube Video Downloader**: Integrated access to Cobalt HD (1080p/720p ad-free), Y2Mate (MP4/MP3), and SaveFrom.
- **Stream Link Exporter**: One-click copy stream source URL for VLC, MPV, or external download managers.
- **Dynamic Server URL Updater**: Easily update and test backend server URLs anytime in both mobile and TV apps (on home screen and inside video player) with one-click presets (`http://rravi.ddns.net:4008`, `http://10.0.2.2:5050`) and live connection ping testing.

---

### 🔴 4. YouTube Hub & Live Search
- **Curated Animated Shows**: Instant access to complete cartoon compilations and full episodes:
  - *Tom and Jerry Mega Compilations*
  - *Looney Tunes & Bugs Bunny*
  - *Pokémon Animated Series & Specials*
  - *Ben 10 Full Episodes*
  - *Popeye the Sailor Man Classics*
  - *Official Anime Episodes (Muse Asia & Ani-One)*
- **Real-Time YouTube Search**: Live search bar that queries YouTube directly and loads playable videos inside the site.

---

### 🔗 5. "Paste & Play Any Link" Universal Streamer
- Paste **ANY** external video URL to instantly stream and unlock download options:
  - YouTube video URLs (`youtube.com/watch?v=...` or `youtu.be/...`)
  - Direct `.mp4`, `.webm`, or `.m3u8` video URLs
  - AnimePahe / Gogoanime / third-party embed links

---

### 💾 6. Local Storage Persistence
- **Watchlist / Favorites**: Save any series, movie, cartoon, or YouTube video to your local collection.
- **Watch History**: Remembers your last-watched episode and playback progress across sessions.

---

### 📑 7. Universal Pagination & Anime List Directory
- **Universal Multi-Page Pagination**:
  - Interactive page navigation on all sections: *Latest Releases*, *Trending*, *Popular*, *Movies*, *Cartoons*, *Search*, and *Directory*.
  - Displays `« Prev`, dynamic page buttons (`1`, `2`, `3`, `...`, `N`), and `Next »`.
  - Direct "Go to Page" jump input for fast navigation across hundreds of pages.
  - Mobile-responsive compact mode with touch-friendly buttons.
- **Complete Anime Directory & Catalog**:
  - Accessible via top navigation and mobile bottom bar (**Anime List**).
  - Search anime titles within the directory in real time.
  - Filters by **Status** (*Ongoing / Completed*), **Format** (*TV Series, Movies, OVA*), and **Genres**.
  - Advanced sorting: *Most Popular*, *Top Rated*, *Latest Aired*, *Alphabetical A-Z*, *Alphabetical Z-A*, and *Most Episodes*.
  - **Dual Display Modes**:
    - 🎴 **Poster Grid Mode**: Visual card gallery.
    - 📋 **Directory Table View**: Compact listing displaying Rank, Thumbnail, Title & Genres, Format, Episodes, Star Rating, Status badge, and direct "Watch" button.

---

## ⚡ Execution Commands for All Types & Platforms

Quick-reference cheat sheet for building, testing, deploying, and running every platform and component in this repository.

### 🌐 Type 1: Web Application & Local Backend Server (Node.js)

```bash
# Navigate to the project root
cd anime-stream-hub

# 1. Install all dependencies
npm install

# 2. Start the production Express server (Port 5050 or PORT env var)
npm start

# 3. Alternatively run directly with Node.js
node server.js

# 4. Open in your web browser
# Local: http://localhost:5050
# Live Remote Endpoint: http://rravi.ddns.net:4008
```

---

### 🐳 Type 2: Docker Deployment & Container Orchestration

#### Option A: Docker Compose (Recommended)
```bash
# Build and run containers in background mode
docker compose up -d --build

# View container runtime status
docker compose ps

# Follow live container streaming logs
docker compose logs -f

# Restart container daemon
docker compose restart

# Stop and remove containers and network
docker compose down
```

#### Option B: Standalone Docker CLI
```bash
# 1. Build the production Alpine Docker image
docker build -t anime-stream-hub:latest .

# 2. Run the Docker container
docker run -d \
  --name anime-stream-hub \
  --restart unless-stopped \
  -p 5050:5050 \
  anime-stream-hub:latest

# 3. View container logs
docker logs -f anime-stream-hub

# 4. Stop and remove the container
docker stop anime-stream-hub && docker rm anime-stream-hub
```

---

### 📱 Type 3: Android Mobile App (`android-app/` • Phone & Tablet)

```bash
# Navigate to Mobile subproject directory
cd anime-stream-hub/android-app

# 1. Ensure JAVA_HOME points to JDK 17 or JDK 21
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64

# 2. Build Signed Production Release APK (v1 + v2 signed with anistream-release.jks)
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk (7.6 MB)

# 3. Build Debug APK
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk (9.4 MB)

# 4. Clean and rebuild
./gradlew clean assembleRelease

# 5. Install on connected phone/tablet or emulator via ADB
adb install -r app/build/outputs/apk/release/app-release.apk

# 6. Launch Mobile app via ADB
adb shell am start -n com.anistream.mobile/.MainActivity

# 7. Monitor Mobile app live logcat output
adb logcat -s AniStreamMobile:V AndroidRuntime:E

# 8. Uninstall Mobile app from device
adb uninstall com.anistream.mobile
```

---

### 📺 Type 4: Android TV App (`android-tv/` • TV, Google TV & Fire TV)

```bash
# Navigate to TV subproject directory
cd anime-stream-hub/android-tv

# 1. Ensure JAVA_HOME points to JDK 17 or JDK 21
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64

# 2. Build Signed Production Release APK for TV
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk (8.4 MB)

# 3. Build Debug APK for TV
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk (11 MB)

# 4. Clean and rebuild TV project
./gradlew clean assembleRelease

# 5. Connect to Android TV / Fire TV over Wi-Fi ADB
adb connect <tv-ip-address>:5555
# Verify connection
adb devices

# 6. Install on TV device via ADB
adb install -r app/build/outputs/apk/release/app-release.apk

# 7. Launch TV app via Leanback Launcher
adb shell am start -n com.anistream.tv/.TvMainActivity

# 8. Monitor TV app live logcat output
adb logcat -s AniStreamTv:V AndroidRuntime:E

# 9. Uninstall TV app from device
adb uninstall com.anistream.tv
```

---

### 🎮 Type 5: TV Remote D-Pad Simulation (ADB Keyevents)

When testing Android TV without a physical remote control, simulate D-pad remote interactions via ADB:

| Remote Button | ADB Command |
|:---|:---|
| **D-pad UP** | `adb shell input keyevent 19` |
| **D-pad DOWN** | `adb shell input keyevent 20` |
| **D-pad LEFT** | `adb shell input keyevent 21` |
| **D-pad RIGHT** | `adb shell input keyevent 22` |
| **Select / OK / Center** | `adb shell input keyevent 23` |
| **Back Button** | `adb shell input keyevent 4` |
| **Play / Pause** | `adb shell input keyevent 85` |
| **Rewind (-10s)** | `adb shell input keyevent 89` |
| **Fast Forward (+10s)** | `adb shell input keyevent 90` |
| **Volume Up / Down** | `adb shell input keyevent 24` / `adb shell input keyevent 25` |

---

### 📦 Type 6: Pre-Built Signed Releases Direct Install

Pre-compiled and signed production release APKs are checked into the repository under `releases/`:

```bash
# 1. Install pre-built Mobile Release APK
adb install -r releases/AniStream-Mobile-v1.0.0-release.apk

# 2. Install pre-built TV Release APK
adb install -r releases/AniStream-TV-v1.0.0-release.apk
```

---

### 🔐 Type 7: Keystore Signature Verification (`apksigner`)

Verify APK signature integrity and certificates:

```bash
# Verify Mobile Release APK signature (v1 & v2 scheme)
/home/avinash/Android/Sdk/build-tools/34.0.0/apksigner verify --verbose releases/AniStream-Mobile-v1.0.0-release.apk

# Verify TV Release APK signature (v1 & v2 scheme)
/home/avinash/Android/Sdk/build-tools/34.0.0/apksigner verify --verbose releases/AniStream-TV-v1.0.0-release.apk

# Inspect Keystore certificate details
/usr/lib/jvm/java-21-openjdk-amd64/bin/keytool -list -v \
  -keystore keystore/anistream-release.jks \
  -storepass anistream_release_pass
```

---

### 📡 Type 8: Backend API & Remote Endpoint Verification (`curl`)

Test all backend API routes against local or production server (`http://rravi.ddns.net:4008`):

```bash
# Set Target Host (Local or DDNS Server)
export API_HOST="http://rravi.ddns.net:4008"   # or "http://localhost:5050"

# 1. Healthcheck / Home Page
curl -I "${API_HOST}/"

# 2. Trending Anime (Page 1)
curl -s "${API_HOST}/api/anime/trending?page=1" | jq .

# 3. Latest Releases
curl -s "${API_HOST}/api/anime/latest?page=1" | jq .

# 4. Top Movies
curl -s "${API_HOST}/api/anime/movies?page=1" | jq .

# 5. Cartoon Library (All / Category Filter)
curl -s "${API_HOST}/api/anime/cartoons?category=all&page=1" | jq .
curl -s "${API_HOST}/api/anime/cartoons?category=tom-jerry" | jq .

# 6. Anime Directory Search & Filters
curl -s "${API_HOST}/api/anime/search?q=naruto&page=1" | jq .
curl -s "${API_HOST}/api/anime/directory?status=RELEASING&sort=POPULARITY_DESC&page=1" | jq .

# 7. Series Details by ID
curl -s "${API_HOST}/api/anime/details/21" | jq .

# 8. YouTube Hub Search
curl -s "${API_HOST}/api/youtube/search?q=looney+tunes" | jq .

# 9. Multi-Resolution Download Resolver
curl -s "${API_HOST}/api/download/resolve?url=https://www.youtube.com/watch?v=dQw4w9WgXcQ" | jq .
```

---

## 🐳 Docker Deployment

The application includes production-optimized containerization using multi-stage Alpine images with built-in health checks and non-root security.

### Option A: Using Docker Compose (Recommended)

1. Start the container in background mode:
   ```bash
   docker compose up -d --build
   ```

2. Check container status:
   ```bash
   docker compose ps
   ```

3. View live logs:
   ```bash
   docker compose logs -f
   ```

4. Stop the container:
   ```bash
   docker compose down
   ```

---

### Option B: Using Standalone Docker CLI

1. Build the Docker image:
   ```bash
   docker build -t anime-stream-hub:latest .
   ```

2. Run the container:
   ```bash
   docker run -d \
     --name anime-stream-hub \
     --restart unless-stopped \
     -p 5050:5050 \
     anime-stream-hub:latest
   ```

3. Open in your browser:
   ```
   http://localhost:5050
   ```

---

## 🚀 Local Development Guide

### Prerequisites
- [Node.js](https://nodejs.org/) (v18 or higher)
- npm (v9 or higher)

### Installation & Run

1. Navigate to the project directory:
   ```bash
   cd /home/avinash/.gemini/antigravity/scratch/anime-stream-hub
   ```

2. Start the server:
   ```bash
   npm start
   ```

3. Open your browser:
   ```
   http://localhost:5050
   ```

---

## 📱 Subprojects: Native Android & Android TV Apps

In addition to the responsive Web app, this repository contains **two dedicated native Android subprojects** pre-configured to connect to backend server `http://rravi.ddns.net:4008`:

### 1. 📱 AniStream Mobile (`android-app/`)
- **Package**: `com.anistream.mobile`
- **Target**: Android phones & tablets (SDK 24 - 34)
- **Features**: Bottom navigation, cartoon category chips, universal responsive pagination (`Prev`, `Next`, Jump to Page dialog with page numbers and total items), offline watchlist, ExoPlayer Media3 player with Fullscreen auto-rotation, aspect ratio cycling (Fit, Zoom, Stretch), PiP (Picture-in-Picture), custom cyberpunk adaptive app icons, and multi-resolution original episode and companion English subtitles (`.srt`) Download Manager (1080p, 720p, 480p, MP3).
- **Default Server**: `http://rravi.ddns.net:4008` (configurable in settings)
- **Pre-Built Signed Release**: [`releases/AniStream-Mobile-v1.0.0-release.apk`](releases/AniStream-Mobile-v1.0.0-release.apk)
- **Build Release APK**:
  ```bash
  cd android-app && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew assembleRelease
  ```
  Output: `android-app/app/build/outputs/apk/release/app-release.apk`

### 2. 📺 AniStream TV (`android-tv/`)
- **Package**: `com.anistream.tv`
- **Target**: Android TV, Google TV & Fire TV (SDK 24 - 34)
- **Features**: 10-foot Leanback UI, D-pad remote navigation with focus scaling & glowing borders, dynamic Spotlight hero banner, 4 horizontal category rails with universal TV D-pad focusable pagination bar (`‹ Prev Page`, `Next Page ›`, page status), remote-friendly fullscreen TV player with D-pad aspect ratio toggle (`Fit`, `Zoom`, `Stretch`), center badge indicators, and matching TV banner.
- **Default Server**: `http://rravi.ddns.net:4008` (configurable in settings)
- **Pre-Built Signed Release**: [`releases/AniStream-TV-v1.0.0-release.apk`](releases/AniStream-TV-v1.0.0-release.apk)
- **Build Release APK**:
  ```bash
  cd android-tv && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew assembleRelease
  ```
  Output: `android-tv/app/build/outputs/apk/release/app-release.apk`

---

## 📂 Project Architecture

```
anime-stream-hub/
├── package.json          # Web backend dependencies & scripts
├── server.js             # Express backend (AniList API, YouTube Scraper, Stream Resolvers)
├── Dockerfile            # Container deployment configuration
├── docker-compose.yml    # Multi-container orchestration
├── README.md             # Documentation
├── keystore/             # 🔐 Production release keystore & signing specs
├── releases/             # 📦 Signed Production Release APKs ready to install
│   ├── AniStream-Mobile-v1.0.0-release.apk
│   └── AniStream-TV-v1.0.0-release.apk
├── public/               # Web client SPA
│   ├── index.html        # Responsive HTML5 layout
│   ├── favicon.svg       # Cyberpunk neon brand SVG icon
│   ├── css/style.css     # Glassmorphism & auto-adjusting responsive grid
│   └── js/app.js         # Client controller, HLS player & download triggers
├── android-app/          # 📱 Standalone Android Phone/Tablet Subproject
│   ├── build.gradle      # Root Gradle script for mobile app
│   ├── settings.gradle
│   ├── gradlew
│   └── app/
│       ├── build.gradle  # Mobile app build config, signing config & ExoPlayer dependencies
│       ├── anistream-release.jks # Release signing keystore
│       └── src/main/     # Java code, layouts, and drawables
└── android-tv/           # 📺 Standalone Android TV Subproject
    ├── build.gradle      # Root Gradle script for TV app
    ├── settings.gradle
    ├── gradlew
    └── app/
        ├── build.gradle  # TV app build config, signing config & Leanback UI dependencies
        ├── anistream-release.jks # Release signing keystore
        └── src/main/     # Java code, TV rails, D-pad remote handlers
```

---

## 📱 Mobile Responsiveness Breakdown

| Screen Size | Device Category | Columns | Navigation Mode | Player Behavior |
|:---|:---|:---:|:---:|:---|
| `< 540px` | Mobile Phones | 2 | Bottom Floating Nav Bar | Auto 16:9 + Docked Mini-Player on scroll |
| `540px - 768px` | Large Phones / Phablets | 3 | Bottom Floating Nav Bar | Responsive Aspect-Ratio Container |
| `768px - 1024px` | Tablets / iPad | 4 | Header Nav + Bottom Nav | Full Controls + Cinema Mode |
| `> 1024px` | Laptops & Desktops | 5 - 6 | Desktop Header Nav | Full Viewport Auto-Adjusted Grid |

---

## 🛡️ License
ISC License
