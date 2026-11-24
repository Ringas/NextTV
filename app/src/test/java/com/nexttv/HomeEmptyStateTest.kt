// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv

import com.nexttv.core.tv.homeEmptyActionLabel
import com.nexttv.core.tv.homeEmptyHint
import com.nexttv.core.tv.homeEmptyMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Home empty-state policy: each branch is user-visible copy, so order matters. */
class HomeEmptyStateTest {
    @Test fun favoritesEmptyWinsOverGenericEmpty() {
        assertEquals("还没有收藏频道", homeEmptyMessage(tab = 1, favoritesEmpty = true, noSources = true,
            addingSubscription = false, updating = false, anyEnabled = false, hasLoadedChannels = false))
    }

    @Test fun noSourcesBeatsUpdatingState() {
        assertEquals("添加订阅后开始使用", homeEmptyMessage(tab = 0, favoritesEmpty = false, noSources = true,
            addingSubscription = true, updating = true, anyEnabled = true, hasLoadedChannels = false))
    }

    @Test fun updatingAndDisabledSourcesHaveDistinctCopy() {
        assertEquals("正在更新订阅…", homeEmptyMessage(tab = 0, favoritesEmpty = false, noSources = false,
            addingSubscription = false, updating = true, anyEnabled = true, hasLoadedChannels = true))
        assertEquals("所有订阅均已停用", homeEmptyMessage(tab = 0, favoritesEmpty = false, noSources = false,
            addingSubscription = false, updating = false, anyEnabled = false, hasLoadedChannels = true))
    }

    @Test fun filteredGroupAndFallback() {
        assertEquals("当前分组下没有频道", homeEmptyMessage(tab = 0, favoritesEmpty = false, noSources = false,
            addingSubscription = false, updating = false, anyEnabled = true, hasLoadedChannels = true))
        assertEquals("订阅暂无可用频道，请重试", homeEmptyMessage(tab = 0, favoritesEmpty = false, noSources = false,
            addingSubscription = false, updating = false, anyEnabled = true, hasLoadedChannels = false))
    }

    @Test fun hintsOnlyForEmptyFavoritesOrFirstRun() {
        assertNull(homeEmptyHint(tab = 0, noSources = false, favoritesEmpty = false))
        assertEquals("粘贴 M3U 地址即可导入频道，无需经过多层设置",
            homeEmptyHint(tab = 0, noSources = true, favoritesEmpty = false))
        assertEquals("在频道上长按可添加收藏",
            homeEmptyHint(tab = 1, noSources = false, favoritesEmpty = true))
    }

    @Test fun actionLabelFollowsSubscriptionState() {
        assertEquals("添加订阅", homeEmptyActionLabel(noSources = true, anyEnabled = false, hasLoadedChannels = false))
        assertEquals("重试", homeEmptyActionLabel(noSources = false, anyEnabled = true, hasLoadedChannels = false))
        assertEquals("管理订阅", homeEmptyActionLabel(noSources = false, anyEnabled = true, hasLoadedChannels = true))
        assertNull(homeEmptyActionLabel(noSources = false, anyEnabled = true, hasLoadedChannels = true, tab = 1))
    }
}
