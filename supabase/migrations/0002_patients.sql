-- Story 1.2 — Paciente se cadastra
--
-- Creates `patients` and extends complete_registration() (same signature as 0001) with the
-- `patient` branch. 0001 is already applied to the hosted project, so this is a new migration.

create table public.patients (
  id uuid primary key references public.profiles (id) on delete cascade,
  name text not null,
  email text not null,
  created_at timestamptz not null default now()
);

comment on column public.patients.email is 'Copied once from auth.users at creation (AD-1), used by reminders (Story 3.1). No e-mail change in the MVP.';

alter table public.patients enable row level security;

create policy patients_select_own on public.patients
  for select
  to authenticated
  using (id = auth.uid());

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
  v_email text;
begin
  if p_role not in ('patient', 'doctor') then
    raise exception 'INVALID: role deve ser patient ou doctor';
  end if;

  if p_name is null or length(trim(p_name)) = 0 then
    raise exception 'INVALID: nome é obrigatório';
  end if;

  insert into public.profiles (id, role) values (p_user_id, p_role);

  if p_role = 'patient' then
    select email into v_email from auth.users where id = p_user_id;
    if v_email is null then
      raise exception 'INVALID: usuário não encontrado';
    end if;

    insert into public.patients (id, name, email)
    values (p_user_id, trim(p_name), v_email);
  end if;

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
end;
$$;

revoke all on function public.complete_registration(uuid, text, text, text, text[], jsonb) from public;
grant execute on function public.complete_registration(uuid, text, text, text, text[], jsonb) to service_role;
