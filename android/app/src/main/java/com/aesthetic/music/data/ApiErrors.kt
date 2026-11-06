package com.aesthetic.music.data

import com.google.gson.JsonParser
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

internal const val NETWORK_ERROR_MESSAGE = "网络连接失败，请检查网络后重试"

internal fun isNetworkError(e: Throwable): Boolean =
    e is IOException ||
        e is ConnectException ||
        e is SocketTimeoutException ||
        e is UnknownHostException ||
        e.message?.contains("Unable to resolve host") == true

internal fun friendlyError(e: Exception): Exception = when {
    isNetworkError(e) -> Exception(NETWORK_ERROR_MESSAGE)
    e is HttpException -> Exception(backendMessage(e) ?: httpStatusMessage(e.code()))
    else -> e
}

internal fun backendMessage(e: HttpException): String? {
    val raw = try {
        e.response()?.errorBody()?.string()
    } catch (_: Exception) {
        null
    }
    if (raw.isNullOrBlank()) return null
    return try {
        JsonParser.parseString(raw).asJsonObject.get("msg")?.asString?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }
}

internal fun httpStatusMessage(code: Int): String = when (code) {
    400 -> "请求参数有误（HTTP 400）"
    401 -> "登录已过期，请重新登录"
    403 -> "没有权限执行该操作"
    404 -> "接口不存在，后端版本可能较旧（HTTP 404）"
    409 -> "与已有数据冲突（HTTP 409）"
    413 -> "提交的内容过大"
    429 -> "操作太频繁，请稍后再试"
    in 500..599 -> "服务器内部错误（HTTP $code）"
    else -> "请求失败（HTTP $code）"
}
