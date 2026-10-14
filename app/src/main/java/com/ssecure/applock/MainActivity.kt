package com.ssecure.applock

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        findViewById<Button>(R.id.btnSetPin).setOnClickListener {
            val intent = Intent(this, LockActivity::class.java).apply {
                putExtra(LockActivity.EXTRA_MODE, LockActivity.MODE_SET_PIN)
            }
            startActivity(intent)
        }

        setupMasterSwitch()
        requestNotificationPermissionIfNeeded()
        loadInstalledApps()
    }

    override fun onResume() {
        super.onResume()
        val hasUsageAccess = hasUsageStatsPermission()
        cardPermission.visibility = if (hasUsageAccess) View.GONE else View.VISIBLE

        if (hasUsageAccess && prefManager.isServiceEnabled()) {
            startProtectionService()
        }
        updateLockedCountText()
    }

    private fun setupMasterSwitch() {
        val enabled = prefManager.isServiceEnabled()
        switchMaster.isChecked = enabled
        updateServiceStatusUI(enabled)

        switchMaster.setOnCheckedChangeListener { _, isChecked ->
            prefManager.setServiceEnabled(isChecked)
            updateServiceStatusUI(isChecked)
            if (isChecked && hasUsageStatsPermission()) {
                startProtectionService()
            } else {
                stopService(Intent(this, AppLockService::class.java))
            }
        }
    }

    private fun updateServiceStatusUI(enabled: Boolean) {
        tvServiceStatus.text = if (enabled) "Protection is ON" else "Protection is OFF"
    }

    private fun startProtectionService() {
        val serviceIntent = Intent(this, AppLockService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun loadInstalledApps() {
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = packageManager.queryIntentActivities(mainIntent, 0)
        val lockedSet = prefManager.getLockedPackages()
        val appItems = mutableListOf<AppItem>()
        val seenPackages = mutableSetOf<String>()

        for (info in resolveInfos) {
            val pkgName = info.activityInfo.packageName
            if (pkgName == packageName || !seenPackages.add(pkgName)) continue

            val label = info.loadLabel(packageManager).toString()
            val icon = info.loadIcon(packageManager)
            appItems.add(
                AppItem(
                    appName = label,
                    packageName = pkgName,
                    icon = icon,
                    isLocked = lockedSet.contains(pkgName)
                )
            )
        }

        appItems.sortBy { it.appName.lowercase() }

        rvApps.adapter = AppListAdapter(appItems) { pkg, isLocked ->
            prefManager.setAppLocked(pkg, isLocked)
            updateLockedCountText()
        }
        updateLockedCountText()
    }

    private fun updateLockedCountText() {
        val count = prefManager.getLockedPackages().size
        tvLockedCount.text = "$count apps locked"
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }
    }
}
