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

package com.oriondev.moneywallet.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TileAddressTest {

    @Test
    fun aBaseAddressIsUsable() {
        assertTrue(Urls.isUsableTileAddress("https://tiles.example.org/"))
        assertTrue(Urls.isUsableTileAddress("https://tiles.example.org/osm"))
    }

    @Test
    fun aCompleteTemplateIsUsable() {
        assertTrue(Urls.isUsableTileAddress("https://tiles.example.org/{z}/{x}/{y}.jpg"))
        assertTrue(Urls.isUsableTileAddress("https://tiles.example.org/{z}/{x}/{y}.png?key=abc"))
    }

    @Test
    fun aHalfTemplateIsRejected() {
        assertFalse(Urls.isUsableTileAddress("https://tiles.example.org/{x}/{y}.png"))
        assertFalse(Urls.isUsableTileAddress("https://tiles.example.org/{z}.png"))
        assertFalse(Urls.isUsableTileAddress("https://tiles.example.org/{z}/{x}.png"))
    }

    @Test
    fun aTemplateCarryingAPlaceholderWeDoNotSubstituteIsRejected() {
        assertFalse(Urls.isUsableTileAddress("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"))
        assertFalse(Urls.isUsableTileAddress("https://tiles.example.org/{z}/{x}/{y}{r}.png"))
    }

    @Test
    fun aFragmentIsRejectedInATemplateToo() {
        assertFalse(Urls.isUsableTileAddress("https://tiles.example.org/#{z}/{x}/{y}.png"))
        assertFalse(Urls.isUsableTileAddress("https://map.example.org/#{z}/{x}/{y}?key=abc"))
    }

    @Test
    fun percentEncodedPlaceholdersAreRejected() {
        assertFalse(Urls.isUsableTileAddress("https://tiles.example.org/%7Bz%7D/%7Bx%7D/%7By%7D.png"))
        assertFalse(Urls.isUsableTileAddress("https://tiles.example.org/%7BZ%7D/%7BX%7D/%7BY%7D.png"))
    }

    @Test
    fun uppercasePlaceholdersAreRejected() {
        assertFalse(Urls.isUsableTileAddress("https://tiles.example.org/{Z}/{X}/{Y}.png"))
    }

    @Test
    fun anEmptyOrBlankAddressIsRejected() {
        assertFalse(Urls.isUsableTileAddress(null))
        assertFalse(Urls.isUsableTileAddress(""))
        assertFalse(Urls.isUsableTileAddress("   "))
    }

    @Test
    fun asTileTemplateCompletesBaseAddress() {
        assertEquals("https://tiles.example.org/{z}/{x}/{y}.png", Urls.asTileTemplate("https://tiles.example.org"))
        assertEquals("https://tiles.example.org/{z}/{x}/{y}.png", Urls.asTileTemplate("https://tiles.example.org/"))
    }

    @Test
    fun asTileTemplateLeavesCompleteTemplateAlone() {
        assertEquals("https://tiles.example.org/{z}/{x}/{y}.jpg", Urls.asTileTemplate("https://tiles.example.org/{z}/{x}/{y}.jpg"))
    }

    @Test
    fun tileUrlReplacesPlaceholders() {
        assertEquals(
            "https://tiles.example.org/14/8391/5462.png",
            Urls.tileUrl("https://tiles.example.org/{z}/{x}/{y}.png", 14, 8391, 5462)
        )
    }
}
