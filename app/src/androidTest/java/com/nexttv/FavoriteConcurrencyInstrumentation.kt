// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import com.nexttv.core.App
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class FavoriteConcurrencyInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        startActivitySync(
            Intent().setClassName("com.nexttv", "com.nexttv.core.tv.TvActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        val prefs = App.get().prefs
        val prefix = "favorite-regression-${UUID.randomUUID()}"
        val removed = (1..40).map { "$prefix-remove-$it" }
        val added = (1..40).map { "$prefix-add-$it" }
        val workers = Executors.newFixedThreadPool(2)
        val result = runCatching {
            for ((removeId, addId) in removed.zip(added)) {
                check(prefs.toggleFavorite(removeId))
                val start = CountDownLatch(1)
                val cleanup = workers.submit {
                    start.await()
                    prefs.removeFavorites(setOf(removeId))
                }
                val toggle = workers.submit {
                    start.await()
                    check(prefs.toggleFavorite(addId))
                }
                start.countDown()
                cleanup.get(5, TimeUnit.SECONDS)
                toggle.get(5, TimeUnit.SECONDS)
                check(!prefs.isFavorite(removeId))
                check(prefs.isFavorite(addId))
            }
            check(added.all(prefs::isFavorite))
        }
        workers.shutdownNow()
        prefs.removeFavorites((removed + added).toSet())
        finish(
            if (result.isSuccess) Activity.RESULT_OK else Activity.RESULT_CANCELED,
            Bundle().apply {
                putString(
                    "stream",
                    result.exceptionOrNull()?.stackTraceToString()
                        ?: "PASS 40 concurrent favorite toggle and cleanup pairs preserved both updates\n",
                )
            },
        )
    }
}
