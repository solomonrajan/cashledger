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

import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.cardview.widget.CardView
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.Coordinates
import com.oriondev.moneywallet.model.Icon
import com.oriondev.moneywallet.model.Place
import com.oriondev.moneywallet.picker.IconPicker
import com.oriondev.moneywallet.picker.MapPlacePicker
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.ui.view.text.MaterialEditText
import com.oriondev.moneywallet.ui.view.text.NonEmptyTextValidator
import com.oriondev.moneywallet.utils.IconLoader
import com.oriondev.moneywallet.view.MapViewWrapper

/**
 * Created by andrea on 06/03/18.
 */
class NewEditPlaceActivity : NewEditItemActivity(),
    IconPicker.Controller,
    MapViewWrapper.OnMapLoadedCallback,
    MapPlacePicker.Controller {

    private var mIconView: ImageView? = null
    private var mNameEditText: MaterialEditText? = null
    private var mAddressEditText: MaterialEditText? = null
    private var mMapCardView: CardView? = null
    private var mMapView: MapViewWrapper? = null

    private var mIconPicker: IconPicker? = null
    private var mPlacePicker: MapPlacePicker? = null

    override fun onCreateHeaderView(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?) {
        val view = inflater.inflate(R.layout.layout_header_new_edit_icon_name_item, parent, true)
        val iconView: ImageView = view.findViewById(R.id.icon_image_view)
        val nameEt: MaterialEditText = view.findViewById(R.id.name_edit_text)
        mIconView = iconView
        mNameEditText = nameEt
        nameEt.addValidator(NonEmptyTextValidator(this, R.string.error_input_name_not_valid))
        iconView.setOnClickListener {
            mIconPicker?.showPicker()
        }
    }

    override fun onCreatePanelView(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?) {
        val view = inflater.inflate(R.layout.layout_panel_new_edit_place, parent, true)
        mAddressEditText = view.findViewById(R.id.address_edit_text)
        mMapCardView = view.findViewById(R.id.map_card_view)
        val mapView = MapViewWrapper(view.findViewById(R.id.map_view))
        mMapView = mapView
        view.findViewById<View>(R.id.place_picker_image_view).setOnClickListener {
            mPlacePicker?.showPicker()
        }
        view.findViewById<View>(R.id.remove_button).setOnClickListener {
            mPlacePicker?.currentPlace = null
        }
        mapView.onCreate(savedInstanceState)
    }

    override fun onViewCreated(savedInstanceState: Bundle?) {
        super.onViewCreated(savedInstanceState)
        var icon: Icon? = null
        var place: Place? = null
        if (savedInstanceState == null && getMode() == Mode.EDIT_ITEM) {
            val contentResolver = contentResolver
            val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_PLACES, getItemId())
            val projection = arrayOf(
                Contract.Place.ID,
                Contract.Place.NAME,
                Contract.Place.ICON,
                Contract.Place.ADDRESS,
                Contract.Place.LATITUDE,
                Contract.Place.LONGITUDE
            )
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Place.ID))
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Place.NAME))
                    icon = IconLoader.parse(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Place.ICON)))
                    val address = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Place.ADDRESS))
                    val latIndex = cursor.getColumnIndexOrThrow(Contract.Place.LATITUDE)
                    val lonIndex = cursor.getColumnIndexOrThrow(Contract.Place.LONGITUDE)
                    var latitude: Double? = null
                    var longitude: Double? = null
                    if (!cursor.isNull(latIndex) && !cursor.isNull(lonIndex)) {
                        latitude = cursor.getDouble(latIndex)
                        longitude = cursor.getDouble(lonIndex)
                    }
                    place = Place(id, name, icon, address, latitude, longitude)
                }
            }
        }
        val fragmentManager = supportFragmentManager
        val iconPicker = IconPicker.createPicker(fragmentManager, TAG_ICON_PICKER, icon)
        mIconPicker = iconPicker
        mPlacePicker = MapPlacePicker.createPicker(fragmentManager, TAG_PLACE_PICKER, place)
        iconPicker.listenOn(mNameEditText)
        mMapView?.loadMapAsync(this)
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

    override fun getActivityTileRes(mode: Mode): Int {
        return when (mode) {
            Mode.NEW_ITEM -> R.string.title_activity_new_place
            Mode.EDIT_ITEM -> R.string.title_activity_edit_place
        }
    }

    override fun onSaveChanges(mode: Mode) {
        if (mNameEditText?.validate() == true) {
            val contentResolver = contentResolver
            val contentValues = ContentValues().apply {
                put(Contract.Place.NAME, mNameEditText?.getTextAsString() ?: "")
                put(Contract.Place.ICON, mIconPicker?.currentIcon?.toString() ?: "")
                put(Contract.Place.ADDRESS, mAddressEditText?.getTextAsString() ?: "")
                val currentPlace = mPlacePicker?.currentPlace
                if (mPlacePicker?.isSelected == true && currentPlace != null && currentPlace.hasCoordinates()) {
                    val coordinates: Coordinates? = currentPlace.coordinates
                    if (coordinates != null) {
                        put(Contract.Place.LATITUDE, coordinates.latitude)
                        put(Contract.Place.LONGITUDE, coordinates.longitude)
                    } else {
                        putNull(Contract.Place.LATITUDE)
                        putNull(Contract.Place.LONGITUDE)
                    }
                } else {
                    putNull(Contract.Place.LATITUDE)
                    putNull(Contract.Place.LONGITUDE)
                }
            }
            when (mode) {
                Mode.NEW_ITEM -> {
                    contentResolver.insert(DataContentProvider.CONTENT_PLACES, contentValues)
                }
                Mode.EDIT_ITEM -> {
                    val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_PLACES, getItemId())
                    contentResolver.update(uri, contentValues, null, null)
                }
            }
            setResult(RESULT_OK)
            finish()
        }
    }

    override fun onIconChanged(tag: String?, icon: Icon?) {
        IconLoader.loadInto(icon, mIconView)
    }

    override fun onMapReady() {
        mMapView?.disableMapInteractions()
        mPlacePicker?.fireCallbackSafely()
    }

    override fun onMapPlaceChanged(tag: String?, place: Place?) {
        if (place != null && place.name != null) {
            mNameEditText?.setText(place.name)
            mAddressEditText?.setText(place.address)
        }
        if (place != null && place.hasCoordinates()) {
            val coords = place.coordinates
            if (coords != null) {
                mMapView?.addCoordinates(coords)
            }
            mMapCardView?.visibility = View.VISIBLE
        } else {
            if (mMapView?.isMapReady == true) {
                mMapView?.clear()
            }
            mMapCardView?.visibility = View.GONE
        }
    }

    companion object {
        private const val TAG_ICON_PICKER = "NewEditPlaceActivity::Tag::IconPicker"
        private const val TAG_PLACE_PICKER = "NewEditPlaceActivity::Tag::PlacePicker"
    }
}
