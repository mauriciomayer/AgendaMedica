# Epic 7 Context: Dívida Técnica e Arquitetura

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

This is an internal-quality epic raised by the architect after closing Epic 6 and approved by the user (2026-09-23). It has no user-visible behavior change and covers no functional requirements. It reduces the risk of silent regressions and improper coupling by (1) typing the doctor's schedule times as real time values instead of text, (2) moving shared appointment UI components out of the patient feature package so the doctor feature no longer depends on it, and (3) running a spike to find out whether Compose UI tests can run in this project's local unit-test task, so pending UI-test gaps recorded in the deferred-work ledger can be closed with evidence, and (4) using that infrastructure to add the Splash screen UI test.

## Stories

- Story 7.1: Horário de atendimento do médico é tipado como hora, não como texto
- Story 7.2: Componentes de consulta vivem em `ui/components/`
- Story 7.3: Spike de teste de UI Compose com Robolectric
- Story 7.4: Splash Screen tem teste de UI

## Requirements & Constraints

- Zero visible change: Minha Agenda still shows schedule times as `HH:mm` (never with seconds), slot generation for the day produces exactly the same slots as before, and both appointment screens (Médico and Paciente) look and behave identically after the move.
- After the schedule-time change, no code outside the Repository boundary may parse a schedule time string; the parse happens exactly once when the Repository builds the schedule block from the database value.
- After the component move, no file in the `doctor` package may import anything from the `patient` package.
- The spike must prove, for the cancel confirmation dialog, that an outside tap and the system back action do not dismiss it, and that the Sim and Não actions invoke their respective callbacks.
- The spike's outcome (viable or not, and why) must be recorded in the story spec. If viable, remaining pending UI tests (e-mail validation wiring on the four e-mail screens, Splash) are added to this story or a follow-up; if not, document the reason and finish without the infrastructure.
- Splash test (reuses the Story 7.3 infrastructure) must prove: before the fixed 1600 ms splash duration the app name is shown, `onFinished` has not fired, and tapping the screen does not end it early; at 1600 ms `onFinished` fires exactly once; after an Activity recreation (e.g. rotation) with part of the time already elapsed, only the remaining time is counted, not a fresh 1600 ms.

## Technical Decisions

- Database schema is unchanged: the schedule's start/end columns are already Postgres `time`; only the Kotlin model type changes (text to `LocalTime`), converted at the Repository layer, which remains the only layer that talks to Supabase.
- Layering rule stays in force: Composables read from a ViewModel, ViewModels talk only to a Repository. Shared, feature-agnostic UI pieces belong in `ui/components/`, not inside a feature package.
- Components to relocate: the appointment card, the cancel confirmation dialog, and the date/time formatting helper.
- Test infrastructure under evaluation: Robolectric plus Compose UI test running under the local unit-test Gradle task, with `compileSdk` 37 (the compatibility with this SDK level is the main open question of the spike). Compose BOM and Kotlin/Compose compiler versions are pinned by the architecture doc; new test dependencies must not force changes to them.

## Cross-Story Dependencies

- Story 7.1 follows up on Story 6.3 (seconds shown on Minha Agenda): it removes the root cause instead of relying on display formatting.
- Story 7.2 cleans up a coupling created when the doctor's cancel flow reused the patient screen's components (Epic 6, Story 6.4 modal).
- Story 7.3 tests the dialog introduced by Story 6.4, and is best done after Story 7.2 so the test targets the component at its final location.
- Story 7.4 depends on Story 7.3 (viable Robolectric + Compose UI test infrastructure); it closes the Splash coverage gap recorded in Story 4.1.
- Stories 7.1 and 7.2 are independent of each other.
