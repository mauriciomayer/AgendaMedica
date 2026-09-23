---
title: 'Horário de atendimento do médico é tipado como hora, não como texto'
type: 'refactor'
created: '2026-09-23'
status: 'review'
route: 'oneshot'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `ScheduleBlock.startTime`/`endTime` (`DoctorProfile.kt`) são `String` com o texto cru do Postgres `time` (`"08:00:00"`). Isso causou o bug dos segundos na Minha Agenda (Story 6.3, corrigido só no ponto de exibição) e obriga `AgendaSlots.kt:108-109` e `MinhaAgendaScreen.formatHora` a fazerem `LocalTime.parse` cada um por conta própria.

**Approach:** `ScheduleBlock.startTime`/`endTime` viram `java.time.LocalTime`. O parse acontece uma única vez, nos dois pontos do `DoctorRepository` que constroem `ScheduleBlock` (`getDoctorDetail` e `getMyProfile`). `AgendaSlots` passa a usar os campos direto; `MinhaAgendaScreen.formatHora` recebe `LocalTime` e só formata. Sem mudança de comportamento visível. `ScheduleInput` (payload de cadastro, `"HH:mm"` como texto para a Edge Function) e o restante do cadastro NÃO mudam.

</frozen-after-approval>

## Implementation Notes

`ScheduleBlock.startTime`/`endTime` agora são `LocalTime`. O parse (`LocalTime.parse`, ISO, aceita `"08:00"` e `"08:00:00"`) acontece só em `DoctorRepository` (`getDoctorDetail` e `getMyProfile`). `AgendaSlots.slotsDoDia` usa os campos direto; `MinhaAgendaScreen.formatHora(LocalTime)` só formata, com o `DateTimeFormatter` hoistado numa constante. Testes ajustados: `AgendaSlotsTest` e `DetalheMedicoViewModelTest` constroem blocos com `LocalTime.of(...)`; `MinhaAgendaScreenKtTest` testa `formatHora` sobre `LocalTime`. `./gradlew assembleDebug testDebugUnitTest` -> `BUILD SUCCESSFUL` antes e depois da revisão.

Horários válidos são garantidos pela origem: a CHECK de `doctor_schedules` (migration 0009) só admite `start_time >= 08:00` e `end_time <= 18:00`, então `24:00:00` ou valores malformados não existem no banco; uma falha de parse cairia no `runCatching` do Repository como qualquer outra falha de leitura.

## Review Triage Log

Camada blind-hunter (N=4, 7 achados).

| Achado | Veredito | Evidência / rota |
|---|---|---|
| Sem teste do parse no Repository (`"HH:mm:ss"` nunca exercitado) | `low`, rejeitado | O corpo dos Repositories não tem cobertura por depender de Ktor/Supabase (já em `deferred-work.md`, spec-1-1); testar `LocalTime.parse` isolado seria testar o JDK |
| Dois pontos de mapeamento duplicados com tratamento de falha diferente (`mapNotNull` vs `first`) | `low`, rejeitado | A diferença de falha é pré-existente e não foi introduzida por esta story; unificar mudaria comportamento fora do escopo |
| Spec sem registro de formato/falha de parse (`24:00:00`) | `false` (+ nota) | A CHECK de `doctor_schedules` impede `24:00:00`/malformados; registrado nas Implementation Notes |
| Testes perderam a mistura `"08:00:00"`/`"10:00"`; sem caso com segundos | `low`, rejeitado | A mistura de formatos de texto deixou de existir com o tipo; o app só grava horários "HH:mm" e o servidor rejeita segundos |
| `DateTimeFormatter` recriado a cada chamada de `formatHora` | `low`, patch | Correção direta de uma linha; hoistado para `HORA_FORMAT` |
| `sprint-status`/spec `in-progress` com código pronto; `context: []` | `false` | Estado normal no meio do fluxo; a finalização (`review`) acontece agora |
| Comparação `startTime < endTime` em `CadastroMedicoViewModel` continua sobre texto | `low`, rejeitado | Pré-existente e fora do escopo definido no Intent (formato "HH:mm" zero-preenchido compara corretamente) |
