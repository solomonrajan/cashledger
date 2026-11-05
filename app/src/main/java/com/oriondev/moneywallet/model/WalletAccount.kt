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

import com.oriondev.moneywallet.utils.IconLoader

/**
 * One entry of the wallet switcher in the navigation drawer: a wallet, or the total of them.
 */
class WalletAccount(
    val id: Long,
    val name: String,
    icon: Icon?,
    val money: Money
) {
    val icon: Icon = icon ?: IconLoader.UNKNOWN

    operator fun component1(): Long = id
    operator fun component2(): String = name
    operator fun component3(): Icon = icon
    operator fun component4(): Money = money

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WalletAccount) return false
        return id == other.id && name == other.name && icon == other.icon && money == other.money
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + icon.hashCode()
        result = 31 * result + money.hashCode()
        return result
    }

    override fun toString(): String {
        return "WalletAccount(id=$id, name=$name, icon=$icon, money=$money)"
    }
}

