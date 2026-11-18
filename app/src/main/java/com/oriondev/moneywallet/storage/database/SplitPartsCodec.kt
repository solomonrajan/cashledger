/*
 * Copyright (c) 2026.
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

package com.oriondev.moneywallet.storage.database

import android.text.TextUtils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * [Contract.Transaction.SPLIT_PARTS], written with an insert or an update, holds every
 * part of the transaction as a JSON array of objects, each holding [Contract.TransactionSplit.PART_CATEGORY],
 * [Contract.TransactionSplit.PART_MONEY] and [Contract.TransactionSplit.PART_NOTE].
 * The parts it holds replace every part the transaction had, an empty array leaves it unsplit,
 * and a write without it leaves the parts alone. Refused with
 * [Contract.ErrorCode.SPLIT_NOT_VALID] when the parts could not be counted.
 */
object SplitPartsCodec {

    @JvmStatic
    fun encode(parts: List<Part>): String {
        val array = JSONArray()
        try {
            for (part in parts) {
                val obj = JSONObject()
                obj.put(Contract.TransactionSplit.PART_CATEGORY, part.categoryId)
                obj.put(Contract.TransactionSplit.PART_MONEY, part.money)
                obj.put(Contract.TransactionSplit.PART_NOTE, part.note ?: JSONObject.NULL)
                array.put(obj)
            }
        } catch (e: JSONException) {
            throw IllegalStateException(e)
        }
        return array.toString()
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun decode(encodedParts: String?): List<Part> {
        val parts = ArrayList<Part>()
        val array = JSONArray(if (TextUtils.isEmpty(encodedParts)) "[]" else encodedParts)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val note = if (obj.isNull(Contract.TransactionSplit.PART_NOTE)) {
                null
            } else {
                obj.getString(Contract.TransactionSplit.PART_NOTE)
            }
            parts.add(
                Part(
                    categoryId = obj.getLong(Contract.TransactionSplit.PART_CATEGORY),
                    money = obj.getLong(Contract.TransactionSplit.PART_MONEY),
                    note = note
                )
            )
        }
        return parts
    }

    data class Part(
        val categoryId: Long,
        val money: Long,
        val note: String? = null
    )
}
