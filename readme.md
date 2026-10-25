# 🔥 Crimson Glass Launcher

**Open-Source 120FPS Frosted Glassmorphism Android Launcher with Lively Crimson-Charcoal Mesh Gradients & Continuous 1:1 Touch-Sliding Physics.**

Developed by **Abhikash Babu** · Open Source under the **MIT License** — Contributions Welcome!

## 🛠️ Testing
![Crimson Glass Launcher](https://raw.githubusercontent.com/abhikashbabu/Crimson-Glass-android-Launcher/refs/heads/main/test5.png)

## 🌐 Live Interactive Web Simulator & APK Download

- 🎮 **Try the Launcher Live in Your Browser**: Open [index.html](https://www.google.com/search?q=index.html) (or visit the **GitHub Pages Live Demo** link in the repository's About section) to experience the 3-page Home Screen, Multi-Select Mode, Left Glass Navigation Drawer, Floating Volume Slider, and Universal Search Drawer right in your browser!
- 📥 **Download Latest Android APK**: Head over to the \[suspicious link removed\] page to download and install the latest `.apk` on your Android phone (Android 8.0+ / API 26–35).

## ✨ Key Features

| Feature | Description |
| :--- | :--- |
| **Frosted Glassmorphism UI** | Translucent blurred glass cards, specular rim highlights, and glowing crimson (`#FF2A3C`) active borders over a lively Crimson Red (`#E50914`) & Deep Charcoal (`#14131A`) mesh gradient (zero flat pure black). |
| **120FPS 1:1 Continuous Touch Motion** | Every gesture—horizontal 3-page Home paging, vertical App Drawer pull-up, and left-edge Navigation Drawer slide—tracks your finger 1:1 in real time using `VelocityTracker` and GPU `LAYER_TYPE_HARDWARE` textures. |
| **System Gesture Task Manager Guard** | Dynamically respects Android's bottom system gesture insets (`WindowInsets.getSystemGestureInsets()`) and `excludeFromRecents="true"` so Android's Recents / Task Manager gesture opens with zero conflict or jitter. |
| **Multi-Select Mode & Smart Folders** | Long-press any app or folder on the Home screen to select multiple items with glowing crimson borders and group them via the floating glass action bar. |
| **Interactive Glass Widgets** | Includes a Weather & Clock Hero Card, Now Playing Music Player with live animated waveform bars, and live CPU / RAM / Battery radial ring gauges. |
| **Floating Glass Volume Overlay** | Touch-draggable vertical glass volume slider on the right edge synced with `AudioManager.STREAM_MUSIC` and hardware volume buttons. |
| **Universal Search & App Drawer** | Instant app filtering, glowing category pills (`All`, `Games`, `Social`, `Utility`, `Work`, `Media`), Voice Search trigger, and one-tap Web/System Settings search card. |

## 🖼️ Matching Wallpapers Included

Inside the [wallpapers/](https://www.google.com/search?q=wallpapers/) folder, you'll find high-resolution `9:16` wallpapers tailored for both your **Home Screen** (`crimson_glass_home_wallpaper.jpg`) and **Lock Screen** (`crimson_glass_lock_wallpaper.jpg`) so your entire device matches the Crimson Glass aesthetic.

## 🛠️ Build from Source

Crimson Glass Launcher is written in pure, zero-bloatware native Android Java (`minSdk 26`, `targetSdk 35`, `compileSdk 35`) with an ultra-small APK footprint (~25 KB).

```bash
# Clone the repository
git clone https://github.com/<your-username>/crimson-glass-launcher.git
cd crimson-glass-launcher

# Build Debug APK (requires JDK 17)
./gradlew assembleDebug
# Or on Windows:
gradlew.bat assembleDebug
```

The compiled APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.

## 🤝 Open Source — How to Contribute!

This project is **100% Open Source** and we warmly welcome contributors! Whether you want to build new Frosted Glass widgets, add custom icon pack support, create new gradient color themes, or improve animations:

1. **Fork** this repository.
2. Create a feature branch (`git checkout -b feature/my-glass-widget`).
3. Test your changes on an Android device or in the `index.html` simulator.
4. Commit your changes and open a **Pull Request**!

See [CONTRIBUTING.md](https://www.google.com/search?q=CONTRIBUTING.md) for detailed guidelines.

## 📄 License

Licensed under the [MIT License](https://www.google.com/search?q=LICENSE). Created and maintained by **Abhikash Babu**.
