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
import coil.load
import com.example.dispatchapp.CreateEventActivity
import com.example.dispatchapp.OrganizerEventDetailActivity
import com.example.dispatchapp.SupabaseClient
import com.example.dispatchapp.UserPreferences
import com.example.dispatchapp.adapters.OrganizerEventAdapter
import com.example.dispatchapp.databinding.FragmentOrganizerHomeBinding
import com.example.dispatchapp.models.Event
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OrganizerHomeFragment : Fragment() {

    private var _binding: FragmentOrganizerHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var eventsAdapter: OrganizerEventAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOrganizerHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = UserPreferences(requireContext())
        val uuid = SupabaseClient.client.auth.currentSessionOrNull()?.user?.id

        if (uuid != null) {
            val avatarUrl = SupabaseClient.client.storage["user_profiles"].publicUrl("avatar_$uuid") + "?t=${System.currentTimeMillis()}"
            binding.ivProfilePic.load(avatarUrl) {
                crossfade(true)
                error(com.example.dispatchapp.R.drawable.ic_profile_placeholder)
            }
        }

        eventsAdapter = OrganizerEventAdapter { event ->
            val intent = Intent(requireContext(), OrganizerEventDetailActivity::class.java)
            intent.putExtra("event_id", event.id)
            startActivity(intent)
        }
        binding.rvEvents.layoutManager = LinearLayoutManager(requireContext())
        binding.rvEvents.adapter = eventsAdapter

        binding.btnCreateEvent.setOnClickListener {
            startActivity(Intent(requireContext(), CreateEventActivity::class.java))
        }

        loadMyEvents(uuid)
    }

    override fun onResume() {
        super.onResume()
        val uuid = SupabaseClient.client.auth.currentSessionOrNull()?.user?.id
        loadMyEvents(uuid)
    }

    private fun loadMyEvents(uuid: String?) {
        if (uuid == null) return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val events = SupabaseClient.client.from("events")
                    .select(columns = Columns.raw("*, profiles(username, role, account_status, organization_name)")) {
                        filter { eq("user_id", uuid) }
                        order("id", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                    }.decodeList<Event>()

                withContext(Dispatchers.Main) {
                    if (events.isEmpty()) {
                        binding.emptyState.visibility = View.VISIBLE
                        binding.rvEvents.visibility = View.GONE
                    } else {
                        binding.emptyState.visibility = View.GONE
                        binding.rvEvents.visibility = View.VISIBLE
                        eventsAdapter.setEvents(events)
                    }
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
