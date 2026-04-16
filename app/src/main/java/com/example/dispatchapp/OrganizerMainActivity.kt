package com.example.dispatchapp

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.dispatchapp.databinding.ActivityOrganizerMainBinding
import com.example.dispatchapp.fragments.OrganizerEventsListFragment
import com.example.dispatchapp.fragments.OrganizerHomeFragment
import com.example.dispatchapp.fragments.OrganizerNotificationsFragment
import com.example.dispatchapp.fragments.OrganizerProfileFragment
import io.github.jan.supabase.auth.auth

class OrganizerMainActivity : BaseActivity() {

    private lateinit var binding: ActivityOrganizerMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val session = SupabaseClient.client.auth.currentSessionOrNull()
        if (session == null) {
            UserPreferences(this).clearUserDataKeepTheme()
            Toast.makeText(this, "Sesi login tidak valid", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, RoleSelection::class.java))
            finish()
            return
        }

        binding = ActivityOrganizerMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBottomNavigation()

        if (savedInstanceState == null) {
            showFragment(OrganizerHomeFragment())
            binding.bottomNavigation.selectedItemId = R.id.nav_home
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { showFragment(OrganizerHomeFragment()); true }
                R.id.nav_events -> { showFragment(OrganizerEventsListFragment()); true }
                R.id.nav_notifications -> { showFragment(OrganizerNotificationsFragment()); true }
                R.id.nav_profile -> { showFragment(OrganizerProfileFragment()); true }
                else -> false
            }
        }
    }

    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}
