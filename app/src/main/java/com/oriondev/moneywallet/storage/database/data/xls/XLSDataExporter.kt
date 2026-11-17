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

package com.oriondev.moneywallet.storage.database.data.xls

import android.content.Context
import android.database.Cursor
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.Wallet
import com.oriondev.moneywallet.storage.database.data.AbstractDataExporter
import com.oriondev.moneywallet.storage.database.data.Constants
import com.oriondev.moneywallet.utils.MoneyFormatter
import java.io.File
import java.io.IOException
import jxl.CellView
import jxl.Workbook
import jxl.write.Label
import jxl.write.WritableCellFormat
import jxl.write.WritableFont
import jxl.write.WritableSheet
import jxl.write.WritableWorkbook
import jxl.write.WriteException

class XLSDataExporter @Throws(IOException::class) constructor(
    context: Context,
    folder: File
) : AbstractDataExporter(context, folder) {

    companion object {
        private const val PEOPLE_SEPARATOR = ", "
    }

    private val outputFile: File = File(folder, getDefaultFileName(".xls"))
    private val workbook: WritableWorkbook = Workbook.createWorkbook(outputFile)
    private val moneyFormatter: MoneyFormatter = MoneyFormatter.getInstance()

    override fun isMultiWalletSupported(): Boolean = true

    override fun getColumns(uniqueWallet: Boolean, optionalColumns: Array<String>?): Array<String> {
        val contractColumns = mutableListOf<String>()
        contractColumns.add(Constants.COLUMN_DATETIME)
        contractColumns.add(Constants.COLUMN_CATEGORY)
        contractColumns.add(Constants.COLUMN_MONEY)
        if (uniqueWallet) {
            contractColumns.add(Constants.COLUMN_WALLET)
        }
        contractColumns.add(Constants.COLUMN_DESCRIPTION)
        appendOptionalColumns(contractColumns, optionalColumns)
        return contractColumns.toTypedArray()
    }

    @Throws(IOException::class)
    override fun exportData(cursor: Cursor, columns: Array<String>, vararg wallets: Wallet) {
        val sheet = workbook.createSheet(getSheetName(*wallets), getSheetIndex())
        try {
            // write the header of each column
            writeSheetHeader(sheet, columns)
            // write the body of the wallet
            for (r in 1..cursor.count) {
                // move the cursor to the fixed position
                cursor.moveToPosition(r - 1)
                // for each line of the cursor, write a line in the sheet
                for (i in columns.indices) {
                    val label = getColumnValue(cursor, columns[i], moneyFormatter, PEOPLE_SEPARATOR)
                    sheet.addCell(Label(i, r, label))
                }
            }
            // calculate the width of each column to fit the values
            for (i in columns.indices) {
                val cellView = sheet.getColumnView(i)
                cellView.isAutosize = true
                sheet.setColumnView(i, cellView)
            }
        } catch (e: WriteException) {
            throw IOException(e)
        }
    }

    private fun getSheetName(vararg wallets: Wallet): String {
        if (wallets.isNotEmpty()) {
            val builder = StringBuilder()
            for (wallet in wallets) {
                if (builder.isNotEmpty()) {
                    builder.append(", ")
                }
                builder.append(wallet.name)
            }
            return builder.toString()
        }
        return context.getString(R.string.hint_unknown)
    }

    private fun getSheetIndex(): Int = workbook.numberOfSheets

    @Throws(WriteException::class)
    private fun writeSheetHeader(sheet: WritableSheet, columns: Array<String>) {
        for (i in columns.indices) {
            val label = getColumnHeader(columns[i])
            val cellFont = WritableFont(WritableFont.TAHOMA, 10).apply {
                setBoldStyle(WritableFont.BOLD)
            }
            val cellFormat = WritableCellFormat(cellFont)
            sheet.addCell(Label(i, 0, label, cellFormat))
        }
    }

    @Throws(IOException::class)
    override fun close() {
        try {
            workbook.write()
            workbook.close()
        } catch (e: WriteException) {
            throw IOException(e)
        }
    }

    override fun getOutputFile(): File = outputFile

    override fun getResultType(): String = "application/vnd.ms-excel"
}
