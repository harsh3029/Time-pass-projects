# AnyTranslate 🌍

An Android app that translates **inside any app** and works as a **live translator**.
All translation runs on your phone with Google ML Kit. Once a language pack (~30 MB) is downloaded,
it works offline and your text never leaves the device.

## Features

### Translate anywhere
| How | What it does |
| --- | --- |
| **Floating button** | A bubble sits on top of every app. Tap it and every piece of text on screen is covered with its translation. Tap a box to peek at the original; tap outside to close. Drag it anywhere; long-press to open the app. |
| **Text inside images (OCR)** | On Android 11+, text in photos, memes and games gets translated too. Latin, Devanagari (Hindi/Marathi), Chinese, Japanese and Korean scripts are supported. |
| **Select text → AnyTranslate** | In any app, select text and pick *AnyTranslate* from the selection menu. In a text box, **Replace** swaps your words for the translation, so you can reply to someone in their language. |
| **Share → AnyTranslate** | Share text from any app to get a quick translation. |
| **Quick Settings tile / accessibility shortcut** | Other ways to translate the current screen without the bubble. |

### Live translator
| Mode | What it does |
| --- | --- |
| **Conversation** | Each person taps the mic under their language and speaks. The translation shows up as a chat and is read aloud. **Face-to-face** splits the screen and flips the top half so the person across the table can read it. |
| **Live subtitles** | Listens continuously and shows running translated subtitles as people speak. Good for lectures, announcements, TV or a tour guide. |

### Also
* **Translate tab:** type, paste or dictate text; detects the language automatically, with copy, listen and share.
* **Offline tab:** download or delete language packs (59 languages).

## Install

1. Open the **Actions** tab of this repo → the latest **AnyTranslate APK** run → download the `AnyTranslate-apk` artifact and unzip it.
   (Or push a tag like `v1.0.0` and grab `AnyTranslate.apk` from **Releases**.)
2. Copy `AnyTranslate.apk` to your phone and open it (allow "Install unknown apps" when asked).
   That's the build for almost every modern phone (64-bit ARM). For an old 32-bit phone use
   `AnyTranslate-armv7.apk`; `AnyTranslate-universal.apk` runs anywhere, including emulators, but is much bigger.
3. Open AnyTranslate → **Anywhere** → **Turn on screen translation**, and switch on *AnyTranslate screen translator* in Accessibility settings.
   * **Switch greyed out?** On Android 13+, sideloaded apps need: *Settings → Apps → AnyTranslate → ⋮ → Allow restricted settings*, then try again.
4. The first time you use a language, its pack downloads (~30 MB). After that it's offline.

Each CI run signs with a fresh debug key, so if an update won't install over the old version, uninstall first.

## Requirements
* Android 8.0+ (OCR inside images needs Android 11+).
* Live translator uses the phone's speech recognizer (the Google app on most phones) and text-to-speech voices.
  Some languages need an internet connection for speech recognition. Translation itself is always on-device.
* OCR models come from Google Play services and download automatically on first use.

## Build it yourself

```bash
cd AnyTranslate
./gradlew assembleDebug      # app/build/outputs/apk/debug/
./gradlew assembleRelease    # app/build/outputs/apk/release/ (minified, signed with the debug key)
```
Requires JDK 17 and the Android SDK (API 35). Or open the `AnyTranslate` folder in Android Studio.

## How it works

```
app/src/main/java/com/timepass/anytranslate/
├── translate/TranslationEngine.kt      ML Kit translation, language ID, language-pack downloads
├── anywhere/
│   ├── TranslateAccessibilityService   reads on-screen text on demand, draws the overlay
│   ├── ScreenText.kt                   walks the accessibility tree for visible text
│   ├── OcrReader.kt                    ML Kit OCR on a screenshot for text inside images
│   ├── TranslationOverlayView.kt       paints translations over the original text
│   ├── FloatingBubble.kt               the draggable translate button
│   └── TranslateTileService.kt         Quick Settings tile
├── quick/QuickTranslateActivity.kt     text-selection menu and share-sheet popup
├── speech/                             SpeechRecognizer and TextToSpeech wrappers
└── ui/                                 Compose screens: Translate, Live, Anywhere, Offline
```

**Privacy:** the accessibility service only reads the screen when you tap the bubble, tile or shortcut.
It listens for window changes only to close a stale overlay. Nothing is stored or uploaded.
