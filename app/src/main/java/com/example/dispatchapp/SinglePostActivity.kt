package com.example.dispatchapp

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.dispatchapp.adapters.ShowcaseReelAdapter
import com.example.dispatchapp.databinding.ActivitySinglePostBinding
import com.example.dispatchapp.models.Post
import com.example.dispatchapp.models.PostComment
import com.example.dispatchapp.models.PostLike
import com.example.dispatchapp.models.SavedPost
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SinglePostActivity : BaseActivity() {

    private lateinit var binding: ActivitySinglePostBinding
    private lateinit var adapter: ShowcaseReelAdapter
    private var currentUserId: String? = null
    private var currentStudentId: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySinglePostBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = UserPreferences(this)
        currentUserId = SupabaseClient.client.auth.currentSessionOrNull()?.user?.id
        currentStudentId = prefs.getStudentId()?.toLongOrNull()

        binding.ivBack.setOnClickListener { finish() }

        adapter = ShowcaseReelAdapter(
            currentUserId = currentUserId ?: "",
            onLikeClick = { post, isLiked, callback ->
                handleLike(post, isLiked, callback)
            },
            onCommentClick = { post ->
                val sheet = CommentsBottomSheet.newInstance(post.id, currentUserId ?: "", currentStudentId ?: -1L)
                sheet.setOnCommentChangedListener {
                    // Fetch latest comment count securely
                    lifecycleScope.launch(Dispatchers.IO) {
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
                sheet.show(supportFragmentManager, "CommentsBottomSheet")
            },
            onSaveClick = { post, isSaved, callback ->
                handleSave(post, isSaved, callback)
            },
            onUserClick = { post ->
                startActivity(UserProfileActivity.createIntent(this, post.userId))
            }
        )
        binding.viewPagerSingleReel.adapter = adapter

        val postId = intent.getLongExtra("POST_ID", -1)
        if (postId != -1L) {
            loadPost(postId)
        } else {
            Toast.makeText(this, "Postingan tidak ditemukan", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        if (::adapter.isInitialized) {
            adapter.pauseAll()
        }
    }

    private fun loadPost(postId: Long) {
        binding.pbLoading.visibility = View.VISIBLE
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Fetch single post
                val post = SupabaseClient.client.from("posts")
                    .select(columns = Columns.raw("*, profiles(username, avatar), students(student_name), post_interests(interest(id, interest))")) {
                        filter { eq("id", postId) }
                    }.decodeSingleOrNull<Post>()

                if (post == null) {
                    withContext(Dispatchers.Main) {
                        binding.pbLoading.visibility = View.GONE
                        Toast.makeText(this@SinglePostActivity, "Postingan tidak ditemukan", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    return@launch
                }

                // Fetch counts
                val likesData = SupabaseClient.client.from("post_likes")
                    .select(columns = Columns.raw("post_id, user_id")) {
                        filter { eq("post_id", postId) }
                    }.decodeList<PostLike>()

                val commentsData = SupabaseClient.client.from("post_comments")
                    .select(columns = Columns.raw("post_id")) {
                        filter { eq("post_id", postId) }
                    }.decodeList<PostComment>()
                    
                val savedData = SupabaseClient.client.from("saved_posts")
                    .select(columns = Columns.raw("post_id, user_id")) {
                        filter { 
                            eq("post_id", postId)
                            eq("user_id", currentUserId ?: "")
                        }
                    }.decodeList<SavedPost>()

                val likeCounts = mapOf(postId to likesData.size)
                val userLikes = mapOf(postId to likesData.any { it.userId == currentUserId })
                val userSaves = mapOf(postId to savedData.isNotEmpty())
                val commentCounts = mapOf(postId to commentsData.size)

                withContext(Dispatchers.Main) {
                    binding.pbLoading.visibility = View.GONE
                    adapter.submitList(listOf(post), likeCounts, commentCounts, userLikes, userSaves)
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    binding.pbLoading.visibility = View.GONE
                    Toast.makeText(this@SinglePostActivity, "Gagal memuat: ${e.message}", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    private fun handleLike(post: Post, isLiked: Boolean, updateCountCallback: (Int) -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) {
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
                
                val likesData = SupabaseClient.client.from("post_likes")
                    .select(columns = Columns.raw("post_id")) {
                        filter { eq("post_id", post.id) }
                    }.decodeList<PostLike>()
                    
                withContext(Dispatchers.Main) {
                    updateCountCallback(likesData.size)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun handleSave(post: Post, isSaved: Boolean, updateCountCallback: (Boolean) -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) {
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
            }
        }
    }
}