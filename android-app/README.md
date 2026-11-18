# 📱 AniStream Mobile — Android Application

Dedicated native Android client for smartphones and tablets (`com.anistream.mobile`).

---

## ✨ Features

- **Mobile-First Glassmorphic Interface**: Custom dark neon UI tuned for 1-hand reachability and high DPI displays.
- **Bottom Navigation**: Fast tab-switching between **Home**, **Cartoons**, **Movies**, and **Watchlist**.
- **Cartoon Quick Filters**: Filter tags for *All*, *Tom & Jerry*, *Looney Tunes*, *Ben 10*, *Pokemon*, and *Popeye*.
- **Native Video Player (ExoPlayer Media3)**:
  - Hardware accelerated HLS (`.m3u8`) and MP4 streaming.
  - Multi-server fallback engine + WebView container for iframe sources.
  - **Full Screen Toggle & Auto-Rotation**: 1-click fullscreen toggle button, automatic sensor landscape/portrait transition, immersive sticky mode hiding system navigation/status bars, and back-button interceptor.
  - **Screen Aspect Ratio Cycling**: Switch between `Fit (16:9 Standard)`, `Zoom (Crop to Fill Screen / Notch)`, and `Stretch (Full Viewport)` with on-screen HUD pop-up indicators.
  - **1-Click Picture-in-Picture (PiP)**: Dedicated player PiP button and background home minimize.
  - Auto-hiding HUD controls on video tap with smooth fade animations.
  - Quick episode carousel inside the player.
- **Custom Branded Neon App Icon**: Adaptive vector icon (`@mipmap/ic_launcher` & `@mipmap/ic_launcher_round`) featuring the AniStream Cyberpunk shield, neon play emblem, and anime star sparkle.
- **Multi-Resolution Download Manager**:
  - Direct integration with system `DownloadManager`.
  - Supports downloads across multiple resolutions: 1080p Full HD, 720p HD, 480p SD, 360p, and MP3 audio extraction.
  - Saves directly to public `/sdcard/Download/AniStream/` storage.
- **Offline Watchlist**: Local `SharedPreferences` persistence for your saved series and movies.
- **Pre-Configured Backend Endpoint**: Pre-configured out of the box to connect directly to `http://rravi.ddns.net:4008`. Also supports custom server overrides via the Settings (⚙️) icon.

---

## 🛠️ Tech Stack & Requirements

- **Target SDK**: Android 34 (Android 14)
- **Min SDK**: Android 24 (Android 7.0 Nougat)
- **Language**: Java 17 / 21
- **Key Libraries**:
  - `androidx.media3:media3-exoplayer:1.2.1` & `media3-ui:1.2.1`
  - `com.github.bumptech.glide:glide:4.16.0` (Image loading & caching)
  - `com.google.android.material:material:1.11.0`
  - `androidx.appcompat:appcompat:1.6.1`

---

## 🚀 Building & Running

### 1. Build Signed Production Release APK
Ensure `JAVA_HOME` points to a full JDK (e.g. OpenJDK 21 or 17 with jlink):
```bash
cd android-app
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew assembleRelease
```

The signed release APK will be generated at:
```
android-app/app/build/outputs/apk/release/app-release.apk
```
(A pre-built signed copy is also available in root `releases/AniStream-Mobile-v1.0.0-release.apk`).

### 2. Build Debug APK
```bash
cd android-app
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew assembleDebug
```

### 3. Install on Device or Emulator
```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

### 4. Connect to Backend Server
By default, the app is configured to connect to `http://rravi.ddns.net:4008`. You can change this anytime:
1. Tap the **Settings** (⚙️) icon in the top right.
2. Enter your custom server address.
3. Tap **Connect & Save**.
