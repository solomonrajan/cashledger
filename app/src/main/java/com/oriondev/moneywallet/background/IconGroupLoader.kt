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

package com.oriondev.moneywallet.background

import android.annotation.SuppressLint
import android.content.Context
import com.oriondev.moneywallet.model.Icon
import com.oriondev.moneywallet.model.IconGroup
import com.oriondev.moneywallet.model.VectorIcon
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

/**
 * This class is used by the IconListActivity to fetch all the available icons and organize
 * them inside a list of IconGroup items. The list of available icons is statically described
 * by the icons.json file inside the assets/resources directory of the project.
 *
 * Every icon is either original artwork or an original disc with a glyph from a permissively
 * licensed library composed on top, so a new one is a vector drawable in res/drawable named by an
 * entry in that file. The libraries and their licenses are in THIRD_PARTY_NOTICES.md. Nothing in
 * the build checks that a name in the file resolves, so run the instrumented IconGroupLoaderTest
 * on a device after adding one.
 */
class IconGroupLoader(context: Context) : AbstractGenericLoader<List<IconGroup>>(context) {

    override fun loadInBackground(): List<IconGroup> {
        val iconGroups = ArrayList<IconGroup>()
        val jsonBuilder = StringBuilder()
        try {
            context.assets.open("resources/icons.json").use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { bufferedReader ->
                    var line: String? = bufferedReader.readLine()
                    while (line != null) {
                        jsonBuilder.append(line)
                        line = bufferedReader.readLine()
                    }
                }
            }
            val categories = JSONArray(jsonBuilder.toString())
            for (i in 0 until categories.length()) {
                iconGroups.add(parseIconGroup(categories.getJSONObject(i)))
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        iconGroups.sortWith { icon1, icon2 -> icon1.groupName.compareTo(icon2.groupName) }
        return iconGroups
    }

    @Throws(JSONException::class)
    private fun parseIconGroup(category: JSONObject): IconGroup {
        val nameResource = category.getString("name_resource")
        val groupName = getStringByName(nameResource)
        val color = getColorForCategory(nameResource)
        val icons = parseIconList(category.getJSONArray("items"), color)
        return IconGroup(groupName, icons)
    }

    @Throws(JSONException::class)
    private fun parseIconList(icons: JSONArray, color: String): List<Icon> {
        val iconList = ArrayList<Icon>()
        for (i in 0 until icons.length()) {
            val iconJson = icons.getJSONObject(i)
            if (!iconJson.has("color")) {
                iconJson.put("color", color)
            }
            iconList.add(VectorIcon(iconJson))
        }
        return iconList
    }

    private fun getColorForCategory(nameResource: String): String {
        return when (nameResource) {
            "icon_category_clothes" -> "#2196F3"
            "icon_category_toys" -> "#9C27B0"
            "icon_category_travel_and_transportation" -> "#FFEB3B"
            "icon_category_home" -> "#795548"
            "icon_category_shopping" -> "#E91E63"
            "icon_category_sport_and_free_time" -> "#FF9800"
            "icon_category_food_and_drinks" -> "#F44336" // Red for pizza
            "icon_category_technology" -> "#607D8B"
            "icon_category_finance" -> "#4CAF50"
            "icon_category_people" -> "#00BCD4"
            "icon_category_animals" -> "#FF5722"
            "icon_category_payments" -> "#3F51B5"
            "icon_category_party" -> "#9C27B0"
            "icon_category_baby" -> "#03A9F4"
            "icon_category_beauty_and_wellness" -> "#F44336" // Red for heart
            "icon_category_others" -> "#9E9E9E" // Grey for others
            else -> "#9E9E9E"
        }
    }

    @SuppressLint("DiscouragedApi")
    private fun getStringByName(name: String): String {
        val packageName = context.packageName
        val resId = context.resources.getIdentifier(name, "string", packageName)
        return context.getString(resId)
    }
}
