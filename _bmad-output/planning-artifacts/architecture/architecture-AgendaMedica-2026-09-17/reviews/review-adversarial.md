---
name: 'Adversarial Review — ARCHITECTURE-SPINE'
type: review
subject: Agenda Médica — Architecture Spine
reviewer: adversarial-stress-test
created: '2026-09-17'
---

# Adversarial Review — ARCHITECTURE-SPINE (Agenda Médica)

Method: for each AD (and the Consistency Conventions table / Capability Map), a scenario is constructed
where two units — built independently, each reading only the spine, each honoring every AD to the letter —
produce incompatible artifacts (clashing shapes, dual ownership, an unclosed race, or an unspecified
security-rule boundary). Each finding names the escaping AD(s), the two divergent implementations, and a
tightened Rule/new AD that would close the gap.

---

## Finding 1 — Doc-ID convention contradicts the ownership pattern AD-1/AD-6 need

**Escapes:** Consistency Conventions table ("IDs: auto-gerados pelo Firestore") vs AD-1, AD-6.

The Conventions table states a blanket rule: *"IDs: auto-gerados pelo Firestore."* But AD-6's role-claim
model and AD-1's security-rule boundary both implicitly require `patients/{uid}` and `doctors/{uid}` to be
keyed by the Firebase Auth UID — that's the only way a security rule can cheaply express "a user may only
read/write their own profile" (`request.auth.uid == docId`) without a query-based lookup.

- **Implementation A** (auth/signup contributor): writes `doctors/{uid}` and `patients/{uid}` using
  `.doc(uid).set(...)`, the idiomatic Firebase pattern, because it's the only way to make AD-6's claim-based
  identity line up with a profile document cheaply.
- **Implementation B** (a contributor who takes the Conventions table literally): creates doctor/patient
  documents with `.add()` (Firestore auto-ID) and stores `authUid` as a plain field, exactly as the table
  says IDs must be auto-generated.

Both are spine-compliant. But now every other feature that references `doctors/{id}` (slot computation,
booking, search) must guess which convention is in effect — A's rules (`docId == auth.uid`) silently fail
to authorize B's documents, and vice versa. Any `appointments.doctorId`/`patientId` foreign key written by
one convention is meaningless to code written against the other.

**Suggested fix:** Add a Rule (or AD-10) stating explicitly: *"`patients/{id}` and `doctors/{id}` are keyed
by Firebase Auth UID (`doc(uid)`), never Firestore auto-ID. Auto-ID applies only to `appointments`."*

---

## Finding 2 — No read-authorization rule for `appointments`; AD-4's "local computation" needs collection-wide read, AD-3's "minhas consultas" needs owner-only read

**Escapes:** AD-1 (silent on reads), AD-4, Capability Map row "Horários disponíveis (RF-5) | app/domain
(cálculo local) | AD-4."

AD-1 only states what the client may **not** write. Read access to `appointments` is asserted informally
("Leitura ... é direta do app ao Firestore, mediada por security rules") but no rule specifies **which**
appointments a given authenticated user may read.

- **Implementation A** ("cancel appointment" / "Minhas Consultas" contributor) writes the narrowest correct
  security rule: `allow read: if request.auth.uid == resource.data.patientId`. This satisfies AD-3's
  "leitura de minhas consultas ativas."
- **Implementation B** ("search doctors" / available-slots contributor), following the Capability Map's
  literal instruction that slot computation is client-side (`app/domain`, "cálculo local" per AD-4), needs
  to read **every** `appointments` doc for a given doctor/day, from **any** authenticated patient, to
  subtract busy times — which A's rule denies outright.

If A's rule ships, B's feature is broken by a security-rule 403, not a bug in B's code. If B's broader rule
ships instead (`allow read: if request.auth != null` on the whole collection), every patient can now read
every other patient's full appointment document — leaking `patientId`, `insurance`, and any PII field on an
appointment to any logged-in user, silently violating no explicit AD but clearly out of spirit with AD-6's
identity model.

**Suggested fix:** AD-4 should mandate that slot availability is computed **server-side** (a callable
`getAvailableSlots`, same trust boundary as AD-1's booking Functions) returning only `{startTime, available:
boolean}` — never raw `appointments` documents to the client. Add explicit read rules to AD-1/AD-3: clients
may read an `appointments` doc only where `request.auth.uid` matches its `patientId` or `doctorId` field —
and no rule may grant broader read on the collection.

---

## Finding 3 — Structural Seed's own ER diagram omits the foreign keys the other findings depend on

**Escapes:** Structural Seed (erDiagram), AD-1, AD-3.

The `APPOINTMENT` entity in the seed's erDiagram lists only `status, startTime, insurance,
reminderSentAt` — no `patientId`/`doctorId` field, despite the same diagram drawing
`DOCTOR ||--o{ APPOINTMENT` and `PATIENT ||--o{ APPOINTMENT` relationships that require exactly such fields
to exist. Two contributors implementing booking vs. cancellation independently have no pinned name to agree
on: one may write `patientId`/`doctorId`, another `patientUid`/`doctorUid` (AD-7 fixes *language*, English
vs Portuguese — it does not fix `Id` vs `Uid` suffix conventions). Every security rule in Finding 2 and
every "who owns this cancellation" check in AD-1 depends on a field name the spine never actually commits
to paper.

**Suggested fix:** Add the foreign-key fields to the `APPOINTMENT` entity in the seed with exact names
(`patientId`, `doctorId`, both = Auth UID per Finding 1), plus `createdAt`/`cancelledAt`/`cancelledBy` if
RF-8/RF-9 allow doctor-initiated cancellation — otherwise two cancel-flow implementers will invent different
audit fields.

---

## Finding 4 — AD-1's boundary is airtight for `appointments` writes, but silent on `doctors/{id}.schedule`, which is the input AD-4's transaction trusts

**Escapes:** AD-1 (scoped only to "Consulta"), AD-4 (trusts `doctors/{id}.schedule` as ground truth).

AD-1's rule text is explicit: *"o app Android nunca escreve no documento de uma Consulta"* — this closes
the "cancelConfirming UI flag leaking into the schema" risk raised in the review brief; AD-1 as written
would block *any* client field write to an `appointments` doc, ephemeral-looking or not, which is good and
airtight for that document.

But `doctors/{id}.schedule` is a *different* document, and it directly determines what AD-4 computes as
"available." AD-1 says nothing about it, and no other AD assigns ownership.

- **Implementation A** (doctor profile/schedule-edit contributor): treats `doctors/{id}` as an ordinary
  self-owned profile doc (matching AD-2's "Repository mediates access," not "Function mediates mutation")
  and writes `schedule` directly from the client via Firestore, gated only by
  `allow update: if request.auth.uid == doctorId`.
- **Implementation B** (`bookAppointment` Function author): writes the atomic transaction assuming
  `doctors/{id}.schedule` is a stable, well-formed structure it can trust inside a transaction the same way
  it trusts `appointments` — because AD-1's "server-authoritative" framing primes the reader to think all
  state that affects booking correctness goes through a Function.

Nothing forces A's client-side writer to validate the map's shape (Firestore rules can't easily assert map
structure), so a malformed `schedule` write — mid-edit, a typo'd weekday key, an unset field — can silently
corrupt B's slot computation or crash the transaction, and no AD assigns blame or a validation gate for that
path.

**Suggested fix:** Either (a) extend AD-1's rule to cover `doctors/{id}.schedule` (schedule edits also go
through a callable Function, validated server-side), or (b) add a new AD explicitly stating schedule is
client-writable but must conform to a documented schema, validated defensively inside `bookAppointment`'s
transaction (reject/self-heal on malformed schedule rather than trusting it).

---

## Finding 5 — AD-4 never states a timezone for interpreting `schedule`, and AD-8 only fixes storage type, not interpretation

**Escapes:** AD-4, AD-8.

AD-8 mandates `Timestamp` (an absolute instant) for `startTime`, which fixes ordering/comparison — but says
nothing about how the *wall-clock* `doctors/{id}.schedule` (necessarily some local-time representation,
e.g. `"09:00"–"18:00"`) is converted into an absolute `Timestamp` for a specific calendar date. No field in
the ER diagram carries a timezone, and Brazil is not IANA-uniform (has offset transitions historically).

- **Implementation A** (client-side slot calculator, `app/domain` per the Capability Map) converts using the
  **device's** local timezone when building the grid the patient sees.
- **Implementation B** (`bookAppointment` Cloud Function, 2nd-gen Node.js runtime — which defaults to UTC
  unless a `timeZone` is explicitly configured on the scheduler/runtime) re-derives the same schedule window
  server-side to validate the requested slot inside its transaction, using UTC.

A 3-hour (America/São_Paulo) offset mismatch between A and B means slots the client shows as "available"
get rejected by the Function with `failed-precondition`, or worse, slots near midnight get shown/booked on
the wrong calendar day — and both sides are individually AD-4/AD-8-compliant, since neither Rule mentions
timezone.

**Suggested fix:** Add to AD-4: *"`schedule` times are interpreted in a fixed, hardcoded timezone
(`America/Sao_Paulo`), identical in the app's slot calculator and in every Cloud Function — never the
device's or the server runtime's default timezone."*

---

## Finding 6 — AD-4's rule doesn't incorporate RF-6's minimum lead time; "available" has two legitimate readings

**Escapes:** AD-4 (Binds FR-5, FR-6, but the Rule text only mentions "schedule minus confirmed").

AD-4's binds list includes FR-6, but its Rule text defines "horários disponíveis" purely as `schedule
menos appointments confirmed`. It never says whether the minimum-booking-lead-time constraint (referenced
only via AD-8's mention of "antecedência mínima (RF-6)") is applied **at grid-generation time** or only
**at booking-attempt time**.

- **Implementation A** filters lead-time-violating slots out of the grid itself, so "available" already
  means "confirmable now."
- **Implementation B** shows the full schedule-minus-confirmed grid regardless of lead time, relying on
  `bookAppointment` to reject with `failed-precondition` (per AD-9) when the client submits a
  too-soon slot.

Both satisfy AD-4's literal text. But then a "search doctors" screen showing "próximo horário disponível"
(built against A's semantics) and the booking screen (built against B's semantics) disagree on what counts
as available for the exact same doctor/day — one will advertise a slot the other's Function will bounce.

**Suggested fix:** Fold RF-6 explicitly into AD-4's Rule: *"the computed grid excludes any slot violating
the RF-6 minimum lead time; `bookAppointment`'s lead-time check is a defense-in-depth guard against races, not
the primary filter."*

---

## Finding 7 — RF-6/RF-8/RF-9 boundary operators (inclusive vs. exclusive) are unspecified despite AD-8's "always compare Timestamp"

**Escapes:** AD-8.

AD-8 fixes the *data type* used for the 24h/48h comparisons ("Timestamp, nunca string") but not the
**operator** at the exact threshold instant. Is a cancellation attempted at exactly `startTime - 48h`
allowed (`<=`) or refused (`<`)? AD-8's text is silent.

- **Implementation A** (`cancelAppointment`) uses `now <= startTime - 48h` (inclusive).
- **Implementation B** (`rescheduleAppointment`), built independently against the same AD-8 text, uses
  `now < startTime - 48h` (exclusive).

A patient hitting the boundary at the exact second gets asymmetric behavior — cancel succeeds, reschedule
of the same appointment at the same instant fails (or vice versa) — with no AD to say which is "correct,"
and both Functions individually pass an AD-9 review since `failed-precondition` is thrown either way.

**Suggested fix:** State the operator explicitly in AD-8 (or add it as a Rule bullet): *"all lead-time and
cancellation-window checks use `now < threshold` (strictly before); the boundary instant itself is treated
as already inside the window,"* and require both Functions to call one shared helper rather than
reimplementing the comparison.

---

## Finding 8 — AD-5's polling interval and idempotency write are underspecified, permitting both duplicate sends and missed reminders

**Escapes:** AD-5.

AD-5 says the scheduled function runs "a cada poucos minutos" and is "idempotente" because
"cada Consulta guarda `reminderSentAt`." Neither the interval nor the query/write semantics are pinned.

- **Implementation A**: polls every 15 min with `where('startTime','<=', now+24h) AND
  where('reminderSentAt','==', null)`, sends, then does a plain (non-transactional) `update({reminderSentAt:
  now})`. Two overlapping invocations (a slow prior run plus Cloud Scheduler's at-least-once retry
  semantics) can both read `reminderSentAt == null` before either writes it, producing a **duplicate**
  push+email to the same patient.
- **Implementation B**: polls every 5 min with a **tight window**
  `where('startTime','>=', now+24h) AND where('startTime','<', now+24h+5min)` to avoid re-scanning
  already-handled rows. If one invocation is skipped (cold start, deploy gap, function timeout), any
  appointment whose 24h-threshold instant fell inside the missed 5-minute window is **never** reminded,
  because B's query design has no catch-up mechanism.

Both are literally "a scheduled function, running every few minutes, guarded by `reminderSentAt`" — AD-5's
letter — yet one over-sends and the other silently drops reminders on infra hiccups that will happen in
practice (Cloud Scheduler/Functions cold starts are routine, not exotic).

**Suggested fix:** Pin the semantics: *"query is cumulative (`startTime <= now + 24h AND reminderSentAt ==
null`), never a tight forward-only window, so a missed cycle self-heals on the next run; the write that sets
`reminderSentAt` must be a transaction/conditional update guarded by `reminderSentAt == null` at write time
(not just read time), and the poll interval is fixed at N minutes for both design and load-testing
purposes."*

---

## Finding 9 — AD-6 says nothing about ID-token claim propagation latency; a freshly-registered doctor is race-prone against the very Functions/rules AD-6 exists to protect

**Escapes:** AD-6.

AD-6's Rule states the role custom claim is set "no momento do cadastro" by the signup Function, and every
Function/rule reads it "do token verificado." What it omits: Firebase ID tokens **cache** custom claims
client-side, and a claim set by a Function does not appear in the client's current token until that token is
force-refreshed (`getIdToken(true)`) or naturally rotates (up to ~1h later).

- **Implementation A** (registration-flow contributor) knows this Firebase quirk and, immediately after
  `onCreateAccount` succeeds, calls `firebaseAuth.currentUser.getIdToken(true)` before navigating to any
  doctor-only screen or Function call.
- **Implementation B** (a different contributor, e.g. building the doctor-onboarding "set up your
  schedule" screen straight after signup, reading AD-6 alone) sees nothing in AD-6 telling them to force a
  refresh, and navigates immediately — the very next Function call or security-rule-gated Firestore write
  (e.g. writing the doctor's own `schedule`, per Finding 4) is evaluated against the pre-registration token,
  which has no `role` claim yet, and is rejected with `permission-denied` even though the account **is**
  correctly a doctor server-side.

Both A and B "read the custom claim from the verified token, never trust client input" per AD-6's letter;
only one of the two flows actually works, and the failure looks like an intermittent race/bug rather than a
documented architectural gap.

**Suggested fix:** Add an explicit Rule to AD-6: *"any client flow that calls the registration Function must
force-refresh the ID token (`getIdToken(true)`) before the first role-gated call, and must not navigate to a
role-gated screen until that refresh resolves."*

---

## Finding 10 — `status` enum values are not pinned as a shared constant, unlike Specialty/Insurance

**Escapes:** AD-3, AD-7, Consistency Conventions ("Sem coleção de configuração dinâmica... Especialidades/
Convênios são uma constante versionada no código de ambos").

The Conventions table explicitly requires Specialty and Insurance enum values to be a single versioned
constant shared between the Kotlin app and the Node Functions, precisely to prevent divergence. No
equivalent requirement is stated for the `status` field values (`"confirmed"`, `"cancelled"`) that AD-3 and
AD-4 both filter on.

- **Implementation A** (Kotlin/Android reader, matching the spine's own prose) filters on `status ==
  "cancelled"` (British spelling, as literally written in AD-3's Rule).
- **Implementation B** (Node.js Cloud Function writer, following common JS/Node ecosystem convention —
  `Function.prototype` methods, AWS/GCP SDKs, and Node style guides overwhelmingly use American spelling)
  writes `status: "canceled"`.

Nothing in AD-3, AD-7, or the Conventions table forces these two independently-written strings to be pulled
from one shared constant, unlike Specialty/Insurance. If they diverge, AD-3's and AD-4's status filters
silently return empty/wrong results — no error is thrown anywhere (Firestore `where` on a mismatched string
just returns no matches), so this fails silently rather than loudly, and is far more likely to actually
happen than the Specialty typo the spine already defends against.

**Suggested fix:** Extend the existing Consistency Conventions bullet: *"status enum values
(`confirmed`/`cancelled`, and any future value) are a single versioned constant shared by app and Functions,
exactly as Specialty/Insurance are — not re-typed as literals in either codebase."*

---

## Summary Table

| # | AD(s) escaped | Core clash |
| --- | --- | --- |
| 1 | Conventions table vs AD-1/AD-6 | Auto-ID vs UID-keyed docs — breaks ownership security rules |
| 2 | AD-1 (silent), AD-4 | No read-scope rule: owner-only read vs. collection-wide read needed for slot calc |
| 3 | Structural Seed | ER diagram omits `patientId`/`doctorId` that every other finding depends on |
| 4 | AD-1 scope, AD-4 | `doctors.schedule` writes ungoverned — client-writable but transaction-trusted |
| 5 | AD-4, AD-8 | No fixed timezone for interpreting `schedule` — client vs. server offset mismatch |
| 6 | AD-4 | "Available" grid may or may not pre-filter RF-6 lead time |
| 7 | AD-8 | Inclusive vs exclusive boundary operator at 24h/48h unspecified |
| 8 | AD-5 | Poll interval/query/write semantics unpinned — duplicate sends vs missed reminders |
| 9 | AD-6 | ID-token claim caching not addressed — stale-token permission-denied race post-signup |
| 10 | AD-3, AD-7, Conventions | `status` string literals not pinned as shared constant like Specialty/Insurance |
