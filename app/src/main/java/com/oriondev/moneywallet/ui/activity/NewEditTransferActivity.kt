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
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
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
import com.oriondev.moneywallet.model.CurrencyUnit
import com.oriondev.moneywallet.model.Event
import com.oriondev.moneywallet.model.Person
import com.oriondev.moneywallet.model.Place
import com.oriondev.moneywallet.model.Wallet
import com.oriondev.moneywallet.picker.AttachmentPicker
import com.oriondev.moneywallet.picker.DateTimePicker
import com.oriondev.moneywallet.picker.EventPicker
import com.oriondev.moneywallet.picker.MoneyPicker
import com.oriondev.moneywallet.picker.PersonPicker
import com.oriondev.moneywallet.picker.PlacePicker
import com.oriondev.moneywallet.picker.WalletPicker
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.TransferContentValuesBuilder
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
 * Created by andrea on 16/03/18.
 */
class NewEditTransferActivity : NewEditItemActivity(),
    MoneyPicker.Controller,
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
    private var mDateEditText: MaterialEditText? = null
    private var mTimeEditText: MaterialEditText? = null
    private var mWalletFromEditText: MaterialEditText? = null
    private var mWalletToEditText: MaterialEditText? = null
    private var mTaxEditText: MaterialEditText? = null
    private var mEventEditText: MaterialEditText? = null
    private var mPeopleEditText: MaterialEditText? = null
    private var mPlaceEditText: MaterialEditText? = null
    private var mNoteEditText: MaterialEditText? = null
    private var mConfirmedCheckBox: CheckBox? = null
    private var mCountInTotalCheckBox: CheckBox? = null
    private var mAttachmentView: AttachmentView? = null

    private var mMoneyPicker: MoneyPicker? = null
    private var mDateTimePicker: DateTimePicker? = null
    private var mWalletFromPicker: WalletPicker? = null
    private var mWalletToPicker: WalletPicker? = null
    private var mTaxPicker: MoneyPicker? = null
    private var mEventPicker: EventPicker? = null
    private var mPlacePicker: PlacePicker? = null
    private var mPersonPicker: PersonPicker? = null
    private var mAttachmentPicker: AttachmentPicker? = null

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
        val view = inflater.inflate(R.layout.layout_panel_new_edit_transfer, parent, true)
        val descriptionEditText = view.findViewById<MaterialEditText>(R.id.description_edit_text)
        val dateEditText = view.findViewById<MaterialEditText>(R.id.date_edit_text)
        val timeEditText = view.findViewById<MaterialEditText>(R.id.time_edit_text)
        val walletFromEditText = view.findViewById<MaterialEditText>(R.id.wallet_from_edit_text)
        val walletToEditText = view.findViewById<MaterialEditText>(R.id.wallet_to_edit_text)
        val taxEditText = view.findViewById<MaterialEditText>(R.id.money_tax_edit_text)
        val eventEditText = view.findViewById<MaterialEditText>(R.id.event_edit_text)
        val peopleEditText = view.findViewById<MaterialEditText>(R.id.people_edit_text)
        val placeEditText = view.findViewById<MaterialEditText>(R.id.place_edit_text)
        val noteEditText = view.findViewById<MaterialEditText>(R.id.note_edit_text)
        val confirmedCheckBox = view.findViewById<CheckBox>(R.id.confirmed_checkbox)
        val countInTotalCheckBox = view.findViewById<CheckBox>(R.id.count_in_total_checkbox)
        val attachmentView = view.findViewById<AttachmentView>(R.id.attachment_view)

        mDescriptionEditText = descriptionEditText
        mDateEditText = dateEditText
        mTimeEditText = timeEditText
        mWalletFromEditText = walletFromEditText
        mWalletToEditText = walletToEditText
        mTaxEditText = taxEditText
        mEventEditText = eventEditText
        mPeopleEditText = peopleEditText
        mPlaceEditText = placeEditText
        mNoteEditText = noteEditText
        mConfirmedCheckBox = confirmedCheckBox
        mCountInTotalCheckBox = countInTotalCheckBox
        mAttachmentView = attachmentView

        dateEditText.setTextViewMode(true)
        timeEditText.setTextViewMode(true)
        walletFromEditText.setTextViewMode(true)
        walletToEditText.setTextViewMode(true)
        taxEditText.setTextViewMode(true)
        eventEditText.setTextViewMode(true)
        peopleEditText.setTextViewMode(true)
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

        dateEditText.setOnClickListener {
            mDateTimePicker?.showDatePicker()
        }
        timeEditText.setOnClickListener {
            mDateTimePicker?.showTimePicker()
        }
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
        var moneyFrom = 0L
        var tax = 0L
        var datetime: Date? = null
        var walletFrom: Wallet? = null
        var walletTo: Wallet? = null
        var event: Event? = null
        var place: Place? = null
        var people: Array<Person>? = null
        var attachments: ArrayList<Attachment>? = null

        if (savedInstanceState == null) {
            val contentResolver = contentResolver
            if (getMode() == Mode.EDIT_ITEM) {
                var uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSFERS, getItemId())
                val projection = arrayOf(
                    Contract.Transfer.DESCRIPTION,
                    Contract.Transfer.DATE,
                    Contract.Transfer.TRANSACTION_FROM_WALLET_ID,
                    Contract.Transfer.TRANSACTION_FROM_WALLET_NAME,
                    Contract.Transfer.TRANSACTION_FROM_WALLET_ICON,
                    Contract.Transfer.TRANSACTION_FROM_WALLET_CURRENCY,
                    Contract.Transfer.TRANSACTION_TO_WALLET_ID,
                    Contract.Transfer.TRANSACTION_TO_WALLET_NAME,
                    Contract.Transfer.TRANSACTION_TO_WALLET_ICON,
                    Contract.Transfer.TRANSACTION_TO_WALLET_CURRENCY,
                    Contract.Transfer.TRANSACTION_FROM_MONEY,
                    Contract.Transfer.TRANSACTION_TO_MONEY,
                    Contract.Transfer.TRANSACTION_TAX_MONEY,
                    Contract.Transfer.NOTE,
                    Contract.Transfer.EVENT_ID,
                    Contract.Transfer.EVENT_NAME,
                    Contract.Transfer.EVENT_ICON,
                    Contract.Transfer.EVENT_START_DATE,
                    Contract.Transfer.EVENT_END_DATE,
                    Contract.Transfer.PLACE_ID,
                    Contract.Transfer.PLACE_NAME,
                    Contract.Transfer.PLACE_ICON,
                    Contract.Transfer.PLACE_ADDRESS,
                    Contract.Transfer.PLACE_LATITUDE,
                    Contract.Transfer.PLACE_LONGITUDE,
                    Contract.Transfer.CONFIRMED,
                    Contract.Transfer.COUNT_IN_TOTAL
                )
                val cursor = contentResolver.query(uri, projection, null, null, null)
                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        mDescriptionEditText?.setText(cursor.getString(cursor.getColumnIndex(Contract.Transfer.DESCRIPTION)))
                        datetime = DateUtils.getDateFromSQLDateTimeString(cursor.getString(cursor.getColumnIndex(Contract.Transfer.DATE)))
                        walletFrom = Wallet(
                            cursor.getLong(cursor.getColumnIndex(Contract.Transfer.TRANSACTION_FROM_WALLET_ID)),
                            cursor.getString(cursor.getColumnIndex(Contract.Transfer.TRANSACTION_FROM_WALLET_NAME)),
                            IconLoader.parse(cursor.getString(cursor.getColumnIndex(Contract.Transfer.TRANSACTION_FROM_WALLET_ICON))),
                            CurrencyManager.getCurrency(cursor.getString(cursor.getColumnIndex(Contract.Transfer.TRANSACTION_FROM_WALLET_CURRENCY))),
                            0L,
                            0L
                        )
                        walletTo = Wallet(
                            cursor.getLong(cursor.getColumnIndex(Contract.Transfer.TRANSACTION_TO_WALLET_ID)),
                            cursor.getString(cursor.getColumnIndex(Contract.Transfer.TRANSACTION_TO_WALLET_NAME)),
                            IconLoader.parse(cursor.getString(cursor.getColumnIndex(Contract.Transfer.TRANSACTION_TO_WALLET_ICON))),
                            CurrencyManager.getCurrency(cursor.getString(cursor.getColumnIndex(Contract.Transfer.TRANSACTION_TO_WALLET_CURRENCY))),
                            0L,
                            0L
                        )
                        moneyFrom = cursor.getLong(cursor.getColumnIndex(Contract.Transfer.TRANSACTION_FROM_MONEY))
                        tax = cursor.getLong(cursor.getColumnIndex(Contract.Transfer.TRANSACTION_TAX_MONEY))
                        if (!cursor.isNull(cursor.getColumnIndex(Contract.Transfer.PLACE_ID))) {
                            val lat = if (cursor.isNull(cursor.getColumnIndex(Contract.Transfer.PLACE_LATITUDE))) {
                                null
                            } else {
                                cursor.getDouble(cursor.getColumnIndex(Contract.Transfer.PLACE_LATITUDE))
                            }
                            val lon = if (cursor.isNull(cursor.getColumnIndex(Contract.Transfer.PLACE_LONGITUDE))) {
                                null
                            } else {
                                cursor.getDouble(cursor.getColumnIndex(Contract.Transfer.PLACE_LONGITUDE))
                            }
                            place = Place(
                                cursor.getLong(cursor.getColumnIndex(Contract.Transfer.PLACE_ID)),
                                cursor.getString(cursor.getColumnIndex(Contract.Transfer.PLACE_NAME)),
                                IconLoader.parse(cursor.getString(cursor.getColumnIndex(Contract.Transfer.PLACE_ICON))),
                                cursor.getString(cursor.getColumnIndex(Contract.Transfer.PLACE_ADDRESS)),
                                lat,
                                lon
                            )
                        }
                        mNoteEditText?.setText(cursor.getString(cursor.getColumnIndex(Contract.Transfer.NOTE)))
                        if (!cursor.isNull(cursor.getColumnIndex(Contract.Transfer.EVENT_ID))) {
                            event = Event(
                                cursor.getLong(cursor.getColumnIndex(Contract.Transfer.EVENT_ID)),
                                cursor.getString(cursor.getColumnIndex(Contract.Transfer.EVENT_NAME)),
                                IconLoader.parse(cursor.getString(cursor.getColumnIndex(Contract.Transfer.EVENT_ICON))),
                                DateUtils.getDateFromSQLDateString(cursor.getString(cursor.getColumnIndex(Contract.Transfer.EVENT_START_DATE))),
                                DateUtils.getDateFromSQLDateString(cursor.getString(cursor.getColumnIndex(Contract.Transfer.EVENT_END_DATE)))
                            )
                        }
                        mConfirmedCheckBox?.isChecked = cursor.getInt(cursor.getColumnIndex(Contract.Transfer.CONFIRMED)) == 1
                        mCountInTotalCheckBox?.isChecked = cursor.getInt(cursor.getColumnIndex(Contract.Transfer.COUNT_IN_TOTAL)) == 1
                    }
                    cursor.close()
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
            } else {
                val intent = intent
                if (intent.getIntExtra(TYPE, TYPE_STANDARD) == TYPE_MODEL) {
                    val modelId = intent.getLongExtra(MODEL_ID, 0L)
                    val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSFER_MODELS, modelId)
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
                datetime = Date()
            }
        }

        val fragmentManager: FragmentManager = supportFragmentManager
        mMoneyPicker = MoneyPicker.createPicker(fragmentManager, TAG_MONEY_PICKER, null, moneyFrom)
        mTaxPicker = MoneyPicker.createPicker(fragmentManager, TAG_TAX_PICKER, null, tax)
        mDateTimePicker = DateTimePicker.createPicker(fragmentManager, TAG_DATETIME_PICKER, datetime)
        mWalletFromPicker = WalletPicker.createPicker(fragmentManager, TAG_WALLET_FROM_PICKER, walletFrom)
        mWalletToPicker = WalletPicker.createPicker(fragmentManager, TAG_WALLET_TO_PICKER, walletTo)
        mEventPicker = EventPicker.createPicker(fragmentManager, TAG_EVENT_PICKER, event)
        mPersonPicker = PersonPicker.createPicker(fragmentManager, TAG_PERSON_PICKER, people)
        mPlacePicker = PlacePicker.createPicker(fragmentManager, TAG_PLACE_PICKER, place)
        mAttachmentPicker = AttachmentPicker.createPicker(fragmentManager, TAG_ATTACHMENT_PICKER, attachments)
    }

    @StringRes
    override fun getActivityTileRes(mode: Mode): Int {
        return when (mode) {
            Mode.NEW_ITEM -> R.string.title_activity_new_transfer
            Mode.EDIT_ITEM -> R.string.title_activity_edit_transfer
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
        if (mWalletFromEditText?.validate() == true && mWalletToEditText?.validate() == true) {
            return mAttachmentPicker?.areAllAttachmentsReady() == true
        }
        return false
    }

    override fun onSaveChanges(mode: Mode) {
        if (validate()) {
            val walletFrom = mWalletFromPicker?.currentWallet ?: return
            val walletTo = mWalletToPicker?.currentWallet ?: return
            val dateTime = mDateTimePicker?.currentDateTime
            val dateStr = if (dateTime != null) DateUtils.getSQLDateTimeString(dateTime) else ""
            val contentValues = TransferContentValuesBuilder()
                .description(mDescriptionEditText?.getTextAsString() ?: "")
                .date(dateStr)
                .fromWalletId(walletFrom.id)
                .toWalletId(walletTo.id)
                .taxWalletId(walletFrom.id)
                .fromMoney(mMoneyPicker?.currentMoney ?: 0L)
                .toMoney(mMoneyPicker?.currentMoney ?: 0L)
                .taxMoney(mTaxPicker?.currentMoney ?: 0L)
                .note(mNoteEditText?.getTextAsString() ?: "")
                .placeId(if (mPlacePicker?.isSelected == true) mPlacePicker?.currentPlace?.id else null)
                .eventId(if (mEventPicker?.isSelected == true) mEventPicker?.currentEvent?.id else null)
                .confirmed(mConfirmedCheckBox?.isChecked == true)
                .countInTotal(mCountInTotalCheckBox?.isChecked == true)
                .peopleIds(Contract.getObjectIds(mPersonPicker?.currentPeople))
                .attachmentIds(Contract.getObjectIds(mAttachmentPicker?.currentAttachments))
                .build()
            val contentResolver = contentResolver
            when (mode) {
                Mode.NEW_ITEM -> {
                    contentResolver.insert(DataContentProvider.CONTENT_TRANSFERS, contentValues)
                }
                Mode.EDIT_ITEM -> {
                    val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSFERS, getItemId())
                    contentResolver.update(uri, contentValues, null, null)
                }
            }
            mAttachmentPicker?.cleanUp(false)
            setResult(Activity.RESULT_OK)
            finishSaveDatedAt(mDateTimePicker?.currentDateTime)
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
        try {
            startActivity(attachment.getActionViewIntent(this))
        } catch (e: ActivityNotFoundException) {
            ThemedDialog.buildMaterialDialog(this)
                .setTitle(R.string.title_error)
                .setMessage(R.string.message_error_activity_not_found)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    override fun onAttachmentDelete(attachment: Attachment) {
        mAttachmentPicker?.remove(attachment)
    }

    companion object {
        const val TYPE = "NewEditTransferActivity::Type"
        const val MODEL_ID = "NewEditTransferActivity::ModelId"

        const val TYPE_STANDARD = 0
        const val TYPE_MODEL = 1

        private const val TAG_MONEY_PICKER = "NewEditTransferModelActivity::Tag::MoneyPicker"
        private const val TAG_DATETIME_PICKER = "NewEditTransferModelActivity::Tag::DateTimePicker"
        private const val TAG_WALLET_FROM_PICKER = "NewEditTransferModelActivity::Tag::WalletFromPicker"
        private const val TAG_WALLET_TO_PICKER = "NewEditTransferModelActivity::Tag::WalletToPicker"
        private const val TAG_TAX_PICKER = "NewEditTransferModelActivity::Tag::TaxPicker"
        private const val TAG_EVENT_PICKER = "NewEditTransferModelActivity::Tag::EventPicker"
        private const val TAG_PLACE_PICKER = "NewEditTransferModelActivity::Tag::PlacePicker"
        private const val TAG_PERSON_PICKER = "NewEditTransferModelActivity::Tag::PersonPicker"
        private const val TAG_ATTACHMENT_PICKER = "NewEditTransferModelActivity::Tag::AttachmentPicker"

        @JvmStatic
        fun insertTransferFromModel(context: Context, modelId: Long): Uri? {
            val contentResolver = context.contentResolver
            val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSFER_MODELS, modelId)
            val projection = arrayOf(
                Contract.TransferModel.DESCRIPTION,
                Contract.TransferModel.WALLET_FROM_ID,
                Contract.TransferModel.WALLET_TO_ID,
                Contract.TransferModel.MONEY_FROM,
                Contract.TransferModel.MONEY_TO,
                Contract.TransferModel.MONEY_TAX,
                Contract.TransferModel.NOTE,
                Contract.TransferModel.EVENT_ID,
                Contract.TransferModel.PLACE_ID,
                Contract.TransferModel.CONFIRMED,
                Contract.TransferModel.COUNT_IN_TOTAL
            )
            val cursor = contentResolver.query(uri, projection, null, null, null)
            if (cursor != null) {
                var resultUri: Uri? = null
                if (cursor.moveToFirst()) {
                    val placeId = if (cursor.isNull(cursor.getColumnIndex(Contract.TransferModel.PLACE_ID))) {
                        null
                    } else {
                        cursor.getLong(cursor.getColumnIndex(Contract.TransferModel.PLACE_ID))
                    }
                    val eventId = if (cursor.isNull(cursor.getColumnIndex(Contract.TransferModel.EVENT_ID))) {
                        null
                    } else {
                        cursor.getLong(cursor.getColumnIndex(Contract.TransferModel.EVENT_ID))
                    }
                    val contentValues = TransferContentValuesBuilder()
                        .description(cursor.getString(cursor.getColumnIndex(Contract.TransferModel.DESCRIPTION)))
                        .date(DateUtils.getSQLDateTimeString(Date()))
                        .fromWalletId(cursor.getLong(cursor.getColumnIndex(Contract.TransferModel.WALLET_FROM_ID)))
                        .toWalletId(cursor.getLong(cursor.getColumnIndex(Contract.TransferModel.WALLET_TO_ID)))
                        .taxWalletId(cursor.getLong(cursor.getColumnIndex(Contract.TransferModel.WALLET_FROM_ID)))
                        .fromMoney(cursor.getLong(cursor.getColumnIndex(Contract.TransferModel.MONEY_FROM)))
                        .toMoney(cursor.getLong(cursor.getColumnIndex(Contract.TransferModel.MONEY_TO)))
                        .taxMoney(cursor.getLong(cursor.getColumnIndex(Contract.TransferModel.MONEY_TAX)))
                        .note(cursor.getString(cursor.getColumnIndex(Contract.TransferModel.NOTE)))
                        .placeId(placeId)
                        .eventId(eventId)
                        .confirmed(cursor.getInt(cursor.getColumnIndex(Contract.TransferModel.CONFIRMED)))
                        .countInTotal(cursor.getInt(cursor.getColumnIndex(Contract.TransferModel.COUNT_IN_TOTAL)))
                        .build()
                    resultUri = contentResolver.insert(DataContentProvider.CONTENT_TRANSFERS, contentValues)
                }
                cursor.close()
                return resultUri
            }
            return null
        }
    }
}
