---
title: 'Usuário recupera a senha esquecida'
type: 'feature'
created: '2026-09-21'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: 'cd61e91a9f5c29459e905aa76dfa8be5ed99f63b'
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Paciente ou Médico que esquece a senha não tem como voltar à conta sem suporte; o link "Esqueci minha senha" do Login ainda não faz nada.

**Approach:** Fluxo nativo do Supabase Auth, sem função nova: tela Recuperar Senha pede o e-mail; o link do e-mail abre o app por deep link (`agendamedica://reset-password`, fluxo implícito) numa tela Nova Senha; depois o usuário volta ao Login e entra com a nova senha.

## Boundaries & Constraints

**Always:**
- A confirmação é sempre neutra ("Se o e-mail informado existir, enviamos um link para redefinir a senha.") e nunca revela se a conta existe (FR11): mesma mensagem para e-mail cadastrado, inexistente e para qualquer rejeição do servidor, exceto limite de envio (`over_email_send_rate_limit`/`over_request_rate_limit` -> "Muitas tentativas. Aguarde alguns minutos e tente novamente."). Falha de rede/inesperada -> mensagem genérica (AD-9).
- Tratar o deep link antes de importar a sessão: fragmento com `error`/`error_code` (link expirado ou já usado) nunca chama `handleDeeplinks`; leva a Recuperar Senha com "Este link expirou ou já foi usado. Solicite um novo link." (prazo padrão do Supabase Auth).
- Só `type=recovery` com `access_token` abre Nova Senha. Nova senha ≥ 6 caracteres, campo único; botão desabilitado até válida, sem erro inline antes do envio.
- Após salvar, encerrar a sessão de recuperação e ir ao Login com o aviso "Senha redefinida. Entre com a nova senha."; sair de Nova Senha sem salvar também encerra essa sessão (nunca deixar o usuário autenticado sem ter feito login).
- Só o `data/` fala com o Supabase (AD-2); e-mail no botão "Enviar link de recuperação" com validação igual à do cadastro; alvos ≥48dp, ícones com `contentDescription`.

**Never:**
- Não criar Edge Function nem migração. Não implementar redefinição por código/OTP nem PKCE. Não hardcodar URL/chave.
- Não alterar Story 1.1/1.2 além do necessário para o link e o aviso no Login.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| E-mail cadastrado | e-mail de conta existente | Mensagem neutra + botão "Voltar ao login"; e-mail enviado | N/A |
| E-mail inexistente | e-mail sem conta | Exatamente a mesma mensagem neutra | N/A |
| E-mail vazio/inválido | campo vazio ou malformado | "Enviar link de recuperação" desabilitado | N/A |
| Servidor rejeita o pedido | erro HTTP do Auth (ex.: destinatário não autorizado pelo SMTP embutido) | Mesma mensagem neutra | Não expor detalhe |
| Limite de envio | 429 / `over_*_rate_limit` | "Muitas tentativas. Aguarde..." | N/A |
| Falha de rede | Supabase inacessível | Mensagem genérica "tente novamente", sem crash | Bucket `UNEXPECTED` |
| Link válido | `#access_token=...&type=recovery` | Abre Nova Senha; senha salva -> Login com aviso; senha antiga passa a ser negada, só a nova entra | N/A |
| Nova senha inválida | < 6 caracteres | "Salvar nova senha" desabilitado | N/A |
| Servidor recusa a nova senha | ex.: igual à anterior | Mensagem genérica, permanece na tela | Sem detalhe técnico |
| Link expirado/já usado | `#error=access_denied&error_code=otp_expired...` | Recuperar Senha com mensagem clara pedindo novo link | Sem crash |

</frozen-after-approval>

## Code Map

- `app/.../data/remote/SupabaseClientProvider.kt` -- `install(Auth)` sem scheme/host; adicionar `scheme = "agendamedica"`, `host = "reset-password"` (fluxo IMPLICIT, o padrão)
- `app/.../data/repository/AuthRepository.kt` -- adicionar `requestPasswordReset`, `updatePassword`, tratamento do deep link; `signOut()` já existe. supabase-kt: `auth.resetPasswordForEmail`, `auth.updateUser { password = }`, `handleDeeplinks(intent)` (lança em fragmento de erro, por isso o parse prévio); `AuthRestException.errorCode` (`AuthErrorCode.OverEmailSendRateLimit`)
- `app/.../data/repository/AppError.kt` -- `toAppError()` já lê `RestException`; reutilizar
- `app/.../MainActivity.kt` + `AndroidManifest.xml` -- `launchMode="singleTop"`, intent-filter VIEW/BROWSABLE para `agendamedica://reset-password`, entrega o intent (`onCreate`/`onNewIntent`) ao NavHost
- `app/.../ui/navigation/AgendaMedicaNavHost.kt` -- rotas `recuperar_senha` e `nova_senha`; navegação disparada pelo deep link
- `app/.../ui/auth/LoginScreen.kt` (+ `LoginViewModel`) -- ligar "Esqueci minha senha" (hoje `onClick = {}` com comentário da 1.3); exibir aviso de senha redefinida
- `app/.../ui/patient/CadastroPacienteViewModel.kt` -- padrão de ViewModel, `EMAIL_PATTERN`
- `app/.../ui/components/Components.kt` -- `PrimaryButton`, `LabeledTextField`, `AccessibleIconButton`
- `supabase/config.toml` -- `additional_redirect_urls` (linha 30, vazia)
- `app/src/test/.../ui/patient/CadastroPacienteViewModelTest.kt` -- padrão de teste (um `UnconfinedTestDispatcher` compartilhado em `setMain` e `runTest`)

## Tasks & Acceptance

**Execution:**
- [x] `data/remote/SupabaseClientProvider.kt`, `AndroidManifest.xml`, `MainActivity.kt`, `supabase/config.toml` -- scheme/host do deep link, intent-filter, `singleTop`, `onNewIntent`, `additional_redirect_urls = ["agendamedica://reset-password"]` -- FR11
- [x] `data/repository/AuthRepository.kt` -- `requestPasswordReset`, `updatePassword`, classificação do deep link (função pura sobre o fragmento: pronto / expirado / ignorar) e importação da sessão só quando `type=recovery` com token
- [x] `ui/auth/{RecuperarSenhaScreen,RecuperarSenhaViewModel}.kt` -- e-mail, neutra/limite/genérica conforme a matriz, "Voltar ao login", banner de link expirado
- [x] `ui/auth/{NovaSenhaScreen,NovaSenhaViewModel}.kt` -- senha única, salvar -> `signOut` -> Login com aviso; voltar = `signOut`
- [x] `ui/auth/LoginScreen.kt`, `LoginViewModel.kt`, `AgendaMedicaNavHost.kt` -- "Esqueci minha senha", aviso de sucesso, rotas
- [x] `supabase/templates/recovery.html` + `config.toml` -- e-mail de recuperação em pt-BR (assunto/corpo com `{{ .ConfirmationURL }}`), versionado para colar no painel
- [x] `app/src/test/...` -- ViewModels (Recuperar: habilitação, neutra em sucesso e rejeição, limite, rede; Nova Senha: habilitação, sucesso -> `signOut` + navega, falha genérica, voltar -> `signOut`) e classificação do deep link (válido/expirado/ignorado)

**Acceptance Criteria:**
- Given o Login, when o usuário toca "Esqueci minha senha" e envia um e-mail, then vê a mesma confirmação neutra exista ou não a conta (FR11)
- Given o link de recuperação recebido, when é aberto no celular, then o app abre em Nova Senha e a senha nova passa a valer e a antiga deixa de valer
- Given um link expirado, when é aberto, then o app mostra a mensagem pedindo um novo link
- Given qualquer erro inesperado, when ocorre, then a mensagem é genérica (AD-9)

## Implementation Notes

**2026-09-21 — implementação concluída.** Deep link (`SupabaseClientProvider`, manifest com `singleTop`, `MainActivity` → NavHost), `AuthRepository` (`requestPasswordReset`, `updatePassword`, `handleRecoveryLink`, classificação pura do fragmento), telas Recuperar Senha e Nova Senha, aviso no Login, e-mail pt-BR versionado em `supabase/templates/recovery.html` (+ `config.toml`).

- **Ajustes na verificação do diff:** (1) o NavHost passou a ter a rota de Login com argumento (`login?senhaRedefinida=`), e os `popUpTo(Routes.LOGIN)` das Stories 1.1/1.2 deixariam de casar com ela (não limpariam a pilha) — trocados por `popUpTo(navController.graph.id)`; (2) `handleDeeplinks` importa a sessão numa corrotina solta (Nova Senha poderia abrir antes da sessão e uma falha de rede escaparia como exceção não tratada) — `handleRecoveryLink` agora faz o parse do fragmento e chama `auth.importAuthToken(..., retrieveUser = true)` (suspend, com falha capturável); (3) a classificação de erro do pedido foi extraída para `passwordResetOutcomeFor` para poder ser testada.
- **Auditoria da matriz:** linhas cobertas por testes — e-mail vazio/inválido, cadastrado/inexistente/rejeição (neutra), limite de envio, falha de rede, nova senha inválida/recusada, link expirado (classificador) e voltar sem salvar. Só verificáveis no aparelho: "Link válido" ponta a ponta (importação real da sessão + `updateUser`) e o e-mail real.
- `./gradlew assembleDebug testDebugUnitTest`: BUILD SUCCESSFUL, 49 testes, 0 falhas. Nenhum deploy/migração nesta história.

**2026-09-21 — patches do gate de revisão (ver Review Triage Log).** Erro 5xx do Auth agora vira falha genérica em vez de "e-mail enviado"; a decisão sobre o link virou a função pura `classifyRecoveryLink` (scheme/host, erro no fragmento ou na query, decodificação única via `encodedFragment`) com testes; teste do aviso "Senha redefinida" no Login. Resultado final: BUILD SUCCESSFUL, 54 testes, 0 falhas. Falta a verificação no aparelho (fluxo real com e-mail).
## Spec Change Log

## Review Triage Log

Três revisores; achados verificados contra o código real. Nenhum `intent_gap`/`bad_spec`; sem loopback.

| # | Achado | Veredito | Evidência | Rota |
|---|--------|----------|-----------|------|
| 1 | 5xx/indisponibilidade do Auth vira "e-mail enviado" (Neutral) | `medium` | `passwordResetOutcomeFor` mapeava todo `RestException` não-429 para Neutral; erro de servidor não depende da existência da conta, então mostrá-lo não viola FR11. | **patch** — `statusCode >= 500` -> `Failed` + teste |
| 2 | Erro de link expirado só lido no fragmento; leitura dupla de decodificação (`Uri.fragment` já decodificado + `URLDecoder`) | `low` | Se o Auth mandar `?error=` na query o link expirado seria ignorado em silêncio (AC 4); a segunda decodificação corrompe `+`/`%` de valores. Correção direta. | **patch** — `classifyRecoveryLink` (pura) lê fragmento e query, usa `encodedFragment`; testes |
| 3 | VG: `handleRecoveryLink` (guards de scheme/host, EXPIRED) sem teste | `medium` | Só o parser de fragmento era testado. | **patch** — lógica de decisão extraída (`classifyRecoveryLink`) e testada; a importação da sessão (READY) permanece verificada só no aparelho |
| 4 | VG: aviso "Senha redefinida" no Login sem teste | `low` | `showPasswordResetNotice`/limpeza no `submit` sem cobertura. | **patch** — teste no `LoginViewModelTest` |
| 5 | Sessão de recuperação pode persistir (kill do app na tela, `signOut` com falha, escopo não global) | `low` | Confirmado: `signOut` falho/kill mantém a sessão salva; hoje o app sempre abre no Login e nenhuma rota restaura sessão, então não há acesso sem senha. Vira relevante quando existir restauração de sessão. | rejeitado (baixo, sem efeito visível hoje); registrado em `deferred-work.md` como aviso para quando houver restauração de sessão |
| 6 | Deep link `agendamedica://` (esquema customizado) pode ser interceptado por outro app; fluxo implícito põe tokens no fragmento | `medium` | Real em teoria, mas a correção (PKCE/App Links verificados) contraria o "Never" da spec (sem PKCE) — rejeitar achado cuja correção é editar a spec. Risco aceito num app de portfólio. | rejeitado |
| 7 | Sem teste de `requestPasswordReset`/`updatePassword` no repositório, da importação real da sessão e da navegação/`MainActivity` | `medium` | Exige cliente Auth real/mock de extensão e infraestrutura de UI test inexistente; mapeamento coberto pela função pura; fluxo real fica no teste manual. | **defer** |
| 8 | Duplo toque duplica pedido; evento de navegação perdido em rotação; replay do `SharedFlow` reentrega intent | `false`/`low` | `viewModelScope` em `Main.immediate` grava `isLoading` antes da 1ª suspensão; `MainActivity` só emite com `savedInstanceState == null` e a Activity recriada ganha fluxo novo; padrão de eventos igual ao das Stories 1.1/1.2. | rejeitado |
| 9 | `importAuthToken` sem rede reporta "link expirou"; refresh token ausente; deep link recebido com sessão ativa; aviso persiste ao trocar aba; coletor sem try/catch | `low` | Exceções da importação já são capturadas; demais são cosméticos/improváveis e exigem novo tipo de resultado ou guards. | rejeitado |
| 10 | Config do projeto hospedado / `content_path` / e-mail só funciona no celular com o app / template sem estilo / limite mínimo de senha / acessibilidade (live region, teclado de e-mail, `strings.xml`) / `AuthRepository()` no NavHost | `false`/`low` | Redirect URL já configurada pelo usuário; `content_path` segue o exemplo da documentação do CLI; demais fora do escopo da spec ou cosméticos. | rejeitado |

## Design Notes

Pré-requisito manual do usuário (o `supabase config push` sobrescreveria configurações do projeto hospedado, então não é usado): no painel, Authentication -> URL Configuration -> Redirect URLs, adicionar `agendamedica://reset-password`; opcionalmente colar `supabase/templates/recovery.html` em Authentication -> Email Templates -> Reset Password. O SMTP embutido do Supabase só envia para membros da organização e tem limite baixo por hora: para testar, cadastre uma conta com seu e-mail real.

## Verification

**Commands:**
- `./gradlew assembleDebug testDebugUnitTest` (com `JAVA_HOME` do JBR do Android Studio) -- expected: `BUILD SUCCESSFUL`, todos os testes passam
- `npx supabase` só se `config.toml` mudar algo aplicável; nenhum deploy previsto

**Manual checks (if no CLI):**
- No celular, após o pré-requisito: Login -> Esqueci minha senha -> e-mail real -> abrir o link no e-mail -> app abre em Nova Senha -> salvar -> Login com aviso -> senha antiga falha, nova entra; abrir o mesmo link de novo -> mensagem de link expirado/usado
