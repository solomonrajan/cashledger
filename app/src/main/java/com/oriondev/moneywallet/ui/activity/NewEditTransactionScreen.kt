@file:Suppress("LongMethod", "LongParameterList", "MatchingDeclarationName", "FunctionNaming", "WildcardImport", "MaxLineLength", "MagicNumber", "UnusedParameter", "CyclomaticComplexMethod")

package com.oriondev.moneywallet.ui.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

val GoogleSansCode = FontFamily(Font(R.font.google_sans_code_regular))

data class TransactionScreenState(
    val money: Long = 0L,
    val currency: CurrencyUnit? = null,
    val description: String = "",
    val category: Category? = null,
    val date: Date? = Date(),
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewEditTransactionScreen(
    state: TransactionScreenState,
    onBackClick: () -> Unit,
    onSaveClick: () -> Unit,
    onMoneyClick: () -> Unit,
    onCategoryClick: () -> Unit,
    onDateChange: (Date) -> Unit,
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
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    
    val calendar = remember(state.date) {
        Calendar.getInstance().apply {
            time = state.date ?: Date()
        }
    }
    
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = calendar.timeInMillis
    )
    
    val timePickerState = rememberTimePickerState(
        initialHour = calendar.get(Calendar.HOUR_OF_DAY),
        initialMinute = calendar.get(Calendar.MINUTE)
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showDatePicker = false
                    showTimePicker = true
                }) { Text("Next") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showTimePicker = false
                    val newCalendar = Calendar.getInstance()
                    datePickerState.selectedDateMillis?.let { newCalendar.timeInMillis = it }
                    newCalendar.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                    newCalendar.set(Calendar.MINUTE, timePickerState.minute)
                    onDateChange(newCalendar.time)
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
            text = {
                TimePicker(state = timePickerState)
            }
        )
    }

    MaterialTheme(colorScheme = colorScheme) {
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
                            Icon(Icons.Default.AttachFile, contentDescription = "Attach Document")
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
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp) // Increased bottom gap for gesture nav
                        .height(64.dp), // Taller button for M3 expressive
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    shape = RoundedCornerShape(32.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Save", modifier = Modifier.padding(end = 12.dp))
                    Text("Save Transaction", style = MaterialTheme.typography.titleMedium)
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
                
                // Header: Money
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    shape = RoundedCornerShape(24.dp) // M3 expressive large rounded corners
                ) {
                    Box(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                text = "Amount",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            val divisor = Math.pow(10.0, state.currency?.decimals?.toDouble() ?: 2.0)
                            val formattedAmount = java.text.NumberFormat.getNumberInstance(java.util.Locale.US).apply {
                                minimumFractionDigits = state.currency?.decimals ?: 2
                                maximumFractionDigits = state.currency?.decimals ?: 2
                            }.format(state.money / divisor)
    
                            Text(
                                text = "${state.currency?.symbol ?: "₹"} $formattedAmount", 
                                style = MaterialTheme.typography.displayMedium, // M3 expressive larger typography
                                fontFamily = GoogleSansCode,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        
                        IconButton(
                            onClick = onMoneyClick,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .background(MaterialTheme.colorScheme.primaryContainer, shape = CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Calculate,
                                contentDescription = "Calculator",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
    
                TransactionField(
                    iconVector = Icons.Default.Menu,
                    label = "Description",
                    value = state.description,
                    placeholder = "Enter description",
                    onValueChange = onDescriptionChange,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    enabled = true
                )
    
                TransactionField(
                    iconVector = Icons.Default.LocalOffer,
                    label = "Category",
                    value = state.category?.name ?: "",
                    placeholder = "Select category",
                    onClick = onCategoryClick,
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
                )
    
                val dateFormat = SimpleDateFormat("EEE, MMM d, yyyy  HH:mm:ss", Locale.getDefault())
                val dateString = state.date?.let { dateFormat.format(it) } ?: ""
    
                TransactionField(
                    iconVector = Icons.Default.DateRange,
                    label = "Date & time",
                    value = dateString,
                    placeholder = "Select date and time",
                    onClick = { showDatePicker = true },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
                )
    
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InputChip(
                        selected = state.wallet != null,
                        onClick = onWalletClick,
                        label = { Text(state.wallet?.name ?: "Wallet") },
                        leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        trailingIcon = if (state.wallet != null) { { Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp)) } } else null,
                        shape = RoundedCornerShape(16.dp)
                    )
                    
                    InputChip(
                        selected = state.event != null,
                        onClick = onEventClick,
                        label = { Text(state.event?.name ?: "Event") },
                        leadingIcon = { Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        trailingIcon = if (state.event != null) { { Icon(Icons.Default.Clear, contentDescription = "Clear Event", modifier = Modifier.size(18.dp).clickable { onEventClear() }) } } else null,
                        shape = RoundedCornerShape(16.dp)
                    )
                    
                    val peopleText = if (state.people.isNotEmpty()) state.people.joinToString { it.name } else "People"
                    InputChip(
                        selected = state.people.isNotEmpty(),
                        onClick = onPeopleClick,
                        label = { Text(peopleText) },
                        leadingIcon = { Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        trailingIcon = if (state.people.isNotEmpty()) { { Icon(Icons.Default.Clear, contentDescription = "Clear People", modifier = Modifier.size(18.dp).clickable { onPeopleClear() }) } } else null,
                        shape = RoundedCornerShape(16.dp)
                    )

                    InputChip(
                        selected = state.place != null,
                        onClick = onPlaceClick,
                        label = { Text(state.place?.name ?: "Place") },
                        leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        trailingIcon = if (state.place != null) { { Icon(Icons.Default.Clear, contentDescription = "Clear Place", modifier = Modifier.size(18.dp).clickable { onPlaceClear() }) } } else null,
                        shape = RoundedCornerShape(16.dp)
                    )
                }
    
                TransactionField(
                    iconVector = Icons.Default.Description,
                    label = "Note",
                    value = state.note,
                    placeholder = "Add a note",
                    onValueChange = onNoteChange,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    enabled = true
                )
                
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun TransactionField(
    iconVector: ImageVector? = null,
    iconRes: Int? = null,
    label: String,
    value: String,
    placeholder: String,
    onClick: (() -> Unit)? = null,
    onValueChange: ((String) -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    enabled: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconVector != null) {
            Icon(
                imageVector = iconVector,
                contentDescription = label,
                modifier = Modifier.padding(end = 16.dp).size(28.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        } else if (iconRes != null) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = label,
                modifier = Modifier.padding(end = 16.dp).size(28.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            val modifier = if (onClick != null) {
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
            } else {
                Modifier.fillMaxWidth()
            }
            
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange ?: {},
                label = { Text(label, style = MaterialTheme.typography.bodyLarge) },
                placeholder = { Text(placeholder) },
                modifier = modifier,
                enabled = enabled,
                keyboardOptions = keyboardOptions,
                trailingIcon = trailingIcon,
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    focusedLabelColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(16.dp) // M3 expressive large corners for inputs
            )
        }
    }
}
