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

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.oriondev.moneywallet.service.AutoBackupJobService
import com.oriondev.moneywallet.storage.preference.BackendManager
import java.util.ArrayList

/**
 * Created by andrea on 27/11/18.
 *
 * No longer a broadcast receiver: the alarm it used to answer is gone, and an alarm queued by
 * an older build does not survive the update that removes it. The name is kept so that the
 * change moving the auto backup to the scheduler does not also touch every caller.
 */
object AutoBackupBroadcastReceiver {

    private const val MILLIS_IN_HOUR = 1000 * 60 * 60
    private const val JOB_ID = 3565
    private const val MINIMUM_LATENCY_MILLIS = 60 * 1000L
    private const val TAG = "AutoBackup"

    /**
     * Queue the next auto backup sweep with the job scheduler. This never runs the sweep
     * inline: an overdue occurrence is queued at the minimum latency like any other. Running
     * it inline is what made this method and the sweep call each other, so a backend that
     * failed without moving its timestamp forward recursed until the process was killed.
     */
    @JvmStatic
    fun scheduleAutoBackupTask(context: Context) {
        scheduleAutoBackupTask(context, true)
    }

    /**
     * For callers that only want a job to exist, not to move one that already does.
     *
     * Scheduling an id that is already queued cancels it, and the framework documents that as
     * one of the ways a job is cancelled by its own app. When the scheduler starts the process
     * to run the sweep, Application.onCreate runs first, so an unconditional call there kills
     * the sweep before onStartJob is ever reached.
     */
    @JvmStatic
    fun ensureAutoBackupTaskScheduled(context: Context) {
        scheduleAutoBackupTask(context, false)
    }

    private fun scheduleAutoBackupTask(context: Context, replaceExisting: Boolean) {
        val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
        val backendIdSet = BackendManager.getAutoBackupEnabledServices()
        if (backendIdSet.isNullOrEmpty()) {
            // Nothing enabled, so drop any job left over from when something was.
            jobScheduler.cancel(JOB_ID)
            return
        }
        if (!replaceExisting && jobScheduler.getPendingJob(JOB_ID) != null) {
            return
        }
        var nextTimestamp: Long? = null
        // Copy first: getStringSet hands back its own live instance, and disabling a backend
        // elsewhere in the app removes an element from it while this loop is walking it.
        for (backendId in ArrayList(backendIdSet)) {
            val lastTimestamp = BackendManager.getAutoBackupLastTime(backendId)
            val nextOccurrence = lastTimestamp + intervalMillis(backendId)
            if (nextTimestamp == null || nextOccurrence < nextTimestamp) {
                nextTimestamp = nextOccurrence
            }
        }
        val targetTimestamp = nextTimestamp ?: System.currentTimeMillis()
        val latency = (targetTimestamp - System.currentTimeMillis()).coerceAtLeast(MINIMUM_LATENCY_MILLIS)
        val service = ComponentName(context, AutoBackupJobService::class.java)
        val jobInfo = JobInfo.Builder(JOB_ID, service)
            .setMinimumLatency(latency)
            .setPersisted(true)
            .build()
        jobScheduler.schedule(jobInfo)
        Log.d(TAG, "Auto backup sweep queued in $latency ms")
    }

    /**
     * The configured interval, never less than an hour. The settings screen offers 24 to 168,
     * but a zero or negative value read from anywhere else would make every occurrence due
     * for ever, and the walk to the most recent passed occurrence would never terminate.
     */
    @JvmStatic
    fun intervalMillis(backendId: String): Long {
        return BackendManager.getAutoBackupHoursOffset(backendId).toLong().coerceAtLeast(1L) * MILLIS_IN_HOUR
    }
}
