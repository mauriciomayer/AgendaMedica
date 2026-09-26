# Epic 8 Context: Telas Seguem o Layout do Protótipo

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

This is a visual/UX alignment epic raised after the architect compared every app screen with the design prototype (2026-09-25). It changes look and layout only, with no new functional requirements and no behavior change. It brings the remaining screens (access screens, Busca, Minhas consultas and the appointment card, Detalhe do médico, Confirmação, Cadastro de médico, Minha agenda) to the prototype's layout, and adds the Inter font app-wide. The Login screen was already aligned in Stories 6.6/6.7 and is the reference for the shared top bar and field style. All Epic 8 stories are executed in sequence.

## Stories

- Story 8.1: Barra do topo compartilhada e telas de acesso no layout do protótipo
- Story 8.2: Busca no layout do protótipo
- Story 8.3: Minhas consultas e cartão de consulta no layout do protótipo
- Story 8.4: Detalhe do médico e Confirmação no layout do protótipo
- Story 8.5: Cadastro de médico no layout do protótipo
- Story 8.6: Minha agenda no layout do protótipo
- Story 8.7: Fonte Inter em todo o app

## Requirements & Constraints

- Existing behavior must survive the restyle. Elements that exist in the app but not in the prototype are kept: Sair button, cancel confirmation dialog, password eye toggle, e-mail mask, Localização field and "Mínimo de 6 caracteres" hint on sign-ups, insurance (convênio) selection when booking, all 7 weekdays in doctor sign-up, Cancelar/Reagendar with the 24h rule on the doctor's agenda, the Busca GPS flow (loading/error/"Tentar novamente" states), and "Nova senha".
- Decisions by the user: appointment card buttons stay in the order Cancelar, Reagendar; doctor avatar is solid blue with white initials.
- Default decisions: the back button is drawn 36 dp inside a 48 dp touch target; Sair moves to the right side of the top bar; the prototype's "intervalos de 15 min" text is not copied (appointments are 30 minutes).
- The 24h cancel/reschedule block and the 48h minimum booking notice keep their current rules; only their presentation changes.
- Inter (regular, medium, semibold, bold) must be bundled in the app, with no download or network dependency, and no text may be clipped or misaligned by the font change.
- Accessibility floor: touch targets at least 48 dp; status never conveyed by color alone (badge text pairs with color); icon buttons need content descriptions; form fields have a visible label.

## Technical Decisions

- The top bar becomes one shared component (white background, bold title, optional subtitle, round back button, hairline bottom border, optional right-side action); Login migrates to it.
- Layering rule stays: Composables read from a ViewModel, ViewModels talk only to a Repository. Shared, feature-agnostic UI lives in `ui/components/`; the appointment card (used by patient and doctor screens) already lives there and is restyled in place, not duplicated.
- Design tokens (from the design spec) are the source of truth for the restyle: single brand blue `#3B6FE0`; pale green screen canvas; white cards with 1 dp hairline border and no heavy shadow (only the booking confirm button may carry a colored shadow); state colors only for state (green ok, amber attention, red danger); blue tint for info boxes.
- Shapes: pill for chips, badges and short text buttons; 14 px cards; 10-12 px inputs and full-width buttons; circle for avatar and round icon buttons. Screen side margin about 18 dp; compact spacing scale 4-20.
- Typography: single family Inter; screen titles 18-19/bold, names 15-16/semibold-bold, body 14, secondary 13, labels and chips 12, badges 11.

## UX & Interaction Patterns

- Field pattern on access screens: label above, example placeholder inside (e.g. "Seu nome", "voce@email.com", "••••••••").
- Criar conta: prompt "Como você quer usar o app?" with two fully clickable cards, "Sou paciente" then "Sou médico".
- Recuperar senha: supporting text, and after sending a green box with "Voltar ao login"; the message stays neutral (never reveals whether the e-mail exists).
- Busca: bar with title "Agende", subtitle "Encontre um médico e agende", pill "Minhas consultas" with blue border; specialty and city filters (with location button) in a single card; "N médico(s) encontrado(s)" line; doctor cards with avatar, name, "Especialidade · Cidade" on one line, insurance in small chips.
- Minhas consultas: "+ Nova consulta" always visible at the end of the list. Appointment card shows name, specialty (when present), Confirmada/Bloqueada badge, "dd/MM às HH:mm · Convênio" on one line, and the block note in a warning box under 24h.
- Detalhe do médico: bar "Escolher horário" (or "Reagendar consulta"), doctor card, day cards with weekday and number, section titles "Escolha o dia" and "Horários disponíveis", fixed bottom bar with the confirm button, note about the 48h minimum notice, insurance selector kept.
- Confirmação: bar "Confirmado", green check circle, centered "Consulta agendada!", single summary card, actions "Buscar outro médico" and "Ver minhas consultas".
- Cadastro de médico: subtitle "Autocadastro, sem validação de CRM", CRM notice in a blue box, labeled selects, toggle chips (active: 2 px blue border with blue tint; inactive: 1 px neutral border).
- Minha agenda: bar with Sair on the right, profile card keeping working hours, blue info box about no two patients at the same time, "Próximas consultas" section title.

## Cross-Story Dependencies

- Story 8.1 comes first: it creates the shared top bar and field style that Stories 8.2-8.6 reuse.
- Story 8.3 restyles the appointment card that Story 8.6 (doctor's agenda) also uses; do 8.3 before 8.6.
- Story 8.7 (Inter) is global and best done last, so layouts are checked against the final font for clipping.
- Builds on Stories 6.6/6.7 (Login in prototype layout) and on the Epic 7 component move, which put the appointment card and cancel dialog in `ui/components/`.
- Existing UI tests (e.g. cancel dialog, Splash) must keep passing after the restyle.
