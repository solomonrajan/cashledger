/*
 * Copyright (c) 2018. MoneyWallet
 * Copyright (c) 2026. solomonrajan/CashLedger
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

@file:Suppress("EmptyFunctionBlock", "MaxLineLength")

package com.oriondev.moneywallet.ui.activity

import android.content.ContentUris
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import androidx.annotation.MenuRes
import androidx.annotation.StringRes
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import androidx.recyclerview.widget.LinearLayoutManager
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.ui.activity.base.SinglePanelSimpleListActivity
import com.oriondev.moneywallet.ui.adapter.recycler.AbstractCursorAdapter
import com.oriondev.moneywallet.ui.adapter.recycler.CategoryRuleCursorAdapter
import com.oriondev.moneywallet.ui.view.AdvancedRecyclerView
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog

/**
 * The rules that file a transaction under a category from what its description holds. A tap opens
 * a rule, a long press offers to delete it, and the button adds one.
 */
class CategoryRuleListActivity : SinglePanelSimpleListActivity(), CategoryRuleCursorAdapter.ActionListener {

    override fun onPrepareRecyclerView(recyclerView: AdvancedRecyclerView) {
        recyclerView.setLayoutManager(LinearLayoutManager(this))
        recyclerView.setEmptyText(R.string.message_no_category_rule_found)
    }

    override fun onCreateAdapter(): AbstractCursorAdapter<*> = CategoryRuleCursorAdapter(this)

    @StringRes
    override fun getActivityTitleRes(): Int = R.string.title_activity_category_rule_list

    @MenuRes
    override fun onInflateMenu(): Int = R.menu.menu_sort_items

    override fun onMenuItemClick(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_sort_items) {
            startActivity(Intent(this, CategoryRuleSortActivity::class.java))
        }
        return false
    }

    override fun isFloatingActionButtonEnabled(): Boolean = true

    override fun onFloatingActionButtonClick() {
        val intent = Intent(this, NewEditCategoryRuleActivity::class.java).apply {
            putExtra(NewEditCategoryRuleActivity.MODE, NewEditCategoryRuleActivity.Mode.NEW_ITEM)
        }
        startActivity(intent)
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor> {
        val uri: Uri = DataContentProvider.CONTENT_CATEGORY_RULES
        val projection = arrayOf(
            Contract.CategoryRule.ID,
            Contract.CategoryRule.PATTERN,
            Contract.CategoryRule.CATEGORY_NAME
        )
        // the order the rules are matched in, so the list reads the way the lookup does
        val sortBy = "${Contract.CategoryRule.INDEX} ASC, ${Contract.CategoryRule.ID} ASC"
        return CursorLoader(this, uri, projection, null, null, sortBy)
    }

    override fun onCategoryRuleClick(id: Long) {
        val intent = Intent(this, NewEditCategoryRuleActivity::class.java).apply {
            putExtra(NewEditCategoryRuleActivity.MODE, NewEditCategoryRuleActivity.Mode.EDIT_ITEM)
            putExtra(NewEditCategoryRuleActivity.ID, id)
        }
        startActivity(intent)
    }

    override fun onCategoryRuleLongClick(id: Long, pattern: String?) {
        ThemedDialog.buildMaterialDialog(this)
            .setTitle(R.string.title_warning)
            .setMessage(getString(R.string.message_delete_category_rule, pattern))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val uri: Uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORY_RULES, id)
                contentResolver.delete(uri, null, null)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
