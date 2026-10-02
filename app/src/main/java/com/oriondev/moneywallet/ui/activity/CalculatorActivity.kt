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

package com.oriondev.moneywallet.ui.activity

import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.view.ActionMode
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.CurrencyUnit
import com.oriondev.moneywallet.ui.activity.base.SinglePanelActivity
import com.oriondev.moneywallet.ui.view.theme.ITheme
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog
import com.oriondev.moneywallet.utils.EquationSolver
import com.oriondev.moneywallet.utils.SystemBars
import kotlin.math.max
import kotlin.math.min

class CalculatorActivity : SinglePanelActivity(), View.OnClickListener, EquationSolver.Controller {

    private lateinit var displayEditText: EditText
    private var actionButton: Button? = null
    private lateinit var solver: EquationSolver

    private var keypadMode = false
    private var allowNegative = false
    private var rendering = false

    override fun getColorBehindNavigationBar(theme: ITheme): Int {
        return theme.colorPrimary
    }

    override fun onCreatePanelView(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?) {
        val view = inflater.inflate(R.layout.layout_panel_calculator, parent, true)
        
        SystemBars.pad(
            view.findViewById(R.id.keypad_container),
            false, resources.getBoolean(R.bool.panel_fills_window), true
        )
        
        displayEditText = view.findViewById(R.id.display_text_view)
        displayEditText.showSoftInputOnFocus = false
        displayEditText.filters = arrayOf(InputFilter { _, _, _, dest, dstart, dend ->
            if (rendering) null else dest.subSequence(dstart, dend)
        })
        displayEditText.isSaveEnabled = false
        displayEditText.customSelectionActionModeCallback = NO_TEXT_ACTION_MODE
        displayEditText.customInsertionActionModeCallback = NO_TEXT_ACTION_MODE
        displayEditText.requestFocus()
        
        actionButton = view.findViewById(R.id.keyboard_action_button)
        
        registerListener(view.findViewById(R.id.keyboard_00_button), OP_00)
        registerListener(view.findViewById(R.id.keyboard_0_button), OP_0)
        registerListener(view.findViewById(R.id.keyboard_1_button), OP_1)
        registerListener(view.findViewById(R.id.keyboard_2_button), OP_2)
        registerListener(view.findViewById(R.id.keyboard_3_button), OP_3)
        registerListener(view.findViewById(R.id.keyboard_4_button), OP_4)
        registerListener(view.findViewById(R.id.keyboard_5_button), OP_5)
        registerListener(view.findViewById(R.id.keyboard_6_button), OP_6)
        registerListener(view.findViewById(R.id.keyboard_7_button), OP_7)
        registerListener(view.findViewById(R.id.keyboard_8_button), OP_8)
        registerListener(view.findViewById(R.id.keyboard_9_button), OP_9)
        registerListener(view.findViewById(R.id.keyboard_clear_button), OP_CLEAR)
        registerListener(view.findViewById(R.id.keyboard_cancel_button), OP_CANCEL)
        registerListener(view.findViewById(R.id.keyboard_point_button), OP_POINT)
        registerListener(view.findViewById(R.id.keyboard_division_button), OP_DIVISION)
        registerListener(view.findViewById(R.id.keyboard_multiplication_button), OP_MULTIPLICATION)
        registerListener(view.findViewById(R.id.keyboard_addition_button), OP_ADDITION)
        registerListener(view.findViewById(R.id.keyboard_subtraction_button), OP_SUBTRACTION)
        registerListener(view.findViewById(R.id.keyboard_action_button), OP_EXECUTE)
        
        solver = EquationSolver(savedInstanceState, this)
        
        savedInstanceState?.let {
            setCursor(it.getInt(SS_CURSOR, displayEditText.length()))
        }
    }

    override fun onViewCreated(savedInstanceState: Bundle?) {
        val currentIntent = intent
        keypadMode = currentIntent.getIntExtra(ACTIVITY_MODE, MODE_CALCULATOR) == MODE_KEYPAD
        allowNegative = currentIntent.getBooleanExtra(ALLOW_NEGATIVE, false)
        
        if (savedInstanceState == null) {
            val currency: CurrencyUnit? = currentIntent.getParcelableExtra(CURRENCY)
            val money = currentIntent.getLongExtra(MONEY, 0L)
            solver.setValue(currency, money)
        }
        updateActionButton()
    }

    private fun registerListener(button: View, operation: String) {
        button.tag = operation
        button.setOnClickListener(this)
    }

    override fun getActivityTitleRes(): Int {
        return R.string.title_activity_calculator
    }

    override fun isFloatingActionButtonEnabled(): Boolean {
        return false
    }

    override fun onClick(button: View) {
        val operation = button.tag as? String ?: return
        when (operation) {
            OP_CLEAR -> solver.clear()
            OP_CANCEL -> setCursor(solver.backspace(displayEditText.selectionStart))
            OP_EXECUTE -> execute()
            OP_ADDITION -> solver.appendOperation(EquationSolver.Operation.ADDITION)
            OP_SUBTRACTION -> solver.appendOperation(EquationSolver.Operation.SUBTRACTION)
            OP_MULTIPLICATION -> solver.appendOperation(EquationSolver.Operation.MULTIPLICATION)
            OP_DIVISION -> solver.appendOperation(EquationSolver.Operation.DIVISION)
            OP_POINT -> setCursor(solver.insertPoint(displayEditText.selectionStart))
            else -> setCursor(solver.insertNumber(operation, displayEditText.selectionStart))
        }
    }

    private fun setCursor(cursor: Int) {
        displayEditText.setSelection(min(max(cursor, 0), displayEditText.length()))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        solver.onSaveInstanceState(outState)
        outState.putInt(SS_CURSOR, displayEditText.selectionStart)
    }

    private fun execute() {
        if (solver.isPendingOperation) {
            if (!solver.execute(true)) {
                // TODO: show error!
            }
        } else if (keypadMode) {
            val money = solver.result
            if (money < 0 && !allowNegative) {
                ThemedDialog.buildMaterialDialog(this)
                    .setTitle(R.string.title_error)
                    .setMessage(R.string.message_error_negative_amount)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                return
            }
            val resultIntent = Intent()
            resultIntent.putExtra(MONEY, money)
            setResult(RESULT_OK, resultIntent)
            finish()
        }
    }

    override fun onUpdateDisplay(text: String?) {
        rendering = true
        displayEditText.setText(text)
        rendering = false
        displayEditText.setSelection(text?.length ?: 0)
        updateActionButton()
    }

    private fun updateActionButton() {
        if (actionButton == null || !::solver.isInitialized) {
            return
        }
        val confirm = keypadMode && !solver.isPendingOperation
        if (confirm) {
            actionButton?.setText(R.string.keyboard_confirm)
            actionButton?.contentDescription = getString(R.string.description_calculator_confirm)
        } else {
            actionButton?.setText(R.string.keyboard_equal)
            actionButton?.contentDescription = getString(R.string.description_calculator_equals)
        }
    }

    companion object {
        const val ACTIVITY_MODE = "CalculatorActivity::Parameters::ActivityMode"
        const val CURRENCY = "CalculatorActivity::Parameters::Currency"
        const val MONEY = "CalculatorActivity::Parameters::Money"
        const val ALLOW_NEGATIVE = "CalculatorActivity::Parameters::AllowNegative"

        private const val SS_CURSOR = "CalculatorActivity::SavedState::Cursor"

        const val MODE_CALCULATOR = 0
        const val MODE_KEYPAD = 1

        private const val OP_00 = "00"
        private const val OP_0 = "0"
        private const val OP_1 = "1"
        private const val OP_2 = "2"
        private const val OP_3 = "3"
        private const val OP_4 = "4"
        private const val OP_5 = "5"
        private const val OP_6 = "6"
        private const val OP_7 = "7"
        private const val OP_8 = "8"
        private const val OP_9 = "9"
        private const val OP_POINT = "."
        private const val OP_CLEAR = "C"
        private const val OP_CANCEL = "B"
        private const val OP_ADDITION = "A"
        private const val OP_SUBTRACTION = "S"
        private const val OP_MULTIPLICATION = "M"
        private const val OP_DIVISION = "D"
        private const val OP_EXECUTE = "E"

        private val NO_TEXT_ACTION_MODE = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean = false
            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false
            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean = false
            override fun onDestroyActionMode(mode: ActionMode) {}
        }
    }
}
