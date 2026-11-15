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
import com.oriondev.moneywallet.background.LicenseLoader
import com.oriondev.moneywallet.model.License
import com.oriondev.moneywallet.ui.adapter.recycler.LicenseAdapter
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog

/**
 * Created by andrea on 06/04/18.
 */
class LicenseDialog : DialogFragment(), LoaderManager.LoaderCallbacks<List<License>>, LicenseAdapter.Controller {

    private var mCallback: Callback? = null
    private var mAdapter: LicenseAdapter? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val currentActivity = activity ?: return super.onCreateDialog(savedInstanceState)
        val adapter = LicenseAdapter(this)
        mAdapter = adapter
        val recyclerView = layoutInflater.inflate(R.layout.dialog_recycler_view, null) as RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(currentActivity)
        recyclerView.adapter = adapter
        val builder = ThemedDialog.buildMaterialDialog(currentActivity)
            .setTitle(R.string.title_licenses)
            .setView(recyclerView)
            .setPositiveButton(android.R.string.ok, null)
        LoaderManager.getInstance(this).restartLoader(LOADER_ID, null, this)
        return builder.create()
    }

    fun setCallback(callback: Callback?) {
        mCallback = callback
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<List<License>> {
        return LicenseLoader(requireContext())
    }

    override fun onLoadFinished(loader: Loader<List<License>>, data: List<License>?) {
        mAdapter?.setLicenses(data)
    }

    override fun onLoaderReset(loader: Loader<List<License>>) {
        mAdapter?.setLicenses(null)
    }

    override fun onLicenseClick(license: License) {
        mCallback?.onLicenseClick(license)
    }

    interface Callback {
        fun onLicenseClick(license: License)
    }

    companion object {
        private const val LOADER_ID = 3698

        @JvmStatic
        fun showSafely(fragmentManager: FragmentManager, tag: String?, callback: Callback?) {
            val dialog = fragmentManager.findFragmentByTag(tag) as? LicenseDialog
            if (dialog == null) {
                val newDialog = LicenseDialog()
                newDialog.setCallback(callback)
                newDialog.show(fragmentManager, tag)
            } else {
                dialog.setCallback(callback)
                fragmentManager.beginTransaction().show(dialog).commit()
            }
        }
    }
}
