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
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.dispatchapp.utils.UsernamePolicy
import com.yalantis.ucrop.UCrop

import com.example.dispatchapp.adapters.MyPostAdapter
import com.example.dispatchapp.models.Post
import com.example.dispatchapp.models.PostLike
import com.example.dispatchapp.models.PostComment
import androidx.recyclerview.widget.LinearLayoutManager

import com.example.dispatchapp.models.SavedPost
import com.google.android.material.tabs.TabLayout

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var myPostsAdapter: MyPostAdapter
    private var isSavedTabSelected = false
    private var isShowingAllPosts = false
    private var latestPosts: List<Post> = emptyList()
    private var latestLikeCounts: Map<Long, Int> = emptyMap()
    private var latestCommentCounts: Map<Long, Int> = emptyMap()
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
        val studentIdInt = studentId?.toIntOrNull()

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
            } else {
                binding.tvStudentName.visibility = View.VISIBLE
                binding.tvStudentName.text = "📋 SPECIAL ACCOUNT"
            }
            // Show interest section and load interests from DB
            binding.tvInterestSection.visibility = View.VISIBLE
            binding.cardInterest.visibility = View.VISIBLE
            if (studentIdInt != null) {
                loadAndDisplayInterests(studentIdInt)
            } else {
                binding.tvNoInterest.visibility = View.VISIBLE
                binding.tvNoInterest.text = "Data minat tidak valid"
            }
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
        
        setupMyPosts()
    }

    override fun onResume() {
        super.onResume()
        syncProfileFromServer()
    }

    private fun syncProfileFromServer() {
        val uuid = SupabaseClient.client.auth.currentSessionOrNull()?.user?.id ?: return
        val prefs = UserPreferences(requireContext())

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val profileResponse = SupabaseClient.client.from("profiles")
                    .select(columns = Columns.list("username, role")) {
                        filter { eq("id", uuid) }
                    }
                    .decodeSingleOrNull<com.example.dispatchapp.models.Profile>()

                if (profileResponse != null) {
                    profileResponse.username?.let { prefs.saveUserName(it) }
                    profileResponse.role?.let { prefs.saveUserRole(it) }

                    withContext(Dispatchers.Main) {
                        if (!isAdded) return@withContext
                        binding.tvProfileName.text = profileResponse.username ?: "—"
                        binding.chipRole.text = when (profileResponse.role) {
                            "student" -> "Siswa"
                            "organizer" -> "Organizer"
                            "student_informan" -> "Informan"
                            else -> profileResponse.role ?: "—"
                        }
                    }
                }
            } catch (_: Exception) {
            }
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
            memoryCacheKey("avatar_$uuid")
            diskCacheKey("avatar_$uuid")
            error(R.drawable.pfp)
            size(300, 300) // downscale resolution for faster loading
        }

        val bannerUrl = bucket.publicUrl("banner_$uuid") + "?t=$timestamp"
        binding.ivProfileBanner.load(bannerUrl, imageLoader) {
            crossfade(true)
            memoryCacheKey("banner_$uuid")
            diskCacheKey("banner_$uuid")
            size(800, 400) // downscale resolution
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
                prefs.clearAll()

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
        val studentIdInt = studentId?.toIntOrNull()

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
            } else {
                binding.tvStudentName.visibility = View.VISIBLE
                binding.tvStudentName.text = "📋 SPECIAL ACCOUNT"
            }

            // Show interest section
            binding.tvInterestSection.visibility = View.VISIBLE
            binding.cardInterest.visibility = View.VISIBLE
            if (studentIdInt != null) {
                loadAndDisplayInterests(studentIdInt)
            } else {
                binding.tvNoInterest.visibility = View.VISIBLE
                binding.tvNoInterest.text = "Data minat tidak valid"
            }
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
                        val displayList = selectedInterests.take(3)
                        val remainingCount = selectedInterests.size - 3

                        displayList.forEach { interest ->
                            val chip = com.google.android.material.chip.Chip(requireContext()).apply {
                                text = interest.interest
                                isClickable = false
                                isCheckable = false
                                textSize = 13f
                            }
                            binding.chipGroupProfileInterest.addView(chip)
                        }

                        if (remainingCount > 0) {
                            val extraChip = com.google.android.material.chip.Chip(requireContext()).apply {
                                text = "+$remainingCount Lainnya"
                                isClickable = true
                                isCheckable = false
                                textSize = 13f
                                chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                                    android.graphics.Color.TRANSPARENT
                                )
                                setChipStrokeColorResource(android.R.color.darker_gray)
                                chipStrokeWidth = 2f
                                
                                setOnClickListener {
                                    binding.chipGroupProfileInterest.removeAllViews()
                                    selectedInterests.forEach { interest ->
                                        val fullChip = com.google.android.material.chip.Chip(requireContext()).apply {
                                            text = interest.interest
                                            isClickable = false
                                            isCheckable = false
                                            textSize = 13f
                                        }
                                        binding.chipGroupProfileInterest.addView(fullChip)
                                    }
                                }
                            }
                            binding.chipGroupProfileInterest.addView(extraChip)
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
                            mapOf("username" to newName)
                        ) {
                            filter { eq("id", uuid) }
                            select(columns = Columns.list("username, role"))
                        }
                        .decodeSingleOrNull<com.example.dispatchapp.models.Profile>()

                    if (updatedProfile == null || updatedProfile.username.isNullOrBlank()) {
                        throw IllegalStateException("Nama profil gagal tersimpan di server")
                    }

                    prefs.saveUserName(updatedProfile.username)
                    updatedProfile.role?.let { prefs.saveUserRole(it) }

                    withContext(Dispatchers.Main) {
                        dialog.dismiss()
                        binding.tvProfileName.text = updatedProfile.username
                        Toast.makeText(requireContext(), "Nama profil diperbarui", Toast.LENGTH_SHORT).show()
                        syncProfileFromServer()
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

    private fun setupMyPosts() {
        myPostsAdapter = MyPostAdapter(
            onItemClick = { post ->
                val intent = Intent(requireContext(), com.example.dispatchapp.SinglePostActivity::class.java)
                intent.putExtra("POST_ID", post.id)
                startActivity(intent)
            },
            onDeleteClick = { post ->
                val title = if (isSavedTabSelected) "Hapus dari Simpanan" else "Hapus Postingan"
                val message = if (isSavedTabSelected) "Hapus postingan ini dari daftar simpanan?" else "Yakin ingin menghapus postingan ini?"
                val positiveText = if (isSavedTabSelected) "Hapus" else "Hapus"
                
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton(positiveText) { _, _ -> deleteMyPost(post) }
                    .setNegativeButton("Batal", null)
                    .show()
            }
        )
        binding.rvMyPosts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMyPosts.adapter = myPostsAdapter

        binding.tvTogglePosts.setOnClickListener {
            isShowingAllPosts = !isShowingAllPosts
            renderPostsSection()
        }
        
        binding.tabLayoutPosts.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                isSavedTabSelected = tab.position == 1
                isShowingAllPosts = false
                loadMyPosts()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        
        loadMyPosts()
    }

    private fun loadMyPosts() {
        val currentUserId = SupabaseClient.client.auth.currentSessionOrNull()?.user?.id ?: return
        
        binding.tabLayoutPosts.visibility = View.VISIBLE
        binding.rvMyPosts.visibility = View.VISIBLE
        binding.pbMyPosts.visibility = View.VISIBLE
        binding.tvEmptyMyPosts.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val posts = if (isSavedTabSelected) {
                    val savedRaw = SupabaseClient.client.from("saved_posts")
                        .select(columns = Columns.raw("*, posts(*)")) {
                            filter { eq("user_id", currentUserId) }
                            order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                        }.decodeList<SavedPost>()
                    savedRaw.mapNotNull { it.posts }
                } else {
                    SupabaseClient.client.from("posts")
                        .select(columns = Columns.raw("*")) {
                            filter { eq("user_id", currentUserId) }
                            order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                        }.decodeList<Post>()
                }

                if (posts.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        latestPosts = emptyList()
                        latestLikeCounts = emptyMap()
                        latestCommentCounts = emptyMap()
                        binding.pbMyPosts.visibility = View.GONE
                        binding.tvEmptyMyPosts.visibility = View.VISIBLE
                        binding.tvTogglePosts.visibility = View.GONE
                        myPostsAdapter.setPosts(emptyList(), emptyMap(), emptyMap())
                    }
                    return@launch
                }

                val postIds = posts.map { it.id }

                val likesData = SupabaseClient.client.from("post_likes")
                    .select(columns = Columns.raw("post_id")) {
                        filter { isIn("post_id", postIds) }
                    }.decodeList<PostLike>()

                val commentsData = SupabaseClient.client.from("post_comments")
                    .select(columns = Columns.raw("post_id")) {
                        filter { isIn("post_id", postIds) }
                    }.decodeList<PostComment>()

                val likeCounts = mutableMapOf<Long, Int>()
                likesData.forEach { likeCounts[it.postId] = (likeCounts[it.postId] ?: 0) + 1 }

                val commentCounts = mutableMapOf<Long, Int>()
                commentsData.forEach { commentCounts[it.postId] = (commentCounts[it.postId] ?: 0) + 1 }

                withContext(Dispatchers.Main) {
                    latestPosts = posts
                    latestLikeCounts = likeCounts
                    latestCommentCounts = commentCounts
                    binding.pbMyPosts.visibility = View.GONE
                    binding.tvEmptyMyPosts.visibility = View.GONE
                    renderPostsSection()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    latestPosts = emptyList()
                    latestLikeCounts = emptyMap()
                    latestCommentCounts = emptyMap()
                    binding.pbMyPosts.visibility = View.GONE
                    binding.tvEmptyMyPosts.visibility = View.VISIBLE
                    binding.tvTogglePosts.visibility = View.GONE
                    binding.tvEmptyMyPosts.text = "Gagal memuat postingan: ${e.message}"
                }
            }
        }
    }

    private fun renderPostsSection() {
        if (latestPosts.isEmpty()) {
            binding.tvTogglePosts.visibility = View.GONE
            myPostsAdapter.setPosts(emptyList(), emptyMap(), emptyMap())
            return
        }

        val hasMoreThanThree = latestPosts.size > 3
        val displayedPosts = if (hasMoreThanThree && !isShowingAllPosts) {
            latestPosts.take(3)
        } else {
            latestPosts
        }

        val displayedIds = displayedPosts.map { it.id }.toSet()
        val displayedLikeCounts = latestLikeCounts.filterKeys { it in displayedIds }
        val displayedCommentCounts = latestCommentCounts.filterKeys { it in displayedIds }

        myPostsAdapter.setPosts(displayedPosts, displayedLikeCounts, displayedCommentCounts)

        if (hasMoreThanThree) {
            binding.tvTogglePosts.visibility = View.VISIBLE
            binding.tvTogglePosts.text = if (isShowingAllPosts) {
                "Tampilkan lebih sedikit"
            } else {
                "Lihat selengkapnya (${latestPosts.size})"
            }
        } else {
            binding.tvTogglePosts.visibility = View.GONE
        }
    }

    private fun deleteMyPost(post: Post) {
        binding.pbMyPosts.visibility = View.VISIBLE
        val currentUserId = SupabaseClient.client.auth.currentSessionOrNull()?.user?.id

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (isSavedTabSelected) {
                    if (currentUserId != null) {
                        SupabaseClient.client.from("saved_posts").delete {
                            filter { 
                                eq("post_id", post.id)
                                eq("user_id", currentUserId)
                            }
                        }
                    }
                } else {
                    // Delete from DB
                    SupabaseClient.client.from("posts").delete {
                        filter { eq("id", post.id) }
                    }

                    // Delete from Storage
                    try {
                        // Extract filename from URL
                        // Example: .../storage/v1/object/public/showcase_media/UUID/16849302.jpg
                        val urlParts = post.mediaUrl.split("showcase_media/")
                        if (urlParts.size > 1) {
                            val path = urlParts[1].substringBefore("?")
                            SupabaseClient.client.storage["showcase_media"].delete(path)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace() // If storage delete fails, at least DB is deleted
                    }
                }

                withContext(Dispatchers.Main) {
                    binding.pbMyPosts.visibility = View.GONE
                    Toast.makeText(context, if (isSavedTabSelected) "Dihapus dari daftar simpan" else "Postingan dihapus", Toast.LENGTH_SHORT).show()
                    loadMyPosts() // Reload list
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    binding.pbMyPosts.visibility = View.GONE
                    Toast.makeText(context, "Gagal menghapus postingan", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
