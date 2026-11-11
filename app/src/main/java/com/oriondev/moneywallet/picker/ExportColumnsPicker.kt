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
import com.oriondev.moneywallet.ui.fragment.dialog.ExportColumnsDialogFragment

/**
 * Created by andrea on 21/12/18.
 */
class ExportColumnsPicker : Fragment(), ExportColumnsDialogFragment.Callback {

    private var mController: Controller? = null

    private var mCurrentIndices: Array<Int>? = null

    private var mColumnsPickerDialog: ExportColumnsDialogFragment? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mController = findController(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            @Suppress("DEPRECATION")
            val raw = savedInstanceState.getSerializable(SS_CURRENT_INDICES)
            mCurrentIndices = if (raw is Array<*>) raw.filterIsInstance<Int>().toTypedArray() else null
        } else {
            mCurrentIndices = emptyArray()
        }
        mColumnsPickerDialog = findOrCreateDialog { ExportColumnsDialogFragment.newInstance() }
        mColumnsPickerDialog?.setCallback(this)
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        fireCallbackSafely()
    }

    private fun fireCallbackSafely() {
        val controller = mController ?: return
        val columnNames = ExportColumnsDialogFragment.getColumnNameFromIndices(activity, mCurrentIndices)
        controller.onExportColumnsChanged(tag, columnNames)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable(SS_CURRENT_INDICES, mCurrentIndices)
    }

    val currentServiceColumns: Array<String>?
        get() = ExportColumnsDialogFragment.getCursorColumnsFromIndices(mCurrentIndices)

    fun showPicker() {
        mColumnsPickerDialog?.showPicker(childFragmentManager, getDefaultDialogTag(), mCurrentIndices)
    }

    override fun onDetach() {
        super.onDetach()
        mController = null
    }

    override fun onExportColumnsSelected(indices: Array<Int>?) {
        mCurrentIndices = indices
        fireCallbackSafely()
    }

    interface Controller {
        fun onExportColumnsChanged(tag: String?, columns: Array<String>?)
    }

    companion object {

        private const val SS_CURRENT_INDICES = "ExportColumnsPicker::SavedState::CurrentIndices"

        @JvmStatic
        fun createPicker(fragmentManager: FragmentManager, tag: String?): ExportColumnsPicker {
            return fragmentManager.findOrCreatePicker(tag) { ExportColumnsPicker() }
        }
    }
}
