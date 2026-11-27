// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.tv

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.nexttv.BuildConfig
import com.nexttv.core.App
import com.nexttv.core.data.CacheStore
import kotlinx.coroutines.*

private val settingsCategories=listOf("订阅与节目单","播放","频道与外观","高级","关于")
private const val UPDATE_SUBSCRIPTIONS = "更新订阅"
private const val START_PAGE = "开始页面"
private const val REQUEST_PARAMETERS = "请求参数"
private const val HOME_PAGE = "主页"
private const val LIVE_PAGE = "直播"
private typealias SettingsEntry = SettingsRowData

/** Category navigation and content share the application's native input/focus/material policies. */
@Composable fun SettingsScreen(navigation:SettingsNavigation,restoreTick:Int,navFocused:Boolean,onContentFocus:()->Unit,onBackToNav:()->Unit,onTopBack:()->Unit,onSources:()->Unit,onChanged:()->Unit){
 val app=App.get();val p=app.prefs;val scope=rememberCoroutineScope();val view=LocalView.current
 val activity=LocalActivity.current as TvActivity
 var category by remember{mutableStateOf(navigation.group.takeIf{it in settingsCategories}?:settingsCategories.first())}
 var inContent by remember{mutableStateOf(!navigation.row.startsWith("分类/"))}
 var subpage by remember{mutableStateOf("")}
 var edit by remember{mutableStateOf("")}
 var pendingRestore by remember{mutableStateOf<Job?>(null)}
 var revision by remember{mutableIntStateOf(0)}
 var cacheBytes by remember{mutableLongStateOf(-1L)}
 LaunchedEffect(Unit){cacheBytes=withContext(Dispatchers.IO){CacheStore.usage(view.context)}}
 var explanation by remember{mutableStateOf("添加订阅、选择节目单来源，或更新频道与节目时间。")}
 val categories=remember{settingsCategories.associateWith{FocusRequester()}}
 val contentRequests=remember{mutableMapOf<String,FocusRequester>()}
 fun changed(){revision++;onChanged()}
 fun focusCategory(){inContent=false;runCatching{categories[category]?.requestFocus()}}
 fun dismissEdit(){if(edit.isBlank()){activity.continueBackAfterDialog();return};pendingRestore?.cancel();edit="";pendingRestore=scope.launch{while(!view.hasWindowFocus())withFrameNanos{};withFrameNanos{};runCatching{contentRequests[navigation.row]?.requestFocus()}}}
 fun back(){if(com.nexttv.BuildConfig.DEBUG)android.util.Log.d("NextTVFocusBack","settings edit=$edit content=$inContent nav=$navFocused row=${navigation.row} window=${view.hasWindowFocus()}");pendingRestore?.cancel();when{
  edit.isNotBlank()->dismissEdit()
  subpage.isNotBlank()->{focusCategory();subpage="";scope.launch{navigation.scroll.scrollTo(0);withFrameNanos{};contentRequests["$category/自动更新"]?.requestFocus()}}
  navFocused->onTopBack()
  inContent->{focusCategory();scope.launch{if(p.reduceMotion)navigation.scroll.scrollTo(0)else navigation.scroll.animateScrollTo(0,androidx.compose.animation.core.tween(180))}}
  else->onBackToNav()
 }}
 val latestBack by rememberUpdatedState<()->Unit>({back()})
 val owner=remember{Any()}
 DisposableEffect(activity){activity.bindPageBack(owner){latestBack()};onDispose{activity.unbindPageBack(owner)}}
 BackHandler{latestBack()}
 SideEffect{navigation.cancelPendingFocus={pendingRestore?.cancel()};navigation.onContentFocus=onContentFocus;navigation.onRowFocus={_,_->}}
 LaunchedEffect(navFocused){if(navFocused){pendingRestore?.cancel();category=settingsCategories.first();subpage="";inContent=false;navigation.scroll.scrollTo(0)}}
 LaunchedEffect(restoreTick){if(restoreTick>0&&!navFocused){while(!view.hasWindowFocus())withFrameNanos{};withFrameNanos{};if(inContent)navigation.focusRow()else focusCategory()}}
 @Suppress("UNUSED_VARIABLE") val read=revision
 val deviceName = remember(view.context) {
  runCatching {
   android.provider.Settings.Global.getString(view.context.contentResolver, "device_name")
  }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: android.os.Build.MODEL
 }
 val resolution = rememberWindowResolution()
 val hardware = listOf(android.os.Build.MANUFACTURER, android.os.Build.MODEL)
  .map(String::trim)
  .filter { it.isNotEmpty() && !it.equals("unknown", ignoreCase = true) }
  .distinct()
  .joinToString(" ")
 val androidInfo = "Android ${android.os.Build.VERSION.RELEASE}（API ${android.os.Build.VERSION.SDK_INT}）"
 val deviceInfo = listOf(hardware, androidInfo).filter(String::isNotEmpty).joinToString(" · ")
 val entries=when {
  subpage=="自动更新"->listOf(
   SettingsEntry("启动时更新订阅",if(p.autoUpdateOnStart)"开启"else"关闭","统一控制所有已启用订阅的自动更新；单个订阅不再单独开关。",{p.autoUpdateOnStart=!p.autoUpdateOnStart;changed()},false),
   SettingsEntry("自动更新节目单",if(p.autoUpdateEpg)"开启"else"关闭","定期更新节目时间与回放信息。",{p.autoUpdateEpg=!p.autoUpdateEpg;changed()},false))
  category=="订阅与节目单"->listOf(
   SettingsEntry("管理订阅","${app.channels.sources.size} 个订阅","添加、编辑、启停或删除频道订阅。",onSources),
   SettingsEntry("节目单来源",if(p.epgUrl.isBlank())"订阅自带"else"自定义","自定义节目单优先；未单独配置时使用订阅自带节目单。",{edit="节目单来源"}),
   SettingsEntry(UPDATE_SUBSCRIPTIONS,if(app.updatingAll)"正在更新…"else"","更新所有已启用的订阅及其节目单，也会更新已配置的自定义节目单。失败时保留缓存。",{app.refreshSubscriptionsAndGuide()},false),
   SettingsEntry("自动更新","","设置订阅与节目单的自动更新方式。",{focusCategory();subpage="自动更新";scope.launch{navigation.scroll.scrollTo(0);withFrameNanos{};contentRequests["$category/启动时更新订阅"]?.requestFocus()}}))
  category=="播放"->listOf(
   SettingsEntry(START_PAGE,if(p.startLive)LIVE_PAGE else HOME_PAGE,"「主页」打开频道首页。「直播」直接播放上次观看的频道，找不到时播放列表中的第一个频道。",{edit=START_PAGE}),
   SettingsEntry("隐藏异常线路",if(p.hideUnavailableRoutes)"开启"else"关闭","后台探测线路：首页空闲时快速检查，约 1 分钟覆盖全部线路；播放中低并发小批量巡检，不干扰播放。超时线路先标记疑似，确认后才隐藏；隐藏线路每 8 秒复测，恢复后自动显示。",{p.hideUnavailableRoutes=!p.hideUnavailableRoutes;changed()},false),
   SettingsEntry("换台预加载",listOf("关闭","下一台","上一台","上下台")[p.preloadMode],"提前准备相邻频道；「上下台」会同时预加载上一台和下一台，会增加网络与内存占用。",{edit="换台预加载"}),
   SettingsEntry("换台保留画面",if(p.keepLastFrame)"开启"else"关闭","切换频道或线路时保留上一画面，直到新画面出现。",{p.keepLastFrame=!p.keepLastFrame;changed()},false),
   SettingsEntry("时间显示",playerClockTitle(p.playerClockMode),"设置播放器右上角时间：隐藏、始终显示、每小时显示或每半小时显示。",{edit="时间显示"}))
  category=="频道与外观"->listOf(
   SettingsEntry("频道合并",if(p.mergeChannelsAcrossSources)"开启"else"关闭","将不同订阅中的同一频道合成多条播放线路，并汇总节目单。优先匹配频道标识，缺少标识时匹配无歧义的频道名。",{p.mergeChannelsAcrossSources=!p.mergeChannelsAcrossSources;scope.launch{app.channels.rebuildChannelsAsync();changed()}},false),
   SettingsEntry("频道布局",if(p.gridMode)"网格"else"列表","选择首页频道的展示方式。",{edit="频道布局"}),
   SettingsEntry("频道预览",if(p.channelPreviews)"开启"else"关闭","停稳后同时静音预览当前可见频道，操作时暂停；会增加网络与解码开销，关闭后停止全部预览。",{p.channelPreviews=!p.channelPreviews;changed()},false),
   SettingsEntry("界面主题",if(p.lightGlass)"浅色"else"深色","选择深色或浅色，应用于首页、设置和播放器浮层。",{edit="界面主题"}),
   SettingsEntry("列表台标",if(p.showChannelLogos)"显示"else"隐藏","控制播放时频道侧栏中的台标；栏宽保持不变。",{p.showChannelLogos=!p.showChannelLogos;changed()},false),
   SettingsEntry("动态效果",if(p.reduceMotion)"减弱"else"标准","减弱模式关闭胶囊回弹、焦点渐变、浮层过渡与换台渐变。鼠标滚轮、触控滑动及其惯性仍可使用。",{edit="动态效果"}))
  category=="高级"->listOf(
   SettingsEntry(REQUEST_PARAMETERS,if(p.defaultReferer.isBlank()&&p.defaultUserAgent==com.nexttv.core.data.Prefs.DEFAULT_UA)"默认"else"自定义","设置订阅、节目单和播放请求的默认 User-Agent 与 Referer。源或线路有专用参数时优先使用。",{edit=REQUEST_PARAMETERS}),
   SettingsEntry("仅 HTTPS",if(p.httpsOnly)"开启"else"关闭","拒绝 http:// 订阅、节目单和播放地址。默认关闭以兼容局域网或未加密的 IPTV 源。",{p.httpsOnly=!p.httpsOnly;changed()},false),
   SettingsEntry("清除缓存",CacheStore.format(cacheBytes),"删除频道、节目单、播放源备份和台标缓存；不会删除订阅、收藏或设置。",{edit="清除缓存"}))
  else -> listOf(
   SettingsEntry("应用版本", BuildConfig.VERSION_NAME),
   SettingsEntry("设备名称", deviceName),
   SettingsEntry("设备信息", deviceInfo),
   SettingsEntry("设备架构", android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "未知"),
   SettingsEntry("设备分辨率", resolution, "本应用当前渲染窗口的物理像素尺寸。")
  )
 }
 fun enterContent(){scope.launch{withFrameNanos{};entries.firstOrNull{it.action!=null}?.let{contentRequests["$category/${it.title}"]?.requestFocus()}}}
 val configuration=LocalConfiguration.current
 val narrow=configuration.screenWidthDp<800
 val bodyHeight=(28f*LocalDensity.current.fontScale.coerceAtLeast(1f)+16f).dp
 // A bounded reading canvas keeps values beside their labels on wide televisions.
 // Short windows retain scrolling, rather than shrinking typography or focus targets.
 BoxWithConstraints(Modifier.fillMaxSize(),contentAlignment=Alignment.TopCenter){
 val canvasWidth=if(narrow)maxWidth-24.dp else (maxWidth*.84f).coerceAtMost(1000.dp)
 Row(Modifier.onPreviewKeyEvent{if(edit.isBlank()&&it.nativeKeyEvent.action==android.view.KeyEvent.ACTION_DOWN)pendingRestore?.cancel();false}.width(canvasWidth).fillMaxHeight().padding(top=if(narrow)8.dp else 4.dp,bottom=12.dp),horizontalArrangement=Arrangement.spacedBy(if(narrow)18.dp else 32.dp)){

  Column(Modifier.weight(if(narrow).36f else .24f).fillMaxHeight()){
   // Align the first category with the first content row, without repeating the selected tab title.
   Spacer(Modifier.height(38.dp*LocalDensity.current.fontScale.coerceAtLeast(1f)))
   Column(Modifier.fillMaxWidth().appVerticalScroll(rememberScrollState()).padding(12.dp),verticalArrangement=Arrangement.spacedBy(2.dp)){
    settingsCategories.forEachIndexed{index,name->
     val key="分类/$name";val request=categories.getValue(name)
     DisposableEffect(key,request){navigation.rows[key]=request;onDispose{navigation.rows.remove(key)}}
     TvSurface(Modifier.fillMaxWidth().focusRequester(request).focusEdges(left=true,down=index==settingsCategories.lastIndex).focusProperties{left=FocusRequester.Cancel;right=FocusRequester.Cancel;if(index==0)up=navigation.top?:FocusRequester.Default;if(index==settingsCategories.lastIndex)down=FocusRequester.Cancel}.onPreviewKeyEvent{e->if(e.nativeKeyEvent.action==android.view.KeyEvent.ACTION_DOWN&&TvInput.action(e.nativeKeyEvent.keyCode)==InputAction.RIGHT){enterContent();true}else false},kind="category",selected=category==name,onFocus={
      pendingRestore?.cancel();inContent=false;onContentFocus();navigation.row=key;navigation.group=name
      if(category!=name){category=name;subpage="";scope.launch{navigation.scroll.scrollTo(0)}}
      explanation=when(name){"订阅与节目单"->"管理频道订阅与节目单。";"播放"->"设置启动方式、换台体验与播放信息。";"频道与外观"->"调整频道展示与动态效果。";"高级"->"配置特殊订阅服务的网络要求。";else->""}
     },onClick={if(InteractionStyle.pointer)runCatching{request.requestFocus()}else enterContent()}){Row(Modifier.fillMaxWidth().height(bodyHeight).padding(horizontal=12.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically){
      AppText(name,Modifier.weight(1f),color=if(category==name)NextTvStyle.Ivory else NextTvStyle.Muted,fontSize=InteractionStyle.body,fontWeight=if(category==name)FontWeight.SemiBold else FontWeight.Normal,maxLines=2)
     }}
    }
   }
  }
  Column(Modifier.weight(if(narrow).64f else .76f).fillMaxHeight()){
   AppText(subpage.ifBlank{category},Modifier.padding(start=12.dp,bottom=8.dp),color=NextTvStyle.Text,fontSize=InteractionStyle.heading,fontWeight=FontWeight.SemiBold,maxLines=1,lineHeight=InteractionStyle.heading*1.1f)
   AppText(when(category){"订阅与节目单"->"管理频道订阅、节目单来源与自动更新。";"播放"->"设置启动方式、换台体验和播放信息。";"频道与外观"->"调整频道展示、主题与动态效果。";"高级"->"配置网络请求、协议限制与缓存。";else->"应用版本与设备信息。"},Modifier.padding(start=12.dp,bottom=16.dp),color=NextTvStyle.Muted,fontSize=InteractionStyle.caption,maxLines=2)
   BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()){
   val availableHeight=maxHeight
   // Explanation travels with the card, without drifting to the screen's bottom edge.
   Column(Modifier.fillMaxWidth()){
   val helpHeight=(InteractionStyle.caption.value*1.4f*LocalDensity.current.fontScale*2+24).dp.coerceAtLeast(64.dp)
   Column(Modifier.fillMaxWidth().heightIn(max=(availableHeight-helpHeight).coerceAtLeast(bodyHeight)).padding(horizontal=12.dp).onGloballyPositioned{navigation.viewport=it}.appVerticalScroll(navigation.scroll).padding(vertical=10.dp)){
    entries.forEachIndexed{index,entry->
     val key="$category/${entry.title}";val request=remember(key){FocusRequester()}
     DisposableEffect(key,request){if(entry.action!=null){contentRequests[key]=request;navigation.rows[key]=request};onDispose{contentRequests.remove(key);navigation.rows.remove(key);navigation.coordinates.remove(key)}}
     val modifier=Modifier.fillMaxWidth().onGloballyPositioned{if(entry.action!=null)navigation.coordinates[key]=it}.then(if(entry.action!=null)Modifier.focusRequester(request).focusEdges(up=index==entries.indexOfFirst{it.action!=null},down=index==entries.indexOfLast{it.action!=null},right=true).focusProperties{left=FocusRequester.Cancel;right=FocusRequester.Cancel;if(index==entries.indexOfFirst{it.action!=null})up=FocusRequester.Cancel;if(index==entries.indexOfLast{it.action!=null})down=FocusRequester.Cancel}.onPreviewKeyEvent{e->if(e.nativeKeyEvent.action==android.view.KeyEvent.ACTION_DOWN&&TvInput.action(e.nativeKeyEvent.keyCode)==InputAction.LEFT){if(subpage.isNotBlank())back()else focusCategory();true}else false}else Modifier)
     val body:@Composable BoxScope.()->Unit={
      SettingsRowContent(entry,category,narrow,bodyHeight,Modifier.fillMaxWidth())
     }
     if(entry.action!=null)TvSurface(modifier,kind="setting",onFocus={pendingRestore?.cancel();inContent=true;navigation.focused(category,key);explanation=entry.explanation},onClick={pendingRestore?.cancel();inContent=true;navigation.row=key;entry.action.invoke()},content=body)else Box(modifier,content=body)
     if(index<entries.lastIndex)Box(Modifier.padding(horizontal=14.dp).fillMaxWidth().height(.5.dp).background(NextTvStyle.Divider,RoundedCornerShape(1.dp)))
    }
   }
   Column(Modifier.fillMaxWidth().height(helpHeight).padding(horizontal=16.dp,vertical=12.dp)){
    AppText(if(category=="订阅与节目单"&&navigation.row.endsWith("/$UPDATE_SUBSCRIPTIONS")&&app.updateStatus.isNotBlank())app.updateStatus else explanation,fontSize=InteractionStyle.caption,color=NextTvStyle.Muted,maxLines=2)
   }
  }
 }
 }
 }
 }
 if(edit.isNotBlank())StableDialogHost({if(edit=="节目单地址")edit="节目单来源"else dismissEdit()},transitionKey=edit){when(edit){
  "节目单来源"->TvDialog(edit,{dismissEdit()}){
   AppText(if(p.epgUrl.isBlank())"当前使用订阅自带的节目单"else"当前使用自定义节目单",color=NextTvStyle.Text,fontSize=InteractionStyle.body)
   TvButton("自定义节目单",Modifier.fillMaxWidth(),onClick={edit="节目单地址"})
   if(p.epgUrl.isNotBlank())TvButton("恢复自动",Modifier.fillMaxWidth(),onClick={p.epgUrl="";changed();app.refreshEpg();dismissEdit()})
  }
  "节目单地址"->AddressInputDialog(p.epgUrl,epg=true,onDismiss={edit="节目单来源"},onConfirm={p.epgUrl=it;changed();app.refreshEpg();dismissEdit()})
  "换台预加载"->ChoiceDialog(edit,listOf("关闭","下一台","上一台","上下台").mapIndexed{i,t->ChoiceOption("$i",t)},"${p.preloadMode}",{dismissEdit()}){p.preloadMode=it.toInt();PlaybackSession.active?.applyPreloadSetting();changed();dismissEdit()}
  START_PAGE->ChoiceDialog(edit,listOf(ChoiceOption("home",HOME_PAGE),ChoiceOption("live",LIVE_PAGE)),if(p.startLive)"live"else"home",{dismissEdit()}){p.startLive=it=="live";changed();dismissEdit()}
  "频道布局"->ChoiceDialog(edit,listOf(ChoiceOption("grid","网格"),ChoiceOption("list","列表")),if(p.gridMode)"grid"else"list",{dismissEdit()}){p.gridMode=it=="grid";changed();dismissEdit()}
  "界面主题"->ChoiceDialog(edit,listOf(ChoiceOption("dark","深色"),ChoiceOption("light","浅色")),if(p.lightGlass)"light"else"dark",{dismissEdit()}){p.lightGlass=it=="light";changed();dismissEdit()}
  "动态效果"->ChoiceDialog(edit,listOf(ChoiceOption("standard","标准"),ChoiceOption("reduced","减弱")),if(p.reduceMotion)"reduced"else"standard",{dismissEdit()}){p.reduceMotion=it=="reduced";changed();dismissEdit()}
  "时间显示"->ChoiceDialog(edit,listOf(ChoiceOption("0","隐藏","不显示右上角时间"),ChoiceOption("1","始终显示","播放时始终显示右上角时间"),ChoiceOption("2","每小时显示","每小时整点显示 ${PLAYER_CLOCK_WINDOW_MS/1000} 秒"),ChoiceOption("3","每半小时显示","每小时 00 分和 30 分显示 ${PLAYER_CLOCK_WINDOW_MS/1000} 秒")),"${p.playerClockMode}",{dismissEdit()}){p.playerClockMode=it.toInt();changed();dismissEdit()}
  REQUEST_PARAMETERS->NetworkRequestDialog({dismissEdit()}){ua,ref->p.defaultUserAgent=ua;p.defaultReferer=ref;changed();dismissEdit()}
  "清除缓存"->TvDialog(edit,{dismissEdit()}){
   AppText("将删除频道、节目单、播放源备份、台标缓存和线路失败记录，不会删除订阅、收藏或设置。当前占用 ${CacheStore.format(cacheBytes)}。",Modifier.weight(1f,false).appVerticalScroll(rememberScrollState()),color=NextTvStyle.Text,fontSize=InteractionStyle.body)
   Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
    TvButton("取消",onClick={dismissEdit()})
    TvButton("清除",kind="danger",onClick={
     scope.launch{
      cacheBytes=withContext(Dispatchers.IO){CacheStore.clear(view.context)}
      app.epg.clearMemoryCache()
      cacheBytes=withContext(Dispatchers.IO){CacheStore.usage(view.context)}
      changed();dismissEdit()
     }
    })
   }
  }
 }}
}

/** Window pixels, not the design-scaled dp canvas, so HDMI mode changes stay truthful. */
@Composable private fun rememberWindowResolution():String{
 val view=LocalView.current
 var resolution by remember{mutableStateOf("检测中…")}
 DisposableEffect(view){
  val root=view.rootView
  fun refresh(){
   val next=formatWindowResolution(root.width,root.height)
   if(next!=resolution)resolution=next
  }
  refresh()
  val observer=root.viewTreeObserver
  val listener=android.view.ViewTreeObserver.OnGlobalLayoutListener{refresh()}
  observer.addOnGlobalLayoutListener(listener)
  onDispose{if(observer.isAlive)observer.removeOnGlobalLayoutListener(listener)}
 }
 return resolution
}

internal fun formatWindowResolution(width:Int,height:Int):String=if(width>0&&height>0)"$width × $height" else "检测中…"

/** Two editable fields have explicit remote/Tab order; the IME owns text entry only after confirmation. */
@Composable private fun NetworkRequestDialog(onDismiss:()->Unit,onSave:(String,String)->Unit){
 val p=App.get().prefs
 var ua by remember{mutableStateOf(p.defaultUserAgent)};var ref by remember{mutableStateOf(p.defaultReferer)}
 val first=remember{FocusRequester()};val second=remember{FocusRequester()};val save=remember{FocusRequester()}
 var editing by remember{mutableStateOf("")}
 TvDialog(REQUEST_PARAMETERS,onDismiss,keyboardSession=if(editing.isBlank())0 else 1,onKeyboardDismiss={editing=""}){
  val keyboard=LocalSoftwareKeyboardController.current
  val inputView=LocalView.current;val inWindowModal=LocalInWindowModal.current
  LaunchedEffect(Unit){awaitModalFocusWindow(inputView,inWindowModal);first.requestFocus()}
  LaunchedEffect(editing){if(editing.isNotBlank()){withFrameNanos{};(if(editing=="User-Agent")first else second).requestFocus();keyboard?.show()}}
  val field:@Composable (String,String,(String)->Unit,FocusRequester,FocusRequester?,FocusRequester)->Unit={label,value,change,request,up,down->
   androidx.compose.material3.OutlinedTextField(value,change,Modifier.fillMaxWidth().focusRequester(request).onFocusChanged{if(!it.isFocused&&editing==label)editing=""}.focusProperties{this.up=up?:FocusRequester.Cancel;this.down=down;next=down;previous=up?:FocusRequester.Cancel}.onPreviewKeyEvent{e->
    if(e.nativeKeyEvent.action!=android.view.KeyEvent.ACTION_DOWN||editing.isNotBlank())false else when(TvInput.action(e.nativeKeyEvent.keyCode)){
     InputAction.DOWN->{down.requestFocus();true}
     InputAction.UP->{up?.requestFocus();true}
     InputAction.CONFIRM->{editing=label;true}
     else->false
    }
   }.pointerInput(label){awaitEachGesture{awaitFirstDown(requireUnconsumed=false);editing=label;waitForUpOrCancellation()}},singleLine=true,readOnly=editing!=label,colors=androidx.compose.material3.OutlinedTextFieldDefaults.colors(focusedBorderColor=NextTvStyle.Muted,focusedLabelColor=NextTvStyle.Text,cursorColor=NextTvStyle.Text),keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(showKeyboardOnFocus=false),shape=RoundedCornerShape(InteractionStyle.radius),label={AppText(label,color=NextTvStyle.Text)})
  }
  Column(Modifier.weight(1f,false).appVerticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
   field("User-Agent",ua,{ua=it},first,null,second)
   field("Referer",ref,{ref=it},second,first,save)
  }
  TvButton("保存",Modifier.focusRequester(save).focusEdges(down=true,left=true,right=true).focusProperties{up=second;previous=second;down=FocusRequester.Cancel;left=FocusRequester.Cancel;right=FocusRequester.Cancel;next=first},onClick={onSave(ua.trim().ifBlank{com.nexttv.core.data.Prefs.DEFAULT_UA},ref.trim())})
 }
}

/** Settings categories use a plain text rail with one selection state. */
