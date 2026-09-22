---
title: 'App exibe splash screen e ícone próprios com o novo logo'
type: 'feature'
created: '2026-09-22'
status: 'review'
route: 'dispatch'
review_loop_iteration: 0
context: []
baseline_commit: '615f50a5e2cff5de6ccf9890d5910b06922073b2'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** O app usa o ícone padrão gerado pelo template do Android Studio (fundo azul + calendário genérico) e abre direto na tela de Login, sem nenhuma splash screen — nada comunica a identidade visual própria definida no protótipo aprovado pelo usuário.

**Approach:** Extrair o desenho do logo (SVG) do protótipo `https://claude.ai/artifact/4522fNaJd6sBqsL9cBwV1A` e usá-lo em dois lugares: uma tela de splash própria (Compose, primeira rota do NavHost, ~1,6s antes de seguir para o destino normal) e o ícone adaptativo do app (substituindo os drawables placeholder atuais).

## Boundaries & Constraints

**Always:**
- Logo = exatamente o desenho do protótipo: um "documento" branco (retângulo arredondado, `rx` proporcional, borda cinza clara, 3 linhas horizontais internas simulando texto) com um selo de cruz médica vermelha (quadrado arredondado vermelho `#D6362E` com uma cruz branca) sobreposto no canto inferior direito. Proporções/posições relativas idênticas às do SVG do protótipo (viewBox 72x72; documento em x=14..48/y=10..56; selo em x=34..68/y=26..60; cruz em dois retângulos brancos 7x18 e 18x7 centrados no selo).
- Splash: fundo `SurfaceCanvas` (`Color.kt`, já é o mesmo tom usado no protótipo), logo centralizado, texto "Agenda Médica" abaixo (negrito, cor `InkPrimary`, tamanho ~22sp), duração fixa de 1600ms, depois navega automaticamente e sai da pilha de navegação (voltar não retorna à splash). Sem interação do usuário durante a splash (sem toque, sem pular).
- Ícone do app: `adaptive-icon` (API 26+, único suporte necessário — `minSdk 26`) com `background` = `SurfaceCanvas` e `foreground` = o mesmo desenho do logo (documento + selo), como vetor, escalado/centralizado na zona segura do ícone adaptativo (círculo de ~66dp dentro do canvas de 108dp). Cores do vetor batem exatamente com as do SVG do protótipo (branco, cinza claro do traço, vermelho `#D6362E`).
- A splash é a primeira rota do NavHost (`startDestination`); a lógica de roteamento existente (Login sempre a rota seguinte, sem restauração de sessão) não muda — a splash só antecede o que já acontece hoje.

**Never:**
- Não adicionar dependência nova (ex.: `androidx.core:core-splashscreen`) nem usar a Splash Screen API do sistema (Android 12+) — a tela é 100% Compose, para ter o mesmo resultado visual em todas as versões suportadas (`minSdk 26`).
- Não alterar nenhuma tela além da splash/ícone; não mexer em `AndroidManifest.xml` além do necessário para o ícone (se algo precisar mudar) e não tocar em Supabase/migrações/Edge Functions — história 100% cliente Android.
- Não criar mipmaps legados (`ic_launcher.png` por densidade) — só o `adaptive-icon` (vetor), como já é o padrão do projeto.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Abertura normal do app (cold start) | usuário toca no ícone do app | Splash aparece (logo + "Agenda Médica") por 1600ms, depois vai para Login (comportamento atual) | N/A |
| Rotação/mudança de configuração durante a splash | girar o aparelho enquanto a splash está visível | Splash continua e ainda navega ao fim dos 1600ms (sem reiniciar a contagem do zero a cada recomposição) | N/A |
| Botão voltar após a splash | usuário chega ao Login e aperta voltar | Sai do app (a splash não está mais na pilha) | N/A |
| Ícone do launcher | app instalado, tela de apps do Android | Ícone mostra o logo (documento + selo vermelho) sobre o fundo claro, não mais o calendário azul antigo | N/A |
| Forma do ícone (launcher com máscara redonda/quadrada/squircle) | launchers variam a máscara do ícone adaptativo | O logo permanece legível e centralizado em qualquer máscara (dentro da zona segura) | N/A |

</frozen-after-approval>

## Code Map

- `app/src/main/java/com/agendamedica/app/ui/navigation/AgendaMedicaNavHost.kt` -- `startDestination` hoje é `Routes.LOGIN_PATTERN`; adicionar rota `splash` como novo `startDestination`, que navega para `Routes.LOGIN_PATTERN` com `popUpTo(...) { inclusive = true }` (mesmo padrão já usado em outras transições do arquivo)
- `app/src/main/java/com/agendamedica/app/ui/theme/Color.kt`, `Theme.kt` -- `SurfaceCanvas`, `InkPrimary`, `AgendaMedicaColors`, `AgendaMedicaTheme`; reutilizar, não criar cores novas
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`, `ic_launcher_round.xml` -- já referenciam `@drawable/ic_launcher_background`/`ic_launcher_foreground`; NÃO precisam mudar
- `app/src/main/res/drawable/ic_launcher_background.xml` -- hoje `#3B6FE0` sólido; trocar para a cor de `SurfaceCanvas` (`#F3F8F4`, ver `Color.kt`)
- `app/src/main/res/drawable/ic_launcher_foreground.xml` -- hoje um calendário genérico branco; substituir pelo desenho do logo (documento + selo), convertido para `<vector>`/`<path>` num canvas 108x108, escalado a partir do viewBox 72x72 do protótipo e centralizado na zona segura
- `app/src/main/AndroidManifest.xml` -- confirmar que `android:icon`/`android:roundIcon` já apontam para `@mipmap/ic_launcher`/`ic_launcher_round` (não deve precisar mudar)
- `app/src/main/java/com/agendamedica/app/ui/auth/EscolhaScreen.kt` ou `LoginScreen.kt` -- padrão de tela Compose simples sem ViewModel próprio para seguir (a splash não precisa de ViewModel: só timer + navegação)
- Fonte de design: protótipo `https://claude.ai/artifact/4522fNaJd6sBqsL9cBwV1A` (mockup "Android", tela de splash e cabeçalho do Login) — o SVG exato do logo está descrito nos "Always" acima

## Tasks & Acceptance

**Execution:**
- [x] `app/src/main/java/com/agendamedica/app/ui/theme/Logo.kt` (novo) -- Composable `AgendaMedicaLogo(size: Dp)` desenhando o logo (documento + selo de cruz) via `Canvas`/`Path` do Compose, fiel ao SVG do protótipo, parametrizado só por tamanho -- reutilizado pela splash (e disponível para uso futuro, ex. cabeçalho do Login, fora do escopo desta história)
- [x] `app/src/main/java/com/agendamedica/app/ui/splash/SplashScreen.kt` (novo) -- Composable com fundo `SurfaceCanvas`, `AgendaMedicaLogo` centralizado (tamanho equivalente aos 216dp do protótipo) e texto "Agenda Médica" abaixo; `LaunchedEffect(Unit) { delay(1600); onFinished() }`
- [x] `app/src/main/java/com/agendamedica/app/ui/navigation/AgendaMedicaNavHost.kt` -- nova rota `splash` como `startDestination`; ao terminar, navega para `Routes.LOGIN_PATTERN` com `popUpTo(navController.graph.id) { inclusive = true }` (splash nunca fica na pilha)
- [x] `app/src/main/res/drawable/ic_launcher_background.xml` -- cor trocada para `#F3F8F4` (`SurfaceCanvas`)
- [x] `app/src/main/res/drawable/ic_launcher_foreground.xml` -- substituído pelo vetor do logo (documento + selo), escalado/centralizado no canvas 108x108 dentro da zona segura (~66dp)
- [x] `app/src/test/java/com/agendamedica/app/ui/splash/SplashScreenTest.kt` (se aplicável) -- teste do que for testável sem infraestrutura de UI test (ex.: nenhuma lógica pura além do delay fixo — se não houver nada de valor a testar por unidade, documentar em Implementation Notes por que e confiar na checagem manual)

**Acceptance Criteria:**
- Given o app é aberto do zero, when a splash aparece, then mostra logo + "Agenda Médica" sobre `SurfaceCanvas` e navega ao Login sozinha após ~1600ms, sem ficar na pilha de navegação
- Given o app está instalado, when o usuário olha o ícone no launcher, then vê o logo (documento + selo vermelho) em vez do calendário azul antigo, com boa aparência em máscaras redonda/quadrada/squircle

## Implementation Notes

- `ui/theme/Logo.kt` (novo): `AgendaMedicaLogo(size: Dp)` desenha o documento (rounded rect branco + borda `SurfaceDisabled` + 3 linhas) e o selo (rounded rect vermelho `#D6362E`, literal — não é `DangerInk`, que é uma cor diferente — + cruz branca) via `Canvas`/`drawRoundRect`/`drawLine`/`drawRect`, com as mesmas coordenadas do SVG do protótipo (viewBox 72x72) escaladas por `size.toPx()/72f`. `SurfaceDisabled` (`Color.kt`) é reaproveitada para o traço, como indicado nas Design Notes.
- `ui/splash/SplashScreen.kt` (novo): `Scaffold` com `containerColor = AgendaMedicaColors.surfaceCanvas`, `Column` centralizada com `AgendaMedicaLogo(size = 216.dp)` + `Text(stringResource(R.string.app_name), ...)` (reaproveita `strings.xml`, que já é "Agenda Médica" — sem string nova). `LaunchedEffect(Unit) { delay(1600); onFinished() }`; sem `Modifier.clickable`/gestos, então não há como o usuário pular a tela. Nenhum ViewModel, como o Code Map sugeria.
- `ui/navigation/AgendaMedicaNavHost.kt`: nova rota privada `Routes.SPLASH = "splash"`, `startDestination` trocado de `Routes.LOGIN_PATTERN` para `Routes.SPLASH`. `composable(Routes.SPLASH) { SplashScreen(onFinished = { navController.goToLogin(passwordChanged = false) }) }`. Usei o helper privado já existente `goToLogin(passwordChanged)` (definido no fim do arquivo, já usado por `RecuperarSenhaScreen`/`NovaSenhaScreen` para voltar ao Login limpando a pilha) em vez de `navController.navigate(Routes.LOGIN_PATTERN)` literal: `LOGIN_PATTERN` é a *string de padrão* de rota (`"login?senhaRedefinida={senhaRedefinida}"`, usada só para declarar `composable(...)`/`startDestination`), não algo que o resto do arquivo jamais passa para `navigate()` com um argumento literal `{...}` — `goToLogin` é o "mesmo padrão já usado em outras transições do arquivo" que o Code Map pede, e produz o resultado exigido: navega para Login e faz `popUpTo(graph.id) { inclusive = true }`, então a splash nunca fica na pilha (voltar no Login sai do app).
- `res/drawable/ic_launcher_background.xml`: cor trocada de `#3B6FE0` para `#F3F8F4` (`SurfaceCanvas`).
- `res/drawable/ic_launcher_foreground.xml`: vetor do calendário genérico substituído pelo logo (documento + selo), reconstruído como `pathData` (comandos `M/H/V/A/Z`) a partir das mesmas coordenadas do SVG do protótipo, dentro de um `<group>` com `scale = 66/72 ≈ 0.9167` e `translate = 21` (em ambos os eixos) para centralizar o desenho de 72x72 na zona segura de ~66dp dentro do canvas de 108x108, como pedido. `mipmap-anydpi-v26/ic_launcher*.xml` e `AndroidManifest.xml` não precisaram mudar (já apontavam para `@mipmap/ic_launcher`/`ic_launcher_round`, confirmado por leitura) — nenhum mipmap legado foi criado.
- `SplashScreenTest.kt` **não foi criado**: o projeto não tem infraestrutura de teste de Compose UI (nem `androidx.compose.ui.test`/`createComposeRule` no `androidTest`, nem Robolectric no `test`; ver `app/build.gradle.kts`, que só tem JUnit + `kotlinx-coroutines-test` + MockK para testes de ViewModel/lógica pura). A spec proíbe adicionar dependências novas (o "Never" fala especificamente de `core-splashscreen`, mas por segurança tratei "não adicionar dependência nova" como regra geral e não trouxe uma lib de teste de UI só para isto). `SplashScreen` não tem lógica pura própria além do delay fixo de 1600ms encadeado com uma callback — não há branch, cálculo ou estado para isolar num teste JVM puro sem renderizar Compose. Fica confiado à checagem manual (ver Verification).
- Build: `./gradlew assembleDebug testDebugUnitTest` com `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` (JBR do Android Studio instalado nesta máquina) → `BUILD SUCCESSFUL`, todos os testes de unidade existentes passaram (nenhum teste novo adicionado, ver ponto acima). Corrigi dois erros encontrados durante essa verificação: comentários XML com `--` duplo em `ic_launcher_background.xml`/`ic_launcher_foreground.xml` (inválido em XML, `parseDebugLocalResources` falhava) e o import de `FontWeight` em `SplashScreen.kt` (é `androidx.compose.ui.text.font.FontWeight`, não `androidx.compose.ui.font.FontWeight`).

## Spec Change Log

## Review Triage Log

Três revisores paralelos (fidelidade geométrica, escopo/regressão, convenções/acessibilidade). Dois blockers reais confirmados independentemente e corrigidos.

| # | Achado | Severidade | Ação |
|---|--------|-----------|------|
| 1 | Ícone do launcher: o `<group>` original escalava/centralizava a caixa 0..72 inteira, mas o desenho (documento+selo) não é centralizado dentro dessa caixa (bbox real x=14..68/y=10..60, centro (41,35)) — o resultado ficava ~4,6dp fora do centro e o canto do selo ~0,66dp fora do círculo de segurança de 66dp | major | Corrigido: `scale`/`translate` recalculados a partir do bbox real do desenho (`scale = 33/half-diagonal ≈ 0,8968`, `translate` = centro do canvas menos o centro do bbox escalado), verificado que os 4 cantos do bbox caem exatamente sobre o círculo de 33dp de raio |
| 2 | Splash: `LaunchedEffect(Unit) { delay(1600) }` reinicia do zero se a Activity for recriada (rotação do aparelho — `AndroidManifest.xml` não declara `configChanges`, confirmado por leitura), contrariando a linha da matriz "sem reiniciar a contagem do zero" | major | Corrigido: `rememberSaveable` guarda o instante de início através da recriação; o efeito calcula o tempo restante (`1600 - decorrido`) em vez de um delay fixo |
| 3 | `ic_launcher_background.xml`/`ic_launcher_foreground.xml` usavam `#F3F8F4`/`#FFFFFFFF` literais em vez de reaproveitar `@color/surface_canvas_approx`/`@color/white`, já existentes em `colors.xml` com esse propósito exato | minor/nit | Corrigido: trocado para as referências de cor existentes |
| 4 | `SplashScreen`'s texto usava `fontSize`/`fontWeight` crus em vez de `MaterialTheme.typography.titleLarge`, o único `Text` de título do app fora do padrão de tipografia (perderia a troca futura de fonte via `InterFontFamily`) | minor | Corrigido: `MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp)`, mantendo o tamanho pedido pela spec mas preservando a indireção de fonte |
| 5 | Sem entrada em `deferred-work.md` para a ausência de teste automatizado da `SplashScreen` (justificada em Implementation Notes, mas fora do padrão de registro usado pelas histórias anteriores) | minor | Corrigido: entrada adicionada em `deferred-work.md` |
| 6 | `AndroidManifest.xml`: `android:icon` e `android:roundIcon` apontam para o mesmo `@mipmap/ic_launcher` (não existe um `ic_launcher_round` distinto) — as Implementation Notes descreveram isso de forma um pouco imprecisa | nit | Aceito sem ação: pré-existente, fora do escopo desta história (nenhuma mudança pedida em `AndroidManifest.xml` além do necessário para o ícone, e não era necessário) |
| 7 | `Logo.kt` usa `Canvas`/`drawRoundRect` em vez de `ImageVector`, sem precedente no projeto para comparar | nit | Aceito sem ação: não há convenção estabelecida a violar (é o primeiro desenho customizado do app); abordagem é consistente internamente com o vetor do ícone |

## Design Notes

O SVG de origem (protótipo, viewBox 0 0 72 72):
```
<rect x="14" y="10" width="34" height="46" rx="6" fill="#fff" stroke="oklch(0.85 0.01 75)" stroke-width="2"/>
<line x1="21" y1="22" x2="41" y2="22" stroke="oklch(0.85 0.01 75)" stroke-width="2"/>
<line x1="21" y1="29" x2="41" y2="29" stroke="oklch(0.85 0.01 75)" stroke-width="2"/>
<line x1="21" y1="36" x2="34" y2="36" stroke="oklch(0.85 0.01 75)" stroke-width="2"/>
<rect x="34" y="26" width="34" height="34" rx="8" fill="#D6362E"/>
<rect x="47.5" y="34" width="7" height="18" fill="#fff"/>
<rect x="42" y="39.5" width="18" height="7" fill="#fff"/>
```
`oklch(0.85 0.01 75)` já tem sRGB aproximado em `Color.kt` como `SurfaceDisabled` (`#D3CFC8`) — reutilizar esse valor para o traço do documento, tanto no Composable quanto no vetor do ícone, em vez de aproximar de novo por conta própria.

Decisão do usuário: o texto da splash é "Agenda Médica" (nome completo do app, igual a `strings.xml`/`AndroidManifest.xml`), não "Agende" como está literalmente escrito no protótipo.

## Verification

**Commands:**
- `./gradlew assembleDebug testDebugUnitTest` (com `JAVA_HOME` do JBR do Android Studio) -- expected: `BUILD SUCCESSFUL`, todos os testes passam

**Manual checks (if no CLI):**
- No celular: abrir o app do zero e conferir a splash (logo, texto, ~1,6s, transição para Login, voltar não retorna a ela); instalar e olhar o ícone na tela de apps
