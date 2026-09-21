// register-patient — Story 1.2 (FR3)
//
// The ONLY place `role = 'patient'` is ever assigned (AD-6). Mirrors register-doctor:
// creates the Auth user, then calls complete_registration() (single transaction); if that
// fails, the Auth user is deleted so no orphan is left. The e-mail stored in `patients` is
// read server-side from auth.users by complete_registration (AD-1), never passed in.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

interface RegisterPatientPayload {
  name: string;
  email: string;
  password: string;
}

function jsonResponse(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

// Same shape check as CadastroPacienteViewModel.kt / register-doctor.
const EMAIL_PATTERN = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;

function validatePayload(payload: Partial<RegisterPatientPayload>): string | null {
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
  return null;
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") {
    return jsonResponse({ error: "INVALID: método não suportado" }, 405);
  }

  let payload: Partial<RegisterPatientPayload>;
  try {
    payload = await req.json();
  } catch {
    return jsonResponse({ error: "INVALID: corpo da requisição inválido" }, 400);
  }

  if (payload === null || typeof payload !== "object" || Array.isArray(payload)) {
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

  const admin = createClient(supabaseUrl, serviceRoleKey, {
    auth: { autoRefreshToken: false, persistSession: false },
  });

  const { data: createdUser, error: createUserError } = await admin.auth.admin.createUser({
    email: payload.email!.trim(),
    password: payload.password!,
    email_confirm: true,
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
    p_role: "patient",
    p_name: payload.name,
  });

  if (rpcError) {
    // Compensating transaction (AD-6): never leave an orphaned Auth user with no profile.
    const { error: deleteError } = await admin.auth.admin.deleteUser(userId);
    if (deleteError) {
      console.error(
        `register-patient: compensating deleteUser failed for orphaned auth user ${userId} — manual cleanup needed`,
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
