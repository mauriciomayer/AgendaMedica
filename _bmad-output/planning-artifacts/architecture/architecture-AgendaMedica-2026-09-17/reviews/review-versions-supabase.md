---
review: version/capability fact-check
target: ../ARCHITECTURE-SPINE.md
date: '2026-09-17'
---

# Fact-Check Review — Supabase Version/Capability Claims

Scope: verify the six version/capability claims in ARCHITECTURE-SPINE.md that underpin the Firebase→Supabase migration decision and the AD-5 (external scheduler) design. Web search performed 2026-09-17.

## Summary Table

| # | Claim | Verdict |
| - | --- | --- |
| 1 | Free tier does NOT include `pg_cron` (Pro+ only) | **WRONG / STALE — must fix** |
| 2 | Free tier requires no credit card anywhere, incl. Edge Functions | OK — confirmed |
| 3 | Partial unique index `UNIQUE (...) WHERE status = 'confirmed'` is valid Postgres | OK — confirmed |
| 4 | `supabase-kt` (auth-kt/postgrest-kt/realtime-kt/functions-kt, minSdk 26) is current & maintained | OK — confirmed |
| 5 | Realtime "Postgres Changes" can filter by column, respecting per-user auth | OK — confirmed |
| 6 | GitHub Actions cron scheduler is free and can be delayed under load | OK — confirmed (arguably understated) |

## 1. pg_cron on Supabase free tier — **INCORRECT, needs correction**

The spine (AD-5, line 57) states: *"o Supabase free tier **não inclui `pg_cron`** (agendador dentro do banco — Pro em diante)"* — and uses this as the stated justification for routing the 24h-reminder trigger through an external GitHub Actions scheduler instead of an in-database cron job.

This claim is **no longer accurate as of 2026**. Multiple independent, current sources confirm `pg_cron` / "Supabase Cron" ships enabled on **every** plan tier, including Free, with no per-execution billing — availability is gated only by the project's own CPU/memory/disk resources, not by plan tier:

- Supabase's own module page (supabase.com/modules/cron) and extension docs describe Cron as a standard, always-available Postgres extension with no plan-tier gating mentioned.
- Supabase's official pricing page (supabase.com/pricing) lists no Cron/pg_cron restriction anywhere in the Free vs. Pro feature comparison — consistent with it being available to all tiers (unlike Edge Functions or DB size, which *are* explicitly tiered).
- A Supabase team member, in an official GitHub discussion ("pg_cron and free tier", github.com/orgs/supabase/discussions/37405), explicitly confirmed: *"Cron is only limited by the resources it uses CPU/Memory/Disk wise on any tier"* — i.e., no Pro-tier gate exists.
- Third-party 2026 guides (crontap.com/guides/supabase-cron-jobs) independently state: *"every Supabase project ships with pg_cron enabled on free, pro, and team plans... you can run `SELECT cron.schedule(...)` immediately with no setup or support ticket needed."*
- Supabase Cron was announced 2024-12-04 as a general product feature, not a paid add-on.

**Correction needed:** AD-5's premise ("free tier doesn't have pg_cron, so we need GitHub Actions") is factually false for 2026. This doesn't necessarily invalidate the *decision* to use GitHub Actions — there may be other valid reasons (e.g., avoiding logic split between DB-internal cron and Edge Function, keeping the reminder trigger visible/version-controlled in the app repo, not wanting a `cron.schedule()` call embedded in a migration, or simply the 5-minute-granularity `HTTP` call needing `pg_net`/`http` extension wiring inside pg_cron which is more moving parts) — but the document should **not** state the false "free tier lacks pg_cron" reason as fact. Recommend either:
- (a) switching AD-5's justification to pg_cron being an available-but-heavier alternative that still requires its own HTTP-calling extension (`pg_net`) and doesn't materially simplify things vs. GitHub Actions, or
- (b) reconsidering whether an in-database `pg_cron` job calling `send-reminders` directly would in fact be simpler than an external GitHub Actions workflow, since the free-tier blocker cited no longer exists.

This also means the Stack table entry "GitHub Actions (agendador externo do lembrete)" and the Deferred section's reliability caveat remain fine as *decisions*, but their stated *rationale* needs updating.

## 2. Free tier, no credit card anywhere (incl. Edge Functions) — Confirmed OK

This is the load-bearing claim for the whole Firebase→Supabase migration and it holds up:

- Supabase's Free plan requires no credit card to sign up or to use any free-tier feature, including Edge Functions (500,000 invocations/month included on Free, per the official pricing page). Payment info is only requested when upgrading to Pro/Team or exceeding free quotas.
- This is a genuine asymmetry vs. Firebase: Firebase's Cloud Functions (2nd gen, built on Cloud Run/Cloud Build) require the pay-as-you-go **Blaze** plan even to deploy a single function, and Blaze requires a billing account with a valid card on file, even if usage stays within the "always free" quota.
- Caveat worth noting in the spine (minor, not a correctness error): Supabase free *projects* auto-pause after ~1 week of inactivity and are capped at 2 active free projects per org — not a credit-card issue, but worth a Deferred-section footnote if the project might sit idle during grading/review gaps.

## 3. Partial unique index — Confirmed OK

`CREATE UNIQUE INDEX ... ON appointments (doctor_id, start_time) WHERE status = 'confirmed'` is standard, fully supported PostgreSQL syntax (documented since very old Postgres versions, unchanged through current Postgres 18 docs at postgresql.org/docs/current/indexes-partial.html). Partial unique indexes are the canonical way to enforce "uniqueness only among non-deleted/active rows" (the docs' own example is functionally identical: `WHERE deleted_at IS NULL`). AD-1's design — unique partial index as the final concurrency guarantee, independent of application/function logic, surfaced to the caller via SQLSTATE `23505` — is sound and idiomatic.

## 4. supabase-kt current & maintained — Confirmed OK

- `supabase-kt` (github.com/supabase-community/supabase-kt) is the actively maintained, official-docs-referenced community Kotlin Multiplatform client, currently on major version 3.x (a 2.x→3.x migration guide exists, confirming active version churn/maintenance).
- It ships the modules named in the spine — `auth-kt` (renamed from `gotrue-kt` pre-3.0), `postgrest-kt`, `realtime-kt`, `functions-kt` — plus others (`storage-kt`, Compose helpers, etc.) not needed here.
- Standalone predecessor repos (`postgrest-kt`, `gotrue-kt` as separate projects) are archived in favor of the unified `supabase-kt` monorepo — consistent with the spine's phrasing that treats these as modules of one library rather than separate libraries.
- `minSdk 26` for the Android target is confirmed by the library's own documentation (lower API levels need core library desugaring).
- The spine's own hedge — "conferir a versão 3.x mais recente no momento do build" — is the right posture given this is a fast-moving community project; no change needed there.

## 5. Realtime "Postgres Changes" filtered broadcast — Confirmed OK

- Supabase Realtime's Postgres Changes feature broadcasts row-level `INSERT`/`UPDATE`/`DELETE` events via Postgres logical replication.
- It supports server-side filtering by a column's value (e.g. `filter: 'doctor_id=eq.<uuid>'`), which matches AD-10's design (channel filtered by `doctor_id=eq.<id>`).
- Since Supabase's "Realtime Postgres RLS" rollout, Postgres Changes payloads for a table with RLS enabled are only delivered to clients whose auth context satisfies that table's RLS policies — i.e., authenticated-and-authorized filtering, not just a raw broadcast. This aligns with (and actually reinforces) AD-10's statement that `booked_slots` has an RLS policy permitting `SELECT` to any authenticated user, since Realtime enforces that same policy for the subscription.
- One nuance not mentioned in the spine: RLS is not evaluated for `DELETE` events (Postgres/Supabase limitation, since there's no way to check access to a row that's gone). This doesn't affect AD-10 as written, since `booked_slots` rows are only inserted/updated by the trigger, not deleted — but worth being aware of if that changes later.

## 6. GitHub Actions scheduled workflows — Confirmed OK, spine's caveat is if anything an understatement

- `on: schedule` with cron syntax remains a supported, free feature of GitHub Actions for both public and private repos on personal (free) GitHub accounts as of 2026.
- The spine's caveat ("agendamentos do GitHub Actions podem atrasar minutos em picos de carga") is an accurate, well-documented, ongoing limitation — GitHub's own docs warn that schedule-triggered runs "may be delayed during periods of high loads," especially around the top of the hour/day (UTC).
- If anything, current community reports (GitHub Community Discussions, 2026) describe delays worse than "minutes" in some cases — multiple reports of 8+ hour delays or entirely dropped runs, particularly on low-activity/free-tier repos (GitHub Actions can deprioritize schedules on repos with little recent activity). Given RF-10 reminders are explicitly best-effort/no-SLA, this doesn't break the design, but the spine's Deferred-section fallback ("trocar por cron-job.org se a demora se mostrar um problema na prática") is well-justified and, if anything, more likely to be needed than the current wording implies. No factual correction required, but consider tightening the caveat's severity language.

## Recommended Action

The one must-fix item is **#1 (pg_cron)**: AD-5's stated rationale is factually wrong for 2026 and should be corrected before this spine is approved, since it's presented as a hard technical constraint ("não inclui") rather than a design preference. All other five claims check out and require no changes.
