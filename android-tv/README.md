# 📺 AniStream TV — Android TV Application

Dedicated 10-foot Leanback Android TV application (`com.anistream.tv`) designed specifically for television screens and D-pad remote controllers.

---

## ✨ Features

- **10-Foot Leanback UI**:
  - Full-screen TV layout with horizontal browsing rails.
  - Distinct high-contrast focus rings and 1.08x scale zoom animations when navigating via D-pad remote keys.
  - Zero touchscreen dependencies (`android.hardware.touchscreen` marked optional).
- **Spotlight Hero Section**:
  - Dynamically showcases the top featured anime or cartoon title on focus with backdrop art, synopsis, and 1-click "Watch Now" remote button.
- **Top TV Navigation Bar**:
  - Instant navigation between categories: **Trending Anime**, **Classic Cartoons**, **Top Movies**, and **Popular Hits**.
- **Remote-Friendly Fullscreen TV Player**:
  - Integrated ExoPlayer Media3 engine for HLS / MP4 playback.
  - Key interceptors for TV remote controls:
    - **DPAD_CENTER / ENTER**: Play / Pause toggle.
    - **DPAD_LEFT / DPAD_RIGHT**: Seek backward / forward by 10 seconds.
    - **DPAD_DOWN / UP**: Show / hide TV player controls overlay and episode rail.
    - **BACK**: Return to home screen.
- **Server Address Configuration**:
  - D-pad accessible Settings button in the top bar to set backend IP (`http://10.0.2.2:5050` or LAN Wi-Fi IP).

---

## 🛠️ Tech Stack & Requirements

- **Target SDK**: Android 34 (Android 14)
- **Min SDK**: Android 24 (Android 7.0 Nougat)
- **TV Guidelines**: Includes `LEANBACK_LAUNCHER` category and `@drawable/tv_banner` (320x180 aspect ratio).
- **Key Libraries**:
  - `androidx.leanback:leanback:1.0.0`
  - `androidx.media3:media3-exoplayer:1.2.1` & `media3-ui:1.2.1`
  - `com.github.bumptech.glide:glide:4.16.0`
  - `com.google.android.material:material:1.11.0`

---

## 🚀 Building & Running

### 1. Build Debug APK
```bash
cd android-tv
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew assembleDebug
```

The APK will be generated at:
```
android-tv/app/build/outputs/apk/debug/app-debug.apk
```

### 2. Install on Android TV / Fire TV / Google TV
```bash
adb connect <tv-ip-address>:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 3. TV Remote Control Key Reference
| TV Remote Key | Action |
|:---|:---|
| **D-pad Arrows (↑ ↓ ← →)** | Move focus between cards, rails, and buttons |
| **Select / OK / Center** | Open anime details, start playback, select episode |
| **Play / Pause Button** | Toggle video playback |
| **Rewind / Fast-Forward** | Seek -10s / +10s |
| **Back Button** | Exit player overlay or return to previous screen |
