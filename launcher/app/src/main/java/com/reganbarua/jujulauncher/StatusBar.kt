package com.reganbarua.jujulauncher

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.telephony.TelephonyManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class StatusInfo(
    val time: String = "",
    val battery: Int = 100,
    val charging: Boolean = false,
    val wifi: Int = -1,          // -1 = not connected, 0..4 bars
    val cell: Int = -1,          // -1 = unknown/no permission, 0..4 bars
    val net: String = "",        // 5G / 4G / 3G / E (only when mobile data is the active network)
)

/** Pure helpers (unit tested). */
object StatusMath {
    fun batteryPercent(level: Int, scale: Int): Int =
        if (level < 0 || scale <= 0) 100 else (level * 100 / scale).coerceIn(0, 100)

    fun netLabel(type: Int): String = when (type) {
        20 -> "5G"                       // NR
        13, 19 -> "4G"                   // LTE, LTE_CA
        3, 8, 9, 10, 15, 17 -> "3G"      // UMTS, HSDPA, HSUPA, HSPA, HSPAP, TD_SCDMA
        1, 2, 4, 7, 11, 16 -> "E"        // GPRS, EDGE, CDMA, 1xRTT, IDEN, GSM
        else -> ""
    }

    fun wifiBars(rssi: Int): Int = when {
        rssi >= -55 -> 4
        rssi >= -67 -> 3
        rssi >= -78 -> 2
        rssi >= -90 -> 1
        else -> 0
    }

    fun hourMinute(h24: Int, m: Int): String {
        val h = if (h24 % 12 == 0) 12 else h24 % 12
        return "$h:${m.toString().padStart(2, '0')}"
    }
}

@SuppressLint("MissingPermission")
@Composable
fun rememberStatus(permTick: Int): State<StatusInfo> {
    val ctx = LocalContext.current
    val state = remember { mutableStateOf(StatusInfo(time = timeNow())) }

    DisposableEffect(ctx) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                when (i?.action) {
                    Intent.ACTION_BATTERY_CHANGED -> {
                        val pct = StatusMath.batteryPercent(i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), i.getIntExtra(BatteryManager.EXTRA_SCALE, 100))
                        val s = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                        val chg = s == BatteryManager.BATTERY_STATUS_CHARGING || s == BatteryManager.BATTERY_STATUS_FULL
                        state.value = state.value.copy(battery = pct, charging = chg)
                    }
                    else -> state.value = state.value.copy(time = timeNow())
                }
            }
        }
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED); addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ctx.registerReceiver(r, f)?.let { r.onReceive(ctx, it) }   // sticky battery value right away
        onDispose { ctx.unregisterReceiver(r) }
    }

    LaunchedEffect(permTick) {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        while (true) {
            var wifi = -1; var net = ""
            try {
                val caps = cm.getNetworkCapabilities(cm.activeNetwork)
                if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                    @Suppress("DEPRECATION")
                    wifi = wm?.connectionInfo?.rssi?.let { StatusMath.wifiBars(it) } ?: 3
                }
            } catch (_: Throwable) {}
            val phoneOk = ctx.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
            var cell = -1
            if (phoneOk && tm != null) {
                try {
                    cell = tm.signalStrength?.level ?: -1
                    val caps = cm.getNetworkCapabilities(cm.activeNetwork)
                    if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true) net = StatusMath.netLabel(tm.dataNetworkType)
                } catch (_: Throwable) {}
            }
            state.value = state.value.copy(wifi = wifi, cell = cell, net = net, time = timeNow())
            delay(4000)
        }
    }
    return state
}

private fun timeNow(): String {
    val c = java.util.Calendar.getInstance()
    return StatusMath.hourMinute(c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE))
}

@Composable
fun IosStatusBar(info: StatusInfo, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 28.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(info.time, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (info.cell >= 0) SignalBars(info.cell)
            if (info.net.isNotEmpty() && info.wifi < 0) Text(info.net, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            if (info.wifi >= 0) WifiIcon(info.wifi)
            BatteryIcon(info.battery, info.charging)
        }
    }
}

@Composable
private fun SignalBars(level: Int) {
    Canvas(Modifier.size(18.dp, 12.dp)) {
        val w = size.width / 4f
        for (i in 0..3) {
            val h = size.height * (0.33f + i * 0.22f)
            drawRoundRect(if (i < level) Color.White else Color(0x59FFFFFF), Offset(i * w + w * 0.1f, size.height - h), Size(w * 0.72f, h), CornerRadius(1.5f, 1.5f))
        }
    }
}

@Composable
private fun WifiIcon(level: Int) {
    Canvas(Modifier.size(17.dp, 12.dp)) {
        val cx = size.width / 2f; val base = size.height
        val sw = size.height * 0.16f
        drawCircle(Color.White, sw * 0.9f, Offset(cx, base - sw))
        for (i in 1..3) {
            val r = size.height * (0.38f * i)
            val on = level >= i + 1 || (i == 1 && level >= 1) || (level >= i)
            drawArc(
                if (level >= i) Color.White else Color(0x59FFFFFF),
                startAngle = -135f, sweepAngle = 90f, useCenter = false,
                topLeft = Offset(cx - r, base - sw - r), size = Size(r * 2, r * 2),
                style = Stroke(sw, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun BatteryIcon(pct: Int, charging: Boolean) {
    val fill = when { charging -> Color(0xFF30D158); pct <= 15 -> Color(0xFFFF3B30); else -> Color.White }
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(28.dp, 13.dp)) {
            val bodyW = size.width - 3.dp.toPx()
            drawRoundRect(Color(0x66FFFFFF), Offset.Zero, Size(bodyW, size.height), CornerRadius(4.dp.toPx()), style = Stroke(1.dp.toPx()))
            val pad = 1.8.dp.toPx()
            val fw = ((bodyW - pad * 2) * pct / 100f).coerceAtLeast(pad)
            drawRoundRect(fill, Offset(pad, pad), Size(fw, size.height - pad * 2), CornerRadius(2.5.dp.toPx()))
            drawRoundRect(Color(0x66FFFFFF), Offset(bodyW + 1.dp.toPx(), size.height * 0.33f), Size(1.6.dp.toPx(), size.height * 0.34f), CornerRadius(1f))
        }
        Text(pct.toString(), color = if (pct >= 50 || charging) Color.Black else Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.offset(x = (-1.5).dp))
    }
}
