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
    "SwallowedException",
    "NestedBlockDepth",
    "ReturnCount",
    "ComplexCondition"
)

package com.oriondev.moneywallet.ui.activity

import android.app.Activity
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.os.Bundle
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
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.broadcast.RecurrenceBroadcastReceiver
import com.oriondev.moneywallet.model.CurrencyUnit
import com.oriondev.moneywallet.model.Event
import com.oriondev.moneywallet.model.Place
import com.oriondev.moneywallet.model.RecurrenceSetting
import com.oriondev.moneywallet.model.Wallet
import com.oriondev.moneywallet.picker.EventPicker
import com.oriondev.moneywallet.picker.MoneyPicker
import com.oriondev.moneywallet.picker.PlacePicker
import com.oriondev.moneywallet.picker.RecurrencePicker
import com.oriondev.moneywallet.picker.WalletPicker
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.utils.CurrencyManager
import com.oriondev.moneywallet.utils.DateUtils
import com.oriondev.moneywallet.utils.IconLoader
import com.oriondev.moneywallet.utils.MoneyFormatter
import java.util.Date

/**
 * Modern Jetpack Compose & Kotlin implementation of NewEditRecurrentTransferActivity.
 * Creates and edits scheduled/repeating transfers between two wallets with tax and recurrence rules.
 */
class NewEditRecurrentTransferActivity : AppCompatActivity(),
    MoneyPicker.Controller,
    WalletPicker.SingleWalletController,
    RecurrencePicker.Controller,
    EventPicker.Controller,
    PlacePicker.Controller {

    private lateinit var moneyPicker: MoneyPicker
    private lateinit var taxPicker: MoneyPicker
    private lateinit var walletFromPicker: WalletPicker
    private lateinit var walletToPicker: WalletPicker
    private lateinit var recurrencePicker: RecurrencePicker
    private lateinit var eventPicker: EventPicker
    private lateinit var placePicker: PlacePicker

    // Observable UI State
    private var money by mutableLongStateOf(0L)
    private var tax by mutableLongStateOf(0L)
    private var currency by mutableStateOf<CurrencyUnit?>(null)
    private var description by mutableStateOf("")
    private var walletFrom by mutableStateOf<Wallet?>(null)
    private var walletTo by mutableStateOf<Wallet?>(null)
    private var recurrenceSetting by mutableStateOf<RecurrenceSetting?>(null)
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
                loadExistingRecurrentTransfer()
            } else {
                loadDefaultWallet()
                recurrenceSetting = RecurrenceSetting(Date(), RecurrenceSetting.TYPE_DAILY)
            }
        }

        setupPickers()

        setContent {
            val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = colorScheme) {
                NewEditRecurrentTransferScreen(
                    mode = mode,
                    money = money,
                    tax = tax,
                    currency = currency,
                    description = description,
                    walletFrom = walletFrom,
                    walletFromError = walletFromError,
                    walletTo = walletTo,
                    walletToError = walletToError,
                    recurrenceSetting = recurrenceSetting,
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
                        taxPicker.setMoney(0L)
                    },
                    onDescriptionChange = { description = it },
                    onWalletFromClick = { walletFromPicker.showSingleWalletPicker() },
                    onWalletToClick = { walletToPicker.showSingleWalletPicker() },
                    onSwapWallets = { swapWallets() },
                    onRecurrenceClick = { recurrencePicker.showPicker() },
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
                    onSaveClick = { saveRecurrentTransfer() },
                    onDeleteClick = { showDeleteDialog = true },
                    onConfirmDelete = { deleteRecurrentTransfer() },
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
        recurrencePicker = RecurrencePicker.createPicker(fm, TAG_RECURRENCE_PICKER, recurrenceSetting)
        eventPicker = EventPicker.createPicker(fm, TAG_EVENT_PICKER, event)
        placePicker = PlacePicker.createPicker(fm, TAG_PLACE_PICKER, place)
    }

    private fun loadExistingRecurrentTransfer() {
        val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_RECURRENT_TRANSFERS, itemId)
        val projection = arrayOf(
            Contract.RecurrentTransfer.DESCRIPTION,
            Contract.RecurrentTransfer.WALLET_FROM_ID,
            Contract.RecurrentTransfer.WALLET_FROM_NAME,
            Contract.RecurrentTransfer.WALLET_FROM_ICON,
            Contract.RecurrentTransfer.WALLET_FROM_CURRENCY,
            Contract.RecurrentTransfer.WALLET_TO_ID,
            Contract.RecurrentTransfer.WALLET_TO_NAME,
            Contract.RecurrentTransfer.WALLET_TO_ICON,
            Contract.RecurrentTransfer.WALLET_TO_CURRENCY,
            Contract.RecurrentTransfer.MONEY_FROM,
            Contract.RecurrentTransfer.MONEY_TO,
            Contract.RecurrentTransfer.MONEY_TAX,
            Contract.RecurrentTransfer.NOTE,
            Contract.RecurrentTransfer.EVENT_ID,
            Contract.RecurrentTransfer.EVENT_NAME,
            Contract.RecurrentTransfer.EVENT_ICON,
            Contract.RecurrentTransfer.EVENT_START_DATE,
            Contract.RecurrentTransfer.EVENT_END_DATE,
            Contract.RecurrentTransfer.PLACE_ID,
            Contract.RecurrentTransfer.PLACE_NAME,
            Contract.RecurrentTransfer.PLACE_ICON,
            Contract.RecurrentTransfer.PLACE_ADDRESS,
            Contract.RecurrentTransfer.PLACE_LATITUDE,
            Contract.RecurrentTransfer.PLACE_LONGITUDE,
            Contract.RecurrentTransfer.CONFIRMED,
            Contract.RecurrentTransfer.COUNT_IN_TOTAL,
            Contract.RecurrentTransfer.START_DATE,
            Contract.RecurrentTransfer.RULE
        )
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                description = cursor.getString(cursor.getColumnIndexOrThrow(Contract.RecurrentTransfer.DESCRIPTION)) ?: ""
                walletFrom = parseWalletFromCursor(cursor)
                walletTo = parseWalletToCursor(cursor)
                currency = walletFrom?.currency
                money = cursor.getLong(cursor.getColumnIndexOrThrow(Contract.RecurrentTransfer.MONEY_FROM))
                tax = cursor.getLong(cursor.getColumnIndexOrThrow(Contract.RecurrentTransfer.MONEY_TAX))
                place = parsePlaceFromCursor(cursor)
                note = cursor.getString(cursor.getColumnIndexOrThrow(Contract.RecurrentTransfer.NOTE)) ?: ""
                event = parseEventFromCursor(cursor)
                val confCol = cursor.getColumnIndexOrThrow(Contract.RecurrentTransfer.CONFIRMED)
                confirmed = cursor.getInt(confCol) == 1
                val countCol = cursor.getColumnIndexOrThrow(Contract.RecurrentTransfer.COUNT_IN_TOTAL)
                countInTotal = cursor.getInt(countCol) == 1
                val startDate = DateUtils.getDateFromSQLDateString(cursor.getString(cursor.getColumnIndexOrThrow(Contract.RecurrentTransfer.START_DATE)))
                val rule = cursor.getString(cursor.getColumnIndexOrThrow(Contract.RecurrentTransfer.RULE))
                recurrenceSetting = RecurrenceSetting(startDate, rule)
            }
        }
    }

    private fun parseWalletFromCursor(cursor: Cursor): Wallet {
        val wId = cursor.getLong(cursor.getColumnIndexOrThrow(Contract.RecurrentTransfer.WALLET_FROM_ID))
        val wName = cursor.getString(cursor.getColumnIndexOrThrow(Contract.RecurrentTransfer.WALLET_FROM_NAME))
        val wIconStr = cursor.getString(cursor.getColumnIndexOrThrow(Contract.RecurrentTransfer.WALLET_FROM_ICON))
        val wCurrStr = cursor.getString(cursor.getColumnIndexOrThrow(Contract.RecurrentTransfer.WALLET_FROM_CURRENCY))
        val wCurr = CurrencyManager.getCurrency(wCurrStr)
        return Wallet(wId, wName, IconLoader.parse(wIconStr), wCurr, 0L, 0L)
    }

    private fun parseWalletToCursor(cursor: Cursor): Wallet {
        val wId = cursor.getLong(cursor.getColumnIndexOrThrow(Contract.RecurrentTransfer.WALLET_TO_ID))
        val wName = cursor.getString(cursor.getColumnIndexOrThrow(Contract.RecurrentTransfer.WALLET_TO_NAME))
        val wIconStr = cursor.getString(cursor.getColumnIndexOrThrow(Contract.RecurrentTransfer.WALLET_TO_ICON))
        val wCurrStr = cursor.getString(cursor.getColumnIndexOrThrow(Contract.RecurrentTransfer.WALLET_TO_CURRENCY))
        val wCurr = CurrencyManager.getCurrency(wCurrStr)
        return Wallet(wId, wName, IconLoader.parse(wIconStr), wCurr, 0L, 0L)
    }

    private fun parsePlaceFromCursor(cursor: Cursor): Place? {
        val placeIdCol = cursor.getColumnIndexOrThrow(Contract.RecurrentTransfer.PLACE_ID)
        if (cursor.isNull(placeIdCol)) return null
        val pId = cursor.getLong(placeIdCol)
        val pName = cursor.getString(cursor.getColumnIndexOrThrow(Contract.RecurrentTransfer.PLACE_NAME))
        val pIcon = cursor.getString(cursor.getColumnIndexOrThrow(Contract.RecurrentTransfer.PLACE_ICON))
        val pAddress = cursor.getString(cursor.getColumnIndexOrThrow(Contract.RecurrentTransfer.PLACE_ADDRESS))
        val pLatCol = cursor.getColumnIndexOrThrow(Contract.RecurrentTransfer.PLACE_LATITUDE)
        val pLngCol = cursor.getColumnIndexOrThrow(Contract.RecurrentTransfer.PLACE_LONGITUDE)
        val pLat = if (cursor.isNull(pLatCol)) null else cursor.getDouble(pLatCol)
        val pLng = if (cursor.isNull(pLngCol)) null else cursor.getDouble(pLngCol)
        return Place(pId, pName, IconLoader.parse(pIcon), pAddress, pLat, pLng)
    }

    private fun parseEventFromCursor(cursor: Cursor): Event? {
        val eventIdCol = cursor.getColumnIndexOrThrow(Contract.RecurrentTransfer.EVENT_ID)
        if (cursor.isNull(eventIdCol)) return null
        val eId = cursor.getLong(eventIdCol)
        val eName = cursor.getString(cursor.getColumnIndexOrThrow(Contract.RecurrentTransfer.EVENT_NAME))
        val eIcon = cursor.getString(cursor.getColumnIndexOrThrow(Contract.RecurrentTransfer.EVENT_ICON))
        val eStart = cursor.getString(cursor.getColumnIndexOrThrow(Contract.RecurrentTransfer.EVENT_START_DATE))
        val eEnd = cursor.getString(cursor.getColumnIndexOrThrow(Contract.RecurrentTransfer.EVENT_END_DATE))
        return Event(
            eId,
            eName,
            IconLoader.parse(eIcon),
            DateUtils.getDateFromSQLDateString(eStart),
            DateUtils.getDateFromSQLDateString(eEnd)
        )
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

    private fun swapWallets() {
        val temp = walletFrom
        walletFrom = walletTo
        walletTo = temp
        walletFromError = false
        walletToError = false
        currency = walletFrom?.currency ?: currency
        moneyPicker.setCurrency(currency)
        taxPicker.setCurrency(currency)
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

    private fun saveRecurrentTransfer() {
        if (!validate()) return
        val wFrom = walletFrom ?: return
        val wTo = walletTo ?: return
        val currentRecurrence = recurrenceSetting ?: return

        val contentValues = ContentValues().apply {
            put(Contract.RecurrentTransfer.DESCRIPTION, description)
            put(Contract.RecurrentTransfer.WALLET_FROM_ID, wFrom.id)
            put(Contract.RecurrentTransfer.WALLET_TO_ID, wTo.id)
            put(Contract.RecurrentTransfer.MONEY_FROM, money)
            put(Contract.RecurrentTransfer.MONEY_TO, money)
            put(Contract.RecurrentTransfer.MONEY_TAX, tax)
            put(Contract.RecurrentTransfer.NOTE, note)
            put(Contract.RecurrentTransfer.EVENT_ID, event?.id)
            put(Contract.RecurrentTransfer.PLACE_ID, place?.id)
            put(Contract.RecurrentTransfer.CONFIRMED, confirmed)
            put(Contract.RecurrentTransfer.COUNT_IN_TOTAL, countInTotal)
            put(Contract.RecurrentTransfer.START_DATE, DateUtils.getSQLDateString(currentRecurrence.startDate))
            put(Contract.RecurrentTransfer.RULE, currentRecurrence.rule)
        }

        when (mode) {
            NewEditItemActivity.Mode.NEW_ITEM -> {
                contentResolver.insert(DataContentProvider.CONTENT_RECURRENT_TRANSFERS, contentValues)
            }
            NewEditItemActivity.Mode.EDIT_ITEM -> {
                val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_RECURRENT_TRANSFERS, itemId)
                contentResolver.update(uri, contentValues, null, null)
            }
        }
        RecurrenceBroadcastReceiver.scheduleRecurrenceTask(this)
        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun deleteRecurrentTransfer() {
        if (itemId > 0L) {
            val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_RECURRENT_TRANSFERS, itemId)
            contentResolver.delete(uri, null, null)
            RecurrenceBroadcastReceiver.scheduleRecurrenceTask(this)
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

    override fun onRecurrenceSettingChanged(tag: String?, newSetting: RecurrenceSetting?) {
        recurrenceSetting = newSetting
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

        private const val TAG_MONEY_PICKER = "NewEditRecurrentTransferActivity::Tag::MoneyPicker"
        private const val TAG_TAX_PICKER = "NewEditRecurrentTransferActivity::Tag::TaxPicker"
        private const val TAG_WALLET_FROM_PICKER = "NewEditRecurrentTransferActivity::Tag::WalletFromPicker"
        private const val TAG_WALLET_TO_PICKER = "NewEditRecurrentTransferActivity::Tag::WalletToPicker"
        private const val TAG_RECURRENCE_PICKER = "NewEditRecurrentTransferActivity::Tag::RecurrencePicker"
        private const val TAG_EVENT_PICKER = "NewEditRecurrentTransferActivity::Tag::EventPicker"
        private const val TAG_PLACE_PICKER = "NewEditRecurrentTransferActivity::Tag::PlacePicker"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewEditRecurrentTransferScreen(
    mode: NewEditItemActivity.Mode,
    money: Long,
    tax: Long,
    currency: CurrencyUnit?,
    description: String,
    walletFrom: Wallet?,
    walletFromError: Boolean,
    walletTo: Wallet?,
    walletToError: Boolean,
    recurrenceSetting: RecurrenceSetting?,
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
    onRecurrenceClick: () -> Unit,
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
            text = { Text(stringResource(R.string.message_delete_recurrent_transfer)) },
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
        R.string.title_activity_edit_recurrence
    } else {
        R.string.title_activity_new_recurrence
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
                            Icon(Icons.Default.Delete, contentDescription = "Delete Recurrence")
                        }
                    }
                    IconButton(onClick = onSaveClick) {
                        Icon(Icons.Default.Check, contentDescription = "Save Recurrence")
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
                    text = stringResource(R.string.menu_action_save_changes),
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
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        if (walletFrom != null) {
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
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        if (walletTo != null) {
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
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        Icons.Default.Percent,
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
                            text = if (tax > 0) formattedTax else stringResource(R.string.hint_tax),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (tax > 0) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                    if (tax > 0) {
                        IconButton(onClick = onTaxClear) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear Tax")
                        }
                    } else {
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Recurrence Schedule Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onRecurrenceClick() },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Update,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.hint_recurrence),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = recurrenceSetting?.getUserReadableString(LocalContext.current)
                                ?: stringResource(R.string.hint_recurrence),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
