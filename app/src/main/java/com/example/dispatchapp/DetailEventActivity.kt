package com.example.dispatchapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.dispatchapp.databinding.ActivityDetailEventBinding
import com.example.dispatchapp.models.Event
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.jan.supabase.auth.auth
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
private data class WishlistInsert(
    @SerialName("user_id") val userId: String,
    @SerialName("event_id") val eventId: Long
)

class DetailEventActivity : BaseActivity() {

    private lateinit var binding: ActivityDetailEventBinding

    private var eventId: Long = -1L
    private var isWishlisted: Boolean = false
    private var colorSecondary: android.content.res.ColorStateList? = null
    private var colorPrimary: android.content.res.ColorStateList? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityDetailEventBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)

        colorSecondary = binding.tvWishlistText.textColors
        colorPrimary = binding.tvParticipantsCount.textColors

        eventId = intent.getLongExtra("event_id", -1L)

        binding.btnBack.setOnClickListener { finish() }

        binding.btnShare.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Lihat event ini di Dispatch App!")
            }
            startActivity(Intent.createChooser(shareIntent, "Bagikan Event"))
        }

        binding.btnKontak.setOnClickListener {
            Toast.makeText(this, "Fitur Kontak segera hadir!", Toast.LENGTH_SHORT).show()
        }

        if (eventId != -1L) {
            fetchEventDetail(eventId)
            checkWishlistStatus()
        } else {
            Toast.makeText(this, "Event tidak ditemukan", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun fetchEventDetail(eventId: Long) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = SupabaseClient.client.from("events")
                    .select(columns = io.github.jan.supabase.postgrest.query.Columns.raw("""
                    *, 
                    profiles(username, role)
                """)) {
                        filter {
                            eq("id", eventId)
                        }
                    }.decodeSingle<Event>()

                withContext(Dispatchers.Main) {
                    bindData(response)

                    binding.progressBar.visibility = android.view.View.GONE
                    binding.scrollContent.visibility = android.view.View.VISIBLE

                    Log.d("DEBUG", "Data Berhasil: ${response.title} oleh ${response.profiles?.username}")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = android.view.View.GONE
                    Toast.makeText(this@DetailEventActivity, "Gagal memuat event", Toast.LENGTH_SHORT).show()
                }
                Log.e( "ERROR", "Gagal ambil data: ${e.message}")
            }
        }
    }

    private fun bindData(event: Event) {
        // Title
        binding.tvTitle.text = event.title

        binding.tvDateDetail.text = "${event.startDate ?: "–"} – ${event.endDate ?: "–"}"

        // Counters
        binding.tvWishlistCount.text = "${event.wishlistCount} Menyukai"
        binding.tvParticipantsCount.text = "0 Mendaftar"

        // Banner image
        if (!event.bannerUrl.isNullOrEmpty()) {
            binding.ivEvent.load(event.bannerUrl) { crossfade(true) }
        }

        binding.btnShare.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Lihat event ini: ${event.title} — Download Dispatch App!")
            }
            startActivity(Intent.createChooser(shareIntent, "Bagikan Event"))
        }

        val uploaderName = event.profiles?.username ?: "Anonim"
        val uploaderRole = event.profiles?.role?.replaceFirstChar { it.uppercase() } ?: "–"
        binding.tvUploaderName.text = uploaderName
        binding.tvUploaderRole.text = uploaderRole

        // Verif icon
        if (event.profiles?.role?.equals("organizer", ignoreCase = true) == true) {
            binding.ivVerif.visibility = android.view.View.VISIBLE
        } else {
            binding.ivVerif.visibility = android.view.View.GONE
        }

        // Location text
        val locationText = event.location?.trim() ?: "–"
        binding.tvLocationDetail.text = locationText

        // Location
        if (locationText.contains(",")) {
            binding.tvLocationName.text = locationText.substringBefore(",").trim()
        } else {
            binding.tvLocationName.text = locationText
        }

        // Map OpenStreetMap
        loadMap(locationText)

        // Description
        binding.tvDescription.text = event.desc.trim()

        binding.btnDaftar.setOnClickListener {
            Toast.makeText(this, "Fitur Pendaftaran segera hadir!", Toast.LENGTH_SHORT).show()
        }
        binding.btnWishlist.setOnClickListener {
            handleWishlist()
        }
    }

    private fun handleWishlist() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val session = SupabaseClient.client.auth.currentSessionOrNull()
                val uuid = session?.user?.id

                if (uuid == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@DetailEventActivity, "Silakan login terlebih dahulu", Toast.LENGTH_SHORT).show()
                     }
                    return@launch
                }

                // Cek apakah data sudah ada di wishlist
                val response = SupabaseClient.client.from("wishlists").select(columns = io.github.jan.supabase.postgrest.query.Columns.raw("id")) {
                    filter {
                        eq("user_id", uuid)
                        eq("event_id", eventId)
                    }
                }.data

                if (response != "[]") {
                    // Jika sudah ada, hapus dari wishlist
                    SupabaseClient.client.from("wishlists").delete {
                        filter {
                            eq("user_id", uuid)
                            eq("event_id", eventId)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        isWishlisted = false
                        updateWishlistUI()
                        Toast.makeText(this@DetailEventActivity, "Dihapus dari wishlist", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // Jika belum ada, tambahkan ke wishlist
                    val wishlistData = WishlistInsert(userId = uuid, eventId = eventId)
                    SupabaseClient.client.from("wishlists").insert(wishlistData)
                    withContext(Dispatchers.Main) {
                        isWishlisted = true
                        updateWishlistUI()
                        Toast.makeText(this@DetailEventActivity, "Ditambahkan ke wishlist", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("DetailEvent", "Error wishlist: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DetailEventActivity, "Gagal mengelola wishlist", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun checkWishlistStatus() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val session = SupabaseClient.client.auth.currentSessionOrNull()
                val uuid = session?.user?.id ?: return@launch

                val response = SupabaseClient.client.from("wishlists").select(columns = io.github.jan.supabase.postgrest.query.Columns.raw("id")) {
                    filter {
                        eq("user_id", uuid)
                        eq("event_id", eventId)
                    }
                }.data

                withContext(Dispatchers.Main) {
                    isWishlisted = response != "[]"
                    updateWishlistUI()
                }
            } catch (e: Exception) {
                Log.e("DetailEvent", "Error check wishlist: ${e.message}")
            }
        }
    }

    private fun updateWishlistUI() {
        if (isWishlisted) {
            binding.ivWishlistIcon.setImageResource(android.R.drawable.btn_star_big_on)
            colorPrimary?.let {
                binding.ivWishlistIcon.imageTintList = it
                binding.tvWishlistText.setTextColor(it)
            }
            binding.tvWishlistText.text = "Tersimpan"
        } else {
            binding.ivWishlistIcon.setImageResource(android.R.drawable.btn_star_big_off)
            colorSecondary?.let {
                binding.ivWishlistIcon.imageTintList = it
                binding.tvWishlistText.setTextColor(it)
            }
            binding.tvWishlistText.text = "Wishlist"
        }
    }

    private fun loadMap(locationQuery: String) {
        val encodedLocation = java.net.URLEncoder.encode(locationQuery, "UTF-8")
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <style>
                    body { margin: 0; padding: 0; }
                    #map { width: 100%; height: 100vh; }
                </style>
                <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
                <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
            </head>
            <body>
                <div id="map"></div>
                <script>
                    fetch('https://nominatim.openstreetmap.org/search?q=$encodedLocation&format=json&limit=1',
                        { headers: { 'User-Agent': 'DispatchApp/1.0' } })
                    .then(r => r.json())
                    .then(data => {
                        var lat = data.length > 0 ? parseFloat(data[0].lat) : -6.2088;
                        var lon = data.length > 0 ? parseFloat(data[0].lon) : 106.8456;
                        var map = L.map('map', { zoomControl: false, attributionControl: false }).setView([lat, lon], 15);
                        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png').addTo(map);
                        L.marker([lat, lon]).addTo(map);
                    })
                    .catch(() => {
                        var map = L.map('map', { zoomControl: false, attributionControl: false }).setView([-6.2088, 106.8456], 13);
                        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png').addTo(map);
                    });
                </script>
            </body>
            </html>
        """.trimIndent()

        binding.mapWebview.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            webViewClient = WebViewClient()
            loadDataWithBaseURL("https://nominatim.openstreetmap.org", html, "text/html", "UTF-8", null)
        }
    }
}