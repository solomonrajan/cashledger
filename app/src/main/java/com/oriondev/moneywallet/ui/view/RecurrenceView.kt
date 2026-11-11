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

package com.oriondev.moneywallet.ui.view

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.ui.view.text.MaterialEditText
import com.oriondev.moneywallet.ui.view.theme.ThemedSpinner
import org.dmfs.rfc5545.recur.Freq
import org.dmfs.rfc5545.recur.RecurrenceRule

/**
 * Created by andrea on 07/11/18.
 */
@SuppressLint("SetTextI18n")
class RecurrenceView : LinearLayout {

    private val mTypeSpinner: ThemedSpinner
    private val mStartDateSpinner: MaterialEditText

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    init {
        val view: View = inflate(context, R.layout.dialog_recurrence_setting_picker, this)
        mTypeSpinner = view.findViewById(R.id.type_spinner)
        mStartDateSpinner = view.findViewById(R.id.start_date_spinner)
        mTypeSpinner.setAdapter(
            ThemedSpinner.Adapter(
                context,
                "Repeat daily",
                "Repeat weekly",
                "Repeat monthly",
                "Repeat yearly"
            )
        )
        mStartDateSpinner.setTextViewMode(true)
        mStartDateSpinner.setOnClickListener {
            // TODO: open picker
        }
        mStartDateSpinner.setText("From: Today")
    }

    fun getRecurrenceRule(): RecurrenceRule {
        // TODO: build the recurrence rule using user settings
        return RecurrenceRule(Freq.MONTHLY)
    }
}
