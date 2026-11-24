// SPDX-License-Identifier: GPL-3.0-only
// Next TV — settings, source manager and EPG (Compose for TV).
@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class,androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.nexttv.core.tv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.tv.material3.Text
import com.nexttv.BuildConfig
import com.nexttv.parseNamedSubscriptionAddress
import com.nexttv.core.App
import com.nexttv.core.data.*
import com.nexttv.core.ui.*
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID

@Composable fun SourcesScreen(onBack:()->Unit,onChanged:()->Unit){
 val app=App.get();val context=LocalContext.current;val scope=rememberCoroutineScope()
 val sourceView=LocalView.current
 val addFocus=remember{androidx.compose.ui.focus.FocusRequester()};val refreshFocus=remember{FocusRequester()}
 var revision by remember{mutableIntStateOf(0)};var dialog by remember{mutableStateOf("")};var chosen by remember{mutableStateOf<Source?>(null)};var status by remember{mutableStateOf("")};var editorParent by remember{mutableStateOf("")}
 fun closeAdd(){dialog="";scope.launch{while(!sourceView.hasWindowFocus())withFrameNanos{};withFrameNanos{};addFocus.requestFocus()}}
 fun sourceBack(){when(dialog){"添加"->closeAdd();"编辑"->dialog=editorParent;"删除"->dialog="操作";"操作","扫码"->dialog="";else->onBack()}}
 val activity=context as TvActivity;val owner=remember{Any()}
 val latestSourceBack by rememberUpdatedState<()->Unit>({sourceBack()})
 DisposableEffect(activity){activity.bindPageBack(owner){latestSourceBack()};onDispose{activity.unbindPageBack(owner)}}
 BackHandler{latestSourceBack()}
 var refreshJob by remember{mutableStateOf<Job?>(null)}
 var operationEntry by remember{mutableStateOf("编辑")}
 val operationRequests=remember{listOf("编辑","自动更新","状态","删除").associateWith{FocusRequester()}}
 val sourceRequests=remember{mutableStateMapOf<String,FocusRequester>()}
 var sourceFocused by remember{mutableStateOf("")};var sourceListFocused by remember{mutableStateOf(false)}
 val sourceList=rememberLazyListState()
 LaunchedEffect(dialog){if(dialog.isBlank()){while(!sourceView.hasWindowFocus())withFrameNanos{};withFrameNanos{};runCatching{(sourceRequests[sourceFocused]?:addFocus).requestFocus()}}}
 val sourceRows=remember(revision,app.channels.revision,app.sourceStatus){app.channels.sources.toList()}
 fun Modifier.sourceToolbarNavigation():Modifier=focusProperties{down=FocusRequester.Cancel}.onPreviewKeyEvent{e->
  if(e.nativeKeyEvent.action==android.view.KeyEvent.ACTION_DOWN&&TvInput.action(e.nativeKeyEvent.keyCode)==InputAction.DOWN){
   val at=sourceList.visibleFocusIndex(sourceRows.indexOfFirst{it.id==sourceFocused})
   at?.let{sourceRows.getOrNull(it)?.id?.let{sourceRequests[it]}}?.let{runCatching{it.requestFocus()}}
   true
  }else false
 }
 fun refresh(){app.refreshSources()}
 fun save(source:Source){scope.launch{app.channels.upsertSourceAsync(source);revision++;onChanged();refresh()}}

 Column(Modifier.fillMaxSize().onPreviewKeyEvent{e->
  if(e.nativeKeyEvent.action!=android.view.KeyEvent.ACTION_DOWN||!TvInput.vertical(e.nativeKeyEvent.keyCode)||!InteractionStyle.keyboardAfterPointer||!sourceListFocused||dialog.isNotBlank())false else{
   val old=sourceRows.indexOfFirst{it.id==sourceFocused};val at=sourceList.visibleFocusIndex(old);val request=at?.let{sourceRows.getOrNull(it)?.id?.let{sourceRequests[it]}}
   if(at==old||request==null)false else runCatching{request.requestFocus();true}.getOrDefault(false)
  }
 }.padding(horizontal=40.dp,vertical=16.dp)){
  Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){AppText("管理订阅",Modifier.weight(1f),fontSize=InteractionStyle.title,fontWeight=FontWeight.Bold,color=NextTvStyle.Text);TvButton("添加订阅",Modifier.focusRequester(addFocus).sourceToolbarNavigation().focusEdges(up=true,left=true).focusProperties{up=FocusRequester.Cancel;left=FocusRequester.Cancel;right=refreshFocus;down=FocusRequester.Cancel},onFocus={sourceListFocused=false},onClick={dialog="添加"});TvButton("重新拉取",Modifier.focusRequester(refreshFocus).sourceToolbarNavigation().focusEdges(up=true,right=true).focusProperties{up=FocusRequester.Cancel;right=FocusRequester.Cancel;left=addFocus;down=FocusRequester.Cancel},onFocus={sourceListFocused=false},onClick={refresh()})}
  AppText(app.sourceStatus,color=NextTvStyle.Muted,fontSize=12.sp,modifier=Modifier.padding(vertical=12.dp))
  LazyColumn(flingBehavior=appFlingBehavior(),state=sourceList,modifier=Modifier.continuousWheel(sourceList),contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){items(sourceRows,key={it.id}){s->
   val request=remember(s.id){FocusRequester()};DisposableEffect(s.id,request){sourceRequests[s.id]=request;onDispose{if(sourceRequests[s.id]===request)sourceRequests.remove(s.id)}}
   TvSurface(Modifier.fillMaxWidth().focusRequester(request).focusEdges(down=s.id==sourceRows.lastOrNull()?.id,left=true,right=true).focusProperties{left=FocusRequester.Cancel;right=FocusRequester.Cancel;if(s.id==sourceRows.firstOrNull()?.id)up=addFocus;if(s.id==sourceRows.lastOrNull()?.id)down=FocusRequester.Cancel},kind="source",onFocus={sourceFocused=s.id;sourceListFocused=true},onClick={sourceFocused=s.id;sourceListFocused=true;chosen=s;operationEntry="编辑";dialog="操作"}){Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){AppText(s.name,fontSize=InteractionStyle.subheading,fontWeight=FontWeight.Bold);AppText(s.url,fontSize=InteractionStyle.caption,color=NextTvStyle.Muted,maxLines=1);AppText("${s.channelCount} 个频道"+(if(s.unsupportedCount>0)" · ${s.unsupportedCount} 个因协议不支持已跳过" else "")+if(s.lastError.isBlank())""else" · ${s.lastError}",fontSize=InteractionStyle.caption,color=NextTvStyle.Muted)};AppText((if(s.enabled)"已启用" else "已停用")+(if(s.autoUpdate)" ›" else " · 自动更新关闭 ›"),fontSize=12.sp,color=NextTvStyle.AccentText)}}}}
 }
 if(dialog.isNotBlank())StableDialogHost({sourceBack()},transitionKey=dialog){when(dialog){
  "编辑"->{val source=chosen!!;AddressInputDialog(if(source.name.isBlank())source.url else "${source.name},${source.url}",epg=false,editingSource=true,onDismiss={dialog=editorParent},onConfirm={input->save(subscriptionFromAddress(input,app.channels.sources,editing=source));dialog="";editorParent=""})}
  "操作"->TvDialog(chosen!!.name,{dialog=""}){
   val operationView=LocalView.current;val inWindowModal=LocalInWindowModal.current
   LaunchedEffect(Unit){awaitModalFocusWindow(operationView,inWindowModal);operationRequests[operationEntry]?.requestFocus()}
   TvButton("编辑",Modifier.fillMaxWidth().focusRequester(operationRequests.getValue("编辑")),onFocus={operationEntry="编辑"},onClick={editorParent="操作";dialog="编辑"})
   TvButton(if(chosen!!.autoUpdate)"关闭自动更新"else"开启自动更新",Modifier.fillMaxWidth().focusRequester(operationRequests.getValue("自动更新")),onFocus={operationEntry="自动更新"},onClick={save(chosen!!.copy(autoUpdate=!chosen!!.autoUpdate));dialog=""})
   TvButton(if(chosen!!.enabled)"停用"else"启用",Modifier.fillMaxWidth().focusRequester(operationRequests.getValue("状态")),onFocus={operationEntry="状态"},onClick={save(chosen!!.copy(enabled=!chosen!!.enabled));dialog=""})
   TvButton("删除",Modifier.fillMaxWidth().focusRequester(operationRequests.getValue("删除")),onFocus={operationEntry="删除"},onClick={dialog="删除"})
  }
  "删除"->TvDialog("删除订阅？",{dialog="操作"},width=420.dp){
   val cancel=remember{androidx.compose.ui.focus.FocusRequester()}
   LaunchedEffect(Unit){withFrameNanos{};cancel.requestFocus()}
   AppText("删除“${chosen!!.name}”及其频道？此操作不会删除订阅服务器上的内容。",Modifier.weight(1f,false).appVerticalScroll(rememberScrollState()),color=NextTvStyle.Text,fontSize=InteractionStyle.body)
   Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){TvButton("保留订阅",Modifier.focusRequester(cancel),onClick={dialog="操作"});TvButton("删除订阅",kind="danger",onClick={val id=chosen!!.id;scope.launch{app.channels.removeSourceAsync(id);revision++;onChanged();dialog=""}})}
  }
  "添加"->AddressInputDialog("",epg=false,onDismiss={closeAdd()},onConfirm={input->save(subscriptionFromAddress(input,app.channels.sources));closeAdd()})
 }}
}
