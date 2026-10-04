@file:Suppress(
    "TooManyFunctions",
    "WildcardImport",
    "MaxLineLength",
    "UnusedPrivateProperty",
    "LongMethod",
    "CyclomaticComplexMethod",
    "MagicNumber"
)

package com.oriondev.moneywallet.ui.activity

import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.*
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.TransactionContentValuesBuilder
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.utils.CurrencyManager
import com.oriondev.moneywallet.utils.DateUtils
import com.oriondev.moneywallet.utils.IconLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

fun NewEditTransactionActivity.loadTransactionData(mode: NewEditItemActivity.Mode, itemId: Long, onLoaded: (TransactionScreenState) -> Unit) {
    lifecycleScope.launch(Dispatchers.IO) {
        var state = TransactionScreenState(mode = mode)
        val duplicateId = if (mode == NewEditItemActivity.Mode.NEW_ITEM) intent.getLongExtra(NewEditTransactionActivity.DUPLICATE_ID, 0L) else 0L
        val duplicating = duplicateId > 0L // Assuming plain transaction for now
        
        if (duplicating) {
            state = state.copy(date = Date())
        }

        if (mode == NewEditItemActivity.Mode.EDIT_ITEM || duplicating) {
            val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSACTIONS, if (duplicating) duplicateId else itemId)
            val projection = arrayOf(
                Contract.Transaction.MONEY, Contract.Transaction.DATE, Contract.Transaction.DESCRIPTION,
                Contract.Transaction.CATEGORY_ID, Contract.Transaction.CATEGORY_NAME, Contract.Transaction.CATEGORY_ICON,
                Contract.Transaction.CATEGORY_TYPE, Contract.Transaction.CATEGORY_TAG, Contract.Transaction.TYPE,
                Contract.Transaction.WALLET_ID, Contract.Transaction.WALLET_NAME, Contract.Transaction.WALLET_ICON,
                Contract.Transaction.WALLET_CURRENCY, Contract.Transaction.NOTE, Contract.Transaction.CONFIRMED,
                Contract.Transaction.COUNT_IN_TOTAL
            )
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val money = cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Transaction.MONEY))
                    val dateStr = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Transaction.DATE))
                    val desc = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Transaction.DESCRIPTION)) ?: ""
                    val note = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Transaction.NOTE)) ?: ""
                    val confirmed = cursor.getInt(cursor.getColumnIndexOrThrow(Contract.Transaction.CONFIRMED)) == 1
                    val countInTotal = cursor.getInt(cursor.getColumnIndexOrThrow(Contract.Transaction.COUNT_IN_TOTAL)) == 1
                    
                    val catId = cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Transaction.CATEGORY_ID))
                    val catName = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Transaction.CATEGORY_NAME))
                    val catIcon = IconLoader.parse(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Transaction.CATEGORY_ICON)))
                    val catType = Contract.CategoryType.fromValue(cursor.getInt(cursor.getColumnIndexOrThrow(Contract.Transaction.CATEGORY_TYPE)))
                    val catTag = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Transaction.CATEGORY_TAG))
                    
                    val walletId = cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Transaction.WALLET_ID))
                    val walletName = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Transaction.WALLET_NAME))
                    val walletIcon = IconLoader.parse(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Transaction.WALLET_ICON)))
                    val walletCurrency = CurrencyManager.getCurrency(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Transaction.WALLET_CURRENCY)))

                    state = state.copy(
                        money = money,
                        date = if (!duplicating) DateUtils.getDateFromSQLDateTimeString(dateStr) else state.date,
                        description = desc,
                        note = note,
                        confirmed = confirmed,
                        countInTotal = countInTotal,
                        category = Category(catId, catName, catIcon, catType, catTag),
                        wallet = Wallet(walletId, walletName, walletIcon, walletCurrency, 0L, 0L),
                        currency = walletCurrency
                    )
                }
            }
            
            // Load Attachments
            if (!duplicating) {
                val attachmentsUri = Uri.withAppendedPath(uri, "attachments")
                val attProjection = arrayOf(Contract.Attachment.ID, Contract.Attachment.FILE, Contract.Attachment.NAME, Contract.Attachment.TYPE, Contract.Attachment.SIZE)
                val attachmentsList = mutableListOf<Attachment>()
                contentResolver.query(attachmentsUri, attProjection, null, null, null)?.use { cursor ->
                    while (cursor.moveToNext()) {
                        attachmentsList.add(Attachment(
                            cursor.getLong(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getLong(4)
                        ))
                    }
                }
                state = state.copy(attachments = attachmentsList)
            }
        } else {
            // New Item
            val type = intent.getIntExtra(NewEditTransactionActivity.TYPE, NewEditTransactionActivity.TYPE_STANDARD)
            if (type == NewEditTransactionActivity.TYPE_STANDARD) {
                val currentWallet = intent.getLongExtra(NewEditTransactionActivity.WALLET_ID, PreferenceManager.getCurrentWallet())
                val wUri = if (currentWallet == PreferenceManager.TOTAL_WALLET_ID) DataContentProvider.CONTENT_WALLETS else ContentUris.withAppendedId(DataContentProvider.CONTENT_WALLETS, currentWallet)
                contentResolver.query(wUri, arrayOf(Contract.Wallet.ID, Contract.Wallet.NAME, Contract.Wallet.ICON, Contract.Wallet.CURRENCY), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val walletCurrency = CurrencyManager.getCurrency(cursor.getString(3))
                        state = state.copy(
                            wallet = Wallet(cursor.getLong(0), cursor.getString(1), IconLoader.parse(cursor.getString(2)), walletCurrency, 0L, 0L),
                            currency = walletCurrency
                        )
                    }
                }
            }
            state = state.copy(date = Date())
        }
        
        withContext(Dispatchers.Main) {
            onLoaded(state)
        }
    }
}

fun NewEditTransactionActivity.saveTransactionData(state: TransactionScreenState, itemId: Long) {
    lifecycleScope.launch(Dispatchers.IO) {
        if (state.category == null || state.wallet == null || state.date == null) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@saveTransactionData, R.string.error_input_missing_category, Toast.LENGTH_SHORT).show()
            }
            return@launch
        }
        
        val builder = TransactionContentValuesBuilder()
            .money(state.money)
            .date(DateUtils.getSQLDateTimeString(state.date))
            .description(state.description)
            .categoryId(state.category.id)
            .walletId(state.wallet.id)
            .note(state.note)
            .confirmed(if (state.confirmed) 1 else 0)
            .countInTotal(if (state.countInTotal) 1 else 0)
            
        state.place?.let { builder.placeId(it.id) }
        state.event?.let { builder.eventId(it.id) }
        
        if (state.mode == NewEditItemActivity.Mode.NEW_ITEM) {
            val insertedUri = contentResolver.insert(DataContentProvider.CONTENT_TRANSACTIONS, builder.build())
            // Save people and attachments
        } else {
            val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSACTIONS, itemId)
            contentResolver.update(uri, builder.build(), null, null)
        }
        
        withContext(Dispatchers.Main) {
            finish()
        }
    }
}
