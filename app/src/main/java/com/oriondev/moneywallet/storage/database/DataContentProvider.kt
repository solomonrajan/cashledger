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
import android.net.Uri
import com.oriondev.moneywallet.BuildConfig
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.widget.WalletWidgetProvider
import java.util.LinkedHashSet
import java.util.concurrent.Callable

/**
 * Created by andrea on 17/01/18.
 */
class DataContentProvider : ContentProvider() {

    companion object {
        internal const val AUTHORITY: String = "${BuildConfig.APPLICATION_ID}.storage.data"

        @JvmField
        val CONTENT_CURRENCIES: Uri = Uri.parse("content://$AUTHORITY/currencies")
        @JvmField
        val CONTENT_WALLETS: Uri = Uri.parse("content://$AUTHORITY/wallets")
        @JvmField
        val CONTENT_TRANSACTIONS: Uri = Uri.parse("content://$AUTHORITY/transactions")
        @JvmField
        val CONTENT_TRANSFERS: Uri = Uri.parse("content://$AUTHORITY/transfers")
        @JvmField
        val CONTENT_CATEGORIES: Uri = Uri.parse("content://$AUTHORITY/categories")
        @JvmField
        val CONTENT_DEBTS: Uri = Uri.parse("content://$AUTHORITY/debts")
        @JvmField
        val CONTENT_BUDGETS: Uri = Uri.parse("content://$AUTHORITY/budgets")
        @JvmField
        val CONTENT_SAVINGS: Uri = Uri.parse("content://$AUTHORITY/savings")
        @JvmField
        val CONTENT_EVENTS: Uri = Uri.parse("content://$AUTHORITY/events")
        @JvmField
        val CONTENT_RECURRENT_TRANSACTIONS: Uri = Uri.parse("content://$AUTHORITY/recurrences/transactions")
        @JvmField
        val CONTENT_RECURRENT_TRANSFERS: Uri = Uri.parse("content://$AUTHORITY/recurrences/transfers")
        @JvmField
        val CONTENT_TRANSACTION_MODELS: Uri = Uri.parse("content://$AUTHORITY/models/transactions")
        @JvmField
        val CONTENT_TRANSFER_MODELS: Uri = Uri.parse("content://$AUTHORITY/models/transfers")
        @JvmField
        val CONTENT_PLACES: Uri = Uri.parse("content://$AUTHORITY/places")
        @JvmField
        val CONTENT_PEOPLE: Uri = Uri.parse("content://$AUTHORITY/people")
        @JvmField
        val CONTENT_CATEGORY_RULES: Uri = Uri.parse("content://$AUTHORITY/category_rules")
        @JvmField
        val CONTENT_ATTACHMENTS: Uri = Uri.parse("content://$AUTHORITY/attachments")

        /**
         * Every uri above sits under this one. An observer registered here with descendants included
         * is told about every change this provider announces, whichever of those uris the write named,
         * because the framework collects an observer sitting above a uri for a notification on
         * anything below it.
         *
         * It is what a cursor this provider hands out is registered on, and what the widget watches.
         */
        @JvmField
        val CONTENT_ALL: Uri = Uri.parse("content://$AUTHORITY")

        private const val CURRENCY_LIST = 1
        private const val WALLET_LIST = 2
        private const val TRANSACTION_LIST = 3
        private const val TRANSFER_LIST = 4
        private const val CATEGORY_LIST = 5
        private const val DEBT_LIST = 6
        private const val BUDGET_LIST = 7
        private const val SAVING_LIST = 8
        private const val EVENT_LIST = 9
        private const val RECURRENT_TRANSACTION_LIST = 10
        private const val RECURRENT_TRANSFER_LIST = 11
        private const val TRANSACTION_MODEL_LIST = 12
        private const val TRANSFER_MODEL_LIST = 13
        private const val PLACE_LIST = 14
        private const val PERSON_LIST = 15
        private const val ATTACHMENT_LIST = 16
        private const val ATTACHMENT_ITEM = 17

        private const val CURRENCY_ITEM = 18
        private const val WALLET_ITEM = 19
        private const val TRANSACTION_ITEM = 20
        private const val TRANSFER_ITEM = 21
        private const val CATEGORY_ITEM = 22
        private const val DEBT_ITEM = 23
        private const val BUDGET_ITEM = 24
        private const val SAVING_ITEM = 25
        private const val EVENT_ITEM = 26
        private const val RECURRENT_TRANSACTION_ITEM = 27
        private const val RECURRENT_TRANSFER_ITEM = 28
        private const val TRANSACTION_MODEL_ITEM = 29
        private const val TRANSFER_MODEL_ITEM = 30
        private const val PLACE_ITEM = 31
        private const val PERSON_ITEM = 32

        private const val TRANSACTION_ATTACHMENTS = 33
        private const val TRANSACTION_PEOPLE = 34
        private const val TRANSFER_ATTACHMENTS = 35
        private const val TRANSFER_PEOPLE = 36
        private const val DEBT_PEOPLE = 37
        private const val BUDGET_WALLETS = 38
        private const val BUDGET_CATEGORIES = 46

        private const val CATEGORY_TRANSACTION_LIST = 39
        private const val DEBT_TRANSACTION_LIST = 40
        private const val BUDGET_TRANSACTION_LIST = 41
        private const val SAVING_TRANSACTION_LIST = 42
        private const val EVENT_TRANSACTION_LIST = 43
        private const val PLACE_TRANSACTION_LIST = 44
        private const val PERSON_TRANSACTION_LIST = 45

        private const val CATEGORY_RULE_LIST = 47
        private const val CATEGORY_RULE_ITEM = 48
        private const val CATEGORY_RULE_MATCH = 49

        private val mUriMatcher: UriMatcher = createUriMatcher()

        private fun createUriMatcher(): UriMatcher {
            val matcher = UriMatcher(UriMatcher.NO_MATCH)
            matcher.addURI(AUTHORITY, "currencies", CURRENCY_LIST)
            matcher.addURI(AUTHORITY, "currencies/*", CURRENCY_ITEM)
            matcher.addURI(AUTHORITY, "wallets", WALLET_LIST)
            matcher.addURI(AUTHORITY, "wallets/#", WALLET_ITEM)
            matcher.addURI(AUTHORITY, "transactions", TRANSACTION_LIST)
            matcher.addURI(AUTHORITY, "transactions/#", TRANSACTION_ITEM)
            matcher.addURI(AUTHORITY, "transactions/#/attachments", TRANSACTION_ATTACHMENTS)
            matcher.addURI(AUTHORITY, "transactions/#/people", TRANSACTION_PEOPLE)
            matcher.addURI(AUTHORITY, "transfers", TRANSFER_LIST)
            matcher.addURI(AUTHORITY, "transfers/#", TRANSFER_ITEM)
            matcher.addURI(AUTHORITY, "transfers/#/attachments", TRANSFER_ATTACHMENTS)
            matcher.addURI(AUTHORITY, "transfers/#/people", TRANSFER_PEOPLE)
            matcher.addURI(AUTHORITY, "categories", CATEGORY_LIST)
            matcher.addURI(AUTHORITY, "categories/#", CATEGORY_ITEM)
            matcher.addURI(AUTHORITY, "categories/#/transactions", CATEGORY_TRANSACTION_LIST)
            matcher.addURI(AUTHORITY, "debts", DEBT_LIST)
            matcher.addURI(AUTHORITY, "debts/#", DEBT_ITEM)
            matcher.addURI(AUTHORITY, "debts/#/people", DEBT_PEOPLE)
            matcher.addURI(AUTHORITY, "debts/#/transactions", DEBT_TRANSACTION_LIST)
            matcher.addURI(AUTHORITY, "budgets", BUDGET_LIST)
            matcher.addURI(AUTHORITY, "budgets/#", BUDGET_ITEM)
            matcher.addURI(AUTHORITY, "budgets/#/wallets", BUDGET_WALLETS)
            matcher.addURI(AUTHORITY, "budgets/#/categories", BUDGET_CATEGORIES)
            matcher.addURI(AUTHORITY, "budgets/#/transactions", BUDGET_TRANSACTION_LIST)
            matcher.addURI(AUTHORITY, "savings", SAVING_LIST)
            matcher.addURI(AUTHORITY, "savings/#", SAVING_ITEM)
            matcher.addURI(AUTHORITY, "savings/#/transactions", SAVING_TRANSACTION_LIST)
            matcher.addURI(AUTHORITY, "events", EVENT_LIST)
            matcher.addURI(AUTHORITY, "events/#", EVENT_ITEM)
            matcher.addURI(AUTHORITY, "events/#/transactions", EVENT_TRANSACTION_LIST)
            matcher.addURI(AUTHORITY, "recurrences/transactions", RECURRENT_TRANSACTION_LIST)
            matcher.addURI(AUTHORITY, "recurrences/transactions/#", RECURRENT_TRANSACTION_ITEM)
            matcher.addURI(AUTHORITY, "recurrences/transfers", RECURRENT_TRANSFER_LIST)
            matcher.addURI(AUTHORITY, "recurrences/transfers/#", RECURRENT_TRANSFER_ITEM)
            matcher.addURI(AUTHORITY, "models/transactions", TRANSACTION_MODEL_LIST)
            matcher.addURI(AUTHORITY, "models/transactions/#", TRANSACTION_MODEL_ITEM)
            matcher.addURI(AUTHORITY, "models/transfers", TRANSFER_MODEL_LIST)
            matcher.addURI(AUTHORITY, "models/transfers/#", TRANSFER_MODEL_ITEM)
            matcher.addURI(AUTHORITY, "category_rules", CATEGORY_RULE_LIST)
            matcher.addURI(AUTHORITY, "category_rules/#", CATEGORY_RULE_ITEM)
            matcher.addURI(AUTHORITY, "category_rules/match/*", CATEGORY_RULE_MATCH)
            matcher.addURI(AUTHORITY, "places", PLACE_LIST)
            matcher.addURI(AUTHORITY, "places/#", PLACE_ITEM)
            matcher.addURI(AUTHORITY, "places/#/transactions", PLACE_TRANSACTION_LIST)
            matcher.addURI(AUTHORITY, "people", PERSON_LIST)
            matcher.addURI(AUTHORITY, "people/#", PERSON_ITEM)
            matcher.addURI(AUTHORITY, "people/#/transactions", PERSON_TRANSACTION_LIST)
            matcher.addURI(AUTHORITY, "attachments", ATTACHMENT_LIST)
            matcher.addURI(AUTHORITY, "attachments/#", ATTACHMENT_ITEM)
            return matcher
        }

        private val sDeferredNotifications: ThreadLocal<MutableSet<Uri>?> = ThreadLocal()

        @JvmStatic
        fun isInsideOneTransaction(): Boolean {
            return sDeferredNotifications.get() != null
        }

        @JvmStatic
        @Throws(Exception::class)
        fun <T> runInOneTransaction(context: Context, body: Callable<T>): T {
            if (sDeferredNotifications.get() != null) {
                return body.call()
            }
            val deferred = LinkedHashSet<Uri>()
            sDeferredNotifications.set(deferred)
            val result: T
            try {
                result = SQLDatabase.inSharedTransaction(context) {
                    try {
                        body.call()
                    } catch (e: RuntimeException) {
                        throw e
                    } catch (e: Exception) {
                        throw CheckedFailure(e)
                    }
                }
            } catch (e: CheckedFailure) {
                val cause = e.cause
                if (cause is Exception) {
                    throw cause
                } else {
                    throw e
                }
            } finally {
                sDeferredNotifications.remove()
            }
            val contentResolver = context.contentResolver
            if (deferred.isNotEmpty()) {
                PreferenceManager.setLastTimeDataIsChanged(System.currentTimeMillis())
                for (uri in deferred) {
                    contentResolver.notifyChange(uri, null)
                }
            }
            return result
        }

        @JvmStatic
        fun replaceDatabaseFile(context: Context, swap: Runnable) {
            SQLDatabase.resetShared(context, swap)
        }

        @JvmStatic
        fun openStagingDatabase(context: Context, name: String) {
            SQLDatabase.openStaging(context, name)
        }

        @JvmStatic
        fun closeStagingDatabase() {
            SQLDatabase.closeStaging()
        }

        @JvmStatic
        @SuppressLint("Recycle")
        fun notifyDatabaseIsChanged(context: Context) {
            val contentResolver: ContentResolver = context.contentResolver
            val client: ContentProviderClient? = contentResolver.acquireContentProviderClient(AUTHORITY)
            if (client != null) {
                val contentProvider = client.localContentProvider
                if (contentProvider is DataContentProvider) {
                    SQLDatabase.resetShared(context)
                    PreferenceManager.setCurrentWallet(context, PreferenceManager.NO_CURRENT_WALLET)
                    WalletWidgetProvider.forgetConfiguredWallets(context)
                }
                client.close()
            }
        }
    }

    private class CheckedFailure(cause: Exception) : RuntimeException(cause)

    private fun db(): SQLDatabase {
        val ctx = context ?: throw IllegalStateException("Context cannot be null")
        return SQLDatabase.getShared(ctx)
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val proj = projection?.let { Array(it.size) { i -> it[i] } }
        val selArgs = selectionArgs?.let { Array(it.size) { i -> it[i] } }
        var cursor: Cursor? = null
        when (mUriMatcher.match(uri)) {
            CURRENCY_LIST -> {
                cursor = MultiUriCursorWrapper(db().getCurrencies(proj, selection, selArgs, sortOrder))
            }
            CURRENCY_ITEM -> {
                cursor = MultiUriCursorWrapper(db().getCurrency(uri.lastPathSegment, proj))
            }
            WALLET_LIST -> {
                cursor = MultiUriCursorWrapper(db().getWallets(proj, selection, selArgs, sortOrder))
            }
            WALLET_ITEM -> {
                cursor = MultiUriCursorWrapper(db().getWallet(ContentUris.parseId(uri), proj))
            }
            TRANSACTION_LIST -> {
                cursor = MultiUriCursorWrapper(db().getTransactions(proj, selection, selArgs, sortOrder))
            }
            TRANSACTION_ITEM -> {
                cursor = MultiUriCursorWrapper(db().getTransaction(ContentUris.parseId(uri), proj))
            }
            TRANSACTION_ATTACHMENTS -> {
                cursor = MultiUriCursorWrapper(db().getTransactionAttachments(parseIdAtIndex(uri, 1), proj, selection, selArgs, sortOrder))
            }
            TRANSACTION_PEOPLE -> {
                cursor = MultiUriCursorWrapper(db().getTransactionPeople(parseIdAtIndex(uri, 1), proj, selection, selArgs, sortOrder))
            }
            TRANSFER_LIST -> {
                cursor = MultiUriCursorWrapper(db().getTransfers(proj, selection, selArgs, sortOrder))
            }
            TRANSFER_ITEM -> {
                cursor = MultiUriCursorWrapper(db().getTransfer(ContentUris.parseId(uri), proj))
            }
            TRANSFER_ATTACHMENTS -> {
                cursor = MultiUriCursorWrapper(db().getTransferAttachments(parseIdAtIndex(uri, 1), proj, selection, selArgs, sortOrder))
            }
            TRANSFER_PEOPLE -> {
                cursor = MultiUriCursorWrapper(db().getTransferPeople(parseIdAtIndex(uri, 1), proj, selection, selArgs, sortOrder))
            }
            CATEGORY_LIST -> {
                cursor = MultiUriCursorWrapper(db().getCategories(proj, selection, selArgs, sortOrder))
            }
            CATEGORY_ITEM -> {
                cursor = MultiUriCursorWrapper(db().getCategory(ContentUris.parseId(uri), proj))
            }
            CATEGORY_TRANSACTION_LIST -> {
                cursor = MultiUriCursorWrapper(db().getCategoryTransactions(parseIdAtIndex(uri, 1), proj, selection, selArgs, sortOrder))
            }
            DEBT_LIST -> {
                cursor = MultiUriCursorWrapper(db().getDebts(proj, selection, selArgs, sortOrder))
            }
            DEBT_ITEM -> {
                cursor = MultiUriCursorWrapper(db().getDebt(ContentUris.parseId(uri), proj))
            }
            DEBT_PEOPLE -> {
                cursor = MultiUriCursorWrapper(db().getDebtPeople(parseIdAtIndex(uri, 1), proj, selection, selArgs, sortOrder))
            }
            DEBT_TRANSACTION_LIST -> {
                cursor = MultiUriCursorWrapper(db().getDebtTransactions(parseIdAtIndex(uri, 1), proj, selection, selArgs, sortOrder))
            }
            BUDGET_LIST -> {
                cursor = MultiUriCursorWrapper(db().getBudgets(proj, selection, selArgs, sortOrder))
            }
            BUDGET_ITEM -> {
                cursor = MultiUriCursorWrapper(db().getBudget(ContentUris.parseId(uri), proj))
            }
            BUDGET_WALLETS -> {
                cursor = MultiUriCursorWrapper(db().getBudgetWallets(parseIdAtIndex(uri, 1), proj, selection, selArgs, sortOrder))
            }
            BUDGET_CATEGORIES -> {
                cursor = MultiUriCursorWrapper(db().getBudgetCategories(parseIdAtIndex(uri, 1), proj, selection, selArgs, sortOrder))
            }
            BUDGET_TRANSACTION_LIST -> {
                cursor = MultiUriCursorWrapper(db().getBudgetTransactions(parseIdAtIndex(uri, 1), proj, selection, selArgs, sortOrder))
            }
            SAVING_LIST -> {
                cursor = MultiUriCursorWrapper(db().getSavings(proj, selection, selArgs, sortOrder))
            }
            SAVING_ITEM -> {
                cursor = MultiUriCursorWrapper(db().getSaving(ContentUris.parseId(uri), proj))
            }
            SAVING_TRANSACTION_LIST -> {
                cursor = MultiUriCursorWrapper(db().getSavingTransactions(parseIdAtIndex(uri, 1), proj, selection, selArgs, sortOrder))
            }
            EVENT_LIST -> {
                cursor = MultiUriCursorWrapper(db().getEvents(proj, selection, selArgs, sortOrder))
            }
            EVENT_ITEM -> {
                cursor = MultiUriCursorWrapper(db().getEvent(ContentUris.parseId(uri), proj))
            }
            EVENT_TRANSACTION_LIST -> {
                cursor = MultiUriCursorWrapper(db().getEventTransactions(parseIdAtIndex(uri, 1), proj, selection, selArgs, sortOrder))
            }
            RECURRENT_TRANSACTION_LIST -> {
                cursor = MultiUriCursorWrapper(db().getRecurrentTransactions(proj, selection, selArgs, sortOrder))
            }
            RECURRENT_TRANSACTION_ITEM -> {
                cursor = MultiUriCursorWrapper(db().getRecurrentTransaction(ContentUris.parseId(uri), proj))
            }
            RECURRENT_TRANSFER_LIST -> {
                cursor = MultiUriCursorWrapper(db().getRecurrentTransfers(proj, selection, selArgs, sortOrder))
            }
            RECURRENT_TRANSFER_ITEM -> {
                cursor = MultiUriCursorWrapper(db().getRecurrentTransfer(ContentUris.parseId(uri), proj))
            }
            TRANSACTION_MODEL_LIST -> {
                cursor = MultiUriCursorWrapper(db().getTransactionModels(proj, selection, selArgs, sortOrder))
            }
            TRANSACTION_MODEL_ITEM -> {
                cursor = MultiUriCursorWrapper(db().getTransactionModel(ContentUris.parseId(uri), proj))
            }
            TRANSFER_MODEL_LIST -> {
                cursor = MultiUriCursorWrapper(db().getTransferModels(proj, selection, selArgs, sortOrder))
            }
            TRANSFER_MODEL_ITEM -> {
                cursor = MultiUriCursorWrapper(db().getTransferModel(ContentUris.parseId(uri), proj))
            }
            CATEGORY_RULE_LIST -> {
                cursor = MultiUriCursorWrapper(db().getCategoryRules(proj, selection, selArgs, sortOrder))
            }
            CATEGORY_RULE_ITEM -> {
                cursor = MultiUriCursorWrapper(db().getCategoryRule(ContentUris.parseId(uri), proj))
            }
            CATEGORY_RULE_MATCH -> {
                cursor = MultiUriCursorWrapper(db().getCategoryRuleMatch(uri.lastPathSegment))
            }
            PLACE_LIST -> {
                cursor = MultiUriCursorWrapper(db().getPlaces(proj, selection, selArgs, sortOrder))
            }
            PLACE_ITEM -> {
                cursor = MultiUriCursorWrapper(db().getPlace(ContentUris.parseId(uri), proj))
            }
            PLACE_TRANSACTION_LIST -> {
                cursor = MultiUriCursorWrapper(db().getPlaceTransactions(parseIdAtIndex(uri, 1), proj, selection, selArgs, sortOrder))
            }
            PERSON_LIST -> {
                cursor = MultiUriCursorWrapper(db().getPeople(proj, selection, selArgs, sortOrder))
            }
            PERSON_ITEM -> {
                cursor = MultiUriCursorWrapper(db().getPerson(ContentUris.parseId(uri), proj))
            }
            PERSON_TRANSACTION_LIST -> {
                cursor = MultiUriCursorWrapper(db().getPeopleTransactions(parseIdAtIndex(uri, 1), proj, selection, selArgs, sortOrder))
            }
            ATTACHMENT_LIST -> {
                cursor = MultiUriCursorWrapper(db().getAttachments(proj, selection, selArgs, sortOrder))
            }
            ATTACHMENT_ITEM -> {
                cursor = MultiUriCursorWrapper(db().getAttachment(ContentUris.parseId(uri), proj))
            }
        }
        cursor?.let {
            val contentResolver = context?.contentResolver
            if (contentResolver != null) {
                it.setNotificationUri(contentResolver, CONTENT_ALL)
            }
        }
        return cursor
    }

    override fun getType(uri: Uri): String? {
        return when (mUriMatcher.match(uri)) {
            CURRENCY_LIST -> "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.currency"
            CURRENCY_ITEM -> "vnd.android.cursor.item/vnd.com.oriondev.moneywallet.storage.currency"
            WALLET_LIST -> "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.wallet"
            WALLET_ITEM -> "vnd.android.cursor.item/vnd.com.oriondev.moneywallet.storage.wallet"
            TRANSACTION_LIST -> "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.transaction"
            TRANSACTION_ITEM -> "vnd.android.cursor.item/vnd.com.oriondev.moneywallet.storage.transaction"
            TRANSACTION_ATTACHMENTS -> "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.attachments"
            TRANSACTION_PEOPLE -> "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.person"
            TRANSFER_LIST -> "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.transfer"
            TRANSFER_ITEM -> "vnd.android.cursor.item/vnd.com.oriondev.moneywallet.storage.transfer"
            TRANSFER_ATTACHMENTS -> "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.attachments"
            TRANSFER_PEOPLE -> "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.person"
            CATEGORY_LIST -> "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.category"
            CATEGORY_ITEM -> "vnd.android.cursor.item/vnd.com.oriondev.moneywallet.storage.category"
            CATEGORY_TRANSACTION_LIST -> "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.transaction"
            DEBT_LIST -> "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.debt"
            DEBT_ITEM -> "vnd.android.cursor.item/vnd.com.oriondev.moneywallet.storage.debt"
            DEBT_PEOPLE -> "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.person"
            DEBT_TRANSACTION_LIST -> "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.transaction"
            BUDGET_LIST -> "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.budget"
            BUDGET_ITEM -> "vnd.android.cursor.item/vnd.com.oriondev.moneywallet.storage.budget"
            BUDGET_WALLETS -> "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.wallet"
            BUDGET_CATEGORIES -> "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.category"
            BUDGET_TRANSACTION_LIST -> "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.transaction"
            SAVING_LIST -> "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.saving"
            SAVING_ITEM -> "vnd.android.cursor.item/vnd.com.oriondev.moneywallet.storage.saving"
            SAVING_TRANSACTION_LIST -> "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.transaction"
            EVENT_LIST -> "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.event"
            EVENT_ITEM -> "vnd.android.cursor.item/vnd.com.oriondev.moneywallet.storage.event"
            EVENT_TRANSACTION_LIST -> "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.transaction"
            RECURRENT_TRANSACTION_LIST -> "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.recurrence.transaction"
            RECURRENT_TRANSACTION_ITEM -> "vnd.android.cursor.item/vnd.com.oriondev.moneywallet.storage.recurrence.transaction"
            RECURRENT_TRANSFER_LIST -> "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.recurrence.transfer"
            RECURRENT_TRANSFER_ITEM -> "vnd.android.cursor.item/vnd.com.oriondev.moneywallet.storage.recurrence.transfer"
            TRANSACTION_MODEL_LIST -> "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.model.transaction"
            TRANSACTION_MODEL_ITEM -> "vnd.android.cursor.item/vnd.com.oriondev.moneywallet.storage.model.transaction"
            TRANSFER_MODEL_LIST -> "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.model.transfer"
            TRANSFER_MODEL_ITEM -> "vnd.android.cursor.item/vnd.com.oriondev.moneywallet.storage.model.transfer"
            CATEGORY_RULE_LIST, CATEGORY_RULE_MATCH -> "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.category_rule"
            CATEGORY_RULE_ITEM -> "vnd.android.cursor.item/vnd.com.oriondev.moneywallet.storage.category_rule"
            PLACE_LIST -> "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.place"
            PLACE_ITEM -> "vnd.android.cursor.item/vnd.com.oriondev.moneywallet.storage.place"
            PLACE_TRANSACTION_LIST -> "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.transaction"
            PERSON_LIST -> "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.person"
            PERSON_ITEM -> "vnd.android.cursor.item/vnd.com.oriondev.moneywallet.storage.person"
            PERSON_TRANSACTION_LIST -> "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.transaction"
            ATTACHMENT_LIST -> "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.attachment"
            ATTACHMENT_ITEM -> "vnd.android.cursor.item/vnd.com.oriondev.moneywallet.storage.attachment"
            else -> null
        }
    }

    override fun insert(uri: Uri, contentValues: ContentValues?): Uri? {
        val cv = contentValues ?: return null
        val objectUri = SQLDatabase.inSharedTransaction(context) { database ->
            insertInTransaction(database, uri, cv)
        }
        if (objectUri != null) {
            notifyObservers(objectUri, uri)
        }
        return objectUri
    }

    private fun insertInTransaction(database: SQLDatabase, uri: Uri, contentValues: ContentValues): Uri? {
        var currencyIso: String? = null
        var objectId = 0L
        when (mUriMatcher.match(uri)) {
            CURRENCY_LIST -> currencyIso = database.insertCurrency(contentValues)
            WALLET_LIST -> objectId = database.insertWallet(contentValues)
            TRANSACTION_LIST -> objectId = database.insertTransaction(contentValues)
            TRANSFER_LIST -> objectId = database.insertTransfer(contentValues)
            CATEGORY_LIST -> objectId = database.insertCategory(contentValues)
            DEBT_LIST -> objectId = database.insertDebt(contentValues)
            BUDGET_LIST -> objectId = database.insertBudget(contentValues)
            SAVING_LIST -> objectId = database.insertSaving(contentValues)
            EVENT_LIST -> objectId = database.insertEvent(contentValues)
            RECURRENT_TRANSACTION_LIST -> objectId = database.insertRecurrentTransaction(contentValues)
            RECURRENT_TRANSFER_LIST -> objectId = database.insertRecurrentTransfer(contentValues)
            TRANSACTION_MODEL_LIST -> objectId = database.insertTransactionModel(contentValues)
            TRANSFER_MODEL_LIST -> objectId = database.insertTransferModel(contentValues)
            CATEGORY_RULE_LIST -> objectId = database.insertCategoryRule(contentValues)
            PLACE_LIST -> objectId = database.insertPlace(contentValues)
            PERSON_LIST -> objectId = database.insertPerson(contentValues)
            ATTACHMENT_LIST -> objectId = database.insertAttachment(contentValues)
        }
        if (currencyIso != null) {
            return Uri.withAppendedPath(uri, currencyIso)
        }
        if (objectId > 0L) {
            return ContentUris.withAppendedId(uri, objectId)
        }
        return null
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        val notifyUri = arrayOfNulls<Uri>(1)
        val result = SQLDatabase.inSharedTransaction(context) { database ->
            deleteInTransaction(database, uri, notifyUri)
        }
        val ctx = context
        if (result > 0 && ctx != null && mUriMatcher.match(uri) == WALLET_ITEM &&
            ContentUris.parseId(uri) == PreferenceManager.getCurrentWallet()
        ) {
            PreferenceManager.setCurrentWallet(ctx, PreferenceManager.TOTAL_WALLET_ID)
        }
        notifyUri[0]?.let {
            notifyObservers(it, it)
        }
        return result
    }

    private fun deleteInTransaction(database: SQLDatabase, uri: Uri, notifyUri: Array<Uri?>): Int {
        var result = 0
        when (mUriMatcher.match(uri)) {
            CURRENCY_ITEM -> {
                notifyUri[0] = CONTENT_CURRENCIES
                result = database.deleteCurrency(uri.lastPathSegment)
            }
            WALLET_ITEM -> {
                notifyUri[0] = CONTENT_WALLETS
                result = database.deleteWallet(ContentUris.parseId(uri))
            }
            TRANSACTION_ITEM -> {
                notifyUri[0] = CONTENT_TRANSACTIONS
                result = database.deleteTransaction(ContentUris.parseId(uri))
            }
            TRANSFER_ITEM -> {
                notifyUri[0] = CONTENT_TRANSFERS
                result = database.deleteTransfer(ContentUris.parseId(uri))
            }
            CATEGORY_ITEM -> {
                notifyUri[0] = CONTENT_CATEGORIES
                result = database.deleteCategory(ContentUris.parseId(uri))
            }
            DEBT_ITEM -> {
                notifyUri[0] = CONTENT_DEBTS
                result = database.deleteDebt(ContentUris.parseId(uri))
            }
            BUDGET_ITEM -> {
                notifyUri[0] = CONTENT_BUDGETS
                result = database.deleteBudget(ContentUris.parseId(uri))
            }
            SAVING_ITEM -> {
                notifyUri[0] = CONTENT_SAVINGS
                result = database.deleteSaving(ContentUris.parseId(uri))
            }
            EVENT_ITEM -> {
                notifyUri[0] = CONTENT_EVENTS
                result = database.deleteEvent(ContentUris.parseId(uri))
            }
            RECURRENT_TRANSACTION_ITEM -> {
                notifyUri[0] = CONTENT_RECURRENT_TRANSACTIONS
                result = database.deleteRecurrentTransaction(ContentUris.parseId(uri))
            }
            RECURRENT_TRANSFER_ITEM -> {
                notifyUri[0] = CONTENT_RECURRENT_TRANSFERS
                result = database.deleteRecurrentTransfer(ContentUris.parseId(uri))
            }
            TRANSACTION_MODEL_ITEM -> {
                notifyUri[0] = CONTENT_TRANSACTION_MODELS
                result = database.deleteTransactionModel(ContentUris.parseId(uri))
            }
            TRANSFER_MODEL_ITEM -> {
                notifyUri[0] = CONTENT_TRANSFER_MODELS
                result = database.deleteTransferModel(ContentUris.parseId(uri))
            }
            CATEGORY_RULE_ITEM -> {
                notifyUri[0] = CONTENT_CATEGORY_RULES
                result = database.deleteCategoryRule(ContentUris.parseId(uri))
            }
            PLACE_ITEM -> {
                notifyUri[0] = CONTENT_PLACES
                result = database.deletePlace(ContentUris.parseId(uri))
            }
            PERSON_ITEM -> {
                notifyUri[0] = CONTENT_PEOPLE
                result = database.deletePerson(ContentUris.parseId(uri))
            }
            ATTACHMENT_ITEM -> {
                notifyUri[0] = CONTENT_ATTACHMENTS
                result = database.deleteAttachment(ContentUris.parseId(uri))
            }
        }
        return result
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        val result = SQLDatabase.inSharedTransaction(context) { database ->
            updateInTransaction(database, uri, values)
        }
        if (result > 0) {
            notifyObservers(uri, uri)
        }
        return result
    }

    private fun updateInTransaction(database: SQLDatabase, uri: Uri, values: ContentValues?): Int {
        var result = 0
        when (mUriMatcher.match(uri)) {
            CURRENCY_ITEM -> result = database.updateCurrency(uri.lastPathSegment, values)
            WALLET_ITEM -> result = database.updateWallet(ContentUris.parseId(uri), values)
            TRANSACTION_ITEM -> result = database.updateTransaction(ContentUris.parseId(uri), values)
            TRANSFER_ITEM -> result = database.updateTransfer(ContentUris.parseId(uri), values)
            CATEGORY_ITEM -> result = database.updateCategory(ContentUris.parseId(uri), values)
            DEBT_ITEM -> result = database.updateDebt(ContentUris.parseId(uri), values)
            BUDGET_ITEM -> result = database.updateBudget(ContentUris.parseId(uri), values)
            SAVING_ITEM -> result = database.updateSaving(ContentUris.parseId(uri), values)
            EVENT_ITEM -> result = database.updateEvent(ContentUris.parseId(uri), values)
            RECURRENT_TRANSACTION_ITEM -> result = database.updateRecurrentTransaction(ContentUris.parseId(uri), values)
            RECURRENT_TRANSFER_ITEM -> result = database.updateRecurrentTransfer(ContentUris.parseId(uri), values)
            TRANSACTION_MODEL_ITEM -> result = database.updateTransactionModel(ContentUris.parseId(uri), values)
            TRANSFER_MODEL_ITEM -> result = database.updateTransferModel(ContentUris.parseId(uri), values)
            CATEGORY_RULE_ITEM -> result = database.updateCategoryRule(ContentUris.parseId(uri), values)
            PLACE_ITEM -> result = database.updatePlace(ContentUris.parseId(uri), values)
            PERSON_ITEM -> result = database.updatePerson(ContentUris.parseId(uri), values)
        }
        return result
    }

    private fun notifyObservers(uri: Uri, whatToRemember: Uri) {
        val deferred = sDeferredNotifications.get()
        if (deferred != null) {
            deferred.add(whatToRemember)
            return
        }
        PreferenceManager.setLastTimeDataIsChanged(System.currentTimeMillis())
        context?.contentResolver?.notifyChange(uri, null)
    }

    private fun parseIdAtIndex(uri: Uri, index: Int): Long {
        val segments = uri.pathSegments
        val fixedIndex = segments.size - index
        return segments[fixedIndex - 1].toLong()
    }
}
