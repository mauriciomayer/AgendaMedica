---
title: Revisão Adversarial — PRD Agenda Médica
type: review-adversarial
source_prd: prd.md
created: 2026-09-17
---

# Revisão Adversarial do PRD — Agenda Médica

Revisão de documento (não de código), aplicando lente adversarial: o que falta, o que parece resolvido mas não está, inconsistências internas, drift de IDs, afirmações não sustentadas pelo escopo declarado, requisitos vagos/não testáveis, trade-offs evitados e "assumptions" que escondem decisões que deveriam ter sido perguntadas explicitamente.

---

### Achado 1

- **location**: §4.5 Cancelamento e Reagendamento — RF-8 e RF-9
- **trigger_condition**: RF-8 permite cancelamento quando faltam "mais de 24h" e RF-9 bloqueia quando faltam "menos de 24h"; nenhum dos dois cobre o instante exato de 24h antes da consulta, deixando esse limite sem regra definida.
- **guard_snippet**: Redefinir com operadores complementares e explícitos, ex.: RF-8 "quando faltarem ≥ 24h" e RF-9 "quando faltarem < 24h" (ou o inverso), e adicionar um caso de teste específico para o instante exato de 24h em ambos os RFs.
- **potential_consequence**: Implementação divergente entre cliente e backend nesse limite (um trata >= como permitido, outro como bloqueado), permitindo cancelamentos fora da regra pretendida ou bloqueando cancelamentos que deveriam ser aceitos — exatamente o tipo de brecha que o projeto diz querer eliminar (§1 Visão: "sem deixar brechas ou exceções não tratadas").

### Achado 2

- **location**: §2.3 JU-3, "Caso extremo (JU-4 relacionada)"
- **trigger_condition**: O caso extremo de JU-3 (Fernanda tenta cancelar faltando 10h) é rotulado como "JU-4 relacionada", mas JU-4 é definida logo abaixo como um cenário totalmente diferente ("Conflito de agenda é evitado sob concorrência"). O rótulo cruzado não corresponde ao conteúdo de JU-4.
- **guard_snippet**: Remover a referência a "JU-4" nesse ponto (o caso extremo já pertence a JU-3 e realiza RF-9) ou, se a intenção era outra, renomear/renumerar para não colidir com a JU-4 real.
- **potential_consequence**: Nas próximas etapas do BMad (Épicos/Histórias), quem mapear histórias a partir das JUs vai associar trabalho de "bloqueio de cancelamento" (RF-9) à jornada errada de concorrência (RF-7), gerando rastreabilidade quebrada entre PRD e backlog.

### Achado 3

- **location**: §7 Métricas de Sucesso — MS-1
- **trigger_condition**: MS-1 declara "Valida RF-1 a RF-11", mas o fluxo de demo descrito (cadastro de médico, cadastro de paciente, busca, agendamento, lembrete e cancelamento) não exercita RF-11 (recuperação de senha) em nenhum ponto.
- **guard_snippet**: Ajustar o texto para "Valida RF-1 a RF-10" e criar uma métrica/caso de teste separado explícito para RF-11 (ex.: "MS-1b: fluxo de recuperação de senha executável sem erros"), ou incluir explicitamente o passo de recuperação de senha no fluxo de demo de MS-1.
- **potential_consequence**: Na fase de Sprint Planning/QA, RF-11 pode ser dado como "coberto" por MS-1 sem nunca ter sido de fato exercitado, deixando um requisito funcional sem verificação real antes do "launch".

### Achado 4

- **location**: §7 Métricas de Sucesso — MS-2
- **trigger_condition**: MS-2 exige "zero ocorrências de conflito de agenda em testes de concorrência simulada", mas não define quantas requisições paralelas, quantas rodadas, ou qual ferramenta/framework caracteriza o teste como suficiente — "zero ocorrências" sobre um universo não especificado não é uma meta testável de forma objetiva.
- **guard_snippet**: Especificar parâmetros mínimos, ex.: "N ≥ 50 requisições concorrentes para o mesmo slot, repetido em ≥ 20 execuções, 0 duplicidades em todas as execuções", e nomear a abordagem esperada (teste de integração com concorrência simulada no nível de banco/API).
- **potential_consequence**: Sem um limiar objetivo, um teste fraco (ex.: 2 requisições sequenciais com delay) pode "passar" a métrica mais crítica do projeto, dando falsa confiança justamente na regra de negócio central que o portfólio pretende demonstrar (§1 Visão).

### Achado 5

- **location**: §7 Métricas de Sucesso — MS-3
- **trigger_condition**: MS-3 define meta de "80%+" de cobertura "nessas regras específicas", mas não especifica tipo de cobertura (linha, branch, caso de uso), ferramenta de medição, nem os limites exatos do que conta como "regras críticas" (arquivos/módulos/classes específicos).
- **guard_snippet**: Definir explicitamente: métrica de cobertura (ex.: branch coverage via JaCoCo/Kover), e listar os módulos/classes que compõem "regras críticas" (ex.: validador de antecedência mínima, validador de janela de cancelamento, serviço de bloqueio de conflito).
- **potential_consequence**: A meta se torna auto-declarada e não auditável — qualquer número pode ser apresentado como "atingido" sem que revisores consigam verificar de forma objetiva se as regras realmente críticas foram testadas.

### Achado 6

- **location**: §4.1 RF-2, consequência final com [ASSUMPTION]
- **trigger_condition**: A regra "consultas já confirmadas em um horário removido da agenda permanecem válidas" é marcada apenas como ASSUMPTION, mas é uma decisão de produto com impacto direto em conflito de agenda futuro (um médico pode remover um bloco de horário e ainda assim ter uma consulta confirmada "fantasma" fora da sua agenda visível) — deveria ter sido perguntada e decidida explicitamente, não assumida.
- **guard_snippet**: Elevar para uma pergunta em §8 Perguntas Abertas (ex.: "O médico pode editar sua grade removendo horários com consultas confirmadas? O que acontece com essas consultas — permanecem, ou o sistema impede a remoção desse intervalo?") e só then registrar como decisão formal no PRD, não como assumption silenciosa.
- **potential_consequence**: Um médico remove um intervalo de atendimento com consultas já confirmadas; a consulta "sobrevive" fora da agenda visível/editável, criando um estado que nenhuma tela do app trata (o paciente vê uma consulta confirmada num médico que "não atende mais" naquele horário) — exatamente o tipo de estado inconsistente que RF-7 diz querer evitar para conflitos, mas aqui é permitido por omissão.

### Achado 7

- **location**: §4.3 RF-4, consequência com [ASSUMPTION] sobre busca sem GPS
- **trigger_condition**: Quando não há GPS, o RF diz apenas que os resultados são "filtrados por região, sem ordenação por distância exata" — não define qual critério de ordenação é usado nesse caso (alfabética? data de cadastro? aleatória?), deixando um comportobservável do sistema totalmente indefinido.
- **guard_snippet**: Especificar um critério de ordenação de fallback explícito, ex.: "ordenação alfabética por nome do médico" ou "ordenação por relevância de correspondência de bairro", e adicionar como consequência testável do RF-4.
- **potential_consequence**: Sem critério definido, a implementação pode escolher uma ordem não determinística (ex.: ordem de inserção no banco), tornando o comportamento não testável e potencialmente instável entre execuções — um requisito que parece completo mas deixa a experiência do usuário indefinida.

### Achado 8

- **location**: §3 Glossário (Convênio) e §4.1 RF-2
- **trigger_condition**: A regra "não editável nem removível pelo Médico após seleção inicial" bloqueia tanto remoção quanto adição de novos convênios depois do cadastro — inclusive impedindo, por exemplo, que um médico que só marcou "Particular" no início passe a aceitar Unimed meses depois. O PRD apresenta isso como decisão fechada sem discutir esse trade-off de usabilidade nem justificar por que a irreversibilidade também bloqueia adição (não só remoção, que teria uma justificativa mais clara relacionada a consultas já agendadas).
- **guard_snippet**: Documentar explicitamente o trade-off considerado (ex.: "decisão deliberada de simplificar o modelo de dados, aceitando a limitação de UX de não permitir adicionar novos convênios depois" ou, alternativamente, permitir adicionar convênios livremente e só bloquear remoção quando houver consultas agendadas sob aquele convênio).
- **potential_consequence**: Se a intenção real era impedir apenas remoção (para não invalidar consultas existentes), a regra como escrita é mais restritiva do que o necessário e pode ser apontada como falha de design em uma avaliação de portfólio, já que bloqueia um caso de uso legítimo e comum (médico expande convênios aceitos) sem necessidade técnica aparente.

### Achado 9

- **location**: §4.2 (RF-3) e §4.6 (RF-10)
- **trigger_condition**: RF-10 depende de o Paciente ter telefone (SMS) e e-mail cadastrados para os três canais de lembrete, mas RF-3/§4.2 deixa os "campos exatos do formulário de cadastro" como pendentes do design visual externo — ou seja, um RF já descrito como "consequência testável" (disparo de SMS) depende de um dado cuja coleta ainda não está garantida em nenhum RF.
- **guard_snippet**: Adicionar a §4.2/RF-3 um requisito mínimo explícito: "o cadastro de Paciente deve obrigatoriamente coletar e-mail e telefone celular, independente do que o design visual definir adicionalmente" — travando esse campo como requisito de PRD, não como decisão de UI.
- **potential_consequence**: Se o design visual externo (ainda não incorporado) definir um formulário de cadastro sem campo de telefone, RF-10 se torna inimplementável como escrito sem retrabalho, e essa dependência circular só seria descoberta tarde, na fase de Arquitetura ou Build.

### Achado 10

- **location**: §4.1 RF-1
- **trigger_condition**: A consequência testável "o perfil do Médico aparece em resultados de busca compatíveis em até poucos segundos" usa um limiar vago ("poucos segundos") que não é um critério de aceite testável de forma objetiva.
- **guard_snippet**: Substituir por um número concreto, ex.: "em até 5 segundos após salvar o cadastro" (ou o valor que fizer sentido para a arquitetura de dados síncrona vs. eventual).
- **potential_consequence**: Um teste de aceitação para RF-1 não tem como falhar ou passar de forma objetiva — "poucos segundos" pode ser interpretado como 2s por um revisor e 30s por outro, minando a alegação do PRD de que os RFs são "testáveis" (§0 Propósito).

### Achado 11

- **location**: §4.6 Notificações (descrição) vs. RF-10
- **trigger_condition**: A descrição de §4.6 afirma que "o Médico recebe apenas notificações de evento (nova consulta, cancelamento)" — mas o único RF da seção (RF-10) cobre exclusivamente o lembrete de rotina ao Paciente. Não existe nenhum RF, consequência testável ou critério de aceite para a notificação de "nova consulta" ao médico; a notificação de cancelamento aparece apenas como uma consequência secundária dentro de RF-8 (§4.5), não em §4.6.
- **guard_snippet**: Adicionar um RF explícito (ex.: "RF-12: Notificação de evento ao médico") com consequências testáveis para o caso de nova consulta agendada, ou remover a afirmação de §4.6 e apontar claramente que esse evento é coberto só por RF-8 para cancelamento, deixando "nova consulta" como gap reconhecido.
- **potential_consequence**: A funcionalidade "médico é notificado de nova consulta" parece resolvida na descrição da seção, mas não tem nenhum requisito verificável — na prática pode nunca ser implementada nem testada, e ninguém perceberá porque o texto descritivo dá a impressão de cobertura completa.

### Achado 12

- **location**: §4.4 RF-6, §4.5 RF-8/RF-9, Glossário (§3, "Antecedência mínima" e "Janela de cancelamento")
- **trigger_condition**: Todas as regras de tempo (48h de antecedência, 24h de cancelamento, 24h de lembrete) são definidas em relação a "hora atual"/"horário marcado" sem especificar fuso horário de referência. O Brasil tem mais de um fuso horário (ex. Amazonas vs. São Paulo), e paciente/médico podem estar em regiões diferentes mesmo dentro do escopo Android nacional.
- **guard_snippet**: Adicionar uma nota de RNF ou assunção explícita: "todos os cálculos de antecedência/janela usam o fuso horário do servidor (UTC) convertido para o fuso horário cadastrado do médico" (ou regra equivalente), e listar isso em §9 Índice de Assunções.
- **potential_consequence**: Sem regra de fuso definida, a arquitetura pode implementar o cálculo usando o relógio do dispositivo do paciente (manipulável e inconsistente) ou do médico, produzindo resultados diferentes para o mesmo agendamento dependendo de quem consulta — uma falha sutil bem no centro da regra de negócio que o projeto quer demonstrar como robusta.

### Achado 13

- **location**: §8 Perguntas Abertas vs. notas "[NOTE FOR PM]" espalhadas (§4.1 Fora de Escopo, §Restrições e Salvaguardas — Privacidade)
- **trigger_condition**: Existem pelo menos duas notas "[NOTE FOR PM]" fora de §8 que representam decisões pendentes reais — (a) se a impossibilidade de trocar de especialidade deveria virar RF de v2, e (b) se práticas mínimas de proteção de dados de saúde deveriam ser documentadas no addendum — mas nenhuma das duas está listada nas "Perguntas Abertas" oficiais do documento (que lista apenas 2 itens, sobre provedor técnico e incorporação do design visual).
- **guard_snippet**: Consolidar todas as notas "[NOTE FOR PM]" do corpo do documento em §8 Perguntas Abertas (ou em uma lista única de "itens pendentes de decisão"), garantindo que nenhuma decisão fique enterrada apenas como comentário inline.
- **potential_consequence**: No gate de finalização do PRD, um revisor que só olhar §8 (o local canônico de pendências) vai concluir erroneamente que restam apenas 2 perguntas abertas, quando na prática há mais decisões não fechadas espalhadas pelo texto — risco de o documento ser "aprovado" com lacunas não visíveis.

### Achado 14

- **location**: §Restrições e Salvaguardas (Privacidade) e §RNFs Transversais
- **trigger_condition**: O próprio PRD reconhece, em uma nota, que "o app ainda trata dados de saúde por natureza (especialidade buscada, histórico de consultas)", mas essa constatação não se traduz em nenhum RNF, requisito ou salvaguarda concreta na seção de RNFs Transversais (que cobre apenas concorrência, notificações, disponibilidade e desempenho — nada sobre proteção de dados sensíveis, criptografia em repouso/trânsito, ou controle de acesso a histórico de consultas).
- **guard_snippet**: Adicionar um RNF mínimo em "RNFs Transversais", ex.: "Dados de histórico de consultas e especialidade buscada são armazenados com controle de acesso por usuário autenticado; nenhuma consulta ou busca de outro usuário é acessível via API sem autorização", mesmo sem exigência formal de conformidade LGPD.
- **potential_consequence**: Sem esse RNF, a implementação pode não aplicar nenhum controle de acesso a dados de saúde sensíveis, e o "diferencial técnico de portfólio" mencionado na nota (boas práticas mínimas de proteção de dados) nunca se materializa porque não virou requisito rastreável — fica apenas como intenção documentada e esquecida.

### Achado 15

- **location**: §4.4 RF-7 (RNFs específicos) e §1 Visão
- **trigger_condition**: O mecanismo de lock/transação atômica — descrito na Visão como a razão de ser do projeto ("regra de negócio central... sem deixar brechas") — é inteiramente delegado a um `addendum.md` não incluído nem referenciado com link/local claro, e essa dependência crítica não aparece em §8 Perguntas Abertas nem em §9 Índice de Assunções como pendência formal de Arquitetura.
- **guard_snippet**: Adicionar a §8 Perguntas Abertas um item explícito: "Qual mecanismo exato (constraint de unicidade no banco vs. lock pessimista vs. transação serializável) implementará o bloqueio de conflito de agenda? Registrar decisão em addendum.md durante a Arquitetura antes de iniciar o Build do Épico correspondente."
- **potential_consequence**: Por ser a peça central do valor do projeto (§1 Visão) mas não estar rastreada como pendência formal, essa decisão de arquitetura pode ser adiada informalmente ou implementada de forma inconsistente (ex.: checagem na aplicação em vez de constraint no banco), justamente a falha que o RNF específico de RF-7 already alerta para evitar.

### Achado 16

- **location**: §3 Glossário — definição de "Antecedência mínima"
- **trigger_condition**: A definição contém um erro de digitação ("pelo meno 48h no futuro" em vez de "pelo menos 48h") na definição do termo mais citado e mais crítico do glossário — usado em RF-5, RF-6, JU-1 e na seção de Mapeamento capacidade → RF.
- **guard_snippet**: Corrigir para "esteja a pelo menos 48h no futuro."
- **potential_consequence**: É um erro cosmético isolado, mas ocorre na definição formal do termo central do sistema; em um documento cujo §0 Propósito promete "vocabulário estável", erros de digitação no próprio glossário reduzem a credibilidade do documento como referência precisa para Arquitetura e Épicos/Histórias.

---

**Resumo:** 16 achados registrados, cobrindo: lacuna de limite exato nas janelas de tempo (24h/48h), drift de referência cruzada entre jornadas (JU-3/JU-4), métricas de sucesso não totalmente testáveis ou com escopo de validação inflado, assumptions que deveriam ser decisões explícitas, dependências circulares entre RFs, trade-offs de UX não discutidos (irreversibilidade de convênio), ausência de RNF de proteção de dados apesar de risco reconhecido no próprio texto, e itens pendentes ("NOTE FOR PM") não consolidados no registro oficial de perguntas abertas.
