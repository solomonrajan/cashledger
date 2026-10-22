package com.oriondev.moneywallet.ui.activity

import com.oriondev.moneywallet.model.ColorIcon
import com.oriondev.moneywallet.model.Coordinates
import com.oriondev.moneywallet.model.Place
import com.oriondev.moneywallet.storage.database.Contract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NewEditPlaceActivityTest {

    @Test
    fun testPlaceCompanionConstants() {
        assertEquals("NewEditItemActivity::Mode", NewEditPlaceActivity.MODE)
        assertEquals("NewEditItemActivity::Id", NewEditPlaceActivity.ID)
        assertEquals(NewEditItemActivity.Mode.NEW_ITEM, NewEditPlaceActivity.Mode.NEW_ITEM)
        assertEquals(NewEditItemActivity.Mode.EDIT_ITEM, NewEditPlaceActivity.Mode.EDIT_ITEM)
    }

    @Test
    fun testPlaceContractColumnNames() {
        assertEquals("place_id", Contract.Place.ID)
        assertEquals("place_name", Contract.Place.NAME)
        assertEquals("place_icon", Contract.Place.ICON)
        assertEquals("place_address", Contract.Place.ADDRESS)
        assertEquals("place_latitude", Contract.Place.LATITUDE)
        assertEquals("place_longitude", Contract.Place.LONGITUDE)
    }

    @Test
    fun testPlaceModelWithCoordinates() {
        val icon = ColorIcon("#3F51B5", "MD")
        val place = Place(303L, "Milan Duomo", icon, "Piazza del Duomo", 45.4642, 9.1900)
        assertEquals(303L, place.id)
        assertEquals("Milan Duomo", place.name)
        assertEquals("Piazza del Duomo", place.address)
        assertTrue(place.hasCoordinates())
        assertNotNull(place.coordinates)
        assertEquals(45.4642, place.coordinates.latitude, 0.0001)
        assertEquals(9.1900, place.coordinates.longitude, 0.0001)
    }
}
