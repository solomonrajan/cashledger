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
import com.oriondev.moneywallet.broadcast.RecurrenceBroadcastReceiver
import com.oriondev.moneywallet.model.Category
import com.oriondev.moneywallet.model.CurrencyUnit
import com.oriondev.moneywallet.model.Event
import com.oriondev.moneywallet.model.Place
import com.oriondev.moneywallet.model.RecurrenceSetting
import com.oriondev.moneywallet.model.Wallet
import com.oriondev.moneywallet.picker.CategoryPicker
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
 * Modern Jetpack Compose & Kotlin implementation of NewEditRecurrentTransactionActivity.
 * Creates and edits scheduled/repeating transactions with recurrence interval rules.
 */
class NewEditRecurrentTransactionActivity : AppCompatActivity(),
    MoneyPicker.Controller,
    CategoryPicker.Controller,
    WalletPicker.SingleWalletController,
    RecurrencePicker.Controller,
    EventPicker.Controller,
    PlacePicker.Controller {

    private lateinit var moneyPicker: MoneyPicker
    private lateinit var categoryPicker: CategoryPicker
    private lateinit var walletPicker: WalletPicker
    private lateinit var recurrencePicker: RecurrencePicker
    private lateinit var eventPicker: EventPicker
    private lateinit var placePicker: PlacePicker

    // Observable UI State
    private var money by mutableLongStateOf(0L)
    private var currency by mutableStateOf<CurrencyUnit?>(null)
    private var description by mutableStateOf("")
    private var category by mutableStateOf<Category?>(null)
    private var wallet by mutableStateOf<Wallet?>(null)
    private var recurrenceSetting by mutableStateOf<RecurrenceSetting?>(null)
    private var event by mutableStateOf<Event?>(null)
    private var place by mutableStateOf<Place?>(null)
    private var note by mutableStateOf("")
    private var confirmed by mutableStateOf(true)
    private var countInTotal by mutableStateOf(true)

    // Validation State
    private var categoryError by mutableStateOf(false)
    private var walletError by mutableStateOf(false)
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
                loadExistingRecurrentTransaction()
            } else {
                loadDefaultWallet()
                recurrenceSetting = RecurrenceSetting(Date(), RecurrenceSetting.TYPE_DAILY)
            }
        }

        setupPickers()

        setContent {
            val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = colorScheme) {
                NewEditRecurrentTransactionScreen(
                    mode = mode,
                    money = money,
                    currency = currency,
                    description = description,
                    category = category,
                    categoryError = categoryError,
                    wallet = wallet,
                    walletError = walletError,
                    recurrenceSetting = recurrenceSetting,
                    event = event,
                    place = place,
                    note = note,
                    confirmed = confirmed,
                    countInTotal = countInTotal,
                    showDeleteDialog = showDeleteDialog,
                    onMoneyClick = { moneyPicker.showPicker() },
                    onDescriptionChange = { description = it },
                    onCategoryClick = { categoryPicker.showPicker() },
                    onWalletClick = { walletPicker.showSingleWalletPicker() },
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
                    onSaveClick = { saveRecurrentTransaction() },
                    onDeleteClick = { showDeleteDialog = true },
                    onConfirmDelete = { deleteRecurrentTransaction() },
                    onDismissDelete = { showDeleteDialog = false }
                )
            }
        }
    }

    private fun setupPickers() {
        val fm = supportFragmentManager
        moneyPicker = MoneyPicker.createPicker(fm, TAG_MONEY_PICKER, currency, money)
        categoryPicker = CategoryPicker.createPicker(fm, TAG_CATEGORY_PICKER, category)
        walletPicker = WalletPicker.createPicker(fm, TAG_WALLET_PICKER, wallet)
        recurrencePicker = RecurrencePicker.createPicker(fm, TAG_RECURRENCE_PICKER, recurrenceSetting)
        eventPicker = EventPicker.createPicker(fm, TAG_EVENT_PICKER, event)
        placePicker = PlacePicker.createPicker(fm, TAG_PLACE_PICKER, place)
    }

    private fun loadExistingRecurrentTransaction() {
        val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_RECURRENT_TRANSACTIONS, itemId)
        val projection = arrayOf(
            Contract.RecurrentTransaction.MONEY,
            Contract.RecurrentTransaction.DESCRIPTION,
            Contract.RecurrentTransaction.CATEGORY_ID,
            Contract.RecurrentTransaction.CATEGORY_NAME,
            Contract.RecurrentTransaction.CATEGORY_ICON,
            Contract.RecurrentTransaction.CATEGORY_TYPE,
            Contract.RecurrentTransaction.CATEGORY_SHOW_REPORT,
            Contract.RecurrentTransaction.DIRECTION,
            Contract.RecurrentTransaction.WALLET_ID,
            Contract.RecurrentTransaction.WALLET_NAME,
            Contract.RecurrentTransaction.WALLET_ICON,
            Contract.RecurrentTransaction.WALLET_CURRENCY,
            Contract.RecurrentTransaction.PLACE_ID,
            Contract.RecurrentTransaction.PLACE_NAME,
            Contract.RecurrentTransaction.PLACE_ICON,
            Contract.RecurrentTransaction.PLACE_ADDRESS,
            Contract.RecurrentTransaction.PLACE_LATITUDE,
            Contract.RecurrentTransaction.PLACE_LONGITUDE,
            Contract.RecurrentTransaction.NOTE,
            Contract.RecurrentTransaction.EVENT_ID,
            Contract.RecurrentTransaction.EVENT_NAME,
            Contract.RecurrentTransaction.EVENT_ICON,
            Contract.RecurrentTransaction.EVENT_START_DATE,
            Contract.RecurrentTransaction.EVENT_END_DATE,
            Contract.RecurrentTransaction.CONFIRMED,
            Contract.RecurrentTransaction.COUNT_IN_TOTAL,
            Contract.RecurrentTransaction.START_DATE,
            Contract.RecurrentTransaction.RULE
        )
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                money = cursor.getLong(cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.MONEY))
                description = cursor.getString(cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.DESCRIPTION)) ?: ""
                category = parseCategoryFromCursor(cursor)
                wallet = parseWalletFromCursor(cursor)
                currency = wallet?.currency
                place = parsePlaceFromCursor(cursor)
                note = cursor.getString(cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.NOTE)) ?: ""
                event = parseEventFromCursor(cursor)
                val confCol = cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.CONFIRMED)
                confirmed = cursor.getInt(confCol) == 1
                val countCol = cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.COUNT_IN_TOTAL)
                countInTotal = cursor.getInt(countCol) == 1
                val startDate = DateUtils.getDateFromSQLDateString(cursor.getString(cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.START_DATE)))
                val rule = cursor.getString(cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.RULE))
                recurrenceSetting = RecurrenceSetting(startDate, rule)
            }
        }
    }

    private fun parseCategoryFromCursor(cursor: Cursor): Category {
        val catId = cursor.getLong(cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.CATEGORY_ID))
        val catName = cursor.getString(cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.CATEGORY_NAME))
        val catIconStr = cursor.getString(cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.CATEGORY_ICON))
        val catTypeInt = cursor.getInt(cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.CATEGORY_TYPE))
        return Category(
            catId,
            catName,
            IconLoader.parse(catIconStr),
            Contract.CategoryType.fromValue(catTypeInt)
        )
    }

    private fun parseWalletFromCursor(cursor: Cursor): Wallet {
        val wId = cursor.getLong(cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.WALLET_ID))
        val wName = cursor.getString(cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.WALLET_NAME))
        val wIconStr = cursor.getString(cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.WALLET_ICON))
        val wCurrStr = cursor.getString(cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.WALLET_CURRENCY))
        val wCurr = CurrencyManager.getCurrency(wCurrStr)
        return Wallet(wId, wName, IconLoader.parse(wIconStr), wCurr, 0L, 0L)
    }

    private fun parsePlaceFromCursor(cursor: Cursor): Place? {
        val placeIdCol = cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.PLACE_ID)
        if (cursor.isNull(placeIdCol)) return null
        val pId = cursor.getLong(placeIdCol)
        val pName = cursor.getString(cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.PLACE_NAME))
        val pIcon = cursor.getString(cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.PLACE_ICON))
        val pAddress = cursor.getString(cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.PLACE_ADDRESS))
        val pLatCol = cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.PLACE_LATITUDE)
        val pLngCol = cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.PLACE_LONGITUDE)
        val pLat = if (cursor.isNull(pLatCol)) null else cursor.getDouble(pLatCol)
        val pLng = if (cursor.isNull(pLngCol)) null else cursor.getDouble(pLngCol)
        return Place(pId, pName, IconLoader.parse(pIcon), pAddress, pLat, pLng)
    }

    private fun parseEventFromCursor(cursor: Cursor): Event? {
        val eventIdCol = cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.EVENT_ID)
        if (cursor.isNull(eventIdCol)) return null
        val eId = cursor.getLong(eventIdCol)
        val eName = cursor.getString(cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.EVENT_NAME))
        val eIcon = cursor.getString(cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.EVENT_ICON))
        val eStart = cursor.getString(cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.EVENT_START_DATE))
        val eEnd = cursor.getString(cursor.getColumnIndexOrThrow(Contract.RecurrentTransaction.EVENT_END_DATE))
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
                wallet = Wallet(wId, wName, IconLoader.parse(wIcon), resolvedCurrency, startMoney, totalMoney)
                currency = resolvedCurrency
            }
        }
    }

    private fun validate(): Boolean {
        var isValid = true
        if (category == null) {
            categoryError = true
            isValid = false
        } else {
            categoryError = false
        }
        if (wallet == null) {
            walletError = true
            isValid = false
        } else {
            walletError = false
        }
        return isValid
    }

    private fun saveRecurrentTransaction() {
        if (!validate()) return
        val currentCat = category ?: return
        val currentWallet = wallet ?: return
        val currentRecurrence = recurrenceSetting ?: return

        val contentValues = ContentValues().apply {
            put(Contract.RecurrentTransaction.MONEY, money)
            put(Contract.RecurrentTransaction.DESCRIPTION, description)
            put(Contract.RecurrentTransaction.CATEGORY_ID, currentCat.id)
            put(Contract.RecurrentTransaction.DIRECTION, currentCat.direction)
            put(Contract.RecurrentTransaction.WALLET_ID, currentWallet.id)
            put(Contract.RecurrentTransaction.PLACE_ID, place?.id)
            put(Contract.RecurrentTransaction.EVENT_ID, event?.id)
            put(Contract.RecurrentTransaction.NOTE, note)
            put(Contract.RecurrentTransaction.CONFIRMED, confirmed)
            put(Contract.RecurrentTransaction.COUNT_IN_TOTAL, countInTotal)
            put(Contract.RecurrentTransaction.START_DATE, DateUtils.getSQLDateString(currentRecurrence.startDate))
            put(Contract.RecurrentTransaction.RULE, currentRecurrence.rule)
        }

        when (mode) {
            NewEditItemActivity.Mode.NEW_ITEM -> {
                contentResolver.insert(DataContentProvider.CONTENT_RECURRENT_TRANSACTIONS, contentValues)
            }
            NewEditItemActivity.Mode.EDIT_ITEM -> {
                val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_RECURRENT_TRANSACTIONS, itemId)
                contentResolver.update(uri, contentValues, null, null)
            }
        }
        RecurrenceBroadcastReceiver.scheduleRecurrenceTask(this)
        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun deleteRecurrentTransaction() {
        if (itemId > 0L) {
            val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_RECURRENT_TRANSACTIONS, itemId)
            contentResolver.delete(uri, null, null)
            RecurrenceBroadcastReceiver.scheduleRecurrenceTask(this)
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    override fun onMoneyChanged(tag: String?, newCurrency: CurrencyUnit?, newMoney: Long) {
        money = newMoney
        if (newCurrency != null) {
            currency = newCurrency
        }
    }

    override fun onCategoryChanged(tag: String?, newCategory: Category?) {
        category = newCategory
        if (newCategory != null) {
            categoryError = false
        }
    }

    override fun onWalletChanged(tag: String?, newWallet: Wallet?) {
        wallet = newWallet
        if (newWallet != null) {
            walletError = false
            currency = newWallet.currency ?: currency
            moneyPicker.setCurrency(currency)
        } else {
            moneyPicker.setCurrency(null)
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

        private const val TAG_MONEY_PICKER = "NewEditRecurrentTransactionActivity::Tag::MoneyPicker"
        private const val TAG_CATEGORY_PICKER = "NewEditRecurrentTransactionActivity::Tag::CategoryPicker"
        private const val TAG_WALLET_PICKER = "NewEditRecurrentTransactionActivity::Tag::WalletPicker"
        private const val TAG_RECURRENCE_PICKER = "NewEditRecurrentTransactionActivity::Tag::RecurrencePicker"
        private const val TAG_EVENT_PICKER = "NewEditRecurrentTransactionActivity::Tag::EventPicker"
        private const val TAG_PLACE_PICKER = "NewEditRecurrentTransactionActivity::Tag::PlacePicker"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewEditRecurrentTransactionScreen(
    mode: NewEditItemActivity.Mode,
    money: Long,
    currency: CurrencyUnit?,
    description: String,
    category: Category?,
    categoryError: Boolean,
    wallet: Wallet?,
    walletError: Boolean,
    recurrenceSetting: RecurrenceSetting?,
    event: Event?,
    place: Place?,
    note: String,
    confirmed: Boolean,
    countInTotal: Boolean,
    showDeleteDialog: Boolean,
    onMoneyClick: () -> Unit,
    onDescriptionChange: (String) -> Unit,
    onCategoryClick: () -> Unit,
    onWalletClick: () -> Unit,
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
            text = { Text(stringResource(R.string.message_delete_recurrent_transaction)) },
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
                        text = "Amount",
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

            // Category Selection Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onCategoryClick() },
                colors = CardDefaults.cardColors(
                    containerColor = if (categoryError) {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    }
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
                    if (category != null) {
                        AndroidView(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            factory = { ctx ->
                                ImageView(ctx).apply {
                                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                                }
                            },
                            update = { view ->
                                IconLoader.loadInto(category.icon, view)
                            }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = category.type.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Icon(
                            Icons.Default.Category,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = if (categoryError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.hint_category),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (categoryError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (categoryError) {
                                Text(
                                    text = stringResource(R.string.error_input_missing_category),
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

            // Wallet Selection Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onWalletClick() },
                colors = CardDefaults.cardColors(
                    containerColor = if (walletError) {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    }
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
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    if (wallet != null) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = wallet.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = wallet.currency?.symbol ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.hint_wallet),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (walletError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (walletError) {
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
