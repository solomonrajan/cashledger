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

package com.oriondev.moneywallet.ui.fragment.multipanel

import android.content.BroadcastReceiver
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.viewModels
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.storage.preference.CurrentWalletController
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.fragment.base.MultiPanelFragment
import com.oriondev.moneywallet.ui.fragment.base.SecondaryPanelFragment
import com.oriondev.moneywallet.ui.fragment.secondary.TransactionItemFragment
import com.oriondev.moneywallet.ui.view.theme.MoneyWalletTheme
import java.util.Date

class TransactionMultiPanelFragment : MultiPanelFragment(), CurrentWalletController {

    private val viewModel: TransactionListViewModel by viewModels()
    private var broadcastReceiver: BroadcastReceiver? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        broadcastReceiver = PreferenceManager.registerCurrentWalletObserver(context, this)
    }

    override fun onDetach() {
        super.onDetach()
        PreferenceManager.unregisterCurrentWalletObserver(activity, broadcastReceiver)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val args = arguments
        if (args != null) {
            @Suppress("DEPRECATION")
            val type = args.getSerializable(FILTER_TYPE) as? FilterType
            val id = args.getLong(FILTER_ID)
            
            @Suppress("DEPRECATION")
            val startDate = args.getSerializable(FILTER_START_DATE) as? Date
            
            @Suppress("DEPRECATION")
            val endDate = args.getSerializable(FILTER_END_DATE) as? Date
            
            viewModel.initialize(type, id, startDate, endDate)
        } else {
            viewModel.initialize(null, 0L, null, null)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MoneyWalletTheme {
                    TransactionListScreen(
                        viewModel = viewModel,
                        onTransactionClick = { id ->
                            showItemId(id)
                            showSecondaryPanel()
                        }
                    )
                }
            }
        }
    }

    override fun onCreateSecondaryPanel(): SecondaryPanelFragment {
        return TransactionItemFragment()
    }

    override fun getSecondaryFragmentTag(): String {
        return SECONDARY_PANEL_TAG
    }

    override fun getTitleRes(): Int {
        return R.string.menu_transaction
    }

    override fun showsCurrentWallet(): Boolean {
        return true
    }

    override fun isFloatingActionButtonEnabled(): Boolean {
        // Floating action button for creating transactions is usually handled by parent
        return false
    }

    override fun onCurrentWalletChanged(walletId: Long) {
        viewModel.clearSelection()
        viewModel.refreshWallet()
    }

    enum class FilterType {
        CATEGORY,
        DEBT,
        BUDGET,
        SAVING,
        EVENT,
        PLACE,
        PERSON
    }

    companion object {
        private const val FILTER_TYPE = "TransactionMultiPanelFragment::Arguments::FilterType"
        private const val FILTER_ID = "TransactionMultiPanelFragment::Arguments::FilterId"
        private const val FILTER_START_DATE = "TransactionMultiPanelFragment::Arguments::FilterStartDate"
        private const val FILTER_END_DATE = "TransactionMultiPanelFragment::Arguments::FilterEndDate"
        private const val SECONDARY_PANEL_TAG = "TransactionMultiPanelFragment::Tag::SecondaryPanel"

        @JvmStatic
        fun newInstance(
            type: FilterType?,
            id: Long,
            startDate: Date?,
            endDate: Date?
        ): TransactionMultiPanelFragment {
            return TransactionMultiPanelFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(FILTER_TYPE, type)
                    putLong(FILTER_ID, id)
                    putSerializable(FILTER_START_DATE, startDate)
                    putSerializable(FILTER_END_DATE, endDate)
                }
            }
        }
    }
}
