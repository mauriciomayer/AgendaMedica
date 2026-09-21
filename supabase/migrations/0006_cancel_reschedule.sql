-- Story 2.4 — Paciente cancela ou reagenda uma consulta
--
-- cancel_appointment() and reschedule_appointment() are the only write paths that change an existing
-- appointment (AD-1). Both serve the patient and the doctor of the appointment (the doctor UI is
-- Story 2.5) and enforce the 24h change window in the database (RF-9); no role bypasses it.
-- booked_slots is kept in sync by the existing sync_booked_slots trigger (0005).

-- =============================================================================================
-- Shared slot validation (alignment, doctor schedule, lead time), used by booking and rescheduling.
-- Internal: not callable by clients.
-- =============================================================================================

create or replace function public.assert_slot_bookable(p_doctor_id uuid, p_start_time timestamptz)
returns void
language plpgsql
stable
security definer
set search_path = public, pg_temp
as $$
declare
  v_local timestamp := p_start_time at time zone 'America/Sao_Paulo';
begin
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

  if p_start_time < now() + interval '48 hours' then
    raise exception 'CONFLICT: lead_time';
  end if;
end;
$$;

revoke all on function public.assert_slot_bookable(uuid, timestamptz) from public, anon, authenticated;

-- =============================================================================================
-- book_appointment, recreated on top of the shared validation (same behavior as 0005).
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

  if not (p_insurance = any (v_insurances)) then
    raise exception 'INVALID: convênio não aceito pelo médico';
  end if;

  perform public.assert_slot_bookable(p_doctor_id, p_start_time);

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
-- cancel_appointment
-- =============================================================================================

create or replace function public.cancel_appointment(p_appointment_id uuid)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_uid uuid := auth.uid();
  v_appt public.appointments%rowtype;
begin
  if v_uid is null then
    raise exception 'FORBIDDEN: sessão obrigatória';
  end if;

  select * into v_appt from public.appointments where id = p_appointment_id for update;
  if not found or v_uid not in (v_appt.patient_id, v_appt.doctor_id) then
    raise exception 'FORBIDDEN: consulta não pertence ao usuário';
  end if;

  if v_appt.status <> 'confirmed' then
    raise exception 'INVALID: consulta não está confirmada';
  end if;

  if v_appt.start_time < now() + interval '24 hours' then
    raise exception 'CONFLICT: cancel_window';
  end if;

  update public.appointments set status = 'cancelled' where id = v_appt.id;

  insert into public.notification_events (recipient_id, event_type, appointment_id)
  values (
    case when v_uid = v_appt.patient_id then v_appt.doctor_id else v_appt.patient_id end,
    'cancellation',
    v_appt.id
  );
end;
$$;

revoke all on function public.cancel_appointment(uuid) from public, anon;
grant execute on function public.cancel_appointment(uuid) to authenticated;

-- =============================================================================================
-- reschedule_appointment: same row (id, doctor, insurance), new start time.
-- =============================================================================================

create or replace function public.reschedule_appointment(
  p_appointment_id uuid,
  p_new_start_time timestamptz
)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_uid uuid := auth.uid();
  v_appt public.appointments%rowtype;
begin
  if v_uid is null then
    raise exception 'FORBIDDEN: sessão obrigatória';
  end if;

  select * into v_appt from public.appointments where id = p_appointment_id for update;
  if not found or v_uid not in (v_appt.patient_id, v_appt.doctor_id) then
    raise exception 'FORBIDDEN: consulta não pertence ao usuário';
  end if;

  if v_appt.status <> 'confirmed' then
    raise exception 'INVALID: consulta não está confirmada';
  end if;

  if v_appt.start_time < now() + interval '24 hours' then
    raise exception 'CONFLICT: cancel_window';
  end if;

  if p_new_start_time is null then
    raise exception 'INVALID: novo horário ausente';
  end if;

  if p_new_start_time = v_appt.start_time then
    raise exception 'INVALID: o novo horário é igual ao atual';
  end if;

  perform public.assert_slot_bookable(v_appt.doctor_id, p_new_start_time);

  begin
    update public.appointments set start_time = p_new_start_time where id = v_appt.id;
  exception when unique_violation then
    raise exception 'CONFLICT: slot_taken';
  end;

  insert into public.notification_events (recipient_id, event_type, appointment_id)
  values (
    case when v_uid = v_appt.patient_id then v_appt.doctor_id else v_appt.patient_id end,
    'reschedule',
    v_appt.id
  );
end;
$$;

revoke all on function public.reschedule_appointment(uuid, timestamptz) from public, anon;
grant execute on function public.reschedule_appointment(uuid, timestamptz) to authenticated;
