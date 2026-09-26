---
title: 'Minha agenda do médico no layout do protótipo'
type: 'feature'
created: '2026-09-25'
status: 'done'
route: 'oneshot'
review_loop_iteration: 0
context: ['{project-root}/_bmad-output/implementation-artifacts/epic-8-context.md']
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** A Minha agenda ainda tem título grande sem barra, cartão de perfil com avatar sobre várias linhas e um rótulo "Próximas consultas" discreto. O usuário pediu telas mais próximas do protótipo (Épico 8) e decidiu manter Cancelar/Reagendar com a regra das 24 h nos cartões do médico.

**Approach:** Reescrever sobre `TelaPadrao` (barra "Minha agenda" com "Sair" à direita, sem voltar), cartão de perfil do protótipo (nome em negrito, especialidade, chips de convênio) mantendo as linhas de horário de atendimento, caixa azul informando que não há dois pacientes no mesmo horário, título de seção "Próximas consultas" e os cartões de consulta compartilhados (já no novo visual desde a 8.3). ViewModel, regra das 24 h e modal não mudam. Remover `AccessibleIconButton`, que ficou sem uso.

</frozen-after-approval>

## Implementation Notes

`MinhaAgendaScreen` = ViewModel + `MinhaAgendaConteudo` (interno, sem estado). O avatar do cartão de perfil saiu (o protótipo não tem); as linhas "Segunda: 08:00 - 12:00" ficam. `InfoBox` ganhou `anunciar` (padrão `true`): a caixa azul é informativa e permanente, então não é live region. `initialsOf` privado duplicado saiu (o compartilhado está em `Layout.kt`) e `AccessibleIconButton` foi removido de `Components.kt` (nenhuma tela o usa mais). O shell só usa lista própria (`scrollable = false`) no ramo do perfil; carregando e erro rolam com o shell.

Verificação: `./gradlew assembleDebug testDebugUnitTest` -> `BUILD SUCCESSFUL`. `MinhaAgendaLayoutTest` (12 testes): barra com Sair à direita e sem voltar, cartão de perfil com horários, caixa azul sem live region abaixo do cartão, título de seção como heading, cartões com Cancelar/Reagendar e bloqueio, modal com Sim/Não, vazio, falha de ação em live region, erro de consultas com perfil visível e nova tentativa, carregando, erro do perfil e tela pequena rolando até a última consulta. Conferido visualmente por captura do Robolectric.

## Review Triage Log

Camada blind-hunter (N=6, 7 achados).

| Achado | Veredito | Evidência / rota |
|---|---|---|
| Modal derivado da lista some se ela esvaziar com `confirmandoId` setado | `medium`, rejeitado (teste patch) | Comportamento pré-existente e documentado no KDoc do diálogo; teste novo cobre o modal com Sim/Não |
| Carregando/erro do perfil em shell não rolável | `medium`, patch | `scrollable` só é falso no ramo do perfil |
| Asserções de "sem voltar"/posição fracas | `low`, rejeitado | O nó "Voltar" só existe com `onBack`; posições comparadas com o próprio título |
| Caixa `anunciar = false` sem semântica própria | `low`, rejeitado | Texto estático lido na travessia; o teste garante ausência da live region |
| Falha de ação fora da vista com a lista rolada e indicadores sem descrição | `medium`, diferido | Pré-existente e igual em Minhas consultas; registrado em `deferred-work.md` |
| Teste de erro das consultas sem verificar o texto | `low`, patch | Asserção do texto adicionada |
| Imports sobrando após remover o botão | `low`, patch parcial | `ImageVector` removido; `Icon`/`IconButton`/`sizeIn` ainda usados por outros componentes; compilação e testes verdes |
