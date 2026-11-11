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

package com.oriondev.moneywallet.api.saf

import android.content.Context
import android.content.UriPermission
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.oriondev.moneywallet.api.AbstractBackendServiceAPI
import com.oriondev.moneywallet.api.BackendException
import com.oriondev.moneywallet.model.IFile
import com.oriondev.moneywallet.model.SAFFile
import com.oriondev.moneywallet.utils.ProgressInputStream
import com.oriondev.moneywallet.utils.ProgressOutputStream
import org.apache.commons.io.IOUtils
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.LinkedList
import java.util.Locale

class SAFBackendServiceAPI @Throws(BackendException::class) constructor(
    private val mContext: Context
) : AbstractBackendServiceAPI<SAFFile>(SAFFile::class.java) {

    private val mRoot: DocumentFile?

    init {
        val uri = SAFBackendService.getUri(mContext)
            ?: throw BackendException("SAFBackendService not enabled")
        val permission = getPermissionFor(uri)
        if (permission == null || !(permission.isReadPermission && permission.isWritePermission)) {
            throw BackendException("Root uri not accessible (no permission)")
        }
        mRoot = DocumentFile.fromTreeUri(mContext, permission.uri)
    }

    private fun getPermissionFor(uri: Uri): UriPermission? {
        for (permission in mContext.contentResolver.persistedUriPermissions) {
            if (permission.uri == uri) {
                return permission
            }
        }
        return null
    }

    @Throws(BackendException::class)
    public override fun upload(
        folder: SAFFile?,
        file: File,
        listener: ProgressInputStream.UploadProgressListener
    ): SAFFile {
        val docFile = createDocumentFile(folder, file)
            ?: throw BackendException(
                "Couldn't create output file ${file.name} under ${folder?.getName() ?: "root directory"}"
            )

        try {
            openUploadInputStream(file, listener).use { inputStream ->
                openUploadOutputStream(docFile).use { out ->
                    if (out != null) {
                        IOUtils.copy(inputStream, out)
                    }
                }
            }
        } catch (e: IOException) {
            throw BackendException(
                "Failed to upload ${file.name} to ${docFile.uri}",
                e,
                true
            )
        }
        return SAFFile(docFile)
    }

    private fun openUploadOutputStream(docFile: DocumentFile): OutputStream? {
        return try {
            mContext.contentResolver.openOutputStream(docFile.uri)
        } catch (e: FileNotFoundException) {
            throw AssertionError("Document file should have been created!")
        }
    }

    @Throws(BackendException::class)
    private fun openUploadInputStream(
        file: File,
        listener: ProgressInputStream.UploadProgressListener
    ): InputStream {
        return try {
            ProgressInputStream(file, listener)
        } catch (e: FileNotFoundException) {
            throw BackendException("File '${file.name}' doesn't exist", e)
        } catch (e: IOException) {
            throw BackendException("Couldn't access file '${file.name}'", e)
        }
    }

    @Throws(BackendException::class)
    private fun createDocumentFile(folder: SAFFile?, file: File): DocumentFile? {
        val docFolder = if (folder == null) {
            mRoot ?: throw BackendException("Couldn't access root directory to create file ${file.name}")
        } else {
            DocumentFile.fromTreeUri(mContext, folder.getUri())
                ?: throw BackendException(
                    "Couldn't open folder ${folder.getUri()} to create file ${file.name}"
                )
        }
        return docFolder.createFile(getFileType(file), file.name)
    }

    private fun getFileType(file: File): String {
        val name = file.name
        val lastDot = name.lastIndexOf('.')
        if (lastDot >= 0) {
            val extension = name.substring(lastDot + 1).lowercase(Locale.ENGLISH)
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            if (mime != null) {
                return mime
            }
        }
        return "application/octet-stream"
    }

    @Throws(BackendException::class)
    public override fun download(
        folder: File,
        file: SAFFile,
        listener: ProgressOutputStream.DownloadProgressListener
    ): File {
        val destination = File(folder, file.getName() ?: "")
        try {
            openDownloadInputStream(file).use { inputStream ->
                openDownloadOutputStream(file, destination, listener).use { out ->
                    IOUtils.copy(inputStream, out)
                }
            }
        } catch (e: IOException) {
            throw BackendException(
                "Failed to download ${file.getName()} to ${destination.name}",
                e,
                true
            )
        }
        return destination
    }

    @Throws(BackendException::class)
    private fun openDownloadInputStream(file: SAFFile): InputStream {
        val inputStream = try {
            mContext.contentResolver.openInputStream(file.getUri())
        } catch (e: FileNotFoundException) {
            throw BackendException("Unable to open uri ${file.getUri()}", e, true)
        }
        return inputStream ?: throw BackendException(
            "Couldn't open file ${file.getUri()} its provider has recently crashed",
            true
        )
    }

    @Throws(BackendException::class)
    private fun openDownloadOutputStream(
        source: SAFFile,
        destination: File,
        listener: ProgressOutputStream.DownloadProgressListener
    ): OutputStream {
        return try {
            ProgressOutputStream(destination, source.getSize(), listener)
        } catch (e: FileNotFoundException) {
            throw BackendException(
                "Couldn't open '${destination.name}' for writing (might be a folder or inaccessible)",
                e,
                true
            )
        }
    }

    @Throws(BackendException::class)
    public override fun list(folder: SAFFile?): List<@JvmSuppressWildcards IFile> {
        val docFolder = if (folder == null) {
            mRoot ?: throw BackendException("Couldn't access root directory")
        } else {
            DocumentFile.fromTreeUri(mContext, folder.getUri())
                ?: throw BackendException(
                    "Couldn't access folder '${folder.getUri()}' (maybe this was called on SDK_Version <21)"
                )
        }
        val folderContents = LinkedList<IFile>()
        for (file in docFolder.listFiles()) {
            folderContents.add(SAFFile(file))
        }
        return folderContents
    }

    @Throws(BackendException::class)
    public override fun newFolder(parent: SAFFile?, name: String): SAFFile {
        // No parent means the top of the granted tree, which is where the explorer starts and
        // is the only place a first folder can be made. Reading a uri off it threw instead, and
        // BackendHandlerIntentService catches BackendException only, so it took the app down.
        // upload and list already read a missing parent this way.
        val docParent = if (parent == null) mRoot else DocumentFile.fromTreeUri(mContext, parent.getUri())
        val parentName = parent?.getUri()?.toString() ?: "root directory"
        if (docParent == null) {
            throw BackendException("Couldn't access parent '$parentName'")
        }
        val docFolder = docParent.createDirectory(name)
            ?: throw BackendException("Couldn't create folder '$name' under parent '$parentName'")
        return SAFFile(docFolder)
    }
}
