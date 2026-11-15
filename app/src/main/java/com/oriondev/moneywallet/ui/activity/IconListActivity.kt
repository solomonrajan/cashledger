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

package com.oriondev.moneywallet.ui.activity

import android.content.Intent
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.Menu
import android.view.ViewGroup
import androidx.annotation.MenuRes
import androidx.annotation.StringRes
import androidx.appcompat.widget.SearchView
import androidx.loader.app.LoaderManager
import androidx.loader.content.Loader
import androidx.recyclerview.widget.GridLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.background.IconGroupLoader
import com.oriondev.moneywallet.model.Icon
import com.oriondev.moneywallet.model.IconGroup
import com.oriondev.moneywallet.ui.activity.base.SinglePanelActivity
import com.oriondev.moneywallet.ui.adapter.recycler.IconAdapter
import com.oriondev.moneywallet.ui.view.AdvancedRecyclerView

/**
 * Created by andrea on 03/02/18.
 */
class IconListActivity : SinglePanelActivity(),
    SwipeRefreshLayout.OnRefreshListener,
    LoaderManager.LoaderCallbacks<List<IconGroup>>,
    IconAdapter.Controller {

    private var mAdvancedRecyclerView: AdvancedRecyclerView? = null
    private var mAdapter: IconAdapter? = null
    private var mIconSpan: Int = 0

    override fun onCreatePanelView(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?) {
        val view = inflater.inflate(R.layout.layout_activity_single_panel_body_list, parent, true)
        val recyclerView: AdvancedRecyclerView = view.findViewById(R.id.advanced_recycler_view)
        mAdvancedRecyclerView = recyclerView
        mIconSpan = getIconSpanCount()
        val gridLayoutManager = GridLayoutManager(this, mIconSpan).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return if (mAdapter?.isHeader(position) == true) mIconSpan else 1
                }
            }
        }
        recyclerView.layoutManager = gridLayoutManager
        recyclerView.setEmptyText(R.string.message_no_icon_found)
        val adapter = IconAdapter(this)
        mAdapter = adapter
        recyclerView.adapter = adapter
        recyclerView.setOnRefreshListener(this)
        recyclerView.setState(AdvancedRecyclerView.State.LOADING)
    }

    private fun getIconSpanCount(): Int {
        val displayMetrics: DisplayMetrics = resources.displayMetrics
        val dpWidth = displayMetrics.widthPixels / displayMetrics.density
        // on large screen the panel width is not equal to the display width
        // so we have to dynamically calculate the panel width
        return when {
            dpWidth < 600 -> (dpWidth / ICON_WIDTH_DP).toInt()
            dpWidth in 600.0..<840.0 -> 540 / ICON_WIDTH_DP
            else -> 640 / ICON_WIDTH_DP
        }
    }

    override fun onViewCreated(savedInstanceState: Bundle?) {
        LoaderManager.getInstance(this).restartLoader(ICON_LOADER_ID, null, this)
    }

    @StringRes
    override fun getActivityTitleRes(): Int = R.string.title_activity_icon_list

    override fun isFloatingActionButtonEnabled(): Boolean = false

    @MenuRes
    override fun onInflateMenu(): Int = R.menu.menu_icon_list

    override fun onMenuCreated(menu: Menu) {
        val searchItem = menu.findItem(R.id.action_search)
        if (searchItem != null) {
            val searchView = searchItem.actionView as? SearchView
            searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    mAdapter?.setFilter(query)
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    mAdapter?.setFilter(newText)
                    return true
                }
            })
        }
    }

    override fun onIconClick(icon: Icon?) {
        val intent = Intent().apply {
            putExtra(RESULT_ICON, icon)
        }
        setResult(RESULT_OK, intent)
        finish()
    }

    override fun onRefresh() {
        LoaderManager.getInstance(this).restartLoader(ICON_LOADER_ID, null, this)
        mAdvancedRecyclerView?.setState(AdvancedRecyclerView.State.REFRESHING)
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<List<IconGroup>> {
        return IconGroupLoader(this)
    }

    override fun onLoadFinished(loader: Loader<List<IconGroup>>, iconGroups: List<IconGroup>?) {
        mAdapter?.setIconGroups(iconGroups)
        if (!iconGroups.isNullOrEmpty()) {
            mAdvancedRecyclerView?.setState(AdvancedRecyclerView.State.READY)
        } else {
            mAdvancedRecyclerView?.setState(AdvancedRecyclerView.State.EMPTY)
        }
    }

    override fun onLoaderReset(loader: Loader<List<IconGroup>>) {
        // nothing to release
    }

    companion object {
        const val RESULT_ICON = "IconListActivity::Result::SelectedIcon"

        private const val ICON_LOADER_ID = 46
        private const val ICON_WIDTH_DP = 48
    }
}
