# Architecture Spine Review — Agenda Médica (Rubric Gate)

**Spine reviewed:** `_bmad-output/planning-artifacts/architecture/architecture-AgendaMedica-2026-09-17/ARCHITECTURE-SPINE.md`
**Reviewer date:** 2026-09-17

## Overall Verdict

The spine is well-constructed for its stated purpose — its nine ADs correctly nail the project's one hard problem (booking concurrency, server-authoritative writes, status-not-delete, computed slots) with enforceable, testable rules, all eleven FRs map somewhere, and the diagrams are valid and match the prose — but it has two real structural gaps that a "fixes all divergence points" spine should not leave silent (Firestore read/privacy security rules, and the doctor-profile/convênio-immutability mutation path), one internal self-contradiction (email provider status), and a verifiable technical error in the Stack table (Compose Compiler plugin version mismatched to the stated Kotlin version); none of these are fatal to the document but each should be closed before the spine is treated as final.

**Findings by severity:** High: 2 | Medium: 4 | Low: 3 | Total: 9

---

## Findings

### 1. [HIGH] No Firestore read/privacy security rules are specified — AD-4's slot computation is a plausible privacy leak
- **Location:** Design Paradigm (line 26, "Leitura ... é direta do app ao Firestore, mediada por security rules"); AD-1 (only denies client `create/update/delete`, says nothing about reads); AD-4 (computed slots).
- **What's wrong:** The PRD's "RNFs Transversais → Proteção de dados" is explicit: appointment/patient data may only be read by the owning Patient and the Doctor of that appointment. The spine never specifies *any* Firestore Security Rule for reads. This matters concretely because AD-4 requires the client to read `appointments` filtered by `status: "confirmed"` for a given doctor/day *directly from Firestore* (not through a Function) to compute the availability grid. Firestore has no field-level read redaction — a client reading those documents to find "which 15-min blocks are taken" necessarily has read access to the full appointment document, including `patientId`/`insurance`, for every patient browsing that doctor's schedule, unless a separate mechanism is used. Nothing in the spine says how this is avoided (e.g., a projection collection/document exposing only booleans, or a callable Function `getAvailability` instead of a direct client query).
- **Suggested fix:** Add an AD (e.g., "AD-10 — Leitura de disponibilidade nunca expõe identidade do paciente") that either (a) requires availability queries to go through a callable Function that strips sensitive fields, or (b) defines a denormalized `availability`/`busySlots` projection document per doctor/day containing only occupied-slot markers, with Security Rules that deny direct client reads of `appointments` documents belonging to other users.

### 2. [HIGH] No AD governs the doctor-profile mutation path or enforces convênio immutability
- **Location:** Capability → Architecture Map, row "Autocadastro e perfil de Médico (RF-1, RF-2)" → governed by AD-6, AD-7 only; AD-1 is explicitly scoped to Consulta writes only ("agendar, cancelar, reagendar").
- **What's wrong:** RF-2 states a hard, testable business rule: once a Convênio is selected and saved, the Médico can never edit or remove it via the interface, even with confirmed Consultas under it. The spine has no AD analogous to AD-1 for this: it never says whether doctor-profile writes (schedule edits, which are allowed at any time per RF-2, vs. convênio edits, which must be permanently blocked) go through a Cloud Function or are direct client writes to `doctors/{id}` mediated by Security Rules. Without this, a future implementer could allow a direct client write to `doctors/{id}` guarded only by a client-side UI check — trivially bypassable via the Firestore SDK/console — silently violating a testable PRD requirement. This is exactly the kind of "two independently built units diverging" scenario the spine is supposed to foreclose.
- **Suggested fix:** Add an AD stating the mutation path for `doctors/{id}` (direct write + Security Rule that diff-checks `insurances` is unchanged after first write, or a callable `updateDoctorProfile` Function that rejects convênio changes server-side).

### 3. [MEDIUM] Internal contradiction: AD-5 prose vs. diagrams on e-mail provider status
- **Location:** AD-5 rule (line 58: "dispara push (sempre) + e-mail (sempre) + SMS (best-effort, provedor em aberto...)"); "Direção de dependência" diagram (line 95: `FN -.->|best-effort, provedor em aberto| EMAIL`); Structural Seed diagram (line 135: `EMAIL[[Provedor de e-mail — Deferred]]`); Deferred section (only lists "Provedor de SMS" as open).
- **What's wrong:** AD-5's rule treats e-mail as a settled, unconditional channel ("sempre"), but both mermaid diagrams label the e-mail edge/node identically to SMS — "best-effort, provedor em aberto" / "— Deferred". No e-mail provider is actually named anywhere in the spine (no SendGrid, no Firebase "Trigger Email" extension, nothing), so the diagrams are the more accurate signal: e-mail provider selection is just as undecided as SMS, but the Deferred list omits it, and the AD text overstates its certainty.
- **Suggested fix:** Either name a concrete e-mail provider and remove "Deferred"/"provedor em aberto" from the diagrams, or add "Provedor de e-mail transacional" to the Deferred list next to SMS and soften AD-5's "sempre" wording to match.

### 4. [MEDIUM] Real-time grid update mechanism (explicitly flagged in addendum.md for Architecture) is silently absent from the spine
- **Location:** Whole document — no AD, no Deferred entry, no Open Question addresses this.
- **What's wrong:** `addendum.md` explicitly lists "Atualização em tempo real da grade de horários" as an open item with status "aberto — decidir na etapa de Arquitetura", and the UX EXPERIENCE.md Flow 1 requires the grid to reflect a lost race "sem recarregar a tela manualmente." The spine never resolves this — AD-4 says *what* the available grid is (computed from schedule minus confirmed appointments) but not *how* the client learns a slot just became occupied (Firestore realtime `addSnapshotListener` vs. manual pull/poll vs. re-fetch-on-error-only). This is precisely a divergence point the spine was supposed to fix per its own sources list, and it isn't decided, deferred, or flagged — it's just missing.
- **Suggested fix:** Add a short AD (e.g., "grade de horários usa Firestore snapshot listener em tempo real, nunca polling") or, if intentionally left open, move it explicitly into the Deferred section with a one-line rationale, so it isn't silently dropped.

### 5. [MEDIUM] Stack table: Compose Compiler Gradle plugin version does not match the stated Kotlin version
- **Location:** Stack table, lines 112–114 (`Kotlin 2.4.20`, `Compose Compiler Gradle plugin 2.3.21`).
- **What's wrong:** Since Kotlin 2.0, the Compose Compiler moved into the Kotlin repo and the `org.jetbrains.kotlin.plugin.compose` Gradle plugin version is required to track the Kotlin version exactly (version.ref = kotlin in the version catalog). Pairing Kotlin `2.4.20` with Compose Compiler plugin `2.3.21` is an inconsistent, almost certainly wrong pairing — confirmed via Kotlin's own documentation ("the version of the Compose compiler now always matches the Kotlin version") and would likely fail to resolve or produce a Kotlin/Compose compiler-version mismatch error at build time. This is one of the two load-bearing version claims flagged for spot-checking.
- **Suggested fix:** Set the Compose Compiler Gradle plugin version to `2.4.20` (matching Kotlin), not `2.3.21`.

### 6. [MEDIUM] Cloud Functions Node.js 22 "(GA)" claim is contested and stated with more confidence than the evidence supports
- **Location:** Stack table, line 116 ("Cloud Functions for Firebase | 2ª geração, runtime Node.js 22 (GA)").
- **What's wrong:** Spot-checking this on the web produced conflicting signals: some sources describe Node.js 22 for Cloud Functions/Cloud Run functions 2nd gen as GA, others (as recently as the review date) describe it as still at Preview release level for 2nd gen specifically (1st gen reached GA for Node 22 around Oct 2025, which is a separate runtime line). The spine already hedges the Firebase BoM version with "conferir a mais recente no momento do build" but states the Node 22/GA claim unconditionally, which risks a false sense of certainty on a load-bearing runtime choice.
- **Suggested fix:** Apply the same hedge used for the Firebase BoM line: "Node.js 22 — confirmar status GA/Preview no console no momento do deploy; usar Node.js 20 (LTS, GA confirmado) como fallback caso 22 ainda esteja em Preview."

### 7. [LOW] RF-11 password-reset link expiry (an explicit PRD open assumption) is not closed, even implicitly
- **Location:** Capability → Architecture Map, row "Recuperação de senha (RF-11)" → "Firebase Authentication (nativo) — sem Cloud Function própria"; Deferred section (no mention).
- **What's wrong:** PRD §9 lists the reset-link expiry window as an assumption explicitly deferred to Architecture ("prazo exato de expiração a definir na Arquitetura, ex.: 30-60 minutos"). The spine delegates RF-11 entirely to "native Firebase Auth" but never states the expiry value or even notes that it inherits Firebase's configurable default — leaving the PRD's open question technically still open, just unlabeled.
- **Suggested fix:** Add one line (in the capability map row or Deferred) noting the reset flow uses Firebase Auth's built-in action-code expiry (default ~1h, configurable in the Firebase console/action code settings), closing the PRD's assumption explicitly.

### 8. [LOW] Event-driven push notifications (new booking, cancellation) have no home in any AD or in the functions/ tree
- **Location:** AD-5 (covers only the scheduled 24h reminder); Árvore mínima `functions/src/notifications/` (only lists the scheduled function); Capability map row "Notificações (RF-10)" (only RF-10, not the notification consequences embedded in RF-6/RF-8).
- **What's wrong:** RF-6 requires a push to the doctor on new booking, and RF-8 requires notifying "the other party" on cancellation/reschedule. These are architecturally relevant (do they fire synchronously inside `bookAppointment`/`cancelAppointment`'s transaction, or via a separate Firestore `onWrite` trigger?) but are not addressed by any AD or reflected in the source tree, unlike the 24h reminder which has its own AD and folder.
- **Suggested fix:** Add a sentence to AD-1 or the capability map stating these notifications are sent synchronously from within the same callable Function after the transaction commits (for consistency with the "single write gate" philosophy of AD-1).

### 9. [LOW] AD-5's scheduling cadence ("a cada poucos minutos") is vague enough to be unfalsifiable
- **Location:** AD-5 rule, line 58.
- **What's wrong:** "A few minutes" is not a concrete, testable cadence. For a solo/single-deployment project the divergence risk is minimal, but as written the rule can't be checked against an implementation (5 minutes and 20 minutes both satisfy "a cada poucos minutos", yet produce different reminder-timing precision).
- **Suggested fix:** Pick and state a concrete interval (e.g., "a cada 5 minutos") so the rule is directly verifiable.

---

## Checklist Coverage Summary

1. **Divergence points fixed / missed:** Core concurrency and write-gating divergences are well covered (AD-1 through AD-9). Missed: read/privacy security rules (#1), doctor-profile/convênio mutation path (#2), real-time grid update mechanism (#4).
2. **AD Rule enforceability:** 8 of 9 ADs are concretely enforceable and testable; AD-5's cadence is the one soft spot (#9).
3. **Deferred risk of divergence:** The existing Deferred items (SMS provider, single env, no CI/CD, no Play Store, test framework choice, visual metadata) are all safe to defer. The risk is what's *not* in Deferred at all — items #1, #2, #4, and the e-mail provider (#3) should be either decided or explicitly deferred, not silently absent.
4. **Tech currency:** Kotlin 2.4.20 and Compose BOM 2026.08.00 verified current via web search. Firebase Android BoM ~34.17.0 is in the right neighborhood (sources show 34.11.0–34.18.0 across 2026), and the spine's own "confirmar no build" hedge is good practice. Two problems found on spot-check: Compose Compiler plugin version mismatch (#5) and unresolved GA/Preview ambiguity for Cloud Functions Node.js 22 (#6).
5. **PRD capability coverage:** All of FR-1 through FR-11 map to a location and governing AD/rule in the Capability → Architecture Map. No FR is orphaned.
6. **Structural dimensions decided/deferred/flagged:** Deployment/environments/CI-CD are explicitly and appropriately addressed in Deferred. Security rules for reads and the profile-mutation path are the two structural dimensions this altitude owns that are silently missing rather than decided or deferred (see #1, #2).
7. **Diagram validity/fidelity:** Both the dependency graph and structural-seed graph are syntactically valid Mermaid, non-empty, and consistent with the prose narrative of layering and write-gating — except for the e-mail-provider labeling contradiction noted in #3. The ER diagram is valid and minimal but adequate for this altitude.
