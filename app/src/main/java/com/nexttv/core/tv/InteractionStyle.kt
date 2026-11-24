package com.nexttv.core.tv
import androidx.compose.runtime.*
import androidx.compose.ui.unit.*
object InteractionStyle {
 internal var lastInputAt by mutableLongStateOf(0L);private set
 var pointer by mutableStateOf(false)
 var verticalNavigation by mutableStateOf(false)
 // Read only during event dispatch; do not trigger recomposition for every key repeat.
 internal var keyboardAfterPointer=false;private set
 internal fun keyboard(code:Int,fromPointer:Boolean=false){if(com.nexttv.core.App.get().prefs.channelPreviews)lastInputAt=android.os.SystemClock.uptimeMillis();keyboardAfterPointer=pointer&&!fromPointer;pointer=fromPointer;verticalNavigation=!fromPointer&&TvInput.vertical(code)}
 internal fun pointerInput(){if(com.nexttv.core.App.get().prefs.channelPreviews)lastInputAt=android.os.SystemClock.uptimeMillis();pointer=true;verticalNavigation=false;keyboardAfterPointer=false}
 const val infoMillis=5000L
 const val actionsMillis=10000L
 val focusMillis=140
 val title=34.sp
 val heading=22.sp
 val subheading=16.sp
 val body=16.sp
 val caption=13.sp
 val captionSmall=12.sp
 val micro=10.sp
 val displayLarge=46.sp
 val display=24.sp
 val displaySmall=22.sp
 val panelRadius=16.dp
 val cardRadius=12.dp
 val controlRadius=12.dp
 val sideRadius=12.dp
 val radius=12.dp
 val badgeRadius=6.dp
 val focusLift=1.04f
 val focusRing=2.dp
 val navHeight=46.dp
 val navIndicatorWidth=28.dp
 val navIndicatorHeight=2.dp
}

internal fun homeColumnCount(widthDp:Int,grid:Boolean,heightDp:Int=Int.MAX_VALUE):Int=if(!grid)1 else if(heightDp<480&&widthDp in 600..899)3 else when{widthDp<720->2;widthDp<900->3;else->4}
