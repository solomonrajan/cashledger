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

@file:Suppress(
    "LongMethod",
    "LongParameterList",
    "MatchingDeclarationName",
    "FunctionNaming",
    "WildcardImport",
    "MaxLineLength",
    "MagicNumber",
    "UnusedParameter",
    "CyclomaticComplexMethod",
    "TooManyFunctions",
    "SwallowedException"
)

package com.oriondev.moneywallet.ui.activity

import android.app.Activity
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.CurrencyUnit
import com.oriondev.moneywallet.model.Event
import com.oriondev.moneywallet.model.Place
import com.oriondev.moneywallet.model.Wallet
import com.oriondev.moneywallet.picker.EventPicker
import com.oriondev.moneywallet.picker.MoneyPicker
import com.oriondev.moneywallet.picker.PlacePicker
import com.oriondev.moneywallet.picker.WalletPicker
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.utils.CurrencyManager
import com.oriondev.moneywallet.utils.DateUtils
import com.oriondev.moneywallet.utils.IconLoader
import com.oriondev.moneywallet.utils.MoneyFormatter

/**
 * Modern Jetpack Compose & Kotlin implementation of NewEditTransferModelActivity.
 * Creates and edits transfer template models that can be instantiated into transfers.
 */
class NewEditTransferModelActivity : AppCompatActivity(),
    MoneyPicker.Controller,
    WalletPicker.SingleWalletController,
    EventPicker.Controller,
    PlacePicker.Controller {

    private lateinit var moneyPicker: MoneyPicker
    private lateinit var taxPicker: MoneyPicker
    private lateinit var walletFromPicker: WalletPicker
    private lateinit var walletToPicker: WalletPicker
    private lateinit var eventPicker: EventPicker
    private lateinit var placePicker: PlacePicker

    // Observable UI State
    private var money by mutableLongStateOf(0L)
    private var tax by mutableLongStateOf(0L)
    private var currency by mutableStateOf<CurrencyUnit?>(null)
    private var description by mutableStateOf("")
    private var walletFrom by mutableStateOf<Wallet?>(null)
    private var walletTo by mutableStateOf<Wallet?>(null)
    private var event by mutableStateOf<Event?>(null)
    private var place by mutableStateOf<Place?>(null)
    private var note by mutableStateOf("")
    private var confirmed by mutableStateOf(true)
    private var countInTotal by mutableStateOf(true)

    // Validation State
    private var walletFromError by mutableStateOf(false)
    private var walletToError by mutableStateOf(false)
    private var showDeleteDialog by mutableStateOf(false)

    private var mode = NewEditItemActivity.Mode.NEW_ITEM
    private var itemId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val intentMode = intent?.getSerializableExtra(NewEditItemActivity.MODE) as? NewEditItemActivity.Mode
        if (intentMode != null) {
            mode = intentMode
        }
        itemId = intent?.getLongExtra(NewEditItemActivity.ID, -1L) ?: -1L

        if (savedInstanceState == null) {
            if (mode == NewEditItemActivity.Mode.EDIT_ITEM && itemId > 0L) {
                loadExistingModel()
            } else {
                loadDefaultWallet()
            }
        }

        setupPickers()

        setContent {
            val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = colorScheme) {
                NewEditTransferModelScreen(
                    mode = mode,
                    money = money,
                    tax = tax,
                    currency = currency,
                    description = description,
                    walletFrom = walletFrom,
                    walletFromError = walletFromError,
                    walletTo = walletTo,
                    walletToError = walletToError,
                    event = event,
                    place = place,
                    note = note,
                    confirmed = confirmed,
                    countInTotal = countInTotal,
                    showDeleteDialog = showDeleteDialog,
                    onMoneyClick = { moneyPicker.showPicker() },
                    onTaxClick = { taxPicker.showPicker() },
                    onTaxClear = {
                        tax = 0L
                        taxPicker.setCurrentMoney(0L)
                    },
                    onDescriptionChange = { description = it },
                    onWalletFromClick = { walletFromPicker.showSingleWalletPicker() },
                    onWalletToClick = { walletToPicker.showSingleWalletPicker() },
                    onSwapWallets = {
                        val tempFrom = walletFrom
                        val tempTo = walletTo
                        walletFrom = tempTo
                        walletTo = tempFrom
                        currency = tempTo?.currency ?: currency
                        walletFromPicker.setCurrentWallet(tempTo)
                        walletToPicker.setCurrentWallet(tempFrom)
                        moneyPicker.setCurrency(currency)
                        taxPicker.setCurrency(currency)
                    },
                    onEventClick = { eventPicker.showPicker(null) },
                    onEventClear = {
                        event = null
                        eventPicker.setCurrentEvent(null)
                    },
                    onPlaceClick = { placePicker.showPicker() },
                    onPlaceClear = {
                        place = null
                        placePicker.setCurrentPlace(null)
                    },
                    onNoteChange = { note = it },
                    onConfirmedChange = { confirmed = it },
                    onCountInTotalChange = { countInTotal = it },
                    onBackClick = { finish() },
                    onSaveClick = { saveTransferModel() },
                    onDeleteClick = { showDeleteDialog = true },
                    onConfirmDelete = { deleteTransferModel() },
                    onDismissDelete = { showDeleteDialog = false }
                )
            }
        }
    }

    private fun setupPickers() {
        val fm = supportFragmentManager
        moneyPicker = MoneyPicker.createPicker(fm, TAG_MONEY_PICKER, currency, money)
        taxPicker = MoneyPicker.createPicker(fm, TAG_TAX_PICKER, currency, tax)
        walletFromPicker = WalletPicker.createPicker(fm, TAG_WALLET_FROM_PICKER, walletFrom)
        walletToPicker = WalletPicker.createPicker(fm, TAG_WALLET_TO_PICKER, walletTo)
        eventPicker = EventPicker.createPicker(fm, TAG_EVENT_PICKER, event)
        placePicker = PlacePicker.createPicker(fm, TAG_PLACE_PICKER, place)
    }

    private fun loadExistingModel() {
        val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSFER_MODELS, itemId)
        val projection = arrayOf(
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
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                description = cursor.getString(cursor.getColumnIndexOrThrow(Contract.TransferModel.DESCRIPTION)) ?: ""

                val fromId = cursor.getLong(cursor.getColumnIndexOrThrow(Contract.TransferModel.WALLET_FROM_ID))
                val fromName = cursor.getString(cursor.getColumnIndexOrThrow(Contract.TransferModel.WALLET_FROM_NAME))
                val fromIcon = cursor.getString(cursor.getColumnIndexOrThrow(Contract.TransferModel.WALLET_FROM_ICON))
                val fromCurrStr = cursor.getString(cursor.getColumnIndexOrThrow(Contract.TransferModel.WALLET_FROM_CURRENCY))
                val fromCurrency = CurrencyManager.getCurrency(fromCurrStr)
                walletFrom = Wallet(fromId, fromName, IconLoader.parse(fromIcon), fromCurrency, 0L, 0L)
                currency = fromCurrency

                val toId = cursor.getLong(cursor.getColumnIndexOrThrow(Contract.TransferModel.WALLET_TO_ID))
                val toName = cursor.getString(cursor.getColumnIndexOrThrow(Contract.TransferModel.WALLET_TO_NAME))
                val toIcon = cursor.getString(cursor.getColumnIndexOrThrow(Contract.TransferModel.WALLET_TO_ICON))
                val toCurrStr = cursor.getString(cursor.getColumnIndexOrThrow(Contract.TransferModel.WALLET_TO_CURRENCY))
                val toCurrency = CurrencyManager.getCurrency(toCurrStr)
                walletTo = Wallet(toId, toName, IconLoader.parse(toIcon), toCurrency, 0L, 0L)

                money = cursor.getLong(cursor.getColumnIndexOrThrow(Contract.TransferModel.MONEY_FROM))
                tax = cursor.getLong(cursor.getColumnIndexOrThrow(Contract.TransferModel.MONEY_TAX))

                val placeIdCol = cursor.getColumnIndexOrThrow(Contract.TransferModel.PLACE_ID)
                if (!cursor.isNull(placeIdCol)) {
                    val pId = cursor.getLong(placeIdCol)
                    val pName = cursor.getString(cursor.getColumnIndexOrThrow(Contract.TransferModel.PLACE_NAME))
                    val pIcon = cursor.getString(cursor.getColumnIndexOrThrow(Contract.TransferModel.PLACE_ICON))
                    val pAddress = cursor.getString(cursor.getColumnIndexOrThrow(Contract.TransferModel.PLACE_ADDRESS))
                    val pLatCol = cursor.getColumnIndexOrThrow(Contract.TransferModel.PLACE_LATITUDE)
                    val pLngCol = cursor.getColumnIndexOrThrow(Contract.TransferModel.PLACE_LONGITUDE)
                    val pLat = if (cursor.isNull(pLatCol)) null else cursor.getDouble(pLatCol)
                    val pLng = if (cursor.isNull(pLngCol)) null else cursor.getDouble(pLngCol)
                    place = Place(pId, pName, IconLoader.parse(pIcon), pAddress, pLat, pLng)
                }

                note = cursor.getString(cursor.getColumnIndexOrThrow(Contract.TransferModel.NOTE)) ?: ""

                val eventIdCol = cursor.getColumnIndexOrThrow(Contract.TransferModel.EVENT_ID)
                if (!cursor.isNull(eventIdCol)) {
                    val eId = cursor.getLong(eventIdCol)
                    val eName = cursor.getString(cursor.getColumnIndexOrThrow(Contract.TransferModel.EVENT_NAME))
                    val eIcon = cursor.getString(cursor.getColumnIndexOrThrow(Contract.TransferModel.EVENT_ICON))
                    val eStart = cursor.getString(cursor.getColumnIndexOrThrow(Contract.TransferModel.EVENT_START_DATE))
                    val eEnd = cursor.getString(cursor.getColumnIndexOrThrow(Contract.TransferModel.EVENT_END_DATE))
                    event = Event(
                        eId,
                        eName,
                        IconLoader.parse(eIcon),
                        DateUtils.getDateFromSQLDateString(eStart),
                        DateUtils.getDateFromSQLDateString(eEnd)
                    )
                }

                val confCol = cursor.getColumnIndexOrThrow(Contract.TransferModel.CONFIRMED)
                confirmed = cursor.getInt(confCol) == 1
                val countCol = cursor.getColumnIndexOrThrow(Contract.TransferModel.COUNT_IN_TOTAL)
                countInTotal = cursor.getInt(countCol) == 1
            }
        }
    }

    private fun loadDefaultWallet() {
        val projection = arrayOf(
            Contract.Wallet.ID,
            Contract.Wallet.NAME,
            Contract.Wallet.ICON,
            Contract.Wallet.CURRENCY,
            Contract.Wallet.START_MONEY,
            Contract.Wallet.TOTAL_MONEY
        )
        val currentWallet = PreferenceManager.getCurrentWallet()
        val uri = if (currentWallet == PreferenceManager.TOTAL_WALLET_ID) {
            DataContentProvider.CONTENT_WALLETS
        } else {
            ContentUris.withAppendedId(DataContentProvider.CONTENT_WALLETS, currentWallet)
        }
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val wId = cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Wallet.ID))
                val wName = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Wallet.NAME))
                val wIcon = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Wallet.ICON))
                val wCurr = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Wallet.CURRENCY))
                val startMoney = cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Wallet.START_MONEY))
                val totalMoney = cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Wallet.TOTAL_MONEY))
                val resolvedCurrency = CurrencyManager.getCurrency(wCurr)
                walletFrom = Wallet(wId, wName, IconLoader.parse(wIcon), resolvedCurrency, startMoney, totalMoney)
                currency = resolvedCurrency
            }
        }
    }

    private fun validate(): Boolean {
        var isValid = true
        if (walletFrom == null) {
            walletFromError = true
            isValid = false
        } else {
            walletFromError = false
        }
        if (walletTo == null) {
            walletToError = true
            isValid = false
        } else {
            walletToError = false
        }
        return isValid
    }

    private fun saveTransferModel() {
        if (!validate()) return

        val from = walletFrom ?: return
        val to = walletTo ?: return

        val contentValues = ContentValues().apply {
            put(Contract.TransferModel.DESCRIPTION, description)
            put(Contract.TransferModel.WALLET_FROM_ID, from.id)
            put(Contract.TransferModel.WALLET_TO_ID, to.id)
            put(Contract.TransferModel.MONEY_FROM, money)
            put(Contract.TransferModel.MONEY_TO, money)
            put(Contract.TransferModel.MONEY_TAX, tax)
            put(Contract.TransferModel.NOTE, note)
            put(Contract.TransferModel.EVENT_ID, event?.id)
            put(Contract.TransferModel.PLACE_ID, place?.id)
            put(Contract.TransferModel.CONFIRMED, confirmed)
            put(Contract.TransferModel.COUNT_IN_TOTAL, countInTotal)
        }

        when (mode) {
            NewEditItemActivity.Mode.NEW_ITEM -> {
                contentResolver.insert(DataContentProvider.CONTENT_TRANSFER_MODELS, contentValues)
            }
            NewEditItemActivity.Mode.EDIT_ITEM -> {
                val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSFER_MODELS, itemId)
                contentResolver.update(uri, contentValues, null, null)
            }
        }
        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun deleteTransferModel() {
        if (itemId > 0L) {
            val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSFER_MODELS, itemId)
            contentResolver.delete(uri, null, null)
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    override fun onMoneyChanged(tag: String?, newCurrency: CurrencyUnit?, newMoney: Long) {
        when (tag) {
            TAG_MONEY_PICKER -> {
                money = newMoney
                if (newCurrency != null) {
                    currency = newCurrency
                }
            }
            TAG_TAX_PICKER -> {
                tax = newMoney
            }
        }
    }

    override fun onWalletChanged(tag: String?, newWallet: Wallet?) {
        when (tag) {
            TAG_WALLET_FROM_PICKER -> {
                walletFrom = newWallet
                if (newWallet != null) {
                    walletFromError = false
                    currency = newWallet.currency ?: currency
                    moneyPicker.setCurrency(currency)
                    taxPicker.setCurrency(currency)
                } else {
                    moneyPicker.setCurrency(null)
                    taxPicker.setCurrency(null)
                }
            }
            TAG_WALLET_TO_PICKER -> {
                walletTo = newWallet
                if (newWallet != null) {
                    walletToError = false
                }
            }
        }
    }

    override fun onEventChanged(tag: String?, newEvent: Event?) {
        event = newEvent
    }

    override fun onPlaceChanged(tag: String?, newPlace: Place?) {
        place = newPlace
    }

    object Mode {
        @JvmField
        val NEW_ITEM: NewEditItemActivity.Mode = NewEditItemActivity.Mode.NEW_ITEM
        @JvmField
        val EDIT_ITEM: NewEditItemActivity.Mode = NewEditItemActivity.Mode.EDIT_ITEM
    }

    companion object {
        const val MODE: String = NewEditItemActivity.MODE
        const val ID: String = NewEditItemActivity.ID

        private const val TAG_MONEY_PICKER = "NewEditTransferModelActivity::Tag::MoneyPicker"
        private const val TAG_WALLET_FROM_PICKER = "NewEditTransferModelActivity::Tag::WalletFromPicker"
        private const val TAG_WALLET_TO_PICKER = "NewEditTransferModelActivity::Tag::WalletToPicker"
        private const val TAG_TAX_PICKER = "NewEditTransferModelActivity::Tag::TaxPicker"
        private const val TAG_EVENT_PICKER = "NewEditTransferModelActivity::Tag::EventPicker"
        private const val TAG_PLACE_PICKER = "NewEditTransferModelActivity::Tag::PlacePicker"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewEditTransferModelScreen(
    mode: NewEditItemActivity.Mode,
    money: Long,
    tax: Long,
    currency: CurrencyUnit?,
    description: String,
    walletFrom: Wallet?,
    walletFromError: Boolean,
    walletTo: Wallet?,
    walletToError: Boolean,
    event: Event?,
    place: Place?,
    note: String,
    confirmed: Boolean,
    countInTotal: Boolean,
    showDeleteDialog: Boolean,
    onMoneyClick: () -> Unit,
    onTaxClick: () -> Unit,
    onTaxClear: () -> Unit,
    onDescriptionChange: (String) -> Unit,
    onWalletFromClick: () -> Unit,
    onWalletToClick: () -> Unit,
    onSwapWallets: () -> Unit,
    onEventClick: () -> Unit,
    onEventClear: () -> Unit,
    onPlaceClick: () -> Unit,
    onPlaceClear: () -> Unit,
    onNoteChange: (String) -> Unit,
    onConfirmedChange: (Boolean) -> Unit,
    onCountInTotalChange: (Boolean) -> Unit,
    onBackClick: () -> Unit,
    onSaveClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit
) {
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text(stringResource(R.string.title_warning)) },
            text = { Text(stringResource(R.string.message_delete_transfer_model)) },
            confirmButton = {
                TextButton(onClick = onConfirmDelete) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDelete) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    val titleRes = if (mode == NewEditItemActivity.Mode.EDIT_ITEM) {
        R.string.title_activity_edit_model
    } else {
        R.string.title_activity_new_model
    }

    val formattedMoney = remember(currency, money) {
        MoneyFormatter.getInstance().getNotTintedString(currency, money, MoneyFormatter.CurrencyMode.ALWAYS_HIDDEN)
    }
    val formattedTax = remember(currency, tax) {
        MoneyFormatter.getInstance().getNotTintedString(currency, tax, MoneyFormatter.CurrencyMode.ALWAYS_HIDDEN)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(titleRes)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (mode == NewEditItemActivity.Mode.EDIT_ITEM) {
                        IconButton(onClick = onDeleteClick) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Model")
                        }
                    }
                    IconButton(onClick = onSaveClick) {
                        Icon(Icons.Default.Check, contentDescription = "Save Model")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Button(
                onClick = onSaveClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(
                    text = stringResource(R.string.action_save_changes),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Amount Header Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .clickable { onMoneyClick() },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Transfer Amount",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = currency?.symbol ?: "?",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = formattedMoney,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            // Description Outlined Field
            OutlinedTextField(
                value = description,
                onValueChange = onDescriptionChange,
                label = { Text(stringResource(R.string.hint_description)) },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null) },
                trailingIcon = if (description.isNotEmpty()) {
                    {
                        IconButton(onClick = { onDescriptionChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                } else null,
                shape = RoundedCornerShape(16.dp),
                singleLine = true
            )

            // Wallets Section: From and To with Swap Button
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Wallet From
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onWalletFromClick() }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (walletFrom != null) {
                            AndroidView(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                factory = { ctx ->
                                    ImageView(ctx).apply {
                                        scaleType = ImageView.ScaleType.CENTER_INSIDE
                                    }
                                },
                                update = { view ->
                                    IconLoader.loadInto(walletFrom.icon, view)
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.hint_wallet_from),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = walletFrom.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        } else {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = if (walletFromError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.hint_wallet_from),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (walletFromError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (walletFromError) {
                                    Text(
                                        text = stringResource(R.string.error_input_missing_wallet),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Swap Button Divider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = onSwapWallets,
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Icon(
                                Icons.Default.SwapVert,
                                contentDescription = "Swap Wallets",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        HorizontalDivider(modifier = Modifier.weight(1f))
                    }

                    // Wallet To
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onWalletToClick() }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (walletTo != null) {
                            AndroidView(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                                factory = { ctx ->
                                    ImageView(ctx).apply {
                                        scaleType = ImageView.ScaleType.CENTER_INSIDE
                                    }
                                },
                                update = { view ->
                                    IconLoader.loadInto(walletTo.icon, view)
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.hint_wallet_to),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = walletTo.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        } else {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = if (walletToError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.hint_wallet_to),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (walletToError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (walletToError) {
                                    Text(
                                        text = stringResource(R.string.error_input_missing_wallet),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Tax Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onTaxClick() },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        Icons.Default.Paid,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.hint_tax),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (tax > 0L) "${currency?.symbol ?: ""} $formattedTax" else "0",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (tax > 0L) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                    if (tax > 0L) {
                        IconButton(onClick = onTaxClear) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear Tax")
                        }
                    }
                }
            }

            // Optional Event Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onEventClick() },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        Icons.Default.Event,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = event?.name ?: stringResource(R.string.hint_event),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        color = if (event != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (event != null) {
                        IconButton(onClick = onEventClear) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear Event")
                        }
                    }
                }
            }

            // Optional Place Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onPlaceClick() },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        Icons.Default.Place,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = place?.name ?: stringResource(R.string.hint_place),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        color = if (place != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (place != null) {
                        IconButton(onClick = onPlaceClear) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear Place")
                        }
                    }
                }
            }

            // Note Outlined Field
            OutlinedTextField(
                value = note,
                onValueChange = onNoteChange,
                label = { Text(stringResource(R.string.hint_note)) },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.EditNote, contentDescription = null) },
                shape = RoundedCornerShape(16.dp),
                maxLines = 3
            )

            // Checkbox Options Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onConfirmedChange(!confirmed) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = confirmed,
                            onCheckedChange = onConfirmedChange
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.hint_confirmed),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCountInTotalChange(!countInTotal) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = countInTotal,
                            onCheckedChange = onCountInTotalChange
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.hint_show_in_total),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
