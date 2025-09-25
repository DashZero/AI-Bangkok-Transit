package com.example.bangkoktransitalarm

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.bangkoktransitalarm.data.Station

class SearchResultsAdapter(
    private var stations: List<Station>,
    private val onItemClick: (Station) -> Unit
) : RecyclerView.Adapter<SearchResultsAdapter.StationViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_result, parent, false)
        return StationViewHolder(view)
    }

    override fun onBindViewHolder(holder: StationViewHolder, position: Int) {
        val station = stations[position]
        holder.stationNameTextView.text = "${station.nameEng} (${station.line})"
        holder.itemView.setOnClickListener { onItemClick(station) }
    }

    override fun getItemCount(): Int = stations.size

    fun updateData(newStations: List<Station>) {
        stations = newStations
        notifyDataSetChanged()
    }

    class StationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val stationNameTextView: TextView = itemView.findViewById(R.id.searchResultText)
    }
}
