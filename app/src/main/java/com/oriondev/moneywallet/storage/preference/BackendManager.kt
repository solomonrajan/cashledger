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

package com.oriondev.moneywallet.storage.preference

import android.content.Context
import android.content.SharedPreferences
import java.util.HashSet

/**
 * Created by andrea on 04/12/18.
 */
object BackendManager {

    private const val FILE_NAME = "backend_preferences"

    private const val AUTO_BACKUP_ENABLED_SERVICES = "auto_backup_enabled_services"
    private const val BACKEND_AUTO_BACKUP_ENABLED = "auto_backup_enabled_"
    private const val BACKEND_AUTO_BACKUP_WIFI_ONLY = "auto_backup_wifi_only_"
    private const val BACKEND_AUTO_BACKUP_DATA_CHANGED_ONLY = "auto_backup_data_changed_only_"
    private const val BACKEND_AUTO_BACKUP_EXPORT_CSV = "auto_backup_export_csv_"
    private const val BACKEND_AUTO_BACKUP_OFFSET = "auto_backup_hour_offset_"
    private const val BACKEND_AUTO_BACKUP_FOLDER = "auto_backup_folder_"
    private const val BACKEND_AUTO_BACKUP_PASSWORD = "auto_backup_password_"
    private const val BACKEND_AUTO_BACKUP_LAST_TIME = "auto_backup_last_time_"
    private const val BACKEND_AUTO_BACKUP_DISABLED_BY_FAILURE = "auto_backup_disabled_by_failure_"

    private var mPreferences: SharedPreferences? = null

    private val prefs: SharedPreferences
        get() = checkNotNull(mPreferences) { "BackendManager must be initialized before use" }

    @JvmStatic
    fun initialize(context: Context) {
        mPreferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Turns automatic backup off for a backend because a backup, a restore or a listing failed in
     * a way the app treats as not recoverable, and records that the app is the one that turned it
     * off. Nothing else sets that flag, so a user switching the service off in the settings dialog
     * is not mistaken for a failure.
     *
     * The sweep builds its backend before the block that calls this, so a backend that cannot be
     * built at all, a released storage grant or a cleared server address, never gets here from
     * the sweep, and stays enabled while its notification says otherwise. The manual backup,
     * restore and browse path builds its backend inside the block, so the same failure does get
     * here from there.
     *
     * The flag is what the settings dialog has to work with. The saved folder and password survive
     * a failure, so the dialog reopens showing them, and without this it cannot tell the two
     * states apart: a backend the user switched off and a backend a failure switched off look
     * identical in these preferences.
     *
     * Only recorded for a backend that had automatic backup on. The manual backup, restore and
     * browse paths share the caller in [com.oriondev.moneywallet.service.BackupHandlerIntentService],
     * and browsing a backend runs a listing every time that screen resumes, so without this a backend
     * the user never turned automatic backup on for would report that a failure turned it off.
     */
    @JvmStatic
    fun disableAutoBackupAfterFailure(backendId: String) {
        if (isAutoBackupEnabled(backendId)) {
            prefs.edit().putBoolean(BACKEND_AUTO_BACKUP_DISABLED_BY_FAILURE + backendId, true).apply()
        }
        setAutoBackupEnabled(backendId, false)
    }

    @JvmStatic
    fun isAutoBackupDisabledByFailure(backendId: String): Boolean {
        return prefs.getBoolean(BACKEND_AUTO_BACKUP_DISABLED_BY_FAILURE + backendId, false)
    }

    @JvmStatic
    fun setAutoBackupEnabled(backendId: String, enabled: Boolean) {
        prefs.edit().putBoolean(BACKEND_AUTO_BACKUP_ENABLED + backendId, enabled).apply()
        val existing = prefs.getStringSet(AUTO_BACKUP_ENABLED_SERVICES, null)
        val backendIdSet = if (existing != null) HashSet(existing) else HashSet()
        if (enabled) {
            backendIdSet.add(backendId)
            // Turning the service on is what forgets the failure that turned it off. The record
            // describes a service that is off, so keeping it past this point would let it be
            // shown against a service that is running.
            prefs.edit()
                .remove(BACKEND_AUTO_BACKUP_DISABLED_BY_FAILURE + backendId)
                .apply()
        } else {
            backendIdSet.remove(backendId)
            // The folder and the password stay. This is reached from the failure path above as
            // well as from the settings dialog, and the failure path never writes them back, so
            // erasing them threw away what the user chose when a backup, a restore or a listing
            // failed. The last run time still goes, because
            // AutoBackupJobService leans on it being cleared, skipping the write back for a
            // backend a failure has just disabled so that switching the backend on again does
            // not fire a backup at once.
            prefs.edit()
                .remove(BACKEND_AUTO_BACKUP_LAST_TIME + backendId)
                .apply()
        }
        prefs.edit().putStringSet(AUTO_BACKUP_ENABLED_SERVICES, backendIdSet).apply()
    }

    @JvmStatic
    fun setAutoBackupOnWiFiOnly(backendId: String, wifiOnly: Boolean) {
        prefs.edit().putBoolean(BACKEND_AUTO_BACKUP_WIFI_ONLY + backendId, wifiOnly).apply()
    }

    @JvmStatic
    fun setAutoBackupExportCsv(backendId: String, exportCsv: Boolean) {
        prefs.edit().putBoolean(BACKEND_AUTO_BACKUP_EXPORT_CSV + backendId, exportCsv).apply()
    }

    @JvmStatic
    fun setAutoBackupWhenDataIsChangedOnly(backendId: String, dataChangedOnly: Boolean) {
        prefs.edit().putBoolean(BACKEND_AUTO_BACKUP_DATA_CHANGED_ONLY + backendId, dataChangedOnly).apply()
    }

    @JvmStatic
    fun setAutoBackupHoursOffset(backendId: String, offset: Int) {
        prefs.edit().putInt(BACKEND_AUTO_BACKUP_OFFSET + backendId, offset).apply()
    }

    @JvmStatic
    fun setAutoBackupFolder(backendId: String, folder: String?) {
        prefs.edit().putString(BACKEND_AUTO_BACKUP_FOLDER + backendId, folder).apply()
    }

    @JvmStatic
    fun setAutoBackupPassword(backendId: String, password: String?) {
        prefs.edit().putString(BACKEND_AUTO_BACKUP_PASSWORD + backendId, password).apply()
    }

    @JvmStatic
    fun setAutoBackupLastTime(backendId: String, timestamp: Long) {
        prefs.edit().putLong(BACKEND_AUTO_BACKUP_LAST_TIME + backendId, timestamp).apply()
    }

    @JvmStatic
    fun getAutoBackupEnabledServices(): Set<String>? {
        return prefs.getStringSet(AUTO_BACKUP_ENABLED_SERVICES, null)
    }

    @JvmStatic
    fun isAutoBackupEnabled(backendId: String): Boolean {
        return prefs.getBoolean(BACKEND_AUTO_BACKUP_ENABLED + backendId, false)
    }

    @JvmStatic
    fun isAutoBackupOnWiFiOnly(backendId: String): Boolean {
        return prefs.getBoolean(BACKEND_AUTO_BACKUP_WIFI_ONLY + backendId, false)
    }

    @JvmStatic
    fun isAutoBackupExportCsv(backendId: String): Boolean {
        return prefs.getBoolean(BACKEND_AUTO_BACKUP_EXPORT_CSV + backendId, false)
    }

    @JvmStatic
    fun isAutoBackupWhenDataIsChangedOnly(backendId: String): Boolean {
        return prefs.getBoolean(BACKEND_AUTO_BACKUP_DATA_CHANGED_ONLY + backendId, true)
    }

    @JvmStatic
    fun getAutoBackupHoursOffset(backendId: String): Int {
        return prefs.getInt(BACKEND_AUTO_BACKUP_OFFSET + backendId, 48)
    }

    @JvmStatic
    fun getAutoBackupFolder(backendId: String): String? {
        return prefs.getString(BACKEND_AUTO_BACKUP_FOLDER + backendId, null)
    }

    @JvmStatic
    fun getAutoBackupPassword(backendId: String): String? {
        return prefs.getString(BACKEND_AUTO_BACKUP_PASSWORD + backendId, null)
    }

    @JvmStatic
    fun getAutoBackupLastTime(backendId: String): Long {
        val now = System.currentTimeMillis()
        if (!prefs.contains(BACKEND_AUTO_BACKUP_LAST_TIME + backendId)) {
            // store the current timestamp to avoid that a recent rescheduling of the auto backup
            // service moves the next occurrence to far
            prefs.edit().putLong(BACKEND_AUTO_BACKUP_LAST_TIME + backendId, now).apply()
        }
        return prefs.getLong(BACKEND_AUTO_BACKUP_LAST_TIME + backendId, now)
    }
}
