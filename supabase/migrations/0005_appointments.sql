-- Story 2.3 — Paciente agenda uma consulta sem conflito de horário
--
-- The partial unique index on (doctor_id, start_time) WHERE status = 'confirmed' is the final
-- guarantee that a slot is taken by exactly one patient (RF-6/RF-7). book_appointment() is the
-- only write path (AD-1, AD-10); it only translates the index violation into CONFLICT: slot_taken.

create type public.appointment_status as enum ('confirmed', 'cancelled');

create table public.appointments (
  id uuid primary key default gen_random_uuid(),
  patient_id uuid not null references public.patients (id) on delete cascade,
  doctor_id uuid not null references public.doctors (id) on delete cascade,
  start_time timestamptz not null,
  status public.appointment_status not null default 'confirmed',
  insurance text not null,
  created_at timestamptz not null default now()
);

comment on table public.appointments is 'Cancelling never deletes (status = cancelled). Written only by book_appointment (Story 2.3) and later cancel/reschedule functions.';

create unique index appointments_doctor_slot_confirmed_uidx
  on public.appointments (doctor_id, start_time)
  where status = 'confirmed';

create index appointments_patient_idx on public.appointments (patient_id);

alter table public.appointments enable row level security;

create policy appointments_select_own_patient on public.appointments
  for select to authenticated
  using (patient_id = auth.uid());

create policy appointments_select_own_doctor on public.appointments
  for select to authenticated
  using (doctor_id = auth.uid());

-- No INSERT/UPDATE/DELETE policies: clients can never write here.
revoke insert, update, delete on public.appointments from anon, authenticated;

-- =============================================================================================
-- notification_events (AD-12): recorded only; no delivery in this story. No client access.
-- =============================================================================================

create table public.notification_events (
  id uuid primary key default gen_random_uuid(),
  recipient_id uuid not null references public.profiles (id) on delete cascade,
  event_type text not null check (event_type in ('new_appointment', 'cancellation', 'reschedule')),
  appointment_id uuid not null references public.appointments (id) on delete cascade,
  created_at timestamptz not null default now(),
  delivered_at timestamptz
);

alter table public.notification_events enable row level security;
revoke all on public.notification_events from anon, authenticated;

-- =============================================================================================
-- booked_slots maintenance: the trigger is the only writer (0004 has no write policies).
-- =============================================================================================

create or replace function public.sync_booked_slots()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  if tg_op = 'UPDATE' then
    if old.status = 'confirmed'
       and (new.status <> 'confirmed' or new.start_time <> old.start_time or new.doctor_id <> old.doctor_id) then
      delete from public.booked_slots
      where doctor_id = old.doctor_id and start_time = old.start_time;
    end if;
    if new.status = 'confirmed'
       and (old.status <> 'confirmed' or new.start_time <> old.start_time or new.doctor_id <> old.doctor_id) then
      insert into public.booked_slots (doctor_id, start_time)
      values (new.doctor_id, new.start_time)
      on conflict do nothing;
    end if;
  elsif new.status = 'confirmed' then
    insert into public.booked_slots (doctor_id, start_time)
    values (new.doctor_id, new.start_time)
    on conflict do nothing;
  end if;
  return null;
end;
$$;

revoke all on function public.sync_booked_slots() from public;

create trigger appointments_sync_booked_slots
  after insert or update of status, start_time on public.appointments
  for each row execute function public.sync_booked_slots();

-- Story 2.2 left a test row; booked_slots is now derived from appointments only.
delete from public.booked_slots;

-- =============================================================================================
-- book_appointment
-- =============================================================================================

create or replace function public.book_appointment(
  p_doctor_id uuid,
  p_start_time timestamptz,
  p_insurance text
)
returns uuid
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_uid uuid := auth.uid();
  v_role text;
  v_insurances text[];
  v_local timestamp;
  v_appointment_id uuid;
begin
  if v_uid is null then
    raise exception 'FORBIDDEN: sessão obrigatória';
  end if;

  select role into v_role from public.profiles where id = v_uid;
  if v_role is distinct from 'patient' then
    raise exception 'FORBIDDEN: apenas pacientes podem agendar';
  end if;

  if p_doctor_id is null or p_start_time is null or p_insurance is null then
    raise exception 'INVALID: dados obrigatórios ausentes';
  end if;

  select insurances into v_insurances from public.doctors where id = p_doctor_id;
  if not found then
    raise exception 'INVALID: médico inexistente';
  end if;

  v_local := p_start_time at time zone 'America/Sao_Paulo';

  if extract(minute from v_local)::int % 15 <> 0 or extract(second from v_local) <> 0 then
    raise exception 'INVALID: horário fora do intervalo de 15 minutos';
  end if;

  if not exists (
    select 1 from public.doctor_schedules s
    where s.doctor_id = p_doctor_id
      and s.weekday = extract(dow from v_local)::int
      and v_local::time >= s.start_time
      and v_local::time < s.end_time
  ) then
    raise exception 'INVALID: horário fora da agenda do médico';
  end if;

  if not (p_insurance = any (v_insurances)) then
    raise exception 'INVALID: convênio não aceito pelo médico';
  end if;

  if p_start_time < now() + interval '48 hours' then
    raise exception 'CONFLICT: lead_time';
  end if;

  begin
    insert into public.appointments (patient_id, doctor_id, start_time, status, insurance)
    values (v_uid, p_doctor_id, p_start_time, 'confirmed', p_insurance)
    returning id into v_appointment_id;
  exception when unique_violation then
    raise exception 'CONFLICT: slot_taken';
  end;

  insert into public.notification_events (recipient_id, event_type, appointment_id)
  values (p_doctor_id, 'new_appointment', v_appointment_id);

  return v_appointment_id;
end;
$$;

revoke all on function public.book_appointment(uuid, timestamptz, text) from public, anon;
grant execute on function public.book_appointment(uuid, timestamptz, text) to authenticated;

-- =============================================================================================
-- Realtime: clients subscribe to booked_slots (filtered by doctor_id) while Detalhe is open.
-- =============================================================================================

do $$
begin
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'booked_slots'
  ) then
    alter publication supabase_realtime add table public.booked_slots;
  end if;
end;
$$;
