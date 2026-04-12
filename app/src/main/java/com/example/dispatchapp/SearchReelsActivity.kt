package com.example.dispatchapp

import android.os.Bundle
import android.content.Intent
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.videoFrameMillis
import com.example.dispatchapp.databinding.ActivitySearchReelsBinding
import com.example.dispatchapp.databinding.ItemSearchProfileBinding
import com.example.dispatchapp.databinding.ItemSearchVideoBinding
import com.example.dispatchapp.models.Post
import com.example.dispatchapp.R
import com.google.android.material.tabs.TabLayout
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class SearchReelsActivity : BaseActivity() {

    private lateinit var binding: ActivitySearchReelsBinding
    private var searchJob: Job? = null
    
    private val videoAdapter = SearchVideoAdapter(
        onItemClick = { post ->
            val intent = Intent(this, SinglePostActivity::class.java)
            intent.putExtra("POST_ID", post.id)
            startActivity(intent)
        },
        onUserClick = { post ->
            startActivity(UserProfileActivity.createIntent(this, post.userId))
        }
    )

    private val profileAdapter = SearchProfileAdapter { profile ->
        startActivity(UserProfileActivity.createIntent(this, profile.id))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchReelsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val baseHeaderTopPadding = binding.layoutSearchHeader.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val topInset = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            ).top
            binding.layoutSearchHeader.updatePadding(top = baseHeaderTopPadding + topInset + dpToPx(4))
            insets
        }

        binding.ivBack.setOnClickListener { finish() }

        binding.rvSearchVideos.layoutManager = GridLayoutManager(this, 2)
        binding.rvSearchVideos.adapter = videoAdapter

        binding.rvSearchProfiles.layoutManager = LinearLayoutManager(this)
        binding.rvSearchProfiles.adapter = profileAdapter

        binding.etSearchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    delay(500)
                    performSearch(s.toString())
                }
            }
        })

        binding.tabSearchMode.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                if (tab?.position == 0) {
                    binding.rvSearchVideos.visibility = View.VISIBLE
                    binding.rvSearchProfiles.visibility = View.GONE
                } else {
                    binding.rvSearchVideos.visibility = View.GONE
                    binding.rvSearchProfiles.visibility = View.VISIBLE
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun performSearch(query: String) {
        if (query.isEmpty()) {
            binding.tvEmptySearch.visibility = View.VISIBLE
            binding.tvEmptySearch.text = "Gunakan kata kunci untuk mencari"
            videoAdapter.submitList(emptyList())
            profileAdapter.submitList(emptyList())
            return
        }

        binding.pbSearch.visibility = View.VISIBLE
        binding.tvEmptySearch.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Search Profiles by username
                val profiles = SupabaseClient.client.from("profiles")
                    .select {
                        filter {
                            ilike("username", "%$query%")
                        }
                    }.decodeList<ProfileDesc>()

                // Search Posts by caption or interest
                val posts = SupabaseClient.client.from("posts")
                    .select(columns = Columns.raw("*, profiles(username, avatar), post_interests(interest(id, interest))")) {
                        filter {
                            ilike("caption", "%$query%")
                        }
                    }.decodeList<Post>()
                    
                withContext(Dispatchers.Main) {
                    binding.pbSearch.visibility = View.GONE
                    profileAdapter.submitList(profiles)
                    videoAdapter.submitList(posts)
                    
                    if (profiles.isEmpty() && posts.isEmpty()) {
                        binding.tvEmptySearch.visibility = View.VISIBLE
                        binding.tvEmptySearch.text = "Tidak ada hasil ditemukan."
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    binding.pbSearch.visibility = View.GONE
                }
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).roundToInt()
    }
}

// Temporary subclass to capture 'id' since Model.kt Profile lacks id
@kotlinx.serialization.Serializable
data class ProfileDesc(
    val id: String = "",
    val username: String? = null,
    val role: String? = null,
    val avatar: String? = null
)

class SearchVideoAdapter(
    private val onItemClick: (Post) -> Unit,
    private val onUserClick: (Post) -> Unit
) : RecyclerView.Adapter<SearchVideoAdapter.VideoViewHolder>() {
    private val items = mutableListOf<Post>()

    fun submitList(newItems: List<Post>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val binding = ItemSearchVideoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VideoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val item = items[position]
        holder.binding.tvCaption.text = item.caption ?: "Tanpa deskripsi"
        holder.binding.tvUsername.text = item.profiles?.username ?: "Unknown"

        if (item.mediaType == "video") {
            holder.binding.ivVideoIndicator.visibility = View.VISIBLE
            holder.binding.ivThumbnail.load(item.mediaUrl) {
                crossfade(true)
                videoFrameMillis(1000)
            }
        } else {
            holder.binding.ivVideoIndicator.visibility = View.GONE
            holder.binding.ivThumbnail.load(item.mediaUrl) {
                crossfade(true)
            }
        }
        
        val avatarUrl = SupabaseClient.client.storage["user_profiles"].publicUrl("avatar_${item.userId}")
        holder.binding.ivAvatar.load(avatarUrl) {
            error(R.drawable.pfp)
            size(100, 100) // downscale
        }
        
        holder.binding.root.setOnClickListener {
            onItemClick(item)
        }

        holder.binding.ivAvatar.setOnClickListener {
            onUserClick(item)
        }

        holder.binding.tvUsername.setOnClickListener {
            onUserClick(item)
        }
    }

    override fun getItemCount() = items.size
    
    inner class VideoViewHolder(val binding: ItemSearchVideoBinding) : RecyclerView.ViewHolder(binding.root)
}

class SearchProfileAdapter(
    private val onProfileClick: (ProfileDesc) -> Unit
) : RecyclerView.Adapter<SearchProfileAdapter.ProfileViewHolder>() {
    private val items = mutableListOf<ProfileDesc>()

    fun submitList(newItems: List<ProfileDesc>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProfileViewHolder {
        val binding = ItemSearchProfileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ProfileViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProfileViewHolder, position: Int) {
        val item = items[position]
        holder.binding.tvUsername.text = item.username ?: "Unknown"
        holder.binding.tvRole.text = item.role ?: "Siswa"
        holder.binding.btnFollow.visibility = View.GONE
        
        val avatarUrl = SupabaseClient.client.storage["user_profiles"].publicUrl("avatar_${item.id}")
        holder.binding.ivUserAvatar.load(avatarUrl) {
            error(R.drawable.pfp)
            size(150, 150)
        }

        holder.binding.root.setOnClickListener {
            onProfileClick(item)
        }
    }

    override fun getItemCount() = items.size
    
    inner class ProfileViewHolder(val binding: ItemSearchProfileBinding) : RecyclerView.ViewHolder(binding.root)
}
