package com.oriondev.moneywallet.ui.fragment.secondary

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.os.Looper
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.SplitLedger
import com.oriondev.moneywallet.storage.database.TestDatabases
import com.oriondev.moneywallet.storage.database.TransferContentValuesBuilder
import com.oriondev.moneywallet.ui.activity.BackupListActivity
import com.oriondev.moneywallet.ui.activity.NewEditTransactionActivity
import com.oriondev.moneywallet.ui.fragment.base.SecondaryPanelFragment
import com.oriondev.moneywallet.utils.CurrencyManager
import com.oriondev.moneywallet.utils.DateUtils
import com.oriondev.moneywallet.utils.MoneyFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class TransactionItemFragmentTest {

    companion object {
        private const val ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}"
        private const val TAG_FRAGMENT = "TransactionItemFragmentTest::Fragment"
        private const val MARKER_TIMEOUT_SECONDS = 5L
    }

    private lateinit var mResolver: ContentResolver
    private var mWalletId: Long = 0L
    private var mTransactionId: Long = 0L
    private var mSecondTransactionId: Long = 0L

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        TestDatabases.useFreshDatabase(context)
        mResolver = context.contentResolver
        mWalletId = insertWallet("Cash")
        val categoryId = insertCategory()
        mTransactionId = insertTransaction(mWalletId, categoryId, "Bread")
        mSecondTransactionId = insertTransaction(mWalletId, categoryId, "Milk")
    }

    @Test
    fun theRowGoesAwayOffTheMainThreadAndThePanelBlanksWhenTheCallbackRuns() {
        ActivityScenario.launch(BackupListActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = showTransaction(activity, mTransactionId)
                val fragmentView = fragment.requireView()
                val release = parkDeleteWorker()
                val reached: Boolean
                try {
                    clickDeleteAndConfirm(fragment)
                    assertTrue("the row went away inside the click", rowExists(mTransactionId))
                } finally {
                    release.countDown()
                    reached = awaitDeleteWorker()
                }
                assertTrue("the delete worker did not reach the marker within 5 seconds", reached)
                idleMainLooper()
                assertFalse("the row was still stored after the callback", rowExists(mTransactionId))
                assertEquals(0L, fragment.getItemId())
                assertEquals(
                    View.VISIBLE,
                    fragmentView.findViewById<View>(R.id.empty_screen_secondary_panel_layout).visibility
                )
            }
        }
    }

    @Test
    fun aFragmentTakenOffTheScreenBeforeTheCallbackKeepsItsItem() {
        ActivityScenario.launch(BackupListActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = showTransaction(activity, mTransactionId)
                val release = parkDeleteWorker()
                val reached: Boolean
                try {
                    clickDeleteAndConfirm(fragment)
                    activity.supportFragmentManager
                        .beginTransaction()
                        .remove(fragment)
                        .commitNow()
                } finally {
                    release.countDown()
                    reached = awaitDeleteWorker()
                }
                assertTrue("the delete worker did not reach the marker within 5 seconds", reached)
                idleMainLooper()
                assertEquals(mTransactionId, fragment.getItemId())
            }
        }
    }

    @Test
    fun aPanelPointedAtAnotherItemBeforeTheCallbackKeepsThatItem() {
        ActivityScenario.launch(BackupListActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = showTransaction(activity, mTransactionId)
                val fragmentView = fragment.requireView()
                val release = parkDeleteWorker()
                val reached: Boolean
                try {
                    clickDeleteAndConfirm(fragment)
                    fragment.showItemId(mSecondTransactionId)
                    idleMainLooper()
                } finally {
                    release.countDown()
                    reached = awaitDeleteWorker()
                }
                assertTrue("the delete worker did not reach the marker within 5 seconds", reached)
                idleMainLooper()
                assertEquals(mSecondTransactionId, fragment.getItemId())
                assertTrue("the second row went away", rowExists(mSecondTransactionId))
                assertFalse("the first row was still stored", rowExists(mTransactionId))
                assertEquals(
                    View.VISIBLE,
                    fragmentView.findViewById<View>(R.id.main_screen_secondary_panel_scroll_view).visibility
                )
            }
        }
    }

    @Test
    fun theToolbarMenuIgnoresAnEditWhileTheDeleteIsInFlight() {
        ActivityScenario.launch(BackupListActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = showTransaction(activity, mTransactionId)
                val toolbar = fragment.requireView().findViewById<Toolbar>(R.id.secondary_toolbar)
                assertNotNull(toolbar)
                val release = parkDeleteWorker()
                val reached: Boolean
                try {
                    clickDeleteAndConfirm(fragment)
                    toolbar?.menu?.performIdentifierAction(R.id.action_edit_item, 0)
                    idleMainLooper()
                    assertNull(
                        "the edit went through while the delete was in flight",
                        shadowOf(activity).nextStartedActivity
                    )
                } finally {
                    release.countDown()
                    reached = awaitDeleteWorker()
                }
                assertTrue("the delete worker did not reach the marker within 5 seconds", reached)
                idleMainLooper()
                assertFalse("the row was still stored after the callback", rowExists(mTransactionId))
            }
        }
    }

    @Test
    fun theSameRowSelectedAgainDuringTheDeleteKeepsTheToolbarDead() {
        ActivityScenario.launch(BackupListActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = showTransaction(activity, mTransactionId)
                val toolbar = fragment.requireView().findViewById<Toolbar>(R.id.secondary_toolbar)
                assertNotNull(toolbar)
                val release = parkDeleteWorker()
                val reached: Boolean
                try {
                    clickDeleteAndConfirm(fragment)
                    fragment.showItemId(mTransactionId)
                    idleMainLooper()
                    toolbar?.menu?.performIdentifierAction(R.id.action_edit_item, 0)
                    idleMainLooper()
                    assertNull(
                        "the edit went through on the row whose delete was in flight",
                        shadowOf(activity).nextStartedActivity
                    )
                } finally {
                    release.countDown()
                    reached = awaitDeleteWorker()
                }
                assertTrue("the delete worker did not reach the marker within 5 seconds", reached)
                idleMainLooper()
                assertFalse("the row was still stored after the callback", rowExists(mTransactionId))
                assertEquals(0L, fragment.getItemId())
            }
        }
    }

    @Test
    fun theDoomedRowSelectedAgainAfterAnotherOneKeepsTheToolbarDead() {
        ActivityScenario.launch(BackupListActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = showTransaction(activity, mTransactionId)
                val toolbar = fragment.requireView().findViewById<Toolbar>(R.id.secondary_toolbar)
                assertNotNull(toolbar)
                val release = parkDeleteWorker()
                val reached: Boolean
                try {
                    clickDeleteAndConfirm(fragment)
                    fragment.showItemId(mSecondTransactionId)
                    idleMainLooper()
                    fragment.showItemId(mTransactionId)
                    idleMainLooper()
                    toolbar?.menu?.performIdentifierAction(R.id.action_edit_item, 0)
                    idleMainLooper()
                    assertNull(
                        "the edit went through on the row whose delete was in flight",
                        shadowOf(activity).nextStartedActivity
                    )
                } finally {
                    release.countDown()
                    reached = awaitDeleteWorker()
                }
                assertTrue("the delete worker did not reach the marker within 5 seconds", reached)
                idleMainLooper()
                assertFalse("the row was still stored after the callback", rowExists(mTransactionId))
                assertEquals(0L, fragment.getItemId())
            }
        }
    }

    @Test
    fun theToolbarMenuComesBackOnTheOtherItemOnceTheDeleteLands() {
        ActivityScenario.launch(BackupListActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = showTransaction(activity, mTransactionId)
                val toolbar = fragment.requireView().findViewById<Toolbar>(R.id.secondary_toolbar)
                assertNotNull(toolbar)
                val release = parkDeleteWorker()
                val reached: Boolean
                try {
                    clickDeleteAndConfirm(fragment)
                    fragment.showItemId(mSecondTransactionId)
                    idleMainLooper()
                } finally {
                    release.countDown()
                    reached = awaitDeleteWorker()
                }
                assertTrue("the delete worker did not reach the marker within 5 seconds", reached)
                idleMainLooper()
                toolbar?.menu?.performIdentifierAction(R.id.action_edit_item, 0)
                idleMainLooper()
                val started: Intent? = shadowOf(activity).nextStartedActivity
                assertNotNull("the edit was still ignored after the delete landed", started)
                assertNotNull(started?.component)
                assertEquals(
                    NewEditTransactionActivity::class.java.name,
                    started?.component?.className
                )
                assertEquals(mSecondTransactionId, fragment.getItemId())
                assertTrue("the second row went away", rowExists(mSecondTransactionId))
            }
        }
    }

    @Test
    fun aRefusedDeleteIsReportedWhenThePanelHasMovedOn() {
        ActivityScenario.launch(BackupListActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val secondWalletId = insertWallet("Bank")
                val transferId = insertTransfer(mWalletId, secondWalletId)
                val legId = transferFromTransactionId(transferId)
                val fragment = showTransaction(activity, legId)
                val release = parkDeleteWorker()
                val confirm: AlertDialog
                val reached: Boolean
                try {
                    confirm = clickDeleteAndConfirm(fragment)
                    fragment.showItemId(mSecondTransactionId)
                    idleMainLooper()
                } finally {
                    release.countDown()
                    reached = awaitDeleteWorker()
                }
                assertTrue("the delete worker did not reach the marker within 5 seconds", reached)
                idleMainLooper()
                assertTrue("the leg of the transfer was deleted", rowExists(legId))
                assertEquals(mSecondTransactionId, fragment.getItemId())
                val reported = ShadowDialog.getLatestDialog() as? AlertDialog
                assertNotNull(reported)
                assertNotSame("no error dialog was put up", confirm, reported)
                val rep = reported ?: return@onActivity
                assertTrue("the error dialog was not showing", rep.isShowing)
                val message = rep.findViewById<TextView>(android.R.id.message)
                assertNotNull(message)
                assertEquals(
                    activity.getString(R.string.message_error_delete_transaction_of_transfer),
                    message?.text?.toString()
                )
            }
        }
    }

    @Test
    fun theToolbarMenuOpensTheEditorWhenNoDeleteIsInFlight() {
        ActivityScenario.launch(BackupListActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = showTransaction(activity, mTransactionId)
                val toolbar = fragment.requireView().findViewById<Toolbar>(R.id.secondary_toolbar)
                assertNotNull(toolbar)
                toolbar?.menu?.performIdentifierAction(R.id.action_edit_item, 0)
                idleMainLooper()
                val started = shadowOf(activity).nextStartedActivity
                assertNotNull("the toolbar menu never reached the fragment", started)
                assertNotNull(started?.component)
                assertEquals(
                    NewEditTransactionActivity::class.java.name,
                    started?.component?.className
                )
            }
        }
    }

    /**
     * RECEIPT is 10000 under Groceries with parts of 2000 in Household and 3000 in Fuel, and a
     * part flagged deleted that is not listed.
     */
    @Test
    fun aSplitRowListsItsPartsAndWhatIsLeftUnderTheMainCategory() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        SplitLedger.build(context)
        SplitLedger.database(context).execSQL("UPDATE transaction_splits SET split_note = 'soap' WHERE split_id = 930")
        ActivityScenario.launch(BackupListActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = showTransaction(activity, SplitLedger.RECEIPT)
                assertEquals(
                    activity.getString(R.string.split_part_with_note, "Household", euro(2000L), "soap") + "\n" +
                        activity.getString(R.string.split_part, "Fuel", euro(3000L)) + "\n" +
                        activity.getString(R.string.split_remainder, "Groceries", euro(10000L - 2000L - 3000L)),
                    awaitSplitText(fragment)
                )
            }
        }
    }

    /** CROSSED holds a Salary part on an expense, which no total counts. */
    @Test
    fun invalidPartsAreListedWithALineSayingTheyAreNotCounted() {
        SplitLedger.build(ApplicationProvider.getApplicationContext())
        ActivityScenario.launch(BackupListActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = showTransaction(activity, SplitLedger.CROSSED)
                assertEquals(
                    activity.getString(R.string.split_part, "Salary", euro(500L)) + "\n" +
                        activity.getString(R.string.split_not_counted),
                    awaitSplitText(fragment)
                )
            }
        }
    }

    /**
     * Coming back to the foreground, the loader hands the fragment the same parts cursor it
     * already read to the end, the way it does when the user backs out of the editor.
     */
    @Test
    fun thePartsStayListedWhenTheScreenComesBackToTheForeground() {
        SplitLedger.build(ApplicationProvider.getApplicationContext())
        ActivityScenario.launch(BackupListActivity::class.java).use { scenario ->
            val shown = arrayOfNulls<String>(1)
            scenario.onActivity { activity -> shown[0] = awaitSplitText(showTransaction(activity, SplitLedger.RECEIPT)) }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                idleMainLooper()
                val fragment = activity.supportFragmentManager.findFragmentByTag(TAG_FRAGMENT) as? TransactionItemFragment
                assertNotNull(fragment)
                val view = fragment?.view ?: error("fragment view is null")
                val split = view.findViewById<TextView>(R.id.split_text_view)
                assertEquals(View.VISIBLE, split.visibility)
                assertEquals(shown[0], split.text.toString())
            }
        }
    }

    /** The parts and the row come back through two loaders, so this waits for the line to fill. */
    private fun awaitSplitText(fragment: TransactionItemFragment): String {
        val view = fragment.view ?: error("fragment view is null")
        val split = view.findViewById<TextView>(R.id.split_text_view)
        var i = 0
        while (i < 200 && split.visibility != View.VISIBLE) {
            idleMainLooper()
            try {
                Thread.sleep(25)
            } catch (e: InterruptedException) {
                throw AssertionError(e)
            }
            i++
        }
        assertEquals("the parts never showed", View.VISIBLE, split.visibility)
        return split.text.toString()
    }

    private fun euro(money: Long): String {
        val currency = CurrencyManager.getCurrency("EUR") ?: error("EUR currency is null")
        return MoneyFormatter.getInstance().getNotTintedString(currency, money)
    }

    private fun parkDeleteWorker(): CountDownLatch {
        val release = CountDownLatch(1)
        submitToDeleteWorker {
            try {
                release.await()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        return release
    }

    private fun awaitDeleteWorker(): Boolean {
        val reached = CountDownLatch(1)
        submitToDeleteWorker { reached.countDown() }
        return try {
            reached.await(MARKER_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    private fun submitToDeleteWorker(runnable: Runnable) {
        SecondaryPanelFragment.sDeleteExecutor.execute(runnable)
    }

    private fun idleMainLooper() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun showTransaction(activity: FragmentActivity, transactionId: Long): TransactionItemFragment {
        val fragment = TransactionItemFragment()
        activity.supportFragmentManager
            .beginTransaction()
            .add(android.R.id.content, fragment, TAG_FRAGMENT)
            .commitNow()
        fragment.showItemId(transactionId)
        idleMainLooper()
        return fragment
    }

    private fun clickDeleteAndConfirm(fragment: TransactionItemFragment): AlertDialog {
        val toolbar = fragment.requireView().findViewById<Toolbar>(R.id.secondary_toolbar)
        assertNotNull(toolbar)
        val delete = toolbar?.menu?.findItem(R.id.action_delete_item)
        assertNotNull(delete)
        val del = delete ?: throw AssertionError("delete menu item is null")
        fragment.onMenuItemClick(del)
        val dialog = ShadowDialog.getLatestDialog() as? AlertDialog
        assertNotNull(dialog)
        val dlg = dialog ?: throw AssertionError("dialog is null")
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        idleMainLooper()
        return dlg
    }

    private fun rowExists(transactionId: Long): Boolean {
        val cursor: Cursor? = mResolver.query(
            ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSACTIONS, transactionId),
            arrayOf("*"), null, null, null
        )
        return cursor?.use { it.moveToFirst() } ?: false
    }

    private fun transferFromTransactionId(transferId: Long): Long {
        val cursor = mResolver.query(
            ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSFERS, transferId),
            arrayOf(Contract.Transfer.TRANSACTION_FROM_ID), null, null, null
        )
        assertNotNull(cursor)
        return cursor?.use {
            assertTrue("the transfer row was not stored", it.moveToFirst())
            it.getLong(it.getColumnIndexOrThrow(Contract.Transfer.TRANSACTION_FROM_ID))
        } ?: throw AssertionError("cursor is null")
    }

    private fun insertWallet(name: String): Long {
        val values = ContentValues()
        values.put(Contract.Wallet.NAME, name)
        values.put(Contract.Wallet.ICON, ICON)
        values.put(Contract.Wallet.CURRENCY, "EUR")
        values.put(Contract.Wallet.START_MONEY, 0L)
        values.put(Contract.Wallet.COUNT_IN_TOTAL, true)
        values.put(Contract.Wallet.ARCHIVED, false)
        val uri = mResolver.insert(DataContentProvider.CONTENT_WALLETS, values)
        return if (uri != null) ContentUris.parseId(uri) else -1L
    }

    private fun insertCategory(): Long {
        val values = ContentValues()
        values.put(Contract.Category.NAME, "Groceries")
        values.put(Contract.Category.ICON, ICON)
        values.put(Contract.Category.TYPE, Contract.CategoryType.EXPENSE.value)
        values.put(Contract.Category.SHOW_REPORT, true)
        val uri = mResolver.insert(DataContentProvider.CONTENT_CATEGORIES, values)
        return if (uri != null) ContentUris.parseId(uri) else -1L
    }

    private fun insertTransaction(walletId: Long, categoryId: Long, description: String): Long {
        val values = ContentValues()
        values.put(Contract.Transaction.MONEY, 1250L)
        values.put(Contract.Transaction.DATE, DateUtils.getSQLDateTimeString(Date()))
        values.put(Contract.Transaction.DESCRIPTION, description)
        values.put(Contract.Transaction.CATEGORY_ID, categoryId)
        values.put(Contract.Transaction.DIRECTION, Contract.Direction.EXPENSE)
        values.put(Contract.Transaction.TYPE, NewEditTransactionActivity.TYPE_STANDARD)
        values.put(Contract.Transaction.WALLET_ID, walletId)
        values.put(Contract.Transaction.CONFIRMED, true)
        values.put(Contract.Transaction.COUNT_IN_TOTAL, true)
        val uri = mResolver.insert(DataContentProvider.CONTENT_TRANSACTIONS, values)
        return if (uri != null) ContentUris.parseId(uri) else -1L
    }

    private fun insertTransfer(fromWalletId: Long, toWalletId: Long): Long {
        val values = TransferContentValuesBuilder()
            .description("Move")
            .date(DateUtils.getSQLDateTimeString(Date()))
            .fromWalletId(fromWalletId)
            .toWalletId(toWalletId)
            .taxWalletId(fromWalletId)
            .fromMoney(500L)
            .toMoney(500L)
            .taxMoney(0L)
            .note("")
            .confirmed(true)
            .countInTotal(true)
            .build()
        val uri = mResolver.insert(DataContentProvider.CONTENT_TRANSFERS, values)
        return if (uri != null) ContentUris.parseId(uri) else -1L
    }
}
