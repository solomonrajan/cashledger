package com.oriondev.moneywallet.ui.fragment.multipanel

import android.app.Application
import android.content.ContentUris
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.Category
import com.oriondev.moneywallet.model.Group
import com.oriondev.moneywallet.model.Wallet
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.storage.wrapper.TransactionHeaderCursor
import com.oriondev.moneywallet.utils.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

sealed class TransactionListItem {
    data class Header(
        val startDate: Date,
        val endDate: Date,
        val groupType: Int,
        val moneyStr: String,
        val incomeStr: String,
        val expenseStr: String
    ) : TransactionListItem()

    data class Transaction(
        val id: Long,
        val type: Int,
        val direction: Int,
        val description: String,
        val date: Date,
        val money: Long,
        val currency: String,
        val categoryName: String,
        val categoryIcon: String,
        val walletId: Long,
        val walletCountInTotal: Int
    ) : TransactionListItem()
}

data class TransactionListUiState(
    val items: List<TransactionListItem> = emptyList(),
    val isLoading: Boolean = true,
    val emptyStateMessageRes: Int = R.string.message_no_transaction_found,
    val selectedItemIds: Set<Long> = emptySet(),
    val collapsedPeriods: Set<String> = emptySet()
)

class TransactionListViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(TransactionListUiState())
    val uiState: StateFlow<TransactionListUiState> = _uiState.asStateFlow()

    private var filterType: TransactionMultiPanelFragment.FilterType? = null
    private var filterId: Long = 0
    private var startDate: Date? = null
    private var endDate: Date? = null

    fun initialize(type: TransactionMultiPanelFragment.FilterType?, id: Long, start: Date?, end: Date?) {
        if (filterType == type && filterId == id && startDate == start && endDate == end) {
            return
        }
        filterType = type
        filterId = id
        startDate = start
        endDate = end

        viewModelScope.launch {
            _uiState.update { it.copy(collapsedPeriods = PreferenceManager.getCollapsedPeriods()) }
            observeTransactions().collectLatest { items ->
                val emptyMessageRes = if (items.isEmpty()) {
                    checkHiddenTransactions()
                } else {
                    R.string.message_no_transaction_found
                }
                _uiState.update { state ->
                    state.copy(
                        items = items,
                        isLoading = false,
                        emptyStateMessageRes = emptyMessageRes
                    )
                }
            }
        }
    }

    private fun buildItemTransactionsUri(): Uri {
        var uri = DataContentProvider.CONTENT_TRANSACTIONS
        val type = filterType ?: return uri
        uri = when (type) {
            TransactionMultiPanelFragment.FilterType.CATEGORY -> ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORIES, filterId)
            TransactionMultiPanelFragment.FilterType.DEBT -> ContentUris.withAppendedId(DataContentProvider.CONTENT_DEBTS, filterId)
            TransactionMultiPanelFragment.FilterType.BUDGET -> ContentUris.withAppendedId(DataContentProvider.CONTENT_BUDGETS, filterId)
            TransactionMultiPanelFragment.FilterType.SAVING -> ContentUris.withAppendedId(DataContentProvider.CONTENT_SAVINGS, filterId)
            TransactionMultiPanelFragment.FilterType.EVENT -> ContentUris.withAppendedId(DataContentProvider.CONTENT_EVENTS, filterId)
            TransactionMultiPanelFragment.FilterType.PLACE -> ContentUris.withAppendedId(DataContentProvider.CONTENT_PLACES, filterId)
            TransactionMultiPanelFragment.FilterType.PERSON -> ContentUris.withAppendedId(DataContentProvider.CONTENT_PEOPLE, filterId)
        }
        return Uri.withAppendedPath(uri, "transactions")
    }

    private fun buildDateRangeSelection(): String? {
        val builder = StringBuilder()
        if (startDate != null) {
            builder.append("DATETIME(").append(Contract.Transaction.DATE).append(") >= DATETIME('")
                .append(DateUtils.getSQLDateTimeString(startDate)).append("')")
        }
        if (endDate != null) {
            if (builder.isNotEmpty()) {
                builder.append(" AND ")
            }
            builder.append("DATETIME(").append(Contract.Transaction.DATE).append(") <= DATETIME('")
                .append(DateUtils.getSQLDateTimeString(endDate)).append("')")
        }
        return if (builder.isNotEmpty()) builder.toString() else null
    }

    private fun observeTransactions(): Flow<List<TransactionListItem>> = callbackFlow {
        val contentResolver = getApplication<Application>().contentResolver
        val uri = buildItemTransactionsUri()

        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                launch(Dispatchers.IO) {
                    trySend(fetchTransactions())
                }
            }
        }
        contentResolver.registerContentObserver(uri, true, observer)
        
        // Initial load
        launch(Dispatchers.IO) {
            trySend(fetchTransactions())
        }

        awaitClose {
            contentResolver.unregisterContentObserver(observer)
        }
    }

    private suspend fun fetchTransactions(): List<TransactionListItem> = withContext(Dispatchers.IO) {
        val uri = buildItemTransactionsUri()
        val currentWallet = PreferenceManager.getCurrentWallet()
        
        var selection = if (currentWallet == PreferenceManager.TOTAL_WALLET_ID) {
            "${Contract.Transaction.WALLET_COUNT_IN_TOTAL} = 1"
        } else {
            "${Contract.Transaction.WALLET_ID} = ?"
        }
        val selectionArgs = if (currentWallet == PreferenceManager.TOTAL_WALLET_ID) {
            null
        } else {
            arrayOf(currentWallet.toString())
        }
        
        selection += " AND DATETIME(${Contract.Transaction.DATE}) <= DATETIME('now', 'localtime')"
        
        val dateRange = buildDateRangeSelection()
        if (dateRange != null) {
            selection += " AND $dateRange"
        }
        val sortOrder = "${Contract.Transaction.DATE} DESC"
        val groupType = PreferenceManager.getCurrentGroupType()

        val items = mutableListOf<TransactionListItem>()
        
        getApplication<Application>().contentResolver.query(uri, null, selection, selectionArgs, sortOrder)?.use { rawCursor ->
            TransactionHeaderCursor(rawCursor, groupType, startDate, endDate).use { cursor ->
                val indexType = cursor.getColumnIndex(TransactionHeaderCursor.COLUMN_ITEM_TYPE)
                val indexHeaderStartDate = cursor.getColumnIndex(TransactionHeaderCursor.COLUMN_HEADER_START_DATE)
                val indexHeaderEndDate = cursor.getColumnIndex(TransactionHeaderCursor.COLUMN_HEADER_END_DATE)
                val indexHeaderMoney = cursor.getColumnIndex(TransactionHeaderCursor.COLUMN_HEADER_MONEY)
                val indexHeaderIncome = cursor.getColumnIndex(TransactionHeaderCursor.COLUMN_HEADER_INCOME)
                val indexHeaderExpense = cursor.getColumnIndex(TransactionHeaderCursor.COLUMN_HEADER_EXPENSE)
                val indexHeaderGroupType = cursor.getColumnIndex(TransactionHeaderCursor.COLUMN_HEADER_GROUP_TYPE)
                
                val indexCategoryName = cursor.getColumnIndex(Contract.Transaction.CATEGORY_NAME)
                val indexCategoryIcon = cursor.getColumnIndex(Contract.Transaction.CATEGORY_ICON)
                val indexTransactionId = cursor.getColumnIndex(Contract.Transaction.ID)
                val indexTransactionType = cursor.getColumnIndex(Contract.Transaction.TYPE)
                val indexTransactionDirection = cursor.getColumnIndex(Contract.Transaction.DIRECTION)
                val indexTransactionDescription = cursor.getColumnIndex(Contract.Transaction.DESCRIPTION)
                val indexTransactionDate = cursor.getColumnIndex(Contract.Transaction.DATE)
                val indexTransactionMoney = cursor.getColumnIndex(Contract.Transaction.MONEY)
                val indexCurrency = cursor.getColumnIndex(Contract.Transaction.WALLET_CURRENCY)
                val indexWalletId = cursor.getColumnIndex(Contract.Transaction.WALLET_ID)
                val indexWalletCount = cursor.getColumnIndex(Contract.Transaction.WALLET_COUNT_IN_TOTAL)

                for (i in 0 until cursor.count) {
                    cursor.moveToPosition(i)
                    val type = cursor.getInt(indexType)
                    if (type == TransactionHeaderCursor.TYPE_HEADER) {
                        items.add(
                            TransactionListItem.Header(
                                startDate = DateUtils.getDateFromSQLDateTimeString(cursor.getString(indexHeaderStartDate)),
                                endDate = DateUtils.getDateFromSQLDateTimeString(cursor.getString(indexHeaderEndDate)),
                                groupType = cursor.getInt(indexHeaderGroupType),
                                moneyStr = cursor.getString(indexHeaderMoney) ?: "",
                                incomeStr = cursor.getString(indexHeaderIncome) ?: "",
                                expenseStr = cursor.getString(indexHeaderExpense) ?: ""
                            )
                        )
                    } else {
                        items.add(
                            TransactionListItem.Transaction(
                                id = cursor.getLong(indexTransactionId),
                                type = cursor.getInt(indexTransactionType),
                                direction = cursor.getInt(indexTransactionDirection),
                                description = cursor.getString(indexTransactionDescription) ?: "",
                                date = DateUtils.getDateFromSQLDateTimeString(cursor.getString(indexTransactionDate)),
                                money = cursor.getLong(indexTransactionMoney),
                                currency = cursor.getString(indexCurrency) ?: "",
                                categoryName = cursor.getString(indexCategoryName) ?: "",
                                categoryIcon = cursor.getString(indexCategoryIcon) ?: "",
                                walletId = cursor.getLong(indexWalletId),
                                walletCountInTotal = cursor.getInt(indexWalletCount)
                            )
                        )
                    }
                }
            }
        }
        return@withContext items
    }

    private suspend fun checkHiddenTransactions(): Int = withContext(Dispatchers.IO) {
        val uri = buildItemTransactionsUri()
        val dateRange = buildDateRangeSelection()
        val projection = arrayOf(
            Contract.Transaction.WALLET_ID,
            Contract.Transaction.WALLET_COUNT_IN_TOTAL,
            Contract.Transaction.DATE
        )
        
        var otherWallets = false
        var future = false
        val currentWallet = PreferenceManager.getCurrentWallet()
        val now = DateUtils.getSQLDateTimeString(Date())

        getApplication<Application>().contentResolver.query(uri, projection, dateRange, null, null)?.use { cursor ->
            val indexWalletId = cursor.getColumnIndex(Contract.Transaction.WALLET_ID)
            val indexCountInTotal = cursor.getColumnIndex(Contract.Transaction.WALLET_COUNT_IN_TOTAL)
            val indexDate = cursor.getColumnIndex(Contract.Transaction.DATE)
            
            for (i in 0 until cursor.count) {
                if (otherWallets && future) break
                cursor.moveToPosition(i)
                
                val inSelectedWallet = if (currentWallet == PreferenceManager.TOTAL_WALLET_ID) {
                    cursor.getInt(indexCountInTotal) == 1
                } else {
                    cursor.getLong(indexWalletId) == currentWallet
                }
                
                if (!inSelectedWallet) {
                    otherWallets = true
                } else {
                    val dateStr = cursor.getString(indexDate)
                    if (dateStr != null && dateStr > now) {
                        future = true
                    }
                }
            }
        }
        
        return@withContext when {
            otherWallets && future -> R.string.message_no_transaction_found_other_wallets_and_future
            otherWallets -> R.string.message_no_transaction_found_other_wallets
            future -> R.string.message_no_transaction_found_future
            else -> R.string.message_no_transaction_found
        }
    }

    fun toggleSelection(id: Long, isSelectable: Boolean = true) {
        if (!isSelectable) return
        _uiState.update { state ->
            val newSelection = state.selectedItemIds.toMutableSet()
            if (!newSelection.remove(id)) {
                newSelection.add(id)
            }
            state.copy(selectedItemIds = newSelection)
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedItemIds = emptySet()) }
    }

    fun togglePeriod(key: String) {
        val stored = PreferenceManager.getCollapsedPeriods()
        if (!stored.remove(key)) {
            stored.add(key)
        }
        PreferenceManager.setCollapsedPeriods(stored)
        _uiState.update { it.copy(collapsedPeriods = stored) }
    }

    fun refreshWallet() {
        // Trigger a reload
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val items = fetchTransactions()
            val emptyMessageRes = if (items.isEmpty()) checkHiddenTransactions() else R.string.message_no_transaction_found
            _uiState.update { state ->
                state.copy(items = items, isLoading = false, emptyStateMessageRes = emptyMessageRes)
            }
        }
    }
}
