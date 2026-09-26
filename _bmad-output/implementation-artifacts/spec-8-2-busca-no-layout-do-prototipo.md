---
title: 'Busca de médicos no layout do protótipo'
type: 'feature'
created: '2026-09-25'
status: 'done'
route: 'oneshot'
review_loop_iteration: 0
context: ['{project-root}/_bmad-output/implementation-artifacts/epic-8-context.md']
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** A tela de Busca ainda usa o botão de voltar/título soltos, filtros espalhados e cartões de médico sem avatar. O usuário pediu que as telas sigam o protótipo (Épico 8) e escolheu avatar azul cheio com iniciais brancas.

**Approach:** Reescrever a Busca sobre `TelaPadrao` (barra "Agende" com subtítulo "Encontre um médico e agende" e o botão em pílula "Minhas consultas" à direita), com os filtros (especialidade, cidade e botão de localização) num único `AppCard`, a linha de contagem "N médico(s) encontrado(s)" e cartões de médico inteiros clicáveis com `Avatar`, "Especialidade · Cidade" numa linha e os chips de convênio. Fluxo de GPS, ViewModel e navegação não mudam; a tela vira `BuscaScreen` (permissões e ViewModel) + `BuscaConteudo` (sem estado, testável).

</frozen-after-approval>

## Implementation Notes

`BuscaScreen.kt` reescrita: `BuscaConteudo` é `internal` e sem estado. Filtros e resultados ficam **numa só `LazyColumn`** (o cartão de filtros é o primeiro item), então com fonte grande ou teclado aberto a tela inteira rola em vez de espremer a lista. Peças novas em `ui/components/Layout.kt`: `Avatar` (círculo azul com iniciais brancas, letras em dp convertidas por `toSp`, escondido dos leitores de tela) e `BotaoPilula`; `initialsOf` saiu da Busca para `Layout.kt`. `TagChip` passou a 11 sp semibold com altura de linha própria (efeito também no Detalhe e na Minha agenda, como no protótipo). O campo de cidade tem ação "Concluído" no teclado. Não copiei o texto "intervalos de 15 min" do protótipo (as consultas duram 30 min) e mantive o que o protótipo não previa: fluxo de GPS, aviso de localização e "Obtendo sua localização...".

Verificação: `./gradlew assembleDebug testDebugUnitTest` -> `BUILD SUCCESSFUL` (251 testes). `BuscaLayoutTest` (13 testes): barra com título como heading, subtítulo e pílula com toque de 48 dp, filtros no mesmo cartão, escolher especialidade e limpar por "Todas as especialidades", contagem e cartões com convênios, cartão inteiro clicável, avatar escondido do TalkBack, estado vazio, carregando (indicador de progresso presente), erro com "Tentar novamente", avisos de localização dentro do cartão, lista longa que realmente rola e tela pequena em que o cartão de filtros rola para fora. Conferido visualmente por captura do Robolectric.

## Review Triage Log

Camada blind-hunter (N=6, 15 achados).

| Achado | Veredito | Evidência / rota |
|---|---|---|
| Cartão de filtros fixo acima da lista espreme os resultados com fonte grande/teclado | `medium`, patch | Tudo numa `LazyColumn`; teste em tela 320x470 dp rolando até o segundo médico |
| Campo de cidade sem `imeAction`; `heightIn(48)` sem efeito (o campo já tem 56 dp) | `low`, patch | `ImeAction.Done` + limpa o foco; `heightIn` removido |
| `TagChip` mantém altura de linha do `labelLarge` | `low`, patch | `lineHeight = 14.sp` |
| `TagChip` redimensionado afeta outras telas | `low`, rejeitado | Intencional: o protótipo usa o mesmo chip em Busca, Detalhe e Minha agenda |
| `Avatar` com letras em sp fixas e `Color.White` com caminho completo | `low`, patch | Tamanho via `toSp` a partir do dp; import de `Color` |
| Testes fracos: limpar especialidade, lista "rola" sem rolar, carregando vazio, erro sem contagem | `medium`, patch | 2 testes novos, rolagem real, indicador de progresso e ausência da contagem no erro |
| Teste de `initialsOf` deveria morar junto dos componentes | `low`, rejeitado | O teste já importa de `ui.components`; mover só o arquivo não muda cobertura |
| Nome `colunasDoCampo` e `Spacer(size)` | `low`, patch | Renomeado para `coresDoCampo`; `Spacer(Modifier.height())` |
| `BuscaResultados` com `fillMaxSize` | `low`, patch | Virou `LazyListScope.resultados` dentro da mesma lista |
| "médico(s)" (plural) sem live region; estado vazio redundante com a contagem | `low`, rejeitado | Textos literais do protótipo; a contagem já aparece na lista e o vazio explica o que fazer |
| Título "Agende" é um verbo | `low`, rejeitado | Título do protótipo, aprovado no plano do épico |
| Campo somente leitura de especialidade e cidade só com placeholder (nome acessível) | `low`, rejeitado | O texto exibido do dropdown é o nome lido; o exemplo da cidade é o do protótipo e o botão de GPS tem descrição própria |
| Botão de GPS não reflete o estado "localizando" e o wrapper `BuscaScreen` não é testado | `low`, rejeitado | O ViewModel já ignora toque enquanto localiza; o aviso "Obtendo sua localização..." aparece; permissões são API do sistema (fluxo inalterado, checado no manual) |
| Cartão sem dica de ação explícita | `low`, rejeitado | `Card(onClick)` já expõe o papel de botão e o clique inteiro |
