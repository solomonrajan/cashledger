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

package com.oriondev.moneywallet.ui.activity

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.ViewGroup
import androidx.annotation.MenuRes
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.Place
import com.oriondev.moneywallet.ui.activity.base.SinglePanelActivity
import com.oriondev.moneywallet.view.MapViewWrapper

/**
 * Created by andrea on 02/11/18.
 */
class PlacePickerActivity : SinglePanelActivity() {

    private var mapView: MapViewWrapper? = null

    override fun onCreatePanelView(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?) {
        val view = inflater.inflate(R.layout.layout_panel_place_picker, parent, true)
        val wrapper = MapViewWrapper(view.findViewById(R.id.map_view)).apply {
            // This map fills the panel down to the bottom of the window.
            keepCopyrightClearOfSystemBars()
            onCreate(savedInstanceState)
            setMinZoomLevel()
        }
        mapView = wrapper
    }

    override fun onStart() {
        super.onStart()
        mapView?.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView?.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView?.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView?.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }

    override fun getActivityTitleRes(): Int {
        return R.string.title_activity_map
    }

    @MenuRes
    override fun onInflateMenu(): Int {
        return R.menu.menu_select_place
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_select_place) {
            val wrapper = mapView
            if (wrapper != null && wrapper.isMapReady()) {
                val coordinates = wrapper.getCenterCoordinates()
                val place = Place(0L, null, null, null, coordinates)
                val intent = Intent().apply {
                    putExtra(RESULT_PLACE, place)
                }
                setResult(RESULT_OK, intent)
                finish()
            }
        }
        return false
    }

    override fun isFloatingActionButtonEnabled(): Boolean {
        return false
    }

    companion object {
        const val RESULT_PLACE: String = "PlacePickerActivity::Result::Place"
    }
}
