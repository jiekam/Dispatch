package com.example.dispatchapp

import android.content.Context
import io.github.jan.supabase.SupabaseClient as CoreClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.auth.SettingsSessionManager
import com.russhwolf.settings.SharedPreferencesSettings
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

object SupabaseClient {
    private const val SUPABASE_URL = "https://kvtpnyyotpbvrxzjnbgx.supabase.co"
    private const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imt2dHBueXlvdHBidnJ4empuYmd4Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzE1NDgxMjQsImV4cCI6MjA4NzEyNDEyNH0.m8HUKUGFOZuRXl4BFste6DuIsQ2SbBCruPwWCX095IU"

    lateinit var client: CoreClient

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("supabase_session", Context.MODE_PRIVATE)
        val settings = SharedPreferencesSettings(prefs)
        
        client = createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_KEY
        ) {
            install(Postgrest)
            install(Auth) {
                sessionManager = SettingsSessionManager(settings)
                alwaysAutoRefresh = true
            }
            install(Realtime)
            install(Storage)
        }
    }
}
