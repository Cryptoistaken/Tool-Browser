# Personal Browser

A lightweight, modern personal Android browser built with native Kotlin and WebView.

## Features

- Multi-tab browsing with easy tab management
- Clean, modern UI with rounded corners and Material You design
- Bookmarks with persistent storage (Room DB)
- Browsing history with search
- Dark mode support (follows system theme)
- Desktop site toggle
- Share & Copy URL
- Clear browsing data
- URL bar with smart search (Google fallback)
- Swipe to refresh
- Pull-to-refresh support

## Architecture

- **MVVM** pattern with `ViewModel` + `LiveData`
- **Room** database for bookmarks and history
- **Hilt** dependency injection
- **Kotlin Coroutines** for async operations
- **ViewBinding** for type-safe view access
- Clean separation: `data/`, `ui/`, `utils/`, `di/` packages

## Building the APK

### Locally

```bash
# Debug APK
./gradlew assembleDebug

# Release APK (unsigned)
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

### Via GitHub Actions

Push to `main` or `master` and GitHub Actions will automatically build both Debug and Release APKs as artifacts downloadable from the Actions tab.

To enable signed releases, add these secrets to your GitHub repository:
- `KEYSTORE_BASE64` — base64-encoded `.jks` keystore
- `KEY_ALIAS` — key alias
- `KEY_PASSWORD` — key password
- `STORE_PASSWORD` — store password

Generate a keystore:
```bash
keytool -genkey -v -keystore browser.jks -keyalg RSA -keysize 2048 -validity 10000 -alias browser
base64 browser.jks | pbcopy   # macOS — copies to clipboard
```

## Adding / Removing Features

Each feature is isolated in its own class for easy removal:

| Feature | Files to change |
|---|---|
| Bookmarks | `BookmarkRepository`, `BookmarkDao`, `Bookmark` |
| History | `HistoryRepository`, `HistoryDao`, `HistoryEntry` |
| Desktop mode | `toggleDesktopMode()` in `MainActivity` |
| Share | `shareCurrentUrl()` in `MainActivity` |
| Tabs | `TabsAdapter`, `Tab` model, `BrowserViewModel` |
| URL handling | `UrlUtils` |

## Requirements

- Android 7.0+ (API 24)
- Java 17
- Android Studio Hedgehog or newer
