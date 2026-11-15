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
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import androidx.annotation.MenuRes
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import androidx.recyclerview.widget.LinearLayoutManager
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.ui.activity.MapActivity
import com.oriondev.moneywallet.ui.activity.NewEditItemActivity
import com.oriondev.moneywallet.ui.activity.NewEditPlaceActivity
import com.oriondev.moneywallet.ui.adapter.recycler.AbstractCursorAdapter
import com.oriondev.moneywallet.ui.adapter.recycler.PlaceCursorAdapter
import com.oriondev.moneywallet.ui.fragment.base.MultiPanelCursorListItemFragment
import com.oriondev.moneywallet.ui.fragment.base.SecondaryPanelFragment
import com.oriondev.moneywallet.ui.fragment.secondary.PlaceItemFragment
import com.oriondev.moneywallet.ui.view.AdvancedRecyclerView

/**
 * Created by andrea on 02/03/18.
 */
class PlaceMultiPanelFragment : MultiPanelCursorListItemFragment(), PlaceCursorAdapter.ActionListener {

    override fun onPrepareRecyclerView(recyclerView: AdvancedRecyclerView) {
        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.setEmptyText(R.string.message_no_place_found)
    }

    override fun onCreateAdapter(): AbstractCursorAdapter<*> {
        return PlaceCursorAdapter(this)
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor>? {
        val act = activity
        if (act != null) {
            val uri: Uri = DataContentProvider.CONTENT_PLACES
            val projection = arrayOf(
                Contract.Place.ID,
                Contract.Place.NAME,
                Contract.Place.ICON,
                Contract.Place.ADDRESS
            )
            val sortOrder = "${Contract.Place.NAME} DESC"
            return CursorLoader(act, uri, projection, null, null, sortOrder)
        }
        return null
    }

    override fun onCreateSecondaryPanel(): SecondaryPanelFragment = PlaceItemFragment()

    override fun getSecondaryFragmentTag(): String = SECONDARY_FRAGMENT_TAG

    override fun getTitleRes(): Int = R.string.menu_place

    @MenuRes
    override fun onInflateMenu(): Int = R.menu.menu_place_multipanel_fragment

    override fun onMenuItemClick(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_open_map) {
            startActivity(Intent(activity, MapActivity::class.java))
        }
        return false
    }

    override fun onPlaceClick(id: Long) {
        showItemId(id)
        showSecondaryPanel()
    }

    override fun onFloatingActionButtonClick() {
        val intent = Intent(activity, NewEditPlaceActivity::class.java).apply {
            putExtra(NewEditItemActivity.MODE, NewEditItemActivity.Mode.NEW_ITEM)
        }
        startActivity(intent)
    }

    companion object {
        private const val SECONDARY_FRAGMENT_TAG = "PlaceMultiPanelFragment::Tag::SecondaryPanelFragment"
    }
}
