package com.oriondev.moneywallet.ui.activity

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.Attachment
import com.oriondev.moneywallet.model.Category
import com.oriondev.moneywallet.model.Person
import com.oriondev.moneywallet.picker.AttachmentPicker
import com.oriondev.moneywallet.picker.CategoryPicker
import com.oriondev.moneywallet.picker.DateTimePicker
import com.oriondev.moneywallet.picker.MoneyPicker
import com.oriondev.moneywallet.picker.PersonPicker
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.TestDatabases
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.view.text.MaterialEditText
import com.oriondev.moneywallet.utils.CurrencyManager
import com.oriondev.moneywallet.utils.DateUtils
import com.oriondev.moneywallet.utils.IconLoader
import com.oriondev.moneywallet.utils.MoneyFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowDialog
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
                    activity.findViewById<MaterialEditText>(R.id.people_edit_text).textAsString
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
            return activity.findViewById<MaterialEditText>(R.id.wallet_edit_text).textAsString
        }

        private fun descriptionField(activity: NewEditTransactionActivity): String {
            return activity.findViewById<MaterialEditText>(R.id.description_edit_text).textAsString
        }

        private fun save(activity: NewEditTransactionActivity) {
            val toolbar = activity.findViewById<Toolbar>(R.id.primary_toolbar)
            val item = checkNotNull(toolbar.menu.findItem(R.id.action_save_changes))
            activity.onMenuItemClick(item)
        }
    }
}
