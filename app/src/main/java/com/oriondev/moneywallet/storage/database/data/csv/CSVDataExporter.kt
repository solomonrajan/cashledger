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
import android.database.Cursor
import com.opencsv.CSVWriter
import com.oriondev.moneywallet.model.Wallet
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.data.AbstractDataExporter
import com.oriondev.moneywallet.storage.database.data.Constants
import com.oriondev.moneywallet.utils.MoneyFormatter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.Collections

class CSVDataExporter @Throws(IOException::class) constructor(
    context: Context,
    folder: File
) : AbstractDataExporter(context, folder) {

    companion object {
        // multiple people are joined with a bare comma (not ", ") because an exported CSV is parsed
        // back by CSVDataImporter, which splits the people cell on this exact separator.
        private const val PEOPLE_SEPARATOR = ","

        /**
         * The transactions a CSV export writes, every one but a transfer leg. CSV is the only format
         * that can be imported back, and it carries nothing that pairs the two legs of a transfer, so
         * importing them recreates each leg as an ordinary transaction. The fee stays, it is one
         * expense and not half of anything. The importer looks for a row already saved among the same
         * transactions.
         */
        const val ROWS_SELECTION: String = "(${Contract.Transaction.TYPE} != ? OR ${Contract.Transaction.CATEGORY_TAG} = ?)"

        @JvmField
        val ROWS_SELECTION_ARGS: List<String> = Collections.unmodifiableList(
            listOf(
                Contract.TransactionType.TRANSFER.toString(),
                Contract.CategoryTag.TRANSFER_TAX
            )
        )
    }

    private val outputFile: File = File(folder, getDefaultFileName(".csv"))
    private val writer: CSVWriter = CSVWriter(FileWriter(outputFile))
    private val moneyFormatter: MoneyFormatter = MoneyFormatter.getInstance().apply {
        isCurrencyEnabled = false
        isRoundDecimalsEnabled = false
        isGroupDigitEnabled = false
    }

    override fun isMultiWalletSupported(): Boolean = false

    override fun getColumns(uniqueWallet: Boolean, optionalColumns: Array<String>?): Array<String> {
        val contractColumns = mutableListOf<String>()
        contractColumns.add(Constants.COLUMN_WALLET)
        contractColumns.add(Constants.COLUMN_CURRENCY)
        contractColumns.add(Constants.COLUMN_CATEGORY)
        contractColumns.add(Constants.COLUMN_DATETIME)
        contractColumns.add(Constants.COLUMN_MONEY)
        contractColumns.add(Constants.COLUMN_DESCRIPTION)
        appendOptionalColumns(contractColumns, optionalColumns)
        return contractColumns.toTypedArray()
    }

    @Throws(IOException::class)
    override fun exportData(cursor: Cursor, columns: Array<String>, vararg wallets: Wallet) {
        // the header line is the raw column keys, not localized labels: an exported CSV is
        // re-imported, so these keys are the machine readable contract with CSVDataImporter.
        writer.writeNext(columns)
        // export all the rows
        while (cursor.moveToNext()) {
            val csvRow = Array(columns.size) { i ->
                getColumnValue(cursor, columns[i], moneyFormatter, PEOPLE_SEPARATOR)
            }
            writer.writeNext(csvRow)
        }
    }

    @Throws(IOException::class)
    override fun close() {
        writer.close()
    }

    override fun getOutputFile(): File = outputFile

    override fun getResultType(): String = "application/vnd.ms-excel"
}
