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


    }


}
