package com.example.dispatchapp.fragments

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil.ImageLoader
import coil.load
import com.example.dispatchapp.Login
import com.example.dispatchapp.R
import com.example.dispatchapp.SupabaseClient
import com.example.dispatchapp.UserPreferences
import com.example.dispatchapp.databinding.FragmentOrganizerProfileBinding
import com.example.dispatchapp.utils.UsernamePolicy
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.yalantis.ucrop.UCrop
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

class OrganizerProfileFragment : Fragment() {

    private var _binding: FragmentOrganizerProfileBinding? = null
    private val binding get() = _binding!!
    private var pendingCropIsAvatar: Boolean? = null

    private val pickAvatarLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { startCrop(it, true) }
    }

    private val pickBannerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { startCrop(it, false) }
    }

    private val cropImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val isAvatar = pendingCropIsAvatar
        pendingCropIsAvatar = null

        if (isAvatar == null) return@registerForActivityResult

        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val outputUri = data?.let { UCrop.getOutput(it) }
            if (outputUri != null) {
                uploadImage(outputUri, isAvatar)
            }
        } else if (result.resultCode == UCrop.RESULT_ERROR) {
            val errorMessage = result.data?.let { UCrop.getError(it)?.message } ?: "Gagal crop gambar"
            Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOrganizerProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = UserPreferences(requireContext())
        binding.switchDarkMode.setOnCheckedChangeListener(null)
        binding.switchDarkMode.isChecked = prefs.isDarkMode()
        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.saveDarkMode(isChecked)
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        binding.ivProfilePicture.setOnClickListener { pickAvatarLauncher.launch("image/*") }
        binding.ivProfileBanner.setOnClickListener { pickBannerLauncher.launch("image/*") }

        binding.btnEditOrgName.setOnClickListener { showEditNameDialog(prefs) }

        binding.itemAboutApp.setOnClickListener {
            showAboutAppDialog()
        }

        binding.itemResetPassword.setOnClickListener {
            showResetPasswordDialog()
        }

        loadProfile()

        binding.btnLogout.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                try {
                    SupabaseClient.client.auth.signOut()
                    withContext(Dispatchers.Main) {
                        UserPreferences(requireContext()).clearAll()
                        val intent = Intent(requireContext(), Login::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Gagal logout: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun loadProfile() {
        val uuid = SupabaseClient.client.auth.currentSessionOrNull()?.user?.id ?: return
        val prefs = UserPreferences(requireContext())

        val orgName = prefs.getOrgName() ?: prefs.getUserName() ?: "Organizer"
        binding.tvOrganizerName.text = orgName

        loadProfileImages()

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val eventCount = SupabaseClient.client.from("events").select {
                    filter { eq("user_id", uuid) }
                }.decodeList<JsonObject>().size

                val followerCount = SupabaseClient.client.from("follows").select {
                    filter { eq("following_id", uuid) }
                }.decodeList<JsonObject>().size

                val registrationCount = SupabaseClient.client.from("event_registrations").select(
                    columns = io.github.jan.supabase.postgrest.query.Columns.raw("id, events!inner(user_id)")
                ) {
                    filter { eq("events.user_id", uuid) }
                }.decodeList<JsonObject>().size

                withContext(Dispatchers.Main) {
                    binding.tvEventsCount.text = eventCount.toString()
                    binding.tvParticipantsCount.text = registrationCount.toString()
                    binding.tvRating.text = followerCount.toString()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.tvEventsCount.text = "—"
                    binding.tvParticipantsCount.text = "—"
                    binding.tvRating.text = "—"
                }
            }
        }
    }

    private fun loadProfileImages() {
        val uuid = SupabaseClient.client.auth.currentSessionOrNull()?.user?.id ?: return

        val imageLoader = ImageLoader.Builder(requireContext())
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(coil.decode.ImageDecoderDecoder.Factory())
                } else {
                    add(coil.decode.GifDecoder.Factory())
                }
            }
            .build()

        val bucket = SupabaseClient.client.storage["user_profiles"]
        val timestamp = System.currentTimeMillis()

        val avatarUrl = bucket.publicUrl("avatar_$uuid") + "?t=$timestamp"
        binding.ivProfilePicture.load(avatarUrl, imageLoader) {
            crossfade(true)
            memoryCacheKey("avatar_$uuid")
            diskCacheKey("avatar_$uuid")
            error(R.drawable.ic_profile_placeholder)
            size(300, 300)
        }

        val bannerUrl = bucket.publicUrl("banner_$uuid") + "?t=$timestamp"
        binding.ivProfileBanner.load(bannerUrl, imageLoader) {
            crossfade(true)
            memoryCacheKey("banner_$uuid")
            diskCacheKey("banner_$uuid")
            size(800, 400)
        }
    }

    private fun startCrop(sourceUri: Uri, isAvatar: Boolean) {
        val mimeType = requireContext().contentResolver.getType(sourceUri) ?: ""
        if (mimeType.equals("image/gif", ignoreCase = true)) {
            Toast.makeText(requireContext(), "GIF tidak mendukung crop, langsung diupload", Toast.LENGTH_SHORT).show()
            uploadImage(sourceUri, isAvatar)
            return
        }

        val destinationUri = Uri.fromFile(
            File(requireContext().cacheDir, "crop_${UUID.randomUUID()}.jpg")
        )

        val options = UCrop.Options().apply {
            setCompressionFormat(Bitmap.CompressFormat.JPEG)
            setCompressionQuality(90)
            setFreeStyleCropEnabled(false)
        }

        val cropIntent = if (isAvatar) {
            UCrop.of(sourceUri, destinationUri)
                .withAspectRatio(1f, 1f)
                .withMaxResultSize(1080, 1080)
                .withOptions(options)
                .getIntent(requireContext())
        } else {
            UCrop.of(sourceUri, destinationUri)
                .withAspectRatio(16f, 9f)
                .withMaxResultSize(1920, 1080)
                .withOptions(options)
                .getIntent(requireContext())
        }

        pendingCropIsAvatar = isAvatar
        cropImageLauncher.launch(cropIntent)
    }

    private fun uploadImage(uri: Uri, isAvatar: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val uuid = SupabaseClient.client.auth.currentSessionOrNull()?.user?.id ?: return@launch
                val fileName = if (isAvatar) "avatar_$uuid" else "banner_$uuid"

                val contentResolver = requireContext().contentResolver
                val mimeType = contentResolver.getType(uri) ?: "image/jpeg"

                val byteArray = if (mimeType == "image/gif") {
                    contentResolver.openInputStream(uri)?.readBytes() ?: return@launch
                } else {
                    val inputStream = contentResolver.openInputStream(uri) ?: return@launch
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    val outputStream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                    outputStream.toByteArray()
                }

                if (byteArray.size > 50 * 1024 * 1024) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Ukuran file terlalu besar (Max 50MB)", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val bucket = SupabaseClient.client.storage["user_profiles"]
                bucket.upload(path = fileName, data = byteArray) { upsert = true }

                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Berhasil upload foto!", Toast.LENGTH_SHORT).show()
                    loadProfileImages()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Gagal upload: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showEditNameDialog(prefs: UserPreferences) {
        val inputLayout = TextInputLayout(requireContext()).apply {
            hint = "Nama Organisasi"
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            setPadding(48, 32, 48, 0)
        }
        val editText = TextInputEditText(inputLayout.context).apply {
            setText(prefs.getOrgName() ?: prefs.getUserName())
            setSelection(text?.length ?: 0)
        }
        inputLayout.addView(editText)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Ubah Nama Organisasi")
            .setMessage("Nama ini akan ditampilkan di profil organizer Anda.")
            .setView(inputLayout)
            .setPositiveButton("Simpan", null)
            .setNegativeButton("Batal", null)
            .create()

        dialog.show()

        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val newName = editText.text.toString().trim()
            val usernameError = UsernamePolicy.validate(newName)
            if (usernameError != null) {
                inputLayout.error = usernameError
                return@setOnClickListener
            }

            inputLayout.error = null
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).isEnabled = false
            dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).isEnabled = false

            val uuid = SupabaseClient.client.auth.currentSessionOrNull()?.user?.id
            if (uuid == null) {
                Toast.makeText(requireContext(), "Sesi tidak valid", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                return@setOnClickListener
            }

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val updatedProfile = SupabaseClient.client.from("profiles")
                        .update(
                            mapOf("organization_name" to newName, "username" to newName)
                        ) {
                            filter { eq("id", uuid) }
                            select(columns = Columns.list("username, organization_name, role"))
                        }
                        .decodeSingleOrNull<com.example.dispatchapp.models.Profile>()

                    if (updatedProfile == null) {
                        throw IllegalStateException("Nama organisasi gagal tersimpan di server")
                    }

                    prefs.saveOrgName(newName)
                    prefs.saveUserName(newName)

                    withContext(Dispatchers.Main) {
                        dialog.dismiss()
                        binding.tvOrganizerName.text = newName
                        Toast.makeText(requireContext(), "Nama organisasi diperbarui", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).isEnabled = true
                        dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).isEnabled = true
                        inputLayout.error = "Gagal: ${e.message}"
                    }
                }
            }
        }
    }

    private fun showAboutAppDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Tentang Aplikasi")
            .setMessage(
                "DispatchApp\nVersi 1.0.0 Beta\n\n" +
                "Platform terpadu untuk menemukan event dan kegiatan yang menawarkan sertifikat, " +
                "membantu siswa mengembangkan portofolio melalui fitur showcase, " +
                "serta mempermudah organizer dalam mengelola dan mempromosikan event mereka.\n\n" +
                "Fitur untuk Organizer:\n" +
                "• Buat dan kelola event dengan mudah\n" +
                "• Pantau daftar peserta\n" +
                "• Kirim notifikasi ke peserta\n" +
                "• Analisis performa event\n\n" +
                "⚠️ Aplikasi masih dalam tahap Beta. Kami terus melakukan perbaikan dan pengembangan fitur. " +
                "Jika menemukan bug atau memiliki saran, silakan hubungi tim kami.\n\n" +
                "© 2026 DispatchApp Team"
            )
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showResetPasswordDialog() {
        val inputLayout = TextInputLayout(requireContext()).apply {
            hint = "Email"
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            setPadding(48, 32, 48, 0)
        }
        val editText = TextInputEditText(inputLayout.context).apply {
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        inputLayout.addView(editText)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Ganti Password")
            .setMessage("Masukkan email Anda untuk menerima link ganti password.")
            .setView(inputLayout)
            .setPositiveButton("Kirim", null)
            .setNegativeButton("Batal", null)
            .create()

        dialog.show()

        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val email = editText.text.toString().trim()
            if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                inputLayout.error = "Email tidak valid"
                return@setOnClickListener
            }

            inputLayout.error = null
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).isEnabled = false
            dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).isEnabled = false

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                try {
                    SupabaseClient.client.auth.resetPasswordForEmail(email)
                    withContext(Dispatchers.Main) {
                        dialog.dismiss()
                        Toast.makeText(requireContext(), "Link ganti password telah dikirim ke email Anda", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).isEnabled = true
                        dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).isEnabled = true
                        inputLayout.error = "Gagal: ${e.message}"
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
