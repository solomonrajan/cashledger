/*
 * Copyright (c) 2018.
 * Copyright (c) 2026 solomonrajan.
 *
 * This file is part of MoneyWallet.
 *
 * MoneyWallet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MoneyWallet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MoneyWallet.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.oriondev.moneywallet.utils

import androidx.test.platform.app.InstrumentationRegistry
import com.oriondev.moneywallet.storage.database.DataContentProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * getCurrencies hands out a view of the cache, and the rate download service walks that view
 * across a run of database writes. A currency edit or a restore reloads the cache in the middle of
 * that walk, and the reload used to empty the very map being walked, so the download thread got a
 * ConcurrentModificationException part way down the list of currencies it was storing rates for.
 */
class CurrencyManagerConcurrencyTest {

    @Test
    @Throws(Exception::class)
    fun aReloadRefusesToRunInsideATransaction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        CurrencyManager.initialize(context)
        try {
            DataContentProvider.runInOneTransaction(context) {
                CurrencyManager.invalidateCache(context)
                null
            }
            fail("invalidateCache ran inside a transaction instead of refusing")
        } catch (expected: IllegalStateException) {
            val message = expected.message ?: ""
            assertTrue("refused for the wrong reason: $message", message.contains("inside a transaction"))
        }
    }

    @Test
    @Throws(Exception::class)
    fun aReloadDoesNotDisturbAWalkOfTheCurrencies() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        CurrencyManager.initialize(context)
        val beforeTheReload = CurrencyManager.getCurrencies()
        val expected = beforeTheReload.size
        assertTrue("only $expected currencies installed, too few to walk", expected > 1)
        val knownIso = beforeTheReload.iterator().next().iso

        val walkStarted = CountDownLatch(1)
        val reloadFinished = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val walked = AtomicInteger()

        val walker = Thread {
            try {
                for (unit in CurrencyManager.getCurrencies()) {
                    assertNotNull("a null currency in the cache", unit)
                    if (walked.incrementAndGet() == 1) {
                        walkStarted.countDown()
                        reloadFinished.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    }
                }
            } catch (t: Throwable) {
                failure.set(t)
            }
        }
        walker.start()
        assertTrue("the walk never started", walkStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))

        CurrencyManager.invalidateCache(context)
        reloadFinished.countDown()
        walker.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS))

        assertFalse("the walk was still running after $TIMEOUT_SECONDS seconds", walker.isAlive)
        assertNull("a reload landing in the middle of a walk threw ${failure.get()}", failure.get())
        assertEquals("the walk saw a different number of currencies than it started with", expected, walked.get())

        val afterTheReload = CurrencyManager.getCurrencies()
        assertNotSame("invalidateCache published no new map, so nothing was driven against the walk", beforeTheReload, afterTheReload)
        assertEquals("the reload published a different number of currencies than it read", expected, afterTheReload.size)
        assertNotNull("the reload published a map without $knownIso in it, which the cache held before it ran", CurrencyManager.getCurrency(knownIso))
    }

    companion object {
        private const val TIMEOUT_SECONDS = 30L
    }
}
