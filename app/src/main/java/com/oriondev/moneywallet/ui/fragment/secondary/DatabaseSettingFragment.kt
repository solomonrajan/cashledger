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

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.RecyclerView
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.ui.activity.BackupListActivity
import com.oriondev.moneywallet.ui.activity.ImportExportActivity
import com.oriondev.moneywallet.utils.SystemBars

/**
 * Created by andre on 21/03/2018.
 */
class DatabaseSettingFragment : PreferenceFragmentCompat() {

    private var mBackupServicesPreference: Preference? = null
    private var mImportDataPreference: Preference? = null
    private var mExportDataPreference: Preference? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.settings_database)
        mBackupServicesPreference = findPreference("backup_services")
        mImportDataPreference = findPreference("import_data")
        mExportDataPreference = findPreference("export_data")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mBackupServicesPreference?.setOnPreferenceClickListener {
            val intent = Intent(activity, BackupListActivity::class.java).apply {
                putExtra(BackupListActivity.BACKUP_MODE, BackupListActivity.FULL)
            }
            startActivity(intent)
            false
        }
        mImportDataPreference?.setOnPreferenceClickListener {
            val intent = Intent(activity, ImportExportActivity::class.java).apply {
                putExtra(ImportExportActivity.MODE, ImportExportActivity.MODE_IMPORT)
            }
            startActivity(intent)
            false
        }
        mExportDataPreference?.setOnPreferenceClickListener {
            val intent = Intent(activity, ImportExportActivity::class.java).apply {
                putExtra(ImportExportActivity.MODE, ImportExportActivity.MODE_EXPORT)
            }
            startActivity(intent)
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
}
