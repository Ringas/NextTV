@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package com.nexttv.core.tv

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*

internal val LocalAppForeground=androidx.compose.runtime.staticCompositionLocalOf{NextTvStyle.Text}

internal object NextTvTypography {
 val body=TextStyle(fontFamily=FontFamily.SansSerif,fontSize=InteractionStyle.body,lineHeight=InteractionStyle.body*1.3f)
 val caption=body.copy(fontSize=InteractionStyle.caption,lineHeight=InteractionStyle.caption*1.35f)
 val heading=body.copy(fontSize=InteractionStyle.heading,lineHeight=InteractionStyle.heading*1.1f,fontWeight=FontWeight.SemiBold)
 val material=androidx.compose.material3.Typography(bodyLarge=body,bodyMedium=body,bodySmall=caption,labelLarge=body,labelMedium=caption,labelSmall=caption,titleMedium=heading,titleSmall=body)
}

@Composable internal fun AppText(text:String,modifier:Modifier=Modifier,color:Color=Color.Unspecified,fontSize:TextUnit=InteractionStyle.body,fontWeight:FontWeight?=null,maxLines:Int=Int.MAX_VALUE,overflow:TextOverflow=TextOverflow.Ellipsis,textAlign:TextAlign?=null,shadow:androidx.compose.ui.graphics.Shadow?=null,fontFeatureSettings:String?=null,lineHeight:TextUnit?=null,letterSpacing:TextUnit=TextUnit.Unspecified){
 val base=if(shadow==null)androidx.tv.material3.LocalTextStyle.current else androidx.tv.material3.LocalTextStyle.current.copy(shadow=shadow)
 androidx.tv.material3.Text(text,modifier,style=if(fontFeatureSettings==null)base else base.copy(fontFeatureSettings=fontFeatureSettings),color=if(color==Color.Unspecified)LocalAppForeground.current else color,fontSize=fontSize,fontWeight=fontWeight,fontFamily=FontFamily.SansSerif,lineHeight=lineHeight?:fontSize*1.3f,maxLines=maxLines,overflow=overflow,textAlign=textAlign,letterSpacing=letterSpacing)
}

/** Honor the system font scale and the viewport reference without double-enlarging the new type scale. */
@Composable internal fun AppTextScale(content:@Composable ()->Unit){
 val current=androidx.compose.ui.platform.LocalDensity.current
 val viewport=tvTypographyScale(androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp)
 val enlarged=androidx.compose.runtime.remember(current,viewport){Density(current.density,current.fontScale*viewport)}
 androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides enlarged,content=content)
}

/** Keep at least the requested enlarged size; compensate unusually wide logical TV canvases. */
internal fun tvTypographyScale(widthDp:Int):Float=(widthDp*29f/(1920f*16f)).coerceIn(1f,1.8f)
