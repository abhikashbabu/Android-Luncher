# Crimson Glass Launcher 0.3

Native Android Frosted Glassmorphism Launcher with **1:1 Continuous Touch-Sliding Motion & Spring Physics** and **Lively Crimson Red & Deep Charcoal Mesh Gradient** theme (no flat pure black).

## Build Instructions

Build using JDK 17:
```bash
./gradlew assembleDebug
# or on Windows:
gradlew.bat assembleDebug
```
The compiled APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Key Features & Smooth Motion Upgrades in v0.3

1. **Continuous 1:1 Touch-Tracking Motion Engine (No Abrupt Jumps)**:
   - **3-Page Horizontal Home Pager**: Drag left/right anywhere on Home with continuous finger-tracking (`VelocityTracker` + rubber-band overscroll + smooth deceleration/spring settle), page scale/alpha depth transitions, and animated glass indicator pills.
   - **Smooth Pull-Up App Drawer & Universal Search**: Swipe up from anywhere on the Home screen to continuously slide the Frosted Glass App Drawer up (`translationY`) while scaling and blurring (`RenderEffect` on Android 12+) the Home stage behind it.
   - **Smooth Left-Edge Glass Navigation Drawer**: Swipe right from the left edge to continuously pull out the Frosted Glass Navigation Drawer (`translationX`), replacing the old static popup dialog.
2. **Frosted Glassmorphism & Lively Crimson-Charcoal Gradient Wallpaper**:
   - Multi-orb radial crimson (`#E50914`, `#FF2A3C`, `#9E0B22`) and deep charcoal (`#14131A`, `#1E1924`) shader wallpaper with live touch-parallax motion.
   - Multi-layered translucent glass cards with top specular highlight rims and glowing crimson active borders.
3. **Multi-Select Customization Mode**:
   - Long-press any app or folder on the Home screen (or tap the **Multi-Select** pill on Page 2) to select multiple apps/folders with glowing crimson borders, checkmark badges, and the floating glass **Multi-Select Options** bar (`Group`, `Uninstall`, `Clear`, `Create Folder`).
4. **Interactive Glass Widgets & Floating Volume Slider**:
   - **Page 1**: Frosted Glass Clock & Weather Hero Card, Universal Search Bar Pill, Pinned Apps Grid, Mini Now Playing & Volume/Status Cards, and 5-icon Frosted Glass Bottom Dock.
   - **Page 2**: Interactive Music Player with live animated crimson waveform bars, Live CPU / RAM / Battery radial ring gauges (`ActivityManager` + `BatteryManager`), and Categorized Smart Glass Folders (`Social`, `Media`, `Gaming`, `Work`).
   - **Right Edge**: Touch-draggable Floating Glassmorphic Vertical Volume Slider synced with `AudioManager.STREAM_MUSIC` and hardware volume keys.
   - **Universal Search Drawer**: Top Glass Search Bar with Voice Search trigger, glowing category filter pills (`All`, `Games`, `Social`, `Utility`, `Work`, `Media`), Recent Apps row, Installed Apps grid, and bottom Instant Web Search & System Settings glass card.
