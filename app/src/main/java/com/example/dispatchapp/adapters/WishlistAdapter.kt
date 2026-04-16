package com.example.dispatchapp.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.dispatchapp.databinding.ItemEventSmallBinding
import com.example.dispatchapp.databinding.ItemWishlistDateHeaderBinding
import com.example.dispatchapp.models.Event

class WishlistAdapter(
    private val onEventClick: (Event) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<WishlistItem>()

    sealed class WishlistItem {
        data class DateHeader(val dateLabel: String) : WishlistItem()
        data class EventItem(val event: Event) : WishlistItem()
    }

    fun setGroupedEvents(groupedEvents: Map<String, List<Event>>) {
        items.clear()
        for ((dateLabel, events) in groupedEvents) {
            items.add(WishlistItem.DateHeader(dateLabel))
            events.forEach { items.add(WishlistItem.EventItem(it)) }
        }
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is WishlistItem.DateHeader -> VIEW_TYPE_HEADER
            is WishlistItem.EventItem -> VIEW_TYPE_EVENT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_HEADER) {
            val binding = ItemWishlistDateHeaderBinding.inflate(inflater, parent, false)
            DateHeaderViewHolder(binding)
        } else {
            val binding = ItemEventSmallBinding.inflate(inflater, parent, false)
            EventViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is WishlistItem.DateHeader -> (holder as DateHeaderViewHolder).bind(item.dateLabel)
            is WishlistItem.EventItem -> (holder as EventViewHolder).bind(item.event)
        }
    }

    override fun getItemCount(): Int = items.size

    inner class DateHeaderViewHolder(private val binding: ItemWishlistDateHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(dateLabel: String) {
            binding.tvDateHeader.text = dateLabel
        }
    }

    inner class EventViewHolder(private val binding: ItemEventSmallBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val item = items[position]
                    if (item is WishlistItem.EventItem) {
                        onEventClick(item.event)
                    }
                }
            }
        }

        fun bind(event: Event) {
            binding.tvSmallTitle.text = event.title
            binding.tvSmallInfoDate.text = "📅 " + (event.endDate ?: "TBD")
            binding.tvSmallInfoLoc.text = "📍 " + (event.location ?: "TBD")

            binding.tvSmallUploader.text = event.profiles?.username ?: "Unknown"

            if (event.profiles?.role?.equals("organizer", ignoreCase = true) == true) {
                binding.ivSmallVerif.visibility = android.view.View.VISIBLE
            } else {
                binding.ivSmallVerif.visibility = android.view.View.GONE
            }

            if (!event.bannerUrl.isNullOrEmpty()) {
                binding.ivSmallBanner.load(event.bannerUrl) {
                    crossfade(true)
                }
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_EVENT = 1
    }
}
