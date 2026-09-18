package com.reganbarua.jujukeys.voice

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Transparent, real permission-request Activity. An InputMethodService cannot call
 * requestPermissions() itself, so tapping the mic button while permission is missing
 * launches this Activity, which asks Android's real permission dialog and reports the
 * result back to the still-running keyboard service via [MicPermissionBus].
 */
class MicPermissionActivity : Activity() {

    private val requestCode = 4201

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            MicPermissionBus.postResult(true)
            finish()
            return
        }
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), requestCode)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        MicPermissionBus.postResult(granted)
        finish()
    }
}
