---
title: 'Login exibe o logo do app'
type: 'feature'
created: '2026-09-23'
status: 'done'
route: 'oneshot'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** O protótipo de design (artefato compartilhado pelo usuário) mostra na tela de Login o logo do app (documento com selo de cruz vermelha) centralizado no topo do conteúdo, com 88 dp. O app nunca o exibiu ali: `LoginScreen` não tem o logo; só a Splash usa `AgendaMedicaLogo` (Story 4.1, que deixou o reuso no Login "fora de escopo", conforme o KDoc de `Logo.kt`).

**Approach:** Em `LoginScreen`, exibir `AgendaMedicaLogo(size = 88.dp)` centralizado no topo do conteúdo (acima do título "Agenda Médica" e do restante do formulário), com o espaçamento do protótipo (8 dp acima, 4 dp abaixo). `AgendaMedicaLogo` ganha um parâmetro `modifier: Modifier = Modifier` (o padrão mantém a Splash idêntica) para o teste localizar o logo por tag. O logo é decorativo: sem ação de toque e sem descrição para leitor de tela (o nome do app já aparece em texto logo abaixo). Nada mais na tela muda; o restante das diferenças entre o protótipo e o app (cabeçalho "Entrar", alinhamento das abas) fica fora desta história.

</frozen-after-approval>

## Implementation Notes

`LoginScreen` exibe `AgendaMedicaLogo(size = 88.dp)` num `Box` centralizado (8 dp acima, 4 dp abaixo, como no protótipo) e um `Spacer(12.dp)` antes do título "Agenda Médica"; `AgendaMedicaLogo` ganhou `modifier: Modifier = Modifier` (o padrão deixa a Splash idêntica: `modifier.size(size)` com `Modifier` padrão equivale a `Modifier.size(size)`) e o KDoc deixou de dizer que o reuso no Login estava fora de escopo. Novo `LoginScreenLogoTest` (4 testes, Robolectric): 88 dp e sem descrição para leitor de tela; logo acima do título e das abas; centralizado; resto do formulário ainda presente. Verificação visual: captura da Login renderizada pelo Robolectric (412x892 dp) confirmou o logo centralizado no topo, igual ao protótipo. `./gradlew assembleDebug testDebugUnitTest` -> `BUILD SUCCESSFUL`.

Fora do escopo, por decisão do Intent: o protótipo também mostra na Login um cabeçalho "Entrar / Acesse sua conta de paciente" e as abas centralizadas; o app segue com o título "Agenda Médica" alinhado à esquerda e as abas à esquerda.

## Review Triage Log

Camada blind-hunter (N=3, 9 achados).

| Achado | Veredito | Evidência / rota |
|---|---|---|
| Em paisagem/tela baixa o logo (112 dp no total) empurra o formulário para baixo da dobra | `low`, rejeitado | A tela já rola (`verticalScroll`); o protótipo tem o mesmo logo; sem clipping. No teste, o tamanho padrão do Robolectric (320x470) mostrou o efeito e o teste foi ajustado para `assertExists` |
| Logo centralizado sobre título alinhado à esquerda parece desalinhado | `low`, rejeitado + pergunta | Fora do pedido (só o ícone); virou pergunta ao usuário sobre centralizar o título |
| `Box` + padding + `Spacer` são 3 espaçamentos para um elemento | `low`, rejeitado | Reproduz exatamente 8/4 dp do protótipo; cosmético |
| Título sem `heading()` para TalkBack | `low`, rejeitado | Pré-existente e comum a todas as telas; não causado por esta mudança |
| "Decorativo" não verificado por teste | `low`, patch | Teste agora afirma que o logo não tem `ContentDescription` |
| Tag de teste em código de produção | `low`, rejeitado | Padrão comum, sem efeito para o usuário |
| Regressão da Splash pelo novo `modifier` sem teste | `low`, rejeitado | `modifier.size(size)` com `Modifier` padrão equivale ao antigo `Modifier.size(size)`; o `SplashScreenTest` da 7.4 continua passando |
| Testes fracos: só comparava com o título; asserção repetida; não checava o formulário | `low`, patch | Agora compara também com as abas, removida a repetição e adicionado teste do formulário |
| Texto "Agenda Médica" fixo no teste; mocks relaxados; `createComposeRule` v1 x v2 | `low`, rejeitado | O v1 na Splash é exigido pelo `StateRestorationTester`; o título da Login já era literal antes desta história |

