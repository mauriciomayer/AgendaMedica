# Epic 3 Context: Notificações de Consulta

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Este épico garante que o Paciente não esqueça a consulta: um lembrete automático por e-mail é enviado 24h antes, disparado pelo próprio backend e nunca pelo app. Em paralelo, eventos de novo agendamento, cancelamento e reagendamento ficam registrados de forma confiável para um futuro consumidor de push, já que o provedor de push é uma decisão deliberadamente adiada. Depende de `appointments` (com `reminder_sent_at`) e das funções `book_appointment`/`cancel_appointment`/`reschedule_appointment` do Epic 2.

## Stories

- Story 3.1: Paciente recebe lembrete de consulta por e-mail 24h antes
- Story 3.2: Sistema registra eventos de notificação para entrega futura (novo agendamento, cancelamento)

## Requirements & Constraints

- O lembrete vai só ao Paciente (o Médico recebe apenas notificações de evento), é despachado uma única vez por Consulta no marco de 24h e não repete.
- Consulta cancelada antes do marco de 24h não gera lembrete. Só consultas `confirmed` são consideradas.
- NFR2: notificação é best-effort e nunca bloqueante. Falha de e-mail (ex.: Resend indisponível) é registrada, mas não trava o processamento das demais consultas da mesma execução nem quebra o fluxo de agendar/cancelar. Sem retry garantido nem SLA formal em v1.
- E-mail usa Resend (camada gratuita, 3.000 e-mails/mês); nenhum custo recorrente é aceitável no volume inicial. SMS está fora do escopo.
- Push não tem provedor definido: nesta fase nenhum envio real é tentado, e o registro do evento é o único efeito colateral, sem erros ou travamentos.

## Technical Decisions

- **Agendador (AD-5):** um job `pg_cron` roda a cada 5 minutos e chama `net.http_post` (`pg_net`) para invocar a Edge Function `send-reminders`, tudo dentro do projeto Supabase, sem agendador externo. Ambas as extensões existem em todos os planos, inclusive o gratuito. O job e a extensão ficam em migração SQL em `supabase/migrations`.
- **Seleção (desvio consciente do AD-5, aprovado na Story 3.1):** por "vencimento", não por janela fixa — `status = 'confirmed'`, `reminder_sent_at IS NULL` e `start_time` em `(now(), now() + 24h]`. Um job atrasado ou um envio que falhou é retomado nas execuções seguintes até o horário da consulta, em vez de perder o lembrete ao sair da janela `[24h, 24h5min)` original.
- **Reivindicação atômica antes de enviar:** todas as linhas devidas são reivindicadas numa única instrução (`UPDATE ... WHERE id IN (SELECT ... FOR UPDATE SKIP LOCKED) RETURNING ...`, limite por execução), e só então o e-mail é enviado. Isso impede duplicidade sob execuções sobrepostas.
- **Segurança:** a Function usa a `service_role` key para ler `appointments` de todos os pacientes ignorando RLS; é a única peça autorizada a isso. A chave fica só nos secrets do projeto Supabase (variável de ambiente), nunca no app Android nem versionada em texto puro.
- **Outbox (AD-12):** tabela `notification_events (id uuid PK default gen_random_uuid(), recipient_id → profiles, event_type in ('new_appointment','cancellation','reschedule'), appointment_id → appointments, created_at default now(), delivered_at)`. É escrita pelas próprias funções `SECURITY DEFINER` do Epic 2, na mesma transação da mutação da Consulta, nunca por processo separado. `delivered_at IS NULL` marca evento não entregue; um consumidor de push futuro filtra por isso e marca ao entregar (mesmo padrão de reivindicar antes de agir). Destinatário: o Médico em novo agendamento; a outra parte em cancelamento/reagendamento.
- **RLS:** nenhum cliente lê `notification_events`; é infraestrutura interna, consumida só por uma futura Edge Function de push com `service_role`.
- Nomes de schema em inglês `snake_case`; datas em `timestamptz`, comparadas instante contra instante.

## Cross-Story Dependencies

- Depende do Epic 2: `appointments.reminder_sent_at` e as funções de agendar/cancelar/reagendar. A Story 3.2 altera as funções criadas no Epic 2 (2.3, 2.4, 2.5) para inserir o evento na mesma transação, então a ligação deve ser coordenada com elas.
- As Stories 3.1 e 3.2 são independentes entre si: 3.1 usa `reminder_sent_at` e Resend; 3.2 usa `notification_events`.
- A entrega de push fica para um épico futuro, dependente da decisão do provedor.
