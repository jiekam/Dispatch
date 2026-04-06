package com.example.dispatchapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.example.dispatchapp.databinding.ActivityLoginBinding
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Login : BaseActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)
        
        binding.loginBtn.setOnClickListener {
            loginUser()
        }
        
        binding.goToSignUp.setOnClickListener {
            startActivity(Intent(this, Register::class.java))
            finish()
        }
    }

    private fun loginUser() {
        val email = binding.mailInp.text.toString().trim()
        val password = binding.pwInp.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Email dan Password harus diisi", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Proses Login ke Supabase
                SupabaseClient.client.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }

                // Get session and fetch profile
                val session = SupabaseClient.client.auth.currentSessionOrNull()
                val uuid = session?.user?.id

                if (uuid != null) {
                    try {
                        val profile = SupabaseClient.client.from("profiles")
                            .select {
                                filter {
                                    eq("id", uuid)
                                }
                            }.decodeSingleOrNull<com.example.dispatchapp.models.Profile>()

                        if (profile != null) {
                            val prefs = UserPreferences(this@Login)
                            profile.role?.let { prefs.saveUserRole(it) }
                            profile.username?.let { prefs.saveUserName(it) }
                            prefs.saveUserEmail(email)
                        }
                    } catch (e: Exception) {
                        Log.e("LOGIN_PROFILE_FETCH", "Gagal ambil profil: ${e.message}")
                    }
                }

                // Jika berhasil, arahkan ke SplashActivity untuk ambil data Student ID
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@Login, "Login Berhasil!", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this@Login, SplashActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("LOGIN_ERROR", e.message.toString())
                    Toast.makeText(this@Login, "Login Gagal: Email atau Password salah", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}