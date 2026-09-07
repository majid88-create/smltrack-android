package com.ideakaryanusa.smltrack.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ideakaryanusa.smltrack.R
import com.ideakaryanusa.smltrack.model.SiteItem

class SiteAdapter(private var items: List<SiteItem>) :
    RecyclerView.Adapter<SiteAdapter.VH>() {

    fun update(newItems: List<SiteItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvSiteName)
        val address: TextView = view.findViewById(R.id.tvSiteAddress)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_site, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.name.text = item.projectName ?: "-"
        holder.address.text = item.address?.takeIf { it.isNotBlank() } ?: (item.role ?: "CP")
    }

    override fun getItemCount() = items.size
}
