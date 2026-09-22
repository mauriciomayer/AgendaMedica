---
title: 'Médico cancela ou reagenda uma consulta de um paciente'
type: 'feature'
created: '2026-09-22'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: '2df5127fc92b4141d961ad904bef4a034e9c3b45'
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** A Minha Agenda do Médico ainda mostra "Nenhuma consulta agendada ainda." mesmo com pacientes marcados: o médico não vê suas consultas nem consegue cancelar ou reagendar, e o paciente não é avisado (RF-8, RF-9, lado médico).

**Approach:** Uma função de leitura devolve ao médico só as suas consultas futuras com o nome do paciente; a Minha Agenda lista os cards com Cancelar (confirmação inline) e Reagendar, reaproveitando as funções `cancel_appointment`/`reschedule_appointment` da 2.4 (que já aceitam o médico) e o Detalhe em modo Reagendar sobre a própria agenda.

## Boundaries & Constraints

**Always:**
- Nova função `list_doctor_appointments()`, `SECURITY DEFINER`, `SET search_path = public, pg_temp`, `EXECUTE` só para `authenticated`, identidade só por `auth.uid()`: devolve `id`, `start_time`, `insurance` e `patient_name` das consultas `confirmed` futuras em que o chamador é o médico, por data crescente. Nunca devolve e-mail nem outro dado do paciente; quem não é médico de nenhuma consulta recebe lista vazia. A tabela `patients` continua sem leitura para médicos (AD-10). Nova migração `0007`.
- Cancelar e reagendar seguem exatamente as regras da 2.4, sem exceção para o médico: 24h ou mais (exatamente 24h permitido, senão `CONFLICT: cancel_window`), consulta `confirmed`, mesma linha atualizada, novo horário dentro da PRÓPRIA agenda do médico, a 48h ou mais, sem conflito; o evento `cancellation`/`reschedule` vai ao PACIENTE (AD-12, sem envio real).
- Minha Agenda mantém o cartão do perfil e ganha "Próximas consultas": cards com paciente, data/hora (São Paulo), convênio e selo "Confirmada"/"Bloqueada"; a menos de 24h, "Cancelar" e "Reagendar" visíveis porém desabilitados com a nota "Bloqueado: faltam menos de 24h — não é mais possível cancelar ou reagendar." (texto, não só cor). "Cancelar" abre confirmação inline no card (par Sim/Não), sem diálogo modal e sem duplo envio; os botões dizem ao leitor de tela de qual paciente e horário são. Vazio: "Nenhuma consulta agendada ainda.". Carregamento com indicador; falha com mensagem genérica e "Tentar novamente" sem esconder o perfil (AD-9); a lista recarrega ao voltar à tela.
- Reagendar abre o Detalhe do PRÓPRIO médico em modo Reagendar (cabeçalho "Reagendar consulta", "Confirmar novo horário", sem convênio); voltar ou sucesso retornam à Minha Agenda. Conflito -> "Este horário acabou de ser reservado, escolha outro.".
- Só `data/` fala com o Supabase (AD-2); reaproveitar o padrão e, se possível, o componente de card de Minhas Consultas em vez de duplicar.

**Never:**
- Não abrir leitura da tabela `patients` a médicos nem expor e-mail. Não criar histórico de consultas passadas/canceladas, Realtime da agenda do médico nem entrega de notificação (Épico 3). Não editar `0001`-`0006`.
- Nenhum papel contorna a janela de 24h; o cliente não faz `UPDATE`/`DELETE` direto em `appointments`.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Listar | médico com consultas futuras | Cards por data crescente com paciente, convênio e selo | N/A |
| Sem consultas | nenhuma futura | "Nenhuma consulta agendada ainda." | N/A |
| Cancelar (≥ 24h) | "Cancelar" e "Sim" | Status `cancelled`; card some; horário livre em `booked_slots`; evento `cancellation` para o PACIENTE | N/A |
| Cancelar, desistir | "Cancelar" e "Não" | Confirmação fecha, nada muda | N/A |
| Menos de 24h | início a < 24h | Botões desabilitados com a nota; chamada direta -> `CONFLICT: cancel_window` | Mensagem clara |
| Reagendar (≥ 24h) | novo horário livre na própria agenda, ≥ 48h | Mesma consulta (mesmo id) no novo horário; evento `reschedule` para o PACIENTE; volta à Minha Agenda atualizada | N/A |
| Reagendar com conflito/inválido | horário tomado, < 48h, fora da agenda | `slot_taken`/`lead_time`/`INVALID:` como na 2.4, consulta inalterada | Mensagem clara/genérica |
| Cancelada pelo paciente | paciente cancelou antes | Some da lista ao recarregar; cancelar/reagendar rejeitado (`INVALID:`) e a lista recarrega | Mensagem genérica |
| Privacidade | paciente ou outro médico chama a função | Só consultas do próprio chamador; paciente recebe vazio; sem sessão negado; nunca e-mail | N/A |
| Duplo toque | "Sim" ou "Confirmar novo horário" repetido | Uma única operação | N/A |
| Falha de rede | Supabase inacessível | Mensagem genérica com "Tentar novamente"; perfil continua visível | Bucket `UNEXPECTED` |

</frozen-after-approval>

## Code Map

- `supabase/migrations/0006_cancel_reschedule.sql` -- `cancel_appointment`/`reschedule_appointment` (aceitam o médico) e `assert_slot_bookable`; NÃO editar
- `supabase/migrations/0005_appointments.sql`, `0002_patients.sql` -- `appointments` e RLS (médico lê as próprias linhas); `patients` (nome, e-mail) com leitura só do dono
- `app/.../ui/doctor/{MinhaAgendaScreen,MinhaAgendaViewModel}.kt` -- perfil e vazio fixo "Nenhuma consulta agendada ainda."; ganha a lista, o cancelamento inline e a entrada de Reagendar
- `app/.../data/repository/AppointmentRepository.kt` -- `cancelAppointment`/`rescheduleAppointment` e resultados tipados (`CancelResult`, `RescheduleResult`), `MinhaConsulta`, `getMyUpcomingAppointments` (paciente); adicionar a leitura do médico
- `app/.../ui/patient/{MinhasConsultasScreen,MinhasConsultasViewModel}.kt` -- padrão de card, selo, nota de 24h, confirmação inline e recarga ao voltar a reaproveitar
- `app/.../ui/patient/{DetalheMedicoScreen,DetalheMedicoViewModel}.kt` -- modo Reagendar (`appointmentId`), sem convênio
- `app/.../ui/navigation/AgendaMedicaNavHost.kt` -- rota `medico/{doctorId}` com `consultaId` opcional; rota `minha_agenda`
- `app/.../domain/agenda/AgendaSlots.kt` -- `podeAlterarConsulta`, `JANELA_ALTERACAO_HORAS`
- `supabase/tests/cancel-reschedule-test.mjs` -- script contra o projeto hospedado, com o cenário do médico como chamador; estender
- `app/src/test/.../ui/doctor/MinhaAgendaViewModelTest.kt`, `ui/patient/MinhasConsultasViewModelTest.kt` -- padrão de teste (um `UnconfinedTestDispatcher` compartilhado em `setMain` e `runTest`)

## Tasks & Acceptance

**Execution:**
- [x] `supabase/migrations/0007_doctor_appointments.sql` -- `list_doctor_appointments()` (só nome do paciente, escopo `auth.uid()`, futuras `confirmed`, ordenadas), grants -- AD-1, AD-10
- [x] `data/repository/AppointmentRepository.kt` -- `getDoctorUpcomingAppointments()` chamando a função, com modelo próprio (paciente, horário, convênio, id)
- [x] `ui/doctor/{MinhaAgendaViewModel,MinhaAgendaScreen}.kt` (+ card compartilhado) -- lista, selo, nota de 24h, cancelamento inline com Sim/Não, estados vazio/carregando/erro sem esconder o perfil, recarga ao voltar, entrada de Reagendar
- [x] `ui/navigation/AgendaMedicaNavHost.kt` (+ `DetalheMedicoScreen` se preciso) -- Reagendar da Minha Agenda abre o Detalhe do próprio médico com `consultaId` e volta à Minha Agenda
- [x] `supabase/tests/cancel-reschedule-test.mjs` -- provar `list_doctor_appointments` (só as do chamador, `patient_name`, sem e-mail, paciente recebe vazio, anon negado) e o fluxo completo do médico cancelar/reagendar com evento ao paciente e janela de 24h; limpar dados
- [x] `app/src/test/...` -- `MinhaAgendaViewModelTest` estendido (cada linha da matriz do app), mapeamento da leitura do médico

**Acceptance Criteria:**
- Given uma consulta com 24h ou mais, when o médico cancela confirmando "Sim", then ela vira `cancelled`, o horário volta a ficar livre e existe o evento para o paciente (FR8)
- Given uma consulta com 24h ou mais, when o médico reagenda dentro da própria agenda, then a mesma consulta passa ao novo horário e o paciente recebe o evento (FR8)
- Given uma consulta a menos de 24h, when o médico abre Minha Agenda ou chama a função, then os botões ficam desabilitados com a nota e o banco rejeita com `CONFLICT`, sem exceção para o médico (FR9)
- Given a leitura da agenda, when o médico lista as consultas, then vê só as suas, com o nome do paciente e nunca o e-mail (AD-10)

## Implementation Notes

**2026-09-22 — implementação concluída.** Migração `0007_doctor_appointments.sql` (`list_doctor_appointments()`), `AppointmentRepository.getDoctorUpcomingAppointments`, `MinhaAgendaViewModel`/`Screen` com a lista, o cancelamento inline e a entrada de Reagendar, `ConsultaCard` compartilhado com Minhas Consultas, rota de Reagendar do Detalhe reaproveitada e testes.

- **Aplicação da migração (orquestrador):** revi o SQL (`language sql stable security definer`, `search_path` fixo, escopo só por `auth.uid()` como `doctor_id`, só o nome do paciente, `anon` sem execução) e apliquei com `npx supabase db push`, autorizado nos argumentos da história.
- **Verificação real:** `cancel-reschedule-test.mjs` (estendido) -> ALL CHECKS PASSED: `list_doctor_appointments` devolve só as consultas do chamador (inclusive < 24h), sem passadas/canceladas, ordenadas, com exatamente `id, start_time, insurance, patient_name` (sem e-mail), outro médico vê só as suas, paciente recebe `[]`, anon -> 401, e o médico continua sem ler `patients`; o médico reagenda e cancela com evento ao PACIENTE, rejeições de mesmo horário/`lead_time`/`cancel_window`, outro médico -> FORBIDDEN, cancelar libera o horário e a consulta sai da lista. `concurrency-test.mjs` (2.3) segue ALL CHECKS PASSED.
- **Auditoria da matriz:** todas as linhas têm teste (`MinhaAgendaViewModelTest`, `DoctorAppointmentRowTest`, scripts contra o banco). Só no aparelho: renderização da Minha Agenda, a confirmação inline e o Reagendar ponta a ponta.
- `./gradlew assembleDebug testDebugUnitTest`: BUILD SUCCESSFUL, 154 testes, 0 falhas.

**2026-09-22 — patches do gate de revisão (ver Review Triage Log).** Spinner da lista do médico não fica preso quando uma recarga silenciosa substitui uma carga visível e falha; `launchSingleTop` nas duas entradas de Reagendar (médico e paciente); 4 testes novos (carga em andamento x cancelamento, `refresh` durante o cancelamento, confirmação obsoleta, spinner preso). Resultado final: BUILD SUCCESSFUL, 158 testes, 0 falhas; os dois scripts contra o banco com ALL CHECKS PASSED.

## Spec Change Log

## Review Triage Log

Três revisores; achados verificados contra o código, o SQL e o banco real. Nenhum `intent_gap`/`bad_spec`; sem loopback.

| # | Achado | Veredito | Evidência | Rota |
|---|--------|----------|-----------|------|
| 1 | Recarga silenciosa que substitui uma carga visível e falha deixa `consultasLoading` ligado (spinner eterno) | `low` | `onFailure` só limpava o spinner quando `showSpinner`. Correção de poucas linhas. | **patch** — limpa o spinner e mostra o erro nesse caso (+ teste) |
| 2 | Toque duplo em Reagendar empilha duas cópias do Detalhe (vale também para a 2.4) | `low` | `navigate` sem `launchSingleTop` nas duas entradas. | **patch** — `launchSingleTop = true` nas duas |
| 3 | VG: corrida carga em andamento x cancelamento, `refresh` durante o cancelamento e `confirmandoId` obsoleto sem teste | `medium` | A lógica existe (`consultasJob?.cancel()`, `takeIf`) mas nenhum teste sobrepunha as operações. | **patch** — 3 testes adicionados |
| 4 | Sem teste de `doctorIdProvider` nulo, da rota/`onReagendar` do médico e do nome do RPC no cliente | `medium` | Contrato Kotlin<->RPC e navegação só no aparelho, como na 2.4. | **defer** (junto da pendência da 2.4) |
| 5 | Falha de recarga silenciosa na volta (ON_RESUME) não mostra erro; `bloqueada` calculada só na carga; `doctorId` nulo faz Reagendar não responder | `low` | Mesmo padrão aceito na 2.4 (o servidor é a verdade e responde `cancel_window`); `doctorId` vem da sessão, sempre presente nesta tela. | rejeitado |
| 6 | Linha com `start_time` inválido derruba a lista; consulta em andamento some da lista; ordenação em 3 lugares; sem limite/índice/`comment on function` | `low` | A coluna é `timestamptz` válida; "futuras" é a definição da spec; o índice único `(doctor_id, start_time)` já serve a consulta; volume mínimo. | rejeitado |
| 7 | Acoplamento do card ao pacote `patient`, ordem de imports, cor da nota de 24h | `low` | Refatoração/cosmético; o card é neutro (título e rótulos vêm por parâmetro). | rejeitado |
| 8 | Sem notificação do paciente no cliente / sem Realtime da agenda do médico / sem motivo do cancelamento | `false` | Fora da spec (Never) — a entrega é do Épico 3 e o evento já é gravado e provado. | rejeitado |
| 9 | Script: `parse()` devolve `[]` em erro, ordenação trivial, offsets de dias fixos | `low` | Os checks exigem `r.ok` e resultados positivos (as listas têm 2+ linhas); offsets de 22 a 24 dias ficam longe das regras de 24h/48h. | rejeitado |

## Design Notes

**Nota de correção (Story 5.1, 2026-09-22):** a premissa de "Slots de 15 minutos" usada nesta spec foi substituída — consulta de 30 min com 15 min de intervalo (grade de 45 em 45 min). O Médico continua escolhendo seu próprio horário de início/fim (nada mudou nisso), mas agora dentro do horário fixo da clínica, 08h-18h (antes 06h-22h). Ver `spec-5-1-grade-horarios-30min.md` e `sprint-change-proposal-2026-09-22.md`.

A alternativa de abrir `patients` ao médico por RLS foi descartada porque a linha traz o e-mail; a função devolve só o nome. As funções de cancelar/reagendar já provaram o médico como chamador na 2.4, então esta história é leitura + UI. Consultas passadas e canceladas não são exibidas, como para o paciente.

## Verification

**Commands:**
- `./gradlew assembleDebug testDebugUnitTest` (com `JAVA_HOME` do JBR do Android Studio) -- expected: `BUILD SUCCESSFUL`, todos os testes passam
- `npx supabase db push` -- expected: migração `0007` aplicada (revisar o SQL antes)
- `node supabase/tests/cancel-reschedule-test.mjs` e `node supabase/tests/concurrency-test.mjs` -- expected: ALL CHECKS PASSED; dados de teste removidos

**Manual checks (if no CLI):**
- No celular: um paciente agenda com o médico; em Minha Agenda o médico vê o card, reagenda (mesma consulta muda de horário) e cancela com Sim/Não; o horário volta a aparecer livre para o paciente; consulta a menos de 24h (criada por SQL) com botões desabilitados e a nota
