/*
 * Copyright (c) 2026.
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

import android.content.Context
import android.database.sqlite.SQLiteDatabase

/**
 * One ledger holding split transactions of every kind the category lines treat apart, valid and
 * invalid, beside ordinary rows in the same wallets and month. Every expected figure a test
 * reads from here is written out by hand from the rows below, never read from the app.
 */
object SplitLedger {

    const val ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}"
    const val DATE = "2026-01-15 12:00:00"
    private const val EDIT = 1565000000000L

    const val CASH = 901L
    const val BANK = 902L
    const val YEN = 903L

    const val GROCERIES = 910L
    const val HOUSEHOLD = 911L
    const val FUEL = 912L
    const val PHARMACY = 913L
    /** A child of Household kept out of the reports, while Household itself is shown. */
    const val HIDDEN = 914L
    const val SALARY = 915L
    const val BONUS = 916L
    /** Named only by a part flagged deleted. */
    const val GIFTS = 917L
    /** Named by nothing. */
    const val SPARE = 918L

    /** id, type, parent or 0, show_report. */
    private val CATEGORIES = arrayOf(
        longArrayOf(GROCERIES, Schema.CategoryType.EXPENSE.toLong(), 0L, 1L),
        longArrayOf(HOUSEHOLD, Schema.CategoryType.EXPENSE.toLong(), 0L, 1L),
        longArrayOf(FUEL, Schema.CategoryType.EXPENSE.toLong(), 0L, 1L),
        longArrayOf(PHARMACY, Schema.CategoryType.EXPENSE.toLong(), 0L, 1L),
        longArrayOf(HIDDEN, Schema.CategoryType.EXPENSE.toLong(), HOUSEHOLD, 0L),
        longArrayOf(SALARY, Schema.CategoryType.INCOME.toLong(), 0L, 1L),
        longArrayOf(BONUS, Schema.CategoryType.INCOME.toLong(), 0L, 1L),
        longArrayOf(GIFTS, Schema.CategoryType.EXPENSE.toLong(), 0L, 1L),
        longArrayOf(SPARE, Schema.CategoryType.EXPENSE.toLong(), 0L, 1L)
    )

    val NAMES: Map<Long, String> = mapOf(
        GROCERIES to "Groceries",
        HOUSEHOLD to "Household",
        FUEL to "Fuel",
        PHARMACY to "Pharmacy",
        HIDDEN to "Hidden",
        SALARY to "Salary",
        BONUS to "Bonus",
        GIFTS to "Gifts",
        SPARE to "Spare"
    )

    /** A valid split with a remainder, 10000 under Groceries with parts of 2000 and 3000. */
    const val RECEIPT = 920L
    /** Parts adding up to exactly the total, so no line under the main category. */
    const val EXACT = 921L
    /** Parts adding up to more than the total. */
    const val OVER = 922L
    /** A part below zero. */
    const val NEGATIVE = 923L
    /** A part in an income category on an expense. */
    const val CROSSED = 924L
    /** Parts on a debt row. */
    const val DEBT_ROW = 925L
    /** A part in a hidden child of Household. */
    const val WITH_HIDDEN = 926L
    /** Two parts in the same category. */
    const val TWICE = 927L
    const val PLAIN_CASH = 928L
    const val PLAIN_BANK = 929L
    const val PAY = 950L
    /** A valid split income. */
    const val PAY_SPLIT = 951L

    /** id, wallet, main category, direction, type, total. */
    val TRANSACTIONS = arrayOf(
        longArrayOf(RECEIPT, CASH, GROCERIES, Schema.Direction.EXPENSE.toLong(), Contract.TransactionType.STANDARD.toLong(), 10000L),
        longArrayOf(EXACT, CASH, GROCERIES, Schema.Direction.EXPENSE.toLong(), Contract.TransactionType.STANDARD.toLong(), 4000L),
        longArrayOf(OVER, CASH, PHARMACY, Schema.Direction.EXPENSE.toLong(), Contract.TransactionType.STANDARD.toLong(), 1000L),
        longArrayOf(NEGATIVE, CASH, FUEL, Schema.Direction.EXPENSE.toLong(), Contract.TransactionType.STANDARD.toLong(), 3000L),
        longArrayOf(CROSSED, CASH, HOUSEHOLD, Schema.Direction.EXPENSE.toLong(), Contract.TransactionType.STANDARD.toLong(), 2000L),
        longArrayOf(DEBT_ROW, CASH, FUEL, Schema.Direction.EXPENSE.toLong(), Contract.TransactionType.DEBT.toLong(), 6000L),
        longArrayOf(WITH_HIDDEN, CASH, HOUSEHOLD, Schema.Direction.EXPENSE.toLong(), Contract.TransactionType.STANDARD.toLong(), 5000L),
        longArrayOf(TWICE, CASH, GROCERIES, Schema.Direction.EXPENSE.toLong(), Contract.TransactionType.STANDARD.toLong(), 7000L),
        longArrayOf(PLAIN_CASH, CASH, GROCERIES, Schema.Direction.EXPENSE.toLong(), Contract.TransactionType.STANDARD.toLong(), 1100L),
        longArrayOf(PLAIN_BANK, BANK, HOUSEHOLD, Schema.Direction.EXPENSE.toLong(), Contract.TransactionType.STANDARD.toLong(), 2200L),
        longArrayOf(PAY, CASH, SALARY, Schema.Direction.INCOME.toLong(), Contract.TransactionType.STANDARD.toLong(), 30000L),
        longArrayOf(PAY_SPLIT, CASH, SALARY, Schema.Direction.INCOME.toLong(), Contract.TransactionType.STANDARD.toLong(), 8000L)
    )

    /** id, transaction, category, money, deleted. */
    val PARTS = arrayOf(
        longArrayOf(930L, RECEIPT, HOUSEHOLD, 2000L, 0L),
        longArrayOf(931L, RECEIPT, FUEL, 3000L, 0L),
        longArrayOf(944L, RECEIPT, GIFTS, 100L, 1L),
        longArrayOf(932L, EXACT, PHARMACY, 1500L, 0L),
        longArrayOf(933L, EXACT, FUEL, 2500L, 0L),
        longArrayOf(934L, OVER, HOUSEHOLD, 800L, 0L),
        longArrayOf(935L, OVER, FUEL, 700L, 0L),
        longArrayOf(936L, NEGATIVE, HOUSEHOLD, -500L, 0L),
        longArrayOf(937L, NEGATIVE, GROCERIES, 1000L, 0L),
        longArrayOf(938L, CROSSED, SALARY, 500L, 0L),
        longArrayOf(939L, DEBT_ROW, GROCERIES, 1000L, 0L),
        longArrayOf(940L, WITH_HIDDEN, HIDDEN, 1200L, 0L),
        longArrayOf(941L, TWICE, HOUSEHOLD, 1000L, 0L),
        longArrayOf(942L, TWICE, HOUSEHOLD, 1500L, 0L),
        longArrayOf(943L, PAY_SPLIT, BONUS, 3000L, 0L)
    )

    /**
     * The lines each transaction should read as, category and money, worked out by hand. An
     * invalid split reads as one line under its main category for its whole total.
     */
    val LINES: Map<Long, List<LongArray>> = mapOf(
        RECEIPT to listOf(
            longArrayOf(GROCERIES, 10000L - 2000L - 3000L),
            longArrayOf(HOUSEHOLD, 2000L),
            longArrayOf(FUEL, 3000L)
        ),
        EXACT to listOf(
            longArrayOf(PHARMACY, 1500L),
            longArrayOf(FUEL, 2500L)
        ),
        OVER to listOf(
            longArrayOf(PHARMACY, 1000L)
        ),
        NEGATIVE to listOf(
            longArrayOf(FUEL, 3000L)
        ),
        CROSSED to listOf(
            longArrayOf(HOUSEHOLD, 2000L)
        ),
        DEBT_ROW to listOf(
            longArrayOf(FUEL, 6000L)
        ),
        WITH_HIDDEN to listOf(
            longArrayOf(HOUSEHOLD, 5000L - 1200L),
            longArrayOf(HIDDEN, 1200L)
        ),
        TWICE to listOf(
            longArrayOf(GROCERIES, 7000L - 1000L - 1500L),
            longArrayOf(HOUSEHOLD, 1000L),
            longArrayOf(HOUSEHOLD, 1500L)
        ),
        PLAIN_CASH to listOf(
            longArrayOf(GROCERIES, 1100L)
        ),
        PLAIN_BANK to listOf(
            longArrayOf(HOUSEHOLD, 2200L)
        ),
        PAY to listOf(
            longArrayOf(SALARY, 30000L)
        ),
        PAY_SPLIT to listOf(
            longArrayOf(SALARY, 8000L - 3000L),
            longArrayOf(BONUS, 3000L)
        )
    )

    @JvmStatic
    fun build(context: Context) {
        val db = SQLDatabase.getShared(context).writableDatabase
        insertWallet(db, CASH, "Cash", "EUR")
        insertWallet(db, BANK, "Bank", "EUR")
        insertWallet(db, YEN, "Yen", "JPY")
        for (category in CATEGORIES) {
            db.execSQL(
                "INSERT INTO categories (category_id, category_name, category_icon, " +
                    "category_type, category_parent, category_show_report, uuid, last_edit, " +
                    "deleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)",
                arrayOf<Any?>(
                    category[0], NAMES[category[0]], ICON, category[1],
                    if (category[2] != 0L) category[2] else null, category[3],
                    "category-" + category[0], EDIT
                )
            )
        }
        for (transaction in TRANSACTIONS) {
            db.execSQL(
                "INSERT INTO transactions (transaction_id, transaction_money, " +
                    "transaction_date, transaction_description, transaction_category, " +
                    "transaction_direction, transaction_type, transaction_wallet, " +
                    "transaction_confirmed, transaction_count_in_total, uuid, last_edit, " +
                    "deleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, 1, ?, ?, 0)",
                arrayOf<Any?>(
                    transaction[0], transaction[5], DATE, description(transaction[0]),
                    transaction[2], transaction[3], transaction[4], transaction[1],
                    "transaction-" + transaction[0], EDIT
                )
            )
        }
        for (part in PARTS) {
            db.execSQL(
                "INSERT INTO transaction_splits (split_id, _transaction, split_category, " +
                    "split_money, uuid, last_edit, deleted) VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(part[0], part[1], part[2], part[3], "split-" + part[0], EDIT, part[4])
            )
        }
    }

    @JvmStatic
    fun database(context: Context): SQLiteDatabase {
        return SQLDatabase.getShared(context).writableDatabase
    }

    @JvmStatic
    fun description(transaction: Long): String {
        return "receipt-$transaction"
    }

    @JvmStatic
    fun transaction(id: Long): LongArray {
        for (transaction in TRANSACTIONS) {
            if (transaction[0] == id) {
                return transaction
            }
        }
        throw IllegalArgumentException("no transaction $id")
    }

    /**
     * Inserts a category budget over the given categories and both euro wallets, for January.
     */
    @JvmStatic
    fun insertCategoryBudget(context: Context, id: Long, vararg categories: Long): Long {
        val db = SQLDatabase.getShared(context).writableDatabase
        db.execSQL(
            "INSERT INTO budgets (budget_id, budget_type, budget_category, budget_start_date, " +
                "budget_end_date, budget_money, budget_currency, uuid, last_edit, deleted) " +
                "VALUES (?, ?, ?, '2026-01-01', '2026-01-31', 100000, 'EUR', ?, ?, 0)",
            arrayOf<Any?>(id, Schema.BudgetType.CATEGORY, categories[0], "budget-$id", EDIT)
        )
        for (wallet in longArrayOf(CASH, BANK)) {
            db.execSQL(
                "INSERT INTO budget_wallets (_budget, _wallet, uuid, last_edit, deleted) " +
                    "VALUES (?, ?, ?, ?, 0)",
                arrayOf<Any?>(id, wallet, "bw-$id-$wallet", EDIT)
            )
        }
        for (category in categories) {
            db.execSQL(
                "INSERT INTO budget_categories (_budget, _category, uuid, last_edit, deleted) " +
                    "VALUES (?, ?, ?, ?, 0)",
                arrayOf<Any?>(id, category, "bc-$id-$category", EDIT)
            )
        }
        return id
    }

    private fun insertWallet(db: SQLiteDatabase, id: Long, name: String, currency: String) {
        db.execSQL(
            "INSERT INTO wallets (wallet_id, wallet_name, wallet_icon, wallet_currency, " +
                "wallet_count_in_total, uuid, last_edit, deleted) VALUES (?, ?, ?, ?, ?, ?, ?, 0)",
            arrayOf<Any?>(id, name, ICON, currency, if ("EUR" == currency) 1 else 0, "wallet-$id", EDIT)
        )
    }
}
