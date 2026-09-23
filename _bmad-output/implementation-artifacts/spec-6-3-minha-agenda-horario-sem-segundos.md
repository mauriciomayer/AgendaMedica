---
title: 'Minha Agenda mostra o horário de atendimento sem os segundos'
type: 'bugfix'
created: '2026-09-23'
status: 'done'
route: 'oneshot'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Na tela Minha Agenda do médico, cada bloco de horário de atendimento é exibido como `"${block.dia.label}: ${block.startTime} - ${block.endTime}"` (`MinhaAgendaScreen.kt:215`), onde `startTime`/`endTime` vêm cru do Postgres (`doctor_schedules.start_time`/`end_time`, tipo `time`), que serializa como `"HH:mm:ss"` — resultando em algo como "Segunda: 08:00:00 - 18:00:00" em vez de "Segunda: 08:00 - 18:00". O comentário em `DoctorProfile.kt:6` ("HH:mm", kept as text for display) está desatualizado/incorreto: o valor real inclui segundos.

**Approach:** Formatar `startTime`/`endTime` como `HH:mm` apenas no ponto de exibição em `MinhaAgendaScreen.kt`, via `LocalTime.parse(valor).format(DateTimeFormatter.ofPattern("HH:mm"))` — mesma técnica de parsing já usada sobre esses mesmos campos em `AgendaSlots.kt:108-109` (`LocalTime.parse(block.startTime)`), sem tocar a camada de dados/Repository nem o formato armazenado no Postgres. Correção cosmética, local, sem mudança de contrato.

</frozen-after-approval>

## Implementation Notes

`MinhaAgendaScreen.kt`: adicionada `private fun formatHora(hora: String): String = LocalTime.parse(hora).format(DateTimeFormatter.ofPattern("HH:mm"))` (perto de `initialsOf`, mesma seção de helpers privados de formatação) e trocado o `Text` do bloco de horário (linha ~215) para usar `formatHora(block.startTime)`/`formatHora(block.endTime)`. Nenhum outro arquivo exibe `ScheduleBlock.startTime`/`endTime` como texto (confirmado por grep em `ui/`) — `DetalheMedicoScreen`/`DetalheMedicoViewModel` só usam esses campos para calcular horários disponíveis via `AgendaSlots.kt`, nunca para exibição direta. Escopo confirmado como um único arquivo. `./gradlew assembleDebug testDebugUnitTest` -> `BUILD SUCCESSFUL`. Sem teste novo: é uma função privada de formatação dentro de um Composable, e este projeto não testa Composables no nível unitário (só ViewModels) — confirmado como convenção já estabelecida, não uma lacuna desta story.

Pós-revisão (blind-hunter): `formatHora` virou `internal` (era `private`) e ganhou `MinhaAgendaScreenKtTest.kt` com 2 testes JUnit puros; comentário desatualizado em `DoctorProfile.kt:6` corrigido; entrada nova em `deferred-work.md` sobre o campo `String` cru não estar tipado/formatado na origem.

## Review Triage Log

Camada blind-hunter (subagente sem contexto prévio), N calculado = 4, 7 achados reportados.

| Achado | Veredito | Evidência |
|---|---|---|
| `formatHora` sem try/catch pode derrubar a tela se o valor for malformado | `false` | Mesma técnica já em produção sem guarda em `AgendaSlots.kt:108-109`, sobre os mesmos campos `ScheduleBlock.startTime/endTime` (Story 5.1, já testado/commitado) — não é risco novo introduzido por esta story |
| Lógica de parsing duplicada em vez de um utilitário compartilhado | `low`, rejeitado | Correção exigiria extrair um helper cross-package (domain + ui); `DetalheMedicoScreen.kt:58` já mostra o padrão estabelecido de constante de formatação privada por tela — não é inconsistente com a convenção atual |
| Comentário desatualizado em `DoctorProfile.kt:6` dizendo "HH:mm" | `low`, patch | Correção trivial de uma linha; aplicada |
| Nenhum teste cobre `formatHora` | `low`, patch | `formatHora` é uma função pura `String -> String`; bastou torná-la `internal` e testar diretamente com JUnit puro, sem precisar de infraestrutura de teste de Compose |
| `sprint-status.yaml` ainda em `in-progress` apesar da implementação pronta | `false` | Sequência normal do workflow — o passo Finalize Spec (que muda para `review`) só roda depois da classificação dos achados, que é exatamente este passo |
| Correção só no ponto de exibição, não no tipo/formato de origem (`ScheduleBlock.startTime` continua `String` cru) | `defer` | Observação estrutural válida, mas o Intent já definiu deliberadamente o escopo como "sem tocar a camada de dados/Repository"; registrado em `deferred-work.md` |
| `DateTimeFormatter.ofPattern("HH:mm")` sem locale fixo | `false` | Padrão idêntico já em produção sem locale fixo em `DetalheMedicoScreen.kt:58` (`HORA_FORMAT`); pattern só numérico ("HH:mm"), sem símbolos sensíveis a locale |

