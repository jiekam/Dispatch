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

                val userInterests = if (studentId != -1) {
                    SupabaseClient.client.from("user_interest")
                        .select { filter { eq("id_student", studentId) } }
                        .decodeList<com.example.dispatchapp.models.UserInterest>()
                } else {
                    emptyList()
                }
                
                val preSelectedIds = userInterests.map { it.interestId }.toSet()
                selectedInterestIds.addAll(preSelectedIds)

                withContext(Dispatchers.Main) {
                    binding.pbLoading.visibility = View.GONE
                    binding.chipGroupInterest.visibility = View.VISIBLE
                    populateChips(interests, preSelectedIds)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.pbLoading.visibility = View.GONE
                    Toast.makeText(this@SelectInterestActivity, "Gagal memuat minat: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun populateChips(interests: List<Interest>, preSelectedIds: Set<Long>) {
        binding.chipGroupInterest.removeAllViews()

        interests.forEach { interest ->
            val chip = Chip(this).apply {
                text = interest.interest
                isCheckable = true
                isCheckedIconVisible = false
                isChecked = preSelectedIds.contains(interest.id)
                
                val states = arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf(-android.R.attr.state_checked)
                )
                val bgColors = intArrayOf(
                    android.graphics.Color.parseColor("#10B981"), // Green when checked
                    android.graphics.Color.TRANSPARENT
                )
                chipBackgroundColor = android.content.res.ColorStateList(states, bgColors)
                
                val strokeColors = intArrayOf(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.DKGRAY
                )
                chipStrokeColor = android.content.res.ColorStateList(states, strokeColors)
                
                val textColors = intArrayOf(
                    android.graphics.Color.WHITE,
                    currentTextColor
                )
                setTextColor(android.content.res.ColorStateList(states, textColors))

                chipStrokeWidth = 2f
                textSize = 14f
                textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER

                // Use robust Chip padding for symmetry
                chipStartPadding = 32f
                chipEndPadding = 32f

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
