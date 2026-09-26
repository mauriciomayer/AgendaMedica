---
title: 'Barra do topo compartilhada e telas de acesso no layout do protótipo'
type: 'feature'
created: '2026-09-25'
status: 'done'
route: 'oneshot'
review_loop_iteration: 0
context: ['{project-root}/_bmad-output/implementation-artifacts/epic-8-context.md']
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Só a Login (Stories 6.6/6.7) segue o protótipo de design. As telas de acesso (Criar conta, Recuperar senha, Nova senha, Cadastro de paciente) ainda têm um botão de voltar solto, o título sobre o fundo verde-claro e campos de rótulo flutuante. O usuário pediu que as demais telas fiquem próximas do design, em sequência (Épico 8), e decidiu manter Cancelar/Reagendar, incluir a fonte Inter e usar avatar azul cheio (Stories seguintes).

**Approach:** Criar as peças compartilhadas do protótipo em `ui/components/Layout.kt` — `TelaPadrao` (barra branca com título em negrito, subtítulo opcional, botão de voltar redondo de 36 dp com toque de 48 dp, ações à direita, linha fina, corpo rolável com padding de 18 dp e `imePadding`), `InfoBox` (caixa de mensagem com 3 tons e live region), `AppCard` e `CartaoEscolha` — e aplicá-las à Login (refatorada sobre o mesmo shell) e às 4 telas de acesso. Criar conta vira dois cartões clicáveis por inteiro (paciente primeiro); Recuperar senha ganha o texto de apoio e, depois do envio, troca o formulário por uma caixa verde + "Voltar ao login"; Cadastro de paciente e Nova senha usam rótulo acima e exemplo dentro. ViewModels, regras e navegação não mudam. As demais telas (Busca, Detalhe, Confirmação, Minhas consultas, Cadastro de médico, Minha agenda) ficam para as histórias 8.2 a 8.6.

</frozen-after-approval>

## Implementation Notes

Peças novas em `ui/components/Layout.kt`: `TelaPadrao`, `InfoBox`/`TomInfoBox`, `AppCard`, `CartaoEscolha` (o `BotaoVoltar` é privado). `LoginScreen` foi reescrita sobre `TelaPadrao` e `InfoBox` (o `CaixaMensagem` privado saiu); `EscolhaScreen`, `RecuperarSenhaScreen`, `NovaSenhaScreen` e `CadastroPacienteScreen` foram reescritas. Mudanças de texto por seguir o protótipo: "Cadastro de Paciente" -> "Cadastro de paciente", "Nome" -> "Nome completo", pergunta da Criar conta "…usar o app?", paciente antes de médico. Recuperar senha, depois de enviado, não mostra mais o campo (como o protótipo); para corrigir o e-mail o usuário volta ao Login e repete "Esqueci minha senha". `AccessibleIconButton` continua nas telas que ainda não migraram (Busca, Detalhe, Minhas consultas, Cadastro de médico) e sai na última história do épico. `EmailFieldWiringTest` localiza o campo de e-mail por posição quando o rótulo está acima.

Verificação: `./gradlew assembleDebug testDebugUnitTest` -> `BUILD SUCCESSFUL` (238 testes). Testes novos: `TelaPadraoTest` (título como heading, subtítulo, voltar com toque de 48 dp, ações à direita sem sobrepor o título, título longo quebrando em 2 linhas — em modo gráfico NATIVE, corpo maior que a tela rolando, live region Polite nos 3 tons, cartão de escolha clicável por inteiro) e `TelasDeAcessoTest` (Criar conta, Recuperar senha nos estados normal/enviado/expirado/limite, Nova senha com título, dica, olho e voltar da barra + botão voltar do sistema, Cadastro de paciente). Conferido visualmente por capturas do Robolectric das 4 telas.

## Review Triage Log

Camada blind-hunter (N=8, 12 achados).

| Achado | Veredito | Evidência / rota |
|---|---|---|
| `TelaPadrao` não trata o teclado (app é edge-to-edge) | `medium`, patch | `imePadding()` no corpo |
| Altura de linha do título mantida do estilo maior (28 sp / 24 sp) | `low`, patch | `lineHeight` explícito (22 sp na barra, 20 sp no cartão) |
| Título com `maxLines = 1` é cortado em fonte grande | `low`, patch | Até 2 linhas; teste em modo NATIVE (o modo padrão não mede a largura do texto) |
| Altura da barra varia entre telas (com/sem voltar) | `low`, rejeitado | O protótipo também varia (com voltar ~68 dp, Login ~72 dp) |
| Recuperar senha perde a chance de corrigir o e-mail | `low`, rejeitado | Comportamento do protótipo, aprovado no plano do épico; volta ao Login |
| Nova senha sem teste de voltar da barra/do sistema, títulos, erros | `medium`, patch | Testes adicionados (barra + `pressBack`, título como heading, dica, olho; erro de limite na Recuperar) |
| `BotaoVoltar` duplica `AccessibleIconButton` | `low`, rejeitado | Temporário: as demais telas migram nas próximas histórias e o antigo sai na última |
| `InfoBox` como live region só é seguro se composto quando há mensagem | `low`, patch | KDoc alertando; todos os chamadores já compõem só quando há mensagem |
| Tom carregado só por cor / sem prefixo de erro | `low`, rejeitado | O texto da própria mensagem diz o que houve; padrão já usado na Login (6.7) |
| API mistura português/inglês; `scrollable = false` sem teste | `low`, rejeitado | O projeto já mistura (`formatarDataHora`, `TelaPadrao`); `scrollable = false` será usado e testado na Busca (8.2) |
| `AppCard` complicado (lambda + Box + clip + clickable) | `low`, patch | Reescrito com `Card(onClick)` do Material3 (semântica de clique, ripple e estado corretos) |
| Testes fracos (live region sem modo, ação sobrepondo título, sem rolagem) | `low`, patch | Verifica `Polite`, não sobreposição, faixa vertical e rolagem |
| Mudanças de texto/ordem não sinalizadas | `low`, patch | Registradas nestas notas |
