package com.ssecure.applock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class AppLockAccessibilityService : AccessibilityService() {

    private lateinit var prefManager: PrefManager

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefManager = PrefManager(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (!prefManager.isServiceEnabled()) return

            val packageName = event.packageName?.toString() ?: return
            if (packageName == pkgName || packageName.contains("launcher") || packageName.contains("systemui")) {
                return
            }

            val lastUnlocked = prefManager.getLastUnlockedPkg()
            if (lastUnlocked.isNotEmpty() && packageName != lastUnlocked) {
                prefManager.setLastUnlockedPkg("")
            }

            if (prefManager.isAppLocked(packageName) && prefManager.getLastUnlockedPkg() != packageName) {
                val lockIntent = Intent(this, LockActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(LockActivity.EXTRA_PACKAGE_NAME, packageName)
                    putExtra(LockActivity.EXTRA_MODE, LockActivity.MODE_LOCK)
                }
                startActivity(lockIntent)
            }
        }
    }

    override fun onInterrupt() {}

    companion object {
        private const val pkgName = "com.ssecure.applock"
    }
}
