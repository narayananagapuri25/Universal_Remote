# OneRemote — Engineering Baseline

**Working product name:** OneRemote (provisional; branding clearance required)  
**Baseline:** v1.0  
**Date:** 2026-11-26  
**Status:** Requirements and engineering baseline accepted; native Android implementation in progress  
**Repository:** `narayananagapuri25/Universal_Remote`

This package converts the gathered product requirements into an implementation-ready starting point. It contains:

- [`docs/PRODUCT_SRS.md`](docs/PRODUCT_SRS.md) — software requirements specification
- [`docs/TECHNICAL_ARCHITECTURE.md`](docs/TECHNICAL_ARCHITECTURE.md) — proposed native Android architecture and protocol boundaries
- [`docs/TEST_PLAN.md`](docs/TEST_PLAN.md) — verification strategy and release gates
- [`docs/REQUIREMENTS_TRACEABILITY.md`](docs/REQUIREMENTS_TRACEABILITY.md) — mapping from gathered decisions R1–R131 to specification sections and tests
- [`docs/DECISIONS_AND_RISKS.md`](docs/DECISIONS_AND_RISKS.md) — decision log, feasibility constraints and risk register

## Repository bootstrap and governance

The owner explicitly approved a **one-time README-only bootstrap commit on `main`** on 2026-11-26 because the repository had no commits or branch refs from which GitHub could create a feature branch. This exception establishes the initial commit only; it does not change the normal workflow. All subsequent engineering documentation and application changes must be made on feature branches, submitted through pull requests, pass mandatory checks, and receive the owner's explicit approval before merge. No application source code is included in the bootstrap commit.

The engineering documents listed above are being submitted separately on the `docs/engineering-baseline-v1` feature branch for review. They are not merged into `main` by the bootstrap action.

## Product summary

OneRemote is a local-first, native Kotlin Android remote-control application. The first release targets televisions, air conditioners and set-top boxes. It supports only device/model/protocol combinations for which a real control method is available and verified. The architecture is designed to expand to projectors, audio systems, fans, streaming devices, disc/media players and supported smart-home devices.

The core remote functionality must operate offline. New devices require user approval; trusted devices are rediscovered/reconnected on launch and foreground resume. Saved profiles remain until the user removes them. There is no account, household setup, mandatory cloud backend or continuous background scanning.

## Android application foundation

The `app/` module is the first native Android implementation foundation:
- Kotlin with Android Gradle Plugin 9.5.3 and Gradle 9.9.1 (pinned)
- Minimum Android API 26; compile SDK API 37.2; target SDK API 36
- Compose Material 3 UI shell with Devices, Discover and Settings
- Room-backed local saved-device persistence
- First-release category and command-safety domain types
- Independently authored NEC, extended NEC, Samsung32 and Sony SIRC12/15/20 protocol encoders with golden-vector tests
- Model-specific IR profile/button model with per-command compatibility evidence, raw waveform or typed protocol commands, and validator
- One-shot dispatcher for protocol or validated raw waveform commands connected to the guarded transmitter
- Bounded Flipper-compatible `.ir` import/export for supported parsed protocols and raw waveform buttons, with imported profiles kept unverified
- Built-in IR capability inspection, carrier-range validation and guarded transmit adapter with unit tests
- Smart TV/set-top-box volume-routing policy with unit tests
- GitHub Actions verification: unit tests, Android Lint and debug APK assembly

The app now has a model-specific IR profile data model, protocol encoders and a Flipper-compatible `.ir` import/export codec. An experimental Daikin AC state editor is exposed from Discover for the documented ARC466A9-style three-frame and ARC480A11-style one-frame protocol families. It requires the user to select and attest to the matching family, checks the phone's reported 38 kHz capability, and sends one complete state frame without retries. This is not a universal Daikin profile, has not been physically tested by OneRemote, and is not yet stored as a reusable AC device profile. There is still no bundled brand/model IR code catalogue. LAN/Bluetooth discovery and pairing and physical IR receiver learning remain unimplemented. The Discover screen also reports built-in IR capability without transmitting during setup. The first build uses a provisional application ID (`dev.localremote.app`) pending product-name clearance; it must not be published under this ID before identity review.

See [IR protocol implementation and data policy](docs/PROTOCOL_IMPLEMENTATION.md) for supported encoder families, code-data licensing rules and compatibility evidence requirements.

## Local build

Install JDK 21, Android SDK Platform API 37.2, Android SDK Platform API 36, Build Tools 36.0.0 and Gradle 9.9.1. Then run:

```bash
gradle clean testDebugUnitTest lintDebug assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. This branch currently uses the Gradle installation directly (no Gradle Wrapper JAR has been committed yet); CI installs the pinned Gradle distribution explicitly.

## Approval and implementation policy

1. Use the merged SRS, architecture, test plan and risk register as the implementation baseline.
2. Resolve branding, legal/licensing, monetisation and hardware-test gates before their dependent release.
3. Implement only on feature branches and submit pull requests.
4. Require mandatory CI checks and the owner's explicit approval before every merge to `main`.
5. Do not publish a production APK until all release gates in `docs/TEST_PLAN.md` pass.

## Source references

The technical decisions are based primarily on current official Android and Google Play documentation. A curated source list appears at the end of each relevant document. Links were checked on 2026-11-26; target API and platform requirements must be rechecked at release time.
