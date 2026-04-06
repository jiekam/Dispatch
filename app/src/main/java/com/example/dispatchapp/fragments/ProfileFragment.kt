package com.example.dispatchapp.fragments

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import coil.ImageLoader
import java.io.ByteArrayOutputStream
import com.example.dispatchapp.Login
import com.example.dispatchapp.RoleSelection
import com.example.dispatchapp.SupabaseClient
import com.example.dispatchapp.UserPreferences
import com.example.dispatchapp.databinding.FragmentProfileBinding
import com.example.dispatchapp.models.Student
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import coil.load
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val jsonConfig = Json { ignoreUnknownKeys = true }

    private val pickAvatarLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { uploadImage(it, true) }
    }

    private val pickBannerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { uploadImage(it, false) }
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

        binding.tvProfileName.text = prefs.getUserName() ?: "—"
        binding.tvProfileEmail.text = prefs.getUserEmail() ?: "—"
        binding.chipRole.text = when (prefs.getUserRole()) {
            "student" -> "Siswa"
            "organizer" -> "Organizer"
            "student_informan" -> "Informan"
            else -> prefs.getUserRole() ?: "—"
        }

        binding.tvNis.text = prefs.getUserNis() ?: "—"
        binding.tvKelas.text = prefs.getUserKelas() ?: "—"
        binding.tvJurusan.text = prefs.getUserJurusan() ?: "—"
        binding.tvProdi.text = prefs.getUserProdi() ?: "—"

        binding.ivProfileAvatar.setOnClickListener {
            pickAvatarLauncher.launch("image/*")
        }
        
        binding.ivProfileBanner.setOnClickListener {
            pickBannerLauncher.launch("image/*")
        }

        loadProfileImages()

        setupLogout()
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
        binding.ivProfileAvatar.load(avatarUrl, imageLoader) {
            crossfade(true)
            transformations(coil.transform.CircleCropTransformation())
            error(com.example.dispatchapp.R.drawable.pfp)
        }
        
        val bannerUrl = bucket.publicUrl("banner_$uuid") + "?t=$timestamp"
        binding.ivProfileBanner.load(bannerUrl, imageLoader) {
            crossfade(true)
        }
    }

    private fun uploadImage(uri: Uri, isAvatar: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val uuid = SupabaseClient.client.auth.currentSessionOrNull()?.user?.id ?: return@launch
                val fileName = if (isAvatar) "avatar_$uuid" else "banner_$uuid"
                
                val contentResolver = requireContext().contentResolver
                val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
                
                val byteArray = if (mimeType == "image/gif") {
                    val inputStream = contentResolver.openInputStream(uri) ?: return@launch
                    inputStream.readBytes()
                } else {
                    val inputStream = contentResolver.openInputStream(uri) ?: return@launch
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    val outputStream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                    outputStream.toByteArray()
                }

                if (byteArray.size > 50 * 1024 * 1024) {
                    withContext(Dispatchers.Main) { Toast.makeText(requireContext(), "Ukuran file terlalu besar (Max 50MB)", Toast.LENGTH_SHORT).show() }
                    return@launch
                }

                val bucket = SupabaseClient.client.storage["user_profiles"]
                bucket.upload(
                    path = fileName,
                    data = byteArray
                ) {
                    upsert = true
                }

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

    // fetchProfileData dihapus karena menggunakan UserPreferences

    private fun setupLogout() {
        binding.btnLogout.setOnClickListener {
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
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
