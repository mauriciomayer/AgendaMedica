# Sprint Change Proposal — Grade de horários: consulta de 30min + intervalo de 15min, dentro da janela da clínica (08h-18h)

**Data:** 2026-09-22
**Autor:** Orquestrador (Claude Code), a pedido de Mauricio
**Modo de revisão:** Em lote (v2 — corrigida após feedback do usuário sobre a Seção 4)

## 1. Resumo do problema

**Categoria:** mal-entendido do requisito original (não é falha técnica nem pivô estratégico).

O produto sempre tratou "Slot" como um intervalo de 15 minutos: a consulta em si durava 15 minutos, a grade de horários do Detalhe do Médico mostrava blocos de 15 em 15 minutos, e o médico configurava livremente seus dias e horário de início/fim de atendimento (06:00-22:00, em passos de 30 min) no cadastro. Isso está implementado, testado e commitado nas Stories 1.1, 2.2, 2.3, 2.4 e 2.5 (todas `done`/`review`).

O usuário identificou que essa premissa está errada. O modelo correto:
- Cada consulta dura **30 minutos**.
- Entre o fim de uma consulta e o início da próxima há um **intervalo de 15 minutos**.
- Logo, os horários de início possíveis ficam espaçados **45 minutos** um do outro (30 + 15), não 15.
- Exemplo dado: consulta das 8h vai das 8:00 às 8:30; intervalo até 8:45; a próxima consulta começa às 8:45 (não às 8:15).
- **A clínica funciona das 8h às 18h — essa é a janela máxima possível.** Dentro dela, **cada Médico continua escolhendo seu próprio horário de início/fim** (como já é hoje), só que agora limitado a caber dentro de 08h-18h, em vez de livre entre 06h-22h. Ex.: um médico pode escolher atender das 8h ao meio-dia; outro, das 14h às 18h — ambos válidos; um médico não pode escolher 06h-14h nem 08h-20h.

**Evidência:** mensagem direta do usuário com exemplo numérico explícito (30min+15min); correção do usuário sobre a Seção 4 original desta proposta, que tinha entendido errado que o horário viraria fixo/não-editável para todo médico — na verdade só o limite (8h-18h) é fixo, o médico continua configurando dentro dele.

## 2. Análise de impacto

### 2.1 Épicos

- **Epic 1 (concluído)** — Story 1.1 (`done`): o cadastro do médico continua com os dropdowns de horário início/fim, exatamente como hoje — só a **lista de horários oferecida** muda de "06:00 a 22:00" para "08:00 a 18:00" (mesmo passo de 30 min). Nenhuma tela nem campo é removido. A spec da 1.1 é histórica (frozen) e não será reescrita — recebe uma nota de erratum apontando para a nova story.
- **Epic 2 (maioria `review`)** — Stories 2.2 (grade de horários), 2.3 (agendar), 2.4 (paciente cancela/reagenda) e 2.5 (médico cancela/reagenda) dependem todas da granularidade do slot e/ou da validação de alinhamento no servidor. Nenhuma delas precisa ser refeita: a lógica de geração de slots (cliente) e a validação de alinhamento (servidor, função `assert_slot_bookable`, reaproveitada por `book_appointment` e `reschedule_appointment` sem precisar recriá-las) são os únicos pontos de mudança real — e ambas já eram desenhadas para ler o horário de cada bloco de `doctor_schedules` genericamente (nunca cravavam um valor fixo), então continuam corretas sem alteração adicional agora que o horário volta a ser variável por médico (só que dentro de um limite nem existia antes). Todas as 5 specs recebem a mesma nota de erratum.
- **Epic 3 e Epic 4** — sem impacto (notificação e identidade visual não dependem da granularidade do slot).
- **Novo épico necessário: Epic 5 — Grade de horários (consulta 30min + intervalo 15min, dentro da janela da clínica 08h-18h)**, com uma única story cobrindo cadastro (faixa de horário mais estreita), domínio de slots, validação no servidor/banco e documentação.

### 2.2 Conflitos de artefato

**PRD** (`_bmad-output/planning-artifacts/prds/prd-AgendaMedica-2026-09-17/prd.md`) — requer edição:
- Glossário, "Slot": `"intervalo de 15 minutos"` → consulta de 30 min + intervalo de 15 min entre uma e outra.
- Novo termo no Glossário: "Horário da clínica" — janela fixa 08h-18h dentro da qual cada Médico define seu próprio horário de início/fim.
- RF-2 (§4.1): "os dias/horários em que atende" → "os dias/horários em que atende, dentro da janela fixa da clínica (08h-18h)"; "Dias/horários definidos geram automaticamente os Slots de 15 minutos" → geram os horários de 30 min com intervalo de 15; mantém "O Médico pode editar dias/horários de atendimento a qualquer momento" (isso não muda — continua editável, só o limite é novo).
- RF-5 (§4.3): "grade é organizada em blocos de 15 minutos" → "consultas de 30 minutos, com 15 minutos de intervalo entre uma e outra".
- NFR/RNF Transversal "Fuso horário": "Slots de 15min" → "consultas de 30min com intervalo de 15min entre elas".
- JU-1 e JU-4 (exemplos narrativos): "14h30" não é mais um horário de início válido na grade de um médico que atende, por exemplo, das 8h às 18h (a grade nesse caso é 08:00, 08:45, ..., 14:00, 14:45, ...) — trocar os exemplos para "14h" nas duas jornadas, por consistência (cosmético, mas evita um exemplo que não bate com a regra).
- JU-2: "define dias da semana e faixas de horário em que atende, que o sistema converte em slots de 15 minutos" → "define dias da semana e faixas de horário em que atende, dentro do horário da clínica (08h-18h), que o sistema converte em horários de 30 minutos com 15 minutos de intervalo entre consultas". A frase "pode editar dias/horários a qualquer momento" permanece sem mudança.

**Arquitetura** (`ARCHITECTURE-SPINE.md`, AD-4) — requer edição: acrescentar a regra de duração (30min) + intervalo (15min) + o novo limite de validação (janela 08h-18h) à descrição de como a grade é derivada, mantendo o princípio central do AD-4 (nunca armazenada como linha, sempre computada, lida genericamente do bloco) intacto — é uma continuação do texto existente, não uma reversão.

**epics.md** — requer edição: FR2/FR5/NFR6 (mesmo texto do PRD, mantido em sincronia); Acceptance Criteria da Story 1.1 (a linha sobre horário de início/fim ganha a menção "dentro de 08h-18h", sem remover nada); Acceptance Criteria da Story 2.2 ("blocos de 15 minutos" → "horários de 30 minutos com 15 min de intervalo"); mais o novo Epic 5/Story 5.1 anexado ao final.

**EXPERIENCE.md** — requer edição pontual: a narrativa de Cadastro de Médico (linha "marca dias de atendimento (chips) e horário início/fim") ganha "(dentro de 08h-18h)" — sem remover o campo.

**DESIGN.md** — sem conflito direto (não fixa a granularidade do slot, só o estilo visual do botão de horário).

**Código já implementado** (Stories 1.1, 2.2-2.5):
- `app/.../ui/doctor/CadastroMedicoViewModel.kt`: `HORARIOS_DISPONIVEIS` muda de `for (hour in 6..21) {...}; add("22:00")` para `for (hour in 8..17) {...}; add("18:00")` — mesma UI, mesmos dropdowns "Início"/"Fim", lista mais estreita. Nenhuma outra mudança na tela/ViewModel.
- `supabase/functions/register-doctor/index.ts`: hoje só valida formato (`HH:mm`) e `startTime < endTime`; passa a também rejeitar (`INVALID:`) qualquer `startTime < "08:00"` ou `endTime > "18:00"` — fecha a mesma regra para uma chamada direta à API (AD-1: o servidor nunca confia só na UI).
- Nova migração `0009_...sql`:
  - `alter table doctor_schedules add constraint doctor_schedules_within_clinic_hours check (start_time >= '08:00' and end_time <= '18:00')` — a garantia final vive no banco (NFR1), não só na Edge Function. Antes de adicionar a constraint, normaliza (`UPDATE`) as linhas já existentes que ficariam fora da janela (mesmo padrão de backfill usado na Story 2.1 para localização): recorta `start_time`/`end_time` para caber em 08:00-18:00, e qualquer linha que ficaria com `start_time >= end_time` depois do recorte vira `08:00`-`18:00` (fallback seguro, só afeta dados de teste).
  - `create or replace function assert_slot_bookable(...)` com a nova regra de alinhamento (grade de 45 em 45 min a partir do `start_time` do bloco do dia daquele médico especificamente, e a consulta de 30 min tem que caber inteira antes do `end_time` daquele bloco) — continua lendo o bloco genericamente por `doctor_id`+`weekday`, exatamente como hoje, só troca a aritmética de "múltiplo de 15 min" para "múltiplo de 45 min a partir do início do bloco, com 30 min de folga até o fim". Como `book_appointment`/`reschedule_appointment` já chamam `assert_slot_bookable` pelo nome, **nenhuma das duas precisa ser recriada**.
- `app/.../domain/agenda/AgendaSlots.kt`: troca o passo de geração de 15 para 45 minutos; a condição de "cabe no bloco" muda de `posição < fim` para `posição + 30min <= fim`; novas constantes `DURACAO_CONSULTA_MINUTOS = 30`, `INTERVALO_ENTRE_CONSULTAS_MINUTOS = 15`. Este arquivo já lê o `start`/`end` de cada bloco dinamicamente (nunca assumiu 06h-22h nem nada fixo), então não precisa de nenhuma outra mudança por causa do novo limite 08h-18h.
- Nenhuma mudança em `booked_slots`, no índice único `(doctor_id, start_time)` nem no Realtime — como a grade nova garante que dois horários canônicos nunca se sobrepõem (45min de espaçamento > 30min de duração), a checagem por instante exato continua suficiente.
- `_bmad-output/test-artifacts/checklist-teste-manual.md`: a linha da Story 2.2 sobre "de 15 em 15 minutos" precisa refletir a nova grade; os exemplos de horário nos casos avançados continuam válidos desde que estejam dentro do horário configurado do médico de teste.
- `supabase/tests/*.mjs`: os scripts de concorrência/cancelar-reagendar usam horários como `saoPauloSlot(dia, hh, mm)` com `mm` variados, e os médicos de teste são cadastrados com uma agenda (ex.: `00:00`-`23:59` em alguns scripts) — essa agenda de teste passa a precisar caber em 08h-18h (a `register-doctor` vai rejeitar `00:00`/`23:59`), e os horários usados nos testes precisam cair na nova grade de 45 min a partir do início real configurado para aquele médico de teste. Isso é parte da implementação da Story 5.1, não uma mudança de escopo à parte.

### 2.3 Outros artefatos
Nenhum impacto em CI/CD, infraestrutura, monitoramento (o projeto não tem nenhum desses hoje).

## 3. Caminho recomendado

**Opção escolhida: Ajuste direto (Direct Adjustment)** — nova Story 5.1 dentro de um novo Epic 5, sem rollback de nada já commitado.

- **Rollback (Opção 2) foi descartado**: as Stories 1.1/2.2-2.5 continuam corretas em tudo que não é a granularidade do slot/limite de horário (cadastro, busca, agendamento, concorrência, cancelar/reagendar, notificação de evento) — desfazê-las jogaria fora trabalho correto só para reimplementar com um número diferente.
- **Revisão de MVP (Opção 3) não se aplica**: o MVP não muda de escopo, só corrige um parâmetro de negócio (duração da consulta/intervalo/limite da janela do dia).
- **Esforço:** baixo-médio (uma spec, ~5-6 arquivos de código, 1 migração nova, ajuste em ~4 documentos de planejamento + 5 specs antigas com nota de erratum). Menor que a v1 desta proposta, já que nenhuma tela precisa remover campo — só estreitar uma lista e apertar validações. **Risco:** baixo — o footprint no banco é uma única função recriada (`assert_slot_bookable`) mais uma `CHECK constraint` nova (com normalização prévia dos dados de teste existentes); nada de `book_appointment`/`reschedule_appointment`/`cancel_appointment` muda.

## 4. Decisão confirmada com o usuário (corrigida)

A clínica funciona das 8h às 18h — isso é fixo e vira uma `CHECK constraint` no banco. **Dentro** dessa janela, cada Médico continua escolhendo livremente seu próprio horário de início/fim por dia (exatamente como hoje), só que o intervalo oferecido no cadastro (e aceito pelo servidor) muda de 06h-22h para 08h-18h.

## 5. Propostas de mudança detalhadas

### 5.1 PRD — `prd.md`

| Local | ANTES | DEPOIS |
|---|---|---|
| Glossário, "Slot" (linha 96) | `**Slot** — intervalo de 15 minutos na Agenda de um Médico, disponível ou ocupado.` | `**Slot** — intervalo de 30 minutos (duração de uma Consulta) na Agenda de um Médico, disponível ou ocupado, sempre seguido de 15 minutos de intervalo até o próximo Slot possível.` |
| Glossário, novo termo (após "Slot") | *(não existe)* | `**Horário da clínica** — janela fixa das 8h às 18h dentro da qual todo Médico define seu próprio horário de início/fim de atendimento; nenhum Médico pode configurar um horário que comece antes das 8h ou termine depois das 18h.` |
| §4.1, descrição (linha 108) | `...define os dias/horários em que atende — que o sistema converte automaticamente em Slots de 15 minutos.` | `...define os dias/horários em que atende, dentro do horário da clínica (8h às 18h) — que o sistema converte automaticamente em Slots de 30 minutos com 15 minutos de intervalo entre um e outro.` |
| RF-2 (linha 122) | `...um ou mais Convênios (...) e os dias/horários em que atende.` | `...um ou mais Convênios (...) e os dias/horários em que atende, dentro do horário da clínica (8h às 18h — ver Glossário).` |
| RF-2, consequências (linha 128) | `Dias/horários definidos geram automaticamente os Slots de 15 minutos correspondentes na Agenda do Médico.` | `Dias/horários definidos (sempre dentro de 8h-18h) geram automaticamente os Slots de 30 minutos, com 15 min de intervalo entre eles, correspondentes na Agenda do Médico.` |
| RF-2, consequências (linha 129) | *(sem mudança)* `O Médico pode editar dias/horários de atendimento a qualquer momento...` | *(sem mudança — continua editável)* |
| RF-2, nova consequência | *(não existe)* | `O horário de início/fim escolhido pelo Médico precisa caber inteiramente dentro do horário da clínica (8h às 18h); o sistema rejeita qualquer configuração fora dessa janela.` |
| RF-5, consequências (linha 171) | `A grade é organizada em blocos de 15 minutos, refletindo os dias/horários definidos pelo Médico (RF-2).` | `A grade é organizada em Slots de 30 minutos com 15 minutos de intervalo entre eles (grade efetiva de 45 em 45 min), refletindo os dias/horários definidos pelo Médico dentro do horário da clínica (RF-2).` |
| RNFs Transversais, "Fuso horário" (linha 332) | `...todas as regras de tempo (Slots de 15min, antecedência mínima de 48h, janela de cancelamento de 24h)...` | `...todas as regras de tempo (Slots de 30min com 15min de intervalo, antecedência mínima de 48h, janela de cancelamento de 24h)...` |
| JU-1, caminho (linha 56) | `...toca em um horário de quinta-feira às 14h30 e confirma...` | `...toca em um horário de quinta-feira às 14h e confirma...` |
| JU-1, clímax (linha 57) | `"Consulta agendada com Dra. Ana — quinta, 14h30"` | `"Consulta agendada com Dra. Ana — quinta, 14h"` |
| JU-2, caminho (linha 64) | `define dias da semana e faixas de horário em que atende, que o sistema converte em slots de 15 minutos.` | `define dias da semana e faixas de horário em que atende, dentro do horário da clínica (8h-18h), que o sistema converte em Slots de 30 minutos com 15 minutos de intervalo entre consultas.` |
| JU-4, persona (linha 78) | `...ambos mirando o slot das 14h30 de quinta-feira.` | `...ambos mirando o slot das 14h de quinta-feira.` |

### 5.2 Arquitetura — `ARCHITECTURE-SPINE.md`, AD-4

Adicionar ao final da regra existente (sem remover nada do texto atual sobre "nunca armazenado como slot" / fuso fixo / `timestamptz`):

> Cada Consulta dura 30 minutos, com 15 minutos de intervalo obrigatório até a próxima (grade efetiva de 45 em 45 min a partir do `start_time` do bloco do dia daquele Médico). O horário de início/fim de cada linha de `doctor_schedules` continua livremente escolhido pelo Médico (nada muda nisso), mas precisa caber dentro da janela fixa da clínica, 08:00-18:00 — garantida por uma `CHECK constraint` na própria tabela (Story 5.1), não só validada na Edge Function, seguindo o mesmo princípio de NFR1 (garantia no nível de armazenamento). Uma posição de início só é válida se estiver alinhada a essa grade de 45 min a partir do `start_time` do bloco **e** a Consulta inteira de 30 min couber antes do `end_time` do bloco.

### 5.3 `epics.md`

- FR2/FR5/NFR6 na "Requirements Inventory": mesmas edições da tabela 5.1 (mantidas em sincronia com o PRD).
- Story 1.1, Acceptance Criteria: a linha `Given que defini dias de atendimento e horário de início/fim / When confirmo a criação do perfil / Then minha agenda semanal é salva...` ganha, no `Then`, "...respeitando o horário da clínica (8h-18h)"; sem remover nada.
- Story 2.2, Acceptance Criteria: `...vejo blocos de 15 minutos, com os horários a menos de 48h...` → `...vejo horários de 30 minutos com 15 min de intervalo entre eles, com os horários a menos de 48h...`.
- Acrescentar ao final do arquivo:

```markdown
## Epic 5: Grade de Horários — Consulta de 30 Minutos com Intervalo de 15

Correção de requisito (Sprint Change Proposal, 2026-09-22): a Consulta dura 30 minutos, com 15 minutos de intervalo obrigatório até a próxima (grade efetiva de 45 em 45 min). O Médico continua escolhendo livremente seu horário de início/fim por dia, mas agora dentro de uma janela fixa da clínica, 08h às 18h (antes era 06h-22h). Substitui a premissa de "Slots de 15 minutos" usada nas Stories 1.1 e 2.2-2.5.

### Story 5.1: Sistema usa consultas de 30 minutos com intervalo de 15, dentro do horário da clínica (08h-18h)

Como usuário do sistema (paciente ou médico),
Eu quero que a agenda reflita a duração real de uma consulta (30min) com um intervalo de descanso (15min) entre uma e outra, e que o horário de cada Médico não ultrapasse o funcionamento da clínica,
Para que a grade de horários mostrada ao Paciente corresponda à forma real de atendimento, e nenhum Médico configure um horário fora do que a clínica sustenta.

**Acceptance Criteria:**

**Given** que um Médico está se cadastrando ou editando sua agenda
**When** escolhe o horário de início/fim de um dia de atendimento
**Then** as opções vão de 08:00 a 18:00 (mesmo passo de 30 min de hoje); um horário fora dessa janela é rejeitado tanto pela Edge Function quanto por uma `CHECK constraint` no banco

**Given** o Detalhe de um Médico com um dia selecionado, cujo horário configurado é, por exemplo, 08:00-12:00
**When** a grade de horários é exibida
**Then** os horários possíveis começam às 08:00 e seguem de 45 em 45 minutos (08:00, 08:45, 09:30, 10:15, 11:00) até o último horário cuja consulta de 30 minutos termine até o fim do bloco daquele Médico

**Given** um horário de início válido na grade
**When** o Paciente agenda esse horário
**Then** a consulta ocupa 30 minutos e o próximo horário agendável para aquele Médico está pelo menos 45 minutos à frente

**Given** uma chamada direta às funções de agendar/reagendar (fora da grade do app) com um horário que não está alinhado à grade de 45 min do bloco daquele Médico, ou cuja consulta de 30 min ultrapassaria o fim do bloco
**When** a chamada ocorre
**Then** é rejeitada com `INVALID:`, sem exceção — a validação vive no banco, não só na UI
```

- Atualizar a seção "Epic List" no topo do arquivo, acrescentando a linha do Epic 5.

### 5.4 `EXPERIENCE.md`

| Local | ANTES | DEPOIS |
|---|---|---|
| Narrativa de Cadastro de Médico (linha 125) | `...marca convênios (chips), marca dias de atendimento (chips) e horário início/fim.` | `...marca convênios (chips), marca dias de atendimento (chips) e horário início/fim (dentro do horário da clínica, 8h-18h).` |

### 5.5 Specs antigas (nota de erratum, sem tocar no bloco congelado)

Acrescentar, na seção "Design Notes" (ou logo após, se a seção não existir) de `spec-1-1-medico-cadastro-perfil.md`, `spec-2-2-paciente-visualiza-horarios.md`, `spec-2-3-paciente-agenda-consulta.md`, `spec-2-4-paciente-cancela-reagenda.md` e `spec-2-5-medico-cancela-reagenda.md`:

> **Nota de correção (Story 5.1, 2026-09-22):** a premissa de "Slots de 15 minutos" usada nesta spec foi substituída — consulta de 30 min com 15 min de intervalo (grade de 45 em 45 min). O Médico continua escolhendo seu próprio horário de início/fim (nada mudou nisso), mas agora dentro do horário fixo da clínica, 08h-18h (antes 06h-22h). Ver `spec-5-1-grade-horarios-30min.md` e o Sprint Change Proposal de 2026-09-22.

### 5.6 Código (implementação da Story 5.1 — detalhado na spec da própria story, não repetido aqui)

Resumo (ver §2.2 acima para a lista completa): estreitar a lista de horários do cadastro de médico (06-22 → 08-18); validar o limite 08h-18h na Edge Function e via `CHECK constraint` no banco (com normalização prévia dos dados de teste); nova migração recriando só `assert_slot_bookable` com a grade de 45 min; `AgendaSlots.kt` com a nova duração/intervalo; ajuste dos scripts de teste e do checklist manual.

## 6. Handoff

**Classificação: Moderada** (reorganização de backlog + implementação, sem replanejamento estratégico) — o Epic 5 é executado por este mesmo orquestrador (papel "Developer" no BMad) seguindo o fluxo `bmad-build` de sempre: spec → implementação → revisão com 3 agentes → commit.

**Responsabilidades:**
- Aplicar as edições de documentação (PRD, ARCHITECTURE-SPINE.md, epics.md, EXPERIENCE.md, notas de erratum nas 5 specs antigas) — orquestrador, imediatamente após aprovação.
- Atualizar `sprint-status.yaml` com `epic-5`/`5-1-...` em `backlog`.
- Rodar o `bmad-build` para a Story 5.1 (spec própria, implementação, revisão, commit).

**Critério de sucesso:** `assert_slot_bookable` rejeita qualquer horário fora da grade de 45 min do bloco do médico ou que não caiba antes do fim do bloco; a `CHECK constraint` impede um horário de médico fora de 08h-18h mesmo via SQL direto; a grade do app mostra os horários esperados para o bloco configurado de cada médico; cadastro de médico oferece só 08:00-18:00 no dropdown; scripts de teste contra o banco hospedado passam com a nova grade; `./gradlew assembleDebug testDebugUnitTest` verde.
