/*
 * Copyright (c) 2018.
 * Copyright (c) 2026 solomonrajan.
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

package com.oriondev.moneywallet.picker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.oriondev.moneywallet.model.Category
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.ui.activity.CategoryPickerActivity
import com.oriondev.moneywallet.ui.fragment.dialog.MultiCategoryPickerDialog
import com.oriondev.moneywallet.ui.fragment.dialog.ParentCategoryPickerDialog

/**
 * Created by andrea on 02/02/18.
 */
class CategoryPicker :
    Fragment(),
    ParentCategoryPickerDialog.Callback,
    MultiCategoryPickerDialog.Callback {

    private var mController: Controller? = null
    private var mMultiCategoryController: MultiCategoryController? = null

    private var mMultiCategory = false
    private var mCurrentCategory: Category? = null
    private var mCurrentCategories: Array<Category>? = null

    private var mParentCategoryPickerDialog: ParentCategoryPickerDialog? = null
    private var mMultiCategoryPickerDialog: MultiCategoryPickerDialog? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mController = findController(Controller::class.java, context, checkParent = true)
        mMultiCategoryController = findController(MultiCategoryController::class.java, context, checkParent = true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            mMultiCategory = savedInstanceState.getBoolean(SS_MULTI_CATEGORY)
            mCurrentCategory = savedInstanceState.readParcelable(SS_CURRENT_CATEGORY)
            @Suppress("DEPRECATION")
            mCurrentCategories = savedInstanceState.getParcelableArray(SS_CURRENT_CATEGORIES)
                .toTypedParcelableArray()
        } else {
            val arguments = arguments
            if (arguments != null) {
                mMultiCategory = arguments.getBoolean(ARG_MULTI_CATEGORY)
                mCurrentCategory = arguments.readParcelable(ARG_DEFAULT_CATEGORY)
                @Suppress("DEPRECATION")
                mCurrentCategories = arguments.getParcelableArray(ARG_DEFAULT_CATEGORIES)
                    .toTypedParcelableArray()
            } else {
                mCurrentCategory = null
                mCurrentCategories = null
            }
        }
        mParentCategoryPickerDialog = findOrCreateDialog(ParentCategoryPickerDialog::class.java) {
            ParentCategoryPickerDialog.newInstance()
        }
        mParentCategoryPickerDialog?.setCallback(this)
        mMultiCategoryPickerDialog = findOrCreateDialog(
            MultiCategoryPickerDialog::class.java,
            getMultiDialogTag()
        ) {
            MultiCategoryPickerDialog.newInstance()
        }
        mMultiCategoryPickerDialog?.setCallback(this)
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        fireCallbackSafely()
    }

    private fun fireCallbackSafely() {
        if (mMultiCategory) {
            mMultiCategoryController?.onCategoryListChanged(tag, mCurrentCategories)
        } else {
            mController?.onCategoryChanged(tag, mCurrentCategory)
        }
    }

    private fun getMultiDialogTag(): String = "${tag}::MultiDialogFragment"

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(SS_MULTI_CATEGORY, mMultiCategory)
        outState.putParcelable(SS_CURRENT_CATEGORY, mCurrentCategory)
        outState.putParcelableArray(SS_CURRENT_CATEGORIES, mCurrentCategories)
    }

    val isSelected: Boolean
        get() = if (mMultiCategory) {
            !mCurrentCategories.isNullOrEmpty()
        } else {
            mCurrentCategory != null
        }

    val currentCategories: Array<Category>?
        get() = mCurrentCategories

    fun showMultiPicker() {
        mMultiCategoryPickerDialog?.showPicker(childFragmentManager, getMultiDialogTag(), mCurrentCategories)
    }

    fun setCategory(category: Category?) {
        mCurrentCategory = category
        fireCallbackSafely()
    }

    val currentCategory: Category?
        get() = mCurrentCategory

    @JvmOverloads
    fun showPicker(showSubCategories: Boolean = true, showSystemCategories: Boolean = false) {
        val intent = Intent(activity, CategoryPickerActivity::class.java)
        intent.putExtra(CategoryPickerActivity.SHOW_SUB_CATEGORIES, showSubCategories)
        intent.putExtra(CategoryPickerActivity.SHOW_SYSTEM_CATEGORIES, showSystemCategories)
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_CATEGORY_PICKER)
    }

    fun showParentPicker(categoryId: Long, type: Contract.CategoryType?) {
        mParentCategoryPickerDialog?.showPicker(
            childFragmentManager,
            getDefaultDialogTag(),
            mCurrentCategory,
            categoryId,
            type
        )
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CATEGORY_PICKER) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                mCurrentCategory = data.getParcelableExtra(CategoryPickerActivity.RESULT_CATEGORY)
                fireCallbackSafely()
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onDetach() {
        super.onDetach()
        mController = null
        mMultiCategoryController = null
    }

    override fun onCategoriesSelected(categories: Array<Category>?) {
        mCurrentCategories = categories
        fireCallbackSafely()
    }

    override fun onCategorySelected(category: Category?) {
        mCurrentCategory = category
        fireCallbackSafely()
    }

    interface Controller {
        fun onCategoryChanged(tag: String?, category: Category?)
    }

    interface MultiCategoryController {
        fun onCategoryListChanged(tag: String?, categories: Array<Category>?)
    }

    companion object {

        private const val SS_CURRENT_CATEGORY = "ParentCategoryPicker::SavedState::CurrentCategory"
        private const val SS_CURRENT_CATEGORIES = "ParentCategoryPicker::SavedState::CurrentCategories"
        private const val SS_MULTI_CATEGORY = "ParentCategoryPicker::SavedState::MultiCategory"
        private const val ARG_DEFAULT_CATEGORY = "ParentCategoryPicker::Arguments::DefaultCategory"
        private const val ARG_DEFAULT_CATEGORIES = "ParentCategoryPicker::Arguments::DefaultCategories"
        private const val ARG_MULTI_CATEGORY = "ParentCategoryPicker::Arguments::MultiCategory"

        private const val REQUEST_CATEGORY_PICKER = 1

        @JvmStatic
        fun createPicker(
            fragmentManager: FragmentManager,
            tag: String?,
            defaultCategory: Category?
        ): CategoryPicker {
            return fragmentManager.findOrCreatePicker(tag) {
                val arguments = Bundle()
                arguments.putBoolean(ARG_MULTI_CATEGORY, false)
                arguments.putParcelable(ARG_DEFAULT_CATEGORY, defaultCategory)
                val picker = CategoryPicker()
                picker.arguments = arguments
                picker
            }
        }

        /**
         * A picker over any number of categories at once, for a budget that covers more than one.
         * It reports through [MultiCategoryController] and leaves [currentCategory] empty; the single
         * category picker above is untouched and still reports through [Controller].
         *
         * @param fragmentManager manager the picker is added to.
         * @param tag name it is found again by.
         * @param defaultCategories categories chosen already, empty or null on a new item.
         * @return the picker, whether it was created here or found by its tag.
         */
        @JvmStatic
        fun createPicker(
            fragmentManager: FragmentManager,
            tag: String?,
            defaultCategories: Array<Category>?
        ): CategoryPicker {
            return fragmentManager.findOrCreatePicker(tag) {
                val arguments = Bundle()
                arguments.putBoolean(ARG_MULTI_CATEGORY, true)
                arguments.putParcelableArray(ARG_DEFAULT_CATEGORIES, defaultCategories)
                val picker = CategoryPicker()
                picker.arguments = arguments
                picker
            }
        }
    }
}
