package com.nexttv.core.tv
import org.junit.Assert.*
import org.junit.Test
class VisibleFocusTest{
 @Test fun skipsClippedRowsWhenACompleteRowExists(){assertEquals(2,visibleFocusIndex(listOf(VisibleFocusItem(1,-20,40),VisibleFocusItem(2,46,106),VisibleFocusItem(3,112,172)),0,160,1))}
 @Test fun keepsVisiblePreference(){assertEquals(3,visibleFocusIndex(listOf(VisibleFocusItem(2,0,60),VisibleFocusItem(3,66,126)),0,160,3))}
 @Test fun stalePreferenceDoesNotReturnAnOffscreenNode(){assertEquals(20,visibleFocusIndex(listOf(VisibleFocusItem(0,-900,-840),VisibleFocusItem(20,10,80)),0,160,0))}
 @Test fun oversizedRowsStillHaveAnEntry(){assertEquals(5,visibleFocusIndex(listOf(VisibleFocusItem(5,-20,260)),0,160,5))}
 @Test fun emptyOrFullyOutsideHasNoEntry(){assertNull(visibleFocusIndex(emptyList(),0,160));assertNull(visibleFocusIndex(listOf(VisibleFocusItem(1,-60,0),VisibleFocusItem(2,160,220)),0,160))}
}
