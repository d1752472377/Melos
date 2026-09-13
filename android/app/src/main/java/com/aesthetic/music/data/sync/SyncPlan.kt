package com.aesthetic.music.data.sync

import com.aesthetic.music.data.model.Song
import com.aesthetic.music.data.settings.MyPlaylist
import java.security.MessageDigest

/**
 * 云同步的**纯逻辑核心**：集合定义、记录键、内容指纹、以及"该推什么 / 不该推什么"的判定。
 *
 * ## 为什么要有这一层
 * 旧实现每次同步都把**本地全量**当 upsert 推上去、并且从不发删除，后果有三个：
 *  1. 别的设备删掉的记录会被本机的旧快照重新写活（upsert 会把墓碑 `deleted=0` 覆盖掉）；
 *  2. 每条记录每次都被写一遍，服务端 `sync_seq` 被整体推高，增量游标形同虚设；
 *  3. 本地删除永远不会传播到云端。
 *
 * 现在的模型是**基线差分**：
 *  - 客户端保存"上次同步成功时的内容指纹"（[SyncSnapshot]）；
 *  - 推送时只发**指纹与基线不同**的记录（[SyncPlan.changedKeys]）；
 *  - 用户**显式删除**才会被记成待删（[SyncPlan.recordDelete]），推送时发墓碑；
 *    本地因为"收藏上限/历史上限"被裁掉的记录**不会**被当成删除——
 *    否则一台设备的上限会把云端数据一起删掉（旧实现最危险的隐患之一）。
 *
 * 这些都是纯函数（不碰 Android / 网络），因此可以在 JVM 单测里逐条钉住。
 */
object SyncCollections {
    const val FAVORITES = "favorites"
    const val HISTORY = "history"

    /**
     * 歌单：**自建歌单与"收藏的网络歌单"共用这一个集合**，用 `kind` 区分（见 [PlaylistKind]）。
     *
     * 为什么不在服务端拆成两张表：两者的字段（id/名称/封面/来源）完全一致，
     * 拆表只会让 schema、路由与客户端 payload 各多一份，而没有任何查询需求要求它们分开。
     */
    const val PLAYLISTS = "playlists"
    const val PLAYLIST_SONGS = "playlistSongs"
    const val SCRIPTS = "scripts"
    const val DOWNLOADS = "downloads"

    /** 全部按"基线差分"同步的集合（playEvents 是 append-only 事件流，不走指纹） */
    val ALL = listOf(FAVORITES, HISTORY, PLAYLISTS, PLAYLIST_SONGS, SCRIPTS, DOWNLOADS)
}

/** 歌单类型（与后端 `playlists.kind` 一一对应） */
object PlaylistKind {
    const val MINE = "mine"
    const val FAV = "fav"
}


/**
 * 记录的跨设备唯一键（与后端表主键一一对应）。
 *
 * 键用**不可见分隔符** `\u0001` 拼成字符串（而不是冒号/斜杠）：
 *  - 平台 id、歌曲 id、歌单 id 里出现冒号是完全可能的（kugou 的 hash、用户自建歌单 id），
 *    用冒号拼串会在"从键反解出各字段"（推送删除墓碑时必须做）时解析错位；
 *  - `\u0001` 不可能出现在这些字段里，[decode] 因此是精确可逆的。
 */
object SyncKey {

    private const val SEP = "\u0001"

    fun encode(vararg parts: String?): String = parts.joinToString(SEP) { it.orEmpty() }

    /** 把键拆回各字段；字段个数由调用方按集合约定解释 */
    fun decode(encoded: String): List<String> = encoded.split(SEP)

    /** 歌曲类：`平台` + `歌曲id` */
    fun song(platform: String?, id: String?): String = encode(platform, id)

    fun song(song: Song): String = song(song.platform, song.id)

    /** 歌单：`kind` + `歌单id` —— 自建歌单与"收藏的网络歌单"可能撞 id，必须分域 */
    fun playlist(kind: String, id: String): String = encode(kind, id)

    /** 歌单曲目：`kind` + `歌单id` + `平台` + `歌曲id` */
    fun playlistSong(kind: String, playlistId: String, platform: String?, songId: String?): String =
        encode(kind, playlistId, platform, songId)

    /** 音源脚本：脚本 id 本身就是 UUID，直接用 */
    fun script(id: String): String = encode(id)

    /**
     * 下载记录：`设备id` + `平台` + `歌曲id`。
     *
     * 设备维度必须进键：下载的是"某台设备上的一个文件"，
     * 若只按歌曲区分，A 设备删掉本地文件会把 B 设备的记录一起删掉。
     */
    fun download(deviceId: String, platform: String?, songId: String?): String =
        encode(deviceId, platform, songId)
}

/**
 * 内容指纹：把一条记录"对同步有意义"的字段拼成规范串再取 SHA-256 前 16 位十六进制。
 *
 * 为什么要哈希而不是直接存原串：基线要落 SharedPreferences，原串（歌单名、脚本名、专辑名）
 * 会把它撑大几十倍；哈希等长且不泄露内容。
 *
 * 为什么不做"字段级 diff"：同步的最小单位是**记录**（服务端就是按记录 LWW），
 * 记录内容变了就整条重推，简单且不会出现半条记录更新的中间态。
 *
 * 注意：指纹只包含**稳定字段**。比如收藏歌曲里绝不包含播放直链（url/试听地址会过期变化），
 * 否则每次播放都会让指纹变化、把收藏整表重复推上去。
 */
object SyncFingerprint {

    /** 字段分隔符：用不可见字符，避免内容里的空格/冒号造成歧义 */
    private const val SEP = "\u0001"

    fun hash(vararg parts: Any?): String {
        val canonical = parts.joinToString(SEP) { it?.toString().orEmpty() }
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        // 16 位十六进制（64bit）碰撞概率对"同一用户的几百上千条记录"完全够用
        return buildString(16) {
            for (i in 0 until 8) append("%02x".format(digest[i]))
        }
    }

    fun of(song: Song): String = hash(song.platform, song.id, song.title, song.artist, song.album, song.artwork)

    /**
     * 歌单指纹按**上传形态**计算（[wireCover] 而非本地 cover）。
     * 本机封面是绝对路径，在别的设备上没有意义，同步时会被丢成空串；
     * 若按本地封面算指纹，用户换一张本机封面会导致"指纹变了"→ 推上去的值却没变（仍是空），
     * 下次同步又变，形成永远同步不完的假变更。
     */
    fun of(playlist: MyPlaylist, kind: String, wireCover: String?): String =
        hash(kind, playlist.id, playlist.name, wireCover)

    fun ofPlaylistSong(
        playlistId: String,
        position: Int,
        platform: String?,
        songId: String?,
        song: Song?,
    ): String = hash(playlistId, position, platform, songId, song?.title, song?.artist, song?.album, song?.artwork)

    fun ofScript(id: String, name: String, description: String, code: String, enabled: Boolean): String =
        hash(id, name, description, code, enabled)

    fun ofDownload(
        platform: String?,
        songId: String?,
        deviceId: String,
        title: String,
        artist: String,
        sizeBytes: Long,
    ): String = hash(platform, songId, deviceId, title, artist, sizeBytes)
}

/**
 * 一条记录在"上次同步成功"时的状态。
 *
 * @param fingerprints 记录键 → 内容指纹（基线）
 * @param pendingDeletes 用户显式删除、但尚未成功推送到服务端的记录键
 */
data class SyncSnapshot(
    val fingerprints: Map<String, String> = emptyMap(),
    val pendingDeletes: Set<String> = emptySet(),
)

object SyncPlan {

    /**
     * 需要上传的记录键：本地有、且与基线不同（新增或内容变了）。
     *
     * 返回排序后的列表，保证同一份本地数据每次产生同样的推送内容（便于日志比对与测试）。
     */
    fun changedKeys(local: Map<String, String>, snapshot: SyncSnapshot): List<String> =
        local.filter { (key, fp) -> snapshot.fingerprints[key] != fp }
            .keys
            .sorted()

    /**
     * 需要上传的删除键：显式删除过，且本地确实已经没有这条记录。
     *
     * "本地确实没有"这个条件很重要：用户删掉后又马上重新添加，此时基线里还留着旧的删除标记，
     * 直接发墓碑会把刚加回来的记录删掉；这里靠本地现状把它过滤掉，并在推送后按现状更新基线。
     */
    fun effectiveDeletes(pending: Set<String>, localKeys: Set<String>): List<String> =
        pending.filterNot { it in localKeys }.sorted()

    /**
     * 拉取到云端改动后，更新基线（**不**改本地数据，本地合并由调用方做）。
     *
     * @param upserts 云端下发的记录键 → 云端内容指纹（与本地应用后的指纹一致）
     * @param deletes 云端下发的删除键（墓碑）
     */
    fun applyPulled(
        snapshot: SyncSnapshot,
        upserts: Map<String, String> = emptyMap(),
        deletes: Collection<String> = emptyList(),
    ): SyncSnapshot {
        val next = snapshot.fingerprints.toMutableMap()
        upserts.forEach { (key, fp) -> next[key] = fp }
        deletes.forEach { next.remove(it) }
        return snapshot.copy(fingerprints = next)
    }

    /**
     * 推送成功后推进基线：只把**本次真正推上去的键**写成新基线，不动其它键。
     *
     * 为什么不是"把整份本地状态当新基线"：推送期间用户可能又改了别的记录，
     * 那样会把尚未同步的改动错误地标记成"已同步"，直接丢数据。
     */
    fun afterPush(
        snapshot: SyncSnapshot,
        pushedUpserts: Map<String, String>,
        pushedDeletes: Collection<String>,
    ): SyncSnapshot = snapshot.copy(
        fingerprints = snapshot.fingerprints + pushedUpserts,
        pendingDeletes = snapshot.pendingDeletes - pushedDeletes.toSet(),
    )

    /** 用户显式删除一条记录：记下待删键（对应地把它从基线里清掉） */
    fun recordDelete(snapshot: SyncSnapshot, key: String): SyncSnapshot = snapshot.copy(
        fingerprints = snapshot.fingerprints - key,
        pendingDeletes = snapshot.pendingDeletes + key,
    )

    /**
     * 本地重新添加了一条曾经删除的记录：撤销待删标记。
     *
     * 少了这一步会出现"加了又没"——待删标记会在下次推送时把刚加回来的记录删掉。
     */
    fun recordAdd(snapshot: SyncSnapshot, key: String): SyncSnapshot =
        snapshot.copy(pendingDeletes = snapshot.pendingDeletes - key)

    /**
     * 远端下发的一条**更新**是否应该应用到本地。
     *
     * 冲突规则：**本地显式删除优先于远端更新**。
     * 若本机用户刚删了这条记录、另一台设备又改了它，按 LWW "后推的赢"会把记录在本机复活，
     * 而用户的删除意图是明确的、且已经记在待删标记里，因此这里跳过应用，
     * 由下一次推送把墓碑发上去（此后云端也进入删除态，两端收敛）。
     */
    fun shouldApplyPulledUpsert(key: String, snapshot: SyncSnapshot): Boolean =
        key !in snapshot.pendingDeletes

    /** 清空某个集合在基线里的痕迹（例如"清空播放历史"这种整体操作） */
    fun clearCollection(snapshot: SyncSnapshot, keys: Collection<String>): SyncSnapshot = snapshot.copy(
        fingerprints = snapshot.fingerprints - keys.toSet(),
        pendingDeletes = snapshot.pendingDeletes - keys.toSet(),
    )
}
