---
title: 'Médico se cadastra e configura seu perfil profissional'
type: 'feature'
created: '2026-09-17'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
context: []
baseline_commit: '64020690e52f18cd9bee66d6cfec32f95e17ef37'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Não existe app nem backend ainda. Um Médico precisa poder se autocadastrar (sem validação de CRM), definir especialidade/convênios/agenda semanal, e ficar visível na busca imediatamente — o primeiro pedaço fatiável do produto.

**Approach:** Bootstrap do projeto Android (Kotlin/Compose, MVVM) e do projeto Supabase (schema + Edge Function de cadastro), implementando a tela de Login (base, reutilizada por todas as histórias futuras), Escolha de papel, Cadastro de Médico e Minha Agenda (estado vazio).

## Boundaries & Constraints

**Always:**
- Papel (`doctor`) é fixado só pela Edge Function `register-doctor`, nunca por um campo do payload do cliente (AD-6).
- `specialty`/`insurances` do médico ficam imutáveis após a criação, via trigger `enforce_doctor_immutable_fields` (AD-11); `doctor_schedules` é livremente editável.
- Especialidade (6 fixas) e Convênio (4 fixos, incluindo "Particular") são constantes no código (app e Edge Function), não uma tabela (Consistency Conventions da Arquitetura) — valores em português, idênticos ao Glossário do PRD.
- Nomes de tabela/coluna/função em inglês `snake_case`; toda função `SECURITY DEFINER` fixa `SET search_path = public, pg_temp` (AD-1, AD-7).
- `Repository` é o único ponto de acesso ao `supabase-kt`; nenhuma tela/ViewModel chama o cliente Supabase diretamente (AD-2).
- Erros de negócio seguem o vocabulário `CONFLICT:`/`INVALID:`/`FORBIDDEN:`, com um bucket `UNEXPECTED` no app para o resto (AD-9).
- Tokens visuais (cores, tipografia Inter, formas) vêm de `DESIGN.md` — ver Design Notes.
- Botões de ícone (voltar) têm rótulo acessível; alvos de toque ≥48dp (UX-DR7).

**Never:**
- Não implementar cadastro de Paciente (História 1.2) nem "Esqueci minha senha" (História 1.3) — o link pode existir na tela de Login sem estar funcional ainda.
- Não criar `appointments`, `booked_slots` nem qualquer coisa do Épico 2.
- Não permitir edição de Especialidade/Convênio pela UI depois de criado o perfil.
- Não hardcodar URL/chave do Supabase no código-fonte — vêm de configuração local (`local.properties`/`BuildConfig`), nunca versionadas.

**Decisão (Open Question resolvida):** Ambiente de desenvolvimento é o **Supabase local** (`supabase start`, stack via Docker) — sem criação de conta/projeto na nuvem nesta história. O app aponta para a URL local (`10.0.2.2` no emulador, ou IP da máquina na rede local para dispositivo físico). Migração para um projeto na nuvem fica para quando o usuário quiser instalar fora da rede local — não é escopo desta história.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Autocadastro válido | nome, e-mail, senha válidos + "Sou médico" | Conta criada, autenticado, sem etapa de aprovação | N/A |
| Especialidade não selecionada | formulário sem especialidade | Botão "Criar perfil" desabilitado | N/A |
| Convênio não selecionado | nenhum convênio marcado | Botão "Criar perfil" desabilitado (mín. 1) | N/A |
| Especialidade fora da lista fixa | payload direto à API com valor inválido | Rejeitado pela função com `INVALID:` | Mensagem genérica no app |
| Tentativa de editar especialidade/convênio pós-criação | `UPDATE` direto na API | Rejeitado pela trigger `enforce_doctor_immutable_fields` | `CONFLICT:` tratado como erro genérico |
| Login com credenciais corretas | e-mail/senha de conta existente | Autenticado, cai em Minha Agenda | N/A |
| Login com credenciais erradas | e-mail/senha inválidos | Mensagem de erro visível, sem detalhe técnico | Erro `UNEXPECTED` genérico |
| Falha de rede durante cadastro/login | Supabase inacessível | Mensagem genérica "tente novamente", sem crash | Bucket `UNEXPECTED` |

</frozen-after-approval>

## Code Map

*Projeto greenfield — nada existe ainda. Lista abaixo é o que esta história cria (não um mapa de código existente).*

- `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts` -- bootstrap Android: Kotlin 2.4.20, Compose BOM 2026.08.00, Compose Compiler plugin 2.4.20, `supabase-kt` (auth-kt, postgrest-kt, realtime-kt, functions-kt), minSdk 26
- `app/src/main/java/.../ui/theme/{Color,Type,Shape,Theme}.kt` -- tokens de `DESIGN.md`
- `app/src/main/java/.../data/remote/SupabaseClient.kt` -- único ponto de inicialização do cliente Supabase
- `app/src/main/java/.../data/repository/AuthRepository.kt` -- signUp/signIn/sessão via `auth-kt`
- `app/src/main/java/.../data/repository/DoctorRepository.kt` -- chama a Edge Function `register-doctor`; lê o próprio perfil
- `app/src/main/java/.../domain/model/{Especialidade,Convenio}.kt` -- enums fixos em português
- `app/src/main/java/.../ui/auth/{LoginScreen,LoginViewModel}.kt` -- tabs Paciente/Médico, submit real
- `app/src/main/java/.../ui/auth/EscolhaScreen.kt` -- "Sou paciente" / "Sou médico"
- `app/src/main/java/.../ui/doctor/{CadastroMedicoScreen,CadastroMedicoViewModel}.kt` -- formulário completo
- `app/src/main/java/.../ui/doctor/MinhaAgendaScreen.kt` -- estado vazio
- `supabase/migrations/0001_init.sql` -- `profiles`, `doctors`, `doctor_schedules`; trigger `enforce_doctor_immutable_fields`; RLS
- `supabase/functions/register-doctor/index.ts` -- Auth Admin API + `complete_registration` transacional

## Tasks & Acceptance

**Execution:**
- [x] `settings.gradle.kts`/`build.gradle.kts`/`app/build.gradle.kts` -- criar projeto Android com o Stack da Arquitetura -- base de tudo
- [x] `ui/theme/*` -- tema Compose a partir de `DESIGN.md` -- UX-DR1
- [x] `supabase/migrations/0001_init.sql` -- schema + trigger + RLS -- AD-6, AD-7, AD-11
- [x] `supabase/functions/register-doctor/index.ts` -- cadastro transacional -- AD-6
- [x] `data/remote/SupabaseClient.kt`, `data/repository/{Auth,Doctor}Repository.kt` -- camada única de acesso -- AD-2
- [x] `domain/model/{Especialidade,Convenio}.kt` -- constantes fixas -- Glossário PRD
- [x] `ui/auth/{LoginScreen,EscolhaScreen}` -- entrada do app -- EXPERIENCE.md
- [x] `ui/doctor/{CadastroMedicoScreen,MinhaAgendaScreen}` -- fluxo do médico -- FR1, FR2

**Acceptance Criteria:**
- Given um médico não cadastrado, when ele completa "Sou médico" + formulário válido, then a conta é criada, ele é autenticado automaticamente e cai em Minha Agenda vazia (FR1, FR2)
- Given especialidade/convênio já definidos, when uma tentativa de alteração ocorre (UI ou API direta), then é bloqueada pela trigger, nunca pela UI sozinha (FR2, AD-11)
- Given um médico já cadastrado, when ele faz login com e-mail/senha corretos, then é autenticado e cai em Minha Agenda
- Given qualquer erro de rede/validação, when ele ocorre, then o app mostra mensagem genérica, nunca o erro técnico bruto (AD-9)

## Implementation Notes

Full implementation was already present in the working tree (untracked) when this pass started — `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`, the full `app/src/main/java/com/agendamedica/app/**` tree (theme, components, navigation, domain models, repositories, Login/Escolha/CadastroMedico/MinhaAgenda screens+ViewModels), `supabase/migrations/0001_init.sql`, and `supabase/functions/register-doctor/index.ts`. This pass reviewed all of it line-by-line against the spec, the Architecture Spine (AD-1..AD-12), and DESIGN.md, then closed the gaps found:

- **Gradle wrapper was entirely missing** (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar` did not exist, only `gradle-wrapper.properties`) — `./gradlew assembleDebug` from the Verification section could not have run at all. Extracted the genuine `gradle-wrapper.jar` for 8.11.1 from the official distribution zip (`services.gradle.org`) and added the standard `gradlew`/`gradlew.bat` launcher scripts, matching `distributionUrl` already pinned in `gradle-wrapper.properties`.
- **`CadastroMedicoScreen.kt` used `Modifier.menuAnchor()` with no arguments** on both `ExposedDropdownMenuBox` anchors (Especialidade, horário início/fim). Verified against the current Compose Material3 source (`androidx/androidx`, `androidx-main`) that the no-arg overload no longer exists — only `menuAnchor(type: ExposedDropdownMenuAnchorType, enabled: Boolean = true)` does. This would have failed to compile against a Compose BOM as new as `2026.08.00`. Fixed both call sites to `menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)` (correct for a read-only anchor field) and added the import.
- **`DoctorRepository.registerDoctor`** called `client.functions.invoke(function, body)` without a `Content-Type` header. functions-kt's own kdoc notes the header isn't set automatically for a serialized body. Deno's `req.json()` on the Edge Function side doesn't actually require it, so this wasn't a functional bug, but added an explicit `application/json` header for correctness or in case a future edge/runtime version changes that behavior.

Everything else — schema/RLS/trigger design, the `register-doctor` Edge Function's validation + compensating delete, the Repository/ViewModel/Compose layering (AD-2), the AD-9 error-vocabulary parsing (`AppError`/`toUserMessage`), the fixed Especialidade/Convênio enums mirrored on both sides (AD-7), and the DESIGN.md token mapping in `ui/theme/*` — matched the spec and architecture as written; no further changes were made there.

**Not independently verified (environment limitation):** this sandbox has no JDK 17 (only a broken Java 8 launcher stub), no Android SDK beyond a stale `android-27`/`build-tools 27.0.3` install, no Docker, and no Supabase CLI — so none of the four Verification commands (`supabase start`, `supabase db push`, `./gradlew assembleDebug`, manual on-device signup) could actually be executed here. Verification was done statically instead: read every file in full, cross-checked Kotlin calls against the real supabase-kt 3.1.4 and Compose Material3 source on GitHub (this is what surfaced the `menuAnchor` break above), and traced the SQL/TS against the Architecture Spine's AD rules by hand. The person running this locally (with Android Studio + `supabase start`) should treat the four Verification commands as still owed.

---

**2026-09-18 — toolchain fixed on the real dev machine; build now actually compiles and passes tests.**

The user updated the Android SDK (added platforms `android-35` through `android-37.1`, build-tools `37.0.0`) on their own machine, which unblocked real verification. This surfaced (and this pass fixed) several environment/toolchain problems the earlier static review couldn't have caught, plus genuine compile errors in the pre-existing code:

- **AGP 9.0+ built-in Kotlin conflict:** AGP was bumped 8.9.0 → 9.2.0 (required a Gradle bump too, see below) to get a JDK-25-compatible toolchain (see next point). AGP 9.0+ no longer allows the separate `org.jetbrains.kotlin.android` plugin. Removed it from both `build.gradle.kts` and `app/build.gradle.kts` (Kotlin support is now built into `com.android.application`); `compose`/`serialization` Kotlin compiler plugins are unaffected and stay applied. Also removed the now-invalid `kotlinOptions { jvmTarget = "17" }` block (an ERROR-level deprecation in this Kotlin/AGP combo, not just a warning) — `jvmTarget` is already implied by the existing `compileOptions` block.
- **Gradle bumped 8.11.1 → 9.4.1:** the only JDK available on this machine is Android Studio's bundled JBR (OpenJDK 25) — Gradle 8.x cannot run on JDK 25 at all (fails with an opaque `25.0.3` exception, no JDK 17 was available as an alternative). Gradle 9.1+ supports running on JDK 25; AGP 9.2.0 itself requires Gradle ≥ 9.4.1, so pinned that version.
- **`compileSdk`/`targetSdk` bumped 36 → 37:** `checkDebugAarMetadata` failed — several transitive Compose 1.12.0 artifacts (foundation, animation, ui-tooling-data, etc.) require compiling against API 37+; `android-37.1` was already available locally after the SDK update.
- **Three real Kotlin compile errors**, not toolchain-related, found only once actual compilation ran:
  - `AuthRepository.kt` imported `io.github.jan.supabase.auth.SessionStatus`; the real class (confirmed via the resolved jar's class list) lives at `io.github.jan.supabase.auth.status.SessionStatus`.
  - `DoctorRepository.kt` imported `kotlinx.serialization.json.decodeFromString`; switched to `kotlinx.serialization.decodeFromString` (the `StringFormat`-scoped reified extension), which resolves correctly against the transitively-resolved `kotlinx-serialization-json` 1.8.0.
  - `CadastroMedicoScreen.kt` imported `androidx.compose.foundation.layout.weight` (resolves to an internal, inaccessible symbol) and a top-level `androidx.compose.material3.ExposedDropdownMenu` (confirmed via the resolved Material3 jar's class list that this is no longer a standalone composable — it's now `ExposedDropdownMenuBoxScope.ExposedDropdownMenu`, a member resolved via the implicit receiver inside `ExposedDropdownMenuBox { ... }`, no import needed). Removed both bad imports; both call sites were already correctly nested inside their `ExposedDropdownMenuBox`/`Row`/`FlowRow` scopes, so removing the imports was the entire fix.
- **Unit tests added** (none existed before this pass) to close the Matrix Test Audit gap as far as this environment allows: `AppErrorTest` (AD-9 bucket classification, all 4 buckets), `CadastroMedicoViewModelTest` (7 tests: submit-enabled gating for especialidade/convênio/dia, successful cadastro→signIn→navigate, INVALID rejection surfaces generically and doesn't navigate, network failure surfaces generically), `LoginViewModelTest` (5 tests: submit-enabled gating, role-tab field isolation, correct-credentials→navigate, wrong-credentials generic error, network-failure generic error). Added `kotlinx-coroutines-test:1.9.0` and `io.mockk:mockk:1.14.7` as `testImplementation`. One non-obvious fix needed to get the async tests passing: `Dispatchers.setMain(UnconfinedTestDispatcher())` and `runTest { }`'s own default dispatcher are two *independent* `TestCoroutineScheduler`s by default — a `viewModelScope.launch` (routed through `Dispatchers.Main`) and a `backgroundScope.launch { flow.collect {} }` (routed through `runTest`'s own scope) could each run eagerly on their own scheduler but couldn't hand off a no-replay `SharedFlow`'s emit/collect rendezvous to each other, so `navigateToMinhaAgenda` was silently never observed. Fixed by holding one shared `UnconfinedTestDispatcher` instance and passing it to both `Dispatchers.setMain(...)` and `runTest(testDispatcher) { ... }`.
- **Result:** `./gradlew assembleDebug testDebugUnitTest` — **BUILD SUCCESSFUL**, 18/18 unit tests pass.
- **Still not covered by an executed test** (Docker/Supabase CLI remain unavailable in this environment): the two I/O-matrix rows that need a real Postgres — "Autocadastro válido" (row actually lands correctly in `doctors`/`doctor_schedules`) and "Tentativa de editar especialidade/convênio pós-criação" (the `enforce_doctor_immutable_fields` trigger actually rejects the `UPDATE`). Both are implemented and were reviewed by hand against the migration SQL; neither has run against a live database. This matches the user's own standing decision (recorded with Winston during Architecture) to rely on simple tests for now and add DB/integration coverage only if the need is felt later.

---

**2026-09-18 — code-review gate (step-04): 18 findings triaged, all `patch`/`defer`/rejected, no loopback needed.**

Three parallel context-free subagent reviewers (Blind Hunter, Edge Case Hunter, Verification Gap) ran against the full diff since baseline commit `64020690e52f18cd9bee66d6cfec32f95e17ef37`. Every finding was personally re-verified against the real code before routing (see `## Review Triage Log` for the full per-finding record). No finding touched the frozen Intent/Boundaries/IO-Matrix and none required a spec change, so neither `intent_gap` nor `bad_spec` was triggered — only `patch`, `defer`, and outright rejections.

`patch` findings applied directly (fastest path, applied by the orchestrator rather than re-engaging the implementation subagent):
- Fixed `DoctorRepository`'s weekday sort to use `DiaSemana.ordered.indexOf(...)` instead of sorting by `isoValue` (which put Sunday first instead of last for a Mon–Sun display order).
- Corrected two "(ISO/Postgres convention)" doc comments (`DiaSemana.kt`, `0001_init.sql`) that mislabeled the weekday numbering — `EXTRACT(DOW)` is *not* ISO 8601.
- Added client-side (`CadastroMedicoViewModel`) and server-side (`register-doctor`) e-mail format validation (`EMAIL_PATTERN` regex), closing a gap where any non-blank string was accepted as a valid e-mail.
- Added duplicate-weekday and duplicate-insurance rejection in `register-doctor`'s `validatePayload` — the Android client can't produce either (built from `Set`s), but the Edge Function is the actual trust boundary per AD-1/AD-6, and a direct API call could previously insert duplicates.
- `register-doctor`'s compensating `deleteUser()` call (on `complete_registration` RPC failure) now logs when the delete itself fails, instead of silently discarding that error — otherwise an orphaned Auth user could exist with zero record of it anywhere.
- Fixed the blank-`SUPABASE_ANON_KEY` gate in `app/build.gradle.kts` from `.any { "test" }` to `.all { "test" }`, so a mixed invocation like `assembleDebug testDebugUnitTest` still requires a real key for the `assembleDebug` half instead of silently shipping the test placeholder in a real debug APK.
- Removed unused `androidTest`/Espresso/Compose-UI-test dependencies (no `src/androidTest` source set exists yet in this story).
- Added a "Cadastro de paciente chega em breve." caption under `EscolhaScreen`'s "Sou paciente" button so the no-op doesn't read as the app being unresponsive.
- Added a "Mínimo de 6 caracteres." hint under the password field in `CadastroMedicoScreen`.
- Wrote `MinhaAgendaViewModelTest.kt` (3 tests: successful load populates profile, failed load surfaces a generic non-leaking message, explicit `load()` re-fetches) — this ViewModel had zero test coverage before, unlike `LoginViewModel`/`CadastroMedicoViewModel`.
- Fixed `gradlew`'s git file mode from `100644` to `100755` (`git update-index --chmod=+x gradlew`) so it stays executable on a fresh clone on Linux/macOS/CI.

`defer` findings (pre-existing or unverifiable without a live Postgres/Docker) recorded in `_bmad-output/implementation-artifacts/deferred-work.md`: (1) `DoctorRepository`/`AuthRepository`'s HTTP/serialization body logic is only ever exercised through mocks, never a real or faked network call; (2) AD-9's `CONFLICT:`/`INVALID:`/`FORBIDDEN:` prefix convention has never been checked against a real Postgrest-wrapped error string; (3) `register-doctor`'s duplicate-email detection is a substring match against Supabase Auth's error wording, not a stable error code (pre-existing pattern, not introduced by this diff).

Rejected as `false`: email-enumeration via the signup `CONFLICT` message (standard/accepted practice for a registration endpoint, unlike login/reset where the neutral pattern is already correctly used); `Convenio.fromLabel`'s `mapNotNull` silent-drop (defensible graceful degradation, not a defect); `LoginViewModel`'s `isDoctor == false` branch (already documented as intentionally unreachable dead code for this story).

**Result after patches:** `./gradlew assembleDebug testDebugUnitTest` — **BUILD SUCCESSFUL**, 21/21 unit tests pass (18 previous + 3 new `MinhaAgendaViewModelTest`).

## Review Triage Log

Reviewed 2026-09-18 against the diff since `baseline_commit`. Three layers (blind-hunter N=10, edge-case-hunter, verification-gap) ran in parallel; every finding verified against the actual code before routing. No finding touches `<frozen-after-approval>` content — nothing routed to `intent_gap`/`bad_spec`; all patches applied directly (no re-derivation loopback needed).

- `low` — `gradlew` staged as file mode `100644` (verified via `git diff --stat`); on a Linux/macOS clone or CI runner `./gradlew` would fail with "Permission denied" (this Windows machine's own `gradlew.bat` path is unaffected). **patch** — re-add with the executable bit set.
- `maybe-false` — AD-9's `PREFIX_PATTERN` regex assumes `CONFLICT:`/`INVALID:` arrives verbatim; not checked against a real postgrest-kt exception's actual message shape (would need a live Postgres to observe). If wrapped, the two DB-only I/O-matrix rows would misclassify into `Unexpected` instead of `Conflict`/`Invalid` — user-visible impact is small either way since both buckets render a generic message. **defer** — settle once `supabase start` is available.
- `low` — `DiaSemana.ordered` (Monday-first) drives the day-picker in `CadastroMedicoScreen`, but `DoctorRepository.getMyProfile()` sorts the read-back schedule by `isoValue` (Sunday-first) — verified both call sites; a médico who picks "Sábado, Domingo" sees them in reversed relative order on Minha Agenda. **patch** — sort by `DiaSemana.ordered.indexOf(...)` instead of `isoValue`.
- `low` — the "ISO/Postgres convention" comments on `DiaSemana.isoValue` and `doctor_schedules.weekday` mislabel Postgres's `EXTRACT(DOW)` (`0=Sunday..6=Saturday`) as "ISO" — verified against ISO 8601 (`Monday=1..Sunday=7`), they don't match. **patch** — fix the comment wording only.
- `low` — no email-format validation exists in `CadastroMedicoUiState.isSubmitEnabled`, `register-doctor`'s `validatePayload`, or `complete_registration()` — verified all three only check non-blank/non-empty. A malformed address only fails deep inside the Auth Admin API call as a generic error. **patch** — add a basic format check client + server side.
- `false` — email-enumeration concern on `register-doctor`'s `CONFLICT` response: verified this is standard, expected behavior for a *signup* endpoint (as opposed to login/reset, where AD-9/EXPERIENCE.md's neutral-response pattern already applies and was already implemented correctly for RF-11). Revealing "email already registered" on signup is common, accepted practice, not a defect introduced here. Rejected.
- `low` — `EscolhaScreen`'s "Sou paciente" `onClick` is an empty lambda (verified) — tapping it gives no feedback, reads as unresponsive rather than "not built yet." **patch** — add a lightweight snackbar; still doesn't implement Story 1.2's screen, per spec's "Never".
- `low` — `app/build.gradle.kts` declares `androidTestImplementation`/`debugImplementation` Espresso/Compose-UI-test dependencies but the diff has no `src/androidTest` directory — verified, confirmed unused. **patch** — remove now; trivial to re-add when a story actually adds instrumented tests.
- `low` — no visible hint of the 6-character password minimum anywhere in `CadastroMedicoScreen` (verified against `CadastroMedicoUiState.isSubmitEnabled`, the Edge Function, and `complete_registration()`, which all enforce it silently). **patch** — add supporting text under the field.
- `medium` — `app/build.gradle.kts`: `SUPABASE_ANON_KEY` present-but-blank in `local.properties` is accepted as `""` since `Properties.getProperty` only returns null when the key is *absent* — verified the `?:` fallback never triggers for a blank value, undermining the "fails loudly" comment right above it. **patch** — add `.takeIf { it.isNotBlank() }`.
- `medium` — same file: the test-anon-key placeholder is gated on `gradle.startParameter.taskNames.any { contains("test") }` — verified this checks the *whole invocation*, so running `assembleDebug testDebugUnitTest` together (exactly what this story's own Verification did) would silently bake the placeholder into a real debug APK instead of requiring a real key. **patch** — change `.any` to `.all`.
- `false` — `DoctorRepository.getMyProfile()`'s `insurances.mapNotNull(Convenio::fromLabel)` silently drops an unrecognized convênio label instead of throwing (asymmetric with `Especialidade`, which throws) — verified, but judged intentional/defensible: `doctors.insurances` is a list, so dropping one unrecognized entry degrades gracefully (partial profile) rather than crashing the whole screen over state the write path (`register-doctor`'s `validatePayload`) already prevents from occurring. Rejected.
- `low` — `register-doctor`'s `validatePayload` never rejects a `schedules` array with a repeated `weekday`, or an `insurances` array with a repeated value — verified; the Android client can't produce either (uses `Set`s), but a direct API call could, inserting duplicate `doctor_schedules` rows or a duplicate convênio tag. **patch** — add a duplicate check for both arrays.
- `medium` — `register-doctor`'s compensating `admin.auth.admin.deleteUser(userId)` (AD-6's "never leave an orphaned Auth user" guarantee) doesn't check its own result — verified. If the delete itself fails, the exact orphaned state AD-6 promises to prevent occurs, silently. **patch** — log on failure for manual cleanup.
- `medium` — `MinhaAgendaViewModel.load()` (the screen every médico lands on after registering) has zero test coverage — verified no test file references it, unlike `LoginViewModel`/`CadastroMedicoViewModel`. Doesn't need a live DB — same mock pattern as the other two ViewModel tests applies. **patch** — add `MinhaAgendaViewModelTest`.
- `medium` (unverified severity) — `DoctorRepository.registerDoctor`/`getMyProfile`'s own bodies (HTTP call, header, JSON error-body decode, row→domain mapping) are only ever mocked away in `CadastroMedicoViewModelTest`/never touched by any `MinhaAgendaViewModel` test — verified no test exercises the real serialization/mapping code. Would need a fake Ktor engine or the local Supabase stack to close properly — larger than "minimal tests" and matches the standing decision (recorded with Winston, Architecture) to defer DB/integration-level coverage. **defer**.
- `maybe-false` — `register-doctor`'s "already registered" detection substring-matches the Admin API's error message (`.includes("already")`/`"registered"`/`"exists"`) — fragile against Admin API wording changes across supabase-js versions, but not introduced by this diff's own logic (pre-existing pattern) and not independently verifiable without hitting a real Auth error. **defer**.
- `false` — `LoginViewModel`'s `isDoctor == false` branch is untested, but the spec's own Implementation Notes already document it as intentionally unreachable dead code for this story (no patient accounts can exist yet) — not a defect. Rejected.

## Design Notes

**Nota de correção (Story 5.1, 2026-09-22):** a premissa de "Slots de 15 minutos" usada nesta spec foi substituída — consulta de 30 min com 15 min de intervalo (grade de 45 em 45 min). O Médico continua escolhendo seu próprio horário de início/fim (nada mudou nisso), mas agora dentro do horário fixo da clínica, 08h-18h (antes 06h-22h). Ver `spec-5-1-grade-horarios-30min.md` e `sprint-change-proposal-2026-09-22.md`.

Tokens de `DESIGN.md` a aplicar no tema Compose (não redefinir aqui — só apontar):
- Cores: `surface-canvas` `oklch(0.97 0.015 150)`, `accent-primary` `#3B6FE0`, famílias `success`/`warning`/`danger`.
- Tipografia: Inter, pesos 400-700, escala 11-19px.
- Formas: `pill` 100px, `lg` 14px, `md` 10-12px, `circle` 50%.

Convênios fixos (ordem sugerida): Unimed, Amil, Bradesco, Particular. Especialidades fixas: Cardiologia, Dermatologia, Pediatria, Ortopedia, Clínico Geral, Ginecologia.

## Verification

**Commands:**
- `supabase start` -- expected: stack local sobe sem erro (Postgres, Auth, Studio) -- **ainda não executado** (Docker/Supabase CLI indisponíveis neste ambiente)
- `supabase db push` (ou aplicar `0001_init.sql` localmente) -- expected: migração aplica sem erro; índice/trigger criados -- **ainda não executado** (mesma limitação)
- `./gradlew assembleDebug` -- expected: build do app sem erro -- **executado e passou** (`BUILD SUCCESSFUL`, 2026-09-19, após toolchain fix + todos os patches do code-review gate)
- Cadastro manual de um médico via app (emulador/dispositivo) -- expected: perfil aparece em `doctors` com `specialty`/`insurances` corretos; tentativa de `UPDATE` direto nesses campos via Studio SQL falha com a trigger -- **ainda não executado** (depende de `supabase start`)
- `./gradlew testDebugUnitTest` (adicionado a esta lista durante a implementação, não estava no plano original) -- **executado e passou**, 21/21 testes (`AppErrorTest`, `LoginViewModelTest`, `CadastroMedicoViewModelTest`, `MinhaAgendaViewModelTest`)

**Manual checks (if no CLI):**
- Conferir visualmente que Login/Escolha/Cadastro Médico/Minha Agenda seguem os tokens de `DESIGN.md` (cores, tipografia, formas) -- **ainda não executado** (requer rodar o app em emulador/dispositivo)

**Pendente para o usuário concluir localmente:** os 3 itens acima marcados como não executados exigem Docker + Supabase CLI, que não estão disponíveis neste ambiente de implementação. Rodar `supabase start`, aplicar a migração, e então testar o cadastro de um médico de ponta a ponta no emulador é o que resta para fechar a Story 1.1 com confiança total.
