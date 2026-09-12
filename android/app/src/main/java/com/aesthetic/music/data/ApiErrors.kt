package com.aesthetic.music.data

import com.google.gson.JsonParser
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 网络异常的用户可见文案。
 * 用户看到的应当是"该做什么"，而不是 `java.net.UnknownHostException` 这类实现细节。
 */
internal const val NETWORK_ERROR_MESSAGE = "网络连接失败，请检查网络后重试"

/**
 * 判断异常是否属于网络层故障。
 *
 * 注：`ConnectException`、`SocketTimeoutException`、`UnknownHostException`
 * 都是 `IOException` 的子类，`e is IOException` 已覆盖它们；这里仍显式列出，
 * 是为了让"哪些故障算网络问题"一眼可见，避免后续有人把 `IOException` 判断删掉。
 *
 * 末行的 message 兜底用于少数 ROM / 自定义 OkHttp 实现把解析失败包成普通异常的情况。
 */
internal fun isNetworkError(e: Throwable): Boolean =
    e is IOException ||
        e is ConnectException ||
        e is SocketTimeoutException ||
        e is UnknownHostException ||
        e.message?.contains("Unable to resolve host") == true

/**
 * 把底层异常转换为适合展示给用户的异常。
 *
 * 网络类故障统一替换为友好文案（丢弃原始异常，避免把主机名/端口泄露到 UI）；
 * 4xx/5xx 由 [backendMessage] 提取后端 `msg`，拿不到再按状态码给一句人话；
 * 其余异常原样返回，由上层按各自语义处理（例如后端返回的业务错误信息需要保留）。
 *
 * 从 `MusicRepository` 的私有方法提取为纯函数，以便在 JVM 上直接测试——
 * 它是全仓库唯一决定"什么算网络失败"的地方，判错会导致
 * 要么把业务错误吞成"网络失败"，要么把断网暴露成技术异常。
 */
internal fun friendlyError(e: Exception): Exception = when {
    isNetworkError(e) -> Exception(NETWORK_ERROR_MESSAGE)
    // Retrofit 对非 2xx 抛 HttpException，**响应体里的中文 msg 会被丢掉**，
    // 直接用 e.message 用户看到的是 "HTTP 409 Conflict" 这种没法行动的提示。
    // 邮箱绑定（409 该邮箱已被其它账号绑定）、注册（400/409）都靠这里才会说人话。
    e is HttpException -> Exception(backendMessage(e) ?: httpStatusMessage(e.code()))
    else -> e
}

/**
 * 取后端错误响应体里的 `msg`（本后端统一返回 `{code,msg,data}`）。
 * 解析失败/字段缺失一律返回 null，由状态码兜底——错误处理本身不能再抛异常。
 */
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

/** 拿不到后端 msg 时的状态码文案：说清"发生了什么 + 该做什么" */
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
