// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.ui
internal fun logoDiskKey(url:String):String {
    val bytes=java.security.MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
    val alphabet="0123456789abcdef";val result=CharArray(bytes.size*2)
    bytes.forEachIndexed{i,b->val n=b.toInt() and 255;result[i*2]=alphabet[n ushr 4];result[i*2+1]=alphabet[n and 15]}
    return String(result)
}
