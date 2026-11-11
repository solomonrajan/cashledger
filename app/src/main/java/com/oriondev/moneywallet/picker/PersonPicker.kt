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
import com.oriondev.moneywallet.model.Person
import com.oriondev.moneywallet.ui.fragment.dialog.PeoplePickerDialog

/**
 * Created by andrea on 10/03/18.
 */
class PersonPicker : Fragment(), PeoplePickerDialog.Callback {

    private var mController: Controller? = null

    private var mCurrentPeople: Array<Person>? = null

    private var mPeoplePickerDialog: PeoplePickerDialog? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mController = findController(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        mCurrentPeople = when {
            savedInstanceState != null ->
                savedInstanceState.getParcelableArray(SS_CURRENT_PEOPLE).toTypedParcelableArray()
            arguments != null ->
                arguments?.getParcelableArray(ARG_DEFAULT_PEOPLE).toTypedParcelableArray()
            else -> null
        }
        mPeoplePickerDialog = findOrCreateDialog { PeoplePickerDialog.newInstance() }
        mPeoplePickerDialog?.setCallback(this)
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        fireCallbackSafely()
    }

    private fun fireCallbackSafely() {
        mController?.onPeopleChanged(tag, mCurrentPeople)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelableArray(SS_CURRENT_PEOPLE, mCurrentPeople)
    }

    val isSelected: Boolean
        get() = !mCurrentPeople.isNullOrEmpty()

    fun setPeople(people: Array<Person>?) {
        mCurrentPeople = people
        fireCallbackSafely()
    }

    val currentPeople: Array<Person>?
        get() = mCurrentPeople

    fun showPicker() {
        mPeoplePickerDialog?.showPicker(childFragmentManager, getDefaultDialogTag(), mCurrentPeople)
    }

    override fun onDetach() {
        super.onDetach()
        mController = null
    }

    override fun onPeopleSelected(people: Array<Person>?) {
        mCurrentPeople = people
        fireCallbackSafely()
    }

    interface Controller {
        fun onPeopleChanged(tag: String?, people: Array<Person>?)
    }

    companion object {

        private const val SS_CURRENT_PEOPLE = "PersonPicker::SavedState::CurrentPeople"
        private const val ARG_DEFAULT_PEOPLE = "PersonPicker::Arguments::DefaultPeople"

        @JvmStatic
        fun createPicker(
            fragmentManager: FragmentManager,
            tag: String?,
            defaultPeople: Array<Person>?
        ): PersonPicker {
            return fragmentManager.findOrCreatePicker(tag) {
                val arguments = Bundle()
                arguments.putParcelableArray(ARG_DEFAULT_PEOPLE, defaultPeople)
                val picker = PersonPicker()
                picker.arguments = arguments
                picker
            }
        }
    }
}
