package com.example.dispatchapp.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.dispatchapp.CommentsBottomSheet
import com.example.dispatchapp.CreatePostActivity
import com.example.dispatchapp.UserProfileActivity
import com.example.dispatchapp.SupabaseClient
import com.example.dispatchapp.UserPreferences
import com.example.dispatchapp.adapters.ShowcaseReelAdapter
import com.example.dispatchapp.databinding.FragmentShowcaseBinding
import com.example.dispatchapp.models.Post
import com.example.dispatchapp.models.PostComment
import com.example.dispatchapp.models.PostLike
import com.example.dispatchapp.models.SavedPost
import com.example.dispatchapp.SearchReelsActivity
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ShowcaseFragment : Fragment() {

    private var _binding: FragmentShowcaseBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ShowcaseReelAdapter
    private var currentUserId: String? = null
    private var currentStudentId: Long? = null
    private var targetPostId: Long? = null

    fun scrollToPost(postId: Long) {
        targetPostId = postId
        if (::adapter.isInitialized && adapter.itemCount > 0) {
            val index = adapter.getPostIndex(postId)
            if (index != -1) {
                binding.viewPagerReels.setCurrentItem(index, false)
                targetPostId = null
            }
        }
    }

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

        val prefs = UserPreferences(requireContext())
        currentUserId = SupabaseClient.client.auth.currentSessionOrNull()?.user?.id
        
        // If student is verified, we should be able to get their student_id
        val storedStudentId = prefs.getStudentId()
        if (!storedStudentId.isNullOrEmpty()) {
            currentStudentId = storedStudentId.toLongOrNull()
        }

        // Setup Create Post button
        if (currentStudentId != null) {
            binding.fabCreatePost.visibility = View.VISIBLE
        } else {
            binding.fabCreatePost.visibility = View.GONE
        }

        binding.fabCreatePost.setOnClickListener {
            startActivity(Intent(requireContext(), CreatePostActivity::class.java))
        }

        binding.ivSearch.setOnClickListener {
            startActivity(Intent(requireContext(), SearchReelsActivity::class.java))
        }

        adapter = ShowcaseReelAdapter(
            currentUserId = currentUserId ?: "",
            onLikeClick = { post, isLiked, callback ->
                handleLike(post, isLiked, callback)
            },
            onCommentClick = { post ->
                val sheet = CommentsBottomSheet.newInstance(post.id, currentUserId ?: "", currentStudentId ?: -1L)
                sheet.setOnCommentChangedListener {
                    // Fetch latest comment count securely
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val commentsData = SupabaseClient.client.from("post_comments")
                                .select(columns = Columns.raw("post_id")) {
                                    filter { eq("post_id", post.id) }
                                }.decodeList<PostComment>()
                            
                            withContext(Dispatchers.Main) {
                                adapter.updateCommentCount(post.id, commentsData.size)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                sheet.show(parentFragmentManager, "CommentsBottomSheet")
            },
            onSaveClick = { post, isSaved, callback ->
                handleSave(post, isSaved, callback)
            },
            onUserClick = { post ->
                startActivity(UserProfileActivity.createIntent(requireContext(), post.userId))
            }
        )
        binding.viewPagerReels.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        // Reload points every time view is visible (to catch new posts if user uploaded)
        loadPosts()
    }

    override fun onPause() {
        super.onPause()
        if (::adapter.isInitialized) {
            adapter.pauseAll()
        }
    }

    private fun loadPosts() {
        binding.pbShowcase.visibility = View.VISIBLE
        binding.tvEmptyShowcase.visibility = View.GONE
        
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Fetch posts with nested joined profile info
                val posts = SupabaseClient.client.from("posts")
                    .select(columns = Columns.raw("*, profiles(username, avatar), students(student_name), post_interests(interest(id, interest))")) {
                        order("created_at", Order.DESCENDING)
                    }.decodeList<Post>()

                if (posts.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        binding.pbShowcase.visibility = View.GONE
                        binding.tvEmptyShowcase.visibility = View.VISIBLE
                        adapter.submitList(emptyList(), emptyMap(), emptyMap(), emptyMap(), emptyMap())
                    }
                    return@launch
                }

                // Compile all IDs to do IN queries for likes/comments
                val postIds = posts.map { it.id }

                // Fetch likes counts by fetching all and grouping, or specific counts
                // For simplicity, we just fetch all likes for these posts matching IDs
                val likesData = SupabaseClient.client.from("post_likes")
                    .select(columns = Columns.raw("post_id, user_id")) {
                        filter { isIn("post_id", postIds) }
                    }.decodeList<PostLike>()

                val commentsData = SupabaseClient.client.from("post_comments")
                    .select(columns = Columns.raw("post_id")) {
                        filter { isIn("post_id", postIds) }
                    }.decodeList<PostComment>()
                    
                val savedData = SupabaseClient.client.from("saved_posts")
                    .select(columns = Columns.raw("post_id, user_id")) {
                        filter { 
                            isIn("post_id", postIds)
                            eq("user_id", currentUserId ?: "")
                        }
                    }.decodeList<SavedPost>()

                // Group
                val likeCounts = mutableMapOf<Long, Int>()
                val userLikes = mutableMapOf<Long, Boolean>()
                likesData.forEach {
                    likeCounts[it.postId] = (likeCounts[it.postId] ?: 0) + 1
                    if (it.userId == currentUserId) {
                        userLikes[it.postId] = true
                    }
                }
                
                val userSaves = mutableMapOf<Long, Boolean>()
                savedData.forEach {
                    userSaves[it.postId] = true
                }

                val commentCounts = mutableMapOf<Long, Int>()
                commentsData.forEach {
                    commentCounts[it.postId] = (commentCounts[it.postId] ?: 0) + 1
                }

                withContext(Dispatchers.Main) {
                    binding.pbShowcase.visibility = View.GONE
                    adapter.submitList(posts, likeCounts, commentCounts, userLikes, userSaves)
                    
                    targetPostId?.let { id ->
                        val index = adapter.getPostIndex(id)
                        if (index != -1) {
                            binding.viewPagerReels.setCurrentItem(index, false)
                        }
                        targetPostId = null
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    binding.pbShowcase.visibility = View.GONE
                    Toast.makeText(context, "Gagal: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun handleLike(post: Post, isLiked: Boolean, updateCountCallback: (Int) -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (isLiked) {
                    SupabaseClient.client.from("post_likes")
                        .insert(buildJsonObject {
                            put("post_id", post.id)
                            put("user_id", currentUserId ?: "")
                        })
                } else {
                    SupabaseClient.client.from("post_likes").delete {
                        filter { 
                            eq("post_id", post.id)
                            eq("user_id", currentUserId ?: "")
                        }
                    }
                }
                
                // Re-fetch count securely
                val likesData = SupabaseClient.client.from("post_likes")
                    .select(columns = Columns.raw("post_id")) {
                        filter { eq("post_id", post.id) }
                    }.decodeList<PostLike>()
                    
                withContext(Dispatchers.Main) {
                    updateCountCallback(likesData.size)
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Gagal sinkronisasi like", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun handleSave(post: Post, isSaved: Boolean, updateCountCallback: (Boolean) -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (isSaved) {
                    SupabaseClient.client.from("saved_posts")
                        .insert(buildJsonObject {
                            put("post_id", post.id)
                            put("user_id", currentUserId ?: "")
                        })
                } else {
                    SupabaseClient.client.from("saved_posts").delete {
                        filter { 
                            eq("post_id", post.id)
                            eq("user_id", currentUserId ?: "")
                        }
                    }
                }
                
                withContext(Dispatchers.Main) {
                    updateCountCallback(isSaved)
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Gagal sinkronisasi penyimpanan", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
