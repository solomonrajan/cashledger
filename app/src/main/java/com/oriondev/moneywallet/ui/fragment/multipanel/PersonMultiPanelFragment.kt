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
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import androidx.recyclerview.widget.LinearLayoutManager
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.ui.activity.NewEditItemActivity
import com.oriondev.moneywallet.ui.activity.NewEditPersonActivity
import com.oriondev.moneywallet.ui.activity.TransactionListActivity
import com.oriondev.moneywallet.ui.adapter.recycler.AbstractCursorAdapter
import com.oriondev.moneywallet.ui.adapter.recycler.PersonCursorAdapter
import com.oriondev.moneywallet.ui.fragment.base.MultiPanelCursorListItemFragment
import com.oriondev.moneywallet.ui.fragment.base.SecondaryPanelFragment
import com.oriondev.moneywallet.ui.fragment.secondary.PersonItemFragment
import com.oriondev.moneywallet.ui.view.AdvancedRecyclerView

/**
 * Created by andrea on 02/03/18.
 */
class PersonMultiPanelFragment : MultiPanelCursorListItemFragment(), PersonCursorAdapter.ActionListener {

    override fun onPrepareRecyclerView(recyclerView: AdvancedRecyclerView) {
        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.setEmptyText(R.string.message_no_person_found)
    }

    override fun onCreateAdapter(): AbstractCursorAdapter<*> {
        return PersonCursorAdapter(this)
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor>? {
        val act = activity
        if (act != null) {
            val uri: Uri = DataContentProvider.CONTENT_PEOPLE
            val projection = arrayOf(
                Contract.Person.ID,
                Contract.Person.NAME,
                Contract.Person.ICON
            )
            val sortOrder = "${Contract.Person.NAME} ASC"
            return CursorLoader(act, uri, projection, null, null, sortOrder)
        }
        return null
    }

    override fun onCreateSecondaryPanel(): SecondaryPanelFragment = PersonItemFragment()

    override fun getSecondaryFragmentTag(): String = SECONDARY_FRAGMENT_TAG

    override fun getTitleRes(): Int = R.string.menu_people

    override fun onPersonClick(id: Long) {
        if (isExtendedLayout()) {
            showPersonPanel(id)
        } else {
            val intent = Intent(activity, TransactionListActivity::class.java).apply {
                putExtra(TransactionListActivity.PERSON_ID, id)
            }
            startActivity(intent)
        }
    }

    override fun onPersonLongClick(id: Long) {
        showPersonPanel(id)
    }

    private fun showPersonPanel(id: Long) {
        showItemId(id)
        showSecondaryPanel()
    }

    override fun onFloatingActionButtonClick() {
        val intent = Intent(activity, NewEditPersonActivity::class.java).apply {
            putExtra(NewEditItemActivity.MODE, NewEditItemActivity.Mode.NEW_ITEM)
        }
        startActivity(intent)
    }

    companion object {
        private const val SECONDARY_FRAGMENT_TAG = "PersonMultiPanelFragment::Tag::SecondaryPanelFragment"
    }
}
