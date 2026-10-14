package com.ssecure.applock

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView

data class AppItem(
    val appName: String,
    val packageName: String,
    val icon: Drawable,
    var isLocked: Boolean
)

class AppListAdapter(
    private val apps: List<AppItem>,
    private val onLockChanged: (String, Boolean) -> Unit
) : RecyclerView.Adapter<AppListAdapter.AppViewHolder>() {

    class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivAppIcon: ImageView = view.findViewById(R.id.ivAppIcon)
        val tvAppName: TextView = view.findViewById(R.id.tvAppName)
        val tvPackageName: TextView = view.findViewById(R.id.tvPackageName)
        val switchAppLock: SwitchCompat = view.findViewById(R.id.switchAppLock)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val item = apps[position]
        holder.ivAppIcon.setImageDrawable(item.icon)
        holder.tvAppName.text = item.appName
        holder.tvPackageName.text = item.packageName

        holder.switchAppLock.setOnCheckedChangeListener(null)
        holder.switchAppLock.isChecked = item.isLocked

        holder.switchAppLock.setOnCheckedChangeListener { _, isChecked ->
            item.isLocked = isChecked
            onLockChanged(item.packageName, isChecked)
        }

        holder.itemView.setOnClickListener {
            holder.switchAppLock.isChecked = !holder.switchAppLock.isChecked
        }
    }

    override fun getItemCount(): Int = apps.size
}
