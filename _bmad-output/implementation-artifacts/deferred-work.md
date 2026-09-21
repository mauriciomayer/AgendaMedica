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

- source_spec: `_bmad-output/implementation-artifacts/spec-1-2-paciente-se-cadastra.md`
  summary: Tela Minhas Consultas do Paciente com estado vazio "Você ainda não tem consultas agendadas." e botão "+ Nova consulta" (4º critério de aceite da Story 1.2 no epics.md), incluindo a ação "Minhas consultas" na Busca.
  evidence: Separada da Story 1.2 por decisão do usuário para manter a spec dentro do escopo (~2.000 tokens > 1.600). É uma tela isolada, sem dependência do cadastro; deve entrar junto com a lista real de consultas do Épico 2 (Story 2.4) ou como história própria antes dela.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-2-paciente-se-cadastra.md`
  summary: `register-patient` copia a deteção de e-mail duplicado por substring (`already`/`registered`/`exists`) na mensagem do `createUser`, agora em duas Edge Functions.
  evidence: Severidade `medium` não verificada — se o Supabase Auth mudar o texto, o CONFLICT vira `UNEXPECTED` e some a mensagem "Já existe uma conta com este e-mail." Resolver junto com o item equivalente da 1.1 (usar o `code` do erro, ex.: `email_exists`, quando o SDK expuser).

- source_spec: `_bmad-output/implementation-artifacts/spec-1-2-paciente-se-cadastra.md`
  summary: Sem testes automatizados de servidor: `complete_registration` (ramos `doctor` e `patient`, reescrito por `create or replace` na 0002), RLS de `patients` e as Edge Functions `register-doctor`/`register-patient`.
  evidence: Só há verificação por chamadas reais ao projeto hospedado (201/409/400 e regressão do médico). Uma regressão silenciosa no ramo do médico copiado na 0002 não falharia nenhum teste Kotlin (todos mockam o repositório). Precisa de pgTAP/SQL smoke ou testes Deno; alinhado à decisão de adiar testes de banco/integração.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-2-paciente-se-cadastra.md`
  summary: O cadastro (médico e paciente) cria conta com `email_confirm: true`, sem verificar que o cadastrante é dono do e-mail; `patients.email` alimenta lembretes da Story 3.1.
  evidence: Severidade `medium` não verificada, vem de decisão de design da 1.1 (sem etapa de validação). Antes de enviar e-mails na 3.1, decidir se basta o risco (projeto de portfólio) ou se cabe confirmação de e-mail / opção de descadastro.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-3-usuario-recupera-senha.md`
  summary: Sem teste automatizado da importação real da sessão de recuperação (`importAuthToken`), de `requestPasswordReset`/`updatePassword` contra o cliente Auth, do roteamento do deep link no NavHost e do `MainActivity`; apenas a lógica pura (classificação do link, mapeamento de erros) e os ViewModels são testados.
  evidence: Severidade `medium`, verificada. Exige mockar o cliente Auth do supabase-kt (propriedade de extensão) ou UI tests instrumentados, que o projeto não tem (decisão registrada com Winston). O fluxo ponta a ponta fica coberto só pelo teste manual no aparelho (e-mail real, link, Nova Senha).

- source_spec: `_bmad-output/implementation-artifacts/spec-1-3-usuario-recupera-senha.md`
  summary: A sessão criada pelo link de recuperação fica salva no aparelho se o app for morto em Nova Senha ou se o `signOut` falhar; hoje o app sempre abre no Login, então nada a usa.
  evidence: Severidade `low` hoje, sobe para `medium` quando alguma história adicionar restauração de sessão/auto-login: aí uma sessão de recuperação abandonada daria acesso sem senha. Nessa história, tratar sessões de recuperação (ex.: detectar `type=recovery` e exigir Nova Senha, ou `signOut(SignOutScope.GLOBAL)`).

- source_spec: `_bmad-output/implementation-artifacts/spec-2-1-paciente-busca-medicos.md`
  summary: Sem teste automatizado do mapeamento de `DoctorRepository.searchDoctors` (filtro de Especialidade no servidor, descarte de linhas desconhecidas), de `AndroidLocationProvider` (permissão, posição recente, timeout, fallback de provedor) e das validações de localização da Edge Function `register-doctor` e do RPC `complete_registration`.
  evidence: Severidade `medium`, verificada. Cobertos apenas por chamadas reais ao projeto hospedado (localização válida 201, fora da lista/ausente 400, leitura como paciente) e pelo teste manual no aparelho. Fechar exige Robolectric/UI test, harness pgTAP/Deno ou extrair o mapeamento linha->resumo para função pura; o teste de consistência da lista de localidades já cobre a divergência entre as três cópias.

- source_spec: `_bmad-output/implementation-artifacts/spec-2-2-paciente-visualiza-horarios.md`
  summary: Sem teste automatizado de `DoctorRepository.getDoctorDetail`/`getBookedSlots` (mapeamento de linhas, filtro de janela, `OffsetDateTime.parse`), da RLS de `booked_slots` e da navegação Busca -> Detalhe.
  evidence: Severidade `medium`, verificada. Cobertos por conferência manual contra o servidor real (formatos `08:00:00` e `2026-09-30T17:30:00+00:00`, janela `gte`/`lt`, escrita como paciente -> 403) e pelo teste manual no aparelho. Fechar exige fake do cliente Postgrest/harness de servidor e UI tests instrumentados; a Story 2.3 (que passa a escrever em `booked_slots` via trigger) é o momento natural para um teste de repositório.

- source_spec: `_bmad-output/implementation-artifacts/spec-2-3-paciente-agenda-consulta.md`
  summary: Se a resposta de `book_appointment` se perde (rede) e o paciente reenvia, ele recebe `CONFLICT: slot_taken` para a própria consulta em vez de sucesso.
  evidence: Severidade `medium`, verificada por leitura do fluxo (sem chave de idempotência). A correção natural é, ao receber `slot_taken`, consultar `appointments` (RLS mostra só as do próprio paciente) e tratar uma linha `confirmed` do mesmo médico/horário como sucesso; não coube nesta história por acrescentar comportamento não previsto na spec.

- source_spec: `_bmad-output/implementation-artifacts/spec-2-3-paciente-agenda-consulta.md`
  summary: Sem teste automatizado do cliente Kotlin contra o servidor (`bookAppointment`, `observeBookedSlots`, canal Realtime real), das rotas/`popUpTo` da Confirmação e do Realtime ponta a ponta; o RPC em si é provado pelo `supabase/tests/concurrency-test.mjs` (manual, contra o projeto hospedado, fora de CI).
  evidence: Severidade `medium`, verificada. Fechar exige fake do cliente supabase-kt/Realtime, UI tests instrumentados e um passo de CI que rode o script com credenciais; enquanto isso, a verificação é o teste manual no aparelho (agendar, ver a Confirmação, disputar o mesmo horário e ver o "Ocupado" sem recarregar).

- source_spec: `_bmad-output/implementation-artifacts/spec-2-4-paciente-cancela-reagenda.md`
  summary: Sem teste automatizado do contrato entre o cliente Kotlin e os RPCs `cancel_appointment`/`reschedule_appointment` (nome da função e chaves JSON), da rota com `consultaId` (Reagendar) e do retorno com atualização da lista; os scripts contra o banco chamam os RPCs por `fetch` puro.
  evidence: Severidade `medium`, verificada. Se um nome de parâmetro divergir no Kotlin, os testes unitários seguem verdes e o app mostra sempre "Algo deu errado"; se o argumento `consultaId` se perder no NavHost, "Reagendar" abriria o fluxo de agendar. Cobertura hoje: teste manual no aparelho. Fechar exige fake do cliente supabase-kt ou UI tests instrumentados.

- source_spec: `_bmad-output/implementation-artifacts/spec-2-4-paciente-cancela-reagenda.md`
  summary: O trigger `sync_booked_slots` (Story 2.3) só reage a INSERT e UPDATE de `appointments`; quando uma consulta é apagada em cascata (excluir o paciente ou o médico), a linha de `booked_slots` fica órfã e o horário continua "Ocupado" para sempre.
  evidence: Severidade `low` hoje e pré-existente (não causada pela 2.4), verificada: apaguei um paciente de teste com consulta confirmada e sobrou 1 linha em `booked_slots` (removida à mão). Não há funcionalidade de excluir conta no MVP; se surgir, incluir `AFTER DELETE` no trigger (ou `ON DELETE` que remova o horário) numa nova migração.

- source_spec: `_bmad-output/implementation-artifacts/spec-2-5-medico-cancela-reagenda.md`
  summary: Sem teste automatizado do contrato entre o cliente Kotlin e o RPC `list_doctor_appointments` (nome da função e formato das linhas contra o servidor), da entrada Reagendar da Minha Agenda (rota `medico/{doctorId}?consultaId=`) e do `doctorIdProvider`; o RPC é provado por `fetch` puro em `supabase/tests/cancel-reschedule-test.mjs`.
  evidence: Severidade `medium`, verificada. Se o nome do RPC divergir no Kotlin, os testes unitários seguem verdes e a lista do médico mostra sempre o erro genérico; se o `consultaId` se perder, Reagendar abriria o fluxo de agendar. Cobertura hoje: teste manual no aparelho. Mesma causa da pendência da Story 2.4 (sem fake do cliente supabase-kt nem UI tests instrumentados).
