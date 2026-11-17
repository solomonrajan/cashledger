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

package com.oriondev.moneywallet.ui.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import com.oriondev.moneywallet.R

/**
 * Created by andrea on 04/12/18.
 */
object NotificationContract {

    const val NOTIFICATION_CHANNEL_BACKUP = "channel_backup"
    const val NOTIFICATION_CHANNEL_REMINDER = "channel_reminder"
    const val NOTIFICATION_CHANNEL_ERROR = "channel_error"

    const val NOTIFICATION_ID_BACKUP_PROGRESS = 23454
    const val NOTIFICATION_ID_BACKUP_ERROR = 23455
    const val NOTIFICATION_ID_REMINDER = 23458
    const val NOTIFICATION_ID_CSV_EXPORT_ERROR = 23459

    private data class ChannelSpec(
        val id: String,
        @StringRes val nameRes: Int,
        val importance: Int
    )

    @JvmStatic
    fun initializeNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return
            // create backup channel: in this channel are posted all the notifications
            // regarding the progress of background operations while creating or
            // restoring a backup file (also auto backup)
            val channels = listOf(
                ChannelSpec(
                    NOTIFICATION_CHANNEL_BACKUP,
                    R.string.notification_channel_name_backup,
                    NotificationManager.IMPORTANCE_DEFAULT
                ),
                ChannelSpec(
                    NOTIFICATION_CHANNEL_REMINDER,
                    R.string.notification_channel_name_reminder,
                    NotificationManager.IMPORTANCE_HIGH
                ),
                ChannelSpec(
                    NOTIFICATION_CHANNEL_ERROR,
                    R.string.notification_channel_name_error,
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
            for (spec in channels) {
                val channel = NotificationChannel(
                    spec.id,
                    context.getString(spec.nameRes),
                    spec.importance
                )
                // disable the badge for all the channels
                channel.setShowBadge(false)
                // Register the channel with the system
                notificationManager.createNotificationChannel(channel)
            }
        }
    }
}
