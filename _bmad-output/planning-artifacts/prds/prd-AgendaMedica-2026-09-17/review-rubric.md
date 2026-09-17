# PRD Quality Review — Agenda Médica

## Overall verdict

This PRD holds up well: it has a genuine thesis (a concurrency-safe scheduling core, not a feature list), FRs with mostly testable consequences, disciplined glossary/ID/cross-reference hygiene, and honest scope framing (explicit non-goals, indexed assumptions, NOTE FOR PM callouts at real tensions like the LGPD/health-data note and the "can a doctor change specialty" gap). The main risks are mechanical rather than structural: a couple of unbounded performance/timing phrases that should carry numbers, and three references to `addendum.md` as a decision-recording target even though that file does not exist yet for this PRD. Nothing here rises to broken or thin on any dimension.

## Decision-readiness — strong

The PRD states decisions rather than hedging them. RF-9 and its "Notas" (§4.5) commit hard to zero exceptions on the 24h cancellation lock — "sem exceção manual possível por ninguém, nem pelo médico" — and explicitly frames this as a deliberate simplification, not an oversight ("Esta é uma decisão de escopo deliberada e já validada... não tratar como gap"). §5 Não-Objetivos and §Restrições e Salvaguardas do real work naming what's given up (LGPD formality, payments, telemedicine, admin override) rather than smoothing them into "future consideration."

`[NOTE FOR PM]` callouts land on genuine tensions, not safe checkpoints: §4.1 RF-2's "Fora de Escopo" flags that specialty-change-after-registration is unresolved and asks the PM to confirm whether it needs to become a v2 RF; §Restrições e Salvaguardas flags that the app handles health-adjacent data despite no formal LGPD program. Both are live open items a decision-maker would actually need to weigh, not rhetorical questions answered in the next sentence. §8 Perguntas Abertas (notification provider; how the external visual design gets folded back into the PRD) are genuinely unresolved rather than pre-answered.

### Findings
- **low** Specialty-change decision is flagged but not escalated to §8 (§4.1, RF-2 "Fora de Escopo") — The `[NOTE FOR PM: confirmar se isso é aceitável ou se precisa virar um RF de v2.]` is a real open decision with data-model consequences (soft-delete vs. hard field), but it only lives as an inline note, not in the Perguntas Abertas list where a reviewer scanning open items would see it. *Fix:* either promote it to §8 as OQ-3, or leave it as is if the PM is confident it will be resolved before Architecture — but make that call explicitly rather than by omission.

## Substance over theater — strong

Personas are load-bearing, not decorative: Paciente and Médico JTBDs (§2.1) each map directly onto FRs and JUs, and the third persona, "Mauricio (o builder)," is explicitly scoped as valid *because* this is a portfolio project (§2.1, "válido em projeto de portfólio") — it's the persona that explains why RF-7 is treated as the system's central requirement and why MS-2/MS-3 exist. That's the opposite of persona theater.

The Vision (§1) refuses innovation theater by name: "não compete por mercado nem mira um cliente específico" — it doesn't pretend to a market thesis it doesn't have, and instead grounds the pitch in a verifiable technical claim (concurrency-safe scheduling with interlocking constraints). A search for boilerplate NFR adjectives ("robusto," "escalável," "seguro," "confiável," "eficiente," "amigável") returns zero hits in the document — the RNFs Transversais section (bottom of file) is specific to this system's actual pressure points (data-layer atomicity, notification best-effort, no-SLA availability at 20 users/week) rather than copied boilerplate.

### Findings
- **medium** "Desempenho" NFR uses an unbounded adjective (RNFs Transversais, "Desempenho") — "devem responder de forma percebida como instantânea" has no number attached (no p95/ms target), which is exactly the "reasonable performance" pattern the rubric flags, even though the surrounding qualifier (20 users/week, no need to optimize for scale) does most of the honesty work. *Fix:* attach even a loose bound, e.g. "resposta percebida em até ~1s para o volume-alvo," so a tester has something to check against.

## Strategic coherence — strong

The PRD has an explicit, single thesis and it drives the document: "demonstrar... a capacidade de projetar e implementar corretamente uma regra de negócio central com múltiplas restrições entrelaçadas — antecedência mínima de agendamento, janela de cancelamento, e bloqueio de concorrência de agenda" (§1). Section 4.4 calls RF-7 "Esta é a regra de negócio central do sistema," and RNFs Transversais makes the thesis-to-priority link explicit: the data-layer concurrency constraint is "o requisito não-funcional mais crítico do sistema, dado que toda a 'Complexidade Média' do projeto vem dele." Feature exclusions (payments, telemedicine, admin panel, multi-specialty doctors) all serve keeping that core rule airtight rather than expanding surface area — this is scope logic that follows from the thesis, not "easiest first."

Success Metrics validate the thesis rather than measuring activity: MS-2 (zero double-booking under simulated concurrency) and MS-3 (80%+ coverage on the critical rules) are thesis-native, and MS-C1 is a genuine counter-metric against scope creep ("Número de funcionalidades além do escopo fechado original — não deve crescer só para 'parecer mais completo'"). Per the task framing, the portfolio-oriented (not market-oriented) shape of §7 is a deliberate, stated choice ("Como projeto de portfólio, as métricas medem completude e qualidade demonstrável, não tração de mercado") and is scored as a strength, not a gap.

## Done-ness clarity — adequate

Most FRs carry genuinely testable consequences: RF-7's concurrency behavior ("exatamente uma é aceita e a outra é rejeitada"), RF-9's negative assertion ("Não existe rota, endpoint ou papel de usuário... capaz de forçar esse cancelamento"), and RF-10's multi-channel independence ("falha em um canal não deve impedir os demais") are all things an engineer could write a test against without further interpretation. Per-FR "Fora de Escopo" subsections (e.g., RF-2) sharpen done-ness further by bounding what the FR does *not* have to handle.

Two spots still rely on unbounded language that this dimension is supposed to catch:

### Findings
- **medium** Unbounded timing claim in RF-1 (§4.1, RF-1 consequências) — "o perfil do Médico aparece em resultados de busca compatíveis em até poucos segundos" gives no concrete bound, so "done" for search-indexing latency is a matter of interpretation. *Fix:* replace "poucos segundos" with a number (even a loose one, e.g. "≤5s") or state it's synchronous/immediate upon save if that's the actual intended behavior.
- **low** RF-11's expiration window is correctly tagged as an assumption to resolve in Architecture (§4.7, "[ASSUMPTION: prazo exato de expiração a definir na Arquitetura, ex.: 30-60 minutos.]") — this is handled honestly (assumption + indexed), so it's a minor note rather than a gap: flagging only so Architecture is held to actually closing it, since a dangling reset-token TTL is a common security oversight if the assumption never gets revisited.

## Scope honesty — strong

§5 Não-Objetivos does real work — ten explicit exclusions, several with the reasoning attached (e.g., "Programa formal de conformidade com a LGPD... decisão de escopo do levantamento original — não há exigência formal em v1, dado o baixo volume e caráter de portfólio"). §6.2 restates MVP exclusions consistently with §5 — no drift between the two.

Assumptions Index roundtrip is clean: all five inline `[ASSUMPTION: ...]` tags (§4.1/RF-2, §4.3/RF-4, §4.4/RF-7, §4.6/RF-10, §4.7/RF-11) are indexed in §9 with matching section references, and no index entry lacks an inline counterpart. Open-items density (2 Open Questions + 5 Assumptions + ~4 NOTE FOR PM callouts) is proportionate for a portfolio-scale PRD carrying launch-level rigor — enough to show the honest edges of the spec without drowning the document in caveats.

De-scoping reads as proposed, not silent: e.g., "Alteração de Especialidade após o cadastro inicial (fora de escopo — exigiria excluir e recriar o perfil)" (§4.1 RF-2) states the workaround cost of the exclusion rather than just omitting the capability.

## Downstream usability — strong

This PRD is chain-top by its own declaration (§0: "orientar as próximas etapas do fluxo BMad (Arquitetura, Épicos e Histórias, Sprint Planning e Build)"), so this dimension carries real weight, and it holds up. Glossary (§3) terms — Paciente, Médico, Especialidade, Convênio, Consulta, Slot, Agenda, Antecedência mínima, Janela de cancelamento, Bloqueio de cancelamento, Conflito de agenda, Autocadastro — are used consistently and capitalized as defined terms throughout the FRs and journeys.

ID continuity is clean: RF-1 through RF-11 contiguous with no gaps or duplicates; JU-1 through JU-4 contiguous; MS-1/MS-2/MS-3/MS-C1 consistently structured. Cross-references resolve both directions — JUs cite "Realiza RF-x" (e.g., JU-1 → RF-4, RF-5, RF-6, RF-7; JU-4 → RF-7) and RFs cite "Realiza JU-x" back (RF-2 → JU-2, RF-5 → JU-1, RF-7 → JU-4); the "Mapeamento capacidade → RF" block (§2.3) and the SM "Valida RF-x" lines (§7) all point at RF numbers that exist. Every UJ has a named, contextualized protagonist (Fernanda, Dr. Ricardo, Fernanda + Bruno) — no floating UJs.

## Shape fit — strong

The product is a two-sided consumer app (patients and doctors), so UJ-driven format is the right shape, and the PRD uses it at a proportionate density (4 UJs, not sprawling). Per the task's framing, this is explicitly a hobby/portfolio project that chose launch-level rigor, and the PRD calibrates well: it keeps the rigor of testable FRs, a glossary, and assumption tracking, while being honest about where hobby-scale reality changes the bar — no formal LGPD program, no SLA-backed availability target, SMS routed through a free-tier provider at low volume (§Restrições e Salvaguardas, RNFs Transversais). The "Mauricio (o builder)" persona is explicitly flagged as valid only "em projeto de portfólio" rather than smuggled in as a generic stakeholder — good self-awareness about the shape. Success Metrics are deliberately portfolio-oriented per the task's stated framing, and correctly not treated as a flaw here.

## Mechanical notes

- **`addendum.md` referenced three times but does not exist for this PRD** (§4.4 RF-7 line 192: "decisão de implementação em `addendum.md`"; §8 OQ-1 line 307: "recomenda-se registrar em `addendum.md`"; §Restrições e Salvaguardas line 334: "vale documentar no addendum..."). These read as forward references to an Architecture-phase artifact rather than broken in-document cross-refs, which is a reasonable pattern — but since no `addendum.md` currently exists in this PRD's folder, whoever owns the Architecture handoff should be aware these three pointers need a landing target, or they'll dangle.
- **Typo in Glossário** (§3, "Antecedência mínima" definition): "esteja a pelo **meno** 48h no futuro" should read "pelo **menos**."
- Glossary terms are capitalized consistently when used as defined terms in RF/Glossary text (e.g., "Convênio," "Slot") but appear lowercase in narrative journey prose (e.g., §2.3 JU-1: "convênios aceitos"). This is a defensible register distinction (formal spec vs. narrative prose) rather than drift, but worth a conscious pass if a downstream tool does literal string-matching against the glossary.
- No Assumptions Index drift: all 5 inline `[ASSUMPTION]` tags match all 5 §9 index entries one-to-one.
- No ID gaps or duplicates found across RF (1–11), JU (1–4), or MS (1–3, C1).
- Required sections for the agreed stakes (launch-level rigor, standalone-but-chain-top PRD) are all present: Purpose, Vision, Target User/JTBD/Non-users/UJs, Glossary, Functional Requirements with testable consequences, Non-Goals, MVP Scope, Success Metrics, Open Questions, Assumptions Index, cross-cutting NFRs, Constraints/Safeguards, Platform, Monetization.
