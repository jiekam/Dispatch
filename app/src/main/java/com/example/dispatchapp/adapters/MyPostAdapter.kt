package com.example.dispatchapp.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.videoFrameMillis
import com.example.dispatchapp.databinding.ItemMyPostBinding
import com.example.dispatchapp.models.Post
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class MyPostAdapter(
    private val onItemClick: (Post) -> Unit,
    private val onDeleteClick: (Post) -> Unit,
    private val showDeleteButton: Boolean = true
) : RecyclerView.Adapter<MyPostAdapter.MyPostViewHolder>() {

    private val posts = mutableListOf<Post>()
    private val likeCounts = mutableMapOf<Long, Int>()
    private val commentCounts = mutableMapOf<Long, Int>()

    fun setPosts(newPosts: List<Post>, newLikes: Map<Long, Int>, newComments: Map<Long, Int>) {
        posts.clear()
        posts.addAll(newPosts)
        likeCounts.clear()
        likeCounts.putAll(newLikes)
        commentCounts.clear()
        commentCounts.putAll(newComments)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyPostViewHolder {
        val binding = ItemMyPostBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MyPostViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MyPostViewHolder, position: Int) {
        holder.bind(posts[position])
    }

    override fun getItemCount() = posts.size

    inner class MyPostViewHolder(private val binding: ItemMyPostBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(post: Post) {
            binding.tvMyPostCaption.text = if (post.caption.isNullOrBlank()) "Tanpa caption" else post.caption
            
            val likes = likeCounts[post.id] ?: 0
            val comments = commentCounts[post.id] ?: 0
            
            binding.tvMyPostLikes.text = likes.toString()
            binding.tvMyPostComments.text = comments.toString()
            
            binding.tvMyPostDate.text = formatDate(post.createdAt)

            if (post.mediaType == "video") {
                binding.ivMyPostVideoIcon.visibility = View.VISIBLE
                binding.ivMyPostThumb.load(post.mediaUrl) {
                    crossfade(true)
                    // Request frame at 1 millisecond
                    videoFrameMillis(1000)
                }
            } else {
                binding.ivMyPostVideoIcon.visibility = View.GONE
                binding.ivMyPostThumb.load(post.mediaUrl) {
                    crossfade(true)
                }
            }

            binding.btnDeletePost.visibility = if (showDeleteButton) View.VISIBLE else View.GONE
            if (showDeleteButton) {
                binding.btnDeletePost.setOnClickListener {
                    onDeleteClick(post)
                }
            } else {
                binding.btnDeletePost.setOnClickListener(null)
            }

            binding.root.setOnClickListener {
                onItemClick(post)
            }
        }

        private fun formatDate(dateString: String?): String {
            if (dateString == null) return ""
            return try {
                val formatUtc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                formatUtc.timeZone = TimeZone.getTimeZone("UTC")
                val date = formatUtc.parse(dateString)
                
                val formatLocal = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                formatLocal.timeZone = TimeZone.getDefault()
                
                date?.let { formatLocal.format(it) } ?: ""
            } catch (e: Exception) {
                ""
            }
        }
    }
}
