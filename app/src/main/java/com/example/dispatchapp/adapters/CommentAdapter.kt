package com.example.dispatchapp.adapters

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.dispatchapp.R
import com.example.dispatchapp.SupabaseClient
import com.example.dispatchapp.databinding.ItemCommentBinding
import com.example.dispatchapp.models.PostComment
import io.github.jan.supabase.storage.storage
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class CommentAdapter(
    private val currentUserId: String?,
    private val onReplyClick: (PostComment) -> Unit,
    private val onDeleteClick: (PostComment) -> Unit
) : RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {

    private val comments = mutableListOf<PostComment>()
    private val commentUsernames = mutableMapOf<Long, String>()

    fun setComments(newComments: List<PostComment>) {
        comments.clear()
        comments.addAll(newComments)
        commentUsernames.clear()
        newComments.forEach { comment ->
            val username = comment.profiles?.username?.ifBlank { "User" } ?: "User"
            commentUsernames[comment.id] = username
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val binding = ItemCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CommentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.bind(comments[position])
    }

    override fun getItemCount() = comments.size

    inner class CommentViewHolder(private val binding: ItemCommentBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(comment: PostComment) {
            val currentUsername = comment.profiles?.username?.ifBlank { "User" } ?: "User"
            val parentUsername = comment.parentId?.let { parentId ->
                commentUsernames[parentId]
            }
            binding.tvCommentUsername.text = if (parentUsername != null) {
                "$currentUsername > $parentUsername"
            } else {
                currentUsername
            }
            binding.tvCommentContent.text = comment.content
            binding.tvCommentTime.text = getRelativeTimeShort(comment.createdAt)

            val avatarUrl = SupabaseClient.client.storage["user_profiles"].publicUrl("avatar_${comment.userId}")
            binding.ivCommentAvatar.load(avatarUrl) {
                crossfade(true)
                error(R.drawable.pfp)
            }

            // Indent if it's a reply child
            val params = binding.root.layoutParams as ViewGroup.MarginLayoutParams
            if (comment.parentId != null) {
                params.marginStart = 56
            } else {
                params.marginStart = 0
            }
            binding.root.layoutParams = params

            if (comment.userId == currentUserId) {
                binding.ivDeleteComment.visibility = View.VISIBLE
                binding.ivDeleteComment.setOnClickListener {
                    onDeleteClick(comment)
                }
            } else {
                binding.ivDeleteComment.visibility = View.GONE
            }

            binding.tvReplyBtn.setOnClickListener {
                onReplyClick(comment)
            }
        }

        private fun getRelativeTimeShort(dateString: String?): String {
            if (dateString == null) return ""
            return try {
                val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                format.timeZone = TimeZone.getTimeZone("UTC")
                val date = format.parse(dateString)
                date?.let {
                    val diff = System.currentTimeMillis() - it.time
                    when {
                        diff < DateUtils.MINUTE_IN_MILLIS -> "baru saja"
                        diff < DateUtils.HOUR_IN_MILLIS -> "${diff / DateUtils.MINUTE_IN_MILLIS}m"
                        diff < DateUtils.DAY_IN_MILLIS -> "${diff / DateUtils.HOUR_IN_MILLIS}j"
                        diff < 7 * DateUtils.DAY_IN_MILLIS -> "${diff / DateUtils.DAY_IN_MILLIS}h"
                        else -> DateUtils.getRelativeTimeSpanString(it.time, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS).toString()
                    }
                } ?: ""
            } catch (e: Exception) {
                ""
            }
        }
    }
}
