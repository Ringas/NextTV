// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv

import com.nexttv.core.data.isEpgRedirect
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpgRepositoryStatusTest {
    @Test fun notModifiedIsCacheHitNotRedirect() {
        assertFalse(isEpgRedirect(304))
        assertFalse(isEpgRedirect(200))
        assertFalse(isEpgRedirect(404))
    }

    @Test fun otherThreeHundredsStillRedirect() {
        assertTrue(isEpgRedirect(301))
        assertTrue(isEpgRedirect(302))
        assertTrue(isEpgRedirect(307))
        assertTrue(isEpgRedirect(308))
    }
}
