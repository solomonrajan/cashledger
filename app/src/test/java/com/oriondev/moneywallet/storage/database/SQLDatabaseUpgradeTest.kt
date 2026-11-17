package com.oriondev.moneywallet.storage.database

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.oriondev.moneywallet.storage.database.Contract
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.ArrayList
import java.util.Collections

/**
 * What [SQLDatabase.onUpgrade] does to a database an older release left behind.
 */
@RunWith(RobolectricTestRunner::class)
class SQLDatabaseUpgradeTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    /**
     * A database from before any of the three steps, carried forward by the release installed over
     * it.
     */
    @Test
    fun aDatabaseFromBeforeAllThreeStepsComesAllTheWayForwardInOneLaunch() {
        upgradeFromBeforeTheJoinTable(2)
    }

    /**
     * What a user of any release from upstream 4.0.5 to this app's 1.5.0 is holding.
     */
    @Test
    fun aDatabaseWhereOnlyTheFirstStepHasEverRun() {
        upgradeFromBeforeTheJoinTable(3)
    }

    /**
     * What a 1.6.0 user is holding, and the upgrade this migration runs most often.
     */
    @Test
    fun aDatabaseWhereEverythingButTheJoinTableHasRun() {
        upgradeFromBeforeTheJoinTable(4)
    }

    private fun upgradeFromBeforeTheJoinTable(version: Int) {
        val old = context.openOrCreateDatabase(NAME, Context.MODE_PRIVATE, null)
        old.execSQL(CREATE_TABLE_WALLET_BEFORE_3)
        old.execSQL(CREATE_TABLE_CATEGORY_BEFORE_3)
        old.execSQL(CREATE_TABLE_BUDGET_BEFORE_4)
        old.execSQL(Schema.CREATE_TABLE_CURRENCY)
        if (version >= 3) {
            old.execSQL(Schema.CREATE_WALLET_INDEX_COLUMN)
            old.execSQL(Schema.CREATE_CATEGORY_INDEX_COLUMN)
        }
        if (version >= 4) {
            old.execSQL(Schema.CREATE_BUDGET_RULE_COLUMN)
            old.execSQL(Schema.CREATE_BUDGET_RULE_START_COLUMN)
        }
        insertWallet(old, 1, "Cash")
        insertCategory(old, 1, "Food")
        insertCategory(old, 2, "Rent")
        insertCategory(old, 3, "Fun")
        insertBudget(old, 1, Schema.BudgetType.CATEGORY, 1L)
        insertBudget(old, 5, Schema.BudgetType.CATEGORY, 1L)
        insertBudget(old, 4, Schema.BudgetType.EXPENSES, null)
        insertBudget(old, 2, Schema.BudgetType.EXPENSES, 2L)
        insertBudget(old, 3, Schema.BudgetType.INCOMES, 3L)
        old.version = version
        old.close()

        val helper = SQLDatabase(context, NAME)
        val upgraded = helper.writableDatabase

        assertEquals(7, upgraded.version)
        assertEquals("ok", text(upgraded, "PRAGMA integrity_check"))
        assertEquals(0L, count(upgraded, "pragma_foreign_key_check", null))
        assertSchemaMatchesAFreshInstall(upgraded)

        assertEquals(0L, count(upgraded, Schema.CategoryRule.TABLE, null))

        assertEquals(1L, count(upgraded, Schema.Wallet.TABLE, null))
        assertEquals(
            "the upgrade neither seeds nor drops categories",
            3L,
            count(upgraded, Schema.Category.TABLE, null)
        )
        assertEquals(5L, count(upgraded, Schema.Budget.TABLE, null))
        assertEquals(
            0L,
            number(upgraded, "SELECT wallet_index FROM wallets WHERE wallet_id = 1")
        )
        assertEquals(
            0L,
            number(upgraded, "SELECT category_index FROM categories WHERE category_id = 1")
        )
        assertNull(number(upgraded, "SELECT budget_rule FROM budgets WHERE budget_id = 1"))
        assertNull(number(upgraded, "SELECT budget_rule_start FROM budgets WHERE budget_id = 1"))

        assertEquals(4L, count(upgraded, Schema.BudgetCategory.TABLE, null))
        assertEquals(2L, count(upgraded, Schema.BudgetCategory.TABLE, "_category = 1 AND deleted = 0"))
        assertEquals(1L, categoryColumnOf(upgraded, 1))
        assertStampedNow(upgraded, "SELECT last_edit FROM budget_categories WHERE _budget = 1")

        assertEquals(1L, count(upgraded, Schema.BudgetCategory.TABLE, "_budget = 2 AND deleted = 1"))
        assertEquals(1L, count(upgraded, Schema.BudgetCategory.TABLE, "_budget = 3 AND deleted = 1"))
        assertNull(categoryColumnOf(upgraded, 2))
        assertNull(categoryColumnOf(upgraded, 3))
        assertEquals(
            EDIT,
            number(upgraded, "SELECT last_edit FROM budgets WHERE budget_id = 2")
        )
        assertEquals(
            EDIT,
            number(upgraded, "SELECT last_edit FROM budgets WHERE budget_id = 1")
        )

        upgraded.execSQL("DELETE FROM categories WHERE category_id = 2")
        assertEquals(0L, count(upgraded, Schema.BudgetCategory.TABLE, "_category = 2"))
        upgraded.execSQL("DELETE FROM budgets WHERE budget_id = 1")
        assertEquals(0L, count(upgraded, Schema.BudgetCategory.TABLE, "_budget = 1"))
        helper.close()
    }

    @Test
    fun aFailedUpgradeLeavesTheVersionWhereItWas() {
        val old = context.openOrCreateDatabase(NAME, Context.MODE_PRIVATE, null)
        old.execSQL(CREATE_TABLE_WALLET_BEFORE_3)
        old.execSQL(CREATE_TABLE_CATEGORY_BEFORE_3)
        old.execSQL(CREATE_TABLE_BUDGET_BEFORE_4)
        insertBudget(old, 1, Schema.BudgetType.CATEGORY, 99L)
        old.version = 2
        old.close()

        val helper = SQLDatabase(context, NAME)
        try {
            helper.writableDatabase
            fail(
                "a database the migration cannot convert came back converted, so either the fill " +
                    "no longer refuses an orphan row or the failure was caught and carried on from"
            )
        } catch (expected: SQLiteConstraintException) {
            helper.close()
        }

        val after = context.openOrCreateDatabase(NAME, Context.MODE_PRIVATE, null)
        assertEquals(2, after.version)
        assertEquals(
            0L,
            count(
                after,
                "pragma_table_list",
                "name = '${Schema.BudgetCategory.TABLE}'"
            )
        )
        after.close()
    }

    @Test
    fun aDatabasePutBackToTheVersionBeforeTheJoinTable() {
        upgradeAgainOverRowsThatAreAlreadyThere(3)
    }

    @Test
    fun rulesSurviveTheUpgradeRunningASecondTime() {
        val writer = SQLDatabase(context, NAME)
        val old = writer.writableDatabase
        insertCategory(old, 900, "Food")
        insertCategoryRule(old, 1, "tesco", 900, 0)
        insertCategoryRule(old, 2, "shell", 900, 1)
        old.version = 6
        writer.close()

        val helper = SQLDatabase(context, NAME)
        val upgraded = helper.writableDatabase

        assertEquals(7, upgraded.version)
        assertEquals("ok", text(upgraded, "PRAGMA integrity_check"))
        assertEquals(0L, count(upgraded, "pragma_foreign_key_check", null))
        assertEquals(2L, count(upgraded, Schema.CategoryRule.TABLE, null))
        assertEquals(
            1L,
            count(
                upgraded,
                Schema.CategoryRule.TABLE,
                "rule_pattern = 'tesco' AND rule_category = 900 AND rule_index = 0"
            )
        )
        helper.close()
    }

    private fun insertCategoryRule(
        db: SQLiteDatabase,
        id: Long,
        pattern: String,
        category: Long,
        index: Int
    ) {
        db.execSQL(
            "INSERT INTO category_rules (rule_id, rule_pattern, rule_category, " +
                "rule_index, uuid, last_edit, deleted) VALUES (?, ?, ?, ?, ?, ?, 0)",
            arrayOf<Any>(id, pattern, category, index, "rule-$id", EDIT)
        )
    }

    private fun upgradeAgainOverRowsThatAreAlreadyThere(version: Int) {
        val old = context.openOrCreateDatabase(NAME, Context.MODE_PRIVATE, null)
        old.execSQL(Schema.CREATE_TABLE_CATEGORY)
        old.execSQL(Schema.CREATE_TABLE_BUDGET)
        old.execSQL(Schema.CREATE_TABLE_BUDGET_CATEGORY)
        old.execSQL(Schema.CREATE_TABLE_CURRENCY)
        insertCategory(old, 1, "Food")
        insertCategory(old, 5, "Rent")
        insertCategory(old, 7, "Fun")
        insertCategory(old, 9, "Travel")

        insertBudget(old, 101, Schema.BudgetType.CATEGORY, 9L)
        insertBudgetCategory(old, 101, 5, false)
        insertBudgetCategory(old, 101, 7, false)
        insertBudgetCategory(old, 101, 1, true)
        insertBudget(old, 102, Schema.BudgetType.CATEGORY, 5L)
        insertBudgetCategory(old, 102, 5, true)
        insertBudget(old, 103, Schema.BudgetType.EXPENSES, null)
        insertBudgetCategory(old, 103, 7, false)
        insertBudgetCategory(old, 103, 1, true)
        insertBudget(old, 104, Schema.BudgetType.CATEGORY, 1L)
        insertBudgetCategory(old, 104, 1, false)
        old.version = version
        old.close()

        val helper = SQLDatabase(context, NAME)
        val upgraded = helper.writableDatabase

        assertEquals(7, upgraded.version)
        assertEquals("ok", text(upgraded, "PRAGMA integrity_check"))
        assertEquals(0L, count(upgraded, "pragma_foreign_key_check", null))
        assertEquals(7L, count(upgraded, Schema.BudgetCategory.TABLE, null))

        assertEquals(2L, count(upgraded, Schema.BudgetCategory.TABLE, "_budget = 101 AND deleted = 0"))
        assertEquals(
            1L,
            count(
                upgraded,
                Schema.BudgetCategory.TABLE,
                "_budget = 101 AND _category = 1 AND deleted = 1"
            )
        )
        assertEquals(0L, count(upgraded, Schema.BudgetCategory.TABLE, "_category = 9"))
        assertEquals(5L, categoryColumnOf(upgraded, 101))
        assertEquals(1L, categoryColumnOf(upgraded, 104))

        assertEquals(
            1L,
            count(
                upgraded,
                Schema.BudgetCategory.TABLE,
                "_budget = 102 AND _category = 5 AND deleted = 0"
            )
        )
        assertEquals(5L, categoryColumnOf(upgraded, 102))

        assertEquals(2L, count(upgraded, Schema.BudgetCategory.TABLE, "_budget = 103 AND deleted = 1"))
        assertStampedNow(
            upgraded,
            "SELECT last_edit FROM budget_categories WHERE _budget = 103 AND _category = 7"
        )
        assertEquals(
            EDIT,
            number(
                upgraded,
                "SELECT last_edit FROM budget_categories WHERE _budget = 103 AND _category = 1"
            )
        )
        helper.close()
    }

    private fun assertSchemaMatchesAFreshInstall(upgraded: SQLiteDatabase) {
        val helper = SQLDatabase(context, "fresh.db")
        val fresh = helper.writableDatabase
        for (table in arrayOf(Schema.Wallet.TABLE, Schema.Category.TABLE, Schema.Budget.TABLE)) {
            val columns = declaration(upgraded, "table_info", table)
            assertFalse("$table is not in the upgraded database", columns.isEmpty())
            assertEquals("$table columns", declaration(fresh, "table_info", table), columns)
            assertEquals(
                "$table foreign keys",
                declaration(fresh, "foreign_key_list", table),
                declaration(upgraded, "foreign_key_list", table)
            )
        }
        helper.close()
    }

    @Test
    @Throws(Exception::class)
    fun theAssetFileAgreesWithTheCurrenciesTheStepMoves() {
        val json = StringBuilder()
        context.assets.open("resources/currencies.json").use { stream ->
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    json.append(line)
                    line = reader.readLine()
                }
            }
        }
        val shipped = JSONArray(json.toString())
        for (iso in SQLDatabase.CURRENCIES_MOVED_TO_TWO_DECIMALS) {
            var found = false
            for (i in 0 until shipped.length()) {
                val currency = shipped.getJSONObject(i)
                if (iso == currency.getString("code")) {
                    found = true
                    assertEquals(
                        "$iso is seeded at the wrong scale on a fresh install",
                        2,
                        currency.optInt("decimals", 2)
                    )
                }
            }
            assertTrue("$iso is not in the asset file at all", found)
        }
    }

    @Test
    fun aCurrencyMovedToTwoDecimalsCarriesItsMoneyWithIt() {
        writeADatabaseAtVersionFive(0)

        val helper = SQLDatabase(context, NAME)
        val upgraded = helper.writableDatabase

        assertEquals(7, upgraded.version)
        assertEquals(2L, decimalsOf(upgraded, "AMD"))
        assertEquals(15000000L, startMoneyOf(upgraded, 901))
        assertEquals(150000L, moneyOf(upgraded, 910))
        assertEquals(3000000L, budgetMoneyOf(upgraded, 920))
        assertEquals(70000L, startMoneyOf(upgraded, 903))
        assertEquals(20000L, moneyOf(upgraded, 911))

        assertEquals(2L, decimalsOf(upgraded, "EUR"))
        assertEquals(5000L, startMoneyOf(upgraded, 902))
        assertEquals(250L, moneyOf(upgraded, 912))
        assertEquals(41000L, budgetMoneyOf(upgraded, 921))

        assertEquals(
            410000L,
            number(upgraded, "SELECT debt_money FROM debts WHERE debt_id = 930")
        )
        assertEquals(
            520000L,
            number(upgraded, "SELECT saving_start_money FROM savings WHERE saving_id = 940")
        )
        assertEquals(
            630000L,
            number(upgraded, "SELECT saving_end_money FROM savings WHERE saving_id = 940")
        )
        assertEquals(
            740000L,
            number(upgraded, "SELECT model_transaction_money FROM transaction_models WHERE model_id = 950")
        )
        assertEquals(
            190000L,
            number(upgraded, "SELECT recurrent_transaction_money FROM recurrent_transactions WHERE recurrent_transaction_id = 970")
        )

        assertEquals(
            850000L,
            number(upgraded, "SELECT model_transfer_from_money FROM transfer_models WHERE model_id = 960")
        )
        assertEquals(
            170000L,
            number(upgraded, "SELECT model_transfer_tax_money FROM transfer_models WHERE model_id = 960")
        )
        assertEquals(
            9600L,
            number(upgraded, "SELECT model_transfer_to_money FROM transfer_models WHERE model_id = 960")
        )

        assertEquals(
            2100L,
            number(upgraded, "SELECT model_transfer_from_money FROM transfer_models WHERE model_id = 961")
        )
        assertEquals(
            400L,
            number(upgraded, "SELECT model_transfer_tax_money FROM transfer_models WHERE model_id = 961")
        )
        assertEquals(
            320000L,
            number(upgraded, "SELECT model_transfer_to_money FROM transfer_models WHERE model_id = 961")
        )

        assertEquals(
            260000L,
            number(upgraded, "SELECT recurrent_transfer_from_money FROM recurrent_transfers WHERE recurrent_transfer_id = 980")
        )
        assertEquals(
            30000L,
            number(upgraded, "SELECT recurrent_transfer_tax_money FROM recurrent_transfers WHERE recurrent_transfer_id = 980")
        )
        assertEquals(
            2700L,
            number(upgraded, "SELECT recurrent_transfer_to_money FROM recurrent_transfers WHERE recurrent_transfer_id = 980")
        )
        assertEquals(
            3300L,
            number(upgraded, "SELECT recurrent_transfer_from_money FROM recurrent_transfers WHERE recurrent_transfer_id = 981")
        )
        assertEquals(
            500L,
            number(upgraded, "SELECT recurrent_transfer_tax_money FROM recurrent_transfers WHERE recurrent_transfer_id = 981")
        )
        assertEquals(
            340000L,
            number(upgraded, "SELECT recurrent_transfer_to_money FROM recurrent_transfers WHERE recurrent_transfer_id = 981")
        )
        helper.close()
    }

    @Test
    fun theSecondTimeTheStepRunsItMovesNothing() {
        writeADatabaseAtVersionFive(0)

        val first = SQLDatabase(context, NAME)
        first.writableDatabase
        first.close()

        val downgraded = context.openOrCreateDatabase(NAME, Context.MODE_PRIVATE, null)
        downgraded.version = 5
        downgraded.close()

        val second = SQLDatabase(context, NAME)
        val upgraded = second.writableDatabase

        assertEquals(7, upgraded.version)
        assertEquals(2L, decimalsOf(upgraded, "AMD"))
        assertEquals(15000000L, startMoneyOf(upgraded, 901))
        assertEquals(150000L, moneyOf(upgraded, 910))
        assertEquals(3000000L, budgetMoneyOf(upgraded, 920))
        second.close()
    }

    @Test
    fun decimalsTheOwnerSetAboveZeroAreLeftAlone() {
        writeADatabaseAtVersionFive(3)

        val helper = SQLDatabase(context, NAME)
        val upgraded = helper.writableDatabase

        assertEquals(3L, decimalsOf(upgraded, "AMD"))
        assertEquals(150000L, startMoneyOf(upgraded, 901))
        assertEquals(1500L, moneyOf(upgraded, 910))
        assertEquals(30000L, budgetMoneyOf(upgraded, 920))
        helper.close()
    }

    private fun writeADatabaseAtVersionFive(amdDecimals: Int) {
        val writer = SQLDatabase(context, NAME)
        val old = writer.writableDatabase
        insertCurrency(old, "AMD", amdDecimals)
        insertCurrency(old, "EUR", 2)
        insertCategory(old, 900, "Food")
        insertWalletIn(old, 901, "Dram", "AMD", 150000L, false)
        insertWalletIn(old, 902, "Euro", "EUR", 5000L, false)
        insertWalletIn(old, 903, "Old dram", "AMD", 700L, true)
        insertTransaction(old, 910, 901, 1500L)
        insertTransaction(old, 911, 903, 200L)
        insertTransaction(old, 912, 902, 250L)
        insertBudgetIn(old, 920, "AMD", 30000L)
        insertBudgetIn(old, 921, "EUR", 41000L)
        insertDebt(old, 930, 901, 4100L)
        insertSaving(old, 940, 901, 5200L, 6300L)
        insertTransactionModel(old, 950, 901, 7400L)
        insertTransferModel(old, 960, 901, 902, 8500L, 9600L, 1700L)
        insertTransferModel(old, 961, 902, 901, 2100L, 3200L, 400L)
        insertRecurrentTransaction(old, 970, 901, 1900L)
        insertRecurrentTransfer(old, 980, 901, 902, 2600L, 2700L, 300L)
        insertRecurrentTransfer(old, 981, 902, 901, 3300L, 3400L, 500L)
        old.version = 5
        writer.close()
    }

    private fun insertCurrency(db: SQLiteDatabase, iso: String, decimals: Int) {
        db.execSQL(
            "INSERT INTO currencies (currency_iso, currency_name, currency_symbol, " +
                "currency_decimals, uuid, last_edit, deleted) VALUES (?, ?, ?, ?, ?, ?, 0)",
            arrayOf<Any>(iso, iso, iso, decimals, "currency-$iso", EDIT)
        )
    }

    private fun insertWalletIn(
        db: SQLiteDatabase,
        id: Long,
        name: String,
        iso: String,
        startMoney: Long,
        deleted: Boolean
    ) {
        db.execSQL(
            "INSERT INTO wallets (wallet_id, wallet_name, wallet_currency, " +
                "wallet_start_money, uuid, last_edit, deleted) VALUES (?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any>(id, name, iso, startMoney, "wallet-$id", EDIT, if (deleted) 1 else 0)
        )
    }

    private fun insertTransaction(db: SQLiteDatabase, id: Long, wallet: Long, money: Long) {
        db.execSQL(
            "INSERT INTO transactions (transaction_id, transaction_money, " +
                "transaction_date, transaction_category, transaction_direction, " +
                "transaction_type, transaction_wallet, uuid, last_edit, deleted) " +
                "VALUES (?, ?, '2026-09-01 12:00:00', 900, ?, ?, ?, ?, ?, 0)",
            arrayOf<Any>(
                id, money, Schema.Direction.EXPENSE,
                Contract.TransactionType.STANDARD, wallet, "transaction-$id", EDIT
            )
        )
    }

    private fun insertBudgetIn(db: SQLiteDatabase, id: Long, iso: String, money: Long) {
        db.execSQL(
            "INSERT INTO budgets (budget_id, budget_type, budget_start_date, " +
                "budget_end_date, budget_money, budget_currency, uuid, last_edit, " +
                "deleted) VALUES (?, ?, '2026-09-01', '2026-09-30', ?, ?, ?, ?, 0)",
            arrayOf<Any>(id, Schema.BudgetType.EXPENSES, money, iso, "budget-$id", EDIT)
        )
    }

    private fun insertDebt(db: SQLiteDatabase, id: Long, wallet: Long, money: Long) {
        db.execSQL(
            "INSERT INTO debts (debt_id, debt_type, debt_icon, debt_description, " +
                "debt_date, debt_wallet, debt_money, uuid, last_edit, deleted) " +
                "VALUES (?, 0, ?, 'Loan', '2026-09-01', ?, ?, ?, ?, 0)",
            arrayOf<Any>(id, ICON, wallet, money, "debt-$id", EDIT)
        )
    }

    private fun insertSaving(db: SQLiteDatabase, id: Long, wallet: Long, start: Long, end: Long) {
        db.execSQL(
            "INSERT INTO savings (saving_id, saving_description, saving_icon, " +
                "saving_start_money, saving_end_money, saving_wallet, uuid, last_edit, " +
                "deleted) VALUES (?, 'Goal', ?, ?, ?, ?, ?, ?, 0)",
            arrayOf<Any>(id, ICON, start, end, wallet, "saving-$id", EDIT)
        )
    }

    private fun insertTransactionModel(db: SQLiteDatabase, id: Long, wallet: Long, money: Long) {
        db.execSQL(
            "INSERT INTO transaction_models (model_id, " +
                "model_transaction_money, model_transaction_category, " +
                "model_transaction_direction, model_transaction_wallet, uuid, last_edit, " +
                "deleted) VALUES (?, ?, 900, ?, ?, ?, ?, 0)",
            arrayOf<Any>(id, money, Schema.Direction.EXPENSE, wallet, "model-$id", EDIT)
        )
    }

    private fun insertTransferModel(
        db: SQLiteDatabase,
        id: Long,
        from: Long,
        to: Long,
        moneyFrom: Long,
        moneyTo: Long,
        tax: Long
    ) {
        db.execSQL(
            "INSERT INTO transfer_models (model_id, model_transfer_from_wallet, " +
                "model_transfer_to_wallet, model_transfer_from_money, " +
                "model_transfer_to_money, model_transfer_tax_money, uuid, last_edit, " +
                "deleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)",
            arrayOf<Any>(id, from, to, moneyFrom, moneyTo, tax, "transfer-model-$id", EDIT)
        )
    }

    private fun insertRecurrentTransaction(db: SQLiteDatabase, id: Long, wallet: Long, money: Long) {
        db.execSQL(
            "INSERT INTO recurrent_transactions (recurrent_transaction_id, " +
                "recurrent_transaction_money, recurrent_transaction_category, " +
                "recurrent_transaction_direction, recurrent_transaction_wallet, " +
                "recurrent_transaction_start_date, recurrent_transaction_last_occurrence, " +
                "recurrent_transaction_rule, uuid, last_edit, deleted) " +
                "VALUES (?, ?, 900, ?, ?, '2026-09-01', '2026-09-01', 'FREQ=MONTHLY', ?, ?, 0)",
            arrayOf<Any>(id, money, Schema.Direction.EXPENSE, wallet, "recurrence-$id", EDIT)
        )
    }

    private fun insertRecurrentTransfer(
        db: SQLiteDatabase,
        id: Long,
        from: Long,
        to: Long,
        moneyFrom: Long,
        moneyTo: Long,
        tax: Long
    ) {
        db.execSQL(
            "INSERT INTO recurrent_transfers (recurrent_transfer_id, " +
                "recurrent_transfer_from_wallet, recurrent_transfer_to_wallet, " +
                "recurrent_transfer_from_money, recurrent_transfer_to_money, " +
                "recurrent_transfer_tax_money, recurrent_transfer_start_date, " +
                "recurrent_transfer_last_occurrence, recurrent_transfer_rule, uuid, " +
                "last_edit, deleted) " +
                "VALUES (?, ?, ?, ?, ?, ?, '2026-09-01', '2026-09-01', 'FREQ=MONTHLY', ?, ?, 0)",
            arrayOf<Any>(id, from, to, moneyFrom, moneyTo, tax, "recurrent-transfer-$id", EDIT)
        )
    }

    private fun decimalsOf(db: SQLiteDatabase, iso: String): Long? {
        return number(db, "SELECT currency_decimals FROM currencies WHERE currency_iso = '$iso'")
    }

    private fun startMoneyOf(db: SQLiteDatabase, wallet: Long): Long? {
        return number(db, "SELECT wallet_start_money FROM wallets WHERE wallet_id = $wallet")
    }

    private fun moneyOf(db: SQLiteDatabase, transaction: Long): Long? {
        return number(db, "SELECT transaction_money FROM transactions WHERE transaction_id = $transaction")
    }

    private fun budgetMoneyOf(db: SQLiteDatabase, budget: Long): Long? {
        return number(db, "SELECT budget_money FROM budgets WHERE budget_id = $budget")
    }

    private fun declaration(db: SQLiteDatabase, pragma: String, table: String): String {
        val cursor = db.rawQuery("PRAGMA $pragma($table)", null)
        val rows = ArrayList<String>()
        try {
            while (cursor.moveToNext()) {
                val row = StringBuilder()
                for (column in 0 until cursor.columnCount) {
                    val name = cursor.getColumnName(column)
                    if ("cid" == name || "id" == name || "seq" == name) {
                        continue
                    }
                    row.append(name).append('=').append(cursor.getString(column)).append(' ')
                }
                rows.add(row.toString())
            }
        } finally {
            cursor.close()
        }
        Collections.sort(rows)
        val joined = StringBuilder()
        for (row in rows) {
            joined.append(row).append('\n')
        }
        return joined.toString()
    }

    private fun insertWallet(db: SQLiteDatabase, id: Long, name: String) {
        db.execSQL(
            "INSERT INTO wallets (wallet_id, wallet_name, wallet_currency, uuid, " +
                "last_edit, deleted) VALUES (?, ?, 'EUR', ?, ?, 0)",
            arrayOf<Any>(id, name, "wallet-$id", EDIT)
        )
    }

    private fun insertCategory(db: SQLiteDatabase, id: Long, name: String) {
        db.execSQL(
            "INSERT INTO categories (category_id, category_name, category_icon, " +
                "category_type, uuid, last_edit, deleted) VALUES (?, ?, ?, ?, ?, ?, 0)",
            arrayOf<Any>(id, name, ICON, Schema.CategoryType.EXPENSE, "category-$id", EDIT)
        )
    }

    private fun insertBudget(db: SQLiteDatabase, id: Long, type: Int, category: Long?) {
        db.execSQL(
            "INSERT INTO budgets (budget_id, budget_type, budget_category, " +
                "budget_start_date, budget_end_date, budget_money, budget_currency, uuid, " +
                "last_edit, deleted) VALUES (?, ?, ?, '2026-09-01', '2026-09-30', 30000, " +
                "'EUR', ?, ?, 0)",
            arrayOf(id, type, category, "budget-$id", EDIT)
        )
    }

    private fun insertBudgetCategory(
        db: SQLiteDatabase,
        budget: Long,
        category: Long,
        deleted: Boolean
    ) {
        db.execSQL(
            "INSERT INTO budget_categories (_budget, _category, uuid, last_edit, deleted) " +
                "VALUES (?, ?, ?, ?, ?)",
            arrayOf<Any>(
                budget, category, "3f7a1c9e-$budget-$category", EDIT,
                if (deleted) 1 else 0
            )
        )
    }

    private fun assertStampedNow(db: SQLiteDatabase, sql: String) {
        val stamped = number(db, sql) ?: 0L
        val since = System.currentTimeMillis() - stamped
        assertTrue("stamped $stamped, which is ${since}ms ago", since in 0..299999L)
    }

    private fun categoryColumnOf(db: SQLiteDatabase, budget: Long): Long? {
        return number(db, "SELECT budget_category FROM budgets WHERE budget_id = $budget")
    }

    private fun number(db: SQLiteDatabase, sql: String): Long? {
        val cursor = db.rawQuery(sql, null)
        try {
            assertTrue("no row for $sql", cursor.moveToFirst())
            return if (cursor.isNull(0)) null else cursor.getLong(0)
        } finally {
            cursor.close()
        }
    }

    private fun count(db: SQLiteDatabase, table: String, selection: String?): Long {
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM $table" +
                if (selection != null) " WHERE $selection" else "",
            null
        )
        try {
            assertTrue(cursor.moveToFirst())
            return cursor.getLong(0)
        } finally {
            cursor.close()
        }
    }

    private fun text(db: SQLiteDatabase, sql: String): String? {
        val cursor = db.rawQuery(sql, null)
        try {
            assertTrue(cursor.moveToFirst())
            return cursor.getString(0)
        } finally {
            cursor.close()
        }
    }

    companion object {
        private const val CREATE_TABLE_WALLET_BEFORE_3 = "CREATE TABLE wallets (" +
            "wallet_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "wallet_name TEXT NOT NULL, " +
            "wallet_icon TEXT, " +
            "wallet_currency TEXT NOT NULL, " +
            "wallet_start_money INTEGER NOT NULL DEFAULT 0, " +
            "wallet_count_in_total INTEGER NOT NULL DEFAULT 1, " +
            "wallet_note TEXT, " +
            "wallet_archived INTEGER NOT NULL DEFAULT 0, " +
            "wallet_tag TEXT, " +
            "uuid TEXT NOT NULL UNIQUE, " +
            "last_edit INTEGER NOT NULL, " +
            "deleted INTEGER NOT NULL DEFAULT 0)"

        private const val CREATE_TABLE_CATEGORY_BEFORE_3 = "CREATE TABLE categories (" +
            "category_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "category_name TEXT NOT NULL, " +
            "category_icon TEXT NOT NULL, " +
            "category_type INTEGER NOT NULL, " +
            "category_parent INTEGER, " +
            "category_show_report INTEGER NOT NULL DEFAULT 1, " +
            "category_tag TEXT, " +
            "uuid TEXT NOT NULL UNIQUE, " +
            "last_edit INTEGER NOT NULL, " +
            "deleted INTEGER NOT NULL DEFAULT 0, " +
            "FOREIGN KEY (category_parent) REFERENCES categories(category_id) " +
            "ON UPDATE NO ACTION ON DELETE CASCADE)"

        private const val CREATE_TABLE_BUDGET_BEFORE_4 = "CREATE TABLE budgets (" +
            "budget_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "budget_type INTEGER NOT NULL, " +
            "budget_category INTEGER, " +
            "budget_start_date DATETIME NOT NULL, " +
            "budget_end_date DATETIME NOT NULL, " +
            "budget_money INTEGER NOT NULL, " +
            "budget_currency TEXT NOT NULL, " +
            "budget_tag TEXT, " +
            "uuid TEXT NOT NULL UNIQUE, " +
            "last_edit INTEGER NOT NULL, " +
            "deleted INTEGER NOT NULL DEFAULT 0, " +
            "FOREIGN KEY (budget_category) REFERENCES categories(category_id) " +
            "ON UPDATE NO ACTION ON DELETE CASCADE)"

        private const val ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}"
        private const val EDIT = 1565000000000L
        private const val NAME = "upgrade.db"
    }
}
