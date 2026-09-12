package com.aesthetic.music.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 网络异常归一化测试。
 *
 * 这是全仓库唯一决定"什么算网络失败"的地方，两个方向判错都会造成用户可见的问题：
 * - 判宽了：后端返回的业务错误（如"用户名或密码错误"）被替换成"网络连接失败"，
 *   用户按提示去检查网络，永远解决不了问题；
 * - 判窄了：断网时把 `UnknownHostException` 直接抛到 UI，用户看到一串英文类名。
 */
class ApiErrorsTest {

    // ---------------- 应被判定为网络故障 ----------------

    @Test
    fun `连接被拒绝算网络故障`() {
        assertTrue(isNetworkError(ConnectException("Connection refused")))
    }

    @Test
    fun `读取超时算网络故障`() {
        assertTrue(isNetworkError(SocketTimeoutException("timeout")))
    }

    @Test
    fun `DNS 解析失败算网络故障`() {
        assertTrue(isNetworkError(UnknownHostException("api.example.com")))
    }

    @Test
    fun `泛化 IOException 算网络故障`() {
        assertTrue(isNetworkError(IOException("unexpected end of stream")))
    }

    @Test
    fun `message 含 Unable to resolve host 时兜底判定为网络故障`() {
        // 少数 ROM / 自定义实现会把解析失败包成普通异常，只留下 message 线索
        assertTrue(isNetworkError(Exception("Unable to resolve host \"api.example.com\": No address associated")))
    }

    // ---------------- 不应被判定为网络故障 ----------------

    @Test
    fun `普通业务异常不算网络故障`() {
        assertFalse(isNetworkError(Exception("用户名或密码错误")))
        assertFalse(isNetworkError(IllegalStateException("注册返回为空")))
    }

    @Test
    fun `空 message 不误判`() {
        assertFalse(isNetworkError(Exception()))
    }

    @Test
    fun `NullPointerException 不算网络故障`() {
        assertFalse(isNetworkError(NullPointerException()))
    }

    // ---------------- friendlyError 的转换行为 ----------------

    @Test
    fun `网络故障被替换为统一的友好文案`() {
        val result = friendlyError(UnknownHostException("api.example.com"))

        assertEquals(NETWORK_ERROR_MESSAGE, result.message)
        assertEquals(NETWORK_ERROR_MESSAGE, "网络连接失败，请检查网络后重试")
    }

    @Test
    fun `网络故障替换后不泄露主机名`() {
        val result = friendlyError(ConnectException("failed to connect to api.internal.example:8787"))
        assertFalse(result.message!!.contains("api.internal.example"))
    }

    @Test
    fun `业务异常原样返回以保留后端提示`() {
        val business = Exception("用户名或密码错误")
        assertSame(business, friendlyError(business))
    }

    @Test
    fun `非网络类 IOException 之外的运行时异常原样返回`() {
        val npe = NullPointerException("boom")
        assertSame(npe, friendlyError(npe))
    }

    // ---------------- 后端错误响应（4xx/5xx）的文案 ----------------

    private fun httpError(code: Int, body: String?): HttpException {
        val responseBody = (body ?: "").toResponseBody("application/json".toMediaType())
        return HttpException(Response.error<Any>(code, responseBody))
    }

    @Test
    fun `409 冲突时使用后端返回的中文提示`() {
        // 邮箱被他人占用时后端返回 {"code":409,"msg":"该邮箱已被其它账号绑定"}；
        // 若这里退化成 "HTTP 409 Conflict"，用户根本不知道换个邮箱就能解决
        val e = friendlyError(httpError(409, """{"code":409,"msg":"该邮箱已被其它账号绑定"}"""))
        assertEquals("该邮箱已被其它账号绑定", e.message)
    }

    @Test
    fun `400 时使用后端的字段级提示`() {
        val e = friendlyError(httpError(400, """{"code":400,"msg":"邮箱格式不正确（示例：name@example.com）"}"""))
        assertEquals("邮箱格式不正确（示例：name@example.com）", e.message)
    }

    @Test
    fun `响应体为空时按状态码给中文兜底`() {
        assertEquals("登录已过期，请重新登录", friendlyError(httpError(401, null)).message)
        assertEquals("没有权限执行该操作", friendlyError(httpError(403, "")).message)
        assertEquals("与已有数据冲突（HTTP 409）", friendlyError(httpError(409, null)).message)
        assertEquals("接口不存在，后端版本可能较旧（HTTP 404）", friendlyError(httpError(404, null)).message)
        assertEquals("服务器内部错误（HTTP 502）", friendlyError(httpError(502, null)).message)
    }

    @Test
    fun `msg 为空白或响应体不是 JSON 时不被当成提示`() {
        // 有些反代会在错误体里塞 HTML，绝不能把它当文案显示给用户
        assertEquals("服务器内部错误（HTTP 500）", friendlyError(httpError(500, "<html>Bad Gateway</html>")).message)
        assertEquals("与已有数据冲突（HTTP 409）", friendlyError(httpError(409, """{"code":409,"msg":"   "}""")).message)
    }

    @Test
    fun `HTTP 错误不算网络故障 不会显示成断网`() {
        val e = httpError(409, """{"code":409,"msg":"该邮箱已被其它账号绑定"}""")
        assertFalse(isNetworkError(e))
        assertFalse(friendlyError(e).message!!.contains("网络连接失败"))
    }
}
