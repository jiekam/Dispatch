package com.example.dispatchapp.fragments

import android.content.DialogInterface
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
import com.example.dispatchapp.R
import com.example.dispatchapp.SupabaseClient
import com.example.dispatchapp.UserPreferences
import com.example.dispatchapp.RoleSelection
import com.example.dispatchapp.VerifyStudentActivity
import android.app.Activity
import com.example.dispatchapp.databinding.DialogChangePasswordBinding
import com.example.dispatchapp.databinding.DialogResetPasswordBinding
import com.example.dispatchapp.databinding.FragmentProfileBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserUpdateBuilder
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import android.content.Intent
import com.example.dispatchapp.SelectInterestActivity
import io.github.jan.supabase.postgrest.postgrest
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val pickAvatarLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { uploadImage(it, true) }
    }

    private val pickBannerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { uploadImage(it, false) }
    }

    private val verifyStudentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Refresh profile data after successful verification
            refreshProfileData()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = UserPreferences(requireContext())

        // Populate user info
        binding.tvProfileName.text = prefs.getUserName() ?: "—"
        binding.tvProfileEmail.text = prefs.getUserEmail() ?: "—"
        binding.chipRole.text = when (prefs.getUserRole()) {
            "student" -> "Siswa"
            "organizer" -> "Organizer"
            "student_informan" -> "Informan"
            else -> prefs.getUserRole() ?: "—"
        }

        // Check if student is verified (has student data)
        val studentId = prefs.getStudentId()
        val isVerified = !studentId.isNullOrEmpty()

        if (isVerified) {
            binding.cardNotVerified.visibility = View.GONE
            binding.cardInfoAkun.visibility = View.VISIBLE
            binding.tvNis.text = prefs.getUserNis() ?: "—"
            binding.tvKelas.text = prefs.getUserKelas() ?: "—"
            binding.tvJurusan.text = prefs.getUserJurusan() ?: "—"
            binding.tvProdi.text = prefs.getUserProdi() ?: "—"

            // Show verified badge
            binding.ivVerifiedBadge.visibility = View.VISIBLE

            // Show student name from card
            val studentName = prefs.getStudentName()
            if (!studentName.isNullOrEmpty()) {
                binding.tvStudentName.visibility = View.VISIBLE
                binding.tvStudentName.text = "📋 $studentName (Nama di Kartu Pelajar)"
            }
            // Show interest section and load interests from DB
            binding.tvInterestSection.visibility = View.VISIBLE
            binding.cardInterest.visibility = View.VISIBLE
            loadAndDisplayInterests(studentId!!.toInt())
        } else {
            binding.cardNotVerified.visibility = View.VISIBLE
            binding.cardInfoAkun.visibility = View.GONE
            binding.ivVerifiedBadge.visibility = View.GONE
            binding.tvStudentName.visibility = View.GONE
            binding.tvInterestSection.visibility = View.GONE
            binding.cardInterest.visibility = View.GONE
            binding.btnVerifyStudent.setOnClickListener {
                val intent = Intent(requireContext(), VerifyStudentActivity::class.java)
                verifyStudentLauncher.launch(intent)
            }
        }

        // Edit Profile Name button
        binding.btnEditName.setOnClickListener { showEditNameDialog(prefs) }

        // Avatar & Banner click
        binding.ivProfileAvatar.setOnClickListener { pickAvatarLauncher.launch("image/*") }
        binding.ivProfileBanner.setOnClickListener { pickBannerLauncher.launch("image/*") }

        loadProfileImages()

        // Dark Mode Switch — set initial state without triggering listener
        binding.switchDarkMode.setOnCheckedChangeListener(null)
        binding.switchDarkMode.isChecked = prefs.isDarkMode()
        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.saveDarkMode(isChecked)
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        // Settings items
        binding.itemChangePassword.setOnClickListener { showChangePasswordDialog() }
        binding.itemAboutApp.setOnClickListener { showAboutDialog() }
        binding.itemResetPassword.setOnClickListener { showResetPasswordDialog() }

        setupLogout()
    }

    // =========================================================
    //  DIALOG: Ganti Password
    // =========================================================
    private fun showChangePasswordDialog() {
        val dialogView = DialogChangePasswordBinding.inflate(layoutInflater)
        val prefs = UserPreferences(requireContext())
        val email = prefs.getUserEmail() ?: ""

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView.root)
            .setPositiveButton("Simpan", null) // set null first to override dismiss
            .setNegativeButton("Batal", null)
            .create()

        dialog.show()

        // Override positive button to prevent auto-dismiss on error
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val oldPw = dialogView.etOldPassword.text.toString().trim()
            val newPw = dialogView.etNewPassword.text.toString().trim()
            val confirmPw = dialogView.etConfirmPassword.text.toString().trim()

            // Validate
            if (oldPw.isEmpty()) { dialogView.tilOldPassword.error = "Masukkan password lama"; return@setOnClickListener }
            if (newPw.isEmpty()) { dialogView.tilNewPassword.error = "Masukkan password baru"; return@setOnClickListener }
            if (newPw.length < 6) { dialogView.tilNewPassword.error = "Password minimal 6 karakter"; return@setOnClickListener }
            if (oldPw == newPw) { dialogView.tilNewPassword.error = "Password baru tidak boleh sama dengan password lama"; return@setOnClickListener }
            if (newPw != confirmPw) { dialogView.tilConfirmPassword.error = "Password tidak cocok"; return@setOnClickListener }

            // Clear errors
            dialogView.tilOldPassword.error = null
            dialogView.tilNewPassword.error = null
            dialogView.tilConfirmPassword.error = null

            dialogView.pbChangePw.visibility = View.VISIBLE
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled = false
            dialog.getButton(DialogInterface.BUTTON_NEGATIVE).isEnabled = false

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                try {
                    // Step 1: Re-authenticate with old password
                    SupabaseClient.client.auth.signInWith(Email) {
                        this.email = email
                        this.password = oldPw
                    }

                    // Step 2: Update to new password
                    SupabaseClient.client.auth.updateUser {
                        password = newPw
                    }

                    withContext(Dispatchers.Main) {
                        dialogView.pbChangePw.visibility = View.GONE
                        dialog.dismiss()
                        Toast.makeText(requireContext(), "Password berhasil diubah!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        dialogView.pbChangePw.visibility = View.GONE
                        dialog.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled = true
                        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).isEnabled = true
                        
                        val errMsg = e.message ?: ""
                        if (errMsg.contains("different from the old password", ignoreCase = true) || errMsg.contains("same password", ignoreCase = true)) {
                            dialogView.tilNewPassword.error = "Password baru tidak boleh sama dengan password lama"
                        } else if (errMsg.contains("invalid", ignoreCase = true) || errMsg.contains("credentials", ignoreCase = true)) {
                            dialogView.tilOldPassword.error = "Password lama salah"
                        } else {
                            dialogView.tilOldPassword.error = "Gagal: $errMsg"
                        }
                    }
                }
            }
        }

        // "Lupa password?" link in the dialog triggers reset flow
        dialogView.tvForgotPassword.setOnClickListener {
            dialog.dismiss()
            showResetPasswordDialog()
        }
    }

    // =========================================================
    //  DIALOG: Lupa / Reset Password via Email OTP
    // =========================================================
    private fun showResetPasswordDialog() {
        val dialogView = DialogResetPasswordBinding.inflate(layoutInflater)
        val prefs = UserPreferences(requireContext())
        val userEmail = prefs.getUserEmail() ?: ""

        // Pre-fill email field
        dialogView.etResetEmail.setText(userEmail)

        var isStep2 = false

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView.root)
            .setPositiveButton("Kirim OTP", null)
            .setNegativeButton("Batal", null)
            .create()

        dialog.show()

        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            if (!isStep2) {
                // Step 1: Send OTP
                val email = dialogView.etResetEmail.text.toString().trim()
                if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    dialogView.tilResetEmail.error = "Masukkan email yang valid"
                    return@setOnClickListener
                }
                dialogView.tilResetEmail.error = null
                dialogView.pbResetPw.visibility = View.VISIBLE
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        SupabaseClient.client.auth.resetPasswordForEmail(email)

                        withContext(Dispatchers.Main) {
                            dialogView.pbResetPw.visibility = View.GONE
                            // Switch to step 2
                            isStep2 = true
                            dialogView.layoutStep1.visibility = View.GONE
                            dialogView.layoutStep2.visibility = View.VISIBLE
                            dialogView.tvResetSubtitle.text = "Kode OTP dikirim ke $email. Cek juga folder Spam."
                            dialog.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled = true
                            dialog.getButton(DialogInterface.BUTTON_POSITIVE).text = "Reset Password"
                            Toast.makeText(requireContext(), "OTP dikirim ke $email", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("RESET_PW", "Error: ${e.message}")
                        withContext(Dispatchers.Main) {
                            dialogView.pbResetPw.visibility = View.GONE
                            dialog.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled = true
                            dialogView.tilResetEmail.error = "Gagal kirim OTP: ${e.message}"
                        }
                    }
                }
            } else {
                // Step 2: Verify OTP + update password
                val emailForReset = dialogView.etResetEmail.text.toString().trim()
                val otp = dialogView.etOtpCode.text.toString().trim()
                val newPw = dialogView.etResetNewPw.text.toString().trim()
                val confirmPw = dialogView.etResetConfirmPw.text.toString().trim()

                if (otp.isEmpty()) { dialogView.tilOtpCode.error = "Masukkan kode OTP"; return@setOnClickListener }
                if (newPw.isEmpty()) { dialogView.tilResetNewPw.error = "Masukkan password baru"; return@setOnClickListener }
                if (newPw.length < 6) { dialogView.tilResetNewPw.error = "Password minimal 6 karakter"; return@setOnClickListener }
                if (newPw != confirmPw) { dialogView.tilResetConfirmPw.error = "Password tidak cocok"; return@setOnClickListener }

                dialogView.tilOtpCode.error = null
                dialogView.tilResetNewPw.error = null
                dialogView.tilResetConfirmPw.error = null
                dialogView.pbResetPw.visibility = View.VISIBLE
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled = false

                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        // Verify OTP for recovery
                        SupabaseClient.client.auth.verifyEmailOtp(
                            type = OtpType.Email.RECOVERY,
                            email = emailForReset,
                            token = otp
                        )

                        // Update password after OTP verified
                        SupabaseClient.client.auth.updateUser {
                            password = newPw
                        }

                        withContext(Dispatchers.Main) {
                            dialogView.pbResetPw.visibility = View.GONE
                            dialog.dismiss()
                            Toast.makeText(requireContext(), "Password berhasil direset!", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            dialogView.pbResetPw.visibility = View.GONE
                            dialog.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled = true
                            val msg = when {
                                e.message?.contains("otp", ignoreCase = true) == true ||
                                e.message?.contains("token", ignoreCase = true) == true ->
                                    "Kode OTP salah atau kadaluarsa"
                                else -> "Gagal: ${e.message}"
                            }
                            dialogView.tilOtpCode.error = msg
                        }
                    }
                }
            }
        }
    }

    // =========================================================
    //  DIALOG: Tentang Aplikasi
    // =========================================================
    private fun showAboutDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Tentang Aplikasi")
            .setMessage(
                "DispatchApp\nVersi 1.0.0\n\n" +
                "Aplikasi manajemen dan informasi kegiatan sekolah.\n\n" +
                "© 2026 DispatchApp Team"
            )
            .setPositiveButton("Tutup", null)
            .show()
    }

    // =========================================================
    //  Load Profile Images
    // =========================================================
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
        binding.ivProfileAvatar.load(avatarUrl, imageLoader) {
            crossfade(true)
            transformations(coil.transform.CircleCropTransformation())
            error(R.drawable.pfp)
        }

        val bannerUrl = bucket.publicUrl("banner_$uuid") + "?t=$timestamp"
        binding.ivProfileBanner.load(bannerUrl, imageLoader) {
            crossfade(true)
        }
    }

    // =========================================================
    //  Upload Image (Avatar / Banner)
    // =========================================================
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

    // =========================================================
    //  Logout
    // =========================================================
    private fun setupLogout() {
        binding.btnLogout.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Keluar")
                .setMessage("Apakah kamu yakin ingin keluar dari akun ini?")
                .setPositiveButton("Keluar") { _, _ ->
                    performLogout()
                }
                .setNegativeButton("Batal", null)
                .show()
        }
    }

    private fun performLogout() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                SupabaseClient.client.auth.signOut()

                val prefs = UserPreferences(requireContext())
                prefs.saveUserRole("")
                prefs.saveUserEmail("")
                prefs.saveUserName("")
                prefs.saveStudentId("")
                prefs.saveUserAvatarUrl("")
                prefs.saveUserNis("")
                prefs.saveUserKelas("")
                prefs.saveUserJurusan("")
                prefs.saveUserProdi("")

                withContext(Dispatchers.Main) {
                    val intent = Intent(requireContext(), RoleSelection::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Gagal keluar: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // =========================================================
    //  Refresh Profile Data (after verify success)
    // =========================================================
    private fun refreshProfileData() {
        val prefs = UserPreferences(requireContext())
        val studentId = prefs.getStudentId()
        val isVerified = !studentId.isNullOrEmpty()

        if (isVerified) {
            binding.cardNotVerified.visibility = View.GONE
            binding.cardInfoAkun.visibility = View.VISIBLE
            binding.tvNis.text = prefs.getUserNis() ?: "—"
            binding.tvKelas.text = prefs.getUserKelas() ?: "—"
            binding.tvJurusan.text = prefs.getUserJurusan() ?: "—"
            binding.tvProdi.text = prefs.getUserProdi() ?: "—"

            // Show verified badge
            binding.ivVerifiedBadge.visibility = View.VISIBLE

            // Show student name
            val studentName = prefs.getStudentName()
            if (!studentName.isNullOrEmpty()) {
                binding.tvStudentName.visibility = View.VISIBLE
                binding.tvStudentName.text = "📋 $studentName (Nama di Kartu Pelajar)"
            }

            // Show interest section
            binding.tvInterestSection.visibility = View.VISIBLE
            binding.cardInterest.visibility = View.VISIBLE
            loadAndDisplayInterests(studentId!!.toInt())
        }
    }

    // =========================================================
    //  Load and Display Interests
    // =========================================================
    private fun loadAndDisplayInterests(studentId: Int) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Fetch user's selected interest_ids
                val userInterests = SupabaseClient.client.from("user_interest")
                    .select { filter { eq("id_student", studentId) } }
                    .decodeList<com.example.dispatchapp.models.UserInterest>()

                // Fetch all available interests
                val allInterests = SupabaseClient.client.from("interest")
                    .select()
                    .decodeList<com.example.dispatchapp.models.Interest>()

                val selectedIds = userInterests.map { it.interestId }.toSet()
                val selectedInterests = allInterests.filter { it.id in selectedIds }

                withContext(Dispatchers.Main) {
                    if (!isAdded) return@withContext
                    binding.chipGroupProfileInterest.removeAllViews()

                    if (selectedInterests.isEmpty()) {
                        binding.tvNoInterest.visibility = View.VISIBLE
                    } else {
                        binding.tvNoInterest.visibility = View.GONE
                        selectedInterests.forEach { interest ->
                            val chip = com.google.android.material.chip.Chip(requireContext()).apply {
                                text = interest.interest
                                isClickable = false
                                isCheckable = false
                                textSize = 13f
                            }
                            binding.chipGroupProfileInterest.addView(chip)
                        }
                    }

                    // Edit interest button
                    binding.btnEditInterest.setOnClickListener {
                        val prefs = UserPreferences(requireContext())
                        val intent = Intent(requireContext(), SelectInterestActivity::class.java)
                        intent.putExtra("student_id", studentId)
                        startActivity(intent)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (!isAdded) return@withContext
                    binding.tvNoInterest.visibility = View.VISIBLE
                    binding.tvNoInterest.text = "Gagal memuat minat"
                }
            }
        }
    }

    // =========================================================
    //  Edit Profile Name (profiles table only, NOT student name)
    // =========================================================
    private fun showEditNameDialog(prefs: UserPreferences) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(android.R.layout.simple_list_item_1, null) // We'll use a custom TextInputLayout

        val inputLayout = com.google.android.material.textfield.TextInputLayout(requireContext()).apply {
            hint = "Nama Profil"
            boxBackgroundMode = com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE
            setPadding(48, 32, 48, 0)
        }
        val editText = com.google.android.material.textfield.TextInputEditText(inputLayout.context).apply {
            setText(prefs.getUserName())
            setSelection(text?.length ?: 0)
        }
        inputLayout.addView(editText)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Ubah Nama Profil")
            .setMessage("Nama ini hanya mengubah nama profil akun, bukan nama di kartu pelajar.")
            .setView(inputLayout)
            .setPositiveButton("Simpan", null)
            .setNegativeButton("Batal", null)
            .create()

        dialog.show()

        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val newName = editText.text.toString().trim()
            if (newName.isEmpty()) {
                inputLayout.error = "Nama tidak boleh kosong"
                return@setOnClickListener
            }
            if (newName.length < 3) {
                inputLayout.error = "Nama minimal 3 karakter"
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
                    // Use proper UPDATE (PATCH) — only send the field we want to change
                    // buildJsonObject ensures only username is patched, NOT null/missing fields
                    SupabaseClient.client.from("profiles")
                        .update(
                            kotlinx.serialization.json.buildJsonObject {
                                put("username", kotlinx.serialization.json.JsonPrimitive(newName))
                            }
                        ) {
                            filter { eq("id", uuid) }
                        }

                    // Update local prefs
                    prefs.saveUserName(newName)

                    withContext(Dispatchers.Main) {
                        dialog.dismiss()
                        binding.tvProfileName.text = newName
                        Toast.makeText(requireContext(), "Nama profil diperbarui", Toast.LENGTH_SHORT).show()
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
