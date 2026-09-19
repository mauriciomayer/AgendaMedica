-- Story 1.1 — Médico se cadastra e configura seu perfil profissional
--
-- Creates: profiles, doctors, doctor_schedules; the complete_registration() function that
-- the register-doctor Edge Function calls to create a doctor account+profile in one
-- transaction (AD-6); the enforce_doctor_immutable_fields trigger (AD-11); and the RLS
-- policies that back all of it. Naming: English snake_case identifiers, Portuguese content
-- values (AD-7).
--
-- Explicitly NOT in this migration (out of this story's scope): `patients`, `appointments`,
-- `booked_slots`, `is_doctor`/`is_patient` helpers (no cross-role policy needs them yet).

create extension if not exists pgcrypto;

-- =============================================================================================
-- Tables
-- =============================================================================================

create table public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  role text not null check (role in ('patient', 'doctor')),
  created_at timestamptz not null default now()
);

comment on table public.profiles is 'Role is fixed exclusively by which register-* Edge Function created the row (AD-6) — never a client-supplied field.';

create table public.doctors (
  id uuid primary key references public.profiles (id) on delete cascade,
  name text not null,
  specialty text not null,
  insurances text[] not null,
  created_at timestamptz not null default now()
);

comment on column public.doctors.specialty is 'One of the 6 fixed Especialidade values (PRD Glossário §3). Immutable after creation — see enforce_doctor_immutable_fields.';
comment on column public.doctors.insurances is 'One or more of the 4 fixed Convenio values (PRD Glossário §3). Immutable after creation — see enforce_doctor_immutable_fields.';

create table public.doctor_schedules (
  id uuid primary key default gen_random_uuid(),
  doctor_id uuid not null references public.doctors (id) on delete cascade,
  weekday smallint not null check (weekday between 0 and 6), -- 0 = Sunday .. 6 = Saturday (Postgres EXTRACT(DOW) convention — NOT ISO 8601)
  start_time time not null,
  end_time time not null,
  created_at timestamptz not null default now(),
  constraint doctor_schedules_time_order check (start_time < end_time)
);

comment on table public.doctor_schedules is 'Freely editable by the owner (AD-11) — no immutability trigger here, unlike doctors.specialty/insurances.';

create index doctor_schedules_doctor_id_idx on public.doctor_schedules (doctor_id);

-- =============================================================================================
-- complete_registration — called only by register-doctor (service_role), never by a client.
-- =============================================================================================

create or replace function public.complete_registration(
  p_user_id uuid,
  p_role text,
  p_name text,
  p_specialty text default null,
  p_insurances text[] default null,
  p_schedules jsonb default null
)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_schedule jsonb;
  v_insurance text;
begin
  if p_role not in ('patient', 'doctor') then
    raise exception 'INVALID: role deve ser patient ou doctor';
  end if;

  if p_name is null or length(trim(p_name)) = 0 then
    raise exception 'INVALID: nome é obrigatório';
  end if;

  insert into public.profiles (id, role) values (p_user_id, p_role);

  if p_role = 'doctor' then
    if p_specialty is null or p_specialty not in (
      'Cardiologia', 'Dermatologia', 'Pediatria', 'Ortopedia', 'Clínico Geral', 'Ginecologia'
    ) then
      raise exception 'INVALID: especialidade inválida';
    end if;

    if p_insurances is null or array_length(p_insurances, 1) is null then
      raise exception 'INVALID: selecione ao menos um convênio';
    end if;

    foreach v_insurance in array p_insurances loop
      if v_insurance not in ('Unimed', 'Amil', 'Bradesco', 'Particular') then
        raise exception 'INVALID: convênio inválido';
      end if;
    end loop;

    if p_schedules is null or jsonb_array_length(p_schedules) = 0 then
      raise exception 'INVALID: selecione ao menos um dia de atendimento';
    end if;

    insert into public.doctors (id, name, specialty, insurances)
    values (p_user_id, trim(p_name), p_specialty, p_insurances);

    for v_schedule in select * from jsonb_array_elements(p_schedules)
    loop
      if not (v_schedule ? 'weekday' and v_schedule ? 'start_time' and v_schedule ? 'end_time') then
        raise exception 'INVALID: horário de atendimento inválido';
      end if;

      insert into public.doctor_schedules (doctor_id, weekday, start_time, end_time)
      values (
        p_user_id,
        (v_schedule ->> 'weekday')::smallint,
        (v_schedule ->> 'start_time')::time,
        (v_schedule ->> 'end_time')::time
      );
    end loop;
  end if;

  -- role = 'patient' is intentionally unimplemented here (Story 1.2, out of scope for 1.1).
  -- register-patient does not exist yet, so this branch cannot be reached in production.
end;
$$;

comment on function public.complete_registration(uuid, text, text, text, text[], jsonb) is 'Single-transaction identity+profile creation for register-doctor/register-patient (AD-6). Only service_role may call it — see grants below.';

-- Postgres functions are executable by PUBLIC unless revoked. A client must never be able to
-- call this directly (it would let anyone pick their own role/specialty), so only the
-- Edge Function's service_role client may invoke it.
revoke all on function public.complete_registration(uuid, text, text, text, text[], jsonb) from public;
grant execute on function public.complete_registration(uuid, text, text, text, text[], jsonb) to service_role;

-- =============================================================================================
-- AD-11 — specialty/insurances immutable after creation; doctor_schedules stays editable.
-- =============================================================================================

create or replace function public.enforce_doctor_immutable_fields()
returns trigger
language plpgsql
set search_path = public, pg_temp
as $$
begin
  if new.specialty is distinct from old.specialty then
    raise exception 'CONFLICT: specialty não pode ser alterada após a criação do perfil';
  end if;

  if new.insurances is distinct from old.insurances then
    raise exception 'CONFLICT: insurances não pode ser alterada após a criação do perfil';
  end if;

  return new;
end;
$$;

create trigger doctors_enforce_immutable_fields
before update on public.doctors
for each row
execute function public.enforce_doctor_immutable_fields();

-- =============================================================================================
-- Row Level Security
-- =============================================================================================

alter table public.profiles enable row level security;
alter table public.doctors enable row level security;
alter table public.doctor_schedules enable row level security;

-- profiles: a user only ever needs to read their own role (used right after login to route
-- into the correct home screen).
create policy profiles_select_own on public.profiles
  for select
  to authenticated
  using (id = auth.uid());

-- doctors: readable by any authenticated user — a doctor's profile must be immediately
-- searchable (FR1) once Épico 2's search screen exists; not sensitive/PII beyond name.
-- No INSERT policy: creation only happens via complete_registration (SECURITY DEFINER,
-- bypasses RLS) — a client can never INSERT here directly.
create policy doctors_select_authenticated on public.doctors
  for select
  to authenticated
  using (true);

-- UPDATE is allowed for the owner; enforce_doctor_immutable_fields (above) is what actually
-- blocks changes to specialty/insurances, not RLS — matches the I/O matrix ("Rejeitado pela
-- trigger enforce_doctor_immutable_fields").
create policy doctors_update_own on public.doctors
  for update
  to authenticated
  using (id = auth.uid())
  with check (id = auth.uid());

-- doctor_schedules: same "publicly readable, owner-writable" shape; freely editable by the
-- owner (AD-11) since there's no immutability rule for it.
create policy doctor_schedules_select_authenticated on public.doctor_schedules
  for select
  to authenticated
  using (true);

create policy doctor_schedules_insert_own on public.doctor_schedules
  for insert
  to authenticated
  with check (doctor_id = auth.uid());

create policy doctor_schedules_update_own on public.doctor_schedules
  for update
  to authenticated
  using (doctor_id = auth.uid())
  with check (doctor_id = auth.uid());

create policy doctor_schedules_delete_own on public.doctor_schedules
  for delete
  to authenticated
  using (doctor_id = auth.uid());
