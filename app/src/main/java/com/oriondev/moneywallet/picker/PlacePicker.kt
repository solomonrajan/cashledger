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
import com.oriondev.moneywallet.model.Place
import com.oriondev.moneywallet.ui.fragment.dialog.PlacePickerDialog

/**
 * Created by andrea on 10/03/18.
 */
class PlacePicker : Fragment(), PlacePickerDialog.Callback {

    private var mController: Controller? = null

    private var mCurrentPlace: Place? = null

    private var mPlacePickerDialog: PlacePickerDialog? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mController = findController(Controller::class.java, context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mCurrentPlace = when {
            savedInstanceState != null -> savedInstanceState.readParcelable(SS_CURRENT_PLACE)
            arguments != null -> arguments.readParcelable(ARG_DEFAULT_PLACE)
            else -> null
        }
        mPlacePickerDialog = findOrCreateDialog(PlacePickerDialog::class.java) { PlacePickerDialog.newInstance() }
        mPlacePickerDialog?.setCallback(this)
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        fireCallbackSafely()
    }

    private fun fireCallbackSafely() {
        mController?.onPlaceChanged(tag, mCurrentPlace)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(SS_CURRENT_PLACE, mCurrentPlace)
    }

    val isSelected: Boolean
        get() = mCurrentPlace != null

    var currentPlace: Place?
        get() = mCurrentPlace
        set(place) {
            mCurrentPlace = place
            fireCallbackSafely()
        }

    fun showPicker() {
        mPlacePickerDialog?.showPicker(childFragmentManager, getDefaultDialogTag(), mCurrentPlace)
    }

    override fun onDetach() {
        super.onDetach()
        mController = null
    }

    override fun onPlaceSelected(place: Place?) {
        mCurrentPlace = place
        fireCallbackSafely()
    }

    interface Controller {
        fun onPlaceChanged(tag: String?, place: Place?)
    }

    companion object {

        private const val SS_CURRENT_PLACE = "PlacePicker::SavedState::CurrentPlace"
        private const val ARG_DEFAULT_PLACE = "PlacePicker::Arguments::DefaultPlace"

        @JvmStatic
        fun createPicker(
            fragmentManager: FragmentManager,
            tag: String?,
            defaultPlace: Place?
        ): PlacePicker {
            return fragmentManager.findOrCreatePicker(tag) {
                val arguments = Bundle()
                arguments.putParcelable(ARG_DEFAULT_PLACE, defaultPlace)
                val picker = PlacePicker()
                picker.arguments = arguments
                picker
            }
        }
    }
}
