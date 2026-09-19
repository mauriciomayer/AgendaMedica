---
title: 'Médico se cadastra e configura seu perfil profissional'
type: 'feature'
created: '2026-09-17'
status: 'in-review'
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

## Review Triage Log

## Design Notes

Tokens de `DESIGN.md` a aplicar no tema Compose (não redefinir aqui — só apontar):
- Cores: `surface-canvas` `oklch(0.97 0.015 150)`, `accent-primary` `#3B6FE0`, famílias `success`/`warning`/`danger`.
- Tipografia: Inter, pesos 400-700, escala 11-19px.
- Formas: `pill` 100px, `lg` 14px, `md` 10-12px, `circle` 50%.

Convênios fixos (ordem sugerida): Unimed, Amil, Bradesco, Particular. Especialidades fixas: Cardiologia, Dermatologia, Pediatria, Ortopedia, Clínico Geral, Ginecologia.

## Verification

**Commands:**
- `supabase start` -- expected: stack local sobe sem erro (Postgres, Auth, Studio)
- `supabase db push` (ou aplicar `0001_init.sql` localmente) -- expected: migração aplica sem erro; índice/trigger criados
- `./gradlew assembleDebug` -- expected: build do app sem erro
- Cadastro manual de um médico via app (emulador/dispositivo) -- expected: perfil aparece em `doctors` com `specialty`/`insurances` corretos; tentativa de `UPDATE` direto nesses campos via Studio SQL falha com a trigger

**Manual checks (if no CLI):**
- Conferir visualmente que Login/Escolha/Cadastro Médico/Minha Agenda seguem os tokens de `DESIGN.md` (cores, tipografia, formas)
