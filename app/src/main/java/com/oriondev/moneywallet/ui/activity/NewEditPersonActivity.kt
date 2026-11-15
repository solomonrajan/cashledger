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

import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.Icon
import com.oriondev.moneywallet.picker.IconPicker
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.ui.view.text.MaterialEditText
import com.oriondev.moneywallet.ui.view.text.NonEmptyTextValidator
import com.oriondev.moneywallet.utils.IconLoader

/**
 * Created by andrea on 06/03/18.
 */
class NewEditPersonActivity : NewEditItemActivity(), IconPicker.Controller {

    private var mIconView: ImageView? = null
    private var mNameEditText: MaterialEditText? = null
    private var mNoteEditText: MaterialEditText? = null
    private var mIconPicker: IconPicker? = null

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
        val view = inflater.inflate(R.layout.layout_panel_new_edit_person, parent, true)
        mNoteEditText = view.findViewById(R.id.note_edit_text)
    }

    override fun onViewCreated(savedInstanceState: Bundle?) {
        super.onViewCreated(savedInstanceState)
        var icon: Icon? = null
        if (savedInstanceState == null && getMode() == Mode.EDIT_ITEM) {
            val contentResolver = contentResolver
            val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_PEOPLE, getItemId())
            val projection = arrayOf(
                Contract.Person.ID,
                Contract.Person.NAME,
                Contract.Person.ICON,
                Contract.Person.NOTE
            )
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    mNameEditText?.setText(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Person.NAME)))
                    icon = IconLoader.parse(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Person.ICON)))
                    mNoteEditText?.setText(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Person.NOTE)))
                }
            }
        }
        val fragmentManager = supportFragmentManager
        val picker = IconPicker.createPicker(fragmentManager, TAG_ICON_PICKER, icon)
        mIconPicker = picker
        picker.listenOn(mNameEditText)
    }

    override fun getActivityTileRes(mode: Mode): Int {
        return when (mode) {
            Mode.NEW_ITEM -> R.string.title_activity_new_person
            Mode.EDIT_ITEM -> R.string.title_activity_edit_person
        }
    }

    override fun onSaveChanges(mode: Mode) {
        if (mNameEditText?.validate() == true) {
            val contentResolver = contentResolver
            val contentValues = ContentValues().apply {
                put(Contract.Person.NAME, mNameEditText?.getTextAsString() ?: "")
                put(Contract.Person.ICON, mIconPicker?.currentIcon?.toString() ?: "")
                put(Contract.Person.NOTE, mNoteEditText?.getTextAsString() ?: "")
            }
            when (mode) {
                Mode.NEW_ITEM -> {
                    contentResolver.insert(DataContentProvider.CONTENT_PEOPLE, contentValues)
                }
                Mode.EDIT_ITEM -> {
                    val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_PEOPLE, getItemId())
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

    companion object {
        private const val TAG_ICON_PICKER = "NewEditPersonActivity::Tag::IconPicker"
    }
}
