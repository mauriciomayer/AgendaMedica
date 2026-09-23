# Massa de demonstração

`demo-data.mjs` limpa o projeto Supabase **hospedado** e o popula com dados fictícios para demonstrações e prints:
8 médicos (6 especialidades, vários bairros e convênios), 6 pacientes e 15 consultas, sendo 1 a menos de 24h
(estado "Bloqueada" no app). Nenhum nome ou e-mail lembra "teste". Todos os e-mails terminam em `@example.com`.

O cadastro usa as Edge Functions reais (`register-doctor` / `register-patient`) e as consultas usam `book_appointment`,
então a massa obedece às mesmas regras do app. As consultas ficam marcadas como "lembrete já enviado" para o job de
lembretes não tentar escrever para os e-mails fictícios.

## Como usar

Pré-requisitos: `local.properties` com `SUPABASE_URL` e `SUPABASE_ANON_KEY`, e o projeto vinculado (`npx supabase link`).
A senha das contas vem da variável `DEMO_PASSWORD` (mínimo 6 caracteres) e não fica no código.

```bash
# Só mostra o que seria criado; não toca na base
node supabase/seed/demo-data.mjs plan

# Limpa TUDO e repopula (exige --yes)
DEMO_PASSWORD=<senha> node supabase/seed/demo-data.mjs all --yes

# Só cria (base já vazia) / só limpa
DEMO_PASSWORD=<senha> node supabase/seed/demo-data.mjs seed
node supabase/seed/demo-data.mjs wipe --yes
```

No PowerShell: `$env:DEMO_PASSWORD = "<senha>"; node supabase/seed/demo-data.mjs all --yes`.

## Atenção

- `wipe` e `all` apagam **todos os usuários** do projeto (cascata: perfis, médicos, pacientes, agenda, consultas e eventos),
  inclusive contas criadas à mão. Não há como desfazer.
- As datas são relativas a hoje (consultas de 3 a ~14 dias à frente). Depois que passarem, rode `all --yes` de novo.
- A consulta "Bloqueada" só existe se amanhã for um dia de atendimento da Dra. Marina Fontes (terça ou quinta); e só
  continua "a menos de 24h" durante um dia.
