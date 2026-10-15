package com.ssecure.applock

import android.app.admin.DeviceAdminReceiver

/**
 * Device administrator receiver. Android can require admin deactivation before uninstall,
 * but a normal device-admin app cannot enforce its own Master PIN in system Settings.
 */
class AppAdminReceiver : DeviceAdminReceiver()
