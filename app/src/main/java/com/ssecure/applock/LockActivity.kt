package com.ssecure.applock

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
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

    private var enteredPin = StringBuilder()
    private var mode: String = MODE_LOCK
    private var targetPackage: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock)

        prefManager = PrefManager(this)
        tvPinDots = findViewById(R.id.tvPinDots)
        tvLockedAppName = findViewById(R.id.tvLockedAppName)
        tvLockSubtitle = findViewById(R.id.tvLockSubtitle)
        ivLockedAppIcon = findViewById(R.id.ivLockedAppIcon)
        btnKeyBiometric = findViewById(R.id.btnKeyBiometric)

        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_LOCK
        targetPackage = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""

        setupModeUI()
        setupKeypad()
        setupBackNavigation()

        if (mode == MODE_LOCK) {
            triggerBiometricIfAvailable()
        }
    }

    private fun setupModeUI() {
        if (mode == MODE_SET_PIN) {
            tvLockedAppName.text = "Set 4-Digit PIN"
            tvLockSubtitle.text = "Enter a new 4-digit PIN for S Secure"
            btnKeyBiometric.visibility = View.INVISIBLE
        } else {
            btnKeyBiometric.visibility = View.VISIBLE
            if (targetPackage.isNotEmpty()) {
                try {
                    val appInfo = packageManager.getApplicationInfo(targetPackage, 0)
                    tvLockedAppName.text = packageManager.getApplicationLabel(appInfo)
                    ivLockedAppIcon.setImageDrawable(packageManager.getApplicationIcon(appInfo))
                } catch (_: Exception) {
                    tvLockedAppName.text = getString(R.string.app_name)
                }
            }
        }
    }

    private fun setupKeypad() {
        val digitButtons = listOf(
            R.id.btnKey0 to "0",
            R.id.btnKey1 to "1",
            R.id.btnKey2 to "2",
            R.id.btnKey3 to "3",
            R.id.btnKey4 to "4",
            R.id.btnKey5 to "5",
            R.id.btnKey6 to "6",
            R.id.btnKey7 to "7",
            R.id.btnKey8 to "8",
            R.id.btnKey9 to "9"
        )

        for ((id, digit) in digitButtons) {
            findViewById<Button>(id).setOnClickListener {
                onDigitPressed(digit)
            }
        }

        findViewById<Button>(R.id.btnKeyBackspace).setOnClickListener {
            if (enteredPin.isNotEmpty()) {
                enteredPin.deleteCharAt(enteredPin.length - 1)
                updatePinDots()
            }
        }

        btnKeyBiometric.setOnClickListener {
            triggerBiometricIfAvailable()
        }
    }

    private fun onDigitPressed(digit: String) {
        if (enteredPin.length < 4) {
            enteredPin.append(digit)
            updatePinDots()

            if (enteredPin.length == 4) {
                handleCompletePin(enteredPin.toString())
            }
        }
    }

    private fun updatePinDots() {
        val dots = (1..4).joinToString("  ") { index ->
            if (index <= enteredPin.length) "●" else "○"
        }
        tvPinDots.text = dots
    }

    private fun handleCompletePin(pin: String) {
        if (mode == MODE_SET_PIN) {
            prefManager.setPin(pin)
            Toast.makeText(this, "PIN updated successfully", Toast.LENGTH_SHORT).show()
            finish()
        } else {
            if (pin == prefManager.getPin()) {
                onUnlockSuccess()
            } else {
                enteredPin.clear()
                updatePinDots()
                tvLockSubtitle.text = "Incorrect PIN. Try again."
            }
        }
    }

    private fun triggerBiometricIfAvailable() {
        val biometricManager = BiometricManager.from(this)
        val canAuth = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.BIOMETRIC_STRONG
        )
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) return

        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onUnlockSuccess()
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("S Secure App Lock")
            .setSubtitle("Verify fingerprint to unlock ${tvLockedAppName.text}")
            .setNegativeButtonText("Use PIN")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun onUnlockSuccess() {
        if (targetPackage.isNotEmpty()) {
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
