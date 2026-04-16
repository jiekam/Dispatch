package com.example.dispatchapp.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.dispatchapp.R
import com.example.dispatchapp.SupabaseClient
import com.example.dispatchapp.databinding.ItemParticipantBinding
import com.example.dispatchapp.models.EventRegistration
import io.github.jan.supabase.storage.storage
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.Date

class ParticipantAdapter(
    private val onStatusChange: (registration: EventRegistration, newStatus: String) -> Unit,
    private val onCardClick: ((registration: EventRegistration) -> Unit)? = null
) : RecyclerView.Adapter<ParticipantAdapter.ViewHolder>() {

    private val participants = mutableListOf<EventRegistration>()

    fun setParticipants(list: List<EventRegistration>) {
        participants.clear()
        participants.addAll(list)
        notifyDataSetChanged()
    }

    inner class ViewHolder(private val binding: ItemParticipantBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(reg: EventRegistration) {
            val name = reg.students?.studentName ?: reg.profiles?.username ?: "Peserta"
            binding.tvParticipantName.text = name
            binding.tvRegisteredDate.text = formatRelativeTime(reg.registeredAt)

            binding.ivAttended.visibility = when (reg.status) {
                "confirmed" -> View.VISIBLE
                else -> View.GONE
            }

            val avatarUrl = reg.profiles?.avatar
            val userId = reg.userId
            if (!avatarUrl.isNullOrEmpty()) {
                binding.ivAvatar.load(avatarUrl) {
                    crossfade(true)
                    error(R.drawable.ic_profile_placeholder)
                }
            } else {
                
                val bucketAvatarUrl = SupabaseClient.client.storage["user_profiles"]
                    .publicUrl("avatar_$userId") + "?t=${System.currentTimeMillis()}"
                binding.ivAvatar.load(bucketAvatarUrl) {
                    crossfade(true)
                    error(R.drawable.ic_profile_placeholder)
                }
            }
            binding.ivAvatar.setOnClickListener { onCardClick?.invoke(reg) }

            binding.btnParticipantMenu.setOnClickListener { view ->
                val popup = PopupMenu(view.context, view)
                popup.menu.add(0, 1, 0, "Lihat Detail")
                popup.menu.add(0, 2, 0, "Konfirmasi")
                popup.menu.add(0, 3, 0, "Tolak")
                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        1 -> onCardClick?.invoke(reg)
                        2 -> onStatusChange(reg, "confirmed")
                        3 -> {
                            
                            androidx.appcompat.app.AlertDialog.Builder(view.context)
                                .setTitle("Tolak Peserta")
                                .setMessage("Yakin ingin menolak ${reg.students?.studentName ?: reg.profiles?.username ?: "peserta"} ini? Data pendaftaran akan dihapus.")
                                .setPositiveButton("Tolak") { _, _ -> onStatusChange(reg, "rejected") }
                                .setNegativeButton("Batal", null)
                                .show()
                        }
                    }
                    true
                }
                popup.show()
            }
        }
    }

    private fun formatRelativeTime(dateStr: String?): String {
        if (dateStr.isNullOrBlank()) return "—"
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val date: Date = sdf.parse(dateStr.take(19)) ?: return "—"
            val diff = System.currentTimeMillis() - date.time
            when {
                diff < TimeUnit.DAYS.toMillis(1) -> "Hari ini"
                diff < TimeUnit.DAYS.toMillis(7) -> "${TimeUnit.MILLISECONDS.toDays(diff)} hari lalu"
                else -> SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(date)
            }
        } catch (_: Exception) { "—" }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemParticipantBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(participants[position])
    override fun getItemCount() = participants.size
}
