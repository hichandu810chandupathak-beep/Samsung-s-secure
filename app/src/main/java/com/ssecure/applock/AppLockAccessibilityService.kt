package com.ssecure.applock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class AppLockAccessibilityService : AccessibilityService() {
    private lateinit var prefManager: PrefManager
    private var activeLockTarget: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefManager = PrefManager(this)
        prefManager.clearUnlockedSessions()
        activeLockTarget = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (!::prefManager.isInitialized) return
        if (!prefManager.isServiceEnabled()) {
            activeLockTarget = null
            return
        }

        val pkg = event.packageName?.toString() ?: return
        if (isTransientSystemWindow(pkg)) return

        if (pkg == APP_PACKAGE) {
            val cls = event.className?.toString().orEmpty()
            if (cls.endsWith("LockActivity")) return
            if (!cls.endsWith("MainActivity") || !prefManager.hasPinSet()) return
            if (prefManager.isAppUnlockedInSession(APP_PACKAGE) &&
                prefManager.getLastUnlockedPkg() == APP_PACKAGE
            ) {
                activeLockTarget = null
                return
            }
            if (activeLockTarget == APP_PACKAGE) return
            activeLockTarget = APP_PACKAGE
            launchLock(APP_PACKAGE)
            return
        }

        if (isLauncher(pkg)) {
            prefManager.clearUnlockedSessions()
            activeLockTarget = null
            return
        }

        val activeTarget = activeLockTarget
        if (activeTarget != null && pkg == activeTarget) {
            if (prefManager.isAppUnlockedInSession(pkg)) activeLockTarget = null
            // While authentication is pending, ignore repeat events for that same app.
            return
        }

        // Same-package sub-activities retain their session. A different foreground app
        // ends the prior session; system/biometric/private-container windows are filtered above.
        if (activeTarget != null && pkg != activeTarget) activeLockTarget = null
        val last = prefManager.getLastUnlockedPkg()
        if (last.isNotEmpty() && pkg != last) prefManager.clearUnlockedSessions()

        if (prefManager.isAppLocked(pkg) &&
            !prefManager.isAppUnlockedInSession(pkg) &&
            prefManager.getLastUnlockedPkg() != pkg
        ) {
            activeLockTarget = pkg
            launchLock(pkg)
        }
    }

    private fun launchLock(pkg: String) {
        try {
            startActivity(Intent(this, LockActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(LockActivity.EXTRA_PACKAGE_NAME, pkg)
                putExtra(LockActivity.EXTRA_MODE, LockActivity.MODE_LOCK)
            })
        } catch (_: Exception) {
            activeLockTarget = null
        }
    }

    private fun isLauncher(pkg: String): Boolean =
        pkg.lowercase().let { it.contains("launcher") || it.contains("home") }

    private fun isTransientSystemWindow(pkg: String): Boolean = pkg.lowercase().let {
        it.contains("systemui") || it.contains("inputmethod") || it.contains("keyboard") ||
            it.contains("honeyboard") || it.contains("biometric") || it.contains("keyguard") ||
            it.contains("securefolder") || it.contains("knox")
    }

    override fun onInterrupt() {}

    companion object {
        private const val APP_PACKAGE = "com.ssecure.applock"
    }
}
