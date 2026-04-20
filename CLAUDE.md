# CLAUDE.md — Tool Browser

## Project Overview

**Tool Browser** is a lightweight, native Android browser built with Kotlin, Jetpack Compose, and WebView. Personal-use project focused on clean MVVM architecture, Material 3 design, and a fast multi-tab browsing experience.

Compiled debug APK: `app/build/outputs/apk/debug/app-debug.apk`

---

## Architecture

MVVM with clean package separation. Zero Hilt — DI replaced by manual `AppDatabase.getInstance()` singleton.

```
app/src/main/java/com/personal/browser/
├── BrowserApplication.kt           # Plain Application subclass
├── data/
│   ├── database/
│   │   ├── BookmarkDao.kt
│   │   ├── BrowserDatabase.kt      # Room singleton (getInstance)
│   │   └── HistoryDao.kt
│   ├── model/
│   │   ├── Bookmark.kt
│   │   ├── HistoryEntry.kt
│   │   └── Tab.kt
│   └── repository/
│       ├── BookmarkRepository.kt
│       └── HistoryRepository.kt
├── ui/
│   ├── activity/
│   │   ├── MainActivity.kt         # setContent{} — WebView + Compose overlay
│   │   └── SettingsActivity.kt     # Pure Compose — no ViewBinding
│   ├── screens/
│   │   ├── BookmarksScreen.kt      # BookmarksSheet + HistorySheet composables
│   │   └── TabsScreen.kt           # TabsSheet composable
│   └── viewmodel/
│       └── BrowserViewModel.kt     # AndroidViewModel, StateFlow
└── utils/
    └── UrlUtils.kt                 # URL parsing / smart search
```

**Key patterns:**
- `AndroidViewModel` + `StateFlow` / `collectAsStateWithLifecycle` for reactive UI
- `Room` (KSP) for bookmarks and history persistence
- `BrowserDatabase.getInstance(context)` — manual singleton, no DI framework
- Kotlin Coroutines for async DB operations
- Jetpack Compose for all UI; no XML layouts remain

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM |
| DI | None (manual singleton) |
| Database | Room (KSP) |
| Async | Kotlin Coroutines + StateFlow |
| Build | Gradle Kotlin DSL (`*.gradle.kts`) |
| Min SDK | API 24 (Android 7.0) |
| Compile SDK | 35 |
| Java toolchain | Java 17 |

---

## Design System

Custom warm-minimal palette:

- **Light:** surface `#FFFFFF`, on-surface `#242424`, accent orange `#f54e00`, outline `#E0E0E0`
- **Dark:** surface `#1A1A1A`, on-surface `#FFFFFF`, outline `#333333`
- Dark mode via `values-night/colors.xml` + `AppCompatDelegate`

Relevant resources:
- `res/drawable/` — Vector icons only
- `res/values/` — `colors.xml`, `strings.xml`, `themes.xml`
- `res/values-night/` — Dark mode overrides
- `res/xml/network_security_config.xml`

No `res/layout/` files exist — all UI is Compose.

---

## Features

- Multi-tab browsing — tab state in `BrowserViewModel`, WebViews in a `FrameLayout` behind Compose
- Bookmarks — Room via `BookmarkDao` / `BookmarkRepository`
- Browsing history — Room via `HistoryDao` / `HistoryRepository`
- Smart URL bar — raw URLs pass through, fallback to Google search (`UrlUtils`)
- Desktop site toggle
- Share & copy URL
- **Copy Cookie button** — copies current page cookies to clipboard, haptic + Snackbar
- Clear browsing data on demand and optionally on exit (cleared at next launch)
- Dark mode (manual toggle in Settings, persisted to prefs)
- Ad blocking (domain-based, `shouldInterceptRequest`)
- User scripts — inject custom JS per page, supports URL / file / text input, per-script enable/disable

---

## Key Implementation Notes

### WebView layout strategy
`MainActivity` adds a `FrameLayout` as the content view first, then calls `setContent{}` on top. `AndroidView(factory = { webViewContainer })` acts as a transparent bridge to keep the Compose tree valid while actual WebViews render in the frame below.

### Clear data on exit
`onCreate` checks `PREF_CLEAR_ON_EXIT` before the UI loads and calls `performClearDataSync()`. This guarantees a clean slate on the next session regardless of how the process was killed.

### User scripts
- Injection in `injectScripts(view)`, called from `onPageFinished`
- Idempotency guard: `window.__toolBrowserScriptsInjected = true`
- Backticks / backslashes / `${` are escaped before embedding
- SPA bridge via `injectSpaNavigationBridge(view)` — patches `pushState` / `replaceState`

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

Output: `app/build/outputs/apk/debug/app-debug.apk`

---

## CI

GitHub Actions (`.github/workflows/build-apk.yml`) builds Debug APK on push to `main`/`master`.
Uses KSP — no `kapt` flags. Configuration cache and build cache enabled.

For signed releases, add secrets: `KEYSTORE_BASE64`, `KEY_ALIAS`, `KEY_PASSWORD`, `STORE_PASSWORD`.

---

## Do NOT Touch

These files must not be modified during any Compose migration work:

- `UrlUtils.kt`
- `data/database/BookmarkDao.kt`, `HistoryDao.kt`
- `data/model/Bookmark.kt`, `HistoryEntry.kt`, `Tab.kt`
- `data/repository/BookmarkRepository.kt`, `HistoryRepository.kt`
- WebView client / chrome client logic inside `MainActivity.kt`
