---
title: 'Minhas consultas e cartão de consulta no layout do protótipo'
type: 'feature'
created: '2026-09-25'
status: 'done'
route: 'oneshot'
review_loop_iteration: 0
context: ['{project-root}/_bmad-output/implementation-artifacts/epic-8-context.md']
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Minhas consultas ainda usa botão de voltar solto, título grande sem barra e cartões com quatro linhas, selo grande e botões altos. O usuário pediu telas mais próximas do protótipo e decidiu manter a ordem "Cancelar, Reagendar".

**Approach:** Reescrever a tela sobre `TelaPadrao` (barra "Minhas consultas" com voltar e "Sair" à direita) e o `ConsultaCard` compartilhado no formato do protótipo: nome 15 sp semibold + especialidade, selo Confirmada/Bloqueada compacto, uma linha "dd/MM às HH:mm · Convênio", aviso de bloqueio numa caixa tingida e botões Cancelar e Reagendar compactos. "+ Nova consulta" fica sempre no fim da lista (também sem consultas). ViewModel, regra das 24 h e modal de confirmação não mudam.

</frozen-after-approval>

## Implementation Notes

`MinhasConsultasScreen` = ViewModel + `MinhasConsultasConteudo` (interno, sem estado, testável). Peças novas: `ShapeSm` (9 dp), `BotaoBarra` (ação de texto da barra, usada por "Sair" e, na 8.6, pela Minha agenda) e `formatarDiaHora` ("29/09 às 10:00"). O `ConsultaCard` também é usado pela Minha agenda do médico e por isso já muda lá; a 8.6 ajusta o resto daquela tela. A mensagem de falha ao cancelar passou a uma `InfoBox` (live region). Descrições de acessibilidade dos botões (com médico e data completa) e o modal continuam iguais.

Verificação: `./gradlew assembleDebug testDebugUnitTest` -> `BUILD SUCCESSFUL`. `MinhasConsultasLayoutTest` (12 testes): barra com título como heading, voltar e Sair à direita com toque de 48 dp; cartão com linha única data · convênio; Cancelar antes de Reagendar com nomes acessíveis; toque de 48 dp nos dois botões; bloqueio com caixa e botões desabilitados só no cartão bloqueado; "+ Nova consulta" no fim e alcançável com lista longa; vazio; falha de ação acima dos cartões; erro sem cartões; carregando; modal ligado a Sim/Não. Conferido visualmente por captura do Robolectric.

## Review Triage Log

Camada blind-hunter (N=6, 7 achados).

| Achado | Veredito | Evidência / rota |
|---|---|---|
| Tamanhos de fonte fixos em sp, selo 11 sp pode quebrar | `low`, rejeitado | sp já acompanha a escala da fonte; tamanhos e paddings são os do protótipo |
| Toque de 48 dp dos botões do cartão sem teste | `medium`, patch | Teste novo mede `touchBoundsInRoot` de Cancelar e Reagendar |
| Borda/rótulo do botão desabilitado com cinzas diferentes | `low`, rejeitado | Igual ao protótipo (cinza + opacidade); o selo "Bloqueada" e a caixa explicam o estado em texto |
| "+ Nova consulta" como último item da lista | `low`, rejeitado | Decisão do plano do épico (sempre no fim da lista, como no protótipo); teste garante alcance |
| Falha de ação sem semântica de anúncio e sem cor de erro | `low`, rejeitado | `InfoBox` já é live region Polite e usa o tom de aviso (fundo alaranjado, texto de erro) |
| Asserções fracas (Sair à direita, heading, dialog, fuso) | `low`, rejeitado | Row garante ordem, e o teste de posição compara com o título; fuso fixo `FUSO_AGENDA` sem horário de verão atual |
| Botões compactos parecidos e ano fora do cartão | `low`, rejeitado | Especificações diferentes (pílula, ação de texto, ação do cartão); o ano está na descrição acessível e só há consultas futuras |
