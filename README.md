# TwinKey — 2FA Authenticator for Android

Android companion to the [TwinKey iOS app](https://apps.apple.com/app/twinkey/id6748137875). Generates TOTP and HOTP one-time passwords, stores accounts in encrypted local storage, and supports backup/restore.

## Features

- TOTP (time-based) and HOTP (counter-based) code generation — RFC 4226/6238
- QR code scanning and manual account entry
- Support for SHA-1, SHA-256, and SHA-512 algorithms
- Import from Google Authenticator migration QR codes
- Encrypted local storage (AES-256-GCM via Android Keystore)
- Backup and restore via `.twinkey` files
- Drag-to-reorder accounts
- Dark mode support

## Requirements

- Android 8.0 (API 26) or later
- Android Studio Meerkat or later (for development)

## Setup

Copy `local.properties.example` to `local.properties` and fill in your values:

```
sdk.dir=/path/to/Android/sdk
appmetricaApiKey=your_key_here
```

`local.properties` is gitignored and never committed.

## Build

```bash
# Debug APK
./gradlew assembleDebug

# Release bundle + APK
./gradlew bundleRelease assembleRelease -PversionCode=<number>

# Unit tests
./gradlew test
```

Release signing requires `keystore.properties` at the repo root. If absent, the release build runs unsigned.

## License

Copyright (c) 2025 Artem Bolotov. All rights reserved.

This repository is public to enable GitHub Actions CI/CD builds. You may view and study the source code. See [LICENSE](LICENSE) for full terms.
