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

package com.oriondev.moneywallet.background

import android.content.Context
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

/**
 * Created by andrea on 08/12/18.
 */
class JsonResourceLoader(context: Context, resource: String) : AbstractGenericLoader<JSONObject>(context) {

    private val mResourcePath: String = "resources/$resource"

    override fun loadInBackground(): JSONObject? {
        val jsonBuilder = StringBuilder()
        try {
            context.assets.open(mResourcePath).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { bufferedReader ->
                    var line: String? = bufferedReader.readLine()
                    while (line != null) {
                        jsonBuilder.append(line)
                        line = bufferedReader.readLine()
                    }
                }
            }
            return JSONObject(jsonBuilder.toString())
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return null
    }
}
