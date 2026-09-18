# Reconciliation — Claude Design import vs DESIGN.md / EXPERIENCE.md

**Input:** protótipo interativo do Claude Design (link fornecido por Mauricio em 2026-09-17), extraído para `imports/claude-design-template.html` + `imports/claude-design-logic.js`.

## Coverage

Todas as 10 telas do protótipo (Login, Recuperar senha, Escolha, Cadastro de Paciente, Cadastro de Médico, Busca, Detalhe/Reagendar, Confirmação, Minhas Consultas, Minha Agenda) foram mapeadas na Information Architecture do EXPERIENCE.md. Cores, tipografia, raios e espaçamento do protótipo foram extraídos com precisão (valores oklch/hex exatos do código-fonte, não estimados visualmente) para o DESIGN.md.

## Gaps / conflitos encontrados e resolvidos com o usuário

1. **Limite de 24h para cancelamento** — o PRD (pós-revisão adversarial) tratava o instante exato de 24h como já bloqueado; o protótipo trata como ainda permitido (bloqueia só abaixo de 24h). Usuário confirmou a regra do protótipo como correta — PRD corrigido de volta.
2. **Lista de especialidades** — protótipo usa uma lista fechada de 6 especialidades não fixada no PRD original. Usuário confirmou que é intencional e fechada — Glossário e RF-2 do PRD atualizados.
3. **Ações do médico em "Minha Agenda"** — protótipo só mostra consultas (leitura), mas o PRD (RF-8) prevê que o médico também cancele/reagende. Usuário optou por manter o RF-8 como está e tratar isso como pendência de design a resolver depois — registrado como Open Item no EXPERIENCE.md e como pergunta aberta no PRD.

## Ideias qualitativas capturadas (que a estrutura de FR/tabela poderia ter deixado escapar)

- O tom "sem desculpas, direto ao ponto" das mensagens do sistema (ex.: aviso de bloqueio automático de conflito, aviso de CRM não validado) — capturado em EXPERIENCE.md → Voice and Tone.
- A confirmação de cancelamento **inline no card**, nunca em diálogo modal — um padrão de interação consistente em toda a tela de Minhas Consultas, capturado em Component Patterns e Interaction Primitives.
- A resposta neutra de "recuperar senha" (não revela se o e-mail existe) — um padrão de segurança implícito no protótipo que não estava explícito no PRD; capturado em State Patterns.

## Unexplained deviations

Nenhuma — os únicos desvios entre PRD e protótipo eram os 3 pontos acima, todos resolvidos com o usuário antes de finalizar as spines.
