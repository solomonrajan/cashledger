package com.oriondev.moneywallet.ui.activity

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.view.View
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.broadcast.RecurrenceBroadcastReceiver
import com.oriondev.moneywallet.model.Category
import com.oriondev.moneywallet.model.RecurrenceSetting
import com.oriondev.moneywallet.model.Wallet
import com.oriondev.moneywallet.picker.BudgetTypePicker
import com.oriondev.moneywallet.picker.CategoryPicker
import com.oriondev.moneywallet.picker.DateTimePicker
import com.oriondev.moneywallet.picker.MoneyPicker
import com.oriondev.moneywallet.picker.RecurrencePicker
import com.oriondev.moneywallet.picker.WalletPicker
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.SyncContentProvider
import com.oriondev.moneywallet.storage.database.TestDatabases
import com.oriondev.moneywallet.storage.database.TransferContentValuesBuilder
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.view.text.MaterialEditText
import com.oriondev.moneywallet.utils.CurrencyManager
import com.oriondev.moneywallet.utils.DateFormatter
import com.oriondev.moneywallet.utils.DateUtils
import com.oriondev.moneywallet.utils.IconLoader
import com.oriondev.moneywallet.utils.MoneyFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowDialog
import java.util.Calendar
import java.util.Collections
import java.util.Date

/**
 * Drives the real budget editor on the JVM, against the real content provider over a fresh
 * database, and reads back through the provider what it wrote. Every save is checked against the
 * PROGRESS column, which the provider sums over the transactions a budget actually covers, so a
 * transposed wallet set, a wrong period, a dropped category or a wrong type all show there even
 * when the row's own copies of those values read correctly. The rest of the cases cover what the
 * screen does with a repeating budget, whose dates and anchor are held or moved by rules the two
 * date fields on screen cannot show.
 */
@RunWith(RobolectricTestRunner::class)
class NewEditBudgetActivityTest {

    private lateinit var context: Context
    private lateinit var resolver: ContentResolver
    private var unusedWallet: Long = 0L
    private var walletA: Long = 0L
    private var walletB: Long = 0L
    private var walletD: Long = 0L
    private var food: Long = 0L
    private var snacks: Long = 0L
    private var rent: Long = 0L
    private var salary: Long = 0L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        TestDatabases.useFreshDatabase(context)
        resolver = context.contentResolver
        // the app schedules the roll alarm from its own onCreate, off whichever database the
        // shared helper was still holding, so a case that reads the alarm starts from nothing
        cancelRollAlarm()
        // one throwaway wallet takes id 1, so the first wallet row is never the current wallet
        unusedWallet = insertWallet("Unused", "EUR")
        walletA = insertWallet("Cash", "EUR")
        walletB = insertWallet("Bank", "EUR")
        walletD = insertWallet("Tokyo", "JPY")
        // nine system categories already hold ids 1 to 9 and eleven throwaway rows carry that on
        // to 20, so Rent is 21, Food 22, Snacks 23 and Salary 24, and none of them equals a wallet
        // id. Rent goes in first so that the order the ids run in and the order the names run in
        // disagree, and an assertion cannot hold by accident of the names
        for (i in 0 until 11) {
            insertCategory("Unused", Contract.CategoryType.EXPENSE, null)
        }
        rent = insertCategory("Rent", Contract.CategoryType.EXPENSE, null)
        food = insertCategory("Food", Contract.CategoryType.EXPENSE, null)
        snacks = insertCategory("Snacks", Contract.CategoryType.EXPENSE, food)
        salary = insertCategory("Salary", Contract.CategoryType.INCOME, null)
        // seven throwaway budgets take ids 1 to 7, so a budget a case makes starts at 8 and equals
        // no wallet id and none of the four category ids above. They sit on wallet A and never
        // repeat, so deleting a wallet in one case cannot take them out of the budget list
        for (i in 0 until 7) {
            insertBudget(
                Contract.BudgetType.EXPENSES, 100L, "EUR", longArrayOf(walletA), null,
                "2001-01-01", "2001-01-31", null, null, null
            )
        }
        // every amount is one digit times a distinct power of ten, so any subset sum names exactly
        // which rows were added up
        insertTransaction(walletA, "2019-04-05 12:00:00", Contract.Direction.EXPENSE, food, 1000L)
        insertTransaction(walletA, "2019-04-06 12:00:00", Contract.Direction.EXPENSE, snacks, 20000L)
        insertTransaction(walletA, "2019-04-07 12:00:00", Contract.Direction.EXPENSE, rent, 300000L)
        insertTransaction(walletA, "2019-04-08 12:00:00", Contract.Direction.INCOME, salary, 4000000L)
        insertTransaction(walletB, "2019-04-09 12:00:00", Contract.Direction.EXPENSE, food, 50000000L)
        insertTransaction(walletD, "2019-04-10 12:00:00", Contract.Direction.EXPENSE, food, 600000000L)
        insertTransaction(walletA, "2019-04-25 12:00:00", Contract.Direction.EXPENSE, rent, 7000000000L)
        insertTransaction(walletA, "2019-05-03 12:00:00", Contract.Direction.EXPENSE, food, 80000000000L)
        insertTransaction(walletA, secondsAgo(5), Contract.Direction.EXPENSE, food, 900000000000L)
        insertTransaction(walletA, secondsAgo(5), Contract.Direction.INCOME, salary, 1000000000000L)
        insertTransaction(walletB, secondsAgo(5), Contract.Direction.EXPENSE, rent, 20000000000000L)
        // the only rent row inside 10 to 20 April, which is the window one edit narrows down to
        insertTransaction(walletA, "2019-04-15 12:00:00", Contract.Direction.EXPENSE, rent, 300000000000000L)
        insertTransfer(walletA, walletB, 4000000000000000L, "2019-04-12 12:00:00")
    }

    @Test
    fun aNewExpensesBudgetOpensOnTodayAndSavesTheMonthAhead() {
        PreferenceManager.setCurrentWallet(context, walletA)
        val calendar = Calendar.getInstance()
        val today = DateUtils.getSQLDateString(calendar.time)
        calendar.add(Calendar.MONTH, 1)
        val monthAhead = DateUtils.getSQLDateString(calendar.time)
        val before = countBudgets()
        ActivityScenario.launchActivityForResult<NewEditBudgetActivity>(newItemIntent()).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(activity.getString(R.string.hint_expenses), fieldText(activity, R.id.type_edit_text))
                assertEquals(View.GONE, activity.findViewById<View>(R.id.category_edit_text).visibility)
                assertFalse(checkBox(activity, R.id.repeat_checkbox).isChecked)
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.repeat_checkbox).visibility)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.recurrence_edit_text).visibility)
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.start_date_edit_text).visibility)
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.end_date_edit_text).visibility)
                assertFalse(fieldText(activity, R.id.start_date_edit_text).isEmpty())
                assertFalse(fieldText(activity, R.id.end_date_edit_text).isEmpty())
                assertEquals(listOf(walletA), walletPickerIds(activity))
                assertEquals(CurrencyManager.getCurrency("EUR"), moneyPicker(activity).currentCurrency)
                assertEquals(
                    CurrencyManager.getCurrency("EUR").symbol,
                    headerText(activity, R.id.currency_text_view)
                )
                assertEquals(today, DateUtils.getSQLDateString(startDatePicker(activity).currentDateTime))
                assertEquals(monthAhead, DateUtils.getSQLDateString(endDatePicker(activity).currentDateTime))
                moneyPicker(activity).currentMoney = 500000L
                assertEquals(
                    formattedMoney("EUR", 500000L),
                    headerText(activity, R.id.money_text_view)
                )
                save(activity)
                assertTrue(activity.isFinishing)
            }
            assertEquals(Activity.RESULT_OK, scenario.result.resultCode)
        }
        assertEquals(before + 1, countBudgets())
        val budget = newestBudget()
        val row = budgetRow(budget)
        assertEquals(Contract.BudgetType.EXPENSES.value, row.getInt(row.getColumnIndexOrThrow(Contract.Budget.TYPE)))
        assertEquals(500000L, row.getLong(row.getColumnIndexOrThrow(Contract.Budget.MONEY)))
        assertEquals("EUR", row.getString(row.getColumnIndexOrThrow(Contract.Budget.CURRENCY)))
        assertTrue(row.isNull(row.getColumnIndexOrThrow(Contract.Budget.CATEGORY_ID)))
        assertEquals(today, row.getString(row.getColumnIndexOrThrow(Contract.Budget.START_DATE)))
        assertEquals(monthAhead, row.getString(row.getColumnIndexOrThrow(Contract.Budget.END_DATE)))
        assertTrue(row.isNull(row.getColumnIndexOrThrow(Contract.Budget.RULE)))
        assertTrue(row.isNull(row.getColumnIndexOrThrow(Contract.Budget.RULE_START)))
        // only the expense in wallet A dated seconds ago, not the income beside it and not the
        // rent expense in wallet B
        assertEquals(900000000000L, row.getLong(row.getColumnIndexOrThrow(Contract.Budget.PROGRESS)))
        row.close()
        assertEquals(listOf(walletA), walletIdsOf(budget))
        assertEquals(emptyList<Long>(), categoryIdsOf(budget))
    }

    @Test
    fun anIncomeBudgetLeavesOutATransferBetweenItsOwnWallets() {
        PreferenceManager.setCurrentWallet(context, walletA)
        val before = countBudgets()
        ActivityScenario.launch<NewEditBudgetActivity>(newItemIntent()).use { scenario ->
            scenario.onActivity { activity ->
                budgetTypePicker(activity).onBudgetTypeSelected(Contract.BudgetType.INCOMES)
                assertEquals(activity.getString(R.string.hint_incomes), fieldText(activity, R.id.type_edit_text))
                assertEquals(View.GONE, activity.findViewById<View>(R.id.category_edit_text).visibility)
                walletsPicker(activity).onWalletsSelected(
                    arrayOf(
                        wallet(walletA, "Cash", "EUR"),
                        wallet(walletB, "Bank", "EUR")
                    )
                )
                startDatePicker(activity).currentDateTime = date(APRIL_START)
                endDatePicker(activity).currentDateTime = date(APRIL_END)
                moneyPicker(activity).currentMoney = 700000L
                save(activity)
                assertTrue(activity.isFinishing)
            }
        }
        assertEquals(before + 1, countBudgets())
        val budget = newestBudget()
        val row = budgetRow(budget)
        assertEquals(Contract.BudgetType.INCOMES.value, row.getInt(row.getColumnIndexOrThrow(Contract.Budget.TYPE)))
        assertEquals(700000L, row.getLong(row.getColumnIndexOrThrow(Contract.Budget.MONEY)))
        assertTrue(row.isNull(row.getColumnIndexOrThrow(Contract.Budget.CATEGORY_ID)))
        assertEquals(APRIL_START, row.getString(row.getColumnIndexOrThrow(Contract.Budget.START_DATE)))
        assertEquals(APRIL_END, row.getString(row.getColumnIndexOrThrow(Contract.Budget.END_DATE)))
        // the one Salary income in wallet A, dated 8 April. The leg the transfer pays into
        // wallet B is left out because wallet A, the wallet it came from, is in the budget too,
        // and the income dated seconds ago is years past April
        assertEquals(4000000L, row.getLong(row.getColumnIndexOrThrow(Contract.Budget.PROGRESS)))
        row.close()
        assertEquals(listOf(walletA, walletB), walletIdsOf(budget))
        assertEquals(emptyList<Long>(), categoryIdsOf(budget))
        val otherSide = insertBudget(
            Contract.BudgetType.INCOMES, 700000L, "EUR",
            longArrayOf(walletB), null, APRIL_START, APRIL_END, null, null, null
        )
        // the same transfer read from a budget wallet A is not part of, where the leg it pays in
        // is the only income wallet B holds in April
        assertEquals(4000000000000000L, progressOf(otherSide))
    }

    @Test
    fun anIncomeBudgetEditedOffTheWalletThatPaysCountsTheTransferAgain() {
        val budget = insertBudget(
            Contract.BudgetType.INCOMES, 700000L, "EUR",
            longArrayOf(walletA, walletB), null, APRIL_START, APRIL_END, null, null, null
        )
        // the one Salary income in wallet A. The leg the transfer pays into wallet B is left out
        // while wallet A, the wallet it comes from, is in the budget too
        assertEquals(4000000L, progressOf(budget))
        assertEquals(4000000L, listedMoneyOf(budget))
        ActivityScenario.launch<NewEditBudgetActivity>(editIntent(budget)).use { scenario ->
            scenario.onActivity { activity ->
                walletsPicker(activity).onWalletsSelected(arrayOf(wallet(walletB, "Bank", "EUR")))
                save(activity)
                assertTrue(activity.isFinishing)
            }
        }
        assertEquals(listOf(walletB), walletIdsOf(budget))
        // the edit takes wallet A out of the budget, so the leg the transfer pays into wallet B
        // counts, and it is the only income wallet B holds in April
        assertEquals(4000000000000000L, progressOf(budget))
        assertEquals(4000000000000000L, listedMoneyOf(budget))
    }

    @Test
    fun anExpensesBudgetEditedOffTheWalletThatReceivesCountsTheTransferAgain() {
        val budget = insertBudget(
            Contract.BudgetType.EXPENSES, 900000L, "EUR",
            longArrayOf(walletA, walletB), null, APRIL_START, APRIL_END, null, null, null
        )
        assertEquals(aprilExpensesProgress(), progressOf(budget))
        assertEquals(aprilExpensesProgress(), listedMoneyOf(budget))
        ActivityScenario.launch<NewEditBudgetActivity>(editIntent(budget)).use { scenario ->
            scenario.onActivity { activity ->
                walletsPicker(activity).onWalletsSelected(arrayOf(wallet(walletA, "Cash", "EUR")))
                save(activity)
                assertTrue(activity.isFinishing)
            }
        }
        assertEquals(listOf(walletA), walletIdsOf(budget))
        assertEquals(aprilExpensesInWalletAWithTheTransfer(), progressOf(budget))
        assertEquals(aprilExpensesInWalletAWithTheTransfer(), listedMoneyOf(budget))
    }

    @Test
    fun aNewBudgetOnAYenWalletSavesInYen() {
        PreferenceManager.setCurrentWallet(context, walletD)
        val before = countBudgets()
        ActivityScenario.launch<NewEditBudgetActivity>(newItemIntent()).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(CurrencyManager.getCurrency("JPY"), moneyPicker(activity).currentCurrency)
                assertEquals(
                    CurrencyManager.getCurrency("JPY").symbol,
                    headerText(activity, R.id.currency_text_view)
                )
                moneyPicker(activity).currentMoney = 250000L
                assertEquals(
                    formattedMoney("JPY", 250000L),
                    headerText(activity, R.id.money_text_view)
                )
                save(activity)
                assertTrue(activity.isFinishing)
            }
        }
        assertEquals(before + 1, countBudgets())
        val row = budgetRow(newestBudget())
        assertEquals("JPY", row.getString(row.getColumnIndexOrThrow(Contract.Budget.CURRENCY)))
        assertEquals(250000L, row.getLong(row.getColumnIndexOrThrow(Contract.Budget.MONEY)))
        // the one row in wallet D is dated 2019-04-10, outside the month this budget opens on
        assertEquals(0L, row.getLong(row.getColumnIndexOrThrow(Contract.Budget.PROGRESS)))
        row.close()
    }

    @Test
    fun aNewBudgetOnTheTotalWalletOpensOnTheFirstWalletRow() {
        PreferenceManager.setCurrentWallet(context, PreferenceManager.TOTAL_WALLET_ID)
        val first = firstWalletRow()
        assertNotEquals(walletA, first)
        ActivityScenario.launch<NewEditBudgetActivity>(newItemIntent()).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(listOf(first), walletPickerIds(activity))
                assertEquals(listOf(unusedWallet), walletPickerIds(activity))
            }
        }
    }

    @Test
    fun aNewCategoryBudgetCarriesEveryCategoryAndEveryWallet() {
        PreferenceManager.setCurrentWallet(context, walletA)
        val before = countBudgets()
        ActivityScenario.launch<NewEditBudgetActivity>(newItemIntent()).use { scenario ->
            scenario.onActivity { activity ->
                budgetTypePicker(activity).onBudgetTypeSelected(Contract.BudgetType.CATEGORY)
                assertEquals(activity.getString(R.string.hint_category), fieldText(activity, R.id.type_edit_text))
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.category_edit_text).visibility)
                categoryPicker(activity).onCategoriesSelected(
                    arrayOf(category(food, "Food"), category(rent, "Rent"))
                )
                assertEquals("Food, Rent", fieldText(activity, R.id.category_edit_text))
                walletsPicker(activity).onWalletsSelected(
                    arrayOf(wallet(walletB, "Bank", "EUR"), wallet(walletA, "Cash", "EUR"))
                )
                assertEquals("Bank, Cash", fieldText(activity, R.id.wallets_edit_text))
                assertEquals(CurrencyManager.getCurrency("EUR"), moneyPicker(activity).currentCurrency)
                moneyPicker(activity).currentMoney = 900000L
                startDatePicker(activity).currentDateTime = date(APRIL_START)
                endDatePicker(activity).currentDateTime = date(APRIL_END)
                save(activity)
                assertTrue(activity.isFinishing)
            }
        }
        assertEquals(before + 1, countBudgets())
        val budget = newestBudget()
        val row = budgetRow(budget)
        assertEquals(Contract.BudgetType.CATEGORY.value, row.getInt(row.getColumnIndexOrThrow(Contract.Budget.TYPE)))
        assertEquals(900000L, row.getLong(row.getColumnIndexOrThrow(Contract.Budget.MONEY)))
        assertEquals(
            "<$rent>,<$food>",
            row.getString(row.getColumnIndexOrThrow(Contract.Budget.CATEGORY_IDS))
        )
        // the picker was handed Food first and the first of that order is what the row keeps,
        // while the column above comes back lowest id first, which is Rent
        assertEquals(food, row.getLong(row.getColumnIndexOrThrow(Contract.Budget.CATEGORY_ID)))
        assertEquals(APRIL_START, row.getString(row.getColumnIndexOrThrow(Contract.Budget.START_DATE)))
        assertEquals(APRIL_END, row.getString(row.getColumnIndexOrThrow(Contract.Budget.END_DATE)))
        assertEquals(aprilCategoryProgress(), row.getLong(row.getColumnIndexOrThrow(Contract.Budget.PROGRESS)))
        row.close()
        assertEquals(listOf(walletA, walletB), walletIdsOf(budget))
        assertEquals(listOf(rent, food), categoryIdsOf(budget))
    }

    @Test
    fun aNewBudgetWithNoWalletIsRefused() {
        PreferenceManager.setCurrentWallet(context, walletA)
        val before = countBudgets()
        ActivityScenario.launch<NewEditBudgetActivity>(newItemIntent()).use { scenario ->
            scenario.onActivity { activity ->
                walletsPicker(activity).onWalletsSelected(null)
                moneyPicker(activity).currentMoney = 500000L
                save(activity)
                assertFalse(activity.isFinishing)
                assertNull(ShadowDialog.getLatestDialog())
                assertFalse(field(activity, R.id.wallets_edit_text).validate())
                assertTrue(field(activity, R.id.start_date_edit_text).validate())
                assertTrue(field(activity, R.id.end_date_edit_text).validate())
                assertTrue(field(activity, R.id.type_edit_text).validate())
            }
        }
        assertEquals(before, countBudgets())
    }

    @Test
    fun aNewCategoryBudgetWithNoCategoryIsRefused() {
        PreferenceManager.setCurrentWallet(context, walletA)
        val before = countBudgets()
        ActivityScenario.launch<NewEditBudgetActivity>(newItemIntent()).use { scenario ->
            scenario.onActivity { activity ->
                budgetTypePicker(activity).onBudgetTypeSelected(Contract.BudgetType.CATEGORY)
                walletsPicker(activity).onWalletsSelected(arrayOf(wallet(walletA, "Cash", "EUR")))
                startDatePicker(activity).currentDateTime = date(APRIL_START)
                endDatePicker(activity).currentDateTime = date(APRIL_END)
                moneyPicker(activity).currentMoney = 500000L
                save(activity)
                assertFalse(activity.isFinishing)
                assertNull(ShadowDialog.getLatestDialog())
                assertFalse(field(activity, R.id.category_edit_text).validate())
                assertTrue(field(activity, R.id.wallets_edit_text).validate())
            }
        }
        assertEquals(before, countBudgets())
    }

    @Test
    fun aNewBudgetEndingBeforeItStartsIsRefused() {
        PreferenceManager.setCurrentWallet(context, walletA)
        val before = countBudgets()
        ActivityScenario.launch<NewEditBudgetActivity>(newItemIntent()).use { scenario ->
            scenario.onActivity { activity ->
                startDatePicker(activity).currentDateTime = date(APRIL_END)
                endDatePicker(activity).currentDateTime = date(APRIL_START)
                moneyPicker(activity).currentMoney = 500000L
                save(activity)
                assertFalse(activity.isFinishing)
                assertNull(ShadowDialog.getLatestDialog())
                assertFalse(field(activity, R.id.start_date_edit_text).validate())
                assertTrue(field(activity, R.id.end_date_edit_text).validate())
                assertTrue(field(activity, R.id.wallets_edit_text).validate())
            }
        }
        assertEquals(before, countBudgets())
    }

    @Test
    fun aNewBudgetOverTwoCurrenciesIsRefused() {
        PreferenceManager.setCurrentWallet(context, walletA)
        val before = countBudgets()
        ActivityScenario.launch<NewEditBudgetActivity>(newItemIntent()).use { scenario ->
            scenario.onActivity { activity ->
                walletsPicker(activity).onWalletsSelected(
                    arrayOf(
                        wallet(walletA, "Cash", "EUR"),
                        wallet(walletD, "Tokyo", "JPY")
                    )
                )
                assertEquals(CurrencyManager.getCurrency("EUR"), moneyPicker(activity).currentCurrency)
                assertEquals(
                    CurrencyManager.getCurrency("EUR").symbol,
                    headerText(activity, R.id.currency_text_view)
                )
                walletsPicker(activity).onWalletsSelected(
                    arrayOf(
                        wallet(walletD, "Tokyo", "JPY"),
                        wallet(walletA, "Cash", "EUR")
                    )
                )
                assertEquals(CurrencyManager.getCurrency("JPY"), moneyPicker(activity).currentCurrency)
                assertEquals(
                    CurrencyManager.getCurrency("JPY").symbol,
                    headerText(activity, R.id.currency_text_view)
                )
                moneyPicker(activity).currentMoney = 500000L
                save(activity)
                assertFalse(activity.isFinishing)
                assertNull(ShadowDialog.getLatestDialog())
                assertFalse(field(activity, R.id.wallets_edit_text).validate())
                assertTrue(field(activity, R.id.start_date_edit_text).validate())
                assertTrue(field(activity, R.id.end_date_edit_text).validate())
            }
        }
        assertEquals(before, countBudgets())
    }

    @Test
    fun aNewBudgetOnAWalletWithNoCurrencyIsRefused() {
        PreferenceManager.setCurrentWallet(context, walletA)
        val before = countBudgets()
        ActivityScenario.launch<NewEditBudgetActivity>(newItemIntent()).use { scenario ->
            scenario.onActivity { activity ->
                walletsPicker(activity).onWalletsSelected(
                    arrayOf(
                        Wallet(walletA, "Cash", IconLoader.parse(ICON), null, 0L, 0L)
                    )
                )
                moneyPicker(activity).currentMoney = 500000L
                save(activity)
                assertFalse(activity.isFinishing)
                assertNull(ShadowDialog.getLatestDialog())
                assertFalse(field(activity, R.id.wallets_edit_text).validate())
            }
        }
        assertEquals(before, countBudgets())
    }

    @Test
    fun aNewRepeatingBudgetLandsOnThePeriodTodayIsIn() {
        PreferenceManager.setCurrentWallet(context, walletA)
        val anchorCalendar = Calendar.getInstance()
        anchorCalendar.add(Calendar.MONTH, -3)
        anchorCalendar.set(Calendar.DAY_OF_MONTH, 15)
        val anchor = anchorCalendar.time
        val period = monthlyPeriod(15)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val before = countBudgets()
        ActivityScenario.launch<NewEditBudgetActivity>(newItemIntent()).use { scenario ->
            scenario.onActivity { activity ->
                checkBox(activity, R.id.repeat_checkbox).performClick()
                assertTrue(checkBox(activity, R.id.repeat_checkbox).isChecked)
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.recurrence_edit_text).visibility)
                assertFalse(fieldText(activity, R.id.recurrence_edit_text).isEmpty())
                assertEquals(View.GONE, activity.findViewById<View>(R.id.start_date_edit_text).visibility)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.end_date_edit_text).visibility)
                val monthly = monthlyOn(anchor)
                recurrencePicker(activity).onRecurrenceSettingChanged(monthly)
                assertEquals(
                    monthly.getUserReadableString(activity),
                    fieldText(activity, R.id.recurrence_edit_text)
                )
                moneyPicker(activity).currentMoney = 250000L
                assertTrue(Shadows.shadowOf(alarmManager).scheduledAlarms.isEmpty())
                save(activity)
                assertTrue(activity.isFinishing)
            }
        }
        assertEquals(before + 1, countBudgets())
        val budget = newestBudget()
        val row = budgetRow(budget)
        assertEquals(monthlyOn(anchor).rule, row.getString(row.getColumnIndexOrThrow(Contract.Budget.RULE)))
        assertEquals(
            DateUtils.getSQLDateString(anchor),
            row.getString(row.getColumnIndexOrThrow(Contract.Budget.RULE_START))
        )
        assertEquals(period[0], row.getString(row.getColumnIndexOrThrow(Contract.Budget.START_DATE)))
        assertEquals(period[1], row.getString(row.getColumnIndexOrThrow(Contract.Budget.END_DATE)))
        assertEquals(900000000000L, row.getLong(row.getColumnIndexOrThrow(Contract.Budget.PROGRESS)))
        row.close()
        assertEquals(listOf(walletA), walletIdsOf(budget))
        assertEquals(emptyList<Long>(), categoryIdsOf(budget))
        // the roll wakes the day after the period ends, at midnight
        assertEquals(1, Shadows.shadowOf(alarmManager).scheduledAlarms.size)
        assertEquals(
            DateUtils.addDays(DateUtils.getCalendar(date(period[1])), 1).time,
            Shadows.shadowOf(alarmManager).scheduledAlarms[0].triggerAtTime
        )
    }

    @Test
    fun aNewRepeatingBudgetIsAnchoredThroughTheStartField() {
        PreferenceManager.setCurrentWallet(context, walletA)
        val anchorCalendar = Calendar.getInstance()
        anchorCalendar.add(Calendar.MONTH, -3)
        anchorCalendar.add(Calendar.DAY_OF_MONTH, -20)
        val anchor = anchorCalendar.time
        val period = monthlyPeriod(anchorCalendar.get(Calendar.DAY_OF_MONTH))
        val before = countBudgets()
        ActivityScenario.launch<NewEditBudgetActivity>(newItemIntent()).use { scenario ->
            scenario.onActivity { activity ->
                startDatePicker(activity).currentDateTime = anchor
                checkBox(activity, R.id.repeat_checkbox).performClick()
                assertEquals(
                    monthlyOn(anchor).getUserReadableString(activity),
                    fieldText(activity, R.id.recurrence_edit_text)
                )
                moneyPicker(activity).currentMoney = 250000L
                save(activity)
                assertTrue(activity.isFinishing)
            }
        }
        assertEquals(before + 1, countBudgets())
        val row = budgetRow(newestBudget())
        assertEquals(monthlyOn(anchor).rule, row.getString(row.getColumnIndexOrThrow(Contract.Budget.RULE)))
        assertEquals(
            DateUtils.getSQLDateString(anchor),
            row.getString(row.getColumnIndexOrThrow(Contract.Budget.RULE_START))
        )
        assertEquals(period[0], row.getString(row.getColumnIndexOrThrow(Contract.Budget.START_DATE)))
        assertEquals(period[1], row.getString(row.getColumnIndexOrThrow(Contract.Budget.END_DATE)))
        assertEquals(900000000000L, row.getLong(row.getColumnIndexOrThrow(Contract.Budget.PROGRESS)))
        row.close()
    }

    @Test
    fun theDayChosenInTheDialogSurvivesTickingTheRepeatBox() {
        PreferenceManager.setCurrentWallet(context, walletA)
        val fieldCalendar = Calendar.getInstance()
        fieldCalendar.add(Calendar.DAY_OF_MONTH, -40)
        val dialogCalendar = Calendar.getInstance()
        dialogCalendar.add(Calendar.DAY_OF_MONTH, -25)
        val fieldDay = fieldCalendar.time
        val chosenDay = dialogCalendar.time
        val period = monthlyPeriod(dialogCalendar.get(Calendar.DAY_OF_MONTH))
        val before = countBudgets()
        ActivityScenario.launch<NewEditBudgetActivity>(newItemIntent()).use { scenario ->
            scenario.onActivity { activity ->
                startDatePicker(activity).currentDateTime = fieldDay
                recurrencePicker(activity).onRecurrenceSettingChanged(monthlyOn(chosenDay))
                checkBox(activity, R.id.repeat_checkbox).performClick()
                assertTrue(checkBox(activity, R.id.repeat_checkbox).isChecked)
                assertEquals(
                    monthlyOn(chosenDay).getUserReadableString(activity),
                    fieldText(activity, R.id.recurrence_edit_text)
                )
                moneyPicker(activity).currentMoney = 250000L
                save(activity)
                assertTrue(activity.isFinishing)
            }
        }
        assertEquals(before + 1, countBudgets())
        val row = budgetRow(newestBudget())
        assertEquals(monthlyOn(chosenDay).rule, row.getString(row.getColumnIndexOrThrow(Contract.Budget.RULE)))
        assertEquals(
            DateUtils.getSQLDateString(chosenDay),
            row.getString(row.getColumnIndexOrThrow(Contract.Budget.RULE_START))
        )
        assertEquals(period[0], row.getString(row.getColumnIndexOrThrow(Contract.Budget.START_DATE)))
        assertEquals(period[1], row.getString(row.getColumnIndexOrThrow(Contract.Budget.END_DATE)))
        assertEquals(900000000000L, row.getLong(row.getColumnIndexOrThrow(Contract.Budget.PROGRESS)))
        row.close()
    }

    @Test
    fun anUntouchedEditOfACategoryBudgetKeepsEveryField() {
        val budget = insertAprilCategoryBudget(longArrayOf(walletA, walletB))
        val before = countBudgets()
        val progress = progressOf(budget)
        assertEquals(aprilCategoryProgress(), progress)
        ActivityScenario.launch<NewEditBudgetActivity>(editIntent(budget)).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(activity.getString(R.string.hint_category), fieldText(activity, R.id.type_edit_text))
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.category_edit_text).visibility)
                assertEquals("Rent, Food", fieldText(activity, R.id.category_edit_text))
                assertEquals(listOf(rent, food), categoryPickerIds(activity))
                assertEquals(listOf(walletA, walletB), sorted(walletPickerIds(activity)))
                assertEquals("Cash, Bank", fieldText(activity, R.id.wallets_edit_text))
                assertEquals(900000L, moneyPicker(activity).currentMoney)
                assertEquals(CurrencyManager.getCurrency("EUR"), moneyPicker(activity).currentCurrency)
                assertFalse(checkBox(activity, R.id.repeat_checkbox).isChecked)
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.repeat_checkbox).visibility)
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.start_date_edit_text).visibility)
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.end_date_edit_text).visibility)
                assertEquals(
                    formattedDate(activity, date(APRIL_START)),
                    fieldText(activity, R.id.start_date_edit_text)
                )
                assertEquals(
                    formattedDate(activity, date(APRIL_END)),
                    fieldText(activity, R.id.end_date_edit_text)
                )
                assertEquals(APRIL_START, DateUtils.getSQLDateString(startDatePicker(activity).currentDateTime))
                assertEquals(APRIL_END, DateUtils.getSQLDateString(endDatePicker(activity).currentDateTime))
                // a budget carrying no anchor seeds the recurrence dialog off the day it starts
                assertEquals(
                    APRIL_START,
                    DateUtils.getSQLDateString(recurrencePicker(activity).currentSettings.startDate)
                )
                save(activity)
                assertTrue(activity.isFinishing)
            }
        }
        assertEquals(before, countBudgets())
        val row = budgetRow(budget)
        assertEquals(Contract.BudgetType.CATEGORY.value, row.getInt(row.getColumnIndexOrThrow(Contract.Budget.TYPE)))
        assertEquals(900000L, row.getLong(row.getColumnIndexOrThrow(Contract.Budget.MONEY)))
        assertEquals("EUR", row.getString(row.getColumnIndexOrThrow(Contract.Budget.CURRENCY)))
        assertEquals(rent, row.getLong(row.getColumnIndexOrThrow(Contract.Budget.CATEGORY_ID)))
        assertEquals(
            "<$rent>,<$food>",
            row.getString(row.getColumnIndexOrThrow(Contract.Budget.CATEGORY_IDS))
        )
        assertEquals(APRIL_START, row.getString(row.getColumnIndexOrThrow(Contract.Budget.START_DATE)))
        assertEquals(APRIL_END, row.getString(row.getColumnIndexOrThrow(Contract.Budget.END_DATE)))
        assertTrue(row.isNull(row.getColumnIndexOrThrow(Contract.Budget.RULE)))
        assertTrue(row.isNull(row.getColumnIndexOrThrow(Contract.Budget.RULE_START)))
        assertEquals(progress, row.getLong(row.getColumnIndexOrThrow(Contract.Budget.PROGRESS)))
        row.close()
        assertEquals(listOf(walletA, walletB), walletIdsOf(budget))
        assertEquals(listOf(rent, food), categoryIdsOf(budget))
    }

    @Test
    fun anEditWritesEveryChangedValue() {
        val budget = insertAprilCategoryBudget(longArrayOf(walletA, walletB))
        val before = countBudgets()
        val uuid = uuidOf(budget)
        ActivityScenario.launch<NewEditBudgetActivity>(editIntent(budget)).use { scenario ->
            scenario.onActivity { activity ->
                moneyPicker(activity).currentMoney = 1200000L
                walletsPicker(activity).onWalletsSelected(arrayOf(wallet(walletA, "Cash", "EUR")))
                categoryPicker(activity).onCategoriesSelected(arrayOf(category(rent, "Rent")))
                startDatePicker(activity).currentDateTime = date("2019-04-10")
                endDatePicker(activity).currentDateTime = date("2019-04-20")
                save(activity)
                assertTrue(activity.isFinishing)
            }
        }
        assertEquals(before, countBudgets())
        val row = budgetRow(budget)
        assertEquals(Contract.BudgetType.CATEGORY.value, row.getInt(row.getColumnIndexOrThrow(Contract.Budget.TYPE)))
        assertEquals(1200000L, row.getLong(row.getColumnIndexOrThrow(Contract.Budget.MONEY)))
        assertEquals("EUR", row.getString(row.getColumnIndexOrThrow(Contract.Budget.CURRENCY)))
        assertEquals(rent, row.getLong(row.getColumnIndexOrThrow(Contract.Budget.CATEGORY_ID)))
        assertEquals("<$rent>", row.getString(row.getColumnIndexOrThrow(Contract.Budget.CATEGORY_IDS)))
        assertEquals("2019-04-10", row.getString(row.getColumnIndexOrThrow(Contract.Budget.START_DATE)))
        assertEquals("2019-04-20", row.getString(row.getColumnIndexOrThrow(Contract.Budget.END_DATE)))
        // only the rent expense of 15 April, signed negative the way a category budget counts it
        assertEquals(-300000000000000L, row.getLong(row.getColumnIndexOrThrow(Contract.Budget.PROGRESS)))
        row.close()
        assertEquals(listOf(walletA), walletIdsOf(budget))
        assertEquals(listOf(rent), categoryIdsOf(budget))
        assertEquals(uuid, uuidOf(budget))
    }

    @Test
    fun anEditOntoAYenWalletRewritesTheCurrency() {
        val budget = insertBudget(
            Contract.BudgetType.EXPENSES, 900000L, "EUR",
            longArrayOf(walletA), null, APRIL_START, APRIL_END, null, null, null
        )
        val before = countBudgets()
        ActivityScenario.launch<NewEditBudgetActivity>(editIntent(budget)).use { scenario ->
            scenario.onActivity { activity ->
                walletsPicker(activity).onWalletsSelected(arrayOf(wallet(walletD, "Tokyo", "JPY")))
                assertEquals(CurrencyManager.getCurrency("JPY"), moneyPicker(activity).currentCurrency)
                assertEquals(
                    CurrencyManager.getCurrency("JPY").symbol,
                    headerText(activity, R.id.currency_text_view)
                )
                save(activity)
                assertTrue(activity.isFinishing)
            }
        }
        assertEquals(before, countBudgets())
        val row = budgetRow(budget)
        assertEquals("JPY", row.getString(row.getColumnIndexOrThrow(Contract.Budget.CURRENCY)))
        assertEquals(900000L, row.getLong(row.getColumnIndexOrThrow(Contract.Budget.MONEY)))
        // the one Food expense in wallet D, dated 10 April, and wallet D holds no other row
        assertEquals(600000000L, row.getLong(row.getColumnIndexOrThrow(Contract.Budget.PROGRESS)))
        row.close()
        assertEquals(listOf(walletD), walletIdsOf(budget))
    }

    @Test
    fun switchingTheTypeClearsTheCategories() {
        val budget = insertAprilCategoryBudget(longArrayOf(walletA, walletB))
        val before = countBudgets()
        ActivityScenario.launch<NewEditBudgetActivity>(editIntent(budget)).use { scenario ->
            scenario.onActivity { activity ->
                budgetTypePicker(activity).onBudgetTypeSelected(Contract.BudgetType.EXPENSES)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.category_edit_text).visibility)
                save(activity)
                assertTrue(activity.isFinishing)
            }
        }
        assertEquals(before, countBudgets())
        val row = budgetRow(budget)
        assertEquals(Contract.BudgetType.EXPENSES.value, row.getInt(row.getColumnIndexOrThrow(Contract.Budget.TYPE)))
        assertTrue(row.isNull(row.getColumnIndexOrThrow(Contract.Budget.CATEGORY_ID)))
        assertEquals(APRIL_START, row.getString(row.getColumnIndexOrThrow(Contract.Budget.START_DATE)))
        assertEquals(APRIL_END, row.getString(row.getColumnIndexOrThrow(Contract.Budget.END_DATE)))
        assertEquals(aprilExpensesProgress(), row.getLong(row.getColumnIndexOrThrow(Contract.Budget.PROGRESS)))
        row.close()
        assertEquals(listOf(walletA, walletB), walletIdsOf(budget))
        assertEquals(emptyList<Long>(), categoryIdsOf(budget))
    }

    @Test
    fun anUnchangedScheduleHoldsTheStoredDates() {
        val schedule = monthlyOn(date("2019-01-15"))
        val budget = insertBudget(
            Contract.BudgetType.EXPENSES, 300000L, "EUR",
            longArrayOf(walletA), null, "2019-03-15", "2019-04-14",
            schedule.rule, "2019-01-15", null
        )
        val before = countBudgets()
        ActivityScenario.launch<NewEditBudgetActivity>(editIntent(budget)).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(checkBox(activity, R.id.repeat_checkbox).isChecked)
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.repeat_checkbox).visibility)
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.recurrence_edit_text).visibility)
                assertEquals(
                    schedule.getUserReadableString(activity),
                    fieldText(activity, R.id.recurrence_edit_text)
                )
                assertEquals(View.GONE, activity.findViewById<View>(R.id.start_date_edit_text).visibility)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.end_date_edit_text).visibility)
                assertEquals("2019-03-15", DateUtils.getSQLDateString(startDatePicker(activity).currentDateTime))
                assertEquals(
                    "2019-01-15",
                    DateUtils.getSQLDateString(recurrencePicker(activity).currentSettings.startDate)
                )
                assertEquals(schedule.rule, recurrencePicker(activity).currentSettings.rule)
                save(activity)
                assertTrue(activity.isFinishing)
            }
        }
        assertEquals(before, countBudgets())
        assertRepeatingRow(budget, "2019-03-15", "2019-04-14", schedule.rule, "2019-01-15")
        ActivityScenario.launch<NewEditBudgetActivity>(editIntent(budget)).use { scenario ->
            scenario.onActivity { activity ->
                checkBox(activity, R.id.repeat_checkbox).performClick()
                assertFalse(checkBox(activity, R.id.repeat_checkbox).isChecked)
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.start_date_edit_text).visibility)
                startDatePicker(activity).currentDateTime = date("2019-03-01")
                checkBox(activity, R.id.repeat_checkbox).performClick()
                assertTrue(checkBox(activity, R.id.repeat_checkbox).isChecked)
                save(activity)
                assertTrue(activity.isFinishing)
            }
        }
        assertEquals(before, countBudgets())
        assertRepeatingRow(budget, "2019-03-15", "2019-04-14", schedule.rule, "2019-01-15")
        // the three early April expenses in wallet A plus the from leg of the transfer, which
        // counts here because wallet B is not part of this budget
        assertEquals(4000000000321000L, progressOf(budget))
    }

    @Test
    fun anEditOfARepeatingBudgetSchedulesTheRollAlarm() {
        val anchorCalendar = Calendar.getInstance()
        anchorCalendar.add(Calendar.MONTH, -3)
        anchorCalendar.set(Calendar.DAY_OF_MONTH, 15)
        val anchor = anchorCalendar.time
        val anchorDate = DateUtils.getSQLDateString(anchor)
        val period = monthlyPeriod(15)
        val budget = insertBudget(
            Contract.BudgetType.EXPENSES, 300000L, "EUR",
            longArrayOf(walletA), null, period[0], period[1], monthlyOn(anchor).rule,
            anchorDate, null
        )
        val yearly = yearlyOn(anchor)
        // a year from the stored anchor closes the period well after today, whatever day of the
        // month this runs on
        val yearlyEnd = yearlyEndAfter(anchorDate, period[0])
        val before = countBudgets()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        assertTrue(Shadows.shadowOf(alarmManager).scheduledAlarms.isEmpty())
        ActivityScenario.launch<NewEditBudgetActivity>(editIntent(budget)).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(checkBox(activity, R.id.repeat_checkbox).isChecked)
                recurrencePicker(activity).onRecurrenceSettingChanged(yearly)
                save(activity)
                assertTrue(activity.isFinishing)
            }
        }
        assertEquals(before, countBudgets())
        assertRepeatingRow(budget, period[0], yearlyEnd, yearly.rule, anchorDate)
        // the expense dated seconds ago is the only expense in wallet A inside that year
        assertEquals(900000000000L, progressOf(budget))
        // the roll wakes the day after the period the save wrote ends, at midnight
        assertEquals(1, Shadows.shadowOf(alarmManager).scheduledAlarms.size)
        assertEquals(
            DateUtils.addDays(DateUtils.getCalendar(date(yearlyEnd)), 1).time,
            Shadows.shadowOf(alarmManager).scheduledAlarms[0].triggerAtTime
        )
    }

    @Test
    fun untickingRepeatMovesTheRollAlarmOntoTheNextRepeatingBudget() {
        val anchorCalendar = Calendar.getInstance()
        anchorCalendar.add(Calendar.MONTH, -3)
        anchorCalendar.set(Calendar.DAY_OF_MONTH, 15)
        val anchor = anchorCalendar.time
        val anchorDate = DateUtils.getSQLDateString(anchor)
        val rule = monthlyOn(anchor).rule
        val period = monthlyPeriod(15)
        val periodPlusOne = periodYearsLater(period, 1)
        val periodPlusTwo = periodYearsLater(period, 2)
        val current = insertBudget(
            Contract.BudgetType.EXPENSES, 300000L, "EUR",
            longArrayOf(walletA), null, period[0], period[1], rule, anchorDate, null
        )
        // the same schedule a year further out, so it owns the earliest end only once the budget
        // above stops repeating
        val later = insertBudget(
            Contract.BudgetType.EXPENSES, 300000L, "EUR",
            longArrayOf(walletA), null, periodPlusOne[0], periodPlusOne[1], rule,
            periodPlusOne[0], null
        )
        // a third one on the same schedule two years out, so two candidates are left once the
        // budget above stops repeating and the alarm has to take the earlier of the two
        val latest = insertBudget(
            Contract.BudgetType.EXPENSES, 300000L, "EUR",
            longArrayOf(walletA), null, periodPlusTwo[0], periodPlusTwo[1], rule,
            periodPlusTwo[0], null
        )
        val before = countBudgets()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        assertTrue(Shadows.shadowOf(alarmManager).scheduledAlarms.isEmpty())
        // arm the roll the way the app itself does when it starts up, on the earliest of the
        // three ends, which is the current budget's own
        RecurrenceBroadcastReceiver.scheduleRecurrenceTask(context)
        assertEquals(1, Shadows.shadowOf(alarmManager).scheduledAlarms.size)
        assertEquals(
            DateUtils.addDays(DateUtils.getCalendar(date(period[1])), 1).time,
            Shadows.shadowOf(alarmManager).scheduledAlarms[0].triggerAtTime
        )
        ActivityScenario.launch<NewEditBudgetActivity>(editIntent(current)).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(checkBox(activity, R.id.repeat_checkbox).isChecked)
                checkBox(activity, R.id.repeat_checkbox).performClick()
                assertFalse(checkBox(activity, R.id.repeat_checkbox).isChecked)
                save(activity)
                assertTrue(activity.isFinishing)
            }
        }
        assertEquals(before, countBudgets())
        val row = budgetRow(current)
        assertTrue(row.isNull(row.getColumnIndexOrThrow(Contract.Budget.RULE)))
        assertTrue(row.isNull(row.getColumnIndexOrThrow(Contract.Budget.RULE_START)))
        assertEquals(period[0], row.getString(row.getColumnIndexOrThrow(Contract.Budget.START_DATE)))
        assertEquals(period[1], row.getString(row.getColumnIndexOrThrow(Contract.Budget.END_DATE)))
        row.close()
        assertRepeatingRow(later, periodPlusOne[0], periodPlusOne[1], rule, periodPlusOne[0])
        assertRepeatingRow(latest, periodPlusTwo[0], periodPlusTwo[1], rule, periodPlusTwo[0])
        // two budgets are left repeating, and the roll moves onto the earlier of their two ends
        assertEquals(1, Shadows.shadowOf(alarmManager).scheduledAlarms.size)
        assertEquals(
            DateUtils.addDays(DateUtils.getCalendar(date(periodPlusOne[1])), 1).time,
            Shadows.shadowOf(alarmManager).scheduledAlarms[0].triggerAtTime
        )
    }

    @Test
    fun untickingTheOnlyRepeatingBudgetClearsTheRollAlarm() {
        val anchorCalendar = Calendar.getInstance()
        anchorCalendar.add(Calendar.MONTH, -3)
        anchorCalendar.set(Calendar.DAY_OF_MONTH, 15)
        val anchor = anchorCalendar.time
        val anchorDate = DateUtils.getSQLDateString(anchor)
        val rule = monthlyOn(anchor).rule
        val period = monthlyPeriod(15)
        val budget = insertBudget(
            Contract.BudgetType.EXPENSES, 300000L, "EUR",
            longArrayOf(walletA), null, period[0], period[1], rule, anchorDate, null
        )
        val before = countBudgets()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        assertTrue(Shadows.shadowOf(alarmManager).scheduledAlarms.isEmpty())
        // arm the roll the way the app itself does when it starts up, off the row just inserted
        RecurrenceBroadcastReceiver.scheduleRecurrenceTask(context)
        assertEquals(1, Shadows.shadowOf(alarmManager).scheduledAlarms.size)
        assertEquals(
            DateUtils.addDays(DateUtils.getCalendar(date(period[1])), 1).time,
            Shadows.shadowOf(alarmManager).scheduledAlarms[0].triggerAtTime
        )
        ActivityScenario.launch<NewEditBudgetActivity>(editIntent(budget)).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(checkBox(activity, R.id.repeat_checkbox).isChecked)
                checkBox(activity, R.id.repeat_checkbox).performClick()
                assertFalse(checkBox(activity, R.id.repeat_checkbox).isChecked)
                save(activity)
                assertTrue(activity.isFinishing)
            }
        }
        assertEquals(before, countBudgets())
        // nothing repeats any more, and the receiver drops the alarm it holds before it looks,
        // so the roll armed above is gone and nothing takes its place
        assertTrue(Shadows.shadowOf(alarmManager).scheduledAlarms.isEmpty())
        val row = budgetRow(budget)
        assertTrue(row.isNull(row.getColumnIndexOrThrow(Contract.Budget.RULE)))
        assertTrue(row.isNull(row.getColumnIndexOrThrow(Contract.Budget.RULE_START)))
        assertEquals(period[0], row.getString(row.getColumnIndexOrThrow(Contract.Budget.START_DATE)))
        assertEquals(period[1], row.getString(row.getColumnIndexOrThrow(Contract.Budget.END_DATE)))
        row.close()
    }

    @Test
    fun reAnchoringKeepsTheStoredAnchorAndMakesOneOddPeriod() {
        val monthly = monthlyOn(date("2019-01-15"))
        val budget = insertBudget(
            Contract.BudgetType.EXPENSES, 300000L, "EUR",
            longArrayOf(walletA), null, "2019-03-15", "2019-04-14",
            monthly.rule, "2019-01-15", null
        )
        val before = countBudgets()
        val fortnightly = weeklyEvery(date("2019-01-20"), 2)
        val rebuilt = weeklyEvery(date("2019-01-15"), 2)
        ActivityScenario.launch<NewEditBudgetActivity>(editIntent(budget)).use { scenario ->
            scenario.onActivity { activity ->
                recurrencePicker(activity).onRecurrenceSettingChanged(fortnightly)
                assertEquals(
                    rebuilt.getUserReadableString(activity),
                    fieldText(activity, R.id.recurrence_edit_text)
                )
                save(activity)
                assertTrue(activity.isFinishing)
            }
        }
        assertEquals(before, countBudgets())
        val row = budgetRow(budget)
        assertEquals(rebuilt.rule, row.getString(row.getColumnIndexOrThrow(Contract.Budget.RULE)))
        assertEquals("2019-01-15", row.getString(row.getColumnIndexOrThrow(Contract.Budget.RULE_START)))
        assertEquals("2019-03-15", row.getString(row.getColumnIndexOrThrow(Contract.Budget.START_DATE)))
        assertEquals(
            weeklyEndAfter("2019-01-15", "2019-03-15", 14),
            row.getString(row.getColumnIndexOrThrow(Contract.Budget.END_DATE))
        )
        // nothing in wallet A falls in the eleven days this odd period covers
        assertEquals(0L, row.getLong(row.getColumnIndexOrThrow(Contract.Budget.PROGRESS)))
        row.close()
        assertEquals(listOf(walletA), walletIdsOf(budget))
    }

    @Test
    fun aYearlyScheduleIsRebuiltOnTheStoredAnchor() {
        val monthly = monthlyOn(date("2019-01-15"))
        val budget = insertBudget(
            Contract.BudgetType.EXPENSES, 300000L, "EUR",
            longArrayOf(walletA), null, "2019-03-15", "2019-04-14",
            monthly.rule, "2019-01-15", null
        )
        val before = countBudgets()
        val yearly = yearlyOn(date("2019-02-20"))
        val rebuilt = yearlyOn(date("2019-01-15"))
        ActivityScenario.launch<NewEditBudgetActivity>(editIntent(budget)).use { scenario ->
            scenario.onActivity { activity ->
                recurrencePicker(activity).onRecurrenceSettingChanged(yearly)
                assertEquals(
                    rebuilt.getUserReadableString(activity),
                    fieldText(activity, R.id.recurrence_edit_text)
                )
                save(activity)
                assertTrue(activity.isFinishing)
            }
        }
        assertEquals(before, countBudgets())
        val row = budgetRow(budget)
        assertEquals(rebuilt.rule, row.getString(row.getColumnIndexOrThrow(Contract.Budget.RULE)))
        assertEquals("2019-01-15", row.getString(row.getColumnIndexOrThrow(Contract.Budget.RULE_START)))
        assertEquals("2019-03-15", row.getString(row.getColumnIndexOrThrow(Contract.Budget.START_DATE)))
        assertEquals(
            yearlyEndAfter("2019-01-15", "2019-03-15"),
            row.getString(row.getColumnIndexOrThrow(Contract.Budget.END_DATE))
        )
        // every expense in wallet A from 15 March 2019 to 14 January 2020, plus the leg the
        // transfer pays out of it, which counts because wallet B is not in this budget
        assertEquals(4300087000321000L, row.getLong(row.getColumnIndexOrThrow(Contract.Budget.PROGRESS)))
        row.close()
        assertEquals(listOf(walletA), walletIdsOf(budget))
    }

    @Test
    fun aYearlyScheduleAnchoredToALeapDayKeepsTheLastDayFallback() {
        val monthly = monthlyOn(date("2020-02-29"))
        val budget = insertBudget(
            Contract.BudgetType.EXPENSES, 300000L, "EUR",
            longArrayOf(walletA), null, "2020-02-29", "2020-03-28",
            monthly.rule, "2020-02-29", null
        )
        val before = countBudgets()
        val rebuilt = yearlyOn(date("2020-02-29"))
        ActivityScenario.launch<NewEditBudgetActivity>(editIntent(budget)).use { scenario ->
            scenario.onActivity { activity ->
                recurrencePicker(activity).onRecurrenceSettingChanged(yearlyOn(date("2021-07-04")))
                assertEquals(
                    rebuilt.getUserReadableString(activity),
                    fieldText(activity, R.id.recurrence_edit_text)
                )
                save(activity)
                assertTrue(activity.isFinishing)
            }
        }
        assertEquals(before, countBudgets())
        val row = budgetRow(budget)
        assertEquals(rebuilt.rule, row.getString(row.getColumnIndexOrThrow(Contract.Budget.RULE)))
        assertEquals("2020-02-29", row.getString(row.getColumnIndexOrThrow(Contract.Budget.RULE_START)))
        assertEquals("2020-02-29", row.getString(row.getColumnIndexOrThrow(Contract.Budget.START_DATE)))
        // the next instance is 28 February 2021, the last day of a February with no 29th
        assertEquals("2021-02-27", row.getString(row.getColumnIndexOrThrow(Contract.Budget.END_DATE)))
        // no fixture row is dated in 2020 or 2021
        assertEquals(0L, row.getLong(row.getColumnIndexOrThrow(Contract.Budget.PROGRESS)))
        row.close()
        assertEquals(listOf(walletA), walletIdsOf(budget))
    }

    @Test
    fun turningRepeatOffWritesTheFieldsAndTurningItOnAnchorsToTheStartField() {
        val monthly = monthlyOn(date("2019-01-15"))
        val budget = insertBudget(
            Contract.BudgetType.EXPENSES, 300000L, "EUR",
            longArrayOf(walletA), null, "2019-03-15", "2019-04-14",
            monthly.rule, "2019-01-15", null
        )
        val before = countBudgets()
        ActivityScenario.launch<NewEditBudgetActivity>(editIntent(budget)).use { scenario ->
            scenario.onActivity { activity ->
                checkBox(activity, R.id.repeat_checkbox).performClick()
                startDatePicker(activity).currentDateTime = date("2019-05-01")
                endDatePicker(activity).currentDateTime = date("2019-05-31")
                save(activity)
                assertTrue(activity.isFinishing)
            }
        }
        assertEquals(before, countBudgets())
        var row = budgetRow(budget)
        assertEquals("2019-05-01", row.getString(row.getColumnIndexOrThrow(Contract.Budget.START_DATE)))
        assertEquals("2019-05-31", row.getString(row.getColumnIndexOrThrow(Contract.Budget.END_DATE)))
        assertTrue(row.isNull(row.getColumnIndexOrThrow(Contract.Budget.RULE)))
        assertTrue(row.isNull(row.getColumnIndexOrThrow(Contract.Budget.RULE_START)))
        // the only expense in wallet A inside May 2019
        assertEquals(80000000000L, row.getLong(row.getColumnIndexOrThrow(Contract.Budget.PROGRESS)))
        row.close()
        val fromTheDialog = monthlyOn(date("2019-06-20"))
        ActivityScenario.launch<NewEditBudgetActivity>(editIntent(budget)).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse(checkBox(activity, R.id.repeat_checkbox).isChecked)
                startDatePicker(activity).currentDateTime = date("2019-05-10")
                recurrencePicker(activity).onRecurrenceSettingChanged(fromTheDialog)
                assertEquals(
                    monthlyOn(date("2019-05-10")).getUserReadableString(activity),
                    fieldText(activity, R.id.recurrence_edit_text)
                )
                checkBox(activity, R.id.repeat_checkbox).performClick()
                assertTrue(checkBox(activity, R.id.repeat_checkbox).isChecked)
                save(activity)
                assertTrue(activity.isFinishing)
            }
        }
        assertEquals(before, countBudgets())
        row = budgetRow(budget)
        assertEquals("2019-05-10", row.getString(row.getColumnIndexOrThrow(Contract.Budget.START_DATE)))
        assertEquals("2019-06-09", row.getString(row.getColumnIndexOrThrow(Contract.Budget.END_DATE)))
        assertEquals(
            monthlyOn(date("2019-05-10")).rule,
            row.getString(row.getColumnIndexOrThrow(Contract.Budget.RULE))
        )
        assertEquals("2019-05-10", row.getString(row.getColumnIndexOrThrow(Contract.Budget.RULE_START)))
        // nothing in wallet A between 10 May and 9 June 2019, the 3 May row is outside now
        assertEquals(0L, row.getLong(row.getColumnIndexOrThrow(Contract.Budget.PROGRESS)))
        row.close()
    }

    @Test
    fun aPeriodTheChainHasMovedPastHidesTheRepeatBox() {
        val root = insertBudget(
            Contract.BudgetType.EXPENSES, 300000L, "EUR",
            longArrayOf(walletA), null, "2019-03-15", "2019-04-14", null, "2019-01-15", null
        )
        val live = insertRolledPeriod(root, "2019-04-15", "2019-05-14")
        assertEquals(uuidOf(root) + ":2019-04-15", uuidOf(live))
        val before = countBudgets()
        ActivityScenario.launch<NewEditBudgetActivity>(editIntent(root)).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(View.GONE, activity.findViewById<View>(R.id.repeat_checkbox).visibility)
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.start_date_edit_text).visibility)
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.end_date_edit_text).visibility)
                moneyPicker(activity).currentMoney = 350000L
                save(activity)
                assertTrue(activity.isFinishing)
                assertNull(ShadowDialog.getLatestDialog())
            }
        }
        assertEquals(before, countBudgets())
        val row = budgetRow(root)
        assertEquals(350000L, row.getLong(row.getColumnIndexOrThrow(Contract.Budget.MONEY)))
        assertTrue(row.isNull(row.getColumnIndexOrThrow(Contract.Budget.RULE)))
        assertEquals("2019-01-15", row.getString(row.getColumnIndexOrThrow(Contract.Budget.RULE_START)))
        assertEquals("2019-03-15", row.getString(row.getColumnIndexOrThrow(Contract.Budget.START_DATE)))
        assertEquals("2019-04-14", row.getString(row.getColumnIndexOrThrow(Contract.Budget.END_DATE)))
        row.close()
        // the save above finished the first launch, so the rotation runs on a second one
        ActivityScenario.launch<NewEditBudgetActivity>(editIntent(root)).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(View.GONE, activity.findViewById<View>(R.id.repeat_checkbox).visibility)
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertEquals(View.GONE, activity.findViewById<View>(R.id.repeat_checkbox).visibility)
            }
        }
        ActivityScenario.launch<NewEditBudgetActivity>(editIntent(live)).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.repeat_checkbox).visibility)
                assertTrue(checkBox(activity, R.id.repeat_checkbox).isChecked)
            }
        }
    }

    @Test
    fun aClosedPeriodWithASpentRuleDropsTheRuleAndKeepsItsAnchor() {
        val root = insertBudget(
            Contract.BudgetType.EXPENSES, 300000L, "EUR",
            longArrayOf(walletA), null, "2019-03-15", "2019-04-14",
            monthlyUntil(date("2019-01-15"), date("2019-02-01")).rule, "2019-01-15", null
        )
        insertRolledPeriod(root, "2019-04-15", "2019-05-14")
        val before = countBudgets()
        ActivityScenario.launch<NewEditBudgetActivity>(editIntent(root)).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(View.GONE, activity.findViewById<View>(R.id.repeat_checkbox).visibility)
                moneyPicker(activity).currentMoney = 350000L
                save(activity)
                assertTrue(activity.isFinishing)
                assertNull(ShadowDialog.getLatestDialog())
            }
        }
        assertEquals(before, countBudgets())
        val row = budgetRow(root)
        assertEquals(350000L, row.getLong(row.getColumnIndexOrThrow(Contract.Budget.MONEY)))
        assertTrue(row.isNull(row.getColumnIndexOrThrow(Contract.Budget.RULE)))
        assertEquals("2019-01-15", row.getString(row.getColumnIndexOrThrow(Contract.Budget.RULE_START)))
        assertEquals("2019-03-15", row.getString(row.getColumnIndexOrThrow(Contract.Budget.START_DATE)))
        assertEquals("2019-04-14", row.getString(row.getColumnIndexOrThrow(Contract.Budget.END_DATE)))
        row.close()
    }

    @Test
    fun aRepeatingPeriodTheChainHasMovedPastKeepsTheBoxAndRefusesTheSave() {
        val rule = monthlyOn(date("2019-01-15")).rule
        val root = insertBudget(
            Contract.BudgetType.EXPENSES, 300000L, "EUR",
            longArrayOf(walletA), null, "2019-03-15", "2019-04-14", rule, "2019-01-15", null
        )
        val live = insertRolledPeriod(root, "2019-04-15", "2019-05-14")
        assertEquals(uuidOf(root) + ":2019-04-15", uuidOf(live))
        val before = countBudgets()
        ActivityScenario.launch<NewEditBudgetActivity>(editIntent(root)).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.repeat_checkbox).visibility)
                assertTrue(checkBox(activity, R.id.repeat_checkbox).isChecked)
                save(activity)
                assertFalse(activity.isFinishing)
                val dialog = ShadowDialog.getLatestDialog() as AlertDialog
                val message = dialog.findViewById<TextView>(android.R.id.message)
                assertEquals(
                    activity.getString(R.string.message_budget_period_superseded),
                    message?.text?.toString()
                )
            }
        }
        assertEquals(before, countBudgets())
        assertRepeatingRow(root, "2019-03-15", "2019-04-14", rule, "2019-01-15")
    }

    @Test
    fun aScheduleThatHasRunOutIsRefused() {
        PreferenceManager.setCurrentWallet(context, walletA)
        val before = countBudgets()
        ActivityScenario.launch<NewEditBudgetActivity>(newItemIntent()).use { scenario ->
            scenario.onActivity { activity ->
                recurrencePicker(activity).onRecurrenceSettingChanged(
                    monthlyUntil(date("2019-01-15"), date("2019-02-01"))
                )
                checkBox(activity, R.id.repeat_checkbox).performClick()
                moneyPicker(activity).currentMoney = 250000L
                save(activity)
                assertFalse(activity.isFinishing)
                assertNull(ShadowDialog.getLatestDialog())
                assertFalse(field(activity, R.id.recurrence_edit_text).validate())
            }
        }
        assertEquals(before, countBudgets())
    }

    @Test
    fun aStoredScheduleThatHasRunOutOpensOnTheDateFields() {
        val budget = insertBudget(
            Contract.BudgetType.EXPENSES, 300000L, "EUR",
            longArrayOf(walletA), null, "2019-03-15", "2019-04-14",
            monthlyUntil(date("2019-01-15"), date("2019-02-01")).rule, "2019-01-15", null
        )
        ActivityScenario.launch<NewEditBudgetActivity>(editIntent(budget)).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse(checkBox(activity, R.id.repeat_checkbox).isChecked)
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.repeat_checkbox).visibility)
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.start_date_edit_text).visibility)
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.end_date_edit_text).visibility)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.recurrence_edit_text).visibility)
            }
        }
    }

    @Test
    fun aChainIsOrderedByItsNamesAndNotByItsDates() {
        val root = insertBudget(
            Contract.BudgetType.EXPENSES, 300000L, "EUR",
            longArrayOf(walletA), null, "2019-02-15", "2019-03-14", null, "2019-01-15", null
        )
        val earlier = insertRolledPeriod(root, "2019-03-15", "2019-04-14")
        val live = insertRolledPeriod(earlier, "2019-04-15", "2019-05-14")
        assertEquals(uuidOf(root) + ":2019-03-15", uuidOf(earlier))
        assertEquals(uuidOf(root) + ":2019-04-15", uuidOf(live))
        // the earlier period is moved past the live one, so the two run the other way round by
        // date while the names they were opened under are untouched
        val moved = ContentValues().apply {
            put(Contract.Budget.TYPE, Contract.BudgetType.EXPENSES.value)
            put(Contract.Budget.MONEY, 300000L)
            put(Contract.Budget.WALLET_IDS, idList(longArrayOf(walletA)))
            put(Contract.Budget.START_DATE, "2030-01-01")
            put(Contract.Budget.END_DATE, "2030-01-31")
        }
        resolver.update(
            ContentUris.withAppendedId(DataContentProvider.CONTENT_BUDGETS, earlier),
            moved, null, null
        )
        val before = countBudgets()
        val liveUuid = uuidOf(live)
        ActivityScenario.launch<NewEditBudgetActivity>(editIntent(live)).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.repeat_checkbox).visibility)
                assertTrue(checkBox(activity, R.id.repeat_checkbox).isChecked)
                save(activity)
                assertTrue(activity.isFinishing)
                assertNull(ShadowDialog.getLatestDialog())
            }
        }
        assertEquals(before, countBudgets())
        assertEquals(liveUuid, uuidOf(live))
        assertRepeatingRow(
            live, "2019-04-15", "2019-05-14",
            monthlyOn(date("2019-01-15")).rule, "2019-01-15"
        )
        // the three expenses in wallet A between 15 April and 14 May 2019
        assertEquals(300087000000000L, progressOf(live))
    }

    @Test
    fun aRollOpeningALaterPeriodRefusesTheSave() {
        val root = insertBudget(
            Contract.BudgetType.EXPENSES, 300000L, "EUR",
            longArrayOf(walletA), null, "2019-03-15", "2019-04-14", null, "2019-01-15", null
        )
        val live = insertRolledPeriod(root, "2019-04-15", "2019-05-14")
        ActivityScenario.launch<NewEditBudgetActivity>(editIntent(live)).use { scenario ->
            val later = insertRolledPeriod(live, "2019-05-15", "2019-06-14")
            assertEquals(uuidOf(root) + ":2019-05-15", uuidOf(later))
            val before = countBudgets()
            scenario.onActivity { activity ->
                save(activity)
                assertFalse(activity.isFinishing)
                val dialog = ShadowDialog.getLatestDialog() as AlertDialog
                val message = dialog.findViewById<TextView>(android.R.id.message)
                assertEquals(
                    activity.getString(R.string.message_budget_period_superseded),
                    message?.text?.toString()
                )
            }
            assertEquals(before, countBudgets())
        }
        assertRepeatingRow(
            live, "2019-04-15", "2019-05-14",
            monthlyOn(date("2019-01-15")).rule, "2019-01-15"
        )
        assertEquals(300087000000000L, progressOf(live))
    }

    @Test
    fun aRollOpeningALaterPeriodThenTurningRepeatOffKeepsTheAnchor() {
        val root = insertBudget(
            Contract.BudgetType.EXPENSES, 300000L, "EUR",
            longArrayOf(walletA), null, "2019-03-15", "2019-04-14", null, "2019-01-15", null
        )
        val live = insertRolledPeriod(root, "2019-04-15", "2019-05-14")
        val before: Int
        ActivityScenario.launch<NewEditBudgetActivity>(editIntent(live)).use { scenario ->
            val later = insertRolledPeriod(live, "2019-05-15", "2019-06-14")
            assertEquals(uuidOf(root) + ":2019-05-15", uuidOf(later))
            before = countBudgets()
            scenario.onActivity { activity ->
                checkBox(activity, R.id.repeat_checkbox).performClick()
                assertFalse(checkBox(activity, R.id.repeat_checkbox).isChecked)
                save(activity)
                assertTrue(activity.isFinishing)
                assertNull(ShadowDialog.getLatestDialog())
            }
        }
        assertEquals(before, countBudgets())
        val row = budgetRow(live)
        assertTrue(row.isNull(row.getColumnIndexOrThrow(Contract.Budget.RULE)))
        assertEquals("2019-01-15", row.getString(row.getColumnIndexOrThrow(Contract.Budget.RULE_START)))
        assertEquals("2019-04-15", row.getString(row.getColumnIndexOrThrow(Contract.Budget.START_DATE)))
        assertEquals("2019-05-14", row.getString(row.getColumnIndexOrThrow(Contract.Budget.END_DATE)))
        row.close()
    }

    @Test
    fun theLivePeriodOfAChainTurnedOffDropsTheAnchor() {
        val root = insertBudget(
            Contract.BudgetType.EXPENSES, 300000L, "EUR",
            longArrayOf(walletA), null, "2019-03-15", "2019-04-14", null, "2019-01-15", null
        )
        val live = insertRolledPeriod(root, "2019-04-15", "2019-05-14")
        val before = countBudgets()
        ActivityScenario.launch<NewEditBudgetActivity>(editIntent(live)).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(checkBox(activity, R.id.repeat_checkbox).isChecked)
                checkBox(activity, R.id.repeat_checkbox).performClick()
                assertFalse(checkBox(activity, R.id.repeat_checkbox).isChecked)
                save(activity)
                assertTrue(activity.isFinishing)
                assertNull(ShadowDialog.getLatestDialog())
            }
        }
        assertEquals(before, countBudgets())
        val row = budgetRow(live)
        assertTrue(row.isNull(row.getColumnIndexOrThrow(Contract.Budget.RULE)))
        assertTrue(row.isNull(row.getColumnIndexOrThrow(Contract.Budget.RULE_START)))
        assertEquals("2019-04-15", row.getString(row.getColumnIndexOrThrow(Contract.Budget.START_DATE)))
        assertEquals("2019-05-14", row.getString(row.getColumnIndexOrThrow(Contract.Budget.END_DATE)))
        row.close()
    }

    @Test
    fun aWalletDeletedWhileTheEditorIsOpenReachesTheRefusalDialog() {
        val budget = insertAprilCategoryBudget(longArrayOf(walletA, unusedWallet))
        val before = countBudgets()
        ActivityScenario.launch<NewEditBudgetActivity>(editIntent(budget)).use { scenario ->
            resolver.delete(
                ContentUris.withAppendedId(DataContentProvider.CONTENT_WALLETS, unusedWallet),
                null, null
            )
            scenario.onActivity { activity ->
                save(activity)
                val dialog = ShadowDialog.getLatestDialog() as AlertDialog
                val message = dialog.findViewById<TextView>(android.R.id.message)
                assertEquals(
                    activity.getString(R.string.error_input_missing_multiple_wallets),
                    message?.text?.toString()
                )
                assertFalse(activity.isFinishing)
            }
        }
        assertEquals(before, countBudgets())
        assertEquals(listOf(walletA), walletIdsOf(budget))
    }

    @Test
    fun aNewBudgetOverAWalletMovedOntoAnotherCurrencyReachesTheRefusalDialog() {
        PreferenceManager.setCurrentWallet(context, walletA)
        val before = countBudgets()
        ActivityScenario.launch<NewEditBudgetActivity>(newItemIntent()).use { scenario ->
            scenario.onActivity { activity ->
                walletsPicker(activity).onWalletsSelected(
                    arrayOf(
                        wallet(walletA, "Cash", "EUR"),
                        wallet(walletB, "Bank", "EUR")
                    )
                )
                moneyPicker(activity).currentMoney = 500000L
                startDatePicker(activity).currentDateTime = date(APRIL_START)
                endDatePicker(activity).currentDateTime = date(APRIL_END)
                val yen = ContentValues().apply {
                    put(Contract.Wallet.CURRENCY, "JPY")
                }
                resolver.update(
                    ContentUris.withAppendedId(DataContentProvider.CONTENT_WALLETS, walletB),
                    yen, null, null
                )
                save(activity)
                val dialog = ShadowDialog.getLatestDialog() as AlertDialog
                val message = dialog.findViewById<TextView>(android.R.id.message)
                assertEquals(
                    activity.getString(R.string.error_input_invalid_multiple_wallets),
                    message?.text?.toString()
                )
            }
        }
        assertEquals(before, countBudgets())
    }

    @Test
    fun aWalletMovedOntoAnotherCurrencyWhileTheEditorIsOpenReachesTheRefusalDialog() {
        val budget = insertBudget(
            Contract.BudgetType.EXPENSES, 900000L, "EUR",
            longArrayOf(walletA, walletB), null, APRIL_START, APRIL_END, null, null, null
        )
        val before = countBudgets()
        ActivityScenario.launch<NewEditBudgetActivity>(editIntent(budget)).use { scenario ->
            val yen = ContentValues().apply {
                put(Contract.Wallet.CURRENCY, "JPY")
            }
            resolver.update(
                ContentUris.withAppendedId(DataContentProvider.CONTENT_WALLETS, walletB),
                yen, null, null
            )
            scenario.onActivity { activity ->
                moneyPicker(activity).currentMoney = 999999L
                save(activity)
                val dialog = ShadowDialog.getLatestDialog() as AlertDialog
                val message = dialog.findViewById<TextView>(android.R.id.message)
                assertEquals(
                    activity.getString(R.string.error_input_invalid_multiple_wallets),
                    message?.text?.toString()
                )
            }
        }
        assertEquals(before, countBudgets())
        val row = budgetRow(budget)
        assertEquals(900000L, row.getLong(row.getColumnIndexOrThrow(Contract.Budget.MONEY)))
        assertEquals(APRIL_START, row.getString(row.getColumnIndexOrThrow(Contract.Budget.START_DATE)))
        assertEquals(APRIL_END, row.getString(row.getColumnIndexOrThrow(Contract.Budget.END_DATE)))
        row.close()
        assertEquals(listOf(walletA, walletB), walletIdsOf(budget))
    }

    @Test
    fun theOnlyWalletMovedOntoAnotherCurrencyWhileTheEditorIsOpenSavesInThatCurrency() {
        val budget = insertBudget(
            Contract.BudgetType.EXPENSES, 900000L, "EUR",
            longArrayOf(walletA), null, APRIL_START, APRIL_END, null, null, null
        )
        val before = countBudgets()
        ActivityScenario.launch<NewEditBudgetActivity>(editIntent(budget)).use { scenario ->
            val yen = ContentValues().apply {
                put(Contract.Wallet.CURRENCY, "JPY")
            }
            resolver.update(
                ContentUris.withAppendedId(DataContentProvider.CONTENT_WALLETS, walletA),
                yen, null, null
            )
            scenario.onActivity { activity ->
                moneyPicker(activity).currentMoney = 999999L
                save(activity)
                assertTrue(activity.isFinishing)
                assertNull(ShadowDialog.getLatestDialog())
            }
        }
        assertEquals(before, countBudgets())
        val row = budgetRow(budget)
        assertEquals(999999L, row.getLong(row.getColumnIndexOrThrow(Contract.Budget.MONEY)))
        assertEquals("JPY", row.getString(row.getColumnIndexOrThrow(Contract.Budget.CURRENCY)))
        assertEquals(APRIL_START, row.getString(row.getColumnIndexOrThrow(Contract.Budget.START_DATE)))
        assertEquals(APRIL_END, row.getString(row.getColumnIndexOrThrow(Contract.Budget.END_DATE)))
        row.close()
        assertEquals(listOf(walletA), walletIdsOf(budget))
    }

    @Test
    fun aNewBudgetOverTheOnlyWalletMovedOntoAnotherCurrencySavesInThatCurrency() {
        PreferenceManager.setCurrentWallet(context, walletA)
        val before = countBudgets()
        ActivityScenario.launch<NewEditBudgetActivity>(newItemIntent()).use { scenario ->
            scenario.onActivity { activity ->
                moneyPicker(activity).currentMoney = 500000L
                startDatePicker(activity).currentDateTime = date(APRIL_START)
                endDatePicker(activity).currentDateTime = date(APRIL_END)
                val yen = ContentValues().apply {
                    put(Contract.Wallet.CURRENCY, "JPY")
                }
                resolver.update(
                    ContentUris.withAppendedId(DataContentProvider.CONTENT_WALLETS, walletA),
                    yen, null, null
                )
                save(activity)
                assertTrue(activity.isFinishing)
                assertNull(ShadowDialog.getLatestDialog())
            }
        }
        assertEquals(before + 1, countBudgets())
        val budget = newestBudget()
        val row = budgetRow(budget)
        assertEquals(500000L, row.getLong(row.getColumnIndexOrThrow(Contract.Budget.MONEY)))
        assertEquals("JPY", row.getString(row.getColumnIndexOrThrow(Contract.Budget.CURRENCY)))
        assertEquals(APRIL_START, row.getString(row.getColumnIndexOrThrow(Contract.Budget.START_DATE)))
        assertEquals(APRIL_END, row.getString(row.getColumnIndexOrThrow(Contract.Budget.END_DATE)))
        row.close()
        assertEquals(listOf(walletA), walletIdsOf(budget))
    }

    @Test
    fun aCurrencySentWithABudgetIsReplacedByItsWalletsCurrency() {
        val budget = insertBudget(
            Contract.BudgetType.EXPENSES, 900000L, "JPY",
            longArrayOf(walletA), null, APRIL_START, APRIL_END, null, null, null
        )
        var row = budgetRow(budget)
        assertEquals("EUR", row.getString(row.getColumnIndexOrThrow(Contract.Budget.CURRENCY)))
        row.close()
        val sent = ContentValues().apply {
            put(Contract.Budget.TYPE, Contract.BudgetType.EXPENSES.value)
            put(Contract.Budget.MONEY, 900000L)
            put(Contract.Budget.CURRENCY, "JPY")
            put(Contract.Budget.WALLET_IDS, idList(longArrayOf(walletA)))
            put(Contract.Budget.START_DATE, APRIL_START)
            put(Contract.Budget.END_DATE, APRIL_END)
        }
        resolver.update(
            ContentUris.withAppendedId(DataContentProvider.CONTENT_BUDGETS, budget),
            sent, null, null
        )
        row = budgetRow(budget)
        assertEquals("EUR", row.getString(row.getColumnIndexOrThrow(Contract.Budget.CURRENCY)))
        row.close()
    }

    @Test
    fun aRecreateHoldsTypedStateOnANewBudget() {
        PreferenceManager.setCurrentWallet(context, walletA)
        ActivityScenario.launch<NewEditBudgetActivity>(newItemIntent()).use { scenario ->
            val recurrenceText = arrayOfNulls<String>(1)
            scenario.onActivity { activity ->
                budgetTypePicker(activity).onBudgetTypeSelected(Contract.BudgetType.CATEGORY)
                categoryPicker(activity).onCategoriesSelected(arrayOf(category(food, "Food")))
                moneyPicker(activity).currentMoney = 7777L
                checkBox(activity, R.id.repeat_checkbox).performClick()
                recurrenceText[0] = fieldText(activity, R.id.recurrence_edit_text)
                assertFalse(recurrenceText[0].orEmpty().isEmpty())
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertEquals(activity.getString(R.string.hint_category), fieldText(activity, R.id.type_edit_text))
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.category_edit_text).visibility)
                assertEquals("Food", fieldText(activity, R.id.category_edit_text))
                assertEquals(listOf(food), categoryPickerIds(activity))
                assertEquals(7777L, moneyPicker(activity).currentMoney)
                assertTrue(checkBox(activity, R.id.repeat_checkbox).isChecked)
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.recurrence_edit_text).visibility)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.start_date_edit_text).visibility)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.end_date_edit_text).visibility)
                assertEquals(recurrenceText[0], fieldText(activity, R.id.recurrence_edit_text))
            }
        }
    }

    // fixtures

    private fun cancelRollAlarm() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(
            PendingIntent.getBroadcast(
                context, 0,
                Intent(context, RecurrenceBroadcastReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

    private fun insertWallet(name: String, currency: String): Long {
        val values = ContentValues().apply {
            put(Contract.Wallet.NAME, name)
            put(Contract.Wallet.ICON, ICON)
            put(Contract.Wallet.CURRENCY, currency)
            put(Contract.Wallet.START_MONEY, 0L)
            put(Contract.Wallet.COUNT_IN_TOTAL, true)
            put(Contract.Wallet.ARCHIVED, false)
        }
        val uri = checkNotNull(resolver.insert(DataContentProvider.CONTENT_WALLETS, values))
        return ContentUris.parseId(uri)
    }

    private fun insertCategory(name: String, type: Contract.CategoryType, parent: Long?): Long {
        val values = ContentValues().apply {
            put(Contract.Category.NAME, name)
            put(Contract.Category.ICON, ICON)
            put(Contract.Category.TYPE, type.value)
            put(Contract.Category.SHOW_REPORT, true)
            if (parent != null) {
                put(Contract.Category.PARENT, parent)
            }
        }
        val uri = checkNotNull(resolver.insert(DataContentProvider.CONTENT_CATEGORIES, values))
        return ContentUris.parseId(uri)
    }

    private fun insertTransaction(
        walletId: Long,
        date: String,
        direction: Int,
        categoryId: Long,
        money: Long
    ): Long {
        val values = ContentValues().apply {
            put(Contract.Transaction.MONEY, money)
            put(Contract.Transaction.DATE, date)
            put(Contract.Transaction.DESCRIPTION, "Fixture row")
            put(Contract.Transaction.CATEGORY_ID, categoryId)
            put(Contract.Transaction.DIRECTION, direction)
            put(Contract.Transaction.TYPE, NewEditTransactionActivity.TYPE_STANDARD)
            put(Contract.Transaction.WALLET_ID, walletId)
            put(Contract.Transaction.CONFIRMED, true)
            put(Contract.Transaction.COUNT_IN_TOTAL, true)
        }
        val uri = checkNotNull(resolver.insert(DataContentProvider.CONTENT_TRANSACTIONS, values))
        return ContentUris.parseId(uri)
    }

    private fun insertTransfer(from: Long, to: Long, money: Long, date: String): Long {
        val values = TransferContentValuesBuilder()
            .description("Fixture transfer")
            .date(date)
            .fromWalletId(from)
            .toWalletId(to)
            .taxWalletId(from)
            .fromMoney(money)
            .toMoney(money)
            .taxMoney(0L)
            .note("")
            .placeId(null)
            .eventId(null)
            .confirmed(true)
            .countInTotal(true)
            .peopleIds(null)
            .attachmentIds(null)
            .build()
        val uri = checkNotNull(resolver.insert(DataContentProvider.CONTENT_TRANSFERS, values))
        return ContentUris.parseId(uri)
    }

    private fun insertBudget(
        type: Contract.BudgetType,
        money: Long,
        currency: String,
        wallets: LongArray?,
        categories: LongArray?,
        startDate: String,
        endDate: String,
        rule: String?,
        ruleStart: String?,
        rolledFrom: Long?
    ): Long {
        val values = ContentValues().apply {
            put(Contract.Budget.TYPE, type.value)
            put(Contract.Budget.MONEY, money)
            put(Contract.Budget.CURRENCY, currency)
            put(Contract.Budget.WALLET_IDS, idList(wallets))
            put(Contract.Budget.CATEGORY_IDS, idList(categories))
            put(Contract.Budget.START_DATE, startDate)
            put(Contract.Budget.END_DATE, endDate)
            put(Contract.Budget.RULE, rule)
            put(Contract.Budget.RULE_START, ruleStart)
            if (rolledFrom != null) {
                put(Contract.Budget.ROLLED_FROM_ID, rolledFrom)
            }
        }
        val uri = checkNotNull(resolver.insert(DataContentProvider.CONTENT_BUDGETS, values))
        return ContentUris.parseId(uri)
    }

    private fun insertAprilCategoryBudget(wallets: LongArray): Long {
        return insertBudget(
            Contract.BudgetType.CATEGORY, 900000L, "EUR", wallets,
            longArrayOf(food, rent), APRIL_START, APRIL_END, null, null, null
        )
    }

    private fun insertRolledPeriod(rolledFrom: Long, startDate: String, endDate: String): Long {
        return insertBudget(
            Contract.BudgetType.EXPENSES, 300000L, "EUR", longArrayOf(walletA),
            null, startDate, endDate, monthlyOn(date("2019-01-15")).rule, "2019-01-15",
            rolledFrom
        )
    }

    // reading back

    private fun budgetRow(budgetId: Long): Cursor {
        val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_BUDGETS, budgetId)
        val cursor = checkNotNull(resolver.query(uri, budgetProjection(), null, null, null))
        assertTrue(cursor.moveToFirst())
        return cursor
    }

    private fun newestBudget(): Long {
        val cursor = checkNotNull(
            resolver.query(
                DataContentProvider.CONTENT_BUDGETS,
                arrayOf(Contract.Budget.ID), null, null, "${Contract.Budget.ID} DESC"
            )
        )
        assertTrue(cursor.moveToFirst())
        val id = cursor.getLong(0)
        cursor.close()
        return id
    }

    private fun countBudgets(): Int {
        val cursor = checkNotNull(
            resolver.query(
                DataContentProvider.CONTENT_BUDGETS,
                arrayOf(Contract.Budget.ID), null, null, null
            )
        )
        val count = cursor.count
        cursor.close()
        return count
    }

    private fun progressOf(budgetId: Long): Long {
        val cursor = budgetRow(budgetId)
        val progress = cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Budget.PROGRESS))
        cursor.close()
        return progress
    }

    /**
     * The money on every transaction the budget's own list carries, added up.
     */
    private fun listedMoneyOf(budgetId: Long): Long {
        val uri = Uri.withAppendedPath(
            ContentUris.withAppendedId(DataContentProvider.CONTENT_BUDGETS, budgetId),
            "transactions"
        )
        val cursor = checkNotNull(
            resolver.query(uri, arrayOf(Contract.Transaction.MONEY), null, null, null)
        )
        var total = 0L
        while (cursor.moveToNext()) {
            total += cursor.getLong(0)
        }
        cursor.close()
        return total
    }

    private fun assertRepeatingRow(
        budgetId: Long,
        startDate: String,
        endDate: String,
        rule: String?,
        ruleStart: String?
    ) {
        val row = budgetRow(budgetId)
        assertEquals(startDate, row.getString(row.getColumnIndexOrThrow(Contract.Budget.START_DATE)))
        assertEquals(endDate, row.getString(row.getColumnIndexOrThrow(Contract.Budget.END_DATE)))
        assertEquals(rule, row.getString(row.getColumnIndexOrThrow(Contract.Budget.RULE)))
        assertEquals(ruleStart, row.getString(row.getColumnIndexOrThrow(Contract.Budget.RULE_START)))
        row.close()
    }

    private fun uuidOf(budgetId: Long): String {
        val cursor = checkNotNull(
            resolver.query(
                SyncContentProvider.CONTENT_BUDGET,
                arrayOf(Contract.BUDGET_UUID), "${Contract.Budget.ID} = ?",
                arrayOf(budgetId.toString()), null
            )
        )
        assertTrue(cursor.moveToFirst())
        val uuid = cursor.getString(0)
        cursor.close()
        assertNotNull(uuid)
        return checkNotNull(uuid)
    }

    private fun walletIdsOf(budgetId: Long): List<Long> {
        return linkedIds(budgetId, "wallets", Contract.Wallet.ID)
    }

    private fun categoryIdsOf(budgetId: Long): List<Long> {
        return linkedIds(budgetId, "categories", Contract.Category.ID)
    }

    private fun linkedIds(budgetId: Long, path: String, column: String): List<Long> {
        val uri = Uri.withAppendedPath(
            ContentUris.withAppendedId(DataContentProvider.CONTENT_BUDGETS, budgetId), path
        )
        val cursor = checkNotNull(resolver.query(uri, arrayOf(column), null, null, null))
        val ids = ArrayList<Long>()
        while (cursor.moveToNext()) {
            ids.add(cursor.getLong(0))
        }
        cursor.close()
        ids.sort()
        return ids
    }

    private fun firstWalletRow(): Long {
        val cursor = checkNotNull(
            resolver.query(
                DataContentProvider.CONTENT_WALLETS,
                arrayOf(Contract.Wallet.ID), null, null, null
            )
        )
        assertTrue(cursor.moveToFirst())
        val id = cursor.getLong(0)
        cursor.close()
        return id
    }

    companion object {
        private const val ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}"

        private const val TAG_MONEY_PICKER = "NewEditBudgetActivity::Tag::MoneyPicker"
        private const val TAG_BUDGET_TYPE_PICKER = "NewEditBudgetActivity::Tag::BudgetTypePicker"
        private const val TAG_CATEGORY_PICKER = "NewEditBudgetActivity::Tag::CategoryPicker"
        private const val TAG_START_DATE_PICKER = "NewEditBudgetActivity::Tag::StartDatePicker"
        private const val TAG_END_DATE_PICKER = "NewEditBudgetActivity::Tag::EndDatePicker"
        private const val TAG_WALLETS_PICKER = "NewEditBudgetActivity::Tag::WalletsPicker"
        private const val TAG_RECURRENCE_PICKER = "NewEditBudgetActivity::Tag::RecurrencePicker"

        private const val APRIL_START = "2019-04-01"
        private const val APRIL_END = "2019-04-30"

        /**
         * The signed sum a category budget over Food and Rent, in wallets A and B, reports for April
         * 2019. Snacks counts through its parent Food, Salary is in neither, the wallet D row and the
         * May row are outside, and both transfer legs carry a system category.
         */
        private fun aprilCategoryProgress(): Long {
            return -(1000L + 20000L + 300000L + 50000000L + 7000000000L + 300000000000000L)
        }

        /**
         * The same rows read as an expenses budget, which counts them unsigned, leaves the income out
         * and drops the from leg of the transfer because wallet B is in the budget too.
         */
        private fun aprilExpensesProgress(): Long {
            return 1000L + 20000L + 300000L + 50000000L + 7000000000L + 300000000000000L
        }

        /**
         * The April expenses of wallet A alone, where the leg the transfer pays out of it counts
         * because wallet B is no longer in the budget, and the wallet B row of 9 April is gone.
         */
        private fun aprilExpensesInWalletAWithTheTransfer(): Long {
            return 1000L + 20000L + 300000L + 7000000000L + 300000000000000L + 4000000000000000L
        }

        private fun idList(ids: LongArray?): String? {
            if (ids == null || ids.isEmpty()) {
                return null
            }
            val builder = java.lang.StringBuilder()
            for (i in ids.indices) {
                if (i != 0) {
                    builder.append(",")
                }
                builder.append("<").append(ids[i]).append(">")
            }
            return builder.toString()
        }

        private fun secondsAgo(seconds: Int): String {
            return DateUtils.getSQLDateTimeString(
                Date(System.currentTimeMillis() - seconds * 1000L)
            )
        }

        private fun date(sqlDate: String): Date {
            return checkNotNull(DateUtils.getDateFromSQLDateString(sqlDate))
        }

        private fun category(id: Long, name: String): Category {
            return Category(id, name, IconLoader.parse(ICON), Contract.CategoryType.EXPENSE)
        }

        private fun wallet(id: Long, name: String, currency: String): Wallet {
            return Wallet(id, name, IconLoader.parse(ICON), CurrencyManager.getCurrency(currency), 0L, 0L)
        }

        // schedules

        private fun monthlyOn(startDate: Date): RecurrenceSetting {
            val builder = RecurrenceSetting.Builder(startDate, RecurrenceSetting.TYPE_MONTHLY)
            builder.setRepeatSameMonthDay()
            return builder.build()
        }

        private fun monthlyUntil(startDate: Date, endDate: Date): RecurrenceSetting {
            val builder = RecurrenceSetting.Builder(startDate, RecurrenceSetting.TYPE_MONTHLY)
            builder.setRepeatSameMonthDay()
            builder.setEndUntil(endDate)
            return builder.build()
        }

        private fun weeklyEvery(startDate: Date, weeks: Int): RecurrenceSetting {
            val builder = RecurrenceSetting.Builder(startDate, RecurrenceSetting.TYPE_WEEKLY)
            builder.setOffset(weeks)
            return builder.build()
        }

        private fun yearlyOn(startDate: Date): RecurrenceSetting {
            val builder = RecurrenceSetting.Builder(startDate, RecurrenceSetting.TYPE_YEARLY)
            builder.setRepeatSameYearDay()
            return builder.build()
        }

        /**
         * The period a monthly schedule anchored to the given day of the month is in today, as the
         * pair {start, end} in SQL date form. A month too short for that day takes its last day, the
         * same fallback the rule carries.
         */
        private fun monthlyPeriod(anchorDay: Int): Array<String> {
            val today = Calendar.getInstance()
            var start = occurrenceIn(today, anchorDay)
            if (start.get(Calendar.DAY_OF_MONTH) > today.get(Calendar.DAY_OF_MONTH)) {
                val previous = today.clone() as Calendar
                previous.add(Calendar.MONTH, -1)
                start = occurrenceIn(previous, anchorDay)
            }
            val next = start.clone() as Calendar
            next.add(Calendar.MONTH, 1)
            val nextOcc = occurrenceIn(next, anchorDay)
            nextOcc.add(Calendar.DAY_OF_MONTH, -1)
            return arrayOf(
                DateUtils.getSQLDateString(start.time),
                DateUtils.getSQLDateString(nextOcc.time)
            )
        }

        /**
         * The same period a whole number of years later, both ends stepped by the same amount. The
         * anchor day is always the 15th, so no month is short of it and no leap day arises.
         */
        private fun periodYearsLater(period: Array<String>, years: Int): Array<String> {
            val start = DateUtils.getCalendar(date(period[0]))
            start.add(Calendar.YEAR, years)
            val end = DateUtils.getCalendar(date(period[1]))
            end.add(Calendar.YEAR, years)
            return arrayOf(
                DateUtils.getSQLDateString(start.time),
                DateUtils.getSQLDateString(end.time)
            )
        }

        private fun occurrenceIn(month: Calendar, anchorDay: Int): Calendar {
            val calendar = month.clone() as Calendar
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            calendar.set(
                Calendar.DAY_OF_MONTH,
                Math.min(anchorDay, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
            )
            return calendar
        }

        /**
         * The day before the first weekly instance strictly after a day, stepping the given number of
         * days at a time from the anchor.
         */
        private fun weeklyEndAfter(anchor: String, after: String, step: Int): String {
            val instance = DateUtils.getCalendar(date(anchor))
            val limit = DateUtils.getCalendar(date(after))
            while (!instance.after(limit)) {
                instance.add(Calendar.DAY_OF_MONTH, step)
            }
            instance.add(Calendar.DAY_OF_MONTH, -1)
            return DateUtils.getSQLDateString(instance.time)
        }

        /**
         * The day before the first yearly instance strictly after a day, stepping a year at a time
         * from the anchor.
         */
        private fun yearlyEndAfter(anchor: String, after: String): String {
            val instance = DateUtils.getCalendar(date(anchor))
            val limit = DateUtils.getCalendar(date(after))
            while (!instance.after(limit)) {
                instance.add(Calendar.YEAR, 1)
            }
            instance.add(Calendar.DAY_OF_MONTH, -1)
            return DateUtils.getSQLDateString(instance.time)
        }

        // intents

        private fun newItemIntent(): Intent {
            return Intent(ApplicationProvider.getApplicationContext(), NewEditBudgetActivity::class.java).apply {
                putExtra(NewEditItemActivity.MODE, NewEditItemActivity.Mode.NEW_ITEM)
            }
        }

        private fun editIntent(budgetId: Long): Intent {
            return Intent(ApplicationProvider.getApplicationContext(), NewEditBudgetActivity::class.java).apply {
                putExtra(NewEditItemActivity.MODE, NewEditItemActivity.Mode.EDIT_ITEM)
                putExtra(NewEditItemActivity.ID, budgetId)
            }
        }

        // driving the screen

        private fun moneyPicker(activity: NewEditBudgetActivity): MoneyPicker {
            return checkNotNull(activity.supportFragmentManager.findFragmentByTag(TAG_MONEY_PICKER) as? MoneyPicker)
        }

        private fun budgetTypePicker(activity: NewEditBudgetActivity): BudgetTypePicker {
            return checkNotNull(activity.supportFragmentManager.findFragmentByTag(TAG_BUDGET_TYPE_PICKER) as? BudgetTypePicker)
        }

        private fun categoryPicker(activity: NewEditBudgetActivity): CategoryPicker {
            return checkNotNull(activity.supportFragmentManager.findFragmentByTag(TAG_CATEGORY_PICKER) as? CategoryPicker)
        }

        private fun startDatePicker(activity: NewEditBudgetActivity): DateTimePicker {
            return checkNotNull(activity.supportFragmentManager.findFragmentByTag(TAG_START_DATE_PICKER) as? DateTimePicker)
        }

        private fun endDatePicker(activity: NewEditBudgetActivity): DateTimePicker {
            return checkNotNull(activity.supportFragmentManager.findFragmentByTag(TAG_END_DATE_PICKER) as? DateTimePicker)
        }

        private fun walletsPicker(activity: NewEditBudgetActivity): WalletPicker {
            return checkNotNull(activity.supportFragmentManager.findFragmentByTag(TAG_WALLETS_PICKER) as? WalletPicker)
        }

        private fun recurrencePicker(activity: NewEditBudgetActivity): RecurrencePicker {
            return checkNotNull(activity.supportFragmentManager.findFragmentByTag(TAG_RECURRENCE_PICKER) as? RecurrencePicker)
        }

        private fun field(activity: NewEditBudgetActivity, viewId: Int): MaterialEditText {
            return activity.findViewById(viewId)
        }

        private fun fieldText(activity: NewEditBudgetActivity, viewId: Int): String {
            return field(activity, viewId).textAsString
        }

        private fun headerText(activity: NewEditBudgetActivity, viewId: Int): String {
            val textView = activity.findViewById<TextView>(viewId)
            return textView.text.toString()
        }

        private fun formattedMoney(currency: String, money: Long): String {
            return MoneyFormatter.getInstance().getNotTintedString(
                CurrencyManager.getCurrency(currency),
                money, MoneyFormatter.CurrencyMode.ALWAYS_HIDDEN
            )
        }

        /**
         * What the formatter writes for a day into a date field, read off a throwaway view so the
         * expectation is the formatter itself and not a second copy of its pattern.
         */
        private fun formattedDate(activity: NewEditBudgetActivity, date: Date): String {
            val textView = TextView(activity)
            DateFormatter.applyDate(textView, date)
            return textView.text.toString()
        }

        private fun checkBox(activity: NewEditBudgetActivity, viewId: Int): CheckBox {
            return activity.findViewById(viewId)
        }

        private fun walletPickerIds(activity: NewEditBudgetActivity): List<Long> {
            val ids = ArrayList<Long>()
            val wallets = walletsPicker(activity).currentWallets ?: return emptyList()
            for (wallet in wallets) {
                ids.add(wallet.id)
            }
            return ids
        }

        private fun categoryPickerIds(activity: NewEditBudgetActivity): List<Long> {
            val ids = ArrayList<Long>()
            val categories = categoryPicker(activity).currentCategories ?: return emptyList()
            for (category in categories) {
                ids.add(category.id)
            }
            return ids
        }

        private fun sorted(ids: List<Long>): List<Long> {
            val copy = ArrayList(ids)
            Collections.sort(copy)
            return copy
        }

        /**
         * Saves through the toolbar menu, the way a tap on the check mark does. The dispatch answers
         * false even after it has run the save, because NewEditItemActivity.onMenuItemClick returns
         * false for every item, so what proves the save arrived is the row read back afterwards.
         */
        private fun save(activity: NewEditBudgetActivity) {
            val toolbar = activity.findViewById<Toolbar>(R.id.primary_toolbar)
            val handled = toolbar.menu.performIdentifierAction(R.id.action_save_changes, 0)
            assertFalse("the toolbar menu answered $handled", handled)
        }

        private fun budgetProjection(): Array<String> {
            return arrayOf(
                Contract.Budget.ID,
                Contract.Budget.TYPE,
                Contract.Budget.CATEGORY_ID,
                Contract.Budget.CATEGORY_IDS,
                Contract.Budget.START_DATE,
                Contract.Budget.END_DATE,
                Contract.Budget.MONEY,
                Contract.Budget.CURRENCY,
                Contract.Budget.RULE,
                Contract.Budget.RULE_START,
                Contract.Budget.PROGRESS
            )
        }
    }
}
