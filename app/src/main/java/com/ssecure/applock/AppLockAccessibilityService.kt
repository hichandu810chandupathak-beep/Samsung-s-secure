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
        if (event == null || !::prefManager.isInitialized) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (!prefManager.isServiceEnabled()) return

        val openedPackage = event.packageName?.toString() ?: return
        if (openedPackage == packageName ||
            openedPackage.contains("launcher", ignoreCase = true) ||
            openedPackage.contains("systemui", ignoreCase = true)
        ) return

        val lastUnlocked = prefManager.getLastUnlockedPkg()
        if (lastUnlocked.isNotEmpty() && openedPackage != lastUnlocked) {
            prefManager.setLastUnlockedPkg("")
        }
        if (prefManager.isAppLocked(openedPackage) &&
            prefManager.getLastUnlockedPkg() != openedPackage
        ) {
            val lockIntent = Intent(this, LockActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(LockActivity.EXTRA_PACKAGE_NAME, openedPackage)
                putExtra(LockActivity.EXTRA_MODE, LockActivity.MODE_LOCK)
            }
            startActivity(lockIntent)
        }
    }

    override fun onInterrupt() {}
}
