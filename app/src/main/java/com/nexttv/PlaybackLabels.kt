package com.nexttv

fun resolutionLabel(height: Int): String = when {
    height >= 4320 -> "8K"
    height >= 2160 -> "4K"
    height > 0 -> "${height}P"
    else -> "—"
}

fun audioChannelLabel(channels:Int):String=when{channels==1->"单声道";channels==2->"立体声";channels>2->"${channels}声道";else->"—"}
fun videoCodecLabel(mime:String?):String?=when(mime){"video/hevc"->"HEVC";"video/avc"->"AVC";"video/av01"->"AV1";"video/mpeg2"->"MPEG-2";"video/mp4v-es"->"MPEG-4";"video/x-vnd.on2.vp9"->"VP9";else->null}
fun transferRateLabel(bytes:Long?,showBytes:Boolean=true):String=when{
 bytes==null||bytes<0->"—"
 bytes>=1073741824->String.format(java.util.Locale.ROOT,"%.1f GB/s",bytes/1073741824.0)
 bytes>=1048576->String.format(java.util.Locale.ROOT,"%.1f MB/s",bytes/1048576.0)
 bytes>=1024||!showBytes->"${bytes/1024} KB/s"
 else->"$bytes B/s"
}

/** Public audio names describe the actual stream format, never a decoder implementation. */
fun audioCodecLabel(mime:String?):String?=when(mime?.lowercase(java.util.Locale.ROOT)){
 "audio/mp4a-latm","audio/aac","audio/aac-adts"->"AAC"
 "audio/mpeg-l1"->"MP1"
 "audio/mpeg-l2"->"MP2"
 "audio/mpeg"->"MP3"
 "audio/ac3"->"AC-3"
 "audio/eac3","audio/eac3-joc"->"E-AC-3"
 "audio/vnd.dts","audio/vnd.dts.hd;profile=lbr"->"DTS"
 "audio/vnd.dts.hd"->"DTS-HD"
 "audio/vnd.dts.uhd;profile=p2"->"DTS:X"
 "audio/true-hd"->"TrueHD"
 "audio/raw"->"PCM"
 "audio/flac"->"FLAC"
 "audio/opus"->"Opus"
 "audio/vorbis"->"Vorbis"
 "audio/alac"->"ALAC"
 else->null
}
