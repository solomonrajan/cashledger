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

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.RadioButton
import androidx.fragment.app.FragmentManager
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.Category
import com.oriondev.moneywallet.model.Icon
import com.oriondev.moneywallet.picker.CategoryPicker
import com.oriondev.moneywallet.picker.IconPicker
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.SQLiteDataException
import com.oriondev.moneywallet.ui.view.text.MaterialEditText
import com.oriondev.moneywallet.ui.view.text.NonEmptyTextValidator
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog
import com.oriondev.moneywallet.utils.IconLoader

/**
 * Created by andrea on 02/03/18.
 */
class NewEditCategoryActivity : NewEditItemActivity(), IconPicker.Controller, CategoryPicker.Controller {

    private var mIconView: ImageView? = null
    private var mNameEditText: MaterialEditText? = null
    private var mParentCategoryEditText: MaterialEditText? = null
    private var mIncomeRadioButton: RadioButton? = null
    private var mExpenseRadioButton: RadioButton? = null
    private var mShowReportCheckBox: CheckBox? = null

    private var mIconPicker: IconPicker? = null
    private var mCategoryPicker: CategoryPicker? = null

    private var mIsSystemCategory: Boolean = false

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
        val view = inflater.inflate(R.layout.layout_panel_new_edit_category, parent, true)
        val parentCategoryEt: MaterialEditText = view.findViewById(R.id.parent_category_edit_text)
        val incomeRb: RadioButton = view.findViewById(R.id.income_radio_button)
        val expenseRb: RadioButton = view.findViewById(R.id.expense_radio_button)
        val showReportCb: CheckBox = view.findViewById(R.id.show_report_check_box)

        mParentCategoryEditText = parentCategoryEt
        mIncomeRadioButton = incomeRb
        mExpenseRadioButton = expenseRb
        mShowReportCheckBox = showReportCb

        parentCategoryEt.setTextViewMode(true)
        parentCategoryEt.setOnClickListener {
            mCategoryPicker?.showParentPicker(getItemId(), getCurrentCategoryType())
        }
        parentCategoryEt.setOnCancelButtonClickListener(object : MaterialEditText.CancelButtonListener {
            override fun onCancelButtonClick(materialEditText: MaterialEditText): Boolean {
                mCategoryPicker?.currentCategory = null
                return false
            }
        })
        incomeRb.setOnCheckedChangeListener { _, isChecked ->
            if (mCategoryPicker?.isSelected == true) {
                val category = mCategoryPicker?.currentCategory
                if (category != null) {
                    if ((isChecked && category.type != Contract.CategoryType.INCOME) ||
                        (!isChecked && category.type != Contract.CategoryType.EXPENSE)
                    ) {
                        mCategoryPicker?.currentCategory = null
                    }
                }
            }
        }
    }

    override fun onViewCreated(savedInstanceState: Bundle?) {
        super.onViewCreated(savedInstanceState)
        var icon: Icon? = null
        var category: Category? = null
        var type: Contract.CategoryType? = null
        if (savedInstanceState == null) {
            if (getMode() == Mode.EDIT_ITEM) {
                val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORIES, getItemId())
                val projection = arrayOf(
                    Contract.Category.NAME,
                    Contract.Category.ICON,
                    Contract.Category.PARENT,
                    Contract.Category.PARENT_NAME,
                    Contract.Category.PARENT_ICON,
                    Contract.Category.PARENT_TYPE,
                    Contract.Category.TYPE,
                    Contract.Category.SHOW_REPORT
                )
                contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        mNameEditText?.setText(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Category.NAME)))
                        icon = IconLoader.parse(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Category.ICON)))
                        val parentIdx = cursor.getColumnIndexOrThrow(Contract.Category.PARENT)
                        if (!cursor.isNull(parentIdx)) {
                            category = Category(
                                cursor.getLong(parentIdx),
                                cursor.getString(cursor.getColumnIndexOrThrow(Contract.Category.PARENT_NAME)),
                                IconLoader.parse(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Category.PARENT_ICON))),
                                Contract.CategoryType.fromValue(cursor.getInt(cursor.getColumnIndexOrThrow(Contract.Category.PARENT_TYPE)))
                                    ?: Contract.CategoryType.EXPENSE
                            )
                        }
                        type = Contract.CategoryType.fromValue(cursor.getInt(cursor.getColumnIndexOrThrow(Contract.Category.TYPE)))
                        mShowReportCheckBox?.isChecked = cursor.getInt(cursor.getColumnIndexOrThrow(Contract.Category.SHOW_REPORT)) == 1
                        mIsSystemCategory = type == Contract.CategoryType.SYSTEM
                    }
                }
            } else if (getMode() == Mode.NEW_ITEM) {
                val callerIntent = intent
                type = callerIntent?.getSerializableExtra(TYPE) as? Contract.CategoryType
                if (type == null) {
                    type = Contract.CategoryType.INCOME
                }
            }
        } else {
            mIsSystemCategory = savedInstanceState.getBoolean(SS_SYSTEM_CATEGORY)
        }
        val fragmentManager: FragmentManager = supportFragmentManager
        val iconPicker = IconPicker.createPicker(fragmentManager, TAG_ICON_PICKER, icon)
        mIconPicker = iconPicker
        mCategoryPicker = CategoryPicker.createPicker(fragmentManager, TAG_CURRENCY_PICKER, category)
        setCurrentCategoryType(type)
        iconPicker.listenOn(mNameEditText)
    }

    override fun getActivityTileRes(mode: Mode): Int {
        return when (mode) {
            Mode.NEW_ITEM -> R.string.title_activity_new_category
            Mode.EDIT_ITEM -> R.string.title_activity_edit_category
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(SS_SYSTEM_CATEGORY, mIsSystemCategory)
    }

    private fun validate(mode: Mode): Boolean {
        if (mNameEditText?.validate() == true) {
            val category = mCategoryPicker?.currentCategory
            if (category != null && mode == Mode.EDIT_ITEM) {
                // TODO check if the category is a parent that is going to be a child.
                // TODO this MUST NOT BE ALLOWED or we could generate a hierarchy.
            }
            return category == null || category.type == getCurrentCategoryType()
        }
        return false
    }

    private fun setCurrentCategoryType(type: Contract.CategoryType?) {
        if (mIsSystemCategory) {
            mParentCategoryEditText?.visibility = View.GONE
            mIncomeRadioButton?.visibility = View.GONE
            mExpenseRadioButton?.visibility = View.GONE
        } else if (type != null) {
            when (type) {
                Contract.CategoryType.INCOME -> mIncomeRadioButton?.isChecked = true
                Contract.CategoryType.EXPENSE -> mExpenseRadioButton?.isChecked = true
                else -> {}
            }
        }
    }

    private fun getCurrentCategoryType(): Contract.CategoryType {
        return if (mIncomeRadioButton?.isChecked == true) {
            Contract.CategoryType.INCOME
        } else {
            Contract.CategoryType.EXPENSE
        }
    }

    override fun onSaveChanges(mode: Mode) {
        if (validate(mode)) {
            val contentValues = ContentValues().apply {
                put(Contract.Category.NAME, mNameEditText?.getTextAsString() ?: "")
                put(Contract.Category.ICON, mIconPicker?.currentIcon?.toString() ?: "")
                if (!mIsSystemCategory) {
                    val parentId = if (mCategoryPicker?.isSelected == true) mCategoryPicker?.currentCategory?.id else null
                    put(Contract.Category.PARENT, parentId)
                    put(Contract.Category.TYPE, getCurrentCategoryType().value)
                }
                put(Contract.Category.SHOW_REPORT, mShowReportCheckBox?.isChecked == true)
            }
            val contentResolver: ContentResolver = contentResolver
            when (mode) {
                Mode.NEW_ITEM -> {
                    try {
                        contentResolver.insert(DataContentProvider.CONTENT_CATEGORIES, contentValues)
                        setResult(RESULT_OK)
                        finish()
                    } catch (e: SQLiteDataException) {
                        var contentRes = 0
                        when (e.errorCode) {
                            Contract.ErrorCode.CATEGORY_HIERARCHY_NOT_SUPPORTED -> {
                                contentRes = R.string.message_error_insert_category_deep_hierarchy
                            }
                            Contract.ErrorCode.CATEGORY_NOT_CONSISTENT -> {
                                contentRes = R.string.message_error_insert_category_not_consistent
                            }
                        }
                        if (contentRes != 0) {
                            ThemedDialog.buildMaterialDialog(this)
                                .setTitle(R.string.title_error)
                                .setMessage(contentRes)
                                .setPositiveButton(android.R.string.ok, null)
                                .show()
                        }
                    }
                }
                Mode.EDIT_ITEM -> {
                    try {
                        val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORIES, getItemId())
                        contentResolver.update(uri, contentValues, null, null)
                        setResult(RESULT_OK)
                        finish()
                    } catch (e: SQLiteDataException) {
                        var contentRes = 0
                        when (e.errorCode) {
                            Contract.ErrorCode.CATEGORY_HIERARCHY_NOT_SUPPORTED -> {
                                contentRes = R.string.message_error_update_category_deep_hierarchy
                            }
                            Contract.ErrorCode.CATEGORY_NOT_CONSISTENT -> {
                                contentRes = R.string.message_error_update_category_not_consistent
                            }
                        }
                        if (contentRes != 0) {
                            ThemedDialog.buildMaterialDialog(this)
                                .setTitle(R.string.title_error)
                                .setMessage(contentRes)
                                .setPositiveButton(android.R.string.ok, null)
                                .show()
                        }
                    }
                }
            }
        }
    }

    override fun onIconChanged(tag: String?, icon: Icon?) {
        IconLoader.loadInto(icon, mIconView)
    }

    override fun onCategoryChanged(tag: String?, category: Category?) {
        if (category != null) {
            mParentCategoryEditText?.setText(category.name)
            if (category.type != getCurrentCategoryType()) {
                setCurrentCategoryType(category.type)
            }
        } else {
            mParentCategoryEditText?.setText(null)
        }
    }

    companion object {
        const val TYPE = "NewEditCategoryActivity::Type"

        private const val TAG_ICON_PICKER = "NewEditCategoryActivity::Tag::IconPicker"
        private const val TAG_CURRENCY_PICKER = "NewEditCategoryActivity::Tag::CategoryPicker"

        private const val SS_SYSTEM_CATEGORY = "NewEditCategoryActivity::SavedState::IsSystemCategory"
    }
}
