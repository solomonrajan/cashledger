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

package com.oriondev.moneywallet.ui.activity

import android.app.Activity
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputFilter
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import androidx.annotation.CallSuper
import androidx.annotation.MenuRes
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.CurrencyUnit
import com.oriondev.moneywallet.model.MoneyScale
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.SQLiteDataException
import com.oriondev.moneywallet.ui.activity.base.SinglePanelScrollActivity
import com.oriondev.moneywallet.ui.view.text.MaterialEditText
import com.oriondev.moneywallet.ui.view.text.NonEmptyTextValidator
import com.oriondev.moneywallet.ui.view.text.Validator
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog
import com.oriondev.moneywallet.utils.CurrencyManager

/**
 * Created by andrea on 05/01/19.
 */
class NewEditCurrencyActivity : SinglePanelScrollActivity() {

    enum class Mode {
        /**
         * This mode indicates that a new item must be created.
         */
        NEW_ITEM,

        /**
         * When the mode is EDIT_ITEM, the caller MUST set a valid item id or a RuntimeException
         * is thrown.
         */
        EDIT_ITEM
    }

    private var mMode: Mode = Mode.NEW_ITEM
    private var mIso: String? = null

    private var mNameEditText: MaterialEditText? = null
    private var mISOEditText: MaterialEditText? = null
    private var mSymbolEditText: MaterialEditText? = null
    private var mDecimalsEditText: MaterialEditText? = null

    override fun onCreateHeaderView(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?) {
        val view = inflater.inflate(R.layout.layout_header_new_edit_name_item, parent, true)
        val nameEt: MaterialEditText = view.findViewById(R.id.name_edit_text)
        mNameEditText = nameEt
        nameEt.addValidator(NonEmptyTextValidator(this, R.string.error_input_name_not_valid))
    }

    override fun onCreatePanelView(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?) {
        val view = inflater.inflate(R.layout.layout_panel_new_edit_currency, parent, true)
        val isoEt: MaterialEditText = view.findViewById(R.id.iso_edit_text)
        val symbolEt: MaterialEditText = view.findViewById(R.id.symbol_edit_text)
        val decimalsEt: MaterialEditText = view.findViewById(R.id.decimal_number_edit_text)
        mISOEditText = isoEt
        mSymbolEditText = symbolEt
        mDecimalsEditText = decimalsEt

        isoEt.filters = arrayOf(InputFilter.AllCaps())
        isoEt.addValidator(object : Validator {
            override fun getErrorMessage(): String = getString(R.string.error_input_iso_not_valid)
            override fun isValid(charSequence: CharSequence): Boolean = charSequence.length == 3
            override fun autoValidate(): Boolean = false
        })
        decimalsEt.addValidator(object : Validator {
            override fun getErrorMessage(): String = getString(R.string.error_input_decimals_not_valid)
            override fun isValid(charSequence: CharSequence): Boolean {
                return try {
                    val decimals = charSequence.toString().toInt()
                    decimals in 0..8
                } catch (_: NumberFormatException) {
                    false
                }
            }
            override fun autoValidate(): Boolean = false
        })
    }

    @CallSuper
    override fun onViewCreated(savedInstanceState: Bundle?) {
        val callerIntent = intent
        if (callerIntent != null) {
            val modeExtra = callerIntent.getSerializableExtra(MODE) as? Mode
            if (modeExtra != null) {
                mMode = modeExtra
                if (modeExtra == Mode.EDIT_ITEM) {
                    val isoExtra = callerIntent.getStringExtra(ISO)
                    if (TextUtils.isEmpty(isoExtra)) {
                        throw RuntimeException("Trying to edit a currency with an invalid iso")
                    }
                    mIso = isoExtra
                } else if (modeExtra == Mode.NEW_ITEM) {
                    mIso = null
                }
            } else {
                mMode = Mode.NEW_ITEM
                mIso = null
            }
        }
        val iso = mIso
        if (savedInstanceState == null && mMode == Mode.EDIT_ITEM && iso != null) {
            val uri = Uri.withAppendedPath(DataContentProvider.CONTENT_CURRENCIES, iso)
            val projections = arrayOf(
                Contract.Currency.NAME,
                Contract.Currency.ISO,
                Contract.Currency.SYMBOL,
                Contract.Currency.DECIMALS
            )
            contentResolver.query(uri, projections, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    mNameEditText?.setText(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Currency.NAME)))
                    mISOEditText?.setText(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Currency.ISO)))
                    mSymbolEditText?.setText(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Currency.SYMBOL)))
                    mDecimalsEditText?.setText(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Currency.DECIMALS)))
                }
            }
        }
        if (mMode == Mode.EDIT_ITEM) {
            mISOEditText?.setTextViewMode(true)
        }
    }

    override fun getActivityTitleRes(): Int {
        return when (mMode) {
            Mode.NEW_ITEM -> R.string.title_activity_new_currency
            Mode.EDIT_ITEM -> R.string.title_activity_edit_currency
        }
    }

    @MenuRes
    override fun onInflateMenu(): Int {
        return R.menu.menu_new_edit_delete_item
    }

    override fun onMenuCreated(menu: Menu) {
        if (mMode == Mode.NEW_ITEM) {
            menu.findItem(R.id.action_delete_item)?.isVisible = false
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_save_changes -> onSaveChanges()
            R.id.action_delete_item -> {
                ThemedDialog.buildMaterialDialog(this)
                    .setTitle(R.string.title_warning)
                    .setMessage(R.string.message_delete_currency)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        onDelete()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
        return false
    }

    private fun validate(): Boolean {
        return mNameEditText?.validate() == true &&
            mISOEditText?.validate() == true &&
            mDecimalsEditText?.validate() == true
    }

    private fun onSaveChanges() {
        if (validate()) {
            val decimalCount = mDecimalsEditText?.getTextAsString()?.toIntOrNull() ?: 0
            val resolver: ContentResolver = contentResolver
            val contentValues = ContentValues().apply {
                put(Contract.Currency.NAME, mNameEditText?.getTextAsString()?.trim() ?: "")
                val symbolText = mSymbolEditText?.getTextAsString()?.trim()
                put(Contract.Currency.SYMBOL, if (TextUtils.isEmpty(symbolText)) null else symbolText)
                put(Contract.Currency.DECIMALS, decimalCount)
            }
            when (mMode) {
                Mode.NEW_ITEM -> {
                    contentValues.put(Contract.Currency.ISO, mISOEditText?.getTextAsString()?.trim() ?: "")
                    resolver.insert(DataContentProvider.CONTENT_CURRENCIES, contentValues)
                    CurrencyManager.invalidateCache(this)
                }
                Mode.EDIT_ITEM -> {
                    val iso = mIso
                    val currencyUnit: CurrencyUnit? = if (iso != null) CurrencyManager.getCurrency(iso) else null
                    if (iso != null && currencyUnit != null) {
                        val uri = Uri.withAppendedPath(DataContentProvider.CONTENT_CURRENCIES, iso)
                        if (currencyUnit.decimals != decimalCount) {
                            val builder = ThemedDialog.buildMaterialDialog(this)
                                .setTitle(R.string.title_warning)
                                .setPositiveButton(android.R.string.ok) { _, _ ->
                                    contentValues.put(Contract.Currency.FIX_MONEY_DECIMALS, true)
                                    resolver.update(uri, contentValues, null, null)
                                    CurrencyManager.invalidateCache(this@NewEditCurrencyActivity)
                                    setResult(Activity.RESULT_OK)
                                    finish()
                                }
                                .setNegativeButton(android.R.string.cancel, null)
                                .setNeutralButton(R.string.action_skip) { _, _ ->
                                    contentValues.put(Contract.Currency.FIX_MONEY_DECIMALS, false)
                                    resolver.update(uri, contentValues, null, null)
                                    CurrencyManager.invalidateCache(this@NewEditCurrencyActivity)
                                    setResult(Activity.RESULT_OK)
                                    finish()
                                }
                            if (currencyUnit.decimals < decimalCount) {
                                val exponent = decimalCount - currencyUnit.decimals
                                val multiplier = MoneyScale.rescale(1L, exponent).toInt()
                                builder.setMessage(getString(R.string.message_multiply_currency_decimals, multiplier))
                            } else {
                                val exponent = currencyUnit.decimals - decimalCount
                                val divider = MoneyScale.rescale(1L, exponent).toInt()
                                builder.setMessage(getString(R.string.message_divide_currency_decimals, divider))
                            }
                            builder.show()
                            return
                        } else {
                            resolver.update(uri, contentValues, null, null)
                            CurrencyManager.invalidateCache(this)
                        }
                    }
                }
            }
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    private fun onDelete() {
        val iso = mIso
        if (mMode == Mode.EDIT_ITEM && iso != null) {
            val uri = Uri.withAppendedPath(DataContentProvider.CONTENT_CURRENCIES, iso)
            val resolver = contentResolver
            try {
                resolver.delete(uri, null, null)
                CurrencyManager.invalidateCache(this)
                setResult(Activity.RESULT_OK)
                finish()
            } catch (e: SQLiteDataException) {
                if (e.errorCode == Contract.ErrorCode.CURRENCY_IN_USE) {
                    ThemedDialog.buildMaterialDialog(this)
                        .setTitle(R.string.title_error)
                        .setMessage(R.string.message_error_delete_currency)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
        }
    }

    companion object {
        const val MODE = "NewEditCurrencyActivity::Mode"
        const val ISO = "NewEditCurrencyActivity::Iso"
    }
}
