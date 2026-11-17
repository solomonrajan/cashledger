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

package com.oriondev.moneywallet.storage.database

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.regex.Pattern

class DataContentProviderTransactionSourceTest {

    private fun isKotlin(): Boolean {
        return findSourceFile("src/main/java/com/oriondev/moneywallet/storage/database/DataContentProvider.kt").exists()
    }

    private fun readSource(): String {
        var file = findSourceFile("src/main/java/com/oriondev/moneywallet/storage/database/DataContentProvider.kt")
        if (!file.exists()) {
            file = findSourceFile("src/main/java/com/oriondev/moneywallet/storage/database/DataContentProvider.java")
        }
        if (!file.exists()) {
            fail("Cannot find DataContentProvider at ${file.absolutePath}")
        }
        return try {
            val source = String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)
            STRIPPED.matcher(source).replaceAll(" ")
        } catch (e: IOException) {
            fail("Cannot read DataContentProvider: ${e.message}")
            ""
        }
    }

    private fun bodyOf(source: String, signature: String): String {
        val start = source.indexOf(signature)
        assertTrue("$signature is gone", start >= 0)
        val end = source.indexOf("\n    }", start)
        assertTrue("no end found for $signature", end > start)
        return source.substring(start, end)
    }

    private fun writeMethods(): Array<Array<String>> {
        return if (isKotlin()) {
            arrayOf(
                arrayOf("fun insert(", "insertInTransaction(database,"),
                arrayOf("fun delete(", "deleteInTransaction(database,"),
                arrayOf("fun update(", "updateInTransaction(database,")
            )
        } else {
            WRITE_METHODS
        }
    }

    private fun transactionBodies(): Array<String> {
        return if (isKotlin()) {
            arrayOf(
                "fun insertInTransaction(",
                "fun deleteInTransaction(",
                "fun updateInTransaction("
            )
        } else {
            TRANSACTION_BODIES
        }
    }

    @Test
    fun everyWriteMethodRunsItsBodyInsideATransaction() {
        val source = readSource()
        for (method in writeMethods()) {
            val body = bodyOf(source, method[0])
            val sharedTxnCall = if (isKotlin()) "SQLDatabase.inSharedTransaction(context" else "SQLDatabase.inSharedTransaction(getContext(),"
            assertTrue(
                method[0] + " no longer goes through SQLDatabase.inSharedTransaction, so a restore can swap the shared helper part way through its write",
                body.contains(sharedTxnCall)
            )
            assertTrue(
                method[0] + " no longer hands its body the same helper the transaction was opened on. The exact call it needs is " + method[1],
                body.contains(method[1])
            )
        }
    }

    @Test
    fun nothingInsideATransactionNotifiesTheObservers() {
        val source = readSource()
        for (signature in transactionBodies()) {
            assertFalse(
                signature + " now calls notifyChange, which would announce a change that a rollback then undoes",
                bodyOf(source, signature).contains("notifyChange(")
            )
        }
    }

    @Test
    fun noBodyResolvesTheSharedHelperForItself() {
        val source = readSource()
        for (signature in transactionBodies()) {
            val body = bodyOf(source, signature)
            for (route in arrayOf("db()", "getShared", "sShared")) {
                assertFalse(
                    signature + " reaches the shared helper through " + route + " instead of using the one its caller resolved and passed in",
                    body.contains(route)
                )
            }
        }
    }

    @Test
    fun thePreferenceWriteStaysOutsideTheTransaction() {
        val source = readSource()
        val deleteInTxnSig = if (isKotlin()) "fun deleteInTransaction(" else "private int deleteInTransaction("
        val deleteSig = if (isKotlin()) "fun delete(" else "public int delete("
        assertFalse(
            "deleteInTransaction writes the current wallet preference again. That write and the broadcast behind it cannot be rolled back, so a commit that fails would leave the wallet in the ledger and the preference already moved off it",
            bodyOf(source, deleteInTxnSig).contains("setCurrentWallet(")
        )
        assertTrue(
            "delete no longer resets the current wallet after the commit, so deleting the wallet in use leaves every query filtering on an id that is gone",
            bodyOf(source, deleteSig).contains("setCurrentWallet(")
        )
    }

    companion object {
        private val STRIPPED: Pattern = Pattern.compile(
            "\"(?:\\\\.|[^\"\\\\])*\"|//.*?$|/[*].*?[*]/",
            Pattern.DOTALL or Pattern.MULTILINE
        )

        private val WRITE_METHODS = arrayOf(
            arrayOf("public Uri insert(", "database -> insertInTransaction(database,"),
            arrayOf("public int delete(", "database -> deleteInTransaction(database,"),
            arrayOf("public int update(", "database -> updateInTransaction(database,")
        )

        private val TRANSACTION_BODIES = arrayOf(
            "private Uri insertInTransaction(",
            "private int deleteInTransaction(",
            "private int updateInTransaction("
        )

        private fun findSourceFile(relativePath: String): File {
            var file = File(relativePath)
            if (!file.exists()) {
                file = File("app/$relativePath")
            }
            return file
        }
    }
}
