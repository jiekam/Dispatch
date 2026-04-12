package com.example.dispatchapp

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.example.dispatchapp.databinding.ActivityCreatePostBinding
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

import android.widget.ArrayAdapter
import com.example.dispatchapp.models.Interest

class CreatePostActivity : BaseActivity() {

    private lateinit var binding: ActivityCreatePostBinding
    private var selectedUri: Uri? = null
    private var isVideo: Boolean = false
    private var isGif: Boolean = false
    private var fileExtension: String = "jpg"
    
    private val interestsList = mutableListOf<Interest>()
    private val selectedInterestIds = mutableListOf<Long>()

    private val mediaPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedUri = uri
            binding.layoutPickMedia.visibility = View.GONE
            
            val mimeType = contentResolver.getType(uri)
            if (mimeType?.startsWith("video/") == true) {
                isVideo = true
                isGif = false
                fileExtension = "mp4" // Assuming mp4 for simplicity, or we can probe properly
                binding.ivPreview.visibility = View.GONE
                binding.vvPreview.visibility = View.VISIBLE
                binding.vvPreview.setVideoURI(uri)
                binding.vvPreview.setOnPreparedListener { mp ->
                    mp.isLooping = true
                    val videoWidth = mp.videoWidth
                    val videoHeight = mp.videoHeight
                    if (videoWidth > 0 && videoHeight > 0) {
                        updatePreviewAspectRatio(videoWidth, videoHeight)
                    }
                    binding.vvPreview.start()
                }
            } else {
                isVideo = false
                isGif = mimeType == "image/gif"
                fileExtension = if (isGif) "gif" else "jpg"
                binding.vvPreview.visibility = View.GONE
                binding.ivPreview.visibility = View.VISIBLE
                binding.ivPreview.setImageURI(uri)
                resolveImageSize(uri)?.let { (width, height) ->
                    updatePreviewAspectRatio(width, height)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreatePostBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.layoutPickMedia.setOnClickListener {
            mediaPickerLauncher.launch("*/*")
        }
        
        binding.ivPreview.setOnClickListener {
            mediaPickerLauncher.launch("*/*")
        }

        binding.vvPreview.setOnClickListener {
            mediaPickerLauncher.launch("*/*")
        }

        binding.btnSubmitPost.setOnClickListener {
            uploadPost()
        }
        
        loadInterests()
    }
    
    private fun loadInterests() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val interests = SupabaseClient.client.from("interest").select().decodeList<Interest>()
                withContext(Dispatchers.Main) {
                    interestsList.clear()
                    interestsList.addAll(interests)
                    
                    val names = interests.map { it.interest }.toTypedArray()
                    val checkedItems = BooleanArray(names.size)
                    
                    binding.btnSelectInterest.setOnClickListener {
                        com.google.android.material.dialog.MaterialAlertDialogBuilder(this@CreatePostActivity)
                            .setTitle("Pilih Kategori/Minat")
                            .setMultiChoiceItems(names, checkedItems) { _, which, isChecked ->
                                checkedItems[which] = isChecked
                            }
                            .setPositiveButton("Selesai") { _, _ ->
                                selectedInterestIds.clear()
                                binding.chipGroupSelection.removeAllViews()
                                
                                for (i in checkedItems.indices) {
                                    if (checkedItems[i]) {
                                        val interest = interestsList[i]
                                        selectedInterestIds.add(interest.id)
                                        
                                        val chip = com.google.android.material.chip.Chip(this@CreatePostActivity)
                                        chip.text = interest.interest
                                        chip.isCloseIconVisible = true
                                        chip.setOnCloseIconClickListener {
                                            binding.chipGroupSelection.removeView(chip)
                                            checkedItems[i] = false
                                            selectedInterestIds.remove(interest.id)
                                        }
                                        binding.chipGroupSelection.addView(chip)
                                    }
                                }
                            }
                            .setNegativeButton("Batal", null)
                            .show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun uploadPost() {
        val uri = selectedUri
        if (uri == null) {
            Toast.makeText(this, "Silakan pilih gambar atau video terlebih dahulu", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (selectedInterestIds.isEmpty()) {
            Toast.makeText(this, "Silakan pilih Kategori/Minat proyek", Toast.LENGTH_SHORT).show()
            return
        }

        val prefs = UserPreferences(this)
        val studentId = prefs.getStudentId()?.toLongOrNull()
        val userId = SupabaseClient.client.auth.currentSessionOrNull()?.user?.id

        if (studentId == null || userId == null) {
            Toast.makeText(this, "Tidak punya akses menulis post. Periksa status verifikasi Anda.", Toast.LENGTH_SHORT).show()
            return
        }

        val captionStr = binding.etCaption.text.toString().trim()
        val descStr = binding.etDescription.text.toString().trim()
        val finalDesc = if (descStr.isEmpty()) null else descStr

        binding.layoutLoading.visibility = View.VISIBLE
        binding.btnSubmitPost.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Read and compress
                val contentBytes = getCompressedMediaBytes(uri, isVideo, isGif)
                
                // Max size check: roughly 50MB (50 * 1024 * 1024 bytes) = 52428800
                if (contentBytes.size > 52428800) {
                    withContext(Dispatchers.Main) {
                        binding.layoutLoading.visibility = View.GONE
                        binding.btnSubmitPost.isEnabled = true
                        Toast.makeText(this@CreatePostActivity, "File terlalu besar (> 50MB).", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                
                // Upload to storage
                val bucket = SupabaseClient.client.storage["showcase_media"]
                val filename = "${userId}/${System.currentTimeMillis()}.$fileExtension"
                bucket.upload(
                    path = filename,
                    data = contentBytes
                ) {
                    upsert = true
                }

                val publicUrl = bucket.publicUrl(filename)

                // Insert into DB
                val insertedPost = SupabaseClient.client.from("posts").insert(buildJsonObject {
                    put("user_id", userId)
                    put("student_id", studentId)
                    put("media_url", publicUrl)
                    put("media_type", if (isVideo) "video" else "image")
                    put("caption", captionStr)
                    if (finalDesc != null) {
                        put("project_description", finalDesc)
                    }
                }) {
                    select()
                }.decodeSingle<com.example.dispatchapp.models.Post>()

                val postInterests = selectedInterestIds.map { interestId ->
                    buildJsonObject {
                        put("post_id", insertedPost.id)
                        put("interest_id", interestId)
                    }
                }
                
                if (postInterests.isNotEmpty()) {
                    SupabaseClient.client.from("post_interests").insert(postInterests)
                }

                withContext(Dispatchers.Main) {
                    binding.layoutLoading.visibility = View.GONE
                    Toast.makeText(this@CreatePostActivity, "Project berhasil diunggah!", Toast.LENGTH_SHORT).show()
                    finish()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    binding.layoutLoading.visibility = View.GONE
                    binding.btnSubmitPost.isEnabled = true
                    Toast.makeText(this@CreatePostActivity, "Terjadi kesalahan saat mengunggah: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun getCompressedMediaBytes(uri: Uri, isVideo: Boolean, isGif: Boolean): ByteArray {
        val inputStream = contentResolver.openInputStream(uri) ?: return ByteArray(0)
        
        if (isVideo) {
            // For video, we skip complex MediaCodec transcoding for robustness
            // and perform direct byte reading. File size max check happens out here.
            return inputStream.readBytes()
        } else if (isGif) {
            return inputStream.readBytes()
        } else {
            // It's an image. Parse and compress format to JPEG 80% to keep it < 2MB easily
            val bitmap = BitmapFactory.decodeStream(inputStream)
            val outputStream = ByteArrayOutputStream()
            // Resize if hugely unneccessary (optional)
            val maxWidth = 1080
            val scaledBitmap = if (bitmap.width > maxWidth) {
                val ratio = maxWidth.toFloat() / bitmap.width
                val newHeight = (bitmap.height * ratio).toInt()
                Bitmap.createScaledBitmap(bitmap, maxWidth, newHeight, true)
            } else {
                bitmap
            }
            
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            return outputStream.toByteArray()
        }
    }

    private fun resolveImageSize(uri: Uri): Pair<Int, Int>? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }

        val width = options.outWidth
        val height = options.outHeight
        return if (width > 0 && height > 0) width to height else null
    }

    private fun updatePreviewAspectRatio(mediaWidth: Int, mediaHeight: Int) {
        if (mediaWidth <= 0 || mediaHeight <= 0) return

        binding.frameMediaPreview.post {
            val containerWidth = binding.frameMediaPreview.width.takeIf { it > 0 }
                ?: (resources.displayMetrics.widthPixels - dpToPx(40))

            if (containerWidth <= 0) return@post

            val rawHeight = (containerWidth.toFloat() * mediaHeight / mediaWidth).roundToInt()
            val targetHeight = rawHeight.coerceIn(dpToPx(180), dpToPx(420))

            val params = binding.frameMediaPreview.layoutParams
            if (params.height != targetHeight) {
                params.height = targetHeight
                binding.frameMediaPreview.layoutParams = params
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).roundToInt()
    }
}
