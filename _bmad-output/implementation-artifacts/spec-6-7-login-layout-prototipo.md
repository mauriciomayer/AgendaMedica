---
title: 'Tela de Login segue o layout do protótipo de design'
type: 'feature'
created: '2026-09-23'
status: 'done'
route: 'oneshot'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** O usuário gostou do layout da tela de Login do protótipo de design (artefato compartilhado) e pediu que a Login do app fique igual a ele, só a Login por enquanto (as demais telas serão revistas depois). Hoje a Login tem o título "Agenda Médica" alinhado à esquerda, as abas em chips à esquerda, campos Material com rótulo flutuante e "Esqueci minha senha" entre a senha e o botão.

**Approach:** Reescrever `LoginScreen` para o layout do protótipo: cabeçalho branco (título "Entrar" 18 sp negrito + subtítulo 12,5 sp "Acesse sua conta de paciente"/"...de médico" conforme a aba, linha fina embaixo, estendido atrás da barra de status); conteúdo com espaçamento de 14 dp: logo 88 dp centralizado (Story 6.6), seletor Paciente/Médico centralizado numa pílula branca (aba ativa azul com texto branco), campos E-mail e Senha com o rótulo pequeno acima e o exemplo dentro ("voce@email.com", "••••••••"), caixas de mensagem coloridas (aviso de sucesso verde, erro em tom quente), botão "Entrar", "Esqueci minha senha" centralizado e "Não tem conta? Criar conta" centralizado. O título "Agenda Médica" sai do conteúdo (o nome do app já aparece na Splash). `LabeledTextField` (compartilhado) só ganha os parâmetros opcionais `labelAbove` e `placeholder`, com padrão que mantém todas as outras telas idênticas. Todo o comportamento existente da Login fica igual (máscara e teclado de e-mail, olho na senha, "Entrar" desabilitado até o formulário ser válido, bloqueio por papel, navegação). O alvo de toque das opções do seletor fica com no mínimo 48 dp (piso de acessibilidade do projeto), um pouco acima dos ~38 px do protótipo.

</frozen-after-approval>

## Implementation Notes

`LoginScreen` foi reescrita seguindo o protótipo: cabeçalho branco (título "Entrar" como heading, subtítulo por papel, `HorizontalDivider`, estendido atrás da barra de status e respeitando os insets laterais), conteúdo com `spacedBy(14.dp)` (logo 88 dp, seletor pílula centralizado, campos, caixas de mensagem, botão, links centralizados). O seletor é um `Row` com `selectableGroup()` e opções `Role.RadioButton` de no mínimo 48 dp; as caixas de mensagem são `liveRegion = Polite`. `LabeledTextField` (compartilhado) ganhou `labelAbove` e `placeholder` opcionais (padrão = comportamento antigo, provado por teste); o exemplo da senha fica oculto do leitor de tela e o exemplo usa `inkTertiary` (contraste). `CampoLabelInk` foi exposto (`internal`) para o rótulo e o texto inativo do seletor. O título "Agenda Médica" saiu do conteúdo, como no protótipo.

Verificação: `./gradlew assembleDebug testDebugUnitTest` -> `BUILD SUCCESSFUL`. Novo `LoginScreenLayoutTest` (12 testes, substitui o `LoginScreenLogoTest`) cobre cabeçalho e subtítulo por papel, seletor e seleção, alvos de toque (medidos por `touchBoundsInRoot`), ordem vertical dos elementos, rótulos acima dos campos, logo (88 dp, centralizado, sem descrição), centralização do seletor e dos links, olho da senha, botão Entrar desabilitado até o formulário ser válido, aviso de senha redefinida e erro de login em caixa com live region, e tela pequena (320x470) com rolagem. `LabeledTextFieldEmailTest` ganhou 2 testes do componente (rótulo flutuante intacto por padrão e rótulo acima). Conferido visualmente por capturas do Robolectric (412x892) nos estados com dados, vazio e com erro.

Diferença conhecida em relação ao protótipo (por escolha): o alvo de toque das opções do seletor é de 48 dp (piso de acessibilidade do projeto) em vez de ~38 px, e os campos têm a altura mínima de 56 dp do Material em vez de ~44 px.

## Review Triage Log

Camada blind-hunter (N=6, 14 achados).

| Achado | Veredito | Evidência / rota |
|---|---|---|
| `contentDescription` no campo pode esconder o texto digitado do TalkBack; `clearAndSetSemantics` no rótulo | `medium`, patch | Risco plausível e não testável aqui; o rótulo voltou a ser um texto normal ao lado do campo (o campo continua lendo o próprio valor) |
| Insets laterais perdidos (só top/bottom do `Scaffold`) | `medium`, patch | `ladoInicio`/`ladoFim` = 18 dp + inset lateral do `Scaffold` no cabeçalho e no corpo |
| Contraste dos ícones da barra de status sobre o cabeçalho branco | `false` | O fundo do app (`surface_canvas`, quase branco) já exige ícones escuros; o branco do cabeçalho não muda isso |
| Nome do app não aparece mais na Login | `low`, rejeitado | Segue o protótipo (título "Entrar"); o nome aparece na Splash |
| Título sem `heading()`; "Entrar" duplicado com o botão; testes por índice frágeis | `low`, patch | Título com `heading()`; testes acham o botão por `hasText + hasClickAction` |
| Seletor com `Role.Tab` fora de um TabRow | `low`, patch | `selectableGroup()` + `Role.RadioButton` |
| Exemplo com baixo contraste (`inkDisabled`); exemplo da senha lido como "pontos" | `low`, patch | Exemplo em `inkTertiary`; exemplo da senha oculto do leitor de tela |
| Caixas de mensagem sem live region | `low`, patch | `liveRegion = Polite` (erro de login e aviso de senha redefinida são anunciados) |
| Contraste `dangerInk` sobre `warningBg` | `low`, rejeitado | Mesmas cores do protótipo |
| Sem teste do caminho padrão de `LabeledTextField` | `medium`, patch | 2 testes do componente e testes de fiação por tela (Cadastro/Recuperar continuam com rótulo flutuante) |
| Testes fracos (uma só linha de links, sem 48 dp, sem olho da senha, sem erro, sem tela pequena) | `low`, patch | Todos adicionados; o de 48 dp mede `touchBoundsInRoot` (o `TextButton` desenha 40 dp e estende o toque) |
| Centralização vertical perdida; `Row` de "Não tem conta?" sem quebra | `low`, rejeitado | O protótipo é alinhado ao topo; o layout já rola |
| Cores/tamanhos fixos fora dos tokens; import qualificado | `low`, patch parcial | `TextUnit` importado; os tamanhos em sp seguem o protótipo (sem token equivalente) |
| Ordem de imports; `heightIn(48)` redundante com o mínimo de 56 dp | `low`, patch parcial | `heightIn` removido; ordem dos imports mantida (o projeto não a impõe) |
| Sem `imeAction`/autofill/`imePadding`; textos fora de recursos | `low`, rejeitado | Já era assim antes desta história e vale para todas as telas |

