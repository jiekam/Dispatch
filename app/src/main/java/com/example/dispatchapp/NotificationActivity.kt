package com.example.dispatchapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.dispatchapp.adapters.NotificationAdapter
import com.example.dispatchapp.databinding.ActivityNotificationBinding
import com.example.dispatchapp.models.Notification
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotificationActivity : BaseActivity() {

    private lateinit var binding: ActivityNotificationBinding
    private lateinit var adapter: NotificationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        adapter = NotificationAdapter { notification ->
            markAsRead(notification.id)
            navigateFromNotification(notification)
        }
        binding.rvNotifications.layoutManager = LinearLayoutManager(this)
        binding.rvNotifications.adapter = adapter

        loadNotifications()
    }

    private fun loadNotifications() {
        val uuid = SupabaseClient.client.auth.currentSessionOrNull()?.user?.id ?: return
        binding.pbNotifications.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val notifications = SupabaseClient.client.from("notifications")
                    .select(columns = Columns.raw("*, profiles!notifications_sender_id_fkey(username, avatar)")) {
                        filter { eq("recipient_id", uuid) }
                        order("created_at", Order.DESCENDING)
                    }.decodeList<Notification>()

                withContext(Dispatchers.Main) {
                    binding.pbNotifications.visibility = View.GONE
                    if (notifications.isEmpty()) {
                        binding.tvEmptyNotifications.visibility = View.VISIBLE
                        binding.rvNotifications.visibility = View.GONE
                    } else {
                        binding.tvEmptyNotifications.visibility = View.GONE
                        binding.rvNotifications.visibility = View.VISIBLE
                        adapter.submitList(notifications)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.pbNotifications.visibility = View.GONE
                    Toast.makeText(this@NotificationActivity, "Gagal memuat notifikasi", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun markAsRead(notifId: Long) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                SupabaseClient.client.from("notifications").update(
                    mapOf("is_read" to true)
                ) { filter { eq("id", notifId) } }
            } catch (_: Exception) {}
        }
    }

    private fun navigateFromNotification(notification: Notification) {
        when (notification.referenceType) {
            "event" -> {
                notification.referenceId?.let { eventId ->
                    val intent = Intent(this, DetailEventActivity::class.java)
                    intent.putExtra("event_id", eventId)
                    startActivity(intent)
                }
            }
            "profile" -> {
                notification.senderId?.let { senderId ->
                    startActivity(UserProfileActivity.createIntent(this, senderId))
                }
            }
            else -> {}
        }
    }
}
