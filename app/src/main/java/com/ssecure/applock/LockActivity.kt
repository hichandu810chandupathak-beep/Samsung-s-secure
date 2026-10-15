package com.ssecure.applock

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

class LockActivity : AppCompatActivity() {

    private lateinit var prefManager: PrefManager
    private lateinit var tvPinDots: TextView
    private lateinit var tvLockedAppName: TextView
    private lateinit var tvLockSubtitle: TextView
    private lateinit var ivLockedAppIcon: ImageView
    private lateinit var btnKeyBiometric: Button
    private lateinit var keypadLayout: GridLayout

    private val enteredPin = StringBuilder()
    private var mode: String = MODE_LOCK
    private var targetPackage: String = ""
    private var appLockType: String = "PIN"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock)

        prefManager = PrefManager(this)
        tvPinDots = findViewById(R.id.tvPinDots)
        tvLockedAppName = findViewById(R.id.tvLockedAppName)
        tvLockSubtitle = findViewById(R.id.tvLockSubtitle)
        ivLockedAppIcon = findViewById(R.id.ivLockedAppIcon)
        btnKeyBiometric = findViewById(R.id.btnKeyBiometric)
        keypadLayout = findViewById(R.id.keypadLayout)

        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_LOCK
        targetPackage = intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()
        if (targetPackage.isNotEmpty()) {
            appLockType = prefManager.getAppLockType(targetPackage)
        }

        setupModeUI()
        setupKeypad()
        setupBackNavigation()
        updatePinDots()

        if (mode == MODE_LOCK && (appLockType == "Fingerprint" || appLockType == "Face")) {
            btnKeyBiometric.post { triggerBiometricPrompt() }
        }
    }

    private fun setupModeUI() {
        if (mode == MODE_SET_PIN) {
            tvLockedAppName.text = "Set Master PIN"
            tvLockSubtitle.text = "Enter your new 4-digit PIN"
            btnKeyBiometric.visibility = View.GONE
            keypadLayout.visibility = View.VISIBLE
            return
        }

        tvLockSubtitle.text = when (appLockType) {
            "Fingerprint" -> "Verify with fingerprint or use your master PIN"
            "Face" -> "Verify with device biometrics or use your master PIN"
            "Password" -> "Enter your 4-digit master PIN"
            "Pattern" -> "Pattern entry is unavailable; use your master PIN"
            else -> "Enter your 4-digit master PIN"
        }

        // Keep the keypad visible as a fallback for every selected lock type.
        keypadLayout.visibility = View.VISIBLE
        btnKeyBiometric.visibility =
            if (appLockType == "Fingerprint" || appLockType == "Face") View.VISIBLE else View.GONE

        if (targetPackage.isNotEmpty()) {
            try {
                val appInfo = packageManager.getApplicationInfo(targetPackage, 0)
                tvLockedAppName.text = packageManager.getApplicationLabel(appInfo)
                ivLockedAppIcon.setImageDrawable(packageManager.getApplicationIcon(appInfo))
            } catch (_: Exception) {
                tvLockedAppName.text = getString(R.string.app_name)
                ivLockedAppIcon.setImageResource(R.mipmap.ic_launcher)
            }
        }
    }

    private fun setupKeypad() {
        val digitButtons = listOf(
            R.id.btnKey0 to "0", R.id.btnKey1 to "1", R.id.btnKey2 to "2",
            R.id.btnKey3 to "3", R.id.btnKey4 to "4", R.id.btnKey5 to "5",
            R.id.btnKey6 to "6", R.id.btnKey7 to "7", R.id.btnKey8 to "8",
            R.id.btnKey9 to "9"
        )

        for ((id, digit) in digitButtons) {
            findViewById<Button>(id).setOnClickListener { onDigitPressed(digit) }
        }

        findViewById<Button>(R.id.btnKeyBackspace).setOnClickListener {
            if (enteredPin.isNotEmpty()) {
                enteredPin.deleteCharAt(enteredPin.length - 1)
                updatePinDots()
            }
        }
        btnKeyBiometric.setOnClickListener { triggerBiometricPrompt() }
    }

    private fun onDigitPressed(digit: String) {
        if (enteredPin.length >= 4) return
        enteredPin.append(digit)
        updatePinDots()
        if (enteredPin.length == 4) handleCompletePin(enteredPin.toString())
    }

    private fun updatePinDots() {
        tvPinDots.text = (1..4).joinToString("  ") { index ->
            if (index <= enteredPin.length) "●" else "○"
        }
    }

    private fun handleCompletePin(pin: String) {
        if (mode == MODE_SET_PIN) {
            prefManager.setPin(pin)
            Toast.makeText(this, "Master PIN updated successfully", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (pin == prefManager.getPin()) {
            onUnlockSuccess()
        } else {
            enteredPin.clear()
            updatePinDots()
            tvLockSubtitle.text = "Incorrect PIN. Try again."
        }
    }

    private fun triggerBiometricPrompt() {
        if (isFinishing || isDestroyed) return

        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK
        val canAuth = BiometricManager.from(this).canAuthenticate(authenticators)
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            tvLockSubtitle.text = "Biometric unavailable. Enter your master PIN."
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onUnlockSuccess()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    tvLockSubtitle.text = "Not recognized. Try again or use your master PIN."
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    tvLockSubtitle.text = if (
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    ) {
                        "Enter your master PIN to unlock."
                    } else {
                        "Biometric unavailable. Enter your master PIN."
                    }
                }
            }
        )

        val title = if (appLockType == "Face") {
            "S Secure - Face / Biometric Unlock"
        } else {
            "S Secure - Fingerprint Unlock"
        }
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle("Verify to unlock ${tvLockedAppName.text}")
            .setAllowedAuthenticators(authenticators)
            .setNegativeButtonText("Use PIN")
            .build()

        prompt.authenticate(promptInfo)
    }

    private fun onUnlockSuccess() {
        if (targetPackage.isNotEmpty()) {
            prefManager.addUnlockedApp(targetPackage)
            prefManager.setLastUnlockedPkg(targetPackage)
        }
        finish()
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (mode == MODE_SET_PIN) {
                    finish()
                } else {
                    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(homeIntent)
                    finish()
                }
            }
        })
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_MODE = "extra_mode"
        const val MODE_LOCK = "LOCK"
        const val MODE_SET_PIN = "SET_PIN"
    }
}
