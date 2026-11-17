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
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CategoryRuleMatchTest {

    private lateinit var database: SQLDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = SQLDatabase(context, NAME)
        val db = database.writableDatabase
        insertCategory(db, FOOD, "Food")
        insertCategory(db, PETROL, "Petrol")
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun aDescriptionHoldingThePatternAnswersWithThatRulesCategory() {
        insertRule("tesco", FOOD, 0)
        assertEquals(FOOD, match("weekly shop at tesco extra"))
    }

    @Test
    fun aLowerCasePatternMatchesACapitalisedDescription() {
        insertRule("tesco", FOOD, 0)
        assertEquals(FOOD, match("TESCO Extra"))
    }

    @Test
    fun aCapitalisedPatternMatchesALowerCaseDescription() {
        insertRule("TeSCo", FOOD, 0)
        assertEquals(FOOD, match("weekly shop at tesco extra"))
    }

    @Test
    fun aPercentInThePatternIsNotAWildcard() {
        insertRule("100% juice", FOOD, 0)
        assertNull(match("100 juice"))
        assertEquals(FOOD, match("Bought 100% juice today"))
    }

    @Test
    fun anUnderscoreInThePatternIsNotAWildcard() {
        insertRule("ju_ce", FOOD, 0)
        assertNull(match("orange juice"))
        assertEquals(FOOD, match("orange ju_ce"))
    }

    @Test
    fun theLowestIndexWinsWhenTwoRulesMatch() {
        insertRule("petrol", PETROL, 7)
        insertRule("tesco", FOOD, 3)
        assertEquals(FOOD, match("tesco petrol station"))
    }

    @Test
    fun theOlderRuleWinsWhenTwoRulesShareAnIndex() {
        insertRule("petrol", PETROL, 4)
        insertRule("tesco", FOOD, 4)
        assertEquals(PETROL, match("tesco petrol station"))
    }

    @Test
    fun aDeletedRuleNeverMatches() {
        insertDeletedRule("tesco", FOOD, 0)
        assertNull(match("weekly shop at tesco extra"))
    }

    @Test
    fun aDescriptionNoRuleCoversComesBackEmpty() {
        insertRule("tesco", FOOD, 0)
        val cursor = database.getCategoryRuleMatch("corner shop")
        assertNotNull(cursor)
        val nonNullCursor = cursor ?: return
        nonNullCursor.use {
            assertEquals(0, it.count)
        }
    }

    @Test
    fun noRulesAtAllComesBackEmpty() {
        assertNull(match("weekly shop at tesco extra"))
    }

    private fun match(description: String): Long? {
        val cursor = database.getCategoryRuleMatch(description)
        assertNotNull(cursor)
        val nonNullCursor = cursor ?: return null
        return nonNullCursor.use {
            if (!it.moveToFirst()) {
                null
            } else {
                it.getLong(it.getColumnIndexOrThrow(Contract.CategoryRule.CATEGORY_ID))
            }
        }
    }

    private fun insertRule(pattern: String, category: Long, index: Int) {
        val contentValues = ContentValues().apply {
            put(Contract.CategoryRule.PATTERN, pattern)
            put(Contract.CategoryRule.CATEGORY_ID, category)
            put(Contract.CategoryRule.INDEX, index)
        }
        assertTrue(database.insertCategoryRule(contentValues) > 0)
    }

    private fun insertDeletedRule(pattern: String, category: Long, index: Int) {
        database.writableDatabase.execSQL(
            "INSERT INTO category_rules (rule_pattern, rule_category, rule_index, uuid, last_edit, deleted) VALUES (?, ?, ?, ?, ?, 1)",
            arrayOf<Any>(pattern, category, index, "rule-$pattern", EDIT)
        )
    }

    private fun insertCategory(db: SQLiteDatabase, id: Long, name: String) {
        db.execSQL(
            "INSERT INTO categories (category_id, category_name, category_icon, category_type, uuid, last_edit, deleted) VALUES (?, ?, ?, ?, ?, ?, 0)",
            arrayOf<Any>(id, name, ICON, Schema.CategoryType.EXPENSE, "category-$id", EDIT)
        )
    }

    companion object {
        private const val NAME = "rules.db"
        private const val ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}"
        private const val EDIT = 1565000000000L
        private const val FOOD = 900L
        private const val PETROL = 901L
    }
}
