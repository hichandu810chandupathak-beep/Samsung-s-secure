package com.ssecure.applock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class AppLockAccessibilityService : AccessibilityService() {
    private lateinit var prefManager: PrefManager
    override fun onServiceConnected() {
        super.onServiceConnected()
        prefManager = PrefManager(this)
        prefManager.clearUnlockedSessions()
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (!::prefManager.isInitialized || !prefManager.isServiceEnabled()) return
        val pkg = event.packageName?.toString() ?: return
        if (isTransientSystemWindow(pkg)) return
        if (pkg == APP_PACKAGE) {
            val cls = event.className?.toString().orEmpty()
            if (!cls.endsWith("MainActivity") || !prefManager.hasPinSet()) return
            if (prefManager.isAppUnlockedInSession(APP_PACKAGE) && prefManager.getLastUnlockedPkg() == APP_PACKAGE) return
            launchLock(APP_PACKAGE)
            return
        }
        if (isLauncher(pkg)) { prefManager.clearUnlockedSessions(); return }
        val last = prefManager.getLastUnlockedPkg()
        if (last.isNotEmpty() && pkg != last) prefManager.clearUnlockedSessions()
        if (prefManager.isAppLocked(pkg) && !prefManager.isAppUnlockedInSession(pkg) && prefManager.getLastUnlockedPkg() != pkg) launchLock(pkg)
    }
    private fun launchLock(pkg: String) {
        startActivity(Intent(this, LockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(LockActivity.EXTRA_PACKAGE_NAME, pkg)
            putExtra(LockActivity.EXTRA_MODE, LockActivity.MODE_LOCK)
        })
    }
    private fun isLauncher(pkg: String): Boolean = pkg.lowercase().let { it.contains("launcher") || it.contains("home") }
    private fun isTransientSystemWindow(pkg: String): Boolean = pkg.lowercase().let {
        it.contains("systemui") || it.contains("inputmethod") || it.contains("keyboard") || it.contains("honeyboard")
    }
    override fun onInterrupt() {}
    companion object { private const val APP_PACKAGE = "com.ssecure.applock" }
}
