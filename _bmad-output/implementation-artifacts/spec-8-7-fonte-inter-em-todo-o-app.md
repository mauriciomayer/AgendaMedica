---
title: 'Fonte Inter em todo o app'
type: 'feature'
created: '2026-09-25'
status: 'done'
route: 'oneshot'
review_loop_iteration: 0
context: ['{project-root}/_bmad-output/implementation-artifacts/epic-8-context.md']
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** O app usa a fonte sans-serif do sistema (Roboto), enquanto o protótipo usa Inter; o `Type.kt` já dizia que a troca dependia de embutir os arquivos. O usuário decidiu incluir a Inter.

**Approach:** Embutir os pesos regular, médio, semibold e negrito da Inter em `res/font` e ligar a família aos estilos do tema, sem mudar nenhuma tela. Arquivos vindos da fonte variável do protótipo (recorte Latin, licença OFL), com a licença e o comando de geração documentados em `docs/licenses/`.

</frozen-after-approval>

## Implementation Notes

`InterFontFamily` em `Type.kt` passou de `FontFamily.SansSerif` para `FontFamily(Font(R.font.inter_regular, Normal), inter_medium, inter_semibold, inter_bold)`; todos os estilos do tema já a usavam. Os quatro TTF estáticos (~66 KB cada, recorte Latin com todo o português) foram instanciados da fonte variável do protótipo com fontTools (comando em `docs/licenses/Inter-OFL.txt`, junto do texto integral da OFL 1.1). Os únicos pesos usados no app são Normal, Medium, SemiBold e Bold (conferido por busca no código), então nenhum cai em peso sintetizado.

Verificação: `./gradlew assembleDebug testDebugUnitTest` -> `BUILD SUCCESSFUL` (todos os testes de quebra de linha do Épico 8, em modo gráfico NATIVE, continuam verdes). `InterFontTest`: a família tem os 4 pesos e não é a sans-serif do sistema; todos os estilos do tema usam a Inter; os 4 arquivos TTF existem, são distintos e válidos; texto com acentos do português renderiza sob o tema. Conferido visualmente numa captura com o tema (Minhas consultas).

## Review Triage Log

Camada blind-hunter (N=3, 4 achados).

| Achado | Veredito | Evidência / rota |
|---|---|---|
| Pesos fora de 400-700 e itálico sem arquivo | `medium`, rejeitado | Busca no código: só Normal, Medium, SemiBold e Bold são usados; não há itálico |
| Teste de fonte fraco (tautologia de `R.font`, render passa com a fonte de fallback) | `medium`, patch | Tautologia removida; teste novo confere que os 4 TTF existem, começam com a assinatura TrueType, têm tamanho plausível e conteúdo distinto |
| Licença só resumida, sem texto integral e sem aviso no APK | `medium`, patch parcial / diferido | Texto integral da OFL 1.1 e comando de geração agora em `docs/licenses/Inter-OFL.txt`; a Inter não tem nome reservado; aviso dentro do app diferido (app ainda não publicado) |
| Recorte Latin sem cadeia de fallback, 4 arquivos em vez de uma variável | `low`, rejeitado | O recorte cobre todo o português; caracteres raros caem no fallback do Android por glifo; fontes variáveis em `res/font` exigem API 26+ e o projeto usa TTFs estáticos por simplicidade |
