# OneRemote — Engineering Baseline

**Working product name:** OneRemote (provisional; branding clearance required)  
**Baseline:** v1.0  
**Date:** 2026-11-26  
**Status:** Requirements consolidated; specification review pending  
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

## Approval and implementation policy

1. Review the SRS, architecture, test plan and risk register.
2. Resolve the branding, legal/licensing, monetisation and hardware test gates listed in the risk register.
3. Review the engineering-baseline pull request and explicitly approve it before merge.
4. Implement only on feature branches.
5. Require mandatory CI checks and explicit owner approval before every merge to `main`.
6. Do not publish an APK until all release gates in `docs/TEST_PLAN.md` pass.

## Source references

The technical decisions are based primarily on current official Android and Google Play documentation. A curated source list appears at the end of each relevant document. Links were checked on 2026-11-26; target API and platform requirements must be rechecked at release time.
