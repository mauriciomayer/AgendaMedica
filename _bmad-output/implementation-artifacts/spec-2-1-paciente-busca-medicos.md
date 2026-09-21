---
title: 'Paciente busca médicos por especialidade e localização'
type: 'feature'
created: '2026-09-21'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: '0de39564d7f9f1f12f5e9ffa9459d1fda1414c3d'
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** O Paciente cai numa Busca vazia e o médico não guarda onde atende, então não há como filtrar por especialidade, por região nem ordenar por distância (RF-4).

**Approach:** O médico passa a escolher uma localização de uma lista fixa (cidade/bairro com coordenadas) no cadastro. A Busca lista médicos com filtro de Especialidade; com GPS ordena por distância, sem GPS filtra por cidade/bairro digitado e ordena por nome.

## Boundaries & Constraints

**Always:**
- Lista fixa de localidades (constante versionada no app e na Edge Function, como Especialidade/Convênio): São Paulo-SP (Centro, Pinheiros, Moema, Vila Mariana, Itaim Bibi), Campinas-SP (Centro, Cambuí), Santos-SP (Gonzaga, Boqueirão), cada uma com latitude/longitude aproximadas. Rótulo exibido: "Bairro, Cidade - UF".
- O servidor grava `city`, `neighborhood`, `latitude`, `longitude` a partir da própria lista; o cliente só envia o rótulo, nunca coordenadas (AD-1). Localização fora da lista -> `INVALID:` (AD-9).
- Cadastro de Médico exige a localização (botão "Criar perfil" desabilitado sem ela). Médicos já existentes recebem "Centro, São Paulo - SP" na migração.
- Só `data/` fala com o Supabase (AD-2); a busca lê `doctors` direto via RLS (sem função nova) e filtra Especialidade no servidor; região e ordenação (distância Haversine, ou nome) são lógica de domínio pura e testada.
- Com GPS ativo o campo de texto mostra "Minha localização"; digitar nele volta ao modo manual. Filtro de região sem acento/caixa, por "contém" em cidade ou bairro.
- Carregamento nunca é tela em branco (indicador); falha de rede/servidor -> mensagem genérica com "Tentar novamente", nunca texto técnico (UX-DR10, AD-9). Alvos ≥48dp; botão de GPS com `contentDescription` "Usar minha localização".

**Never:**
- Não implementar Detalhe do Médico, horários, agendamento nem Minhas Consultas; o card não é clicável nesta história.
- Não editar `0001`/`0002`; mudanças só em nova migração `0003`. Não usar Play Services nem geocodificação/API de mapas.
- Não tornar a localização imutável nem editável pela UI depois do cadastro.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Busca sem filtros | abrir Busca, sem GPS/texto | Todos os médicos, ordem alfabética por nome | N/A |
| Filtro de Especialidade | escolher "Cardiologia" | Só médicos dessa especialidade ("Todas" limpa o filtro) | N/A |
| GPS concedido | toque em "Usar minha localização" e permissão dada | Resultados por distância crescente (empate: nome); campo mostra "Minha localização" | N/A |
| Permissão negada | usuário nega | Mensagem pedindo busca manual; busca manual segue disponível | Sem crash |
| GPS sem posição | localização desligada/sem fix | "Não foi possível obter sua localização. Busque por cidade ou bairro." | Sem crash |
| Busca manual | texto "pinheiros" ou "campinas" | Só médicos dessa região, ordenados por nome | N/A |
| Sem resultados | filtros sem correspondência | "Nenhum médico encontrado com esses filtros." | N/A |
| Carregando | lista ainda não chegou | Indicador de carregamento | N/A |
| Falha de rede/servidor | Supabase inacessível | Mensagem genérica + "Tentar novamente" | Bucket `UNEXPECTED` |
| Card do médico | resultado exibido | Iniciais, nome, especialidade, cidade e chips de convênio | N/A |
| Cadastro com localização | médico escolhe localização válida | Grava cidade/bairro/coordenadas vindos do servidor | N/A |
| Localização inválida | payload direto com valor fora da lista | Rejeitado pela função | `INVALID:` -> mensagem genérica |

</frozen-after-approval>

## Code Map

- `supabase/migrations/0002_patients.sql` -- `complete_registration()` atual (mesma assinatura da 0001); NÃO editar. A 0003 remove a assinatura antiga e recria com parâmetros de localização (default `null`; `register-patient` chama por nome e segue funcionando), refaz `revoke`/`grant` só a `service_role`
- `supabase/functions/register-doctor/index.ts` -- validação, `createUser`, RPC; ganha lista de localidades, campo `location`, envio de cidade/bairro/coordenadas ao RPC
- `app/.../domain/model/{Especialidade,Convenio}.kt` -- padrão de constante fixa (`label`, `fromLabel`); criar `Localizacao.kt` igual
- `app/.../data/repository/DoctorRepository.kt` -- `registerDoctor` (adicionar `location` ao `RegisterDoctorRequest`), `getMyProfile`; adicionar a leitura da busca via Postgrest
- `app/.../data/repository/AppError.kt` -- reutilizar `toAppError()/toUserMessage()`
- `app/.../ui/doctor/{CadastroMedicoViewModel,CadastroMedicoScreen}.kt` -- adicionar dropdown "Localização" (padrão de `EspecialidadeDropdown`) e exigi-la em `isSubmitEnabled`
- `app/.../ui/patient/BuscaScreen.kt` -- hoje casca ("A busca de médicos chega em breve."); vira a tela real, com ViewModel
- `app/.../ui/navigation/AgendaMedicaNavHost.kt` -- rota `busca` já existe
- `app/.../ui/components/Components.kt` -- `TagChip`, `LabeledTextField`, `AccessibleIconButton`, `PrimaryButton`
- `app/src/main/AndroidManifest.xml` -- permissões de localização (coarse/fine)
- `app/src/test/.../ui/doctor/CadastroMedicoViewModelTest.kt`, `ui/patient/CadastroPacienteViewModelTest.kt` -- padrão de teste (um `UnconfinedTestDispatcher` compartilhado em `setMain` e `runTest`)

## Tasks & Acceptance

**Execution:**
- [x] `supabase/migrations/0003_doctor_location.sql` -- colunas `city`, `neighborhood`, `latitude`, `longitude` em `doctors` (backfill dos existentes com Centro/São Paulo, depois `not null`); nova `complete_registration` com localização validada contra a lista -- AD-1, AD-6
- [x] `supabase/functions/register-doctor/index.ts` -- lista fixa, `location` obrigatório e validado, coordenadas resolvidas no servidor -- AD-1
- [x] `domain/model/Localizacao.kt` + `domain/search/DoctorSearch.kt` -- lista fixa com coordenadas; Haversine, filtro de região (sem acento/caixa) e ordenações como funções puras
- [x] `data/repository/DoctorRepository.kt` -- `location` no cadastro; `searchDoctors(especialidade?)` via Postgrest devolvendo o resumo do médico
- [x] `data/location/LocationProvider.kt` -- posição atual via `LocationManagerCompat` (androidx.core), sem Play Services; `AndroidManifest.xml` com as permissões
- [x] `ui/doctor/{CadastroMedicoScreen,CadastroMedicoViewModel}.kt` -- campo "Localização" obrigatório
- [x] `ui/patient/{BuscaScreen,BuscaViewModel}.kt` -- filtro de Especialidade (opção "Todas"), campo de região, botão de GPS com pedido de permissão, cards, estados de carregamento/vazio/erro com "Tentar novamente"
- [x] `app/src/test/...` -- `DoctorSearchTest` (Haversine, ordem por distância e por nome, região sem acento), `BuscaViewModelTest` (cada linha da matriz), ajuste do `CadastroMedicoViewModelTest`

**Acceptance Criteria:**
- Given um Paciente autenticado na Busca, when escolhe uma Especialidade, then só vê médicos dela (FR4)
- Given GPS ativo e permitido, when a busca roda, then os médicos aparecem por distância crescente; sem GPS, o filtro manual por região ordena por nome (FR4)
- Given qualquer resultado, when o card aparece, then mostra nome, especialidade, cidade e chips de convênio
- Given carregamento ou falha, when ocorrem, then há indicador ou mensagem genérica com nova tentativa, nunca tela em branco nem erro bruto

## Implementation Notes

**2026-09-21 — implementação concluída.** Migração `0003_doctor_location.sql` (colunas de localização, backfill "Centro, São Paulo - SP", `complete_registration` recriada com validação da lista), `register-doctor` com lista fixa e coordenadas resolvidas no servidor, `Localizacao` + `DoctorSearch` (Haversine, região sem acento, ordenações), `DoctorRepository.searchDoctors`, `LocationProvider` (androidx, sem Play Services), campo "Localização" no Cadastro de Médico e a Busca real (`BuscaScreen`/`BuscaViewModel`).

- **Backend hospedado (executado pelo orquestrador; o subagente não fez deploy):** `db push` (0003) antes do `functions deploy register-doctor` (a função nova depende da nova assinatura do RPC). Chamadas reais: médico em Pinheiros 201; médico em Cambuí 201; localização fora da lista 400; sem localização 400; regressão `register-patient` 201. Lendo `doctors` como paciente autenticado: os 2 médicos antigos aparecem com Centro/São Paulo (backfill), os novos com suas coordenadas, e o filtro de Especialidade no servidor devolve só Cardiologia. Usuários de teste `smoke.*@example.com` ficaram no projeto (apagar em Authentication → Users).
- **Auditoria da matriz:** todas as linhas têm teste (`DoctorSearchTest`, `BuscaViewModelTest`, `CadastroMedicoViewModelTest`), exceto a composição visual do "Card do médico" (só as iniciais têm teste, `BuscaCardTest`) e as linhas de servidor, verificadas por chamadas reais. GPS real, permissão e modo avião só no aparelho.
- `./gradlew assembleDebug testDebugUnitTest`: BUILD SUCCESSFUL, 77 testes, 0 falhas.

**2026-09-21 — patches do gate de revisão (ver Review Triage Log).** A busca cancelada por troca de filtro não pisca mais erro; um fix de GPS tardio não sobrescreve o texto digitado; `LocationProvider` só reutiliza posição de até 10 min e tenta cada provedor; novo `LocalizacaoConsistencyTest` amarra o enum, a Edge Function e o SQL. Resultado final: BUILD SUCCESSFUL, 82 testes, 0 falhas. Falta a verificação no aparelho (GPS real, permissão, modo avião).
## Spec Change Log

## Review Triage Log

Três revisores; achados verificados contra o código real. Nenhum `intent_gap`/`bad_spec`; sem loopback.

| # | Achado | Veredito | Evidência | Rota |
|---|--------|----------|-----------|------|
| 1 | `runCatching` engole `CancellationException`: a busca cancelada por troca de filtro dispara `onFailure` e pisca erro / encerra o spinner enquanto a nova busca roda | `medium` | `load()` cancela `loadJob`; o repositório devolve `Result.failure(CancellationException)` e o `onFailure` roda antes da nova resposta. | **patch** — `onFailure` ignora `CancellationException` (+ teste) |
| 2 | Fix de GPS que chega depois de o usuário digitar sobrescreve o texto e liga o modo GPS | `medium` | `onRegionChanged` não cancelava a localização em andamento. | **patch** — `locateJob` cancelado ao digitar/negar permissão; `runCatching` da localização não engole cancelamento (+ teste) |
| 3 | `LocationProvider`: última posição sem limite de idade; posição nova só pedida ao 1º provedor; `catch (RuntimeException)` engole cancelamento | `medium` | Posição de dias atrás ordenaria por lugar errado; se a rede não responde o GPS nunca é tentado. | **patch** — só reutiliza posição de até 10 min, tenta cada provedor (6 s) e propaga cancelamento; verificado só no aparelho |
| 4 | Três cópias da lista de localidades (Kotlin, TS, SQL) sem teste que as una | `medium` | Uma divergência ofereceria no dropdown um local que o servidor rejeita como `INVALID`. | **patch** — `LocalizacaoConsistencyTest` compara as três (coordenadas como número) |
| 5 | Caso "digitar um caractere em modo GPS" sem asserção | `low` | Só o caso de apagar estava coberto. | **patch** — teste adicionado |
| 6 | Sem teste do mapeamento de `searchDoctors`, de `AndroidLocationProvider` e das validações de servidor (Edge Function/RPC) | `medium` | Verificado apenas por chamadas reais (201/400, leitura como paciente) e no aparelho; exige harness de servidor/Robolectric que o projeto não tem. | **defer** |
| 7 | `mapNotNull` descarta linhas com especialidade/convênio desconhecidos sem registro; sem paginação (teto de 1000 do PostgREST) | `low` | Lista fixa dos dois lados e ~20 usuários; improvável. | rejeitado |
| 8 | Backfill fixa médicos antigos em "Centro, São Paulo - SP" | `false` | Decisão explícita do usuário (opção "localização padrão"). | rejeitado |
| 9 | `drop function` sem `if exists`; ordem de deploy; clientes antigos sem `location` | `false` | Migração já aplicada com sucesso; a função nova exige `location` de propósito e já foi publicada logo após a migração. | rejeitado |
| 10 | Igualdade exata de `double` no SQL; `findLocalidade(...)!` reconsultado; parâmetros nulos no RPC | `low` | Cadastros reais (201) validaram a igualdade; a asserção não nula segue a validação; nulos caem em `INVALID` pelo `exists`. | rejeitado |
| 11 | RLS/privacidade das novas colunas | `false` | `doctors_select_authenticated` existe desde a 0001; leitura como paciente confirmada. | rejeitado |
| 12 | Campo "Minha localização" por heurística de tamanho; sem distância no card; sem raio; aviso em cor de erro; permissões FINE+COARSE; live regions; lista some ao trocar filtro; recomputo do getter; `initialsOf` em nome vazio; sufixo "- SP" fixo | `low` | Comportamento dentro da spec ou cosmético; a correção exigiria novos controles/parâmetros. | rejeitado |

## Design Notes

A lista de localidades é cópia dupla (Kotlin e Deno), como Especialidade/Convênio: alterar as duas juntas. As coordenadas são aproximadas (centro do bairro) e servem só para ordenar. Para testar a ordenação por distância cadastre médicos em localidades diferentes.

## Verification

**Commands:**
- `./gradlew assembleDebug testDebugUnitTest` (com `JAVA_HOME` do JBR do Android Studio) -- expected: `BUILD SUCCESSFUL`, todos os testes passam
- `npx supabase db push` e `npx supabase functions deploy register-doctor --project-ref vuqvizzkdeiseyunjrms --use-api` -- expected: migração `0003` aplicada, função publicada
- Chamadas diretas a `register-doctor`: localização válida -> 201 e colunas preenchidas; fora da lista -> 400; `register-patient` continua 201

**Manual checks (if no CLI):**
- No celular: cadastrar 2-3 médicos em localidades e especialidades diferentes; como paciente, filtrar por especialidade, usar GPS (permitir e negar) e buscar "pinheiros"; conferir estados de vazio, carregamento e erro (modo avião)
