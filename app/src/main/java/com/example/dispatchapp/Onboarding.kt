package com.example.dispatchapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.dispatchapp.databinding.ActivityOnboardingBinding

class Onboarding : BaseActivity() {
    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var userPreferences: UserPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityOnboardingBinding.inflate(layoutInflater)

        enableEdgeToEdge()
        setContentView(binding.root)

        userPreferences = UserPreferences(this)
        val role = userPreferences.getUserRole()

        if(role == "organizer"){
            binding.imageBanner.setImageResource(R.drawable.banner_organizer)
            binding.slogan.text = "Find all the talent for your event"
            binding.description.text = "Temukan orang yang tertarik dengan event mu dan cari yang terbaik"
        }

        binding.signupBtn.setOnClickListener {
            startActivity(Intent(this, Register::class.java))
        }

        binding.signinBtn.setOnClickListener {
            startActivity(Intent(this, Login::class.java))
        }

    }
}