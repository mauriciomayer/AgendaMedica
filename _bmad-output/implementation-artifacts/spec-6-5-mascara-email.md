---
title: 'Campos de e-mail aceitam só caracteres válidos, como no login do Google'
type: 'feature'
created: '2026-09-23'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
context: ['{project-root}/_bmad-output/implementation-artifacts/epic-6-context.md']
baseline_commit: '1a27b0e8e2462dc44f11f2528cf2542e27a628a6'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** A Story 6.1 leu "o campo de e-mail aceita qualquer caractere" como validação de formato (só desabilita o botão via `EMAIL_PATTERN`). O usuário queria uma **máscara de digitação**, como no campo de e-mail das telas de login Google no Android: espaço, acento e caracteres inválidos simplesmente não entram. Hoje `LabeledTextField` (`Components.kt:138`) não tem `keyboardOptions` nem filtro, então tudo entra.

**Approach:** `LabeledTextField` ganha `isEmail: Boolean = false` (mesmo padrão de `isPassword`). Com `isEmail`, aplica teclado de e-mail e passa todo texto recebido por uma função pura `filtrarEmail(String): String` antes de repassar ao `onValueChange`. As 4 telas com campo de e-mail passam `isEmail = true`. Nenhuma mudança nos ViewModels; a validação de formato da 6.1 (`EMAIL_PATTERN`) continua valendo, por cima da máscara.

## Boundaries & Constraints

**Always:**
- `filtrarEmail` mantém apenas letras ASCII sem acento (`A-Z`, `a-z`), dígitos, `@`, `.`, `_`, `-`, `+`, `%`; descarta o resto silenciosamente (sem mensagem de erro) e mantém **só o primeiro `@`** (decisão do usuário: segundo `@` é bloqueado). Maiúsculas/minúsculas preservadas como digitadas.
- Texto colado passa pelo mesmo filtro e é limpo, não rejeitado por inteiro (ex.: `" joao silva@gmail.com "` -> `"joaosilva@gmail.com"`).
- Com `isEmail`: `KeyboardOptions(keyboardType = KeyboardType.Email, capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false)`; sem definir `imeAction` (comportamento atual do botão de ação do teclado não muda).
- Aplicar em todos os campos de e-mail do app: Login, Cadastro de Médico, Cadastro de Paciente, Recuperar Senha (decisão do usuário).

**Never:**
- Não mexer nos ViewModels, em `EMAIL_PATTERN`, nem no `.trim()` que os ViewModels já fazem. Não aplicar `isEmail` a campos que não são e-mail (nome, senha, busca).
- Não forçar minúsculas. Não adicionar biblioteca nova.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Digitar espaço | campo com `joao` | texto continua `joao` | silencioso |
| Digitar acento/emoji | `ç`, `é`, `😀` | não entra | silencioso |
| Segundo `@` | campo com `a@b` | continua `a@b` | silencioso |
| Colar com lixo | `" joao silva@gmail.com "` | `joaosilva@gmail.com` | silencioso |
| Caracteres válidos | `Joao.Silva+x_y-z%1@Mail.com` | entra inalterado (maiúsculas preservadas) | N/A |
| Teclado | campo de e-mail focado | teclado de e-mail (`@` à mão), sem maiúscula automática, sem autocorreção | N/A |
| Campo que não é e-mail | Nome, Senha, Busca | comportamento inalterado | N/A |

</frozen-after-approval>

## Code Map

- `app/src/main/java/com/agendamedica/app/ui/components/Components.kt:138-177` -- `LabeledTextField`: adicionar `isEmail: Boolean = false`, `keyboardOptions` condicional e `onValueChange = { onValueChange(if (isEmail) filtrarEmail(it) else it) }`; criar `internal fun filtrarEmail(input: String): String` no mesmo arquivo (testável no mesmo módulo); imports: `KeyboardOptions`, `KeyboardType`, `KeyboardCapitalization` (`androidx.compose.foundation.text.KeyboardOptions`, `androidx.compose.ui.text.input.KeyboardType`, `androidx.compose.ui.text.input.KeyboardCapitalization`)
- `app/src/main/java/com/agendamedica/app/ui/auth/LoginScreen.kt:86` -- campo "E-mail": `isEmail = true`
- `app/src/main/java/com/agendamedica/app/ui/auth/RecuperarSenhaScreen.kt:72` -- idem
- `app/src/main/java/com/agendamedica/app/ui/doctor/CadastroMedicoScreen.kt:95` -- idem
- `app/src/main/java/com/agendamedica/app/ui/patient/CadastroPacienteScreen.kt:64` -- idem
- `app/src/test/java/com/agendamedica/app/ui/components/` -- novo `EmailInputFilterTest.kt` (JUnit puro, mesmo padrão de `MinhaAgendaScreenKtTest`)

## Tasks & Acceptance

**Execution:**
- [ ] `Components.kt` -- `filtrarEmail` + `isEmail` em `LabeledTextField` -- ponto único, as 4 telas herdam
- [ ] `LoginScreen.kt`, `RecuperarSenhaScreen.kt`, `CadastroMedicoScreen.kt`, `CadastroPacienteScreen.kt` -- passar `isEmail = true` no campo de e-mail
- [ ] `EmailInputFilterTest.kt` -- tabela cobrindo todas as linhas da matriz que são da função pura (espaço no meio/pontas, acento, emoji, segundo `@`, `@` no início, colar com lixo, todos os caracteres permitidos, maiúsculas preservadas, string vazia)

**Acceptance Criteria:**
- Given qualquer campo de e-mail do app, when digito espaço, acento ou um segundo `@`, then o caractere não entra e nenhum erro aparece
- Given um campo de e-mail, when colo um e-mail com espaços sobrando, then o campo fica com o e-mail limpo
- Given um campo de e-mail focado, then o teclado é o de e-mail, sem maiúscula automática nem autocorreção

## Implementation Notes

`LabeledTextField` ganhou `isEmail`; `filtrarEmail` vive em `Components.kt`; as 4 telas passam `isEmail = true`; `EmailInputFilterTest.kt` cobre a função pura. `./gradlew assembleDebug testDebugUnitTest` -> `BUILD SUCCESSFUL` (verificado pelo subagente e, de forma independente, pelo orquestrador antes e depois da revisão). Diff conferido contra o `baseline_commit`, não só pelo relatório.

Pós-revisão: `filtrarEmail` passou a receber `anterior: String = ""` (o `value` atual do campo). Com isso, digitar `@` antes de um `@` já existente descarta só o `@` recém-inserido (diferença entre `anterior` e o novo texto, por prefixo/sufixo comuns) em vez de apagar o original — o esboço das Design Notes só via a string resultante e violava a linha "Segundo `@`" da matriz nesse caso. Testes novos: `@` no início/ao lado/no meio, substituição do trecho que continha o `@`, colar com dois `@` em campo vazio, `@@`, só caracteres inválidos, lookalikes Unicode (`＠` full-width, dígito árabe-índico) e idempotência.

## Spec Change Log

## Review Triage Log

3 camadas em paralelo sobre o diff real: blind-hunter (N=3, 15 achados), edge-case-hunter (9), verification-gap (1, pré-verificado).

| Achado | Camada | Veredito | Evidência / rota |
|---|---|---|---|
| Digitar `@` antes de um `@` existente apaga o original (`a@b` + `@` no início -> `@ab`); viola a linha "Segundo `@`" da matriz | edge + blind | `medium`, patch | Reproduzido por leitura de `filtrarEmail` (só via a string final). Corrigido com `anterior`; ver Implementation Notes |
| Cursor/IME pode pular ao rejeitar tecla no meio do texto (String-based `OutlinedTextField`) | blind + edge (x2) | `maybe-false`, defer | Padrão comum de filtro em `onValueChange`; só se resolve no aparelho. Já é checagem manual da spec; registrado em `deferred-work.md` |
| Nenhum teste garante que `isEmail = true` está ligado nas 4 telas/keyboardOptions | verification-gap + blind | defer | Precisa de infra de teste de UI Compose (ausente, `app/build.gradle.kts:118-119`). Extrair `keyboardOptionsFor`/wrapper só para testá-los foi rejeitado (complexidade sem cobrir as omissões por tela). Registrado em `deferred-work.md` |
| ViewModels não filtram; estado restaurado/autofill poderia burlar | blind + edge | `false` | Autofill e colar passam por `onValueChange` (filtrado); nenhum caminho programático seta o e-mail hoje (sem restauração/prefill); a spec proíbe mexer nos ViewModels |
| Conjunto de caracteres estreito (`'`, `!`, IDN...) e contas antigas com esses caracteres não conseguem logar | blind + edge x2 | `low`, rejeitado | Conjunto foi decidido pelo usuário na sala/spec (Always); alargá-lo é editar a spec. Tradeoff comunicado ao usuário |
| `a@b@c` colado vira `a@bc` (mantém o primeiro `@`) | blind | `low`, rejeitado | É o comportamento especificado ("só o primeiro `@`") |
| Não bloqueia ponto inicial/duplo nem domínio ausente | blind | `false` | Máscara é por caractere; formato continua a cargo de `EMAIL_PATTERN` (6.1), que já exige domínio |
| Testes faltando: `@@`, só-inválidos, idempotência, lookalikes Unicode, colar com autofill | blind + edge | `low`, patch | Cobertos pelos testes novos (autofill/colar usam o mesmo caminho da função) |
| `isEmail` + `isPassword` simultâneos; flag booleana acopla comportamento | blind + edge | `false` | Nenhum call site combina; `require` falharia alto num estado inalcançável |
| Regex por caractere aloca String | blind | `low`, rejeitado | Entrada de teclado, custo desprezível; troca é refatoração sem ganho perceptível |
| `autoCorrectEnabled` pode não existir na versão do Compose | blind | `false` | BOM 2026.08.00; compila e passa (`BUILD SUCCESSFUL`) |
| Nomes PT/EN inconsistentes; sem anúncio de acessibilidade ao rejeitar tecla | blind | `low`, rejeitado | Identificadores em PT já são a convenção (`formatHora`, `slotsDoDia`); o campo do Google também não anuncia |

## Design Notes

```kotlin
private val EMAIL_CHARS = Regex("[A-Za-z0-9._%+\\-]")

internal fun filtrarEmail(input: String): String {
    val sb = StringBuilder()
    var temArroba = false
    for (c in input) {
        when {
            c == '@' -> if (!temArroba) { sb.append(c); temArroba = true }
            EMAIL_CHARS.matches(c.toString()) -> sb.append(c)
        }
    }
    return sb.toString()
}
```
Filtro no componente (não nos ViewModels): todos os campos de e-mail passam por `LabeledTextField`, então um único ponto cobre as 4 telas. Comportamento do cursor ao rejeitar tecla no meio do texto não é coberto por teste automatizado (o projeto não tem infra de teste de UI Compose) — verificação manual no aparelho.

## Verification

**Commands:**
- `./gradlew assembleDebug testDebugUnitTest` (JAVA_HOME = JBR do Android Studio) -- expected: `BUILD SUCCESSFUL`, todos os testes passam

**Manual checks (if no CLI):**
- No aparelho, nos 4 campos de e-mail: digitar espaço/acento (não entra), tentar segundo `@` (não entra), colar `" joao silva@gmail.com "` (fica limpo), conferir teclado de e-mail; editar no meio do texto digitando um espaço e ver se o cursor fica no lugar
