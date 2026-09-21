---
title: 'Paciente agenda uma consulta sem conflito de horário'
type: 'feature'
created: '2026-09-21'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: 'bee3838e3991eb115a4851becc206eddd53d5168'
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** O Paciente vê a agenda do médico mas não consegue agendar, e nada impede dois pacientes de disputarem o mesmo horário. Esta é a regra central do produto (RF-6, RF-7): exatamente um vence, mesmo sob concorrência.

**Approach:** Tabela `appointments` com índice único parcial `(doctor_id, start_time) WHERE status = 'confirmed'` como garantia final, função Postgres `book_appointment` como única via de escrita, trigger que mantém `booked_slots`, e no app o botão "Confirmar agendamento" no Detalhe, a tela de Confirmação e a grade atualizada por Realtime. Um script de concorrência prova a regra contra o banco real.

## Boundaries & Constraints

**Always:**
- Escrita só por `book_appointment(p_doctor_id, p_start_time timestamptz, p_insurance text)`, `SECURITY DEFINER`, `SET search_path = public, pg_temp`, `EXECUTE` só para `authenticated`. A identidade vem de `auth.uid()`; a função não recebe `patient_id`. RLS de `appointments`: `SELECT` do paciente e do médico donos, nenhuma política de `INSERT`/`UPDATE`/`DELETE` (AD-1, AD-10).
- Validações na função, com erros `CONFLICT:`/`INVALID:`/`FORBIDDEN:` (AD-9): sem sessão ou papel diferente de `patient` -> `FORBIDDEN:`; médico inexistente, horário fora da agenda do médico (dia da semana e faixa em `America/Sao_Paulo`, alinhado a 15 min, fim exclusivo) ou convênio que o médico não aceita -> `INVALID:`; início a menos de 48h de `now()` -> `CONFLICT: lead_time` (exatamente 48h é aceito); violação do índice único (SQLSTATE 23505) -> `CONFLICT: slot_taken`. Comparações `timestamptz` contra `timestamptz` (AD-8).
- `status` é ENUM `confirmed`/`cancelled` (cancelar nunca apaga). O trigger `sync_booked_slots` (AFTER INSERT OR UPDATE OF status, start_time) mantém `booked_slots` (insere ao confirmar; remove quando deixa de ser `confirmed` ou muda de horário), `SECURITY DEFINER`, `ON CONFLICT DO NOTHING`.
- Na mesma transação, `book_appointment` grava um evento `new_appointment` para o médico em `notification_events` (`id`, `recipient_id`, `event_type` in `new_appointment`/`cancellation`/`reschedule`, `appointment_id`, `created_at`, `delivered_at`), sem leitura por clientes (RLS ligada, sem políticas) e sem qualquer envio real (AD-12).
- Realtime (`realtime-kt`) assina `booked_slots` filtrado por `doctor_id` enquanto o Detalhe está aberto e é cancelado ao sair; `booked_slots` entra na publicação `supabase_realtime`. Rejeição por conflito também refaz a leitura de `booked_slots` (não depende só do push).
- No Detalhe o Paciente escolhe um dos convênios aceitos pelo médico (chips; já selecionado se o médico aceita só um). "Confirmar agendamento" só habilita com médico, dia, horário e convênio; durante o envio fica desabilitado (sem duplo envio). Mensagem de conflito exata: "Este horário acabou de ser reservado, escolha outro."; `lead_time` e demais falhas mostram mensagem clara/genérica, nunca texto técnico (AD-9).
- Confirmação mostra médico, especialidade, data/hora (São Paulo) e convênio; o "!" só em "Consulta agendada!". Só `data/` fala com o Supabase (AD-2).

**Never:**
- Não implementar cancelar, reagendar, Minhas Consultas nem a lista de consultas (Stories 2.4/2.5); a Confirmação tem "Voltar à Busca" (o "Ver minhas consultas" chega com a 2.4).
- Não criar `send-reminders`, `reminder_sent_at` nem entrega de notificação (Épico 3). Não editar `0001`-`0004`.
- Nenhum cliente escreve em `appointments`/`booked_slots`/`notification_events` diretamente.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Agendamento válido | médico, dia, horário ≥ 48h e convênio aceito | Consulta `confirmed`; horário entra em `booked_slots`; evento `new_appointment` para o médico; tela de Confirmação com o resumo | N/A |
| Botão incompleto | falta médico, dia, horário ou convênio | "Confirmar agendamento" desabilitado | N/A |
| Concorrência | várias requisições simultâneas ao mesmo médico e horário | Exatamente uma aceita; as demais `CONFLICT: slot_taken` | "Este horário acabou de ser reservado, escolha outro." |
| Grade após conflito | rejeição por conflito | Horário passa a "Ocupado", seleção limpa, sem recarregar a tela | N/A |
| Realtime | outro paciente reserva com o Detalhe aberto | Horário vira "Ocupado" sem ação; se era o selecionado, seleção limpa com aviso | N/A |
| Menos de 48h | chamada direta com início a < 48h | Rejeitada `CONFLICT: lead_time`; exatamente 48h é aceito | Mensagem clara |
| Fora da agenda | dia/horário que o médico não atende ou fora dos 15 min | Rejeitada `INVALID:` | Mensagem genérica |
| Convênio inválido | convênio que o médico não aceita | Rejeitada `INVALID:` | Mensagem genérica |
| Papel/identidade | médico ou sem sessão chamando; `patient_id` alheio | `FORBIDDEN:`; a identidade é sempre `auth.uid()` | Sem detalhe técnico |
| Escrita direta | cliente tenta INSERT/UPDATE/DELETE em `appointments` | Negado pela RLS | N/A |
| Cancelamento libera | consulta vira `cancelled` (SQL) | Linha some de `booked_slots` e o horário pode ser reservado de novo | N/A |
| Falha de rede | Supabase inacessível ao confirmar | Mensagem genérica, sem crash, sem navegar | Bucket `UNEXPECTED` |

</frozen-after-approval>

## Code Map

- `supabase/migrations/0004_booked_slots.sql` -- `booked_slots` (PK `doctor_id, start_time`, RLS `SELECT` autenticado, sem escrita); NÃO editar. O trigger da nova migração passa a ser o único escritor
- `supabase/migrations/0001_init.sql`, `0002`, `0003` -- padrão de função `SECURITY DEFINER` com `search_path` fixo, `profiles.role`, `doctors.insurances`, `doctor_schedules (weekday 0=domingo, start_time, end_time)`; NÃO editar
- `app/.../domain/agenda/AgendaSlots.kt` -- regra de 48h e slots (`slotsDoDia`, `ANTECEDENCIA_MINIMA_HORAS`), `FUSO_AGENDA`; reutilizar
- `app/.../ui/patient/{DetalheMedicoScreen,DetalheMedicoViewModel}.kt` -- grade com seleção (só destaca hoje); ganha convênio, botão, Realtime e navegação à Confirmação
- `app/.../data/repository/{DoctorRepository,AppError}.kt` -- leitura de `booked_slots`; `toAppError()` (lê o prefixo de `RestException.error`)
- `app/.../ui/navigation/AgendaMedicaNavHost.kt` -- rota `medico/{doctorId}` existe; adicionar a Confirmação
- `app/.../data/remote/SupabaseClientProvider.kt` -- `Realtime` já instalado. supabase-kt 3.1.4: fontes em `~/.gradle/caches/modules-2/files-2.1/io.github.jan-tennert.supabase/realtime-kt-android-debug/3.1.4/*/*-sources.jar` (`postgresChangeFlow`, `FilterOperator`)
- `app/src/test/.../ui/patient/DetalheMedicoViewModelTest.kt`, `domain/agenda/AgendaSlotsTest.kt` -- padrão de teste (um `UnconfinedTestDispatcher` compartilhado em `setMain` e `runTest`)
- `package.json`, `local.properties` -- Node já disponível para scripts; URL/chave vêm de `local.properties` (git-ignored)

## Tasks & Acceptance

**Execution:**
- [x] `supabase/migrations/0005_appointments.sql` -- ENUM, `appointments` (`patient_id`, `doctor_id`, `start_time timestamptz`, `status`, `insurance`, `created_at`), índice único parcial, RLS de leitura, `notification_events`, trigger `sync_booked_slots`, função `book_appointment`, `booked_slots` na publicação `supabase_realtime` -- AD-1, AD-8, AD-10, AD-12
- [x] `data/repository/AppointmentRepository.kt` -- `bookAppointment(doctorId, start, insurance)` com resultado tipado (sucesso, horário reservado, antecedência, falha genérica) a partir do prefixo/`token` do erro; assinatura Realtime de `booked_slots` do médico como `Flow` -- AD-2, AD-9
- [x] `ui/patient/{DetalheMedicoViewModel,DetalheMedicoScreen}.kt` -- chips de convênio, botão "Confirmar agendamento" (estados desabilitado/enviando), tratamento de conflito (mensagem, refetch, seleção limpa), aplicação dos eventos Realtime na grade
- [x] `ui/patient/{ConfirmacaoScreen}.kt` + `AgendaMedicaNavHost.kt` -- resumo e "Voltar à Busca"; rota após o sucesso sem voltar ao Detalhe pelo botão voltar
- [x] `supabase/tests/concurrency-test.mjs` (+ script npm) -- cria pacientes de teste via `register-patient`, dispara requisições simultâneas de `book_appointment` ao mesmo horário em várias rodadas e exige exatamente 1 sucesso por rodada e `CONFLICT: slot_taken` nas demais; confere no banco 1 `confirmed` por horário; limpa os dados de teste (`npx supabase db query --linked`)
- [x] `app/src/test/...` -- `DetalheMedicoViewModelTest` (habilitação do botão, sucesso, conflito, `lead_time`, falha de rede, evento Realtime) e teste do resultado tipado do `AppointmentRepository`

**Acceptance Criteria:**
- Given médico, dia, horário e convênio válidos, when toco em "Confirmar agendamento", then a consulta é criada e vejo a Confirmação com o resumo (FR6)
- Given duas requisições simultâneas ao mesmo horário, when chegam juntas, then exatamente uma é aceita e a outra recebe "Este horário acabou de ser reservado, escolha outro." (FR7, MS-2), provado pelo script de concorrência contra o banco
- Given uma rejeição por conflito, when ocorre, then a grade mostra o horário como "Ocupado" sem eu recarregar (FR7, AD-10)
- Given uma consulta criada, when a função conclui, then existe um evento `new_appointment` para o médico, sem nenhum envio real (AD-12)
- Given qualquer chamada direta ou de outro papel, when tenta contornar a regra, then é rejeitada no banco (48h, agenda, convênio, papel, RLS)

## Implementation Notes

**2026-09-21 — implementação concluída.** Migração `0005_appointments.sql` (ENUM `appointment_status`, `appointments`, índice único parcial `(doctor_id, start_time) WHERE status = 'confirmed'`, RLS de leitura para paciente/médico donos e escrita revogada, `notification_events` sem acesso de cliente, trigger `sync_booked_slots`, função `book_appointment`, `booked_slots` na publicação `supabase_realtime`), `AppointmentRepository` (`bookAppointment` com resultado tipado + Realtime como `Flow`), Detalhe com convênio/botão/conflito/Realtime, `ConfirmacaoScreen` e `supabase/tests/concurrency-test.mjs` (`npm run test:concurrency`).

- **Verificação independente (orquestrador):** revisado o SQL linha a linha (identidade só por `auth.uid()`, `search_path` fixo, ordem FORBIDDEN -> INVALID -> CONFLICT, 48h inclusivo, índice único como garantia final). Conferido nas fontes do postgrest-kt 3.1.4 que o `RestException.error` de um erro de RPC é o `message` do PostgREST (`CONFLICT: slot_taken`), que o `toAppError`/`toBookingResult` reconhece. Rodei `node supabase/tests/concurrency-test.mjs` contra o projeto hospedado: 8 requisições simultâneas x 5 rodadas -> exatamente 1 sucesso e 7 `CONFLICT: slot_taken` por rodada; 1 `confirmed` por horário; 5 linhas em `booked_slots`; 5 eventos `new_appointment` não entregues; `lead_time`, convênio, alinhamento, médico inexistente, chamador médico e anon rejeitados; INSERT direto -> 403; cancelar remove de `booked_slots` e o horário volta a ser reservável; usuários de teste removidos por cascata.
- A migração apaga `booked_slots` inteira (só continha a linha de teste da Story 2.2; a partir daqui a tabela é derivada de `appointments`).
- **Auditoria da matriz:** todas as linhas com teste (ViewModel, `BookingResultTest`, script de concorrência). Só no aparelho: Realtime ponta a ponta na tela e a renderização da Confirmação.
- `./gradlew assembleDebug testDebugUnitTest`: BUILD SUCCESSFUL, 114 testes, 0 falhas.

**2026-09-21 — patches do gate de revisão (ver Review Triage Log).** `toChange` ignora eventos de outro médico (DELETE do Realtime não é filtrável), canal Realtime com nome único por coleta, `Resync` a cada assinatura/reconexão, aviso de "reservado" suprimido durante o próprio envio, testes de `toChange` e do formato de data da Confirmação. Resultado final: BUILD SUCCESSFUL, 119 testes, 0 falhas; script de concorrência reexecutado limpo e banco sem dados de teste.

## Spec Change Log

## Review Triage Log

Três revisores; achados verificados contra o código, o SQL e a API do Realtime. Nenhum `intent_gap`/`bad_spec`; sem loopback.

| # | Achado | Veredito | Evidência | Rota |
|---|--------|----------|-----------|------|
| 1 | DELETE do Realtime não é filtrável por coluna: a exclusão de outro médico no mesmo instante liberaria o "Ocupado" deste | `medium` | O filtro `doctor_id=eq` só vale para INSERT/UPDATE; `toChange` não conferia o médico do evento. | **patch** — `toChange(doctorId)` ignora evento de outro médico (+ testes) |
| 2 | Reentrada rápida da tela reutiliza o canal de mesmo nome que o teardown anterior ainda remove; as atualizações ao vivo param em silêncio | `medium` | Nome fixo `booked-slots-<id>` e teardown assíncrono em `NonCancellable`; rotação recria a composição. | **patch** — canal com nome único por coleta |
| 3 | Sem ressincronização após assinar/reconectar: eventos entre a carga e o join, ou durante uma queda, se perdem | `medium` | Assinatura era iniciada sem nova leitura; `subscribe()` não bloqueia por padrão. | **patch** — emite `Resync` a cada `SUBSCRIBED` do canal |
| 4 | Push da própria reserva pode chegar antes da resposta do RPC e mostrar "acabou de ser reservado" | `low` | `onSlotChange` limpava a seleção mesmo com `isSubmitting`. | **patch** — ignora enquanto envia |
| 5 | Mapeamento Realtime (`toChange`) e `formatarDataHora` sem teste | `medium` | Só o ViewModel com fluxo falso era testado. | **patch** — `RealtimeChangeTest`, `ConfirmacaoFormatTest` |
| 6 | Reenvio depois de uma resposta perdida (rede) devolve `slot_taken` para a própria reserva | `medium` | Real sob rede instável, sem chave de idempotência; a correção (tratar `slot_taken` do próprio paciente como sucesso) adiciona comportamento novo não previsto na spec. | **defer** |
| 7 | Sem teste de `bookAppointment`/`observeBookedSlots` ponta a ponta, de rotas/`popUpTo` e do Realtime real | `medium` | O RPC é provado pelo script de concorrência contra o banco real; o cliente Kotlin e o Realtime só no aparelho. | **defer** |
| 8 | `p_insurance = any(NULL)`; slot que ultrapassa `end_time`; trigger sem `doctor_id` | `false`/`low` | `doctors.insurances` é `NOT NULL`; início `< end_time` é a definição da spec (cliente e servidor iguais); `doctor_id` nunca muda (reagendar mantém o médico). | rejeitado |
| 9 | `REPLICA IDENTITY FULL`, política/RLS de `booked_slots` "ausentes" | `false` | A PK `(doctor_id, start_time)` já vai no `oldRecord`; a política `SELECT` está na 0004 e a publicação na 0005. | rejeitado |
| 10 | Convênio como texto livre; sem regra de sobreposição por paciente; sem limite de taxa; médico não vê o paciente; sem retenção de `notification_events` | `low` | Fora da spec ou de outras histórias (2.5, Épico 3). | rejeitado |
| 11 | `ocupados` como `var`; refetch pode sobrescrever delta; reload reseta convênio; Confirmação com argumentos vazios/`Uri.encode`; sem `TopAppBar`/scroll | `low` | Todas as mutações ocorrem na thread principal; efeitos transitórios ou só alcançáveis por navegação interna correta. | rejeitado |
| 12 | `delete from booked_slots` incondicional na migração; script só roda contra o projeto hospedado; `sql()` por shell | `low` | Migração já aplicada e a tabela só tinha a linha de teste da 2.2; o script rodou limpo duas vezes. | rejeitado |

## Design Notes

O índice único, não a função, é a garantia final: a função só traduz a violação em `CONFLICT: slot_taken`. O script de concorrência roda contra o projeto hospedado (sem Docker); `notification_events` nasce aqui porque o critério de aceite exige registrar o evento, e a Story 3.2 só completa `cancellation`/`reschedule`. Uma linha de teste em `booked_slots` (Dra. Busca Pinheiros, 2026-09-29 10:00) foi deixada pela Story 2.2 e pode ser apagada.

## Verification

**Commands:**
- `./gradlew assembleDebug testDebugUnitTest` (com `JAVA_HOME` do JBR do Android Studio) -- expected: `BUILD SUCCESSFUL`, todos os testes passam
- `npx supabase db push` -- expected: migração `0005` aplicada
- `node supabase/tests/concurrency-test.mjs` -- expected: em todas as rodadas exatamente 1 sucesso e o restante `slot_taken`; 1 consulta `confirmed` por horário; dados de teste removidos
- `npx supabase db query --linked` -- expected: `update appointments set status = 'cancelled'` remove a linha de `booked_slots`; INSERT direto de cliente autenticado é negado

**Manual checks (if no CLI):**
- No celular, com dois usuários (ou o script rodando junto): agendar, ver a Confirmação, tentar o mesmo horário pelo outro usuário e ver a mensagem e o "Ocupado" sem recarregar
