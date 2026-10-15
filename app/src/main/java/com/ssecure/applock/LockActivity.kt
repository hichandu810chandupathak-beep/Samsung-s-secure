package com.ssecure.applock

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

class LockActivity : AppCompatActivity() {
    private lateinit var pm: PrefManager
    private lateinit var dots: TextView
    private lateinit var appName: TextView
    private lateinit var subtitle: TextView
    private lateinit var appIcon: ImageView
    private lateinit var bio: Button
    private lateinit var keypad: GridLayout
    private val pin = StringBuilder()
    private var mode = MODE_LOCK
    private var target = ""
    private var type = "PIN"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawableResource(R.color.lock_bg_dark)
        window.clearFlags(
            WindowManager.LayoutParams.FLAG_DIM_BEHIND or
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
        )
        window.setDimAmount(0f)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        window.statusBarColor = ContextCompat.getColor(this, R.color.lock_bg_dark)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.lock_bg_dark)
        setContentView(R.layout.activity_lock)
        window.setBackgroundDrawableResource(R.color.lock_bg_dark)
        window.decorView.setBackgroundColor(ContextCompat.getColor(this, R.color.lock_bg_dark))
        pm = PrefManager(this)
        dots = findViewById(R.id.tvPinDots)
        appName = findViewById(R.id.tvLockedAppName)
        subtitle = findViewById(R.id.tvLockSubtitle)
        appIcon = findViewById(R.id.ivLockedAppIcon)
        bio = findViewById(R.id.btnKeyBiometric)
        keypad = findViewById(R.id.keypadLayout)
        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_LOCK
        target = intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()
        if (target.isNotEmpty()) type = pm.getAppLockType(target)
        setupUI()
        setupKeypad()
        setupBack()
        if (mode == MODE_LOCK && type == "Fingerprint") bio.post { biometric() }
    }

    private fun setupUI() {
        if (mode == MODE_SET_PIN) {
            appName.text = "Set Master PIN"
            subtitle.text = "Enter your new 4-digit PIN"
            bio.visibility = View.GONE
            keypad.visibility = View.VISIBLE
            subtitle.setOnClickListener { setupEmail() }
            return
        }
        subtitle.text = if (type == "Fingerprint") {
            "Verify with fingerprint or enter your master PIN."
        } else {
            "Enter your master PIN. Tap here for recovery."
        }
        subtitle.setOnClickListener { recover() }
        if (target.isNotEmpty()) try {
            val info = packageManager.getApplicationInfo(target, 0)
            appName.text = packageManager.getApplicationLabel(info)
            appIcon.setImageDrawable(packageManager.getApplicationIcon(info))
        } catch (_: Exception) {
            appName.text = getString(R.string.app_name)
            appIcon.setImageResource(R.mipmap.ic_launcher)
        }
        bio.visibility = if (type == "Fingerprint") View.VISIBLE else View.GONE
        keypad.visibility = View.VISIBLE
        dots.visibility = View.VISIBLE
    }

    private fun setupKeypad() {
        listOf(
            R.id.btnKey0 to "0", R.id.btnKey1 to "1", R.id.btnKey2 to "2", R.id.btnKey3 to "3",
            R.id.btnKey4 to "4", R.id.btnKey5 to "5", R.id.btnKey6 to "6", R.id.btnKey7 to "7",
            R.id.btnKey8 to "8", R.id.btnKey9 to "9"
        ).forEach { (id, digit) ->
            findViewById<Button>(id).setOnClickListener {
                if (pin.length < 4) {
                    pin.append(digit)
                    updateDots()
                    if (pin.length == 4) checkPin()
                }
            }
        }
        findViewById<Button>(R.id.btnKeyBackspace).setOnClickListener {
            if (pin.isNotEmpty()) {
                pin.deleteCharAt(pin.length - 1)
                updateDots()
            }
        }
        bio.setOnClickListener { biometric() }
    }

    private fun updateDots() {
        dots.text = (1..4).joinToString("  ") { if (it <= pin.length) "●" else "○" }
    }

    private fun checkPin() {
        if (mode == MODE_SET_PIN) {
            pm.setPin(pin.toString())
            pin.clear()
            updateDots()
            Toast.makeText(this, "Master PIN updated", Toast.LENGTH_SHORT).show()
            setupEmail()
            return
        }
        if (pin.toString() == pm.getPin()) unlock() else {
            pin.clear()
            updateDots()
            subtitle.text = "Incorrect PIN. Tap here for email recovery."
        }
    }

    private fun setupEmail() {
        val input = EditText(this).apply {
            hint = "Recovery email"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setSingleLine(true)
            setText(pm.getRecoveryEmail())
        }
        AlertDialog.Builder(this).setTitle("Local email recovery")
            .setMessage("Saved on this device only. No email or OTP is sent.")
            .setView(input).setPositiveButton("Save") { _, _ ->
                val email = input.text.toString().trim()
                if (android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    pm.setRecoveryEmail(email)
                    Toast.makeText(this, "Recovery email saved locally", Toast.LENGTH_SHORT).show()
                    finish()
                } else Toast.makeText(this, "Enter a valid email", Toast.LENGTH_LONG).show()
            }.setNegativeButton("Skip") { _, _ -> finish() }.setCancelable(false).show()
    }

    private fun recover() {
        val saved = pm.getRecoveryEmail()
        if (saved.isBlank()) {
            AlertDialog.Builder(this).setTitle("Recovery email not set")
                .setMessage("Set a recovery email while setting the master PIN.")
                .setPositiveButton("Set email") { _, _ -> setupEmail() }
                .setNegativeButton("Cancel", null).show()
            return
        }
        val input = EditText(this).apply {
            hint = "Saved recovery email"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setSingleLine(true)
        }
        AlertDialog.Builder(this).setTitle("Recover PIN")
            .setMessage("Local email match only; nothing is sent.")
            .setView(input).setPositiveButton("Verify") { _, _ ->
                if (input.text.toString().trim().equals(saved, true)) newPinDialog()
                else Toast.makeText(this, "Recovery email doesn't match", Toast.LENGTH_LONG).show()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun newPinDialog() {
        val input = EditText(this).apply {
            hint = "New 4-digit PIN"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setSingleLine(true)
        }
        AlertDialog.Builder(this).setTitle("Reset master PIN").setView(input)
            .setPositiveButton("Reset") { _, _ ->
                val value = input.text.toString()
                if (value.length == 4 && value.all { it.isDigit() }) {
                    pm.setPin(value)
                    pm.clearAppCredentials()
                    pin.clear()
                    updateDots()
                    subtitle.text = "PIN reset. Enter the new PIN."
                    Toast.makeText(this, "PIN reset", Toast.LENGTH_LONG).show()
                } else Toast.makeText(this, "PIN must be exactly 4 digits", Toast.LENGTH_LONG).show()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun biometric() {
        if (isFinishing || isDestroyed) return
        // Android's API selects biometric strength, not a literal modality. Strong auth
        // excludes weak face unlock on devices where face is Class 2.
        val auth = BiometricManager.Authenticators.BIOMETRIC_STRONG
        if (BiometricManager.from(this).canAuthenticate(auth) != BiometricManager.BIOMETRIC_SUCCESS) {
            subtitle.text = "Strong fingerprint biometric unavailable. Use master PIN."
            return
        }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                unlock()
            }
            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                subtitle.text = "Fingerprint not recognized. Try again or use PIN."
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                subtitle.text = "Use fingerprint or enter your master PIN."
            }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("S Secure - Fingerprint")
                .setSubtitle("Verify to unlock ${appName.text}")
                .setAllowedAuthenticators(auth)
                .setNegativeButtonText("Use PIN")
                .build()
        )
    }

    private fun unlock() {
        if (target.isNotEmpty()) {
            pm.addUnlockedApp(target)
            pm.setLastUnlockedPkg(target)
        }
        finish()
    }

    private fun setupBack() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (mode == MODE_SET_PIN) finish() else {
                    startActivity(
                        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
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
