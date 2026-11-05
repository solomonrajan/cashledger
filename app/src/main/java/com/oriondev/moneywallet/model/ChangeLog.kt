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
 * Created by andrea on 06/04/18.
 */
class ChangeLog {

    var type: Int = 0
    var versionName: String? = null
    var versionDate: String? = null
    var changeText: String? = null
    var changeType: Int = 0

    class HeaderBuilder {
        private var versionName: String? = null
        private var versionDate: String? = null

        fun versionName(versionName: String?): HeaderBuilder {
            this.versionName = versionName
            return this
        }

        fun versionDate(versionDate: String?): HeaderBuilder {
            this.versionDate = versionDate
            return this
        }

        fun build(): ChangeLog {
            val changeLog = ChangeLog()
            changeLog.type = TYPE_HEADER
            changeLog.versionName = versionName
            changeLog.versionDate = versionDate
            return changeLog
        }
    }

    class ItemBuilder {
        private var changeText: String? = null

        fun changeText(changeText: String?): ItemBuilder {
            this.changeText = changeText
            return this
        }

        fun build(): ChangeLog {
            val changeLog = ChangeLog()
            changeLog.type = TYPE_ITEM
            changeLog.changeText = changeText
            return changeLog
        }
    }

    companion object {
        const val TYPE_HEADER: Int = 0
        const val TYPE_ITEM: Int = 1
    }
}

