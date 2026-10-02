/*
 * Copyright (c) 2018. MoneyWallet
 * Copyright (c) 2026. solomonrajan/CashLedger
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
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.viewpager.widget.PagerAdapter
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.Category
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.ui.activity.base.SinglePanelViewPagerActivity
import com.oriondev.moneywallet.ui.adapter.pager.CategoryViewPagerAdapter
import com.oriondev.moneywallet.ui.fragment.primary.CategoryListFragment
import com.oriondev.moneywallet.utils.IconLoader

class CategoryPickerActivity : SinglePanelViewPagerActivity(), CategoryListFragment.Controller {

    private var query = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            query = savedInstanceState.getString(SS_QUERY, "")
        }
        super.onCreate(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(SS_QUERY, query)
    }

    override fun onToolbarReady(toolbar: Toolbar) {
        toolbar.title = null
        val view = layoutInflater.inflate(R.layout.layout_toolbar_search_view, toolbar, true)
        val searchEditText: EditText = view.findViewById(R.id.search_edit_text)
        searchEditText.setText(query)
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                val newQuery = s.toString()
                if (newQuery != query) {
                    query = newQuery
                    for (fragment in supportFragmentManager.fragments) {
                        if (fragment is CategoryListFragment) {
                            fragment.setQuery(query)
                        }
                    }
                }
            }

            override fun afterTextChanged(s: Editable) {}
        })
    }

    override fun getCategoryQuery(): String = query

    override fun onCreatePagerAdapter(fragmentManager: FragmentManager): PagerAdapter {
        var showSubCategories = true
        var showSystemCategories = false
        val currentIntent = intent
        if (currentIntent != null) {
            showSubCategories = currentIntent.getBooleanExtra(SHOW_SUB_CATEGORIES, true)
            showSystemCategories = currentIntent.getBooleanExtra(SHOW_SYSTEM_CATEGORIES, false)
        }
        return CategoryViewPagerAdapter(fragmentManager, this, showSubCategories, showSystemCategories)
    }

    override fun onCreatePanelView(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?) {
        super.onCreatePanelView(inflater, parent, savedInstanceState)
        if (savedInstanceState == null) {
            viewPagerPosition = 1
        }
    }

    override fun getActivityTitleRes(): Int = R.string.title_activity_category_picker

    override fun onFloatingActionButtonClick() {
        val intent = Intent(this, NewEditCategoryActivity::class.java)
        intent.putExtra(NewEditItemActivity.MODE, NewEditItemActivity.Mode.NEW_ITEM)
        when (viewPagerPosition) {
            0, 2 -> intent.putExtra(NewEditCategoryActivity.TYPE, Contract.CategoryType.INCOME)
            1 -> intent.putExtra(NewEditCategoryActivity.TYPE, Contract.CategoryType.EXPENSE)
        }
        startActivity(intent)
    }

    override fun onCategoryClick(id: Long) {
        val contentResolver: ContentResolver = contentResolver
        val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORIES, id)
        val projection = arrayOf(
            Contract.Category.ID,
            Contract.Category.NAME,
            Contract.Category.ICON,
            Contract.Category.TYPE
        )
        val cursor = contentResolver.query(uri, projection, null, null, null)
        if (cursor != null) {
            val intent = Intent()
            var category: Category? = null
            if (cursor.moveToFirst()) {
                category = Category(
                    cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Category.ID)),
                    cursor.getString(cursor.getColumnIndexOrThrow(Contract.Category.NAME)),
                    IconLoader.parse(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Category.ICON))),
                    Contract.CategoryType.fromValue(cursor.getInt(cursor.getColumnIndexOrThrow(Contract.Category.TYPE)))
                )
            }
            cursor.close()
            intent.putExtra(RESULT_CATEGORY, category)
            setResult(Activity.RESULT_OK, intent)
            finish()
        }
    }

    companion object {
        const val SHOW_SUB_CATEGORIES = "CategoryPickerActivity::Argument::ShowSubCategories"
        const val SHOW_SYSTEM_CATEGORIES = "CategoryPickerActivity::Argument::ShowSystemCategories"
        const val RESULT_CATEGORY = "CategoryPickerActivity::Result::Category"
        private const val SS_QUERY = "CategoryPickerActivity::SavedState::Query"
    }
}
