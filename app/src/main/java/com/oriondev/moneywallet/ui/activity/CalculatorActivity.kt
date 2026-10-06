@file:Suppress("LongMethod", "FunctionName", "FunctionNaming", "WildcardImport", "MaxLineLength", "MagicNumber", "UnusedParameter", "LongParameterList", "ReturnCount", "CyclomaticComplexMethod")
/*
 * Copyright (c) 2018. MoneyWallet
 * Copyright (c) 2026. solomonrajan/CashLedger
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
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.CurrencyUnit
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog
import com.oriondev.moneywallet.utils.EquationSolver

class CalculatorActivity : AppCompatActivity(), EquationSolver.Controller {

    private lateinit var solver: EquationSolver
    private var keypadMode = false
    private var allowNegative = false

    private var displayText by mutableStateOf("0")
    private var isPendingOperation by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val currentIntent = intent
        keypadMode = currentIntent.getIntExtra(ACTIVITY_MODE, MODE_CALCULATOR) == MODE_KEYPAD
        allowNegative = currentIntent.getBooleanExtra(ALLOW_NEGATIVE, false)

        solver = EquationSolver(savedInstanceState, this)

        if (savedInstanceState == null) {
            val currency: CurrencyUnit? = currentIntent.getParcelableExtra(CURRENCY)
            val money = currentIntent.getLongExtra(MONEY, 0L)
            solver.setValue(currency, money)
        }
        
        isPendingOperation = solver.isPendingOperation

        setContent {
            val isDark = isSystemInDarkTheme()
            val colorScheme = if (isDark) darkColorScheme() else lightColorScheme()
            
            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as Activity).window
                    window.statusBarColor = Color.Transparent.toArgb()
                    window.navigationBarColor = Color.Transparent.toArgb()
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
                    WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !isDark
                }
            }

            MaterialTheme(colorScheme = colorScheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    CalculatorScreen(
                        displayText = displayText,
                        isKeypadMode = keypadMode,
                        isPendingOperation = isPendingOperation,
                        onBackClick = { finish() },
                        onInput = { op -> handleInput(op) }
                    )
                }
            }
        }
    }

    private fun handleInput(op: String) {
        // Assume cursor is always at the end for simple Compose UI
        val cursor = displayText.length
        
        when (op) {
            OP_CLEAR -> solver.clear()
            OP_CANCEL -> solver.backspace(cursor)
            OP_EXECUTE -> execute()
            OP_ADDITION -> solver.appendOperation(EquationSolver.Operation.ADDITION)
            OP_SUBTRACTION -> solver.appendOperation(EquationSolver.Operation.SUBTRACTION)
            OP_MULTIPLICATION -> solver.appendOperation(EquationSolver.Operation.MULTIPLICATION)
            OP_DIVISION -> solver.appendOperation(EquationSolver.Operation.DIVISION)
            OP_POINT -> solver.insertPoint(cursor)
            else -> solver.insertNumber(op, cursor)
        }
        
        isPendingOperation = solver.isPendingOperation
    }

    private fun execute() {
        if (solver.isPendingOperation) {
            if (!solver.execute(true)) {
                // error
            }
        } else if (keypadMode) {
            val money = solver.result
            if (money < 0 && !allowNegative) {
                ThemedDialog.buildMaterialDialog(this)
                    .setTitle(R.string.title_error)
                    .setMessage(R.string.message_error_negative_amount)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                return
            }
            val resultIntent = Intent()
            resultIntent.putExtra(MONEY, money)
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
    }

    override fun onUpdateDisplay(text: String?) {
        displayText = if (text.isNullOrEmpty()) "0" else text
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        solver.onSaveInstanceState(outState)
    }

    companion object {
        const val ACTIVITY_MODE = "CalculatorActivity::Parameters::ActivityMode"
        const val CURRENCY = "CalculatorActivity::Parameters::Currency"
        const val MONEY = "CalculatorActivity::Parameters::Money"
        const val ALLOW_NEGATIVE = "CalculatorActivity::Parameters::AllowNegative"

        const val MODE_CALCULATOR = 0
        const val MODE_KEYPAD = 1

        const val OP_00 = "00"
        const val OP_0 = "0"
        const val OP_1 = "1"
        const val OP_2 = "2"
        const val OP_3 = "3"
        const val OP_4 = "4"
        const val OP_5 = "5"
        const val OP_6 = "6"
        const val OP_7 = "7"
        const val OP_8 = "8"
        const val OP_9 = "9"
        const val OP_POINT = "."
        const val OP_CLEAR = "C"
        const val OP_CANCEL = "B"
        const val OP_ADDITION = "A"
        const val OP_SUBTRACTION = "S"
        const val OP_MULTIPLICATION = "M"
        const val OP_DIVISION = "D"
        const val OP_EXECUTE = "E"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
    displayText: String,
    isKeypadMode: Boolean,
    isPendingOperation: Boolean,
    onBackClick: () -> Unit,
    onInput: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_activity_calculator)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Display
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(36.dp), // M3 expressive huge corner radius
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 24.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Text(
                        text = if (displayText.isEmpty()) "0" else displayText,
                        style = MaterialTheme.typography.displayLarge, // M3 expressive display font
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Keypad

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp) // M3 expressive breathing room
            ) {
                CalculatorRow(listOf("C" to CalculatorActivity.OP_CLEAR, "÷" to CalculatorActivity.OP_DIVISION, "×" to CalculatorActivity.OP_MULTIPLICATION, "DEL" to CalculatorActivity.OP_CANCEL), onInput)
                CalculatorRow(listOf("7" to CalculatorActivity.OP_7, "8" to CalculatorActivity.OP_8, "9" to CalculatorActivity.OP_9, "-" to CalculatorActivity.OP_SUBTRACTION), onInput)
                CalculatorRow(listOf("4" to CalculatorActivity.OP_4, "5" to CalculatorActivity.OP_5, "6" to CalculatorActivity.OP_6, "+" to CalculatorActivity.OP_ADDITION), onInput)
                
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(3f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CalculatorRow(listOf("1" to CalculatorActivity.OP_1, "2" to CalculatorActivity.OP_2, "3" to CalculatorActivity.OP_3), onInput)
                        CalculatorRow(listOf("0" to CalculatorActivity.OP_0, "00" to CalculatorActivity.OP_00, "." to CalculatorActivity.OP_POINT), onInput)
                    }
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape)
                            .clickable { onInput(CalculatorActivity.OP_EXECUTE) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isKeypadMode && !isPendingOperation) {
                            Icon(Icons.Default.Check, contentDescription = "Confirm", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(36.dp))
                        } else {
                            Text("=", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CalculatorRow(items: List<Pair<String, String>>, onInput: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp) // Match M3 expressive spacing
    ) {
        items.forEach { (label, op) ->
            val isAction = op in listOf(CalculatorActivity.OP_CLEAR, CalculatorActivity.OP_CANCEL, CalculatorActivity.OP_DIVISION, CalculatorActivity.OP_MULTIPLICATION, CalculatorActivity.OP_SUBTRACTION, CalculatorActivity.OP_ADDITION)
            val containerColor = if (isAction) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant
            val contentColor = if (isAction) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            
            Button(
                onClick = { onInput(op) },
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1.2f),
                shape = androidx.compose.foundation.shape.CircleShape, // M3 expressive circular/pill keys
                colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
                contentPadding = PaddingValues(0.dp)
            ) {
                if (op == CalculatorActivity.OP_CANCEL) {
                    Icon(Icons.Default.Backspace, contentDescription = "Delete", modifier = Modifier.size(28.dp))
                } else {
                    Text(text = label, style = MaterialTheme.typography.headlineMedium)
                }
            }
        }
    }
}
