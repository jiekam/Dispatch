package com.example.dispatchapp.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.dispatchapp.DetailEventActivity
import com.example.dispatchapp.OtpVerification
import com.example.dispatchapp.SupabaseClient
import com.example.dispatchapp.adapters.EventAdapter
import com.example.dispatchapp.databinding.ActivityAllEventFragmentBinding
import com.example.dispatchapp.models.Event
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class AllEventFragment : Fragment() {

    private var _binding: ActivityAllEventFragmentBinding? = null
    private val binding get() = _binding!!

    private lateinit var eventsAdapter: EventAdapter
    private val jsonConfig = Json { ignoreUnknownKeys = true }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ActivityAllEventFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        fetchAllEvents()
    }

    private fun setupRecyclerView() {
        // Menggunakan tampilan list small untuk All Event
        eventsAdapter = EventAdapter(isLarge = false) { event ->
            Toast.makeText(requireContext(), "Clicked: ${event.id}", Toast.LENGTH_SHORT).show()
            val intentToDetail = Intent(requireContext(), DetailEventActivity::class.java).apply {
                putExtra("event_id", event.id)
            }
            startActivity(intentToDetail)
        }
        binding.rvAllEvents.layoutManager = LinearLayoutManager(requireContext())
        binding.rvAllEvents.adapter = eventsAdapter
    }

    private fun fetchAllEvents() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                val currentDate = sdf.format(java.util.Date())
                val response = SupabaseClient.client.from("events")
                    .select(
                        columns = io.github.jan.supabase.postgrest.query.Columns.raw("*, profiles(username, role)")
                    ) {
                        filter {
                            gte("end_date", currentDate)
                        }
                    }.data

                val eventsList = jsonConfig.decodeFromString<List<Event>>(response)

                withContext(Dispatchers.Main) {
                    if (eventsList.isNotEmpty()) {
                        eventsAdapter.setEvents(eventsList)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Failed to load events: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}