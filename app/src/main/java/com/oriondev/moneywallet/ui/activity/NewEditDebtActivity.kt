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
import android.content.DialogInterface
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.CurrencyUnit
import com.oriondev.moneywallet.model.Icon
import com.oriondev.moneywallet.model.Person
import com.oriondev.moneywallet.model.Place
import com.oriondev.moneywallet.model.Wallet
import com.oriondev.moneywallet.picker.DateTimePicker
import com.oriondev.moneywallet.picker.IconPicker
import com.oriondev.moneywallet.picker.MoneyPicker
import com.oriondev.moneywallet.picker.PersonPicker
import com.oriondev.moneywallet.picker.PlacePicker
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
import java.util.Calendar
import java.util.Date

/**
 * Created by andrea on 06/03/18.
 */
class NewEditDebtActivity : NewEditItemActivity(),
    IconPicker.Controller,
    MoneyPicker.Controller,
    DateTimePicker.Controller,
    WalletPicker.SingleWalletController,
    PersonPicker.Controller,
    PlacePicker.Controller {

    private var mAvatarImageView: ImageView? = null
    private var mCurrencyTextView: TextView? = null
    private var mMoneyTextView: TextView? = null
    private var mDescriptionEditText: MaterialEditText? = null
    private var mDateEditText: MaterialEditText? = null
    private var mExpirationDateEditText: MaterialEditText? = null
    private var mWalletEditText: MaterialEditText? = null
    private var mPeopleEditText: MaterialEditText? = null
    private var mPlaceEditText: MaterialEditText? = null
    private var mNoteEditText: MaterialEditText? = null

    private var mIconPicker: IconPicker? = null
    private var mMoneyPicker: MoneyPicker? = null
    private var mDatePicker: DateTimePicker? = null
    private var mExpDatePicker: DateTimePicker? = null
    private var mWalletPicker: WalletPicker? = null
    private var mPersonPicker: PersonPicker? = null
    private var mPlacePicker: PlacePicker? = null

    private var mDebtType: Contract.DebtType? = null

    private val mMoneyFormatter: MoneyFormatter = MoneyFormatter.getInstance()

    override fun onCreateHeaderView(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?) {
        val view = inflater.inflate(R.layout.layout_header_new_edit_icon_money_item, parent, true)
        val avatarIv: ImageView = view.findViewById(R.id.avatar_image_view)
        val currencyTv: TextView = view.findViewById(R.id.currency_text_view)
        val moneyTv: TextView = view.findViewById(R.id.money_text_view)

        mAvatarImageView = avatarIv
        mCurrencyTextView = currencyTv
        mMoneyTextView = moneyTv

        avatarIv.setOnClickListener {
            mIconPicker?.showPicker()
        }
        moneyTv.setOnClickListener {
            mMoneyPicker?.showPicker()
        }
    }

    override fun onCreatePanelView(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?) {
        val view = inflater.inflate(R.layout.layout_panel_new_edit_debt, parent, true)
        val descriptionEt: MaterialEditText = view.findViewById(R.id.description_edit_text)
        val dateEt: MaterialEditText = view.findViewById(R.id.date_edit_text)
        val expirationDateEt: MaterialEditText = view.findViewById(R.id.expiration_date_edit_text)
        val walletEt: MaterialEditText = view.findViewById(R.id.wallet_edit_text)
        val peopleEt: MaterialEditText = view.findViewById(R.id.people_edit_text)
        val placeEt: MaterialEditText = view.findViewById(R.id.place_edit_text)
        val noteEt: MaterialEditText = view.findViewById(R.id.note_edit_text)

        mDescriptionEditText = descriptionEt
        mDateEditText = dateEt
        mExpirationDateEditText = expirationDateEt
        mWalletEditText = walletEt
        mPeopleEditText = peopleEt
        mPlaceEditText = placeEt
        mNoteEditText = noteEt

        dateEt.setTextViewMode(true)
        expirationDateEt.setTextViewMode(true)
        walletEt.setTextViewMode(true)
        peopleEt.setTextViewMode(true)
        placeEt.setTextViewMode(true)

        descriptionEt.addValidator(NonEmptyTextValidator(this, R.string.error_input_missing_description))
        dateEt.addValidator(object : Validator {
            override fun getErrorMessage(): String = getString(R.string.error_input_missing_date)
            override fun isValid(charSequence: CharSequence): Boolean = mDatePicker?.isSelected == true
            override fun autoValidate(): Boolean = false
        })
        walletEt.addValidator(object : Validator {
            override fun getErrorMessage(): String = getString(R.string.error_input_missing_wallet)
            override fun isValid(charSequence: CharSequence): Boolean = mWalletPicker?.isSelected == true
            override fun autoValidate(): Boolean = false
        })

        dateEt.setOnClickListener {
            mDatePicker?.showDatePicker()
        }
        expirationDateEt.setOnClickListener {
            mExpDatePicker?.showDatePicker()
        }
        expirationDateEt.setOnCancelButtonClickListener(object : MaterialEditText.CancelButtonListener {
            override fun onCancelButtonClick(materialEditText: MaterialEditText): Boolean {
                mExpDatePicker?.currentDateTime = null
                return true
            }
        })
        walletEt.setOnClickListener {
            mWalletPicker?.showSingleWalletPicker()
        }
        peopleEt.setOnClickListener {
            mPersonPicker?.showPicker()
        }
        peopleEt.setOnCancelButtonClickListener(object : MaterialEditText.CancelButtonListener {
            override fun onCancelButtonClick(materialEditText: MaterialEditText): Boolean {
                mPersonPicker?.setPeople(null)
                return true
            }
        })
        placeEt.setOnClickListener {
            mPlacePicker?.showPicker()
        }
        placeEt.setOnCancelButtonClickListener(object : MaterialEditText.CancelButtonListener {
            override fun onCancelButtonClick(materialEditText: MaterialEditText): Boolean {
                mPlacePicker?.currentPlace = null
                return true
            }
        })
    }

    override fun onViewCreated(savedInstanceState: Bundle?) {
        super.onViewCreated(savedInstanceState)
        var icon: Icon? = null
        var money = 0L
        val calendar = Calendar.getInstance()
        var date: Date? = calendar.time
        var expirationDate: Date? = null
        var wallet: Wallet? = null
        var people: Array<Person>? = null
        var place: Place? = null
        if (savedInstanceState == null) {
            val resolver = contentResolver
            if (getMode() == Mode.EDIT_ITEM) {
                var uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_DEBTS, getItemId())
                val projection = arrayOf(
                    Contract.Debt.ICON,
                    Contract.Debt.DESCRIPTION,
                    Contract.Debt.TYPE,
                    Contract.Debt.MONEY,
                    Contract.Debt.DATE,
                    Contract.Debt.EXPIRATION_DATE,
                    Contract.Debt.WALLET_ID,
                    Contract.Debt.WALLET_NAME,
                    Contract.Debt.WALLET_ICON,
                    Contract.Debt.WALLET_CURRENCY,
                    Contract.Debt.NOTE,
                    Contract.Debt.PLACE_ID,
                    Contract.Debt.PLACE_NAME,
                    Contract.Debt.PLACE_ICON,
                    Contract.Debt.PLACE_ADDRESS,
                    Contract.Debt.PLACE_LATITUDE,
                    Contract.Debt.PLACE_LONGITUDE
                )
                resolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        mDebtType = Contract.DebtType.fromValue(cursor.getInt(cursor.getColumnIndexOrThrow(Contract.Debt.TYPE)))
                        mDescriptionEditText?.setText(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Debt.DESCRIPTION)))
                        mNoteEditText?.setText(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Debt.NOTE)))
                        icon = IconLoader.parse(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Debt.ICON)))
                        money = cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Debt.MONEY))
                        date = DateUtils.getDateFromSQLDateString(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Debt.DATE)))
                        val expIndex = cursor.getColumnIndexOrThrow(Contract.Debt.EXPIRATION_DATE)
                        if (!cursor.isNull(expIndex)) {
                            expirationDate = DateUtils.getDateFromSQLDateString(cursor.getString(expIndex))
                        }
                        wallet = Wallet(
                            cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Debt.WALLET_ID)),
                            cursor.getString(cursor.getColumnIndexOrThrow(Contract.Debt.WALLET_NAME)),
                            IconLoader.parse(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Debt.WALLET_ICON))),
                            CurrencyManager.getCurrency(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Debt.WALLET_CURRENCY))),
                            0L,
                            0L
                        )
                        val placeIdIndex = cursor.getColumnIndexOrThrow(Contract.Debt.PLACE_ID)
                        if (!cursor.isNull(placeIdIndex)) {
                            val latIndex = cursor.getColumnIndexOrThrow(Contract.Debt.PLACE_LATITUDE)
                            val lonIndex = cursor.getColumnIndexOrThrow(Contract.Debt.PLACE_LONGITUDE)
                            val lat = if (cursor.isNull(latIndex)) null else cursor.getDouble(latIndex)
                            val lon = if (cursor.isNull(lonIndex)) null else cursor.getDouble(lonIndex)
                            place = Place(
                                cursor.getLong(placeIdIndex),
                                cursor.getString(cursor.getColumnIndexOrThrow(Contract.Debt.PLACE_NAME)),
                                IconLoader.parse(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Debt.PLACE_ICON))),
                                cursor.getString(cursor.getColumnIndexOrThrow(Contract.Debt.PLACE_ADDRESS)),
                                lat,
                                lon
                            )
                        }
                    }
                }
                uri = Uri.withAppendedPath(uri, "people")
                val peopleProjection = arrayOf(
                    Contract.Person.ID,
                    Contract.Person.NAME,
                    Contract.Person.ICON
                )
                resolver.query(uri, peopleProjection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val peopleList = ArrayList<Person>()
                        for (i in 0 until cursor.count) {
                            if (cursor.moveToPosition(i)) {
                                peopleList.add(
                                    Person(
                                        cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Person.ID)),
                                        cursor.getString(cursor.getColumnIndexOrThrow(Contract.Person.NAME)),
                                        IconLoader.parse(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Person.ICON)))
                                    )
                                )
                            }
                        }
                        people = peopleList.toTypedArray()
                    }
                }
            } else {
                mDebtType = intent?.getSerializableExtra(TYPE) as? Contract.DebtType
                val walletProjection = arrayOf(
                    Contract.Wallet.ID,
                    Contract.Wallet.NAME,
                    Contract.Wallet.ICON,
                    Contract.Wallet.CURRENCY,
                    Contract.Wallet.START_MONEY,
                    Contract.Wallet.TOTAL_MONEY
                )
                val currentWallet = PreferenceManager.getCurrentWallet()
                val walletUri = if (currentWallet == PreferenceManager.TOTAL_WALLET_ID) {
                    DataContentProvider.CONTENT_WALLETS
                } else {
                    ContentUris.withAppendedId(DataContentProvider.CONTENT_WALLETS, currentWallet)
                }
                resolver.query(walletUri, walletProjection, null, null, null)?.use { cursor ->
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
                }
            }
        } else {
            mDebtType = savedInstanceState.getSerializable(SS_TYPE) as? Contract.DebtType
        }
        val fragmentManager: FragmentManager = supportFragmentManager
        val iconPicker = IconPicker.createPicker(fragmentManager, TAG_ICON_PICKER, icon)
        mIconPicker = iconPicker
        mMoneyPicker = MoneyPicker.createPicker(fragmentManager, TAG_MONEY_PICKER, null, money)
        mDatePicker = DateTimePicker.createPicker(fragmentManager, TAG_DATE_PICKER, date)
        mExpDatePicker = DateTimePicker.createPicker(fragmentManager, TAG_EXP_DATE_PICKER, expirationDate)
        mWalletPicker = WalletPicker.createPicker(fragmentManager, TAG_WALLET_PICKER, wallet)
        mPersonPicker = PersonPicker.createPicker(fragmentManager, TAG_PERSON_PICKER, people)
        mPlacePicker = PlacePicker.createPicker(fragmentManager, TAG_PLACE_PICKER, place)
        mDescriptionEditText?.let { iconPicker.listenOn(it) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable(SS_TYPE, mDebtType)
    }

    override fun getActivityTileRes(mode: Mode): Int {
        return when (mode) {
            Mode.NEW_ITEM -> R.string.title_activity_new_debt
            Mode.EDIT_ITEM -> R.string.title_activity_edit_debt
        }
    }

    private fun validate(): Boolean {
        return mDescriptionEditText?.validate() == true && mWalletEditText?.validate() == true
    }

    override fun onSaveChanges(mode: Mode) {
        if (validate()) {
            if (mode == Mode.NEW_ITEM) {
                val listener = DialogInterface.OnClickListener { _, which ->
                    updateDatabase(mode, which == DialogInterface.BUTTON_POSITIVE)
                    setResult(Activity.RESULT_OK)
                    finish()
                }
                ThemedDialog.buildMaterialDialog(this)
                    .setTitle(R.string.title_debt_insert_master_transaction)
                    .setMessage(R.string.message_debt_insert_master_transaction)
                    .setPositiveButton(android.R.string.yes, listener)
                    .setNegativeButton(android.R.string.no, listener)
                    .show()
            } else {
                updateDatabase(mode, false)
            }
        }
    }

    private fun updateDatabase(mode: Mode, addMasterTransaction: Boolean) {
        val debtType = mDebtType ?: return
        val wallet = mWalletPicker?.currentWallet ?: return
        val contentValues = ContentValues().apply {
            put(Contract.Debt.TYPE, debtType.value)
            put(Contract.Debt.ICON, mIconPicker?.currentIcon?.toString() ?: "")
            put(Contract.Debt.DESCRIPTION, mDescriptionEditText?.getTextAsString() ?: "")
            val date = mDatePicker?.currentDateTime
            val dateStr = if (date != null) DateUtils.getSQLDateString(date) else ""
            put(Contract.Debt.DATE, dateStr)
            val expDate = if (mExpDatePicker?.isSelected == true) {
                mExpDatePicker?.currentDateTime?.let { DateUtils.getSQLDateString(it) }
            } else {
                null
            }
            put(Contract.Debt.EXPIRATION_DATE, expDate)
            put(Contract.Debt.WALLET_ID, wallet.id)
            put(Contract.Debt.NOTE, mNoteEditText?.getTextAsString() ?: "")
            val placeId = if (mPlacePicker?.isSelected == true) mPlacePicker?.currentPlace?.id else null
            put(Contract.Debt.PLACE_ID, placeId)
            put(Contract.Debt.MONEY, mMoneyPicker?.currentMoney ?: 0L)
            put(Contract.Debt.ARCHIVED, false)
            put(Contract.Debt.PEOPLE_IDS, Contract.getObjectIds(mPersonPicker?.currentPeople))
            put(Contract.Debt.INSERT_MASTER_TRANSACTION, addMasterTransaction)
        }
        val resolver = contentResolver
        try {
            when (mode) {
                Mode.NEW_ITEM -> {
                    resolver.insert(DataContentProvider.CONTENT_DEBTS, contentValues)
                }
                Mode.EDIT_ITEM -> {
                    val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_DEBTS, getItemId())
                    resolver.update(uri, contentValues, null, null)
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

    override fun onIconChanged(tag: String?, icon: Icon?) {
        mAvatarImageView?.let { IconLoader.loadInto(icon, it) }
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

    override fun onDateTimeChanged(tag: String?, date: Date?) {
        when (tag) {
            TAG_DATE_PICKER -> {
                if (date != null) {
                    mDateEditText?.let { DateFormatter.applyDate(it, date) }
                } else {
                    mDateEditText?.setText(null)
                }
            }
            TAG_EXP_DATE_PICKER -> {
                if (date != null) {
                    mExpirationDateEditText?.let { DateFormatter.applyDate(it, date) }
                } else {
                    mExpirationDateEditText?.setText(null)
                }
            }
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

    override fun onPeopleChanged(tag: String?, people: Array<Person>?) {
        if (people != null) {
            val builder = StringBuilder()
            for (i in people.indices) {
                if (i != 0) {
                    builder.append(", ")
                }
                builder.append(people[i].name)
            }
            mPeopleEditText?.setText(builder)
        } else {
            mPeopleEditText?.setText(null)
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
        const val TYPE = "NewEditDebtActivity::Argument::Type"
        const val SS_TYPE = "NewEditDebtActivity::SavedState::Type"

        private const val TAG_ICON_PICKER = "NewEditDebtActivity::Tag::IconPicker"
        private const val TAG_MONEY_PICKER = "NewEditDebtActivity::Tag::MoneyPicker"
        private const val TAG_DATE_PICKER = "NewEditDebtActivity::Tag::DatePicker"
        private const val TAG_EXP_DATE_PICKER = "NewEditDebtActivity::Tag::ExpDatePicker"
        private const val TAG_WALLET_PICKER = "NewEditDebtActivity::Tag::WalletPicker"
        private const val TAG_PERSON_PICKER = "NewEditDebtActivity::Tag::PersonPicker"
        private const val TAG_PLACE_PICKER = "NewEditDebtActivity::Tag::PlacePicker"
    }
}
