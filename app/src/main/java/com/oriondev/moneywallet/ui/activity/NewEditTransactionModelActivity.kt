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
import com.oriondev.moneywallet.model.Category
import com.oriondev.moneywallet.model.CurrencyUnit
import com.oriondev.moneywallet.model.Event
import com.oriondev.moneywallet.model.Place
import com.oriondev.moneywallet.model.Wallet
import com.oriondev.moneywallet.picker.CategoryPicker
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
 * Modern Jetpack Compose & Kotlin implementation of NewEditTransactionModelActivity.
 * Creates and edits transaction template models that can be instantiated into transactions.
 */
class NewEditTransactionModelActivity : AppCompatActivity(),
    MoneyPicker.Controller,
    CategoryPicker.Controller,
    WalletPicker.SingleWalletController,
    EventPicker.Controller,
    PlacePicker.Controller {

    private lateinit var moneyPicker: MoneyPicker
    private lateinit var categoryPicker: CategoryPicker
    private lateinit var walletPicker: WalletPicker
    private lateinit var eventPicker: EventPicker
    private lateinit var placePicker: PlacePicker

    // Observable UI State
    private var money by mutableLongStateOf(0L)
    private var currency by mutableStateOf<CurrencyUnit?>(null)
    private var description by mutableStateOf("")
    private var category by mutableStateOf<Category?>(null)
    private var wallet by mutableStateOf<Wallet?>(null)
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
                loadExistingModel()
            } else {
                loadDefaultWallet()
            }
        }

        setupPickers()

        setContent {
            val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = colorScheme) {
                NewEditTransactionModelScreen(
                    mode = mode,
                    money = money,
                    currency = currency,
                    description = description,
                    category = category,
                    categoryError = categoryError,
                    wallet = wallet,
                    walletError = walletError,
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
                    onSaveClick = { saveTransactionModel() },
                    onDeleteClick = { showDeleteDialog = true },
                    onConfirmDelete = { deleteTransactionModel() },
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
        eventPicker = EventPicker.createPicker(fm, TAG_EVENT_PICKER, event)
        placePicker = PlacePicker.createPicker(fm, TAG_PLACE_PICKER, place)
    }

    private fun loadExistingModel() {
        val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSACTION_MODELS, itemId)
        val projection = arrayOf(
            Contract.TransactionModel.MONEY,
            Contract.TransactionModel.DESCRIPTION,
            Contract.TransactionModel.CATEGORY_ID,
            Contract.TransactionModel.CATEGORY_NAME,
            Contract.TransactionModel.CATEGORY_ICON,
            Contract.TransactionModel.CATEGORY_TYPE,
            Contract.TransactionModel.CATEGORY_SHOW_REPORT,
            Contract.TransactionModel.DIRECTION,
            Contract.TransactionModel.WALLET_ID,
            Contract.TransactionModel.WALLET_NAME,
            Contract.TransactionModel.WALLET_ICON,
            Contract.TransactionModel.WALLET_CURRENCY,
            Contract.TransactionModel.PLACE_ID,
            Contract.TransactionModel.PLACE_NAME,
            Contract.TransactionModel.PLACE_ICON,
            Contract.TransactionModel.PLACE_ADDRESS,
            Contract.TransactionModel.PLACE_LATITUDE,
            Contract.TransactionModel.PLACE_LONGITUDE,
            Contract.TransactionModel.NOTE,
            Contract.TransactionModel.EVENT_ID,
            Contract.TransactionModel.EVENT_NAME,
            Contract.TransactionModel.EVENT_ICON,
            Contract.TransactionModel.EVENT_START_DATE,
            Contract.TransactionModel.EVENT_END_DATE,
            Contract.TransactionModel.CONFIRMED,
            Contract.TransactionModel.COUNT_IN_TOTAL
        )
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                money = cursor.getLong(cursor.getColumnIndexOrThrow(Contract.TransactionModel.MONEY))
                description = cursor.getString(cursor.getColumnIndexOrThrow(Contract.TransactionModel.DESCRIPTION)) ?: ""
                
                val catId = cursor.getLong(cursor.getColumnIndexOrThrow(Contract.TransactionModel.CATEGORY_ID))
                val catName = cursor.getString(cursor.getColumnIndexOrThrow(Contract.TransactionModel.CATEGORY_NAME))
                val catIconStr = cursor.getString(cursor.getColumnIndexOrThrow(Contract.TransactionModel.CATEGORY_ICON))
                val catTypeInt = cursor.getInt(cursor.getColumnIndexOrThrow(Contract.TransactionModel.CATEGORY_TYPE))
                category = Category(
                    catId,
                    catName,
                    IconLoader.parse(catIconStr),
                    Contract.CategoryType.fromValue(catTypeInt)
                )

                val wId = cursor.getLong(cursor.getColumnIndexOrThrow(Contract.TransactionModel.WALLET_ID))
                val wName = cursor.getString(cursor.getColumnIndexOrThrow(Contract.TransactionModel.WALLET_NAME))
                val wIconStr = cursor.getString(cursor.getColumnIndexOrThrow(Contract.TransactionModel.WALLET_ICON))
                val wCurrStr = cursor.getString(cursor.getColumnIndexOrThrow(Contract.TransactionModel.WALLET_CURRENCY))
                val wCurr = CurrencyManager.getCurrency(wCurrStr)
                wallet = Wallet(wId, wName, IconLoader.parse(wIconStr), wCurr, 0L, 0L)
                currency = wCurr

                val placeIdCol = cursor.getColumnIndexOrThrow(Contract.TransactionModel.PLACE_ID)
                if (!cursor.isNull(placeIdCol)) {
                    val pId = cursor.getLong(placeIdCol)
                    val pName = cursor.getString(cursor.getColumnIndexOrThrow(Contract.TransactionModel.PLACE_NAME))
                    val pIcon = cursor.getString(cursor.getColumnIndexOrThrow(Contract.TransactionModel.PLACE_ICON))
                    val pAddress = cursor.getString(cursor.getColumnIndexOrThrow(Contract.TransactionModel.PLACE_ADDRESS))
                    val pLatCol = cursor.getColumnIndexOrThrow(Contract.TransactionModel.PLACE_LATITUDE)
                    val pLngCol = cursor.getColumnIndexOrThrow(Contract.TransactionModel.PLACE_LONGITUDE)
                    val pLat = if (cursor.isNull(pLatCol)) null else cursor.getDouble(pLatCol)
                    val pLng = if (cursor.isNull(pLngCol)) null else cursor.getDouble(pLngCol)
                    place = Place(pId, pName, IconLoader.parse(pIcon), pAddress, pLat, pLng)
                }

                note = cursor.getString(cursor.getColumnIndexOrThrow(Contract.TransactionModel.NOTE)) ?: ""

                val eventIdCol = cursor.getColumnIndexOrThrow(Contract.TransactionModel.EVENT_ID)
                if (!cursor.isNull(eventIdCol)) {
                    val eId = cursor.getLong(eventIdCol)
                    val eName = cursor.getString(cursor.getColumnIndexOrThrow(Contract.TransactionModel.EVENT_NAME))
                    val eIcon = cursor.getString(cursor.getColumnIndexOrThrow(Contract.TransactionModel.EVENT_ICON))
                    val eStart = cursor.getString(cursor.getColumnIndexOrThrow(Contract.TransactionModel.EVENT_START_DATE))
                    val eEnd = cursor.getString(cursor.getColumnIndexOrThrow(Contract.TransactionModel.EVENT_END_DATE))
                    event = Event(
                        eId,
                        eName,
                        IconLoader.parse(eIcon),
                        DateUtils.getDateFromSQLDateString(eStart),
                        DateUtils.getDateFromSQLDateString(eEnd)
                    )
                }

                val confCol = cursor.getColumnIndexOrThrow(Contract.TransactionModel.CONFIRMED)
                confirmed = cursor.getInt(confCol) == 1
                val countCol = cursor.getColumnIndexOrThrow(Contract.TransactionModel.COUNT_IN_TOTAL)
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

    private fun saveTransactionModel() {
        if (!validate()) return

        val currentCat = category ?: return
        val currentWallet = wallet ?: return

        val contentValues = ContentValues().apply {
            put(Contract.TransactionModel.MONEY, money)
            put(Contract.TransactionModel.DESCRIPTION, description)
            put(Contract.TransactionModel.CATEGORY_ID, currentCat.id)
            put(Contract.TransactionModel.DIRECTION, currentCat.direction)
            put(Contract.TransactionModel.WALLET_ID, currentWallet.id)
            put(Contract.TransactionModel.PLACE_ID, place?.id)
            put(Contract.TransactionModel.EVENT_ID, event?.id)
            put(Contract.TransactionModel.NOTE, note)
            put(Contract.TransactionModel.CONFIRMED, confirmed)
            put(Contract.TransactionModel.COUNT_IN_TOTAL, countInTotal)
        }

        when (mode) {
            NewEditItemActivity.Mode.NEW_ITEM -> {
                contentResolver.insert(DataContentProvider.CONTENT_TRANSACTION_MODELS, contentValues)
            }
            NewEditItemActivity.Mode.EDIT_ITEM -> {
                val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSACTION_MODELS, itemId)
                contentResolver.update(uri, contentValues, null, null)
            }
        }
        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun deleteTransactionModel() {
        if (itemId > 0L) {
            val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSACTION_MODELS, itemId)
            contentResolver.delete(uri, null, null)
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

        private const val TAG_MONEY_PICKER = "NewEditTransactionModelActivity::Tag::MoneyPicker"
        private const val TAG_CATEGORY_PICKER = "NewEditTransactionModelActivity::Tag::CategoryPicker"
        private const val TAG_WALLET_PICKER = "NewEditTransactionModelActivity::Tag::WalletPicker"
        private const val TAG_EVENT_PICKER = "NewEditTransactionModelActivity::Tag::EventPicker"
        private const val TAG_PLACE_PICKER = "NewEditTransactionModelActivity::Tag::PlacePicker"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewEditTransactionModelScreen(
    mode: NewEditItemActivity.Mode,
    money: Long,
    currency: CurrencyUnit?,
    description: String,
    category: Category?,
    categoryError: Boolean,
    wallet: Wallet?,
    walletError: Boolean,
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
            text = { Text(stringResource(R.string.message_delete_transaction_model)) },
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
                    if (wallet != null) {
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
                                IconLoader.loadInto(wallet.icon, view)
                            }
                        )
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
                        Icon(
                            Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = if (walletError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
