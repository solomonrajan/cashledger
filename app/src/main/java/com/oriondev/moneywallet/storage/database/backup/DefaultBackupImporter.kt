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
import com.oriondev.moneywallet.storage.database.DatabaseImporter
import com.oriondev.moneywallet.storage.database.ImportException
import com.oriondev.moneywallet.storage.database.json.JSONDatabaseImporter
import java.io.File
import java.io.IOException
import net.lingala.zip4j.core.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.io.ZipInputStream
import net.lingala.zip4j.model.FileHeader

class DefaultBackupImporter(
    context: Context,
    backupFile: File,
    private val password: String?
) : AbstractBackupImporter(context, backupFile) {

    @Throws(ImportException::class)
    override fun importDatabase(temporaryFolder: File) {
        var importer: DatabaseImporter? = null
        try {
            val zipFile = ZipFile(backupFile)
            if (!zipFile.isValidZipFile) {
                throw ImportException("Invalid backup file: not a zip file")
            }
            val path = BackupManager.FileStructure.FOLDER_DATABASES + BackupManager.FileStructure.FILE_DATABASE
            val header: FileHeader = zipFile.getFileHeader(path)
                ?: throw ImportException("Invalid backup file: database file not found")
            if (header.isEncrypted) {
                if (password != null) {
                    header.password = password.toCharArray()
                } else {
                    throw ImportException("Decryption filed: missing user password")
                }
            }
            beginStagedImport()
            val inputStream: ZipInputStream = zipFile.getInputStream(header)
            importer = JSONDatabaseImporter(inputStream)
            val resolver = contentResolver
            importer.importHeader()
            importer.importCurrencies(resolver)
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
            if (importer != null) {
                try {
                    importer.close()
                } catch (ignore: Exception) {
                    // ignore
                }
            }
        }
    }

    @Throws(IOException::class, ImportException::class)
    override fun importAttachmentFiles(attachmentFolder: File) {
        try {
            val zipFile = ZipFile(backupFile)
            if (!zipFile.isValidZipFile) {
                throw ImportException("Invalid backup file: not a zip file")
            }
            val headers = zipFile.fileHeaders
            if (headers != null) {
                for (obj in headers) {
                    val header = obj as? FileHeader ?: continue
                    if (header.isEncrypted) {
                        if (password != null) {
                            header.password = password.toCharArray()
                        } else {
                            throw ImportException("Decryption filed: missing user password")
                        }
                    }
                    val path = header.fileName
                    if (path != null && path.startsWith(BackupManager.FileStructure.FOLDER_ATTACHMENTS)) {
                        val name = path.substring(BackupManager.FileStructure.FOLDER_ATTACHMENTS.length)
                        if (!name.contains("/")) {
                            zipFile.extractFile(header, attachmentFolder.path, null, name)
                        }
                    }
                }
            }
        } catch (e: ZipException) {
            throw ImportException(e.message)
        }
    }
}
