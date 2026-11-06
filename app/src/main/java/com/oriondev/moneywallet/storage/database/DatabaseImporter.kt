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

import android.content.ContentResolver

/**
 * Created by andrea on 25/10/18.
 */
interface DatabaseImporter {

    @Throws(ImportException::class)
    fun importHeader()

    @Throws(ImportException::class)
    fun importCurrencies(contentResolver: ContentResolver)

    @Throws(ImportException::class)
    fun importWallets(contentResolver: ContentResolver)

    @Throws(ImportException::class)
    fun importCategories(contentResolver: ContentResolver)

    @Throws(ImportException::class)
    fun importEvents(contentResolver: ContentResolver)

    @Throws(ImportException::class)
    fun importPlaces(contentResolver: ContentResolver)

    @Throws(ImportException::class)
    fun importPeople(contentResolver: ContentResolver)

    @Throws(ImportException::class)
    fun importEventPeople(contentResolver: ContentResolver)

    @Throws(ImportException::class)
    fun importDebts(contentResolver: ContentResolver)

    @Throws(ImportException::class)
    fun importDebtPeople(contentResolver: ContentResolver)

    @Throws(ImportException::class)
    fun importBudgets(contentResolver: ContentResolver)

    @Throws(ImportException::class)
    fun importBudgetWallets(contentResolver: ContentResolver)

    @Throws(ImportException::class)
    fun importBudgetCategories(contentResolver: ContentResolver)

    @Throws(ImportException::class)
    fun importCategoryRules(contentResolver: ContentResolver)

    @Throws(ImportException::class)
    fun importSavings(contentResolver: ContentResolver)

    @Throws(ImportException::class)
    fun importRecurrentTransactions(contentResolver: ContentResolver)

    @Throws(ImportException::class)
    fun importRecurrentTransfers(contentResolver: ContentResolver)

    @Throws(ImportException::class)
    fun importTransactions(contentResolver: ContentResolver)

    @Throws(ImportException::class)
    fun importTransactionPeople(contentResolver: ContentResolver)

    @Throws(ImportException::class)
    fun importTransactionModels(contentResolver: ContentResolver)

    @Throws(ImportException::class)
    fun importTransfers(contentResolver: ContentResolver)

    @Throws(ImportException::class)
    fun importTransferPeople(contentResolver: ContentResolver)

    @Throws(ImportException::class)
    fun importTransferModels(contentResolver: ContentResolver)

    @Throws(ImportException::class)
    fun importAttachments(contentResolver: ContentResolver)

    @Throws(ImportException::class)
    fun importTransactionAttachments(contentResolver: ContentResolver)

    @Throws(ImportException::class)
    fun importTransferAttachments(contentResolver: ContentResolver)

    @Throws(ImportException::class)
    fun close()
}
