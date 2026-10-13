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
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.*
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.TransferContentValuesBuilder
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.utils.CurrencyManager
import com.oriondev.moneywallet.utils.DateUtils
import com.oriondev.moneywallet.utils.IconLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

fun NewEditTransferActivity.loadTransferData(
    mode: NewEditItemActivity.Mode,
    itemId: Long,
    onLoaded: (TransferScreenState) -> Unit
) {
    lifecycleScope.launch(Dispatchers.IO) {
        val titleRes = if (mode == NewEditItemActivity.Mode.EDIT_ITEM) {
            R.string.title_activity_edit_transfer
        } else {
            R.string.title_activity_new_transfer
        }
        var state = TransferScreenState(mode = mode, titleRes = titleRes)

        // Query all active wallets
        val walletsList = mutableListOf<Wallet>()
        val walletsUri = DataContentProvider.CONTENT_WALLETS
        val wProjection = arrayOf(
            Contract.Wallet.ID,
            Contract.Wallet.NAME,
            Contract.Wallet.ICON,
            Contract.Wallet.CURRENCY,
            Contract.Wallet.START_MONEY,
            Contract.Wallet.TOTAL_MONEY
        )
        val wSelection = "${Contract.Wallet.ARCHIVED} = 0"
        val wSortOrder = "${Contract.Wallet.INDEX} ASC, ${Contract.Wallet.NAME} ASC"
        contentResolver.query(walletsUri, wProjection, wSelection, null, wSortOrder)?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(Contract.Wallet.ID)
            val nameIdx = cursor.getColumnIndexOrThrow(Contract.Wallet.NAME)
            val iconIdx = cursor.getColumnIndexOrThrow(Contract.Wallet.ICON)
            val currencyIdx = cursor.getColumnIndexOrThrow(Contract.Wallet.CURRENCY)
            val startIdx = cursor.getColumnIndexOrThrow(Contract.Wallet.START_MONEY)
            val totalIdx = cursor.getColumnIndexOrThrow(Contract.Wallet.TOTAL_MONEY)
            while (cursor.moveToNext()) {
                val wCurrency = CurrencyManager.getCurrency(cursor.getString(currencyIdx))
                walletsList.add(
                    Wallet(
                        cursor.getLong(idIdx),
                        cursor.getString(nameIdx),
                        IconLoader.parse(cursor.getString(iconIdx)),
                        wCurrency,
                        cursor.getLong(startIdx),
                        cursor.getLong(totalIdx)
                    )
                )
            }
        }
        state = state.copy(availableWallets = walletsList)

        if (mode == NewEditItemActivity.Mode.EDIT_ITEM) {
            val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSFERS, itemId)
            val projection = arrayOf(
                Contract.Transfer.DESCRIPTION,
                Contract.Transfer.DATE,
                Contract.Transfer.TRANSACTION_FROM_WALLET_ID,
                Contract.Transfer.TRANSACTION_FROM_WALLET_NAME,
                Contract.Transfer.TRANSACTION_FROM_WALLET_ICON,
                Contract.Transfer.TRANSACTION_FROM_WALLET_CURRENCY,
                Contract.Transfer.TRANSACTION_TO_WALLET_ID,
                Contract.Transfer.TRANSACTION_TO_WALLET_NAME,
                Contract.Transfer.TRANSACTION_TO_WALLET_ICON,
                Contract.Transfer.TRANSACTION_TO_WALLET_CURRENCY,
                Contract.Transfer.TRANSACTION_FROM_MONEY,
                Contract.Transfer.TRANSACTION_TO_MONEY,
                Contract.Transfer.TRANSACTION_TAX_MONEY,
                Contract.Transfer.NOTE,
                Contract.Transfer.EVENT_ID,
                Contract.Transfer.EVENT_NAME,
                Contract.Transfer.EVENT_ICON,
                Contract.Transfer.EVENT_START_DATE,
                Contract.Transfer.EVENT_END_DATE,
                Contract.Transfer.PLACE_ID,
                Contract.Transfer.PLACE_NAME,
                Contract.Transfer.PLACE_ICON,
                Contract.Transfer.PLACE_ADDRESS,
                Contract.Transfer.PLACE_LATITUDE,
                Contract.Transfer.PLACE_LONGITUDE,
                Contract.Transfer.CONFIRMED,
                Contract.Transfer.COUNT_IN_TOTAL
            )
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val desc = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Transfer.DESCRIPTION)) ?: ""
                    val dateStr = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Transfer.DATE))
                    val date = DateUtils.getDateFromSQLDateTimeString(dateStr)

                    val fromWalletId = cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Transfer.TRANSACTION_FROM_WALLET_ID))
                    val fromWalletName = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Transfer.TRANSACTION_FROM_WALLET_NAME))
                    val fromWalletIcon = IconLoader.parse(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Transfer.TRANSACTION_FROM_WALLET_ICON)))
                    val fromWalletCurrency = CurrencyManager.getCurrency(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Transfer.TRANSACTION_FROM_WALLET_CURRENCY)))
                    val walletFrom = Wallet(fromWalletId, fromWalletName, fromWalletIcon, fromWalletCurrency, 0L, 0L)

                    val toWalletId = cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Transfer.TRANSACTION_TO_WALLET_ID))
                    val toWalletName = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Transfer.TRANSACTION_TO_WALLET_NAME))
                    val toWalletIcon = IconLoader.parse(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Transfer.TRANSACTION_TO_WALLET_ICON)))
                    val toWalletCurrency = CurrencyManager.getCurrency(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Transfer.TRANSACTION_TO_WALLET_CURRENCY)))
                    val walletTo = Wallet(toWalletId, toWalletName, toWalletIcon, toWalletCurrency, 0L, 0L)

                    val moneyFrom = cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Transfer.TRANSACTION_FROM_MONEY))
                    val tax = cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Transfer.TRANSACTION_TAX_MONEY))
                    val note = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Transfer.NOTE)) ?: ""

                    val confirmed = cursor.getInt(cursor.getColumnIndexOrThrow(Contract.Transfer.CONFIRMED)) == 1
                    val countInTotal = cursor.getInt(cursor.getColumnIndexOrThrow(Contract.Transfer.COUNT_IN_TOTAL)) == 1

                    var place: Place? = null
                    if (!cursor.isNull(cursor.getColumnIndexOrThrow(Contract.Transfer.PLACE_ID))) {
                        place = Place(
                            cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Transfer.PLACE_ID)),
                            cursor.getString(cursor.getColumnIndexOrThrow(Contract.Transfer.PLACE_NAME)),
                            IconLoader.parse(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Transfer.PLACE_ICON))),
                            cursor.getString(cursor.getColumnIndexOrThrow(Contract.Transfer.PLACE_ADDRESS)),
                            if (cursor.isNull(cursor.getColumnIndexOrThrow(Contract.Transfer.PLACE_LATITUDE))) null else cursor.getDouble(cursor.getColumnIndexOrThrow(Contract.Transfer.PLACE_LATITUDE)),
                            if (cursor.isNull(cursor.getColumnIndexOrThrow(Contract.Transfer.PLACE_LONGITUDE))) null else cursor.getDouble(cursor.getColumnIndexOrThrow(Contract.Transfer.PLACE_LONGITUDE))
                        )
                    }

                    var event: Event? = null
                    if (!cursor.isNull(cursor.getColumnIndexOrThrow(Contract.Transfer.EVENT_ID))) {
                        event = Event(
                            cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Transfer.EVENT_ID)),
                            cursor.getString(cursor.getColumnIndexOrThrow(Contract.Transfer.EVENT_NAME)),
                            IconLoader.parse(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Transfer.EVENT_ICON))),
                            DateUtils.getDateFromSQLDateString(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Transfer.EVENT_START_DATE))),
                            DateUtils.getDateFromSQLDateString(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Transfer.EVENT_END_DATE)))
                        )
                    }

                    state = state.copy(
                        money = moneyFrom,
                        tax = tax,
                        currency = fromWalletCurrency,
                        description = desc,
                        date = date,
                        walletFrom = walletFrom,
                        walletTo = walletTo,
                        event = event,
                        place = place,
                        note = note,
                        confirmed = confirmed,
                        countInTotal = countInTotal
                    )
                }
            }

            // Load People
            val peopleUri = Uri.withAppendedPath(uri, "people")
            val pProjection = arrayOf(Contract.Person.ID, Contract.Person.NAME, Contract.Person.ICON)
            val peopleList = mutableListOf<Person>()
            contentResolver.query(peopleUri, pProjection, null, null, null)?.use { pCursor ->
                val pIdIdx = pCursor.getColumnIndexOrThrow(Contract.Person.ID)
                val pNameIdx = pCursor.getColumnIndexOrThrow(Contract.Person.NAME)
                val pIconIdx = pCursor.getColumnIndexOrThrow(Contract.Person.ICON)
                while (pCursor.moveToNext()) {
                    peopleList.add(
                        Person(
                            pCursor.getLong(pIdIdx),
                            pCursor.getString(pNameIdx),
                            IconLoader.parse(pCursor.getString(pIconIdx))
                        )
                    )
                }
            }
            state = state.copy(people = peopleList)

            // Load Attachments
            val attachmentsUri = Uri.withAppendedPath(uri, "attachments")
            val aProjection = arrayOf(
                Contract.Attachment.ID,
                Contract.Attachment.FILE,
                Contract.Attachment.NAME,
                Contract.Attachment.TYPE,
                Contract.Attachment.SIZE
            )
            val attachmentList = mutableListOf<Attachment>()
            contentResolver.query(attachmentsUri, aProjection, null, null, null)?.use { aCursor ->
                val aIdIdx = aCursor.getColumnIndexOrThrow(Contract.Attachment.ID)
                val aFileIdx = aCursor.getColumnIndexOrThrow(Contract.Attachment.FILE)
                val aNameIdx = aCursor.getColumnIndexOrThrow(Contract.Attachment.NAME)
                val aTypeIdx = aCursor.getColumnIndexOrThrow(Contract.Attachment.TYPE)
                val aSizeIdx = aCursor.getColumnIndexOrThrow(Contract.Attachment.SIZE)
                while (aCursor.moveToNext()) {
                    attachmentList.add(
                        Attachment(
                            aCursor.getLong(aIdIdx),
                            aCursor.getString(aFileIdx),
                            aCursor.getString(aNameIdx),
                            aCursor.getString(aTypeIdx),
                            aCursor.getLong(aSizeIdx)
                        )
                    )
                }
            }
            state = state.copy(attachments = attachmentList)
        } else {
            // Mode.NEW_ITEM
            if (intent.getIntExtra(NewEditTransferActivity.TYPE, NewEditTransferActivity.TYPE_STANDARD) == NewEditTransferActivity.TYPE_MODEL) {
                val modelId = intent.getLongExtra(NewEditTransferActivity.MODEL_ID, 0L)
                val modelUri = ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSFER_MODELS, modelId)
                val mProjection = arrayOf(
                    Contract.TransferModel.DESCRIPTION,
                    Contract.TransferModel.WALLET_FROM_ID,
                    Contract.TransferModel.WALLET_FROM_NAME,
                    Contract.TransferModel.WALLET_FROM_ICON,
                    Contract.TransferModel.WALLET_FROM_CURRENCY,
                    Contract.TransferModel.WALLET_TO_ID,
                    Contract.TransferModel.WALLET_TO_NAME,
                    Contract.TransferModel.WALLET_TO_ICON,
                    Contract.TransferModel.WALLET_TO_CURRENCY,
                    Contract.TransferModel.MONEY_FROM,
                    Contract.TransferModel.MONEY_TO,
                    Contract.TransferModel.MONEY_TAX,
                    Contract.TransferModel.NOTE,
                    Contract.TransferModel.EVENT_ID,
                    Contract.TransferModel.EVENT_NAME,
                    Contract.TransferModel.EVENT_ICON,
                    Contract.TransferModel.EVENT_START_DATE,
                    Contract.TransferModel.EVENT_END_DATE,
                    Contract.TransferModel.PLACE_ID,
                    Contract.TransferModel.PLACE_NAME,
                    Contract.TransferModel.PLACE_ICON,
                    Contract.TransferModel.PLACE_ADDRESS,
                    Contract.TransferModel.PLACE_LATITUDE,
                    Contract.TransferModel.PLACE_LONGITUDE,
                    Contract.TransferModel.CONFIRMED,
                    Contract.TransferModel.COUNT_IN_TOTAL
                )
                contentResolver.query(modelUri, mProjection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val desc = cursor.getString(cursor.getColumnIndexOrThrow(Contract.TransferModel.DESCRIPTION)) ?: ""
                        val fromWalletId = cursor.getLong(cursor.getColumnIndexOrThrow(Contract.TransferModel.WALLET_FROM_ID))
                        val fromWalletName = cursor.getString(cursor.getColumnIndexOrThrow(Contract.TransferModel.WALLET_FROM_NAME))
                        val fromWalletIcon = IconLoader.parse(cursor.getString(cursor.getColumnIndexOrThrow(Contract.TransferModel.WALLET_FROM_ICON)))
                        val fromWalletCurrency = CurrencyManager.getCurrency(cursor.getString(cursor.getColumnIndexOrThrow(Contract.TransferModel.WALLET_FROM_CURRENCY)))
                        val walletFrom = Wallet(fromWalletId, fromWalletName, fromWalletIcon, fromWalletCurrency, 0L, 0L)

                        val toWalletId = cursor.getLong(cursor.getColumnIndexOrThrow(Contract.TransferModel.WALLET_TO_ID))
                        val toWalletName = cursor.getString(cursor.getColumnIndexOrThrow(Contract.TransferModel.WALLET_TO_NAME))
                        val toWalletIcon = IconLoader.parse(cursor.getString(cursor.getColumnIndexOrThrow(Contract.TransferModel.WALLET_TO_ICON)))
                        val toWalletCurrency = CurrencyManager.getCurrency(cursor.getString(cursor.getColumnIndexOrThrow(Contract.TransferModel.WALLET_TO_CURRENCY)))
                        val walletTo = Wallet(toWalletId, toWalletName, toWalletIcon, toWalletCurrency, 0L, 0L)

                        val moneyFrom = cursor.getLong(cursor.getColumnIndexOrThrow(Contract.TransferModel.MONEY_FROM))
                        val tax = cursor.getLong(cursor.getColumnIndexOrThrow(Contract.TransferModel.MONEY_TAX))
                        val note = cursor.getString(cursor.getColumnIndexOrThrow(Contract.TransferModel.NOTE)) ?: ""
                        val confirmed = cursor.getInt(cursor.getColumnIndexOrThrow(Contract.TransferModel.CONFIRMED)) == 1
                        val countInTotal = cursor.getInt(cursor.getColumnIndexOrThrow(Contract.TransferModel.COUNT_IN_TOTAL)) == 1

                        var place: Place? = null
                        if (!cursor.isNull(cursor.getColumnIndexOrThrow(Contract.TransferModel.PLACE_ID))) {
                            place = Place(
                                cursor.getLong(cursor.getColumnIndexOrThrow(Contract.TransferModel.PLACE_ID)),
                                cursor.getString(cursor.getColumnIndexOrThrow(Contract.TransferModel.PLACE_NAME)),
                                IconLoader.parse(cursor.getString(cursor.getColumnIndexOrThrow(Contract.TransferModel.PLACE_ICON))),
                                cursor.getString(cursor.getColumnIndexOrThrow(Contract.TransferModel.PLACE_ADDRESS)),
                                if (cursor.isNull(cursor.getColumnIndexOrThrow(Contract.TransferModel.PLACE_LATITUDE))) null else cursor.getDouble(cursor.getColumnIndexOrThrow(Contract.TransferModel.PLACE_LATITUDE)),
                                if (cursor.isNull(cursor.getColumnIndexOrThrow(Contract.TransferModel.PLACE_LONGITUDE))) null else cursor.getDouble(cursor.getColumnIndexOrThrow(Contract.TransferModel.PLACE_LONGITUDE))
                            )
                        }

                        var event: Event? = null
                        if (!cursor.isNull(cursor.getColumnIndexOrThrow(Contract.TransferModel.EVENT_ID))) {
                            event = Event(
                                cursor.getLong(cursor.getColumnIndexOrThrow(Contract.TransferModel.EVENT_ID)),
                                cursor.getString(cursor.getColumnIndexOrThrow(Contract.TransferModel.EVENT_NAME)),
                                IconLoader.parse(cursor.getString(cursor.getColumnIndexOrThrow(Contract.TransferModel.EVENT_ICON))),
                                DateUtils.getDateFromSQLDateString(cursor.getString(cursor.getColumnIndexOrThrow(Contract.TransferModel.EVENT_START_DATE))),
                                DateUtils.getDateFromSQLDateString(cursor.getString(cursor.getColumnIndexOrThrow(Contract.TransferModel.EVENT_END_DATE)))
                            )
                        }

                        state = state.copy(
                            money = moneyFrom,
                            tax = tax,
                            currency = fromWalletCurrency,
                            description = desc,
                            date = Date(),
                            walletFrom = walletFrom,
                            walletTo = walletTo,
                            event = event,
                            place = place,
                            note = note,
                            confirmed = confirmed,
                            countInTotal = countInTotal
                        )
                    }
                }
            } else {
                // Standard new transfer: set default from and to wallets
                val currentWalletPref = PreferenceManager.getCurrentWallet()
                val defaultWalletFrom = if (currentWalletPref != PreferenceManager.TOTAL_WALLET_ID) {
                    walletsList.find { it.id == currentWalletPref } ?: walletsList.firstOrNull()
                } else {
                    walletsList.firstOrNull()
                }

                val defaultWalletTo = walletsList.find { it.id != defaultWalletFrom?.id }

                state = state.copy(
                    walletFrom = defaultWalletFrom,
                    walletTo = defaultWalletTo,
                    currency = defaultWalletFrom?.currency ?: CurrencyManager.getDefaultCurrency(),
                    date = Date()
                )
            }
        }

        withContext(Dispatchers.Main) {
            onLoaded(state)
        }
    }
}

fun NewEditTransferActivity.saveTransferData(state: TransferScreenState, itemId: Long) {
    lifecycleScope.launch(Dispatchers.IO) {
        if (state.walletFrom == null || state.walletTo == null) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@saveTransferData, R.string.error_input_missing_wallet, Toast.LENGTH_SHORT).show()
            }
            return@launch
        }

        if (state.walletFrom.id == state.walletTo.id) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@saveTransferData, "Source and destination wallets must be different", Toast.LENGTH_SHORT).show()
            }
            return@launch
        }

        val builder = TransferContentValuesBuilder()
            .description(state.description)
            .date(DateUtils.getSQLDateTimeString(state.date ?: Date()))
            .fromWalletId(state.walletFrom.id)
            .toWalletId(state.walletTo.id)
            .taxWalletId(state.walletFrom.id)
            .fromMoney(state.money)
            .toMoney(state.money)
            .taxMoney(state.tax)
            .note(state.note)
            .confirmed(if (state.confirmed) 1 else 0)
            .countInTotal(if (state.countInTotal) 1 else 0)
            .peopleIds(Contract.getObjectIds(state.people))
            .attachmentIds(Contract.getObjectIds(state.attachments))

        state.place?.let { builder.placeId(it.id) }
        state.event?.let { builder.eventId(it.id) }

        val contentValues = builder.build()
        if (state.mode == NewEditItemActivity.Mode.NEW_ITEM) {
            contentResolver.insert(DataContentProvider.CONTENT_TRANSFERS, contentValues)
        } else {
            val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSFERS, itemId)
            contentResolver.update(uri, contentValues, null, null)
        }

        withContext(Dispatchers.Main) {
            cleanUpAttachments()
            setResult(android.app.Activity.RESULT_OK)
            finishSaveDatedAt(state.date)
        }
    }
}
