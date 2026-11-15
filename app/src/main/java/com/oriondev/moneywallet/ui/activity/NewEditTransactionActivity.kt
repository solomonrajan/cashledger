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
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.annotation.MenuRes
import androidx.annotation.StringRes
import androidx.fragment.app.FragmentManager
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.Attachment
import com.oriondev.moneywallet.model.Category
import com.oriondev.moneywallet.model.CurrencyUnit
import com.oriondev.moneywallet.model.Event
import com.oriondev.moneywallet.model.Person
import com.oriondev.moneywallet.model.Place
import com.oriondev.moneywallet.model.Wallet
import com.oriondev.moneywallet.picker.AttachmentPicker
import com.oriondev.moneywallet.picker.CategoryPicker
import com.oriondev.moneywallet.picker.DateTimePicker
import com.oriondev.moneywallet.picker.EventPicker
import com.oriondev.moneywallet.picker.MoneyPicker
import com.oriondev.moneywallet.picker.PersonPicker
import com.oriondev.moneywallet.picker.PlacePicker
import com.oriondev.moneywallet.picker.WalletPicker
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.SQLiteDataException
import com.oriondev.moneywallet.storage.database.TransactionContentValuesBuilder
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.view.AttachmentView
import com.oriondev.moneywallet.ui.view.text.MaterialEditText
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
class NewEditTransactionActivity : NewEditItemActivity(),
    MoneyPicker.Controller,
    CategoryPicker.Controller,
    DateTimePicker.Controller,
    WalletPicker.SingleWalletController,
    EventPicker.Controller,
    PersonPicker.Controller,
    PlacePicker.Controller,
    AttachmentPicker.Controller,
    AttachmentView.Controller {

    private var mCurrencyTextView: TextView? = null
    private var mMoneyTextView: TextView? = null
    private var mDescriptionEditText: MaterialEditText? = null
    private var mCategoryEditText: MaterialEditText? = null
    private var mDateEditText: MaterialEditText? = null
    private var mTimeEditText: MaterialEditText? = null
    private var mWalletEditText: MaterialEditText? = null
    private var mEventEditText: MaterialEditText? = null
    private var mPeopleEditText: MaterialEditText? = null
    private var mPlaceEditText: MaterialEditText? = null
    private var mNoteEditText: MaterialEditText? = null
    private var mConfirmedCheckBox: CheckBox? = null
    private var mCountInTotalCheckBox: CheckBox? = null
    private var mAttachmentView: AttachmentView? = null

    private var mMoneyPicker: MoneyPicker? = null
    private var mCategoryPicker: CategoryPicker? = null
    private var mDateTimePicker: DateTimePicker? = null
    private var mWalletPicker: WalletPicker? = null
    private var mEventPicker: EventPicker? = null
    private var mPlacePicker: PlacePicker? = null
    private var mPersonPicker: PersonPicker? = null
    private var mAttachmentPicker: AttachmentPicker? = null

    private var mRules = TransactionEditorRules()

    private var mCategoryFilledByRule = false

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
        val view = inflater.inflate(R.layout.layout_panel_new_edit_transaction, parent, true)
        val descriptionEditText = view.findViewById<MaterialEditText>(R.id.description_edit_text)
        val categoryEditText = view.findViewById<MaterialEditText>(R.id.category_edit_text)
        val dateEditText = view.findViewById<MaterialEditText>(R.id.date_edit_text)
        val timeEditText = view.findViewById<MaterialEditText>(R.id.time_edit_text)
        val walletEditText = view.findViewById<MaterialEditText>(R.id.wallet_edit_text)
        val eventEditText = view.findViewById<MaterialEditText>(R.id.event_edit_text)
        val peopleEditText = view.findViewById<MaterialEditText>(R.id.people_edit_text)
        val placeEditText = view.findViewById<MaterialEditText>(R.id.place_edit_text)
        val noteEditText = view.findViewById<MaterialEditText>(R.id.note_edit_text)
        val confirmedCheckBox = view.findViewById<CheckBox>(R.id.confirmed_checkbox)
        val countInTotalCheckBox = view.findViewById<CheckBox>(R.id.count_in_total_checkbox)
        val attachmentView = view.findViewById<AttachmentView>(R.id.attachment_view)

        mDescriptionEditText = descriptionEditText
        mCategoryEditText = categoryEditText
        mDateEditText = dateEditText
        mTimeEditText = timeEditText
        mWalletEditText = walletEditText
        mEventEditText = eventEditText
        mPeopleEditText = peopleEditText
        mPlaceEditText = placeEditText
        mNoteEditText = noteEditText
        mConfirmedCheckBox = confirmedCheckBox
        mCountInTotalCheckBox = countInTotalCheckBox
        mAttachmentView = attachmentView

        categoryEditText.setTextViewMode(true)
        dateEditText.setTextViewMode(true)
        timeEditText.setTextViewMode(true)
        walletEditText.setTextViewMode(true)
        eventEditText.setTextViewMode(true)
        peopleEditText.setTextViewMode(true)
        placeEditText.setTextViewMode(true)

        dateEditText.addValidator(object : Validator {
            override fun getErrorMessage(): String {
                return getString(R.string.error_input_missing_date)
            }

            override fun isValid(charSequence: CharSequence): Boolean {
                return mDateTimePicker?.isSelected == true
            }

            override fun autoValidate(): Boolean = false
        })

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
        dateEditText.setOnClickListener {
            mDateTimePicker?.showDatePicker()
        }
        timeEditText.setOnClickListener {
            mDateTimePicker?.showTimePicker()
        }
        walletEditText.setOnClickListener {
            mWalletPicker?.showSingleWalletPicker()
        }
        eventEditText.setOnClickListener {
            mEventPicker?.showPicker(mDateTimePicker?.currentDateTime)
        }
        eventEditText.setOnCancelButtonClickListener(object : MaterialEditText.CancelButtonListener {
            override fun onCancelButtonClick(materialEditText: MaterialEditText): Boolean {
                mEventPicker?.currentEvent = null
                return false
            }
        })
        peopleEditText.setOnClickListener {
            mPersonPicker?.showPicker()
        }
        peopleEditText.setOnCancelButtonClickListener(object : MaterialEditText.CancelButtonListener {
            override fun onCancelButtonClick(materialEditText: MaterialEditText): Boolean {
                mPersonPicker?.setPeople(null)
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
        attachmentView.setController(this)
    }

    override fun onViewCreated(savedInstanceState: Bundle?) {
        super.onViewCreated(savedInstanceState)
        var money = 0L
        var category: Category? = null
        var datetime: Date? = null
        var wallet: Wallet? = null
        var event: Event? = null
        var people: Array<Person>? = null
        var place: Place? = null
        var attachments: ArrayList<Attachment>? = null

        if (savedInstanceState == null) {
            val contentResolver = contentResolver
            val duplicateId = if (getMode() == Mode.NEW_ITEM) intent.getLongExtra(DUPLICATE_ID, 0L) else 0L
            val duplicating = duplicateId > 0L && isPlainTransaction(contentResolver, duplicateId)
            if (duplicating) {
                datetime = Date()
            }
            if (getMode() == Mode.EDIT_ITEM || duplicating) {
                var uri = ContentUris.withAppendedId(
                    DataContentProvider.CONTENT_TRANSACTIONS,
                    if (duplicating) duplicateId else getItemId()
                )
                val projection = arrayOf(
                    Contract.Transaction.MONEY,
                    Contract.Transaction.DATE,
                    Contract.Transaction.DESCRIPTION,
                    Contract.Transaction.CATEGORY_ID,
                    Contract.Transaction.CATEGORY_NAME,
                    Contract.Transaction.CATEGORY_ICON,
                    Contract.Transaction.CATEGORY_TYPE,
                    Contract.Transaction.CATEGORY_TAG,
                    Contract.Transaction.CATEGORY_SHOW_REPORT,
                    Contract.Transaction.TYPE,
                    Contract.Transaction.WALLET_ID,
                    Contract.Transaction.WALLET_NAME,
                    Contract.Transaction.WALLET_ICON,
                    Contract.Transaction.WALLET_CURRENCY,
                    Contract.Transaction.PLACE_ID,
                    Contract.Transaction.PLACE_NAME,
                    Contract.Transaction.PLACE_ICON,
                    Contract.Transaction.PLACE_ADDRESS,
                    Contract.Transaction.PLACE_LATITUDE,
                    Contract.Transaction.PLACE_LONGITUDE,
                    Contract.Transaction.NOTE,
                    Contract.Transaction.EVENT_ID,
                    Contract.Transaction.EVENT_NAME,
                    Contract.Transaction.EVENT_ICON,
                    Contract.Transaction.EVENT_NOTE,
                    Contract.Transaction.EVENT_START_DATE,
                    Contract.Transaction.EVENT_END_DATE,
                    Contract.Transaction.SAVING_ID,
                    Contract.Transaction.DEBT_ID,
                    Contract.Transaction.CONFIRMED,
                    Contract.Transaction.COUNT_IN_TOTAL
                )
                val cursor = contentResolver.query(uri, projection, null, null, null)
                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        money = cursor.getLong(cursor.getColumnIndex(Contract.Transaction.MONEY))
                        if (!duplicating) {
                            datetime = DateUtils.getDateFromSQLDateTimeString(cursor.getString(cursor.getColumnIndex(Contract.Transaction.DATE)))
                        }
                        mDescriptionEditText?.setText(cursor.getString(cursor.getColumnIndex(Contract.Transaction.DESCRIPTION)))
                        category = Category(
                            cursor.getLong(cursor.getColumnIndex(Contract.Transaction.CATEGORY_ID)),
                            cursor.getString(cursor.getColumnIndex(Contract.Transaction.CATEGORY_NAME)),
                            IconLoader.parse(cursor.getString(cursor.getColumnIndex(Contract.Transaction.CATEGORY_ICON))),
                            Contract.CategoryType.fromValue(cursor.getInt(cursor.getColumnIndex(Contract.Transaction.CATEGORY_TYPE)))
                                ?: Contract.CategoryType.EXPENSE,
                            cursor.getString(cursor.getColumnIndex(Contract.Transaction.CATEGORY_TAG))
                        )
                        mRules.type = cursor.getInt(cursor.getColumnIndex(Contract.Transaction.TYPE))
                        wallet = Wallet(
                            cursor.getLong(cursor.getColumnIndex(Contract.Transaction.WALLET_ID)),
                            cursor.getString(cursor.getColumnIndex(Contract.Transaction.WALLET_NAME)),
                            IconLoader.parse(cursor.getString(cursor.getColumnIndex(Contract.Transaction.WALLET_ICON))),
                            CurrencyManager.getCurrency(cursor.getString(cursor.getColumnIndex(Contract.Transaction.WALLET_CURRENCY))),
                            0L,
                            0L
                        )
                        if (!cursor.isNull(cursor.getColumnIndex(Contract.Transaction.PLACE_ID))) {
                            val lat = if (cursor.isNull(cursor.getColumnIndex(Contract.Transaction.PLACE_LATITUDE))) {
                                null
                            } else {
                                cursor.getDouble(cursor.getColumnIndex(Contract.Transaction.PLACE_LATITUDE))
                            }
                            val lon = if (cursor.isNull(cursor.getColumnIndex(Contract.Transaction.PLACE_LONGITUDE))) {
                                null
                            } else {
                                cursor.getDouble(cursor.getColumnIndex(Contract.Transaction.PLACE_LONGITUDE))
                            }
                            place = Place(
                                cursor.getLong(cursor.getColumnIndex(Contract.Transaction.PLACE_ID)),
                                cursor.getString(cursor.getColumnIndex(Contract.Transaction.PLACE_NAME)),
                                IconLoader.parse(cursor.getString(cursor.getColumnIndex(Contract.Transaction.PLACE_ICON))),
                                cursor.getString(cursor.getColumnIndex(Contract.Transaction.PLACE_ADDRESS)),
                                lat,
                                lon
                            )
                        }
                        mNoteEditText?.setText(cursor.getString(cursor.getColumnIndex(Contract.Transaction.NOTE)))
                        if (!cursor.isNull(cursor.getColumnIndex(Contract.Transaction.EVENT_ID))) {
                            event = Event(
                                cursor.getLong(cursor.getColumnIndex(Contract.Transaction.EVENT_ID)),
                                cursor.getString(cursor.getColumnIndex(Contract.Transaction.EVENT_NAME)),
                                IconLoader.parse(cursor.getString(cursor.getColumnIndex(Contract.Transaction.EVENT_ICON))),
                                DateUtils.getDateFromSQLDateString(cursor.getString(cursor.getColumnIndex(Contract.Transaction.EVENT_START_DATE))),
                                DateUtils.getDateFromSQLDateString(cursor.getString(cursor.getColumnIndex(Contract.Transaction.EVENT_END_DATE)))
                            )
                        }
                        if (!cursor.isNull(cursor.getColumnIndex(Contract.Transaction.DEBT_ID))) {
                            mRules.debtId = cursor.getLong(cursor.getColumnIndex(Contract.Transaction.DEBT_ID))
                        }
                        if (!cursor.isNull(cursor.getColumnIndex(Contract.Transaction.SAVING_ID))) {
                            mRules.savingId = cursor.getLong(cursor.getColumnIndex(Contract.Transaction.SAVING_ID))
                        }
                        mConfirmedCheckBox?.isChecked = cursor.getInt(cursor.getColumnIndex(Contract.Transaction.CONFIRMED)) == 1
                        mCountInTotalCheckBox?.isChecked = cursor.getInt(cursor.getColumnIndex(Contract.Transaction.COUNT_IN_TOTAL)) == 1
                    }
                    cursor.close()
                }

                if (mRules.type == TYPE_TRANSFER) {
                    val transferUri = DataContentProvider.CONTENT_TRANSFERS
                    val transferProjection = arrayOf(Contract.Transfer.ID)
                    val selection = Contract.Transfer.TRANSACTION_FROM_ID + " = ? OR " +
                        Contract.Transfer.TRANSACTION_TO_ID + " = ? OR " +
                        Contract.Transfer.TRANSACTION_TAX_ID + " = ?"
                    val selectionArgs = arrayOf(
                        getItemId().toString(),
                        getItemId().toString(),
                        getItemId().toString()
                    )
                    val transferCursor = contentResolver.query(transferUri, transferProjection, selection, selectionArgs, null)
                    if (transferCursor != null) {
                        try {
                            if (transferCursor.moveToFirst()) {
                                val transferId = transferCursor.getLong(transferCursor.getColumnIndex(Contract.Transfer.ID))
                                val intent = Intent(this, NewEditTransferActivity::class.java).apply {
                                    putExtra(NewEditItemActivity.MODE, Mode.EDIT_ITEM)
                                    putExtra(NewEditItemActivity.ID, transferId)
                                }
                                startActivity(intent)
                                finish()
                                return
                            }
                        } finally {
                            transferCursor.close()
                        }
                    }
                }

                val peopleUri = Uri.withAppendedPath(uri, "people")
                val peopleProjection = arrayOf(
                    Contract.Person.ID,
                    Contract.Person.NAME,
                    Contract.Person.ICON
                )
                val peopleCursor = contentResolver.query(peopleUri, peopleProjection, null, null, null)
                if (peopleCursor != null) {
                    if (peopleCursor.moveToFirst()) {
                        val count = peopleCursor.count
                        val list = ArrayList<Person>(count)
                        var i = 0
                        while (peopleCursor.moveToPosition(i) && i < count) {
                            list.add(
                                Person(
                                    peopleCursor.getLong(peopleCursor.getColumnIndex(Contract.Person.ID)),
                                    peopleCursor.getString(peopleCursor.getColumnIndex(Contract.Person.NAME)),
                                    IconLoader.parse(peopleCursor.getString(peopleCursor.getColumnIndex(Contract.Person.ICON)))
                                )
                            )
                            i++
                        }
                        people = list.toTypedArray()
                    }
                    peopleCursor.close()
                }

                if (!duplicating) {
                    attachments = ArrayList()
                    val attachmentsUri = Uri.withAppendedPath(uri, "attachments")
                    val attachProjection = arrayOf(
                        Contract.Attachment.ID,
                        Contract.Attachment.FILE,
                        Contract.Attachment.NAME,
                        Contract.Attachment.TYPE,
                        Contract.Attachment.SIZE
                    )
                    val attachCursor = contentResolver.query(attachmentsUri, attachProjection, null, null, null)
                    if (attachCursor != null) {
                        if (attachCursor.moveToFirst()) {
                            val count = attachCursor.count
                            var i = 0
                            while (attachCursor.moveToPosition(i) && i < count) {
                                attachments.add(
                                    Attachment(
                                        attachCursor.getLong(attachCursor.getColumnIndex(Contract.Attachment.ID)),
                                        attachCursor.getString(attachCursor.getColumnIndex(Contract.Attachment.FILE)),
                                        attachCursor.getString(attachCursor.getColumnIndex(Contract.Attachment.NAME)),
                                        attachCursor.getString(attachCursor.getColumnIndex(Contract.Attachment.TYPE)),
                                        attachCursor.getLong(attachCursor.getColumnIndex(Contract.Attachment.SIZE))
                                    )
                                )
                                i++
                            }
                        }
                        attachCursor.close()
                    }
                }
            } else {
                val intent = intent
                mRules.type = intent.getIntExtra(TYPE, TYPE_STANDARD)
                if (mRules.type == TYPE_STANDARD) {
                    val projection = arrayOf(
                        Contract.Wallet.ID,
                        Contract.Wallet.NAME,
                        Contract.Wallet.ICON,
                        Contract.Wallet.CURRENCY,
                        Contract.Wallet.START_MONEY,
                        Contract.Wallet.TOTAL_MONEY
                    )
                    val currentWallet = intent.getLongExtra(WALLET_ID, PreferenceManager.getCurrentWallet())
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

                    if (intent.hasExtra(PERSON_ID)) {
                        val personUri = ContentUris.withAppendedId(DataContentProvider.CONTENT_PEOPLE, intent.getLongExtra(PERSON_ID, 0L))
                        val personProjection = arrayOf(
                            Contract.Person.ID,
                            Contract.Person.NAME,
                            Contract.Person.ICON
                        )
                        val personCursor = contentResolver.query(personUri, personProjection, null, null, null)
                        if (personCursor != null) {
                            if (personCursor.moveToFirst()) {
                                people = arrayOf(
                                    Person(
                                        personCursor.getLong(personCursor.getColumnIndex(Contract.Person.ID)),
                                        personCursor.getString(personCursor.getColumnIndex(Contract.Person.NAME)),
                                        IconLoader.parse(personCursor.getString(personCursor.getColumnIndex(Contract.Person.ICON)))
                                    )
                                )
                            }
                            personCursor.close()
                        }
                    }
                } else if (mRules.type == TYPE_TRANSFER) {
                    startActivity(Intent(this, NewEditTransferActivity::class.java))
                    finish()
                } else if (mRules.type == TYPE_DEBT) {
                    mRules.debtId = intent.getLongExtra(DEBT_ID, 0L)
                    var debtType: Contract.DebtType? = null
                    var uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_DEBTS, mRules.debtId ?: 0L)
                    var projection = arrayOf(
                        Contract.Debt.TYPE,
                        Contract.Debt.DESCRIPTION,
                        Contract.Debt.MONEY,
                        Contract.Debt.PROGRESS,
                        Contract.Debt.WALLET_ID,
                        Contract.Debt.WALLET_NAME,
                        Contract.Debt.WALLET_ICON,
                        Contract.Debt.WALLET_CURRENCY,
                        Contract.Debt.PLACE_ID,
                        Contract.Debt.PLACE_NAME,
                        Contract.Debt.PLACE_ICON,
                        Contract.Debt.PLACE_ADDRESS,
                        Contract.Debt.PLACE_LATITUDE,
                        Contract.Debt.PLACE_LONGITUDE
                    )
                    var cursor = contentResolver.query(uri, projection, null, null, null)
                    if (cursor != null) {
                        if (cursor.moveToFirst()) {
                            debtType = Contract.DebtType.fromValue(cursor.getInt(cursor.getColumnIndex(Contract.Debt.TYPE)))
                            mDescriptionEditText?.setText(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Debt.DESCRIPTION)))
                            if (TransactionEditorRules.settlesInFull(intent.getIntExtra(DEBT_ACTION, 0))) {
                                money = TransactionEditorRules.settleDebtPrefill(
                                    cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Debt.MONEY)),
                                    cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Debt.PROGRESS))
                                )
                            }
                            wallet = Wallet(
                                cursor.getLong(cursor.getColumnIndex(Contract.Debt.WALLET_ID)),
                                cursor.getString(cursor.getColumnIndex(Contract.Debt.WALLET_NAME)),
                                IconLoader.parse(cursor.getString(cursor.getColumnIndex(Contract.Debt.WALLET_ICON))),
                                CurrencyManager.getCurrency(cursor.getString(cursor.getColumnIndex(Contract.Debt.WALLET_CURRENCY))),
                                0L,
                                0L
                            )
                            if (!cursor.isNull(cursor.getColumnIndex(Contract.Debt.PLACE_ID))) {
                                val lat = if (cursor.isNull(cursor.getColumnIndex(Contract.Debt.PLACE_LATITUDE))) {
                                    null
                                } else {
                                    cursor.getDouble(cursor.getColumnIndex(Contract.Debt.PLACE_LATITUDE))
                                }
                                val lon = if (cursor.isNull(cursor.getColumnIndex(Contract.Debt.PLACE_LONGITUDE))) {
                                    null
                                } else {
                                    cursor.getDouble(cursor.getColumnIndex(Contract.Debt.PLACE_LONGITUDE))
                                }
                                place = Place(
                                    cursor.getLong(cursor.getColumnIndex(Contract.Debt.PLACE_ID)),
                                    cursor.getString(cursor.getColumnIndex(Contract.Debt.PLACE_NAME)),
                                    IconLoader.parse(cursor.getString(cursor.getColumnIndex(Contract.Debt.PLACE_ICON))),
                                    cursor.getString(cursor.getColumnIndex(Contract.Debt.PLACE_ADDRESS)),
                                    lat,
                                    lon
                                )
                            }
                        }
                        cursor.close()
                    }

                    uri = Uri.withAppendedPath(uri, "people")
                    projection = arrayOf(
                        Contract.Person.ID,
                        Contract.Person.NAME,
                        Contract.Person.ICON
                    )
                    cursor = contentResolver.query(uri, projection, null, null, null)
                    if (cursor != null) {
                        if (cursor.moveToFirst()) {
                            val count = cursor.count
                            val list = ArrayList<Person>(count)
                            var i = 0
                            while (cursor.moveToPosition(i) && i < count) {
                                list.add(
                                    Person(
                                        cursor.getLong(cursor.getColumnIndex(Contract.Person.ID)),
                                        cursor.getString(cursor.getColumnIndex(Contract.Person.NAME)),
                                        IconLoader.parse(cursor.getString(cursor.getColumnIndex(Contract.Person.ICON)))
                                    )
                                )
                                i++
                            }
                            people = list.toTypedArray()
                        }
                        cursor.close()
                    }

                    uri = DataContentProvider.CONTENT_CATEGORIES
                    projection = arrayOf(
                        Contract.Category.ID,
                        Contract.Category.NAME,
                        Contract.Category.ICON,
                        Contract.Category.TYPE,
                        Contract.Category.TAG
                    )
                    val where = Contract.Category.TAG + " = ?"
                    if (debtType == null) {
                        debtType = TransactionEditorRules.debtTypeFor(intent.getIntExtra(DEBT_ACTION, 0))
                    }
                    val debtTag = TransactionEditorRules.debtCategoryTag(debtType)
                    if (debtTag != null) {
                        val whereArgs = arrayOf(debtTag)
                        cursor = contentResolver.query(uri, projection, where, whereArgs, null)
                        if (cursor != null) {
                            if (cursor.moveToFirst()) {
                                category = Category(
                                    cursor.getLong(cursor.getColumnIndex(Contract.Category.ID)),
                                    cursor.getString(cursor.getColumnIndex(Contract.Category.NAME)),
                                    IconLoader.parse(cursor.getString(cursor.getColumnIndex(Contract.Category.ICON))),
                                    Contract.CategoryType.fromValue(cursor.getInt(cursor.getColumnIndex(Contract.Category.TYPE)))
                                        ?: Contract.CategoryType.EXPENSE,
                                    cursor.getString(cursor.getColumnIndex(Contract.Category.TAG))
                                )
                            }
                            cursor.close()
                        }
                    }
                } else if (mRules.type == TYPE_SAVING) {
                    mRules.savingId = intent.getLongExtra(SAVING_ID, 0L)
                    var startMoney = 0L
                    val savingUri = ContentUris.withAppendedId(DataContentProvider.CONTENT_SAVINGS, mRules.savingId ?: 0L)
                    val savingProjection = arrayOf(
                        Contract.Saving.START_MONEY,
                        Contract.Saving.WALLET_ID,
                        Contract.Saving.WALLET_NAME,
                        Contract.Saving.WALLET_ICON,
                        Contract.Saving.WALLET_CURRENCY
                    )
                    var cursor = contentResolver.query(savingUri, savingProjection, null, null, null)
                    if (cursor != null) {
                        if (cursor.moveToFirst()) {
                            startMoney = cursor.getLong(cursor.getColumnIndex(Contract.Saving.START_MONEY))
                            wallet = Wallet(
                                cursor.getLong(cursor.getColumnIndex(Contract.Saving.WALLET_ID)),
                                cursor.getString(cursor.getColumnIndex(Contract.Saving.WALLET_NAME)),
                                IconLoader.parse(cursor.getString(cursor.getColumnIndex(Contract.Saving.WALLET_ICON))),
                                CurrencyManager.getCurrency(cursor.getString(cursor.getColumnIndex(Contract.Saving.WALLET_CURRENCY))),
                                0L,
                                0L
                            )
                        }
                        cursor.close()
                    }
                    val uri = DataContentProvider.CONTENT_CATEGORIES
                    val catProjection = arrayOf(
                        Contract.Category.ID,
                        Contract.Category.NAME,
                        Contract.Category.ICON,
                        Contract.Category.TYPE,
                        Contract.Category.TAG
                    )
                    val selection = Contract.Category.TAG + " = ?"
                    val action = intent.getIntExtra(SAVING_ACTION, 0)
                    val savingTag = TransactionEditorRules.savingCategoryTag(action)
                    if (savingTag == null) {
                        finish()
                        return
                    }
                    if (TransactionEditorRules.completesTheSaving(action)) {
                        val lowest = readLowestSavingBalanceFrom(
                            contentResolver,
                            savingUri,
                            startMoney,
                            DateUtils.getSQLDateTimeString(Date()),
                            -1L
                        )
                        money = TransactionEditorRules.withdrawEverythingPrefill(lowest)
                        mRules.isSavingCompleted = true
                    }
                    val selectionArgs = arrayOf(savingTag)
                    cursor = contentResolver.query(uri, catProjection, selection, selectionArgs, null)
                    if (cursor != null) {
                        if (cursor.moveToFirst()) {
                            category = Category(
                                cursor.getLong(cursor.getColumnIndex(Contract.Category.ID)),
                                cursor.getString(cursor.getColumnIndex(Contract.Category.NAME)),
                                IconLoader.parse(cursor.getString(cursor.getColumnIndex(Contract.Category.ICON))),
                                Contract.CategoryType.fromValue(cursor.getInt(cursor.getColumnIndex(Contract.Category.TYPE)))
                                    ?: Contract.CategoryType.EXPENSE,
                                cursor.getString(cursor.getColumnIndex(Contract.Category.TAG))
                            )
                        }
                        cursor.close()
                    }
                } else if (mRules.type == TYPE_MODEL) {
                    val modelId = intent.getLongExtra(MODEL_ID, 0L)
                    val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSACTION_MODELS, modelId)
                    val projection = arrayOf(
                        Contract.TransactionModel.MONEY,
                        Contract.TransactionModel.DESCRIPTION,
                        Contract.TransactionModel.CATEGORY_ID,
                        Contract.TransactionModel.CATEGORY_NAME,
                        Contract.TransactionModel.CATEGORY_ICON,
                        Contract.TransactionModel.CATEGORY_TYPE,
                        Contract.TransactionModel.CATEGORY_SHOW_REPORT,
                        Contract.TransactionModel.DIRECTION,
                        Contract.TransactionModel.WALLET_ID,
                        Contract.TransactionModel.WALLET_NAME,
                        Contract.TransactionModel.WALLET_ICON,
                        Contract.TransactionModel.WALLET_CURRENCY,
                        Contract.TransactionModel.PLACE_ID,
                        Contract.TransactionModel.PLACE_NAME,
                        Contract.TransactionModel.PLACE_ICON,
                        Contract.TransactionModel.PLACE_ADDRESS,
                        Contract.TransactionModel.PLACE_LATITUDE,
                        Contract.TransactionModel.PLACE_LONGITUDE,
                        Contract.TransactionModel.NOTE,
                        Contract.TransactionModel.EVENT_ID,
                        Contract.TransactionModel.EVENT_NAME,
                        Contract.TransactionModel.EVENT_ICON,
                        Contract.TransactionModel.EVENT_START_DATE,
                        Contract.TransactionModel.EVENT_END_DATE,
                        Contract.TransactionModel.CONFIRMED,
                        Contract.TransactionModel.COUNT_IN_TOTAL
                    )
                    val cursor = contentResolver.query(uri, projection, null, null, null)
                    if (cursor != null) {
                        if (cursor.moveToFirst()) {
                            money = cursor.getLong(cursor.getColumnIndex(Contract.TransactionModel.MONEY))
                            mDescriptionEditText?.setText(cursor.getString(cursor.getColumnIndex(Contract.TransactionModel.DESCRIPTION)))
                            category = Category(
                                cursor.getLong(cursor.getColumnIndex(Contract.TransactionModel.CATEGORY_ID)),
                                cursor.getString(cursor.getColumnIndex(Contract.TransactionModel.CATEGORY_NAME)),
                                IconLoader.parse(cursor.getString(cursor.getColumnIndex(Contract.TransactionModel.CATEGORY_ICON))),
                                Contract.CategoryType.fromValue(cursor.getInt(cursor.getColumnIndex(Contract.TransactionModel.CATEGORY_TYPE)))
                                    ?: Contract.CategoryType.EXPENSE
                            )
                            wallet = Wallet(
                                cursor.getLong(cursor.getColumnIndex(Contract.TransactionModel.WALLET_ID)),
                                cursor.getString(cursor.getColumnIndex(Contract.TransactionModel.WALLET_NAME)),
                                IconLoader.parse(cursor.getString(cursor.getColumnIndex(Contract.TransactionModel.WALLET_ICON))),
                                CurrencyManager.getCurrency(cursor.getString(cursor.getColumnIndex(Contract.TransactionModel.WALLET_CURRENCY))),
                                0L,
                                0L
                            )
                            if (!cursor.isNull(cursor.getColumnIndex(Contract.TransactionModel.PLACE_ID))) {
                                val lat = if (cursor.isNull(cursor.getColumnIndex(Contract.TransactionModel.PLACE_LATITUDE))) {
                                    null
                                } else {
                                    cursor.getDouble(cursor.getColumnIndex(Contract.TransactionModel.PLACE_LATITUDE))
                                }
                                val lon = if (cursor.isNull(cursor.getColumnIndex(Contract.TransactionModel.PLACE_LONGITUDE))) {
                                    null
                                } else {
                                    cursor.getDouble(cursor.getColumnIndex(Contract.TransactionModel.PLACE_LONGITUDE))
                                }
                                place = Place(
                                    cursor.getLong(cursor.getColumnIndex(Contract.TransactionModel.PLACE_ID)),
                                    cursor.getString(cursor.getColumnIndex(Contract.TransactionModel.PLACE_NAME)),
                                    IconLoader.parse(cursor.getString(cursor.getColumnIndex(Contract.TransactionModel.PLACE_ICON))),
                                    cursor.getString(cursor.getColumnIndex(Contract.TransactionModel.PLACE_ADDRESS)),
                                    lat,
                                    lon
                                )
                            }
                            mNoteEditText?.setText(cursor.getString(cursor.getColumnIndex(Contract.TransactionModel.NOTE)))
                            if (!cursor.isNull(cursor.getColumnIndex(Contract.TransactionModel.EVENT_ID))) {
                                event = Event(
                                    cursor.getLong(cursor.getColumnIndex(Contract.TransactionModel.EVENT_ID)),
                                    cursor.getString(cursor.getColumnIndex(Contract.TransactionModel.EVENT_NAME)),
                                    IconLoader.parse(cursor.getString(cursor.getColumnIndex(Contract.TransactionModel.EVENT_ICON))),
                                    DateUtils.getDateFromSQLDateString(cursor.getString(cursor.getColumnIndex(Contract.TransactionModel.EVENT_START_DATE))),
                                    DateUtils.getDateFromSQLDateString(cursor.getString(cursor.getColumnIndex(Contract.TransactionModel.EVENT_END_DATE)))
                                )
                            }
                            mConfirmedCheckBox?.isChecked = cursor.getInt(cursor.getColumnIndex(Contract.TransactionModel.CONFIRMED)) == 1
                            mCountInTotalCheckBox?.isChecked = cursor.getInt(cursor.getColumnIndex(Contract.TransactionModel.COUNT_IN_TOTAL)) == 1
                        }
                        cursor.close()
                    }
                }
                datetime = Date()
            }
        } else {
            val restored = savedInstanceState.getSerializable(SS_RULES) as? TransactionEditorRules
            if (restored != null) {
                mRules = restored
            }
        }

        if (savedInstanceState == null && category != null) {
            mRules.isDebtPayment = TransactionEditorRules.isDebtPaymentTag(category.tag)
        }

        if (mRules.hidesCategoryField()) {
            mCategoryEditText?.visibility = View.GONE
        }
        if (mRules.hidesWalletField()) {
            mWalletEditText?.visibility = View.GONE
        }

        val fragmentManager: FragmentManager = supportFragmentManager
        mMoneyPicker = MoneyPicker.createPicker(fragmentManager, TAG_MONEY_PICKER, null, money)
        mCategoryPicker = CategoryPicker.createPicker(fragmentManager, TAG_CATEGORY_PICKER, category)
        mDateTimePicker = DateTimePicker.createPicker(fragmentManager, TAG_DATETIME_PICKER, datetime)
        mWalletPicker = WalletPicker.createPicker(fragmentManager, TAG_WALLET_PICKER, wallet)
        mEventPicker = EventPicker.createPicker(fragmentManager, TAG_EVENT_PICKER, event)
        mPersonPicker = PersonPicker.createPicker(fragmentManager, TAG_PERSON_PICKER, people)
        mPlacePicker = PlacePicker.createPicker(fragmentManager, TAG_PLACE_PICKER, place)
        mAttachmentPicker = AttachmentPicker.createPicker(fragmentManager, TAG_ATTACHMENT_PICKER, attachments)

        mDescriptionEditText?.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                fillCategoryFromRules()
            }
        }

        if (savedInstanceState == null) {
            fillFieldsFromIntent(intent)
            if (getMode() == Mode.NEW_ITEM && intent.getBooleanExtra(AUTO_OPEN_CALCULATOR, false)) {
                supportFragmentManager.executePendingTransactions()
                mMoneyPicker?.showPicker()
            }
        }
    }

    private fun fillCategoryFromRules() {
        if (getMode() != Mode.NEW_ITEM || mRules.hidesCategoryField()) {
            return
        }
        if (mCategoryPicker?.isSelected == true && !mCategoryFilledByRule) {
            return
        }
        val description = mDescriptionEditText?.getTextAsString()
        val category = if (TextUtils.isEmpty(description)) {
            null
        } else {
            readCategoryNamedBy(contentResolver, description ?: "")
        }
        if (category == null && !mCategoryFilledByRule) {
            return
        }
        mCategoryPicker?.setCategory(category)
        mCategoryFilledByRule = category != null
    }

    @Suppress("DEPRECATION")
    private fun fillFieldsFromIntent(intent: Intent) {
        if (Intent.ACTION_SEND == intent.action) {
            val fileUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (fileUri != null) {
                mAttachmentPicker?.addFileFromUri(fileUri)
            }
        }
        if (Intent.ACTION_SEND_MULTIPLE == intent.action) {
            val fileUris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            if (fileUris != null) {
                for (uri in fileUris) {
                    if (uri != null) {
                        mAttachmentPicker?.addFileFromUri(uri)
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable(SS_RULES, mRules)
    }

    @StringRes
    override fun getActivityTileRes(mode: Mode): Int {
        return when (mode) {
            Mode.NEW_ITEM -> R.string.title_activity_new_transaction
            Mode.EDIT_ITEM -> R.string.title_activity_edit_transaction
        }
    }

    @MenuRes
    override fun onInflateMenu(): Int = R.menu.menu_new_edit_item_with_attachment

    override fun onMenuItemClick(item: MenuItem): Boolean {
        return if (item.itemId == R.id.action_attach_file) {
            mAttachmentPicker?.showPicker()
            false
        } else {
            super.onMenuItemClick(item)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        mAttachmentPicker?.cleanUp(true)
    }

    private fun validate(): Boolean {
        if (mDateEditText?.validate() == true &&
            mCategoryEditText?.validate() == true &&
            mWalletEditText?.validate() == true
        ) {
            if (mAttachmentPicker?.areAllAttachmentsReady() == true) {
                return validateSavingWithdraw()
            }
        }
        return false
    }

    private fun readLowestSavingBalanceFrom(
        contentResolver: ContentResolver,
        savingUri: Uri,
        startMoney: Long,
        from: String,
        excludedId: Long
    ): Long? {
        val projection = arrayOf(
            Contract.Transaction.ID,
            Contract.Transaction.DATE,
            Contract.Transaction.MONEY,
            Contract.Transaction.DIRECTION,
            Contract.Transaction.CONFIRMED
        )
        val selection = Contract.Transaction.CATEGORY_TAG + " IN (?, ?)"
        val selectionArgs = arrayOf(
            Contract.CategoryTag.SAVING_DEPOSIT,
            Contract.CategoryTag.SAVING_WITHDRAW
        )
        val cursor = contentResolver.query(
            Uri.withAppendedPath(savingUri, "transactions"),
            projection,
            selection,
            selectionArgs,
            Contract.Transaction.DATE + " ASC"
        ) ?: return null
        val rows = ArrayList<TransactionEditorRules.SavingRow>()
        while (cursor.moveToNext()) {
            rows.add(
                TransactionEditorRules.SavingRow(
                    cursor.getLong(cursor.getColumnIndex(Contract.Transaction.ID)),
                    cursor.getString(cursor.getColumnIndex(Contract.Transaction.DATE)),
                    cursor.getLong(cursor.getColumnIndex(Contract.Transaction.MONEY)),
                    cursor.getInt(cursor.getColumnIndex(Contract.Transaction.DIRECTION)),
                    cursor.getInt(cursor.getColumnIndex(Contract.Transaction.CONFIRMED)) == 1
                )
            )
        }
        cursor.close()
        return TransactionEditorRules.lowestSavingBalanceFrom(startMoney, rows, excludedId, from)
    }

    private fun isStoredWithdrawalKeptOrLowered(
        contentResolver: ContentResolver,
        money: Long,
        date: String
    ): Boolean {
        if (getMode() != Mode.EDIT_ITEM) {
            return false
        }
        val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSACTIONS, getItemId())
        val projection = arrayOf(
            Contract.Transaction.MONEY,
            Contract.Transaction.DATE
        )
        val cursor = contentResolver.query(uri, projection, null, null, null)
        var unchangedOrSmaller = false
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                unchangedOrSmaller = TransactionEditorRules.isStoredWithdrawalKeptOrLowered(
                    money,
                    date,
                    cursor.getLong(cursor.getColumnIndex(Contract.Transaction.MONEY)),
                    cursor.getString(cursor.getColumnIndex(Contract.Transaction.DATE))
                )
            }
            cursor.close()
        }
        return unchangedOrSmaller
    }

    private fun validateSavingWithdraw(): Boolean {
        val savingId = mRules.savingId ?: return true
        val category = mCategoryPicker?.currentCategory
        if (category == null || Contract.CategoryTag.SAVING_WITHDRAW != category.tag) {
            return true
        }
        val contentResolver = contentResolver
        val savingUri = ContentUris.withAppendedId(DataContentProvider.CONTENT_SAVINGS, savingId)
        val projection = arrayOf(
            Contract.Saving.START_MONEY,
            Contract.Saving.WALLET_CURRENCY
        )
        var startMoney = 0L
        var currencyIso: String? = null
        val cursor = contentResolver.query(savingUri, projection, null, null, null)
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                startMoney = cursor.getLong(cursor.getColumnIndex(Contract.Saving.START_MONEY))
                currencyIso = cursor.getString(cursor.getColumnIndex(Contract.Saving.WALLET_CURRENCY))
            }
            cursor.close()
        }
        val currency = if (currencyIso != null) CurrencyManager.getCurrency(currencyIso) else null
        val walletCurrency = mWalletPicker?.currentWallet?.currency
        if (!TransactionEditorRules.ceilingApplies(currency?.iso, walletCurrency?.iso)) {
            return true
        }
        val money = mMoneyPicker?.currentMoney ?: 0L
        val dateTime = mDateTimePicker?.currentDateTime
        val date = if (dateTime != null) DateUtils.getSQLDateTimeString(dateTime) else ""
        if (isStoredWithdrawalKeptOrLowered(contentResolver, money, date)) {
            return true
        }
        val lowest = readLowestSavingBalanceFrom(contentResolver, savingUri, startMoney, date, getItemId())
            ?: return true
        val limit = TransactionEditorRules.withdrawLimit(lowest)
        if (TransactionEditorRules.isWithinLimit(money, limit)) {
            return true
        }
        val message = getString(
            R.string.error_saving_withdraw_over_balance,
            mMoneyFormatter.getNotTintedString(currency, limit)
        )
        ThemedDialog.buildMaterialDialog(this)
            .setTitle(R.string.title_error)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
        return false
    }

    override fun onSaveChanges(mode: Mode) {
        fillCategoryFromRules()
        if (validate()) {
            val category = mCategoryPicker?.currentCategory ?: return
            val wallet = mWalletPicker?.currentWallet ?: return
            val dateTime = mDateTimePicker?.currentDateTime
            val dateStr = if (dateTime != null) DateUtils.getSQLDateTimeString(dateTime) else ""
            val contentValues = TransactionContentValuesBuilder()
                .money(mMoneyPicker?.currentMoney ?: 0L)
                .date(dateStr)
                .description(mDescriptionEditText?.getTextAsString() ?: "")
                .categoryId(category.id)
                .direction(category.direction)
                .type(mRules.type)
                .walletId(wallet.id)
                .placeId(if (mPlacePicker?.isSelected == true) mPlacePicker?.currentPlace?.id else null)
                .note(mNoteEditText?.getTextAsString() ?: "")
                .eventId(if (mEventPicker?.isSelected == true) mEventPicker?.currentEvent?.id else null)
                .savingId(mRules.savingId)
                .debtId(mRules.debtId)
                .confirmed(mConfirmedCheckBox?.isChecked == true)
                .countInTotal(mCountInTotalCheckBox?.isChecked == true)
                .peopleIds(Contract.getObjectIds(mPersonPicker?.currentPeople))
                .attachmentIds(Contract.getObjectIds(mAttachmentPicker?.currentAttachments))
                .build()
            val contentResolver = contentResolver
            try {
                when (mode) {
                    Mode.NEW_ITEM -> {
                        contentResolver.insert(DataContentProvider.CONTENT_TRANSACTIONS, contentValues)
                        val savingId = mRules.savingId
                        if (savingId != null && mRules.isSavingCompleted) {
                            setSavingCompleted(contentResolver, savingId)
                        }
                    }
                    Mode.EDIT_ITEM -> {
                        val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSACTIONS, getItemId())
                        contentResolver.update(uri, contentValues, null, null)
                    }
                }
            } catch (e: SQLiteDataException) {
                if (e.errorCode != Contract.ErrorCode.WALLETS_NOT_CONSISTENT) {
                    throw e
                }
                ThemedDialog.buildMaterialDialog(this)
                    .setTitle(R.string.title_error)
                    .setMessage(R.string.error_debt_wallet_currency_not_consistent)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                return
            }
            mAttachmentPicker?.cleanUp(false)
            setResult(Activity.RESULT_OK)
            finishSaveDatedAt(mDateTimePicker?.currentDateTime)
        }
    }

    private fun setSavingCompleted(contentResolver: ContentResolver, savingId: Long) {
        val contentValues = ContentValues().apply {
            put(Contract.Saving.COMPLETE, true)
        }
        val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_SAVINGS, savingId)
        contentResolver.update(uri, contentValues, null, null)
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
        mCategoryFilledByRule = false
        if (category != null) {
            mCategoryEditText?.setText(category.name)
        } else {
            mCategoryEditText?.setText(null)
        }
    }

    override fun onDateTimeChanged(tag: String?, date: Date?) {
        if (date != null) {
            mDateEditText?.let { DateFormatter.applyDate(it, date) }
            mTimeEditText?.let { DateFormatter.applyTime(it, date) }
        } else {
            mDateEditText?.setText(null)
            mTimeEditText?.setText(null)
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

    override fun onEventChanged(tag: String?, event: Event?) {
        if (event != null) {
            mEventEditText?.setText(event.name)
        } else {
            mEventEditText?.setText(null)
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

    override fun onAttachmentListChanged(attachments: List<Attachment>) {
        mAttachmentView?.visibility = if (attachments.isEmpty()) View.GONE else View.VISIBLE
        mAttachmentView?.setAttachments(attachments)
    }

    override fun onAttachmentClick(attachment: Attachment) {
        Attachment.openAttachment(this, attachment)
    }

    override fun onAttachmentDelete(attachment: Attachment) {
        mAttachmentPicker?.remove(attachment)
    }

    companion object {
        const val TYPE = "NewEditTransactionActivity::Type"
        const val DEBT_ID = "NewEditTransactionActivity::DebtId"
        const val DEBT_ACTION = "NewEditTransactionActivity::DebtAction"
        const val SAVING_ID = "NewEditTransactionActivity::SavingId"
        const val SAVING_ACTION = "NewEditTransactionActivity::SavingAction"
        const val AUTO_OPEN_CALCULATOR = "auto_open_calculator"
        const val PERSON_ID = "NewEditTransactionActivity::PersonId"
        const val MODEL_ID = "NewEditTransactionActivity::ModelId"
        const val DUPLICATE_ID = "NewEditTransactionActivity::DuplicateId"
        const val WALLET_ID = "NewEditTransactionActivity::WalletId"

        const val TYPE_STANDARD = TransactionEditorRules.TYPE_STANDARD
        const val TYPE_TRANSFER = TransactionEditorRules.TYPE_TRANSFER
        const val TYPE_DEBT = TransactionEditorRules.TYPE_DEBT
        const val TYPE_SAVING = TransactionEditorRules.TYPE_SAVING
        const val TYPE_MODEL = TransactionEditorRules.TYPE_MODEL

        const val DEBT_PAY = TransactionEditorRules.DEBT_PAY
        const val DEBT_RECEIVE = TransactionEditorRules.DEBT_RECEIVE
        const val DEBT_PAY_IN_FULL = TransactionEditorRules.DEBT_PAY_IN_FULL
        const val DEBT_RECEIVE_IN_FULL = TransactionEditorRules.DEBT_RECEIVE_IN_FULL

        const val SAVING_DEPOSIT = TransactionEditorRules.SAVING_DEPOSIT
        const val SAVING_WITHDRAW = TransactionEditorRules.SAVING_WITHDRAW
        const val SAVING_WITHDRAW_EVERYTHING = TransactionEditorRules.SAVING_WITHDRAW_EVERYTHING

        private const val TAG_MONEY_PICKER = "NewEditTransactionActivity::Tag::MoneyPicker"
        private const val TAG_CATEGORY_PICKER = "NewEditTransactionActivity::Tag::CategoryPicker"
        private const val TAG_DATETIME_PICKER = "NewEditTransactionActivity::Tag::DateTimePicker"
        private const val TAG_WALLET_PICKER = "NewEditTransactionActivity::Tag::WalletPicker"
        private const val TAG_EVENT_PICKER = "NewEditTransactionActivity::Tag::EventPicker"
        private const val TAG_PLACE_PICKER = "NewEditTransactionActivity::Tag::PlacePicker"
        private const val TAG_PERSON_PICKER = "NewEditTransactionActivity::Tag::PersonPicker"
        private const val TAG_ATTACHMENT_PICKER = "NewEditTransactionActivity::Tag::AttachmentPicker"

        private const val SS_RULES = "NewEditTransactionActivity::SavedState::Rules"

        private fun isPlainTransaction(contentResolver: ContentResolver, transactionId: Long): Boolean {
            val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSACTIONS, transactionId)
            val cursor = contentResolver.query(uri, arrayOf(Contract.Transaction.TYPE), null, null, null)
            var plain = false
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    plain = cursor.getInt(cursor.getColumnIndex(Contract.Transaction.TYPE)) == TYPE_STANDARD
                }
                cursor.close()
            }
            return plain
        }

        private fun readCategoryNamedBy(contentResolver: ContentResolver, description: String): Category? {
            val matchUri = DataContentProvider.CONTENT_CATEGORY_RULES.buildUpon()
                .appendPath("match")
                .appendPath(description)
                .build()
            var categoryId: Long? = null
            var cursor = contentResolver.query(
                matchUri,
                arrayOf(Contract.CategoryRule.CATEGORY_ID),
                null,
                null,
                null
            )
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    categoryId = cursor.getLong(cursor.getColumnIndexOrThrow(Contract.CategoryRule.CATEGORY_ID))
                }
                cursor.close()
            }
            if (categoryId == null) {
                return null
            }
            val categoryUri = ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORIES, categoryId)
            val projection = arrayOf(
                Contract.Category.ID,
                Contract.Category.NAME,
                Contract.Category.ICON,
                Contract.Category.TYPE,
                Contract.Category.TAG
            )
            var category: Category? = null
            cursor = contentResolver.query(categoryUri, projection, null, null, null)
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    category = Category(
                        cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Category.ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(Contract.Category.NAME)),
                        IconLoader.parse(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Category.ICON))),
                        Contract.CategoryType.fromValue(cursor.getInt(cursor.getColumnIndexOrThrow(Contract.Category.TYPE)))
                            ?: Contract.CategoryType.EXPENSE,
                        cursor.getString(cursor.getColumnIndexOrThrow(Contract.Category.TAG))
                    )
                }
                cursor.close()
            }
            return category
        }

        @JvmStatic
        fun insertTransactionFromModel(context: Context, modelId: Long): Uri? {
            val contentResolver = context.contentResolver
            val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSACTION_MODELS, modelId)
            val projection = arrayOf(
                Contract.TransactionModel.MONEY,
                Contract.TransactionModel.DESCRIPTION,
                Contract.TransactionModel.CATEGORY_ID,
                Contract.TransactionModel.DIRECTION,
                Contract.TransactionModel.WALLET_ID,
                Contract.TransactionModel.PLACE_ID,
                Contract.TransactionModel.NOTE,
                Contract.TransactionModel.EVENT_ID,
                Contract.TransactionModel.CONFIRMED,
                Contract.TransactionModel.COUNT_IN_TOTAL
            )
            val cursor = contentResolver.query(uri, projection, null, null, null)
            if (cursor != null) {
                var resultUri: Uri? = null
                if (cursor.moveToFirst()) {
                    val placeId = if (cursor.isNull(cursor.getColumnIndex(Contract.TransactionModel.PLACE_ID))) {
                        null
                    } else {
                        cursor.getLong(cursor.getColumnIndex(Contract.TransactionModel.PLACE_ID))
                    }
                    val eventId = if (cursor.isNull(cursor.getColumnIndex(Contract.TransactionModel.EVENT_ID))) {
                        null
                    } else {
                        cursor.getLong(cursor.getColumnIndex(Contract.TransactionModel.EVENT_ID))
                    }
                    val contentValues = TransactionContentValuesBuilder()
                        .money(cursor.getLong(cursor.getColumnIndex(Contract.TransactionModel.MONEY)))
                        .date(DateUtils.getSQLDateTimeString(Date()))
                        .description(cursor.getString(cursor.getColumnIndex(Contract.TransactionModel.DESCRIPTION)))
                        .categoryId(cursor.getLong(cursor.getColumnIndex(Contract.TransactionModel.CATEGORY_ID)))
                        .direction(cursor.getInt(cursor.getColumnIndex(Contract.TransactionModel.DIRECTION)))
                        .type(Contract.TransactionType.STANDARD)
                        .walletId(cursor.getLong(cursor.getColumnIndex(Contract.TransactionModel.WALLET_ID)))
                        .placeId(placeId)
                        .note(cursor.getString(cursor.getColumnIndex(Contract.TransactionModel.NOTE)))
                        .eventId(eventId)
                        .confirmed(cursor.getInt(cursor.getColumnIndex(Contract.TransactionModel.CONFIRMED)))
                        .countInTotal(cursor.getInt(cursor.getColumnIndex(Contract.TransactionModel.COUNT_IN_TOTAL)))
                        .build()
                    resultUri = contentResolver.insert(DataContentProvider.CONTENT_TRANSACTIONS, contentValues)
                }
                cursor.close()
                return resultUri
            }
            return null
        }
    }
}
