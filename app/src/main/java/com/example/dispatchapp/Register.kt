package com.example.dispatchapp

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.example.dispatchapp.SupabaseClient
import com.example.dispatchapp.databinding.ActivityRegisterBinding
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Register : BaseActivity() {

    private lateinit var binding: ActivityRegisterBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)

        binding.registBtn.setOnClickListener {
            regist()
        }

        binding.goToSignIn.setOnClickListener {
            startActivity(Intent(this, Login::class.java))
            finish()
        }
    }

    private fun regist() {
        val name = binding.nameInp.text.toString().trim()
        val email = binding.mailInp.text.toString().trim()
        val password = binding.pwInp.text.toString().trim()

        if (email.isEmpty() || password.isEmpty() || name.isEmpty()) {
            Toast.makeText(this, "Masukkan semua input yang diperlukan!!", Toast.LENGTH_SHORT).show()
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Email tidak valid!", Toast.LENGTH_SHORT).show()
            return
        }

        if (password.length < 6) {
            Toast.makeText(this, "Password minimal 6 karakter!", Toast.LENGTH_SHORT).show()
            return
        }

        val intentToOtp = Intent(this, OtpVerification::class.java).apply {
            putExtra("EMAIL", email)
            putExtra("PASSWORD", password)
            putExtra("NAME", name)
        }

        binding.registBtn.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // CEK APAKAH EMAIL SUDAH ADA DI TABEL PROFILES
                val existingUser = SupabaseClient.client.postgrest["profiles"]
                    .select {
                        filter {
                            eq("email", email)
                        }
                    }.decodeSingleOrNull<Map<String, String>>()

                if (existingUser != null) {
                    // Jika email ditemukan di tabel profiles
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@Register, "Email sudah terdaftar! Silakan login.", Toast.LENGTH_LONG).show()
                        binding.registBtn.isEnabled = true
                        binding.registBtn.text = "Daftar"
                    }
                    return@launch // Stop proses registrasi di sini
                }

                // JIKA EMAIL BELUM ADA, LANJUT SIGN UP
                withContext(Dispatchers.Main) {
                    binding.registBtn.text = "Mengirim OTP..."
                }

                SupabaseClient.client.auth.signUpWith(Email) {
                    this.email = email
                    this.password = password
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@Register, "Kode OTP telah dikirim ke email", Toast.LENGTH_SHORT).show()
                    val intentToOtp = Intent(this@Register, OtpVerification::class.java).apply {
                        putExtra("EMAIL", email)
                        putExtra("PASSWORD", password)
                        putExtra("NAME", name)
                    }
                    startActivity(intentToOtp)
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    val errorMsg = e.localizedMessage ?: "Terjadi kesalahan"
                    Toast.makeText(this@Register, "Gagal: $errorMsg", Toast.LENGTH_LONG).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    binding.registBtn.isEnabled = true
                    binding.registBtn.text = "Daftar"
                }
            }
        }
        }
    }