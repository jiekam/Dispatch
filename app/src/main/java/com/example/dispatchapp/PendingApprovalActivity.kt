package com.example.dispatchapp

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.example.dispatchapp.databinding.ActivityPendingApprovalBinding
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PendingApprovalActivity : BaseActivity() {

    private lateinit var binding: ActivityPendingApprovalBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPendingApprovalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnCheckStatus.setOnClickListener { checkStatus() }
        binding.btnLogoutPending.setOnClickListener { logout() }
    }

    private fun checkStatus() {
        binding.btnCheckStatus.isEnabled = false
        binding.btnCheckStatus.text = "Memeriksa..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val uuid = SupabaseClient.client.auth.currentSessionOrNull()?.user?.id
                    ?: throw Exception("Sesi tidak ditemukan")

                val profile = SupabaseClient.client.from("profiles").select {
                    filter { eq("id", uuid) }
                }.decodeSingleOrNull<com.example.dispatchapp.models.Profile>()

                val status = profile?.accountStatus ?: "pending_approval"

                withContext(Dispatchers.Main) {
                    binding.btnCheckStatus.isEnabled = true
                    binding.btnCheckStatus.text = "Cek Status"

                    val prefs = UserPreferences(this@PendingApprovalActivity)
                    prefs.saveAccountStatus(status)

                    when (status) {
                        "active" -> {
                            Toast.makeText(this@PendingApprovalActivity, "Akun disetujui! Selamat datang.", Toast.LENGTH_LONG).show()
                            val intent = Intent(this@PendingApprovalActivity, OrganizerMainActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                        }
                        "pending_approval" -> {
                            Toast.makeText(this@PendingApprovalActivity, "Akun masih dalam proses review.", Toast.LENGTH_SHORT).show()
                        }
                        "rejected" -> {
                            AlertDialog.Builder(this@PendingApprovalActivity)
                                .setTitle("Akun Ditolak")
                                .setMessage("Maaf, akun organizer Anda tidak disetujui. Hubungi admin untuk informasi lebih lanjut.")
                                .setPositiveButton("OK") { _, _ -> }
                                .show()
                        }
                        "suspended" -> {
                            AlertDialog.Builder(this@PendingApprovalActivity)
                                .setTitle("Akun Ditangguhkan")
                                .setMessage("Akun Anda telah ditangguhkan. Hubungi admin.")
                                .setPositiveButton("OK") { _, _ -> }
                                .show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.btnCheckStatus.isEnabled = true
                    binding.btnCheckStatus.text = "Cek Status"
                    Toast.makeText(this@PendingApprovalActivity, "Gagal memeriksa status: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun logout() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                SupabaseClient.client.auth.signOut()
                withContext(Dispatchers.Main) {
                    UserPreferences(this@PendingApprovalActivity).clearAll()
                    val intent = Intent(this@PendingApprovalActivity, Login::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PendingApprovalActivity, "Gagal logout", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
