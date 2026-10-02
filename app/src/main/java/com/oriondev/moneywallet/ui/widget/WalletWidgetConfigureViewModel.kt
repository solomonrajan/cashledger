package com.oriondev.moneywallet.ui.widget

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oriondev.moneywallet.model.Wallet
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.utils.CurrencyManager
import com.oriondev.moneywallet.utils.IconLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class WidgetConfigureUiState(
    val selectedWallet: Wallet? = null,
    val showWhenLocked: Boolean = false,
    val availableWallets: List<Wallet> = emptyList(),
    val isPickerOpen: Boolean = false
)

class WalletWidgetConfigureViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(WidgetConfigureUiState())
    val uiState: StateFlow<WidgetConfigureUiState> = _uiState.asStateFlow()

    fun initialize(context: Context, appWidgetId: Int) {
        viewModelScope.launch {
            val showLocked = WalletWidgetPreferences.isShowWhenLocked(context, appWidgetId)
            val walletId = WalletWidgetPreferences.getWallet(context, appWidgetId)
            
            val wallets = fetchAllWallets(context)
            val selected = wallets.find { it.id == walletId }
            
            _uiState.update { it.copy(
                showWhenLocked = showLocked,
                selectedWallet = selected,
                availableWallets = wallets
            ) }
        }
    }

    fun onWalletSelected(wallet: Wallet) {
        _uiState.update { it.copy(selectedWallet = wallet, isPickerOpen = false) }
    }

    fun onShowWhenLockedChanged(checked: Boolean) {
        _uiState.update { it.copy(showWhenLocked = checked) }
    }

    fun setPickerOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isPickerOpen = isOpen) }
    }

    private suspend fun fetchAllWallets(context: Context): List<Wallet> = withContext(Dispatchers.IO) {
        val wallets = mutableListOf<Wallet>()
        val projection = arrayOf(
            Contract.Wallet.ID,
            Contract.Wallet.NAME,
            Contract.Wallet.ICON,
            Contract.Wallet.CURRENCY,
            Contract.Wallet.START_MONEY,
            Contract.Wallet.TOTAL_MONEY
        )
        val cursor = context.contentResolver.query(
            DataContentProvider.CONTENT_WALLETS, projection, null, null, null
        ) ?: return@withContext emptyList()
        
        cursor.use {
            val idIdx = it.getColumnIndexOrThrow(Contract.Wallet.ID)
            val nameIdx = it.getColumnIndexOrThrow(Contract.Wallet.NAME)
            val iconIdx = it.getColumnIndexOrThrow(Contract.Wallet.ICON)
            val currIdx = it.getColumnIndexOrThrow(Contract.Wallet.CURRENCY)
            val startIdx = it.getColumnIndexOrThrow(Contract.Wallet.START_MONEY)
            val totalIdx = it.getColumnIndexOrThrow(Contract.Wallet.TOTAL_MONEY)
            
            while (it.moveToNext()) {
                wallets.add(
                    Wallet(
                        it.getLong(idIdx),
                        it.getString(nameIdx),
                        IconLoader.parse(it.getString(iconIdx)),
                        CurrencyManager.getCurrency(it.getString(currIdx)),
                        it.getLong(startIdx),
                        it.getLong(totalIdx)
                    )
                )
            }
        }
        wallets
    }
}
