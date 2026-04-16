package com.example.dispatchapp.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Event(
    val id: Long = 0,
    val title: String,
    val desc: String,
    @SerialName("banner_url")
    val bannerUrl: String? = null,
    @SerialName("start_date")
    val startDate: String? = null,
    @SerialName("end_date")
    val endDate: String? = null,
    val location: String? = null,
    @SerialName("wishlist_count")
    val wishlistCount: Long = 0,
    @SerialName("user_id")
    val userId: String? = null,
    @SerialName("registration_type")
    val registrationType: String? = null,
    @SerialName("max_participants")
    val maxParticipants: Int? = null,
    @SerialName("external_registration_url")
    val externalRegistrationUrl: String? = null,
    @SerialName("contact_type")
    val contactType: String? = null,
    @SerialName("contact_value")
    val contactValue: String? = null,
    val profiles: Profile? = null
)

@Serializable
data class Profile(
    val id: String? = null,
    val username: String? = null,
    val role: String? = null,
    val avatar: String? = null,
    @SerialName("organization_name")
    val organizationName: String? = null,
    @SerialName("account_status")
    val accountStatus: String? = null
)

@Serializable
data class Student(
    @SerialName("student_id") 
    val student_id: Long? = null,
    val kelas: String? = null,
    val jurusan: String? = null,
    val prodi: String? = null,
    val nis: Long? = null,
    @SerialName("student_name")
    val studentName: String? = null
)

@Serializable
data class EventRegistration(
    val id: Long = 0,
    @SerialName("event_id")
    val eventId: Long,
    @SerialName("user_id")
    val userId: String,
    @SerialName("registered_at")
    val registeredAt: String? = null,
    val status: String? = "pending",
    val students: Student? = null,
    val profiles: Profile? = null
)

@Serializable
data class Notification(
    val id: Long = 0,
    val title: String,
    val body: String,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("is_read")
    val isRead: Boolean = false,
    val type: String = "info",
    @SerialName("reference_type")
    val referenceType: String? = null,
    @SerialName("reference_id")
    val referenceId: Long? = null,
    @SerialName("sender_id")
    val senderId: String? = null,
    val profiles: Profile? = null
)

@Serializable
data class WishlistWithEvent(
    val id: Long,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("user_id")
    val userId: String,
    @SerialName("event_id")
    val eventId: Long,
    val events: Event? = null
)

@Serializable
data class Interest(
    val id: Long = 0,
    val interest: String
)

@Serializable
data class UserInterest(
    val id: Long? = null,
    @SerialName("interest_id")
    val interestId: Long,
    @SerialName("id_student")
    val idStudent: Int
)

@Serializable
data class PostInterest(
    val id: Long = 0,
    @SerialName("post_id") val postId: Long = 0,
    @SerialName("interest_id") val interestId: Long = 0,
    val interest: Interest? = null
)

@Serializable
data class Post(
    val id: Long = 0,
    @SerialName("user_id") val userId: String = "",
    @SerialName("student_id") val studentId: Long = 0,
    @SerialName("media_url") val mediaUrl: String = "",
    @SerialName("media_type") val mediaType: String = "image",
    val caption: String? = null,
    @SerialName("project_description") val projectDescription: String? = null,
    @SerialName("interest_id") val interestId: Long? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val profiles: Profile? = null,
    val students: Student? = null,
    val interest: Interest? = null,
    val post_interests: List<PostInterest>? = null
)

@Serializable
data class SavedPost(
    val id: Long = 0,
    @SerialName("user_id") val userId: String = "",
    @SerialName("post_id") val postId: Long = 0,
    @SerialName("created_at") val createdAt: String? = null,
    val posts: Post? = null
)

@Serializable
data class PostLike(
    val id: Long = 0,
    @SerialName("post_id") val postId: Long = 0,
    @SerialName("user_id") val userId: String = ""
)

@Serializable
data class PostComment(
    val id: Long = 0,
    @SerialName("post_id") val postId: Long = 0,
    @SerialName("user_id") val userId: String = "",
    @SerialName("student_id") val studentId: Long = 0,
    val content: String = "",
    @SerialName("parent_id") val parentId: Long? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val profiles: Profile? = null,
    @kotlinx.serialization.Transient var replies: MutableList<PostComment> = mutableListOf()
)
