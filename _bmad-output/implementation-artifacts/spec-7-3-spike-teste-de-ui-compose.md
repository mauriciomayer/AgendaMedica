---
title: 'Spike de teste de UI Compose com Robolectric'
type: 'chore'
created: '2026-09-23'
status: 'done'
route: 'oneshot'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** O projeto não tem teste de UI Compose (`app/build.gradle.kts` documenta a ausência) e isso deixou 4 pendências no `deferred-work.md`: o `CancelConfirmDialog` não-dispensável (Story 6.4), a ligação de `isEmail`/filtro no campo de e-mail (6.5), o cursor ao rejeitar tecla no meio do texto (6.5) e a Splash (4.1). Só teste manual as cobre hoje.

**Approach:** Spike: adicionar Robolectric + `compose-ui-test-junit4` ao `testDebugUnitTest` (JVM, sem emulador; `compileSdk` 37, Gradle em JDK 25 — risco de compatibilidade conhecido) e provar a viabilidade com testes reais do `CancelConfirmDialog` (Sim/Não chamam seus callbacks; voltar do sistema não fecha nem chama `onNao`) e do `LabeledTextField(isEmail = true)` (filtro aplicado ao digitar/colar; cursor não salta ao rejeitar tecla no meio do texto). Se a infra não for viável neste ambiente, reverter as mudanças de build e documentar o motivo (a história termina sem a infra). O resultado do spike fica registrado nas Implementation Notes.

</frozen-after-approval>

## Implementation Notes

**Resultado do spike: VIÁVEL.** Robolectric 4.17 + `compose-ui-test-junit4` rodam no `testDebugUnitTest` deste projeto, sem emulador, no Android 17 (SDK 37, igual ao `compileSdk`; fixado em `app/src/test/resources/robolectric.properties`). Dois obstáculos, ambos resolvidos: (1) Espresso não vem transitivo, adicionado só para `Espresso.pressBack()`; (2) o JDK 25 do Android Studio não exporta `jdk.internal.access` para o Robolectric (`IllegalAccessException` em `AndroidInterceptors`), resolvido com `--add-opens` na JVM de teste (`testOptions.unitTests.all` em `app/build.gradle.kts`). A suíte completa passou de 181 para 200+ testes, sem falhas; o `testDebugUnitTest` ficou ~20-30s mais lento.

Testes novos (`ui/components/`): `CancelConfirmDialogTest` (Sim/Não chamam seus callbacks; voltar do sistema não fecha nem chama callback; estado "Cancelando..." desabilita os dois botões), `LabeledTextFieldEmailTest` (máscara ao digitar, segundo `@`, campo que não é e-mail intocado, cursor ao rejeitar tecla no meio do texto) e `EmailFieldWiringTest` (as 4 telas reais — Login, Recuperar Senha, Cadastro de Médico, Cadastro de Paciente — aplicam a máscara; cursor com ViewModel/StateFlow reais; troca de aba do Login). Prova de sanidade por mutação: quebrar o diálogo (voltar chamando `onNao`) faz o teste de "voltar" falhar; remover `isEmail = true` do Login faz o teste do Login falhar.

**O spike achou um bug real (cursor):** ao rejeitar uma tecla no meio do texto de um e-mail, o texto ficava certo mas o cursor avançava uma posição (esperado 2, obtido 3) — a suspeita registrada em `deferred-work.md` na Story 6.5. Corrigido em `LabeledTextField`: o campo de e-mail (e só ele) passou a manter um `TextFieldValue` interno espelhando `value`, reposicionando o cursor quando o filtro remove caracteres. Campos de senha/nome/busca continuam no overload `String`, idênticos ao que eram.

Pendências do `deferred-work.md` fechadas por esta história: diálogo não-dispensável (6.4, exceto o toque fora, ver abaixo), ligação de `isEmail` nas 4 telas e cursor (6.5). Continua sem cobertura: toque fora do diálogo (não há como simular o toque na janela do diálogo de forma confiável em Robolectric) e a Splash (ficam para uma história futura, agora com infraestrutura).

## Review Triage Log

Camada blind-hunter (N=4, 9 achados sobre o diff completo).

| Achado | Veredito | Evidência / rota |
|---|---|---|
| `if (texto != value) onValueChange` compara com o parâmetro "velho": duas edições antes de recompor podem se anular | `medium`, patch | Real. Passou a comparar com o texto anterior do próprio campo (`anterior`) |
| Espelho `TextFieldValue` aplicado a TODOS os campos (senha, nome, busca): risco para IME/composição, mudança externa, ViewModel que transforma o valor | `medium`, patch | Argumento válido de blast radius: o caminho novo ficou restrito a `isEmail`; os demais campos usam o overload `String` como antes |
| Cursor inicial em 0 vs. fim após mudança externa | `low`, patch | Estado inicial do espelho agora usa `TextRange(value.length)` |
| Cálculo do cursor assume caracteres removidos antes do cursor | `low`, rejeitado | O filtro só remove do trecho recém-inserido (`filtrarEmail` usa o texto anterior); caracteres inválidos depois do cursor não existem porque o campo nunca os aceita |
| Teste de "voltar" do diálogo pode passar de forma vazia | `false` | Prova de mutação: com o diálogo mudado para `onDismissRequest = onNao`/dismiss true, o teste falha (linha 58) |
| `cancelando = true` sem teste | `low`, patch | Adicionado `while cancelling, Sim reads Cancelando and both buttons are disabled` |
| Wiring test frágil, sem troca de aba do Login, repositórios `relaxed` | `low`, patch parcial | Adicionado teste de troca de aba; casos de colar/segundo `@` já cobertos no teste do componente; o restante é preferência de estilo |
| `testReleaseUnitTest` falharia sem `ui-test-manifest` | `false` | A tarefa `testReleaseUnitTest` não existe neste projeto (erro "Task not found", inclusive sem as mudanças desta história); o README já usa `testDebugUnitTest` |
| Robolectric sem SDK fixado | `low`, patch | Rodou no SDK 37 (jar `android-all-instrumented-17`); fixado com `robolectric.properties` (`sdk=37`) |
| Custo do Gradle (`isIncludeAndroidResources` global, `--add-opens` em toda JVM de teste, Espresso só para `pressBack`) | `low`, rejeitado | Necessário para o Robolectric no JDK 25 (o `pressBack` real chega à janela do diálogo, `onBackPressed()` da Activity não chegaria); custo medido ~20-30s |
| KDoc de `LabeledTextField` desatualizado | `low`, patch | Reescrito descrevendo `isEmail`, o espelho `TextFieldValue` e por que só nesse modo |

