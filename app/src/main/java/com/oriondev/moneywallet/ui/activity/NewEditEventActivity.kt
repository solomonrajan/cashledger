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
import android.widget.ImageView
import androidx.fragment.app.FragmentManager
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.Icon
import com.oriondev.moneywallet.picker.DateTimePicker
import com.oriondev.moneywallet.picker.IconPicker
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.ui.view.text.MaterialEditText
import com.oriondev.moneywallet.ui.view.text.NonEmptyTextValidator
import com.oriondev.moneywallet.ui.view.text.Validator
import com.oriondev.moneywallet.utils.DateFormatter
import com.oriondev.moneywallet.utils.DateUtils
import com.oriondev.moneywallet.utils.IconLoader
import java.util.Calendar
import java.util.Date

/**
 * Created by andrea on 02/03/18.
 */
class NewEditEventActivity : NewEditItemActivity(), IconPicker.Controller, DateTimePicker.Controller {

    private var mIconView: ImageView? = null
    private var mNameEditText: MaterialEditText? = null
    private var mStartDateEditText: MaterialEditText? = null
    private var mEndDateEditText: MaterialEditText? = null
    private var mNoteEditText: MaterialEditText? = null

    private var mIconPicker: IconPicker? = null
    private var mStartDatePicker: DateTimePicker? = null
    private var mEndDatePicker: DateTimePicker? = null

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
        val view = inflater.inflate(R.layout.layout_panel_new_edit_event, parent, true)
        val startDateEt: MaterialEditText = view.findViewById(R.id.start_date_edit_text)
        val endDateEt: MaterialEditText = view.findViewById(R.id.end_date_edit_text)
        val noteEt: MaterialEditText = view.findViewById(R.id.note_edit_text)

        mStartDateEditText = startDateEt
        mEndDateEditText = endDateEt
        mNoteEditText = noteEt

        startDateEt.setTextViewMode(true)
        endDateEt.setTextViewMode(true)

        startDateEt.addValidator(object : Validator {
            override fun getErrorMessage(): String = getString(R.string.error_input_missing_start_date)
            override fun isValid(charSequence: CharSequence): Boolean = mStartDatePicker?.isSelected == true
            override fun autoValidate(): Boolean = false
        })
        endDateEt.addValidator(object : Validator {
            override fun getErrorMessage(): String = getString(R.string.error_input_missing_end_date)
            override fun isValid(charSequence: CharSequence): Boolean = mEndDatePicker?.isSelected == true
            override fun autoValidate(): Boolean = false
        })
        startDateEt.addValidator(object : Validator {
            override fun getErrorMessage(): String = getString(R.string.error_input_invalid_date_range)
            override fun isValid(charSequence: CharSequence): Boolean {
                val start = mStartDatePicker?.currentDateTime
                val end = mEndDatePicker?.currentDateTime
                return start != null && end != null && start.time <= end.time
            }
            override fun autoValidate(): Boolean = false
        })
        startDateEt.setOnClickListener {
            mStartDatePicker?.showDatePicker()
        }
        endDateEt.setOnClickListener {
            mEndDatePicker?.showDatePicker()
        }
        startDateEt.addValidator(object : Validator {
            override fun getErrorMessage(): String = getString(R.string.error_input_date_range_not_valid)
            override fun isValid(charSequence: CharSequence): Boolean {
                val start = mStartDatePicker?.currentDateTime
                val end = mEndDatePicker?.currentDateTime
                return start != null && end != null && start.compareTo(end) <= 0
            }
            override fun autoValidate(): Boolean = false
        })
    }

    override fun onViewCreated(savedInstanceState: Bundle?) {
        super.onViewCreated(savedInstanceState)
        var name: String? = null
        var icon: Icon? = null
        val calendar = Calendar.getInstance()
        var startDate: Date? = calendar.time
        var endDate: Date? = DateUtils.addMonths(calendar, 1)
        if (savedInstanceState == null && getMode() == Mode.EDIT_ITEM) {
            val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_EVENTS, getItemId())
            val projection = arrayOf(
                Contract.Event.NAME,
                Contract.Event.ICON,
                Contract.Event.START_DATE,
                Contract.Event.END_DATE,
                Contract.Event.NOTE
            )
            val contentResolver: ContentResolver = contentResolver
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    name = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Event.NAME))
                    icon = IconLoader.parse(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Event.ICON)))
                    startDate = DateUtils.getDateFromSQLDateString(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Event.START_DATE)))
                    endDate = DateUtils.getDateFromSQLDateString(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Event.END_DATE)))
                    mNoteEditText?.setText(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Event.NOTE)))
                }
            }
        }
        val fragmentManager: FragmentManager = supportFragmentManager
        mNameEditText?.setText(name)
        val iconPicker = IconPicker.createPicker(fragmentManager, TAG_ICON_PICKER, icon)
        mIconPicker = iconPicker
        mStartDatePicker = DateTimePicker.createPicker(fragmentManager, TAG_START_DATE_PICKER, startDate)
        mEndDatePicker = DateTimePicker.createPicker(fragmentManager, TAG_END_DATE_PICKER, endDate)
        iconPicker.listenOn(mNameEditText)
    }

    override fun getActivityTileRes(mode: Mode): Int {
        return when (mode) {
            Mode.NEW_ITEM -> R.string.title_activity_new_event
            Mode.EDIT_ITEM -> R.string.title_activity_edit_event
        }
    }

    private fun validate(): Boolean {
        return mNameEditText?.validate() == true &&
            mStartDateEditText?.validate() == true &&
            mEndDateEditText?.validate() == true
    }

    override fun onSaveChanges(mode: Mode) {
        if (validate()) {
            val contentValues = ContentValues().apply {
                put(Contract.Event.NAME, mNameEditText?.getTextAsString() ?: "")
                put(Contract.Event.ICON, mIconPicker?.currentIcon?.toString() ?: "")
                put(Contract.Event.START_DATE, DateUtils.getSQLDateString(mStartDatePicker?.currentDateTime))
                put(Contract.Event.END_DATE, DateUtils.getSQLDateString(mEndDatePicker?.currentDateTime))
                put(Contract.Event.NOTE, mNoteEditText?.getTextAsString() ?: "")
            }
            val contentResolver: ContentResolver = contentResolver
            when (mode) {
                Mode.NEW_ITEM -> {
                    contentResolver.insert(DataContentProvider.CONTENT_EVENTS, contentValues)
                }
                Mode.EDIT_ITEM -> {
                    val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_EVENTS, getItemId())
                    contentResolver.update(uri, contentValues, null, null)
                }
            }
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    override fun onIconChanged(tag: String?, icon: Icon?) {
        IconLoader.loadInto(icon, mIconView)
    }

    override fun onDateTimeChanged(tag: String?, date: Date?) {
        when (tag) {
            TAG_START_DATE_PICKER -> {
                mStartDateEditText?.let { DateFormatter.applyDate(it, date) }
            }
            TAG_END_DATE_PICKER -> {
                mEndDateEditText?.let { DateFormatter.applyDate(it, date) }
            }
        }
    }

    companion object {
        private const val TAG_ICON_PICKER = "NewEditEventActivity::Tag::IconPicker"
        private const val TAG_START_DATE_PICKER = "NewEditEventActivity::Tag::StartDatePicker"
        private const val TAG_END_DATE_PICKER = "NewEditEventActivity::Tag::EndDatePicker"
    }
}
