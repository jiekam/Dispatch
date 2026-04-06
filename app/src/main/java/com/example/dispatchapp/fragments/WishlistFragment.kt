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
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.dispatchapp.DetailEventActivity
import com.example.dispatchapp.SupabaseClient
import com.example.dispatchapp.adapters.WishlistAdapter
import com.example.dispatchapp.databinding.FragmentWishlistBinding
import com.example.dispatchapp.models.Event
import com.example.dispatchapp.models.WishlistWithEvent
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class WishlistFragment : Fragment() {

    private var _binding: FragmentWishlistBinding? = null
    private val binding get() = _binding!!

    private lateinit var wishlistAdapter: WishlistAdapter
    private val jsonConfig = Json { ignoreUnknownKeys = true }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWishlistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        fetchWishlist()
    }

    private fun setupRecyclerView() {
        wishlistAdapter = WishlistAdapter { event ->
            val intentToDetail = Intent(requireContext(), DetailEventActivity::class.java).apply {
                putExtra("event_id", event.id)
            }
            startActivity(intentToDetail)
        }
        binding.rvWishlist.layoutManager = LinearLayoutManager(requireContext())
        binding.rvWishlist.adapter = wishlistAdapter
    }

    private fun fetchWishlist() {
        binding.progressBar.visibility = View.VISIBLE
        binding.rvWishlist.visibility = View.GONE
        binding.tvEmpty.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val session = SupabaseClient.client.auth.currentSessionOrNull()
                val uuid = session?.user?.id

                if (uuid.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                        binding.tvEmpty.visibility = View.VISIBLE
                        binding.tvEmpty.text = "Silakan login terlebih dahulu"
                    }
                    return@launch
                }

                val response = SupabaseClient.client.from("wishlists")
                    .select(
                        columns = Columns.raw("""
                            *,
                            events(
                                *,
                                profiles(username, role)
                            )
                        """.trimIndent())
                    ) {
                        filter {
                            eq("user_id", uuid)
                        }
                        order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                    }.data

                val wishlistItems = jsonConfig.decodeFromString<List<WishlistWithEvent>>(response)

                // Extract events and group by event end_date
                val events = wishlistItems.mapNotNull { it.events }

                val dateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("id", "ID"))

                val groupedEvents = events.groupBy { event ->
                    try {
                        val date = LocalDate.parse(event.endDate ?: "")
                        date.format(dateFormatter)
                    } catch (e: Exception) {
                        "Tanggal Tidak Diketahui"
                    }
                }.toSortedMap(compareBy { label ->
                    try {
                        LocalDate.parse(label, dateFormatter)
                    } catch (e: Exception) {
                        LocalDate.MAX
                    }
                })

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE

                    if (events.isEmpty()) {
                        binding.tvEmpty.visibility = View.VISIBLE
                        binding.rvWishlist.visibility = View.GONE
                    } else {
                        binding.tvEmpty.visibility = View.GONE
                        binding.rvWishlist.visibility = View.VISIBLE
                        wishlistAdapter.setGroupedEvents(groupedEvents)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("WishlistFragment", "Gagal memuat wishlist: ${e.message}")
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.tvEmpty.text = "Gagal memuat data"
                    Toast.makeText(requireContext(), "Gagal memuat wishlist: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        fetchWishlist()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
