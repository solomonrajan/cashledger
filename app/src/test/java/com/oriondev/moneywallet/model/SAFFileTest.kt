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

package com.oriondev.moneywallet.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for issue #177: a legacy auto-backup location persisted by older versions
 * (a raw filesystem path or a bare content URI) must NOT be treated as an encoded SAFFile
 * descriptor.
 */
class SAFFileTest {

    @Test
    fun legacyRawPathIsNotEncoded() {
        assertFalse(SAFFile.looksEncoded("/storage/emulated/0"))
        assertFalse(SAFFile.looksEncoded("content://com.android.externalstorage.documents/tree/primary"))
    }

    @Test
    fun nullOrBlankIsNotEncoded() {
        assertFalse(SAFFile.looksEncoded(null))
        assertFalse(SAFFile.looksEncoded(""))
        assertFalse(SAFFile.looksEncoded("   "))
    }

    @Test
    fun jsonDescriptorIsEncoded() {
        assertTrue(SAFFile.looksEncoded("{\"uri\":\"content://x\",\"name\":\"backups\"}"))
        assertTrue(SAFFile.looksEncoded("   {\"uri\":\"content://x\"}   "))
    }
}
