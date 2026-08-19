# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Git workflow

`git push origin main` works normally in Claude Code sessions for this repository. Use standard git push:

```bash
git push origin main
# or from a feature branch:
git push origin HEAD:main
```

## Build & Run

```bash
# Debug APK
./gradlew assembleDebug

# Release bundle + APK (required for Play Store)
./gradlew bundleRelease assembleRelease -PversionCode=<number>

# Unit tests
./gradlew test

# Instrumented tests (requires connected device/emulator)
./gradlew androidTest
```

Lint rules (app/build.gradle.kts): `OldTargetApi` and `GradleDependency` are disabled.  
JVM target: Java 11. Kotlin 2.4.10, AGP 9.3.1 (Gradle 9.5.0). Min SDK: 26. Target/Compile SDK: 36/37.

Release signing reads from `keystore.properties` at the repo root; if absent, the release build runs unsigned (no signingConfig). Release builds run R8: `isMinifyEnabled = true` and `isShrinkResources = true`, with `proguard-android-optimize.txt` plus `proguard-rules.pro`. The deobfuscation mapping lands in `app/build/outputs/mapping/release/mapping.txt`.

## Architecture

Android port of an iOS TOTP authenticator. Layers are intentionally mirrored after the iOS app — screen names, service names, and storage keys match their Swift counterparts.

`TwinKeyApplication` is the `Application` subclass; it initializes AppMetrica analytics on startup (API key read from `local.properties` / Gradle property `appmetricaApiKey`, activity auto-tracking enabled).

### Data flow

```
domain/ (pure Kotlin, no Android deps)
    ↓
data/   (Repository, KeychainService, BackupManager)
    ↓
ui/     (ViewModel → StateFlow → Composable screens)

core/   AppState + AppMode enum (shared root state shape)
```

### domain/
Pure Kotlin business logic with no Android dependencies (designed for JVM testability).
- `Token` — core model; `CodableToken` — serialization shape stored in the keychain
- `OtpGenerator` (interface) + `TotpCodeGenerator` — RFC 4226/6238 HMAC-based OTP; `OtpAlgorithm` enum (SHA1/256/512), `OtpFactor` enum (TOTP/HOTP). Truncation follows the RFC dynamic truncation spec.
- `TokenUrlParser` — parses/serializes `otpauth://` URIs. No `android.net.Uri`, no `java.net.URI`, no `URLEncoder`: the raw URL string is split by hand (the reader needs the label *before* percent-decoding) and percent-encoding is RFC 3986 (`URLEncoder` writes a space as `+`). Writing is strictly canonical, reading is permissive — see `docs/BACKUP_FORMAT.md`
- `GoogleAuthMigrationParser` — decodes Google Authenticator migration QR payloads

### data/
- `KeychainService` — EncryptedSharedPreferences backed by Android Keystore (AES256-GCM). Storage keys match iOS: `"accounts"` (map UUID→CodableToken) and `"order"` (array of UUIDs for display order). JSON via kotlinx.serialization.
- `AccountRepository` — CRUD + ordering over the keychain
- `BackupManager` — exports/imports `.twinkey` files. The byte-level format is shared with the iOS app and specified in `docs/BACKUP_FORMAT.md`: both apps must emit identical bytes for identical accounts, and both must keep reading the pre-spec files each of them used to write

### ui/
State management pattern used throughout:
- ViewModel holds `_state: MutableStateFlow<UiState>`, exposes `state: StateFlow<UiState>`
- Composables observe via `state.collectAsState()` and call ViewModel methods — no direct state mutations in UI
- `ModalBottomSheetState` animation state lives in the Composable (UI-only concern, not in ViewModel)
- `ui/components/` provides shared building blocks: `AppModalBottomSheet` + `rememberAppSheetState` (wrappers used by every overlay), `OtpCodeView`, `CheckableTokenRow`, `GlassScaffold` (frosted-glass scaffold via Haze), `ReadOnlyField`, `TextInputScreen`

**Overlay system** (`AccountsScreen`): a sealed class `AccountsOverlay` drives which bottom sheet is visible (`None`, `Scanner`, `Manual`, `Added(token)`, `Editing(token)`, `Settings`, `ImportFromEmpty`). `AccountsSheets` renders sheets conditionally based on overlay state.

**Navigation**: no NavController. `WelcomeScreen` acts as a root router via `AppMode` enum in `core/` (`Unknown` → splash, `Welcome` → `TutorialScreen`, `Accounts` → `AccountsScreen`). All state survives config changes via ViewModel. System splash uses `androidx.core.splashscreen`.

**Keyboard / focus pattern** (`AccountsScreen`):
- `rememberUpdatedState(WindowInsets.ime.getBottom(density))` captures IME height in composable context
- `snapshotFlow { imeBottomPx }.drop(1)` reacts to changes only (skips initial value — safe for Bluetooth keyboards)
- `pointerInput` with `PointerEventPass.Initial` on the accounts list clears focus on any touch without consuming events

### ui/welcome/
`TutorialViewModel` drives a chat-style onboarding sequence with message timing and animation state. After the tutorial completes, `AppMode` transitions to `Accounts`.

## Key dependencies

| Library | Purpose |
|---|---|
| Compose BOM 2026.08.00 + Material3 | UI |
| CameraX + ML Kit barcode | QR scanning |
| EncryptedSharedPreferences | Secure storage |
| Apache Commons Codec | Base32 decoding |
| Calvin Reorderable | Drag-to-reorder list |
| kotlinx.serialization | JSON backup format |
| androidx.core.splashscreen | System splash screen |
| AppMetrica analytics | Usage analytics (initialized in `TwinKeyApplication`) |
| Haze | Frosted-glass blur effect (`GlassScaffold`) |
