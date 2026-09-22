-- Story 3.1 — Paciente recebe lembrete de consulta por e-mail 24h antes (FR10, AD-5)
--
-- A pg_cron job calls the send-reminders Edge Function every 5 minutes. The function claims due
-- appointments atomically via claim_due_reminders() and e-mails each patient. Reminders are by
-- "due" (start_time <= now() + 24h), not a fixed 5-minute window, so a late job or a failed send is
-- retried on later runs until the appointment starts. The job secret lives in Supabase Vault
-- (reminder_cron_secret) and is read at run time; it is never in this file.

-- =============================================================================================
-- Column, index, reset trigger
-- =============================================================================================

alter table public.appointments add column reminder_sent_at timestamptz;

comment on column public.appointments.reminder_sent_at is 'Set atomically by claim_due_reminders() before the reminder e-mail is sent; reset to NULL on send failure (release_reminder) or when start_time changes.';

create index appointments_reminder_due_idx
  on public.appointments (start_time)
  where status = 'confirmed' and reminder_sent_at is null;

create or replace function public.reset_reminder_on_reschedule()
returns trigger
language plpgsql
set search_path = public, pg_temp
as $$
begin
  if new.start_time is distinct from old.start_time then
    new.reminder_sent_at := null;
  end if;
  return new;
end;
$$;

revoke all on function public.reset_reminder_on_reschedule() from public;

create trigger appointments_reset_reminder
  before update of start_time on public.appointments
  for each row execute function public.reset_reminder_on_reschedule();

-- =============================================================================================
-- claim_due_reminders / release_reminder (service_role only)
-- =============================================================================================

create or replace function public.claim_due_reminders(p_limit int default 100)
returns table (
  appointment_id uuid,
  patient_email text,
  patient_name text,
  doctor_name text,
  doctor_specialty text,
  doctor_city text,
  doctor_neighborhood text,
  insurance text,
  start_time timestamptz,
  claimed_at timestamptz
)
language sql
security definer
set search_path = public, pg_temp
as $$
  with claimed as (
    update public.appointments a
    set reminder_sent_at = now()
    where a.id in (
      select d.id
      from public.appointments d
      where d.status = 'confirmed'
        and d.reminder_sent_at is null
        and d.start_time > now()
        and d.start_time <= now() + interval '24 hours'
      order by d.start_time
      limit greatest(coalesce(p_limit, 100), 0)
      for update skip locked
    )
    returning a.id, a.patient_id, a.doctor_id, a.insurance, a.start_time, a.reminder_sent_at
  )
  select c.id, p.email, p.name, dr.name, dr.specialty, dr.city, dr.neighborhood,
         c.insurance, c.start_time, c.reminder_sent_at
  from claimed c
  join public.patients p on p.id = c.patient_id
  join public.doctors dr on dr.id = c.doctor_id
  order by c.start_time;
$$;

revoke all on function public.claim_due_reminders(int) from public, anon, authenticated;
grant execute on function public.claim_due_reminders(int) to service_role;

create or replace function public.release_reminder(p_id uuid, p_claimed_at timestamptz)
returns void
language sql
security definer
set search_path = public, pg_temp
as $$
  update public.appointments
  set reminder_sent_at = null
  where id = p_id and reminder_sent_at = p_claimed_at;
$$;

revoke all on function public.release_reminder(uuid, timestamptz) from public, anon, authenticated;
grant execute on function public.release_reminder(uuid, timestamptz) to service_role;

-- =============================================================================================
-- Scheduling: pg_cron + pg_net
-- =============================================================================================

create extension if not exists pg_cron with schema pg_catalog;
create extension if not exists pg_net with schema extensions;

do $$
begin
  if exists (select 1 from cron.job where jobname = 'send-reminders') then
    perform cron.unschedule('send-reminders');
  end if;
end;
$$;

select cron.schedule(
  'send-reminders',
  '*/5 * * * *',
  $job$
  select net.http_post(
    url := 'https://vuqvizzkdeiseyunjrms.supabase.co/functions/v1/send-reminders',
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'x-cron-secret', (select decrypted_secret from vault.decrypted_secrets where name = 'reminder_cron_secret')
    ),
    body := '{}'::jsonb,
    timeout_milliseconds := 30000
  );
  $job$
);
