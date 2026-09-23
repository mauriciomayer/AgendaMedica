---
title: 'Usuário sai da própria conta (logout)'
type: 'feature'
created: '2026-09-23'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
context: ['{project-root}/_bmad-output/implementation-artifacts/epic-6-context.md']
baseline_commit: '3b24cbc4c2f5d70f421973db03f1ef4af4cabd30'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Não existe nenhum botão de logout no app — nenhuma tela (Minha Agenda, Minhas Consultas) tem forma de encerrar a sessão sem forçar o fechamento do app. Como o app não restaura sessão sozinho (sempre abre na Splash/Login), forçar fechamento já "desloga" na prática, mas não há como trocar de conta sem isso.

**Approach:** `MinhaAgendaViewModel` e `MinhasConsultasViewModel` ganham um `logout()` que chama `authRepository.signOut()` (mesmo padrão já usado por `NovaSenhaViewModel`) e emite um evento de navegação; cada tela ganha um botão "Sair" no cabeçalho que aciona esse método e, ao navegar, limpa a pilha até o Login (mesmo `goToLogin` já usado pelo fluxo de recuperação de senha).

## Boundaries & Constraints

**Always:**
- `logout()` chama `authRepository.signOut()` e então emite o evento de navegação (`SharedFlow`), mesmo padrão de `NovaSenhaViewModel.onBack()`/`submit()` — não checar o `Result` de `signOut()` (mesma convenção já aceita no restante do código).
- Ao navegar para o Login após logout, a pilha de navegação é limpa por inteiro (`popUpTo(navController.graph.id) { inclusive = true }`, via o `goToLogin` já existente em `AgendaMedicaNavHost.kt`) — o botão voltar do sistema, a partir do Login, sai do app.
- O botão "Sair" fica visível no cabeçalho de Minha Agenda (Médico) e Minhas Consultas (Paciente), sempre acessível (nunca desabilitado, mesmo com a lista de consultas carregando ou com erro).

**Never:**
- Não mexer em `AuthRepository`/`DoctorRepository`/`AppointmentRepository` além de reaproveitar `signOut()`, já existente. Nenhuma mudança de RLS, função Postgres ou Edge Function.
- Não adicionar tela nem fluxo de confirmação para o logout (diferente do cancelamento de consulta) — um toque em "Sair" já encerra a sessão direto, sem "Tem certeza?".
- Não adicionar o botão em nenhuma outra tela (Busca, Detalhe do Médico, Confirmação) — só nas duas telas citadas no Épico 6.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Toque em "Sair" (Médico, Minha Agenda) | autenticado, perfil carregado | Sessão encerrada, navega ao Login, pilha limpa | N/A |
| Toque em "Sair" (Paciente, Minhas Consultas) | autenticado, lista carregada | Sessão encerrada, navega ao Login, pilha limpa | N/A |
| "Sair" com a lista de consultas ainda carregando ou com erro | `consultasLoading`/`consultasErro`/`errorMessage` presentes | Logout funciona normalmente, independente do estado da lista | N/A |
| Botão voltar do sistema após o logout | usuário no Login, aperta voltar | Sai do app (pilha já foi limpa) | N/A |

</frozen-after-approval>

## Code Map

- `app/src/main/java/com/agendamedica/app/ui/doctor/MinhaAgendaViewModel.kt` -- já importa `AuthRepository` (linha 6, usado só para `doctorIdProvider`, linha 52); adicionar parâmetro `authRepository: AuthRepository = AuthRepository()` próprio, `_navigateToLogin` (`MutableSharedFlow<Unit>`) e `fun logout()`
- `app/src/main/java/com/agendamedica/app/ui/patient/MinhasConsultasViewModel.kt` -- ainda não importa `AuthRepository`; mesmo padrão do arquivo acima
- `app/src/main/java/com/agendamedica/app/ui/auth/NovaSenhaViewModel.kt` -- padrão já usado para logout: `authRepository.signOut()` sem checar `Result`, seguido de emitir/coletar evento de navegação
- `app/src/main/java/com/agendamedica/app/ui/doctor/MinhaAgendaScreen.kt` -- linhas 73-78: `Text("Minha Agenda", ...)` sozinho; vira `Row` com o título + `TextButton("Sair")`; falta importar `Row`, `TextButton`, `LaunchedEffect`; a screen ganha `onLogout: () -> Unit`
- `app/src/main/java/com/agendamedica/app/ui/patient/MinhasConsultasScreen.kt` -- linhas 74-78: `Text("Minhas consultas", ...)` depois do botão voltar; vira `Row` com o título + `TextButton("Sair")` (já importa `Row`; falta `TextButton`, `LaunchedEffect`); a screen ganha `onLogout: () -> Unit`
- `app/src/main/java/com/agendamedica/app/ui/navigation/AgendaMedicaNavHost.kt` -- `composable(Routes.MINHA_AGENDA)`/`composable(Routes.MINHAS_CONSULTAS)`: adicionar `onLogout = { navController.goToLogin(passwordChanged = false) }` (helper privado já existente no fim do arquivo, já usado por `RecuperarSenhaScreen`/`NovaSenhaScreen`)
- `app/src/test/java/com/agendamedica/app/ui/doctor/MinhaAgendaViewModelTest.kt`, `app/src/test/java/com/agendamedica/app/ui/patient/MinhasConsultasViewModelTest.kt` -- padrão de teste já usado (`UnconfinedTestDispatcher` compartilhado, MockK); `LoginViewModelTest.kt` tem o padrão exato de `coVerify { authRepository.signOut() }` + coleta de `SharedFlow` de navegação a reaproveitar

## Tasks & Acceptance

**Execution:**
- [x] `app/src/main/java/com/agendamedica/app/ui/doctor/MinhaAgendaViewModel.kt` -- `authRepository`, `navigateToLogin`, `logout()`
- [x] `app/src/main/java/com/agendamedica/app/ui/patient/MinhasConsultasViewModel.kt` -- idem
- [x] `app/src/main/java/com/agendamedica/app/ui/doctor/MinhaAgendaScreen.kt` -- botão "Sair" no cabeçalho, `onLogout` coletado via `LaunchedEffect`
- [x] `app/src/main/java/com/agendamedica/app/ui/patient/MinhasConsultasScreen.kt` -- idem
- [x] `app/src/main/java/com/agendamedica/app/ui/navigation/AgendaMedicaNavHost.kt` -- `onLogout` wired para `goToLogin(passwordChanged = false)` nas duas rotas
- [x] `app/src/test/java/com/agendamedica/app/ui/doctor/MinhaAgendaViewModelTest.kt`, `app/src/test/java/com/agendamedica/app/ui/patient/MinhasConsultasViewModelTest.kt` -- teste de `logout()`: `signOut()` chamado, evento de navegação emitido

**Acceptance Criteria:**
- Given que estou autenticado em Minha Agenda (Médico) ou Minhas Consultas (Paciente), when toco em "Sair", then minha sessão é encerrada e volto ao Login, sem conseguir voltar à tela anterior pelo botão voltar

## Implementation Notes

Implementado exatamente conforme o Code Map e as Design Notes. `MinhaAgendaViewModel` e `MinhasConsultasViewModel` ganharam `authRepository`, `navigateToLogin` (`SharedFlow<Unit>`) e `logout()` idênticos ao padrão já usado por `NovaSenhaViewModel`. Ambas as telas ganharam o cabeçalho em `Row` com o botão "Sair" e um `LaunchedEffect` que coleta `navigateToLogin` e chama `onLogout`; `AgendaMedicaNavHost` liga `onLogout` ao `goToLogin(passwordChanged = false)` já existente nas duas rotas. `./gradlew assembleDebug testDebugUnitTest` -> `BUILD SUCCESSFUL` (verificado tanto pelo subagente de implementação quanto, de forma independente, pelo orquestrador antes e depois da revisão).

## Spec Change Log

## Review Triage Log

Revisão com 3 subagentes paralelos (correção/segurança, cobertura de testes, escopo/consistência) sobre o diff real (não sobre o relatório do subagente de implementação).

| # | Achado | Origem | Severidade | Decisão |
|---|--------|--------|------------|---------|
| 1 | Faltava teste cobrindo a linha do I/O Matrix "Sair com a lista ainda carregando ou com erro" | Cobertura de testes | Real (spec explicitamente lista esse cenário) | Corrigido: adicionado `logout works while the appointment list is still loading or in error` em ambos os arquivos de teste |
| 2 | Faltava teste cobrindo `signOut()` retornando falha (a spec exige explicitamente "não checar o Result", ou seja, logout deve navegar mesmo assim) | Cobertura de testes | Real (regra explícita da spec sem cobertura) | Corrigido: adicionado `logout navigates even when signOut fails` em ambos os arquivos |
| 3 | Nenhum teste confirmava que fluxos não relacionados ao logout NÃO chamam `signOut()` (paridade com o rigor já usado em `LoginViewModelTest`) | Cobertura de testes | Menor (assimetria com padrão já estabelecido) | Corrigido: adicionado `coVerify(exactly = 0) { authRepository.signOut() }` em um teste de carregamento normal por arquivo |
| 4 | `LaunchedEffect(viewModel)` nas telas em vez do `LaunchedEffect(Unit)` mostrado nas Design Notes | Correção/segurança | Não é bug — chave mais explícita e igualmente segura (sem coleta dupla ou perdida) | Aceito sem alteração |
| 5 | Ordem `signOut()` -> `emit()` não é verificada explicitamente por `verifyOrder` nos novos testes | Cobertura de testes | Muito menor — a implementação já garante a ordem por ser sequencial dentro do mesmo `launch`; risco de regressão silenciosa é baixo | Aceito sem alteração (não vale o ruído de mais um teste) |

Nenhum problema de escopo, nomenclatura ou "Never"-boundary encontrado pelos 3 revisores.

## Design Notes

**Padrão do `logout()` no ViewModel** (idêntico nos dois arquivos, só o nome do repositório de dados muda):
```kotlin
private val _navigateToLogin = MutableSharedFlow<Unit>()
val navigateToLogin: SharedFlow<Unit> = _navigateToLogin.asSharedFlow()

fun logout() {
    viewModelScope.launch {
        authRepository.signOut()
        _navigateToLogin.emit(Unit)
    }
}
```

**Na tela** (`MinhaAgendaScreen`/`MinhasConsultasScreen`), junto dos outros `LaunchedEffect` já existentes:
```kotlin
LaunchedEffect(Unit) {
    viewModel.navigateToLogin.collect { onLogout() }
}
```
E o cabeçalho vira uma `Row` com o título à esquerda e o botão à direita:
```kotlin
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
) {
    Text("Minha Agenda", style = MaterialTheme.typography.titleLarge, color = AgendaMedicaColors.inkPrimary)
    TextButton(onClick = viewModel::logout) {
        Text("Sair", color = AgendaMedicaColors.accentPrimary)
    }
}
```
Em `MinhasConsultasScreen`, o botão voltar (`AccessibleIconButton`) continua numa linha própria acima dessa `Row` — não faz parte dela.

## Verification

**Commands:**
- `./gradlew assembleDebug testDebugUnitTest` (com `JAVA_HOME` do JBR do Android Studio) -- expected: `BUILD SUCCESSFUL`, todos os testes passam

**Manual checks (if no CLI):**
- No celular: logar como médico, tocar "Sair" em Minha Agenda, conferir que volta ao Login e o botão voltar do sistema sai do app; repetir como paciente em Minhas Consultas
