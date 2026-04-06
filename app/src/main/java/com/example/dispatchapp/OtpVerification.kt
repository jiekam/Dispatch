package com.example.dispatchapp

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.dispatchapp.SupabaseClient
import com.example.dispatchapp.databinding.ActivityOtpVerificationBinding
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OtpVerification : BaseActivity() {

    private lateinit var binding: ActivityOtpVerificationBinding
    private var email: String = ""
    private var name: String = ""
    private var timer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOtpVerificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        email = intent.getStringExtra("EMAIL") ?: ""
        name = intent.getStringExtra("NAME") ?: ""

        binding.tvSubtitle.text = "Kode OTP telah dikirim ke $email"

        binding.btnVerify.setOnClickListener {
            val otp = binding.etOtp.text.toString().trim()
            if (otp.length == 8) {
                verifyOtp(otp)
            } else {
                binding.etOtp.error = "Masukkan 8 digit kode"
            }
        }

        binding.tvResend.setOnClickListener { resendOtp() }
        binding.btnBack.setOnClickListener { finish() }

        startTimer()
    }

    private fun verifyOtp(otp: String) {
        binding.btnVerify.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                SupabaseClient.client.auth.verifyEmailOtp(
                    type = OtpType.Email.SIGNUP,
                    email = email,
                    token = otp
                )

                val user = SupabaseClient.client.auth.currentSessionOrNull()?.user ?: throw Exception("Sesi tidak ditemukan")
                val userId = user.id

                val userPrefs = UserPreferences(this@OtpVerification)
                val rawRole = userPrefs.getUserRole() ?: "student"
                val finalRole = rawRole.lowercase().trim()

                SupabaseClient.client.postgrest["profiles"]
                    .upsert(mapOf(
                        "id" to userId,
                        "username" to name,
                        "email" to email,
                        "role" to finalRole
                    ))

                userPrefs.saveUserEmail(email)
                userPrefs.saveUserName(name)
                userPrefs.saveUserRole(finalRole)

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@OtpVerification, "Verifikasi Berhasil!", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this@OtpVerification, MainActivity::class.java)
                    startActivity(intent)
                    finishAffinity()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    binding.btnVerify.isEnabled = true
                    val errorMsg = e.localizedMessage ?: "Gagal verifikasi"
                    binding.etOtp.error = errorMsg
                    Toast.makeText(this@OtpVerification, errorMsg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun resendOtp() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                SupabaseClient.client.auth.resendEmail(
                    type = OtpType.Email.SIGNUP,
                    email = this@OtpVerification.email
                )
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@OtpVerification, "OTP dikirim ulang", Toast.LENGTH_SHORT).show()
                    startTimer()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@OtpVerification, "Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun startTimer() {
        timer?.cancel()
        binding.tvResend.isEnabled = false
        binding.tvResend.alpha = 0.5f

        timer = object : CountDownTimer(120000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                binding.tvTimer.text = String.format("00:%02d", seconds)
            }

            override fun onFinish() {
                binding.tvTimer.text = "00:00"
                binding.tvResend.isEnabled = true
                binding.tvResend.alpha = 1.0f
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
    }
}