// send-reminders — Story 3.1 (FR10, NFR2, AD-5)
//
// Called every 5 minutes by a pg_cron job (migration 0008) with the shared secret in `x-cron-secret`.
// Atomically claims due appointments (claim_due_reminders), e-mails each patient through Resend and
// releases the claim of any send that fails so the next run retries it. One failure never stops the
// others. Logs never contain e-mail addresses or other personal data.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

type SendMode = "live" | "simulate-success" | "simulate-failure";

interface DueReminder {
  appointment_id: string;
  patient_email: string;
  patient_name: string;
  doctor_name: string;
  doctor_specialty: string;
  doctor_city: string;
  doctor_neighborhood: string;
  insurance: string;
  start_time: string;
  claimed_at: string;
}

const CLAIM_LIMIT = 100;

function jsonResponse(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

/** Constant-time comparison: hash both sides so lengths match, then XOR every byte. */
async function secretsMatch(provided: string, expected: string): Promise<boolean> {
  const enc = new TextEncoder();
  const [a, b] = await Promise.all([
    crypto.subtle.digest("SHA-256", enc.encode(provided)),
    crypto.subtle.digest("SHA-256", enc.encode(expected)),
  ]);
  const va = new Uint8Array(a);
  const vb = new Uint8Array(b);
  let diff = 0;
  for (let i = 0; i < va.length; i++) diff |= va[i] ^ vb[i];
  return diff === 0;
}

function escapeHtml(s: string): string {
  return s
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

/** Strips control/newline characters so untrusted text (e.g. a doctor's self-chosen name) is safe
 * to interpolate into a single-line field like the e-mail subject. */
function sanitizeForSubject(s: string): string {
  // deno-lint-ignore no-control-regex
  return s.replace(/[\x00-\x1f\x7f]+/g, " ").trim();
}

function formatDateTime(iso: string): string {
  const text = new Intl.DateTimeFormat("pt-BR", {
    timeZone: "America/Sao_Paulo",
    weekday: "long",
    day: "2-digit",
    month: "long",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(new Date(iso));
  return text;
}

function buildEmail(r: DueReminder): { subject: string; html: string; text: string } {
  const when = formatDateTime(r.start_time);
  const where = `${r.doctor_neighborhood}, ${r.doctor_city}`;
  const notice =
    "A partir de agora não é mais possível cancelar ou reagendar esta consulta pelo aplicativo.";
  const subject = `Lembrete: sua consulta com ${sanitizeForSubject(r.doctor_name)} está próxima`;

  const text = [
    `Olá, ${r.patient_name}!`,
    "",
    "Este é um lembrete da sua consulta nas próximas 24 horas.",
    "",
    `Médico: ${r.doctor_name}`,
    `Especialidade: ${r.doctor_specialty}`,
    `Data e horário: ${when}`,
    `Local: ${where}`,
    `Convênio: ${r.insurance}`,
    "",
    notice,
    "",
    "Agenda Médica",
  ].join("\n");

  const html = `<!doctype html>
<html lang="pt-BR">
<body style="font-family: Arial, Helvetica, sans-serif; color: #1f2933;">
  <p>Olá, ${escapeHtml(r.patient_name)}!</p>
  <p>Este é um lembrete da sua consulta nas próximas 24 horas.</p>
  <ul>
    <li><strong>Médico:</strong> ${escapeHtml(r.doctor_name)}</li>
    <li><strong>Especialidade:</strong> ${escapeHtml(r.doctor_specialty)}</li>
    <li><strong>Data e horário:</strong> ${escapeHtml(when)}</li>
    <li><strong>Local:</strong> ${escapeHtml(where)}</li>
    <li><strong>Convênio:</strong> ${escapeHtml(r.insurance)}</li>
  </ul>
  <p>${escapeHtml(notice)}</p>
  <p>Agenda Médica</p>
</body>
</html>`;

  return { subject, html, text };
}

/** Sends one reminder. Throws on any failure (the caller isolates and releases the claim). */
async function sendOne(r: DueReminder, mode: SendMode): Promise<void> {
  if (mode === "simulate-success") return;
  if (mode === "simulate-failure") {
    if (r.patient_email.startsWith("fail-")) throw new Error("simulated failure");
    return;
  }

  const apiKey = Deno.env.get("RESEND_API_KEY");
  if (!apiKey) throw new Error("RESEND_API_KEY not configured");
  const from = Deno.env.get("REMINDER_FROM") ?? "Agenda Médica <onboarding@resend.dev>";
  const { subject, html, text } = buildEmail(r);

  const res = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: { Authorization: `Bearer ${apiKey}`, "Content-Type": "application/json" },
    body: JSON.stringify({ from, to: [r.patient_email], subject, html, text }),
  });
  if (!res.ok) {
    // Status only: the response body may echo the recipient address.
    throw new Error(`Resend responded ${res.status}`);
  }
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") {
    return jsonResponse({ error: "método não suportado" }, 405);
  }

  const expected = Deno.env.get("REMINDER_CRON_SECRET");
  const provided = req.headers.get("x-cron-secret");
  if (!expected || !provided || !(await secretsMatch(provided, expected))) {
    return jsonResponse({ error: "unauthorized" }, 401);
  }

  let mode: SendMode = "live";
  try {
    const body = await req.json();
    if (body && typeof body === "object" && !Array.isArray(body) && "sendMode" in body) {
      if (body.sendMode === "simulate-success" || body.sendMode === "simulate-failure" || body.sendMode === "live") {
        mode = body.sendMode;
      } else {
        return jsonResponse({ error: "sendMode inválido" }, 400);
      }
    }
  } catch {
    // Empty or non-JSON body: defaults apply.
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!supabaseUrl || !serviceRoleKey) {
    return jsonResponse({ error: "configuração do servidor ausente" }, 500);
  }
  const admin = createClient(supabaseUrl, serviceRoleKey, {
    auth: { autoRefreshToken: false, persistSession: false },
  });

  const { data, error } = await admin.rpc("claim_due_reminders", { p_limit: CLAIM_LIMIT });
  if (error) {
    console.error(`send-reminders: claim_due_reminders failed: ${error.message}`);
    return jsonResponse({ error: "falha ao reivindicar lembretes" }, 500);
  }

  const due = (data ?? []) as DueReminder[];
  let sent = 0;
  let failed = 0;

  for (const reminder of due) {
    try {
      await sendOne(reminder, mode);
      sent++;
    } catch (e) {
      failed++;
      const reason = e instanceof Error ? e.message : "unknown error";
      console.error(`send-reminders: failed to send reminder for appointment ${reminder.appointment_id}: ${reason}`);
      try {
        const { error: releaseError } = await admin.rpc("release_reminder", {
          p_id: reminder.appointment_id,
          p_claimed_at: reminder.claimed_at,
        });
        if (releaseError) {
          console.error(`send-reminders: release_reminder failed for appointment ${reminder.appointment_id}: ${releaseError.message}`);
        }
      } catch (releaseErr) {
        const rr = releaseErr instanceof Error ? releaseErr.message : "unknown error";
        console.error(`send-reminders: release_reminder threw for appointment ${reminder.appointment_id}: ${rr}`);
      }
    }
  }

  return jsonResponse({ due: due.length, sent, failed }, 200);
});
