package com.example.dispatchapp

import android.app.Application


class DispatchApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SupabaseClient.init(this)
    }
}
