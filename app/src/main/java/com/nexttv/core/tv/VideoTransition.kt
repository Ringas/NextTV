// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.tv

/** A working old video surface must stay exposed while its snapshot is being captured. */
internal fun videoTransitionNeedsCover(keepFrame:Boolean,hasSnapshot:Boolean,surfaceHasFrame:Boolean,rendered:Boolean):Boolean =
 !keepFrame||(!hasSnapshot&&(!surfaceHasFrame||rendered))
