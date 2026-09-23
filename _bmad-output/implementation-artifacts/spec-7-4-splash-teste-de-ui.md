---
title: 'Splash Screen tem teste de UI'
type: 'chore'
created: '2026-09-23'
status: 'done'
route: 'oneshot'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** A `SplashScreen` (Story 4.1) não tem nenhum teste automatizado: nada prova que ela chama `onFinished` só depois de 1600 ms, que tocar não a encerra antes, nem que, após recriação da Activity, conta só o tempo restante (`rememberSaveable` de `startTimeMillis`). A pendência está no `deferred-work.md` desde a 4.1.

**Approach:** Usar a infraestrutura Robolectric + compose-ui-test da Story 7.3 para testar a `SplashScreen` real (`app/src/test/.../ui/splash/SplashScreenTest.kt`), com `mainClock` e `StateRestorationTester`. Para o teste ser determinístico, `SplashScreen` ganha um parâmetro `nowMillis: () -> Long = System::currentTimeMillis` (padrão = comportamento real, sem mudança para o app); o teste injeta `{ rule.mainClock.currentTime }`, o mesmo relógio virtual que comanda o `delay`.

</frozen-after-approval>

## Implementation Notes

`SplashScreen` ganhou o parâmetro `nowMillis: () -> Long = System::currentTimeMillis` (usado tanto para marcar o início quanto para calcular o tempo decorrido; o padrão preserva o comportamento real; o único chamador de produção, `AgendaMedicaNavHost`, continua passando só `onFinished`). Novo `SplashScreenTest` (4 testes, Robolectric): nome do app exibido e sem terminar antes de 1600 ms (1599 vs 1600 exatos); `onFinished` exatamente uma vez; tocar na tela não encerra a Splash; após recriação da Activity conta só o tempo restante.

Detalhes que valem registrar (aprendidos durante o trabalho): (1) `advanceTimeBy` arredonda para múltiplos de frame (16 ms), então os limites exatos usam `ignoreFrameDuration = true`; (2) `StateRestorationTester` só recria a composição se o relógio puder gerar frames, por isso `autoAdvance` fica ligado só durante `emulateSavedInstanceStateRestore()`; (3) o relógio do teste usa um offset de época realista (1,79e12) para não mascarar bugs de "zero = não definido". Prova de sanidade por mutação, no código real: duração 1600 -> 1650 faz 3 testes falharem; `rememberSaveable` -> `remember` faz exatamente o teste de recriação falhar. `./gradlew assembleDebug testDebugUnitTest` -> `BUILD SUCCESSFUL`.

## Review Triage Log

Camada blind-hunter (N=2, 12 achados). Antes dela, uma tentativa inicial (sem injeção de relógio, com `Thread.sleep`) já tinha sido descartada por não ser determinística; ver Spec Change Log.

| Achado | Veredito | Evidência / rota |
|---|---|---|
| Teste de recriação pode passar de forma vazia (relógio avança durante o restore) | `low`, patch | A mutação `remember` já o derrubava; acrescentado `assertEquals(0, finished)` logo após o restore |
| Janela do teste de recriação frouxa | `low`, patch | Passou a afirmar 1599 (ainda 0) e 1600 (terminou), o tempo restante exato |
| `finished` como `var` simples | `low`, rejeitado | Uma única thread principal no Robolectric; sem risco real |
| Sem verificar "exatamente uma vez" após recriação; limite 1599 vs 1600 não testado | `low`, patch | Ambos adicionados (avança mais 3000 ms e reafirma 1; limites exatos) |
| Teste do toque fraco; string "Agenda Médica" fixa no teste | `low`, patch parcial | O toque agora ocorre em dois momentos e o resultado é verificado em 1599/1600; a string fixa foi mantida (`R.string.app_name` não é traduzido) |
| Logo não verificado | `low`, rejeitado | Fora do que a 4.1 garante de forma testável (o logo é desenhado em Canvas); o foco é tempo e navegação |
| Relógio de teste começa em 0 | `low`, patch | Offset de época realista (`1_790_000_000_000`) |
| Tempo decorrido não é limitado por cima: relógio de parede voltando atrás faria a Splash esperar mais de 1600 ms | `low`, defer | Pré-existente (não causado por esta mudança) e exige recriação da Activity dentro dos 1,6 s com o relógio alterado; registrado em `deferred-work.md` |
| `onFinished`/`nowMillis` capturados por `LaunchedEffect` sem `rememberUpdatedState` | `low`, rejeitado | Pré-existente e especulativo: o lambda do NavHost captura só o `navController`, que é estável |
| Comentário "(spec-7-4)" no código de produção | `low`, patch | Trocado por `@param` no KDoc |
| `StateRestorationTester` legado e dependência das configs da 7.3 | `low`, patch parcial | Comentário no teste explica o motivo e o `autoAdvance`; as configs da 7.3 já estão commitadas |
| Nenhum teste exercita o parâmetro padrão nem o NavHost | `low`, rejeitado | O padrão é a mesma chamada de antes (`System::currentTimeMillis`); testar em tempo real seria dependente do relógio de parede (justamente o problema que motivou a injeção) |

## Spec Change Log

- **Abordagem original substituída (antes da revisão, rascunho ainda não apresentado ao usuário):** o plano era não tocar em produção e simular o tempo decorrido com `Thread.sleep` real, já que `startTimeMillis` usa `System.currentTimeMillis()`. Foi abandonado porque o Robolectric leva segundos para subir: o tempo real entre gravar `startTimeMillis` e o primeiro frame às vezes já passa de 1600 ms, então `onFinished` disparava em `virtual=0` numa execução e em `virtual=247` na outra (não determinístico), e a mutação `rememberSaveable` -> `remember` passou sem ser detectada. KEEP: `SplashScreen` continua usando `rememberSaveable` + `LaunchedEffect`, com o mesmo cálculo de tempo restante.
