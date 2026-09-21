package com.agendamedica.app.data.remote

import com.agendamedica.app.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

/**
 * The single initialization point for the Supabase client (AD-2). Nothing outside `data/`
 * may construct or hold a [SupabaseClient] directly — screens and ViewModels only ever see
 * a [com.agendamedica.app.data.repository.AuthRepository] / [com.agendamedica.app.data.repository.DoctorRepository].
 *
 * URL/anon key come from BuildConfig, which in turn comes from local.properties (never
 * hardcoded/versioned — see app/build.gradle.kts).
 */
object SupabaseClientProvider {
    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
        ) {
            install(Auth) {
                // Deep link for the password-recovery e-mail (implicit flow, the default).
                scheme = "agendamedica"
                host = "reset-password"
            }
            install(Postgrest)
            install(Realtime)
            install(Functions)
        }
    }
}
