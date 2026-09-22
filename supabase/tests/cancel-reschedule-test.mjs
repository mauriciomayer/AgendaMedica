// Proof for cancel_appointment / reschedule_appointment (Story 2.4, RF-8/RF-9), against the linked
// (hosted) project. Run AFTER migration 0006 is applied.
//
// Creates a throwaway doctor and patients through the real register-* Edge Functions, creates
// appointments through book_appointment (or SQL when a start inside the 24h window is needed), and
// checks: valid cancel/reschedule (same appointment id, booked_slots swap, events to the OTHER party),
// the 24h window (just outside -> CONFLICT: cancel_window, just inside -> allowed), racing reschedules
// to one slot (exactly 1 wins), foreign/unknown/anon callers, already-cancelled appointments, the doctor
// as caller, denied direct UPDATE/DELETE and that book_appointment still behaves as in Story 2.3.
// Story 2.5 adds list_doctor_appointments() (migration 0007): only the caller's own upcoming confirmed
// appointments with the patient NAME (never e-mail), empty for a patient, denied to anon; plus the
// doctor's cancel/reschedule flow with events to the patient. Run AFTER migration 0007 is applied.
// Every test row is removed at the end.
//
// The exact-24h boundary cannot be hit against the real clock (now() advances between the SQL insert
// and the call); it is covered by the unit test of podeAlterarConsulta and by the strict `<` in SQL.
//
// Usage: node supabase/tests/cancel-reschedule-test.mjs   (needs local.properties and `supabase link`)

import { spawnSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..", "..");
const props = Object.fromEntries(
  readFileSync(join(root, "local.properties"), "utf8")
    .split(/\r?\n/)
    .filter((l) => l.includes("=") && !l.trim().startsWith("#"))
    .map((l) => [l.slice(0, l.indexOf("=")).trim(), l.slice(l.indexOf("=") + 1).trim()]),
);
const URL_BASE = props.SUPABASE_URL;
const ANON = props.SUPABASE_ANON_KEY;
if (!URL_BASE || !ANON) throw new Error("SUPABASE_URL / SUPABASE_ANON_KEY missing in local.properties");

const PREFIX = `cr-${Date.now()}`;
const PASSWORD = "Teste-123456";
const RACE_ROUNDS = 3;

let failures = 0;
function check(cond, label) {
  if (cond) console.log(`  ok   ${label}`);
  else {
    failures++;
    console.log(`  FAIL ${label}`);
  }
}

function sql(query) {
  query = query.replace(/\s+/g, " "); // the shell quoting below cannot carry newlines
  // The CLI occasionally fails to connect; retry a few times before giving up.
  let r;
  for (let attempt = 1; attempt <= 3; attempt++) {
    r = spawnSync("npx", ["supabase", "db", "query", "--linked", `"${query.replace(/"/g, '\\"')}"`], {
      cwd: root,
      encoding: "utf8",
      shell: true,
    });
    if (r.status === 0) break;
    Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, 1500);
  }
  // Concatenate both streams (not `stderr || stdout`): the CLI often prints only harmless notices
  // (e.g. "npm notice run ...") to stderr while the actual Postgres error body lands in stdout —
  // picking just one at random can silently discard the real error text a caller wants to inspect.
  if (r.status !== 0) throw new Error(`db query failed: ${r.stdout}\n${r.stderr}`);
  const out = r.stdout;
  const json = JSON.parse(out.slice(out.indexOf("{"), out.lastIndexOf("}") + 1));
  return json.rows ?? [];
}

async function fn(name, body) {
  const res = await fetch(`${URL_BASE}/functions/v1/${name}`, {
    method: "POST",
    headers: { "Content-Type": "application/json", apikey: ANON, Authorization: `Bearer ${ANON}` },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`${name} failed: ${res.status} ${await res.text()}`);
}

/** Like `fn`, but never throws — returns { ok, status, message } so a rejected call can be asserted. */
async function tryFn(name, body) {
  const res = await fetch(`${URL_BASE}/functions/v1/${name}`, {
    method: "POST",
    headers: { "Content-Type": "application/json", apikey: ANON, Authorization: `Bearer ${ANON}` },
    body: JSON.stringify(body),
  });
  const text = await res.text();
  let message = text;
  try {
    message = JSON.parse(text).error ?? text;
  } catch {
    // scalar / empty body
  }
  return { ok: res.ok, status: res.status, message };
}

async function login(email) {
  const res = await fetch(`${URL_BASE}/auth/v1/token?grant_type=password`, {
    method: "POST",
    headers: { "Content-Type": "application/json", apikey: ANON },
    body: JSON.stringify({ email, password: PASSWORD }),
  });
  if (!res.ok) throw new Error(`login failed: ${res.status} ${await res.text()}`);
  return (await res.json()).access_token;
}

async function rpc(token, name, body) {
  const res = await fetch(`${URL_BASE}/rest/v1/rpc/${name}`, {
    method: "POST",
    headers: { "Content-Type": "application/json", apikey: ANON, Authorization: `Bearer ${token ?? ANON}` },
    body: JSON.stringify(body),
  });
  const text = await res.text();
  let message = text;
  try {
    message = JSON.parse(text).message ?? text;
  } catch {
    // scalar / empty body
  }
  return { ok: res.ok, status: res.status, message };
}

const book = (t, doctorId, start, insurance = "Unimed") =>
  rpc(t, "book_appointment", { p_doctor_id: doctorId, p_start_time: start, p_insurance: insurance });
const cancel = (t, id) => rpc(t, "cancel_appointment", { p_appointment_id: id });
const reschedule = (t, id, start) => rpc(t, "reschedule_appointment", { p_appointment_id: id, p_new_start_time: start });

/** ISO instant of `hh:mm` (America/Sao_Paulo, UTC-3 all year) on the day `daysAhead` from now. */
function saoPauloSlot(daysAhead, hh, mm) {
  const d = new Date(Date.now() + daysAhead * 86400000 - 3 * 3600000);
  const pad = (n) => String(n).padStart(2, "0");
  return `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())}T${pad(hh)}:${pad(mm)}:00-03:00`;
}

// Valid start positions for a doctor working 08:00-18:00 (Story 5.1): 30min consultation + 15min
// gap = 45min grid from 08:00 (spec Design Notes).
const GRID_SLOTS = [
  [8, 0], [8, 45], [9, 30], [10, 15], [11, 0], [11, 45], [12, 30],
  [13, 15], [14, 0], [14, 45], [15, 30], [16, 15], [17, 0],
];

/** A grid-aligned slot comfortably under 48h from now (tomorrow's first block position, 08:00). */
function underLeadTimeSlot() {
  return saoPauloSlot(1, 8, 0);
}

const instant = (iso) => new Date(iso).getTime();
const apptRow = (id) =>
  sql(`select id, patient_id, doctor_id, start_time, status, insurance from appointments where id = '${id}'`)[0];
const bookedCount = (doctorId, start) =>
  sql(`select count(*)::int as n from booked_slots where doctor_id = '${doctorId}' and start_time = '${start}'`)[0].n;
const events = (apptId, type) =>
  sql(`select recipient_id, delivered_at from notification_events where appointment_id = '${apptId}' and event_type = '${type}'`);

async function main() {
  console.log(`Test prefix: ${PREFIX}`);
  const doctorEmail = `${PREFIX}-doctor@example.com`;
  await fn("register-doctor", {
    name: "Dr. Cancelamento",
    email: doctorEmail,
    password: PASSWORD,
    specialty: "Clínico Geral",
    insurances: ["Unimed", "Amil"],
    location: "Centro, São Paulo - SP",
    schedules: [0, 1, 2, 3, 4, 5, 6].map((weekday) => ({ weekday, startTime: "08:00", endTime: "18:00" })),
  });
  const emails = [0, 1, 2, 3].map((i) => `${PREFIX}-p${i}@example.com`);
  for (const [i, email] of emails.entries()) await fn("register-patient", { name: `Paciente ${i}`, email, password: PASSWORD });
  const doctorId = sql(`select id from auth.users where email = '${doctorEmail}'`)[0].id;
  const ids = emails.map((e) => sql(`select id from auth.users where email = '${e}'`)[0].id);
  const [t0, t1, t2, t3] = await Promise.all(emails.map(login));
  const tDoctor = await login(doctorEmail);

  // ---------------------------------------------------------------------------------------------
  console.log("\nGrade de 45 min e horário da clínica (Story 5.1)");

  let r;
  let reg = await tryFn("register-doctor", {
    name: "Dr. Fora do Horário",
    email: `${PREFIX}-fora-horario@example.com`,
    password: PASSWORD,
    specialty: "Clínico Geral",
    insurances: ["Unimed"],
    location: "Centro, São Paulo - SP",
    schedules: [{ weekday: 1, startTime: "07:45", endTime: "12:00" }],
  });
  check(!reg.ok && String(reg.message).startsWith("INVALID:"), `startTime antes de 08:00 -> INVALID (got ${reg.message})`);

  reg = await tryFn("register-doctor", {
    name: "Dr. Fora do Horário 2",
    email: `${PREFIX}-fora-horario2@example.com`,
    password: PASSWORD,
    specialty: "Clínico Geral",
    insurances: ["Unimed"],
    location: "Centro, São Paulo - SP",
    schedules: [{ weekday: 1, startTime: "14:00", endTime: "18:15" }],
  });
  check(!reg.ok && String(reg.message).startsWith("INVALID:"), `endTime depois de 18:00 -> INVALID (got ${reg.message})`);

  reg = await tryFn("register-doctor", {
    name: "Dr. Padrão Antigo",
    email: `${PREFIX}-padrao-antigo@example.com`,
    password: PASSWORD,
    specialty: "Clínico Geral",
    insurances: ["Unimed"],
    location: "Centro, São Paulo - SP",
    schedules: [{ weekday: 1, startTime: "00:00", endTime: "23:59" }],
  });
  check(!reg.ok && String(reg.message).startsWith("INVALID:"), `padrão antigo 00:00-23:59 -> INVALID (got ${reg.message})`);

  const exactEmail = `${PREFIX}-exato@example.com`;
  reg = await tryFn("register-doctor", {
    name: "Dr. Exato",
    email: exactEmail,
    password: PASSWORD,
    specialty: "Clínico Geral",
    insurances: ["Unimed"],
    location: "Centro, São Paulo - SP",
    schedules: [{ weekday: 1, startTime: "08:00", endTime: "18:00" }],
  });
  check(reg.ok, `exatamente 08:00-18:00 -> aceito (got ${reg.status} ${reg.message})`);
  const exactDoctorId = sql(`select id from auth.users where email = '${exactEmail}'`)[0].id;

  console.log("\nBypass da Edge Function: INSERT direto em doctor_schedules fora de 08h-18h");
  let sqlError = "";
  try {
    sql(`insert into doctor_schedules (doctor_id, weekday, start_time, end_time) values ('${exactDoctorId}', 2, '07:00', '12:00')`);
  } catch (e) {
    sqlError = e.message;
  }
  check(
    sqlError.includes("doctor_schedules_within_clinic_hours"),
    `INSERT direto com start_time < 08:00 é rejeitado pela CHECK constraint (got ${sqlError.slice(0, 120)})`,
  );
  sqlError = "";
  try {
    sql(`insert into doctor_schedules (doctor_id, weekday, start_time, end_time) values ('${exactDoctorId}', 3, '14:00', '19:00')`);
  } catch (e) {
    sqlError = e.message;
  }
  check(
    sqlError.includes("doctor_schedules_within_clinic_hours"),
    `INSERT direto com end_time > 18:00 é rejeitado pela CHECK constraint (got ${sqlError.slice(0, 120)})`,
  );

  const insideEmail = `${PREFIX}-dentro@example.com`;
  reg = await tryFn("register-doctor", {
    name: "Dr. Faixa Interna",
    email: insideEmail,
    password: PASSWORD,
    specialty: "Clínico Geral",
    insurances: ["Unimed"],
    location: "Centro, São Paulo - SP",
    schedules: [{ weekday: 1, startTime: "09:00", endTime: "15:00" }],
  });
  check(reg.ok, `faixa estritamente dentro de 08h-18h (09:00-15:00) -> aceita (got ${reg.status} ${reg.message})`);

  console.log("\nGrade de 45 min: consulta alinhada, desalinhada e cabendo no bloco (bloco 08:00-18:00)");
  const grid1 = saoPauloSlot(26, 9, 30); // 08:00 + 45min*2 -> aligned
  r = await book(t0, doctorId, grid1, "Unimed");
  check(r.ok, `consulta alinhada à grade de 45 min (09:30) -> aceita (got ${r.status} ${r.message})`);
  r = await book(t1, doctorId, saoPauloSlot(26, 9, 15), "Unimed");
  check(!r.ok && r.message.startsWith("INVALID:"), `09:15 não está na grade (08:00+45min*N) -> INVALID (got ${r.message})`);
  r = await book(t1, doctorId, saoPauloSlot(26, 17, 45), "Unimed");
  check(!r.ok && r.message.startsWith("INVALID:"), `17:45 está alinhado mas terminaria às 18:15, fora do bloco -> INVALID (got ${r.message})`);

  console.log("\nMeia-noite não deve enganar a checagem de fim do bloco (wraparound de `time + interval`)");
  r = await book(t1, doctorId, saoPauloSlot(26, 23, 45), "Unimed");
  check(
    !r.ok && r.message.startsWith("INVALID:"),
    `23:45 (perto da virada do dia, fora do bloco 08h-18h) -> INVALID (got ${r.message})`,
  );

  console.log("\nDuas consultas consecutivas (45 min de distância) ambas aceitas");
  const consecA = saoPauloSlot(27, 8, 0);
  const consecB = saoPauloSlot(27, 8, 45);
  r = await book(t2, doctorId, consecA, "Unimed");
  check(r.ok, `primeira consulta às 08:00 -> aceita (got ${r.message})`);
  r = await book(t3, doctorId, consecB, "Unimed");
  check(r.ok, `segunda consulta às 08:45 (45 min depois) -> aceita, sem conflito (got ${r.message})`);

  // ---------------------------------------------------------------------------------------------
  console.log("\nReschedule (valid): same appointment, new slot");
  const s1 = saoPauloSlot(5, 10, 15);
  const s2 = saoPauloSlot(6, 11, 0);
  r = await book(t0, doctorId, s1, "Amil");
  check(r.ok, `patient books ${s1}`);
  const A = r.message.replace(/"/g, "");
  r = await reschedule(t0, A, s2);
  check(r.ok, `reschedule to a free slot succeeds (got ${r.status} ${r.message})`);
  let row = apptRow(A);
  check(row.id === A && instant(row.start_time) === instant(s2), "same appointment id, new start_time");
  check(row.status === "confirmed" && row.doctor_id === doctorId && row.insurance === "Amil", "status, doctor and insurance unchanged");
  check(bookedCount(doctorId, s1) === 0 && bookedCount(doctorId, s2) === 1, "booked_slots swapped old -> new");
  let ev = events(A, "reschedule");
  check(ev.length === 1 && ev[0].recipient_id === doctorId && ev[0].delivered_at === null, "reschedule event for the doctor, undelivered");

  console.log("\nReschedule (invalid inputs leave the appointment untouched)");
  r = await reschedule(t0, A, s2);
  check(!r.ok && r.message.startsWith("INVALID:"), `same slot -> INVALID (got ${r.message})`);
  r = await reschedule(t0, A, saoPauloSlot(6, 10, 7));
  check(!r.ok && r.message.startsWith("INVALID:"), `not aligned to 45 min -> INVALID (got ${r.message})`);
  r = await reschedule(t0, A, underLeadTimeSlot());
  check(!r.ok && r.message === "CONFLICT: lead_time", `new slot under 48h -> CONFLICT: lead_time (got ${r.message})`);
  check(instant(apptRow(A).start_time) === instant(s2), "appointment still at the last valid slot");

  console.log("\nCancel (valid)");
  r = await cancel(t0, A);
  check(r.ok, `cancel succeeds (got ${r.status} ${r.message})`);
  check(apptRow(A).status === "cancelled", "status = cancelled (row kept)");
  check(bookedCount(doctorId, s2) === 0, "slot released from booked_slots");
  ev = events(A, "cancellation");
  check(ev.length === 1 && ev[0].recipient_id === doctorId, "cancellation event for the doctor");
  r = await cancel(t0, A);
  check(!r.ok && r.message.startsWith("INVALID:"), `cancel again -> INVALID (got ${r.message})`);
  r = await reschedule(t0, A, saoPauloSlot(7, 10, 15));
  check(!r.ok && r.message.startsWith("INVALID:"), `reschedule a cancelled one -> INVALID (got ${r.message})`);
  r = await book(t1, doctorId, s2, "Unimed");
  check(r.ok, `released slot can be booked by someone else (got ${r.message})`);

  // ---------------------------------------------------------------------------------------------
  console.log("\n24h window (appointments inserted by SQL to get a start inside the window)");
  const insertAppt = (patientId, offsetSql) =>
    sql(
      `insert into appointments (patient_id, doctor_id, start_time, insurance) values ('${patientId}', '${doctorId}', now() + interval '${offsetSql}', 'Unimed') returning id`,
    )[0].id;
  const near = insertAppt(ids[0], "23 hours 59 minutes");
  const nearStart = apptRow(near).start_time;
  r = await cancel(t0, near);
  check(!r.ok && r.message === "CONFLICT: cancel_window", `cancel under 24h -> CONFLICT: cancel_window (got ${r.message})`);
  r = await reschedule(t0, near, saoPauloSlot(9, 10, 15));
  check(!r.ok && r.message === "CONFLICT: cancel_window", `reschedule under 24h -> CONFLICT: cancel_window (got ${r.message})`);
  r = await cancel(tDoctor, near);
  check(!r.ok && r.message === "CONFLICT: cancel_window", `the doctor cannot bypass the window (got ${r.message})`);
  check(apptRow(near).status === "confirmed" && instant(apptRow(near).start_time) === instant(nearStart), "appointment unchanged");
  check(events(near, "cancellation").length === 0 && events(near, "reschedule").length === 0, "no event recorded for rejected calls");

  const edge = insertAppt(ids[0], "24 hours 1 minute");
  r = await cancel(t0, edge);
  check(r.ok, `just over 24h -> cancel allowed (got ${r.message})`);
  const edge2 = insertAppt(ids[0], "24 hours 1 minute");
  r = await reschedule(t0, edge2, saoPauloSlot(9, 11, 0));
  check(r.ok, `just over 24h -> reschedule allowed (got ${r.message})`);

  // ---------------------------------------------------------------------------------------------
  console.log("\nCallers");
  r = await book(t0, doctorId, saoPauloSlot(10, 9, 30), "Unimed");
  const B = r.message.replace(/"/g, "");
  r = await cancel(t1, B);
  check(!r.ok && r.message.startsWith("FORBIDDEN:"), `other patient cancel -> FORBIDDEN (got ${r.message})`);
  r = await reschedule(t1, B, saoPauloSlot(10, 10, 15));
  check(!r.ok && r.message.startsWith("FORBIDDEN:"), `other patient reschedule -> FORBIDDEN (got ${r.message})`);
  r = await cancel(t0, "00000000-0000-0000-0000-000000000000");
  check(!r.ok && r.message.startsWith("FORBIDDEN:"), `unknown id -> FORBIDDEN (got ${r.message})`);
  r = await cancel(null, B);
  check(!r.ok, `no session (anon) -> rejected (got ${r.status} ${r.message})`);
  check(apptRow(B).status === "confirmed", "appointment untouched by rejected callers");

  r = await reschedule(tDoctor, B, saoPauloSlot(10, 11, 0));
  check(r.ok, `the appointment's doctor can reschedule (got ${r.message})`);
  ev = events(B, "reschedule");
  check(ev.length === 1 && ev[0].recipient_id === ids[0], "doctor reschedule -> event for the PATIENT");
  r = await cancel(tDoctor, B);
  check(r.ok, `the appointment's doctor can cancel (got ${r.message})`);
  ev = events(B, "cancellation");
  check(ev.length === 1 && ev[0].recipient_id === ids[0], "doctor cancel -> event for the PATIENT");

  // ---------------------------------------------------------------------------------------------
  console.log("\nDirect writes are denied");
  r = await book(t0, doctorId, saoPauloSlot(11, 9, 30), "Unimed");
  const C = r.message.replace(/"/g, "");
  const patch = await fetch(`${URL_BASE}/rest/v1/appointments?id=eq.${C}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json", apikey: ANON, Authorization: `Bearer ${t0}` },
    body: JSON.stringify({ status: "cancelled" }),
  });
  check(!patch.ok, `direct UPDATE denied (status ${patch.status})`);
  const del = await fetch(`${URL_BASE}/rest/v1/appointments?id=eq.${C}`, {
    method: "DELETE",
    headers: { apikey: ANON, Authorization: `Bearer ${t0}` },
  });
  check(!del.ok, `direct DELETE denied (status ${del.status})`);
  check(apptRow(C)?.status === "confirmed", "appointment still confirmed after direct write attempts");

  // ---------------------------------------------------------------------------------------------
  console.log(`\nRace: two patients reschedule to the same slot (${RACE_ROUNDS} rounds)`);
  for (let round = 0; round < RACE_ROUNDS; round++) {
    const a = (await book(t2, doctorId, saoPauloSlot(12, ...GRID_SLOTS[round]))).message.replace(/"/g, "");
    const b = (await book(t3, doctorId, saoPauloSlot(13, ...GRID_SLOTS[round]))).message.replace(/"/g, "");
    const target = saoPauloSlot(14, ...GRID_SLOTS[round]);
    const results = await Promise.all([reschedule(t2, a, target), reschedule(t3, b, target)]);
    const wins = results.filter((x) => x.ok).length;
    const taken = results.filter((x) => !x.ok && x.message === "CONFLICT: slot_taken").length;
    check(wins === 1 && taken === 1, `round ${round + 1}: exactly 1 success and 1 CONFLICT: slot_taken (got ${wins}/${taken})`);
    const atTarget = sql(
      `select count(*)::int as n from appointments where doctor_id = '${doctorId}' and start_time = '${target}' and status = 'confirmed'`,
    )[0].n;
    check(atTarget === 1, `round ${round + 1}: one confirmed appointment at the target slot`);
    const loserId = results[0].ok ? b : a;
    const loserOrig = results[0].ok ? saoPauloSlot(13, ...GRID_SLOTS[round]) : saoPauloSlot(12, ...GRID_SLOTS[round]);
    check(instant(apptRow(loserId).start_time) === instant(loserOrig), `round ${round + 1}: loser stays at the original slot`);
    check(bookedCount(doctorId, loserOrig) === 1, `round ${round + 1}: loser's original slot still in booked_slots`);
  }

  // ---------------------------------------------------------------------------------------------
  console.log("\nbook_appointment unchanged (Story 2.3 rules)");
  r = await book(t0, doctorId, underLeadTimeSlot());
  check(!r.ok && r.message === "CONFLICT: lead_time", `under 48h -> CONFLICT: lead_time (got ${r.message})`);
  r = await book(t0, doctorId, saoPauloSlot(15, 10, 15), "Bradesco");
  check(!r.ok && r.message.startsWith("INVALID:"), `insurance not accepted -> INVALID (got ${r.message})`);
  r = await book(t0, doctorId, saoPauloSlot(15, 10, 7));
  check(!r.ok && r.message.startsWith("INVALID:"), `not aligned -> INVALID (got ${r.message})`);
  r = await book(tDoctor, doctorId, saoPauloSlot(15, 10, 15));
  check(!r.ok && r.message.startsWith("FORBIDDEN:"), `doctor caller -> FORBIDDEN (got ${r.message})`);
  r = await book(t0, doctorId, s2);
  check(!r.ok && r.message === "CONFLICT: slot_taken", `taken slot -> CONFLICT: slot_taken (got ${r.message})`);

  // ---------------------------------------------------------------------------------------------
  console.log("\nMinhas Consultas: the exact PostgREST query the app runs");
  // Patient 3 gets: a past confirmed one, a future cancelled one and a future confirmed one (via book).
  const p3 = ids[3];
  const inserted = sql(
    `insert into appointments (patient_id, doctor_id, start_time, status, insurance) values
       ('${p3}', '${doctorId}', now() - interval '3 days', 'confirmed', 'Unimed'),
       ('${p3}', '${doctorId}', now() + interval '20 days', 'cancelled', 'Unimed') returning id`,
  );
  const keep = (await book(t3, doctorId, saoPauloSlot(21, 11, 0), "Amil")).message.replace(/"/g, "");
  const select = encodeURIComponent("id, doctor_id, start_time, insurance, doctors(name, specialty)");
  const list = await fetch(
    `${URL_BASE}/rest/v1/appointments?select=${select}&status=eq.confirmed&start_time=gte.${new Date().toISOString()}&order=start_time.asc`,
    { headers: { apikey: ANON, Authorization: `Bearer ${t3}` } },
  );
  const rows = list.ok ? await list.json() : [];
  check(list.ok, `select with the doctor embed succeeds (status ${list.status})`);
  const listed = rows.map((r) => r.id);
  check(listed.includes(keep), "the future confirmed appointment is listed");
  check(!inserted.some((r) => listed.includes(r.id)), "the past confirmed and the cancelled one are excluded");
  check(rows.every((r, i) => i === 0 || instant(rows[i - 1].start_time) <= instant(r.start_time)), "ordered by start time ascending");
  check(rows.length > 0 && rows.every((r) => r.doctors?.name === "Dr. Cancelamento" && r.doctors?.specialty === "Clínico Geral"), "embedded doctor name and specialty decode");

  console.log("\nlist_doctor_appointments (Story 2.5)");
  const other = `${PREFIX}-doctor2@example.com`;
  await fn("register-doctor", {
    name: "Dr. Outro",
    email: other,
    password: PASSWORD,
    specialty: "Clínico Geral",
    insurances: ["Unimed"],
    location: "Centro, São Paulo - SP",
    schedules: [0, 1, 2, 3, 4, 5, 6].map((weekday) => ({ weekday, startTime: "08:00", endTime: "18:00" })),
  });
  const otherId = sql(`select id from auth.users where email = '${other}'`)[0].id;
  const tOther = await login(other);
  const own = (await book(t0, doctorId, saoPauloSlot(22, 9, 30), "Unimed")).message.replace(/"/g, "");
  const foreign = (await book(t1, otherId, saoPauloSlot(22, 9, 30), "Unimed")).message.replace(/"/g, "");
  const nearMine = insertAppt(ids[1], "23 hours 30 minutes");
  const cancelledMine = insertAppt(ids[2], "10 days");
  sql(`update appointments set status = 'cancelled' where id = '${cancelledMine}'`);
  const pastMine = sql(
    `insert into appointments (patient_id, doctor_id, start_time, status, insurance) values ('${ids[2]}', '${doctorId}', now() - interval '2 days', 'confirmed', 'Unimed') returning id`,
  )[0].id;

  const parse = (res) => {
    try {
      return JSON.parse(res.message);
    } catch {
      return [];
    }
  };
  r = await rpc(tDoctor, "list_doctor_appointments", {});
  let dlist = parse(r);
  check(r.ok, `the doctor can call it (got ${r.status})`);
  const listedIds = dlist.map((x) => x.id);
  check(listedIds.includes(own) && listedIds.includes(nearMine), "own confirmed future appointments are listed (including under 24h)");
  check(!listedIds.includes(foreign), "another doctor's appointment is not listed");
  check(!listedIds.includes(cancelledMine) && !listedIds.includes(pastMine), "cancelled and past ones are excluded");
  check(dlist.every((x, i) => i === 0 || instant(dlist[i - 1].start_time) <= instant(x.start_time)), "ordered by start time ascending");
  const mine = dlist.find((x) => x.id === own);
  check(mine?.patient_name === "Paciente 0" && mine?.insurance === "Unimed", "patient_name and insurance are returned");
  check(dlist.every((x) => Object.keys(x).sort().join(",") === "id,insurance,patient_name,start_time"), "only id, start_time, insurance, patient_name (no e-mail)");
  check(!r.message.includes("@example.com"), "no e-mail anywhere in the payload");

  r = await rpc(tOther, "list_doctor_appointments", {});
  check(r.ok && parse(r).every((x) => x.id !== own) && parse(r).some((x) => x.id === foreign), "another doctor only sees their own");
  r = await rpc(t0, "list_doctor_appointments", {});
  check(r.ok && r.message.trim() === "[]", `a patient receives an empty list (got ${r.message})`);
  r = await rpc(null, "list_doctor_appointments", {});
  check(!r.ok, `no session (anon) is denied (got ${r.status})`);
  const leak = await fetch(`${URL_BASE}/rest/v1/patients?select=email`, { headers: { apikey: ANON, Authorization: `Bearer ${tDoctor}` } });
  check(leak.ok && (await leak.json()).length === 0, "the doctor still cannot read the patients table");

  console.log("\nDoctor flow: cancel and reschedule with event to the patient, 24h window");
  const R1 = saoPauloSlot(23, 9, 30);
  r = await reschedule(tDoctor, own, R1);
  check(r.ok, `doctor reschedules within their own schedule (got ${r.message})`);
  check(apptRow(own).id === own && instant(apptRow(own).start_time) === instant(R1), "same appointment id at the new slot");
  ev = events(own, "reschedule");
  check(ev.length === 1 && ev[0].recipient_id === ids[0], "reschedule event goes to the PATIENT");
  r = await reschedule(tDoctor, own, R1);
  check(!r.ok && r.message.startsWith("INVALID:"), `same slot -> INVALID (got ${r.message})`);
  r = await reschedule(tDoctor, own, underLeadTimeSlot());
  check(!r.ok && r.message === "CONFLICT: lead_time", `new slot under 48h -> lead_time (got ${r.message})`);
  r = await reschedule(tDoctor, nearMine, saoPauloSlot(24, 9, 30));
  check(!r.ok && r.message === "CONFLICT: cancel_window", `doctor reschedule under 24h -> cancel_window (got ${r.message})`);
  r = await cancel(tDoctor, nearMine);
  check(!r.ok && r.message === "CONFLICT: cancel_window", `doctor cancel under 24h -> cancel_window (got ${r.message})`);
  r = await cancel(tOther, own);
  check(!r.ok && r.message.startsWith("FORBIDDEN:"), `another doctor cannot cancel it (got ${r.message})`);
  r = await cancel(tDoctor, own);
  check(r.ok, `doctor cancels (got ${r.message})`);
  check(apptRow(own).status === "cancelled" && bookedCount(doctorId, R1) === 0, "status cancelled and slot released");
  ev = events(own, "cancellation");
  check(ev.length === 1 && ev[0].recipient_id === ids[0], "cancellation event goes to the PATIENT");
  r = await rpc(tDoctor, "list_doctor_appointments", {});
  check(r.ok && !parse(r).some((x) => x.id === own), "cancelled appointment leaves the doctor's list");
  r = await cancel(tDoctor, own);
  check(!r.ok && r.message.startsWith("INVALID:"), `cancelling again -> INVALID (got ${r.message})`);

  console.log("\nOutside the doctor's schedule (last: removes one weekday's block)");
  const far = saoPauloSlot(21, 10, 15);
  const weekday = new Date(new Date(far).getTime() - 3 * 3600000).getUTCDay();
  const dSlot = saoPauloSlot(16, 10, 15);
  const D = (await book(t0, doctorId, dSlot)).message.replace(/"/g, "");
  sql(`delete from doctor_schedules where doctor_id = '${doctorId}' and weekday = ${weekday}`);
  r = await reschedule(t0, D, far);
  check(!r.ok && r.message.startsWith("INVALID:"), `slot outside the schedule -> INVALID (got ${r.message})`);
  check(instant(apptRow(D).start_time) === instant(dSlot), "appointment unchanged");
}

let exitCode = 0;
try {
  await main();
} catch (e) {
  console.error(e);
  failures++;
} finally {
  console.log("\nCleaning up test data");
  try {
    sql(`delete from auth.users where email like '${PREFIX}-%'`);
    const left = sql(`select count(*)::int as n from auth.users where email like '${PREFIX}-%'`)[0].n;
    console.log(left === 0 ? "  test users removed (cascade)" : `  WARNING: ${left} test users left`);
    if (left !== 0) failures++;
  } catch (e) {
    console.error("cleanup failed:", e.message);
    failures++;
  }
  exitCode = failures === 0 ? 0 : 1;
  console.log(failures === 0 ? "\nALL CHECKS PASSED" : `\n${failures} CHECK(S) FAILED`);
}
process.exit(exitCode);
