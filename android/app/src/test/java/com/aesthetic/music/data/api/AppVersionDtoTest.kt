package com.aesthetic.music.data.api

import com.aesthetic.music.data.AppJson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 客户端版本数据契约测试：验证兼容 snake_case 与 camelCase 响应结构。
 */
class AppVersionDtoTest {

    @Test
    fun `后端 snake_case 响应能正确反序列化`() {
        val json = """
            {
                "code": 200,
                "msg": "ok",
                "data": {
                    "id": 1,
                    "version_code": 105,
                    "version_name": "2.1.0",
                    "apk_url": "/api/app/download/105-2.1.0.apk",
                    "file_size": 35678123,
                    "hash_sha256": "4a7d2b8c9e",
                    "changelog": "1. 修复多级反代子路径\n2. 优化更新下载",
                    "force_update": true,
                    "has_local_file": true,
                    "created_at": 1711800000
                }
            }
        """.trimIndent()

        val type = object : TypeToken<ApiResponse<AppVersionDto>>() {}.type
        val resp: ApiResponse<AppVersionDto> = AppJson.gson.fromJson(json, type)
        assertEquals(200, resp.code)
        val data = resp.data
        assertNotNull(data)
        assertEquals(105L, data!!.versionCode)
        assertEquals("2.1.0", data.versionName)
        assertEquals("/api/app/download/105-2.1.0.apk", data.apkUrl)
        assertEquals(35678123L, data.fileSize)
        assertEquals("4a7d2b8c9e", data.hashSha256)
        assertTrue(data.forceUpdate)
        assertTrue(data.hasLocalFile)
    }

    @Test
    fun `camelCase 结构能正确反序列化`() {
        val json = """
            {
                "code": 200,
                "msg": "ok",
                "data": {
                    "id": 2,
                    "versionCode": 200,
                    "versionName": "2.2.0",
                    "apkUrl": "https://example.com/app.apk",
                    "fileSize": 50000000,
                    "hashSha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                    "changelog": "最新优化",
                    "forceUpdate": false,
                    "hasLocalFile": false,
                    "createdAt": 1720000000
                }
            }
        """.trimIndent()

        val type = object : TypeToken<ApiResponse<AppVersionDto>>() {}.type
        val resp: ApiResponse<AppVersionDto> = AppJson.gson.fromJson(json, type)
        val data = resp.data
        assertNotNull(data)
        assertEquals(200L, data!!.versionCode)
        assertEquals("2.2.0", data.versionName)
        assertEquals("https://example.com/app.apk", data.apkUrl)
        assertEquals(50000000L, data.fileSize)
    }

    @Test
    fun `空数据响应正确解析为 null`() {
        val json = """
            {
                "code": 200,
                "msg": "ok",
                "data": null
            }
        """.trimIndent()

        val type = object : TypeToken<ApiResponse<AppVersionDto>>() {}.type
        val resp: ApiResponse<AppVersionDto> = AppJson.gson.fromJson(json, type)
        assertEquals(200, resp.code)
        assertEquals(null, resp.data)
    }
}
