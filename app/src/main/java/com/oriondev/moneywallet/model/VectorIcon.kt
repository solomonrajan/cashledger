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

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Parcel
import android.os.Parcelable
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import org.json.JSONException
import org.json.JSONObject

/**
 * Created by andrea on 23/01/18.
 */
class VectorIcon : Icon {

    private val mResourceName: String
    private val mColor: String?

    @Throws(JSONException::class)
    constructor(jsonObject: JSONObject) {
        mResourceName = jsonObject.getString(RESOURCE)
        mColor = jsonObject.optString(COLOR, null)
    }

    constructor(original: VectorIcon, color: String?) {
        mResourceName = original.mResourceName
        mColor = color
    }

    protected constructor(source: Parcel) {
        mResourceName = source.readString() ?: ""
        mColor = source.readString()
    }

    fun getResourceName(): String = mResourceName

    override fun getType(): Type = Type.RESOURCE

    @Throws(JSONException::class)
    override fun writeJSON(jsonObject: JSONObject) {
        jsonObject.put(RESOURCE, mResourceName)
        if (!mColor.isNullOrEmpty()) {
            jsonObject.put(COLOR, mColor)
        }
    }

    override fun getDrawable(context: Context): Drawable? {
        val drawableId = getDrawableId(context, mResourceName)
        if (drawableId > 0) {
            return ContextCompat.getDrawable(context, drawableId)
        }
        return null
    }

    override fun apply(imageView: ImageView): Boolean {
        val resourceId = getResource(imageView.context)
        if (resourceId > 0) {
            imageView.setImageResource(resourceId)
            var hasColor = false
            var parsedColor = 0
            if (!mColor.isNullOrEmpty()) {
                try {
                    parsedColor = Color.parseColor(mColor)
                    imageView.setColorFilter(parsedColor)
                    hasColor = true
                } catch (e: IllegalArgumentException) {
                    imageView.clearColorFilter()
                }
            } else {
                imageView.clearColorFilter()
            }

            if (hasColor) {
                val backgroundColor = Color.argb(26, Color.red(parsedColor), Color.green(parsedColor), Color.blue(parsedColor))
                val shape = GradientDrawable()
                shape.shape = GradientDrawable.OVAL
                shape.setColor(backgroundColor)
                imageView.background = shape
                val padding = (8 * imageView.resources.displayMetrics.density).toInt()
                imageView.setPadding(padding, padding, padding, padding)
            } else {
                imageView.background = null
                imageView.setPadding(0, 0, 0, 0)
            }
            return true
        }
        return false
    }

    @DrawableRes
    fun getResource(context: Context): Int {
        return getDrawableId(context, mResourceName)
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(mResourceName)
        dest.writeString(mColor)
    }

    companion object {
        private const val RESOURCE = "resource"
        private const val COLOR = "color"

        @JvmField
        val CREATOR: Parcelable.Creator<VectorIcon> = object : Parcelable.Creator<VectorIcon> {
            override fun createFromParcel(source: Parcel): VectorIcon {
                return VectorIcon(source)
            }

            override fun newArray(size: Int): Array<VectorIcon?> {
                return arrayOfNulls(size)
            }
        }
    }
}
