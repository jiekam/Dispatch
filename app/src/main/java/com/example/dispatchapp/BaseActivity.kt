package com.example.dispatchapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

abstract class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme()
        super.onCreate(savedInstanceState)
    }

    private fun applyTheme() {
        val userPref = UserPreferences(this)
        val role = userPref.getUserRole()

        when (role) {
            "student" -> setTheme(R.style.Theme_DispatchApp_Student)
            "organizer" -> setTheme(R.style.Theme_DispatchApp_Organizer)
            else -> setTheme(R.style.Theme_DispatchApp)
        }
    }
}