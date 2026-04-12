package com.example.dispatchapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

abstract class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme()
        super.onCreate(savedInstanceState)
        hideStatusBar()
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

    private fun hideStatusBar() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}