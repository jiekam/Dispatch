package com.example.dispatchapp.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.dispatchapp.databinding.ItemEventLargeBinding
import com.example.dispatchapp.databinding.ItemEventSmallBinding
import com.example.dispatchapp.models.Event

class EventAdapter(
    private val isLarge: Boolean,
    private val onEventClick: (Event) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val events = mutableListOf<Event>()

    fun setEvents(newEvents: List<Event>) {
        events.clear()
        events.addAll(newEvents)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (isLarge) VIEW_TYPE_LARGE else VIEW_TYPE_SMALL
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_LARGE) {
            val binding = ItemEventLargeBinding.inflate(inflater, parent, false)
            LargeViewHolder(binding)
        } else {
            val binding = ItemEventSmallBinding.inflate(inflater, parent, false)
            SmallViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val event = events[position]
        if (holder is LargeViewHolder) {
            holder.bind(event)
        } else if (holder is SmallViewHolder) {
            holder.bind(event)
        }
    }

    override fun getItemCount(): Int = events.size

    inner class LargeViewHolder(private val binding: ItemEventLargeBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onEventClick(events[position])
                }
            }
        }

        fun bind(event: Event) {
            binding.tvLargeTitle.text = event.title
            binding.tvLargeInfoDate.text = "📅 " + (event.endDate ?: "NULL")
            binding.tvLargeInfoLoc.text = "📍 " + (event.location ?: "NULL")

            binding.tvLargeUploader.text = event.profiles?.username ?: "Unknown"
            
            // Verif icon visibility based on role 'organizer'
            if (event.profiles?.role?.equals("organizer", ignoreCase = true) == true) {
                binding.ivLargeVerif.visibility = android.view.View.VISIBLE
            } else {
                binding.ivLargeVerif.visibility = android.view.View.GONE
            }

            if (!event.bannerUrl.isNullOrEmpty()) {
                binding.ivLargeBanner.load(event.bannerUrl) {
                    crossfade(true)
                }
            }
        }
    }

    inner class SmallViewHolder(private val binding: ItemEventSmallBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onEventClick(events[position])
                }
            }
        }

        fun bind(event: Event) {
            binding.tvSmallTitle.text = event.title
            binding.tvSmallInfoDate.text = "📅 " + (event.endDate ?: "TBD")
            binding.tvSmallInfoLoc.text = "📍 " + (event.location ?: "TBD")

            binding.tvSmallUploader.text = event.profiles?.username ?: "Unknown"

            if (event.profiles?.role?.equals("organizer", ignoreCase = true) == true) {
                binding.tvSmallIcVerif.visibility = android.view.View.VISIBLE
            } else {
                binding.tvSmallIcVerif.visibility = android.view.View.GONE
            }

            if (!event.bannerUrl.isNullOrEmpty()) {
                binding.ivSmallBanner.load(event.bannerUrl) {
                    crossfade(true)
                }
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_SMALL = 0
        private const val VIEW_TYPE_LARGE = 1
    }
}
