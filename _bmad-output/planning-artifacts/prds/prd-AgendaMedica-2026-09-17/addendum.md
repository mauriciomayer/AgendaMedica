# Addendum — Agenda Médica

Conteúdo técnico-how que não pertence ao PRD. Decisões tomadas na Arquitetura (`bmad-architecture`) — ver `_bmad-output/planning-artifacts/architecture/architecture-AgendaMedica-2026-09-17/ARCHITECTURE-SPINE.md` para o detalhe completo.

## Mecanismo de bloqueio de conflito de agenda (referenciado por RF-7)

**Status:** resolvido (AD-1 do spine de arquitetura). Toda escrita de Consulta passa por uma Cloud Function que executa em transação atômica do Firestore — não uma constraint de banco relacional, mas cumpre o mesmo papel: exatamente uma requisição concorrente é aceita, a outra é rejeitada.

## Atualização em tempo real da grade de horários (referenciado por RF-7)

**Status:** resolvido (AD-10 do spine de arquitetura). O app assina um listener em tempo real do Firestore sobre uma projeção sem dados pessoais (`doctors/{uid}.bookedSlots`) — é push nativo do SDK, não polling.

## Provedor técnico de notificações (push/e-mail)

**Status:** resolvido.
- **Push:** Firebase Cloud Messaging — canal garantido (AD-5).
- **E-mail:** [Resend](https://resend.com) — camada gratuita de 3.000 e-mails/mês, mais que suficiente para o volume do MVP (20 usuários/semana). Best-effort, sem SLA formal.
- **SMS:** removido do escopo (decisão do usuário, 2026-09-17) — não há provedor gratuito de envio de SMS de texto livre, e não compensa o custo/esforço para um MVP de portfólio. RF-10 e o app não incluem mais SMS em nenhuma versão.
