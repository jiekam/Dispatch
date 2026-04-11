package com.example.dispatchapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.dispatchapp.databinding.ActivityVerifyStudentBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream

class VerifyStudentActivity : BaseActivity() {

    private lateinit var binding: ActivityVerifyStudentBinding
    private var imageCapture: ImageCapture? = null
    private var capturedBitmap: Bitmap? = null
    private var detectedNis: String? = null
    private var detectedName: String? = null

    // Enum values from database
    private val kelasOptions = listOf("X", "XI", "XII", "XIII")
    private val jurusanOptions = listOf("RPL", "TKJ", "SIJA", "TKR", "TKP", "DPIB", "DKV", "TITL", "DGM", "TP")
    private val prodiOptions = listOf("INFORMATIKA", "OTOMOTIF", "BANGUNAN", "DKV", "LISTRIK", "MESIN")

    // Camera permission launcher
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Izin kamera diperlukan untuk scan kartu", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVerifyStudentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupDropdowns()
        setupClickListeners()
        checkCameraPermission()
    }

    private fun setupDropdowns() {
        binding.ddKelas.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, kelasOptions)
        )
        binding.ddJurusan.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, jurusanOptions)
        )
        binding.ddProdi.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, prodiOptions)
        )
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnBackForm.setOnClickListener { backToCamera() }
        binding.btnRetake.setOnClickListener { backToCamera() }

        binding.fabCapture.setOnClickListener { capturePhoto() }
        binding.btnSubmit.setOnClickListener { submitVerification() }
    }

    // ========== CAMERA ==========

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = binding.cameraPreview.surfaceProvider
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (e: Exception) {
                Log.e("VerifyStudent", "Camera bind failed: ${e.message}")
                Toast.makeText(this, "Gagal membuka kamera", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun capturePhoto() {
        val capture = imageCapture ?: return

        binding.layoutLoading.visibility = View.VISIBLE
        binding.fabCapture.isEnabled = false

        capture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    val bitmap = imageProxyToBitmap(imageProxy)
                    imageProxy.close()

                    if (bitmap != null) {
                        capturedBitmap = bitmap
                        processWithOcr(bitmap)
                    } else {
                        binding.layoutLoading.visibility = View.GONE
                        binding.fabCapture.isEnabled = true
                        Toast.makeText(this@VerifyStudentActivity, "Gagal mengambil foto", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    binding.layoutLoading.visibility = View.GONE
                    binding.fabCapture.isEnabled = true
                    Toast.makeText(this@VerifyStudentActivity, "Error: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    // ========== OCR ==========

    private fun processWithOcr(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val fullText = visionText.text
                Log.d("VerifyStudent", "OCR Result:\n$fullText")

                val validationResult = validateCardText(fullText)

                if (validationResult.isValid) {
                    detectedNis = validationResult.nis
                    detectedName = validationResult.studentName

                    // Check NIS duplicate immediately after OCR (before showing form)
                    checkNisDuplicateAndShowForm(bitmap, validationResult)
                } else {
                    binding.layoutLoading.visibility = View.GONE
                    binding.fabCapture.isEnabled = true
                    showValidationError(validationResult.errorMessage)
                }
            }
            .addOnFailureListener { e ->
                binding.layoutLoading.visibility = View.GONE
                binding.fabCapture.isEnabled = true
                Toast.makeText(this, "OCR gagal: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    data class CardValidationResult(
        val isValid: Boolean,
        val nis: String? = null,
        val studentName: String? = null,
        val errorMessage: String = ""
    )

    private fun validateCardText(text: String): CardValidationResult {
        val upperText = text.uppercase()

        // 1. Check for school identifier
        val isSmkn1Jakarta = upperText.contains("SMK") &&
                (upperText.contains("NEGERI 1 JAKARTA") || upperText.contains("SMKN 1 JAKARTA") || upperText.contains("SMKN1JAKARTA"))

        if (!isSmkn1Jakarta) {
            val hasSmk = upperText.contains("SMK")
            val hasJakarta = upperText.contains("JAKARTA")
            if (!(hasSmk && hasJakarta)) {
                return CardValidationResult(
                    isValid = false,
                    errorMessage = "Kartu ini bukan kartu pelajar SMKN 1 Jakarta.\nPastikan kartu terlihat jelas dan coba lagi."
                )
            }
        }

        // 2. Check for "KARTU TANDA PELAJAR" or "KTP" keywords
        val isStudentCard = upperText.contains("KARTU TANDA PELAJAR") ||
                upperText.contains("KTP") ||
                upperText.contains("KARTU PELAJAR")

        if (!isStudentCard) {
            val hasPelajar = upperText.contains("PELAJAR")
            if (!hasPelajar) {
                return CardValidationResult(
                    isValid = false,
                    errorMessage = "Tidak terdeteksi sebagai Kartu Tanda Pelajar.\nPastikan foto jelas dan tidak terpotong."
                )
            }
        }

        // 3. Extract NIS
        val nisPatterns = listOf(
            Regex("""NIS\s*[/\\]\s*NISN\s*[:\s]*(\d{5,10})""", RegexOption.IGNORE_CASE),
            Regex("""NIS\s*[:\s]+(\d{5,10})""", RegexOption.IGNORE_CASE),
            Regex("""\b(20\d{7})\b"""),
        )

        var extractedNis: String? = null
        for (pattern in nisPatterns) {
            val match = pattern.find(text)
            if (match != null) {
                extractedNis = match.groupValues[1]
                break
            }
        }

        if (extractedNis == null) {
            return CardValidationResult(
                isValid = false,
                errorMessage = "NIS tidak terdeteksi pada kartu.\nPastikan nomor NIS terlihat jelas dan coba lagi."
            )
        }

        // 4. Extract Student Name
        // Key insight: on this card, the student name (ALL CAPS) always appears
        // AFTER the NIS number and BEFORE the word "Paket" (Paket Keahlian).
        // We use the NIS value we already found as an anchor position in the text.
        var extractedName: String? = null
        val lines = text.split("\n")

        // Log all lines for debugging
        Log.d("VerifyStudent", "=== OCR LINES ===")
        lines.forEachIndexed { idx, line -> Log.d("VerifyStudent", "[$idx] '${line.trim()}'") }
        Log.d("VerifyStudent", "=================")

        // Strategy 1: Inline "Nama : VALUE" on the SAME line (most common and reliable)
        // Use MULTILINE so ^ and $ match per line
        val namaInlineRegex = Regex("""^Nama\s*:\s*(.+)$""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
        val namaInlineMatch = namaInlineRegex.find(text)
        if (namaInlineMatch != null) {
            val candidate = namaInlineMatch.groupValues[1].trim()
            // Reject if it's a field label, not an actual name
            val badWords = listOf("Paket", "Keahlian", "Tempat", "Agama", "Alamat", "Islam", "Kristen")
            if (candidate.length >= 3 && badWords.none { candidate.startsWith(it, ignoreCase = true) }) {
                extractedName = candidate
                Log.d("VerifyStudent", "Name (S1 inline): $extractedName")
            }
        }

        // Strategy 2: Positional — slice text between NIS value and "Paket Keahlian"
        // The student name (ALL CAPS) is the prominent text in that zone
        if (extractedName == null && extractedNis != null) {
            val nisPos = text.indexOf(extractedNis)
            val paketPos = text.indexOf("Paket", ignoreCase = true)

            if (nisPos >= 0 && paketPos > nisPos) {
                // Extract the text window between NIS value and Paket Keahlian
                val window = text.substring(nisPos + extractedNis.length, paketPos)
                Log.d("VerifyStudent", "Window (S2): '$window'")

                // Find the longest sequence of ALL-CAPS words (≥2 letters each, at least 2 words)
                // This reliably identifies a person's name: "ARFA BANYU SANTORO"
                val allCapsRegex = Regex("""[A-Z]{2,}(?:\s+[A-Z]{2,})+""")
                val allCapsMatches = allCapsRegex.findAll(window).toList()

                if (allCapsMatches.isNotEmpty()) {
                    // Pick longest match (most likely to be the full name)
                    extractedName = allCapsMatches.maxByOrNull { it.value.length }?.value?.trim()
                    Log.d("VerifyStudent", "Name (S2 positional): $extractedName")
                }
            }
        }

        // Strategy 3: Scan line-by-line for "Nama" label then check next non-label line
        // Guards: must not be a field name, must not contain digits, must not have a colon
        if (extractedName == null) {
            val fieldStarters = setOf("nis", "nisn", "paket", "keahlian", "tempat", "agama", "alamat", "kelas", "jurusan", "rekayasa", "otomotif", "islam", "kristen", "hindu", "buddha")
            for (i in lines.indices) {
                val line = lines[i].trim()
                if (line.equals("Nama", ignoreCase = true)) {
                    for (j in i + 1 until minOf(i + 5, lines.size)) {
                        val next = lines[j].trim()
                        val nextLower = next.lowercase()
                        val isLabel = fieldStarters.any { nextLower.startsWith(it) }
                        val hasDigits = next.any { it.isDigit() }
                        val hasColon = next.contains(":")
                        val isShort = next.length < 3
                        if (!isLabel && !hasDigits && !hasColon && !isShort) {
                            extractedName = next
                            Log.d("VerifyStudent", "Name (S3 next-line [$j]): $extractedName")
                            break
                        }
                    }
                    if (extractedName != null) break
                }
            }
        }

        Log.d("VerifyStudent", "Final extracted name: $extractedName")

        if (extractedName.isNullOrEmpty()) {
            return CardValidationResult(
                isValid = false,
                errorMessage = "Nama siswa tidak terdeteksi pada kartu.\n\nPastikan:\n• Seluruh kartu terlihat jelas dan tidak miring\n• Pencahayaan cukup, tidak ada pantulan cahaya\n• Foto langsung kartu fisik, bukan foto layar\n\nCoba foto ulang."
            )
        }

        return CardValidationResult(
            isValid = true,
            nis = extractedNis,
            studentName = extractedName
        )
    }

    // ========== NIS DUPLICATE CHECK (right after scan, before form) ==========

    private fun checkNisDuplicateAndShowForm(bitmap: Bitmap, result: CardValidationResult) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val existingList = SupabaseClient.client.from("students")
                    .select {
                        filter { eq("nis", result.nis!!.toLong()) }
                    }.decodeList<com.example.dispatchapp.models.Student>()

                withContext(Dispatchers.Main) {
                    binding.layoutLoading.visibility = View.GONE

                    if (existingList.isNotEmpty()) {
                        // NIS already registered — block immediately
                        binding.fabCapture.isEnabled = true
                        com.google.android.material.dialog.MaterialAlertDialogBuilder(this@VerifyStudentActivity)
                            .setTitle("⚠️ NIS Sudah Terdaftar")
                            .setMessage(
                                "NIS ${result.nis} sudah terdaftar di sistem.\n\n" +
                                "Jika kamu merasa ini adalah kesalahan, hubungi admin sekolah.\n\n" +
                                "Kemungkinan penyebab:\n" +
                                "• Kamu sudah pernah mendaftar\n" +
                                "• Ada percobaan duplikasi kartu pelajar"
                            )
                            .setPositiveButton("Mengerti", null)
                            .show()
                    } else {
                        // NIS is unique — show the form
                        showFormState(bitmap, result)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.layoutLoading.visibility = View.GONE
                    binding.fabCapture.isEnabled = true
                    Toast.makeText(this@VerifyStudentActivity, "Gagal cek NIS: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ========== UI STATE ==========

    private fun showFormState(bitmap: Bitmap, result: CardValidationResult) {
        binding.layoutCamera.visibility = View.GONE
        binding.layoutForm.visibility = View.VISIBLE
        binding.ivCapturedCard.setImageBitmap(bitmap)

        // Pre-fill NIS (read-only — cannot be edited)
        binding.etNis.setText(result.nis)
        binding.etNis.isEnabled = false
        binding.etNis.isFocusable = false

        // Pre-fill Student Name (read-only — from the card)
        binding.etStudentName.setText(result.studentName)
        binding.etStudentName.isEnabled = false
        binding.etStudentName.isFocusable = false

        binding.tvOcrStatus.text = "Kartu pelajar SMKN 1 Jakarta terdeteksi\nNama: ${result.studentName} — NIS: ${result.nis}"
    }

    private fun backToCamera() {
        binding.layoutCamera.visibility = View.VISIBLE
        binding.layoutForm.visibility = View.GONE
        binding.fabCapture.isEnabled = true
        detectedNis = null
        detectedName = null
        capturedBitmap = null
        // Clear form fields
        binding.ddKelas.setText("", false)
        binding.ddJurusan.setText("", false)
        binding.ddProdi.setText("", false)
    }

    private fun showValidationError(message: String) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Verifikasi Gagal")
            .setMessage(message)
            .setPositiveButton("Coba Lagi", null)
            .show()
    }

    // ========== SUBMIT ==========

    private fun submitVerification() {
        // NIS and Name are already locked from OCR
        val nis = detectedNis ?: binding.etNis.text.toString().trim()
        val studentName = detectedName ?: binding.etStudentName.text.toString().trim()
        val kelas = binding.ddKelas.text.toString().trim()
        val jurusan = binding.ddJurusan.text.toString().trim()
        val prodi = binding.ddProdi.text.toString().trim()

        // Validate dropdowns only (NIS and name are guaranteed from OCR)
        if (kelas.isEmpty()) { binding.tilKelas.error = "Pilih kelas"; return }
        if (jurusan.isEmpty()) { binding.tilJurusan.error = "Pilih jurusan"; return }
        if (prodi.isEmpty()) { binding.tilProdi.error = "Pilih program studi"; return }

        binding.tilKelas.error = null
        binding.tilJurusan.error = null
        binding.tilProdi.error = null

        binding.pbVerify.visibility = View.VISIBLE
        binding.btnSubmit.isEnabled = false
        binding.btnRetake.isEnabled = false

        val uuid = SupabaseClient.client.auth.currentSessionOrNull()?.user?.id
        if (uuid == null) {
            Toast.makeText(this, "Sesi tidak valid, silakan login ulang", Toast.LENGTH_SHORT).show()
            binding.pbVerify.visibility = View.GONE
            binding.btnSubmit.isEnabled = true
            binding.btnRetake.isEnabled = true
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Step 1: Upload card image to Supabase Storage
                val bitmap = capturedBitmap
                if (bitmap != null) {
                    val outputStream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
                    val byteArray = outputStream.toByteArray()

                    val bucket = SupabaseClient.client.storage["student_cards"]
                    bucket.upload(
                        path = "card_$uuid.jpg",
                        data = byteArray
                    ) {
                        upsert = true
                    }
                }

                // Step 2: Insert into students table using buildJsonObject (avoids Map<String, Any> error)
                SupabaseClient.client.from("students")
                    .insert(
                        buildJsonObject {
                            put("id", uuid)
                            put("nis", nis.toLong())
                            put("student_name", studentName)
                            put("kelas", kelas)
                            put("jurusan", jurusan)
                            put("prodi", prodi)
                        }
                    )

                // Step 3: Update local preferences
                val prefs = UserPreferences(this@VerifyStudentActivity)
                prefs.saveUserNis(nis)
                prefs.saveStudentName(studentName)
                prefs.saveUserKelas(kelas)
                prefs.saveUserJurusan(jurusan)
                prefs.saveUserProdi(prodi)

                // Fetch the auto-generated student_id
                val studentData = SupabaseClient.client.from("students")
                    .select {
                        filter { eq("id", uuid) }
                    }.decodeSingleOrNull<com.example.dispatchapp.models.Student>()

                studentData?.student_id?.let { prefs.saveStudentId(it.toString()) }

                withContext(Dispatchers.Main) {
                    binding.pbVerify.visibility = View.GONE
                    // Launch interest selection screen with congratulations
                    val intent = Intent(this@VerifyStudentActivity, SelectInterestActivity::class.java)
                    intent.putExtra("student_id", studentData?.student_id?.toInt() ?: -1)
                    startActivity(intent)
                    setResult(RESULT_OK)
                    finish()
                }

            } catch (e: Exception) {
                Log.e("VerifyStudent", "Submit error: ${e.message}")
                withContext(Dispatchers.Main) {
                    binding.pbVerify.visibility = View.GONE
                    binding.btnSubmit.isEnabled = true
                    binding.btnRetake.isEnabled = true

                    val errMsg = e.message ?: "Terjadi kesalahan"
                    if (errMsg.contains("duplicate", ignoreCase = true) || errMsg.contains("unique", ignoreCase = true)) {
                        com.google.android.material.dialog.MaterialAlertDialogBuilder(this@VerifyStudentActivity)
                            .setTitle("⚠️ Data Duplikat")
                            .setMessage("Akun kamu sudah terdaftar sebagai siswa atau NIS sudah digunakan.")
                            .setPositiveButton("Mengerti", null)
                            .show()
                    } else {
                        Toast.makeText(this@VerifyStudentActivity, "Gagal: $errMsg", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}
