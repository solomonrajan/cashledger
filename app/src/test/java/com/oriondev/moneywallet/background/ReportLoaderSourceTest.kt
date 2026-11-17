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

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.regex.Pattern

class ReportLoaderSourceTest {

    @Test
    fun everyReportLoaderAppliesTheReportFilter() {
        for (loader in LOADERS) {
            val kotlin = isKotlin(loader)
            val expected = if (kotlin) APPEND_KOTLIN else APPEND_JAVA
            assertTrue(
                "a report screen that stops filtering on REPORT_FILTER counts a category " +
                    "kept out of the reports again, and disagrees with the other two: $loader",
                readSource(loader).contains(expected)
            )
        }
    }

    private fun isKotlin(loader: String): Boolean {
        return findSourceFile("src/main/java/com/oriondev/moneywallet/background/$loader.kt").exists()
    }

    private fun readSource(loader: String): String {
        var file = findSourceFile("src/main/java/com/oriondev/moneywallet/background/$loader.kt")
        if (!file.exists()) {
            file = findSourceFile("src/main/java/com/oriondev/moneywallet/background/$loader.java")
        }
        if (!file.exists()) {
            fail("Cannot find $loader at ${file.absolutePath}")
        }
        return try {
            val source = String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)
            COMMENTS.matcher(source).replaceAll(" ").replace("\\s+".toRegex(), " ")
        } catch (e: IOException) {
            fail("Cannot read $loader: ${e.message}")
            ""
        }
    }

    companion object {
        private val COMMENTS: Pattern = Pattern.compile("//.*?$|/[*].*?[*]/", Pattern.DOTALL or Pattern.MULTILINE)

        private val LOADERS = arrayOf(
            "OverviewDataLoader",
            "PeriodDetailFlowLoader",
            "PeriodDetailSummaryLoader"
        )

        private const val APPEND_JAVA = "selection += \" AND \" + Contract.Transaction.REPORT_FILTER;"
        private const val APPEND_KOTLIN = "selection += \" AND \" + Contract.Transaction.REPORT_FILTER"

        private fun findSourceFile(relativePath: String): File {
            var file = File(relativePath)
            if (!file.exists()) {
                file = File("app/$relativePath")
            }
            return file
        }
    }
}
