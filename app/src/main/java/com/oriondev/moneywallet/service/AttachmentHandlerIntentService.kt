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

package com.oriondev.moneywallet.service

import android.app.IntentService
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.oriondev.moneywallet.broadcast.LocalAction
import com.oriondev.moneywallet.model.Attachment
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Created by andrea on 27/03/18.
 */
@Suppress("DEPRECATION")
class AttachmentHandlerIntentService : IntentService("AttachmentHandlerIntentService") {

    companion object {
        const val ATTACHMENT: String = "AttachmentHandlerIntentService::Parameters::Attachment"
        const val ACTION: String = "AttachmentHandlerIntentService::Parameters::Action"
        const val ERROR: String = "AttachmentHandlerIntentService::Parameters::Error"

        const val ACTION_CREATE: Int = 1
        const val ACTION_DELETE: Int = 2
    }

    private var mReporter: TaskReporter? = null

    private fun getReporter(): TaskReporter {
        val current = mReporter
        if (current != null) {
            return current
        }
        val created = TaskReporter(this)
        mReporter = created
        return created
    }

    override fun onHandleIntent(intent: Intent?) {
        if (intent != null) {
            mReporter = TaskReporter(this)
            val action = intent.getIntExtra(ACTION, 0)
            val attachment: Attachment? = intent.getParcelableExtra(ATTACHMENT)
            notifyOperationStarted(attachment, action)
            try {
                when (action) {
                    ACTION_CREATE -> {
                        if (attachment == null) {
                            throw IOException("Missing attachment parameter")
                        }
                        val sourceUri = intent.data ?: throw IOException("Missing attachment URI")
                        val uri = createAttachment(sourceUri, attachment)
                            ?: throw IOException("Failed to insert attachment")
                        attachment.id = ContentUris.parseId(uri)
                    }
                    ACTION_DELETE -> {
                        if (attachment == null) {
                            throw IOException("Missing attachment parameter")
                        }
                        deleteAttachment(attachment)
                    }
                }
                notifyOperationFinished(attachment, action)
            } catch (e: IOException) {
                notifyOperationFailed(attachment, action, e.message)
            }
        }
    }

    @Throws(IOException::class)
    private fun getAttachmentFile(attachment: Attachment): File {
        val folder = File(getExternalFilesDir(null), "attachments")
        FileUtils.forceMkdir(folder)
        val file = File(folder, attachment.file)
        if (!file.exists() && !file.createNewFile()) {
            throw IOException("Failed to create a new file")
        }
        return file
    }

    @Throws(IOException::class)
    private fun createAttachment(uri: Uri, attachment: Attachment): Uri? {
        val contentResolver = contentResolver
        contentResolver.openInputStream(uri)?.use { inputStream ->
            FileOutputStream(getAttachmentFile(attachment)).use { outputStream ->
                IOUtils.copy(inputStream, outputStream)
            }
        }
        val contentValues = ContentValues()
        contentValues.put(Contract.Attachment.FILE, attachment.file)
        contentValues.put(Contract.Attachment.NAME, attachment.name)
        contentValues.put(Contract.Attachment.TYPE, attachment.type)
        contentValues.put(Contract.Attachment.SIZE, attachment.size)
        return contentResolver.insert(DataContentProvider.CONTENT_ATTACHMENTS, contentValues)
    }

    @Throws(IOException::class)
    private fun deleteAttachment(attachment: Attachment) {
        val contentResolver = contentResolver
        val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_ATTACHMENTS, attachment.id)
        contentResolver.delete(uri, null, null)
        FileUtils.forceDelete(getAttachmentFile(attachment))
    }

    private fun notifyOperationStarted(attachment: Attachment?, action: Int) {
        val extras = Bundle()
        extras.putParcelable(ATTACHMENT, attachment)
        extras.putInt(ACTION, action)
        getReporter().broadcast(LocalAction.ACTION_ATTACHMENT_OP_STARTED, extras)
    }

    private fun notifyOperationFinished(attachment: Attachment?, action: Int) {
        val extras = Bundle()
        extras.putParcelable(ATTACHMENT, attachment)
        extras.putInt(ACTION, action)
        getReporter().broadcast(LocalAction.ACTION_ATTACHMENT_OP_FINISHED, extras)
    }

    private fun notifyOperationFailed(attachment: Attachment?, action: Int, error: String?) {
        val extras = Bundle()
        extras.putParcelable(ATTACHMENT, attachment)
        extras.putInt(ACTION, action)
        extras.putString(ERROR, error)
        getReporter().broadcast(LocalAction.ACTION_ATTACHMENT_OP_FAILED, extras)
    }
}
