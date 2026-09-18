# Review: Stack Version Fact-Check — ARCHITECTURE-SPINE.md

**Reviewer:** version fact-check pass (web search verification)
**Date of check:** 2026-09-17
**Scope:** `## Stack` table and version references in ARCHITECTURE-SPINE.md

---

## Verdict summary

| Claim | Status | Note |
| --- | --- | --- |
| Kotlin 2.4.20 | OK | Confirmed current stable |
| Jetpack Compose BOM 2026.08.00 | OK | Confirmed real, current |
| Compose Compiler Gradle plugin 2.3.21 | **STALE / MISMATCHED** | Wrong pairing with Kotlin 2.4.20 — see below |
| Firebase Android BoM ~34.17.0 | **LIKELY ONE BEHIND** | Doc already hedges "confirm at build time"; a newer BoM appears to exist |
| Cloud Functions 2nd gen, Node.js 22 GA | **INCOMPLETE, NOT WRONG** | Node.js 24 also reached GA; product also being rebranded "Cloud Run functions" upstream |
| Firestore / Auth / FCM product names | OK | No deprecation or renaming found |

---

## Findings

### 1. Kotlin 2.4.20 — OK

Confirmed via JetBrains Kotlin blog: "Kotlin 2.4.20 Released" (blog.jetbrains.com/kotlin/2026/09/kotlin-2-4-20-released/), published within the last few days as of 2026-09-17. This is the current stable release; Kotlin 2.5.0 is only planned for December 2026. No issue.

### 2. Jetpack Compose BOM 2026.08.00 — OK

Confirmed real via the official Android Developers blog post "What's new in the Jetpack Compose August '26 release" (developer.android.com/blog/posts/what-s-new-in-the-jetpack-compose-august-26-release), which explicitly references `implementation(platform("androidx.compose:compose-bom:2026.08.00"))` and Compose 1.12 (Mesh Gradients, WCG, Grid named areas, Credential Manager integration, compileSdk 37 / AGP 9.1.1 minimum). Not a hallucination, and it's the current BOM as of the review date. No issue.

### 3. Compose Compiler Gradle plugin 2.3.21 — STALE / VERSION MISMATCH (flag)

Since Kotlin 2.0, the Compose Compiler has been merged into the Kotlin repo and **the Compose Compiler Gradle plugin version tracks the Kotlin version 1:1** — plugin version X.Y.Z is compatible with (and intended to pair with) Kotlin X.Y.Z of the *same* version number (developer.android.com/develop/ui/compose/compiler; kotlinlang.org compose-compiler-migration-guide).

The spine pairs **Compose Compiler plugin 2.3.21** with **Kotlin 2.4.20**. These are numbers from two different Kotlin minor release lines (2.3.x vs 2.4.x) — this is an internally inconsistent pairing, not a valid current combination. This looks exactly like the kind of error stale training data produces: grabbing a plausible-looking compiler patch version that was current for an *older* Kotlin line and pairing it with a newer Kotlin version bump.

**Correction:** the Compose Compiler Gradle plugin version should match the Kotlin version being used, i.e. it should be pinned to **2.4.20** (or whatever exact 2.4.x patch is verified compatible/available at build time) — not 2.3.21. Recommend the spine either state "Compose Compiler Gradle plugin = same version as Kotlin (currently 2.4.20)" or re-verify the exact matching patch at implementation time rather than hardcoding a mismatched number.

### 4. Firebase Android BoM ~34.17.0 — LIKELY ALREADY ONE (OR TWO) BEHIND (flag, low severity)

Search results conflict slightly on the exact "latest" number as of 2026-09-17:
- Maven Central listing (mvnrepository.com) shows **34.16.0** dated July 9, 2026 as the latest indexed there.
- Other sources (Releasebot Firebase updates for September 2026; Firebase C++ SDK release notes) reference **34.17.0**, and one C++ release-notes hit references **34.18.0** (bundling firebase-firestore 26.6.0, firebase-messaging 25.1.2, etc.), suggesting a newer BoM shipped after the Maven mirror snapshot search returned.

Net: 34.17.0 is a real, recently-shipped version (not a hallucination), but there is credible evidence a newer BoM (34.18.0) may already be current by 2026-09-17. This is exactly the kind of fast-moving pinned number that goes stale within weeks.

**Mitigating factor:** the spine already writes "~34.17.0 (conferir a mais recente no momento do build)" — i.e., it does NOT commit hard to this number and explicitly defers to build-time verification. This is the correct posture given how quickly this BoM revs; no change required beyond keeping that hedge intact.

### 5. Cloud Functions for Firebase 2nd gen, Node.js 22 (GA) — accurate but incomplete, and terminology drifting (flag, medium severity)

- Node.js 22 is indeed GA for Cloud Functions/Cloud Run functions — this part is correct, not stale.
- However, **Node.js 24 has also reached General Availability** as a supported runtime (Google Cloud Functions release notes confirm GA status as of September 3, 2026 — i.e., before this spine's date). For a document being written today (2026-09-17) recommending a runtime for a new MVP, Node.js 22 is a defensible but no-longer-newest choice; Node.js 24 is the newer GA option and would give a longer support runway. The spine should at least note that Node 24 GA exists and record *why* 22 was chosen (if intentional, e.g. tooling/library compatibility) rather than silently reading as "22 is the current GA" (implying it's the newest), which slightly overstates its currency.
- Separate naming/branding flag: Google Cloud's underlying product has been renamed — **"Cloud Functions (2nd gen)" is now branded "Cloud Run functions"** upstream in Google Cloud docs (docs.cloud.google.com/functions/docs/release-notes, updated as of September 3, 2026: "Cloud Run functions (formerly known as Cloud Functions)"). Firebase-facing docs still use "Cloud Functions for Firebase" as the product name (firebase.google.com/docs/functions), so the spine's terminology is not wrong today, but it is mid-rename upstream — worth a one-line awareness note since a reader building against raw GCP docs (vs. Firebase docs) will see the new "Cloud Run functions" name and may be confused it's the same thing.

**Recommendation:** Add a short note: "Node.js 22 chosen for GA stability; Node.js 24 also reached GA in 2026 and is an acceptable alternative — revisit at build time. Note: GCP has begun rebranding 'Cloud Functions (2nd gen)' as 'Cloud Run functions' upstream; Firebase docs still call it 'Cloud Functions for Firebase.'"

### 6. Firebase Cloud Firestore / Firebase Authentication / Firebase Cloud Messaging — OK, no deprecation/rename found

- **Cloud Firestore**: no replacement or deprecation found; transactions API referenced in AD-1 remains the current mechanism for atomic server-side writes. OK.
- **Firebase Authentication**: email/password auth remains a current, non-deprecated provider. OK.
- **Firebase Cloud Messaging (FCM)**: product is not deprecated. Note found: FCM's server-side Admin SDKs (Node.js, Java, Python) deprecated the `token` field on the Send API in favor of `fid` (Firebase Installation ID) around June–July 2026 — this is an internal API/field-level change, not a product rename or deprecation, and doesn't affect the architecture-level decision to use FCM for push. Worth a minor implementation-time note for whoever writes the `notifications` Cloud Function (use current Admin SDK version so it targets `fid`/current send semantics), but not a spine-level correction.

---

## Overall assessment

Most of the pinned versions in the Stack table check out as real, current, non-hallucinated values — this table reflects genuine research, not just plausible-sounding stale defaults. The one clear, well-evidenced defect is **Finding #3** (Compose Compiler Gradle plugin 2.3.21 mismatched against Kotlin 2.4.20) — this is an internally inconsistent pairing and should be corrected before this spine is treated as final. Findings #4 and #5 are lower-severity currency notes rather than errors: the Firebase BoM already carries an appropriate "verify at build" hedge, and the Node.js runtime choice is valid but not the newest GA option, with the underlying product name also mid-rename upstream.
