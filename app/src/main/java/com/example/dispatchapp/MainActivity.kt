package com.example.dispatchapp

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.example.dispatchapp.databinding.ActivityMainBinding
import com.example.dispatchapp.fragments.HomeFragment
import com.example.dispatchapp.fragments.ProfileFragment
import com.example.dispatchapp.fragments.ShowcaseFragment
import com.example.dispatchapp.fragments.WishlistFragment

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)

        enableEdgeToEdge()
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupBottomNavigation()

        if (savedInstanceState == null) {
            showFragment(HomeFragment())
            binding.bottomNavigation.selectedItemId = R.id.nav_home
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    showFragment(HomeFragment())
                    true
                }
                R.id.nav_showcase -> {
                    showFragment(ShowcaseFragment())
                    true
                }
                R.id.nav_whistlist -> {
                    showFragment(WishlistFragment())
                    true
                }
                R.id.nav_profile -> {
                    showFragment(ProfileFragment())
                    true
                }
                else -> false
            }
        }
    }

    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    fun navigateToShowcaseWithPost(postId: Long) {
        binding.bottomNavigation.selectedItemId = R.id.nav_showcase
        val showcaseFragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as? ShowcaseFragment
        showcaseFragment?.scrollToPost(postId)
    }
}