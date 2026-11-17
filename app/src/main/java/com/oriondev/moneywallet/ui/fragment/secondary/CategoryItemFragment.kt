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

package com.oriondev.moneywallet.ui.fragment.secondary

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.loader.app.LoaderManager
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.Icon
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.SQLiteDataException
import com.oriondev.moneywallet.ui.activity.NewEditCategoryActivity
import com.oriondev.moneywallet.ui.activity.NewEditItemActivity
import com.oriondev.moneywallet.ui.activity.TransactionListActivity
import com.oriondev.moneywallet.ui.fragment.base.SecondaryPanelFragment
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog
import com.oriondev.moneywallet.utils.IconLoader

/**
 * Created by andrea on 01/04/18.
 */
class CategoryItemFragment : SecondaryPanelFragment(), LoaderManager.LoaderCallbacks<Cursor> {

    private var mProgressLayout: View? = null
    private var mMainLayout: View? = null

    private var mAvatarImageView: ImageView? = null
    private var mNameTextView: TextView? = null
    private var mParentCategoryTextView: TextView? = null
    private var mCategoryTypeRadioGroup: RadioGroup? = null
    private var mIncomeRadioButton: RadioButton? = null
    private var mExpenseRadioButton: RadioButton? = null
    private var mShowReportCheckBox: CheckBox? = null

    override fun onCreateHeaderView(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?) {
        val view = inflater.inflate(R.layout.layout_header_show_icon_name_item, parent, true)
        mAvatarImageView = view.findViewById(R.id.avatar_image_view)
        mNameTextView = view.findViewById(R.id.name_text_view)
    }

    override fun onCreateBodyView(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?) {
        val view = inflater.inflate(R.layout.layout_panel_show_category_item, parent, true)
        mProgressLayout = view.findViewById(R.id.secondary_panel_progress_wheel)
        mMainLayout = view.findViewById(R.id.secondary_panel_layout)
        mParentCategoryTextView = view.findViewById(R.id.parent_category_text_view)
        mCategoryTypeRadioGroup = view.findViewById(R.id.category_type)
        mIncomeRadioButton = view.findViewById(R.id.income_radio_button)
        mExpenseRadioButton = view.findViewById(R.id.expense_radio_button)
        mShowReportCheckBox = view.findViewById(R.id.show_report_check_box)
    }

    override fun getTitle(): String {
        return getString(R.string.title_fragment_item_category)
    }

    override fun onInflateMenu(): Int {
        return R.menu.menu_list_edit_delete_item
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        val itemId = item.itemId
        if (itemId == R.id.action_show_transaction_list) {
            val intent = Intent(activity, TransactionListActivity::class.java).apply {
                putExtra(TransactionListActivity.CATEGORY_ID, getItemId())
            }
            startActivity(intent)
        } else if (itemId == R.id.action_edit_item) {
            val intent = Intent(activity, NewEditCategoryActivity::class.java).apply {
                putExtra(NewEditItemActivity.MODE, NewEditItemActivity.Mode.EDIT_ITEM)
                putExtra(NewEditItemActivity.ID, getItemId())
            }
            startActivity(intent)
        } else if (itemId == R.id.action_delete_item) {
            activity?.let { showDeleteDialog(it) }
        }
        return false
    }

    private fun showDeleteDialog(context: Context) {
        ThemedDialog.buildMaterialDialog(context)
            .setTitle(R.string.title_warning)
            .setMessage(R.string.message_delete_category)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val act = activity
                if (act != null) {
                    try {
                        val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORIES, getItemId())
                        val contentResolver: ContentResolver = act.contentResolver
                        contentResolver.delete(uri, null, null)
                        navigateBackSafely()
                        showItemId(0L)
                    } catch (e: SQLiteDataException) {
                        var contentRes = 0
                        when (e.errorCode) {
                            Contract.ErrorCode.CATEGORY_HAS_CHILDREN -> {
                                contentRes = R.string.message_error_delete_category_with_children
                            }
                            Contract.ErrorCode.CATEGORY_IN_USE -> {
                                contentRes = R.string.message_error_delete_category_in_use
                            }
                            Contract.ErrorCode.SYSTEM_CATEGORY_NOT_MODIFIABLE -> {
                                contentRes = R.string.message_error_delete_system_category
                            }
                        }
                        if (contentRes != 0) {
                            ThemedDialog.buildMaterialDialog(act)
                                .setTitle(R.string.title_error)
                                .setMessage(contentRes)
                                .setPositiveButton(android.R.string.ok, null)
                                .show()
                        }
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onShowItemId(itemId: Long) {
        setLoadingScreen(true)
        LoaderManager.getInstance(this).restartLoader(CATEGORY_LOADER_ID, null, this)
    }

    private fun setLoadingScreen(loading: Boolean) {
        if (loading) {
            mAvatarImageView?.setImageDrawable(null)
            mNameTextView?.text = null
            mProgressLayout?.visibility = View.VISIBLE
            mMainLayout?.visibility = View.GONE
        } else {
            mProgressLayout?.visibility = View.GONE
            mMainLayout?.visibility = View.VISIBLE
        }
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor>? {
        val act = activity ?: return null
        val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORIES, getItemId())
        val projection = arrayOf(
            Contract.Category.NAME,
            Contract.Category.ICON,
            Contract.Category.PARENT,
            Contract.Category.PARENT_NAME,
            Contract.Category.TYPE,
            Contract.Category.SHOW_REPORT,
            Contract.Category.PARENT_SHOW_REPORT
        )
        return CursorLoader(act, uri, projection, null, null, null)
    }

    override fun onLoadFinished(loader: Loader<Cursor>, cursor: Cursor?) {
        if (cursor != null && cursor.moveToFirst()) {
            val icon: Icon? = IconLoader.parse(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Category.ICON)))
            mAvatarImageView?.let { IconLoader.loadInto(icon, it) }
            mNameTextView?.text = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Category.NAME))
            if (!cursor.isNull(cursor.getColumnIndexOrThrow(Contract.Category.PARENT))) {
                mParentCategoryTextView?.text = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Category.PARENT_NAME))
                mParentCategoryTextView?.visibility = View.VISIBLE
            } else {
                mParentCategoryTextView?.visibility = View.GONE
            }
            val type = Contract.CategoryType.fromValue(cursor.getInt(cursor.getColumnIndexOrThrow(Contract.Category.TYPE)))
            if (type != null) {
                when (type) {
                    Contract.CategoryType.INCOME -> {
                        mIncomeRadioButton?.isChecked = true
                        mCategoryTypeRadioGroup?.visibility = View.VISIBLE
                    }
                    Contract.CategoryType.EXPENSE -> {
                        mExpenseRadioButton?.isChecked = true
                        mCategoryTypeRadioGroup?.visibility = View.VISIBLE
                    }
                    else -> {
                        mCategoryTypeRadioGroup?.visibility = View.GONE
                    }
                }
            } else {
                mCategoryTypeRadioGroup?.visibility = View.GONE
            }
            if (cursor.getInt(cursor.getColumnIndexOrThrow(Contract.Category.SHOW_REPORT)) == 1) {
                mShowReportCheckBox?.isChecked = true
                // the reports keep a transaction only when its category and its parent are both
                // included, so a subcategory under a hidden parent is out however its own box
                // reads. A top level category joins no parent row, and the null that leaves here
                // means included, which is how the report filter reads it too.
                val parentShowReport = cursor.getColumnIndexOrThrow(Contract.Category.PARENT_SHOW_REPORT)
                val parentName = cursor.getColumnIndexOrThrow(Contract.Category.PARENT_NAME)
                if (!cursor.isNull(parentShowReport) && cursor.getInt(parentShowReport) == 0) {
                    mShowReportCheckBox?.text = getString(
                        R.string.hint_show_category_report_off_parent,
                        cursor.getString(parentName)
                    )
                } else {
                    mShowReportCheckBox?.setText(R.string.hint_show_category_report_on)
                }
            } else {
                mShowReportCheckBox?.isChecked = false
                mShowReportCheckBox?.setText(R.string.hint_show_category_report_off)
            }
        } else {
            showItemId(0L)
        }
        setLoadingScreen(false)
    }

    override fun onLoaderReset(loader: Loader<Cursor>) {
        // nothing to release
    }

    companion object {
        private const val CATEGORY_LOADER_ID = 246327
    }
}
