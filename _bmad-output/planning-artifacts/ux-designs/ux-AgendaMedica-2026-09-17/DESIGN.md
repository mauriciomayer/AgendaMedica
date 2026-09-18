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
colors:
  surface-canvas: 'oklch(0.97 0.015 150)'
  surface-card: '#FFFFFF'
  border-hairline: 'oklch(0.92 0.01 75)'
  border-input: 'oklch(0.88 0.01 75)'
  border-input-subtle: 'oklch(0.85 0.01 75)'
  ink-primary: 'oklch(0.25 0.02 75)'
  ink-secondary: 'oklch(0.5 0.02 75)'
  ink-tertiary: 'oklch(0.55 0.02 75)'
  ink-disabled: 'oklch(0.7 0.01 75)'
  surface-subtle: 'oklch(0.95 0.01 75)'
  surface-input: 'oklch(0.98 0.005 75)'
  surface-disabled: 'oklch(0.85 0.01 75)'
  accent-primary: '#3B6FE0'
  accent-primary-tint: 'oklch(0.94 0.05 250)'
  accent-primary-tint-soft: 'oklch(0.95 0.02 250)'
  accent-primary-ink-soft: 'oklch(0.4 0.1 250)'
  success-ink: 'oklch(0.45 0.1 150)'
  success-bg: 'oklch(0.94 0.05 150)'
  success-ink-muted: 'oklch(0.4 0.08 150)'
  warning-ink: 'oklch(0.5 0.12 40)'
  warning-bg: 'oklch(0.95 0.04 40)'
  danger-ink: 'oklch(0.5 0.14 25)'
  danger-border: 'oklch(0.85 0.05 25)'
typography:
  font-family: "'Inter', system-ui, sans-serif"
  weights: [400, 500, 600, 700]
  display:
    size: 28px
    weight: 400
    note: 'App bar grande (Material 3 large top app bar) — não usado nas telas atuais do app, reservado.'
  heading:
    size: 18-19px
    weight: 700
    note: 'Título do app bar padrão; título "Consulta agendada!"'
  label:
    size: 15-16px
    weight: 600-700
    note: 'Nome do médico/paciente em cards, opções de escolha de perfil.'
  body:
    size: 14-14.5px
    weight: 400-600
    note: 'Texto de inputs, botões primários.'
  body-sm:
    size: 13-13.5px
    weight: 400-600
    note: 'Texto secundário em cards, botões de ação secundária.'
  caption:
    size: 12-12.5px
    weight: 400-600
    note: 'Labels de campo, subtítulos de header, chips.'
  micro:
    size: 11-11.5px
    weight: 600-700
    note: 'Badges de status, dias da semana no seletor de data.'
rounded:
  pill: 100px
  lg: 14px
  md: 10-12px
  circle: 50%
spacing:
  '1': 4px
  '1.5': 6px
  '2': 8px
  '2.5': 10px
  '3': 12px
  '3.5': 14px
  '4': 16px
  '4.5': 18px
  '5': 20px
components:
  button-primary: 'pill ou md, bg accent-primary, texto branco, weight 600-700'
  button-outline: 'bg branco, borda accent-primary ou neutra, texto colorido'
  button-danger-outline: 'bg branco, borda danger-border, texto danger-ink'
  chip-toggle: 'pill, ativo: borda 2px accent-primary + bg accent-primary-tint + texto accent-primary; inativo: borda border-input + bg branco + texto ink-secondary'
  chip-tag: 'pill, bg success-bg, texto success-ink-muted — usado para Convênio'
  badge-status: 'pill, texto micro weight 700 — confirmada (success) / bloqueada (warning)'
  card: 'bg branco, borda border-hairline, rounded lg, padding 14px'
  avatar-initials: 'circle, bg accent-primary, texto branco weight 600'
  input: 'rounded md, borda border-input, padding ~11px 12px, font 14px'
---

## Brand & Style

O Agenda Médica projeta calma e confiabilidade clínica: fundo levemente esverdeado (não branco puro, não azul-hospital), cartões brancos bem definidos por borda fina (sem sombra pesada), e uma única cor de marca — um azul confiável (`#3B6FE0`) — reservada para ações primárrias e seleção. O tom é direto e funcional, sem ilustração ou personagem de marca: a interface deve parecer um utilitário sério, não um app de consumo lúdico. Paleta e tipografia seguem convenções Material 3 (Android nativo), consistente com a decisão do PRD de v1 ser Android-only.

## Colors

- **`surface-canvas`** (`oklch(0.97 0.015 150)`) — fundo de tela, um verde muito pálido quase neutro. Não é branco puro: dá uma sensação "clínica calma" sem ser frio.
- **`surface-card`** (`#FFFFFF`) — todos os cards, inputs e o cabeçalho flutuam em branco puro sobre o canvas verde-pálido, criando contraste suave sem sombra.
- **Neutros de texto** (`ink-primary` → `ink-disabled`, escala de cinza morno, hue 75) — hierarquia de texto do mais escuro (títulos) ao mais claro (desabilitado).
- **`accent-primary`** (`#3B6FE0`) — a única cor de marca. Usada em: botões primários, links, tab ativa, borda de seleção (data/horário escolhido), texto de ação secundária ("Minhas consultas", "Esqueci minha senha"). Acompanhada por `accent-primary-tint` como fundo suave quando um item está selecionado (data, horário) ou como banner informativo (`accent-primary-tint-soft` + `accent-primary-ink-soft`, ex.: aviso de bloqueio automático de conflito).
- **`success-*`** (verde, mesma família de matiz do `surface-canvas`) — usado em dois contextos: chip de Convênio (sempre, neutro-positivo) e badge "Confirmada" / ícone de sucesso na tela de confirmação. Não é usado como cor de ação, só como indicador de estado.
- **`warning-*`** (âmbar) — badge "Bloqueada" e aviso inline de que uma consulta não pode mais ser cancelada.
- **`danger-*`** (vermelho) — botão "Cancelar" (contorno) e mensagem de erro de login. [NOTE FOR UX: o fundo de erro de login reutiliza `warning-bg` (âmbar) com texto `danger-ink` (vermelho) — mistura de família observada no protótipo; considerar um `danger-bg` dedicado mais vermelho se isso incomodar visualmente ao revisar as telas.]

Evitar: gradientes, ilustrações, cores de marca adicionais além do azul — o app usa cor para *estado* (verde=ok, âmbar=atenção, vermelho=perigo), nunca para decoração.

## Typography

Fonte única: **Inter** (pesos 400/500/600/700), com fallback `system-ui, sans-serif`. Não há uso de fonte serifada ou monoespaçada em nenhuma tela.

Escala compacta e densa (11px a 19px cobre quase toda a interface — é um app utilitário, não editorial): títulos de tela em 18-19px/700, nomes (médico/paciente) em 15-16px/600-700, corpo/inputs em 14-14.5px, texto secundário em 13-13.5px, labels de campo e chips em 12-12.5px/600, badges e seletor de data em 11-11.5px/600-700. O tamanho 28px/400 (Material 3 "large app bar") está reservado no runtime mas não é usado em nenhuma tela atual — pode ser útil para uma futura tela de splash/onboarding.

## Layout & Spacing

Escala curta e cerrada: 4 / 6 / 8 / 10 / 12 / 14 / 16 / 18 / 20px. Padding interno de cards e do cabeçalho gira em torno de 14-18px; gaps entre elementos relacionados (chips, botões lado a lado) usam 6-10px; espaçamento entre blocos de conteúdo usa 12-16px. Layout é sempre uma coluna única (mobile, sem grid multi-coluna), exceto a grade de horários (3 colunas fixas) e o carrossel horizontal de dias.

Margem lateral de tela: 18px. Sem safe-area especial documentada além do necessário para o device frame do Android.

## Elevation & Depth

O app é quase plano: cards e inputs se diferenciam do fundo por **borda de 1px** (`border-hairline` ou `border-input`), não por sombra. A única sombra observada no protótipo é no botão primário de confirmação de agendamento quando habilitado (`0 6px 16px rgba(59,111,224,.35)`, uma sombra colorida sutil na cor de marca) — reservar sombra para essa única ação de maior compromisso (confirmar agendamento), não usar em outros botões.

## Shapes

- **Pill (`100px`)** — todos os botões de texto curto, tabs (Paciente/Médico), chips (convênio, dia da semana, especialidade ao selecionar), badges de status.
- **`lg` (14px)** — cards (médico, consulta, confirmação).
- **`md` (10-12px)** — inputs, selects, botões de largura total (largura cheia, ex. "Entrar", "Criar conta"), botões de slot de horário.
- **`circle` (50%)** — avatar com iniciais do médico, ícones de botão redondo (voltar, GPS).

Nada é totalmente quadrado — todo elemento interativo tem pelo menos um raio pequeno (10px) ou é pill.

## Components

- **Botão primário (largura cheia)** — bg `accent-primary`, texto branco 600-700, padding ~13-14px, `md` rounded. Estado desabilitado: bg `surface-disabled`, texto `ink-disabled`, sem sombra.
- **Botão outline** — bg branco, borda 1px (cor varia pelo contexto: `accent-primary` para ação secundária de marca, `border-input` para neutro, `danger-border` para cancelar), texto na cor da borda.
- **Chip de alternância (toggle)** — usado para Convênio e Dias de atendimento no cadastro de médico, e para Especialidade/Paciente-Médico no seletor de papel. Ativo: borda 2px `accent-primary` + fundo `accent-primary-tint` + texto `accent-primary`. Inativo: borda 1px `border-input` + fundo branco + texto `ink-secondary`.
- **Chip de tag (somente leitura)** — Convênio aceito, exibido em listas/detalhe de médico. Sempre `success-bg`/`success-ink-muted`, pill.
- **Badge de status** — "Confirmada" (success) / "Bloqueada" (warning), pill, texto micro 700.
- **Card padrão** — bg branco, borda `border-hairline`, `lg` rounded, padding 14px. Usado para: card de médico na busca, card de consulta, card de perfil do médico, card de confirmação.
- **Avatar com iniciais** — círculo `accent-primary` com iniciais do médico em branco, 40-48px de diâmetro.
- **Input de texto/senha** — `md` rounded, borda `border-input`, padding ~11px 12px, fonte 14px, placeholder em cinza.
- **Select nativo (dropdown)** — mesmo estilo visual do input; usado para Especialidade, horário de início/fim.
- **Barra superior (app bar)** — bg branco, borda inferior `border-hairline`, botão de voltar circular condicional, título 18px/700 + subtítulo opcional 12.5px, botão de atalho opcional ("Minhas consultas") no canto direito.
- **Barra de ação inferior (fixa)** — usada só na tela de detalhe do médico, contém o botão primário de confirmar agendamento; bg branco, borda superior `border-hairline`.

## Do's and Don'ts

| Do | Don't |
|---|---|
| Uma única cor de marca (`accent-primary`), reservada para ação e seleção | Introduzir uma segunda cor de marca ou gradiente |
| Cor comunica estado (verde=ok, âmbar=atenção, vermelho=perigo) | Usar cor de estado como decoração |
| Bordas finas de 1px para separar cards/inputs do fundo | Sombras pesadas ou elevação tipo Material antigo |
| Pills para tabs, chips e botões de texto curto | Botões quadrados ou com raio inconsistente entre telas similares |
| Fonte única (Inter) em todos os pesos necessários | Introduzir uma segunda família tipográfica |
