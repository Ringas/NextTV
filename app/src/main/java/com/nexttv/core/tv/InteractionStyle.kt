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
 val focusMillis=120
 val title=30.sp
 val heading=20.sp
 val panelRadius=24.dp
 val cardRadius=20.dp
 val pillRadius=28.dp
 val pillInnerRadius=23.dp
 val controlRadius=18.dp
 val subheading=16.sp
 val badgeRadius=10.dp
 val sideRadius=16.dp
 val radius=16.dp
 val body=14.sp
 val caption=12.sp
}

internal fun homeColumnCount(widthDp:Int,grid:Boolean,heightDp:Int=Int.MAX_VALUE):Int=if(!grid)1 else if(heightDp<480&&widthDp in 600..899)3 else when{widthDp<720->2;widthDp<900->3;else->4}
