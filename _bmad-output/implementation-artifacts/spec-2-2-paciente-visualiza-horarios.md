---
title: 'Paciente visualiza horários disponíveis de um médico'
type: 'feature'
created: '2026-09-21'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: 'acf081b7b5801ef72c2786a1e248acae3a997477'
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Os cards da Busca não levam a lugar nenhum: o Paciente não vê a agenda de um médico, então não sabe quais horários estão livres nem respeita a antecedência mínima.

**Approach:** Tocar num card abre o Detalhe do Médico, com carrossel dos próximos dias de atendimento e grade de horários de 15 minutos. A grade é calculada no app a partir de `doctor_schedules` menos `booked_slots` (tabela nova, sem dados de paciente); horários ocupados ou com menos de 48h ficam desabilitados com o motivo.

## Boundaries & Constraints

**Always:**
- A grade é derivada em memória, nunca armazenada (AD-4): cada bloco de `doctor_schedules` vira slots de 15 min, início inclusivo e fim exclusivo (08:00-12:00 dá 08:00..11:45). Regras de tempo no fuso fixo `America/Sao_Paulo`, independentes do fuso do aparelho; "agora" vem de um relógio injetável para teste.
- Carrossel: olha os próximos 10 dias a partir de hoje, mantém só os dias da semana em que o médico atende e mostra no máximo 6 (como o protótipo). Nenhum dia selecionado ao abrir; selecionar um dia limpa o horário selecionado.
- Slot habilitado só se não ocupado e a ≥ 48h de agora (exatamente 48h é habilitado; 1 segundo a menos, não). `ANTECEDENCIA_MINIMA_HORAS = 48` num único lugar do domínio. Desabilitado mostra o motivo no próprio horário e o anuncia ao TalkBack: "Ocupado" (tem prioridade) ou "Antecedência mín. 48h". Comparações instante contra instante (AD-8).
- Ocupação vem de `booked_slots (doctor_id, start_time timestamptz)`, sem coluna de paciente (AD-10); RLS: `SELECT` para qualquer autenticado, nenhuma política de escrita. Nova migração `0004`.
- Só `data/` fala com o Supabase (AD-2); falha de rede/servidor -> mensagem genérica com "Tentar novamente"; carregamento com indicador (UX-DR10, AD-9). Grade em 3 colunas, alvos ≥48dp.

**Never:**
- Não agendar, não ter botão "Confirmar agendamento", não criar `appointments` nem o trigger `sync_booked_slots`, não assinar Realtime (Story 2.3). Não editar `0001`-`0003`.
- Não mostrar Minhas Consultas nem reagendar.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Abrir Detalhe | toque num card da Busca | Nome, especialidade, cidade, chips de convênio e carrossel; voltar leva à Busca | N/A |
| Carrossel | médico atende seg/qua/sex | Só essas datas dos próximos 10 dias, no máximo 6, sem seleção inicial | N/A |
| Selecionar dia | toque numa data | Grade de slots de 15 min (início inclusivo, fim exclusivo), 3 colunas | N/A |
| Slot com antecedência | ≥ 48h de agora (inclusive exatamente 48h) | Habilitado e selecionável (fica destacado) | N/A |
| Slot a menos de 48h | < 48h (ex.: 47h59m59s) | Desabilitado, motivo "Antecedência mín. 48h" | N/A |
| Slot ocupado | consta em `booked_slots` | Desabilitado, motivo "Ocupado" (mesmo se também < 48h) | N/A |
| Dia sem horários | dia selecionado sem slots | "Sem atendimento neste dia." | N/A |
| Sem dias de atendimento | médico sem dias nos próximos 10 dias | "Sem atendimento nos próximos dias." | N/A |
| Trocar de dia | horário selecionado, toque em outra data | Seleção de horário é limpa | N/A |
| Fuso do aparelho diferente | aparelho em outro fuso | Datas, dia da semana e 48h calculados em America/Sao_Paulo | N/A |
| Carregando | dados ainda não chegaram | Indicador de carregamento | N/A |
| Falha de rede/servidor | Supabase inacessível | Mensagem genérica + "Tentar novamente" | Bucket `UNEXPECTED` |
| Privacidade | outro paciente com consulta | Só `doctor_id` e `start_time` são legíveis; nenhuma identidade | N/A |

</frozen-after-approval>

## Code Map

- `supabase/migrations/0001_init.sql` -- `doctor_schedules (doctor_id, weekday 0=Domingo..6, start_time, end_time)` e RLS de leitura para autenticados; NÃO editar
- `app/.../domain/model/{DiaSemana,DoctorProfile}.kt` -- `DiaSemana.isoValue` segue `EXTRACT(DOW)` (0 = domingo); `ScheduleBlock(dia, startTime, endTime)` (hoje só do próprio médico)
- `app/.../data/repository/DoctorRepository.kt` -- padrão Postgrest (`getMyProfile`, `searchDoctors`, linhas `@Serializable` privadas); adicionar leitura do médico por id + agenda + slots ocupados
- `app/.../data/repository/AppError.kt` -- reutilizar `toAppError()/toUserMessage()`
- `app/.../domain/search/DoctorSearch.kt` -- padrão de lógica de domínio pura testada (`DoctorSummary`)
- `app/.../ui/patient/{BuscaScreen,BuscaViewModel}.kt` -- `DoctorCard` (hoje não clicável) passa a abrir o Detalhe
- `app/.../ui/navigation/AgendaMedicaNavHost.kt` -- rota `busca` existe; adicionar rota do Detalhe com o id do médico
- `app/.../ui/components/Components.kt` -- `TagChip`, `ToggleChip`, `AccessibleIconButton`, `OutlineButton`
- `app/src/test/.../ui/patient/BuscaViewModelTest.kt`, `domain/search/DoctorSearchTest.kt` -- padrão de teste (um `UnconfinedTestDispatcher` compartilhado em `setMain` e `runTest`)

## Tasks & Acceptance

**Execution:**
- [x] `supabase/migrations/0004_booked_slots.sql` -- `booked_slots (doctor_id -> doctors on delete cascade, start_time timestamptz, PK (doctor_id, start_time))`, RLS ligada, `SELECT` a autenticados, sem escrita -- AD-10
- [x] `domain/agenda/AgendaSlots.kt` -- carrossel (10 dias, no máx. 6) e slots de 15 min com estado/motivo, como funções puras com `Clock`/`ZoneId` injetáveis -- AD-4, AD-8
- [x] `data/repository/DoctorRepository.kt` -- `getDoctorDetail(id)` (perfil público + agenda semanal) e `getBookedSlots(doctorId, de, até)` -- AD-2
- [x] `ui/patient/{DetalheMedicoScreen,DetalheMedicoViewModel}.kt` -- cabeçalho, carrossel, grade 3 colunas, seleção de horário, estados de carregamento/erro/vazio, acessibilidade do motivo
- [x] `ui/patient/BuscaScreen.kt`, `ui/navigation/AgendaMedicaNavHost.kt` -- card clicável e rota do Detalhe (voltar retorna à Busca)
- [x] `app/src/test/...` -- `AgendaSlotsTest` (limites de 48h, fim exclusivo, dia da semana/fuso, "Ocupado" com prioridade, máx. 6 dias) e `DetalheMedicoViewModelTest` (cada linha da matriz)

**Acceptance Criteria:**
- Given um Paciente na Busca, when toca num card, then vê o Detalhe do Médico com dias de atendimento (até 6) e, ao escolher um dia, a grade de 15 min (FR5)
- Given um slot a menos de 48h ou já ocupado, when a grade aparece, then fica desabilitado com o motivo em texto e para o TalkBack (FR5, FR6)
- Given um dia sem horários, when é selecionado, then aparece "Sem atendimento neste dia."
- Given carregamento ou falha, when ocorrem, then há indicador ou mensagem genérica com nova tentativa

## Implementation Notes

**2026-09-21 — implementação concluída.** Migração `0004_booked_slots.sql`, `AgendaSlots.kt` (domínio puro: carrossel de 10 dias/máx. 6, slots de 15 min, "Ocupado" antes de antecedência, `Clock` e fuso `America/Sao_Paulo` injetáveis), `DoctorRepository.getDoctorDetail`/`getBookedSlots`, `DetalheMedicoScreen`/`ViewModel`, card da Busca clicável e rota `medico/{doctorId}`.

- **Verificação real no projeto hospedado (feita pelo orquestrador; o subagente não aplicou a migração):** `db push` da 0004; formatos crus conferidos — `doctor_schedules.start_time` = `"08:00:00"` e `booked_slots.start_time` = `"2026-09-30T17:30:00+00:00"` (ambos aceitos por `LocalTime.parse`/`OffsetDateTime.parse`); o filtro `gte`/`lt` com `Instant.toString()` devolve o horário dentro da janela e nada fora dela; escrita direta em `booked_slots` como paciente autenticado -> 403.
- **Dado de teste deixado de propósito** para a verificação no aparelho: um horário ocupado de "Dra. Busca Pinheiros" (Dermatologia, atende terça 09:00-13:00) em 2026-09-29 10:00 (São Paulo), inserido via `npx supabase db query --linked`. Apagar depois com `delete from public.booked_slots;`. Usuários `smoke.*@example.com` seguem no projeto.
- **Auditoria da matriz:** linhas cobertas por `AgendaSlotsTest` (limites de 48h, fim exclusivo, fuso, prioridade do "Ocupado", máx. 6 dias) e `DetalheMedicoViewModelTest`; renderização (grade em 3 colunas, TalkBack) só no aparelho.
- `./gradlew assembleDebug testDebugUnitTest`: BUILD SUCCESSFUL, 100 testes, 0 falhas.

**2026-09-21 — patches do gate de revisão (ver Review Triage Log).** Slot selecionado exposto ao TalkBack (`selected`), texto de antecedência derivado da constante, dica "Selecione um dia para ver os horários." e import não usado removido. Resultado final: BUILD SUCCESSFUL, 100 testes, 0 falhas.

## Spec Change Log

## Review Triage Log

Três revisores; achados verificados contra o código real. Nenhum `intent_gap`/`bad_spec`; sem loopback.

| # | Achado | Veredito | Evidência | Rota |
|---|--------|----------|-----------|------|
| 1 | Slot selecionado só se distingue por cor/borda; TalkBack não anuncia a seleção | `medium` | `SlotCell` sobrescreve `contentDescription` sem semântica `selected`; UX-DR8 pede que estado não dependa só de cor. | **patch** — `selected` nas semantics |
| 2 | Texto "Antecedência mín. 48h" duplica o número em vez de derivar de `ANTECEDENCIA_MINIMA_HORAS` | `low` | Podem divergir se a constante mudar. Correção direta. | **patch** — interpolação na constante |
| 3 | Sem dia selecionado a tela mostra só os chips e uma área vazia | `low` | Sem orientação ao usuário; correção de uma linha. | **patch** — "Selecione um dia para ver os horários."; import não usado removido |
| 4 | Repositório (`getDoctorDetail`/`getBookedSlots`), RLS de `booked_slots` e navegação Busca -> Detalhe sem teste automatizado | `medium` | Formatos e filtro conferidos contra o servidor real (`08:00:00`, `...+00:00`, janela `gte`/`lt`, escrita -> 403); sem harness de repositório/UI. | **defer** |
| 5 | "Ocupado" inalcançável ponta a ponta: `booked_slots` não tem escritor até a 2.3 | `false` | É decisão da spec (Design Notes); o caminho de dados foi provado inserindo uma linha real e lendo como paciente. | rejeitado |
| 6 | Médico não encontrado dá erro genérico sem saída; id vazio/sem codificação na rota | `low` | Não há exclusão de médico e o id vem sempre de um card (UUID). | rejeitado |
| 7 | Horário malformado derrubaria a tela; blocos não alinhados/sobrepostos sem teste; DST | `low` | Coluna `time` do Postgres sempre devolve `HH:MM:SS` válido e há CHECK `start < end`; São Paulo sem horário de verão. | rejeitado |
| 8 | Janela/ocupação/limite de 48h ficam velhos com a tela aberta (virada da meia-noite ou fronteira de 48h); sem refresh | `low` | Exige deixar a tela aberta por horas; a garantia de verdade é o servidor na 2.3 (`book_appointment`) e o Realtime da 2.3. | rejeitado |
| 9 | Bairro carregado mas não exibido; teto de 1000 linhas; `mapNotNull` de weekday; corrida de carga sem teste; acessibilidade de contraste/`onClickLabel`; `verticalScroll` com 48 slots; sem `down` migration | `low` | Cosmético ou improvável para o volume do projeto. | rejeitado |
| 10 | `Card(onClick)` experimental / falta de `revoke` para anon | `false` | Compila sem opt-in; RLS só tem política `to authenticated` (anon lê vazio) e a escrita foi negada (403). | rejeitado |

## Design Notes

**Nota de correção (Story 5.1, 2026-09-22):** a premissa de "Slots de 15 minutos" usada nesta spec foi substituída — consulta de 30 min com 15 min de intervalo (grade de 45 em 45 min). O Médico continua escolhendo seu próprio horário de início/fim (nada mudou nisso), mas agora dentro do horário fixo da clínica, 08h-18h (antes 06h-22h). Ver `spec-5-1-grade-horarios-30min.md` e `sprint-change-proposal-2026-09-22.md`.

A ocupação só passa a existir de verdade na Story 2.3 (agendar + trigger `sync_booked_slots`). Nesta história `booked_slots` nasce vazia e o "Ocupado" é validado por testes unitários e por uma linha inserida à mão só para conferir. Slot selecionado apenas destaca; confirmar é da 2.3.

## Verification

**Commands:**
- `./gradlew assembleDebug testDebugUnitTest` (com `JAVA_HOME` do JBR do Android Studio) -- expected: `BUILD SUCCESSFUL`, todos os testes passam
- `npx supabase db push` -- expected: migração `0004` aplicada
- `npx supabase db query --linked` inserindo e depois apagando um horário de teste em `booked_slots`, e lendo a tabela como paciente autenticado -- expected: linha visível só com `doctor_id`/`start_time`; sem escrita possível pelo cliente

**Manual checks (if no CLI):**
- No celular: Busca -> card -> Detalhe; conferir carrossel, grade (desabilitados hoje e amanhã por antecedência), "Ocupado" com o horário inserido à mão, "Tentar novamente" em modo avião e voltar à Busca
