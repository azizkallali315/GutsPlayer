# GutsPlayer — Android Studio Project

This is a proper native Android app that wraps your index.html in a WebView
and provides REAL music notification controls (lock screen, notification shade).

## How it works
- Your index.html runs inside a WebView
- A JavaScript bridge (AndroidBridge) connects your audio events to a native service
- MusicService is a MediaBrowserService that creates the notification with prev/play/pause/next
- No external APK builder needed — this builds with Android Studio directly

---

## Setup Steps

### 1. Install Android Studio
Download from: https://developer.android.com/studio
Install with default settings (includes SDK & Gradle).

### 2. Open the project
- Open Android Studio
- Click "Open" → select the GutsPlayerApp folder
- Wait for Gradle sync to finish (first time takes ~5 minutes, downloads dependencies)

### 3. Build the APK
- Menu: Build → Build Bundle(s) / APK(s) → Build APK(s)
- Or press Shift+F10 to run directly on a connected phone
- APK will be at: app/build/outputs/apk/debug/app-debug.apk

### 4. Install on your phone
Option A — USB: Enable Developer Options on phone → USB Debugging → plug in → press Run in Android Studio
Option B — File: Copy app-debug.apk to your phone → open it → tap Install

---

## What you get
✅ Music notification in status bar while playing
✅ Prev / Play-Pause / Next buttons in notification
✅ Lock screen controls
✅ Headphone button support
✅ Stays alive when screen is off (foreground service)
✅ Your full GutsPlayer UI unchanged

## Your index.html
It's in: app/src/main/assets/index.html
Any changes you make there will be included in the next build.

## Updating your app
1. Replace app/src/main/assets/index.html with your new version
2. Build APK again
