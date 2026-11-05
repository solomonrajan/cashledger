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

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import androidx.documentfile.provider.DocumentFile
import org.json.JSONException
import org.json.JSONObject

/**
 * Represents a file/document with regards to the android storage access framework.
 */
class SAFFile : IFile {

    private val mUri: Uri
    private var mName: String? = null
    private var mSize: Long = 0
    private var mIsDir: Boolean = false

    constructor(encoded: String) {
        try {
            val jsonObject = JSONObject(encoded)
            mUri = Uri.parse(jsonObject.getString(URI))
            mName = jsonObject.getString(NAME)
            mSize = jsonObject.getLong(SIZE)
            mIsDir = jsonObject.getBoolean(IS_DIRECTORY)
        } catch (e: JSONException) {
            throw RuntimeException("Cannot decode file from string: " + e.message)
        }
    }

    constructor(documentFile: DocumentFile) {
        mIsDir = documentFile.isDirectory
        mUri = documentFile.uri
        mSize = documentFile.length()
        mName = documentFile.name
    }

    constructor(parcel: Parcel) {
        mUri = Uri.parse(parcel.readString() ?: "")
        mName = parcel.readString()
        mSize = parcel.readLong()
        mIsDir = parcel.readByte().toInt() != 0
    }

    override fun getName(): String? = mName

    override fun getExtension(): String? {
        val name = mName ?: return null
        val index = name.lastIndexOf(".")
        return if (index >= 0) name.substring(index) else null
    }

    override fun isDirectory(): Boolean = mIsDir

    override fun getSize(): Long = mSize

    override fun encodeToString(): String {
        try {
            val jsonObject = JSONObject()
            jsonObject.put(URI, mUri.toString())
            jsonObject.put(NAME, mName)
            jsonObject.put(SIZE, mSize)
            jsonObject.put(IS_DIRECTORY, mIsDir)
            return jsonObject.toString()
        } catch (e: JSONException) {
            throw RuntimeException("Cannot encode file to string: " + e.message)
        }
    }

    fun getUri(): Uri = mUri

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(mUri.toString())
        dest.writeString(mName)
        dest.writeLong(mSize)
        dest.writeByte((if (mIsDir) 1 else 0).toByte())
    }

    companion object {
        private const val URI = "uri"
        private const val NAME = "name"
        private const val SIZE = "size"
        private const val IS_DIRECTORY = "isDir"

        /**
         * Returns true only when the value looks like an encoded SAFFile descriptor (a JSON object),
         * as opposed to a legacy raw path (e.g. "/storage/emulated/0") persisted by older versions.
         * Pure check with no Android dependencies, so it is unit-testable.
         */
        @JvmStatic
        fun looksEncoded(encoded: String?): Boolean {
            return encoded != null && encoded.trim().startsWith("{")
        }

        /**
         * Decodes an encoded descriptor, returning null instead of throwing when the value is missing,
         * a legacy path, or otherwise undecodable. Lets callers fail soft rather than crash at startup
         * (see issue #177, where a legacy auto-backup path crashed the app in Application.onCreate).
         */
        @JvmStatic
        fun decode(encoded: String?): SAFFile? {
            if (!looksEncoded(encoded)) {
                return null
            }
            return try {
                SAFFile(encoded ?: return null)
            } catch (e: RuntimeException) {
                null
            }
        }

        @JvmField
        val CREATOR: Parcelable.Creator<SAFFile> = object : Parcelable.Creator<SAFFile> {
            override fun createFromParcel(parcel: Parcel): SAFFile {
                return SAFFile(parcel)
            }

            override fun newArray(size: Int): Array<SAFFile?> {
                return arrayOfNulls(size)
            }
        }
    }
}
