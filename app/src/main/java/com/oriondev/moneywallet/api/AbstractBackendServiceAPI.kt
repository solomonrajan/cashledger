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

package com.oriondev.moneywallet.api

import com.oriondev.moneywallet.model.IFile
import com.oriondev.moneywallet.utils.ProgressInputStream
import com.oriondev.moneywallet.utils.ProgressOutputStream
import java.io.File

/**
 * Created by andrea on 24/11/18.
 */
abstract class AbstractBackendServiceAPI<T : IFile> @Throws(BackendException::class) constructor(
    private val mType: Class<T>
) : IBackendServiceAPI {

    @Throws(BackendException::class)
    override fun uploadFile(
        folder: IFile?,
        file: File,
        listener: ProgressInputStream.UploadProgressListener
    ): IFile {
        return upload(castNullable(folder, "upload a file to a folder"), file, listener)
    }

    @Throws(BackendException::class)
    protected abstract fun upload(
        folder: T?,
        file: File,
        listener: ProgressInputStream.UploadProgressListener
    ): T

    @Throws(BackendException::class)
    override fun downloadFile(
        folder: File,
        file: IFile,
        listener: ProgressOutputStream.DownloadProgressListener
    ): File {
        val typedFile = if (mType.isInstance(file)) mType.cast(file) else null
        if (typedFile != null) {
            return download(folder, typedFile, listener)
        }
        throw ClassCastException(
            "Backend cannot download a file that is not an instance of ${mType.name}. " +
                "The provided file is an instance of: ${folder.javaClass.name}"
        )
    }

    @Throws(BackendException::class)
    protected abstract fun download(
        folder: File,
        file: T,
        listener: ProgressOutputStream.DownloadProgressListener
    ): File

    @Throws(BackendException::class)
    override fun getFolderContent(folder: IFile?): List<@JvmSuppressWildcards IFile> {
        return list(castNullable(folder, "list the content of a folder"))
    }

    @Throws(BackendException::class)
    protected abstract fun list(folder: T?): List<@JvmSuppressWildcards IFile>

    @Throws(BackendException::class)
    override fun createFolder(parent: IFile?, name: String): IFile {
        return newFolder(castNullable(parent, "create a folder in a folder"), name)
    }

    @Throws(BackendException::class)
    protected abstract fun newFolder(parent: T?, name: String): T

    private fun castNullable(folder: IFile?, operation: String): T? {
        if (folder == null) {
            return null
        }
        val typedFolder = if (mType.isInstance(folder)) mType.cast(folder) else null
        if (typedFolder != null) {
            return typedFolder
        }
        throw ClassCastException(
            "Backend cannot $operation that is not an instance of ${mType.name}. " +
                "The provided folder is an instance of: ${folder.javaClass.name}"
        )
    }
}
