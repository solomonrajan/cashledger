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

import android.util.JsonReader
import android.util.JsonToken
import org.json.JSONException
import org.json.JSONObject
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Created by andrea on 27/10/18.
 */
internal class JSONDataStreamReader @Throws(IOException::class) constructor(
    inputStream: InputStream
) : Closeable {

    private val mReader: JsonReader = JsonReader(InputStreamReader(inputStream))

    init {
        mReader.beginObject()
    }

    @Throws(IOException::class)
    fun readName(): String {
        return mReader.nextName()
    }

    @Throws(IOException::class)
    fun beginArray() {
        mReader.beginArray()
    }

    @Throws(IOException::class)
    fun endArray() {
        mReader.endArray()
    }

    @Throws(IOException::class)
    fun hasArrayAnotherObject(): Boolean {
        return mReader.peek() != JsonToken.END_ARRAY
    }

    @Throws(IOException::class, JSONException::class)
    fun readObject(): JSONObject {
        mReader.beginObject()
        val `object` = JSONObject()
        while (mReader.peek() != JsonToken.END_OBJECT) {
            val name = mReader.nextName()
            when (mReader.peek()) {
                JsonToken.STRING -> {
                    `object`.put(name, mReader.nextString())
                }
                JsonToken.NUMBER -> {
                    val value = mReader.nextString()
                    try {
                        `object`.put(name, value.toLong())
                    } catch (ignore: NumberFormatException) {
                        // ignore
                    }
                    try {
                        `object`.put(name, value.toDouble())
                    } catch (ignore: NumberFormatException) {
                        // ignore
                    }
                }
                JsonToken.BOOLEAN -> {
                    `object`.put(name, mReader.nextBoolean())
                }
                else -> {
                    mReader.skipValue()
                }
            }
        }
        mReader.endObject()
        return `object`
    }

    @Throws(IOException::class)
    override fun close() {
        mReader.close()
    }
}
