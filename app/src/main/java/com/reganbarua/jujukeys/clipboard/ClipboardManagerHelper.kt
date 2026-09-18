package com.reganbarua.jujukeys.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent

/**
 * Thin wrapper around the real Android ClipboardManager plus the "Save to Google Keep"
 * (or any note app) share action via ACTION_SEND / the system Sharesheet.
 *
 * We never touch Google Keep's private database or any private API — this only uses
 * the public Android share intent, which is the legitimate, documented way to hand text
 * to another app.
 */
class ClipboardManagerHelper(private val context: Context) {

    private val systemClipboard: ClipboardManager
        get() = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    /** Call this when the app becomes visible/active to pick up whatever the user last copied. */
    fun syncFromSystemClipboard() {
        val clip = systemClipboard.primaryClip ?: return
        if (clip.itemCount == 0) return
        val text = clip.getItemAt(0).coerceToText(context)?.toString() ?: return
        ClipboardHistoryStore.push(text)
    }

    fun copyToSystemClipboard(text: String) {
        systemClipboard.setPrimaryClip(ClipData.newPlainText("JuJuKeys", text))
    }

    /**
     * Opens the system Sharesheet targeted at Google Keep if it's installed, falling back
     * to the normal Sharesheet (any note/notes app, Drive, etc.) otherwise. Never claims
     * a private "saved to Keep" integration — it's always the standard share flow.
     */
    fun shareToKeepOrSharesheet(text: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val keepPackage = "com.google.android.keep"
        val isKeepInstalled = runCatching {
            context.packageManager.getPackageInfo(keepPackage, 0)
        }.isSuccess

        val chooserIntent = if (isKeepInstalled) {
            Intent(sendIntent).apply { setPackage(keepPackage) }
        } else {
            Intent.createChooser(sendIntent, "Share to").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        runCatching { context.startActivity(chooserIntent) }
            .onFailure {
                // Keep app not reachable directly (e.g. no matching activity) -> fall back to chooser.
                runCatching {
                    context.startActivity(
                        Intent.createChooser(sendIntent, "Share to").apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                }
            }
    }
}
