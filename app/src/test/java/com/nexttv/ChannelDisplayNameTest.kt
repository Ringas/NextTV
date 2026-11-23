package com.nexttv
import com.nexttv.core.ui.Ui
import org.junit.Assert.assertEquals
import org.junit.Test
class ChannelDisplayNameTest {
 @Test fun resolutionCanBeTheActualChannelBrand(){assertEquals("CCTV4K",Ui.displayName("CCTV4K"));assertEquals("CCTV8K",Ui.displayName("CCTV8K"))}
 @Test fun sourceNamesAreNotMeasuredPictureQuality(){assertEquals("电影频道 HD",Ui.displayName("电影频道 HD"))}
 @Test fun onlyAccidentalOuterWhitespaceIsRemoved(){assertEquals("央视 4K",Ui.displayName("  央视 4K  "))}
}
