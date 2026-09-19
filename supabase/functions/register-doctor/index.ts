// register-doctor — Story 1.1 (FR1, FR2)
//
// The ONLY place `role = 'doctor'` is ever assigned (AD-6). Creates the Supabase Auth user
// (Admin API, service_role) and then calls complete_registration() — a single Postgres
// transaction that inserts profiles + doctors + doctor_schedules. If that transaction fails,
// the just-created Auth user is deleted (compensating action) so no orphaned Auth user is
// ever left without a profile.
//
// Naming/content convention: identifiers in English (AD-7); ESPECIALIDADES/CONVENIOS values
// are the exact Portuguese strings from the PRD Glossário — this is the Edge Function's own
// copy of the same fixed lists the Android app keeps in domain/model/{Especialidade,Convenio}.kt.
// If the lists ever change, both places must change together (Deno can't import Kotlin code).

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const ESPECIALIDADES = [
  "Cardiologia",
  "Dermatologia",
  "Pediatria",
  "Ortopedia",
  "Clínico Geral",
  "Ginecologia",
] as const;

const CONVENIOS = ["Unimed", "Amil", "Bradesco", "Particular"] as const;

interface ScheduleInput {
  weekday: number;
  startTime: string;
  endTime: string;
}

interface RegisterDoctorPayload {
  name: string;
  email: string;
  password: string;
  specialty: string;
  insurances: string[];
  schedules: ScheduleInput[];
}

function jsonResponse(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

const TIME_PATTERN = /^([01]\d|2[0-3]):[0-5]\d$/;
// Basic shape check only (not full RFC 5322) — mirrors the same check in
// CadastroMedicoViewModel.kt so a malformed address is rejected before hitting the Auth
// Admin API, where it would otherwise surface only as a generic UNEXPECTED failure.
const EMAIL_PATTERN = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;

/** Mirrors the I/O matrix row "Especialidade/Convênio fora da lista fixa -> INVALID:". */
function validatePayload(payload: Partial<RegisterDoctorPayload>): string | null {
  if (typeof payload.name !== "string" || payload.name.trim().length === 0) {
    return "INVALID: nome é obrigatório";
  }
  if (typeof payload.email !== "string" || payload.email.trim().length === 0) {
    return "INVALID: e-mail é obrigatório";
  }
  if (!EMAIL_PATTERN.test(payload.email.trim())) {
    return "INVALID: e-mail inválido";
  }
  if (typeof payload.password !== "string" || payload.password.length < 6) {
    return "INVALID: senha deve ter ao menos 6 caracteres";
  }
  if (typeof payload.specialty !== "string" || !ESPECIALIDADES.includes(payload.specialty as typeof ESPECIALIDADES[number])) {
    return "INVALID: especialidade inválida";
  }
  if (!Array.isArray(payload.insurances) || payload.insurances.length === 0) {
    return "INVALID: selecione ao menos um convênio";
  }
  if (payload.insurances.some((insurance) => !CONVENIOS.includes(insurance as typeof CONVENIOS[number]))) {
    return "INVALID: convênio inválido";
  }
  if (new Set(payload.insurances).size !== payload.insurances.length) {
    return "INVALID: convênio duplicado";
  }
  if (!Array.isArray(payload.schedules) || payload.schedules.length === 0) {
    return "INVALID: selecione ao menos um dia de atendimento";
  }
  // The Android client can't produce a duplicate weekday (it builds this array from a Set),
  // but this function is the actual trust boundary (AD-1/AD-6) — a direct API call must not
  // be able to insert two doctor_schedules rows for the same day.
  if (new Set(payload.schedules.map((schedule) => schedule?.weekday)).size !== payload.schedules.length) {
    return "INVALID: dia de atendimento duplicado";
  }
  for (const schedule of payload.schedules) {
    const validWeekday = typeof schedule?.weekday === "number" && schedule.weekday >= 0 && schedule.weekday <= 6;
    const validTimes =
      typeof schedule?.startTime === "string" &&
      typeof schedule?.endTime === "string" &&
      TIME_PATTERN.test(schedule.startTime) &&
      TIME_PATTERN.test(schedule.endTime) &&
      schedule.startTime < schedule.endTime;
    if (!validWeekday || !validTimes) {
      return "INVALID: horário de atendimento inválido";
    }
  }
  return null;
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") {
    return jsonResponse({ error: "INVALID: método não suportado" }, 405);
  }

  let payload: Partial<RegisterDoctorPayload>;
  try {
    payload = await req.json();
  } catch {
    return jsonResponse({ error: "INVALID: corpo da requisição inválido" }, 400);
  }

  const validationError = validatePayload(payload);
  if (validationError) {
    return jsonResponse({ error: validationError }, 400);
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!supabaseUrl || !serviceRoleKey) {
    return jsonResponse({ error: "UNEXPECTED: configuração do servidor ausente" }, 500);
  }

  // service_role key never reaches the app (AD-5's rule for send-reminders applies here too,
  // by the same reasoning) — it lives only in this Function's environment.
  const admin = createClient(supabaseUrl, serviceRoleKey, {
    auth: { autoRefreshToken: false, persistSession: false },
  });

  const { data: createdUser, error: createUserError } = await admin.auth.admin.createUser({
    email: payload.email!,
    password: payload.password!,
    email_confirm: true, // no approval/validation step for a doctor (FR1) — authenticated immediately
  });

  if (createUserError || !createdUser?.user) {
    const message = (createUserError?.message ?? "").toLowerCase();
    if (message.includes("already") || message.includes("registered") || message.includes("exists")) {
      return jsonResponse({ error: "CONFLICT: já existe uma conta com este e-mail" }, 409);
    }
    return jsonResponse({ error: "UNEXPECTED: não foi possível criar a conta" }, 500);
  }

  const userId = createdUser.user.id;

  const { error: rpcError } = await admin.rpc("complete_registration", {
    p_user_id: userId,
    p_role: "doctor",
    p_name: payload.name,
    p_specialty: payload.specialty,
    p_insurances: payload.insurances,
    p_schedules: payload.schedules!.map((schedule) => ({
      weekday: schedule.weekday,
      start_time: schedule.startTime,
      end_time: schedule.endTime,
    })),
  });

  if (rpcError) {
    // Compensating transaction (AD-6): never leave an orphaned Auth user with no profile.
    // If the delete itself fails, we can't silently pretend AD-6's guarantee held — log it so
    // there's at least a record for manual cleanup, since nothing else will ever know.
    const { error: deleteError } = await admin.auth.admin.deleteUser(userId);
    if (deleteError) {
      console.error(
        `register-doctor: compensating deleteUser failed for orphaned auth user ${userId} — manual cleanup needed`,
        deleteError,
      );
    }

    const message = rpcError.message ?? "";
    if (message.startsWith("CONFLICT:") || message.startsWith("INVALID:") || message.startsWith("FORBIDDEN:")) {
      return jsonResponse({ error: message }, 400);
    }
    return jsonResponse({ error: "UNEXPECTED: não foi possível concluir o cadastro" }, 500);
  }

  return jsonResponse({ userId }, 201);
});
