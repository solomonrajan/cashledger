package com.oriondev.moneywallet.ui.activity

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Looper
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.tabs.TabLayout
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.Attachment
import com.oriondev.moneywallet.model.Category
import com.oriondev.moneywallet.model.Person
import com.oriondev.moneywallet.model.Wallet
import com.oriondev.moneywallet.picker.AttachmentPicker
import com.oriondev.moneywallet.picker.CategoryPicker
import com.oriondev.moneywallet.picker.DateTimePicker
import com.oriondev.moneywallet.picker.MoneyPicker
import com.oriondev.moneywallet.picker.PersonPicker
import com.oriondev.moneywallet.picker.WalletPicker
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.SplitLedger
import com.oriondev.moneywallet.storage.database.TestDatabases
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.view.text.MaterialEditText
import com.oriondev.moneywallet.utils.CurrencyManager
import com.oriondev.moneywallet.utils.DateUtils
import com.oriondev.moneywallet.utils.IconLoader
import com.oriondev.moneywallet.utils.MoneyFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import java.util.Collections
import java.util.Date

/**
 * Drives the real editor on the JVM, against the real content provider over a fresh database,
 * and reads what it wrote. TransactionEditorRulesTest covers each rule on its own; this covers
 * the call sites, which is where a transposed argument or a dropped one compiles and ships.
 *
 * Amounts are minor units, so 1000 is 10.00. The saving fixture opens on 10.00, took a
 * confirmed 100.00 ten days ago, an unconfirmed 50.00 five days ago that must not count, and
 * gives back 30.00 ten days from now. From now onwards the lowest it reaches is 80.00; counted
 * from before the deposit it is 10.00.
 */
@RunWith(RobolectricTestRunner::class)
class NewEditTransactionActivityTest {

    private lateinit var resolver: ContentResolver
    private var euroWallet: Long = 0L
    private var dollarWallet: Long = 0L
    private var saving: Long = 0L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        TestDatabases.useFreshDatabase(context)
        resolver = context.contentResolver
        // takes wallet id 1, so no wallet below shares its id with the saving, which is 1
        insertWallet("Unused", "EUR")
        euroWallet = insertWallet("Cash", "EUR")
        dollarWallet = insertWallet("Bank", "USD")
        // a different number of unused rows per table, so the first place, event, person and
        // category a case makes are ids 4, 5, 6 and 7 and none of them equals a wallet, the
        // saving, a debt or a model, which are 1 to 3. An id read off the wrong column or from
        // the wrong object is then a different number, which an assertion on an id can see
        for (i in 0 until 3) {
            insertPlace()
        }
        for (i in 0 until 4) {
            insertEvent()
        }
        for (i in 0 until 5) {
            insertPerson("Unused")
        }
        for (i in 0 until 6) {
            insertCategory()
        }
        saving = insertSaving(1000L, 50000L, euroWallet)
        insertSavingRow(10000L, daysFromNow(-10), Contract.CategoryTag.SAVING_DEPOSIT, true, euroWallet)
        insertSavingRow(5000L, daysFromNow(-5), Contract.CategoryTag.SAVING_DEPOSIT, false, euroWallet)
        insertSavingRow(3000L, daysFromNow(10), Contract.CategoryTag.SAVING_WITHDRAW, true, euroWallet)
    }

    @Test
    fun aDepositOpensOnTheSavingsOwnWalletAndFilesTheRowAgainstTheSaving() {
        val before = countTransactions()
        ActivityScenario.launch<NewEditTransactionActivity>(savingIntent(NewEditTransactionActivity.SAVING_DEPOSIT)).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(View.GONE, activity.findViewById<View>(R.id.wallet_edit_text).visibility)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.category_edit_text).visibility)
                assertEquals("Cash", walletField(activity))
                // a deposit opens on nothing; only withdraw everything is prefilled
                assertEquals(0L, moneyPicker(activity).currentMoney)
                moneyPicker(activity).currentMoney = 2500L
                save(activity)
                assertTrue(activity.isFinishing)
            }
        }
        assertEquals(before + 1, countTransactions())
        val row = newestTransaction()
        assertEquals(2500L, row.getLong(row.getColumnIndexOrThrow(Contract.Transaction.MONEY)))
        assertEquals(euroWallet, row.getLong(row.getColumnIndexOrThrow(Contract.Transaction.WALLET_ID)))
        assertEquals(saving, row.getLong(row.getColumnIndexOrThrow(Contract.Transaction.SAVING_ID)))
        assertTrue(row.isNull(row.getColumnIndexOrThrow(Contract.Transaction.DEBT_ID)))
        assertEquals(
            NewEditTransactionActivity.TYPE_SAVING,
            row.getInt(row.getColumnIndexOrThrow(Contract.Transaction.TYPE))
        )
        assertEquals(Contract.Direction.EXPENSE, row.getInt(row.getColumnIndexOrThrow(Contract.Transaction.DIRECTION)))
        assertEquals(
            Contract.CategoryTag.SAVING_DEPOSIT,
            row.getString(row.getColumnIndexOrThrow(Contract.Transaction.CATEGORY_TAG))
        )
        row.close()
    }

    @Test
    fun aWithdrawalIsFiledAsIncomeUnderTheWithdrawTag() {
        val before = countTransactions()
        ActivityScenario.launch<NewEditTransactionActivity>(savingIntent(NewEditTransactionActivity.SAVING_WITHDRAW)).use { scenario ->
            scenario.onActivity { activity ->
                moneyPicker(activity).currentMoney = 100L
                save(activity)
                assertTrue(activity.isFinishing)
            }
        }
        // the fixture's own future withdrawal is the newest row until the editor writes one
        assertEquals(before + 1, countTransactions())
        val row = newestTransaction()
        assertEquals(100L, row.getLong(row.getColumnIndexOrThrow(Contract.Transaction.MONEY)))
        assertEquals(Contract.Direction.INCOME, row.getInt(row.getColumnIndexOrThrow(Contract.Transaction.DIRECTION)))
        assertEquals(
            Contract.CategoryTag.SAVING_WITHDRAW,
            row.getString(row.getColumnIndexOrThrow(Contract.Transaction.CATEGORY_TAG))
        )
        row.close()
    }

    @Test
    fun withdrawEverythingOpensOnTheLowestBalanceFromNowAndCompletesTheSaving() {
        ActivityScenario.launch<NewEditTransactionActivity>(
            savingIntent(NewEditTransactionActivity.SAVING_WITHDRAW_EVERYTHING)
        ).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(8000L, moneyPicker(activity).currentMoney)
                save(activity)
                assertTrue(activity.isFinishing)
            }
        }
        val row = newestTransaction()
        assertEquals(8000L, row.getLong(row.getColumnIndexOrThrow(Contract.Transaction.MONEY)))
        assertEquals(Contract.Direction.INCOME, row.getInt(row.getColumnIndexOrThrow(Contract.Transaction.DIRECTION)))
        row.close()
        assertTrue(savingIsComplete())
    }

    @Test
    fun aWithdrawalOverTheLowestBalanceIsRefusedAndOneAtItIsSaved() {
        val before = countTransactions()
        ActivityScenario.launch<NewEditTransactionActivity>(savingIntent(NewEditTransactionActivity.SAVING_WITHDRAW)).use { scenario ->
            scenario.onActivity { activity ->
                moneyPicker(activity).currentMoney = 8001L
                save(activity)
                assertFalse(activity.isFinishing)
                assertEquals(before, countTransactions())
                moneyPicker(activity).currentMoney = 8000L
                save(activity)
                assertTrue(activity.isFinishing)
            }
        }
        assertEquals(before + 1, countTransactions())
        assertFalse(savingIsComplete())
    }

    @Test
    fun theCeilingIsCountedFromTheDateOnScreen() {
        val before = countTransactions()
        // from yesterday the lowest is still 8000, from before the deposit it is 1000
        val onScreen = daysFromNow(-1)
        ActivityScenario.launch<NewEditTransactionActivity>(savingIntent(NewEditTransactionActivity.SAVING_WITHDRAW)).use { scenario ->
            scenario.onActivity { activity ->
                moneyPicker(activity).currentMoney = 2000L
                dateTimePicker(activity).currentDateTime = daysFromNow(-20)
                save(activity)
                assertFalse(activity.isFinishing)
                assertEquals(before, countTransactions())
                dateTimePicker(activity).currentDateTime = onScreen
                save(activity)
                assertTrue(activity.isFinishing)
            }
        }
        assertEquals(before + 1, countTransactions())
        val row = newestTransaction()
        assertEquals(
            "the row keeps the date on screen, not the moment it was saved",
            DateUtils.getSQLDateTimeString(onScreen),
            row.getString(row.getColumnIndexOrThrow(Contract.Transaction.DATE))
        )
        row.close()
    }

    @Test
    fun anEditedWithdrawalIsNotHeldAgainstItself() {
        val row = insertSavingRow(5000L, daysFromNow(-1), Contract.CategoryTag.SAVING_WITHDRAW, true, euroWallet)
        val storedDate = dateOf(row)
        ActivityScenario.launch<NewEditTransactionActivity>(editIntent(row)).use { scenario ->
            scenario.onActivity { activity ->
                moneyPicker(activity).currentMoney = 7000L
                save(activity)
                assertTrue(activity.isFinishing)
            }
        }
        assertEquals(7000L, moneyOf(row))
        assertEquals("an edit keeps the row's own date", storedDate, dateOf(row))
    }

    @Test
    fun aStoredWithdrawalOnASavingUnderZeroCanBeLoweredButNotRaised() {
        val testSaving = insertSaving(0L, 0L, euroWallet)
        val row = insertSavingRow(testSaving, 5000L, daysFromNow(-1), Contract.CategoryTag.SAVING_WITHDRAW, true, euroWallet)
        ActivityScenario.launch<NewEditTransactionActivity>(editIntent(row)).use { scenario ->
            scenario.onActivity { activity ->
                moneyPicker(activity).currentMoney = 5001L
                save(activity)
                assertFalse(activity.isFinishing)
                // lowered, but moved to a date before the one it was stored on, is a new drain
                // on the day it moves across and is held to the ceiling, which is 0 here
                moneyPicker(activity).currentMoney = 4000L
                dateTimePicker(activity).currentDateTime = daysFromNow(-2)
                save(activity)
                assertFalse(activity.isFinishing)
                dateTimePicker(activity).currentDateTime = daysFromNow(-1)
                save(activity)
                assertTrue(activity.isFinishing)
            }
        }
        assertEquals(4000L, moneyOf(row))
    }

    @Test
    fun anAmountOfNothingIsNeverRefused() {
        val testSaving = insertSaving(0L, 0L, euroWallet)
        insertSavingRow(testSaving, 5000L, daysFromNow(-1), Contract.CategoryTag.SAVING_WITHDRAW, true, euroWallet)
        val intent = savingIntent(NewEditTransactionActivity.SAVING_WITHDRAW)
        intent.putExtra(NewEditTransactionActivity.SAVING_ID, testSaving)
        val before = countTransactions()
        ActivityScenario.launch<NewEditTransactionActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                moneyPicker(activity).currentMoney = 1L
                save(activity)
                assertFalse(activity.isFinishing)
                // the lowest this saving reaches is minus 5000 and the ceiling is held at
                // nothing, so the refusal names 0.00 and not a negative figure
                val dialog = ShadowDialog.getLatestDialog() as AlertDialog
                val message = dialog.findViewById<TextView>(android.R.id.message)
                assertEquals(
                    activity.getString(
                        R.string.error_saving_withdraw_over_balance,
                        MoneyFormatter.getInstance().getNotTintedString(
                            CurrencyManager.getCurrency("EUR"), 0L
                        )
                    ),
                    message?.text?.toString()
                )
                moneyPicker(activity).currentMoney = 0L
                save(activity)
                assertTrue(activity.isFinishing)
            }
        }
        assertEquals(before + 1, countTransactions())
    }

    @Test
    fun theCeilingStepsAsideForARowHeldInAnotherCurrency() {
        val testSaving = insertSaving(0L, 0L, euroWallet)
        val row = insertSavingRow(testSaving, 100L, daysFromNow(-1), Contract.CategoryTag.SAVING_WITHDRAW, true, dollarWallet)
        ActivityScenario.launch<NewEditTransactionActivity>(editIntent(row)).use { scenario ->
            scenario.onActivity { activity ->
                moneyPicker(activity).currentMoney = 99999L
                save(activity)
                assertTrue(activity.isFinishing)
            }
        }
        assertEquals(99999L, moneyOf(row))
    }

    @Test
    fun aSavingIntentNamingNoActionClosesTheEditor() {
        val intent = savingIntent(0)
        intent.removeExtra(NewEditTransactionActivity.SAVING_ACTION)
        ActivityScenario.launch<NewEditTransactionActivity>(intent).use { scenario ->
            // finished from inside its own onCreate, so it is gone before the scenario can hand
            // it over
            assertEquals(Lifecycle.State.DESTROYED, scenario.state)
        }
    }

    @Test
    fun aDebtPaymentHidesTheWalletFieldAndKeepsItHiddenAcrossARecreate() {
        val debt = insertDebt(euroWallet)
        val intent = newItemIntent().apply {
            putExtra(NewEditTransactionActivity.TYPE, NewEditTransactionActivity.TYPE_DEBT)
            putExtra(NewEditTransactionActivity.DEBT_ID, debt)
        }
        ActivityScenario.launch<NewEditTransactionActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(View.GONE, activity.findViewById<View>(R.id.wallet_edit_text).visibility)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.category_edit_text).visibility)
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertEquals(View.GONE, activity.findViewById<View>(R.id.wallet_edit_text).visibility)
                moneyPicker(activity).currentMoney = 700L
                save(activity)
                assertTrue(activity.isFinishing)
            }
        }
        val row = newestTransaction()
        assertEquals(debt, row.getLong(row.getColumnIndexOrThrow(Contract.Transaction.DEBT_ID)))
        assertTrue(row.isNull(row.getColumnIndexOrThrow(Contract.Transaction.SAVING_ID)))
        assertEquals(NewEditTransactionActivity.TYPE_DEBT, row.getInt(row.getColumnIndexOrThrow(Contract.Transaction.TYPE)))
        assertEquals(Contract.CategoryTag.PAID_DEBT, row.getString(row.getColumnIndexOrThrow(Contract.Transaction.CATEGORY_TAG)))
        row.close()
    }

    @Test
    fun payingInFullOpensOnWhatIsLeftAndTheDebtsDescription() {
        val debt = insertPartlyPaidDebt()
        ActivityScenario.launch<NewEditTransactionActivity>(
            debtIntent(debt, NewEditTransactionActivity.DEBT_PAY_IN_FULL)
        ).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(
                    "10000 owed less the 2500 already paid, the figure the card shows",
                    7500L,
                    moneyPicker(activity).currentMoney
                )
                assertEquals("Rent", descriptionField(activity))
            }
        }
    }

    @Test
    fun payingOpensOnNothingAndTheDebtsDescription() {
        val debt = insertPartlyPaidDebt()
        ActivityScenario.launch<NewEditTransactionActivity>(
            debtIntent(debt, NewEditTransactionActivity.DEBT_PAY)
        ).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(0L, moneyPicker(activity).currentMoney)
                assertEquals("Rent", descriptionField(activity))
            }
        }
    }

    @Test
    fun aDebtsOwnRowKeepsTheWalletField() {
        val debt = insertDebt(euroWallet)
        ActivityScenario.launch<NewEditTransactionActivity>(editIntent(masterTransactionOf(debt))).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.wallet_edit_text).visibility)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.category_edit_text).visibility)
            }
        }
    }

    @Test
    fun aDepositOverTheSavingsBalanceIsNeverRefused() {
        val before = countTransactions()
        ActivityScenario.launch<NewEditTransactionActivity>(savingIntent(NewEditTransactionActivity.SAVING_DEPOSIT)).use { scenario ->
            scenario.onActivity { activity ->
                moneyPicker(activity).currentMoney = 20000L
                save(activity)
                assertTrue(activity.isFinishing)
            }
        }
        assertEquals(before + 1, countTransactions())
    }

    @Test
    fun aNewTransactionOpensOnTheWalletTheIntentNames() {
        val intent = newItemIntent().apply {
            putExtra(NewEditTransactionActivity.TYPE, NewEditTransactionActivity.TYPE_STANDARD)
            putExtra(NewEditTransactionActivity.WALLET_ID, dollarWallet)
        }
        ActivityScenario.launch<NewEditTransactionActivity>(intent).use { scenario ->
            scenario.onActivity { activity -> assertEquals("Bank", walletField(activity)) }
        }
    }

    @Test
    fun aNewTransactionOpensOnTheCurrentWalletWhenTheIntentNamesNone() {
        PreferenceManager.setCurrentWallet(ApplicationProvider.getApplicationContext(), dollarWallet)
        val intent = newItemIntent().apply {
            putExtra(NewEditTransactionActivity.TYPE, NewEditTransactionActivity.TYPE_STANDARD)
        }
        ActivityScenario.launch<NewEditTransactionActivity>(intent).use { scenario ->
            scenario.onActivity { activity -> assertEquals("Bank", walletField(activity)) }
        }
    }

    @Test
    fun aNewTransactionOpenedFromAPersonHasThatPersonAttached() {
        val person = ContentValues().apply {
            put(Contract.Person.NAME, "Alice")
            put(Contract.Person.ICON, ICON)
        }
        val personUri = checkNotNull(resolver.insert(DataContentProvider.CONTENT_PEOPLE, person))
        val personId = ContentUris.parseId(personUri)
        // no TYPE extra on purpose: the person screen, the launcher shortcut, the reminder and
        // the main screen's button all launch without one and rely on the default
        val intent = newItemIntent().apply {
            putExtra(NewEditTransactionActivity.PERSON_ID, personId)
        }
        ActivityScenario.launch<NewEditTransactionActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(
                    "Alice",
                    activity.findViewById<MaterialEditText>(R.id.people_edit_text).getTextAsString()
                )
                // the id is what files the row; the label only shows the name
                val people = checkNotNull(personPicker(activity).currentPeople)
                assertEquals(personId, people[0].id)
            }
        }
    }

    @Test
    fun aDebtLaunchWithNoDebtRowFilesUnderTheActionItNames() {
        val intent = newItemIntent().apply {
            putExtra(NewEditTransactionActivity.TYPE, NewEditTransactionActivity.TYPE_DEBT)
            putExtra(NewEditTransactionActivity.DEBT_ID, 999L)
            putExtra(NewEditTransactionActivity.DEBT_ACTION, NewEditTransactionActivity.DEBT_RECEIVE)
        }
        ActivityScenario.launch<NewEditTransactionActivity>(intent).use { scenario ->
            // the action picks the paid credit category, which makes this a payment and hides
            // the wallet field; a debt row would have named the kind itself. Both debt tags
            // hide the field, so the category is what tells the two actions apart
            scenario.onActivity { activity ->
                assertEquals(View.GONE, activity.findViewById<View>(R.id.wallet_edit_text).visibility)
                assertEquals(
                    Contract.CategoryTag.PAID_CREDIT,
                    categoryPicker(activity).currentCategory?.tag
                )
            }
        }
    }

    @Test
    fun withdrawEverythingOnASavingUnderZeroOpensOnNothingAndWritesNothingNegative() {
        val testSaving = insertSaving(0L, 0L, euroWallet)
        insertSavingRow(testSaving, 5000L, daysFromNow(1), Contract.CategoryTag.SAVING_WITHDRAW, true, euroWallet)
        val intent = savingIntent(NewEditTransactionActivity.SAVING_WITHDRAW_EVERYTHING).apply {
            putExtra(NewEditTransactionActivity.SAVING_ID, testSaving)
        }
        ActivityScenario.launch<NewEditTransactionActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                // the lowest from now is minus 5000; the prefill is held at nothing
                assertEquals(0L, moneyPicker(activity).currentMoney)
                save(activity)
                assertTrue(activity.isFinishing)
            }
        }
        val row = newestTransaction()
        assertEquals(0L, row.getLong(row.getColumnIndexOrThrow(Contract.Transaction.MONEY)))
        row.close()
    }

    @Test
    fun aNewTransactionOnTheTotalWalletStillOpensOnAWallet() {
        PreferenceManager.setCurrentWallet(
            ApplicationProvider.getApplicationContext(),
            PreferenceManager.TOTAL_WALLET_ID
        )
        val intent = newItemIntent().apply {
            putExtra(NewEditTransactionActivity.TYPE, NewEditTransactionActivity.TYPE_STANDARD)
        }
        ActivityScenario.launch<NewEditTransactionActivity>(intent).use { scenario ->
            // the total wallet is every wallet, so the editor takes the first one
            scenario.onActivity { activity -> assertEquals("Unused", walletField(activity)) }
        }
    }

    @Test
    fun withdrawEverythingOffersTheWholeBalanceOnASavingPastItsTarget() {
        val testSaving = insertSaving(0L, 1000L, euroWallet)
        insertSavingRow(testSaving, 5000L, daysFromNow(-10), Contract.CategoryTag.SAVING_DEPOSIT, true, euroWallet)
        val intent = savingIntent(NewEditTransactionActivity.SAVING_WITHDRAW_EVERYTHING).apply {
            putExtra(NewEditTransactionActivity.SAVING_ID, testSaving)
        }
        ActivityScenario.launch<NewEditTransactionActivity>(intent).use { scenario ->
            // the target is 1000 and the saving holds 5000; emptying it means all of it
            scenario.onActivity { activity -> assertEquals(5000L, moneyPicker(activity).currentMoney) }
        }
    }

    @Test
    fun aDebtPaymentCarriesTheDebtsOwnPerson() {
        val person = ContentValues().apply {
            put(Contract.Person.NAME, "Bob")
            put(Contract.Person.ICON, ICON)
        }
        val personUri = checkNotNull(resolver.insert(DataContentProvider.CONTENT_PEOPLE, person))
        val personId = ContentUris.parseId(personUri)
        val debt = insertDebt(euroWallet, personId)
        val intent = newItemIntent().apply {
            putExtra(NewEditTransactionActivity.TYPE, NewEditTransactionActivity.TYPE_DEBT)
            putExtra(NewEditTransactionActivity.DEBT_ID, debt)
        }
        ActivityScenario.launch<NewEditTransactionActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                val people = checkNotNull(personPicker(activity).currentPeople)
                assertEquals(personId, people[0].id)
            }
        }
    }

    @Test
    fun aSavedStandardTransactionCarriesTheWalletAndCategoryIds() {
        val categoryId = insertCategory()
        val intent = newItemIntent().apply {
            putExtra(NewEditTransactionActivity.TYPE, NewEditTransactionActivity.TYPE_STANDARD)
            putExtra(NewEditTransactionActivity.WALLET_ID, dollarWallet)
        }
        val before = countTransactions()
        ActivityScenario.launch<NewEditTransactionActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                categoryPicker(activity).setCategory(
                    Category(
                        categoryId, "Groceries",
                        IconLoader.parse(ICON), Contract.CategoryType.EXPENSE, null
                    )
                )
                moneyPicker(activity).currentMoney = 700L
                save(activity)
                assertTrue(activity.isFinishing)
            }
        }
        // the name on screen is not the id on the row, and a row filed against a wallet or a
        // category that does not exist is dropped by the join with the editor reporting success
        assertEquals(before + 1, countTransactions())
        val row = newestTransaction()
        assertEquals(dollarWallet, row.getLong(row.getColumnIndexOrThrow(Contract.Transaction.WALLET_ID)))
        assertEquals(categoryId, row.getLong(row.getColumnIndexOrThrow(Contract.Transaction.CATEGORY_ID)))
        row.close()
    }

    @Test
    fun aSavedModelTransactionCarriesTheModelsIds() {
        val categoryId = insertCategory()
        val placeId = insertPlace()
        val eventId = insertEvent()
        val model = insertModel(4200L, dollarWallet, categoryId, placeId, eventId)
        val intent = newItemIntent().apply {
            putExtra(NewEditTransactionActivity.TYPE, NewEditTransactionActivity.TYPE_MODEL)
            putExtra(NewEditTransactionActivity.MODEL_ID, model)
        }
        val before = countTransactions()
        ActivityScenario.launch<NewEditTransactionActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                save(activity)
                assertTrue(activity.isFinishing)
            }
        }
        assertEquals(before + 1, countTransactions())
        val row = newestTransaction()
        assertEquals(4200L, row.getLong(row.getColumnIndexOrThrow(Contract.Transaction.MONEY)))
        assertEquals(dollarWallet, row.getLong(row.getColumnIndexOrThrow(Contract.Transaction.WALLET_ID)))
        assertEquals(categoryId, row.getLong(row.getColumnIndexOrThrow(Contract.Transaction.CATEGORY_ID)))
        assertEquals(placeId, row.getLong(row.getColumnIndexOrThrow(Contract.Transaction.PLACE_ID)))
        assertEquals(eventId, row.getLong(row.getColumnIndexOrThrow(Contract.Transaction.EVENT_ID)))
        row.close()
    }

    @Test
    fun aDebtPaymentCarriesTheDebtsOwnPlace() {
        val placeId = insertPlace()
        val debt = insertDebt(euroWallet, null, placeId)
        val intent = newItemIntent().apply {
            putExtra(NewEditTransactionActivity.TYPE, NewEditTransactionActivity.TYPE_DEBT)
            putExtra(NewEditTransactionActivity.DEBT_ID, debt)
        }
        val before = countTransactions()
        ActivityScenario.launch<NewEditTransactionActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                moneyPicker(activity).currentMoney = 300L
                save(activity)
                assertTrue(activity.isFinishing)
            }
        }
        assertEquals(before + 1, countTransactions())
        val row = newestTransaction()
        assertEquals(placeId, row.getLong(row.getColumnIndexOrThrow(Contract.Transaction.PLACE_ID)))
        row.close()
    }

    @Test
    fun aCopyOpensOnTheOriginalsValuesWithoutItsAttachments() {
        val categoryId = insertCategory()
        val attachmentId = insertAttachment()
        val original = ContentValues().apply {
            put(Contract.Transaction.MONEY, 1234L)
            put(Contract.Transaction.DATE, DateUtils.getSQLDateTimeString(daysFromNow(-3)))
            put(Contract.Transaction.DESCRIPTION, "Lunch")
            put(Contract.Transaction.CATEGORY_ID, categoryId)
            put(Contract.Transaction.DIRECTION, Contract.Direction.EXPENSE)
            put(Contract.Transaction.TYPE, NewEditTransactionActivity.TYPE_STANDARD)
            put(Contract.Transaction.WALLET_ID, dollarWallet)
            put(Contract.Transaction.CONFIRMED, true)
            put(Contract.Transaction.COUNT_IN_TOTAL, true)
            put(
                Contract.Transaction.ATTACHMENT_IDS,
                Contract.getObjectIds(
                    arrayOf(
                        Attachment(attachmentId, "receipt.jpg", "receipt.jpg", "image/jpeg", 1234L)
                    )
                )
            )
        }
        val originalUri = checkNotNull(resolver.insert(DataContentProvider.CONTENT_TRANSACTIONS, original))
        val originalId = ContentUris.parseId(originalUri)
        val intent = newItemIntent().apply {
            putExtra(NewEditTransactionActivity.DUPLICATE_ID, originalId)
        }
        ActivityScenario.launch<NewEditTransactionActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(1234L, moneyPicker(activity).currentMoney)
                assertEquals(categoryId, categoryPicker(activity).currentCategory?.id)
                assertEquals("Bank", walletField(activity))
                // removing an attachment in the editor deletes its file outright, so a copy that
                // shared one would leave the original pointing at nothing
                assertTrue(attachmentPicker(activity).currentAttachments.isEmpty())
            }
        }
    }

    @Test
    fun aModelFillsTheEditorFromItsOwnRow() {
        val model = insertModel(4200L, dollarWallet)
        val intent = newItemIntent().apply {
            putExtra(NewEditTransactionActivity.TYPE, NewEditTransactionActivity.TYPE_MODEL)
            putExtra(NewEditTransactionActivity.MODEL_ID, model)
        }
        ActivityScenario.launch<NewEditTransactionActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(4200L, moneyPicker(activity).currentMoney)
                assertEquals("Bank", walletField(activity))
            }
        }
    }

    @Test
    fun aTransactionSavedFromAModelIsStoredAsAPlainOne() {
        val intent = newItemIntent().apply {
            putExtra(NewEditTransactionActivity.TYPE, NewEditTransactionActivity.TYPE_MODEL)
            putExtra(NewEditTransactionActivity.MODEL_ID, insertModel(4200L, dollarWallet))
        }
        val before = countTransactions()
        ActivityScenario.launch<NewEditTransactionActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                save(activity)
                assertTrue(activity.isFinishing)
            }
        }
        assertEquals(before + 1, countTransactions())
        assertEquals(Contract.TransactionType.STANDARD.toLong(), longOf(newestId(), Contract.Transaction.TYPE))
    }

    // splits, over SplitLedger, whose ids start at 901 and so never meet the rows above

    @Test
    fun aNewTransactionIsSavedWithItsParts() {
        SplitLedger.build(ApplicationProvider.getApplicationContext())
        val intent = newItemIntent().apply {
            putExtra(NewEditTransactionActivity.WALLET_ID, SplitLedger.CASH)
        }
        ActivityScenario.launch<NewEditTransactionActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                categoryPicker(activity).setCategory(ledgerCategory(SplitLedger.GROCERIES))
                moneyPicker(activity).setMoney(9000L)
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.split_edit_text).visibility)
                addPart(activity, SplitLedger.HOUSEHOLD, 2000L)
                addPart(activity, SplitLedger.FUEL, 1500L)
                assertEquals(listOf("Household", "Fuel"), partLabels(activity))
                assertEquals(remainingHint(activity, "Groceries"), remainingField(activity).floatingLabelText)
                assertEquals(euro(9000L - 2000L - 1500L), remainingField(activity).getTextAsString())
                save(activity)
                assertTrue(activity.isFinishing)
            }
        }
        val id = newestId()
        assertEquals(sortedOf(SplitLedger.HOUSEHOLD.toString() + ":2000:null", SplitLedger.FUEL.toString() + ":1500:null"), storedParts(id))
    }

    /** The part picker offers the main category's type alone, and the keypad follows the pick. */
    @Test
    fun aPartIsPickedAmongCategoriesOfTheMainOnesType() {
        SplitLedger.build(ApplicationProvider.getApplicationContext())
        ActivityScenario.launch<NewEditTransactionActivity>(editIntent(SplitLedger.PAY_SPLIT)).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<View>(R.id.split_edit_text).performClick()
                val picker = shadowOf(activity).nextStartedActivity
                assertEquals(CategoryPickerActivity::class.java.name, picker.component?.className)
                assertEquals(Contract.CategoryType.INCOME, picker.getSerializableExtra(CategoryPickerActivity.ONLY_TYPE))
                partCategoryPicker(activity).setCategory(ledgerCategory(SplitLedger.BONUS))
                assertStartedCalculator(activity)
            }
        }
    }

    /** The expense page is the second when every type is shown, so it cannot pass by being first. */
    @Test
    fun thePartPickerShowsThePageOfTheMainCategorysType() {
        SplitLedger.build(ApplicationProvider.getApplicationContext())
        val picker = arrayOfNulls<Intent>(1)
        ActivityScenario.launch<NewEditTransactionActivity>(editIntent(SplitLedger.RECEIPT)).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<View>(R.id.split_edit_text).performClick()
                picker[0] = shadowOf(activity).nextStartedActivity
            }
        }
        ActivityScenario.launch<CategoryPickerActivity>(checkNotNull(picker[0])).use { scenario ->
            scenario.onActivity { activity ->
                val tabs = activity.findViewById<TabLayout>(R.id.tab_layout)
                assertEquals(1, tabs.tabCount)
                assertEquals(activity.getString(R.string.menu_category_tab_expense), tabs.getTabAt(0)?.text?.toString())
                val names = categoryPickerNames(activity)
                assertTrue(names.toString(), names.containsAll(listOf("Fuel", "Groceries")))
                assertFalse(names.toString(), names.contains("Salary") || names.contains("Bonus"))
            }
        }
    }

    @Test
    fun anEditedPartIsSavedAndTheOthersKept() {
        SplitLedger.build(ApplicationProvider.getApplicationContext())
        ActivityScenario.launch<NewEditTransactionActivity>(editIntent(SplitLedger.RECEIPT)).use { scenario ->
            scenario.onActivity { activity ->
                // the part flagged deleted is not listed
                assertEquals(listOf("Household", "Fuel"), partLabels(activity))
                choosePartAction(activity, 1, 1)
                partMoneyPicker(activity).setMoney(4000L)
                choosePartAction(activity, 0, 2)
                val note = ShadowDialog.getLatestDialog() as? AlertDialog ?: error("no note dialog")
                note.findViewById<EditText>(R.id.dialog_input_edit_text)?.setText("soap")
                note.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                shadowOf(Looper.getMainLooper()).idle()
                assertEquals(euro(10000L - 2000L - 4000L), remainingField(activity).getTextAsString())
                save(activity)
                assertTrue(activity.isFinishing)
            }
        }
        assertEquals(
            sortedOf(SplitLedger.HOUSEHOLD.toString() + ":2000:soap", SplitLedger.FUEL.toString() + ":4000:null"),
            storedParts(SplitLedger.RECEIPT)
        )
    }

    @Test
    fun aTotalBelowThePartsIsRefused() {
        SplitLedger.build(ApplicationProvider.getApplicationContext())
        ActivityScenario.launch<NewEditTransactionActivity>(editIntent(SplitLedger.RECEIPT)).use { scenario ->
            scenario.onActivity { activity ->
                // the parts are 2000 and 3000
                moneyPicker(activity).setMoney(2000L + 3000L - 1L)
                assertRefused(activity, R.string.error_split_total_below_parts)
                moneyPicker(activity).setMoney(2000L + 3000L)
                save(activity)
                assertTrue(activity.isFinishing)
            }
        }
        assertEquals(2000L + 3000L, moneyOf(SplitLedger.RECEIPT))
    }

    /** Added plainly, 2000 raised to the largest amount less 1000, and 3000, wrap below the total. */
    @Test
    fun partsAddingUpPastTheLargestAmountAreRefusedBeforeTheDatabase() {
        SplitLedger.build(ApplicationProvider.getApplicationContext())
        val row = rowsOf("transactions", "transaction_id = " + SplitLedger.RECEIPT)
        val parts = rowsOf("transaction_splits", "_transaction = " + SplitLedger.RECEIPT)
        ActivityScenario.launch<NewEditTransactionActivity>(editIntent(SplitLedger.RECEIPT)).use { scenario ->
            scenario.onActivity { activity ->
                choosePartAction(activity, 0, 1)
                partMoneyPicker(activity).setMoney(Long.MAX_VALUE - 1000L)
                assertRefused(activity, R.string.error_split_total_below_parts)
            }
        }
        assertEquals(row, rowsOf("transactions", "transaction_id = " + SplitLedger.RECEIPT))
        assertEquals(parts, rowsOf("transaction_splits", "_transaction = " + SplitLedger.RECEIPT))
    }

    /** Spare goes from another window, through the app's own delete, after the editor took it for a part. */
    @Test
    fun aNewTransactionWhosePartCategoryIsDeletedElsewhereIsRefusedThenSaved() {
        SplitLedger.build(ApplicationProvider.getApplicationContext())
        val intent = newItemIntent().apply {
            putExtra(NewEditTransactionActivity.WALLET_ID, SplitLedger.CASH)
        }
        val before = countTransactions()
        val partsBefore = countParts("1")
        val label = ArrayList<String>()
        ActivityScenario.launch<NewEditTransactionActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                categoryPicker(activity).setCategory(ledgerCategory(SplitLedger.GROCERIES))
                moneyPicker(activity).setMoney(9000L)
                addPart(activity, SplitLedger.SPARE, 2000L)
                addPart(activity, SplitLedger.FUEL, 1500L)
                assertEquals(
                    1,
                    resolver.delete(
                        ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORIES, SplitLedger.SPARE),
                        null,
                        null
                    )
                )
                assertRefused(activity, R.string.error_split_not_saved)
                label.addAll(partLabels(activity))
                assertRefused(activity, R.string.error_split_not_saved)
                choosePartAction(activity, 0, 0)
                partCategoryPicker(activity).setCategory(ledgerCategory(SplitLedger.HOUSEHOLD))
                save(activity)
                assertTrue(activity.isFinishing)
            }
        }
        assertEquals(listOf("Spare", "Fuel"), label)
        assertEquals(before + 1, countTransactions())
        assertEquals(partsBefore + 2, countParts("1"))
        assertEquals(
            sortedOf(SplitLedger.HOUSEHOLD.toString() + ":2000:null", SplitLedger.FUEL.toString() + ":1500:null"),
            storedParts(newestId())
        )
    }

    /** On an edit the row and its parts stay as they were, and removing the new part lets it save. */
    @Test
    fun anEditWhoseNewPartCategoryIsDeletedElsewhereIsRefusedThenSaved() {
        SplitLedger.build(ApplicationProvider.getApplicationContext())
        val row = rowsOf("transactions", "transaction_id = " + SplitLedger.RECEIPT)
        val parts = rowsOf("transaction_splits", "_transaction = " + SplitLedger.RECEIPT)
        ActivityScenario.launch<NewEditTransactionActivity>(editIntent(SplitLedger.RECEIPT)).use { scenario ->
            scenario.onActivity { activity ->
                moneyPicker(activity).setMoney(12000L)
                addPart(activity, SplitLedger.SPARE, 1000L)
                assertEquals(
                    1,
                    resolver.delete(
                        ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORIES, SplitLedger.SPARE),
                        null,
                        null
                    )
                )
                assertRefused(activity, R.string.error_split_not_saved)
                assertEquals(row, rowsOf("transactions", "transaction_id = " + SplitLedger.RECEIPT))
                assertEquals(parts, rowsOf("transaction_splits", "_transaction = " + SplitLedger.RECEIPT))
                choosePartAction(activity, 2, -1)
                save(activity)
                assertTrue(activity.isFinishing)
            }
        }
        assertEquals(12000L, moneyOf(SplitLedger.RECEIPT))
        assertEquals(
            sortedOf(SplitLedger.HOUSEHOLD.toString() + ":2000:null", SplitLedger.FUEL.toString() + ":3000:null"),
            storedParts(SplitLedger.RECEIPT)
        )
    }

    @Test
    fun aPartOfZeroIsRefused() {
        SplitLedger.build(ApplicationProvider.getApplicationContext())
        ActivityScenario.launch<NewEditTransactionActivity>(editIntent(SplitLedger.RECEIPT)).use { scenario ->
            scenario.onActivity { activity ->
                choosePartAction(activity, 0, 1)
                partMoneyPicker(activity).setMoney(0L)
                assertRefused(activity, R.string.error_split_part_not_positive)
            }
        }
    }

    /** A new main category of the other direction would leave the parts on the wrong side. */
    @Test
    fun aDirectionFlipOnASplitRowIsRefused() {
        SplitLedger.build(ApplicationProvider.getApplicationContext())
        ActivityScenario.launch<NewEditTransactionActivity>(editIntent(SplitLedger.RECEIPT)).use { scenario ->
            scenario.onActivity { activity ->
                categoryPicker(activity).setCategory(ledgerCategory(SplitLedger.SALARY))
                assertRefused(activity, R.string.error_split_part_wrong_type)
            }
        }
        assertEquals(SplitLedger.GROCERIES, longOf(SplitLedger.RECEIPT, Contract.Transaction.CATEGORY_ID))
        assertEquals(Contract.Direction.EXPENSE.toLong(), longOf(SplitLedger.RECEIPT, Contract.Transaction.DIRECTION))
    }

    /**
     * CROSSED holds a Salary part on an expense, so it reads as unsplit. It opens on that part,
     * is refused while the part stays, and removing it saves the row unsplit.
     */
    @Test
    fun aRowWithInvalidPartsOpensOnThemAndIsRefusedUntilRepaired() {
        SplitLedger.build(ApplicationProvider.getApplicationContext())
        ActivityScenario.launch<NewEditTransactionActivity>(editIntent(SplitLedger.CROSSED)).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(listOf("Salary"), partLabels(activity))
                assertRefused(activity, R.string.error_split_part_wrong_type)
                choosePartAction(activity, 0, -1)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.split_parts_layout).visibility)
                save(activity)
                assertTrue(activity.isFinishing)
            }
        }
        assertEquals(emptyList<String>(), storedParts(SplitLedger.CROSSED))
    }

    /** OVER holds parts of 800 and 700 on a total of 1000, and is saved once the total covers them. */
    @Test
    fun aRowWhosePartsPassItsTotalIsSavedOnceTheTotalCoversThem() {
        SplitLedger.build(ApplicationProvider.getApplicationContext())
        ActivityScenario.launch<NewEditTransactionActivity>(editIntent(SplitLedger.OVER)).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(listOf("Household", "Fuel"), partLabels(activity))
                assertRefused(activity, R.string.error_split_total_below_parts)
                moneyPicker(activity).setMoney(800L + 700L)
                save(activity)
                assertTrue(activity.isFinishing)
            }
        }
        assertEquals(
            sortedOf(SplitLedger.HOUSEHOLD.toString() + ":800:null", SplitLedger.FUEL.toString() + ":700:null"),
            storedParts(SplitLedger.OVER)
        )
    }

    /**
     * MoneyPicker keeps the minor units of the total when the wallet changes currency, and the
     * parts keep theirs with it, so 10000 with parts of 2000 and 3000 becomes 10000 yen with parts
     * of 2000 and 3000 yen.
     */
    @Test
    fun aWalletOfAnotherCurrencyKeepsThePartsInStepWithTheTotal() {
        SplitLedger.build(ApplicationProvider.getApplicationContext())
        ActivityScenario.launch<NewEditTransactionActivity>(editIntent(SplitLedger.RECEIPT)).use { scenario ->
            scenario.onActivity { activity ->
                val wallets = activity.supportFragmentManager.findFragmentByTag(WALLET_PICKER) as? WalletPicker
                    ?: error("no wallet picker")
                val currency = CurrencyManager.getCurrency("JPY") ?: error("no JPY currency")
                wallets.onWalletSelected(Wallet(SplitLedger.YEN, "Yen", null, currency, 0L, 0L))
                assertEquals(10000L, moneyPicker(activity).currentMoney)
                assertEquals(
                    MoneyFormatter.getInstance().getNotTintedString(currency, 10000L - 2000L - 3000L),
                    remainingField(activity).getTextAsString()
                )
                save(activity)
                assertTrue(activity.isFinishing)
            }
        }
        assertEquals(SplitLedger.YEN, longOf(SplitLedger.RECEIPT, Contract.Transaction.WALLET_ID))
        assertEquals(10000L, moneyOf(SplitLedger.RECEIPT))
        assertEquals(
            sortedOf(SplitLedger.HOUSEHOLD.toString() + ":2000:null", SplitLedger.FUEL.toString() + ":3000:null"),
            storedParts(SplitLedger.RECEIPT)
        )
    }

    @Test
    fun aCopyTakesThePartsAsPartsOfItsOwn() {
        SplitLedger.build(ApplicationProvider.getApplicationContext())
        val original = storedParts(SplitLedger.RECEIPT)
        val intent = newItemIntent().apply {
            putExtra(NewEditTransactionActivity.DUPLICATE_ID, SplitLedger.RECEIPT)
        }
        ActivityScenario.launch<NewEditTransactionActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(listOf("Household", "Fuel"), partLabels(activity))
                save(activity)
                assertTrue(activity.isFinishing)
            }
        }
        val copy = newestId()
        assertFalse(copy == SplitLedger.RECEIPT)
        assertEquals(sortedOf(SplitLedger.HOUSEHOLD.toString() + ":2000:null", SplitLedger.FUEL.toString() + ":3000:null"), storedParts(copy))
        assertEquals("the original keeps its own rows", original, storedParts(SplitLedger.RECEIPT))
        assertEquals(2L, countParts("_transaction = $copy AND split_id NOT IN (930, 931)"))
    }

    /** A part added, and one waiting for its category, both come back after a recreate. */
    @Test
    fun thePartsSurviveARecreate() {
        SplitLedger.build(ApplicationProvider.getApplicationContext())
        val intent = newItemIntent().apply {
            putExtra(NewEditTransactionActivity.WALLET_ID, SplitLedger.CASH)
        }
        ActivityScenario.launch<NewEditTransactionActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                categoryPicker(activity).setCategory(ledgerCategory(SplitLedger.GROCERIES))
                moneyPicker(activity).setMoney(9000L)
                addPart(activity, SplitLedger.HOUSEHOLD, 2000L)
                activity.findViewById<View>(R.id.split_edit_text).performClick()
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertEquals(listOf("Household"), partLabels(activity))
                assertEquals(euro(9000L - 2000L), remainingField(activity).getTextAsString())
                // the category picked for the part that was being added when the screen went
                partCategoryPicker(activity).setCategory(ledgerCategory(SplitLedger.FUEL))
                partMoneyPicker(activity).setMoney(1500L)
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertEquals(listOf("Household", "Fuel"), partLabels(activity))
                save(activity)
                assertTrue(activity.isFinishing)
            }
        }
        val id = newestId()
        assertEquals(sortedOf(SplitLedger.HOUSEHOLD.toString() + ":2000:null", SplitLedger.FUEL.toString() + ":1500:null"), storedParts(id))
    }

    /** Nothing but a plain transaction offers the action, and saving one of the others leaves its parts. */
    @Test
    fun onlyAPlainTransactionOffersTheSplit() {
        SplitLedger.build(ApplicationProvider.getApplicationContext())
        val debtParts = storedParts(SplitLedger.DEBT_ROW)
        ActivityScenario.launch<NewEditTransactionActivity>(editIntent(SplitLedger.DEBT_ROW)).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(View.GONE, activity.findViewById<View>(R.id.split_edit_text).visibility)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.split_parts_layout).visibility)
                save(activity)
            }
        }
        assertEquals(debtParts, storedParts(SplitLedger.DEBT_ROW))
        ActivityScenario.launch<NewEditTransactionActivity>(
            savingIntent(NewEditTransactionActivity.SAVING_DEPOSIT)
        ).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(View.GONE, activity.findViewById<View>(R.id.split_edit_text).visibility)
            }
        }
        ActivityScenario.launch<NewEditTransactionActivity>(
            debtIntent(insertDebt(euroWallet), NewEditTransactionActivity.DEBT_PAY)
        ).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(View.GONE, activity.findViewById<View>(R.id.split_edit_text).visibility)
            }
        }
        val model = newItemIntent().apply {
            putExtra(NewEditTransactionActivity.TYPE, NewEditTransactionActivity.TYPE_MODEL)
            putExtra(NewEditTransactionActivity.MODEL_ID, insertModel(4200L, euroWallet))
        }
        ActivityScenario.launch<NewEditTransactionActivity>(model).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.split_edit_text).visibility)
            }
        }
    }

    @Test
    fun aRowARecurrenceWroteHidesTheSplitAndACopyOfItOffersIt() {
        SplitLedger.build(ApplicationProvider.getApplicationContext())
        val db = SplitLedger.database(ApplicationProvider.getApplicationContext())
        db.execSQL(
            "INSERT INTO recurrent_transactions (recurrent_transaction_id, recurrent_transaction_money, " +
                "recurrent_transaction_category, recurrent_transaction_direction, " +
                "recurrent_transaction_wallet, recurrent_transaction_start_date, " +
                "recurrent_transaction_last_occurrence, recurrent_transaction_rule, uuid, last_edit, " +
                "deleted) VALUES (960, 10000, ?, ?, ?, '2026-01-15', '2026-01-15', 'FREQ=MONTHLY', " +
                "'recurrence-960', 0, 0)",
            arrayOf<Any>(SplitLedger.GROCERIES, Contract.Direction.EXPENSE, SplitLedger.CASH)
        )
        db.execSQL("UPDATE transactions SET transaction_recurrence = 960 WHERE transaction_id = " + SplitLedger.RECEIPT)
        ActivityScenario.launch<NewEditTransactionActivity>(editIntent(SplitLedger.RECEIPT)).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(View.GONE, activity.findViewById<View>(R.id.split_edit_text).visibility)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.split_parts_layout).visibility)
            }
        }
        val copy = newItemIntent().apply {
            putExtra(NewEditTransactionActivity.DUPLICATE_ID, SplitLedger.RECEIPT)
        }
        ActivityScenario.launch<NewEditTransactionActivity>(copy).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.split_edit_text).visibility)
                assertEquals(listOf("Household", "Fuel"), partLabels(activity))
            }
        }
    }

    // fixtures

    private fun insertCategory(): Long {
        val category = ContentValues().apply {
            put(Contract.Category.NAME, "Groceries")
            put(Contract.Category.ICON, ICON)
            put(Contract.Category.TYPE, Contract.CategoryType.EXPENSE.value)
            put(Contract.Category.SHOW_REPORT, true)
        }
        val uri = checkNotNull(resolver.insert(DataContentProvider.CONTENT_CATEGORIES, category))
        return ContentUris.parseId(uri)
    }

    private fun insertPerson(name: String): Long {
        val person = ContentValues().apply {
            put(Contract.Person.NAME, name)
            put(Contract.Person.ICON, ICON)
        }
        val uri = checkNotNull(resolver.insert(DataContentProvider.CONTENT_PEOPLE, person))
        return ContentUris.parseId(uri)
    }

    private fun insertAttachment(): Long {
        val attachment = ContentValues().apply {
            put(Contract.Attachment.FILE, "receipt.jpg")
            put(Contract.Attachment.NAME, "receipt.jpg")
            put(Contract.Attachment.TYPE, "image/jpeg")
            put(Contract.Attachment.SIZE, 1234L)
        }
        val uri = checkNotNull(resolver.insert(DataContentProvider.CONTENT_ATTACHMENTS, attachment))
        return ContentUris.parseId(uri)
    }

    private fun insertPlace(): Long {
        val place = ContentValues().apply {
            put(Contract.Place.NAME, "Market")
            put(Contract.Place.ICON, ICON)
        }
        val uri = checkNotNull(resolver.insert(DataContentProvider.CONTENT_PLACES, place))
        return ContentUris.parseId(uri)
    }

    private fun insertEvent(): Long {
        val event = ContentValues().apply {
            put(Contract.Event.NAME, "Fair")
            put(Contract.Event.ICON, ICON)
            put(Contract.Event.START_DATE, DateUtils.getSQLDateString(daysFromNow(-30)))
            put(Contract.Event.END_DATE, DateUtils.getSQLDateString(daysFromNow(30)))
        }
        val uri = checkNotNull(resolver.insert(DataContentProvider.CONTENT_EVENTS, event))
        return ContentUris.parseId(uri)
    }

    private fun insertModel(money: Long, walletId: Long): Long {
        return insertModel(money, walletId, insertCategory(), null, null)
    }

    private fun insertModel(
        money: Long,
        walletId: Long,
        categoryId: Long,
        placeId: Long?,
        eventId: Long?
    ): Long {
        val values = ContentValues().apply {
            put(Contract.TransactionModel.MONEY, money)
            put(Contract.TransactionModel.DESCRIPTION, "Weekly shop")
            put(Contract.TransactionModel.CATEGORY_ID, categoryId)
            put(Contract.TransactionModel.DIRECTION, Contract.Direction.EXPENSE)
            put(Contract.TransactionModel.WALLET_ID, walletId)
            put(Contract.TransactionModel.PLACE_ID, placeId)
            put(Contract.TransactionModel.EVENT_ID, eventId)
            put(Contract.TransactionModel.CONFIRMED, true)
            put(Contract.TransactionModel.COUNT_IN_TOTAL, true)
        }
        val uri = checkNotNull(resolver.insert(DataContentProvider.CONTENT_TRANSACTION_MODELS, values))
        return ContentUris.parseId(uri)
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

    private fun insertSaving(startMoney: Long, endMoney: Long, walletId: Long): Long {
        val values = ContentValues().apply {
            put(Contract.Saving.DESCRIPTION, "Trip")
            put(Contract.Saving.ICON, ICON)
            put(Contract.Saving.START_MONEY, startMoney)
            put(Contract.Saving.END_MONEY, endMoney)
            put(Contract.Saving.WALLET_ID, walletId)
            put(Contract.Saving.COMPLETE, false)
        }
        val uri = checkNotNull(resolver.insert(DataContentProvider.CONTENT_SAVINGS, values))
        return ContentUris.parseId(uri)
    }

    private fun insertSavingRow(
        money: Long,
        date: Date,
        tag: String,
        confirmed: Boolean,
        walletId: Long
    ): Long {
        return insertSavingRow(saving, money, date, tag, confirmed, walletId)
    }

    private fun insertSavingRow(
        savingId: Long,
        money: Long,
        date: Date,
        tag: String,
        confirmed: Boolean,
        walletId: Long
    ): Long {
        val values = ContentValues().apply {
            put(Contract.Transaction.MONEY, money)
            put(Contract.Transaction.DATE, DateUtils.getSQLDateTimeString(date))
            put(Contract.Transaction.DESCRIPTION, tag)
            put(Contract.Transaction.CATEGORY_ID, systemCategory(tag))
            put(
                Contract.Transaction.DIRECTION,
                if (Contract.CategoryTag.SAVING_WITHDRAW == tag) Contract.Direction.INCOME else Contract.Direction.EXPENSE
            )
            put(Contract.Transaction.TYPE, NewEditTransactionActivity.TYPE_SAVING)
            put(Contract.Transaction.WALLET_ID, walletId)
            put(Contract.Transaction.SAVING_ID, savingId)
            put(Contract.Transaction.CONFIRMED, confirmed)
            put(Contract.Transaction.COUNT_IN_TOTAL, true)
        }
        val uri = checkNotNull(resolver.insert(DataContentProvider.CONTENT_TRANSACTIONS, values))
        return ContentUris.parseId(uri)
    }

    private fun insertDebt(walletId: Long): Long {
        return insertDebt(walletId, null, null)
    }

    private fun insertDebt(walletId: Long, personId: Long?): Long {
        return insertDebt(walletId, personId, null)
    }

    /** A debt of 10000 named Rent with one confirmed payment of 2500 made yesterday. */
    private fun insertPartlyPaidDebt(): Long {
        val values = ContentValues().apply {
            put(Contract.Debt.TYPE, Contract.DebtType.DEBT.value)
            put(Contract.Debt.ICON, ICON)
            put(Contract.Debt.DESCRIPTION, "Rent")
            put(Contract.Debt.DATE, DateUtils.getSQLDateString(daysFromNow(-3)))
            put(Contract.Debt.WALLET_ID, euroWallet)
            put(Contract.Debt.MONEY, 10000L)
            put(Contract.Debt.ARCHIVED, false)
            put(Contract.Debt.INSERT_MASTER_TRANSACTION, true)
        }
        val debtUri = checkNotNull(resolver.insert(DataContentProvider.CONTENT_DEBTS, values))
        val debt = ContentUris.parseId(debtUri)
        val payment = ContentValues().apply {
            put(Contract.Transaction.MONEY, 2500L)
            put(Contract.Transaction.DATE, DateUtils.getSQLDateTimeString(daysFromNow(-1)))
            put(Contract.Transaction.DESCRIPTION, "Rent")
            put(Contract.Transaction.CATEGORY_ID, systemCategory(Contract.CategoryTag.PAID_DEBT))
            put(Contract.Transaction.DIRECTION, Contract.Direction.EXPENSE)
            put(Contract.Transaction.TYPE, NewEditTransactionActivity.TYPE_DEBT)
            put(Contract.Transaction.WALLET_ID, euroWallet)
            put(Contract.Transaction.DEBT_ID, debt)
            put(Contract.Transaction.CONFIRMED, true)
            put(Contract.Transaction.COUNT_IN_TOTAL, true)
        }
        resolver.insert(DataContentProvider.CONTENT_TRANSACTIONS, payment)
        return debt
    }

    private fun insertDebt(walletId: Long, personId: Long?, placeId: Long?): Long {
        val values = ContentValues().apply {
            if (personId != null) {
                put(
                    Contract.Debt.PEOPLE_IDS,
                    Contract.getObjectIds(arrayOf(Person(personId, "Bob", null)))
                )
            }
            put(Contract.Debt.PLACE_ID, placeId)
            put(Contract.Debt.TYPE, Contract.DebtType.DEBT.value)
            put(Contract.Debt.ICON, ICON)
            put(Contract.Debt.DESCRIPTION, "Loan")
            put(Contract.Debt.DATE, DateUtils.getSQLDateString(daysFromNow(-3)))
            put(Contract.Debt.WALLET_ID, walletId)
            put(Contract.Debt.MONEY, 20000L)
            put(Contract.Debt.ARCHIVED, false)
            put(Contract.Debt.INSERT_MASTER_TRANSACTION, true)
        }
        val debtUri = checkNotNull(resolver.insert(DataContentProvider.CONTENT_DEBTS, values))
        return ContentUris.parseId(debtUri)
    }

    private fun systemCategory(tag: String): Long {
        val cursor = checkNotNull(
            resolver.query(
                DataContentProvider.CONTENT_CATEGORIES,
                arrayOf(Contract.Category.ID),
                "${Contract.Category.TAG} = ?",
                arrayOf(tag),
                null
            )
        )
        assertTrue("no system category tagged $tag", cursor.moveToFirst())
        val id = cursor.getLong(0)
        cursor.close()
        return id
    }

    private fun masterTransactionOf(debt: Long): Long {
        val cursor = checkNotNull(
            resolver.query(
                DataContentProvider.CONTENT_TRANSACTIONS,
                arrayOf(Contract.Transaction.ID),
                "${Contract.Transaction.DEBT_ID} = ? AND ${Contract.Transaction.CATEGORY_TAG} = ?",
                arrayOf(debt.toString(), Contract.CategoryTag.DEBT),
                null
            )
        )
        assertTrue("the debt has no row of its own", cursor.moveToFirst())
        val id = cursor.getLong(0)
        cursor.close()
        return id
    }

    private fun countTransactions(): Int {
        val cursor = checkNotNull(
            resolver.query(
                DataContentProvider.CONTENT_TRANSACTIONS,
                arrayOf(Contract.Transaction.ID),
                null,
                null,
                null
            )
        )
        val count = cursor.count
        cursor.close()
        return count
    }

    private fun newestTransaction(): Cursor {
        val cursor = checkNotNull(
            resolver.query(
                DataContentProvider.CONTENT_TRANSACTIONS,
                arrayOf(
                    Contract.Transaction.ID,
                    Contract.Transaction.MONEY,
                    Contract.Transaction.DATE,
                    Contract.Transaction.WALLET_ID,
                    Contract.Transaction.SAVING_ID,
                    Contract.Transaction.DEBT_ID,
                    Contract.Transaction.TYPE,
                    Contract.Transaction.DIRECTION,
                    Contract.Transaction.CATEGORY_TAG,
                    Contract.Transaction.CATEGORY_ID,
                    Contract.Transaction.PLACE_ID,
                    Contract.Transaction.EVENT_ID
                ),
                null,
                null,
                "${Contract.Transaction.ID} DESC"
            )
        )
        assertTrue(cursor.moveToFirst())
        return cursor
    }

    private fun moneyOf(transactionId: Long): Long {
        val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSACTIONS, transactionId)
        val cursor = checkNotNull(
            resolver.query(uri, arrayOf(Contract.Transaction.MONEY), null, null, null)
        )
        assertTrue(cursor.moveToFirst())
        val money = cursor.getLong(0)
        cursor.close()
        return money
    }

    private fun dateOf(transactionId: Long): String {
        val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSACTIONS, transactionId)
        val cursor = checkNotNull(
            resolver.query(uri, arrayOf(Contract.Transaction.DATE), null, null, null)
        )
        assertTrue(cursor.moveToFirst())
        val date = cursor.getString(0)
        cursor.close()
        return date
    }

    private fun savingIsComplete(): Boolean {
        val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_SAVINGS, saving)
        val cursor = checkNotNull(
            resolver.query(uri, arrayOf(Contract.Saving.COMPLETE), null, null, null)
        )
        assertTrue(cursor.moveToFirst())
        val complete = cursor.getInt(0) == 1
        cursor.close()
        return complete
    }

    private fun assertRefused(activity: NewEditTransactionActivity, message: Int) {
        val before = countTransactions()
        val parts = countParts("1")
        save(activity)
        assertFalse(activity.isFinishing)
        val dialog = ShadowDialog.getLatestDialog() as? AlertDialog ?: error("no refused dialog")
        assertTrue(dialog.isShowing)
        val text = dialog.findViewById<TextView>(android.R.id.message)
        assertEquals(activity.getString(message), text?.text?.toString())
        dialog.dismiss()
        assertEquals(before, countTransactions())
        assertEquals(parts, countParts("1"))
    }

    private fun storedParts(transaction: Long): List<String> {
        val parts = ArrayList<String>()
        val cursor = SplitLedger.database(ApplicationProvider.getApplicationContext()).rawQuery(
            "SELECT split_category || ':' || split_money || ':' || IFNULL(split_note, 'null') FROM " +
                "transaction_splits WHERE deleted = 0 AND _transaction = $transaction",
            null
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                parts.add(c.getString(0))
            }
        }
        Collections.sort(parts)
        return parts
    }

    private fun rowsOf(table: String, selection: String): List<String> {
        val rows = ArrayList<String>()
        val cursor = SplitLedger.database(ApplicationProvider.getApplicationContext()).rawQuery(
            "SELECT * FROM $table WHERE $selection",
            null
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                val columns = ArrayList<String>()
                for (i in 0 until c.columnCount) {
                    columns.add(c.getColumnName(i) + "=" + c.getString(i))
                }
                rows.add(columns.toString())
            }
        }
        Collections.sort(rows)
        return rows
    }

    private fun countParts(selection: String): Long {
        val cursor = SplitLedger.database(ApplicationProvider.getApplicationContext()).rawQuery(
            "SELECT COUNT(*) FROM transaction_splits WHERE $selection",
            null
        )
        cursor.use { c ->
            assertTrue(c.moveToFirst())
            return c.getLong(0)
        }
    }

    private fun longOf(transactionId: Long, column: String): Long {
        val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSACTIONS, transactionId)
        val cursor = resolver.query(uri, arrayOf(column), null, null, null)
        cursor?.use { c ->
            assertTrue(c.moveToFirst())
            return c.getLong(0)
        } ?: error("cursor was null")
    }

    private fun newestId(): Long {
        val cursor = newestTransaction()
        cursor.use { c ->
            return c.getLong(0)
        }
    }

    private fun savingIntent(action: Int): Intent {
        return newItemIntent().apply {
            putExtra(NewEditTransactionActivity.TYPE, NewEditTransactionActivity.TYPE_SAVING)
            putExtra(NewEditTransactionActivity.SAVING_ID, saving)
            putExtra(NewEditTransactionActivity.SAVING_ACTION, action)
        }
    }

    companion object {
        private const val DAY = 24L * 60L * 60L * 1000L
        private const val ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}"

        private const val MONEY_PICKER = "NewEditTransactionActivity::Tag::MoneyPicker"
        private const val DATETIME_PICKER = "NewEditTransactionActivity::Tag::DateTimePicker"
        private const val CATEGORY_PICKER = "NewEditTransactionActivity::Tag::CategoryPicker"
        private const val PERSON_PICKER = "NewEditTransactionActivity::Tag::PersonPicker"
        private const val ATTACHMENT_PICKER = "NewEditTransactionActivity::Tag::AttachmentPicker"
        private const val PART_CATEGORY_PICKER = "NewEditTransactionActivity::Tag::PartCategoryPicker"
        private const val PART_MONEY_PICKER = "NewEditTransactionActivity::Tag::PartMoneyPicker"
        private const val WALLET_PICKER = "NewEditTransactionActivity::Tag::WalletPicker"

        private fun daysFromNow(days: Int): Date {
            return Date(System.currentTimeMillis() + days * DAY)
        }

        private fun newItemIntent(): Intent {
            return Intent(ApplicationProvider.getApplicationContext(), NewEditTransactionActivity::class.java).apply {
                putExtra(NewEditItemActivity.MODE, NewEditItemActivity.Mode.NEW_ITEM)
            }
        }

        private fun debtIntent(debt: Long, action: Int): Intent {
            return newItemIntent().apply {
                putExtra(NewEditTransactionActivity.TYPE, NewEditTransactionActivity.TYPE_DEBT)
                putExtra(NewEditTransactionActivity.DEBT_ID, debt)
                putExtra(NewEditTransactionActivity.DEBT_ACTION, action)
            }
        }

        private fun editIntent(transactionId: Long): Intent {
            return Intent(ApplicationProvider.getApplicationContext(), NewEditTransactionActivity::class.java).apply {
                putExtra(NewEditItemActivity.MODE, NewEditItemActivity.Mode.EDIT_ITEM)
                putExtra(NewEditItemActivity.ID, transactionId)
            }
        }

        private fun moneyPicker(activity: NewEditTransactionActivity): MoneyPicker {
            return checkNotNull(activity.supportFragmentManager.findFragmentByTag(MONEY_PICKER) as? MoneyPicker)
        }

        private fun dateTimePicker(activity: NewEditTransactionActivity): DateTimePicker {
            return checkNotNull(activity.supportFragmentManager.findFragmentByTag(DATETIME_PICKER) as? DateTimePicker)
        }

        private fun categoryPicker(activity: NewEditTransactionActivity): CategoryPicker {
            return checkNotNull(activity.supportFragmentManager.findFragmentByTag(CATEGORY_PICKER) as? CategoryPicker)
        }

        private fun personPicker(activity: NewEditTransactionActivity): PersonPicker {
            return checkNotNull(activity.supportFragmentManager.findFragmentByTag(PERSON_PICKER) as? PersonPicker)
        }

        private fun attachmentPicker(activity: NewEditTransactionActivity): AttachmentPicker {
            return checkNotNull(activity.supportFragmentManager.findFragmentByTag(ATTACHMENT_PICKER) as? AttachmentPicker)
        }

        private fun walletField(activity: NewEditTransactionActivity): String {
            return activity.findViewById<MaterialEditText>(R.id.wallet_edit_text).getTextAsString()
        }

        private fun descriptionField(activity: NewEditTransactionActivity): String {
            return activity.findViewById<MaterialEditText>(R.id.description_edit_text).getTextAsString()
        }

        private fun save(activity: NewEditTransactionActivity) {
            val toolbar = activity.findViewById<Toolbar>(R.id.primary_toolbar)
            val item = checkNotNull(toolbar.menu.findItem(R.id.action_save_changes))
            activity.onMenuItemClick(item)
        }

        private fun ledgerCategory(id: Long): Category {
            val income = id == SplitLedger.SALARY || id == SplitLedger.BONUS
            return Category(
                id,
                SplitLedger.NAMES[id],
                null,
                if (income) Contract.CategoryType.INCOME else Contract.CategoryType.EXPENSE
            )
        }

        private fun partCategoryPicker(activity: NewEditTransactionActivity): CategoryPicker {
            return checkNotNull(activity.supportFragmentManager.findFragmentByTag(PART_CATEGORY_PICKER) as? CategoryPicker)
        }

        private fun partMoneyPicker(activity: NewEditTransactionActivity): MoneyPicker {
            return checkNotNull(activity.supportFragmentManager.findFragmentByTag(PART_MONEY_PICKER) as? MoneyPicker)
        }

        private fun addPart(activity: NewEditTransactionActivity, category: Long, money: Long) {
            activity.findViewById<View>(R.id.split_edit_text).performClick()
            partCategoryPicker(activity).setCategory(ledgerCategory(category))
            partMoneyPicker(activity).setMoney(money)
        }

        private fun choosePartAction(activity: NewEditTransactionActivity, part: Int, action: Int) {
            val parts = activity.findViewById<ViewGroup>(R.id.split_parts_layout)
            parts.getChildAt(part).performClick()
            val dialog = ShadowDialog.getLatestDialog() as? AlertDialog ?: error("no dialog")
            if (action < 0) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
            } else {
                val list = dialog.listView ?: error("no list view in dialog")
                val itemId = list.adapter?.getItemId(action) ?: 0L
                list.performItemClick(list, action, itemId)
            }
            shadowOf(Looper.getMainLooper()).idle()
        }

        private fun partLabels(activity: NewEditTransactionActivity): List<String> {
            val parts = activity.findViewById<ViewGroup>(R.id.split_parts_layout)
            val labels = ArrayList<String>()
            for (i in 0 until parts.childCount) {
                val child = parts.getChildAt(i) as? MaterialEditText
                labels.add(child?.floatingLabelText.orEmpty())
            }
            return labels
        }

        private fun remainingField(activity: NewEditTransactionActivity): MaterialEditText {
            return activity.findViewById(R.id.split_remaining_edit_text)
        }

        private fun remainingHint(activity: NewEditTransactionActivity, category: String): String {
            return activity.getString(R.string.hint_split_remaining, category)
        }

        private fun euro(money: Long): String {
            val currency = CurrencyManager.getCurrency("EUR") ?: error("EUR is null")
            return MoneyFormatter.getInstance().getNotTintedString(currency, money)
        }

        private fun sortedOf(vararg rows: String): List<String> {
            val list = ArrayList(listOf(*rows))
            Collections.sort(list)
            return list
        }

        private fun categoryPickerNames(activity: CategoryPickerActivity): List<String> {
            val list = activity.findViewById<RecyclerView>(R.id.recycler_view)
            var i = 0
            while (i < 200 && (list.adapter == null || (list.adapter?.itemCount ?: 0) == 0)) {
                shadowOf(Looper.getMainLooper()).idle()
                try {
                    Thread.sleep(25)
                } catch (e: InterruptedException) {
                    throw AssertionError(e)
                }
                i++
            }
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("the categories never loaded", View.VISIBLE, list.visibility)
            val decor = activity.window.decorView
            val metrics = decor.resources.displayMetrics
            decor.measure(
                View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.EXACTLY)
            )
            decor.layout(0, 0, decor.measuredWidth, decor.measuredHeight)
            val names = ArrayList<String>()
            for (childIdx in 0 until list.childCount) {
                names.add(list.getChildAt(childIdx).findViewById<TextView>(R.id.name_text_view).text.toString())
            }
            return names
        }

        private fun assertStartedCalculator(activity: NewEditTransactionActivity) {
            val started = shadowOf(activity).nextStartedActivity
            assertEquals(CalculatorActivity::class.java.name, started.component?.className)
            assertNull(shadowOf(activity).nextStartedActivity)
        }
    }
}
