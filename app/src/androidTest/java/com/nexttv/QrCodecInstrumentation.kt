// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.nexttv.core.ui.QrCode

class QrCodecInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val result = runCatching {
            check(QrCode.render("", 480) == null)
            check(QrCode.render("https://example.invalid", 0) == null)
            for (address in listOf("http://10.0.2.2:9978", "https://example.invalid/subscribe?a=1&b=2")) {
                val bitmap = checkNotNull(QrCode.render(address, 480))
                try {
                    val colors = IntArray(bitmap.width * bitmap.height)
                    bitmap.getPixels(colors, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    val source = RGBLuminanceSource(bitmap.width, bitmap.height, colors)
                    check(QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text == address)
                } finally {
                    bitmap.recycle()
                }
            }
        }
        finish(if (result.isSuccess) Activity.RESULT_OK else Activity.RESULT_CANCELED, Bundle().apply {
            putString("stream", result.exceptionOrNull()?.stackTraceToString()
                ?: "PASS QR round-trips LAN and query-bearing subscription addresses\n")
        })
    }
}
