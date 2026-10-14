package com.ssecure.applock

import android.content.Context
import android.content.SharedPreferences

class PrefManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("s_secure_prefs", Context.MODE_PRIVATE)

    fun getLockedPackages(): Set<String> {
        return prefs.getStringSet(KEY_LOCKED_PACKAGES, emptySet())?.toSet() ?: emptySet()
    }

    fun setAppLocked(packageName: String, locked: Boolean) {
        val current = getLockedPackages().toMutableSet()
        if (locked) {
            current.add(packageName)
        } else {
            current.remove(packageName)
        }
        prefs.edit().putStringSet(KEY_LOCKED_PACKAGES, current).apply()
    }

    fun isAppLocked(packageName: String): Boolean {
        return getLockedPackages().contains(packageName)
    }

    fun getPin(): String {
        return prefs.getString(KEY_PIN, "1234") ?: "1234"
    }

    fun setPin(pin: String) {
        prefs.edit().putString(KEY_PIN, pin).apply()
    }

    fun isServiceEnabled(): Boolean {
        return prefs.getBoolean(KEY_SERVICE_ENABLED, true)
    }

    fun setServiceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply()
    }

    fun getLastUnlockedPkg(): String {
        return prefs.getString(KEY_LAST_UNLOCKED, "") ?: ""
    }

    fun setLastUnlockedPkg(packageName: String) {
        prefs.edit().putString(KEY_LAST_UNLOCKED, packageName).apply()
    }

    companion object {
        private const val KEY_LOCKED_PACKAGES = "locked_packages"
        private const val KEY_PIN = "pin_code"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_LAST_UNLOCKED = "last_unlocked_pkg"
    }
}
