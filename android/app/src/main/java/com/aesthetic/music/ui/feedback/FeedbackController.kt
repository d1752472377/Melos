package com.aesthetic.music.ui.feedback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.aesthetic.music.BuildConfig
import com.aesthetic.music.data.api.ApiClient
import com.aesthetic.music.data.api.FeedbackItemDto
import com.aesthetic.music.data.api.FeedbackSubmitBody
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 问题类型：和服务端 feedbackTypes 一一对应 */
enum class FeedbackType(val key: String, val label: String) {
    PLAYBACK("playback", "播放问题"),
    LYRICS("lyrics", "歌词问题"),
    LIBRARY("library", "音乐库与同步"),
    ACCOUNT("account", "账号问题"),
    UI("ui", "界面问题"),
    FEATURE("feature", "功能建议"),
    OTHER("other", "其他问题"),
}

data class CaptchaUi(val id: String, val image: ImageBitmap?)

class FeedbackController(
    private val scope: CoroutineScope,
) {

    sealed interface SubmitState {
        data object Idle : SubmitState
        data object Submitting : SubmitState
        data object Done : SubmitState
        data class Error(val message: String) : SubmitState
    }

    private val _captcha = MutableStateFlow<CaptchaUi?>(null)
    val captcha: StateFlow<CaptchaUi?> = _captcha.asStateFlow()

    private val _captchaLoading = MutableStateFlow(false)
    val captchaLoading: StateFlow<Boolean> = _captchaLoading.asStateFlow()

    private val _submitState = MutableStateFlow<SubmitState>(SubmitState.Idle)
    val submitState: StateFlow<SubmitState> = _submitState.asStateFlow()

    private val _mine = MutableStateFlow<List<FeedbackItemDto>>(emptyList())
    val mine: StateFlow<List<FeedbackItemDto>> = _mine.asStateFlow()

    private val _mineLoading = MutableStateFlow(false)
    val mineLoading: StateFlow<Boolean> = _mineLoading.asStateFlow()

    private val _mineError = MutableStateFlow<String?>(null)
    val mineError: StateFlow<String?> = _mineError.asStateFlow()

    fun refreshCaptcha() {
        if (_captchaLoading.value) return
        _captchaLoading.value = true
        scope.launch {
            val result = runCatching { ApiClient.api.feedbackCaptcha() }.getOrNull()
            val dto = result?.data
            if (result?.code == 200 && dto != null) {
                val bitmap = withContext(Dispatchers.IO) { decodeBase64Bitmap(dto.image) }
                _captcha.value = CaptchaUi(dto.id, bitmap)
            } else {
                _captcha.value = null
            }
            _captchaLoading.value = false
        }
    }

    suspend fun submit(
        context: Context,
        type: FeedbackType,
        content: String,
        images: List<Uri>,
        withLogs: Boolean,
        captchaCode: String,
    ): Boolean {
        val captchaId = _captcha.value?.id
        if (captchaId.isNullOrBlank()) {
            _submitState.value = SubmitState.Error("验证码已失效，请刷新后重试")
            return false
        }
        _submitState.value = SubmitState.Submitting
        val encoded = withContext(Dispatchers.IO) {
            images.take(3).mapNotNull { uri -> encodeImageBase64(context, uri) }
        }
        val body = FeedbackSubmitBody(
            type = type.key,
            content = content.trim(),
            images = encoded,
            withLogs = withLogs,
            appVersion = BuildConfig.VERSION_NAME,
            device = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
            captchaId = captchaId,
            captcha = captchaCode.trim(),
        )
        return runCatching { ApiClient.api.submitFeedback(body) }
            .fold(
                onSuccess = { resp ->
                    if (resp.code == 200) {
                        _submitState.value = SubmitState.Done
                        refreshCaptcha()
                        true
                    } else {
                        _submitState.value = SubmitState.Error(resp.msg ?: "提交失败，请稍后再试")
                        // 验证码一次性，失败后换一张
                        refreshCaptcha()
                        false
                    }
                },
                onFailure = { e ->
                    _submitState.value = SubmitState.Error(e.message ?: "网络异常，请稍后再试")
                    false
                },
            )
    }

    fun resetSubmitState() {
        _submitState.value = SubmitState.Idle
    }

    fun loadMine() {
        if (_mineLoading.value) return
        _mineLoading.value = true
        _mineError.value = null
        scope.launch {
            runCatching { ApiClient.api.myFeedback() }
                .onSuccess { resp ->
                    if (resp.code == 200) {
                        _mine.value = resp.data.orEmpty()
                    } else {
                        _mineError.value = resp.msg ?: "读取失败"
                    }
                }
                .onFailure { e -> _mineError.value = e.message ?: "网络异常" }
            _mineLoading.value = false
        }
    }

    private fun decodeBase64Bitmap(raw: String): ImageBitmap? = runCatching {
        val bytes = Base64.decode(raw, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()

    /** 压缩成 JPEG 再转 base64：按长边缩到 1080，质量 78，单张通常 100~300KB */
    private fun encodeImageBase64(context: Context, uri: Uri): String? = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

        var sample = 1
        val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
        while (maxSide / sample > 1080) sample *= 2

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap: Bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return@runCatching null

        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 78, out)
        bitmap.recycle()
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }.getOrNull()
}
