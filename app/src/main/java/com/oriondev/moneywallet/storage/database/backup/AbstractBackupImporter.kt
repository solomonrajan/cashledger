/*
 * Copyright (c) 2026 solomonrajan.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.oriondev.moneywallet.storage.database.backup

import android.content.ContentResolver
import android.content.Context
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.ImportException
import com.oriondev.moneywallet.storage.database.SQLDatabaseImporter
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import java.io.File
import java.io.IOException
import org.apache.commons.io.FileUtils

abstract class AbstractBackupImporter(
    protected val context: Context,
    val backupFile: File
) {

    companion object {
        /**
         * The aside copy the design before this one made, kept only long enough to roll a failed
         * restore back. Nothing writes it any more.
         */
        private const val ABANDONED_BACKUP_COPY = "database.bk"

        private fun deleteDatabaseAndJournal(database: File) {
            FileUtils.deleteQuietly(database)
            FileUtils.deleteQuietly(journalOf(database))
        }

        private fun journalOf(database: File): File {
            return File(database.path + "-journal")
        }
    }

    protected open val contentResolver: ContentResolver
        get() = context.contentResolver

    /**
     * This method imports all the data included in the backup file inside the main database.
     */
    @Throws(ImportException::class)
    open fun importDatabase(temporaryFolder: File, databaseFolder: File) {
        val staged = File(databaseFolder, SQLDatabaseImporter.STAGING_DATABASE_NAME)
        try {
            importDatabase(temporaryFolder)
            promoteStagedDatabase(databaseFolder, staged)
        } catch (e: ImportException) {
            discardStagedDatabase(staged)
            throw e
        } catch (e: RuntimeException) {
            discardStagedDatabase(staged)
            throw e
        } finally {
            DataContentProvider.closeStagingDatabase()
        }
        PreferenceManager.setCollapsedCategories(emptySet())
    }

    @Throws(ImportException::class)
    protected abstract fun importDatabase(temporaryFolder: File)

    @Throws(ImportException::class)
    open fun importAttachments(attachmentFolder: File) {
        try {
            if (attachmentFolder.exists()) {
                FileUtils.cleanDirectory(attachmentFolder)
            } else {
                FileUtils.forceMkdir(attachmentFolder)
            }
            importAttachmentFiles(attachmentFolder)
        } catch (e: IOException) {
            throw ImportException(e.message)
        }
    }

    @Throws(IOException::class, ImportException::class)
    protected abstract fun importAttachmentFiles(attachmentFolder: File)

    /**
     * Sends this thread to the staging file, from here to the end of the import. Each importer
     * calls it once it has opened its backup file and read enough of it to be worth trusting,
     * and before the first row goes in.
     */
    open fun beginStagedImport() {
        deleteDatabaseAndJournal(context.getDatabasePath(SQLDatabaseImporter.STAGING_DATABASE_NAME))
        DataContentProvider.openStagingDatabase(context, SQLDatabaseImporter.STAGING_DATABASE_NAME)
    }

    /**
     * Puts the staged import in place.
     */
    @Throws(ImportException::class)
    private fun promoteStagedDatabase(databaseFolder: File, staged: File) {
        val database = File(databaseFolder, SQLDatabaseImporter.DATABASE_NAME)
        DataContentProvider.closeStagingDatabase()
        val promoted = BooleanArray(1)
        DataContentProvider.replaceDatabaseFile(context) {
            promoted[0] = staged.renameTo(database)
            if (promoted[0]) {
                FileUtils.deleteQuietly(journalOf(database))
                FileUtils.deleteQuietly(journalOf(staged))
            }
        }
        if (!promoted[0]) {
            throw ImportException("Cannot put the imported database in place")
        }
        deleteDatabaseAndJournal(File(databaseFolder, ABANDONED_BACKUP_COPY))
    }

    /**
     * Sends this thread back to the live database and throws the staged import away.
     */
    private fun discardStagedDatabase(staged: File) {
        DataContentProvider.closeStagingDatabase()
        deleteDatabaseAndJournal(staged)
    }
}
