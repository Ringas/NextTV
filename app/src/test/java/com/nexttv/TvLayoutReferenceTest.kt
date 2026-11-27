package com.nexttv
import com.nexttv.core.tv.homeSafeInsets
import com.nexttv.core.tv.tvViewportScale
import com.nexttv.core.tv.homeColumnCount
import org.junit.Assert.*
import org.junit.Test
class TvLayoutReferenceTest {
 @Test fun referenceSafeArea(){val i=homeSafeInsets(1920,1080);assertEquals(80f,i.horizontal,.01f);assertEquals(60f,i.vertical,.01f)}
 @Test fun logicalHalfScale(){val i=homeSafeInsets(960,540);assertEquals(40f,i.horizontal,.01f);assertEquals(30f,i.vertical,.01f)}
 @Test fun shortWideWindowFitsThreeCards(){assertEquals(3,homeColumnCount(640,true,360));assertEquals(4,homeColumnCount(1138,true,640));assertEquals(1,homeColumnCount(640,false,360))}
 @Test fun viewportScaleTracksPhysicalScreenHeight(){assertEquals(1f,tvViewportScale(360),.001f);assertEquals(1f,tvViewportScale(540),.001f);assertEquals(4f/3f,tvViewportScale(720),.001f);assertEquals(2f,tvViewportScale(1080),.001f);assertEquals(4f,tvViewportScale(2160),.001f)}
}
