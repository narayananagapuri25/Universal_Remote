# OneRemote — TV and Set-top-box Platform Compatibility

| Field | Value |
|---|---|
| Document ID | UR-TV-PLAT-001 |
| Version | 0.1 |
| Status | Research baseline; adapters must pass protocol, security and physical gates before product support labels |

## 1. Design rule

OneRemote is manufacturer-neutral at the product level and capability-based at the connection layer. A discovered network service is only a candidate. The app must identify the protocol, complete the protocol's user-approved trust/pairing step, negotiate the capabilities that the endpoint actually advertises, and enable only those controls.

There is no single universal LAN remote-control protocol. The existing Android TV Remote v2 adapter is manufacturer-neutral when a TV exposes the _androidtvremote2._tcp. service; that does not mean every Android TV/Google TV television exposes it.

## 2. Current researched platforms

| Platform | Researched control method | Trust / user setup | Current OneRemote state |
|---|---|---|---|
| Android TV / Google TV | Android TV Remote v2 over local TLS; mDNS service discovery; capability negotiation | Six-character on-screen code pairing; client identity in Android Keystore; TV certificate pinned for remote session | Implemented; first physical test target is Realme TechLife; not yet physically verified |
| LG legacy NetCast | Official LG UDAP 2.0 uses SSDP discovery and HTTP/XML description, pairing, command, event and query operations | Protocol-defined pairing stage | Not implemented. LG UDAP 2.0 is explicitly limited to NetCast 3.0 (2012) and NetCast 4.0 (2013) models; do not apply it to current webOS TVs |
| LG webOS | Community-documented SSAP second-screen WebSocket service; on-screen approval returns a reusable client key | User must approve first connection on TV; client key must be kept private; TLS/certificate behavior needs separate review | Not implemented. Need a verified current webOS model matrix, secure certificate-pinning design and model-specific command tests |
| Samsung Tizen Smart TV | SmartThings app requires registering the TV on the same Wi-Fi and approving an on-screen PIN; remote features vary by app and TV model | TV registration and PIN approval | A public local third-party command API was not established by the official Tizen remote-control guide reviewed. Community WSS protocols are reverse-engineered and need separate terms/security/model review |
| Roku TV / streaming player | Roku documents SSDP discovery and local ECP endpoints; current docs restrict third-party mobile-app ECP command sending | Control by mobile apps must be enabled for certain commands | Do not implement ECP key-command control under the current documented restriction. No usable remote-control support may be claimed |
| Sony BRAVIA Professional Displays | Official local REST/JSON-RPC, IRCC-IP/SOAP and Simple IP Control variants; some require a pre-shared key | IP control enabled on display; PSK for protected API levels | Potential future pro-display adapter only; not universal consumer BRAVIA support |
| Indian DTH / cable set-top boxes | IR profiles from exact receiver model/original remote; some receivers may offer documented vendor APIs | IR requires compatible phone or external emitter; network API must be documented and authenticated | Imported IR profiles supported; bundled model-code catalogue not yet populated; no provider-wide code assumptions |

## 3. Security and permission gates

- Discovery is protocol-specific and bounded. Do not scan arbitrary ports or send guessed commands.
- Android local-network discovery/communication must respect applicable OS and target-SDK permission boundaries, including ACCESS_LOCAL_NETWORK when targeting API 37+.
- A first-time pairing prompt is not equivalent to a verified command channel. Persist credentials only in protected local storage; never log or export them in plain text.
- Self-signed TLS must not be accepted silently. Pairing/first trust must be explicit, then the peer certificate must be pinned or otherwise validated for the saved session.
- Where platform mobile-control terms prohibit third-party control, do not implement or expose that command path.
- Discovery names and network responses are untrusted input. Parse defensively and render names as data.

## 4. Prioritised implementation sequence

1. Complete and physically validate Android TV Remote v2 on the owner's Realme TechLife TV; retain generic manufacturer-neutral naming.
2. Expand IR parsed-protocol support for Flipper Zero v1 files. Philips RC5/RC5X support is now implemented, including persistent per-profile toggle state. Other parsed protocols remain rejected until their encoder and vectors are implemented.
3. Create a provenance-reviewed IR catalogue by exact brand, device model, original remote model and command set. Imported or catalogue entries remain Unverified until vectors are checked and exact hardware commands physically tested.
4. Investigate LG webOS SSAP with current model coverage, on-screen pairing, credential storage and TLS pinning review.
5. Investigate Samsung using an explicitly documented or vendor-approved local interface. Reverse-engineered protocol use requires security, terms and exact model testing.
6. Evaluate Sony BRAVIA Professional Display control as a narrow model-family adapter with PSK setup; keep consumer-TV support separate.
7. Do not implement Roku ECP mobile key commands unless Roku publishes a policy-compliant route for third-party mobile controller applications.

## 5. Research references

- LG official UDAP 2.0 scope and protocol PDF: https://webostv.developer.lge.com/assets/netcast/NetCast-UDAP.pdf
- LG webOS developer portal: https://webostv.developer.lge.com/
- Community documentation of modern LG webOS SSAP pairing/client-key flow: https://docs.rs/tv-rs-webos/latest/tv_rs_webos/
- Samsung SmartThings registration and on-screen PIN approval: https://www.samsung.com/in/support/tv-audio-video/use-the-smartthings-app-to-control-your-smart-tv/
- Samsung Tizen remote-control key API (for applications running on TV, not a public mobile remote API): https://developer.samsung.com/smarttv/develop/guides/user-interaction/remote-control.html
- Roku ECP restrictions, SSDP and mobile-app control requirements: https://developer.roku.com/dev/docs/external-control-api
- Sony BRAVIA Professional Display REST API and PSK access levels: https://pro-bravia.sony.net/remote-display-control/rest-api/structure/
- Android local-network permission for target API 37+: https://developer.android.com/privacy-and-security/local-network-permission

## 6. Support language

The app may say "Compatible Android TV service found" when it discovers the mDNS endpoint. It may say "Paired" only after the on-device pairing handshake succeeds. It may say "Connected" only after the remote session is ready. It may say a command was "Sent" when the transport reports dispatch. It must not say a model is "Supported" or "Verified" until its required controls have been physically tested on that model.
