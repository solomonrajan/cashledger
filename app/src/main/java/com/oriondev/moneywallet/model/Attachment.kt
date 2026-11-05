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

package com.oriondev.moneywallet.model

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import android.text.TextUtils
import androidx.core.content.FileProvider
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog
import java.io.File
import java.util.UUID

/**
 * Created by andrea on 27/03/18.
 */
class Attachment : Parcelable, Identifiable {

    override var id: Long
    val file: String
    val name: String
    val type: String
    val size: Long
    var status: Status

    constructor(id: Long, file: String, name: String, type: String, size: Long) {
        this.id = id
        this.file = file
        this.name = name
        this.type = type
        this.size = size
        this.status = Status.READY
    }

    constructor(id: Long, file: String, name: String, type: String, size: Long, status: Status) {
        this.id = id
        this.file = file
        this.name = name
        this.type = type
        this.size = size
        this.status = status
    }

    private constructor(parcel: Parcel) {
        id = parcel.readLong()
        file = parcel.readString() ?: ""
        name = parcel.readString() ?: ""
        type = parcel.readString() ?: ""
        size = parcel.readLong()
        status = (parcel.readSerializable() as? Status) ?: Status.READY
    }

    fun setStatus(status: Status, error: String? = null) {
        this.status = status
        // TODO handle error
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeLong(id)
        dest.writeString(file)
        dest.writeString(name)
        dest.writeString(type)
        dest.writeLong(size)
        dest.writeSerializable(status)
    }

    override fun equals(other: Any?): Boolean {
        return other is Attachment && file == other.file
    }

    override fun hashCode(): Int = file.hashCode()

    fun getUri(context: Context): Uri {
        val authority = context.packageName + ".storage.file"
        val folder = File(context.getExternalFilesDir(null), FOLDER_NAME)
        val attachment = File(folder, file)
        return FileProvider.getUriForFile(context, authority, attachment)
    }

    fun getActionViewIntent(context: Context): Intent {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(getUri(context), type)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return intent
    }

    enum class Status {
        PENDING,
        READY,
        ERROR
    }

    companion object {
        const val FOLDER_NAME: String = "attachments"

        @JvmField
        val CREATOR: Parcelable.Creator<Attachment> = object : Parcelable.Creator<Attachment> {
            override fun createFromParcel(parcel: Parcel): Attachment {
                return Attachment(parcel)
            }

            override fun newArray(size: Int): Array<Attachment?> {
                return arrayOfNulls(size)
            }
        }

        @JvmStatic
        fun generateFileUID(): String {
            return UUID.randomUUID().toString().replace("-", "")
        }

        @JvmStatic
        fun openAttachment(context: Context, attachment: Attachment) {
            try {
                context.startActivity(attachment.getActionViewIntent(context))
            } catch (e: ActivityNotFoundException) {
                ThemedDialog.buildMaterialDialog(context)
                    .setTitle(R.string.title_error)
                    .setMessage(R.string.message_error_activity_not_found)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }

        @JvmStatic
        fun getIconResByType(type: String?): Int {
            if (!TextUtils.isEmpty(type)) {
                when (type) {
                    "application/pdf" -> return R.drawable.ic_file_pdf_24dp
                    "text/plain" -> return R.drawable.ic_file_document_24dp
                    "image/png", "image/jpg", "image/jpeg", "image/gif", "image/bmp" -> return R.drawable.ic_file_image_24dp
                    "video/mp4" -> return R.drawable.ic_file_video_24dp
                }
            }
            return R.drawable.ic_file_outline_24dp
        }
    }
}
