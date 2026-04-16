package com.example.dispatchapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.example.dispatchapp.adapters.ParticipantAdapter
import com.example.dispatchapp.databinding.ActivityOrganizerEventDetailBinding
import com.example.dispatchapp.models.Event
import com.example.dispatchapp.models.EventRegistration
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

class OrganizerEventDetailActivity : BaseActivity() {

    private lateinit var binding: ActivityOrganizerEventDetailBinding
    private lateinit var participantAdapter: ParticipantAdapter
    private var eventId: Long = -1L
    private var currentEvent: Event? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOrganizerEventDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        eventId = intent.getLongExtra("event_id", -1L)
        if (eventId == -1L) { finish(); return }

        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        participantAdapter = ParticipantAdapter(
            onStatusChange = { reg, newStatus ->
                updateParticipantStatus(reg.id, newStatus)
            },
            onCardClick = { reg ->
                showStudentCardDialog(reg)
            }
        )
        binding.rvParticipants.layoutManager = LinearLayoutManager(this)
        binding.rvParticipants.adapter = participantAdapter
        binding.rvParticipants.isNestedScrollingEnabled = false

        binding.btnViewAllParticipants.setOnClickListener {
            val intent = Intent(this, ParticipantListActivity::class.java)
            intent.putExtra("event_id", eventId)
            intent.putExtra("event_title", currentEvent?.title ?: "")
            startActivity(intent)
        }

        binding.btnEditEvent.setOnClickListener {
            val intent = Intent(this, EditEventActivity::class.java)
            intent.putExtra("event_id", eventId)
            startActivity(intent)
        }

        binding.btnDeleteEvent.setOnClickListener { confirmDeleteEvent() }

        loadEventDetail()
        loadParticipants()
        loadEventStats()
    }

    private fun loadEventDetail() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val event = SupabaseClient.client.from("events")
                    .select(columns = Columns.raw("*, profiles(username, role, account_status, organization_name)")) {
                        filter { eq("id", eventId) }
                    }.decodeSingle<Event>()

                withContext(Dispatchers.Main) {
                    currentEvent = event
                    binding.tvEventTitle.text = event.title
                    binding.tvEventDescription.text = event.desc
                    binding.tvOrganizerName.text = event.profiles?.username ?: "Organizer"

                    if (!event.bannerUrl.isNullOrEmpty()) {
                        binding.ivEventImage.load(event.bannerUrl) { crossfade(true) }
                    }

                    val organizerId = event.userId
                    if (!organizerId.isNullOrEmpty()) {
                        val avatarUrl = SupabaseClient.client.storage["user_profiles"]
                            .publicUrl("avatar_$organizerId") + "?t=${System.currentTimeMillis()}"
                        binding.ivOrganizerAvatar.load(avatarUrl) {
                            crossfade(true)
                            error(R.drawable.ic_profile_placeholder)
                        }
                    }

                    val dateText = listOfNotNull(event.startDate, event.endDate)
                        .joinToString(" – ")
                        .ifEmpty { "–" }
                    binding.infoDate.setValue(dateText)
                    binding.infoLocation.setValue(event.location ?: "–")

                    val statusText = when {
                        event.endDate != null && event.startDate != null -> {
                            val now = System.currentTimeMillis()
                            val start = parseDate(event.startDate)
                            val end = parseDate(event.endDate)
                            when {
                                start != null && start > now -> "Mendatang"
                                end != null && end < now -> "Selesai"
                                else -> "Berlangsung"
                            }
                        }
                        else -> "Mendatang"
                    }
                    binding.chipEventStatus.text = statusText
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@OrganizerEventDetailActivity, "Gagal memuat detail: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
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
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val registrations = SupabaseClient.client.from("event_registrations")
                    .select(columns = Columns.raw("*, profiles(id, username, avatar), students(student_id, nis, kelas, jurusan, prodi, student_name)")) {
                        filter { eq("event_id", eventId) }
                        order("registered_at", Order.DESCENDING)
                        limit(5)
                    }.decodeList<EventRegistration>()

                val totalCount = SupabaseClient.client.from("event_registrations")
                    .select(columns = Columns.raw("id")) { filter { eq("event_id", eventId) } }
                    .decodeList<JsonObject>().size

                withContext(Dispatchers.Main) {
                    binding.tvParticipantCount.text = "$totalCount"
                    participantAdapter.setParticipants(registrations)
                    binding.btnViewAllParticipants.visibility = if (totalCount > 5) View.VISIBLE else View.GONE
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@OrganizerEventDetailActivity, "Gagal memuat peserta", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadEventStats() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                
                val registrationsCount = SupabaseClient.client.from("event_registrations")
                    .select(columns = Columns.raw("id")) { 
                        filter { eq("event_id", eventId) } 
                    }
                    .decodeList<JsonObject>().size

                val wishlistsCount = SupabaseClient.client.from("wishlists")
                    .select(columns = Columns.raw("id")) { 
                        filter { eq("event_id", eventId) } 
                    }
                    .decodeList<JsonObject>().size

                withContext(Dispatchers.Main) {
                    binding.tvParticipantCount.text = "$registrationsCount Mendaftar"
                    binding.tvWishlistsCount.text = "$wishlistsCount Menyukai"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.tvParticipantCount.text = "0 Mendaftar"
                    binding.tvWishlistsCount.text = "0 Menyukai"
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
                        Toast.makeText(this@OrganizerEventDetailActivity, "Peserta ditolak dan dihapus", Toast.LENGTH_SHORT).show()
                        loadParticipants()
                        loadEventStats() 
                    }
                } else {
                    SupabaseClient.client.from("event_registrations").update(
                        mapOf("status" to newStatus)
                    ) { filter { eq("id", registrationId) } }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@OrganizerEventDetailActivity, "Status diperbarui", Toast.LENGTH_SHORT).show()
                        loadParticipants()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@OrganizerEventDetailActivity, "Gagal update: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun confirmDeleteEvent() {
        AlertDialog.Builder(this)
            .setTitle("Hapus Event")
            .setMessage("Yakin ingin menghapus event ini? Aksi ini tidak dapat dibatalkan.")
            .setPositiveButton("Hapus") { _, _ -> deleteEvent() }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun deleteEvent() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                SupabaseClient.client.from("events").delete {
                    filter { eq("id", eventId) }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@OrganizerEventDetailActivity, "Event berhasil dihapus", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@OrganizerEventDetailActivity, "Gagal hapus: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun parseDate(dateStr: String?): Long? {
        if (dateStr.isNullOrBlank()) return null
        return try {
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).parse(dateStr)?.time
        } catch (_: Exception) { null }
    }

    override fun onResume() {
        super.onResume()
        loadEventStats() 
    }
}
