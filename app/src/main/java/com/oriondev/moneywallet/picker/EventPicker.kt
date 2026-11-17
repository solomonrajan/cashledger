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
import com.oriondev.moneywallet.model.Event
import com.oriondev.moneywallet.ui.fragment.dialog.EventPickerDialog
import java.util.Date

/**
 * Created by andrea on 10/03/18.
 */
class EventPicker : Fragment(), EventPickerDialog.Callback {

    private var mController: Controller? = null

    private var mCurrentEvent: Event? = null

    private var mEventPickerDialog: EventPickerDialog? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mController = findController(Controller::class.java, context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mCurrentEvent = when {
            savedInstanceState != null -> savedInstanceState.readParcelable(SS_CURRENT_EVENT)
            arguments != null -> arguments.readParcelable(ARG_DEFAULT_EVENT)
            else -> null
        }
        mEventPickerDialog = findOrCreateDialog(EventPickerDialog::class.java) { EventPickerDialog.newInstance() }
        mEventPickerDialog?.setCallback(this)
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        fireCallbackSafely()
    }

    private fun fireCallbackSafely() {
        mController?.onEventChanged(tag, mCurrentEvent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(SS_CURRENT_EVENT, mCurrentEvent)
    }

    val isSelected: Boolean
        get() = mCurrentEvent != null

    var currentEvent: Event?
        get() = mCurrentEvent
        set(event) {
            mCurrentEvent = event
            fireCallbackSafely()
        }

    fun showPicker(filterDate: Date?) {
        mEventPickerDialog?.showPicker(childFragmentManager, getDefaultDialogTag(), mCurrentEvent, filterDate)
    }

    override fun onDetach() {
        super.onDetach()
        mController = null
    }

    override fun onEventSelected(event: Event?) {
        mCurrentEvent = event
        fireCallbackSafely()
    }

    interface Controller {
        fun onEventChanged(tag: String?, event: Event?)
    }

    companion object {

        private const val SS_CURRENT_EVENT = "EventPicker::SavedState::CurrentEvent"
        private const val ARG_DEFAULT_EVENT = "EventPicker::Arguments::DefaultEvent"

        @JvmStatic
        fun createPicker(
            fragmentManager: FragmentManager,
            tag: String?,
            defaultEvent: Event?
        ): EventPicker {
            return fragmentManager.findOrCreatePicker(tag) {
                val arguments = Bundle()
                arguments.putParcelable(ARG_DEFAULT_EVENT, defaultEvent)
                val picker = EventPicker()
                picker.arguments = arguments
                picker
            }
        }
    }
}
