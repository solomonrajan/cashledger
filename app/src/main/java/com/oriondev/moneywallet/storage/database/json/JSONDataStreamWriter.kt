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

package com.oriondev.moneywallet.storage.database.json

import android.util.JsonWriter
import org.json.JSONException
import org.json.JSONObject
import java.io.Closeable
import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter

/**
 * Created by andrea on 28/10/18.
 */
internal class JSONDataStreamWriter @Throws(IOException::class) constructor(
    outputStream: OutputStream
) : Closeable {

    private val mWriter: JsonWriter = JsonWriter(OutputStreamWriter(outputStream))

    init {
        mWriter.beginObject()
    }

    @Throws(IOException::class)
    fun writeName(name: String) {
        mWriter.name(name)
    }

    @Throws(IOException::class)
    fun beginArray() {
        mWriter.beginArray()
    }

    @Throws(IOException::class)
    fun endArray() {
        mWriter.endArray()
    }

    @Throws(IOException::class, JSONException::class)
    fun writeJSONObject(`object`: JSONObject) {
        mWriter.beginObject()
        val it = `object`.keys()
        while (it.hasNext()) {
            val key = it.next()
            mWriter.name(key)
            when (val value = `object`.get(key)) {
                is String -> mWriter.value(value)
                is Number -> mWriter.value(value)
                is Boolean -> mWriter.value(value)
                else -> mWriter.nullValue()
            }
        }
        mWriter.endObject()
    }

    @Throws(IOException::class)
    override fun close() {
        mWriter.endObject()
        mWriter.close()
    }
}
