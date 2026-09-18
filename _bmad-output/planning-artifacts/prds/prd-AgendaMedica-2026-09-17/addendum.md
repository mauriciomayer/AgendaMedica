# Addendum — Agenda Médica

Conteúdo técnico-how que não pertence ao PRD. Decisões tomadas na Arquitetura (`bmad-architecture`) — ver `_bmad-output/planning-artifacts/architecture/architecture-AgendaMedica-2026-09-17/ARCHITECTURE-SPINE.md` para o detalhe completo.

## Backend (revisão 2026-09-17)

**Status:** resolvido. Trocado de Firebase para **Supabase** (Postgres + Auth + Edge Functions + Realtime) por decisão do usuário — evita a exigência de cartão de crédito que o Firebase impõe para habilitar Cloud Functions (Supabase não exige cartão em nenhuma camada do free tier).

## Mecanismo de bloqueio de conflito de agenda (referenciado por RF-7)

**Status:** resolvido (AD-1 do spine de arquitetura). Um índice único parcial do Postgres (`UNIQUE (doctor_id, start_time) WHERE status = 'confirmed'`) torna um agendamento duplicado uma violação de constraint do próprio banco — mais forte que uma checagem em código, pois nem um bug na função de agendamento consegue burlar.

## Atualização em tempo real da grade de horários (referenciado por RF-7)

**Status:** resolvido (AD-10 do spine de arquitetura). Realtime nativo do Supabase (baseado em replicação lógica do Postgres) sobre uma tabela sem dados pessoais (`booked_slots`).

## Provedor técnico de notificações (push/e-mail)

- **E-mail:** resolvido — [Resend](https://resend.com), camada gratuita de 3.000 e-mails/mês.
- **Push:** **em aberto, deliberadamente.** Por decisão do usuário, vira uma decisão a ser tomada no momento oportuno (ex.: junto com o épico de notificações), não bloqueando o restante da arquitetura.
- **SMS:** removido do escopo — não há provedor gratuito de envio de SMS de texto livre, e não compensa o custo/esforço para um MVP de portfólio.

## Agendamento de tarefa do lembrete de 24h (novo, específico do Supabase)

O Supabase não oferece `pg_cron` (agendador dentro do banco) na camada gratuita — só em planos pagos. **Status:** resolvido (AD-5) com um agendador externo (GitHub Actions, gratuito) chamando uma Edge Function a cada 5 minutos.
