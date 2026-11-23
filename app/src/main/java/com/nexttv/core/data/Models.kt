// SPDX-License-Identifier: GPL-3.0-only
// Next TV — Compose for TV implementation.
package com.nexttv.core.data

data class ChannelRoute(val url: String, val sourceId: String, val originalId: String, val catchup:String="", val catchupSource:String="", val catchupDays:Int?=null,val headers:Map<String,String> = emptyMap(),val tvgId:String="",val channelName:String="")

/** 一个直播频道；合并身份保留首条 ID，线路保留各自源配置。 */
data class Channel(
    val id: String,
    val name: String,
    val tvgId: String,
    val logo: String,
    val group: String,
    val url: String,
    val sourceId: String,
    val number: Int = 0,
    val routes: List<ChannelRoute> = emptyList(),
    val catchup:String="", val catchupSource:String="", val catchupDays:Int?=null, val headers:Map<String,String> = emptyMap()
) {
    val playbackRoutes: List<ChannelRoute> get() = routes.ifEmpty { listOf(ChannelRoute(url, sourceId, id,catchup,catchupSource,catchupDays,headers,tvgId,name)) }

    val groupName: String
        get() = group.ifBlank { DEFAULT_GROUP }

    companion object {
        const val DEFAULT_GROUP = "未分组"
    }
}

/** 播放源。kind = url 表示远程订阅地址，file 表示本地导入的文件 */
data class Source(
    val id: String,
    val name: String,
    val url: String,
    val kind: String = KIND_URL,
    val epgUrl: String = "",
    val userAgent: String = "",
    val referer: String = "",
    /** 自定义请求头，每行 "Key: Value" */
    val headers: String = "",
    val enabled: Boolean = true,
    val autoUpdate: Boolean = true,
    val channelCount: Int = 0,
    /** 最近一次解析被跳过的非 HTTP(S) 频道数 */
    val unsupportedCount: Int = 0,
    val lastUpdate: Long = 0L,
    val lastError: String = ""
) {
    val isFile: Boolean get() = kind == KIND_FILE

    companion object {
        const val KIND_URL = "url"
        const val KIND_FILE = "file"
    }
}

/** 节目单中的一条节目 */
data class Program(
    val channelKey: String,
    val title: String,
    val desc: String,
    val start: Long,
    val stop: Long,
    val guideSourceIds: List<String> = emptyList(),
)
