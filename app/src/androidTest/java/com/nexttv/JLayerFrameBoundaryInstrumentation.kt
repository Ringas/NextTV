// SPDX-License-Identifier: GPL-3.0-only
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.nexttv

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import android.os.SystemClock
import androidx.media3.extractor.MpegAudioUtil
import java.io.File
import java.nio.ByteBuffer

class JLayerFrameBoundaryInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val result = runCatching {
            val audio = File(targetContext.filesDir, "mp2-fragment-test.mp2").readBytes()
            val header = MpegAudioUtil.Header()
            check(header.setForHeaderData(ByteBuffer.wrap(audio).getInt()))
            val frame = audio.copyOf(header.frameSize)
            val decoder = Mp2Decoder(4096, header.channels, header.sampleRate)
            try {
                fun enqueue(bytes: ByteArray, timeUs: Long) {
                    val input = checkNotNull(decoder.dequeueInputBuffer())
                    input.ensureSpaceForWrite(bytes.size)
                    checkNotNull(input.data).put(bytes)
                    input.timeUs = timeUs
                    input.flip()
                    decoder.queueInputBuffer(input)
                }

                fun read(): Pair<Long, ByteArray> {
                    val deadline = SystemClock.elapsedRealtime() + 3000
                    do {
                        val output = decoder.dequeueOutputBuffer()
                        if (output != null) {
                            try {
                                val pcm = checkNotNull(output.data)
                                return output.timeUs to ByteArray(pcm.remaining()).also { pcm.get(it) }
                            } finally {
                                output.release()
                            }
                        }
                        SystemClock.sleep(10)
                    } while (SystemClock.elapsedRealtime() < deadline)
                    error("No PCM from complete MPEG frame")
                }

                enqueue(frame, 1_000_000)
                val (wholeTime, whole) = read()
                check(wholeTime == 1_000_000L && whole.size == header.samplesPerFrame * header.channels * 2)
                check(whole.any { it != 0.toByte() })
                decoder.flush()
                enqueue(byteArrayOf(0x00, 0x01, 0x02) + frame.copyOfRange(0, 3), 2_000_000)
                enqueue(frame.copyOfRange(3, frame.size), 2_000_123)
                val (splitTime, split) = read()
                check(splitTime == 2_000_000L && split.contentEquals(whole))
                enqueue(frame.copyOf(9), 3_000_000)
                decoder.flush()
                enqueue(frame, 4_000_000)
                val (resetTime, reset) = read()
                check(resetTime == 4_000_000L && reset.contentEquals(whole))
            } finally {
                decoder.release()
            }
        }
        finish(if (result.isSuccess) Activity.RESULT_OK else Activity.RESULT_CANCELED, Bundle().apply {
            putString("stream", result.exceptionOrNull()?.stackTraceToString()
                ?: "PASS initial MP2 PCM is identical after fragmented input and flush; timestamps preserved\n")
        })
    }
}
