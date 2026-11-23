// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.data

import com.nexttv.core.io.ImportLimitException
import com.nexttv.core.io.ImportPolicy
import java.io.Reader

/** Read bounded lines without materializing the playlist or an unbounded line. */
internal fun readPlaylistLines(reader: Reader, checkBudget: () -> Unit, consume: (String) -> Unit) {
    val block = CharArray(8192)
    val line = StringBuilder()
    var total = 0L
    var afterCr = false
    while (true) {
        checkBudget()
        val count = reader.read(block)
        if (count < 0) break
        for (index in 0 until count) {
            val char = block[index]
            if (++total > ImportPolicy.PLAYLIST_BYTES) throw ImportLimitException("播放源内容超过大小上限")
            if ((total == 1L && char == '\uFEFF') || (afterCr && char == '\n')) {
                afterCr = false
                continue
            }
            afterCr = char == '\r'
            if (char == '\n' || afterCr) {
                checkBudget()
                consume(line.toString())
                line.setLength(0)
            } else {
                if (line.length == ImportPolicy.LINE_CHARS) throw ImportLimitException("播放源单行内容过长")
                line.append(char)
            }
        }
    }
    if (line.isNotEmpty()) consume(line.toString())
}
