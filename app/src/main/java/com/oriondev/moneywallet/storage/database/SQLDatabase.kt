/*
 * Copyright (c) 2018.
 * Copyright (c) 2026 solomonrajan.
 *
 * This file is part of MoneyWallet.
 *
 * MoneyWallet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MoneyWallet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MoneyWallet.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.oriondev.moneywallet.storage.database

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import android.text.TextUtils
import android.util.Log
import android.util.SparseLongArray
import com.oriondev.moneywallet.model.Attachment
import com.oriondev.moneywallet.model.ColorIcon
import com.oriondev.moneywallet.model.CurrencyUnit
import com.oriondev.moneywallet.model.Icon
import com.oriondev.moneywallet.model.RecurrenceSetting
import com.oriondev.moneywallet.utils.CurrencyManager
import com.oriondev.moneywallet.utils.DateUtils
import com.oriondev.moneywallet.utils.IconLoader
import com.oriondev.moneywallet.utils.MoneyFormatter
import org.dmfs.rfc5545.DateTime
import org.dmfs.rfc5545.recur.InvalidRecurrenceRuleException
import org.dmfs.rfc5545.recur.RecurrenceRule
import org.dmfs.rfc5545.recur.RecurrenceRuleIterator
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.ArrayList
import java.util.Calendar
import java.util.Date
import java.util.HashMap
import java.util.LinkedHashMap
import java.util.Locale
import java.util.UUID
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.function.Function
import java.util.function.Supplier

/**
 * This class implements the default SQLite helper class of the Android Framework.
 * Here are handled all the raw operation with the database.
 */
@SuppressLint("Range")
open class SQLDatabase(
    private val mContext: Context,
    name: String
) : SQLiteOpenHelper(mContext, name, null, DATABASE_VERSION) {

    companion object {
        private const val TAG = "SQLDatabase"

        const val DATABASE_NAME: String = "database.db"
        private const val DATABASE_VERSION = 7

        /**
         * The currencies whose shipped decimals were taken from the number of digits a country
         * uses in cash, where it circulates no minor coin. A ledger records bank statements,
         * cards and transfers, so the count used in a formal setting is the right one, and
         * ISO 4217 puts all nine at two as well.
         */
        @JvmField
        val CURRENCIES_MOVED_TO_TWO_DECIMALS: Array<String> = arrayOf(
            "AMD", "CRC", "ETB", "GYD", "MNT", "MUR", "RSD", "TZS", "UZS"
        )

        /**
         * The helper a restore builds its import in, set only on the thread running that import.
         */
        private val sStaging = ThreadLocal<SQLDatabase>()

        @SuppressLint("StaticFieldLeak")
        private var sShared: SQLDatabase? = null

        /**
         * Held for the whole of one provider write, and exclusively while the shared helper is
         * swapped. Resolving the helper and running the transaction have to be under the same hold,
         * or a swap landing between the two would run the transaction on a helper that has just been
         * closed.
         */
        private val sSwapLock = ReentrantReadWriteLock(true)

        @JvmStatic
        @Synchronized
        fun getShared(context: Context): SQLDatabase {
            val staging = sStaging.get()
            if (staging != null) {
                return staging
            }
            var shared = sShared
            if (shared == null) {
                shared = SQLDatabase(context.applicationContext, DATABASE_NAME)
                sShared = shared
            }
            return shared
        }

        @JvmStatic
        fun resetShared(context: Context) {
            resetShared(context, null)
        }

        @JvmStatic
        fun resetShared(context: Context, whileClosed: Runnable?) {
            if (sSwapLock.readHoldCount > 0) {
                throw IllegalStateException("resetShared called from inside a transaction")
            }
            sSwapLock.writeLock().lock()
            try {
                synchronized(SQLDatabase::class.java) {
                    try {
                        sShared?.close()
                        whileClosed?.run()
                    } finally {
                        sShared = SQLDatabase(context.applicationContext, DATABASE_NAME)
                    }
                }
            } finally {
                sSwapLock.writeLock().unlock()
            }
        }

        @JvmStatic
        fun openStaging(context: Context, name: String) {
            if (sStaging.get() != null) {
                throw IllegalStateException("a staging database is already open on this thread")
            }
            val staging = SQLDatabase(context.applicationContext, name)
            staging.writableDatabase
            sStaging.set(staging)
        }

        @JvmStatic
        fun closeStaging() {
            val staging = sStaging.get()
            if (staging != null) {
                sStaging.remove()
                staging.close()
            }
        }

        @JvmStatic
        fun <T> inSharedTransaction(context: Context?, body: Function<SQLDatabase, T>): T {
            requireNotNull(context) { "Context cannot be null" }
            sSwapLock.readLock().lock()
            try {
                val database = getShared(context)
                return database.inTransaction { body.apply(database) }
            } finally {
                sSwapLock.readLock().unlock()
            }
        }

        private fun masterTransactionDateFor(debtDate: String?, transactionDate: String?): String {
            if (debtDate == null) {
                return DateUtils.getSQLDateTimeString(System.currentTimeMillis())
            }
            val day = DateUtils.getDateFromSQLDateString(debtDate)
            if (transactionDate == null) {
                return DateUtils.getSQLDateTimeString(day)
            }
            val date = DateUtils.getDateFromSQLDateTimeString(transactionDate)
            val time = DateUtils.getCalendar(date)
            return DateUtils.getSQLDateTimeString(
                DateUtils.setTime(
                    day,
                    time.get(Calendar.HOUR_OF_DAY),
                    time.get(Calendar.MINUTE),
                    time.get(Calendar.SECOND),
                    0
                )
            )
        }

        private fun budgetCategoriesOf(budgetAlias: String): String {
            return "(SELECT bc." + Schema.BudgetCategory.CATEGORY + " FROM " +
                    Schema.BudgetCategory.TABLE + " AS bc WHERE bc." + Schema.BudgetCategory.BUDGET +
                    " = " + budgetAlias + "." + Schema.Budget.ID + " AND bc." +
                    Schema.BudgetCategory.DELETED + " = 0)"
        }
    }

    /**
     * Attachment file names removed by the body of the transaction currently running on this
     * thread. Null when this thread is not inside one.
     */
    private val mPendingAttachmentFiles = ThreadLocal<MutableList<String>>()

    fun <T> inTransaction(body: Supplier<T>): T {
        if (mPendingAttachmentFiles.get() != null) {
            return body.get()
        }
        val database = writableDatabase
        mPendingAttachmentFiles.set(ArrayList())
        val result: T
        try {
            database.beginTransaction()
            try {
                result = body.get()
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
        } catch (e: RuntimeException) {
            mPendingAttachmentFiles.remove()
            throw e
        } catch (e: Error) {
            mPendingAttachmentFiles.remove()
            throw e
        }
        val pending = mPendingAttachmentFiles.get()
        mPendingAttachmentFiles.remove()
        deleteAttachmentFiles(pending)
        return result
    }

    override fun onCreate(db: SQLiteDatabase) {
        // create all tables
        db.execSQL(Schema.CREATE_TABLE_CURRENCY)
        db.execSQL(Schema.CREATE_TABLE_WALLET)
        db.execSQL(Schema.CREATE_TABLE_CATEGORY)
        db.execSQL(Schema.CREATE_TABLE_CATEGORY_RULE)
        db.execSQL(Schema.CREATE_TABLE_EVENT)
        db.execSQL(Schema.CREATE_TABLE_PLACE)
        db.execSQL(Schema.CREATE_TABLE_PERSON)
        db.execSQL(Schema.CREATE_TABLE_EVENT_PEOPLE)
        db.execSQL(Schema.CREATE_TABLE_DEBT)
        db.execSQL(Schema.CREATE_TABLE_DEBT_PEOPLE)
        db.execSQL(Schema.CREATE_TABLE_BUDGET)
        db.execSQL(Schema.CREATE_TABLE_BUDGET_WALLET)
        db.execSQL(Schema.CREATE_TABLE_BUDGET_CATEGORY)
        db.execSQL(Schema.CREATE_TABLE_SAVING)
        db.execSQL(Schema.CREATE_TABLE_TRANSACTION)
        db.execSQL(Schema.CREATE_TABLE_TRANSACTION_PEOPLE)
        db.execSQL(Schema.CREATE_TABLE_TRANSFER)
        db.execSQL(Schema.CREATE_TABLE_TRANSFER_PEOPLE)
        db.execSQL(Schema.CREATE_TABLE_TRANSACTION_MODEL)
        db.execSQL(Schema.CREATE_TABLE_TRANSFER_MODEL)
        db.execSQL(Schema.CREATE_TABLE_RECURRENT_TRANSACTION)
        db.execSQL(Schema.CREATE_TABLE_RECURRENT_TRANSFER)
        db.execSQL(Schema.CREATE_TABLE_ATTACHMENT)
        db.execSQL(Schema.CREATE_TABLE_TRANSACTION_ATTACHMENT)
        db.execSQL(Schema.CREATE_TABLE_TRANSFER_ATTACHMENT)
        // insert default items
        addSystemCategories(db)
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 3) {
            db.execSQL(Schema.CREATE_WALLET_INDEX_COLUMN)
            db.execSQL(Schema.CREATE_CATEGORY_INDEX_COLUMN)
        }
        if (oldVersion < 4) {
            if (!hasColumn(db, Schema.Budget.TABLE, Schema.Budget.RULE)) {
                db.execSQL(Schema.CREATE_BUDGET_RULE_COLUMN)
            }
            if (!hasColumn(db, Schema.Budget.TABLE, Schema.Budget.RULE_START)) {
                db.execSQL(Schema.CREATE_BUDGET_RULE_START_COLUMN)
            }
        }
        if (oldVersion < 5) {
            db.execSQL(Schema.CREATE_TABLE_BUDGET_CATEGORY)
            db.execSQL(Schema.FILL_BUDGET_CATEGORY_FROM_COLUMN)
            db.execSQL(Schema.ALIGN_BUDGET_CATEGORY_COLUMN)
            db.execSQL(Schema.CLEAR_BUDGET_CATEGORY_OF_OTHER_TYPES)
            db.execSQL(Schema.CLEAR_BUDGET_COLUMN_OF_OTHER_TYPES)
        }
        if (oldVersion < 6) {
            for (iso in CURRENCIES_MOVED_TO_TWO_DECIMALS) {
                if (readCurrencyDecimals(db, iso) == 0) {
                    val currency = ContentValues()
                    currency.put(Schema.Currency.DECIMALS, 2)
                    currency.put(Schema.Currency.LAST_EDIT, System.currentTimeMillis())
                    db.update(
                        Schema.Currency.TABLE, currency,
                        Schema.Currency.ISO + " = ?", arrayOf(iso)
                    )
                    fixCurrencyAmounts(db, iso, 2)
                }
            }
        }
        if (oldVersion < 7) {
            db.execSQL(Schema.CREATE_TABLE_CATEGORY_RULE)
        }
    }

    private fun readCurrencyDecimals(db: SQLiteDatabase, iso: String): Int {
        val cursor = db.query(
            Schema.Currency.TABLE, arrayOf(Schema.Currency.DECIMALS),
            Schema.Currency.ISO + " = ?", arrayOf(iso), null, null, null
        )
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    return cursor.getInt(0)
                }
            } finally {
                cursor.close()
            }
        }
        return -1
    }

    private fun hasColumn(db: SQLiteDatabase, table: String, column: String): Boolean {
        val cursor = db.rawQuery("PRAGMA table_info($table)", null)
        if (cursor != null) {
            try {
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (TextUtils.equals(column, cursor.getString(nameIndex))) {
                        return true
                    }
                }
            } finally {
                cursor.close()
            }
        }
        return false
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    }

    private fun addSystemCategories(db: SQLiteDatabase) {
        for (category in SystemCategory.mSystemCategories) {
            val contentValues = ContentValues()
            contentValues.put(Schema.Category.NAME, category.getName(mContext))
            contentValues.put(Schema.Category.ICON, category.getIcon(mContext))
            contentValues.put(Schema.Category.TYPE, Contract.CategoryType.SYSTEM.value)
            contentValues.put(Schema.Category.SHOW_REPORT, true)
            contentValues.put(Schema.Category.INDEX, 0)
            contentValues.put(Schema.Category.TAG, category.tag)
            contentValues.put(Schema.Category.UUID, category.uuid)
            contentValues.put(Schema.Category.LAST_EDIT, System.currentTimeMillis())
            contentValues.put(Schema.Category.DELETED, false)
            db.insertWithOnConflict(Schema.Category.TABLE, null, contentValues, SQLiteDatabase.CONFLICT_ABORT)
        }
    }

    private fun getSystemCategoryId(tag: String): Long? {
        val projection = arrayOf(Schema.Category.ID)
        val selection = Schema.Category.TAG + " = ?"
        val selectionArgs = arrayOf(tag)
        val cursor = getCategories(projection, selection, selectionArgs, null)
        if (cursor != null) {
            var categoryId: Long? = null
            if (cursor.moveToFirst()) {
                categoryId = cursor.getLong(cursor.getColumnIndex(Schema.Category.ID))
            }
            cursor.close()
            return categoryId
        }
        return null
    }

    open fun getCurrency(iso: String, projection: Array<String>?): Cursor? {
        val selection = Schema.Currency.ISO + " = ?"
        val selectionArgs = arrayOf(iso)
        return getCurrencies(projection, selection, selectionArgs, null)
    }

    open fun getCurrencies(projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        val subQuery = "SELECT " +
                Schema.Currency.ISO + " AS " + Contract.Currency.ISO + ", " +
                Schema.Currency.NAME + " AS " + Contract.Currency.NAME + ", " +
                Schema.Currency.SYMBOL + " AS " + Contract.Currency.SYMBOL + ", " +
                Schema.Currency.DECIMALS + " AS " + Contract.Currency.DECIMALS + ", " +
                Schema.Currency.FAVOURITE + " AS " + Contract.Currency.FAVOURITE +
                " FROM " + Schema.Currency.TABLE +
                " WHERE " + Schema.Currency.DELETED + " = 0"
        return queryFrom(subQuery, projection, selection, selectionArgs, sortOrder)
    }

    open fun insertCurrency(contentValues: ContentValues): String? {
        val cv = ContentValues()
        cv.put(Schema.Currency.ISO, contentValues.getAsString(Contract.Currency.ISO))
        cv.put(Schema.Currency.NAME, contentValues.getAsString(Contract.Currency.NAME))
        cv.put(Schema.Currency.SYMBOL, contentValues.getAsString(Contract.Currency.SYMBOL))
        if (contentValues.containsKey(Schema.Currency.DECIMALS)) {
            var decimals = contentValues.getAsInteger(Contract.Currency.DECIMALS) ?: 0
            if (decimals < 0) {
                decimals = 0
            } else if (decimals > 8) {
                decimals = 8
            }
            cv.put(Schema.Currency.DECIMALS, decimals)
        }
        if (contentValues.containsKey(Schema.Currency.FAVOURITE)) {
            cv.put(Schema.Currency.FAVOURITE, contentValues.getAsBoolean(Contract.Currency.FAVOURITE))
        }
        cv.put(Schema.Currency.UUID, "currency_" + contentValues.getAsString(Contract.Currency.ISO))
        cv.put(Schema.Currency.LAST_EDIT, System.currentTimeMillis())
        cv.put(Schema.Currency.DELETED, false)
        val iso = contentValues.getAsString(Contract.Currency.ISO)
        if (writableDatabase.insert(Schema.Currency.TABLE, null, cv) > 0) {
            return iso
        }
        if (!contentValues.containsKey(Contract.Currency.REVIVE_IF_DELETED)
                || !java.lang.Boolean.TRUE.equals(contentValues.getAsBoolean(Contract.Currency.REVIVE_IF_DELETED))) {
            return null
        }
        val revived = ContentValues()
        revived.put(Schema.Currency.DELETED, false)
        revived.put(Schema.Currency.LAST_EDIT, System.currentTimeMillis())
        val where = Schema.Currency.ISO + " = ? AND " + Schema.Currency.DELETED + " = 1"
        if (writableDatabase.update(Schema.Currency.TABLE, revived, where, arrayOf(iso)) > 0) {
            return iso
        }
        return null
    }

    open fun updateCurrency(iso: String, contentValues: ContentValues): Int {
        var oldDecimals = 0
        val projection = arrayOf(Schema.Currency.DECIMALS)
        val selection = Schema.Currency.ISO + " = ?"
        val selectionArgs = arrayOf(iso)
        val cursor = readableDatabase.query(Schema.Currency.TABLE, projection, selection, selectionArgs, null, null, null)
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                oldDecimals = cursor.getInt(cursor.getColumnIndex(Schema.Currency.DECIMALS))
            }
            cursor.close()
        }
        val cv = ContentValues()
        if (contentValues.containsKey(Schema.Currency.NAME)) {
            cv.put(Schema.Currency.NAME, contentValues.getAsString(Contract.Currency.NAME))
        }
        if (contentValues.containsKey(Schema.Currency.SYMBOL)) {
            cv.put(Schema.Currency.SYMBOL, contentValues.getAsString(Contract.Currency.SYMBOL))
        }
        var newDecimals = oldDecimals
        if (contentValues.containsKey(Schema.Currency.DECIMALS)) {
            val dec = contentValues.getAsInteger(Contract.Currency.DECIMALS) ?: oldDecimals
            newDecimals = if (dec < 0) 0 else if (dec > 8) 8 else dec
            cv.put(Schema.Currency.DECIMALS, newDecimals)
        }
        if (contentValues.containsKey(Schema.Currency.FAVOURITE)) {
            cv.put(Schema.Currency.FAVOURITE, contentValues.getAsBoolean(Contract.Currency.FAVOURITE))
        }
        cv.put(Schema.Currency.LAST_EDIT, System.currentTimeMillis())
        val where = Schema.Currency.ISO + " = ?"
        val whereArgs = arrayOf(iso)
        val rows = writableDatabase.update(Schema.Currency.TABLE, cv, where, whereArgs)
        if (oldDecimals != newDecimals && contentValues.containsKey(Contract.Currency.FIX_MONEY_DECIMALS) && java.lang.Boolean.TRUE.equals(contentValues.getAsBoolean(Contract.Currency.FIX_MONEY_DECIMALS))) {
            val decimalOffset = newDecimals - oldDecimals
            fixCurrencyAmounts(writableDatabase, iso, decimalOffset)
        }
        return rows
    }

    open fun deleteCurrency(iso: String): Int {
        val projection = arrayOf(Schema.Wallet.ID)
        val where = Schema.Wallet.CURRENCY + " = ?"
        val whereArgs = arrayOf(iso)
        val cursor = readableDatabase.query(Schema.Wallet.TABLE, projection, where, whereArgs, null, null, null)
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    val walletId = cursor.getLong(cursor.getColumnIndex(Schema.Wallet.ID))
                    throw SQLiteDataException(
                        Contract.ErrorCode.CURRENCY_IN_USE,
                        String.format(Locale.ENGLISH, "The currency (iso: %s) cannot be deleted because is in use in wallet (id: %d)", iso, walletId)
                    )
                }
            } finally {
                cursor.close()
            }
        }
        val deleteWhere = Schema.Currency.ISO + " = ?"
        return writableDatabase.delete(Schema.Currency.TABLE, deleteWhere, whereArgs)
    }

    open fun getWallet(id: Long, projection: Array<String>?): Cursor? {
        val selection = Schema.Wallet.ID + " = ?"
        val selectionArgs = arrayOf(id.toString())
        return getWallets(projection, selection, selectionArgs, null)
    }

    open fun getWallets(projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        val subQuery = "SELECT " +
                Schema.Wallet.ID + " AS " + Contract.Wallet.ID + ", " +
                Schema.Wallet.NAME + " AS " + Contract.Wallet.NAME + ", " +
                Schema.Wallet.ICON + " AS " + Contract.Wallet.ICON + ", " +
                Schema.Wallet.CURRENCY + " AS " + Contract.Wallet.CURRENCY + ", " +
                Schema.Wallet.COUNT_IN_TOTAL + " AS " + Contract.Wallet.COUNT_IN_TOTAL + ", " +
                Schema.Wallet.START_MONEY + " AS " + Contract.Wallet.START_MONEY + ", " +
                Schema.Wallet.NOTE + " AS " + Contract.Wallet.NOTE + ", " +
                Schema.Wallet.ARCHIVED + " AS " + Contract.Wallet.ARCHIVED + ", " +
                Schema.Wallet.INDEX + " AS " + Contract.Wallet.INDEX + ", " +
                Schema.Wallet.TAG + " AS " + Contract.Wallet.TAG + ", " +
                "total_money AS " + Contract.Wallet.TOTAL_MONEY + " FROM " + Schema.Wallet.TABLE +
                " LEFT JOIN (SELECT " + Schema.Transaction.WALLET + " AS _wallet," +
                " SUM(((" + Schema.Transaction.DIRECTION + " * 2) - 1) * " + Schema.Transaction.MONEY +
                ") AS total_money FROM " + Schema.Transaction.TABLE + " AS t WHERE t." +
                Schema.Transaction.DELETED + " = 0 AND " + Schema.Transaction.CONFIRMED + " = 1 AND " +
                Schema.Transaction.COUNT_IN_TOTAL + " = 1 AND DATETIME(" + Schema.Transaction.DATE +
                ") <= DATETIME('now', 'localtime') GROUP BY _wallet) ON " + Schema.Wallet.ID +
                " = _wallet WHERE " + Schema.Wallet.DELETED + " = 0"
        return queryFrom(subQuery, projection, selection, selectionArgs, sortOrder)
    }

    open fun insertWallet(contentValues: ContentValues): Long {
        val cv = ContentValues()
        cv.put(Schema.Wallet.NAME, contentValues.getAsString(Contract.Wallet.NAME))
        cv.put(Schema.Wallet.ICON, contentValues.getAsString(Contract.Wallet.ICON))
        cv.put(Schema.Wallet.CURRENCY, contentValues.getAsString(Contract.Wallet.CURRENCY))
        cv.put(Schema.Wallet.START_MONEY, contentValues.getAsLong(Contract.Wallet.START_MONEY))
        cv.put(Schema.Wallet.COUNT_IN_TOTAL, contentValues.getAsBoolean(Contract.Wallet.COUNT_IN_TOTAL))
        cv.put(Schema.Wallet.NOTE, contentValues.getAsString(Contract.Wallet.NOTE))
        if (contentValues.containsKey(Contract.Wallet.ARCHIVED)) {
            cv.put(Schema.Wallet.ARCHIVED, contentValues.getAsBoolean(Contract.Wallet.ARCHIVED))
        }
        val index = contentValues.getAsInteger(Contract.Wallet.INDEX)
        cv.put(Schema.Wallet.INDEX, index ?: 0)
        cv.put(Schema.Wallet.TAG, contentValues.getAsString(Contract.Wallet.TAG))
        cv.put(Schema.Wallet.UUID, UUID.randomUUID().toString())
        cv.put(Schema.Wallet.LAST_EDIT, System.currentTimeMillis())
        cv.put(Schema.Wallet.DELETED, false)
        return writableDatabase.insert(Schema.Wallet.TABLE, null, cv)
    }

    open fun updateWallet(walletId: Long, contentValues: ContentValues): Int {
        val cv = ContentValues()
        if (contentValues.containsKey(Contract.Wallet.NAME)) {
            cv.put(Schema.Wallet.NAME, contentValues.getAsString(Contract.Wallet.NAME))
        }
        if (contentValues.containsKey(Contract.Wallet.ICON)) {
            cv.put(Schema.Wallet.ICON, contentValues.getAsString(Contract.Wallet.ICON))
        }
        if (contentValues.containsKey(Contract.Wallet.CURRENCY)) {
            cv.put(Schema.Wallet.CURRENCY, contentValues.getAsString(Contract.Wallet.CURRENCY))
        }
        if (contentValues.containsKey(Contract.Wallet.START_MONEY)) {
            cv.put(Schema.Wallet.START_MONEY, contentValues.getAsLong(Contract.Wallet.START_MONEY))
        }
        if (contentValues.containsKey(Contract.Wallet.COUNT_IN_TOTAL)) {
            cv.put(Schema.Wallet.COUNT_IN_TOTAL, contentValues.getAsBoolean(Contract.Wallet.COUNT_IN_TOTAL))
        }
        if (contentValues.containsKey(Contract.Wallet.NOTE)) {
            cv.put(Schema.Wallet.NOTE, contentValues.getAsString(Contract.Wallet.NOTE))
        }
        if (contentValues.containsKey(Contract.Wallet.ARCHIVED)) {
            cv.put(Schema.Wallet.ARCHIVED, contentValues.getAsBoolean(Contract.Wallet.ARCHIVED))
        }
        if (contentValues.containsKey(Contract.Wallet.INDEX)) {
            val index = contentValues.getAsInteger(Contract.Wallet.INDEX)
            cv.put(Schema.Wallet.INDEX, index ?: 0)
        }
        if (contentValues.containsKey(Contract.Wallet.TAG)) {
            cv.put(Schema.Wallet.TAG, contentValues.getAsString(Contract.Wallet.TAG))
        }
        cv.put(Schema.Wallet.LAST_EDIT, System.currentTimeMillis())
        val where = Schema.Wallet.ID + " = ?"
        val whereArgs = arrayOf(walletId.toString())
        return writableDatabase.update(Schema.Wallet.TABLE, cv, where, whereArgs)
    }

    open fun deleteWallet(walletId: Long): Int {
        val query = "SELECT " + Schema.Transfer.ID + " FROM " + Schema.Transfer.TABLE + " JOIN " +
                Schema.Transaction.TABLE + " AS t1 ON " + Schema.Transfer.TRANSACTION_FROM + " = t1." +
                Schema.Transaction.ID + " AND t1." + Schema.Transaction.DELETED + " = 0 JOIN " +
                Schema.Transaction.TABLE + " AS t2 ON " + Schema.Transfer.TRANSACTION_TO + " = t2." +
                Schema.Transaction.ID + " AND t2." + Schema.Transaction.DELETED + " = 0 LEFT JOIN " +
                Schema.Transaction.TABLE + " AS t3 ON " + Schema.Transfer.TRANSACTION_TAX + " = t3." +
                Schema.Transaction.ID + " AND t3." + Schema.Transaction.DELETED + " = 0 WHERE t1." +
                Schema.Transaction.WALLET + " = ? OR t2." + Schema.Transaction.WALLET + " = ? OR t3." +
                Schema.Transaction.WALLET + " = ?"
        val args = arrayOf(walletId.toString(), walletId.toString(), walletId.toString())
        val cursor = readableDatabase.rawQuery(query, args)
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    val transferId = cursor.getLong(cursor.getColumnIndex(Schema.Transfer.ID))
                    throw SQLiteDataException(
                        Contract.ErrorCode.WALLET_USED_IN_TRANSFER,
                        String.format(Locale.ENGLISH, "The wallet (id: %d) cannot be deleted because is in use in a transfer (id: %d)", walletId, transferId)
                    )
                }
            } finally {
                cursor.close()
            }
        }
        deleteAttachmentsOf(
            Schema.TransactionAttachment.TABLE, Schema.TransactionAttachment.ATTACHMENT,
            Schema.TransactionAttachment.TRANSACTION + " IN (SELECT " + Schema.Transaction.ID +
                    " FROM " + Schema.Transaction.TABLE + " WHERE " + Schema.Transaction.WALLET +
                    " = ?) AND " + Schema.TransactionAttachment.DELETED + " = 0",
            arrayOf(walletId.toString())
        )
        val where = Schema.Transaction.WALLET + " = ?"
        val whereArgs = arrayOf(walletId.toString())
        writableDatabase.delete(Schema.Transaction.TABLE, where, whereArgs)
        val whereModel = Schema.TransactionModel.WALLET + " = ?"
        writableDatabase.delete(Schema.TransactionModel.TABLE, whereModel, whereArgs)
        val whereTransferModel = Schema.TransferModel.WALLET_FROM + " = ? OR " + Schema.TransferModel.WALLET_TO + " = ?"
        val whereTransferModelArgs = arrayOf(walletId.toString(), walletId.toString())
        writableDatabase.delete(Schema.TransferModel.TABLE, whereTransferModel, whereTransferModelArgs)
        val whereRecurrent = Schema.RecurrentTransaction.WALLET + " = ?"
        writableDatabase.delete(Schema.RecurrentTransaction.TABLE, whereRecurrent, whereArgs)
        val whereRecurrentTransfer = Schema.RecurrentTransfer.WALLET_FROM + " = ? OR " + Schema.RecurrentTransfer.WALLET_TO + " = ?"
        writableDatabase.delete(Schema.RecurrentTransfer.TABLE, whereRecurrentTransfer, whereTransferModelArgs)
        val whereSaving = Schema.Saving.WALLET + " = ?"
        writableDatabase.delete(Schema.Saving.TABLE, whereSaving, whereArgs)
        val whereDebt = Schema.Debt.WALLET + " = ?"
        writableDatabase.delete(Schema.Debt.TABLE, whereDebt, whereArgs)
        val whereBudgetWallet = Schema.BudgetWallet.WALLET + " = ?"
        writableDatabase.delete(Schema.BudgetWallet.TABLE, whereBudgetWallet, whereArgs)
        val whereWallet = Schema.Wallet.ID + " = ?"
        return writableDatabase.delete(Schema.Wallet.TABLE, whereWallet, whereArgs)
    }

    open fun getTransaction(id: Long, projection: Array<String>?): Cursor? {
        val selection = Schema.Transaction.ID + " = ?"
        val selectionArgs = arrayOf(id.toString())
        return getTransactions(projection, selection, selectionArgs, null)
    }

    open fun getTransactions(projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        val subQuery = "SELECT " +
                "t." + Schema.Transaction.ID + " AS " + Contract.Transaction.ID + ", " +
                "t." + Schema.Transaction.MONEY + " AS " + Contract.Transaction.MONEY + ", " +
                "t." + Schema.Transaction.DATE + " AS " + Contract.Transaction.DATE + ", " +
                "t." + Schema.Transaction.DESCRIPTION + " AS " + Contract.Transaction.DESCRIPTION + ", " +
                "t." + Schema.Transaction.CATEGORY + " AS " + Contract.Transaction.CATEGORY_ID + ", " +
                "c." + Schema.Category.NAME + " AS " + Contract.Transaction.CATEGORY_NAME + ", " +
                "c." + Schema.Category.ICON + " AS " + Contract.Transaction.CATEGORY_ICON + ", " +
                "c." + Schema.Category.PARENT + " AS " + Contract.Transaction.CATEGORY_PARENT_ID + ", " +
                "c." + Schema.Category.TYPE + " AS " + Contract.Transaction.CATEGORY_TYPE + ", " +
                "c." + Schema.Category.TAG + " AS " + Contract.Transaction.CATEGORY_TAG + ", " +
                "c." + Schema.Category.SHOW_REPORT + " AS " + Contract.Transaction.CATEGORY_SHOW_REPORT + ", " +
                "pc." + Schema.Category.SHOW_REPORT + " AS " + Contract.Transaction.CATEGORY_PARENT_SHOW_REPORT + ", " +
                "t." + Schema.Transaction.DIRECTION + " AS " + Contract.Transaction.DIRECTION + ", " +
                "t." + Schema.Transaction.TYPE + " AS " + Contract.Transaction.TYPE + ", " +
                "t." + Schema.Transaction.WALLET + " AS " + Contract.Transaction.WALLET_ID + ", " +
                "w." + Schema.Wallet.NAME + " AS " + Contract.Transaction.WALLET_NAME + ", " +
                "w." + Schema.Wallet.ICON + " AS " + Contract.Transaction.WALLET_ICON + ", " +
                "w." + Schema.Wallet.CURRENCY + " AS " + Contract.Transaction.WALLET_CURRENCY + ", " +
                "w." + Schema.Wallet.COUNT_IN_TOTAL + " AS " + Contract.Transaction.WALLET_COUNT_IN_TOTAL + ", " +
                "w." + Schema.Wallet.ARCHIVED + " AS " + Contract.Transaction.WALLET_ARCHIVED + ", " +
                "w." + Schema.Wallet.TAG + " AS " + Contract.Transaction.WALLET_TAG + ", " +
                "t." + Schema.Transaction.PLACE + " AS " + Contract.Transaction.PLACE_ID + ", " +
                "p." + Schema.Place.NAME + " AS " + Contract.Transaction.PLACE_NAME + ", " +
                "p." + Schema.Place.ICON + " AS " + Contract.Transaction.PLACE_ICON + ", " +
                "p." + Schema.Place.ADDRESS + " AS " + Contract.Transaction.PLACE_ADDRESS + ", " +
                "p." + Schema.Place.LATITUDE + " AS " + Contract.Transaction.PLACE_LATITUDE + ", " +
                "p." + Schema.Place.LONGITUDE + " AS " + Contract.Transaction.PLACE_LONGITUDE + ", " +
                "p." + Schema.Place.TAG + " AS " + Contract.Transaction.PLACE_TAG + ", " +
                "t." + Schema.Transaction.EVENT + " AS " + Contract.Transaction.EVENT_ID + ", " +
                "e." + Schema.Event.NAME + " AS " + Contract.Transaction.EVENT_NAME + ", " +
                "e." + Schema.Event.ICON + " AS " + Contract.Transaction.EVENT_ICON + ", " +
                "e." + Schema.Event.NOTE + " AS " + Contract.Transaction.EVENT_NOTE + ", " +
                "e." + Schema.Event.START_DATE + " AS " + Contract.Transaction.EVENT_START_DATE + ", " +
                "e." + Schema.Event.END_DATE + " AS " + Contract.Transaction.EVENT_END_DATE + ", " +
                "e." + Schema.Event.TAG + " AS " + Contract.Transaction.EVENT_TAG + ", " +
                "t." + Schema.Transaction.NOTE + " AS " + Contract.Transaction.NOTE + ", " +
                "t." + Schema.Transaction.DEBT + " AS " + Contract.Transaction.DEBT_ID + ", " +
                "t." + Schema.Transaction.SAVING + " AS " + Contract.Transaction.SAVING_ID + ", " +
                "t." + Schema.Transaction.RECURRENCE + " AS " + Contract.Transaction.RECURRENCE_ID + ", " +
                "t." + Schema.Transaction.CONFIRMED + " AS " + Contract.Transaction.CONFIRMED + ", " +
                "t." + Schema.Transaction.COUNT_IN_TOTAL + " AS " + Contract.Transaction.COUNT_IN_TOTAL + ", " +
                "t." + Schema.Transaction.TAG + " AS " + Contract.Transaction.TAG + ", " +
                "GROUP_CONCAT('<' || pe." + Schema.Person.ID + " || '>') AS " + Contract.Transaction.PEOPLE_IDS + " " +
                "FROM " + Schema.Transaction.TABLE + " AS t LEFT JOIN " + Schema.Category.TABLE +
                " AS c ON t." + Schema.Transaction.CATEGORY + " = c." + Schema.Category.ID + " AND c." +
                Schema.Category.DELETED + " = 0 LEFT JOIN " + Schema.Category.TABLE + " AS pc ON c." +
                Schema.Category.PARENT + " = pc." + Schema.Category.ID + " AND pc." +
                Schema.Category.DELETED + " = 0 JOIN " + Schema.Wallet.TABLE + " AS w ON t." +
                Schema.Transaction.WALLET + " = w." + Schema.Wallet.ID + " AND w." +
                Schema.Wallet.DELETED + " = 0 LEFT JOIN " + Schema.TransactionPeople.TABLE + " AS tp ON t." +
                Schema.Transaction.ID + " = tp." + Schema.TransactionPeople.TRANSACTION + " AND tp." +
                Schema.TransactionPeople.DELETED + " = 0 LEFT JOIN " + Schema.Person.TABLE + " AS pe ON tp." +
                Schema.TransactionPeople.PERSON + " = pe." + Schema.Person.ID + " AND pe." +
                Schema.Person.DELETED + " = 0 LEFT JOIN " + Schema.Place.TABLE + " AS p ON t." +
                Schema.Transaction.PLACE + " = " + Schema.Place.ID + " AND p." + Schema.Place.DELETED +
                " = 0 LEFT JOIN " + Schema.Event.TABLE + " AS e ON t." + Schema.Transaction.EVENT +
                " = e." + Schema.Event.ID + " AND e." + Schema.Event.DELETED + " = 0 WHERE t." +
                Schema.Transaction.DELETED + " = 0 GROUP BY t." + Schema.Transaction.ID
        return queryFrom(subQuery, projection, selection, selectionArgs, sortOrder)
    }

    open fun insertTransaction(contentValues: ContentValues): Long {
        var transactionUUID = UUID.randomUUID().toString()
        var lastEdit = System.currentTimeMillis()
        if (contentValues.containsKey(Contract.Transaction.RECURRENCE_ID)) {
            val recurrenceId = contentValues.getAsLong(Contract.Transaction.RECURRENCE_ID) ?: 0L
            val table = Schema.RecurrentTransaction.TABLE
            val projection = arrayOf(Schema.RecurrentTransaction.UUID)
            val selection = Schema.RecurrentTransaction.ID + " = ?"
            val selectionArgs = arrayOf(recurrenceId.toString())
            val cursor = readableDatabase.query(table, projection, selection, selectionArgs, null, null, null)
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    val recurrenceUUID = cursor.getString(cursor.getColumnIndex(Schema.RecurrentTransaction.UUID))
                    val dateString = contentValues.getAsString(Contract.Transaction.DATE)
                    val date = if (dateString != null) DateUtils.getDateFromSQLDateTimeString(dateString) else null
                    if (recurrenceUUID != null && date != null) {
                        transactionUUID = getRecurrentItemUUID(recurrenceUUID, date)
                        lastEdit = date.time
                    }
                }
                cursor.close()
            }
        }
        val cv = ContentValues()
        cv.put(Schema.Transaction.MONEY, contentValues.getAsLong(Contract.Transaction.MONEY))
        cv.put(Schema.Transaction.DATE, contentValues.getAsString(Contract.Transaction.DATE))
        cv.put(Schema.Transaction.DESCRIPTION, contentValues.getAsString(Contract.Transaction.DESCRIPTION))
        cv.put(Schema.Transaction.CATEGORY, contentValues.getAsLong(Contract.Transaction.CATEGORY_ID))
        cv.put(Schema.Transaction.DIRECTION, contentValues.getAsInteger(Contract.Transaction.DIRECTION))
        cv.put(Schema.Transaction.TYPE, contentValues.getAsInteger(Contract.Transaction.TYPE))
        cv.put(Schema.Transaction.WALLET, contentValues.getAsLong(Contract.Transaction.WALLET_ID))
        cv.put(Schema.Transaction.PLACE, contentValues.getAsLong(Contract.Transaction.PLACE_ID))
        cv.put(Schema.Transaction.NOTE, contentValues.getAsString(Contract.Transaction.NOTE))
        cv.put(Schema.Transaction.SAVING, contentValues.getAsLong(Contract.Transaction.SAVING_ID))
        cv.put(Schema.Transaction.DEBT, contentValues.getAsLong(Contract.Transaction.DEBT_ID))
        cv.put(Schema.Transaction.EVENT, contentValues.getAsLong(Contract.Transaction.EVENT_ID))
        cv.put(Schema.Transaction.RECURRENCE, contentValues.getAsLong(Contract.Transaction.RECURRENCE_ID))
        cv.put(Schema.Transaction.CONFIRMED, contentValues.getAsBoolean(Contract.Transaction.CONFIRMED))
        cv.put(Schema.Transaction.COUNT_IN_TOTAL, contentValues.getAsBoolean(Contract.Transaction.COUNT_IN_TOTAL))
        cv.put(Schema.Transaction.TAG, contentValues.getAsString(Contract.Transaction.TAG))
        cv.put(Schema.Transaction.UUID, transactionUUID)
        cv.put(Schema.Transaction.LAST_EDIT, lastEdit)
        cv.put(Schema.Transaction.DELETED, false)
        val transactionId = writableDatabase.insert(Schema.Transaction.TABLE, null, cv)
        if (transactionId > 0L) {
            val peopleIds = parseIds(contentValues.getAsString(Contract.Transaction.PEOPLE_IDS))
            if (peopleIds != null) {
                for (personId in peopleIds) {
                    val cvp = ContentValues()
                    cvp.put(Schema.TransactionPeople.TRANSACTION, transactionId)
                    cvp.put(Schema.TransactionPeople.PERSON, personId)
                    cvp.put(Schema.TransactionPeople.UUID, UUID.randomUUID().toString())
                    cvp.put(Schema.TransactionPeople.LAST_EDIT, System.currentTimeMillis())
                    cvp.put(Schema.TransactionPeople.DELETED, false)
                    writableDatabase.insert(Schema.TransactionPeople.TABLE, null, cvp)
                }
            }
            val attachmentsIds = parseIds(contentValues.getAsString(Contract.Transaction.ATTACHMENT_IDS))
            if (attachmentsIds != null) {
                for (attachmentId in attachmentsIds) {
                    val cva = ContentValues()
                    cva.put(Schema.TransactionAttachment.TRANSACTION, transactionId)
                    cva.put(Schema.TransactionAttachment.ATTACHMENT, attachmentId)
                    cva.put(Schema.TransactionAttachment.UUID, UUID.randomUUID().toString())
                    cva.put(Schema.TransactionAttachment.LAST_EDIT, System.currentTimeMillis())
                    cva.put(Schema.TransactionAttachment.DELETED, false)
                    writableDatabase.insert(Schema.TransactionAttachment.TABLE, null, cva)
                }
            }
        }
        return transactionId
    }

    open fun getTransactionAttachments(id: Long, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        val subQuery = "SELECT " +
                "a." + Schema.Attachment.ID + " AS " + Contract.Attachment.ID + ", " +
                "a." + Schema.Attachment.FILE + " AS " + Contract.Attachment.FILE + ", " +
                "a." + Schema.Attachment.NAME + " AS " + Contract.Attachment.NAME + ", " +
                "a." + Schema.Attachment.TYPE + " AS " + Contract.Attachment.TYPE + ", " +
                "a." + Schema.Attachment.SIZE + " AS " + Contract.Attachment.SIZE + ", " +
                "a." + Schema.Attachment.TAG + " AS " + Contract.Attachment.TAG + " " +
                "FROM " + Schema.TransactionAttachment.TABLE + " AS ta JOIN " + Schema.Attachment.TABLE + " AS a " +
                "ON ta." + Schema.TransactionAttachment.ATTACHMENT + " = a." + Schema.Attachment.ID + " AND ta." +
                Schema.TransactionAttachment.DELETED + " = 0 AND a." + Schema.Attachment.DELETED + " = 0 AND " +
                Schema.TransactionAttachment.TRANSACTION + " = " + id.toString()
        return queryFrom(subQuery, projection, selection, selectionArgs, sortOrder)
    }

    open fun getTransactionPeople(id: Long, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        val subQuery = "SELECT " +
                Schema.Person.ID + " AS " + Contract.Person.ID + ", " +
                Schema.Person.NAME + " AS " + Contract.Person.NAME + ", " +
                Schema.Person.ICON + " AS " + Contract.Person.ICON + ", " +
                Schema.Person.NOTE + " AS " + Contract.Person.NOTE + ", " +
                Schema.Person.TAG + " AS " + Contract.Person.TAG + " " +
                "FROM " + Schema.TransactionPeople.TABLE + " AS dp JOIN " + Schema.Person.TABLE + " AS p " +
                "ON dp." + Schema.TransactionPeople.PERSON + " = p." + Schema.Person.ID + " AND dp." +
                Schema.TransactionPeople.DELETED + " = 0 AND p." + Schema.Person.DELETED + " = 0 AND " +
                Schema.TransactionPeople.TRANSACTION + " = " + id.toString()
        return queryFrom(subQuery, projection, selection, selectionArgs, sortOrder)
    }

    open fun updateTransaction(transactionId: Long, contentValues: ContentValues): Int {
        checkDebtOfMasterTransactionWallet(transactionId, contentValues)
        val rows = updateTransactionRow(transactionId, contentValues)
        if (rows > 0) {
            syncDebtOfMasterTransaction(transactionId, contentValues)
        }
        return rows
    }

    private fun updateTransactionRow(transactionId: Long, contentValues: ContentValues): Int {
        val projection = arrayOf(Schema.Transfer.ID)
        val where = Schema.Transfer.TRANSACTION_FROM + " = ? OR " + Schema.Transfer.TRANSACTION_TO +
                " = ? OR " + Schema.Transfer.TRANSACTION_TAX + " = ? AND " + Schema.Transfer.DELETED + " = 0"
        val whereArgs = arrayOf(transactionId.toString(), transactionId.toString(), transactionId.toString())
        val cursor = readableDatabase.query(Schema.Transfer.TABLE, projection, where, whereArgs, null, null, null)
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    val transferId = cursor.getLong(cursor.getColumnIndex(Schema.Transfer.ID))
                    throw SQLiteDataException(
                        Contract.ErrorCode.TRANSACTION_USED_IN_TRANSFER,
                        String.format(Locale.ENGLISH, "The transaction (id: %d) cannot be updated because it is part of a transfer (id: %d)", transactionId, transferId)
                    )
                }
            } finally {
                cursor.close()
            }
        }
        var cv = ContentValues()
        if (contentValues.containsKey(Contract.Transaction.MONEY)) {
            cv.put(Schema.Transaction.MONEY, contentValues.getAsLong(Contract.Transaction.MONEY))
        }
        if (contentValues.containsKey(Contract.Transaction.DATE)) {
            cv.put(Schema.Transaction.DATE, contentValues.getAsString(Contract.Transaction.DATE))
        }
        if (contentValues.containsKey(Contract.Transaction.DESCRIPTION)) {
            cv.put(Schema.Transaction.DESCRIPTION, contentValues.getAsString(Contract.Transaction.DESCRIPTION))
        }
        if (contentValues.containsKey(Contract.Transaction.CATEGORY_ID)) {
            cv.put(Schema.Transaction.CATEGORY, contentValues.getAsLong(Contract.Transaction.CATEGORY_ID))
        }
        if (contentValues.containsKey(Contract.Transaction.DIRECTION)) {
            cv.put(Schema.Transaction.DIRECTION, contentValues.getAsInteger(Contract.Transaction.DIRECTION))
        }
        if (contentValues.containsKey(Contract.Transaction.WALLET_ID)) {
            cv.put(Schema.Transaction.WALLET, contentValues.getAsLong(Contract.Transaction.WALLET_ID))
        }
        if (contentValues.containsKey(Contract.Transaction.PLACE_ID)) {
            cv.put(Schema.Transaction.PLACE, contentValues.getAsLong(Contract.Transaction.PLACE_ID))
        }
        if (contentValues.containsKey(Contract.Transaction.NOTE)) {
            cv.put(Schema.Transaction.NOTE, contentValues.getAsString(Contract.Transaction.NOTE))
        }
        if (contentValues.containsKey(Contract.Transaction.SAVING_ID)) {
            cv.put(Schema.Transaction.SAVING, contentValues.getAsLong(Contract.Transaction.SAVING_ID))
        }
        if (contentValues.containsKey(Contract.Transaction.DEBT_ID)) {
            cv.put(Schema.Transaction.DEBT, contentValues.getAsLong(Contract.Transaction.DEBT_ID))
        }
        if (contentValues.containsKey(Contract.Transaction.EVENT_ID)) {
            cv.put(Schema.Transaction.EVENT, contentValues.getAsLong(Contract.Transaction.EVENT_ID))
        }
        if (contentValues.containsKey(Contract.Transaction.CONFIRMED)) {
            cv.put(Schema.Transaction.CONFIRMED, contentValues.getAsBoolean(Contract.Transaction.CONFIRMED))
        }
        if (contentValues.containsKey(Contract.Transaction.COUNT_IN_TOTAL)) {
            cv.put(Schema.Transaction.COUNT_IN_TOTAL, contentValues.getAsBoolean(Contract.Transaction.COUNT_IN_TOTAL))
        }
        if (contentValues.containsKey(Contract.Transaction.TAG)) {
            cv.put(Schema.Transaction.TAG, contentValues.getAsString(Contract.Transaction.TAG))
        }
        cv.put(Schema.Transaction.LAST_EDIT, System.currentTimeMillis())
        val updateWhere = Schema.Transaction.ID + " = ?"
        val updateWhereArgs = arrayOf(transactionId.toString())
        val rows = writableDatabase.update(Schema.Transaction.TABLE, cv, updateWhere, updateWhereArgs)
        if (rows > 0) {
            if (contentValues.containsKey(Contract.Transaction.PEOPLE_IDS)) {
                cv = ContentValues()
                cv.put(Schema.TransactionPeople.DELETED, true)
                val pWhere = Schema.TransactionPeople.TRANSACTION + " = ?"
                val pWhereArgs = arrayOf(transactionId.toString())
                writableDatabase.update(Schema.TransactionPeople.TABLE, cv, pWhere, pWhereArgs)
                val peopleIds = parseIds(contentValues.getAsString(Contract.Transaction.PEOPLE_IDS))
                if (peopleIds != null) {
                    for (personId in peopleIds) {
                        cv = ContentValues()
                        cv.put(Schema.TransactionPeople.TRANSACTION, transactionId)
                        cv.put(Schema.TransactionPeople.PERSON, personId)
                        cv.put(Schema.TransactionPeople.UUID, UUID.randomUUID().toString())
                        cv.put(Schema.TransactionPeople.LAST_EDIT, System.currentTimeMillis())
                        cv.put(Schema.TransactionPeople.DELETED, false)
                        val newId = writableDatabase.insertWithOnConflict(Schema.TransactionPeople.TABLE, null, cv, SQLiteDatabase.CONFLICT_IGNORE)
                        if (newId == -1L) {
                            cv.clear()
                            cv.put(Schema.TransactionPeople.LAST_EDIT, System.currentTimeMillis())
                            cv.put(Schema.TransactionPeople.DELETED, false)
                            val conflictWhere = Schema.TransactionPeople.TRANSACTION + " = ? AND " + Schema.TransactionPeople.PERSON + " = ?"
                            val conflictWhereArgs = arrayOf(transactionId.toString(), personId.toString())
                            writableDatabase.update(Schema.TransactionPeople.TABLE, cv, conflictWhere, conflictWhereArgs)
                        }
                    }
                }
            }
            if (contentValues.containsKey(Contract.Transaction.ATTACHMENT_IDS)) {
                cv = ContentValues()
                cv.put(Schema.TransactionAttachment.DELETED, true)
                val aWhere = Schema.TransactionAttachment.TRANSACTION + " = ?"
                val aWhereArgs = arrayOf(transactionId.toString())
                writableDatabase.update(Schema.TransactionAttachment.TABLE, cv, aWhere, aWhereArgs)
                val attachmentIds = parseIds(contentValues.getAsString(Contract.Transaction.ATTACHMENT_IDS))
                if (attachmentIds != null) {
                    for (attachmentId in attachmentIds) {
                        cv = ContentValues()
                        cv.put(Schema.TransactionAttachment.TRANSACTION, transactionId)
                        cv.put(Schema.TransactionAttachment.ATTACHMENT, attachmentId)
                        cv.put(Schema.TransactionAttachment.UUID, UUID.randomUUID().toString())
                        cv.put(Schema.TransactionAttachment.LAST_EDIT, System.currentTimeMillis())
                        cv.put(Schema.TransactionAttachment.DELETED, false)
                        val newId = writableDatabase.insertWithOnConflict(Schema.TransactionAttachment.TABLE, null, cv, SQLiteDatabase.CONFLICT_IGNORE)
                        if (newId == -1L) {
                            cv.clear()
                            cv.put(Schema.TransactionAttachment.LAST_EDIT, System.currentTimeMillis())
                            cv.put(Schema.TransactionAttachment.DELETED, false)
                            val conflictWhere = Schema.TransactionAttachment.TRANSACTION + " = ? AND " + Schema.TransactionAttachment.ATTACHMENT + " = ?"
                            val conflictWhereArgs = arrayOf(transactionId.toString(), attachmentId.toString())
                            writableDatabase.update(Schema.TransactionAttachment.TABLE, cv, conflictWhere, conflictWhereArgs)
                        }
                    }
                }
            }
            val delPeopleWhere = Schema.TransactionPeople.TRANSACTION + " = ? AND " + Schema.TransactionPeople.DELETED + " = 1"
            val delWhereArgs = arrayOf(transactionId.toString())
            writableDatabase.delete(Schema.TransactionPeople.TABLE, delPeopleWhere, delWhereArgs)
            val delAttWhere = Schema.TransactionAttachment.TRANSACTION + " = ? AND " + Schema.TransactionAttachment.DELETED + " = 1"
            writableDatabase.delete(Schema.TransactionAttachment.TABLE, delAttWhere, delWhereArgs)
        }
        return rows
    }

    open fun deleteTransaction(transactionId: Long): Int {
        val projection = arrayOf(Schema.Transfer.ID)
        val where = Schema.Transfer.TRANSACTION_FROM + " = ? OR " + Schema.Transfer.TRANSACTION_TO +
                " = ? OR " + Schema.Transfer.TRANSACTION_TAX + " = ? AND " + Schema.Transfer.DELETED + " = 0"
        val whereArgs = arrayOf(transactionId.toString(), transactionId.toString(), transactionId.toString())
        val cursor = readableDatabase.query(Schema.Transfer.TABLE, projection, where, whereArgs, null, null, null)
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    val transferId = cursor.getLong(cursor.getColumnIndex(Schema.Transfer.ID))
                    throw SQLiteDataException(
                        Contract.ErrorCode.TRANSACTION_USED_IN_TRANSFER,
                        String.format(Locale.ENGLISH, "The transaction (id: %d) cannot be deleted because it is part of a transfer (id: %d)", transactionId, transferId)
                    )
                }
            } finally {
                cursor.close()
            }
        }
        return deleteTransactionItem(transactionId)
    }

    private fun deleteTransactionItem(transactionId: Long): Int {
        deleteAttachmentsOf(
            Schema.TransactionAttachment.TABLE, Schema.TransactionAttachment.ATTACHMENT,
            Schema.TransactionAttachment.TRANSACTION + " = ? AND " + Schema.TransactionAttachment.DELETED + " = 0",
            arrayOf(transactionId.toString())
        )
        val where = Schema.TransactionPeople.TRANSACTION + " = ?"
        val whereArgs = arrayOf(transactionId.toString())
        writableDatabase.delete(Schema.TransactionPeople.TABLE, where, whereArgs)
        val attWhere = Schema.TransactionAttachment.TRANSACTION + " = ?"
        writableDatabase.delete(Schema.TransactionAttachment.TABLE, attWhere, whereArgs)
        val txnWhere = Schema.Transaction.ID + " = ?"
        return writableDatabase.delete(Schema.Transaction.TABLE, txnWhere, whereArgs)
    }

    private fun deleteTransactionItems(cursor: Cursor): Int {
        val cache = ArrayList<Long>()
        try {
            while (cursor.moveToNext()) {
                cache.add(cursor.getLong(cursor.getColumnIndex(Contract.Transaction.ID)))
            }
        } finally {
            cursor.close()
        }
        var rows = 0
        for (transactionId in cache) {
            rows += deleteTransaction(transactionId)
        }
        return rows
    }

    open fun getTransfer(id: Long, projection: Array<String>?): Cursor? {
        val selection = Schema.Transfer.ID + " = ?"
        val selectionArgs = arrayOf(id.toString())
        return getTransfers(projection, selection, selectionArgs, null)
    }

    open fun getTransfers(projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        val subQuery = "SELECT " +
                "t." + Schema.Transfer.ID + " AS " + Contract.Transfer.ID + ", " +
                "t." + Schema.Transfer.DESCRIPTION + " AS " + Contract.Transfer.DESCRIPTION + ", " +
                "t." + Schema.Transfer.DATE + " AS " + Contract.Transfer.DATE + ", " +
                "t." + Schema.Transfer.TRANSACTION_FROM + " AS " + Contract.Transfer.TRANSACTION_FROM_ID + ", " +
                "t1." + Schema.Transaction.MONEY + " AS " + Contract.Transfer.TRANSACTION_FROM_MONEY + ", " +
                "w1." + Schema.Wallet.ID + " AS " + Contract.Transfer.TRANSACTION_FROM_WALLET_ID + ", " +
                "w1." + Schema.Wallet.NAME + " AS " + Contract.Transfer.TRANSACTION_FROM_WALLET_NAME + ", " +
                "w1." + Schema.Wallet.ICON + " AS " + Contract.Transfer.TRANSACTION_FROM_WALLET_ICON + ", " +
                "w1." + Schema.Wallet.CURRENCY + " AS " + Contract.Transfer.TRANSACTION_FROM_WALLET_CURRENCY + ", " +
                "w1." + Schema.Wallet.COUNT_IN_TOTAL + " AS " + Contract.Transfer.TRANSACTION_FROM_WALLET_COUNT_IN_TOTAL + ", " +
                "w1." + Schema.Wallet.ARCHIVED + " AS " + Contract.Transfer.TRANSACTION_FROM_WALLET_ARCHIVED + ", " +
                "w1." + Schema.Wallet.TAG + " AS " + Contract.Transfer.TRANSACTION_FROM_WALLET_TAG + ", " +
                "t1." + Schema.Transaction.TAG + " AS " + Contract.Transfer.TRANSACTION_FROM_TAG + ", " +
                "t." + Schema.Transfer.TRANSACTION_TO + " AS " + Contract.Transfer.TRANSACTION_TO_ID + ", " +
                "t2." + Schema.Transaction.MONEY + " AS " + Contract.Transfer.TRANSACTION_TO_MONEY + ", " +
                "w2." + Schema.Wallet.ID + " AS " + Contract.Transfer.TRANSACTION_TO_WALLET_ID + ", " +
                "w2." + Schema.Wallet.NAME + " AS " + Contract.Transfer.TRANSACTION_TO_WALLET_NAME + ", " +
                "w2." + Schema.Wallet.ICON + " AS " + Contract.Transfer.TRANSACTION_TO_WALLET_ICON + ", " +
                "w2." + Schema.Wallet.CURRENCY + " AS " + Contract.Transfer.TRANSACTION_TO_WALLET_CURRENCY + ", " +
                "w2." + Schema.Wallet.COUNT_IN_TOTAL + " AS " + Contract.Transfer.TRANSACTION_TO_WALLET_COUNT_IN_TOTAL + ", " +
                "w2." + Schema.Wallet.ARCHIVED + " AS " + Contract.Transfer.TRANSACTION_TO_WALLET_ARCHIVED + ", " +
                "w2." + Schema.Wallet.TAG + " AS " + Contract.Transfer.TRANSACTION_TO_WALLET_TAG + ", " +
                "t2." + Schema.Transaction.TAG + " AS " + Contract.Transfer.TRANSACTION_TO_TAG + ", " +
                "t." + Schema.Transfer.TRANSACTION_TAX + " AS " + Contract.Transfer.TRANSACTION_TAX_ID + ", " +
                "t3." + Schema.Transaction.MONEY + " AS " + Contract.Transfer.TRANSACTION_TAX_MONEY + ", " +
                "w3." + Schema.Wallet.ID + " AS " + Contract.Transfer.TRANSACTION_TAX_WALLET_ID + ", " +
                "w3." + Schema.Wallet.NAME + " AS " + Contract.Transfer.TRANSACTION_TAX_WALLET_NAME + ", " +
                "w3." + Schema.Wallet.CURRENCY + " AS " + Contract.Transfer.TRANSACTION_TAX_WALLET_CURRENCY + ", " +
                "w3." + Schema.Wallet.COUNT_IN_TOTAL + " AS " + Contract.Transfer.TRANSACTION_TAX_WALLET_COUNT_IN_TOTAL + ", " +
                "w3." + Schema.Wallet.ARCHIVED + " AS " + Contract.Transfer.TRANSACTION_TAX_WALLET_ARCHIVED + ", " +
                "w3." + Schema.Wallet.TAG + " AS " + Contract.Transfer.TRANSACTION_TAX_WALLET_TAG + ", " +
                "t3." + Schema.Transaction.TAG + " AS " + Contract.Transfer.TRANSACTION_TAX_TAG + ", " +
                "t." + Schema.Transfer.NOTE + " AS " + Contract.Transfer.NOTE + ", " +
                "t." + Schema.Transfer.PLACE + " AS " + Contract.Transfer.PLACE_ID + ", " +
                "p." + Schema.Place.NAME + " AS " + Contract.Transfer.PLACE_NAME + ", " +
                "p." + Schema.Place.ICON + " AS " + Contract.Transfer.PLACE_ICON + ", " +
                "p." + Schema.Place.ADDRESS + " AS " + Contract.Transfer.PLACE_ADDRESS + ", " +
                "p." + Schema.Place.LATITUDE + " AS " + Contract.Transfer.PLACE_LATITUDE + ", " +
                "p." + Schema.Place.LONGITUDE + " AS " + Contract.Transfer.PLACE_LONGITUDE + ", " +
                "p." + Schema.Place.TAG + " AS " + Contract.Transfer.PLACE_TAG + ", " +
                "t." + Schema.Transfer.EVENT + " AS " + Contract.Transfer.EVENT_ID + ", " +
                "e." + Schema.Event.NAME + " AS " + Contract.Transfer.EVENT_NAME + ", " +
                "e." + Schema.Event.ICON + " AS " + Contract.Transfer.EVENT_ICON + ", " +
                "e." + Schema.Event.NOTE + " AS " + Contract.Transfer.EVENT_NOTE + ", " +
                "e." + Schema.Event.START_DATE + " AS " + Contract.Transfer.EVENT_START_DATE + ", " +
                "e." + Schema.Event.END_DATE + " AS " + Contract.Transfer.EVENT_END_DATE + ", " +
                "e." + Schema.Event.TAG + " AS " + Contract.Transfer.EVENT_TAG + ", " +
                "t." + Schema.Transfer.RECURRENCE + " AS " + Contract.Transfer.RECURRENCE_ID + ", " +
                "t." + Schema.Transfer.CONFIRMED + " AS " + Contract.Transfer.CONFIRMED + ", " +
                "t." + Schema.Transfer.COUNT_IN_TOTAL + " AS " + Contract.Transfer.COUNT_IN_TOTAL + ", " +
                "t." + Schema.Transfer.TAG + " AS " + Contract.Transfer.TAG + ", " +
                "GROUP_CONCAT('<' || pe." + Schema.Person.ID + " || '>') AS " + Contract.Transfer.PEOPLE_IDS + " " +
                "FROM transfers AS t \n" +
                "JOIN transactions AS t1 ON transfer_transaction_from = t1.transaction_id AND t1.deleted = 0 \n" +
                "JOIN wallets AS w1 ON t1.transaction_wallet = w1.wallet_id AND w1.deleted = 0\n" +
                "JOIN transactions AS t2 ON transfer_transaction_to = t2.transaction_id AND t2.deleted = 0\n" +
                "JOIN wallets AS w2 ON t2.transaction_wallet = w2.wallet_id AND w2.deleted = 0\n" +
                "LEFT JOIN transactions AS t3 ON transfer_transaction_tax = t3.transaction_id AND t3.deleted = 0\n" +
                "LEFT JOIN wallets AS w3 ON t3.transaction_wallet = w3.wallet_id AND w3.deleted = 0\n" +
                "LEFT JOIN transfer_people AS tp ON t.transfer_id = tp._transfer AND tp.deleted = 0\n" +
                "LEFT JOIN people AS pe ON tp._person = pe.person_id AND pe.deleted = 0\n" +
                "LEFT JOIN places AS p ON transfer_place = place_id AND p.deleted = 0 \n" +
                "LEFT JOIN events AS e ON transfer_event = event_id AND e.deleted = 0 \n" +
                "WHERE t.deleted = 0\n" +
                "GROUP BY t.transfer_id"
        return queryFrom(subQuery, projection, selection, selectionArgs, sortOrder)
    }

    private fun getTransferTransactionIds(transferId: Long): Array<Long?> {
        val transactionIds = arrayOfNulls<Long>(3)
        val projection = arrayOf(
            Schema.Transfer.TRANSACTION_FROM,
            Schema.Transfer.TRANSACTION_TO,
            Schema.Transfer.TRANSACTION_TAX
        )
        val selection = Schema.Transfer.ID + " = ?"
        val selectionArgs = arrayOf(transferId.toString())
        val cursor = getTransfers(projection, selection, selectionArgs, null)
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                transactionIds[0] = cursor.getLong(cursor.getColumnIndex(Schema.Transfer.TRANSACTION_FROM))
                transactionIds[1] = cursor.getLong(cursor.getColumnIndex(Schema.Transfer.TRANSACTION_TO))
                if (!cursor.isNull(cursor.getColumnIndex(Schema.Transfer.TRANSACTION_TAX))) {
                    transactionIds[2] = cursor.getLong(cursor.getColumnIndex(Schema.Transfer.TRANSACTION_TAX))
                } else {
                    transactionIds[2] = null
                }
            }
            cursor.close()
        }
        return transactionIds
    }

    open fun getTransferAttachments(id: Long, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        val subQuery = "SELECT " +
                "a." + Schema.Attachment.ID + " AS " + Contract.Attachment.ID + ", " +
                "a." + Schema.Attachment.FILE + " AS " + Contract.Attachment.FILE + ", " +
                "a." + Schema.Attachment.NAME + " AS " + Contract.Attachment.NAME + ", " +
                "a." + Schema.Attachment.TYPE + " AS " + Contract.Attachment.TYPE + ", " +
                "a." + Schema.Attachment.SIZE + " AS " + Contract.Attachment.SIZE + ", " +
                "a." + Schema.Attachment.TAG + " AS " + Contract.Attachment.TAG + " " +
                "FROM " + Schema.TransferAttachment.TABLE + " AS ta JOIN " + Schema.Attachment.TABLE + " AS a " +
                "ON ta." + Schema.TransferAttachment.ATTACHMENT + " = a." + Schema.Attachment.ID + " AND ta." +
                Schema.TransferAttachment.DELETED + " = 0 AND a." + Schema.Attachment.DELETED + " = 0 AND " +
                Schema.TransferAttachment.TRANSFER + " = " + id.toString()
        return queryFrom(subQuery, projection, selection, selectionArgs, sortOrder)
    }

    open fun getTransferPeople(id: Long, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        val subQuery = "SELECT " +
                Schema.Person.ID + " AS " + Contract.Person.ID + ", " +
                Schema.Person.NAME + " AS " + Contract.Person.NAME + ", " +
                Schema.Person.ICON + " AS " + Contract.Person.ICON + ", " +
                Schema.Person.NOTE + " AS " + Contract.Person.NOTE + ", " +
                Schema.Person.TAG + " AS " + Contract.Person.TAG + " " +
                "FROM " + Schema.TransferPeople.TABLE + " AS dp JOIN " + Schema.Person.TABLE + " AS p " +
                "ON dp." + Schema.TransferPeople.PERSON + " = p." + Schema.Person.ID + " AND dp." +
                Schema.TransferPeople.DELETED + " = 0 AND p." + Schema.Person.DELETED + " = 0 AND " +
                Schema.TransferPeople.TRANSFER + " = " + id.toString()
        return queryFrom(subQuery, projection, selection, selectionArgs, sortOrder)
    }

    open fun insertTransfer(contentValues: ContentValues): Long {
        val transferCategoryId = getSystemCategoryId(Schema.CategoryTag.TRANSFER)
        val transferTaxCategoryId = getSystemCategoryId(Schema.CategoryTag.TRANSFER_TAX)
        var transferUUID = UUID.randomUUID().toString()
        var lastEdit = System.currentTimeMillis()
        if (contentValues.containsKey(Contract.Transfer.RECURRENCE_ID)) {
            val recurrenceId = contentValues.getAsLong(Contract.Transfer.RECURRENCE_ID) ?: 0L
            val table = Schema.RecurrentTransfer.TABLE
            val projection = arrayOf(Schema.RecurrentTransfer.UUID)
            val selection = Schema.RecurrentTransfer.ID + " = ?"
            val selectionArgs = arrayOf(recurrenceId.toString())
            val cursor = readableDatabase.query(table, projection, selection, selectionArgs, null, null, null)
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    val recurrenceUUID = cursor.getString(cursor.getColumnIndex(Schema.RecurrentTransfer.UUID))
                    val dateString = contentValues.getAsString(Contract.Transfer.DATE)
                    val date = if (dateString != null) DateUtils.getDateFromSQLDateTimeString(dateString) else null
                    if (recurrenceUUID != null && date != null) {
                        transferUUID = getRecurrentItemUUID(recurrenceUUID, date)
                        lastEdit = date.time
                    }
                }
                cursor.close()
            }
        }
        var cv = ContentValues()
        cv.put(Schema.Transaction.MONEY, contentValues.getAsLong(Contract.Transfer.TRANSACTION_FROM_MONEY))
        cv.put(Schema.Transaction.DATE, contentValues.getAsString(Contract.Transfer.DATE))
        cv.put(Schema.Transaction.DESCRIPTION, contentValues.getAsString(Contract.Transfer.DESCRIPTION))
        cv.put(Schema.Transaction.CATEGORY, transferCategoryId)
        cv.put(Schema.Transaction.DIRECTION, Contract.Direction.EXPENSE)
        cv.put(Schema.Transaction.TYPE, Contract.TransactionType.TRANSFER)
        cv.put(Schema.Transaction.WALLET, contentValues.getAsLong(Contract.Transfer.TRANSACTION_FROM_WALLET_ID))
        cv.put(Schema.Transaction.PLACE, contentValues.getAsLong(Contract.Transfer.PLACE_ID))
        cv.put(Schema.Transaction.NOTE, contentValues.getAsString(Contract.Transfer.NOTE))
        cv.putNull(Schema.Transaction.SAVING)
        cv.putNull(Schema.Transaction.DEBT)
        cv.put(Schema.Transaction.EVENT, contentValues.getAsLong(Contract.Transfer.EVENT_ID))
        cv.put(Schema.Transaction.CONFIRMED, contentValues.getAsBoolean(Contract.Transfer.CONFIRMED))
        cv.put(Schema.Transaction.COUNT_IN_TOTAL, contentValues.getAsBoolean(Contract.Transfer.COUNT_IN_TOTAL))
        cv.put(Schema.Transaction.UUID, UUID.randomUUID().toString())
        cv.put(Schema.Transaction.LAST_EDIT, System.currentTimeMillis())
        cv.put(Schema.Transaction.DELETED, false)
        val transaction1 = writableDatabase.insert(Schema.Transaction.TABLE, null, cv)
        cv = ContentValues()
        cv.put(Schema.Transaction.MONEY, contentValues.getAsLong(Contract.Transfer.TRANSACTION_TO_MONEY))
        cv.put(Schema.Transaction.DATE, contentValues.getAsString(Contract.Transfer.DATE))
        cv.put(Schema.Transaction.DESCRIPTION, contentValues.getAsString(Contract.Transfer.DESCRIPTION))
        cv.put(Schema.Transaction.CATEGORY, transferCategoryId)
        cv.put(Schema.Transaction.DIRECTION, Contract.Direction.INCOME)
        cv.put(Schema.Transaction.TYPE, Contract.TransactionType.TRANSFER)
        cv.put(Schema.Transaction.WALLET, contentValues.getAsLong(Contract.Transfer.TRANSACTION_TO_WALLET_ID))
        cv.put(Schema.Transaction.PLACE, contentValues.getAsLong(Contract.Transfer.PLACE_ID))
        cv.put(Schema.Transaction.NOTE, contentValues.getAsString(Contract.Transfer.NOTE))
        cv.putNull(Schema.Transaction.SAVING)
        cv.putNull(Schema.Transaction.DEBT)
        cv.put(Schema.Transaction.EVENT, contentValues.getAsLong(Contract.Transfer.EVENT_ID))
        cv.put(Schema.Transaction.CONFIRMED, contentValues.getAsBoolean(Contract.Transfer.CONFIRMED))
        cv.put(Schema.Transaction.COUNT_IN_TOTAL, contentValues.getAsBoolean(Contract.Transfer.COUNT_IN_TOTAL))
        cv.put(Schema.Transaction.UUID, UUID.randomUUID().toString())
        cv.put(Schema.Transaction.LAST_EDIT, System.currentTimeMillis())
        cv.put(Schema.Transaction.DELETED, false)
        val transaction2 = writableDatabase.insert(Schema.Transaction.TABLE, null, cv)
        var transaction3: Long? = null
        val taxMoney = contentValues.getAsLong(Contract.Transfer.TRANSACTION_TAX_MONEY) ?: 0L
        if (taxMoney != 0L) {
            cv = ContentValues()
            cv.put(Schema.Transaction.MONEY, taxMoney)
            cv.put(Schema.Transaction.DATE, contentValues.getAsString(Contract.Transfer.DATE))
            cv.put(Schema.Transaction.DESCRIPTION, contentValues.getAsString(Contract.Transfer.DESCRIPTION))
            cv.put(Schema.Transaction.CATEGORY, transferTaxCategoryId)
            cv.put(Schema.Transaction.DIRECTION, Contract.Direction.EXPENSE)
            cv.put(Schema.Transaction.TYPE, Contract.TransactionType.TRANSFER)
            cv.put(Schema.Transaction.WALLET, contentValues.getAsLong(Contract.Transfer.TRANSACTION_TAX_WALLET_ID))
            cv.put(Schema.Transaction.PLACE, contentValues.getAsLong(Contract.Transfer.PLACE_ID))
            cv.put(Schema.Transaction.NOTE, contentValues.getAsString(Contract.Transfer.NOTE))
            cv.putNull(Schema.Transaction.SAVING)
            cv.putNull(Schema.Transaction.DEBT)
            cv.put(Schema.Transaction.EVENT, contentValues.getAsLong(Contract.Transfer.EVENT_ID))
            cv.put(Schema.Transaction.CONFIRMED, contentValues.getAsBoolean(Contract.Transfer.CONFIRMED))
            cv.put(Schema.Transaction.COUNT_IN_TOTAL, contentValues.getAsBoolean(Contract.Transfer.COUNT_IN_TOTAL))
            cv.put(Schema.Transaction.UUID, UUID.randomUUID().toString())
            cv.put(Schema.Transaction.LAST_EDIT, System.currentTimeMillis())
            cv.put(Schema.Transaction.DELETED, false)
            transaction3 = writableDatabase.insert(Schema.Transaction.TABLE, null, cv)
        }
        cv = ContentValues()
        cv.put(Schema.Transfer.DESCRIPTION, contentValues.getAsString(Contract.Transfer.DESCRIPTION))
        cv.put(Schema.Transfer.DATE, contentValues.getAsString(Contract.Transfer.DATE))
        cv.put(Schema.Transfer.TRANSACTION_FROM, transaction1)
        cv.put(Schema.Transfer.TRANSACTION_TO, transaction2)
        cv.put(Schema.Transfer.TRANSACTION_TAX, transaction3)
        cv.put(Schema.Transfer.NOTE, contentValues.getAsString(Contract.Transfer.NOTE))
        cv.put(Schema.Transfer.PLACE, contentValues.getAsLong(Contract.Transfer.PLACE_ID))
        cv.put(Schema.Transfer.EVENT, contentValues.getAsLong(Contract.Transfer.EVENT_ID))
        cv.put(Schema.Transfer.CONFIRMED, contentValues.getAsBoolean(Contract.Transfer.CONFIRMED))
        cv.put(Schema.Transfer.COUNT_IN_TOTAL, contentValues.getAsBoolean(Contract.Transfer.COUNT_IN_TOTAL))
        cv.put(Schema.Transfer.TAG, contentValues.getAsString(Contract.Transfer.TAG))
        cv.put(Schema.Transfer.UUID, transferUUID)
        cv.put(Schema.Transfer.LAST_EDIT, lastEdit)
        cv.put(Schema.Transfer.DELETED, false)
        val transferId = writableDatabase.insert(Schema.Transfer.TABLE, null, cv)
        if (transferId > 0) {
            val peopleIds = parseIds(contentValues.getAsString(Contract.Transfer.PEOPLE_IDS))
            if (peopleIds != null) {
                for (personId in peopleIds) {
                    val cvp = ContentValues()
                    cvp.put(Schema.TransferPeople.TRANSFER, transferId)
                    cvp.put(Schema.TransferPeople.PERSON, personId)
                    cvp.put(Schema.TransferPeople.UUID, UUID.randomUUID().toString())
                    cvp.put(Schema.TransferPeople.LAST_EDIT, System.currentTimeMillis())
                    cvp.put(Schema.TransferPeople.DELETED, false)
                    writableDatabase.insert(Schema.TransferPeople.TABLE, null, cvp)
                }
            }
            val attachmentsIds = parseIds(contentValues.getAsString(Contract.Transfer.ATTACHMENT_IDS))
            if (attachmentsIds != null) {
                for (attachmentId in attachmentsIds) {
                    val cva = ContentValues()
                    cva.put(Schema.TransferAttachment.TRANSFER, transferId)
                    cva.put(Schema.TransferAttachment.ATTACHMENT, attachmentId)
                    cva.put(Schema.TransferAttachment.UUID, UUID.randomUUID().toString())
                    cva.put(Schema.TransferAttachment.LAST_EDIT, System.currentTimeMillis())
                    cva.put(Schema.TransferAttachment.DELETED, false)
                    writableDatabase.insert(Schema.TransferAttachment.TABLE, null, cva)
                }
            }
        }
        return transferId
    }

    open fun updateTransfer(transferId: Long, contentValues: ContentValues): Int {
        val transferCategoryId = getSystemCategoryId(Schema.CategoryTag.TRANSFER)
        val transferTaxCategoryId = getSystemCategoryId(Schema.CategoryTag.TRANSFER_TAX)
        val transactionIds = getTransferTransactionIds(transferId)
        var cv = ContentValues()
        cv.put(Schema.Transaction.MONEY, contentValues.getAsLong(Contract.Transfer.TRANSACTION_FROM_MONEY))
        cv.put(Schema.Transaction.DATE, contentValues.getAsString(Contract.Transfer.DATE))
        cv.put(Schema.Transaction.DESCRIPTION, contentValues.getAsString(Contract.Transfer.DESCRIPTION))
        cv.put(Schema.Transaction.CATEGORY, transferCategoryId)
        cv.put(Schema.Transaction.DIRECTION, Contract.Direction.EXPENSE)
        cv.put(Schema.Transaction.TYPE, Contract.TransactionType.TRANSFER)
        cv.put(Schema.Transaction.WALLET, contentValues.getAsLong(Contract.Transfer.TRANSACTION_FROM_WALLET_ID))
        cv.put(Schema.Transaction.PLACE, contentValues.getAsLong(Contract.Transfer.PLACE_ID))
        cv.put(Schema.Transaction.NOTE, contentValues.getAsString(Contract.Transfer.NOTE))
        cv.putNull(Schema.Transaction.SAVING)
        cv.putNull(Schema.Transaction.DEBT)
        cv.put(Schema.Transaction.EVENT, contentValues.getAsLong(Contract.Transfer.EVENT_ID))
        cv.put(Schema.Transaction.CONFIRMED, contentValues.getAsBoolean(Contract.Transfer.CONFIRMED))
        cv.put(Schema.Transaction.COUNT_IN_TOTAL, contentValues.getAsBoolean(Contract.Transfer.COUNT_IN_TOTAL))
        cv.put(Schema.Transaction.LAST_EDIT, System.currentTimeMillis())
        var where = Schema.Transaction.ID + " = ?"
        var whereArgs = arrayOf(transactionIds[0].toString())
        writableDatabase.update(Schema.Transaction.TABLE, cv, where, whereArgs)
        cv = ContentValues()
        cv.put(Schema.Transaction.MONEY, contentValues.getAsLong(Contract.Transfer.TRANSACTION_TO_MONEY))
        cv.put(Schema.Transaction.DATE, contentValues.getAsString(Contract.Transfer.DATE))
        cv.put(Schema.Transaction.DESCRIPTION, contentValues.getAsString(Contract.Transfer.DESCRIPTION))
        cv.put(Schema.Transaction.CATEGORY, transferCategoryId)
        cv.put(Schema.Transaction.DIRECTION, Contract.Direction.INCOME)
        cv.put(Schema.Transaction.TYPE, Contract.TransactionType.TRANSFER)
        cv.put(Schema.Transaction.WALLET, contentValues.getAsLong(Contract.Transfer.TRANSACTION_TO_WALLET_ID))
        cv.put(Schema.Transaction.PLACE, contentValues.getAsLong(Contract.Transfer.PLACE_ID))
        cv.put(Schema.Transaction.NOTE, contentValues.getAsString(Contract.Transfer.NOTE))
        cv.putNull(Schema.Transaction.SAVING)
        cv.putNull(Schema.Transaction.DEBT)
        cv.put(Schema.Transaction.EVENT, contentValues.getAsLong(Contract.Transfer.EVENT_ID))
        cv.put(Schema.Transaction.CONFIRMED, contentValues.getAsBoolean(Contract.Transfer.CONFIRMED))
        cv.put(Schema.Transaction.COUNT_IN_TOTAL, contentValues.getAsBoolean(Contract.Transfer.COUNT_IN_TOTAL))
        cv.put(Schema.Transaction.LAST_EDIT, System.currentTimeMillis())
        where = Schema.Transaction.ID + " = ?"
        whereArgs = arrayOf(transactionIds[1].toString())
        writableDatabase.update(Schema.Transaction.TABLE, cv, where, whereArgs)
        val taxMoney = contentValues.getAsLong(Contract.Transfer.TRANSACTION_TAX_MONEY) ?: 0L
        if (transactionIds[2] != null) {
            if (taxMoney != 0L) {
                cv = ContentValues()
                cv.put(Schema.Transaction.MONEY, taxMoney)
                cv.put(Schema.Transaction.DATE, contentValues.getAsString(Contract.Transfer.DATE))
                cv.put(Schema.Transaction.DESCRIPTION, contentValues.getAsString(Contract.Transfer.DESCRIPTION))
                cv.put(Schema.Transaction.CATEGORY, transferTaxCategoryId)
                cv.put(Schema.Transaction.DIRECTION, Contract.Direction.EXPENSE)
                cv.put(Schema.Transaction.TYPE, Contract.TransactionType.TRANSFER)
                cv.put(Schema.Transaction.WALLET, contentValues.getAsLong(Contract.Transfer.TRANSACTION_TAX_WALLET_ID))
                cv.put(Schema.Transaction.PLACE, contentValues.getAsLong(Contract.Transfer.PLACE_ID))
                cv.put(Schema.Transaction.NOTE, contentValues.getAsString(Contract.Transfer.NOTE))
                cv.putNull(Schema.Transaction.SAVING)
                cv.putNull(Schema.Transaction.DEBT)
                cv.put(Schema.Transaction.EVENT, contentValues.getAsLong(Contract.Transfer.EVENT_ID))
                cv.put(Schema.Transaction.CONFIRMED, contentValues.getAsBoolean(Contract.Transfer.CONFIRMED))
                cv.put(Schema.Transaction.COUNT_IN_TOTAL, contentValues.getAsBoolean(Contract.Transfer.COUNT_IN_TOTAL))
                cv.put(Schema.Transaction.LAST_EDIT, System.currentTimeMillis())
                where = Schema.Transaction.ID + " = ?"
                whereArgs = arrayOf(transactionIds[2].toString())
                writableDatabase.update(Schema.Transaction.TABLE, cv, where, whereArgs)
            } else {
                where = Schema.Transaction.ID + " = ?"
                whereArgs = arrayOf(transactionIds[2].toString())
                writableDatabase.delete(Schema.Transaction.TABLE, where, whereArgs)
                transactionIds[2] = null
            }
        } else if (taxMoney != 0L) {
            cv = ContentValues()
            cv.put(Schema.Transaction.MONEY, taxMoney)
            cv.put(Schema.Transaction.DATE, contentValues.getAsString(Contract.Transfer.DATE))
            cv.put(Schema.Transaction.DESCRIPTION, contentValues.getAsString(Contract.Transfer.DESCRIPTION))
            cv.put(Schema.Transaction.CATEGORY, transferTaxCategoryId)
            cv.put(Schema.Transaction.DIRECTION, Contract.Direction.EXPENSE)
            cv.put(Schema.Transaction.TYPE, Contract.TransactionType.TRANSFER)
            cv.put(Schema.Transaction.WALLET, contentValues.getAsLong(Contract.Transfer.TRANSACTION_TAX_WALLET_ID))
            cv.put(Schema.Transaction.PLACE, contentValues.getAsLong(Contract.Transfer.PLACE_ID))
            cv.put(Schema.Transaction.NOTE, contentValues.getAsString(Contract.Transfer.NOTE))
            cv.putNull(Schema.Transaction.SAVING)
            cv.putNull(Schema.Transaction.DEBT)
            cv.put(Schema.Transaction.EVENT, contentValues.getAsLong(Contract.Transfer.EVENT_ID))
            cv.put(Schema.Transaction.CONFIRMED, contentValues.getAsBoolean(Contract.Transfer.CONFIRMED))
            cv.put(Schema.Transaction.COUNT_IN_TOTAL, contentValues.getAsBoolean(Contract.Transfer.COUNT_IN_TOTAL))
            cv.put(Schema.Transaction.UUID, UUID.randomUUID().toString())
            cv.put(Schema.Transaction.LAST_EDIT, System.currentTimeMillis())
            cv.put(Schema.Transaction.DELETED, false)
            transactionIds[2] = writableDatabase.insert(Schema.Transaction.TABLE, null, cv)
        }
        cv = ContentValues()
        cv.put(Schema.Transfer.DESCRIPTION, contentValues.getAsString(Contract.Transfer.DESCRIPTION))
        cv.put(Schema.Transfer.DATE, contentValues.getAsString(Contract.Transfer.DATE))
        cv.put(Schema.Transfer.TRANSACTION_FROM, transactionIds[0])
        cv.put(Schema.Transfer.TRANSACTION_TO, transactionIds[1])
        cv.put(Schema.Transfer.TRANSACTION_TAX, transactionIds[2])
        cv.put(Schema.Transfer.NOTE, contentValues.getAsString(Contract.Transfer.NOTE))
        cv.put(Schema.Transfer.PLACE, contentValues.getAsLong(Contract.Transfer.PLACE_ID))
        cv.put(Schema.Transfer.EVENT, contentValues.getAsLong(Contract.Transfer.EVENT_ID))
        cv.put(Schema.Transfer.CONFIRMED, contentValues.getAsBoolean(Contract.Transfer.CONFIRMED))
        cv.put(Schema.Transfer.COUNT_IN_TOTAL, contentValues.getAsBoolean(Contract.Transfer.COUNT_IN_TOTAL))
        if (contentValues.containsKey(Schema.Transfer.TAG)) {
            cv.put(Schema.Transfer.TAG, contentValues.getAsString(Contract.Transfer.TAG))
        }
        cv.put(Schema.Transfer.LAST_EDIT, System.currentTimeMillis())
        where = Schema.Transfer.ID + " = ?"
        whereArgs = arrayOf(transferId.toString())
        val rows = writableDatabase.update(Schema.Transfer.TABLE, cv, where, whereArgs)
        if (rows > 0) {
            cv = ContentValues()
            cv.put(Schema.TransferPeople.DELETED, true)
            where = Schema.TransferPeople.TRANSFER + " = ?"
            whereArgs = arrayOf(transferId.toString())
            writableDatabase.update(Schema.TransferPeople.TABLE, cv, where, whereArgs)
            val peopleIds = parseIds(contentValues.getAsString(Contract.Transfer.PEOPLE_IDS))
            if (peopleIds != null) {
                for (personId in peopleIds) {
                    cv = ContentValues()
                    cv.put(Schema.TransferPeople.TRANSFER, transferId)
                    cv.put(Schema.TransferPeople.PERSON, personId)
                    cv.put(Schema.TransferPeople.UUID, UUID.randomUUID().toString())
                    cv.put(Schema.TransferPeople.LAST_EDIT, System.currentTimeMillis())
                    cv.put(Schema.TransferPeople.DELETED, false)
                    val newId = writableDatabase.insertWithOnConflict(Schema.TransferPeople.TABLE, null, cv, SQLiteDatabase.CONFLICT_IGNORE)
                    if (newId == -1L) {
                        cv.clear()
                        cv.put(Schema.TransferPeople.LAST_EDIT, System.currentTimeMillis())
                        cv.put(Schema.TransferPeople.DELETED, false)
                        val pWhere = Schema.TransferPeople.TRANSFER + " = ? AND " + Schema.TransferPeople.PERSON + " = ?"
                        val pWhereArgs = arrayOf(transferId.toString(), personId.toString())
                        writableDatabase.update(Schema.TransferPeople.TABLE, cv, pWhere, pWhereArgs)
                    }
                }
            }
            cv = ContentValues()
            cv.put(Schema.TransferAttachment.DELETED, true)
            where = Schema.TransferAttachment.TRANSFER + " = ?"
            whereArgs = arrayOf(transferId.toString())
            writableDatabase.update(Schema.TransferAttachment.TABLE, cv, where, whereArgs)
            val attachmentIds = parseIds(contentValues.getAsString(Contract.Transfer.ATTACHMENT_IDS))
            if (attachmentIds != null) {
                for (attachmentId in attachmentIds) {
                    cv = ContentValues()
                    cv.put(Schema.TransferAttachment.TRANSFER, transferId)
                    cv.put(Schema.TransferAttachment.ATTACHMENT, attachmentId)
                    cv.put(Schema.TransferAttachment.UUID, UUID.randomUUID().toString())
                    cv.put(Schema.TransferAttachment.LAST_EDIT, System.currentTimeMillis())
                    cv.put(Schema.TransferAttachment.DELETED, false)
                    val newId = writableDatabase.insertWithOnConflict(Schema.TransferAttachment.TABLE, null, cv, SQLiteDatabase.CONFLICT_IGNORE)
                    if (newId == -1L) {
                        cv.clear()
                        cv.put(Schema.TransferAttachment.LAST_EDIT, System.currentTimeMillis())
                        cv.put(Schema.TransferAttachment.DELETED, false)
                        val aWhere = Schema.TransferAttachment.TRANSFER + " = ? AND " + Schema.TransferAttachment.ATTACHMENT + " = ?"
                        val aWhereArgs = arrayOf(transferId.toString(), attachmentId.toString())
                        writableDatabase.update(Schema.TransferAttachment.TABLE, cv, aWhere, aWhereArgs)
                    }
                }
            }
            val delPeopleWhere = Schema.TransferPeople.TRANSFER + " = ? AND " + Schema.TransferPeople.DELETED + " = 1"
            val delWhereArgs = arrayOf(transferId.toString())
            writableDatabase.delete(Schema.TransferPeople.TABLE, delPeopleWhere, delWhereArgs)
            val delAttWhere = Schema.TransferAttachment.TRANSFER + " = ? AND " + Schema.TransferAttachment.DELETED + " = 1"
            writableDatabase.delete(Schema.TransferAttachment.TABLE, delAttWhere, delWhereArgs)
        }
        return rows
    }

    open fun deleteTransfer(transferId: Long): Int {
        deleteAttachmentsOf(
            Schema.TransferAttachment.TABLE, Schema.TransferAttachment.ATTACHMENT,
            Schema.TransferAttachment.TRANSFER + " = ? AND " + Schema.TransferAttachment.DELETED + " = 0",
            arrayOf(transferId.toString())
        )
        for (transactionId in getTransferTransactionIds(transferId)) {
            if (transactionId != null) {
                deleteTransactionItem(transactionId)
            }
        }
        val where = Schema.TransferPeople.TRANSFER + " = ?"
        val whereArgs = arrayOf(transferId.toString())
        writableDatabase.delete(Schema.TransferPeople.TABLE, where, whereArgs)
        val attWhere = Schema.TransferAttachment.TRANSFER + " = ?"
        writableDatabase.delete(Schema.TransferAttachment.TABLE, attWhere, whereArgs)
        val transferWhere = Schema.Transfer.ID + " = ?"
        return writableDatabase.delete(Schema.Transfer.TABLE, transferWhere, whereArgs)
    }

    open fun getCategory(id: Long, projection: Array<String>?): Cursor? {
        val selection = Schema.Category.ID + " = ?"
        val selectionArgs = arrayOf(id.toString())
        return getCategories(projection, selection, selectionArgs, null)
    }

    open fun getCategories(projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        val subQuery = "SELECT " +
                "c." + Schema.Category.ID + " AS " + Contract.Category.ID + ", " +
                "c." + Schema.Category.NAME + " AS " + Contract.Category.NAME + ", " +
                "c." + Schema.Category.ICON + " AS " + Contract.Category.ICON + ", " +
                "c." + Schema.Category.TYPE + " AS " + Contract.Category.TYPE + ", " +
                "c." + Schema.Category.PARENT + " AS " + Contract.Category.PARENT + ", " +
                "p." + Schema.Category.NAME + " AS " + Contract.Category.PARENT_NAME + ", " +
                "p." + Schema.Category.ICON + " AS " + Contract.Category.PARENT_ICON + ", " +
                "p." + Schema.Category.TYPE + " AS " + Contract.Category.PARENT_TYPE + ", " +
                "p." + Schema.Category.SHOW_REPORT + " AS " + Contract.Category.PARENT_SHOW_REPORT + ", " +
                "p." + Schema.Category.TAG + " AS " + Contract.Category.PARENT_TAG + ", " +
                "c." + Schema.Category.TAG + " AS " + Contract.Category.TAG + ", " +
                "c." + Schema.Category.SHOW_REPORT + " AS " + Contract.Category.SHOW_REPORT + ", " +
                "c." + Schema.Category.INDEX + " AS " + Contract.Category.INDEX + ", " +
                "IFNULL(p." + Schema.Category.ID + ", c." + Schema.Category.ID + ") AS " + Contract.Category.GROUP_ID + ", " +
                "IFNULL(p." + Schema.Category.NAME + ", c." + Schema.Category.NAME + ") AS " + Contract.Category.GROUP_NAME + ", " +
                "IFNULL(p." + Schema.Category.INDEX + ", c." + Schema.Category.INDEX + ") AS " + Contract.Category.GROUP_INDEX + " " +
                "FROM " + Schema.Category.TABLE + " AS c LEFT JOIN " + Schema.Category.TABLE +
                " AS p ON c." + Schema.Category.PARENT + " = p." + Schema.Category.ID + " AND p." +
                Schema.Category.DELETED + " = 0 WHERE c." + Schema.Category.DELETED + " = 0"
        return queryFrom(subQuery, projection, selection, selectionArgs, sortOrder)
    }

    open fun getCategoryTransactions(categoryId: Long, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        var _selection = "(" + Contract.Transaction.CATEGORY_ID + " = ? OR " +
                Contract.Transaction.CATEGORY_PARENT_ID + " = ?)"
        if (!TextUtils.isEmpty(selection)) {
            _selection += " AND " + selection
        }
        val size = selectionArgs?.size ?: 0
        val _selectionArgs = arrayOfNulls<String>(size + 2)
        _selectionArgs[0] = categoryId.toString()
        _selectionArgs[1] = categoryId.toString()
        if (selectionArgs != null) {
            System.arraycopy(selectionArgs, 0, _selectionArgs, 2, size)
        }
        val nonNullArgs = Array(_selectionArgs.size) { i -> _selectionArgs[i] ?: "" }
        return getTransactions(projection, _selection, nonNullArgs, sortOrder)
    }

    open fun insertCategory(contentValues: ContentValues): Long {
        if (contentValues.containsKey(Contract.Category.PARENT)) {
            val projection = arrayOf(Schema.Category.ID, Schema.Category.TYPE, Schema.Category.PARENT)
            val where = Schema.Category.ID + " = ?"
            val whereArgs = arrayOf(contentValues.getAsLong(Contract.Category.PARENT).toString())
            val cursor = readableDatabase.query(Schema.Category.TABLE, projection, where, whereArgs, null, null, null)
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        if (cursor.getLong(cursor.getColumnIndex(Schema.Category.PARENT)) > 0L) {
                            throw SQLiteDataException(
                                Contract.ErrorCode.CATEGORY_HIERARCHY_NOT_SUPPORTED,
                                "The category cannot be inserted because nested relations are not supported"
                            )
                        }
                        if (cursor.getInt(cursor.getColumnIndex(Schema.Category.TYPE)) != contentValues.getAsInteger(Contract.Category.TYPE)) {
                            throw SQLiteDataException(
                                Contract.ErrorCode.CATEGORY_NOT_CONSISTENT,
                                "The category cannot be inserted because is not consistent with the parent category"
                            )
                        }
                    }
                } finally {
                    cursor.close()
                }
            }
        }
        val cv = ContentValues()
        cv.put(Schema.Category.NAME, contentValues.getAsString(Contract.Category.NAME))
        cv.put(Schema.Category.ICON, contentValues.getAsString(Contract.Category.ICON))
        cv.put(Schema.Category.TYPE, contentValues.getAsInteger(Contract.Category.TYPE))
        cv.put(Schema.Category.PARENT, contentValues.getAsLong(Contract.Category.PARENT))
        cv.put(Schema.Category.TAG, contentValues.getAsString(Contract.Category.TAG))
        cv.put(Schema.Category.SHOW_REPORT, contentValues.getAsBoolean(Contract.Category.SHOW_REPORT))
        val index = contentValues.getAsInteger(Contract.Category.INDEX)
        cv.put(Schema.Category.INDEX, index ?: 0)
        cv.put(Schema.Category.UUID, UUID.randomUUID().toString())
        cv.put(Schema.Category.LAST_EDIT, System.currentTimeMillis())
        cv.put(Schema.Category.DELETED, false)
        return writableDatabase.insert(Schema.Category.TABLE, null, cv)
    }

    open fun updateCategory(categoryId: Long, contentValues: ContentValues): Int {
        var isSystemCategory = false
        var isChildCategory = false
        var projection = arrayOf(Schema.Category.TYPE, Schema.Category.PARENT)
        var where = Schema.Category.ID + " = ?"
        var whereArgs = arrayOf(categoryId.toString())
        var cursor = readableDatabase.query(Schema.Category.TABLE, projection, where, whereArgs, null, null, null)
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    if (cursor.getInt(cursor.getColumnIndex(Schema.Category.TYPE)) == Contract.CategoryType.SYSTEM.value) {
                        isSystemCategory = true
                    }
                    if (!cursor.isNull(cursor.getColumnIndex(Schema.Category.PARENT))) {
                        isChildCategory = true
                    }
                }
            } finally {
                cursor.close()
            }
        }
        val isValidParentSet = contentValues.containsKey(Contract.Category.PARENT) && contentValues.getAsLong(Contract.Category.PARENT) != null
        if (isValidParentSet) {
            projection = arrayOf(Schema.Category.ID)
            where = Schema.Category.PARENT + " = ?"
            whereArgs = arrayOf(categoryId.toString())
            cursor = readableDatabase.query(Schema.Category.TABLE, projection, where, whereArgs, null, null, null)
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        throw SQLiteDataException(
                            Contract.ErrorCode.CATEGORY_HIERARCHY_NOT_SUPPORTED,
                            String.format(Locale.ENGLISH, "The category (id: %d) cannot be updated because nested relations are not supported", categoryId)
                        )
                    }
                } finally {
                    cursor.close()
                }
            }
            projection = arrayOf(Schema.Category.ID, Schema.Category.TYPE, Schema.Category.PARENT)
            where = Schema.Category.ID + " = ?"
            whereArgs = arrayOf(contentValues.getAsLong(Contract.Category.PARENT).toString())
            cursor = readableDatabase.query(Schema.Category.TABLE, projection, where, whereArgs, null, null, null)
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        if (cursor.getLong(cursor.getColumnIndex(Schema.Category.PARENT)) > 0L) {
                            throw SQLiteDataException(
                                Contract.ErrorCode.CATEGORY_HIERARCHY_NOT_SUPPORTED,
                                String.format(Locale.ENGLISH, "The category (id: %d) cannot be updated because nested relations are not supported", categoryId)
                            )
                        }
                        if (cursor.getInt(cursor.getColumnIndex(Schema.Category.TYPE)) != contentValues.getAsInteger(Contract.Category.TYPE)) {
                            throw SQLiteDataException(
                                Contract.ErrorCode.CATEGORY_NOT_CONSISTENT,
                                String.format(Locale.ENGLISH, "The category (id: %d) cannot be updated because is not consistent with the parent category", categoryId)
                            )
                        }
                    }
                } finally {
                    cursor.close()
                }
            }
        } else {
            if (contentValues.containsKey(Contract.Category.TYPE)) {
                projection = arrayOf(Schema.Category.ID)
                where = Schema.Category.PARENT + " = ? AND " + Schema.Category.TYPE + " != ?"
                whereArgs = arrayOf(categoryId.toString(), contentValues.getAsInteger(Contract.Category.TYPE).toString())
                cursor = readableDatabase.query(Schema.Category.TABLE, projection, where, whereArgs, null, null, null)
                if (cursor != null) {
                    try {
                        if (cursor.moveToFirst()) {
                            throw SQLiteDataException(
                                Contract.ErrorCode.CATEGORY_NOT_CONSISTENT,
                                String.format(Locale.ENGLISH, "The category (id: %d) cannot be updated because is not consistent with the children categories", categoryId)
                            )
                        }
                    } finally {
                        cursor.close()
                    }
                }
            }
        }
        var cv = ContentValues()
        if (contentValues.containsKey(Contract.Category.NAME)) {
            cv.put(Schema.Category.NAME, contentValues.getAsString(Contract.Category.NAME))
        }
        if (contentValues.containsKey(Contract.Category.ICON)) {
            cv.put(Schema.Category.ICON, contentValues.getAsString(Contract.Category.ICON))
        }
        if (!isSystemCategory) {
            if (contentValues.containsKey(Contract.Category.TYPE)) {
                val type = contentValues.getAsInteger(Contract.Category.TYPE)
                if (type != Schema.CategoryType.SYSTEM) {
                    cv.put(Schema.Category.TYPE, type)
                }
            }
            if (contentValues.containsKey(Contract.Category.PARENT)) {
                cv.put(Schema.Category.PARENT, contentValues.getAsLong(Contract.Category.PARENT))
            }
            if (contentValues.containsKey(Contract.Category.TAG)) {
                cv.put(Schema.Category.TAG, contentValues.getAsString(Contract.Category.TAG))
            }
        }
        if (contentValues.containsKey(Contract.Category.SHOW_REPORT)) {
            cv.put(Schema.Category.SHOW_REPORT, contentValues.getAsBoolean(Contract.Category.SHOW_REPORT))
        }
        if (contentValues.containsKey(Contract.Category.INDEX)) {
            var index = contentValues.getAsInteger(Contract.Category.INDEX) ?: 0
            if (isChildCategory || isValidParentSet) {
                index = 0
            }
            cv.put(Schema.Category.INDEX, index)
        } else {
            if (isChildCategory || isValidParentSet) {
                cv.put(Schema.Category.INDEX, 0)
            }
        }
        cv.put(Schema.Category.LAST_EDIT, System.currentTimeMillis())
        where = Schema.Category.ID + " = ?"
        whereArgs = arrayOf(categoryId.toString())
        val rows = writableDatabase.update(Schema.Category.TABLE, cv, where, whereArgs)
        if (!isSystemCategory && contentValues.containsKey(Contract.Category.TYPE)) {
            val type = contentValues.getAsInteger(Contract.Category.TYPE)
            if (type != Schema.CategoryType.SYSTEM) {
                where = Schema.Transaction.CATEGORY + " = ?"
                whereArgs = arrayOf(categoryId.toString())
                cv = ContentValues()
                cv.put(Schema.Transaction.DIRECTION, if (type == Schema.CategoryType.INCOME) Schema.Direction.INCOME else Schema.Direction.EXPENSE)
                cv.put(Schema.Transaction.LAST_EDIT, System.currentTimeMillis())
                writableDatabase.update(Schema.Transaction.TABLE, cv, where, whereArgs)
            }
        }
        return rows
    }

    open fun deleteCategory(categoryId: Long): Int {
        var projection = arrayOf(Schema.Category.ID)
        var where = Schema.Category.PARENT + " = ? AND " + Schema.Category.DELETED + " = 0"
        var whereArgs = arrayOf(categoryId.toString())
        var cursor = readableDatabase.query(Schema.Category.TABLE, projection, where, whereArgs, null, null, null)
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    throw SQLiteDataException(
                        Contract.ErrorCode.CATEGORY_HAS_CHILDREN,
                        String.format(Locale.ENGLISH, "The category (id: %d) cannot be deleted because it is parent of %d categories", categoryId, cursor.count)
                    )
                }
            } finally {
                cursor.close()
            }
        }
        projection = arrayOf(Schema.Category.TYPE)
        where = Schema.Category.ID + " = ?"
        whereArgs = arrayOf(categoryId.toString())
        cursor = readableDatabase.query(Schema.Category.TABLE, projection, where, whereArgs, null, null, null)
        if (cursor != null) {
            try {
                if (cursor.moveToFirst() && cursor.getInt(cursor.getColumnIndex(Schema.Category.TYPE)) == Contract.CategoryType.SYSTEM.value) {
                    throw SQLiteDataException(
                        Contract.ErrorCode.SYSTEM_CATEGORY_NOT_MODIFIABLE,
                        String.format(Locale.ENGLISH, "The category (id: %d) cannot be deleted because it is a system category", categoryId)
                    )
                }
            } finally {
                cursor.close()
            }
        }
        projection = arrayOf(Schema.Transaction.ID)
        where = Schema.Transaction.CATEGORY + " = ? AND " + Schema.Transaction.DELETED + " = 0"
        whereArgs = arrayOf(categoryId.toString())
        cursor = readableDatabase.query(Schema.Transaction.TABLE, projection, where, whereArgs, null, null, null)
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    throw SQLiteDataException(
                        Contract.ErrorCode.CATEGORY_IN_USE,
                        String.format(Locale.ENGLISH, "The category (id: %d) cannot be deleted because it is in use in %d transactions", categoryId, cursor.count)
                    )
                }
            } finally {
                cursor.close()
            }
        }
        projection = arrayOf(Schema.TransactionModel.ID)
        where = Schema.TransactionModel.CATEGORY + " = ? AND " + Schema.TransactionModel.DELETED + " = 0"
        whereArgs = arrayOf(categoryId.toString())
        cursor = readableDatabase.query(Schema.TransactionModel.TABLE, projection, where, whereArgs, null, null, null)
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    throw SQLiteDataException(
                        Contract.ErrorCode.CATEGORY_IN_USE,
                        String.format(Locale.ENGLISH, "The category (id: %d) cannot be deleted because it is in use in %d transaction-models", categoryId, cursor.count)
                    )
                }
            } finally {
                cursor.close()
            }
        }
        projection = arrayOf(Schema.RecurrentTransaction.ID)
        where = Schema.RecurrentTransaction.CATEGORY + " = ? AND " + Schema.RecurrentTransaction.DELETED + " = 0"
        whereArgs = arrayOf(categoryId.toString())
        cursor = readableDatabase.query(Schema.RecurrentTransaction.TABLE, projection, where, whereArgs, null, null, null)
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    throw SQLiteDataException(
                        Contract.ErrorCode.CATEGORY_IN_USE,
                        String.format(Locale.ENGLISH, "The category (id: %d) cannot be deleted because it is in use in %d recurrent-transactions", categoryId, cursor.count)
                    )
                }
            } finally {
                cursor.close()
            }
        }
        projection = arrayOf(Schema.Budget.ID)
        where = Schema.Budget.CATEGORY + " = ? AND " + Schema.Budget.DELETED + " = 0"
        whereArgs = arrayOf(categoryId.toString())
        cursor = readableDatabase.query(Schema.Budget.TABLE, projection, where, whereArgs, null, null, null)
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    throw SQLiteDataException(
                        Contract.ErrorCode.CATEGORY_IN_USE,
                        String.format(Locale.ENGLISH, "The category (id: %d) cannot be deleted because it is in use in %d budgets", categoryId, cursor.count)
                    )
                }
            } finally {
                cursor.close()
            }
        }
        projection = arrayOf("bc." + Schema.BudgetCategory.BUDGET)
        where = "bc." + Schema.BudgetCategory.CATEGORY + " = ? AND bc." +
                Schema.BudgetCategory.DELETED + " = 0 AND b." + Schema.Budget.DELETED + " = 0"
        whereArgs = arrayOf(categoryId.toString())
        cursor = readableDatabase.query(
            Schema.BudgetCategory.TABLE + " AS bc JOIN " +
                    Schema.Budget.TABLE + " AS b ON bc." + Schema.BudgetCategory.BUDGET + " = b." +
                    Schema.Budget.ID, projection, where, whereArgs, null, null, null
        )
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    throw SQLiteDataException(
                        Contract.ErrorCode.CATEGORY_IN_USE,
                        String.format(Locale.ENGLISH, "The category (id: %d) cannot be deleted because it is in use in %d budgets", categoryId, cursor.count)
                    )
                }
            } finally {
                cursor.close()
            }
        }
        where = Schema.Category.ID + " = ?"
        whereArgs = arrayOf(categoryId.toString())
        return writableDatabase.delete(Schema.Category.TABLE, where, whereArgs)
    }

    open fun getCategoryRule(ruleId: Long, projection: Array<String>?): Cursor? {
        val selection = Schema.CategoryRule.ID + " = ?"
        val selectionArgs = arrayOf(ruleId.toString())
        return getCategoryRules(projection, selection, selectionArgs, null)
    }

    open fun getCategoryRules(projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        val subQuery = "SELECT " +
                "cr." + Schema.CategoryRule.ID + " AS " + Contract.CategoryRule.ID + ", " +
                "cr." + Schema.CategoryRule.PATTERN + " AS " + Contract.CategoryRule.PATTERN + ", " +
                "cr." + Schema.CategoryRule.CATEGORY + " AS " + Contract.CategoryRule.CATEGORY_ID + ", " +
                "c." + Schema.Category.NAME + " AS " + Contract.CategoryRule.CATEGORY_NAME + ", " +
                "cr." + Schema.CategoryRule.INDEX + " AS " + Contract.CategoryRule.INDEX +
                " FROM " + Schema.CategoryRule.TABLE + " AS cr JOIN " + Schema.Category.TABLE +
                " AS c ON cr." + Schema.CategoryRule.CATEGORY + " = c." + Schema.Category.ID +
                " AND c." + Schema.Category.DELETED + " = 0 WHERE cr." +
                Schema.CategoryRule.DELETED + " = 0"
        return queryFrom(subQuery, projection, selection, selectionArgs, sortOrder)
    }

    open fun getCategoryRuleMatch(description: String): Cursor? {
        val query = "SELECT " + Schema.CategoryRule.CATEGORY + " AS " +
                Contract.CategoryRule.CATEGORY_ID + " FROM " + Schema.CategoryRule.TABLE +
                " WHERE " + Schema.CategoryRule.DELETED + " = 0 AND instr(lower(?), lower(" +
                Schema.CategoryRule.PATTERN + ")) > 0 ORDER BY " + Schema.CategoryRule.INDEX +
                ", " + Schema.CategoryRule.ID + " LIMIT 1"
        return readableDatabase.rawQuery(query, arrayOf(description))
    }

    private fun nextCategoryRuleIndex(): Int {
        val cursor = readableDatabase.rawQuery(
            "SELECT IFNULL(MAX(" + Schema.CategoryRule.INDEX + "), -1) + 1 FROM " + Schema.CategoryRule.TABLE, null
        )
        try {
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        } finally {
            cursor.close()
        }
    }

    open fun insertCategoryRule(contentValues: ContentValues): Long {
        val cv = ContentValues()
        cv.put(Schema.CategoryRule.PATTERN, contentValues.getAsString(Contract.CategoryRule.PATTERN))
        cv.put(Schema.CategoryRule.CATEGORY, contentValues.getAsLong(Contract.CategoryRule.CATEGORY_ID))
        val index = contentValues.getAsInteger(Contract.CategoryRule.INDEX)
        cv.put(Schema.CategoryRule.INDEX, index ?: nextCategoryRuleIndex())
        cv.put(Schema.CategoryRule.UUID, UUID.randomUUID().toString())
        cv.put(Schema.CategoryRule.LAST_EDIT, System.currentTimeMillis())
        cv.put(Schema.CategoryRule.DELETED, false)
        return writableDatabase.insert(Schema.CategoryRule.TABLE, null, cv)
    }

    open fun updateCategoryRule(ruleId: Long, contentValues: ContentValues): Int {
        val cv = ContentValues()
        if (contentValues.containsKey(Contract.CategoryRule.PATTERN)) {
            cv.put(Schema.CategoryRule.PATTERN, contentValues.getAsString(Contract.CategoryRule.PATTERN))
        }
        if (contentValues.containsKey(Contract.CategoryRule.CATEGORY_ID)) {
            cv.put(Schema.CategoryRule.CATEGORY, contentValues.getAsLong(Contract.CategoryRule.CATEGORY_ID))
        }
        if (contentValues.containsKey(Contract.CategoryRule.INDEX)) {
            cv.put(Schema.CategoryRule.INDEX, contentValues.getAsInteger(Contract.CategoryRule.INDEX))
        }
        cv.put(Schema.CategoryRule.LAST_EDIT, System.currentTimeMillis())
        val where = Schema.CategoryRule.ID + " = ?"
        val whereArgs = arrayOf(ruleId.toString())
        return writableDatabase.update(Schema.CategoryRule.TABLE, cv, where, whereArgs)
    }

    open fun deleteCategoryRule(ruleId: Long): Int {
        val where = Schema.CategoryRule.ID + " = ?"
        val whereArgs = arrayOf(ruleId.toString())
        return writableDatabase.delete(Schema.CategoryRule.TABLE, where, whereArgs)
    }

    open fun getDebt(id: Long, projection: Array<String>?): Cursor? {
        val selection = Schema.Debt.ID + " = ?"
        val selectionArgs = arrayOf(id.toString())
        return getDebts(projection, selection, selectionArgs, null)
    }

    open fun getDebts(projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        val subQuery = "SELECT " +
                Schema.Debt.ID + " AS " + Contract.Debt.ID + ", " +
                Schema.Debt.TYPE + " AS " + Contract.Debt.TYPE + ", " +
                Schema.Debt.ICON + " AS " + Contract.Debt.ICON + ", " +
                Schema.Debt.DESCRIPTION + " AS " + Contract.Debt.DESCRIPTION + ", " +
                Schema.Debt.DATE + " AS " + Contract.Debt.DATE + ", " +
                Schema.Debt.EXPIRATION_DATE + " AS " + Contract.Debt.EXPIRATION_DATE + ", " +
                Schema.Debt.WALLET + " AS " + Contract.Debt.WALLET_ID + ", " +
                Schema.Wallet.NAME + " AS " + Contract.Debt.WALLET_NAME + ", " +
                Schema.Wallet.ICON + " AS " + Contract.Debt.WALLET_ICON + ", " +
                Schema.Wallet.CURRENCY + " AS " + Contract.Debt.WALLET_CURRENCY + ", " +
                Schema.Wallet.COUNT_IN_TOTAL + " AS " + Contract.Debt.WALLET_COUNT_IN_TOTAL + ", " +
                Schema.Wallet.ARCHIVED + " AS " + Contract.Debt.WALLET_ARCHIVED + ", " +
                Schema.Wallet.TAG + " AS " + Contract.Debt.WALLET_TAG + ", " +
                Schema.Debt.NOTE + " AS " + Contract.Debt.NOTE + ", " +
                Schema.Debt.PLACE + " AS " + Contract.Debt.PLACE_ID + ", " +
                Schema.Place.NAME + " AS " + Contract.Debt.PLACE_NAME + ", " +
                Schema.Place.ICON + " AS " + Contract.Debt.PLACE_ICON + ", " +
                Schema.Place.ADDRESS + " AS " + Contract.Debt.PLACE_ADDRESS + ", " +
                Schema.Place.LATITUDE + " AS " + Contract.Debt.PLACE_LATITUDE + ", " +
                Schema.Place.LONGITUDE + " AS " + Contract.Debt.PLACE_LONGITUDE + ", " +
                Schema.Place.TAG + " AS " + Contract.Debt.PLACE_TAG + ", " +
                Schema.Debt.MONEY + " AS " + Contract.Debt.MONEY + ", " +
                Schema.Debt.ARCHIVED + " AS " + Contract.Debt.ARCHIVED + ", " +
                Schema.Debt.TAG + " AS " + Contract.Debt.TAG + ", " +
                "_debt_progress AS " + Contract.Debt.PROGRESS + ",\n" +
                "GROUP_CONCAT('<' || _person_id || '>') AS " + Contract.Debt.PEOPLE_IDS + "\n" +
                "FROM (\n" +
                "\tSELECT d.*, w.*, p.*,\n" +
                "\tt._debt_progress,\n" +
                "\tpe.person_id AS _person_id\n" +
                "\tFROM debts AS d\n" +
                "\t\tLEFT JOIN debt_people AS dp ON d.debt_id = dp._debt AND dp.deleted = 0\n" +
                "\t\tLEFT JOIN people AS pe ON dp._person = pe.person_id AND pe.deleted = 0\n" +
                "\t\tJOIN wallets AS w ON d.debt_wallet = w.wallet_id AND w.deleted = 0\n" +
                "\t\tLEFT JOIN places AS p ON debt_place = p.place_id AND p.deleted = 0\n" +
                "\t\tLEFT JOIN (\n" +
                "\t\t\tSELECT transaction_debt AS _debt_id,\n" +
                "\t\t\tSUM(((transaction_direction * 2) - 1) * transaction_money) AS _debt_progress\n" +
                "\t\t\tFROM transactions AS t\n" +
                "\t\t\tLEFT JOIN categories AS c ON t.transaction_category = c.category_id AND c.deleted = 0\n" +
                "\t\t\tWHERE t.deleted = 0 AND t.transaction_confirmed = 1 AND (\n" +
                "\t\t\tc.category_tag = 'system::paid_debt' OR \n" +
                "\t\t\tc.category_tag = 'system::paid_credit'\n" +
                "\t\t\t) AND DATETIME(t.transaction_date) <= DATETIME('now', 'localtime')\n" +
                "\t\t\tGROUP BY _debt_id\n" +
                "\t\t) AS t ON t._debt_id = d.debt_id\n" +
                "\t\tWHERE d.deleted = 0\n" +
                ")\n" +
                "GROUP BY debt_id"
        return queryFrom(subQuery, projection, selection, selectionArgs, sortOrder)
    }

    open fun getDebtPeople(id: Long, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        val subQuery = "SELECT " +
                Schema.Person.ID + " AS " + Contract.Person.ID + ", " +
                Schema.Person.NAME + " AS " + Contract.Person.NAME + ", " +
                Schema.Person.ICON + " AS " + Contract.Person.ICON + ", " +
                Schema.Person.NOTE + " AS " + Contract.Person.NOTE + ", " +
                Schema.Person.TAG + " AS " + Contract.Person.TAG + " " +
                "FROM " + Schema.DebtPeople.TABLE + " AS dp JOIN " + Schema.Person.TABLE + " AS p " +
                "ON dp." + Schema.DebtPeople.PERSON + " = p." + Schema.Person.ID + " AND dp." +
                Schema.DebtPeople.DELETED + " = 0 AND p." + Schema.Person.DELETED + " = 0 AND " +
                Schema.DebtPeople.DEBT + " = " + id.toString()
        return queryFrom(subQuery, projection, selection, selectionArgs, sortOrder)
    }

    open fun getDebtTransactions(debtId: Long, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        var _selection = Schema.Transaction.DEBT + " = ?"
        if (!TextUtils.isEmpty(selection)) {
            _selection += " AND " + selection
        }
        val size = selectionArgs?.size ?: 0
        val _selectionArgs = arrayOfNulls<String>(size + 1)
        _selectionArgs[0] = debtId.toString()
        if (selectionArgs != null) {
            System.arraycopy(selectionArgs, 0, _selectionArgs, 1, size)
        }
        val nonNullArgs = Array(_selectionArgs.size) { i -> _selectionArgs[i] ?: "" }
        return getTransactions(projection, _selection, nonNullArgs, sortOrder)
    }

    private fun masterTransactionDebtId(transactionId: Long): Long? {
        val projection = arrayOf(
            Schema.Transaction.TYPE,
            Schema.Transaction.DEBT,
            Schema.Transaction.CATEGORY
        )
        val selection = Schema.Transaction.ID + " = ?"
        val selectionArgs = arrayOf(transactionId.toString())
        val cursor = readableDatabase.query(
            Schema.Transaction.TABLE, projection, selection,
            selectionArgs, null, null, null
        )
        var debtId: Long? = null
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()
                    && cursor.getInt(cursor.getColumnIndex(Schema.Transaction.TYPE)) == Contract.TransactionType.DEBT
                    && !cursor.isNull(cursor.getColumnIndex(Schema.Transaction.DEBT))
                ) {
                    val categoryId = cursor.getLong(cursor.getColumnIndex(Schema.Transaction.CATEGORY))
                    val debtCategoryId = getSystemCategoryId(Schema.CategoryTag.DEBT)
                    val creditCategoryId = getSystemCategoryId(Schema.CategoryTag.CREDIT)
                    if ((debtCategoryId != null && debtCategoryId == categoryId)
                        || (creditCategoryId != null && creditCategoryId == categoryId)
                    ) {
                        debtId = cursor.getLong(cursor.getColumnIndex(Schema.Transaction.DEBT))
                    }
                }
            } finally {
                cursor.close()
            }
        }
        return debtId
    }

    private fun checkDebtOfMasterTransactionWallet(transactionId: Long, contentValues: ContentValues) {
        if (!contentValues.containsKey(Contract.Transaction.WALLET_ID)) {
            return
        }
        val debtId = masterTransactionDebtId(transactionId) ?: return
        val walletId = contentValues.getAsLong(Contract.Transaction.WALLET_ID) ?: return
        checkDebtWalletCurrency(debtId, walletId)
    }

    private fun syncDebtOfMasterTransaction(transactionId: Long, contentValues: ContentValues) {
        val debtId = masterTransactionDebtId(transactionId) ?: return
        val cv = ContentValues()
        if (contentValues.containsKey(Contract.Transaction.MONEY)) {
            cv.put(Schema.Debt.MONEY, contentValues.getAsLong(Contract.Transaction.MONEY))
        }
        val date = contentValues.getAsString(Contract.Transaction.DATE)
        if (date != null) {
            val dateTime = DateUtils.getDateFromSQLDateTimeString(date)
            if (dateTime != null) {
                cv.put(Schema.Debt.DATE, DateUtils.getSQLDateString(dateTime))
            }
        }
        val description = contentValues.getAsString(Contract.Transaction.DESCRIPTION)
        if (description != null && description.trim().isNotEmpty()) {
            cv.put(Schema.Debt.DESCRIPTION, description)
            val icon = renamedDebtIcon(debtId, description)
            if (icon != null) {
                cv.put(Schema.Debt.ICON, icon)
            }
        }
        if (contentValues.containsKey(Contract.Transaction.WALLET_ID)) {
            cv.put(Schema.Debt.WALLET, contentValues.getAsLong(Contract.Transaction.WALLET_ID))
        }
        if (contentValues.containsKey(Contract.Transaction.PLACE_ID)) {
            cv.put(Schema.Debt.PLACE, contentValues.getAsLong(Contract.Transaction.PLACE_ID))
        }
        if (contentValues.containsKey(Contract.Transaction.NOTE)) {
            cv.put(Schema.Debt.NOTE, contentValues.getAsString(Contract.Transaction.NOTE))
        }
        if (cv.size() > 0) {
            cv.put(Schema.Debt.LAST_EDIT, System.currentTimeMillis())
            val where = Schema.Debt.ID + " = ?"
            val whereArgs = arrayOf(debtId.toString())
            writableDatabase.update(Schema.Debt.TABLE, cv, where, whereArgs)
        }
        if (contentValues.containsKey(Contract.Transaction.PEOPLE_IDS)) {
            updateDebtPeople(debtId, contentValues.getAsString(Contract.Transaction.PEOPLE_IDS))
        }
    }

    private fun renamedDebtIcon(debtId: Long, description: String): String? {
        var stored: String? = null
        var storedDescription: String? = null
        val cursor = readableDatabase.query(
            Schema.Debt.TABLE,
            arrayOf(Schema.Debt.ICON, Schema.Debt.DESCRIPTION), Schema.Debt.ID + " = ?",
            arrayOf(debtId.toString()), null, null, null
        )
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    stored = cursor.getString(cursor.getColumnIndex(Schema.Debt.ICON))
                    storedDescription = cursor.getString(cursor.getColumnIndex(Schema.Debt.DESCRIPTION))
                }
            } finally {
                cursor.close()
            }
        }
        if (TextUtils.equals(description, storedDescription)) {
            return null
        }
        return try {
            val icon = IconLoader.parse(stored)
            if (icon !is ColorIcon) {
                null
            } else {
                ColorIcon(icon, IconLoader.getColorIconString(description)).toString()
            }
        } catch (e: Exception) {
            Log.e(TAG, "unreadable icon on a renamed debt, keeping the old one", e)
            null
        }
    }

    private fun updateDebtPeople(debtId: Long, peopleIdList: String?) {
        var cv = ContentValues()
        cv.put(Schema.DebtPeople.DELETED, true)
        var where = Schema.DebtPeople.DEBT + " = ?"
        var whereArgs = arrayOf(debtId.toString())
        writableDatabase.update(Schema.DebtPeople.TABLE, cv, where, whereArgs)
        val peopleIds = parseIds(peopleIdList)
        if (peopleIds != null) {
            for (personId in peopleIds) {
                cv.clear()
                cv.put(Schema.DebtPeople.DEBT, debtId)
                cv.put(Schema.DebtPeople.PERSON, personId)
                cv.put(Schema.DebtPeople.UUID, UUID.randomUUID().toString())
                cv.put(Schema.DebtPeople.LAST_EDIT, System.currentTimeMillis())
                cv.put(Schema.DebtPeople.DELETED, false)
                val newId = writableDatabase.insertWithOnConflict(Schema.DebtPeople.TABLE, null, cv, SQLiteDatabase.CONFLICT_IGNORE)
                if (newId == -1L) {
                    cv.clear()
                    cv.put(Schema.DebtPeople.LAST_EDIT, System.currentTimeMillis())
                    cv.put(Schema.DebtPeople.DELETED, false)
                    where = Schema.DebtPeople.DEBT + " = ? AND " + Schema.DebtPeople.PERSON + " = ?"
                    whereArgs = arrayOf(debtId.toString(), personId.toString())
                    writableDatabase.update(Schema.DebtPeople.TABLE, cv, where, whereArgs)
                }
            }
        }
        where = Schema.DebtPeople.DEBT + " = ? AND " + Schema.DebtPeople.DELETED + " = 1"
        whereArgs = arrayOf(debtId.toString())
        writableDatabase.delete(Schema.DebtPeople.TABLE, where, whereArgs)
    }

    open fun insertDebt(contentValues: ContentValues): Long {
        var cv = ContentValues()
        cv.put(Schema.Debt.TYPE, contentValues.getAsInteger(Contract.Debt.TYPE))
        cv.put(Schema.Debt.ICON, contentValues.getAsString(Contract.Debt.ICON))
        cv.put(Schema.Debt.DESCRIPTION, contentValues.getAsString(Contract.Debt.DESCRIPTION))
        cv.put(Schema.Debt.DATE, contentValues.getAsString(Contract.Debt.DATE))
        cv.put(Schema.Debt.EXPIRATION_DATE, contentValues.getAsString(Contract.Debt.EXPIRATION_DATE))
        cv.put(Schema.Debt.WALLET, contentValues.getAsLong(Contract.Debt.WALLET_ID))
        cv.put(Schema.Debt.NOTE, contentValues.getAsString(Contract.Debt.NOTE))
        cv.put(Schema.Debt.PLACE, contentValues.getAsLong(Contract.Debt.PLACE_ID))
        cv.put(Schema.Debt.MONEY, contentValues.getAsLong(Contract.Debt.MONEY))
        cv.put(Schema.Debt.ARCHIVED, contentValues.getAsBoolean(Contract.Debt.ARCHIVED))
        cv.put(Schema.Debt.TAG, contentValues.getAsString(Contract.Debt.TAG))
        cv.put(Schema.Debt.UUID, UUID.randomUUID().toString())
        cv.put(Schema.Debt.LAST_EDIT, System.currentTimeMillis())
        cv.put(Schema.Debt.DELETED, false)
        val debtId = writableDatabase.insert(Schema.Debt.TABLE, null, cv)
        if (debtId > 0L) {
            val peopleIds = parseIds(contentValues.getAsString(Contract.Debt.PEOPLE_IDS))
            if (peopleIds != null) {
                for (personId in peopleIds) {
                    val cvp = ContentValues()
                    cvp.put(Schema.DebtPeople.DEBT, debtId)
                    cvp.put(Schema.DebtPeople.PERSON, personId)
                    cvp.put(Schema.DebtPeople.UUID, UUID.randomUUID().toString())
                    cvp.put(Schema.DebtPeople.LAST_EDIT, System.currentTimeMillis())
                    cvp.put(Schema.DebtPeople.DELETED, false)
                    writableDatabase.insert(Schema.DebtPeople.TABLE, null, cvp)
                }
            }
            if (contentValues.containsKey(Contract.Debt.INSERT_MASTER_TRANSACTION)
                && java.lang.Boolean.TRUE.equals(contentValues.getAsBoolean(Contract.Debt.INSERT_MASTER_TRANSACTION))
            ) {
                cv = ContentValues()
                val debtTypeInt = contentValues.getAsInteger(Contract.Debt.TYPE) ?: 0
                val type = Contract.DebtType.fromValue(debtTypeInt)
                cv.put(Contract.Transaction.MONEY, contentValues.getAsLong(Contract.Debt.MONEY))
                cv.put(Contract.Transaction.DATE, masterTransactionDateFor(contentValues.getAsString(Contract.Debt.DATE), null))
                cv.put(Contract.Transaction.DESCRIPTION, contentValues.getAsString(Contract.Debt.DESCRIPTION))
                cv.put(Contract.Transaction.CATEGORY_ID, getSystemCategoryId(if (type == Contract.DebtType.DEBT) Schema.CategoryTag.DEBT else Schema.CategoryTag.CREDIT))
                cv.put(Contract.Transaction.DIRECTION, if (type == Contract.DebtType.CREDIT) Contract.Direction.EXPENSE else Contract.Direction.INCOME)
                cv.put(Contract.Transaction.TYPE, Contract.TransactionType.DEBT)
                cv.put(Contract.Transaction.WALLET_ID, if (contentValues.containsKey(Contract.Debt.WALLET_ID)) contentValues.getAsLong(Contract.Debt.WALLET_ID) else null)
                cv.put(Contract.Transaction.PLACE_ID, if (contentValues.containsKey(Contract.Debt.PLACE_ID)) contentValues.getAsLong(Contract.Debt.PLACE_ID) else null)
                cv.put(Contract.Transaction.NOTE, contentValues.getAsString(Contract.Debt.NOTE))
                cv.put(Contract.Transaction.SAVING_ID, null as Long?)
                cv.put(Contract.Transaction.DEBT_ID, debtId)
                cv.put(Contract.Transaction.EVENT_ID, null as Long?)
                cv.put(Contract.Transaction.RECURRENCE_ID, null as Long?)
                cv.put(Contract.Transaction.CONFIRMED, true)
                cv.put(Contract.Transaction.COUNT_IN_TOTAL, true)
                cv.put(Contract.Transaction.PEOPLE_IDS, contentValues.getAsString(Contract.Debt.PEOPLE_IDS))
                cv.put(Contract.Transaction.ATTACHMENT_IDS, null as String?)
                insertTransaction(cv)
            }
        }
        return debtId
    }

    open fun updateDebt(debtId: Long, contentValues: ContentValues): Int {
        if (contentValues.containsKey(Contract.Debt.WALLET_ID)) {
            val walletId = contentValues.getAsLong(Contract.Debt.WALLET_ID) ?: 0L
            checkDebtWalletCurrency(debtId, walletId)
        }
        var cv = ContentValues()
        if (contentValues.containsKey(Contract.Debt.TYPE)) {
            cv.put(Schema.Debt.TYPE, contentValues.getAsInteger(Contract.Debt.TYPE))
        }
        if (contentValues.containsKey(Contract.Debt.ICON)) {
            cv.put(Schema.Debt.ICON, contentValues.getAsString(Contract.Debt.ICON))
        }
        if (contentValues.containsKey(Contract.Debt.DESCRIPTION)) {
            cv.put(Schema.Debt.DESCRIPTION, contentValues.getAsString(Contract.Debt.DESCRIPTION))
        }
        if (contentValues.containsKey(Contract.Debt.DATE)) {
            cv.put(Schema.Debt.DATE, contentValues.getAsString(Contract.Debt.DATE))
        }
        if (contentValues.containsKey(Contract.Debt.EXPIRATION_DATE)) {
            cv.put(Schema.Debt.EXPIRATION_DATE, contentValues.getAsString(Contract.Debt.EXPIRATION_DATE))
        }
        if (contentValues.containsKey(Contract.Debt.WALLET_ID)) {
            cv.put(Schema.Debt.WALLET, contentValues.getAsLong(Contract.Debt.WALLET_ID))
        }
        if (contentValues.containsKey(Contract.Debt.NOTE)) {
            cv.put(Schema.Debt.NOTE, contentValues.getAsString(Contract.Debt.NOTE))
        }
        if (contentValues.containsKey(Contract.Debt.PLACE_ID)) {
            cv.put(Schema.Debt.PLACE, contentValues.getAsLong(Contract.Debt.PLACE_ID))
        }
        if (contentValues.containsKey(Contract.Debt.MONEY)) {
            cv.put(Schema.Debt.MONEY, contentValues.getAsLong(Contract.Debt.MONEY))
        }
        if (contentValues.containsKey(Contract.Debt.ARCHIVED)) {
            cv.put(Schema.Debt.ARCHIVED, contentValues.getAsBoolean(Contract.Debt.ARCHIVED))
        }
        if (contentValues.containsKey(Contract.Debt.TAG)) {
            cv.put(Schema.Debt.TAG, contentValues.getAsString(Contract.Debt.TAG))
        }
        cv.put(Schema.Debt.LAST_EDIT, System.currentTimeMillis())
        val where = Schema.Debt.ID + " = ?"
        val whereArgs = arrayOf(debtId.toString())
        val rows = writableDatabase.update(Schema.Debt.TABLE, cv, where, whereArgs)
        if (rows > 0) {
            if (contentValues.containsKey(Contract.Debt.PEOPLE_IDS)) {
                updateDebtPeople(debtId, contentValues.getAsString(Contract.Debt.PEOPLE_IDS))
            }
            val debtCategoryId = getSystemCategoryId(Schema.CategoryTag.DEBT)
            val creditCategoryId = getSystemCategoryId(Schema.CategoryTag.CREDIT)
            val projection = arrayOf(Contract.Transaction.ID, Contract.Transaction.DATE)
            val selection = Contract.Transaction.TYPE + " = ? AND " +
                    Contract.Transaction.DEBT_ID + " = ? AND (" +
                    Contract.Transaction.CATEGORY_ID + " = ? OR " +
                    Contract.Transaction.CATEGORY_ID + " = ?)"
            val selectionArgs = arrayOf(
                Contract.TransactionType.DEBT.toString(),
                debtId.toString(),
                debtCategoryId.toString(),
                creditCategoryId.toString()
            )
            val cursor = getTransactions(projection, selection, selectionArgs, null)
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    val transactionId = cursor.getLong(cursor.getColumnIndex(Contract.Transaction.ID))
                    val transactionDate = cursor.getString(cursor.getColumnIndex(Contract.Transaction.DATE))
                    cv = ContentValues()
                    if (contentValues.containsKey(Contract.Debt.MONEY)) {
                        cv.put(Contract.Transaction.MONEY, contentValues.getAsLong(Contract.Debt.MONEY))
                    }
                    if (contentValues.containsKey(Contract.Debt.DESCRIPTION)) {
                        cv.put(Contract.Transaction.DESCRIPTION, contentValues.getAsString(Contract.Debt.DESCRIPTION))
                    }
                    if (contentValues.containsKey(Contract.Debt.DATE)) {
                        cv.put(
                            Contract.Transaction.DATE, masterTransactionDateFor(
                                contentValues.getAsString(Contract.Debt.DATE), transactionDate
                            )
                        )
                    }
                    if (contentValues.containsKey(Contract.Debt.TYPE)) {
                        val type = Contract.DebtType.fromValue(contentValues.getAsInteger(Contract.Debt.TYPE) ?: 0)
                        if (type == Contract.DebtType.CREDIT) {
                            cv.put(Contract.Transaction.DIRECTION, Contract.Direction.EXPENSE)
                            cv.put(Contract.Transaction.CATEGORY_ID, creditCategoryId)
                        } else if (type == Contract.DebtType.DEBT) {
                            cv.put(Contract.Transaction.DIRECTION, Contract.Direction.INCOME)
                            cv.put(Contract.Transaction.CATEGORY_ID, debtCategoryId)
                        }
                    }
                    if (contentValues.containsKey(Contract.Debt.WALLET_ID)) {
                        cv.put(Contract.Transaction.WALLET_ID, contentValues.getAsLong(Contract.Debt.WALLET_ID))
                    }
                    if (contentValues.containsKey(Contract.Debt.PLACE_ID)) {
                        cv.put(Contract.Transaction.PLACE_ID, contentValues.getAsLong(Contract.Debt.PLACE_ID))
                    }
                    if (contentValues.containsKey(Contract.Debt.NOTE)) {
                        cv.put(Contract.Transaction.NOTE, contentValues.getAsString(Contract.Debt.NOTE))
                    }
                    if (contentValues.containsKey(Contract.Debt.PEOPLE_IDS)) {
                        cv.put(Contract.Transaction.PEOPLE_IDS, contentValues.getAsString(Contract.Debt.PEOPLE_IDS))
                    }
                    updateTransactionRow(transactionId, cv)
                }
                cursor.close()
            }
        }
        return rows
    }

    open fun deleteDebt(debtId: Long): Int {
        val cursor = getDebtTransactions(debtId, arrayOf(Contract.Transaction.ID), null, null, null)
        if (cursor != null) {
            deleteTransactionItems(cursor)
        }
        val where = Schema.DebtPeople.DEBT + " = ?"
        val whereArgs = arrayOf(debtId.toString())
        writableDatabase.delete(Schema.DebtPeople.TABLE, where, whereArgs)
        val debtWhere = Schema.Debt.ID + " = ?"
        return writableDatabase.delete(Schema.Debt.TABLE, debtWhere, whereArgs)
    }


    open fun getBudget(id: Long, projection: Array<String>?): Cursor? {
        val selection = Schema.Budget.ID + " = ?"
        val selectionArgs = arrayOf(id.toString())
        return getBudgets(projection, selection, selectionArgs, null)
    }

    open fun getBudgets(
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        val subQuery = "SELECT " +
                "b." + Schema.Budget.ID + " AS " + Contract.Budget.ID + ", " +
                "b." + Schema.Budget.TYPE + " AS " + Contract.Budget.TYPE + ", " +
                "b." + Schema.Budget.CATEGORY + " AS " + Contract.Budget.CATEGORY_ID + ", " +
                "(SELECT GROUP_CONCAT(n, ', ') FROM (SELECT c2." + Schema.Category.NAME +
                " AS n FROM " + Schema.BudgetCategory.TABLE + " AS bc2 JOIN " +
                Schema.Category.TABLE + " AS c2 ON bc2." + Schema.BudgetCategory.CATEGORY +
                " = c2." + Schema.Category.ID + " AND c2." + Schema.Category.DELETED +
                " = 0 WHERE bc2." + Schema.BudgetCategory.BUDGET + " = b." + Schema.Budget.ID +
                " AND bc2." + Schema.BudgetCategory.DELETED + " = 0 ORDER BY c2." +
                Schema.Category.NAME + ")) AS " + Contract.Budget.CATEGORY_NAME + ", " +
                "(SELECT GROUP_CONCAT(i) FROM (SELECT '<' || bc3." + Schema.BudgetCategory.CATEGORY +
                " || '>' AS i FROM " + Schema.BudgetCategory.TABLE + " AS bc3 JOIN " +
                Schema.Category.TABLE + " AS c3 ON bc3." + Schema.BudgetCategory.CATEGORY +
                " = c3." + Schema.Category.ID + " AND c3." + Schema.Category.DELETED +
                " = 0 WHERE bc3." + Schema.BudgetCategory.BUDGET + " = b." + Schema.Budget.ID +
                " AND bc3." + Schema.BudgetCategory.DELETED + " = 0 ORDER BY bc3." +
                Schema.BudgetCategory.CATEGORY + ")) AS " + Contract.Budget.CATEGORY_IDS + ", " +
                "c." + Schema.Category.ICON + " AS " + Contract.Budget.CATEGORY_ICON + ", " +
                "c." + Schema.Category.TYPE + " AS " + Contract.Budget.CATEGORY_TYPE + ", " +
                "c." + Schema.Category.SHOW_REPORT + " AS " + Contract.Budget.CATEGORY_SHOW_REPORT + ", " +
                "c." + Schema.Category.TAG + " AS " + Contract.Budget.CATEGORY_TAG + ", " +
                "b." + Schema.Budget.START_DATE + " AS " + Contract.Budget.START_DATE + ", " +
                "b." + Schema.Budget.END_DATE + " AS " + Contract.Budget.END_DATE + ", " +
                "b." + Schema.Budget.MONEY + " AS " + Contract.Budget.MONEY + ", " +
                "b." + Schema.Budget.CURRENCY + " AS " + Contract.Budget.CURRENCY + ", " +
                "b." + Schema.Budget.RULE + " AS " + Contract.Budget.RULE + ", " +
                "b." + Schema.Budget.RULE_START + " AS " + Contract.Budget.RULE_START + ", " +
                "b." + Schema.Budget.TAG + " AS " + Contract.Budget.TAG + ", " +
                "SUM(_progress) AS " + Contract.Budget.PROGRESS + "," +
                "GROUP_CONCAT('<' || _wallet_id || '>') AS " + Contract.Budget.WALLET_IDS + "," +
                "MAX(_wallet_total) AS " + Contract.Budget.HAS_WALLET_IN_TOTAL + " FROM(" +

                // query all budgets of type expenses
                "SELECT b.*, SUM(t." + Schema.Transaction.MONEY + ") AS _progress " +
                "FROM(SELECT b.*, w." + Schema.Wallet.ID + " AS _wallet_id, " +
                "w." + Schema.Wallet.CURRENCY + " AS _wallet_currency, " +
                "w." + Schema.Wallet.COUNT_IN_TOTAL + " AS _wallet_total FROM " +
                Schema.BudgetWallet.TABLE + " AS bw JOIN " + Schema.Budget.TABLE + " AS b ON " +
                "bw._budget = b." + Schema.Budget.ID + " JOIN " + Schema.Wallet.TABLE + " AS w ON " +
                "bw._wallet = w." + Schema.Wallet.ID + " WHERE bw. " + Schema.BudgetWallet.DELETED +
                " = 0 AND b." + Schema.Budget.DELETED + " = 0 AND w." + Schema.Wallet.DELETED +
                " = 0 AND b." + Schema.Budget.TYPE + " = " + Schema.BudgetType.EXPENSES +
                " ) AS b LEFT JOIN " + Schema.Transaction.TABLE + " AS t ON b._wallet_id = t." +
                Schema.Transaction.WALLET + " AND t." + Schema.Transaction.DELETED + " = 0 AND t." +
                Schema.Transaction.DIRECTION + " = 0 AND DATETIME(t." + Schema.Transaction.DATE +
                ") <= DATETIME('now', 'localtime') AND DATE(t." + Schema.Transaction.DATE +
                ") >= DATE(b." + Schema.Budget.START_DATE + ") AND DATE(t." + Schema.Transaction.DATE +
                ") <=  DATE(b." + Schema.Budget.END_DATE + ") " +
                // exclude transfers within the budget
                "AND t." + Schema.Transaction.ID + " NOT IN ( " +
                "SELECT tf." + Schema.Transfer.TRANSACTION_FROM + " " +
                "FROM " + Schema.Transfer.TABLE + " AS tf " +
                "LEFT JOIN " + Schema.Transaction.TABLE + " AS t2 " +
                "ON tf." + Schema.Transfer.TRANSACTION_TO + " = t2." + Schema.Transaction.ID + " " +
                "LEFT JOIN " + Schema.BudgetWallet.TABLE + " AS bw2 " +
                "ON t2." + Schema.Transaction.WALLET + " = bw2." + Schema.BudgetWallet.WALLET + " " +
                "WHERE bw2." + Schema.BudgetWallet.BUDGET + " = b." + Schema.Budget.ID + " " +
                "AND bw2." + Schema.BudgetWallet.DELETED + " = 0 " +
                "AND bw2." + Schema.BudgetWallet.WALLET + " != b._wallet_id " +
                ") " +
                "GROUP BY b." + Schema.Budget.ID + ",b._wallet_id " +
                "UNION " +

                // query all budgets of type incomes
                "SELECT b.*, SUM(t." + Schema.Transaction.MONEY + ") AS _progress " +
                "FROM(SELECT b.*, w." + Schema.Wallet.ID + " AS _wallet_id, " +
                "w." + Schema.Wallet.CURRENCY + " AS _wallet_currency, " +
                "w." + Schema.Wallet.COUNT_IN_TOTAL + " AS _wallet_total FROM " +
                Schema.BudgetWallet.TABLE + " AS bw JOIN " + Schema.Budget.TABLE + " AS b ON " +
                "bw._budget = b." + Schema.Budget.ID + " JOIN " + Schema.Wallet.TABLE + " AS w ON " +
                "bw._wallet = w." + Schema.Wallet.ID + " WHERE bw. " + Schema.BudgetWallet.DELETED +
                " = 0 AND b." + Schema.Budget.DELETED + " = 0 AND w." + Schema.Wallet.DELETED +
                " = 0 AND b." + Schema.Budget.TYPE + " = " + Schema.BudgetType.INCOMES +
                " ) AS b LEFT JOIN " + Schema.Transaction.TABLE + " AS t ON b._wallet_id = t." +
                Schema.Transaction.WALLET + " AND t." + Schema.Transaction.DELETED + " = 0 AND t." +
                Schema.Transaction.DIRECTION + " = 1 AND DATETIME(t." + Schema.Transaction.DATE +
                ") <= DATETIME('now', 'localtime') AND DATE(t." + Schema.Transaction.DATE +
                ") >= DATE(b." + Schema.Budget.START_DATE + ") AND DATE(t." +
                Schema.Transaction.DATE + ") <= DATE(b." + Schema.Budget.END_DATE + ") " +
                // exclude transfers within the budget
                "AND t." + Schema.Transaction.ID + " NOT IN ( " +
                "SELECT tf." + Schema.Transfer.TRANSACTION_TO + " " +
                "FROM " + Schema.Transfer.TABLE + " AS tf " +
                "LEFT JOIN " + Schema.Transaction.TABLE + " AS t2 " +
                "ON tf." + Schema.Transfer.TRANSACTION_FROM + " = t2." + Schema.Transaction.ID + " " +
                "LEFT JOIN " + Schema.BudgetWallet.TABLE + " AS bw2 " +
                "ON t2." + Schema.Transaction.WALLET + " = bw2." + Schema.BudgetWallet.WALLET + " " +
                "WHERE bw2." + Schema.BudgetWallet.BUDGET + " = b." + Schema.Budget.ID + " " +
                "AND bw2." + Schema.BudgetWallet.DELETED + " = 0 " +
                "AND bw2." + Schema.BudgetWallet.WALLET + " != b._wallet_id " +
                ") " +

                "GROUP BY b."
                + Schema.Budget.ID + ", _wallet_id UNION " +
                // query all budgets of type category
                "SELECT b.*, SUM(((t." + Schema.Transaction.DIRECTION + " * 2) - 1) * t." +
                Schema.Transaction.MONEY + ") AS _progress FROM(SELECT b.*, w." + Schema.Wallet.ID +
                " AS _wallet_id, w." + Schema.Wallet.CURRENCY + " AS _wallet_currency, w." +
                Schema.Wallet.COUNT_IN_TOTAL + " AS _wallet_total FROM " + Schema.BudgetWallet.TABLE +
                " AS bw JOIN " + Schema.Budget.TABLE + " AS b ON bw._budget = b." + Schema.Budget.ID +
                " JOIN " + Schema.Wallet.TABLE + " AS w ON bw._wallet = w." + Schema.Wallet.ID +
                " WHERE bw. " + Schema.BudgetWallet.DELETED + " = 0 AND b." + Schema.Budget.DELETED +
                " = 0 AND w." + Schema.Wallet.DELETED + " = 0 AND b." + Schema.Budget.TYPE + " = " +
                Schema.BudgetType.CATEGORY + ") AS b LEFT JOIN (SELECT tr.*, tc." +
                Schema.Category.PARENT + " AS _parent_category FROM " + Schema.Transaction.TABLE +
                " AS tr JOIN " + Schema.Category.TABLE + " AS tc ON tr." + Schema.Transaction.CATEGORY +
                " = tc." + Schema.Category.ID + " WHERE tr." + Schema.Transaction.DELETED + " = 0 " +
                " AND tc." + Schema.Category.DELETED + " = 0) AS t ON (b._wallet_id = t." +
                Schema.Transaction.WALLET + " AND DATETIME(t." + Schema.Transaction.DATE +
                ") <= DATETIME('now', 'localtime') AND DATE(t." + Schema.Transaction.DATE +
                ") >= DATE(b." + Schema.Budget.START_DATE + ") AND DATE(t." +
                Schema.Transaction.DATE + ") <=  DATE(b." + Schema.Budget.END_DATE + ") AND (t."
                + Schema.Transaction.CATEGORY + " IN " + budgetCategoriesOf("b") +
                " OR t._parent_category IN " + budgetCategoriesOf("b") + ")) GROUP BY b." + Schema.Budget.ID +
                ", _wallet_id) AS b LEFT JOIN " + Schema.Category.TABLE + " AS c ON b." +
                Schema.Budget.CATEGORY + " = c." + Schema.Category.ID + " AND c." +
                Schema.Category.DELETED + " = 0 GROUP BY b." + Schema.Budget.ID
        return queryFrom(subQuery, projection, selection, selectionArgs, sortOrder)
    }

    open fun getBudgetWallets(
        id: Long,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        val subQuery = "SELECT " +
                Schema.Wallet.ID + " AS " + Contract.Wallet.ID + ", " +
                Schema.Wallet.NAME + " AS " + Contract.Wallet.NAME + ", " +
                Schema.Wallet.ICON + " AS " + Contract.Wallet.ICON + ", " +
                Schema.Wallet.CURRENCY + " AS " + Contract.Wallet.CURRENCY + ", " +
                Schema.Wallet.COUNT_IN_TOTAL + " AS " + Contract.Wallet.COUNT_IN_TOTAL + ", " +
                Schema.Wallet.START_MONEY + " AS " + Contract.Wallet.START_MONEY + ", " +
                Schema.Wallet.ARCHIVED + " AS " + Contract.Wallet.ARCHIVED + ", " +
                Schema.Wallet.TAG + " AS " + Contract.Wallet.TAG + " " +
                "FROM " + Schema.BudgetWallet.TABLE + " AS bw JOIN " + Schema.Wallet.TABLE + " AS w " +
                "ON bw." + Schema.BudgetWallet.WALLET + " = w." + Schema.Wallet.ID + " AND bw." +
                Schema.BudgetWallet.DELETED + " = 0 AND w." + Schema.Wallet.DELETED + " = 0 AND " +
                Schema.BudgetWallet.BUDGET + " = " + id
        return queryFrom(subQuery, projection, selection, selectionArgs, sortOrder)
    }

    open fun getBudgetCategories(
        id: Long,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        val subQuery = "SELECT " +
                "c." + Schema.Category.ID + " AS " + Contract.Category.ID + ", " +
                "c." + Schema.Category.NAME + " AS " + Contract.Category.NAME + ", " +
                "c." + Schema.Category.ICON + " AS " + Contract.Category.ICON + ", " +
                "c." + Schema.Category.TYPE + " AS " + Contract.Category.TYPE + ", " +
                "c." + Schema.Category.PARENT + " AS " + Contract.Category.PARENT + ", " +
                "c." + Schema.Category.SHOW_REPORT + " AS " + Contract.Category.SHOW_REPORT + ", " +
                "c." + Schema.Category.INDEX + " AS " + Contract.Category.INDEX + ", " +
                "c." + Schema.Category.TAG + " AS " + Contract.Category.TAG + " " +
                "FROM " + Schema.BudgetCategory.TABLE + " AS bc JOIN " + Schema.Category.TABLE +
                " AS c ON bc." + Schema.BudgetCategory.CATEGORY + " = c." + Schema.Category.ID +
                " AND bc." + Schema.BudgetCategory.DELETED + " = 0 AND c." +
                Schema.Category.DELETED + " = 0 AND bc." + Schema.BudgetCategory.BUDGET + " = " +
                id
        return queryFrom(subQuery, projection, selection, selectionArgs, sortOrder)
    }

    open fun getBudgetTransactions(
        budgetId: Long,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        val subQuery = "SELECT " +
                "t." + Schema.Transaction.ID + " AS " + Contract.Transaction.ID + ", " +
                "t." + Schema.Transaction.MONEY + " AS " + Contract.Transaction.MONEY + ", " +
                "t." + Schema.Transaction.DATE + " AS " + Contract.Transaction.DATE + ", " +
                "t." + Schema.Transaction.DESCRIPTION + " AS " + Contract.Transaction.DESCRIPTION + ", " +
                "t." + Schema.Transaction.CATEGORY + " AS " + Contract.Transaction.CATEGORY_ID + ", " +
                "c." + Schema.Category.NAME + " AS " + Contract.Transaction.CATEGORY_NAME + ", " +
                "c." + Schema.Category.ICON + " AS " + Contract.Transaction.CATEGORY_ICON + ", " +
                "c." + Schema.Category.PARENT + " AS " + Contract.Transaction.CATEGORY_PARENT_ID + ", " +
                "c." + Schema.Category.TYPE + " AS " + Contract.Transaction.CATEGORY_TYPE + ", " +
                "c." + Schema.Category.TAG + " AS " + Contract.Transaction.CATEGORY_TAG + ", " +
                "c." + Schema.Category.SHOW_REPORT + " AS " + Contract.Transaction.CATEGORY_SHOW_REPORT + ", " +
                "t." + Schema.Transaction.DIRECTION + " AS " + Contract.Transaction.DIRECTION + ", " +
                "t." + Schema.Transaction.TYPE + " AS " + Contract.Transaction.TYPE + ", " +
                "t." + Schema.Transaction.WALLET + " AS " + Contract.Transaction.WALLET_ID + ", " +
                "w." + Schema.Wallet.NAME + " AS " + Contract.Transaction.WALLET_NAME + ", " +
                "w." + Schema.Wallet.ICON + " AS " + Contract.Transaction.WALLET_ICON + ", " +
                "w." + Schema.Wallet.CURRENCY + " AS " + Contract.Transaction.WALLET_CURRENCY + ", " +
                "w." + Schema.Wallet.COUNT_IN_TOTAL + " AS " + Contract.Transaction.WALLET_COUNT_IN_TOTAL + ", " +
                "w." + Schema.Wallet.ARCHIVED + " AS " + Contract.Transaction.WALLET_ARCHIVED + ", " +
                "w." + Schema.Wallet.TAG + " AS " + Contract.Transaction.WALLET_TAG + ", " +
                "t." + Schema.Transaction.PLACE + " AS " + Contract.Transaction.PLACE_ID + ", " +
                "p." + Schema.Place.NAME + " AS " + Contract.Transaction.PLACE_NAME + ", " +
                "p." + Schema.Place.ICON + " AS " + Contract.Transaction.PLACE_ICON + ", " +
                "p." + Schema.Place.ADDRESS + " AS " + Contract.Transaction.PLACE_ADDRESS + ", " +
                "p." + Schema.Place.LATITUDE + " AS " + Contract.Transaction.PLACE_LATITUDE + ", " +
                "p." + Schema.Place.LONGITUDE + " AS " + Contract.Transaction.PLACE_LONGITUDE + ", " +
                "p." + Schema.Place.TAG + " AS " + Contract.Transaction.PLACE_TAG + ", " +
                "t." + Schema.Transaction.EVENT + " AS " + Contract.Transaction.EVENT_ID + ", " +
                "e." + Schema.Event.NAME + " AS " + Contract.Transaction.EVENT_NAME + ", " +
                "e." + Schema.Event.ICON + " AS " + Contract.Transaction.EVENT_ICON + ", " +
                "e." + Schema.Event.NOTE + " AS " + Contract.Transaction.EVENT_NOTE + ", " +
                "e." + Schema.Event.START_DATE + " AS " + Contract.Transaction.EVENT_START_DATE + ", " +
                "e." + Schema.Event.END_DATE + " AS " + Contract.Transaction.EVENT_END_DATE + ", " +
                "e." + Schema.Event.TAG + " AS " + Contract.Transaction.EVENT_TAG + ", " +
                "t." + Schema.Transaction.NOTE + " AS " + Contract.Transaction.NOTE + ", " +
                "t." + Schema.Transaction.DEBT + " AS " + Contract.Transaction.DEBT_ID + ", " +
                "t." + Schema.Transaction.SAVING + " AS " + Contract.Transaction.SAVING_ID + ", " +
                "t." + Schema.Transaction.CONFIRMED + " AS " + Contract.Transaction.CONFIRMED + ", " +
                "t." + Schema.Transaction.COUNT_IN_TOTAL + " AS " + Contract.Transaction.COUNT_IN_TOTAL + ", " +
                "t." + Schema.Transaction.TAG + " AS " + Contract.Transaction.TAG + ", " +
                "GROUP_CONCAT('<' || pe." + Schema.Person.ID + " || '>') AS " + Contract.Transaction.PEOPLE_IDS + " " +
                "FROM (" +

                // case budget of type expense
                "SELECT t.* FROM(SELECT b.*, w." + Schema.Wallet.ID + " AS _wallet_id, " +
                "w." + Schema.Wallet.CURRENCY + " AS _wallet_currency, " +
                "w." + Schema.Wallet.COUNT_IN_TOTAL + " AS _wallet_total FROM " +
                Schema.BudgetWallet.TABLE + " AS bw JOIN " + Schema.Budget.TABLE + " AS b ON " +
                "bw._budget = b." + Schema.Budget.ID + " JOIN " + Schema.Wallet.TABLE + " AS w ON " +
                "bw._wallet = w." + Schema.Wallet.ID + " WHERE bw. " + Schema.BudgetWallet.DELETED +
                " = 0 AND b." + Schema.Budget.DELETED + " = 0 AND w." + Schema.Wallet.DELETED +
                " = 0 AND b." + Schema.Budget.TYPE + " = " + Schema.BudgetType.EXPENSES +
                " ) AS b LEFT JOIN " + Schema.Transaction.TABLE + " AS t ON b._wallet_id = t." +
                Schema.Transaction.WALLET + " AND t." + Schema.Transaction.DELETED + " = 0 AND t." +
                Schema.Transaction.DIRECTION + " = 0 AND DATETIME(t." + Schema.Transaction.DATE +
                ") <= DATETIME('now', 'localtime') AND DATE(t." + Schema.Transaction.DATE +
                ") >= DATE(b." + Schema.Budget.START_DATE + ") AND DATE(t." + Schema.Transaction.DATE +
                ") <= DATE(b." + Schema.Budget.END_DATE + ") WHERE b." + Schema.Budget.ID + " = " +
                budgetId + " " +
                // exclude transfers within the budget
                "AND t." + Schema.Transaction.ID + " NOT IN ( " +
                "SELECT tf." + Schema.Transfer.TRANSACTION_FROM + " " +
                "FROM " + Schema.Transfer.TABLE + " AS tf " +
                "LEFT JOIN " + Schema.Transaction.TABLE + " AS t2 " +
                "ON tf." + Schema.Transfer.TRANSACTION_TO + " = t2." + Schema.Transaction.ID + " " +
                "LEFT JOIN " + Schema.BudgetWallet.TABLE + " AS bw2 " +
                "ON t2." + Schema.Transaction.WALLET + " = bw2." + Schema.BudgetWallet.WALLET + " " +
                "WHERE bw2." + Schema.BudgetWallet.BUDGET + " = b." + Schema.Budget.ID + " " +
                "AND bw2." + Schema.BudgetWallet.DELETED + " = 0 " +
                "AND bw2." + Schema.BudgetWallet.WALLET + " != b._wallet_id " +
                ") " +
                "UNION " +

                // case budget of type income
                "SELECT t.* FROM(SELECT b.*, w." + Schema.Wallet.ID + " AS _wallet_id, " +
                "w." + Schema.Wallet.CURRENCY + " AS _wallet_currency, " +
                "w." + Schema.Wallet.COUNT_IN_TOTAL + " AS _wallet_total FROM " +
                Schema.BudgetWallet.TABLE + " AS bw JOIN " + Schema.Budget.TABLE + " AS b ON " +
                "bw._budget = b." + Schema.Budget.ID + " JOIN " + Schema.Wallet.TABLE + " AS w ON " +
                "bw._wallet = w." + Schema.Wallet.ID + " WHERE bw. " + Schema.BudgetWallet.DELETED +
                " = 0 AND b." + Schema.Budget.DELETED + " = 0 AND w." + Schema.Wallet.DELETED +
                " = 0 AND b." + Schema.Budget.TYPE + " = " + Schema.BudgetType.INCOMES +
                " ) AS b LEFT JOIN " + Schema.Transaction.TABLE + " AS t ON b._wallet_id = t." +
                Schema.Transaction.WALLET + " AND t." + Schema.Transaction.DELETED + " = 0 AND t." +
                Schema.Transaction.DIRECTION + " = 1 AND DATETIME(t." + Schema.Transaction.DATE +
                ") <= DATETIME('now', 'localtime') AND DATE(t." + Schema.Transaction.DATE +
                ") >= DATE(b." + Schema.Budget.START_DATE + ") AND DATE(t." +
                Schema.Transaction.DATE + ") <= DATE(b." + Schema.Budget.END_DATE + ") WHERE b."
                + Schema.Budget.ID + " = " + budgetId + " " +
                // exclude transfers within the budget
                "AND t." + Schema.Transaction.ID + " NOT IN ( " +
                "SELECT tf." + Schema.Transfer.TRANSACTION_TO + " " +
                "FROM " + Schema.Transfer.TABLE + " AS tf " +
                "LEFT JOIN " + Schema.Transaction.TABLE + " AS t2 " +
                "ON tf." + Schema.Transfer.TRANSACTION_FROM + " = t2." + Schema.Transaction.ID + " " +
                "LEFT JOIN " + Schema.BudgetWallet.TABLE + " AS bw2 " +
                "ON t2." + Schema.Transaction.WALLET + " = bw2." + Schema.BudgetWallet.WALLET + " " +
                "WHERE bw2." + Schema.BudgetWallet.BUDGET + " = b." + Schema.Budget.ID + " " +
                "AND bw2." + Schema.BudgetWallet.DELETED + " = 0 " +
                "AND bw2." + Schema.BudgetWallet.WALLET + " != b._wallet_id " +
                ") " +
                "UNION " +

                // case budget of type category
                "SELECT t.* FROM(SELECT b.*, w." + Schema.Wallet.ID + " AS _wallet_id, " +
                "w." + Schema.Wallet.CURRENCY + " AS _wallet_currency, " +
                "w." + Schema.Wallet.COUNT_IN_TOTAL + " AS _wallet_total FROM " +
                Schema.BudgetWallet.TABLE + " AS bw JOIN " + Schema.Budget.TABLE + " AS b ON " +
                "bw._budget = b." + Schema.Budget.ID + " JOIN " + Schema.Wallet.TABLE + " AS w ON " +
                "bw._wallet = w." + Schema.Wallet.ID + " WHERE bw. " + Schema.BudgetWallet.DELETED +
                " = 0 AND b." + Schema.Budget.DELETED + " = 0 AND w." + Schema.Wallet.DELETED +
                " = 0 AND b." + Schema.Budget.TYPE + " = " + Schema.BudgetType.CATEGORY +
                ") AS b LEFT JOIN " + Schema.Transaction.TABLE + " AS t ON b._wallet_id = t." +
                Schema.Transaction.WALLET + " AND t." + Schema.Transaction.DELETED + " = 0 JOIN " +
                Schema.Category.TABLE + " AS tc ON t." + Schema.Transaction.CATEGORY + " = tc." +
                Schema.Category.ID + " AND tc." + Schema.Category.DELETED + " = 0 WHERE (" +
                Schema.Transaction.CATEGORY + " IN " + budgetCategoriesOf("b") + " OR tc." +
                Schema.Category.PARENT + " IN " + budgetCategoriesOf("b") + ") " +
                "AND DATETIME(t." + Schema.Transaction.DATE + ") <= DATETIME('now', 'localtime') " +
                "AND DATE(t." + Schema.Transaction.DATE + ") >= DATE(b." +
                Schema.Budget.START_DATE + ") AND DATE(t." + Schema.Transaction.DATE +
                ") <=  DATE(b." + Schema.Budget.END_DATE + ") " + "AND b." + Schema.Budget.ID +
                " = " + budgetId + " " +

                ") AS t " +
                "LEFT JOIN " + Schema.Category.TABLE + " AS c " +
                "ON t." + Schema.Transaction.CATEGORY + " = c." + Schema.Category.ID + " " +
                "AND c." + Schema.Category.DELETED + " = 0 " +
                "JOIN " + Schema.Wallet.TABLE + " AS w " +
                "ON t." + Schema.Transaction.WALLET + " = w." + Schema.Wallet.ID + " " +
                "AND w." + Schema.Wallet.DELETED + " = 0 " +
                "LEFT JOIN " + Schema.TransactionPeople.TABLE + " AS tp " +
                "ON t." + Schema.Transaction.ID + " = tp." + Schema.TransactionPeople.TRANSACTION + " " +
                "AND tp." + Schema.TransactionPeople.DELETED + " = 0 " +
                "LEFT JOIN " + Schema.Person.TABLE + " AS pe " +
                "ON tp." + Schema.TransactionPeople.PERSON + " = pe." + Schema.Person.ID + " " +
                "AND pe." + Schema.Person.DELETED + " = 0 " +
                "LEFT JOIN " + Schema.Place.TABLE + " AS p " +
                "ON t." + Schema.Transaction.PLACE + " = " + Schema.Place.ID + " " +
                "AND p." + Schema.Place.DELETED + " = 0 " +
                "LEFT JOIN " + Schema.Event.TABLE + " AS e " +
                "ON t." + Schema.Transaction.EVENT + " = e." + Schema.Event.ID + " " +
                "AND e." + Schema.Event.DELETED + " = 0 " +
                "WHERE t." + Schema.Transaction.DELETED + " = 0 " +
                "GROUP BY t." + Schema.Transaction.ID
        return queryFrom(subQuery, projection, selection, selectionArgs, sortOrder)
    }

    open fun insertBudget(contentValues: ContentValues): Long {
        val walletIds = parseIds(contentValues.getAsString(Contract.Budget.WALLET_IDS))
        if (walletIds == null || walletIds.isEmpty()) {
            throw SQLiteDataException(Contract.ErrorCode.WALLETS_NOT_FOUND, "No wallet id provided")
        }
        val currency = checkWalletsConsistency(walletIds)
        val categoryIds = budgetCategoryIds(contentValues)
        val cv = ContentValues()
        cv.put(Schema.Budget.TYPE, contentValues.getAsInteger(Contract.Budget.TYPE))
        if (categoryIds == null) {
            cv.putNull(Schema.Budget.CATEGORY)
        } else {
            cv.put(Schema.Budget.CATEGORY, categoryIds[0])
        }
        cv.put(Schema.Budget.START_DATE, contentValues.getAsString(Contract.Budget.START_DATE))
        cv.put(Schema.Budget.END_DATE, contentValues.getAsString(Contract.Budget.END_DATE))
        cv.put(Schema.Budget.MONEY, contentValues.getAsLong(Contract.Budget.MONEY))
        cv.put(Schema.Budget.CURRENCY, currency)
        cv.put(Schema.Budget.RULE, contentValues.getAsString(Contract.Budget.RULE))
        cv.put(Schema.Budget.RULE_START, contentValues.getAsString(Contract.Budget.RULE_START))
        cv.put(Schema.Budget.TAG, contentValues.getAsString(Contract.Budget.TAG))
        cv.put(Schema.Budget.UUID, budgetUUID(contentValues))
        cv.put(Schema.Budget.LAST_EDIT, System.currentTimeMillis())
        cv.put(Schema.Budget.DELETED, false)
        val budgetId = writableDatabase.insert(Schema.Budget.TABLE, null, cv)
        if (budgetId > 0) {
            for (walletId in walletIds) {
                val cvw = ContentValues()
                cvw.put(Schema.BudgetWallet.BUDGET, budgetId)
                cvw.put(Schema.BudgetWallet.WALLET, walletId)
                cvw.put(Schema.BudgetWallet.UUID, UUID.randomUUID().toString())
                cvw.put(Schema.BudgetWallet.LAST_EDIT, System.currentTimeMillis())
                cvw.put(Schema.BudgetWallet.DELETED, false)
                writableDatabase.insert(Schema.BudgetWallet.TABLE, null, cvw)
            }
            writeBudgetCategories(budgetId, categoryIds)
        }
        return budgetId
    }

    private fun budgetUUID(contentValues: ContentValues): String {
        if (contentValues.containsKey(Contract.Budget.ROLLED_FROM_ID)) {
            val projection = arrayOf(Schema.Budget.UUID)
            val selection = Schema.Budget.ID + " = ?"
            val rolledFromId = contentValues.getAsString(Contract.Budget.ROLLED_FROM_ID)
            val selectionArgs = arrayOf(rolledFromId ?: "")
            val cursor = readableDatabase.query(Schema.Budget.TABLE, projection, selection, selectionArgs, null, null, null)
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        val parentUUID = cursor.getString(cursor.getColumnIndex(Schema.Budget.UUID))
                        val startDateStr = contentValues.getAsString(Contract.Budget.START_DATE)
                        val startDate = if (startDateStr != null) DateUtils.getDateFromSQLDateString(startDateStr) else null
                        if (parentUUID != null && startDate != null) {
                            return getRecurrentItemUUID(Contract.budgetChainOf(parentUUID), startDate)
                        }
                    }
                } finally {
                    cursor.close()
                }
            }
        }
        return UUID.randomUUID().toString()
    }

    open fun updateBudget(budgetId: Long, contentValues: ContentValues): Int {
        val walletIds = parseIds(contentValues.getAsString(Contract.Budget.WALLET_IDS))
        if (walletIds == null || walletIds.isEmpty()) {
            throw SQLiteDataException(Contract.ErrorCode.WALLETS_NOT_FOUND, "No wallet id provided")
        }
        val currency = checkWalletsConsistency(walletIds)
        val categoryIds = budgetCategoryIds(contentValues)
        var cv = ContentValues()
        cv.put(Schema.Budget.TYPE, contentValues.getAsInteger(Contract.Budget.TYPE))
        if (categoryIds == null) {
            cv.putNull(Schema.Budget.CATEGORY)
        } else {
            cv.put(Schema.Budget.CATEGORY, categoryIds[0])
        }
        cv.put(Schema.Budget.START_DATE, contentValues.getAsString(Contract.Budget.START_DATE))
        cv.put(Schema.Budget.END_DATE, contentValues.getAsString(Contract.Budget.END_DATE))
        cv.put(Schema.Budget.MONEY, contentValues.getAsLong(Contract.Budget.MONEY))
        cv.put(Schema.Budget.CURRENCY, currency)
        if (contentValues.containsKey(Contract.Budget.RULE)) {
            cv.put(Schema.Budget.RULE, contentValues.getAsString(Contract.Budget.RULE))
        }
        if (contentValues.containsKey(Contract.Budget.RULE_START)) {
            cv.put(Schema.Budget.RULE_START, contentValues.getAsString(Contract.Budget.RULE_START))
        }
        if (contentValues.containsKey(Contract.Budget.TAG)) {
            cv.put(Schema.Budget.TAG, contentValues.getAsString(Contract.Budget.TAG))
        }
        cv.put(Schema.Budget.LAST_EDIT, System.currentTimeMillis())
        var where = Schema.Budget.ID + " = ?"
        var whereArgs = arrayOf(budgetId.toString())
        val rows = writableDatabase.update(Schema.Budget.TABLE, cv, where, whereArgs)
        if (rows > 0) {
            cv = ContentValues()
            cv.put(Schema.BudgetWallet.DELETED, true)
            where = Schema.BudgetWallet.BUDGET + " = ?"
            whereArgs = arrayOf(budgetId.toString())
            writableDatabase.update(Schema.BudgetWallet.TABLE, cv, where, whereArgs)
            for (walletId in walletIds) {
                cv = ContentValues()
                cv.put(Schema.BudgetWallet.BUDGET, budgetId)
                cv.put(Schema.BudgetWallet.WALLET, walletId)
                cv.put(Schema.BudgetWallet.UUID, UUID.randomUUID().toString())
                cv.put(Schema.BudgetWallet.LAST_EDIT, System.currentTimeMillis())
                cv.put(Schema.BudgetWallet.DELETED, false)
                val newId = writableDatabase.insertWithOnConflict(Schema.BudgetWallet.TABLE, null, cv, SQLiteDatabase.CONFLICT_IGNORE)
                if (newId == -1L) {
                    cv = ContentValues()
                    cv.put(Schema.BudgetWallet.LAST_EDIT, System.currentTimeMillis())
                    cv.put(Schema.BudgetWallet.DELETED, false)
                    where = Schema.BudgetWallet.BUDGET + " = ? AND " + Schema.BudgetWallet.WALLET + " = ?"
                    whereArgs = arrayOf(budgetId.toString(), walletId.toString())
                    writableDatabase.update(Schema.BudgetWallet.TABLE, cv, where, whereArgs)
                }
            }
            cv = ContentValues()
            cv.put(Schema.BudgetCategory.DELETED, true)
            cv.put(Schema.BudgetCategory.LAST_EDIT, System.currentTimeMillis())
            where = Schema.BudgetCategory.BUDGET + " = ?"
            whereArgs = arrayOf(budgetId.toString())
            writableDatabase.update(Schema.BudgetCategory.TABLE, cv, where, whereArgs)
            writeBudgetCategories(budgetId, categoryIds)
        }
        return rows
    }

    private fun budgetCategoryIds(contentValues: ContentValues): LongArray? {
        val type = contentValues.getAsInteger(Contract.Budget.TYPE)
        if (type == null || type != Schema.BudgetType.CATEGORY) {
            return null
        }
        val categoryIds = parseIds(contentValues.getAsString(Contract.Budget.CATEGORY_IDS))
        if (categoryIds != null && categoryIds.isNotEmpty()) {
            return categoryIds
        }
        val categoryId = contentValues.getAsLong(Contract.Budget.CATEGORY_ID)
        return if (categoryId == null) null else longArrayOf(categoryId)
    }

    private fun writeBudgetCategories(budgetId: Long, categoryIds: LongArray?) {
        if (categoryIds == null) {
            return
        }
        for (categoryId in categoryIds) {
            var cv = ContentValues()
            cv.put(Schema.BudgetCategory.BUDGET, budgetId)
            cv.put(Schema.BudgetCategory.CATEGORY, categoryId)
            cv.put(Schema.BudgetCategory.UUID, UUID.randomUUID().toString())
            cv.put(Schema.BudgetCategory.LAST_EDIT, System.currentTimeMillis())
            cv.put(Schema.BudgetCategory.DELETED, false)
            val newId: Long
            try {
                newId = writableDatabase.insertWithOnConflict(Schema.BudgetCategory.TABLE, null, cv, SQLiteDatabase.CONFLICT_IGNORE)
            } catch (e: SQLiteConstraintException) {
                continue
            }
            if (newId == -1L) {
                cv = ContentValues()
                cv.put(Schema.BudgetCategory.LAST_EDIT, System.currentTimeMillis())
                cv.put(Schema.BudgetCategory.DELETED, false)
                val where = Schema.BudgetCategory.BUDGET + " = ? AND " + Schema.BudgetCategory.CATEGORY + " = ?"
                val whereArgs = arrayOf(budgetId.toString(), categoryId.toString())
                writableDatabase.update(Schema.BudgetCategory.TABLE, cv, where, whereArgs)
            }
        }
    }

    private fun checkWalletsConsistency(walletIds: LongArray): String? {
        var savedCurrency: String? = null
        val projection = arrayOf(Contract.Wallet.CURRENCY)
        for (walletId in walletIds) {
            val cursor = getWallet(walletId, projection)
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        val currency = cursor.getString(cursor.getColumnIndex(Contract.Wallet.CURRENCY))
                        val currentSaved = savedCurrency
                        if (currentSaved != null) {
                            if (!TextUtils.equals(currentSaved, currency)) {
                                val message = String.format(Locale.ENGLISH, "Wallet currency is not consistent (found %s and %s)", currentSaved, currency)
                                throw SQLiteDataException(Contract.ErrorCode.WALLETS_NOT_CONSISTENT, message)
                            }
                        } else {
                            savedCurrency = currency
                        }
                    } else {
                        val message = String.format(Locale.ENGLISH, "Wallet (id: %d) not found", walletId)
                        throw SQLiteDataException(Contract.ErrorCode.WALLETS_NOT_FOUND, message)
                    }
                } finally {
                    cursor.close()
                }
            }
        }
        return savedCurrency
    }

    private fun checkStrandedCurrency(
        storedWalletId: Long,
        walletId: Long,
        selection: String,
        selectionArgs: Array<String>
    ) {
        val walletCurrency = walletCurrency(walletId)
        if (walletCurrency == null || TextUtils.equals(walletCurrency, walletCurrency(storedWalletId))) {
            return
        }
        if (noneIsHeldIn(walletCurrency, selection, selectionArgs)) {
            val message = String.format(
                Locale.ENGLISH,
                "Wallet (id: %d) is in %s and nothing filed against this item is",
                walletId,
                walletCurrency
            )
            throw SQLiteDataException(Contract.ErrorCode.WALLETS_NOT_CONSISTENT, message)
        }
    }

    private fun noneIsHeldIn(currency: String, selection: String, selectionArgs: Array<String>): Boolean {
        val query = "SELECT COUNT(*), SUM(CASE WHEN w." + Schema.Wallet.CURRENCY +
                " = ? THEN 1 ELSE 0 END) FROM " + Schema.Transaction.TABLE + " AS t JOIN " +
                Schema.Wallet.TABLE + " AS w ON t." + Schema.Transaction.WALLET + " = w." +
                Schema.Wallet.ID + " WHERE t." + Schema.Transaction.DELETED + " = 0 AND " +
                selection
        val args = Array(selectionArgs.size + 1) { index ->
            if (index == 0) currency else selectionArgs[index - 1]
        }
        val cursor = readableDatabase.rawQuery(query, args)
        var noneIsHeldIn = false
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    noneIsHeldIn = cursor.getLong(0) > 0L && cursor.getLong(1) == 0L
                }
            } finally {
                cursor.close()
            }
        }
        return noneIsHeldIn
    }

    private fun walletCurrency(walletId: Long): String? {
        val projection = arrayOf(Schema.Wallet.CURRENCY)
        val selection = Schema.Wallet.ID + " = ?"
        val selectionArgs = arrayOf(walletId.toString())
        val cursor = readableDatabase.query(Schema.Wallet.TABLE, projection, selection, selectionArgs, null, null, null)
        var currency: String? = null
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    currency = cursor.getString(cursor.getColumnIndex(Schema.Wallet.CURRENCY))
                }
            } finally {
                cursor.close()
            }
        }
        return currency
    }

    private fun storedWallet(table: String, idColumn: String, walletColumn: String, id: Long): Long? {
        val projection = arrayOf(walletColumn)
        val selection = idColumn + " = ?"
        val selectionArgs = arrayOf(id.toString())
        val cursor = readableDatabase.query(table, projection, selection, selectionArgs, null, null, null)
        var walletId: Long? = null
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    walletId = cursor.getLong(cursor.getColumnIndex(walletColumn))
                }
            } finally {
                cursor.close()
            }
        }
        return walletId
    }

    private fun checkSavingWalletCurrency(savingId: Long, walletId: Long) {
        val storedWalletId = storedWallet(Schema.Saving.TABLE, Schema.Saving.ID, Schema.Saving.WALLET, savingId)
        if (storedWalletId == null || storedWalletId == walletId) {
            return
        }
        checkStrandedCurrency(storedWalletId, walletId, "t." + Schema.Transaction.SAVING + " = ?", arrayOf(savingId.toString()))
    }

    private fun checkDebtWalletCurrency(debtId: Long, walletId: Long) {
        val storedWalletId = storedWallet(Schema.Debt.TABLE, Schema.Debt.ID, Schema.Debt.WALLET, debtId)
        val debtCategoryId = getSystemCategoryId(Schema.CategoryTag.DEBT)
        val creditCategoryId = getSystemCategoryId(Schema.CategoryTag.CREDIT)
        if (storedWalletId == null || storedWalletId == walletId || debtCategoryId == null || creditCategoryId == null) {
            return
        }
        val selection = "t." + Schema.Transaction.DEBT + " = ? AND t." +
                Schema.Transaction.CATEGORY + " NOT IN (?, ?)"
        val selectionArgs = arrayOf(
            debtId.toString(),
            debtCategoryId.toString(),
            creditCategoryId.toString()
        )
        checkStrandedCurrency(storedWalletId, walletId, selection, selectionArgs)
    }

    open fun deleteBudget(budgetId: Long): Int {
        var where = Schema.BudgetWallet.BUDGET + " = ?"
        var whereArgs = arrayOf(budgetId.toString())
        writableDatabase.delete(Schema.BudgetWallet.TABLE, where, whereArgs)
        where = Schema.BudgetCategory.BUDGET + " = ?"
        whereArgs = arrayOf(budgetId.toString())
        writableDatabase.delete(Schema.BudgetCategory.TABLE, where, whereArgs)
        where = Schema.Budget.ID + " = ?"
        whereArgs = arrayOf(budgetId.toString())
        return writableDatabase.delete(Schema.Budget.TABLE, where, whereArgs)
    }

    open fun getSaving(id: Long, projection: Array<String>?): Cursor? {
        val selection = Schema.Saving.ID + " = ?"
        val selectionArgs = arrayOf(id.toString())
        return getSavings(projection, selection, selectionArgs, null)
    }

    open fun getSavings(
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        val signedMoney = "(((" + Schema.Transaction.DIRECTION + " * -2) + 1) * " +
                Schema.Transaction.MONEY + ")"
        val landed = Schema.Transaction.CONFIRMED + " = 1 AND DATETIME(" +
                Schema.Transaction.DATE + ") <= DATETIME('now', 'localtime')"
        val subQuery = "SELECT " +
                Schema.Saving.ID + " AS " + Contract.Saving.ID + ", " +
                Schema.Saving.DESCRIPTION + " AS " + Contract.Saving.DESCRIPTION + ", " +
                Schema.Saving.ICON + " AS " + Contract.Saving.ICON + ", " +
                Schema.Saving.START_MONEY + " AS " + Contract.Saving.START_MONEY + ", " +
                Schema.Saving.END_MONEY + " AS " + Contract.Saving.END_MONEY + ", " +
                Schema.Saving.WALLET + " AS " + Contract.Saving.WALLET_ID + ", " +
                Schema.Wallet.NAME + " AS " + Contract.Saving.WALLET_NAME + ", " +
                Schema.Wallet.ICON + " AS " + Contract.Saving.WALLET_ICON + ", " +
                Schema.Wallet.CURRENCY + " AS " + Contract.Saving.WALLET_CURRENCY + ", " +
                Schema.Wallet.COUNT_IN_TOTAL + " AS " + Contract.Saving.WALLET_COUNT_IN_TOTAL + ", " +
                Schema.Wallet.ARCHIVED + " AS " + Contract.Saving.WALLET_ARCHIVED + ", " +
                Schema.Wallet.TAG + " AS " + Contract.Saving.WALLET_TAG + ", " +
                Schema.Saving.END_DATE + " AS " + Contract.Saving.END_DATE + ", " +
                Schema.Saving.COMPLETE + " AS " + Contract.Saving.COMPLETE + ", " +
                Schema.Saving.NOTE + " AS " + Contract.Saving.NOTE + ", " +
                Schema.Saving.TAG + " AS " + Contract.Saving.TAG + ", " +
                "_progress AS " + Contract.Saving.PROGRESS +
                " FROM " + Schema.Saving.TABLE +
                " AS s LEFT JOIN " + Schema.Wallet.TABLE + " ON " + Schema.Saving.WALLET + " = " +
                Schema.Wallet.ID + " LEFT JOIN (SELECT " + Schema.Transaction.SAVING + " AS _saving, " +
                " SUM(CASE WHEN " + landed + " THEN " + signedMoney + " ELSE 0 END) AS _progress" +
                " FROM " + Schema.Transaction.TABLE + " AS j LEFT JOIN " + Schema.Category.TABLE +
                " ON " + Schema.Transaction.CATEGORY + " = " + Schema.Category.ID + " WHERE j." +
                Schema.Transaction.DELETED + " = 0 AND (" + Schema.Category.TAG + " = '" +
                Schema.CategoryTag.SAVING_DEPOSIT + "' OR " + Schema.Category.TAG + " = '" +
                Schema.CategoryTag.SAVING_WITHDRAW + "') GROUP BY _saving) ON " +
                Schema.Saving.ID + " = _saving WHERE s." + Schema.Saving.DELETED + " = 0"
        return queryFrom(subQuery, projection, selection, selectionArgs, sortOrder)
    }

    open fun getSavingTransactions(
        savingId: Long,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        var _selection = Schema.Transaction.SAVING + " = ?"
        if (!TextUtils.isEmpty(selection)) {
            _selection += " AND " + selection
        }
        val size = selectionArgs?.size ?: 0
        val _selectionArgs = Array(size + 1) { index ->
            if (index == 0) savingId.toString() else (selectionArgs?.get(index - 1) ?: "")
        }
        return getTransactions(projection, _selection, _selectionArgs, sortOrder)
    }

    open fun insertSaving(contentValues: ContentValues): Long {
        val cv = ContentValues()
        cv.put(Schema.Saving.DESCRIPTION, contentValues.getAsString(Contract.Saving.DESCRIPTION))
        cv.put(Schema.Saving.ICON, contentValues.getAsString(Contract.Saving.ICON))
        cv.put(Schema.Saving.START_MONEY, contentValues.getAsLong(Contract.Saving.START_MONEY))
        cv.put(Schema.Saving.END_MONEY, contentValues.getAsLong(Contract.Saving.END_MONEY))
        cv.put(Schema.Saving.WALLET, contentValues.getAsLong(Contract.Saving.WALLET_ID))
        cv.put(Schema.Saving.END_DATE, contentValues.getAsString(Contract.Saving.END_DATE))
        cv.put(Schema.Saving.COMPLETE, contentValues.getAsBoolean(Contract.Saving.COMPLETE))
        cv.put(Schema.Saving.NOTE, contentValues.getAsString(Contract.Saving.NOTE))
        cv.put(Schema.Saving.TAG, contentValues.getAsString(Contract.Saving.TAG))
        cv.put(Schema.Saving.UUID, UUID.randomUUID().toString())
        cv.put(Schema.Saving.LAST_EDIT, System.currentTimeMillis())
        cv.put(Schema.Saving.DELETED, false)
        return writableDatabase.insert(Schema.Saving.TABLE, null, cv)
    }

    open fun updateSaving(savingId: Long, contentValues: ContentValues): Int {
        if (contentValues.containsKey(Contract.Saving.WALLET_ID)) {
            val walletId = contentValues.getAsLong(Contract.Saving.WALLET_ID)
            if (walletId != null) {
                checkSavingWalletCurrency(savingId, walletId)
            }
        }
        val cv = ContentValues()
        if (contentValues.containsKey(Contract.Saving.COMPLETE)) {
            cv.put(Schema.Saving.COMPLETE, contentValues.getAsBoolean(Contract.Saving.COMPLETE))
        }
        if (contentValues.containsKey(Contract.Saving.DESCRIPTION)) {
            cv.put(Schema.Saving.DESCRIPTION, contentValues.getAsString(Contract.Saving.DESCRIPTION))
        }
        if (contentValues.containsKey(Contract.Saving.ICON)) {
            cv.put(Schema.Saving.ICON, contentValues.getAsString(Contract.Saving.ICON))
        }
        if (contentValues.containsKey(Contract.Saving.START_MONEY)) {
            cv.put(Schema.Saving.START_MONEY, contentValues.getAsLong(Contract.Saving.START_MONEY))
        }
        if (contentValues.containsKey(Contract.Saving.END_MONEY)) {
            cv.put(Schema.Saving.END_MONEY, contentValues.getAsLong(Contract.Saving.END_MONEY))
        }
        if (contentValues.containsKey(Contract.Saving.WALLET_ID)) {
            cv.put(Schema.Saving.WALLET, contentValues.getAsLong(Contract.Saving.WALLET_ID))
        }
        if (contentValues.containsKey(Contract.Saving.END_DATE)) {
            cv.put(Schema.Saving.END_DATE, contentValues.getAsString(Contract.Saving.END_DATE))
        }
        if (contentValues.containsKey(Contract.Saving.NOTE)) {
            cv.put(Schema.Saving.NOTE, contentValues.getAsString(Contract.Saving.NOTE))
        }
        if (contentValues.containsKey(Contract.Saving.TAG)) {
            cv.put(Schema.Saving.TAG, contentValues.getAsString(Contract.Saving.TAG))
        }
        cv.put(Schema.Saving.LAST_EDIT, System.currentTimeMillis())
        val where = Schema.Saving.ID + " = ?"
        val whereArgs = arrayOf(savingId.toString())
        return writableDatabase.update(Schema.Saving.TABLE, cv, where, whereArgs)
    }

    open fun deleteSaving(savingId: Long): Int {
        val cursor = getSavingTransactions(savingId, arrayOf(Contract.Transaction.ID), null, null, null)
        if (cursor != null) {
            deleteTransactionItems(cursor)
        }
        val where = Schema.Saving.ID + " = ?"
        val whereArgs = arrayOf(savingId.toString())
        return writableDatabase.delete(Schema.Saving.TABLE, where, whereArgs)
    }

    open fun getEvent(id: Long, projection: Array<String>?): Cursor? {
        val selection = Schema.Event.ID + " = ?"
        val selectionArgs = arrayOf(id.toString())
        return getEvents(projection, selection, selectionArgs, null)
    }

    open fun getEvents(
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        val subQuery = "SELECT " +
                Schema.Event.ID + " AS " + Contract.Event.ID + ", " +
                Schema.Event.NAME + " AS " + Contract.Event.NAME + ", " +
                Schema.Event.ICON + " AS " + Contract.Event.ICON + ", " +
                Schema.Event.NOTE + " AS " + Contract.Event.NOTE + ", " +
                Schema.Event.START_DATE + " AS " + Contract.Event.START_DATE + ", " +
                Schema.Event.END_DATE + " AS " + Contract.Event.END_DATE + ", " +
                Schema.Event.TAG + " AS " + Contract.Event.TAG + ", " +
                "_progress AS " + Contract.Event.PROGRESS + " FROM " + Schema.Event.TABLE + " AS e" +
                " LEFT JOIN (SELECT *, GROUP_CONCAT(wallet_currency || ' ' || _currency_money) AS " +
                "_progress FROM (SELECT * , SUM (((" + Schema.Transaction.DIRECTION + " * 2) - 1) * " +
                Schema.Transaction.MONEY + ") AS _currency_money FROM " + Schema.Transaction.TABLE +
                " AS t JOIN " + Schema.Wallet.TABLE + " AS w ON " + Schema.Transaction.WALLET + " = " +
                Schema.Wallet.ID + " WHERE " + Schema.Transaction.EVENT + " IS NOT NULL AND " +
                "DATETIME(" + Schema.Transaction.DATE + ") <= DATETIME('now', 'localtime')" +
                " AND " + Schema.Transaction.CONFIRMED + " = 1 AND t." + Schema.Transaction.DELETED +
                " = 0 AND w." + Schema.Wallet.DELETED + " = 0 GROUP BY " + Schema.Transaction.EVENT +
                ", wallet_currency) GROUP BY " + Schema.Transaction.EVENT + ") ON " + Schema.Event.ID +
                " = " + Schema.Transaction.EVENT + " WHERE e." + Schema.Event.DELETED + " = 0"
        return queryFrom(subQuery, projection, selection, selectionArgs, sortOrder)
    }

    open fun getEventTransactions(
        eventId: Long,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        var _selection = Schema.Transaction.EVENT + " = ?"
        if (!TextUtils.isEmpty(selection)) {
            _selection += " AND " + selection
        }
        val size = selectionArgs?.size ?: 0
        val _selectionArgs = Array(size + 1) { index ->
            if (index == 0) eventId.toString() else (selectionArgs?.get(index - 1) ?: "")
        }
        return getTransactions(projection, _selection, _selectionArgs, sortOrder)
    }

    open fun insertEvent(contentValues: ContentValues): Long {
        val cv = ContentValues()
        cv.put(Schema.Event.NAME, contentValues.getAsString(Contract.Event.NAME))
        cv.put(Schema.Event.ICON, contentValues.getAsString(Contract.Event.ICON))
        cv.put(Schema.Event.NOTE, contentValues.getAsString(Contract.Event.NOTE))
        cv.put(Schema.Event.START_DATE, contentValues.getAsString(Contract.Event.START_DATE))
        cv.put(Schema.Event.END_DATE, contentValues.getAsString(Contract.Event.END_DATE))
        cv.put(Schema.Event.TAG, contentValues.getAsString(Contract.Event.TAG))
        cv.put(Schema.Event.UUID, UUID.randomUUID().toString())
        cv.put(Schema.Event.LAST_EDIT, System.currentTimeMillis())
        cv.put(Schema.Event.DELETED, false)
        return writableDatabase.insert(Schema.Event.TABLE, null, cv)
    }

    open fun updateEvent(eventId: Long, contentValues: ContentValues): Int {
        val cv = ContentValues()
        cv.put(Schema.Event.NAME, contentValues.getAsString(Contract.Event.NAME))
        cv.put(Schema.Event.ICON, contentValues.getAsString(Contract.Event.ICON))
        cv.put(Schema.Event.NOTE, contentValues.getAsString(Contract.Event.NOTE))
        cv.put(Schema.Event.START_DATE, contentValues.getAsString(Contract.Event.START_DATE))
        cv.put(Schema.Event.END_DATE, contentValues.getAsString(Contract.Event.END_DATE))
        if (contentValues.containsKey(Contract.Event.TAG)) {
            cv.put(Schema.Event.TAG, contentValues.getAsString(Contract.Event.TAG))
        }
        cv.put(Schema.Event.LAST_EDIT, System.currentTimeMillis())
        val where = Schema.Event.ID + " = ?"
        val whereArgs = arrayOf(eventId.toString())
        return writableDatabase.update(Schema.Event.TABLE, cv, where, whereArgs)
    }

    open fun deleteEvent(eventId: Long): Int {
        var cv = ContentValues()
        cv.putNull(Schema.Transaction.EVENT)
        cv.put(Schema.Transaction.LAST_EDIT, System.currentTimeMillis())
        var where = Schema.Transaction.EVENT + " = ?"
        var whereArgs = arrayOf(eventId.toString())
        writableDatabase.update(Schema.Transaction.TABLE, cv, where, whereArgs)

        cv = ContentValues()
        cv.putNull(Schema.TransactionModel.EVENT)
        cv.put(Schema.TransactionModel.LAST_EDIT, System.currentTimeMillis())
        where = Schema.TransactionModel.EVENT + " = ?"
        whereArgs = arrayOf(eventId.toString())
        writableDatabase.update(Schema.TransactionModel.TABLE, cv, where, whereArgs)

        cv = ContentValues()
        cv.putNull(Schema.Transfer.EVENT)
        cv.put(Schema.Transfer.LAST_EDIT, System.currentTimeMillis())
        where = Schema.Transfer.EVENT + " = ?"
        whereArgs = arrayOf(eventId.toString())
        writableDatabase.update(Schema.Transfer.TABLE, cv, where, whereArgs)

        cv = ContentValues()
        cv.putNull(Schema.TransferModel.EVENT)
        cv.put(Schema.TransferModel.LAST_EDIT, System.currentTimeMillis())
        where = Schema.TransferModel.EVENT + " = ?"
        whereArgs = arrayOf(eventId.toString())
        writableDatabase.update(Schema.TransferModel.TABLE, cv, where, whereArgs)

        cv = ContentValues()
        cv.putNull(Schema.RecurrentTransaction.EVENT)
        cv.put(Schema.RecurrentTransaction.LAST_EDIT, System.currentTimeMillis())
        where = Schema.RecurrentTransaction.EVENT + " = ?"
        whereArgs = arrayOf(eventId.toString())
        writableDatabase.update(Schema.RecurrentTransaction.TABLE, cv, where, whereArgs)

        cv = ContentValues()
        cv.putNull(Schema.RecurrentTransfer.EVENT)
        cv.put(Schema.RecurrentTransfer.LAST_EDIT, System.currentTimeMillis())
        where = Schema.RecurrentTransfer.EVENT + " = ?"
        whereArgs = arrayOf(eventId.toString())
        writableDatabase.update(Schema.RecurrentTransfer.TABLE, cv, where, whereArgs)

        where = Schema.EventPeople.EVENT + " = ?"
        whereArgs = arrayOf(eventId.toString())
        writableDatabase.delete(Schema.EventPeople.TABLE, where, whereArgs)

        where = Schema.Event.ID + " = ?"
        whereArgs = arrayOf(eventId.toString())
        return writableDatabase.delete(Schema.Event.TABLE, where, whereArgs)
    }


    open fun getRecurrentTransaction(transactionId: Long, projection: Array<String>?): Cursor? {
        val selection = Schema.RecurrentTransaction.ID + " = ?"
        val selectionArgs = arrayOf(transactionId.toString())
        return getRecurrentTransactions(projection, selection, selectionArgs, null)
    }

    open fun getRecurrentTransactions(
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        val subQuery = "SELECT " +
                "rt." + Schema.RecurrentTransaction.ID + " AS " + Contract.RecurrentTransaction.ID + ", " +
                "rt." + Schema.RecurrentTransaction.MONEY + " AS " + Contract.RecurrentTransaction.MONEY + ", " +
                "rt." + Schema.RecurrentTransaction.DESCRIPTION + " AS " + Contract.RecurrentTransaction.DESCRIPTION + ", " +
                "rt." + Schema.RecurrentTransaction.CATEGORY + " AS " + Contract.RecurrentTransaction.CATEGORY_ID + ", " +
                "c." + Schema.Category.NAME + " AS " + Contract.RecurrentTransaction.CATEGORY_NAME + ", " +
                "c." + Schema.Category.ICON + " AS " + Contract.RecurrentTransaction.CATEGORY_ICON + ", " +
                "c." + Schema.Category.TYPE + " AS " + Contract.RecurrentTransaction.CATEGORY_TYPE + ", " +
                "c." + Schema.Category.SHOW_REPORT + " AS " + Contract.RecurrentTransaction.CATEGORY_SHOW_REPORT + ", " +
                "c." + Schema.Category.TAG + " AS " + Contract.RecurrentTransaction.CATEGORY_TAG + ", " +
                "rt." + Schema.RecurrentTransaction.DIRECTION + " AS " + Contract.RecurrentTransaction.DIRECTION + ", " +
                "rt." + Schema.RecurrentTransaction.WALLET + " AS " + Contract.RecurrentTransaction.WALLET_ID + ", " +
                "w." + Schema.Wallet.NAME + " AS " + Contract.RecurrentTransaction.WALLET_NAME + ", " +
                "w." + Schema.Wallet.ICON + " AS " + Contract.RecurrentTransaction.WALLET_ICON + ", " +
                "w." + Schema.Wallet.CURRENCY + " AS " + Contract.RecurrentTransaction.WALLET_CURRENCY + ", " +
                "w." + Schema.Wallet.COUNT_IN_TOTAL + " AS " + Contract.RecurrentTransaction.WALLET_COUNT_IN_TOTAL + ", " +
                "w." + Schema.Wallet.ARCHIVED + " AS " + Contract.RecurrentTransaction.WALLET_ARCHIVED + ", " +
                "w." + Schema.Wallet.TAG + " AS " + Contract.RecurrentTransaction.WALLET_TAG + ", " +
                "rt." + Schema.RecurrentTransaction.PLACE + " AS " + Contract.RecurrentTransaction.PLACE_ID + ", " +
                "p." + Schema.Place.NAME + " AS " + Contract.RecurrentTransaction.PLACE_NAME + ", " +
                "p." + Schema.Place.ICON + " AS " + Contract.RecurrentTransaction.PLACE_ICON + ", " +
                "p." + Schema.Place.ADDRESS + " AS " + Contract.RecurrentTransaction.PLACE_ADDRESS + ", " +
                "p." + Schema.Place.LATITUDE + " AS " + Contract.RecurrentTransaction.PLACE_LATITUDE + ", " +
                "p." + Schema.Place.LONGITUDE + " AS " + Contract.RecurrentTransaction.PLACE_LONGITUDE + ", " +
                "p." + Schema.Place.TAG + " AS " + Contract.RecurrentTransaction.PLACE_TAG + ", " +
                "rt." + Schema.RecurrentTransaction.NOTE + " AS " + Contract.RecurrentTransaction.NOTE + ", " +
                "rt." + Schema.RecurrentTransaction.EVENT + " AS " + Contract.RecurrentTransaction.EVENT_ID + ", " +
                "e." + Schema.Event.NAME + " AS " + Contract.RecurrentTransaction.EVENT_NAME + ", " +
                "e." + Schema.Event.ICON + " AS " + Contract.RecurrentTransaction.EVENT_ICON + ", " +
                "e." + Schema.Event.NOTE + " AS " + Contract.RecurrentTransaction.EVENT_NOTE + ", " +
                "e." + Schema.Event.START_DATE + " AS " + Contract.RecurrentTransaction.EVENT_START_DATE + ", " +
                "e." + Schema.Event.END_DATE + " AS " + Contract.RecurrentTransaction.EVENT_END_DATE + ", " +
                "e." + Schema.Event.TAG + " AS " + Contract.RecurrentTransaction.EVENT_TAG + ", " +
                "rt." + Schema.RecurrentTransaction.CONFIRMED + " AS " + Contract.RecurrentTransaction.CONFIRMED + ", " +
                "rt." + Schema.RecurrentTransaction.COUNT_IN_TOTAL + " AS " + Contract.RecurrentTransaction.COUNT_IN_TOTAL + ", " +
                "rt." + Schema.RecurrentTransaction.START_DATE + " AS " + Contract.RecurrentTransaction.START_DATE + ", " +
                "rt." + Schema.RecurrentTransaction.LAST_OCCURRENCE + " AS " + Contract.RecurrentTransaction.LAST_OCCURRENCE + ", " +
                "rt." + Schema.RecurrentTransaction.NEXT_OCCURRENCE + " AS " + Contract.RecurrentTransaction.NEXT_OCCURRENCE + ", " +
                "rt." + Schema.RecurrentTransaction.RULE + " AS " + Contract.RecurrentTransaction.RULE + ", " +
                "rt." + Schema.RecurrentTransaction.TAG + " AS " + Contract.RecurrentTransaction.TAG + " " +
                "FROM " + Schema.RecurrentTransaction.TABLE + " AS rt JOIN " + Schema.Wallet.TABLE +
                " AS w ON rt." + Schema.RecurrentTransaction.WALLET + " = w." + Schema.Wallet.ID + " AND w." +
                Schema.Wallet.DELETED + " = 0 JOIN " + Schema.Category.TABLE + " AS c ON rt." +
                Schema.RecurrentTransaction.CATEGORY + " = c." + Schema.Category.ID + " AND c." +
                Schema.Category.DELETED + " = 0 LEFT JOIN " + Schema.Place.TABLE + " AS p ON rt." +
                Schema.RecurrentTransaction.PLACE + " = p." + Schema.Place.ID + " AND p." +
                Schema.Place.ID + " = 0 LEFT JOIN " + Schema.Event.TABLE + " AS e ON rt." +
                Schema.RecurrentTransaction.EVENT + " = e." + Schema.Event.ID + " AND e." +
                Schema.Event.DELETED + " = 0 WHERE rt." + Schema.RecurrentTransaction.DELETED + " = 0"
        return queryFrom(subQuery, projection, selection, selectionArgs, sortOrder)
    }

    open fun insertRecurrentTransaction(contentValues: ContentValues): Long {
        val recurrenceRule: RecurrenceRule
        try {
            recurrenceRule = RecurrenceRule(contentValues.getAsString(Contract.RecurrentTransaction.RULE))
        } catch (e: InvalidRecurrenceRuleException) {
            throw SQLiteDataException(Contract.ErrorCode.INVALID_RECURRENCE_RULE, e.message)
        }
        val uuid = UUID.randomUUID().toString()
        val cv = ContentValues()
        cv.put(Schema.RecurrentTransaction.MONEY, contentValues.getAsLong(Contract.RecurrentTransaction.MONEY))
        cv.put(Schema.RecurrentTransaction.DESCRIPTION, contentValues.getAsString(Contract.RecurrentTransaction.DESCRIPTION))
        cv.put(Schema.RecurrentTransaction.CATEGORY, contentValues.getAsLong(Contract.RecurrentTransaction.CATEGORY_ID))
        cv.put(Schema.RecurrentTransaction.DIRECTION, contentValues.getAsInteger(Contract.RecurrentTransaction.DIRECTION))
        cv.put(Schema.RecurrentTransaction.WALLET, contentValues.getAsLong(Contract.RecurrentTransaction.WALLET_ID))
        cv.put(Schema.RecurrentTransaction.PLACE, contentValues.getAsLong(Contract.RecurrentTransaction.PLACE_ID))
        cv.put(Schema.RecurrentTransaction.EVENT, contentValues.getAsLong(Contract.RecurrentTransaction.EVENT_ID))
        cv.put(Schema.RecurrentTransaction.NOTE, contentValues.getAsString(Contract.RecurrentTransaction.NOTE))
        cv.put(Schema.RecurrentTransaction.CONFIRMED, contentValues.getAsBoolean(Contract.RecurrentTransaction.CONFIRMED))
        cv.put(Schema.RecurrentTransaction.COUNT_IN_TOTAL, contentValues.getAsBoolean(Contract.RecurrentTransaction.COUNT_IN_TOTAL))
        cv.put(Schema.RecurrentTransaction.START_DATE, contentValues.getAsString(Contract.RecurrentTransaction.START_DATE))
        cv.put(Schema.RecurrentTransaction.LAST_OCCURRENCE, contentValues.getAsString(Contract.RecurrentTransaction.START_DATE))
        cv.put(Schema.RecurrentTransaction.RULE, contentValues.getAsString(Contract.RecurrentTransaction.RULE))
        cv.put(Schema.RecurrentTransaction.TAG, contentValues.getAsString(Contract.RecurrentTransaction.TAG))
        cv.put(Schema.RecurrentTransaction.UUID, uuid)
        cv.put(Schema.RecurrentTransaction.LAST_EDIT, System.currentTimeMillis())
        cv.put(Schema.RecurrentTransaction.DELETED, false)
        val id = writableDatabase.insert(Schema.RecurrentTransaction.TABLE, null, cv)
        if (id > 0L) {
            val startDateString = contentValues.getAsString(Contract.RecurrentTransaction.START_DATE)
            val startDate = if (startDateString != null) DateUtils.getDateFromSQLDateString(startDateString) else null
            if (startDate != null) {
                val currentDateTime = DateUtils.getFixedDateTime(Date())
                val startDateTime = DateUtils.getFixedDateTime(startDate)
                var lastOccurrence = DateUtils.getFixedDateTime(startDate)
                var nextOccurrence: DateTime? = null
                val iterator = recurrenceRule.iterator(startDateTime)
                while (iterator.hasNext()) {
                    val nextInstance = iterator.nextDateTime()
                    if (!nextInstance.after(currentDateTime)) {
                        val transactionDate = DateUtils.getFixedDate(nextInstance)
                        val cvt = ContentValues()
                        cvt.put(Schema.Transaction.MONEY, contentValues.getAsLong(Contract.RecurrentTransaction.MONEY))
                        cvt.put(Schema.Transaction.DATE, DateUtils.getSQLDateTimeString(transactionDate))
                        cvt.put(Schema.Transaction.DESCRIPTION, contentValues.getAsString(Contract.RecurrentTransaction.DESCRIPTION))
                        cvt.put(Schema.Transaction.CATEGORY, contentValues.getAsLong(Contract.RecurrentTransaction.CATEGORY_ID))
                        cvt.put(Schema.Transaction.DIRECTION, contentValues.getAsInteger(Contract.RecurrentTransaction.DIRECTION))
                        cvt.put(Schema.Transaction.TYPE, Contract.TransactionType.STANDARD)
                        cvt.put(Schema.Transaction.WALLET, contentValues.getAsLong(Contract.RecurrentTransaction.WALLET_ID))
                        cvt.put(Schema.Transaction.PLACE, contentValues.getAsLong(Contract.RecurrentTransaction.PLACE_ID))
                        cvt.put(Schema.Transaction.NOTE, contentValues.getAsString(Contract.RecurrentTransaction.NOTE))
                        cvt.put(Schema.Transaction.EVENT, contentValues.getAsLong(Contract.RecurrentTransaction.EVENT_ID))
                        cvt.put(Schema.Transaction.RECURRENCE, id)
                        cvt.put(Schema.Transaction.CONFIRMED, contentValues.getAsBoolean(Contract.RecurrentTransaction.CONFIRMED))
                        cvt.put(Schema.Transaction.COUNT_IN_TOTAL, contentValues.getAsBoolean(Contract.RecurrentTransaction.COUNT_IN_TOTAL))
                        cvt.put(Schema.Transaction.UUID, getRecurrentItemUUID(uuid, transactionDate))
                        cvt.put(Schema.Transaction.LAST_EDIT, System.currentTimeMillis())
                        cvt.put(Schema.Transaction.DELETED, false)
                        writableDatabase.insert(Schema.Transaction.TABLE, null, cvt)
                        lastOccurrence = nextInstance
                    } else {
                        nextOccurrence = nextInstance
                        break
                    }
                }
                val cvu = ContentValues()
                cvu.put(Schema.RecurrentTransaction.LAST_OCCURRENCE, DateUtils.getSQLDateString(DateUtils.getFixedDate(lastOccurrence)))
                val nextOccDate = nextOccurrence?.let { DateUtils.getFixedDate(it) }
                cvu.put(Schema.RecurrentTransaction.NEXT_OCCURRENCE, if (nextOccDate != null) DateUtils.getSQLDateString(nextOccDate) else null)
                val selection = Schema.RecurrentTransaction.ID + " = ?"
                val selectionArgs = arrayOf(id.toString())
                writableDatabase.update(Schema.RecurrentTransaction.TABLE, cvu, selection, selectionArgs)
            }
        }
        return id
    }

    open fun updateRecurrentTransaction(transactionId: Long, contentValues: ContentValues): Int {
        if (contentValues.containsKey(Contract.RecurrentTransaction.LAST_OCCURRENCE) && contentValues.containsKey(Contract.RecurrentTransaction.NEXT_OCCURRENCE)) {
            val cv = ContentValues()
            cv.put(Schema.RecurrentTransaction.LAST_OCCURRENCE, contentValues.getAsString(Contract.RecurrentTransaction.LAST_OCCURRENCE))
            cv.put(Schema.RecurrentTransaction.NEXT_OCCURRENCE, contentValues.getAsString(Contract.RecurrentTransaction.NEXT_OCCURRENCE))
            cv.put(Schema.RecurrentTransaction.LAST_EDIT, System.currentTimeMillis())
            val where = Schema.RecurrentTransaction.ID + " = ?"
            val selectionArgs = arrayOf(transactionId.toString())
            return writableDatabase.update(Schema.RecurrentTransaction.TABLE, cv, where, selectionArgs)
        }
        val rule = contentValues.getAsString(Contract.RecurrentTransaction.RULE)
        try {
            RecurrenceRule(rule)
        } catch (e: InvalidRecurrenceRuleException) {
            throw SQLiteDataException(Contract.ErrorCode.INVALID_RECURRENCE_RULE, e.message)
        }
        val startDate = contentValues.getAsString(Contract.RecurrentTransaction.START_DATE)
        val cv = ContentValues()
        cv.put(Schema.RecurrentTransaction.MONEY, contentValues.getAsLong(Contract.RecurrentTransaction.MONEY))
        cv.put(Schema.RecurrentTransaction.DESCRIPTION, contentValues.getAsString(Contract.RecurrentTransaction.DESCRIPTION))
        cv.put(Schema.RecurrentTransaction.CATEGORY, contentValues.getAsLong(Contract.RecurrentTransaction.CATEGORY_ID))
        cv.put(Schema.RecurrentTransaction.DIRECTION, contentValues.getAsInteger(Contract.RecurrentTransaction.DIRECTION))
        cv.put(Schema.RecurrentTransaction.WALLET, contentValues.getAsLong(Contract.RecurrentTransaction.WALLET_ID))
        cv.put(Schema.RecurrentTransaction.PLACE, contentValues.getAsLong(Contract.RecurrentTransaction.PLACE_ID))
        cv.put(Schema.RecurrentTransaction.EVENT, contentValues.getAsLong(Contract.RecurrentTransaction.EVENT_ID))
        cv.put(Schema.RecurrentTransaction.NOTE, contentValues.getAsString(Contract.RecurrentTransaction.NOTE))
        cv.put(Schema.RecurrentTransaction.CONFIRMED, contentValues.getAsBoolean(Contract.RecurrentTransaction.CONFIRMED))
        cv.put(Schema.RecurrentTransaction.COUNT_IN_TOTAL, contentValues.getAsBoolean(Contract.RecurrentTransaction.COUNT_IN_TOTAL))
        cv.put(Schema.RecurrentTransaction.START_DATE, startDate)
        cv.put(Schema.RecurrentTransaction.RULE, rule)
        cv.put(
            Schema.RecurrentTransaction.NEXT_OCCURRENCE,
            resumedNextOccurrence(
                Schema.RecurrentTransaction.TABLE,
                Schema.RecurrentTransaction.ID,
                Schema.RecurrentTransaction.RULE,
                Schema.RecurrentTransaction.START_DATE,
                Schema.RecurrentTransaction.NEXT_OCCURRENCE,
                transactionId,
                rule ?: "",
                startDate ?: ""
            )
        )
        cv.put(Schema.RecurrentTransaction.LAST_EDIT, System.currentTimeMillis())
        val where = Schema.RecurrentTransaction.ID + " = ?"
        val selectionArgs = arrayOf(transactionId.toString())
        return writableDatabase.update(Schema.RecurrentTransaction.TABLE, cv, where, selectionArgs)
    }

    open fun deleteRecurrentTransaction(transactionId: Long): Int {
        val contentValues = ContentValues()
        contentValues.putNull(Schema.Transaction.RECURRENCE)
        contentValues.put(Schema.Transaction.LAST_EDIT, System.currentTimeMillis())
        var selection = Schema.Transaction.RECURRENCE + " = ?"
        var selectionArgs = arrayOf(transactionId.toString())
        writableDatabase.update(Schema.Transaction.TABLE, contentValues, selection, selectionArgs)
        selection = Schema.RecurrentTransaction.ID + " = ?"
        selectionArgs = arrayOf(transactionId.toString())
        return writableDatabase.delete(Schema.RecurrentTransaction.TABLE, selection, selectionArgs)
    }

    open fun getRecurrentTransfer(transferId: Long, projection: Array<String>?): Cursor? {
        val selection = Schema.RecurrentTransfer.ID + " = ?"
        val selectionArgs = arrayOf(transferId.toString())
        return getRecurrentTransfers(projection, selection, selectionArgs, null)
    }

    open fun getRecurrentTransfers(
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        val subQuery = "SELECT " +
                "rt." + Schema.RecurrentTransfer.ID + " AS " + Contract.RecurrentTransfer.ID + ", " +
                "rt." + Schema.RecurrentTransfer.DESCRIPTION + " AS " + Contract.RecurrentTransfer.DESCRIPTION + ", " +
                "rt." + Schema.RecurrentTransfer.WALLET_FROM + " AS " + Contract.RecurrentTransfer.WALLET_FROM_ID + ", " +
                "wf." + Schema.Wallet.NAME + " AS " + Contract.RecurrentTransfer.WALLET_FROM_NAME + ", " +
                "wf." + Schema.Wallet.ICON + " AS " + Contract.RecurrentTransfer.WALLET_FROM_ICON + ", " +
                "wf." + Schema.Wallet.CURRENCY + " AS " + Contract.RecurrentTransfer.WALLET_FROM_CURRENCY + ", " +
                "wf." + Schema.Wallet.COUNT_IN_TOTAL + " AS " + Contract.RecurrentTransfer.WALLET_FROM_COUNT_IN_TOTAL + ", " +
                "wf." + Schema.Wallet.ARCHIVED + " AS " + Contract.RecurrentTransfer.WALLET_FROM_ARCHIVED + ", " +
                "wf." + Schema.Wallet.TAG + " AS " + Contract.RecurrentTransfer.WALLET_FROM_TAG + ", " +
                "rt." + Schema.RecurrentTransfer.WALLET_TO + " AS " + Contract.RecurrentTransfer.WALLET_TO_ID + ", " +
                "wt." + Schema.Wallet.NAME + " AS " + Contract.RecurrentTransfer.WALLET_TO_NAME + ", " +
                "wt." + Schema.Wallet.ICON + " AS " + Contract.RecurrentTransfer.WALLET_TO_ICON + ", " +
                "wt." + Schema.Wallet.CURRENCY + " AS " + Contract.RecurrentTransfer.WALLET_TO_CURRENCY + ", " +
                "wt." + Schema.Wallet.COUNT_IN_TOTAL + " AS " + Contract.RecurrentTransfer.WALLET_TO_COUNT_IN_TOTAL + ", " +
                "wt." + Schema.Wallet.ARCHIVED + " AS " + Contract.RecurrentTransfer.WALLET_TO_ARCHIVED + ", " +
                "wt." + Schema.Wallet.TAG + " AS " + Contract.RecurrentTransfer.WALLET_TO_TAG + ", " +
                "rt." + Schema.RecurrentTransfer.MONEY_FROM + " AS " + Contract.RecurrentTransfer.MONEY_FROM + ", " +
                "rt." + Schema.RecurrentTransfer.MONEY_TO + " AS " + Contract.RecurrentTransfer.MONEY_TO + ", " +
                "rt." + Schema.RecurrentTransfer.MONEY_TAX + " AS " + Contract.RecurrentTransfer.MONEY_TAX + ", " +
                "rt." + Schema.RecurrentTransfer.NOTE + " AS " + Contract.RecurrentTransfer.NOTE + ", " +
                "rt." + Schema.RecurrentTransfer.EVENT + " AS " + Contract.RecurrentTransfer.EVENT_ID + ", " +
                "e." + Schema.Event.NAME + " AS " + Contract.RecurrentTransfer.EVENT_NAME + ", " +
                "e." + Schema.Event.ICON + " AS " + Contract.RecurrentTransfer.EVENT_ICON + ", " +
                "e." + Schema.Event.NOTE + " AS " + Contract.RecurrentTransfer.EVENT_NOTE + ", " +
                "e." + Schema.Event.START_DATE + " AS " + Contract.RecurrentTransfer.EVENT_START_DATE + ", " +
                "e." + Schema.Event.END_DATE + " AS " + Contract.RecurrentTransfer.EVENT_END_DATE + ", " +
                "e." + Schema.Event.TAG + " AS " + Contract.RecurrentTransfer.EVENT_TAG + ", " +
                "rt." + Schema.RecurrentTransfer.PLACE + " AS " + Contract.RecurrentTransfer.PLACE_ID + ", " +
                "p." + Schema.Place.NAME + " AS " + Contract.RecurrentTransfer.PLACE_NAME + ", " +
                "p." + Schema.Place.ICON + " AS " + Contract.RecurrentTransfer.PLACE_ICON + ", " +
                "p." + Schema.Place.ADDRESS + " AS " + Contract.RecurrentTransfer.PLACE_ADDRESS + ", " +
                "p." + Schema.Place.LATITUDE + " AS " + Contract.RecurrentTransfer.PLACE_LATITUDE + ", " +
                "p." + Schema.Place.LONGITUDE + " AS " + Contract.RecurrentTransfer.PLACE_LONGITUDE + ", " +
                "p." + Schema.Place.TAG + " AS " + Contract.RecurrentTransfer.PLACE_TAG + ", " +
                "rt." + Schema.RecurrentTransfer.CONFIRMED + " AS " + Contract.RecurrentTransfer.CONFIRMED + ", " +
                "rt." + Schema.RecurrentTransfer.COUNT_IN_TOTAL + " AS " + Contract.RecurrentTransfer.COUNT_IN_TOTAL + ", " +
                "rt." + Schema.RecurrentTransfer.START_DATE + " AS " + Contract.RecurrentTransfer.START_DATE + ", " +
                "rt." + Schema.RecurrentTransfer.LAST_OCCURRENCE + " AS " + Contract.RecurrentTransfer.LAST_OCCURRENCE + ", " +
                "rt." + Schema.RecurrentTransfer.NEXT_OCCURRENCE + " AS " + Contract.RecurrentTransfer.NEXT_OCCURRENCE + ", " +
                "rt." + Schema.RecurrentTransfer.RULE + " AS " + Contract.RecurrentTransfer.RULE + ", " +
                "rt." + Schema.RecurrentTransfer.TAG + " AS " + Contract.RecurrentTransfer.TAG + " " +
                "FROM " + Schema.RecurrentTransfer.TABLE + " AS rt JOIN " + Schema.Wallet.TABLE + " AS " +
                "wf ON rt." + Schema.RecurrentTransfer.WALLET_FROM + " = wf." + Schema.Wallet.ID +
                " AND wf." + Schema.Wallet.DELETED + " = 0 JOIN " + Schema.Wallet.TABLE + " AS " +
                "wt ON rt." + Schema.RecurrentTransfer.WALLET_TO + " = wt." + Schema.Wallet.ID +
                " AND wt." + Schema.Wallet.DELETED + " = 0 LEFT JOIN " + Schema.Event.TABLE +
                " AS e ON rt." + Schema.RecurrentTransfer.EVENT + " = e." + Schema.Event.ID + " AND e." +
                Schema.Event.DELETED + " = 0 LEFT JOIN " + Schema.Place.TABLE + " AS p ON rt." +
                Schema.RecurrentTransfer.PLACE + " = p." + Schema.Place.ID + " AND p." +
                Schema.Place.DELETED + " = 0 WHERE rt." + Schema.RecurrentTransfer.DELETED + " = 0"
        return queryFrom(subQuery, projection, selection, selectionArgs, sortOrder)
    }

    open fun insertRecurrentTransfer(contentValues: ContentValues): Long {
        val recurrenceRule: RecurrenceRule
        try {
            recurrenceRule = RecurrenceRule(contentValues.getAsString(Contract.RecurrentTransfer.RULE))
        } catch (e: InvalidRecurrenceRuleException) {
            throw SQLiteDataException(Contract.ErrorCode.INVALID_RECURRENCE_RULE, e.message)
        }
        val uuid = UUID.randomUUID().toString()
        val cv = ContentValues()
        cv.put(Schema.RecurrentTransfer.DESCRIPTION, contentValues.getAsString(Contract.RecurrentTransfer.DESCRIPTION))
        cv.put(Schema.RecurrentTransfer.WALLET_FROM, contentValues.getAsLong(Contract.RecurrentTransfer.WALLET_FROM_ID))
        cv.put(Schema.RecurrentTransfer.WALLET_TO, contentValues.getAsLong(Contract.RecurrentTransfer.WALLET_TO_ID))
        cv.put(Schema.RecurrentTransfer.MONEY_FROM, contentValues.getAsLong(Contract.RecurrentTransfer.MONEY_FROM))
        cv.put(Schema.RecurrentTransfer.MONEY_TO, contentValues.getAsLong(Contract.RecurrentTransfer.MONEY_TO))
        cv.put(Schema.RecurrentTransfer.MONEY_TAX, contentValues.getAsLong(Contract.RecurrentTransfer.MONEY_TAX))
        cv.put(Schema.RecurrentTransfer.NOTE, contentValues.getAsString(Contract.RecurrentTransfer.NOTE))
        cv.put(Schema.RecurrentTransfer.EVENT, contentValues.getAsLong(Contract.RecurrentTransfer.EVENT_ID))
        cv.put(Schema.RecurrentTransfer.PLACE, contentValues.getAsLong(Contract.RecurrentTransfer.PLACE_ID))
        cv.put(Schema.RecurrentTransfer.CONFIRMED, contentValues.getAsBoolean(Contract.RecurrentTransfer.CONFIRMED))
        cv.put(Schema.RecurrentTransfer.COUNT_IN_TOTAL, contentValues.getAsBoolean(Contract.RecurrentTransfer.COUNT_IN_TOTAL))
        cv.put(Schema.RecurrentTransfer.START_DATE, contentValues.getAsString(Contract.RecurrentTransfer.START_DATE))
        cv.put(Schema.RecurrentTransfer.LAST_OCCURRENCE, contentValues.getAsString(Contract.RecurrentTransfer.START_DATE))
        cv.put(Schema.RecurrentTransfer.RULE, contentValues.getAsString(Contract.RecurrentTransfer.RULE))
        cv.put(Schema.RecurrentTransfer.TAG, contentValues.getAsString(Contract.RecurrentTransfer.TAG))
        cv.put(Schema.RecurrentTransfer.UUID, uuid)
        cv.put(Schema.RecurrentTransfer.LAST_EDIT, System.currentTimeMillis())
        cv.put(Schema.RecurrentTransfer.DELETED, false)
        val id = writableDatabase.insert(Schema.RecurrentTransfer.TABLE, null, cv)
        if (id > 0L) {
            val startDateString = contentValues.getAsString(Contract.RecurrentTransfer.START_DATE)
            val startDate = if (startDateString != null) DateUtils.getDateFromSQLDateString(startDateString) else null
            if (startDate != null) {
                val currentDateTime = DateUtils.getFixedDateTime(Date())
                val startDateTime = DateUtils.getFixedDateTime(startDate)
                var lastOccurrence = DateUtils.getFixedDateTime(startDate)
                var nextOccurrence: DateTime? = null
                val iterator = recurrenceRule.iterator(startDateTime)
                val transferCategoryId = getSystemCategoryId(Schema.CategoryTag.TRANSFER)
                val transferTaxCategoryId = getSystemCategoryId(Schema.CategoryTag.TRANSFER_TAX)
                while (iterator.hasNext()) {
                    val nextInstance = iterator.nextDateTime()
                    if (!nextInstance.after(currentDateTime)) {
                        val transactionDate = DateUtils.getFixedDate(nextInstance)
                        var cvt = ContentValues()
                        cvt.put(Schema.Transaction.MONEY, contentValues.getAsLong(Contract.RecurrentTransfer.MONEY_FROM))
                        cvt.put(Schema.Transaction.DATE, DateUtils.getSQLDateTimeString(transactionDate))
                        cvt.put(Schema.Transaction.DESCRIPTION, contentValues.getAsString(Contract.RecurrentTransfer.DESCRIPTION))
                        cvt.put(Schema.Transaction.CATEGORY, transferCategoryId)
                        cvt.put(Schema.Transaction.DIRECTION, Contract.Direction.EXPENSE)
                        cvt.put(Schema.Transaction.TYPE, Contract.TransactionType.TRANSFER)
                        cvt.put(Schema.Transaction.WALLET, contentValues.getAsLong(Contract.RecurrentTransfer.WALLET_FROM_ID))
                        cvt.put(Schema.Transaction.PLACE, contentValues.getAsLong(Contract.RecurrentTransfer.PLACE_ID))
                        cvt.put(Schema.Transaction.NOTE, contentValues.getAsString(Contract.RecurrentTransfer.NOTE))
                        cvt.putNull(Schema.Transaction.SAVING)
                        cvt.putNull(Schema.Transaction.DEBT)
                        cvt.put(Schema.Transaction.EVENT, contentValues.getAsLong(Contract.RecurrentTransfer.EVENT_ID))
                        cvt.put(Schema.Transaction.CONFIRMED, contentValues.getAsBoolean(Contract.RecurrentTransfer.CONFIRMED))
                        cvt.put(Schema.Transaction.COUNT_IN_TOTAL, contentValues.getAsBoolean(Contract.RecurrentTransfer.COUNT_IN_TOTAL))
                        cvt.put(Schema.Transaction.UUID, UUID.randomUUID().toString())
                        cvt.put(Schema.Transaction.LAST_EDIT, System.currentTimeMillis())
                        cvt.put(Schema.Transaction.DELETED, false)
                        val transaction1 = writableDatabase.insert(Schema.Transaction.TABLE, null, cvt)

                        cvt = ContentValues()
                        cvt.put(Schema.Transaction.MONEY, contentValues.getAsLong(Contract.RecurrentTransfer.MONEY_TO))
                        cvt.put(Schema.Transaction.DATE, DateUtils.getSQLDateTimeString(transactionDate))
                        cvt.put(Schema.Transaction.DESCRIPTION, contentValues.getAsString(Contract.RecurrentTransfer.DESCRIPTION))
                        cvt.put(Schema.Transaction.CATEGORY, transferCategoryId)
                        cvt.put(Schema.Transaction.DIRECTION, Contract.Direction.INCOME)
                        cvt.put(Schema.Transaction.TYPE, Contract.TransactionType.TRANSFER)
                        cvt.put(Schema.Transaction.WALLET, contentValues.getAsLong(Contract.RecurrentTransfer.WALLET_TO_ID))
                        cvt.put(Schema.Transaction.PLACE, contentValues.getAsLong(Contract.RecurrentTransfer.PLACE_ID))
                        cvt.put(Schema.Transaction.NOTE, contentValues.getAsString(Contract.RecurrentTransfer.NOTE))
                        cvt.putNull(Schema.Transaction.SAVING)
                        cvt.putNull(Schema.Transaction.DEBT)
                        cvt.put(Schema.Transaction.EVENT, contentValues.getAsLong(Contract.RecurrentTransfer.EVENT_ID))
                        cvt.put(Schema.Transaction.CONFIRMED, contentValues.getAsBoolean(Contract.RecurrentTransfer.CONFIRMED))
                        cvt.put(Schema.Transaction.COUNT_IN_TOTAL, contentValues.getAsBoolean(Contract.RecurrentTransfer.COUNT_IN_TOTAL))
                        cvt.put(Schema.Transaction.UUID, UUID.randomUUID().toString())
                        cvt.put(Schema.Transaction.LAST_EDIT, System.currentTimeMillis())
                        cvt.put(Schema.Transaction.DELETED, false)
                        val transaction2 = writableDatabase.insert(Schema.Transaction.TABLE, null, cvt)

                        var transaction3: Long? = null
                        val taxMoney = contentValues.getAsLong(Contract.RecurrentTransfer.MONEY_TAX)
                        if (taxMoney != null && taxMoney != 0L) {
                            cvt = ContentValues()
                            cvt.put(Schema.Transaction.MONEY, taxMoney)
                            cvt.put(Schema.Transaction.DATE, DateUtils.getSQLDateTimeString(transactionDate))
                            cvt.put(Schema.Transaction.DESCRIPTION, contentValues.getAsString(Contract.RecurrentTransfer.DESCRIPTION))
                            cvt.put(Schema.Transaction.CATEGORY, transferTaxCategoryId)
                            cvt.put(Schema.Transaction.DIRECTION, Contract.Direction.EXPENSE)
                            cvt.put(Schema.Transaction.TYPE, Contract.TransactionType.TRANSFER)
                            cvt.put(Schema.Transaction.WALLET, contentValues.getAsLong(Contract.RecurrentTransfer.WALLET_FROM_ID))
                            cvt.put(Schema.Transaction.PLACE, contentValues.getAsLong(Contract.RecurrentTransfer.PLACE_ID))
                            cvt.put(Schema.Transaction.NOTE, contentValues.getAsString(Contract.RecurrentTransfer.NOTE))
                            cvt.putNull(Schema.Transaction.SAVING)
                            cvt.putNull(Schema.Transaction.DEBT)
                            cvt.put(Schema.Transaction.EVENT, contentValues.getAsLong(Contract.RecurrentTransfer.EVENT_ID))
                            cvt.put(Schema.Transaction.CONFIRMED, contentValues.getAsBoolean(Contract.RecurrentTransfer.CONFIRMED))
                            cvt.put(Schema.Transaction.COUNT_IN_TOTAL, contentValues.getAsBoolean(Contract.RecurrentTransfer.COUNT_IN_TOTAL))
                            cvt.put(Schema.Transaction.UUID, UUID.randomUUID().toString())
                            cvt.put(Schema.Transaction.LAST_EDIT, System.currentTimeMillis())
                            cvt.put(Schema.Transaction.DELETED, false)
                            transaction3 = writableDatabase.insert(Schema.Transaction.TABLE, null, cvt)
                        }

                        cvt = ContentValues()
                        cvt.put(Schema.Transfer.DESCRIPTION, contentValues.getAsString(Contract.RecurrentTransfer.DESCRIPTION))
                        cvt.put(Schema.Transfer.DATE, DateUtils.getSQLDateTimeString(transactionDate))
                        cvt.put(Schema.Transfer.TRANSACTION_FROM, transaction1)
                        cvt.put(Schema.Transfer.TRANSACTION_TO, transaction2)
                        cvt.put(Schema.Transfer.TRANSACTION_TAX, transaction3)
                        cvt.put(Schema.Transfer.NOTE, contentValues.getAsString(Contract.RecurrentTransfer.NOTE))
                        cvt.put(Schema.Transfer.PLACE, contentValues.getAsLong(Contract.RecurrentTransfer.PLACE_ID))
                        cvt.put(Schema.Transfer.EVENT, contentValues.getAsLong(Contract.RecurrentTransfer.EVENT_ID))
                        cvt.put(Schema.Transfer.CONFIRMED, contentValues.getAsBoolean(Contract.RecurrentTransfer.CONFIRMED))
                        cvt.put(Schema.Transfer.COUNT_IN_TOTAL, contentValues.getAsBoolean(Contract.RecurrentTransfer.COUNT_IN_TOTAL))
                        cvt.put(Schema.Transfer.TAG, contentValues.getAsString(Contract.RecurrentTransfer.TAG))
                        cvt.put(Schema.Transfer.UUID, getRecurrentItemUUID(uuid, transactionDate))
                        cvt.put(Schema.Transfer.LAST_EDIT, System.currentTimeMillis())
                        cvt.put(Schema.Transfer.DELETED, false)
                        writableDatabase.insert(Schema.Transfer.TABLE, null, cvt)
                        lastOccurrence = nextInstance
                    } else {
                        nextOccurrence = nextInstance
                        break
                    }
                }
                val cvu = ContentValues()
                cvu.put(Schema.RecurrentTransfer.LAST_OCCURRENCE, DateUtils.getSQLDateString(DateUtils.getFixedDate(lastOccurrence)))
                val nextOccDate = nextOccurrence?.let { DateUtils.getFixedDate(it) }
                cvu.put(Schema.RecurrentTransfer.NEXT_OCCURRENCE, if (nextOccDate != null) DateUtils.getSQLDateString(nextOccDate) else null)
                val selection = Schema.RecurrentTransfer.ID + " = ?"
                val selectionArgs = arrayOf(id.toString())
                writableDatabase.update(Schema.RecurrentTransfer.TABLE, cvu, selection, selectionArgs)
            }
        }
        return id
    }

    open fun updateRecurrentTransfer(transferId: Long, contentValues: ContentValues): Int {
        if (contentValues.containsKey(Contract.RecurrentTransfer.LAST_OCCURRENCE) && contentValues.containsKey(Contract.RecurrentTransfer.NEXT_OCCURRENCE)) {
            val cv = ContentValues()
            cv.put(Schema.RecurrentTransfer.LAST_OCCURRENCE, contentValues.getAsString(Contract.RecurrentTransfer.LAST_OCCURRENCE))
            cv.put(Schema.RecurrentTransfer.NEXT_OCCURRENCE, contentValues.getAsString(Contract.RecurrentTransfer.NEXT_OCCURRENCE))
            cv.put(Schema.RecurrentTransfer.LAST_EDIT, System.currentTimeMillis())
            val where = Schema.RecurrentTransfer.ID + " = ?"
            val selectionArgs = arrayOf(transferId.toString())
            return writableDatabase.update(Schema.RecurrentTransfer.TABLE, cv, where, selectionArgs)
        }
        val rule = contentValues.getAsString(Contract.RecurrentTransfer.RULE)
        try {
            RecurrenceRule(rule)
        } catch (e: InvalidRecurrenceRuleException) {
            throw SQLiteDataException(Contract.ErrorCode.INVALID_RECURRENCE_RULE, e.message)
        }
        val startDate = contentValues.getAsString(Contract.RecurrentTransfer.START_DATE)
        val cv = ContentValues()
        cv.put(Schema.RecurrentTransfer.DESCRIPTION, contentValues.getAsString(Contract.RecurrentTransfer.DESCRIPTION))
        cv.put(Schema.RecurrentTransfer.WALLET_FROM, contentValues.getAsLong(Contract.RecurrentTransfer.WALLET_FROM_ID))
        cv.put(Schema.RecurrentTransfer.WALLET_TO, contentValues.getAsLong(Contract.RecurrentTransfer.WALLET_TO_ID))
        cv.put(Schema.RecurrentTransfer.MONEY_FROM, contentValues.getAsLong(Contract.RecurrentTransfer.MONEY_FROM))
        cv.put(Schema.RecurrentTransfer.MONEY_TO, contentValues.getAsLong(Contract.RecurrentTransfer.MONEY_TO))
        cv.put(Schema.RecurrentTransfer.MONEY_TAX, contentValues.getAsLong(Contract.RecurrentTransfer.MONEY_TAX))
        cv.put(Schema.RecurrentTransfer.NOTE, contentValues.getAsString(Contract.RecurrentTransfer.NOTE))
        cv.put(Schema.RecurrentTransfer.EVENT, contentValues.getAsLong(Contract.RecurrentTransfer.EVENT_ID))
        cv.put(Schema.RecurrentTransfer.PLACE, contentValues.getAsLong(Contract.RecurrentTransfer.PLACE_ID))
        cv.put(Schema.RecurrentTransfer.CONFIRMED, contentValues.getAsBoolean(Contract.RecurrentTransfer.CONFIRMED))
        cv.put(Schema.RecurrentTransfer.COUNT_IN_TOTAL, contentValues.getAsBoolean(Contract.RecurrentTransfer.COUNT_IN_TOTAL))
        cv.put(Schema.RecurrentTransfer.START_DATE, startDate)
        cv.put(Schema.RecurrentTransfer.RULE, rule)
        cv.put(
            Schema.RecurrentTransfer.NEXT_OCCURRENCE,
            resumedNextOccurrence(
                Schema.RecurrentTransfer.TABLE,
                Schema.RecurrentTransfer.ID,
                Schema.RecurrentTransfer.RULE,
                Schema.RecurrentTransfer.START_DATE,
                Schema.RecurrentTransfer.NEXT_OCCURRENCE,
                transferId,
                rule ?: "",
                startDate ?: ""
            )
        )
        cv.put(Schema.RecurrentTransfer.LAST_EDIT, System.currentTimeMillis())
        val where = Schema.RecurrentTransfer.ID + " = ?"
        val selectionArgs = arrayOf(transferId.toString())
        return writableDatabase.update(Schema.RecurrentTransfer.TABLE, cv, where, selectionArgs)
    }

    open fun deleteRecurrentTransfer(transferId: Long): Int {
        val contentValues = ContentValues()
        contentValues.putNull(Schema.Transfer.RECURRENCE)
        contentValues.put(Schema.Transfer.LAST_EDIT, System.currentTimeMillis())
        var selection = Schema.Transfer.RECURRENCE + " = ?"
        var selectionArgs = arrayOf(transferId.toString())
        writableDatabase.update(Schema.Transfer.TABLE, contentValues, selection, selectionArgs)
        selection = Schema.RecurrentTransfer.ID + " = ?"
        selectionArgs = arrayOf(transferId.toString())
        return writableDatabase.delete(Schema.RecurrentTransfer.TABLE, selection, selectionArgs)
    }

    open fun insertRecurrentTransferOccurrence(transferId: Long, contentValues: ContentValues): Long {
        return -1L
    }

    open fun getTransactionModel(modelId: Long, projection: Array<String>?): Cursor? {
        val selection = Schema.TransactionModel.ID + " = ?"
        val selectionArgs = arrayOf(modelId.toString())
        return getTransactionModels(projection, selection, selectionArgs, null)
    }

    open fun getTransactionModels(
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        val subQuery = "SELECT " +
                "tm." + Schema.TransactionModel.ID + " AS " + Contract.TransactionModel.ID + ", " +
                "tm." + Schema.TransactionModel.MONEY + " AS " + Contract.TransactionModel.MONEY + ", " +
                "tm." + Schema.TransactionModel.DESCRIPTION + " AS " + Contract.TransactionModel.DESCRIPTION + ", " +
                "tm." + Schema.TransactionModel.CATEGORY + " AS " + Contract.TransactionModel.CATEGORY_ID + ", " +
                "c." + Schema.Category.NAME + " AS " + Contract.TransactionModel.CATEGORY_NAME + ", " +
                "c." + Schema.Category.ICON + " AS " + Contract.TransactionModel.CATEGORY_ICON + ", " +
                "c." + Schema.Category.TYPE + " AS " + Contract.TransactionModel.CATEGORY_TYPE + ", " +
                "c." + Schema.Category.SHOW_REPORT + " AS " + Contract.TransactionModel.CATEGORY_SHOW_REPORT + ", " +
                "c." + Schema.Category.TAG + " AS " + Contract.TransactionModel.CATEGORY_TAG + ", " +
                "tm." + Schema.TransactionModel.DIRECTION + " AS " + Contract.TransactionModel.DIRECTION + ", " +
                "tm." + Schema.TransactionModel.WALLET + " AS " + Contract.TransactionModel.WALLET_ID + ", " +
                "w." + Schema.Wallet.NAME + " AS " + Contract.TransactionModel.WALLET_NAME + ", " +
                "w." + Schema.Wallet.ICON + " AS " + Contract.TransactionModel.WALLET_ICON + ", " +
                "w." + Schema.Wallet.CURRENCY + " AS " + Contract.TransactionModel.WALLET_CURRENCY + ", " +
                "w." + Schema.Wallet.COUNT_IN_TOTAL + " AS " + Contract.TransactionModel.WALLET_COUNT_IN_TOTAL + ", " +
                "w." + Schema.Wallet.ARCHIVED + " AS " + Contract.TransactionModel.WALLET_ARCHIVED + ", " +
                "w." + Schema.Wallet.TAG + " AS " + Contract.TransactionModel.WALLET_TAG + ", " +
                "tm." + Schema.TransactionModel.PLACE + " AS " + Contract.TransactionModel.PLACE_ID + ", " +
                "p." + Schema.Place.NAME + " AS " + Contract.TransactionModel.PLACE_NAME + ", " +
                "p." + Schema.Place.ICON + " AS " + Contract.TransactionModel.PLACE_ICON + ", " +
                "p." + Schema.Place.ADDRESS + " AS " + Contract.TransactionModel.PLACE_ADDRESS + ", " +
                "p." + Schema.Place.LATITUDE + " AS " + Contract.TransactionModel.PLACE_LATITUDE + ", " +
                "p." + Schema.Place.LONGITUDE + " AS " + Contract.TransactionModel.PLACE_LONGITUDE + ", " +
                "p." + Schema.Place.TAG + " AS " + Contract.TransactionModel.PLACE_TAG + ", " +
                "tm." + Schema.TransactionModel.NOTE + " AS " + Contract.TransactionModel.NOTE + ", " +
                "tm." + Schema.TransactionModel.EVENT + " AS " + Contract.TransactionModel.EVENT_ID + ", " +
                "e." + Schema.Event.NAME + " AS " + Contract.TransactionModel.EVENT_NAME + ", " +
                "e." + Schema.Event.ICON + " AS " + Contract.TransactionModel.EVENT_ICON + ", " +
                "e." + Schema.Event.NOTE + " AS " + Contract.TransactionModel.EVENT_NOTE + ", " +
                "e." + Schema.Event.START_DATE + " AS " + Contract.TransactionModel.EVENT_START_DATE + ", " +
                "e." + Schema.Event.END_DATE + " AS " + Contract.TransactionModel.EVENT_END_DATE + ", " +
                "e." + Schema.Event.TAG + " AS " + Contract.TransactionModel.EVENT_TAG + ", " +
                "tm." + Schema.TransactionModel.CONFIRMED + " AS " + Contract.TransactionModel.CONFIRMED + ", " +
                "tm." + Schema.TransactionModel.COUNT_IN_TOTAL + " AS " + Contract.TransactionModel.COUNT_IN_TOTAL + ", " +
                "tm." + Schema.TransactionModel.TAG + " AS " + Contract.TransactionModel.TAG + " " +
                "FROM " + Schema.TransactionModel.TABLE + " AS tm JOIN " + Schema.Wallet.TABLE +
                " AS w ON tm." + Schema.TransactionModel.WALLET + " = w." + Schema.Wallet.ID + " AND w." +
                Schema.Wallet.DELETED + " = 0 JOIN " + Schema.Category.TABLE + " AS c ON tm." +
                Schema.TransactionModel.CATEGORY + " = c." + Schema.Category.ID + " AND c." +
                Schema.Category.DELETED + " = 0 LEFT JOIN " + Schema.Place.TABLE + " AS p ON tm." +
                Schema.TransactionModel.PLACE + " = p." + Schema.Place.ID + " AND p." +
                Schema.Place.ID + " = 0 LEFT JOIN " + Schema.Event.TABLE + " AS e ON tm." +
                Schema.TransactionModel.EVENT + " = e." + Schema.Event.ID + " AND e." +
                Schema.Event.DELETED + " = 0 WHERE tm." + Schema.TransactionModel.DELETED + " = 0"
        return queryFrom(subQuery, projection, selection, selectionArgs, sortOrder)
    }

    open fun insertTransactionModel(contentValues: ContentValues): Long {
        val cv = ContentValues()
        cv.put(Schema.TransactionModel.MONEY, contentValues.getAsLong(Contract.TransactionModel.MONEY))
        cv.put(Schema.TransactionModel.DESCRIPTION, contentValues.getAsString(Contract.TransactionModel.DESCRIPTION))
        cv.put(Schema.TransactionModel.CATEGORY, contentValues.getAsLong(Contract.TransactionModel.CATEGORY_ID))
        cv.put(Schema.TransactionModel.DIRECTION, contentValues.getAsInteger(Contract.TransactionModel.DIRECTION))
        cv.put(Schema.TransactionModel.WALLET, contentValues.getAsLong(Contract.TransactionModel.WALLET_ID))
        cv.put(Schema.TransactionModel.PLACE, contentValues.getAsLong(Contract.TransactionModel.PLACE_ID))
        cv.put(Schema.TransactionModel.NOTE, contentValues.getAsString(Contract.TransactionModel.NOTE))
        cv.put(Schema.TransactionModel.EVENT, contentValues.getAsLong(Contract.TransactionModel.EVENT_ID))
        cv.put(Schema.TransactionModel.CONFIRMED, contentValues.getAsBoolean(Contract.TransactionModel.CONFIRMED))
        cv.put(Schema.TransactionModel.COUNT_IN_TOTAL, contentValues.getAsBoolean(Contract.TransactionModel.COUNT_IN_TOTAL))
        cv.put(Schema.TransactionModel.TAG, contentValues.getAsString(Contract.TransactionModel.TAG))
        cv.put(Schema.TransactionModel.UUID, UUID.randomUUID().toString())
        cv.put(Schema.TransactionModel.LAST_EDIT, System.currentTimeMillis())
        cv.put(Schema.TransactionModel.DELETED, false)
        return writableDatabase.insert(Schema.TransactionModel.TABLE, null, cv)
    }

    open fun updateTransactionModel(modelId: Long, contentValues: ContentValues): Int {
        val cv = ContentValues()
        cv.put(Schema.TransactionModel.MONEY, contentValues.getAsLong(Contract.TransactionModel.MONEY))
        cv.put(Schema.TransactionModel.DESCRIPTION, contentValues.getAsString(Contract.TransactionModel.DESCRIPTION))
        cv.put(Schema.TransactionModel.CATEGORY, contentValues.getAsLong(Contract.TransactionModel.CATEGORY_ID))
        cv.put(Schema.TransactionModel.DIRECTION, contentValues.getAsInteger(Contract.TransactionModel.DIRECTION))
        cv.put(Schema.TransactionModel.WALLET, contentValues.getAsLong(Contract.TransactionModel.WALLET_ID))
        cv.put(Schema.TransactionModel.PLACE, contentValues.getAsLong(Contract.TransactionModel.PLACE_ID))
        cv.put(Schema.TransactionModel.NOTE, contentValues.getAsString(Contract.TransactionModel.NOTE))
        cv.put(Schema.TransactionModel.EVENT, contentValues.getAsLong(Contract.TransactionModel.EVENT_ID))
        cv.put(Schema.TransactionModel.CONFIRMED, contentValues.getAsBoolean(Contract.TransactionModel.CONFIRMED))
        cv.put(Schema.TransactionModel.COUNT_IN_TOTAL, contentValues.getAsBoolean(Contract.TransactionModel.COUNT_IN_TOTAL))
        if (contentValues.containsKey(Contract.TransactionModel.TAG)) {
            cv.put(Schema.TransactionModel.TAG, contentValues.getAsString(Contract.TransactionModel.TAG))
        }
        cv.put(Schema.TransactionModel.LAST_EDIT, System.currentTimeMillis())
        val where = Schema.TransactionModel.ID + " = ?"
        val whereArgs = arrayOf(modelId.toString())
        return writableDatabase.update(Schema.TransactionModel.TABLE, cv, where, whereArgs)
    }

    open fun deleteTransactionModel(modelId: Long): Int {
        val where = Schema.TransactionModel.ID + " = ?"
        val whereArgs = arrayOf(modelId.toString())
        return writableDatabase.delete(Schema.TransactionModel.TABLE, where, whereArgs)
    }

    open fun getTransferModel(modelId: Long, projection: Array<String>?): Cursor? {
        val selection = Schema.TransferModel.ID + " = ?"
        val selectionArgs = arrayOf(modelId.toString())
        return getTransferModels(projection, selection, selectionArgs, null)
    }

    open fun getTransferModels(
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        val subQuery = "SELECT " +
                "tm." + Schema.TransferModel.ID + " AS " + Contract.TransferModel.ID + ", " +
                "tm." + Schema.TransferModel.DESCRIPTION + " AS " + Contract.TransferModel.DESCRIPTION + ", " +
                "tm." + Schema.TransferModel.WALLET_FROM + " AS " + Contract.TransferModel.WALLET_FROM_ID + ", " +
                "wf." + Schema.Wallet.NAME + " AS " + Contract.TransferModel.WALLET_FROM_NAME + ", " +
                "wf." + Schema.Wallet.ICON + " AS " + Contract.TransferModel.WALLET_FROM_ICON + ", " +
                "wf." + Schema.Wallet.CURRENCY + " AS " + Contract.TransferModel.WALLET_FROM_CURRENCY + ", " +
                "wf." + Schema.Wallet.COUNT_IN_TOTAL + " AS " + Contract.TransferModel.WALLET_FROM_COUNT_IN_TOTAL + ", " +
                "wf." + Schema.Wallet.ARCHIVED + " AS " + Contract.TransferModel.WALLET_FROM_ARCHIVED + ", " +
                "wf." + Schema.Wallet.TAG + " AS " + Contract.TransferModel.WALLET_FROM_TAG + ", " +
                "tm." + Schema.TransferModel.WALLET_TO + " AS " + Contract.TransferModel.WALLET_TO_ID + ", " +
                "wt." + Schema.Wallet.NAME + " AS " + Contract.TransferModel.WALLET_TO_NAME + ", " +
                "wt." + Schema.Wallet.ICON + " AS " + Contract.TransferModel.WALLET_TO_ICON + ", " +
                "wt." + Schema.Wallet.CURRENCY + " AS " + Contract.TransferModel.WALLET_TO_CURRENCY + ", " +
                "wt." + Schema.Wallet.COUNT_IN_TOTAL + " AS " + Contract.TransferModel.WALLET_TO_COUNT_IN_TOTAL + ", " +
                "wt." + Schema.Wallet.ARCHIVED + " AS " + Contract.TransferModel.WALLET_TO_ARCHIVED + ", " +
                "wt." + Schema.Wallet.TAG + " AS " + Contract.TransferModel.WALLET_TO_TAG + ", " +
                "tm." + Schema.TransferModel.MONEY_FROM + " AS " + Contract.TransferModel.MONEY_FROM + ", " +
                "tm." + Schema.TransferModel.MONEY_TO + " AS " + Contract.TransferModel.MONEY_TO + ", " +
                "tm." + Schema.TransferModel.MONEY_TAX + " AS " + Contract.TransferModel.MONEY_TAX + ", " +
                "tm." + Schema.TransferModel.NOTE + " AS " + Contract.TransferModel.NOTE + ", " +
                "tm." + Schema.TransferModel.EVENT + " AS " + Contract.TransferModel.EVENT_ID + ", " +
                "e." + Schema.Event.NAME + " AS " + Contract.TransferModel.EVENT_NAME + ", " +
                "e." + Schema.Event.ICON + " AS " + Contract.TransferModel.EVENT_ICON + ", " +
                "e." + Schema.Event.NOTE + " AS " + Contract.TransferModel.EVENT_NOTE + ", " +
                "e." + Schema.Event.START_DATE + " AS " + Contract.TransferModel.EVENT_START_DATE + ", " +
                "e." + Schema.Event.END_DATE + " AS " + Contract.TransferModel.EVENT_END_DATE + ", " +
                "e." + Schema.Event.TAG + " AS " + Contract.TransferModel.EVENT_TAG + ", " +
                "tm." + Schema.TransferModel.PLACE + " AS " + Contract.TransferModel.PLACE_ID + ", " +
                "p." + Schema.Place.NAME + " AS " + Contract.TransferModel.PLACE_NAME + ", " +
                "p." + Schema.Place.ICON + " AS " + Contract.TransferModel.PLACE_ICON + ", " +
                "p." + Schema.Place.ADDRESS + " AS " + Contract.TransferModel.PLACE_ADDRESS + ", " +
                "p." + Schema.Place.LATITUDE + " AS " + Contract.TransferModel.PLACE_LATITUDE + ", " +
                "p." + Schema.Place.LONGITUDE + " AS " + Contract.TransferModel.PLACE_LONGITUDE + ", " +
                "p." + Schema.Place.TAG + " AS " + Contract.TransferModel.PLACE_TAG + ", " +
                "tm." + Schema.TransferModel.CONFIRMED + " AS " + Contract.TransferModel.CONFIRMED + ", " +
                "tm." + Schema.TransferModel.COUNT_IN_TOTAL + " AS " + Contract.TransferModel.COUNT_IN_TOTAL + ", " +
                "tm." + Schema.TransferModel.TAG + " AS " + Contract.TransferModel.TAG + " " +
                "FROM " + Schema.TransferModel.TABLE + " AS tm JOIN " + Schema.Wallet.TABLE + " AS " +
                "wf ON tm." + Schema.TransferModel.WALLET_FROM + " = wf." + Schema.Wallet.ID +
                " AND wf." + Schema.Wallet.DELETED + " = 0 JOIN " + Schema.Wallet.TABLE + " AS " +
                "wt ON tm." + Schema.TransferModel.WALLET_TO + " = wt." + Schema.Wallet.ID +
                " AND wt." + Schema.Wallet.DELETED + " = 0 LEFT JOIN " + Schema.Event.TABLE +
                " AS e ON tm." + Schema.TransferModel.EVENT + " = e." + Schema.Event.ID + " AND e." +
                Schema.Event.DELETED + " = 0 LEFT JOIN " + Schema.Place.TABLE + " AS p ON tm." +
                Schema.TransferModel.PLACE + " = p." + Schema.Place.ID + " AND p." +
                Schema.Place.DELETED + " = 0 WHERE tm." + Schema.TransferModel.DELETED + " = 0"
        return queryFrom(subQuery, projection, selection, selectionArgs, sortOrder)
    }

    open fun insertTransferModel(contentValues: ContentValues): Long {
        val cv = ContentValues()
        cv.put(Schema.TransferModel.DESCRIPTION, contentValues.getAsString(Contract.TransferModel.DESCRIPTION))
        cv.put(Schema.TransferModel.WALLET_FROM, contentValues.getAsLong(Contract.TransferModel.WALLET_FROM_ID))
        cv.put(Schema.TransferModel.WALLET_TO, contentValues.getAsLong(Contract.TransferModel.WALLET_TO_ID))
        cv.put(Schema.TransferModel.MONEY_FROM, contentValues.getAsLong(Contract.TransferModel.MONEY_FROM))
        cv.put(Schema.TransferModel.MONEY_TO, contentValues.getAsLong(Contract.TransferModel.MONEY_TO))
        cv.put(Schema.TransferModel.MONEY_TAX, contentValues.getAsLong(Contract.TransferModel.MONEY_TAX))
        cv.put(Schema.TransferModel.NOTE, contentValues.getAsString(Contract.TransferModel.NOTE))
        cv.put(Schema.TransferModel.EVENT, contentValues.getAsLong(Contract.TransferModel.EVENT_ID))
        cv.put(Schema.TransferModel.PLACE, contentValues.getAsLong(Contract.TransferModel.PLACE_ID))
        cv.put(Schema.TransferModel.CONFIRMED, contentValues.getAsBoolean(Contract.TransferModel.CONFIRMED))
        cv.put(Schema.TransferModel.COUNT_IN_TOTAL, contentValues.getAsBoolean(Contract.TransferModel.COUNT_IN_TOTAL))
        cv.put(Schema.TransferModel.TAG, contentValues.getAsString(Contract.TransferModel.TAG))
        cv.put(Schema.TransferModel.UUID, UUID.randomUUID().toString())
        cv.put(Schema.TransferModel.LAST_EDIT, System.currentTimeMillis())
        cv.put(Schema.TransferModel.DELETED, false)
        return writableDatabase.insert(Schema.TransferModel.TABLE, null, cv)
    }

    open fun updateTransferModel(modelId: Long, contentValues: ContentValues): Int {
        val cv = ContentValues()
        cv.put(Schema.TransferModel.DESCRIPTION, contentValues.getAsString(Contract.TransferModel.DESCRIPTION))
        cv.put(Schema.TransferModel.WALLET_FROM, contentValues.getAsLong(Contract.TransferModel.WALLET_FROM_ID))
        cv.put(Schema.TransferModel.WALLET_TO, contentValues.getAsLong(Contract.TransferModel.WALLET_TO_ID))
        cv.put(Schema.TransferModel.MONEY_FROM, contentValues.getAsLong(Contract.TransferModel.MONEY_FROM))
        cv.put(Schema.TransferModel.MONEY_TO, contentValues.getAsLong(Contract.TransferModel.MONEY_TO))
        cv.put(Schema.TransferModel.MONEY_TAX, contentValues.getAsLong(Contract.TransferModel.MONEY_TAX))
        cv.put(Schema.TransferModel.NOTE, contentValues.getAsString(Contract.TransferModel.NOTE))
        cv.put(Schema.TransferModel.EVENT, contentValues.getAsLong(Contract.TransferModel.EVENT_ID))
        cv.put(Schema.TransferModel.PLACE, contentValues.getAsLong(Contract.TransferModel.PLACE_ID))
        cv.put(Schema.TransferModel.CONFIRMED, contentValues.getAsBoolean(Contract.TransferModel.CONFIRMED))
        cv.put(Schema.TransferModel.COUNT_IN_TOTAL, contentValues.getAsBoolean(Contract.TransferModel.COUNT_IN_TOTAL))
        if (contentValues.containsKey(Contract.TransferModel.TAG)) {
            cv.put(Schema.TransferModel.TAG, contentValues.getAsString(Contract.TransferModel.TAG))
        }
        cv.put(Schema.TransferModel.LAST_EDIT, System.currentTimeMillis())
        val where = Schema.TransferModel.ID + " = ?"
        val whereArgs = arrayOf(modelId.toString())
        return writableDatabase.update(Schema.TransferModel.TABLE, cv, where, whereArgs)
    }

    open fun deleteTransferModel(modelId: Long): Int {
        val where = Schema.TransferModel.ID + " = ?"
        val whereArgs = arrayOf(modelId.toString())
        return writableDatabase.delete(Schema.TransferModel.TABLE, where, whereArgs)
    }


    open fun getPlace(placeId: Long, projection: Array<String>?): Cursor? {
        val selection = Schema.Place.ID + " = ?"
        val selectionArgs = arrayOf(placeId.toString())
        return getPlaces(projection, selection, selectionArgs, null)
    }

    open fun getPlaces(
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        val subQuery = "SELECT " +
                Schema.Place.ID + " AS " + Contract.Place.ID + ", " +
                Schema.Place.NAME + " AS " + Contract.Place.NAME + ", " +
                Schema.Place.ICON + " AS " + Contract.Place.ICON + ", " +
                Schema.Place.ADDRESS + " AS " + Contract.Place.ADDRESS + ", " +
                Schema.Place.LATITUDE + " AS " + Contract.Place.LATITUDE + ", " +
                Schema.Place.LONGITUDE + " AS " + Contract.Place.LONGITUDE + ", " +
                Schema.Place.TAG + " AS " + Contract.Place.TAG + " FROM " +
                Schema.Place.TABLE + " WHERE " + Schema.Place.DELETED + " = 0"
        return queryFrom(subQuery, projection, selection, selectionArgs, sortOrder)
    }

    open fun getPlaceTransactions(
        placeId: Long,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        var _selection = Schema.Transaction.PLACE + " = ?"
        if (!TextUtils.isEmpty(selection)) {
            _selection += " AND " + selection
        }
        val size = selectionArgs?.size ?: 0
        val _selectionArgs = Array(size + 1) { index ->
            if (index == 0) placeId.toString() else (selectionArgs?.get(index - 1) ?: "")
        }
        return getTransactions(projection, _selection, _selectionArgs, sortOrder)
    }

    open fun insertPlace(contentValues: ContentValues): Long {
        val cv = ContentValues()
        cv.put(Schema.Place.NAME, contentValues.getAsString(Contract.Place.NAME))
        cv.put(Schema.Place.ICON, contentValues.getAsString(Contract.Place.ICON))
        cv.put(Schema.Place.ADDRESS, contentValues.getAsString(Contract.Place.ADDRESS))
        cv.put(Schema.Place.LATITUDE, contentValues.getAsString(Contract.Place.LATITUDE))
        cv.put(Schema.Place.LONGITUDE, contentValues.getAsString(Contract.Place.LONGITUDE))
        cv.put(Schema.Place.TAG, contentValues.getAsString(Contract.Place.TAG))
        cv.put(Schema.Place.UUID, UUID.randomUUID().toString())
        cv.put(Schema.Place.LAST_EDIT, System.currentTimeMillis())
        cv.put(Schema.Place.DELETED, false)
        return writableDatabase.insert(Schema.Place.TABLE, null, cv)
    }

    open fun updatePlace(placeId: Long, contentValues: ContentValues): Int {
        val cv = ContentValues()
        cv.put(Schema.Place.NAME, contentValues.getAsString(Contract.Place.NAME))
        cv.put(Schema.Place.ICON, contentValues.getAsString(Contract.Place.ICON))
        cv.put(Schema.Place.ADDRESS, contentValues.getAsString(Contract.Place.ADDRESS))
        cv.put(Schema.Place.LATITUDE, contentValues.getAsString(Contract.Place.LATITUDE))
        cv.put(Schema.Place.LONGITUDE, contentValues.getAsString(Contract.Place.LONGITUDE))
        if (contentValues.containsKey(Contract.Place.TAG)) {
            cv.put(Schema.Place.TAG, contentValues.getAsString(Contract.Place.TAG))
        }
        cv.put(Schema.Place.LAST_EDIT, System.currentTimeMillis())
        val where = Schema.Place.ID + " = ?"
        val whereArgs = arrayOf(placeId.toString())
        return writableDatabase.update(Schema.Place.TABLE, cv, where, whereArgs)
    }

    open fun deletePlace(placeId: Long): Int {
        var cv = ContentValues()
        cv.putNull(Schema.Transaction.PLACE)
        cv.put(Schema.Transaction.LAST_EDIT, System.currentTimeMillis())
        var where = Schema.Transaction.PLACE + " = ?"
        var whereArgs = arrayOf(placeId.toString())
        writableDatabase.update(Schema.Transaction.TABLE, cv, where, whereArgs)

        cv = ContentValues()
        cv.putNull(Schema.Transfer.PLACE)
        cv.put(Schema.Transfer.LAST_EDIT, System.currentTimeMillis())
        where = Schema.Transfer.PLACE + " = ?"
        whereArgs = arrayOf(placeId.toString())
        writableDatabase.update(Schema.Transfer.TABLE, cv, where, whereArgs)

        cv = ContentValues()
        cv.putNull(Schema.Debt.PLACE)
        cv.put(Schema.Debt.LAST_EDIT, System.currentTimeMillis())
        where = Schema.Debt.PLACE + " = ?"
        whereArgs = arrayOf(placeId.toString())
        writableDatabase.update(Schema.Debt.TABLE, cv, where, whereArgs)

        cv = ContentValues()
        cv.putNull(Schema.TransactionModel.PLACE)
        cv.put(Schema.TransactionModel.LAST_EDIT, System.currentTimeMillis())
        where = Schema.TransactionModel.PLACE + " = ?"
        whereArgs = arrayOf(placeId.toString())
        writableDatabase.update(Schema.TransactionModel.TABLE, cv, where, whereArgs)

        cv = ContentValues()
        cv.putNull(Schema.TransferModel.PLACE)
        cv.put(Schema.TransferModel.LAST_EDIT, System.currentTimeMillis())
        where = Schema.TransferModel.PLACE + " = ?"
        whereArgs = arrayOf(placeId.toString())
        writableDatabase.update(Schema.TransferModel.TABLE, cv, where, whereArgs)

        cv = ContentValues()
        cv.putNull(Schema.RecurrentTransaction.PLACE)
        cv.put(Schema.RecurrentTransaction.LAST_EDIT, System.currentTimeMillis())
        where = Schema.RecurrentTransaction.PLACE + " = ?"
        whereArgs = arrayOf(placeId.toString())
        writableDatabase.update(Schema.RecurrentTransaction.TABLE, cv, where, whereArgs)

        cv = ContentValues()
        cv.putNull(Schema.RecurrentTransfer.PLACE)
        cv.put(Schema.RecurrentTransfer.LAST_EDIT, System.currentTimeMillis())
        where = Schema.RecurrentTransfer.PLACE + " = ?"
        whereArgs = arrayOf(placeId.toString())
        writableDatabase.update(Schema.RecurrentTransfer.TABLE, cv, where, whereArgs)

        where = Schema.Place.ID + " = ?"
        whereArgs = arrayOf(placeId.toString())
        return writableDatabase.delete(Schema.Place.TABLE, where, whereArgs)
    }

    open fun getPerson(personId: Long, projection: Array<String>?): Cursor? {
        val selection = Schema.Person.ID + " = ?"
        val selectionArgs = arrayOf(personId.toString())
        return getPeople(projection, selection, selectionArgs, null)
    }

    open fun getPeople(
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        val subQuery = "SELECT " +
                Schema.Person.ID + " AS " + Contract.Person.ID + ", " +
                Schema.Person.NAME + " AS " + Contract.Person.NAME + ", " +
                Schema.Person.ICON + " AS " + Contract.Person.ICON + ", " +
                Schema.Person.NOTE + " AS " + Contract.Person.NOTE + ", " +
                Schema.Person.TAG + " AS " + Contract.Person.TAG + " " +
                "FROM " + Schema.Person.TABLE + " WHERE " + Schema.Person.DELETED + " = 0"
        return queryFrom(subQuery, projection, selection, selectionArgs, sortOrder)
    }

    open fun getPeopleTransactions(
        personId: Long,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        var _selection = Contract.Transaction.PEOPLE_IDS + " LIKE ?"
        if (!TextUtils.isEmpty(selection)) {
            _selection += " AND " + selection
        }
        val size = selectionArgs?.size ?: 0
        val _selectionArgs = Array(size + 1) { index ->
            if (index == 0) "%<" + personId + ">%" else (selectionArgs?.get(index - 1) ?: "")
        }
        return getTransactions(projection, _selection, _selectionArgs, sortOrder)
    }

    open fun insertPerson(contentValues: ContentValues): Long {
        val cv = ContentValues()
        cv.put(Schema.Person.NAME, contentValues.getAsString(Contract.Person.NAME))
        cv.put(Schema.Person.ICON, contentValues.getAsString(Contract.Person.ICON))
        cv.put(Schema.Person.NOTE, contentValues.getAsString(Contract.Person.NOTE))
        cv.put(Schema.Person.TAG, contentValues.getAsString(Contract.Person.TAG))
        cv.put(Schema.Person.UUID, UUID.randomUUID().toString())
        cv.put(Schema.Person.LAST_EDIT, System.currentTimeMillis())
        cv.put(Schema.Person.DELETED, false)
        return writableDatabase.insert(Schema.Person.TABLE, null, cv)
    }

    open fun updatePerson(personId: Long, contentValues: ContentValues): Int {
        val cv = ContentValues()
        cv.put(Schema.Person.NAME, contentValues.getAsString(Contract.Person.NAME))
        cv.put(Schema.Person.ICON, contentValues.getAsString(Contract.Person.ICON))
        cv.put(Schema.Person.NOTE, contentValues.getAsString(Contract.Person.NOTE))
        if (contentValues.containsKey(Contract.Person.TAG)) {
            cv.put(Schema.Person.TAG, contentValues.getAsString(Contract.Person.TAG))
        }
        cv.put(Schema.Person.LAST_EDIT, System.currentTimeMillis())
        val where = Schema.Person.ID + " = ?"
        val whereArgs = arrayOf(personId.toString())
        return writableDatabase.update(Schema.Person.TABLE, cv, where, whereArgs)
    }

    open fun deletePerson(personId: Long): Int {
        var where = Schema.TransactionPeople.PERSON + " = ?"
        var whereArgs = arrayOf(personId.toString())
        writableDatabase.delete(Schema.TransactionPeople.TABLE, where, whereArgs)

        where = Schema.TransferPeople.PERSON + " = ?"
        whereArgs = arrayOf(personId.toString())
        writableDatabase.delete(Schema.TransferPeople.TABLE, where, whereArgs)

        where = Schema.EventPeople.PERSON + " = ?"
        whereArgs = arrayOf(personId.toString())
        writableDatabase.delete(Schema.EventPeople.TABLE, where, whereArgs)

        where = Schema.DebtPeople.PERSON + " = ?"
        whereArgs = arrayOf(personId.toString())
        writableDatabase.delete(Schema.DebtPeople.TABLE, where, whereArgs)

        where = Schema.Person.ID + " = ?"
        whereArgs = arrayOf(personId.toString())
        return writableDatabase.delete(Schema.Person.TABLE, where, whereArgs)
    }

    open fun getAttachment(attachmentId: Long, projection: Array<String>?): Cursor? {
        val selection = Schema.Attachment.ID + " = ?"
        val selectionArgs = arrayOf(attachmentId.toString())
        return getAttachments(projection, selection, selectionArgs, null)
    }

    open fun getAttachments(
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        val subQuery = "SELECT " +
                Schema.Attachment.ID + " AS " + Contract.Attachment.ID + ", " +
                Schema.Attachment.FILE + " AS " + Contract.Attachment.FILE + ", " +
                Schema.Attachment.NAME + " AS " + Contract.Attachment.NAME + ", " +
                Schema.Attachment.TYPE + " AS " + Contract.Attachment.TYPE + ", " +
                Schema.Attachment.SIZE + " AS " + Contract.Attachment.SIZE + ", " +
                Schema.Attachment.TAG + " AS " + Contract.Attachment.TAG + " " +
                "FROM " + Schema.Attachment.TABLE + " WHERE " + Schema.Attachment.DELETED + " = 0"
        return queryFrom(subQuery, projection, selection, selectionArgs, sortOrder)
    }

    open fun insertAttachment(contentValues: ContentValues): Long {
        val cv = ContentValues()
        cv.put(Schema.Attachment.FILE, contentValues.getAsString(Contract.Attachment.FILE))
        cv.put(Schema.Attachment.NAME, contentValues.getAsString(Contract.Attachment.NAME))
        cv.put(Schema.Attachment.TYPE, contentValues.getAsString(Contract.Attachment.TYPE))
        cv.put(Schema.Attachment.SIZE, contentValues.getAsLong(Contract.Attachment.SIZE))
        cv.put(Schema.Attachment.TAG, contentValues.getAsString(Contract.Attachment.TAG))
        cv.put(Schema.Attachment.UUID, UUID.randomUUID().toString())
        cv.put(Schema.Attachment.LAST_EDIT, System.currentTimeMillis())
        cv.put(Schema.Attachment.DELETED, false)
        return writableDatabase.insert(Schema.Attachment.TABLE, null, cv)
    }

    open fun updateAttachment(attachmentId: Long, contentValues: ContentValues): Int {
        val cv = ContentValues()
        cv.put(Schema.Attachment.FILE, contentValues.getAsString(Contract.Attachment.FILE))
        cv.put(Schema.Attachment.NAME, contentValues.getAsString(Contract.Attachment.NAME))
        cv.put(Schema.Attachment.TYPE, contentValues.getAsString(Contract.Attachment.TYPE))
        cv.put(Schema.Attachment.SIZE, contentValues.getAsLong(Contract.Attachment.SIZE))
        if (contentValues.containsKey(Contract.Attachment.TAG)) {
            cv.put(Schema.Attachment.TAG, contentValues.getAsString(Contract.Attachment.TAG))
        }
        cv.put(Schema.Attachment.LAST_EDIT, System.currentTimeMillis())
        val where = Schema.Attachment.ID + " = ?"
        val whereArgs = arrayOf(attachmentId.toString())
        return writableDatabase.update(Schema.Attachment.TABLE, cv, where, whereArgs)
    }

    open fun deleteAttachment(attachmentId: Long): Int {
        val where = Schema.Attachment.ID + " = ?"
        val whereArgs = arrayOf(attachmentId.toString())
        return writableDatabase.delete(Schema.Attachment.TABLE, where, whereArgs)
    }

    private fun deleteAttachmentsOf(
        linkTable: String,
        attachmentColumn: String,
        linkSelection: String,
        selectionArgs: Array<String>?
    ) {
        val query = "SELECT " + Schema.Attachment.ID + ", " + Schema.Attachment.FILE +
                " FROM " + Schema.Attachment.TABLE +
                " WHERE " + Schema.Attachment.DELETED + " = 0 AND " + Schema.Attachment.ID +
                " IN (SELECT " + attachmentColumn + " FROM " + linkTable + " WHERE " + linkSelection + ")"
        val attachments = LinkedHashMap<Long, String?>()
        val cursor = readableDatabase.rawQuery(query, selectionArgs)
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    attachments[cursor.getLong(cursor.getColumnIndex(Schema.Attachment.ID))] =
                        cursor.getString(cursor.getColumnIndex(Schema.Attachment.FILE))
                }
            } finally {
                cursor.close()
            }
        }
        if (attachments.isEmpty()) {
            return
        }
        val removed = ArrayList<String>()
        for ((key, value) in attachments) {
            if (isLinkedOutside(key, linkTable, attachmentColumn, linkSelection, selectionArgs)) {
                continue
            }
            deleteAttachment(key)
            if (value != null) {
                removed.add(value)
            }
        }
        val pending = mPendingAttachmentFiles.get()
        if (pending != null) {
            pending.addAll(removed)
        } else {
            deleteAttachmentFiles(removed)
        }
    }

    private fun deleteAttachmentFiles(fileNames: List<String>?) {
        if (fileNames.isNullOrEmpty()) {
            return
        }
        val folder = getAttachmentFolder() ?: return
        for (fileName in fileNames) {
            deleteAttachmentFile(folder, fileName)
        }
    }

    private fun isLinkedOutside(
        attachmentId: Long,
        linkTable: String,
        attachmentColumn: String,
        linkSelection: String,
        selectionArgs: Array<String>?
    ): Boolean {
        val query = "SELECT (SELECT COUNT(*) FROM " + Schema.TransactionAttachment.TABLE +
                " WHERE " + Schema.TransactionAttachment.ATTACHMENT + " = " + attachmentId + " AND " +
                Schema.TransactionAttachment.DELETED + " = 0) + (SELECT COUNT(*) FROM " +
                Schema.TransferAttachment.TABLE + " WHERE " + Schema.TransferAttachment.ATTACHMENT +
                " = " + attachmentId + " AND " + Schema.TransferAttachment.DELETED +
                " = 0) - (SELECT COUNT(*) FROM " + linkTable + " WHERE " + attachmentColumn +
                " = " + attachmentId + " AND (" + linkSelection + "))"
        val cursor = readableDatabase.rawQuery(query, selectionArgs)
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    return cursor.getInt(0) > 0
                }
            } finally {
                cursor.close()
            }
        }
        return false
    }

    private fun getAttachmentFolder(): File? {
        val external = mContext.getExternalFilesDir(null)
        return if (external != null) File(external, Attachment.FOLDER_NAME) else null
    }

    private fun deleteAttachmentFile(folder: File, fileName: String) {
        if (TextUtils.isEmpty(fileName) || fileName.contains("/") || fileName.contains("\\")
                || fileName == "." || fileName == "..") {
            return
        }
        File(folder, fileName).delete()
    }

    private fun queryFrom(
        subQuery: String,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        return readableDatabase.query("($subQuery)", projection, selection, selectionArgs, null, null, sortOrder)
    }

    private fun parseIds(list: String?): LongArray? {
        if (!TextUtils.isEmpty(list)) {
            val encodedIds = (list ?: "").split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val ids = LongArray(encodedIds.size)
            for (i in encodedIds.indices) {
                val encodedId = encodedIds[i]
                if (encodedId.startsWith("<") && encodedId.endsWith(">")) {
                    ids[i] = encodedId.substring(1, encodedId.length - 1).toLong()
                } else {
                    val message = "The ids column not follow the pattern at index %d. Content: %s"
                    throw SQLiteException(String.format(Locale.ENGLISH, message, i, list))
                }
            }
            return ids
        }
        return null
    }

    private fun getRecurrentItemUUID(recurrenceUUID: String, date: Date): String {
        return String.format("%s:%s", recurrenceUUID, DateUtils.getSQLDateString(date))
    }

    private fun resumedNextOccurrence(
        table: String,
        idColumn: String,
        ruleColumn: String,
        startDateColumn: String,
        nextOccurrenceColumn: String,
        id: Long,
        newRule: String,
        newStartDate: String
    ): String? {
        var oldRule: String? = null
        var oldStartDate: String? = null
        var oldNextOccurrence: String? = null
        val projection = arrayOf(ruleColumn, startDateColumn, nextOccurrenceColumn)
        val cursor = writableDatabase.query(
            table,
            projection,
            "$idColumn = ?",
            arrayOf(id.toString()),
            null,
            null,
            null
        )
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    oldRule = cursor.getString(0)
                    oldStartDate = cursor.getString(1)
                    oldNextOccurrence = if (cursor.isNull(2)) null else cursor.getString(2)
                }
            } finally {
                cursor.close()
            }
        }
        return RecurrenceSetting.resumeAfterEdit(
            oldRule,
            oldStartDate,
            oldNextOccurrence,
            newRule,
            newStartDate,
            Date()
        )
    }

    private fun fixCurrencyAmounts(db: SQLiteDatabase, iso: String, decimalOffset: Int) {
        if (decimalOffset != 0) {
            val walletIds = ArrayList<Long>()
            val projections = arrayOf(
                Contract.Wallet.ID,
                Contract.Wallet.START_MONEY
            )
            val selection = Contract.Wallet.CURRENCY + " = ?"
            val selectionArgs = arrayOf(iso)
            var cursor = db.query(Schema.Wallet.TABLE, projections, selection, selectionArgs, null, null, null)
            if (cursor != null) {
                val indexId = cursor.getColumnIndex(Contract.Wallet.ID)
                val indexStartMoney = cursor.getColumnIndex(Contract.Wallet.START_MONEY)
                while (cursor.moveToNext()) {
                    val walletId = cursor.getLong(indexId)
                    val startMoney = cursor.getLong(indexStartMoney)
                    val fixedStartMoney = MoneyFormatter.normalize(startMoney, decimalOffset)
                    val contentValues = ContentValues()
                    contentValues.put(Schema.Wallet.START_MONEY, fixedStartMoney)
                    val whereClause = Schema.Wallet.ID + " = ?"
                    val whereArgs = arrayOf(walletId.toString())
                    db.update(Schema.Wallet.TABLE, contentValues, whereClause, whereArgs)
                    walletIds.add(walletId)
                }
                cursor.close()
            }
            for (walletId in walletIds) {
                var projection = arrayOf(
                    Schema.Debt.ID,
                    Schema.Debt.MONEY
                )
                var sel = Schema.Debt.WALLET + " = ?"
                var selArgs = arrayOf(walletId.toString())
                cursor = db.query(Schema.Debt.TABLE, projection, sel, selArgs, null, null, null)
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(cursor.getColumnIndex(Schema.Debt.ID))
                        val money = cursor.getLong(cursor.getColumnIndex(Schema.Debt.MONEY))
                        val fixedMoney = MoneyFormatter.normalize(money, decimalOffset)
                        val contentValues = ContentValues()
                        contentValues.put(Schema.Debt.MONEY, fixedMoney)
                        val whereClause = Schema.Debt.ID + " = ?"
                        val whereArgs = arrayOf(id.toString())
                        db.update(Schema.Debt.TABLE, contentValues, whereClause, whereArgs)
                    }
                    cursor.close()
                }

                projection = arrayOf(
                    Schema.Saving.ID,
                    Schema.Saving.START_MONEY,
                    Schema.Saving.END_MONEY
                )
                sel = Schema.Saving.WALLET + " = ?"
                cursor = db.query(Schema.Saving.TABLE, projection, sel, selArgs, null, null, null)
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(cursor.getColumnIndex(Schema.Saving.ID))
                        val startMoney = cursor.getLong(cursor.getColumnIndex(Schema.Saving.START_MONEY))
                        val endMoney = cursor.getLong(cursor.getColumnIndex(Schema.Saving.END_MONEY))
                        val fixedStartMoney = MoneyFormatter.normalize(startMoney, decimalOffset)
                        val fixedEndMoney = MoneyFormatter.normalize(endMoney, decimalOffset)
                        val contentValues = ContentValues()
                        contentValues.put(Schema.Saving.START_MONEY, fixedStartMoney)
                        contentValues.put(Schema.Saving.END_MONEY, fixedEndMoney)
                        val whereClause = Schema.Saving.ID + " = ?"
                        val whereArgs = arrayOf(id.toString())
                        db.update(Schema.Saving.TABLE, contentValues, whereClause, whereArgs)
                    }
                    cursor.close()
                }

                projection = arrayOf(
                    Schema.Transaction.ID,
                    Schema.Transaction.MONEY
                )
                sel = Schema.Transaction.WALLET + " = ?"
                cursor = db.query(Schema.Transaction.TABLE, projection, sel, selArgs, null, null, null)
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(cursor.getColumnIndex(Schema.Transaction.ID))
                        val money = cursor.getLong(cursor.getColumnIndex(Schema.Transaction.MONEY))
                        val fixedMoney = MoneyFormatter.normalize(money, decimalOffset)
                        val contentValues = ContentValues()
                        contentValues.put(Schema.Transaction.MONEY, fixedMoney)
                        val whereClause = Schema.Transaction.ID + " = ?"
                        val whereArgs = arrayOf(id.toString())
                        db.update(Schema.Transaction.TABLE, contentValues, whereClause, whereArgs)
                    }
                    cursor.close()
                }

                projection = arrayOf(
                    Schema.TransactionModel.ID,
                    Schema.TransactionModel.MONEY
                )
                sel = Schema.TransactionModel.WALLET + " = ?"
                cursor = db.query(Schema.TransactionModel.TABLE, projection, sel, selArgs, null, null, null)
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(cursor.getColumnIndex(Schema.TransactionModel.ID))
                        val money = cursor.getLong(cursor.getColumnIndex(Schema.TransactionModel.MONEY))
                        val fixedMoney = MoneyFormatter.normalize(money, decimalOffset)
                        val contentValues = ContentValues()
                        contentValues.put(Schema.TransactionModel.MONEY, fixedMoney)
                        val whereClause = Schema.TransactionModel.ID + " = ?"
                        val whereArgs = arrayOf(id.toString())
                        db.update(Schema.TransactionModel.TABLE, contentValues, whereClause, whereArgs)
                    }
                    cursor.close()
                }

                projection = arrayOf(
                    Schema.TransferModel.ID,
                    Schema.TransferModel.MONEY_FROM,
                    Schema.TransferModel.MONEY_TAX
                )
                sel = Schema.TransferModel.WALLET_FROM + " = ?"
                cursor = db.query(Schema.TransferModel.TABLE, projection, sel, selArgs, null, null, null)
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(cursor.getColumnIndex(Schema.TransferModel.ID))
                        val money = cursor.getLong(cursor.getColumnIndex(Schema.TransferModel.MONEY_FROM))
                        val fixedMoney = MoneyFormatter.normalize(money, decimalOffset)
                        val contentValues = ContentValues()
                        contentValues.put(Schema.TransferModel.MONEY_FROM, fixedMoney)
                        if (!cursor.isNull(cursor.getColumnIndex(Schema.TransferModel.MONEY_TAX))) {
                            val moneyTax = cursor.getLong(cursor.getColumnIndex(Schema.TransferModel.MONEY_TAX))
                            if (moneyTax > 0L) {
                                val fixedMoneyTax = MoneyFormatter.normalize(moneyTax, decimalOffset)
                                contentValues.put(Schema.TransferModel.MONEY_TAX, fixedMoneyTax)
                            }
                        }
                        val whereClause = Schema.TransferModel.ID + " = ?"
                        val whereArgs = arrayOf(id.toString())
                        db.update(Schema.TransferModel.TABLE, contentValues, whereClause, whereArgs)
                    }
                    cursor.close()
                }

                projection = arrayOf(
                    Schema.TransferModel.ID,
                    Schema.TransferModel.MONEY_TO
                )
                sel = Schema.TransferModel.WALLET_TO + " = ?"
                cursor = db.query(Schema.TransferModel.TABLE, projection, sel, selArgs, null, null, null)
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(cursor.getColumnIndex(Schema.TransferModel.ID))
                        val money = cursor.getLong(cursor.getColumnIndex(Schema.TransferModel.MONEY_TO))
                        val fixedMoney = MoneyFormatter.normalize(money, decimalOffset)
                        val contentValues = ContentValues()
                        contentValues.put(Schema.TransferModel.MONEY_TO, fixedMoney)
                        val whereClause = Schema.TransferModel.ID + " = ?"
                        val whereArgs = arrayOf(id.toString())
                        db.update(Schema.TransferModel.TABLE, contentValues, whereClause, whereArgs)
                    }
                    cursor.close()
                }

                projection = arrayOf(
                    Schema.RecurrentTransaction.ID,
                    Schema.RecurrentTransaction.MONEY
                )
                sel = Schema.RecurrentTransaction.WALLET + " = ?"
                cursor = db.query(Schema.RecurrentTransaction.TABLE, projection, sel, selArgs, null, null, null)
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(cursor.getColumnIndex(Schema.RecurrentTransaction.ID))
                        val money = cursor.getLong(cursor.getColumnIndex(Schema.RecurrentTransaction.MONEY))
                        val fixedMoney = MoneyFormatter.normalize(money, decimalOffset)
                        val contentValues = ContentValues()
                        contentValues.put(Schema.RecurrentTransaction.MONEY, fixedMoney)
                        val whereClause = Schema.RecurrentTransaction.ID + " = ?"
                        val whereArgs = arrayOf(id.toString())
                        db.update(Schema.RecurrentTransaction.TABLE, contentValues, whereClause, whereArgs)
                    }
                    cursor.close()
                }

                projection = arrayOf(
                    Schema.RecurrentTransfer.ID,
                    Schema.RecurrentTransfer.MONEY_FROM,
                    Schema.RecurrentTransfer.MONEY_TAX
                )
                sel = Schema.RecurrentTransfer.WALLET_FROM + " = ?"
                cursor = db.query(Schema.RecurrentTransfer.TABLE, projection, sel, selArgs, null, null, null)
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(cursor.getColumnIndex(Schema.RecurrentTransfer.ID))
                        val money = cursor.getLong(cursor.getColumnIndex(Schema.RecurrentTransfer.MONEY_FROM))
                        val fixedMoney = MoneyFormatter.normalize(money, decimalOffset)
                        val contentValues = ContentValues()
                        contentValues.put(Schema.RecurrentTransfer.MONEY_FROM, fixedMoney)
                        if (!cursor.isNull(cursor.getColumnIndex(Schema.RecurrentTransfer.MONEY_TAX))) {
                            val moneyTax = cursor.getLong(cursor.getColumnIndex(Schema.RecurrentTransfer.MONEY_TAX))
                            if (moneyTax > 0L) {
                                val fixedMoneyTax = MoneyFormatter.normalize(moneyTax, decimalOffset)
                                contentValues.put(Schema.RecurrentTransfer.MONEY_TAX, fixedMoneyTax)
                            }
                        }
                        val whereClause = Schema.RecurrentTransfer.ID + " = ?"
                        val whereArgs = arrayOf(id.toString())
                        db.update(Schema.RecurrentTransfer.TABLE, contentValues, whereClause, whereArgs)
                    }
                    cursor.close()
                }

                projection = arrayOf(
                    Schema.RecurrentTransfer.ID,
                    Schema.RecurrentTransfer.MONEY_TO
                )
                sel = Schema.RecurrentTransfer.WALLET_TO + " = ?"
                cursor = db.query(Schema.RecurrentTransfer.TABLE, projection, sel, selArgs, null, null, null)
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(cursor.getColumnIndex(Schema.RecurrentTransfer.ID))
                        val money = cursor.getLong(cursor.getColumnIndex(Schema.RecurrentTransfer.MONEY_TO))
                        val fixedMoney = MoneyFormatter.normalize(money, decimalOffset)
                        val contentValues = ContentValues()
                        contentValues.put(Schema.RecurrentTransfer.MONEY_TO, fixedMoney)
                        val whereClause = Schema.RecurrentTransfer.ID + " = ?"
                        val whereArgs = arrayOf(id.toString())
                        db.update(Schema.RecurrentTransfer.TABLE, contentValues, whereClause, whereArgs)
                    }
                    cursor.close()
                }
            }
            val projection = arrayOf(
                Schema.Budget.ID,
                Schema.Budget.MONEY
            )
            val sel = Schema.Budget.CURRENCY + " = ?"
            val selArgs = arrayOf(iso)
            cursor = db.query(Schema.Budget.TABLE, projection, sel, selArgs, null, null, null)
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(cursor.getColumnIndex(Schema.Budget.ID))
                    val money = cursor.getLong(cursor.getColumnIndex(Schema.Budget.MONEY))
                    val fixedMoney = MoneyFormatter.normalize(money, decimalOffset)
                    val contentValues = ContentValues()
                    contentValues.put(Schema.Budget.MONEY, fixedMoney)
                    val whereClause = Schema.Budget.ID + " = ?"
                    val whereArgs = arrayOf(id.toString())
                    db.update(Schema.Budget.TABLE, contentValues, whereClause, whereArgs)
                }
                cursor.close()
            }
        }
    }
}
