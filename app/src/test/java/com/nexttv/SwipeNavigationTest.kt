package com.nexttv
import com.nexttv.core.tv.*
import org.junit.Assert.*
import org.junit.Test
class SwipeNavigationTest{
 @Test fun smallDragIsTap(){assertNull(swipeDirection(20f,10f,36f))}
 @Test fun leftRevealsRight(){assertEquals(SwipeDirection.RIGHT,swipeDirection(-90f,4f,36f))}
 @Test fun rightReturnsLeft(){assertEquals(SwipeDirection.LEFT,swipeDirection(90f,4f,36f))}
 @Test fun upwardAdvances(){assertEquals(SwipeDirection.DOWN,swipeDirection(4f,-90f,36f))}
 @Test fun downwardPrevious(){assertEquals(SwipeDirection.UP,swipeDirection(4f,90f,36f))}
 @Test fun diagonalWaitsForAxis(){assertNull(swipeDirection(90f,90f,36f))}
}
