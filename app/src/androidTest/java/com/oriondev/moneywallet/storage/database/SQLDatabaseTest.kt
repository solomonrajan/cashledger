/*
 * Copyright (c) 2018.
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

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.database.ContentObserver
import android.database.Cursor
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.Attachment
import com.oriondev.moneywallet.model.ColorIcon
import com.oriondev.moneywallet.model.Money
import com.oriondev.moneywallet.utils.DateUtils
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertNotNull
import junit.framework.Assert.assertNull
import junit.framework.Assert.assertSame
import junit.framework.Assert.assertTrue
import junit.framework.Assert.fail
import org.apache.commons.io.FileUtils
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.ArrayList
import java.util.Arrays
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.BlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Created by andrea on 28/08/18.
 */
@LargeTest
class SQLDatabaseTest {

    private lateinit var context: Context
    private var database: SQLDatabase? = null
    private var realDatabaseLength: Long = 0L

    /**
     * Keeps the suite off the storage of the build it is running against. Database names are
     * prefixed and the attachment directory moves into a subdirectory, so SQLDatabase opens
     * test.database.db and deletes attachment files under a test folder, never the real ones
     * beside them. This replaces android.test.RenamingDelegatingContext, which shipped in the
     * android.test.runner library that this change removes.
     */
    private class TestStorageContext(base: Context) : ContextWrapper(base) {

        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: SQLiteDatabase.CursorFactory?
        ): SQLiteDatabase {
            return super.openOrCreateDatabase(PREFIX + name, mode, factory)
        }

        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: SQLiteDatabase.CursorFactory?,
            errorHandler: DatabaseErrorHandler?
        ): SQLiteDatabase {
            return super.openOrCreateDatabase(PREFIX + name, mode, factory, errorHandler)
        }

        override fun getDatabasePath(name: String): File {
            return super.getDatabasePath(PREFIX + name)
        }

        override fun deleteDatabase(name: String): Boolean {
            return super.deleteDatabase(PREFIX + name)
        }

        /**
         * SQLDatabase.getAttachmentFolder resolves this and the delete paths then call delete()
         * on file names taken from a column, and a restored backup can put any name in that
         * column. The folder need not exist, since nothing lists it and a delete that finds
         * nothing returns false.
         */
        override fun getExternalFilesDir(type: String?): File? {
            val external = super.getExternalFilesDir(type)
            return if (external != null) File(external, EXTERNAL_SUBDIR) else null
        }

        /**
         * Without this, SQLDatabase.getShared normalizes straight past the wrapper and the shared
         * helper opens the real ledger, so the whole shared static was untestable. ContextWrapper
         * answers this from the context it wraps, and every redirect above lives on this object.
         *
         * The cost is that the static is process wide, so a case that installs a test helper into
         * it has to put it back. releaseSharedHelper does that.
         */
        override fun getApplicationContext(): Context {
            return this
        }

        companion object {
            const val PREFIX = "test."
            const val EXTERNAL_SUBDIR = "test"
        }
    }

    @Before
    fun setUp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val testDatabase = targetContext.getDatabasePath(
            TestStorageContext.PREFIX + SQLDatabase.DATABASE_NAME
        )
        realDatabaseLength = targetContext.getDatabasePath(SQLDatabase.DATABASE_NAME).length()
        // the prefix has to name a different file, which is what everything below rests on and
        // is what a check written against the prefix itself cannot tell you
        assertFalse(
            targetContext.getDatabasePath(SQLDatabase.DATABASE_NAME).path == testDatabase.path
        )
        context = TestStorageContext(targetContext)
        assertEquals(
            testDatabase.path,
            context.getDatabasePath(SQLDatabase.DATABASE_NAME).path
        )
        // remove the previous case's database. Through the unwrapped context, so the delete every
        // case makes cannot land on the real database when the wrapper is the thing that is broken
        targetContext.deleteDatabase(TestStorageContext.PREFIX + SQLDatabase.DATABASE_NAME)
        // create a new database for testing purposes
        val db = SQLDatabase(context, SQLDatabase.DATABASE_NAME)
        database = db
        // and ask SQLDatabase which file it actually opened, whichever way it got there
        assertEquals(testDatabase.path, db.writableDatabase.path)
        // point the process wide shared helper at the same test file. These cases run inside the
        // app's own process, so its providers have already put a helper built on the real context
        // into that static, and getShared hands back whatever is there. Without this an
        // inSharedTransaction case reads and writes the real ledger and only its rollback keeps
        // that from showing. resetShared replaces it unconditionally; releaseSharedHelper puts a
        // real one back afterwards
        SQLDatabase.resetShared(context)
        assertEquals(testDatabase.path, sharedPath())
    }

    /**
     * Puts the process wide shared helper back on the real context. Any case that reached
     * getShared through the wrapper left a helper pointed at the test database in a static that
     * outlives this class. The replacement has opened nothing, since SQLiteOpenHelper opens on
     * first use, so the real ledger is not touched by putting it there.
     */
    @After
    fun releaseSharedHelper() {
        // the staged import goes first. A case that opened one and failed before closing it would
        // otherwise leave this thread writing into the staged file for every later case in the
        // run, since the redirect lives on the thread and the runner reuses it
        SQLDatabase.closeStaging()
        SQLDatabase.resetShared(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    @After
    fun tearDown() {
        val db = database ?: return
        db.close()
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        targetContext.deleteDatabase(TestStorageContext.PREFIX + SQLDatabase.DATABASE_NAME)
        targetContext.deleteDatabase(
            TestStorageContext.PREFIX + SQLDatabaseImporter.STAGING_DATABASE_NAME
        )
        // the database the build under test owns was not unlinked, and was not replaced by a
        // smaller one. Against the size setUp saw and not against zero, so a device without that
        // database reads nothing against nothing and passes instead of failing every case
        assertTrue(
            targetContext.getDatabasePath(SQLDatabase.DATABASE_NAME).length() >= realDatabaseLength
        )
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////// INTERNAL METHODS FOR TESTING ///////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////

    private fun getObjectIds(ids: Array<out Long?>?): String? {
        if (!ids.isNullOrEmpty()) {
            val builder = StringBuilder()
            for (i in ids.indices) {
                if (i != 0) {
                    builder.append(",")
                }
                builder.append(String.format(Locale.ENGLISH, "<%d>", ids[i]))
            }
            return builder.toString()
        }
        return null
    }

    private fun parseIds(list: String?): LongArray? {
        if (!TextUtils.isEmpty(list)) {
            val safeList = checkNotNull(list)
            println(safeList)
            val encodedIds = safeList.split(",")
            val ids = LongArray(encodedIds.size)
            for (i in encodedIds.indices) {
                val encodedId = encodedIds[i]
                if (encodedId.startsWith("<") && encodedId.endsWith(">")) {
                    ids[i] = encodedId.substring(1, encodedId.length - 1).toLong()
                } else {
                    val message = "The ids column not follow the pattern at index %d. Content: %s"
                    throw SQLiteException(String.format(Locale.ENGLISH, message, i, safeList))
                }
            }
            return ids
        }
        return null
    }

    private fun checkCursorMinSize(cursor: Cursor?, minSize: Int): Int {
        assertNotNull(cursor)
        val safeCursor = checkNotNull(cursor)
        val cursorSize = safeCursor.count
        assertEquals(true, cursorSize >= minSize)
        safeCursor.close()
        return cursorSize
    }

    /**
     * Rows straight from the table, with no deleted = 0 filter on the way. Every accessor this
     * class otherwise reads through carries that filter, so a row flagged as deleted and a row
     * that is gone look the same through them, and only a direct read separates the two. The
     * selection is where a caller names the flag it is asking about.
     */
    private fun assertRowCount(table: String, selection: String?, expected: Int) {
        val db = checkNotNull(database)
        val cursor = db.readableDatabase.query(table, null, selection, null, null, null, null)
        assertNotNull(cursor)
        try {
            // named, because every caller passes the same expected count and the same selection
            assertEquals(table, expected, cursor.count)
        } finally {
            cursor.close()
        }
    }

    private fun checkCursorSize(cursor: Cursor?, expectedSize: Int) {
        assertNotNull(cursor)
        val safeCursor = checkNotNull(cursor)
        if (safeCursor.count != expectedSize) {
            println("checkCursorSize is going to fail! printing cursor:")
            var rowBuilder = StringBuilder()
            rowBuilder.append(" | ")
            for (i in 0 until safeCursor.columnCount) {
                rowBuilder.append(safeCursor.getColumnName(i))
                rowBuilder.append(" | ")
            }
            println(rowBuilder)
            while (safeCursor.moveToNext()) {
                rowBuilder = StringBuilder()
                rowBuilder.append(" | ")
                for (i in 0 until safeCursor.columnCount) {
                    rowBuilder.append(safeCursor.getString(i))
                    rowBuilder.append(" | ")
                }
                println(rowBuilder)
            }
        }
        assertEquals(expectedSize, safeCursor.count)
        safeCursor.close()
    }

    private fun checkNullable(cursor: Cursor, value: Any?, index: Int) {
        assertEquals(value == null, cursor.isNull(index))
        if (!cursor.isNull(index) && value != null) {
            when (value) {
                is Long -> assertEquals(value, cursor.getLong(index))
                is Int -> assertEquals(value, cursor.getInt(index))
                is String -> assertEquals(value, cursor.getString(index))
                is Double -> assertEquals(value, cursor.getDouble(index))
            }
        }
    }

    private fun checkWalletId(
        id: Long,
        name: String,
        icon: String,
        currency: String,
        note: String?,
        countInTotal: Boolean,
        startMoney: Long,
        totalMoney: Long,
        archived: Boolean,
        tag: String?
    ) {
        val db = checkNotNull(database)
        val cursor = checkNotNull(db.getWallet(id, null))
        assertEquals(1, cursor.count)
        assertEquals(true, cursor.moveToFirst())
        assertEquals(id, cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Wallet.ID)))
        assertEquals(name, cursor.getString(cursor.getColumnIndexOrThrow(Contract.Wallet.NAME)))
        assertEquals(icon, cursor.getString(cursor.getColumnIndexOrThrow(Contract.Wallet.ICON)))
        assertEquals(currency, cursor.getString(cursor.getColumnIndexOrThrow(Contract.Wallet.CURRENCY)))
        assertEquals(note, cursor.getString(cursor.getColumnIndexOrThrow(Contract.Wallet.NOTE)))
        assertEquals(countInTotal, 1 == cursor.getInt(cursor.getColumnIndexOrThrow(Contract.Wallet.COUNT_IN_TOTAL)))
        assertEquals(startMoney, cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Wallet.START_MONEY)))
        assertEquals(archived, 1 == cursor.getInt(cursor.getColumnIndexOrThrow(Contract.Wallet.ARCHIVED)))
        assertEquals(tag, cursor.getString(cursor.getColumnIndexOrThrow(Contract.Wallet.TAG)))
        val money = Money.parse(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Wallet.TOTAL_MONEY)))
        assertEquals(totalMoney, money?.getMoney(currency) ?: 0L)
        cursor.close()
    }

    private fun insertWallet(
        name: String,
        icon: String,
        currency: String,
        note: String?,
        countInTotal: Boolean,
        startMoney: Long,
        archived: Boolean,
        tag: String?
    ): Long {
        val contentValues = ContentValues().apply {
            put(Contract.Wallet.NAME, name)
            put(Contract.Wallet.ICON, icon)
            put(Contract.Wallet.CURRENCY, currency)
            put(Contract.Wallet.NOTE, note)
            put(Contract.Wallet.COUNT_IN_TOTAL, countInTotal)
            put(Contract.Wallet.START_MONEY, startMoney)
            put(Contract.Wallet.ARCHIVED, archived)
            put(Contract.Wallet.TAG, tag)
        }
        return checkNotNull(database).insertWallet(contentValues)
    }

    private fun updateWallet(
        walletId: Long,
        name: String,
        icon: String,
        currency: String,
        note: String?,
        countInTotal: Boolean,
        startMoney: Long,
        archived: Boolean,
        tag: String?
    ): Int {
        val contentValues = ContentValues().apply {
            put(Contract.Wallet.NAME, name)
            put(Contract.Wallet.ICON, icon)
            put(Contract.Wallet.CURRENCY, currency)
            put(Contract.Wallet.NOTE, note)
            put(Contract.Wallet.COUNT_IN_TOTAL, countInTotal)
            put(Contract.Wallet.START_MONEY, startMoney)
            put(Contract.Wallet.ARCHIVED, archived)
            put(Contract.Wallet.TAG, tag)
        }
        return checkNotNull(database).updateWallet(walletId, contentValues)
    }

    private fun checkCategoryId(
        categoryId: Long,
        name: String,
        icon: String,
        type: Int,
        parentId: Long?,
        showReport: Boolean,
        tag: String?
    ) {
        val db = checkNotNull(database)
        val cursor = checkNotNull(db.getCategory(categoryId, null))
        assertEquals(1, cursor.count)
        assertEquals(true, cursor.moveToFirst())
        assertEquals(name, cursor.getString(cursor.getColumnIndexOrThrow(Contract.Category.NAME)))
        assertEquals(icon, cursor.getString(cursor.getColumnIndexOrThrow(Contract.Category.ICON)))
        assertEquals(type, cursor.getInt(cursor.getColumnIndexOrThrow(Contract.Category.TYPE)))
        checkNullable(cursor, parentId, cursor.getColumnIndexOrThrow(Contract.Category.PARENT))
        assertEquals(showReport, 1 == cursor.getInt(cursor.getColumnIndexOrThrow(Contract.Category.SHOW_REPORT)))
        assertEquals(tag, cursor.getString(cursor.getColumnIndexOrThrow(Contract.Category.TAG)))
        cursor.close()
    }

    private fun insertCategory(
        name: String,
        icon: String,
        type: Int,
        parentId: Long?,
        showReport: Boolean,
        tag: String?
    ): Long {
        val contentValues = ContentValues().apply {
            put(Contract.Category.NAME, name)
            put(Contract.Category.ICON, icon)
            put(Contract.Category.TYPE, type)
            put(Contract.Category.PARENT, parentId)
            put(Contract.Category.SHOW_REPORT, showReport)
            put(Contract.Category.TAG, tag)
        }
        return checkNotNull(database).insertCategory(contentValues)
    }

    private fun getSystemCategory(tag: String): Long {
        val projection = arrayOf(Contract.Category.ID)
        val selection = "${Contract.Category.TAG} = ?"
        val selectionArgs = arrayOf(tag)
        val db = checkNotNull(database)
        val cursor = checkNotNull(db.getCategories(projection, selection, selectionArgs, null))
        assertEquals(true, cursor.moveToFirst())
        val categoryId = cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Category.ID))
        cursor.close()
        return categoryId
    }

    private fun updateCategory(
        categoryId: Long,
        name: String,
        icon: String,
        type: Int,
        parentId: Long?,
        showReport: Boolean,
        tag: String?
    ): Int {
        val contentValues = ContentValues().apply {
            put(Contract.Category.NAME, name)
            put(Contract.Category.ICON, icon)
            put(Contract.Category.TYPE, type)
            put(Contract.Category.PARENT, parentId)
            put(Contract.Category.SHOW_REPORT, showReport)
            put(Contract.Category.TAG, tag)
        }
        return checkNotNull(database).updateCategory(categoryId, contentValues)
    }

    private fun checkEventId(
        id: Long,
        name: String,
        icon: String,
        startDate: Date,
        endDate: Date,
        note: String?,
        tag: String?
    ) {
        val db = checkNotNull(database)
        val cursor = checkNotNull(db.getEvent(id, null))
        assertEquals(1, cursor.count)
        assertEquals(true, cursor.moveToFirst())
        assertEquals(name, cursor.getString(cursor.getColumnIndexOrThrow(Contract.Event.NAME)))
        assertEquals(icon, cursor.getString(cursor.getColumnIndexOrThrow(Contract.Event.ICON)))
        assertEquals(DateUtils.getSQLDateString(startDate), cursor.getString(cursor.getColumnIndexOrThrow(Contract.Event.START_DATE)))
        assertEquals(DateUtils.getSQLDateString(endDate), cursor.getString(cursor.getColumnIndexOrThrow(Contract.Event.END_DATE)))
        assertEquals(note, cursor.getString(cursor.getColumnIndexOrThrow(Contract.Event.NOTE)))
        assertEquals(tag, cursor.getString(cursor.getColumnIndexOrThrow(Contract.Event.TAG)))
        cursor.close()
    }

    private fun insertEvent(
        name: String,
        icon: String,
        startDate: Date,
        endDate: Date,
        note: String?,
        tag: String?
    ): Long {
        val contentValues = ContentValues().apply {
            put(Contract.Event.NAME, name)
            put(Contract.Event.ICON, icon)
            put(Contract.Event.START_DATE, DateUtils.getSQLDateString(startDate))
            put(Contract.Event.END_DATE, DateUtils.getSQLDateString(endDate))
            put(Contract.Event.NOTE, note)
            put(Contract.Event.TAG, tag)
        }
        return checkNotNull(database).insertEvent(contentValues)
    }

    private fun updateEvent(
        id: Long,
        name: String,
        icon: String,
        startDate: Date,
        endDate: Date,
        note: String?,
        tag: String?
    ): Int {
        val contentValues = ContentValues().apply {
            put(Contract.Event.NAME, name)
            put(Contract.Event.ICON, icon)
            put(Contract.Event.START_DATE, DateUtils.getSQLDateString(startDate))
            put(Contract.Event.END_DATE, DateUtils.getSQLDateString(endDate))
            put(Contract.Event.NOTE, note)
            put(Contract.Event.TAG, tag)
        }
        return checkNotNull(database).updateEvent(id, contentValues)
    }

    private fun checkPlaceId(
        id: Long,
        name: String,
        icon: String,
        address: String?,
        latitude: Double?,
        longitude: Double?,
        tag: String?
    ) {
        val db = checkNotNull(database)
        val cursor = checkNotNull(db.getPlace(id, null))
        assertEquals(1, cursor.count)
        assertEquals(true, cursor.moveToFirst())
        assertEquals(name, cursor.getString(cursor.getColumnIndexOrThrow(Contract.Place.NAME)))
        assertEquals(icon, cursor.getString(cursor.getColumnIndexOrThrow(Contract.Place.ICON)))
        assertEquals(address, cursor.getString(cursor.getColumnIndexOrThrow(Contract.Place.ADDRESS)))
        checkNullable(cursor, latitude, cursor.getColumnIndexOrThrow(Contract.Place.LATITUDE))
        checkNullable(cursor, longitude, cursor.getColumnIndexOrThrow(Contract.Place.LONGITUDE))
        assertEquals(tag, cursor.getString(cursor.getColumnIndexOrThrow(Contract.Place.TAG)))
        cursor.close()
    }

    private fun insertPlace(
        name: String,
        icon: String,
        address: String?,
        latitude: Double?,
        longitude: Double?,
        tag: String?
    ): Long {
        val contentValues = ContentValues().apply {
            put(Contract.Place.NAME, name)
            put(Contract.Place.ICON, icon)
            put(Contract.Place.ADDRESS, address)
            put(Contract.Place.LATITUDE, latitude)
            put(Contract.Place.LONGITUDE, longitude)
            put(Contract.Place.TAG, tag)
        }
        return checkNotNull(database).insertPlace(contentValues)
    }

    private fun updatePlace(
        id: Long,
        name: String,
        icon: String,
        address: String?,
        latitude: Double?,
        longitude: Double?,
        tag: String?
    ): Int {
        val contentValues = ContentValues().apply {
            put(Contract.Place.NAME, name)
            put(Contract.Place.ICON, icon)
            put(Contract.Place.ADDRESS, address)
            put(Contract.Place.LATITUDE, latitude)
            put(Contract.Place.LONGITUDE, longitude)
            put(Contract.Place.TAG, tag)
        }
        return checkNotNull(database).updatePlace(id, contentValues)
    }

    private fun insertCategoryRule(pattern: String, categoryId: Long): Long {
        val contentValues = ContentValues().apply {
            put(Contract.CategoryRule.PATTERN, pattern)
            put(Contract.CategoryRule.CATEGORY_ID, categoryId)
        }
        return checkNotNull(database).insertCategoryRule(contentValues)
    }

    /**
     * The category the rules name for a description, read the way the content provider reads it.
     *
     * @param description text to match the patterns against.
     * @return the category id, or null when no rule matches.
     */
    private fun matchCategoryRule(description: String): Long? {
        val db = checkNotNull(database)
        val cursor = checkNotNull(db.getCategoryRuleMatch(description))
        var categoryId: Long? = null
        if (cursor.moveToFirst()) {
            categoryId = cursor.getLong(cursor.getColumnIndexOrThrow(Contract.CategoryRule.CATEGORY_ID))
        }
        cursor.close()
        return categoryId
    }

    private fun checkCategoryRuleId(id: Long, pattern: String, categoryId: Long, index: Int) {
        val db = checkNotNull(database)
        val cursor = checkNotNull(db.getCategoryRule(id, null))
        assertEquals(1, cursor.count)
        assertEquals(true, cursor.moveToFirst())
        assertEquals(pattern, cursor.getString(cursor.getColumnIndexOrThrow(Contract.CategoryRule.PATTERN)))
        assertEquals(categoryId, cursor.getLong(cursor.getColumnIndexOrThrow(Contract.CategoryRule.CATEGORY_ID)))
        assertEquals(index, cursor.getInt(cursor.getColumnIndexOrThrow(Contract.CategoryRule.INDEX)))
        cursor.close()
    }

    @Test
    fun categoryRulesAreAppendedInOrder() {
        val food = insertCategory("Food", "encoded-icon-food", 1, null, true, "tag-food")
        val petrol = insertCategory("Petrol", "encoded-icon-petrol", 1, null, true, "tag-petrol")
        val first = insertCategoryRule("tesco", food)
        val second = insertCategoryRule("shell", petrol)
        checkCategoryRuleId(first, "tesco", food, 0)
        checkCategoryRuleId(second, "shell", petrol, 1)
        checkCursorSize(checkNotNull(database).getCategoryRules(null, null, null, null), 2)
    }

    @Test
    fun categoryRuleMatchesDescriptionWhateverTheCase() {
        val food = insertCategory("Food", "encoded-icon-food", 1, null, true, "tag-food")
        insertCategoryRule("TeSCo", food)
        assertEquals(food, matchCategoryRule("Weekly shop at tesco extra"))
        assertEquals(food, matchCategoryRule("TESCO"))
        assertNull(matchCategoryRule("Corner shop"))
    }

    @Test
    fun categoryRuleMatchTakesTheLowestIndexThatFits() {
        val food = insertCategory("Food", "encoded-icon-food", 1, null, true, "tag-food")
        val petrol = insertCategory("Petrol", "encoded-icon-petrol", 1, null, true, "tag-petrol")
        // both patterns are in the description, and only the one appended first answers
        insertCategoryRule("tesco", food)
        insertCategoryRule("petrol", petrol)
        assertEquals(food, matchCategoryRule("tesco petrol station"))
    }

    @Test
    fun categoryRulePatternIsNotAWildcard() {
        // a percent and an underscore are wildcards under LIKE, and the lookup uses instr, so
        // they stand for themselves and match nothing else
        val food = insertCategory("Food", "encoded-icon-food", 1, null, true, "tag-food")
        insertCategoryRule("100% ju_ce", food)
        assertNull(matchCategoryRule("orange juice"))
        assertEquals(food, matchCategoryRule("Bought 100% ju_ce today"))
    }

    @Test
    fun updateCategoryRuleReplacesPatternAndCategory() {
        val food = insertCategory("Food", "encoded-icon-food", 1, null, true, "tag-food")
        val petrol = insertCategory("Petrol", "encoded-icon-petrol", 1, null, true, "tag-petrol")
        val rule = insertCategoryRule("tesco", food)
        val contentValues = ContentValues().apply {
            put(Contract.CategoryRule.PATTERN, "shell")
            put(Contract.CategoryRule.CATEGORY_ID, petrol)
        }
        assertEquals(1, checkNotNull(database).updateCategoryRule(rule, contentValues))
        checkCategoryRuleId(rule, "shell", petrol, 0)
        assertNull(matchCategoryRule("tesco extra"))
        assertEquals(petrol, matchCategoryRule("shell garage"))
    }

    @Test
    fun deleteCategoryRuleLeavesTheOthers() {
        val food = insertCategory("Food", "encoded-icon-food", 1, null, true, "tag-food")
        val petrol = insertCategory("Petrol", "encoded-icon-petrol", 1, null, true, "tag-petrol")
        val first = insertCategoryRule("tesco", food)
        insertCategoryRule("shell", petrol)
        assertEquals(1, checkNotNull(database).deleteCategoryRule(first))
        checkCursorSize(checkNotNull(database).getCategoryRules(null, null, null, null), 1)
        assertNull(matchCategoryRule("tesco extra"))
        assertEquals(petrol, matchCategoryRule("shell garage"))
    }

    @Test
    fun deletingACategoryTakesItsRulesWithIt() {
        val food = insertCategory("Food", "encoded-icon-food", 1, null, true, "tag-food")
        val petrol = insertCategory("Petrol", "encoded-icon-petrol", 1, null, true, "tag-petrol")
        insertCategoryRule("tesco", food)
        insertCategoryRule("shell", petrol)
        checkNotNull(database).deleteCategory(food)
        checkCursorSize(checkNotNull(database).getCategoryRules(null, null, null, null), 1)
        assertNull(matchCategoryRule("tesco extra"))
    }

    private fun checkPersonId(id: Long, name: String, icon: String, note: String?, tag: String?) {
        val db = checkNotNull(database)
        val cursor = checkNotNull(db.getPerson(id, null))
        assertEquals(1, cursor.count)
        assertEquals(true, cursor.moveToFirst())
        assertEquals(name, cursor.getString(cursor.getColumnIndexOrThrow(Contract.Person.NAME)))
        assertEquals(icon, cursor.getString(cursor.getColumnIndexOrThrow(Contract.Person.ICON)))
        assertEquals(note, cursor.getString(cursor.getColumnIndexOrThrow(Contract.Person.NOTE)))
        assertEquals(tag, cursor.getString(cursor.getColumnIndexOrThrow(Contract.Person.TAG)))
        cursor.close()
    }

    private fun insertPerson(name: String, icon: String, note: String?, tag: String?): Long {
        val contentValues = ContentValues().apply {
            put(Contract.Person.NAME, name)
            put(Contract.Person.ICON, icon)
            put(Contract.Person.NOTE, note)
            put(Contract.Person.TAG, tag)
        }
        return checkNotNull(database).insertPerson(contentValues)
    }

    private fun updatePerson(id: Long, name: String, icon: String, note: String?, tag: String?): Int {
        val contentValues = ContentValues().apply {
            put(Contract.Person.NAME, name)
            put(Contract.Person.ICON, icon)
            put(Contract.Person.NOTE, note)
            put(Contract.Person.TAG, tag)
        }
        return checkNotNull(database).updatePerson(id, contentValues)
    }

    private fun checkAttachmentId(
        id: Long,
        file: String,
        name: String,
        type: String,
        size: Long,
        tag: String?
    ) {
        val db = checkNotNull(database)
        val cursor = checkNotNull(db.getAttachment(id, null))
        assertEquals(1, cursor.count)
        assertEquals(true, cursor.moveToFirst())
        assertEquals(file, cursor.getString(cursor.getColumnIndexOrThrow(Contract.Attachment.FILE)))
        assertEquals(name, cursor.getString(cursor.getColumnIndexOrThrow(Contract.Attachment.NAME)))
        assertEquals(type, cursor.getString(cursor.getColumnIndexOrThrow(Contract.Attachment.TYPE)))
        assertEquals(size, cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Attachment.SIZE)))
        assertEquals(tag, cursor.getString(cursor.getColumnIndexOrThrow(Contract.Attachment.TAG)))
        cursor.close()
    }

    private fun insertAttachment(
        file: String,
        name: String,
        type: String,
        size: Long,
        tag: String?
    ): Long {
        val contentValues = ContentValues().apply {
            put(Contract.Attachment.FILE, file)
            put(Contract.Attachment.NAME, name)
            put(Contract.Attachment.TYPE, type)
            put(Contract.Attachment.SIZE, size)
            put(Contract.Attachment.TAG, tag)
        }
        return checkNotNull(database).insertAttachment(contentValues)
    }

    private fun updateAttachment(
        id: Long,
        file: String,
        name: String,
        type: String,
        size: Long,
        tag: String?
    ): Int {
        val contentValues = ContentValues().apply {
            put(Contract.Attachment.FILE, file)
            put(Contract.Attachment.NAME, name)
            put(Contract.Attachment.TYPE, type)
            put(Contract.Attachment.SIZE, size)
            put(Contract.Attachment.TAG, tag)
        }
        return checkNotNull(database).updateAttachment(id, contentValues)
    }

    private fun checkDebtId(
        id: Long,
        type: Int,
        icon: String,
        description: String,
        date: Date,
        exp: Date?,
        walletId: Long,
        note: String?,
        placeId: Long?,
        money: Long,
        archived: Boolean,
        peopleIds: Array<out Long?>?,
        tag: String?
    ) {
        val db = checkNotNull(database)
        val cursor = checkNotNull(db.getDebt(id, null))
        assertEquals(1, cursor.count)
        assertEquals(true, cursor.moveToFirst())
        assertEquals(type, cursor.getInt(cursor.getColumnIndexOrThrow(Contract.Debt.TYPE)))
        assertEquals(icon, cursor.getString(cursor.getColumnIndexOrThrow(Contract.Debt.ICON)))
        assertEquals(description, cursor.getString(cursor.getColumnIndexOrThrow(Contract.Debt.DESCRIPTION)))
        assertEquals(DateUtils.getSQLDateString(date), cursor.getString(cursor.getColumnIndexOrThrow(Contract.Debt.DATE)))
        checkNullable(cursor, exp?.let { DateUtils.getSQLDateString(it) }, cursor.getColumnIndexOrThrow(Contract.Debt.EXPIRATION_DATE))
        assertEquals(walletId, cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Debt.WALLET_ID)))
        assertEquals(note, cursor.getString(cursor.getColumnIndexOrThrow(Contract.Debt.NOTE)))
        checkNullable(cursor, placeId, cursor.getColumnIndexOrThrow(Contract.Debt.PLACE_ID))
        assertEquals(money, cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Debt.MONEY)))
        assertEquals(archived, 1 == cursor.getInt(cursor.getColumnIndexOrThrow(Contract.Debt.ARCHIVED)))
        assertEquals(getObjectIds(peopleIds), cursor.getString(cursor.getColumnIndexOrThrow(Contract.Debt.PEOPLE_IDS)))
        assertEquals(tag, cursor.getString(cursor.getColumnIndexOrThrow(Contract.Debt.TAG)))
        cursor.close()
    }

    private fun insertDebt(
        type: Int,
        icon: String,
        description: String,
        date: Date,
        exp: Date?,
        walletId: Long,
        note: String?,
        placeId: Long?,
        money: Long,
        archived: Boolean,
        peopleIds: Array<out Long?>?,
        tag: String?,
        addMasterTransaction: Boolean
    ): Long {
        val contentValues = ContentValues().apply {
            put(Contract.Debt.TYPE, type)
            put(Contract.Debt.ICON, icon)
            put(Contract.Debt.DESCRIPTION, description)
            put(Contract.Debt.DATE, DateUtils.getSQLDateString(date))
            put(Contract.Debt.EXPIRATION_DATE, exp?.let { DateUtils.getSQLDateString(it) })
            put(Contract.Debt.WALLET_ID, walletId)
            put(Contract.Debt.NOTE, note)
            put(Contract.Debt.PLACE_ID, placeId)
            put(Contract.Debt.MONEY, money)
            put(Contract.Debt.ARCHIVED, archived)
            put(Contract.Debt.PEOPLE_IDS, getObjectIds(peopleIds))
            put(Contract.Debt.TAG, tag)
            put(Contract.Debt.INSERT_MASTER_TRANSACTION, addMasterTransaction)
        }
        return checkNotNull(database).insertDebt(contentValues)
    }

    private fun updateDebt(
        id: Long,
        type: Int,
        icon: String,
        description: String,
        date: Date,
        exp: Date?,
        walletId: Long,
        note: String?,
        placeId: Long?,
        money: Long,
        archived: Boolean,
        peopleIds: Array<out Long?>?,
        tag: String?
    ): Int {
        val contentValues = ContentValues().apply {
            put(Contract.Debt.TYPE, type)
            put(Contract.Debt.ICON, icon)
            put(Contract.Debt.DESCRIPTION, description)
            put(Contract.Debt.DATE, DateUtils.getSQLDateString(date))
            put(Contract.Debt.EXPIRATION_DATE, exp?.let { DateUtils.getSQLDateString(it) })
            put(Contract.Debt.WALLET_ID, walletId)
            put(Contract.Debt.NOTE, note)
            put(Contract.Debt.PLACE_ID, placeId)
            put(Contract.Debt.MONEY, money)
            put(Contract.Debt.ARCHIVED, archived)
            put(Contract.Debt.PEOPLE_IDS, getObjectIds(peopleIds))
            put(Contract.Debt.TAG, tag)
        }
        return checkNotNull(database).updateDebt(id, contentValues)
    }

    private fun checkBudgetId(
        id: Long,
        type: Int,
        categoryId: Long?,
        startDate: Date,
        endDate: Date,
        money: Long,
        currency: String,
        walletIds: Array<out Long?>?,
        tag: String?,
        progress: Long
    ) {
        val db = checkNotNull(database)
        val cursor = checkNotNull(db.getBudget(id, null))
        assertEquals(1, cursor.count)
        assertEquals(true, cursor.moveToFirst())
        assertEquals(type, cursor.getInt(cursor.getColumnIndexOrThrow(Contract.Budget.TYPE)))
        checkNullable(cursor, categoryId, cursor.getColumnIndexOrThrow(Contract.Budget.CATEGORY_ID))
        assertEquals(DateUtils.getSQLDateString(startDate), cursor.getString(cursor.getColumnIndexOrThrow(Contract.Budget.START_DATE)))
        assertEquals(DateUtils.getSQLDateString(endDate), cursor.getString(cursor.getColumnIndexOrThrow(Contract.Budget.END_DATE)))
        assertEquals(money, cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Budget.MONEY)))
        assertEquals(currency, cursor.getString(cursor.getColumnIndexOrThrow(Contract.Budget.CURRENCY)))
        assertEquals(getObjectIds(walletIds), cursor.getString(cursor.getColumnIndexOrThrow(Contract.Budget.WALLET_IDS)))
        assertEquals(tag, cursor.getString(cursor.getColumnIndexOrThrow(Contract.Budget.TAG)))
        assertEquals(progress, cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Budget.PROGRESS)))
        cursor.close()
    }

    private fun insertBudget(
        type: Int,
        categoryId: Long?,
        startDate: Date,
        endDate: Date,
        money: Long,
        currency: String,
        walletIds: Array<out Long?>?,
        tag: String?
    ): Long {
        val contentValues = ContentValues().apply {
            put(Contract.Budget.TYPE, type)
            put(Contract.Budget.CATEGORY_ID, categoryId)
            put(Contract.Budget.START_DATE, DateUtils.getSQLDateString(startDate))
            put(Contract.Budget.END_DATE, DateUtils.getSQLDateString(endDate))
            put(Contract.Budget.MONEY, money)
            put(Contract.Budget.CURRENCY, currency)
            put(Contract.Budget.WALLET_IDS, getObjectIds(walletIds))
            put(Contract.Budget.TAG, tag)
        }
        return checkNotNull(database).insertBudget(contentValues)
    }

    private fun updateBudget(
        id: Long,
        type: Int,
        categoryId: Long?,
        startDate: Date,
        endDate: Date,
        money: Long,
        currency: String,
        walletIds: Array<out Long?>?,
        tag: String?
    ): Int {
        val contentValues = ContentValues().apply {
            put(Contract.Budget.TYPE, type)
            put(Contract.Budget.CATEGORY_ID, categoryId)
            put(Contract.Budget.START_DATE, DateUtils.getSQLDateString(startDate))
            put(Contract.Budget.END_DATE, DateUtils.getSQLDateString(endDate))
            put(Contract.Budget.MONEY, money)
            put(Contract.Budget.CURRENCY, currency)
            put(Contract.Budget.WALLET_IDS, getObjectIds(walletIds))
            put(Contract.Budget.TAG, tag)
        }
        return checkNotNull(database).updateBudget(id, contentValues)
    }

    private fun insertBudgetCovering(
        type: Int,
        categoryIds: Array<out Long?>?,
        startDate: Date,
        endDate: Date,
        money: Long,
        currency: String,
        walletIds: Array<out Long?>?,
        tag: String?
    ): Long {
        val contentValues = ContentValues().apply {
            put(Contract.Budget.TYPE, type)
            put(Contract.Budget.CATEGORY_IDS, getObjectIds(categoryIds))
            put(Contract.Budget.START_DATE, DateUtils.getSQLDateString(startDate))
            put(Contract.Budget.END_DATE, DateUtils.getSQLDateString(endDate))
            put(Contract.Budget.MONEY, money)
            put(Contract.Budget.CURRENCY, currency)
            put(Contract.Budget.WALLET_IDS, getObjectIds(walletIds))
            put(Contract.Budget.TAG, tag)
        }
        return checkNotNull(database).insertBudget(contentValues)
    }

    private fun updateBudgetCovering(
        id: Long,
        type: Int,
        categoryIds: Array<out Long?>?,
        startDate: Date,
        endDate: Date,
        money: Long,
        currency: String,
        walletIds: Array<out Long?>?,
        tag: String?
    ): Int {
        val contentValues = ContentValues().apply {
            put(Contract.Budget.TYPE, type)
            put(Contract.Budget.CATEGORY_IDS, getObjectIds(categoryIds))
            put(Contract.Budget.START_DATE, DateUtils.getSQLDateString(startDate))
            put(Contract.Budget.END_DATE, DateUtils.getSQLDateString(endDate))
            put(Contract.Budget.MONEY, money)
            put(Contract.Budget.CURRENCY, currency)
            put(Contract.Budget.WALLET_IDS, getObjectIds(walletIds))
            put(Contract.Budget.TAG, tag)
        }
        return checkNotNull(database).updateBudget(id, contentValues)
    }

    /**
     * Reads back what a budget covers, both the way the editor reads it and the way the list row
     * shows it.
     *
     * @param id of the budget.
     * @param categoryIds every category it should cover, lowest id first, which is the order
     *                    a query hands them over in.
     * @param joinedNames the names the list row should show, joined the way the query joins them.
     * @param progress the figure the budget should now be at.
     */
    private fun checkBudgetCategories(
        id: Long,
        categoryIds: Array<out Long>,
        joinedNames: String,
        progress: Long
    ) {
        val db = checkNotNull(database)
        val cursor = checkNotNull(db.getBudget(id, null))
        assertEquals(1, cursor.count)
        assertEquals(true, cursor.moveToFirst())
        assertEquals(getObjectIds(categoryIds), cursor.getString(cursor.getColumnIndexOrThrow(Contract.Budget.CATEGORY_IDS)))
        assertEquals(joinedNames, cursor.getString(cursor.getColumnIndexOrThrow(Contract.Budget.CATEGORY_NAME)))
        assertEquals(categoryIds[0], cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Budget.CATEGORY_ID)))
        assertEquals(progress, cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Budget.PROGRESS)))
        cursor.close()
    }

    private fun checkSavingId(
        id: Long,
        description: String,
        icon: String,
        startMoney: Long,
        endMoney: Long,
        walletId: Long,
        exp: Date?,
        completed: Boolean,
        note: String?,
        tag: String?
    ) {
        val db = checkNotNull(database)
        val cursor = checkNotNull(db.getSaving(id, null))
        assertEquals(1, cursor.count)
        assertEquals(true, cursor.moveToFirst())
        assertEquals(description, cursor.getString(cursor.getColumnIndexOrThrow(Contract.Saving.DESCRIPTION)))
        assertEquals(icon, cursor.getString(cursor.getColumnIndexOrThrow(Contract.Saving.ICON)))
        assertEquals(startMoney, cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Saving.START_MONEY)))
        assertEquals(endMoney, cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Saving.END_MONEY)))
        assertEquals(walletId, cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Saving.WALLET_ID)))
        checkNullable(cursor, exp?.let { DateUtils.getSQLDateString(it) }, cursor.getColumnIndexOrThrow(Contract.Saving.END_DATE))
        assertEquals(completed, 1 == cursor.getInt(cursor.getColumnIndexOrThrow(Contract.Saving.COMPLETE)))
        assertEquals(note, cursor.getString(cursor.getColumnIndexOrThrow(Contract.Saving.NOTE)))
        assertEquals(tag, cursor.getString(cursor.getColumnIndexOrThrow(Contract.Saving.TAG)))
        cursor.close()
    }

    private fun insertSaving(
        description: String,
        icon: String,
        startMoney: Long,
        endMoney: Long,
        walletId: Long,
        exp: Date?,
        completed: Boolean,
        note: String?,
        tag: String?
    ): Long {
        val contentValues = ContentValues().apply {
            put(Contract.Saving.DESCRIPTION, description)
            put(Contract.Saving.ICON, icon)
            put(Contract.Saving.START_MONEY, startMoney)
            put(Contract.Saving.END_MONEY, endMoney)
            put(Contract.Saving.WALLET_ID, walletId)
            put(Contract.Saving.END_DATE, exp?.let { DateUtils.getSQLDateString(it) })
            put(Contract.Saving.COMPLETE, completed)
            put(Contract.Saving.NOTE, note)
            put(Contract.Saving.TAG, tag)
        }
        return checkNotNull(database).insertSaving(contentValues)
    }

    private fun updateSaving(
        id: Long,
        description: String,
        icon: String,
        startMoney: Long,
        endMoney: Long,
        walletId: Long,
        exp: Date?,
        completed: Boolean,
        note: String?,
        tag: String?
    ): Int {
        val contentValues = ContentValues().apply {
            put(Contract.Saving.DESCRIPTION, description)
            put(Contract.Saving.ICON, icon)
            put(Contract.Saving.START_MONEY, startMoney)
            put(Contract.Saving.END_MONEY, endMoney)
            put(Contract.Saving.WALLET_ID, walletId)
            put(Contract.Saving.END_DATE, exp?.let { DateUtils.getSQLDateString(it) })
            put(Contract.Saving.COMPLETE, completed)
            put(Contract.Saving.NOTE, note)
            put(Contract.Saving.TAG, tag)
        }
        return checkNotNull(database).updateSaving(id, contentValues)
    }

    private fun checkTransactionId(
        id: Long,
        money: Long,
        datetime: Date,
        description: String?,
        categoryId: Long,
        direction: Int,
        type: Int,
        walletId: Long,
        placeId: Long?,
        note: String?,
        eventId: Long?,
        savingId: Long?,
        debtId: Long?,
        confirmed: Boolean,
        countInTotal: Boolean,
        peopleIds: Array<out Long?>?,
        tag: String?
    ) {
        val db = checkNotNull(database)
        val cursor = checkNotNull(db.getTransaction(id, null))
        assertEquals(1, cursor.count)
        assertEquals(true, cursor.moveToFirst())
        assertEquals(money, cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Transaction.MONEY)))
        assertEquals(DateUtils.getSQLDateTimeString(datetime), cursor.getString(cursor.getColumnIndexOrThrow(Contract.Transaction.DATE)))
        assertEquals(description, cursor.getString(cursor.getColumnIndexOrThrow(Contract.Transaction.DESCRIPTION)))
        assertEquals(categoryId, cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Transaction.CATEGORY_ID)))
        assertEquals(direction, cursor.getInt(cursor.getColumnIndexOrThrow(Contract.Transaction.DIRECTION)))
        assertEquals(type, cursor.getInt(cursor.getColumnIndexOrThrow(Contract.Transaction.TYPE)))
        assertEquals(walletId, cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Transaction.WALLET_ID)))
        checkNullable(cursor, placeId, cursor.getColumnIndexOrThrow(Contract.Transaction.PLACE_ID))
        assertEquals(note, cursor.getString(cursor.getColumnIndexOrThrow(Contract.Transaction.NOTE)))
        checkNullable(cursor, eventId, cursor.getColumnIndexOrThrow(Contract.Transaction.EVENT_ID))
        checkNullable(cursor, savingId, cursor.getColumnIndexOrThrow(Contract.Transaction.SAVING_ID))
        checkNullable(cursor, debtId, cursor.getColumnIndexOrThrow(Contract.Transaction.DEBT_ID))
        assertEquals(confirmed, 1 == cursor.getInt(cursor.getColumnIndexOrThrow(Contract.Transaction.CONFIRMED)))
        assertEquals(countInTotal, 1 == cursor.getInt(cursor.getColumnIndexOrThrow(Contract.Transaction.COUNT_IN_TOTAL)))
        assertEquals(getObjectIds(peopleIds), cursor.getString(cursor.getColumnIndexOrThrow(Contract.Transaction.PEOPLE_IDS)))
        assertEquals(tag, cursor.getString(cursor.getColumnIndexOrThrow(Contract.Transaction.TAG)))
        cursor.close()
    }

    private fun insertTransaction(
        money: Long,
        datetime: Date,
        description: String?,
        categoryId: Long,
        direction: Int,
        type: Int,
        walletId: Long,
        placeId: Long?,
        note: String?,
        eventId: Long?,
        savingId: Long?,
        debtId: Long?,
        confirmed: Boolean,
        countInTotal: Boolean,
        peopleIds: Array<out Long?>?,
        attachmentIds: Array<out Long?>?,
        tag: String?
    ): Long {
        val contentValues = ContentValues().apply {
            put(Contract.Transaction.MONEY, money)
            put(Contract.Transaction.DATE, DateUtils.getSQLDateTimeString(datetime))
            put(Contract.Transaction.DESCRIPTION, description)
            put(Contract.Transaction.CATEGORY_ID, categoryId)
            put(Contract.Transaction.DIRECTION, direction)
            put(Contract.Transaction.TYPE, type)
            put(Contract.Transaction.WALLET_ID, walletId)
            put(Contract.Transaction.PLACE_ID, placeId)
            put(Contract.Transaction.NOTE, note)
            put(Contract.Transaction.EVENT_ID, eventId)
            put(Contract.Transaction.SAVING_ID, savingId)
            put(Contract.Transaction.DEBT_ID, debtId)
            put(Contract.Transaction.CONFIRMED, confirmed)
            put(Contract.Transaction.COUNT_IN_TOTAL, countInTotal)
            put(Contract.Transaction.PEOPLE_IDS, getObjectIds(peopleIds))
            put(Contract.Transaction.ATTACHMENT_IDS, getObjectIds(attachmentIds))
            put(Contract.Transaction.TAG, tag)
        }
        return checkNotNull(database).insertTransaction(contentValues)
    }

    private fun updateTransaction(
        id: Long,
        money: Long,
        datetime: Date,
        description: String?,
        categoryId: Long,
        direction: Int,
        type: Int,
        walletId: Long,
        placeId: Long?,
        note: String?,
        eventId: Long?,
        savingId: Long?,
        debtId: Long?,
        confirmed: Boolean,
        countInTotal: Boolean,
        peopleIds: Array<out Long?>?,
        attachmentIds: Array<out Long?>?,
        tag: String?
    ): Int {
        val contentValues = ContentValues().apply {
            put(Contract.Transaction.MONEY, money)
            put(Contract.Transaction.DATE, DateUtils.getSQLDateTimeString(datetime))
            put(Contract.Transaction.DESCRIPTION, description)
            put(Contract.Transaction.CATEGORY_ID, categoryId)
            put(Contract.Transaction.DIRECTION, direction)
            put(Contract.Transaction.TYPE, type)
            put(Contract.Transaction.WALLET_ID, walletId)
            put(Contract.Transaction.PLACE_ID, placeId)
            put(Contract.Transaction.NOTE, note)
            put(Contract.Transaction.EVENT_ID, eventId)
            put(Contract.Transaction.SAVING_ID, savingId)
            put(Contract.Transaction.DEBT_ID, debtId)
            put(Contract.Transaction.CONFIRMED, confirmed)
            put(Contract.Transaction.COUNT_IN_TOTAL, countInTotal)
            put(Contract.Transaction.PEOPLE_IDS, getObjectIds(peopleIds))
            put(Contract.Transaction.ATTACHMENT_IDS, getObjectIds(attachmentIds))
            put(Contract.Transaction.TAG, tag)
        }
        return checkNotNull(database).updateTransaction(id, contentValues)
    }

    private fun checkTransferId(
        id: Long,
        description: String?,
        datetime: Date,
        walletFromId: Long,
        walletToId: Long,
        walletTaxId: Long?,
        moneyFrom: Long,
        moneyTo: Long,
        moneyTax: Long,
        note: String?,
        placeId: Long?,
        eventId: Long?,
        confirmed: Boolean,
        countInTotal: Boolean,
        peopleIds: Array<out Long?>?,
        tag: String?
    ) {
        val db = checkNotNull(database)
        val cursor = checkNotNull(db.getTransfer(id, null))
        assertEquals(1, cursor.count)
        assertEquals(true, cursor.moveToFirst())
        assertEquals(description, cursor.getString(cursor.getColumnIndexOrThrow(Contract.Transfer.DESCRIPTION)))
        assertEquals(DateUtils.getSQLDateTimeString(datetime), cursor.getString(cursor.getColumnIndexOrThrow(Contract.Transfer.DATE)))
        assertEquals(walletFromId, cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Transfer.TRANSACTION_FROM_WALLET_ID)))
        assertEquals(walletToId, cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Transfer.TRANSACTION_TO_WALLET_ID)))
        checkNullable(cursor, walletTaxId, cursor.getColumnIndexOrThrow(Contract.Transfer.TRANSACTION_TAX_WALLET_ID))
        assertEquals(moneyFrom, cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Transfer.TRANSACTION_FROM_MONEY)))
        assertEquals(moneyTo, cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Transfer.TRANSACTION_TO_MONEY)))
        assertEquals(moneyTax, cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Transfer.TRANSACTION_TAX_MONEY)))
        assertEquals(note, cursor.getString(cursor.getColumnIndexOrThrow(Contract.Transfer.NOTE)))
        checkNullable(cursor, placeId, cursor.getColumnIndexOrThrow(Contract.Transfer.PLACE_ID))
        checkNullable(cursor, eventId, cursor.getColumnIndexOrThrow(Contract.Transfer.EVENT_ID))
        assertEquals(confirmed, 1 == cursor.getInt(cursor.getColumnIndexOrThrow(Contract.Transfer.CONFIRMED)))
        assertEquals(countInTotal, 1 == cursor.getInt(cursor.getColumnIndexOrThrow(Contract.Transfer.COUNT_IN_TOTAL)))
        assertEquals(getObjectIds(peopleIds), cursor.getString(cursor.getColumnIndexOrThrow(Contract.Transfer.PEOPLE_IDS)))
        assertEquals(tag, cursor.getString(cursor.getColumnIndexOrThrow(Contract.Transfer.TAG)))
        cursor.close()
    }

    private fun insertTransfer(
        description: String?,
        datetime: Date,
        walletFromId: Long,
        walletToId: Long,
        walletTaxId: Long?,
        moneyFrom: Long,
        moneyTo: Long,
        moneyTax: Long,
        note: String?,
        placeId: Long?,
        eventId: Long?,
        confirmed: Boolean,
        countInTotal: Boolean,
        peopleIds: Array<out Long?>?,
        attachmentIds: Array<out Long?>?,
        tag: String?
    ): Long {
        val contentValues = ContentValues().apply {
            put(Contract.Transfer.DESCRIPTION, description)
            put(Contract.Transfer.DATE, DateUtils.getSQLDateTimeString(datetime))
            put(Contract.Transfer.TRANSACTION_FROM_WALLET_ID, walletFromId)
            put(Contract.Transfer.TRANSACTION_TO_WALLET_ID, walletToId)
            put(Contract.Transfer.TRANSACTION_TAX_WALLET_ID, walletTaxId)
            put(Contract.Transfer.TRANSACTION_FROM_MONEY, moneyFrom)
            put(Contract.Transfer.TRANSACTION_TO_MONEY, moneyTo)
            put(Contract.Transfer.TRANSACTION_TAX_MONEY, moneyTax)
            put(Contract.Transfer.NOTE, note)
            put(Contract.Transfer.PLACE_ID, placeId)
            put(Contract.Transfer.EVENT_ID, eventId)
            put(Contract.Transfer.CONFIRMED, confirmed)
            put(Contract.Transfer.COUNT_IN_TOTAL, countInTotal)
            put(Contract.Transfer.PEOPLE_IDS, getObjectIds(peopleIds))
            put(Contract.Transfer.ATTACHMENT_IDS, getObjectIds(attachmentIds))
            put(Contract.Transfer.TAG, tag)
        }
        return checkNotNull(database).insertTransfer(contentValues)
    }

    private fun updateTransfer(
        id: Long,
        description: String?,
        datetime: Date,
        walletFromId: Long,
        walletToId: Long,
        walletTaxId: Long?,
        moneyFrom: Long,
        moneyTo: Long,
        moneyTax: Long,
        note: String?,
        placeId: Long?,
        eventId: Long?,
        confirmed: Boolean,
        countInTotal: Boolean,
        peopleIds: Array<out Long?>?,
        attachmentIds: Array<out Long?>?,
        tag: String?
    ): Int {
        val contentValues = ContentValues().apply {
            put(Contract.Transfer.DESCRIPTION, description)
            put(Contract.Transfer.DATE, DateUtils.getSQLDateTimeString(datetime))
            put(Contract.Transfer.TRANSACTION_FROM_WALLET_ID, walletFromId)
            put(Contract.Transfer.TRANSACTION_TO_WALLET_ID, walletToId)
            put(Contract.Transfer.TRANSACTION_TAX_WALLET_ID, walletTaxId)
            put(Contract.Transfer.TRANSACTION_FROM_MONEY, moneyFrom)
            put(Contract.Transfer.TRANSACTION_TO_MONEY, moneyTo)
            put(Contract.Transfer.TRANSACTION_TAX_MONEY, moneyTax)
            put(Contract.Transfer.NOTE, note)
            put(Contract.Transfer.PLACE_ID, placeId)
            put(Contract.Transfer.EVENT_ID, eventId)
            put(Contract.Transfer.CONFIRMED, confirmed)
            put(Contract.Transfer.COUNT_IN_TOTAL, countInTotal)
            put(Contract.Transfer.PEOPLE_IDS, getObjectIds(peopleIds))
            put(Contract.Transfer.ATTACHMENT_IDS, getObjectIds(attachmentIds))
            put(Contract.Transfer.TAG, tag)
        }
        return checkNotNull(database).updateTransfer(id, contentValues)
    }

    private fun checkTransactionModelId(
        id: Long,
        money: Long,
        description: String?,
        categoryId: Long,
        direction: Int,
        walletId: Long,
        placeId: Long?,
        note: String?,
        eventId: Long?,
        confirmed: Boolean,
        countInTotal: Boolean,
        tag: String?
    ) {
        val db = checkNotNull(database)
        val cursor = checkNotNull(db.getTransactionModel(id, null))
        assertEquals(1, cursor.count)
        assertEquals(true, cursor.moveToFirst())
        assertEquals(money, cursor.getLong(cursor.getColumnIndexOrThrow(Contract.TransactionModel.MONEY)))
        assertEquals(description, cursor.getString(cursor.getColumnIndexOrThrow(Contract.TransactionModel.DESCRIPTION)))
        assertEquals(categoryId, cursor.getLong(cursor.getColumnIndexOrThrow(Contract.TransactionModel.CATEGORY_ID)))
        assertEquals(direction, cursor.getInt(cursor.getColumnIndexOrThrow(Contract.TransactionModel.DIRECTION)))
        assertEquals(walletId, cursor.getLong(cursor.getColumnIndexOrThrow(Contract.TransactionModel.WALLET_ID)))
        checkNullable(cursor, placeId, cursor.getColumnIndexOrThrow(Contract.TransactionModel.PLACE_ID))
        assertEquals(note, cursor.getString(cursor.getColumnIndexOrThrow(Contract.TransactionModel.NOTE)))
        checkNullable(cursor, eventId, cursor.getColumnIndexOrThrow(Contract.TransactionModel.EVENT_ID))
        assertEquals(confirmed, 1 == cursor.getInt(cursor.getColumnIndexOrThrow(Contract.TransactionModel.CONFIRMED)))
        assertEquals(countInTotal, 1 == cursor.getInt(cursor.getColumnIndexOrThrow(Contract.TransactionModel.COUNT_IN_TOTAL)))
        assertEquals(tag, cursor.getString(cursor.getColumnIndexOrThrow(Contract.TransactionModel.TAG)))
        cursor.close()
    }

    private fun insertTransactionModel(
        money: Long,
        description: String?,
        categoryId: Long,
        direction: Int,
        walletId: Long,
        placeId: Long?,
        note: String?,
        eventId: Long?,
        confirmed: Boolean,
        countInTotal: Boolean,
        tag: String?
    ): Long {
        val contentValues = ContentValues().apply {
            put(Contract.TransactionModel.MONEY, money)
            put(Contract.TransactionModel.DESCRIPTION, description)
            put(Contract.TransactionModel.CATEGORY_ID, categoryId)
            put(Contract.TransactionModel.DIRECTION, direction)
            put(Contract.TransactionModel.WALLET_ID, walletId)
            put(Contract.TransactionModel.PLACE_ID, placeId)
            put(Contract.TransactionModel.EVENT_ID, eventId)
            put(Contract.TransactionModel.NOTE, note)
            put(Contract.TransactionModel.CONFIRMED, confirmed)
            put(Contract.TransactionModel.COUNT_IN_TOTAL, countInTotal)
            put(Contract.TransactionModel.TAG, tag)
        }
        return checkNotNull(database).insertTransactionModel(contentValues)
    }

    private fun updateTransactionModel(
        id: Long,
        money: Long,
        description: String?,
        categoryId: Long,
        direction: Int,
        walletId: Long,
        placeId: Long?,
        note: String?,
        eventId: Long?,
        confirmed: Boolean,
        countInTotal: Boolean,
        tag: String?
    ): Int {
        val contentValues = ContentValues().apply {
            put(Contract.TransactionModel.MONEY, money)
            put(Contract.TransactionModel.DESCRIPTION, description)
            put(Contract.TransactionModel.CATEGORY_ID, categoryId)
            put(Contract.TransactionModel.DIRECTION, direction)
            put(Contract.TransactionModel.WALLET_ID, walletId)
            put(Contract.TransactionModel.PLACE_ID, placeId)
            put(Contract.TransactionModel.EVENT_ID, eventId)
            put(Contract.TransactionModel.NOTE, note)
            put(Contract.TransactionModel.CONFIRMED, confirmed)
            put(Contract.TransactionModel.COUNT_IN_TOTAL, countInTotal)
            put(Contract.TransactionModel.TAG, tag)
        }
        return checkNotNull(database).updateTransactionModel(id, contentValues)
    }

    private fun checkTransferModelId(
        id: Long,
        description: String?,
        walletFromId: Long,
        walletToId: Long,
        moneyFrom: Long,
        moneyTo: Long,
        moneyTax: Long,
        note: String?,
        placeId: Long?,
        eventId: Long?,
        confirmed: Boolean,
        countInTotal: Boolean,
        tag: String?
    ) {
        val db = checkNotNull(database)
        val cursor = checkNotNull(db.getTransferModel(id, null))
        assertEquals(1, cursor.count)
        assertEquals(true, cursor.moveToFirst())
        assertEquals(description, cursor.getString(cursor.getColumnIndexOrThrow(Contract.TransferModel.DESCRIPTION)))
        assertEquals(walletFromId, cursor.getLong(cursor.getColumnIndexOrThrow(Contract.TransferModel.WALLET_FROM_ID)))
        assertEquals(walletToId, cursor.getLong(cursor.getColumnIndexOrThrow(Contract.TransferModel.WALLET_TO_ID)))
        assertEquals(moneyFrom, cursor.getLong(cursor.getColumnIndexOrThrow(Contract.TransferModel.MONEY_FROM)))
        assertEquals(moneyTo, cursor.getLong(cursor.getColumnIndexOrThrow(Contract.TransferModel.MONEY_TO)))
        assertEquals(moneyTax, cursor.getLong(cursor.getColumnIndexOrThrow(Contract.TransferModel.MONEY_TAX)))
        assertEquals(note, cursor.getString(cursor.getColumnIndexOrThrow(Contract.TransferModel.NOTE)))
        checkNullable(cursor, placeId, cursor.getColumnIndexOrThrow(Contract.TransferModel.PLACE_ID))
        checkNullable(cursor, eventId, cursor.getColumnIndexOrThrow(Contract.TransferModel.EVENT_ID))
        assertEquals(confirmed, 1 == cursor.getInt(cursor.getColumnIndexOrThrow(Contract.TransferModel.CONFIRMED)))
        assertEquals(countInTotal, 1 == cursor.getInt(cursor.getColumnIndexOrThrow(Contract.TransferModel.COUNT_IN_TOTAL)))
        assertEquals(tag, cursor.getString(cursor.getColumnIndexOrThrow(Contract.TransferModel.TAG)))
        cursor.close()
    }

    private fun insertTransferModel(
        description: String?,
        walletFromId: Long,
        walletToId: Long,
        moneyFrom: Long,
        moneyTo: Long,
        moneyTax: Long,
        note: String?,
        placeId: Long?,
        eventId: Long?,
        confirmed: Boolean,
        countInTotal: Boolean,
        tag: String?
    ): Long {
        val contentValues = ContentValues().apply {
            put(Contract.TransferModel.DESCRIPTION, description)
            put(Contract.TransferModel.WALLET_FROM_ID, walletFromId)
            put(Contract.TransferModel.WALLET_TO_ID, walletToId)
            put(Contract.TransferModel.MONEY_FROM, moneyFrom)
            put(Contract.TransferModel.MONEY_TO, moneyTo)
            put(Contract.TransferModel.MONEY_TAX, moneyTax)
            put(Contract.TransferModel.NOTE, note)
            put(Contract.TransferModel.PLACE_ID, placeId)
            put(Contract.TransferModel.EVENT_ID, eventId)
            put(Contract.TransferModel.CONFIRMED, confirmed)
            put(Contract.TransferModel.COUNT_IN_TOTAL, countInTotal)
            put(Contract.TransferModel.TAG, tag)
        }
        return checkNotNull(database).insertTransferModel(contentValues)
    }

    private fun updateTransferModel(
        id: Long,
        description: String?,
        walletFromId: Long,
        walletToId: Long,
        moneyFrom: Long,
        moneyTo: Long,
        moneyTax: Long,
        note: String?,
        placeId: Long?,
        eventId: Long?,
        confirmed: Boolean,
        countInTotal: Boolean,
        tag: String?
    ): Int {
        val contentValues = ContentValues().apply {
            put(Contract.TransferModel.DESCRIPTION, description)
            put(Contract.TransferModel.WALLET_FROM_ID, walletFromId)
            put(Contract.TransferModel.WALLET_TO_ID, walletToId)
            put(Contract.TransferModel.MONEY_FROM, moneyFrom)
            put(Contract.TransferModel.MONEY_TO, moneyTo)
            put(Contract.TransferModel.MONEY_TAX, moneyTax)
            put(Contract.TransferModel.NOTE, note)
            put(Contract.TransferModel.PLACE_ID, placeId)
            put(Contract.TransferModel.EVENT_ID, eventId)
            put(Contract.TransferModel.CONFIRMED, confirmed)
            put(Contract.TransferModel.COUNT_IN_TOTAL, countInTotal)
            put(Contract.TransferModel.TAG, tag)
        }
        return checkNotNull(database).updateTransferModel(id, contentValues)
    }

    /////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////// START THE TEST /////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Covers the wrapper's other three database methods, which nothing else in here reaches on
     * the runners this suite is run on. Without this they are three overrides whose loss nothing
     * would notice.
     */
    @Test
    fun everyDatabaseMethodOnTheWrapperIsPrefixed() {
        val expected = InstrumentationRegistry.getInstrumentation().targetContext
            .getDatabasePath(TestStorageContext.PREFIX + SQLDatabase.DATABASE_NAME)
        // closed first, so the delete at the end is not unlinking a file still open here
        checkNotNull(database).close()
        val threeArgument = context.openOrCreateDatabase(
            SQLDatabase.DATABASE_NAME, Context.MODE_PRIVATE, null
        )
        try {
            assertEquals(expected.path, threeArgument.path)
        } finally {
            threeArgument.close()
        }
        val fourArgument = context.openOrCreateDatabase(
            SQLDatabase.DATABASE_NAME, Context.MODE_PRIVATE, null, null
        )
        try {
            assertEquals(expected.path, fourArgument.path)
        } finally {
            fourArgument.close()
        }
        context.deleteDatabase(SQLDatabase.DATABASE_NAME)
        assertFalse(expected.exists())
    }

    /**
     * Proves the attachment deletes land in the wrapper's folder. The file is made under the
     * wrapper's own external directory, so nothing here writes into the app's attachments, and it
     * has to be gone afterwards.
     */
    @Test
    fun attachmentFileIsDeletedUnderTheTestFolder() {
        // built from the unwrapped context, never from the wrapper, or an emptied EXTERNAL_SUBDIR
        // would move this file and the delete together and they would still agree
        val external = checkNotNull(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
        )
        // same reason as the prefix check in setUp, and before anything is written below
        assertFalse(external == context.getExternalFilesDir(null))
        val folder = File(File(external, TestStorageContext.EXTERNAL_SUBDIR), Attachment.FOLDER_NAME)
        assertTrue(folder.isDirectory || folder.mkdirs())
        val sentinel = File(folder, "path1")
        assertTrue(sentinel.isFile || sentinel.createNewFile())
        val id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val id2 = insertCategory("Test category 1", "encoded-icon-1", Contract.CategoryType.INCOME.value, null, true, "tag-category-1")
        val id3 = insertAttachment("path1", "name-1", "mime-type-1", 90L, "tag-1")
        val id4 = insertTransaction(2000L, Date(), "desc", id2, Contract.Direction.INCOME, 0, id1, null, "note", null, null, null, true, true, null, arrayOf(id3), "tag")
        checkNotNull(database).deleteTransaction(id4)
        assertFalse(sentinel.exists())
    }

    @Test
    fun insertWallet() {
        // insert 4 wallets and then query for each one and check the returned id
        val id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val id2 = insertWallet("Test wallet 2", "encoded-icon-2", "USD", "note-wallet-2", true, 3000L, true, "tag-wallet-2")
        val id3 = insertWallet("Test wallet 3", "encoded-icon-3", "EUR", "note-wallet-3", true, 1000L, false, "tag-wallet-3")
        val id4 = insertWallet("Test wallet 4", "encoded-icon-4", "USD", "note-wallet-4", false, 7500L, false, "tag-wallet-4")
        // now query each wallet and check that everything is ok
        checkWalletId(id1, "Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, 0L, false, "tag-wallet-1")
        checkWalletId(id2, "Test wallet 2", "encoded-icon-2", "USD", "note-wallet-2", true, 3000L, 0L, true, "tag-wallet-2")
        checkWalletId(id3, "Test wallet 3", "encoded-icon-3", "EUR", "note-wallet-3", true, 1000L, 0L, false, "tag-wallet-3")
        checkWalletId(id4, "Test wallet 4", "encoded-icon-4", "USD", "note-wallet-4", false, 7500L, 0L, false, "tag-wallet-4")
    }

    @Test
    fun updateWallet() {
        // insert 4 wallets and then query for each one and check the returned id
        val id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val id2 = insertWallet("Test wallet 2", "encoded-icon-2", "USD", "note-wallet-2", true, 3000L, true, "tag-wallet-2")
        val id3 = insertWallet("Test wallet 3", "encoded-icon-3", "EUR", "note-wallet-3", true, 1000L, false, "tag-wallet-3")
        val id4 = insertWallet("Test wallet 4", "encoded-icon-4", "USD", "note-wallet-4", false, 7500L, false, "tag-wallet-4")
        // now modify each wallet
        assertEquals(1, updateWallet(id1, "Test wallet 1-edited", "encoded-icon-1-edited", "USD", "note-wallet-1-edited", false, 4000L, true, "tag-wallet-1-edited"))
        assertEquals(1, updateWallet(id2, "Test wallet 2-edited", "encoded-icon-2-edited", "EUR", "note-wallet-2-edited", true, 3500L, true, "tag-wallet-2-edited"))
        assertEquals(1, updateWallet(id3, "Test wallet 3-edited", "encoded-icon-3-edited", "USD", "note-wallet-3-edited", false, 500L, false, "tag-wallet-3-edited"))
        assertEquals(1, updateWallet(id4, "Test wallet 4-edited", "encoded-icon-4-edited", "EUR", "note-wallet-4-edited", true, 7800L, false, "tag-wallet-4-edited"))
        // now check that the values are changed
        checkWalletId(id1, "Test wallet 1-edited", "encoded-icon-1-edited", "USD", "note-wallet-1-edited", false, 4000L, 0L, true, "tag-wallet-1-edited")
        checkWalletId(id2, "Test wallet 2-edited", "encoded-icon-2-edited", "EUR", "note-wallet-2-edited", true, 3500L, 0L, true, "tag-wallet-2-edited")
        checkWalletId(id3, "Test wallet 3-edited", "encoded-icon-3-edited", "USD", "note-wallet-3-edited", false, 500L, 0L, false, "tag-wallet-3-edited")
        checkWalletId(id4, "Test wallet 4-edited", "encoded-icon-4-edited", "EUR", "note-wallet-4-edited", true, 7800L, 0L, false, "tag-wallet-4-edited")
    }

    @Test
    fun deleteWallet() {
        // insert 4 wallets and then query for each one and check the returned id
        val id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val id2 = insertWallet("Test wallet 2", "encoded-icon-2", "USD", "note-wallet-2", true, 3000L, true, "tag-wallet-2")
        val id3 = insertWallet("Test wallet 3", "encoded-icon-3", "EUR", "note-wallet-3", true, 1000L, false, "tag-wallet-3")
        val id4 = insertWallet("Test wallet 4", "encoded-icon-4", "USD", "note-wallet-4", false, 7500L, false, "tag-wallet-4")
        // check that the returned cursor contains exactly 4 wallets
        val db = checkNotNull(database)
        checkCursorSize(db.getWallets(null, null, null, null), 4)
        // now remove two wallets
        assertEquals(1, db.deleteWallet(id2))
        assertEquals(1, db.deleteWallet(id3))
        // recheck the wallet count
        checkCursorSize(db.getWallets(null, null, null, null), 2)
    }

    @Test
    fun testEnsureDatabaseCleanAfterWalletDelete() {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -3)
        val startDate = calendar.time
        calendar.add(Calendar.MONTH, 5)
        val endDate = calendar.time
        val id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val id2 = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 3000L, true, "tag-wallet-2")
        val id3 = insertCategory("category 3", "encoded-icon-3", 1, null, true, "category-tag-3")
        val id4 = insertTransaction(10, Date(), "test", id3, Contract.Direction.EXPENSE, 0, id1, null, null, null, null, null, true, true, null, null, null)
        val id5 = insertTransactionModel(25, "desc", id3, Contract.Direction.INCOME, id1, null, null, null, true, true, null)
        val id6 = insertTransferModel("desc", id1, id2, 30L, 30L, 0L, "note", null, null, true, true, null)
        val id7 = insertSaving("desc", "encoded-icon", 0L, 100L, id1, null, false, null, null)
        val id8 = insertDebt(Contract.DebtType.DEBT.value, "encoded-icon", "desc", Date(), null, id1, null, null, 20, false, null, null, false)
        val id9 = insertBudget(Schema.BudgetType.CATEGORY, id3, startDate, endDate, 3000L, "EUR", arrayOf(id1, id2), null)
        checkBudgetId(id9, Schema.BudgetType.CATEGORY, id3, startDate, endDate, 3000L, "EUR", arrayOf(id1, id2), null, -10L)
        // now delete the wallet 1
        val db = checkNotNull(database)
        db.deleteWallet(id1)
        // check if everything has been deleted
        checkCursorSize(db.getTransaction(id4, null), 0)
        checkCursorSize(db.getTransactionModel(id5, null), 0)
        checkCursorSize(db.getTransferModel(id6, null), 0)
        checkCursorSize(db.getSaving(id7, null), 0)
        checkCursorSize(db.getDebt(id8, null), 0)
        checkBudgetId(id9, Schema.BudgetType.CATEGORY, id3, startDate, endDate, 3000L, "EUR", arrayOf(id2), null, 0L)
    }

    /**
     * Puts a file where SQLDatabase.getAttachmentFolder will look for it, by the same route that
     * method takes. That is deliberate and it is not the identity a sentinel built from the
     * wrapper would be, because the pair of cases below differ only in whether the transaction
     * commits. A wrong folder makes the committed case fail, so the path is under test too.
     */
    private fun writeAttachmentFile(name: String): File {
        val folder = File(context.getExternalFilesDir(null), Attachment.FOLDER_NAME)
        assertTrue("cannot create $folder", folder.exists() || folder.mkdirs())
        val file = File(folder, name)
        assertTrue("cannot create $file", file.exists() || file.createNewFile())
        return file
    }

    private fun walletWithAnAttachedTransaction(fileName: String): Long {
        val walletId = insertWallet("Attached", "icon", "EUR", "note", true, 0L, false, "tag")
        val categoryId = insertCategory("category", "icon", 0, null, true, "tag")
        val attachmentId = insertAttachment(fileName, "name", "mime-type", 90L, "tag")
        insertTransaction(
            10, Date(), "desc", categoryId, Contract.Direction.EXPENSE, 0,
            walletId, null, null, null, null, null, true, true, null,
            arrayOf(attachmentId), null
        )
        return walletId
    }

    /**
     * The four cases below are the two sided proof of the transaction wrapper, and each pair needs
     * both halves. Without the committing case, a wrapper that rolled everything back and
     * committed nothing would satisfy the rollback case, and no other case in this class would
     * see it, because they all call SQLDatabase directly and never open a transaction at all.
     */
    @Test
    fun inTransactionCommitsWhatTheBodyWrote() {
        val walletId = checkNotNull(database).inTransaction {
            insertWallet("Committed", "icon", "EUR", "note", true, 0L, false, "tag")
        }
        assertTrue(walletId > 0L)
        checkCursorSize(checkNotNull(database).getWallets(null, null, null, null), 1)
    }

    @Test
    fun inTransactionRollsBackEveryWriteWhenTheBodyThrows() {
        insertWallet("Written before the transaction", "icon", "EUR", "note", true, 0L, false, "tag")
        try {
            checkNotNull(database).inTransaction<Long> {
                insertWallet("Rolled back", "icon", "EUR", "note", true, 0L, false, "tag")
                insertPerson("Rolled back too", "icon", "note", "tag")
                throw IllegalStateException("forced from the body")
            }
            fail("the body's exception did not leave inTransaction")
        } catch (expected: IllegalStateException) {
            // it has to reach the caller, since that is how a provider learns its write is undone
        }
        // both writes inside the transaction are gone, and the one before it is untouched, so
        // this fails either way round: on a wrapper that does not roll back, and on one that
        // rolls back more than the transaction
        val db = checkNotNull(database)
        checkCursorSize(db.getWallets(null, null, null, null), 1)
        checkCursorSize(db.getPeople(null, null, null, null), 0)
    }

    /**
     * The shared helper, its swap, and the lock between them had no coverage at all until the
     * wrapper started answering getApplicationContext for itself. Removing the lock left every
     * other case in this class green, which made it a guard nothing could see.
     */
    @Test
    fun theSharedHelperResolvesThroughTheTestWrapper() {
        // the assertion that everything below rests on. If the wrapper were bypassed here the
        // shared helper would be the real ledger, and these cases would be writing into it
        assertEquals(
            context.getDatabasePath(SQLDatabase.DATABASE_NAME).path,
            sharedPath()
        )
    }

    @Test
    fun resetSharedReplacesTheInstanceAndGetSharedKeepsIt() {
        val first = shared()
        assertTrue(
            "getShared handed back two helpers for one process",
            first === shared()
        )
        SQLDatabase.resetShared(context)
        assertFalse(
            "resetShared left the old helper installed",
            first === shared()
        )
    }

    @Test
    fun inSharedTransactionRunsOnTheSharedHelperAndRollsBack() {
        val shared = shared()
        try {
            SQLDatabase.inSharedTransaction<Long>(context) { db ->
                assertTrue(
                    "the body was handed a different helper than the transaction was opened on",
                    db === shared
                )
                val cv = ContentValues().apply {
                    put(Schema.Wallet.NAME, "rolled back")
                    put(Schema.Wallet.ICON, "icon")
                    put(Schema.Wallet.CURRENCY, "EUR")
                    put(Schema.Wallet.START_MONEY, 0L)
                    put(Schema.Wallet.COUNT_IN_TOTAL, true)
                    put(Schema.Wallet.ARCHIVED, false)
                    put(Schema.Wallet.INDEX, 0)
                    put(Schema.Wallet.UUID, java.util.UUID.randomUUID().toString())
                    put(Schema.Wallet.LAST_EDIT, System.currentTimeMillis())
                    put(Schema.Wallet.DELETED, false)
                }
                db.writableDatabase.insert(Schema.Wallet.TABLE, null, cv)
                throw IllegalStateException("forced from the body")
            }
            fail("the body's exception did not leave inSharedTransaction")
        } catch (expected: IllegalStateException) {
            // as elsewhere
        }
        val cursor = shared.writableDatabase.query(
            Schema.Wallet.TABLE, null, null, null, null, null, null
        )
        assertNotNull(cursor)
        try {
            assertEquals(0, cursor.count)
        } finally {
            cursor.close()
        }
    }

    /**
     * The only case that fails if the swap lock is deleted, downgraded or inverted. Everything
     * else in this class is single threaded and stays green without it.
     */
    @Test(timeout = 20000)
    fun resetSharedWaitsForAWriteAlreadyInFlight() {
        val inside = CountDownLatch(1)
        val release = CountDownLatch(1)
        val resetReturned = CountDownLatch(1)
        val writer = Thread {
            SQLDatabase.inSharedTransaction(context) { _ ->
                inside.countDown()
                try {
                    release.await()
                } catch (e: InterruptedException) {
                    throw IllegalStateException(e)
                }
                1L
            }
        }
        writer.start()
        assertTrue("the write never entered the transaction", inside.await(10, TimeUnit.SECONDS))

        val resetter = Thread {
            SQLDatabase.resetShared(context)
            resetReturned.countDown()
        }
        resetter.start()
        try {
            // it has to still be waiting. Closing the helper here is what sends the rest of an
            // unfinished write to a second connection with no transaction on it
            assertFalse(
                "resetShared swapped the helper while a write was still in flight",
                resetReturned.await(1, TimeUnit.SECONDS)
            )
        } finally {
            // in a finally for the reason given on the case below
            release.countDown()
        }
        writer.join(10000)
        assertTrue(
            "resetShared never completed once the write finished",
            resetReturned.await(10, TimeUnit.SECONDS)
        )
        resetter.join(10000)
    }

    /**
     * The same exclusion for the overload that carries the work. The case above drives the plain
     * overload, so a version that took the write lock only there would keep it green while every
     * rename in AbstractBackupImporter ran with a write still open on the file being moved.
     */
    @Test(timeout = 20000)
    fun resetSharedWaitsForAWriteAlreadyInFlightBeforeRunningTheWork() {
        val inside = CountDownLatch(1)
        val release = CountDownLatch(1)
        val workRan = CountDownLatch(1)
        val writer = Thread {
            SQLDatabase.inSharedTransaction(context) { _ ->
                inside.countDown()
                try {
                    release.await()
                } catch (e: InterruptedException) {
                    throw IllegalStateException(e)
                }
                1L
            }
        }
        writer.start()
        assertTrue("the write never entered the transaction", inside.await(10, TimeUnit.SECONDS))

        val resetter = Thread { SQLDatabase.resetShared(context) { workRan.countDown() } }
        resetter.start()
        try {
            assertFalse(
                "the work ran while a write was still in flight, which is the rename moving the database file out from under an open transaction",
                workRan.await(1, TimeUnit.SECONDS)
            )
        } finally {
            // in a finally, or a failed assertion leaves the writer parked forever inside an open
            // transaction holding the read lock, and the next resetShared waits on it for good.
            // The suite then hangs instead of reporting, which reads as a broken run and not as a
            // broken guard
            release.countDown()
        }
        writer.join(10000)
        assertTrue(
            "the work never ran once the write finished",
            workRan.await(10, TimeUnit.SECONDS)
        )
        resetter.join(10000)
    }

    /**
     * The slot a restore renames the database file in. If the work ran anywhere else the file
     * would move with a connection open on it, which strands that connection's journal at the
     * old path and refuses its next write.
     */
    @Test
    fun resetSharedRunsTheWorkWithTheFileClosedAndReplacesTheHelperAfterIt() {
        val first = shared()
        val open = first.writableDatabase
        assertTrue("the helper was not open before the swap", open.isOpen)
        val ran = booleanArrayOf(false)
        SQLDatabase.resetShared(context) {
            ran[0] = true
            assertFalse("the work ran with the database still open", open.isOpen)
            // and before the replacement, which is what leaves the path free for the rename
            assertTrue("the helper was replaced before the work ran", shared() === first)
        }
        assertTrue("the work never ran", ran[0])
        assertFalse("resetShared left the old helper installed", shared() === first)
    }

    @Test
    fun resetSharedInstallsAWorkingHelperEvenWhenTheWorkThrows() {
        val first = shared()
        try {
            SQLDatabase.resetShared(context) {
                throw IllegalStateException("forced from the work")
            }
            fail("the work's exception did not leave resetShared")
        } catch (expected: IllegalStateException) {
            // it has to leave, since the caller is the one that knows what a failed file swap
            // means. What must not happen is the helper being left closed behind it
        }
        val replacement = shared()
        assertFalse(
            "the closed helper was left installed after the work threw",
            replacement === first
        )
        assertTrue(
            "the helper installed after the work threw cannot open the database",
            replacement.writableDatabase.isOpen
        )
    }

    private fun wallet(name: String): ContentValues {
        return ContentValues().apply {
            put(Contract.Wallet.NAME, name)
            put(Contract.Wallet.ICON, "icon")
            put(Contract.Wallet.CURRENCY, "EUR")
            put(Contract.Wallet.NOTE, "note")
            put(Contract.Wallet.COUNT_IN_TOTAL, true)
            put(Contract.Wallet.START_MONEY, 0L)
            put(Contract.Wallet.ARCHIVED, false)
            put(Contract.Wallet.TAG, "tag")
        }
    }

    /**
     * A wallet row as a restore writes one. SyncContentProvider inserts raw with no defaults of
     * its own, so the columns SQLDatabase.insertWallet would have generated have to be here.
     */
    private fun syncWallet(name: String): ContentValues {
        return ContentValues().apply {
            put(Schema.Wallet.NAME, name)
            put(Schema.Wallet.ICON, "icon")
            put(Schema.Wallet.CURRENCY, "EUR")
            put(Schema.Wallet.START_MONEY, 0L)
            put(Schema.Wallet.COUNT_IN_TOTAL, true)
            put(Schema.Wallet.ARCHIVED, false)
            put(Schema.Wallet.INDEX, 0)
            put(Schema.Wallet.NOTE, "note")
            put(Schema.Wallet.TAG, "tag")
            put(Schema.Wallet.UUID, java.util.UUID.randomUUID().toString())
            put(Schema.Wallet.LAST_EDIT, System.currentTimeMillis())
            put(Schema.Wallet.DELETED, false)
        }
    }

    /** Counts through whichever file this thread is currently resolving. */
    private fun sharedWalletsNamed(name: String): Long {
        val cursor = SQLDatabase.getShared(context).readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM ${Schema.Wallet.TABLE} WHERE ${Schema.Wallet.NAME} = ?",
            arrayOf(name)
        )
        return try {
            cursor.moveToFirst()
            cursor.getLong(0)
        } finally {
            cursor.close()
        }
    }

    /** The helper this thread resolves, which is the staged one while a staged import is open. */
    private fun shared(): SQLDatabase {
        return SQLDatabase.getShared(context)
    }

    /** The file this thread is open on. */
    private fun sharedPath(): String {
        return SQLDatabase.getShared(context).writableDatabase.path
    }

    /**
     * The claim the whole redesign rests on, and the reason the redirect is per thread. The
     * importing thread has to write into the staged file, and every other thread has to go on
     * writing into the live ledger, because that is what keeps the live file name still and lets
     * every read in the app run with no lock at all.
     *
     * The first assertion is the one that matters. A staged name resolving to the live file
     * would make everything below pass without testing anything.
     */
    @Test
    fun aStagedImportRedirectsItsOwnThreadAndNoOther() {
        val onImporter = "Written on the importing thread"
        val onOther = "Written on another thread"
        val live = context.getDatabasePath(SQLDatabase.DATABASE_NAME)
        val staged = context.getDatabasePath(SQLDatabaseImporter.STAGING_DATABASE_NAME)
        assertFalse(
            "the staged name resolves to the live file, so nothing below can fail",
            live.path == staged.path
        )
        assertEquals(live.path, sharedPath())

        val otherPath = arrayOfNulls<String>(1)
        val otherFailure = arrayOfNulls<Throwable>(1)
        SQLDatabase.openStaging(context, SQLDatabaseImporter.STAGING_DATABASE_NAME)
        try {
            assertEquals(
                "the importing thread did not follow the staged name",
                staged.path, sharedPath()
            )
            SQLDatabase.inSharedTransaction(context) { database ->
                database.insertWallet(wallet(onImporter))
            }

            val other = Thread {
                try {
                    otherPath[0] = sharedPath()
                    SQLDatabase.inSharedTransaction(context) { database ->
                        database.insertWallet(wallet(onOther))
                    }
                } catch (t: Throwable) {
                    otherFailure[0] = t
                }
            }
            other.start()
            other.join()
        } finally {
            SQLDatabase.closeStaging()
        }
        assertNull("the other thread failed: ${otherFailure[0]}", otherFailure[0])
        assertEquals(
            "another thread was redirected to the staged file too",
            live.path, otherPath[0]
        )
        assertEquals(
            "the importing thread did not come back to the live database",
            live.path, sharedPath()
        )

        assertEquals(
            "the other thread row is not in the live ledger",
            1L, sharedWalletsNamed(onOther)
        )
        assertEquals(
            "the staged row reached the live ledger",
            0L, sharedWalletsNamed(onImporter)
        )

        // and the staged row really is in the staged file. Without this the assertion above
        // passes just as well when the insert wrote nowhere at all
        SQLDatabase.openStaging(context, SQLDatabaseImporter.STAGING_DATABASE_NAME)
        try {
            assertEquals(
                "the staged row never reached the staged database",
                1L, sharedWalletsNamed(onImporter)
            )
            assertEquals(
                "the other thread row reached the staged database",
                0L, sharedWalletsNamed(onOther)
            )
        } finally {
            SQLDatabase.closeStaging()
        }
    }

    /**
     * The path a restore actually writes on. Every row an import puts in goes ContentResolver to
     * SyncContentProvider, which resolves the helper on each call and takes no transaction, so a
     * redirect that only reached inSharedTransaction would leave every restored row in the live
     * ledger. inSharedTransaction is DataContentProvider entry point and no restore uses it.
     *
     * The provider is used against the live ledger first on purpose, and that is what gives this
     * case its teeth. SyncContentProvider.db resolves the helper on every use instead of holding
     * it in a field, and its own javadoc says so; hold it in a field instead and the read below
     * fills that field with the live helper, so the staged insert goes into the ledger and this
     * case fails. Without the read first, a field would be filled by the staged insert itself and
     * the same broken provider would pass.
     */
    @Test
    fun aRowWrittenThroughTheSyncProviderFollowsTheStagedRedirect() {
        val viaProvider = "Written through the sync provider"
        val resolver = context.contentResolver

        val warmUp = resolver.query(SyncContentProvider.CONTENT_WALLETS, null, null, null, null)
        assertNotNull("the sync provider answered nothing on the live ledger", warmUp)
        warmUp?.close()

        SQLDatabase.openStaging(context, SQLDatabaseImporter.STAGING_DATABASE_NAME)
        try {
            resolver.insert(SyncContentProvider.CONTENT_WALLETS, syncWallet(viaProvider))
            assertEquals(
                "the row did not reach the staged database",
                1L, sharedWalletsNamed(viaProvider)
            )
        } finally {
            SQLDatabase.closeStaging()
        }
        assertEquals(
            "the row written through the sync provider landed in the live ledger",
            0L, sharedWalletsNamed(viaProvider)
        )
    }

    /**
     * closeStaging is called from a finally on a path where the open may never have happened, so
     * calling it with nothing staged, and calling it twice, both have to be no ops. If either
     * moved this thread off the live database the whole suite after it would read the wrong file.
     *
     * The second close here runs against a helper with a connection really open on it, since
     * openStaging opens the staged file instead of leaving it to the first row.
     */
    @Test
    fun closingAStagedImportIsSafeWithoutOneAndTwiceOver() {
        val live = context.getDatabasePath(SQLDatabase.DATABASE_NAME)
        SQLDatabase.closeStaging()
        assertEquals("closing with nothing staged moved this thread", live.path, sharedPath())

        SQLDatabase.openStaging(context, SQLDatabaseImporter.STAGING_DATABASE_NAME)
        SQLDatabase.closeStaging()
        SQLDatabase.closeStaging()
        assertEquals(
            "a second close moved this thread off the live database",
            live.path, sharedPath()
        )
    }

    /**
     * An import that writes no rows still has to leave a file behind for the promote to rename.
     * SQLiteOpenHelper creates the file on first use, so openStaging opens the staged database
     * itself; without that, a backup whose arrays are all empty writes nothing, the rename finds
     * no source and the restore reports a failure for a backup with nothing wrong with it. A
     * legacy backup of an empty install is the reachable one, since that importer reads no
     * currencies and so has no table that is always written.
     */
    @Test
    fun openingAStagedImportCreatesTheFileBeforeAnyRowIsWritten() {
        val staged = context.getDatabasePath(SQLDatabaseImporter.STAGING_DATABASE_NAME)
        staged.delete()
        assertFalse("the precondition failed, a staged file was already on disk", staged.exists())

        SQLDatabase.openStaging(context, SQLDatabaseImporter.STAGING_DATABASE_NAME)
        try {
            assertTrue("openStaging left no file for the promote to rename", staged.exists())
        } finally {
            SQLDatabase.closeStaging()
        }
        assertTrue("the staged file went away when the helper closed", staged.exists())
    }

    /**
     * Two staged imports on one thread would leak the first helper, leaving a connection open on
     * a file the promote is about to rename, so the second is refused instead. Nothing does this
     * today and the refusal is what keeps it that way.
     */
    @Test
    fun aSecondStagedImportOnOneThreadIsRefused() {
        SQLDatabase.openStaging(context, SQLDatabaseImporter.STAGING_DATABASE_NAME)
        try {
            SQLDatabase.openStaging(context, SQLDatabaseImporter.STAGING_DATABASE_NAME)
            fail("a second staged import was allowed on one thread")
        } catch (expected: IllegalStateException) {
            assertTrue(
                "refused for the wrong reason: ${expected.message}",
                expected.message?.contains("already open") == true
            )
        } finally {
            SQLDatabase.closeStaging()
        }
    }

    /**
     * What promoteStagedDatabase rests on. It renames the staged file straight over the live one
     * and deletes nothing ahead of it, so that a rename which fails leaves the user's ledger
     * where it was. That is only safe if a rename replaces an existing target instead of
     * refusing it, which is a platform promise and not one this code can make.
     */
    @Test
    fun aRenameOverAnExistingFileReplacesItInOneStep() {
        val live = File(context.cacheDir, "rename-live.db")
        val staged = File(context.cacheDir, "rename-staged.db")
        try {
            FileUtils.write(live, "the ledger", "UTF-8")
            FileUtils.write(staged, "the import", "UTF-8")
            assertTrue(
                "the rename refused an existing target, so a promote would have to delete the live database first and a failure there would destroy both",
                staged.renameTo(live)
            )
            assertEquals("the import", FileUtils.readFileToString(live, "UTF-8"))
            assertFalse("the staged file outlived its own rename", staged.exists())
        } finally {
            FileUtils.deleteQuietly(live)
            FileUtils.deleteQuietly(staged)
        }
    }

    @Test
    fun aNestedInTransactionRollsBackWithTheOuterOne() {
        // outside the transaction, so the count below fails both ways round: on a nested call that
        // committed on its own, and on one that rolled back past the wallet written before
        insertWallet("Written before the transaction", "icon", "EUR", "note", true, 0L, false, "tag")
        val db = checkNotNull(database)
        try {
            db.inTransaction<Long> {
                insertWallet("Written before the nested call", "icon", "EUR", "note", true, 0L, false, "tag")
                val nested = db.inTransaction {
                    insertWallet("Nested", "icon", "EUR", "note", true, 0L, false, "tag")
                }
                // the nested call has to have written something for the count below to be
                // testing a rollback. A build that refused to nest never reaches this line, and
                // one that ran the body and threw the row away would fail here
                assertTrue("the nested call inserted nothing", nested > 0L)
                // its own type. IllegalStateException is what a build that refuses to nest throws
                // from the line above, and a catch on that type below cannot tell the two apart
                throw ImportStopped()
            }
            fail("the body's exception did not leave inTransaction")
        } catch (expected: ImportStopped) {
            // this is the failure the import path has to survive, a row is refused after earlier
            // rows have gone in through the provider, each of those opening a transaction of its
            // own. Only the wallet written before the outer transaction may be left
        }
        checkCursorSize(db.getWallets(null, null, null, null), 1)
    }

    /** How the case below ends its import, told apart from anything the app itself raises. */
    private class ImportStopped : RuntimeException()

    /** The values an import builds for a wallet it has to create before it can use one. */
    private fun walletValues(name: String): ContentValues {
        return ContentValues().apply {
            put(Contract.Wallet.NAME, name)
            put(Contract.Wallet.ICON, "icon")
            put(Contract.Wallet.CURRENCY, "EUR")
            put(Contract.Wallet.COUNT_IN_TOTAL, true)
            put(Contract.Wallet.START_MONEY, 0L)
            put(Contract.Wallet.ARCHIVED, false)
        }
    }

    /**
     * A transaction naming a wallet and a category that are not there. Every column the table
     * insists on is filled, so the only thing wrong with this row is the two ids, and the insert
     * fails on them and not on something left out.
     */
    private fun transactionValuesOnMissingRows(): ContentValues {
        return transactionValues(987654321L, 987654321L)
    }

    /**
     * The whole of what this change buys an import, driven the way an import drives it: rows in
     * through the provider, one refused part way, and nothing left behind.
     */
    @Test
    fun aRowTheProviderRefusesTakesEveryRowBeforeItBackOut() {
        val contentResolver = context.contentResolver
        try {
            DataContentProvider.runInOneTransaction(context) {
                val saved = contentResolver.insert(
                    DataContentProvider.CONTENT_WALLETS,
                    walletValues("Saved before the refused row")
                )
                assertNotNull("the provider refused a wallet that is fine", saved)
                // foreign keys are on, so this one cannot go in, and the provider says so by
                // answering nothing. An importer that ignored that answer is what used to let a
                // refused row disappear while the import reported that it had worked
                val refused = contentResolver.insert(
                    DataContentProvider.CONTENT_TRANSACTIONS,
                    transactionValuesOnMissingRows()
                )
                assertNull(
                    "the provider took a transaction on a wallet that does not exist, so this case is no longer testing a refusal",
                    refused
                )
                // its own type, and not one the code under test also raises. Catching
                // IllegalStateException here would swallow the one a provider write throws when
                // it refuses to run inside a transaction already open, and this case would go
                // green on a build where no row ever reached the database
                throw ImportStopped()
            }
            fail("the failure did not leave runInOneTransaction")
        } catch (expected: ImportStopped) {
            // the importers all end the import here, and the transaction has to go with it
        }
        checkCursorSize(checkNotNull(database).getWallets(null, null, null, null), 0)
    }

    @Test
    fun everyRowOfAnImportThatFinishesIsThere() {
        val contentResolver = context.contentResolver
        DataContentProvider.runInOneTransaction(context) {
            contentResolver.insert(DataContentProvider.CONTENT_WALLETS, walletValues("First"))
            contentResolver.insert(DataContentProvider.CONTENT_WALLETS, walletValues("Second"))
            null
        }
        checkCursorSize(checkNotNull(database).getWallets(null, null, null, null), 2)
    }

    /**
     * Watches the wallet list the way an open list screen does, descendants included, so it hears
     * about a write whichever uri that write announces.
     */
    private fun watchWallets(contentResolver: ContentResolver, out: Array<ContentObserver?>): CountDownLatch {
        return watch(contentResolver, DataContentProvider.CONTENT_WALLETS, true, out)
    }

    private fun watch(
        contentResolver: ContentResolver,
        uri: Uri,
        descendants: Boolean,
        out: Array<ContentObserver?>
    ): CountDownLatch {
        val told = CountDownLatch(1)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                told.countDown()
            }
        }
        contentResolver.registerContentObserver(uri, descendants, observer)
        out[0] = observer
        return told
    }

    /**
     * What an import announces, and what that reaches. Both watchers here take descendants off, so
     * each one hears about exactly one uri: the list watcher only if the list itself was
     * announced, and the row watcher only if announcing the list reaches the rows under it.
     *
     * The second half is the reason an import is allowed to remember one uri per list instead of
     * one per row, and it is a claim about the framework, so it is checked here and not argued.
     */
    @Test
    fun anImportAnnouncesTheListAndReachesTheRowsUnderIt() {
        val contentResolver = context.contentResolver
        val existing = insertWallet("Open on a detail screen", "icon", "EUR", "note", true, 0L, false, "tag")
        val listObserver = arrayOfNulls<ContentObserver>(1)
        val rowObserver = arrayOfNulls<ContentObserver>(1)
        val listTold = watch(contentResolver, DataContentProvider.CONTENT_WALLETS, false, listObserver)
        val rowTold = watch(
            contentResolver,
            ContentUris.withAppendedId(DataContentProvider.CONTENT_WALLETS, existing), false, rowObserver
        )
        try {
            DataContentProvider.runInOneTransaction(context) {
                contentResolver.insert(DataContentProvider.CONTENT_WALLETS, walletValues("Imported"))
            }
            assertTrue(
                "the import announced the row it wrote instead of the list it wrote into",
                listTold.await(5, TimeUnit.SECONDS)
            )
            assertTrue(
                "announcing the list did not reach a screen watching a row under it, so remembering the list instead of the row loses a watcher",
                rowTold.await(5, TimeUnit.SECONDS)
            )
        } finally {
            listObserver[0]?.let { contentResolver.unregisterContentObserver(it) }
            rowObserver[0]?.let { contentResolver.unregisterContentObserver(it) }
        }
    }

    @Test
    fun anImportThatFinishesTellsTheOpenScreens() {
        val contentResolver = context.contentResolver
        val observer = arrayOfNulls<ContentObserver>(1)
        val told = watchWallets(contentResolver, observer)
        try {
            DataContentProvider.runInOneTransaction(context) {
                assertNotNull(
                    "the provider refused a wallet that is fine, so this case is no longer testing an import that wrote anything",
                    contentResolver.insert(DataContentProvider.CONTENT_WALLETS, walletValues("Imported"))
                )
                null
            }
            // that an import which finished says something at all. It does not pin WHEN, since a
            // latch that has already counted down reads the same either way, and an import that
            // announced every row as it went would satisfy this too. What pins the holding back
            // is anImportThatFailedAnnouncesNothing, where an announcement must never arrive
            assertTrue("nothing was announced after the import committed", told.await(5, TimeUnit.SECONDS))
        } finally {
            observer[0]?.let { contentResolver.unregisterContentObserver(it) }
        }
    }

    @Test
    fun anImportThatFailedAnnouncesNothing() {
        val contentResolver = context.contentResolver
        val observer = arrayOfNulls<ContentObserver>(1)
        val told = watchWallets(contentResolver, observer)
        try {
            DataContentProvider.runInOneTransaction(context) {
                assertNotNull(
                    "the provider refused a wallet that is fine, so there is no row for this case to roll back",
                    contentResolver.insert(DataContentProvider.CONTENT_WALLETS, walletValues("Rolled back"))
                )
                throw ImportStopped()
            }
            fail("the failure did not leave runInOneTransaction")
        } catch (expected: ImportStopped) {
            // the wallet above went in and came back out, and announcing it would send every open
            // screen to look for a row that is not there. Checked here and not in the finally,
            // where a failure of its own would replace the one the case was really reporting and
            // leave the observer registered on the process wide resolver for the rest of the run
            assertFalse(
                "a row that was rolled back was announced anyway",
                told.await(2, TimeUnit.SECONDS)
            )
        } finally {
            observer[0]?.let { contentResolver.unregisterContentObserver(it) }
        }
    }

    @Test
    fun anImportInsideAnImportIsOneImport() {
        val contentResolver = context.contentResolver
        val observer = arrayOfNulls<ContentObserver>(1)
        val told = watchWallets(contentResolver, observer)
        try {
            DataContentProvider.runInOneTransaction(context) {
                // the inner has to run the body. Every assertion this case makes afterwards is a
                // negative one, so an inner branch that returned without calling the body at all
                // would satisfy every one of them
                assertNotNull(
                    "the nested call wrote nothing",
                    DataContentProvider.runInOneTransaction(context) {
                        contentResolver.insert(DataContentProvider.CONTENT_WALLETS, walletValues("Inner"))
                    }
                )
                // after the inner call returns, and this is the write that matters. An inner call
                // that cleared what the outer had left on the thread instead of leaving it alone
                // would send this one straight to the observers, mid transaction
                assertNotNull(
                    "the provider refused a wallet that is fine",
                    contentResolver.insert(DataContentProvider.CONTENT_WALLETS, walletValues("After the inner"))
                )
                throw ImportStopped()
            }
            fail("the failure did not leave runInOneTransaction")
        } catch (expected: ImportStopped) {
            // the inner call must not commit or announce anything of its own. Nothing nests
            // today, and this is what keeps the first caller that does from announcing rows the
            // outer transaction then rolls back
            assertFalse(
                "the inner import announced rows the outer one rolled back",
                told.await(2, TimeUnit.SECONDS)
            )
        } finally {
            observer[0]?.let { contentResolver.unregisterContentObserver(it) }
        }
        checkCursorSize(checkNotNull(database).getWallets(null, null, null, null), 0)
    }

    /**
     * The success path of the same nesting, which is where an inner call that kept a set of its
     * own would show. Its row would go in and the outer commit would announce only what the outer
     * itself wrote, so an open screen would never hear about the rows the inner call made.
     */
    @Test
    fun anImportInsideAnImportAnnouncesTheInnerRowsToo() {
        val contentResolver = context.contentResolver
        val observer = arrayOfNulls<ContentObserver>(1)
        val told = watch(contentResolver, DataContentProvider.CONTENT_WALLETS, false, observer)
        try {
            DataContentProvider.runInOneTransaction(context) {
                assertNotNull(
                    "the nested call wrote nothing",
                    DataContentProvider.runInOneTransaction(context) {
                        contentResolver.insert(DataContentProvider.CONTENT_WALLETS, walletValues("Inner"))
                    }
                )
                null
            }
            assertTrue("the row the nested call wrote was never announced", told.await(5, TimeUnit.SECONDS))
        } finally {
            observer[0]?.let { contentResolver.unregisterContentObserver(it) }
        }
        checkCursorSize(checkNotNull(database).getWallets(null, null, null, null), 1)
    }

    /**
     * Every column the table insists on, so a row built from two ids that are really there cannot
     * be refused at all.
     */
    private fun transactionValues(walletId: Long, categoryId: Long): ContentValues {
        return ContentValues().apply {
            put(Contract.Transaction.WALLET_ID, walletId)
            put(Contract.Transaction.CATEGORY_ID, categoryId)
            put(Contract.Transaction.DATE, DateUtils.getSQLDateTimeString(Date()))
            put(Contract.Transaction.MONEY, 1000L)
            put(Contract.Transaction.DIRECTION, Contract.Direction.EXPENSE)
            put(Contract.Transaction.TYPE, Contract.TransactionType.STANDARD)
            put(Contract.Transaction.CONFIRMED, true)
            put(Contract.Transaction.COUNT_IN_TOTAL, true)
        }
    }

    private fun categoryValues(name: String): ContentValues {
        return ContentValues().apply {
            put(Contract.Category.NAME, name)
            put(Contract.Category.ICON, "encoded-icon")
            put(Contract.Category.TYPE, Contract.CategoryType.EXPENSE.value)
            put(Contract.Category.SHOW_REPORT, true)
        }
    }

    /**
     * Every query of the currency table filters DELETED = 0, so a row carrying a 1 is one the app
     * does not serve while its iso still holds the primary key. A backup restored through
     * SyncContentProvider writes that column straight through, so an installation can hold every
     * currency and offer none, and the default set written to repair that collided with those rows
     * and landed nothing.
     */
    @Test
    fun insertCurrencyBringsBackARowThatIsThereAndNotServed() {
        val db = checkNotNull(database)
        assertEquals("TST", db.insertCurrency(currencyValues("TST")))
        markDeleted("TST")
        assertEquals(
            "marking the row deleted did not stop it being served, so what follows is not being asked anything",
            0, servedCount("TST")
        )

        assertEquals("TST", db.insertCurrency(seedValues("TST")))

        assertEquals(
            "the row is still not served, so the default set cannot be written back over a table that holds it and offers nothing",
            1, servedCount("TST")
        )
    }

    /**
     * The editor creates a currency through the same method and must not be given the revive. A
     * row that came from a backup as deleted holds that backup's name, symbol and decimals, so
     * answering the editor with it would discard everything the user typed and report success.
     */
    @Test
    fun insertCurrencyDoesNotBringBackARowForACallerThatDidNotAsk() {
        val db = checkNotNull(database)
        assertEquals("TST", db.insertCurrency(currencyValues("TST")))
        markDeleted("TST")

        assertNull(
            "an insert that did not ask for the revive was answered with an existing row",
            db.insertCurrency(currencyValues("TST"))
        )

        assertEquals(
            "a caller that did not ask for the revive brought the row back anyway",
            0, servedCount("TST")
        )
    }

    /** The revive is for the iso it was asked about, not for every row that is not served. */
    @Test
    fun insertCurrencyBringsBackOnlyTheIsoItWasAskedAbout() {
        val db = checkNotNull(database)
        assertEquals("TST", db.insertCurrency(currencyValues("TST")))
        assertEquals("TSU", db.insertCurrency(currencyValues("TSU")))
        markDeleted("TST")
        markDeleted("TSU")

        assertEquals("TST", db.insertCurrency(seedValues("TST")))

        assertEquals(1, servedCount("TST"))
        assertEquals(
            "reviving one currency brought back another the caller said nothing about",
            0, servedCount("TSU")
        )
    }

    /**
     * The scale is the reason this writes as little as it does. Money is stored as a long scaled
     * by the currency's decimals, which is why updateCurrency reads the old value first and sends
     * every amount held in that currency through fixCurrencyAmounts before it changes them.
     */
    @Test
    fun insertCurrencyLeavesTheScaleOfTheRowItBringsBack() {
        val owned = currencyValues("TST").apply {
            put(Contract.Currency.DECIMALS, 2)
        }
        val db = checkNotNull(database)
        assertEquals("TST", db.insertCurrency(owned))
        markDeleted("TST")
        // the default set's own idea of this currency, at a different scale from the owner's
        val fromTheAssetFile = seedValues("TST").apply {
            put(Contract.Currency.DECIMALS, 0)
            put(Contract.Currency.NAME, "From the asset file")
            put(Contract.Currency.SYMBOL, "A")
        }

        assertEquals("TST", db.insertCurrency(fromTheAssetFile))

        assertEquals(
            "bringing the row back moved its decimals, so every amount already stored in that currency now means something else, with nothing rescaled and nothing said",
            2, servedDecimals("TST")
        )
        // the same rule one size down. These are the owner's, and the asset file is not entitled
        // to them either
        assertEquals(
            "bringing the row back renamed the owner's currency from the asset file",
            "Currency TST", servedString("TST", Contract.Currency.NAME)
        )
        assertEquals(
            "bringing the row back took the owner's symbol from the asset file",
            "T", servedString("TST", Contract.Currency.SYMBOL)
        )
        // the uuid is the row's identity in the backup it came from, and the column is NOT NULL
        // UNIQUE, so writing a computed one over it can also collide and raise out of update,
        // where the insert above would have answered a quiet -1
        assertEquals(
            "bringing the row back rewrote the uuid it arrived with",
            "from-the-backup-TST", storedValue("TST", Schema.Currency.UUID)
        )
        assertEquals(
            "bringing the row back cleared the owner's favourite flag",
            "1", storedValue("TST", Schema.Currency.FAVOURITE)
        )
        // and it is a write, so it says when it happened
        assertFalse(
            "bringing the row back left the timestamp of the backup it came from, so an export carries the wrong last edit for it",
            "1" == storedValue("TST", Schema.Currency.LAST_EDIT)
        )
    }

    /**
     * The revive is for a row that is not served. Asking for it against one that is has to be
     * refused, or a caller that meant to insert is told it did and a row nobody edited has its
     * timestamp moved. The case below covers a caller that did not ask, and returns at the gate
     * before this clause is reached, so it cannot stand in for this one.
     */
    @Test
    fun insertCurrencyDoesNotBringBackARowThatIsAlreadyServed() {
        val db = checkNotNull(database)
        assertEquals("TST", db.insertCurrency(currencyValues("TST")))
        val beforeTheSecondInsert = storedValue("TST", Schema.Currency.LAST_EDIT)

        assertNull(
            "an insert that asked for the revive was answered with a row that is already served, so a collision reads as a success",
            db.insertCurrency(seedValues("TST"))
        )

        assertEquals(
            "the refused insert moved the timestamp of a row it did not change",
            beforeTheSecondInsert, storedValue("TST", Schema.Currency.LAST_EDIT)
        )
    }

    @Test
    fun insertCurrencyStillRefusesOneThatIsAlreadyServed() {
        val db = checkNotNull(database)
        assertEquals("TST", db.insertCurrency(currencyValues("TST")))

        assertNull(
            "a second insert of a currency that is already there was accepted, so the currency editor can take over one that exists",
            db.insertCurrency(currencyValues("TST"))
        )
    }

    /** What the default set sends, which is the only caller that asks for a revive. */
    private fun seedValues(iso: String): ContentValues {
        return currencyValues(iso).apply {
            put(Contract.Currency.REVIVE_IF_DELETED, true)
        }
    }

    private fun servedString(iso: String, column: String): String {
        val db = checkNotNull(database)
        val cursor = checkNotNull(
            db.getCurrencies(arrayOf(column), "${Contract.Currency.ISO} = ?", arrayOf(iso), null)
        )
        return try {
            assertTrue("no served row for $iso", cursor.moveToFirst())
            cursor.getString(cursor.getColumnIndexOrThrow(column))
        } finally {
            cursor.close()
        }
    }

    /**
     * The state a restore leaves, and not merely a deleted row. A row that arrived from a backup
     * carries that backup's uuid, which is nothing this build would compute, and whatever the
     * owner had set on it. A ghost built by deleting one of our own inserts holds exactly the
     * values a fresh insert would write, so it cannot tell a revive that keeps them from one that
     * writes them again.
     */
    private fun markDeleted(iso: String) {
        val values = ContentValues().apply {
            put(Schema.Currency.DELETED, true)
            put(Schema.Currency.UUID, "from-the-backup-$iso")
            put(Schema.Currency.FAVOURITE, true)
            // a timestamp from the backup and not one this run produced. Left as the insert wrote it,
            // the case below compares two readings of the clock taken a few statements apart and can
            // fail on a fast run for a defect that is not there
            put(Schema.Currency.LAST_EDIT, 1L)
        }
        checkNotNull(database).writableDatabase.update(
            Schema.Currency.TABLE, values, "${Schema.Currency.ISO} = ?", arrayOf(iso)
        )
    }

    /** Straight off the table, for the columns the served view does not carry. */
    private fun storedValue(iso: String, column: String): String {
        val cursor = checkNotNull(database).writableDatabase.query(
            Schema.Currency.TABLE, arrayOf(column), "${Schema.Currency.ISO} = ?", arrayOf(iso),
            null, null, null
        )
        return try {
            assertTrue("no row at all for $iso", cursor.moveToFirst())
            cursor.getString(0)
        } finally {
            cursor.close()
        }
    }

    private fun servedCount(iso: String): Int {
        val cursor = checkNotNull(database).getCurrencies(
            arrayOf(Contract.Currency.ISO), "${Contract.Currency.ISO} = ?", arrayOf(iso), null
        )
        return try {
            cursor?.count ?: 0
        } finally {
            cursor?.close()
        }
    }

    private fun servedDecimals(iso: String): Int {
        val cursor = checkNotNull(database).getCurrencies(
            arrayOf(Contract.Currency.DECIMALS), "${Contract.Currency.ISO} = ?", arrayOf(iso), null
        )
        assertNotNull(cursor)
        val safeCursor = checkNotNull(cursor)
        return try {
            assertTrue("no served row for $iso", safeCursor.moveToFirst())
            safeCursor.getInt(safeCursor.getColumnIndexOrThrow(Contract.Currency.DECIMALS))
        } finally {
            safeCursor.close()
        }
    }

    private fun currencyValues(iso: String): ContentValues {
        return ContentValues().apply {
            put(Contract.Currency.ISO, iso)
            put(Contract.Currency.NAME, "Currency $iso")
            put(Contract.Currency.SYMBOL, "T")
            // the column is NOT NULL with no default, so a currency without it is refused
            put(Contract.Currency.DECIMALS, 2)
        }
    }

    /**
     * Watches a cursor the way a CursorLoader does, by registering on the cursor itself. An
     * observer put on the resolver by hand instead would prove that the framework delivers a
     * notification and say nothing about the uri the provider registered the cursor on, which is
     * the whole of what these cases are about.
     *
     * It keeps the uris it is told about instead of counting. A case that only counts cannot tell
     * its own write from any other write in the process, and these run in the live app, where the
     * recurrence alarm can fire and a widget observer is already registered. That was harmless
     * while a cursor only heard about the entities it named and is not now.
     */
    private fun watchCursor(cursor: Cursor): BlockingQueue<Uri> {
        val heard = LinkedBlockingQueue<Uri>()
        cursor.registerContentObserver(object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                heard.add(uri ?: Uri.EMPTY)
            }
        })
        return heard
    }

    /**
     * The same, for an observer put on the resolver instead of on a cursor.
     */
    private fun watchUris(
        contentResolver: ContentResolver,
        uri: Uri,
        descendants: Boolean,
        out: Array<ContentObserver?>
    ): BlockingQueue<Uri> {
        val heard = LinkedBlockingQueue<Uri>()
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, changed: Uri?) {
                heard.add(changed ?: Uri.EMPTY)
            }
        }
        contentResolver.registerContentObserver(uri, descendants, observer)
        out[0] = observer
        return heard
    }

    /**
     * Two seconds of nothing at all, and not two seconds of anything but one named uri. A write
     * that announced the bare authority would reach this observer as that uri and not as the row
     * it wrote, so a case that only refused the row uri would let it through. Call it after
     * something else has been shown to have heard the write, or it is two seconds of waiting for
     * a notification that has not been sent yet and it passes whatever the truth is.
     */
    private fun assertHeardNothing(heard: BlockingQueue<Uri>, message: String) {
        val uri = heard.poll(2000L, TimeUnit.MILLISECONDS)
        assertNull("$message, it was told about $uri", uri)
    }

    /**
     * Waits for one named uri and ignores anything else that arrives. Five seconds in total and
     * not five seconds per uri, so a run of unrelated announcements cannot stretch it.
     */
    private fun assertHeard(heard: BlockingQueue<Uri>, expected: Uri, message: String) {
        val deadline = SystemClock.uptimeMillis() + 5000L
        var left = 5000L
        while (left > 0L) {
            val uri = heard.poll(left, TimeUnit.MILLISECONDS) ?: break
            if (expected == uri) {
                return
            }
            left = deadline - SystemClock.uptimeMillis()
        }
        fail(message)
    }

    /**
     * Two writes of two entities, and the second one is what makes this a check. A single write
     * cannot tell a cursor registered on the whole provider apart from one registered on whatever
     * that write happens to announce, so a provider that put every cursor on the currency list
     * would pass a currency only version of this on its own.
     *
     * A currency is the first, because it is the entity the fewest queries read. A provider that
     * registered each cursor on the entities its own query names would leave every cursor here
     * except the currency one hearing nothing about it.
     */
    @Test
    fun everyListCursorIsToldAboutAWriteToAnyEntity() {
        val contentResolver = context.contentResolver
        assertEveryListCursorIsToldAbout(
            contentResolver, DataContentProvider.CONTENT_CURRENCIES, currencyValues("TSA")
        )
        assertEveryListCursorIsToldAbout(
            contentResolver, DataContentProvider.CONTENT_WALLETS, walletValues("Written while every list is open")
        )
    }

    /**
     * Opens its own cursors each time it is called. Watching one cursor twice would not work,
     * since the wrapper replays what it has already been told to an observer that registers after
     * it, so the second round would start with its latch already down.
     */
    private fun assertEveryListCursorIsToldAbout(
        contentResolver: ContentResolver,
        listUri: Uri,
        values: ContentValues
    ) {
        val cursors = arrayOfNulls<Cursor>(LIST_URIS.size)
        val heard = ArrayList<BlockingQueue<Uri>>()
        try {
            for (i in LIST_URIS.indices) {
                cursors[i] = contentResolver.query(LIST_URIS[i], null, null, null, null)
                assertNotNull("the provider answered nothing for ${LIST_URIS[i]}", cursors[i])
                val safeCursor = checkNotNull(cursors[i])
                heard.add(watchCursor(safeCursor))
            }
            val written = contentResolver.insert(listUri, values)
            assertNotNull(
                "the provider refused a row that is fine, so nothing was written for these cursors to hear about",
                written
            )
            val safeWritten = checkNotNull(written)
            for (i in LIST_URIS.indices) {
                assertHeard(
                    heard[i], safeWritten,
                    "the cursor on ${LIST_URIS[i]} was not told about $safeWritten, so a screen holding it goes on showing what was true before that write"
                )
            }
        } finally {
            for (cursor in cursors) {
                cursor?.close()
            }
        }
    }

    /**
     * The two kinds of branch the case above does not open, a list that hangs off a row and a
     * single row.
     *
     * Two writes on the first, and for the same reason as above. A transaction because that is the
     * relation this cursor was given a uri for by hand, saving a debt's master transaction writes
     * the debt's people. A category because nothing about a debt's people reads one, so a provider
     * registering this cursor on what its own query names would fail there and pass the
     * transaction on its own. The single row uri takes a currency for the same job, since the debt
     * row reads a wallet, a category, a transaction, a person and a place and never a currency.
     */
    @Test
    fun aSubListAndASingleRowCursorAreToldAboutAWriteToAnotherEntity() {
        val contentResolver = context.contentResolver
        val walletId = insertWallet("Wallet the debt is against", "encoded-icon", "EUR", null, true, 0L, false, null)
        val debtId = insertDebt(Contract.DebtType.DEBT.value, "encoded-icon", "desc", Date(), null, walletId, null, null, 2000L, false, null, null, false)
        val categoryId = insertCategory("Category the transaction is in", "encoded-icon", Contract.CategoryType.EXPENSE.value, null, true, null)
        val people = Uri.withAppendedPath(ContentUris.withAppendedId(DataContentProvider.CONTENT_DEBTS, debtId), "people")
        assertCursorIsToldAbout(contentResolver, people, DataContentProvider.CONTENT_TRANSACTIONS, transactionValues(walletId, categoryId))
        assertCursorIsToldAbout(contentResolver, people, DataContentProvider.CONTENT_CATEGORIES, categoryValues("Written somewhere else"))
        assertCursorIsToldAbout(contentResolver, ContentUris.withAppendedId(DataContentProvider.CONTENT_DEBTS, debtId), DataContentProvider.CONTENT_CURRENCIES, currencyValues("TSD"))
    }

    /**
     * Its own cursor each time, for the reason the list helper opens its own.
     */
    private fun assertCursorIsToldAbout(
        contentResolver: ContentResolver,
        cursorUri: Uri,
        listUri: Uri,
        values: ContentValues
    ) {
        val cursor = contentResolver.query(cursorUri, null, null, null, null)
        try {
            assertNotNull("the provider answered nothing for $cursorUri", cursor)
            val safeCursor = checkNotNull(cursor)
            val heard = watchCursor(safeCursor)
            val written = contentResolver.insert(listUri, values)
            assertNotNull("the provider refused a row that is fine, so nothing was written for this cursor to hear about", written)
            val safeWritten = checkNotNull(written)
            assertHeard(
                heard, safeWritten,
                "the cursor on $cursorUri was not told about $safeWritten, so the detail screen holding it goes on showing what was true before that write"
            )
        } finally {
            cursor?.close()
        }
    }

    /**
     * The replay, which is the one thing MultiUriCursorWrapper still does that the platform cursor
     * does not, and the reason it is still wrapped now that there is one uri to register. A loader
     * registers on its cursor after the query it is loading has returned, and on a real screen the
     * time between the two is the first window filling. AbstractCursor drops a change that lands
     * in there.
     *
     * Writing and then registering proves nothing on its own, since the announcement is delivered
     * asynchronously and would reach the second watcher through the ordinary path if it had not
     * arrived yet. What rules that out is the lock: registerContentObserver takes the one onChange
     * holds across both its dispatch and its add, so the second registration cannot run until the
     * change has been stored. Waiting for the first watcher only says the dispatch happened, and
     * that dispatch is a post the main thread can drain before the add has run.
     */
    @Test
    fun aCursorHandsAChangeItWasAlreadyToldAboutToALateObserver() {
        val contentResolver = context.contentResolver
        val cursor = contentResolver.query(DataContentProvider.CONTENT_WALLETS, null, null, null, null)
        try {
            assertNotNull("the provider answered nothing for the wallet list", cursor)
            val safeCursor = checkNotNull(cursor)
            val early = watchCursor(safeCursor)
            val written = contentResolver.insert(DataContentProvider.CONTENT_WALLETS, walletValues("Written before the late observer"))
            assertNotNull("the provider refused a wallet that is fine, so nothing was written for this cursor to be told about", written)
            val safeWritten = checkNotNull(written)
            assertHeard(early, safeWritten, "the cursor was never told about $safeWritten, so this case cannot say anything about what a later observer gets")
            assertHeard(
                watchCursor(safeCursor), safeWritten,
                "a change the cursor had already been told about was not handed to an observer that registered after it, so a loader registering after its query would hold what was true before that write"
            )
        } finally {
            cursor?.close()
        }
    }

    /**
     * The registration the widget makes, made here rather than by calling the widget, since
     * nothing can read back what the framework was asked to watch. So this pins the mechanism and
     * not WalletWidgetObserver's own two lines, which no case reaches.
     *
     * The second half is the part worth having. Nothing ever announces CONTENT_ALL itself, so an
     * observer that did not ask for the uris below it hears none of these writes, and that is what
     * carries the whole registration.
     */
    @Test
    fun anObserverOnTheWholeProviderIsToldWhicheverUriTheWriteAnnounces() {
        val contentResolver = context.contentResolver
        val lists = arrayOf(DataContentProvider.CONTENT_CURRENCIES, DataContentProvider.CONTENT_WALLETS)
        val values = arrayOf(currencyValues("TSB"), walletValues("Watched by the widget"))
        for (i in lists.indices) {
            val wide = arrayOfNulls<ContentObserver>(1)
            val narrow = arrayOfNulls<ContentObserver>(1)
            val wideHeard = watchUris(contentResolver, DataContentProvider.CONTENT_ALL, true, wide)
            val narrowHeard = watchUris(contentResolver, DataContentProvider.CONTENT_ALL, false, narrow)
            try {
                val written = contentResolver.insert(lists[i], values[i])
                assertNotNull("the provider refused a row that is fine, so nothing was written for this observer to hear about", written)
                val safeWritten = checkNotNull(written)
                assertHeard(
                    wideHeard, safeWritten,
                    "an observer on the whole provider was not told about $safeWritten, which is how the widget would go stale after a write"
                )
                assertHeardNothing(
                    narrowHeard,
                    "an observer that did not ask for the uris below CONTENT_ALL was told about a write. Either the descendants flag is not what carries this, or something now announces the bare authority"
                )
            } finally {
                wide[0]?.let { contentResolver.unregisterContentObserver(it) }
                narrow[0]?.let { contentResolver.unregisterContentObserver(it) }
            }
        }
    }

    /**
     * The two importers both throw a checked exception, and the transaction body cannot declare
     * one, so runInOneTransaction wraps it on the way in and unwraps it on the way out. Every
     * other case here throws an unchecked one and never reaches that path.
     */
    @Test
    fun aCheckedFailureLeavesTheImportAsItself() {
        val contentResolver = context.contentResolver
        val thrown = IOException("the backup file ended early")
        try {
            DataContentProvider.runInOneTransaction(context) {
                assertNotNull(
                    "the provider refused a wallet that is fine, so there is no row for this case to roll back",
                    contentResolver.insert(DataContentProvider.CONTENT_WALLETS, walletValues("Rolled back"))
                )
                throw thrown
            }
            fail("the failure did not leave runInOneTransaction")
        } catch (expected: IOException) {
            // the same object, not one that merely reads the same. The screen that reports a
            // failed import branches on what it caught, and a build that rebuilt the failure from
            // its message would keep the words and lose the type
            assertSame("the failure was rebuilt on the way out instead of carried out", thrown, expected)
        }
        checkCursorSize(checkNotNull(database).getWallets(null, null, null, null), 0)
    }

    @Test
    fun aNestedInTransactionCommitsWithTheOuterOne() {
        val nestedId = checkNotNull(database).inTransaction {
            insertWallet("Written before the nested call", "icon", "EUR", "note", true, 0L, false, "tag")
            checkNotNull(database).inTransaction {
                insertWallet("Nested", "icon", "EUR", "note", true, 0L, false, "tag")
            }
        }
        assertTrue("the nested call did not insert a row", nestedId > 0L)
        checkCursorSize(checkNotNull(database).getWallets(null, null, null, null), 2)
    }

    @Test
    fun aRolledBackWalletDeleteLeavesItsAttachmentFileOnDisk() {
        val file = writeAttachmentFile("path1")
        val walletId = walletWithAnAttachedTransaction("path1")
        try {
            checkNotNull(database).inTransaction<Int> {
                checkNotNull(database).deleteWallet(walletId)
                throw IllegalStateException("forced after the delete")
            }
            fail("the body's exception did not leave inTransaction")
        } catch (expected: IllegalStateException) {
            // as above
        }
        // the rows came back, so a file deleted here would be one the restored rows still name
        assertTrue("the attachment file was deleted before the commit", file.exists())
        checkCursorSize(checkNotNull(database).getWallets(null, null, null, null), 1)
        assertRowCount(Schema.Attachment.TABLE, null, 1)
    }

    @Test
    fun aCommittedWalletDeleteRemovesItsAttachmentFile() {
        val file = writeAttachmentFile("path1")
        val walletId = walletWithAnAttachedTransaction("path1")
        checkNotNull(database).inTransaction { checkNotNull(database).deleteWallet(walletId) }
        assertFalse("the attachment file outlived a committed delete", file.exists())
        checkCursorSize(checkNotNull(database).getWallets(null, null, null, null), 0)
        assertRowCount(Schema.Attachment.TABLE, null, 0)
    }

    /**
     * The attachment names a nested body collects belong to the outer transaction. A nested call
     * that started a list of its own, or cleared the one already there, would leave this file on
     * the volume forever with no row naming it, and every count in this case would still be right.
     */
    @Test
    fun aNestedDeleteHandsItsAttachmentFilesToTheOuterCommit() {
        val file = writeAttachmentFile("path1")
        val walletId = walletWithAnAttachedTransaction("path1")
        checkNotNull(database).inTransaction {
            checkNotNull(database).inTransaction { checkNotNull(database).deleteWallet(walletId) }
        }
        assertFalse("the file removed by the nested body outlived the outer commit", file.exists())
        assertRowCount(Schema.Attachment.TABLE, null, 0)
    }

    /**
     * The rollback half of the case above. A nested body that collected the names into a list of
     * its own and deleted them itself would pass that one, and would leave this rollback holding
     * rows that name files already gone from the volume.
     *
     * Its own file name, because the attachment folder is not cleaned between cases and another
     * one deliberately leaves path1 behind, which would make the assertion below pass on its own.
     */
    @Test
    fun aRolledBackNestedDeleteLeavesItsAttachmentFileOnDisk() {
        val file = writeAttachmentFile("pathNestedRollback")
        val walletId = walletWithAnAttachedTransaction("pathNestedRollback")
        try {
            checkNotNull(database).inTransaction<Int> {
                checkNotNull(database).inTransaction { checkNotNull(database).deleteWallet(walletId) }
                throw ImportStopped()
            }
            fail("the body's exception did not leave inTransaction")
        } catch (expected: ImportStopped) {
            // the delete came back out, so the file the nested body collected has to still be here
        }
        assertTrue(
            "the nested body deleted its attachment file before the outer transaction committed, and the rollback has put back rows that name it",
            file.exists()
        )
        assertRowCount(Schema.Attachment.TABLE, null, 1)
    }

    /**
     * The case the join exists for, and the only one that tells it apart from a real nested pair.
     * Android rolls the whole transaction back when an inner frame ends unmarked and says nothing,
     * so under a nested pair the row written after the catch would disappear at the commit while
     * this method returned as though it had worked.
     */
    @Test
    fun anOuterBodyThatCatchesANestedFailureStillCommits() {
        checkNotNull(database).inTransaction {
            insertWallet("Written before the nested call", "icon", "EUR", "note", true, 0L, false, "tag")
            try {
                checkNotNull(database).inTransaction<Long> {
                    throw ImportStopped()
                }
                fail("the nested body's exception did not leave inTransaction")
            } catch (caughtAndCarriedOn: ImportStopped) {
                // exactly what the old refusal made impossible and the join has to survive
            }
            insertWallet("Written after the catch", "icon", "EUR", "note", true, 0L, false, "tag")
        }
        checkCursorSize(checkNotNull(database).getWallets(null, null, null, null), 2)
    }

    @Test(expected = SQLiteDataException::class)
    fun deleteWalletInTransfer() {
        val id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val id2 = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 3000L, true, "tag-wallet-2")
        insertTransfer("desc", Date(), id1, id2, null, 10L, 10L, 0L, "note", null, null, true, true, null, null, "tag")
        // the wallet cannot be deleted because is used in the transfer 3
        checkNotNull(database).deleteWallet(id2)
    }

    @Test
    fun insertTransaction() {
        val id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val id2 = insertCategory("Test category 2", "encoded-icon-2", Contract.CategoryType.INCOME.value, null, true, "tag-category-2")
        val id3 = insertPlace("place-2", "encoded-icon-2", "fake-address-2", 7.3467, 8.364, "tag-2")
        val id4 = insertPerson("person-1", "encoded-icon-1", "note-1", "tag-1")
        val id5 = insertPerson("person-2", "encoded-icon-2", "note-2", "tag-2")
        val id6 = insertAttachment("path1", "name-1", "mime-type-1", 90L, "tag-1")
        val id7 = insertAttachment("path2", "name-2", "mime-type-2", 4560L, "tag-2")
        val date = Date()
        val peopleIds = arrayOf(id4, id5)
        val attachmentIds = arrayOf(id6, id7)
        val id8 = insertTransaction(2000L, date, "desc", id2, Contract.Direction.INCOME, 0, id1, id3, "note", null, null, null, true, true, peopleIds, attachmentIds, "tag")
        checkTransactionId(id8, 2000L, date, "desc", id2, Contract.Direction.INCOME, 0, id1, id3, "note", null, null, null, true, true, peopleIds, "tag")
    }

    @Test
    fun updateTransaction() {
        val id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val id2 = insertCategory("Test category 2", "encoded-icon-2", Contract.CategoryType.INCOME.value, null, true, "tag-category-2")
        val id3 = insertPlace("place-2", "encoded-icon-2", "fake-address-2", 7.3467, 8.364, "tag-2")
        val id4 = insertPerson("person-1", "encoded-icon-1", "note-1", "tag-1")
        val id5 = insertPerson("person-2", "encoded-icon-2", "note-2", "tag-2")
        val id6 = insertAttachment("path1", "name-1", "mime-type-1", 90L, "tag-1")
        val id7 = insertAttachment("path2", "name-2", "mime-type-2", 4560L, "tag-2")
        val date = Date()
        var peopleIds = arrayOf(id4, id5)
        val attachmentIds = arrayOf(id6, id7)
        val id8 = insertTransaction(2000L, date, "desc", id2, Contract.Direction.INCOME, 0, id1, id3, "note", null, null, null, true, true, peopleIds, attachmentIds, "tag")
        // now modify the transaction
        val id9 = insertWallet("Test wallet 9", "encoded-icon-9", "USD", "note-wallet-9", false, 2000L, false, "tag-wallet-9")
        val id10 = insertCategory("Test category 10", "encoded-icon-10", Contract.CategoryType.EXPENSE.value, null, true, "tag-category-10")
        val id11 = insertPerson("person-11", "encoded-icon-11", "note-11", "tag-11")
        peopleIds = arrayOf(id4, id5, id11)
        assertEquals(1, updateTransaction(id8, 4000L, date, "desc-edited", id10, Contract.Direction.EXPENSE, 0, id9, null, "note-edited", null, null, null, true, false, peopleIds, null, "tag-edited"))
        // now check if transaction has been properly edited
        checkTransactionId(id8, 4000L, date, "desc-edited", id10, Contract.Direction.EXPENSE, 0, id9, null, "note-edited", null, null, null, true, false, peopleIds, "tag-edited")
    }

    @Test
    fun deleteTransaction() {
        val id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val id2 = insertCategory("Test category 2", "encoded-icon-2", Contract.CategoryType.INCOME.value, null, true, "tag-category-2")
        val id3 = insertPlace("place-2", "encoded-icon-2", "fake-address-2", 7.3467, 8.364, "tag-2")
        val id4 = insertPerson("person-1", "encoded-icon-1", "note-1", "tag-1")
        val id5 = insertPerson("person-2", "encoded-icon-2", "note-2", "tag-2")
        val id6 = insertAttachment("path1", "name-1", "mime-type-1", 90L, "tag-1")
        val id7 = insertAttachment("path2", "name-2", "mime-type-2", 4560L, "tag-2")
        val date = Date()
        val peopleIds = arrayOf(id4, id5)
        val attachmentIds = arrayOf(id6, id7)
        val id8 = insertTransaction(2000L, date, "desc", id2, Contract.Direction.INCOME, 0, id1, id3, "note", null, null, null, true, true, peopleIds, attachmentIds, "tag")
        val db = checkNotNull(database)
        // check the transaction count
        checkCursorSize(db.getTransactions(null, null, null, null), 1)
        // now delete the transaction item
        db.deleteTransaction(id8)
        // recheck the transaction count
        checkCursorSize(db.getTransaction(id8, null), 0)
        checkCursorSize(db.getTransactions(null, null, null, null), 0)
    }

    @Test
    fun insertTransfer() {
        val id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val id2 = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 3000L, true, "tag-wallet-2")
        val id3 = insertPlace("place-2", "encoded-icon-2", "fake-address-2", 7.3467, 8.364, "tag-2")
        val id4 = insertPerson("person-1", "encoded-icon-1", "note-1", "tag-1")
        val id5 = insertPerson("person-2", "encoded-icon-2", "note-2", "tag-2")
        val id6 = insertAttachment("path1", "name-1", "mime-type-1", 90L, "tag-1")
        val id7 = insertAttachment("path2", "name-2", "mime-type-2", 4560L, "tag-2")
        val id8 = insertEvent("event-8", "encoded-icon-8", Date(), Date(), "note-8", "tag-8")
        val date = Date()
        val peopleIds = arrayOf(id4, id5)
        val attachmentIds = arrayOf(id6, id7)
        val id9 = insertTransfer("desc", date, id1, id2, id1, 10L, 10L, 4L, "note", id3, id8, true, true, peopleIds, attachmentIds, "tag")
        checkTransferId(id9, "desc", date, id1, id2, id1, 10L, 10L, 4L, "note", id3, id8, true, true, peopleIds, "tag")
        checkCursorSize(checkNotNull(database).getTransactions(null, null, null, null), 3)
    }

    @Test
    fun updateTransfer() {
        val id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val id2 = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 3000L, true, "tag-wallet-2")
        val id3 = insertPlace("place-2", "encoded-icon-2", "fake-address-2", 7.3467, 8.364, "tag-2")
        val id4 = insertPerson("person-1", "encoded-icon-1", "note-1", "tag-1")
        val id5 = insertPerson("person-2", "encoded-icon-2", "note-2", "tag-2")
        val id6 = insertAttachment("path1", "name-1", "mime-type-1", 90L, "tag-1")
        val id7 = insertAttachment("path2", "name-2", "mime-type-2", 4560L, "tag-2")
        val id8 = insertEvent("event-8", "encoded-icon-8", Date(), Date(), "note-8", "tag-8")
        val date = Date()
        var peopleIds = arrayOf(id4, id5)
        val attachmentIds = arrayOf(id6, id7)
        val id9 = insertTransfer("desc", date, id1, id2, null, 10L, 10L, 0L, "note", id3, id8, true, true, peopleIds, attachmentIds, "tag")
        val db = checkNotNull(database)
        checkCursorSize(db.getTransactions(null, null, null, null), 2)
        // now we update the transfer adding the tax
        val id10 = insertPerson("person-10", "encoded-icon-10", "note-10", "tag-10")
        val id11 = insertPerson("person-11", "encoded-icon-11", "note-11", "tag-11")
        peopleIds = arrayOf(id10, id11)
        assertEquals(1, updateTransfer(id9, "desc-edited", date, id1, id2, id1, 10L, 10L, 4L, "note-edited", id3, id8, false, false, peopleIds, null, "tag-edited"))
        checkTransferId(id9, "desc-edited", date, id1, id2, id1, 10L, 10L, 4L, "note-edited", id3, id8, false, false, peopleIds, "tag-edited")
        checkCursorSize(db.getTransactions(null, null, null, null), 3)
        // now we change the source wallet
        val id12 = insertWallet("Test wallet 12", "encoded-icon-12", "EUR", "note-wallet-12", true, 22000L, false, "tag-wallet-12")
        assertEquals(1, updateTransfer(id9, "desc-edited", date, id12, id2, id12, 10L, 10L, 4L, "note-edited", id3, id8, false, false, peopleIds, null, "tag-edited"))
        checkTransferId(id9, "desc-edited", date, id12, id2, id12, 10L, 10L, 4L, "note-edited", id3, id8, false, false, peopleIds, "tag-edited")
        checkCursorSize(db.getTransactions(null, null, null, null), 3)
        // now remove again the tax
        assertEquals(1, updateTransfer(id9, "desc-edited", date, id12, id2, null, 10L, 10L, 0L, "note-edited", id3, id8, false, false, peopleIds, null, "tag-edited"))
        checkTransferId(id9, "desc-edited", date, id12, id2, null, 10L, 10L, 0L, "note-edited", id3, id8, false, false, peopleIds, "tag-edited")
        checkCursorSize(db.getTransactions(null, null, null, null), 2)
    }

    @Test
    fun deleteTransfer() {
        val id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val id2 = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 3000L, true, "tag-wallet-2")
        val id3 = insertPlace("place-2", "encoded-icon-2", "fake-address-2", 7.3467, 8.364, "tag-2")
        val id4 = insertPerson("person-1", "encoded-icon-1", "note-1", "tag-1")
        val id5 = insertPerson("person-2", "encoded-icon-2", "note-2", "tag-2")
        val id6 = insertAttachment("path1", "name-1", "mime-type-1", 90L, "tag-1")
        val id7 = insertAttachment("path2", "name-2", "mime-type-2", 4560L, "tag-2")
        val id8 = insertEvent("event-8", "encoded-icon-8", Date(), Date(), "note-8", "tag-8")
        val date = Date()
        val peopleIds = arrayOf(id4, id5)
        val attachmentIds = arrayOf(id6, id7)
        val id9 = insertTransfer("desc", date, id1, id2, id1, 10L, 10L, 2L, "note", id3, id8, true, true, peopleIds, attachmentIds, "tag")
        val db = checkNotNull(database)
        checkCursorSize(db.getTransfers(null, null, null, null), 1)
        checkCursorSize(db.getTransactions(null, null, null, null), 3)
        // now delete the transfer
        db.deleteTransfer(id9)
        // recheck the table sizes
        checkCursorSize(db.getTransfer(id9, null), 0)
        checkCursorSize(db.getTransfers(null, null, null, null), 0)
        // also the transactions must be removed
        checkCursorSize(db.getTransactions(null, null, null, null), 0)
    }

    @Test
    fun insertCategory() {
        val id1 = insertCategory("category 1", "encoded-icon-1", 0, null, true, "category-tag-1")
        val id2 = insertCategory("category 2", "encoded-icon-2", 0, id1, false, "category-tag-2")
        val id3 = insertCategory("category 3", "encoded-icon-3", 1, null, true, "category-tag-3")
        val id4 = insertCategory("category 4", "encoded-icon-4", 1, id3, true, "category-tag-4")
        val id5 = insertCategory("category 5", "encoded-icon-5", 1, null, true, "category-tag-5")
        checkCategoryId(id1, "category 1", "encoded-icon-1", 0, null, true, "category-tag-1")
        checkCategoryId(id2, "category 2", "encoded-icon-2", 0, id1, false, "category-tag-2")
        checkCategoryId(id3, "category 3", "encoded-icon-3", 1, null, true, "category-tag-3")
        checkCategoryId(id4, "category 4", "encoded-icon-4", 1, id3, true, "category-tag-4")
        checkCategoryId(id5, "category 5", "encoded-icon-5", 1, null, true, "category-tag-5")
    }

    @Test(expected = SQLiteDataException::class)
    fun testInsertNestedCategories() {
        val id1 = insertCategory("category 1", "encoded-icon-1", 0, null, true, "category-tag-1")
        val id2 = insertCategory("category 2", "encoded-icon-2", 0, id1, false, "category-tag-2")
        insertCategory("category 3", "encoded-icon-3", 0, id2, false, "category-tag-3")
    }

    @Test(expected = SQLiteDataException::class)
    fun testInsertInconsistentCategoryTree() {
        val id1 = insertCategory("category 1", "encoded-icon-1", 0, null, true, "category-tag-1")
        insertCategory("category 2", "encoded-icon-2", 1, id1, false, "category-tag-2")
    }

    @Test
    fun updateCategory() {
        val id1 = insertCategory("category 1", "encoded-icon-1", 0, null, true, "category-tag-1")
        val id2 = insertCategory("category 2", "encoded-icon-2", 0, id1, false, "category-tag-2")
        val id3 = insertCategory("category 3", "encoded-icon-3", 1, null, true, "category-tag-3")
        val id4 = insertCategory("category 4", "encoded-icon-4", 1, id3, true, "category-tag-4")
        val id5 = insertCategory("category 5", "encoded-icon-5", 1, null, true, "category-tag-5")
        // move category 5 as child of category 3 and edit category 2
        assertEquals(1, updateCategory(id2, "category 2-edited", "encoded-icon-2-edited", 0, null, true, "category-tag-2-edited"))
        assertEquals(1, updateCategory(id5, "category 5-edited", "encoded-icon-5-edited", 1, id3, false, "category-tag-5-edited"))
        // check that categories 1, 3 and 4 are not changed and category 2 and 5 are changed
        checkCategoryId(id1, "category 1", "encoded-icon-1", 0, null, true, "category-tag-1")
        checkCategoryId(id2, "category 2-edited", "encoded-icon-2-edited", 0, null, true, "category-tag-2-edited")
        checkCategoryId(id3, "category 3", "encoded-icon-3", 1, null, true, "category-tag-3")
        checkCategoryId(id4, "category 4", "encoded-icon-4", 1, id3, true, "category-tag-4")
        checkCategoryId(id5, "category 5-edited", "encoded-icon-5-edited", 1, id3, false, "category-tag-5-edited")
    }

    @Test(expected = SQLiteDataException::class)
    fun testUpdateNestedCategories1() {
        val id1 = insertCategory("category 1", "encoded-icon-1", 0, null, true, "category-tag-1")
        insertCategory("category 2", "encoded-icon-2", 0, id1, false, "category-tag-2")
        val id3 = insertCategory("category 3", "encoded-icon-3", 0, null, false, "category-tag-3")
        // update category 1 to become children of category 3
        updateCategory(id1, "category 1", "encoded-icon-1", 0, id3, true, "category-tag-1")
    }

    @Test(expected = SQLiteDataException::class)
    fun testUpdateNestedCategories2() {
        val id1 = insertCategory("category 1", "encoded-icon-1", 0, null, true, "category-tag-1")
        val id2 = insertCategory("category 2", "encoded-icon-2", 0, id1, false, "category-tag-2")
        val id3 = insertCategory("category 3", "encoded-icon-3", 0, null, false, "category-tag-3")
        // update category 3 to become children of category 2
        updateCategory(id3, "category 3", "encoded-icon-3", 0, id2, false, "category-tag-3")
    }

    @Test(expected = SQLiteDataException::class)
    fun testUpdateInconsistentChildrenCategory() {
        val id1 = insertCategory("category 1", "encoded-icon-1", 0, null, true, "category-tag-1")
        insertCategory("category 2", "encoded-icon-2", 0, id1, false, "category-tag-2")
        val id3 = insertCategory("category 3", "encoded-icon-3", 0, id1, false, "category-tag-3")
        // now change the type of the child category 3
        updateCategory(id3, "category 3", "encoded-icon-3", 1, id1, false, "category-tag-3")
    }

    @Test(expected = SQLiteDataException::class)
    fun testUpdateInconsistentParentCategory() {
        val id1 = insertCategory("category 1", "encoded-icon-1", 0, null, true, "category-tag-1")
        insertCategory("category 2", "encoded-icon-2", 0, id1, false, "category-tag-2")
        insertCategory("category 3", "encoded-icon-3", 0, id1, false, "category-tag-3")
        // now change the type of the parent category 1
        updateCategory(id1, "category 1", "encoded-icon-1", 1, null, true, "category-tag-1")
    }

    @Test
    fun deleteCategory() {
        val id1 = insertCategory("category 1", "encoded-icon-1", 0, null, true, "category-tag-1")
        val id2 = insertCategory("category 2", "encoded-icon-2", 0, id1, false, "category-tag-2")
        val id3 = insertCategory("category 3", "encoded-icon-3", 0, id1, false, "category-tag-3")
        val db = checkNotNull(database)
        // check cursor size
        val count = checkCursorMinSize(db.getCategories(null, null, null, null), 3)
        // now delete category 2 and 3
        assertEquals(1, db.deleteCategory(id2))
        assertEquals(1, db.deleteCategory(id3))
        // recheck cursor size
        val newCount = checkCursorMinSize(db.getCategories(null, null, null, null), 1)
        assertEquals(newCount, count - 2)
    }

    @Test(expected = SQLiteDataException::class)
    fun testDeleteCategoryWithChildren() {
        val id1 = insertCategory("category 1", "encoded-icon-1", 0, null, true, "category-tag-1")
        insertCategory("category 2", "encoded-icon-2", 0, id1, false, "category-tag-2")
        // category 1 must not be removed because it has at least one child
        checkNotNull(database).deleteCategory(id1)
    }

    @Test(expected = SQLiteDataException::class)
    fun testDeleteSystemCategory() {
        val categoryId = getSystemCategory(Contract.CategoryTag.TRANSFER)
        // a system category cannot be deleted
        checkNotNull(database).deleteCategory(categoryId)
    }

    @Test(expected = SQLiteDataException::class)
    fun testDeleteCategoryUsedInTransaction() {
        val id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val id2 = insertCategory("category 2", "encoded-icon-2", 0, null, true, "category-tag-2")
        insertTransaction(2000L, Date(), "description-3", id2, Contract.Direction.INCOME, 0, id1, null, "note-3", null, null, null, true, true, null, null, "tag-3")
        // the category cannot be deleted because it is used in transaction 3
        checkNotNull(database).deleteCategory(id2)
    }

    @Test(expected = SQLiteDataException::class)
    fun testDeleteCategoryUsedInTransactionModel() {
        val id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val id2 = insertCategory("category 2", "encoded-icon-2", 0, null, true, "category-tag-2")
        insertTransactionModel(2000L, "description-3", id2, Contract.Direction.INCOME, id1, null, "note-3", null, true, true, "tag-3")
        // the category cannot be deleted because it is used in transaction-model 3
        checkNotNull(database).deleteCategory(id2)
    }

    @Test(expected = SQLiteDataException::class)
    fun testDeleteCategoryUsedInBudget() {
        val id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val id2 = insertCategory("category 2", "encoded-icon-2", 0, null, true, "category-tag-2")
        insertBudget(Schema.BudgetType.CATEGORY, id2, Date(), Date(), 3000L, "EUR", arrayOf(id1), "tag")
        // the category cannot be deleted because it is used in budget 3
        checkNotNull(database).deleteCategory(id2)
    }


    @Test
    fun insertDebt() {
        val id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val id2 = insertPlace("place-2", "encoded-icon-2", "fake-address-2", 7.3467, 8.364, "tag-2")
        val id3 = insertPerson("person-1", "encoded-icon-1", "note-1", "tag-1")
        val id4 = insertPerson("person-2", "encoded-icon-2", "note-2", "tag-2")
        val id5 = insertPerson("person-3", "encoded-icon-3", "note-3", "tag-3")
        val date = Date()
        val peopleIds1 = arrayOf<Long?>(id3, id4, id5)
        val id6 = insertDebt(Contract.DebtType.DEBT.value, "encoded-icon-1", "desc-1", date, null, id1, "note-1", id2, 2000L, false, peopleIds1, "tag-1", false)
        checkDebtId(id6, Contract.DebtType.DEBT.value, "encoded-icon-1", "desc-1", date, null, id1, "note-1", id2, 2000L, false, peopleIds1, "tag-1")
        checkCursorSize(checkNotNull(database).getTransactions(null, null, null, null), 0)
        val id7 = insertDebt(Contract.DebtType.CREDIT.value, "encoded-icon-1", "desc-1", date, date, id1, "note-1", id2, 3000L, true, null, "tag-2", true)
        checkDebtId(id7, Contract.DebtType.CREDIT.value, "encoded-icon-1", "desc-1", date, date, id1, "note-1", id2, 3000L, true, null, "tag-2")
        checkCursorSize(checkNotNull(database).getTransactions(null, null, null, null), 1)
    }

    @Test
    fun updateDebt() {
        val id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val id2 = insertPlace("place-2", "encoded-icon-2", "fake-address-2", 7.3467, 8.364, "tag-2")
        val id3 = insertPerson("person-1", "encoded-icon-1", "note-1", "tag-1")
        val id4 = insertPerson("person-2", "encoded-icon-2", "note-2", "tag-2")
        val id5 = insertPerson("person-3", "encoded-icon-3", "note-3", "tag-3")
        val date = Date()
        var peopleIds1: Array<Long?>? = arrayOf(id3, id4)
        val id6 = insertDebt(Contract.DebtType.DEBT.value, "encoded-icon-1", "desc-1", date, null, id1, "note-1", id2, 2000L, false, peopleIds1, "tag-1", false)
        val id7 = insertDebt(Contract.DebtType.CREDIT.value, "encoded-icon-1", "desc-1", date, date, id1, "note-1", id2, 3000L, true, null, "tag-2", true)
        checkDebtId(id6, Contract.DebtType.DEBT.value, "encoded-icon-1", "desc-1", date, null, id1, "note-1", id2, 2000L, false, peopleIds1, "tag-1")
        checkDebtId(id7, Contract.DebtType.CREDIT.value, "encoded-icon-1", "desc-1", date, date, id1, "note-1", id2, 3000L, true, null, "tag-2")
        checkCursorSize(checkNotNull(database).getTransactions(null, null, null, null), 1)
        // now update both debts
        peopleIds1 = arrayOf(id4, id5)
        assertEquals(1, updateDebt(id6, Contract.DebtType.DEBT.value, "encoded-icon-1-edited", "desc-1-edited", date, date, id1, "note-1-edited", id2, 3000L, true, peopleIds1, "tag-1-edited"))
        assertEquals(1, updateDebt(id7, Contract.DebtType.CREDIT.value, "encoded-icon-2-edited", "desc-2-edited", date, null, id1, "note-1-edited", id2, 8000L, false, null, "tag-2-edited"))
        // now check that everything has been updated correctly
        checkDebtId(id6, Contract.DebtType.DEBT.value, "encoded-icon-1-edited", "desc-1-edited", date, date, id1, "note-1-edited", id2, 3000L, true, peopleIds1, "tag-1-edited")
        checkDebtId(id7, Contract.DebtType.CREDIT.value, "encoded-icon-2-edited", "desc-2-edited", date, null, id1, "note-1-edited", id2, 8000L, false, null, "tag-2-edited")
        checkCursorSize(checkNotNull(database).getTransactions(null, null, null, null), 1)
    }

    @Test
    fun debtMasterTransactionTakesTheDebtDate() {
        val walletId = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        // A past date, so a failure reads as a wrong month, not a wrong time of day. What
        // actually left the existing insertDebt and updateDebt tests blind to this is that they
        // never assert the transaction's date at all, only how many rows came back.
        val picked = checkNotNull(DateUtils.getDateFromSQLDateString("2026-07-01"))
        val debtId = insertDebt(Contract.DebtType.DEBT.value, "encoded-icon-1", "desc-1", picked, null, walletId, "note-1", null, 2000L, false, null, "tag-1", true)
        assertEquals(DateUtils.getSQLDateTimeString(picked), masterTransactionDate(debtId))
        // and it follows the debt when the date is edited
        val moved = checkNotNull(DateUtils.getDateFromSQLDateString("2026-06-15"))
        assertEquals(1, updateDebt(debtId, Contract.DebtType.DEBT.value, "encoded-icon-1", "desc-1", moved, null, walletId, "note-1", null, 2000L, false, null, "tag-1"))
        assertEquals(DateUtils.getSQLDateTimeString(moved), masterTransactionDate(debtId))
    }

    private fun masterTransactionDate(debtId: Long): String {
        val cursor = checkNotNull(database).getTransactions(
            arrayOf(Contract.Transaction.DATE),
            Contract.Transaction.DEBT_ID + " = ?",
            arrayOf(debtId.toString()),
            null
        )
        assertNotNull(cursor)
        val safeCursor = checkNotNull(cursor)
        assertEquals(1, safeCursor.count)
        safeCursor.moveToFirst()
        val date = safeCursor.getString(safeCursor.getColumnIndexOrThrow(Contract.Transaction.DATE))
        safeCursor.close()
        return date
    }

    @Test
    fun savingADebtKeepsItsMasterTransactionTime() {
        val walletId = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val categoryId = getSystemCategory(Contract.CategoryTag.DEBT)
        val picked = checkNotNull(DateUtils.getDateFromSQLDateString("2026-07-01"))
        val debtId = insertDebt(Contract.DebtType.DEBT.value, "encoded-icon-1", "desc-1", picked, null, walletId, "note-1", null, 2000L, false, null, "tag-1", true)
        // A master transaction is created at the start of the debt's day, so the time under test
        // has to be put there the way a user does it, through the transaction editor.
        val afternoon = checkNotNull(DateUtils.getDateFromSQLDateTimeString("2026-07-01 14:30:45"))
        assertEquals(1, updateTransaction(masterTransactionId(debtId), 2000L, afternoon, "desc-1", categoryId,
                Contract.Direction.INCOME, Contract.TransactionType.DEBT, walletId, null, "note-1",
                null, null, debtId, true, true, null, null, "tag-1"))
        assertEquals("2026-07-01 14:30:45", masterTransactionDate(debtId))
        // The debt editor sends its date on every save, so this is a save that changed something
        // else and never opened the date field.
        assertEquals(1, updateDebt(debtId, Contract.DebtType.DEBT.value, "encoded-icon-1", "desc-edited", picked, null, walletId, "note-1", null, 2000L, false, null, "tag-1"))
        assertEquals("2026-07-01 14:30:45", masterTransactionDate(debtId))
        // And a date that did move takes the transaction to the new day at the same time.
        val moved = checkNotNull(DateUtils.getDateFromSQLDateString("2026-06-15"))
        assertEquals(1, updateDebt(debtId, Contract.DebtType.DEBT.value, "encoded-icon-1", "desc-edited", moved, null, walletId, "note-1", null, 2000L, false, null, "tag-1"))
        assertEquals("2026-06-15 14:30:45", masterTransactionDate(debtId))
    }

    @Test
    fun editingTheMasterTransactionMovesTheDebt() {
        val walletId = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val otherWalletId = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 2000L, false, "tag-wallet-2")
        val placeId = insertPlace("place-1", "encoded-icon-1", "fake-address-1", 7.3467, 8.364, "tag-1")
        val otherPlaceId = insertPlace("place-2", "encoded-icon-2", "fake-address-2", 9.1234, 3.567, "tag-2")
        val personId1 = insertPerson("person-1", "encoded-icon-1", "note-1", "tag-1")
        val personId2 = insertPerson("person-2", "encoded-icon-2", "note-2", "tag-2")
        val personId3 = insertPerson("person-3", "encoded-icon-3", "note-3", "tag-3")
        val categoryId = getSystemCategory(Contract.CategoryTag.DEBT)
        val date = checkNotNull(DateUtils.getDateFromSQLDateString("2026-07-01"))
        val peopleIds = arrayOf<Long?>(personId1, personId2)
        val debtId = insertDebt(Contract.DebtType.DEBT.value, "encoded-icon-1", "desc-1", date, null, walletId, "note-1", placeId, 2000L, false, peopleIds, "tag-1", true)
        val transactionId = masterTransactionId(debtId)
        // Everything a debt owns, moved at once at the method the transaction editor's save
        // reaches, which is where Edit on this row in the transaction list ends up. Without
        // the sync the debt keeps 2000.00, 2026-07-01, the first wallet, the first place, the old
        // description, the old note and the first two people.
        val moved = checkNotNull(DateUtils.getDateFromSQLDateTimeString("2026-08-25 14:30:00"))
        val editedPeopleIds = arrayOf<Long?>(personId2, personId3)
        assertEquals(1, updateTransaction(transactionId, 3000L, moved, "desc-edited", categoryId,
                Contract.Direction.INCOME, Contract.TransactionType.DEBT, otherWalletId, otherPlaceId,
                "note-edited", null, null, debtId, true, true, editedPeopleIds, null, "tag-transaction"))
        // The expiration date and the archived flag have no column on a transaction, and the
        // tag has one on both tables and is carried by neither direction, so all three are
        // asserted at the values the debt was inserted with. The tag asserted here is the debt's
        // own and the save above sends a different one to the transaction. The icon is asserted
        // unchanged for its own reason, encoded-icon-1 is not readable json, so renamedDebtIcon
        // leaves it alone. A debt whose icon does move is pinned by
        // renamingTheMasterTransactionMovesTheDebtIconLetters below.
        checkDebtId(debtId, Contract.DebtType.DEBT.value, "encoded-icon-1", "desc-edited", moved, null, otherWalletId, "note-edited", otherPlaceId, 3000L, false, editedPeopleIds, "tag-1")
        // And the transaction keeps the time of day it was given. The debt carries a day, so
        // writing the debt's date back onto the transaction would drop it to 00:00:00.
        assertEquals("2026-08-25 14:30:00", masterTransactionDate(debtId))
    }

    @Test
    fun editingADebtPaymentLeavesTheDebtAlone() {
        val walletId = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val paidCategoryId = getSystemCategory(Contract.CategoryTag.PAID_DEBT)
        val date = checkNotNull(DateUtils.getDateFromSQLDateString("2026-07-01"))
        val debtId = insertDebt(Contract.DebtType.DEBT.value, "encoded-icon-1", "desc-1", date, null, walletId, "note-1", null, 2000L, false, null, "tag-1", false)
        // A payment carries the same type and the same debt id as a master transaction and is
        // told apart only by its category. Syncing one would drag the debt to the amount and the
        // day of whichever payment was edited.
        val paymentId = insertTransaction(500L, date, "payment", paidCategoryId, Contract.Direction.EXPENSE,
                Contract.TransactionType.DEBT, walletId, null, "payment-note", null, null, debtId, true, true, null, null, "tag-payment")
        val moved = checkNotNull(DateUtils.getDateFromSQLDateTimeString("2026-08-25 14:30:00"))
        assertEquals(1, updateTransaction(paymentId, 800L, moved, "payment-edited", paidCategoryId,
                Contract.Direction.EXPENSE, Contract.TransactionType.DEBT, walletId, null, "payment-note-edited",
                null, null, debtId, true, true, null, null, "tag-payment"))
        checkDebtId(debtId, Contract.DebtType.DEBT.value, "encoded-icon-1", "desc-1", date, null, walletId, "note-1", null, 2000L, false, null, "tag-1")
    }

    @Test
    fun aBlankDescriptionIsNotCarriedToTheDebt() {
        val walletId = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val categoryId = getSystemCategory(Contract.CategoryTag.DEBT)
        val date = checkNotNull(DateUtils.getDateFromSQLDateString("2026-07-01"))
        val debtId = insertDebt(Contract.DebtType.DEBT.value, "encoded-icon-1", "desc-1", date, null, walletId, "note-1", null, 2000L, false, null, "tag-1", true)
        val transactionId = masterTransactionId(debtId)
        // The transaction editor has no validator on the description, so it can be blanked there.
        // Debt.DESCRIPTION is NOT NULL and the debt editor holds it non empty after a trim, so
        // carrying a blank across would leave a debt that its own editor then refuses to save.
        // One space and not the empty string, since one space is the blank a length test lets
        // through and the guard's own trim does not.
        //
        // The money moves in the same save and is asserted below at its new amount. Without it
        // this case passes just as well on a build that never syncs anything, which is the state
        // it is meant to tell apart from a working guard.
        assertEquals(1, updateTransaction(transactionId, 3000L, date, " ", categoryId,
                Contract.Direction.INCOME, Contract.TransactionType.DEBT, walletId, null, "note-1",
                null, null, debtId, true, true, null, null, "tag-transaction"))
        checkDebtId(debtId, Contract.DebtType.DEBT.value, "encoded-icon-1", "desc-1", date, null, walletId, "note-1", null, 3000L, false, null, "tag-1")
    }

    @Test
    fun renamingTheMasterTransactionMovesTheDebtIconLetters() {
        val walletId = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val categoryId = getSystemCategory(Contract.CategoryTag.DEBT)
        val date = checkNotNull(DateUtils.getDateFromSQLDateString("2026-07-01"))
        // The letters a debt of this name would have been given by its own editor.
        val storedIcon = ColorIcon("#FF0000", "RP").toString()
        val debtId = insertDebt(Contract.DebtType.DEBT.value, storedIcon, "Rent Payment", date, null, walletId, "note-1", null, 2000L, false, null, "tag-1", true)
        val transactionId = masterTransactionId(debtId)
        assertEquals(1, updateTransaction(transactionId, 2000L, date, "Car Loan", categoryId,
                Contract.Direction.INCOME, Contract.TransactionType.DEBT, walletId, null, "note-1",
                null, null, debtId, true, true, null, null, "tag-transaction"))
        // Same color, new letters. Without this the list row reads Car Loan beside an RP icon.
        val renamedIcon = ColorIcon("#FF0000", "CL").toString()
        checkDebtId(debtId, Contract.DebtType.DEBT.value, renamedIcon, "Car Loan", date, null, walletId, "note-1", null, 2000L, false, null, "tag-1")
    }

    @Test
    fun anEditThatIsNotARenameLeavesTheDebtIconAlone() {
        val walletId = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val categoryId = getSystemCategory(Contract.CategoryTag.DEBT)
        val date = checkNotNull(DateUtils.getDateFromSQLDateString("2026-07-01"))
        // Letters that do not match the description, which IconPicker.restoreColorIcon can write.
        // A debt whose letters already agree would pass this case whether the rename test is here
        // or not, since the value written would be the value already stored.
        val storedIcon = ColorIcon("#FF0000", "r").toString()
        val debtId = insertDebt(Contract.DebtType.DEBT.value, storedIcon, "rent payment", date, null, walletId, "note-1", null, 2000L, false, null, "tag-1", true)
        val transactionId = masterTransactionId(debtId)
        // Same description, new amount. The editor sends the description on every save, so this
        // reaches the sync looking exactly like a rename does.
        assertEquals(1, updateTransaction(transactionId, 5000L, date, "rent payment", categoryId,
                Contract.Direction.INCOME, Contract.TransactionType.DEBT, walletId, null, "note-1",
                null, null, debtId, true, true, null, null, "tag-transaction"))
        checkDebtId(debtId, Contract.DebtType.DEBT.value, storedIcon, "rent payment", date, null, walletId, "note-1", null, 5000L, false, null, "tag-1")
    }

    @Test
    fun movingADebtAwayFromTheCurrencyItsPaymentsAreInIsRefused() {
        val euroWallet = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val dollarWallet = insertWallet("Test wallet 2", "encoded-icon-2", "USD", "note-wallet-2", true, 2000L, false, "tag-wallet-2")
        val paidCategoryId = getSystemCategory(Contract.CategoryTag.PAID_DEBT)
        val date = checkNotNull(DateUtils.getDateFromSQLDateString("2026-07-01"))
        val debtId = insertDebt(Contract.DebtType.DEBT.value, "encoded-icon-1", "desc-1", date, null, euroWallet, "note-1", null, 2000L, false, null, "tag-1", false)
        insertTransaction(500L, date, "payment", paidCategoryId, Contract.Direction.EXPENSE,
                Contract.TransactionType.DEBT, euroWallet, null, "payment-note", null, null, debtId, true, true, null, null, "tag-payment")
        try {
            updateDebt(debtId, Contract.DebtType.DEBT.value, "encoded-icon-1", "desc-1", date, null, dollarWallet, "note-1", null, 2000L, false, null, "tag-1")
            fail("The debt was moved away from the currency its payments are in")
        } catch (e: SQLiteDataException) {
            assertEquals(Contract.ErrorCode.WALLETS_NOT_CONSISTENT, e.errorCode)
        }
        checkDebtId(debtId, Contract.DebtType.DEBT.value, "encoded-icon-1", "desc-1", date, null, euroWallet, "note-1", null, 2000L, false, null, "tag-1")
    }

    @Test
    fun movingADebtBackToTheCurrencyItsPaymentsAreInIsAllowed() {
        val euroWallet = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val dollarWallet = insertWallet("Test wallet 2", "encoded-icon-2", "USD", "note-wallet-2", true, 2000L, false, "tag-wallet-2")
        val paidCategoryId = getSystemCategory(Contract.CategoryTag.PAID_DEBT)
        val date = checkNotNull(DateUtils.getDateFromSQLDateString("2026-07-01"))
        // The debt is held in euro and its payment sits in the dollar wallet.
        val debtId = insertDebt(Contract.DebtType.DEBT.value, "encoded-icon-1", "desc-1", date, null, euroWallet, "note-1", null, 2000L, false, null, "tag-1", false)
        insertTransaction(500L, date, "payment", paidCategoryId, Contract.Direction.EXPENSE,
                Contract.TransactionType.DEBT, dollarWallet, null, "payment-note", null, null, debtId, true, true, null, null, "tag-payment")
        assertEquals(1, updateDebt(debtId, Contract.DebtType.DEBT.value, "encoded-icon-1", "desc-1", date, null, dollarWallet, "note-1", null, 2000L, false, null, "tag-1"))
        checkDebtId(debtId, Contract.DebtType.DEBT.value, "encoded-icon-1", "desc-1", date, null, dollarWallet, "note-1", null, 2000L, false, null, "tag-1")
    }

    @Test
    fun movingAMismatchedDebtWithinItsOwnCurrencyIsAllowed() {
        val euroWallet = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val otherEuroWallet = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 2000L, false, "tag-wallet-2")
        val dollarWallet = insertWallet("Test wallet 3", "encoded-icon-3", "USD", "note-wallet-3", true, 2000L, false, "tag-wallet-3")
        val paidCategoryId = getSystemCategory(Contract.CategoryTag.PAID_DEBT)
        val date = checkNotNull(DateUtils.getDateFromSQLDateString("2026-07-01"))
        val debtId = insertDebt(Contract.DebtType.DEBT.value, "encoded-icon-1", "desc-1", date, null, euroWallet, "note-1", null, 2000L, false, null, "tag-1", false)
        insertTransaction(500L, date, "payment", paidCategoryId, Contract.Direction.EXPENSE,
                Contract.TransactionType.DEBT, dollarWallet, null, "payment-note", null, null, debtId, true, true, null, null, "tag-payment")
        assertEquals(1, updateDebt(debtId, Contract.DebtType.DEBT.value, "encoded-icon-1", "desc-1", date, null, otherEuroWallet, "note-1", null, 2000L, false, null, "tag-1"))
        checkDebtId(debtId, Contract.DebtType.DEBT.value, "encoded-icon-1", "desc-1", date, null, otherEuroWallet, "note-1", null, 2000L, false, null, "tag-1")
    }

    @Test
    fun movingAMismatchedDebtToAThirdCurrencyIsRefused() {
        val euroWallet = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val dollarWallet = insertWallet("Test wallet 2", "encoded-icon-2", "USD", "note-wallet-2", true, 2000L, false, "tag-wallet-2")
        val poundWallet = insertWallet("Test wallet 3", "encoded-icon-3", "GBP", "note-wallet-3", true, 2000L, false, "tag-wallet-3")
        val paidCategoryId = getSystemCategory(Contract.CategoryTag.PAID_DEBT)
        val date = checkNotNull(DateUtils.getDateFromSQLDateString("2026-07-01"))
        val debtId = insertDebt(Contract.DebtType.DEBT.value, "encoded-icon-1", "desc-1", date, null, euroWallet, "note-1", null, 2000L, false, null, "tag-1", false)
        insertTransaction(500L, date, "payment", paidCategoryId, Contract.Direction.EXPENSE,
                Contract.TransactionType.DEBT, dollarWallet, null, "payment-note", null, null, debtId, true, true, null, null, "tag-payment")
        try {
            updateDebt(debtId, Contract.DebtType.DEBT.value, "encoded-icon-1", "desc-1", date, null, poundWallet, "note-1", null, 2000L, false, null, "tag-1")
            fail("The debt was moved to a currency matching neither what it holds nor its payments")
        } catch (e: SQLiteDataException) {
            assertEquals(Contract.ErrorCode.WALLETS_NOT_CONSISTENT, e.errorCode)
        }
        checkDebtId(debtId, Contract.DebtType.DEBT.value, "encoded-icon-1", "desc-1", date, null, euroWallet, "note-1", null, 2000L, false, null, "tag-1")
    }

    @Test
    fun movingAMixedDebtToACurrencyItHoldsNoneOfIsRefused() {
        val euroWallet = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val dollarWallet = insertWallet("Test wallet 2", "encoded-icon-2", "USD", "note-wallet-2", true, 2000L, false, "tag-wallet-2")
        val poundWallet = insertWallet("Test wallet 3", "encoded-icon-3", "GBP", "note-wallet-3", true, 2000L, false, "tag-wallet-3")
        val paidCategoryId = getSystemCategory(Contract.CategoryTag.PAID_DEBT)
        val date = checkNotNull(DateUtils.getDateFromSQLDateString("2026-07-01"))
        val debtId = insertDebt(Contract.DebtType.DEBT.value, "encoded-icon-1", "desc-1", date, null, euroWallet, "note-1", null, 2000L, false, null, "tag-1", false)
        insertTransaction(500L, date, "payment", paidCategoryId, Contract.Direction.EXPENSE,
                Contract.TransactionType.DEBT, euroWallet, null, "payment-note", null, null, debtId, true, true, null, null, "tag-payment")
        insertTransaction(500L, date, "payment", paidCategoryId, Contract.Direction.EXPENSE,
                Contract.TransactionType.DEBT, dollarWallet, null, "payment-note", null, null, debtId, true, true, null, null, "tag-payment")
        try {
            updateDebt(debtId, Contract.DebtType.DEBT.value, "encoded-icon-1", "desc-1", date, null, poundWallet, "note-1", null, 2000L, false, null, "tag-1")
            fail("The debt was moved to a currency it holds nothing in")
        } catch (e: SQLiteDataException) {
            assertEquals(Contract.ErrorCode.WALLETS_NOT_CONSISTENT, e.errorCode)
        }
        checkDebtId(debtId, Contract.DebtType.DEBT.value, "encoded-icon-1", "desc-1", date, null, euroWallet, "note-1", null, 2000L, false, null, "tag-1")
    }

    @Test
    fun movingAMixedDebtToOneOfItsOwnCurrenciesIsAllowed() {
        val euroWallet = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val dollarWallet = insertWallet("Test wallet 2", "encoded-icon-2", "USD", "note-wallet-2", true, 2000L, false, "tag-wallet-2")
        val paidCategoryId = getSystemCategory(Contract.CategoryTag.PAID_DEBT)
        val date = checkNotNull(DateUtils.getDateFromSQLDateString("2026-07-01"))
        val debtId = insertDebt(Contract.DebtType.DEBT.value, "encoded-icon-1", "desc-1", date, null, euroWallet, "note-1", null, 2000L, false, null, "tag-1", false)
        insertTransaction(500L, date, "payment", paidCategoryId, Contract.Direction.EXPENSE,
                Contract.TransactionType.DEBT, euroWallet, null, "payment-note", null, null, debtId, true, true, null, null, "tag-payment")
        insertTransaction(500L, date, "payment", paidCategoryId, Contract.Direction.EXPENSE,
                Contract.TransactionType.DEBT, dollarWallet, null, "payment-note", null, null, debtId, true, true, null, null, "tag-payment")
        assertEquals(1, updateDebt(debtId, Contract.DebtType.DEBT.value, "encoded-icon-1", "desc-1", date, null, dollarWallet, "note-1", null, 2000L, false, null, "tag-1"))
        checkDebtId(debtId, Contract.DebtType.DEBT.value, "encoded-icon-1", "desc-1", date, null, dollarWallet, "note-1", null, 2000L, false, null, "tag-1")
    }

    @Test
    fun movingADebtThatStrandsNothingGoesThrough() {
        val euroWallet = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val dollarWallet = insertWallet("Test wallet 2", "encoded-icon-2", "USD", "note-wallet-2", true, 2000L, false, "tag-wallet-2")
        val date = checkNotNull(DateUtils.getDateFromSQLDateString("2026-07-01"))
        val debtId = insertDebt(Contract.DebtType.DEBT.value, "encoded-icon-1", "desc-1", date, null, euroWallet, "note-1", null, 2000L, false, null, "tag-1", true)
        assertEquals(1, updateDebt(debtId, Contract.DebtType.DEBT.value, "encoded-icon-1", "desc-1", date, null, dollarWallet, "note-1", null, 2000L, false, null, "tag-1"))
        checkDebtId(debtId, Contract.DebtType.DEBT.value, "encoded-icon-1", "desc-1", date, null, dollarWallet, "note-1", null, 2000L, false, null, "tag-1")
        assertEquals(dollarWallet, masterTransactionWallet(debtId))
    }

    @Test
    fun movingTheMasterTransactionToAnotherCurrencyMovesNothing() {
        val euroWallet = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val dollarWallet = insertWallet("Test wallet 2", "encoded-icon-2", "USD", "note-wallet-2", true, 2000L, false, "tag-wallet-2")
        val categoryId = getSystemCategory(Contract.CategoryTag.DEBT)
        val paidCategoryId = getSystemCategory(Contract.CategoryTag.PAID_DEBT)
        val date = checkNotNull(DateUtils.getDateFromSQLDateString("2026-07-01"))
        val debtId = insertDebt(Contract.DebtType.DEBT.value, "encoded-icon-1", "desc-1", date, null, euroWallet, "note-1", null, 2000L, false, null, "tag-1", true)
        // The payment is what the move would strand. Without one the debt has nothing left behind
        // and the move is allowed, which movingADebtThatStrandsNothingGoesThrough pins.
        insertTransaction(500L, date, "payment", paidCategoryId, Contract.Direction.EXPENSE,
                Contract.TransactionType.DEBT, euroWallet, null, "payment-note", null, null, debtId, true, true, null, null, "tag-payment")
        val transactionId = masterTransactionId(debtId)
        try {
            updateTransaction(transactionId, 2000L, date, "desc-1", categoryId,
                    Contract.Direction.INCOME, Contract.TransactionType.DEBT, dollarWallet, null,
                    "note-1", null, null, debtId, true, true, null, null, "tag-transaction")
            fail("The master transaction was moved to a wallet in another currency")
        } catch (e: SQLiteDataException) {
            assertEquals(Contract.ErrorCode.WALLETS_NOT_CONSISTENT, e.errorCode)
        }
        checkDebtId(debtId, Contract.DebtType.DEBT.value, "encoded-icon-1", "desc-1", date, null, euroWallet, "note-1", null, 2000L, false, null, "tag-1")
        assertEquals(euroWallet, masterTransactionWallet(debtId))
    }

    private fun masterTransactionWallet(debtId: Long): Long {
        val cursor = checkNotNull(database).getTransaction(
            masterTransactionId(debtId),
            arrayOf(Contract.Transaction.WALLET_ID)
        )
        assertNotNull(cursor)
        val safeCursor = checkNotNull(cursor)
        assertEquals(1, safeCursor.count)
        safeCursor.moveToFirst()
        val walletId = safeCursor.getLong(safeCursor.getColumnIndexOrThrow(Contract.Transaction.WALLET_ID))
        safeCursor.close()
        return walletId
    }

    private fun masterTransactionId(debtId: Long): Long {
        val cursor = checkNotNull(database).getTransactions(
            arrayOf(Contract.Transaction.ID),
            Contract.Transaction.DEBT_ID + " = ? AND " + Contract.Transaction.CATEGORY_ID + " = ?",
            arrayOf(debtId.toString(), getSystemCategory(Contract.CategoryTag.DEBT).toString()),
            null
        )
        assertNotNull(cursor)
        val safeCursor = checkNotNull(cursor)
        assertEquals(1, safeCursor.count)
        safeCursor.moveToFirst()
        val id = safeCursor.getLong(safeCursor.getColumnIndexOrThrow(Contract.Transaction.ID))
        safeCursor.close()
        return id
    }

    @Test
    fun deleteDebt() {
        val id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val id2 = insertPlace("place-2", "encoded-icon-2", "fake-address-2", 7.3467, 8.364, "tag-2")
        val id3 = insertPerson("person-1", "encoded-icon-1", "note-1", "tag-1")
        val id4 = insertPerson("person-2", "encoded-icon-2", "note-2", "tag-2")
        val id5 = insertPerson("person-3", "encoded-icon-3", "note-3", "tag-3")
        val id6 = getSystemCategory(Contract.CategoryTag.PAID_DEBT)
        val date = Date()
        val peopleIds1 = arrayOf<Long?>(id3, id4)
        val id7 = insertDebt(Contract.DebtType.DEBT.value, "encoded-icon-1", "desc-1", date, null, id1, "note-1", id2, 2000L, false, peopleIds1, "tag-1", true)
        insertTransaction(10L, date, "desc", id6, Contract.Direction.EXPENSE, Contract.TransactionType.DEBT, id1, null, null, null, null, id7, true, true, arrayOf(id5), null, "tag")
        insertTransaction(10L, date, "desc", id6, Contract.Direction.EXPENSE, Contract.TransactionType.DEBT, id1, id2, null, null, null, id7, true, true, null, null, "tag")
        insertTransaction(10L, date, "desc", id6, Contract.Direction.EXPENSE, Contract.TransactionType.DEBT, id1, null, null, null, null, id7, true, true, arrayOf(id3, id4), null, "tag")
        insertTransaction(10L, date, "desc", id6, Contract.Direction.EXPENSE, Contract.TransactionType.DEBT, id1, id2, null, null, null, id7, true, true, null, null, "tag")
        checkCursorSize(checkNotNull(database).getDebts(null, null, null, null), 1)
        checkCursorSize(checkNotNull(database).getTransactions(null, null, null, null), 5)
        // now delete the debt and check if all the related transactions are removed
        assertEquals(1, checkNotNull(database).deleteDebt(id7))
        checkCursorSize(checkNotNull(database).getDebts(null, null, null, null), 0)
        checkCursorSize(checkNotNull(database).getTransactions(null, null, null, null), 0)
    }

    @Test
    fun insertBudget() {
        val id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val id2 = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 3000L, true, "tag-wallet-2")
        val id3 = insertWallet("Test wallet 3", "encoded-icon-3", "EUR", "note-wallet-3", true, 1000L, true, "tag-wallet-3")
        val id4 = insertWallet("Test wallet 4", "encoded-icon-4", "USD", "note-wallet-4", true, 2300L, true, "tag-wallet-4")
        val id5 = insertWallet("Test wallet 5", "encoded-icon-5", "USD", "note-wallet-5", true, 3500L, true, "tag-wallet-5")
        val id6 = insertCategory("Test category 1", "encoded-icon", 1, null, true, "category-tag")
        // test insert income budget
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -3)
        val startDate = calendar.time
        calendar.add(Calendar.MONTH, 6)
        val endDate = calendar.time
        val walletIds1 = arrayOf<Long?>(id1, id3)
        val walletIds2 = arrayOf<Long?>(id1, id2, id3)
        val walletIds3 = arrayOf<Long?>(id4, id5)
        val id7 = insertBudget(Schema.BudgetType.INCOMES, null, startDate, endDate, 5000L, "EUR", walletIds1, "tag-1")
        val id8 = insertBudget(Schema.BudgetType.EXPENSES, null, startDate, endDate, 10000L, "EUR", walletIds2, "tag-2")
        val id9 = insertBudget(Schema.BudgetType.CATEGORY, id6, startDate, endDate, 13000L, "USD", walletIds3, "tag-3")
        // now ensure everything is stored correctly
        checkBudgetId(id7, Schema.BudgetType.INCOMES, null, startDate, endDate, 5000L, "EUR", walletIds1, "tag-1", 0L)
        checkBudgetId(id8, Schema.BudgetType.EXPENSES, null, startDate, endDate, 10000L, "EUR", walletIds2, "tag-2", 0L)
        checkBudgetId(id9, Schema.BudgetType.CATEGORY, id6, startDate, endDate, 13000L, "USD", walletIds3, "tag-3", 0L)
    }

    @Test
    fun budgetCoveringSeveralCategoriesCountsThemAll() {
        val wallet = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val food = insertCategory("Food", "encoded-icon-food", 1, null, true, "tag-food")
        val rent = insertCategory("Rent", "encoded-icon-rent", 1, null, true, "tag-rent")
        val petrol = insertCategory("Petrol", "encoded-icon-petrol", 1, null, true, "tag-petrol")
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -3)
        val startDate = calendar.time
        calendar.add(Calendar.MONTH, 6)
        val endDate = calendar.time
        val wallets = arrayOf<Long?>(wallet)
        val budget = insertBudgetCovering(Schema.BudgetType.CATEGORY, arrayOf(food, rent), startDate, endDate, 5000L, "EUR", wallets, "tag-1")
        insertTransaction(100L, Date(), null, food, Contract.Direction.EXPENSE, Contract.TransactionType.STANDARD, wallet, null, null, null, null, null, true, true, null, null, "tag")
        insertTransaction(250L, Date(), null, rent, Contract.Direction.EXPENSE, Contract.TransactionType.STANDARD, wallet, null, null, null, null, null, true, true, null, null, "tag")
        // the control: a category the budget does not cover, in the same wallet and the same
        // period, so a budget that matched on the wallet alone would swallow it
        insertTransaction(700L, Date(), null, petrol, Contract.Direction.EXPENSE, Contract.TransactionType.STANDARD, wallet, null, null, null, null, null, true, true, null, null, "tag")
        checkBudgetCategories(budget, arrayOf(food, rent), "Food, Rent", -350L)
    }

    @Test
    fun budgetNamingOneCategoryStillFillsTheJoinTable() {
        // every caller that predates a budget covering more than one category writes the single
        // column, and the queries read the join table, so the two have to meet
        val wallet = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val food = insertCategory("Food", "encoded-icon-food", 1, null, true, "tag-food")
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -3)
        val startDate = calendar.time
        calendar.add(Calendar.MONTH, 6)
        val endDate = calendar.time
        val budget = insertBudget(Schema.BudgetType.CATEGORY, food, startDate, endDate, 5000L, "EUR", arrayOf(wallet), "tag-1")
        insertTransaction(100L, Date(), null, food, Contract.Direction.EXPENSE, Contract.TransactionType.STANDARD, wallet, null, null, null, null, null, true, true, null, null, "tag")
        checkBudgetCategories(budget, arrayOf(food), "Food", -100L)
    }

    @Test
    fun updateBudgetReplacesTheCategoriesItCovers() {
        val wallet = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val food = insertCategory("Food", "encoded-icon-food", 1, null, true, "tag-food")
        val rent = insertCategory("Rent", "encoded-icon-rent", 1, null, true, "tag-rent")
        val petrol = insertCategory("Petrol", "encoded-icon-petrol", 1, null, true, "tag-petrol")
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -3)
        val startDate = calendar.time
        calendar.add(Calendar.MONTH, 6)
        val endDate = calendar.time
        val wallets = arrayOf<Long?>(wallet)
        val budget = insertBudgetCovering(Schema.BudgetType.CATEGORY, arrayOf(food, rent), startDate, endDate, 5000L, "EUR", wallets, "tag-1")
        insertTransaction(100L, Date(), null, food, Contract.Direction.EXPENSE, Contract.TransactionType.STANDARD, wallet, null, null, null, null, null, true, true, null, null, "tag")
        insertTransaction(250L, Date(), null, rent, Contract.Direction.EXPENSE, Contract.TransactionType.STANDARD, wallet, null, null, null, null, null, true, true, null, null, "tag")
        insertTransaction(700L, Date(), null, petrol, Contract.Direction.EXPENSE, Contract.TransactionType.STANDARD, wallet, null, null, null, null, null, true, true, null, null, "tag")
        checkBudgetCategories(budget, arrayOf(food, rent), "Food, Rent", -350L)
        // food is dropped and petrol is added, so the figure follows the new pair, and the row
        // that the update flagged deleted for rent comes back instead of being written twice
        updateBudgetCovering(budget, Schema.BudgetType.CATEGORY, arrayOf(rent, petrol), startDate, endDate, 5000L, "EUR", wallets, "tag-1")
        checkBudgetCategories(budget, arrayOf(rent, petrol), "Petrol, Rent", -950L)
    }

    @Test(expected = SQLiteDataException::class)
    fun deleteCategoryReachedOnlyThroughTheJoinTable() {
        // the budget row itself names food, so rent is in the join table and nowhere else
        val wallet = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val food = insertCategory("Food", "encoded-icon-food", 1, null, true, "tag-food")
        val rent = insertCategory("Rent", "encoded-icon-rent", 1, null, true, "tag-rent")
        insertBudgetCovering(Schema.BudgetType.CATEGORY, arrayOf(food, rent), Date(), Date(), 3000L, "EUR", arrayOf(wallet), "tag")
        checkNotNull(database).deleteCategory(rent)
    }

    @Test
    fun deleteBudgetTakesItsCategoriesWithIt() {
        val wallet = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val food = insertCategory("Food", "encoded-icon-food", 1, null, true, "tag-food")
        val rent = insertCategory("Rent", "encoded-icon-rent", 1, null, true, "tag-rent")
        val budget = insertBudgetCovering(Schema.BudgetType.CATEGORY, arrayOf(food, rent), Date(), Date(), 3000L, "EUR", arrayOf(wallet), "tag")
        checkNotNull(database).deleteBudget(budget)
        checkCursorSize(checkNotNull(database).getBudget(budget, null), 0)
        // with the join rows gone the categories are free again
        checkNotNull(database).deleteCategory(rent)
        checkNotNull(database).deleteCategory(food)
    }

    @Test(expected = SQLiteDataException::class)
    fun insertBudgetWithNoWallets() {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -3)
        val startDate = calendar.time
        calendar.add(Calendar.MONTH, 6)
        val endDate = calendar.time
        insertBudget(Schema.BudgetType.INCOMES, null, startDate, endDate, 5000L, "EUR", null, "tag-1")
    }

    @Test(expected = SQLiteDataException::class)
    fun insertBudgetWithNotConsistentWallets() {
        val id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val id3 = insertWallet("Test wallet 3", "encoded-icon-3", "EUR", "note-wallet-3", true, 1000L, true, "tag-wallet-3")
        val id4 = insertWallet("Test wallet 4", "encoded-icon-4", "USD", "note-wallet-4", true, 2300L, true, "tag-wallet-4")
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -3)
        val startDate = calendar.time
        calendar.add(Calendar.MONTH, 6)
        val endDate = calendar.time
        val walletIds = arrayOf<Long?>(id1, id3, id4)
        insertBudget(Schema.BudgetType.INCOMES, null, startDate, endDate, 5000L, "EUR", walletIds, "tag-1")
    }

    @Test(expected = SQLiteDataException::class)
    fun insertBudgetWithInvalidWallet() {
        val id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val id2 = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 3000L, true, "tag-wallet-2")
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -3)
        val startDate = calendar.time
        calendar.add(Calendar.MONTH, 6)
        val endDate = calendar.time
        val walletIds = arrayOf<Long?>(id1, id2, id1 + id2)
        insertBudget(Schema.BudgetType.INCOMES, null, startDate, endDate, 5000L, "EUR", walletIds, "tag-1")
    }

    @Test
    fun updateBudget() {
        val id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val id2 = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 3000L, true, "tag-wallet-2")
        val id3 = insertWallet("Test wallet 3", "encoded-icon-3", "EUR", "note-wallet-3", true, 1000L, true, "tag-wallet-3")
        val id4 = insertWallet("Test wallet 4", "encoded-icon-4", "USD", "note-wallet-4", true, 2300L, true, "tag-wallet-4")
        val id5 = insertWallet("Test wallet 5", "encoded-icon-5", "USD", "note-wallet-5", true, 3500L, true, "tag-wallet-5")
        val id6 = insertCategory("Test category 1", "encoded-icon", 1, null, true, "category-tag")
        // test insert income budget
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -3)
        val startDate = calendar.time
        calendar.add(Calendar.MONTH, 6)
        val endDate = calendar.time
        val walletIds1 = arrayOf<Long?>(id1, id3)
        var walletIds2: Array<Long?>? = arrayOf(id1, id2, id3)
        var walletIds3: Array<Long?>? = arrayOf(id4, id5)
        val id7 = insertBudget(Schema.BudgetType.INCOMES, null, startDate, endDate, 5000L, "EUR", walletIds1, "tag-1")
        val id8 = insertBudget(Schema.BudgetType.EXPENSES, null, startDate, endDate, 10000L, "EUR", walletIds2, "tag-2")
        val id9 = insertBudget(Schema.BudgetType.CATEGORY, id6, startDate, endDate, 13000L, "USD", walletIds3, "tag-3")
        // now update budget 8 and 9
        walletIds2 = arrayOf(id5)
        walletIds3 = arrayOf(id4)
        calendar.add(Calendar.MONTH, -1)
        val newStartDate = calendar.time
        calendar.add(Calendar.MONTH, 9)
        val newEndDate = calendar.time
        assertEquals(1, updateBudget(id7, Schema.BudgetType.CATEGORY, id6, startDate, endDate, 1000L, "EUR", walletIds1, "tag-1-edited"))
        assertEquals(1, updateBudget(id8, Schema.BudgetType.EXPENSES, null, startDate, endDate, 5000L, "USD", walletIds2, "tag-2-edited"))
        assertEquals(1, updateBudget(id9, Schema.BudgetType.CATEGORY, id6, newStartDate, newEndDate, 8000L, "USD", walletIds3, "tag-3-edited"))
        // now check that everything has been updated correctly
        checkBudgetId(id7, Schema.BudgetType.CATEGORY, id6, startDate, endDate, 1000L, "EUR", walletIds1, "tag-1-edited", 0L)
        checkBudgetId(id8, Schema.BudgetType.EXPENSES, null, startDate, endDate, 5000L, "USD", walletIds2, "tag-2-edited", 0L)
        checkBudgetId(id9, Schema.BudgetType.CATEGORY, id6, newStartDate, newEndDate, 8000L, "USD", walletIds3, "tag-3-edited", 0L)
    }

    @Test(expected = SQLiteDataException::class)
    fun updateBudgetWithNoWallets() {
        val id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val id2 = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 3000L, true, "tag-wallet-2")
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -3)
        val startDate = calendar.time
        calendar.add(Calendar.MONTH, 6)
        val endDate = calendar.time
        val walletIds = arrayOf<Long?>(id1, id2)
        val id7 = insertBudget(Schema.BudgetType.INCOMES, null, startDate, endDate, 5000L, "EUR", walletIds, "tag-1")
        // now update the budget with no wallets
        updateBudget(id7, Schema.BudgetType.INCOMES, null, startDate, endDate, 5000L, "EUR", null, "tag-1")
    }

    @Test(expected = SQLiteDataException::class)
    fun updateBudgetWithNotConsistentWallets() {
        val id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val id2 = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 3000L, true, "tag-wallet-2")
        val id4 = insertWallet("Test wallet 4", "encoded-icon-4", "USD", "note-wallet-4", true, 2300L, true, "tag-wallet-4")
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -3)
        val startDate = calendar.time
        calendar.add(Calendar.MONTH, 6)
        val endDate = calendar.time
        var walletIds: Array<Long?>? = arrayOf(id1, id2)
        val id7 = insertBudget(Schema.BudgetType.INCOMES, null, startDate, endDate, 5000L, "EUR", walletIds, "tag-1")
        // now update the budget with inconsistent wallets
        walletIds = arrayOf(id1, id4)
        updateBudget(id7, Schema.BudgetType.INCOMES, null, startDate, endDate, 5000L, "EUR", walletIds, "tag-1")
    }

    @Test(expected = SQLiteDataException::class)
    fun updateBudgetWithInvalidWallet() {
        val id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val id2 = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 3000L, true, "tag-wallet-2")
        val id4 = insertWallet("Test wallet 4", "encoded-icon-4", "USD", "note-wallet-4", true, 2300L, true, "tag-wallet-4")
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -3)
        val startDate = calendar.time
        calendar.add(Calendar.MONTH, 6)
        val endDate = calendar.time
        var walletIds: Array<Long?>? = arrayOf(id1, id2)
        val id7 = insertBudget(Schema.BudgetType.INCOMES, null, startDate, endDate, 5000L, "EUR", walletIds, "tag-1")
        // now update the budget with invalid wallets
        walletIds = arrayOf(id1, id2, id1 + id2 + id4)
        updateBudget(id7, Schema.BudgetType.INCOMES, null, startDate, endDate, 5000L, "EUR", walletIds, "tag-1")
    }

    @Test
    fun deleteBudget() {
        val id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val id2 = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 3000L, true, "tag-wallet-2")
        val id3 = insertWallet("Test wallet 3", "encoded-icon-3", "EUR", "note-wallet-3", true, 1000L, true, "tag-wallet-3")
        val id4 = insertWallet("Test wallet 4", "encoded-icon-4", "USD", "note-wallet-4", true, 2300L, true, "tag-wallet-4")
        val id5 = insertWallet("Test wallet 5", "encoded-icon-5", "USD", "note-wallet-5", true, 3500L, true, "tag-wallet-5")
        val id6 = insertCategory("Test category 1", "encoded-icon", 1, null, true, "category-tag")
        // test insert income budget
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -3)
        val startDate = calendar.time
        calendar.add(Calendar.MONTH, 6)
        val endDate = calendar.time
        val walletIds1 = arrayOf<Long?>(id1, id3)
        val walletIds2 = arrayOf<Long?>(id1, id2, id3)
        val walletIds3 = arrayOf<Long?>(id4, id5)
        val id7 = insertBudget(Schema.BudgetType.INCOMES, null, startDate, endDate, 5000L, "EUR", walletIds1, "tag-1")
        val id8 = insertBudget(Schema.BudgetType.EXPENSES, null, startDate, endDate, 10000L, "EUR", walletIds2, "tag-2")
        val id9 = insertBudget(Schema.BudgetType.CATEGORY, id6, startDate, endDate, 13000L, "USD", walletIds3, "tag-3")
        // check budget count
        checkCursorSize(checkNotNull(database).getBudgets(null, null, null, null), 3)
        // now delete all the budget one by one and check the budget count, than ensure all the
        // wallets and the category are not deleted
        assertEquals(1, checkNotNull(database).deleteBudget(id7))
        checkCursorSize(checkNotNull(database).getBudget(id7, null), 0)
        checkCursorSize(checkNotNull(database).getBudgets(null, null, null, null), 2)
        assertEquals(1, checkNotNull(database).deleteBudget(id8))
        checkCursorSize(checkNotNull(database).getBudget(id8, null), 0)
        checkCursorSize(checkNotNull(database).getBudgets(null, null, null, null), 1)
        assertEquals(1, checkNotNull(database).deleteBudget(id9))
        checkCursorSize(checkNotNull(database).getBudget(id9, null), 0)
        checkCursorSize(checkNotNull(database).getBudgets(null, null, null, null), 0)
        checkCursorSize(checkNotNull(database).getWallet(id1, null), 1)
        checkCursorSize(checkNotNull(database).getWallet(id2, null), 1)
        checkCursorSize(checkNotNull(database).getWallet(id3, null), 1)
        checkCursorSize(checkNotNull(database).getWallet(id4, null), 1)
        checkCursorSize(checkNotNull(database).getWallet(id5, null), 1)
        checkCursorSize(checkNotNull(database).getCategory(id6, null), 1)
    }

    @Test
    fun considerExternalTransactionsInIncomeBudget() {
        // Setup wallets
        val wallet1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val wallet2 = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 3000L, false, "tag-wallet-2")
        val wallet3 = insertWallet("Test wallet 3", "encoded-icon-2", "EUR", "note-wallet-2", true, 3000L, false, "tag-wallet-3")

        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -3)
        val startDate = calendar.time
        calendar.add(Calendar.MONTH, 6)
        val endDate = calendar.time
        val wallets = arrayOf<Long?>(wallet1, wallet2)

        // Setup budget of type income
        val incomeBudget = insertBudget(Schema.BudgetType.INCOMES, null, startDate, endDate, 5000L, "EUR", wallets, "tag-1")

        // Setup transfer between wallets not in the budget
        insertTransfer("desc", startDate, wallet3, wallet1, null, 4000L, 4000L, 0L, "note", null, null, true, true, null, null, "tag-1")
        insertTransferModel("desc", wallet3, wallet1, 4000L, 4000L, 0L, "note", null, null, true, true, "tag-2")

        // Transfer should not be added towards the progress of the budgets
        val expectedProgress = 4000L
        checkBudgetId(incomeBudget, Schema.BudgetType.INCOMES, null, startDate, endDate, 5000L, "EUR", wallets, "tag-1", expectedProgress)
    }

    @Test
    fun considerExternalTransactionsInExpenseBudget() {
        // Setup wallets
        val wallet1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val wallet2 = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 3000L, false, "tag-wallet-2")
        val wallet3 = insertWallet("Test wallet 3", "encoded-icon-2", "EUR", "note-wallet-2", true, 3000L, false, "tag-wallet-3")

        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -3)
        val startDate = calendar.time
        calendar.add(Calendar.MONTH, 6)
        val endDate = calendar.time
        val wallets = arrayOf<Long?>(wallet1, wallet2)

        // Setup budget of type expense
        val expenseBudget = insertBudget(Schema.BudgetType.EXPENSES, null, startDate, endDate, 5000L, "EUR", wallets, "tag-1")

        // Setup transfer between wallets not in the budget
        insertTransfer("desc", startDate, wallet1, wallet3, wallet1, 4000L, 4000L, 10L, "note", null, null, true, true, null, null, "tag-1")
        insertTransferModel("desc", wallet1, wallet3, 4000L, 4000L, 10L, "note", null, null, true, true, "tag-2")

        // Transfer should not be added towards the progress of the budgets
        val expectedProgress = 4010L
        checkBudgetId(expenseBudget, Schema.BudgetType.EXPENSES, null, startDate, endDate, 5000L, "EUR", wallets, "tag-1", expectedProgress)
    }

    @Test
    fun ignoreInternalTransactionsInBudget() {
        // Setup wallets
        val wallet1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val wallet2 = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 3000L, false, "tag-wallet-2")

        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -3)
        val startDate = calendar.time
        calendar.add(Calendar.MONTH, 6)
        val endDate = calendar.time
        val wallets = arrayOf<Long?>(wallet1, wallet2)

        // Setup budget of type income
        val incomeBudget = insertBudget(Schema.BudgetType.INCOMES, null, startDate, endDate, 5000L, "EUR", wallets, "tag-1")

        // Setup budget of type expense
        val expenseBudget = insertBudget(Schema.BudgetType.EXPENSES, null, startDate, endDate, 5000L, "EUR", wallets, "tag-2")

        // Setup transfer between the wallets of the budget
        insertTransfer("desc", startDate, wallet1, wallet2, wallet1, 4000L, 4000L, 10L, "note", null, null, true, true, null, null, "tag-1")
        insertTransferModel("desc", wallet1, wallet2, 4000L, 4000L, 10L, "note", null, null, true, true, "tag-2")

        // Transfer should not be added towards the progress of the budgets
        checkBudgetId(incomeBudget, Schema.BudgetType.INCOMES, null, startDate, endDate, 5000L, "EUR", wallets, "tag-1", 0L)
        checkBudgetId(expenseBudget, Schema.BudgetType.EXPENSES, null, startDate, endDate, 5000L, "EUR", wallets, "tag-2", 10L)
    }

    @Test
    fun considerTransactionsWithinBudgetDurationOnly() {
        // Setup wallets
        val wallet1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val wallet2 = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 3000L, false, "tag-wallet-2")

        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -3)
        val startDate = calendar.time
        calendar.add(Calendar.MONTH, 6)
        val endDate = calendar.time
        calendar.add(Calendar.MONTH, 1)
        val afterBudgetDuration = calendar.time
        val wallets = arrayOf<Long?>(wallet1, wallet2)

        // Setup budget of type expense
        val expenseBudget = insertBudget(Schema.BudgetType.EXPENSES, null, startDate, endDate, 5000L, "EUR", wallets, "tag-1")

        // Setup transfers
        insertTransfer("desc", startDate, wallet1, wallet2, wallet1, 4000L, 4000L, 10L, "note", null, null, true, true, null, null, "tag-3")
        insertTransferModel("desc", wallet1, wallet2, 4000L, 4000L, 10L, "note", null, null, true, true, "tag-4")
        insertTransfer("desc", afterBudgetDuration, wallet1, wallet2, wallet1, 4000L, 4000L, 10L, "note", null, null, true, true, null, null, "tag-1")
        insertTransferModel("desc", wallet1, wallet2, 4000L, 4000L, 10L, "note", null, null, true, true, "tag-2")

        // Setup transactions
        val category = insertCategory("Test category 1", "encoded-icon", 1, null, true, "category-tag")
        insertTransaction(100L, startDate, null, category, Contract.Direction.EXPENSE, Contract.TransactionType.STANDARD, wallet1, null, null, null, null, null, true, true, null, null, "tag")
        insertTransaction(100L, afterBudgetDuration, null, category, Contract.Direction.EXPENSE, Contract.TransactionType.STANDARD, wallet1, null, null, null, null, null, true, true, null, null, "tag")

        val expectedProgress = 110L
        checkBudgetId(expenseBudget, Schema.BudgetType.EXPENSES, null, startDate, endDate, 5000L, "EUR", wallets, "tag-1", expectedProgress)
    }


    @Test
    fun getBudgetTransactions() {
        // Setup wallets
        val wallet1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val wallet2 = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 3000L, false, "tag-wallet-2")
        val wallet3 = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 3000L, false, "tag-wallet-3")

        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -3)
        val startDate = calendar.time
        calendar.add(Calendar.MONTH, 6)
        val endDate = calendar.time
        calendar.add(Calendar.MONTH, 1)
        val afterBudgetDuration = calendar.time
        val wallets = arrayOf<Long?>(wallet1, wallet2)

        // Setup budget
        val budget = insertBudget(Schema.BudgetType.EXPENSES, null, startDate, endDate, 5000L, "EUR", wallets, "tag-1")

        // Setup transfers
        insertTransfer("desc", startDate, wallet1, wallet2, wallet1, 4000L, 4000L, 10L, "note", null, null, true, true, null, null, "tag-3")
        insertTransferModel("desc", wallet1, wallet2, 4000L, 4000L, 10L, "note", null, null, true, true, "tag-4")

        insertTransfer("desc", afterBudgetDuration, wallet1, wallet2, wallet1, 4000L, 4000L, 10L, "note", null, null, true, true, null, null, "tag-1")
        insertTransferModel("desc", wallet1, wallet2, 4000L, 4000L, 10L, "note", null, null, true, true, "tag-2")

        insertTransfer("desc", startDate, wallet1, wallet3, wallet1, 4000L, 4000L, 10L, "note", null, null, true, true, null, null, "tag-1")
        insertTransferModel("desc", wallet1, wallet3, 4000L, 4000L, 10L, "note", null, null, true, true, "tag-2")

        // Setup transactions
        val customCategory = insertCategory("Test category 1", "encoded-icon", 1, null, true, "category-tag")
        insertTransaction(100L, startDate, null, customCategory, Contract.Direction.EXPENSE, Contract.TransactionType.STANDARD, wallet1, null, null, null, null, null, true, true, null, null, "tag")
        insertTransaction(100L, afterBudgetDuration, null, customCategory, Contract.Direction.EXPENSE, Contract.TransactionType.STANDARD, wallet1, null, null, null, null, null, true, true, null, null, "tag")

        val transactions = checkNotNull(checkNotNull(database).getBudgetTransactions(budget, null, null, null, null))
        try {
            assertEquals(4, transactions.count)
            assertEquals(true, transactions.moveToFirst())
            assertEquals(context.getString(R.string.system_category_transfer_tax), transactions.getString(transactions.getColumnIndexOrThrow(Contract.Transaction.CATEGORY_NAME)))
            assertEquals(true, transactions.moveToNext())
            assertEquals(context.getString(R.string.system_category_transfer), transactions.getString(transactions.getColumnIndexOrThrow(Contract.Transaction.CATEGORY_NAME)))
            assertEquals(true, transactions.moveToNext())
            assertEquals(context.getString(R.string.system_category_transfer_tax), transactions.getString(transactions.getColumnIndexOrThrow(Contract.Transaction.CATEGORY_NAME)))
        } finally {
            transactions.close()
        }
    }

    @Test
    fun insertSaving() {
        val id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val exp = Date()
        val id2 = insertSaving("desc-1", "encoded-icon", 0L, 10000L, id1, null, false, "note-1", "tag-1")
        val id3 = insertSaving("desc-2", "encoded-icon", 500L, 23000L, id1, exp, true, "note-2", "tag-2")
        checkSavingId(id2, "desc-1", "encoded-icon", 0L, 10000L, id1, null, false, "note-1", "tag-1")
        checkSavingId(id3, "desc-2", "encoded-icon", 500L, 23000L, id1, exp, true, "note-2", "tag-2")
    }

    @Test
    fun updateSaving() {
        val id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val exp = Date()
        val id2 = insertSaving("desc-1", "encoded-icon", 0L, 10000L, id1, null, false, "note-1", "tag-1")
        val id3 = insertSaving("desc-2", "encoded-icon", 500L, 23000L, id1, exp, true, "note-2", "tag-2")
        // now update the saving
        assertEquals(1, updateSaving(id2, "desc-1-edited", "encoded-icon-edited", 3L, 100L, id1, exp, true, "note-1-edited", "tag-1-edited"))
        assertEquals(1, updateSaving(id3, "desc-2-edited", "encoded-icon-edited", 50L, 73000L, id1, null, false, "note-2-edited", "tag-2-edited"))
        // now check that both savings have been successfully update
        checkSavingId(id2, "desc-1-edited", "encoded-icon-edited", 3L, 100L, id1, exp, true, "note-1-edited", "tag-1-edited")
        checkSavingId(id3, "desc-2-edited", "encoded-icon-edited", 50L, 73000L, id1, null, false, "note-2-edited", "tag-2-edited")
    }

    @Test
    fun movingASavingAwayFromTheCurrencyItsDepositsAreInIsRefused() {
        val euroWallet = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val dollarWallet = insertWallet("Test wallet 2", "encoded-icon-2", "USD", "note-wallet-2", true, 2000L, false, "tag-wallet-2")
        val depositCategoryId = getSystemCategory(Contract.CategoryTag.SAVING_DEPOSIT)
        val date = checkNotNull(DateUtils.getDateFromSQLDateString("2026-07-01"))
        val savingId = insertSaving("desc-1", "encoded-icon", 0L, 10000L, euroWallet, null, false, "note-1", "tag-1")
        insertTransaction(500L, date, "deposit", depositCategoryId, Contract.Direction.EXPENSE,
                Contract.TransactionType.SAVING, euroWallet, null, "deposit-note", null, savingId, null, true, true, null, null, "tag-deposit")
        try {
            updateSaving(savingId, "desc-1", "encoded-icon", 0L, 10000L, dollarWallet, null, false, "note-1", "tag-1")
            fail("The saving was moved away from the currency its deposits are in")
        } catch (e: SQLiteDataException) {
            assertEquals(Contract.ErrorCode.WALLETS_NOT_CONSISTENT, e.errorCode)
        }
        checkSavingId(savingId, "desc-1", "encoded-icon", 0L, 10000L, euroWallet, null, false, "note-1", "tag-1")
    }

    @Test
    fun movingASavingBackToTheCurrencyItsDepositsAreInIsAllowed() {
        val euroWallet = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val dollarWallet = insertWallet("Test wallet 2", "encoded-icon-2", "USD", "note-wallet-2", true, 2000L, false, "tag-wallet-2")
        val depositCategoryId = getSystemCategory(Contract.CategoryTag.SAVING_DEPOSIT)
        val date = checkNotNull(DateUtils.getDateFromSQLDateString("2026-07-01"))
        val savingId = insertSaving("desc-1", "encoded-icon", 0L, 10000L, euroWallet, null, false, "note-1", "tag-1")
        insertTransaction(500L, date, "deposit", depositCategoryId, Contract.Direction.EXPENSE,
                Contract.TransactionType.SAVING, dollarWallet, null, "deposit-note", null, savingId, null, true, true, null, null, "tag-deposit")
        assertEquals(1, updateSaving(savingId, "desc-1", "encoded-icon", 0L, 10000L, dollarWallet, null, false, "note-1", "tag-1"))
        checkSavingId(savingId, "desc-1", "encoded-icon", 0L, 10000L, dollarWallet, null, false, "note-1", "tag-1")
    }

    @Test
    fun movingASavingToAWalletInTheSameCurrencyGoesThrough() {
        val walletId = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val otherWalletId = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 2000L, false, "tag-wallet-2")
        val depositCategoryId = getSystemCategory(Contract.CategoryTag.SAVING_DEPOSIT)
        val date = checkNotNull(DateUtils.getDateFromSQLDateString("2026-07-01"))
        val savingId = insertSaving("desc-1", "encoded-icon", 0L, 10000L, walletId, null, false, "note-1", "tag-1")
        insertTransaction(500L, date, "deposit", depositCategoryId, Contract.Direction.EXPENSE,
                Contract.TransactionType.SAVING, walletId, null, "deposit-note", null, savingId, null, true, true, null, null, "tag-deposit")
        assertEquals(1, updateSaving(savingId, "desc-1", "encoded-icon", 0L, 10000L, otherWalletId, null, false, "note-1", "tag-1"))
        checkSavingId(savingId, "desc-1", "encoded-icon", 0L, 10000L, otherWalletId, null, false, "note-1", "tag-1")
    }

    @Test
    fun movingASavingThatStrandsNothingGoesThrough() {
        val euroWallet = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val dollarWallet = insertWallet("Test wallet 2", "encoded-icon-2", "USD", "note-wallet-2", true, 2000L, false, "tag-wallet-2")
        val savingId = insertSaving("desc-1", "encoded-icon", 0L, 10000L, euroWallet, null, false, "note-1", "tag-1")
        assertEquals(1, updateSaving(savingId, "desc-1", "encoded-icon", 0L, 10000L, dollarWallet, null, false, "note-1", "tag-1"))
        checkSavingId(savingId, "desc-1", "encoded-icon", 0L, 10000L, dollarWallet, null, false, "note-1", "tag-1")
    }

    @Test
    fun savingProgressCountsOnlyTheRowsThatHaveLanded() {
        val wallet = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 0L, false, "tag-wallet-1")
        val saving = insertSaving("desc-1", "encoded-icon", 0L, 10000L, wallet, null, false, "note-1", "tag-1")
        val deposit = getSystemCategory(Contract.CategoryTag.SAVING_DEPOSIT)
        val withdraw = getSystemCategory(Contract.CategoryTag.SAVING_WITHDRAW)
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, 1)
        val nextMonth = calendar.time
        // Landed, so the progress counts them: a confirmed deposit of 1000 and a confirmed
        // withdrawal of 100, both dated now. The withdrawal is the only landed row counted
        // negatively, so without it the progress cannot tell a rule that counts every landed row
        // from one that counts only landed deposits.
        insertTransaction(1000L, Date(), null, deposit, Contract.Direction.EXPENSE, Contract.TransactionType.SAVING, wallet, null, null, null, saving, null, true, true, null, null, "tag")
        insertTransaction(100L, Date(), null, withdraw, Contract.Direction.INCOME, Contract.TransactionType.SAVING, wallet, null, null, null, saving, null, true, true, null, null, "tag")
        // Left out by the date alone, one on each side: a confirmed deposit of 400 and a
        // confirmed withdrawal of 300, both dated next month. Without them a rule with no date
        // test at all reads the same as this one.
        insertTransaction(400L, nextMonth, null, deposit, Contract.Direction.EXPENSE, Contract.TransactionType.SAVING, wallet, null, null, null, saving, null, true, true, null, null, "tag")
        insertTransaction(300L, nextMonth, null, withdraw, Contract.Direction.INCOME, Contract.TransactionType.SAVING, wallet, null, null, null, saving, null, true, true, null, null, "tag")
        // Left out by the confirmed test alone, one on each side: an unconfirmed withdrawal of
        // 200 and an unconfirmed deposit of 500, both dated now. Without them a rule that counts
        // every row dated at or before now reads the same as this one.
        insertTransaction(200L, Date(), null, withdraw, Contract.Direction.INCOME, Contract.TransactionType.SAVING, wallet, null, null, null, saving, null, false, true, null, null, "tag")
        insertTransaction(500L, Date(), null, deposit, Contract.Direction.EXPENSE, Contract.TransactionType.SAVING, wallet, null, null, null, saving, null, false, true, null, null, "tag")
        // Left out by both, an unconfirmed withdrawal of 50 dated next month, so a rule that
        // drops one test still has to drop the other to reach it.
        insertTransaction(50L, nextMonth, null, withdraw, Contract.Direction.INCOME, Contract.TransactionType.SAVING, wallet, null, null, null, saving, null, false, true, null, null, "tag")

        val cursor = checkNotNull(checkNotNull(database).getSavings(null, null, null, null))
        assertEquals(true, cursor.moveToFirst())
        assertEquals(900L, cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Saving.PROGRESS)))
        cursor.close()
    }

    @Test
    fun deleteSaving() {
        val id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val exp = Date()
        val id2 = insertSaving("desc-1", "encoded-icon", 0L, 10000L, id1, null, false, "note-1", "tag-1")
        // we can now add some transactions to this saving
        val id3 = getSystemCategory(Contract.CategoryTag.SAVING_DEPOSIT)
        val id4 = getSystemCategory(Contract.CategoryTag.SAVING_WITHDRAW)
        insertTransaction(10L, Date(), null, id3, Contract.Direction.EXPENSE, Contract.TransactionType.SAVING, id1, null, null, null, id2, null, true, true, null, null, "tag")
        insertTransaction(20L, Date(), null, id3, Contract.Direction.EXPENSE, Contract.TransactionType.SAVING, id1, null, null, null, id2, null, true, true, null, null, "tag")
        insertTransaction(30L, Date(), null, id4, Contract.Direction.INCOME, Contract.TransactionType.SAVING, id1, null, null, null, id2, null, true, true, null, null, "tag")
        insertTransaction(10L, Date(), null, id3, Contract.Direction.EXPENSE, Contract.TransactionType.SAVING, id1, null, null, null, id2, null, true, true, null, null, "tag")
        insertTransaction(20L, Date(), null, id3, Contract.Direction.EXPENSE, Contract.TransactionType.SAVING, id1, null, null, null, id2, null, true, true, null, null, "tag")
        insertTransaction(30L, Date(), null, id4, Contract.Direction.INCOME, Contract.TransactionType.SAVING, id1, null, null, null, id2, null, true, true, null, null, "tag")
        // now check the number of savings and transactions
        checkCursorSize(checkNotNull(database).getSavings(null, null, null, null), 1)
        checkCursorSize(checkNotNull(database).getTransactions(null, null, null, null), 6)
        // now delete the saving
        assertEquals(1, checkNotNull(database).deleteSaving(id2))
        // now ensure that all the transactions and the saving have been deleted
        checkCursorSize(checkNotNull(database).getSavings(null, null, null, null), 0)
        checkCursorSize(checkNotNull(database).getTransactions(null, null, null, null), 0)
    }

    @Test
    fun insertEvent() {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -4)
        val startDate = calendar.time
        val endDate = Date()
        val id1 = insertEvent("Event 1", "encoded-icon-1", startDate, endDate, "note-1", "tag-1")
        val id2 = insertEvent("Event 2", "encoded-icon-2", startDate, endDate, "note-2", "tag-2")
        val id3 = insertEvent("Event 3", "encoded-icon-3", startDate, endDate, "note-3", "tag-3")
        checkEventId(id1, "Event 1", "encoded-icon-1", startDate, endDate, "note-1", "tag-1")
        checkEventId(id2, "Event 2", "encoded-icon-2", startDate, endDate, "note-2", "tag-2")
        checkEventId(id3, "Event 3", "encoded-icon-3", startDate, endDate, "note-3", "tag-3")
    }

    @Test
    fun updateEvent() {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -4)
        val startDate = calendar.time
        val endDate = Date()
        val id1 = insertEvent("Event 1", "encoded-icon-1", startDate, endDate, "note-1", "tag-1")
        val id2 = insertEvent("Event 2", "encoded-icon-2", startDate, endDate, "note-2", "tag-2")
        val id3 = insertEvent("Event 3", "encoded-icon-3", startDate, endDate, "note-3", "tag-3")
        // update event 3
        calendar.add(Calendar.MONTH, 2)
        val newStartDate = calendar.time
        calendar.add(Calendar.MONTH, 5)
        val newEndDate = calendar.time
        assertEquals(1, updateEvent(id3, "Event 3-edited", "encoded-icon-3-edited", newStartDate, newEndDate, "note-3-edited", "tag-3-edited"))
        // check
        checkEventId(id1, "Event 1", "encoded-icon-1", startDate, endDate, "note-1", "tag-1")
        checkEventId(id2, "Event 2", "encoded-icon-2", startDate, endDate, "note-2", "tag-2")
        checkEventId(id3, "Event 3-edited", "encoded-icon-3-edited", newStartDate, newEndDate, "note-3-edited", "tag-3-edited")
    }

    @Test
    fun deleteEvent() {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -4)
        val startDate = calendar.time
        val endDate = Date()
        val id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val id2 = insertCategory("Test category 1", "encoded-icon-1", 0, null, true, "tag-1")
        val id3 = insertEvent("Event 1", "encoded-icon-1", startDate, endDate, "note-1", "tag-1")
        // add the event to all the possible items
        val id4 = insertTransaction(2000L, endDate, "desc", id2, Contract.Direction.INCOME, 0, id1, null, "note-1", id3, null, null, true, true, null, null, "tag-1")
        val id5 = insertTransactionModel(4503L, "desc", id2, Contract.Direction.INCOME, id1, null, "note", id3, true, true, "tag")
        val id6 = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 2200L, false, "tag-wallet-2")
        val id7 = insertTransfer("desc", endDate, id1, id6, null, 4000L, 4000L, 0L, "note", null, id3, true, true, null, null, "tag-1")
        val id8 = insertTransferModel("desc", id1, id6, 4000L, 4000L, 0L, "note", null, id3, true, true, "tag-8")
        assertEquals(1, checkNotNull(database).deleteEvent(id3))
        checkTransactionId(id4, 2000L, endDate, "desc", id2, Contract.Direction.INCOME, 0, id1, null, "note-1", null, null, null, true, true, null, "tag-1")
        checkTransactionModelId(id5, 4503L, "desc", id2, Contract.Direction.INCOME, id1, null, "note", null, true, true, "tag")
        checkTransferId(id7, "desc", endDate, id1, id6, null, 4000L, 4000L, 0L, "note", null, null, true, true, null, "tag-1")
        checkTransferModelId(id8, "desc", id1, id6, 4000L, 4000L, 0L, "note", null, null, true, true, "tag-8")
    }

    @Test
    fun insertTransactionModel() {
        val id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val id2 = insertCategory("Test category 1", "encoded-icon-1", 0, null, true, "tag-1")
        val id3 = insertTransactionModel(302L, "desc-1", id2, Contract.Direction.INCOME, id1, null, "note-1", null, true, true, "tag-1")
        val id4 = insertTransactionModel(275L, "desc-2", id2, Contract.Direction.INCOME, id1, null, "note-2", null, false, false, "tag-2")
        checkTransactionModelId(id3, 302L, "desc-1", id2, Contract.Direction.INCOME, id1, null, "note-1", null, true, true, "tag-1")
        checkTransactionModelId(id4, 275L, "desc-2", id2, Contract.Direction.INCOME, id1, null, "note-2", null, false, false, "tag-2")
    }

    @Test
    fun updateTransactionModel() {
        val id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val id2 = insertCategory("Test category 1", "encoded-icon-1", 0, null, true, "tag-1")
        val id3 = insertTransactionModel(302L, "desc-1", id2, Contract.Direction.INCOME, id1, null, "note-1", null, true, true, "tag-1")
        val id4 = insertTransactionModel(275L, "desc-2", id2, Contract.Direction.INCOME, id1, null, "note-2", null, false, false, "tag-2")
        // update transaction model 3
        assertEquals(1, updateTransactionModel(id3, 302L, "desc-1-edited", id2, Contract.Direction.EXPENSE, id1, null, "note-1-edited", null, false, true, "tag-1-edited"))
        // check transaction models
        checkTransactionModelId(id3, 302L, "desc-1-edited", id2, Contract.Direction.EXPENSE, id1, null, "note-1-edited", null, false, true, "tag-1-edited")
        checkTransactionModelId(id4, 275L, "desc-2", id2, Contract.Direction.INCOME, id1, null, "note-2", null, false, false, "tag-2")
    }

    @Test
    fun deleteTransactionModel() {
        val id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val id2 = insertCategory("Test category 1", "encoded-icon-1", 0, null, true, "tag-1")
        val id3 = insertTransactionModel(302L, "desc-1", id2, Contract.Direction.INCOME, id1, null, "note-1", null, true, true, "tag-1")
        val id4 = insertTransactionModel(275L, "desc-2", id2, Contract.Direction.INCOME, id1, null, "note-2", null, false, false, "tag-2")
        // check the current count of transaction models
        checkCursorSize(checkNotNull(database).getTransactionModels(null, null, null, null), 2)
        // now delete the two transaction models
        assertEquals(1, checkNotNull(database).deleteTransactionModel(id3))
        assertEquals(1, checkNotNull(database).deleteTransactionModel(id4))
        // recheck the current count of transaction models
        checkCursorSize(checkNotNull(database).getTransactionModels(null, null, null, null), 0)
    }

    @Test
    fun insertTransferModel() {
        val id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val id2 = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 3000L, true, "tag-wallet-2")
        val id3 = insertTransferModel("desc-1", id1, id2, 10L, 10L, 0L, "note-1", null, null, true, true, "tag-1")
        val id4 = insertTransferModel("desc-2", id1, id2, 5L, 5L, 10L, "note-2", null, null, true, false, "tag-2")
        checkTransferModelId(id3, "desc-1", id1, id2, 10L, 10L, 0L, "note-1", null, null, true, true, "tag-1")
        checkTransferModelId(id4, "desc-2", id1, id2, 5L, 5L, 10L, "note-2", null, null, true, false, "tag-2")
    }

    @Test
    fun updateTransferModel() {
        val id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val id2 = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 3000L, true, "tag-wallet-2")
        val id3 = insertTransferModel("desc-1", id1, id2, 10L, 10L, 0L, "note-1", null, null, true, true, "tag-1")
        val id4 = insertTransferModel("desc-2", id1, id2, 5L, 5L, 10L, "note-2", null, null, true, false, "tag-2")
        // now update the transfer model 3
        assertEquals(1, updateTransferModel(id3, "desc-1-edited", id1, id2, 20L, 20L, 3L, "note-1-edited", null, null, false, true, "tag-1-edited"))
        // check the transfer models
        checkTransferModelId(id3, "desc-1-edited", id1, id2, 20L, 20L, 3L, "note-1-edited", null, null, false, true, "tag-1-edited")
        checkTransferModelId(id4, "desc-2", id1, id2, 5L, 5L, 10L, "note-2", null, null, true, false, "tag-2")
    }

    @Test
    fun deleteTransferModel() {
        val id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val id2 = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 3000L, true, "tag-wallet-2")
        val id3 = insertTransferModel("desc-1", id1, id2, 10L, 10L, 0L, "note-1", null, null, true, true, "tag-1")
        val id4 = insertTransferModel("desc-2", id1, id2, 5L, 5L, 10L, "note-2", null, null, true, false, "tag-2")
        // check the current count of transaction models
        checkCursorSize(checkNotNull(database).getTransferModels(null, null, null, null), 2)
        // now delete the two transaction models
        assertEquals(1, checkNotNull(database).deleteTransferModel(id3))
        assertEquals(1, checkNotNull(database).deleteTransferModel(id4))
        // recheck the current count of transaction models
        checkCursorSize(checkNotNull(database).getTransferModels(null, null, null, null), 0)
    }

    @Test
    fun insertPlace() {
        val id1 = insertPlace("place-1", "encoded-icon-1", "fake-address-1", null, null, "tag-1")
        val id2 = insertPlace("place-2", "encoded-icon-2", "fake-address-2", 7.3467, 8.364, "tag-2")
        checkPlaceId(id1, "place-1", "encoded-icon-1", "fake-address-1", null, null, "tag-1")
        checkPlaceId(id2, "place-2", "encoded-icon-2", "fake-address-2", 7.3467, 8.364, "tag-2")
    }

    @Test
    fun updatePlace() {
        val id1 = insertPlace("place-1", "encoded-icon-1", "fake-address-1", null, null, "tag-1")
        val id2 = insertPlace("place-2", "encoded-icon-2", "fake-address-2", 7.3467, 8.364, "tag-2")
        // now update place 2
        assertEquals(1, updatePlace(id2, "place-2-edited", "encoded-icon-2-edited", "fake-address-2-edited", 2.354, 1.783, "tag-2-edited"))
        // check places
        checkPlaceId(id1, "place-1", "encoded-icon-1", "fake-address-1", null, null, "tag-1")
        checkPlaceId(id2, "place-2-edited", "encoded-icon-2-edited", "fake-address-2-edited", 2.354, 1.783, "tag-2-edited")
    }

    @Test
    fun deletePlace() {
        val id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val id2 = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 3000L, true, "tag-wallet-2")
        val id3 = insertCategory("Test category 1", "encoded-icon-1", 0, null, true, "tag-1")
        val id4 = insertPlace("place-1", "encoded-icon-1", "fake-address-1", null, null, "tag-1")
        // now create one element for every item that can be linked to this place
        val date = Date()
        val id5 = insertDebt(Contract.DebtType.DEBT.value, "icon", "desc", date, null, id1, null, id4, 10000L, false, null, null, false)
        val id6 = insertTransaction(10L, date, "desc", id3, Contract.Direction.INCOME, 0, id1, id4, null, null, null, null, true, true, null, null, null)
        val id7 = insertTransfer("desc", date, id1, id2, null, 10L, 10L, 0L, null, id4, null, true, true, null, null, null)
        val id8 = insertTransactionModel(10L, "desc", id3, Contract.Direction.INCOME, id1, id4, null, null, true, true, null)
        val id9 = insertTransferModel("desc", id1, id2, 10L, 10L, 0L, null, id4, null, true, true, null)
        // now delete the place
        assertEquals(1, checkNotNull(database).deletePlace(id4))
        // check if items has no place as expected
        checkCursorSize(checkNotNull(database).getPlaces(null, null, null, null), 0)
        checkDebtId(id5, Contract.DebtType.DEBT.value, "icon", "desc", date, null, id1, null, null, 10000L, false, null, null)
        checkTransactionId(id6, 10L, date, "desc", id3, Contract.Direction.INCOME, 0, id1, null, null, null, null, null, true, true, null, null)
        checkTransferId(id7, "desc", date, id1, id2, null, 10L, 10L, 0L, null, null, null, true, true, null, null)
        checkTransactionModelId(id8, 10L, "desc", id3, Contract.Direction.INCOME, id1, null, null, null, true, true, null)
        checkTransferModelId(id9, "desc", id1, id2, 10L, 10L, 0L, null, null, null, true, true, null)
    }

    @Test
    fun insertPerson() {
        val id1 = insertPerson("person-1", "encoded-icon-1", "note-1", "tag-1")
        val id2 = insertPerson("person-2", "encoded-icon-2", "note-2", "tag-2")
        checkPersonId(id1, "person-1", "encoded-icon-1", "note-1", "tag-1")
        checkPersonId(id2, "person-2", "encoded-icon-2", "note-2", "tag-2")
    }

    @Test
    fun updatePerson() {
        val id1 = insertPerson("person-1", "encoded-icon-1", "note-1", "tag-1")
        val id2 = insertPerson("person-2", "encoded-icon-2", "note-2", "tag-2")
        // now update person 2
        assertEquals(1, updatePerson(id2, "person-2-edited", "encoded-icon-2-edited", "note-2-edited", "tag-2-edited"))
        // check people
        checkPersonId(id1, "person-1", "encoded-icon-1", "note-1", "tag-1")
        checkPersonId(id2, "person-2-edited", "encoded-icon-2-edited", "note-2-edited", "tag-2-edited")
    }

    @Test
    fun deletePerson() {
        val id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1")
        val id2 = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 3000L, true, "tag-wallet-2")
        val id3 = insertCategory("Test category 1", "encoded-icon-1", 0, null, true, "tag-1")
        val id4 = insertPerson("person-1", "encoded-icon-1", "note-1", "tag-1")
        val id5 = insertPerson("person-2", "encoded-icon-2", "note-2", "tag-2")
        val id6 = insertPerson("person-3", "encoded-icon-3", "note-3", "tag-3")
        // now attach the person 5 to every item that supports it
        val date = Date()
        val id7 = insertDebt(Contract.DebtType.DEBT.value, "icon", "desc", date, null, id1, null, null, 10000L, false, arrayOf(id5, id6), null, false)
        val id8 = insertTransaction(10L, date, "desc", id3, Contract.Direction.INCOME, 0, id1, null, null, null, null, null, true, true, arrayOf(id4, id5), null, null)
        val id9 = insertTransfer("desc", date, id1, id2, null, 10L, 10L, 0L, null, null, null, true, true, arrayOf(id5), null, null)
        // now delete the person 5
        assertEquals(1, checkNotNull(database).deletePerson(id5))
        checkCursorSize(checkNotNull(database).getPeople(null, null, null, null), 2)
        // now check that every item has no more person 5 in list
        checkDebtId(id7, Contract.DebtType.DEBT.value, "icon", "desc", date, null, id1, null, null, 10000L, false, arrayOf(id6), null)
        checkTransactionId(id8, 10L, date, "desc", id3, Contract.Direction.INCOME, 0, id1, null, null, null, null, null, true, true, arrayOf(id4), null)
        checkTransferId(id9, "desc", date, id1, id2, null, 10L, 10L, 0L, null, null, null, true, true, null, null)
        // Every accessor the checks above read through filters on deleted = 0, so a row flagged
        // as deleted and a row that is gone look identical through all of them. Ask the tables
        // for the flag instead. The fixture gave person 5 a row in each of the four tables
        // asserted below.
        //
        // The five deletes in deletePerson used to share one boolean and could only go soft
        // together. That boolean is gone and nothing couples them now, so any subset of the five
        // can regress on its own, and this sees only the subsets that include the person delete,
        // because a real delete of the person row cascades every flagged link row away.
        //
        // The person table is asserted last on purpose. Put it first and a maintainer fixing the
        // person delete brings the cascade back, which hides every link delete still writing a
        // flag and lets the test go green with four of the five wrong. Last, each fix uncovers
        // the next failure.
        //
        // EventPeople is the one other table deletePerson writes, and it is not asserted here
        // because the fixture never links person 5 to an event, so the count would come back
        // zero whichever branch ran.
        assertRowCount(Schema.DebtPeople.TABLE, Schema.DebtPeople.DELETED + " = 1", 0)
        assertRowCount(Schema.TransactionPeople.TABLE, Schema.TransactionPeople.DELETED + " = 1", 0)
        assertRowCount(Schema.TransferPeople.TABLE, Schema.TransferPeople.DELETED + " = 1", 0)
        assertRowCount(Schema.Person.TABLE, Schema.Person.DELETED + " = 1", 0)
    }

    @Test
    fun insertAttachment() {
        val id1 = insertAttachment("path1", "name-1", "mime-type-1", 90L, "tag-1")
        val id2 = insertAttachment("path2", "name-2", "mime-type-2", 4560L, "tag-2")
        checkAttachmentId(id1, "path1", "name-1", "mime-type-1", 90L, "tag-1")
        checkAttachmentId(id2, "path2", "name-2", "mime-type-2", 4560L, "tag-2")
    }

    @Test
    fun updateAttachment() {
        val id1 = insertAttachment("path1", "name-1", "mime-type-1", 90L, "tag-1")
        val id2 = insertAttachment("path2", "name-2", "mime-type-2", 4560L, "tag-2")
        // now update attachment 2
        assertEquals(1, updateAttachment(id2, "path2-edited", "name-2-edited", "mime-type-2-edited", 3647L, "tag-2-edited"))
        // check attachments
        checkAttachmentId(id1, "path1", "name-1", "mime-type-1", 90L, "tag-1")
        checkAttachmentId(id2, "path2-edited", "name-2-edited", "mime-type-2-edited", 3647L, "tag-2-edited")
    }

    @Test
    fun deleteAttachment() {
        val id1 = insertAttachment("path1", "name-1", "mime-type-1", 90L, "tag-1")
        val id2 = insertAttachment("path2", "name-2", "mime-type-2", 4560L, "tag-2")
        checkCursorSize(checkNotNull(database).getAttachments(null, null, null, null), 2)
        assertEquals(1, checkNotNull(database).deleteAttachment(id1))
        checkCursorSize(checkNotNull(database).getAttachments(null, null, null, null), 1)
        assertEquals(1, checkNotNull(database).deleteAttachment(id2))
        checkCursorSize(checkNotNull(database).getAttachments(null, null, null, null), 0)
    }

    @Test
    fun reportFilterDropsAHiddenCategoryAndAHiddenParent() {
        val wallet = insertWallet("Wallet", "icon", "EUR", null, true, 0L, false, "tag-wallet")
        val shown = insertCategory("Shown", "icon", 0, null, true, "tag-shown")
        val hidden = insertCategory("Hidden", "icon", 0, null, false, "tag-hidden")
        val shownChild = insertCategory("Shown child", "icon", 0, shown, true, "tag-shown-child")
        val hiddenChild = insertCategory("Hidden child", "icon", 0, shown, false, "tag-hidden-child")
        val childOfHidden = insertCategory("Child of hidden", "icon", 0, hidden, true, "tag-child-of-hidden")
        val date = Date()
        insertTransaction(100L, date, "on a shown category", shown, Contract.Direction.EXPENSE, 0, wallet, null, null, null, null, null, true, true, null, null, "tag-1")
        insertTransaction(100L, date, "on a hidden category", hidden, Contract.Direction.EXPENSE, 0, wallet, null, null, null, null, null, true, true, null, null, "tag-2")
        insertTransaction(100L, date, "on a shown child", shownChild, Contract.Direction.EXPENSE, 0, wallet, null, null, null, null, null, true, true, null, null, "tag-3")
        insertTransaction(100L, date, "on a hidden child", hiddenChild, Contract.Direction.EXPENSE, 0, wallet, null, null, null, null, null, true, true, null, null, "tag-4")
        insertTransaction(100L, date, "on a shown child of a hidden category", childOfHidden, Contract.Direction.EXPENSE, 0, wallet, null, null, null, null, null, true, true, null, null, "tag-5")
        // the reports keep the two rows whose own category and parent are both shown
        val projection = arrayOf(Contract.Transaction.ID, Contract.Transaction.CATEGORY_ID)
        checkCursorSize(checkNotNull(database).getTransactions(projection, Contract.Transaction.REPORT_FILTER, null, null), 2)
        // reading the own flag alone keeps the child of a hidden category, which is the third row
        checkCursorSize(checkNotNull(database).getTransactions(projection, Contract.Transaction.CATEGORY_SHOW_REPORT + " = 1", null, null), 3)
        checkCursorSize(checkNotNull(database).getTransactions(projection, null, null, null), 5)
    }
}
