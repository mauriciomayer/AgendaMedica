---
stepsCompleted: [1, 2, 3]
inputDocuments:
  - _bmad-output/planning-artifacts/prds/prd-AgendaMedica-2026-09-17/prd.md
  - _bmad-output/planning-artifacts/prds/prd-AgendaMedica-2026-09-17/addendum.md
  - _bmad-output/planning-artifacts/architecture/architecture-AgendaMedica-2026-09-17/ARCHITECTURE-SPINE.md
  - _bmad-output/planning-artifacts/ux-designs/ux-AgendaMedica-2026-09-17/DESIGN.md
  - _bmad-output/planning-artifacts/ux-designs/ux-AgendaMedica-2026-09-17/EXPERIENCE.md
---

# Agenda Médica - Epic Breakdown

## Overview

This document provides the complete epic and story breakdown for Agenda Médica, decomposing the requirements from the PRD, UX Design, and Architecture requirements into implementable stories.

## Requirements Inventory

### Functional Requirements

FR1: Um Médico pode se autocadastrar informando seus dados e ficar visível na busca imediatamente, sem etapa de aprovação humana ou validação de documento (ex.: CRM).
FR2: Um Médico define exatamente uma Especialidade (lista fixa de 6), um ou mais Convênios (lista fixa de 4, incluindo "Particular") e os dias/horários em que atende, dentro do horário da clínica (8h às 18h). Especialidade e Convênios são imutáveis após a criação do perfil; dias/horários são editáveis a qualquer momento, sempre dentro dessa janela.
FR3: Um usuário pode se cadastrar como Paciente informando os dados definidos no design (nome, e-mail, senha), sem exigência de vínculo com Convênio no cadastro.
FR4: Um Paciente pode buscar Médicos filtrando por Especialidade e localização (GPS automático, com fallback de busca manual por cidade/bairro).
FR5: Um Paciente pode visualizar a Agenda de um Médico específico, vendo apenas Slots de 30 minutos (com 15 minutos de intervalo entre eles) livres e respeitando a Antecedência mínima de 48h.
FR6: Um Paciente pode agendar uma Consulta em qualquer Slot disponível com 48h ou mais de antecedência da hora atual.
FR7: O sistema deve garantir que nunca dois agendamentos coexistam para o mesmo Slot do mesmo Médico, mesmo sob requisições concorrentes simultâneas — a regra de negócio central do produto.
FR8: Paciente ou Médico podem cancelar ou reagendar uma Consulta enquanto faltarem 24h ou mais para o horário marcado. Reagendar é um fluxo dedicado que atualiza a Consulta existente (não cria uma nova).
FR9: Nenhum ator (Paciente, Médico) pode cancelar ou reagendar uma Consulta com menos de 24h de antecedência — bloqueio total pelo app, sem exceção manual possível por ninguém.
FR10: O sistema envia automaticamente um lembrete por push e e-mail ao Paciente 24h antes da Consulta (uma única vez, idempotente). O Médico recebe notificações de evento (nova Consulta, cancelamento), não lembrete de rotina.
FR11: Um usuário (Paciente ou Médico) que esqueceu a senha pode solicitar redefinição via e-mail cadastrado.

### NonFunctional Requirements

NFR1: A regra de bloqueio de conflito de agenda deve ser garantida no nível de armazenamento de dados (constraint de unicidade), não apenas verificada na camada de aplicação.
NFR2: Falha em um canal de notificação (push ou e-mail) não deve impedir o outro nem quebrar o fluxo de agendamento/cancelamento — notificação é best-effort, nunca bloqueante.
NFR3: O app não deve perder ou duplicar uma Consulta já confirmada em nenhuma circunstância, mesmo sem exigência formal de alta disponibilidade/SLA no volume inicial (20 usuários/semana).
NFR4: Buscas e visualização de agenda devem responder de forma percebida como instantânea para o volume-alvo, sem exigência de otimização para escala maior em v1.
NFR5: Dados de Paciente e histórico de Consultas só podem ser lidos pelo próprio Paciente e pelo Médico das Consultas em questão — nenhum outro Paciente ou Médico tem acesso. Autenticação obrigatória em toda operação que leia ou grave Consulta.
NFR6: Todas as regras de tempo (Slots de 30min com 15min de intervalo, antecedência mínima de 48h, janela de cancelamento de 24h) usam um fuso horário único e fixo (`America/Sao_Paulo`) — sem suporte a médico e paciente em fusos diferentes.
NFR7: Nenhum custo recorrente é aceitável no volume inicial — notificações usam apenas camadas gratuitas (Resend para e-mail); SMS está fora de escopo.
NFR8: Sem exigência formal de conformidade com a LGPD em v1, mas com boas práticas mínimas de proteção de dados (ver NFR5).
NFR9: Plataforma única Android nativo (v1); instalação direta no dispositivo é suficiente, sem exigência de publicação em loja.

### Additional Requirements

- Stack: Kotlin 2.4.20 + Jetpack Compose (BOM 2026.08.00, Compose Compiler plugin 2.4.20), padrão MVVM (View → ViewModel → Repository). Projeto greenfield sem starter template externo — estrutura de pastas definida no Structural Seed da Arquitetura (`app/ui`, `app/viewmodel`, `app/data/repository`, `app/data/remote`, `app/domain/model`).
- Backend: projeto Supabase único (Postgres + Auth + Edge Functions + Realtime), sem separação dev/produção no MVP. Cliente Android via `supabase-kt` (`auth-kt`, `postgrest-kt`, `realtime-kt`, `functions-kt`; minSdk 26).
- Schema de banco: tabelas `profiles`, `patients`, `doctors`, `doctor_schedules`, `appointments`, `booked_slots`; ENUM `appointment_status` (`confirmed`/`cancelled`); índice único parcial `UNIQUE (doctor_id, start_time) WHERE status = 'confirmed'`.
- Triggers: `sync_booked_slots` (mantém a projeção sem PII de horários ocupados, reage a mudança de `status` e `start_time`); `enforce_doctor_immutable_fields` (bloqueia alteração de `specialty`/`insurances` após criação).
- Funções Postgres `SECURITY DEFINER` (com `SET search_path` fixo, identidade sempre via `auth.uid()` interno, nunca por parâmetro do chamador): `book_appointment`, `cancel_appointment`, `reschedule_appointment`, `complete_registration`; funções auxiliares de RLS `is_doctor`/`is_patient` num schema não exposto (`private`), com `GRANT EXECUTE` e chamadas sempre schema-qualificadas.
- RLS: `appointments` escopado por dono (`patient_id`/`doctor_id` = `auth.uid()`); `booked_slots` com leitura aberta a autenticados (sem PII); `doctors`/`patients`/`profiles` só criados via função de cadastro.
- Edge Functions: `register-patient`, `register-doctor` (Auth Admin API + `complete_registration` transacional, com rollback do usuário Auth se a transação falhar), `send-reminders` (usa a `service_role` key, nunca exposta ao app; reivindica cada Consulta atomicamente antes de notificar, evitando duplicidade).
- Agendamento do lembrete: job `pg_cron` a cada 5 minutos, usando `pg_net.http_post` para chamar `send-reminders` — dentro do próprio projeto Supabase.
- Convenção de erro única nas funções Postgres: prefixos `CONFLICT:`, `INVALID:`, `FORBIDDEN:` via `RAISE EXCEPTION`, mais um bucket implícito `UNEXPECTED` no app para qualquer erro fora desse formato.
- Provedor de e-mail transacional: Resend (camada gratuita, 3.000/mês).
- Push notification: **deliberadamente fora do escopo desta leva de épicos** — decisão em aberto, a resolver como seu próprio épico futuro.
- Testes: stack local via Supabase CLI (`supabase start`) para testar as funções Postgres e a constraint de unicidade sem tocar dados reais; JUnit/Espresso no app Android.
- Deploy: manual via Supabase CLI (`supabase db push`, `supabase functions deploy`); app instalado diretamente no dispositivo (Android Studio/USB ou APK assinado) — sem publicação em loja no MVP.

### UX Design Requirements

UX-DR1: Implementar os tokens de design do DESIGN.md como tema Compose: cores (`surface-canvas`, `accent-primary` `#3B6FE0`, famílias `success`/`warning`/`danger`, neutros de texto), tipografia (Inter, pesos 400-700, escala de 11px a 19px), formas (`pill` 100px, `lg` 14px, `md` 10-12px, `circle` 50%).
UX-DR2: Implementar os componentes reutilizáveis definidos em DESIGN.md.Components: botão primário, botão outline, botão outline de perigo, chip de alternância (toggle), chip de tag somente-leitura, badge de status, card padrão, avatar com iniciais, input de texto/senha.
UX-DR3: Implementar as 10 superfícies de IA do EXPERIENCE.md: Login, Recuperar Senha, Criar Conta (Escolha), Cadastro de Paciente, Cadastro de Médico, Busca, Detalhe do Médico, Confirmação, Minhas Consultas, Minha Agenda.
UX-DR4: Reaproveitar a superfície de Detalhe também para o fluxo de Reagendar (mesmo layout; cabeçalho e rótulo do botão de confirmação mudam conforme o modo) — não construir uma tela nova para reagendamento.
UX-DR5: Implementar a confirmação de cancelamento **inline no card** (par de botões Sim/Não expandindo dentro do próprio card de consulta) — nunca um diálogo modal do sistema.
UX-DR6: Implementar todos os "State Patterns" do EXPERIENCE.md: campos obrigatórios incompletos (botão desabilitado), login inválido, recuperação de senha enviada (mensagem neutra, sem revelar se o e-mail existe), busca sem resultados, sem atendimento no dia selecionado, slot ocupado, slot fora da antecedência mínima, consulta bloqueada para cancelamento, sem consultas (Paciente), sem consultas (Médico).
UX-DR7: Implementar o "Voice and Tone" do EXPERIENCE.md nas microcópias do app — mensagens diretas e específicas (ex.: aviso de bloqueio automático de conflito, aviso de autocadastro sem validação de CRM), sem tom de desculpa e sem gamificação.
UX-DR8: Implementar o piso de acessibilidade do EXPERIENCE.md: alvos de toque ≥ 48dp; rótulos acessíveis (`contentDescription`) em botões de ícone (voltar, usar localização); todo estado (badge de status, motivo de slot/botão desabilitado) comunicado por texto, nunca só por cor; leitor de tela anuncia o motivo de um controle desabilitado.
UX-DR9: Adicionar à superfície "Minha Agenda" (Médico) os controles de Reagendar/Cancelar, reaproveitando o mesmo padrão de card já especificado para "Minhas Consultas" — pendência de design explicitamente registrada no EXPERIENCE.md (Open Items), necessária para cobrir FR8/FR9 do lado do Médico.
UX-DR10: Implementar estados de carregamento (skeleton/spinner) e erro de rede/servidor — não definidos no protótipo original (Open Item do EXPERIENCE.md, já que o protótipo é local/sem backend real), mas necessários numa implementação conectada de fato ao Supabase.

### FR Coverage Map

FR1: Epic 1 - Autocadastro de médico sem validação
FR2: Epic 1 - Perfil profissional do médico (especialidade, convênios, agenda)
FR3: Epic 1 - Cadastro de paciente
FR4: Epic 2 - Busca de médicos por especialidade e localização
FR5: Epic 2 - Visualização de horários disponíveis
FR6: Epic 2 - Agendamento com antecedência mínima de 48h
FR7: Epic 2 - Bloqueio de conflito de agenda (concorrência)
FR8: Epic 2 - Cancelamento/reagendamento dentro da janela de 24h
FR9: Epic 2 - Bloqueio total de cancelamento após 24h
FR10: Epic 3 - Lembrete de consulta e notificações de evento
FR11: Epic 1 - Recuperação de senha

## Epic List

### Epic 1: Cadastro, Perfil e Autenticação
Médico e Paciente criam conta; o Médico configura especialidade, convênios e agenda semanal e já aparece na busca; qualquer um recupera a senha se esquecer. Inclui a base técnica (setup do projeto Android + Supabase, schema inicial, tema visual).
**FRs covered:** FR1, FR2, FR3, FR11

### Epic 2: Agendamento de Consultas
Paciente busca médicos, vê horários realmente livres, agenda com antecedência mínima garantida e sem risco de conflito mesmo sob concorrência; Paciente ou Médico cancelam/reagendam dentro do prazo, bloqueado totalmente depois.
**FRs covered:** FR4, FR5, FR6, FR7, FR8, FR9

### Epic 3: Notificações de Consulta
Paciente recebe lembrete automático (push + e-mail) 24h antes; Médico recebe notificação quando uma consulta é marcada ou cancelada. Push fica com o canal preparado; o provedor de push em si é decisão futura (deferida).
**FRs covered:** FR10

### Epic 4: Identidade Visual do App
Adição pós-MVP, fora do escopo original do PRD: o app ganha um logo próprio, usado na splash screen e como ícone do app.
**FRs covered:** nenhum (identidade visual, fora do PRD)

### Epic 5: Grade de Horários — Consulta de 30 Minutos com Intervalo de 15
Correção de requisito (Sprint Change Proposal, 2026-09-22): a Consulta dura 30 minutos, com 15 minutos de intervalo obrigatório até a próxima (grade efetiva de 45 em 45 min), dentro do horário fixo da clínica (8h-18h) que todo Médico precisa respeitar ao configurar seu próprio horário.
**FRs covered:** FR2, FR5 (correção)

### Epic 6: Ajustes de Login, Sessão e Cancelamento
Triagem de bugs/melhorias reportados pelo usuário (bmad-party, 2026-09-23): Login mais robusto (e-mail validado, senha visível, bloqueio por papel — reverte a Story 1.2), botão de logout, horário sem segundos na Minha Agenda, cancelamento de consulta por modal em vez de confirmação inline (reverte UX-DR5 / Stories 2.4-2.5), e máscara de digitação nos campos de e-mail (corrige o entendimento da 6.1).
**FRs covered:** FR1, FR3 (robustez do login), FR8 (fluxo de cancelamento)

### Epic 7: Dívida Técnica e Arquitetura
Refatorações estruturais levantadas pelo arquiteto (Winston) ao fechar o Épico 6, sem mudança de comportamento visível: horário do médico tipado como `LocalTime`, componentes de consulta movidos para `ui/components/` (dependência doctor -> patient desfeita), um spike de teste de UI Compose (Robolectric) e o teste de UI da Splash, para fechar pendências do `deferred-work.md`.
**FRs covered:** nenhum (qualidade interna)

### Epic 8: Telas Seguem o Layout do Protótipo
Depois de alinhar a Login ao protótipo de design (Stories 6.6 e 6.7), as demais telas passam a seguir o mesmo layout: barra branca no topo com título/subtítulo/voltar, rótulos acima dos campos, cartões e avatares no estilo do protótipo, e a fonte Inter. As decisões posteriores ao protótipo (Sair, janela de confirmação do cancelamento, máscara de e-mail, campo Localização, convênio no agendamento, 7 dias da semana, Cancelar/Reagendar na agenda do médico) são mantidas.
**FRs covered:** nenhum (identidade visual, fora do PRD)

## Epic 1: Cadastro, Perfil e Autenticação

Médico e Paciente criam conta; o Médico configura especialidade, convênios e agenda semanal e já aparece na busca; qualquer um recupera a senha se esquecer. Inclui a base técnica (setup do projeto Android + Supabase, schema inicial, tema visual).

### Story 1.1: Médico se cadastra e configura seu perfil profissional

Como médico,
Eu quero me cadastrar informando meus dados, escolher especialidade/convênios e definir minha agenda semanal,
Para que meu perfil fique visível na busca imediatamente, sem validação de ninguém.

**Acceptance Criteria:**

**Given** que sou médico não cadastrado
**When** escolho "Sou médico", preencho nome/e-mail/senha e envio
**Then** minha conta é criada e sou autenticado automaticamente, sem etapa de aprovação ou verificação de documento
**And** nenhuma mensagem de "aguardando validação" é exibida (FR1)

**Given** que estou definindo meu perfil
**When** seleciono uma Especialidade
**Then** só posso ter uma selecionada por vez, entre as 6 fixas (Cardiologia, Dermatologia, Pediatria, Ortopedia, Clínico Geral, Ginecologia)
**And** o botão "Criar perfil" fica desabilitado sem essa seleção (FR2)

**Given** que estou definindo meu perfil
**When** seleciono Convênios
**Then** só as 4 opções fixas (Unimed, Amil, Bradesco, Particular) estão disponíveis
**And** preciso selecionar ao menos 1 para habilitar "Criar perfil" (FR2)

**Given** que defini dias de atendimento e horário de início/fim
**When** confirmo a criação do perfil
**Then** minha agenda semanal é salva em `doctor_schedules`, respeitando o horário da clínica (8h-18h)
**And** "Minha Agenda" aparece vazia, sem consultas (FR2)

**Given** que já criei meu perfil com Especialidade e Convênios definidos
**When** tento alterar Especialidade ou Convênios depois
**Then** a interface não oferece essa opção
**And** uma tentativa direta via API é rejeitada pela trigger `enforce_doctor_immutable_fields` (FR2, AD-11)

**Given** que já tenho perfil criado
**When** edito meus dias/horários de atendimento
**Then** a alteração é salva imediatamente em `doctor_schedules`, sem afetar Consultas já confirmadas (FR2)

**Given** qualquer tela de cadastro/login construída nesta história
**When** uso um leitor de tela (TalkBack)
**Then** botões de ícone (ex.: voltar) têm rótulo acessível (`contentDescription`) e todo alvo de toque mede ao menos 48dp (UX-DR7)

**Given** as mensagens do sistema nas telas desta história (ex.: aviso de autocadastro sem validação de CRM)
**When** são exibidas ao usuário
**Then** seguem o tom direto e específico do EXPERIENCE.md — sem tom de desculpa, sem gamificação, sem exclamação fora do ponto de sucesso (UX-DR6)

### Story 1.2: Paciente se cadastra

Como paciente,
Eu quero me cadastrar informando meus dados,
Para que eu possa buscar médicos e agendar consultas.

**Acceptance Criteria:**

**Given** que não sou cadastrado
**When** escolho "Sou paciente", preencho nome/e-mail/senha e envio
**Then** minha conta é criada e sou autenticado automaticamente, caindo na tela de Busca (FR3)

**Given** que estou no formulário de cadastro
**When** deixo um campo obrigatório vazio
**Then** o botão "Criar conta" permanece desabilitado

**Given** que estou me cadastrando
**When** completo o formulário
**Then** não existe nenhum campo obrigatório de Convênio (FR3)

**Given** que acabei de me cadastrar
**When** acesso "Minhas Consultas"
**Then** vejo o estado vazio "Você ainda não tem consultas agendadas"

### Story 1.3: Usuário recupera a senha esquecida

Como paciente ou médico que esqueceu a senha,
Eu quero solicitar um link de redefinição pelo e-mail cadastrado,
Para que eu recupere o acesso à minha conta sem depender de suporte.

**Acceptance Criteria:**

**Given** que estou na tela de Login
**When** toco em "Esqueci minha senha" e informo meu e-mail cadastrado
**Then** recebo uma mensagem neutra de confirmação ("Se o e-mail informado existir, enviamos um link...")
**And** a resposta nunca revela se a conta existe (FR11)

**Given** que solicitei a redefinição
**When** abro o link recebido por e-mail dentro do prazo padrão do Supabase Auth
**Then** consigo definir uma nova senha

**Given** que defini uma nova senha
**When** tento entrar com a senha antiga
**Then** o acesso é negado — só a nova senha funciona

**Given** que o link expirou
**When** tento usá-lo
**Then** recebo uma mensagem de erro clara pedindo para solicitar um novo link

## Epic 2: Agendamento de Consultas

Paciente busca médicos, vê horários realmente livres, agenda com antecedência mínima garantida e sem risco de conflito mesmo sob concorrência; Paciente ou Médico cancelam/reagendam dentro do prazo, bloqueado totalmente depois.

### Story 2.1: Paciente busca médicos por especialidade e localização

Como paciente,
Eu quero buscar médicos filtrando por especialidade e localização,
Para encontrar rapidamente quem atende minha necessidade.

**Acceptance Criteria:**

**Given** que estou na tela de Busca autenticado
**When** seleciono uma Especialidade no filtro
**Then** só vejo médicos daquela especialidade (FR4)

**Given** que tenho localização por GPS ativada
**When** realizo a busca
**Then** os resultados aparecem ordenados por distância crescente (FR4)

**Given** que não tenho permissão de GPS
**When** uso a busca manual por cidade/bairro
**Then** os resultados são filtrados por essa região e ordenados alfabeticamente pelo nome do médico (FR4, fallback)

**Given** que a busca não retorna resultados
**When** aplico filtros sem correspondência
**Then** vejo a mensagem "Nenhum médico encontrado com esses filtros."

**Given** um resultado de busca
**When** visualizo o card do médico
**Then** vejo nome, especialidade, cidade e os chips de convênio aceitos

**Given** que a busca está em andamento
**When** a lista de médicos ainda não chegou do Supabase
**Then** vejo um indicador de carregamento, nunca uma tela em branco (UX-DR10)

**Given** uma falha de rede/servidor ao buscar médicos
**When** isso ocorre
**Then** vejo uma mensagem genérica de erro com opção de tentar novamente — nunca o texto bruto do erro técnico (UX-DR10, AD-9 bucket `UNEXPECTED`)

### Story 2.2: Paciente visualiza horários disponíveis de um médico

Como paciente,
Eu quero ver a agenda de um médico específico,
Para escolher um horário que respeite a antecedência mínima.

**Acceptance Criteria:**

**Given** que abri o perfil de um médico
**When** visualizo o carrossel de dias
**Then** só aparecem os dias em que ele atende, até 6 dias à frente (FR5)

**Given** que selecionei um dia
**When** a grade de horários carrega
**Then** vejo horários de 30 minutos com 15 min de intervalo entre eles, com os horários a menos de 48h de antecedência desabilitados e com o motivo "Antecedência mín. 48h" (FR5, FR6)

**Given** horários já ocupados por outra consulta confirmada
**When** vejo a grade
**Then** esses horários aparecem desabilitados com o motivo "Ocupado" (FR5)

**Given** um dia sem nenhum horário de atendimento configurado
**When** seleciono esse dia
**Then** vejo a mensagem "Sem atendimento neste dia."

### Story 2.3: Paciente agenda uma consulta com garantia de que não haverá conflito de horário

Como paciente,
Eu quero confirmar um agendamento em um horário disponível,
Para garantir minha consulta sem risco de conflito, mesmo se outro paciente tentar o mesmo horário ao mesmo tempo.

**Acceptance Criteria:**

**Given** que escolhi médico, dia e horário válidos (48h ou mais de antecedência)
**When** toco em "Confirmar agendamento"
**Then** a consulta é criada com sucesso e vejo a tela de Confirmação com o resumo (médico, especialidade, data/hora, convênio) (FR6)

**Given** que médico, dia ou horário não estão todos selecionados
**When** olho o botão de confirmar
**Then** ele está desabilitado

**Given** duas requisições de agendamento simultâneas para o mesmo médico e horário
**When** ambas chegam ao mesmo tempo
**Then** exatamente uma é aceita e a outra recebe o erro "Este horário acabou de ser reservado, escolha outro" (FR7, testável via teste de concorrência simulada — valida MS-2 do PRD)

**Given** que minha requisição de agendamento foi rejeitada por conflito
**When** a rejeição acontece
**Then** a grade de horários é atualizada automaticamente (via Realtime) para mostrar o horário como ocupado, sem eu precisar recarregar a tela (FR7, AD-10)

**Given** que agendei com sucesso
**When** a consulta é criada
**Then** o evento fica registrado para notificar o médico — a entrega efetiva (push) depende do provedor de push, decisão adiada para um épico futuro (ver Épico 3, Story 3.2)

### Story 2.4: Paciente cancela ou reagenda uma consulta

Como paciente,
Eu quero cancelar ou reagendar uma consulta futura,
Para ajustar meus planos sem perder o controle da minha agenda.

**Acceptance Criteria:**

**Given** uma consulta confirmada com 24h ou mais de antecedência
**When** toco em "Cancelar" e confirmo "Sim" na confirmação inline
**Then** a consulta muda para status cancelada e o horário volta a ficar disponível para outros pacientes imediatamente (FR8)

**Given** uma consulta confirmada com 24h ou mais de antecedência
**When** toco em "Reagendar", escolho novo dia/horário e confirmo
**Then** a mesma consulta é atualizada para o novo horário — não é criada uma consulta nova (FR8)

**Given** uma consulta a menos de 24h do horário marcado
**When** abro "Minhas Consultas"
**Then** os botões "Cancelar" e "Reagendar" aparecem desabilitados, com a nota "Bloqueado: faltam menos de 24h..." (FR9)

**Given** uma consulta bloqueada (menos de 24h)
**When** tento chamar a função de cancelamento diretamente, fora da UI
**Then** a função rejeita a operação com um erro `CONFLICT` — não existe nenhuma forma de contornar o bloqueio (FR9)

**Given** que cancelei ou reagendei
**When** a ação é concluída
**Then** o evento fica registrado para notificar o médico — a entrega efetiva (push) depende do provedor de push, decisão adiada para um épico futuro (ver Épico 3, Story 3.2)

### Story 2.5: Médico cancela ou reagenda uma consulta de um paciente

Como médico,
Eu quero cancelar ou reagendar uma consulta marcada comigo,
Para ajustar minha agenda dentro do mesmo prazo aplicado ao paciente.

**Acceptance Criteria:**

**Given** uma consulta confirmada com 24h ou mais de antecedência
**When** acesso "Minha Agenda" e toco em "Cancelar" numa consulta, confirmando "Sim"
**Then** a consulta é cancelada e o horário libera para outros pacientes (FR8, lado médico)

**Given** uma consulta confirmada com 24h ou mais de antecedência
**When** toco em "Reagendar" e escolho novo horário dentro da minha própria agenda
**Then** a consulta é atualizada com o novo horário (FR8, lado médico)

**Given** uma consulta a menos de 24h
**When** vejo "Minha Agenda"
**Then** os botões de Cancelar/Reagendar aparecem desabilitados — mesma regra do lado do Paciente, sem exceção para o médico (FR9)

**Given** que cancelei/reagendei como médico
**When** a ação é concluída
**Then** o evento fica registrado para notificar o paciente — a entrega efetiva (push) depende do provedor de push, decisão adiada para um épico futuro (ver Épico 3, Story 3.2)

## Epic 3: Notificações de Consulta

Paciente recebe lembrete automático (e-mail) 24h antes; eventos de novo agendamento/cancelamento ficam registrados para notificação push futura, já que o provedor de push é uma decisão deliberadamente adiada.

### Story 3.1: Paciente recebe lembrete de consulta por e-mail 24h antes

Como paciente,
Eu quero receber um lembrete por e-mail 24h antes da minha consulta,
Para não esquecer o compromisso.

**Acceptance Criteria:**

**Given** uma consulta confirmada chegando a 24h de antecedência
**When** o job de lembrete roda (a cada 5 minutos)
**Then** o paciente recebe um e-mail com os detalhes da consulta (FR10)

**Given** que o lembrete já foi enviado para uma consulta
**When** o job roda novamente
**Then** o mesmo lembrete não é enviado de novo, via `reminder_sent_at` (FR10)

**Given** uma consulta cancelada antes do marco de 24h
**When** o job roda
**Then** nenhum lembrete é enviado para essa consulta (FR10)

**Given** uma falha ao enviar e-mail (ex.: Resend indisponível)
**When** isso ocorre
**Then** o erro é registrado, mas não trava o processamento das demais consultas da mesma execução (NFR2)

### Story 3.2: Sistema registra eventos de notificação para entrega futura (novo agendamento, cancelamento)

Como usuário do sistema (paciente ou médico),
Eu quero que eventos de agendamento/cancelamento fiquem registrados de forma confiável,
Para que eu receba notificações push assim que esse canal for decidido, sem perder eventos que já aconteceram enquanto isso.

**Acceptance Criteria:**

**Given** que uma consulta é agendada
**When** a função `book_appointment` é concluída
**Then** um registro é criado em `notification_events` com `event_type = 'new_appointment'`, associado ao médico (`recipient_id`) e à consulta (`appointment_id`), na mesma transação (AD-12)

**Given** que uma consulta é cancelada ou reagendada
**When** a função correspondente (`cancel_appointment`/`reschedule_appointment`) conclui
**Then** um registro é criado em `notification_events` com `event_type = 'cancellation'`/`'reschedule'`, associado à outra parte, na mesma transação (AD-12)

**Given** que um evento foi registrado
**When** consulto `notification_events`
**Then** encontro `event_type`, `recipient_id`, `appointment_id` e `created_at` — pronto para um futuro consumidor de push filtrar por `delivered_at IS NULL`, sem reprocessar dados históricos (AD-12)

**Given** que o provedor de push ainda não está implementado
**When** um evento é criado
**Then** nenhuma tentativa de envio real é feita — o registro é o único efeito colateral nesta fase, sem erros ou travamentos

## Epic 4: Identidade Visual do App

Adição pós-MVP, fora do escopo original do PRD: o app ganha um logo próprio (documento/prancheta com selo de cruz médica vermelha), usado na splash screen e como ícone do app no launcher do Android, substituindo o ícone padrão gerado pelo template.

### Story 4.1: App exibe splash screen e ícone próprios com o novo logo

Como usuário (Paciente ou Médico),
Eu quero ver a identidade visual do app (logo) ao abrir e no launcher do celular,
Para reconhecer o app e ter uma primeira impressão profissional, em vez do ícone/tela padrão do Android.

**Especificação de origem:** protótipo interativo em `https://claude.ai/artifact/4522fNaJd6sBqsL9cBwV1A` (mockup do Android, telas "splash" e "Login"), com o desenho exato do logo (SVG) e as cores.

**Acceptance Criteria:**

**Given** o app é aberto do zero (cold start)
**When** a splash screen aparece
**Then** ela mostra o logo (documento branco com 3 linhas e selo de cruz vermelha) centralizado sobre o fundo `oklch(0.97 0.015 150)` (mesmo tom de `surface-canvas` do DESIGN.md), com o nome do app abaixo, por cerca de 1,6s, e então segue para a tela normal (Login ou a tela do usuário já autenticado)

**Given** o app está instalado no celular
**When** o usuário olha a tela de apps/launcher
**Then** o ícone do app é gerado a partir do mesmo logo (documento + selo de cruz vermelha), não mais o ícone padrão do Android Studio, com uma versão adaptável (adaptive icon) para os formatos de máscara do Android moderno

**Given** o logo aparece em mais de um lugar (splash e cabeçalho do Login, como já ocorre no protótipo)
**When** for desenhado
**Then** usa exatamente o mesmo desenho SVG do protótipo (mesmas proporções e cores), só variando o tamanho

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

## Epic 6: Ajustes de Login, Sessão e Cancelamento

Triagem de bugs e melhorias reportados pelo usuário após uso real do app (bmad-party, 2026-09-23). Duas das quatro histórias revertem decisões de design já revisadas e testadas em histórias anteriores — o usuário confirmou a reversão nos dois casos, com o motivo registrado em cada história.

### Story 6.1: Login valida o e-mail, mostra a senha digitada e bloqueia por papel selecionado

Como usuário (Paciente ou Médico),
Eu quero que o Login recuse um e-mail mal formado, deixe eu ver a senha que digitei, e me avise se eu selecionar a aba errada,
Para não digitar errado sem perceber e não cair na tela do papel errado sem entender por quê.

**Decisão de reversão:** a Story 1.2 (Review Triage Log, achado #8) decidiu deliberadamente que a aba Paciente/Médico do Login é só um atalho visual — quem manda é o papel real gravado em `profiles`, mesmo se a aba selecionada for a outra. O usuário testou esse comportamento na prática e achou a experiência ruim; a partir desta história, a aba selecionada passa a ser **obrigatória**: um login bem-sucedido cuja conta pertence ao outro papel é negado com mensagem clara, não redirecionado.

**Acceptance Criteria:**

**Given** o formulário de Login (Paciente ou Médico)
**When** o e-mail digitado não tem o formato `algo@algo.algo`
**Then** o botão "Entrar" fica desabilitado, mesmo padrão já usado no Cadastro

**Given** o campo de senha, no Login e em qualquer outro formulário que peça senha
**When** o usuário toca no ícone de olho ao lado do campo
**Then** a senha digitada fica visível em texto; tocar de novo volta a ocultar

**Given** que o usuário selecionou a aba "Paciente" e digitou e-mail/senha de uma conta de Médico (ou vice-versa)
**When** as credenciais são válidas e o login seria aceito
**Then** o acesso é negado com uma mensagem clara (ex.: "Este e-mail é de uma conta de médico. Selecione a aba correta.") e o usuário permanece no Login, sem sessão aberta

### Story 6.2: Usuário sai da própria conta (logout)

Como usuário autenticado (Paciente ou Médico),
Eu quero um botão para sair da minha conta,
Para trocar de usuário sem precisar fechar e reabrir o app.

**Acceptance Criteria:**

**Given** que estou autenticado em Minha Agenda (Médico) ou Minhas Consultas (Paciente)
**When** toco em "Sair"
**Then** minha sessão é encerrada e volto ao Login, sem conseguir voltar à tela anterior pelo botão voltar

### Story 6.3: Minha Agenda mostra o horário de atendimento sem os segundos

Como médico,
Eu quero ver meu horário de atendimento no formato "08:00 - 18:00",
Para não ver um "08:00:00" que não faz sentido para mim.

**Acceptance Criteria:**

**Given** a tela Minha Agenda com dias/horários de atendimento cadastrados
**When** a lista é exibida
**Then** cada horário aparece como "HH:mm" (ex.: "08:00 - 18:00"), nunca com segundos

### Story 6.4: Cancelamento de consulta usa uma janela de confirmação, não mais inline no card

Como paciente ou médico,
Eu quero que cancelar uma consulta abra uma janela de confirmação que só fecha quando eu escolher Sim ou Não,
Para não cancelar por engano tocando perto do botão ou fora da área de confirmação.

**Decisão de reversão:** UX-DR5 (e as Stories 2.4/2.5, que a implementaram e testaram nos dois lados) definiram deliberadamente que a confirmação de cancelamento é inline no próprio card, nunca um diálogo modal do sistema. O usuário decidiu reverter essa decisão: um modal que não fecha por toque fora nem pelo botão voltar é mais seguro contra cancelamento acidental do que a confirmação inline.

**Acceptance Criteria:**

**Given** uma consulta confirmada com 24h ou mais de antecedência, em Minhas Consultas (Paciente) ou Minha Agenda (Médico)
**When** toco em "Cancelar"
**Then** uma janela de confirmação (modal) abre por cima da tela, com as opções "Sim" e "Não" do mesmo tamanho

**Given** a janela de confirmação aberta
**When** toco fora dela ou aciono o botão/gesto de voltar do sistema
**Then** nada acontece — a janela só fecha quando "Sim" ou "Não" é escolhido

**Given** a janela de confirmação aberta
**When** escolho "Sim"
**Then** a consulta é cancelada como hoje (mesma regra de 24h, mesmo evento para a outra parte); escolhendo "Não", a janela fecha sem nenhuma alteração

### Story 6.5: Campos de e-mail aceitam só caracteres válidos, como no login do Google

Como usuário (Paciente ou Médico),
Eu quero que todo campo de e-mail do app só me deixe digitar caracteres que existem num e-mail, com o teclado de e-mail do Android,
Para não errar por engano (espaço, acento, dois "@") nem colar um e-mail com espaço sobrando.

**Correção da Story 6.1:** a 6.1 tratou "o campo aceita qualquer caractere" como validação de formato (botão desabilitado se o e-mail não bate com `algo@algo.algo`). O usuário esclareceu que queria uma **máscara de digitação**, no estilo do campo de e-mail das telas de login de conta Google no Android: a tecla inválida simplesmente não entra. A validação de formato da 6.1 continua valendo; esta história acrescenta a máscara por cima.

**Acceptance Criteria:**

**Given** qualquer campo de e-mail do app (Login, Cadastro de Médico, Cadastro de Paciente, Recuperar Senha)
**When** o campo recebe foco
**Then** o teclado é o de e-mail (com `@` à mão), sem maiúscula automática e sem autocorreção

**Given** um desses campos
**When** o usuário digita espaço, letra com acento, emoji ou qualquer caractere fora de letras sem acento, números, `@`, `.`, `_`, `-`, `+` e `%`
**Then** o caractere não entra no campo, sem mensagem de erro

**Given** um desses campos que já contém um `@`
**When** o usuário tenta digitar um segundo `@`
**Then** o segundo `@` não entra

**Given** um desses campos
**When** o usuário cola um texto com caracteres inválidos ou espaços nas pontas (ex.: `" joao silva@gmail.com "`)
**Then** o texto é limpo pelo mesmo filtro em vez de rejeitado por inteiro (resultado: `joaosilva@gmail.com`); maiúsculas e minúsculas são preservadas como digitadas

### Story 6.6: Login exibe o logo do app

Como usuário (Paciente ou Médico),
Eu quero ver o logo do app no topo da tela de Login, como no protótipo de design,
Para reconhecer o app assim que ele abre e a tela ficar fiel ao design.

**Origem:** o protótipo de design (artefato compartilhado pelo usuário) mostra o logo (documento com selo de cruz vermelha) centralizado no topo do conteúdo da tela de Login, com 88 dp. O app nunca o exibiu ali: a Story 4.1 só o colocou na Splash e deixou o reuso no Login "fora de escopo" (KDoc de `Logo.kt`).

**Acceptance Criteria:**

**Given** a tela de Login
**When** ela é exibida
**Then** o logo do app aparece centralizado no topo do conteúdo, com 88 dp de largura, acima do restante do formulário

**Given** a tela de Login
**When** o restante do formulário é usado (abas Paciente/Médico, e-mail, senha, Entrar, Esqueci minha senha, Criar conta)
**Then** todo o comportamento existente continua igual; o logo é apenas decorativo, sem ação de toque nem leitura por leitor de tela

### Story 6.7: Tela de Login segue o layout do protótipo de design

Como usuário (Paciente ou Médico),
Eu quero que a tela de Login tenha o mesmo layout do protótipo de design,
Para a primeira tela do app ficar fiel ao design aprovado (as demais telas serão revistas depois, uma a uma).

**Escopo:** só a tela de Login. Nenhuma outra tela muda de aparência nesta história.

**Acceptance Criteria:**

**Given** a tela de Login
**When** ela é exibida
**Then** há um cabeçalho branco com o título "Entrar" e, abaixo, um subtítulo "Acesse sua conta de paciente" (aba Paciente) ou "Acesse sua conta de médico" (aba Médico), separado do conteúdo por uma linha fina

**Given** o conteúdo da Login
**When** ele é exibido
**Then** aparecem, de cima para baixo e alinhados como no protótipo: o logo centralizado (Story 6.6), o seletor Paciente/Médico centralizado em formato de pílula (aba ativa azul com texto branco), o campo E-mail com o rótulo acima e o exemplo "voce@email.com", o campo Senha com o rótulo acima e "••••••••", o botão "Entrar", o link "Esqueci minha senha" centralizado e "Não tem conta? Criar conta" centralizado

**Given** mensagens de erro ou de aviso
**When** existirem (erro de login, papel errado, "senha redefinida")
**Then** aparecem em caixas arredondadas coloridas acima do botão Entrar (aviso de erro em tom quente, aviso de sucesso em tom verde)

**Given** qualquer interação da Login
**When** o usuário usa a tela
**Then** todo o comportamento existente continua igual: máscara e teclado de e-mail, olho na senha, botão "Entrar" desabilitado até o formulário ser válido, bloqueio por papel, "Esqueci minha senha" e "Criar conta" navegam como antes

## Epic 7: Dívida Técnica e Arquitetura

Refatorações estruturais aprovadas pelo usuário após conversa com o arquiteto (2026-09-23). Nenhuma delas muda o comportamento visível do app; todas reduzem risco de regressão silenciosa ou de acoplamento indevido.

### Story 7.1: Horário de atendimento do médico é tipado como hora, não como texto

Como desenvolvedor,
Eu quero que `ScheduleBlock.startTime`/`endTime` sejam `LocalTime`, com o parse feito uma única vez na fronteira do Repository,
Para que um horário nunca mais apareça com segundos (bug da Story 6.3) nem exija `LocalTime.parse` espalhado pelo código.

**Acceptance Criteria:**

**Given** um horário de atendimento lido do Postgres (`"08:00:00"`)
**When** o `DoctorRepository` monta o `ScheduleBlock`
**Then** o campo já é um `LocalTime`, e nenhum outro código faz `LocalTime.parse` sobre ele

**Given** a tela Minha Agenda
**When** exibe um bloco de horário
**Then** aparece como `HH:mm`, igual a antes da mudança

**Given** a geração de horários disponíveis (`slotsDoDia`)
**When** roda sobre blocos `LocalTime`
**Then** produz exatamente os mesmos horários de antes

### Story 7.2: Componentes de consulta vivem em `ui/components/`

Como desenvolvedor,
Eu quero que `ConsultaCard`, `CancelConfirmDialog` e `formatarDataHora` saiam do pacote `patient`,
Para que a tela do médico não dependa de um pacote de outra feature.

**Acceptance Criteria:**

**Given** a tela Minha Agenda (Médico) e a tela Minhas Consultas (Paciente)
**When** o app é compilado
**Then** as duas importam esses componentes de `ui/components/`, e nenhum arquivo do pacote `doctor` importa algo do pacote `patient`

**Given** qualquer uma das duas telas
**When** usada
**Then** o comportamento e a aparência são idênticos aos de antes (mudança puramente estrutural)

### Story 7.3: Spike de teste de UI Compose com Robolectric

Como desenvolvedor,
Eu quero saber se testes de UI Compose rodam no `testDebugUnitTest` deste projeto (Robolectric + `compose-ui-test`, `compileSdk` 37),
Para decidir com evidência se vale fechar as pendências de teste de UI registradas no `deferred-work.md`.

**Acceptance Criteria:**

**Given** a infraestrutura de teste adicionada
**When** roda um teste do `CancelConfirmDialog`
**Then** ele comprova que tocar fora ou apertar voltar não fecha o diálogo, e que Sim/Não chamam seus callbacks

**Given** o spike concluído
**When** o resultado é avaliado
**Then** fica registrado (na spec) se a infraestrutura é viável; se sim, os demais testes pendentes (ligação de `isEmail` nas 4 telas, Splash) entram nesta mesma história ou em uma seguinte; se não, o motivo fica documentado e a história termina sem a infra

### Story 7.4: Splash Screen tem teste de UI

Como desenvolvedor,
Eu quero testes de UI da `SplashScreen` usando a infraestrutura Robolectric criada na Story 7.3,
Para fechar a pendência de cobertura registrada na Story 4.1 (navegação ao Login após o tempo fixo e cálculo do tempo restante após recriação da Activity).

**Acceptance Criteria:**

**Given** a Splash exibida
**When** passam menos de 1600 ms
**Then** o nome do app aparece e `onFinished` ainda não foi chamado; tocar na tela não a encerra antes do tempo

**Given** a Splash exibida
**When** passam 1600 ms
**Then** `onFinished` é chamado exatamente uma vez

**Given** uma recriação da Activity (ex.: rotação) depois de parte do tempo já ter passado
**When** a Splash é recriada
**Then** ela conta só o tempo restante, não reinicia os 1600 ms do zero

## Epic 8: Telas Seguem o Layout do Protótipo

Levantamento feito com o arquiteto (2026-09-25) comparando cada tela do app com o protótipo de design. Decisões do usuário: (1) incluir a fonte Inter; (2) executar todas as histórias em sequência; (3) nos cartões de consulta a ordem dos botões continua **Cancelar, Reagendar**; (4) o avatar do médico é azul cheio com letras brancas, como no protótipo. Decisões já tomadas por padrão: o botão de voltar é desenhado com 36 dp dentro de uma área de toque de 48 dp (acessibilidade); o **Sair** vai para o canto direito da barra; o texto "intervalos de 15 min" do protótipo **não** é copiado (as consultas são de 30 minutos).

**Mantido mesmo sem existir no protótipo:** botão Sair, janela de confirmação do cancelamento, olho da senha, máscara de e-mail, campo Localização e "Mínimo de 6 caracteres" nos cadastros, convênio ao agendar, 7 dias da semana no cadastro do médico, Cancelar/Reagendar com a regra de 24h na agenda do médico, fluxo de GPS da Busca, "Nova senha".

### Story 8.1: Barra do topo compartilhada e telas de acesso no layout do protótipo

Como usuário,
Eu quero que Recuperar senha, Nova senha, Criar conta e Cadastro de paciente tenham a barra branca do protótipo e o mesmo estilo de campos,
Para as telas de acesso ficarem consistentes com a Login.

**Acceptance Criteria:**

**Given** qualquer uma dessas 4 telas
**When** ela é exibida
**Then** aparece a barra branca com título em negrito, subtítulo quando houver, botão de voltar redondo (36 dp visuais, toque de 48 dp) e a linha fina embaixo; a Login passa a usar o mesmo componente

**Given** Criar conta
**When** exibida
**Then** mostra o texto "Como você quer usar o app?" e dois cartões clicáveis por inteiro: "Sou paciente" (Buscar médicos e agendar consultas) e "Sou médico" (Cadastrar meu perfil e atender pacientes), nessa ordem

**Given** Cadastro de paciente e Recuperar senha
**When** exibidos
**Then** os campos têm rótulo acima e exemplo dentro (Nome completo/"Seu nome", E-mail/"voce@email.com", Senha/"••••••••"); Recuperar senha mostra o texto de apoio e, depois de enviar, uma caixa verde com "Voltar ao login"

### Story 8.2: Busca no layout do protótipo

Como paciente,
Eu quero a Busca com a barra "Agende / Encontre um médico e agende", os filtros num cartão, a contagem de resultados e os cartões de médico com avatar,
Para a tela principal ficar fiel ao design.

**Acceptance Criteria:**

**Given** a Busca
**When** exibida
**Then** a barra tem o título "Agende", o subtítulo "Encontre um médico e agende" e o botão "Minhas consultas" em pílula com borda azul; os filtros (especialidade e cidade, com o botão de localização) ficam num único cartão; uma linha "N médico(s) encontrado(s)" aparece acima da lista

**Given** a lista de resultados
**When** exibida
**Then** cada cartão tem avatar azul cheio com iniciais brancas, nome, "Especialidade · Cidade" numa linha e os convênios em chips menores; o fluxo de GPS, os estados de carregando/erro e "Tentar novamente" continuam iguais

### Story 8.3: Minhas consultas e cartão de consulta no layout do protótipo

Como paciente,
Eu quero Minhas consultas com a barra do protótipo e o cartão de consulta no estilo do design,
Para a lista ficar fiel ao design sem perder a regra de 24 horas.

**Acceptance Criteria:**

**Given** Minhas consultas
**When** exibida
**Then** a barra tem título "Minhas consultas", voltar e o **Sair** no canto direito; o botão "+ Nova consulta" fica sempre visível ao final da lista

**Given** um cartão de consulta (paciente e médico)
**When** exibido
**Then** mostra nome, especialidade (quando houver), selo Confirmada/Bloqueada, "dd/MM às HH:mm · Convênio" numa linha, a nota de bloqueio em caixa de aviso quando faltar menos de 24h, e os botões Cancelar e Reagendar (nessa ordem) com a regra de 24h e a janela de confirmação inalteradas

### Story 8.4: Detalhe do médico e Confirmação no layout do protótipo

Como paciente,
Eu quero escolher horário e ver a confirmação como no protótipo,
Para o fluxo de agendamento ficar fiel ao design.

**Acceptance Criteria:**

**Given** o Detalhe do médico
**When** exibido
**Then** a barra tem "Escolher horário" (ou "Reagendar consulta") com voltar; o médico aparece num cartão com avatar, nome e "Especialidade · Cidade" com os convênios em chips; os dias são cartões com dia da semana e número; "Escolha o dia" e "Horários disponíveis" aparecem como títulos de seção; o botão de confirmar fica numa barra fixa embaixo; a nota "Agendamento exige mínimo de 48h de antecedência." aparece; o seletor de convênio continua

**Given** a Confirmação
**When** exibida
**Then** tem a barra "Confirmado", um círculo verde com check, "Consulta agendada!" centralizado e o resumo (nome, especialidade, data/hora, convênio) num único cartão, com "Buscar outro médico" e "Ver minhas consultas"

### Story 8.5: Cadastro de médico no layout do protótipo

Como médico,
Eu quero o Cadastro de médico com a barra e os campos no estilo do protótipo,
Para o cadastro ficar consistente com as outras telas de acesso.

**Acceptance Criteria:**

**Given** o Cadastro de médico
**When** exibido
**Then** a barra tem "Cadastro de médico" e o subtítulo "Autocadastro, sem validação de CRM"; o aviso do CRM aparece numa caixa azul; os campos e as seleções (especialidade, horário de início e fim, localização) têm rótulo acima; os chips de convênio e de dia da semana seguem o estilo do protótipo, com os 7 dias; o campo Localização e as regras existentes continuam

### Story 8.6: Minha agenda no layout do protótipo

Como médico,
Eu quero a Minha agenda com a barra do protótipo e o cartão de perfil no estilo do design,
Para a tela inicial do médico ficar fiel ao design.

**Acceptance Criteria:**

**Given** Minha agenda
**When** exibida
**Then** a barra tem o título "Minha agenda" e o **Sair** à direita; o cartão de perfil segue o estilo do protótipo (mantendo os horários de atendimento); uma caixa azul avisa que não é possível haver dois pacientes no mesmo horário; "Próximas consultas" é um título de seção; os cartões de consulta mantêm Cancelar/Reagendar e a regra de 24h

### Story 8.7: Fonte Inter em todo o app

Como usuário,
Eu quero que o app use a fonte Inter, como no protótipo,
Para a tipografia ficar igual ao design em todas as telas.

**Acceptance Criteria:**

**Given** qualquer tela do app
**When** exibida
**Then** o texto usa a fonte Inter (pesos regular, médio, semibold e negrito) embutida no app, sem depender de download nem de conexão, e nenhum texto fica cortado ou desalinhado pela troca de fonte

