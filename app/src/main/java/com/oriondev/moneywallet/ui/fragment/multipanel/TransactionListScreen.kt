@file:Suppress("LongMethod", "FunctionNaming", "MaxLineLength", "UnusedPrivateProperty")
package com.oriondev.moneywallet.ui.fragment.multipanel

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.Money
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.view.theme.ThemeEngine
import com.oriondev.moneywallet.utils.CurrencyManager
import com.oriondev.moneywallet.utils.DateFormatter
import com.oriondev.moneywallet.utils.DateUtils
import com.oriondev.moneywallet.utils.MoneyFormatter
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TransactionListScreen(
    viewModel: TransactionListViewModel,
    onTransactionClick: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val isSelectionMode = uiState.selectedItemIds.isNotEmpty()

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text(text = "${uiState.selectedItemIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = { /* TODO delete action */ }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (uiState.isLoading) {
                // Could show a loading indicator here, or just empty list for now.
            } else if (uiState.items.isEmpty()) {
                Text(
                    text = stringResource(id = uiState.emptyStateMessageRes),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(
                        items = uiState.items,
                        key = { item ->
                            when (item) {
                                is TransactionListItem.Header -> "header_${item.groupType}_${item.startDate.time}"
                                is TransactionListItem.Transaction -> "tx_${item.id}"
                            }
                        }
                    ) { item ->
                        when (item) {
                            is TransactionListItem.Header -> {
                                val key = "${item.groupType}:${DateUtils.getSQLDateTimeString(item.startDate)}"
                                val isCollapsed = uiState.collapsedPeriods.contains(key)
                                HeaderRow(
                                    item = item,
                                    onClick = { viewModel.togglePeriod(key) }
                                )
                            }
                            is TransactionListItem.Transaction -> {
                                val isSelected = uiState.selectedItemIds.contains(item.id)
                                TransactionRow(
                                    item = item,
                                    isSelected = isSelected,
                                    onClick = {
                                        if (isSelectionMode) {
                                            viewModel.toggleSelection(item.id, item.type != Contract.TransactionType.TRANSFER)
                                        } else {
                                            onTransactionClick(item.id)
                                        }
                                    },
                                    onLongClick = {
                                        viewModel.toggleSelection(item.id, item.type != Contract.TransactionType.TRANSFER)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HeaderRow(
    item: TransactionListItem.Header,
    onClick: () -> Unit
) {
    // Attempting to match legacy header UI (left date text, right totals)
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val dateStr = DateFormatter.getDateRange(androidx.compose.ui.platform.LocalContext.current, item.startDate, item.endDate)
            Text(
                text = dateStr,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Column(horizontalAlignment = Alignment.End) {
                // Money string logic matching legacy adapter
                val moneyObj = Money.parse(item.moneyStr)
                val formatter = MoneyFormatter.getInstance()
                
                Text(
                    text = formatter.getNotTintedString(moneyObj),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Row {
                    val incomeObj = Money.parse(item.incomeStr)
                    val expenseObj = Money.parse(item.expenseStr)
                    
                    val incomeMoney = if (incomeObj.numberOfCurrencies > 0) incomeObj else zeroMoney(moneyObj)
                    val expenseMoney = if (expenseObj.numberOfCurrencies > 0) expenseObj else zeroMoney(moneyObj)

                    Text(
                        text = formatter.getNotTintedString(incomeMoney),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(ThemeEngine.getTheme().getVisibleColor(
                            PreferenceManager.getCurrentIncomeColor(), PreferenceManager.getDefaultColorIncome()
                        ))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatter.getNotTintedString(expenseMoney),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(ThemeEngine.getTheme().getVisibleColor(
                            PreferenceManager.getCurrentExpenseColor(), PreferenceManager.getDefaultColorExpense()
                        ))
                    )
                }
            }
        }
    }
}

fun zeroMoney(counted: Money): Money {
    if (counted.numberOfCurrencies == 0) return Money()
    val zero = Money()
    counted.currencies.forEach { currency ->
        zero.addMoney(currency, 0)
    }
    return zero
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionRow(
    item: TransactionListItem.Transaction,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val bgColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon logic (simplified for Compose, usually uses IconLoader which requires context/ImageView)
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small),
            contentAlignment = Alignment.Center
        ) {
            // TODO replace with actual IconLoader logic adapted to Compose
            Text(item.categoryName.take(1).uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.categoryName,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.description.isNotBlank()) {
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            val currency = CurrencyManager.getCurrency(item.currency)
            val formatter = MoneyFormatter.getInstance()
            val moneyStr = formatter.getNotTintedString(currency, item.money)
            
            val isIncome = item.direction == Contract.Direction.INCOME
            val moneyColor = if (isIncome) {
                Color(ThemeEngine.getTheme().getVisibleColor(
                    PreferenceManager.getCurrentIncomeColor(), PreferenceManager.getDefaultColorIncome()
                ))
            } else {
                Color(ThemeEngine.getTheme().getVisibleColor(
                    PreferenceManager.getCurrentExpenseColor(), PreferenceManager.getDefaultColorExpense()
                ))
            }

            Text(
                text = moneyStr,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = moneyColor
            )
            Text(
                text = DateFormatter.getFormattedDateTime(item.date),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
