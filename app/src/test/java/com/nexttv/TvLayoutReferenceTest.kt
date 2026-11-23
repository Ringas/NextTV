package com.nexttv
import com.nexttv.core.tv.homeSafeInsets
import com.nexttv.core.tv.tvTypographyScale
import com.nexttv.core.tv.homeColumnCount
import org.junit.Assert.*
import org.junit.Test
class TvLayoutReferenceTest {
 @Test fun referenceSafeArea(){val i=homeSafeInsets(1920,1080);assertEquals(80f,i.horizontal,.01f);assertEquals(60f,i.vertical,.01f)}
 @Test fun logicalHalfScale(){val i=homeSafeInsets(960,540);assertEquals(40f,i.horizontal,.01f);assertEquals(30f,i.vertical,.01f)}
 @Test fun shortWideWindowFitsThreeCards(){assertEquals(3,homeColumnCount(640,true,360));assertEquals(4,homeColumnCount(1138,true,640));assertEquals(1,homeColumnCount(640,false,360))}
 @Test fun widerCanvasMeetsReferenceWithoutShrinkingCompactText(){assertEquals(1f,tvTypographyScale(640),.001f);assertEquals(1f,tvTypographyScale(960),.001f);assertTrue(16f*tvTypographyScale(1280)>=29f*1280f/1920f-.01f);assertTrue(tvTypographyScale(4000)<=1.8f)}
}
