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
import kotlin.math.roundToInt

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

/**
 * Render the 960×540 dp TV canvas at the physical size the panel actually is.
 * Both dp and sp are scaled together, so a 4K panel does not turn fixed-width
 * panels or 44 dp rows into half-size islands with clipped copy. The system
 * font scale is preserved, and the 4.0 ceiling keeps extreme windows sane.
 */
@Composable internal fun TvViewportScale(content:@Composable ()->Unit){
 val baseDensity=androidx.compose.ui.platform.LocalDensity.current
 val baseConfig=androidx.compose.ui.platform.LocalConfiguration.current
 val viewport=tvViewportScale(baseConfig.screenHeightDp)
 val scaledDensity=androidx.compose.runtime.remember(baseDensity,viewport){Density(baseDensity.density*viewport,baseDensity.fontScale)}
 val scaledConfig=androidx.compose.runtime.remember(baseConfig,viewport){
  android.content.res.Configuration(baseConfig).apply {
   screenWidthDp=(baseConfig.screenWidthDp/viewport).roundToInt().coerceAtLeast(1)
   screenHeightDp=(baseConfig.screenHeightDp/viewport).roundToInt().coerceAtLeast(1)
   if (smallestScreenWidthDp>0) smallestScreenWidthDp=(smallestScreenWidthDp/viewport).roundToInt().coerceAtLeast(1)
   densityDpi=(baseConfig.densityDpi*viewport).roundToInt()
  }
 }
 androidx.compose.runtime.CompositionLocalProvider(
  androidx.compose.ui.platform.LocalDensity provides scaledDensity,
  androidx.compose.ui.platform.LocalConfiguration provides scaledConfig,
  content=content,
 )
}

/**
 * The design ramp is authored for a 960×540 dp TV canvas (1080p at xhdpi).
 * Scaling by height keeps the physical text proportion identical on 720p, 1080p
 * and 4K screens instead of only reacting to unusually wide logical canvases.
 * The upper bound prevents pathological window sizes from swallowing whole rows.
 */
internal fun tvViewportScale(heightDp:Int):Float=(heightDp/540f).coerceIn(1f,4f)
