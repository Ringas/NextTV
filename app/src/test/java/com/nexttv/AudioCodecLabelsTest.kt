package com.nexttv
import org.junit.Assert.*
import org.junit.Test
class AudioCodecLabelsTest {
 @Test fun mpegLayersStayDistinct(){assertEquals("MP1",audioCodecLabel("audio/mpeg-L1"));assertEquals("MP2",audioCodecLabel("audio/mpeg-L2"));assertEquals("MP3",audioCodecLabel("audio/mpeg"))}
 @Test fun aacAliasesUseOnePublicName(){assertEquals("AAC",audioCodecLabel("audio/mp4a-latm"));assertEquals("AAC",audioCodecLabel("AUDIO/AAC"))}
 @Test fun audioNamesDoNotGuessUnknownFormats(){assertNull(audioCodecLabel(null));assertNull(audioCodecLabel("audio/x-unknown"));assertEquals("AC-3",audioCodecLabel("audio/ac3"));assertEquals("PCM",audioCodecLabel("audio/raw"))}
}
