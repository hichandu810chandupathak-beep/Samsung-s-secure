package com.ssecure.applock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class AppLockAccessibilityService : AccessibilityService() {

    private lateinit var prefManager: PrefManager

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefManager = PrefManager(this)
        // A service restart must never restore an old unlock session.
        prefManager.clearUnlockedSessions()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (!::prefManager.isInitialized || !prefManager.isServiceEnabled()) return

        val packageName = event.packageName?.toString() ?: return
        if (packageName == APP_PACKAGE) return
        if (isTransientSystemWindow(packageName)) return

        // Leaving the protected app for the launcher clears the unlock immediately.
        if (isLauncher(packageName)) {
            prefManager.clearUnlockedSessions()
            return
        }

        val lastUnlocked = prefManager.getLastUnlockedPkg()
        if (lastUnlocked.isNotEmpty() && packageName != lastUnlocked) {
            prefManager.clearUnlockedSessions()
        }

        if (prefManager.isAppLocked(packageName) &&
            !prefManager.isAppUnlockedInSession(packageName) &&
            prefManager.getLastUnlockedPkg() != packageName
        ) {
            val lockIntent = Intent(this, LockActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
                putExtra(LockActivity.EXTRA_PACKAGE_NAME, packageName)
                putExtra(LockActivity.EXTRA_MODE, LockActivity.MODE_LOCK)
            }
            startActivity(lockIntent)
        }
    }

    private fun isLauncher(packageName: String): Boolean {
        val name = packageName.lowercase()
        return name.contains("launcher") || name.contains("home")
    }

    private fun isTransientSystemWindow(packageName: String): Boolean {
        val name = packageName.lowercase()
        return name.contains("systemui") ||
            name.contains("inputmethod") ||
            name.contains("keyboard") ||
            name.contains("honeyboard")
    }

    override fun onInterrupt() {}

    companion object {
        private const val APP_PACKAGE = "com.ssecure.applock"
    }
}
