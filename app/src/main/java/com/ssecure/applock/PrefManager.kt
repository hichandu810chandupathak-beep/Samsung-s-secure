package com.ssecure.applock

import android.content.Context
import android.content.SharedPreferences

class PrefManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("s_secure_prefs", Context.MODE_PRIVATE)
    fun getLockedPackages(): Set<String> = prefs.getStringSet(KEY_LOCKED_PACKAGES, emptySet())?.toSet() ?: emptySet()
    fun setAppLocked(packageName: String, locked: Boolean) {
        val current = getLockedPackages().toMutableSet()
        if (locked) {
            current.add(packageName)
            if (!prefs.contains("lock_type_$packageName")) prefs.edit().putString("lock_type_$packageName", "PIN").apply()
        } else {
            current.remove(packageName)
            prefs.edit().remove("lock_type_$packageName").apply()
            clearUnlockedSession(packageName)
        }
        prefs.edit().putStringSet(KEY_LOCKED_PACKAGES, current).apply()
    }
    fun isAppLocked(packageName: String) = getLockedPackages().contains(packageName)
    fun getAppLockType(packageName: String) = prefs.getString("lock_type_$packageName", "PIN") ?: "PIN"
    fun setAppLockType(packageName: String, type: String) { prefs.edit().putString("lock_type_$packageName", type).apply() }
    fun getPin() = prefs.getString(KEY_PIN, "1234") ?: "1234"
    fun hasPinSet() = prefs.contains(KEY_PIN)
    fun setPin(pin: String) { prefs.edit().putString(KEY_PIN, pin).apply() }
    fun getRecoveryEmail() = prefs.getString(KEY_RECOVERY_EMAIL, "") ?: ""
    fun setRecoveryEmail(email: String) { prefs.edit().putString(KEY_RECOVERY_EMAIL, email.trim().lowercase()).apply() }
    fun getPattern(packageName: String) = prefs.getString("pattern_$packageName", "") ?: ""
    fun setPattern(packageName: String, pattern: String) { prefs.edit().putString("pattern_$packageName", pattern).apply() }
    fun getAppPassword(packageName: String) = prefs.getString("password_$packageName", "") ?: ""
    fun setAppPassword(packageName: String, password: String) { prefs.edit().putString("password_$packageName", password).apply() }
    fun clearAppCredentials() {
        val edit = prefs.edit()
        prefs.all.keys.filter { it.startsWith("password_") || it.startsWith("pattern_") }.forEach { edit.remove(it) }
        edit.apply()
    }
    fun isServiceEnabled() = prefs.getBoolean(KEY_SERVICE_ENABLED, true)
    fun setServiceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply()
        if (!enabled) clearUnlockedSessions()
    }
    fun addUnlockedApp(packageName: String) {
        val unlocked = getUnlockedSessionApps().toMutableSet(); unlocked.add(packageName)
        prefs.edit().putStringSet(KEY_UNLOCKED_SESSION_APPS, unlocked).apply()
    }
    fun isAppUnlockedInSession(packageName: String) = getUnlockedSessionApps().contains(packageName)
    fun clearUnlockedSession(packageName: String) {
        val unlocked = getUnlockedSessionApps().toMutableSet(); unlocked.remove(packageName)
        prefs.edit().putStringSet(KEY_UNLOCKED_SESSION_APPS, unlocked).apply()
        if (getLastUnlockedPkg() == packageName) setLastUnlockedPkg("")
    }
    fun clearUnlockedSessions() { prefs.edit().remove(KEY_UNLOCKED_SESSION_APPS).putString(KEY_LAST_UNLOCKED, "").apply() }
    fun getLastUnlockedPkg() = prefs.getString(KEY_LAST_UNLOCKED, "") ?: ""
    fun setLastUnlockedPkg(packageName: String) { prefs.edit().putString(KEY_LAST_UNLOCKED, packageName).apply() }
    private fun getUnlockedSessionApps() = prefs.getStringSet(KEY_UNLOCKED_SESSION_APPS, emptySet())?.toSet() ?: emptySet()
    companion object {
        private const val KEY_LOCKED_PACKAGES = "locked_packages"
        private const val KEY_PIN = "pin_code"
        private const val KEY_RECOVERY_EMAIL = "recovery_email"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_LAST_UNLOCKED = "last_unlocked_pkg"
        private const val KEY_UNLOCKED_SESSION_APPS = "unlocked_session_apps"
    }
}
