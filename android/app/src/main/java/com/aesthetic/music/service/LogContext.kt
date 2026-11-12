package com.aesthetic.music.service

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.telephony.TelephonyManager
import com.aesthetic.music.BuildConfig
import com.aesthetic.music.data.settings.SyncServerStore
import com.aesthetic.music.data.settings.UserStore
import java.io.File
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

object LogContext {

    private const val TAG = "LogContext"

    @Volatile
    private var appContext: Context? = null

    private var processStartElapsed = 0L

    private var staticCache: Map<String, String>? = null

    @Volatile
    var playerState: (() -> Map<String, String>)? = null

    @Volatile
    var currentPage: (() -> String)? = null

    @Volatile
    var activeSource: (() -> String)? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        processStartElapsed = SystemClock.elapsedRealtime()
    }

    fun snapshot(): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        out.putAll(staticPart())
        safe("mem") {
            val rt = Runtime.getRuntime()
            val usedMb = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024
            out["appHeap"] = "${usedMb}/${rt.maxMemory() / 1024 / 1024}MB"
            appContext?.let { ctx ->
                val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                val mi = ActivityManager.MemoryInfo()
                am?.getMemoryInfo(mi)
                out["sysMemFree"] = "${mi.availMem / 1024 / 1024}MB"
                out["sysMemTotal"] = "${mi.totalMem / 1024 / 1024}MB"
                out["lowMemory"] = mi.lowMemory.toString()
                out["lowRamDevice"] = (am?.isLowRamDevice ?: false).toString()
            }
        }
        safe("storage") {
            appContext?.let { ctx ->
                val st = StatFs(ctx.filesDir.absolutePath)
                out["storageFree"] = "${st.availableBytes / 1024 / 1024}MB"
                out["storageTotal"] = "${st.totalBytes / 1024 / 1024}MB"
                out["storageLow"] = (st.availableBytes < 200L * 1024 * 1024).toString()
                out["extStorage"] = Environment.getExternalStorageState()
            }
        }
        safe("battery") {
            appContext?.let { ctx ->
                val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.let {
                    if (it in 0..100) out["batteryLevel"] = "$it%"
                }
                val intent = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                if (intent != null) {
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    out["charging"] = when (status) {
                        BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
                        BatteryManager.BATTERY_STATUS_FULL -> "full"
                        BatteryManager.BATTERY_STATUS_DISCHARGING -> "no"
                        else -> "unknown"
                    }
                    val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                    if (temp != Int.MIN_VALUE) out["batteryTemp"] = "${temp / 10.0}°C"
                    val powerSave = ctx.getSystemService(android.os.PowerManager::class.java)?.isPowerSaveMode
                    if (powerSave != null) out["powerSave"] = powerSave.toString()
                }
            }
        }
        safe("network") {
            appContext?.let { ctx ->
                val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val net = cm?.activeNetwork
                val caps = net?.let { cm.getNetworkCapabilities(it) }
                out["network"] = when {
                    caps == null -> "none"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
                    else -> "other"
                }
                caps?.let { out["metered"] = (!it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)).toString() }
                caps?.let { out["internet"] = it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).toString() }
                val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                tm?.networkOperatorName?.takeIf { it.isNotBlank() }?.let { out["carrier"] = it }
                @Suppress("DEPRECATION")
                tm?.networkType?.let { out["cellType"] = cellTypeName(it) }
            }
        }
        safe("process") {
            out["uptime"] = fmtDuration(SystemClock.elapsedRealtime() - processStartElapsed)
            out["threads"] = Thread.activeCount().toString()
            appContext?.let { ctx ->
                val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                val procs = am?.runningAppProcesses
                val me = procs?.firstOrNull { it.pid == android.os.Process.myPid() }
                out["foreground"] = when {
                    me == null -> "unknown"
                    me.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "yes"
                    me.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "service"
                    else -> "no"
                }
                out["pid"] = android.os.Process.myPid().toString()
                out["process"] = BuildConfig.APPLICATION_ID
            }
        }
        safe("user") {
            val u = UserStore.user()
            out["loggedIn"] = (u != null).toString()
            if (u != null) {
                out["uid"] = u.id.toString()
                out["username"] = u.username
            }
            SyncServerStore.getServerUrl().takeIf { it.isNotBlank() }?.let { out["server"] = it }
        }
        safe("player") { playerState?.invoke()?.let { out.putAll(it) } }
        safe("page") { currentPage?.invoke()?.takeIf { it.isNotBlank() }?.let { out["page"] = it } }
        safe("source") { activeSource?.invoke()?.takeIf { it.isNotBlank() }?.let { out["source"] = it } }
        return out
    }

    private fun staticPart(): Map<String, String> {
        staticCache?.let { return it }
        val m = LinkedHashMap<String, String>()
        safe("device") {
            m["manufacturer"] = Build.MANUFACTURER.orEmpty()
            m["brand"] = Build.BRAND.orEmpty()
            m["model"] = Build.MODEL.orEmpty()
            m["device"] = Build.DEVICE.orEmpty()
            m["product"] = Build.PRODUCT.orEmpty()
            m["hardware"] = Build.HARDWARE.orEmpty()
            m["abi"] = Build.SUPPORTED_ABIS.joinToString(",")
            m["android"] = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
            m["securityPatch"] = Build.VERSION.SECURITY_PATCH.orEmpty()
            m["buildTags"] = Build.TAGS.orEmpty()
            m["emulator"] = isEmulator().toString()
            m["rooted"] = isProbablyRooted().toString()
        }
        safe("app") {
            m["appVersion"] = BuildConfig.VERSION_NAME
            m["versionCode"] = BuildConfig.VERSION_CODE.toString()
            m["buildType"] = BuildConfig.BUILD_TYPE
            m["debug"] = BuildConfig.DEBUG.toString()
        }
        safe("screen") {
            appContext?.let { ctx ->
                val dm = ctx.resources.displayMetrics
                m["screen"] = "${dm.widthPixels}x${dm.heightPixels}"
                m["density"] = "${dm.densityDpi}dpi"
                m["fontScale"] = String.format(Locale.US, "%.2f", ctx.resources.configuration.fontScale)
                val night = (ctx.resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
                m["darkMode"] = night.toString()
            }
        }
        safe("locale") {
            val loc = Locale.getDefault()
            m["locale"] = "${loc.language}-${loc.country}"
            m["timezone"] = TimeZone.getDefault().id
        }
        staticCache = m
        return m
    }

    fun snapshotOrEmpty(): Map<String, String> = try {
        snapshot()
    } catch (_: Exception) {
        emptyMap()
    }

    private inline fun safe(name: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            android.util.Log.w(TAG, "采集 $name 失败: ${e.message}")
        }
    }

    // EVDO/CDMA/1xRTT 这些常量在 API 30 被标记弃用，但 getDataNetworkType() 仍会返回它们，
    // 作为诊断信息保留映射不会影响新设备。
    @Suppress("DEPRECATION")
    private fun cellTypeName(type: Int): String = when (type) {
        TelephonyManager.NETWORK_TYPE_NR -> "5G"
        TelephonyManager.NETWORK_TYPE_LTE -> "4G"
        TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_HSPA,
        TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA,
        TelephonyManager.NETWORK_TYPE_UMTS, TelephonyManager.NETWORK_TYPE_EVDO_0,
        TelephonyManager.NETWORK_TYPE_EVDO_A, TelephonyManager.NETWORK_TYPE_EVDO_B -> "3G"
        TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_GPRS,
        TelephonyManager.NETWORK_TYPE_CDMA, TelephonyManager.NETWORK_TYPE_1xRTT -> "2G"
        else -> "type$type"
    }

    private fun isEmulator(): Boolean = try {
        Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.contains("emulator") ||
            Build.MODEL.contains("google_sdk", true) ||
            Build.MODEL.contains("Emulator", true) ||
            Build.MODEL.contains("Android SDK built for", true) ||
            Build.PRODUCT.contains("sdk_gphone", true) ||
            Build.HARDWARE.contains("goldfish") || Build.HARDWARE.contains("ranchu")
    } catch (_: Exception) {
        false
    }

    private fun isProbablyRooted(): Boolean = try {
        val paths = arrayOf(
            "/system/app/Superuser.apk", "/sbin/su", "/system/bin/su", "/system/xbin/su",
            "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su",
            "/system/bin/failsafe/su", "/data/local/su", "/su/bin/su",
        )
        paths.any { File(it).exists() } || Build.TAGS?.contains("test-keys") == true
    } catch (_: Exception) {
        false
    }

    private fun fmtDuration(ms: Long): String {
        val h = TimeUnit.MILLISECONDS.toHours(ms)
        val m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
        val s = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
        return if (h > 0) "${h}h${m}m" else if (m > 0) "${m}m${s}s" else "${s}s"
    }
}
