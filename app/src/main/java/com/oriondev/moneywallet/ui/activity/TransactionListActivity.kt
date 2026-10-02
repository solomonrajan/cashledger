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

package com.oriondev.moneywallet.ui.activity

import android.content.Intent
import com.oriondev.moneywallet.ui.activity.base.MultiPanelActivity
import com.oriondev.moneywallet.ui.fragment.base.MultiPanelFragment
import com.oriondev.moneywallet.ui.fragment.multipanel.TransactionMultiPanelFragment
import java.util.Date

/**
 * Activity for displaying a list of transactions based on filter parameters.
 */
class TransactionListActivity : MultiPanelActivity() {

    override fun onCreateMultiPanelFragment(): MultiPanelFragment? {
        val currentIntent = intent ?: return null

        var type: TransactionMultiPanelFragment.FilterType? = null
        var itemId = 0L

        when {
            currentIntent.hasExtra(CATEGORY_ID) -> {
                type = TransactionMultiPanelFragment.FilterType.CATEGORY
                itemId = currentIntent.getLongExtra(CATEGORY_ID, 0L)
            }
            currentIntent.hasExtra(DEBT_ID) -> {
                type = TransactionMultiPanelFragment.FilterType.DEBT
                itemId = currentIntent.getLongExtra(DEBT_ID, 0L)
            }
            currentIntent.hasExtra(BUDGET_ID) -> {
                type = TransactionMultiPanelFragment.FilterType.BUDGET
                itemId = currentIntent.getLongExtra(BUDGET_ID, 0L)
            }
            currentIntent.hasExtra(SAVING_ID) -> {
                type = TransactionMultiPanelFragment.FilterType.SAVING
                itemId = currentIntent.getLongExtra(SAVING_ID, 0L)
            }
            currentIntent.hasExtra(EVENT_ID) -> {
                type = TransactionMultiPanelFragment.FilterType.EVENT
                itemId = currentIntent.getLongExtra(EVENT_ID, 0L)
            }
            currentIntent.hasExtra(PLACE_ID) -> {
                type = TransactionMultiPanelFragment.FilterType.PLACE
                itemId = currentIntent.getLongExtra(PLACE_ID, 0L)
            }
            currentIntent.hasExtra(PERSON_ID) -> {
                type = TransactionMultiPanelFragment.FilterType.PERSON
                itemId = currentIntent.getLongExtra(PERSON_ID, 0L)
            }
        }

        @Suppress("DEPRECATION")
        val startDate = currentIntent.getSerializableExtra(START_DATE) as? Date
        
        @Suppress("DEPRECATION")
        val endDate = currentIntent.getSerializableExtra(END_DATE) as? Date

        return TransactionMultiPanelFragment.newInstance(
            type,
            itemId,
            startDate,
            endDate
        )
    }

    override fun getMultiPanelFragmentTag(): String {
        return MULTI_PANEL_FRAGMENT_TAG
    }

    companion object {
        private const val MULTI_PANEL_FRAGMENT_TAG = "TransactionListActivity::Tag::MultiPanelFragment"

        const val START_DATE = "TransactionListActivity::Arguments::StartDate"
        const val END_DATE = "TransactionListActivity::Arguments::EndDate"

        const val CATEGORY_ID = "TransactionListActivity::Arguments::CategoryId"
        const val DEBT_ID = "TransactionListActivity::Arguments::DebtId"
        const val BUDGET_ID = "TransactionListActivity::Arguments::BudgetId"
        const val SAVING_ID = "TransactionListActivity::Arguments::SavingId"
        const val EVENT_ID = "TransactionListActivity::Arguments::EventId"
        const val PLACE_ID = "TransactionListActivity::Arguments::PlaceId"
        const val PERSON_ID = "TransactionListActivity::Arguments::PersonId"
    }
}
