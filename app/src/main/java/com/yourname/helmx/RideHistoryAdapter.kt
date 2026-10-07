package com.yourname.helmx

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.yourname.helmx.databinding.ItemRideHistoryBinding

class RideHistoryAdapter(private val rides: List<Ride>) :
    RecyclerView.Adapter<RideHistoryAdapter.RideViewHolder>() {

    class RideViewHolder(val binding: ItemRideHistoryBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RideViewHolder {
        val binding = ItemRideHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return RideViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RideViewHolder, position: Int) {
        val ride = rides[position]
        holder.binding.apply {
            tvDestination.text = "To: ${ride.destination}"
            tvDate.text = ride.date
            tvDistance.text = ride.distance
            tvDuration.text = ride.duration
            tvAvgSpeed.text = ride.avgSpeed
        }
    }

    override fun getItemCount() = rides.size
}
