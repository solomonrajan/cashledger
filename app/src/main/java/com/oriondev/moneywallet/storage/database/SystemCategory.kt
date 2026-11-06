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

package com.oriondev.moneywallet.storage.database

import android.content.Context
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.ColorIcon
import com.oriondev.moneywallet.model.VectorIcon
import com.oriondev.moneywallet.utils.IconLoader
import com.oriondev.moneywallet.utils.Utils
import org.json.JSONException
import org.json.JSONObject

/**
 * Created by andrea on 01/03/18.
 */
class SystemCategory private constructor(
    private val mName: Int,
    val tag: String,
    color: Int,
    val resourceName: String?
) {

    private val mColor: String = Utils.getHexColor(color)

    fun getName(context: Context): String {
        return context.getString(mName)
    }

    fun getIcon(context: Context): String {
        if (resourceName != null) {
            try {
                val jsonObject = JSONObject()
                jsonObject.put("resource", resourceName)
                jsonObject.put("color", mColor)
                return VectorIcon(jsonObject).toString()
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
        val source = IconLoader.getColorIconString(context.getString(mName))
        return ColorIcon(mColor, source).toString()
    }

    val uuid: String
        get() = "system-uuid-$tag"

    fun getUUID(): String = uuid

    companion object {
        @JvmField
        val mSystemCategories: List<SystemCategory> = listOf(
            SystemCategory(
                R.string.system_category_transfer,
                Schema.CategoryTag.TRANSFER,
                Utils.getRandomMDColor(),
                "ic_transfer_24dp"
            ),
            SystemCategory(
                R.string.system_category_transfer_tax,
                Schema.CategoryTag.TRANSFER_TAX,
                Utils.getRandomMDColor(),
                "ic_icon_percent"
            ),
            SystemCategory(
                R.string.system_category_debt,
                Schema.CategoryTag.DEBT,
                Utils.getRandomMDColor(),
                "ic_debt_24dp"
            ),
            SystemCategory(
                R.string.system_category_credit,
                Schema.CategoryTag.CREDIT,
                Utils.getRandomMDColor(),
                "ic_icon_credit_score"
            ),
            SystemCategory(
                R.string.system_category_debt_paid,
                Schema.CategoryTag.PAID_DEBT,
                Utils.getRandomMDColor(),
                "ic_icon_handshake"
            ),
            SystemCategory(
                R.string.system_category_credit_paid,
                Schema.CategoryTag.PAID_CREDIT,
                Utils.getRandomMDColor(),
                "ic_icon_handshake"
            ),
            SystemCategory(
                R.string.system_category_generic_tax,
                Schema.CategoryTag.TAX,
                Utils.getRandomMDColor(),
                "ic_icon_percent"
            ),
            SystemCategory(
                R.string.system_category_saving_in,
                Schema.CategoryTag.SAVING_DEPOSIT,
                Utils.getRandomMDColor(),
                "ic_icon_hand_deposit"
            ),
            SystemCategory(
                R.string.system_category_saving_out,
                Schema.CategoryTag.SAVING_WITHDRAW,
                Utils.getRandomMDColor(),
                "ic_icon_hand_withdraw"
            )
        )
    }
}
