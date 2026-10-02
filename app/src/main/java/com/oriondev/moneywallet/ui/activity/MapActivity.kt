/*
 * Copyright (c) 2018. MoneyWallet
 * Copyright (c) 2026. solomonrajan/CashLedger
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
import androidx.loader.app.LoaderManager
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.Place
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.ui.activity.base.SinglePanelActivity
import com.oriondev.moneywallet.view.MapViewWrapper

class MapActivity : SinglePanelActivity(), LoaderManager.LoaderCallbacks<Cursor>, 
    MapViewWrapper.OnMapLoadedCallback, MapViewWrapper.OnInfoWindowClickListener {

    private lateinit var mapView: MapViewWrapper
    private var placesDelivered = false

    override fun onCreatePanelView(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?) {
        val view = inflater.inflate(R.layout.layout_panel_map, parent, true)
        mapView = MapViewWrapper(view.findViewById(R.id.map_view))
        mapView.keepCopyrightClearOfSystemBars()
        mapView.onCreate(savedInstanceState)
        mapView.loadMapAsync(this)
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

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
        if (placesDelivered) {
            return
        }
        placesDelivered = true
        if (cursor != null) {
            if (mapView.isMapReady && cursor.moveToFirst()) {
                val placeList = mutableListOf<Place>()
                do {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Place.ID))
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Place.NAME))
                    val address = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Place.ADDRESS))
                    val latitude = cursor.getDouble(cursor.getColumnIndexOrThrow(Contract.Place.LATITUDE))
                    val longitude = cursor.getDouble(cursor.getColumnIndexOrThrow(Contract.Place.LONGITUDE))
                    placeList.add(Place(id, name, null, address, latitude, longitude))
                } while (cursor.moveToNext())
                mapView.addPlaces(placeList)
            }
        }
    }

    override fun onLoaderReset(loader: Loader<Cursor>) {
        // nothing to release
    }

    override fun onMapReady() {
        mapView.setOnInfoClickListener(this)
        LoaderManager.getInstance(this).restartLoader(LOADER_PLACES, null, this)
    }

    override fun onInfoWindowClick(placeId: Long) {
        val intent = Intent(this, TransactionListActivity::class.java)
        intent.putExtra(TransactionListActivity.PLACE_ID, placeId)
        startActivity(intent)
    }

    companion object {
        private const val LOADER_PLACES = 23748
    }
}
