# Epic 2 Context: Agendamento de Consultas

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Este épico entrega o núcleo do produto: o Paciente busca médicos por especialidade e localização, vê apenas horários realmente livres, agenda respeitando a antecedência mínima e nunca entra em conflito com outro paciente, mesmo sob requisições concorrentes. Paciente ou Médico podem cancelar/reagendar dentro do prazo; depois dele, o bloqueio é total e sem exceção manual. Depende dos médicos, pacientes, auth e `doctor_schedules` criados no Epic 1; o registro de eventos de notificação é tratado no Epic 3.

## Stories

- Story 2.1: Paciente busca médicos por especialidade e localização
- Story 2.2: Paciente visualiza horários disponíveis de um médico
- Story 2.3: Paciente agenda uma consulta com garantia de que não haverá conflito de horário
- Story 2.4: Paciente cancela ou reagenda uma consulta
- Story 2.5: Médico cancela ou reagenda uma consulta de um paciente

## Requirements & Constraints

- Busca filtra por Especialidade; com GPS, ordena por distância crescente; sem GPS, fallback manual por cidade/bairro ordenado alfabeticamente pelo nome do médico. O card do médico mostra nome, especialidade, cidade e chips de convênio.
- Slots têm 15 minutos. Só é agendável quem tem 48h ou mais de antecedência em relação a agora; o carrossel de dias mostra apenas dias em que o médico atende, até 6 dias à frente.
- Nunca pode haver duas Consultas confirmadas para o mesmo médico e horário, nem sob concorrência: exatamente uma requisição vence e a outra recebe "Este horário acabou de ser reservado, escolha outro". Precisa ser coberto por teste de concorrência simulada.
- Cancelar ou reagendar só é permitido com 24h ou mais de antecedência, para Paciente e Médico igualmente. Abaixo disso o bloqueio vale também para chamadas diretas à função (erro `CONFLICT`), sem contorno.
- Reagendar atualiza a Consulta existente; nunca cria outra. Cancelar libera o horário imediatamente.
- Paciente só lê suas próprias consultas e o Médico só as suas; a disponibilidade nunca expõe dados de outros pacientes. Autenticação é obrigatória.
- Todas as regras de tempo usam o fuso fixo `America/Sao_Paulo`.
- Buscas e agenda devem parecer instantâneas para o volume-alvo (cerca de 20 usuários/semana); sem otimização para escala maior.
- As histórias de cancelar/reagendar/agendar devem deixar o evento registrado para notificação (entrega efetiva é do Epic 3; push está deferido).

## Technical Decisions

- Toda mutação de Consulta passa por funções Postgres `SECURITY DEFINER` chamadas via `.rpc()`: `book_appointment`, `cancel_appointment`, `reschedule_appointment`. RLS nega `INSERT`/`UPDATE`/`DELETE` diretos em `appointments`. As funções fixam `search_path`, obtêm identidade só de `auth.uid()` (nunca `patient_id`/`doctor_id` do chamador) e devem ficar sob `supabase/migrations`.
- Garantia final de concorrência é do schema: índice único parcial `UNIQUE (doctor_id, start_time) WHERE status = 'confirmed'`. A função captura `unique_violation` (23505) e relança como `CONFLICT:`.
- `appointments.status` é ENUM `confirmed`/`cancelled`; cancelar grava status, nunca `DELETE`. Leituras de "ocupado" e "consultas ativas" filtram por `confirmed`. Campos de data/hora são `timestamptz`; antecedência e janela de cancelamento comparam instante contra instante, nunca texto.
- A grade de horários é computada no app (`doctor_schedules` menos horários ocupados); não existem linhas de slot armazenadas. A ocupação vem de `booked_slots (doctor_id, start_time)`, projeção sem PII mantida pelo trigger `sync_booked_slots` (reage a `status` e `start_time`, então cobre reagendamento e cancelamento). Leitura liberada a qualquer autenticado.
- Realtime (`realtime-kt`) assina `booked_slots` filtrado por `doctor_id` enquanto a tela de Detalhe está aberta, para refletir conflitos sem recarregar (é push nativo, sem polling).
- RLS em `appointments`: dono por `patient_id`/`doctor_id` = `auth.uid()`. Busca lê `doctors`/`profiles` direto via RLS, sem funções.
- Erros: `RAISE EXCEPTION` com prefixos `CONFLICT:` (conflito, fora da janela, fora da antecedência) / `INVALID:` / `FORBIDDEN:`. A Repository faz o parse do prefixo; qualquer outra falha (rede, PostgREST) cai em `UNEXPECTED` e mostra mensagem genérica de "tente novamente", nunca texto bruto.
- MVVM: Composable, ViewModel/StateFlow, Repository; só `data/` fala com Supabase. Tabelas com `appointments.insurance` e `reminder_sent_at`.
- Nomes de schema em inglês `snake_case`; valores de especialidade/convênio permanecem em português.

## UX & Interaction Patterns

- Telas: Busca, Detalhe do Médico, Confirmação, Minhas Consultas, Minha Agenda. Detalhe é reaproveitado para Reagendar (cabeçalho "Reagendar consulta", botão "Confirmar novo horário", voltar leva a Minhas Consultas); confirmar atualiza a mesma Consulta.
- Grade de horários em 3 colunas. Slot ocupado ou fora da antecedência fica desabilitado com o motivo em texto acessível ("Ocupado" / "Antecedência mín. 48h"), anunciado pelo TalkBack. "Confirmar agendamento" fica desabilitado até médico, dia e horário estarem selecionados; selecionar um dia reseta o horário.
- Cancelamento é confirmado inline no card (par Sim/Não), nunca em diálogo modal. Consulta a menos de 24h mostra badge "Bloqueada" e nota "Bloqueado: faltam menos de 24h...", com Reagendar/Cancelar visíveis porém desabilitados, não escondidos.
- Minha Agenda (Médico) precisa ganhar os controles de Reagendar/Cancelar reaproveitando o padrão de card de Minhas Consultas (pendência de design registrada).
- Estados a implementar: busca sem resultados ("Nenhum médico encontrado com esses filtros."), "Sem atendimento neste dia.", sem consultas do Paciente ("Você ainda não tem consultas agendadas." + "+ Nova consulta"), sem consultas do Médico ("Nenhuma consulta agendada ainda."), além de carregamento (skeleton/spinner) e erro de rede com nova tentativa, que o protótipo não define.
- Confirmação exibe resumo (médico, especialidade, data/hora, convênio); "Consulta agendada!" é o único ponto de exclamação. Após conflito, a grade se atualiza sozinha.
- GPS é um botão de ícone ao lado do campo de texto, sem impedir digitação manual. Acessibilidade: alvos ≥ 48dp, `contentDescription` em botões de ícone, estados nunca só por cor.

## Cross-Story Dependencies

- Toda a épica depende do Epic 1 (contas, `doctors`, `doctor_schedules`, tema, base Supabase/Android).
- 2.1 leva ao Detalhe da 2.2, que alimenta a grade usada em 2.3. O modo Reagendar de 2.4 e 2.5 reutiliza a tela e a grade de 2.2/2.3.
- 2.3, 2.4 e 2.5 compartilham as funções Postgres e o trigger `sync_booked_slots`; o Realtime de 2.3 também serve o liberar de horário em cancelamentos e reagendamentos.
- As funções de 2.3, 2.4 e 2.5 registram eventos em `notification_events` (Epic 3, Story 3.2), na mesma transação; essa tabela e a ligação devem ser coordenadas com o Epic 3.
