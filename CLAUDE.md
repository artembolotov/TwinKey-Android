# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

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
JVM target: Java 11. Min SDK: 26. Target/Compile SDK: 36/37.

## Architecture

Android port of an iOS TOTP authenticator. Layers are intentionally mirrored after the iOS app — screen names, service names, and storage keys match their Swift counterparts.

### Data flow

```
domain/ (pure Kotlin, no Android deps)
    ↓
data/   (Repository, KeychainService, BackupManager)
    ↓
ui/     (ViewModel → StateFlow → Composable screens)
```

### domain/
Pure Kotlin business logic with no Android dependencies (designed for JVM testability).
- `Token` + `OtpGenerator` — core models
- `TotpCodeGenerator` — RFC 4226/6238 HMAC-based OTP; algorithm is SHA1/256/512, truncation follows the RFC dynamic truncation spec
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

**Overlay system** (`AccountsScreen`): a sealed class `AccountsOverlay` drives which bottom sheet is visible (Scanner, Manual, Added, Editing, Settings, ImportFromEmpty). Composable renders sheets conditionally based on overlay state.

**Navigation**: no NavController. `WelcomeScreen` acts as a root router via `AppMode` enum (Unknown → splash, Welcome → TutorialScreen, Accounts → AccountsScreen). All state survives config changes via ViewModel.

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
