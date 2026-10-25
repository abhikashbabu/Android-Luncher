# Crimson Launcher 0.2

Native Android launcher. Build using JDK 17 with `gradlew.bat assembleDebug`.

## Navigation

- Swipe up on home to open the app library.
- Tap the left edge or swipe right from it for the category panel and launcher settings.
- Back or Home returns to the cached home screen.
- Tap search to search apps; long press an app to pin/unpin it on home or view app info.
- Refresh installed apps through Launcher settings after installing or removing an app.

## Changes

Soft peach/cream accents (#FFB4A9), warm primary text (#F4EEE8), muted labels, and dark charcoal surfaces. No home scrolling, headings for essentials, focus card or library button. Home fits up to three rows based on screen height. Clock renders immediately and updates on minute boundaries while visible. Home views, labels and icon constant states are reused; short translation/alpha transitions use platform animations.

## Limits

No true backdrop blur, custom icon pack, weather, media controls, folders or multi-select yet. App categories depend on installed app metadata. Build and device launch checks do not establish frame-rate or lag-free behavior across devices. Large app libraries currently use a GridLayout and may need virtualization as the library grows.
