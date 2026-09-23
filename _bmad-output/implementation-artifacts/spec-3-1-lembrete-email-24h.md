---
title: 'Paciente recebe lembrete de consulta por e-mail 24h antes'
type: 'feature'
created: '2026-09-22'
status: 'done'
baseline_commit: '5da9077e22f9b9ed12c51c781b8048e662472040'
route: 'dispatch'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** O Paciente agenda mas nada o lembra da consulta; esquecer é o motivo mais comum de falta, e o sistema só grava eventos (Story 2.3-2.5), sem enviar nada (RF-10).

**Approach:** Um job `pg_cron` a cada 5 minutos chama a Edge Function `send-reminders`, que reivindica de forma atômica as consultas confirmadas que entraram nas últimas 24h e ainda não foram lembradas, e envia um e-mail em português via Resend a cada paciente, sem que a falha de um envio afete os outros (NFR2).

## Boundaries & Constraints

**Always:**
- Lembrete só para consulta `confirmed` com `reminder_sent_at IS NULL` e `start_time` em `(now(), now() + 24h]`. É por "vencimento" (não por janela de 5 min): um job atrasado ou um envio que falhou é retomado nas execuções seguintes até o horário da consulta; consulta cancelada nunca é lembrada. Lembra só o Paciente (o médico já recebe eventos, AD-12).
- Nova migração `0008`: coluna `appointments.reminder_sent_at timestamptz`, índice parcial para a busca, trigger que zera `reminder_sent_at` quando `start_time` muda, funções `claim_due_reminders(p_limit int)` e `release_reminder(p_id uuid, p_claimed_at timestamptz)` (`SECURITY DEFINER`, `SET search_path = public, pg_temp`, `EXECUTE` só para `service_role`), extensões `pg_cron` e `pg_net`, e o job `send-reminders` (`*/5 * * * *`).
- `claim_due_reminders` reivindica numa única instrução (`UPDATE ... SET reminder_sent_at = now() WHERE id IN (SELECT ... FOR UPDATE SKIP LOCKED) RETURNING ...`, limite por execução) e devolve o necessário ao e-mail: id, e-mail e nome do paciente, nome/especialidade/cidade/bairro do médico, convênio e `start_time`. Execuções sobrepostas nunca reivindicam a mesma consulta (AD-5). Se o envio falha, `release_reminder` devolve `reminder_sent_at` a NULL (só se ainda for o valor reivindicado) para nova tentativa.
- A Edge Function `send-reminders` (Deno, `service_role` só do ambiente da função, nunca no app) só aceita chamadas com o cabeçalho `x-cron-secret` igual ao segredo `REMINDER_CRON_SECRET` (comparação em tempo constante; senão 401 sem tocar em nada). Processa cada consulta em `try/catch` isolado: uma falha é registrada (`console.error` com o id da consulta e o motivo, sem e-mail nem outro dado pessoal) e não interrompe as demais; responde 200 com contagens `{ due, sent, failed }`.
- E-mail via Resend (`RESEND_API_KEY`, remetente `REMINDER_FROM`, padrão `Agenda Médica <onboarding@resend.dev>`), assunto e corpo em pt-BR (HTML e texto) com médico, especialidade, data/hora em `America/Sao_Paulo`, local (bairro, cidade) e convênio, avisando que a partir de agora não é mais possível cancelar/reagendar pelo app. Sem `RESEND_API_KEY` o envio conta como falha (nada é marcado como enviado).
- Só para teste, o corpo da requisição aceita `sendMode`: `live` (padrão), `simulate-success` ou `simulate-failure`; os modos simulados nunca chamam o Resend (sucesso simulado; falha simulada para pacientes cujo e-mail começa com `fail-`) e exigem o mesmo segredo.
- O segredo do job vem do Vault do Supabase (`reminder_cron_secret`), nunca literal na migração nem no repositório; `RESEND_API_KEY` e `REMINDER_CRON_SECRET` ficam nos segredos da Edge Function.

**Never:**
- Não enviar push, SMS nem lembrete ao médico; não criar tela nem alterar o app Android. Não editar `0001`-`0007`. Não versionar chaves nem segredos.
- Não marcar `reminder_sent_at` de forma definitiva sem envio bem-sucedido; não reenviar lembrete já enviado.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Consulta a ≤ 24h | `confirmed`, sem lembrete, início em (agora, agora+24h] | E-mail enviado ao paciente com os detalhes; `reminder_sent_at` preenchido | N/A |
| Ainda não é hora | início a > 24h | Nada enviado; `reminder_sent_at` segue nulo | N/A |
| Já lembrada | `reminder_sent_at` preenchido | Nunca reenviada | N/A |
| Cancelada | `status = cancelled` | Nenhum lembrete | N/A |
| Já passou | início ≤ agora | Nenhum lembrete | N/A |
| Execuções simultâneas | várias chamadas ao mesmo tempo | Cada consulta reivindicada por uma só; um único e-mail | N/A |
| Falha de envio | Resend indisponível/recusa/sem chave | Erro registrado; consulta volta a "não enviada" e é tentada na próxima execução; as demais da execução seguem e são enviadas | Resposta 200 com `failed` |
| Reagendada | `start_time` muda | `reminder_sent_at` volta a nulo (novo horário, novo lembrete) | N/A |
| Chamada não autorizada | sem segredo ou segredo errado | 401, nada reivindicado | Sem detalhe |
| Job agendado | migração aplicada | Job `send-reminders` a cada 5 min chamando a função com o segredo do Vault | N/A |

</frozen-after-approval>

## Code Map

- `supabase/migrations/0005_appointments.sql` -- `appointments` (status, `start_time`), trigger `sync_booked_slots`, `notification_events`; `0006_cancel_reschedule.sql` -- `assert_slot_bookable`/`cancel_appointment`/`reschedule_appointment`; ambas com o padrão de função `SECURITY DEFINER` e revoke/grant; NÃO editar
- `supabase/migrations/0002_patients.sql`, `0003_doctor_location.sql` -- `patients.email/name`, `doctors.name/specialty/city/neighborhood`
- `supabase/functions/register-patient/index.ts`, `register-doctor/index.ts` -- padrão de Edge Function Deno (`createClient` com `service_role` do ambiente, respostas JSON, `console.error`)
- `supabase/config.toml` -- `[functions.*] verify_jwt`; adicionar `[functions.send-reminders] verify_jwt = false`
- `supabase/tests/cancel-reschedule-test.mjs`, `concurrency-test.mjs` -- padrão de script contra o projeto hospedado (`sql()` via `npx supabase db query --linked`, helpers, limpeza por prefixo)
- `package.json`, `local.properties` -- scripts npm dos testes; `local.properties` (git-ignored) guarda URL/chave e agora `REMINDER_CRON_SECRET` para o script
- `_bmad-output/planning-artifacts/architecture/architecture-AgendaMedica-2026-09-17/ARCHITECTURE-SPINE.md` -- AD-5 (job e reivindicação atômica) e AD-12

## Tasks & Acceptance

**Execution:**
- [x] `supabase/migrations/0008_reminders.sql` -- coluna, índice parcial, trigger de reset por reagendamento, `claim_due_reminders`/`release_reminder`, extensões, job `pg_cron` lendo o segredo do Vault -- AD-5, AD-12
- [x] `supabase/functions/send-reminders/index.ts` + `supabase/config.toml` -- autenticação por `x-cron-secret`, laço com isolamento por consulta, Resend, modos de teste, e-mail pt-BR (HTML e texto) -- FR10, NFR2
- [x] `supabase/tests/reminders-test.mjs` (+ script npm) -- prova contra o projeto hospedado: 401 sem segredo; só as devidas são enviadas (não devida, já lembrada, cancelada, passada ficam intactas); segunda chamada não reenvia; 5 chamadas simultâneas enviam cada consulta uma só vez; falha isolada não impede as demais e desfaz a reivindicação; reagendar zera o lembrete; o job existe com `*/5 * * * *` e sem segredo literal; limpa os dados de teste
- [x] Preparação do ambiente (orquestrador, sem expor segredos): gerar `REMINDER_CRON_SECRET`, gravá-lo nos segredos da função, no Vault e no `local.properties`; implantar a função

**Acceptance Criteria:**
- Given uma consulta confirmada a 24h ou menos do início e sem lembrete, when o job roda, then o paciente recebe o e-mail com os detalhes e `reminder_sent_at` é preenchido (FR10)
- Given um lembrete já enviado, when o job roda de novo, then não há reenvio (FR10)
- Given uma consulta cancelada, when o job roda, then nenhum lembrete é enviado (FR10)
- Given uma falha de envio, when ocorre, then o erro é registrado e as demais consultas da mesma execução seguem (NFR2)

## Implementation Notes

## Spec Change Log

## Review Triage Log

Três revisores paralelos (correção/segurança, testes/edge cases, consistência arquitetural). Nenhum blocker.

| # | Achado | Severidade | Ação |
|---|--------|-----------|------|
| 1 | `reminders-test.mjs` roda em modos simulados que reivindicam qualquer consulta devida do projeto, não só as do teste, e podiam marcar uma consulta real como lembrada sem enviar e-mail | major | Corrigido: `assertNoForeignDueAppointments()` aborta o teste antes de cada chamada que reivindica se achar consulta devida real fora do prefixo do teste; comprovado (achou e abortou sobre lixo de execução anterior, depois passou limpo) |
| 2 | Decisão pendente da 1.2 ("decidir antes de enviar e-mails na 3.1 se basta o risco de e-mail não verificado") nunca foi registrada nesta spec | major | Corrigido: decisão registrada nas Design Notes (aceitar o risco, projeto de portfólio) e `deferred-work.md` marcado como resolvido |
| 3 | Assunto do e-mail sempre dizia "é amanhã", impreciso para consultas a poucas horas (janela é até 24h, não só amanhã) | minor | Corrigido: assunto trocado para "está próxima" |
| 4 | `doctor_name` (auto-cadastrado) interpolado sem sanitização no assunto do e-mail (corpo HTML já escapava, assunto não) | minor | Corrigido: `sanitizeForSubject` remove caracteres de controle antes de compor o assunto |
| 5 | AD-5 em ARCHITECTURE-SPINE.md e "Seleção"/"Reivindicação atômica" em epic-3-context.md ainda descreviam a janela fixa `[24h,24h5min)` original, não o desvio "por vencimento" já aprovado nesta spec | minor | Corrigido: ambos os documentos atualizados para refletir a reivindicação por vencimento em lote |
| 6 | Atribuição do Code Map dizia que `0006_cancel_reschedule.sql` também define `sync_booked_slots`/`notification_events` (na verdade só `0005`) | nit | Corrigido no Code Map |
| 7 | Falha dupla (envio falha E `release_reminder` também falha) deixa a consulta presa sem lembrete futuro, sem recuperação automática | minor | Adiado: registrado em `deferred-work.md`, fora da matriz de IO congelada, recuperação manual hoje |
| 8 | Cobertura automatizada não exercita `buildEmail`/Resend (só os modos simulados); formatação real do e-mail só tem checagem manual | minor | Adiado: já coberto pela checagem manual da spec (Verification); registrado em `deferred-work.md` |
| 9 | Guarda contra poluição de teste (item 1) ainda depende de rodar num instante sem consulta real pendente; não há filtro nativo por dado de teste em `claim_due_reminders` | nit | Adiado: mitigado pela guarda; endurecimento futuro registrado em `deferred-work.md` |
| 10 | Limites exatos (`= now()+24h`, `= now()`) não testados no instante literal, só com margem | nit | Aceito: mesma limitação já existente em `cancel-reschedule-test.mjs` (relógio real) |
| 11 | CAS de `release_reminder` não tem teste específico de corrida com um release tardio/obsoleto | nit | Aceito: coberto estruturalmente por `SKIP LOCKED`, baixo valor para o esforço |

## Design Notes

Desvio consciente do AD-5: em vez de uma janela fixa `[24h, 24h5min)`, o lembrete é por vencimento (`start_time <= now()+24h`). Uma janela de 5 minutos perde o lembrete se o job atrasar ou o envio falhar uma vez; por vencimento, a reivindicação atômica (`SKIP LOCKED`) continua garantindo um e-mail por consulta e um envio que falhou é retomado. A reivindicação roda em SQL (uma instrução) e não N atualizações a partir da função, com a mesma garantia. Refletido de volta em ARCHITECTURE-SPINE.md (AD-5) e epic-3-context.md. Pré-requisito do usuário para o teste real: conta no Resend e `npx supabase secrets set RESEND_API_KEY=...` (rodar no próprio terminal); sem domínio verificado o Resend só entrega para o e-mail dono da conta e a partir de `onboarding@resend.dev`, então o teste ao vivo usa uma consulta cujo paciente tenha esse e-mail.

Decisão sobre o item adiado da 1.2 ("decidir se basta o risco de e-mail não verificado ou se cabe confirmação/descadastro" antes de enviar e-mails na 3.1): aceito o risco tal como está — projeto de portfólio, sem etapa de confirmação de e-mail nem opção de descadastro nesta história. `deferred-work.md` atualizado para registrar a decisão.

## Verification

**Commands:**
- `npx supabase db push` -- expected: migração `0008` aplicada (revisar o SQL antes; extensões `pg_cron`/`pg_net` habilitadas)
- `npx supabase functions deploy send-reminders --project-ref vuqvizzkdeiseyunjrms --use-api` -- expected: função publicada
- `node supabase/tests/reminders-test.mjs` -- expected: ALL CHECKS PASSED; dados de teste removidos

**Manual checks (if no CLI):**
- Com a chave do Resend definida: inserir por SQL uma consulta confirmada a ~23h30m para um paciente com o e-mail dono da conta Resend, esperar o job (até 5 min) ou chamar a função, e conferir o e-mail recebido e `reminder_sent_at` preenchido
