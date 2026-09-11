package com.aesthetic.music.data

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
 * 其余异常原样返回，由上层按各自语义处理（例如后端返回的业务错误信息需要保留）。
 *
 * 从 `MusicRepository` 的私有方法提取为纯函数，以便在 JVM 上直接测试——
 * 它是全仓库唯一决定"什么算网络失败"的地方，判错会导致
 * 要么把业务错误吞成"网络失败"，要么把断网暴露成技术异常。
 */
internal fun friendlyError(e: Exception): Exception =
    if (isNetworkError(e)) Exception(NETWORK_ERROR_MESSAGE) else e
