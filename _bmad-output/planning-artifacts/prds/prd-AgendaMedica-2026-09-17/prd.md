---
title: Agenda Médica
status: final
created: 2026-09-17
updated: 2026-09-17
---

# PRD: Agenda Médica
*Título de trabalho — confirme com o produtor.*

## 0. Propósito do Documento

Este PRD documenta o escopo funcional do aplicativo Android de agendamento médico "Agenda Médica", um projeto de portfólio pessoal de Mauricio. Destina-se a orientar as próximas etapas do fluxo BMad (Arquitetura, Épicos e Histórias, Sprint Planning e Build) com um vocabulário estável e requisitos testáveis. O documento parte de um levantamento de escopo já fechado (`docs/Agendamento Medico.txt`) e das decisões tomadas durante a descoberta deste PRD. O design visual (UI/UX) está sendo elaborado paralelamente em outra ferramenta e será incorporado a este PRD (ou a um documento de UX complementar via `bmad-ux`) em uma etapa futura — este PRD não antecipa decisões visuais. Decisões técnicas ainda em aberto (mecanismo de lock de concorrência, atualização em tempo real, provedor de notificações) estão registradas em `addendum.md`, a ser resolvido na Arquitetura. Termos do Glossário (§3) são usados de forma consistente em todo o documento; Requisitos Funcionais (RF) são numerados globalmente e referenciam as Jornadas do Usuário (JU) que realizam.

## 1. Visão

O Agenda Médica resolve um problema simples e recorrente: marcar uma consulta médica hoje ainda depende, na maior parte das vezes, de telefonema, WhatsApp ou balcão — processos manuais, sujeitos a erro humano e sem visibilidade real da agenda do médico. O app propõe uma alternativa direta: o paciente busca um médico por especialidade e localização, vê os horários realmente disponíveis e agenda em poucos toques, com a garantia de que dois pacientes nunca vão brigar pelo mesmo horário. Do lado do médico, o app oferece autocadastro imediato — sem burocracia de validação — e controle total sobre especialidade, convênios atendidos e agenda.

Como projeto de portfólio, o Agenda Médica não compete por mercado nem mira um cliente específico: seu objetivo é demonstrar, de forma completa e verificável, a capacidade de projetar e implementar corretamente uma regra de negócio central com múltiplas restrições entrelaçadas — antecedência mínima de agendamento, janela de cancelamento e bloqueio de concorrência de agenda — sem deixar brechas ou exceções não tratadas.

O valor entregue é duplo: para um paciente fictício, a certeza de que o horário reservado é seu, sem risco de conflito; para um médico fictício, uma agenda que se autogerencia dentro de regras simples e sem necessidade de moderação humana.

O levantamento de escopo original classificou o projeto como Complexidade Média e Risco Baixo: o volume inicial é pequeno, não há dependências externas e todas as regras de negócio ficaram fechadas sem exceções a tratar (nenhum ponto crítico permaneceu em aberto). Esse julgamento se mantém válido neste PRD — a complexidade concentra-se inteiramente na regra de concorrência de agenda (§RNFs Transversais), não em superfície de features.

## 2. Usuário-Alvo

### 2.1 Trabalhos a Realizar (JTBD)

**Paciente**
- Quando preciso de uma consulta com um especialista, quero encontrar um médico por especialidade e proximidade, para não perder tempo ligando em clínicas.
- Quando encontro um médico, quero ver horários realmente livres, para agendar sem ida e volta de confirmação.
- Quando meus planos mudam, quero cancelar ou reagendar dentro de um prazo razoável, para não perder a consulta nem pagar por algo que não vou usar.
- Quando uma consulta se aproxima, quero ser lembrado automaticamente, para não esquecer o compromisso.

**Médico**
- Quando decido atender por conta própria, quero me cadastrar e aparecer na busca imediatamente, sem esperar validação de terceiros.
- Quando defino minha agenda, quero controlar especialidade, convênios aceitos e dias/horários, para que só apareçam para pacientes compatíveis com o que ofereço.
- Quando um paciente cancela ou preciso reagendar, quero que o sistema aplique a mesma regra de prazo para todos, sem exceções que eu precise negociar manualmente.

**Mauricio (o builder)** *(válido em projeto de portfólio)*
- Quero implementar e demonstrar, de ponta a ponta, uma regra de concorrência de agenda corretamente testada, como peça central do meu portfólio técnico.

### 2.2 Não-Usuários (v1)

- Clínicas, hospitais ou operadoras de saúde como contas institucionais (o app é individual: um médico, um cadastro).
- Médicos que atuam em múltiplas especialidades (o cadastro permite apenas uma especialidade por médico).
- Convênios além dos quatro fixos (Unimed, Amil, Bradesco, Particular) — nenhum outro convênio fora dessa lista é suportado em v1.
- Usuários de iOS ou web — v1 é Android apenas.
- Pacientes ou médicos que precisam de telemedicina (consulta remota) — fora de escopo.

### 2.3 Jornadas-Chave do Usuário

- **JU-1. Fernanda agenda uma consulta com uma dermatologista perto de casa.**
  - **Persona + contexto:** Fernanda, 34 anos, autônoma, sente uma alergia recorrente e quer resolver antes do fim de semana.
  - **Estado inicial:** autenticada no app como paciente, na tela inicial de busca.
  - **Caminho:** (1) busca por especialidade "Dermatologia" com localização ativada por GPS; (2) vê lista de médicas ordenada por distância, com convênios aceitos e badge de especialidade; (3) abre o perfil da Dra. Ana e vê a grade de horários livres em Slots de 30 minutos, apenas a partir de 48h à frente; (4) toca em um horário de quinta-feira às 14h e confirma o agendamento.
  - **Clímax:** a tela de confirmação mostra "Consulta agendada com Dra. Ana — quinta, 14h" e o horário desaparece imediatamente da grade de outros pacientes. Realiza RF-4, RF-5, RF-6, RF-7.
  - **Resolução:** Fernanda recebe confirmação por push e sabe que receberá um lembrete 24h antes.
  - **Caso extremo:** se, entre ela abrir a grade e confirmar, outro paciente já tiver reservado o mesmo horário, o app rejeita a ação com "Este horário acabou de ser reservado, escolha outro" e atualiza a grade automaticamente (realiza RF-7).

- **JU-2. Dr. Ricardo se cadastra e monta sua agenda no primeiro acesso.**
  - **Persona + contexto:** Ricardo, cardiologista recém-formado, decide oferecer consultas particulares e via convênio pelo app, sem depender de uma clínica.
  - **Estado inicial:** não autenticado, tela inicial "Sou médico".
  - **Caminho:** (1) preenche o cadastro (campos exatos a definir pelo design visual em elaboração externa; sem exigência de CRM ou documento, conforme autocadastro sem validação); (2) escolhe sua única especialidade, "Cardiologia", em lista pré-definida; (3) marca os convênios que atende dentre Unimed, Amil, Bradesco e Particular; (4) define dias da semana e faixas de horário em que atende, dentro do horário da clínica (8h-18h), que o sistema converte em Slots de 30 minutos com 15 minutos de intervalo entre consultas.
  - **Clímax:** ao salvar, seu perfil já aparece imediatamente nas buscas por "Cardiologia" na região dele — sem etapa de aprovação. Realiza RF-1, RF-2.
  - **Resolução:** Ricardo vê sua agenda vazia populada com os slots corretos e pode editar dias/horários a qualquer momento (convênios e especialidade, uma vez definidos, seguem as mesmas regras de edição — ver RF-2).
  - **Caso extremo:** se Ricardo tentar se cadastrar em duas especialidades, o sistema bloqueia e exige escolher uma.

- **JU-3. Fernanda cancela a tempo e reagenda.**
  - **Persona + contexto:** mesma Fernanda; um compromisso de trabalho surge na véspera da consulta.
  - **Estado inicial:** autenticada, na tela "Minhas Consultas".
  - **Caminho:** (1) abre a consulta agendada, que mostra o horário e um contador "faltam 30h — você pode cancelar"; (2) toca em "Cancelar", confirma no diálogo; (3) o horário volta a ficar disponível na agenda da Dra. Ana; (4) Fernanda busca novamente e agenda um novo horário.
  - **Clímax:** o cancelamento é confirmado instantaneamente porque está dentro da janela de 24h. Realiza RF-8.
  - **Resolução:** Fernanda recebe confirmação do cancelamento; a Dra. Ana é notificada de que o horário abriu.
  - **Caso extremo:** se Fernanda tentasse cancelar essa mesma consulta faltando 10h, o botão "Cancelar" aparece desabilitado com a mensagem "Cancelamento não é mais permitido para esta consulta" — sem exceção manual possível por ninguém, nem pelo médico (realiza RF-9).

- **JU-4. Conflito de agenda é evitado sob concorrência.**
  - **Persona + contexto:** dois pacientes, Fernanda e Bruno, abrem a mesma grade de horários da Dra. Ana ao mesmo tempo, ambos mirando o slot das 14h de quinta-feira.
  - **Estado inicial:** ambos autenticados, ambos com a grade de horários carregada no app.
  - **Caminho:** (1) Fernanda toca em "Confirmar" primeiro; (2) o backend aplica um lock/transação atômica sobre o slot; (3) Bruno toca em "Confirmar" um segundo depois, para o mesmo slot.
  - **Clímax:** o pedido de Fernanda é aceito; o pedido de Bruno é rejeitado com mensagem clara e a grade dele é atualizada em tempo real, mostrando o slot como ocupado. Realiza RF-7.
  - **Resolução:** apenas uma consulta existe para aquele slot; não há estado inconsistente nem consulta duplicada no médico.

**Mapeamento capacidade → RF (jornadas mais críticas):**
- O sistema deve impedir que dois agendamentos ocorram no mesmo slot do mesmo médico, mesmo sob requisições concorrentes. → RF-7
- O sistema deve impedir cancelamento após o limite de 24h antes da consulta, sem exceção de perfil (nem paciente, nem médico, nem suporte). → RF-9
- O sistema deve impedir agendamento com menos de 48h de antecedência. → RF-6

## 3. Glossário

- **Paciente** — usuário que busca e agenda consultas.
- **Médico** — usuário que oferece consultas; possui exatamente uma Especialidade.
- **Especialidade** — área de atuação médica; um Médico tem exatamente uma. Lista fixa do sistema (mesmo padrão do Convênio): Cardiologia, Dermatologia, Pediatria, Ortopedia, Clínico Geral, Ginecologia.
- **Convênio** — plano de saúde (ou atendimento "Particular", sem convênio) aceito por um Médico. Lista fixa do sistema: Unimed, Amil, Bradesco, Particular. Não editável nem removível pelo Médico após seleção inicial, mesmo com Consultas já agendadas naquele Convênio.
- **Consulta** — um agendamento entre um Paciente e um Médico em um Slot específico.
- **Slot** — intervalo de 30 minutos (duração de uma Consulta) na Agenda de um Médico, disponível ou ocupado, sempre seguido de 15 minutos de intervalo até o próximo Slot possível.
- **Horário da clínica** — janela fixa das 8h às 18h dentro da qual todo Médico define seu próprio horário de início/fim de atendimento; nenhum Médico pode configurar um horário que comece antes das 8h ou termine depois das 18h.
- **Agenda** — conjunto de Slots de um Médico, derivado dos dias/horários de atendimento que ele define.
- **Antecedência mínima** — regra de 48 horas: uma Consulta só pode ser agendada para um Slot cujo horário esteja a 48h ou mais no futuro, contadas a partir do instante do agendamento. Um Slot a menos de 48h (inclusive exatamente 48h menos um segundo) não é agendável.
- **Janela de cancelamento** — período em que Cancelamento/reagendamento é permitido: enquanto faltarem 24h ou mais para o horário da Consulta (o instante exato de 24h ainda está dentro da Janela).
- **Bloqueio de cancelamento** — assim que faltar menos de 24h (estritamente) para o horário da Consulta, ela não pode mais ser cancelada ou reagendada por ninguém através do app.
- **Conflito de agenda** — tentativa de duas Consultas ocupando o mesmo Slot do mesmo Médico; deve ser sempre impedido pelo sistema.
- **Autocadastro** — cadastro de Médico sem validação externa (ex.: CRM); o perfil fica visível na busca imediatamente após salvar.

## 4. Funcionalidades

### 4.1 Cadastro e Perfil de Médico

**Descrição:** Um Médico se autocadastra informando seus dados, escolhe sua Especialidade (única), seleciona os Convênios que atende dentre a lista fixa e define os dias/horários em que atende, dentro do horário da clínica (8h às 18h) — que o sistema converte automaticamente em Slots de 30 minutos com 15 minutos de intervalo entre um e outro. Não há etapa de validação (ex.: verificação de CRM): ao salvar, o perfil já aparece na busca. Realiza JU-2. [NOTE FOR PM: campos exatos do formulário a definir a partir do design visual — ver §9, "Dependências pendentes do design visual".]

**Requisitos Funcionais:**

#### RF-1: Autocadastro de médico sem validação

Um Médico pode se cadastrar informando dados básicos e ficar visível na busca imediatamente, sem etapa de aprovação humana ou validação de documento.

**Consequências (testáveis):**
- Ao concluir o cadastro, o perfil do Médico aparece em resultados de busca compatíveis em até poucos segundos (sem fila de aprovação).
- O sistema não bloqueia nem sinaliza cadastros por ausência de CRM ou qualquer documento.

#### RF-2: Definição de perfil profissional do médico

Um Médico define exatamente uma Especialidade (dentre a lista fixa — ver Glossário, §3), um ou mais Convênios (dentre Unimed, Amil, Bradesco, Particular) e os dias/horários em que atende, dentro do horário da clínica (8h às 18h — ver Glossário). Realiza JU-2.

**Consequências (testáveis):**
- O sistema rejeita a seleção de mais de uma Especialidade por Médico, e rejeita qualquer Especialidade fora da lista fixa.
- Convênios que não fazem parte da lista fixa (Unimed, Amil, Bradesco, Particular) não podem ser selecionados.
- Uma vez que um Convênio é selecionado e salvo, o Médico não tem opção de removê-lo ou editá-lo pela interface — inclusive se já houver Consultas agendadas sob aquele Convênio.
- Dias/horários definidos (sempre dentro de 8h-18h) geram automaticamente os Slots de 30 minutos, com 15 min de intervalo entre eles, correspondentes na Agenda do Médico.
- O horário de início/fim escolhido pelo Médico precisa caber inteiramente dentro do horário da clínica (8h às 18h); o sistema rejeita qualquer configuração fora dessa janela.
- O Médico pode editar dias/horários de atendimento a qualquer momento. Se um Slot com Consulta já confirmada deixar de existir na nova agenda, a Consulta permanece válida e deve ser honrada pelo Médico — o sistema não a cancela retroativamente nem avisa o Paciente de qualquer mudança (decisão confirmada, para evitar cancelamento-surpresa ao Paciente).

**Fora de Escopo:**
- Alteração de Especialidade após o cadastro inicial (fora de escopo — exigiria excluir e recriar o perfil). [NOTE FOR PM: confirmar se isso é aceitável ou se precisa virar um RF de v2.]

### 4.2 Cadastro de Paciente

**Descrição:** Um Paciente se cadastra para poder buscar médicos e agendar consultas. Sem exigência de convênio no cadastro — o convênio, quando relevante, é considerado apenas no momento da busca/agendamento. Não exige validação de identidade, análogo ao autocadastro do médico, mas sem visibilidade pública associada. [NOTE FOR PM: campos exatos do formulário a definir a partir do design visual — ver §9, "Dependências pendentes do design visual".]

**Requisitos Funcionais:**

#### RF-3: Cadastro de paciente

Um usuário pode se cadastrar como Paciente informando os dados definidos no design visual (a incorporar).

**Consequências (testáveis):**
- Um Paciente cadastrado consegue autenticar-se e acessar busca, agendamento e "Minhas Consultas".
- O cadastro não exige vínculo obrigatório com um Convênio.

### 4.3 Busca de Médicos

**Descrição:** O Paciente busca Médicos por Especialidade e localização. A localização pode ser obtida automaticamente via GPS do dispositivo (padrão) ou informada manualmente por cidade/bairro, para cobrir o caso em que o Paciente não quer compartilhar localização ou está buscando para outra região. Realiza JU-1.

**Requisitos Funcionais:**

#### RF-4: Busca por especialidade e localização

Um Paciente pode buscar Médicos filtrando por Especialidade e localização (GPS automático ou entrada manual de cidade/bairro).

**Consequências (testáveis):**
- Resultados exibem apenas Médicos da Especialidade filtrada.
- Com GPS ativo, resultados são ordenados por distância crescente até o Paciente.
- Sem permissão de GPS ou com busca manual, o Paciente informa cidade/bairro e recebe resultados filtrados por essa região, ordenados alfabeticamente pelo nome do Médico (sem ordenação por distância exata). [ASSUMPTION: sem GPS, não há cálculo preciso de distância — apenas correspondência de região; ordem alfabética como fallback.]
- Cada resultado exibe Convênios aceitos pelo Médico.

#### RF-5: Visualização de horários disponíveis

Um Paciente pode visualizar a Agenda de um Médico específico, vendo apenas Slots livres e respeitando a Antecedência mínima de 48h. Realiza JU-1.

**Consequências (testáveis):**
- Slots a menos de 48h da hora atual não aparecem como selecionáveis.
- Slots já ocupados por outra Consulta não aparecem como disponíveis.
- A grade é organizada em Slots de 30 minutos com 15 minutos de intervalo entre eles (grade efetiva de 45 em 45 min), refletindo os dias/horários definidos pelo Médico dentro do horário da clínica (RF-2).

### 4.4 Agendamento de Consulta

**Descrição:** O Paciente confirma um Slot disponível, respeitando antecedência mínima e sem permitir conflito de agenda mesmo sob concorrência simultânea. Esta é a regra de negócio central do sistema. Realiza JU-1, JU-4.

**Requisitos Funcionais:**

#### RF-6: Agendamento com antecedência mínima de 48h

Um Paciente pode agendar uma Consulta em qualquer Slot disponível que esteja a pelo menos 48h de antecedência da hora atual.

**Consequências (testáveis):**
- Tentativas de agendar um Slot a menos de 48h são rejeitadas com mensagem explicativa antes mesmo de chegar à etapa de confirmação.
- Ao confirmar, o sistema registra data/hora da Consulta, Paciente, Médico e Convênio (se aplicável).
- O Médico recebe uma notificação por push da nova Consulta agendada (notificação de evento, não de lembrete — ver §4.6).

#### RF-7: Bloqueio de conflito de agenda (concorrência)

O sistema deve garantir que nunca dois agendamentos coexistam para o mesmo Slot do mesmo Médico, mesmo quando duas requisições de agendamento chegam simultaneamente. Realiza JU-4.

**Consequências (testáveis):**
- Sob duas requisições concorrentes para o mesmo Slot, exatamente uma é aceita e a outra é rejeitada com mensagem clara ("Este horário acabou de ser reservado").
- Após a rejeição, a grade de horários do Paciente que perdeu a disputa é atualizada para refletir o Slot como ocupado, sem necessidade de recarregar a tela manualmente. [ASSUMPTION: atualização em tempo real via polling ou push; mecanismo exato é decisão de arquitetura.]
- Não existe nenhuma janela de tempo em que o sistema aceitaria as duas requisições (condição de corrida coberta por lock/transação atômica — decisão de implementação em `addendum.md`).

**RNFs específicos da funcionalidade:**
- A verificação de conflito deve ser atômica no nível de dados (ex.: constraint de unicidade ou transação com lock), não apenas checada e depois gravada em passos separados.

### 4.5 Cancelamento e Reagendamento

**Descrição:** Paciente ou Médico podem cancelar ou reagendar uma Consulta até 24h antes do horário marcado. Após esse prazo, o cancelamento é bloqueado totalmente pelo app — não há fluxo alternativo, aviso especial ao médico, registro de "cancelamento tardio" ou penalidade: a ação simplesmente deixa de estar disponível. Realiza JU-3.

**Requisitos Funcionais:**

#### RF-8: Cancelamento/reagendamento dentro da janela de 24h

Paciente ou Médico podem cancelar ou reagendar uma Consulta enquanto faltarem 24h ou mais para o horário marcado.

**Consequências (testáveis):**
- Ao cancelar dentro da janela, o Slot correspondente volta a ficar disponível imediatamente para outros Pacientes.
- Reagendar é um fluxo dedicado (não é cancelar + criar uma nova consulta): o usuário escolhe um novo dia/horário para a mesma Consulta, sujeito às mesmas regras de RF-6 e RF-7, e a Consulta original é atualizada em vez de substituída por um novo registro. Confirmado pelo protótipo de UX.
- A outra parte (Médico, se o Paciente cancelou; Paciente, se o Médico cancelou) é notificada do cancelamento.

#### RF-9: Bloqueio total de cancelamento após 24h

Nenhum ator (Paciente, Médico) pode cancelar ou reagendar uma Consulta cujo horário esteja a menos de 24h de distância, através do app (o instante exato de 24h ainda permite cancelamento — ver Glossário (§3), "Bloqueio de cancelamento").

**Consequências (testáveis):**
- A interface de cancelamento fica desabilitada/oculta para Consultas dentro dessa janela, com mensagem explicativa.
- Não existe rota, endpoint ou papel de usuário (incluindo suporte/admin, que não existe em v1) capaz de forçar esse cancelamento pelo sistema.

**Notas:** Esta é uma decisão de escopo deliberada e já validada no levantamento original — não tratar como gap. Simplifica o sistema por eliminar a necessidade de fluxos de exceção, avisos adicionais ou registro de penalidades.

### 4.6 Notificações

**Descrição:** O sistema envia lembretes automáticos de Consulta por push e e-mail, 24h antes do horário marcado, para o Paciente. O lembrete de rotina é exclusivo do Paciente; o Médico recebe apenas notificações de evento (nova consulta, cancelamento), não lembrete de rotina. [NOTE: SMS foi removido do escopo — decisão do usuário em 2026-09-17, não compensa o esforço/custo para um MVP de portfólio.]

**Requisitos Funcionais:**

#### RF-10: Lembrete de consulta 24h antes

O sistema envia automaticamente um lembrete por push e e-mail ao Paciente 24h antes do horário da Consulta.

**Consequências (testáveis):**
- O lembrete é despachado uma única vez por Consulta, no marco de 24h antes (não repete).
- Se a Consulta for cancelada antes do marco de 24h, o lembrete correspondente não é enviado.
- Os dois canais (push, e-mail) são disparados a partir do mesmo evento; falha em um canal não deve impedir o outro. [ASSUMPTION: canais são melhor esforço — não há retry garantido nem SLA formal em v1, dado o volume inicial baixo.]

**RNFs específicos da funcionalidade:**
- E-mail deve usar um provedor transacional com camada gratuita (ver `addendum.md` para a escolha técnica) — nenhum custo recorrente é aceitável no volume inicial. Ver §Restrições e Salvaguardas.

### 4.7 Autenticação e Recuperação de Senha

**Descrição:** Paciente ou Médico que esqueceu a senha pode redefini-la sem depender de suporte humano, já que o cadastro é feito por e-mail/senha e não há papel de administrador em v1.

**Requisitos Funcionais:**

#### RF-11: Recuperação de senha

Um usuário (Paciente ou Médico) que esqueceu a senha pode solicitar redefinição informando o e-mail cadastrado.

**Consequências (testáveis):**
- O sistema envia ao e-mail cadastrado um link ou código de redefinição de senha.
- O link/código expira após um período determinado. [ASSUMPTION: prazo exato de expiração a definir na Arquitetura, ex.: 30-60 minutos.]
- Após a redefinição, o usuário autentica-se com a nova senha e a senha anterior deixa de funcionar.

## 5. Não-Objetivos (Explícitos)

- Telemedicina ou qualquer forma de consulta remota dentro do app.
- Cobrança, pagamento ou qualquer fluxo financeiro dentro do app.
- Validação de credenciais médicas (CRM ou similar) — o autocadastro é deliberadamente aberto.
- Suporte a médicos com mais de uma especialidade.
- Edição ou remoção de convênios após seleção inicial, mesmo diante de consultas já agendadas.
- Integrações externas de qualquer tipo (operadoras de convênio, prontuário eletrônico, calendários de terceiros).
- Programa formal de conformidade com a LGPD (decisão de escopo do levantamento original — não há exigência formal em v1, dado o baixo volume e caráter de portfólio).
- iOS, versão web ou qualquer plataforma além de Android nativo em v1.
- Avaliações/reviews de médicos por pacientes — decisão confirmada de não incluir.
- Painel administrativo ou papel de "suporte" com poderes de override sobre regras de cancelamento/agendamento.

## 6. Escopo do MVP

### 6.1 Em Escopo

- Autocadastro de médico (sem validação) com definição de especialidade única, convênios (lista fixa) e agenda semanal.
- Cadastro de paciente.
- Busca de médicos por especialidade e localização (GPS + manual).
- Visualização de agenda em slots de 15 minutos, respeitando antecedência mínima de 48h.
- Agendamento com bloqueio de conflito de agenda sob concorrência.
- Cancelamento/reagendamento com janela de 24h e bloqueio total após esse prazo.
- Notificações de lembrete (push, e-mail) 24h antes da consulta.
- Recuperação de senha (redefinição via e-mail) para paciente e médico.

### 6.2 Fora de Escopo

- Qualquer forma de pagamento ou cobrança dentro do app.
- Telemedicina.
- Validação formal de credenciais médicas.
- Suporte a múltiplos convênios além da lista fixa ou edição dela.
- Plataformas além de Android.
- Painel administrativo / papel de suporte.
- Avaliações de médicos por pacientes — confirmado que não haverá essa funcionalidade em nenhuma versão.

## 7. Métricas de Sucesso

*Como projeto de portfólio, as métricas medem completude e qualidade demonstrável, não tração de mercado.*

**Primárias**
- **MS-1**: Demo funcional de ponta a ponta — cadastro de médico, cadastro de paciente, busca, agendamento, lembrete, cancelamento e recuperação de senha — executável sem erros em um dispositivo/emulador. Valida RF-1 a RF-11.
- **MS-2**: Zero ocorrências de conflito de agenda (double-booking) em testes de concorrência simulada (múltiplas requisições paralelas para o mesmo slot). Valida RF-7.

**Secundárias**
- **MS-3**: Cobertura de testes automatizados nas regras de negócio críticas (antecedência mínima, janela de cancelamento, bloqueio de conflito) — meta de referência: 80%+ nessas regras específicas, mesmo que a cobertura geral do app seja menor. Valida RF-6, RF-7, RF-8, RF-9.

**Contra-métricas (não otimizar)**
- **MS-C1**: Número de funcionalidades além do escopo fechado original — não deve crescer só para "parecer mais completo" no portfólio. Contrabalança MS-1: a demo deve ser sólida no que já foi definido, não ampla em funcionalidades não validadas.

## 8. Perguntas Abertas

Nenhuma pendente. A questão sobre os controles de cancelar/reagendar do Médico em "Minha Agenda" (RF-8) foi resolvida na quebra de épicos e histórias — ver Épico 2, História 2.5 em `epics.md`.

## 9. Índice de Assunções

- §4.3 (RF-4) — Sem GPS, busca manual retorna correspondência por região, ordenada alfabeticamente pelo nome do Médico como fallback (sem cálculo preciso de distância).
- §4.4 (RF-7) — Atualização em tempo real da grade após conflito é resolvida por realtime do banco de dados (decisão fechada na Arquitetura, ver `addendum.md`).
- §4.6 (RF-10) — Canais de notificação (push, e-mail) são melhor esforço, sem retry garantido ou SLA formal em v1. E-mail via Resend (camada gratuita); provedor de push ainda em aberto — tratado como decisão a fazer no momento oportuno (ver `addendum.md`).
- §4.7 (RF-11) — Prazo de expiração do link/código de recuperação de senha a definir na Arquitetura (ex.: 30-60 minutos).
- §RNFs Transversais — Todas as regras de tempo (15min, 24h, 48h) usam o fuso horário local do dispositivo; sem suporte a médico e paciente em fusos diferentes.

**Dependências pendentes do design visual** *(primeira versão importada em 2026-09-17 via `bmad-ux`; ver `_bmad-output/planning-artifacts/ux-designs/ux-AgendaMedica-2026-09-17/`)*:
- Campos exatos do formulário de cadastro de Médico (§4.1) e de Paciente (§4.2) — a versão atual do design usa apenas nome/e-mail/senha, mas o usuário sinalizou que pode crescer em versões futuras do design.

---

## RNFs Transversais

- **Consistência de dados sob concorrência:** a regra de bloqueio de conflito de agenda (RF-7) deve ser garantida no nível de armazenamento de dados (constraint de unicidade e/ou transação com lock), não apenas na camada de aplicação — é o requisito não-funcional mais crítico do sistema, dado que toda a "Complexidade Média" do projeto vem dele.
- **Confiabilidade de notificações:** falha em um canal (push/e-mail) não deve impedir o outro nem quebrar o fluxo de agendamento/cancelamento — notificação é best-effort, nunca bloqueante.
- **Disponibilidade:** dado o volume inicial (20 usuários/semana), não há exigência de alta disponibilidade formal (sem SLA), mas o app não deve perder ou duplicar uma Consulta já confirmada em nenhuma circunstância.
- **Desempenho:** buscas e visualização de agenda devem responder de forma percebida como instantânea para o volume-alvo (20 usuários/semana) — sem exigência de otimização para escala maior em v1.
- **Proteção de dados:** apesar de não haver exigência formal de LGPD (ver §Restrições e Salvaguardas → Privacidade), dados de Paciente e histórico de Consultas só podem ser lidos pelo próprio Paciente e pelo Médico das Consultas em questão — nenhum outro Paciente ou Médico tem acesso a esses dados. Autenticação obrigatória em toda operação que leia ou grave Consulta.
- **Fuso horário:** todas as regras de tempo (Slots de 30min com 15min de intervalo, antecedência mínima de 48h, janela de cancelamento de 24h) são calculadas no fuso horário local do dispositivo do usuário. [ASSUMPTION: sem suporte a médico e paciente em fusos horários diferentes — cenário improvável dado o volume e escopo do projeto.]

## Restrições e Salvaguardas

**Privacidade**
- Não há exigência formal de conformidade com a LGPD em v1 (decisão de escopo deliberada, dado o caráter de portfólio e volume baixo). [NOTE FOR PM: o app ainda trata dados de saúde por natureza (especialidade buscada, histórico de consultas); mesmo sem exigência formal, vale documentar no addendum boas práticas mínimas de proteção de dados como diferencial técnico do portfólio.]

**Custo**
- SMS foi removido do escopo de notificações (decisão do usuário, 2026-09-17): não compensa o esforço/custo para um MVP de portfólio, dado que não há provedor gratuito de envio de SMS de texto livre. E-mail usa um provedor transacional gratuito (Resend, ver `addendum.md`). Nenhum custo recorrente é aceitável no volume inicial.

## Plataforma

- Android nativo, v1 único. iOS e web ficam fora de escopo até segunda ordem.
- Um único aplicativo com dois perfis de acesso (Paciente e Médico), diferenciados após login — não dois apps separados.

## Monetização

- Nenhuma. O app não cobra de pacientes nem de médicos em nenhuma etapa.
