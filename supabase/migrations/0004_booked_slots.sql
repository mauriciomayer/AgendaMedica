-- Story 2.2 — Paciente visualiza horários disponíveis de um médico
--
-- booked_slots holds only which start times of a doctor are taken, with NO patient column
-- (AD-10), so any authenticated user can read occupancy without seeing who booked.
-- It starts empty: rows are written by the sync_booked_slots trigger of Story 2.3.
-- No INSERT/UPDATE/DELETE policies exist, so a client can never write here.

create table public.booked_slots (
  doctor_id uuid not null references public.doctors (id) on delete cascade,
  start_time timestamptz not null,
  primary key (doctor_id, start_time)
);

comment on table public.booked_slots is 'Occupied 15-min slots per doctor. No patient data (AD-10). Written only by a server-side trigger (Story 2.3).';

alter table public.booked_slots enable row level security;

create policy booked_slots_select_authenticated on public.booked_slots
  for select
  to authenticated
  using (true);
