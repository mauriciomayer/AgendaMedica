# Checklist de Teste Manual — Agenda Médica

Este arquivo reúne **todos os casos de teste manual** das Stories 1.1 a 4.1, para você validar o app instalado no celular contra o projeto Supabase hospedado (`vuqvizzkdeiseyunjrms`). Tudo que já tem prova automatizada (testes unitários Kotlin, scripts contra o banco) **não** está repetido aqui — este arquivo cobre só o que precisa de olho humano e de um aparelho real: aparência, gestos, GPS, e-mail de verdade, dois usuários ao mesmo tempo, etc.

Marque `[x]` conforme for testando. Onde o resultado não bater com o esperado, anote o que aconteceu (print ajuda) e me avise.

---

## Como usar este checklist

1. Instale a build mais recente do app no celular (`./gradlew assembleDebug` + instalar o APK, ou rodar direto do Android Studio).
2. O app já aponta para o projeto hospedado (`local.properties`); não precisa de Supabase local nem Docker.
3. Sugestão de contas para reaproveitar em vários testes (evita recriar conta toda hora):
   - **1 médico**: nome à sua escolha, especialidade "Cardiologia", localização "Pinheiros, São Paulo - SP", convênios "Unimed" + "Particular", agenda em pelo menos 2 dias da semana.
   - **2 pacientes**: "Paciente A" e "Paciente B" (senhas ≥ 6 caracteres).
   - Para os testes de **e-mail real** (recuperação de senha e lembrete 24h, seções 1.3 e 3.1), cadastre um paciente com o **seu e-mail de verdade** — o Supabase só envia e-mail de recuperação para e-mails reais, e o Resend (lembrete) sem domínio verificado só entrega para o e-mail dono da conta Resend.
4. Alguns casos (agenda a menos de 24h/48h, concorrência) não são alcançáveis só navegando no app, porque agendar sempre exige ≥48h de antecedência. Esses casos estão marcados **[Avançado — requer SQL]** com o comando pronto para colar; são opcionais, mas fecham a cobertura. Se preferir pular, tudo bem — já têm prova automatizada.
5. No fim de cada sessão de teste, você pode apagar as contas de teste em **Authentication → Users** no painel do Supabase (cascata remove tudo: perfil, paciente/médico, consultas).

---

## Épico 1 — Cadastro e Autenticação

### Story 1.1 — Médico se cadastra e configura seu perfil profissional

- [ ] **Cadastro válido**: abrir o app → "Sou médico" → preencher nome, e-mail, senha (≥6), especialidade, ao menos 1 convênio, ao menos 1 dia de agenda → "Criar perfil". Esperado: conta criada, login automático, cai em "Minha Agenda" vazia.
- [ ] **Botão desabilitado sem especialidade** (ou sem convênio, ou sem dia de agenda): o botão "Criar perfil" deve continuar desabilitado até tudo estar preenchido.
- [ ] **Login correto**: sair e logar de novo com o mesmo e-mail/senha → volta a Minha Agenda.
- [ ] **Login errado**: senha errada → mensagem de erro visível, sem termos técnicos.
- [ ] **Falha de rede**: ativar modo avião e tentar cadastrar/logar → mensagem genérica ("tente novamente"), sem crash.
- [ ] **Visual**: conferir que as telas (Login, Escolha, Cadastro de Médico, Minha Agenda) usam as cores/tipografia/formas do design (nada "cru" do Material padrão).

### Story 1.2 — Paciente se cadastra

- [ ] **Cadastro válido**: "Sou paciente" → nome, e-mail, senha → "Criar conta" (repare que não há campo de Convênio). Esperado: autenticado, cai na tela de Busca.
- [ ] **Campo obrigatório vazio ou e-mail inválido**: botão "Criar conta" continua desabilitado.
- [ ] **E-mail já cadastrado**: tentar cadastrar de novo com um e-mail já usado (de médico ou paciente) → mensagem "Já existe uma conta com este e-mail.", nenhuma conta nova criada.
- [ ] **Login por papel**: logar com a conta do paciente → cai em Busca; logar com a conta do médico → continua caindo em Minha Agenda.
- [ ] **Falha de rede**: modo avião ao cadastrar → mensagem genérica, sem crash.

### Story 1.3 — Usuário recupera a senha esquecida

> **Pré-requisito único**: no painel do Supabase, em Authentication → URL Configuration → Redirect URLs, confirme que `agendamedica://reset-password` está cadastrado (se você já fez isso, pule). Use uma conta de paciente ou médico com **seu e-mail real**.

- [ ] **Pedido de recuperação**: Login → "Esqueci minha senha" → digitar o e-mail real cadastrado → enviar. Esperado: mensagem neutra ("Se o e-mail informado existir, enviamos um link...").
- [ ] **Mesma mensagem para e-mail inexistente**: repetir com um e-mail que não existe → mensagem idêntica (não revela se a conta existe).
- [ ] **E-mail vazio/inválido**: botão "Enviar link de recuperação" desabilitado.
- [ ] **Link chega e funciona**: abrir o e-mail recebido no celular → tocar no link → o app abre direto na tela "Nova Senha" → definir uma senha nova (≥6) → salvar. Esperado: volta ao Login com o aviso "Senha redefinida. Entre com a nova senha."
- [ ] **Senha antiga não funciona mais / nova funciona**: tentar logar com a senha antiga → falha; logar com a nova → entra normalmente.
- [ ] **Link usado de novo**: abrir o mesmo link do e-mail uma segunda vez → mensagem "Este link expirou ou já foi usado. Solicite um novo link.", sem crash.
- [ ] **Nova senha inválida**: na tela Nova Senha, digitar menos de 6 caracteres → botão "Salvar nova senha" desabilitado.

---

## Épico 2 — Busca e Agendamento

### Story 2.1 — Paciente busca médicos por especialidade e localização

> Cadastre 2-3 médicos com especialidades e localizações diferentes antes de testar (pode reaproveitar o médico da Story 1.1 e criar mais 1-2).

- [ ] **Lista sem filtro**: abrir Busca sem mexer em nada → todos os médicos aparecem, em ordem alfabética por nome.
- [ ] **Filtro por especialidade**: escolher uma especialidade específica → só aparecem médicos dela; escolher "Todas" → volta a mostrar todos.
- [ ] **Card do médico**: cada card mostra iniciais, nome, especialidade, cidade e os chips de convênio.
- [ ] **GPS permitido**: tocar "Usar minha localização" e conceder a permissão → resultados ordenados por distância (o mais perto primeiro); o campo passa a mostrar "Minha localização".
- [ ] **GPS negado**: negar a permissão → mensagem pedindo para buscar manualmente; a busca por texto continua funcionando.
- [ ] **Busca manual por região**: digitar "pinheiros" (ou outro bairro/cidade cadastrado) → só médicos dessa região, ordenados por nome. Digitar algo sem correspondência → "Nenhum médico encontrado com esses filtros."
- [ ] **Digitar cancela o modo GPS**: com "Minha localização" ativo, comece a digitar um bairro → volta ao modo manual (não fica travado em GPS).
- [ ] **Falha de rede**: modo avião ao abrir a Busca → mensagem genérica com "Tentar novamente"; tocar em "Tentar novamente" com a rede de volta → carrega normalmente.

### Story 2.2 — Paciente visualiza horários disponíveis de um médico

- [ ] **Abrir o Detalhe**: tocar num card da Busca → abre o Detalhe do Médico com nome, especialidade, cidade, convênios e um carrossel de dias.
- [ ] **Carrossel**: só aparecem os dias em que o médico atende (conforme a agenda cadastrada), no máximo 6 dias, nenhum selecionado ao abrir.
- [ ] **Selecionar um dia**: a grade de horários aparece em 3 colunas, com horários de 30 minutos e 15 minutos de intervalo entre eles (de 45 em 45 minutos).
- [ ] **Slot a menos de 48h desabilitado**: horários de hoje e de amanhã (dentro de 48h) aparecem desabilitados com o motivo "Antecedência mín. 48h".
- [ ] **Slot com 48h ou mais**: aparece habilitado e selecionável (fica destacado ao tocar).
- [ ] **Trocar de dia limpa a seleção**: selecionar um horário, trocar de dia no carrossel → a seleção de horário some.
- [ ] **Dia sem horário** (se algum dia do carrossel não tiver agenda): "Sem atendimento neste dia."
- [ ] **Voltar**: botão voltar leva de volta à Busca.
- [ ] **Falha de rede**: modo avião ao abrir o Detalhe → mensagem genérica com "Tentar novamente".

- [ ] **[Avançado — requer SQL] Horário "Ocupado"**: rode o comando abaixo trocando `<DOCTOR_ID>` (pegue o id do médico com `select id from doctors where name = '<nome do médico>'`) e o horário por um dentro da agenda do médico e a ≥48h de agora:
  ```
  npx supabase db query --linked "insert into booked_slots (doctor_id, start_time) values ('<DOCTOR_ID>', '2026-10-10 14:00:00+00')"
  ```
  Abra o Detalhe desse médico no dia/horário inserido → o slot aparece desabilitado com "Ocupado" (tem prioridade sobre "Antecedência mín. 48h" se as duas se aplicarem). Depois apague com:
  ```
  npx supabase db query --linked "delete from booked_slots where doctor_id = '<DOCTOR_ID>'"
  ```

### Story 2.3 — Paciente agenda uma consulta sem conflito de horário

- [ ] **Agendamento válido**: no Detalhe, escolha dia + horário (≥48h) + convênio (chips; já vem marcado se o médico só aceita 1) → "Confirmar agendamento". Esperado: tela de Confirmação com médico, especialidade, data/hora e convênio corretos.
- [ ] **Botão incompleto**: sem médico/dia/horário/convênio escolhido, "Confirmar agendamento" fica desabilitado.
- [ ] **Sem duplo agendamento**: tocar "Confirmar agendamento" e tentar tocar de novo rapidamente → só uma consulta é criada (o botão fica desabilitado durante o envio).
- [ ] **Concorrência com Realtime (2 aparelhos ou 1 aparelho + o script)**: abra o Detalhe do mesmo médico/horário em dois pacientes diferentes (2 celulares, ou 1 celular + rodar `npm run test:concurrency` no computador mirando o mesmo horário). Ao um confirmar primeiro, o outro deve:
  - ver o horário virar "Ocupado" na grade **sem precisar recarregar a tela** (é o Realtime atualizando ao vivo); e
  - se tentar confirmar mesmo assim, ver a mensagem "Este horário acabou de ser reservado, escolha outro." e a seleção some.
- [ ] **Falha de rede ao confirmar**: modo avião bem na hora de tocar "Confirmar agendamento" → mensagem genérica, sem crash, sem navegar para a Confirmação.

### Story 2.4 — Paciente cancela ou reagenda uma consulta

- [ ] **Acesso a Minhas Consultas**: pela Busca (ação "Minhas consultas") e pela tela de Confirmação ("Ver minhas consultas") — as duas devem levar à mesma lista.
- [ ] **Listagem**: consultas futuras confirmadas aparecem por data crescente, cada card com médico, especialidade, data/hora, convênio e selo "Confirmada".
- [ ] **Lista vazia**: sem nenhuma consulta agendada → "Você ainda não tem consultas agendadas." + botão "+ Nova consulta" (deve levar de volta à Busca).
- [ ] **Cancelar com confirmação**: tocar "Cancelar" no card → aparece a confirmação inline no próprio card (Sim/Não), **não** um pop-up separado. Tocar "Não" → fecha, nada muda. Tocar "Sim" → o card some da lista.
- [ ] **Horário libera após cancelar**: depois de cancelar, volte à Busca/Detalhe daquele médico (pode ser com outro paciente) e confirme que o horário está livre de novo.
- [ ] **Reagendar**: tocar "Reagendar" num card → abre o Detalhe do médico em modo "Reagendar consulta" (sem escolha de convênio), escolher novo dia/horário → "Confirmar novo horário". Esperado: volta a Minhas Consultas já atualizada, com a **mesma consulta** (não uma nova) no novo horário.
- [ ] **Reagendar com conflito**: se possível, provoque o mesmo horário sendo ocupado por outro paciente enquanto reagenda → "Este horário acabou de ser reservado, escolha outro.", a consulta original permanece como estava.

- [ ] **[Avançado — requer SQL] Consulta bloqueada por 24h**: insira uma consulta confirmada a menos de 24h para o seu paciente de teste:
  ```
  npx supabase db query --linked "insert into appointments (patient_id, doctor_id, start_time, insurance) values ('<PATIENT_ID>', '<DOCTOR_ID>', now() + interval '10 hours', 'Unimed')"
  ```
  (pegue `PATIENT_ID`/`DOCTOR_ID` com `select id from auth.users where email = '...'`). Abra Minhas Consultas → o card dessa consulta mostra "Cancelar"/"Reagendar" **desabilitados** com a nota "Bloqueado: faltam menos de 24h — não é mais possível cancelar ou reagendar.". Apague depois com `delete from appointments where id = '<ID>'`.

### Story 2.5 — Médico cancela ou reagenda uma consulta de um paciente

- [ ] **Minha Agenda mostra as consultas**: logado como médico, a tela "Minha Agenda" (além do cartão de perfil) lista "Próximas consultas" com paciente, data/hora, convênio e selo — use o paciente de teste para agendar algo com esse médico primeiro, se ainda não houver nada agendado.
- [ ] **Sem consultas**: médico sem nenhuma consulta futura → "Nenhuma consulta agendada ainda." (perfil continua visível).
- [ ] **Cancelar pelo médico**: "Cancelar" → confirmação inline → "Sim" → card some; o horário libera (confirme na Busca do paciente).
- [ ] **Evento para o paciente**: depois do médico cancelar/reagendar, não há UI de notificação ainda (Épico 3.2 só registra o evento no banco) — não espere nada visível no app do paciente aqui, é esperado.
- [ ] **Reagendar pelo médico**: "Reagendar" → abre o Detalhe da própria agenda do médico em modo Reagendar → escolher novo horário → volta a Minha Agenda com a mesma consulta atualizada.
- [ ] **Consulta a menos de 24h bloqueada** (reaproveite o passo avançado da 2.4, mas coloque o médico como `doctor_id` da consulta de teste): botões desabilitados com a nota, igual ao lado do paciente.
- [ ] **Falha de rede**: modo avião ao abrir Minha Agenda → mensagem genérica com "Tentar novamente", perfil continua visível.

---

## Épico 3 — Notificações de Consulta

### Story 3.1 — Paciente recebe lembrete de consulta por e-mail 24h antes

> Este teste é o mais "de infraestrutura" do lote: não há nada para tocar no app, o e-mail chega sozinho. Precisa da `RESEND_API_KEY` já configurada (você confirmou que já rodou o `secrets set`) e de um paciente cadastrado com **o e-mail dono da sua conta Resend** (sem domínio verificado, o Resend só entrega para esse e-mail).

- [ ] **[Avançado — requer SQL] Lembrete chega por e-mail**: insira uma consulta confirmada a ~23h30 de agora para esse paciente:
  ```
  npx supabase db query --linked "insert into appointments (patient_id, doctor_id, start_time, insurance) values ('<PATIENT_ID_COM_SEU_EMAIL>', '<DOCTOR_ID>', now() + interval '23 hours 30 minutes', 'Unimed')"
  ```
  Espere até 5 minutos (o job `pg_cron` roda nesse intervalo) e confira sua caixa de entrada. Esperado: e-mail em português, do remetente `Agenda Médica <onboarding@resend.dev>`, com o assunto mencionando o médico, e o corpo com médico, especialidade, data/hora, bairro/cidade e convênio, avisando que não é mais possível cancelar/reagendar pelo app.
- [ ] **Não reenvia**: espere mais um ciclo (5 min) → o mesmo e-mail não chega de novo. Confirme com:
  ```
  npx supabase db query --linked "select reminder_sent_at from appointments where id = '<ID_DA_CONSULTA>'"
  ```
  deve estar preenchido (não nulo).
- [ ] **Se quiser conferir mais rápido, sem esperar o cron**, chame a função diretamente (usa a mesma `REMINDER_CRON_SECRET` do `local.properties`):
  ```
  curl -X POST "https://vuqvizzkdeiseyunjrms.supabase.co/functions/v1/send-reminders" -H "x-cron-secret: <REMINDER_CRON_SECRET do local.properties>" -H "Content-Type: application/json" -d "{}"
  ```
  Resposta esperada: `{"due":1,"sent":1,"failed":0}` (ou mais, se houver outras consultas devidas).
- [ ] Apague a consulta de teste depois: `npx supabase db query --linked "delete from appointments where id = '<ID>'"`.

### Story 3.2 — Sistema registra eventos de notificação para entrega futura

> Não há nada visível no app para esta história — é só registro interno para um futuro envio por push, já coberto por teste automatizado (`npm run test:notification-events`). Se quiser só **ver com os próprios olhos** que o evento é gravado, é opcional:

- [ ] **[Opcional — requer SQL] Ver o evento sendo criado**: agende uma consulta pelo app normalmente, depois rode:
  ```
  npx supabase db query --linked "select event_type, recipient_id, appointment_id, delivered_at from notification_events order by created_at desc limit 5"
  ```
  Esperado: uma linha `new_appointment` com `recipient_id` = id do médico e `delivered_at` nulo. Cancele ou reagende essa mesma consulta pelo app e rode de novo → aparece uma nova linha `cancellation`/`reschedule` com `recipient_id` = a outra parte (quem não fez a ação).

---

## Épico 4 — Identidade Visual do App

### Story 4.1 — App exibe splash screen e ícone próprios com o novo logo

- [ ] **Splash ao abrir do zero**: force-feche o app (não só minimize) e abra de novo pelo ícone. Esperado: por ~1,6 segundo aparece uma tela com o logo (documento branco com selo de cruz vermelha) centralizado e o texto "Agenda Médica" abaixo, sobre um fundo claro — depois segue sozinha para o Login, sem você tocar em nada.
- [ ] **Girar o aparelho durante a splash**: force-feche e abra de novo; assim que a splash aparecer, gire o celular rapidamente. Esperado: a splash não reinicia a contagem do zero — o tempo total até chegar ao Login continua sendo por volta de 1,6s (contado desde a primeira abertura), não ~3,2s.
- [ ] **Voltar depois da splash**: deixe a splash passar até o Login, aperte o botão voltar do sistema. Esperado: sai do app (a splash não fica "presa" na pilha de navegação para onde voltar retornaria).
- [ ] **Ícone do app no launcher**: com o app instalado, veja o ícone na tela de apps/launcher do celular. Esperado: mostra o mesmo logo (documento + selo de cruz vermelha) sobre um fundo claro, não mais o ícone padrão azul com calendário.
- [ ] **Formato do ícone** (se o seu launcher permitir trocar a forma do ícone, em Configurações → Ícones/Launcher, ou comparando com outro launcher instalado): confira que o logo continua legível e bem centralizado tanto em máscara redonda quanto quadrada/squircle, sem cortar o selo vermelho.

---

## Depois de terminar

- Apague as contas de teste em **Authentication → Users** no painel do Supabase (a exclusão do usuário remove em cascata perfil, paciente/médico e consultas).
- Se algum passo "Avançado" deixou uma consulta ou horário de teste para trás, confirme que já foi removido com os comandos `delete` indicados.
- Qualquer resultado diferente do esperado: anote a story, o passo exato e (se possível) um print — me manda que eu investigo.
