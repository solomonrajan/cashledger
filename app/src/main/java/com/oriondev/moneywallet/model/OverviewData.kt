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

import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.RadarData

/**
 * Created by andrea on 17/08/18.
 */
class OverviewData(
    val barData: BarData?,
    val lineData: LineData?,
    val radarData: RadarData?,
    private val mPeriodMoneyList: List<PeriodMoney>
) {

    fun getPeriodMoney(index: Int): PeriodMoney = mPeriodMoneyList[index]

    fun getPeriodCount(): Int = mPeriodMoneyList.size
}
