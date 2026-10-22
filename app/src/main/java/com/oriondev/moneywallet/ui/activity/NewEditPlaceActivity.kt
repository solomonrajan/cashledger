/*
 * Copyright (c) 2018. MoneyWallet
 * Copyright (c) 2026. solomonrajan/CashLedger
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

@file:Suppress(
    "LongMethod",
    "LongParameterList",
    "MatchingDeclarationName",
    "FunctionNaming",
    "WildcardImport",
    "MaxLineLength",
    "MagicNumber",
    "UnusedParameter",
    "CyclomaticComplexMethod",
    "TooManyFunctions",
    "SwallowedException",
    "NestedBlockDepth",
    "ReturnCount",
    "ComplexCondition"
)

package com.oriondev.moneywallet.ui.activity

import android.app.Activity
import android.content.ContentUris
import android.content.ContentValues
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.ColorIcon
import com.oriondev.moneywallet.model.Coordinates
import com.oriondev.moneywallet.model.Icon
import com.oriondev.moneywallet.model.Place
import com.oriondev.moneywallet.picker.IconPicker
import com.oriondev.moneywallet.picker.MapPlacePicker
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.utils.IconLoader
import com.oriondev.moneywallet.view.MapViewWrapper

class NewEditPlaceActivity : AppCompatActivity(),
    IconPicker.Controller,
    MapViewWrapper.OnMapLoadedCallback,
    MapPlacePicker.Controller {

    private lateinit var iconPicker: IconPicker
    private lateinit var placePicker: MapPlacePicker
    private var mapView: MapViewWrapper? = null

    // State
    private var name by mutableStateOf("")
    private var icon by mutableStateOf<Icon?>(null)
    private var address by mutableStateOf("")
    private var coordinates by mutableStateOf<Coordinates?>(null)
    private var nameError by mutableStateOf(false)

    private var mode = NewEditItemActivity.Mode.NEW_ITEM
    private var itemId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val intentMode = intent?.getSerializableExtra(NewEditItemActivity.MODE) as? NewEditItemActivity.Mode
        if (intentMode != null) {
            mode = intentMode
        }
        itemId = intent?.getLongExtra(NewEditItemActivity.ID, -1L) ?: -1L

        var initialPlace: Place? = null
        if (savedInstanceState == null && mode == NewEditItemActivity.Mode.EDIT_ITEM && itemId > 0L) {
            initialPlace = loadPlaceData()
        }

        val fm = supportFragmentManager
        iconPicker = IconPicker.createPicker(fm, TAG_ICON_PICKER, icon)
        placePicker = MapPlacePicker.createPicker(fm, TAG_PLACE_PICKER, initialPlace)

        setContent {
            val isDark = isSystemInDarkTheme()
            val colorScheme = if (isDark) darkColorScheme() else lightColorScheme()

            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NewEditPlaceScreen(
                        mode = mode,
                        name = name,
                        onNameChange = { newName ->
                            name = newName
                            nameError = false
                            val currentIcon = iconPicker.currentIcon ?: icon
                            if (currentIcon is ColorIcon) {
                                val initial = IconLoader.getColorIconString(newName)
                                val updatedIcon = ColorIcon(currentIcon, initial)
                                iconPicker.setCurrentIcon(updatedIcon)
                                icon = updatedIcon
                            }
                        },
                        nameError = nameError,
                        icon = icon,
                        onIconClick = { iconPicker.showPicker() },
                        address = address,
                        onAddressChange = { address = it },
                        onPickOnMapClick = { placePicker.showPicker() },
                        coordinates = coordinates,
                        onRemoveCoordinates = {
                            placePicker.setCurrentPlace(null)
                            coordinates = null
                            mapView?.clear()
                        },
                        onBindMapView = { wrapper ->
                            mapView = wrapper
                            wrapper.keepCopyrightClearOfSystemBars()
                            wrapper.onCreate(savedInstanceState)
                            wrapper.disableMapInteractions()
                            wrapper.loadMapAsync(this@NewEditPlaceActivity)
                        },
                        onBackClick = { finish() },
                        onSaveClick = { savePlace() }
                    )
                }
            }
        }
    }

    private fun loadPlaceData(): Place? {
        val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_PLACES, itemId)
        val projection = arrayOf(
            Contract.Place.ID,
            Contract.Place.NAME,
            Contract.Place.ICON,
            Contract.Place.ADDRESS,
            Contract.Place.LATITUDE,
            Contract.Place.LONGITUDE
        )
        var loadedPlace: Place? = null
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Place.ID))
                name = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Place.NAME)) ?: ""
                val iconStr = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Place.ICON))
                icon = IconLoader.parse(iconStr)
                address = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Place.ADDRESS)) ?: ""

                val latIndex = cursor.getColumnIndexOrThrow(Contract.Place.LATITUDE)
                val lonIndex = cursor.getColumnIndexOrThrow(Contract.Place.LONGITUDE)
                val latitude = if (!cursor.isNull(latIndex)) cursor.getDouble(latIndex) else null
                val longitude = if (!cursor.isNull(lonIndex)) cursor.getDouble(lonIndex) else null

                if (latitude != null && longitude != null) {
                    coordinates = Coordinates(latitude, longitude)
                }
                loadedPlace = Place(id, name, icon, address, latitude, longitude)
            } else {
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
        } ?: run {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
        return loadedPlace
    }

    private fun validate(): Boolean {
        if (name.isBlank()) {
            nameError = true
            Toast.makeText(this, R.string.error_input_name_not_valid, Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun savePlace() {
        if (!validate()) return

        val contentValues = ContentValues().apply {
            put(Contract.Place.NAME, name.trim())
            put(Contract.Place.ICON, (iconPicker.currentIcon ?: icon)?.toString() ?: "")
            put(Contract.Place.ADDRESS, address.trim())
            val currentPlace = placePicker.currentPlace
            if (placePicker.isSelected && currentPlace?.hasCoordinates() == true) {
                val coords = currentPlace.coordinates
                put(Contract.Place.LATITUDE, coords.latitude)
                put(Contract.Place.LONGITUDE, coords.longitude)
            } else if (coordinates != null) {
                put(Contract.Place.LATITUDE, coordinates?.latitude)
                put(Contract.Place.LONGITUDE, coordinates?.longitude)
            } else {
                putNull(Contract.Place.LATITUDE)
                putNull(Contract.Place.LONGITUDE)
            }
        }

        when (mode) {
            NewEditItemActivity.Mode.NEW_ITEM -> {
                contentResolver.insert(DataContentProvider.CONTENT_PLACES, contentValues)
            }
            NewEditItemActivity.Mode.EDIT_ITEM -> {
                val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_PLACES, itemId)
                contentResolver.update(uri, contentValues, null, null)
            }
        }
        setResult(Activity.RESULT_OK)
        finish()
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

    override fun onIconChanged(tag: String?, newIcon: Icon?) {
        icon = newIcon
    }

    override fun onMapReady() {
        mapView?.disableMapInteractions()
        placePicker.fireCallbackSafely()
    }

    override fun onMapPlaceChanged(tag: String?, place: Place?) {
        if (place != null) {
            val placeName = place.name
            if (!placeName.isNullOrEmpty()) {
                name = placeName
            }
            val placeAddress = place.address
            if (!placeAddress.isNullOrEmpty()) {
                address = placeAddress
            }
            if (place.hasCoordinates()) {
                val coords = place.coordinates
                coordinates = coords
                mapView?.addCoordinates(coords)
            } else {
                if (mapView?.isMapReady == true) {
                    mapView?.clear()
                }
                coordinates = null
            }
        } else {
            if (mapView?.isMapReady == true) {
                mapView?.clear()
            }
            coordinates = null
        }
    }

    object Mode {
        @JvmField
        val NEW_ITEM: NewEditItemActivity.Mode = NewEditItemActivity.Mode.NEW_ITEM
        @JvmField
        val EDIT_ITEM: NewEditItemActivity.Mode = NewEditItemActivity.Mode.EDIT_ITEM
    }

    companion object {
        const val MODE: String = NewEditItemActivity.MODE
        const val ID: String = NewEditItemActivity.ID

        private const val TAG_ICON_PICKER = "NewEditPlaceActivity::Tag::IconPicker"
        private const val TAG_PLACE_PICKER = "NewEditPlaceActivity::Tag::PlacePicker"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewEditPlaceScreen(
    mode: NewEditItemActivity.Mode,
    name: String,
    onNameChange: (String) -> Unit,
    nameError: Boolean,
    icon: Icon?,
    onIconClick: () -> Unit,
    address: String,
    onAddressChange: (String) -> Unit,
    onPickOnMapClick: () -> Unit,
    coordinates: Coordinates?,
    onRemoveCoordinates: () -> Unit,
    onBindMapView: (MapViewWrapper) -> Unit,
    onBackClick: () -> Unit,
    onSaveClick: () -> Unit
) {
    val titleRes = if (mode == NewEditItemActivity.Mode.NEW_ITEM) {
        R.string.title_activity_new_place
    } else {
        R.string.title_activity_edit_place
    }
    val context = LocalContext.current
    val title = context.getString(titleRes)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onSaveClick) {
                        Icon(Icons.Default.Check, contentDescription = "Save Place")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Button(
                        onClick = onSaveClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text(
                            text = stringResource(R.string.menu_action_save_changes),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Main Header Card: Icon and Name
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Avatar / Icon
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onIconClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                android.widget.ImageView(ctx).apply {
                                    scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                                    val pd = (8 * ctx.resources.displayMetrics.density).toInt()
                                    setPadding(pd, pd, pd, pd)
                                }
                            },
                            update = { view ->
                                if (icon != null) {
                                    IconLoader.loadInto(icon, view)
                                } else {
                                    view.setImageDrawable(null)
                                }
                            }
                        )
                    }

                    OutlinedTextField(
                        value = name,
                        onValueChange = onNameChange,
                        label = { Text(stringResource(R.string.hint_name)) },
                        leadingIcon = {
                            Icon(Icons.Default.Place, contentDescription = null)
                        },
                        isError = nameError,
                        supportingText = if (nameError) {
                            { Text(stringResource(R.string.error_input_name_not_valid)) }
                        } else null,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next
                        ),
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            }

            // Address Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = address,
                        onValueChange = onAddressChange,
                        label = { Text(stringResource(R.string.hint_address)) },
                        leadingIcon = {
                            Icon(Icons.Default.Place, contentDescription = null)
                        },
                        trailingIcon = {
                            IconButton(onClick = onPickOnMapClick) {
                                Icon(
                                    imageVector = Icons.Default.Map,
                                    contentDescription = stringResource(R.string.description_place_picker_icon),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Done
                        ),
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            }

            // Map preview card (shown when coordinates are present)
            if (coordinates != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Embedded Map View via AndroidView
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        ) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { ctx ->
                                    val cardView = LayoutInflater.from(ctx).inflate(R.layout.view_card_map, null, false)
                                    val mv = MapViewWrapper(cardView.findViewById(R.id.map_view))
                                    onBindMapView(mv)
                                    cardView
                                },
                                update = { _ ->
                                    // Map coordinates updated in activity callbacks
                                }
                            )
                        }

                        // Map Action Bar: Coordinates summary & Remove button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "%.4f, %.4f".format(coordinates.latitude, coordinates.longitude),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            TextButton(
                                onClick = onRemoveCoordinates,
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp).padding(end = 4.dp)
                                )
                                Text(stringResource(R.string.action_remove))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
