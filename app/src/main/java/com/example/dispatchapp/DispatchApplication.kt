package com.example.dispatchapp

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class DispatchApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SupabaseClient.init(this)

        // Apply dark mode preference on app start
        val prefs = UserPreferences(this)
        val nightMode = if (prefs.isDarkMode()) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }
}
