/*
 * Copyright (c) 2026.
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

package com.oriondev.moneywallet.ui.fragment.primary

import android.content.Intent
import android.database.MatrixCursor
import android.view.ContextThemeWrapper
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.TestDatabases
import com.oriondev.moneywallet.storage.wrapper.DebtHeaderCursor
import com.oriondev.moneywallet.ui.activity.BackupListActivity
import com.oriondev.moneywallet.ui.activity.NewEditTransactionActivity
import com.oriondev.moneywallet.ui.adapter.recycler.DebtCursorAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The debt card's own half of settling in full, which is the choice of which of its four buttons
 * a row shows and what the button a user taps sends to the editor.
 */
@RunWith(RobolectricTestRunner::class)
class DebtCardButtonsTest {

    companion object {
        private const val ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}"
        private const val TAG_FRAGMENT = "DebtCardButtonsTest::Fragment"

        private const val DEBT_ID = 42L
        private const val SECOND_DEBT_ID = 43L
        private const val THIRD_DEBT_ID = 44L

        /** The projection DebtListFragment loads, in its order, ending on the settled expression. */
        private val COLUMNS = arrayOf(
            Contract.Debt.ID,
            Contract.Debt.TYPE,
            Contract.Debt.ICON,
            Contract.Debt.DESCRIPTION,
            Contract.Debt.MONEY,
            Contract.Debt.PROGRESS,
            Contract.Debt.WALLET_CURRENCY,
            Contract.Debt.EXPIRATION_DATE,
            Contract.Debt.ARCHIVED,
            Contract.Debt.PLACE_ID,
            Contract.Debt.PLACE_NAME,
            DebtHeaderCursor.COLUMN_IS_SETTLED
        )

        private fun threeRows(
            debtType: Contract.DebtType,
            money: Long,
            progress: Long?,
            settled: Boolean
        ): MatrixCursor {
            val cursor = MatrixCursor(COLUMNS)
            for (id in longArrayOf(DEBT_ID, SECOND_DEBT_ID, THIRD_DEBT_ID)) {
                cursor.addRow(
                    arrayOf(
                        id, debtType.value, ICON, "Rent", money, progress, "EUR", null, 0,
                        null, null, if (settled) 1 else 0
                    )
                )
            }
            return cursor
        }

        private fun cardOf(adapter: DebtCursorAdapter, position: Int): View {
            val recyclerView = RecyclerView(
                ContextThemeWrapper(
                    ApplicationProvider.getApplicationContext(), R.style.MoneyWalletAppTheme
                )
            )
            recyclerView.layoutManager = LinearLayoutManager(recyclerView.context)
            recyclerView.adapter = adapter
            recyclerView.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY)
            )
            recyclerView.layout(0, 0, 1080, 2400)
            val card = recyclerView.layoutManager?.findViewByPosition(position)
            assertNotNull("nothing was laid out where the card belongs", card)
            return card ?: throw AssertionError("Card view was null")
        }

        private fun assertButtons(
            card: View,
            pay: Int,
            payInFull: Int,
            receive: Int,
            receiveInFull: Int
        ) {
            assertEquals("pay_button", pay, card.findViewById<View>(R.id.pay_button).visibility)
            assertEquals("pay_in_full_button", payInFull, card.findViewById<View>(R.id.pay_in_full_button).visibility)
            assertEquals("receive_button", receive, card.findViewById<View>(R.id.receive_button).visibility)
            assertEquals("receive_in_full_button", receiveInFull, card.findViewById<View>(R.id.receive_in_full_button).visibility)
        }

        private fun assertEditorOpened(activity: BackupListActivity, debtAction: Int) {
            val next: Intent? = shadowOf(activity).nextStartedActivity
            assertNotNull("the tap started nothing", next)
            val intent = next ?: return
            assertEquals(
                NewEditTransactionActivity::class.java.name,
                intent.component?.className
            )
            assertEquals(
                "TYPE", NewEditTransactionActivity.TYPE_DEBT,
                intent.getIntExtra(NewEditTransactionActivity.TYPE, -1)
            )
            assertEquals(
                "DEBT_ID", SECOND_DEBT_ID,
                intent.getLongExtra(NewEditTransactionActivity.DEBT_ID, -1L)
            )
            assertEquals(
                "DEBT_ACTION", debtAction,
                intent.getIntExtra(NewEditTransactionActivity.DEBT_ACTION, -1)
            )
        }
    }

    @Before
    fun setUp() {
        TestDatabases.useFreshDatabase(ApplicationProvider.getApplicationContext())
    }

    // ---- visibility -------------------------------------------------------------------------

    @Test
    fun aDebtStillBeingPaidOffOffersPayAndPayInFull() {
        onCard(Contract.DebtType.DEBT, 10000L, -2500L, false, 1) { _, card ->
            assertButtons(card, View.VISIBLE, View.VISIBLE, View.GONE, View.GONE)
        }
    }

    @Test
    fun aDebtPaidOffKeepsPayAndDropsPayInFull() {
        onCard(Contract.DebtType.DEBT, 10000L, -10000L, true, 1) { _, card ->
            assertButtons(card, View.VISIBLE, View.GONE, View.GONE, View.GONE)
        }
    }

    @Test
    fun aDebtPaidPastItsTargetDropsPayInFull() {
        onCard(Contract.DebtType.DEBT, 10000L, -12000L, true, 1) { _, card ->
            assertButtons(card, View.VISIBLE, View.GONE, View.GONE, View.GONE)
        }
    }

    @Test
    fun aCreditStillBeingRepaidOffersReceiveAndReceiveInFull() {
        onCard(Contract.DebtType.CREDIT, 10000L, 2500L, false, 1) { _, card ->
            assertButtons(card, View.GONE, View.GONE, View.VISIBLE, View.VISIBLE)
        }
    }

    @Test
    fun aCreditRepaidKeepsReceiveAndDropsReceiveInFull() {
        onCard(Contract.DebtType.CREDIT, 10000L, 10000L, true, 1) { _, card ->
            assertButtons(card, View.GONE, View.GONE, View.VISIBLE, View.GONE)
        }
    }

    @Test
    fun aDebtWithNoPaymentYetOffersPayAndPayInFull() {
        onCard(Contract.DebtType.DEBT, 10000L, null, false, 1) { _, card ->
            assertButtons(card, View.VISIBLE, View.VISIBLE, View.GONE, View.GONE)
        }
    }

    // ---- what each button sends the editor --------------------------------------------------

    @Test
    fun payInFullOnADebtAsksTheEditorToSettleInFull() {
        onCard(Contract.DebtType.DEBT, 10000L, -2500L, false, 2) { activity, card ->
            card.findViewById<View>(R.id.pay_in_full_button).performClick()
            assertEditorOpened(activity, NewEditTransactionActivity.DEBT_PAY_IN_FULL)
        }
    }

    @Test
    fun receiveInFullOnACreditAsksTheEditorToSettleInFull() {
        onCard(Contract.DebtType.CREDIT, 10000L, 2500L, false, 2) { activity, card ->
            card.findViewById<View>(R.id.receive_in_full_button).performClick()
            assertEditorOpened(activity, NewEditTransactionActivity.DEBT_RECEIVE_IN_FULL)
        }
    }

    @Test
    fun payOnADebtStillAsksForAPlainPayment() {
        onCard(Contract.DebtType.DEBT, 10000L, -2500L, false, 2) { activity, card ->
            card.findViewById<View>(R.id.pay_button).performClick()
            assertEditorOpened(activity, NewEditTransactionActivity.DEBT_PAY)
        }
    }

    @Test
    fun receiveOnACreditStillAsksForAPlainRepayment() {
        onCard(Contract.DebtType.CREDIT, 10000L, 2500L, false, 2) { activity, card ->
            card.findViewById<View>(R.id.receive_button).performClick()
            assertEditorOpened(activity, NewEditTransactionActivity.DEBT_RECEIVE)
        }
    }

    // ---- the card under a real listener -----------------------------------------------------

    private fun onCard(
        debtType: Contract.DebtType,
        money: Long,
        progress: Long?,
        settled: Boolean,
        position: Int,
        check: (activity: BackupListActivity, card: View) -> Unit
    ) {
        ActivityScenario.launch(BackupListActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = DebtListFragment.newInstance(debtType)
                activity.supportFragmentManager
                    .beginTransaction()
                    .add(android.R.id.content, fragment, TAG_FRAGMENT)
                    .commitNow()
                val adapter = DebtCursorAdapter(fragment)
                adapter.changeCursor(
                    DebtHeaderCursor(
                        threeRows(debtType, money, progress, settled), debtType
                    )
                )
                check(activity, cardOf(adapter, position))
            }
        }
    }
}
