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

package com.oriondev.moneywallet.ui.fragment.secondary

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.RecyclerView
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.LockMode
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.activity.CategoryRuleListActivity
import com.oriondev.moneywallet.ui.activity.CurrencyListActivity
import com.oriondev.moneywallet.ui.activity.LockActivity
import com.oriondev.moneywallet.ui.preference.ThemedInputPreference
import com.oriondev.moneywallet.ui.preference.ThemedListPreference
import com.oriondev.moneywallet.utils.DateFormatter
import com.oriondev.moneywallet.utils.SystemBars
import com.oriondev.moneywallet.utils.Urls
import com.oriondev.moneywallet.utils.Utils
import com.oriondev.moneywallet.view.MapViewWrapper
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Created by andrea on 07/03/18.
 */
class UtilitySettingFragment : PreferenceFragmentCompat() {

    /**
     * A fragment has to register before it is created, so this cannot move to the point of use.
     */
    private val mNotificationPermissionLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // nothing to do either way. the reminder is scheduled on a denial too, its
            // alarm just fires into nothing, and the ask is already recorded as spent.
        }

    private var mDailyReminderPreference: ThemedListPreference? = null
    private var mSecurityModeListPreference: ThemedListPreference? = null
    private var mSecurityModeChangeKeyPreference: Preference? = null

    private var mMapTileServerPreference: ThemedInputPreference? = null

    private var mCurrencyManagementPreference: Preference? = null
    private var mCategoryRulesPreference: Preference? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.settings_utility)
        mDailyReminderPreference = findPreference("daily_reminder") as? ThemedListPreference
        mSecurityModeListPreference = findPreference("security_mode") as? ThemedListPreference
        mSecurityModeChangeKeyPreference = findPreference("security_change_key")

        mCurrencyManagementPreference = findPreference("currency_management")
        mCategoryRulesPreference = findPreference("category_rules")
        mMapTileServerPreference = findPreference("map_tile_server") as? ThemedInputPreference
        if (!MapViewWrapper.supportsCustomTileServer()) {
            val mapCategory = findPreference("map_category") as? PreferenceCategory
            if (mapCategory != null) {
                preferenceScreen.removePreference(mapCategory)
            }
            mMapTileServerPreference = null
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // setup preference logic
        // must precede setupCurrentDailyReminder below: setting the value looks it up in the
        // entry values, and onResume is too late because it runs after this
        setupDailyReminderEntries()
        val context = activity
        if (context != null && isFingerprintAuthSupported(context)) {
            mSecurityModeListPreference?.setEntries(
                arrayOf(
                    getString(R.string.setting_item_security_none),
                    getString(R.string.setting_item_security_pin),
                    getString(R.string.setting_item_security_sequence),
                    getString(R.string.setting_item_security_fingerprint)
                )
            )
            mSecurityModeListPreference?.setEntryValues(
                arrayOf(
                    PreferenceManager.LOCK_MODE_NONE.toString(),
                    PreferenceManager.LOCK_MODE_PIN.toString(),
                    PreferenceManager.LOCK_MODE_SEQUENCE.toString(),
                    PreferenceManager.LOCK_MODE_FINGERPRINT.toString()
                )
            )
        } else {
            mSecurityModeListPreference?.setEntries(
                arrayOf(
                    getString(R.string.setting_item_security_none),
                    getString(R.string.setting_item_security_pin),
                    getString(R.string.setting_item_security_sequence)
                )
            )
            mSecurityModeListPreference?.setEntryValues(
                arrayOf(
                    PreferenceManager.LOCK_MODE_NONE.toString(),
                    PreferenceManager.LOCK_MODE_PIN.toString(),
                    PreferenceManager.LOCK_MODE_SEQUENCE.toString()
                )
            )
        }

        // setup current (or default) values
        setupCurrentDailyReminder()
        setupCurrentLockMode()
        setupCurrentMapTileServer()

        // attach a listener to get notified when values changes
        mDailyReminderPreference?.setOnPreferenceChangeListener { _, newValue ->
            val hour = (newValue as String).toInt()
            PreferenceManager.setCurrentDailyReminder(activity, hour)
            setupCurrentDailyReminder()
            if (hour != PreferenceManager.DAILY_REMINDER_DISABLED) {
                requestNotificationPermission()
            }
            false
        }
        mSecurityModeListPreference?.setOnPreferenceChangeListener { preference, newValue ->
            val oldValue = (preference as ThemedListPreference).getValue()
            val value = newValue as String
            if (TextUtils.equals(oldValue, value)) {
                // the value is not changed
                return@setOnPreferenceChangeListener false
            }
            var intent: Intent? = null
            val integerValue = value.toInt()
            when (integerValue) {
                PreferenceManager.LOCK_MODE_NONE -> intent = LockActivity.disableLock(activity)
                PreferenceManager.LOCK_MODE_PIN,
                PreferenceManager.LOCK_MODE_SEQUENCE,
                PreferenceManager.LOCK_MODE_FINGERPRINT -> {
                    if (oldValue?.toIntOrNull() == PreferenceManager.LOCK_MODE_NONE) {
                        intent = LockActivity.enableLock(activity, LockMode.get(integerValue))
                    } else {
                        intent = LockActivity.changeMode(activity, LockMode.get(integerValue))
                    }
                }
            }
            if (intent != null) {
                @Suppress("DEPRECATION")
                startActivityForResult(intent, REQUEST_CODE_LOCK_ACTIVITY)
            }
            false
        }
        mSecurityModeChangeKeyPreference?.setOnPreferenceClickListener {
            startActivity(LockActivity.changeKey(activity))
            false
        }

        val mapTilePref = mMapTileServerPreference
        if (mapTilePref != null) {
            mapTilePref.setInput(R.string.setting_hint_map_tile_server, true, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
            mapTilePref.setOnPreferenceChangeListener { _, newValue ->
                val url = (newValue as? String)?.trim()
                if (!url.isNullOrEmpty() && !Urls.isUsableTileAddress(url)) {
                    // rejected here because at map load a bad address is simply ignored, so
                    // the map quietly keeps using the default and the setting looks applied
                    mapTilePref.setSummary(R.string.setting_summary_map_tile_server_invalid)
                    // the widget keeps whatever was typed, and that value is both the
                    // dialog's prefill and how it decides something changed. Put the stored one
                    // back, or retyping the same bad address is silently a no op
                    mapTilePref.setCurrentValue(PreferenceManager.getMapTileServer())
                    return@setOnPreferenceChangeListener false
                }
                PreferenceManager.setMapTileServer(url)
                setupCurrentMapTileServer()
                false
            }
            setupCurrentMapTileServer()
        }

        mCurrencyManagementPreference?.setOnPreferenceClickListener {
            val act = activity
            if (act != null) {
                val intent = Intent(act, CurrencyListActivity::class.java).apply {
                    putExtra(CurrencyListActivity.ACTIVITY_MODE, CurrencyListActivity.CURRENCY_MANAGER)
                }
                startActivity(intent)
            }
            false
        }
        mCategoryRulesPreference?.setOnPreferenceClickListener {
            val act = activity
            if (act != null) {
                startActivity(Intent(act, CategoryRuleListActivity::class.java))
            }
            false
        }
    }

    override fun onCreateRecyclerView(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?): RecyclerView {
        val recyclerView = super.onCreateRecyclerView(inflater, parent, savedInstanceState)
        recyclerView.setPadding(0, 0, 0, 0)
        // After the reset above, not before it, or the navigation bar clearance is the
        // padding that gets zeroed.
        SystemBars.pad(
            recyclerView,
            false,
            resources.getBoolean(R.bool.secondary_panel_fills_window),
            true
        )
        return recyclerView
    }

    private fun isFingerprintAuthSupported(context: Context): Boolean {
        // Hardware present and usable, or present but not yet enrolled (the lock screen then
        // guides the user to set up a fingerprint). Mirrors the old isHardwareDetected() semantics.
        val status = BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        )
        return status == BiometricManager.BIOMETRIC_SUCCESS ||
            status == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
    }

    override fun onResume() {
        super.onResume()
        // the 12 or 24 hour choice lives in the system settings and changing it is not a
        // configuration change, so nothing recreates this screen on the way back
        setupDailyReminderEntries()
        setupCurrentDailyReminder()
    }

    /**
     * The hours were written out as fixed 24 hour strings, which read wrong on a device set to 12
     * hour and in Persian, which does not use Latin digits. The pattern comes from DateFormatter so
     * this row follows the same decision as every other time in the app rather than deriving it
     * again from the platform.
     */
    private fun setupDailyReminderEntries() {
        val entries = arrayOfNulls<String>(HOURS_IN_DAY + 1)
        val values = arrayOfNulls<String>(HOURS_IN_DAY + 1)
        entries[0] = getString(R.string.setting_item_daily_reminder_none)
        values[0] = PreferenceManager.DAILY_REMINDER_DISABLED.toString()
        val format = hourOfDayFormat()
        for (hour in 0 until HOURS_IN_DAY) {
            entries[hour + 1] = formatHourOfDay(format, hour)
            values[hour + 1] = hour.toString()
        }
        mDailyReminderPreference?.setEntries(entries.filterNotNull().toTypedArray())
        mDailyReminderPreference?.setEntryValues(values.filterNotNull().toTypedArray())
    }

    private fun requestNotificationPermission() {
        val act = activity
        if (act != null && Utils.shouldAskNotificationPermission(act)) {
            PreferenceManager.setAskedNotificationPermission()
            mNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun setupCurrentDailyReminder() {
        val hour = PreferenceManager.getCurrentDailyReminder()
        mDailyReminderPreference?.setValue(hour.toString())
        if (hour == PreferenceManager.DAILY_REMINDER_DISABLED) {
            mDailyReminderPreference?.setSummary(R.string.setting_item_daily_reminder_none)
        } else {
            val summary = getString(R.string.setting_summary_daily_reminder, formatHourOfDay(hourOfDayFormat(), hour))
            mDailyReminderPreference?.summary = summary
        }
    }

    private fun setupCurrentLockMode() {
        val lockMode = PreferenceManager.getCurrentLockMode()
        mSecurityModeListPreference?.setValue(lockMode.valueAsString)
        when (lockMode) {
            LockMode.NONE -> {
                mSecurityModeListPreference?.setSummary(R.string.setting_item_security_none)
                mSecurityModeChangeKeyPreference?.isVisible = false
            }
            LockMode.PIN -> {
                mSecurityModeListPreference?.setSummary(R.string.setting_item_security_pin)
                mSecurityModeChangeKeyPreference?.setTitle(R.string.setting_title_security_change_pin)
                mSecurityModeChangeKeyPreference?.isVisible = true
            }
            LockMode.SEQUENCE -> {
                mSecurityModeListPreference?.setSummary(R.string.setting_item_security_sequence)
                mSecurityModeChangeKeyPreference?.setTitle(R.string.setting_title_security_change_sequence)
                mSecurityModeChangeKeyPreference?.isVisible = true
            }
            LockMode.FINGERPRINT -> {
                mSecurityModeListPreference?.setSummary(R.string.setting_item_security_fingerprint)
                mSecurityModeChangeKeyPreference?.isVisible = false
            }
        }
    }

    private fun setupCurrentMapTileServer() {
        val pref = mMapTileServerPreference ?: return
        val url = PreferenceManager.getMapTileServer()
        if (TextUtils.isEmpty(url)) {
            pref.setSummary(R.string.setting_summary_map_tile_server_default)
        } else {
            pref.summary = getString(R.string.setting_summary_map_tile_server, url)
        }
        // without this the dialog opens empty even when a server is configured, so an existing
        // address has to be retyped rather than corrected, and pressing ok on the untouched field
        // clears it
        pref.setCurrentValue(url)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_LOCK_ACTIVITY) {
            setupCurrentLockMode()
        } else {
            @Suppress("DEPRECATION")
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    companion object {
        private const val REQUEST_CODE_LOCK_ACTIVITY = 8239
        private const val HOURS_IN_DAY = 24

        /**
         * Formatted in UTC deliberately. These are hours of a day rather than moments in time, and
         * putting an hour into a local calendar means asking for a wall time that does not exist on the
         * morning the clocks go forward. Which hour depends on the zone: where the shift is at 02:00,
         * that hour vanishes from the list and 03:00 appears twice, once a year.
         */
        private fun hourOfDayFormat(): DateFormat {
            val format = SimpleDateFormat(DateFormatter.getTimePattern(), Locale.getDefault())
            format.timeZone = TimeZone.getTimeZone("UTC")
            return format
        }

        private fun formatHourOfDay(format: DateFormat, hour: Int): String {
            return format.format(Date(hour * DateUtils.HOUR_IN_MILLIS))
        }
    }
}
