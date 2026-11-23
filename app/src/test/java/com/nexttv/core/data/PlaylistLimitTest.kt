package com.nexttv.core.data
import org.junit.Test
import org.junit.Assert.*
import com.nexttv.core.io.*
class PlaylistLimitTest {
 @Test fun hugeLineIsRejected(){try{M3uParser.parse("#"+"a".repeat(ImportPolicy.LINE_CHARS),"test");fail()}catch(_:ImportLimitException){}}
 @Test fun routeQuantityIsBounded(){val text=buildString{append("#EXTM3U\n");for(i in 0..ImportPolicy.CHANNELS)append("#EXTINF:-1,C$i\nhttp://example.test/$i\n")};try{M3uParser.parse(text,"test");fail()}catch(_:ImportLimitException){}}
 @Test fun parserCooperatesWithCancellation(){var checked=0;try{M3uParser.parse("#EXTM3U\n#EXTINF:-1,Live\nhttp://example.test/no_suffix","test"){if(++checked==2)throw java.util.concurrent.CancellationException()};fail()}catch(_:java.util.concurrent.CancellationException){};assertEquals(2,checked)}
}
