# Deferred Work

Findings from the Story 1.1 code-review gate (step-04) that were judged real (or plausibly
real) but out of scope to fix within this story. Each entry names the spec that surfaced it.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-1-medico-cadastro-perfil.md`
  summary: `DoctorRepository` and `AuthRepository`'s actual body logic (HTTP calls, JSON
  serialization/deserialization, error-string mapping to `AppError`) is only ever exercised
  through MockK-mocked interfaces in `CadastroMedicoViewModelTest`/`LoginViewModelTest`/
  `MinhaAgendaViewModelTest` — the repositories' own request/response handling has zero direct
  test coverage.
  evidence: Confirmed by grepping `app/src/test/` — no test file references `DoctorRepository`
  or `AuthRepository` without also mocking them. Closing this needs either a fake Ktor
  `MockEngine` per repository or a live local Supabase instance (Docker), both larger than a
  single-story fix. Matches the standing project decision (Winston conversation, architecture
  phase) to defer DB/integration test coverage for this portfolio project unless a specific
  need arises.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-1-medico-cadastro-perfil.md`
  summary: AD-9's `CONFLICT:`/`INVALID:`/`FORBIDDEN:` prefix convention is asserted in
  `AppErrorTest.kt` against hand-constructed strings, but never against a real error message as
  actually wrapped/returned by Postgrest or the `functions.invoke` client when a Postgres
  exception crosses that boundary — the real wrapping shape has not been observed.
  evidence: No Docker/Supabase CLI available in this environment to run `supabase start` and
  trigger a real `complete_registration()` failure end-to-end. Marked `maybe-false` at review
  time; settling it requires running the local stack (see spec's Verification section, still
  pending: `supabase start` / `supabase db push` / manual on-device signup) and checking the
  literal string shape of a triggered CONFLICT/INVALID error.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-1-medico-cadastro-perfil.md`
  summary: `register-doctor`'s duplicate-email detection (`supabase/functions/register-doctor/
  index.ts`) matches on `message.includes("already"|"registered"|"exists")` against Supabase
  Auth Admin API's `createUser` error, which is a substring match against upstream wording, not
  a stable error code — a future Supabase Auth API wording change would silently misclassify
  this as `UNEXPECTED` instead of `CONFLICT`.
  evidence: Pre-existing pattern (not introduced by this story's diff), so not blocking for
  Story 1.1. No stable error-code alternative was found in the current `@supabase/supabase-js`
  Admin API surface at review time; worth revisiting if/when the SDK exposes one.
