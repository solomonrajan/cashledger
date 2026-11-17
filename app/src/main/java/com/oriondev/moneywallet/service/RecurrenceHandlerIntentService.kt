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

package com.oriondev.moneywallet.service

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.util.Log
import androidx.core.app.JobIntentService
import com.oriondev.moneywallet.broadcast.RecurrenceBroadcastReceiver
import com.oriondev.moneywallet.model.RecurrenceSetting
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.SQLiteDataException
import com.oriondev.moneywallet.storage.database.TransactionContentValuesBuilder
import com.oriondev.moneywallet.storage.database.TransferContentValuesBuilder
import com.oriondev.moneywallet.utils.DateUtils
import java.util.Date

/**
 * Created by andrea on 11/11/18.
 */
@Suppress("DEPRECATION")
class RecurrenceHandlerIntentService : JobIntentService() {

    companion object {
        private const val TAG = "RecurrenceHandler"

        private const val JOB_ID = 3564

        // a daily budget that has been left alone for years would otherwise write one row per day in a
        // single pass; the period that is still open keeps the rule, so the next run carries on
        private const val MAX_BUDGET_PERIODS_PER_RUN = 400

        @JvmStatic
        fun enqueueWork(context: Context, intent: Intent) {
            enqueueWork(context, RecurrenceHandlerIntentService::class.java, JOB_ID, intent)
        }
    }

    override fun onHandleWork(intent: Intent) {
        addMissingRecurrentTransactionOccurrences()
        addMissingRecurrentTransferOccurrences()
        addMissingBudgetPeriods()
        RecurrenceBroadcastReceiver.scheduleRecurrenceTask(this)
    }

    /**
     * Open the periods that a repeating budget has come due for. Every period is a budget row of
     * its own, so the ones that have finished stay where they are and are read as history in the
     * expired tab. Only the period that is currently running keeps the rule, which is what stops
     * a finished period opening its successor a second time.
     */
    private fun addMissingBudgetPeriods() {
        val uri = DataContentProvider.CONTENT_BUDGETS
        val selection = "${Contract.Budget.RULE} IS NOT NULL AND DATE(${Contract.Budget.END_DATE}) < DATE('now', 'localtime')"
        val cursor = contentResolver.query(uri, null, selection, null, null)
        cursor?.use {
            while (it.moveToNext()) {
                try {
                    rollBudgetForward(it)
                } catch (e: SQLiteDataException) {
                    // a budget the provider refuses, such as one whose wallets no longer agree on
                    // a currency, is left where it is; the rest of the run still has to happen,
                    // and so does the alarm that onHandleWork asks for when it returns
                    Log.e(TAG, "Cannot roll budget forward", e)
                }
            }
        }
    }

    private fun rollBudgetForward(cursor: Cursor) {
        val budgetId = cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Budget.ID))
        val rule = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Budget.RULE)) ?: return
        val startDateStr = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Budget.START_DATE)) ?: return
        var periodStart = DateUtils.getDateFromSQLDateString(startDateStr)
        val anchorString = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Budget.RULE_START))
        // the schedule is counted from the day it is anchored to, not from the day this period
        // happens to start, so a weekly one keeps its weekday and one that repeats every second
        // month keeps its months. A row that arrived without an anchor counts from itself.
        val anchor = if (anchorString != null) DateUtils.getDateFromSQLDateString(anchorString) else periodStart
        // walk the rule forward until a period is reached that has not finished yet, so a device
        // that was away for several months opens every period it missed instead of only the last
        val periods = ArrayList<Pair<Date, Date>>()
        var ruleIsSpent = false
        while (periods.size < MAX_BUDGET_PERIODS_PER_RUN) {
            val nextStart = RecurrenceSetting.nextInstanceAfter(rule, anchor, periodStart)
            val nextEnd = if (nextStart != null) RecurrenceSetting.periodEnd(rule, anchor, nextStart) else null
            if (nextStart == null || nextEnd == null) {
                ruleIsSpent = true
                break
            }
            periods.add(Pair(nextStart, nextEnd))
            periodStart = nextStart
            if (!DateUtils.isBeforeToday(nextEnd)) {
                break
            }
        }
        // The rule leaves the period that has just closed before any period is opened. It reads
        // backwards, and it is the order that matters: a run killed part way through then leaves
        // the budget not repeating, which the owner can turn back on, instead of leaving the rule
        // both here and on a period this run opened. Two rows carrying it are two chains, and the
        // next roll walks both, so the budget doubles on every period from then on.
        val closed = copyBudget(cursor)
        closed.put(Contract.Budget.START_DATE, startDateStr)
        closed.put(Contract.Budget.END_DATE, cursor.getString(cursor.getColumnIndexOrThrow(Contract.Budget.END_DATE)))
        closed.putNull(Contract.Budget.RULE)
        contentResolver.update(
            ContentUris.withAppendedId(DataContentProvider.CONTENT_BUDGETS, budgetId),
            closed,
            null,
            null
        )
        for (i in periods.indices) {
            val contentValues = copyBudget(cursor)
            contentValues.put(Contract.Budget.START_DATE, DateUtils.getSQLDateString(periods[i].first))
            contentValues.put(Contract.Budget.END_DATE, DateUtils.getSQLDateString(periods[i].second))
            contentValues.put(Contract.Budget.ROLLED_FROM_ID, budgetId)
            // The last period this run reaches carries the rule on, and it is the only one that
            // does. It carries it even when it has already ended, which is what a run stopped by
            // MAX_BUDGET_PERIODS_PER_RUN leaves behind: the next run picks that period up and
            // keeps going. The rule is dropped only once it can open no further period at all.
            if (i == periods.size - 1 && !ruleIsSpent) {
                contentValues.put(Contract.Budget.RULE, rule)
            } else {
                contentValues.putNull(Contract.Budget.RULE)
            }
            contentResolver.insert(DataContentProvider.CONTENT_BUDGETS, contentValues)
        }
    }

    /**
     * Everything a budget row carries except its dates, its rule and its currency. Every caller
     * sets the dates and the rule, and the insert takes the currency from the wallets.
     */
    private fun copyBudget(cursor: Cursor): ContentValues {
        val contentValues = ContentValues()
        contentValues.put(Contract.Budget.TYPE, cursor.getInt(cursor.getColumnIndexOrThrow(Contract.Budget.TYPE)))
        val categoryIdIndex = cursor.getColumnIndexOrThrow(Contract.Budget.CATEGORY_ID)
        if (cursor.isNull(categoryIdIndex)) {
            contentValues.putNull(Contract.Budget.CATEGORY_ID)
        } else {
            contentValues.put(Contract.Budget.CATEGORY_ID, cursor.getLong(categoryIdIndex))
        }
        contentValues.put(Contract.Budget.MONEY, cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Budget.MONEY)))
        contentValues.put(Contract.Budget.TAG, cursor.getString(cursor.getColumnIndexOrThrow(Contract.Budget.TAG)))
        contentValues.put(Contract.Budget.RULE_START, cursor.getString(cursor.getColumnIndexOrThrow(Contract.Budget.RULE_START)))
        contentValues.put(Contract.Budget.WALLET_IDS, cursor.getString(cursor.getColumnIndexOrThrow(Contract.Budget.WALLET_IDS)))
        contentValues.put(Contract.Budget.CATEGORY_IDS, cursor.getString(cursor.getColumnIndexOrThrow(Contract.Budget.CATEGORY_IDS)))
        return contentValues
    }

    private fun addMissingRecurrentTransactionOccurrences() {
        val uri = DataContentProvider.CONTENT_RECURRENT_TRANSACTIONS
        val selection = "${Contract.RecurrentTransaction.NEXT_OCCURRENCE} IS NOT NULL AND DATE(${Contract.RecurrentTransaction.NEXT_OCCURRENCE}) <= DATE('now', 'localtime')"
        val cursor = contentResolver.query(uri, null, selection, null, null)
        cursor?.use {
            while (it.moveToNext()) {
                // get basic information about the recurrence entity
                val transactionId = it.getLong(it.getColumnIndexOrThrow(Contract.RecurrentTransaction.ID))
                val firstOccurrenceDateString = it.getString(it.getColumnIndexOrThrow(Contract.RecurrentTransaction.NEXT_OCCURRENCE))
                    ?: continue
                val firstOccurrenceDate = DateUtils.getDateFromSQLDateString(firstOccurrenceDateString)
                val rule = it.getString(it.getColumnIndexOrThrow(Contract.RecurrentTransaction.RULE)) ?: continue
                val update = RecurrenceSetting.computeOccurrences(rule, firstOccurrenceDate, Date())
                for (occurrenceDate in update.occurrenceDates) {
                    val builder = TransactionContentValuesBuilder()
                        .money(it.getLong(it.getColumnIndexOrThrow(Contract.RecurrentTransaction.MONEY)))
                        .date(DateUtils.getSQLDateTimeString(occurrenceDate))
                        .description(it.getString(it.getColumnIndexOrThrow(Contract.RecurrentTransaction.DESCRIPTION)))
                        .categoryId(it.getLong(it.getColumnIndexOrThrow(Contract.RecurrentTransaction.CATEGORY_ID)))
                        .direction(it.getInt(it.getColumnIndexOrThrow(Contract.RecurrentTransaction.DIRECTION)))
                        .type(Contract.TransactionType.STANDARD)
                        .walletId(it.getLong(it.getColumnIndexOrThrow(Contract.RecurrentTransaction.WALLET_ID)))
                        .note(it.getString(it.getColumnIndexOrThrow(Contract.RecurrentTransaction.NOTE)))
                    val placeIdIndex = it.getColumnIndexOrThrow(Contract.RecurrentTransaction.PLACE_ID)
                    if (!it.isNull(placeIdIndex)) {
                        builder.placeId(it.getLong(placeIdIndex))
                    }
                    val eventIdIndex = it.getColumnIndexOrThrow(Contract.RecurrentTransaction.EVENT_ID)
                    if (!it.isNull(eventIdIndex)) {
                        builder.eventId(it.getLong(eventIdIndex))
                    }
                    builder.recurrenceId(transactionId)
                        .confirmed(it.getInt(it.getColumnIndexOrThrow(Contract.RecurrentTransaction.CONFIRMED)) == 1)
                        .countInTotal(it.getInt(it.getColumnIndexOrThrow(Contract.RecurrentTransaction.COUNT_IN_TOTAL)) == 1)
                    contentResolver.insert(DataContentProvider.CONTENT_TRANSACTIONS, builder.build())
                }
                val contentValues = ContentValues()
                contentValues.put(
                    Contract.RecurrentTransaction.LAST_OCCURRENCE,
                    DateUtils.getSQLDateString(update.lastOccurrence)
                )
                val nextOccurrenceString: String? = if (update.nextOccurrence != null) {
                    DateUtils.getSQLDateString(update.nextOccurrence)
                } else {
                    null
                }
                contentValues.put(Contract.RecurrentTransaction.NEXT_OCCURRENCE, nextOccurrenceString)
                val contentUri = ContentUris.withAppendedId(
                    DataContentProvider.CONTENT_RECURRENT_TRANSACTIONS,
                    transactionId
                )
                contentResolver.update(contentUri, contentValues, null, null)
            }
        }
    }

    private fun addMissingRecurrentTransferOccurrences() {
        val uri = DataContentProvider.CONTENT_RECURRENT_TRANSFERS
        val selection = "${Contract.RecurrentTransfer.NEXT_OCCURRENCE} IS NOT NULL AND DATE(${Contract.RecurrentTransfer.NEXT_OCCURRENCE}) <= DATE('now', 'localtime')"
        val cursor = contentResolver.query(uri, null, selection, null, null)
        cursor?.use {
            while (it.moveToNext()) {
                // get basic information about the recurrence entity
                val recurrenceId = it.getLong(it.getColumnIndexOrThrow(Contract.RecurrentTransfer.ID))
                val firstOccurrenceDateString = it.getString(it.getColumnIndexOrThrow(Contract.RecurrentTransfer.NEXT_OCCURRENCE))
                    ?: continue
                val firstOccurrenceDate = DateUtils.getDateFromSQLDateString(firstOccurrenceDateString)
                val rule = it.getString(it.getColumnIndexOrThrow(Contract.RecurrentTransfer.RULE)) ?: continue
                val update = RecurrenceSetting.computeOccurrences(rule, firstOccurrenceDate, Date())
                for (occurrenceDate in update.occurrenceDates) {
                    val fromWalletId = it.getLong(it.getColumnIndexOrThrow(Contract.RecurrentTransfer.WALLET_FROM_ID))
                    val builder = TransferContentValuesBuilder()
                        .description(it.getString(it.getColumnIndexOrThrow(Contract.RecurrentTransfer.DESCRIPTION)))
                        .date(DateUtils.getSQLDateTimeString(occurrenceDate))
                        .fromWalletId(fromWalletId)
                        .toWalletId(it.getLong(it.getColumnIndexOrThrow(Contract.RecurrentTransfer.WALLET_TO_ID)))
                        .taxWalletId(fromWalletId)
                        .fromMoney(it.getLong(it.getColumnIndexOrThrow(Contract.RecurrentTransfer.MONEY_FROM)))
                        .toMoney(it.getLong(it.getColumnIndexOrThrow(Contract.RecurrentTransfer.MONEY_TO)))
                        .taxMoney(it.getLong(it.getColumnIndexOrThrow(Contract.RecurrentTransfer.MONEY_TAX)))
                        .note(it.getString(it.getColumnIndexOrThrow(Contract.RecurrentTransfer.NOTE)))
                    val placeIdIndex = it.getColumnIndexOrThrow(Contract.RecurrentTransfer.PLACE_ID)
                    if (!it.isNull(placeIdIndex)) {
                        builder.placeId(it.getLong(placeIdIndex))
                    }
                    val eventIdIndex = it.getColumnIndexOrThrow(Contract.RecurrentTransfer.EVENT_ID)
                    if (!it.isNull(eventIdIndex)) {
                        builder.eventId(it.getLong(eventIdIndex))
                    }
                    builder.recurrenceId(recurrenceId)
                        .confirmed(it.getInt(it.getColumnIndexOrThrow(Contract.RecurrentTransfer.CONFIRMED)) == 1)
                        .countInTotal(it.getInt(it.getColumnIndexOrThrow(Contract.RecurrentTransfer.COUNT_IN_TOTAL)) == 1)
                    contentResolver.insert(DataContentProvider.CONTENT_TRANSFERS, builder.build())
                }
                val contentValues = ContentValues()
                contentValues.put(
                    Contract.RecurrentTransfer.LAST_OCCURRENCE,
                    DateUtils.getSQLDateString(update.lastOccurrence)
                )
                val nextOccurrenceString: String? = if (update.nextOccurrence != null) {
                    DateUtils.getSQLDateString(update.nextOccurrence)
                } else {
                    null
                }
                contentValues.put(Contract.RecurrentTransfer.NEXT_OCCURRENCE, nextOccurrenceString)
                val contentUri = ContentUris.withAppendedId(
                    DataContentProvider.CONTENT_RECURRENT_TRANSFERS,
                    recurrenceId
                )
                contentResolver.update(contentUri, contentValues, null, null)
            }
        }
    }
}
