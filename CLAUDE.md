# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Git workflow

`git push origin main` fails with 403 in Claude Code sessions — the local proxy only allows pushing to `claude/*` branches. To push to `main`, use the GitHub MCP tool:

```
mcp__github__push_files(owner="artembolotov", repo="twinkey-android", branch="main", message="...", files=[...])
```

After pushing via MCP, sync locally: `git pull origin main`.

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
JVM target: Java 11. Kotlin 2.3.20, AGP 9.2.1. Min SDK: 26. Target/Compile SDK: 36/37.

Release signing reads from `keystore.properties` at the repo root; if absent, the release build runs unsigned (no signingConfig). `isMinifyEnabled = false` in release.

## Architecture

Android port of an iOS TOTP authenticator. Layers are intentionally mirrored after the iOS app — screen names, service names, and storage keys match their Swift counterparts.

`TwinKeyApplication` is the `Application` subclass; it initializes AppMetrica analytics on startup (API key hard-coded, activity auto-tracking enabled).

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
- `TokenUrlParser` — parses/serializes `otpauth://` URIs using `java.net.URI` (not `android.net.Uri`)
- `GoogleAuthMigrationParser` — decodes Google Authenticator migration QR payloads

### data/
- `KeychainService` — EncryptedSharedPreferences backed by Android Keystore (AES256-GCM). Storage keys match iOS: `"accounts"` (map UUID→CodableToken) and `"order"` (array of UUIDs for display order). JSON via kotlinx.serialization.
- `AccountRepository` — CRUD + ordering over the keychain
- `BackupManager` — exports/imports `.twinkey` files (JSON)

### ui/
State management pattern used throughout:
- ViewModel holds `_state: MutableStateFlow<UiState>`, exposes `state: StateFlow<UiState>`
- Composables observe via `state.collectAsState()` and call ViewModel methods — no direct state mutations in UI
- `ModalBottomSheetState` animation state lives in the Composable (UI-only concern, not in ViewModel)
- `ui/components/` provides shared building blocks: `AppModalBottomSheet` + `rememberAppSheetState` (wrappers used by every overlay), `OtpCodeView`, `CheckableTokenRow`

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
| Compose BOM 2026.03.01 + Material3 | UI |
| CameraX + ML Kit barcode | QR scanning |
| EncryptedSharedPreferences | Secure storage |
| Apache Commons Codec | Base32 decoding |
| Calvin Reorderable | Drag-to-reorder list |
| kotlinx.serialization | JSON backup format |
| androidx.core.splashscreen | System splash screen |
| AppMetrica analytics | Usage analytics (initialized in `TwinKeyApplication`) |
