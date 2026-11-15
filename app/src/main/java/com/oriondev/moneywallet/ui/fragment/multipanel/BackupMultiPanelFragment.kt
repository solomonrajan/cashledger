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
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.BackupService
import com.oriondev.moneywallet.ui.adapter.recycler.BackupServiceAdapter
import com.oriondev.moneywallet.ui.fragment.base.MultiPanelFragment
import com.oriondev.moneywallet.ui.fragment.secondary.BackupHandlerFragment

/**
 * Created by andre on 21/03/2018.
 */
class BackupMultiPanelFragment : MultiPanelFragment(), BackupServiceAdapter.Controller {

    private var secondaryPanelCoverLayout: ViewGroup? = null
    private var secondaryPanelFragmentContainer: ViewGroup? = null

    private var allowBackup: Boolean = true
    private var allowRestore: Boolean = true
    private var showCoverLayout: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val arguments = arguments
        if (arguments != null) {
            allowBackup = arguments.getBoolean(ARG_ALLOW_BACKUP, true)
            allowRestore = arguments.getBoolean(ARG_ALLOW_RESTORE, true)
        } else {
            throw IllegalStateException("Arguments bundle is null, please instantiate the fragment using the newInstance() method instead.")
        }
    }

    override fun onCreatePrimaryPanel(inflater: LayoutInflater, primaryPanel: ViewGroup, savedInstanceState: Bundle?) {
        val view = inflater.inflate(R.layout.layout_recycler_view, primaryPanel, true)
        val recyclerView: RecyclerView = view.findViewById(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.adapter = BackupServiceAdapter(this)
    }

    override fun onCreateSecondaryPanel(inflater: LayoutInflater, secondaryPanel: ViewGroup, savedInstanceState: Bundle?) {
        val view = inflater.inflate(R.layout.layout_secondary_panel_fragment_container, secondaryPanel, true)
        val coverLayout: ViewGroup = view.findViewById(R.id.secondary_panel_cover_layout)
        val fragmentContainer: ViewGroup = view.findViewById(R.id.secondary_panel_fragment_container)
        secondaryPanelCoverLayout = coverLayout
        secondaryPanelFragmentContainer = fragmentContainer
        showCoverLayout = savedInstanceState?.getBoolean(SS_SHOW_COVER_LAYOUT) ?: true
        showCoverLayout(showCoverLayout)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(SS_SHOW_COVER_LAYOUT, showCoverLayout)
    }

    override fun getTitleRes(): Int = R.string.title_activity_backup_services

    override fun isFloatingActionButtonEnabled(): Boolean = false

    private fun showCoverLayout(show: Boolean) {
        secondaryPanelCoverLayout?.visibility = if (show) View.VISIBLE else View.GONE
        secondaryPanelFragmentContainer?.visibility = if (show) View.GONE else View.VISIBLE
    }

    override fun onBackupServiceClick(service: BackupService?) {
        val fragmentManager: FragmentManager = childFragmentManager
        val tag = getBackupServiceTag(service)
        val fragment: Fragment? = tag?.let { fragmentManager.findFragmentByTag(it) }
        val container = secondaryPanelFragmentContainer
        if (fragment != null) {
            fragmentManager.beginTransaction().show(fragment).commit()
        } else if (service != null && container != null) {
            val newFragment = BackupHandlerFragment.newInstance(service.identifier, allowBackup, allowRestore)
            fragmentManager.beginTransaction().replace(container.id, newFragment, tag).commit()
        }
        showCoverLayout = service == null
        showCoverLayout(showCoverLayout)
        showSecondaryPanel()
    }

    private fun getBackupServiceTag(service: BackupService?): String? {
        return if (service != null) "BackupMultiPanelFragment::Fragment::${service.identifier}" else null
    }

    companion object {
        private const val ARG_ALLOW_BACKUP = "BackupMultiPanelFragment::Arguments::AllowBackup"
        private const val ARG_ALLOW_RESTORE = "BackupMultiPanelFragment::Arguments::AllowRestore"
        private const val SS_SHOW_COVER_LAYOUT = "BackupMultiPanelFragment::Arguments::ShowCoverLayout"

        @JvmStatic
        fun newInstance(allowBackup: Boolean, allowRestore: Boolean): BackupMultiPanelFragment {
            return BackupMultiPanelFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_ALLOW_BACKUP, allowBackup)
                    putBoolean(ARG_ALLOW_RESTORE, allowRestore)
                }
            }
        }
    }
}
