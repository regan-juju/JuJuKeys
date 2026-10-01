package com.reganbarua.jujukeys.security

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.widget.Toast
import androidx.annotation.RequiresApi

/**
 * A see-through screen that only asks for the phone's own lock (PIN / pattern / password, or
 * fingerprint / face where the phone offers it) before a sensitive clipboard item is shown,
 * pasted, copied or sent to Keep. JuJuKeys never sees the PIN or the fingerprint — Android
 * checks it and only answers "yes" or "no".
 */
class AuthActivity : Activity() {

    private val cancel = CancellationSignal()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!km.isDeviceSecure) {
            Toast.makeText(this, "ফোনে স্ক্রিন লক (PIN/প্যাটার্ন) নেই — সংবেদনশীল লেখা খুলতে আগে স্ক্রিন লক চালু করুন", Toast.LENGTH_LONG).show()
            done(false); return
        }
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> biometric {
                setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            }
            Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> biometric {
                @Suppress("DEPRECATION") setDeviceCredentialAllowed(true)
            }
            else -> {
                @Suppress("DEPRECATION")
                val i = km.createConfirmDeviceCredentialIntent("JuJuKeys", "সংবেদনশীল লেখা খুলতে ফোনের লক দিন")
                if (i == null) done(false) else startActivityForResult(i, REQ)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun biometric(configure: BiometricPrompt.Builder.() -> Unit) {
        val prompt = BiometricPrompt.Builder(this)
            .setTitle("JuJuKeys")
            .setSubtitle("সংবেদনশীল লেখা খুলতে ফোনের লক দিন")
            .apply(configure)
            .build()
        prompt.authenticate(cancel, mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) = done(true)
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) = done(false)
        })
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ) done(resultCode == RESULT_OK)
    }

    private var finished = false
    private fun done(ok: Boolean) {
        if (finished) return
        finished = true
        if (ok) SensitiveGate.unlock() else SensitiveGate.cancelPending()
        finish()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        if (!finished) { finished = true; SensitiveGate.cancelPending() }
        cancel.cancel()
        super.onDestroy()
    }

    companion object {
        private const val REQ = 41

        fun start(context: Context) {
            context.startActivity(
                Intent(context, AuthActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            )
        }
    }
}
