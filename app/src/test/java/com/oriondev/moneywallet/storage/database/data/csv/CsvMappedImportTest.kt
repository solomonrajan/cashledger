package com.oriondev.moneywallet.storage.database.data.csv

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.TestDatabases
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter

/**
 * A mapped import run for real against a fresh database, for what CsvImportMappingTest cannot
 * reach without one: a row that reads fine before the row that is refused, and a file that is
 * saved.
 */
@RunWith(RobolectricTestRunner::class)
class CsvMappedImportTest {

    private lateinit var context: Context
    private lateinit var resolver: ContentResolver
    private var walletId: Long = 0

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        TestDatabases.useFreshDatabase(context)
        resolver = context.contentResolver
        val values = ContentValues().apply {
            put(Contract.Wallet.NAME, "Checking")
            put(Contract.Wallet.ICON, ICON)
            put(Contract.Wallet.CURRENCY, "EUR")
            put(Contract.Wallet.COUNT_IN_TOTAL, true)
            put(Contract.Wallet.START_MONEY, 0L)
            put(Contract.Wallet.ARCHIVED, false)
        }
        val uri = resolver.insert(DataContentProvider.CONTENT_WALLETS, values)
        assertNotNull(uri)
        walletId = if (uri != null) ContentUris.parseId(uri) else 0L
    }

    /**
     * The file from the device. The thousands comma in the second row is not quoted, so the row
     * reads as five cells, and read by position it used to save 1 under a new category named
     * 234.56. The row before it is fine, so the refusal also shows the whole import is held back.
     */
    @Test
    @Throws(IOException::class)
    fun aRowWithMoreCellsThanTheHeaderRefusesTheImportAndSavesNothing() {
        val categories = count(DataContentProvider.CONTENT_CATEGORIES, Contract.Category.ID)
        val refusal = refusal(
            write(
                "Date,Merchant,Amount,Category\n" +
                    "09/01/2026,\"Hardware, Inc.\",12.00,Home\n" +
                    "09/02/2026,\"Hardware, Inc.\",1,234.56,Home\n"
            ),
            deviceMapping()
        )
        assertEquals(
            "Line 3: the row has 5 cells and the header has 4, and every row needs as many as the header",
            refusal
        )
        assertEquals(0, count(DataContentProvider.CONTENT_TRANSACTIONS, Contract.Transaction.ID))
        assertEquals(categories, count(DataContentProvider.CONTENT_CATEGORIES, Contract.Category.ID))
    }

    /** The numbering the device showed, a refusal on the second data row naming line 3. */
    @Test
    @Throws(IOException::class)
    fun aRefusalOnTheSecondDataRowNamesLineThree() {
        val refusal = refusal(
            write(
                "Date,Merchant,Amount,Category\n" +
                    "09/01/2026,Grocer,1.00,Food\n" +
                    "09/02/2026,Grocer,\"12,50\",Food\n"
            ),
            deviceMapping()
        )
        assertEquals("Line 3: the amount \"12,50\" is not a number written as 1,234.56", refusal)
        assertEquals(0, count(DataContentProvider.CONTENT_TRANSACTIONS, Contract.Transaction.ID))
    }

    /**
     * A blank line between rows is a row of one empty cell to the reader, so it is refused like
     * any other row that does not match the header, and nothing before it is saved.
     */
    @Test
    @Throws(IOException::class)
    fun aBlankLineBetweenRowsRefusesTheImport() {
        val refusal = refusal(
            write(
                "Date,Merchant,Amount,Category\n" +
                    "09/01/2026,Grocer,1.00,Food\n" +
                    "\n" +
                    "09/02/2026,Grocer,2.00,Food\n"
            ),
            deviceMapping()
        )
        assertEquals(
            "Line 3: the row has 1 cell and the header has 4, and every row needs as many as the header",
            refusal
        )
        assertEquals(0, count(DataContentProvider.CONTENT_TRANSACTIONS, Contract.Transaction.ID))
    }

    /** The line ending after the last row, as nearly every tool writes one, is not a row of its own. */
    @Test
    @Throws(IOException::class)
    fun aLineEndingAfterTheLastRowImportsEveryRowAndNothingMore() {
        importFile(
            write(
                "Date,Merchant,Amount,Category\n" +
                    "09/01/2026,\"Hardware, Inc.\",12.00,Home\n" +
                    "09/02/2026,Grocer,3.50,\n"
            ),
            deviceMapping()
        )
        assertEquals(2, count(DataContentProvider.CONTENT_TRANSACTIONS, Contract.Transaction.ID))
        importFile(
            write(
                "Date,Merchant,Amount,Category\r\n" +
                    "09/03/2026,Grocer,4.00,Food\r\n"
            ),
            deviceMapping()
        )
        assertEquals(3, count(DataContentProvider.CONTENT_TRANSACTIONS, Contract.Transaction.ID))
    }

    /**
     * A mapped import read as far as its first row.
     */
    @Test
    @Throws(IOException::class)
    fun aMappedImportSkipsTheHeaderAndSplitsOnTheMappingSeparator() {
        val file = write("\uFEFFDate;Amount;Memo\n\"01/02/2026\";\"1;5\";x\n")
        assertEquals(
            "Line 2: the amount \"1;5\" is not a number written as 1,234.56",
            refusal(file, semicolonMapping())
        )
    }

    @Test
    @Throws(IOException::class)
    fun aMappedImportRefusesAMissingOrEmptyRequiredCell() {
        assertEquals(
            "Line 2: the date is empty, and every row needs one",
            refusal(write("Date;Amount\n  ;1.00\n"), semicolonMapping())
        )
        assertEquals(
            "Line 2: the amount is empty, and every row needs one",
            refusal(write("Date;Amount\n01/02/2026;\n"), semicolonMapping())
        )
        assertEquals(
            "Line 2: the date \"2026-02-01\" is not a real date written as dd/MM/yyyy, with or without a time",
            refusal(write("Date;Amount\n2026-02-01;1.00\n"), semicolonMapping())
        )
    }

    /**
     * Each row here has a date and an amount that read fine, so without the count check it would
     * be saved. A separator inside quotes is part of one cell, in the header and in a row alike.
     */
    @Test
    @Throws(IOException::class)
    fun aMappedRowWithMoreOrFewerCellsThanTheHeaderIsRefused() {
        assertEquals(
            "Line 2: the row has 2 cells and the header has 3, and every row needs as many as the header",
            refusal(write("Date;Amount;Memo\n01/02/2026;1.00\n"), semicolonMapping())
        )
        assertEquals(
            "Line 2: the row has 1 cell and the header has 3, and every row needs as many as the header",
            refusal(write("Date;Amount;Memo\n01/02/2026\n"), semicolonMapping())
        )
        assertEquals(
            "Line 2: the row has 4 cells and the header has 3, and every row needs as many as the header",
            refusal(write("Date;Amount;\"Memo; long\"\n01/02/2026;1.00;x;y\n"), semicolonMapping())
        )
        assertEquals(0, count(DataContentProvider.CONTENT_TRANSACTIONS, Contract.Transaction.ID))
    }

    /**
     * A backslash just before a closing quote used to escape the quote.
     */
    @Test
    @Throws(IOException::class)
    fun aBackslashBeforeAClosingQuoteEndsTheCell() {
        importFile(
            write(
                "Date,Description,Amount\n" +
                    "01/02/2026,\"C:\\\",-5.00\n" +
                    "01/03/2026,\"D:\\\",-3.00\n" +
                    "01/04/2026,Shop,-1.00\n"
            ),
            commaMapping()
        )
        assertEquals("C:\\ 500|D:\\ 300|Shop 100|", savedRows())
    }

    @Test
    @Throws(IOException::class)
    fun aBackslashInsideADescriptionIsKept() {
        importFile(write("Date,Description,Amount\n01/02/2026,PAYPAL\\ACME,-5.00\n"), commaMapping())
        assertEquals("PAYPAL\\ACME 500|", savedRows())
    }

    /**
     * The wallet picked on the import screen was EUR, and its currency was edited to JPY before
     * the import ran. The amount is saved in the scale of JPY, which has no decimals.
     */
    @Test
    @Throws(IOException::class)
    fun anAmountIsSavedInTheCurrencyTheWalletHasWhenTheImportRuns() {
        val values = ContentValues().apply {
            put(Contract.Wallet.CURRENCY, "JPY")
        }
        assertEquals(
            1,
            resolver.update(
                ContentUris.withAppendedId(DataContentProvider.CONTENT_WALLETS, walletId),
                values,
                null,
                null
            )
        )
        importFile(write("Date,Description,Amount\n01/02/2026,Shop,1500\n"), commaMapping())
        assertEquals("Shop 1500|", savedRows())
    }

    /** The wallet is looked up before any row is read, so its refusal comes before a bad row's. */
    @Test
    @Throws(IOException::class)
    fun aDeletedWalletIsRefusedBeforeAnyRowAndSavesNothing() {
        assertEquals(
            1,
            resolver.delete(
                ContentUris.withAppendedId(DataContentProvider.CONTENT_WALLETS, walletId),
                null,
                null
            )
        )
        assertEquals(
            "the wallet picked for this import no longer exists",
            refusal(write("Date,Description,Amount\n01/02/2026,Shop,1.00\nnot a date,Shop,1.00\n"), commaMapping())
        )
        assertEquals(0, count(DataContentProvider.CONTENT_TRANSACTIONS, Contract.Transaction.ID))
    }

    /**
     * The inch marks are not quoted, so the first one opens a quote the second one closes three
     * rows later.
     */
    @Test
    @Throws(IOException::class)
    fun aRowJoinedByAStrayQuotePairIsRefusedNamingTheLineItStartsOn() {
        val refusal = refusal(
            write(
                "Date,Description,Amount\n" +
                    "09/01/2026,Monitor 27\" LG,-199.00\n" +
                    "09/02/2026,Coffee,-3.50\n" +
                    "09/03/2026,Salary,2500.00\n" +
                    "09/04/2026,Tablet 11\" case,-20.00\n" +
                    "09/05/2026,Bread,-2.00\n"
            ),
            commaMapping()
        )
        assertEquals(
            "Line 2: a quote in this row is not closed on the same line, and every row has to fit on one line",
            refusal
        )
        assertEquals(0, count(DataContentProvider.CONTENT_TRANSACTIONS, Contract.Transaction.ID))
    }

    @Test
    @Throws(IOException::class)
    fun aStrayQuotePairAfterGoodRowsNamesTheLineTheJoinedRowStartsOn() {
        val refusal = refusal(
            write(
                "Date,Description,Amount\n" +
                    "09/01/2026,Coffee,-3.50\n" +
                    "09/02/2026,Bread,-2.00\n" +
                    "09/03/2026,Monitor 27\" LG,-199.00\n" +
                    "09/04/2026,Salary,2500.00\n" +
                    "09/05/2026,Tablet 11\" case,-20.00\n" +
                    "09/06/2026,Milk,-1.00\n"
            ),
            commaMapping()
        )
        assertEquals(
            "Line 4: a quote in this row is not closed on the same line, and every row has to fit on one line",
            refusal
        )
        assertEquals(0, count(DataContentProvider.CONTENT_TRANSACTIONS, Contract.Transaction.ID))
    }

    /**
     * A single inch mark opens a quote that nothing closes.
     */
    @Test
    @Throws(IOException::class)
    fun aRowWithOneStrayQuoteIsRefusedNamingTheLineItStartsOn() {
        val refusal = refusal(
            write(
                "Date,Description,Amount\n" +
                    "09/01/2026,Monitor 27\" LG,-199.00\n" +
                    "09/02/2026,Coffee,-3.50\n"
            ),
            commaMapping()
        )
        assertEquals(
            "Line 2: a quote in this row is not closed on the same line, and every row has to fit on one line",
            refusal
        )
        assertEquals(0, count(DataContentProvider.CONTENT_TRANSACTIONS, Contract.Transaction.ID))
    }

    @Test
    @Throws(IOException::class)
    fun oneStrayQuoteAfterGoodRowsNamesTheLineItStartsOn() {
        val refusal = refusal(
            write(
                "Date,Description,Amount\n" +
                    "09/01/2026,Coffee,-3.50\n" +
                    "09/02/2026,Bread,-2.00\n" +
                    "09/03/2026,Monitor 27\" LG,-199.00\n" +
                    "09/04/2026,Milk,-1.00\n"
            ),
            commaMapping()
        )
        assertEquals(
            "Line 4: a quote in this row is not closed on the same line, and every row has to fit on one line",
            refusal
        )
        assertEquals(0, count(DataContentProvider.CONTENT_TRANSACTIONS, Contract.Transaction.ID))
    }

    /** The last row, with no line ending after it, is still the line the refusal names. */
    @Test
    @Throws(IOException::class)
    fun oneStrayQuoteOnTheLastRowWithNoLineEndingNamesThatLine() {
        val refusal = refusal(
            write(
                "Date,Description,Amount\n" +
                    "09/01/2026,Coffee,-3.50\n" +
                    "09/02/2026,Tablet 11\" case,-20.00"
            ),
            commaMapping()
        )
        assertEquals(
            "Line 3: a quote in this row is not closed on the same line, and every row has to fit on one line",
            refusal
        )
        assertEquals(0, count(DataContentProvider.CONTENT_TRANSACTIONS, Contract.Transaction.ID))
    }

    /** A line break inside quotes is valid CSV, and a mapped import still refuses it. */
    @Test
    @Throws(IOException::class)
    fun aQuotedLineBreakIsRefusedNamingTheLineItStartsOn() {
        val refusal = refusal(
            write(
                "Date,Description,Amount\n" +
                    "09/01/2026,Coffee,-3.50\n" +
                    "09/02/2026,\"two\nlines\",-2.00\n" +
                    "09/03/2026,Bread,-1.00\n"
            ),
            commaMapping()
        )
        assertEquals(
            "Line 3: a quote in this row is not closed on the same line, and every row has to fit on one line",
            refusal
        )
        assertEquals(0, count(DataContentProvider.CONTENT_TRANSACTIONS, Contract.Transaction.ID))
    }

    /** Line endings of two characters and a quoted separator are still one line per row. */
    @Test
    @Throws(IOException::class)
    fun aFileWithCrlfEndingsAndAQuotedSeparatorImportsEveryRow() {
        importFile(
            write(
                "Date,Description,Amount\r\n" +
                    "01/02/2026,\"Shop, Inc.\",-5.00\r\n" +
                    "01/03/2026,Cafe,-3.00\r\n" +
                    "01/04/2026,Bakery,-1.00"
            ),
            commaMapping()
        )
        assertEquals("Shop, Inc. 500|Cafe 300|Bakery 100|", savedRows())
    }

    /**
     * Through both read passes and the transaction the second one runs in, so a row is counted
     * once and not once per pass. The file repeats a row, which the first import saves twice.
     */
    @Test
    @Throws(IOException::class)
    fun importingTheSameFileAgainSavesNothingAndCountsEveryRow() {
        val file = write(
            "Date,Description,Amount\n" +
                "01/02/2026,Shop,-5.00\n" +
                "01/02/2026,Shop,-5.00\n" +
                "01/03/2026,,-3.00\n"
        )
        assertEquals(0, importCountingSkipped(file, commaMapping()))
        assertEquals(3, count(DataContentProvider.CONTENT_TRANSACTIONS, Contract.Transaction.ID))
        assertEquals(3, importCountingSkipped(file, commaMapping()))
        assertEquals(3, count(DataContentProvider.CONTENT_TRANSACTIONS, Contract.Transaction.ID))
    }

    @Test
    @Throws(IOException::class)
    fun aRowSkippedOnReimportIsNotCountedAsRounded() {
        val file = write("Date,Description,Amount\n01/02/2026,Shop,-5.005\n")
        val first = CSVDataImporter(context, file, commaMapping())
        val second = CSVDataImporter(context, file, commaMapping())
        try {
            first.importData()
            assertEquals(1, first.roundedAmounts)
            second.importData()
            assertEquals(1, second.alreadySavedRows)
            assertEquals(0, second.roundedAmounts)
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun aRowSkippedOnReimportOfOurOwnFormatIsNotCountedAsRounded() {
        val file = write(
            "wallet,currency,category,datetime,money,description\n" +
                "Checking,EUR,Food,2026-01-02 10:00:00,-5.005,Shop\n"
        )
        val first = CSVDataImporter(context, file)
        val second = CSVDataImporter(context, file)
        try {
            first.importData()
            assertEquals(1, first.roundedAmounts)
            second.importData()
            assertEquals(1, second.alreadySavedRows)
            assertEquals(0, second.roundedAmounts)
        } finally {
            first.close()
            second.close()
        }
    }

    @Throws(IOException::class)
    private fun importCountingSkipped(file: File, mapping: CsvImportMapping): Int {
        val importer = CSVDataImporter(context, file, mapping)
        try {
            importer.importData()
            return importer.alreadySavedRows
        } finally {
            importer.close()
        }
    }

    private fun semicolonMapping(): CsvImportMapping {
        return CsvImportMapping().apply {
            separator = ';'
            date = 0
            amount = 1
            description = 2
            datePattern = "dd/MM/yyyy"
            walletId = this@CsvMappedImportTest.walletId
        }
    }

    private fun commaMapping(): CsvImportMapping {
        return CsvImportMapping().apply {
            separator = ','
            date = 0
            description = 1
            amount = 2
            datePattern = "MM/dd/yyyy"
            walletId = this@CsvMappedImportTest.walletId
        }
    }

    /** Every saved row as its description and money, oldest first. */
    private fun savedRows(): String {
        val cursor = resolver.query(
            DataContentProvider.CONTENT_TRANSACTIONS,
            arrayOf(Contract.Transaction.DESCRIPTION, Contract.Transaction.MONEY),
            null,
            null,
            "${Contract.Transaction.DATE} ASC"
        )
        assertNotNull(cursor)
        val rows = StringBuilder()
        cursor?.use {
            while (it.moveToNext()) {
                rows.append(it.getString(0)).append(' ').append(it.getLong(1)).append('|')
            }
        }
        return rows.toString()
    }

    private fun deviceMapping(): CsvImportMapping {
        return CsvImportMapping().apply {
            separator = ','
            date = 0
            description = 1
            amount = 2
            category = 3
            datePattern = "MM/dd/yyyy"
            spendingPositive = true
            walletId = this@CsvMappedImportTest.walletId
        }
    }

    @Throws(IOException::class)
    private fun importFile(file: File, mapping: CsvImportMapping) {
        val importer = CSVDataImporter(context, file, mapping)
        try {
            importer.importData()
        } finally {
            importer.close()
        }
    }

    @Throws(IOException::class)
    private fun refusal(file: File, mapping: CsvImportMapping): String? {
        try {
            importFile(file, mapping)
            fail("the file has to be refused")
            return null
        } catch (expected: RuntimeException) {
            return expected.message
        }
    }

    private fun count(uri: Uri, idColumn: String): Int {
        val cursor = resolver.query(uri, arrayOf(idColumn), null, null, null)
        assertNotNull(cursor)
        return cursor?.use { it.count } ?: 0
    }

    companion object {
        private const val ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"W\"}"

        @Throws(IOException::class)
        private fun write(contents: String): File {
            val file = File.createTempFile("tallybook-mapped-import", ".csv")
            file.deleteOnExit()
            OutputStreamWriter(FileOutputStream(file), Charsets.UTF_8).use { writer ->
                writer.write(contents)
            }
            return file
        }
    }
}
