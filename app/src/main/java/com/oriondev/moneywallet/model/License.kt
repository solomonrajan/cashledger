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

/**
 * Created by andrea on 31/03/18.
 */
data class License(
    val name: String?,
    val author: String?,
    val website: String?,
    val version: String?,
    val copyright: String?,
    val type: Type
) {

    constructor(
        name: String?,
        author: String?,
        website: String?,
        version: String?,
        copyright: String?,
        type: String?
    ) : this(name, author, website, version, copyright, parse(type))

    val url: String?
        get() = website

    val typeName: String
        get() = when (type) {
            Type.APACHE -> "Apache License"
            Type.MIT -> "MIT License"
            Type.ISC -> "ISC License"
            Type.GPL3 -> "GNU GPLv3"
            Type.GPL2 -> "GNU GPLv2"
            Type.AGPL -> "GNU Affero GPL"
            Type.CUSTOM -> "Custom License"
            Type.UNKNOWN -> "Unknown license"
        }

    companion object {
        @JvmStatic
        fun parse(type: String?): Type {
            return when (type) {
                "MIT" -> Type.MIT
                "ISC" -> Type.ISC
                "APACHE" -> Type.APACHE
                "GPL2" -> Type.GPL2
                "GPL3" -> Type.GPL3
                "AGPL" -> Type.AGPL
                "CUSTOM" -> Type.CUSTOM
                else -> Type.UNKNOWN
            }
        }
    }

    enum class Type {
        UNKNOWN,
        CUSTOM,
        APACHE,
        GPL2,
        GPL3,
        AGPL,
        MIT,
        ISC
    }
}
