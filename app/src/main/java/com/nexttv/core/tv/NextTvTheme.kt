// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.tv

import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape

/** Both Material families consume the same reactive semantic palette, including text inputs. */
@Composable internal fun NextTvTheme(content:@Composable ()->Unit){
 val light=com.nexttv.core.App.get().prefs.lightGlass
 val material=if(light)androidx.compose.material3.lightColorScheme()else androidx.compose.material3.darkColorScheme()
 val tv=if(light)androidx.tv.material3.lightColorScheme()else androidx.tv.material3.darkColorScheme()
 androidx.compose.material3.MaterialTheme(typography=NextTvTypography.material,
  colorScheme=material.copy(primary=NextTvStyle.Accent,onPrimary=NextTvStyle.Background,
   background=NextTvStyle.Background,onBackground=NextTvStyle.Text,
   surface=NextTvStyle.Surface,onSurface=NextTvStyle.Text,onSurfaceVariant=NextTvStyle.Muted,
   surfaceVariant=NextTvStyle.Button,outline=NextTvStyle.Stroke,error=NextTvStyle.Error),
  shapes=androidx.compose.material3.Shapes(extraSmall=RoundedCornerShape(InteractionStyle.badgeRadius),small=RoundedCornerShape(InteractionStyle.radius),medium=RoundedCornerShape(InteractionStyle.panelRadius),large=RoundedCornerShape(InteractionStyle.panelRadius),extraLarge=RoundedCornerShape(InteractionStyle.panelRadius))){
   androidx.compose.runtime.CompositionLocalProvider(LocalAppForeground provides NextTvStyle.Text){
   androidx.tv.material3.MaterialTheme(colorScheme=tv.copy(background=NextTvStyle.Background,onBackground=NextTvStyle.Text,surface=NextTvStyle.Surface,onSurface=NextTvStyle.Text,primary=NextTvStyle.Accent,onPrimary=NextTvStyle.Background),content=content)
   }
 }
}
