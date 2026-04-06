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
import com.example.dispatchapp.UserPreferences
import com.example.dispatchapp.adapters.EventAdapter
import com.example.dispatchapp.databinding.ActivityInterestEventFragmentBinding
import com.example.dispatchapp.models.Event
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class InterestEventFragment : Fragment() {

    private var _binding: ActivityInterestEventFragmentBinding? = null
    private val binding get() = _binding!!

    private lateinit var popularEventsAdapter: EventAdapter
    private lateinit var otherEventsAdapter: EventAdapter

    private val jsonConfig = Json { ignoreUnknownKeys = true }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ActivityInterestEventFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Toast.makeText(requireContext(), UserPreferences(requireContext()).getStudentId(), Toast.LENGTH_SHORT).show()

        setupRecyclerViews()
        fetchEventsFromSupabase()
    }

    private fun setupRecyclerViews() {
        // Horizontal untuk Terdekat Populer
        popularEventsAdapter = EventAdapter(isLarge = true) { event ->
            Toast.makeText(requireContext(), "Clicked: ${event.id}", Toast.LENGTH_SHORT).show()
            val intentToDetail = Intent(requireContext(), DetailEventActivity::class.java).apply {
                putExtra("event_id", event.id)
            }
            startActivity(intentToDetail)
        }
        binding.rvPopularEvents.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvPopularEvents.adapter = popularEventsAdapter

        // Vertical untuk Lainnya
        otherEventsAdapter = EventAdapter(isLarge = false) { event ->
            Toast.makeText(requireContext(), "Clicked: ${event.id}", Toast.LENGTH_SHORT).show()
            val intentToDetail = Intent(requireContext(), DetailEventActivity::class.java).apply {
                putExtra("event_id", event.id)
            }
            startActivity(intentToDetail)
        }
        binding.rvOtherEvents.layoutManager =
            LinearLayoutManager(requireContext())
        binding.rvOtherEvents.adapter = otherEventsAdapter
    }

    private fun fetchEventsFromSupabase() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val studentId = UserPreferences(requireContext()).getStudentId() ?: ""
                if (studentId.isEmpty()) {
                    Log.w("InterestEventFragment", "Student ID is empty!")
                }

                val currentDate = java.time.LocalDate.now().toString()

                val response = SupabaseClient.client.from("events")
                    .select(
                        columns = Columns.raw(
                            """
                            *,
                            profiles(username, role),
                            event_tag!inner(
                                interest!inner(
                                    user_interest!inner(
                                        id_student
                                    )
                                )
                            )
                            """.trimIndent()
                        )
                    ) {
                        filter {
                            eq("event_tag.interest.user_interest.id_student", studentId.toInt())
                            gte("end_date", currentDate)
                        }
                    }

                val eventsList = jsonConfig.decodeFromString<List<Event>>(response.data)

                withContext(Dispatchers.Main) {
                    if (eventsList.isNotEmpty()) {
                        // Urutkan berdasarkan wishlist_count terbanyak
                        val sortedList = eventsList.sortedByDescending { it.wishlistCount }

                        val popular = sortedList.take(1)
                        val other = if (sortedList.size > 1) sortedList.drop(1) else emptyList()
                        
                        popularEventsAdapter.setEvents(popular)
                        otherEventsAdapter.setEvents(other)
                    } else {
                        Log.w("InterestEventFragment", "Events list is empty after filtering for student: $studentId")
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