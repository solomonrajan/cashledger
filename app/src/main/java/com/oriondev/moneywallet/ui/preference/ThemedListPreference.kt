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

package com.oriondev.moneywallet.ui.preference

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.ArrayRes
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.oriondev.moneywallet.ui.view.theme.ThemeEngine
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog

/**
 * Created by andrea on 25/07/18.
 */
class ThemedListPreference : Preference {

    private var mEntries: Array<String>? = null
    private var mEntryValues: Array<String>? = null

    private var mCurrentValueIndex: Int = 0

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    init {
        configureMaterialPreferenceLayout { showDialog() }
    }

    fun setEntries(entries: Array<String>?) {
        mEntries = entries
    }

    fun setEntries(@ArrayRes array: Int) {
        mEntries = context.resources.getStringArray(array)
    }

    fun setEntryValues(entryValues: Array<String>?) {
        mEntryValues = entryValues
    }

    fun setValue(valueIndex: Int) {
        mCurrentValueIndex = valueIndex
    }

    fun getValue(): String? {
        val values = mEntryValues ?: return null
        return if (mCurrentValueIndex in values.indices) values[mCurrentValueIndex] else null
    }

    fun setValue(value: String?) {
        mCurrentValueIndex = indexOf(value)
    }

    private fun indexOf(value: String?): Int {
        val values = mEntryValues ?: return -1
        return values.indexOfFirst { it == value }
    }

    private fun showDialog() {
        var selected = mCurrentValueIndex
        ThemedDialog.buildMaterialDialog(context)
            .setTitle(title)
            .setSingleChoiceItems(mEntries, mCurrentValueIndex) { _, which ->
                selected = which
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val values = mEntryValues
                if (selected != mCurrentValueIndex && values != null && selected in values.indices) {
                    callChangeListener(values[selected])
                    mCurrentValueIndex = selected
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // see ThemedPreference.onBindViewHolder
    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        ThemeEngine.applyTheme(holder.itemView, true)
    }
}
