// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.data

import com.nexttv.NamedSubscriptionAddress
import com.nexttv.parseNamedSubscriptionAddress

/** Shared add/edit resolution. Metadata and source identity survive edits and duplicate-URL adds. */
internal fun subscriptionFromAddress(input:String,sources:List<Source>,editing:Source?=null,newId:()->String={java.util.UUID.randomUUID().toString()}):Source {
 val parsed=parseNamedSubscriptionAddress(input)
 val previous=editing?:sources.firstOrNull{it.url==parsed.url}
 return previous?.copy(name=parsed.name.ifBlank{previous.name},url=parsed.url)
  ?:Source(newId(),parsed.name.ifBlank{"订阅 ${sources.size+1}"},parsed.url)
}
