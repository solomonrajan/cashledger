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

package com.oriondev.moneywallet.ui.fragment.multipanel

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.ui.adapter.recycler.SettingCategoryAdapter
import com.oriondev.moneywallet.ui.fragment.base.MultiPanelFragment
import com.oriondev.moneywallet.ui.fragment.secondary.BackupHandlerFragment

/**
 * Created by andrea on 03/03/18.
 */
class BackupMultiPanelFragment : MultiPanelFragment(), SettingCategoryAdapter.ActionListener {

    private var secondaryToolbar: Toolbar? = null
    private var currentAction: BackupHandlerFragment.Action? = null

    override fun onCreatePrimaryPanel(inflater: LayoutInflater, primaryPanel: ViewGroup, savedInstanceState: Bundle?) {
        val view = inflater.inflate(R.layout.layout_setting_primary_panel, primaryPanel, true)
        val recyclerView: RecyclerView = view.findViewById(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(inflater.context)
        val adapter = SettingCategoryAdapter(this)
        adapter.addCategory(
            ID_DRIVE,
            R.drawable.ic_google_drive_black_24dp,
            R.string.backup_title_drive,
            R.string.backup_subtitle_drive
        )
        adapter.addCategory(
            ID_LOCAL_BACKUP,
            R.drawable.ic_file_upload_black_24dp,
            R.string.backup_title_local_backup,
            R.string.backup_subtitle_local_backup
        )
        adapter.addCategory(
            ID_LOCAL_RESTORE,
            R.drawable.ic_file_download_black_24dp,
            R.string.backup_title_local_restore,
            R.string.backup_subtitle_local_restore
        )
        recyclerView.adapter = adapter
    }

    override fun onCreateSecondaryPanel(inflater: LayoutInflater, secondaryPanel: ViewGroup, savedInstanceState: Bundle?) {
        val view = inflater.inflate(R.layout.layout_setting_secondary_panel, secondaryPanel, true)
        val toolbar: Toolbar = view.findViewById(R.id.secondary_toolbar)
        secondaryToolbar = toolbar
        if (!isExtendedLayout) {
            toolbar.setNavigationIcon(R.drawable.ic_arrow_back_black_24dp)
            toolbar.setNavigationOnClickListener { hideSecondaryPanel() }
        }
        val action = if (savedInstanceState != null && savedInstanceState.containsKey(SS_CURRENT_ACTION)) {
            val serializable = savedInstanceState.getSerializable(SS_CURRENT_ACTION)
            serializable as? BackupHandlerFragment.Action ?: BackupHandlerFragment.Action.GOOGLE_DRIVE
        } else {
            BackupHandlerFragment.Action.GOOGLE_DRIVE
        }
        loadSecondaryFragment(action)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable(SS_CURRENT_ACTION, currentAction)
    }

    override fun getTitleRes(): Int = R.string.menu_backup

    override fun isFloatingActionButtonEnabled(): Boolean = false

    override fun onSettingCategoryClick(id: Int) {
        val action = when (id) {
            ID_DRIVE -> BackupHandlerFragment.Action.GOOGLE_DRIVE
            ID_LOCAL_BACKUP -> BackupHandlerFragment.Action.LOCAL_BACKUP
            ID_LOCAL_RESTORE -> BackupHandlerFragment.Action.LOCAL_RESTORE
            else -> return
        }
        loadSecondaryFragment(action)
        showSecondaryPanel()
    }

    private fun loadSecondaryFragment(action: BackupHandlerFragment.Action) {
        val fragmentManager = childFragmentManager
        val fragmentTag = getSecondaryFragmentTag(action)
        var fragment = fragmentManager.findFragmentByTag(fragmentTag)
        if (fragment == null) {
            fragment = BackupHandlerFragment.newInstance(action)
            fragmentManager.beginTransaction()
                .replace(R.id.secondary_panel_fragment_container, fragment, fragmentTag)
                .commitNow()
        } else {
            fragmentManager.beginTransaction().show(fragment).commit()
        }
        secondaryToolbar?.setTitle(getFragmentTitle(action))
        currentAction = action
    }

    private fun getSecondaryFragmentTag(action: BackupHandlerFragment.Action): String {
        return "$SECONDARY_FRAGMENT_TAG::${action.name}"
    }

    private fun getFragmentTitle(action: BackupHandlerFragment.Action): Int {
        return when (action) {
            BackupHandlerFragment.Action.GOOGLE_DRIVE -> R.string.backup_title_drive
            BackupHandlerFragment.Action.LOCAL_BACKUP -> R.string.backup_title_local_backup
            BackupHandlerFragment.Action.LOCAL_RESTORE -> R.string.backup_title_local_restore
        }
    }

    companion object {
        private const val SS_CURRENT_ACTION = "BackupMultiPanelFragment::SavedState::CurrentAction"
        private const val SECONDARY_FRAGMENT_TAG = "BackupMultiPanelFragment::Tag::SecondaryFragment"

        private const val ID_DRIVE = 0
        private const val ID_LOCAL_BACKUP = 1
        private const val ID_LOCAL_RESTORE = 2
    }
}
