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
import com.oriondev.moneywallet.broadcast.RecurrenceBroadcastReceiver
import com.oriondev.moneywallet.model.Category
import com.oriondev.moneywallet.model.CurrencyUnit
import com.oriondev.moneywallet.model.Event
import com.oriondev.moneywallet.model.Place
import com.oriondev.moneywallet.model.RecurrenceSetting
import com.oriondev.moneywallet.model.Wallet
import com.oriondev.moneywallet.picker.CategoryPicker
import com.oriondev.moneywallet.picker.EventPicker
import com.oriondev.moneywallet.picker.MoneyPicker
import com.oriondev.moneywallet.picker.PlacePicker
import com.oriondev.moneywallet.picker.RecurrencePicker
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
import java.util.Date

/**
 * Created by andrea on 06/11/18.
 */
class NewEditRecurrentTransactionActivity : NewEditItemActivity(),
    MoneyPicker.Controller,
    CategoryPicker.Controller,
    WalletPicker.SingleWalletController,
    RecurrencePicker.Controller,
    EventPicker.Controller,
    PlacePicker.Controller {

    private var mCurrencyTextView: TextView? = null
    private var mMoneyTextView: TextView? = null
    private var mDescriptionEditText: MaterialEditText? = null
    private var mCategoryEditText: MaterialEditText? = null
    private var mWalletEditText: MaterialEditText? = null
    private var mRecurrenceEditText: MaterialEditText? = null
    private var mEventEditText: MaterialEditText? = null
    private var mPlaceEditText: MaterialEditText? = null
    private var mNoteEditText: MaterialEditText? = null
    private var mConfirmedCheckBox: CheckBox? = null
    private var mCountInTotalCheckBox: CheckBox? = null

    private var mMoneyPicker: MoneyPicker? = null
    private var mCategoryPicker: CategoryPicker? = null
    private var mWalletPicker: WalletPicker? = null
    private var mRecurrencePicker: RecurrencePicker? = null
    private var mEventPicker: EventPicker? = null
    private var mPlacePicker: PlacePicker? = null

    private val mMoneyFormatter = MoneyFormatter.getInstance()

    override fun onCreateHeaderView(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?) {
        val view = inflater.inflate(R.layout.layout_header_new_edit_money_item, parent, true)
        mCurrencyTextView = view.findViewById(R.id.currency_text_view)
        mMoneyTextView = view.findViewById(R.id.money_text_view)
        mMoneyTextView?.setOnClickListener {
            mMoneyPicker?.showPicker()
        }
    }

    override fun onCreatePanelView(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?) {
        val view = inflater.inflate(R.layout.layout_panel_new_edit_recurrent_transaction, parent, true)
        val descriptionEditText = view.findViewById<MaterialEditText>(R.id.description_edit_text)
        val categoryEditText = view.findViewById<MaterialEditText>(R.id.category_edit_text)
        val walletEditText = view.findViewById<MaterialEditText>(R.id.wallet_edit_text)
        val recurrenceEditText = view.findViewById<MaterialEditText>(R.id.recurrence_edit_text)
        val eventEditText = view.findViewById<MaterialEditText>(R.id.event_edit_text)
        val placeEditText = view.findViewById<MaterialEditText>(R.id.place_edit_text)
        val noteEditText = view.findViewById<MaterialEditText>(R.id.note_edit_text)
        val confirmedCheckBox = view.findViewById<CheckBox>(R.id.confirmed_checkbox)
        val countInTotalCheckBox = view.findViewById<CheckBox>(R.id.count_in_total_checkbox)

        mDescriptionEditText = descriptionEditText
        mCategoryEditText = categoryEditText
        mWalletEditText = walletEditText
        mRecurrenceEditText = recurrenceEditText
        mEventEditText = eventEditText
        mPlaceEditText = placeEditText
        mNoteEditText = noteEditText
        mConfirmedCheckBox = confirmedCheckBox
        mCountInTotalCheckBox = countInTotalCheckBox

        categoryEditText.setTextViewMode(true)
        walletEditText.setTextViewMode(true)
        recurrenceEditText.setTextViewMode(true)
        eventEditText.setTextViewMode(true)
        placeEditText.setTextViewMode(true)

        categoryEditText.addValidator(object : Validator {
            override fun getErrorMessage(): String {
                return getString(R.string.error_input_missing_category)
            }

            override fun isValid(charSequence: CharSequence): Boolean {
                return mCategoryPicker?.isSelected == true
            }

            override fun autoValidate(): Boolean = false
        })

        walletEditText.addValidator(object : Validator {
            override fun getErrorMessage(): String {
                return getString(R.string.error_input_missing_wallet)
            }

            override fun isValid(charSequence: CharSequence): Boolean {
                return mWalletPicker?.isSelected == true
            }

            override fun autoValidate(): Boolean = false
        })

        categoryEditText.setOnClickListener {
            mCategoryPicker?.showPicker()
        }
        walletEditText.setOnClickListener {
            mWalletPicker?.showSingleWalletPicker()
        }
        recurrenceEditText.setOnClickListener {
            mRecurrencePicker?.showPicker()
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
        var money = 0L
        var category: Category? = null
        var wallet: Wallet? = null
        var recurrenceSetting: RecurrenceSetting? = null
        var event: Event? = null
        var place: Place? = null

        if (savedInstanceState == null) {
            val contentResolver = contentResolver
            if (getMode() == Mode.EDIT_ITEM) {
                val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_RECURRENT_TRANSACTIONS, getItemId())
                val projection = arrayOf(
                    Contract.RecurrentTransaction.MONEY,
                    Contract.RecurrentTransaction.DESCRIPTION,
                    Contract.RecurrentTransaction.CATEGORY_ID,
                    Contract.RecurrentTransaction.CATEGORY_NAME,
                    Contract.RecurrentTransaction.CATEGORY_ICON,
                    Contract.RecurrentTransaction.CATEGORY_TYPE,
                    Contract.RecurrentTransaction.CATEGORY_SHOW_REPORT,
                    Contract.RecurrentTransaction.DIRECTION,
                    Contract.RecurrentTransaction.WALLET_ID,
                    Contract.RecurrentTransaction.WALLET_NAME,
                    Contract.RecurrentTransaction.WALLET_ICON,
                    Contract.RecurrentTransaction.WALLET_CURRENCY,
                    Contract.RecurrentTransaction.PLACE_ID,
                    Contract.RecurrentTransaction.PLACE_NAME,
                    Contract.RecurrentTransaction.PLACE_ICON,
                    Contract.RecurrentTransaction.PLACE_ADDRESS,
                    Contract.RecurrentTransaction.PLACE_LATITUDE,
                    Contract.RecurrentTransaction.PLACE_LONGITUDE,
                    Contract.RecurrentTransaction.NOTE,
                    Contract.RecurrentTransaction.EVENT_ID,
                    Contract.RecurrentTransaction.EVENT_NAME,
                    Contract.RecurrentTransaction.EVENT_ICON,
                    Contract.RecurrentTransaction.EVENT_START_DATE,
                    Contract.RecurrentTransaction.EVENT_END_DATE,
                    Contract.RecurrentTransaction.CONFIRMED,
                    Contract.RecurrentTransaction.COUNT_IN_TOTAL,
                    Contract.RecurrentTransaction.START_DATE,
                    Contract.RecurrentTransaction.RULE
                )
                val cursor = contentResolver.query(uri, projection, null, null, null)
                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        money = cursor.getLong(cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.MONEY))
                        mDescriptionEditText?.setText(cursor.getString(cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.DESCRIPTION)))
                        category = Category(
                            cursor.getLong(cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.CATEGORY_ID)),
                            cursor.getString(cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.CATEGORY_NAME)),
                            IconLoader.parse(cursor.getString(cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.CATEGORY_ICON))),
                            Contract.CategoryType.fromValue(cursor.getInt(cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.CATEGORY_TYPE)))
                                ?: Contract.CategoryType.EXPENSE
                        )
                        wallet = Wallet(
                            cursor.getLong(cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.WALLET_ID)),
                            cursor.getString(cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.WALLET_NAME)),
                            IconLoader.parse(cursor.getString(cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.WALLET_ICON))),
                            CurrencyManager.getCurrency(cursor.getString(cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.WALLET_CURRENCY))),
                            0L,
                            0L
                        )
                        if (!cursor.isNull(cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.PLACE_ID))) {
                            val lat = if (cursor.isNull(cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.PLACE_LATITUDE))) {
                                null
                            } else {
                                cursor.getDouble(cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.PLACE_LATITUDE))
                            }
                            val lon = if (cursor.isNull(cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.PLACE_LONGITUDE))) {
                                null
                            } else {
                                cursor.getDouble(cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.PLACE_LONGITUDE))
                            }
                            place = Place(
                                cursor.getLong(cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.PLACE_ID)),
                                cursor.getString(cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.PLACE_NAME)),
                                IconLoader.parse(cursor.getString(cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.PLACE_ICON))),
                                cursor.getString(cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.PLACE_ADDRESS)),
                                lat,
                                lon
                            )
                        }
                        mNoteEditText?.setText(cursor.getString(cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.NOTE)))
                        if (!cursor.isNull(cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.EVENT_ID))) {
                            event = Event(
                                cursor.getLong(cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.EVENT_ID)),
                                cursor.getString(cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.EVENT_NAME)),
                                IconLoader.parse(cursor.getString(cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.EVENT_ICON))),
                                DateUtils.getDateFromSQLDateString(cursor.getString(cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.EVENT_START_DATE))),
                                DateUtils.getDateFromSQLDateString(cursor.getString(cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.EVENT_END_DATE)))
                            )
                        }
                        mConfirmedCheckBox?.isChecked = cursor.getInt(cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.CONFIRMED)) == 1
                        mCountInTotalCheckBox?.isChecked = cursor.getInt(cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.COUNT_IN_TOTAL)) == 1
                        val startDate = DateUtils.getDateFromSQLDateString(cursor.getString(cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.START_DATE)))
                        val rule = cursor.getString(cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.RULE))
                        if (startDate != null && rule != null) {
                            recurrenceSetting = RecurrenceSetting(startDate, rule)
                        }
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
                recurrenceSetting = RecurrenceSetting(Date(), RecurrenceSetting.TYPE_DAILY)
            }
        }

        val fragmentManager: FragmentManager = supportFragmentManager
        mMoneyPicker = MoneyPicker.createPicker(fragmentManager, TAG_MONEY_PICKER, null, money)
        mCategoryPicker = CategoryPicker.createPicker(fragmentManager, TAG_CATEGORY_PICKER, category)
        mWalletPicker = WalletPicker.createPicker(fragmentManager, TAG_WALLET_PICKER, wallet)
        mRecurrencePicker = RecurrencePicker.createPicker(fragmentManager, TAG_RECURRENCE_PICKER, recurrenceSetting)
        mEventPicker = EventPicker.createPicker(fragmentManager, TAG_EVENT_PICKER, event)
        mPlacePicker = PlacePicker.createPicker(fragmentManager, TAG_PLACE_PICKER, place)
    }

    @StringRes
    override fun getActivityTileRes(mode: Mode): Int {
        return when (mode) {
            Mode.NEW_ITEM -> R.string.title_activity_new_recurrence
            Mode.EDIT_ITEM -> R.string.title_activity_edit_recurrence
        }
    }

    private fun validate(): Boolean {
        return mCategoryEditText?.validate() == true && mWalletEditText?.validate() == true
    }

    override fun onSaveChanges(mode: Mode) {
        if (validate()) {
            val category = mCategoryPicker?.currentCategory ?: return
            val wallet = mWalletPicker?.currentWallet ?: return
            val recurrenceSetting = mRecurrencePicker?.currentSettings ?: return
            val contentValues = ContentValues().apply {
                put(Contract.RecurrentTransaction.MONEY, mMoneyPicker?.currentMoney ?: 0L)
                put(Contract.RecurrentTransaction.DESCRIPTION, mDescriptionEditText?.getTextAsString() ?: "")
                put(Contract.RecurrentTransaction.CATEGORY_ID, category.id)
                put(Contract.RecurrentTransaction.DIRECTION, category.direction)
                put(Contract.RecurrentTransaction.WALLET_ID, wallet.id)
                val placeId = if (mPlacePicker?.isSelected == true) mPlacePicker?.currentPlace?.id else null
                put(Contract.RecurrentTransaction.PLACE_ID, placeId)
                val eventId = if (mEventPicker?.isSelected == true) mEventPicker?.currentEvent?.id else null
                put(Contract.RecurrentTransaction.EVENT_ID, eventId)
                put(Contract.RecurrentTransaction.NOTE, mNoteEditText?.getTextAsString() ?: "")
                put(Contract.RecurrentTransaction.CONFIRMED, mConfirmedCheckBox?.isChecked == true)
                put(Contract.RecurrentTransaction.COUNT_IN_TOTAL, mCountInTotalCheckBox?.isChecked == true)
                put(Contract.RecurrentTransaction.START_DATE, DateUtils.getSQLDateString(recurrenceSetting.getStartDate()))
                put(Contract.RecurrentTransaction.RULE, recurrenceSetting.getRule())
            }
            val contentResolver = contentResolver
            when (mode) {
                Mode.NEW_ITEM -> {
                    contentResolver.insert(DataContentProvider.CONTENT_RECURRENT_TRANSACTIONS, contentValues)
                }
                Mode.EDIT_ITEM -> {
                    val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_RECURRENT_TRANSACTIONS, getItemId())
                    contentResolver.update(uri, contentValues, null, null)
                }
            }
            RecurrenceBroadcastReceiver.scheduleRecurrenceTask(this)
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    override fun onMoneyChanged(tag: String?, currency: CurrencyUnit?, money: Long) {
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

    override fun onCategoryChanged(tag: String?, category: Category?) {
        if (category != null) {
            mCategoryEditText?.setText(category.name)
        } else {
            mCategoryEditText?.setText(null)
        }
    }

    override fun onWalletChanged(tag: String?, wallet: Wallet?) {
        if (wallet != null) {
            mWalletEditText?.setText(wallet.name)
            mMoneyPicker?.setCurrency(wallet.currency)
        } else {
            mWalletEditText?.setText(null)
            mMoneyPicker?.setCurrency(null)
        }
    }

    override fun onRecurrenceSettingChanged(tag: String?, recurrenceSetting: RecurrenceSetting?) {
        mRecurrenceEditText?.setText(recurrenceSetting?.getUserReadableString(this))
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
        private const val TAG_MONEY_PICKER = "NewEditRecurrentTransactionActivity::Tag::MoneyPicker"
        private const val TAG_CATEGORY_PICKER = "NewEditRecurrentTransactionActivity::Tag::CategoryPicker"
        private const val TAG_WALLET_PICKER = "NewEditRecurrentTransactionActivity::Tag::WalletPicker"
        private const val TAG_RECURRENCE_PICKER = "NewEditRecurrentTransactionActivity::Tag::RecurrencePicker"
        private const val TAG_EVENT_PICKER = "NewEditRecurrentTransactionActivity::Tag::EventPicker"
        private const val TAG_PLACE_PICKER = "NewEditRecurrentTransactionActivity::Tag::PlacePicker"
    }
}
