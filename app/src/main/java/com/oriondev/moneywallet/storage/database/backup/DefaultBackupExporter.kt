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
import com.oriondev.moneywallet.storage.database.DatabaseExporter
import com.oriondev.moneywallet.storage.database.ExportException
import com.oriondev.moneywallet.storage.database.SQLDatabaseExporter
import com.oriondev.moneywallet.storage.database.json.JSONDatabaseExporter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import net.lingala.zip4j.core.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.util.Zip4jConstants
import org.apache.commons.io.FileUtils

class DefaultBackupExporter @Throws(ExportException::class) constructor(
    contentResolver: ContentResolver,
    backupFile: File,
    private val password: String?
) : AbstractBackupExporter(contentResolver, backupFile) {

    companion object {
        private const val INTERNAL_BACKUP_DATABASE = BackupManager.FileStructure.FILE_DATABASE
    }

    @Throws(ExportException::class)
    override fun exportDatabase(tempFolder: File) {
        var exporter: DatabaseExporter? = null
        val tempFile = File(tempFolder, INTERNAL_BACKUP_DATABASE)
        try {
            if (!tempFile.exists() && !tempFile.createNewFile()) {
                throw ExportException("Failed to create temporary file. Probably the internal storage of the device is full.")
            }
            exporter = JSONDatabaseExporter(FileOutputStream(tempFile))
            val resolver = contentResolver
            exporter.exportHeader()
            exporter.exportCurrencies(SQLDatabaseExporter.getAllCurrencies(resolver))
            exporter.exportWallets(SQLDatabaseExporter.getAllWallets(resolver))
            exporter.exportCategories(SQLDatabaseExporter.getAllCategories(resolver))
            exporter.exportEvents(SQLDatabaseExporter.getAllEvents(resolver))
            exporter.exportPlaces(SQLDatabaseExporter.getAllPlaces(resolver))
            exporter.exportPeople(SQLDatabaseExporter.getAllPeople(resolver))
            exporter.exportEventPeople(SQLDatabaseExporter.getAllEventPeople(resolver))
            exporter.exportDebts(SQLDatabaseExporter.getAllDebt(resolver))
            exporter.exportDebtPeople(SQLDatabaseExporter.getAllDebtPeople(resolver))
            exporter.exportBudgets(SQLDatabaseExporter.getAllBudget(resolver))
            exporter.exportBudgetWallets(SQLDatabaseExporter.getAllBudgetWallets(resolver))
            exporter.exportBudgetCategories(SQLDatabaseExporter.getAllBudgetCategories(resolver))
            exporter.exportCategoryRules(SQLDatabaseExporter.getAllCategoryRules(resolver))
            exporter.exportSavings(SQLDatabaseExporter.getAllSavings(resolver))
            exporter.exportRecurrentTransactions(SQLDatabaseExporter.getAllRecurrentTransactions(resolver))
            exporter.exportRecurrentTransfers(SQLDatabaseExporter.getAllRecurrentTransfers(resolver))
            exporter.exportTransactions(SQLDatabaseExporter.getAllTransactions(resolver))
            exporter.exportTransactionPeople(SQLDatabaseExporter.getAllTransactionPeople(resolver))
            exporter.exportTransactionModels(SQLDatabaseExporter.getAllTransactionModels(resolver))
            exporter.exportTransfers(SQLDatabaseExporter.getAllTransfers(resolver))
            exporter.exportTransferPeople(SQLDatabaseExporter.getAllTransferPeople(resolver))
            exporter.exportTransferModels(SQLDatabaseExporter.getAllTransferModels(resolver))
            exporter.exportAttachments(SQLDatabaseExporter.getAllAttachments(resolver))
            exporter.exportTransactionAttachments(SQLDatabaseExporter.getAllTransactionAttachments(resolver))
            exporter.exportTransferAttachments(SQLDatabaseExporter.getAllTransferAttachments(resolver))
        } catch (e: IOException) {
            throw ExportException(e.message)
        } finally {
            if (exporter != null) {
                try {
                    exporter.close()
                } catch (ignore: Exception) {
                    // ignore
                }
            }
        }
        try {
            val zipFile = ZipFile(backupFile)
            zipFile.addFile(tempFile, generateZipParameters(BackupManager.FileStructure.FOLDER_DATABASES))
        } catch (e: ZipException) {
            throw ExportException(e.message)
        }
        FileUtils.deleteQuietly(tempFile)
    }

    @Throws(ExportException::class)
    override fun exportAttachmentFiles(fileList: List<File>) {
        try {
            val zipFile = ZipFile(backupFile)
            if (!zipFile.isValidZipFile) {
                throw ExportException("Invalid backup file: not a zip file")
            }
            val zipParameters = generateZipParameters(BackupManager.FileStructure.FOLDER_ATTACHMENTS)
            for (attachment in fileList) {
                zipFile.addFile(attachment, zipParameters)
            }
        } catch (e: ZipException) {
            throw ExportException(e.message)
        }
    }

    private fun generateZipParameters(root: String): ZipParameters {
        val parameters = ZipParameters()
        parameters.rootFolderInZip = root
        parameters.compressionMethod = Zip4jConstants.COMP_DEFLATE
        parameters.compressionLevel = Zip4jConstants.DEFLATE_LEVEL_NORMAL
        if (!password.isNullOrEmpty()) {
            parameters.isEncryptFiles = true
            parameters.encryptionMethod = Zip4jConstants.ENC_METHOD_AES
            parameters.aesKeyStrength = Zip4jConstants.AES_STRENGTH_256
            parameters.setPassword(password)
        }
        return parameters
    }
}
