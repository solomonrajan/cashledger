package com.oriondev.moneywallet.storage.database.data.csv

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.math.BigDecimal
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar

/**
 * Everything a mapped import decides before it saves a row. The import itself reads the picked
 * wallet from a database, so CsvMappedImportTest drives it.
 */
class CsvImportMappingTest {

    @Test
    fun theSeparatorIsTheOneUsedMostOutsideQuotes() {
        assertEquals(',', CsvImportMapping.detectSeparator("date,amount,memo"))
        assertEquals(';', CsvImportMapping.detectSeparator("date;amount;memo"))
        assertEquals('\t', CsvImportMapping.detectSeparator("date\tamount\tmemo"))
        assertEquals(',', CsvImportMapping.detectSeparator("\"a;b;c\",memo"))
        assertEquals(';', CsvImportMapping.detectSeparator("\"a,b,c\";amount;memo"))
    }

    @Test
    fun aTieOrNoSeparatorAtAllIsAComma() {
        assertEquals(',', CsvImportMapping.detectSeparator("date,amount;memo"))
        assertEquals(',', CsvImportMapping.detectSeparator("date;amount\tmemo"))
        assertEquals(',', CsvImportMapping.detectSeparator("date"))
        assertEquals(',', CsvImportMapping.detectSeparator(""))
    }

    @Test
    @Throws(IOException::class)
    fun aHeaderIsReadPastItsMarkWithTheSeparatorItUses() {
        val file = write("\uFEFFDate;Amount;\"Memo; long\"\n01/02/2026;12,50;x\n")
        val header = CsvImportMapping.readHeader(file)
        assertEquals(';', header.separator)
        assertArrayEquals(arrayOf("Date", "Amount", "Memo; long"), header.cells)
        assertFalse(header.nativeHeader)
    }

    @Test
    @Throws(IOException::class)
    fun aHeaderThisAppWroteIsNative() {
        val file = write("\uFEFF\"wallet\",\"currency\",\"category\",\"datetime\",\"money\",\"note\"\n")
        assertTrue(CsvImportMapping.readHeader(file).nativeHeader)
    }

    /**
     * A quoted cell whose last character is a backslash, as a Windows path is written. The app's
     * own import reads a backslash as an escape, so it cannot read this line at all, and the file
     * used to be refused outright under the reader's own wording about an unterminated field.
     */
    @Test
    @Throws(IOException::class)
    fun aHeaderTheAppsOwnImportCannotReadIsNotNative() {
        val header = CsvImportMapping.readHeader(
            write("Konto,\"Pfad\\\",Betrag,Text\n01/02/2026,x,12.50,y\n")
        )
        assertArrayEquals(arrayOf("Konto", "Pfad\\", "Betrag", "Text"), header.cells)
        assertFalse(header.nativeHeader)
        assertFalse(
            CsvImportMapping.readHeader(
                write("wallet,currency,category,datetime,money,\"Pfad\\\"\n")
            ).nativeHeader
        )
        assertTrue(
            CsvImportMapping.readHeader(
                write("wallet,currency,category,datetime,money\n")
            ).nativeHeader
        )
    }

    /** One stray quote is not a header line at all, and neither parser reads it. */
    @Test
    @Throws(IOException::class)
    fun aHeaderWithASingleStrayQuoteIsStillRefused() {
        val file = write("Konto,Pfa\"d,Betrag\n01/02/2026,x,12.50\n")
        try {
            CsvImportMapping.readHeader(file)
            fail("a header with a quote that is never closed has to be refused")
        } catch (expected: IOException) {
            assertEquals(
                "Line 1: a quote in this row is not closed on the same line, and every row has to fit on one line",
                expected.message
            )
        }
    }

    @Test
    fun aNativeHeaderNeedsAllFiveNamesSeparatedByCommas() {
        assertTrue(CsvImportMapping.isNativeHeader("money,datetime,category,currency,wallet"))
        assertFalse(CsvImportMapping.isNativeHeader("wallet,currency,category,datetime"))
        assertFalse(CsvImportMapping.isNativeHeader("wallet;currency;category;datetime;money"))
        assertFalse(CsvImportMapping.isNativeHeader("Wallet,Currency,Category,Datetime,Money"))
    }

    @Test
    @Throws(IOException::class)
    fun anEmptyFileIsRefusedWhenItsHeaderIsRead() {
        try {
            CsvImportMapping.readHeader(write(""))
            fail("a file with nothing in it has to be refused")
        } catch (expected: RuntimeException) {
            assertEquals("the file has no header line in it", expected.message)
        }
    }

    @Test
    fun theSignatureIgnoresCaseAndSurroundingSpace() {
        assertEquals(
            CsvImportMapping.signature(arrayOf("date", "amount")),
            CsvImportMapping.signature(arrayOf(" Date ", "AMOUNT"))
        )
        assertNotEquals(
            CsvImportMapping.signature(arrayOf("date", "amount")),
            CsvImportMapping.signature(arrayOf("amount", "date"))
        )
    }

    @Test
    fun aCellHoldingACommaOrAQuoteCannotCollide() {
        assertNotEquals(
            CsvImportMapping.signature(arrayOf("a,b", "c")),
            CsvImportMapping.signature(arrayOf("a", "b,c"))
        )
        assertNotEquals(
            CsvImportMapping.signature(arrayOf("a\",b")),
            CsvImportMapping.signature(arrayOf("a", "b"))
        )
        assertNotEquals(
            CsvImportMapping.signature(arrayOf("")),
            CsvImportMapping.signature(arrayOf("", ""))
        )
    }

    @Test
    fun theRememberedSettingsSurviveARoundTripWithoutTheWallet() {
        val mapping = CsvImportMapping().apply {
            separator = ';'
            date = 3
            amount = 0
            description = CsvImportMapping.NONE
            note = 2
            category = 1
            datePattern = "dd.MM.yyyy"
            decimalComma = true
            spendingPositive = true
            walletId = 42
        }
        val decoded = CsvImportMapping.decode(mapping.encode())
        assertNotNull(decoded)
        val nonNullDecoded = decoded ?: return
        assertEquals(3, nonNullDecoded.date)
        assertEquals(0, nonNullDecoded.amount)
        assertEquals(CsvImportMapping.NONE, nonNullDecoded.description)
        assertEquals(2, nonNullDecoded.note)
        assertEquals(1, nonNullDecoded.category)
        assertEquals("dd.MM.yyyy", nonNullDecoded.datePattern)
        assertTrue(nonNullDecoded.decimalComma)
        assertTrue(nonNullDecoded.spendingPositive)
        assertEquals(0, nonNullDecoded.walletId)
        assertFalse(mapping.encode().contains("42"))
    }

    @Test
    fun somethingEncodeDidNotWriteDecodesToNull() {
        assertNull(CsvImportMapping.decode(null))
        assertNull(CsvImportMapping.decode(""))
        assertNull(CsvImportMapping.decode("garbage"))
        assertNull(CsvImportMapping.decode("\"0\",\"1\",\"-1\",\"-1\",\"-1\",\"yyyy-MM-dd\",\"false\""))
        assertNull(CsvImportMapping.decode("\"0\",\"1\",\"-1\",\"-1\",\"-1\",\"yyyy-MM-dd\",\"false\",\"false\",\"x\""))
        assertNull(CsvImportMapping.decode("\"zero\",\"1\",\"-1\",\"-1\",\"-1\",\"yyyy-MM-dd\",\"false\",\"false\""))
        assertNull(CsvImportMapping.decode("\"-1\",\"1\",\"-1\",\"-1\",\"-1\",\"yyyy-MM-dd\",\"false\",\"false\""))
        assertNull(CsvImportMapping.decode("\"0\",\"1\",\"-2\",\"-1\",\"-1\",\"yyyy-MM-dd\",\"false\",\"false\""))
        assertNull(CsvImportMapping.decode("\"0\",\"1\",\"-1\",\"-1\",\"-1\",\"yy-MM-dd\",\"false\",\"false\""))
        assertNull(CsvImportMapping.decode("\"0\",\"1\",\"-1\",\"-1\",\"-1\",\"yyyy-MM-dd\",\"yes\",\"false\""))
        assertNull(CsvImportMapping.decode("\"0\",\"1\",\"-1\",\"-1\",\"-1\",\"yyyy-MM-dd\",\"false\",\"false"))
        assertNotNull(CsvImportMapping.decode("\"0\",\"1\",\"-1\",\"-1\",\"-1\",\"yyyy-MM-dd\",\"false\",\"false\""))
    }

    @Test
    fun everyDateFormatReadsADateAlone() {
        assertEquals(at(2026, 8, 12, 0, 0, 0), CsvImportMapping.parseDate("2026-08-12", "yyyy-MM-dd"))
        assertEquals(at(2026, 8, 12, 0, 0, 0), CsvImportMapping.parseDate("12/08/2026", "dd/MM/yyyy"))
        assertEquals(at(2026, 8, 12, 0, 0, 0), CsvImportMapping.parseDate("08/12/2026", "MM/dd/yyyy"))
        assertEquals(at(2026, 8, 12, 0, 0, 0), CsvImportMapping.parseDate("12.08.2026", "dd.MM.yyyy"))
        assertEquals(at(2026, 3, 5, 0, 0, 0), CsvImportMapping.parseDate("2026-3-5", "yyyy-MM-dd"))
        assertEquals(at(2026, 3, 5, 0, 0, 0), CsvImportMapping.parseDate("5/3/2026", "dd/MM/yyyy"))
    }

    @Test
    fun everyDateFormatReadsATimeWithAndWithoutSeconds() {
        assertEquals(at(2026, 8, 12, 9, 30, 0), CsvImportMapping.parseDate("2026-08-12 09:30", "yyyy-MM-dd"))
        assertEquals(at(2026, 8, 12, 9, 30, 15), CsvImportMapping.parseDate("2026-08-12 09:30:15", "yyyy-MM-dd"))
        assertEquals(at(2026, 8, 12, 9, 30, 0), CsvImportMapping.parseDate("12/08/2026 9:30", "dd/MM/yyyy"))
        assertEquals(at(2026, 8, 12, 21, 30, 15), CsvImportMapping.parseDate("12/08/2026 21:30:15", "dd/MM/yyyy"))
        assertEquals(at(2026, 8, 12, 9, 30, 0), CsvImportMapping.parseDate("08/12/2026 09:30", "MM/dd/yyyy"))
        assertEquals(at(2026, 8, 12, 9, 30, 15), CsvImportMapping.parseDate("08/12/2026 09:30:15", "MM/dd/yyyy"))
        assertEquals(at(2026, 8, 12, 9, 30, 0), CsvImportMapping.parseDate("12.08.2026 09:30", "dd.MM.yyyy"))
        assertEquals(at(2026, 8, 12, 9, 30, 15), CsvImportMapping.parseDate("12.08.2026 09:30:15", "dd.MM.yyyy"))
    }

    @Test
    fun aDateThatIsNotRealInTheChosenFormatIsRefused() {
        refusesDate("2026-02-30", "yyyy-MM-dd")
        refusesDate("2026-13-01", "yyyy-MM-dd")
        refusesDate("12/31/2026", "dd/MM/yyyy")
        refusesDate("31/12/2026", "MM/dd/yyyy")
        refusesDate("08/12/26", "MM/dd/yyyy")
        refusesDate("12/08/26", "dd/MM/yyyy")
        refusesDate("26-08-12", "yyyy-MM-dd")
        refusesDate("12.08.26", "dd.MM.yyyy")
        refusesDate("12026-08-12", "yyyy-MM-dd")
        refusesDate("2026-08-12x", "yyyy-MM-dd")
        refusesDate("2026-08-12T09:30", "yyyy-MM-dd")
        refusesDate("2026-08-12 09:30:15 UTC", "yyyy-MM-dd")
        refusesDate("2026-08-12  09:30", "yyyy-MM-dd")
        refusesDate("2026-08-12 24:00", "yyyy-MM-dd")
        refusesDate("2026-08-12 09:3", "yyyy-MM-dd")
        refusesDate("12.08.2026", "dd/MM/yyyy")
        refusesDate("", "yyyy-MM-dd")
    }

    /** SimpleDateFormat puts a two digit year within 80 years before today and 20 after. */
    @Test
    fun aTwoDigitYearTakesItsCenturyFromTheWindow() {
        assertEquals(at(2026, 3, 5, 0, 0, 0), CsvImportMapping.parseDate("05/03/26", "dd/MM/yy"))
        assertEquals(at(1999, 3, 5, 0, 0, 0), CsvImportMapping.parseDate("03/05/99", "MM/dd/yy"))
        assertEquals(at(2026, 3, 5, 0, 0, 0), CsvImportMapping.parseDate("05.03.26", "dd.MM.yy"))
        refusesDate("31/02/26", "dd/MM/yy")
        refusesDate("05/03/2026", "dd/MM/yy")
        refusesDate("03/05/1999", "MM/dd/yy")
        refusesDate("05.03.2026", "dd.MM.yy")
    }

    @Test
    fun anAmountWithADotForDecimals() {
        assertEquals(BigDecimal("1234.56"), CsvImportMapping.parseAmount("1234.56", false))
        assertEquals(BigDecimal("1234.56"), CsvImportMapping.parseAmount("1,234.56", false))
        assertEquals(BigDecimal("-1234567.8"), CsvImportMapping.parseAmount("-1,234,567.8", false))
        assertEquals(BigDecimal("12"), CsvImportMapping.parseAmount("+12", false))
        assertEquals(BigDecimal("1234"), CsvImportMapping.parseAmount("1,234", false))
        assertEquals(BigDecimal("1234.5"), CsvImportMapping.parseAmount("1 234.5", false))
        assertEquals(BigDecimal("1234.5"), CsvImportMapping.parseAmount("1\u00A0234.5", false))
    }

    /** A space groups digits only where the style's own mark would, and never beside it. */
    @Test
    fun aSpaceGroupsThousandsInThreesInEitherStyle() {
        assertEquals(BigDecimal("1234.56"), CsvImportMapping.parseAmount("1 234.56", false))
        assertEquals(BigDecimal("1234.56"), CsvImportMapping.parseAmount("1 234,56", true))
        assertEquals(BigDecimal("12345678"), CsvImportMapping.parseAmount("12 345 678", false))
        assertEquals(BigDecimal("12345678"), CsvImportMapping.parseAmount("12 345 678", true))
        assertEquals(BigDecimal("-1234.5"), CsvImportMapping.parseAmount("-1\u202F234.5", false))
        for (decimalComma in booleanArrayOf(false, true)) {
            refusesAmount("12 50", decimalComma)
            refusesAmount("12\u00A050", decimalComma)
            refusesAmount("1 2 3.4", decimalComma)
            refusesAmount("1 2 3,4", decimalComma)
            refusesAmount("1234 567", decimalComma)
            refusesAmount("1\u202F2345", decimalComma)
            refusesAmount("- 1 234", decimalComma)
            refusesAmount("1 234 ", decimalComma)
        }
        refusesAmount("1 234,567.89", false)
        refusesAmount("1 234.567,89", true)
    }

    @Test
    fun anAmountWithACommaForDecimals() {
        assertEquals(BigDecimal("1234.56"), CsvImportMapping.parseAmount("1234,56", true))
        assertEquals(BigDecimal("1234.56"), CsvImportMapping.parseAmount("1.234,56", true))
        assertEquals(BigDecimal("-1234567.8"), CsvImportMapping.parseAmount("-1.234.567,8", true))
        assertEquals(BigDecimal("-12.50"), CsvImportMapping.parseAmount("-12,50", true))
        assertEquals(BigDecimal("12"), CsvImportMapping.parseAmount("+12", true))
        assertEquals(BigDecimal("1234.5"), CsvImportMapping.parseAmount("1\u202F234,5", true))
    }

    /** The reason the reading is strict. A loose read of these would import a different number. */
    @Test
    fun anAmountInTheOtherStyleIsRefused() {
        refusesAmount("12,50", false)
        refusesAmount("1.234,56", false)
        refusesAmount("1,234.56", true)
        refusesAmount("12.50", true)
    }

    @Test
    fun anAmountThatIsNotClearlyOneNumberIsRefused() {
        refusesAmount("1,23.4", false)
        refusesAmount("1,2345", false)
        refusesAmount("1234,567.8", false)
        refusesAmount("12.", false)
        refusesAmount(".5", false)
        refusesAmount("--1", false)
        refusesAmount("1e3", false)
        refusesAmount("", false)
        refusesAmount("1.23,4", true)
        refusesAmount("12,", true)
    }

    @Test
    fun aCurrencySymbolBeforeOrAfterTheNumberIsDropped() {
        assertEquals(BigDecimal("1234.56"), CsvImportMapping.parseAmount("\$1,234.56", false))
        assertEquals(BigDecimal("12.00"), CsvImportMapping.parseAmount("\$12.00", false))
        assertEquals(BigDecimal("-5"), CsvImportMapping.parseAmount("-\$5", false))
        assertEquals(BigDecimal("-5"), CsvImportMapping.parseAmount("\$-5", false))
        assertEquals(BigDecimal("1234.56"), CsvImportMapping.parseAmount("1.234,56 \u20AC", true))
        assertEquals(BigDecimal("-1234.56"), CsvImportMapping.parseAmount("-1.234,56 \u20AC", true))
        assertEquals(BigDecimal("1234.56"), CsvImportMapping.parseAmount("1.234,56\u00A0\u20AC", true))
        assertEquals(BigDecimal("5"), CsvImportMapping.parseAmount("\u00A35", false))
        assertEquals(BigDecimal("1234"), CsvImportMapping.parseAmount("\u00A51,234", false))
    }

    @Test
    fun anAmountInParenthesesIsNegative() {
        assertEquals(BigDecimal("-12.50"), CsvImportMapping.parseAmount("(12.50)", false))
        assertEquals(BigDecimal("-1234.56"), CsvImportMapping.parseAmount("(\$1,234.56)", false))
    }

    @Test
    fun anAmountWithATrailingMinusIsNegative() {
        assertEquals(BigDecimal("-1234.56"), CsvImportMapping.parseAmount("1234.56-", false))
        assertEquals(BigDecimal("-1234.56"), CsvImportMapping.parseAmount("1.234,56 \u20AC-", true))
    }

    /** Two signs, two symbols, or a symbol that is really a code have no one clear reading. */
    @Test
    fun aSignOrSymbolThatIsNotClearIsRefused() {
        val cells = arrayOf(
            "(-5)", "-5-", "+5-", "\$5\$", "5- \u20AC", "USD 5", "R\$ 5", "CHF 5",
            "\$ \$5", "\$  5", "5  \$", "()", "-", "\$"
        )
        for (cell in cells) {
            refusesAmount(cell, false)
        }
    }

    /** Grouping never starts with 0, so these used to import 500 and 250. */
    @Test
    fun aThousandsGroupStartingWithZeroIsRefused() {
        refusesAmount("0,500", false)
        refusesAmount("00,250", false)
        refusesAmount("0 500", false)
        refusesAmount("0.500", true)
        refusesAmount("0 500", true)
        assertEquals(BigDecimal("1500"), CsvImportMapping.parseAmount("1,500", false))
        assertEquals(BigDecimal("1500"), CsvImportMapping.parseAmount("1.500", true))
        assertEquals(BigDecimal("0.50"), CsvImportMapping.parseAmount("0.50", false))
        assertEquals(BigDecimal("0.50"), CsvImportMapping.parseAmount("0,50", true))
        assertEquals(BigDecimal("10000"), CsvImportMapping.parseAmount("10,000", false))
    }

    companion object {
        private fun refusesDate(cell: String, pattern: String) {
            try {
                val parsed = CsvImportMapping.parseDate(cell, pattern)
                fail("$cell has to be refused as $pattern, but it read as $parsed")
            } catch (expected: RuntimeException) {
                assertEquals(
                    "the date \"$cell\" is not a real date written as $pattern, with or without a time",
                    expected.message
                )
            }
        }

        private fun refusesAmount(cell: String, decimalComma: Boolean) {
            try {
                val parsed = CsvImportMapping.parseAmount(cell, decimalComma)
                fail("$cell has to be refused, but it read as $parsed")
            } catch (expected: RuntimeException) {
                assertEquals(
                    "the amount \"$cell\" is not a number written as ${if (decimalComma) "1.234,56" else "1,234.56"}",
                    expected.message
                )
            }
        }

        @Throws(IOException::class)
        private fun write(contents: String): File {
            val file = File.createTempFile("tallybook-mapped-import", ".csv")
            file.deleteOnExit()
            OutputStreamWriter(FileOutputStream(file), Charsets.UTF_8).use { writer ->
                writer.write(contents)
            }
            return file
        }

        private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): Date {
            val calendar = GregorianCalendar(year, month - 1, day, hour, minute, second)
            calendar.set(Calendar.MILLISECOND, 0)
            return calendar.time
        }
    }
}
