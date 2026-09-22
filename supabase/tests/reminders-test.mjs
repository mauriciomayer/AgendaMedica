// Proof for the 24h e-mail reminder (Story 3.1, FR10/NFR2/AD-5), against the linked (hosted) project.
// Run AFTER migration 0008 is applied, the send-reminders function is deployed and REMINDER_CRON_SECRET
// is set both as a function secret and in local.properties.
//
// All sends use the test-only sendMode ("simulate-success" / "simulate-failure"), so Resend is never
// called. Checks: 401 without/with a wrong secret (nothing claimed); only due appointments are claimed
// (not due, already reminded, cancelled and past stay untouched); a second call does not resend;
// 5 concurrent calls send each appointment exactly once; an isolated failure does not stop the others
// and releases its claim (retried on the next run); rescheduling resets the reminder; the pg_cron job
// exists every 5 minutes and holds no literal secret. Every test row is removed at the end.
//
// NOTE: the simulated modes claim ANY due appointment in the project, not only the test ones, and mark
// it as reminded without sending an e-mail. assertNoForeignDueAppointments() guards every claiming
// call below and aborts the whole run rather than silently consuming a real patient's reminder.
//
// Usage: node supabase/tests/reminders-test.mjs   (needs local.properties and `supabase link`)

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
const CRON_SECRET = props.REMINDER_CRON_SECRET;
if (!URL_BASE || !ANON || !CRON_SECRET) {
  throw new Error("SUPABASE_URL / SUPABASE_ANON_KEY / REMINDER_CRON_SECRET missing in local.properties");
}

const PREFIX = `rem-${Date.now()}`;
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

/** Calls send-reminders. `secret` undefined -> the real secret; null -> no header. */
async function callReminders(sendMode, secret = CRON_SECRET) {
  const headers = { "Content-Type": "application/json" };
  if (secret !== null) headers["x-cron-secret"] = secret;
  const res = await fetch(`${URL_BASE}/functions/v1/send-reminders`, {
    method: "POST",
    headers,
    body: JSON.stringify(sendMode ? { sendMode } : {}),
  });
  let body = null;
  try {
    body = await res.json();
  } catch {
    // empty body
  }
  return { status: res.status, body };
}

const insertAppt = (patientId, doctorId, offset, extraCols = "", extraVals = "") =>
  sql(
    `insert into appointments (patient_id, doctor_id, start_time, insurance ${extraCols}) values ('${patientId}', '${doctorId}', now() + interval '${offset}', 'Unimed' ${extraVals}) returning id`,
  )[0].id;

const remState = (id) => sql(`select reminder_sent_at, status, start_time from appointments where id = '${id}'`)[0];

/**
 * Aborts the run if a real (non-test) due appointment exists: a simulate-* call would silently
 * mark it as reminded without sending its actual e-mail. Called before every claiming call below.
 */
function assertNoForeignDueAppointments() {
  const foreign = sql(
    `select a.id from appointments a join patients p on p.id = a.patient_id ` +
      `where a.status = 'confirmed' and a.reminder_sent_at is null ` +
      `and a.start_time > now() and a.start_time <= now() + interval '24 hours' ` +
      `and p.email not like '%${PREFIX}-%'`,
  );
  if (foreign.length > 0) {
    throw new Error(
      `Refusing to run: ${foreign.length} real due appointment(s) exist outside this test's own data ` +
        `(a simulate-* call would mark them reminded without sending e-mail). Re-run when none are pending.`,
    );
  }
}

async function main() {
  console.log(`Test prefix: ${PREFIX}`);
  const doctorEmail = `${PREFIX}-doctor@example.com`;
  await fn("register-doctor", {
    name: "Dr. Lembrete",
    email: doctorEmail,
    password: PASSWORD,
    specialty: "Clínico Geral",
    insurances: ["Unimed"],
    location: "Centro, São Paulo - SP",
    schedules: [0, 1, 2, 3, 4, 5, 6].map((weekday) => ({ weekday, startTime: "00:00", endTime: "23:59" })),
  });
  const okEmail = `${PREFIX}-p0@example.com`;
  const failEmail = `fail-${PREFIX}-p1@example.com`;
  await fn("register-patient", { name: "Paciente Ok", email: okEmail, password: PASSWORD });
  await fn("register-patient", { name: "Paciente Falha", email: failEmail, password: PASSWORD });
  const doctorId = sql(`select id from auth.users where email = '${doctorEmail}'`)[0].id;
  const okId = sql(`select id from auth.users where email = '${okEmail}'`)[0].id;
  const failId = sql(`select id from auth.users where email = '${failEmail}'`)[0].id;

  // ---------------------------------------------------------------------------------------------
  console.log("\nSchedule");
  const job = sql(`select schedule, command from cron.job where jobname = 'send-reminders'`);
  check(job.length === 1 && job[0].schedule === "*/5 * * * *", "pg_cron job send-reminders runs every 5 minutes");
  check(
    job.length === 1 && job[0].command.includes("reminder_cron_secret") && job[0].command.includes("vault.decrypted_secrets"),
    "job reads the secret from the Vault at run time",
  );
  check(job.length === 1 && !job[0].command.includes(CRON_SECRET), "job command holds no literal secret");

  // ---------------------------------------------------------------------------------------------
  console.log("\nAuthorization");
  const early = insertAppt(okId, doctorId, "2 hours");
  let r = await callReminders("simulate-success", null);
  check(r.status === 401, `no secret -> 401 (got ${r.status})`);
  r = await callReminders("simulate-success", `${CRON_SECRET}x`);
  check(r.status === 401, `wrong secret -> 401 (got ${r.status})`);
  check(remState(early).reminder_sent_at === null, "nothing claimed by unauthorized calls");
  sql(`delete from appointments where id = '${early}'`);

  // ---------------------------------------------------------------------------------------------
  console.log("\nSelection and failure isolation (simulate-failure)");
  const due1 = insertAppt(okId, doctorId, "23 hours");
  const due2 = insertAppt(okId, doctorId, "20 hours");
  const dueFail = insertAppt(failId, doctorId, "22 hours");
  const notDue = insertAppt(okId, doctorId, "25 hours");
  const already = insertAppt(okId, doctorId, "10 hours", ", reminder_sent_at", ", now() - interval '1 hour'");
  const alreadyBefore = remState(already).reminder_sent_at;
  const cancelled = insertAppt(okId, doctorId, "12 hours", ", status", ", 'cancelled'");
  const past = insertAppt(okId, doctorId, "-1 hour");

  assertNoForeignDueAppointments();
  r = await callReminders("simulate-failure");
  check(r.status === 200 && r.body && r.body.due >= 3, `200 with counts (got ${r.status} ${JSON.stringify(r.body)})`);
  check(r.body && r.body.sent >= 2 && r.body.failed >= 1, "failed and sent counts reported");
  const s1 = remState(due1).reminder_sent_at;
  const s2 = remState(due2).reminder_sent_at;
  check(s1 !== null && s2 !== null, "due appointments are marked as reminded, despite the other one failing");
  check(remState(dueFail).reminder_sent_at === null, "the failed one is released (reminder_sent_at back to NULL)");
  check(remState(notDue).reminder_sent_at === null, "appointment beyond 24h untouched");
  check(remState(already).reminder_sent_at === alreadyBefore, "already reminded untouched");
  check(remState(cancelled).reminder_sent_at === null, "cancelled untouched");
  check(remState(past).reminder_sent_at === null, "past appointment untouched");

  console.log("\nNo resend; failed one retried on the next run");
  r = await callReminders("simulate-failure");
  check(r.status === 200 && r.body.failed >= 1, "failed one is retried (and fails again)");
  check(remState(due1).reminder_sent_at === s1 && remState(due2).reminder_sent_at === s2, "already reminded ones are not claimed again");
  check(remState(dueFail).reminder_sent_at === null, "still released after the second failure");
  r = await callReminders("simulate-success");
  check(r.status === 200 && r.body.sent >= 1, "retry succeeds once sending works");
  check(remState(dueFail).reminder_sent_at !== null, "previously failed one is now marked as reminded");
  const sFail = remState(dueFail).reminder_sent_at;
  r = await callReminders("simulate-success");
  check(remState(dueFail).reminder_sent_at === sFail && remState(due1).reminder_sent_at === s1, "next run changes nothing");

  // ---------------------------------------------------------------------------------------------
  console.log("\nReschedule resets the reminder");
  sql(`update appointments set start_time = start_time + interval '15 minutes' where id = '${due1}'`);
  check(remState(due1).reminder_sent_at === null, "changing start_time sets reminder_sent_at back to NULL");
  r = await callReminders("simulate-success");
  check(remState(due1).reminder_sent_at !== null, "the new time gets a new reminder");
  sql(`update appointments set insurance = 'Unimed' where id = '${due2}'`);
  check(remState(due2).reminder_sent_at === s2, "an update that keeps start_time keeps the reminder");

  // ---------------------------------------------------------------------------------------------
  console.log("\nConcurrent runs (5 simultaneous calls)");
  assertNoForeignDueAppointments();
  const raceIds = ["3 hours", "4 hours", "5 hours", "6 hours"].map((o) => insertAppt(okId, doctorId, o));
  const strays = sql(
    `select count(*)::int as n from appointments where status = 'confirmed' and reminder_sent_at is null and start_time > now() and start_time <= now() + interval '24 hours' and id not in (${raceIds.map((i) => `'${i}'`).join(",")})`,
  )[0].n;
  const results = await Promise.all(Array.from({ length: 5 }, () => callReminders("simulate-success")));
  check(results.every((x) => x.status === 200), "all 5 calls answered 200");
  const totalSent = results.reduce((n, x) => n + (x.body?.sent ?? 0), 0);
  check(totalSent === raceIds.length + strays, `each appointment claimed exactly once (sent ${totalSent}, expected ${raceIds.length + strays})`);
  check(raceIds.every((id) => remState(id).reminder_sent_at !== null), "all race appointments are marked as reminded");
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
