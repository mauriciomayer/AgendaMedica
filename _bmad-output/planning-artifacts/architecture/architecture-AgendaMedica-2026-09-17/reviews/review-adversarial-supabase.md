# Adversarial Review — ARCHITECTURE-SPINE.md (Supabase rewrite)

Reviewer stance: adversary. For each finding below, assume two contributors each read only the
spine, each honor every AD they touch to the letter, and still ship something incompatible,
insecure, or silently broken when composed. Findings are ordered roughly by severity
(security/data-integrity first, then correctness/availability).

---

## Finding 1 — AD-1: SECURITY DEFINER functions never mandated to derive identity from `auth.uid()`, enabling IDOR across the whole RPC surface

**Scenario:** AD-1's Rule states RLS denies direct `INSERT`/`UPDATE`/`DELETE` on `appointments` and
that the *only* path is `book_appointment` / `cancel_appointment` / `reschedule_appointment`, all
`SECURITY DEFINER`. It never specifies the function *signatures* or states that `patient_id`
(or `doctor_id`) must be derived from `auth.uid()` inside the function body rather than accepted
as a caller-supplied parameter.

`SECURITY DEFINER` functions execute with the *owner's* privileges and therefore bypass RLS on
every table they touch internally — that is the whole point of routing writes through them (AD-1
says so explicitly: "a função... nunca por escrita direta de tabela"). But that also means the
`patient_id = auth.uid()` check that RLS *would* have enforced on a direct INSERT is not enforced
anywhere unless the function re-implements it.

Two contributors, both fully compliant with AD-1's literal text:
- Dev A implements `book_appointment(p_doctor_id uuid, p_patient_id uuid, p_start_time timestamptz)`
  and inserts `(doctor_id, patient_id, start_time)` verbatim — a natural, even idiomatic, RPC
  signature (the client already has both ids in the booking screen's state).
- Dev B implements `cancel_appointment(p_appointment_id uuid)` and correctly adds
  `WHERE patient_id = auth.uid() OR doctor_id = auth.uid()` before the UPDATE.

Dev A's version lets any authenticated Patient book (or cancel/reschedule, if the same pattern is
repeated) an appointment *on behalf of any other patient_id*, or a Patient could pass a
`doctor_id`/`patient_id` pair that makes an appointment appear to belong to someone else entirely
— a straightforward IDOR that RLS would have blocked on a direct write, and that AD-9's error
vocabulary gives no code for ("FORBIDDEN" is only mentioned for role-mismatch, not for
identity-spoofing on the acting party). Nothing in AD-1, AD-9, or the Capability→Architecture Map
tells Dev A this is wrong; both devs believe they satisfied "escrita via função Postgres."

**Suggested tightened Rule (AD-1):** Every `SECURITY DEFINER` function that mutates `appointments`
must derive the acting patient/doctor id exclusively from `auth.uid()` inside the function body
— never accept it as an IN parameter for authorization purposes — and must include an explicit
ownership predicate (`WHERE patient_id = auth.uid()` for patient-initiated actions,
`doctor_id = auth.uid()` for doctor-initiated ones) on every UPDATE/SELECT it performs, raising
`FORBIDDEN: not the owner of this appointment` (0 rows affected) otherwise. State the required
function signatures explicitly (e.g. `book_appointment(p_doctor_id uuid, p_start_time timestamptz)`
with no `p_patient_id` parameter at all) so this isn't left to convention.

---

## Finding 2 — AD-1/AD-6: no `SET search_path` pinning on `SECURITY DEFINER` functions (search_path hijacking)

**Scenario:** `book_appointment`, `cancel_appointment`, `reschedule_appointment`, `is_doctor`,
`is_patient` are all specified as `SECURITY DEFINER`. Postgres/Supabase's own linter flags any
`SECURITY DEFINER` function without an explicit, fixed `search_path` as a security defect
("Function Search Path Mutable"): such a function resolves unqualified identifiers (types,
operators, other function calls) against the *caller's* session `search_path`, not a fixed one.
An authenticated user can create objects in a schema that appears earlier in their own
search_path (e.g. their own `"$user"` schema, or `public` if it's writable) that shadow names the
definer-privileged function calls internally, causing the function to execute attacker-controlled
code with the *definer's* elevated privileges — the canonical Postgres privilege-escalation vector
for `SECURITY DEFINER`.

The spine never states that these functions must pin `search_path` (e.g.
`SET search_path = pg_catalog, public` or `= ''`). Two contributors adding a *sixth* SECURITY
DEFINER function later (AD-9 explicitly anticipates this: "qualquer função SECURITY DEFINER
futura") have no rule telling them to pin it — one might copy an existing function that happens to
have it, another might not, and there is no automated gate (AD's "Deferred" section says CI/CD is
out of scope beyond the reminder workflow, so `supabase db lint` isn't wired in either).

**Suggested tightened Rule:** Add to AD-1 (or a new cross-cutting rule referenced by AD-1/AD-6/AD-9):
"Every `SECURITY DEFINER` function must declare `SET search_path = pg_catalog, public` (or an
equivalent fixed, minimal path) in its definition. This is non-negotiable and should be checked by
`supabase db lint` (or an equivalent grep over migrations) before merge."

---

## Finding 3 — AD-6: `is_doctor`/`is_patient` living in a non-`public` schema requires explicit `GRANT USAGE`/schema-qualification that the spine never states, and unqualified calls will fail at policy-evaluation time

**Scenario:** AD-6 says the helper functions live "num schema não exposto pela API — nunca em
`public`." This is correct for keeping them off the PostgREST surface (PostgREST only exposes
schemas listed in its config), but that is orthogonal to whether an RLS policy — evaluated in the
querying session under the `authenticated` role, not as a superuser — can actually *call* the
function:

- A newly created non-`public` schema does **not** grant `USAGE` to `PUBLIC` by default (unlike
  `public` itself, which historically does). Without an explicit
  `GRANT USAGE ON SCHEMA <private_schema> TO authenticated;`, any RLS policy referencing that
  function will fail with `permission denied for schema <private_schema>` the moment a real
  authenticated user's query hits it — not at migration time, but at first `SELECT`/`INSERT` from
  the app.
- Even with schema USAGE granted, an **unqualified** call inside a policy body — e.g.
  `USING (is_doctor(auth.uid()))` — resolves against the evaluating role's `search_path`, which for
  PostgREST/`authenticated` sessions is typically `"$user", public`. A function living in a schema
  outside that path will simply not resolve (`function is_doctor(uuid) does not exist`) unless the
  policy schema-qualifies the call (`private.is_doctor(auth.uid())`) or the role's `search_path` is
  explicitly altered to include that schema.

Two contributors, both honoring AD-6's letter ("schema não exposto"): Dev A writes the
`doctor_schedules` policy as `USING (is_doctor(auth.uid()))` (copy-pasting a pattern that "worked"
in a local psql session run as the table owner, where the unqualified name resolves fine because
the owner's search_path differs from `authenticated`'s). Dev B writes the `appointments` policy
correctly as `USING (private.is_doctor(auth.uid()))`. Dev A's table becomes completely unreadable
for every real user in production — a hard outage of that capability — while Dev B's works. Nothing
in AD-6 tells either of them which form is required, and there's no note that `GRANT EXECUTE ON
FUNCTION ... TO authenticated` and `GRANT USAGE ON SCHEMA ... TO authenticated` are required
migration steps.

**Suggested tightened Rule (AD-6):** State explicitly: "The non-public schema housing `is_doctor`/
`is_patient` must be created with `GRANT USAGE ON SCHEMA <schema> TO authenticated;` and each
function with `GRANT EXECUTE ON FUNCTION <schema>.<fn> TO authenticated;`. Every RLS policy that
calls these functions must reference them schema-qualified (`<schema>.is_doctor(auth.uid())`),
never bare, regardless of any role `search_path` configuration."

---

## Finding 4 — AD-10: the trigger's `UPDATE OF status` column filter means a `reschedule_appointment` that only changes `start_time` never fires it, leaving `booked_slots` stale and silently reopening/permanently-closing slots

**Scenario:** This is a direct, demonstrable contradiction between AD-1's own data model and AD-10's
trigger definition, not a hypothetical edge case. Postgres column-level `UPDATE OF <col>` triggers
fire **only when the named column appears in the `UPDATE ... SET` target list of the statement** —
not merely "when any row value changes." `reschedule_appointment` (per AD-1's naming and the
Capability→Architecture Map row "Cancelamento/Reagendamento") is described as changing an
appointment's `start_time` while it remains `confirmed` — the natural, indeed only sensible,
implementation is `UPDATE appointments SET start_time = $2 WHERE id = $1`, touching `status` not at
all. Under AD-10's `AFTER INSERT OR UPDATE OF status ON appointments`, that statement **does not
fire `sync_booked_slots()`**.

Concretely: whoever implements `reschedule_appointment` (guided by AD-1's rule: "todas
SECURITY DEFINER... chamam via .rpc()") writes the natural single-column UPDATE. Whoever implements
the trigger (guided by AD-10's rule literally as written: `AFTER INSERT OR UPDATE OF status`)
builds exactly what's specified. Both are individually correct against their own AD. The composed
result: (a) the appointment's *old* slot stays in `booked_slots` forever (never removed, since
nothing ever fires to remove it) — permanently and incorrectly shrinking that doctor's visible
availability; (b) the appointment's *new* slot is never added to `booked_slots` — so AD-4's
client-computed availability grid (`doctor_schedules` minus `booked_slots`) shows the new slot as
free to every other patient, who can then start a booking flow that only fails at the very last
step when `book_appointment`'s unique index rejects it (AD-1) — a confusing CONFLICT for a slot the
UI just told them was open, and a permanently wrong availability picture for the old slot that no
user action can ever fix.

**Suggested tightened Rule (AD-10):** Change the trigger to
`AFTER INSERT OR UPDATE OF status, start_time ON appointments`, and make `sync_booked_slots()`
compare `OLD` vs `NEW` for *both* columns (delete the row keyed on `OLD.start_time` if it existed
and is no longer `confirmed`/moved, insert the row keyed on `NEW.start_time` if `NEW.status =
'confirmed'`) so it is correct regardless of which combination of columns a given RPC touches.
Additionally, state explicitly in AD-1/AD-9 that any future function touching `appointments.status`
or `.start_time` must be re-checked against this trigger's column list — the two are coupled and
that coupling is currently invisible unless a reader cross-references both ADs by hand.

---

## Finding 5 — AD-5: `send-reminders` needs the `service_role` key to see other users' rows, but the spine never says so — a naive implementation using the anon key silently returns zero reminders

**Scenario:** `send-reminders` must query across **all** patients' `confirmed` appointments to find
ones whose `start_time` falls in `[24h, 24h+5min)`. Every other table access described in the spine
is scoped by RLS to `auth.uid()` (AD-10), and Edge Functions have no ambient "user" unless a JWT is
forwarded — `send-reminders` is invoked by a GitHub Actions cron via a shared secret, not by any
logged-in user, so there is no `auth.uid()` at all in that request context. If the function's
Supabase client is initialized with the `anon` key (the default/most commonly documented pattern,
and the same key pattern the Android app itself uses for its Postgrest client per AD-2/AD-10), the
query runs as the `anon` role: RLS on `appointments` (scoped to `patient_id = auth.uid()` /
`doctor_id = auth.uid()`, both `NULL` for an anonymous/unauthenticated context) returns **zero
rows** — not an error, just silently nothing to send. RF-10 quietly stops working end-to-end and
nothing in the spine's error/monitoring story (there is none specified — see Deferred/CI-CD) would
surface that.

The spine also never states that the `service_role` key (which bypasses RLS entirely and is exactly
what's needed here) must be (a) used specifically for this query, and (b) stored **only** as a
Supabase Edge Function secret, never as the same "shared secret" GitHub Actions uses for
HTTPS-header authentication, and never bundled into the Android app. Without that separation stated
explicitly, a future contributor rotating "the reminder secret" could plausibly conflate the two
and put the `service_role` key into the GitHub Actions secret store or, worse, reuse it as the
HTTPS-auth header value itself — turning a narrowly-scoped webhook secret into a full RLS-bypass
credential reachable by anyone who can trigger the workflow.

**Suggested tightened Rule (AD-5):** State explicitly that `send-reminders` must construct its
Postgres/Postgrest client using the `service_role` key (never `anon`) specifically because it must
read across all patients, and that this key: (1) is stored only via `supabase secrets set` as an
Edge-Function-only secret, distinct from the GitHub Actions → Edge Function shared header secret;
(2) is never exposed to the GitHub Actions workflow YAML or any client-side config; (3) has a
documented rotation procedure (who rotates it, where, and that rotating it requires redeploying the
function's secret, not just the GitHub secret).

---

## Finding 6 — AD-9: the "PREFIX: message" convention only covers errors the functions themselves raise; PostgREST/Postgres-level errors (a different shape entirely) will reach the Repository unclassified and be silently mishandled

**Scenario:** AD-9's Rule assumes every error the Repository ever sees from an RPC call originates
from a `RAISE EXCEPTION 'CODE: ...'` inside `book_appointment`/`cancel_appointment`/
`reschedule_appointment`. In practice, the Postgrest/Deno stack surfaces several other error shapes
through the exact same `.rpc()` call that never pass through that `RAISE EXCEPTION` at all:

- An **uncaught internal error** inside the function itself — e.g. a `NOT NULL` violation from a
  bug, a `permission denied for schema` from Finding 3 above, or any other unanticipated Postgres
  error — arrives as PostgREST's own JSON error envelope (`{code, details, hint, message}`) where
  `message` is the raw Postgres error text (e.g. `null value in column "..." violates not-null
  constraint`), with **no** `CONFLICT:`/`INVALID:`/`FORBIDDEN:` prefix and typically no colon-delimited
  structure that means what AD-9 assumes.
- A **PostgREST-level failure that never touches the function body at all** — wrong argument types,
  an overload PostgREST can't resolve (`PGRST202`), a schema-cache-stale 404 after a migration —
  arrives as a PostgREST error code (`PGRST...` or a raw Postgres SQLSTATE like `42883`) in the
  `code` field, again with a `message` that doesn't follow the app's convention.
- A **transport/infrastructure failure** (Edge network hiccup, 502/504, connection reset) may not
  even carry a `message` field the app's convention can parse at all.

AD-9's rule says "a camada Repository faz o parse do prefixo antes dos dois-pontos... de forma
genérica para as três famílias" — three families, no fourth "I don't recognize this shape" bucket
described. A contributor implementing the Repository's error mapping strictly per AD-9 will most
likely write `code = message.substringBefore(":")` and then a `when(code) { "CONFLICT" -> ...;
"INVALID" -> ...; "FORBIDDEN" -> ...; else -> ??? }` — and whatever `else` branch they improvise
(most likely, given no spec, treating it as a generic validation failure and showing the raw
Postgres text to the user, or worse, silently swallowing it as if it were `INVALID`) will
misclassify a genuine infrastructure/permission failure as a business-rule failure, hide the real
bug, leak internal schema/constraint names to the end user, and give no actionable signal that
something is actually broken server-side (as opposed to "you picked an invalid time").

**Suggested tightened Rule (AD-9):** (1) Every `SECURITY DEFINER` function in this family must wrap
its body in `EXCEPTION WHEN OTHERS THEN RAISE EXCEPTION 'INTERNAL: %', SQLERRM;` so that literally
every error path *from inside the function* carries a recognized prefix. (2) The Repository's
parser must treat "no colon-delimited recognized prefix found" as its own explicit fourth state
(`UNKNOWN`/technical error), distinct from and never defaulting to `INVALID`, and must inspect
PostgREST's structured `code` field (not just free-text `message`) to detect PostgREST/Postgres-level
failures (e.g. any SQLSTATE/PGRST code) that never reached the function body, surfacing those as a
generic "try again" error rather than a business-rule message.

---

## Finding 7 — Consistency Conventions/IDs: registration across `auth.users` → `profiles` → `patients`/`doctors` is a three-step chain with no stated atomicity or referential guarantee tying `profiles.role` to which child table a row may exist in

**Scenario:** The Consistency Conventions table states `profiles.id`/`patients.id`/`doctors.id` all
`= auth.uid()`, and the ERD shows `PATIENT`/`DOCTOR` as entities distinct from `profiles`. AD-6 says
`register-patient`/`register-doctor` "cada uma cria o usuário (Auth Admin API) e a linha de
`profiles`" — but never mentions the `patients`/`doctors` row explicitly, and never states that all
of {Auth user creation, `profiles` insert, `patients`-or-`doctors` insert} happen atomically.

This matters because they cannot trivially be one atomic unit: Auth user creation goes through
GoTrue's Admin API (a separate service, not a DML statement in the same Postgres transaction as
`profiles`/`patients` inserts). Two contributors implementing the two registration functions could
reasonably choose different sequencing/error-handling:
- Dev A (`register-patient`): creates the Auth user, then does two sequential Postgrest inserts
  (`profiles`, then `patients`) from Deno, with no compensation if the second insert fails (e.g. a
  cold-start timeout, a transient network blip) — leaving a live `auth.users` row with a `profiles`
  row but no `patients` row, or with neither.
- Dev B (`register-doctor`): wraps the `profiles` + `doctors` insert in a single `SECURITY DEFINER`
  RPC call (one Postgres transaction) but the Auth user creation still happens as a separate prior
  step outside that transaction, so the same "orphaned `auth.users`, no `profiles` row" failure mode
  exists on that first boundary regardless.

Either way, a partially-registered user holds a **valid JWT** (Auth succeeded) but `is_doctor`/
`is_patient` (AD-6) — which query `profiles` — return false/NULL for them, and every RLS policy
gated on those helpers denies everything. The user is authenticated but functionally locked out with
no error message pointing at the real cause, and the spine defines no reconciliation/cleanup path
for this state.

Separately: nothing enforces that a `doctors` row can only exist for a `profiles.id` whose
`role = 'doctor'` (or `patients` ↔ `role = 'patient'`). The FK `doctors.id references profiles.id`
only enforces existence, not role agreement. A future feature (e.g. any admin/support tooling, or a
bug in either registration function) could insert a `doctors` row for a `profiles.role = 'patient'`
id; `is_doctor(uid)` (checks `profiles.role`) and any code path that instead infers "is a doctor"
from "has a row in `doctors`" would then disagree about that user's identity — exactly the kind of
two-independently-correct-implementations-disagree bug this review is looking for.

**Suggested tightened Rule (Consistency Conventions / AD-6):** (1) State explicitly that
`register-patient`/`register-doctor` must create the `profiles` row and the corresponding
`patients`/`doctors` row inside a **single Postgres transaction** (e.g. one `SECURITY DEFINER` RPC
call taking the already-created `auth.users.id` as input), so partial profile/child-table state is
impossible; and that if this transaction fails, the function must delete the just-created
`auth.users` row (compensating action) before returning an error, so no orphaned Auth user survives
a failed registration. (2) Add a `CHECK`-equivalent guarantee that role and child-table membership
can't diverge — e.g. a trigger on `patients`/`doctors` insert that verifies
`(SELECT role FROM profiles WHERE id = NEW.id) = 'patient'|'doctor'` and rejects the insert
otherwise — so `profiles.role` remains the single source of truth and no code path can rely on
child-table existence as an alternate, potentially-inconsistent, definition of role.
