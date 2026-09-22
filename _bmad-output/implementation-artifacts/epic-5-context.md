# Epic 5 Context: Grade de Horários — Consulta de 30 Minutos com Intervalo de 15

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Este épico corrige um mal-entendido de requisito identificado após a implementação dos Epics 1 e 2 (Sprint Change Proposal, 2026-09-22): a Consulta dura 30 minutos, não 15, e precisa de 15 minutos de intervalo obrigatório até a próxima — uma grade efetiva de 45 em 45 minutos, em vez de 15 em 15. Além disso, a janela dentro da qual cada Médico pode configurar seu próprio horário de início/fim deixa de ser livre (06h-22h) e passa a ser limitada ao horário fixo da clínica, 08h-18h. O Médico continua escolhendo livremente seus dias e seu horário de início/fim por dia (nada muda nisso) — só o intervalo oferecido e aceito muda. Esta correção substitui a premissa de "Slots de 15 minutos" usada nas Stories 1.1 (cadastro/edição de agenda do médico) e 2.2-2.5 (grade de horários, agendamento, cancelamento/reagendamento), sem exigir rollback: a lógica dessas stories já lia o bloco de horário de cada médico genericamente, então só os pontos aritméticos e de validação de limite precisam mudar.

## Stories

- Story 5.1: Sistema usa consultas de 30 minutos com intervalo de 15, dentro do horário da clínica (08h-18h)

## Requirements & Constraints

- Toda Consulta ocupa 30 minutos; entre o fim de uma e o início da próxima há 15 minutos de intervalo obrigatório — logo, horários de início possíveis para o mesmo médico ficam espaçados 45 minutos entre si.
- A clínica funciona das 08h às 18h — janela fixa e não negociável. Dentro dela, cada Médico continua escolhendo livremente seu próprio horário de início/fim de atendimento por dia, editável a qualquer momento; a única mudança é que esse intervalo precisa caber inteiramente dentro de 08h-18h (antes era 06h-22h).
- Um horário de início/fim de médico fora de 08h-18h é rejeitado tanto na camada de aplicação (Edge Function) quanto no banco (constraint), nunca só num dos dois lados.
- Uma posição de início de consulta só é válida quando (a) está alinhada à grade de 45 min contada a partir do `start_time` do bloco daquele médico naquele dia, e (b) a consulta inteira de 30 min cabe antes do `end_time` desse bloco. Isso vale tanto para a grade mostrada ao Paciente quanto para qualquer chamada direta às funções de agendar/reagendar — uma chamada fora da grade ou que ultrapasse o fim do bloco é rejeitada com erro `INVALID:`, sem exceção manual possível.
- Todas as regras de tempo continuam no fuso fixo `America/Sao_Paulo`, comparando sempre instante contra instante (nunca texto), consistente com a antecedência mínima de 48h e a janela de cancelamento de 24h já existentes.
- Critério de sucesso: a função de validação de horário agendável rejeita qualquer posição fora da grade de 45 min do bloco do médico ou que não caiba antes do fim do bloco; uma constraint no banco impede um horário de médico fora de 08h-18h mesmo via SQL direto; a grade do app mostra os horários esperados para o bloco configurado de cada médico; o cadastro/edição de médico oferece só 08:00-18:00 no seletor de horário.

## Technical Decisions

- Nova `CHECK constraint` em `doctor_schedules` (nova migração) exigindo `start_time >= '08:00' AND end_time <= '18:00'` — a garantia final vive no armazenamento, não só na Edge Function (mesmo princípio de NFR1). Antes de criar a constraint, dados de teste já existentes fora da janela são normalizados (recorte para caber em 08:00-18:00; qualquer linha que ficasse com `start_time >= end_time` após o recorte vira `08:00`-`18:00` como fallback seguro).
- A função Postgres que valida se um horário é agendável (`assert_slot_bookable`, já usada internamente por `book_appointment` e `reschedule_appointment`) é recriada com a nova aritmética: grade de 45 min a partir do `start_time` do bloco do dia daquele médico especificamente, com a consulta de 30 min tendo que caber antes do `end_time`. Continua lendo o bloco genericamente por `doctor_id`+`weekday`, como já fazia — nenhuma mudança adicional é necessária por causa do limite 08h-18h. `book_appointment`, `cancel_appointment` e `reschedule_appointment` não precisam ser recriadas.
- Edge Function de cadastro de médico (`register-doctor`) passa a rejeitar (`INVALID:`) qualquer `startTime < "08:00"` ou `endTime > "18:00"`, além da validação de formato e `startTime < endTime` já existente.
- No app, a lista de horários oferecida no cadastro/edição de agenda do médico estreita de "06:00 a 22:00" para "08:00 a 18:00" (mesmo passo de 30 min); nenhuma tela ou campo é removido, só a lista de opções.
- No domínio de geração de slots do app, o passo de geração muda de 15 para 45 minutos e a condição de "cabe no bloco" muda de "posição < fim" para "posição + 30 min ≤ fim"; duração da consulta (30 min) e intervalo entre consultas (15 min) passam a ser constantes nomeadas explicitamente, em vez de implícitas no passo da grade.
- Nenhuma mudança é necessária em `booked_slots`, no índice único parcial de `appointments` por médico/horário, nem na assinatura Realtime dessa tabela — como a nova grade garante 45 min de espaçamento entre horários canônicos (maior que os 30 min de duração), a checagem por instante exato continua suficiente para impedir sobreposição.
- Scripts de teste que exercitam concorrência e cancelamento/reagendamento contra o banco precisam de ajuste: médicos de teste cadastrados com agendas fora de 08h-18h (ex.: `00:00`-`23:59`) passam a ser rejeitados pelo cadastro, e os horários de consulta usados nos testes precisam cair na nova grade de 45 min a partir do início real configurado para o médico de teste em questão.

## Cross-Story Dependencies

- Depende da tabela `doctor_schedules` e das funções Postgres de agendamento/cancelamento/reagendamento já criadas nos Epics 1 e 2 — este épico as ajusta, não as recria (exceto a função de validação de horário agendável, que é recriada).
- Substitui a premissa de "Slots de 15 minutos" usada na Story 1.1 (cadastro/edição de horário do médico) e nas Stories 2.2-2.5 (grade de horários, agendamento, cancelamento/reagendamento pelo Paciente e pelo Médico). Essas specs permanecem congeladas como histórico, com uma nota de erratum apontando para este épico — não são reescritas.
- Sem impacto nos Epics 3 (notificações) e 4 (identidade visual), que não dependem da granularidade da grade de horários.
