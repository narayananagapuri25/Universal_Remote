# OneRemote — IR Protocol Implementation and Data Policy

| Field | Value |
|---|---|
| Document ID | UR-IR-001 |
| Version | 0.1 |
| Status | Encoder foundation implemented; model-code catalogue not yet populated |

## 1. Scope

OneRemote separates protocol encoding from model-specific remote-code data.

- A **protocol encoder** converts typed address/command parameters to a carrier frequency and alternating mark/space timings.
- A **device profile** maps an appliance's named buttons to typed protocol commands.
- A **profile evidence record** distinguishes unverified, protocol-verified and physically tested commands.
- A **transmitter adapter** checks the actual phone hardware's carrier-frequency support and attempts to dispatch the waveform.

A protocol implementation does not prove that a particular brand or model uses that protocol or command value.

## 2. Encoder families in the current branch

| Protocol | Carrier | Current encoder behaviour | Important limitation |
|---|---:|---|---|
| NEC | 38 kHz | 8-bit address, inverted address, 8-bit command and inverted command; LSB first; 9 ms / 4.5 ms header and trailing mark | The protocol family alone does not supply model button values |
| Extended NEC | 38 kHz | 16-bit address, 8-bit command and inverted command; LSB first | Legacy address pairs are rejected from the extended form |
| Samsung32 | 38 kHz | Address byte repeated, command byte and inverted command; LSB first; 4.5 ms / 4.5 ms header | Samsung-family remotes can use other frame sizes/variants |
| Sony SIRC12 | 40 kHz | 7-bit command followed by 5-bit address; LSB first; pulse-width modulation | The model profile must identify correct address and command |
| Sony SIRC15 | 40 kHz | 7-bit command followed by 8-bit address; LSB first | The model profile must identify correct address and command |
| Sony SIRC20 | 40 kHz | 7-bit command, 5-bit address and 8-bit extended field; LSB first | The additional field is kept explicit; no automatic inference |

Encoders validate input bit ranges, produce immutable signal snapshots, and share the existing waveform validator. The Android transmitter adapter remains responsible for checking phone support and carrier ranges. Every transmission is a single explicit dispatch; no state-changing retry is performed automatically.

## 3. Model profile and verification rules

Profiles are model-specific, not brand-wide promises. Each button stores:

- Stable button ID and visible label.
- Typed protocol command parameters.
- Optional long-press/repeat intent.
- Evidence status and source reference.
- Optional source-data licence identifier.
- Physical test record ID when labelled physically tested.

The profile validator rejects duplicate button IDs, incomplete profile identity, protocol-verified commands without source references, and physically tested commands without a physical test record.

The current branch contains the profile model and validator only. It does **not** yet contain a bundled list of working Samsung, LG, Sony, Daikin, Voltas, Tata Play, Airtel or other appliance commands. No fabricated or guessed model codes are shipped.

## 4. Data-source and licence policy

No third-party IR command database or encoder source code has been copied into the current implementation. The encoders are independently authored from public protocol descriptions and checked against deterministic unit vectors.

Potential future data sources require per-repository and, where relevant, per-file provenance review:

1. **Flipper-IRDB** — repository materials describe CC0-1.0 for submissions made after the licensing cutover; the repository notes older submissions are not covered. Only entries whose provenance is covered by the licence will be considered.
   - https://github.com/Lucaslhm/Flipper-IRDB
   - https://github.com/Lucaslhm/Flipper-IRDB/blob/main/LICENSE
   - https://github.com/Lucaslhm/Flipper-IRDB/blob/main/README.md

2. **SmartIR** — the project reports an MIT licence and contains community device-code tables. Before importing any individual table, inspect its provenance and file-level notices. The project also expressly restricts use of the SmartIR name in derivatives; OneRemote will not use that name or imply affiliation.
   - https://github.com/smartHomeHub/SmartIR
   - https://github.com/smartHomeHub/SmartIR/blob/master/LICENSE

3. **Global Caché Control Tower** — downloadable code access is available to users under account limits, but partner API integration requires a partner agreement. Download availability is not treated as permission to redistribute a bundled copy. No API or code set will be integrated until redistribution and commercial-use terms are confirmed.
   - https://irdb.globalcache.com/
   - https://www.globalcache.com/downloads.html

4. **Flipper Devices IRDB** — a separate official repository reports an MIT repository licence. Each candidate dataset's actual file and history still need inspection before copying.
   - https://github.com/flipperdevices/IRDB

The catalogue build must preserve source URL/reference, retrieval date, file path or code-set ID, licence identifier and any attribution/notice requirements. Entries with unclear provenance are excluded from shipped data until resolved. Device brand names are used only to identify compatibility; no manufacturer endorsement is implied.

## 5. Protocol references used for independent implementation

- NEC framing, LSB-first bit order, 9 ms leader, 4.5 ms space, inverted bytes and repeat frame: https://www.sbprojects.net/knowledge/ir/nec.php
- Sony SIRC 12/15/20-bit layouts, 40 kHz carrier, pulse-width modulation and bit order: https://www.sbprojects.net/knowledge/ir/sirc.php
- Samsung32 pulse-distance timing and 38 kHz frame references: https://arduino-irremote.github.io/Arduino-IRremote/ir__Samsung_8hpp_source.html
- Android ConsumerIrManager hardware detection, frequency ranges and synchronous transmit contract: https://developer.android.com/reference/android/hardware/ConsumerIrManager

These are protocol descriptions/reference materials. The OneRemote source code is independently written; no source implementation from those projects is copied into the Android app.

## 6. Required verification before compatibility labels

- Protocol-vector tests validate encoding only.
- PROTOCOL_VERIFIED needs a referenced code source and deterministic expected waveform or a captured reference with measured parameters.
- PHYSICALLY_TESTED additionally needs an actual appliance test record identifying appliance model, original remote/source, phone model and Android version, transmitter, carrier and commands tested.
- Partial compatibility is recorded per button; passing Power does not prove Volume, Input, Mode or every other button.
- IR dispatch success means only that the Android transmit call returned. It never means that the appliance changed state.
