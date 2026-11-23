// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.tv

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

/** Only display stream metadata; no latency probes or extra live downloads on menu open. */
@Composable internal fun RouteChoiceDialog(engine:PlaybackSession,onDismiss:()->Unit,onChoose:(String)->Unit){
 val routes=engine.current.playbackRoutes
 val sources=engine.app.channels.sources.associateBy{it.id}
 val crossSource=routes.map{it.sourceId}.distinct().size>1
 var inspected by remember{mutableStateOf<Int?>(null)}
 val index=inspected
 StableDialogHost(onDismiss,transitionKey=index){
 if(index!=null&&index in routes.indices){
  val context=LocalContext.current
  val route=routes[index]
  TvDialog("线路 ${index+1}",onDismiss){
   val copyFocus=remember{FocusRequester()}
   val inputView=androidx.compose.ui.platform.LocalView.current;val inWindowModal=LocalInWindowModal.current
   LaunchedEffect(Unit){awaitModalFocusWindow(inputView,inWindowModal);copyFocus.requestFocus()}
   AppText("完整地址",color=NextTvStyle.Muted,fontSize=InteractionStyle.caption)
   AppText(route.url,Modifier.weight(1f,false).appVerticalScroll(rememberScrollState()),color=NextTvStyle.Text,fontSize=InteractionStyle.body)
   TvButton("复制地址",Modifier.focusRequester(copyFocus),onClick={
    (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("线路地址",route.url));onDismiss()
   })
  }
 }else ChoiceDialog("线路选择",routes.mapIndexed{i,r->
  val replay=com.nexttv.core.data.supportsReplay(r)
  val label=listOfNotNull("线路 ${i+1}",sources[r.sourceId]?.name?.takeIf{crossSource&&it.isNotBlank()}, "回放".takeIf{replay}).joinToString(" · ")
  ChoiceOption(i.toString(),label,r.url,engine.replay==null||com.nexttv.core.data.replayAddress(r,engine.replay!!,System.currentTimeMillis())!=null)
 },engine.routeIndex.toString(),onDismiss,onInspect={inspected=it.toIntOrNull()},singleLineDetails=true,width=520.dp,onChoose=onChoose)
 }
}
