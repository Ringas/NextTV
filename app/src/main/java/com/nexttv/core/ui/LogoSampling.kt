package com.nexttv.core.ui
import com.nexttv.core.io.ImportPolicy
internal fun logoSampleSize(width:Int,height:Int):Int?{
 if(width<=0||height<=0||width>8192||height>8192||width.toLong()*height>ImportPolicy.LOGO_PIXELS)return null
 var sample=1;val longest=maxOf(width,height)
 while((longest+sample-1)/sample>420)sample*=2
 return sample
}
