---
title: 'Médico se cadastra e configura seu perfil profissional'
type: 'feature'
created: '2026-09-17'
status: 'ready-for-dev'
route: 'dispatch'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Não existe app nem backend ainda. Um Médico precisa poder se autocadastrar (sem validação de CRM), definir especialidade/convênios/agenda semanal, e ficar visível na busca imediatamente — o primeiro pedaço fatiável do produto.

**Approach:** Bootstrap do projeto Android (Kotlin/Compose, MVVM) e do projeto Supabase (schema + Edge Function de cadastro), implementando a tela de Login (base, reutilizada por todas as histórias futuras), Escolha de papel, Cadastro de Médico e Minha Agenda (estado vazio).

## Boundaries & Constraints

**Always:**
- Papel (`doctor`) é fixado só pela Edge Function `register-doctor`, nunca por um campo do payload do cliente (AD-6).
- `specialty`/`insurances` do médico ficam imutáveis após a criação, via trigger `enforce_doctor_immutable_fields` (AD-11); `doctor_schedules` é livremente editável.
- Especialidade (6 fixas) e Convênio (4 fixos, incluindo "Particular") são constantes no código (app e Edge Function), não uma tabela (Consistency Conventions da Arquitetura) — valores em português, idênticos ao Glossário do PRD.
- Nomes de tabela/coluna/função em inglês `snake_case`; toda função `SECURITY DEFINER` fixa `SET search_path = public, pg_temp` (AD-1, AD-7).
- `Repository` é o único ponto de acesso ao `supabase-kt`; nenhuma tela/ViewModel chama o cliente Supabase diretamente (AD-2).
- Erros de negócio seguem o vocabulário `CONFLICT:`/`INVALID:`/`FORBIDDEN:`, com um bucket `UNEXPECTED` no app para o resto (AD-9).
- Tokens visuais (cores, tipografia Inter, formas) vêm de `DESIGN.md` — ver Design Notes.
- Botões de ícone (voltar) têm rótulo acessível; alvos de toque ≥48dp (UX-DR7).

**Never:**
- Não implementar cadastro de Paciente (História 1.2) nem "Esqueci minha senha" (História 1.3) — o link pode existir na tela de Login sem estar funcional ainda.
- Não criar `appointments`, `booked_slots` nem qualquer coisa do Épico 2.
- Não permitir edição de Especialidade/Convênio pela UI depois de criado o perfil.
- Não hardcodar URL/chave do Supabase no código-fonte — vêm de configuração local (`local.properties`/`BuildConfig`), nunca versionadas.

**Decisão (Open Question resolvida):** Ambiente de desenvolvimento é o **Supabase local** (`supabase start`, stack via Docker) — sem criação de conta/projeto na nuvem nesta história. O app aponta para a URL local (`10.0.2.2` no emulador, ou IP da máquina na rede local para dispositivo físico). Migração para um projeto na nuvem fica para quando o usuário quiser instalar fora da rede local — não é escopo desta história.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Autocadastro válido | nome, e-mail, senha válidos + "Sou médico" | Conta criada, autenticado, sem etapa de aprovação | N/A |
| Especialidade não selecionada | formulário sem especialidade | Botão "Criar perfil" desabilitado | N/A |
| Convênio não selecionado | nenhum convênio marcado | Botão "Criar perfil" desabilitado (mín. 1) | N/A |
| Especialidade fora da lista fixa | payload direto à API com valor inválido | Rejeitado pela função com `INVALID:` | Mensagem genérica no app |
| Tentativa de editar especialidade/convênio pós-criação | `UPDATE` direto na API | Rejeitado pela trigger `enforce_doctor_immutable_fields` | `CONFLICT:` tratado como erro genérico |
| Login com credenciais corretas | e-mail/senha de conta existente | Autenticado, cai em Minha Agenda | N/A |
| Login com credenciais erradas | e-mail/senha inválidos | Mensagem de erro visível, sem detalhe técnico | Erro `UNEXPECTED` genérico |
| Falha de rede durante cadastro/login | Supabase inacessível | Mensagem genérica "tente novamente", sem crash | Bucket `UNEXPECTED` |

</frozen-after-approval>

## Code Map

*Projeto greenfield — nada existe ainda. Lista abaixo é o que esta história cria (não um mapa de código existente).*

- `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts` -- bootstrap Android: Kotlin 2.4.20, Compose BOM 2026.08.00, Compose Compiler plugin 2.4.20, `supabase-kt` (auth-kt, postgrest-kt, realtime-kt, functions-kt), minSdk 26
- `app/src/main/java/.../ui/theme/{Color,Type,Shape,Theme}.kt` -- tokens de `DESIGN.md`
- `app/src/main/java/.../data/remote/SupabaseClient.kt` -- único ponto de inicialização do cliente Supabase
- `app/src/main/java/.../data/repository/AuthRepository.kt` -- signUp/signIn/sessão via `auth-kt`
- `app/src/main/java/.../data/repository/DoctorRepository.kt` -- chama a Edge Function `register-doctor`; lê o próprio perfil
- `app/src/main/java/.../domain/model/{Especialidade,Convenio}.kt` -- enums fixos em português
- `app/src/main/java/.../ui/auth/{LoginScreen,LoginViewModel}.kt` -- tabs Paciente/Médico, submit real
- `app/src/main/java/.../ui/auth/EscolhaScreen.kt` -- "Sou paciente" / "Sou médico"
- `app/src/main/java/.../ui/doctor/{CadastroMedicoScreen,CadastroMedicoViewModel}.kt` -- formulário completo
- `app/src/main/java/.../ui/doctor/MinhaAgendaScreen.kt` -- estado vazio
- `supabase/migrations/0001_init.sql` -- `profiles`, `doctors`, `doctor_schedules`; trigger `enforce_doctor_immutable_fields`; RLS
- `supabase/functions/register-doctor/index.ts` -- Auth Admin API + `complete_registration` transacional

## Tasks & Acceptance

**Execution:**
- [ ] `settings.gradle.kts`/`build.gradle.kts`/`app/build.gradle.kts` -- criar projeto Android com o Stack da Arquitetura -- base de tudo
- [ ] `ui/theme/*` -- tema Compose a partir de `DESIGN.md` -- UX-DR1
- [ ] `supabase/migrations/0001_init.sql` -- schema + trigger + RLS -- AD-6, AD-7, AD-11
- [ ] `supabase/functions/register-doctor/index.ts` -- cadastro transacional -- AD-6
- [ ] `data/remote/SupabaseClient.kt`, `data/repository/{Auth,Doctor}Repository.kt` -- camada única de acesso -- AD-2
- [ ] `domain/model/{Especialidade,Convenio}.kt` -- constantes fixas -- Glossário PRD
- [ ] `ui/auth/{LoginScreen,EscolhaScreen}` -- entrada do app -- EXPERIENCE.md
- [ ] `ui/doctor/{CadastroMedicoScreen,MinhaAgendaScreen}` -- fluxo do médico -- FR1, FR2

**Acceptance Criteria:**
- Given um médico não cadastrado, when ele completa "Sou médico" + formulário válido, then a conta é criada, ele é autenticado automaticamente e cai em Minha Agenda vazia (FR1, FR2)
- Given especialidade/convênio já definidos, when uma tentativa de alteração ocorre (UI ou API direta), then é bloqueada pela trigger, nunca pela UI sozinha (FR2, AD-11)
- Given um médico já cadastrado, when ele faz login com e-mail/senha corretos, then é autenticado e cai em Minha Agenda
- Given qualquer erro de rede/validação, when ele ocorre, then o app mostra mensagem genérica, nunca o erro técnico bruto (AD-9)

## Implementation Notes

## Spec Change Log

## Review Triage Log

## Design Notes

Tokens de `DESIGN.md` a aplicar no tema Compose (não redefinir aqui — só apontar):
- Cores: `surface-canvas` `oklch(0.97 0.015 150)`, `accent-primary` `#3B6FE0`, famílias `success`/`warning`/`danger`.
- Tipografia: Inter, pesos 400-700, escala 11-19px.
- Formas: `pill` 100px, `lg` 14px, `md` 10-12px, `circle` 50%.

Convênios fixos (ordem sugerida): Unimed, Amil, Bradesco, Particular. Especialidades fixas: Cardiologia, Dermatologia, Pediatria, Ortopedia, Clínico Geral, Ginecologia.

## Verification

**Commands:**
- `supabase start` -- expected: stack local sobe sem erro (Postgres, Auth, Studio)
- `supabase db push` (ou aplicar `0001_init.sql` localmente) -- expected: migração aplica sem erro; índice/trigger criados
- `./gradlew assembleDebug` -- expected: build do app sem erro
- Cadastro manual de um médico via app (emulador/dispositivo) -- expected: perfil aparece em `doctors` com `specialty`/`insurances` corretos; tentativa de `UPDATE` direto nesses campos via Studio SQL falha com a trigger

**Manual checks (if no CLI):**
- Conferir visualmente que Login/Escolha/Cadastro Médico/Minha Agenda seguem os tokens de `DESIGN.md` (cores, tipografia, formas)
