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
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import androidx.annotation.StringRes
import androidx.fragment.app.FragmentManager
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.CurrencyUnit
import com.oriondev.moneywallet.model.Icon
import com.oriondev.moneywallet.picker.CurrencyPicker
import com.oriondev.moneywallet.picker.IconPicker
import com.oriondev.moneywallet.picker.MoneyPicker
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.ui.view.text.MaterialEditText
import com.oriondev.moneywallet.ui.view.text.NonEmptyTextValidator
import com.oriondev.moneywallet.ui.view.text.Validator
import com.oriondev.moneywallet.utils.CurrencyManager
import com.oriondev.moneywallet.utils.IconLoader
import com.oriondev.moneywallet.utils.MoneyFormatter

/**
 * This class is buildMaterialDialog on top of [NewEditItemActivity] and let the user to create a new wallet
 * or edit an existing one inside the database storage through the use of the [DataContentProvider].
 * The activity uses the following pickers to retrieve information from the user:
 * - [IconPicker] to create or select an icon for the wallet.
 * - [CurrencyPicker] to select the currency of the wallet.
 * - [MoneyPicker] to insert the starting amount of money for the wallet.
 */
class NewEditWalletActivity : NewEditItemActivity(),
    IconPicker.Controller,
    CurrencyPicker.Controller,
    MoneyPicker.Controller {

    private var mIconView: ImageView? = null
    private var mNameEditText: MaterialEditText? = null
    private var mCurrencyEditText: MaterialEditText? = null
    private var mStartMoneyEditText: MaterialEditText? = null
    private var mNoteEditText: MaterialEditText? = null
    private var mNotExcludeTotalCheckBox: CheckBox? = null

    private var mIconPicker: IconPicker? = null
    private var mCurrencyPicker: CurrencyPicker? = null
    private var mMoneyPicker: MoneyPicker? = null

    private val mMoneyFormatter: MoneyFormatter = MoneyFormatter.getInstance()

    override fun onCreateHeaderView(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?) {
        val view = inflater.inflate(R.layout.layout_header_new_edit_icon_name_item, parent, true)
        val iconView: ImageView = view.findViewById(R.id.icon_image_view)
        val nameEt: MaterialEditText = view.findViewById(R.id.name_edit_text)
        mIconView = iconView
        mNameEditText = nameEt
        nameEt.addValidator(NonEmptyTextValidator(this, R.string.error_input_name_not_valid))
        iconView.setOnClickListener {
            mIconPicker?.showPicker()
        }
    }

    override fun onCreatePanelView(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?) {
        val view = inflater.inflate(R.layout.layout_panel_new_edit_wallet, parent, true)
        val currencyEt: MaterialEditText = view.findViewById(R.id.currency_edit_text)
        val startMoneyEt: MaterialEditText = view.findViewById(R.id.start_money_edit_text)
        val noteEt: MaterialEditText = view.findViewById(R.id.note_edit_text)
        val notExcludeTotalCb: CheckBox = view.findViewById(R.id.not_exclude_total_check_box)

        mCurrencyEditText = currencyEt
        mStartMoneyEditText = startMoneyEt
        mNoteEditText = noteEt
        mNotExcludeTotalCheckBox = notExcludeTotalCb

        currencyEt.addValidator(object : Validator {
            override fun getErrorMessage(): String = getString(R.string.error_input_currency_not_valid)
            override fun isValid(charSequence: CharSequence): Boolean = mCurrencyPicker?.currentCurrency != null
            override fun autoValidate(): Boolean = false
        })
        currencyEt.setTextViewMode(true)
        startMoneyEt.setTextViewMode(true)
        currencyEt.setOnClickListener {
            mCurrencyPicker?.showPicker()
        }
        startMoneyEt.setOnClickListener {
            mMoneyPicker?.showPicker()
        }
    }

    override fun onViewCreated(savedInstanceState: Bundle?) {
        super.onViewCreated(savedInstanceState)
        var icon: Icon? = null
        var currencyUnit: CurrencyUnit? = CurrencyManager.getDefaultCurrency()
        var startMoney = 0L
        if (savedInstanceState == null && getMode() == Mode.EDIT_ITEM) {
            var loadComplete = false
            val projection = arrayOf(
                Contract.Wallet.NAME,
                Contract.Wallet.ICON,
                Contract.Wallet.CURRENCY,
                Contract.Wallet.START_MONEY,
                Contract.Wallet.COUNT_IN_TOTAL,
                Contract.Wallet.NOTE
            )
            val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_WALLETS, getItemId())
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    mNameEditText?.setText(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Wallet.NAME)))
                    icon = IconLoader.parse(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Wallet.ICON)))
                    currencyUnit = CurrencyManager.getCurrency(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Wallet.CURRENCY)))
                    startMoney = cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Wallet.START_MONEY))
                    mNotExcludeTotalCheckBox?.isChecked = cursor.getInt(cursor.getColumnIndexOrThrow(Contract.Wallet.COUNT_IN_TOTAL)) == 1
                    mNoteEditText?.setText(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Wallet.NOTE)))
                    loadComplete = true
                }
            }
            if (!loadComplete) {
                setResult(Activity.RESULT_CANCELED)
                finish()
                return
            }
        }
        val fragmentManager: FragmentManager = supportFragmentManager
        val iconPicker = IconPicker.createPicker(fragmentManager, TAG_ICON_PICKER, icon)
        mIconPicker = iconPicker
        mCurrencyPicker = CurrencyPicker.createPicker(fragmentManager, TAG_CURRENCY_PICKER, currencyUnit)
        mMoneyPicker = MoneyPicker.createPicker(fragmentManager, TAG_MONEY_PICKER, currencyUnit, startMoney, true)
        iconPicker.listenOn(mNameEditText)
    }

    @StringRes
    override fun getActivityTileRes(mode: Mode): Int {
        return when (mode) {
            Mode.NEW_ITEM -> R.string.title_activity_new_wallet
            Mode.EDIT_ITEM -> R.string.title_activity_edit_wallet
        }
    }

    private fun validate(): Boolean {
        return mNameEditText?.validate() == true && mCurrencyEditText?.validate() == true
    }

    override fun onSaveChanges(mode: Mode) {
        if (validate()) {
            val contentValues = ContentValues().apply {
                put(Contract.Wallet.NAME, mNameEditText?.getTextAsString() ?: "")
                put(Contract.Wallet.ICON, mIconPicker?.currentIcon?.toString() ?: "")
                put(Contract.Wallet.CURRENCY, mCurrencyPicker?.currentCurrency?.iso ?: "")
                put(Contract.Wallet.START_MONEY, mMoneyPicker?.currentMoney ?: 0L)
                put(Contract.Wallet.COUNT_IN_TOTAL, mNotExcludeTotalCheckBox?.isChecked == true)
                put(Contract.Wallet.NOTE, mNoteEditText?.getTextAsString() ?: "")
            }
            val contentResolver: ContentResolver = contentResolver
            when (mode) {
                Mode.NEW_ITEM -> {
                    contentResolver.insert(DataContentProvider.CONTENT_WALLETS, contentValues)
                }
                Mode.EDIT_ITEM -> {
                    val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_WALLETS, getItemId())
                    contentResolver.update(uri, contentValues, null, null)
                }
            }
            setResult(RESULT_OK)
            finish()
        }
    }

    override fun onIconChanged(tag: String?, icon: Icon?) {
        IconLoader.loadInto(icon, mIconView)
    }

    override fun onCurrencyChanged(tag: String?, currency: CurrencyUnit?) {
        if (currency != null) {
            mCurrencyEditText?.setText(currency.name)
        } else {
            mCurrencyEditText?.setText(null)
        }
        mMoneyPicker?.currency = currency
    }

    override fun onMoneyChanged(tag: String?, currency: CurrencyUnit?, money: Long) {
        val startMoneyEt = mStartMoneyEditText ?: return
        mMoneyFormatter.applyNotTinted(startMoneyEt, currency, money)
    }

    companion object {
        private const val TAG_ICON_PICKER = "NewEditWalletActivity::Tag::IconPicker"
        private const val TAG_CURRENCY_PICKER = "NewEditWalletActivity::Tag::CurrencyPicker"
        private const val TAG_MONEY_PICKER = "NewEditWalletActivity::Tag::MoneyPicker"
    }
}
