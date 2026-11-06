/*
 * Copyright (c) 2026 solomonrajan.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.oriondev.moneywallet.storage.database.data.csv

import android.content.Context
import com.opencsv.CSVReader
import com.opencsv.CSVReaderBuilder
import com.opencsv.CSVReaderHeaderAware
import com.opencsv.exceptions.CsvMalformedLineException
import com.opencsv.exceptions.CsvValidationException
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.CurrencyUnit
import com.oriondev.moneywallet.model.MoneyScale
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.data.AbstractDataImporter
import com.oriondev.moneywallet.storage.database.data.Constants
import com.oriondev.moneywallet.utils.CurrencyManager
import com.oriondev.moneywallet.utils.DateUtils
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.PushbackReader
import java.io.Reader
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.util.Date
import java.util.TreeSet

class CSVDataImporter @Throws(IOException::class) @JvmOverloads constructor(
    context: Context?,
    private val file: File,
    private val mapping: CsvImportMapping? = null
) : AbstractDataImporter(context, file) {

    companion object {
        /** The length of yyyy-MM-dd, which the date parse itself does not bound. */
        private const val SQL_DATE_LENGTH = 10

        /** What a file saved as Unicode text can carry in front of its first character. */
        private const val BYTE_ORDER_MARK = '\uFEFF'

        private const val MAPPED_HEADER_LINES = 1

        /**
         * The file, with any byte order mark it starts with dropped.
         */
        @JvmStatic
        @Throws(IOException::class)
        fun openFile(file: File): Reader {
            val reader = PushbackReader(
                InputStreamReader(FileInputStream(file), StandardCharsets.UTF_8)
            )
            try {
                var first = reader.read()
                while (first == BYTE_ORDER_MARK.code) {
                    first = reader.read()
                }
                if (first == -1) {
                    throw RuntimeException("the file has no header line in it")
                }
                reader.unread(first)
                return reader
            } catch (failed: IOException) {
                closeQuietly(reader)
                throw failed
            } catch (failed: RuntimeException) {
                closeQuietly(reader)
                throw failed
            }
        }

        private fun closeQuietly(reader: Reader?) {
            try {
                reader?.close()
            } catch (ignored: IOException) {
                // nothing useful to do with it
            }
        }

        private fun rowSpansLines(start: Long): RuntimeException {
            return RuntimeException(
                "Line $start: a quote in this row is not closed on the same line, and every row has to fit on one line"
            )
        }

        @Throws(IOException::class)
        private fun readMap(reader: CSVReaderHeaderAware): Map<String, String>? {
            return try {
                reader.readMap()
            } catch (e: CsvValidationException) {
                throw IOException(e)
            }
        }

        @Throws(IOException::class)
        private fun readNext(reader: CSVReader): Array<String>? {
            return try {
                reader.readNext()
            } catch (e: CsvValidationException) {
                throw IOException(e)
            }
        }

        @JvmStatic
        fun required(lineMap: Map<String, String>, column: String): String {
            val value = lineMap[column]
                ?: throw RuntimeException(
                    "no $column column was found. The columns read out of the header line, sorted, are ${TreeSet(lineMap.keys)}"
                )
            val trimmed = value.trim()
            if (trimmed.isEmpty()) {
                throw RuntimeException("the $column is empty, and every row needs one")
            }
            return trimmed
        }

        @JvmStatic
        fun parseDatetime(value: String): Date {
            try {
                return DateUtils.getDateFromSQLDateTimeString(value)
            } catch (notADatetime: RuntimeException) {
                // fall through and read it as a date on its own
            }
            val date: Date = try {
                DateUtils.getDateFromSQLDateString(value)
            } catch (notADate: RuntimeException) {
                throw notADate(value)
            }
            if (value.length != SQL_DATE_LENGTH || DateUtils.getSQLDateString(date) != value) {
                throw notADate(value)
            }
            return date
        }

        private fun notADate(value: String): RuntimeException {
            return RuntimeException(
                "the datetime \"$value\" is not a real date as yyyy-MM-dd HH:mm:ss or yyyy-MM-dd"
            )
        }

        private fun mappedRequired(cells: Array<String>, column: Int, field: String): String {
            return mappedOptional(cells, column)
                ?: throw RuntimeException("the $field is empty, and every row needs one")
        }

        private fun mappedOptional(cells: Array<String>, column: Int): String? {
            if (column < 0 || column >= cells.size) {
                return null
            }
            val cell = cells[column] ?: return null
            val value = cell.trim()
            return if (value.isEmpty()) null else value
        }

        private fun currencyUnit(currency: String): CurrencyUnit {
            return CurrencyManager.getCurrency(currency)
                ?: throw RuntimeException("Unknown currency unit ($currency)")
        }

        private fun directionOf(money: Long): Int {
            return if (money < 0) Contract.Direction.EXPENSE else Contract.Direction.INCOME
        }
    }

    private val headerCells: Int = if (mapping == null) 0 else CsvImportMapping.readHeader(file).cells.size
    private val reader: CSVReader = openReader()
    private var mappedCurrency: CurrencyUnit? = null
    private var roundedAmounts: Int = 0

    @Throws(IOException::class)
    private fun openReader(): CSVReader {
        val fileReader = openFile(file)
        try {
            if (mapping == null) {
                return CSVReaderHeaderAware(fileReader)
            }
            return CSVReaderBuilder(fileReader)
                .withCSVParser(CsvImportMapping.parser(mapping.separator))
                .withSkipLines(MAPPED_HEADER_LINES)
                .build()
        } catch (failed: IOException) {
            closeQuietly(fileReader)
            throw failed
        } catch (failed: RuntimeException) {
            closeQuietly(fileReader)
            throw failed
        }
    }

    @Throws(IOException::class)
    override fun importData() {
        if (mapping != null) {
            mappedCurrency = requireMappedWallet()
        }
        openReader().use { check ->
            readRows(check, false)
        }
        try {
            DataContentProvider.runInOneTransaction(getContext()) {
                readRows(reader, true)
                null
            }
        } catch (e: IOException) {
            throw e
        } catch (e: RuntimeException) {
            throw e
        } catch (e: Exception) {
            throw IOException(e)
        }
    }

    @Throws(IOException::class)
    private fun readRows(reader: CSVReader, write: Boolean) {
        while (true) {
            val lineMap: Map<String, String>?
            val cells: Array<String>?
            if (mapping == null) {
                lineMap = readMap(reader as CSVReaderHeaderAware) ?: return
                cells = null
            } else {
                val before = reader.linesRead
                val start = maxOf(before, MAPPED_HEADER_LINES.toLong()) + 1
                try {
                    cells = readNext(reader) ?: return
                } catch (quoteNeverClosed: CsvMalformedLineException) {
                    throw rowSpansLines(start)
                }
                if (reader.linesRead > start) {
                    throw rowSpansLines(start)
                }
                lineMap = null
            }
            try {
                if (mapping == null) {
                    if (lineMap != null) {
                        readRow(lineMap, write)
                    }
                } else {
                    if (cells != null) {
                        readMappedRow(cells, write)
                    }
                }
            } catch (e: RuntimeException) {
                if (write) {
                    throw e
                }
                throw RuntimeException("Line ${reader.linesRead}: ${e.message}", e)
            }
        }
    }

    private fun readRow(lineMap: Map<String, String>, write: Boolean) {
        val wallet = required(lineMap, Constants.COLUMN_WALLET)
        val currency = required(lineMap, Constants.COLUMN_CURRENCY)
        val category = required(lineMap, Constants.COLUMN_CATEGORY)
        val datetimeString = required(lineMap, Constants.COLUMN_DATETIME)
        val moneyString = required(lineMap, Constants.COLUMN_MONEY)

        val description = getTrimmedString(lineMap[Constants.COLUMN_DESCRIPTION])
        val event = getTrimmedString(lineMap[Constants.COLUMN_EVENT])
        val people = getTrimmedString(lineMap[Constants.COLUMN_PEOPLE])
        val place = getTrimmedString(lineMap[Constants.COLUMN_PLACE])
        val note = getTrimmedString(lineMap[Constants.COLUMN_NOTE])

        val currencyUnit = currencyUnit(currency)
        val moneyDecimal = try {
            BigDecimal(moneyString.replace(",", "."))
        } catch (e: NumberFormatException) {
            throw RuntimeException("Invalid money amount (${e.message})")
        }
        val money = toMinorUnits(moneyDecimal, moneyString, currencyUnit.decimals)
        val direction = directionOf(money)
        val datetime = parseDatetime(datetimeString)
        if (write && insertTransaction(
                wallet,
                currencyUnit,
                category,
                datetime,
                kotlin.math.abs(money),
                direction,
                description,
                event,
                place,
                people,
                note
            )
        ) {
            countIfRounded(money, currencyUnit.decimals, moneyDecimal)
        }
    }

    private fun readMappedRow(cells: Array<String>, write: Boolean) {
        val map = mapping ?: return
        if (cells.size != headerCells) {
            throw RuntimeException(
                "the row has ${cells.size}${if (cells.size == 1) " cell" else " cells"} and the header has $headerCells, and every row needs as many as the header"
            )
        }
        val dateString = mappedRequired(cells, map.date, "date")
        val amountString = mappedRequired(cells, map.amount, "amount")
        val description = mappedOptional(cells, map.description)
        val note = mappedOptional(cells, map.note)
        val category = mappedOptional(cells, map.category)
        var amount = CsvImportMapping.parseAmount(amountString, map.decimalComma)
        if (map.spendingPositive) {
            amount = amount.negate()
        }
        val datetime = CsvImportMapping.parseDate(dateString, map.datePattern)
        val currentMappedCurrency = mappedCurrency
            ?: throw RuntimeException("the wallet picked for this import no longer exists")
        val money = toMinorUnits(amount, amountString, currentMappedCurrency.decimals)
        val direction = directionOf(money)
        if (write) {
            val finalCategory = category ?: getContext().getString(R.string.csv_import_default_category)
            if (insertTransaction(
                    map.walletId,
                    finalCategory,
                    datetime,
                    kotlin.math.abs(money),
                    direction,
                    description,
                    null,
                    null,
                    null,
                    note
                )
            ) {
                countIfRounded(money, currentMappedCurrency.decimals, amount)
            }
        }
    }

    private fun requireMappedWallet(): CurrencyUnit {
        val map = mapping ?: throw RuntimeException("the wallet picked for this import no longer exists")
        val cursor = getContext().contentResolver.query(
            DataContentProvider.CONTENT_WALLETS,
            arrayOf(Contract.Wallet.ID, Contract.Wallet.CURRENCY),
            "${Contract.Wallet.ID} = ?",
            arrayOf(map.walletId.toString()),
            null
        )
        var currency: String? = null
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndexOrThrow(Contract.Wallet.CURRENCY)
                currency = it.getString(index)
            }
        }
        val currentCurrency = currency ?: throw RuntimeException("the wallet picked for this import no longer exists")
        return currencyUnit(currentCurrency)
    }

    override fun getRoundedAmounts(): Int = roundedAmounts

    private fun toMinorUnits(amount: BigDecimal, cell: String, decimals: Int): Long {
        val money: Long
        try {
            money = MoneyScale.toMinorUnitsRounded(amount, decimals)
        } catch (e: ArithmeticException) {
            throw RuntimeException("Money amount is out of range ($cell)")
        }
        if (money == Long.MIN_VALUE) {
            throw RuntimeException("Money amount is out of range ($cell)")
        }
        return money
    }

    private fun countIfRounded(money: Long, decimals: Int, amount: BigDecimal) {
        if (MoneyScale.toHumanAmount(money, decimals).compareTo(amount) != 0) {
            roundedAmounts++
        }
    }

    private fun getTrimmedString(source: String?): String? {
        return source?.trim()
    }

    @Throws(IOException::class)
    override fun close() {
        reader.close()
    }
}
