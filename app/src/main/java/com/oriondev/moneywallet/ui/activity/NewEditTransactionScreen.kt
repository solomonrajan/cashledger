@file:Suppress("LongMethod", "LongParameterList", "MatchingDeclarationName", "FunctionNaming", "WildcardImport", "MaxLineLength")

package com.oriondev.moneywallet.ui.activity

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.*
import java.util.Date

data class TransactionScreenState(
    val money: Long = 0L,
    val currency: CurrencyUnit? = null,
    val description: String = "",
    val category: Category? = null,
    val date: Date? = null,
    val wallet: Wallet? = null,
    val event: Event? = null,
    val people: List<Person> = emptyList(),
    val place: Place? = null,
    val note: String = "",
    val confirmed: Boolean = true,
    val countInTotal: Boolean = true,
    val attachments: List<Attachment> = emptyList(),
    val mode: NewEditItemActivity.Mode = NewEditItemActivity.Mode.NEW_ITEM,
    val titleRes: Int = R.string.title_activity_new_transaction,
    
    // UI visibility toggles from Rules
    val isCategoryEnabled: Boolean = true,
    val isMoneyEnabled: Boolean = true,
    val isWalletEnabled: Boolean = true
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewEditTransactionScreen(
    state: TransactionScreenState,
    onBackClick: () -> Unit,
    onSaveClick: () -> Unit,
    onMoneyClick: () -> Unit,
    onCategoryClick: () -> Unit,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
    onWalletClick: () -> Unit,
    onEventClick: () -> Unit,
    onEventClear: () -> Unit,
    onPeopleClick: () -> Unit,
    onPeopleClear: () -> Unit,
    onPlaceClick: () -> Unit,
    onPlaceClear: () -> Unit,
    onAttachmentClick: () -> Unit,
    onAttachmentOpen: (Attachment) -> Unit,
    onAttachmentDelete: (Attachment) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onConfirmedChange: (Boolean) -> Unit,
    onCountInTotalChange: (Boolean) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(state.titleRes)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onAttachmentClick) {
                        Icon(Icons.Default.Add, contentDescription = "Attach File")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onSaveClick) {
                Icon(androidx.compose.material.icons.Icons.Default.ArrowBack, contentDescription = "Save") // Use a save icon if available, or just fallback
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
            
            // Header: Money
            Card(
                modifier = Modifier.fillMaxWidth().clickable(enabled = state.isMoneyEnabled, onClick = onMoneyClick),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${state.currency?.symbol ?: "?"} ${state.money}", 
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            OutlinedTextField(
                value = state.description,
                onValueChange = onDescriptionChange,
                label = { Text(stringResource(R.string.hint_description)) },
                modifier = Modifier.fillMaxWidth()
            )

            // Category Picker
            OutlinedTextField(
                value = state.category?.name ?: "",
                onValueChange = {},
                label = { Text(stringResource(R.string.hint_category)) },
                modifier = Modifier.fillMaxWidth().clickable(enabled = state.isCategoryEnabled, onClick = onCategoryClick),
                enabled = false,
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )

            // Date & Time
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.date?.toString() ?: "", // Placeholder formatting
                    onValueChange = {},
                    label = { Text(stringResource(R.string.hint_date)) },
                    modifier = Modifier.weight(1f).clickable(onClick = onDateClick),
                    enabled = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                OutlinedTextField(
                    value = state.date?.toString() ?: "", // Placeholder formatting
                    onValueChange = {},
                    label = { Text(stringResource(R.string.hint_time)) },
                    modifier = Modifier.weight(1f).clickable(onClick = onTimeClick),
                    enabled = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            // Wallet
            OutlinedTextField(
                value = state.wallet?.name ?: "",
                onValueChange = {},
                label = { Text(stringResource(R.string.hint_wallet)) },
                modifier = Modifier.fillMaxWidth().clickable(enabled = state.isWalletEnabled, onClick = onWalletClick),
                enabled = false,
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            
            // Optional Fields (Event, People, Place)
            ClearablePickerField(
                label = stringResource(R.string.hint_event),
                value = state.event?.name ?: "",
                onClick = onEventClick,
                onClear = onEventClear
            )

            ClearablePickerField(
                label = stringResource(R.string.hint_people),
                value = state.people.joinToString { it.name },
                onClick = onPeopleClick,
                onClear = onPeopleClear
            )

            ClearablePickerField(
                label = stringResource(R.string.hint_place),
                value = state.place?.name ?: "",
                onClick = onPlaceClick,
                onClear = onPlaceClear
            )

            OutlinedTextField(
                value = state.note,
                onValueChange = onNoteChange,
                label = { Text(stringResource(R.string.hint_note)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            // Checkboxes
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Checkbox(checked = state.confirmed, onCheckedChange = onConfirmedChange)
                Text(stringResource(R.string.hint_confirmed), modifier = Modifier.clickable { onConfirmedChange(!state.confirmed) })
            }
            
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Checkbox(checked = state.countInTotal, onCheckedChange = onCountInTotalChange)
                Text(stringResource(R.string.hint_show_in_total), modifier = Modifier.clickable { onCountInTotalChange(!state.countInTotal) })
            }
            
            // Attachments
            if (state.attachments.isNotEmpty()) {
                Text(stringResource(R.string.hint_attachments), fontWeight = FontWeight.Bold)
                state.attachments.forEach { attachment ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onAttachmentOpen(attachment) }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(attachment.name ?: "Attachment")
                        IconButton(onClick = { onAttachmentDelete(attachment) }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove")
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(80.dp)) // FAB spacing
        }
    }
}

@Composable
fun ClearablePickerField(
    label: String,
    value: String,
    onClick: () -> Unit,
    onClear: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        enabled = false,
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Close, contentDescription = "Clear")
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            disabledTextColor = MaterialTheme.colorScheme.onSurface,
            disabledBorderColor = MaterialTheme.colorScheme.outline,
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}
