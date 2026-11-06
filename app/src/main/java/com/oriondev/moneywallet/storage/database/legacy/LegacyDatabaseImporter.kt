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

package com.oriondev.moneywallet.storage.database.legacy

import android.content.ContentResolver
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.collection.LongSparseArray
import com.oriondev.moneywallet.model.ColorIcon
import com.oriondev.moneywallet.model.Icon
import com.oriondev.moneywallet.model.MoneyScale
import com.oriondev.moneywallet.model.RecurrenceSetting
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.DatabaseImporter
import com.oriondev.moneywallet.storage.database.ImportException
import com.oriondev.moneywallet.storage.database.SQLDatabaseImporter
import com.oriondev.moneywallet.storage.database.model.Attachment
import com.oriondev.moneywallet.storage.database.model.Budget
import com.oriondev.moneywallet.storage.database.model.BudgetWallet
import com.oriondev.moneywallet.storage.database.model.Category
import com.oriondev.moneywallet.storage.database.model.Debt
import com.oriondev.moneywallet.storage.database.model.Event
import com.oriondev.moneywallet.storage.database.model.Place
import com.oriondev.moneywallet.storage.database.model.RecurrentTransaction
import com.oriondev.moneywallet.storage.database.model.Saving
import com.oriondev.moneywallet.storage.database.model.Transaction
import com.oriondev.moneywallet.storage.database.model.Transfer
import com.oriondev.moneywallet.storage.database.model.Wallet
import com.oriondev.moneywallet.utils.CurrencyManager
import com.oriondev.moneywallet.utils.DateUtils
import com.oriondev.moneywallet.utils.IconLoader
import com.oriondev.moneywallet.utils.Utils
import java.io.File
import java.util.UUID

/**
 * This class is an implementation of the DatabaseImporter interface and is used by
 * LegacyEditionImporter to correctly upgrade the internal database when switching from
 * a legacy edition of the application to one of the latest releases, and by
 * LegacyBackupImporter when a legacy backup has been selected to be restored.
 */
class LegacyDatabaseImporter @Throws(ImportException::class) constructor(legacyDatabase: File) : DatabaseImporter {

    companion object {
        private const val LEGACY_DECIMALS = 2

        private fun normalize(currency: String?, money: Long): Long {
            val currencyUnit = CurrencyManager.getCurrency(currency)
            if (currencyUnit != null && currencyUnit.decimals != LEGACY_DECIMALS) {
                return MoneyScale.toMinorUnitsRounded(
                    MoneyScale.toHumanAmount(money, LEGACY_DECIMALS),
                    currencyUnit.decimals
                )
            }
            return money
        }
    }

    private val mDatabase: SQLiteDatabase

    private val mCacheWallet = LongSparseArray<Long>()
    private val mCacheWalletCurrency = LongSparseArray<String>()
    private val mCacheCategory = LongSparseArray<Long>()
    private val mCacheCategoryTag = LongSparseArray<String>()
    private val mCacheEvent = LongSparseArray<Long>()
    private val mCachePlace = HashMap<String, Long>()
    private val mCacheDebt = LongSparseArray<Long>()
    private val mCacheSaving = LongSparseArray<Long>()
    private val mCacheRecurrences = LongSparseArray<Long>()
    private val mCacheTransfer = HashMap<String, Transfer>()
    private val mCacheTransaction = HashSet<String>()

    init {
        if (!legacyDatabase.exists()) {
            throw ImportException("Legacy database not exists")
        }
        val databasePath = legacyDatabase.path
        val flags = SQLiteDatabase.OPEN_READONLY
        try {
            mDatabase = SQLiteDatabase.openDatabase(databasePath, null, flags)
        } catch (e: SQLiteException) {
            throw ImportException(e.message)
        }
    }

    @Throws(ImportException::class)
    override fun importHeader() {
        // no header to check :(
    }

    @Throws(ImportException::class)
    override fun importCurrencies(contentResolver: ContentResolver) {
        // not supported in legacy database
    }

    @Throws(ImportException::class)
    override fun importWallets(contentResolver: ContentResolver) {
        val selection = "${LegacyDatabaseSchema.Wallet.DELETED} = 0"
        val cursor = mDatabase.query(LegacyDatabaseSchema.Wallet.TABLE, null, selection, null, null, null, null)
        if (cursor != null) {
            while (cursor.moveToNext()) {
                val wallet = Wallet()
                wallet.mName = getStringSafely(cursor, LegacyDatabaseSchema.Wallet.NAME)
                wallet.mIcon = getIconSafely(cursor, LegacyDatabaseSchema.Wallet.ICON, wallet.mName)
                wallet.mCurrency = getStringSafely(cursor, LegacyDatabaseSchema.Wallet.CURRENCY_ISO)
                wallet.mCountInTotal = getBooleanSafely(cursor, LegacyDatabaseSchema.Wallet.IN_TOTAL)
                wallet.mStartMoney = normalize(wallet.mCurrency, getLongSafely(cursor, LegacyDatabaseSchema.Wallet.INITIAL))
                wallet.mUUID = getStringSafely(cursor, LegacyDatabaseSchema.Wallet.UUID)
                wallet.mDeleted = getBooleanSafely(cursor, LegacyDatabaseSchema.Wallet.DELETED)
                wallet.mLastEdit = getLongSafely(cursor, LegacyDatabaseSchema.Wallet.LAST_EDIT)
                try {
                    val id = SQLDatabaseImporter.insert(contentResolver, wallet)
                    // cache the old id and the new id to reconstruct the relationships
                    val legacyId = getLongSafely(cursor, LegacyDatabaseSchema.Wallet.ID)
                    mCacheWallet.put(legacyId, id)
                    wallet.mCurrency?.let { mCacheWalletCurrency.put(legacyId, it) }
                } catch (_: Exception) {
                    // if an exception occur, the wallet id is not inserted in the cache
                    // and all the related stuff will be discarded during the migration
                }
            }
            cursor.close()
        }
    }

    @Throws(ImportException::class)
    override fun importCategories(contentResolver: ContentResolver) {
        // firstly, query only the categories that have no parent id set
        var selection = "${LegacyDatabaseSchema.Category.DELETED} = 0 AND (" +
            "${LegacyDatabaseSchema.Category.PARENT} IS NULL OR " +
            "${LegacyDatabaseSchema.Category.PARENT} <= 0)"
        var cursor = mDatabase.query(LegacyDatabaseSchema.Category.TABLE, null, selection, null, null, null, null)
        if (cursor != null) {
            // populate map to easily detect a system category
            val systemCategoryMap = LongSparseArray<String>().apply {
                put(LegacyDatabaseSchema.INDEX_HIDDEN_CATEGORY_CREDIT, Contract.CategoryTag.CREDIT)
                put(LegacyDatabaseSchema.INDEX_HIDDEN_CATEGORY_CREDIT_PAID, Contract.CategoryTag.PAID_CREDIT)
                put(LegacyDatabaseSchema.INDEX_HIDDEN_CATEGORY_DEBT, Contract.CategoryTag.DEBT)
                put(LegacyDatabaseSchema.INDEX_HIDDEN_CATEGORY_DEBT_PAID, Contract.CategoryTag.PAID_DEBT)
                put(LegacyDatabaseSchema.INDEX_HIDDEN_CATEGORY_TRANSFER, Contract.CategoryTag.TRANSFER)
                put(LegacyDatabaseSchema.INDEX_HIDDEN_CATEGORY_TRANSFER_TAX, Contract.CategoryTag.TRANSFER_TAX)
                put(LegacyDatabaseSchema.INDEX_HIDDEN_CATEGORY_OPERATION_TAX, Contract.CategoryTag.TAX)
                put(LegacyDatabaseSchema.INDEX_HIDDEN_CATEGORY_SAVING_IN, Contract.CategoryTag.SAVING_DEPOSIT)
                put(LegacyDatabaseSchema.INDEX_HIDDEN_CATEGORY_SAVING_OUT, Contract.CategoryTag.SAVING_WITHDRAW)
            }
            // iterate between the rows returned by the query
            while (cursor.moveToNext()) {
                val legacyId = getLongSafely(cursor, LegacyDatabaseSchema.Category.ID)
                val parentId = getIntSafely(cursor, LegacyDatabaseSchema.Category.PARENT).toLong()
                val systemCategoryTag = systemCategoryMap.get(parentId)
                if (parentId < 0 && systemCategoryTag != null) {
                    // this is a system category, we have to link it to the new system category
                    val id = getSystemCategoryId(contentResolver, systemCategoryTag)
                    mCacheCategory.put(legacyId, id)
                    mCacheCategoryTag.put(legacyId, systemCategoryTag)
                } else {
                    // this is a standard category, just insert it and link the id in the cache
                    val category = Category()
                    category.mName = getStringSafely(cursor, LegacyDatabaseSchema.Category.NAME)
                    category.mIcon = getIconSafely(cursor, LegacyDatabaseSchema.Category.ICON, category.mName)
                    category.mType = if (getBooleanSafely(cursor, LegacyDatabaseSchema.Category.IS_IN)) {
                        Contract.CategoryType.INCOME.value
                    } else {
                        Contract.CategoryType.EXPENSE.value
                    }
                    category.mShowReport = getBooleanSafely(cursor, LegacyDatabaseSchema.Category.REPORT)
                    category.mUUID = getStringSafely(cursor, LegacyDatabaseSchema.Category.UUID)
                    category.mDeleted = getBooleanSafely(cursor, LegacyDatabaseSchema.Category.DELETED)
                    category.mLastEdit = getLongSafely(cursor, LegacyDatabaseSchema.Category.LAST_EDIT)
                    try {
                        val id = SQLDatabaseImporter.insert(contentResolver, category)
                        mCacheCategory.put(legacyId, id)
                    } catch (_: Exception) {
                        // if an exception occur, the category id is not inserted in the cache
                        // and all the related stuff will be discarded during the migration
                    }
                }
            }
            cursor.close()
        }
        // now, query all the categories that have a parent set
        selection = "${LegacyDatabaseSchema.Category.DELETED} = 0 AND " +
            "${LegacyDatabaseSchema.Category.PARENT} > 0"
        cursor = mDatabase.query(LegacyDatabaseSchema.Category.TABLE, null, selection, null, null, null, null)
        if (cursor != null) {
            while (cursor.moveToNext()) {
                val category = Category()
                category.mName = getStringSafely(cursor, LegacyDatabaseSchema.Category.NAME)
                category.mIcon = getIconSafely(cursor, LegacyDatabaseSchema.Category.ICON, category.mName)
                category.mType = if (getBooleanSafely(cursor, LegacyDatabaseSchema.Category.IS_IN)) {
                    Contract.CategoryType.INCOME.value
                } else {
                    Contract.CategoryType.EXPENSE.value
                }
                category.mParent = mCacheCategory.get(getLongSafely(cursor, LegacyDatabaseSchema.Category.PARENT))
                category.mShowReport = getBooleanSafely(cursor, LegacyDatabaseSchema.Category.REPORT)
                category.mUUID = getStringSafely(cursor, LegacyDatabaseSchema.Category.UUID)
                category.mDeleted = getBooleanSafely(cursor, LegacyDatabaseSchema.Category.DELETED)
                category.mLastEdit = getLongSafely(cursor, LegacyDatabaseSchema.Category.LAST_EDIT)
                try {
                    val id = SQLDatabaseImporter.insert(contentResolver, category)
                    val legacyId = getLongSafely(cursor, LegacyDatabaseSchema.Category.ID)
                    mCacheCategory.put(legacyId, id)
                } catch (_: Exception) {
                    // if an exception occur, the category id is not inserted in the cache
                    // and all the related stuff will be discarded during the migration
                }
            }
            cursor.close()
        }
    }

    @Throws(ImportException::class)
    private fun getSystemCategoryId(contentResolver: ContentResolver, tag: String): Long {
        var systemCategoryId = -1L
        val uri = DataContentProvider.CONTENT_CATEGORIES
        val projection = arrayOf(Contract.Category.ID)
        val selection = "${Contract.Category.TAG} = ?"
        val selectionArgs = arrayOf(tag)
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(Contract.Category.ID)
                if (index >= 0) {
                    systemCategoryId = cursor.getLong(index)
                }
            }
            cursor.close()
        }
        if (systemCategoryId == -1L) {
            throw ImportException("Failed to link a legacy system category in the new database")
        }
        return systemCategoryId
    }

    @Throws(ImportException::class)
    override fun importEvents(contentResolver: ContentResolver) {
        val selection = "${LegacyDatabaseSchema.Event.DELETED} = 0"
        val cursor = mDatabase.query(LegacyDatabaseSchema.Event.TABLE, null, selection, null, null, null, null)
        if (cursor != null) {
            while (cursor.moveToNext()) {
                val event = Event()
                event.mName = getStringSafely(cursor, LegacyDatabaseSchema.Event.NAME)
                event.mIcon = getIconSafely(cursor, LegacyDatabaseSchema.Event.ICON, event.mName)
                event.mNote = getStringSafely(cursor, LegacyDatabaseSchema.Event.NOTE)
                event.mStartDate = getStringSafely(cursor, LegacyDatabaseSchema.Event.DATE_FROM)
                event.mEndDate = getStringSafely(cursor, LegacyDatabaseSchema.Event.DATE_TO)
                event.mUUID = getStringSafely(cursor, LegacyDatabaseSchema.Event.UUID)
                event.mDeleted = getBooleanSafely(cursor, LegacyDatabaseSchema.Event.DELETED)
                event.mLastEdit = getLongSafely(cursor, LegacyDatabaseSchema.Event.LAST_EDIT)
                try {
                    val id = SQLDatabaseImporter.insert(contentResolver, event)
                    val legacyId = getLongSafely(cursor, LegacyDatabaseSchema.Event.ID)
                    mCacheEvent.put(legacyId, id)
                } catch (_: Exception) {
                    // if an exception occur, the event id is not inserted in the cache
                    // and all the related stuff will be discarded during the migration
                }
            }
            cursor.close()
        }
    }

    @Throws(ImportException::class)
    override fun importPlaces(contentResolver: ContentResolver) {
        // The legacy database does not have a separate table for places: it saves the
        // place entity as a string directly inside the other tables (transactions,
        // recurrences and debts). We have to query these table to retrieve all the
        // places that have been added (we use the place name as key so, if multiple
        // places with the same name were found, we insert only one place in the table).
        val rawQuery = "SELECT place_name, place_latitude, place_longitude FROM (" +
            "SELECT transaction_place AS place_name, " +
            "transaction_latitude AS place_latitude, " +
            "transaction_longitude AS place_longitude " +
            "FROM transaction_table " +
            "WHERE transaction_deleted = 0 AND transaction_place != '' " +
            "UNION SELECT recurring_place AS place_name, " +
            "recurring_latitude AS place_latitude, " +
            "recurring_longitude AS place_longitude " +
            "FROM recurring_table " +
            "WHERE recurring_deleted = 0 AND recurring_place != '' " +
            "UNION SELECT debt_place AS place_name, " +
            "NULL AS place_latitude, " +
            "NULL AS place_longitude " +
            "FROM debt_table " +
            "WHERE debt_deleted = 0 AND debt_place != '' " +
            ") GROUP BY place_name"
        val cursor = mDatabase.rawQuery(rawQuery, null)
        if (cursor != null) {
            while (cursor.moveToNext()) {
                val place = Place()
                place.mName = getStringSafely(cursor, "place_name")
                place.mIcon = getIconSafely(cursor, null, place.mName)
                place.mLatitude = getDoubleObjSafely(cursor, "place_latitude")
                place.mLongitude = getDoubleObjSafely(cursor, "place_longitude")
                place.mUUID = UUID.randomUUID().toString()
                place.mLastEdit = System.currentTimeMillis()
                place.mDeleted = false
                try {
                    val id = SQLDatabaseImporter.insert(contentResolver, place)
                    place.mName?.let { mCachePlace.put(it, id) }
                } catch (_: Exception) {
                    // if an exception occur, the place id is not inserted in the cache
                    // and all the related stuff will be discarded during the migration
                }
            }
            cursor.close()
        }
    }

    @Throws(ImportException::class)
    override fun importPeople(contentResolver: ContentResolver) {
        // not supported in legacy database
    }

    @Throws(ImportException::class)
    override fun importEventPeople(contentResolver: ContentResolver) {
        // not supported in legacy database
    }

    @Throws(ImportException::class)
    override fun importDebts(contentResolver: ContentResolver) {
        val selection = "${LegacyDatabaseSchema.Debt.DELETED} = 0"
        val cursor = mDatabase.query(LegacyDatabaseSchema.Debt.TABLE, null, selection, null, null, null, null)
        if (cursor != null) {
            while (cursor.moveToNext()) {
                val debt = Debt()
                val legacyType = getIntSafely(cursor, LegacyDatabaseSchema.Debt.TYPE)
                debt.mType = if (legacyType == LegacyDatabaseSchema.TYPE_DEBT) {
                    Contract.DebtType.DEBT.value
                } else {
                    Contract.DebtType.CREDIT.value
                }
                debt.mDescription = getStringSafely(cursor, LegacyDatabaseSchema.Debt.DESCRIPTION)
                debt.mIcon = getIconSafely(cursor, null, debt.mDescription)
                debt.mDate = getStringSafely(cursor, LegacyDatabaseSchema.Debt.DATE)
                debt.mExpirationDate = getStringSafely(cursor, LegacyDatabaseSchema.Debt.EXPIRATION_DATE)
                val legacyWalletId = getLongSafely(cursor, LegacyDatabaseSchema.Debt.WALLET)
                debt.mWallet = mCacheWallet.get(legacyWalletId)
                debt.mNote = getStringSafely(cursor, LegacyDatabaseSchema.Debt.NOTE)
                val placeName = getStringSafely(cursor, LegacyDatabaseSchema.Debt.PLACE)
                debt.mPlace = if (placeName != null) mCachePlace[placeName] else null
                debt.mMoney = normalize(
                    mCacheWalletCurrency.get(legacyWalletId),
                    getLongSafely(cursor, LegacyDatabaseSchema.Debt.IMPORT)
                )
                debt.mArchived = false
                debt.mUUID = getStringSafely(cursor, LegacyDatabaseSchema.Debt.UUID)
                debt.mDeleted = getBooleanSafely(cursor, LegacyDatabaseSchema.Debt.DELETED)
                debt.mLastEdit = getLongSafely(cursor, LegacyDatabaseSchema.Debt.LAST_EDIT)
                try {
                    val id = SQLDatabaseImporter.insert(contentResolver, debt)
                    val legacyId = getLongSafely(cursor, LegacyDatabaseSchema.Debt.ID)
                    mCacheDebt.put(legacyId, id)
                } catch (_: Exception) {
                    // if an exception occur, the debt id is not inserted in the cache
                    // and all the related stuff will be discarded during the migration
                }
            }
            cursor.close()
        }
    }

    @Throws(ImportException::class)
    override fun importDebtPeople(contentResolver: ContentResolver) {
        // not supported in legacy database
    }

    @Throws(ImportException::class)
    override fun importBudgets(contentResolver: ContentResolver) {
        val selection = "${LegacyDatabaseSchema.Budget.DELETED} = 0"
        val cursor = mDatabase.query(LegacyDatabaseSchema.Budget.TABLE, null, selection, null, null, null, null)
        if (cursor != null) {
            while (cursor.moveToNext()) {
                val walletIds = getStringSafely(cursor, LegacyDatabaseSchema.Budget.WALLETS)
                val legacyIds = decodeWallets(walletIds)
                if (legacyIds != null && legacyIds.isNotEmpty()) {
                    // reconstruct the budget item starting from the legacy copy
                    val budget = Budget()
                    val legacyType = getIntSafely(cursor, LegacyDatabaseSchema.Budget.TYPE)
                    if (legacyType == LegacyDatabaseSchema.TYPE_BUDGET_CATEGORY) {
                        budget.mType = Contract.BudgetType.CATEGORY.value
                        budget.mCategory = mCacheCategory.get(getLongSafely(cursor, LegacyDatabaseSchema.Budget.CATEGORY_OR_FLOW))
                    } else if (legacyType == LegacyDatabaseSchema.TYPE_BUDGET_CASH_FLOW) {
                        val legacyFlow = getIntSafely(cursor, LegacyDatabaseSchema.Budget.CATEGORY_OR_FLOW)
                        budget.mType = when (legacyFlow) {
                            LegacyDatabaseSchema.BUDGET_INFLOW -> Contract.BudgetType.INCOMES.value
                            LegacyDatabaseSchema.BUDGET_OUTFLOW -> Contract.BudgetType.EXPENSES.value
                            else -> throw ImportException("Invalid budget flow type ($legacyFlow)")
                        }
                        budget.mCategory = null
                    } else {
                        throw ImportException("Invalid budget type ($legacyType)")
                    }
                    budget.mStartDate = getStringSafely(cursor, LegacyDatabaseSchema.Budget.DATE_FROM)
                    budget.mEndDate = getStringSafely(cursor, LegacyDatabaseSchema.Budget.DATE_TO)
                    budget.mCurrency = mCacheWalletCurrency.get(legacyIds[0])
                    budget.mMoney = normalize(budget.mCurrency, getLongSafely(cursor, LegacyDatabaseSchema.Budget.MAX))
                    budget.mUUID = getStringSafely(cursor, LegacyDatabaseSchema.Budget.UUID)
                    budget.mDeleted = getBooleanSafely(cursor, LegacyDatabaseSchema.Budget.DELETED)
                    budget.mLastEdit = getLongSafely(cursor, LegacyDatabaseSchema.Budget.LAST_EDIT)
                    try {
                        val id = SQLDatabaseImporter.insert(contentResolver, budget)
                        SQLDatabaseImporter.insertBudgetCategory(contentResolver, id, budget.mCategory)
                        // for each linked wallet, insert a link in the new database
                        for (legacyId in legacyIds) {
                            val budgetWallet = BudgetWallet().apply {
                                mBudget = id
                                mWallet = mCacheWallet.get(legacyId)
                                mUUID = UUID.randomUUID().toString()
                                mLastEdit = System.currentTimeMillis()
                                mDeleted = false
                            }
                            SQLDatabaseImporter.insert(contentResolver, budgetWallet)
                        }
                    } catch (_: Exception) {
                        // if an exception occur, the budget is not inserted in the database
                        // and all the related budget-wallet entities are not created
                    }
                }
            }
            cursor.close()
        }
    }

    private fun decodeWallets(stringToDecode: String?): Array<Long>? {
        if (stringToDecode != null) {
            val parts = stringToDecode.split(';'.toString())
            val walletIds = ArrayList<Long>()
            for (i in 1 until parts.size) {
                try {
                    val walletId = parts[i].toLong()
                    if (mCacheWallet.get(walletId) != null) {
                        walletIds.add(walletId)
                    }
                } catch (_: NumberFormatException) {
                    // malformed wallet string!
                }
            }
            return walletIds.toTypedArray()
        }
        return null
    }

    @Throws(ImportException::class)
    override fun importBudgetWallets(contentResolver: ContentResolver) {
        // not supported in legacy database
    }

    @Throws(ImportException::class)
    override fun importBudgetCategories(contentResolver: ContentResolver) {
        // a legacy budget names one category on its own row, and importBudgets above puts that
        // into the join table as it goes, so there is nothing left to read here
    }

    @Throws(ImportException::class)
    override fun importCategoryRules(contentResolver: ContentResolver) {
        // not supported in legacy database
    }

    @Throws(ImportException::class)
    override fun importSavings(contentResolver: ContentResolver) {
        val selection = "${LegacyDatabaseSchema.Saving.DELETED} = 0"
        val cursor = mDatabase.query(LegacyDatabaseSchema.Saving.TABLE, null, selection, null, null, null, null)
        if (cursor != null) {
            while (cursor.moveToNext()) {
                val saving = Saving()
                saving.mDescription = getStringSafely(cursor, LegacyDatabaseSchema.Saving.DESCRIPTION)
                saving.mIcon = getIconSafely(cursor, null, saving.mDescription)
                val legacyWalletId = getLongSafely(cursor, LegacyDatabaseSchema.Saving.WALLET)
                saving.mStartMoney = normalize(
                    mCacheWalletCurrency.get(legacyWalletId),
                    getLongSafely(cursor, LegacyDatabaseSchema.Saving.INITIAL)
                )
                saving.mEndMoney = normalize(
                    mCacheWalletCurrency.get(legacyWalletId),
                    getLongSafely(cursor, LegacyDatabaseSchema.Saving.TARGET)
                )
                saving.mWallet = mCacheWallet.get(legacyWalletId)
                saving.mEndDate = getStringSafely(cursor, LegacyDatabaseSchema.Saving.END_DATE)
                saving.mComplete = getBooleanSafely(cursor, LegacyDatabaseSchema.Saving.COMPLETE)
                saving.mUUID = getStringSafely(cursor, LegacyDatabaseSchema.Saving.UUID)
                saving.mDeleted = getBooleanSafely(cursor, LegacyDatabaseSchema.Saving.DELETED)
                saving.mLastEdit = getLongSafely(cursor, LegacyDatabaseSchema.Saving.LAST_EDIT)
                try {
                    val id = SQLDatabaseImporter.insert(contentResolver, saving)
                    val legacyId = getLongSafely(cursor, LegacyDatabaseSchema.Saving.ID)
                    mCacheSaving.put(legacyId, id)
                } catch (_: Exception) {
                    // if an exception occur, the saving id is not inserted in the cache
                    // and all the related stuff will be discarded during the migration
                }
            }
            cursor.close()
        }
    }

    @Throws(ImportException::class)
    override fun importRecurrentTransactions(contentResolver: ContentResolver) {
        val selection = "${LegacyDatabaseSchema.Recurrence.DELETED} = 0"
        val cursor = mDatabase.query(LegacyDatabaseSchema.Recurrence.TABLE, null, selection, null, null, null, null)
        if (cursor != null) {
            while (cursor.moveToNext()) {
                val transaction = RecurrentTransaction()
                val legacyId = getLongSafely(cursor, LegacyDatabaseSchema.Recurrence.ID)
                val legacyWalletId = getLongSafely(cursor, LegacyDatabaseSchema.Recurrence.WALLET)
                transaction.mMoney = normalize(
                    mCacheWalletCurrency.get(legacyWalletId),
                    getLongSafely(cursor, LegacyDatabaseSchema.Recurrence.IMPORT)
                )
                transaction.mDescription = getStringSafely(cursor, LegacyDatabaseSchema.Recurrence.DESCRIPTION)
                transaction.mCategory = mCacheCategory.get(getLongSafely(cursor, LegacyDatabaseSchema.Recurrence.CATEGORY))
                transaction.mDirection = if (getBooleanSafely(cursor, LegacyDatabaseSchema.Recurrence.IS_IN)) {
                    Contract.Direction.INCOME
                } else {
                    Contract.Direction.EXPENSE
                }
                transaction.mWallet = mCacheWallet.get(legacyWalletId)
                val placeName = getStringSafely(cursor, LegacyDatabaseSchema.Recurrence.PLACE)
                transaction.mPlace = if (placeName != null) mCachePlace[placeName] else null
                transaction.mNote = getStringSafely(cursor, LegacyDatabaseSchema.Recurrence.NOTE)
                transaction.mConfirmed = true
                transaction.mCountInTotal = true
                val encodedInfo = getStringSafely(cursor, LegacyDatabaseSchema.Recurrence.ENCODED_INFO)
                val recurrenceSetting = parseRecurrenceSetting(encodedInfo)
                transaction.mStartDate = DateUtils.getSQLDateString(recurrenceSetting.startDate)
                transaction.mLastOccurrence = getRecurrenceLastOccurrence(legacyId, transaction.mStartDate)
                val nextOccurrence = recurrenceSetting.getNextOccurrence(DateUtils.getDateFromSQLDateString(transaction.mLastOccurrence))
                transaction.mNextOccurrence = if (nextOccurrence != null) DateUtils.getSQLDateString(nextOccurrence) else null
                transaction.mRule = recurrenceSetting.rule
                transaction.mUUID = getStringSafely(cursor, LegacyDatabaseSchema.Recurrence.UUID)
                transaction.mDeleted = getBooleanSafely(cursor, LegacyDatabaseSchema.Recurrence.DELETED)
                transaction.mLastEdit = getLongSafely(cursor, LegacyDatabaseSchema.Recurrence.LAST_EDIT)
                try {
                    val id = SQLDatabaseImporter.insert(contentResolver, transaction)
                    mCacheRecurrences.put(legacyId, id)
                } catch (_: Exception) {
                    // if an exception occur, the recurrence id is not inserted in the cache
                    // and all the related stuff will be discarded during the migration
                }
            }
            cursor.close()
        }
    }

    private fun parseRecurrenceSetting(encodedInfo: String?): RecurrenceSetting {
        val rules = encodedInfo?.split(";") ?: emptyList()
        var recurrenceType = RecurrenceSetting.TYPE_DAILY
        if (rules.isNotEmpty()) {
            when (rules[0].toIntOrNull() ?: 0) {
                0 -> recurrenceType = RecurrenceSetting.TYPE_DAILY
                1 -> recurrenceType = RecurrenceSetting.TYPE_WEEKLY
                2 -> recurrenceType = RecurrenceSetting.TYPE_MONTHLY
                3 -> recurrenceType = RecurrenceSetting.TYPE_YEARLY
            }
        }
        val offset = if (rules.size > 1) rules[1].toIntOrNull() ?: 0 else 0
        val weekDays = BooleanArray(7)
        if (rules.size > 3) {
            val daysStr = rules[3]
            weekDays[0] = daysStr.contains("0")
            weekDays[1] = daysStr.contains("1")
            weekDays[2] = daysStr.contains("2")
            weekDays[3] = daysStr.contains("3")
            weekDays[4] = daysStr.contains("4")
            weekDays[5] = daysStr.contains("5")
            weekDays[6] = daysStr.contains("6")
        }
        var endType = RecurrenceSetting.END_FOREVER
        if (rules.size > 4) {
            when (rules[4].toIntOrNull() ?: -1) {
                0 -> endType = RecurrenceSetting.END_UNTIL
                1 -> endType = RecurrenceSetting.END_FOR
            }
        }
        val occurrences = if (rules.size > 5) rules[5].toIntOrNull() ?: 0 else 0
        val startDate = if (rules.size > 6) DateUtils.getDateFromSQLDateString(rules[6]) else null
        val endDate = if (rules.size > 7) DateUtils.getDateFromSQLDateString(rules[7]) else null

        // reconstruct the rule object
        val builder = RecurrenceSetting.Builder(startDate, recurrenceType)
        builder.setOffset(offset)
        if (recurrenceType == RecurrenceSetting.TYPE_WEEKLY) {
            builder.setRepeatWeekDay(weekDays)
        } else if (recurrenceType == RecurrenceSetting.TYPE_MONTHLY) {
            builder.setRepeatSameMonthDay()
        }
        when (endType) {
            RecurrenceSetting.END_UNTIL -> builder.setEndUntil(endDate)
            RecurrenceSetting.END_FOR -> builder.setEndFor(occurrences)
        }
        return builder.build()
    }

    private fun getRecurrenceLastOccurrence(id: Long, startDate: String?): String? {
        // we need to query the maximum value of the transaction date where the date
        // is previous or equals today
        val rawQuery = "SELECT MAX(${LegacyDatabaseSchema.Transaction.DATE}) FROM " +
            "${LegacyDatabaseSchema.Transaction.TABLE} WHERE DATE(" +
            "${LegacyDatabaseSchema.Transaction.DATE}) <= DATE('now', 'localtime') AND " +
            "${LegacyDatabaseSchema.Transaction.RECURRENCE} = ? AND " +
            "${LegacyDatabaseSchema.Transaction.DELETED} = 0"
        val selectionArgs = arrayOf(id.toString())
        val cursor = mDatabase.rawQuery(rawQuery, selectionArgs)
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                val date = cursor.getString(0)
                if (!date.isNullOrEmpty()) {
                    cursor.close()
                    return date
                }
            }
            cursor.close()
        }
        return startDate
    }

    @Throws(ImportException::class)
    override fun importRecurrentTransfers(contentResolver: ContentResolver) {
        // not supported in legacy database
    }

    @Throws(ImportException::class)
    override fun importTransactions(contentResolver: ContentResolver) {
        val selection = "${LegacyDatabaseSchema.Transaction.DELETED} = 0 AND DATETIME(" +
            "${LegacyDatabaseSchema.Transaction.DATE}) <= DATETIME('now', 'localtime')"
        val cursor = mDatabase.query(LegacyDatabaseSchema.Transaction.TABLE, null, selection, null, null, null, null)
        if (cursor != null) {
            while (cursor.moveToNext()) {
                val transaction = Transaction()
                val legacyWalletId = getLongSafely(cursor, LegacyDatabaseSchema.Transaction.WALLET)
                transaction.mMoney = normalize(
                    mCacheWalletCurrency.get(legacyWalletId),
                    getLongSafely(cursor, LegacyDatabaseSchema.Transaction.IMPORT)
                )
                transaction.mDate = getDateTimeSafely(cursor, LegacyDatabaseSchema.Transaction.DATE)
                transaction.mDescription = getStringSafely(cursor, LegacyDatabaseSchema.Transaction.DESCRIPTION)
                transaction.mCategory = mCacheCategory.get(getLongSafely(cursor, LegacyDatabaseSchema.Transaction.CATEGORY))
                transaction.mDirection = if (getBooleanSafely(cursor, LegacyDatabaseSchema.Transaction.IS_IN)) {
                    Contract.Direction.INCOME
                } else {
                    Contract.Direction.EXPENSE
                }
                transaction.mWallet = mCacheWallet.get(legacyWalletId)
                val placeName = getStringSafely(cursor, LegacyDatabaseSchema.Transaction.PLACE)
                transaction.mPlace = if (placeName != null) mCachePlace[placeName] else null
                transaction.mNote = getStringSafely(cursor, LegacyDatabaseSchema.Transaction.NOTE)
                transaction.mEvent = mCacheEvent.get(getLongSafely(cursor, LegacyDatabaseSchema.Transaction.EVENT))
                transaction.mRecurrence = mCacheRecurrences.get(getLongSafely(cursor, LegacyDatabaseSchema.Transaction.RECURRENCE))
                transaction.mConfirmed = true
                transaction.mCountInTotal = true

                val debtIndex = cursor.getColumnIndex(LegacyDatabaseSchema.Transaction.DEBT)
                val savingIndex = cursor.getColumnIndex(LegacyDatabaseSchema.Transaction.SAVING)
                val transferIndex = cursor.getColumnIndex(LegacyDatabaseSchema.Transaction.TRANSFER)

                if (debtIndex >= 0 && !cursor.isNull(debtIndex)) {
                    transaction.mDebt = mCacheDebt.get(getLongSafely(cursor, LegacyDatabaseSchema.Transaction.DEBT))
                    transaction.mType = if (transaction.mDebt != null) {
                        Contract.TransactionType.DEBT
                    } else {
                        Contract.TransactionType.STANDARD
                    }
                } else if (savingIndex >= 0 && !cursor.isNull(savingIndex)) {
                    transaction.mSaving = mCacheSaving.get(getLongSafely(cursor, LegacyDatabaseSchema.Transaction.SAVING))
                    transaction.mType = if (transaction.mSaving != null) {
                        Contract.TransactionType.SAVING
                    } else {
                        Contract.TransactionType.STANDARD
                    }
                } else if (transferIndex >= 0 && !cursor.isNull(transferIndex)) {
                    transaction.mType = Contract.TransactionType.TRANSFER
                } else {
                    transaction.mType = Contract.TransactionType.STANDARD
                }

                transaction.mUUID = getStringSafely(cursor, LegacyDatabaseSchema.Transaction.UUID)
                transaction.mDeleted = getBooleanSafely(cursor, LegacyDatabaseSchema.Transaction.DELETED)
                transaction.mLastEdit = getLongSafely(cursor, LegacyDatabaseSchema.Transaction.LAST_EDIT)
                try {
                    val id = SQLDatabaseImporter.insert(contentResolver, transaction)
                    transaction.mUUID?.let { mCacheTransaction.add(it) }
                    // handle the case of a transfer transaction
                    if (transaction.mType == Contract.TransactionType.TRANSFER) {
                        val transferId = getStringSafely(cursor, LegacyDatabaseSchema.Transaction.TRANSFER)
                        if (transferId != null) {
                            var transfer = mCacheTransfer[transferId]
                            if (transfer == null) {
                                transfer = Transfer()
                            }
                            if (transaction.mDirection == Contract.Direction.INCOME) {
                                transfer.mTransactionTo = id
                            } else if (transaction.mDirection == Contract.Direction.EXPENSE) {
                                val legacyCategoryId = getLongSafely(cursor, LegacyDatabaseSchema.Transaction.CATEGORY)
                                val categoryTag = mCacheCategoryTag.get(legacyCategoryId)
                                if (Contract.CategoryTag.TRANSFER_TAX == categoryTag) {
                                    transfer.mTransactionTax = id
                                } else if (Contract.CategoryTag.TRANSFER == categoryTag) {
                                    transfer.mTransactionFrom = id
                                    transfer.mDescription = transaction.mDescription
                                    transfer.mDate = transaction.mDate
                                    transfer.mNote = transaction.mNote
                                    transfer.mPlace = transaction.mPlace
                                    transfer.mEvent = transaction.mEvent
                                    transfer.mConfirmed = true
                                    transfer.mCountInTotal = true
                                    transfer.mUUID = UUID.randomUUID().toString()
                                    transfer.mDeleted = false
                                    transfer.mLastEdit = System.currentTimeMillis()
                                }
                            }
                            mCacheTransfer[transferId] = transfer
                        }
                    }
                } catch (_: Exception) {
                    // if an exception occur, the transaction id is not inserted in the cache
                    // and all the related stuff will be discarded during the migration
                }
            }
            cursor.close()
        }
    }

    @Throws(ImportException::class)
    override fun importTransactionPeople(contentResolver: ContentResolver) {
        // not supported in legacy database
    }

    @Throws(ImportException::class)
    override fun importTransactionModels(contentResolver: ContentResolver) {
        // not supported in legacy database
    }

    @Throws(ImportException::class)
    override fun importTransfers(contentResolver: ContentResolver) {
        for (transfer in mCacheTransfer.values) {
            if (transfer.mTransactionFrom != null && transfer.mTransactionTo != null) {
                try {
                    SQLDatabaseImporter.insert(contentResolver, transfer)
                } catch (_: Exception) {
                    // if an exception occur, the transfer has not been correctly inserted and the
                    // related transactions are registered as standard transactions
                }
            }
        }
    }

    @Throws(ImportException::class)
    override fun importTransferPeople(contentResolver: ContentResolver) {
        // not supported in legacy database
    }

    @Throws(ImportException::class)
    override fun importTransferModels(contentResolver: ContentResolver) {
        // not supported in legacy database
    }

    @Throws(ImportException::class)
    override fun importAttachments(contentResolver: ContentResolver) {
        // not supported in legacy database
    }

    @Throws(ImportException::class)
    override fun importTransactionAttachments(contentResolver: ContentResolver) {
        // not supported in legacy database
    }

    @Throws(ImportException::class)
    override fun importTransferAttachments(contentResolver: ContentResolver) {
        // not supported in legacy database
    }

    fun getAttachmentId(legacyIdentifier: String?): String? {
        return if (legacyIdentifier != null && mCacheTransaction.contains(legacyIdentifier)) {
            UUID.randomUUID().toString()
        } else {
            null
        }
    }

    fun importAttachment(contentResolver: ContentResolver, file: String?, size: Long) {
        val attachment = Attachment().apply {
            mFile = file
            mName = file
            mType = "image/*"
            mSize = size
            mUUID = UUID.randomUUID().toString()
            mLastEdit = System.currentTimeMillis()
            mDeleted = false
        }
        try {
            SQLDatabaseImporter.insert(contentResolver, attachment)
        } catch (_: Exception) {
            // simply ignore this case
        }
    }

    private fun getIconSafely(cursor: Cursor, columnName: String?, name: String?): String {
        val randomColor = Utils.getRandomMDColor()
        val iconText = IconLoader.getColorIconString(name)
        val icon = ColorIcon(randomColor, iconText)
        return icon.toString()
    }

    private fun getDateTimeSafely(cursor: Cursor, columnName: String): String? {
        val index = cursor.getColumnIndex(columnName)
        if (index >= 0) {
            val str = cursor.getString(index)
            if (str != null) {
                return "$str 00:00:00"
            }
        }
        return null
    }

    private fun getStringSafely(cursor: Cursor, columnName: String): String? {
        val index = cursor.getColumnIndex(columnName)
        return if (index >= 0) cursor.getString(index) else null
    }

    private fun getIntSafely(cursor: Cursor, columnName: String): Int {
        val index = cursor.getColumnIndex(columnName)
        return if (index >= 0) cursor.getInt(index) else 0
    }

    private fun getLongSafely(cursor: Cursor, columnName: String): Long {
        val index = cursor.getColumnIndex(columnName)
        return if (index >= 0) cursor.getLong(index) else 0L
    }

    private fun getBooleanSafely(cursor: Cursor, columnName: String): Boolean {
        val index = cursor.getColumnIndex(columnName)
        return index >= 0 && cursor.getInt(index) == 1
    }

    private fun getDoubleObjSafely(cursor: Cursor, columnName: String): Double? {
        val index = cursor.getColumnIndex(columnName)
        return if (index >= 0 && !cursor.isNull(index)) cursor.getDouble(index) else null
    }

    @Throws(ImportException::class)
    override fun close() {
        mDatabase.close()
    }
}
