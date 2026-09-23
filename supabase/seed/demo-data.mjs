// Massa de demonstração do Agenda Médica: limpa a base HOSPEDADA e a popula com dados fictícios
// (médicos, pacientes e consultas) para demonstrações e prints. Nada nos dados lembra "teste".
//
// O cadastro passa pelas Edge Functions reais (register-doctor / register-patient) e as consultas
// por book_appointment, então a massa obedece às mesmas regras do app (grade de 45 em 45 min dentro
// de 08h-18h, antecedência mínima de 48h, fuso America/Sao_Paulo). Uma consulta a menos de 24h é
// inserida por SQL para mostrar o estado "Bloqueada". Todas as consultas ficam marcadas como
// "lembrete já enviado" para o job de lembretes não escrever para os e-mails fictícios (@example.com).
//
// Uso (na raiz do projeto; precisa de local.properties e `npx supabase link`):
//   DEMO_PASSWORD=<senha das contas> node supabase/seed/demo-data.mjs seed
//   DEMO_PASSWORD=<senha das contas> node supabase/seed/demo-data.mjs all --yes
//   node supabase/seed/demo-data.mjs plan            (só mostra o que seria criado; não toca na base)
//
// Comandos: plan | seed | wipe | all.  `wipe` e `all` APAGAM TODOS OS USUÁRIOS e dados (cascata:
// perfis, médicos, pacientes, agenda, consultas, eventos) e por isso exigem --yes.
// Datas são relativas a hoje (consultas 3 a ~14 dias à frente); rode `all --yes` de novo para renovar.
//
// A senha vem de DEMO_PASSWORD (nunca fica no código). Mínimo de 6 caracteres.

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
const args = process.argv.slice(2);
const MODE = args.find((a) => !a.startsWith("--")) ?? "plan"; // plan | seed | wipe | all
const CONFIRMED = args.includes("--yes");
const PASSWORD = process.env.DEMO_PASSWORD ?? "";
if (!["plan", "seed", "wipe", "all"].includes(MODE)) {
  throw new Error(`Comando desconhecido "${MODE}". Use: plan | seed | wipe | all`);
}
if ((MODE === "wipe" || MODE === "all") && !CONFIRMED) {
  throw new Error("`wipe`/`all` apagam TODOS os usuários e dados da base hospedada. Repita com --yes para confirmar.");
}
if ((MODE === "seed" || MODE === "all") && PASSWORD.length < 6) {
  throw new Error("Defina DEMO_PASSWORD (mínimo 6 caracteres) para criar as contas.");
}
if (!URL_BASE || !ANON) throw new Error("SUPABASE_URL / SUPABASE_ANON_KEY ausentes em local.properties");

function sql(query) {
  query = query.replace(/\s+/g, " ");
  let r;
  for (let attempt = 1; attempt <= 3; attempt++) {
    r = spawnSync("npx", ["supabase", "db", "query", "--linked", `"${query.replace(/"/g, '\\"')}"`], {
      cwd: root, encoding: "utf8", shell: true,
    });
    if (r.status === 0) break;
    Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, 1500);
  }
  if (r.status !== 0) throw new Error(`db query failed: ${r.stdout}\n${r.stderr}`);
  const out = r.stdout;
  const json = JSON.parse(out.slice(out.indexOf("{"), out.lastIndexOf("}") + 1));
  return json.rows ?? [];
}

async function post(path, body, headers = {}) {
  const res = await fetch(`${URL_BASE}${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json", apikey: ANON, Authorization: `Bearer ${ANON}`, ...headers },
    body: JSON.stringify(body),
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`${path} -> ${res.status} ${text}`);
  return text ? JSON.parse(text) : null;
}

const DOCTORS = [
  { key: "helena", name: "Dra. Helena Duarte", email: "helena.duarte@example.com", specialty: "Cardiologia", insurances: ["Unimed", "Amil"], location: "Pinheiros, São Paulo - SP", days: [1, 3, 5], start: "08:00", end: "18:00" },
  { key: "ricardo", name: "Dr. Ricardo Alves", email: "ricardo.alves@example.com", specialty: "Clínico Geral", insurances: ["Unimed", "Bradesco", "Particular"], location: "Centro, São Paulo - SP", days: [1, 2, 3, 4, 5], start: "08:00", end: "17:00" },
  { key: "marina", name: "Dra. Marina Fontes", email: "marina.fontes@example.com", specialty: "Dermatologia", insurances: ["Amil", "Particular"], location: "Moema, São Paulo - SP", days: [2, 4], start: "09:00", end: "17:00" },
  { key: "paulo", name: "Dr. Paulo Menezes", email: "paulo.menezes@example.com", specialty: "Ortopedia", insurances: ["Bradesco", "Unimed"], location: "Cambuí, Campinas - SP", days: [1, 3, 5], start: "08:00", end: "16:00" },
  { key: "beatriz", name: "Dra. Beatriz Nogueira", email: "beatriz.nogueira@example.com", specialty: "Pediatria", insurances: ["Unimed", "Amil", "Bradesco"], location: "Vila Mariana, São Paulo - SP", days: [1, 2, 3, 4, 5], start: "08:00", end: "12:00" },
  { key: "camila", name: "Dra. Camila Rezende", email: "camila.rezende@example.com", specialty: "Ginecologia", insurances: ["Amil", "Bradesco", "Particular"], location: "Gonzaga, Santos - SP", days: [2, 4, 6], start: "08:00", end: "13:00" },
  { key: "fernando", name: "Dr. Fernando Castro", email: "fernando.castro@example.com", specialty: "Cardiologia", insurances: ["Particular", "Bradesco"], location: "Itaim Bibi, São Paulo - SP", days: [2, 4], start: "10:00", end: "18:00" },
  { key: "larissa", name: "Dra. Larissa Prado", email: "larissa.prado@example.com", specialty: "Dermatologia", insurances: ["Unimed"], location: "Centro, Campinas - SP", days: [1, 3, 5], start: "08:00", end: "15:00" },
];

const PATIENTS = [
  { key: "mariana", name: "Mariana Costa", email: "mariana.costa@example.com" },
  { key: "joao", name: "João Pedro Martins", email: "joao.martins@example.com" },
  { key: "ana", name: "Ana Beatriz Lima", email: "ana.lima@example.com" },
  { key: "carlos", name: "Carlos Eduardo Souza", email: "carlos.souza@example.com" },
  { key: "fernanda", name: "Fernanda Ribeiro", email: "fernanda.ribeiro@example.com" },
  { key: "rafael", name: "Rafael Teixeira", email: "rafael.teixeira@example.com" },
];

// patient, doctor, occurrence (n-th upcoming matching weekday, >=3 days ahead), slot index, insurance
const APPOINTMENTS = [
  ["mariana", "helena", 0, 2, "Unimed"],
  ["mariana", "beatriz", 1, 1, "Unimed"],
  ["mariana", "ricardo", 2, 4, "Unimed"],
  ["joao", "paulo", 0, 1, "Bradesco"],
  ["joao", "ricardo", 1, 0, "Bradesco"],
  ["ana", "marina", 0, 3, "Amil"],
  ["ana", "camila", 0, 2, "Amil"],
  ["ana", "ricardo", 0, 5, "Particular"],
  ["carlos", "fernando", 0, 1, "Particular"],
  ["carlos", "ricardo", 0, 2, "Particular"],
  ["fernanda", "helena", 1, 0, "Amil"],
  ["fernanda", "camila", 1, 1, "Amil"],
  ["rafael", "larissa", 0, 1, "Unimed"],
  ["rafael", "beatriz", 0, 3, "Unimed"],
];

function spDateParts(offsetDays) {
  const d = new Date(Date.now() - 3 * 3600 * 1000 + offsetDays * 86400000);
  return { y: d.getUTCFullYear(), m: d.getUTCMonth() + 1, d: d.getUTCDate(), dow: d.getUTCDay() };
}
const pad = (n) => String(n).padStart(2, "0");
function nthDate(weekdays, n) {
  let found = 0;
  for (let off = 3; off <= 40; off++) {
    const p = spDateParts(off);
    if (weekdays.includes(p.dow)) {
      if (found === n) return p;
      found++;
    }
  }
  throw new Error("no date found");
}
function slotTimes(start, end) {
  const [sh, sm] = start.split(":").map(Number);
  const [eh, em] = end.split(":").map(Number);
  const out = [];
  for (let t = sh * 60 + sm; t + 30 <= eh * 60 + em; t += 45) out.push(`${pad(Math.floor(t / 60))}:${pad(t % 60)}`);
  return out;
}

/** Consultas previstas (a partir de hoje), já com data/hora e ISO em America/Sao_Paulo. */
function agendaPlanejada() {
  return APPOINTMENTS.map(([pk, dk, occ, slot, insurance]) => {
    const doc = DOCTORS.find((x) => x.key === dk);
    const pat = PATIENTS.find((x) => x.key === pk);
    const date = nthDate(doc.days, occ);
    const hora = slotTimes(doc.start, doc.end)[slot];
    if (!hora) throw new Error(`Horário inexistente na grade de ${doc.name} (índice ${slot})`);
    return {
      pat, doc, insurance, hora,
      rotulo: `${pad(date.d)}/${pad(date.m)} ${hora}`,
      iso: `${date.y}-${pad(date.m)}-${pad(date.d)}T${hora}:00-03:00`,
    };
  });
}

if (MODE === "plan") {
  console.log(`Médicos (${DOCTORS.length}):`);
  DOCTORS.forEach((d) => console.log(`  ${d.email} | ${d.name} | ${d.specialty} | ${d.location} | ${d.insurances.join(", ")}`));
  console.log(`Pacientes (${PATIENTS.length}):`);
  PATIENTS.forEach((p) => console.log(`  ${p.email} | ${p.name}`));
  console.log("Consultas previstas:");
  agendaPlanejada().forEach((a) => console.log(`  ${a.pat.name} -> ${a.doc.name} | ${a.rotulo} | ${a.insurance}`));
  console.log("Nada foi alterado na base (comando plan).");
}

if (MODE === "wipe" || MODE === "all") {
  console.log("Limpando a base...");
  sql("delete from auth.users");
  const c = sql("select (select count(*) from auth.users) users, (select count(*) from public.profiles) profiles, (select count(*) from public.doctors) doctors, (select count(*) from public.patients) patients, (select count(*) from public.appointments) appointments, (select count(*) from public.booked_slots) booked_slots, (select count(*) from public.notification_events) events, (select count(*) from public.doctor_schedules) schedules")[0];
  console.log("Após limpeza:", JSON.stringify(c));
  if (Object.values(c).some((v) => Number(v) !== 0)) throw new Error("base não ficou vazia");
}

if (MODE === "seed" || MODE === "all") {
  console.log("Cadastrando médicos...");
  for (const d of DOCTORS) {
    await post("/functions/v1/register-doctor", {
      name: d.name, email: d.email, password: PASSWORD, specialty: d.specialty, insurances: d.insurances, location: d.location,
      schedules: d.days.map((w) => ({ weekday: w, startTime: d.start, endTime: d.end })),
    });
    console.log("  ok", d.name);
  }
  console.log("Cadastrando pacientes...");
  for (const p of PATIENTS) {
    await post("/functions/v1/register-patient", { name: p.name, email: p.email, password: PASSWORD });
    console.log("  ok", p.name);
  }

  const idRows = sql("select d.id, u.email from public.doctors d join auth.users u on u.id = d.id");
  const doctorId = Object.fromEntries(idRows.map((r) => [r.email, r.id]));

  console.log("Agendando consultas...");
  const tokens = {};
  for (const p of PATIENTS) {
    const s = await post("/auth/v1/token?grant_type=password", { email: p.email, password: PASSWORD });
    tokens[p.key] = s.access_token;
  }
  for (const a of agendaPlanejada()) {
    await post("/rest/v1/rpc/book_appointment",
      { p_doctor_id: doctorId[a.doc.email], p_start_time: a.iso, p_insurance: a.insurance },
      { Authorization: `Bearer ${tokens[a.pat.key]}` });
    console.log(`  ok ${a.pat.name} -> ${a.doc.name} | ${a.rotulo} | ${a.insurance}`);
  }

  // Uma consulta a menos de 24h (estado "Bloqueada" no app): amanhã de manhã, direto por SQL,
  // porque book_appointment exige 48h de antecedência.
  const tomorrow = spDateParts(1);
  const marina = DOCTORS.find((x) => x.key === "marina");
  if (marina.days.includes(tomorrow.dow)) {
    const hora = slotTimes(marina.start, marina.end).find((h) => h >= "10:00");
    const iso = `${tomorrow.y}-${pad(tomorrow.m)}-${pad(tomorrow.d)}T${hora}:00-03:00`;
    const ana = PATIENTS.find((x) => x.key === "ana");
    sql(`insert into public.appointments (patient_id, doctor_id, start_time, status, insurance) select pa.id, '${doctorId[marina.email]}', '${iso}'::timestamptz, 'confirmed', 'Amil' from public.patients pa join auth.users u on u.id = pa.id where u.email = '${ana.email}'`);
    console.log(`  ok (a menos de 24h) Ana Beatriz Lima -> Dra. Marina Fontes | ${pad(tomorrow.d)}/${pad(tomorrow.m)} ${hora} | Amil`);
  } else {
    console.log("  amanhã não é dia de atendimento da Dra. Marina; consulta bloqueada não criada");
  }

  // Evita e-mails de lembrete para endereços que não existem.
  sql("update public.appointments set reminder_sent_at = now() where reminder_sent_at is null");
}

if (MODE !== "plan") {
const fin = sql("select (select count(*) from public.doctors) doctors, (select count(*) from public.doctor_schedules) schedules, (select count(*) from public.patients) patients, (select count(*) from public.appointments) appointments, (select count(*) from public.booked_slots) booked_slots");
console.log("Estado final:", JSON.stringify(fin[0]));
}
