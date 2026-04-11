package com.example.dispatchapp.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import coil.load
import com.example.dispatchapp.SupabaseClient
import com.example.dispatchapp.UserPreferences
import com.example.dispatchapp.databinding.ActivityHomeFragmentBinding
import com.example.dispatchapp.models.Profile
import com.example.dispatchapp.models.Student
import com.google.android.material.tabs.TabLayoutMediator
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import io.ktor.client.plugins.sse.SSEBufferPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class HomeFragment : Fragment() {
    private var _binding: ActivityHomeFragmentBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ActivityHomeFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = UserPreferences(requireContext())
        
        // Check if student is verified
        val studentId = prefs.getStudentId()
        val isStudentVerified = !studentId.isNullOrEmpty()
        
        setupViewPager(isStudentVerified)

        binding.userName.text = prefs.getUserName().toString().trim().substringBefore(" ")
        
        val uuid = SupabaseClient.client.auth.currentSessionOrNull()?.user?.id
        if (uuid != null) {
            val avatarUrl = SupabaseClient.client.storage["user_profiles"].publicUrl("avatar_$uuid") + "?t=${System.currentTimeMillis()}"
            binding.userAvatar.load(avatarUrl) {
                crossfade(true)
                transformations(coil.transform.CircleCropTransformation())
                error(com.example.dispatchapp.R.drawable.pfp)
            }
        } else {
            binding.userAvatar.load(com.example.dispatchapp.R.drawable.pfp) {
                transformations(coil.transform.CircleCropTransformation())
            }
        }

        binding.userAvatar.setOnClickListener {
            val nav = requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(com.example.dispatchapp.R.id.bottom_navigation)
            nav?.selectedItemId = com.example.dispatchapp.R.id.nav_profile
        }

    }

    // fetchAvatarFromSupabase dihilangkan karena memuat avatar langsung di onViewCreated dari UserPreferences



    private fun setupViewPager(isStudentVerified: Boolean) {
        if (isStudentVerified) {
            // Show both tabs: Minat Bakat + Semua
            binding.tabLayout.visibility = View.VISIBLE
            binding.lineDivider.visibility = View.VISIBLE
            
            val adapter = object : FragmentStateAdapter(this) {
                override fun getItemCount(): Int = 2
                override fun createFragment(position: Int): Fragment {
                    return when (position) {
                        0 -> InterestEventFragment()
                        else -> AllEventFragment()
                    }
                }
            }
            binding.viewPager.adapter = adapter
            TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
                tab.text = when (position) {
                    0 -> "Minat Bakat"
                    else -> "Semua"
                }
            }.attach()
        } else {
            // Not verified: only show Semua Event, hide tabs
            binding.tabLayout.visibility = View.GONE
            binding.lineDivider.visibility = View.GONE
            
            val adapter = object : FragmentStateAdapter(this) {
                override fun getItemCount(): Int = 1
                override fun createFragment(position: Int): Fragment {
                    return AllEventFragment()
                }
            }
            binding.viewPager.adapter = adapter
        }
    }

    private fun roleCheck(){
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try{
                val session = SupabaseClient.client.auth.currentSessionOrNull()
                val uuid = session?.user?.id

                if (uuid.isNullOrEmpty()) {
                    Log.w("InterestEventFragment", "Student ID is empty!")
                }

                val response = SupabaseClient.client.from("profiles")
                    .select(columns = Columns.list("role")) {
                        filter {
                            eq("id", uuid.toString())
                        }
                    }.decodeSingle<Profile>()

                withContext(Dispatchers.Main) {
                    if (response.role?.equals("student", ignoreCase = true) == true) {
                        Toast.makeText(requireContext(), response.toString(), Toast.LENGTH_SHORT).show()
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}