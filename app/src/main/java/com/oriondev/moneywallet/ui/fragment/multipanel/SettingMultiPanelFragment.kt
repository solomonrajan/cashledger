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

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.ui.activity.AboutActivity
import com.oriondev.moneywallet.ui.adapter.recycler.SettingCategoryAdapter
import com.oriondev.moneywallet.ui.fragment.base.MultiPanelFragment
import com.oriondev.moneywallet.ui.fragment.secondary.DatabaseSettingFragment
import com.oriondev.moneywallet.ui.fragment.secondary.UserInterfaceSettingFragment
import com.oriondev.moneywallet.ui.fragment.secondary.UtilitySettingFragment

/**
 * Created by andrea on 03/03/18.
 */
class SettingMultiPanelFragment : MultiPanelFragment(), SettingCategoryAdapter.ActionListener {

    private var secondaryToolbar: Toolbar? = null
    private var currentId: Int = ID_USER_INTERFACE

    override fun onCreatePrimaryPanel(inflater: LayoutInflater, primaryPanel: ViewGroup, savedInstanceState: Bundle?) {
        val view = inflater.inflate(R.layout.layout_setting_primary_panel, primaryPanel, true)
        val recyclerView: RecyclerView = view.findViewById(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(inflater.context)
        val adapter = SettingCategoryAdapter(this)
        adapter.addCategory(
            ID_USER_INTERFACE,
            R.drawable.ic_color_lens_black_24dp,
            R.string.setting_title_user_interface,
            R.string.setting_subtitle_user_interface
        )
        adapter.addCategory(
            ID_UTILITY,
            R.drawable.ic_vpn_key_black_24dp,
            R.string.setting_title_utility,
            R.string.setting_subtitle_utility
        )
        adapter.addCategory(
            ID_DATABASE,
            R.drawable.ic_storage_black_24dp,
            R.string.setting_title_database,
            R.string.setting_subtitle_database
        )
        adapter.addCategory(
            ID_ABOUT,
            R.drawable.ic_info_outline_24dp,
            R.string.setting_title_about,
            R.string.setting_subtitle_about
        )
        recyclerView.adapter = adapter
    }

    override fun onCreateSecondaryPanel(inflater: LayoutInflater, secondaryPanel: ViewGroup, savedInstanceState: Bundle?) {
        val view = inflater.inflate(R.layout.layout_setting_secondary_panel, secondaryPanel, true)
        val toolbar: Toolbar = view.findViewById(R.id.secondary_toolbar)
        secondaryToolbar = toolbar
        if (!isExtendedLayout()) {
            toolbar.setNavigationIcon(R.drawable.ic_arrow_back_black_24dp)
            toolbar.setNavigationOnClickListener { hideSecondaryPanel() }
        }
        if (savedInstanceState != null) {
            currentId = savedInstanceState.getInt(SS_CURRENT_ID, ID_USER_INTERFACE)
        }
        loadSecondaryFragment(currentId)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(SS_CURRENT_ID, currentId)
    }

    override fun getTitleRes(): Int = R.string.menu_setting

    override fun isFloatingActionButtonEnabled(): Boolean = false

    override fun onSettingCategoryClick(id: Int) {
        if (id < FRAGMENT_TITLES.size) {
            loadSecondaryFragment(id)
            showSecondaryPanel()
        } else {
            startActivity(Intent(activity, AboutActivity::class.java))
        }
    }

    private fun loadSecondaryFragment(identifier: Int) {
        val fragmentManager = childFragmentManager
        val fragmentTag = getSecondaryFragmentTag(identifier)
        var fragment = fragmentManager.findFragmentByTag(fragmentTag)
        if (fragment == null) {
            fragment = onCreateFragment(identifier)
            fragmentManager.beginTransaction()
                .replace(R.id.secondary_panel_fragment_container, fragment, fragmentTag)
                .commitNow()
        } else {
            fragmentManager.beginTransaction().show(fragment).commit()
        }
        secondaryToolbar?.setTitle(FRAGMENT_TITLES[identifier])
        currentId = identifier
    }

    private fun getSecondaryFragmentTag(identifier: Int): String {
        return "SettingMultiPanelFragment::Tag::SecondaryFragment$identifier"
    }

    private fun onCreateFragment(identifier: Int): Fragment {
        return when (identifier) {
            ID_USER_INTERFACE -> UserInterfaceSettingFragment()
            ID_UTILITY -> UtilitySettingFragment()
            ID_DATABASE -> DatabaseSettingFragment()
            else -> throw IllegalArgumentException("Invalid fragment id: $identifier")
        }
    }

    companion object {
        private const val SS_CURRENT_ID = "SettingMultiPanelFragment::SavedState::CurrentId"

        private const val ID_USER_INTERFACE = 0
        private const val ID_UTILITY = 1
        private const val ID_DATABASE = 2
        private const val ID_ABOUT = 3

        private val FRAGMENT_TITLES = intArrayOf(
            R.string.setting_title_user_interface,
            R.string.setting_title_utility,
            R.string.setting_title_database
        )
    }
}
