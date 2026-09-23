---
title: 'Paciente se cadastra'
type: 'feature'
created: '2026-09-21'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: 'e63ffd4a673ffd4e6a56e921e19a9a2aa7678ea0'
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Só o Médico consegue criar conta. O Paciente precisa se autocadastrar (nome/e-mail/senha, sem convênio) e cair autenticado na Busca, para depois buscar médicos e agendar (Épico 2).

**Approach:** Espelhar o fluxo do Médico: Edge Function `register-patient` + ramo `patient` de `complete_registration` (migração `0002`) e Cadastro de Paciente no app. A Busca é só uma tela-casca de destino (a busca real é a Story 2.1). O Login passa a rotear Paciente para a Busca.

## Boundaries & Constraints

**Always:**
- Papel `patient` fixado só pela Edge Function `register-patient`, nunca por campo do cliente (AD-6); e-mail gravado em `patients` lido server-side de `auth.users`, nunca passado pelo cliente (AD-1).
- Mesma transação/compensação da 1.1: se `complete_registration` falha, a Edge Function apaga o usuário Auth criado; `SET search_path = public, pg_temp`; erros `CONFLICT:`/`INVALID:`/`FORBIDDEN:` + bucket `UNEXPECTED` (AD-9).
- Validação (nome não vazio, e-mail válido, senha ≥ 6) idêntica no app e na Edge Function; botão "Criar conta" desabilitado até tudo válido, sem erro inline antes do envio.
- Nenhum campo de Convênio no cadastro (FR3). Identificadores em inglês `snake_case` (AD-7); só o `data/` fala com o Supabase (AD-2).
- Alvos de toque ≥48dp, ícones com `contentDescription` (UX-DR7).

**Never:**
- Não implementar busca real, Detalhe, agendamento, `appointments` nem a tela Minhas Consultas (adiada, ver `deferred-work.md`); nem "Esqueci minha senha" (Story 1.3).
- Não editar `0001_init.sql` (já aplicada no Supabase hospedado) — mudanças só em nova migração.
- Não hardcodar URL/chave do Supabase.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Cadastro válido | "Sou paciente" + nome/e-mail/senha válidos | Conta + `profiles(role=patient)` + `patients` criados; autenticado; cai em Busca | N/A |
| Campo obrigatório vazio/inválido | campo vazio, e-mail malformado ou senha < 6 | "Criar conta" desabilitado | N/A |
| E-mail já cadastrado | e-mail existente (médico ou paciente) | Nenhuma conta nova | `CONFLICT:` -> "já existe uma conta com este e-mail" |
| Falha em `complete_registration` | erro após criar usuário Auth | Usuário Auth removido (sem órfão) | Mensagem genérica, sem detalhe técnico |
| Payload direto inválido | nome vazio, e-mail/senha inválidos via API | Rejeitado pela função | `INVALID:` -> mensagem genérica no app |
| Falha de rede | Supabase inacessível | Sem crash | Bucket `UNEXPECTED`, "tente novamente" |
| Login de Paciente | credenciais corretas de conta `patient` | Autenticado, cai em Busca | Erro genérico se falhar |

</frozen-after-approval>

## Code Map

- `supabase/migrations/0001_init.sql` -- `profiles`, `complete_registration()` (ramo `patient` marcado como não implementado, ~linha 122); NÃO editar
- `supabase/functions/register-doctor/index.ts` -- modelo a espelhar (validação, `createUser`, RPC, compensação com log)
- `app/.../data/repository/DoctorRepository.kt` -- padrão de `registerDoctor`, `isCurrentUserDoctor()`
- `app/.../data/repository/AppError.kt` -- reutilizar `toAppError()/toUserMessage()`
- `app/.../ui/doctor/CadastroMedicoViewModel.kt` + `CadastroMedicoScreen.kt` -- padrão de ViewModel/tela (SharedFlow de navegação, `EMAIL_PATTERN`)
- `app/.../ui/auth/{EscolhaScreen,LoginViewModel,LoginScreen}.kt` -- habilitar "Sou paciente"; ramo `isDoctor == false` vira navegação para Busca
- `app/.../ui/navigation/AgendaMedicaNavHost.kt` -- novas rotas
- `app/.../ui/components/Components.kt` -- `PrimaryButton`, `LabeledTextField`, `AccessibleIconButton`
- `app/src/test/.../ui/doctor/CadastroMedicoViewModelTest.kt`, `ui/auth/LoginViewModelTest.kt` -- padrão de teste (um `UnconfinedTestDispatcher` compartilhado em `setMain` e `runTest`)

## Tasks & Acceptance

**Execution:**
- [x] `supabase/migrations/0002_patients.sql` -- tabela `patients (id -> profiles, name, email)` + RLS `select` do próprio; `create or replace` de `complete_registration` com ramo `patient` (lê e-mail de `auth.users`), mesma assinatura, grants só a `service_role` -- AD-1, AD-6
- [x] `supabase/functions/register-patient/index.ts` -- cadastro transacional espelhando `register-doctor` -- AD-6
- [x] `data/repository/PatientRepository.kt` -- `registerPatient()` via `functions.invoke("register-patient")` -- AD-2
- [x] `ui/patient/{CadastroPacienteScreen,CadastroPacienteViewModel}.kt` -- form nome/e-mail/senha; submit = registrar + `signIn` + navegar para Busca
- [x] `ui/patient/BuscaScreen.kt` -- casca: título "Buscar médicos" e texto "A busca de médicos chega em breve."
- [x] `ui/auth/EscolhaScreen.kt`, `LoginViewModel.kt`, `LoginScreen.kt`, `AgendaMedicaNavHost.kt` -- ligar "Sou paciente" (remover legenda "chega em breve"), rotear Paciente para Busca no login e no cadastro (limpando a pilha até Login)
- [x] `app/src/test/...` -- `CadastroPacienteViewModelTest` (habilitação do botão, sucesso -> navega, `INVALID`/rede -> mensagem genérica sem navegar); atualizar `LoginViewModelTest` para o ramo Paciente

**Acceptance Criteria:**
- Given um visitante, when conclui "Sou paciente" com dados válidos, then fica autenticado na Busca e existem `patients` + `profiles(role=patient)` (FR3)
- Given o formulário, when qualquer campo obrigatório falta, then "Criar conta" fica desabilitado e não há campo de Convênio
- Given um erro qualquer, when ocorre, then o app mostra mensagem genérica, nunca o erro técnico bruto (AD-9)

## Implementation Notes

**2026-09-21 — implementação concluída.** Migração `0002_patients.sql` (tabela `patients`, RLS de leitura do próprio, `complete_registration` com ramo `patient` lendo o e-mail de `auth.users`), Edge Function `register-patient`, `PatientRepository`, `CadastroPacienteScreen/ViewModel`, `BuscaScreen` (casca), `EscolhaScreen` habilitando "Sou paciente", Login roteando não-médico para a Busca (novo `navigateToBusca`) e rotas no NavHost.

- **Auditoria da matriz:** a linha "E-mail já cadastrado" exigia a mensagem "já existe uma conta com este e-mail", mas o app mostrava o texto genérico de `CONFLICT`. Corrigido no código: `CadastroPacienteViewModel` mapeia `AppError.Conflict` para "Já existe uma conta com este e-mail." (a matriz não foi alterada) + teste. `AppError.toUserMessage()` continua genérico para os demais fluxos.
- **Backend hospedado:** `db push` (0002) e `functions deploy register-patient` executados no projeto `vuqvizzkdeiseyunjrms`. Chamadas reais: cadastro válido 201; e-mail repetido 409; nome vazio 400; senha curta 400; regressão `register-doctor` 201 (o `create or replace` de `complete_registration` não quebrou o médico). Usuários de teste `smoke.paciente.*`/`smoke.med.*@example.com` ficaram no projeto (apagar em Authentication → Users).
- **Não exercitado:** a linha "Falha em `complete_registration`" (remoção do usuário Auth órfão) — exige forçar um erro de banco após o `createUser`; a lógica espelha a do `register-doctor` (já revisada na 1.1).
- `./gradlew assembleDebug testDebugUnitTest`: BUILD SUCCESSFUL, 30 testes (AppError 6, Login 6, CadastroMedico 7, MinhaAgenda 3, CadastroPaciente 8), 0 falhas.

**2026-09-21 — patches do gate de revisão (ver Review Triage Log).** `AppError.toAppError()` passou a ler o prefixo AD-9 de `RestException.error` (supabase-kt lança em todo não-2xx; a mensagem da exceção começa com o JSON bruto), o que corrige a mensagem de e-mail duplicado/`INVALID` também no fluxo do médico e resolve, para o caminho das Edge Functions, o item "AD-9 vs formato real" adiado na 1.1 (o caminho Postgrest/RPC continua sem verificação real). `PatientRepository` perdeu o ramo morto de `isSuccess`; falha de `signIn` pós-cadastro manda o usuário ao Login; `register-patient` rejeita corpo não-objeto com 400 (republicado). Resultado final: BUILD SUCCESSFUL, 32 testes (AppError 7, Login 6, CadastroMedico 7, MinhaAgenda 3, CadastroPaciente 9), 0 falhas.

## Spec Change Log

## Review Triage Log

Três revisores (Blind Hunter, Edge Case Hunter, Verification Gap), achados verificados contra o código real. Nenhum `intent_gap`/`bad_spec`; sem loopback.

| # | Achado | Veredito | Evidência | Rota |
|---|--------|----------|-----------|------|
| 1 | VG: parsing do erro em `PatientRepository` sem teste | `high` | Verificado nas fontes do supabase-kt 3.1.4 (`SupabaseApi.kt:25`): `functions.invoke` lança `RestException` em toda resposta não-2xx, então o ramo `isSuccess` era código morto e `RestException.message` começa com o JSON bruto + URL/headers; o regex ancorado de `toAppError()` nunca casava. E-mail duplicado e `INVALID` caíam sempre em "Algo deu errado" (linha "E-mail já cadastrado" da matriz quebrada na prática; vale também para o fluxo do médico). | **patch** — `toAppError()` lê o prefixo de `RestException.error` (corpo JSON `{"error": ...}`); ramo morto removido do `PatientRepository`; teste em `AppErrorTest` |
| 2 | Falha de `signIn` após cadastro deixa o usuário preso (CONFLICT ao reenviar) | `medium` | `CadastroPacienteViewModel` mostrava mensagem genérica e reenviar batia em CONFLICT. | **patch** — mensagem manda voltar e fazer login + teste |
| 3 | Corpo JSON `null`/array/string derruba `register-patient` com 500 | `low` | `validatePayload(null)` lança `TypeError`; confirmado o guard ausente. Correção de uma linha. | **patch** — guard 400 `INVALID:`; republicado e testado (400 nos três casos) |
| 4 | `MutableSharedFlow` sem replay pode perder o evento de navegação (rotação/segundo plano) | `low` | Padrão idêntico ao da 1.1 em todos os ViewModels; só ocorre se o coletor estiver ausente no instante exato do emit; correção exige trocar o tipo de fluxo nos 3 ViewModels. | rejeitado (baixo, improvável, correção maior) |
| 5 | Não-médico → Busca inclui contas sem `profiles` | `false` | `isCurrentUserDoctor()` usa `decodeSingle` em `profiles`: sem linha lança e o Login mostra erro; `role` só admite `patient`/`doctor` (CHECK). | rejeitado |
| 6 | Duplo toque em "Criar conta" duplica chamada | `false` | `viewModelScope` roda em `Main.immediate`: `isLoading = true` é gravado antes da primeira suspensão, e `isSubmitEnabled` exige `!isLoading`. | rejeitado |
| 7 | E-mail em caixa diferente diverge entre `auth.users` e `patients` | `false` | `patients.email` é copiado de `auth.users` dentro de `complete_registration`, nunca do payload. | rejeitado |
| 8 | Aba Paciente/Médico com credencial do outro papel não tratada | `false` | Login roteia pelo papel real em `profiles`; a aba é só visual (comportamento definido na 1.1). | rejeitado |
| 9 | Órfão Auth se `deleteUser` também falhar | `false` | Caso já registrado em log (`console.error`) e igual ao `register-doctor` da 1.1. | rejeitado |
| 10 | Limites de tamanho de nome/senha, `isNotBlank` vs `trim()`, 400 vs 409 em `CONFLICT` do RPC, teclado de e-mail/toggle de senha/anúncio de erro, índice em `patients.email`, sem down-migration | `low` | Reais porém cosméticos ou sem consumidor hoje; correção exige guards/parâmetros novos. | rejeitado |
| 11 | Busca sem logout / sem saída | `low` | Busca é casca declarada pela spec (Intent); logout não pertence a nenhuma história do Épico 1. | rejeitado |
| 12 | Navegação do fluxo paciente sem teste de UI | `low` | Emissões de navegação cobertas nos ViewModels; sem infraestrutura de UI test (decisão registrada com Winston). | rejeitado |
| 13 | Deteção de e-mail duplicado por substring na mensagem do Auth (cópia em `register-patient`) | `medium` (não verificado) | Padrão herdado da 1.1 e agora copiado; se o texto do Supabase mudar, vira `UNEXPECTED`. | **defer** |
| 14 | Sem testes automatizados de servidor (`complete_registration`, RLS de `patients`, Edge Functions) | `medium` | Verificado apenas por chamadas reais ao projeto hospedado (201/409/400 + regressão do médico); sem harness pgTAP/Deno. | **defer** |
| 15 | Endpoint público cria conta com `email_confirm: true` sem verificar posse do e-mail; reminders da 3.1 poderiam ir a um terceiro | `medium` (não verificado) | Decisão de design herdada da 1.1 (sem etapa de aprovação/validação); relevante a partir da Story 3.1. | **defer** |

## Design Notes

**Nota de correção (Story 6.1, 2026-09-23):** o achado #8 do Review Triage Log acima (aba Paciente/Médico do Login é só visual; o papel real sempre vence) foi revertido a pedido do usuário, que testou o comportamento na prática e achou a experiência ruim. A partir da Story 6.1, a aba selecionada passa a ser obrigatória: um login com credenciais do outro papel é negado com mensagem clara, não redirecionado. Ver `spec-6-1-login-validacao-senha-papel.md`.

`patients.email` duplica `auth.users.email` por decisão do Épico 1 (usado por lembretes da Story 3.1); copiado uma vez na criação, sem troca de e-mail no MVP. `register-patient` fica autocontido (sem `_shared`) para não tocar `register-doctor`, já em produção.

## Verification

**Commands:**
- `./gradlew assembleDebug testDebugUnitTest` (com `JAVA_HOME` do JBR do Android Studio) -- expected: `BUILD SUCCESSFUL`, todos os testes passam
- `npx supabase db push` e `npx supabase functions deploy register-patient --project-ref vuqvizzkdeiseyunjrms` -- expected: migração `0002` aplicada, função publicada
- Chamada direta a `register-patient` (e-mail descartável) -- expected: 201 e linhas em `profiles`/`patients`

**Manual checks (if no CLI):**
- No celular: Criar conta -> Sou paciente -> cadastro -> Busca; logar como paciente cai na Busca; logar como médico continua em Minha Agenda
