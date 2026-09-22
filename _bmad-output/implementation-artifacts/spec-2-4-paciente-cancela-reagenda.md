---
title: 'Paciente cancela ou reagenda uma consulta'
type: 'feature'
created: '2026-09-22'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: '0f7cdeb65e156b45b947b2eb654dab926e7d1f4e'
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Depois de agendar, o Paciente não vê suas consultas nem consegue cancelar ou reagendar, e nada impede alterar uma consulta que já está a menos de 24h (RF-8, RF-9).

**Approach:** Funções Postgres `cancel_appointment` e `reschedule_appointment` (única via de escrita, bloqueio de 24h no banco, evento para a outra parte), tela Minhas Consultas com cancelamento inline, e o Detalhe reaproveitado em modo Reagendar que atualiza a mesma consulta.

## Boundaries & Constraints

**Always:**
- Funções `SECURITY DEFINER`, `SET search_path = public, pg_temp`, `EXECUTE` só para `authenticated`; identidade só por `auth.uid()`, que deve ser o paciente ou o médico da consulta (senão `FORBIDDEN:`). Servem os dois lados; a UI do médico é a Story 2.5. Trancam a linha da consulta (`FOR UPDATE`) para serializar cancelar/reagendar.
- Só consulta `confirmed` pode mudar (senão `INVALID:`). Alterar exige que o início atual esteja a 24h ou mais de `now()` (exatamente 24h é permitido); a menos de 24h -> `CONFLICT: cancel_window`, sem contorno por nenhum papel. Comparações `timestamptz` contra `timestamptz` (AD-8).
- `cancel_appointment(p_appointment_id)` muda para `cancelled` (nunca apaga). `reschedule_appointment(p_appointment_id, p_new_start_time)` atualiza a MESMA linha (mesmo id, mesmo médico e convênio) e aplica as regras do agendamento ao novo horário: alinhado a 15 min, dentro da agenda do médico, a 48h ou mais (`CONFLICT: lead_time`), diferente do atual (`INVALID:`); colisão no índice único -> `CONFLICT: slot_taken` e a consulta fica como estava. As validações de horário viram uma função interna única reutilizada por `book_appointment` e `reschedule_appointment` (recriando `book_appointment` na nova migração sem mudar o comportamento; o script de concorrência da 2.3 continua passando).
- O trigger `sync_booked_slots` já libera/troca o horário em `booked_slots`. Na mesma transação cada função grava em `notification_events` o evento `cancellation`/`reschedule` para a OUTRA parte (paciente cancela -> médico; médico cancela -> paciente); sem envio real (AD-12).
- Minhas Consultas lista as consultas `confirmed` futuras do próprio paciente (RLS), por data crescente, cada card com médico, especialidade, data/hora (São Paulo), convênio e selo "Confirmada"/"Bloqueada". A menos de 24h, "Cancelar" e "Reagendar" ficam visíveis porém desabilitados, com a nota "Bloqueado: faltam menos de 24h — não é mais possível cancelar ou reagendar." (estado por texto, não só cor). "Cancelar" abre confirmação inline no card (par Sim/Não), nunca diálogo modal; sem duplo envio. Vazio: "Você ainda não tem consultas agendadas." + "+ Nova consulta" (volta à Busca). Carregamento com indicador; falha com mensagem genérica e "Tentar novamente" (AD-9).
- Reagendar reutiliza o Detalhe: cabeçalho "Reagendar consulta", botão "Confirmar novo horário", sem escolha de convênio (mantém o original), voltar retorna a Minhas Consultas; sucesso volta à lista já atualizada. Conflito -> "Este horário acabou de ser reservado, escolha outro." e a grade atualiza como na 2.3.
- Acesso: ação "Minhas consultas" na Busca e "Ver minhas consultas" na Confirmação (que mantém "Voltar à Busca"). Só `data/` fala com o Supabase (AD-2).

**Never:**
- Não implementar a UI do médico (Minha Agenda com Cancelar/Reagendar, Story 2.5), histórico de consultas passadas/canceladas, lembretes ou entrega de notificação (Épico 3). Não editar `0001`-`0005`.
- Nenhum cliente faz `UPDATE`/`DELETE` direto em `appointments`; nenhum papel ignora a janela de 24h.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Listar | paciente com consultas futuras | Cards por data crescente com selo Confirmada/Bloqueada | N/A |
| Sem consultas | nenhuma futura | "Você ainda não tem consultas agendadas." + "+ Nova consulta" | N/A |
| Cancelar (≥ 24h) | "Cancelar" e "Sim" no card | Status `cancelled`; card some; horário sai de `booked_slots` e volta a ficar livre; evento `cancellation` para o médico | N/A |
| Cancelar, desistir | "Cancelar" e "Não" | Confirmação fecha, nada muda | N/A |
| Exatamente 24h | início a exatamente 24h de agora | Cancelar/reagendar permitido | N/A |
| Menos de 24h | início a < 24h | Botões desabilitados com a nota; chamada direta às funções -> `CONFLICT: cancel_window` | Mensagem clara |
| Reagendar (≥ 24h) | novo horário livre, ≥ 48h, na agenda | Mesma consulta (mesmo id) no novo horário; `booked_slots` troca o horário; evento `reschedule` para o médico; volta à lista atualizada | N/A |
| Reagendar com conflito | outro paciente pegou o horário | `CONFLICT: slot_taken`, consulta inalterada | "Este horário acabou de ser reservado, escolha outro." |
| Reagendar inválido | novo horário < 48h, fora da agenda/15 min, igual ao atual | `CONFLICT: lead_time` / `INVALID:` | Mensagem clara/genérica |
| Consulta alheia | id de consulta de outro paciente | `FORBIDDEN:` | Sem detalhe técnico |
| Já cancelada | cancelar/reagendar consulta `cancelled` | `INVALID:` | Mensagem genérica |
| Médico como chamador | médico da consulta cancela/reagenda (≥ 24h) | Permitido nas mesmas regras; evento ao paciente | N/A |
| Escrita direta | cliente tenta UPDATE/DELETE em `appointments` | Negado | N/A |
| Duplo toque | "Sim" ou "Confirmar novo horário" repetido | Uma única operação | N/A |
| Falha de rede | Supabase inacessível | Mensagem genérica, lista inalterada | Bucket `UNEXPECTED` |

</frozen-after-approval>

## Code Map

- `supabase/migrations/0005_appointments.sql` -- `appointments`, índice único parcial, `book_appointment` (validações de horário a extrair), `sync_booked_slots`, `notification_events`; NÃO editar
- `app/.../data/repository/AppointmentRepository.kt` -- `bookAppointment`, `BookingResult`, `observeBookedSlots`; adicionar listar/cancelar/reagendar com resultado tipado
- `app/.../domain/agenda/AgendaSlots.kt` -- `ANTECEDENCIA_MINIMA_HORAS`, `FUSO_AGENDA`, slots; adicionar a constante e a regra da janela de 24h
- `app/.../ui/patient/{DetalheMedicoScreen,DetalheMedicoViewModel}.kt` -- fluxo de agendar (convênio, botão, Realtime, conflito); ganha o modo Reagendar
- `app/.../ui/patient/{BuscaScreen,ConfirmacaoScreen}.kt` -- pontos de entrada para Minhas Consultas
- `app/.../ui/navigation/AgendaMedicaNavHost.kt` -- rotas `busca`, `medico/{doctorId}`, `confirmacao`; adicionar Minhas Consultas e o modo Reagendar
- `app/.../ui/components/Components.kt` -- `PrimaryButton`, `OutlineButton`, `TagChip`
- `app/.../data/repository/AppError.kt` -- `toAppError()`; `BookingResult` mostra o padrão de mapeamento por token (`slot_taken`, `lead_time`)
- `supabase/tests/concurrency-test.mjs` -- padrão do script contra o projeto hospedado; estender ou criar irmão com helpers compartilhados
- `app/src/test/.../ui/patient/DetalheMedicoViewModelTest.kt`, `data/repository/BookingResultTest.kt` -- padrão de teste (um `UnconfinedTestDispatcher` compartilhado em `setMain` e `runTest`)

## Tasks & Acceptance

**Execution:**
- [x] `supabase/migrations/0006_cancel_reschedule.sql` -- função interna de validação de horário, `book_appointment` recriada usando-a, `cancel_appointment`, `reschedule_appointment` (bloqueio 24h, `FOR UPDATE`, evento à outra parte), grants -- AD-1, AD-8, AD-9, AD-12
- [x] `data/repository/AppointmentRepository.kt` + `domain/agenda/AgendaSlots.kt` -- listagem das próprias consultas (com médico), `cancelAppointment`/`rescheduleAppointment` com resultado tipado (sucesso, horário reservado, antecedência, janela de 24h, falha genérica); constante e função da janela de 24h
- [x] `ui/patient/{MinhasConsultasScreen,MinhasConsultasViewModel}.kt` -- lista, selo, nota de bloqueio, confirmação inline, estados vazio/carregando/erro, recarga ao voltar
- [x] `ui/patient/{DetalheMedicoViewModel,DetalheMedicoScreen}.kt` + `ui/navigation/AgendaMedicaNavHost.kt` -- modo Reagendar (cabeçalho, botão, sem convênio, voltar), rota de Minhas Consultas e entradas na Busca e na Confirmação
- [x] `supabase/tests/` -- script que prova contra o banco real: cancelar/reagendar válidos, exatamente 24h, < 24h (consulta criada por SQL), corrida de dois reagendamentos para o mesmo horário (exatamente 1 vence), consulta alheia, já cancelada, chamador médico, escrita direta negada, eventos `cancellation`/`reschedule` para a outra parte, `book_appointment` inalterado; limpa os dados de teste
- [x] `app/src/test/...` -- `MinhasConsultasViewModelTest` (cada linha da matriz do app), modo Reagendar no `DetalheMedicoViewModelTest`, mapeamento dos resultados

**Acceptance Criteria:**
- Given uma consulta com 24h ou mais, when cancelo confirmando "Sim", then ela vira `cancelled` e o horário volta a ficar livre imediatamente (FR8)
- Given uma consulta com 24h ou mais, when reagendo para um horário livre, then a mesma consulta (mesmo id) passa ao novo horário (FR8)
- Given uma consulta a menos de 24h, when abro Minhas Consultas ou chamo a função direto, then a UI fica desabilitada com a nota e o banco rejeita com `CONFLICT` (FR9)
- Given um cancelamento ou reagendamento concluído, when a função termina, then existe o evento para a outra parte, sem envio real (FR8, AD-12)

## Implementation Notes

**2026-09-22 — implementação concluída.** Migração `0006_cancel_reschedule.sql` (função interna `assert_slot_bookable` fechada para clientes, `book_appointment` recriada sobre ela, `cancel_appointment`, `reschedule_appointment` com `FOR UPDATE`, janela de 24h inclusiva no banco e evento à outra parte), `AppointmentRepository` (listar/cancelar/reagendar com resultados tipados), `AgendaSlots` (janela de 24h), `MinhasConsultasScreen`/`ViewModel`, modo Reagendar no Detalhe, rotas e entradas (Busca e Confirmação), scripts `supabase/tests/cancel-reschedule-test.mjs` (`npm run test:cancel-reschedule`) e testes unitários.

- **Aplicação da migração (orquestrador):** o subagente foi barrado ao aplicar a 0006 no banco hospedado; revi o SQL linha a linha (identidade só por `auth.uid()`, ordem FORBIDDEN -> INVALID -> CONFLICT, `unique_violation` revertendo o UPDATE, evento para a outra parte, sem contorno da janela para nenhum papel) e apliquei com `npx supabase db push`, autorizado nos argumentos da história.
- **Verificação real:** `cancel-reschedule-test.mjs` -> ALL CHECKS PASSED (reagendar válido mantém o mesmo id e troca `booked_slots`; entradas inválidas não alteram a consulta; cancelar mantém a linha e libera o horário; janela de 24h por SQL em cancelar e reagendar, inclusive para o médico; consulta alheia/inexistente -> FORBIDDEN; UPDATE/DELETE direto -> 403; corrida de dois reagendamentos ao mesmo horário, 3 rodadas, exatamente 1 vence; médico como chamador gera evento ao PACIENTE; `book_appointment` inalterado). `concurrency-test.mjs` (2.3) -> ALL CHECKS PASSED com a função recriada. A leitura de Minhas Consultas com o médico embutido (`doctors(name, specialty)`) foi conferida contra a API real e bate com o modelo do app.
- Uma primeira execução do script novo falhou por queda transitória do CLI e deixou dados de teste (5 usuários); limpei por SQL e adicionei retry ao helper `sql()` dos dois scripts.
- **Auditoria da matriz:** todas as linhas têm teste (ViewModels, resultados tipados, scripts contra o banco). Só no aparelho: renderização de Minhas Consultas, confirmação inline e o fluxo Reagendar ponta a ponta. "Exatamente 24h" é coberto pelo teste unitário e pelo `<` estrito no SQL (o script testa logo abaixo e logo acima, pois o relógio real não permite igualdade).
- `./gradlew assembleDebug testDebugUnitTest`: BUILD SUCCESSFUL, 139 testes, 0 falhas.

**2026-09-22 — patches do gate de revisão (ver Review Triage Log).** Carga anterior ao cancelamento não ressuscita o card e `refresh()` é ignorado durante o cancelamento; cancelamento rejeitado (`FORBIDDEN`/`INVALID`) recarrega a lista; botões dos cards com descrição do médico e horário para o TalkBack; textos de 24h/48h derivados das constantes; o script contra o banco agora executa a consulta exata de Minhas Consultas (incluindo o embed do médico) e o helper `sql()` tem retry. Resultado final: BUILD SUCCESSFUL, 142 testes, 0 falhas; `cancel-reschedule-test.mjs` e `concurrency-test.mjs` com ALL CHECKS PASSED.

## Spec Change Log

## Review Triage Log

Três revisores; achados verificados contra o código, o SQL e o banco real. Nenhum `intent_gap`/`bad_spec`; sem loopback.

| # | Achado | Veredito | Evidência | Rota |
|---|--------|----------|-----------|------|
| 1 | Uma carga em andamento antes do cancelamento pode reescrever a lista com o card cancelado; `refresh()` pode rodar durante o cancelamento | `medium` | `onCancelarSim` não cancelava `loadJob` e `refresh()` só olhava `loadJob`. | **patch** — cancela a carga ao concluir o cancelamento e `refresh()` é ignorado com `cancelandoId` (+ 2 testes) |
| 2 | Cancelamento rejeitado por `FORBIDDEN`/`INVALID` (já cancelada em outro lugar, cancelada pelo médico) deixa o card com botões ativos | `medium` | Só o ramo `WindowClosed` recarregava a lista. | **patch** — `Failure` também recarrega em silêncio (+ teste) |
| 3 | Botões "Cancelar/Reagendar/Sim/Não" idênticos em todos os cards: TalkBack não diz a qual consulta pertencem | `medium` | Sem `contentDescription`; UX-DR8. | **patch** — descrição com médico e data/hora em cada botão |
| 4 | Textos "24h" e "48 horas" digitados à mão, podendo divergir das constantes | `low` | `MSG_JANELA_24H`/`MSG_ANTECEDENCIA` literais. | **patch** — derivados de `JANELA_ALTERACAO_HORAS`/`ANTECEDENCIA_MINIMA_HORAS` |
| 5 | VG: a consulta de Minhas Consultas (filtro de status/data, ordem, embed do médico) nunca é executada por teste | `medium` | Só ViewModel com mock e conferência manual. | **patch** — o script contra o banco real agora roda a mesma consulta do app (futura confirmada aparece; passada e cancelada não; ordem; embed decodifica) |
| 6 | Contrato cliente/RPC (nomes de função e parâmetros), rota `consultaId` e volta do Reagendar sem teste automatizado | `medium` | O RPC é provado por `fetch` puro no script; o Kotlin e o NavHost só no aparelho. | **defer** |
| 7 | Deadlock ao trocar dois horários entre duas consultas do mesmo médico ao mesmo tempo | `low` | Exige duas trocas cruzadas simultâneas; o Postgres aborta uma (40P01) e ela vira erro genérico, sem corrupção. | rejeitado |
| 8 | `book_appointment` recriado com ordem de checagem diferente (convênio antes de alinhamento/agenda) | `false` | Mudança declarada; só altera qual `INVALID:` aparece com várias violações; regressão coberta pelos dois scripts, que passaram. | rejeitado |
| 9 | CHECK de `notification_events` sem `cancellation`/`reschedule`; embed nulo; falta filtro explícito `patient_id` | `false` | O CHECK da 0005 já os permite (eventos gravados no teste real); `doctor_id` é `NOT NULL` com leitura liberada; a tela é só do paciente e a RLS escopa. | rejeitado |
| 10 | Reagendar preso em `WindowClosed`/falha; `bloqueada` calculada só na carga; refresh silencioso pulado durante carga | `low` | Voltar dispara `refresh`; o servidor é a verdade e responde `cancel_window`. | rejeitado |
| 11 | Sem mensagem de sucesso após cancelar/reagendar; tela de Reagendar sem o horário atual; selo "Bloqueada"; foco/anúncio da confirmação inline; recarga dupla na entrada | `low` | UX polish fora da spec. | rejeitado |
| 12 | Retry de `sql()` repete escritas; helper duplicado em dois scripts | `low` | A CLI só falha ao conectar; a limpeza final por prefixo desfaz eventual duplicata. | rejeitado |

## Design Notes

**Nota de correção (Story 5.1, 2026-09-22):** a premissa de "Slots de 15 minutos" usada nesta spec foi substituída — consulta de 30 min com 15 min de intervalo (grade de 45 em 45 min). O Médico continua escolhendo seu próprio horário de início/fim (nada mudou nisso), mas agora dentro do horário fixo da clínica, 08h-18h (antes 06h-22h). Ver `spec-5-1-grade-horarios-30min.md` e `sprint-change-proposal-2026-09-22.md`.

As funções já aceitam o médico como chamador para que a Story 2.5 só precise da UI; o script prova isso. Consultas passadas e canceladas não são exibidas (o cancelamento "remove" a consulta da lista, como no protótipo). O bloqueio de 24h usa `now()` do servidor; o app calcula o mesmo limite só para desabilitar botões e, se a fronteira for cruzada com a tela aberta, o servidor responde `cancel_window` e a lista é recarregada.

## Verification

**Commands:**
- `./gradlew assembleDebug testDebugUnitTest` (com `JAVA_HOME` do JBR do Android Studio) -- expected: `BUILD SUCCESSFUL`, todos os testes passam
- `npx supabase db push` -- expected: migração `0006` aplicada
- `node supabase/tests/concurrency-test.mjs` e o novo script de cancelar/reagendar -- expected: todas as verificações passam (o de 2.3 prova que `book_appointment` não mudou); dados de teste removidos

**Manual checks (if no CLI):**
- No celular: agendar, abrir Minhas Consultas, reagendar (mesma consulta muda de horário), cancelar com Sim/Não e ver o horário livre na Busca/Detalhe de outro paciente; consulta a menos de 24h (criada por SQL) com botões desabilitados e a nota
