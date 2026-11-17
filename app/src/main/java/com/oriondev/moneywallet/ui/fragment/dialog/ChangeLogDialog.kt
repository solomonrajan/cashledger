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

package com.oriondev.moneywallet.ui.fragment.dialog

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.loader.app.LoaderManager
import androidx.loader.content.Loader
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.background.ChangeLogLoader
import com.oriondev.moneywallet.model.ChangeLog
import com.oriondev.moneywallet.ui.adapter.recycler.ChangeLogAdapter
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog

/**
 * Created by andrea on 06/04/18.
 */
class ChangeLogDialog : DialogFragment(), LoaderManager.LoaderCallbacks<List<ChangeLog>> {

    private var mAdapter: ChangeLogAdapter? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val currentActivity = activity ?: return super.onCreateDialog(savedInstanceState)
        val adapter = ChangeLogAdapter()
        mAdapter = adapter
        val recyclerView = layoutInflater.inflate(R.layout.dialog_recycler_view, null) as RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(currentActivity)
        recyclerView.adapter = adapter
        val builder = ThemedDialog.buildMaterialDialog(currentActivity)
            .setTitle(R.string.dialog_change_log_title)
            .setPositiveButton(android.R.string.ok, null)
            .setView(recyclerView)
        LoaderManager.getInstance(this).restartLoader(LOADER_ID, null, this)
        return builder.create()
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<List<ChangeLog>> {
        return ChangeLogLoader(requireContext())
    }

    override fun onLoadFinished(loader: Loader<List<ChangeLog>>, data: List<ChangeLog>?) {
        mAdapter?.setChangeLogs(data)
    }

    override fun onLoaderReset(loader: Loader<List<ChangeLog>>) {
        mAdapter?.setChangeLogs(null)
    }

    companion object {
        private const val LOADER_ID = 3647

        @JvmStatic
        fun showSafely(fragmentManager: FragmentManager, tag: String?) {
            val dialog = fragmentManager.findFragmentByTag(tag) as? ChangeLogDialog
            if (dialog == null) {
                val newDialog = ChangeLogDialog()
                newDialog.show(fragmentManager, tag)
            } else {
                fragmentManager.beginTransaction().show(dialog).commit()
            }
        }
    }
}
