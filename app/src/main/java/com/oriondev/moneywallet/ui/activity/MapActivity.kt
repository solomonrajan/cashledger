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
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.loader.app.LoaderManager
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.Place
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.ui.activity.base.SinglePanelActivity
import com.oriondev.moneywallet.view.MapViewWrapper
import java.util.ArrayList

/**
 * Created by andrea on 04/04/18.
 */
class MapActivity : SinglePanelActivity(),
    LoaderManager.LoaderCallbacks<Cursor>,
    MapViewWrapper.OnMapLoadedCallback,
    MapViewWrapper.OnInfoWindowClickListener {

    private var mMapView: MapViewWrapper? = null
    private var mPlacesDelivered = false

    override fun onCreatePanelView(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?) {
        val view = inflater.inflate(R.layout.layout_panel_map, parent, true)
        val mapView = MapViewWrapper(view.findViewById(R.id.map_view))
        mMapView = mapView
        // This map fills the panel down to the bottom of the window.
        mapView.keepCopyrightClearOfSystemBars()
        mapView.onCreate(savedInstanceState)
        mapView.loadMapAsync(this)
    }

    override fun onStart() {
        super.onStart()
        mMapView?.onStart()
    }

    override fun onResume() {
        super.onResume()
        mMapView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        mMapView?.onPause()
    }

    override fun onStop() {
        super.onStop()
        mMapView?.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        mMapView?.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mMapView?.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mMapView?.onLowMemory()
    }

    @StringRes
    override fun getActivityTitleRes(): Int = R.string.title_activity_map

    override fun isFloatingActionButtonEnabled(): Boolean = false

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor> {
        val uri: Uri = DataContentProvider.CONTENT_PLACES
        val projection = arrayOf(
            Contract.Place.ID,
            Contract.Place.NAME,
            Contract.Place.ICON,
            Contract.Place.ADDRESS,
            Contract.Place.LATITUDE,
            Contract.Place.LONGITUDE
        )
        val selection = "${Contract.Place.LATITUDE} IS NOT NULL AND ${Contract.Place.LONGITUDE} IS NOT NULL"
        return CursorLoader(this, uri, projection, selection, null, null)
    }

    override fun onLoadFinished(loader: Loader<Cursor>, cursor: Cursor?) {
        // The framework manager delivered to this screen once. The AndroidX one delivers again
        // every time the screen is started and after writes elsewhere in the app, and addPlaces
        // stacks a second overlay on the first and recenters the map.
        if (mPlacesDelivered) {
            return
        }
        mPlacesDelivered = true
        val mapView = mMapView
        if (cursor != null && mapView != null) {
            if (mapView.isMapReady && cursor.moveToFirst()) {
                val placeList: MutableList<Place> = ArrayList()
                do {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Place.ID))
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Place.NAME))
                    val address = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Place.ADDRESS))
                    val latitude = cursor.getDouble(cursor.getColumnIndexOrThrow(Contract.Place.LATITUDE))
                    val longitude = cursor.getDouble(cursor.getColumnIndexOrThrow(Contract.Place.LONGITUDE))
                    placeList.add(Place(id, name, null, address, latitude, longitude))
                } while (cursor.moveToNext())
                // display the list of places on map
                mapView.addPlaces(placeList)
            }
        }
    }

    override fun onLoaderReset(loader: Loader<Cursor>) {
        // nothing to release
    }

    override fun onMapReady() {
        mMapView?.setOnInfoClickListener(this)
        LoaderManager.getInstance(this).restartLoader(LOADER_PLACES, null, this)
    }

    override fun onInfoWindowClick(placeId: Long) {
        val intent = Intent(this, TransactionListActivity::class.java).apply {
            putExtra(TransactionListActivity.PLACE_ID, placeId)
        }
        startActivity(intent)
    }

    companion object {
        private const val LOADER_PLACES = 23748
    }
}
