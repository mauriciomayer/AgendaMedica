-- Story 2.1 — Paciente busca médicos por especialidade e localização
--
-- Adds the doctor's location (city/neighborhood/coordinates) and recreates complete_registration()
-- with location parameters. 0001/0002 are already applied, so this is a new migration.
-- The location list is fixed (same list as register-doctor and domain/model/Localizacao.kt).

alter table public.doctors
  add column city text,
  add column neighborhood text,
  add column latitude double precision,
  add column longitude double precision;

-- Existing doctors get "Centro, São Paulo - SP".
update public.doctors
set city = 'São Paulo', neighborhood = 'Centro', latitude = -23.5505, longitude = -46.6333
where city is null;

alter table public.doctors
  alter column city set not null,
  alter column neighborhood set not null,
  alter column latitude set not null,
  alter column longitude set not null;

comment on column public.doctors.city is 'City from the fixed location list, resolved server-side at registration (AD-1). Not editable through the UI after creation.';

-- The old signature is dropped so only one complete_registration() exists.
drop function public.complete_registration(uuid, text, text, text, text[], jsonb);

create or replace function public.complete_registration(
  p_user_id uuid,
  p_role text,
  p_name text,
  p_specialty text default null,
  p_insurances text[] default null,
  p_schedules jsonb default null,
  p_city text default null,
  p_neighborhood text default null,
  p_latitude double precision default null,
  p_longitude double precision default null
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

    if not exists (
      select 1
      from (values
        ('São Paulo', 'Centro', -23.5505::double precision, -46.6333::double precision),
        ('São Paulo', 'Pinheiros', -23.5613, -46.7008),
        ('São Paulo', 'Moema', -23.6001, -46.6658),
        ('São Paulo', 'Vila Mariana', -23.5893, -46.6345),
        ('São Paulo', 'Itaim Bibi', -23.5845, -46.6784),
        ('Campinas', 'Centro', -22.9056, -47.0608),
        ('Campinas', 'Cambuí', -22.8990, -47.0500),
        ('Santos', 'Gonzaga', -23.9680, -46.3350),
        ('Santos', 'Boqueirão', -23.9700, -46.3200)
      ) as l (city, neighborhood, latitude, longitude)
      where l.city = p_city
        and l.neighborhood = p_neighborhood
        and l.latitude = p_latitude
        and l.longitude = p_longitude
    ) then
      raise exception 'INVALID: localização inválida';
    end if;

    insert into public.doctors (id, name, specialty, insurances, city, neighborhood, latitude, longitude)
    values (p_user_id, trim(p_name), p_specialty, p_insurances, p_city, p_neighborhood, p_latitude, p_longitude);

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

revoke all on function public.complete_registration(uuid, text, text, text, text[], jsonb, text, text, double precision, double precision) from public;
grant execute on function public.complete_registration(uuid, text, text, text, text[], jsonb, text, text, double precision, double precision) to service_role;
