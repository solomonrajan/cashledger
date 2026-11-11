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
import android.text.TextUtils
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.EditText
import androidx.annotation.StringRes
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.ui.view.theme.ThemeEngine
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog

/**
 * Created by andrea on 13/11/18.
 */
class ThemedInputPreference : Preference {

    private var mContent: String? = null

    private var mHint: String? = null
    private var mAllowEmptyInput: Boolean = false
    private var mInputType: Int = 0

    private var mValue: String? = null

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

    fun setContent(content: String?) {
        mContent = content
    }

    fun setContent(@StringRes content: Int) {
        mContent = context.getString(content)
    }

    fun setInput(hint: String?, allowEmptyInput: Boolean, inputType: Int) {
        mHint = hint
        mAllowEmptyInput = allowEmptyInput
        mInputType = inputType
    }

    fun setInput(@StringRes hint: Int, allowEmptyInput: Boolean, inputType: Int) {
        mHint = context.getString(hint)
        mAllowEmptyInput = allowEmptyInput
        mInputType = inputType
    }

    fun setCurrentValue(value: String?) {
        mValue = value
    }

    private fun showDialog() {
        val inputView = LayoutInflater.from(context).inflate(R.layout.dialog_input, null)
        val inputEditText: EditText = inputView.findViewById(R.id.dialog_input_edit_text)
        inputEditText.hint = mHint
        inputEditText.setText(mValue)
        inputEditText.inputType = mInputType
        val dialog = ThemedDialog.buildMaterialDialog(context)
            .setTitle(title)
            .setMessage(mContent)
            .setView(inputView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val input = inputEditText.text?.toString().orEmpty()
                if (!TextUtils.equals(input, mValue)) {
                    mValue = input
                    callChangeListener(mValue)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        ThemedDialog.showWithInput(dialog, inputEditText, mAllowEmptyInput)
    }

    // see ThemedPreference.onBindViewHolder
    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        ThemeEngine.applyTheme(holder.itemView, true)
    }
}
