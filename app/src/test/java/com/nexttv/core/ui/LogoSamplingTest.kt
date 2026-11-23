package com.nexttv.core.ui
import org.junit.Test
import org.junit.Assert.*
class LogoSamplingTest {
 @Test fun impossibleAndOversizedDimensionsAreRejected(){for((w,h)in listOf(0 to 100,100 to -1,1 to 1000000,8192 to 8192,Int.MAX_VALUE to 1))assertNull(logoSampleSize(w,h))}
 @Test fun tallImagesAreSampledByHeight(){assertEquals(16,logoSampleSize(3,4096));assertEquals(16,logoSampleSize(4096,3))}
 @Test fun roundedOutputNeverExceedsTarget(){for((w,h)in listOf(420 to 420,421 to 30,841 to 40,4096 to 4096)){val s=logoSampleSize(w,h)!!;assertTrue((maxOf(w,h)+s-1)/s<=420)}}
}
