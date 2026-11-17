package com.oriondev.moneywallet.storage.database.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import androidx.test.core.app.ApplicationProvider
import com.oriondev.moneywallet.model.CurrencyUnit
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.TestDatabases
import com.oriondev.moneywallet.utils.CurrencyManager
import com.oriondev.moneywallet.utils.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException
import java.util.Date

/**
 * Two things are pinned here.
 *
 * The first is chooseCategory, run for real against a mocked cursor. It is the half of the lookup
 * that decides which matched category an imported row is filed on. An income or expense category
 * is its direction, but a system category is not: the transaction editor derives one from the
 * category and writes it over the row on every save, so a row given a system category whose tag
 * implies the other direction is flipped the first time it is opened and saved, and the wallet
 * total moves by twice the amount.
 *
 * The second is the lookup around it, driven for real. The importer is run through the content
 * provider on a fresh database seeded the way a first launch seeds it, and the transaction row it
 * writes is read back out of the same provider, so the projection, the selection arguments and the
 * line that hands the cursor to the guard are all covered by what lands in the table.
 */
@RunWith(RobolectricTestRunner::class)
class AbstractDataImporterTest {

    private lateinit var context: Context
    private lateinit var resolver: ContentResolver
    private lateinit var importer: TestImporter
    private lateinit var euro: CurrencyUnit
    private var taxId: Long = 0
    private var taxName: String? = null
    private var transferId: Long = 0
    private var transferName: String? = null
    private var food: Long = 0

    /** AbstractDataImporter is abstract, so something concrete has to exist before insertTransaction can be called. */
    private class TestImporter(context: Context) : AbstractDataImporter(context, File("unused.csv")) {
        override fun importData() {}
        override fun getRoundedAmounts(): Int = 0
        override fun close() {}
    }

    @Before
    @Throws(IOException::class)
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        TestDatabases.useFreshDatabase(context)
        resolver = context.contentResolver
        importer = TestImporter(context)
        val foundEuro = CurrencyManager.getCurrency("EUR")
        assertNotNull("no EUR currency to import against", foundEuro)
        euro = foundEuro ?: return
        // the seeded names come from string resources and the ids from the order the seed runs
        // in, so both are read out of the database and neither is written down here
        val cursor = resolver.query(
            DataContentProvider.CONTENT_CATEGORIES,
            arrayOf(
                Contract.Category.ID,
                Contract.Category.NAME,
                Contract.Category.TYPE,
                Contract.Category.TAG
            ),
            null,
            null,
            null
        )
        assertNotNull(cursor)
        cursor?.use {
            while (it.moveToNext()) {
                val tag = it.getString(it.getColumnIndex(Contract.Category.TAG))
                if (Contract.CategoryTag.TAX == tag) {
                    taxId = it.getLong(it.getColumnIndex(Contract.Category.ID))
                    taxName = it.getString(it.getColumnIndex(Contract.Category.NAME))
                } else if (Contract.CategoryTag.TRANSFER == tag) {
                    transferId = it.getLong(it.getColumnIndex(Contract.Category.ID))
                    transferName = it.getString(it.getColumnIndex(Contract.Category.NAME))
                }
            }
        }
        if (taxName == null || transferName == null) {
            fail("the fresh database does not carry a seeded tax and transfer category")
        }
        food = insertCategory("Food", Contract.CategoryType.EXPENSE)
    }

    private fun cursorOver(vararg rows: Array<Any?>): Cursor {
        val cursor = mock(Cursor::class.java)
        `when`(cursor.getColumnIndex(Contract.Category.TYPE)).thenReturn(TYPE)
        `when`(cursor.getColumnIndex(Contract.Category.TAG)).thenReturn(TAG)
        `when`(cursor.getColumnIndex(Contract.Category.ID)).thenReturn(ID)
        val at = intArrayOf(-1)
        `when`(cursor.moveToNext()).thenAnswer { ++at[0] < rows.size }
        `when`(cursor.moveToFirst()).thenAnswer {
            at[0] = 0
            rows.isNotEmpty()
        }
        `when`(cursor.moveToPosition(-1)).thenAnswer {
            at[0] = -1
            false
        }
        `when`(cursor.getLong(ID)).thenAnswer { rows[at[0]][0] as Long }
        `when`(cursor.getInt(TYPE)).thenAnswer { (rows[at[0]][1] as Contract.CategoryType).value }
        `when`(cursor.getString(TAG)).thenAnswer { rows[at[0]][2] as? String }
        return cursor
    }

    private fun row(id: Long, type: Contract.CategoryType, tag: String?): Array<Any?> {
        return arrayOf(id, type, tag)
    }

    @Test
    fun anOrdinaryCategoryHoldsARowOfItsOwnDirection() {
        assertEquals(
            7L,
            AbstractDataImporter.chooseCategory(
                cursorOver(row(7L, Contract.CategoryType.EXPENSE, null)),
                Contract.Direction.EXPENSE
            )
        )
        assertEquals(
            7L,
            AbstractDataImporter.chooseCategory(
                cursorOver(row(7L, Contract.CategoryType.INCOME, null)),
                Contract.Direction.INCOME
            )
        )
        assertEquals(
            "an expense category cannot hold an income row",
            AbstractDataImporter.NO_CATEGORY,
            AbstractDataImporter.chooseCategory(
                cursorOver(row(7L, Contract.CategoryType.EXPENSE, null)),
                Contract.Direction.INCOME
            )
        )
    }

    @Test
    fun aTagWithNoKnownDirectionIsRefusedForBoth() {
        for (direction in intArrayOf(Contract.Direction.EXPENSE, Contract.Direction.INCOME)) {
            assertEquals(
                "the transfer tag has no direction of its own",
                AbstractDataImporter.NO_CATEGORY,
                AbstractDataImporter.chooseCategory(
                    cursorOver(row(1L, Contract.CategoryType.SYSTEM, Contract.CategoryTag.TRANSFER)),
                    direction
                )
            )
            assertEquals(
                "a system row with no tag is not one this build seeded",
                AbstractDataImporter.NO_CATEGORY,
                AbstractDataImporter.chooseCategory(
                    cursorOver(row(1L, Contract.CategoryType.SYSTEM, null)),
                    direction
                )
            )
            assertEquals(
                "nor is one carrying a tag this build does not know, which a restore can write and a later version can introduce",
                AbstractDataImporter.NO_CATEGORY,
                AbstractDataImporter.chooseCategory(
                    cursorOver(row(1L, Contract.CategoryType.SYSTEM, "system::tenth")),
                    direction
                )
            )
            assertEquals(
                "an empty tag is not null and must be refused the same way",
                AbstractDataImporter.NO_CATEGORY,
                AbstractDataImporter.chooseCategory(
                    cursorOver(row(1L, Contract.CategoryType.SYSTEM, "")),
                    direction
                )
            )
        }
    }

    @Test
    fun everyKnownTagIsOneTheDirectionLookupAnswersFor() {
        val expense = arrayOf(
            Contract.CategoryTag.CREDIT,
            Contract.CategoryTag.PAID_DEBT,
            Contract.CategoryTag.SAVING_DEPOSIT,
            Contract.CategoryTag.TAX,
            Contract.CategoryTag.TRANSFER_TAX
        )
        val income = arrayOf(
            Contract.CategoryTag.DEBT,
            Contract.CategoryTag.PAID_CREDIT,
            Contract.CategoryTag.SAVING_WITHDRAW
        )
        for (tag in expense) {
            assertEquals(
                "$tag is an expense",
                3L,
                AbstractDataImporter.chooseCategory(
                    cursorOver(row(3L, Contract.CategoryType.SYSTEM, tag)),
                    Contract.Direction.EXPENSE
                )
            )
            assertEquals(
                "$tag must not hold an income row",
                AbstractDataImporter.NO_CATEGORY,
                AbstractDataImporter.chooseCategory(
                    cursorOver(row(3L, Contract.CategoryType.SYSTEM, tag)),
                    Contract.Direction.INCOME
                )
            )
        }
        for (tag in income) {
            assertEquals(
                "$tag is an income",
                3L,
                AbstractDataImporter.chooseCategory(
                    cursorOver(row(3L, Contract.CategoryType.SYSTEM, tag)),
                    Contract.Direction.INCOME
                )
            )
            assertEquals(
                "$tag must not hold an expense row",
                AbstractDataImporter.NO_CATEGORY,
                AbstractDataImporter.chooseCategory(
                    cursorOver(row(3L, Contract.CategoryType.SYSTEM, tag)),
                    Contract.Direction.EXPENSE
                )
            )
        }
    }

    @Test
    fun aRefusedCandidateIsPassedOverRatherThanEndingTheSearch() {
        assertEquals(
            "a system row the guard refuses must not hide a category the user made behind it",
            40L,
            AbstractDataImporter.chooseCategory(
                cursorOver(
                    row(8L, Contract.CategoryType.SYSTEM, Contract.CategoryTag.SAVING_DEPOSIT),
                    row(40L, Contract.CategoryType.INCOME, null)
                ),
                Contract.Direction.INCOME
            )
        )
        assertEquals(
            "the first candidate that holds the row wins, and the query orders them newest first",
            40L,
            AbstractDataImporter.chooseCategory(
                cursorOver(
                    row(40L, Contract.CategoryType.EXPENSE, null),
                    row(8L, Contract.CategoryType.SYSTEM, Contract.CategoryTag.SAVING_DEPOSIT)
                ),
                Contract.Direction.EXPENSE
            )
        )
        assertEquals(
            "an empty cursor finds nothing",
            AbstractDataImporter.NO_CATEGORY,
            AbstractDataImporter.chooseCategory(cursorOver(), Contract.Direction.EXPENSE)
        )
    }

    @Test
    fun aCursorSomebodyElseAlreadyMovedStillFindsTheFirstRow() {
        val cursor = cursorOver(row(40L, Contract.CategoryType.EXPENSE, null))
        cursor.moveToFirst()
        assertEquals(40L, AbstractDataImporter.chooseCategory(cursor, Contract.Direction.EXPENSE))
    }

    @Test
    fun anExpenseRowNamingTheSeededTaxCategoryLandsOnIt() {
        val before = countCategories()
        val nonNullTaxName = taxName ?: return
        importRow(nonNullTaxName, Contract.Direction.EXPENSE)
        val transaction = newestTransaction()
        try {
            assertEquals(
                "the row did not reach the seeded tax category",
                taxId,
                transaction.getLong(transaction.getColumnIndex(Contract.Transaction.CATEGORY_ID))
            )
            assertEquals(
                Contract.Direction.EXPENSE,
                transaction.getInt(transaction.getColumnIndex(Contract.Transaction.DIRECTION))
            )
            assertEquals(
                MONEY,
                transaction.getLong(transaction.getColumnIndex(Contract.Transaction.MONEY))
            )
        } finally {
            transaction.close()
        }
        assertEquals(
            "a category was created for a name the database already holds",
            before,
            countCategories()
        )
    }

    @Test
    fun anIncomeRowNamingTheSeededTaxCategoryIsRefusedItAndGetsAnOrdinaryOne() {
        val before = countCategories()
        val nonNullTaxName = taxName ?: return
        importRow(nonNullTaxName, Contract.Direction.INCOME)
        val categoryId = newestTransactionCategory()
        assertNotEquals(
            "an income row was filed on the tax category, which the editor would rewrite to an expense on the first save",
            taxId,
            categoryId
        )
        val category = categoryRow(categoryId)
        try {
            assertEquals(
                Contract.CategoryType.INCOME.value,
                category.getInt(category.getColumnIndex(Contract.Category.TYPE))
            )
            assertNull(category.getString(category.getColumnIndex(Contract.Category.TAG)))
        } finally {
            category.close()
        }
        assertEquals(before + 1, countCategories())
    }

    @Test
    fun aRowNamingTheTransferCategoryNeverLandsOnItInEitherDirection() {
        val before = countCategories()
        val nonNullTransferName = transferName ?: return
        for (direction in intArrayOf(Contract.Direction.EXPENSE, Contract.Direction.INCOME)) {
            importRow(nonNullTransferName, direction)
            val categoryId = newestTransactionCategory()
            assertNotEquals(
                "a transfer is written as two legs, so both directions appear under that tag and neither can be filed on it",
                transferId,
                categoryId
            )
            val category = categoryRow(categoryId)
            try {
                assertEquals(
                    if (direction == Contract.Direction.INCOME) Contract.CategoryType.INCOME.value else Contract.CategoryType.EXPENSE.value,
                    category.getInt(category.getColumnIndex(Contract.Category.TYPE))
                )
            } finally {
                category.close()
            }
        }
        assertEquals(before + 2, countCategories())
    }

    @Test
    fun aUserExpenseCategoryIsReusedByAnExpenseRowAndNotByAnIncomeRow() {
        val before = countCategories()
        importRow("Food", Contract.Direction.EXPENSE)
        assertEquals(
            "an expense row did not reuse the expense category of its own name",
            food,
            newestTransactionCategory()
        )
        assertEquals(before, countCategories())

        importRow("Food", Contract.Direction.INCOME)
        val income = newestTransactionCategory()
        assertNotEquals(food, income)
        val category = categoryRow(income)
        try {
            assertEquals("Food", category.getString(category.getColumnIndex(Contract.Category.NAME)))
            assertEquals(
                Contract.CategoryType.INCOME.value,
                category.getInt(category.getColumnIndex(Contract.Category.TYPE))
            )
        } finally {
            category.close()
        }
        assertEquals(before + 1, countCategories())

        importRow("Food", Contract.Direction.INCOME)
        assertEquals(
            "the second income row did not reuse the income category the first made",
            income,
            newestTransactionCategory()
        )
        assertEquals(before + 1, countCategories())
    }

    @Test
    @Throws(IOException::class)
    fun aRowTheWalletAlreadyHoldsIsSkippedAndCounted() {
        importRow("Food", Contract.Direction.EXPENSE)
        val again = TestImporter(context)
        again.insertTransaction(
            "Cash", euro, "Food", DATE, MONEY, Contract.Direction.EXPENSE,
            "desc", null, null, null, null
        )
        assertEquals(1, countTransactions())
        assertEquals(1, again.alreadySavedRows)
    }

    @Test
    @Throws(IOException::class)
    fun aRowRepeatedInOneFileIsSavedEachTimeItIsNew() {
        importRow("Food", Contract.Direction.EXPENSE)
        importRow("Food", Contract.Direction.EXPENSE)
        assertEquals(2, countTransactions())
        assertEquals(0, importer.alreadySavedRows)

        val again = TestImporter(context)
        for (i in 0 until 3) {
            again.insertTransaction(
                "Cash", euro, "Food", DATE, MONEY, Contract.Direction.EXPENSE,
                "desc", null, null, null, null
            )
        }
        assertEquals(3, countTransactions())
        assertEquals(2, again.alreadySavedRows)
    }

    @Test
    @Throws(IOException::class)
    fun noDescriptionMatchesAnEmptyOne() {
        importer.insertTransaction(
            "Cash", euro, "Food", DATE, MONEY, Contract.Direction.EXPENSE,
            null, null, null, null, null
        )
        val again = TestImporter(context)
        again.insertTransaction(
            "Cash", euro, "Food", DATE, MONEY, Contract.Direction.EXPENSE,
            "", null, null, null, null
        )
        assertEquals(1, countTransactions())
        assertEquals(1, again.alreadySavedRows)
    }

    @Test
    @Throws(IOException::class)
    fun aDescriptionSavedWithSpacesMatchesTheTrimmedOne() {
        importer.insertTransaction(
            "Cash", euro, "Food", DATE, MONEY, Contract.Direction.EXPENSE,
            "Coffee ", null, null, null, null
        )
        val again = TestImporter(context)
        again.insertTransaction(
            "Cash", euro, "Food", DATE, MONEY, Contract.Direction.EXPENSE,
            "Coffee", null, null, null, null
        )
        assertEquals(1, countTransactions())
        assertEquals(1, again.alreadySavedRows)
    }

    @Test
    @Throws(IOException::class)
    fun aZeroExpenseMatchesTheZeroIncomeTheExportTurnsItInto() {
        importer.insertTransaction(
            "Cash", euro, "Food", DATE, 0L, Contract.Direction.EXPENSE,
            "desc", null, null, null, null
        )
        val again = TestImporter(context)
        again.insertTransaction(
            "Cash", euro, "Food", DATE, 0L, Contract.Direction.INCOME,
            "desc", null, null, null, null
        )
        assertEquals(1, countTransactions())
        assertEquals(1, again.alreadySavedRows)
    }

    @Test
    @Throws(IOException::class)
    fun aWalletNameSavedWithASpaceIsFoundByTheTrimmedOne() {
        importer.insertTransaction(
            "Cash ", euro, "Food", DATE, MONEY, Contract.Direction.EXPENSE,
            "desc", null, null, null, null
        )
        val again = TestImporter(context)
        again.insertTransaction(
            "Cash", euro, "Food", DATE, MONEY, Contract.Direction.EXPENSE,
            "desc", null, null, null, null
        )
        assertEquals(1, countTransactions())
        assertEquals(1, again.alreadySavedRows)
    }

    @Test
    @Throws(IOException::class)
    fun aWalletNameSavedWithATabIsFoundByTheTrimmedOne() {
        importer.insertTransaction(
            "Cash\t", euro, "Food", DATE, MONEY, Contract.Direction.EXPENSE,
            "desc", null, null, null, null
        )
        val again = TestImporter(context)
        again.insertTransaction(
            "Cash", euro, "Food", DATE, MONEY, Contract.Direction.EXPENSE,
            "desc", null, null, null, null
        )
        assertEquals(1, countTransactions())
        assertEquals(1, again.alreadySavedRows)
    }

    @Test
    @Throws(IOException::class)
    fun aWalletNameWithANulInsideIsImported() {
        importer.insertTransaction(
            "Ca\u0000sh", euro, "Food", DATE, MONEY, Contract.Direction.EXPENSE,
            "desc", null, null, null, null
        )
        val again = TestImporter(context)
        again.insertTransaction(
            "Ca\u0000sh", euro, "Food", DATE, MONEY, Contract.Direction.EXPENSE,
            "desc", null, null, null, null
        )
        assertEquals(1, countTransactions())
        assertEquals(1, again.alreadySavedRows)
    }

    @Test
    @Throws(IOException::class)
    fun theWalletWithTheExactNameWinsOverANewerPaddedOne() {
        importRow("Food", Contract.Direction.EXPENSE)
        importer.insertTransaction(
            "Cash ", euro, "Food", DATE, MONEY, Contract.Direction.EXPENSE,
            "other", null, null, null, null
        )
        val again = TestImporter(context)
        again.insertTransaction(
            "Cash", euro, "Food", DATE, MONEY, Contract.Direction.EXPENSE,
            "desc", null, null, null, null
        )
        assertEquals(2, countTransactions())
        assertEquals(1, again.alreadySavedRows)
    }

    @Test
    @Throws(IOException::class)
    fun aRowDifferingInAnyComparedFieldIsSaved() {
        importRow("Food", Contract.Direction.EXPENSE)
        val again = TestImporter(context)
        again.insertTransaction(
            "Other", euro, "Food", DATE, MONEY, Contract.Direction.EXPENSE,
            "desc", null, null, null, null
        )
        again.insertTransaction(
            "Cash", euro, "Food", Date(DATE.time + 1000L), MONEY,
            Contract.Direction.EXPENSE, "desc", null, null, null, null
        )
        again.insertTransaction(
            "Cash", euro, "Food", DATE, MONEY + 1, Contract.Direction.EXPENSE,
            "desc", null, null, null, null
        )
        again.insertTransaction(
            "Cash", euro, "Food", DATE, MONEY, Contract.Direction.INCOME,
            "desc", null, null, null, null
        )
        again.insertTransaction(
            "Cash", euro, "Food", DATE, MONEY, Contract.Direction.EXPENSE,
            "other", null, null, null, null
        )
        assertEquals(6, countTransactions())
        assertEquals(0, again.alreadySavedRows)
    }

    @Test
    @Throws(IOException::class)
    fun everyRowTheExportWritesIsMatchedAndATransferLegIsNot() {
        importRow("Food", Contract.Direction.EXPENSE)
        val wallet = newestTransactionWallet()
        insertTyped(wallet, "debt", Contract.TransactionType.DEBT, systemCategory(Contract.CategoryTag.DEBT))
        insertTyped(
            wallet, "saving", Contract.TransactionType.SAVING,
            systemCategory(Contract.CategoryTag.SAVING_DEPOSIT)
        )
        insertTyped(
            wallet, "fee", Contract.TransactionType.TRANSFER,
            systemCategory(Contract.CategoryTag.TRANSFER_TAX)
        )
        insertTyped(wallet, "leg", Contract.TransactionType.TRANSFER, transferId)
        val again = TestImporter(context)
        for (description in arrayOf("debt", "saving", "fee", "leg")) {
            again.insertTransaction(
                "Cash", euro, "Food", DATE, MONEY, Contract.Direction.EXPENSE,
                description, null, null, null, null
            )
        }
        assertEquals(3, again.alreadySavedRows)
        assertEquals(6, countTransactions())
    }

    private fun newestTransactionWallet(): Long {
        val cursor = resolver.query(
            DataContentProvider.CONTENT_TRANSACTIONS,
            arrayOf(Contract.Transaction.WALLET_ID),
            null,
            null,
            "${Contract.Transaction.ID} DESC"
        )
        assertNotNull(cursor)
        cursor?.use {
            assertTrue(it.moveToFirst())
            return it.getLong(0)
        }
        return 0L
    }

    private fun insertTyped(wallet: Long, description: String, type: Int, category: Long) {
        val values = ContentValues().apply {
            put(Contract.Transaction.MONEY, MONEY)
            put(Contract.Transaction.DATE, DateUtils.getSQLDateTimeString(DATE))
            put(Contract.Transaction.DESCRIPTION, description)
            put(Contract.Transaction.CATEGORY_ID, category)
            put(Contract.Transaction.DIRECTION, Contract.Direction.EXPENSE)
            put(Contract.Transaction.TYPE, type)
            put(Contract.Transaction.WALLET_ID, wallet)
            put(Contract.Transaction.CONFIRMED, true)
            put(Contract.Transaction.COUNT_IN_TOTAL, true)
        }
        assertNotNull(resolver.insert(DataContentProvider.CONTENT_TRANSACTIONS, values))
    }

    private fun systemCategory(tag: String): Long {
        val cursor = resolver.query(
            DataContentProvider.CONTENT_CATEGORIES,
            arrayOf(Contract.Category.ID),
            "${Contract.Category.TAG} = ?",
            arrayOf(tag),
            null
        )
        assertNotNull(cursor)
        cursor?.use {
            assertTrue("no system category tagged $tag", it.moveToFirst())
            return it.getLong(0)
        }
        return 0L
    }

    private fun countTransactions(): Int {
        val cursor = resolver.query(
            DataContentProvider.CONTENT_TRANSACTIONS,
            arrayOf(Contract.Transaction.ID),
            null,
            null,
            null
        )
        assertNotNull(cursor)
        return cursor?.use { it.count } ?: 0
    }

    private fun importRow(category: String, direction: Int) {
        importer.insertTransaction(
            "Cash", euro, category, DATE, MONEY, direction,
            "desc", null, null, null, null
        )
    }

    private fun insertCategory(name: String, type: Contract.CategoryType): Long {
        val values = ContentValues().apply {
            put(Contract.Category.NAME, name)
            put(Contract.Category.ICON, ICON)
            put(Contract.Category.TYPE, type.value)
            put(Contract.Category.SHOW_REPORT, true)
        }
        val uri = resolver.insert(DataContentProvider.CONTENT_CATEGORIES, values)
        return if (uri != null) ContentUris.parseId(uri) else 0L
    }

    private fun newestTransaction(): Cursor {
        val cursor = resolver.query(
            DataContentProvider.CONTENT_TRANSACTIONS,
            arrayOf(
                Contract.Transaction.ID,
                Contract.Transaction.CATEGORY_ID,
                Contract.Transaction.DIRECTION,
                Contract.Transaction.MONEY
            ),
            null,
            null,
            "${Contract.Transaction.ID} DESC"
        )
        assertNotNull(cursor)
        val nonNullCursor = cursor ?: fail("the importer wrote no transaction row") as Nothing
        assertTrue("the importer wrote no transaction row", nonNullCursor.moveToFirst())
        return nonNullCursor
    }

    private fun newestTransactionCategory(): Long {
        val cursor = newestTransaction()
        try {
            return cursor.getLong(cursor.getColumnIndex(Contract.Transaction.CATEGORY_ID))
        } finally {
            cursor.close()
        }
    }

    private fun categoryRow(categoryId: Long): Cursor {
        val cursor = resolver.query(
            ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORIES, categoryId),
            arrayOf(
                Contract.Category.ID,
                Contract.Category.NAME,
                Contract.Category.TYPE,
                Contract.Category.TAG
            ),
            null,
            null,
            null
        )
        assertNotNull(cursor)
        val nonNullCursor = cursor ?: fail("no category row $categoryId") as Nothing
        assertTrue("no category row $categoryId", nonNullCursor.moveToFirst())
        return nonNullCursor
    }

    private fun countCategories(): Int {
        val cursor = resolver.query(
            DataContentProvider.CONTENT_CATEGORIES,
            arrayOf(Contract.Category.ID),
            null,
            null,
            null
        )
        assertNotNull(cursor)
        return cursor?.use { it.count } ?: 0
    }

    companion object {
        private const val ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}"
        private val DATE = Date(1565000000000L)
        private const val MONEY = 1200L

        private const val TYPE = 0
        private const val TAG = 1
        private const val ID = 2
    }
}
