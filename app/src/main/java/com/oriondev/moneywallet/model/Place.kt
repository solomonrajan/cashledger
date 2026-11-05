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

import android.os.Parcel
import android.os.Parcelable

/**
 * Created by andrea on 10/03/18.
 */
data class Place(
    override val id: Long,
    val name: String,
    val icon: Icon?,
    val address: String?,
    val latitude: Double?,
    val longitude: Double?
) : Identifiable, Parcelable {

    constructor(id: Long, name: String, icon: Icon?, address: String?, coordinates: Coordinates?) : this(
        id,
        name,
        icon,
        address,
        coordinates?.latitude,
        coordinates?.longitude
    )

    constructor(parcel: Parcel) : this(
        parcel.readLong(),
        parcel.readString() ?: "",
        parcel.readParcelable(Icon::class.java.classLoader),
        parcel.readString(),
        if (parcel.readByte().toInt() == 0) null else parcel.readDouble(),
        if (parcel.readByte().toInt() == 0) null else parcel.readDouble()
    )

    fun hasCoordinates(): Boolean = latitude != null && longitude != null

    fun getCoordinates(): Coordinates? {
        val lat = latitude
        val lon = longitude
        return if (lat != null && lon != null) Coordinates(lat, lon) else null
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeLong(id)
        dest.writeString(name)
        dest.writeParcelable(icon, flags)
        dest.writeString(address)
        if (latitude == null) {
            dest.writeByte(0.toByte())
        } else {
            dest.writeByte(1.toByte())
            dest.writeDouble(latitude)
        }
        if (longitude == null) {
            dest.writeByte(0.toByte())
        } else {
            dest.writeByte(1.toByte())
            dest.writeDouble(longitude)
        }
    }

    companion object CREATOR : Parcelable.Creator<Place> {
        override fun createFromParcel(parcel: Parcel): Place {
            return Place(parcel)
        }

        override fun newArray(size: Int): Array<Place?> {
            return arrayOfNulls(size)
        }
    }
}

