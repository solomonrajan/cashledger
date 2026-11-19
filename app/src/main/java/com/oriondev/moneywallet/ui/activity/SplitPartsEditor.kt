/*
 * Copyright (c) 2026.
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
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.Category
import com.oriondev.moneywallet.picker.CategoryPicker
import com.oriondev.moneywallet.picker.MoneyPicker
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.SplitPartsCodec
import com.oriondev.moneywallet.ui.view.text.MaterialEditText
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog
import com.oriondev.moneywallet.utils.MoneyFormatter

/**
 * [mPartCategoryTarget] and [mPartMoneyTarget] hold the part each part picker writes
 * into, the size of the list while one is being added and -1 for none. One each, because a
 * picker hands its last value back again after a recreate, and it has to land on the part it was
 * opened for.
 */
internal class SplitPartsEditor(
    private val mActivity: Activity,
    private val mSplitPartsLayout: ViewGroup,
    private val mSplitRemainingEditText: MaterialEditText,
    private val mSplitEditText: MaterialEditText,
    private val mCategoryEditText: MaterialEditText
) {
    companion object {
        private const val SS_PART_CATEGORIES = "NewEditTransactionActivity::SavedState::PartCategories"
        private const val SS_PART_MONEY = "NewEditTransactionActivity::SavedState::PartMoney"
        private const val SS_PART_NOTES = "NewEditTransactionActivity::SavedState::PartNotes"
        private const val SS_PART_CATEGORY_TARGET = "NewEditTransactionActivity::SavedState::PartCategoryTarget"
        private const val SS_PART_MONEY_TARGET = "NewEditTransactionActivity::SavedState::PartMoneyTarget"

        private fun nameOf(category: Category?): String {
            return category?.name ?: "?"
        }

        private fun partTypeOf(main: Category): Contract.CategoryType {
            return if (main.direction == Contract.Direction.INCOME) {
                Contract.CategoryType.INCOME
            } else {
                Contract.CategoryType.EXPENSE
            }
        }
    }

    private var mRules: TransactionEditorRules? = null
    private var mMoneyPicker: MoneyPicker? = null
    private var mCategoryPicker: CategoryPicker? = null
    private var mPartCategoryPicker: CategoryPicker? = null
    private var mPartMoneyPicker: MoneyPicker? = null

    private val mParts = ArrayList<Part>()

    private var mPartCategoryTarget = -1
    private var mPartMoneyTarget = -1

    private val mMoneyFormatter = MoneyFormatter.getInstance()

    init {
        mSplitRemainingEditText.setTextViewMode(true)
        mSplitRemainingEditText.isClickable = false
        mSplitEditText.setTextViewMode(true)
        mSplitEditText.setOnClickListener {
            pickPartCategory(mParts.size)
        }
    }

    fun attach(
        rules: TransactionEditorRules,
        moneyPicker: MoneyPicker,
        categoryPicker: CategoryPicker,
        partCategoryPicker: CategoryPicker,
        partMoneyPicker: MoneyPicker
    ) {
        mRules = rules
        mMoneyPicker = moneyPicker
        mCategoryPicker = categoryPicker
        mPartCategoryPicker = partCategoryPicker
        mPartMoneyPicker = partMoneyPicker
        showParts()
    }

    fun addPart(category: Category, money: Long, note: String?) {
        mParts.add(Part(category, money, note))
    }

    @Suppress("DEPRECATION")
    fun restoreState(savedInstanceState: Bundle) {
        val partCategories: ArrayList<Category>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            savedInstanceState.getParcelableArrayList(SS_PART_CATEGORIES, Category::class.java)
        } else {
            savedInstanceState.getParcelableArrayList(SS_PART_CATEGORIES)
        }
        val partMoney = savedInstanceState.getLongArray(SS_PART_MONEY)
        val partNotes = savedInstanceState.getStringArray(SS_PART_NOTES)
        if (partCategories != null && partMoney != null && partNotes != null) {
            val count = minOf(partCategories.size, partMoney.size, partNotes.size)
            for (i in 0 until count) {
                val cat = partCategories[i]
                if (cat != null) {
                    mParts.add(Part(cat, partMoney[i], partNotes[i]))
                }
            }
        }
        mPartCategoryTarget = savedInstanceState.getInt(SS_PART_CATEGORY_TARGET, -1)
        mPartMoneyTarget = savedInstanceState.getInt(SS_PART_MONEY_TARGET, -1)
    }

    fun saveState(outState: Bundle) {
        val partCategories = ArrayList<Category>()
        val partMoney = LongArray(mParts.size)
        val partNotes = arrayOfNulls<String>(mParts.size)
        for (i in mParts.indices) {
            val part = mParts[i]
            partCategories.add(part.category)
            partMoney[i] = part.money
            partNotes[i] = part.note
        }
        outState.putParcelableArrayList(SS_PART_CATEGORIES, partCategories)
        outState.putLongArray(SS_PART_MONEY, partMoney)
        outState.putStringArray(SS_PART_NOTES, partNotes)
        outState.putInt(SS_PART_CATEGORY_TARGET, mPartCategoryTarget)
        outState.putInt(SS_PART_MONEY_TARGET, mPartMoneyTarget)
    }

    /**
     * Draws the parts, what they leave under the main category, and the action that adds one.
     * The amounts are minor units of the wallet on screen, the same ones the total is held in, so
     * a wallet of another currency shows the same digits and nothing is rescaled.
     */
    fun showParts() {
        val rules = mRules
        val offered = rules?.offersSplit() == true
        val split = offered && mParts.isNotEmpty()
        mSplitEditText.visibility = if (offered) View.VISIBLE else View.GONE
        mSplitPartsLayout.visibility = if (split) View.VISIBLE else View.GONE
        mSplitRemainingEditText.visibility = if (split) View.VISIBLE else View.GONE
        if (!offered) {
            return
        }
        mSplitEditText.setHint(if (split) R.string.hint_split_add_part else R.string.hint_split_into_categories)
        mSplitPartsLayout.removeAllViews()
        val currency = mMoneyPicker?.currentCurrency
        var remaining = mMoneyPicker?.currentMoney ?: 0L
        for (i in mParts.indices) {
            val part = mParts[i]
            val view = mActivity.layoutInflater.inflate(
                R.layout.layout_split_part, mSplitPartsLayout, false
            ) as MaterialEditText
            view.setTextViewMode(true)
            view.floatingLabelText = nameOf(part.category)
            val moneyStr = mMoneyFormatter.getNotTintedString(currency, part.money)
            val note = part.note
            view.setText(
                if (note.isNullOrEmpty()) {
                    moneyStr
                } else {
                    mActivity.getString(R.string.split_part, moneyStr, note)
                }
            )
            view.setOnClickListener {
                showPartActions(i)
            }
            mSplitPartsLayout.addView(view)
            remaining -= part.money
        }
        val mainCat = mCategoryPicker?.currentCategory
        mSplitRemainingEditText.floatingLabelText = mActivity.getString(
            R.string.hint_split_remaining,
            nameOf(mainCat)
        )
        mSplitRemainingEditText.setText(mMoneyFormatter.getNotTintedString(currency, remaining))
    }

    private fun showPartActions(index: Int) {
        if (index !in mParts.indices) {
            return
        }
        val actions = arrayOf(
            mActivity.getString(R.string.hint_category),
            mActivity.getString(R.string.hint_money),
            mActivity.getString(R.string.hint_note)
        )
        ThemedDialog.buildMaterialDialog(mActivity)
            .setTitle(nameOf(mParts[index].category))
            .setItems(actions) { _, which ->
                if (index < mParts.size) {
                    when (which) {
                        0 -> pickPartCategory(index)
                        1 -> pickPartMoney(index)
                        2 -> editPartNote(index)
                    }
                }
            }
            .setNeutralButton(R.string.action_remove) { _, _ ->
                if (index < mParts.size) {
                    mParts.removeAt(index)
                    mPartCategoryTarget = -1
                    mPartMoneyTarget = -1
                    showParts()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Opens the categories of the main category's type alone, for the part at this index or, at
     * the size of the list, for a new one, which is added once a category comes back.
     */
    private fun pickPartCategory(index: Int) {
        val main = mCategoryPicker?.currentCategory
        if (main == null) {
            mCategoryEditText.validate()
            return
        }
        val partCategoryPicker = mPartCategoryPicker ?: return
        mPartCategoryTarget = index
        partCategoryPicker.setCategory(if (index < mParts.size) mParts[index].category else null)
        partCategoryPicker.showPicker(partTypeOf(main))
    }

    private fun pickPartMoney(index: Int) {
        if (index !in mParts.indices) {
            return
        }
        val partMoneyPicker = mPartMoneyPicker ?: return
        val moneyPicker = mMoneyPicker ?: return
        mPartMoneyTarget = index
        partMoneyPicker.setCurrencyAndMoney(moneyPicker.currentCurrency, mParts[index].money)
        partMoneyPicker.showPicker()
    }

    private fun editPartNote(index: Int) {
        if (index !in mParts.indices) {
            return
        }
        val inputView = LayoutInflater.from(mActivity).inflate(R.layout.dialog_input, null)
        val inputEditText = inputView.findViewById<EditText>(R.id.dialog_input_edit_text)
        inputEditText.setHint(R.string.hint_note)
        inputEditText.setText(mParts[index].note)
        val dialog = ThemedDialog.buildMaterialDialog(mActivity)
            .setTitle(R.string.hint_note)
            .setView(inputView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (index < mParts.size) {
                    val note = inputEditText.text?.toString()?.trim()
                    mParts[index].note = if (note.isNullOrEmpty()) null else note
                    showParts()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        ThemedDialog.showWithInput(dialog, inputEditText, true)
    }

    fun onPartCategoryChanged(category: Category?) {
        if (category == null || mPartCategoryTarget < 0 || mPartCategoryTarget > mParts.size) {
            return
        }
        if (mPartCategoryTarget == mParts.size) {
            mParts.add(Part(category, 0L, null))
            showParts()
            pickPartMoney(mPartCategoryTarget)
        } else {
            mParts[mPartCategoryTarget].category = category
            showParts()
        }
    }

    fun onPartMoneyChanged(money: Long) {
        if (mPartMoneyTarget in mParts.indices) {
            mParts[mPartMoneyTarget].money = money
            showParts()
        }
    }

    /**
     * Refuses parts the ledger would not count, each reason with its own message. A part in a
     * category of the other type is how a direction flip on a split row is stopped, since the
     * direction follows the main category and the parts keep theirs.
     */
    fun validate(): Boolean {
        if (mRules?.offersSplit() != true || mParts.isEmpty()) {
            return true
        }
        val mainCat = mCategoryPicker?.currentCategory
        val type = if (mainCat != null) partTypeOf(mainCat) else null
        var error = 0
        var sum = 0L
        for (part in mParts) {
            if (part.money <= 0L) {
                error = R.string.error_split_part_not_positive
                break
            }
            if (type == null || part.category.type != type) {
                error = R.string.error_split_part_wrong_type
                break
            }
            sum = if (part.money > Long.MAX_VALUE - sum) Long.MAX_VALUE else sum + part.money
        }
        val currentMoney = mMoneyPicker?.currentMoney ?: 0L
        if (error == 0 && sum > currentMoney) {
            error = R.string.error_split_total_below_parts
        }
        if (error == 0) {
            return true
        }
        ThemedDialog.buildMaterialDialog(mActivity)
            .setTitle(R.string.title_error)
            .setMessage(error)
            .setPositiveButton(android.R.string.ok, null)
            .show()
        return false
    }

    fun encodeParts(): String {
        val parts = ArrayList<SplitPartsCodec.Part>()
        for (part in mParts) {
            parts.add(SplitPartsCodec.Part(part.category.id, part.money, part.note))
        }
        return SplitPartsCodec.encode(parts)
    }

    private class Part(
        var category: Category,
        var money: Long,
        var note: String?
    )
}
