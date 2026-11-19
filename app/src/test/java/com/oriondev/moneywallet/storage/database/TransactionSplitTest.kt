package com.oriondev.moneywallet.storage.database

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.oriondev.moneywallet.storage.database.backup.DefaultBackupExporter
import com.oriondev.moneywallet.storage.database.backup.DefaultBackupImporter
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * The parts a transaction is split into, through a backup and through every delete that can take
 * them. Each expected value is read from the fixture arrays below and never from the code the
 * case runs.
 */
@RunWith(RobolectricTestRunner::class)
class TransactionSplitTest {

    private lateinit var context: Context
    private lateinit var resolver: ContentResolver

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PreferenceManager.initialize(context)
        TestDatabases.useFreshDatabase(context)
        resolver = context.contentResolver
        val db = SQLDatabase.getShared(context).writableDatabase
        insertWallet(db, CASH, "Cash")
        insertWallet(db, BANK, "Bank")
        insertCategory(db, FOOD, "Food")
        insertCategory(db, HOUSEHOLD, "Household")
        insertCategory(db, PHARMACY, "Pharmacy")
        for (parent in PARENTS) {
            db.execSQL(
                "INSERT INTO transactions (transaction_id, transaction_money, " +
                    "transaction_date, transaction_category, transaction_direction, " +
                    "transaction_type, transaction_wallet, uuid, last_edit, deleted) " +
                    "VALUES (?, ?, '2026-09-01 12:00:00', ?, ?, ?, ?, ?, ?, 0)",
                arrayOf<Any>(
                    parent[0], parent[3], parent[2], Schema.Direction.EXPENSE,
                    Contract.TransactionType.STANDARD, parent[1], uuid("transaction", parent[0]),
                    EDIT
                )
            )
        }
        for (i in PARTS.indices) {
            db.execSQL(
                "INSERT INTO transaction_splits (split_id, _transaction, split_category, " +
                    "split_money, split_note, uuid, last_edit, deleted) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, 0)",
                arrayOf(
                    PARTS[i][0], PARTS[i][1], PARTS[i][2], PARTS[i][3], NOTES[i],
                    uuid("split", PARTS[i][0]), EDIT
                )
            )
        }
    }

    /**
     * Every part comes back on the parent it left with, under the category it left with, carrying
     * the same money and note, and every parent keeps its total and its main category.
     */
    @Test
    fun aBackupCarriesEveryPartToItsOwnParent() {
        val backup = exportBackup()

        // the header is what an older release reads to refuse this file, and the parts have to sit
        // right after the transactions they name
        val json = readDatabaseJson(backup)
        assertEquals(5, JSONObject(json).getJSONObject("header").getInt("version_code"))
        val transactions = json.indexOf("\"transactions\"")
        val splits = json.indexOf("\"transaction_splits\"")
        val people = json.indexOf("\"transaction_people\"")
        assertTrue(json, transactions >= 0 && transactions < splits && splits < people)

        restore(backup)
        // a restore numbers its rows afresh, so a fixture id still present means these reads are
        // landing on the ledger the backup was taken from
        assertEquals(0L, count("transactions", "transaction_id = " + PARENTS[0][0]))

        val expected = ArrayList<String>()
        for (i in PARTS.indices) {
            val parent = parentOf(PARTS[i][1])
            expected.add(
                uuid("split", PARTS[i][0]) + " parent=" + uuid("transaction", parent[0]) +
                    " total=" + parent[3] + " main=" + uuid("category", parent[2]) +
                    " category=" + uuid("category", PARTS[i][2]) + " money=" + PARTS[i][3] +
                    " note=" + NOTES[i]
            )
        }
        Collections.sort(expected)
        val restored = ArrayList<String>()
        val cursor = db().rawQuery(
            "SELECT s.uuid, t.uuid, t.transaction_money, c.uuid, " +
                "pc.uuid, s.split_money, s.split_note FROM transaction_splits AS s " +
                "JOIN transactions AS t ON s._transaction = t.transaction_id " +
                "JOIN categories AS c ON t.transaction_category = c.category_id " +
                "JOIN categories AS pc ON s.split_category = pc.category_id",
            null
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                restored.add(
                    c.getString(0) + " parent=" + c.getString(1) +
                        " total=" + c.getLong(2) + " main=" + c.getString(3) +
                        " category=" + c.getString(4) + " money=" + c.getLong(5) +
                        " note=" + c.getString(6)
                )
            }
        }
        Collections.sort(restored)
        assertEquals(expected, restored)
        // the join above reaches a parent only through its parts, so the parents are counted alone
        assertEquals(
            PARENTS.size.toLong(),
            count("transactions", "transaction_type = " + Contract.TransactionType.STANDARD)
        )
    }

    /** A file written before parts existed carries no array for them, and restores as it did. */
    @Test
    fun aVersionFourBackupStillRestores() {
        val backup = writeBackup(4)
        restore(backup)
        assertEquals(
            1L,
            count("transactions", "uuid = 'old-transaction' AND transaction_money = 1234")
        )
        assertEquals(0L, count("transaction_splits", null))
    }

    /**
     * A file from a release newer than this one is refused by its header before any row is read,
     * which is what a release that predates parts does to a version 5 file.
     */
    @Test
    fun aBackupFromANewerReleaseIsRefusedByItsHeader() {
        val backup = writeBackup(6)
        try {
            restore(backup)
            fail("a backup newer than this release was restored")
        } catch (expected: ImportException) {
            val message = expected.message.orEmpty()
            assertTrue(message, message.contains("newer version"))
        }
        // nothing reached the staging file, and the ledger it would have replaced is untouched
        assertEquals(0L, count("currencies", null))
        assertEquals(0L, count("wallets", null))
        DataContentProvider.closeStagingDatabase()
        assertEquals(PARTS.size.toLong(), count("transaction_splits", null))
    }

    /**
     * The wallet delete removes its transactions in one statement without going through the per
     * transaction delete, so only the cascade on the parent key takes their parts.
     */
    @Test
    fun deletingAWalletTakesThePartsOfItsTransactions() {
        resolver.delete(ContentUris.withAppendedId(DataContentProvider.CONTENT_WALLETS, CASH), null, null)
        assertEquals(partsOfWallet(BANK), count("transaction_splits", null))
        assertEquals(0L, count("transaction_splits", "_transaction IN (920, 921)"))
    }

    /**
     * A category only parts use leaves with them, and the parents stay with their totals and main
     * categories, so the money of the lost part reads under the main category again. The delete is
     * the bare statement a release that predates parts runs, since this one refuses a category a
     * part uses.
     */
    @Test
    fun deletingACategoryTakesThePartsUsingItAndLeavesTheirParents() {
        SQLDatabase.getShared(context).writableDatabase.execSQL(
            "DELETE FROM categories WHERE category_id = $PHARMACY"
        )
        var left = 0L
        for (part in PARTS) {
            if (part[2] != PHARMACY) {
                left++
            }
        }
        assertEquals(left, count("transaction_splits", null))
        assertEquals(0L, count("transaction_splits", "split_category = $PHARMACY"))
        for (parent in PARENTS) {
            assertEquals(
                1L,
                count(
                    "transactions",
                    "transaction_id = " + parent[0] +
                        " AND transaction_money = " + parent[3] +
                        " AND transaction_category = " + parent[2]
                )
            )
        }
    }

    /** A transaction delete removes its parts outright, the way it removes its people. */
    @Test
    fun deletingATransactionRemovesItsParts() {
        resolver.delete(ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSACTIONS, 920), null, null)
        var left = 0L
        for (part in PARTS) {
            if (part[1] != 920L) {
                left++
            }
        }
        // read with no flag filter, so a part flagged deleted and left behind would be counted
        assertEquals(0L, count("transaction_splits", "_transaction = 920"))
        assertEquals(left, count("transaction_splits", null))
    }

    /**
     * Accepting the rescale when a currency's decimals change moves every part with its total, or
     * the remainder under the main category would take the difference. The dollar wallet's row is
     * the control, since a pass that walked every part would move it too.
     */
    @Test
    fun changingTheDecimalsOfACurrencyRescalesItsParts() {
        val db = SQLDatabase.getShared(context).writableDatabase
        // pinned, so the factor asserted below is known
        db.execSQL("UPDATE currencies SET currency_decimals = 2 WHERE currency_iso = 'EUR'")
        assertEquals(1L, count("currencies", "currency_iso = 'EUR' AND currency_decimals = 2"))
        db.execSQL(
            "INSERT INTO wallets (wallet_id, wallet_name, wallet_currency, uuid, " +
                "last_edit, deleted) VALUES (903, 'Dollars', 'USD', 'wallet-903', ?, 0)",
            arrayOf<Any>(EDIT)
        )
        db.execSQL(
            "INSERT INTO transactions (transaction_id, transaction_money, " +
                "transaction_date, transaction_category, transaction_direction, " +
                "transaction_type, transaction_wallet, uuid, last_edit, deleted) " +
                "VALUES (923, 600, '2026-09-01 12:00:00', ?, ?, ?, 903, 'transaction-923', ?, 0)",
            arrayOf(FOOD, Schema.Direction.EXPENSE, Contract.TransactionType.STANDARD, EDIT)
        )
        db.execSQL(
            "INSERT INTO transaction_splits (split_id, _transaction, split_category, " +
                "split_money, uuid, last_edit, deleted) " +
                "VALUES (934, 923, ?, 200, 'split-934', ?, 0)",
            arrayOf(PHARMACY, EDIT)
        )

        val values = ContentValues()
        values.put(Contract.Currency.DECIMALS, 3)
        values.put(Contract.Currency.FIX_MONEY_DECIMALS, true)
        assertEquals(
            1,
            resolver.update(
                Uri.withAppendedPath(DataContentProvider.CONTENT_CURRENCIES, "EUR"),
                values,
                null,
                null
            )
        )

        // two decimals to three, so every euro amount is ten times what the fixture wrote
        for (part in PARTS) {
            assertEquals(
                1L,
                count(
                    "transaction_splits",
                    "split_id = " + part[0] + " AND split_money = " + part[3] * 10
                )
            )
        }
        for (parent in PARENTS) {
            assertEquals(
                1L,
                count(
                    "transactions",
                    "transaction_id = " + parent[0] + " AND transaction_money = " + parent[3] * 10
                )
            )
        }
        assertEquals(1L, count("transaction_splits", "split_id = 934 AND split_money = 200"))
        assertEquals(1L, count("transactions", "transaction_id = 923 AND transaction_money = 600"))
    }

    private fun partsOfWallet(wallet: Long): Long {
        var parts = 0L
        for (part in PARTS) {
            if (parentOf(part[1])[1] == wallet) {
                parts++
            }
        }
        return parts
    }

    private fun parentOf(id: Long): LongArray {
        for (parent in PARENTS) {
            if (parent[0] == id) {
                return parent
            }
        }
        throw IllegalArgumentException("no parent $id")
    }

    private fun exportBackup(): File {
        val folder = File(context.cacheDir, "split-export")
        assertTrue(folder.mkdirs() || folder.isDirectory)
        val backup = File(context.cacheDir, "splits.mwbx")
        DefaultBackupExporter(resolver, backup, null).exportDatabase(folder)
        return backup
    }

    /**
     * Imports into the staging file and leaves this thread pointed at it, so every read after
     * this sees what the import wrote. The promote that follows in the app is a rename over the
     * live file, which reads no row, and which File.renameTo refuses on a Windows host while the
     * live file exists.
     */
    private fun restore(backup: File) {
        val folder = File(context.cacheDir, "split-import")
        assertTrue(folder.mkdirs() || folder.isDirectory)
        DefaultBackupImporter(context, backup, null).importDatabase(folder)
    }

    @After
    fun tearDown() {
        DataContentProvider.closeStagingDatabase()
    }

    /**
     * A backup file as a release writing the given version would, holding one currency, one wallet,
     * one category and one transaction and every other array empty. Up to version 4 there is no array of parts.
     */
    private fun writeBackup(version: Int): File {
        val arrays = arrayOf(
            "currencies", "wallets", "categories", "events", "places", "people",
            "event_people", "debts", "debt_people", "budgets", "budget_wallets",
            "budget_categories", "category_rules", "savings", "recurrent_transactions",
            "recurrent_transfers", "transactions", "transaction_people", "transaction_models",
            "transfers", "transfer_people", "transfer_models", "attachments",
            "transaction_attachment", "transfer_attachment"
        )
        val json = StringBuilder("{\"header\":{\"version_code\":$version}")
        for (array in arrays) {
            json.append(",\"").append(array).append("\":[")
            if ("currencies" == array) {
                json.append(
                    "{\"iso\":\"XTS\",\"name\":\"Test\",\"symbol\":\"X\",\"decimals\":2," +
                        "\"last_edit\":$EDIT}"
                )
            } else if ("wallets" == array) {
                json.append(
                    "{\"id\":\"old-wallet\",\"name\":\"Cash\",\"currency\":\"EUR\"," +
                        "\"start_money\":0,\"last_edit\":$EDIT}"
                )
            } else if ("categories" == array) {
                json.append(
                    "{\"id\":\"old-category\",\"name\":\"Food\",\"icon\":" +
                        JSONObject.quote(ICON) + ",\"type\":" + Schema.CategoryType.EXPENSE +
                        ",\"last_edit\":$EDIT}"
                )
            } else if ("transactions" == array) {
                json.append(
                    "{\"id\":\"old-transaction\",\"money\":1234," +
                        "\"date\":\"2026-09-01 12:00:00\",\"category\":\"old-category\"," +
                        "\"direction\":" + Schema.Direction.EXPENSE + ",\"type\":" +
                        Contract.TransactionType.STANDARD + ",\"wallet\":\"old-wallet\"," +
                        "\"last_edit\":$EDIT}"
                )
            }
            json.append("]")
            if ("transactions" == array && version >= 5) {
                json.append(",\"transaction_splits\":[]")
            }
        }
        json.append("}")
        val backup = File(context.cacheDir, "version$version.mwbx")
        val zip = ZipOutputStream(FileOutputStream(backup))
        try {
            zip.putNextEntry(ZipEntry("databases/database.json"))
            zip.write(json.toString().toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()
        } finally {
            zip.close()
        }
        return backup
    }

    private fun readDatabaseJson(backup: File): String {
        val zip = ZipFile(backup)
        try {
            val entry = zip.getEntry("databases/database.json")
            val stream = zip.getInputStream(entry)
            val bytes = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var read: Int
            while (stream.read(buffer).also { read = it } != -1) {
                bytes.write(buffer, 0, read)
            }
            return bytes.toString(StandardCharsets.UTF_8.name())
        } finally {
            zip.close()
        }
    }

    private fun db(): SQLiteDatabase {
        return SQLDatabase.getShared(context).readableDatabase
    }

    private fun count(table: String, selection: String?): Long {
        val cursor = db().rawQuery(
            "SELECT COUNT(*) FROM " + table +
                if (selection != null) " WHERE $selection" else "",
            null
        )
        cursor.use { c ->
            assertTrue(c.moveToFirst())
            return c.getLong(0)
        }
    }

    private fun insertWallet(db: SQLiteDatabase, id: Long, name: String) {
        db.execSQL(
            "INSERT INTO wallets (wallet_id, wallet_name, wallet_currency, uuid, " +
                "last_edit, deleted) VALUES (?, ?, 'EUR', ?, ?, 0)",
            arrayOf<Any>(id, name, uuid("wallet", id), EDIT)
        )
    }

    private fun insertCategory(db: SQLiteDatabase, id: Long, name: String) {
        db.execSQL(
            "INSERT INTO categories (category_id, category_name, category_icon, " +
                "category_type, uuid, last_edit, deleted) VALUES (?, ?, ?, ?, ?, ?, 0)",
            arrayOf(id, name, ICON, Schema.CategoryType.EXPENSE, uuid("category", id), EDIT)
        )
    }

    companion object {
        private const val ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}"
        private const val EDIT = 1565000000000L

        private const val CASH = 901L
        private const val BANK = 902L
        private const val FOOD = 910L
        private const val HOUSEHOLD = 911L
        private const val PHARMACY = 912L

        /** id, wallet, main category, total. */
        private val PARENTS = arrayOf(
            longArrayOf(920, CASH, FOOD, 10000),
            longArrayOf(921, CASH, HOUSEHOLD, 4000),
            longArrayOf(922, BANK, FOOD, 800)
        )

        /** id, parent, category, money. The note of each is in NOTES at the same index. */
        private val PARTS = arrayOf(
            longArrayOf(930, 920, HOUSEHOLD, 3000),
            longArrayOf(931, 920, PHARMACY, 2500),
            longArrayOf(932, 921, FOOD, 1000),
            longArrayOf(933, 922, PHARMACY, 300)
        )
        private val NOTES = arrayOf("soap", null, "bread", "plasters")

        private fun uuid(kind: String, id: Long): String {
            return "$kind-$id"
        }
    }
}
