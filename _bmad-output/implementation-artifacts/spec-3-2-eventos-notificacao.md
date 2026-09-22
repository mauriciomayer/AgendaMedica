---
title: 'Sistema registra eventos de notificação para entrega futura'
type: 'feature'
created: '2026-09-22'
status: 'done'
baseline_commit: '7e256de9a94adb8f48d22d03fe469af3df387225'
route: 'oneshot'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** A Story 3.2 pede que agendar/cancelar/reagendar registrem eventos em `notification_events` de forma confiável para um futuro consumidor de push (AD-12). Isso já foi implementado como efeito colateral das Stories 2.3-2.5 (`book_appointment`, `cancel_appointment`, `reschedule_appointment` inserem o evento na mesma transação, migrações `0005`/`0006`), mas nunca foi auditado formalmente contra as 4 acceptance criteria do épico nem contra o requisito de nenhum cliente conseguir ler/escrever a tabela diretamente.

**Approach:** Auditar a implementação existente linha a linha contra as 4 acceptance criteria de `epics.md` (Story 3.2) e a cobertura já existente em `supabase/tests/concurrency-test.mjs` e `cancel-reschedule-test.mjs`. Fechar a única lacuna real: provar por teste automatizado que nenhum cliente (anônimo, paciente ou médico autenticado) consegue ler ou escrever `notification_events` via REST — hoje garantido só por `enable row level security` sem nenhuma política, nunca exercitado por um teste. Nenhuma migração, função ou Edge Function nova é necessária; esta história é verificação e fechamento de lacuna de teste.

</frozen-after-approval>

## Implementation Notes

Auditoria confirmou que as 4 acceptance criteria da Story 3.2 (`epics.md`) já estavam implementadas e cobertas por testes existentes, sem necessidade de tocar em código de produção:

- AC1 (evento `new_appointment` para o médico, mesma transação): `book_appointment` em `supabase/migrations/0005_appointments.sql:168-169` e `0006_cancel_reschedule.sql:97-98`; provado por `supabase/tests/concurrency-test.mjs:142-145`.
- AC2 (evento `cancellation`/`reschedule` para a outra parte, mesma transação): `cancel_appointment`/`reschedule_appointment` em `0006_cancel_reschedule.sql:140-145,202-207`; provado por `supabase/tests/cancel-reschedule-test.mjs` (recipient_id correto para ação do paciente e do médico, nenhum evento em chamada rejeitada).
- AC3 (colunas prontas para um futuro consumidor de push filtrar por `delivered_at IS NULL`): schema de `notification_events` em `0005_appointments.sql:44-51`; `delivered_at` nunca é escrito em nenhum caminho hoje, confirmado por leitura do código (nenhuma função grava esse campo).
- AC4 (nenhuma tentativa real de envio, registro é o único efeito colateral): confirmado por leitura — nenhuma chamada de rede é feita nas três funções.

Única lacuna real encontrada: nada provava que um cliente (anônimo, paciente ou médico autenticado) não consegue ler ou escrever `notification_events` diretamente via REST — a tabela tem `enable row level security` sem nenhuma política e todos os grants revogados de `anon`/`authenticated` (`0005_appointments.sql:53-54`), mas isso nunca foi exercitado. Fechado com `supabase/tests/notification-events-test.mjs` (+ script npm `test:notification-events`): reserva um agendamento real (via `book_appointment`, com `apikey`/token válidos em todos os casos — o que é negado é o papel, não a ausência de credencial) para gerar uma linha genuína, então tenta GET/POST/PATCH/DELETE como anônimo, paciente e médico (todas as combinações de papel × método) — todas negadas pelo revoke da tabela (o teste registra o código HTTP observado mas não trava numa combinação específica de código por papel, mesma convenção de `cancel-reschedule-test.mjs`), e a linha permanece intacta. `node supabase/tests/notification-events-test.mjs` → ALL CHECKS PASSED.

Nenhuma migração, função ou Edge Function foi alterada.

## Review Triage Log

Blind Hunter (1 revisor, sem código de produção alterado — achados só sobre a spec e o novo teste).

| Achado | Verdict | Evidência / ação |
|---|---|---|
| Faltava `baseline_commit` no frontmatter da spec (todas as outras têm) | medium | Real, correção trivial. Corrigido: `7e256de9a94adb8f48d22d03fe469af3df387225` (HEAD ao iniciar a 3.2) |
| Matriz de negação REST desigual: PATCH só como médico, DELETE só como paciente; texto dizia "todos negados" para os 4 métodos × 3 papéis sem provar isso | medium | Real. Corrigido: loop agora cobre GET/POST/PATCH/DELETE × anônimo/paciente/médico (9 combinações de escrita + 3 de leitura), todas verificadas |
| `restCall()` mandava `Prefer: "undefined"` (string literal) em vez de omitir o header fora do POST | low | Real bug (inofensivo contra o PostgREST hoje, mas o código não fazia o que a ternária sugeria). Corrigido: `Prefer` só é setado para POST |
| Notas de implementação alegavam códigos HTTP exatos por papel sem o teste travar neles, e diziam "anônimo sem apikey" quando na verdade `anon` sempre envia `apikey`/token de sessão anônima válidos (o que é negado é o grant da tabela, não a ausência de credencial) | medium | Real overclaim de documentação, sem bug de código. Corrigido: texto reescrito para não fixar códigos exatos por papel e descrever corretamente o que é negado |
| Nenhum usuário autenticado "estranho" (sem relação com a consulta) foi testado, só as duas partes da consulta | low | Real, mas rejeitado: o revoke é por tabela inteira (não por linha), então paciente/médico já provam o mesmo limite que um terceiro provaria hoje; a correção exigiria registrar/logar um usuário a mais só para redundância. Só passaria a valer a pena se uma migração futura trocasse o revoke geral por políticas de RLS por linha |
| 4ª cópia quase idêntica dos helpers de teste (`sql`/`fn`/`login`/`rpc`/`saoPauloSlot`/`check`) entre os scripts contra o projeto hospedado, sem módulo compartilhado | medium (dívida) | Real, mas a correção (extrair `supabase/tests/_helpers.mjs`) tocaria nos 3 scripts já commitados de histórias anteriores — não é uma correção simples para o escopo desta história. Adiado em `deferred-work.md` |
| Workflow ainda não tinha chegado a Finalize/Commit no momento da revisão | n/a | Observação de processo, não achado de conteúdo — resolvido ao concluir os passos restantes deste workflow (este documento) |

