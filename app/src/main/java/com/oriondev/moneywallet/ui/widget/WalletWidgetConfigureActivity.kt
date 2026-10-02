package com.oriondev.moneywallet.ui.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.Wallet

class WalletWidgetConfigureActivity : ComponentActivity() {

    private val viewModel: WalletWidgetConfigureViewModel by viewModels()
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        setResult(Activity.RESULT_CANCELED, resultIntent())

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        viewModel.initialize(this, appWidgetId)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    WalletWidgetConfigureScreen(
                        viewModel = viewModel,
                        onCancel = { finish() },
                        onSave = { wallet, showLocked -> saveAndFinish(wallet, showLocked) }
                    )
                }
            }
        }
    }

    private fun saveAndFinish(wallet: Wallet, showWhenLocked: Boolean) {
        WalletWidgetPreferences.save(this, appWidgetId, wallet.id, showWhenLocked)
        AppWidgetManager.getInstance(this).updateAppWidget(
            appWidgetId,
            WalletWidgetProvider.buildViews(this, appWidgetId)
        )
        setResult(Activity.RESULT_OK, resultIntent())
        finish()
    }

    private fun resultIntent(): Intent {
        return Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletWidgetConfigureScreen(
    viewModel: WalletWidgetConfigureViewModel,
    onCancel: () -> Unit,
    onSave: (Wallet, Boolean) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showError by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.title_activity_widget_configure)) },
                actions = {
                    TextButton(onClick = {
                        if (uiState.selectedWallet != null) {
                            onSave(uiState.selectedWallet!!, uiState.showWhenLocked)
                        } else {
                            showError = true
                        }
                    }) {
                        Text("SAVE")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Box is used here to capture clicks over the disabled text field
            Box(modifier = Modifier.clickable { viewModel.setPickerOpen(true) }) {
                OutlinedTextField(
                    value = uiState.selectedWallet?.name ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(id = R.string.hint_wallet)) },
                    isError = showError && uiState.selectedWallet == null,
                    supportingText = { if (showError && uiState.selectedWallet == null) Text(stringResource(id = R.string.error_input_missing_wallet)) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.onShowWhenLockedChanged(!uiState.showWhenLocked) }
                    .padding(vertical = 8.dp)
            ) {
                Checkbox(
                    checked = uiState.showWhenLocked,
                    onCheckedChange = { viewModel.onShowWhenLockedChanged(it) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Show when screen is locked")
            }
        }
    }

    if (uiState.isPickerOpen) {
        AlertDialog(
            onDismissRequest = { viewModel.setPickerOpen(false) },
            title = { Text(stringResource(id = R.string.hint_wallet)) },
            text = {
                LazyColumn {
                    items(uiState.availableWallets) { wallet ->
                        Text(
                            text = wallet.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.onWalletSelected(wallet) }
                                .padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.setPickerOpen(false) }) {
                    Text(stringResource(id = android.R.string.cancel))
                }
            }
        )
    }
}
