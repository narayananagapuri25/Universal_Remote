# OneRemote — Requirements Traceability Matrix

| Field | Value |
|---|---|
| Document ID | UR-TRACE-001 |
| Version | 1.0 |
| Date | 2026-11-26 |
| Status | Consolidated decision mapping |

This matrix maps the gathered decision IDs (R1–R131) to the consolidated Software Requirements Specification (SRS) and verification areas. Later clarifications supersede earlier conflicting decisions. It is intentionally grouped by source requirement number so the product owner can trace each answer without maintaining duplicate requirement prose.

| Source decision(s) | Requirement theme / final interpretation | SRS / architecture trace | Verification trace |
|---|---|---|---|
| R1 | Phone hardware capability detection; built-in/external IR, Wi-Fi and Bluetooth; no root | SRS FR-030–034, NFR-001–003; Architecture §§7, 7.1–7.4 | TEST §2.5, OS matrix |
| R2, R34, R65 | Ten long-term categories; first release TV, AC and STB | SRS §§1, 4.1–4.2 | TEST §2.5 |
| R3, R40, R42, R70, R85 | Guided discovery/setup; new-device approval; manual model/profile setup and test | SRS FR-001–003, FR-020–027; Architecture §§7–8 | TEST §§2.4, 2.5 |
| R4, R24, R72–R76 | Dedicated/custom remotes, touchpad, keyboard, voice, macros, favourites, long press | SRS FR-070–077, FR-090–099 | TEST §§2.2, 2.4 |
| R5, R8, R20, R80–R81 | No account, no household/room/floor hierarchy; personal device list | SRS §§2.1–2.2, FR-004; Risk decisions | TEST §2.4 |
| R6, R22, R26, R48–R50, R69 | Local-only multi-transport architecture and compatible accessories; no remote relay | SRS FR-017–018, FR-030–036; Architecture §§7–8 | TEST §§2.3, 2.5, 5 |
| R7, R65–R68 | Model-specific TV, AC and STB controls/catalogue brand targets | SRS §§4.1, 5.5–5.7 | TEST §2.5; model-specific physical reports |
| R9, R58 | Android API 26 minimum; phones/tablets; capability detection; no root | SRS NFR-001–003; Architecture §§4, 14 | TEST §3 |
| R10, R92 | Contextual permissions; graceful denial; battery-aware background behaviour | SRS §§8, NFR-013, NFR-033; Architecture §§7, 11 | TEST §§3, 5, 7 |
| R11, R27, R75 | English/Hindi/Telugu; tap-to-use voice; optional offline-first speech, optional free online fallback | SRS FR-075–077, FR-116 | TEST §§2.4, 8 |
| R12, R23, R71, R84, R94–R98 | Premium accessible hybrid dashboard; three main destinations; immediate saved-device launch; no rooms | SRS FR-001–005, FR-015–016; NFR-040–041 | TEST §§2.4, 8 |
| R13, R35, R55, R91 | Limited non-personal ads only if privacy-compatible; no analytics/tracking/profiling | SRS FR-130–133, NFR-030–035; Architecture §14 | TEST §5; privacy release gate |
| R14, R57, R89 | Signed releases/catalogue updates, notify/ask, preserve data, rollback on failure | SRS FR-120–126; Architecture §10, §14 | TEST §§2.3, 5, 6, 9 |
| R15, R86–R87 | Protocol-verified / physically tested / unverified labels; partial support allowed | SRS §7; Architecture §§5, 7 | TEST §§2.2, 2.5 |
| R16, R17, R45, R83, R110–R111 | Manual safe retries only, truthful feedback, explicit power actions, no blind toggle/retry | SRS FR-080–087, FR-083–086; Architecture §6 | TEST §4 (mandatory) |
| R18 | Kotlin/native architecture; zero-cost first; no unapproved paid services | Architecture §§3–4, 14–16; Risks §§3, 5 | TEST §2.1, §9 |
| R19, R21, R88 | Curated versioned catalogue; local custom profiles; free legal data; provenance/license verification | SRS FR-023–024, FR-037, FR-120–122; Architecture §§7.1, 10, 15 | TEST §§2.2, 2.3, 5 |
| R28, R52, R76 | Multi-device scenes/macros, order/delay, parallel independent actions, history/pause/cancel | SRS FR-080–099; Architecture §6, §11 | TEST §§2.2, 4, 6 |
| R29, R77–R78, R122–R123 | Full home-screen widgets and Quick Settings controls; secure by default while locked | SRS FR-100–103; Architecture §12 | TEST §§2.4, 4, 5 |
| R30, R43, R113–R114 | Maximum pairing security; optional app biometric lock and secure credential storage | SRS FR-026, FR-114, NFR-031; Architecture §§8–9 | TEST §5 |
| R31, R39, R50–R51, R83 | Foreground/launch rediscovery; manual/on-demand scan; saved offline devices retained | SRS FR-010–018; Architecture §§8–9 | TEST §§2.3, 6 |
| R32, R74, R95 | Accessibility, haptics/sound controls, theme/contrast support | SRS FR-005, FR-074, FR-115, NFR-040–041 | TEST §§2.4, 8 |
| R33, R61, R93 | Phased internal delivery, full first-release scope; all mandatory automated tests pass | SRS §§1, 9; TEST §§1, 9 | All mandatory suites |
| R36–R37 | In-app Ad-Free purchase only; monthly/lifetime price ranges; avoid sideload billing promises | SRS FR-134–135; Architecture §14; Risks RISK-015 | TEST §5 and billing release gate |
| R38, R44, R98–R99 | Keep unavailable devices; preserve local names; safe identity matching and no duplicates | SRS FR-013–016; Architecture §§5.1, 8–9 | TEST §§2.2, 2.3, 6 |
| R41, R115–R117 | Smart method selection, standby wake methods, network-change recovery | SRS FR-017–018, FR-034–036; Architecture §§7–8 | TEST §2.5, §6 |
| R46, R82, R110 | Local automatic/manual backup, preview, merge/replace, secrets always encrypted | SRS FR-110–113; Architecture §9 | TEST §§2.3, 5, 6 |
| R47, R54, R90 | Strict offline operation; core app/catalogue profiles bundled; optional expansion caching | SRS §§2.1, 6.1, 6.2; Architecture §§9–10 | TEST §6 |
| R53, R97 | Fast non-blocking dashboard and dispatch goals; battery-aware discovery | SRS NFR-010–014; Architecture §§2, 8 | TEST §7 |
| R56 | Manual-only scrubbed diagnostic export; no automatic crash upload | SRS FR-113, NFR-035 | TEST §5 |
| R59 | Owner-controlled release key; never stored in public repository | SRS FR-125; Architecture §14 | TEST §§2.1, 9 |
| R60 | Primary phone / IR hardware details remain TBD pending owner hardware inventory | SRS §9; Test Plan §10; Risks RISK-019 | Physical matrix after hardware inventory |
| R62 | Feature branches, mandatory PR checks and explicit owner approval before each merge | Architecture §14; TEST §9; README | PR and release governance review |
| R63 | India first, global-ready catalogue/localisation architecture | SRS §§1, 4, FR-116 | Catalogue/localisation tests |
| R64 | OneRemote working name only; existing same-category listing means branding clearance needed | SRS product metadata; Risks §§3–4 | Brand/trademark/app-store check before package ID/store listing |
| R79 | Opt-in reminders and automation result notifications only; no promotional/disconnection push | SRS FR-117; Architecture §13 (notifications) | TEST §2.4 |
| R100 | Guided troubleshooting, manual safe retry, preserve saved profiles | SRS FR-118; Architecture §13 (troubleshooting) | TEST §2.4, §5 |
| R101–R102 | AC control temperature range/increments, modes, presets and model-specific capabilities | SRS FR-050–054 | TEST §§2.2, 2.5 |
| R103, R105–R107 | Layered TV layout, keyboard/touchpad, app shortcuts and source controls | SRS FR-040–044 | TEST §2.5 |
| R104, R108–R109 | Layered STB layout, channels/favourites/recent, EPG where documented, channel list import | SRS FR-060–063 | TEST §§2.2, 2.5 |
| R112 | Smart set-top box volume routing; user override and saved preference | SRS FR-035, FR-063 | SAFE-014 |
| R118–R120 | Comprehensive standard/documented LAN, Bluetooth and external IR accessory framework | SRS FR-020–036; Architecture §7 | TEST §2.5, OS matrix |
| R121 | Complete IR learning studio with compatible receiver; raw capture only where supportable | SRS FR-037; Architecture §7.1–7.2 | TEST §§2.2, 2.5 |
| R124 | Advanced long-press and per-button repeat behaviour; power excluded from repeat | SRS FR-072–074 | TEST §2.4, SAFE-008 |
| R125–R127 | Advanced command queues, continue independent scene devices, safe no-rollback recovery | SRS FR-080–093; Architecture §6 | TEST §4 |
| R128–R131 | Complete local automation with maximum approval and smart reboot recovery; exact-alarm policy is an engineering default pending explicit confirmation | SRS FR-090–099; Architecture §11 | TEST §§2.2, 4, 6 |

## 2. Open validation items

These do not reopen the requirements baseline: exact phone/accessory/device models, brand clearance, data licences, privacy-compatible ad provider and purchase entitlement strategy. The empty-repository bootstrap is resolved by the owner-approved one-time README-only initial commit; the ordinary feature-branch/PR approval policy remains mandatory for all later changes.