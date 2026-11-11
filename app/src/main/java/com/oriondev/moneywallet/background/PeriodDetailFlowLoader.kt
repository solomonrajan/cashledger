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

package com.oriondev.moneywallet.background

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import com.oriondev.moneywallet.model.Category
import com.oriondev.moneywallet.model.CategoryMoney
import com.oriondev.moneywallet.model.CurrencyUnit
import com.oriondev.moneywallet.model.Money
import com.oriondev.moneywallet.model.PeriodDetailFlowData
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.view.chart.PieData
import com.oriondev.moneywallet.ui.view.chart.PieSlice
import com.oriondev.moneywallet.utils.CurrencyManager
import com.oriondev.moneywallet.utils.DateUtils
import com.oriondev.moneywallet.utils.IconLoader
import java.util.Date

/**
 * Created by andrea on 13/08/18.
 */
class PeriodDetailFlowLoader(
    context: Context,
    private val mStartDate: Date?,
    private val mEndDate: Date?,
    private val mIncomes: Boolean
) : AbstractGenericLoader<PeriodDetailFlowData>(context) {

    override fun getObservedUri(): Uri = DataContentProvider.CONTENT_ALL

    @SuppressLint("Range", "UseSparseArrays")
    override fun loadInBackground(): PeriodDetailFlowData {
        val totalMoney = Money()
        val pieDataSets = HashMap<CurrencyUnit?, PieData>()
        val categoryMoneyMap = HashMap<Long, CategoryMoney>()
        val childMoneyMap = HashMap<Long, MutableMap<Long, CategoryMoney>>()
        val directMoneyMap = HashMap<Long, Money>()
        val categoryCache = loadCategoryCache()
        val childCategoryCache = loadChildCategoryCache()
        val uri = DataContentProvider.CONTENT_TRANSACTIONS
        val projection = arrayOf(
            Contract.Transaction.CATEGORY_ID,
            Contract.Transaction.CATEGORY_PARENT_ID,
            Contract.Transaction.MONEY,
            Contract.Transaction.WALLET_CURRENCY
        )
        var selection: String
        val selectionArgs: Array<String>?
        val currentWallet = PreferenceManager.getCurrentWallet()
        if (currentWallet == PreferenceManager.TOTAL_WALLET_ID) {
            selection = Contract.Transaction.WALLET_COUNT_IN_TOTAL + " = 1"
            selectionArgs = null
        } else {
            selection = Contract.Transaction.WALLET_ID + " = ?"
            selectionArgs = arrayOf(currentWallet.toString())
        }
        selection += " AND " + Contract.Transaction.CONFIRMED + " = '1' AND " + Contract.Transaction.COUNT_IN_TOTAL + " = '1'"
        selection += " AND " + Contract.Transaction.REPORT_FILTER
        selection += " AND DATETIME(" + Contract.Transaction.DATE + ") <= DATETIME('now', 'localtime')"
        selection += " AND " + Contract.Transaction.DIRECTION + " = " + if (mIncomes) Contract.Direction.INCOME else Contract.Direction.EXPENSE
        if (mStartDate != null) {
            selection += " AND DATETIME(" + Contract.Transaction.DATE + ") >= DATETIME('" + DateUtils.getSQLDateTimeString(mStartDate) + "')"
        }
        if (mEndDate != null) {
            selection += " AND DATETIME(" + Contract.Transaction.DATE + ") <= DATETIME('" + DateUtils.getSQLDateTimeString(mEndDate) + "')"
        }
        val sortOrder = Contract.Transaction.CATEGORY_ID
        val cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
        cursor?.use {
            if (it.moveToFirst()) {
                do {
                    val ownId = it.getLong(it.getColumnIndex(Contract.Transaction.CATEGORY_ID))
                    val categoryId: Long
                    val childId: Long
                    if (it.isNull(it.getColumnIndex(Contract.Transaction.CATEGORY_PARENT_ID))) {
                        categoryId = ownId
                        childId = 0L
                    } else {
                        categoryId = it.getLong(it.getColumnIndex(Contract.Transaction.CATEGORY_PARENT_ID))
                        childId = ownId
                    }
                    val money = it.getLong(it.getColumnIndex(Contract.Transaction.MONEY))
                    val iso = it.getString(it.getColumnIndex(Contract.Transaction.WALLET_CURRENCY)) ?: ""
                    if (categoryCache.containsKey(categoryId)) {
                        val child = if (childId != 0L) childCategoryCache[childId] else null
                        if (child != null) {
                            addChildMoney(childMoneyMap, categoryId, childId, child, iso, money)
                        } else {
                            addDirectMoney(directMoneyMap, categoryId, iso, money)
                        }
                    }
                    val existingCategoryMoney = categoryMoneyMap[categoryId]
                    if (existingCategoryMoney != null) {
                        existingCategoryMoney.money.addMoney(iso, money)
                    } else {
                        val category = categoryCache[categoryId]
                        if (category != null) {
                            val categoryMoney = CategoryMoney(
                                categoryId,
                                category.name,
                                category.icon,
                                Money(iso, money)
                            )
                            categoryMoneyMap[categoryId] = categoryMoney
                        }
                    }
                } while (it.moveToNext())
            }
        }
        val categoryMoneyList = ArrayList<CategoryMoney>()
        for (categoryMoney in categoryMoneyMap.values) {
            val money = categoryMoney.money
            totalMoney.addMoney(money)
            for ((currencyIso, currencyAmount) in money.currencyMoneys) {
                val currency = CurrencyManager.getCurrency(currencyIso)
                val pieData = pieDataSets.getOrPut(currency) { PieData() }
                pieData.add(
                    PieSlice(
                        categoryMoney.name,
                        currencyAmount,
                        categoryMoney.icon?.getDrawable(context)
                    )
                )
            }
            attachChildren(
                categoryMoney,
                childMoneyMap[categoryMoney.id],
                directMoneyMap[categoryMoney.id]
            )
            categoryMoneyList.add(categoryMoney)
        }
        val pieDataList = ArrayList<PieData>(pieDataSets.values)
        return PeriodDetailFlowData(totalMoney, pieDataList, categoryMoneyList)
    }

    /**
     * The caller checked that the parent has a row, so a child counted here is also counted in
     * the parent row above it.
     */
    private fun addChildMoney(
        childMoneyMap: MutableMap<Long, MutableMap<Long, CategoryMoney>>,
        parentId: Long,
        childId: Long,
        child: Category,
        iso: String,
        money: Long
    ) {
        val children = childMoneyMap.getOrPut(parentId) { HashMap() }
        val childMoney = children[childId]
        if (childMoney != null) {
            childMoney.money.addMoney(iso, money)
        } else {
            children[childId] = CategoryMoney(childId, child.name, child.icon, Money(iso, money))
        }
    }

    private fun addDirectMoney(
        directMoneyMap: MutableMap<Long, Money>,
        categoryId: Long,
        iso: String,
        money: Long
    ) {
        val direct = directMoneyMap[categoryId]
        if (direct == null) {
            directMoneyMap[categoryId] = Money(iso, money)
        } else {
            direct.addMoney(iso, money)
        }
    }

    /**
     * Sorted by name, because the map has no order of its own and rows that move between two loads
     * of one period read as a defect. Money filed on the category itself leads the list under the
     * category's own name, so the rows always add up to the category.
     */
    private fun attachChildren(
        parent: CategoryMoney,
        children: Map<Long, CategoryMoney>?,
        direct: Money?
    ) {
        if (children == null) {
            return
        }
        if (direct != null) {
            parent.addChild(CategoryMoney(parent.id, parent.name, parent.icon, direct))
        }
        val sorted = ArrayList(children.values)
        sorted.sortWith { left, right ->
            String.CASE_INSENSITIVE_ORDER.compare(left.name ?: "", right.name ?: "")
        }
        for (child in sorted) {
            parent.addChild(child)
        }
    }

    /**
     * Both caches name rows and neither decides what counts. A category hidden from the reports is
     * already out, because REPORT_FILTER drops its transactions before they reach this loader.
     */
    private fun loadChildCategoryCache(): Map<Long, Category> {
        return loadCategories(Contract.Category.PARENT + " IS NOT NULL")
    }

    @SuppressLint("UseSparseArrays")
    private fun loadCategoryCache(): Map<Long, Category> {
        return loadCategories(Contract.Category.PARENT + " IS NULL")
    }

    @SuppressLint("Range", "UseSparseArrays")
    private fun loadCategories(selection: String): Map<Long, Category> {
        val cache = HashMap<Long, Category>()
        val uri = DataContentProvider.CONTENT_CATEGORIES
        val projection = arrayOf(
            Contract.Category.ID,
            Contract.Category.NAME,
            Contract.Category.ICON,
            Contract.Category.TYPE
        )
        val cursor = context.contentResolver.query(uri, projection, selection, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                do {
                    val categoryId = it.getLong(it.getColumnIndex(Contract.Category.ID))
                    val categoryType = Contract.CategoryType.fromValue(
                        it.getInt(it.getColumnIndex(Contract.Category.TYPE))
                    ) ?: Contract.CategoryType.EXPENSE
                    val category = Category(
                        categoryId,
                        it.getString(it.getColumnIndex(Contract.Category.NAME)),
                        IconLoader.parse(it.getString(it.getColumnIndex(Contract.Category.ICON))),
                        categoryType
                    )
                    cache[categoryId] = category
                } while (it.moveToNext())
            }
        }
        return cache
    }
}
