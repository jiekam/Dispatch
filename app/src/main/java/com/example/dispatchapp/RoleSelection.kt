package com.example.dispatchapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.dispatchapp.databinding.ActivityOnboardingBinding
import com.example.dispatchapp.databinding.ActivityRoleSelectionBinding

class RoleSelection : AppCompatActivity() {

    private lateinit var binding: ActivityRoleSelectionBinding
    private lateinit var userPreferences: UserPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTheme(R.style.Theme_DispatchApp)

        binding = ActivityRoleSelectionBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)

        userPreferences = UserPreferences(this)

        fun saveRole(role: String){
            userPreferences.saveUserRole(role)
        }

        binding.btnSiswa.setOnClickListener {
            saveRole("student")
            startActivity(Intent(this, Onboarding::class.java))
            finish()
        }

        binding.btnPenyelenggara.setOnClickListener {
            saveRole("organizer")
            startActivity(Intent(this, Onboarding::class.java))
            finish()
        }


    }
}