-- Story 5.1 — Sistema usa consultas de 30 minutos com intervalo de 15, dentro do horário da
-- clínica (08h-18h)
--
-- Sprint Change Proposal (2026-09-22): "Slot" deixa de ser um intervalo de 15 minutos livremente
-- escolhido (06h-22h) e passa a ser uma Consulta de 30 minutos com 15 minutos de intervalo
-- obrigatório até a próxima (grade efetiva de 45 em 45 min a partir do `start_time` do bloco de
-- cada Médico), dentro da janela fixa da clínica, 08h-18h. O Médico continua escolhendo
-- livremente seu próprio horário de início/fim por dia — só o limite muda.
--
-- Normaliza (`UPDATE`) as linhas de `doctor_schedules` já existentes que ficariam fora de
-- 08:00-18:00 antes de adicionar a `CHECK constraint`, para não falhar contra dados de teste já
-- cadastrados (mesmo padrão de backfill da Story 2.1 para localização). Em seguida recria
-- `assert_slot_bookable` (única função que muda — `book_appointment`/`cancel_appointment`/
-- `reschedule_appointment`, 0005/0006, não precisam ser recriadas: chamam-na pelo nome).

-- =============================================================================================
-- Normalização de dados existentes fora da nova janela (08:00-18:00)
--
-- Feita em um único UPDATE (não dois): a constraint doctor_schedules_time_order (0001, start_time
-- < end_time) é verificada ao final de CADA statement, não só no commit da transação. Uma linha
-- válida sob a regra antiga (06h-22h) mas totalmente fora da nova janela — ex.: 18:00-20:00 —
-- viraria 18:00-18:00 se o recorte (greatest/least) rodasse como UPDATE isolado, violando aquela
-- constraint antes que um segundo UPDATE pudesse corrigir a inversão. Calculando as duas colunas
-- na mesma expressão (lendo sempre os valores originais da linha), o resultado já sai consistente.
-- =============================================================================================

update public.doctor_schedules
set
  start_time = case
    when greatest(start_time, time '08:00') >= least(end_time, time '18:00') then time '08:00'
    else greatest(start_time, time '08:00')
  end,
  end_time = case
    when greatest(start_time, time '08:00') >= least(end_time, time '18:00') then time '18:00'
    else least(end_time, time '18:00')
  end;

-- =============================================================================================
-- Garantia final no armazenamento (NFR1): mesmo um INSERT direto, sem passar pela Edge Function
-- register-doctor, não pode gravar um horário fora de 08:00-18:00.
-- =============================================================================================

alter table public.doctor_schedules
  add constraint doctor_schedules_within_clinic_hours
  check (start_time >= time '08:00' and end_time <= time '18:00');

-- =============================================================================================
-- assert_slot_bookable: grade de 45 em 45 min (30 min de consulta + 15 min de intervalo) a
-- partir do start_time do bloco daquele médico/dia, com a consulta inteira cabendo antes do
-- end_time do bloco. Continua lida genericamente por doctor_id+weekday (nunca crava 08:00/18:00
-- na própria aritmética de alinhamento — isso só entra via a CHECK constraint acima, na origem
-- do dado). book_appointment/reschedule_appointment chamam esta função pelo nome e não
-- precisam ser recriadas.
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
  v_block public.doctor_schedules%rowtype;
begin
  if extract(second from v_local) <> 0 then
    raise exception 'INVALID: horário fora da grade de horários';
  end if;

  select * into v_block
  from public.doctor_schedules s
  where s.doctor_id = p_doctor_id
    and s.weekday = extract(dow from v_local)::int;

  if not found then
    raise exception 'INVALID: horário fora da agenda do médico';
  end if;

  -- Comparing via epoch-seconds (not `v_local::time + interval '30 minutes'`) on purpose: Postgres
  -- wraps `time + interval` arithmetic modulo 24h (e.g. 23:45 + 30min = 00:15:00), which would make
  -- a late-night start_time silently pass the "fits before end_time" check. Plain integer seconds
  -- never wrap, so this is safe for any time-of-day.
  if v_local::time < v_block.start_time
     or extract(epoch from v_local::time)::int + 1800 > extract(epoch from v_block.end_time)::int then
    raise exception 'INVALID: horário fora da agenda do médico';
  end if;

  if extract(epoch from (v_local::time - v_block.start_time))::int % 2700 <> 0 then -- 2700s = 45min
    raise exception 'INVALID: horário fora da grade de horários';
  end if;

  if p_start_time < now() + interval '48 hours' then
    raise exception 'CONFLICT: lead_time';
  end if;
end;
$$;
