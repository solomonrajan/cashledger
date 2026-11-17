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

package com.oriondev.moneywallet.storage.database;

import android.content.Context;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.model.ColorIcon;
import com.oriondev.moneywallet.utils.IconLoader;
import com.oriondev.moneywallet.utils.Utils;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;
import com.oriondev.moneywallet.model.VectorIcon;

/**
 * Created by andrea on 01/03/18.
 */
/*package-local*/ class SystemCategory {

    /*package-local*/ static final List<SystemCategory> mSystemCategories;

    static {
        mSystemCategories = new ArrayList<>();
        mSystemCategories.add(new SystemCategory(R.string.system_category_transfer, Schema.CategoryTag.TRANSFER, Utils.getRandomMDColor(), "ic_transfer_24dp"));
        mSystemCategories.add(new SystemCategory(R.string.system_category_transfer_tax, Schema.CategoryTag.TRANSFER_TAX, Utils.getRandomMDColor(), "ic_icon_percent"));
        mSystemCategories.add(new SystemCategory(R.string.system_category_debt, Schema.CategoryTag.DEBT, Utils.getRandomMDColor(), "ic_debt_24dp"));
        mSystemCategories.add(new SystemCategory(R.string.system_category_credit, Schema.CategoryTag.CREDIT, Utils.getRandomMDColor(), "ic_icon_credit_score"));
        mSystemCategories.add(new SystemCategory(R.string.system_category_debt_paid, Schema.CategoryTag.PAID_DEBT, Utils.getRandomMDColor(), "ic_icon_handshake"));
        mSystemCategories.add(new SystemCategory(R.string.system_category_credit_paid, Schema.CategoryTag.PAID_CREDIT, Utils.getRandomMDColor(), "ic_icon_handshake"));
        mSystemCategories.add(new SystemCategory(R.string.system_category_generic_tax, Schema.CategoryTag.TAX, Utils.getRandomMDColor(), "ic_icon_percent"));
        mSystemCategories.add(new SystemCategory(R.string.system_category_saving_in, Schema.CategoryTag.SAVING_DEPOSIT, Utils.getRandomMDColor(), "ic_icon_hand_deposit"));
        mSystemCategories.add(new SystemCategory(R.string.system_category_saving_out, Schema.CategoryTag.SAVING_WITHDRAW, Utils.getRandomMDColor(), "ic_icon_hand_withdraw"));
    }

    private final int mName;
    private final String mTag;
    private final String mColor;
    private final String mResourceName;

    private SystemCategory(int name, String tag, int color, String resourceName) {
        mName = name;
        mTag = tag;
        mColor = Utils.getHexColor(color);
        mResourceName = resourceName;
    }

    /*package-local*/ String getName(Context context) {
        return context.getString(mName);
    }

    /*package-local*/ String getTag() {
        return mTag;
    }
    
    /*package-local*/ String getResourceName() {
        return mResourceName;
    }

    /*package-local*/ String getIcon(Context context) {
        if (mResourceName != null) {
            try {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("resource", mResourceName);
                jsonObject.put("color", mColor);
                return new VectorIcon(jsonObject).toString();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        String source = IconLoader.getColorIconString(context.getString(mName));
        return new ColorIcon(mColor, source).toString();
    }

    /*package-local*/ String getUUID() {
        return "system-uuid-" + mTag;
    }
}