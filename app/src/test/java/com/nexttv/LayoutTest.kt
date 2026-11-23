package com.nexttv
import org.junit.Test
import com.nexttv.core.tv.homeColumnCount
import org.junit.Assert.*
class LayoutTest {
 @Test fun columnsRespectTabletAndTvBoundaries(){assertEquals(2,homeColumnCount(719,true));assertEquals(3,homeColumnCount(720,true));assertEquals(3,homeColumnCount(899,true));assertEquals(4,homeColumnCount(900,true));assertEquals(1,homeColumnCount(1920,false))}
}
