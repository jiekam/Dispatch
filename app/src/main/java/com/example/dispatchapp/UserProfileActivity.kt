package com.example.dispatchapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import coil.ImageLoader
import coil.load
import com.example.dispatchapp.adapters.MyPostAdapter
import com.example.dispatchapp.databinding.FragmentProfileBinding
import com.example.dispatchapp.models.Post
import com.example.dispatchapp.models.PostComment
import com.example.dispatchapp.models.PostLike
import com.example.dispatchapp.models.Student
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class UserProfileActivity : BaseActivity() {

    private lateinit var profileBinding: FragmentProfileBinding
    private lateinit var postsAdapter: MyPostAdapter
    private var targetUserId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_profile)

        targetUserId = intent.getStringExtra(EXTRA_USER_ID).orEmpty()
        if (targetUserId.isBlank()) {
            Toast.makeText(this, "User tidak ditemukan", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        profileBinding = FragmentProfileBinding.bind(findViewById(R.id.profileContent))
        findViewById<ImageView>(R.id.ivBack).setOnClickListener { finish() }

        setupReadOnlyUi()
        setupPostsList()
        loadUserProfile()
        loadProfileImages()
        loadUserPosts()
    }

    private fun setupReadOnlyUi() {
        profileBinding.btnEditName.visibility = View.GONE
        profileBinding.btnEditInterest.visibility = View.GONE

        profileBinding.tvProfileEmail.visibility = View.GONE

        profileBinding.tvSettingsSection.visibility = View.GONE
        profileBinding.cardSettings.visibility = View.GONE
        profileBinding.tvSecuritySection.visibility = View.GONE
        profileBinding.cardSecurity.visibility = View.GONE
        profileBinding.btnLogout.visibility = View.GONE

        profileBinding.cardNotVerified.visibility = View.GONE
        profileBinding.btnVerifyStudent.visibility = View.GONE

        profileBinding.switchDarkMode.isEnabled = false

        profileBinding.tabLayoutPosts.visibility = View.VISIBLE
        if (profileBinding.tabLayoutPosts.tabCount > 1) {
            profileBinding.tabLayoutPosts.removeTabAt(1)
        }
    }

    private fun setupPostsList() {
        postsAdapter = MyPostAdapter(
            onItemClick = { post ->
                val intent = Intent(this, SinglePostActivity::class.java)
                intent.putExtra("POST_ID", post.id)
                startActivity(intent)
            },
            onDeleteClick = {},
            showDeleteButton = false
        )

        profileBinding.rvMyPosts.layoutManager = LinearLayoutManager(this)
        profileBinding.rvMyPosts.adapter = postsAdapter
        profileBinding.rvMyPosts.visibility = View.VISIBLE
    }

    private fun loadUserProfile() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val profile = SupabaseClient.client.from("profiles")
                    .select(columns = Columns.list("id, username, role")) {
                        filter { eq("id", targetUserId) }
                    }
                    .decodeSingleOrNull<ProfileDetail>()

                val student = SupabaseClient.client.from("students")
                    .select(columns = Columns.list("student_id, nis, kelas, jurusan, prodi, student_name")) {
                        filter { eq("id", targetUserId) }
                    }
                    .decodeSingleOrNull<Student>()

                withContext(Dispatchers.Main) {
                    profileBinding.tvProfileName.text = profile?.username ?: "Unknown"
                    profileBinding.chipRole.text = when (profile?.role) {
                        "student" -> "Siswa"
                        "organizer" -> "Organizer"
                        "student_informan" -> "Informan"
                        else -> profile?.role ?: "User"
                    }

                    if (student != null) {
                        profileBinding.cardInfoAkun.visibility = View.VISIBLE
                        profileBinding.ivVerifiedBadge.visibility = View.VISIBLE
                        profileBinding.tvNis.text = student.nis?.toString() ?: "—"
                        profileBinding.tvKelas.text = student.kelas ?: "—"
                        profileBinding.tvJurusan.text = student.jurusan ?: "—"
                        profileBinding.tvProdi.text = student.prodi ?: "—"

                        if (!student.studentName.isNullOrBlank()) {
                            profileBinding.tvStudentName.visibility = View.VISIBLE
                            profileBinding.tvStudentName.text = "📋 ${student.studentName} (Nama di Kartu Pelajar)"
                        } else {
                            profileBinding.tvStudentName.visibility = View.GONE
                        }

                        profileBinding.tvInterestSection.visibility = View.VISIBLE
                        profileBinding.cardInterest.visibility = View.VISIBLE
                        val studentIdInt = student.student_id?.toInt()
                        if (studentIdInt != null) {
                            loadAndDisplayInterests(studentIdInt)
                        } else {
                            profileBinding.tvNoInterest.visibility = View.VISIBLE
                            profileBinding.tvNoInterest.text = "Belum ada minat"
                        }
                    } else {
                        profileBinding.cardInfoAkun.visibility = View.GONE
                        profileBinding.ivVerifiedBadge.visibility = View.GONE
                        profileBinding.tvStudentName.visibility = View.GONE
                        profileBinding.tvInterestSection.visibility = View.GONE
                        profileBinding.cardInterest.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@UserProfileActivity, "Gagal memuat profil: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadProfileImages() {
        val imageLoader = ImageLoader.Builder(this)
            .components {
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    add(coil.decode.ImageDecoderDecoder.Factory())
                } else {
                    add(coil.decode.GifDecoder.Factory())
                }
            }
            .build()

        val bucket = SupabaseClient.client.storage["user_profiles"]
        val timestamp = System.currentTimeMillis()

        val avatarUrl = bucket.publicUrl("avatar_$targetUserId") + "?t=$timestamp"
        profileBinding.ivProfileAvatar.load(avatarUrl, imageLoader) {
            crossfade(true)
            memoryCacheKey("avatar_$targetUserId")
            diskCacheKey("avatar_$targetUserId")
            error(R.drawable.pfp)
            size(300, 300)
        }

        val bannerUrl = bucket.publicUrl("banner_$targetUserId") + "?t=$timestamp"
        profileBinding.ivProfileBanner.load(bannerUrl, imageLoader) {
            crossfade(true)
            memoryCacheKey("banner_$targetUserId")
            diskCacheKey("banner_$targetUserId")
            size(800, 400)
        }
    }

    private fun loadAndDisplayInterests(studentId: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val userInterests = SupabaseClient.client.from("user_interest")
                    .select { filter { eq("id_student", studentId) } }
                    .decodeList<com.example.dispatchapp.models.UserInterest>()

                val allInterests = SupabaseClient.client.from("interest")
                    .select()
                    .decodeList<com.example.dispatchapp.models.Interest>()

                val selectedIds = userInterests.map { it.interestId }.toSet()
                val selectedInterests = allInterests.filter { it.id in selectedIds }

                withContext(Dispatchers.Main) {
                    profileBinding.chipGroupProfileInterest.removeAllViews()
                    if (selectedInterests.isEmpty()) {
                        profileBinding.tvNoInterest.visibility = View.VISIBLE
                    } else {
                        profileBinding.tvNoInterest.visibility = View.GONE
                        selectedInterests.forEach { interest ->
                            val chip = com.google.android.material.chip.Chip(this@UserProfileActivity).apply {
                                text = interest.interest
                                isClickable = false
                                isCheckable = false
                                textSize = 13f
                            }
                            profileBinding.chipGroupProfileInterest.addView(chip)
                        }
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    profileBinding.tvNoInterest.visibility = View.VISIBLE
                    profileBinding.tvNoInterest.text = "Gagal memuat minat"
                }
            }
        }
    }

    private fun loadUserPosts() {
        profileBinding.pbMyPosts.visibility = View.VISIBLE
        profileBinding.tvEmptyMyPosts.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val posts = SupabaseClient.client.from("posts")
                    .select(columns = Columns.raw("*")) {
                        filter { eq("user_id", targetUserId) }
                        order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                    }
                    .decodeList<Post>()

                if (posts.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        profileBinding.pbMyPosts.visibility = View.GONE
                        profileBinding.tvEmptyMyPosts.visibility = View.VISIBLE
                        profileBinding.tvEmptyMyPosts.text = "Belum ada postingan"
                        postsAdapter.setPosts(emptyList(), emptyMap(), emptyMap())
                    }
                    return@launch
                }

                val postIds = posts.map { it.id }

                val likesData = SupabaseClient.client.from("post_likes")
                    .select(columns = Columns.raw("post_id")) {
                        filter { isIn("post_id", postIds) }
                    }
                    .decodeList<PostLike>()

                val commentsData = SupabaseClient.client.from("post_comments")
                    .select(columns = Columns.raw("post_id")) {
                        filter { isIn("post_id", postIds) }
                    }
                    .decodeList<PostComment>()

                val likeCounts = mutableMapOf<Long, Int>()
                likesData.forEach { likeCounts[it.postId] = (likeCounts[it.postId] ?: 0) + 1 }

                val commentCounts = mutableMapOf<Long, Int>()
                commentsData.forEach { commentCounts[it.postId] = (commentCounts[it.postId] ?: 0) + 1 }

                withContext(Dispatchers.Main) {
                    profileBinding.pbMyPosts.visibility = View.GONE
                    profileBinding.tvEmptyMyPosts.visibility = View.GONE
                    postsAdapter.setPosts(posts, likeCounts, commentCounts)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    profileBinding.pbMyPosts.visibility = View.GONE
                    profileBinding.tvEmptyMyPosts.visibility = View.VISIBLE
                    profileBinding.tvEmptyMyPosts.text = "Gagal memuat postingan"
                }
            }
        }
    }

    @Serializable
    data class ProfileDetail(
        val id: String,
        val username: String? = null,
        val role: String? = null,
        @SerialName("avatar") val avatarUrl: String? = null
    )

    companion object {
        private const val EXTRA_USER_ID = "extra_user_id"

        fun createIntent(context: Context, userId: String): Intent {
            return Intent(context, UserProfileActivity::class.java).apply {
                putExtra(EXTRA_USER_ID, userId)
            }
        }
    }
}
