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
FR2: Um Médico define exatamente uma Especialidade (lista fixa de 6), um ou mais Convênios (lista fixa de 4, incluindo "Particular") e os dias/horários em que atende. Especialidade e Convênios são imutáveis após a criação do perfil; dias/horários são editáveis a qualquer momento.
FR3: Um usuário pode se cadastrar como Paciente informando os dados definidos no design (nome, e-mail, senha), sem exigência de vínculo com Convênio no cadastro.
FR4: Um Paciente pode buscar Médicos filtrando por Especialidade e localização (GPS automático, com fallback de busca manual por cidade/bairro).
FR5: Um Paciente pode visualizar a Agenda de um Médico específico, vendo apenas Slots de 15 minutos livres e respeitando a Antecedência mínima de 48h.
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
NFR6: Todas as regras de tempo (Slots de 15min, antecedência mínima de 48h, janela de cancelamento de 24h) usam um fuso horário único e fixo (`America/Sao_Paulo`) — sem suporte a médico e paciente em fusos diferentes.
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
**Then** minha agenda semanal é salva em `doctor_schedules`
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
**Then** vejo blocos de 15 minutos, com os horários a menos de 48h de antecedência desabilitados e com o motivo "Antecedência mín. 48h" (FR5, FR6)

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
