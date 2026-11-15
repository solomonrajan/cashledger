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
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.ui.adapter.recycler.BudgetTypeSelectorAdapter
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog

/**
 * Created by andre on 24/03/2018.
 */
class BudgetTypePickerDialog : DialogFragment(), BudgetTypeSelectorAdapter.Controller {

    private var mCallback: Callback? = null
    private var mBudgetType: Contract.BudgetType? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val currentActivity = activity ?: return super.onCreateDialog(savedInstanceState)
        if (savedInstanceState != null) {
            mBudgetType = savedInstanceState.getSerializable(SS_SELECTED_BUDGET_TYPE) as? Contract.BudgetType
        }
        val recyclerView = layoutInflater.inflate(R.layout.dialog_recycler_view, null) as RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(currentActivity)
        recyclerView.adapter = BudgetTypeSelectorAdapter(this)
        return ThemedDialog.buildMaterialDialog(currentActivity)
            .setTitle(R.string.dialog_budget_type_picker_title)
            .setView(recyclerView)
            .show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable(SS_SELECTED_BUDGET_TYPE, mBudgetType)
    }

    fun setCallback(callback: Callback?) {
        mCallback = callback
    }

    fun showPicker(fragmentManager: FragmentManager, tag: String?, budgetType: Contract.BudgetType?) {
        mBudgetType = budgetType
        show(fragmentManager, tag)
    }

    override fun onBudgetTypeSelected(budgetType: Contract.BudgetType) {
        mCallback?.onBudgetTypeSelected(budgetType)
        dismiss()
    }

    override fun isBudgetTypeSelected(budgetType: Contract.BudgetType): Boolean {
        return mBudgetType == budgetType
    }

    interface Callback {
        fun onBudgetTypeSelected(budgetType: Contract.BudgetType?)
    }

    companion object {
        private const val SS_SELECTED_BUDGET_TYPE = "BudgetTypePickerDialog::SavedState::CurrentBudgetType"

        @JvmStatic
        fun newInstance(): BudgetTypePickerDialog {
            return BudgetTypePickerDialog()
        }
    }
}
