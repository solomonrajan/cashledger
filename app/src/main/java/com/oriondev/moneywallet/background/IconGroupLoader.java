/*
 * Copyright (c) 2018.
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

package com.oriondev.moneywallet.background;

import android.content.Context;

import com.oriondev.moneywallet.model.Icon;
import com.oriondev.moneywallet.model.IconGroup;
import com.oriondev.moneywallet.model.VectorIcon;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

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
public class IconGroupLoader extends AbstractGenericLoader<List<IconGroup>> {

    public IconGroupLoader(Context context) {
        super(context);
    }

    @Override
    public List<IconGroup> loadInBackground() {
        List<IconGroup> iconGroups = new ArrayList<>();
        StringBuilder jsonBuilder = new StringBuilder();
        try {
            InputStream inputStream = getContext().getAssets().open("resources/icons.json");
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                jsonBuilder.append(line);
            }
            JSONArray categories = new JSONArray(jsonBuilder.toString());
            for (int i = 0; i < categories.length(); i++) {
                iconGroups.add(parseIconGroup(categories.getJSONObject(i)));
            }
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
        Collections.sort(iconGroups, new AlphabeticIconComparator());
        return iconGroups;
    }

    private IconGroup parseIconGroup(JSONObject category) throws JSONException {
        String nameResource = category.getString("name_resource");
        String groupName = getStringByName(nameResource);
        String color = getColorForCategory(nameResource);
        List<Icon> icons = parseIconList(category.getJSONArray("items"), color);
        return new IconGroup(groupName, icons);
    }

    private List<Icon> parseIconList(JSONArray icons, String color) throws JSONException {
        List<Icon> iconList = new ArrayList<>();
        for (int i = 0; i < icons.length(); i++) {
            JSONObject iconJson = icons.getJSONObject(i);
            if (!iconJson.has("color")) {
                iconJson.put("color", color);
            }
            iconList.add(new VectorIcon(iconJson));
        }
        return iconList;
    }

    private String getColorForCategory(String nameResource) {
        switch (nameResource) {
            case "icon_category_clothes": return "#2196F3";
            case "icon_category_toys": return "#9C27B0";
            case "icon_category_travel_and_transportation": return "#FFEB3B";
            case "icon_category_home": return "#795548";
            case "icon_category_shopping": return "#E91E63";
            case "icon_category_sport_and_free_time": return "#FF9800";
            case "icon_category_food_and_drinks": return "#F44336"; // Red for pizza
            case "icon_category_technology": return "#607D8B";
            case "icon_category_finance": return "#4CAF50";
            case "icon_category_people": return "#00BCD4";
            case "icon_category_animals": return "#FF5722";
            case "icon_category_payments": return "#3F51B5";
            case "icon_category_party": return "#9C27B0";
            case "icon_category_baby": return "#03A9F4";
            case "icon_category_beauty_and_wellness": return "#F44336"; // Red for heart
            case "icon_category_others": return "#9E9E9E"; // Grey for others
            default: return "#9E9E9E";
        }
    }

    private String getStringByName(String name) {
        Context context = getContext();
        String packageName = context.getPackageName();
        int resId = context.getResources().getIdentifier(name, "string", packageName);
        return context.getString(resId);
    }

    private class AlphabeticIconComparator implements Comparator<IconGroup> {

        @Override
        public int compare(IconGroup icon1, IconGroup icon2) {
            return icon1.getGroupName().compareTo(icon2.getGroupName());
        }
    }
}