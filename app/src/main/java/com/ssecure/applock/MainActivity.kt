package com.ssecure.applock

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.accessibility.AccessibilityManager

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
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }

        findViewById<Button>(R.id.btnSetPin).setOnClickListener {
            val intent = Intent(this, LockActivity::class.java).apply {
                putExtra(LockActivity.EXTRA_MODE, LockActivity.MODE_SET_PIN)
            }
            startActivity(intent)
        }

        setupMasterSwitch()
        loadInstalledApps()
        promptDeviceAdminActivationIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        val isAccessibilityEnabled = isAccessibilityServiceEnabled()
        cardPermission.visibility = if (isAccessibilityEnabled) View.GONE else View.VISIBLE
        updateLockedCountText()
    }

    private fun promptDeviceAdminActivationIfNeeded() {
        val prefs = getSharedPreferences("security_setup", MODE_PRIVATE)
        val component = ComponentName(this, AppAdminReceiver::class.java)
        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (dpm.isAdminActive(component) || prefs.getBoolean("admin_prompt_shown", false)) return

        AlertDialog.Builder(this)
            .setTitle("Enable uninstall protection")
            .setMessage("Android Device Administrator can require this app's administrator access to be deactivated before uninstalling. It does not enforce your Master PIN inside Android Settings.")
            .setPositiveButton("Enable") { _, _ ->
                prefs.edit().putBoolean("admin_prompt_shown", true).apply()
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Enable S Secure device-admin protection. Android may require deactivation before uninstall.")
                }
                startActivity(intent)
            }
            .setNegativeButton("Not now") { _, _ ->
                prefs.edit().putBoolean("admin_prompt_shown", true).apply()
            }
            .show()
    }

    private fun setupMasterSwitch() {
        val enabled = prefManager.isServiceEnabled()
        switchMaster.isChecked = enabled
        updateServiceStatusUI(enabled)

        switchMaster.setOnCheckedChangeListener { _, isChecked ->
            prefManager.setServiceEnabled(isChecked)
            updateServiceStatusUI(isChecked)
        }
    }

    private fun updateServiceStatusUI(enabled: Boolean) {
        tvServiceStatus.text = if (enabled) "Protection is Active" else "Protection is Paused"
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
            val lockType = prefManager.getAppLockType(pkgName)

            appItems.add(
                AppItem(
                    appName = label,
                    packageName = pkgName,
                    icon = icon,
                    isLocked = lockedSet.contains(pkgName),
                    lockType = lockType
                )
            )
        }

        // Add S Secure itself as a lockable item; launcher query excludes this package above.
        if (seenPackages.add(packageName)) {
            try {
                val ownInfo = packageManager.getApplicationInfo(packageName, 0)
                appItems.add(
                    AppItem(
                        appName = packageManager.getApplicationLabel(ownInfo).toString(),
                        packageName = packageName,
                        icon = packageManager.getApplicationIcon(ownInfo),
                        isLocked = lockedSet.contains(packageName),
                        lockType = prefManager.getAppLockType(packageName)
                    )
                )
            } catch (_: Exception) {
                // Keep the installed-app list available if own app label/icon lookup fails.
            }
        }

        appItems.sortBy { it.appName.lowercase() }

        rvApps.adapter = AppListAdapter(
            appItems,
            onLockChanged = { pkg, isLocked ->
                prefManager.setAppLocked(pkg, isLocked)
                updateLockedCountText()
            },
            onAppItemClicked = { appItem ->
                showLockOptionsDialog(appItem)
            }
        )
        updateLockedCountText()
    }

    private fun showLockOptionsDialog(appItem: AppItem) {
        val options = arrayOf("PIN Lock", "Pattern Lock", "Fingerprint Lock", "Face Lock", "Password")
        val currentType = prefManager.getAppLockType(appItem.packageName)
        val checkedItem = when (currentType) {
            "Pattern" -> 1
            "Fingerprint" -> 2
            "Face" -> 3
            "Password" -> 4
            else -> 0
        }

        AlertDialog.Builder(this)
            .setTitle("Security for ${appItem.appName}")
            .setSingleChoiceItems(options, checkedItem) { dialog, which ->
                val selectedType = when (which) {
                    1 -> "Pattern"
                    2 -> "Fingerprint"
                    3 -> "Face"
                    4 -> "Password"
                    else -> "PIN"
                }
                prefManager.setAppLockType(appItem.packageName, selectedType)
                prefManager.setAppLocked(appItem.packageName, true)
                loadInstalledApps()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateLockedCountText() {
        val count = prefManager.getLockedPackages().size
        tvLockedCount.text = "$count apps locked"
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(packageName)
    }
}
