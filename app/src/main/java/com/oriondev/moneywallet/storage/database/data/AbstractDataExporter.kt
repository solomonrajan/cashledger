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

package com.oriondev.moneywallet.storage.database.data

import android.content.Context
import android.database.Cursor
import androidx.collection.LongSparseArray
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.CurrencyUnit
import com.oriondev.moneywallet.model.Wallet
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.utils.CurrencyManager
import com.oriondev.moneywallet.utils.DateUtils
import com.oriondev.moneywallet.utils.MoneyFormatter
import java.io.File
import java.io.IOException
import java.util.Date

abstract class AbstractDataExporter @Throws(IOException::class) constructor(
    protected val context: Context,
    folder: File
) {

    companion object {
        const val COLUMN_EVENT: String = "column_event"
        const val COLUMN_PEOPLE: String = "column_people"
        const val COLUMN_PLACE: String = "column_place"
        const val COLUMN_NOTE: String = "column_note"
    }

    private val peopleCache: LongSparseArray<String> = LongSparseArray()
    private var shouldLoadPeople: Boolean = false

    protected open fun getDefaultFileName(extension: String): String {
        val dateTimeString = DateUtils.getFilenameDateTimeString(Date())
        return "Tallybook_export_$dateTimeString$extension"
    }

    abstract fun isMultiWalletSupported(): Boolean

    abstract fun getColumns(uniqueWallet: Boolean, optionalColumns: Array<String>?): Array<String>

    /**
     * Append the user-selected optional columns to a format specific base column list. The
     * mapping from selector token to contract column key is identical for every exporter, as is
     * the side effect of flagging that the people cache must be loaded, so it lives here once.
     */
    protected open fun appendOptionalColumns(columns: MutableList<String>, optionalColumns: Array<String>?) {
        if (optionalColumns != null) {
            for (column in optionalColumns) {
                when (column) {
                    COLUMN_EVENT -> columns.add(Constants.COLUMN_EVENT)
                    COLUMN_PEOPLE -> {
                        columns.add(Constants.COLUMN_PEOPLE)
                        shouldLoadPeople = true
                    }
                    COLUMN_PLACE -> columns.add(Constants.COLUMN_PLACE)
                    COLUMN_NOTE -> columns.add(Constants.COLUMN_NOTE)
                }
            }
        }
    }

    open fun shouldLoadPeople(): Boolean {
        return shouldLoadPeople
    }

    /**
     * Resolve the cell value for a single contract column from the current cursor row. This is the
     * one place that knows how each column maps onto the cursor, so the three formats no longer
     * duplicate it. Two things stay parametrized because they are format specific:
     * <ul>
     *     <li>[formatter] is the exporter's own [MoneyFormatter] (the CSV formatter is
     *     configured differently from the XLS/PDF one), and</li>
     *     <li>[peopleSeparator] joins multiple people, ", " for XLS/PDF but "," for CSV
     *     because exported CSVs are parsed back by the CSV importer.</li>
     * </ul>
     * Returns `null` for an empty people list or an unknown column, matching the previous
     * per-format behaviour exactly.
     */
    protected open fun getColumnValue(
        cursor: Cursor,
        column: String,
        formatter: MoneyFormatter,
        peopleSeparator: String
    ): String? {
        return when (column) {
            Constants.COLUMN_WALLET -> {
                val index = cursor.getColumnIndex(Contract.Transaction.WALLET_NAME)
                if (index != -1) cursor.getString(index) else null
            }
            Constants.COLUMN_CURRENCY -> {
                val index = cursor.getColumnIndex(Contract.Transaction.WALLET_CURRENCY)
                if (index != -1) cursor.getString(index) else null
            }
            Constants.COLUMN_CATEGORY -> {
                val index = cursor.getColumnIndex(Contract.Transaction.CATEGORY_NAME)
                if (index != -1) cursor.getString(index) else null
            }
            Constants.COLUMN_DATETIME -> {
                val index = cursor.getColumnIndex(Contract.Transaction.DATE)
                if (index != -1) cursor.getString(index) else null
            }
            Constants.COLUMN_MONEY -> {
                val currencyIndex = cursor.getColumnIndex(Contract.Transaction.WALLET_CURRENCY)
                val currencyUnit: CurrencyUnit? = if (currencyIndex != -1) {
                    val currencyIso = cursor.getString(currencyIndex)
                    if (currencyIso != null) CurrencyManager.getCurrency(currencyIso) else null
                } else {
                    null
                }
                val moneyIndex = cursor.getColumnIndex(Contract.Transaction.MONEY)
                var money = if (moneyIndex != -1) cursor.getLong(moneyIndex) else 0L
                val directionIndex = cursor.getColumnIndex(Contract.Transaction.DIRECTION)
                val direction = if (directionIndex != -1) cursor.getInt(directionIndex) else Contract.Direction.EXPENSE
                if (direction == Contract.Direction.EXPENSE) {
                    money *= -1
                }
                formatter.getNotTintedString(currencyUnit, money)
            }
            Constants.COLUMN_DESCRIPTION -> {
                val index = cursor.getColumnIndex(Contract.Transaction.DESCRIPTION)
                if (index != -1) cursor.getString(index) else null
            }
            Constants.COLUMN_EVENT -> {
                val index = cursor.getColumnIndex(Contract.Transaction.EVENT_NAME)
                if (index != -1) cursor.getString(index) else null
            }
            Constants.COLUMN_PEOPLE -> getPeopleValue(cursor, peopleSeparator)
            Constants.COLUMN_PLACE -> {
                val index = cursor.getColumnIndex(Contract.Transaction.PLACE_NAME)
                if (index != -1) cursor.getString(index) else null
            }
            Constants.COLUMN_NOTE -> {
                val index = cursor.getColumnIndex(Contract.Transaction.NOTE)
                if (index != -1) cursor.getString(index) else null
            }
            else -> null
        }
    }

    private fun getPeopleValue(cursor: Cursor, separator: String): String? {
        val peopleIdsIndex = cursor.getColumnIndex(Contract.Transaction.PEOPLE_IDS)
        val rawPeopleIds = if (peopleIdsIndex != -1) cursor.getString(peopleIdsIndex) else null
        val peopleIds = if (rawPeopleIds != null) Contract.parseObjectIds(rawPeopleIds) else null
        if (!peopleIds.isNullOrEmpty()) {
            val builder = StringBuilder()
            for (personId in peopleIds) {
                if (personId != null) {
                    val name = getPersonName(personId)
                    if (!name.isNullOrEmpty()) {
                        if (builder.isNotEmpty()) {
                            builder.append(separator)
                        }
                        builder.append(name)
                    }
                }
            }
            return builder.toString()
        }
        return null
    }

    /**
     * Localized header label for a contract column. Used by the XLS and PDF formats, which write a
     * translated header row; the CSV format writes the raw column keys instead and never calls
     * this. Returns `null` for a column without a header string, matching the old behaviour.
     */
    protected open fun getColumnHeader(column: String): String? {
        return when (column) {
            Constants.COLUMN_DATETIME -> context.getString(R.string.hint_date)
            Constants.COLUMN_CATEGORY -> context.getString(R.string.hint_category)
            Constants.COLUMN_MONEY -> context.getString(R.string.hint_money)
            Constants.COLUMN_WALLET -> context.getString(R.string.hint_wallet)
            Constants.COLUMN_DESCRIPTION -> context.getString(R.string.hint_description)
            Constants.COLUMN_EVENT -> context.getString(R.string.hint_event)
            Constants.COLUMN_PEOPLE -> context.getString(R.string.hint_people)
            Constants.COLUMN_PLACE -> context.getString(R.string.hint_place)
            Constants.COLUMN_NOTE -> context.getString(R.string.hint_note)
            else -> null
        }
    }

    open fun cachePeople(cursor: Cursor) {
        val idIndex = cursor.getColumnIndex(Contract.Person.ID)
        val nameIndex = cursor.getColumnIndex(Contract.Person.NAME)
        while (cursor.moveToNext()) {
            val id = if (idIndex != -1) cursor.getLong(idIndex) else continue
            val name = if (nameIndex != -1) cursor.getString(nameIndex) else null
            if (name != null) {
                peopleCache.put(id, name)
            }
        }
    }

    protected open fun getPersonName(id: Long): String? {
        return peopleCache.get(id)
    }

    @Throws(IOException::class)
    abstract fun exportData(cursor: Cursor, columns: Array<String>, vararg wallets: Wallet)

    @Throws(IOException::class)
    abstract fun close()

    abstract fun getOutputFile(): File

    abstract fun getResultType(): String
}
