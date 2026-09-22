// Proof for Story 3.2 (Sistema registra eventos de notificação para entrega futura, AD-12), against
// the linked (hosted) project.
//
// The insertion of `notification_events` by book_appointment/cancel_appointment/reschedule_appointment
// (migrations 0005/0006) is already proven by supabase/tests/concurrency-test.mjs (new_appointment,
// undelivered, one per booking) and cancel-reschedule-test.mjs (cancellation/reschedule, correct
// recipient — the OTHER party, whichever side acted — and no event on a rejected call). This script
// covers the one gap those leave: `notification_events` has RLS enabled with no policies and every
// grant revoked from anon/authenticated (0005_appointments.sql), but nothing exercised that a client
// actually cannot read or write it directly. It proves that boundary of AD-12 ("no client access").
//
// Usage: node supabase/tests/notification-events-test.mjs   (needs local.properties and `supabase link`)

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

const PREFIX = `ne-${Date.now()}`;
const PASSWORD = "Teste-123456";

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

/** ISO instant of `hh:mm` (America/Sao_Paulo, UTC-3 all year) on the day `daysAhead` from now. */
function saoPauloSlot(daysAhead, hh, mm) {
  const d = new Date(Date.now() + daysAhead * 86400000 - 3 * 3600000);
  const pad = (n) => String(n).padStart(2, "0");
  return `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())}T${pad(hh)}:${pad(mm)}:00-03:00`;
}

/**
 * Attempts a direct REST call against notification_events as `token` (null = anon; anon still sends
 * a valid `apikey`/anon-role token — what is denied is the role's table grant, not a missing credential).
 * `filter` is a query string suffix (e.g. "?id=eq.<uuid>") for PATCH/DELETE. Never throws.
 */
async function restCall(method, token, { body, filter = "" } = {}) {
  const headers = { "Content-Type": "application/json", apikey: ANON, Authorization: `Bearer ${token ?? ANON}` };
  if (method === "POST") headers.Prefer = "return=representation"; // omitted entirely for other methods
  const res = await fetch(`${URL_BASE}/rest/v1/notification_events${filter}`, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  });
  return { ok: res.ok, status: res.status };
}

async function main() {
  console.log(`Test prefix: ${PREFIX}`);
  const doctorEmail = `${PREFIX}-doctor@example.com`;
  const patientEmail = `${PREFIX}-p0@example.com`;
  await fn("register-doctor", {
    name: "Dr. Eventos",
    email: doctorEmail,
    password: PASSWORD,
    specialty: "Clínico Geral",
    insurances: ["Unimed"],
    location: "Centro, São Paulo - SP",
    schedules: [0, 1, 2, 3, 4, 5, 6].map((weekday) => ({ weekday, startTime: "08:00", endTime: "18:00" })),
  });
  await fn("register-patient", { name: "Paciente Eventos", email: patientEmail, password: PASSWORD });
  const doctorId = sql(`select id from auth.users where email = '${doctorEmail}'`)[0].id;
  const tPatient = await login(patientEmail);
  const tDoctor = await login(doctorEmail);

  console.log("\nSetup: book an appointment to generate a real notification_events row");
  const r = await book(tPatient, doctorId, saoPauloSlot(5, 10, 15), "Unimed"); // 10:15 is on the 45-min grid from 08:00
  check(r.ok, `patient books an appointment (got ${r.status} ${r.message})`);
  const apptId = r.message.replace(/"/g, "");
  const before = sql(
    `select id, recipient_id, delivered_at from notification_events where appointment_id = '${apptId}'`,
  )[0];
  check(before && before.recipient_id === doctorId && before.delivered_at === null, "event exists (proven by concurrency-test.mjs already; sanity check here)");

  const roles = [
    ["anon", null],
    ["patient (own event's other party)", tPatient],
    ["doctor (recipient)", tDoctor],
  ];
  const idFilter = `?id=eq.${before.id}`;

  console.log("\nDirect read is denied to every client role (AD-12: no client access)");
  for (const [label, token] of roles) {
    const res = await restCall("GET", token);
    check(!res.ok, `${label} GET notification_events -> denied (got ${res.status})`);
  }

  console.log("\nDirect writes are denied to every client role x method");
  for (const [label, token] of roles) {
    const insertRes = await restCall("POST", token, {
      body: { recipient_id: doctorId, event_type: "new_appointment", appointment_id: apptId },
    });
    check(!insertRes.ok, `${label} INSERT notification_events -> denied (got ${insertRes.status})`);

    const patchRes = await restCall("PATCH", token, { body: { delivered_at: new Date().toISOString() }, filter: idFilter });
    check(!patchRes.ok, `${label} UPDATE (delivered_at) -> denied (got ${patchRes.status})`);

    const delRes = await restCall("DELETE", token, { filter: idFilter });
    check(!delRes.ok, `${label} DELETE -> denied (got ${delRes.status})`);
  }

  const after = sql(`select recipient_id, delivered_at from notification_events where id = '${before.id}'`)[0];
  check(after && after.recipient_id === before.recipient_id && after.delivered_at === null, "row unchanged after every rejected attempt");
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
    sql(`delete from auth.users where email like '%${PREFIX}-%'`);
    const left = sql(`select count(*)::int as n from auth.users where email like '%${PREFIX}-%'`)[0].n;
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
