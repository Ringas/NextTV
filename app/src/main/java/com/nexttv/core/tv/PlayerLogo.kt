package com.nexttv.core.tv
import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.Text
import com.nexttv.core.data.Channel
import com.nexttv.core.ui.LogoLoader
@Composable internal fun PlayerLogo(c:Channel,modifier:Modifier,matchHeight:Dp?=null){val context=LocalContext.current;var logo by remember(c.logo){mutableStateOf<Bitmap?>(LogoLoader.cached(c.logo))};DisposableEffect(c.logo){val cancel=LogoLoader.fetch(context,c.logo){logo=it};onDispose{cancel()}};Box(if(matchHeight!=null)modifier.size(width=matchHeight*1.65f,height=matchHeight)else modifier,contentAlignment=Alignment.Center){if(logo!=null)Image(logo!!.asImageBitmap(),c.name,Modifier.fillMaxSize(),contentScale=ContentScale.Fit)else AppText(c.name.take(2),color=NextTvStyle.Text,fontSize=InteractionStyle.body)}}
