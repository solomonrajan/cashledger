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
import com.oriondev.moneywallet.storage.database.ExportException
import com.oriondev.moneywallet.storage.database.SQLDatabaseExporter
import java.io.File

abstract class AbstractBackupExporter(
    protected val contentResolver: ContentResolver,
    val backupFile: File
) {

    @Throws(ExportException::class)
    abstract fun exportDatabase(tempFolder: File)

    @Throws(ExportException::class)
    open fun exportAttachments(attachmentFolder: File) {
        val cursor = SQLDatabaseExporter.getAllAttachments(contentResolver)
        cursor?.use {
            val fileList = mutableListOf<File>()
            while (it.moveToNext()) {
                val attachment = SQLDatabaseExporter.getAttachment(it)
                val fileName = attachment?.mFile
                if (!fileName.isNullOrEmpty()) {
                    val file = File(attachmentFolder, fileName)
                    if (file.exists()) {
                        fileList.add(file)
                    }
                }
            }
            exportAttachmentFiles(fileList)
        }
    }

    @Throws(ExportException::class)
    protected abstract fun exportAttachmentFiles(fileList: List<File>)
}
