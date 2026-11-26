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

This is still an early development build, not a remote-control release. It checks built-in IR capability, includes a guarded ConsumerIrManager transmission engine and independently authored NEC, extended NEC, Samsung32 and Sony SIRC12/15/20 encoders. A model-specific profile data structure exists, but no model-specific command catalogue or user-facing appliance command has been wired to it yet. LAN/Bluetooth discovery and pairing remain unimplemented. No permissions are requested until those features are implemented.

The package ID `dev.localremote.app`, working brand name, icon and display label are provisional. Do not publish builds under this ID until branding/package clearance is complete.
