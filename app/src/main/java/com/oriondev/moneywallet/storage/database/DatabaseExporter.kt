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

import android.database.Cursor

/**
 * Created by andrea on 28/10/18.
 */
interface DatabaseExporter {

    @Throws(ExportException::class)
    fun exportHeader()

    @Throws(ExportException::class)
    fun exportCurrencies(cursor: Cursor?)

    @Throws(ExportException::class)
    fun exportWallets(cursor: Cursor?)

    @Throws(ExportException::class)
    fun exportCategories(cursor: Cursor?)

    @Throws(ExportException::class)
    fun exportEvents(cursor: Cursor?)

    @Throws(ExportException::class)
    fun exportPlaces(cursor: Cursor?)

    @Throws(ExportException::class)
    fun exportPeople(cursor: Cursor?)

    @Throws(ExportException::class)
    fun exportEventPeople(cursor: Cursor?)

    @Throws(ExportException::class)
    fun exportDebts(cursor: Cursor?)

    @Throws(ExportException::class)
    fun exportDebtPeople(cursor: Cursor?)

    @Throws(ExportException::class)
    fun exportBudgets(cursor: Cursor?)

    @Throws(ExportException::class)
    fun exportBudgetWallets(cursor: Cursor?)

    @Throws(ExportException::class)
    fun exportBudgetCategories(cursor: Cursor?)

    @Throws(ExportException::class)
    fun exportCategoryRules(cursor: Cursor?)

    @Throws(ExportException::class)
    fun exportSavings(cursor: Cursor?)

    @Throws(ExportException::class)
    fun exportRecurrentTransactions(cursor: Cursor?)

    @Throws(ExportException::class)
    fun exportRecurrentTransfers(cursor: Cursor?)

    @Throws(ExportException::class)
    fun exportTransactions(cursor: Cursor?)

    @Throws(ExportException::class)
    fun exportTransactionPeople(cursor: Cursor?)

    @Throws(ExportException::class)
    fun exportTransactionModels(cursor: Cursor?)

    @Throws(ExportException::class)
    fun exportTransfers(cursor: Cursor?)

    @Throws(ExportException::class)
    fun exportTransferPeople(cursor: Cursor?)

    @Throws(ExportException::class)
    fun exportTransferModels(cursor: Cursor?)

    @Throws(ExportException::class)
    fun exportAttachments(cursor: Cursor?)

    @Throws(ExportException::class)
    fun exportTransactionAttachments(cursor: Cursor?)

    @Throws(ExportException::class)
    fun exportTransferAttachments(cursor: Cursor?)

    @Throws(ExportException::class)
    fun close()
}

