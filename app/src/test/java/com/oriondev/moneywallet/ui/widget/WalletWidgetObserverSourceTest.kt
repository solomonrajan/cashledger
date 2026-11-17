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

package com.oriondev.moneywallet.ui.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.regex.Pattern

/**
 * Pinned as text the way DataContentProviderTransactionSourceTest pins what the provider's write
 * methods must be reached by.
 */
class WalletWidgetObserverSourceTest {

    private fun source(): String {
        val ktPath = "src/main/java/com/oriondev/moneywallet/ui/widget/WalletWidgetObserver.kt"
        val javaPath = "src/main/java/com/oriondev/moneywallet/ui/widget/WalletWidgetObserver.java"
        var file = findSourceFile(ktPath)
        if (!file.exists()) {
            file = findSourceFile(javaPath)
        }
        if (!file.exists()) {
            fail("Cannot find WalletWidgetObserver source at ${file.absolutePath}")
        }
        return try {
            val read = String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)
            STRIPPED.matcher(read).replaceAll(" ")
        } catch (e: IOException) {
            fail("Cannot read ${file.name}: ${e.message}")
            ""
        }
    }

    @Test
    fun theWidgetWatchesTheWholeProviderAndAsksForWhatIsUnderIt() {
        assertTrue(
            "the widget no longer registers on the whole provider with descendants included. Nothing announces CONTENT_ALL itself, so without the flag it is told about nothing at all, and with a narrower uri it is told about nothing outside that one",
            WATCHES_THE_WHOLE_PROVIDER.matcher(source()).find()
        )
    }

    @Test
    fun theWidgetRegistersOnce() {
        val matcher = ANY_REGISTRATION.matcher(source())
        var registrations = 0
        while (matcher.find()) {
            registrations++
        }
        assertTrue("the widget registers on nothing at all", registrations > 0)
        assertEquals(
            "the widget registers on something as well as the whole provider",
            1,
            registrations
        )
    }

    companion object {
        private val STRIPPED = Pattern.compile(
            "\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|//.*?$|/[*].*?[*]/",
            Pattern.DOTALL or Pattern.MULTILINE
        )

        private val WATCHES_THE_WHOLE_PROVIDER = Pattern.compile(
            "registerContentObserver\\(\\s*DataContentProvider\\.CONTENT_ALL\\s*,\\s*true\\s*,"
        )

        private val ANY_REGISTRATION = Pattern.compile(
            "\\.\\s*registerContentObserver\\s*\\("
        )

        private fun findSourceFile(path: String): File {
            var file = File(path)
            if (!file.exists()) {
                file = File("app/$path")
            }
            return file
        }
    }
}
