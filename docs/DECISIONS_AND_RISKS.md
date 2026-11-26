# OneRemote — Decisions, Feasibility Constraints and Risk Register

| Field | Value |
|---|---|
| Document ID | UR-RISK-001 |
| Version | 1.0 |
| Date | 2026-11-26 |
| Status | Open risks; mitigation decisions proposed |

## 1. Confirmed product decisions

| Area | Baseline decision |
|---|---|
| Product identity | “OneRemote” is a provisional working name, not cleared for release |
| Platform | Native Kotlin Android; minimum API 26 |
| First release | TV, AC and set-top box; full real functionality for supported model/protocol combinations |
| Later categories | Projector, audio, fans, streaming, media players, smart home and compatible appliances |
| Accounts/cloud | No login, no account, no household profiles, no mandatory cloud or remote relay |
| Operation | Local/offline-first; internet only for optional catalogue/app updates, eligible ads and optional online speech |
| Saved devices | Persist until user deletes; automatically rediscover trusted network/Bluetooth devices on launch/foreground; keep offline profiles visible |
| New device trust | User approval and pairing; authenticated method where supported; warn on legacy unauthenticated protocols |
| Connection selection | Smart route recommendation, manual override, saved preference; no unsafe silent fallback |
| Dashboard | Favourites/quick actions; connected devices; saved/unavailable devices; search and Add Device |
| Main navigation | Devices, Discover, Settings |
| Remote design | Device-specific presets plus full custom layout editor |
| Commands | Per-device queues; sequential by default; parallel only for independent targets; no automatic retry for state-changing commands |
| Power | Prefer explicit On/Off; toggle only where needed; no blind second toggle |
| IR state | “Sent” is not “confirmed”; IR-only devices usually have unknown/estimated state |
| Scenes | Continue independent devices by default; optional stop-on-failure; no automatic rollback; user-approved recovery |
| Schedules | Full schedule/condition builder; approximate by default; exact alarm only when needed and permitted; no missed-command backlog |
| Offline | Configured remote functions do not require internet |
| Privacy | No analytics, tracking, behavioural profiling or auto diagnostics; reject incompatible SDKs |
| Ads | Limited non-personal ads only if SDK passes privacy gate; otherwise no ads |
| Purchase | Optional Play in-app ad-removal only if secure and policy-compliant; sideload build not promised |
| Backup | Local automatic + manual export/restore; profile export unencrypted by default; secrets always encrypted |
| Signing | User owns private signing key; never commit it; encrypted offline backup |
| Updates | Notify and ask; signed app/catalogue; preserve local configuration; no interruption to active remote |
| Workflow | Feature branches, mandatory PR checks, explicit owner approval before every merge to `main` |

## 2. Feasibility constraints — do not turn into marketing promises

### 2.1 There is no universal Android remote-control interface

An app can control a device only when the phone has a compatible transmitter or the device exposes a usable, documented local protocol. mDNS/SSDP discovery, Bluetooth pairing or a device name alone does not imply that commands are supported. Traditional IR-only devices are not discoverable by network discovery; they require a suitable IR transmitter and a profile/manual setup.

### 2.2 Built-in Android IR is optional hardware

Android's `ConsumerIrManager` requires the device IR feature, exposes supported carrier ranges and transmits a pulse pattern. The public API does not provide a general receiver/learning method. It also documents a maximum pattern duration under two seconds. Therefore:

- phone IR capability is checked at runtime;
- no built-in IR is assumed;
- IR learning needs a compatible external receiver;
- longer frames/repeats need protocol-aware segmentation only if the target hardware/protocol supports it;
- external USB-C/Bluetooth/network transmitters each need adapter-specific validation.

### 2.3 Bluetooth is not an appliance protocol by itself

A TV may use Bluetooth for a keyboard, remote accessory or pairing, but each function has a separate profile/protocol. Only implemented, documented controls are enabled. Generic Bluetooth pairing cannot be advertised as generic TV control.

### 2.4 Local network discovery has an Android 17 permission boundary

Android 17/API 37 enforces `ACCESS_LOCAL_NETWORK` for target-37+ apps doing local IP/multicast operations. The app must show context and request local network permission for broad home-automation discovery/control where needed. If denied, manual IR control and other unrelated functionality still work; LAN discovery/control must be shown unavailable until permission is granted.

### 2.5 Automation cannot run when the phone is off

Local-only schedules depend on the phone being powered on, the app having the necessary OS scheduling permission, and the target transport/device being reachable. A phone cannot transmit IR or send LAN commands while powered off. Exact alarms are restricted and can be denied/revoked; approximate tasks can be deferred under Doze/battery restrictions.

### 2.6 TV/STB HDMI-CEC is not directly exposed to every phone

Volume routing may use TV IR, STB IR, vendor APIs or an available HDMI-CEC bridge/endpoint. The phone cannot assume it is a CEC controller simply because TV and receiver share HDMI. Select only tested paths and avoid duplicate volume actions.

## 3. High-priority risks

| ID | Risk | Severity | Mitigation / release gate |
|---|---|---|---|
| RISK-001 | Existing Google Play app uses exact “OneRemote” name in a similar TV/AC universal remote category | High (brand/release) | Keep as internal working name only. Do not finalise package ID, icon, domain or store listing until app-store/trademark/company/name clearance. Consider a more distinctive name; do not silently rename without owner decision. |
| RISK-002 | The user expects broad brand coverage but models use different codes/APIs | Critical | Catalogue is model/protocol-specific; label protocol-verified/physically-tested/unverified; no blanket brand claims. |
| RISK-003 | Generic USB-C/Bluetooth IR accessory assumptions fail | High | Maintain verified accessory catalogue and adapter per interface/model; unsupported hardware gets clear error. |
| RISK-004 | Built-in phone lacks IR emitter; user expects every phone to control old TVs/ACs | High | Detect hardware at runtime; list compatible external accessories; keep manual profile setup distinct from actual transmission capability. |
| RISK-005 | IR learning expected from transmit-only phone | High | Explain receiver requirement; learning flow requires a compatible external IR receiver. |
| RISK-006 | IR signal databases contain restrictive or mixed data licences | Critical | Audit file-level licence/provenance before bundling. Flipper-IRDB reports CC0; Flipper Devices IRDB reports MIT; verify licence scope at implementation. Exclude `probonopd/irdb` unless its custom terms are accepted (notice, informing maintainers and up to three product copies on request). |
| RISK-007 | Protocol library has copyleft obligations or is designed for ESP hardware, not Android | High | Do not embed IRremoteESP8266 without LGPL-2.1 and technical review. Prefer independent Kotlin encoders or vetted permissive libraries. |
| RISK-008 | Android 17 denies broad LAN traffic for target API 37+ without permission | High | Add API/target-aware `ACCESS_LOCAL_NETWORK` path; test denial/revocation; keep non-LAN controls usable. |
| RISK-009 | Discovery succeeds but device does not accept control commands | High | Capability test after discovery, typed profile adapter and real command verification; pairing not equal control. |
| RISK-010 | AC full-state IR protocol is encoded incorrectly by isolated command fragments | Critical (safety/reliability) | Model full desired state; protocol-specific full-frame generation, golden vectors and physical tests per model. |
| RISK-011 | IR command delivery incorrectly reported as device state confirmation | Critical | Separate transport result from device state. IR result is Sent/Estimated/Unknown unless confirmed through a supported feedback channel. |
| RISK-012 | Toggle command retried or duplicated and leaves appliance in wrong state | Critical | Typed On/Off/Toggle, no automatic retries, dedupe and safety interlocks across main app/macros/widgets/tiles. |
| RISK-013 | Scheduled automation executes late or while phone is off/out of range | High | Approximate-by-default; explicit limits; no backlog; schedule history; user-defined grace handling; disclose phone/transport dependency. |
| RISK-014 | Ads SDK collection conflicts with maximum privacy requirement | Critical (privacy) | Do not select ad provider until full current data inventory and SDK configuration audit. GMA Next-Gen disclosure currently lists IP-derived location, user interactions, diagnostics and device/account identifiers; non-personalised does not mean zero data. Exclude if it conflicts. |
| RISK-015 | Play billing on sideload APK cannot be securely verified without backend | High | Confine billing to Play build; no sideload purchase promise. Evaluate client-only Play Billing threat model and whether backend verification is required before enabling products. |
| RISK-016 | Public Play distribution has a registration cost despite the zero-budget development goal | Medium | Full distribution currently requires a one-time US$25 registration fee; the free limited-distribution plan is capped at 20 devices and is not public mass distribution. GitHub Releases remain a separate distribution path. Owner decides whether/when to pay. |
| RISK-017 | New personal Play developer account may have closed-test gate | Medium | If account is a new personal account created after 2023-11-13, current requirement is at least 12 opted-in testers for 14 continuous days before production-access application. Validate account type/date. |
| RISK-018 | Target SDK requirement changes before release | High | At 2026-11-26, Play requires API 36+ for new apps/updates from 2026-08-31; recheck policy at release. Test API 37 local-network changes now. |
| RISK-019 | No physical test hardware or phone IR model supplied yet | High | Keep model-level profiles unverified; create compatibility lab plan as soon as devices are known; do not claim physical tests without evidence. |
| RISK-020 | Brand protocol licensing/reverse-engineering restrictions | High | Use documented local APIs, legally usable open protocols and user-learned commands; review terms and legal risk before vendor-specific implementation. |
| RISK-021 | Saved credentials lost/undecipherable after app reinstall/device migration | Medium | Explicit credential restore limitation; secrets encrypted with Keystore; design re-pair flow; backup previews disclose what transfers. |
| RISK-022 | Catalogue update corrupts or overwrites custom profile | Critical | Signed immutable catalogue plus user overlays, atomic activation, backup/rollback and migration tests. |
| RISK-023 | Android OEM battery managers delay background automation | High | Do not promise exact execution; show vendor battery guidance only when relevant; user-configurable grace and missed-run behaviour. |
| RISK-024 | Widgets/tiles bypass app security or show stale state | High | Same command executor and safety rules; lock by default; unknown/stale labels; Android instrumentation tests. |
| RISK-025 | Empty repository prevented creation of a feature branch before an initial commit | Resolved (workflow) | Owner approved a one-time README-only bootstrap commit on `main` on 2026-11-26. Every subsequent documentation/source change must use a feature branch and PR, with mandatory checks and explicit owner approval before merge. No application code is part of the bootstrap. |

## 4. Branding research finding

A Google Play listing titled **“OneRemote: TV & AC Remote”** already exists and describes a remote-control app for TVs and other home devices (listing updated 2026-08-06 at the research date). This is a direct naming conflict signal, although it does not establish trademark ownership or legal infringement. “OneRemote” must remain a code/working name only until proper India trademark, app-store, domain and relevant global market checks are complete. Do not allocate a final package namespace based on this provisional name.

Listing: https://play.google.com/store/apps/details?id=com.alicious.oneremote

## 5. Zero-budget and distribution facts

- For public/full Android app distribution, the current Android Developer Console full-distribution plan requires a one-time US$25 registration fee. A no-fee limited-distribution plan is capped at 20 devices and is not a substitute for public distribution. Current console documentation says the distribution plan cannot be changed later, so the account owner must choose deliberately.
- New personal accounts created after 2023-11-13 currently need a closed test with at least 12 opted-in testers continuously for 14 days before they can apply for production access. This does not necessarily apply to every account type; verify the actual account.
- GitHub Releases can distribute signed APKs without Google Play registration, but users must trust the signing certificate and approve installation/update through Android's package installer.
- Play app submissions require target API 36 or higher as of 2026-08-31 at this research date. Revalidate the policy for the actual submission date.

## 6. Unresolved engineering gates (not more requirement questions)

These are fact-finding and validation tasks for implementation, not a request to reopen the product questionnaire:

1. Identify the primary Android phone model and test whether it has Consumer IR and which frequencies are supported.
2. Inventory actual TV, AC, set-top box, USB-C IR, Bluetooth IR and network IR hardware available for testing.
3. Select the final product name and package ID after clearance.
4. Decide whether the project accepts the Play registration charge and, if account is new personal, recruit testers for the required closed test.
5. Approve a privacy-compliant ad SDK only if it meets the strict data rules; otherwise ship without ads initially.
6. Decide the billing threat model for Play vs sideload builds; no backend is allowed unless explicitly approved.
7. Complete file-level data/protocol licence audit for every initial IR profile.
8. Choose and verify a catalogue signing/release-key backup procedure.

## 7. Repository bootstrap decision and audit

GitHub confirmed `narayananagapuri25/Universal_Remote` was public, size 0, with no branch references and no commit history. GitHub could not create a feature branch from an empty repository. On 2026-11-26, the owner explicitly approved a one-time README-only initial commit on `main` to establish the root commit. This is a narrow, recorded exception to the normal branch policy. The engineering documentation set is submitted separately on `docs/engineering-baseline-v1`; application code must also use feature branches. Nothing may be merged to `main` without mandatory checks and the owner's explicit approval. The bootstrap commit contains no application code.

## 8. Sources checked 2026-11-26

- Existing OneRemote app listing: https://play.google.com/store/apps/details?id=com.alicious.oneremote
- Android local network permission/API 37: https://developer.android.com/privacy-and-security/local-network-permission
- Google Play target API policy: https://developer.android.com/google/play/requirements/target-sdk
- ConsumerIrManager: https://developer.android.com/reference/android/hardware/ConsumerIrManager
- Bluetooth permissions: https://developer.android.com/develop/connectivity/bluetooth/bt-permissions
- Alarms and reboot: https://developer.android.com/develop/background-work/services/alarms
- Exact alarm permission: https://developer.android.com/about/versions/14/changes/schedule-exact-alarms
- Google Mobile Ads data disclosure: https://developers.google.com/admob/android/next-gen/privacy/play-data-disclosure
- Android Developer Console distribution plans and full-distribution registration fee: https://support.google.com/android-developer-console/answer/16604405
- Android Developer Console distribution options: https://support.google.com/android-developer-console/answer/16640817
- New personal developer account test requirement: https://support.google.com/googleplay/android-developer/answer/14151465
- Flipper-IRDB CC0 dataset candidate: https://github.com/Lucaslhm/Flipper-IRDB
- Flipper Devices IRDB (MIT): https://github.com/flipperdevices/IRDB
- probonopd/irdb special terms: https://github.com/probonopd/irdb/blob/master/LICENSE.md
- IRremoteESP8266 LGPL-2.1: https://github.com/crankyoldgit/IRremoteESP8266/blob/master/LICENSE.txt