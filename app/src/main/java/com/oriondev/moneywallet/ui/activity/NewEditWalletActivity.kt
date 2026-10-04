@file:Suppress("LongMethod", "FunctionName", "FunctionNaming", "WildcardImport", "MaxLineLength", "MagicNumber", "UnusedParameter", "LongParameterList", "ReturnCount")
/*
 * Copyright (c) 2018.
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

package com.oriondev.moneywallet.ui.activity

import android.app.Activity
import android.content.ContentUris
import android.content.ContentValues
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.CurrencyUnit
import com.oriondev.moneywallet.model.Icon
import com.oriondev.moneywallet.picker.CurrencyPicker
import com.oriondev.moneywallet.picker.IconPicker
import com.oriondev.moneywallet.picker.MoneyPicker
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.utils.CurrencyManager
import com.oriondev.moneywallet.utils.IconLoader
import com.oriondev.moneywallet.utils.MoneyFormatter

class NewEditWalletActivity : AppCompatActivity(), IconPicker.Controller, CurrencyPicker.Controller, MoneyPicker.Controller {

    private lateinit var iconPicker: IconPicker
    private lateinit var currencyPicker: CurrencyPicker
    private lateinit var moneyPicker: MoneyPicker

    // State
    private var name by mutableStateOf("")
    private var icon by mutableStateOf<Icon?>(null)
    private var currency by mutableStateOf(CurrencyManager.getDefaultCurrency())
    private var startMoney by mutableStateOf(0L)
    private var countInTotal by mutableStateOf(true)
    private var note by mutableStateOf("")

    private var mode = NewEditItemActivity.Mode.NEW_ITEM
    private var itemId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val intentMode = intent.getSerializableExtra(NewEditItemActivity.MODE) as? NewEditItemActivity.Mode
        if (intentMode != null) mode = intentMode
        itemId = intent.getLongExtra(NewEditItemActivity.ID, -1)

        // Initialize pickers
        iconPicker = IconPicker.createPicker(supportFragmentManager, TAG_ICON_PICKER, null)
        currencyPicker = CurrencyPicker.createPicker(supportFragmentManager, TAG_CURRENCY_PICKER, currency)
        moneyPicker = MoneyPicker.createPicker(supportFragmentManager, TAG_MONEY_PICKER, currency, startMoney, true)

        if (savedInstanceState == null && mode == NewEditItemActivity.Mode.EDIT_ITEM) {
            loadWalletData()
        }

        setContent {
            val isDark = isSystemInDarkTheme()
            val colorScheme = if (isDark) darkColorScheme() else lightColorScheme()

            MaterialTheme(colorScheme = colorScheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    NewEditWalletScreen(
                        mode = mode,
                        name = name,
                        onNameChange = { name = it },
                        icon = icon,
                        onIconClick = { iconPicker.showPicker() },
                        currency = currency,
                        onCurrencyClick = { currencyPicker.showPicker() },
                        startMoney = startMoney,
                        onStartMoneyClick = { moneyPicker.showPicker() },
                        countInTotal = countInTotal,
                        onCountInTotalChange = { countInTotal = it },
                        note = note,
                        onNoteChange = { note = it },
                        onBackClick = { finish() },
                        onSaveClick = { saveWallet() }
                    )
                }
            }
        }
    }

    private fun loadWalletData() {
        val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_WALLETS, itemId)
        val projection = arrayOf(
            Contract.Wallet.NAME,
            Contract.Wallet.ICON,
            Contract.Wallet.CURRENCY,
            Contract.Wallet.START_MONEY,
            Contract.Wallet.COUNT_IN_TOTAL,
            Contract.Wallet.NOTE
        )
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                name = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Wallet.NAME))
                val iconStr = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Wallet.ICON))
                icon = IconLoader.parse(iconStr)
                currency = CurrencyManager.getCurrency(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Wallet.CURRENCY)))
                startMoney = cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Wallet.START_MONEY))
                countInTotal = cursor.getInt(cursor.getColumnIndexOrThrow(Contract.Wallet.COUNT_IN_TOTAL)) == 1
                note = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Wallet.NOTE)) ?: ""
                
                // Update pickers with loaded data
                iconPicker = IconPicker.createPicker(supportFragmentManager, TAG_ICON_PICKER, icon)
                currencyPicker = CurrencyPicker.createPicker(supportFragmentManager, TAG_CURRENCY_PICKER, currency)
                moneyPicker = MoneyPicker.createPicker(supportFragmentManager, TAG_MONEY_PICKER, currency, startMoney, true)
            } else {
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
        } ?: run {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    private fun saveWallet() {
        if (name.isBlank()) {
            Toast.makeText(this, R.string.error_input_name_not_valid, Toast.LENGTH_SHORT).show()
            return
        }
        if (currency == null) {
            Toast.makeText(this, R.string.error_input_currency_not_valid, Toast.LENGTH_SHORT).show()
            return
        }

        val contentValues = ContentValues().apply {
            put(Contract.Wallet.NAME, name)
            put(Contract.Wallet.ICON, icon?.toString() ?: "")
            put(Contract.Wallet.CURRENCY, currency.iso)
            put(Contract.Wallet.START_MONEY, startMoney)
            put(Contract.Wallet.COUNT_IN_TOTAL, if (countInTotal) 1 else 0)
            put(Contract.Wallet.NOTE, note)
        }

        when (mode) {
            NewEditItemActivity.Mode.NEW_ITEM -> {
                contentResolver.insert(DataContentProvider.CONTENT_WALLETS, contentValues)
            }
            NewEditItemActivity.Mode.EDIT_ITEM -> {
                val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_WALLETS, itemId)
                contentResolver.update(uri, contentValues, null, null)
            }
        }
        setResult(Activity.RESULT_OK)
        finish()
    }

    override fun onIconChanged(tag: String?, newIcon: Icon?) {
        icon = newIcon
    }

    override fun onCurrencyChanged(tag: String?, newCurrency: CurrencyUnit?) {
        if (newCurrency != null) {
            currency = newCurrency
            moneyPicker.setCurrency(newCurrency)
        }
    }

    override fun onMoneyChanged(tag: String?, newCurrency: CurrencyUnit?, newMoney: Long) {
        startMoney = newMoney
    }

    companion object {
        private const val TAG_ICON_PICKER = "NewEditWalletActivity::Tag::IconPicker"
        private const val TAG_CURRENCY_PICKER = "NewEditWalletActivity::Tag::CurrencyPicker"
        private const val TAG_MONEY_PICKER = "NewEditWalletActivity::Tag::MoneyPicker"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewEditWalletScreen(
    mode: NewEditItemActivity.Mode,
    name: String,
    onNameChange: (String) -> Unit,
    icon: Icon?,
    onIconClick: () -> Unit,
    currency: CurrencyUnit,
    onCurrencyClick: () -> Unit,
    startMoney: Long,
    onStartMoneyClick: () -> Unit,
    countInTotal: Boolean,
    onCountInTotalChange: (Boolean) -> Unit,
    note: String,
    onNoteChange: (String) -> Unit,
    onBackClick: () -> Unit,
    onSaveClick: () -> Unit
) {
    val titleRes = if (mode == NewEditItemActivity.Mode.NEW_ITEM) R.string.title_activity_new_wallet else R.string.title_activity_edit_wallet
    val context = LocalContext.current
    val title = context.getString(titleRes)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onSaveClick) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Icon and Name
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Icon view using legacy IconLoader in AndroidView
                AndroidView(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onIconClick() },
                    factory = { ctx ->
                        android.widget.ImageView(ctx).apply {
                            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                            val pd = (8 * ctx.resources.displayMetrics.density).toInt()
                            setPadding(pd, pd, pd, pd)
                        }
                    },
                    update = { view ->
                        if (icon != null) {
                            IconLoader.loadInto(icon, view)
                        } else {
                            view.setImageDrawable(null) // Empty or fallback
                        }
                    }
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.hint_name)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
            }

            // Currency
            OutlinedTextField(
                value = currency.name ?: currency.iso,
                onValueChange = { },
                label = { Text(stringResource(R.string.hint_currency)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCurrencyClick() },
                enabled = false, // Acts as button
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )

            // Starting Amount
            val formattedMoney = MoneyFormatter.getInstance().getNotTintedString(currency, startMoney)
            OutlinedTextField(
                value = formattedMoney,
                onValueChange = { },
                label = { Text(stringResource(R.string.hint_start_money)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onStartMoneyClick() },
                enabled = false,
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )

            // Exclude from total checkbox
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCountInTotalChange(!countInTotal) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.hint_not_exclude_wallet),
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = countInTotal,
                    onCheckedChange = onCountInTotalChange
                )
            }

            // Note
            OutlinedTextField(
                value = note,
                onValueChange = onNoteChange,
                label = { Text(stringResource(R.string.hint_note)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5
            )
        }
    }
}
