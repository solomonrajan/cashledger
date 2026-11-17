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

package com.oriondev.moneywallet.utils

import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import kotlin.math.min

/**
 * Created by andrea on 26/07/18.
 */
class ProgressInputStream(
    private val mInputStream: InputStream,
    private val mFileSize: Long,
    private val mListener: UploadProgressListener
) : InputStream() {

    private var mProgress: Long = 0L
    private var mLastPercentage: Int = 0

    @Throws(IOException::class)
    constructor(file: File, listener: UploadProgressListener) : this(
        FileInputStream(file),
        file.length(),
        listener
    )

    @Throws(IOException::class)
    override fun read(): Int {
        val b = mInputStream.read()
        mProgress += 1
        notifyProgress()
        return b
    }

    @Throws(IOException::class)
    override fun read(b: ByteArray): Int {
        return read(b, 0, b.size)
    }

    @Throws(IOException::class)
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val bytes = mInputStream.read(b, off, len)
        if (bytes > 0) {
            mProgress += bytes
            notifyProgress()
        }
        return bytes
    }

    @Throws(IOException::class)
    override fun skip(n: Long): Long {
        val skipped = mInputStream.skip(n)
        if (skipped > 0) {
            mProgress += skipped
            notifyProgress()
        }
        return skipped
    }

    @Throws(IOException::class)
    override fun available(): Int {
        return mInputStream.available()
    }

    @Throws(IOException::class)
    override fun close() {
        mInputStream.close()
    }

    private fun notifyProgress() {
        if (mFileSize <= 0) return
        val currentPercentage = ((mProgress * 100) / mFileSize).toInt()
        if (currentPercentage - mLastPercentage > MIN_NOTIFY_RANGE) {
            mLastPercentage = min(currentPercentage, 100)
            mListener.onUploadProgressUpdate(mLastPercentage)
        }
    }

    fun interface UploadProgressListener {
        fun onUploadProgressUpdate(percentage: Int)
    }

    companion object {
        private const val MIN_NOTIFY_RANGE = 5
    }
}

