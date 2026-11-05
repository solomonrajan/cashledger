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

import com.oriondev.moneywallet.ui.view.chart.PieData

/**
 * Created by andrea on 13/08/18.
 */
class PeriodDetailFlowData(
    val totalMoney: Money,
    private val mPieDataList: List<PieData>,
    private val mCategoryMoneyList: List<CategoryMoney>
) {

    fun getChartData(index: Int): PieData = mPieDataList[index]

    fun getChartCount(): Int = mPieDataList.size

    fun getCategory(index: Int): CategoryMoney = mCategoryMoneyList[index]

    fun getCategoryCount(): Int = mCategoryMoneyList.size
}
