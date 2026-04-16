package com.example.dispatchapp.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.dispatchapp.R
import com.example.dispatchapp.databinding.ItemEventSmallBinding
import com.example.dispatchapp.models.Event

class OrganizerEventAdapter(
    private val onClick: (Event) -> Unit
) : RecyclerView.Adapter<OrganizerEventAdapter.ViewHolder>() {

    private val events = mutableListOf<Event>()

    fun setEvents(newEvents: List<Event>) {
        events.clear()
        events.addAll(newEvents)
        notifyDataSetChanged()
    }

    inner class ViewHolder(private val binding: ItemEventSmallBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(event: Event) {
            binding.tvSmallTitle.text = event.title
            binding.tvSmallInfoLoc.text = "📍 ${event.location ?: "—"}"
            binding.tvSmallInfoDate.text = "📅 ${event.startDate ?: "—"}"
            binding.tvSmallUploader.visibility = android.view.View.GONE
            binding.ivSmallVerif.visibility = android.view.View.GONE

            if (!event.bannerUrl.isNullOrEmpty()) {
                binding.ivSmallBanner.load(event.bannerUrl) {
                    crossfade(true)
                    error(R.drawable.placeholder_event_image)
                }
            } else {
                binding.ivSmallBanner.setImageResource(R.drawable.placeholder_event_image)
            }

            binding.root.setOnClickListener { onClick(event) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemEventSmallBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(events[position])
    override fun getItemCount() = events.size
}
