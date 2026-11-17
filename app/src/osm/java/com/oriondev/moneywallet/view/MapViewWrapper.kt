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

package com.oriondev.moneywallet.view

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.Coordinates
import com.oriondev.moneywallet.model.Place
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.utils.Urls
import org.osmdroid.config.Configuration
import org.osmdroid.config.IConfigurationProvider
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.ItemizedIconOverlay
import org.osmdroid.views.overlay.ItemizedOverlayWithFocus
import org.osmdroid.views.overlay.OverlayItem
import org.osmdroid.views.util.constants.OverlayConstants
import java.io.File
import java.util.ArrayList

/**
 * Created by andrea on 16/10/18.
 */
class MapViewWrapper(view: View) {

    private val mapView: MapView = view as MapView
    private var infoWindowClickListener: OnInfoWindowClickListener? = null
    private var copyrightOverlay: CopyrightOverlay? = null
    private var offsetCopyrightForSystemBars = false

    init {
        mapView.setBuiltInZoomControls(false)
        mapView.setMultiTouchControls(true)
        mapView.minZoomLevel = DEFAULT_MIN_ZOOM_LEVEL
        mapView.controller.setZoom(DEFAULT_ZOOM_LEVEL)
        setupConfiguration(mapView.context)
    }

    private fun setupConfiguration(context: Context) {
        val configuration: IConfigurationProvider = Configuration.getInstance()
        // to prevent using a default user agent, we can set it to the current package name
        configuration.userAgentValue = context.packageName
        // fix for empty map caused by a read-only access to the file system: to overcome this issue
        // we can use a temp directory inside the cache folder of the application
        val cacheFolder = context.externalCacheDir
        if (cacheFolder != null) {
            // we have a reference to the global cache folder of the application, maybe it's better
            // to create a sub folder for caching tiles
            val tileCache = File(cacheFolder, TILE_CACHE_FOLDER)
            if (tileCache.exists() || tileCache.mkdir()) {
                configuration.osmdroidTileCache = tileCache
            }
        }
        setupTileSource()
    }

    /**
     * Point the map at whatever server the user chose, so that replacing the default one does not
     * need a change to this app. Doing nothing leaves the library's own default in place.
     */
    private fun setupTileSource() {
        val url = PreferenceManager.getMapTileServer()
        if (url.isNullOrEmpty() || !Urls.isUsableTileAddress(url)) {
            return
        }
        mapView.setTileSource(TemplateTileSource(Urls.asTileTemplate(url)))
    }

    private class TemplateTileSource(
        private val template: String
    ) : OnlineTileSourceBase(
        template,
        0,
        TILE_MAX_ZOOM,
        TILE_SIZE_PIXELS,
        "",
        arrayOf(template),
        OSM_COPYRIGHT,
        TILE_POLICY
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            return Urls.tileUrl(
                template,
                MapTileIndex.getZoom(pMapTileIndex),
                MapTileIndex.getX(pMapTileIndex),
                MapTileIndex.getY(pMapTileIndex)
            )
        }
    }

    /**
     * Only the maps that fill a window ask for this. Two of the four do; the other two are short
     * maps inside a card on the place screens, where offsetting the notice by a navigation bar they
     * never touch would park it in the middle of the card.
     */
    fun keepCopyrightClearOfSystemBars() {
        offsetCopyrightForSystemBars = true
        if (copyrightOverlay != null) {
            applyCopyrightOffset()
        }
    }

    private fun setupCopyrightOverlay() {
        // OpenStreetMap requires that you add "© OpenStreetMap contributors" to the map
        val overlay = CopyrightOverlay(mapView.context)
        copyrightOverlay = overlay
        mapView.overlayManager.add(0, overlay)
        applyCopyrightOffset()
    }

    /**
     * A map that fills a window is not padded, because it is meant to run under the navigation bar.
     * The notice still has to stay readable, and it is drawn on the canvas instead of laid out, so
     * it moves by its own offset. A larger offset draws it higher, since it is aligned to the
     * bottom of the canvas.
     */
    private fun applyCopyrightOffset() {
        if (!offsetCopyrightForSystemBars) {
            return
        }
        val overlay = copyrightOverlay ?: return
        ViewCompat.setOnApplyWindowInsetsListener(mapView) { view, insets ->
            val bars: Insets = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            overlay.setOffset(bars.left + COPYRIGHT_EDGE_MARGIN, bars.bottom + COPYRIGHT_EDGE_MARGIN)
            view.invalidate()
            insets
        }
        mapView.requestApplyInsets()
    }

    fun onCreate(savedInstanceState: Bundle?) {
        // here we can initialize the copyright overlay
        setupCopyrightOverlay()
    }

    fun onStart() {
        // do nothing
    }

    fun onResume() {
        mapView.onResume()
    }

    fun onPause() {
        mapView.onPause()
    }

    fun onStop() {
        // do nothing
    }

    fun onDestroy() {
        // do nothing
    }

    fun onSaveInstanceState(outState: Bundle?) {
        // do nothing
    }

    fun onLowMemory() {
        // do nothing
    }

    fun loadMapAsync(callback: OnMapLoadedCallback?) {
        callback?.onMapReady()
    }

    val isMapReady: Boolean
        get() = true

    fun disableMapInteractions() {
        mapView.setOnTouchListener { _, _ -> true }
    }

    private fun getMarkerIcon(): Drawable? {
        return ContextCompat.getDrawable(mapView.context, R.drawable.ic_osm_marker)
    }

    fun addPlaces(places: List<Place>?) {
        if (places != null && places.isNotEmpty()) {
            var pointCount = 0
            var centerLatitude = 0.0
            var centerLongitude = 0.0
            val items = ArrayList<OverlayItem>()
            for (place in places) {
                if (place.hasCoordinates()) {
                    val coordinates = place.coordinates
                    if (coordinates != null) {
                        val identifier = place.id.toString()
                        val name = place.name
                        val description = place.address
                        val geoPoint = GeoPoint(coordinates.latitude, coordinates.longitude)
                        items.add(OverlayItem(identifier, name, description, geoPoint))
                        pointCount += 1
                        centerLatitude += coordinates.latitude
                        centerLongitude += coordinates.longitude
                    }
                }
            }
            val marker = getMarkerIcon()
            val overlay = ItemizedOverlayWithFocus(
                items,
                marker,
                marker,
                OverlayConstants.NOT_SET,
                object : ItemizedIconOverlay.OnItemGestureListener<OverlayItem> {
                    override fun onItemSingleTapUp(index: Int, item: OverlayItem): Boolean {
                        val listener = infoWindowClickListener
                        val uid = item.uid
                        if (listener != null && uid != null) {
                            val parsedId = uid.toLongOrNull()
                            if (parsedId != null) {
                                listener.onInfoWindowClick(parsedId)
                            }
                        }
                        return true
                    }

                    override fun onItemLongPress(index: Int, item: OverlayItem): Boolean {
                        return false
                    }
                },
                mapView.context
            )
            overlay.setFocusItemsOnTap(true)
            overlay.setMarkerTitleForegroundColor(Color.BLACK)
            overlay.setMarkerDescriptionForegroundColor(Color.BLACK)
            overlay.setMarkerBackgroundColor(Color.WHITE)

            overlay.setDescriptionBoxPadding(10)
            overlay.setDescriptionBoxCornerWidth(10)

            mapView.overlays.add(overlay)
            if (pointCount > 0) {
                centerLatitude /= pointCount
                centerLongitude /= pointCount
                val centerPoint = GeoPoint(centerLatitude, centerLongitude)
                mapView.controller.setCenter(centerPoint)
            }
            mapView.controller.setZoom(GLOBAL_ZOOM_LEVEL)
        }
    }

    fun addCoordinates(coordinates: Coordinates?) {
        if (coordinates != null) {
            val geoPoint = GeoPoint(coordinates.latitude, coordinates.longitude)
            val items = ArrayList<OverlayItem>()
            items.add(OverlayItem("", "", geoPoint))
            val marker = getMarkerIcon()
            val overlay = ItemizedOverlayWithFocus(
                items,
                marker,
                null,
                OverlayConstants.NOT_SET,
                object : ItemizedIconOverlay.OnItemGestureListener<OverlayItem> {
                    override fun onItemSingleTapUp(index: Int, item: OverlayItem): Boolean {
                        return false
                    }

                    override fun onItemLongPress(index: Int, item: OverlayItem): Boolean {
                        return false
                    }
                },
                mapView.context
            )
            overlay.setFocusItemsOnTap(true)
            mapView.overlays.add(overlay)
            mapView.controller.setCenter(geoPoint)
            mapView.controller.setZoom(DEFAULT_ZOOM_LEVEL)
        }
    }

    fun clear() {
        mapView.overlays.clear()
        setupCopyrightOverlay()
    }

    fun setOnInfoClickListener(callback: OnInfoWindowClickListener?) {
        infoWindowClickListener = callback
    }

    fun getCenterCoordinates(): Coordinates {
        val geoPoint = mapView.mapCenter
        return Coordinates(geoPoint.latitude, geoPoint.longitude)
    }

    fun setMinZoomLevel() {
        mapView.controller.setZoom(DEFAULT_MIN_ZOOM_LEVEL)
    }

    interface OnMapLoadedCallback {
        fun onMapReady()
    }

    interface OnInfoWindowClickListener {
        fun onInfoWindowClick(placeId: Long)
    }

    companion object {
        private const val TILE_CACHE_FOLDER = "osm_tiles"
        private const val OSM_COPYRIGHT = "© OpenStreetMap contributors"

        private val TILE_POLICY = TileSourcePolicy(
            2,
            TileSourcePolicy.FLAG_NO_BULK or
                TileSourcePolicy.FLAG_NO_PREVENTIVE or
                TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL or
                TileSourcePolicy.FLAG_USER_AGENT_NORMALIZED
        )

        private const val TILE_SIZE_PIXELS = 256
        private const val TILE_MAX_ZOOM = 19

        private const val DEFAULT_MIN_ZOOM_LEVEL = 4.0
        private const val DEFAULT_ZOOM_LEVEL = 14.0
        private const val GLOBAL_ZOOM_LEVEL = 8.0

        private const val COPYRIGHT_EDGE_MARGIN = 10

        @JvmStatic
        fun supportsCustomTileServer(): Boolean {
            return true
        }
    }
}
