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

import android.content.Context
import com.oriondev.moneywallet.storage.database.ImportException
import com.oriondev.moneywallet.storage.database.legacy.LegacyDatabaseImporter
import java.io.File
import java.io.IOException
import net.lingala.zip4j.core.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.model.FileHeader
import org.apache.commons.io.FileUtils

class LegacyBackupImporter(
    context: Context,
    backupFile: File
) : AbstractBackupImporter(context, backupFile) {

    companion object {
        private val LEGACY_BACKUP_PASSWORD = charArrayOf(
            'Q', '9', 'Y', 'y', 'v', '1', 'L', 'c', 'O', 'u', 'E', '1', '8', 'i',
            'T', '0', 'n', '8', 'K', 'b', 'b', 'V', '/', 'i', '2', '3', '5', 'g',
            'k', 'Y', 'e', 'K', 'N', 'D', '8', 'j', '7', 'J', '9', 'j', 'o', 'J',
            'w', 'I', 'D', 'A', 'Q', 'A', 'B', 'f', 'H', 'R', '3', '3', '/', 's',
            'k', 'g', 'l', 'U', '3', 'o', 'B', 'E', 'v', 'k', 'Z', 'R', 'C', 'H',
            'N', 's', 'q', 'y', 'O', 'E', 'l', 'W', '+', 'l', 'v', 'I', 'I', 'm',
            'H', 'u', 'u', 'J', 'c', 'v', 'T', 'Z', 'w', 'A', 'Q', 'Q', 'g', 'o',
            '8', 'E', 'X', 'd', 'J', 'O', 'Q', 'N', 'v', '2', '3', 'p', 'm', 'U',
            'Q', 'E', '9', 'q', 'n', 'Z', 'f', 't', '/', 'x', 'P', 'U', 'o', 'E',
            '8', 'u', 'c', 'Z', 'E', 's', '/', 'A', '8', 'd', 'j', 'q', 'I', '9',
            'W', 'T', 'E', 'A', 'E', 't', 'e', 'M', 'I', 'I', 'B', 'I', 'j', 'A',
            'N', 'B', 'g', 'k', 'q', 'h', 'k', 'i', 'G', '9', 'w', '0', 'B', 'A',
            'Q', 'E', 'F', 'A', 'A', 'O', 'C', 'A', 'Q', '8', 'A', 'M', 'I', 'I',
            'B', 'C', 'g', 'K', 'C', 'A', 'Q', 'E', 'A', 'j', 'j', 'a', 'g', 's',
            'e', '/', 'p', '2', 'z', '5', '5', 'c', 'J', 'L', 'J', 'V', 'Q', 'B',
            'F', 'Q', '9', '3', 't', 'N', 'I', 'n', 'B', 'D', '2', 'n', '5', 'j',
            '6', 'M', '4', 'r', 'i', 'x', 'j', 'r', 'N', 'a', 'I', 'T', 'n', 'P',
            'k', 'Q', 'P', 'G', 'W', 'Z', 's', 'D', 'F', 'G', 'X', 'q', '5', 't',
            's', 'x', 'N', 'J', 'b', '7', '1', 's', 'p', '7', 'D', 'D', 'B', 'k',
            'k', 'R', 's', 'z', 'Y', 'S', 'z', 'd', 'P', 'e', 'b', 'p', '6', '2',
            'J', 'm', 'M', 'B', 'X', 'F', '5', 'b', '2', '7', 'I', '+', 'i', 'u',
            'q', 'T', 'k', 'r', 'W', 'F', 'l', 'q', 'N', 'H', '9', 'W', 'Y', 'r',
            'N', 'd', 'k', 'N', 'l', 'c', '/', 'Q', 'J', '2', '6', 'b', '6', 'i',
            'K', 'c', 'p', 'D', 'y', 'p', 'M', 'L', '9', 'v', 'c', '9', 'H', 'k',
            'P', 'I', '4', '0', 'K', 'M', '4', 't', 'i', 'h', 'u', 'v', 'L', 'A',
            's', 'h', '0', '/', 'h', 'A', '7', 'G', 'n', '6', '6', '+', 'e', 'N',
            'q', 'o', 'v', 'w', 'B', '/', 'Q', '5', 'I', 'y', 'D', '+', 'E', 'V',
            'O', 'J', 'x', 'x', 'D', 'p', 'b', 'Y', 'j', 'h', 'R', 'h', 'S', '2'
        )

        private const val LEGACY_BACKUP_DATABASE_PATH = "database/MoneyWallet"
        private const val LEGACY_BACKUP_IMAGE_FOLDER_PATH = "images/"
        private const val TEMPORARY_DATABASE_FILE = "legacy_database.tmp"
    }

    private var databaseImporter: LegacyDatabaseImporter? = null

    @Throws(ImportException::class)
    override fun importDatabase(temporaryFolder: File) {
        val temporaryDatabase = File(temporaryFolder, TEMPORARY_DATABASE_FILE)
        try {
            val zipFile = ZipFile(backupFile)
            if (!zipFile.isValidZipFile) {
                throw ImportException("Invalid backup file: not a zip file")
            }
            val header = zipFile.getFileHeader(LEGACY_BACKUP_DATABASE_PATH)
                ?: throw ImportException("Invalid backup file: database file not found")
            if (header.isEncrypted) {
                header.password = LEGACY_BACKUP_PASSWORD
            }
            beginStagedImport()
            zipFile.extractFile(header, temporaryFolder.path, null, TEMPORARY_DATABASE_FILE)
            val resolver = contentResolver
            val importer = LegacyDatabaseImporter(temporaryDatabase)
            databaseImporter = importer
            importer.importWallets(resolver)
            importer.importCategories(resolver)
            importer.importEvents(resolver)
            importer.importPlaces(resolver)
            importer.importPeople(resolver)
            importer.importEventPeople(resolver)
            importer.importDebts(resolver)
            importer.importDebtPeople(resolver)
            importer.importBudgets(resolver)
            importer.importBudgetWallets(resolver)
            importer.importBudgetCategories(resolver)
            importer.importCategoryRules(resolver)
            importer.importSavings(resolver)
            importer.importRecurrentTransactions(resolver)
            importer.importRecurrentTransfers(resolver)
            importer.importTransactions(resolver)
            importer.importTransactionPeople(resolver)
            importer.importTransactionModels(resolver)
            importer.importTransfers(resolver)
            importer.importTransferPeople(resolver)
            importer.importTransferModels(resolver)
            importer.importAttachments(resolver)
            importer.importTransactionAttachments(resolver)
            importer.importTransferAttachments(resolver)
        } catch (e: ZipException) {
            throw ImportException(e.message)
        } finally {
            FileUtils.deleteQuietly(temporaryDatabase)
        }
    }

    @Throws(IOException::class, ImportException::class)
    override fun importAttachmentFiles(attachmentFolder: File) {
        try {
            val zipFile = ZipFile(backupFile)
            if (!zipFile.isValidZipFile) {
                throw ImportException("Invalid backup file: not a zip file")
            }
            val resolver = contentResolver
            val importer = databaseImporter
            val headers = zipFile.fileHeaders
            if (headers != null && importer != null) {
                for (obj in headers) {
                    val header = obj as? FileHeader ?: continue
                    if (header.isEncrypted) {
                        header.password = LEGACY_BACKUP_PASSWORD
                    }
                    val path = header.fileName
                    if (path != null && path.startsWith(LEGACY_BACKUP_IMAGE_FOLDER_PATH)) {
                        val name = path.substring(LEGACY_BACKUP_IMAGE_FOLDER_PATH.length)
                        if (!name.contains("/")) {
                            val identifier = importer.getAttachmentId(name)
                            if (identifier != null) {
                                zipFile.extractFile(header, attachmentFolder.path, null, identifier)
                                val attachment = File(attachmentFolder, identifier)
                                importer.importAttachment(resolver, identifier, attachment.length())
                            }
                        }
                    }
                }
            }
        } catch (e: ZipException) {
            throw ImportException(e.message)
        }
    }
}
