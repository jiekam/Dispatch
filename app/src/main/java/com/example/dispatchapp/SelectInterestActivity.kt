package com.example.dispatchapp

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.dispatchapp.databinding.ActivitySelectInterestBinding
import com.example.dispatchapp.models.Interest
import com.google.android.material.chip.Chip
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SelectInterestActivity : BaseActivity() {

    private lateinit var binding: ActivitySelectInterestBinding
    private val selectedInterestIds = mutableSetOf<Long>()
    private var studentId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySelectInterestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // student_id (integer) passed from VerifyStudentActivity
        studentId = intent.getIntExtra("student_id", -1)

        setupClickListeners()
        loadInterests()
    }

    private fun setupClickListeners() {
        binding.btnSaveInterest.setOnClickListener { saveInterests() }
        binding.tvSkip.setOnClickListener { navigateToMain() }
    }

    // ========== LOAD INTERESTS FROM DB ==========

    private fun loadInterests() {
        binding.pbLoading.visibility = View.VISIBLE
        binding.chipGroupInterest.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val interests = SupabaseClient.client.from("interest")
                    .select()
                    .decodeList<Interest>()

                withContext(Dispatchers.Main) {
                    binding.pbLoading.visibility = View.GONE
                    binding.chipGroupInterest.visibility = View.VISIBLE
                    populateChips(interests)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.pbLoading.visibility = View.GONE
                    Toast.makeText(this@SelectInterestActivity, "Gagal memuat minat: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun populateChips(interests: List<Interest>) {
        binding.chipGroupInterest.removeAllViews()

        interests.forEach { interest ->
            val chip = Chip(this).apply {
                text = interest.interest
                isCheckable = true
                isCheckedIconVisible = true
                chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.TRANSPARENT
                )
                setChipStrokeColorResource(android.R.color.darker_gray)
                chipStrokeWidth = 2f
                textSize = 14f
                setPadding(8, 4, 8, 4)

                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedInterestIds.add(interest.id)
                    } else {
                        selectedInterestIds.remove(interest.id)
                    }
                }
            }
            binding.chipGroupInterest.addView(chip)
        }
    }

    // ========== SAVE INTERESTS ==========

    private fun saveInterests() {
        if (selectedInterestIds.isEmpty()) {
            Toast.makeText(this, "Pilih minimal 1 minat bakat", Toast.LENGTH_SHORT).show()
            return
        }

        if (studentId == -1) {
            Toast.makeText(this, "Data siswa tidak ditemukan", Toast.LENGTH_SHORT).show()
            navigateToMain()
            return
        }

        binding.btnSaveInterest.isEnabled = false
        binding.btnSaveInterest.text = "Menyimpan..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Delete existing interests first (for update case)
                SupabaseClient.client.from("user_interest")
                    .delete {
                        filter { eq("id_student", studentId) }
                    }

                // Insert new interests one by one
                selectedInterestIds.forEach { interestId ->
                    SupabaseClient.client.from("user_interest")
                        .insert(
                            buildJsonObject {
                                put("interest_id", interestId)
                                put("id_student", studentId)
                            }
                        )
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@SelectInterestActivity,
                        "Minat bakat disimpan! Selamat bereksplorasi 🚀",
                        Toast.LENGTH_LONG
                    ).show()
                    navigateToMain()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.btnSaveInterest.isEnabled = true
                    binding.btnSaveInterest.text = "Mulai Eksplorasi! 🚀"
                    Toast.makeText(this@SelectInterestActivity, "Gagal menyimpan: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }

    override fun onBackPressed() {
        // Prevent going back — must choose or skip
        navigateToMain()
    }
}
