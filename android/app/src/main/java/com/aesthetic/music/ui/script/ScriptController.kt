package com.aesthetic.music.ui.script

import com.aesthetic.music.data.settings.ScriptCatalogEntry
import com.aesthetic.music.data.settings.ScriptCatalogStore
import com.aesthetic.music.data.userapi.ScriptMetadata
import com.aesthetic.music.data.userapi.UserApiEngine
import com.aesthetic.music.data.userapi.UserApiStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 音源脚本管理控制器（导入 / 重载 / 卸载 / 本地切换）。
 *
 * 从 `MainViewModel` 抽出：脚本生命周期是自成一体的一个域——状态来自
 * [UserApiEngine] 与 [ScriptCatalogStore]，操作只有"把脚本送进沙箱执行"这一件事，
 * 与播放/歌单/同步等域没有交集。此前混在 1500 余行的 ViewModel 里。
 *
 * 与 UI 的唯一耦合是"给用户一句提示"，通过 [onToast] 注入，
 * 而不是让控制器反向持有 ViewModel 或直接操作 Snackbar 状态。
 *
 * 注意：脚本内容由用户自行导入（本地文件或 URL），在 QuickJS 沙箱中执行。
 * 沙箱不提供文件系统与 DOM，但脚本可发起网络请求——信任边界见 [UserApiEngine]。
 */
class ScriptController(
    private val scope: CoroutineScope,
    /** 向用户展示一次性提示（对应 ViewModel 的全局 Snackbar 通道） */
    private val onToast: (String) -> Unit,
) {

    /** 当前已激活脚本的元信息（引擎侧状态，直接转发） */
    val activeScriptInfo: StateFlow<ScriptMetadata?> = UserApiEngine.activeMetadata

    /** 沙箱引擎是否就绪 */
    val isEngineReady: StateFlow<Boolean> = UserApiEngine.isEngineReady

    /** 引擎级错误（原生库初始化失败等） */
    val engineError: StateFlow<String?> = UserApiEngine.engineError

    /**
     * 已同步到本地的脚本目录摘要。
     *
     * ⚠ 当前恒为空列表。`ScriptCatalogStore.saveAll()` 在全仓库没有任何调用点，
     * `ScriptCatalogEntry` 也没有任何构造点，且同步接口 [CloudSyncPullData] 里
     * 根本没有脚本字段——也就是说"云端下发脚本目录"这一环尚未实现。
     * 由于设置页对该列表加了 `isNotEmpty()` 判断，界面上不会出现空目录或报错，
     * 只是「音源脚本目录」这一节永远不显示、[switchTo] 永远走到"未找到脚本"分支。
     *
     * 这里保留该状态与 [switchTo]，是为了让实现同步下发时客户端已就绪；
     * 在此之前不应对外宣称支持"云端切换音源"。
     */
    private val _catalog = MutableStateFlow<List<ScriptCatalogEntry>>(ScriptCatalogStore.list())
    val catalog: StateFlow<List<ScriptCatalogEntry>> = _catalog.asStateFlow()

    // ==================== 导入 ====================

    /** 从脚本文本导入并立即激活 */
    fun importFromText(content: String, name: String? = null) {
        if (content.isBlank()) {
            onToast("脚本代码不能为空")
            return
        }
        scope.launch {
            val meta = UserApiStore.saveScript(content, customName = name)
            val res = UserApiEngine.loadScript(content, meta)
            if (res.isSuccess) {
                onToast("已成功导入并激活音源脚本: [${meta.name}]")
            } else {
                val err = res.exceptionOrNull()?.message ?: "执行异常"
                onToast("脚本已保存，但沙箱初始化异常: $err")
            }
        }
    }

    /** 从 URL 抓取并导入 */
    fun importFromUrl(url: String, onFinished: (Boolean, String) -> Unit) {
        val cleanUrl = url.trim()
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            onFinished(false, "URL 格式不正确")
            return
        }
        scope.launch(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val req = Request.Builder()
                    .url(cleanUrl)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        withContext(Dispatchers.Main) {
                            onFinished(false, "HTTP ${resp.code}: 下载失败")
                        }
                        return@launch
                    }
                    val text = resp.body?.string().orEmpty()
                    if (text.isBlank()) {
                        withContext(Dispatchers.Main) {
                            onFinished(false, "下载的脚本内容为空")
                        }
                        return@launch
                    }
                    val meta = UserApiStore.saveScript(text)
                    val res = UserApiEngine.loadScript(text, meta)
                    withContext(Dispatchers.Main) {
                        if (res.isSuccess) {
                            onToast("已成功导入并激活音源脚本: [${meta.name}]")
                            onFinished(true, "导入成功")
                        } else {
                            val err = res.exceptionOrNull()?.message ?: "执行异常"
                            onToast("脚本已下载，但沙箱执行异常: $err")
                            onFinished(false, "脚本执行异常: $err")
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onFinished(false, "下载失败: ${e.message}")
                }
            }
        }
    }

    // ==================== 生命周期 ====================

    /** 重新加载已保存在本地的脚本（沙箱初始化失败后的重试入口） */
    fun retryLoad() {
        val script = UserApiStore.getScriptContent()
        val meta = UserApiStore.getMetadata()
        if (script.isNullOrBlank() || meta == null) {
            onToast("未找到已保存的脚本")
            return
        }
        scope.launch {
            onToast("正在重新在沙箱中加载脚本…")
            val res = UserApiEngine.loadScript(script, meta)
            if (res.isSuccess) {
                onToast("音源脚本已成功激活: [${meta.name}]")
            } else {
                val err = res.exceptionOrNull()?.message ?: "执行异常"
                onToast("加载依然异常: $err")
            }
        }
    }

    /** 卸载当前脚本，回到平台直连兜底 */
    fun unload() {
        UserApiEngine.unloadScript()
        onToast("已卸载自定义音源脚本")
    }

    /** 从已同步的脚本目录本地切换音源（不回服务端修改激活项） */
    fun switchTo(id: String) {
        val entry = _catalog.value.firstOrNull { it.id == id }
        if (entry == null) {
            onToast("未找到该音源脚本，请先执行一次云同步")
            return
        }
        val content = ScriptCatalogStore.getContent(id)
        if (content.isNullOrBlank()) {
            onToast("脚本源码缺失，请重新同步")
            return
        }
        scope.launch {
            val meta = UserApiStore.saveScript(
                content = content,
                customName = entry.name,
                customVersion = entry.version,
                customId = entry.id,
            )
            val res = UserApiEngine.loadScript(content, meta)
            if (res.isSuccess) {
                onToast("已本地切换到音源: [${entry.name}]")
            } else {
                onToast("切换失败: ${res.exceptionOrNull()?.message ?: "沙箱执行异常"}")
            }
        }
    }
}
