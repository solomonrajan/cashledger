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
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.Category
import com.oriondev.moneywallet.picker.CategoryPicker
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.ui.view.text.MaterialEditText
import com.oriondev.moneywallet.ui.view.text.NonEmptyTextValidator
import com.oriondev.moneywallet.ui.view.text.Validator
import com.oriondev.moneywallet.utils.IconLoader

/**
 * One rule, a snippet to look for in a transaction description and the category a transaction
 * holding it is filed under.
 */
class NewEditCategoryRuleActivity : NewEditItemActivity(), CategoryPicker.Controller {

    private var mPatternEditText: MaterialEditText? = null
    private var mCategoryEditText: MaterialEditText? = null
    private var mCategoryPicker: CategoryPicker? = null

    override fun onCreateHeaderView(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?) {
        val view = inflater.inflate(R.layout.layout_header_new_edit_category_rule, parent, true)
        val patternEt: MaterialEditText = view.findViewById(R.id.pattern_edit_text)
        mPatternEditText = patternEt
        patternEt.addValidator(NonEmptyTextValidator(this, R.string.error_input_pattern_not_valid))
    }

    override fun onCreatePanelView(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?) {
        val view = inflater.inflate(R.layout.layout_panel_new_edit_category_rule, parent, true)
        val categoryEt: MaterialEditText = view.findViewById(R.id.category_edit_text)
        mCategoryEditText = categoryEt
        categoryEt.setTextViewMode(true)
        categoryEt.addValidator(object : Validator {
            override fun getErrorMessage(): String = getString(R.string.error_input_missing_category)
            override fun isValid(charSequence: CharSequence): Boolean = mCategoryPicker?.isSelected == true
            override fun autoValidate(): Boolean = false
        })
        categoryEt.setOnClickListener {
            mCategoryPicker?.showPicker()
        }
    }

    override fun onViewCreated(savedInstanceState: Bundle?) {
        super.onViewCreated(savedInstanceState)
        var category: Category? = null
        if (savedInstanceState == null && getMode() == Mode.EDIT_ITEM) {
            val contentResolver = contentResolver
            val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORY_RULES, getItemId())
            val projection = arrayOf(
                Contract.CategoryRule.PATTERN,
                Contract.CategoryRule.CATEGORY_ID
            )
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    mPatternEditText?.setText(cursor.getString(cursor.getColumnIndexOrThrow(Contract.CategoryRule.PATTERN)))
                    category = readCategory(
                        contentResolver,
                        cursor.getLong(cursor.getColumnIndexOrThrow(Contract.CategoryRule.CATEGORY_ID))
                    )
                }
            }
        }
        val fragmentManager = supportFragmentManager
        mCategoryPicker = CategoryPicker.createPicker(fragmentManager, TAG_CATEGORY_PICKER, category)
    }

    override fun getActivityTileRes(mode: Mode): Int {
        return when (mode) {
            Mode.NEW_ITEM -> R.string.title_activity_new_category_rule
            Mode.EDIT_ITEM -> R.string.title_activity_edit_category_rule
        }
    }

    override fun onSaveChanges(mode: Mode) {
        val patternValid = mPatternEditText?.validate() == true
        val categoryValid = mCategoryEditText?.validate() == true
        val currentCategory = mCategoryPicker?.currentCategory
        if (patternValid && categoryValid && currentCategory != null) {
            val contentResolver = contentResolver
            val contentValues = ContentValues().apply {
                put(Contract.CategoryRule.PATTERN, mPatternEditText?.getTextAsString()?.trim() ?: "")
                put(Contract.CategoryRule.CATEGORY_ID, currentCategory.id)
            }
            when (mode) {
                Mode.NEW_ITEM -> {
                    // no index is named, so the insert appends the rule after the ones stored
                    contentResolver.insert(DataContentProvider.CONTENT_CATEGORY_RULES, contentValues)
                }
                Mode.EDIT_ITEM -> {
                    val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORY_RULES, getItemId())
                    contentResolver.update(uri, contentValues, null, null)
                }
            }
            setResult(RESULT_OK)
            finish()
        }
    }

    override fun onCategoryChanged(tag: String?, category: Category?) {
        mCategoryEditText?.setText(category?.name)
    }

    companion object {
        private const val TAG_CATEGORY_PICKER = "NewEditCategoryRuleActivity::Tag::CategoryPicker"

        /**
         * The category a stored rule names, read in full so the picker opens on it.
         *
         * @param contentResolver resolver to read through.
         * @param categoryId id the rule holds.
         * @return the category, or null when it is no longer there.
         */
        @JvmStatic
        private fun readCategory(contentResolver: ContentResolver, categoryId: Long): Category? {
            val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORIES, categoryId)
            val projection = arrayOf(
                Contract.Category.ID,
                Contract.Category.NAME,
                Contract.Category.ICON,
                Contract.Category.TYPE,
                Contract.Category.TAG
            )
            return contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    Category(
                        cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Category.ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(Contract.Category.NAME)),
                        IconLoader.parse(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Category.ICON))),
                        Contract.CategoryType.fromValue(cursor.getInt(cursor.getColumnIndexOrThrow(Contract.Category.TYPE)))
                            ?: Contract.CategoryType.EXPENSE,
                        cursor.getString(cursor.getColumnIndexOrThrow(Contract.Category.TAG))
                    )
                } else {
                    null
                }
            }
        }
    }
}
