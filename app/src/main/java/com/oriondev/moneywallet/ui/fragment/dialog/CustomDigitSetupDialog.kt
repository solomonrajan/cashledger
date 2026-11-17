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
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.DialogFragment
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog
import com.oriondev.moneywallet.utils.CurrencyManager
import com.oriondev.moneywallet.utils.MoneyFormatter

/**
 * Created by andrea on 29/07/18.
 */
class CustomDigitSetupDialog : DialogFragment() {

    private var mDisplayTextView: TextView? = null
    private var mFormatter: MoneyFormatter = MoneyFormatter.getInstance()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val currentActivity = activity ?: return super.onCreateDialog(savedInstanceState)
        val view = layoutInflater.inflate(R.layout.layout_dialog_custom_digit_setup, null)
        val dialog = ThemedDialog.buildMaterialDialog(currentActivity)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                onSaveChanges()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        mDisplayTextView = view.findViewById(R.id.display_text_view)
        val currencyEnabledSwitch: SwitchCompat = view.findViewById(R.id.show_currency_switch)
        val groupingEnabledSwitch: SwitchCompat = view.findViewById(R.id.group_digits_switch)
        val roundingEnabledSwitch: SwitchCompat = view.findViewById(R.id.round_decimals_switch)
        val symbolEnabledSwitch: SwitchCompat = view.findViewById(R.id.show_always_plus_minus_switch)

        // setup formatter and load default or saved settings
        mFormatter = MoneyFormatter.getInstance()
        if (savedInstanceState != null) {
            mFormatter.isCurrencyEnabled = savedInstanceState.getBoolean(SS_CURRENCY_ENABLED)
            mFormatter.isGroupDigitEnabled = savedInstanceState.getBoolean(SS_GROUPING_ENABLED)
            mFormatter.isRoundDecimalsEnabled = savedInstanceState.getBoolean(SS_ROUNDING_ENABLED)
            mFormatter.isShowSymbolEnabled = savedInstanceState.getBoolean(SS_SYMBOL_ENABLED)
        }
        currencyEnabledSwitch.isChecked = mFormatter.isCurrencyEnabled
        groupingEnabledSwitch.isChecked = mFormatter.isGroupDigitEnabled
        roundingEnabledSwitch.isChecked = mFormatter.isRoundDecimalsEnabled
        symbolEnabledSwitch.isChecked = mFormatter.isShowSymbolEnabled

        // attach listeners
        currencyEnabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            mFormatter.isCurrencyEnabled = isChecked
            refreshDisplay()
        }
        groupingEnabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            mFormatter.isGroupDigitEnabled = isChecked
            refreshDisplay()
        }
        roundingEnabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            mFormatter.isRoundDecimalsEnabled = isChecked
            refreshDisplay()
        }
        symbolEnabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            mFormatter.isShowSymbolEnabled = isChecked
            refreshDisplay()
        }

        // refresh the current display
        refreshDisplay()
        return dialog
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(SS_CURRENCY_ENABLED, mFormatter.isCurrencyEnabled)
        outState.putBoolean(SS_GROUPING_ENABLED, mFormatter.isGroupDigitEnabled)
        outState.putBoolean(SS_ROUNDING_ENABLED, mFormatter.isRoundDecimalsEnabled)
        outState.putBoolean(SS_SYMBOL_ENABLED, mFormatter.isShowSymbolEnabled)
    }

    private fun refreshDisplay() {
        val textView = mDisplayTextView ?: return
        mFormatter.applyNotTinted(textView, CurrencyManager.getDefaultCurrency(), DEFAULT_MONEY)
    }

    private fun onSaveChanges() {
        PreferenceManager.setCurrencyEnabled(mFormatter.isCurrencyEnabled)
        PreferenceManager.setGroupDigitsEnabled(mFormatter.isGroupDigitEnabled)
        PreferenceManager.setRoundDecimalsEnabled(mFormatter.isRoundDecimalsEnabled)
        PreferenceManager.setShowPlusMinusSymbolEnabled(mFormatter.isShowSymbolEnabled)
    }

    companion object {
        private const val SS_CURRENCY_ENABLED = "CustomDigitSetupDialog::SavedState::CurrencyEnabled"
        private const val SS_GROUPING_ENABLED = "CustomDigitSetupDialog::SavedState::GroupingEnabled"
        private const val SS_ROUNDING_ENABLED = "CustomDigitSetupDialog::SavedState::RoundingEnabled"
        private const val SS_SYMBOL_ENABLED = "CustomDigitSetupDialog::SavedState::SymbolEnabled"

        private const val DEFAULT_MONEY = 1258972L

        @JvmStatic
        fun newInstance(): CustomDigitSetupDialog {
            return CustomDigitSetupDialog()
        }
    }
}
