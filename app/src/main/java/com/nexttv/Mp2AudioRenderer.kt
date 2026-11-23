// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv

import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.decoder.CryptoConfig
import androidx.media3.decoder.DecoderException
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.decoder.SimpleDecoder
import androidx.media3.decoder.SimpleDecoderOutputBuffer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DecoderAudioRenderer
import androidx.media3.extractor.MpegAudioUtil
import javazoom.jl.decoder.Bitstream
import javazoom.jl.decoder.Decoder
import javazoom.jl.decoder.JavaLayerException
import javazoom.jl.decoder.SampleBuffer
import java.io.InputStream
import kotlin.math.sqrt

/** Media3 renderer for the MPEG audio layers serviced by the JLayer fallback. */
@UnstableApi
class Mp2AudioRenderer(
    handler: Handler?,
    listener: AudioRendererEventListener?,
    sink: AudioSink
) : DecoderAudioRenderer<Mp2Decoder>(handler, listener, sink) {
    override fun getName() = "Mp2AudioRenderer"

    override fun supportsFormatInternal(format: Format): Int = when {
        !isLayerOneOrTwo(format.sampleMimeType) -> C.FORMAT_UNSUPPORTED_TYPE
        format.drmInitData != null || format.cryptoType != C.CRYPTO_TYPE_NONE -> C.FORMAT_UNSUPPORTED_DRM
        sinkSupportsFormat(pcmOutputFormat(format.channelCount, format.sampleRate)) -> C.FORMAT_HANDLED
        else -> C.FORMAT_UNSUPPORTED_SUBTYPE
    }

    override fun createDecoder(format: Format, cryptoConfig: CryptoConfig?): Mp2Decoder {
        return Mp2Decoder((format.maxInputSize).coerceIn(4096, 65536), format.channelCount, format.sampleRate)
    }

    override fun getOutputFormat(decoder: Mp2Decoder): Format = decoder.pcmFormat
}

private fun isLayerOneOrTwo(mime: String?) =
    mime == MimeTypes.AUDIO_MPEG_L1 || mime == MimeTypes.AUDIO_MPEG_L2

@UnstableApi
private fun pcmOutputFormat(channels: Int, sampleRate: Int) =
    Util.getPcmFormat(C.ENCODING_PCM_16BIT, if (channels > 0) channels else 2, if (sampleRate > 0) sampleRate else 48000)

@UnstableApi
class Mp2DecoderException(message: String, cause: Throwable? = null) : DecoderException(message, cause)

@UnstableApi
class Mp2Decoder(initialBytes: Int, channels: Int, sampleRate: Int) :
    SimpleDecoder<DecoderInputBuffer, SimpleDecoderOutputBuffer, Mp2DecoderException>(
        Array(8) { DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL) },
        Array(8) { SimpleDecoderOutputBuffer {} }
    ) {
    private val initialFormat = pcmOutputFormat(channels, sampleRate)
    @Volatile var pcmFormat: Format = initialFormat
        private set
    private val header = MpegAudioUtil.Header()
    private val compressed = ByteArray(256 * 1024)
    private var size = 0
    private var firstPts = 0L
    private var session: LayerAudioSession? = null
    private var samples = ShortArray(2304)

    init { setInitialInputBufferSize(initialBytes) }

    override fun getName() = "Mp2Decoder"
    override fun createInputBuffer() = DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL)
    override fun createOutputBuffer() = SimpleDecoderOutputBuffer(::releaseOutputBuffer)
    override fun createUnexpectedDecodeException(error: Throwable) = Mp2DecoderException("MPEG 音频解码失败", error)

    override fun decode(input: DecoderInputBuffer, output: SimpleDecoderOutputBuffer, reset: Boolean): Mp2DecoderException? {
        if (reset) {
            size = 0
            session = null
            pcmFormat = initialFormat
        }
        val data = input.data
        if (data != null && data.remaining() > compressed.size - size) {
            return Mp2DecoderException("MPEG 音频输入块超出缓冲区")
        }
        if (size == 0) firstPts = input.timeUs
        if (data != null) {
            val count = data.remaining()
            data.get(compressed, size, count)
            size += count
        }

        var read = 0
        var written = 0
        var framePts = firstPts
        var outputPts = firstPts
        while (size - read >= 4) {
            var word = 0
            for (at in read until read + 4) word = (word shl 8) or (compressed[at].toInt() and 255)
            if (!header.setForHeaderData(word) || !isLayerOneOrTwo(header.mimeType)) {
                read++
                continue
            }
            if (size - read < header.frameSize) break
            val rate = header.sampleRate
            val count = header.channels
            if (written > 0 && (pcmFormat.channelCount != count || pcmFormat.sampleRate != rate)) break

            var decoder = session
            if (decoder == null || decoder.rate != rate || decoder.channels != count || decoder.mime != header.mimeType) {
                decoder = LayerAudioSession(rate, count, header.mimeType.orEmpty())
                session = decoder
            }
            val start = read
            read += header.frameSize
            val time = framePts
            framePts += header.samplesPerFrame * 1_000_000L / rate
            val pcm = try {
                decoder.decode(compressed, start, read)
            } catch (_: JavaLayerException) {
                session = null
                continue
            } catch (_: RuntimeException) {
                session = null
                continue
            } ?: continue
            if (pcm.bufferLength == 0) continue
            if (written == 0) {
                outputPts = time
                if (pcmFormat.channelCount != count || pcmFormat.sampleRate != rate) pcmFormat = pcmOutputFormat(count, rate)
            }
            val needed = written + pcm.bufferLength
            if (needed > samples.size) samples = samples.copyOf(maxOf(needed, samples.size * 2))
            decoder.copyPcm(pcm, samples, written)
            written = needed
        }
        if (read > 0) {
            compressed.copyInto(compressed, 0, read, size)
            size -= read
            firstPts = framePts
        }
        val target = output.init(outputPts, written * Short.SIZE_BYTES)
        if (written == 0) output.shouldBeSkipped = true
        else target.asShortBuffer().put(samples, 0, written)
        return null
    }
}

/** Reuse the synthesis state across frames, but expose only the current complete frame to JLayer. */
private class LayerAudioSession(val rate: Int, val channels: Int, val mime: String) {
    private val input = FrameWindow()
    private val stream = Bitstream(input)
    private val decoder = Decoder()
    private var gain = 1.0

    fun decode(bytes: ByteArray, start: Int, end: Int): SampleBuffer? {
        input.bind(bytes, start, end)
        val header = stream.readFrame() ?: return null
        return try {
            decoder.decodeFrame(header, stream) as SampleBuffer
        } finally {
            stream.closeFrame()
        }
    }

    fun copyPcm(pcm: SampleBuffer, target: ShortArray, offset: Int) {
        val input = pcm.buffer
        val length = pcm.bufferLength
        var squares = 0.0
        for (index in 0 until length) {
            val value = input[index].toDouble()
            squares += value * value
        }
        val rms = sqrt(squares / length)
        val desired = if (rms > 1.0) (2800.0 / rms).coerceIn(1.0, 4.0) else 1.0
        gain += (desired - gain) * 0.12
        for (index in 0 until length) {
            target[offset + index] = (input[index] * gain).toInt().coerceIn(-32768, 32767).toShort()
        }
    }
}

private class FrameWindow : InputStream() {
    private lateinit var bytes: ByteArray
    private var cursor = 0
    private var limit = 0

    fun bind(source: ByteArray, start: Int, end: Int) {
        bytes = source
        cursor = start
        limit = end
    }

    override fun read(): Int = if (cursor == limit) -1 else bytes[cursor++].toInt() and 255

    override fun read(output: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val count = minOf(length, limit - cursor)
        if (count == 0) return -1
        bytes.copyInto(output, offset, cursor, cursor + count)
        cursor += count
        return count
    }
}
