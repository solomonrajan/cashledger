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
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.fragment.app.FragmentManager
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.CurrencyUnit
import com.oriondev.moneywallet.model.Event
import com.oriondev.moneywallet.model.Place
import com.oriondev.moneywallet.model.Wallet
import com.oriondev.moneywallet.picker.EventPicker
import com.oriondev.moneywallet.picker.MoneyPicker
import com.oriondev.moneywallet.picker.PlacePicker
import com.oriondev.moneywallet.picker.WalletPicker
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.view.text.MaterialEditText
import com.oriondev.moneywallet.ui.view.text.Validator
import com.oriondev.moneywallet.utils.CurrencyManager
import com.oriondev.moneywallet.utils.DateUtils
import com.oriondev.moneywallet.utils.IconLoader
import com.oriondev.moneywallet.utils.MoneyFormatter

/**
 * Created by andrea on 14/03/18.
 */
class NewEditTransferModelActivity : NewEditItemActivity(),
    MoneyPicker.Controller,
    WalletPicker.SingleWalletController,
    EventPicker.Controller,
    PlacePicker.Controller {

    private var mCurrencyTextView: TextView? = null
    private var mMoneyTextView: TextView? = null

    private var mDescriptionEditText: MaterialEditText? = null
    private var mWalletFromEditText: MaterialEditText? = null
    private var mWalletToEditText: MaterialEditText? = null
    private var mTaxEditText: MaterialEditText? = null
    private var mEventEditText: MaterialEditText? = null
    private var mPlaceEditText: MaterialEditText? = null
    private var mNoteEditText: MaterialEditText? = null
    private var mConfirmedCheckBox: CheckBox? = null
    private var mCountInTotalCheckBox: CheckBox? = null

    private var mMoneyPicker: MoneyPicker? = null
    private var mWalletFromPicker: WalletPicker? = null
    private var mWalletToPicker: WalletPicker? = null
    private var mTaxPicker: MoneyPicker? = null
    private var mEventPicker: EventPicker? = null
    private var mPlacePicker: PlacePicker? = null

    private val mMoneyFormatter = MoneyFormatter.getInstance()

    override fun onCreateHeaderView(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?) {
        val view = inflater.inflate(R.layout.layout_header_new_edit_money_multiple_currencies_item, parent, true)
        mCurrencyTextView = view.findViewById(R.id.currency_text_view)
        mMoneyTextView = view.findViewById(R.id.money_text_view)
        mMoneyTextView?.setOnClickListener {
            mMoneyPicker?.showPicker()
        }
    }

    override fun onCreatePanelView(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?) {
        val view = inflater.inflate(R.layout.layout_panel_new_edit_model_transfer, parent, true)
        val descriptionEditText = view.findViewById<MaterialEditText>(R.id.description_edit_text)
        val walletFromEditText = view.findViewById<MaterialEditText>(R.id.wallet_from_edit_text)
        val walletToEditText = view.findViewById<MaterialEditText>(R.id.wallet_to_edit_text)
        val taxEditText = view.findViewById<MaterialEditText>(R.id.money_tax_edit_text)
        val eventEditText = view.findViewById<MaterialEditText>(R.id.event_edit_text)
        val placeEditText = view.findViewById<MaterialEditText>(R.id.place_edit_text)
        val noteEditText = view.findViewById<MaterialEditText>(R.id.note_edit_text)
        val confirmedCheckBox = view.findViewById<CheckBox>(R.id.confirmed_checkbox)
        val countInTotalCheckBox = view.findViewById<CheckBox>(R.id.count_in_total_checkbox)

        mDescriptionEditText = descriptionEditText
        mWalletFromEditText = walletFromEditText
        mWalletToEditText = walletToEditText
        mTaxEditText = taxEditText
        mEventEditText = eventEditText
        mPlaceEditText = placeEditText
        mNoteEditText = noteEditText
        mConfirmedCheckBox = confirmedCheckBox
        mCountInTotalCheckBox = countInTotalCheckBox

        walletFromEditText.setTextViewMode(true)
        walletToEditText.setTextViewMode(true)
        taxEditText.setTextViewMode(true)
        eventEditText.setTextViewMode(true)
        placeEditText.setTextViewMode(true)

        walletFromEditText.addValidator(object : Validator() {
            override fun getErrorMessage(): String {
                return getString(R.string.error_input_missing_wallet)
            }

            override fun isValid(charSequence: CharSequence): Boolean {
                return mWalletFromPicker?.isSelected == true
            }

            override fun autoValidate(): Boolean = false
        })

        walletToEditText.addValidator(object : Validator() {
            override fun getErrorMessage(): String {
                return getString(R.string.error_input_missing_wallet)
            }

            override fun isValid(charSequence: CharSequence): Boolean {
                return mWalletToPicker?.isSelected == true
            }

            override fun autoValidate(): Boolean = false
        })

        walletFromEditText.setOnClickListener {
            mWalletFromPicker?.showSingleWalletPicker()
        }
        walletToEditText.setOnClickListener {
            mWalletToPicker?.showSingleWalletPicker()
        }
        taxEditText.setOnClickListener {
            mTaxPicker?.showPicker()
        }
        eventEditText.setOnClickListener {
            mEventPicker?.showPicker(null)
        }
        eventEditText.setOnCancelButtonClickListener(object : MaterialEditText.CancelButtonListener {
            override fun onCancelButtonClick(materialEditText: MaterialEditText): Boolean {
                mEventPicker?.currentEvent = null
                return false
            }
        })
        placeEditText.setOnClickListener {
            mPlacePicker?.showPicker()
        }
        placeEditText.setOnCancelButtonClickListener(object : MaterialEditText.CancelButtonListener {
            override fun onCancelButtonClick(materialEditText: MaterialEditText): Boolean {
                mPlacePicker?.currentPlace = null
                return false
            }
        })
    }

    override fun onViewCreated(savedInstanceState: Bundle?) {
        super.onViewCreated(savedInstanceState)
        var moneyFrom = 0L
        var tax = 0L
        var walletFrom: Wallet? = null
        var walletTo: Wallet? = null
        var event: Event? = null
        var place: Place? = null

        if (savedInstanceState == null) {
            val contentResolver = contentResolver
            if (getMode() == Mode.EDIT_ITEM) {
                val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSFER_MODELS, getItemId())
                val projection = arrayOf(
                    Contract.TransferModel.DESCRIPTION,
                    Contract.TransferModel.WALLET_FROM_ID,
                    Contract.TransferModel.WALLET_FROM_NAME,
                    Contract.TransferModel.WALLET_FROM_ICON,
                    Contract.TransferModel.WALLET_FROM_CURRENCY,
                    Contract.TransferModel.WALLET_TO_ID,
                    Contract.TransferModel.WALLET_TO_NAME,
                    Contract.TransferModel.WALLET_TO_ICON,
                    Contract.TransferModel.WALLET_TO_CURRENCY,
                    Contract.TransferModel.MONEY_FROM,
                    Contract.TransferModel.MONEY_TO,
                    Contract.TransferModel.MONEY_TAX,
                    Contract.TransferModel.NOTE,
                    Contract.TransferModel.EVENT_ID,
                    Contract.TransferModel.EVENT_NAME,
                    Contract.TransferModel.EVENT_ICON,
                    Contract.TransferModel.EVENT_START_DATE,
                    Contract.TransferModel.EVENT_END_DATE,
                    Contract.TransferModel.PLACE_ID,
                    Contract.TransferModel.PLACE_NAME,
                    Contract.TransferModel.PLACE_ICON,
                    Contract.TransferModel.PLACE_ADDRESS,
                    Contract.TransferModel.PLACE_LATITUDE,
                    Contract.TransferModel.PLACE_LONGITUDE,
                    Contract.TransferModel.CONFIRMED,
                    Contract.TransferModel.COUNT_IN_TOTAL
                )
                val cursor = contentResolver.query(uri, projection, null, null, null)
                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        mDescriptionEditText?.setText(cursor.getString(cursor.getColumnIndex(Contract.TransferModel.DESCRIPTION)))
                        walletFrom = Wallet(
                            cursor.getLong(cursor.getColumnIndex(Contract.TransferModel.WALLET_FROM_ID)),
                            cursor.getString(cursor.getColumnIndex(Contract.TransferModel.WALLET_FROM_NAME)),
                            IconLoader.parse(cursor.getString(cursor.getColumnIndex(Contract.TransferModel.WALLET_FROM_ICON))),
                            CurrencyManager.getCurrency(cursor.getString(cursor.getColumnIndex(Contract.TransferModel.WALLET_FROM_CURRENCY))),
                            0L,
                            0L
                        )
                        walletTo = Wallet(
                            cursor.getLong(cursor.getColumnIndex(Contract.TransferModel.WALLET_TO_ID)),
                            cursor.getString(cursor.getColumnIndex(Contract.TransferModel.WALLET_TO_NAME)),
                            IconLoader.parse(cursor.getString(cursor.getColumnIndex(Contract.TransferModel.WALLET_TO_ICON))),
                            CurrencyManager.getCurrency(cursor.getString(cursor.getColumnIndex(Contract.TransferModel.WALLET_TO_CURRENCY))),
                            0L,
                            0L
                        )
                        moneyFrom = cursor.getLong(cursor.getColumnIndex(Contract.TransferModel.MONEY_FROM))
                        tax = cursor.getLong(cursor.getColumnIndex(Contract.TransferModel.MONEY_TAX))
                        if (!cursor.isNull(cursor.getColumnIndex(Contract.TransferModel.PLACE_ID))) {
                            val lat = if (cursor.isNull(cursor.getColumnIndex(Contract.TransferModel.PLACE_LATITUDE))) {
                                null
                            } else {
                                cursor.getDouble(cursor.getColumnIndex(Contract.TransferModel.PLACE_LATITUDE))
                            }
                            val lon = if (cursor.isNull(cursor.getColumnIndex(Contract.TransferModel.PLACE_LONGITUDE))) {
                                null
                            } else {
                                cursor.getDouble(cursor.getColumnIndex(Contract.TransferModel.PLACE_LONGITUDE))
                            }
                            place = Place(
                                cursor.getLong(cursor.getColumnIndex(Contract.TransferModel.PLACE_ID)),
                                cursor.getString(cursor.getColumnIndex(Contract.TransferModel.PLACE_NAME)),
                                IconLoader.parse(cursor.getString(cursor.getColumnIndex(Contract.TransferModel.PLACE_ICON))),
                                cursor.getString(cursor.getColumnIndex(Contract.TransferModel.PLACE_ADDRESS)),
                                lat,
                                lon
                            )
                        }
                        mNoteEditText?.setText(cursor.getString(cursor.getColumnIndex(Contract.TransferModel.NOTE)))
                        if (!cursor.isNull(cursor.getColumnIndex(Contract.TransferModel.EVENT_ID))) {
                            event = Event(
                                cursor.getLong(cursor.getColumnIndex(Contract.TransferModel.EVENT_ID)),
                                cursor.getString(cursor.getColumnIndex(Contract.TransferModel.EVENT_NAME)),
                                IconLoader.parse(cursor.getString(cursor.getColumnIndex(Contract.TransferModel.EVENT_ICON))),
                                DateUtils.getDateFromSQLDateString(cursor.getString(cursor.getColumnIndex(Contract.TransferModel.EVENT_START_DATE))),
                                DateUtils.getDateFromSQLDateString(cursor.getString(cursor.getColumnIndex(Contract.TransferModel.EVENT_END_DATE)))
                            )
                        }
                        mConfirmedCheckBox?.isChecked = cursor.getInt(cursor.getColumnIndex(Contract.TransferModel.CONFIRMED)) == 1
                        mCountInTotalCheckBox?.isChecked = cursor.getInt(cursor.getColumnIndex(Contract.TransferModel.COUNT_IN_TOTAL)) == 1
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
                        walletFrom = Wallet(
                            cursor.getLong(cursor.getColumnIndex(Contract.Wallet.ID)),
                            cursor.getString(cursor.getColumnIndex(Contract.Wallet.NAME)),
                            IconLoader.parse(cursor.getString(cursor.getColumnIndex(Contract.Wallet.ICON))),
                            CurrencyManager.getCurrency(cursor.getString(cursor.getColumnIndex(Contract.Wallet.CURRENCY))),
                            cursor.getLong(cursor.getColumnIndex(Contract.Wallet.START_MONEY)),
                            cursor.getLong(cursor.getColumnIndex(Contract.Wallet.TOTAL_MONEY))
                        )
                    }
                    cursor.close()
                }
            }
        }

        val fragmentManager: FragmentManager = supportFragmentManager
        mMoneyPicker = MoneyPicker.createPicker(fragmentManager, TAG_MONEY_PICKER, null, moneyFrom)
        mTaxPicker = MoneyPicker.createPicker(fragmentManager, TAG_TAX_PICKER, null, tax)
        mWalletFromPicker = WalletPicker.createPicker(fragmentManager, TAG_WALLET_FROM_PICKER, walletFrom)
        mWalletToPicker = WalletPicker.createPicker(fragmentManager, TAG_WALLET_TO_PICKER, walletTo)
        mEventPicker = EventPicker.createPicker(fragmentManager, TAG_EVENT_PICKER, event)
        mPlacePicker = PlacePicker.createPicker(fragmentManager, TAG_PLACE_PICKER, place)
    }

    @StringRes
    override fun getActivityTileRes(mode: Mode): Int {
        return when (mode) {
            Mode.NEW_ITEM -> R.string.title_activity_new_model
            Mode.EDIT_ITEM -> R.string.title_activity_edit_model
        }
    }

    private fun validate(): Boolean {
        return mWalletFromEditText?.validate() == true && mWalletToEditText?.validate() == true
    }

    override fun onSaveChanges(mode: Mode) {
        if (validate()) {
            val walletFrom = mWalletFromPicker?.currentWallet ?: return
            val walletTo = mWalletToPicker?.currentWallet ?: return
            val contentValues = ContentValues().apply {
                put(Contract.TransferModel.DESCRIPTION, mDescriptionEditText?.getTextAsString() ?: "")
                put(Contract.TransferModel.WALLET_FROM_ID, walletFrom.id)
                put(Contract.TransferModel.WALLET_TO_ID, walletTo.id)
                put(Contract.TransferModel.MONEY_FROM, mMoneyPicker?.currentMoney ?: 0L)
                put(Contract.TransferModel.MONEY_TO, mMoneyPicker?.currentMoney ?: 0L)
                put(Contract.TransferModel.MONEY_TAX, mTaxPicker?.currentMoney ?: 0L)
                put(Contract.TransferModel.NOTE, mNoteEditText?.getTextAsString() ?: "")
                val eventId = if (mEventPicker?.isSelected == true) mEventPicker?.currentEvent?.id else null
                put(Contract.TransferModel.EVENT_ID, eventId)
                val placeId = if (mPlacePicker?.isSelected == true) mPlacePicker?.currentPlace?.id else null
                put(Contract.TransferModel.PLACE_ID, placeId)
                put(Contract.TransferModel.CONFIRMED, mConfirmedCheckBox?.isChecked == true)
                put(Contract.TransferModel.COUNT_IN_TOTAL, mCountInTotalCheckBox?.isChecked == true)
            }
            val contentResolver = contentResolver
            when (mode) {
                Mode.NEW_ITEM -> {
                    contentResolver.insert(DataContentProvider.CONTENT_TRANSFER_MODELS, contentValues)
                }
                Mode.EDIT_ITEM -> {
                    val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSFER_MODELS, getItemId())
                    contentResolver.update(uri, contentValues, null, null)
                }
            }
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    override fun onMoneyChanged(tag: String?, currency: CurrencyUnit?, money: Long) {
        when (tag) {
            TAG_MONEY_PICKER -> {
                if (currency != null) {
                    mCurrencyTextView?.text = currency.symbol
                } else {
                    mCurrencyTextView?.text = "?"
                }
                mMoneyTextView?.text = mMoneyFormatter.getNotTintedString(
                    currency,
                    money,
                    MoneyFormatter.CurrencyMode.ALWAYS_HIDDEN
                )
            }
            TAG_TAX_PICKER -> {
                mTaxEditText?.let { mMoneyFormatter.applyNotTinted(it, currency, money) }
            }
        }
    }

    override fun onWalletChanged(tag: String?, wallet: Wallet?) {
        when (tag) {
            TAG_WALLET_FROM_PICKER -> {
                if (wallet != null) {
                    mWalletFromEditText?.setText(wallet.name)
                    mMoneyPicker?.setCurrency(wallet.currency)
                    mTaxPicker?.setCurrency(wallet.currency)
                } else {
                    mWalletFromEditText?.setText(null)
                    mMoneyPicker?.setCurrency(null)
                    mTaxPicker?.setCurrency(null)
                }
            }
            TAG_WALLET_TO_PICKER -> {
                if (wallet != null) {
                    mWalletToEditText?.setText(wallet.name)
                } else {
                    mWalletToEditText?.setText(null)
                }
            }
        }
    }

    override fun onEventChanged(tag: String?, event: Event?) {
        if (event != null) {
            mEventEditText?.setText(event.name)
        } else {
            mEventEditText?.setText(null)
        }
    }

    override fun onPlaceChanged(tag: String?, place: Place?) {
        if (place != null) {
            mPlaceEditText?.setText(place.name)
        } else {
            mPlaceEditText?.setText(null)
        }
    }

    companion object {
        private const val TAG_MONEY_PICKER = "NewEditTransferModelActivity::Tag::MoneyPicker"
        private const val TAG_WALLET_FROM_PICKER = "NewEditTransferModelActivity::Tag::WalletFromPicker"
        private const val TAG_WALLET_TO_PICKER = "NewEditTransferModelActivity::Tag::WalletToPicker"
        private const val TAG_TAX_PICKER = "NewEditTransferModelActivity::Tag::TaxPicker"
        private const val TAG_EVENT_PICKER = "NewEditTransferModelActivity::Tag::EventPicker"
        private const val TAG_PLACE_PICKER = "NewEditTransferModelActivity::Tag::PlacePicker"
    }
}
