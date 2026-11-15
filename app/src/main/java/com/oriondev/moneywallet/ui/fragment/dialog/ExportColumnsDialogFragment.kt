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
import android.content.Context
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.storage.database.data.AbstractDataExporter
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog

/**
 * Created by andrea on 21/12/18.
 */
class ExportColumnsDialogFragment : DialogFragment() {

    private var mCallback: Callback? = null
    private var mIndices: Array<Int>? = null

    @Suppress("UNCHECKED_CAST")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val currentActivity = activity ?: return super.onCreateDialog(savedInstanceState)
        if (savedInstanceState != null) {
            mIndices = savedInstanceState.getSerializable(SS_CURRENT_INDICES) as? Array<Int>
        }
        val items = Array(COLUMN_NAMES.size) { i -> getString(COLUMN_NAMES[i]) }
        val checked = BooleanArray(items.size)
        val indices = mIndices
        if (indices != null) {
            for (index in indices) {
                if (index in checked.indices) {
                    checked[index] = true
                }
            }
        }
        return ThemedDialog.buildMaterialDialog(currentActivity)
            .setTitle(R.string.dialog_export_optional_columns_title)
            .setMultiChoiceItems(items, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val selectedIndices = mutableListOf<Int>()
                for (i in checked.indices) {
                    if (checked[i]) {
                        selectedIndices.add(i)
                    }
                }
                val selectedArray = selectedIndices.toTypedArray()
                mIndices = selectedArray
                mCallback?.onExportColumnsSelected(selectedArray)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable(SS_CURRENT_INDICES, mIndices)
    }

    fun setCallback(callback: Callback?) {
        mCallback = callback
    }

    fun showPicker(fragmentManager: FragmentManager, tag: String?, indices: Array<Int>?) {
        mIndices = indices
        show(fragmentManager, tag)
    }

    interface Callback {
        fun onExportColumnsSelected(indices: Array<Int>?)
    }

    companion object {
        private const val SS_CURRENT_INDICES = "ExportColumnsDialogFragment::SavedState::CurrentIndices"

        private val COLUMN_NAMES = intArrayOf(
            R.string.hint_event,
            R.string.hint_people,
            R.string.hint_place,
            R.string.hint_note
        )

        private val SERVICE_COLUMNS = arrayOf(
            AbstractDataExporter.COLUMN_EVENT,
            AbstractDataExporter.COLUMN_PEOPLE,
            AbstractDataExporter.COLUMN_PLACE,
            AbstractDataExporter.COLUMN_NOTE
        )

        @JvmStatic
        fun newInstance(): ExportColumnsDialogFragment {
            return ExportColumnsDialogFragment()
        }

        @JvmStatic
        fun getColumnNameFromIndices(context: Context, indices: Array<Int>?): Array<String>? {
            if (indices != null) {
                return Array(indices.size) { i ->
                    context.getString(COLUMN_NAMES[indices[i]])
                }
            }
            return null
        }

        @JvmStatic
        fun getCursorColumnsFromIndices(indices: Array<Int>?): Array<String>? {
            if (indices != null) {
                return Array(indices.size) { i ->
                    SERVICE_COLUMNS[indices[i]]
                }
            }
            return null
        }
    }
}
