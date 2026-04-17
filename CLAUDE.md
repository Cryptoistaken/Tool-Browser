# CLAUDE.md — Tool Browser

## Project Overview

**Tool Browser** is a lightweight, native Android browser app built with Kotlin and WebView. It is a personal-use project focused on clean architecture, Material You design, and a fast multi-tab browsing experience. The compiled debug APK lives at `app/build/outputs/apk/debug/app-debug.apk`.

---

## Architecture

The app follows **MVVM** with a clean package separation:

```
app/src/main/java/com/personal/browser/
├── BrowserApplication.kt           # Hilt application entry point
├── data/
│   ├── database/                   # Room DAOs and database definition
│   │   ├── BookmarkDao.kt
│   │   ├── BrowserDatabase.kt
│   │   └── HistoryDao.kt
│   ├── model/                      # Data models
│   │   ├── Bookmark.kt
│   │   ├── HistoryEntry.kt
│   │   └── Tab.kt
│   └── repository/                 # Repository layer
│       ├── BookmarkRepository.kt
│       └── HistoryRepository.kt
├── di/
│   └── DatabaseModule.kt           # Hilt DI module for Room
├── ui/
│   ├── activity/
│   │   ├── MainActivity.kt         # Primary browser activity
│   │   └── SettingsActivity.kt
│   ├── adapter/
│   │   ├── BookmarkHistoryAdapter.kt
│   │   └── TabsAdapter.kt
│   └── viewmodel/
│       └── BrowserViewModel.kt     # Central ViewModel
└── utils/
    └── UrlUtils.kt                 # URL parsing / smart search
```

**Key patterns:**
- `ViewModel` + `LiveData` for reactive UI updates
- `Room` database for bookmark and history persistence
- `Hilt` for dependency injection (KSP-generated)
- `ViewBinding` for type-safe view access
- Kotlin Coroutines for async DB operations

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | XML layouts, ViewBinding, Material 3 |
| Architecture | MVVM |
| DI | Hilt (Dagger) |
| Database | Room (KSP) |
| Async | Kotlin Coroutines |
| Build | Gradle (Kotlin DSL via `build.gradle`) |
| Min SDK | API 24 (Android 7.0) |
| Compile SDK | Latest in `build.gradle` |
| Java toolchain | Java 17 |

---

## Design System

The app uses a custom design language documented in `DESIGN.md`, inspired by Cursor's warm minimalism:

- **Color palette:** Warm off-white surfaces (`#f2f1ed`), near-black text (`#26251e`), accent orange (`#f54e00`), error crimson (`#cf2d56`)
- **Borders:** `oklab()`-space warm brown at various alpha levels (10%, 20%, 55%)
- **Radius:** 8px for cards/buttons; full-pill (9999px) for tags/filters
- **Shadows:** Large diffused blur values (28px, 70px) for elevated cards
- **Dark mode:** Supported via `values-night/` resource variants

Relevant resource directories:
- `res/drawable/` — Vector icons (back, forward, bookmark, history, share, etc.)
- `res/layout/` — `activity_main.xml`, `activity_settings.xml`, `item_tab.xml`, `item_bookmark_history.xml`
- `res/values/` — `colors.xml`, `strings.xml`, `themes.xml`
- `res/values-night/` — Dark mode overrides for colors and themes
- `res/color/url_bar_stroke.xml` — State-aware URL bar border color
- `res/xml/network_security_config.xml` — Network security policy

---

## Features

- Multi-tab browsing with `TabsAdapter` and tab state in `BrowserViewModel`
- Bookmarks persisted in Room via `BookmarkDao` / `BookmarkRepository`
- Browsing history with search via `HistoryDao` / `HistoryRepository`
- Smart URL bar — passes raw URLs through, falls back to Google search
- Pull-to-refresh / swipe-to-refresh on WebView
- Desktop site toggle (`toggleDesktopMode()` in `MainActivity`)
- Share & copy URL actions
- **Copy Cookie button** (`btnCopyCookie`) — top-left dedicated button, visible only on http/https pages.
  Copies the current page's cookies to clipboard, triggers haptic vibration feedback, and shows a
  Snackbar success notification. Uses `ic_copy` drawable. Requires `VIBRATE` permission in manifest.
- Clear browsing data on demand and optionally on exit
- Dark mode (follows system theme)

---

## Key Implementation Notes

### btnCopyCookie (top-left icon)
- **Visible** when the current URL starts with `http://` or `https://`
- **Hidden** (INVISIBLE) on the new tab / about:blank screen
- On click: calls `copyCurrentCookies()`, vibrates via `VibrationEffect` (API 26+) with fallback, shows Snackbar "Cookies copied"
- Icon: `@drawable/ic_copy`

### Clear data on exit
- Triggered in both `onStop` and `onDestroy` when `isFinishing` is true and `PREF_CLEAR_ON_EXIT` is set. A `didClearOnExit` flag prevents double-clearing if both run.
- `onStop` handles the common case (user swipes app away from recents); `onDestroy` is a fallback for explicit `finish()` calls. `isFinishing` is only true when the activity is truly finishing, not when it's backgrounded.
- Called **before** `webViews.values.forEach { it.destroy() }` so each WebView is still alive when `clearCache`/`clearHistory` are invoked.
- Uses `performClearDataSync()` which calls `cookieManager.removeAllCookies(null)` + `cookieManager.flush()` synchronously — the async callback variant is unreliable when the process is about to die.
- Room history is cleared via `runBlocking { viewModel.clearHistorySync() }` — this blocks until the DELETE finishes, ensuring history is actually erased before the process exits. `clearHistorySync()` is a `suspend fun` on `BrowserViewModel` that calls `historyRepository.clearHistory()` directly.
- `performClearData(showToast)` (async variant with `viewModelScope.launch`) is still used for the manual "Clear Data" menu action.

### User scripts
- Injection entry point is `injectScripts(view: WebView)`, called from `onPageFinished`.
- Scripts are wrapped to respect `document.readyState`: if the document is still loading the wrapper defers via `DOMContentLoaded`; otherwise it runs immediately. This prevents scripts from running before the DOM exists.
- Backticks, backslashes, and `${` in user script code are escaped before embedding so arbitrary JS survives the string wrapper intact.
- SPA navigation (Instagram, Facebook, Gmail, etc.) is handled by `injectSpaNavigationBridge(view)`, also called from `onPageFinished` (before `injectScripts`). The bridge monkey-patches `history.pushState` and `history.replaceState` and listens for `popstate`, then calls `window.__toolBrowserRunScripts()` after a 350 ms settle delay so scripts re-run after each client-side route change. `injectScripts` registers `window.__toolBrowserRunScripts = __runAll` so the bridge always finds it.
- The bridge is idempotent — it checks `window.__toolBrowserBridgeInstalled` before installing, so re-calling `onPageFinished` on the same page is safe.
- `doUpdateVisitedHistory` is no longer used for script injection — it caused double-injection on normal loads and premature injection before new content rendered on SPAs.
- Observe LiveData with `viewLifecycleOwner` — sheets use a dedicated local `LifecycleOwner` wrapper to avoid leaking Activity observers after dismiss.

### SwipeRefreshLayout + WebView
- `SwipeRefreshLayout.setOnChildScrollUpCallback` is set so pull-to-refresh only triggers when the WebView is scrolled to the very top, preventing accidental refreshes mid-page.

### ProgressBar
- Initial `android:visibility="invisible"` (not `gone`) avoids layout re-flow on show/hide.

---

## Build Commands

```bash
# Debug APK
./gradlew assembleDebug

# Release APK (unsigned)
./gradlew assembleRelease

# Clean
./gradlew clean
```

Output path: `app/build/outputs/apk/debug/app-debug.apk`

---

## Adding / Removing Features

Each feature is cleanly isolated:

| Feature | Files to touch |
|---|---|
| Bookmarks | `BookmarkRepository`, `BookmarkDao`, `Bookmark` model |
| History | `HistoryRepository`, `HistoryDao`, `HistoryEntry` model |
| Tabs | `TabsAdapter`, `Tab` model, `BrowserViewModel` |
| Desktop mode | `toggleDesktopMode()` in `MainActivity` |
| Share | `shareCurrentUrl()` in `MainActivity` |
| URL handling | `UrlUtils` |
| Settings screen | `SettingsActivity`, `activity_settings.xml` |
| Copy Cookie button | `btnCopyCookie` in `activity_main.xml`, `copyCurrentCookies()` in `MainActivity` |

---

## CI / Signed Releases

GitHub Actions workflow (`.github/`) builds both Debug and Release APKs on push to `main`/`master`. For signed releases, add these repository secrets:

- `KEYSTORE_BASE64` — base64-encoded `.jks` keystore
- `KEY_ALIAS`
- `KEY_PASSWORD`
- `STORE_PASSWORD`

Generate a keystore:
```bash
keytool -genkey -v -keystore browser.jks -keyalg RSA -keysize 2048 -validity 10000 -alias browser
base64 browser.jks | pbcopy   # macOS
```

---

## Notes for Claude

- This is a **single-module Android project** — all source lives under `app/`.
- The build system uses `build.gradle` (Groovy DSL, not KTS). Check `app/build.gradle` for dependency versions before suggesting library updates.
- Hilt generates significant boilerplate in `app/build/generated/` — do not edit generated files.
- Room schema migrations are not yet configured; destructive migration is likely in use for development.
- The `ui/fragment/` directory exists but is currently empty — fragments are not yet used.
- WebView logic (client, chrome client, JS interface) lives entirely inside `MainActivity.kt`.
- `UrlUtils.kt` handles smart search fallback: if the input is not a valid URL, it constructs a Google search query.
- `VIBRATE` permission is declared in `AndroidManifest.xml` for haptic feedback on the copy-cookie button.
