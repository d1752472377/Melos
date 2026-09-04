package com.aesthetic.music.service

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import com.aesthetic.music.R
import java.net.HttpURLConnection
import java.net.URL

/**
 * 系统悬浮窗胶囊（类灵动岛）——移植自参考 App 的 FloatCapsuleManager。
 * 用 ApplicationContext + TYPE_APPLICATION_OVERLAY 实现跨 App 悬浮显示。
 * 支持拖动、点击回 App、播放/暂停控制。
 */
object FloatCapsuleManager {
    private var windowManager: WindowManager? = null
    private var capsuleView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var onPlayPauseTap: (() -> Unit)? = null
    private var onCapsuleTap: (() -> Unit)? = null
    /** 当前已展示的封面 URL：未变化时不重新下载图片 */
    private var currentCoverUrl: String? = null

    private var initialX = 0f
    private var initialY = 0f
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    /** 拖拽位置记忆（SharedPreferences）：此前每次重新展示都回到 y=100px 固定位 */
    private var positionPrefs: android.content.SharedPreferences? = null

    private fun prefsOf(context: Context): android.content.SharedPreferences =
        positionPrefs ?: context.applicationContext
            .getSharedPreferences("aesthetic_music_capsule", Context.MODE_PRIVATE)
            .also { positionPrefs = it }

    private fun loadCapsulePosition(context: Context): Pair<Int, Int>? {
        val p = prefsOf(context)
        if (!p.contains("pos_x") || !p.contains("pos_y")) return null
        return p.getInt("pos_x", 0) to p.getInt("pos_y", 0)
    }

    private fun saveCapsulePosition(context: Context, x: Int, y: Int) {
        try {
            prefsOf(context).edit().putInt("pos_x", x).putInt("pos_y", y).apply()
        } catch (_: Exception) {
        }
    }

    /** 用 isAttachedToWindow 判定：权限被系统收回时窗口已被移除，
     *  此前 capsuleView 残留导致 isShowing() 失真、状态无法自愈 */
    fun isShowing(): Boolean = capsuleView?.isAttachedToWindow == true

    fun hasPermission(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun openPermissionSettings(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    fun show(
        context: Context,
        title: String,
        artist: String,
        coverUrl: String?,
        isPlaying: Boolean,
        onPlayPause: () -> Unit,
        onTap: () -> Unit,
    ) {
        currentCoverUrl = coverUrl
        if (capsuleView != null) {
            update(title, artist, coverUrl, isPlaying)
            return
        }
        if (!hasPermission(context)) return
        if (windowManager == null) {
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        }
        onPlayPauseTap = onPlayPause
        onCapsuleTap = onTap

        val view = LayoutInflater.from(context).inflate(R.layout.float_capsule, null)
        val titleView = view.findViewById<TextView>(R.id.fc_title)
        val artistView = view.findViewById<TextView>(R.id.fc_artist)
        val coverView = view.findViewById<ImageView>(R.id.fc_cover)
        val playBtn = view.findViewById<ImageButton>(R.id.fc_play)

        titleView.text = title
        artistView.text = artist
        playBtn.setImageResource(if (isPlaying) R.drawable.ic_pause_white else R.drawable.ic_play_white)
        if (!coverUrl.isNullOrEmpty()) {
            loadImage(coverUrl, coverView)
        }

        playBtn.setOnClickListener { onPlayPauseTap?.invoke() }

        // 手势判定与"回 App"统一放在 touch listener 里处理。
        // 此前逻辑颠倒：OnTouchListener 对 DOWN/MOVE/UP 全 return true 消费事件后，
        // View 的 OnClickListener 永远不会被手势触发 → 轻点胶囊"回 App"完全失效；
        // 而 ACTION_UP 里 `if (isDragging) performClick()` 恰恰在"拖动松手"时手动触发
        // 点击 → 一拖动就把用户从其他 App 拽回 Melos。现在：轻点(未拖动)= 回 App，拖动只改位置。
        view.setOnTouchListener { v, event ->
            val params = layoutParams
            if (params == null) return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x.toFloat()
                    initialY = params.y.toFloat()
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (!isDragging && (Math.abs(dx) > 8 || Math.abs(dy) > 8)) isDragging = true
                    if (isDragging) {
                        // 屏幕边界 clamp：此前可把胶囊拖出屏外且找不回来
                        val dm = v.resources.displayMetrics
                        val maxX = (dm.widthPixels / 2f - v.width / 2f).toInt().coerceAtLeast(0)
                        val minX = (-dm.widthPixels / 2f + v.width / 2f).toInt().coerceAtMost(0)
                        val maxY = (dm.heightPixels - v.height).toInt().coerceAtLeast(0)
                        params.x = (initialX + dx).toInt().coerceIn(minX, maxX)
                        params.y = (initialY + dy).toInt().coerceIn(0, maxY)
                        windowManager?.updateViewLayout(v, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        // 轻点（未拖动）= 回 App
                        onCapsuleTap?.invoke()
                        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
                            ?.apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                            }
                        if (launch != null) context.startActivity(launch)
                    } else {
                        // 拖动结束：记住位置，下次展示沿用
                        saveCapsulePosition(context, params.x, params.y)
                    }
                    isDragging = false
                    true
                }
                else -> false
            }
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        // 恢复上次拖拽位置；首次展示默认距顶 100dp（此前 y=100 是像素硬编码，不同密度屏位置差异大且不持久化）
        val saved = loadCapsulePosition(context)
        params.x = saved?.first ?: 0
        params.y = saved?.second ?: (100 * context.resources.displayMetrics.density).toInt()

        layoutParams = params
        capsuleView = view
        try {
            windowManager?.addView(view, params)
        } catch (_: Exception) {
            capsuleView = null
        }
    }

    fun update(title: String, artist: String, coverUrl: String?, isPlaying: Boolean) {
        val view = capsuleView ?: return
        Handler(Looper.getMainLooper()).post {
            view.findViewById<TextView>(R.id.fc_title)?.text = title
            view.findViewById<TextView>(R.id.fc_artist)?.text = artist
            view.findViewById<ImageButton>(R.id.fc_play)?.setImageResource(
                if (isPlaying) R.drawable.ic_pause_white else R.drawable.ic_play_white
            )
            // 封面 URL 未变化时不再重新下载（此前每次状态刷新都会重新拉一次网络图片）
            if (!coverUrl.isNullOrEmpty() && coverUrl != currentCoverUrl) {
                loadImage(coverUrl, view.findViewById(R.id.fc_cover))
            }
        }
    }

    fun updatePlayState(isPlaying: Boolean) {
        val view = capsuleView ?: return
        Handler(Looper.getMainLooper()).post {
            view.findViewById<ImageButton>(R.id.fc_play)?.setImageResource(
                if (isPlaying) R.drawable.ic_pause_white else R.drawable.ic_play_white
            )
        }
    }

    fun hide() {
        val view = capsuleView ?: return
        try {
            windowManager?.removeView(view)
        } catch (_: Exception) {
        }
        capsuleView = null
        layoutParams = null
        currentCoverUrl = null
    }

    /** 简单网络图片加载（避免额外依赖，失败静默）。
     *  两遍请求：先读尺寸算出采样率，再按采样解码——
     *  旧实现直接全尺寸解码，大封面在低内存设备上有 OOM 风险。 */
    private fun loadImage(url: String, imageView: ImageView) {
        Thread {
            var conn: HttpURLConnection? = null
            try {
                conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 6000
                conn.readTimeout = 8000
                conn.connect()
                // 第一遍：只读边界，不加载像素
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                conn.inputStream.use { BitmapFactory.decodeStream(it, null, bounds) }
                val sample = calcSampleSize(bounds.outWidth, bounds.outHeight, 160)
                conn.disconnect()

                // 第二遍：按采样率解码
                conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 6000
                conn.readTimeout = 8000
                conn.connect()
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                val bitmap = conn.inputStream.use { BitmapFactory.decodeStream(it, null, opts) }
                if (bitmap != null) {
                    Handler(Looper.getMainLooper()).post {
                        // 胶囊已隐藏或已切换到下一首封面时不再贴旧图
                        if (isShowing() && currentCoverUrl == url) imageView.setImageBitmap(bitmap)
                    }
                }
            } catch (_: Exception) {
            } finally {
                try {
                    conn?.disconnect()
                } catch (_: Exception) {
                }
            }
        }.start()
    }

    /** 目标边长 160px 内的 2 的幂采样率（悬浮窗封面显示尺寸约 40dp，160px 足够） */
    private fun calcSampleSize(width: Int, height: Int, target: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        while (width / (sample * 2) >= target && height / (sample * 2) >= target) sample *= 2
        return sample
    }
}
