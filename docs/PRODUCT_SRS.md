# OneRemote — Software Requirements Specification

| Field | Value |
|---|---|
| Document ID | UR-SRS-001 |
| Version | 1.0 |
| Date | 2026-11-26 |
| Status | Consolidated baseline; product-owner review pending |
| Product | OneRemote (working name only) |
| Platform | Native Android phone/tablet app |
| Minimum supported OS | Android 8.0 / API 26 |
| Initial market | India; architecture must permit global expansion |

## 1. Purpose

This specification defines the product, functional, safety, security, privacy, performance, compatibility and release requirements for OneRemote. It is the reference for architecture, implementation, testing and release acceptance. It is intentionally honest about hardware and protocol limitations: “universal” describes the product ambition, not a guarantee that every brand/model can be controlled by every Android phone.

The first release must provide real, testable control functionality for three device categories: televisions, air conditioners and set-top boxes. A control is supported only when an applicable protocol and transport are implemented and verified. A polished screen with non-functional buttons is not acceptable.

## 2. Product vision and goals

OneRemote should replace as many physical remotes as is technically possible through a single, accessible Android app. The app should discover compatible local devices, help the user add non-discoverable IR devices, save each approved device, reconnect trusted network/Bluetooth devices when the app opens or resumes, and keep all saved remotes available even when devices are switched off or temporarily unreachable.

### 2.1 Goals

- Native Android app, written in Kotlin.
- No account, login, registration or household setup.
- Local-first operation; configured remotes must work offline.
- No mandatory cloud, remote relay, remote access or hosted backend.
- TV, AC and set-top box support in the first release.
- Multiple supported local transports: phone IR, compatible external IR, local-network IR, Wi-Fi/LAN, Bluetooth and documented manufacturer APIs.
- Extensive, versioned, legally usable device/protocol catalogue.
- Safe command handling, truthful state reporting and no blind retries.
- Fully customisable device-specific remotes, voice, keyboard/touchpad, favourites, scenes/macros, schedules, widgets and Quick Settings.
- Maximum practical local privacy and no analytics/behavioural profiling.
- Free core functionality, with optional privacy-compatible non-personalised ads and optional in-app ad removal only where technically and policy-compliantly supportable.
- Zero-budget development preference: no paid APIs, hosting, databases or SDKs without explicit approval. Required platform account charges must be disclosed.

### 2.2 Non-goals / explicit exclusions

- No user accounts, cloud sync, remote internet control, VPN relay or household/floor/room hierarchy.
- No always-listening microphone.
- No continuous background network scanning or automatic disconnection notifications.
- No root requirement.
- No promise of control merely because a device was discovered or Bluetooth-paired.
- No universal compatibility claim for every model in a listed brand.
- No proprietary IR database redistribution without an explicit licence.
- No automatic power-state retries, blind toggles or unsafe scene rollback.

## 3. Users and operating context

Primary users are consumers in India who want one local app for their TV, AC and set-top box remotes. Users may have older non-smart devices, smart TVs, different brands in one home, low-end Android phones, phones without built-in IR, or intermittent/no internet. The app must adapt to each phone's actual hardware and to each device's actual capabilities.

## 4. Product scope and catalogue

### 4.1 First-release categories and brand catalogue targets

**Televisions (15 priority brands):** Samsung, LG, Sony, Xiaomi, OnePlus, TCL, Hisense, Panasonic, Philips, Vu, Haier, Onida, Kodak, Thomson and Realme.

**Air conditioners (18 priority brands):** Daikin, Voltas, LG, Samsung, Hitachi, Blue Star, Panasonic, Mitsubishi Electric, Carrier, Lloyd, Godrej, Whirlpool, Haier, O General, Toshiba, IFB, Onida and Hisense.

**Set-top boxes / receivers:** Tata Play/Tata Sky, Airtel Digital TV, Dish TV, d2h, Sun Direct, DD Free Dish-compatible receivers and compatible independent satellite/cable receivers, including receiver models sold through providers such as Videocon, DEN and GTPL. The catalogue must distinguish operator/provider, receiver manufacturer, exact hardware model and IR protocol where known.

These lists define research and catalogue targets, not blanket support. A listed brand may contain several incompatible IR code sets, local APIs and hardware revisions.

### 4.2 Long-term expansion categories

Projectors; soundbars/home theatres/receivers/speakers; fans; streaming boxes and sticks; DVD/Blu-ray/media players; smart lights/plugs/appliances; and other devices with documented or legally learnable protocols.

## 5. Functional requirements

The identifiers below are stable acceptance identifiers for implementation and test traceability. The numbered user decisions (R1–R131) are consolidated into these groups; later decisions supersede earlier contradictory choices as noted.

### 5.1 First launch and navigation

- **FR-001:** App opens directly to the saved-device dashboard. No account/login screen, splash advertisement or mandatory tutorial.
- **FR-002:** On first launch, show a short explanation and request each permission only when the user activates the relevant feature.
- **FR-003:** Main navigation has three destinations: Devices, Discover and Settings. Scenes/macros are accessible within the app without a fourth primary destination.
- **FR-004:** Dashboard order is: favourites/quick actions; connected devices; saved devices that are unavailable or not currently connected; search; Add Device. There are no rooms, floors or household profiles.
- **FR-005:** Default theme follows Android system theme. User may select light/dark mode, accent colours and supported themes. Accessibility and readability take precedence over decoration.

### 5.2 Device identity, saving and reconnection

- **FR-010:** A newly discovered device must be approved by the user before becoming trusted or being saved.
- **FR-011:** Previously saved/trusted network and Bluetooth devices are automatically rediscovered/reconnected when the app launches or returns to the foreground. No continuous background scanning is permitted.
- **FR-012:** IR devices are saved as ready-to-use profiles; the app must not describe an IR-only appliance as persistently connected.
- **FR-013:** Saved devices, names, favourites, custom buttons/layouts, macros and connection preferences remain until explicitly removed by the user. Device unavailability, power-off or network changes must not delete a profile.
- **FR-014:** Matching a rediscovered device must use reliable stable identifiers where available. When confidence is low, ask before merging. Multiple identical models must remain separately addressable.
- **FR-015:** Dashboard loads local data immediately; reconnection occurs asynchronously and independently per device. One failing connection must not block other devices.
- **FR-016:** Device naming suggests brand/category defaults (for example, “Samsung TV 1”) and permits custom names. Names stay local and are not used for advertising.
- **FR-017:** Connection method selection recommends the best supported method, permits user override, and remembers preference per device. Switching methods must not unexpectedly change device state.
- **FR-018:** On network change, preserve all profiles, rediscover trusted devices on the current network, match safely and request new credentials/pairing only when necessary.

### 5.3 Discovery and pairing

- **FR-020:** Discover compatible local-network devices using supported mDNS/DNS-SD, SSDP/UPnP and documented manufacturer discovery mechanisms.
- **FR-021:** Support supported Android TV/Google TV and other platform/manufacturer protocols through separate adapters. Do not infer control support from discovery alone.
- **FR-022:** Discovery screen groups candidates by category and displays available name, manufacturer/model, transport, signal/reachability when exposed, saved/new state and a Connect/Test action.
- **FR-023:** Unknown/non-discoverable IR devices can be added by guided category → brand → model → candidate code set → test commands → save workflow.
- **FR-024:** Provide advanced manual configuration, including manual IP/port/protocol setup only for documented or safely configurable endpoints. Never perform indiscriminate port scanning or send arbitrary commands to unknown devices.
- **FR-025:** Bluetooth discovery must support applicable BLE and Bluetooth Classic capabilities. Pairing must be user-approved and use Android-supported pairing flows where possible. Pairing success alone is not proof of remote-control capability.
- **FR-026:** Use authenticated pairing where the target protocol supports it. Warn when a legacy protocol has no authentication/encryption. Never bypass manufacturer authentication or silently trust unknown devices.
- **FR-027:** A connection/discovery adapter reports the actual capabilities and security class of the endpoint. Unsupported methods are shown as unavailable, not simulated.

### 5.4 Connectivity and hardware capability

- **FR-030:** At runtime, detect built-in IR transmitter availability and supported carrier frequency ranges. Do not assume every phone has an IR emitter.
- **FR-031:** Support compatible external USB-C, Bluetooth and local-network IR transmitters through accessory-specific adapters. An accessory is supported only after its interface and signal transmission are verified.
- **FR-032:** Support Wi-Fi/LAN control through documented local APIs and approved protocol adapters; internet access must not be required for configured local control.
- **FR-033:** Support Bluetooth control only when a documented/implemented protocol or supported HID/GATT profile exposes usable control commands.
- **FR-034:** Support Wake-on-LAN, IR wake and documented manufacturer standby wake methods where the model, network and device settings permit. Clearly indicate unsupported standby wake.
- **FR-035:** Set-top box volume uses smart routing: recommend TV or receiver volume according to confirmed capability, allow manual selection and save the preference. Never send the same volume action to both devices unless the user explicitly configures that behaviour.
- **FR-036:** If multiple methods exist for a device, allow manual selection and configured fallback. No silent fallback that could cause a duplicate or state-changing action.
- **FR-037:** Provide a complete IR learning studio when compatible receiver hardware is attached: capture, decode where possible, replay, edit/name, test, organise and export learned commands. Preserve raw signal timings when decoding is unavailable but safe replay is supported. Reject malformed/noisy/incomplete captures and explain when the signal cannot be reliably learned or reproduced. A phone's built-in transmitter alone is not an IR receiver.

### 5.5 Device controls — TV

- **FR-040:** Support model-verified power, volume, mute, channel, number pad, source/input, navigation, OK, Back, Home, Menu, Settings, playback and supported app shortcuts.
- **FR-041:** TV main remote prioritises navigation pad, OK, Back, Home, power, volume, channel and source. Number pad, keyboard, touchpad, playback, app shortcuts and advanced actions are available in expandable panels or tabs.
- **FR-042:** Provide phone keyboard and touchpad modes where the connected TV protocol supports them, including text/password entry, Enter/Back and pointer/navigation functions.
- **FR-043:** Discover supported installed TV apps and launch them only when the device exposes a supported local API. Users can customise/favourite supported shortcuts.
- **FR-044:** Provide auto-detected and manually configurable input/source labels (HDMI 1/2, AV, USB, antenna, etc.) only for sources supported by the model.

### 5.6 Device controls — AC

- **FR-050:** Support model-specific power, setpoint, Auto/Cool/Heat/Dry/Fan modes, fan speed, swing, Turbo, Sleep, Eco, timer and display features where supported.
- **FR-051:** Temperature adjustment supports plus/minus, direct numeric entry and slider. Enforce the model's supported temperature range and increment. Celsius is default; Fahrenheit is optional.
- **FR-052:** AC remote offers configurable presets and shows only controls supported by the model/profile.
- **FR-053:** AC IR protocol adapters must handle full-state frames where required. The app must not assume that a temperature-only fragment is valid for protocols that require the full AC state.
- **FR-054:** When a command updates a local desired-state model but the device cannot confirm the result, display Estimated or Unknown, never Confirmed.

### 5.7 Device controls — set-top boxes

- **FR-060:** Support model-verified power, navigation, OK, Back, Home/Menu, number pad, channel up/down, EPG/Guide, favourites and playback controls.
- **FR-061:** Provide local editable channel names/numbers, recently watched, search, favourites and optional EPG/Guide integration where a documented source exists.
- **FR-062:** Channel lists may be retrieved automatically only through supported documented interfaces, manually entered or imported from a supported CSV/file format.
- **FR-063:** TV-volume and receiver-volume routing must be separately configurable and saved per set-top box/TV pairing.

### 5.8 Remote layouts and command controls

- **FR-070:** Provide a professional device-specific preset and a full custom layout editor.
- **FR-071:** Users may add/remove/move/resize buttons; edit labels/icons/colours; change command mapping; save multiple layouts; duplicate, export and restore defaults.
- **FR-072:** Provide touchpad/gesture controls, long-press/repeat, recently used commands and favourites where relevant.
- **FR-073:** Short tap sends one command. Advanced press behaviour supports configurable initial delay/repeat rate and per-button repeat rules. Repeat stops immediately on release. Power buttons never repeat.
- **FR-074:** Haptics and button click sounds are separately configurable and must respect Android accessibility/silent settings.
- **FR-075:** Voice entry is tap-to-use from dashboard and remote screens, optionally through compatible headset activation. No always-listening service. Manual controls remain available.
- **FR-076:** Voice supports English, Hindi and Telugu at launch. Prefer offline speech recognition when installed/available. Online speech fallback is optional, opt-in and free-only; explain when audio leaves the device. Clarify ambiguous commands.
- **FR-077:** Voice commands map to explicit typed actions and may not bypass safety, confirmation, pairing or permission rules.

**Implementation status (2026-11-26):** The first TV/STB layout subset is implemented for imported IR profiles. Users explicitly map an existing imported button to a semantic role (Power, navigation, OK, volume, channel, number pad and category-specific controls); mappings are persisted locally. The app does not infer a role from a command label. Unassigned roles are disabled and imported command evidence remains unchanged. Drag/resize layout editing, haptics, voice and hold-repeat remain future work.

### 5.9 Command execution and state model

- **FR-080:** Each device has an independent command queue. Default execution is sequential for a given transport/device; independent devices may execute in parallel.
- **FR-081:** Support configurable inter-command delays, pending-command cancellation, queue visibility and per-device execution status.
- **FR-082:** Queue/scene reports Queued, Sending, Command sent, Confirmed, Estimated, Unavailable or Failed as applicable.
- **FR-083:** Classify commands by idempotency and side effect. Prefer explicit PowerOn/PowerOff. Use PowerToggle only where protocol requires it or the model has no discrete commands.
- **FR-084:** Never automatically retry a potentially state-changing command. When outcome is uncertain, do not send another toggle automatically. Offer a user-initiated retry only when safe.
- **FR-085:** IR transmission completion means “signal sent”, not “appliance changed state”. Unless a supported feedback channel confirms state, use Estimated or Unknown.
- **FR-086:** Command result is device- and command-specific. A device may have partial compatibility; supported controls remain usable while unsupported controls are disabled/identified.
- **FR-087:** If a status is stale, display last-known value with timestamp and stale/unknown indication.

### 5.10 Scenes, macros and automations

- **FR-090:** Support user-created one-tap scenes and macros across multiple devices, configurable step order and delay, sequential default execution and optional parallel operations for independent devices.
- **FR-091:** When one device fails during a scene, continue independent steps by default and report the result of every device. Each scene may optionally stop on first failure.
- **FR-092:** Never automatically roll back a failed/partial scene. Show completed/failed/unconfirmed actions and offer explicitly configured, user-approved recovery scenes.
- **FR-093:** Provide pause/cancel for pending steps, scene history and per-device status. Cancellation cannot promise reversal of a command already transmitted.
- **FR-094:** Support one-time, daily, weekly and selected-day schedules, conditions and supported local device-state triggers.
- **FR-095:** Scheduled automation is created and enabled only by explicit user action. Show devices, actions, schedule and conditions before activation. Imported automation remains disabled until approved.
- **FR-096:** Require confirmation for disruptive multi-device automations. Pause affected automations when their referenced profile or command mapping changes materially. Provide immediate pause/disable/delete.
- **FR-097:** Persist schedules across app updates and phone reboot. Restore eligible schedules after reboot. Skip stale missed commands by default; allow per-automation grace-period recovery. Never replay a backlog of missed state-changing actions.
- **FR-098:** Normal schedules use battery-friendly approximate timing. Request exact-alarm permission only for a user-selected function that genuinely needs it and only where Android allows. Show the difference between approximate and exact timing.
- **FR-099:** Scheduling requires the phone to be powered on and the relevant local transport to be reachable. The app must disclose that local-only scheduled actions cannot run if the phone is switched off, out of range or disconnected from the required network.

### 5.11 Widgets and Quick Settings

- **FR-100:** Provide Android home-screen widgets for device controls, favourites, scenes and macros, with multiple sizes and device-appropriate layouts.
- **FR-101:** Provide configurable Quick Settings tiles/shortcuts for devices, favourites, scenes and macros to the extent permitted by Android APIs.
- **FR-102:** Widgets/tiles use the same typed commands, state honesty, queue rules, security and power safeguards as the main app.
- **FR-103:** Require phone unlock for remote commands by default. Lock-screen controls are not enabled by default; only explicitly selected safe shortcuts may be offered where Android supports them. Never expose pairing secrets or sensitive settings.

### 5.12 Backup, restore, settings and accessibility

- **FR-110:** Provide automatic local backups and manual export/restore. Include saved device profiles, custom remotes/layouts/button mappings, favourites, scenes/macros, connection preferences, settings and catalogue selections.
- **FR-111:** Default profile backup is unencrypted, with optional password-encrypted backup. Pairing credentials/secrets must always be encrypted and protected regardless of export setting; if credentials cannot be safely migrated, require re-pairing on the new phone.
- **FR-112:** Restore offers preview, duplicate detection and merge/replace choices. Imported profiles/commands must be schema-validated and size-limited before use.
- **FR-113:** Provide user-initiated scrubbed diagnostic export only. No automatic diagnostic/crash uploads. Do not include credentials, pairing tokens or private device names in diagnostics.
- **FR-114:** Optional biometric app lock protects the whole app or selected sensitive operations. Default is direct app access. Provide device-credential fallback and safe recovery; biometric failure must not erase profiles.
- **FR-115:** Provide TalkBack labels, scalable text, large touch targets, clear focus states, non-colour-only status, gesture alternatives and configurable haptics throughout screens, remotes, widgets and custom controls.
- **FR-116:** Locale follows Android settings by default, with manual override; English, Hindi and Telugu. AC Celsius default, Fahrenheit option; date/time formats follow regional settings unless overridden.
- **FR-117:** Notifications are configurable for user-requested automation results, reminders and selected scheduled events. No promotional push, continuous device monitoring or automatic disconnection notifications. Notification permission is requested only when the user enables notifications.
- **FR-118:** Provide guided troubleshooting for connection, discovery, permission, IR hardware and command failures. Show likely causes and step-by-step user checks; preserve saved profiles; permit user-initiated retry only where safe. Troubleshooting must not automatically retry a state-changing command or upload diagnostics.

### 5.13 Updates, catalogue and release distribution

- **FR-120:** Include an initial offline catalogue sufficient for core first-release profiles. Catalogue expansions are cached locally and usable offline after download.
- **FR-121:** Catalogue is versioned, signed, schema-validated and provenance/licence-aware. Update notification presents added/changed/corrected profiles and waits for user approval.
- **FR-122:** Catalogue update must not overwrite user names, custom commands, layouts or saved identity. Failed update retains the previous working catalogue and permits rollback.
- **FR-123:** App update checks are optional/network-dependent, notify with release notes and ask before proceeding. Never interrupt an active remote session.
- **FR-124:** Provide signed APK releases through GitHub Releases and Google Play distribution when available. A GitHub APK update must use the same signing identity as earlier releases and require the user to approve Android package installation.
- **FR-125:** User controls the release-signing key. Private key is never committed to the public repository; CI signing secrets are protected and offline encrypted backups retained.
- **FR-126:** Preserve local data across app and catalogue updates. Provide export and full reset/delete-data controls.

### 5.14 Advertising and purchases

- **FR-130:** Core remote features are free. No paid feature gates, forced video, full-screen interruption or delayed remote command.
- **FR-131:** Ads may appear only in allowed non-remote contexts such as dashboard/catalogue/settings; never inside remote controls, during discovery, pairing, reconnection or command execution. Ads do not load offline.
- **FR-132:** Ads must be non-personalised and cannot use device names, brands, rooms, command history or appliance state for targeting. No app analytics, behavioural profiling or ad-related tracking by OneRemote itself.
- **FR-133:** An ad SDK is admitted only after a documented data-flow, permission, privacy-policy and Play Data Safety review confirms it does not violate the maximum-privacy requirement. If no qualifying SDK is available, ship without ads rather than weaken the privacy baseline.
- **FR-134:** Optional ad removal is an in-app purchase only, with provisional India price targets ₹19–₹29/month or ₹99–₹149 lifetime. Final prices require tax/platform-fee review.
- **FR-135:** Google Play Billing is the intended purchase route for the Play build. Do not advertise purchase availability in a GitHub-sideload build unless entitlement verification works without an unapproved backend/service and is validated. The app must never claim an unverified purchase is permanent.

## 6. Non-functional requirements

### 6.1 Compatibility

- **NFR-001:** Minimum Android API 26 (Android 8.0). Phones and tablets, portrait/landscape and 32/64-bit where dependencies support.
- **NFR-002:** At the 2026-11-26 research date, Google Play new apps/updates must target at least API 36 from 2026-08-31. Target/compile levels must be revalidated immediately before each release. The architecture must be ready for API 37's local-network permission model.
- **NFR-003:** Detect and disclose device features (IR emitter, BLE/classic support, Wi-Fi/LAN capability) at runtime. Never require root.

### 6.2 Performance and resource use

- **NFR-010:** Target dashboard time-to-first-useful-frame under 2 seconds on a defined capable reference phone. Measure with Macrobenchmark; report device/OS and test conditions.
- **NFR-011:** Target local command dispatch under 300 ms where the transport and protocol permit. This target measures app dispatch to transport, not end-device response or IR carrier duration.
- **NFR-012:** Startup must not block on discovery. Favourites may be prioritised. A slow or failed device may not stall the UI or another device's queue.
- **NFR-013:** No continuous scanning service merely to keep devices visible. Use launch/foreground/manual discovery and event-driven, opt-in scheduled work only.
- **NFR-014:** Balanced APK: core app and essential profiles bundled; optional catalogue expansions downloaded only when online and cached for offline use.

### 6.3 Reliability and data integrity

- **NFR-020:** Database migrations must be tested with representative old versions and backups. Failed migration must not silently wipe user data.
- **NFR-021:** Catalogue signatures, schema, hashes, file sizes and version monotonicity must be verified before activation.
- **NFR-022:** Connection or command failures are isolated per device. No silent data loss or duplicate saved devices.

### 6.4 Privacy and security

- **NFR-030:** No analytics, behavioural profiling, automatic diagnostics upload, unnecessary data collection or account.
- **NFR-031:** Sensitive credentials are encrypted with keys held in Android Keystore. Prefer hardware-backed protection when available; never assume all phones have secure hardware.
- **NFR-032:** Imports are untrusted input. Validate schema, command bounds, file size, archive paths, URLs and protocol ranges; never execute imported scripts/code.
- **NFR-033:** Use minimal, contextual permissions and explain the purpose. Denial must not disable unrelated IR or saved-device functions.
- **NFR-034:** LAN control is restricted to the active local network and documented device endpoints. No remote relay, port sweeping, exploit or authentication bypass.
- **NFR-035:** Local logs are redacted and bounded. Sensitive material must not appear in Logcat, crash traces or exported diagnostics.

### 6.5 Accessibility and quality

- **NFR-040:** All primary operations must be usable with TalkBack, font scaling, high contrast, switch/accessibility navigation and touch alternatives.
- **NFR-041:** Minimum target touch size follows Android accessibility guidance; no essential action depends on colour alone or a gesture-only interaction.
- **NFR-042:** Release-quality CI is mandatory. No known blocker/high-severity safety, privacy, security or data-loss defect may ship.

## 7. Device compatibility and support labels

Each model/protocol profile has one of these statuses:

1. **Protocol verified:** Protocol framing/command encoding has been tested against the intended transport or a suitable protocol fixture. This is not physical appliance certification.
2. **Physically tested:** The named model/revision was tested against real hardware, with test date, transport, phone/accessory, commands and outcomes recorded.
3. **Unverified:** Candidate or community-supplied profile that has not passed review. It must be labelled and cannot be presented as fully supported.

Partial compatibility is allowed: working commands may be enabled, unsupported controls marked unavailable, and the device must not be described as fully supported if only some functions work.

## 8. Permission and operating-system behaviour

Permission prompts are contextual. Bluetooth permissions follow target-SDK/OS rules; on Android 12+ scanning and connecting require the applicable Nearby Devices permissions. Older Android versions can have Bluetooth scanning/location permission differences. Android 17 (API 37) enforces `ACCESS_LOCAL_NETWORK` for apps targeting API 37+ that perform raw LAN/multicast/unicast traffic. The app must test permission granted, denied, revoked and re-requested states. Location is requested only where the OS and selected API actually require it; do not request it merely because the app scans Bluetooth on modern Android where the app can assert no location derivation.

## 9. Release acceptance summary

A release candidate is acceptable only when:

- Each advertised first-release function has an implemented protocol adapter and a passing test.
- No mock/fake control or false connection/state claim remains.
- Power and scene safeguards pass all mandatory safety tests.
- Offline operation and saved-profile persistence pass.
- Permissions, API 26 compatibility and current target-SDK requirements pass.
- Signed catalogue and APK update validation pass.
- Backup/restore and migration tests pass without data loss.
- Privacy/ad SDK review is complete or advertising is disabled.
- Release signing key ownership and secure backup are verified.
- Automated blocker tests pass and physical hardware test status is labelled accurately.

## 10. Superseded decisions and interpretation

- Earlier household/room hierarchy requirements are superseded: the final product has one personal device list, no room/floor/household model.
- Earlier “no ads” choice is superseded by later explicit opt-in to limited non-personalised advertising, subject to the privacy gate in FR-133.
- Initial simple remote ideas are superseded by full device-specific remotes, advanced customisation, widgets, Quick Settings, voice, scenes and automation.
- “All protocols” means implement all relevant documented/usable methods in an extensible framework, not claim that every vendor device supports each protocol.
- Requirement 131 was not separately answered before requirements were closed. The specification therefore adopts the recommended smart scheduling policy as a provisional engineering default: battery-friendly approximate timing by default and exact alarms only when needed, explicitly requested and permitted. Treat this as a planning assumption, not a confirmed user preference.

## 11. Source references checked 2026-11-26

- Android local network permissions and Android 17/API 37 enforcement: https://developer.android.com/privacy-and-security/local-network-permission
- Google Play target API requirements (API 36 required for new apps/updates from 2026-08-31): https://developer.android.com/google/play/requirements/target-sdk
- Android Bluetooth permissions: https://developer.android.com/develop/connectivity/bluetooth/bt-permissions
- Android Consumer IR API: https://developer.android.com/reference/android/hardware/ConsumerIrManager
- Android alarms and reboot restoration: https://developer.android.com/develop/background-work/services/alarms
- Exact alarm permission: https://developer.android.com/about/versions/14/changes/schedule-exact-alarms
- Android architecture recommendations: https://developer.android.com/topic/architecture/recommendations
- Room persistence: https://developer.android.com/training/data-storage/room
- Android Keystore: https://developer.android.com/privacy-and-security/keystore
- BiometricPrompt: https://developer.android.com/identity/sign-in/biometric-auth
- Google Mobile Ads data disclosure: https://developers.google.com/admob/android/next-gen/privacy/play-data-disclosure
- Play Console one-time registration fee: https://support.google.com/googleplay/android-developer/answer/6112435
- New personal developer account testing requirement: https://support.google.com/googleplay/android-developer/answer/14151465
- Existing Google Play listing using “OneRemote” in a similar TV/AC remote category (branding conflict signal; not a trademark search): https://play.google.com/store/apps/details?id=com.alicious.oneremote