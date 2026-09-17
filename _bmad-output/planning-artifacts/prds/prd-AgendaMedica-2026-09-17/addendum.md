# Addendum — Agenda Médica

Conteúdo técnico-how que não pertence ao PRD, mas que a Arquitetura deve resolver. Cada item referenciado a partir de `prd.md`.

## Mecanismo de bloqueio de conflito de agenda (referenciado por RF-7)

RF-7 exige que dois agendamentos nunca coexistam no mesmo Slot do mesmo Médico, mesmo sob requisições concorrentes. A implementação exata é uma decisão de Arquitetura — candidatos:
- Constraint de unicidade no banco de dados sobre (Médico, Slot) — mais simples, falha rápido no nível de dados.
- Transação com lock pessimista sobre o Slot durante a confirmação.
- Lock otimista com retry (versão/timestamp no Slot).

Qualquer opção deve satisfazer: exatamente uma requisição concorrente é aceita; a outra é rejeitada com mensagem clara; nenhuma janela de tempo aceita as duas.

**Status:** aberto — decidir na etapa de Arquitetura (`bmad-architecture`).

## Atualização em tempo real da grade de horários (referenciado por RF-7)

Após um Slot ser ocupado, os demais Pacientes com a grade aberta devem vê-lo como ocupado sem recarregar manualmente. Mecanismo (polling periódico vs. push/websocket) é decisão de Arquitetura.

**Status:** aberto — decidir na etapa de Arquitetura (`bmad-architecture`).

## Provedor técnico de notificações (push/SMS/e-mail)

RF-10 exige lembrete por push, SMS e e-mail. O provedor técnico específico (ex.: Firebase Cloud Messaging para push; gateway de SMS nacional pré-pago ou franquia gratuita de provedor de autenticação, conforme já indicado no PRD; serviço de e-mail transacional) é decisão de Arquitetura.

**Status:** aberto — decidir na etapa de Arquitetura (`bmad-architecture`).
