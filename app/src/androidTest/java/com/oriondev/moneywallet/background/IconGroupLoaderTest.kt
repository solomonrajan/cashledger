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

package com.oriondev.moneywallet.background

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.oriondev.moneywallet.model.IconGroup
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.HashSet

/**
 * IconGroupLoader builds the picker's list from assets/resources/icons.json, and nothing in the
 * build checks that a name in that file resolves to anything. This runs the check through the
 * loader itself, so it sees what the picker sees.
 */
class IconGroupLoaderTest {

    @Test
    @Throws(Exception::class)
    fun everyIconInTheCatalogResolves() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val groups: List<IconGroup>? = IconGroupLoader(context).loadInBackground()

        val categories = JSONObject(readCatalog(context)).getJSONArray("categories")
        var declared = 0
        for (i in 0 until categories.length()) {
            declared += categories.getJSONObject(i).getJSONArray("icons").length()
        }
        assertTrue("icons.json declares no icons at all", declared > 0)

        assertNotNull("the loader returned null", groups)
        val loadedGroups = groups ?: return
        assertEquals(
            "the loader did not return every group icons.json declares",
            categories.length(),
            loadedGroups.size
        )

        var loaded = 0
        for (group in loadedGroups) {
            val name = group.groupName
            assertFalse("the group $name has no icons", group.groupIcons.isEmpty())
            val seen = HashSet<String>()
            for (icon in group.groupIcons) {
                assertNotNull(
                    "an icon in $name has no drawable behind it, $icon",
                    icon.getDrawable(context)
                )
                assertTrue("$icon is listed twice in $name", seen.add(icon.toString()))
                loaded++
            }
        }
        assertEquals("the loader did not return every icon icons.json declares", declared, loaded)
    }

    @Throws(IOException::class)
    private fun readCatalog(context: Context): String {
        val builder = StringBuilder()
        BufferedReader(InputStreamReader(context.assets.open(CATALOG))).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                builder.append(line)
            }
        }
        return builder.toString()
    }

    companion object {
        private const val CATALOG = "resources/icons.json"
    }
}
