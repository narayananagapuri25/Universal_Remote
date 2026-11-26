# Building OneRemote

## Prerequisites

- JDK 21
- Android SDK Platform API 37.2
- Android SDK Platform API 36 (target SDK)
- Android SDK Build Tools 36.0.0
- Gradle 9.9.1
- Network access for first-time dependency resolution only

## Build and verify

From the repository root:

```bash
gradle --version
gradle clean testDebugUnitTest lintDebug assembleDebug
```

The debug APK is generated at:

`app/build/outputs/apk/debug/app-debug.apk`

## Android Studio

Open the repository as a Gradle project. Until the Gradle Wrapper JAR is added, configure Android Studio to use a local Gradle 9.9.1 installation rather than a wrapper distribution.

## Current implementation limits

This is still an early development build, not a production remote-control release. It checks built-in IR capability, includes a guarded ConsumerIrManager transmission engine and independently authored NEC, extended NEC, Samsung32 and Sony SIRC12/15/20 encoders. A model-specific profile data structure and bounded Flipper-compatible `.ir` import/export codec exist, including supported parsed protocol commands and raw waveform buttons. The app also exposes an experimental Daikin AC full-state editor for the documented ARC466A9-style and ARC480A11-style transport families. The user must choose and confirm the matching remote family; the editor verifies reported 38 kHz support, sends only after explicit tap, and does not retry. This protocol-specific UI is not a universal Daikin controller, has not been physically tested by OneRemote, and does not yet create a durable saved AC device profile. No general brand/model command catalogue exists yet. LAN/Bluetooth discovery and pairing, general IR receiver learning, and complete AC model onboarding remain unfinished.

The package ID `dev.localremote.app`, working brand name, icon and display label are provisional. Do not publish builds under this ID until branding/package clearance is complete.
