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
import com.example.dispatchapp.CreateEventActivity
import com.example.dispatchapp.OrganizerEventDetailActivity
import com.example.dispatchapp.SupabaseClient
import com.example.dispatchapp.adapters.OrganizerEventAdapter
import com.example.dispatchapp.databinding.FragmentOrganizerEventsListBinding
import com.example.dispatchapp.models.Event
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OrganizerEventsListFragment : Fragment() {

    private var _binding: FragmentOrganizerEventsListBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: OrganizerEventAdapter
    private var allEvents: List<Event> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOrganizerEventsListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = OrganizerEventAdapter { event ->
            val intent = Intent(requireContext(), OrganizerEventDetailActivity::class.java)
            intent.putExtra("event_id", event.id)
            startActivity(intent)
        }
        binding.rvMyEvents.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMyEvents.adapter = adapter

        binding.searchEvents.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                val filtered = if (newText.isNullOrBlank()) allEvents
                else allEvents.filter { it.title.contains(newText, ignoreCase = true) }
                adapter.setEvents(filtered)
                return true
            }
        })

        binding.tabEventStatus.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                filterByTab(tab?.position ?: 0)
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })

        loadMyEvents()
    }

    override fun onResume() {
        super.onResume()
        loadMyEvents()
    }

    private fun filterByTab(position: Int) {
        val now = System.currentTimeMillis()
        val filtered = when (position) {
            0 -> allEvents
            1 -> allEvents.filter { event ->
                val start = parseDate(event.startDate)
                start != null && start > now
            }
            2 -> allEvents.filter { event ->
                val start = parseDate(event.startDate)
                val end = parseDate(event.endDate)
                start != null && end != null && start <= now && end >= now
            }
            3 -> allEvents.filter { event ->
                val end = parseDate(event.endDate)
                end != null && end < now
            }
            else -> allEvents
        }
        adapter.setEvents(filtered)
        binding.emptyStateEvents.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.rvMyEvents.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun parseDate(dateStr: String?): Long? {
        if (dateStr.isNullOrBlank()) return null
        return try {
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).parse(dateStr)?.time
        } catch (_: Exception) { null }
    }

    private fun loadMyEvents() {
        val uuid = SupabaseClient.client.auth.currentSessionOrNull()?.user?.id ?: return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val events = SupabaseClient.client.from("events")
                    .select(columns = Columns.raw("*, profiles(username, role, account_status, organization_name)")) {
                        filter { eq("user_id", uuid) }
                        order("id", Order.DESCENDING)
                    }.decodeList<Event>()

                withContext(Dispatchers.Main) {
                    allEvents = events
                    filterByTab(binding.tabEventStatus.selectedTabPosition)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Gagal memuat event: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
