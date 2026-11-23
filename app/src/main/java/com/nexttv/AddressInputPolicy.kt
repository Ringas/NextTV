package com.nexttv

/** Shared validation for QR submissions and the TV editor; transport may require a nonempty URL. */
enum class AddressInputKind{SUBSCRIPTION,PROGRAMME_GUIDE,PLAIN_URL}
fun normalizeAddressInput(value:String,kind:AddressInputKind,allowEmpty:Boolean=kind==AddressInputKind.PROGRAMME_GUIDE):String {
 if(allowEmpty&&kind==AddressInputKind.PROGRAMME_GUIDE&&value.isBlank())return ""
 return if(kind==AddressInputKind.SUBSCRIPTION)parseNamedSubscriptionAddress(value).input else validateSubscriptionAddress(value)
}
internal data class AddressInputLabels(val title:String,val field:String,val confirm:String,val action:String)
internal fun addressInputLabels(kind:AddressInputKind,editing:Boolean):AddressInputLabels=when(kind){
 AddressInputKind.PROGRAMME_GUIDE->AddressInputLabels("节目单地址","XMLTV 节目单地址","保存并更新","更新")
 else->if(editing)AddressInputLabels("编辑订阅","M3U 订阅地址","保存并刷新","保存")else AddressInputLabels("添加订阅","M3U 订阅地址","确认添加","添加")
}
