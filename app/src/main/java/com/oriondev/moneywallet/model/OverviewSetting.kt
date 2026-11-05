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
import com.oriondev.moneywallet.utils.DateUtils
import java.util.Date

/**
 * Created by andrea on 17/08/18.
 */
class OverviewSetting : Parcelable {

    val startDate: Date
    val endDate: Date
    val groupType: Group
    val type: Type
    val cashFlow: CashFlow
    val categoryId: Long

    constructor(startDate: Date, endDate: Date, groupType: Group, cashFlow: CashFlow) {
        this.startDate = DateUtils.setTime(startDate, 0, 0, 0, 0)
        this.endDate = DateUtils.setTime(endDate, 23, 59, 59, 999)
        this.groupType = groupType
        this.type = Type.CASH_FLOW
        this.cashFlow = cashFlow
        this.categoryId = 0L
    }

    constructor(startDate: Date, endDate: Date, groupType: Group, categoryId: Long) {
        this.startDate = DateUtils.setTime(startDate, 0, 0, 0, 0)
        this.endDate = DateUtils.setTime(endDate, 23, 59, 59, 999)
        this.groupType = groupType
        this.type = Type.CATEGORY
        this.cashFlow = CashFlow.NET_INCOMES
        this.categoryId = categoryId
    }

    private constructor(parcel: Parcel) {
        startDate = (parcel.readSerializable() as? Date) ?: Date()
        endDate = (parcel.readSerializable() as? Date) ?: Date()
        groupType = (parcel.readSerializable() as? Group) ?: Group.DAILY
        type = (parcel.readSerializable() as? Type) ?: Type.CASH_FLOW
        cashFlow = (parcel.readSerializable() as? CashFlow) ?: CashFlow.NET_INCOMES
        categoryId = parcel.readLong()
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeSerializable(startDate)
        dest.writeSerializable(endDate)
        dest.writeSerializable(groupType)
        dest.writeSerializable(type)
        dest.writeSerializable(cashFlow)
        dest.writeLong(categoryId)
    }

    enum class Type {
        CASH_FLOW,
        CATEGORY
    }

    enum class CashFlow {
        INCOMES,
        EXPENSES,
        NET_INCOMES
    }

    companion object CREATOR : Parcelable.Creator<OverviewSetting> {
        override fun createFromParcel(parcel: Parcel): OverviewSetting {
            return OverviewSetting(parcel)
        }

        override fun newArray(size: Int): Array<OverviewSetting?> {
            return arrayOfNulls(size)
        }
    }
}
