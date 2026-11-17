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

package com.oriondev.moneywallet.ui.view.chart

/**
 * Created by andrea on 14/08/18.
 */
class PieData {

    private val mPieSlices: MutableList<PieSlice>

    constructor() {
        mPieSlices = ArrayList()
    }

    constructor(sliceList: List<PieSlice>) {
        mPieSlices = ArrayList(sliceList)
    }

    operator fun get(index: Int): PieSlice = mPieSlices[index]

    fun add(slice: PieSlice): Boolean = mPieSlices.add(slice)

    fun remove(index: Int): PieSlice = mPieSlices.removeAt(index)

    fun getAllSlices(): List<PieSlice> = mPieSlices

    fun size(): Int = mPieSlices.size
}
