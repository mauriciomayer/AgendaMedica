-- Story 2.5 — Médico cancela ou reagenda uma consulta de um paciente
--
-- list_doctor_appointments() lets a doctor read their own upcoming confirmed appointments together
-- with the patient's NAME only. `patients` stays unreadable to doctors (AD-10): the row carries the
-- e-mail, so this SECURITY DEFINER function is the only bridge and never returns it.
-- Cancelling/rescheduling reuse cancel_appointment/reschedule_appointment from 0006 (no changes).

create or replace function public.list_doctor_appointments()
returns table (id uuid, start_time timestamptz, insurance text, patient_name text)
language sql
stable
security definer
set search_path = public, pg_temp
as $$
  select a.id, a.start_time, a.insurance, p.name
  from public.appointments a
  join public.patients p on p.id = a.patient_id
  where a.doctor_id = auth.uid()
    and a.status = 'confirmed'
    and a.start_time >= now()
  order by a.start_time asc;
$$;

revoke all on function public.list_doctor_appointments() from public, anon;
grant execute on function public.list_doctor_appointments() to authenticated;
