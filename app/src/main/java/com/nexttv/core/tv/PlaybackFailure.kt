// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.tv

import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
import com.nexttv.StreamHttpStatusException
import java.net.ConnectException
import java.net.MalformedURLException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

internal class UnsupportedPlaybackProtocol : IllegalArgumentException()
internal class HttpsRequired : IllegalArgumentException()

internal fun playbackFailureMessage(error: Throwable, playerErrorCode: Int? = null): String {
    val causes = generateSequence(error) { it.cause }.take(8).toList()
    val status = causes.firstNotNullOfOrNull {
        when (it) {
            is HttpDataSource.InvalidResponseCodeException -> it.responseCode
            is StreamHttpStatusException -> it.status
            else -> null
        }
    }
    if (status != null) {
        val reason = when (status) {
            401 -> "播放源需要身份验证"
            403 -> "播放源拒绝访问"
            404, 410 -> "播放源不存在或已失效"
            429 -> "播放源请求过于频繁"
            in 500..599 -> "播放源服务器异常"
            else -> "播放源返回异常状态"
        }
        return "$reason（HTTP $status）"
    }
    return when {
        causes.any { it is HttpsRequired } -> "已开启仅 HTTPS，请更换为加密线路或在设置中关闭该限制"
        causes.any { it is UnsupportedPlaybackProtocol } -> "当前播放引擎仅支持 HTTP/HTTPS，暂不支持该线路协议"
        causes.any { it is MalformedURLException } -> "播放地址格式无效"
        causes.any { it is UnknownHostException } -> "无法解析播放源域名"
        causes.any { it is SocketTimeoutException } -> "连接或读取播放源超时"
        causes.any { it is SSLException } -> "播放源安全连接失败"
        causes.any { it is ConnectException || it is NoRouteToHostException } -> "无法连接到播放源"
        else -> when (playerErrorCode) {
            PlaybackException.ERROR_CODE_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "连接或读取播放源超时"
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "无法连接到播放源"
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "播放源返回异常状态"
            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE -> "播放源返回的内容不是可播放的媒体"
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "播放资源不存在"
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> "没有访问播放资源的权限"
            PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED -> "设备不允许未加密的 HTTP 连接"
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> "播放源的媒体或清单格式有误"
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> "当前设备不支持该媒体或清单格式"
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED -> "音视频解码失败"
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> "当前设备不支持该音视频编码"
            PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED -> "音频输出失败"
            in 6000..6999 -> "受保护内容无法播放"
            else -> if (playerErrorCode != null) "播放时发生未知错误" else "无法打开播放源"
        }
    }
}
