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

package com.oriondev.moneywallet.background

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.oriondev.moneywallet.model.Group
import com.oriondev.moneywallet.model.OverviewSetting
import com.oriondev.moneywallet.storage.database.DataContentProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import java.util.ArrayList
import java.util.Collections
import java.util.Date
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
class AbstractGenericLoaderTest {

    private lateinit var context: Context
    private lateinit var received: MutableList<String?>

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        received = ArrayList()
    }

    @Test
    fun aFirstStartLoadsAndHandsBackNothing() {
        val loader = watching(DataContentProvider.CONTENT_ALL)
        loader.startLoading()
        assertEquals(1, loader.loads)
        assertEquals(Collections.emptyList<String>(), received)
    }

    @Test
    fun aStartWithNothingWrittenSinceHandsBackWhatItHasAndDoesNotLoadAgain() {
        val loader = watching(DataContentProvider.CONTENT_ALL)
        loader.startLoading()
        loader.deliverResult("first")
        loader.stopLoading()
        loader.startLoading()
        assertEquals(1, loader.loads)
        assertEquals(listOf("first", "first"), received)
    }

    @Test
    fun aWriteWhileTheScreenIsAwayLoadsAgainAndSkipsTheOldAnswer() {
        val loader = watching(DataContentProvider.CONTENT_ALL)
        loader.startLoading()
        loader.deliverResult("first")
        loader.stopLoading()
        loader.onContentChanged()
        loader.startLoading()
        assertEquals(2, loader.loads)
        assertEquals(listOf("first"), received)
    }

    @Test
    fun aWriteOnTheProviderReachesALoaderWhoseScreenIsAway() {
        val loader = watching(DataContentProvider.CONTENT_ALL)
        loader.startLoading()
        loader.deliverResult("first")
        loader.stopLoading()
        context.contentResolver.notifyChange(DataContentProvider.CONTENT_TRANSACTIONS, null)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        loader.startLoading()
        assertEquals(2, loader.loads)
        assertEquals(listOf("first"), received)
    }

    @Test
    fun aLoaderWatchesItsUriWhileItLivesAndLetsItGoOnReset() {
        val uri = DataContentProvider.CONTENT_ALL
        val before = watchersOf(uri).size
        val loader = watching(uri)
        loader.startLoading()
        assertEquals(before + 1, watchersOf(uri).size)
        loader.stopLoading()
        assertEquals(before + 1, watchersOf(uri).size)
        loader.startLoading()
        assertEquals(before + 1, watchersOf(uri).size)
        loader.reset()
        assertEquals(before, watchersOf(uri).size)
    }

    @Test
    fun anAbandonedLoaderLetsItsUriGoWithoutWaitingForAReset() {
        val uri = DataContentProvider.CONTENT_ALL
        val before = watchersOf(uri).size
        val loader = watching(uri)
        loader.startLoading()
        loader.deliverResult("first")
        assertEquals(before + 1, watchersOf(uri).size)
        loader.abandon()
        assertEquals(before, watchersOf(uri).size)
    }

    @Test
    @Throws(Exception::class)
    fun aLoaderCanBeBuiltOnAThreadWithNoLooper() {
        val thrown = AtomicReference<Throwable?>()
        val thread = Thread {
            try {
                CountingLoader(context, DataContentProvider.CONTENT_ALL)
            } catch (t: Throwable) {
                thrown.set(t)
            }
        }
        thread.start()
        thread.join()
        assertNull(thrown.get())
    }

    @Test
    fun aLoaderWithNoUriWatchesNothing() {
        val uri = DataContentProvider.CONTENT_ALL
        val before = watchersOf(uri).size
        val loader = watching(null)
        loader.startLoading()
        assertEquals(before, watchersOf(uri).size)
    }

    @Test
    fun everyLoaderOverTheLedgerWatchesItAndTheOthersDoNot() {
        val start = Date(0L)
        val end = Date(1L)
        val setting = OverviewSetting(
            start,
            end,
            Group.MONTHLY,
            OverviewSetting.CashFlow.NET_INCOMES
        )
        assertEquals(DataContentProvider.CONTENT_ALL, OverviewDataLoader(context, setting).getObservedUri())
        assertEquals(DataContentProvider.CONTENT_ALL, PeriodDetailFlowLoader(context, start, end, false).getObservedUri())
        assertEquals(
            DataContentProvider.CONTENT_ALL,
            PeriodDetailSummaryLoader(context, start, end, PeriodDetailSummaryLoader.GROUP_BY_MONTH).getObservedUri()
        )
        assertNull(LicenseLoader(context).getObservedUri())
        assertNull(ChangeLogLoader(context).getObservedUri())
        assertNull(IconGroupLoader(context).getObservedUri())
        assertNull(JsonResourceLoader(context, "anything").getObservedUri())
    }

    private fun watching(uri: Uri?): CountingLoader {
        val loader = CountingLoader(context, uri)
        loader.registerListener(1) { _, data ->
            received.add(data)
        }
        return loader
    }

    private fun watchersOf(uri: Uri): Collection<ContentObserver> {
        return Shadows.shadowOf(context.contentResolver).getContentObservers(uri)
    }

    private class CountingLoader(
        context: Context,
        private val observed: Uri?
    ) : AbstractGenericLoader<String>(context) {

        var loads: Int = 0
            private set

        override fun getObservedUri(): Uri? = observed

        override fun loadInBackground(): String = "loaded"

        override fun forceLoad() {
            loads++
        }
    }
}
