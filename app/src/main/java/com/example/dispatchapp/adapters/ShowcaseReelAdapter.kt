package com.example.dispatchapp.adapters

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.dispatchapp.R
import com.example.dispatchapp.SupabaseClient
import com.example.dispatchapp.databinding.ItemShowcaseReelBinding
import com.example.dispatchapp.models.Post
import io.github.jan.supabase.storage.storage
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.MediaItem

class ShowcaseReelAdapter(
    private val currentUserId: String?,
    private val onLikeClick: (Post, Boolean, (Int) -> Unit) -> Unit, // post, isLiked, callback to update count UI
    private val onCommentClick: (Post) -> Unit,
    private val onSaveClick: (Post, Boolean, (Boolean) -> Unit) -> Unit, // post, isSaved, callback to update save UI
    private val onUserClick: (Post) -> Unit
) : RecyclerView.Adapter<ShowcaseReelAdapter.ReelViewHolder>() {

    private val posts = mutableListOf<Post>()
    // post_id to likes count
    private val likeCounts = mutableMapOf<Long, Int>()
    // post_id to comment count
    private val commentCounts = mutableMapOf<Long, Int>()
    // post_id -> isLiked by current user
    private val userLikes = mutableMapOf<Long, Boolean>()
    // post_id -> isSaved by current user
    private val userSaves = mutableMapOf<Long, Boolean>()

    fun submitList(
        newPosts: List<Post>, 
        newLikeCounts: Map<Long, Int>, 
        newCommentCounts: Map<Long, Int>, 
        newUserLikes: Map<Long, Boolean>,
        newUserSaves: Map<Long, Boolean>
    ) {
        posts.clear()
        posts.addAll(newPosts)
        likeCounts.clear()
        likeCounts.putAll(newLikeCounts)
        commentCounts.clear()
        commentCounts.putAll(newCommentCounts)
        userLikes.clear()
        userLikes.putAll(newUserLikes)
        userSaves.clear()
        userSaves.putAll(newUserSaves)
        notifyDataSetChanged()
    }

    fun getPostIndex(postId: Long): Int {
        return posts.indexOfFirst { it.id == postId }
    }

    fun updateCommentCount(postId: Long, newCount: Int) {
        commentCounts[postId] = newCount
        val index = getPostIndex(postId)
        if (index != -1) {
            notifyItemChanged(index, "COMMENT_COUNT_UPDATE")
        }
    }

    override fun onBindViewHolder(holder: ReelViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            for (payload in payloads) {
                if (payload == "COMMENT_COUNT_UPDATE") {
                    val post = posts[position]
                    holder.binding.tvCommentCount.text = (commentCounts[post.id] ?: 0).toString()
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReelViewHolder {
        val binding = ItemShowcaseReelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ReelViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ReelViewHolder, position: Int) {
        holder.bind(posts[position])
    }

    private val activePlayers = mutableSetOf<ExoPlayer>()

    override fun getItemCount() = posts.size

    override fun onViewAttachedToWindow(holder: ReelViewHolder) {
        super.onViewAttachedToWindow(holder)
        holder.player?.let { player ->
            player.seekTo(0)
            player.play()
        }
    }

    override fun onViewDetachedFromWindow(holder: ReelViewHolder) {
        super.onViewDetachedFromWindow(holder)
        holder.player?.pause()
    }

    override fun onViewRecycled(holder: ReelViewHolder) {
        super.onViewRecycled(holder)
        holder.player?.let {
            activePlayers.remove(it)
            it.release()
        }
        holder.player = null
        holder.binding.pvReelMedia.player = null
    }

    fun pauseAll() {
        activePlayers.forEach { it.pause() }
    }

    inner class ReelViewHolder(val binding: ItemShowcaseReelBinding) : RecyclerView.ViewHolder(binding.root) {

        // ExoPlayer instance
        var player: ExoPlayer? = null

        fun bind(post: Post) {
            // User info
            binding.tvUsername.text = post.profiles?.username ?: "Unknown User"
            
            // Avatar
            val avatarUrl = SupabaseClient.client.storage["user_profiles"].publicUrl("avatar_${post.userId}")
            binding.ivAvatar.load(avatarUrl) {
                crossfade(true)
                error(R.drawable.pfp)
                size(150, 150)
            }

            // Student Verification Badge badge
            binding.ivVerifiedBadge.visibility = View.VISIBLE

            // Interest Tag
            if (!post.post_interests.isNullOrEmpty()) {
                binding.tvInterestTag.visibility = View.VISIBLE
                val interestNames = post.post_interests
                    .mapNotNull { it.interest?.interest }
                    .filter { it.isNotBlank() }

                if (interestNames.isEmpty()) {
                    binding.tvInterestTag.text = "Unknown"
                    binding.tvInterestTag.setOnClickListener(null)
                } else if (interestNames.size == 1) {
                    binding.tvInterestTag.text = "${interestNames.first()}"
                    binding.tvInterestTag.setOnClickListener(null)
                } else {
                    var isExpandedInterests = false

                    fun updateInterestTag() {
                        if (isExpandedInterests) {
                            binding.tvInterestTag.text = "${interestNames.joinToString(" • ")}"
                        } else {
                            binding.tvInterestTag.text = "${interestNames.first()} +${interestNames.size - 1}"
                        }
                    }

                    updateInterestTag()
                    binding.tvInterestTag.setOnClickListener {
                        isExpandedInterests = !isExpandedInterests
                        updateInterestTag()
                    }
                }
            } else {
                binding.tvInterestTag.visibility = View.GONE
                binding.tvInterestTag.setOnClickListener(null)
            }

            // Caption
            val hasCaption = !post.caption.isNullOrBlank()
            val hasDesc = !post.projectDescription.isNullOrBlank()
            
            if (!hasCaption && !hasDesc) {
                binding.tvCaption.visibility = View.GONE
            } else {
                binding.tvCaption.visibility = View.VISIBLE
                val shortText = post.caption ?: "Deskripsi Proyek"
                val fullText = buildString {
                    if (hasCaption) append(post.caption)
                    if (hasCaption && hasDesc) append("\n\n")
                    if (hasDesc) {
                        append("Deskripsi Lengkap:\n")
                        append(post.projectDescription)
                    }
                }
                
                binding.tvCaption.text = shortText
                
                var isExpanded = false
                binding.tvCaption.setOnClickListener {
                    isExpanded = !isExpanded
                    if (isExpanded) {
                        binding.tvCaption.maxLines = Integer.MAX_VALUE
                        binding.tvCaption.ellipsize = null
                        binding.tvCaption.text = fullText
                    } else {
                        binding.tvCaption.maxLines = 2
                        binding.tvCaption.ellipsize = android.text.TextUtils.TruncateAt.END
                        binding.tvCaption.text = shortText
                    }
                }
            }

            // Timestamp
            binding.tvTimestamp.text = getRelativeTime(post.createdAt)

            // Counts
            val likes = likeCounts[post.id] ?: 0
            val comments = commentCounts[post.id] ?: 0
            var isLiked = userLikes[post.id] ?: false
            var isSaved = userSaves[post.id] ?: false

            binding.tvLikeCount.text = likes.toString()
            binding.tvCommentCount.text = comments.toString()

            updateLikeUi(isLiked)
            updateSaveUi(isSaved)

            // Media
            if (post.mediaType == "video") {
                binding.ivReelMedia.visibility = View.GONE
                binding.pvReelMedia.visibility = View.VISIBLE
                
                // Initialize ExoPlayer
                if (player == null) {
                    player = ExoPlayer.Builder(binding.root.context).build()
                    binding.pvReelMedia.player = player
                    activePlayers.add(player!!)
                }
                
                player?.let {
                    it.setMediaItem(androidx.media3.common.MediaItem.fromUri(post.mediaUrl))
                    it.repeatMode = ExoPlayer.REPEAT_MODE_ALL
                    it.prepare()
                    it.playWhenReady = false // Will be started in onViewAttachedToWindow
                }

                var isSpeedBoosting = false
                binding.pvReelMedia.setOnClickListener {
                    val activePlayer = player ?: return@setOnClickListener
                    if (activePlayer.isPlaying) {
                        activePlayer.pause()
                    } else {
                        activePlayer.play()
                    }
                }
                binding.pvReelMedia.setOnLongClickListener {
                    val activePlayer = player ?: return@setOnLongClickListener false
                    activePlayer.playbackParameters = PlaybackParameters(2f)
                    isSpeedBoosting = true
                    true
                }
                binding.pvReelMedia.setOnTouchListener { _, event ->
                    if (isSpeedBoosting && (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL)) {
                        player?.playbackParameters = PlaybackParameters(1f)
                        isSpeedBoosting = false
                    }
                    false
                }
            } else {
                binding.pvReelMedia.visibility = View.GONE
                binding.ivReelMedia.visibility = View.VISIBLE
                binding.pvReelMedia.setOnClickListener(null)
                binding.pvReelMedia.setOnLongClickListener(null)
                binding.pvReelMedia.setOnTouchListener(null)
                // Release player if switching types in recycled view
                player?.let {
                    activePlayers.remove(it)
                    it.release()
                }
                player = null
                binding.pvReelMedia.player = null
                
                binding.ivReelMedia.load(post.mediaUrl) {
                    crossfade(true)
                }
            }

            // Actions
            binding.ivLike.setOnClickListener {
                if (currentUserId == null) return@setOnClickListener
                
                isLiked = !isLiked
                userLikes[post.id] = isLiked
                updateLikeUi(isLiked)
                
                // Call back and get updated count
                onLikeClick(post, isLiked) { newCount ->
                    likeCounts[post.id] = newCount
                    binding.tvLikeCount.text = newCount.toString()
                }
            }

            binding.ivComment.setOnClickListener {
                onCommentClick(post)
            }

            binding.ivAvatar.setOnClickListener {
                onUserClick(post)
            }

            binding.tvUsername.setOnClickListener {
                onUserClick(post)
            }

            binding.ivBookmark.setOnClickListener {
                if (currentUserId == null) return@setOnClickListener
                
                isSaved = !isSaved
                userSaves[post.id] = isSaved
                updateSaveUi(isSaved)
                
                onSaveClick(post, isSaved) { resultState ->
                    // Optional callback if it failed
                }
            }
        }
        
        private fun updateSaveUi(isSaved: Boolean) {
            if (isSaved) {
                binding.ivBookmark.setImageResource(android.R.drawable.ic_menu_save) // Fill icon? Using same for simplicity or tint yellow
                binding.ivBookmark.setColorFilter(android.graphics.Color.parseColor("#FBBF24")) // Yellow
                binding.tvBookmarkCount.text = "Tersimpan"
            } else {
                binding.ivBookmark.setImageResource(android.R.drawable.ic_menu_save)
                binding.ivBookmark.setColorFilter(android.graphics.Color.WHITE)
                binding.tvBookmarkCount.text = "Simpan"
            }
        }
        
        private fun updateLikeUi(isLiked: Boolean) {
            if (isLiked) {
                binding.ivLike.setImageResource(R.drawable.ic_heart)
                binding.ivLike.setColorFilter(android.graphics.Color.parseColor("#EF4444")) // Red
            } else {
                binding.ivLike.setImageResource(R.drawable.ic_heart)
                binding.ivLike.setColorFilter(android.graphics.Color.WHITE)
            }
        }

        private fun getRelativeTime(dateString: String?): String {
            if (dateString == null) return ""
            return try {
                val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                format.timeZone = TimeZone.getTimeZone("UTC")
                val date = format.parse(dateString)
                date?.let {
                    DateUtils.getRelativeTimeSpanString(it.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
                } ?: ""
            } catch (e: Exception) {
                ""
            }
        }
    }
}
