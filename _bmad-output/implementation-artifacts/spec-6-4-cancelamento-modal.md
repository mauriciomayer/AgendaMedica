---
title: 'Cancelamento de consulta usa uma janela de confirmação, não mais inline no card'
type: 'feature'
created: '2026-09-23'
status: 'review'
route: 'dispatch'
review_loop_iteration: 0
context: []
baseline_commit: '0776a0204c19054653c2836ec8ab567eff67bfb0'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Hoje, cancelar uma consulta (Minhas Consultas do paciente, Minha Agenda do médico) mostra "Cancelar esta consulta?" com botões Sim/Não *inline dentro do próprio card* (`ConsultaCard`, compartilhado pelas duas telas). Um toque perdido ao rolar a lista, ou tocar em outra parte da tela, não fecha nada por engano hoje — mas também nada impede um toque acidental de acertar o Sim/Não do card errado numa lista longa. O usuário decidiu que uma janela modal bloqueante é mais segura contra cancelamento acidental (reversão de UX-DR5/Stories 2.4-2.5, confirmada explicitamente).

**Approach:** Mover a confirmação para um diálogo modal (`androidx.compose.ui.window.Dialog`) renderizado uma vez por tela (fora do `LazyColumn`), disparado quando `confirmandoId != null`, com `DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)` — nem toque fora nem o botão/gesto de voltar do sistema o fecham; só os botões Sim/Não do próprio diálogo. `ConsultaCard` perde os parâmetros `confirmando`/`cancelando`/`onSim`/`onNao` (não são mais necessários: o card volta a mostrar sempre "Cancelar"/"Reagendar"). Nenhuma mudança em `MinhasConsultasViewModel`/`MinhaAgendaViewModel` — `confirmandoId`, `cancelandoId`, `onCancelarClick`, `onCancelarSim`, `onCancelarNao` já implementam toda a máquina de estados necessária (confirmado por investigação: `cancelandoId` só é setado enquanto `confirmandoId` do mesmo id ainda está setado, então o diálogo naturalmente permanece aberto mostrando "Cancelando..." até a chamada resolver).

## Boundaries & Constraints

**Always:**
- O modal só fecha por toque explícito em "Sim" ou "Não" (`DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)`, `onDismissRequest = {}`).
- Sim e Não têm o mesmo tamanho (`Modifier.weight(1f)` num `Row`, mesmo padrão já usado hoje na confirmação inline) e mantêm o `contentDescription` nomeando a consulta (`"Sim/Não cancelar a consulta com $quem"`, já existente).
- Enquanto `cancelandoId` corresponde ao id em confirmação, o botão Sim mostra "Cancelando..." e ambos os botões ficam desabilitados — mesmo comportamento visual de hoje, agora dentro do diálogo.
- Aplicar a mesma mudança em `MinhasConsultasScreen.kt` e `MinhaAgendaScreen.kt`, já que as duas usam o mesmo `ConsultaCard`.

**Never:**
- Não mexer em `MinhasConsultasViewModel`/`MinhaAgendaViewModel`, na janela de 24h, no cancelamento em si (`cancelAppointment`) nem no evento de notificação para a outra parte — só a UI de confirmação muda.
- Não adicionar biblioteca nova de diálogo — usar `androidx.compose.ui.window.Dialog`/`DialogProperties`, já disponíveis via Compose UI.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Toque em "Cancelar" num card cancelável | `confirmandoId` vira o id do card | Diálogo abre, nomeando a consulta (`quem`) | N/A |
| Toque fora do diálogo aberto | diálogo aberto | Nada muda, diálogo continua aberto | N/A |
| Botão/gesto de voltar do sistema com diálogo aberto | diálogo aberto | Nada muda, diálogo continua aberto, tela por trás não navega | N/A |
| Toque em "Não" | diálogo aberto | Diálogo fecha, `confirmandoId` volta a `null`, nada é cancelado | N/A |
| Toque em "Sim" | diálogo aberto | Diálogo mostra "Cancelando...", depois fecha seguindo o resultado já existente (sucesso remove o card / janela fechada mostra aviso / falha mostra mensagem) | Igual ao fluxo já existente, inalterado |
| `load()` encontra a consulta já cancelada/bloqueada enquanto o diálogo está aberto | `confirmandoId` setado, reload roda | Diálogo fecha automaticamente (guard já existente em `load()` zera `confirmandoId`) | N/A |

</frozen-after-approval>

## Code Map

- `app/src/main/java/com/agendamedica/app/ui/patient/MinhasConsultasScreen.kt:207-227` -- remover o bloco `if (confirmando) {...} else {...}` de dentro de `ConsultaCard`; o card sempre renderiza o `else` (Cancelar/Reagendar), sem mais precisar de `confirmando`/`cancelando`/`onSim`/`onNao`
- `app/src/main/java/com/agendamedica/app/ui/patient/MinhasConsultasScreen.kt:165-176` -- assinatura de `ConsultaCard`: remover `confirmando: Boolean`, `cancelando: Boolean`, `onSim: () -> Unit`, `onNao: () -> Unit`
- `app/src/main/java/com/agendamedica/app/ui/patient/MinhasConsultasScreen.kt` -- criar `internal fun CancelConfirmDialog(quem: String, cancelando: Boolean, onSim: () -> Unit, onNao: () -> Unit)` (novo, ao lado de `ConsultaCard`): `Dialog` com `Surface`/`Card` (mesmo `ShapeMd`/`surfaceCard`/`borderHairline` de `ConsultaCard`) contendo o texto "Cancelar esta consulta?" + `quem` + `Row` com os dois `OutlineButton` (Sim com `borderColor = dangerInk`, Não default) -- reaproveita exatamente os botões/estilos/content-descriptions já existentes nas linhas 213-227 de hoje
- `app/src/main/java/com/agendamedica/app/ui/patient/MinhasConsultasScreen.kt:128-147` -- fora do `LazyColumn`, renderizar `CancelConfirmDialog` quando `state.consultas.firstOrNull { it.consulta.id == state.confirmandoId }` não for nulo, montando `quem` a partir desse item (`"${item.consulta.doctorName}, ${formatarDataHora(item.consulta.start)}"`) e `cancelando = state.cancelandoId != null`
- `app/src/main/java/com/agendamedica/app/ui/patient/MinhasConsultasScreen.kt:132-144` -- chamada de `ConsultaCard`: remover os args `confirmando`/`cancelando`/`onSim`/`onNao`
- `app/src/main/java/com/agendamedica/app/ui/doctor/MinhaAgendaScreen.kt` -- mesmo tratamento no call site de `ConsultaCard` (linhas ~152-164): remover args removidos, adicionar `CancelConfirmDialog` fora da lista usando `item.consulta.patientName` para `quem`
- `app/src/main/java/com/agendamedica/app/ui/components/Components.kt:71-91` -- `OutlineButton`, reaproveitado sem alteração
- `app/src/test/java/com/agendamedica/app/ui/patient/MinhasConsultasViewModelTest.kt`, `app/src/test/java/com/agendamedica/app/ui/doctor/MinhaAgendaViewModelTest.kt` -- já cobrem toda a máquina de estados (`onCancelarClick`/`onCancelarSim`/`onCancelarNao`, guards, resultado); confirmado que nenhuma mudança é necessária, pois nada nesses arquivos toca Compose UI

## Tasks & Acceptance

**Execution:**
- [x] `MinhasConsultasScreen.kt` -- simplificar `ConsultaCard` (remover params/bloco inline), criar `CancelConfirmDialog`, renderizá-lo fora da lista -- centraliza a UI de confirmação num único diálogo por tela em vez de duplicar por card
- [x] `MinhaAgendaScreen.kt` -- aplicar a mesma mudança no call site -- consistência entre as duas telas que compartilham `ConsultaCard`

**Acceptance Criteria:**
- Given uma consulta cancelável, when toco em "Cancelar", then um diálogo modal abre pedindo confirmação e nem toque fora nem o botão/gesto de voltar o fecham
- Given o diálogo aberto, when toco em "Sim", then a consulta é cancelada seguindo exatamente o fluxo já existente (janela de 24h, mensagens de erro, notificação para a outra parte)
- Given o diálogo aberto, when toco em "Não", then o diálogo fecha sem cancelar nada e sem side effects

## Implementation Notes

Implementado exatamente conforme o Code Map e as Design Notes (diff conferido linha a linha contra o `baseline_commit` pelo orquestrador, não só pelo relatório do subagente). Em `MinhaAgendaScreen.kt` o `LazyColumn` precisou ser envolvido num `Box` (não previsto explicitamente no Code Map) para o `CancelConfirmDialog` poder ficar como irmão da lista, já que o `LazyColumn` era toda a expressão do branch `uiState.profile != null ->`; mesma solução aplicada de forma implícita em `MinhasConsultasScreen.kt`, que já tinha um `Column` envolvente. `./gradlew assembleDebug testDebugUnitTest` -> `BUILD SUCCESSFUL`, verificado de forma independente pelo orquestrador antes e depois da revisão.

**Matrix Test Audit:** das 6 linhas do I/O Matrix, 4 já são cobertas por testes de ViewModel pré-existentes e inalterados por esta story (linha "Cancelar" -> `onCancelarClick` seta `confirmandoId`; "Não"; "Sim" nos 3 desfechos; "reload fecha o diálogo" já coberta em `MinhaAgendaViewModelTest` — o guard em `load()` é idêntico nos dois ViewModels, não alterado por esta story). As 2 linhas puramente de UI ("toque fora não fecha", "voltar do sistema não fecha") não têm nenhum teste automatizado cobrindo-as: o projeto não tem `src/androidTest`/Compose-UI-test/Robolectric configurado (confirmado em `app/build.gradle.kts:118-119`, mesma limitação já registrada em `deferred-work.md` para a Splash Screen). Adicionar essa infraestrutura para uma única story está fora de proporção; ver `deferred-work.md`.

Pós-revisão: KDoc de `CancelConfirmDialog` ajustado para não sugerir que apenas Sim/Não fecham o diálogo — um reload que encontra a consulta sumida também fecha (comportamento já existente e intencional, só a redação do comentário estava imprecisa). `./gradlew assembleDebug testDebugUnitTest` -> `BUILD SUCCESSFUL` novamente após o ajuste.

## Spec Change Log

## Review Triage Log

3 camadas rodadas em paralelo sobre o diff real (`git diff {baseline_commit}`): blind-hunter (N=4, 6 achados), edge-case-hunter (0 achados), verification-gap (1 achado, pré-verificado).

| Achado | Camada | Veredito | Evidência / rota |
|---|---|---|---|
| `cancelando` virou `cancelandoId != null` (era `== item.id`), perdendo precisão por item | blind-hunter | `false` | O ViewModel só seta `cancelandoId` igual ao `confirmandoId` corrente (`onCancelarSim`); com um único diálogo vinculado a um único item, `!= null` e `== esseId` são exatamente equivalentes hoje |
| KDoc de `CancelConfirmDialog` diz que só Sim/Não fecham o diálogo, mas um reload que encontra a consulta sumida também fecha (via `firstOrNull` deixando de casar) | blind-hunter | `low`, patch | Comportamento é a própria linha 6 do I/O Matrix da spec (guard já existente, não é um "dismiss" do usuário) — só o comentário estava impreciso; corrigido o texto do KDoc |
| `MinhaAgendaScreen.kt` (pacote doctor) importa `CancelConfirmDialog`/`formatarDataHora` do pacote patient — acoplamento cross-feature | blind-hunter | `false` | `MinhaAgendaScreen.kt` já importava `ConsultaCard` do mesmo pacote patient antes desta story (padrão pré-existente, documentado no próprio comentário de `ConsultaCard`: "shared by Minhas Consultas... and Minha Agenda") |
| Nenhum teste automatizado cobre o contrato de não-dismissal do diálogo (toque fora / voltar) | blind-hunter + verification-gap (mesma causa raiz) | `low`/`medium` conforme relatado, `defer` | Já registrado em `deferred-work.md` (adicionado durante o Matrix Test Audit do step-03, antes mesmo da revisão); verification-gap confirmou que a entrada já existente descreve exatamente a mesma lacuna — nenhuma entrada duplicada foi criada |
| String "nome, data/hora" (`quem`) construída 3x (dentro de `ConsultaCard` + nos 2 call sites do diálogo) | blind-hunter | `low`, rejeitado | Sem dano funcional; extrair um helper compartilhado exigiria uma assinatura nova cruzando dois tipos de item diferentes (`ConsultaDoMedico`/`MinhaConsulta`) — mais que uma correção direta |
| `firstOrNull{...}?.let{ CancelConfirmDialog(...) }` duplicado nas duas telas | blind-hunter | `low`, rejeitado | Mesma razão acima — generalizar exigiria abstrair sobre dois tipos de `UiState`/item diferentes, não é uma correção trivial |

## Design Notes

**`CancelConfirmDialog`** (novo, mesmo estilo visual de `ConsultaCard`):
```kotlin
@Composable
internal fun CancelConfirmDialog(quem: String, cancelando: Boolean, onSim: () -> Unit, onNao: () -> Unit) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Surface(shape = ShapeMd, color = AgendaMedicaColors.surfaceCard) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Cancelar esta consulta?", style = MaterialTheme.typography.titleMedium, color = AgendaMedicaColors.inkPrimary)
                Text(quem, style = MaterialTheme.typography.bodyMedium, color = AgendaMedicaColors.inkSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlineButton(
                        text = if (cancelando) "Cancelando..." else "Sim",
                        onClick = onSim,
                        enabled = !cancelando,
                        borderColor = AgendaMedicaColors.dangerInk,
                        modifier = Modifier.weight(1f).semantics { contentDescription = "Sim, cancelar a consulta com $quem" },
                    )
                    OutlineButton(
                        text = "Não",
                        onClick = onNao,
                        enabled = !cancelando,
                        modifier = Modifier.weight(1f).semantics { contentDescription = "Não cancelar a consulta com $quem" },
                    )
                }
            }
        }
    }
}
```
Chamada na tela (fora do `LazyColumn`, dentro do `else ->` que já lista as consultas):
```kotlin
state.consultas.firstOrNull { it.consulta.id == state.confirmandoId }?.let { item ->
    CancelConfirmDialog(
        quem = "${item.consulta.doctorName}, ${formatarDataHora(item.consulta.start)}",
        cancelando = state.cancelandoId != null,
        onSim = viewModel::onCancelarSim,
        onNao = viewModel::onCancelarNao,
    )
}
```
Não se usa `androidx.compose.material3.AlertDialog` porque seus slots `confirmButton`/`dismissButton` não dão botões de largura igual por padrão (a exigência é "mesmo tamanho"); `Dialog` cru com `Surface` reaproveita o mesmo `Row`+`weight(1f)` já usado hoje inline.

## Verification

**Commands:**
- `./gradlew assembleDebug testDebugUnitTest` -- expected: `BUILD SUCCESSFUL`, todos os testes existentes continuam passando sem alteração

**Manual checks (if no CLI):**
- No celular: tocar "Cancelar" num card cancelável em Minhas Consultas e em Minha Agenda; confirmar que tocar fora do diálogo e apertar voltar não fecham nada; tocar "Não" fecha sem cancelar; tocar "Sim" cancela normalmente (sucesso remove o card; se a janela de 24h fechar entre o toque e a resposta, mostra o aviso já existente)
