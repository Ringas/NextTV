package com.nexttv
import org.junit.Test
import org.junit.Assert.*
class FocusMotionTest {
    @Test fun topReturnStaysFastAtAnyDepth() {
        assertEquals(220,fastReturnDuration(0f,500))
        assertTrue(fastReturnDuration(1000f,500)>220)
        assertEquals(420,fastReturnDuration(100000f,500))
    }
}
