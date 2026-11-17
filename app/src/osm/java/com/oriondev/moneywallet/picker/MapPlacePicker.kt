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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.oriondev.moneywallet.model.Place
import com.oriondev.moneywallet.ui.activity.PlacePickerActivity

/**
 * Created by andrea on 16/10/18.
 */
class MapPlacePicker : Fragment() {

    private var controller: Controller? = null

    var currentPlace: Place? = null
        set(value) {
            field = value
            fireCallbackSafely()
        }

    val isSelected: Boolean
        get() = currentPlace != null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is Controller) {
            controller = context
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialPlace: Place? = if (savedInstanceState != null) {
            savedInstanceState.getParcelable(SS_CURRENT_PLACE)
        } else {
            arguments?.getParcelable(ARG_DEFAULT_PLACE)
        }
        currentPlace = initialPlace
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        @Suppress("DEPRECATION")
        super.onActivityCreated(savedInstanceState)
        fireCallbackSafely()
    }

    fun fireCallbackSafely() {
        controller?.onMapPlaceChanged(tag, currentPlace)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(SS_CURRENT_PLACE, currentPlace)
    }

    fun setCurrentPlace(place: Place?) {
        currentPlace = place
    }

    fun getCurrentPlace(): Place? {
        return currentPlace
    }

    fun showPicker() {
        val intent = Intent(activity, PlacePickerActivity::class.java)
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_PLACE_PICKER)
    }

    override fun onDetach() {
        super.onDetach()
        controller = null
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_PLACE_PICKER) {
            val act = activity
            if (act != null && resultCode == Activity.RESULT_OK && data != null) {
                currentPlace = data.getParcelableExtra(PlacePickerActivity.RESULT_PLACE)
            }
        } else {
            @Suppress("DEPRECATION")
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    interface Controller {
        fun onMapPlaceChanged(tag: String?, place: Place?)
    }

    companion object {
        private const val SS_CURRENT_PLACE = "MapPlacePicker::SavedState::CurrentPlace"
        private const val ARG_DEFAULT_PLACE = "MapPlacePicker::Arguments::DefaultPlace"
        private const val REQUEST_PLACE_PICKER = 132

        @JvmStatic
        fun createPicker(fragmentManager: FragmentManager, tag: String, defaultPlace: Place?): MapPlacePicker {
            var mapPlacePicker = fragmentManager.findFragmentByTag(tag) as? MapPlacePicker
            if (mapPlacePicker == null) {
                mapPlacePicker = MapPlacePicker().apply {
                    val arguments = Bundle().apply {
                        putParcelable(ARG_DEFAULT_PLACE, defaultPlace)
                    }
                    setArguments(arguments)
                }
                fragmentManager.beginTransaction().add(mapPlacePicker, tag).commit()
            }
            return mapPlacePicker
        }
    }
}
