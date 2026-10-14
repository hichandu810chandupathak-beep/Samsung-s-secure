package com.ssecure.applock

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {
    private lateinit var prefManager: PrefManager
    private lateinit var tvLockedCount: TextView
    private lateinit var cardPermission: LinearLayout
    private lateinit var tvServiceStatus: TextView
    private lateinit var switchMaster: SwitchCompat
    private lateinit var rvApps: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefManager = PrefManager(this)
        tvLockedCount = findViewById(R.id.tvLockedCount)
        cardPermission = findViewById(R.id.cardPermission)
        tvServiceStatus = findViewById(R.id.tvServiceStatus)
        switchMaster = findViewById(R.id.switchMaster)
        rvApps = findViewById(R.id.rvApps)
        rvApps.layoutManager = LinearLayoutManager(this)

        findViewById<Button>(R.id.btnGrantPermission).setOnClickListener {
            try { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            catch (_: Exception) { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }
        findViewById<Button>(R.id.btnSetPin).setOnClickListener {
            startActivity(Intent(this, LockActivity::class.java).apply {
                putExtra(LockActivity.EXTRA_MODE, LockActivity.MODE_SET_PIN)
            })
        }
        setupMasterSwitch()
        loadInstalledApps()
    }

    override fun onResume() {
        super.onResume()
        cardPermission.visibility = if (isAccessibilityServiceEnabled()) View.GONE else View.VISIBLE
        updateLockedCountText()
    }

    private fun setupMasterSwitch() {
        val enabled = prefManager.isServiceEnabled()
        switchMaster.isChecked = enabled
        updateServiceStatusUI(enabled)
        switchMaster.setOnCheckedChangeListener { _, checked ->
            prefManager.setServiceEnabled(checked)
            updateServiceStatusUI(checked)
        }
    }

    private fun updateServiceStatusUI(enabled: Boolean) {
        tvServiceStatus.text = if (enabled) "Protection is Active" else "Protection is Paused"
    }

    private fun loadInstalledApps() {
        val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val resolved = packageManager.queryIntentActivities(launcherIntent, 0)
        val locked = prefManager.getLockedPackages()
        val items = mutableListOf<AppItem>()
        val seen = mutableSetOf<String>()
        for (info in resolved) {
            val pkg = info.activityInfo.packageName
            if (pkg == packageName || !seen.add(pkg)) continue
            items.add(AppItem(
                appName = info.loadLabel(packageManager).toString(),
                packageName = pkg,
                icon = info.loadIcon(packageManager),
                isLocked = locked.contains(pkg),
                lockType = prefManager.getAppLockType(pkg)
            ))
        }
        items.sortBy { it.appName.lowercase() }
        rvApps.adapter = AppListAdapter(
            items,
            onLockChanged = { pkg, checked ->
                prefManager.setAppLocked(pkg, checked)
                updateLockedCountText()
            },
            onAppItemClicked = { item -> showLockOptionsDialog(item) }
        )
        updateLockedCountText()
    }

    private fun showLockOptionsDialog(item: AppItem) {
        val choices = arrayOf("PIN Lock", "Pattern Lock", "Fingerprint Lock", "Face Lock", "Password")
        val checked = when (prefManager.getAppLockType(item.packageName)) {
            "Pattern" -> 1
            "Fingerprint" -> 2
            "Face" -> 3
            "Password" -> 4
            else -> 0
        }
        AlertDialog.Builder(this)
            .setTitle("Security for ${item.appName}")
            .setSingleChoiceItems(choices, checked) { dialog, which ->
                val type = when (which) {
                    1 -> "Pattern"
                    2 -> "Fingerprint"
                    3 -> "Face"
                    4 -> "Password"
                    else -> "PIN"
                }
                prefManager.setAppLockType(item.packageName, type)
                prefManager.setAppLocked(item.packageName, true)
                loadInstalledApps()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateLockedCountText() {
        tvLockedCount.text = "${prefManager.getLockedPackages().size} apps locked"
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val manager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val expected = "$packageName/${AppLockAccessibilityService::class.java.name}"
        return manager.isEnabled && enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }
}
