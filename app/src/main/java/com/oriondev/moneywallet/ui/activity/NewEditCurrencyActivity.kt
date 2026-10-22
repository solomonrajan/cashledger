/*
 * Copyright (c) 2019. MoneyWallet
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
import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.MoneyScale
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.SQLiteDataException
import com.oriondev.moneywallet.utils.CurrencyManager

class NewEditCurrencyActivity : AppCompatActivity() {

    enum class Mode {
        NEW_ITEM,
        EDIT_ITEM
    }

    private var mode = Mode.NEW_ITEM
    private var isoParam: String? = null

    // State
    private var name by mutableStateOf("")
    private var iso by mutableStateOf("")
    private var symbol by mutableStateOf("")
    private var decimals by mutableStateOf("2")

    private var nameError by mutableStateOf(false)
    private var isoError by mutableStateOf(false)
    private var decimalsError by mutableStateOf(false)

    private var showDeleteDialog by mutableStateOf(false)
    private var rescaleDialogMessage by mutableStateOf<String?>(null)
    private var pendingDecimalCount by mutableIntStateOf(2)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val intentMode = intent?.getSerializableExtra(MODE) as? Mode
        if (intentMode != null) {
            mode = intentMode
        }
        isoParam = intent?.getStringExtra(ISO)

        if (mode == Mode.EDIT_ITEM && isoParam.isNullOrEmpty()) {
            throw RuntimeException("Trying to edit a currency with an invalid iso")
        }

        if (savedInstanceState == null && mode == Mode.EDIT_ITEM) {
            loadCurrencyData()
        }

        setContent {
            val isDark = isSystemInDarkTheme()
            val colorScheme = if (isDark) darkColorScheme() else lightColorScheme()

            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NewEditCurrencyScreen(
                        mode = mode,
                        name = name,
                        onNameChange = {
                            name = it
                            nameError = false
                        },
                        nameError = nameError,
                        iso = iso,
                        onIsoChange = {
                            if (it.length <= 3) {
                                iso = it.uppercase()
                                isoError = false
                            }
                        },
                        isoError = isoError,
                        symbol = symbol,
                        onSymbolChange = { symbol = it },
                        decimals = decimals,
                        onDecimalsChange = {
                            decimals = it
                            decimalsError = false
                        },
                        decimalsError = decimalsError,
                        showDeleteDialog = showDeleteDialog,
                        onShowDeleteDialogChange = { showDeleteDialog = it },
                        onConfirmDelete = { deleteCurrency() },
                        rescaleDialogMessage = rescaleDialogMessage,
                        onConfirmRescale = { fixMoneyDecimals ->
                            val msg = rescaleDialogMessage
                            rescaleDialogMessage = null
                            if (msg != null) {
                                executeSaveCurrency(pendingDecimalCount, fixMoneyDecimals)
                            }
                        },
                        onDismissRescale = { rescaleDialogMessage = null },
                        onBackClick = { finish() },
                        onSaveClick = { handleSave() }
                    )
                }
            }
        }
    }

    private fun loadCurrencyData() {
        val targetIso = isoParam ?: return
        val uri = Uri.withAppendedPath(DataContentProvider.CONTENT_CURRENCIES, targetIso)
        val projections = arrayOf(
            Contract.Currency.NAME,
            Contract.Currency.ISO,
            Contract.Currency.SYMBOL,
            Contract.Currency.DECIMALS
        )
        contentResolver.query(uri, projections, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                name = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Currency.NAME)) ?: ""
                iso = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Currency.ISO)) ?: ""
                symbol = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Currency.SYMBOL)) ?: ""
                decimals = cursor.getInt(cursor.getColumnIndexOrThrow(Contract.Currency.DECIMALS)).toString()
            } else {
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
        } ?: run {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    private fun validate(): Boolean {
        var valid = true
        if (name.isBlank()) {
            nameError = true
            valid = false
        }
        if (iso.length != 3) {
            isoError = true
            valid = false
        }
        val dec = decimals.toIntOrNull()
        if (dec == null || dec !in 0..8) {
            decimalsError = true
            valid = false
        }
        if (!valid) {
            if (nameError) {
                Toast.makeText(this, R.string.error_input_name_not_valid, Toast.LENGTH_SHORT).show()
            } else if (isoError) {
                Toast.makeText(this, R.string.error_input_iso_not_valid, Toast.LENGTH_SHORT).show()
            } else if (decimalsError) {
                Toast.makeText(this, R.string.error_input_decimals_not_valid, Toast.LENGTH_SHORT).show()
            }
        }
        return valid
    }

    private fun handleSave() {
        if (!validate()) return

        val decimalCount = decimals.toIntOrNull() ?: 2

        if (mode == Mode.EDIT_ITEM) {
            val targetIso = isoParam
            val currencyUnit = if (targetIso != null) CurrencyManager.getCurrency(targetIso) else null
            if (currencyUnit != null && currencyUnit.decimals != decimalCount) {
                val isMultiply = currencyUnit.decimals < decimalCount
                val exponent = if (isMultiply) decimalCount - currencyUnit.decimals else currencyUnit.decimals - decimalCount
                val factor = MoneyScale.rescale(1L, exponent).toInt()
                val message = if (isMultiply) {
                    getString(R.string.message_multiply_currency_decimals, factor)
                } else {
                    getString(R.string.message_divide_currency_decimals, factor)
                }
                pendingDecimalCount = decimalCount
                rescaleDialogMessage = message
                return
            }
        }

        executeSaveCurrency(decimalCount, null)
    }

    private fun executeSaveCurrency(decimalCount: Int, fixMoneyDecimals: Boolean?) {
        val contentValues = ContentValues().apply {
            put(Contract.Currency.NAME, name.trim())
            put(Contract.Currency.SYMBOL, symbol.trim().ifEmpty { null })
            put(Contract.Currency.DECIMALS, decimalCount)
            if (fixMoneyDecimals != null) {
                put(Contract.Currency.FIX_MONEY_DECIMALS, fixMoneyDecimals)
            }
        }

        when (mode) {
            Mode.NEW_ITEM -> {
                contentValues.put(Contract.Currency.ISO, iso.trim().uppercase())
                contentResolver.insert(DataContentProvider.CONTENT_CURRENCIES, contentValues)
                CurrencyManager.invalidateCache(this)
            }
            Mode.EDIT_ITEM -> {
                val targetIso = isoParam ?: return
                val uri = Uri.withAppendedPath(DataContentProvider.CONTENT_CURRENCIES, targetIso)
                contentResolver.update(uri, contentValues, null, null)
                CurrencyManager.invalidateCache(this)
            }
        }
        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun deleteCurrency() {
        val targetIso = isoParam
        if (mode == Mode.EDIT_ITEM && !targetIso.isNullOrEmpty()) {
            val uri = Uri.withAppendedPath(DataContentProvider.CONTENT_CURRENCIES, targetIso)
            try {
                contentResolver.delete(uri, null, null)
                CurrencyManager.invalidateCache(this)
                setResult(Activity.RESULT_OK)
                finish()
            } catch (e: SQLiteDataException) {
                if (e.errorCode == Contract.ErrorCode.CURRENCY_IN_USE) {
                    Toast.makeText(this, R.string.message_error_delete_currency, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    companion object {
        const val MODE = "NewEditCurrencyActivity::Mode"
        const val ISO = "NewEditCurrencyActivity::Iso"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewEditCurrencyScreen(
    mode: NewEditCurrencyActivity.Mode,
    name: String,
    onNameChange: (String) -> Unit,
    nameError: Boolean,
    iso: String,
    onIsoChange: (String) -> Unit,
    isoError: Boolean,
    symbol: String,
    onSymbolChange: (String) -> Unit,
    decimals: String,
    onDecimalsChange: (String) -> Unit,
    decimalsError: Boolean,
    showDeleteDialog: Boolean,
    onShowDeleteDialogChange: (Boolean) -> Unit,
    onConfirmDelete: () -> Unit,
    rescaleDialogMessage: String?,
    onConfirmRescale: (Boolean) -> Unit,
    onDismissRescale: () -> Unit,
    onBackClick: () -> Unit,
    onSaveClick: () -> Unit
) {
    val titleRes = if (mode == NewEditCurrencyActivity.Mode.NEW_ITEM) {
        R.string.title_activity_new_currency
    } else {
        R.string.title_activity_edit_currency
    }
    val context = LocalContext.current
    val title = context.getString(titleRes)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (mode == NewEditCurrencyActivity.Mode.EDIT_ITEM) {
                        IconButton(onClick = { onShowDeleteDialogChange(true) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Currency")
                        }
                    }
                    IconButton(onClick = onSaveClick) {
                        Icon(Icons.Default.Check, contentDescription = "Save Currency")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Button(
                        onClick = onSaveClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text(
                            text = stringResource(R.string.menu_action_save_changes),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
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
            // Currency Details Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Currency Name
                    OutlinedTextField(
                        value = name,
                        onValueChange = onNameChange,
                        label = { Text(stringResource(R.string.hint_name)) },
                        leadingIcon = {
                            Icon(Icons.Default.Title, contentDescription = null)
                        },
                        isError = nameError,
                        supportingText = if (nameError) {
                            { Text(stringResource(R.string.error_input_name_not_valid)) }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next
                        ),
                        shape = RoundedCornerShape(14.dp)
                    )

                    // ISO Code
                    OutlinedTextField(
                        value = iso,
                        onValueChange = onIsoChange,
                        label = { Text(stringResource(R.string.hint_iso)) },
                        leadingIcon = {
                            Icon(Icons.Default.Paid, contentDescription = null)
                        },
                        isError = isoError,
                        enabled = mode == NewEditCurrencyActivity.Mode.NEW_ITEM,
                        supportingText = if (isoError) {
                            { Text(stringResource(R.string.error_input_iso_not_valid)) }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Next
                        ),
                        shape = RoundedCornerShape(14.dp)
                    )

                    // Symbol (Optional)
                    OutlinedTextField(
                        value = symbol,
                        onValueChange = onSymbolChange,
                        label = { Text(stringResource(R.string.hint_symbol)) },
                        leadingIcon = {
                            Icon(Icons.Default.AttachMoney, contentDescription = null)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Next
                        ),
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            }

            // Decimal Configuration Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = decimals,
                        onValueChange = onDecimalsChange,
                        label = { Text(stringResource(R.string.hint_decimals)) },
                        leadingIcon = {
                            Icon(Icons.Default.Numbers, contentDescription = null)
                        },
                        isError = decimalsError,
                        supportingText = if (decimalsError) {
                            { Text(stringResource(R.string.error_input_decimals_not_valid)) }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { onShowDeleteDialogChange(false) },
            title = { Text(stringResource(R.string.title_warning)) },
            text = { Text(stringResource(R.string.message_delete_currency)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onShowDeleteDialogChange(false)
                        onConfirmDelete()
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { onShowDeleteDialogChange(false) }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    // Rescale Decimal Confirmation Dialog
    if (rescaleDialogMessage != null) {
        AlertDialog(
            onDismissRequest = onDismissRescale,
            title = { Text(stringResource(R.string.title_warning)) },
            text = { Text(rescaleDialogMessage) },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onConfirmRescale(false) }) {
                        Text(stringResource(R.string.action_skip))
                    }
                    Button(onClick = { onConfirmRescale(true) }) {
                        Text(stringResource(android.R.string.ok))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRescale) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}
