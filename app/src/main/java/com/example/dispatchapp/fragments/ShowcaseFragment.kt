package com.example.dispatchapp.fragments

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.dispatchapp.DetailEventActivity
import com.example.dispatchapp.SupabaseClient
import com.example.dispatchapp.adapters.EventAdapter
import com.example.dispatchapp.databinding.FragmentShowcaseBinding
import com.example.dispatchapp.models.Event
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class ShowcaseFragment : Fragment() {

    private var _binding: FragmentShowcaseBinding? = null
    private val binding get() = _binding!!

    private lateinit var searchAdapter: EventAdapter
    private val jsonConfig = Json { ignoreUnknownKeys = true }

    private var searchJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentShowcaseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSearchBar()
    }

    private fun setupRecyclerView() {
        searchAdapter = EventAdapter(isLarge = false) { event ->
            val intent = Intent(requireContext(), DetailEventActivity::class.java).apply {
                putExtra("event_id", event.id)
            }
            startActivity(intent)
        }
        binding.rvSearchResults.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSearchResults.adapter = searchAdapter
    }

    private fun setupSearchBar() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                // Debounce 400ms agar tidak spam request tiap ketukan
                searchJob?.cancel()
                searchJob = viewLifecycleOwner.lifecycleScope.launch {
                    delay(400)
                    if (query.isEmpty()) {
                        binding.rvSearchResults.visibility = View.GONE
                        binding.tvHint.visibility = View.VISIBLE
                        binding.progressBar.visibility = View.GONE
                    } else {
                        searchEvents(query)
                    }
                }
            }
        })

        // Ketika user tekan Search di keyboard
        binding.etSearch.setOnEditorActionListener { _, _, _ ->
            val query = binding.etSearch.text?.toString()?.trim() ?: ""
            if (query.isNotEmpty()) searchEvents(query)
            true
        }

        // Tombol ikon search di ujung kanan
        binding.tilSearch.setEndIconOnClickListener {
            val query = binding.etSearch.text?.toString()?.trim() ?: ""
            if (query.isNotEmpty()) searchEvents(query)
        }
    }

    private fun searchEvents(query: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.rvSearchResults.visibility = View.GONE
        binding.tvHint.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = SupabaseClient.client.from("events")
                    .select(
                        columns = Columns.raw("*, profiles(username, role)")
                    ) {
                        filter {
                            ilike("title", "%$query%")
                        }
                    }.data

                val results = jsonConfig.decodeFromString<List<Event>>(response)

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    if (results.isEmpty()) {
                        binding.tvHint.text = "Tidak ada event dengan kata \"$query\""
                        binding.tvHint.visibility = View.VISIBLE
                        binding.rvSearchResults.visibility = View.GONE
                    } else {
                        searchAdapter.setEvents(results)
                        binding.rvSearchResults.visibility = View.VISIBLE
                        binding.tvHint.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.tvHint.text = "Gagal mencari: ${e.message}"
                    binding.tvHint.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
