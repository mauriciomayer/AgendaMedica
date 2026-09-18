# Epic 1 Context: Cadastro, Perfil e Autenticação

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

This epic builds the entry gate to the whole app: account creation and identity for both actor types, plus the technical foundation everything else sits on (Android project setup, Supabase project/schema, visual theme). A Médico self-registers with no approval or document validation and becomes searchable immediately, then configures a professional profile (specialty, insurances, weekly schedule). A Paciente registers with minimal data. Either can recover a forgotten password without support. Without this epic, no downstream booking flow (Epic 2) has doctors, patients, or auth to work against.

## Stories

- Story 1.1: Médico se cadastra e configura seu perfil profissional
- Story 1.2: Paciente se cadastra
- Story 1.3: Usuário recupera a senha esquecida

## Requirements & Constraints

- Doctor self-registration has no approval/validation step (no CRM check); profile must be searchable within seconds of saving.
- A Médico selects exactly one specialty from a fixed list of 6 (Cardiologia, Dermatologia, Pediatria, Ortopedia, Clínico Geral, Ginecologia) and one or more insurances from a fixed list of 4 (Unimed, Amil, Bradesco, Particular). Both become immutable after profile creation — no edit path in the UI, and a direct API attempt to change them must be rejected.
- Doctor's weekly availability (days + start/end times) generates the basis for 15-minute slots; it's editable anytime, and editing it must never retroactively affect already-confirmed appointments or notify the patient of any change.
- Patient registration collects name/email/password only (current design; may grow in future versions) and never requires an insurance to be selected.
- Password reset is requested via the registered email; the confirmation message is always neutral and never reveals whether that email has an account. Reset link expiration follows Supabase Auth's own default. Old password stops working once a new one is set.
- Authentication is mandatory for any operation reading/writing appointment-adjacent data; access is always scoped to the owning user.
- All time-based rules (slots, lead times) resolve in a single fixed timezone, `America/Sao_Paulo` — no per-user timezone handling.
- Platform is Android native only, installed directly on-device (no store publish requirement).
- Success criteria for this epic's slice: doctor signup, patient signup, and password recovery all run end-to-end without errors on device/emulator.

## Technical Decisions

- Stack: Kotlin 2.4.20 + Jetpack Compose (BOM 2026.08.00, compiler plugin 2.4.20 pinned to the Kotlin version), MVVM with unidirectional flow (Compose View → ViewModel/StateFlow → Repository). Only the `data/` layer talks to Supabase (`supabase-kt`: auth-kt, postgrest-kt, realtime-kt, functions-kt); no ViewModel or Composable calls it directly. Package layout: `app/ui`, `app/viewmodel`, `app/data/repository`, `app/data/remote`, `app/domain/model`.
- Backend: single Supabase project (Postgres + Auth + Edge Functions + Realtime), no dev/prod separation in the MVP. minSdk 26.
- Core tables touched here: `profiles` (id = `auth.uid()`, `role` fixed to `patient`/`doctor`), `doctors` (specialty, insurances[]), `patients` (name, email), `doctor_schedules` (doctor_id, weekday, start_time, end_time) — a normalized table, not JSON; actual time slots are computed on the fly elsewhere, never stored as rows here.
- Role is never client-declared: two Edge Functions, `register-patient` and `register-doctor`, each create the Auth user then call a single Postgres function `complete_registration(role, ...)` in one transaction that inserts `profiles` (role fixed by which endpoint was called) plus the matching `patients`/`doctors` row. If that transaction fails, the Edge Function deletes the just-created Auth user — never leaves an orphaned Auth user with no profile.
- RLS role checks always go through `is_doctor(uid)`/`is_patient(uid)` — `SECURITY DEFINER`, `STABLE`, fixed `search_path`, living in a non-exposed `private` schema, granted `EXECUTE` to `authenticated`, always called schema-qualified from policies. Role state is read live from `profiles` on every check, never trusted from a cached JWT claim.
- Doctor profile writes: owner can `UPDATE` `doctors` directly via RLS, but a `BEFORE UPDATE` trigger `enforce_doctor_immutable_fields()` rejects any change to `specialty` or `insurances` versus the stored value. `doctor_schedules` has no such trigger and stays freely editable by the owner.
- Naming convention: all schema/table/column/function/Edge Function names are English `snake_case`; user-facing enum values (specialty names, insurance names) stay in Portuguese as displayed content, not as identifiers.
- Postgres function errors follow one vocabulary: `RAISE EXCEPTION` messages prefixed `CONFLICT:` / `INVALID:` / `FORBIDDEN:`; anything else (network failure, unexpected PostgREST error) falls into an implicit `UNEXPECTED` bucket that the Repository shows as a generic "try again" message — never raw infra error text. This applies to `complete_registration` failures too.
- Password reset itself needs no custom function — it's native Supabase Auth end to end.
- Specialty and insurance lists are versioned constants in code (app and Edge Functions), not an editable database table.

## UX & Interaction Patterns

- Implement the Compose theme from the design tokens: `surface-canvas` background, single brand color `accent-primary` (`#3B6FE0`) reserved for actions/selection, `success`/`warning`/`danger` families used only for state (never decoration), Inter typeface (400-700) across an 11-19px scale, shapes `pill` (100px) for buttons/chips/tabs, `lg` (14px) for cards, `md` (10-12px) for inputs.
- Components needed for this epic's screens: primary button (full-width, disabled state uses `surface-disabled`/`ink-disabled`), outline button, toggle chip (used for specialty/insurance/day selection — active state adds a 2px accent border and tint background), read-only tag chip (for accepted insurances), avatar with initials, text/password input.
- Screens in scope: Login, Recuperar Senha, Criar Conta (Escolha "Sou paciente"/"Sou médico"), Cadastro de Paciente, Cadastro de Médico.
- Any primary action button stays disabled until every required field on the screen is filled; no inline error is shown before a submit attempt.
- Voice and tone: direct, specific, no apologetic phrasing, no gamification, exclamation marks reserved for the single success moment (e.g. "Sem validação de CRM — seu perfil fica visível na busca assim que você concluir o cadastro.").
- Password recovery always shows the same neutral confirmation regardless of whether the email exists, followed by a single "Voltar ao login" action.
- Accessibility floor: touch targets ≥ 48dp; icon-only buttons (e.g. back) carry a real `contentDescription`; every form field has a properly associated label; nothing is communicated by color alone.

## Cross-Story Dependencies

- Stories 1.1 and 1.2 both depend on the epic's underlying technical base (Android project scaffold, Supabase project/schema, and the Compose theme) being set up — this base is part of Epic 1's scope rather than its own story, so whichever story starts first will likely need it in place.
- The doctor profile and `doctor_schedules` data created in Story 1.1 are a hard prerequisite for Epic 2 (search, availability grid, booking all read from this data).
- Story 1.3 (password reset) only needs Supabase Auth configured as part of the base setup; it does not depend on the registration flows in 1.1/1.2 beyond accounts existing to recover.
