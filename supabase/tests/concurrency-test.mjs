// Concurrency proof for book_appointment (Story 2.3, RF-6/RF-7, MS-2), against the linked (hosted) project.
//
// Creates a throwaway doctor and several patients through the real register-* Edge Functions, fires
// simultaneous book_appointment calls at the same doctor/slot over several rounds, and requires exactly
// one success per round with `CONFLICT: slot_taken` for all the others. Also checks the direct-call
// rules (lead time, insurance, alignment, role, RLS) and cancellation releasing the slot, then removes
// every test row.
//
// Usage: node supabase/tests/concurrency-test.mjs   (needs local.properties and `supabase link`)

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

const PREFIX = `conc-${Date.now()}`;
const PASSWORD = "Teste-123456";
const PATIENTS = 8;
const ROUNDS = 5;

let failures = 0;
function check(cond, label) {
  if (cond) console.log(`  ok   ${label}`);
  else {
    failures++;
    console.log(`  FAIL ${label}`);
  }
}

function sql(query) {
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
  if (r.status !== 0) throw new Error(`db query failed: ${r.stderr || r.stdout}`);
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

async function login(email) {
  const res = await fetch(`${URL_BASE}/auth/v1/token?grant_type=password`, {
    method: "POST",
    headers: { "Content-Type": "application/json", apikey: ANON },
    body: JSON.stringify({ email, password: PASSWORD }),
  });
  if (!res.ok) throw new Error(`login failed: ${res.status} ${await res.text()}`);
  return (await res.json()).access_token;
}

async function book(token, doctorId, start, insurance) {
  const res = await fetch(`${URL_BASE}/rest/v1/rpc/book_appointment`, {
    method: "POST",
    headers: { "Content-Type": "application/json", apikey: ANON, Authorization: `Bearer ${token ?? ANON}` },
    body: JSON.stringify({ p_doctor_id: doctorId, p_start_time: start, p_insurance: insurance }),
  });
  const text = await res.text();
  let message = text;
  try {
    message = JSON.parse(text).message ?? text;
  } catch {
    // scalar body (the appointment id)
  }
  return { ok: res.ok, status: res.status, message };
}

/** ISO instant of `hh:mm` (America/Sao_Paulo, UTC-3 all year) on the day `daysAhead` from now. */
function saoPauloSlot(daysAhead, hh, mm) {
  const d = new Date(Date.now() + daysAhead * 86400000 - 3 * 3600000); // shift to SP wall clock
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

async function main() {
  console.log(`Test prefix: ${PREFIX}`);
  const doctorEmail = `${PREFIX}-doctor@example.com`;
  await fn("register-doctor", {
    name: "Dr. Concorrencia",
    email: doctorEmail,
    password: PASSWORD,
    specialty: "Clínico Geral",
    insurances: ["Unimed", "Amil"],
    location: "Centro, São Paulo - SP",
    schedules: [0, 1, 2, 3, 4, 5, 6].map((weekday) => ({ weekday, startTime: "08:00", endTime: "18:00" })),
  });
  const patientEmails = [];
  for (let i = 0; i < PATIENTS; i++) {
    const email = `${PREFIX}-p${i}@example.com`;
    patientEmails.push(email);
    await fn("register-patient", { name: `Paciente ${i}`, email, password: PASSWORD });
  }
  const doctorId = sql(`select id from auth.users where email = '${doctorEmail}'`)[0].id;
  const tokens = await Promise.all(patientEmails.map(login));
  const doctorToken = await login(doctorEmail);

  console.log(`\nConcurrency: ${PATIENTS} simultaneous requests x ${ROUNDS} rounds`);
  const slots = [];
  for (let round = 0; round < ROUNDS; round++) {
    const start = saoPauloSlot(5, ...GRID_SLOTS[round]);
    slots.push(start);
    const results = await Promise.all(tokens.map((t) => book(t, doctorId, start, "Unimed")));
    const wins = results.filter((r) => r.ok).length;
    const taken = results.filter((r) => !r.ok && r.message === "CONFLICT: slot_taken").length;
    check(wins === 1, `round ${round + 1}: exactly 1 success (got ${wins})`);
    check(taken === PATIENTS - 1, `round ${round + 1}: ${PATIENTS - 1} x CONFLICT: slot_taken (got ${taken})`);
  }

  console.log("\nDatabase state");
  const confirmed = sql(
    `select start_time, count(*)::int as n from appointments where doctor_id = '${doctorId}' and status = 'confirmed' group by start_time`,
  );
  check(confirmed.length === ROUNDS && confirmed.every((r) => r.n === 1), "1 confirmed appointment per slot");
  const booked = sql(`select count(*)::int as n from booked_slots where doctor_id = '${doctorId}'`)[0].n;
  check(booked === ROUNDS, `booked_slots has ${ROUNDS} rows (got ${booked})`);
  const events = sql(
    `select count(*)::int as n from notification_events where recipient_id = '${doctorId}' and event_type = 'new_appointment' and delivered_at is null`,
  )[0].n;
  check(events === ROUNDS, `${ROUNDS} new_appointment events for the doctor, undelivered (got ${events})`);

  console.log("\nDirect-call rules");
  let r = await book(tokens[0], doctorId, underLeadTimeSlot(), "Unimed");
  check(!r.ok && r.message === "CONFLICT: lead_time", `under 48h -> CONFLICT: lead_time (got ${r.message})`);
  r = await book(tokens[0], doctorId, saoPauloSlot(6, 10, 15), "Bradesco");
  check(!r.ok && r.message.startsWith("INVALID:"), `insurance not accepted -> INVALID (got ${r.message})`);
  r = await book(tokens[0], doctorId, saoPauloSlot(6, 10, 7), "Unimed");
  check(!r.ok && r.message.startsWith("INVALID:"), `not aligned to 45 min -> INVALID (got ${r.message})`);
  r = await book(tokens[0], "00000000-0000-0000-0000-000000000000", saoPauloSlot(6, 10, 15), "Unimed");
  check(!r.ok && r.message.startsWith("INVALID:"), `unknown doctor -> INVALID (got ${r.message})`);
  r = await book(doctorToken, doctorId, saoPauloSlot(6, 10, 15), "Unimed");
  check(!r.ok && r.message.startsWith("FORBIDDEN:"), `doctor caller -> FORBIDDEN (got ${r.message})`);
  r = await book(null, doctorId, saoPauloSlot(6, 10, 15), "Unimed");
  check(!r.ok, `no session (anon) -> rejected (got ${r.status} ${r.message})`);

  const direct = await fetch(`${URL_BASE}/rest/v1/appointments`, {
    method: "POST",
    headers: { "Content-Type": "application/json", apikey: ANON, Authorization: `Bearer ${tokens[0]}` },
    body: JSON.stringify({ patient_id: "00000000-0000-0000-0000-000000000000", doctor_id: doctorId, start_time: saoPauloSlot(7, 9, 30), insurance: "Unimed" }),
  });
  check(!direct.ok, `direct INSERT into appointments denied (status ${direct.status})`);

  console.log("\nCancellation releases the slot");
  sql(`update appointments set status = 'cancelled' where doctor_id = '${doctorId}' and start_time = '${slots[0]}'`);
  const after = sql(`select count(*)::int as n from booked_slots where doctor_id = '${doctorId}' and start_time = '${slots[0]}'`)[0].n;
  check(after === 0, "cancelled appointment removed from booked_slots");
  r = await book(tokens[1], doctorId, slots[0], "Amil");
  check(r.ok, `slot can be booked again after cancelling (got ${r.message})`);
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
