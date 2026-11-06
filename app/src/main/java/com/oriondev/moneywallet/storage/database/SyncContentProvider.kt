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
import android.content.ContentProvider
import android.content.ContentProviderClient
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.UriMatcher
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.oriondev.moneywallet.BuildConfig

/**
 * This content provider exposes the full SQLiteDatabase structure.
 * The goal is to make the full tables available to backup and sync components.
 */
class SyncContentProvider : ContentProvider() {

    companion object {
        private const val AUTHORITY: String = "${BuildConfig.APPLICATION_ID}.storage.sync"

        @JvmField
        val CONTENT_CURRENCIES: Uri = Uri.parse("content://$AUTHORITY/currencies")
        @JvmField
        val CONTENT_WALLETS: Uri = Uri.parse("content://$AUTHORITY/wallets")
        @JvmField
        val CONTENT_CATEGORIES: Uri = Uri.parse("content://$AUTHORITY/categories")
        @JvmField
        val CONTENT_EVENTS: Uri = Uri.parse("content://$AUTHORITY/events")
        @JvmField
        val CONTENT_CATEGORY_RULES: Uri = Uri.parse("content://$AUTHORITY/category_rules")
        @JvmField
        val CONTENT_PLACES: Uri = Uri.parse("content://$AUTHORITY/places")
        @JvmField
        val CONTENT_PEOPLE: Uri = Uri.parse("content://$AUTHORITY/people")
        @JvmField
        val CONTENT_EVENT_PEOPLE: Uri = Uri.parse("content://$AUTHORITY/event_people")
        @JvmField
        val CONTENT_DEBT: Uri = Uri.parse("content://$AUTHORITY/debts")
        @JvmField
        val CONTENT_DEBT_PEOPLE: Uri = Uri.parse("content://$AUTHORITY/debt_people")
        @JvmField
        val CONTENT_BUDGET: Uri = Uri.parse("content://$AUTHORITY/budget")
        @JvmField
        val CONTENT_BUDGET_WALLET: Uri = Uri.parse("content://$AUTHORITY/budget_wallets")
        @JvmField
        val CONTENT_BUDGET_CATEGORY: Uri = Uri.parse("content://$AUTHORITY/budget_categories")
        @JvmField
        val CONTENT_SAVING: Uri = Uri.parse("content://$AUTHORITY/savings")
        @JvmField
        val CONTENT_RECURRENT_TRANSACTION: Uri = Uri.parse("content://$AUTHORITY/recurrent_transactions")
        @JvmField
        val CONTENT_RECURRENT_TRANSFER: Uri = Uri.parse("content://$AUTHORITY/recurrent_transfers")
        @JvmField
        val CONTENT_TRANSACTION: Uri = Uri.parse("content://$AUTHORITY/transactions")
        @JvmField
        val CONTENT_TRANSACTION_PEOPLE: Uri = Uri.parse("content://$AUTHORITY/transaction_people")
        @JvmField
        val CONTENT_TRANSACTION_MODEL: Uri = Uri.parse("content://$AUTHORITY/transaction_model")
        @JvmField
        val CONTENT_TRANSFER: Uri = Uri.parse("content://$AUTHORITY/transfers")
        @JvmField
        val CONTENT_TRANSFER_PEOPLE: Uri = Uri.parse("content://$AUTHORITY/transfer_people")
        @JvmField
        val CONTENT_TRANSFER_MODEL: Uri = Uri.parse("content://$AUTHORITY/transfer_model")
        @JvmField
        val CONTENT_ATTACHMENT: Uri = Uri.parse("content://$AUTHORITY/attachments")
        @JvmField
        val CONTENT_TRANSACTION_ATTACHMENT: Uri = Uri.parse("content://$AUTHORITY/transaction_attachments")
        @JvmField
        val CONTENT_TRANSFER_ATTACHMENT: Uri = Uri.parse("content://$AUTHORITY/transfer_attachments")

        private const val TABLE_CURRENCIES = 1
        private const val TABLE_WALLETS = 2
        private const val TABLE_CATEGORIES = 3
        private const val TABLE_EVENTS = 4
        private const val TABLE_PLACES = 5
        private const val TABLE_PEOPLE = 6
        private const val TABLE_EVENT_PEOPLE = 7
        private const val TABLE_DEBTS = 8
        private const val TABLE_DEBT_PEOPLE = 9
        private const val TABLE_BUDGETS = 10
        private const val TABLE_BUDGET_WALLETS = 11
        private const val TABLE_BUDGET_CATEGORIES = 24
        private const val TABLE_SAVINGS = 12
        private const val TABLE_RECURRENT_TRANSACTIONS = 13
        private const val TABLE_RECURRENT_TRANSFERS = 14
        private const val TABLE_TRANSACTIONS = 15
        private const val TABLE_TRANSACTION_PEOPLE = 16
        private const val TABLE_TRANSACTION_MODELS = 17
        private const val TABLE_TRANSFERS = 18
        private const val TABLE_TRANSFER_PEOPLE = 19
        private const val TABLE_TRANSFER_MODELS = 20
        private const val TABLE_ATTACHMENTS = 21
        private const val TABLE_TRANSACTION_ATTACHMENTS = 22
        private const val TABLE_TRANSFER_ATTACHMENTS = 23
        private const val TABLE_CATEGORY_RULES = 25

        private val mUriMatcher: UriMatcher = createUriMatcher()

        private fun createUriMatcher(): UriMatcher {
            val matcher = UriMatcher(UriMatcher.NO_MATCH)
            matcher.addURI(AUTHORITY, "currencies", TABLE_CURRENCIES)
            matcher.addURI(AUTHORITY, "wallets", TABLE_WALLETS)
            matcher.addURI(AUTHORITY, "categories", TABLE_CATEGORIES)
            matcher.addURI(AUTHORITY, "events", TABLE_EVENTS)
            matcher.addURI(AUTHORITY, "category_rules", TABLE_CATEGORY_RULES)
            matcher.addURI(AUTHORITY, "places", TABLE_PLACES)
            matcher.addURI(AUTHORITY, "people", TABLE_PEOPLE)
            matcher.addURI(AUTHORITY, "event_people", TABLE_EVENT_PEOPLE)
            matcher.addURI(AUTHORITY, "debts", TABLE_DEBTS)
            matcher.addURI(AUTHORITY, "debt_people", TABLE_DEBT_PEOPLE)
            matcher.addURI(AUTHORITY, "budget", TABLE_BUDGETS)
            matcher.addURI(AUTHORITY, "budget_wallets", TABLE_BUDGET_WALLETS)
            matcher.addURI(AUTHORITY, "budget_categories", TABLE_BUDGET_CATEGORIES)
            matcher.addURI(AUTHORITY, "savings", TABLE_SAVINGS)
            matcher.addURI(AUTHORITY, "recurrent_transactions", TABLE_RECURRENT_TRANSACTIONS)
            matcher.addURI(AUTHORITY, "recurrent_transfers", TABLE_RECURRENT_TRANSFERS)
            matcher.addURI(AUTHORITY, "transactions", TABLE_TRANSACTIONS)
            matcher.addURI(AUTHORITY, "transaction_people", TABLE_TRANSACTION_PEOPLE)
            matcher.addURI(AUTHORITY, "transaction_model", TABLE_TRANSACTION_MODELS)
            matcher.addURI(AUTHORITY, "transfers", TABLE_TRANSFERS)
            matcher.addURI(AUTHORITY, "transfer_people", TABLE_TRANSFER_PEOPLE)
            matcher.addURI(AUTHORITY, "transfer_model", TABLE_TRANSFER_MODELS)
            matcher.addURI(AUTHORITY, "attachments", TABLE_ATTACHMENTS)
            matcher.addURI(AUTHORITY, "transaction_attachments", TABLE_TRANSACTION_ATTACHMENTS)
            matcher.addURI(AUTHORITY, "transfer_attachments", TABLE_TRANSFER_ATTACHMENTS)
            return matcher
        }

        @JvmStatic
        @SuppressLint("Recycle")
        fun notifyDatabaseIsChanged(context: Context) {
            val contentResolver: ContentResolver = context.contentResolver
            val client: ContentProviderClient? = contentResolver.acquireContentProviderClient(AUTHORITY)
            if (client != null) {
                val contentProvider = client.localContentProvider
                if (contentProvider is SyncContentProvider) {
                    SQLDatabase.resetShared(context)
                }
                client.close()
            }
        }
    }

    private fun db(): SQLDatabase {
        val ctx = context ?: throw IllegalStateException("Context cannot be null")
        return SQLDatabase.getShared(ctx)
    }

    override fun onCreate(): Boolean = true

    private fun getTable(uri: Uri): String? {
        return when (mUriMatcher.match(uri)) {
            TABLE_CURRENCIES -> Schema.Currency.TABLE
            TABLE_WALLETS -> Schema.Wallet.TABLE
            TABLE_CATEGORIES -> Schema.Category.TABLE
            TABLE_EVENTS -> Schema.Event.TABLE
            TABLE_CATEGORY_RULES -> Schema.CategoryRule.TABLE
            TABLE_PLACES -> Schema.Place.TABLE
            TABLE_PEOPLE -> Schema.Person.TABLE
            TABLE_EVENT_PEOPLE -> Schema.EventPeople.TABLE
            TABLE_DEBTS -> Schema.Debt.TABLE
            TABLE_DEBT_PEOPLE -> Schema.DebtPeople.TABLE
            TABLE_BUDGETS -> Schema.Budget.TABLE
            TABLE_BUDGET_WALLETS -> Schema.BudgetWallet.TABLE
            TABLE_BUDGET_CATEGORIES -> Schema.BudgetCategory.TABLE
            TABLE_SAVINGS -> Schema.Saving.TABLE
            TABLE_RECURRENT_TRANSACTIONS -> Schema.RecurrentTransaction.TABLE
            TABLE_RECURRENT_TRANSFERS -> Schema.RecurrentTransfer.TABLE
            TABLE_TRANSACTIONS -> Schema.Transaction.TABLE
            TABLE_TRANSACTION_PEOPLE -> Schema.TransactionPeople.TABLE
            TABLE_TRANSACTION_MODELS -> Schema.TransactionModel.TABLE
            TABLE_TRANSFERS -> Schema.Transfer.TABLE
            TABLE_TRANSFER_PEOPLE -> Schema.TransferPeople.TABLE
            TABLE_TRANSFER_MODELS -> Schema.TransferModel.TABLE
            TABLE_ATTACHMENTS -> Schema.Attachment.TABLE
            TABLE_TRANSACTION_ATTACHMENTS -> Schema.TransactionAttachment.TABLE
            TABLE_TRANSFER_ATTACHMENTS -> Schema.TransferAttachment.TABLE
            else -> null
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val table = getTable(uri)
        return if (table != null) {
            db().readableDatabase.query(table, projection, selection, selectionArgs, null, null, sortOrder)
        } else {
            null
        }
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val table = getTable(uri)
        return if (table != null) {
            val id = db().writableDatabase.insertWithOnConflict(table, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            ContentUris.withAppendedId(uri, id)
        } else {
            null
        }
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        val table = getTable(uri)
        return if (table != null) {
            db().writableDatabase.delete(table, selection, selectionArgs)
        } else {
            0
        }
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        val table = getTable(uri)
        return if (table != null) {
            db().writableDatabase.update(table, values, selection, selectionArgs)
        } else {
            0
        }
    }
}
