# 🎬 AniStream HUB

> **A Mobile-First, Responsive Streaming & Download Hub for Animations, Cartoons, Series, Movies & YouTube Videos.**

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

### 📥 3. Video Download Manager
- **Direct MP4 Downloads**: 1-click browser download trigger with custom clean filenames.
- **YouTube Video Downloader**: Integrated access to Cobalt HD (1080p/720p ad-free), Y2Mate (MP4/MP3), and SaveFrom.
- **Stream Link Exporter**: One-click copy stream source URL for VLC, MPV, or external download managers.

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

## 🚀 Quick Start Guide

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

## 📂 Project Architecture

```
anime-stream-hub/
├── package.json          # Dependencies & scripts
├── server.js             # Express backend (AniList API, YouTube Scraper, Stream Resolvers)
├── README.md             # Documentation
└── public/
    ├── index.html        # Mobile-first responsive HTML5 layout & modal containers
    ├── css/
    │   └── style.css     # CSS custom variables, glassmorphism, responsive grid & media queries
    └── js/
        └── app.js        # SPA client controller, HLS player, server switcher, downloads & storage
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
