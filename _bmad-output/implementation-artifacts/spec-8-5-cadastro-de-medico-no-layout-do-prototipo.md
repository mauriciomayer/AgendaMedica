---
title: 'Cadastro de médico no layout do protótipo'
type: 'feature'
created: '2026-09-25'
status: 'done'
route: 'oneshot'
review_loop_iteration: 0
context: ['{project-root}/_bmad-output/implementation-artifacts/epic-8-context.md']
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** O Cadastro de médico ainda tem voltar solto, título grande, campos de rótulo flutuante e listas com estilo próprio. O usuário pediu telas mais próximas do protótipo (Épico 8).

**Approach:** Reescrever sobre `TelaPadrao` (barra "Cadastro de médico" com subtítulo "Autocadastro, sem validação de CRM" e voltar), caixa azul com o aviso do CRM, campos com rótulo acima e exemplo dentro, dropdowns com rótulo acima (novo `CampoSelecao`), chips de convênio e dias, Início/Fim lado a lado e botão "Criar perfil e começar a atender". Mantidos: 7 dias da semana, campo Localização, dica "Mínimo de 6 caracteres.", máscara de e-mail e olho da senha. ViewModel e regras não mudam.

</frozen-after-approval>

## Implementation Notes

Peças novas em `ui/components/Components.kt`: `RotuloCampo` (rótulo de 12,5 sp semibold) e `CampoSelecao<T>` (dropdown genérico somente leitura com rótulo acima, placeholder "Selecione…"; o campo é anunciado como "Rótulo, valor" e mantém o papel de lista suspensa). `ToggleChip` passou a expor o estado `selected` para leitores de tela (antes só borda e cor). O protótipo lista 5 dias; mantive os 7 (decisão anterior do usuário). `EmailFieldWiringTest` localiza o e-mail do médico por posição (rótulo acima).

Verificação: `./gradlew assembleDebug testDebugUnitTest` -> `BUILD SUCCESSFUL`. `CadastroMedicoLayoutTest` (9 testes): barra com subtítulo e voltar, caixa do CRM sob a barra, rótulos acima dos campos com exemplos, dropdown com papel de lista suspensa e anúncio "Especialidade, nenhum selecionado" atualizando o formulário, Localização, Início/Fim lado a lado, 7 dias com estado selecionado, botão desabilitado e formulário rolando até o botão em tela pequena. Conferido visualmente por captura do Robolectric.

## Review Triage Log

Camada blind-hunter (N=6, 7 achados).

| Achado | Veredito | Evidência / rota |
|---|---|---|
| `contentDescription` no dropdown esconde papel e estado | `high`, rejeitado | O nó mantém `Role.DropdownList` e o clique do `menuAnchor` (teste novo comprova o papel); a descrição só troca o texto lido por "Rótulo, valor", e o campo é somente leitura |
| Rótulo solto do campo / grupos de chips sem agrupamento | `medium`, rejeitado | Padrão do protótipo e da 8.1; cada chip anuncia a si mesmo e agora seu estado |
| Sem altura fixa e fonte grande nos campos lado a lado | `medium`, rejeitado | Altura padrão do Material (56 dp) e sp acompanham a escala; a tela rola |
| Testes sem estado dos chips | `medium`, patch | `ToggleChip` expõe `selected`; teste verifica não selecionado -> selecionado |
| Lacunas de teste do `CampoSelecao` e comparação exata de Dp | `low`, rejeitado | O componente é coberto pelo uso real (2 dropdowns e 2 horários); a comparação de Dp passa de forma determinística |
| Erro em `InfoBox` de aviso e caixa CRM duplicada com o subtítulo | `low`, rejeitado | `InfoBox` é live region Polite com texto de erro; texto e subtítulo são os do protótipo |
| Ordem de imports | `low`, rejeitado | O arquivo já não seguia ordem alfabética; sem regra de lint no projeto |
