package com.example.dispatchapp

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.example.dispatchapp.adapters.ParticipantAdapter
import com.example.dispatchapp.databinding.ActivityParticipantListBinding
import com.example.dispatchapp.models.EventRegistration
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ParticipantListActivity : BaseActivity() {

    private lateinit var binding: ActivityParticipantListBinding
    private lateinit var adapter: ParticipantAdapter
    private var eventId: Long = -1L
    private var eventTitle: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityParticipantListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        eventId = intent.getLongExtra("event_id", -1L)
        eventTitle = intent.getStringExtra("event_title") ?: "Event"

        binding.tvEventTitle.text = eventTitle
        binding.btnBack.setOnClickListener { finish() }

        adapter = ParticipantAdapter(
            onStatusChange = { registration, newStatus ->
                updateParticipantStatus(registration.id, newStatus)
            },
            onCardClick = { registration ->
                showStudentCardDialog(registration)
            }
        )
        binding.rvParticipants.layoutManager = LinearLayoutManager(this)
        binding.rvParticipants.adapter = adapter

        loadParticipants()
    }

    private fun showStudentCardDialog(registration: EventRegistration) {
        val userId = registration.userId
        val student = registration.students
        val studentName = student?.studentName ?: registration.profiles?.username ?: "Peserta"

        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(0, 0, 0, 8)
        }

        val imageView = android.widget.ImageView(this).apply {
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            setPadding(16, 16, 16, 8)
        }

        val cardUrl = SupabaseClient.client.storage["student_cards"].publicUrl("card_$userId.jpg")
        imageView.load(cardUrl) {
            crossfade(true)
            error(R.drawable.ic_profile_placeholder)
        }
        container.addView(imageView)

        val infoText = buildString {
            if (student != null) {
                appendLine("👤  $studentName")
                if (!student.kelas.isNullOrEmpty()) appendLine("🏫  Kelas: ${student.kelas}")
                if (!student.jurusan.isNullOrEmpty()) appendLine("📚  Jurusan: ${student.jurusan}")
                if (!student.prodi.isNullOrEmpty()) appendLine("🎓  Prodi: ${student.prodi}")
                if (student.nis != null) appendLine("🪪  NIS: ${student.nis}")
            } else {
                appendLine("👤  $studentName")
            }
        }.trimEnd()

        val infoView = android.widget.TextView(this).apply {
            text = infoText
            textSize = 13f
            setPadding(32, 8, 32, 8)
            setTextColor(context.getColor(android.R.color.tab_indicator_text))
            lineHeight = (textSize * 1.8f).toInt()
        }
        container.addView(infoView)

        MaterialAlertDialogBuilder(this)
            .setTitle("Kartu Pelajar")
            .setView(container)
            .setPositiveButton("Tutup", null)
            .show()
    }

    private fun loadParticipants() {
        if (eventId == -1L) { finish(); return }
        binding.pbParticipants.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val registrations = SupabaseClient.client.from("event_registrations")
                    .select(columns = Columns.raw("*, profiles(id, username, avatar), students(student_id, nis, kelas, jurusan, prodi, student_name)")) {
                        filter { eq("event_id", eventId) }
                        order("registered_at", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
                    }.decodeList<EventRegistration>()

                withContext(Dispatchers.Main) {
                    binding.pbParticipants.visibility = View.GONE
                    binding.tvParticipantCount.text = "${registrations.size} peserta terdaftar"

                    if (registrations.isEmpty()) {
                        binding.tvEmptyParticipants.visibility = View.VISIBLE
                        binding.rvParticipants.visibility = View.GONE
                    } else {
                        binding.tvEmptyParticipants.visibility = View.GONE
                        binding.rvParticipants.visibility = View.VISIBLE
                        adapter.setParticipants(registrations)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.pbParticipants.visibility = View.GONE
                    Toast.makeText(this@ParticipantListActivity, "Gagal memuat peserta: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateParticipantStatus(registrationId: Long, newStatus: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (newStatus == "rejected") {
                    
                    SupabaseClient.client.from("event_registrations").delete {
                        filter { eq("id", registrationId) }
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ParticipantListActivity, "Peserta ditolak dan dihapus", Toast.LENGTH_SHORT).show()
                        loadParticipants()
                    }
                } else {
                    SupabaseClient.client.from("event_registrations").update(
                        mapOf("status" to newStatus)
                    ) { filter { eq("id", registrationId) } }
                    val statusText = "dikonfirmasi"
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ParticipantListActivity, "Status peserta berhasil $statusText", Toast.LENGTH_SHORT).show()
                        loadParticipants()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ParticipantListActivity, "Gagal update status: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
