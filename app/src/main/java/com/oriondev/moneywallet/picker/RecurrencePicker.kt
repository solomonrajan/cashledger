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
import com.oriondev.moneywallet.model.RecurrenceSetting
import com.oriondev.moneywallet.ui.fragment.dialog.RecurrencePickerDialog

/**
 * Created by andrea on 07/11/18.
 */
class RecurrencePicker : Fragment(), RecurrencePickerDialog.Callback {

    private var mController: Controller? = null

    private var mRecurrenceSetting: RecurrenceSetting? = null

    private var mChosen = false
    private var mEndTypeEnabled = false
    private var mOnceAPeriod = false

    private var mRecurrencePickerDialog: RecurrencePickerDialog? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mController = findController(Controller::class.java, context, checkParent = true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val arguments = arguments
        mEndTypeEnabled = arguments == null || arguments.getBoolean(ARG_END_TYPE_ENABLED, true)
        mOnceAPeriod = arguments != null && arguments.getBoolean(ARG_ONCE_A_PERIOD, false)
        if (savedInstanceState != null) {
            mRecurrenceSetting = savedInstanceState.readParcelable(SS_RECURRENCE_SETTING)
            mChosen = savedInstanceState.getBoolean(SS_CHOSEN, false)
        } else {
            if (arguments != null && arguments.containsKey(ARG_RECURRENCE_SETTING)) {
                mRecurrenceSetting = arguments.readParcelable(ARG_RECURRENCE_SETTING)
            } else {
                throw IllegalStateException(
                    "RecurrencePicker not initialized correctly. Please use RecurrencePicker.createPicker(...) instead."
                )
            }
        }
        mRecurrencePickerDialog = findOrCreateDialog(RecurrencePickerDialog::class.java) {
            RecurrencePickerDialog.newInstance()
        }
        mRecurrencePickerDialog?.setCallback(this)
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        fireCallbackSafely()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(SS_RECURRENCE_SETTING, mRecurrenceSetting)
        outState.putBoolean(SS_CHOSEN, mChosen)
    }

    /**
     * Whether the schedule the picker holds was chosen in its dialog, as opposed to the one it
     * was created with. A caller that would otherwise anchor the schedule somewhere of its own
     * asks this first, so that a choice already made is not quietly undone.
     */
    val isChosen: Boolean
        get() = mChosen

    private fun fireCallbackSafely() {
        mController?.onRecurrenceSettingChanged(tag, mRecurrenceSetting)
    }

    /**
     * Replace the schedule the picker holds without marking it as chosen in the dialog.
     */
    var currentSettings: RecurrenceSetting?
        get() = mRecurrenceSetting
        set(recurrenceSetting) {
            // The caller is seeding a schedule, not a user picking one, so keep isChosen unchanged.
            mRecurrenceSetting = recurrenceSetting
            fireCallbackSafely()
        }

    fun showPicker() {
        mRecurrencePickerDialog?.showPicker(
            childFragmentManager,
            getDefaultDialogTag(),
            mRecurrenceSetting,
            mEndTypeEnabled,
            mOnceAPeriod
        )
    }

    override fun onDetach() {
        super.onDetach()
        mController = null
    }

    override fun onRecurrenceSettingChanged(recurrenceSetting: RecurrenceSetting?) {
        mRecurrenceSetting = recurrenceSetting
        mChosen = true
        fireCallbackSafely()
    }

    interface Controller {
        fun onRecurrenceSettingChanged(tag: String?, recurrenceSetting: RecurrenceSetting?)
    }

    companion object {

        private const val SS_RECURRENCE_SETTING = "RecurrencePicker::SavedState::RecurrenceSetting"
        private const val SS_CHOSEN = "RecurrencePicker::SavedState::Chosen"

        private const val ARG_RECURRENCE_SETTING = "RecurrencePicker::Argument::RecurrenceSetting"
        private const val ARG_END_TYPE_ENABLED = "RecurrencePicker::Argument::EndTypeEnabled"
        private const val ARG_ONCE_A_PERIOD = "RecurrencePicker::Argument::OnceAPeriod"

        /**
         * @param endTypeEnabled false to hide the control that stops the recurrence after a date or a
         *                       number of times, for a caller whose recurrence can only run forever.
         * @param onceAPeriod    true to hide the weekday multi select, for a caller that reads the
         *                       stretch between one occurrence and the next as a length and so needs
         *                       every stretch to be the same.
         */
        @JvmOverloads
        @JvmStatic
        fun createPicker(
            fragmentManager: FragmentManager,
            tag: String?,
            recurrenceSetting: RecurrenceSetting?,
            endTypeEnabled: Boolean = true,
            onceAPeriod: Boolean = false
        ): RecurrencePicker {
            return fragmentManager.findOrCreatePicker(tag) {
                val arguments = Bundle()
                arguments.putParcelable(ARG_RECURRENCE_SETTING, recurrenceSetting)
                arguments.putBoolean(ARG_END_TYPE_ENABLED, endTypeEnabled)
                arguments.putBoolean(ARG_ONCE_A_PERIOD, onceAPeriod)
                val picker = RecurrencePicker()
                picker.arguments = arguments
                picker
            }
        }
    }
}
