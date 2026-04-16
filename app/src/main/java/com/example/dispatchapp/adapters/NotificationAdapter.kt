package com.example.dispatchapp.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.dispatchapp.R
import com.example.dispatchapp.databinding.ItemNotificationBinding
import com.example.dispatchapp.models.Notification
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class NotificationAdapter(
    private val onClick: (Notification) -> Unit
) : ListAdapter<Notification, NotificationAdapter.ViewHolder>(DiffCallback()) {

    inner class ViewHolder(private val binding: ItemNotificationBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(notification: Notification) {
            binding.tvNotifTitle.text = notification.title
            binding.tvNotifBody.text = notification.body ?: ""
            binding.tvNotifBody.visibility = if (notification.body.isNullOrBlank()) View.GONE else View.VISIBLE
            binding.tvNotifTime.text = formatRelativeTime(notification.createdAt)
            binding.viewUnreadDot.visibility = if (!notification.isRead) View.VISIBLE else View.GONE

            val iconRes = when (notification.type) {
                "new_event" -> R.drawable.ic_events
                "follow" -> R.drawable.ic_people
                "registration_update" -> R.drawable.ic_check_circle
                else -> R.drawable.ic_bell
            }
            binding.ivNotifIcon.setImageResource(iconRes)

            binding.root.setOnClickListener { onClick(notification) }
        }
    }

    private fun formatRelativeTime(dateStr: String?): String {
        if (dateStr.isNullOrBlank()) return ""
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val date: Date = sdf.parse(dateStr.take(19)) ?: return ""
            val diff = System.currentTimeMillis() - date.time
            when {
                diff < TimeUnit.MINUTES.toMillis(1) -> "Baru saja"
                diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)} menit lalu"
                diff < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diff)} jam lalu"
                diff < TimeUnit.DAYS.toMillis(7) -> "${TimeUnit.MILLISECONDS.toDays(diff)} hari lalu"
                else -> SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(date)
            }
        } catch (_: Exception) { "" }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    class DiffCallback : DiffUtil.ItemCallback<Notification>() {
        override fun areItemsTheSame(oldItem: Notification, newItem: Notification) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Notification, newItem: Notification) = oldItem == newItem
    }
}
