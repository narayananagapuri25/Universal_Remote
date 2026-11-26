# OneRemote — Verification and Test Plan

| Field | Value |
|---|---|
| Document ID | UR-TEST-001 |
| Version | 1.0 |
| Date | 2026-11-26 |
| Status | Mandatory release test strategy |

## 1. Test policy

No first-release APK may be labelled production-ready unless all mandatory automated tests pass and device/protocol support labels are accurate. A passing build or emulator test does not prove physical IR, Bluetooth or vendor-protocol compatibility. Tests must distinguish pure logic, protocol-vector verification, emulator/platform behaviour and actual physical-device testing.

No blocker/high-severity safety, security, privacy, data-loss or command-routing defect may be waived silently. Any failure blocks the release candidate until fixed and rerun. A release exception requires written owner approval and may not waive a safety/security control.

## 2. Test layers

### 2.1 Static and build checks

- Gradle wrapper integrity and dependency resolution.
- Kotlin compilation for all configured variants.
- Android Lint, Kotlin formatting/style and dependency vulnerability review.
- Manifest permission audit by target API.
- Dependency licence/SBOM inventory and forbidden licence check.
- APK/AAB signature and package metadata validation.
- Catalogue schema, signature, hash and size checks.

### 2.2 Pure Kotlin unit tests

The first implementation branch now has deterministic unit coverage for built-in IR capability checks, waveform validation, protocol encoders (NEC, NECext, Samsung32, Sony SIRC) and profile metadata validation. These tests prove encoding/validation logic only; they do not prove a specific appliance will respond.

- Device identity normalization, confidence scoring and duplicate prevention.
- Capability selection and connection-route recommendation.
- Command idempotency and PowerOn/PowerOff/PowerToggle classification.
- AC state transitions, full-state packet generation, checksum and boundary values.
- TV/STB key mapping, channel list parsing and source selection.
- IR code parsing, carrier/timing bounds, repeat frames and truncation rejection.
- Golden-vector tests for NEC, extended NEC, Samsung32 and Sony SIRC12/15/20 timing, payload bit order, field boundaries and invalid values.
- Daikin AC saved-profile mapping round trips for both transport variants, all exposed state flags, invalid enum values, invalid temperatures, and bounded display names.
- Semantic TV/STB layout validation and persistence mapping: supported category roles, sparse user assignments, duplicate role rejection, unknown command-button rejection and unknown stored-role rejection.
- Device-profile validation for unique button IDs, evidence references, physical test-record requirements and raw waveform validity.
- Flipper `.ir` importer/exporter: header/version validation, bounded file/button/line sizes, duplicate and unknown field rejection, four-byte little-endian values, protocol field bounds, raw timing count <= 1,024, duty-cycle metadata, unsupported parsed protocols, and import/export round-trip fixtures.
- Protocol/raw dispatch tests proving one encode/one transmit and no retry; invalid raw patterns must be rejected before hardware access.
- Built-in IR capability detection, reported carrier-range enforcement, malformed waveform rejection, strict under-two-second duration, serialised transmitter access and no false appliance-state confirmation.
- IR learning capture parsing, noise/incomplete-signal rejection, raw waveform bounds, replay mapping and receiver-not-present flow.
- Command queue sequencing, parallelism across devices, cancellation and duplicate suppression.
- Scene failure continuation, stop-on-failure override and recovery scene presentation.
- Schedule recurrence, time-zone/DST changes, grace periods, missed-run classification and no-backlog rule.
- Backup archive validation, duplicate detection, merge/replace and credential exclusion/encryption.
- Catalogue signature, anti-rollback, version compatibility and provenance/licence validation.

Use protocol golden vectors with source/provenance. Where practical, include both known-good and malformed/hostile inputs. Avoid relying solely on mocks for complex protocol encoders; use deterministic fixtures and fuzz/property tests for parsers and importers.

### 2.3 Data and integration tests

- Room DAO/repository persistence and observed Flow updates.
- All database migrations from previous shipped schema versions, including Room 2-to-3 Daikin profile persistence and 3-to-4 semantic TV/STB mapping table creation, preserving previous IR/device records.
- App process death/restart and local DB corruption handling.
- Profile/custom overlay preservation after catalogue update.
- Backup/restore across app versions and simulated new-device restore.
- Keystore key generation, field encryption/decryption, authentication invalidation and recovery.
- Catalogue download interruption, corrupted signature, expired/older manifest, bad schema and rollback.
- Network adapters against local test fixtures/emulated responders.
- Device capability discovery separate from control command validation.

### 2.4 Android instrumentation and UI tests

- First launch without login; contextual permission prompts only when feature activated.
- Devices / Discover / Settings navigation.
- Dashboard loads from local database before discovery completes.
- Add-device guided flow, pairing approval, manual configuration and custom profile.
- TV, AC and STB remote layouts, long-press/repeat, keyboard/touchpad fallback.
- Accessibility semantics, TalkBack labels, focus order, font scaling, large text and touch targets.
- Widgets and Quick Settings command dispatch and stale-state indicators.
- Optional biometric lock, device credential fallback and denial/recovery.
- Opt-in notifications for schedule reminders/results; no promotional or continuous-disconnection notifications.
- Guided troubleshooting maps protocol/transport errors to safe steps and never retries state-changing commands automatically.
- Lock-screen restrictions and authentication for sensitive actions.
- Haptics/sound options.

### 2.5 Hardware and protocol tests

A model is marked Physically tested only after the actual hardware test is recorded. Each report records device brand/model/revision, original remote/protocol source, Android phone model/OS, built-in/external transmitter model, carrier frequency, distance/orientation, tested commands and result.

**IR transmitter:** verify carrier frequency acceptance, timing boundaries, signal strength/range, repeat behaviour, heat/battery, transmitter concurrency lock, cancellation semantics and all required commands. Built-in IR support must be checked per phone model.

**IR learning:** verify supported receiver hardware, decode/replay, raw timing capture, noise/partial capture handling, protocol recognition, learned-command storage and clear “not learnable” result for unsupported signals.

**USB-C accessory:** verify enumeration/USB host APIs, power negotiation, permission prompt, disconnect/reconnect, firmware/device identity, transmission and unsupported accessory error.

**Bluetooth accessory/device:** verify BLE and Classic scanning/association, OS permissions, pairing approval, reconnect, lost connection, GATT/profile control, protocol command response, battery impact and unpair/removal. Pairing alone is not accepted as control verification.

**LAN devices:** verify mDNS, SSDP, documented API, authentication, TLS/certificate failure, network changes, DHCP address change, multiple devices, router isolation/client isolation and permission denial/revocation. Ensure no traffic leaves the local subnet for a control command.

**Android TV Remote v2 / Realme TechLife:** verify `_androidtvremote2._tcp.` discovery and manual IP fallback; pairing request/options/configuration acknowledgements; valid and invalid six-character pairing codes (including retry without losing the active challenge after a local check-byte mismatch); RSA pairing-secret known-answer vector; Android Keystore client certificate persistence; TV-certificate pinning and changed-certificate rejection; remote feature negotiation; no key sends before the remote service activates; key injection and ping replies; IME capability negotiation, focused-field counters and nested text batch-edit messages; APP_LINK feature bit 512 negotiation and disabled shortcuts when absent; YouTube/Netflix/Prime Video field-90 app-link payload encoding; disconnect clearing capability state; stale IME state cleared on reconnect; disconnect status; reconnection after Wi-Fi changes; explicit forget/unpair; and service-unavailable guidance. Physical acceptance requires the owner's exact Realme TV model/revision: pair on-screen, then verify Home, Back, D-pad/OK, volume, mute, input, power, text entry in a TV search field and each supported app shortcut. The app-launch request is only a request; confirm the intended app opens on real hardware. CI/protocol tests do not count as physical compatibility.

**AC:** test each supported mode, setpoint min/max/step, full-state frames, fan/swing/timer bits, model variants and command sequencing against real AC hardware. Avoid claiming confirmation if no authoritative feedback exists.

**TV/STB:** test power discrete/toggle semantics, volume routing, source, number keys, channel, menu navigation, media, app launch, keyboard/touchpad and standby wake independently per model.

## 3. OS compatibility matrix

At minimum, CI/emulator or test devices must cover the minimum API and the major permission/scheduling transition versions. Where exact emulator images are unavailable, document the gap and test on the nearest supported physical device.

| OS/API band | Key behaviours to verify |
|---|---|
| API 26 (Android 8.0) | Minimum install/start, IR APIs, legacy Bluetooth/location permission behaviour, database |
| API 28–30 | Legacy BLE scan/location prompts, background limits, boot receiver behaviour |
| API 31–32 | BLUETOOTH_SCAN/CONNECT, nearby-device prompt, exact alarm permission rules |
| API 33 | Notification runtime permission, target SDK changes, exact alarm default denial cases |
| API 34–35 | Background and alarm restrictions, biometric and permissions |
| API 36 (Android 16) | Current Google Play target floor as of 2026-11-26; test current permission/foreground/notification behaviour |
| API 37.2 (Android 17) | Compile-SDK compatibility; `ACCESS_LOCAL_NETWORK` behaviour when targeting API 37+, denied/revoked flows for mDNS/SSDP/raw TCP/UDP and LAN controls |

Also test at least two OEM Android builds with aggressive battery/background restrictions once the reference phone and test hardware are known.

## 4. Mandatory safety test cases

| ID | Scenario | Expected result |
|---|---|---|
| SAFE-001 | PowerOn command where discrete command exists | Send explicit On; never send toggle |
| SAFE-002 | PowerOff command where discrete command exists | Send explicit Off; never send toggle |
| SAFE-003 | Toggle protocol with unknown state | Send only one deliberate toggle; never auto-repeat after uncertain result |
| SAFE-004 | IR send returns | UI reports signal sent/estimated/unknown, not confirmed power state |
| SAFE-005 | Device fails during a multi-device scene | Other independent devices continue; per-device failures reported |
| SAFE-006 | Scene set to stop-on-failure | Pending steps stop at the first failure; already sent commands are not reversed |
| SAFE-007 | Scene failure with recovery scene | No recovery without user approval; confirmation for disruptive actions |
| SAFE-008 | Duplicate/repeated tap on Power | Safety window prevents accidental duplicate dispatch; deliberate separate tap remains possible |
| SAFE-009 | Command queue cancel | Pending steps cancelled; already transmitted command is not represented as undone |
| SAFE-010 | Device becomes unavailable | No automatic state-changing retry; saved device remains |
| SAFE-011 | Profile mapping changes under schedule | Affected automation paused and user notified before execution |
| SAFE-012 | Imported automation/profile | Remains disabled/untrusted until validated and user-approved |
| SAFE-013 | AC full-state protocol | State packet reflects complete current desired state; no invalid partial frame |
| SAFE-014 | Set-top box volume route | Exactly selected TV or STB route receives command, not both by default |

## 5. Mandatory privacy/security tests

- No network request for device names, command history, custom profile contents or credentials.
- No analytics SDK/events, advertising profile, behavioural tracking or automatic diagnostics upload.
- Permission denial must not disable unrelated offline or IR functions.
- LAN command traffic stays within approved local endpoint; no remote relay.
- Unknown device does not auto-pair or become trusted.
- Authentication/credential material is encrypted at rest and absent from logs/exports.
- Invalid/malformed catalogue or imported profile never causes arbitrary code execution, unbounded memory use or unsafe command execution.
- Release/catalogue signatures are verified; corrupted/unsigned/rolled-back catalogue is rejected.
- Optional biometric failure or unenrolled state follows recovery flow and does not delete data.
- Ad SDK privacy inventory, Play Data Safety declaration and consent flow are reviewed before ads enable. If review fails, ad integration remains disabled.

## 6. Offline and recovery test cases

- Fresh install, setup of an IR profile and command testing with airplane mode enabled.
- App restart with no internet: dashboard, saved remotes, favourites, layouts, commands, scenes and local network control work.
- Optional online functions show unavailable gracefully without blocking remotes.
- Trusted device unavailable: remains saved, status stale/unavailable, no duplicate after rediscovery.
- Wi-Fi SSID/router/password change: no deletion; reconnect/credential recovery flow.
- Phone reboot: schedules are restored according to policy; missed commands are skipped or grace-handled once, never burst-replayed.
- Phone switched off at schedule time: no false execution; missed status shown after boot.
- Catalogue update fails midway: previous version remains active.
- APK update: data migration preserves profiles and user configuration.

## 7. Performance and resource tests

- Macrobenchmark dashboard startup to first useful content on a defined reference device. Target <2 seconds on that capable baseline; record median and p95.
- Measure app-side command dispatch latency for a local fake/test transport; target <300 ms where possible. Exclude appliance response time and physical IR travel/processing from the app-dispatch metric.
- Verify discovery does not block dashboard and does not scan continuously in background.
- Measure battery impact for foreground discovery and scheduled local tasks; no permanent service for reconnection.
- Exercise large catalogue and many saved device profiles to measure memory and list rendering.
- Test Compose recomposition and custom remote layouts for responsiveness.

## 8. Accessibility and usability checks

- All controls have meaningful TalkBack labels and state descriptions.
- All actions are operable without gesture-only navigation.
- Touch target sizes and spacing remain usable on small screens.
- Large font and display-size settings do not hide critical controls.
- Connected/unavailable/unknown/failure statuses are not distinguished by colour alone.
- Haptics, sounds and animations respect user/system settings.
- Rotation, resizing, split-screen and tablet layouts preserve current remote state.

## 9. CI gates and release checklist

Required pull-request gates:

1. Formatting, lint and static analysis.
2. Debug/release compilation.
3. Unit and protocol golden-vector tests.
4. Database/schema/import validation tests.
5. Command safety/queue/scene/schedule tests.
6. Instrumentation/UI tests on configured API matrix.
7. Permission and manifest policy check.
8. Licence/SBOM and privacy dependency audit.
9. APK/AAB signature and catalogue verification tests.

Release gates:

- All mandatory CI checks green.
- No unresolved blocker/high-severity safety/security/privacy/data integrity defects.
- Current API target and Play policy requirements rechecked.
- Physical test evidence present for every profile labelled Physically tested.
- Unverified/community profiles clearly labelled and not counted as confirmed support.
- User-owned signing key and offline backup verified.
- Privacy policy, Play Data Safety, ad consent/disclosures and billing flows reviewed; ads/billing disabled if not ready.
- Backup, update, rollback and migration tested on a copy of representative user data.
- Owner reviews and approves PR before merge; owner explicitly approves release before publishing.

## 10. Current test blockers / TBD

- User's primary Android phone model and IR emitter capability are not yet recorded.
- The exact Realme TechLife TV model/revision and its Android TV Remote Service availability are still pending physical confirmation. AC, set-top box, Bluetooth and external IR accessory models also remain to be recorded.
- The first physical compatibility lab matrix must be created when hardware is identified.
- API 37.2 compile platform is required by the pinned stable Compose dependency set. API 37/37.2 runtime test devices or emulator images must be confirmed during implementation; targetSdk36 and targetSdk37 behaviour must be tested separately.
- No model can receive the “Physically tested” label until evidence exists.