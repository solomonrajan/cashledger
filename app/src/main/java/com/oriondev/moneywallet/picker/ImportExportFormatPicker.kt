/*
 * Copyright (c) 2018.
 * Copyright (c) 2026 solomonrajan.
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

package com.oriondev.moneywallet.picker

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.oriondev.moneywallet.model.DataFormat
import com.oriondev.moneywallet.ui.fragment.dialog.DataFormatPickerDialog

/**
 * Created by andrea on 12/03/18.
 */
class ImportExportFormatPicker : Fragment(), DataFormatPickerDialog.Callback {

    private var mController: Controller? = null

    private var mCurrentFormat: DataFormat? = null

    private var mDataFormatPickerDialog: DataFormatPickerDialog? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mController = findController(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mCurrentFormat = savedInstanceState.readSerializable(SS_CURRENT_FORMAT)
        mDataFormatPickerDialog = findOrCreateDialog(DataFormatPickerDialog::class.java) {
            DataFormatPickerDialog.newInstance()
        }
        mDataFormatPickerDialog?.setCallback(this)
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        fireCallbackSafely()
    }

    private fun fireCallbackSafely() {
        mController?.onFormatChanged(tag, mCurrentFormat)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable(SS_CURRENT_FORMAT, mCurrentFormat)
    }

    val isSelected: Boolean
        get() = mCurrentFormat != null

    var currentFormat: DataFormat?
        get() = mCurrentFormat
        set(dataFormat) {
            mCurrentFormat = dataFormat
            fireCallbackSafely()
        }

    fun showPicker(dataFormats: Array<DataFormat>) {
        val index = dataFormats.indexOf(mCurrentFormat)
        mDataFormatPickerDialog?.showPicker(childFragmentManager, getDefaultDialogTag(), dataFormats, index)
    }

    override fun onDetach() {
        super.onDetach()
        mController = null
    }

    override fun onDataFormatSelected(dataFormat: DataFormat?) {
        mCurrentFormat = dataFormat
        fireCallbackSafely()
    }

    interface Controller {
        fun onFormatChanged(tag: String?, format: DataFormat?)
    }

    companion object {

        private const val SS_CURRENT_FORMAT = "ImportExportFormatPicker::SavedState::CurrentFormat"

        @JvmStatic
        fun createPicker(
            fragmentManager: FragmentManager,
            tag: String?
        ): ImportExportFormatPicker {
            return fragmentManager.findOrCreatePicker(tag) { ImportExportFormatPicker() }
        }
    }
}
