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

package com.oriondev.moneywallet.storage.cache

import android.content.Context
import android.graphics.Typeface
import android.widget.TextView
import java.util.HashMap

/**
 * Created by andrea on 30/01/18.
 */
object TypefaceCache {

    const val ROBOTO_MEDIUM = "Roboto-Medium"
    const val ROBOTO_REGULAR = "Roboto-Regular"

    private val mCache = HashMap<String, Typeface>()

    @JvmStatic
    fun get(context: Context, name: String): Typeface? {
        synchronized(mCache) {
            if (!mCache.containsKey(name)) {
                val path = "fonts/$name.ttf"
                var typeface: Typeface? = null
                try {
                    typeface = Typeface.createFromAsset(context.assets, path)
                } catch (e: RuntimeException) {
                    e.printStackTrace()
                }
                if (typeface != null) {
                    mCache[name] = typeface
                }
                return typeface
            }
            return mCache[name]
        }
    }

    @JvmStatic
    fun apply(textView: TextView, name: String) {
        val typeface = get(textView.context, name)
        if (typeface != null) {
            textView.typeface = typeface
        }
    }
}
