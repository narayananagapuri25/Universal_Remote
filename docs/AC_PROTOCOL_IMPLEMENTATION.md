# OneRemote — Daikin ARC AC Protocol Foundation

| Field | Value |
|---|---|
| Document ID | UR-AC-001 |
| Version | 0.1 |
| Status | Independent state-frame encoder and one-shot dispatcher implemented; no user-facing Daikin state editor yet |

## 1. Model scope

This encoder targets the reverse-engineered Daikin ARC466A9-style three-frame transport and ARC480A11-style one-frame transport described in the protocol reference below. The reference reports tests against specific Daikin FTXS35/42/50 K2V1B units. OneRemote has not yet physically tested these models. The encoder must not be treated as a universal Daikin or universal air-conditioner controller.

## 2. Frame and timing facts

- Carrier: 38 kHz.
- Modulation: pulse-position modulation, bytes and bits sent least-significant-bit first.
- Bit mark: 454 µs.
- Zero space: 413 µs.
- One space: 1,280 µs.
- State frame: 19 bytes, header \`11 DA 27 00 00\`, final byte is the sum of the first 18 bytes modulo 256.
- Three-frame transport: preamble, identity frame, clock frame and state frame; the documented frame contains 584 timing edges.
- One-frame transport: preamble and state frame; the documented frame contains 320 timing edges.

The implementation emits alternating positive mark/space durations for Android \`ConsumerIrManager\`. Inter-frame gaps are represented as space durations, and each frame includes a terminating mark.

## 3. Supported state fields

The independent Kotlin model and encoder support:

- Power on/off as an absolute state bit (not a toggle).
- Auto, Dry, Cool, Heat and Fan modes.
- Half-degree setpoints in the published mode ranges: Auto 18–30°C, Cool 18–32°C, Heat 10–30°C. Dry uses the fixed \`0xC0\` no-setpoint marker.
- Fan levels 1–5, Auto, Night and Comfort.
- Vertical and horizontal swing.
- Intelligent Eye, Econo, Outdoor Quiet and Powerful.
- Both documented transport variants and the three-frame wall-clock frame.

Safety/state rules:
- Every transmission carries a complete state frame. It is not safe to treat the encoder as a stream of independent temperature or mode toggles.
- Powerful mode clears Econo and Outdoor Quiet and downgrades Comfort fan to Auto; Intelligent Eye remains preserved.
- Comfort fan requires the three-frame variant and vertical swing off.
- Timer enable bits are left disabled and their documented per-variant disabled sentinel is emitted. Timer programming is not implemented.
- The weekly schedule flag only communicates the state of an already-programmed schedule. OneRemote does not program the schedule itself.

## 4. OneRemote implementation boundary

Files:
- \`DaikinAcState.kt\` — typed full-state model and protocol enums.
- \`DaikinAcEncoder.kt\` — state frame, identity frame, clock frame and raw timing synthesis.
- \`DaikinAcCommandDispatcher.kt\` — one-shot dispatch to the shared IR transmitter, no automatic retry.
- \`DaikinAcEncoderTest.kt\` and \`DaikinAcCommandDispatcherTest.kt\` — golden frames, checksum, timing lengths, state boundaries, mutual exclusion and dispatch tests.

The engine is not yet connected to the saved-profile schema or the user-facing AC remote editor. The current app UI imports static \`.ir\` button profiles; it does not yet expose a dynamic Daikin mode/temperature/fan/swing editor. Do not advertise Daikin AC control as user-ready until that editor and a model-specific profile are added and physically tested.

A successful Android transmit call means only that the signal-dispatch API returned. It does not confirm the air conditioner received or applied the state.

## 5. Data and source licence policy

This is an independent Kotlin implementation written from the protocol descriptions and test-vector facts cited below. No source code from another encoder library has been copied.

- The GitHub repository \`BokoEnos/daikin_ir_encoder\` currently reports MIT in its repository \`LICENSE\` file. The associated PyPI package metadata has been reported with an Apache-2.0 licence, so those metadata sources are inconsistent. OneRemote does not copy or bundle that project code.
- The Gree encoder \`ClusterM/gree-rc-encoder\` is GPL-3.0. It is not used or copied into OneRemote.
- Brand and model references are for interoperability identification only; OneRemote is not affiliated with or endorsed by Daikin.

Candidate protocol/code datasets still require provenance, file-level licensing and redistribution review before bundling.

## 6. Protocol references

- Daikin protocol summary, byte layout, timing, state fields, transport variants and model test notes: https://github.com/BokoEnos/daikin_ir_encoder
- Daikin repository MIT licence: https://github.com/BokoEnos/daikin_ir_encoder/blob/main/LICENSE
- Gree encoder and GPLv3 licence declaration: https://github.com/ClusterM/gree-rc-encoder
- Android ConsumerIrManager contract (carrier support and raw timing dispatch; no duty-cycle parameter): https://developer.android.com/reference/android/hardware/ConsumerIrManager

The protocol information is reverse-engineered/community documented. It is not a manufacturer-issued universal protocol specification. Compatibility labels remain Unverified until OneRemote has the correct model profile and performs the required protocol or physical tests.
