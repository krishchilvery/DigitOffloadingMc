package com.example.captureslave

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.recyclerview.widget.RecyclerView

class DiscoveryAdapter(private val mList: List<DiscoveryDataModel>, private val connectCallback: (String) -> Unit): RecyclerView.Adapter<DiscoveryAdapter.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // inflates the card_view_design view
        // that is used to hold list item
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.card_view_design, parent, false)

        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        val dataModel = mList[position]

        // sets the text to the textview from our itemHolder class
        holder.button.text = "${dataModel.endpointId}: ${dataModel.info?.endpointName}"
        holder.button.setOnClickListener {
            connectCallback.invoke(dataModel.endpointId)
        }
    }

    class ViewHolder(ItemView: View) : RecyclerView.ViewHolder(ItemView) {
        val button: Button = itemView.findViewById(R.id.advertiserButton)
    }

    override fun getItemCount(): Int {
        return mList.size
    }

}