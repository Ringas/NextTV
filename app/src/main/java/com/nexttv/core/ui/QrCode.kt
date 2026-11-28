// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.common.BitArray
import com.google.zxing.qrcode.QRCodeWriter

object QrCode {
    fun render(text: String, size: Int): Bitmap? {
        if (text.isBlank() || size !in 1..1024) return null
        return runCatching {
            val modules = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
            val bitmap = createBitmap(modules.width, modules.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val ink = Paint().apply { color = Color.BLACK }
            val scratch = BitArray(modules.width)
            for (y in 0 until modules.height) {
                val row = modules.getRow(y, scratch)
                var left = row.getNextSet(0)
                while (left < modules.width) {
                    val right = row.getNextUnset(left)
                    canvas.drawRect(left.toFloat(), y.toFloat(), right.toFloat(), (y + 1).toFloat(), ink)
                    left = row.getNextSet(right)
                }
            }
            bitmap
        }.getOrNull()
    }
}
