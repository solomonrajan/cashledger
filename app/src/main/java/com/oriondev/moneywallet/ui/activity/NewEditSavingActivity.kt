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
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.fragment.app.FragmentManager
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.CurrencyUnit
import com.oriondev.moneywallet.model.Icon
import com.oriondev.moneywallet.model.Wallet
import com.oriondev.moneywallet.picker.DateTimePicker
import com.oriondev.moneywallet.picker.IconPicker
import com.oriondev.moneywallet.picker.MoneyPicker
import com.oriondev.moneywallet.picker.WalletPicker
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.SQLiteDataException
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.view.text.MaterialEditText
import com.oriondev.moneywallet.ui.view.text.NonEmptyTextValidator
import com.oriondev.moneywallet.ui.view.text.Validator
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog
import com.oriondev.moneywallet.utils.CurrencyManager
import com.oriondev.moneywallet.utils.DateFormatter
import com.oriondev.moneywallet.utils.DateUtils
import com.oriondev.moneywallet.utils.IconLoader
import com.oriondev.moneywallet.utils.MoneyFormatter
import java.util.Date

/**
 * Created by andrea on 06/03/18.
 */
class NewEditSavingActivity : NewEditItemActivity(),
    IconPicker.Controller,
    MoneyPicker.Controller,
    DateTimePicker.Controller,
    WalletPicker.SingleWalletController {

    private var mAvatarImageView: ImageView? = null
    private var mCurrencyTextView: TextView? = null
    private var mMoneyTextView: TextView? = null
    private var mDescriptionEditText: MaterialEditText? = null
    private var mStartMoneyEditText: MaterialEditText? = null
    private var mExpirationDateEditText: MaterialEditText? = null
    private var mWalletEditText: MaterialEditText? = null
    private var mNoteEditText: MaterialEditText? = null

    private var mIconPicker: IconPicker? = null
    private var mMoneyPicker: MoneyPicker? = null
    private var mStartMoneyPicker: MoneyPicker? = null
    private var mExpDatePicker: DateTimePicker? = null
    private var mWalletPicker: WalletPicker? = null

    private val mMoneyFormatter = MoneyFormatter.getInstance()

    override fun onCreateHeaderView(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?) {
        val view = inflater.inflate(R.layout.layout_header_new_edit_icon_money_item, parent, true)
        mAvatarImageView = view.findViewById(R.id.avatar_image_view)
        mCurrencyTextView = view.findViewById(R.id.currency_text_view)
        mMoneyTextView = view.findViewById(R.id.money_text_view)

        mAvatarImageView?.setOnClickListener {
            mIconPicker?.showPicker()
        }
        mMoneyTextView?.setOnClickListener {
            mMoneyPicker?.showPicker()
        }
    }

    override fun onCreatePanelView(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?) {
        val view = inflater.inflate(R.layout.layout_panel_new_edit_saving, parent, true)
        val descriptionEditText = view.findViewById<MaterialEditText>(R.id.description_edit_text)
        val startMoneyEditText = view.findViewById<MaterialEditText>(R.id.start_money_edit_text)
        val expirationDateEditText = view.findViewById<MaterialEditText>(R.id.expiration_date_edit_text)
        val walletEditText = view.findViewById<MaterialEditText>(R.id.wallet_edit_text)
        val noteEditText = view.findViewById<MaterialEditText>(R.id.note_edit_text)

        mDescriptionEditText = descriptionEditText
        mStartMoneyEditText = startMoneyEditText
        mExpirationDateEditText = expirationDateEditText
        mWalletEditText = walletEditText
        mNoteEditText = noteEditText

        startMoneyEditText.setTextViewMode(true)
        expirationDateEditText.setTextViewMode(true)
        walletEditText.setTextViewMode(true)

        descriptionEditText.addValidator(NonEmptyTextValidator(this, R.string.error_input_missing_description))
        walletEditText.addValidator(object : Validator {
            override fun getErrorMessage(): String {
                return getString(R.string.error_input_missing_wallet)
            }

            override fun isValid(charSequence: CharSequence): Boolean {
                return mWalletPicker?.isSelected == true
            }

            override fun autoValidate(): Boolean = false
        })

        startMoneyEditText.setOnClickListener {
            mStartMoneyPicker?.showPicker()
        }
        expirationDateEditText.setOnClickListener {
            mExpDatePicker?.showDatePicker()
        }
        expirationDateEditText.setOnCancelButtonClickListener(object : MaterialEditText.CancelButtonListener {
            override fun onCancelButtonClick(materialEditText: MaterialEditText): Boolean {
                mExpDatePicker?.currentDateTime = null
                return true
            }
        })
        walletEditText.setOnClickListener {
            mWalletPicker?.showSingleWalletPicker()
        }
    }

    override fun onViewCreated(savedInstanceState: Bundle?) {
        super.onViewCreated(savedInstanceState)
        var icon: Icon? = null
        var money = 0L
        var startMoney = 0L
        var expirationDate: Date? = null
        var wallet: Wallet? = null

        if (savedInstanceState == null) {
            val contentResolver = contentResolver
            if (getMode() == Mode.EDIT_ITEM) {
                val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_SAVINGS, getItemId())
                val projection = arrayOf(
                    Contract.Saving.DESCRIPTION,
                    Contract.Saving.ICON,
                    Contract.Saving.START_MONEY,
                    Contract.Saving.END_MONEY,
                    Contract.Saving.WALLET_ID,
                    Contract.Saving.WALLET_NAME,
                    Contract.Saving.WALLET_ICON,
                    Contract.Saving.WALLET_CURRENCY,
                    Contract.Saving.END_DATE,
                    Contract.Saving.NOTE
                )
                val cursor = contentResolver.query(uri, projection, null, null, null)
                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        mDescriptionEditText?.setText(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Saving.DESCRIPTION)))
                        icon = IconLoader.parse(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Saving.ICON)))
                        startMoney = cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Saving.START_MONEY))
                        money = cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Saving.END_MONEY))
                        if (!cursor.isNull(cursor.getColumnIndexOrThrow(Contract.Saving.END_DATE))) {
                            expirationDate = DateUtils.getDateFromSQLDateString(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Saving.END_DATE)))
                        }
                        mNoteEditText?.setText(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Saving.NOTE)))
                        wallet = Wallet(
                            cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Saving.WALLET_ID)),
                            cursor.getString(cursor.getColumnIndexOrThrow(Contract.Saving.WALLET_NAME)),
                            IconLoader.parse(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Saving.WALLET_ICON))),
                            CurrencyManager.getCurrency(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Saving.WALLET_CURRENCY))),
                            0L,
                            0L
                        )
                    }
                    cursor.close()
                }
            } else {
                val projection = arrayOf(
                    Contract.Wallet.ID,
                    Contract.Wallet.NAME,
                    Contract.Wallet.ICON,
                    Contract.Wallet.CURRENCY,
                    Contract.Wallet.START_MONEY,
                    Contract.Wallet.TOTAL_MONEY
                )
                val currentWallet = PreferenceManager.getCurrentWallet()
                val cursor: Cursor? = if (currentWallet == PreferenceManager.TOTAL_WALLET_ID) {
                    val uri = DataContentProvider.CONTENT_WALLETS
                    contentResolver.query(uri, projection, null, null, null)
                } else {
                    val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_WALLETS, currentWallet)
                    contentResolver.query(uri, projection, null, null, null)
                }
                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        wallet = Wallet(
                            cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Wallet.ID)),
                            cursor.getString(cursor.getColumnIndexOrThrow(Contract.Wallet.NAME)),
                            IconLoader.parse(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Wallet.ICON))),
                            CurrencyManager.getCurrency(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Wallet.CURRENCY))),
                            cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Wallet.START_MONEY)),
                            cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Wallet.TOTAL_MONEY))
                        )
                    }
                    cursor.close()
                }
            }
        }

        val fragmentManager: FragmentManager = supportFragmentManager
        val iconPicker = IconPicker.createPicker(fragmentManager, TAG_ICON_PICKER, icon)
        mIconPicker = iconPicker
        mMoneyPicker = MoneyPicker.createPicker(fragmentManager, TAG_MONEY_PICKER, null, money)
        mStartMoneyPicker = MoneyPicker.createPicker(fragmentManager, TAG_START_MONEY_PICKER, null, startMoney)
        mExpDatePicker = DateTimePicker.createPicker(fragmentManager, TAG_EXP_DATE_PICKER, expirationDate)
        mWalletPicker = WalletPicker.createPicker(fragmentManager, TAG_WALLET_PICKER, wallet)

        mDescriptionEditText?.let { iconPicker.listenOn(it) }
    }

    @StringRes
    override fun getActivityTileRes(mode: Mode): Int {
        return when (mode) {
            Mode.NEW_ITEM -> R.string.title_activity_new_saving
            Mode.EDIT_ITEM -> R.string.title_activity_edit_saving
        }
    }

    private fun validate(): Boolean {
        return mDescriptionEditText?.validate() == true && mWalletEditText?.validate() == true
    }

    override fun onSaveChanges(mode: Mode) {
        if (validate()) {
            val wallet = mWalletPicker?.currentWallet ?: return
            val contentValues = ContentValues().apply {
                put(Contract.Saving.DESCRIPTION, mDescriptionEditText?.getTextAsString() ?: "")
                put(Contract.Saving.ICON, mIconPicker?.currentIcon?.toString() ?: "")
                put(Contract.Saving.START_MONEY, mStartMoneyPicker?.currentMoney ?: 0L)
                put(Contract.Saving.END_MONEY, mMoneyPicker?.currentMoney ?: 0L)
                put(Contract.Saving.WALLET_ID, wallet.id)
                val expDate = if (mExpDatePicker?.isSelected == true) {
                    mExpDatePicker?.currentDateTime?.let { DateUtils.getSQLDateString(it) }
                } else {
                    null
                }
                put(Contract.Saving.END_DATE, expDate)
                put(Contract.Saving.COMPLETE, false)
                put(Contract.Saving.NOTE, mNoteEditText?.getTextAsString() ?: "")
            }
            val contentResolver = contentResolver
            try {
                when (mode) {
                    Mode.NEW_ITEM -> {
                        contentResolver.insert(DataContentProvider.CONTENT_SAVINGS, contentValues)
                    }
                    Mode.EDIT_ITEM -> {
                        val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_SAVINGS, getItemId())
                        contentResolver.update(uri, contentValues, null, null)
                    }
                }
            } catch (e: SQLiteDataException) {
                if (e.errorCode != Contract.ErrorCode.WALLETS_NOT_CONSISTENT) {
                    throw e
                }
                ThemedDialog.buildMaterialDialog(this)
                    .setTitle(R.string.title_error)
                    .setMessage(R.string.error_wallet_currency_not_consistent)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                return
            }
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    override fun onIconChanged(tag: String?, icon: Icon?) {
        mAvatarImageView?.let { IconLoader.loadInto(icon, it) }
    }

    override fun onMoneyChanged(tag: String?, currency: CurrencyUnit?, money: Long) {
        when (tag) {
            TAG_MONEY_PICKER -> {
                mMoneyTextView?.text = mMoneyFormatter.getNotTintedString(
                    currency,
                    money,
                    MoneyFormatter.CurrencyMode.ALWAYS_HIDDEN
                )
                if (currency != null) {
                    mCurrencyTextView?.text = currency.symbol
                } else {
                    mCurrencyTextView?.text = "?"
                }
            }
            TAG_START_MONEY_PICKER -> {
                mStartMoneyEditText?.let { mMoneyFormatter.applyNotTinted(it, currency, money) }
            }
        }
    }

    override fun onDateTimeChanged(tag: String?, date: Date?) {
        if (date != null) {
            mExpirationDateEditText?.let { DateFormatter.applyDate(it, date) }
        } else {
            mExpirationDateEditText?.setText(null)
        }
    }

    override fun onWalletChanged(tag: String?, wallet: Wallet?) {
        if (wallet != null) {
            mWalletEditText?.setText(wallet.name)
            mMoneyPicker?.setCurrency(wallet.currency)
            mStartMoneyPicker?.setCurrency(wallet.currency)
        } else {
            mWalletEditText?.setText(null)
            mMoneyPicker?.setCurrency(null)
            mStartMoneyPicker?.setCurrency(null)
        }
    }

    companion object {
        private const val TAG_ICON_PICKER = "NewEditSavingActivity::Tag::IconPicker"
        private const val TAG_MONEY_PICKER = "NewEditSavingActivity::Tag::MoneyPicker"
        private const val TAG_START_MONEY_PICKER = "NewEditSavingActivity::Tag::StartMoneyPicker"
        private const val TAG_EXP_DATE_PICKER = "NewEditSavingActivity::Tag::ExpDatePicker"
        private const val TAG_WALLET_PICKER = "NewEditSavingActivity::Tag::WalletPicker"
    }
}
