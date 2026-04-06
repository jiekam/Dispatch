package com.example.dispatchapp.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Event(
    val id: Long,
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
    val profiles: Profile? = null
)

@Serializable
data class Profile(
    val username: String? = null,
    val role: String? = null,
    val avatar: String? = null
)

@Serializable
data class Student(
    @SerialName("student_id") 
    val student_id: Long? = null,
    val kelas: String? = null,
    val jurusan: String? = null,
    val prodi: String? = null,
    val nis: Long? = null
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
