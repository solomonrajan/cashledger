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

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CategoryRuleUpdateTest {

    private lateinit var database: SQLDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = SQLDatabase(context, NAME)
        val db = database.writableDatabase
        insertCategory(db, FOOD, "Food", "category-food")
        insertCategory(db, PETROL, "Petrol", "category-petrol")
    }

    private fun insertCategory(db: SQLiteDatabase, id: Long, name: String, uuid: String) {
        db.execSQL(
            "INSERT INTO categories (category_id, category_name, category_icon, category_type, uuid, last_edit, deleted) VALUES (?, ?, ?, ?, ?, ?, 0)",
            arrayOf<Any>(id, name, ICON, Schema.CategoryType.EXPENSE, uuid, EDIT)
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun anUpdateCarryingOnlyTheIndexLeavesThePatternAndTheCategoryAlone() {
        val insert = ContentValues().apply {
            put(Contract.CategoryRule.PATTERN, "tesco")
            put(Contract.CategoryRule.CATEGORY_ID, FOOD)
            put(Contract.CategoryRule.INDEX, 3)
        }
        val ruleId = database.insertCategoryRule(insert)
        assertTrue(ruleId > 0)

        val other = ContentValues().apply {
            put(Contract.CategoryRule.PATTERN, "aldi")
            put(Contract.CategoryRule.CATEGORY_ID, PETROL)
            put(Contract.CategoryRule.INDEX, 5)
        }
        val otherId = database.insertCategoryRule(other)
        assertTrue(otherId > 0)

        val update = ContentValues().apply {
            put(Contract.CategoryRule.INDEX, 2)
        }
        assertEquals(1, database.updateCategoryRule(ruleId, update))

        val otherUpdate = ContentValues().apply {
            put(Contract.CategoryRule.INDEX, 1)
        }
        assertEquals(1, database.updateCategoryRule(otherId, otherUpdate))

        val projection = arrayOf(
            Contract.CategoryRule.PATTERN,
            Contract.CategoryRule.CATEGORY_ID,
            Contract.CategoryRule.INDEX
        )
        val cursor = database.getCategoryRule(ruleId, projection)
        assertNotNull(cursor)
        val nonNullCursor = cursor ?: return
        nonNullCursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("tesco", it.getString(it.getColumnIndexOrThrow(Contract.CategoryRule.PATTERN)))
            assertEquals(FOOD, it.getLong(it.getColumnIndexOrThrow(Contract.CategoryRule.CATEGORY_ID)))
            assertEquals(2, it.getInt(it.getColumnIndexOrThrow(Contract.CategoryRule.INDEX)))
        }

        val otherCursor = database.getCategoryRule(otherId, projection)
        assertNotNull(otherCursor)
        val nonNullOtherCursor = otherCursor ?: return
        nonNullOtherCursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("aldi", it.getString(it.getColumnIndexOrThrow(Contract.CategoryRule.PATTERN)))
            assertEquals(PETROL, it.getLong(it.getColumnIndexOrThrow(Contract.CategoryRule.CATEGORY_ID)))
            assertEquals(1, it.getInt(it.getColumnIndexOrThrow(Contract.CategoryRule.INDEX)))
        }
    }

    @Test
    fun anUpdateCarryingThePatternAndTheCategoryWritesBothAndLeavesTheIndex() {
        val insert = ContentValues().apply {
            put(Contract.CategoryRule.PATTERN, "tesco")
            put(Contract.CategoryRule.CATEGORY_ID, FOOD)
            put(Contract.CategoryRule.INDEX, 3)
        }
        val ruleId = database.insertCategoryRule(insert)
        assertTrue(ruleId > 0)

        val other = ContentValues().apply {
            put(Contract.CategoryRule.PATTERN, "aldi")
            put(Contract.CategoryRule.CATEGORY_ID, FOOD)
            put(Contract.CategoryRule.INDEX, 5)
        }
        val otherId = database.insertCategoryRule(other)
        assertTrue(otherId > 0)

        val update = ContentValues().apply {
            put(Contract.CategoryRule.PATTERN, "shell")
            put(Contract.CategoryRule.CATEGORY_ID, PETROL)
        }
        assertEquals(1, database.updateCategoryRule(ruleId, update))

        val projection = arrayOf(
            Contract.CategoryRule.PATTERN,
            Contract.CategoryRule.CATEGORY_ID,
            Contract.CategoryRule.INDEX
        )
        val cursor = database.getCategoryRule(ruleId, projection)
        assertNotNull(cursor)
        val nonNullCursor = cursor ?: return
        nonNullCursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("shell", it.getString(it.getColumnIndexOrThrow(Contract.CategoryRule.PATTERN)))
            assertEquals(PETROL, it.getLong(it.getColumnIndexOrThrow(Contract.CategoryRule.CATEGORY_ID)))
            assertEquals(3, it.getInt(it.getColumnIndexOrThrow(Contract.CategoryRule.INDEX)))
        }

        val otherCursor = database.getCategoryRule(otherId, projection)
        assertNotNull(otherCursor)
        val nonNullOtherCursor = otherCursor ?: return
        nonNullOtherCursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("aldi", it.getString(it.getColumnIndexOrThrow(Contract.CategoryRule.PATTERN)))
            assertEquals(FOOD, it.getLong(it.getColumnIndexOrThrow(Contract.CategoryRule.CATEGORY_ID)))
            assertEquals(5, it.getInt(it.getColumnIndexOrThrow(Contract.CategoryRule.INDEX)))
        }
    }

    @Test
    fun anUpdateCarryingOnlyThePatternLeavesTheCategoryAndKeepsTheCase() {
        val insert = ContentValues().apply {
            put(Contract.CategoryRule.PATTERN, "tesco")
            put(Contract.CategoryRule.CATEGORY_ID, FOOD)
            put(Contract.CategoryRule.INDEX, 3)
        }
        val ruleId = database.insertCategoryRule(insert)
        assertTrue(ruleId > 0)

        val update = ContentValues().apply {
            put(Contract.CategoryRule.PATTERN, "Shell Express")
        }
        assertEquals(1, database.updateCategoryRule(ruleId, update))
        assertEquals(0, database.updateCategoryRule(ruleId + 1000, update))

        val projection = arrayOf(
            Contract.CategoryRule.PATTERN,
            Contract.CategoryRule.CATEGORY_ID,
            Contract.CategoryRule.INDEX
        )
        val cursor = database.getCategoryRule(ruleId, projection)
        assertNotNull(cursor)
        val nonNullCursor = cursor ?: return
        nonNullCursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("Shell Express", it.getString(it.getColumnIndexOrThrow(Contract.CategoryRule.PATTERN)))
            assertEquals(FOOD, it.getLong(it.getColumnIndexOrThrow(Contract.CategoryRule.CATEGORY_ID)))
            assertEquals(3, it.getInt(it.getColumnIndexOrThrow(Contract.CategoryRule.INDEX)))
        }
    }

    companion object {
        private const val NAME = "rule-updates.db"
        private const val ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}"
        private const val EDIT = 1565000000000L
        private const val FOOD = 900L
        private const val PETROL = 901L
    }
}
