---
title: Agenda Médica
status: final
created: 2026-09-17
updated: 2026-09-17
sources:
  - _bmad-output/planning-artifacts/prds/prd-AgendaMedica-2026-09-17/prd.md
  - _bmad-output/planning-artifacts/prds/prd-AgendaMedica-2026-09-17/addendum.md
  - imports/claude-design-template.html (protótipo Claude Design, importado 2026-09-17)
  - imports/claude-design-logic.js
---

# Agenda Médica — Experience Spine

> Android nativo (Material 3), app único com dois perfis de acesso (Paciente / Médico) via login — ver PRD §Plataforma. Pareado com `DESIGN.md`. Termos (Paciente, Médico, Consulta, Slot, Convênio, Especialidade, Antecedência mínima, Janela de cancelamento) seguem o Glossário do PRD (§3) — usados aqui de forma idêntica.

## Foundation

Mobile único (Android, Material 3 como convenção visual de referência — ver `DESIGN.md`). Um único app com duas experiências completamente distintas por trás do login: **Paciente** (busca e agenda consultas) e **Médico** (gerencia perfil e agenda). O papel é definido no cadastro/login da conta, não trocável livremente dentro do app por um mesmo usuário — a alternância "Paciente/Médico" vista no protótipo de design é uma **conveniência de demonstração do protótipo**, não um controle real do app final (na v1 real, cada conta tem um papel fixo).

## Information Architecture

| Superfície | Alcançada a partir de | Propósito |
|---|---|---|
| Login | Abertura do app (não autenticado) | Autenticar Paciente ou Médico |
| Recuperar senha | Login → "Esqueci minha senha" | Solicitar link de redefinição por e-mail (realiza RF-11) |
| Criar conta (escolha) | Login → "Criar conta" | Escolher "Sou paciente" ou "Sou médico" |
| Cadastro de Paciente | Escolha → "Sou paciente" | Autocadastro de Paciente (RF-3) |
| Cadastro de Médico | Escolha → "Sou médico" | Autocadastro de Médico + perfil profissional (RF-1, RF-2) |
| Busca | Login (Paciente autenticado) | Buscar Médicos por Especialidade + localização (RF-4) |
| Detalhe do Médico | Busca → toque em um card | Ver Convênios, escolher dia/horário e agendar (RF-5, RF-6, RF-7) — também reaproveitada para **Reagendar** (ver Component Patterns) |
| Confirmação | Detalhe → agendamento confirmado | Confirma o agendamento, ponte para Minhas Consultas |
| Minhas Consultas | App bar "Minhas consultas" (Busca) ou pós-confirmação | Listar, cancelar (RF-8/RF-9) e reagendar consultas do Paciente |
| Minha Agenda | Login (Médico autenticado/cadastrado) | Perfil do Médico + próximas consultas — **hoje somente leitura** (ver Open Items) |

Sem menu lateral, sem tab bar persistente — navegação linear por ações (toque em card, botão "voltar" contextual no app bar). Modal/confirmação inline nunca empilha mais de um nível (ex.: confirmar cancelamento aparece dentro do próprio card, não como diálogo separado).

→ Referência de composição: `imports/claude-design-template.html` (protótipo interativo completo, todas as telas). Spine vence em caso de conflito.

## Voice and Tone

Microcopy. Voz e tom de marca vivem em `DESIGN.md.Brand & Style`.

| Do | Don't |
|---|---|
| "Sem validação de CRM — seu perfil fica visível na busca assim que você concluir o cadastro." | "Parabéns! Seu perfil já está no ar! 🎉" |
| "Conflitos de horário no mesmo período são bloqueados automaticamente pelo sistema." | "Não se preocupe, cuidamos de tudo pra você!" |
| "Bloqueado: faltam menos de {toleranciaLabel}h — não é mais possível cancelar ou reagendar." | "Ops, muito tarde para cancelar 😅" |
| "Consulta agendada!" (único ponto de exclamação, reservado para o momento de sucesso) | Exclamação em mensagens de erro, aviso ou rotina |
| Frases curtas, diretas, que explicam a regra quando ela restringe uma ação | Justificativas longas ou tom de desculpa por uma regra do sistema |

## Component Patterns

Comportamental. Especificação visual vive em `DESIGN.md.Components`.

| Componente | Uso | Regras comportamentais |
|---|---|---|
| Tab de papel (Paciente/Médico) | Login | Alterna o formulário de login exibido; não muda dados já digitados no outro papel. |
| Card de médico (lista de Busca) | Busca | Toque abre Detalhe. Sempre mostra iniciais, nome, especialidade, cidade e chips de Convênio — nunca um card "incompleto". |
| Carrossel de dias (Detalhe) | Detalhe/Reagendar | Mostra só os próximos dias em que o Médico atende (filtrado por dia da semana definido em RF-2), até 6 dias à frente. Selecionar um dia reseta o horário selecionado. |
| Grade de horários (Detalhe) | Detalhe/Reagendar | 3 colunas. Um Slot é clicável somente se não estiver ocupado **e** respeitar a Antecedência mínima (RF-6); Slot não clicável mostra o motivo ("Ocupado" ou "Antecedência mín. {antecedenciaLabel}h") de forma acessível, não só visual. |
| Botão "Confirmar agendamento" (barra inferior) | Detalhe | Desabilitado até Médico + dia + horário estarem selecionados. Rótulo muda para "Confirmar novo horário" quando a tela está em modo **Reagendar**. |
| **Reagendar (reuso da tela Detalhe)** | Minhas Consultas → Reagendar | Mesma superfície de Detalhe, mas em modo de edição de uma Consulta existente: o cabeçalho muda para "Reagendar consulta", o botão de voltar retorna para Minhas Consultas (não para Busca), e confirmar **atualiza a Consulta existente** — não cria uma nova nem duplica (realiza RF-8; ver PRD §4.5). |
| Card de consulta (Minhas Consultas) | Minhas Consultas | Mostra badge de status (Confirmada/Bloqueada), e ações Reagendar/Cancelar — ambas desabilitadas quando a Consulta está dentro do Bloqueio de cancelamento (RF-9). |
| Confirmação de cancelamento inline | Card de consulta | Ao tocar "Cancelar", o próprio card expande um par de botões Sim/Não — nunca um diálogo modal separado. "Sim" remove a Consulta; "Não" fecha a confirmação sem ação. |
| Chip de alternância (Convênio/Dia, Cadastro de Médico) | Cadastro de Médico | Multi-seleção — toque alterna estado ativo/inativo; sem limite mínimo/máximo declarado além de RF-2 (pelo menos 1 Convênio e 1 dia para habilitar "Criar perfil"). |
| Botão de ação principal (largura cheia) | Cadastros, Login, Recuperação | Desabilitado até todos os campos obrigatórios da tela estarem preenchidos (ver State Patterns). |

## State Patterns

| Estado | Superfície | Tratamento |
|---|---|---|
| Campos incompletos | Cadastro Paciente / Médico, Login | Botão de ação principal desabilitado (`{colors.surface-disabled}` / `{colors.ink-disabled}`); nenhuma mensagem de erro até o usuário tentar submeter — o botão desabilitado já comunica o estado. |
| Login inválido (Médico sem cadastro) | Login | Mensagem inline abaixo dos campos: "Nenhum cadastro encontrado para este médico. Crie sua conta." — não bloqueia nova tentativa. |
| Recuperação enviada | Recuperar senha | Mensagem de confirmação neutra ("Se o e-mail informado existir, enviamos um link...") — **não confirma nem nega se o e-mail existe**, por padrão de segurança. Botão único "Voltar ao login". |
| Busca sem resultados | Busca | "Nenhum médico encontrado com esses filtros." — sem sugestão de ação alternativa. |
| Sem atendimento no dia selecionado | Detalhe | "Sem atendimento neste dia." no lugar da grade de horários. |
| Slot ocupado | Detalhe | Botão do horário desabilitado, estilo neutro, motivo "Ocupado" exposto via `title`/descrição acessível. |
| Slot fora da antecedência mínima | Detalhe | Mesma tratativa visual do slot ocupado, motivo "Antecedência mín. {N}h". |
| Consulta bloqueada para cancelamento | Minhas Consultas | Badge "Bloqueada" + nota inline explicando o motivo; botões Reagendar/Cancelar visíveis mas desabilitados (não escondidos — o Paciente deve entender que existe a ação, só não está mais disponível). |
| Sem consultas (Paciente) | Minhas Consultas | "Você ainda não tem consultas agendadas." + botão "+ Nova consulta". |
| Sem consultas (Médico) | Minha Agenda | "Nenhuma consulta agendada ainda." |
| Confirmação de cancelamento aberta | Card de consulta | Estado local por card — abrir a confirmação em um card não fecha a de outro card (múltiplos podem, em teoria, estar abertos). |

**[NOTE FOR UX]** O protótipo é client-side (sem rede real), então não define estados de **carregamento** (skeleton/spinner) nem de **erro de rede/servidor**. Isso precisa ser desenhado antes da implementação — ver Open Items.

## Interaction Primitives

- Toque para selecionar/agir em tudo (sem gestos customizados, sem swipe além do scroll nativo).
- Confirmação destrutiva (cancelar consulta) é **inline no próprio card** (Sim/Não), nunca um diálogo modal do sistema.
- Estado desabilitado sempre acompanha um motivo perceptível (texto ou `title`), nunca um botão silenciosamente inerte.
- Scroll horizontal apenas no carrossel de dias; todo o resto é vertical.
- Seleção de localização por GPS é um botão de ícone dedicado ao lado do campo de texto — preenche o campo, não substitui a possibilidade de digitar manualmente (RF-4).
- **Banido:** diálogos modais do sistema para confirmação, animações de entrada elaboradas, gamificação (streak, badge de conquista) — não cabem no tom "utilitário clínico" do produto.

## Accessibility Floor

Comportamental. Contraste visual vive em `DESIGN.md`.

- Alvos de toque ≥ 48dp (convenção Android), especialmente nos botões de ícone (voltar, GPS) que hoje são pequenos divs decorativos no protótipo — precisam de `contentDescription`/rótulo acessível ("Voltar", "Usar minha localização") na implementação real.
- Nenhum estado é comunicado só por cor: badges de status sempre pareiam cor + texto ("Confirmada"/"Bloqueada"); motivo de slot desabilitado é texto, não só estilo visual.
- Motivo de campo/botão desabilitado deve ser anunciado por leitor de tela (TalkBack), não só exposto como tooltip visual (`title`) — atenção especial na grade de horários e nos botões de ação da Consulta.
- Todo campo de formulário tem rótulo visível associado programaticamente (hoje são `<div>` de rótulo acima do input no protótipo — precisa virar `<label>`/`contentDescription` real na implementação).
- Ordem de foco segue a ordem de leitura visual em cada tela.

## Key Flows

### Flow 1 — Fernanda agenda uma consulta (realiza UJ-1 do PRD)

1. Fernanda abre o app já autenticada como Paciente; cai em **Busca**.
2. Filtra por Especialidade "Dermatologia" e ativa localização por GPS.
3. Toca no card da Dra. Ana → **Detalhe**.
4. Escolhe um dia no carrossel (só dias em que a médica atende aparecem) → a grade de horários atualiza.
5. Toca em um horário disponível (dentro da antecedência mínima, não ocupado) → botão inferior "Confirmar agendamento" habilita.
6. Toca em confirmar.
7. **Clímax:** tela **Confirmação** — check verde, resumo da consulta (médica, especialidade, data/hora, convênio) — o slot já some da grade para qualquer outro Paciente.
8. Resolução: toca "Ver minhas consultas" → **Minhas Consultas** mostra o novo card com badge "Confirmada".

Falha: se outro Paciente reservar o mesmo horário um instante antes, a confirmação (passo 6) deve falhar com aviso e atualizar a grade — comportamento especificado no PRD (RF-7), ainda **não simulado no protótipo atual** (que roda local, sem concorrência real) — ver Open Items.

### Flow 2 — Dr. Ricardo se cadastra e monta a agenda (realiza UJ-2)

1. Ricardo abre o app, não autenticado, escolhe papel "Médico" no Login e toca "Criar conta".
2. Tela **Escolha**: toca "Sou médico".
3. **Cadastro de Médico**: preenche nome/e-mail/senha, escolhe Especialidade "Cardiologia" (lista fechada), marca convênios (chips), marca dias de atendimento (chips) e horário início/fim.
4. Botão "Criar perfil e começar a atender" habilita só quando todos os campos obrigatórios estão preenchidos.
5. **Clímax:** ao confirmar, cai direto em **Minha Agenda** já autenticado — sem etapa de aprovação, perfil já buscável.
6. Resolução: vê o card do próprio perfil + lista de próximas consultas (inicialmente vazia até pacientes agendarem).

### Flow 3 — Fernanda reagenda uma consulta

1. Fernanda abre **Minhas Consultas**, vê a consulta com a Dra. Ana.
2. Toca "Reagendar" (habilitado — está fora da janela de bloqueio).
3. Cai em **Detalhe** em modo reagendamento (cabeçalho "Reagendar consulta").
4. Escolhe novo dia e horário.
5. Toca "Confirmar novo horário".
6. **Clímax:** volta para **Confirmação**, mesma Consulta agora com o novo horário — nenhuma consulta duplicada é criada.

### Flow 4 — Fernanda tenta cancelar depois do prazo (realiza UJ-3, caso extremo)

1. Fernanda abre **Minhas Consultas**; uma consulta está a poucas horas de distância.
2. O card já mostra badge "Bloqueada" e a nota "Bloqueado: faltam menos de 24h — não é mais possível cancelar ou reagendar."
3. Os botões Reagendar/Cancelar aparecem visíveis, mas desabilitados.
4. **Clímax:** não há nenhuma ação possível — nem para Fernanda, nem (segundo o PRD) para o Médico. A UI já entrega essa certeza sem exigir que ela tente tocar para descobrir.

### Flow 5 — Paciente esqueceu a senha (realiza RF-11)

1. Na tela **Login**, toca "Esqueci minha senha".
2. **Recuperar senha**: informa e-mail, toca "Enviar link de recuperação".
3. **Clímax:** mensagem neutra de confirmação ("Se o e-mail informado existir, enviamos um link...") — sem revelar se a conta existe.
4. Toca "Voltar ao login" e o fluxo termina ali (o clique no link do e-mail, fora do app, está fora de escopo desta spine).

## Open Items

- **[NOTE FOR UX]** Minha Agenda (Médico) ainda não tem os controles de Reagendar/Cancelar exigidos pelo PRD (RF-8) — pendente de adicionar ao design (decisão registrada no memlog em 2026-09-17: adicionar depois, não remover do PRD).
- **[NOTE FOR UX]** Estados de carregamento e erro de rede/servidor não existem no protótipo (que é local/sem backend) — precisam de tratamento antes da implementação (Arquitetura/Build).
- **[NOTE FOR UX]** Simulação visual do conflito de agenda sob concorrência (RF-7) não está no protótipo atual — o comportamento descrito no Flow 1 (falha + atualização da grade) é uma extrapolação do PRD, não algo já desenhado clique a clique.
