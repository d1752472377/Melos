package com.aesthetic.music.data.model

/**
 * 一条歌曲评论。
 *
 * 各平台字段差异很大，这里只保留四个平台都有的公共部分：
 * 作者、头像、正文、时间、点赞数、IP 属地，以及一层楼中楼。
 * 平台自己的内部结构（比如酷狗的表情、QQ 的音频评论）不在这里体现。
 */
data class MusicComment(
    val id: String = "",
    val text: String = "",
    val userName: String = "",
    val avatar: String? = null,
    /** 已格式化好的时间文本，取不到就为 null */
    val timeText: String? = null,
    val location: String? = null,
    val likedCount: Int? = null,
    val replies: List<MusicComment> = emptyList(),
)

/**
 * 一页评论结果。
 *
 * [total] 是平台给出的评论总数；个别平台分页拿不到准确总数时退化为"已知条数"，
 * 这里不做假数据填充，界面需要据此决定要不要显示页码。
 */
data class CommentPage(
    val comments: List<MusicComment> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val limit: Int = 20,
)
