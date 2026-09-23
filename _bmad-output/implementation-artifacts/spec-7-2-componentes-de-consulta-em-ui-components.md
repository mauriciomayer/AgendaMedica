---
title: 'Componentes de consulta vivem em ui/components'
type: 'refactor'
created: '2026-09-23'
status: 'done'
route: 'oneshot'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `MinhaAgendaScreen`/`MinhaAgendaViewModel` (pacote `doctor`) importam `ConsultaCard`, `CancelConfirmDialog`, `formatarDataHora` (de `MinhasConsultasScreen.kt`/`ConfirmacaoScreen.kt`) e `MSG_JANELA_24H` (de `DetalheMedicoViewModel.kt`), todos do pacote `patient`: a feature do médico depende da feature do paciente.

**Approach:** Mover esses símbolos, sem mudar comportamento nem aparência, para um novo arquivo `ui/components/ConsultaComponents.kt` (pacote `com.agendamedica.app.ui.components`): `ConsultaCard`, `StatusBadge` (privado, acompanha o card), `CancelConfirmDialog`, `formatarDataHora` e `MSG_JANELA_24H`. Telas e ViewModels dos dois lados passam a importar de `ui/components`. Resultado: nenhum arquivo do pacote `doctor` importa algo do pacote `patient` (o `AgendaMedicaNavHost` é de navegação e fica de fora). Refatoração puramente estrutural.

</frozen-after-approval>

## Implementation Notes

Novo arquivo `ui/components/ConsultaComponents.kt` com `ConsultaCard`, `StatusBadge` (privado), `CancelConfirmDialog`, `formatarDataHora`/`DATA_HORA_FORMAT` e `MSG_JANELA_24H`, copiados sem mudança de comportamento (o revisor comparou cada trecho removido contra o novo arquivo). Imports ajustados em `MinhasConsultasScreen`, `ConfirmacaoScreen`, `DetalheMedicoViewModel`, `MinhasConsultasViewModel`, `MinhaAgendaScreen`, `MinhaAgendaViewModel` e nos testes. `ConfirmacaoFormatTest` virou `ui/components/FormatarDataHoraTest`. Verificação: `grep` por `import com.agendamedica.app.ui.patient` fora do pacote `patient` e do `AgendaMedicaNavHost` retorna vazio; `./gradlew assembleDebug testDebugUnitTest` -> `BUILD SUCCESSFUL` antes e depois da revisão.

## Review Triage Log

Camada blind-hunter (N=5, 7 achados). Equivalência do código movido confirmada pelo revisor.

| Achado | Veredito | Evidência / rota |
|---|---|---|
| Import `JANELA_ALTERACAO_HORAS` ficou sem uso em `DetalheMedicoViewModel` | `low`, patch | Único uso era a constante que saiu; removido |
| Imports novos fora de ordem alfabética em 6 arquivos | `low`, rejeitado | Os arquivos já não seguiam ordenação estrita antes (ex.: `MinhaAgendaScreen`); reordenar é ruído sem efeito |
| `start: java.time.Instant` totalmente qualificado no `ConsultaCard` | `low`, patch | Trocado por `Instant` (já importado no arquivo novo), sem efeito de comportamento |
| `MSG_JANELA_24H` sem KDoc, num arquivo de UI, longe das outras `MSG_*`; comentário cita o ID da spec | `low`, rejeitado | O ID de spec em comentários é convenção do repositório; a constante depende de `ConsultaCard` só por co-localização com quem a exibe e é usada pelos dois lados |
| `ConfirmacaoFormatTest` com nome ligado a uma tela | `low`, patch | Renomeado para `FormatarDataHoraTest` (a função agora é compartilhada por 3 telas) |
| Sem teste de UI/texto da constante para provar "puramente estrutural" | `low`, rejeitado | Sem infra de teste de UI Compose (spike na Story 7.3); os testes de ViewModel comparam com a própria constante |
| `Locale("pt","BR")` deprecado em JDKs novos | `low`, rejeitado | Carregado sem mudança; compila sem erro; fora do escopo de uma mudança estrutural |

