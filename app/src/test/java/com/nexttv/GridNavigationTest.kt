package com.nexttv

import com.nexttv.core.tv.*
import org.junit.Assert.*
import org.junit.Test

class GridNavigationTest {
 @Test fun repeatedDownRetainsThirdColumnAcrossUncomposedRows(){
  var index=2
  repeat(20){index=gridFocusTarget(index,153,4,InputAction.DOWN)!!;assertEquals(2,index%4)}
  assertEquals(82,index)
 }
 @Test fun incompleteLastRowDoesNotJumpToFirstColumn(){assertNull(gridFocusTarget(10,13,4,InputAction.DOWN))}
 @Test fun horizontalDoesNotWrapRows(){assertNull(gridFocusTarget(3,20,4,InputAction.RIGHT));assertNull(gridFocusTarget(4,20,4,InputAction.LEFT))}
 @Test fun listAndFirstRowBoundaries(){assertEquals(2,gridFocusTarget(1,8,1,InputAction.DOWN));assertNull(gridFocusTarget(2,8,4,InputAction.UP))}
}
