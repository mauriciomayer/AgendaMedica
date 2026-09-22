---
title: 'Sistema usa consultas de 30 minutos com intervalo de 15, dentro do horário da clínica (08h-18h)'
type: 'bugfix'
created: '2026-09-22'
status: 'review'
route: 'dispatch'
review_loop_iteration: 0
context: ['{project-root}/_bmad-output/planning-artifacts/sprint-change-proposal-2026-09-22.md']
baseline_commit: 'b14a3e93baddbd0939a8d7a78e220f297ea3d5bd'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** O produto trata "Slot" como um intervalo de 15 minutos e deixa o Médico escolher livremente seu horário de início/fim (06:00-22:00) — as duas premissas estão erradas (Sprint Change Proposal, 2026-09-22, já aprovado). O correto: cada Consulta dura 30 minutos, com 15 minutos de intervalo obrigatório até a próxima; e o Médico continua escolhendo seu próprio horário de início/fim (nada muda nisso), mas agora só dentro do horário fixo da clínica, 08h às 18h.

**Approach:** Trocar a aritmética da grade (cliente e função de validação no banco) de "passo de 15 min" para "passo de 45 min (30 consulta + 15 intervalo), com a consulta inteira cabendo antes do fim do bloco"; estreitar a lista de horários oferecida no cadastro do médico para 08:00-18:00; e impor esse limite tanto na Edge Function quanto por uma `CHECK constraint` no banco (garantia final, NFR1).

## Boundaries & Constraints

**Always:**
- Consulta = 30 min; intervalo obrigatório até a próxima = 15 min; logo, posições de início válidas ficam a 45 min umas das outras, contadas a partir do `start_time` do bloco daquele Médico naquele dia da semana (nunca uma grade global fixa desde a meia-noite).
- Uma posição de início só é válida se (a) estiver alinhada a essa grade de 45 min a partir do `start_time` do bloco, **e** (b) a consulta inteira de 30 min couber antes do `end_time` do bloco (equivalente a `posição + 30min <= fim`, generalizando o "fim exclusivo" que já existia para 15 min).
- O horário de início/fim de cada Médico continua livremente escolhido por ele, por dia, exatamente como hoje (mesmos dropdowns, mesmo fluxo de cadastro/edição) — só a lista de opções oferecida muda de "06:00 a 22:00" para "08:00 a 18:00" (mesmo passo de 30 min).
- Um horário de início/fim fora de 08:00-18:00 é rejeitado em duas camadas independentes: a Edge Function `register-doctor` (`INVALID:`) e uma `CHECK constraint` nova em `doctor_schedules` (garantia no armazenamento, NFR1) — nunca só uma das duas.
- A função `assert_slot_bookable` (já usada internamente por `book_appointment` e `reschedule_appointment`, que não precisam ser recriadas) é a única peça do banco que muda; continua lendo o bloco de `doctor_schedules` genericamente por `doctor_id`+`weekday`, nunca cravando 08:00/18:00 na própria aritmética de alinhamento.
- Migração nova (não editar `0001`-`0008`): normaliza (`UPDATE`) linhas existentes de `doctor_schedules` que ficariam fora de 08:00-18:00 antes de adicionar a `CHECK constraint`, para a migração não falhar em dados de teste já existentes.
- `booked_slots`, o índice único parcial de `appointments`, a assinatura Realtime e as demais funções (`book_appointment`, `cancel_appointment`, `reschedule_appointment`) continuam exatamente como estão — a nova grade já garante 45 min de espaçamento entre posições canônicas (maior que os 30 min de duração), então a checagem por instante exato continua sendo suficiente contra conflito.
- Mensagens de erro continuam no vocabulário `CONFLICT:`/`INVALID:`/`FORBIDDEN:` (AD-9); a validação vive no banco, nunca só na UI/Edge Function.

**Never:**
- Não introduzir mais de um bloco de horário por Médico por dia da semana (continua exatamente um `start_time`/`end_time` por dia, como hoje).
- Não alterar a antecedência mínima de 48h nem a janela de cancelamento de 24h — só a granularidade/limite do horário de atendimento.
- Não editar `book_appointment`, `cancel_appointment` nem `reschedule_appointment` (0005/0006) — só `assert_slot_bookable` precisa ser recriada.
- Não remover os campos "Início"/"Fim" do cadastro do médico nem seu fluxo de edição — só estreitar a lista de opções.
- Não adicionar dependência nova.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Horário de médico dentro de 08h-18h | ex.: 08:00-12:00, ou 14:00-18:00 | Cadastro/edição aceitos | N/A |
| Horário de médico exatamente 08:00-18:00 | limites inclusivos | Aceito | N/A |
| Horário de médico fora de 08h-18h | ex.: início 07:45, ou fim 18:15, ou 00:00-23:59 (padrão antigo) | Rejeitado pela Edge Function | `INVALID:` |
| Bypass da Edge Function (INSERT direto em `doctor_schedules`) | horário fora de 08h-18h via SQL | Rejeitado pela `CHECK constraint` | Erro de constraint do Postgres |
| Consulta em posição alinhada à grade de 45 min do bloco | ex.: bloco 08:00-12:00, início 09:30 | Aceita | N/A |
| Consulta em posição não alinhada | ex.: início 09:15 (não é 08:00+45×N) | Rejeitada | `INVALID:` |
| Consulta alinhada mas que não cabe antes do fim do bloco | bloco 08:00-12:00, início 11:45 (terminaria 12:15) | Rejeitada | `INVALID:` |
| Duas consultas consecutivas válidas (45 min de distância) | ex.: 08:00 e 08:45 para o mesmo médico | Ambas aceitas, sem conflito | N/A |
| Reagendar para posição inválida (grade ou limite do bloco) | via `reschedule_appointment` | Rejeitado, consulta original inalterada | `INVALID:` |
| Dados já existentes fora de 08h-18h ao aplicar a migração | linha de teste com `00:00`-`23:59` | Normalizada para caber em 08:00-18:00 (ou 08:00-18:00 se o recorte inverteria início/fim) | N/A |

</frozen-after-approval>

## Code Map

- `app/src/main/java/com/agendamedica/app/domain/agenda/AgendaSlots.kt` -- `SLOT_MINUTOS` (linha 29) e o laço em `slotsDoDia` (linhas 96-106); trocar por `DURACAO_CONSULTA_MINUTOS`/`INTERVALO_ENTRE_CONSULTAS_MINUTOS`/`ESPACAMENTO_SLOTS_MINUTOS` e a nova condição de ajuste (ver Design Notes para o código exato)
- `app/src/test/java/com/agendamedica/app/domain/agenda/AgendaSlotsTest.kt` -- `block` (linha 24, widen para "08:00"-"10:00"), teste `block end is exclusive...` (linhas 26-34, reescrever expectativas), teste `exactly 48h...` (linha 40, bloco vira "08:00"-"08:30"); os demais testes do carrossel não usam `slotsDoDia` e não precisam mudar
- `app/src/main/java/com/agendamedica/app/ui/doctor/CadastroMedicoViewModel.kt` -- `HORARIOS_DISPONIVEIS` (linhas 30-36): `for (hour in 6..21) {...}; add("22:00")` vira `for (hour in 8..17) {...}; add("18:00")`; `startTime`/`endTime` default já são "08:00"/"18:00" (linhas 46-47), não precisam mudar
- `supabase/functions/register-doctor/index.ts` -- `validatePayload`, laço de `schedules` (linhas 112-123): acrescentar `schedule.startTime >= "08:00" && schedule.endTime <= "18:00"` à condição `validTimes` (comparação de string funciona pois o formato é sempre `HH:mm` zero-padded, mesmo padrão já usado para `startTime < endTime`)
- `supabase/migrations/0001_init.sql` -- definição de `doctor_schedules` (`start_time time not null`, `end_time time not null`, `constraint doctor_schedules_time_order check (start_time < end_time)`); NÃO editar, só referência
- `supabase/migrations/0006_cancel_reschedule.sql` -- `assert_slot_bookable` atual (recriada aqui, não editada no arquivo original); `book_appointment`/`reschedule_appointment` chamam-na pelo nome e NÃO precisam ser recriadas
- Nova migração `supabase/migrations/0009_grade_horarios_30min.sql` -- ver Design Notes para o SQL completo
- `supabase/tests/concurrency-test.mjs`, `cancel-reschedule-test.mjs`, `reminders-test.mjs`, `notification-events-test.mjs` -- todos cadastram um médico de teste com `schedules: [...].map((weekday) => ({ weekday, startTime: "00:00", endTime: "23:59" }))`; trocar para `"08:00"`/`"18:00"`. Qualquer horário de consulta literal usado nesses scripts (`saoPauloSlot(dia, hh, mm)` ou SQL direto) precisa cair na grade de 45 min a partir de 08:00 (lista completa nas Design Notes) — auditar e corrigir cada chamada
- `_bmad-output/test-artifacts/checklist-teste-manual.md` -- Story 2.2, item "Selecionar um dia" menciona "de 15 em 15 minutos"; atualizar

## Tasks & Acceptance

**Execution:**
- [x] `supabase/migrations/0009_grade_horarios_30min.sql` -- normalização de dados + `CHECK constraint` em `doctor_schedules` + `assert_slot_bookable` recriada com a grade de 45 min -- AD-1, AD-4, NFR1
- [x] `supabase/functions/register-doctor/index.ts` -- validação do horário dentro de 08:00-18:00 -- AD-1
- [x] `app/.../ui/doctor/CadastroMedicoViewModel.kt` -- `HORARIOS_DISPONIVEIS` estreitada para 08:00-18:00
- [x] `app/.../domain/agenda/AgendaSlots.kt` -- nova aritmética de 45 min (30 consulta + 15 intervalo), constantes nomeadas
- [x] `app/.../domain/agenda/AgendaSlotsTest.kt` -- testes ajustados à nova grade
- [x] `supabase/tests/cancel-reschedule-test.mjs` -- nova seção "Grade de 45 min e horário da clínica (Story 5.1)": médico com horário fora de 08h-18h rejeitado (Edge Function); médico com horário exatamente 08:00-18:00 aceito; INSERT direto em `doctor_schedules` fora de 08h-18h rejeitado pela constraint; consulta alinhada aceita; consulta desalinhada rejeitada `INVALID:`; consulta alinhada mas que não cabe antes do fim do bloco rejeitada `INVALID:`; duas consultas consecutivas (45 min de distância) ambas aceitas
- [x] `supabase/tests/concurrency-test.mjs`, `cancel-reschedule-test.mjs`, `reminders-test.mjs`, `notification-events-test.mjs` -- fixture de agenda do médico de teste (`00:00`-`23:59` -> `08:00`-`18:00`) e todo horário de consulta literal ajustado para a nova grade; todos devem continuar `ALL CHECKS PASSED`
- [x] `_bmad-output/test-artifacts/checklist-teste-manual.md` -- texto da Story 2.2 atualizado

**Acceptance Criteria:**
- Given que um Médico está se cadastrando ou editando sua agenda, when escolhe o horário de início/fim de um dia de atendimento, then as opções vão de 08:00 a 18:00; um horário fora dessa janela é rejeitado tanto pela Edge Function quanto por uma `CHECK constraint` no banco
- Given o Detalhe de um Médico com um dia selecionado cujo horário configurado é, por exemplo, 08:00-12:00, when a grade de horários é exibida, then os horários possíveis começam às 08:00 e seguem de 45 em 45 minutos até o último cuja consulta de 30 min termine até o fim do bloco
- Given um horário de início válido na grade, when o Paciente agenda esse horário, then a consulta ocupa 30 minutos e o próximo horário agendável para aquele Médico está pelo menos 45 minutos à frente
- Given uma chamada direta às funções de agendar/reagendar com um horário fora da grade de 45 min do bloco daquele Médico, ou cuja consulta ultrapassaria o fim do bloco, when a chamada ocorre, then é rejeitada com `INVALID:`, sem exceção

## Implementation Notes

- Migração `0009`: a normalização de dados foi implementada como um único `UPDATE` com `CASE` (em vez dos dois `UPDATE`s separados sugeridos nas Design Notes), porque a versão em dois passos podia violar transitoriamente a constraint pré-existente `doctor_schedules_time_order` (start_time < end_time) para uma linha totalmente fora da nova janela (ex.: 18:00-20:00 viraria 18:00-18:00 só com o recorte, antes do segundo UPDATE corrigir a inversão). O `CASE` lê os valores originais da linha uma única vez, então o resultado já sai consistente. Verificado à mão contra os casos de borda (linha antes da janela, depois da janela, cobrindo a janela toda, invertendo exatamente no limite).
- `app/.../ui/patient/DetalheMedicoViewModelTest.kt` (não listado no Code Map da spec) precisou de ajuste: sua fixture de agenda (`08:00`-`09:00`) passou a gerar só 1 posição na nova grade de 45 min, insuficiente para os testes que indexam `slots[1]`. Alargada para `08:00`-`10:00` (3 posições), mesma lógica usada em `AgendaSlotsTest.kt`.
- Migração aplicada com `npx supabase db push`; `register-doctor` implantada com `npx supabase functions deploy`.

## Spec Change Log

## Review Triage Log

Três revisores paralelos (SQL/migração, domínio Kotlin/testes, scripts de teste/escopo). Um blocker real, corrigido e reverificado contra o banco hospedado.

| # | Achado | Severidade | Ação |
|---|--------|-----------|------|
| 1 | `assert_slot_bookable`: a checagem "cabe antes do fim do bloco" usava `v_local::time + interval '30 minutes' > end_time`, que sofre wraparound de 24h no Postgres (`time + interval` "dá a volta" à meia-noite) — um horário como 23:45 (fora de qualquer bloco 08h-18h) passava pela validação sem erro, porque o resultado "embrulhado" (00:15) parecia caber dentro do horário e, coincidentemente, ainda batia com o alinhamento de 45 min a partir de blocos que começam às 08:00/08:30. Já estava aplicado no banco hospedado quando encontrado | blocker | Corrigido: comparação trocada para aritmética em segundos (`extract(epoch from v_local::time)::int + 1800 > extract(epoch from v_block.end_time)::int`), sem risco de wraparound. Função corrigida reaplicada diretamente no banco hospedado (o `create or replace` já muda o comportamento sem precisar de nova migração, já que a 0009 ainda não tinha sido commitada); arquivo da migração também corrigido. Teste de regressão adicionado (`cancel-reschedule-test.mjs`, agendamento às 23:45 -> `INVALID:`), reexecutado e confirmado |
| 2 | Dois comentários de doc desatualizados (`DetalheMedicoScreen.kt`, `DetalheMedicoViewModel.kt`) ainda diziam "the 15-minute slot grid"/"the 15-minute grid" | minor | Corrigido para "45-minute" |
| 3 | A checagem de bypass da Edge Function (`cancel-reschedule-test.mjs`) só verificava que o INSERT falhou, sem confirmar que era a `CHECK constraint` certa que disparou (um erro de conexão transitório passaria igual) | minor | Corrigido: agora verifica que a mensagem de erro contém `doctor_schedules_within_clinic_hours`. Ao aplicar, achado um bug secundário no helper `sql()` do script: `r.stderr \|\| r.stdout` descartava o `stdout` (onde estava o erro real do Postgres) sempre que o `stderr` tinha qualquer coisa, mesmo só avisos do `npm`. Corrigido para concatenar os dois streams |
| 4 | Faltava um caso de teste com faixa de horário estritamente dentro de 08h-18h (ex.: 09:00-15:00) — só o limite exato e os casos fora da janela estavam cobertos | minor | Adicionado: cadastro de médico com 09:00-15:00 -> aceito |
| 5 | `GRID_SLOTS`/`underLeadTimeSlot()` duplicados verbatim entre `cancel-reschedule-test.mjs` e `concurrency-test.mjs`, somando-se à duplicação de helpers já registrada | minor | Adicionado um `update` à entrada existente em `deferred-work.md` (spec-3-2), sem criar entrada nova |
| 6 | Sem constraint `UNIQUE(doctor_id, weekday)` em `doctor_schedules` — `assert_slot_bookable` pegaria uma linha arbitrária se houvesse duplicatas | nit | Aceito sem ação: pré-existente, nunca produzível pelos caminhos da aplicação (cadastro rejeita dia duplicado), fora do escopo desta correção |
| 7 | Linha da matriz "Reagendar para posição inválida" só tem o lado "desalinhado" testado via `reschedule_appointment`; o lado "não cabe antes do fim do bloco" só é testado via `book_appointment` | nit | Aceito sem ação: ambos delegam para a mesma `assert_slot_bookable`, risco desprezível |
| 8 | Code Map da spec não listava `DetalheMedicoViewModelTest.kt`, que precisou de ajuste (ver Implementation Notes) | nit | Aceito sem ação: já documentado em Implementation Notes |
| 9 | `reminders-test.mjs` falhou numa primeira tentativa de reverificação por causa de consultas reais pendentes no projeto (`assertNoForeignDueAppointments`) | n/a | Não é defeito desta história: risco pré-existente já registrado em `deferred-work.md` (spec-3-1). Confirmado `ALL CHECKS PASSED` numa reexecução minutos depois |

## Design Notes

**SQL completo da migração `0009_grade_horarios_30min.sql`:**

```sql
-- Normaliza linhas existentes que ficariam fora da nova janela, antes da CHECK constraint
-- (mesmo padrão de backfill da Story 2.1 para localização).
update public.doctor_schedules
set start_time = greatest(start_time, time '08:00'),
    end_time = least(end_time, time '18:00');

update public.doctor_schedules
set start_time = time '08:00', end_time = time '18:00'
where start_time >= end_time;

alter table public.doctor_schedules
  add constraint doctor_schedules_within_clinic_hours
  check (start_time >= time '08:00' and end_time <= time '18:00');

create or replace function public.assert_slot_bookable(p_doctor_id uuid, p_start_time timestamptz)
returns void
language plpgsql
stable
security definer
set search_path = public, pg_temp
as $$
declare
  v_local timestamp := p_start_time at time zone 'America/Sao_Paulo';
  v_block public.doctor_schedules%rowtype;
begin
  if extract(second from v_local) <> 0 then
    raise exception 'INVALID: horário fora da grade de horários';
  end if;

  select * into v_block
  from public.doctor_schedules s
  where s.doctor_id = p_doctor_id
    and s.weekday = extract(dow from v_local)::int;

  if not found then
    raise exception 'INVALID: horário fora da agenda do médico';
  end if;

  if v_local::time < v_block.start_time
     or v_local::time + interval '30 minutes' > v_block.end_time then
    raise exception 'INVALID: horário fora da agenda do médico';
  end if;

  if extract(epoch from (v_local::time - v_block.start_time))::int % 2700 <> 0 then
    raise exception 'INVALID: horário fora da grade de horários';
  end if;

  if p_start_time < now() + interval '48 hours' then
    raise exception 'CONFLICT: lead_time';
  end if;
end;
$$;
```
(`2700` = 45 minutos em segundos.)

**Novo `AgendaSlots.kt` (trecho):**

```kotlin
const val DURACAO_CONSULTA_MINUTOS = 30L
const val INTERVALO_ENTRE_CONSULTAS_MINUTOS = 15L
const val ESPACAMENTO_SLOTS_MINUTOS = DURACAO_CONSULTA_MINUTOS + INTERVALO_ENTRE_CONSULTAS_MINUTOS // 45

// dentro de slotsDoDia, substituindo o laço atual:
schedule.filter { it.dia == diaSemana }.forEach { block ->
    val fim = LocalTime.parse(block.endTime)
    var t = LocalTime.parse(block.startTime)
    while (!t.plusMinutes(DURACAO_CONSULTA_MINUTOS).isAfter(fim)) {
        horas.add(t)
        val next = t.plusMinutes(ESPACAMENTO_SLOTS_MINUTOS)
        if (next <= t) break // wrapped past midnight
        t = next
    }
}
```

**Grade válida para um médico de teste com horário 08:00-18:00 (usar nos scripts):** 08:00, 08:45, 09:30, 10:15, 11:00, 11:45, 12:30, 13:15, 14:00, 14:45, 15:30, 16:15, 17:00 (13 posições; 17:45 ficaria de fora por terminar às 18:15).

**Testes de `AgendaSlotsTest.kt` a reescrever:**
- `block` (linha 24) passa a ser `ScheduleBlock(DiaSemana.SEGUNDA, "08:00:00", "10:00")` (mantém o formato "com segundos" para continuar testando o parse), dando 3 posições (08:00, 08:45, 09:30) — suficiente para o teste "ocupado wins" (usa `slots[1]`).
- Teste do bloco 08:00-12:00 (hoje "block end is exclusive...") passa a esperar exatamente `[08:00, 08:45, 09:30, 10:15, 11:00]` (5 posições; 11:45 ficaria de fora por terminar às 12:15).
- Teste "exactly 48h..." usa um bloco que caiba exatamente 1 consulta: `ScheduleBlock(DiaSemana.QUARTA, "08:00", "08:30")` (era "08:00"-"08:15").
- Os testes de carrossel (`diasCarrossel`) não dependem da aritmética de slots — não precisam mudar.

Manter os literais `"08:00"`/`"18:00"` em sincronia entre `register-doctor/index.ts` e a `CHECK constraint` da migração — mesmo padrão de duplicação consciente já usado no projeto para Especialidade/Convênio/Localidade (Design Notes da Story 2.1).

## Verification

**Commands:**
- `./gradlew assembleDebug testDebugUnitTest` (com `JAVA_HOME` do JBR do Android Studio) -- expected: `BUILD SUCCESSFUL`, todos os testes passam
- `npx supabase db push` -- expected: migração `0009` aplicada (revisar o SQL antes — normalização de dados + constraint + função recriada)
- `npx supabase functions deploy register-doctor --project-ref vuqvizzkdeiseyunjrms --use-api` -- expected: função publicada
- `node supabase/tests/cancel-reschedule-test.mjs`, `node supabase/tests/concurrency-test.mjs`, `node supabase/tests/reminders-test.mjs`, `node supabase/tests/notification-events-test.mjs` -- expected: todos ALL CHECKS PASSED com a nova grade; dados de teste removidos

**Manual checks (if no CLI):**
- No celular: cadastrar um médico e conferir que o seletor de horário só oferece 08:00-18:00; abrir o Detalhe de um médico existente e conferir que a grade mostra os horários espaçados de 45 em 45 minutos
