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

package com.oriondev.moneywallet.storage.database.data.pdf

import android.content.Context
import android.database.Cursor
import com.itextpdf.text.BaseColor
import com.itextpdf.text.Chapter
import com.itextpdf.text.Chunk
import com.itextpdf.text.Document
import com.itextpdf.text.DocumentException
import com.itextpdf.text.Element
import com.itextpdf.text.Font
import com.itextpdf.text.FontFactory
import com.itextpdf.text.PageSize
import com.itextpdf.text.Paragraph
import com.itextpdf.text.pdf.PdfPTable
import com.itextpdf.text.pdf.PdfWriter
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.Wallet
import com.oriondev.moneywallet.storage.database.data.AbstractDataExporter
import com.oriondev.moneywallet.storage.database.data.Constants
import com.oriondev.moneywallet.utils.MoneyFormatter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class PDFDataExporter @Throws(IOException::class) constructor(
    context: Context,
    folder: File
) : AbstractDataExporter(context, folder) {

    companion object {
        private const val PEOPLE_SEPARATOR = ", "
    }

    private val outputFile: File = File(folder, getDefaultFileName(".pdf"))
    private val document: Document = Document(PageSize.A4)
    private val moneyFormatter: MoneyFormatter = MoneyFormatter.getInstance()
    private var chapterCount: Int = 0

    init {
        try {
            PdfWriter.getInstance(document, FileOutputStream(outputFile))
        } catch (e: DocumentException) {
            throw IOException(e)
        }
        document.addAuthor("Tallybook - Expense Manager")
        document.open()
    }

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
        try {
            val chapter = createChapter(*wallets)
            chapter.add(createTable(cursor, columns))
            document.add(chapter)
        } catch (e: DocumentException) {
            throw IOException(e)
        }
    }

    @Throws(DocumentException::class)
    private fun createChapter(vararg wallets: Wallet): Chapter {
        val chapterTitleBuilder = StringBuilder()
        if (wallets.isNotEmpty()) {
            for (wallet in wallets) {
                if (chapterTitleBuilder.isNotEmpty()) {
                    chapterTitleBuilder.append(", ")
                }
                chapterTitleBuilder.append(wallet.name)
            }
        } else {
            chapterTitleBuilder.append(context.getString(R.string.hint_unknown))
        }
        val font = FontFactory.getFont(FontFactory.HELVETICA, 16f, Font.BOLDITALIC)
        val chunk = Chunk(chapterTitleBuilder.toString(), font)
        val paragraph = Paragraph(chunk)
        paragraph.spacingAfter = 30f
        return Chapter(paragraph, ++chapterCount)
    }

    @Throws(DocumentException::class)
    private fun createTable(cursor: Cursor, columns: Array<String>): PdfPTable {
        val table = createTable(columns)
        for (i in 0 until cursor.count) {
            cursor.moveToPosition(i)
            for (column in columns) {
                table.addCell(getColumnValue(cursor, column, moneyFormatter, PEOPLE_SEPARATOR))
            }
        }
        return table
    }

    @Throws(DocumentException::class)
    private fun createTable(columns: Array<String>): PdfPTable {
        val table = PdfPTable(columns.size)
        table.widthPercentage = 100f
        table.defaultCell.backgroundColor = BaseColor.YELLOW
        table.defaultCell.horizontalAlignment = Element.ALIGN_CENTER
        for (column in columns) {
            table.addCell(getColumnHeader(column))
        }
        table.defaultCell.backgroundColor = BaseColor.WHITE
        return table
    }

    @Throws(IOException::class)
    override fun close() {
        document.close()
    }

    override fun getOutputFile(): File = outputFile

    override fun getResultType(): String = "application/pdf"
}
