package com.example.dispatchapp

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.dispatchapp.databinding.ActivityCreateEventBinding
import com.example.dispatchapp.models.Event
import com.example.dispatchapp.models.Interest
import com.google.android.material.chip.Chip
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Calendar
import java.util.UUID

class EditEventActivity : BaseActivity() {

    private lateinit var binding: ActivityCreateEventBinding
    private var selectedBannerUri: Uri? = null
    private val PICK_IMAGE_REQUEST = 101
    private val calendar = Calendar.getInstance()
    private var eventId: Long = -1L
    private var currentEvent: Event? = null
    private var existingBannerUrl: String? = null
    private val selectedInterestIds = mutableSetOf<Long>()
    private var selectedContactType: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateEventBinding.inflate(layoutInflater)
        setContentView(binding.root)

        eventId = intent.getLongExtra("event_id", -1L)
        if (eventId == -1L) { finish(); return }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Edit Event"
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnPickBanner.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
            startActivityForResult(intent, PICK_IMAGE_REQUEST)
        }

        binding.etStartDate.setOnClickListener { pickDate { binding.etStartDate.setText(it) } }
        binding.etEndDate.setOnClickListener { pickDate { binding.etEndDate.setText(it) } }

        binding.rgRegistrationType.setOnCheckedChangeListener { _, checkedId ->
            binding.tilExternalUrl.visibility = if (checkedId == R.id.rb_external) View.VISIBLE else View.GONE
            binding.tilMaxParticipants.visibility = if (checkedId == R.id.rb_internal) View.VISIBLE else View.GONE
        }

        setupContactUI()

        binding.btnSubmit.text = "Simpan Perubahan"
        binding.btnSubmit.setOnClickListener { updateEvent() }

        loadEventData()
    }

    private fun setupContactUI() {
        binding.rgContactType.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rb_email -> {
                    selectedContactType = "email"
                    binding.etContactValue.isEnabled = true
                    binding.etContactValue.hint = "contoh@email.com"
                }
                R.id.rb_whatsapp -> {
                    selectedContactType = "whatsapp"
                    binding.etContactValue.isEnabled = true
                    binding.etContactValue.hint = "628123456789"
                }
                R.id.rb_discord -> {
                    selectedContactType = "discord"
                    binding.etContactValue.isEnabled = true
                    binding.etContactValue.hint = "https://discord.gg/invite-code or username#1234"
                }
                else -> {
                    selectedContactType = null
                    binding.etContactValue.isEnabled = false
                    binding.etContactValue.hint = "Masukkan kontak"
                }
            }
            binding.tvContactError.visibility = View.GONE
        }

        binding.etContactValue.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.tvContactError.visibility = View.GONE
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun loadEventData() {
        binding.pbCreate.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val event = SupabaseClient.client.from("events")
                    .select(columns = Columns.raw("*")) {
                        filter { eq("id", eventId) }
                    }.decodeSingle<Event>()

                val existingTags = SupabaseClient.client.from("event_tag")
                    .select(columns = Columns.raw("interest_id")) {
                        filter { eq("event_id", eventId) }
                    }.data

                val existingInterestIds = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                    .decodeFromString<List<kotlinx.serialization.json.JsonObject>>(existingTags)
                    .mapNotNull { it["interest_id"]?.let { v ->
                        (v as? kotlinx.serialization.json.JsonPrimitive)?.content?.toLongOrNull()
                    }}
                    .toSet()

                val allInterests = SupabaseClient.client.from("interest")
                    .select().decodeList<Interest>()

                withContext(Dispatchers.Main) {
                    currentEvent = event
                    existingBannerUrl = event.bannerUrl

                    binding.etTitle.setText(event.title)
                    binding.etDesc.setText(event.desc)
                    binding.etLocation.setText(event.location)
                    binding.etStartDate.setText(event.startDate)
                    binding.etEndDate.setText(event.endDate)

                    if (!event.bannerUrl.isNullOrEmpty()) {
                        binding.ivBannerPreview.load(event.bannerUrl) { crossfade(true) }
                    }

                    when (event.registrationType) {
                        "internal" -> {
                            binding.rbInternal.isChecked = true
                            binding.etMaxParticipants.setText(event.maxParticipants?.toString() ?: "")
                        }
                        "external" -> {
                            binding.rbExternal.isChecked = true
                            binding.etExternalUrl.setText(event.externalRegistrationUrl ?: "")
                        }
                        else -> binding.rbNone.isChecked = true
                    }

                    if (!event.contactType.isNullOrEmpty() && !event.contactValue.isNullOrEmpty()) {
                        selectedContactType = event.contactType
                        when (event.contactType) {
                            "email" -> binding.rbEmail.isChecked = true
                            "whatsapp" -> binding.rbWhatsapp.isChecked = true
                            "discord" -> binding.rbDiscord.isChecked = true
                        }
                        binding.etContactValue.setText(event.contactValue)
                    }

                    selectedInterestIds.clear()
                    selectedInterestIds.addAll(existingInterestIds)
                    binding.chipGroupInterest.removeAllViews()
                    allInterests.forEach { interest ->
                        val chip = Chip(this@EditEventActivity).apply {
                            text = interest.interest
                            isCheckable = true
                            isChecked = interest.id in existingInterestIds
                            tag = interest.id
                            setOnCheckedChangeListener { _, isChecked ->
                                if (isChecked) selectedInterestIds.add(interest.id)
                                else selectedInterestIds.remove(interest.id)
                                if (selectedInterestIds.isNotEmpty()) {
                                    binding.tvInterestError.visibility = View.GONE
                                }
                            }
                        }
                        binding.chipGroupInterest.addView(chip)
                    }

                    binding.pbCreate.visibility = View.GONE
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.pbCreate.visibility = View.GONE
                    Toast.makeText(this@EditEventActivity, "Gagal memuat data event: ${e.message}", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun pickDate(onPicked: (String) -> Unit) {
        val y = calendar.get(Calendar.YEAR)
        val m = calendar.get(Calendar.MONTH)
        val d = calendar.get(Calendar.DAY_OF_MONTH)
        DatePickerDialog(this, { _, year, month, day ->
            onPicked(String.format("%04d-%02d-%02d", year, month + 1, day))
        }, y, m, d).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK) {
            selectedBannerUri = data?.data
            binding.ivBannerPreview.load(selectedBannerUri) { crossfade(true) }
        }
    }

    private fun updateEvent() {
        val title = binding.etTitle.text.toString().trim()
        val desc = binding.etDesc.text.toString().trim()
        val location = binding.etLocation.text.toString().trim()
        val startDate = binding.etStartDate.text.toString().trim()
        val endDate = binding.etEndDate.text.toString().trim()

        if (title.isEmpty() || desc.isEmpty() || location.isEmpty() || startDate.isEmpty() || endDate.isEmpty()) {
            Toast.makeText(this, "Lengkapi semua field yang diperlukan", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedInterestIds.isEmpty()) {
            binding.tvInterestError.visibility = View.VISIBLE
            Toast.makeText(this, "Pilih minimal 1 interest", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedContactType == null) {
            Toast.makeText(this, "Pilih metode kontak", Toast.LENGTH_SHORT).show()
            return
        }

        val contactValue = binding.etContactValue.text.toString().trim()
        if (contactValue.isEmpty()) {
            Toast.makeText(this, "Masukkan informasi kontak", Toast.LENGTH_SHORT).show()
            return
        }

        val validationResult = com.example.dispatchapp.utils.ContactValidator.validate(selectedContactType!!, contactValue)
        if (validationResult is com.example.dispatchapp.utils.ContactValidator.ValidationResult.Invalid) {
            binding.tvContactError.text = validationResult.message
            binding.tvContactError.visibility = View.VISIBLE
            return
        }

        val registrationType = when (binding.rgRegistrationType.checkedRadioButtonId) {
            R.id.rb_internal -> "internal"
            R.id.rb_external -> "external"
            else -> "none"
        }

        val externalUrl = binding.etExternalUrl.text.toString().trim().takeIf { registrationType == "external" }
        val maxParticipants = binding.etMaxParticipants.text.toString().trim().toIntOrNull().takeIf { registrationType == "internal" }

        if (registrationType == "external" && externalUrl.isNullOrBlank()) {
            Toast.makeText(this, "Masukkan URL pendaftaran eksternal", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnSubmit.isEnabled = false
        binding.pbCreate.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                var bannerUrl: String? = existingBannerUrl

                if (selectedBannerUri != null) {
                    val imageId = UUID.randomUUID().toString()
                    val bytes = contentResolver.openInputStream(selectedBannerUri!!)?.readBytes() ?: ByteArray(0)
                    SupabaseClient.client.storage["event_banners"].upload("banner_$imageId.jpg", bytes) { upsert = true }
                    bannerUrl = SupabaseClient.client.storage["event_banners"].publicUrl("banner_$imageId.jpg")
                }

                val eventData = buildJsonObject {
                    put("title", title)
                    put("desc", desc)
                    put("location", location)
                    put("start_date", startDate)
                    put("end_date", endDate)
                    put("registration_type", registrationType)
                    put("contact_type", selectedContactType!!)
                    put("contact_value", contactValue)
                    if (bannerUrl != null) put("banner_url", bannerUrl)
                    if (externalUrl != null) put("external_registration_url", externalUrl)
                    if (maxParticipants != null) put("max_participants", maxParticipants)
                }

                SupabaseClient.client.from("events").update(eventData) {
                    filter { eq("id", eventId) }
                }

                SupabaseClient.client.from("event_tag").delete {
                    filter { eq("event_id", eventId) }
                }
                selectedInterestIds.forEach { interestId ->
                    SupabaseClient.client.from("event_tag").insert(
                        buildJsonObject {
                            put("event_id", eventId)
                            put("interest_id", interestId)
                        }
                    )
                }

                withContext(Dispatchers.Main) {
                    binding.pbCreate.visibility = View.GONE
                    Toast.makeText(this@EditEventActivity, "Event berhasil diperbarui!", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.btnSubmit.isEnabled = true
                    binding.pbCreate.visibility = View.GONE
                    Toast.makeText(this@EditEventActivity, "Gagal memperbarui event: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
