package com.nexttv
import com.nexttv.core.tv.homeHeaderEntryIndex
import org.junit.Assert.*
import org.junit.Test
class HomeHeaderEntryTest {
 @Test fun topNeverRestoresSecondRow(){for(columns in 2..5)for(old in -1..40)assertTrue(homeHeaderEntryIndex(0,0,columns,old,columns)!! in 0 until columns)}
 @Test fun topPreservesFirstRowColumn(){assertEquals(2,homeHeaderEntryIndex(0,0,4,2,4))}
 @Test fun subpixelTopResidualDoesNotSkipFirstRow(){for(old in 4..40)assertEquals(0,homeHeaderEntryIndex(0,1,4,old,4))}
 @Test fun densityScaledFocusMarginToleratesOnlyTinyResidual(){assertEquals(0,homeHeaderEntryIndex(0,3,4,7,4,3));assertEquals(4,homeHeaderEntryIndex(0,40,4,7,4,3))}
 @Test fun wheelKeepsVisibleEntry(){assertEquals(12,homeHeaderEntryIndex(8,140,4,2,12));assertNull(homeHeaderEntryIndex(8,140,4,2,null))}
}
