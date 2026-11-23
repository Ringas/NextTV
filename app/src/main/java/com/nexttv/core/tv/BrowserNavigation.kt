// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.tv

internal enum class BrowserPane(val wire:String){CHANNELS("channels"),GROUPS("groups"),PROGRAMMES("epg")}
internal enum class BrowserRegion(val wire:String){CHANNELS("channels"),GROUPS("groups"),PROGRAMMES("epg"),DATES("dates")}
internal enum class BrowserMove{NONE,CHANNELS,GROUPS,PROGRAMMES,DATES}

/** One immutable snapshot; asynchronous outgoing focus callbacks cannot mutate navigation. */
internal data class BrowserNavigation(val pane:BrowserPane=BrowserPane.CHANNELS,val region:BrowserRegion=BrowserRegion.CHANNELS){
 init{require(when(pane){BrowserPane.CHANNELS->region==BrowserRegion.CHANNELS;BrowserPane.GROUPS->region==BrowserRegion.GROUPS||region==BrowserRegion.CHANNELS;BrowserPane.PROGRAMMES->region!=BrowserRegion.GROUPS})}
 fun focusChannels(closeAdjacent:Boolean=false)=if(closeAdjacent)BrowserNavigation()else copy(region=BrowserRegion.CHANNELS)
 fun horizontal(delta:Int,emptyProgramme:Boolean):BrowserMove=if(delta<0)when(region){
  BrowserRegion.GROUPS->BrowserMove.NONE
  BrowserRegion.CHANNELS->BrowserMove.GROUPS
  BrowserRegion.PROGRAMMES->BrowserMove.CHANNELS
  BrowserRegion.DATES->if(emptyProgramme)BrowserMove.CHANNELS else BrowserMove.PROGRAMMES
 }else if(delta>0)when(region){
  BrowserRegion.GROUPS->BrowserMove.CHANNELS
  BrowserRegion.CHANNELS->BrowserMove.PROGRAMMES
  BrowserRegion.PROGRAMMES->BrowserMove.DATES
  BrowserRegion.DATES->BrowserMove.NONE
 }else BrowserMove.NONE
 companion object{
  val Groups get()=BrowserNavigation(BrowserPane.GROUPS,BrowserRegion.GROUPS)
  val Programmes get()=BrowserNavigation(BrowserPane.PROGRAMMES,BrowserRegion.PROGRAMMES)
  val Dates get()=BrowserNavigation(BrowserPane.PROGRAMMES,BrowserRegion.DATES)
 }
}
