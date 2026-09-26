---
title: 'Detalhe do médico e Confirmação no layout do protótipo'
type: 'feature'
created: '2026-09-25'
status: 'done'
route: 'oneshot'
review_loop_iteration: 0
context: ['{project-root}/_bmad-output/implementation-artifacts/epic-8-context.md']
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Detalhe do médico e Confirmação ainda têm botão de voltar solto, cabeçalho de texto puro, chips de dia genéricos e o botão de confirmar no fim do conteúdo. O usuário pediu telas mais próximas do protótipo (Épico 8) e decidiu manter o seletor de convênio.

**Approach:** Detalhe sobre `TelaPadrao` (barra "Escolher horário" ou "Reagendar consulta" com voltar): cartão do médico com avatar de 48 dp, seção "Escolha o dia" com cartões de 52 dp (dia da semana sobre o dia do mês), seção "Horários disponíveis" na grade de 3 colunas, nota "Agendamento exige mínimo de 48h de antecedência." e o botão de confirmar numa barra fixa embaixo (novo slot `rodape` do `TelaPadrao`). Confirmação: barra "Confirmado", círculo verde com check, "Consulta agendada!" centralizado, um cartão-resumo e os botões "Ver minhas consultas" e "Buscar outro médico". Regras, ViewModels e navegação não mudam.

</frozen-after-approval>

## Implementation Notes

`DetalheMedicoScreen` = ViewModel + `DetalheConteudo` (interno, sem estado, testável); `TelaPadrao` ganhou `rodape` (barra branca fixa com linha fina, assume o inset inferior). Mantidos do que o protótipo não previa: seletor "Convênio" (ao agendar; ao reagendar aparecem os chips informativos do médico), motivo do horário desabilitado ("Ocupado", "Antecedência mín. 48h") e mensagens de erro/aviso. A mensagem de falha ao agendar fica **na barra fixa, acima do botão** (no corpo rolável ela ficaria fora da vista). O texto "(intervalos de 15 min)" do protótipo não foi copiado (a grade é de 45 min, consultas de 30). Os cartões de dia usam `selectable(role = RadioButton)`. Confirmação mostra a data curta "29/09 às 10:00".

Verificação: `./gradlew assembleDebug testDebugUnitTest` -> `BUILD SUCCESSFUL`. `DetalheLayoutTest` (17 testes) e `ConfirmacaoScreenTest` (4): barra e títulos como heading, voltar, reagendar sem seletor de convênio, avatar escondido do TalkBack, cartões de dia com seleção e toque de 48 dp, grade de 3 colunas com horários desabilitados, nota de antecedência, seletor de convênio, barra de confirmar (desabilitada/habilitada, "Agendando..."), mensagem de falha acima do botão, tela pequena com barra fixa e conteúdo rolando, sem dias, sem horários, carregando e erro. Conferido visualmente por capturas do Robolectric.

## Review Triage Log

Camada blind-hunter (N=7, 8 achados).

| Achado | Veredito | Evidência / rota |
|---|---|---|
| Barra fixa pode sumir se o corpo ocupar tudo | `medium`, rejeitado | O corpo já tem `weight(1f)`; teste novo em tela 320x400 dp prova a barra fixa com o conteúdo rolando |
| Teste de rolagem não prova a barra fixa | `medium`, patch | Teste em tela pequena: barra abaixo do conteúdo e visível depois de rolar |
| Falha ao agendar fica fora da vista, sem anúncio | `medium`, patch | Mensagem movida para a barra fixa (`InfoBox` é live region); teste de posição acima do botão |
| Teste do botão voltar da Confirmação procurava texto | `medium`, patch | Procura pela descrição "Voltar"; voltar do sistema já sai do Detalhe da pilha (`popUpTo inclusive`) |
| Dia como botão sem estado de seleção nativo | `medium`, patch | `selectable(selected, role = RadioButton)` |
| Testes dependentes de fuso e "48h" fixo | `low`, patch parcial | "48h" vem da constante; fuso rejeitado (produção usa `FUSO_AGENDA` fixo, não o do aparelho); teste renomeado |
| Erro não rolável e IME só no corpo | `low`, patch parcial | `scrollable = !isLoading` (erro rola); KDoc do `rodape` avisa que é para telas sem campo de texto |
| Tamanhos em sp fixos e "Confirmado" + "Consulta agendada!" ambos heading | `low`, rejeitado | Tamanhos e títulos do protótipo; sp acompanha a escala da fonte |
