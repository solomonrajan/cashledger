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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.DataFormat
import com.oriondev.moneywallet.ui.adapter.recycler.DataFormatSelectorAdapter
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog

/**
 * Created by andrea on 20/12/18.
 */
class DataFormatPickerDialog : DialogFragment(), DataFormatSelectorAdapter.Controller {

    private var mCallback: Callback? = null
    private var mDataFormats: Array<DataFormat>? = null
    private var mIndex: Int = -1

    @Suppress("UNCHECKED_CAST")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val currentActivity = activity ?: return super.onCreateDialog(savedInstanceState)
        if (savedInstanceState != null) {
            mDataFormats = savedInstanceState.getSerializable(SS_DATA_FORMATS) as? Array<DataFormat>
            mIndex = savedInstanceState.getInt(SS_CURRENT_INDEX, -1)
        }
        val recyclerView = layoutInflater.inflate(R.layout.dialog_recycler_view, null) as RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(currentActivity)
        recyclerView.adapter = DataFormatSelectorAdapter(mDataFormats, this)
        return ThemedDialog.buildMaterialDialog(currentActivity)
            .setTitle(R.string.dialog_data_format_picker_title)
            .setView(recyclerView)
            .show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable(SS_DATA_FORMATS, mDataFormats)
        outState.putInt(SS_CURRENT_INDEX, mIndex)
    }

    fun setCallback(callback: Callback?) {
        mCallback = callback
    }

    fun showPicker(fragmentManager: FragmentManager, tag: String?, dataFormats: Array<DataFormat>?, index: Int) {
        mDataFormats = dataFormats
        mIndex = index
        show(fragmentManager, tag)
    }

    override fun onDataFormatSelected(dataFormat: DataFormat) {
        mCallback?.onDataFormatSelected(dataFormat)
        dismiss()
    }

    override fun isDataFormatSelected(dataFormat: DataFormat): Boolean {
        val formats = mDataFormats ?: return false
        return mIndex in formats.indices && formats[mIndex] == dataFormat
    }

    interface Callback {
        fun onDataFormatSelected(dataFormat: DataFormat?)
    }

    companion object {
        private const val SS_DATA_FORMATS = "DataFormatPickerDialog::SavedState::DataFormats"
        private const val SS_CURRENT_INDEX = "DataFormatPickerDialog::SavedState::CurrentIndex"

        @JvmStatic
        fun newInstance(): DataFormatPickerDialog {
            return DataFormatPickerDialog()
        }
    }
}
