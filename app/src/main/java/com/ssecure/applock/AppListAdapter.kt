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
    var isLocked: Boolean,
    var lockType: String = "PIN"
)

class AppListAdapter(
    private val apps: List<AppItem>,
    private val onLockChanged: (String, Boolean) -> Unit,
    private val onAppItemClicked: (AppItem) -> Unit = {}
) : RecyclerView.Adapter<AppListAdapter.AppViewHolder>() {

    class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivAppIcon: ImageView = view.findViewById(R.id.ivAppIcon)
        val tvAppName: TextView = view.findViewById(R.id.tvAppName)
        val tvPackageName: TextView = view.findViewById(R.id.tvPackageName)
        val tvLockTypeBadge: TextView = view.findViewById(R.id.tvLockTypeBadge)
        val switchAppLock: SwitchCompat = view.findViewById(R.id.switchAppLock)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val item = apps[position]
        holder.ivAppIcon.setImageDrawable(item.icon)
        holder.tvAppName.text = item.appName
        holder.tvPackageName.text = item.packageName
        holder.tvLockTypeBadge.visibility = if (item.isLocked) View.VISIBLE else View.GONE
        holder.tvLockTypeBadge.text = item.lockType
        holder.switchAppLock.setOnCheckedChangeListener(null)
        holder.switchAppLock.isChecked = item.isLocked
        holder.switchAppLock.setOnCheckedChangeListener { _, checked ->
            item.isLocked = checked
            holder.tvLockTypeBadge.visibility = if (checked) View.VISIBLE else View.GONE
            onLockChanged(item.packageName, checked)
        }
        holder.itemView.setOnClickListener { onAppItemClicked(item) }
    }

    override fun getItemCount(): Int = apps.size
}
