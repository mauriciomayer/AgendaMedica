---
title: 'Login valida o e-mail, mostra a senha digitada e bloqueia por papel selecionado'
type: 'feature'
created: '2026-09-23'
status: 'review'
route: 'dispatch'
review_loop_iteration: 0
context: ['{project-root}/_bmad-output/implementation-artifacts/epic-6-context.md']
baseline_commit: 'c11f4aaa6050553945d1c5722bcbfa16046bd11d'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** O Login aceita qualquer texto no campo de e-mail (sem validar formato), nunca deixa ver a senha digitada, e ignora a aba Paciente/Médico selecionada — um login bem-sucedido com credenciais do outro papel é silenciosamente redirecionado para a tela certa em vez de avisar o usuário. Os dois primeiros são lacunas; o terceiro é uma decisão de design da Story 1.2 (achado #8: "aba é só visual") que o usuário testou na prática e decidiu reverter.

**Approach:** Reaproveitar o padrão de validação de e-mail já usado no Cadastro e em Recuperar Senha; adicionar um ícone de olho ao componente de campo de senha compartilhado (`LabeledTextField`), o que resolve as quatro telas com senha de uma vez; e, no `LoginViewModel`, comparar a aba selecionada com o papel real após o login bem-sucedido, encerrando a sessão e mostrando um erro claro em vez de redirecionar quando não baterem.

## Boundaries & Constraints

**Always:**
- E-mail do Login segue o mesmo padrão já usado (`EMAIL_PATTERN`, replicado por convenção em cada ViewModel — não extrair para um util compartilhado nesta história): botão "Entrar" fica desabilitado até o e-mail bater com `algo@algo.algo`, sem mensagem de erro antes do envio.
- O ícone de olho entra no componente compartilhado `LabeledTextField` (`ui/components/Components.kt`), nunca duplicado por tela — assim cobre Login, Cadastro de Médico, Cadastro de Paciente e Nova Senha de uma vez. Estado de visibilidade é local ao campo (`remember`), reseta ao recompor a tela do zero; alternar o ícone nunca perde o texto já digitado.
- Depois de `signIn` bem-sucedido, o papel real (`isCurrentUserDoctor()`) é comparado com `selectedRole`. Se não baterem: `signOut()` é chamado antes de qualquer outra coisa (nunca fica sessão aberta), `isLoading` volta a `false`, e uma mensagem nomeando o papel real da conta aparece no mesmo lugar que já mostra erro de credencial errada — sem navegar para nenhuma tela autenticada.
- Se baterem, o comportamento é exatamente o de hoje (navega para Minha Agenda ou Busca conforme o papel).

**Never:**
- Não mexer em `AuthRepository`/`DoctorRepository` além de reaproveitar `signIn`/`signOut`/`isCurrentUserDoctor()`, já existentes. Nenhuma mudança de RLS, função Postgres ou Edge Function — o papel já é lido ao vivo de `profiles`, isso não muda.
- Não adicionar campo, tela ou fluxo novo. Não mexer em Cadastro/Recuperar Senha além do que o ícone de olho do componente compartilhado já resolve automaticamente.
- Não remover nem enfraquecer a mensagem neutra de "credenciais erradas" já existente (AD-9) — o novo erro de papel errado só aparece quando as credenciais estavam corretas.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| E-mail mal formatado | ex.: "abc", "a@b", "a b@c.com" | Botão "Entrar" desabilitado | N/A |
| E-mail válido + senha preenchida | ex.: "a@b.com" | Botão "Entrar" habilitado (comportamento já existente) | N/A |
| Toque no ícone de olho | campo de senha com texto digitado | Texto fica visível; ícone muda para "ocultar"; texto digitado não se perde | N/A |
| Segundo toque no ícone de olho | senha já visível | Volta a ocultar (`•••`) | N/A |
| Aba Médico + credenciais de conta Paciente | login e senha corretos, mas a conta é de paciente | Sessão aberta é encerrada; mensagem "Este e-mail é de uma conta de paciente. Selecione a aba \"Paciente\"."; permanece no Login | Sem navegação |
| Aba Paciente + credenciais de conta Médico | login e senha corretos, mas a conta é de médico | Sessão aberta é encerrada; mensagem "Este e-mail é de uma conta de médico. Selecione a aba \"Médico\"."; permanece no Login | Sem navegação |
| Aba e papel batem | credenciais corretas, papel real = aba selecionada | Comportamento inalterado: navega para Minha Agenda (médico) ou Busca (paciente) | N/A |
| Credenciais erradas | e-mail/senha não correspondem a nenhuma conta | Mensagem genérica de sempre, sem detalhe técnico (AD-9, inalterado) | N/A |

</frozen-after-approval>

## Code Map

- `app/src/main/java/com/agendamedica/app/ui/auth/LoginViewModel.kt` -- `isSubmitEnabled` (linha 37: só checa `isNotBlank`); `submit()` (linhas 84-118: após `isCurrentUserDoctor()`, decide `navigateToMinhaAgenda`/`navigateToBusca` sem checar `selectedRole`) -- é aqui que entram o regex e a comparação de papel
- `app/src/main/java/com/agendamedica/app/ui/patient/CadastroPacienteViewModel.kt`, `app/src/main/java/com/agendamedica/app/ui/doctor/CadastroMedicoViewModel.kt`, `app/src/main/java/com/agendamedica/app/ui/auth/RecuperarSenhaViewModel.kt` -- cada um já tem seu próprio `private val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")` -- copiar o mesmo padrão para `LoginViewModel.kt` (convenção já estabelecida, não extrair)
- `app/src/main/java/com/agendamedica/app/ui/components/Components.kt` -- `LabeledTextField` (linhas 121-145): `visualTransformation` hoje é incondicional; ganha estado de visibilidade e `trailingIcon`. Usado por `LoginScreen.kt:96`, `NovaSenhaScreen.kt:67`, `CadastroMedicoScreen.kt:101`, `CadastroPacienteScreen.kt:70` (nenhum desses 4 call sites precisa mudar — o ícone é interno ao componente)
- `app/build.gradle.kts` -- linha 92, só tem `material-icons-core` (sem `Visibility`/`VisibilityOff`); adicionar `implementation("androidx.compose.material:material-icons-extended")` logo abaixo, coberto pelo BOM já declarado (linha 87), sem versão explícita
- `app/src/main/java/com/agendamedica/app/data/repository/AuthRepository.kt` -- `signIn`/`signOut` (linhas 100, 107) já existem e bastam; `DoctorRepository.isCurrentUserDoctor()` (linha 223) idem -- reaproveitar, não editar
- `app/src/test/java/com/agendamedica/app/ui/auth/LoginViewModelTest.kt` -- padrão de teste já usado (`UnconfinedTestDispatcher` compartilhado, MockK); os testes existentes de credenciais corretas (médico e paciente) já cobrem o caso "aba bate com o papel" e não devem quebrar

## Tasks & Acceptance

**Execution:**
- [x] `app/src/main/java/com/agendamedica/app/ui/auth/LoginViewModel.kt` -- `EMAIL_PATTERN` + `isSubmitEnabled` exige formato válido; `submit()` compara `selectedRole` com `isCurrentUserDoctor()`, chama `signOut()` e mostra erro claro em caso de divergência, sem navegar
- [x] `app/src/main/java/com/agendamedica/app/ui/components/Components.kt` -- `LabeledTextField` ganha ícone de olho (mostrar/ocultar) quando `isPassword = true`
- [x] `app/build.gradle.kts` -- dependência `material-icons-extended`
- [x] `app/src/test/java/com/agendamedica/app/ui/auth/LoginViewModelTest.kt` -- testes novos para as duas linhas de divergência de papel (médico logando na aba paciente e vice-versa): `signOut` chamado, nenhuma navegação, mensagem de erro presente e nomeando o papel certo

**Acceptance Criteria:**
- Given o formulário de Login, when o e-mail digitado não tem o formato `algo@algo.algo`, then o botão "Entrar" fica desabilitado
- Given o campo de senha em qualquer tela que o usa, when o usuário toca no ícone de olho, then a senha digitada fica visível; tocar de novo oculta de novo
- Given que o usuário selecionou uma aba e digitou e-mail/senha válidos de uma conta do outro papel, when o login seria aceito, then o acesso é negado com uma mensagem nomeando o papel real da conta, a sessão é encerrada, e o usuário permanece no Login

## Implementation Notes

- `LoginViewModel.kt`: `EMAIL_PATTERN` duplicado (byte a byte igual às outras 3 cópias); `isSubmitEnabled` exige formato válido; `submit()` compara `selectedRole` com `isCurrentUserDoctor()` logo após o login bem-sucedido — em caso de divergência, `signOut()` é chamado antes de qualquer atualização de estado, `isLoading` volta a `false` e o erro é mostrado, sem navegar.
- `Components.kt`: `LabeledTextField` ganhou estado local (`remember`) de visibilidade e `trailingIcon` condicional; nenhum dos 4 call sites precisou mudar.
- `app/build.gradle.kts`: `material-icons-extended` adicionada (coberta pela BOM já declarada, sem versão explícita).
- `./gradlew assembleDebug testDebugUnitTest`: BUILD SUCCESSFUL.

## Spec Change Log

## Review Triage Log

Três revisores paralelos (correção/segurança, testes/matriz, escopo/consistência). Nenhum blocker.

| # | Achado | Severidade | Ação |
|---|--------|-----------|------|
| 1 | `spec-1-2-paciente-se-cadastra.md` não tinha nota de erratum apontando para esta história, apesar de reverter seu achado #8 (padrão já usado pela Story 5.1 para as specs que ela substituiu) | major | Corrigido: nota de correção adicionada às Design Notes de `spec-1-2` |
| 2 | `authRepository.signOut()` no ramo de divergência de papel não checa o `Result` — se o próprio `signOut()` falhar, a sessão da conta errada pode continuar viva em memória sem log nem aviso (mesma classe de risco já registrada para a Story 1.3) | minor (aceito conscientemente, já previsto no esboço da spec) | Registrado em `deferred-work.md`; raio de impacto hoje é contido (app nunca restaura sessão automaticamente) |
| 3 | Os dois testes de "papel bate com a aba" (caminho de sucesso) não verificavam que `signOut()` **não** é chamado — um bug que chamasse `signOut()` incondicionalmente passaria despercebido | minor | Corrigido: `coVerify(exactly = 0) { authRepository.signOut() }` adicionado aos dois testes |
| 4 | As duas asserções combinadas (`a && b` num único `assertTrue`) nos testes de divergência de papel não indicam qual metade falhou | nit | Corrigido: separadas em duas asserções independentes |
| 5 | Só 1 dos 3 exemplos de e-mail malformado da matriz tem teste dedicado; nenhum teste cobre `signOut()` falhando durante a divergência de papel; ícone de olho (mostrar/ocultar) não tem teste automatizado | nit / aceito | Sem ação: `EMAIL_PATTERN` já é usado (e testado) em 3 outros ViewModels; o projeto não tem infraestrutura de teste de Compose UI (mesma limitação já documentada em outras histórias) — checagem manual já prevista na Verification |
| 6 | Exemplo de mensagem em `epics.md` ("Selecione a aba correta") diverge da redação exata da spec/código ("Selecione a aba \"Médico\"") | nit | Aceito sem ação: pré-existente, claramente marcado como exemplo (`ex.:`), baixo risco |
| 7 | Dois idiomas diferentes para botão de ícone acessível no mesmo arquivo (`AccessibleIconButton` vs. o novo `IconButton` do olho) | nit | Aceito sem ação: os dois são acessíveis; o ícone de olho tem contexto visível do campo, diferente de um botão só-ícone genérico |

## Design Notes

**`LabeledTextField` com ícone de olho** (`Components.kt`):
```kotlin
@Composable
fun LabeledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
) {
    var senhaVisivel by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (isPassword && !senhaVisivel) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (isPassword) {
            {
                IconButton(onClick = { senhaVisivel = !senhaVisivel }) {
                    Icon(
                        imageVector = if (senhaVisivel) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (senhaVisivel) "Ocultar senha" else "Mostrar senha",
                    )
                }
            }
        } else null,
        shape = ShapeMd,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AgendaMedicaColors.accentPrimary,
            unfocusedBorderColor = AgendaMedicaColors.borderInput,
            focusedContainerColor = AgendaMedicaColors.surfaceInput,
            unfocusedContainerColor = AgendaMedicaColors.surfaceInput,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}
```

**`LoginViewModel.submit()` — comparação de papel** (esboço; ajustar ao estilo do arquivo):
```kotlin
val isDoctor = doctorRepository.isCurrentUserDoctor().getOrElse { throwable -> /* já existe */ }
val esperandoMedico = state.selectedRole == LoginRole.MEDICO
if (isDoctor != esperandoMedico) {
    authRepository.signOut()
    val papelReal = if (isDoctor) "médico" else "paciente"
    val abaCerta = if (isDoctor) "Médico" else "Paciente"
    _uiState.update {
        it.copy(isLoading = false, errorMessage = "Este e-mail é de uma conta de $papelReal. Selecione a aba \"$abaCerta\".")
    }
    return@launch
}
```

Por que duplicar `EMAIL_PATTERN` em vez de extrair: é a convenção já usada em três ViewModels (`CadastroMedicoViewModel`, `CadastroPacienteViewModel`, `RecuperarSenhaViewModel`) — seguir o padrão existente em vez de introduzir uma refatoração fora do pedido desta história.

Por que `material-icons-extended` em vez de desenhar o ícone à mão (como o logo da Story 4.1): é uma dependência de primeira parte (Google), já coberta pela BOM do Compose já declarada, sem versão a fixar — desenhar um ícone padrão do Material à mão só duplicaria trabalho que a própria biblioteca resolve corretamente em todos os tamanhos/temas.

## Verification

**Commands:**
- `./gradlew assembleDebug testDebugUnitTest` (com `JAVA_HOME` do JBR do Android Studio) -- expected: `BUILD SUCCESSFUL`, todos os testes passam

**Manual checks (if no CLI):**
- No celular: digitar um e-mail sem "@" no Login e conferir que "Entrar" fica desabilitado; tocar no ícone de olho no campo de senha (Login, Cadastro Médico, Cadastro Paciente, Nova Senha) e ver a senha aparecer/sumir; logar na aba Paciente com um e-mail de médico (e vice-versa) e ver a mensagem de erro, permanecendo no Login
