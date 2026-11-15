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

package com.oriondev.moneywallet.ui.fragment.secondary

import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.loader.app.LoaderManager
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.Coordinates
import com.oriondev.moneywallet.model.Icon
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.ui.activity.NewEditItemActivity
import com.oriondev.moneywallet.ui.activity.NewEditPlaceActivity
import com.oriondev.moneywallet.ui.activity.TransactionListActivity
import com.oriondev.moneywallet.ui.fragment.base.SecondaryPanelFragment
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog
import com.oriondev.moneywallet.utils.IconLoader
import com.oriondev.moneywallet.view.MapViewWrapper
import java.util.Locale

/**
 * Created by andrea on 03/04/18.
 */
class PlaceItemFragment :
    SecondaryPanelFragment(),
    LoaderManager.LoaderCallbacks<Cursor>,
    MapViewWrapper.OnMapLoadedCallback {

    private var mProgressLayout: View? = null
    private var mMainLayout: View? = null

    private var mAvatarImageView: ImageView? = null
    private var mNameTextView: TextView? = null
    private var mAddressTextView: TextView? = null
    private var mMapCardView: CardView? = null
    private var mMapView: MapViewWrapper? = null

    private var mCoordinates: Coordinates? = null

    override fun onCreateHeaderView(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?) {
        val view = inflater.inflate(R.layout.layout_header_show_icon_name_item, parent, true)
        mAvatarImageView = view.findViewById(R.id.avatar_image_view)
        mNameTextView = view.findViewById(R.id.name_text_view)
    }

    override fun onCreateBodyView(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?) {
        val view = inflater.inflate(R.layout.layout_panel_show_place_item, parent, true)
        mProgressLayout = view.findViewById(R.id.secondary_panel_progress_wheel)
        mMainLayout = view.findViewById(R.id.secondary_panel_layout)
        mAddressTextView = view.findViewById(R.id.address_text_view)
        mMapCardView = view.findViewById(R.id.map_card_view)
        val mapView = MapViewWrapper(view.findViewById(R.id.map_view))
        mMapView = mapView
        view.findViewById<View>(R.id.open_button).setOnClickListener {
            val coords = mCoordinates
            if (coords != null) {
                try {
                    val uri = Uri.parse(String.format(Locale.ENGLISH, "geo:0,0?q=%f,%f", coords.latitude, coords.longitude))
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    val act = activity
                    if (act != null) {
                        ThemedDialog.buildMaterialDialog(act)
                            .setTitle(R.string.title_error)
                            .setMessage(R.string.message_error_activity_not_found)
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    }
                }
            }
        }
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

    override fun getTitle(): String {
        return getString(R.string.title_fragment_item_place)
    }

    override fun onInflateMenu(): Int {
        return R.menu.menu_list_edit_delete_item
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        val itemId = item.itemId
        if (itemId == R.id.action_show_transaction_list) {
            val intent = Intent(activity, TransactionListActivity::class.java).apply {
                putExtra(TransactionListActivity.PLACE_ID, getItemId())
            }
            startActivity(intent)
        } else if (itemId == R.id.action_edit_item) {
            val intent = Intent(activity, NewEditPlaceActivity::class.java).apply {
                putExtra(NewEditItemActivity.MODE, NewEditItemActivity.Mode.EDIT_ITEM)
                putExtra(NewEditItemActivity.ID, getItemId())
            }
            startActivity(intent)
        } else if (itemId == R.id.action_delete_item) {
            activity?.let { showDeleteDialog(it) }
        }
        return false
    }

    private fun showDeleteDialog(context: Context) {
        ThemedDialog.buildMaterialDialog(context)
            .setTitle(R.string.title_warning)
            .setMessage(R.string.message_delete_place)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val act = activity
                if (act != null) {
                    val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_PLACES, getItemId())
                    val contentResolver: ContentResolver = act.contentResolver
                    contentResolver.delete(uri, null, null)
                    navigateBackSafely()
                    showItemId(0L)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onShowItemId(itemId: Long) {
        setLoadingScreen(true)
        LoaderManager.getInstance(this).restartLoader(PLACE_LOADER_ID, null, this)
    }

    private fun setLoadingScreen(loading: Boolean) {
        if (loading) {
            mAvatarImageView?.setImageDrawable(null)
            mNameTextView?.text = null
            mCoordinates = null
            mProgressLayout?.visibility = View.VISIBLE
            mMainLayout?.visibility = View.GONE
        } else {
            mProgressLayout?.visibility = View.GONE
            mMainLayout?.visibility = View.VISIBLE
        }
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor> {
        val act = activity ?: throw IllegalStateException("Parent activity is null")
        val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_PLACES, getItemId())
        val projection = arrayOf(
            Contract.Place.NAME,
            Contract.Place.ICON,
            Contract.Place.ADDRESS,
            Contract.Place.LATITUDE,
            Contract.Place.LONGITUDE
        )
        return CursorLoader(act, uri, projection, null, null, null)
    }

    override fun onLoadFinished(loader: Loader<Cursor>, cursor: Cursor?) {
        if (cursor != null && cursor.moveToFirst()) {
            val icon: Icon? = IconLoader.parse(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Place.ICON)))
            mAvatarImageView?.let { IconLoader.loadInto(icon, it) }
            mNameTextView?.text = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Place.NAME))
            val address = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Place.ADDRESS))
            if (!TextUtils.isEmpty(address)) {
                mAddressTextView?.text = address
            } else {
                mAddressTextView?.setText(R.string.hint_address_unknown)
            }
            if (!cursor.isNull(cursor.getColumnIndexOrThrow(Contract.Place.LATITUDE)) &&
                !cursor.isNull(cursor.getColumnIndexOrThrow(Contract.Place.LONGITUDE))
            ) {
                mCoordinates = Coordinates(
                    cursor.getDouble(cursor.getColumnIndexOrThrow(Contract.Place.LATITUDE)),
                    cursor.getDouble(cursor.getColumnIndexOrThrow(Contract.Place.LONGITUDE))
                )
            } else {
                mCoordinates = null
            }
            onCoordinatesChanged()
        } else {
            showItemId(0L)
        }
        setLoadingScreen(false)
    }

    override fun onLoaderReset(loader: Loader<Cursor>) {
        // nothing to release
    }

    private fun onCoordinatesChanged() {
        val coords = mCoordinates
        val map = mMapView
        if (coords != null && map?.isMapReady == true) {
            map.addCoordinates(coords)
            mMapCardView?.visibility = View.VISIBLE
        } else {
            if (map?.isMapReady == true) {
                map.clear()
            }
            mMapCardView?.visibility = View.GONE
        }
    }

    override fun onMapReady() {
        mMapView?.disableMapInteractions()
        onCoordinatesChanged()
    }

    companion object {
        private const val PLACE_LOADER_ID = 54363
    }
}
