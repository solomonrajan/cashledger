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
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.ui.fragment.dialog.BudgetTypePickerDialog

/**
 * Created by andrea on 12/03/18.
 */
class BudgetTypePicker : Fragment(), BudgetTypePickerDialog.Callback {

    private var mController: Controller? = null

    private var mCurrentType: Contract.BudgetType? = null

    private var mBudgetTypePickerDialog: BudgetTypePickerDialog? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mController = findController(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mCurrentType = when {
            savedInstanceState != null -> savedInstanceState.readSerializable(SS_CURRENT_TYPE)
            arguments != null -> arguments.readSerializable(ARG_DEFAULT_TYPE)
            else -> Contract.BudgetType.EXPENSES
        }
        mBudgetTypePickerDialog = findOrCreateDialog { BudgetTypePickerDialog.newInstance() }
        mBudgetTypePickerDialog?.setCallback(this)
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        fireCallbackSafely()
    }

    private fun fireCallbackSafely() {
        mController?.onTypeChanged(tag, mCurrentType)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable(SS_CURRENT_TYPE, mCurrentType)
    }

    val isSelected: Boolean
        get() = mCurrentType != null

    val currentType: Contract.BudgetType?
        get() = mCurrentType

    fun showPicker() {
        mBudgetTypePickerDialog?.showPicker(childFragmentManager, getDefaultDialogTag(), mCurrentType)
    }

    override fun onDetach() {
        super.onDetach()
        mController = null
    }

    override fun onBudgetTypeSelected(budgetType: Contract.BudgetType?) {
        mCurrentType = budgetType
        fireCallbackSafely()
    }

    interface Controller {
        fun onTypeChanged(tag: String?, type: Contract.BudgetType?)
    }

    companion object {

        private const val SS_CURRENT_TYPE = "BudgetTypePicker::SavedState::CurrentType"
        private const val ARG_DEFAULT_TYPE = "BudgetTypePicker::Arguments::DefaultType"

        @JvmStatic
        fun createPicker(
            fragmentManager: FragmentManager,
            tag: String?,
            defaultType: Contract.BudgetType?
        ): BudgetTypePicker {
            return fragmentManager.findOrCreatePicker(tag) {
                val arguments = Bundle()
                arguments.putSerializable(ARG_DEFAULT_TYPE, defaultType)
                val picker = BudgetTypePicker()
                picker.arguments = arguments
                picker
            }
        }
    }
}
