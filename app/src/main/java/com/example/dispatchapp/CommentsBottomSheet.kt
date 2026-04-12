package com.example.dispatchapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.example.dispatchapp.adapters.CommentAdapter
import com.example.dispatchapp.databinding.DialogCommentsBinding
import com.example.dispatchapp.models.PostComment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class CommentsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: DialogCommentsBinding? = null
    private val binding get() = _binding!!

    private var postId: Long = -1
    private var currentUserId: String = ""
    private var studentId: Long = -1 // -1 means not a verified student
    private lateinit var adapter: CommentAdapter

    private var replyingToComment: PostComment? = null
    private var onCommentAddedOrDeleted: (() -> Unit)? = null
    private var sortNewestFirst = false

    fun setOnCommentChangedListener(listener: () -> Unit) {
        this.onCommentAddedOrDeleted = listener
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogCommentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        val d = dialog as? BottomSheetDialog
        val bottomSheet = d?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        if (bottomSheet != null) {
            val screenHeight = resources.displayMetrics.heightPixels
            bottomSheet.layoutParams.height = (screenHeight * 0.62f).toInt()
            val behavior = BottomSheetBehavior.from(bottomSheet)
            behavior.skipCollapsed = true
            behavior.isDraggable = false
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }

        d?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        postId = arguments?.getLong("post_id") ?: -1
        currentUserId = arguments?.getString("current_user_id") ?: ""
        studentId = arguments?.getLong("student_id") ?: -1

        setupUI()
        fetchComments()
    }

    private fun setupUI() {
        adapter = CommentAdapter(
            currentUserId, 
            onReplyClick = { comment -> setupReplyState(comment) },
            onDeleteClick = { comment -> deleteComment(comment) }
        )
        binding.rvComments.layoutManager = LinearLayoutManager(requireContext())
        binding.rvComments.adapter = adapter
        binding.tvCommentsTitle.text = "0 komentar"

        binding.ivCloseComments.setOnClickListener {
            dismiss()
        }

        binding.ivSortComments.setOnClickListener {
            sortNewestFirst = !sortNewestFirst
            val text = if (sortNewestFirst) "Filter: terbaru" else "Filter: terlama"
            Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT).show()
            fetchComments()
        }

        // Only verified students can comment
        if (studentId != -1L && currentUserId.isNotEmpty()) {
            binding.layoutBottom.visibility = View.VISIBLE
            binding.tvNotEligible.visibility = View.GONE
            
            // Load user own avatar
            val avatarUrl = SupabaseClient.client.storage["user_profiles"].publicUrl("avatar_$currentUserId") + "?t=${System.currentTimeMillis()}"
            binding.ivMyAvatar.load(avatarUrl) {
                crossfade(true)
                error(R.drawable.pfp)
            }
        } else {
            binding.layoutBottom.visibility = View.GONE
            binding.tvNotEligible.visibility = View.VISIBLE
        }

        binding.btnSendComment.setOnClickListener {
            val content = binding.etComment.text.toString().trim()
            if (content.isNotEmpty()) {
                submitComment(content)
            }
        }

        binding.ivCancelReply.setOnClickListener {
            clearReplyState()
        }
    }

    private fun setupReplyState(comment: PostComment) {
        replyingToComment = comment.copy(id = comment.id)
        
        val myName = UserPreferences(requireContext()).getUserName()?.ifBlank { "Saya" } ?: "Saya"
        val targetUsername = comment.profiles?.username ?: "User"
        binding.layoutReplyContext.visibility = View.VISIBLE
        binding.tvReplyContext.text = "$myName > $targetUsername"
        binding.etComment.requestFocus()
    }

    private fun clearReplyState() {
        replyingToComment = null
        binding.layoutReplyContext.visibility = View.GONE
        binding.tvReplyContext.text = ""
    }

    private fun fetchComments() {
        binding.pbComments.visibility = View.VISIBLE
        binding.tvEmptyComments.visibility = View.GONE
        
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Fetch comments with profile join
                val response = SupabaseClient.client.from("post_comments")
                    .select(columns = Columns.raw("*, profiles(username, avatar)")) {
                        filter { eq("post_id", postId) }
                        order("created_at", if (sortNewestFirst) Order.DESCENDING else Order.ASCENDING)
                    }.decodeList<PostComment>()

                withContext(Dispatchers.Main) {
                    binding.pbComments.visibility = View.GONE
                    binding.tvCommentsTitle.text = "${response.size} komentar"
                    if (response.isEmpty()) {
                        binding.tvEmptyComments.visibility = View.VISIBLE
                    } else {
                        binding.tvEmptyComments.visibility = View.GONE
                        
                        val sortedSource = if (sortNewestFirst) {
                            response.sortedByDescending { it.createdAt ?: "" }
                        } else {
                            response.sortedBy { it.createdAt ?: "" }
                        }

                        val childrenByParent = sortedSource
                            .filter { it.parentId != null }
                            .groupBy { it.parentId!! }

                        val sortedList = mutableListOf<PostComment>()
                        val roots = sortedSource.filter { it.parentId == null }

                        fun addWithChildren(node: PostComment) {
                            sortedList.add(node)
                            val children = childrenByParent[node.id].orEmpty()
                            children.forEach { child -> addWithChildren(child) }
                        }

                        roots.forEach { root -> addWithChildren(root) }
                        
                        adapter.setComments(sortedList)
                        if (sortedList.isNotEmpty()) {
                            val targetPosition = if (sortNewestFirst) 0 else sortedList.size - 1
                            binding.rvComments.scrollToPosition(targetPosition)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    binding.pbComments.visibility = View.GONE
                    Toast.makeText(context, "Gagal memuat: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun submitComment(content: String) {
        binding.etComment.isEnabled = false
        binding.btnSendComment.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val parentIdToInsert = replyingToComment?.id

                SupabaseClient.client.from("post_comments")
                    .insert(buildJsonObject {
                        put("post_id", postId)
                        put("user_id", currentUserId)
                        put("student_id", studentId)
                        put("content", content)
                        if (parentIdToInsert != null && parentIdToInsert > 0) {
                            put("parent_id", parentIdToInsert)
                        }
                    })

                withContext(Dispatchers.Main) {
                    binding.etComment.text.clear()
                    binding.etComment.isEnabled = true
                    binding.btnSendComment.isEnabled = true
                    clearReplyState()
                    fetchComments() // Reload comments to show the new one
                    onCommentAddedOrDeleted?.invoke()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    binding.etComment.isEnabled = true
                    binding.btnSendComment.isEnabled = true
                    Toast.makeText(context, "Gagal komentar: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun deleteComment(comment: PostComment) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                SupabaseClient.client.from("post_comments").delete {
                    filter { eq("id", comment.id) }
                }
                withContext(Dispatchers.Main) {
                    fetchComments()
                    onCommentAddedOrDeleted?.invoke()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Gagal menghapus", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(postId: Long, currentUserId: String, studentId: Long): CommentsBottomSheet {
            val fragment = CommentsBottomSheet()
            val args = Bundle().apply {
                putLong("post_id", postId)
                putString("current_user_id", currentUserId)
                putLong("student_id", studentId)
            }
            fragment.arguments = args
            return fragment
        }
    }
}
