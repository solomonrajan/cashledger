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

package com.oriondev.moneywallet.broadcast

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.oriondev.moneywallet.service.RecurrenceHandlerIntentService
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.SyncContentProvider
import com.oriondev.moneywallet.utils.DateUtils
import java.util.Date

/**
 * Created by andrea on 10/11/18.
 */
class RecurrenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context != null) {
            startBackgroundTask(context)
        }
    }

    companion object {

        @JvmStatic
        fun scheduleRecurrenceTask(context: Context) {
            cancelPendingIntent(context)
            val contentResolver = context.contentResolver
            val nextOccurrence1 = getMinNextOccurrence(
                contentResolver,
                DataContentProvider.CONTENT_RECURRENT_TRANSACTIONS,
                Contract.RecurrentTransaction.NEXT_OCCURRENCE,
                Contract.RecurrentTransaction.NEXT_OCCURRENCE + " IS NOT NULL"
            )
            val nextOccurrence2 = getMinNextOccurrence(
                contentResolver,
                DataContentProvider.CONTENT_RECURRENT_TRANSFERS,
                Contract.RecurrentTransfer.NEXT_OCCURRENCE,
                Contract.RecurrentTransfer.NEXT_OCCURRENCE + " IS NOT NULL"
            )
            val nextOccurrence3 = getMinNextOccurrence(
                contentResolver,
                SyncContentProvider.CONTENT_BUDGET,
                "DATE(" + Contract.Budget.END_DATE + ", '+1 day')",
                Contract.ROLLABLE_BUDGET_SELECTION
            )
            val nextOccurrence = getMinDate(getMinDate(nextOccurrence1, nextOccurrence2), nextOccurrence3)
            if (nextOccurrence != null) {
                println("[ALARM] Next occurrence is at: $nextOccurrence")
                if (DateUtils.isBeforeNow(nextOccurrence)) {
                    startBackgroundTask(context)
                } else {
                    schedulePendingIntent(context, nextOccurrence)
                }
            }
        }

        private fun getMinNextOccurrence(
            contentResolver: ContentResolver,
            uri: Uri,
            nextOccurrence: String,
            selection: String
        ): Date? {
            val projection = arrayOf("MIN($nextOccurrence)")
            val cursor = contentResolver.query(uri, projection, selection, null, null)
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        val nextOccurrenceString = cursor.getString(0)
                        if (!nextOccurrenceString.isNullOrEmpty()) {
                            return DateUtils.getDateFromSQLDateString(nextOccurrenceString)
                        }
                    }
                } finally {
                    cursor.close()
                }
            }
            return null
        }

        private fun getMinDate(date1: Date?, date2: Date?): Date? {
            return when {
                date1 != null && date2 != null -> if (DateUtils.isBefore(date1, date2)) date1 else date2
                date1 != null -> date1
                date2 != null -> date2
                else -> null
            }
        }

        private fun schedulePendingIntent(context: Context, date: Date) {
            val pendingIntent = createPendingIntent(context)
            val alarmManager = context.getSystemService(Service.ALARM_SERVICE) as? AlarmManager
            if (alarmManager != null) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, date.time, pendingIntent)
                println("[ALARM] RecurrenceTask scheduled at: $date")
            }
        }

        private fun cancelPendingIntent(context: Context) {
            val pendingIntent = createPendingIntent(context)
            val alarmManager = context.getSystemService(Service.ALARM_SERVICE) as? AlarmManager
            alarmManager?.cancel(pendingIntent)
        }

        private fun createPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, RecurrenceBroadcastReceiver::class.java)
            return PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun startBackgroundTask(context: Context) {
            println("[ALARM] RecurrenceTask fired now")
            RecurrenceHandlerIntentService.enqueueWork(context, Intent())
        }
    }
}
