# Epic 6 Context: Ajustes de Login, Sessão e Cancelamento

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

This epic triages bugs/improvements the user found using the real app after Epics 1-5 shipped (bmad-party session, 2026-09-23). It makes Login more robust (validated e-mail format, a show/hide toggle for the typed password, and strict enforcement of the selected role tab), adds a missing logout control, fixes a cosmetic time display bug on Minha Agenda, replaces the inline-card cancellation confirmation with a blocking modal dialog, adds a typing mask to every e-mail field, shows the app logo at the top of Login as in the design prototype, and finally aligns the whole Login screen layout to that shared prototype (Login only; other screens will be revisited later, one by one). Two stories deliberately reverse decisions made and tested in earlier epics (6.1 and 6.4), and Story 6.5 corrects how Story 6.1 interpreted the user's e-mail request — the user confirmed each explicitly after hands-on use, and each story records the reason.

## Stories

- Story 6.1: Login valida o e-mail, mostra a senha digitada e bloqueia por papel selecionado
- Story 6.2: Usuário sai da própria conta (logout)
- Story 6.3: Minha Agenda mostra o horário de atendimento sem os segundos
- Story 6.4: Cancelamento de consulta usa uma janela de confirmação, não mais inline no card
- Story 6.5: Campos de e-mail aceitam só caracteres válidos, como no login do Google
- Story 6.6: Login exibe o logo do app
- Story 6.7: Tela de Login segue o layout do protótipo de design

## Requirements & Constraints

- Login's e-mail field must gate the submit button on a basic format check (`algo@algo.algo`), the same disabled-until-valid pattern already used elsewhere in the app — no inline error before a submit attempt.
- Every e-mail field in the app (Login, Cadastro de Médico, Cadastro de Paciente, Recuperar Senha) must behave as a typing mask, like Google's Android sign-in e-mail field: e-mail keyboard, no auto-capitalization, no autocorrect; characters outside letters without accents, digits, `@ . _ - + %` are silently dropped (no error message); a second `@` is not accepted. Pasted text is cleaned by the same filter (invalid chars and surrounding spaces removed, e.g. `" joao silva@gmail.com "` becomes `joaosilva@gmail.com`) rather than rejected; case is preserved as typed. The 6.1 format validation stays in force; the mask sits on top of it.
- Any password field (Login and elsewhere) needs a visibility toggle (eye icon) that shows/hides the typed characters on tap; toggling back re-hides it.
- The role tab selected on Login (Paciente/Médico) becomes authoritative: if the credentials are valid but belong to an account of the *other* role, access must be denied with a clear message (naming the actual account role and telling the user to pick the right tab) and no session is opened — the user stays on Login.
- Every authenticated screen (Minha Agenda for Médico, Minhas Consultas for Paciente) needs a visible "Sair" action; using it ends the session, returns to Login, and the system back button must not be able to return to the now-closed authenticated screen.
- Doctor schedule times on Minha Agenda must render as `HH:mm` (e.g. "08:00 - 18:00"), never with seconds.
- Cancelling an appointment (Paciente in Minhas Consultas, Médico in Minha Agenda) now opens a modal with two equal-size "Sim"/"Não" actions; neither an outside tap nor the system back gesture/button may dismiss it — only choosing Sim or Não closes it. The existing 24h cancellation-window rule, the resulting status change, and the notification event for the other party are unchanged; only the confirmation UI changes.
- Login must show the app logo (document with red medical-cross seal) centered at the top of the content, 88dp wide, above the rest of the form. It is purely decorative: no tap action, not exposed to screen readers, and all existing Login behavior (role tabs, e-mail, password, Entrar, Esqueci minha senha, Criar conta) is unchanged.

- Login layout must follow the shared design prototype, for the Login screen only (no other screen changes appearance in this epic): a white header with the title "Entrar" and a role-dependent subtitle ("Acesse sua conta de paciente" / "Acesse sua conta de médico"), separated from the content by a thin line; below it, top to bottom, the centered logo, a centered pill-shaped Paciente/Médico toggle (active tab blue with white text), E-mail and Senha fields with labels above and example placeholders ("voce@email.com", "••••••••"), the Entrar button, then centered "Esqueci minha senha" and "Não tem conta? Criar conta" links. Error, role-mismatch and "senha redefinida" messages appear in rounded colored boxes above the Entrar button (warm tone for errors, green for success). All existing Login behavior (mask, keyboard, password eye, disabled-until-valid Entrar, role enforcement, navigation) must remain unchanged.

## Technical Decisions

- No backend schema/RPC changes are implied by this epic. Story 6.4's cancel action still calls the same `cancel_appointment` function with the same 24h-window enforcement; Story 6.1's role check is a client-side denial against data the Login flow already reads, not a new Postgres error class. Story 6.5's mask is purely client-side input filtering.
- Role is always read live from `profiles` (via the existing `is_doctor`/`is_patient` pattern), never trusted from a cached JWT claim — this is the same live read Login already performs; it now gates access on tab mismatch instead of silently ignoring the tab and routing by role.
- Error convention already in place stays unchanged: Postgres exceptions use `CONFLICT:`/`INVALID:`/`FORBIDDEN:` prefixes, with an implicit `UNEXPECTED` bucket in the app for anything else. The new role-mismatch message on Login is app-side copy, not a new backend error type.
- MVVM boundaries hold: only the Repository layer talks to Supabase Auth; logout goes through the same auth session teardown call the Repository already exposes.
- The e-mail mask applies to four different screens, so it should be one shared filter/component rather than per-screen copies.
- The logo is already an existing reusable composable (`AgendaMedicaLogo` in `ui/theme/Logo.kt`), currently used only by the Splash; Story 6.6 reuses it on Login rather than redrawing it.

## UX & Interaction Patterns

- Existing button-danger-outline component (white background, danger-colored border and text) is the established style for cancel/destructive actions and is available for the modal's Sim/Não pair.
- Login's existing inline-error placement (below the fields, non-blocking, e.g. "Nenhum cadastro encontrado...") is the pattern to extend for the new role-mismatch message.
- The shared design prototype places the logo at the top of the Login content (88dp, centered); Login should match it, and Story 6.7 extends that fidelity to the whole Login layout (header, pill role toggle, labels above fields, centered links, colored message boxes). Other screens are deliberately left for later review.
- Voice and tone stays direct and specific, no apologetic phrasing: the role-mismatch message should name the actual account type and the needed action, not just say "login failed."
- Accessibility floor still applies: the eye-icon password toggle needs a real `contentDescription`, and the modal's Sim/Não targets must each meet the ≥48dp touch target minimum.

## Cross-Story Dependencies

- Story 6.1 explicitly reverses a decision from Story 1.2's review triage (achado #8): the Login role tab was deliberately built as visual-only, with the real `profiles.role` always winning and silently routing to the correct screen regardless of the selected tab. The user tested that behavior live, found it confusing, and asked for the tab to become enforced instead — denying login outright on mismatch. The reversal is explicitly confirmed by the user.
- Story 6.4 explicitly reverses UX-DR5 and its implementation in Stories 2.4 (Paciente side) and 2.5 (Médico side), which deliberately built and tested inline-in-card Sim/Não cancellation confirmation instead of a modal. The user decided a modal that can't be dismissed by outside tap or back gesture is safer against accidental cancellation than the inline card, and confirmed the reversal for both the Paciente and Médico surfaces.
- Story 6.5 corrects Story 6.1's interpretation of the user's e-mail request: 6.1 treated "the field accepts any character" as a format-validation problem (disabled button until `algo@algo.algo`), but the user actually wanted a typing mask like Google's Android login. 6.1's validation is kept; 6.5 layers the mask on top and extends it beyond Login to all e-mail fields (Cadastro Médico/Paciente, Recuperar Senha). 6.5 builds on 6.1's Login e-mail field.
- Story 6.2 (logout) has no prior decision to reverse — the earlier Epic 1 review triage (achado #11) explicitly deferred logout as out of scope for that epic, noting it belonged to no existing story. Epic 6 is where it's finally addressed.
- Story 6.6 completes an item Story 4.1 left out of scope (logo reuse on Login; the logo was placed only on the Splash). It touches the same Login screen as 6.1 and 6.5 but only adds a decorative element above the form, so it does not conflict with them.
- Story 6.7 is a visual-only restyle of the Login screen and builds on 6.1, 6.5 and 6.6: it must keep their behavior (e-mail validation and mask, password toggle, role enforcement, logo) intact while rearranging the layout. It also relocates the role-mismatch and inline error messages into the colored boxes above Entrar. Scope is Login only.
- Stories 6.2-6.4 are otherwise independent: they touch different screens/components, and none blocks another within this epic.
