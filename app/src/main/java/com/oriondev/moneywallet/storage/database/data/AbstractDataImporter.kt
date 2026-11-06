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

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.oriondev.moneywallet.model.Category
import com.oriondev.moneywallet.model.ColorIcon
import com.oriondev.moneywallet.model.CurrencyUnit
import com.oriondev.moneywallet.model.Icon
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.TransactionContentValuesBuilder
import com.oriondev.moneywallet.storage.database.data.csv.CSVDataExporter
import com.oriondev.moneywallet.utils.DateUtils
import com.oriondev.moneywallet.utils.IconLoader
import com.oriondev.moneywallet.utils.Utils
import java.io.File
import java.io.IOException
import java.util.Date
import java.util.Locale

abstract class AbstractDataImporter @Throws(IOException::class) constructor(
    private val mContext: Context?,
    file: File
) {

    companion object {
        const val NO_CATEGORY: Long = -1L

        private val TAGS_WITH_A_KNOWN_DIRECTION: Set<String> = hashSetOf(
            Contract.CategoryTag.CREDIT,
            Contract.CategoryTag.PAID_DEBT,
            Contract.CategoryTag.SAVING_DEPOSIT,
            Contract.CategoryTag.TAX,
            Contract.CategoryTag.TRANSFER_TAX,
            Contract.CategoryTag.DEBT,
            Contract.CategoryTag.PAID_CREDIT,
            Contract.CategoryTag.SAVING_WITHDRAW
        )

        /**
         * The id of the first category in the cursor that can hold a row of this direction, or
         * [NO_CATEGORY] when none can. A candidate that cannot hold the row is passed over
         * rather than ending the search, so a system category the guard refuses does not hide a
         * category the user made behind it. The cursor is rewound first, so wrapping the call in the
         * moveToFirst every other lookup in this class opens with does not skip a candidate.
         */
        @JvmStatic
        fun chooseCategory(cursor: Cursor, direction: Int): Long {
            cursor.moveToPosition(-1)
            while (cursor.moveToNext()) {
                if (keepsDirection(cursor, direction)) {
                    val idIndex = cursor.getColumnIndex(Contract.Category.ID)
                    if (idIndex != -1) {
                        return cursor.getLong(idIndex)
                    }
                }
            }
            return NO_CATEGORY
        }

        /**
         * Whether a category holds a row of this direction without the direction being rewritten
         * later.
         */
        private fun keepsDirection(cursor: Cursor, direction: Int): Boolean {
            val typeIndex = cursor.getColumnIndex(Contract.Category.TYPE)
            val typeVal = if (typeIndex != -1) cursor.getInt(typeIndex) else -1
            val type = Contract.CategoryType.fromValue(typeVal) ?: return false
            val tagIndex = cursor.getColumnIndex(Contract.Category.TAG)
            val tag = if (tagIndex != -1) cursor.getString(tagIndex) else null
            if (type == Contract.CategoryType.SYSTEM && (tag == null || !TAGS_WITH_A_KNOWN_DIRECTION.contains(tag))) {
                return false
            }
            return Category(0, null, null, type, tag).direction == direction
        }

        private fun readSavedRows(contentResolver: ContentResolver, walletId: Long): MutableMap<List<Any>, Int> {
            val saved = HashMap<List<Any>, Int>()
            val arguments = ArrayList<String>(CSVDataExporter.ROWS_SELECTION_ARGS)
            arguments.add(walletId.toString())
            val cursor = contentResolver.query(
                DataContentProvider.CONTENT_TRANSACTIONS,
                arrayOf(
                    Contract.Transaction.DATE,
                    Contract.Transaction.DIRECTION,
                    Contract.Transaction.MONEY,
                    Contract.Transaction.DESCRIPTION
                ),
                "${CSVDataExporter.ROWS_SELECTION} AND ${Contract.Transaction.WALLET_ID} = ?",
                arguments.toTypedArray(),
                null
            )
            cursor?.use {
                while (it.moveToNext()) {
                    val date = it.getString(0) ?: ""
                    val direction = it.getInt(1)
                    val money = it.getLong(2)
                    val description = it.getString(3)
                    val key = rowKey(date, direction, money, description)
                    val count = saved[key]
                    saved[key] = if (count == null) 1 else count + 1
                }
            }
            return saved
        }

        private fun rowKey(date: String, direction: Int, money: Long, description: String?): List<Any> {
            return listOf(
                date,
                if (money == 0L) Contract.Direction.INCOME else direction,
                money,
                description?.trim() ?: ""
            )
        }
    }

    /**
     * Per wallet, how many rows it already held for each key, read the first time this import
     * reaches that wallet and so before it wrote anything there. A match is used up when a row is
     * skipped against it, and rows this import writes are never added.
     */
    private val savedRows: MutableMap<Long, MutableMap<List<Any>, Int>> = HashMap()

    var alreadySavedRows: Int = 0
        private set

    protected open fun getContext(): Context {
        return mContext ?: throw IllegalStateException("Context is not available")
    }

    @Throws(IOException::class)
    abstract fun importData()

    /**
     * How many amounts this import had to round to fit the currency they were in, once
     * [importData] has run, and zero before it. A format that cannot carry an amount
     * the currency does not hold answers zero, but it has to say so rather than inherit it.
     */
    abstract fun getRoundedAmounts(): Int

    fun getAlreadySavedRows(): Int {
        return alreadySavedRows
    }

    /**
     * @return false when the row was skipped because the wallet already held it.
     */
    protected open fun insertTransaction(
        wallet: String,
        currencyUnit: CurrencyUnit,
        category: String,
        datetime: Date,
        money: Long,
        direction: Int,
        description: String?,
        event: String?,
        place: String?,
        people: String?,
        note: String?
    ): Boolean {
        val contentResolver = getContext().contentResolver
        val walletId = getOrCreateWallet(contentResolver, wallet, currencyUnit)
        return insertTransaction(
            contentResolver,
            walletId,
            category,
            datetime,
            money,
            direction,
            description,
            event,
            place,
            people,
            note
        )
    }

    /**
     * The same, into a wallet that already exists and was picked by id, whose currency the
     * amount is already in.
     */
    protected open fun insertTransaction(
        walletId: Long,
        category: String,
        datetime: Date,
        money: Long,
        direction: Int,
        description: String?,
        event: String?,
        place: String?,
        people: String?,
        note: String?
    ): Boolean {
        return insertTransaction(
            getContext().contentResolver,
            walletId,
            category,
            datetime,
            money,
            direction,
            description,
            event,
            place,
            people,
            note
        )
    }

    private fun insertTransaction(
        contentResolver: ContentResolver,
        walletId: Long,
        category: String,
        datetime: Date,
        money: Long,
        direction: Int,
        description: String?,
        event: String?,
        place: String?,
        people: String?,
        note: String?
    ): Boolean {
        val date = DateUtils.getSQLDateTimeString(datetime)
        if (isAlreadySaved(contentResolver, walletId, date, direction, money, description)) {
            alreadySavedRows++
            return false
        }
        val contentValues = TransactionContentValuesBuilder()
            .walletId(walletId)
            .categoryId(getOrCreateCategory(contentResolver, category, direction))
            .date(date)
            .direction(direction)
            .money(money)
            .description(description)
            .note(note)
            .eventId(getEvent(contentResolver, event))
            .placeId(getOrCreatePlace(contentResolver, place))
            .peopleIds(getOrCreatePeople(contentResolver, people))
            .type(Contract.TransactionType.STANDARD)
            .confirmed(true)
            .countInTotal(true)
            .build()
        val result = contentResolver.insert(DataContentProvider.CONTENT_TRANSACTIONS, contentValues)
            ?: throw RuntimeException("Failed to save the transaction")
        return true
    }

    private fun isAlreadySaved(
        contentResolver: ContentResolver,
        walletId: Long,
        date: String,
        direction: Int,
        money: Long,
        description: String?
    ): Boolean {
        var saved = savedRows[walletId]
        if (saved == null) {
            saved = readSavedRows(contentResolver, walletId)
            savedRows[walletId] = saved
        }
        val key = rowKey(date, direction, money, description)
        val left = saved[key] ?: return false
        if (left == 1) {
            saved.remove(key)
        } else {
            saved[key] = left - 1
        }
        return true
    }

    private fun getOrCreateWallet(contentResolver: ContentResolver, name: String, currencyUnit: CurrencyUnit): Long {
        val uri = DataContentProvider.CONTENT_WALLETS
        val projection = arrayOf(Contract.Wallet.ID)
        val trimmedName = "TRIM(" + Contract.Wallet.NAME + ", ' ' || char(9) || char(10) || char(13))"
        val selection = "$trimmedName = ? AND ${Contract.Wallet.CURRENCY} = ?"
        val selectionArgs = arrayOf(name, currencyUnit.iso)
        val sortOrder = "(${Contract.Wallet.NAME} = $trimmedName) DESC, ${Contract.Wallet.ID} DESC"
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(Contract.Wallet.ID)
                if (index != -1) {
                    return it.getLong(index)
                }
            }
        }
        val contentValues = ContentValues().apply {
            put(Contract.Wallet.NAME, name)
            put(Contract.Wallet.ICON, generateRandomIcon(name))
            put(Contract.Wallet.CURRENCY, currencyUnit.iso)
            put(Contract.Wallet.COUNT_IN_TOTAL, true)
            put(Contract.Wallet.START_MONEY, 0L)
            put(Contract.Wallet.ARCHIVED, false)
        }
        val result = contentResolver.insert(uri, contentValues)
            ?: throw RuntimeException("Failed to create the new wallet")
        return ContentUris.parseId(result)
    }

    private fun getOrCreateCategory(contentResolver: ContentResolver, name: String, direction: Int): Long {
        val uri = DataContentProvider.CONTENT_CATEGORIES
        val type = if (direction == Contract.Direction.INCOME) Contract.CategoryType.INCOME else Contract.CategoryType.EXPENSE
        val projection = arrayOf(Contract.Category.ID, Contract.Category.TYPE, Contract.Category.TAG)
        val selection = "${Contract.Category.NAME} = ? AND (${Contract.Category.TYPE} = ? OR ${Contract.Category.TYPE} = ?)"
        val selectionArgs = arrayOf(
            name,
            type.value.toString(),
            Contract.CategoryType.SYSTEM.value.toString()
        )
        val sortOrder = "${Contract.Category.ID} DESC"
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
        cursor?.use {
            val categoryId = chooseCategory(it, direction)
            if (categoryId != NO_CATEGORY) {
                return categoryId
            }
        }
        val contentValues = ContentValues().apply {
            put(Contract.Category.NAME, name)
            put(Contract.Category.ICON, generateRandomIcon(name))
            put(Contract.Category.TYPE, type.value)
            putNull(Contract.Category.PARENT)
            put(Contract.Category.SHOW_REPORT, true)
        }
        val result = contentResolver.insert(uri, contentValues)
            ?: throw RuntimeException("Failed to create the new category")
        return ContentUris.parseId(result)
    }

    private fun getEvent(contentResolver: ContentResolver, name: String?): Long? {
        if (!name.isNullOrEmpty()) {
            val uri = DataContentProvider.CONTENT_EVENTS
            val projection = arrayOf(Contract.Event.ID)
            val selection = "${Contract.Event.NAME} = ?"
            val selectionArgs = arrayOf(name)
            val sortOrder = "${Contract.Event.ID} DESC"
            val cursor = contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(Contract.Event.ID)
                    if (index != -1) {
                        return it.getLong(index)
                    }
                }
            }
        }
        return null
    }

    private fun getOrCreatePlace(contentResolver: ContentResolver, name: String?): Long? {
        if (!name.isNullOrEmpty()) {
            val uri = DataContentProvider.CONTENT_PLACES
            val projection = arrayOf(Contract.Place.ID)
            val selection = "${Contract.Place.NAME} = ?"
            val selectionArgs = arrayOf(name)
            val sortOrder = "${Contract.Place.ID} DESC"
            val cursor = contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(Contract.Place.ID)
                    if (index != -1) {
                        return it.getLong(index)
                    }
                }
            }
            val contentValues = ContentValues().apply {
                put(Contract.Place.NAME, name)
                put(Contract.Place.ICON, generateRandomIcon(name))
                putNull(Contract.Place.ADDRESS)
                putNull(Contract.Place.LATITUDE)
                putNull(Contract.Place.LONGITUDE)
            }
            val result = contentResolver.insert(uri, contentValues)
                ?: throw RuntimeException("Failed to create the new place")
            return ContentUris.parseId(result)
        }
        return null
    }

    private fun getOrCreatePeople(contentResolver: ContentResolver, people: String?): String? {
        if (!people.isNullOrEmpty()) {
            val peopleIds = ArrayList<Long>()
            for (person in people.split(",")) {
                val name = person.trim()
                if (name.isNotEmpty()) {
                    var personFound = false
                    val uri = DataContentProvider.CONTENT_PEOPLE
                    val projection = arrayOf(Contract.Person.ID)
                    val selection = "${Contract.Person.NAME} = ?"
                    val selectionArgs = arrayOf(name)
                    val sortOrder = "${Contract.Person.ID} DESC"
                    val cursor = contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val index = it.getColumnIndex(Contract.Person.ID)
                            if (index != -1) {
                                personFound = true
                                peopleIds.add(it.getLong(index))
                            }
                        }
                    }
                    if (!personFound) {
                        val contentValues = ContentValues().apply {
                            put(Contract.Person.NAME, name)
                            put(Contract.Person.ICON, name)
                            putNull(Contract.Person.NOTE)
                        }
                        val result = contentResolver.insert(uri, contentValues)
                            ?: throw RuntimeException("Failed to create the new person")
                        peopleIds.add(ContentUris.parseId(result))
                    }
                }
            }
            if (peopleIds.isNotEmpty()) {
                val peopleIdBuilder = StringBuilder()
                for (i in peopleIds.indices) {
                    if (i != 0) {
                        peopleIdBuilder.append(",")
                    }
                    peopleIdBuilder.append(String.format(Locale.ENGLISH, "<%d>", peopleIds[i]))
                }
                return peopleIdBuilder.toString()
            }
        }
        return null
    }

    private fun generateRandomIcon(name: String): String {
        val randomColor = Utils.getRandomMDColor()
        val iconText = IconLoader.getColorIconString(name)
        val icon: Icon = ColorIcon(randomColor, iconText)
        return icon.toString()
    }

    @Throws(IOException::class)
    abstract fun close()
}
