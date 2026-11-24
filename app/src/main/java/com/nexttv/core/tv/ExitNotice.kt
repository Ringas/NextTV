package com.nexttv.core.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.unit.*
import androidx.tv.material3.Text

/** Shared anchor and typography for application and live-player exit confirmation. */
@Composable internal fun BoxScope.ExitNotice(message:String){
 Box(Modifier.align(Alignment.BottomCenter).padding(bottom=28.dp).fillMaxWidth(),contentAlignment=Alignment.Center){
  AppText(message,textAlign=androidx.compose.ui.text.style.TextAlign.Center,color=NextTvStyle.Text,fontSize=InteractionStyle.body,modifier=Modifier.readingSurface(RoundedCornerShape(InteractionStyle.radius)).padding(horizontal=22.dp,vertical=12.dp))
 }
}
